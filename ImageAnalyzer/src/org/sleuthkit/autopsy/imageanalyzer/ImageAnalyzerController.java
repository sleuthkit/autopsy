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

import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
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
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupManager;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupViewState;
import org.sleuthkit.autopsy.imageanalyzer.gui.NoGroupsDialog;
import org.sleuthkit.autopsy.imageanalyzer.gui.SummaryTablePane;
import org.sleuthkit.autopsy.imageanalyzer.gui.Toolbar;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Connects different parts of ImageAnalyzer together and is hub for flow of
 * control.
 */
public final class ImageAnalyzerController {

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
            new PerCaseProperties(Case.getCurrentCase()).setConfigSetting(ImageAnalyzerModule.MODULE_NAME, PerCaseProperties.STALE, b.toString());
        }
    }

    public ReadOnlyBooleanProperty stale() {
        return stale.getReadOnlyProperty();
    }

    boolean isStale() {
        return stale.get();
    }

    private ImageAnalyzerController() {

        listeningEnabled.addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            if (newValue && !oldValue && Case.existsCurrentCase() && ImageAnalyzerModule.isCaseStale(Case.getCurrentCase())) {
                queueDBWorkerTask(new CopyAnalyzedFiles());
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

        viewState().addListener((Observable observable) -> {
            selectionModel.clearSelection();
        });

        regroupDisabled.addListener((Observable observable) -> {
            checkForGroups();
        });

        IngestManager.getInstance().addIngestModuleEventListener((PropertyChangeEvent evt) -> {
            Platform.runLater(this::updateRegroupDisabled);
        });
        IngestManager.getInstance().addIngestJobEventListener((PropertyChangeEvent evt) -> {
            Platform.runLater(this::updateRegroupDisabled);
        });
//        metaDataCollapsed.bind(Toolbar.getDefault().showMetaDataProperty());
    }

    synchronized public ReadOnlyBooleanProperty getCanAdvance() {
        return historyManager.getCanAdvance();
    }

    synchronized public ReadOnlyBooleanProperty getCanRetreat() {
        return historyManager.getCanRetreat();
    }

    synchronized public void advance(GroupViewState newState) {
        historyManager.advance(newState);
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

        getFileUpdateQueueSizeProperty().addListener((Observable o) -> {
            Platform.runLater(this::updateRegroupDisabled);
        });

        Thread th = new Thread(dbWorkerThread);
        th.setDaemon(false); // we want it to go away when it is done
        th.start();
    }

    /**
     * onStart the controller for a specific case.
     *
     * @param c
     */
    public synchronized void setCase(Case c) {

        this.db = DrawableDB.getDrawableDB(c.getCaseDirectory(), this);

        setListeningEnabled(ImageAnalyzerModule.isEnabledforCase(c));
        setStale(ImageAnalyzerModule.isCaseStale(c));

        // if we add this line icons are made as files are analyzed rather than on demand.
        // db.addUpdatedFileListener(IconCache.getDefault());
        restartWorker();
        historyManager.clear();
        groupManager.setDB(db);
        SummaryTablePane.getDefault().handleCategoryChanged(Collections.emptyList());
    }

    /**
     * reset the state of the controller (eg if the case is closed)
     */
    public synchronized void reset() {
        LOGGER.info("resetting ImageAnalyzerControler to initial state.");
        selectionModel.clearSelection();
        Platform.runLater(() -> {
            historyManager.clear();
        });

        Toolbar.getDefault().reset();
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
    final void queueDBWorkerTask(InnerTask innerTask) {
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

    public ReadOnlyDoubleProperty regroupProgress() {
        return groupManager.regroupProgress();
    }

    /** invoked by {@link OnStart} to make sure that the ImageAnalyzer listeners
     * get setup as early as possible, and do other setup stuff. */
    void onStart() {
        Platform.setImplicitExit(false);
        LOGGER.info("setting up ImageAnalyzer listeners");
        //TODO can we do anything usefull in an InjestJobEventListener?
        //IngestManager.getInstance().addIngestJobEventListener((PropertyChangeEvent evt) -> {});
        IngestManager.getInstance().addIngestModuleEventListener((PropertyChangeEvent evt) -> {
            switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                case CONTENT_CHANGED:
                //TODO: do we need to do anything here?  -jm
                case DATA_ADDED:
                    /* we could listen to DATA events and progressivly
                     * update files, and get data from DataSource ingest
                     * modules, but given that most modules don't post new
                     * artifacts in the events and we would have to query for
                     * them, without knowing which are the new ones, we just
                     * ignore these events for now. The relevant data should all
                     * be captured by file done event, anyways -jm */
                    break;
                case FILE_DONE:
                    /** getOldValue has fileID
                     * getNewValue has {@link Abstractfile} */
                    AbstractFile file = (AbstractFile) evt.getNewValue();
                    if (isListeningEnabled()) {
                        if (ImageAnalyzerModule.isSupportedAndNotKnown(file)) {
                            //this file should be included and we don't already know about it from hash sets (NSRL)
                            queueDBWorkerTask(new UpdateFileTask(file));
                        } else if (ImageAnalyzerModule.getAllSupportedExtensions().contains(file.getNameExtension())) {
                            //doing this check results in fewer tasks queued up, and faster completion of db update
                            //this file would have gotten scooped up in initial grab, but actually we don't need it
                            queueDBWorkerTask(new RemoveFileTask(file));
                        }
                    } else {   //TODO: keep track of what we missed for later
                        setStale(true);
                    }
                    break;
            }
        });
        Case.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            switch (Case.Events.valueOf(evt.getPropertyName())) {
                case CURRENT_CASE:
                    Case newCase = (Case) evt.getNewValue();
                    if (newCase != null) { // case has been opened
                        setCase(newCase);    //connect db, groupmanager, start worker thread
                    } else { // case is closing
                        //close window, reset everything
                        SwingUtilities.invokeLater(ImageAnalyzerTopComponent::closeTopComponent);
                        reset();
                    }
                    break;
                case DATA_SOURCE_ADDED:
                    //copy all file data to drawable databse
                    Content newDataSource = (Content) evt.getNewValue();
                    if (isListeningEnabled()) {
                        queueDBWorkerTask(new PrePopulateDataSourceFiles(newDataSource.getId()));
                    } else {//TODO: keep track of what we missed for later
                        setStale(true);
                    }
                    break;
            }
        });
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
            Platform.runLater(() -> {
                queueSizeProperty.set(workQueue.size());
            });
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
                    
                    Platform.runLater(() -> {
                        queueSizeProperty.set(workQueue.size());
                    });

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
    static private abstract class InnerTask implements Runnable {

        public double getProgress() {
            return progress.get();
        }

        public final void updateProgress(Double workDone) {
            this.progress.set(workDone);
        }

        public String getMessage() {
            return message.get();
        }

        public final void updateMessage(String Status) {
            this.message.set(Status);
        }
        SimpleObjectProperty<Worker.State> state = new SimpleObjectProperty<>(Worker.State.READY);
        SimpleDoubleProperty progress = new SimpleDoubleProperty(this, "pregress");
        SimpleStringProperty message = new SimpleStringProperty(this, "status");

        public SimpleDoubleProperty progressProperty() {
            return progress;
        }

        public SimpleStringProperty messageProperty() {
            return message;
        }

        public Worker.State getState() {
            return state.get();
        }

        protected void updateState(Worker.State newState) {
            state.set(newState);
        }

        public ReadOnlyObjectProperty<Worker.State> stateProperty() {
            return new ReadOnlyObjectWrapper<>(state.get());
        }

        protected InnerTask() {
        }

        protected volatile boolean cancelled = false;

        public void cancel() {
            updateState(Worker.State.CANCELLED);
        }

        protected boolean isCancelled() {
            return getState() == Worker.State.CANCELLED;
        }
    }

    /**
     * Abstract base class for tasks associated with a file in the database
     */
    static private abstract class FileTask extends InnerTask {

        private final AbstractFile file;

        public AbstractFile getFile() {
            return file;
        }

        public FileTask(AbstractFile f) {
            super();
            this.file = f;
        }

    }

    /**
     * task that updates one file in database with results from ingest
     */
    private class UpdateFileTask extends FileTask {
        
        public UpdateFileTask(AbstractFile f) {
            super(f);
        }

        /**
         * Update a file in the database
         */
        @Override
        public void run() {
            DrawableFile<?> drawableFile = DrawableFile.create(getFile(), true);
            db.updateFile(drawableFile);
        }
    }

    /**
     * task that updates one file in database with results from ingest
     */
    private class RemoveFileTask extends FileTask {
        
        public RemoveFileTask(AbstractFile f) {
            super(f);
        }

        /**
         * Update a file in the database
         */
        @Override
        public void run() {
            db.removeFile(getFile().getId());
        }
    }

    /**
     * Task that runs when image analyzer listening is (re) enabled.
     *
     * Uses the presence of TSK_FILE_TYPE_SIG attributes as a approximation to
     * 'analyzed'. Grabs all files with supported image/video mime types, and
     * adds them to the Drawable DB
     */
    class CopyAnalyzedFiles extends InnerTask {

        final private String DRAWABLE_QUERY = "name LIKE '%." + StringUtils.join(ImageAnalyzerModule.getAllSupportedExtensions(), "' or name LIKE '%.") + "'";

        private ProgressHandle progressHandle = ProgressHandleFactory.createHandle("populating analyzed image/video database");

        @Override
        public void run() {
            progressHandle.start();
            updateMessage("populating analyzed image/video database");

            try {
                //grab all files with supported extension or mime types
                final List<AbstractFile> files = getSleuthKitCase().findAllFilesWhere(DRAWABLE_QUERY + " or tsk_files.obj_id in (select tsk_files.obj_id from tsk_files , blackboard_artifacts,  blackboard_attributes"
                        + " where  blackboard_artifacts.obj_id = tsk_files.obj_id"
                        + " and blackboard_attributes.artifact_id = blackboard_artifacts.artifact_id"
                        + " and blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID()
                        + " and blackboard_attributes.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID()
                        + " and blackboard_attributes.value_text in ('" + StringUtils.join(ImageAnalyzerModule.getSupportedMimes(), "','") + "'))");
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
                    final Boolean hasMimeType = ImageAnalyzerModule.hasSupportedMimeType(f);
                    final boolean known = f.getKnown() == TskData.FileKnown.KNOWN;

                    if (known) {
                        db.removeFile(f.getId(), tr);  //remove known files
                    } else {
                        if (hasMimeType == null) {
                            if (ImageAnalyzerModule.isSupported(f)) {
                                //no mime type but supported =>  add as not analyzed
                                db.insertFile(DrawableFile.create(f, false), tr);
                            } else {
                                //no mime type, not supported  => remove ( should never get here)
                                db.removeFile(f.getId(), tr);
                            }
                        } else {
                            if (hasMimeType) {  // supported mimetype => analyzed
                                db.updateFile(DrawableFile.create(f, true), tr);
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
     * netbeans and ImageAnalyzer progress/status
     */
    class PrePopulateDataSourceFiles extends InnerTask {

        private final Long id;    // id of image or file
        /**
         * here we grab by extension but in file_done listener we look at file
         * type id attributes but fall back on jpeg signatures and extensions to
         * check for supported images
         */
        // (name like '.jpg' or name like '.png' ...)
        private final String DRAWABLE_QUERY = "name LIKE '%." + StringUtils.join(ImageAnalyzerModule.getAllSupportedExtensions(), "' or name LIKE '%.") + "'";

        private ProgressHandle progressHandle = ProgressHandleFactory.createHandle("prepopulating image/video database");

        public PrePopulateDataSourceFiles(Long id) {
            super();
            this.id = id;

        }

        /**
         * Copy files from a newly added data source into the DB
         */
        @Override
        public void run() {
            progressHandle.start();
            updateMessage("prepopulating image/video database");

            /* Get all "drawable" files, based on extension. After ingest we use
             * file type id module and if necessary jpeg signature matching to
             * add/remove files */
            final List<AbstractFile> files;
            try {
                files = getSleuthKitCase().findAllFilesWhere(DRAWABLE_QUERY + "and fs_obj_id = " + this.id);
                progressHandle.switchToDeterminate(files.size());

                //do in transaction
                DrawableDB.DrawableTransaction tr = db.beginTransaction();
                int units = 0;
                for (final AbstractFile f : files) {
                    if (cancelled) {
                        LOGGER.log(Level.WARNING, "task cancelled: not all contents may be transfered to database");
                        progressHandle.finish();
                        break;
                    }
                    db.insertFile(DrawableFile.create(f, false), tr);
                    units++;
                    final int prog = units;
                    progressHandle.progress(f.getName(), units);
                }

                progressHandle.finish();
                progressHandle = ProgressHandleFactory.createHandle("commiting image/video database");

                progressHandle.start();
                db.commitTransaction(tr, false);

            } catch (TskCoreException ex) {
                Logger.getLogger(PrePopulateDataSourceFiles.class.getName()).log(Level.WARNING, "failed to transfer all database contents", ex);
            } catch (IllegalStateException ex) {
                Logger.getLogger(PrePopulateDataSourceFiles.class.getName()).log(Level.SEVERE, "Case was closed out from underneath CopyDataSource task", ex);
            }

            progressHandle.finish();
        }
    }

}
