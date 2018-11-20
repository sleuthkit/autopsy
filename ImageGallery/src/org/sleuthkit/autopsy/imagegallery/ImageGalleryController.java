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
import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Worker;
import javax.annotation.Nonnull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.actions.UndoRedoManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB.DrawableDbBuildStatusEnum;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.HashSetManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Connects different parts of ImageGallery together and is hub for flow of
 * control.
 */
public final class ImageGalleryController {

    private static final Logger logger = Logger.getLogger(ImageGalleryController.class.getName());

    /**
     * The file limit for Image Gallery. If the selected datasource (or all
     * datasources if that option is selected) has more than this many files (in
     * the tsk_files table) we don't allow the user to view it.
     */
    private static final long FILE_LIMIT = 6_000_000;

    /**
     * true if Image Gallery should listen to ingest events, false if it should
     * not listen to speed up ingest
     */
    private final SimpleBooleanProperty listeningEnabled = new SimpleBooleanProperty(false);

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ReadOnlyBooleanWrapper stale = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyBooleanWrapper metaDataCollapsed = new ReadOnlyBooleanWrapper(false);
    private final SimpleDoubleProperty thumbnailSizeProp = new SimpleDoubleProperty(100);
    private final ReadOnlyBooleanWrapper regroupDisabled = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyIntegerWrapper dbTaskQueueSize = new ReadOnlyIntegerWrapper(0);

    private final FileIDSelectionModel selectionModel = new FileIDSelectionModel(this);

    private final History<GroupViewState> historyManager = new History<>();
    private final UndoRedoManager undoManager = new UndoRedoManager();
    private final ThumbnailCache thumbnailCache = new ThumbnailCache(this);
    private final GroupManager groupManager;
    private final HashSetManager hashSetManager;
    private final CategoryManager categoryManager;
    private final DrawableTagsManager tagsManager;

    private ListeningExecutorService dbExecutor;

    private final Case autopsyCase;
    private final SleuthkitCase sleuthKitCase;
    private final DrawableDB drawableDB;

    public Case getAutopsyCase() {
        return autopsyCase;
    }

    public ReadOnlyBooleanProperty metaDataCollapsedProperty() {
        return metaDataCollapsed.getReadOnlyProperty();
    }

    public void setMetaDataCollapsed(Boolean metaDataCollapsed) {
        this.metaDataCollapsed.set(metaDataCollapsed);
    }

    public DoubleProperty thumbnailSizeProperty() {
        return thumbnailSizeProp;
    }

    public ReadOnlyBooleanProperty regroupDisabledProperty() {
        return regroupDisabled.getReadOnlyProperty();
    }

    public FileIDSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public DrawableDB getDatabase() {
        return drawableDB;
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
    }

    public ReadOnlyBooleanProperty staleProperty() {
        return stale.getReadOnlyProperty();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean isStale() {
        return stale.get();
    }

    ImageGalleryController(@Nonnull Case newCase) throws TskCoreException {

        this.autopsyCase = Objects.requireNonNull(newCase);
        this.sleuthKitCase = newCase.getSleuthkitCase();

        setListeningEnabled(ImageGalleryModule.isEnabledforCase(newCase));

        groupManager = new GroupManager(this);
        this.drawableDB = DrawableDB.getDrawableDB(this);
        categoryManager = new CategoryManager(this);
        tagsManager = new DrawableTagsManager(this);
        tagsManager.registerListener(groupManager);
        tagsManager.registerListener(categoryManager);

        hashSetManager = new HashSetManager(drawableDB);
        setStale(isDataSourcesTableStale());

        dbExecutor = getNewDBExecutor();

        // listener for the boolean property about when IG is listening / enabled
        listeningEnabled.addListener((observable, wasPreviouslyEnabled, isEnabled) -> {
            try {
                // if we just turned on listening and a single-user case is open and that case is not up to date, then rebuild it
                // For multiuser cases, we defer DB rebuild till the user actually opens Image Gallery
                if (isEnabled && !wasPreviouslyEnabled
                        && isDataSourcesTableStale()
                        && (Case.getCurrentCaseThrows().getCaseType() == CaseType.SINGLE_USER_CASE)) {
                    //populate the db
                    this.rebuildDB();
                }

            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Exception while getting open case.", ex);
            }
        });

        viewStateProperty().addListener((Observable observable) -> {
            //when the viewed group changes, clear the selection and the undo/redo history
            selectionModel.clearSelection();
            undoManager.clear();
        });

        IngestManager ingestManager = IngestManager.getInstance();
        PropertyChangeListener ingestEventHandler
                = propertyChangeEvent -> Platform.runLater(this::updateRegroupDisabled);

        ingestManager.addIngestModuleEventListener(ingestEventHandler);
        ingestManager.addIngestJobEventListener(ingestEventHandler);

        dbTaskQueueSize.addListener(obs -> this.updateRegroupDisabled());

    }

    /**
     * @return Currently displayed group or null if nothing is being displayed
     */
    public GroupViewState getViewState() {
        return historyManager.getCurrentState();
    }

    /**
     * Get observable property of the current group. The UI currently changes
     * based on this property changing, which happens when other actions and
     * threads call advance().
     *
     * @return Currently displayed group (as a property that can be observed)
     */
    public ReadOnlyObjectProperty<GroupViewState> viewStateProperty() {
        return historyManager.currentState();
    }

    /**
     * Should the "forward" button on the history be enabled?
     *
     * @return
     */
    public ReadOnlyBooleanProperty getCanAdvance() {
        return historyManager.getCanAdvance();
    }

    /**
     * Should the "Back" button on the history be enabled?
     *
     * @return
     */
    public ReadOnlyBooleanProperty getCanRetreat() {
        return historyManager.getCanRetreat();
    }

    /**
     * Display the passed in group. Causes this group to get recorded in the
     * history queue and observers of the current state will be notified and
     * update their panels/widgets appropriately.
     *
     * @param newState
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    public void advance(GroupViewState newState) {
        historyManager.advance(newState);
    }

    /**
     * Display the next group in the "forward" history stack
     *
     * @return
     */
    public GroupViewState advance() {
        return historyManager.advance();
    }

    /**
     * Display the previous group in the "back" history stack
     *
     * @return
     */
    public GroupViewState retreat() {
        return historyManager.retreat();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void updateRegroupDisabled() {
        regroupDisabled.set((dbTaskQueueSize.get() > 0) || IngestManager.getInstance().isIngestRunning());
    }

    /**
     * Rebuilds the DrawableDB database.
     *
     */
    public void rebuildDB() {
        // queue a rebuild task for each stale data source
        getStaleDataSourceIds().forEach(dataSourceObjId -> queueDBTask(new CopyAnalyzedFiles(dataSourceObjId, this)));
    }

    /**
     * reset the state of the controller (eg if the case is closed)
     */
    public synchronized void reset() {
        logger.info("Closing ImageGalleryControler for case."); //NON-NLS

        selectionModel.clearSelection();
        thumbnailCache.clearCache();
        historyManager.clear();
        groupManager.reset();

        shutDownDBExecutor();
        drawableDB.close();
        dbExecutor = getNewDBExecutor();
    }

    /**
     * Checks if the datasources table in drawable DB is stale.
     *
     * @return true if datasources table is stale
     */
    public boolean isDataSourcesTableStale() {
        return isNotEmpty(getStaleDataSourceIds());
    }

    /**
     * Returns a set of data source object ids that are stale.
     *
     * This includes any data sources already in the table, that are not in
     * COMPLETE status, or any data sources that might have been added to the
     * case, but are not in the datasources table.
     *
     * @return list of data source object ids that are stale.
     */
    Set<Long> getStaleDataSourceIds() {

        Set<Long> staleDataSourceIds = new HashSet<>();

        // no current case open to check
        if ((null == getDatabase()) || (null == getSleuthKitCase())) {
            return staleDataSourceIds;
        }

        try {
            Map<Long, DrawableDbBuildStatusEnum> knownDataSourceIds = getDatabase().getDataSourceDbBuildStatus();

            List<DataSource> dataSources = getSleuthKitCase().getDataSources();
            Set<Long> caseDataSourceIds = new HashSet<>();
            dataSources.stream().map(DataSource::getId).forEach(caseDataSourceIds::add);

            // collect all data sources already in the table, that are not yet COMPLETE
            knownDataSourceIds.entrySet().stream().forEach((Map.Entry<Long, DrawableDbBuildStatusEnum> t) -> {
                DrawableDbBuildStatusEnum status = t.getValue();
                if (DrawableDbBuildStatusEnum.COMPLETE != status) {
                    staleDataSourceIds.add(t.getKey());
                }
            });

            // collect any new data sources in the case.
            caseDataSourceIds.forEach((Long id) -> {
                if (!knownDataSourceIds.containsKey(id)) {
                    staleDataSourceIds.add(id);
                }
            });

            return staleDataSourceIds;
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Image Gallery failed to check if datasources table is stale.", ex);
            return staleDataSourceIds;
        }

    }

    /**
     * Returns a map of all data source object ids, along with their DB build
     * status.
     *
     * This includes any data sources already in the table, and any data sources
     * that might have been added to the case, but are not in the datasources
     * table.
     *
     * @return map of data source object ids and their Db build status.
     */
    public Map<Long, DrawableDbBuildStatusEnum> getAllDataSourcesDrawableDBStatus() {

        Map<Long, DrawableDbBuildStatusEnum> dataSourceStatusMap = new HashMap<>();

        // no current case open to check
        if ((null == getDatabase()) || (null == getSleuthKitCase())) {
            return dataSourceStatusMap;
        }

        try {
            Map<Long, DrawableDbBuildStatusEnum> knownDataSourceIds = getDatabase().getDataSourceDbBuildStatus();

            List<DataSource> dataSources = getSleuthKitCase().getDataSources();
            Set<Long> caseDataSourceIds = new HashSet<>();
            dataSources.stream().map(DataSource::getId).forEach(caseDataSourceIds::add);

            // collect all data sources already in the table
            knownDataSourceIds.entrySet().stream().forEach((Map.Entry<Long, DrawableDbBuildStatusEnum> t) -> {
                dataSourceStatusMap.put(t.getKey(), t.getValue());
            });

            // collect any new data sources in the case.
            caseDataSourceIds.forEach((Long id) -> {
                if (!knownDataSourceIds.containsKey(id)) {
                    dataSourceStatusMap.put(id, DrawableDbBuildStatusEnum.UNKNOWN);
                }
            });

            return dataSourceStatusMap;
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Image Gallery failed to get data source DB status.", ex);
            return dataSourceStatusMap;
        }
    }

    public boolean hasTooManyFiles(DataSource datasource) throws TskCoreException {
        String whereClause = (datasource == null)
                ? "1 = 1"
                : "data_source_obj_id = " + datasource.getId();

        return sleuthKitCase.countFilesWhere(whereClause) > FILE_LIMIT;

    }

    /**
     * Checks if the given data source has any files with no mimetype
     *
     * @param datasource
     *
     * @return true if the datasource has any files with no mime type
     *
     * @throws TskCoreException
     */
    public boolean hasFilesWithNoMimetype(Content datasource) throws TskCoreException {

        // There are some special files/attributes in the root folder, like $BadClus:$Bad and $Security:$SDS  
        // The IngestTasksScheduler does not push them down to the ingest modules, 
        // and hence they do not have any assigned mimetype
        String whereClause = "data_source_obj_id = " + datasource.getId()
                + " AND ( meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue() + ")"
                + " AND ( mime_type IS NULL )"
                + " AND ( meta_addr >= 32 ) "
                + " AND ( parent_path <> '/' )"
                + " AND ( name NOT like '$%:%' )";

        return sleuthKitCase.countFilesWhere(whereClause) > 0;
    }

    synchronized private void shutDownDBExecutor() {
        if (dbExecutor != null) {
            dbExecutor.shutdownNow();
            try {
                dbExecutor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, "Image Gallery failed to shutdown DB Task Executor in a timely fashion.", ex);
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

    public DrawableFile getFileFromID(Long fileID) throws TskCoreException {
        return drawableDB.getFileFromID(fileID);
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

    public UndoRedoManager getUndoManager() {
        return undoManager;
    }

    public ReadOnlyIntegerProperty getDBTasksQueueSizeProperty() {
        return dbTaskQueueSize.getReadOnlyProperty();
    }

    public synchronized SleuthkitCase getSleuthKitCase() {
        return sleuthKitCase;

    }

    public ThumbnailCache getThumbsCache() {
        return thumbnailCache;

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

        FileTask(AbstractFile f, DrawableDB taskDB) {
            super();
            this.file = f;
            this.taskDB = taskDB;
        }
    }

    /**
     * task that updates one file in database with results from ingest
     */
    static class UpdateFileTask extends FileTask {

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
            } catch (TskCoreException | SQLException ex) {
                Logger.getLogger(UpdateFileTask.class.getName()).log(Level.SEVERE, "Error in update file task", ex); //NON-NLS
            }
        }
    }

    /**
     * task that updates one file in database with results from ingest
     */
    static class RemoveFileTask extends FileTask {

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
            } catch (TskCoreException | SQLException ex) {
                Logger.getLogger(RemoveFileTask.class.getName()).log(Level.SEVERE, "Error in remove file task", ex); //NON-NLS
            }
        }
    }

    /**
     * Base abstract class for various methods of copying image files data, for
     * a given data source, into the Image gallery DB.
     */
    @NbBundle.Messages({"BulkTask.committingDb.status=committing image/video database",
        "BulkTask.stopCopy.status=Stopping copy to drawable db task.",
        "BulkTask.errPopulating.errMsg=There was an error populating Image Gallery database."})
    abstract static class BulkTransferTask extends BackgroundTask {

        static private final String FILE_EXTENSION_CLAUSE
                = "(extension LIKE '" //NON-NLS
                + String.join("' OR extension LIKE '", FileTypeUtils.getAllSupportedExtensions()) //NON-NLS
                + "') ";

        static private final String MIMETYPE_CLAUSE
                = "(mime_type LIKE '" //NON-NLS
                + String.join("' OR mime_type LIKE '", FileTypeUtils.getAllSupportedMimeTypes()) //NON-NLS
                + "') ";

        private final String DRAWABLE_QUERY;
        private final String DATASOURCE_CLAUSE;

        protected final ImageGalleryController controller;
        protected final DrawableDB taskDB;
        protected final SleuthkitCase tskCase;
        protected final long dataSourceObjId;

        private ProgressHandle progressHandle;
        private boolean taskCompletionStatus;

        BulkTransferTask(long dataSourceObjId, ImageGalleryController controller) {
            this.controller = controller;
            this.taskDB = controller.getDatabase();
            this.tskCase = controller.getSleuthKitCase();
            this.dataSourceObjId = dataSourceObjId;

            DATASOURCE_CLAUSE = " (data_source_obj_id = " + dataSourceObjId + ") ";

            DRAWABLE_QUERY
                    = DATASOURCE_CLAUSE
                    + " AND ( meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue() + ")"
                    + " AND ( "
                    + //grab files with supported extension
                    FILE_EXTENSION_CLAUSE
                    //grab files with supported mime-types
                    + " OR " + MIMETYPE_CLAUSE //NON-NLS
                    //grab files with image or video mime-types even if we don't officially support them
                    + " OR mime_type LIKE 'video/%' OR mime_type LIKE 'image/%' )"; //NON-NLS
        }

        /**
         * Do any cleanup for this task.
         *
         * @param success true if the transfer was successful
         */
        abstract void cleanup(boolean success);

        abstract void processFile(final AbstractFile f, DrawableDB.DrawableTransaction tr, CaseDbTransaction caseDBTransaction) throws TskCoreException;

        /**
         * Gets a list of files to process.
         *
         * @return list of files to process
         *
         * @throws TskCoreException
         */
        List<AbstractFile> getFiles() throws TskCoreException {
            return tskCase.findAllFilesWhere(DRAWABLE_QUERY);
        }

        @Override
        public void run() {
            progressHandle = getInitialProgressHandle();
            progressHandle.start();
            updateMessage(Bundle.CopyAnalyzedFiles_populatingDb_status() + " (Data Source " + dataSourceObjId + ")");

            DrawableDB.DrawableTransaction drawableDbTransaction = null;
            CaseDbTransaction caseDbTransaction = null;
            try {
                //grab all files with supported extension or detected mime types
                final List<AbstractFile> files = getFiles();
                progressHandle.switchToDeterminate(files.size());

                taskDB.insertOrUpdateDataSource(dataSourceObjId, DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS);

                updateProgress(0.0);
                taskCompletionStatus = true;
                int workDone = 0;

                // Cycle through all of the files returned and call processFile on each
                //do in transaction
                drawableDbTransaction = taskDB.beginTransaction();

                /*
                 * We are going to periodically commit the CaseDB transaction
                 * and sleep so that the user can have Autopsy do other stuff
                 * while these bulk tasks are ongoing.
                 */
                int caseDbCounter = 0;
                for (final AbstractFile f : files) {
                    if (caseDbTransaction == null) {
                        caseDbTransaction = tskCase.beginTransaction();
                    }

                    if (isCancelled() || Thread.interrupted()) {
                        logger.log(Level.WARNING, "Task cancelled or interrupted: not all contents may be transfered to drawable database."); //NON-NLS
                        taskCompletionStatus = false;
                        progressHandle.finish();

                        break;
                    }

                    processFile(f, drawableDbTransaction, caseDbTransaction);

                    workDone++;
                    progressHandle.progress(f.getName(), workDone);
                    updateProgress(workDone - 1 / (double) files.size());
                    updateMessage(f.getName());

                    // Periodically, commit the transaction (which frees the lock) and sleep
                    // to allow other threads to get some work done in CaseDB
                    if ((++caseDbCounter % 200) == 0) {
                        caseDbTransaction.commit();
                        caseDbTransaction = null;
                        Thread.sleep(500); // 1/2 second
                    }
                }

                progressHandle.finish();
                progressHandle = ProgressHandle.createHandle(Bundle.BulkTask_committingDb_status());
                updateMessage(Bundle.BulkTask_committingDb_status() + " (Data Source " + dataSourceObjId + ")");
                updateProgress(1.0);

                progressHandle.start();
                if (caseDbTransaction != null) {
                    caseDbTransaction.commit();
                    caseDbTransaction = null;
                }

                // pass true so that groupmanager is notified of the changes
                taskDB.commitTransaction(drawableDbTransaction, true);
                drawableDbTransaction = null;

            } catch (TskCoreException | SQLException | InterruptedException ex) {
                if (null != caseDbTransaction) {
                    try {
                        caseDbTransaction.rollback();
                    } catch (TskCoreException ex2) {
                        logger.log(Level.SEVERE, String.format("Failed to roll back case db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                    }
                }
                if (null != drawableDbTransaction) {
                    try {
                        taskDB.rollbackTransaction(drawableDbTransaction);
                    } catch (SQLException ex2) {
                        logger.log(Level.SEVERE, String.format("Failed to roll back drawables db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                    }
                }
                progressHandle.progress(Bundle.BulkTask_stopCopy_status());
                logger.log(Level.WARNING, "Stopping copy to drawable db task.  Failed to transfer all database contents", ex); //NON-NLS
                MessageNotifyUtil.Notify.warn(Bundle.BulkTask_errPopulating_errMsg(), ex.getMessage());
                cleanup(false);
            } finally {
                progressHandle.finish();

                DrawableDB.DrawableDbBuildStatusEnum datasourceDrawableDBStatus
                        = (taskCompletionStatus)
                                ? DrawableDB.DrawableDbBuildStatusEnum.COMPLETE
                                : DrawableDB.DrawableDbBuildStatusEnum.DEFAULT;
                taskDB.insertOrUpdateDataSource(dataSourceObjId, datasourceDrawableDBStatus);

                updateMessage("");
                updateProgress(-1.0);
            }
            cleanup(taskCompletionStatus);
        }

        abstract ProgressHandle getInitialProgressHandle();

        protected void setTaskCompletionStatus(boolean status) {
            taskCompletionStatus = status;
        }
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
    static class CopyAnalyzedFiles extends BulkTransferTask {

        CopyAnalyzedFiles(long dataSourceObjId, ImageGalleryController controller) {
            super(dataSourceObjId, controller);
            taskDB.buildFileMetaDataCache();
        }

        @Override
        protected void cleanup(boolean success) {
            taskDB.freeFileMetaDataCache();
            // at the end of the task, set the stale status based on the 
            // cumulative status of all data sources
            controller.setStale(controller.isDataSourcesTableStale());
        }

        @Override
        void processFile(AbstractFile f, DrawableDB.DrawableTransaction tr, CaseDbTransaction caseDbTransaction) throws TskCoreException {
            final boolean known = f.getKnown() == TskData.FileKnown.KNOWN;

            if (known) {
                taskDB.removeFile(f.getId(), tr);  //remove known files
            } else {
                // if mimetype of the file hasn't been ascertained, ingest might not have completed yet.
                if (null == f.getMIMEType()) {
                    // set to false to force the DB to be marked as stale
                    this.setTaskCompletionStatus(false);
                } //supported mimetype => analyzed
                else if (FileTypeUtils.hasDrawableMIMEType(f)) {
                    taskDB.updateFile(DrawableFile.create(f, true, false), tr, caseDbTransaction);
                } //unsupported mimtype => analyzed but shouldn't include
                else {
                    taskDB.removeFile(f.getId(), tr);
                }
            }
        }

        @Override
        @NbBundle.Messages({"CopyAnalyzedFiles.populatingDb.status=populating analyzed image/video database",})
        ProgressHandle getInitialProgressHandle() {
            return ProgressHandle.createHandle(Bundle.CopyAnalyzedFiles_populatingDb_status(), this);
        }
    }

}
