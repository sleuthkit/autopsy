/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.timeline;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import static java.util.Collections.singleton;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import static javafx.concurrent.Worker.State.FAILED;
import static javafx.concurrent.Worker.State.SUCCEEDED;
import javafx.scene.control.Alert;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.SwingUtilities;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.ReadablePeriod;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.DATA_SOURCE_ADDED;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadUtils;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.timeline.events.TimelineEventAddedEvent;
import org.sleuthkit.autopsy.timeline.events.ViewInTimelineRequestedEvent;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.SqlFilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.DescriptionFilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;
import org.sleuthkit.autopsy.timeline.zooming.EventsModelParams;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineFilter.EventTypeFilter;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * Controller in the MVC design along with FilteredEventsModel TimeLineView.
 * Forwards interpreted user gestures form views to model. Provides model to
 * view.
 *
 * Concurrency Policy:<ul>
 * <li>Since filteredEvents is internally synchronized, only compound access to
 * it needs external synchronization</li>
 *
 * <li>Other state including topComponent, viewMode, and the listeners should
 * only be accessed with this object's intrinsic lock held, or on the EDT as
 * indicated.
 * </li>
 * </ul>
 */
@NbBundle.Messages({"Timeline.dialogs.title= Timeline",
    "TimeLinecontroller.updateNowQuestion=Do you want to update the events database now?"})
public class TimeLineController {

    private static final Logger logger = Logger.getLogger(TimeLineController.class.getName());

    private static final ReadOnlyObjectWrapper<TimeZone> timeZone = new ReadOnlyObjectWrapper<>(TimeZone.getDefault());

    private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("Timeline Controller BG thread").build()));
    private final ReadOnlyListWrapper<Task<?>> tasks = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
    private final ReadOnlyDoubleWrapper taskProgress = new ReadOnlyDoubleWrapper(-1);
    private final ReadOnlyStringWrapper taskMessage = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper taskTitle = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper statusMessage = new ReadOnlyStringWrapper();

    private final EventBus eventbus = new EventBus("TimeLineController_EventBus");

    public static ZoneId getTimeZoneID() {
        return timeZone.get().toZoneId();
    }

    public static DateTimeFormatter getZonedFormatter() {
        return DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss").withZone(getJodaTimeZone()); //NON-NLS
    }

    public static DateTimeZone getJodaTimeZone() {
        return DateTimeZone.forTimeZone(timeZoneProperty().get());
    }

    public static ReadOnlyObjectProperty<TimeZone> timeZoneProperty() {
        return timeZone.getReadOnlyProperty();
    }

    public static TimeZone getTimeZone() {
        return timeZone.get();
    }

    /**
     * Status is a string that will be displayed in the status bar as a kind of
     * user hint/information when it is not empty
     *
     * @return The status property
     */
    public ReadOnlyStringProperty statusMessageProperty() {
        return statusMessage.getReadOnlyProperty();
    }

    public void setStatusMessage(String string) {
        statusMessage.set(string);
    }
    private final Case autoCase;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<DescriptionFilterState> quickHideFilters = FXCollections.observableArrayList();

    public ObservableList<DescriptionFilterState> getQuickHideFilters() {
        return quickHideFilters;
    }

    /**
     * @return The autopsy Case assigned to the controller
     */
    public Case getAutopsyCase() {
        return autoCase;
    }

    synchronized public ReadOnlyListProperty<Task<?>> getTasks() {
        return tasks.getReadOnlyProperty();
    }

    synchronized public ReadOnlyDoubleProperty taskProgressProperty() {
        return taskProgress.getReadOnlyProperty();
    }

    synchronized public ReadOnlyStringProperty taskMessageProperty() {
        return taskMessage.getReadOnlyProperty();
    }

    synchronized public ReadOnlyStringProperty taskTitleProperty() {
        return taskTitle.getReadOnlyProperty();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private TimeLineTopComponent topComponent;

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<ViewMode> viewMode = new ReadOnlyObjectWrapper<>(ViewMode.COUNTS);

    @GuardedBy("filteredEvents")
    private final EventsModel filteredEvents;

    @GuardedBy("this")
    private final EventsModelParams InitialZoomState;

    @GuardedBy("this")
    private final History<EventsModelParams> historyManager = new History<>();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<EventsModelParams> currentParams = new ReadOnlyObjectWrapper<>();

    //selected events (ie shown in the result viewer)
    @GuardedBy("this")
    private final ObservableList<Long> selectedEventIDs = FXCollections.<Long>observableArrayList();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<Interval> selectedTimeRange = new ReadOnlyObjectWrapper<>();

    private final PromptDialogManager promptDialogManager = new PromptDialogManager(this);

    /**
     * Get an ObservableList of selected event IDs
     *
     * @return A list of the selected event IDs
     */
    synchronized public ObservableList<Long> getSelectedEventIDs() {
        return selectedEventIDs;
    }

    /**
     * Get a read only observable view of the selected time range.
     *
     * @return A read only view of the selected time range.
     */
    synchronized public ReadOnlyObjectProperty<Interval> selectedTimeRangeProperty() {
        return selectedTimeRange.getReadOnlyProperty();
    }

    /**
     * Get the selected time range.
     *
     * @return The selected time range.
     */
    synchronized public Interval getSelectedTimeRange() {
        return selectedTimeRange.get();
    }

    synchronized public ReadOnlyBooleanProperty canAdvanceProperty() {
        return historyManager.getCanAdvance();
    }

    synchronized public ReadOnlyBooleanProperty canRetreatProperty() {
        return historyManager.getCanRetreat();
    }

    synchronized public ReadOnlyObjectProperty<ViewMode> viewModeProperty() {
        return viewMode.getReadOnlyProperty();
    }

    /**
     * Set a new ViewMode as the active one.
     *
     * @param viewMode The new ViewMode to set.
     */
    synchronized public void setViewMode(ViewMode viewMode) {
        if (this.viewMode.get() != viewMode) {
            this.viewMode.set(viewMode);
        }
    }

    /**
     * Get the currently active ViewMode.
     *
     * @return The currently active ViewMode.
     */
    synchronized public ViewMode getViewMode() {
        return viewMode.get();
    }

    TimeLineController(Case autoCase) throws TskCoreException {
        this.autoCase = autoCase;
        filteredEvents = new EventsModel(autoCase, currentParams.getReadOnlyProperty());
        /*
         * as the history manager's current state changes, modify the tags
         * filter to be in sync, and expose that as propery from
         * TimeLineController. Do we need to do this with datasource or hash hit
         * filters?
         */
        historyManager.currentState().addListener((observable, oldState, newState) -> {
            EventsModelParams historyManagerState = newState;
            filteredEvents.addDataSourceFilters(historyManagerState.getEventFilterState());
            currentParams.set(historyManagerState);

        });

        try {
            InitialZoomState = new EventsModelParams(filteredEvents.getSpanningInterval(),
                    TimelineEventType.HierarchyLevel.CATEGORY,
                    filteredEvents.eventFilterProperty().get(),
                    TimelineLevelOfDetail.LOW);
        } catch (TskCoreException ex) {
            throw new TskCoreException("Error getting spanning interval.", ex);
        }
        historyManager.advance(InitialZoomState);

        //clear the selected events when the view mode changes
        viewMode.addListener(observable -> {
            try {
                selectEventIDs(Collections.emptySet());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error clearing the timeline selection.", ex);
            }
        });
    }

    /**
     * @return a shared events model
     */
    public EventsModel getEventsModel() {
        return filteredEvents;
    }

    public void applyDefaultFilters() {
        pushFilters(filteredEvents.getDefaultEventFilterState());
    }

    public void zoomOutToActivity() throws TskCoreException {
        Interval boundingEventsInterval = filteredEvents.getSpanningInterval(getJodaTimeZone());
        advance(filteredEvents.modelParamsProperty().get().withTimeRange(boundingEventsInterval));
    }

    private final ObservableSet<DetailViewEvent> pinnedEvents = FXCollections.observableSet();
    private final ObservableSet<DetailViewEvent> pinnedEventsUnmodifiable = FXCollections.unmodifiableObservableSet(pinnedEvents);

    public void pinEvent(DetailViewEvent event) {
        pinnedEvents.add(event);
    }

    public void unPinEvent(DetailViewEvent event) {
        pinnedEvents.removeIf(event::equals);
    }

    public ObservableSet<DetailViewEvent> getPinnedEvents() {
        return pinnedEventsUnmodifiable;
    }

    /**
     * Show the entire range of the timeline.
     */
    boolean showFullRange() throws TskCoreException {
        synchronized (filteredEvents) {
            return pushTimeRange(filteredEvents.getSpanningInterval());
        }
    }

    /**
     * Show the events and the amount of time indicated in the given
     * ViewInTimelineRequestedEvent in the List View.
     *
     * @param requestEvent Contains the ID of the requested events and the
     * timerange to show.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void showInListView(ViewInTimelineRequestedEvent requestEvent) throws TskCoreException {
        synchronized (filteredEvents) {
            setViewMode(ViewMode.LIST);
            selectEventIDs(requestEvent.getEventIDs());
            try {
                if (pushTimeRange(requestEvent.getInterval()) == false) {
                    eventbus.post(requestEvent);
                }
            } catch (TskCoreException ex) {
                throw new TskCoreException("Error pushing requested timerange.", ex);
            }
        }
    }

    /**
     * Shuts down the task executor in charge of handling case events.
     */
    void shutDownTimeLineListeners() {
        ThreadUtils.shutDownTaskExecutor(executor);
    }

    /**
     * "Shut down" Timeline. Close the timeline window.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void shutDownTimeLineGui() {
        if (topComponent != null) {
            topComponent.close();
            topComponent = null;
        }
    }

    /**
     * Add the case and ingest listeners, prompt for rebuilding the database if
     * necessary, and show the timeline window.
     *
     * @param file The AbstractFile from which to choose an event to show in the
     * List View.
     * @param artifact The BlackboardArtifact to show in the List View.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void showTimeLine(AbstractFile file, BlackboardArtifact artifact) {
        Platform.runLater(() -> {
            //if there is an existing prompt or progressdialog,...
            if (promptDialogManager.bringCurrentDialogToFront()) {
                //... just show that
            } else {

                if ( //confirm timeline during ingest
                        IngestManager.getInstance().isIngestRunning()
                        && promptDialogManager.confirmDuringIngest() == false) {
                    return;  //if they cancel, do nothing.
                }
                try {
                    if (file == null && artifact == null) {
                        SwingUtilities.invokeLater(TimeLineController.this::showWindow);
                    } else {
                        //prompt user to pick specific event and time range
                        ShowInTimelineDialog showInTimelineDilaog = (file == null)
                                ? new ShowInTimelineDialog(this, artifact)
                                : new ShowInTimelineDialog(this, file);
                        Optional<ViewInTimelineRequestedEvent> dialogResult = showInTimelineDilaog.showAndWait();
                        dialogResult.ifPresent(viewInTimelineRequestedEvent -> {
                            SwingUtilities.invokeLater(this::showWindow);
                            try {
                                showInListView(viewInTimelineRequestedEvent); //show requested event in list view
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error showing requested events in listview: " + viewInTimelineRequestedEvent, ex);
                                new Alert(Alert.AlertType.ERROR, "There was an error opening Timeline.").showAndWait();
                            }
                        });

                    }
                } catch (TskCoreException tskCoreException) {
                    logger.log(Level.SEVERE, "Error showing Timeline ", tskCoreException);
                    new Alert(Alert.AlertType.ERROR, "There was an error opening Timeline.").showAndWait();
                }
            }
        });
    }

    /**
     * Request a time range the same length as the given period and centered
     * around the middle of the currently viewed time range.
     *
     * @param period The period of time to show around the current center of the
     * view.
     */
    synchronized public void pushPeriod(ReadablePeriod period) throws TskCoreException {
        synchronized (filteredEvents) {
            pushTimeRange(IntervalUtils.getIntervalAroundMiddle(filteredEvents.getTimeRange(), period));
        }
    }

    synchronized public void pushZoomOutTime() throws TskCoreException {
        final Interval timeRange = filteredEvents.getTimeRange();
        long toDurationMillis = timeRange.toDurationMillis() / 4;
        DateTime start = timeRange.getStart().minus(toDurationMillis);
        DateTime end = timeRange.getEnd().plus(toDurationMillis);
        pushTimeRange(new Interval(start, end));
    }

    synchronized public void pushZoomInTime() throws TskCoreException {
        final Interval timeRange = filteredEvents.getTimeRange();
        long toDurationMillis = timeRange.toDurationMillis() / 4;
        DateTime start = timeRange.getStart().plus(toDurationMillis);
        DateTime end = timeRange.getEnd().minus(toDurationMillis);
        pushTimeRange(new Interval(start, end));
    }

    /**
     * Show the timeline TimeLineTopComponent. This method will construct a new
     * instance of TimeLineTopComponent if necessary.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    synchronized private void showWindow() {
        if (topComponent == null) {
            topComponent = new TimeLineTopComponent(this);
        }
        if (topComponent.isOpened() == false) {
            topComponent.open();
        }
        topComponent.toFront();
        /*
         * Make this top component active so its ExplorerManager's lookup gets
         * proxied in Utilities.actionsGlobalContext()
         */
        topComponent.requestActive();
    }

    synchronized public TimeLineTopComponent getTopComponent() {
        return topComponent;
    }

    synchronized public void pushEventTypeZoom(TimelineEventType.HierarchyLevel typeZoomeLevel) {
        EventsModelParams currentZoom = filteredEvents.modelParamsProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withTypeZoomLevel(typeZoomeLevel));
        } else if (currentZoom.hasTypeZoomLevel(typeZoomeLevel) == false) {
            advance(currentZoom.withTypeZoomLevel(typeZoomeLevel));
        }
    }

    /**
     * Set the new interval to view, and record it in the history. The interval
     * will be clamped to the span of events in the current case.
     *
     * @param timeRange The Interval to view.
     *
     * @return True if the interval was changed. False if the interval was the
     * same as the existing one and no change happened.
     */
    synchronized public boolean pushTimeRange(Interval timeRange) throws TskCoreException {
        //clamp timerange to case
        Interval clampedTimeRange;
        if (timeRange == null) {
            clampedTimeRange = this.filteredEvents.getSpanningInterval();
        } else {
            Interval spanningInterval = this.filteredEvents.getSpanningInterval();
            if (spanningInterval.overlaps(timeRange)) {
                clampedTimeRange = spanningInterval.overlap(timeRange);
            } else {
                clampedTimeRange = spanningInterval;
            }
        }

        EventsModelParams currentZoom = filteredEvents.modelParamsProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withTimeRange(clampedTimeRange));
            return true;
        } else if (currentZoom.hasTimeRange(clampedTimeRange) == false) {
            advance(currentZoom.withTimeRange(clampedTimeRange));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Change the view by setting a new time range that is the length of
     * timeUnit and centered at the current center.
     *
     * @param timeUnit The unit of time to view
     *
     * @return true if the view actually changed.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    synchronized public boolean pushTimeUnit(TimeUnits timeUnit) throws TskCoreException {
        if (timeUnit == TimeUnits.FOREVER) {
            return showFullRange();
        } else {
            return pushTimeRange(IntervalUtils.getIntervalAroundMiddle(filteredEvents.getTimeRange(), timeUnit.toUnitPeriod()));
        }
    }

    synchronized public void pushDescrLOD(TimelineLevelOfDetail newLOD) {
        EventsModelParams currentZoom = filteredEvents.modelParamsProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withDescrLOD(newLOD));
        } else if (currentZoom.hasDescrLOD(newLOD) == false) {
            advance(currentZoom.withDescrLOD(newLOD));
        }
    }

    @SuppressWarnings("AssignmentToMethodParameter") //clamp timerange to case
    synchronized public void pushTimeAndType(Interval timeRange, TimelineEventType.HierarchyLevel typeZoom) throws TskCoreException {
        Interval overlappingTimeRange = this.filteredEvents.getSpanningInterval().overlap(timeRange);
        EventsModelParams currentZoom = filteredEvents.modelParamsProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withTimeAndType(overlappingTimeRange, typeZoom));
        } else if (currentZoom.hasTimeRange(overlappingTimeRange) == false && currentZoom.hasTypeZoomLevel(typeZoom) == false) {
            advance(currentZoom.withTimeAndType(overlappingTimeRange, typeZoom));
        } else if (currentZoom.hasTimeRange(overlappingTimeRange) == false) {
            advance(currentZoom.withTimeRange(overlappingTimeRange));
        } else if (currentZoom.hasTypeZoomLevel(typeZoom) == false) {
            advance(currentZoom.withTypeZoomLevel(typeZoom));
        }
    }

    synchronized public void pushFilters(RootFilterState filter) {
        EventsModelParams currentZoom = filteredEvents.modelParamsProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withFilterState(filter));
        } else if (currentZoom.hasFilterState(filter) == false) {
            advance(currentZoom.withFilterState(filter));
        }
    }

    synchronized public void advance() {
        historyManager.advance();
    }

    synchronized public void retreat() {
        historyManager.retreat();
    }

    synchronized private void advance(EventsModelParams newState) {
        historyManager.advance(newState);
    }

    /**
     * Select the given event IDs and set their spanning interval as the
     * selected time range.
     *
     * @param eventIDs The eventIDs to select
     */
    final synchronized public void selectEventIDs(Collection<Long> eventIDs) throws TskCoreException {
        selectedTimeRange.set(filteredEvents.getSpanningInterval(eventIDs));
        selectedEventIDs.setAll(eventIDs);
    }

    public void selectTimeAndType(Interval interval, TimelineEventType type) throws TskCoreException {
        final Interval timeRange = filteredEvents.getSpanningInterval().overlap(interval);

        final LoggedTask<Collection<Long>> selectTimeAndTypeTask = new LoggedTask<Collection<Long>>("Select Time and Type", true) { //NON-NLS
            @Override
            protected Collection< Long> call() throws Exception {
                synchronized (TimeLineController.this) {
                    return filteredEvents.getEventIDs(timeRange, new SqlFilterState<>(new EventTypeFilter(type), true));
                }
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                try {
                    synchronized (TimeLineController.this) {
                        selectedTimeRange.set(timeRange);
                        selectedEventIDs.setAll(get());

                    }
                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.SEVERE, getTitle() + " Unexpected error", ex); //NON-NLS
                }
            }
        };

        monitorTask(selectTimeAndTypeTask);
    }

    /**
     * submit a task for execution and add it to the list of tasks whose
     * progress is monitored and displayed in the progress bar
     *
     * @param task
     */
    synchronized public void monitorTask(final Task<?> task) {
        //TODO: refactor this to use JavaFX Service? -jm
        if (task != null) {
            Platform.runLater(() -> {

                //is this actually threadsafe, could we get a finished task stuck in the list?
                task.stateProperty().addListener((Observable observable) -> {
                    switch (task.getState()) {
                        case READY:
                        case RUNNING:
                        case SCHEDULED:
                            break;
                        case SUCCEEDED:
                        case CANCELLED:
                        case FAILED:
                            tasks.remove(task);
                            if (tasks.isEmpty() == false) {
                                taskProgress.bind(tasks.get(0).progressProperty());
                                taskMessage.bind(tasks.get(0).messageProperty());
                                taskTitle.bind(tasks.get(0).titleProperty());
                            }
                            break;
                    }
                });
                tasks.add(task);
                taskProgress.bind(task.progressProperty());
                taskMessage.bind(task.messageProperty());
                taskTitle.bind(task.titleProperty());
                switch (task.getState()) {
                    case READY:
                        //TODO: Check future result for errors....
                        executor.submit(task);
                        break;
                    case SCHEDULED:
                    case RUNNING:

                    case SUCCEEDED:
                    case CANCELLED:
                    case FAILED:
                        tasks.remove(task);
                        if (tasks.isEmpty() == false) {
                            taskProgress.bind(tasks.get(0).progressProperty());
                            taskMessage.bind(tasks.get(0).messageProperty());
                            taskTitle.bind(tasks.get(0).titleProperty());
                        }
                        break;
                }
            });
        }
    }

    /**
     * Register the given object to receive events.
     *
     * @param listener The object to register. Must implement public methods
     * annotated with Subscribe.
     */
    synchronized public void registerForEvents(Object listener) {
        eventbus.register(listener);
    }

    /**
     * Un-register the given object, so it no longer receives events.
     *
     * @param listener The object to un-register.
     */
    synchronized public void unRegisterForEvents(Object listener) {
        eventbus.unregister(listener);
    }

    static synchronized public void setTimeZone(TimeZone timeZone) {
        TimeLineController.timeZone.set(timeZone);

    }

    void handleIngestModuleEvent(PropertyChangeEvent evt) {
        /**
         * Checking for a current case is a stop gap measure until a different
         * way of handling the closing of cases is worked out. Currently, remote
         * events may be received for a case that is already closed.
         */
        try {
            Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException notUsed) {
            // Case is closed, do nothing.
            return;
        }
        // ignore remote events.  The node running the ingest should update the Case DB
        // @@@ We should signal though that there is more data and flush caches...
        if (((AutopsyEvent) evt).getSourceType() == AutopsyEvent.SourceType.REMOTE) {
            return;
        }

        switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
            case CONTENT_CHANGED:
                // new files were already added to the events table from SleuthkitCase.
                break;
            case DATA_ADDED:
                ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                if (null != eventData && eventData.getBlackboardArtifactType().getTypeID() == TSK_HASHSET_HIT.getTypeID()) {
                    logFutureException(executor.submit(() -> filteredEvents.updateEventsForHashSetHits(eventData.getArtifacts())),
                            "Error executing task in response to DATA_ADDED event.",
                            "Error executing response to new data.");
                }
                break;
            case FILE_DONE:
            /*
             * Since the known state or hash hit state may have changed
             * invalidate caches.
             */
            //@@@ This causes HUGE slow downs during ingest when TL is open.  
            // executor.submit(filteredEvents::invalidateAllCaches);

            // known state should have been udpated automatically via SleuthkitCase.setKnown();
            // hashes should have been updated from event
            }
    }

    void handleCaseEvent(PropertyChangeEvent evt) {
        ListenableFuture<?> future = Futures.immediateFuture(null);
        switch (Case.Events.valueOf(evt.getPropertyName())) {
            case BLACKBOARD_ARTIFACT_TAG_ADDED:
                future = executor.submit(() -> filteredEvents.handleArtifactTagAdded((BlackBoardArtifactTagAddedEvent) evt));
                break;
            case BLACKBOARD_ARTIFACT_TAG_DELETED:
                future = executor.submit(() -> filteredEvents.handleArtifactTagDeleted((BlackBoardArtifactTagDeletedEvent) evt));
                break;
            case CONTENT_TAG_ADDED:
                future = executor.submit(() -> filteredEvents.handleContentTagAdded((ContentTagAddedEvent) evt));
                break;
            case CONTENT_TAG_DELETED:
                future = executor.submit(() -> filteredEvents.handleContentTagDeleted((ContentTagDeletedEvent) evt));
                break;
            case DATA_SOURCE_ADDED:
                future = executor.submit(() -> {
                    filteredEvents.handleDataSourceAdded();
                    return null;
                });
                break;
            case TIMELINE_EVENT_ADDED:
                future = executor.submit(() -> {
                    filteredEvents.invalidateCaches(singleton(((TimelineEventAddedEvent) evt).getAddedEventID()));
                    return null;
                });
                break;
        }
        logFutureException(future,
                "Error executing task in response to " + evt.getPropertyName() + " event.",
                "Error executing task in response to case event.");
    }

    private void logFutureException(ListenableFuture<?> future, String errorLogMessage, String errorUserMessage) {
        future.addListener(() -> {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, errorLogMessage, ex);
            }
        }, MoreExecutors.directExecutor());
    }
}
