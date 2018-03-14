/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import static javafx.concurrent.Worker.State.FAILED;
import static javafx.concurrent.Worker.State.SUCCEEDED;
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
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestJobEvent.CANCELLED;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.db.EventsRepository;
import org.sleuthkit.autopsy.timeline.events.ViewInTimelineRequestedEvent;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Controller in the MVC design along with FilteredEventsModel TimeLineView.
 * Forwards interpreted user gestures form views to model. Provides model to
 * view. Is entry point for timeline module.
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
 * </ul>
 */
@NbBundle.Messages({"Timeline.dialogs.title= Timeline",
    "TimeLinecontroller.updateNowQuestion=Do you want to update the events database now?"})
public class TimeLineController {

    private static final Logger LOGGER = Logger.getLogger(TimeLineController.class.getName());

    private static final ReadOnlyObjectWrapper<TimeZone> timeZone = new ReadOnlyObjectWrapper<>(TimeZone.getDefault());

    public static ZoneId getTimeZoneID() {
        return timeZone.get().toZoneId();
    }

    public static DateTimeFormatter getZonedFormatter() {
        return DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss").withZone(getJodaTimeZone()); //NON-NLS
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

    private final ReadOnlyStringWrapper statusMessage = new ReadOnlyStringWrapper();
    private EventBus eventbus = new EventBus("TimeLineController_EventBus");

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
    private final PerCaseTimelineProperties perCaseTimelineProperties;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<DescriptionFilter> quickHideFilters = FXCollections.observableArrayList();

    public ObservableList<DescriptionFilter> getQuickHideFilters() {
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

    //are the listeners currently attached
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private boolean listeningToAutopsy = false;

    private final PropertyChangeListener caseListener = new AutopsyCaseListener();
    private final PropertyChangeListener ingestJobListener = new AutopsyIngestJobListener();
    private final PropertyChangeListener ingestModuleListener = new AutopsyIngestModuleListener();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<ViewMode> viewMode = new ReadOnlyObjectWrapper<>(ViewMode.COUNTS);

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
    private final ObservableList<Long> selectedEventIDs = FXCollections.<Long>observableArrayList();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<Interval> selectedTimeRange = new ReadOnlyObjectWrapper<>();

    private final ReadOnlyBooleanWrapper eventsDBStale = new ReadOnlyBooleanWrapper(true);

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

    public ReadOnlyBooleanProperty eventsDBStaleProperty() {
        return eventsDBStale.getReadOnlyProperty();
    }

    /**
     * Is the events db out of date (stale)?
     *
     * @return True if the events db is out of date , false otherwise
     */
    public boolean isEventsDBStale() {
        return eventsDBStale.get();
    }

    /**
     * Set the events database stale or not
     *
     * @param stale The new state of the events db: stale/not-stale
     */
    @NbBundle.Messages({
        "TimeLineController.setEventsDBStale.errMsgStale=Failed to mark the timeline db as stale. Some results may be out of date or missing.",
        "TimeLineController.setEventsDBStale.errMsgNotStale=Failed to mark the timeline db as not stale. Some results may be out of date or missing."})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void setEventsDBStale(final Boolean stale) {
        eventsDBStale.set(stale);
        try {
            //persist to disk
            perCaseTimelineProperties.setDbStale(stale);
        } catch (IOException ex) {
            MessageNotifyUtil.Notify.error(Bundle.Timeline_dialogs_title(),
                    stale ? Bundle.TimeLineController_setEventsDBStale_errMsgStale()
                            : Bundle.TimeLineController_setEventsDBStale_errMsgNotStale());
            LOGGER.log(Level.SEVERE, "Error marking the timeline db as stale.", ex); //NON-NLS
        }
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

        //clear the selected events when the view mode changes
        viewMode.addListener(observable -> selectEventIDs(Collections.emptySet()));
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

    private final ObservableSet<TimeLineEvent> pinnedEvents = FXCollections.observableSet();
    private final ObservableSet<TimeLineEvent> pinnedEventsUnmodifiable = FXCollections.unmodifiableObservableSet(pinnedEvents);

    public void pinEvent(TimeLineEvent event) {
        pinnedEvents.add(event);
    }

    public void unPinEvent(TimeLineEvent event) {
        pinnedEvents.removeIf(event::equals);
    }

    public ObservableSet<TimeLineEvent> getPinnedEvents() {
        return pinnedEventsUnmodifiable;
    }

    /**
     * Rebuild the repo using the given repoBuilder (expected to be a member
     * reference to EventsRepository.rebuildRepository() or
     * EventsRepository.rebuildTags()) and display the UI when it is done. If
     * either file or artifact is not null the user will be prompted to choose a
     * derived event and time range to show in the Timeline List View.
     *
     * @param repoBuilder    A Function from Consumer<Worker.State> to
     *                       CancellationProgressTask<?>. Ie a function that
     *                       given a worker state listener, produces a task with
     *                       that listener attached. Expected to be a method
     *                       reference to either
     *                       EventsRepository.rebuildRepository() or
     *                       EventsRepository.rebuildTags()
     * @param markDBNotStale After the repo is rebuilt should it be marked not
     *                       stale
     * @param file           The AbstractFile from which to choose an event to
     *                       show in the List View.
     * @param artifact       The BlackboardArtifact to show in the List View.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({
        "TimeLineController.setIngestRunning.errMsgRunning=Failed to mark the timeline db as populated while ingest was running. Some results may be out of date or missing.",
        "TimeLinecontroller.setIngestRunning.errMsgNotRunning=Failed to mark the timeline db as populated while ingest was not running. Some results may be out of date or missing."})
    private void rebuildRepoHelper(Function<Consumer<Worker.State>, CancellationProgressTask<?>> repoBuilder, Boolean markDBNotStale, AbstractFile file, BlackboardArtifact artifact) {

        boolean ingestRunning = IngestManager.getInstance().isIngestRunning();
        //if there is an existing prompt or progressdialog, just show that
        if (promptDialogManager.bringCurrentDialogToFront()) {
            return;
        }

        //confirm timeline during ingest
        if (ingestRunning && promptDialogManager.confirmDuringIngest() == false) {
            return;  //if they cancel, do nothing.
        }

        //get a task that rebuilds the repo with the below state listener attached
        final CancellationProgressTask<?> rebuildRepositoryTask;
        rebuildRepositoryTask = repoBuilder.apply(new Consumer<Worker.State>() {
            @Override
            public void accept(Worker.State newSate) {
                //this will be on JFX thread
                switch (newSate) {
                    case SUCCEEDED:
                        /*
                         * Record if ingest was running the last time the db was
                         * rebuilt, and hence it might stale.
                         */
                        try {
                            perCaseTimelineProperties.setIngestRunning(ingestRunning);
                        } catch (IOException ex) {
                            MessageNotifyUtil.Notify.error(Bundle.Timeline_dialogs_title(),
                                    ingestRunning ? Bundle.TimeLineController_setIngestRunning_errMsgRunning()
                                            : Bundle.TimeLinecontroller_setIngestRunning_errMsgNotRunning());
                            LOGGER.log(Level.SEVERE, "Error marking the ingest state while the timeline db was populated.", ex); //NON-NLS
                        }
                        if (markDBNotStale) {
                            setEventsDBStale(false);
                            filteredEvents.postDBUpdated();
                        }
                        if (file == null && artifact == null) {
                            SwingUtilities.invokeLater(TimeLineController.this::showWindow);
                            TimeLineController.this.showFullRange();
                        } else {
                            //prompt user to pick specific event and time range
                            ShowInTimelineDialog showInTimelineDilaog =
                                    (file == null)
                                            ? new ShowInTimelineDialog(TimeLineController.this, artifact)
                                            : new ShowInTimelineDialog(TimeLineController.this, file);
                            Optional<ViewInTimelineRequestedEvent> dialogResult = showInTimelineDilaog.showAndWait();
                            dialogResult.ifPresent(viewInTimelineRequestedEvent -> {
                                SwingUtilities.invokeLater(TimeLineController.this::showWindow);
                                showInListView(viewInTimelineRequestedEvent); //show requested event in list view
                            });
                        }
                        break;
                    case FAILED:
                    case CANCELLED:
                        setEventsDBStale(true);
                        break;
                }
            }
        });

        /*
         * Since both of the expected repoBuilders start the back ground task,
         * all we have to do is show progress dialog for the task
         */
        promptDialogManager.showDBPopulationProgressDialog(rebuildRepositoryTask);
    }

    /**
     * Rebuild the entire repo in the background, and show the timeline when
     * done.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void rebuildRepo() {
        rebuildRepo(null, null);
    }

    /**
     * Rebuild the entire repo in the background, and show the timeline when
     * done.
     *
     * @param file     The AbstractFile from which to choose an event to show in
     *                 the List View.
     * @param artifact The BlackboardArtifact to show in the List View.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void rebuildRepo(AbstractFile file, BlackboardArtifact artifact) {
        rebuildRepoHelper(eventsRepository::rebuildRepository, true, file, artifact);
    }

    /**
     * Drop the tags table and rebuild it in the background, and show the
     * timeline when done.
     *
     * @param file     The AbstractFile from which to choose an event to show in
     *                 the List View.
     * @param artifact The BlackboardArtifact to show in the List View.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void rebuildTagsTable(AbstractFile file, BlackboardArtifact artifact) {
        rebuildRepoHelper(eventsRepository::rebuildTags, false, file, artifact);
    }

    /**
     * Show the entire range of the timeline.
     */
    private boolean showFullRange() {
        synchronized (filteredEvents) {
            return pushTimeRange(filteredEvents.getSpanningInterval());
        }
    }

    /**
     * Show the events and the amount of time indicated in the given
     * ViewInTimelineRequestedEvent in the List View.
     *
     * @param requestEvent Contains the ID of the requested events and the
     *                     timerange to show.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void showInListView(ViewInTimelineRequestedEvent requestEvent) {
        synchronized (filteredEvents) {
            setViewMode(ViewMode.LIST);
            selectEventIDs(requestEvent.getEventIDs());
            if (pushTimeRange(requestEvent.getInterval()) == false) {
                eventbus.post(requestEvent);
            }
        }
    }

    /**
     * "Shut down" Timeline. Remove all the case and ingest listers. Close the
     * timeline window.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void shutDownTimeLine() {
        listeningToAutopsy = false;
        IngestManager.getInstance().removeIngestModuleEventListener(ingestModuleListener);
        IngestManager.getInstance().removeIngestJobEventListener(ingestJobListener);
        Case.removePropertyChangeListener(caseListener);
        if (topComponent != null) {
            topComponent.close();
            topComponent = null;
        }
        OpenTimelineAction.invalidateController();
    }

    /**
     * Add the case and ingest listeners, prompt for rebuilding the database if
     * necessary, and show the timeline window.
     *
     * @param file     The AbstractFile from which to choose an event to show in
     *                 the List View.
     * @param artifact The BlackboardArtifact to show in the List View.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void showTimeLine(AbstractFile file, BlackboardArtifact artifact) {
        // listen for case changes (specifically images being added, and case changes).
        if (Case.isCaseOpen() && !listeningToAutopsy) {
            IngestManager.getInstance().addIngestModuleEventListener(ingestModuleListener);
            IngestManager.getInstance().addIngestJobEventListener(ingestJobListener);
            Case.addPropertyChangeListener(caseListener);
            listeningToAutopsy = true;
        }
        Platform.runLater(() -> promptForRebuild(file, artifact));
    }

    /**
     * Prompt the user to confirm rebuilding the db. Checks if a database
     * rebuild is necessary and includes the reasons in the prompt. If the user
     * confirms, rebuilds the database. Shows the timeline window when the
     * rebuild is done, or immediately if the rebuild is not confirmed.
     *
     * @param file     The AbstractFile from which to choose an event to show in
     *                 the List View.
     * @param artifact The BlackboardArtifact to show in the List View.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void promptForRebuild(AbstractFile file, BlackboardArtifact artifact) {
        //if there is an existing prompt or progressdialog, just show that
        if (promptDialogManager.bringCurrentDialogToFront()) {
            return;
        }

        //if the repo is empty just (re)build it with out asking, the user can always cancel part way through
        if (eventsRepository.countAllEvents() == 0) {
            rebuildRepo(file, artifact);
            return;
        }

        //if necessary prompt user with reasons to rebuild
        List<String> rebuildReasons = getRebuildReasons();
        if (false == rebuildReasons.isEmpty()) {
            if (promptDialogManager.confirmRebuild(rebuildReasons)) {
                rebuildRepo(file, artifact);
                return;
            }
        }

        /*
         * if the repo was not rebuilt, at a minimum rebuild the tags which may
         * have been updated without our knowing it, since we can't/aren't
         * checking them. This should at least be quick.
         *
         * //TODO: can we check the tags to see if we need to do this?
         */
        rebuildTagsTable(file, artifact);
    }

    /**
     * Get a list of reasons why the user might won't to rebuild the database.
     * The potential reasons are not necessarily orthogonal to each other.
     *
     * @return A list of reasons why the user might won't to rebuild the
     *         database.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    @NbBundle.Messages({"TimeLineController.errorTitle=Timeline error.",
        "TimeLineController.outOfDate.errorMessage=Error determing if the timeline is out of date.  We will assume it should be updated.  See the logs for more details.",
        "TimeLineController.rebuildReasons.outOfDateError=Could not determine if the timeline data is out of date.",
        "TimeLineController.rebuildReasons.outOfDate=The event data is out of date:  Not all events will be visible.",
        "TimeLineController.rebuildReasons.ingestWasRunning=The Timeline events database was previously populated while ingest was running:  Some events may be missing, incomplete, or inaccurate.",
        "TimeLineController.rebuildReasons.incompleteOldSchema=The Timeline events database was previously populated without incomplete information:  Some features may be unavailable or non-functional unless you update the events database."})
    private List<String> getRebuildReasons() {
        ArrayList<String> rebuildReasons = new ArrayList<>();

        try {
            //if ingest was running during last rebuild, prompt to rebuild
            if (perCaseTimelineProperties.wasIngestRunning()) {
                rebuildReasons.add(Bundle.TimeLineController_rebuildReasons_ingestWasRunning());
            }

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error determing the state of the timeline db. We will assume the it is out of date.", ex); //NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.TimeLineController_errorTitle(),
                    Bundle.TimeLineController_outOfDate_errorMessage());
            rebuildReasons.add(Bundle.TimeLineController_rebuildReasons_outOfDateError());
        }
        //if the events db is stale, prompt to rebuild
        if (isEventsDBStale()) {
            rebuildReasons.add(Bundle.TimeLineController_rebuildReasons_outOfDate());
        }
        // if the TL DB schema has been upgraded since last time TL ran, prompt for rebuild
        if (eventsRepository.hasNewColumns() == false) {
            rebuildReasons.add(Bundle.TimeLineController_rebuildReasons_incompleteOldSchema());
        }
        return rebuildReasons;
    }

    /**
     * Request a time range the same length as the given period and centered
     * around the middle of the currently viewed time range.
     *
     * @param period The period of time to show around the current center of the
     *               view.
     */
    synchronized public void pushPeriod(ReadablePeriod period) {
        synchronized (filteredEvents) {
            pushTimeRange(IntervalUtils.getIntervalAroundMiddle(filteredEvents.getTimeRange(), period));
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

    synchronized public void pushEventTypeZoom(EventTypeZoomLevel typeZoomeLevel) {
        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
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
     *         same as the existing one and no change happened.
     */
    synchronized public boolean pushTimeRange(Interval timeRange) {
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

        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
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
     */
    synchronized public boolean pushTimeUnit(TimeUnits timeUnit) {
        if (timeUnit == TimeUnits.FOREVER) {
            return showFullRange();
        } else {
            return pushTimeRange(IntervalUtils.getIntervalAroundMiddle(filteredEvents.getTimeRange(), timeUnit.getPeriod()));
        }
    }

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

    /**
     * Select the given event IDs and set their spanning interval as the
     * selected time range.
     *
     * @param eventIDs The eventIDs to select
     */
    synchronized public void selectEventIDs(Collection<Long> eventIDs) {
        selectedTimeRange.set(filteredEvents.getSpanningInterval(eventIDs));
        selectedEventIDs.setAll(eventIDs);
    }

    public void selectTimeAndType(Interval interval, EventType type) {
        final Interval timeRange = filteredEvents.getSpanningInterval().overlap(interval);

        final LoggedTask<Collection<Long>> selectTimeAndTypeTask = new LoggedTask<Collection<Long>>("Select Time and Type", true) { //NON-NLS
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
                    LOGGER.log(Level.SEVERE, getTitle() + " Unexpected error", ex); //NON-NLS
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

    /**
     * Register the given object to receive events.
     *
     * @param o The object to register. Must implement public methods annotated
     *          with Subscribe.
     */
    synchronized public void registerForEvents(Object o) {
        eventbus.register(o);
    }

    /**
     * Un-register the given object, so it no longer receives events.
     *
     * @param o The object to un-register.
     */
    synchronized public void unRegisterForEvents(Object o) {
        eventbus.unregister(0);
    }

    static synchronized public void setTimeZone(TimeZone timeZone) {
        TimeLineController.timeZone.set(timeZone);

    }

    /**
     * Listener for IngestManager.IngestModuleEvents.
     */
    @Immutable
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
                Case.getOpenCase();
            } catch (NoCurrentCaseException notUsed) {
                // Case is closed, do nothing.
                return;
            }

            switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                case CONTENT_CHANGED:
                case DATA_ADDED:
                    //since black board artifacts or new derived content have been added, the DB is stale.
                    Platform.runLater(() -> setEventsDBStale(true));
                    break;
                case FILE_DONE:
                    /*
                     * Do nothing, since we have captured all new results in
                     * CONTENT_CHANGED and DATA_ADDED or the IngestJob listener,
                     */
                    break;
            }
        }
    }

    /**
     * Listener for IngestManager.IngestJobEvents
     */
    @Immutable
    private class AutopsyIngestJobListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            switch (IngestManager.IngestJobEvent.valueOf(evt.getPropertyName())) {
                case DATA_SOURCE_ANALYSIS_COMPLETED:
                    //mark db stale, and prompt to rebuild
                    Platform.runLater(() -> setEventsDBStale(true));
                    filteredEvents.postAutopsyEventLocally((AutopsyEvent) evt);
                    break;
                case DATA_SOURCE_ANALYSIS_STARTED:
                case CANCELLED:
                case COMPLETED:
                case STARTED:
                    break;
            }
        }
    }

    /**
     * Listener for Case.Events
     */
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
                    //mark db stale, and prompt to rebuild
                    Platform.runLater(() -> setEventsDBStale(true));
                    filteredEvents.postAutopsyEventLocally((AutopsyEvent) evt);
                    break;
                case CURRENT_CASE:
                    //close timeline on case changes.
                    SwingUtilities.invokeLater(TimeLineController.this::shutDownTimeLine);
                    break;
            }
        }
    }
}
