/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import static javafx.concurrent.Worker.State.CANCELLED;
import static javafx.concurrent.Worker.State.FAILED;
import static javafx.concurrent.Worker.State.READY;
import static javafx.concurrent.Worker.State.RUNNING;
import static javafx.concurrent.Worker.State.SCHEDULED;
import static javafx.concurrent.Worker.State.SUCCEEDED;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javax.annotation.concurrent.GuardedBy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.Category;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupKey;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupManager;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupViewState;
import org.sleuthkit.autopsy.imageanalyzer.grouping.Grouping;
import org.sleuthkit.autopsy.imageanalyzer.gui.EurekaToolbar;
import org.sleuthkit.autopsy.imageanalyzer.gui.NoGroupsDialog;
import org.sleuthkit.autopsy.imageanalyzer.gui.SummaryTablePane;
import org.sleuthkit.autopsy.imageanalyzer.progress.ProgressAdapterBase;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Acts as the controller in GroupManager - GroupListPane -
 * ImageAnalyzerController MVC Trio
 *
 * Connects different parts of Eureka together and is hub for flow of control.
 */
public class ImageAnalyzerController implements FileUpdateListener {

    private static final Logger LOGGER = Logger.getLogger(ImageAnalyzerController.class.getName());

    private final Region infoOverLayBackground = new Region() {
        {
            setBackground(new Background(new BackgroundFill(Color.GREY, CornerRadii.EMPTY, Insets.EMPTY)));
            setOpacity(.4);
        }
    };

    private static ImageAnalyzerController instance;

    public static synchronized ImageAnalyzerController getDefault() {
        if (instance == null) {
            instance = new ImageAnalyzerController();
        }
        return instance;
    }

    @GuardedBy("this")
    private final History<GroupViewState> historyManager = new History<>();

    private final ReadOnlyBooleanWrapper listeningEnabled = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyIntegerWrapper queueSizeProperty = new ReadOnlyIntegerWrapper(0);

    private final ReadOnlyBooleanWrapper regroupDisabled = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyBooleanWrapper stale = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyBooleanWrapper metaDataCollapsed = new ReadOnlyBooleanWrapper(false);

    private final FileIDSelectionModel selectionModel = FileIDSelectionModel.getInstance();

    private DBWorkerThread dbWorkerThread;

    private DrawableDB db;

    private final GroupManager groupManager = new GroupManager(this);

    private StackPane fullUIStackPane;

    private StackPane centralStackPane;

    private Node infoOverlay;

    public ReadOnlyBooleanProperty getMetaDataCollapsed() {
        return metaDataCollapsed.getReadOnlyProperty();
    }

    public void setMetaDataCollapsed(Boolean metaDataCollapsed) {
        this.metaDataCollapsed.set(metaDataCollapsed);
    }

    private GroupViewState getViewState() {
        return historyManager.getCurrentState();
    }

    public ReadOnlyBooleanProperty regroupDisabled() {
        return regroupDisabled.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<GroupViewState> viewState() {
        return historyManager.currentState();
    }

    /**
     * the list of tasks queued to run in the uiBGTaskExecutor. By keeping this
     * list we can cancel them more gracefully than by {@link ExecutorService#shutdownNow()
     */
    @GuardedBy("bgTasks")
    private final SimpleListProperty<Future<?>> bgTasks = new SimpleListProperty<>(FXCollections.observableArrayList());

    /**
     * an executor to submit async ui related background tasks to.
     */
    final ExecutorService bgTaskExecutor = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("ui task -%d").build());

    public synchronized FileIDSelectionModel getSelectionModel() {

        return selectionModel;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public void setListeningEnabled(boolean enabled) {
        listeningEnabled.set(enabled);
    }

    ReadOnlyBooleanProperty listeningEnabled() {
        return listeningEnabled.getReadOnlyProperty();
    }

    boolean isListeningEnabled() {
        return listeningEnabled.get();
    }

    void setStale(Boolean b) {
        Platform.runLater(() -> {
            stale.set(b);
        });
        if (Case.isCaseOpen()) {
            new PerCaseProperties(Case.getCurrentCase()).setConfigSetting(EurekaModule.MODULE_NAME, PerCaseProperties.STALE, b.toString());
        }
    }

    public ReadOnlyBooleanProperty stale() {
        return stale.getReadOnlyProperty();
    }

    boolean isStale() {
        return stale.get();
    }

    private ImageAnalyzerController() {

        listeningEnabled.addListener((observable, oldValue, newValue) -> {
            if (newValue && !oldValue && Case.existsCurrentCase() && EurekaModule.isCaseStale(Case.getCurrentCase())) {
                queueTask(new CopyAnalyzedFiles());
            }
        });

        groupManager.getAnalyzedGroups().addListener((Observable o) -> {
            checkForGroups();
        });

        groupManager.getUnSeenGroups().addListener((Observable observable) -> {
            //if there are unseen groups and none being viewed
            if (groupManager.getUnSeenGroups().size() > 0 && (getViewState() == null || getViewState().getGroup() == null)) {
                advance(GroupViewState.tile(groupManager.getUnSeenGroups().get(0)));
            }
        });
        regroupDisabled.addListener((Observable observable) -> {
            checkForGroups();
        });

        IngestManager.getInstance().addIngestModuleEventListener((evt) -> {
            Platform.runLater(this::updateRegroupDisabled);
        });
        IngestManager.getInstance().addIngestJobEventListener((evt) -> {
            Platform.runLater(this::updateRegroupDisabled);
        });
//        metaDataCollapsed.bind(EurekaToolbar.getDefault().showMetaDataProperty());
    }

    /**
     * submit a background {@link Task} to be queued for execution by the thread
     * pool.
     *
     * @param task
     */
    @SuppressWarnings("fallthrough")
    public void submitBGTask(final Task<?> task) {
        //listen to task state and remove task from list of tasks once it is 'done'
        task.stateProperty().addListener((observableState, oldState, newState) -> {
            switch (newState) {
                case READY:
                case SCHEDULED:
                case RUNNING:
                    break;
                case FAILED:
                    LOGGER.log(Level.WARNING, "task :" + task.getTitle() + " failed", task.getException());
                case CANCELLED:
                case SUCCEEDED:
                    Platform.runLater(() -> {
                        synchronized (bgTasks) {
                            bgTasks.remove(task);
                        }
                    });
                    break;
            }
        });

        synchronized (bgTasks) {
            bgTasks.add(task);
        }

        bgTaskExecutor.execute(task);
    }

    synchronized public ReadOnlyBooleanProperty getCanAdvance() {
        return historyManager.getCanAdvance();
    }

    synchronized public ReadOnlyBooleanProperty getCanRetreat() {
        return historyManager.getCanRetreat();
    }

    synchronized public void advance(GroupViewState newState) {
        if (viewState().get() == null || (viewState().get().getGroup() != newState.getGroup())) {
            historyManager.advance(newState);
        }
    }

    synchronized public GroupViewState advance() {
        return historyManager.advance();
    }

    synchronized public GroupViewState retreat() {
        return historyManager.retreat();
    }

    private void updateRegroupDisabled() {
        regroupDisabled.set(getFileUpdateQueueSizeProperty().get() > 0 || IngestManager.getInstance().isIngestRunning());
    }

    /**
     * Check if there are any fully analyzed groups available from the
     * GroupManager and remove blocking progress spinners if there are. If there
     * aren't, add a blocking progress spinner with appropriate message.
     */
    public final void checkForGroups() {
        if (groupManager.getAnalyzedGroups().isEmpty()) {
            advance(null);
            if (IngestManager.getInstance().isIngestRunning()) {
                if (listeningEnabled.get() == false) {
                    replaceNotification(fullUIStackPane,
                            new NoGroupsDialog("No groups are fully analyzed but listening to ingest is disabled. "
                                    + " No groups will be available until ingest is finished and listening is re-enabled."));
                } else {
                    replaceNotification(fullUIStackPane,
                            new NoGroupsDialog("No groups are fully analyzed yet, but ingest is still ongoing.  Please Wait.",
                                    new ProgressIndicator()));
                }

            } else if (getFileUpdateQueueSizeProperty().get() > 0) {
                replaceNotification(fullUIStackPane,
                        new NoGroupsDialog("No groups are fully analyzed yet, but image / video data is still being populated.  Please Wait.",
                                new ProgressIndicator()));
            } else if (db != null && db.countAllFiles() <= 0) { // there are no files in db
                replaceNotification(fullUIStackPane,
                        new NoGroupsDialog("There are no images/videos in the added datasources."));

            } else if (!groupManager.isRegrouping()) {
                replaceNotification(centralStackPane,
                        new NoGroupsDialog("There are no fully analyzed groups to display:"
                                + "  the current Group By setting resulted in no groups, "
                                + "or no groups are fully analyzed but ingest is not running."));
            }
//            else {
//                replaceNotification(fullUIStackPane,
//                                    new NoGroupsDialog("Please wait while the images/videos are re grouped.",
//                                            new ProgressIndicator()));
//
//            }
            // }

        } else {
            clearNotification();
        }
    }

    private void clearNotification() {
        //remove the ingest spinner
        if (fullUIStackPane != null) {
            fullUIStackPane.getChildren().remove(infoOverlay);
        }
        //remove the ingest spinner
        if (centralStackPane != null) {
            centralStackPane.getChildren().remove(infoOverlay);
        }
    }

    private void replaceNotification(StackPane stackPane, Node newNode) {
        clearNotification();

        infoOverlay = new StackPane(infoOverLayBackground, newNode);
        if (stackPane != null) {
            stackPane.getChildren().add(infoOverlay);
        }
    }

    private void restartWorker() {
        if (dbWorkerThread != null) {
            dbWorkerThread.cancelAllTasks();
        }
        dbWorkerThread = new DBWorkerThread();

        getFileUpdateQueueSizeProperty().addListener((o) -> {
            Platform.runLater(this::updateRegroupDisabled);
        });

        Thread th = new Thread(dbWorkerThread);
        th.setDaemon(false); // we want it to go away when it is done
        th.start();
    }

    /**
     * initialize the controller for a specific case.
     *
     * @param c
     */
    public synchronized void setCase(Case c) {

        this.db = DrawableDB.getDrawableDB(c.getCaseDirectory(), this);
        db.addUpdatedFileListener(this);
        setListeningEnabled(EurekaModule.isEnabledforCase(c));
        setStale(EurekaModule.isCaseStale(c));

        // if we add this line icons are made as files are analyzed rather than on demand.
        // db.addUpdatedFileListener(IconCache.getDefault());
        restartWorker();

        groupManager.setDB(db);
        SummaryTablePane.getDefault().handleCategoryChanged(Collections.emptyList());
    }

    /**
     * handle {@link FileUpdateEvent} sent from Db when files are
     * inserted/updated
     *
     * @param evt
     */
    @Override
    synchronized public void handleFileUpdate(FileUpdateEvent evt) {
        final Collection<Long> fileIDs = evt.getUpdatedFiles();
        switch (evt.getUpdateType()) {
            case FILE_REMOVED:
                for (final long fileId : fileIDs) {
                    //get grouping(s) this file would be in
                    Set<GroupKey<?>> groupsForFile = groupManager.getGroupKeysForFileID(fileId);

                    for (GroupKey<?> gk : groupsForFile) {
                        groupManager.removeFromGroup(gk, fileId);
                    }
                }

                break;
            case FILE_UPDATED:

                /**
                 * TODO: is there a way to optimize this to avoid quering to db
                 * so much. the problem is that as a new files are analyzed they
                 * might be in new groups( if we are grouping by say make or
                 * model)
                 *
                 * TODO: Should this be a InnerTask so it can be done by the
                 * WorkerThread? Is it already done by worker thread because
                 * handlefileUpdate is invoked through call on db in UpdateTask
                 * innertask? -jm
                 */
                for (final long fileId : fileIDs) {

                    //get grouping(s) this file would be in
                    Set<GroupKey<?>> groupsForFile = groupManager.getGroupKeysForFileID(fileId);

                    for (GroupKey<?> gk : groupsForFile) {
                        Grouping g = groupManager.getGroupForKey(gk);

                        //if there is aleady a group that was previously deemed fully analyzed, then add this newly analyzed file to it.
                        if (g != null) {
                            g.addFile(fileId);
                        } ////if there wasn't already a group check if there should be one now
                        else {
                            //TODO: use method in groupmanager ?
                            List<Long> checkAnalyzed = groupManager.checkAnalyzed(gk);
                            if (checkAnalyzed != null) { // => the group is analyzed, so add it to the ui
                                groupManager.populateAnalyzedGroup(gk, checkAnalyzed);
                            }
                        }
                    }
                }

                Category.fireChange(fileIDs);
                if (evt.getChangedAttribute() == DrawableAttribute.TAGS) {
                    TagUtils.fireChange(fileIDs);
                }
                break;
        }
    }

    /**
     * reset the state of the controller (eg if the case is closed)
     */
    public synchronized void reset() {
        LOGGER.info("resetting EurekaControler to initial state.");
        selectionModel.clearSelection();
        Platform.runLater(() -> {
            historyManager.clear();
        });

        EurekaToolbar.getDefault().reset();
        groupManager.clear();
        if (db != null) {
            db.closeDBCon();
        }
        db = null;
    }

    /**
     * add InnerTask to the queue that the worker thread gets its work from
     *
     * @param innerTask
     */
    final void queueTask(InnerTask innerTask) {
        // @@@ We could make a lock for the worker thread
        if (dbWorkerThread == null) {
            restartWorker();
        }
        dbWorkerThread.addTask(innerTask);
    }

    public DrawableFile<?> getFileFromId(Long fileID) throws TskCoreException {
        return db.getFileFromID(fileID);
    }

    public void setStacks(StackPane fullUIStack, StackPane centralStack) {
        fullUIStackPane = fullUIStack;
        this.centralStackPane = centralStack;
        Platform.runLater(this::checkForGroups);
    }

    public final ReadOnlyIntegerProperty getFileUpdateQueueSizeProperty() {
        return queueSizeProperty.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty bgTaskQueueSizeProperty() {
        return bgTasks.sizeProperty();
    }

    // @@@ REVIEW IF THIS SHOLD BE STATIC...
    //TODO: concept seems like  the controller deal with how much work to do at a given time
    // @@@ review this class for synchronization issues (i.e. reset and cancel being called, add, etc.)
    private class DBWorkerThread implements Runnable {

        // true if the process was requested to stop.  Currently no way to reset it
        private volatile boolean cancelled = false;

        // list of tasks to run
        private final BlockingQueue<InnerTask> workQueue = new LinkedBlockingQueue<>();

        /**
         * Cancel all of the queued up tasks and the currently scheduled task.
         * Note that after you cancel, you cannot submit new jobs to this
         * thread.
         */
        public void cancelAllTasks() {
            cancelled = true;
            for (InnerTask it : workQueue) {
                it.cancel();
            }
            workQueue.clear();
            queueSizeProperty.set(workQueue.size());
        }

        /**
         * Add a task for the worker thread to perform
         *
         * @param it
         */
        public void addTask(InnerTask it) {
            workQueue.add(it);
            queueSizeProperty.set(workQueue.size());
        }

        @Override
        public void run() {
            // nearly infinite loop waiting for tasks
            while (true) {
                if (cancelled) {
                    return;
                }
                try {
                    // @@@ Could probably do something more fancy here and check if we've been canceled every now and then
                    InnerTask it = workQueue.take();
                    if (it.cancelled == false) {
                        it.run();
                    }

                    queueSizeProperty.set(workQueue.size());

                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }

            }
        }
    }

    public SleuthkitCase getSleuthKitCase() throws IllegalStateException {
        if (Case.isCaseOpen()) {
            return Case.getCurrentCase().getSleuthkitCase();
        } else {
            throw new IllegalStateException("No Case is open!");
        }
    }

    /**
     * Abstract base class for task to be done on {@link DBWorkerThread}
     */
    static public abstract class InnerTask extends ProgressAdapterBase implements Runnable {

        protected volatile boolean cancelled = false;

        public void cancel() {
            updateState(Worker.State.CANCELLED);
        }

        protected boolean isCancelled() {
            return getState() == Worker.State.CANCELLED;
        }
    }

    /**
     * Abstract base class for tasks associated with an obj id in the database
     */
    static private abstract class TaskWithID extends InnerTask {

        protected Long obj_id;    // id of image or file

        public TaskWithID(Long id) {
            super();
            this.obj_id = id;
        }

        public Long getId() {
            return obj_id;
        }
    }

    /**
     * Task to mark all unanalyzed files in the DB as analyzed. Just to make
     * sure that all are displayed. Added because there were rare cases where
     * something failed and a file was never marked as analyzed and therefore
     * never displayed. This task should go into the queue at the end after all
     * of the update tasks.
     */
    class MarkAllFilesAsAnalyzed extends InnerTask {

        @Override
        public void run() {
            db.markAllFilesAnalyzed();
//            checkForGroups();
        }
    }

    /**
     * task that updates one file in database with results from ingest
     */
    class UpdateFile extends InnerTask {

        private final AbstractFile file;

        public UpdateFile(AbstractFile f) {
            super();
            this.file = f;
        }

        /**
         * Update a file in the database
         */
        @Override
        public void run() {
            DrawableFile<?> drawableFile = DrawableFile.create(file, true);
            db.updateFile(drawableFile);
        }
    }

    /**
     * task that updates one file in database with results from ingest
     */
    class RemoveFile extends InnerTask {

        private final AbstractFile file;

        public RemoveFile(AbstractFile f) {
            super();
            this.file = f;
        }

        /**
         * Update a file in the database
         */
        @Override
        public void run() {
            boolean removeFile = db.removeFile(file.getId());
        }
    }

    /**
     * Task that runs when eureka listening is (re) enabled.
     *
     * Uses the presence of TSK_FILE_TYPE_SIG attributes as a approximation to
     * 'analyzed'. Grabs all files with supported image/video mime types, and
     * adds them to the Drawable DB
     */
    class CopyAnalyzedFiles extends InnerTask {

        final private String DRAWABLE_QUERY = "name LIKE '%." + StringUtils.join(EurekaModule.getAllSupportedExtensions(), "' or name LIKE '%.") + "'";

        private ProgressHandle progressHandle = ProgressHandleFactory.createHandle("populating analyzed image/video database");

        @Override
        public void run() {
            progressHandle.start();
            updateMessage("populating analyzed image/video database");

            try {
                //grap all files with supported mime types
                final List<AbstractFile> files = getSleuthKitCase().findAllFilesWhere(DRAWABLE_QUERY + " or tsk_files.obj_id in (select tsk_files.obj_id from tsk_files , blackboard_artifacts,  blackboard_attributes"
                        + " where  blackboard_artifacts.obj_id = tsk_files.obj_id"
                        + " and blackboard_attributes.artifact_id = blackboard_artifacts.artifact_id"
                        + " and blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID()
                        + " and blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID()
                        + " and blackboard_attributes.value_text in ('" + StringUtils.join(EurekaModule.getSupportedMimes(), "','") + "'))");
                progressHandle.switchToDeterminate(files.size());

                updateProgress(0.0);

                //do in transaction
                DrawableDB.DrawableTransaction tr = db.beginTransaction();
                int units = 0;
                for (final AbstractFile f : files) {
                    if (cancelled) {
                        LOGGER.log(Level.WARNING, "task cancelled: not all contents may be transfered to database");
                        progressHandle.finish();
                        break;
                    }
                    final Boolean hasMimeType = EurekaModule.hasSupportedMimeType(f);
                    final boolean known = f.getKnown() == TskData.FileKnown.KNOWN;

                    if (known) {
                        db.removeFile(f.getId(), tr);  //remove known files
                    } else {
                        if (hasMimeType == null) {
                            if (EurekaModule.isSupported(f)) {
                                //no mime type but supported => not add as not analyzed
                                db.updatefile(DrawableFile.create(f, false), tr);
                            } else {
                                //no mime type, not supported  => remove ( how dd we get here)
                                db.removeFile(f.getId(), tr);
                            }
                        } else {
                            if (hasMimeType) {  // supported mimetype => analyzed
                                db.updatefile(DrawableFile.create(f, true), tr);
                            } else { //unsupported mimtype => analyzed but shouldn't include
                                db.removeFile(f.getId(), tr);
                            }
                        }
                    }

                    units++;
                    final int prog = units;
                    progressHandle.progress(f.getName(), units);
                    updateProgress(prog - 1 / (double) files.size());
                    updateMessage(f.getName());
                }

                progressHandle.finish();

                progressHandle = ProgressHandleFactory.createHandle("commiting image/video database");
                updateMessage("commiting image/video database");
                updateProgress(1.0);

                progressHandle.start();
                db.commitTransaction(tr, true);

            } catch (TskCoreException ex) {
                Logger.getLogger(CopyAnalyzedFiles.class.getName()).log(Level.WARNING, "failed to transfer all database contents", ex);
            } catch (IllegalStateException ex) {
                Logger.getLogger(CopyAnalyzedFiles.class.getName()).log(Level.SEVERE, "Case was closed out from underneath CopyDataSource task", ex);
            }

            progressHandle.finish();

            updateMessage(
                    "");
            updateProgress(
                    -1.0);
            setStale(false);
        }

    }

    /**
     * task that does pre-ingest copy over of files from a new datasource with
     * (uses fs_obj_id to identify files from new datasource) *
     *
     * TODO: create methods to simplify progress value/text updates to both
     * netbeans and eureka progress/status
     */
    class PrePopulateDataSourceFiles extends TaskWithID {

        /**
         * @TODO: for initial grab is there any better way than by extension?
         *
         * in file_done listener we look at file type id attributes and fall
         * back on jpeg signatures and extensions to check for supported images
         */
        // (name like '.jpg' or name like '.png' ...)
        final private String DRAWABLE_QUERY = "name LIKE '%." + StringUtils.join(EurekaModule.getAllSupportedExtensions(), "' or name LIKE '%.") + "'";

        private ProgressHandle progressHandle = ProgressHandleFactory.createHandle("prepopulating image/video database");

        public PrePopulateDataSourceFiles(Long id) {
            super(id);
        }

        /**
         * Copy files from a newly added data source into the DB
         */
        @Override
        public void run() {
            progressHandle.start();
            updateMessage("prepopulating image/video database");

            /* Get all "drawable" files, based on extension. After ingest we
             * use
             * file type id module and if necessary jpeg signature matching
             * to
             * add remove files */
            final List<AbstractFile> files;
            try {
                files = getSleuthKitCase().findAllFilesWhere(DRAWABLE_QUERY + "and fs_obj_id = " + this.obj_id);
                progressHandle.switchToDeterminate(files.size());

//                updateProgress(0.0);
                //do in transaction
                DrawableDB.DrawableTransaction tr = db.beginTransaction();
                int units = 0;
                for (final AbstractFile f : files) {
                    if (cancelled) {
                        LOGGER.log(Level.WARNING, "task cancelled: not all contents may be transfered to database");
                        progressHandle.finish();
                        break;
                    }
                    db.updatefile(DrawableFile.create(f, false), tr);
                    units++;
                    final int prog = units;
                    progressHandle.progress(f.getName(), units);
//                    updateProgress(prog - 1 / (double) files.size());
//                    updateMessage(f.getName());
                }

                progressHandle.finish();
                progressHandle = ProgressHandleFactory.createHandle("commiting image/video database");
//                updateMessage("commiting image/video database");
//                updateProgress(1.0);

                progressHandle.start();
                db.commitTransaction(tr, false);

            } catch (TskCoreException ex) {
                Logger.getLogger(PrePopulateDataSourceFiles.class.getName()).log(Level.WARNING, "failed to transfer all database contents", ex);
            } catch (IllegalStateException ex) {
                Logger.getLogger(PrePopulateDataSourceFiles.class.getName()).log(Level.SEVERE, "Case was closed out from underneath CopyDataSource task", ex);
            }

            progressHandle.finish();

//            updateMessage("");
//            updateProgress(-1.0);
        }
    }
}
