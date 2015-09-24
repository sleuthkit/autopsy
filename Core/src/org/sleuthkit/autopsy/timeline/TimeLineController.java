/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
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
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.ReadablePeriod;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import static org.sleuthkit.autopsy.casemodule.Case.Events.DATA_SOURCE_ADDED;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.db.EventsRepository;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

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
 * listeners should only be accessed with this object's intrinsic lock held
 * </li>
 * <ul>
 */
@NbBundle.Messages({"Timeline.confirmation.dialogs.title=Update Timeline database?"})
public class TimeLineController {

    private static final Logger LOGGER = Logger.getLogger(TimeLineController.class.getName());

    private static final ReadOnlyObjectWrapper<TimeZone> timeZone = new ReadOnlyObjectWrapper<>(TimeZone.getDefault());

    public static ZoneId getTimeZoneID() {
        return timeZone.get().toZoneId();
    }

    public static DateTimeFormatter getZonedFormatter() {
        return DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss").withZone(getJodaTimeZone()); // NON-NLS
    }

    public static DateTimeZone getJodaTimeZone() {
        return DateTimeZone.forTimeZone(getTimeZone().get());
    }

    public static ReadOnlyObjectProperty<TimeZone> getTimeZone() {
        return timeZone.getReadOnlyProperty();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ReadOnlyListWrapper<Task<?>> tasks = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());

    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(-1);

    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper taskTitle = new ReadOnlyStringWrapper();

    private final Case autoCase;

    private final ObservableList<DescriptionFilter> quickHideMasks = FXCollections.observableArrayList();

    public ObservableList<DescriptionFilter> getQuickHideMasks() {
        return quickHideMasks;
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

    synchronized public ReadOnlyDoubleProperty getProgress() {
        return progress.getReadOnlyProperty();
    }

    synchronized public ReadOnlyStringProperty getMessage() {
        return message.getReadOnlyProperty();
    }

    synchronized public ReadOnlyStringProperty getTaskTitle() {
        return taskTitle.getReadOnlyProperty();
    }

    @GuardedBy("this")
    private TimeLineTopComponent mainFrame;

    //are the listeners currently attached
    @GuardedBy("this")
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

    @GuardedBy("eventsRepository")
    private final EventsRepository eventsRepository;

    @GuardedBy("this")
    private final ZoomParams InitialZoomState;

    @GuardedBy("this")
    private final History<ZoomParams> historyManager = new History<>();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<ZoomParams> currentParams = new ReadOnlyObjectWrapper<>();

    //all members should be access with the intrinsict lock of this object held
    //selected events (ie shown in the result viewer)
    @GuardedBy("this")
    private final ObservableList<Long> selectedEventIDs = FXCollections.<Long>synchronizedObservableList(FXCollections.<Long>observableArrayList());

    /**
     * @return an unmodifiable list of the selected event ids
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

    public ReadOnlyBooleanProperty getNewEventsFlag() {
        return newEventsFlag.getReadOnlyProperty();
    }

    private final ReadOnlyBooleanWrapper needsHistogramRebuild = new ReadOnlyBooleanWrapper(false);

    public ReadOnlyBooleanProperty getNeedsHistogramRebuild() {
        return needsHistogramRebuild.getReadOnlyProperty();
    }

    synchronized public ReadOnlyBooleanProperty getCanAdvance() {
        return historyManager.getCanAdvance();
    }

    synchronized public ReadOnlyBooleanProperty getCanRetreat() {
        return historyManager.getCanRetreat();
    }
    private final ReadOnlyBooleanWrapper newEventsFlag = new ReadOnlyBooleanWrapper(false);

    public TimeLineController(Case autoCase) {
        this.autoCase = autoCase;

        /*
         * as the history manager's current state changes, modify the tags
         * filter to be in sync, and expose that as propery from
         * TimeLineController. Do we need to do this with datasource or hash hit
         * filters?
         */
        historyManager.currentState().addListener(new InvalidationListener() {
            public void invalidated(Observable observable) {
                ZoomParams historyManagerParams = historyManager.getCurrentState();
                eventsRepository.syncTagsFilter(historyManagerParams.getFilter().getTagsFilter());
                currentParams.set(historyManagerParams);
            }
        });

        eventsRepository = new EventsRepository(autoCase, currentParams.getReadOnlyProperty());
        filteredEvents = eventsRepository.getEventsModel();

        InitialZoomState = new ZoomParams(filteredEvents.getSpanningInterval(),
                EventTypeZoomLevel.BASE_TYPE,
                filteredEvents.filterProperty().get(),
                DescriptionLOD.SHORT);
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
     * rebuld the repo.
     *
     * @return False if the repo was not rebuilt because of an error or because
     *         the user aborted after prompt about ingest running. True if the
     *         repo was rebuilt.
     */
    boolean rebuildRepo() {
        if (IngestManager.getInstance().isIngestRunning()) {
            //confirm timeline during ingest
            if (confirmRebuildDuringIngest() == false) {
                return false;
            }
        }
        LOGGER.log(Level.INFO, "Beginning generation of timeline"); // NON-NLS
        try {
            SwingUtilities.invokeLater(() -> {
                if (isWindowOpen()) {
                    mainFrame.close();
                }
            });
            final SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();
            final long lastObjId = sleuthkitCase.getLastObjectId();
            final long lastArtfID = getCaseLastArtifactID(sleuthkitCase);
            final Boolean injestRunning = IngestManager.getInstance().isIngestRunning();
            //TODO: verify this locking is correct? -jm
            synchronized (eventsRepository) {
                eventsRepository.rebuildRepository(() -> {
                    synchronized (eventsRepository) {
                        eventsRepository.recordLastObjID(lastObjId);
                        eventsRepository.recordLastArtifactID(lastArtfID);
                        eventsRepository.recordWasIngestRunning(injestRunning);
                    }
                    synchronized (TimeLineController.this) {
                        //TODO: this looks hacky.  what is going on? should this be an event?
                        needsHistogramRebuild.set(true);
                        needsHistogramRebuild.set(false);
                        showWindow();
                    }

                    Platform.runLater(() -> {
                        //TODO: should this be an event?
                        newEventsFlag.set(false);
                        historyManager.reset(filteredEvents.zoomParametersProperty().get());
                        TimeLineController.this.showFullRange();
                    });
                });
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error when generating timeline, ", ex); // NON-NLS
            return false;
        }
        return true;
    }

    /**
     * Since tags might have changed while TimeLine wasn't listening, drop the
     * tags table and rebuild it by querying for all the tags and inserting them
     * in to the TimeLine DB.
     */
    void rebuildTagsTable() {
        LOGGER.log(Level.INFO, "starting to rebuild tags table"); // NON-NLS
        SwingUtilities.invokeLater(() -> {
            if (isWindowOpen()) {
                mainFrame.close();
            }
        });
        synchronized (eventsRepository) {
            eventsRepository.rebuildTags(() -> {
                showWindow();
                Platform.runLater(() -> {
                    showFullRange();
                });
            });
        }
    }

    public void showFullRange() {
        synchronized (filteredEvents) {
            pushTimeRange(filteredEvents.getSpanningInterval());
        }
    }

    synchronized public void closeTimeLine() {
        if (mainFrame != null) {
            listeningToAutopsy = false;
            IngestManager.getInstance().removeIngestModuleEventListener(ingestModuleListener);
            IngestManager.getInstance().removeIngestJobEventListener(ingestJobListener);
            Case.removePropertyChangeListener(caseListener);
            mainFrame.close();
            mainFrame.setVisible(false);
            mainFrame = null;
        }
    }

    /**
     * show the timeline window and prompt for rebuilding database if necessary.
     */
    synchronized void openTimeLine() {
        // listen for case changes (specifically images being added, and case changes).
        if (Case.isCaseOpen() && !listeningToAutopsy) {
            IngestManager.getInstance().addIngestModuleEventListener(ingestModuleListener);
            IngestManager.getInstance().addIngestJobEventListener(ingestJobListener);
            Case.addPropertyChangeListener(caseListener);
            listeningToAutopsy = true;
        }

        try {
            boolean repoRebuilt = false;  //has the repo been rebuilt
            long timeLineLastObjectId = eventsRepository.getLastObjID();

            //if the repo is empty rebuild it
            if (timeLineLastObjectId == -1) {
                repoRebuilt = rebuildRepo();
            }

            if (repoRebuilt == false) {
                //if ingest was running uring last rebuild, prompt to rebuild
                if (eventsRepository.getWasIngestRunning()) {
                    if (confirmLastBuiltDuringIngestRebuild()) {
                        repoRebuilt = rebuildRepo();
                    }
                }
            }

            if (repoRebuilt == false) {
                final SleuthkitCase sleuthkitCase = autoCase.getSleuthkitCase();
                //if the last artifact and object ids don't match between skc and tldb, prompt to rebuild
                if (sleuthkitCase.getLastObjectId() != timeLineLastObjectId
                        || getCaseLastArtifactID(sleuthkitCase) != eventsRepository.getLastArtfactID()) {
                    if (confirmOutOfDateRebuild()) {
                        repoRebuilt = rebuildRepo();
                    }
                }
            }

            if (repoRebuilt == false) {
                // if the TLDB schema has been upgraded since last time TL ran, prompt for rebuild
                if (eventsRepository.hasNewColumns() == false) {
                    if (confirmDataSourceIDsMissingRebuild()) {
                        repoRebuilt = rebuildRepo();
                    }
                }
            }

            /*
             * if the repo was not rebuilt at minimum rebuild the tags which may
             * have been updated without our knowing it.
             */
            if (repoRebuilt == false) {
                rebuildTagsTable();
            }

        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error when generating timeline, ", ex); // NON-NLS
        } catch (HeadlessException | MissingResourceException ex) {
            LOGGER.log(Level.SEVERE, "Unexpected error when generating timeline, ", ex); // NON-NLS
        }
    }

    private long getCaseLastArtifactID(final SleuthkitCase sleuthkitCase) {
        long caseLastArtfId = -1;
        String query = "select Max(artifact_id) as max_id from blackboard_artifacts"; // NON-NLS

        try (CaseDbQuery dbQuery = sleuthkitCase.executeQuery(query)) {
            ResultSet resultSet = dbQuery.getResultSet();
            while (resultSet.next()) {
                caseLastArtfId = resultSet.getLong("max_id"); // NON-NLS
            }
        } catch (TskCoreException | SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error getting last artifact id: ", ex); // NON-NLS
        }
        return caseLastArtfId;
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
        final LoggedTask<Interval> selectEventIDsTask = new LoggedTask<Interval>("Select Event IDs", true) { // NON-NLS
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
                } catch (InterruptedException ex) {
                    Logger.getLogger(FilteredEventsModel.class
                            .getName()).log(Level.SEVERE, getTitle() + " interrupted unexpectedly", ex); // NON-NLS
                } catch (ExecutionException ex) {
                    Logger.getLogger(FilteredEventsModel.class
                            .getName()).log(Level.SEVERE, getTitle() + " unexpectedly threw " + ex.getCause(), ex); // NON-NLS
                }
            }
        };

        monitorTask(selectEventIDsTask);
    }

    /**
     * private method to build gui if necessary and make it visible.
     */
    synchronized private void showWindow() {
        if (mainFrame == null) {
            LOGGER.log(Level.WARNING, "Tried to show timeline with invalid window. Rebuilding GUI."); // NON-NLS
            mainFrame = (TimeLineTopComponent) WindowManager.getDefault().findTopComponent(
                    NbBundle.getMessage(TimeLineController.class, "CTL_TimeLineTopComponentAction"));
            if (mainFrame == null) {
                mainFrame = new TimeLineTopComponent();
            }
            mainFrame.setController(this);
        }
        SwingUtilities.invokeLater(() -> {
            mainFrame.open();
            mainFrame.setVisible(true);
            mainFrame.toFront();
        });
    }

    synchronized public void pushEventTypeZoom(EventTypeZoomLevel typeZoomeLevel) {
        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withTypeZoomLevel(typeZoomeLevel));
        } else if (currentZoom.hasTypeZoomLevel(typeZoomeLevel) == false) {
            advance(currentZoom.withTypeZoomLevel(typeZoomeLevel));
        }
    }

    synchronized public void pushTimeRange(Interval timeRange) {
//        timeRange = this.filteredEvents.getSpanningInterval().overlap(timeRange);
        ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
        if (currentZoom == null) {
            advance(InitialZoomState.withTimeRange(timeRange));
        } else if (currentZoom.hasTimeRange(timeRange) == false) {
            advance(currentZoom.withTimeRange(timeRange));
        }
    }

    @NbBundle.Messages({"# {0} - the number of events",
        "Timeline.pushDescrLOD.confdlg.msg=You are about to show details for {0} events."
        + " This might be very slow or even crash Autopsy.\n\nDo you want to continue?"})
    synchronized public boolean pushDescrLOD(DescriptionLOD newLOD) {
        Map<EventType, Long> eventCounts = filteredEvents.getEventCounts(filteredEvents.zoomParametersProperty().get().getTimeRange());
        final Long count = eventCounts.values().stream().reduce(0l, Long::sum);

        boolean shouldContinue = true;
        if (newLOD == DescriptionLOD.FULL && count > 10_000) {
            String format = NumberFormat.getInstance().format(count);

            int showConfirmDialog = JOptionPane.showConfirmDialog(mainFrame,
                    Bundle.Timeline_pushDescrLOD_confdlg_msg(format),
                    Bundle.Timeline_confirmation_dialogs_title(),
                    JOptionPane.YES_NO_OPTION);

            shouldContinue = (showConfirmDialog == JOptionPane.YES_OPTION);
        }

        if (shouldContinue) {
            ZoomParams currentZoom = filteredEvents.zoomParametersProperty().get();
            if (currentZoom == null) {
                advance(InitialZoomState.withDescrLOD(newLOD));
            } else if (currentZoom.hasDescrLOD(newLOD) == false) {
                advance(currentZoom.withDescrLOD(newLOD));
            }
        }
        return shouldContinue;
    }

    synchronized public void pushTimeAndType(Interval timeRange, EventTypeZoomLevel typeZoom) {
//        timeRange = this.filteredEvents.getSpanningInterval().overlap(timeRange);
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

        final LoggedTask<Collection<Long>> selectTimeAndTypeTask = new LoggedTask<Collection<Long>>("Select Time and Type", true) { // NON-NLS
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
                } catch (InterruptedException ex) {
                    Logger.getLogger(FilteredEventsModel.class
                            .getName()).log(Level.SEVERE, getTitle() + " interrupted unexpectedly", ex);// NON-NLS
                } catch (ExecutionException ex) {
                    Logger.getLogger(FilteredEventsModel.class
                            .getName()).log(Level.SEVERE, getTitle() + " unexpectedly threw " + ex.getCause(), ex);// NON-NLS
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
                                progress.bind(tasks.get(0).progressProperty());
                                message.bind(tasks.get(0).messageProperty());
                                taskTitle.bind(tasks.get(0).titleProperty());
                            }
                            break;
                    }
                });
                tasks.add(task);
                progress.bind(task.progressProperty());
                message.bind(task.messageProperty());
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
                            progress.bind(tasks.get(0).progressProperty());
                            message.bind(tasks.get(0).messageProperty());
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
    synchronized private boolean isWindowOpen() {
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
            if (confirmOutOfDateRebuild()) {
                rebuildRepo();
            }
        }
    }

    /**
     * prompt the user to rebuild the db because that datasource_ids are missing
     * from the database and that the datasource filter will not work
     *
     * @return true if they agree to rebuild
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @NbBundle.Messages({"datasource.missing.confirmation=The Timeline events database was previously populated with an old version of Autopsy."
        + "\nThe data source filter will be unavailable unless you update the events database."
        + "\nDo you want to update the events database now?"})
    synchronized boolean confirmDataSourceIDsMissingRebuild() {
        return JOptionPane.showConfirmDialog(mainFrame,
                Bundle.datasource_missing_confirmation(),
                Bundle.Timeline_confirmation_dialogs_title(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * prompt the user to rebuild the db because the db was last build during
     * ingest and may be incomplete
     *
     * @return true if they agree to rebuild
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @NbBundle.Messages({"Timeline.do_repopulate.msg=The Timeline events database was previously populated while ingest was running."
        + "\nSome events may not have been populated or may have been populated inaccurately."
        + "\nDo you want to repopulate the events database now?"})
    synchronized boolean confirmLastBuiltDuringIngestRebuild() {
        return JOptionPane.showConfirmDialog(mainFrame,
                Bundle.Timeline_do_repopulate_msg(),
                Bundle.Timeline_confirmation_dialogs_title(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * prompt the user to rebuild the db because the db is out of date and
     * doesn't include things from subsequent ingests
     *
     * @return true if they agree to rebuild
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @NbBundle.Messages({"Timeline.propChg.confDlg.timelineOOD.msg=The event data is out of date. Would you like to regenerate it?",})
    synchronized boolean confirmOutOfDateRebuild() throws MissingResourceException, HeadlessException {
        return JOptionPane.showConfirmDialog(mainFrame,
                Bundle.Timeline_propChg_confDlg_timelineOOD_msg(),
                Bundle.Timeline_confirmation_dialogs_title(),
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    /**
     * prompt the user that ingest is running and the db may not end up
     * complete.
     *
     * @return true if they want to continue anyways
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @NbBundle.Messages({"Timeline.initTimeline.confDlg.genBeforeIngest.msg=You are trying to generate a timeline before ingest has been completed. "
        + "The timeline may be incomplete. Do you want to continue?"})
    synchronized boolean confirmRebuildDuringIngest() throws MissingResourceException, HeadlessException {
        return JOptionPane.showConfirmDialog(mainFrame,
                Bundle.Timeline_initTimeline_confDlg_genBeforeIngest_msg(),
                Bundle.Timeline_confirmation_dialogs_title(),
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private class AutopsyIngestModuleListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                case CONTENT_CHANGED:
                case DATA_ADDED:
                    break;
                case FILE_DONE:
                    Platform.runLater(() -> {
                        newEventsFlag.set(true);
                    });
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
                    executor.submit(() -> {
                        filteredEvents.handleArtifactTagAdded((BlackBoardArtifactTagAddedEvent) evt);
                    });
                    break;
                case BLACKBOARD_ARTIFACT_TAG_DELETED:
                    executor.submit(() -> {
                        filteredEvents.handleArtifactTagDeleted((BlackBoardArtifactTagDeletedEvent) evt);
                    });
                    break;
                case CONTENT_TAG_ADDED:
                    executor.submit(() -> {
                        filteredEvents.handleContentTagAdded((ContentTagAddedEvent) evt);
                    });
                    break;
                case CONTENT_TAG_DELETED:
                    executor.submit(() -> {
                        filteredEvents.handleContentTagDeleted((ContentTagDeletedEvent) evt);
                    });
                    break;
                case DATA_SOURCE_ADDED:
                    SwingUtilities.invokeLater(TimeLineController.this::confirmOutOfDateRebuildIfWindowOpen);
                    break;
                case CURRENT_CASE:
                    OpenTimelineAction.invalidateController();
                    SwingUtilities.invokeLater(TimeLineController.this::closeTimeLine);
                    break;
            }
        }
    }
}
