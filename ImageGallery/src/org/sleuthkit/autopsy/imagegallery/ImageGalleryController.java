/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.actions.UndoRedoManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.HashSetManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;
import org.sleuthkit.autopsy.imagegallery.gui.NoGroupsDialog;
import org.sleuthkit.autopsy.imagegallery.gui.Toolbar;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Connects different parts of ImageGallery together and is hub for flow of
 * control.
 */
public final class ImageGalleryController {

    private static final Logger LOGGER = Logger.getLogger(ImageGalleryController.class.getName());
    private static ImageGalleryController instance;

    /**
     * true if Image Gallery should listen to ingest events, false if it should
     * not listen to speed up ingest
     */
    private final SimpleBooleanProperty listeningEnabled = new SimpleBooleanProperty(false);

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ReadOnlyBooleanWrapper stale = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyBooleanWrapper metaDataCollapsed = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyDoubleWrapper thumbnailSize = new ReadOnlyDoubleWrapper(100);
    private final ReadOnlyBooleanWrapper regroupDisabled = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyIntegerWrapper dbTaskQueueSize = new ReadOnlyIntegerWrapper(0);

    private final FileIDSelectionModel selectionModel = new FileIDSelectionModel(this);

    private final History<GroupViewState> historyManager = new History<>();
    private final UndoRedoManager undoManager = new UndoRedoManager();
    private final GroupManager groupManager = new GroupManager(this);
    private final HashSetManager hashSetManager = new HashSetManager();
    private final CategoryManager categoryManager = new CategoryManager(this);
    private final DrawableTagsManager tagsManager = new DrawableTagsManager(null);

    private Runnable showTree;
    private Toolbar toolbar;
    private StackPane fullUIStackPane;
    private StackPane centralStackPane;
    private Node infoOverlay;
    private final Region infoOverLayBackground = new Region() {
        {
            setBackground(new Background(new BackgroundFill(Color.GREY, CornerRadii.EMPTY, Insets.EMPTY)));
            setOpacity(.4);
        }
    };

    private ListeningExecutorService dbExecutor;

    private SleuthkitCase sleuthKitCase;
    private DrawableDB db;

    public static synchronized ImageGalleryController getDefault() {
        if (instance == null) {
            instance = new ImageGalleryController();
        }
        return instance;
    }

    public ReadOnlyBooleanProperty getMetaDataCollapsed() {
        return metaDataCollapsed.getReadOnlyProperty();
    }

    public void setMetaDataCollapsed(Boolean metaDataCollapsed) {
        this.metaDataCollapsed.set(metaDataCollapsed);
    }

    public ReadOnlyDoubleProperty thumbnailSizeProperty() {
        return thumbnailSize.getReadOnlyProperty();
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

    public FileIDSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    synchronized public DrawableDB getDatabase() {
        return db;
    }

    public void setListeningEnabled(boolean enabled) {
        synchronized (listeningEnabled) {
            listeningEnabled.set(enabled);
        }
    }

    boolean isListeningEnabled() {
        synchronized (listeningEnabled) {
            return listeningEnabled.get();
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    void setStale(Boolean b) {
        Platform.runLater(() -> {
            stale.set(b);
        });
        try {
            new PerCaseProperties(Case.getOpenCase()).setConfigSetting(ImageGalleryModule.getModuleName(), PerCaseProperties.STALE, b.toString());
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(ImageGalleryController.class.getName()).log(Level.WARNING, "Exception while getting open case."); //NON-NLS
        }
    }

    public ReadOnlyBooleanProperty stale() {
        return stale.getReadOnlyProperty();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean isStale() {
        return stale.get();
    }

    private ImageGalleryController() {

        listeningEnabled.addListener((observable, oldValue, newValue) -> {
            try {
                //if we just turned on listening and a case is open and that case is not up to date
                if (newValue && !oldValue && ImageGalleryModule.isDrawableDBStale(Case.getOpenCase())) {
                    //populate the db
                    queueDBTask(new CopyAnalyzedFiles(instance, db, sleuthKitCase));
                }
            } catch (NoCurrentCaseException ex) {
                LOGGER.log(Level.WARNING, "Exception while getting open case.", ex);
            }
        });

        groupManager.getAnalyzedGroups().addListener((Observable o) -> {
            //analyzed groups is confined  to JFX thread
            if (Case.isCaseOpen()) {
                checkForGroups();
            }
        });

        groupManager.getUnSeenGroups().addListener((Observable observable) -> {
            //if there are unseen groups and none being viewed
            if (groupManager.getUnSeenGroups().isEmpty() == false && (getViewState() == null || getViewState().getGroup() == null)) {
                advance(GroupViewState.tile(groupManager.getUnSeenGroups().get(0)), true);
            }
        });

        viewState().addListener((Observable observable) -> {
            //when the viewed group changes, clear the selection and the undo/redo history
            selectionModel.clearSelection();
            undoManager.clear();
        });

        regroupDisabled.addListener(observable -> checkForGroups());

        IngestManager ingestManager = IngestManager.getInstance();
        PropertyChangeListener ingestEventHandler =
                propertyChangeEvent -> Platform.runLater(this::updateRegroupDisabled);

        ingestManager.addIngestModuleEventListener(ingestEventHandler);
        ingestManager.addIngestJobEventListener(ingestEventHandler);

        dbTaskQueueSize.addListener(obs -> this.updateRegroupDisabled());
    }

    public ReadOnlyBooleanProperty getCanAdvance() {
        return historyManager.getCanAdvance();
    }

    public ReadOnlyBooleanProperty getCanRetreat() {
        return historyManager.getCanRetreat();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    public void advance(GroupViewState newState, boolean forceShowTree) {
        if (forceShowTree && showTree != null) {
            showTree.run();
        }
        historyManager.advance(newState);
    }

    public GroupViewState advance() {
        return historyManager.advance();
    }

    public GroupViewState retreat() {
        return historyManager.retreat();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void updateRegroupDisabled() {
        regroupDisabled.set((dbTaskQueueSize.get() > 0) || IngestManager.getInstance().isIngestRunning());
    }

    /**
     * Check if there are any fully analyzed groups available from the
     * GroupManager and remove blocking progress spinners if there are. If there
     * aren't, add a blocking progress spinner with appropriate message.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({"ImageGalleryController.noGroupsDlg.msg1=No groups are fully analyzed; but listening to ingest is disabled. "
        + " No groups will be available until ingest is finished and listening is re-enabled.",
        "ImageGalleryController.noGroupsDlg.msg2=No groups are fully analyzed yet, but ingest is still ongoing.  Please Wait.",
        "ImageGalleryController.noGroupsDlg.msg3=No groups are fully analyzed yet, but image / video data is still being populated.  Please Wait.",
        "ImageGalleryController.noGroupsDlg.msg4=There are no images/videos available from the added datasources;  but listening to ingest is disabled. "
        + " No groups will be available until ingest is finished and listening is re-enabled.",
        "ImageGalleryController.noGroupsDlg.msg5=There are no images/videos in the added datasources.",
        "ImageGalleryController.noGroupsDlg.msg6=There are no fully analyzed groups to display:"
        + "  the current Group By setting resulted in no groups, "
        + "or no groups are fully analyzed but ingest is not running."})
    synchronized private void checkForGroups() {
        if (groupManager.getAnalyzedGroups().isEmpty()) {
            if (IngestManager.getInstance().isIngestRunning()) {
                if (listeningEnabled.not().get()) {
                    replaceNotification(fullUIStackPane,
                            new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg1()));
                } else {
                    replaceNotification(fullUIStackPane,
                            new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg2(),
                                    new ProgressIndicator()));
                }

            } else if (dbTaskQueueSize.get() > 0) {
                replaceNotification(fullUIStackPane,
                        new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg3(),
                                new ProgressIndicator()));
            } else if (db != null && db.countAllFiles() <= 0) { // there are no files in db
                if (listeningEnabled.not().get()) {
                    replaceNotification(fullUIStackPane,
                            new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg4()));
                } else {
                    replaceNotification(fullUIStackPane,
                            new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg5()));
                }

            } else if (!groupManager.isRegrouping()) {
                replaceNotification(centralStackPane,
                        new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg6()));
            }

        } else {
            clearNotification();
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
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

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void replaceNotification(StackPane stackPane, Node newNode) {
        clearNotification();

        infoOverlay = new StackPane(infoOverLayBackground, newNode);
        if (stackPane != null) {
            stackPane.getChildren().add(infoOverlay);
        }
    }

    /**
     * configure the controller for a specific case.
     *
     * @param theNewCase the case to configure the controller for
     */
    public synchronized void setCase(Case theNewCase) {
        if (null == theNewCase) {
            reset();
        } else {
            this.sleuthKitCase = theNewCase.getSleuthkitCase();
            this.db = DrawableDB.getDrawableDB(ImageGalleryModule.getModuleOutputDir(theNewCase), this);

            setListeningEnabled(ImageGalleryModule.isEnabledforCase(theNewCase));
            setStale(ImageGalleryModule.isDrawableDBStale(theNewCase));

            // if we add this line icons are made as files are analyzed rather than on demand.
            // db.addUpdatedFileListener(IconCache.getDefault());
            historyManager.clear();
            groupManager.setDB(db);
            hashSetManager.setDb(db);
            categoryManager.setDb(db);
            tagsManager.setAutopsyTagsManager(theNewCase.getServices().getTagsManager());
            tagsManager.registerListener(groupManager);
            tagsManager.registerListener(categoryManager);
            shutDownDBExecutor();
            dbExecutor = getNewDBExecutor();
        }
    }

    /**
     * reset the state of the controller (eg if the case is closed)
     */
    public synchronized void reset() {
        LOGGER.info("resetting ImageGalleryControler to initial state."); //NON-NLS
        selectionModel.clearSelection();
        setListeningEnabled(false);
        ThumbnailCache.getDefault().clearCache();
        historyManager.clear();
        groupManager.clear();
        tagsManager.clearFollowUpTagName();
        tagsManager.unregisterListener(groupManager);
        tagsManager.unregisterListener(categoryManager);
        shutDownDBExecutor();

        if (toolbar != null) {
            toolbar.reset();
        }

        if (db != null) {
            db.closeDBCon();
        }
        db = null;
    }

    synchronized private void shutDownDBExecutor() {
        if (dbExecutor != null) {
            dbExecutor.shutdownNow();
            try {
                dbExecutor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.WARNING, "Image Gallery failed to shutdown DB Task Executor in a timely fashion.", ex);
            }
        }
    }

    private static ListeningExecutorService getNewDBExecutor() {
        return MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("DB-Worker-Thread-%d").build()));
    }

    /**
     * add InnerTask to the queue that the worker thread gets its work from
     *
     * @param bgTask
     */
    public synchronized void queueDBTask(BackgroundTask bgTask) {
        if (dbExecutor == null || dbExecutor.isShutdown()) {
            dbExecutor = getNewDBExecutor();
        }
        incrementQueueSize();
        dbExecutor.submit(bgTask).addListener(this::decrementQueueSize, MoreExecutors.directExecutor());

    }

    private void incrementQueueSize() {
        Platform.runLater(() -> dbTaskQueueSize.set(dbTaskQueueSize.get() + 1));
    }

    private void decrementQueueSize() {
        Platform.runLater(() -> dbTaskQueueSize.set(dbTaskQueueSize.get() - 1));
    }

    @Nullable
    synchronized public DrawableFile getFileFromId(Long fileID) throws TskCoreException {
        if (Objects.isNull(db)) {
            LOGGER.log(Level.WARNING, "Could not get file from id, no DB set.  The case is probably closed."); //NON-NLS
            return null;
        }
        return db.getFileFromID(fileID);
    }

    public void setStacks(StackPane fullUIStack, StackPane centralStack) {
        fullUIStackPane = fullUIStack;
        this.centralStackPane = centralStack;
        Platform.runLater(this::checkForGroups);
    }

    public synchronized void setToolbar(Toolbar toolbar) {
        if (this.toolbar != null) {
            throw new IllegalStateException("Can not set the toolbar a second time!");
        }
        this.toolbar = toolbar;
        thumbnailSize.bind(toolbar.thumbnailSizeProperty());
    }

    public ReadOnlyDoubleProperty regroupProgress() {
        return groupManager.regroupProgress();
    }

    /**
     * invoked by {@link OnStart} to make sure that the ImageGallery listeners
     * get setup as early as possible, and do other setup stuff.
     */
    void onStart() {
        Platform.setImplicitExit(false);
        LOGGER.info("setting up ImageGallery listeners"); //NON-NLS
        //TODO can we do anything usefull in an InjestJobEventListener?
        //IngestManager.getInstance().addIngestJobEventListener((PropertyChangeEvent evt) -> {});
        IngestManager.getInstance().addIngestModuleEventListener(new IngestModuleEventListener());
        Case.addPropertyChangeListener(new CaseEventListener());
    }

    public HashSetManager getHashSetManager() {
        return hashSetManager;
    }

    public CategoryManager getCategoryManager() {
        return categoryManager;
    }

    public DrawableTagsManager getTagsManager() {
        return tagsManager;
    }

    public void setShowTree(Runnable showTree) {
        this.showTree = showTree;
    }

    public UndoRedoManager getUndoManager() {
        return undoManager;
    }

    public ReadOnlyIntegerProperty getDBTasksQueueSizeProperty() {
        return dbTaskQueueSize.getReadOnlyProperty();
    }

    public synchronized SleuthkitCase getSleuthKitCase() {
        return sleuthKitCase;
    }

    /**
     * Abstract base class for task to be done on {@link DBWorkerThread}
     */
    @NbBundle.Messages({"ImageGalleryController.InnerTask.progress.name=progress",
        "ImageGalleryController.InnerTask.message.name=status"})
    static public abstract class BackgroundTask implements Runnable, Cancellable {

        private final SimpleObjectProperty<Worker.State> state = new SimpleObjectProperty<>(Worker.State.READY);
        private final SimpleDoubleProperty progress = new SimpleDoubleProperty(this, Bundle.ImageGalleryController_InnerTask_progress_name());
        private final SimpleStringProperty message = new SimpleStringProperty(this, Bundle.ImageGalleryController_InnerTask_message_name());

        protected BackgroundTask() {
        }

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

        public SimpleDoubleProperty progressProperty() {
            return progress;
        }

        public SimpleStringProperty messageProperty() {
            return message;
        }

        public Worker.State getState() {
            return state.get();
        }

        public ReadOnlyObjectProperty<Worker.State> stateProperty() {
            return new ReadOnlyObjectWrapper<>(state.get());
        }

        @Override
        public synchronized boolean cancel() {
            updateState(Worker.State.CANCELLED);
            return true;
        }

        protected void updateState(Worker.State newState) {
            state.set(newState);
        }

        protected synchronized boolean isCancelled() {
            return getState() == Worker.State.CANCELLED;
        }
    }

    /**
     * Abstract base class for tasks associated with a file in the database
     */
    static abstract class FileTask extends BackgroundTask {

        private final AbstractFile file;
        private final DrawableDB taskDB;

        public DrawableDB getTaskDB() {
            return taskDB;
        }

        public AbstractFile getFile() {
            return file;
        }

        public FileTask(AbstractFile f, DrawableDB taskDB) {
            super();
            this.file = f;
            this.taskDB = taskDB;
        }
    }

    /**
     * task that updates one file in database with results from ingest
     */
    static private class UpdateFileTask extends FileTask {

        UpdateFileTask(AbstractFile f, DrawableDB taskDB) {
            super(f, taskDB);
        }

        /**
         * Update a file in the database
         */
        @Override
        public void run() {
            try {
                DrawableFile drawableFile = DrawableFile.create(getFile(), true, false);
                getTaskDB().updateFile(drawableFile);
            } catch (NullPointerException ex) {
                // This is one of the places where we get many errors if the case is closed during processing.
                // We don't want to print out a ton of exceptions if this is the case.
                if (Case.isCaseOpen()) {
                    Logger.getLogger(UpdateFileTask.class.getName()).log(Level.SEVERE, "Error in UpdateFile task"); //NON-NLS
                }
            }
        }
    }

    /**
     * task that updates one file in database with results from ingest
     */
    static private class RemoveFileTask extends FileTask {

        RemoveFileTask(AbstractFile f, DrawableDB taskDB) {
            super(f, taskDB);
        }

        /**
         * Update a file in the database
         */
        @Override
        public void run() {
            try {
                getTaskDB().removeFile(getFile().getId());
            } catch (NullPointerException ex) {
                // This is one of the places where we get many errors if the case is closed during processing.
                // We don't want to print out a ton of exceptions if this is the case.
                if (Case.isCaseOpen()) {
                    Logger.getLogger(RemoveFileTask.class.getName()).log(Level.SEVERE, "Case was closed out from underneath RemoveFile task"); //NON-NLS
                }
            }
        }
    }

    @NbBundle.Messages({"BulkTask.committingDb.status=committing image/video database",
        "BulkTask.stopCopy.status=Stopping copy to drawable db task.",
        "BulkTask.errPopulating.errMsg=There was an error populating Image Gallery database."})
    abstract static private class BulkTransferTask extends BackgroundTask {

        static private final String FILE_EXTENSION_CLAUSE =
                "(name LIKE '%." //NON-NLS
                + String.join("' OR name LIKE '%.", FileTypeUtils.getAllSupportedExtensions()) //NON-NLS
                + "')";

        static private final String MIMETYPE_CLAUSE =
                "(mime_type LIKE '" //NON-NLS
                + String.join("' OR mime_type LIKE '", FileTypeUtils.getAllSupportedMimeTypes()) //NON-NLS
                + "') ";

        static final String DRAWABLE_QUERY =
                //grab files with supported extension
                "(" + FILE_EXTENSION_CLAUSE
                //grab files with supported mime-types
                + " OR " + MIMETYPE_CLAUSE //NON-NLS
                //grab files with image or video mime-types even if we don't officially support them
                + " OR mime_type LIKE 'video/%' OR mime_type LIKE 'image/%' )"; //NON-NLS

        final ImageGalleryController controller;
        final DrawableDB taskDB;
        final SleuthkitCase tskCase;

        ProgressHandle progressHandle;

        BulkTransferTask(ImageGalleryController controller, DrawableDB taskDB, SleuthkitCase tskCase) {
            this.controller = controller;
            this.taskDB = taskDB;
            this.tskCase = tskCase;
        }

        abstract void cleanup(boolean success);

        abstract List<AbstractFile> getFiles() throws TskCoreException;

        abstract void processFile(final AbstractFile f, DrawableDB.DrawableTransaction tr) throws TskCoreException;

        @Override
        public void run() {
            progressHandle = getInitialProgressHandle();
            progressHandle.start();
            updateMessage(Bundle.CopyAnalyzedFiles_populatingDb_status());

            try {
                //grab all files with supported extension or detected mime types
                final List<AbstractFile> files = getFiles();
                progressHandle.switchToDeterminate(files.size());

                updateProgress(0.0);

                //do in transaction
                DrawableDB.DrawableTransaction tr = taskDB.beginTransaction();
                int workDone = 0;
                for (final AbstractFile f : files) {
                    if (isCancelled() || Thread.interrupted()) {
                        LOGGER.log(Level.WARNING, "Task cancelled: not all contents may be transfered to drawable database."); //NON-NLS
                        progressHandle.finish();
                        break;
                    }

                    processFile(f, tr);

                    workDone++;
                    progressHandle.progress(f.getName(), workDone);
                    updateProgress(workDone - 1 / (double) files.size());
                    updateMessage(f.getName());
                }

                progressHandle.finish();
                progressHandle = ProgressHandle.createHandle(Bundle.BulkTask_committingDb_status());
                updateMessage(Bundle.BulkTask_committingDb_status());
                updateProgress(1.0);

                progressHandle.start();
                taskDB.commitTransaction(tr, true);

            } catch (TskCoreException ex) {
                progressHandle.progress(Bundle.BulkTask_stopCopy_status());
                LOGGER.log(Level.WARNING, "Stopping copy to drawable db task.  Failed to transfer all database contents", ex); //NON-NLS
                MessageNotifyUtil.Notify.warn(Bundle.BulkTask_errPopulating_errMsg(), ex.getMessage());
                cleanup(false);
                return;
            } finally {
                progressHandle.finish();
                updateMessage("");
                updateProgress(-1.0);
            }
            cleanup(true);
        }

        abstract ProgressHandle getInitialProgressHandle();
    }

    /**
     * Task that runs when image gallery listening is (re) enabled.
     *
     * Grabs all files with supported image/video mime types or extensions, and
     * adds them to the Drawable DB. Uses the presence of a mimetype as an
     * approximation to 'analyzed'.
     */
    @NbBundle.Messages({"CopyAnalyzedFiles.committingDb.status=committing image/video database",
        "CopyAnalyzedFiles.stopCopy.status=Stopping copy to drawable db task.",
        "CopyAnalyzedFiles.errPopulating.errMsg=There was an error populating Image Gallery database."})
    static private class CopyAnalyzedFiles extends BulkTransferTask {

        CopyAnalyzedFiles(ImageGalleryController controller, DrawableDB taskDB, SleuthkitCase tskCase) {
            super(controller, taskDB, tskCase);
        }

        @Override
        protected void cleanup(boolean success) {
            controller.setStale(!success);
        }

        @Override
        List<AbstractFile> getFiles() throws TskCoreException {
            return tskCase.findAllFilesWhere(DRAWABLE_QUERY);
        }

        @Override
        void processFile(AbstractFile f, DrawableDB.DrawableTransaction tr) {
            final boolean known = f.getKnown() == TskData.FileKnown.KNOWN;

            if (known) {
                taskDB.removeFile(f.getId(), tr);  //remove known files
            } else {

                try {
                    if (FileTypeUtils.hasDrawableMIMEType(f)) {  //supported mimetype => analyzed
                        taskDB.updateFile(DrawableFile.create(f, true, false), tr);
                    } else { //unsupported mimtype => analyzed but shouldn't include
                        taskDB.removeFile(f.getId(), tr);
                    }
                } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        @Override
        @NbBundle.Messages({"CopyAnalyzedFiles.populatingDb.status=populating analyzed image/video database",})
        ProgressHandle getInitialProgressHandle() {
            return ProgressHandle.createHandle(Bundle.CopyAnalyzedFiles_populatingDb_status(), this);
        }
    }

    /**
     * Copy files from a newly added data source into the DB. Get all "drawable"
     * files, based on extension and mime-type. After ingest we use file type id
     * module and if necessary jpeg/png signature matching to add/remove files
     *
     * TODO: create methods to simplify progress value/text updates to both
     * netbeans and ImageGallery progress/status
     */
    @NbBundle.Messages({"PrePopulateDataSourceFiles.committingDb.status=committing image/video database"})
    static private class PrePopulateDataSourceFiles extends BulkTransferTask {

        private static final Logger LOGGER = Logger.getLogger(PrePopulateDataSourceFiles.class.getName());

        private final Content dataSource;

        /**
         *
         * @param dataSourceId Data source object ID
         */
        PrePopulateDataSourceFiles(Content dataSource, ImageGalleryController controller, DrawableDB taskDB, SleuthkitCase tskCase) {
            super(controller, taskDB, tskCase);
            this.dataSource = dataSource;
        }

        @Override
        protected void cleanup(boolean success) {
        }

        @Override
        void processFile(final AbstractFile f, DrawableDB.DrawableTransaction tr) {
            taskDB.insertFile(DrawableFile.create(f, false, false), tr);
        }

        @Override
        List<AbstractFile> getFiles() throws TskCoreException {
            long datasourceID = dataSource.getDataSource().getId();
            return tskCase.findAllFilesWhere("data_source_obj_id = " + datasourceID + " AND " + DRAWABLE_QUERY);
        }

        @Override
        @NbBundle.Messages({"PrePopulateDataSourceFiles.prepopulatingDb.status=prepopulating image/video database",})
        ProgressHandle getInitialProgressHandle() {
            return ProgressHandle.createHandle(Bundle.PrePopulateDataSourceFiles_prepopulatingDb_status(), this);
        }
    }

    private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (RuntimeProperties.runningWithGUI() == false) {
                /*
                 * Running in "headless" mode, no need to process any events.
                 * This cannot be done earlier because the switch to core
                 * components inactive may not have been made at start up.
                 */
                IngestManager.getInstance().removeIngestModuleEventListener(this);
                return;
            }
            switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                case CONTENT_CHANGED:
                //TODO: do we need to do anything here?  -jm
                case DATA_ADDED:
                    /*
                     * we could listen to DATA events and progressivly update
                     * files, and get data from DataSource ingest modules, but
                     * given that most modules don't post new artifacts in the
                     * events and we would have to query for them, without
                     * knowing which are the new ones, we just ignore these
                     * events for now. The relevant data should all be captured
                     * by file done event, anyways -jm
                     */
                    break;
                case FILE_DONE:
                    /**
                     * getOldValue has fileID getNewValue has
                     * {@link Abstractfile}
                     */

                    AbstractFile file = (AbstractFile) evt.getNewValue();

                    if (isListeningEnabled()) {
                        if (file.isFile()) {
                            try {
                                synchronized (ImageGalleryController.this) {
                                    if (ImageGalleryModule.isDrawableAndNotKnown(file)) {
                                        //this file should be included and we don't already know about it from hash sets (NSRL)
                                        queueDBTask(new UpdateFileTask(file, db));
                                    } else if (FileTypeUtils.getAllSupportedExtensions().contains(file.getNameExtension())) {
                                        //doing this check results in fewer tasks queued up, and faster completion of db update
                                        //this file would have gotten scooped up in initial grab, but actually we don't need it
                                        queueDBTask(new RemoveFileTask(file, db));
                                    }
                                }
                            } catch (TskCoreException | FileTypeDetector.FileTypeDetectorInitException ex) {
                                //TODO: What to do here?
                                LOGGER.log(Level.SEVERE, "Unable to determine if file is drawable and not known.  Not making any changes to DB", ex); //NON-NLS
                                MessageNotifyUtil.Notify.error("Image Gallery Error",
                                        "Unable to determine if file is drawable and not known.  Not making any changes to DB.  See the logs for details.");
                            }
                        }
                    } else {   //TODO: keep track of what we missed for later
                        setStale(true);
                    }
                    break;
            }
        }
    }

    private class CaseEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (RuntimeProperties.runningWithGUI() == false) {
                /*
                 * Running in "headless" mode, no need to process any events.
                 * This cannot be done earlier because the switch to core
                 * components inactive may not have been made at start up.
                 */
                Case.removePropertyChangeListener(this);
                return;
            }
            switch (Case.Events.valueOf(evt.getPropertyName())) {
                case CURRENT_CASE:
                    Case newCase = (Case) evt.getNewValue();
                    if (newCase == null) { // case is closing
                        //close window, reset everything
                        SwingUtilities.invokeLater(ImageGalleryTopComponent::closeTopComponent);
                        reset();
                    } else { // a new case has been opened
                        setCase(newCase);    //connect db, groupmanager, start worker thread
                    }
                    break;
                case DATA_SOURCE_ADDED:
                    //copy all file data to drawable databse
                    Content newDataSource = (Content) evt.getNewValue();
                    if (isListeningEnabled()) {
                        queueDBTask(new PrePopulateDataSourceFiles(newDataSource, ImageGalleryController.this, getDatabase(), getSleuthKitCase()));
                    } else {//TODO: keep track of what we missed for later
                        setStale(true);
                    }
                    break;
                case CONTENT_TAG_ADDED:
                    final ContentTagAddedEvent tagAddedEvent = (ContentTagAddedEvent) evt;
                    if (getDatabase().isInDB(tagAddedEvent.getAddedTag().getContent().getId())) {
                        getTagsManager().fireTagAddedEvent(tagAddedEvent);
                    }
                    break;
                case CONTENT_TAG_DELETED:
                    final ContentTagDeletedEvent tagDeletedEvent = (ContentTagDeletedEvent) evt;
                    if (getDatabase().isInDB(tagDeletedEvent.getDeletedTagInfo().getContentID())) {
                        getTagsManager().fireTagDeletedEvent(tagDeletedEvent);
                    }
                    break;
            }
        }
    }
}
