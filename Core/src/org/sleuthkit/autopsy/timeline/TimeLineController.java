/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2016 Basis Technology Corp.
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

import java.awt.HeadlessException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.MissingResourceException;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
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
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.swing.SwingUtilities;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.ReadablePeriod;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import static org.sleuthkit.autopsy.casemodule.Case.Events.DATA_SOURCE_ADDED;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.db.EventsRepository;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

/**
 * Controller in the MVC design along with model = {@link FilteredEventsModel}
 * and views = {@link TimeLineView}. Forwards interpreted user gestures form
 * views to model. Provides model to view. Is entry point for timeline module.
 *
 * Concurrency Policy:<ul>
 * <li>Since filteredEvents is internally synchronized, only compound access to
 * it needs external synchronization</li>
 * * <li>Since eventsRepository is internally synchronized, only compound
 * access to it needs external synchronization <li>
 * <li>Other state including listeningToAutopsy, mainFrame, viewMode, and the
 * listeners should only be accessed with this object's intrinsic lock held, or
 * on the EDT as indicated.
 * </li>
 * <ul>
 */
@NbBundle.Messages({"Timeline.confirmation.dialogs.title=Update Timeline database?",
    "TimeLinecontroller.updateNowQuestion=Do you want to update the events database now?"})
public class TimeLineController {

    private static final Logger LOGGER = Logger.getLogger(TimeLineController.class.getName());

    private static final ReadOnlyObjectWrapper<TimeZone> timeZone = new ReadOnlyObjectWrapper<>(TimeZone.getDefault());

    public static ZoneId getTimeZoneID() {
        return timeZone.get().toZoneId();
    }

    public static DateTimeFormatter getZonedFormatter() {
        return DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss").withZone(getJodaTimeZone()); // NON-NLS //NOI18N
    }

    public static DateTimeZone getJodaTimeZone() {
        return DateTimeZone.forTimeZone(getTimeZone().get());
    }

    public static ReadOnlyObjectProperty<TimeZone> getTimeZone() {
        return timeZone.getReadOnlyProperty();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ReadOnlyListWrapper<Task<?>> tasks = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());

    private final ReadOnlyDoubleWrapper taskProgress = new ReadOnlyDoubleWrapper(-1);

    private final ReadOnlyStringWrapper taskMessage = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper taskTitle = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper();

    /**
     * status is a string that will be displayed in the status bar as a kind of
     * user hint/information when it is not empty
     *
     * @return the status property
     */
    public ReadOnlyStringProperty getStatusProperty() {
        return status.getReadOnlyProperty();
    }

    public void setStatus(String string) {
        status.set(string);
    }
    private final Case autoCase;
    private final PerCaseTimelineProperties perCaseTimelineProperties;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<DescriptionFilter> quickHideFilters = FXCollections.observableArrayList();

    public ObservableList<DescriptionFilter> getQuickHideFilters() {
        return quickHideFilters;
    }

    /**
     * @return the autopsy Case assigned to the controller
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
    private TimeLineTopComponent mainFrame;

    //are the listeners currently attached
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private boolean listeningToAutopsy = false;

    private final PropertyChangeListener caseListener = new AutopsyCaseListener();
    private final PropertyChangeListener ingestJobListener = new AutopsyIngestJobListener();
    private final PropertyChangeListener ingestModuleListener = new AutopsyIngestModuleListener();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<VisualizationMode> viewMode = new ReadOnlyObjectWrapper<>(VisualizationMode.COUNTS);

    synchronized public ReadOnlyObjectProperty<VisualizationMode> viewModeProperty() {
        return viewMode.getReadOnlyProperty();
    }

    @GuardedBy("filteredEvents")
    private final FilteredEventsModel filteredEvents;

    private final EventsRepository eventsRepository;

    @GuardedBy("this")
    private final ZoomParams InitialZoomState;

    @GuardedBy("this")
    private final History<ZoomParams> historyManager = new History<>();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<ZoomParams> currentParams = new ReadOnlyObjectWrapper<>();

    //selected events (ie shown in the result viewer)
    @GuardedBy("this")
    private final ObservableList<Long> selectedEventIDs = FXCollections.<Long>synchronizedObservableList(FXCollections.<Long>observableArrayList());

    /**
     * @return a list of the selected event ids
     */
    synchronized public ObservableList<Long> getSelectedEventIDs() {
        return selectedEventIDs;
    }

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<Interval> selectedTimeRange = new ReadOnlyObjectWrapper<>();

    /**
     * @return a read only view of the selected interval.
     */
    synchronized public ReadOnlyObjectProperty<Interval> getSelectedTimeRange() {
        return selectedTimeRange.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty eventsDBStaleProperty() {
        return eventsDBStale.getReadOnlyProperty();
    }

    synchronized public ReadOnlyBooleanProperty getCanAdvance() {
        return historyManager.getCanAdvance();
    }

    synchronized public ReadOnlyBooleanProperty getCanRetreat() {
        return historyManager.getCanRetreat();
    }
    private final ReadOnlyBooleanWrapper eventsDBStale = new ReadOnlyBooleanWrapper(true);

    private final PromptDialogManager promptDialogManager = new PromptDialogManager(this);

    public TimeLineController(Case autoCase) throws IOException {
        this.autoCase = autoCase;
        this.perCaseTimelineProperties = new PerCaseTimelineProperties(autoCase);
        eventsDBStale.set(perCaseTimelineProperties.isDBStale());
        eventsRepository = new EventsRepository(autoCase, currentParams.getReadOnlyProperty());
        /*
         * as the history manager's current state changes, modify the tags
         * filter to be in sync, and expose that as propery from
         * TimeLineController. Do we need to do this with datasource or hash hit
         * filters?
         */
        historyManager.currentState().addListener((Observable observable) -> {
            ZoomParams historyManagerParams = historyManager.getCurrentState();
            eventsRepository.syncTagsFilter(historyManagerParams.getFilter().getTagsFilter());
            currentParams.set(historyManagerParams);
        });
        filteredEvents = eventsRepository.getEventsModel();

        InitialZoomState = new ZoomParams(filteredEvents.getSpanningInterval(),
                EventTypeZoomLevel.BASE_TYPE,
                filteredEvents.filterProperty().get(),
                DescriptionLoD.SHORT);
        historyManager.advance(InitialZoomState);
    }

    /**
     * @return a shared events model
     */
    public FilteredEventsModel getEventsModel() {
        return filteredEvents;
    }

    public void applyDefaultFilters() {
        pushFilters(filteredEvents.getDefaultFilter());
    }

    public void zoomOutToActivity() {
        Interval boundingEventsInterval = filteredEvents.getBoundingEventsInterval();
        advance(filteredEvents.zoomParametersProperty().get().withTimeRange(boundingEventsInterval));
    }

    /**
     * rebuild the repo using the given repo builder (expected to be a member
     * reference to {@link EventsRepository#rebuildRepository(java.util.function.Consumer)
     * } or {@link EventsRepository#rebuildTags(java.util.function.Consumer) })
     * and display the ui when it is done.
     *
     * @param repoBuilder
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void rebuildRepoHelper(Function<Consumer<Worker.State>, CancellationProgressTask<?>> repoBuilder) {
        SwingUtilities.invokeLater(this::closeTimelineWindow);
        boolean ingestRunning = IngestManager.getInstance().isIngestRunning();
        final CancellationProgressTask<?> rebuildRepository = repoBuilder.apply(newSate -> {
            setIngestRunning(ingestRunning);
            //this will be on JFX thread
            switch (newSate) {
                case SUCCEEDED:
                    setEventsDBStale(false);
                    SwingUtilities.invokeLater(TimeLineController.this::showWindow);
                    historyManager.reset(filteredEvents.zoomParametersProperty().get());
                    TimeLineController.this.showFullRange();
                    break;
                case FAILED:
                case CANCELLED:
                    setEventsDBStale(true);
                    break;
            }
        });
        promptDialogManager.showProgressDialog(rebuildRepository);
    }

    /**
     * rebuld the entire repo.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void rebuildRepo() {
        rebuildRepoHelper(eventsRepository::rebuildRepository);
    }

    /**
     * Since tags might have changed while TimeLine wasn't listening, drop the
     * tags table and rebuild it by querying for all the tags and inserting them
     * in to the TimeLine DB.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void rebuildTagsTable() {
        rebuildRepoHelper(eventsRepository::rebuildTags);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void closeTimelineWindow() {
        if (isWindowOpen()) {
            mainFrame.close();
        }
    }

    public void showFullRange() {
        synchronized (filteredEvents) {
            pushTimeRange(filteredEvents.getSpanningInterval());
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void shutDownTimeLine() {
        if (mainFrame != null) {
            listeningToAutopsy = false;
            IngestManager.getInstance().removeIngestModuleEventListener(ingestModuleListener);
            IngestManager.getInstance().removeIngestJobEventListener(ingestJobListener);
            Case.removePropertyChangeListener(caseListener);
            mainFrame.close();
            mainFrame = null;
        }
    }

    /**
     * show the timeline window and prompt for rebuilding database if necessary.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void openTimeLine() {
        // listen for case changes (specifically images being added, and case changes).
        if (Case.isCaseOpen() && !listeningToAutopsy) {
            IngestManager.getInstance().addIngestModuleEventListener(ingestModuleListener);
            IngestManager.getInstance().addIngestJobEventListener(ingestJobListener);
            Case.addPropertyChangeListener(caseListener);
            listeningToAutopsy = true;
        }

        Platform.runLater(() -> {
            try {
                if (promptDialogManager.bringCurrentDialogToFront()) {
                    return;
                }
                if (IngestManager.getInstance().isIngestRunning()) {
                    //confirm timeline during ingest
                    if (promptDialogManager.confirmDuringIngest() == false) {
                        return;
                    }
                }

                /*
                 * if the repo was not rebuilt at minimum rebuild the tags which
                 * may have been updated without our knowing it, since we
                 * can't/aren't checking them. This should at least be quick.
                 * //TODO: can we check the tags to see if we need to do this?
                 */
                if (checkAndPromptForRebuild() == false) {
                    rebuildTagsTable();
                }

            } catch (HeadlessException | MissingResourceException ex) {
                LOGGER.log(Level.SEVERE, "Unexpected error when generating timeline, ", ex); // NON-NLS //NOI18N
            }
        });
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private boolean checkAndPromptForRebuild() {
        //if the repo is empty just (r)ebuild it with out asking,  they can always cancel part way through;
        if (eventsRepository.countAllEvents() == 0) {
            rebuildRepo();
            return true;
        }

        ArrayList<String> rebuildReasons = getRebuildReasons();
        if (rebuildReasons.isEmpty() == false) {
            if (promptDialogManager.confirmRebuild(rebuildReasons)) {
                rebuildRepo();
                return true;
            }
        }
        return false;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    @NbBundle.Messages({"TimeLineController.errorTitle=Timeline error.",
        "TimeLineController.outOfDate.errorMessage=Error determing if the timeline is out of date.  We will assume it should be updated.  See the logs for more details.",
        "TimeLineController.rebuildReasons.outOfDateError=Could not determine if the timeline data is out of date.",
        "TimeLineController.rebuildReasons.outOfDate=The event data is out of date:  Not all events will be visible.",
        "TimeLineController.rebuildReasons.ingestWasRunning=The Timeline events database was previously populated while ingest was running:  Some events may be missing, incomplete, or inaccurate.",
        "TimeLineController.rebuildReasons.incompleteOldSchema=The Timeline events database was previously populated without incomplete information:  Some features may be unavailable or non-functional unless you update the events database."})
    private ArrayList<String> getRebuildReasons() {
        ArrayList<String> rebuildReasons = new ArrayList<>();

        try {
            //if ingest was running during last rebuild, prompt to rebuild
            if (perCaseTimelineProperties.wasIngestRunning()) {
                rebuildReasons.add(Bundle.TimeLineController_rebuildReasons_ingestWasRunning());
            }

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error determing the state of the timeline db. We will assume the it is out of date.", ex); // NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.TimeLineController_errorTitle(),
                    Bundle.TimeLineController_outOfDate_errorMessage());
            rebuildReasons.add(Bundle.TimeLineController_rebuildReasons_outOfDateError());
        }
        //if the events db is stale, prompt to rebuild
        if (isEventsDBStale()) {
            rebuildReasons.add(Bundle.TimeLineController_rebuildReasons_outOfDate());
        }
        // if the TLDB schema has been upgraded since last time TL ran, prompt for rebuild
        if (eventsRepository.hasNewColumns() == false) {
            rebuildReasons.add(Bundle.TimeLineController_rebuildReasons_incompleteOldSchema());
        }
        return rebuildReasons;
    }

    /**
     * request a time range the same length as the given period and centered
     * around the middle of the currently selected range
     *
     * @param period
     */
    synchronized public void pushPeriod(ReadablePeriod period) {
        synchronized (filteredEvents) {
            final DateTime middleOf = IntervalUtils.middleOf(filteredEvents.timeRangeProperty().get());
            pushTimeRange(IntervalUtils.getIntervalAround(middleOf, period));
        }
    }

    synchronized public void pushZoomOutTime() {
        final Interval timeRange = filteredEvents.timeRangeProperty().get();
        long toDurationMillis = timeRange.toDurationMillis() / 4;
        DateTime start = timeRange.getStart().minus(toDurationMillis);
        DateTime end = timeRange.getEnd().plus(toDurationMillis);
        pushTimeRange(new Interval(start, end));
    }

    synchronized public void pushZoomInTime() {
        final Interval timeRange = filteredEvents.timeRangeProperty().get();
        long toDurationMillis = timeRange.toDurationMillis() / 4;
        DateTime start = timeRange.getStart().plus(toDurationMillis);
        DateTime end = timeRange.getEnd().minus(toDurationMillis);
        pushTimeRange(new Interval(start, end));
    }

    synchronized public void setViewMode(VisualizationMode visualizationMode) {
        if (viewMode.get() != visualizationMode) {
            viewMode.set(visualizationMode);
        }
    }

    public void selectEventIDs(Collection<Long> events) {
        final LoggedTask<Interval> selectEventIDsTask = new LoggedTask<Interval>("Select Event IDs", true) { // NON-NLS //NOI18N
            @Override
            protected Interval call() throws Exception {
                return filteredEvents.getSpanningInterval(events);
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                try {
                    synchronized (TimeLineController.this) {
                        selectedTimeRange.set(get());
                        selectedEventIDs.setAll(events);

                    }
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, getTitle() + " Unexpected error", ex); // NON-NLS //NOI18N
                }
            }
        };

        monitorTask(selectEventIDsTask);
    }

    /**
     * private method to build gui if necessary and make it visible.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    synchronized private void showWindow() {
        if (mainFrame == null) {
            mainFrame = new TimeLineTopComponent(this);
        }
        mainFrame.open();
        mainFrame.toFront();
    }

    synchronized public void pushEventTypeZoom(EventTypeZoomLevel typeZoomeLevel) {
        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withTypeZoomLevel(typeZoomeLevel));
        } else if (currentZoom.hasTypeZoomLevel(typeZoomeLevel) == false) {
            advance(currentZoom.withTypeZoomLevel(typeZoomeLevel));
        }
    }

    @SuppressWarnings("AssignmentToMethodParameter") //clamp timerange to case
    synchronized public boolean pushTimeRange(Interval timeRange) {
        timeRange = this.filteredEvents.getSpanningInterval().overlap(timeRange);
        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withTimeRange(timeRange));
            return true;
        } else if (currentZoom.hasTimeRange(timeRange) == false) {
            advance(currentZoom.withTimeRange(timeRange));
            return true;
        } else {
            return false;
        }
    }

    @NbBundle.Messages({"# {0} - the number of events",
        "Timeline.pushDescrLOD.confdlg.msg=You are about to show details for {0} events.  This might be very slow or even crash Autopsy.\n\nDo you want to continue?",
        "Timeline.pushDescrLOD.confdlg.title=Change description level of detail?"})
    synchronized public void pushDescrLOD(DescriptionLoD newLOD) {
        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withDescrLOD(newLOD));
        } else if (currentZoom.hasDescrLOD(newLOD) == false) {
            advance(currentZoom.withDescrLOD(newLOD));
        }
    }

    @SuppressWarnings("AssignmentToMethodParameter") //clamp timerange to case
    synchronized public void pushTimeAndType(Interval timeRange, EventTypeZoomLevel typeZoom) {
        timeRange = this.filteredEvents.getSpanningInterval().overlap(timeRange);
        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withTimeAndType(timeRange, typeZoom));
        } else if (currentZoom.hasTimeRange(timeRange) == false && currentZoom.hasTypeZoomLevel(typeZoom) == false) {
            advance(currentZoom.withTimeAndType(timeRange, typeZoom));
        } else if (currentZoom.hasTimeRange(timeRange) == false) {
            advance(currentZoom.withTimeRange(timeRange));
        } else if (currentZoom.hasTypeZoomLevel(typeZoom) == false) {
            advance(currentZoom.withTypeZoomLevel(typeZoom));
        }
    }

    synchronized public void pushFilters(RootFilter filter) {
        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withFilter(filter.copyOf()));
        } else if (currentZoom.hasFilter(filter) == false) {
            advance(currentZoom.withFilter(filter.copyOf()));
        }
    }

    synchronized public void advance() {
        historyManager.advance();
    }

    synchronized public void retreat() {
        historyManager.retreat();
    }

    synchronized private void advance(ZoomParams newState) {
        historyManager.advance(newState);
    }

    public void selectTimeAndType(Interval interval, EventType type) {
        final Interval timeRange = filteredEvents.getSpanningInterval().overlap(interval);

        final LoggedTask<Collection<Long>> selectTimeAndTypeTask = new LoggedTask<Collection<Long>>("Select Time and Type", true) { // NON-NLS //NOI18N
            @Override
            protected Collection< Long> call() throws Exception {
                synchronized (TimeLineController.this) {
                    return filteredEvents.getEventIDs(timeRange, new TypeFilter(type));
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
                    LOGGER.log(Level.SEVERE, getTitle() + " Unexpected error", ex); // NON-NLS //NOI18N
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

    static synchronized public void setTimeZone(TimeZone timeZone) {
        TimeLineController.timeZone.set(timeZone);
    }

    Interval getSpanningInterval(Collection<Long> eventIDs) {
        return filteredEvents.getSpanningInterval(eventIDs);

    }

    /**
     * is the timeline window open.
     *
     * @return true if the timeline window is open
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private boolean isWindowOpen() {
        return mainFrame != null && mainFrame.isOpened() && mainFrame.isVisible();
    }

    /**
     * prompt the user to rebuild the db because the db is out of date and
     * doesn't include things from subsequent ingests ONLY IF THE TIMELINE
     * WINDOW IS OPEN
     *
     * @return true if they agree to rebuild
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void confirmOutOfDateRebuildIfWindowOpen() throws MissingResourceException, HeadlessException {
        if (isWindowOpen()) {
            Platform.runLater(this::checkAndPromptForRebuild);

        }
    }

    /**
     * is the events db out of date
     *
     * @return true if the events db is out of date , false otherwise
     */
    public boolean isEventsDBStale() {
        return eventsDBStale.get();
    }

    /**
     *
     * @param stale the value of stale
     */
    private void setEventsDBStale(final Boolean stale) {
        eventsDBStale.set(stale);
        try {
            perCaseTimelineProperties.setDbStale(stale);
        } catch (IOException ex) {
            MessageNotifyUtil.Notify.error("Timeline", "Failed to mark the timeline db as " + (stale ? "" : "not ") + "stale. Some results may be out of date or missing.");
            LOGGER.log(Level.SEVERE, "Error marking the timeline db as stale.", ex);
        }
    }

    private void setIngestRunning(boolean ingestRunning) {
        try {
            perCaseTimelineProperties.setIngestRunning(ingestRunning);
        } catch (IOException ex) {
            MessageNotifyUtil.Notify.error("Timeline", "Failed to mark the timeline db as populated while ingest was" + (ingestRunning ? "" : "not ") + "running. Some results may be out of date or missing.");
            LOGGER.log(Level.SEVERE, "Error marking the ingest state while the timeline db was populated.", ex);
        }
    }

    private class AutopsyIngestModuleListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            /**
             * Checking for a current case is a stop gap measure until a
             * different way of handling the closing of cases is worked out.
             * Currently, remote events may be received for a case that is
             * already closed.
             */
            try {
                Case.getCurrentCase();
            } catch (IllegalStateException notUsed) {
                /**
                 * Case is closed, do nothing.
                 */
                return;
            }

            switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                case CONTENT_CHANGED:
                    break;
                case DATA_ADDED:
                case FILE_DONE:
                    Platform.runLater(() -> setEventsDBStale(true));
                    break;
            }
        }
    }

    @Immutable
    private class AutopsyIngestJobListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            switch (IngestManager.IngestJobEvent.valueOf(evt.getPropertyName())) {
                case CANCELLED:
                case COMPLETED:
                    SwingUtilities.invokeLater(TimeLineController.this::confirmOutOfDateRebuildIfWindowOpen);
            }
        }
    }

    @Immutable
    private class AutopsyCaseListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            switch (Case.Events.valueOf(evt.getPropertyName())) {
                case BLACKBOARD_ARTIFACT_TAG_ADDED:
                    executor.submit(() -> filteredEvents.handleArtifactTagAdded((BlackBoardArtifactTagAddedEvent) evt));
                    break;
                case BLACKBOARD_ARTIFACT_TAG_DELETED:
                    executor.submit(() -> filteredEvents.handleArtifactTagDeleted((BlackBoardArtifactTagDeletedEvent) evt));
                    break;
                case CONTENT_TAG_ADDED:
                    executor.submit(() -> filteredEvents.handleContentTagAdded((ContentTagAddedEvent) evt));
                    break;
                case CONTENT_TAG_DELETED:
                    executor.submit(() -> filteredEvents.handleContentTagDeleted((ContentTagDeletedEvent) evt));
                    break;
                case DATA_SOURCE_ADDED:
                    Platform.runLater(() -> {
                        setEventsDBStale(true);
                        SwingUtilities.invokeLater(TimeLineController.this::confirmOutOfDateRebuildIfWindowOpen);
                    });
                    break;
                case CURRENT_CASE:
                    OpenTimelineAction.invalidateController();
                    SwingUtilities.invokeLater(TimeLineController.this::shutDownTimeLine);
                    break;
            }
        }
    }
}
