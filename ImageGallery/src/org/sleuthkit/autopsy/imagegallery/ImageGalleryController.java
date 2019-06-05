/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
import java.sql.SQLException;
import java.util.EnumSet;
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
import javax.annotation.concurrent.GuardedBy;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.commons.collections4.CollectionUtils;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.events.AutopsyEvent;
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
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * This class is responsible for the controller role in an MVC pattern
 * implementation where the model is the drawables database for the case plus
 * the image gallery tables in the case database, and the view is the image
 * gallery top component. There is a per case Singleton instance of this class.
 */
public final class ImageGalleryController {

    private static final Logger logger = Logger.getLogger(ImageGalleryController.class.getName());

    /*
     * The file limit for image gallery use. If the selected data source (or all
     * data sources, if that option is selected) has more than this many files
     * in the tsk_files table, the user cannot use the image gallery.
     */
    private static final long FILE_LIMIT = 6_000_000;

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(
            Case.Events.CURRENT_CASE,
            Case.Events.DATA_SOURCE_ADDED,
            Case.Events.CONTENT_TAG_ADDED,
            Case.Events.CONTENT_TAG_DELETED
    );

    /*
     * There is Singleton instance of this class per case. It is created during
     * the opening of case resources and destroyed during the closing of case
     * resources.
     */
    private static final Object controllerLock = new Object();
    @GuardedBy("controllerLock")
    private static final Map<String, ImageGalleryController> controllersByCase = new HashMap<>();

    /**
     * A flag that controls whether or not the controller is handling various
     * application events in "real time." Set to true by default. If the flag is
     * not set then:
     *
     * - All ingest module events are ignored.
     *
     * - Data source added events are ignored.
     *
     * -
     * RJCTODO: Finish this RJCTODO: Why is this perceived as speeding up
     * ingest?
     */
    private final SimpleBooleanProperty listeningEnabled;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX) // RJCTODO: Why? This does not seem to be enforced.
    private final ReadOnlyBooleanWrapper isCaseStale;

    private final ReadOnlyBooleanWrapper metaDataCollapsed;
    private final SimpleDoubleProperty thumbnailSizeProp;
    private final ReadOnlyBooleanWrapper regroupDisabled;
    private final ReadOnlyIntegerWrapper dbTaskQueueSize;
    private final History<GroupViewState> historyManager;
    private final UndoRedoManager undoManager;
    private final Case autopsyCase;
    private final SleuthkitCase sleuthKitCase;
    private final ListeningExecutorService dbExecutor;
    private final CaseEventListener caseEventListener;
    private final IngestJobEventListener ingestJobEventListener;
    private final IngestModuleEventListener ingestModuleEventListener;
    private FileIDSelectionModel selectionModel;
    private ThumbnailCache thumbnailCache;
    private DrawableDB drawableDB;
    private GroupManager groupManager;
    private HashSetManager hashSetManager;
    private CategoryManager categoryManager;
    private DrawableTagsManager tagsManager;

    /**
     * Creates an image gallery controller for a case. The controller will
     * create/open the model for the case: a local drawables database and the
     * image gallery tables in the case database.
     *
     * @param theCase The case.
     *
     * @throws TskCoreException If there is an issue creating/opening the model
     *                          for the case.
     */
    static void createController(Case theCase) throws TskCoreException {
        synchronized (controllerLock) {
            if (!controllersByCase.containsKey(theCase.getName())) {
                ImageGalleryController controller = new ImageGalleryController(theCase);
                controller.startUp();
                controllersByCase.put(theCase.getName(), controller);
            }
        }
    }

    /**
     * Gets the image gallery controller for a case.
     *
     * @param theCase The case.
     *
     * @return The image gallery controller or null if it does not exist.
     */
    public static ImageGalleryController getController(Case theCase) {
        synchronized (controllerLock) {
            return controllersByCase.get(theCase.getName());
        }
    }    
    
    /**
     * Shuts down the image gallery controller for a case. The controller will
     * close the model for the case.
     *
     * @param theCase The case.
     */
    static void shutDownController(Case theCase) {
        ImageGalleryController controller = null;
        synchronized (controllerLock) {
            if (controllersByCase.containsKey(theCase.getName())) {
                controller = controllersByCase.remove(theCase.getName());
            }
        }
        if (controller != null) {
            controller.shutDown();
        }
    }

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

    public boolean isListeningEnabled() {
        synchronized (listeningEnabled) {
            return listeningEnabled.get();
        }
    }

    /**
     *
     * @param b True if any data source in the case is stale
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    void setCaseStale(Boolean b) {
        Platform.runLater(() -> {
            isCaseStale.set(b);
        });
    }

    public ReadOnlyBooleanProperty staleProperty() {
        return isCaseStale.getReadOnlyProperty();
    }

    /**
     *
     * @return true if any data source in the case is stale
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean isCaseStale() {
        return isCaseStale.get();
    }

    ImageGalleryController(@Nonnull Case newCase) throws TskCoreException {
        autopsyCase = Objects.requireNonNull(newCase);
        sleuthKitCase = newCase.getSleuthkitCase();
        listeningEnabled = new SimpleBooleanProperty(false);
        isCaseStale = new ReadOnlyBooleanWrapper(false);
        metaDataCollapsed = new ReadOnlyBooleanWrapper(false);
        thumbnailSizeProp = new SimpleDoubleProperty(100);
        regroupDisabled = new ReadOnlyBooleanWrapper(false);
        dbTaskQueueSize = new ReadOnlyIntegerWrapper(0);
        historyManager = new History<>();
        undoManager = new UndoRedoManager();
        setListeningEnabled(ImageGalleryModule.isEnabledforCase(newCase));
        dbExecutor = getNewDBExecutor();
        caseEventListener = new CaseEventListener();
        ingestJobEventListener = new IngestJobEventListener();
        ingestModuleEventListener = new IngestModuleEventListener();
    }

    void startUp() throws TskCoreException {
        selectionModel = new FileIDSelectionModel(this);
        thumbnailCache = new ThumbnailCache(this);

        /*
         * These two lines need to be executed in this order. RJCTODO: Why?
         */
        groupManager = new GroupManager(this);
        drawableDB = DrawableDB.getDrawableDB(this);

        categoryManager = new CategoryManager(this);
        tagsManager = new DrawableTagsManager(this);
        tagsManager.registerListener(groupManager);
        tagsManager.registerListener(categoryManager);
        hashSetManager = new HashSetManager(drawableDB);

        setCaseStale(isDataSourcesTableStale());

        /*
         * Add a listener for changes to the Image Gallery enabled property that
         * is set by a user via the options panel. For single-user cases, the
         * listener queues drawables database rebuild tasks if the drawables
         * database for the current case is stale. For multi-user cases, thw
         * listener does nothing, because rebuilding the drawables database is
         * deferred until the Image Gallery tool is opened.
         */
        listeningEnabled.addListener((observable, wasPreviouslyEnabled, isEnabled) -> {
            try {
                if (isEnabled && !wasPreviouslyEnabled
                        && (Case.getCurrentCaseThrows().getCaseType() == CaseType.SINGLE_USER_CASE)
                        && isDataSourcesTableStale()) {
                    queueDbRebuildTasks();
                }
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Exception while getting open case.", ex);
            }
        });

        /*
         * Add a listener for changes to the view state property that clears the
         * current selection and flush the undo/redo history.
         */
        viewStateProperty().addListener((Observable observable) -> {
            selectionModel.clearSelection();
            undoManager.clear();
        });

        /*
         * Add a listener for ingest manager ingest module and ingest job events
         * that enables/disables regrouping based on the drawables database task
         * queue size and whether or not ingest is running. Note that execution
         * of this logic needs to be dispatched to the JFX thread since the
         * listener's event handler will be invoked in the ingest manager's
         * event publishing thread.
         */
        PropertyChangeListener ingestEventHandler = propertyChangeEvent -> Platform.runLater(this::updateRegroupDisabled);
        IngestManager ingestManager = IngestManager.getInstance();
        ingestManager.addIngestModuleEventListener(ingestEventHandler);
        ingestManager.addIngestJobEventListener(ingestEventHandler);

        /*
         * Add a listener to the size of the drawables database task queue that
         * enables/disables regrouping based on the drawables database task
         * queue size and whether or not ingest is running.
         */
        dbTaskQueueSize.addListener(obs -> this.updateRegroupDisabled());

        /*
         * Subscribe to application events.
         */
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, caseEventListener);
        IngestManager.getInstance().addIngestJobEventListener(ingestJobEventListener);
        IngestManager.getInstance().addIngestModuleEventListener(ingestModuleEventListener);
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
    public void queueDbRebuildTasks() {
        // queue a rebuild task for each stale data source
        getStaleDataSourceIds().forEach(dataSourceObjId -> queueDBTask(new CopyAnalyzedFiles(dataSourceObjId, this)));
    }

    /**
     * Shuts down this per case singleton image gallery controller.
     */
    public synchronized void shutDown() {
        logger.log(Level.INFO, String.format("Shutting down image gallery controller for case %s (%s)", autopsyCase.getDisplayName(), autopsyCase.getName()));
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, caseEventListener);
        IngestManager.getInstance().removeIngestJobEventListener(ingestJobEventListener);
        IngestManager.getInstance().removeIngestModuleEventListener(ingestModuleEventListener);
        selectionModel.clearSelection();
        thumbnailCache.clearCache();
        historyManager.clear();
        groupManager.reset();
        shutDownDBExecutor();
        drawableDB.close();
        logger.log(Level.INFO, String.format("Completed shut down of image gallery controller for case %s (%s)", autopsyCase.getDisplayName(), autopsyCase.getName()));
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
     * COMPLETE or IN_PROGRESS status, or any data sources that might have been
     * added to the case, but are not in the datasources table.
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
                switch (status) {
                    case COMPLETE:
                    case IN_PROGRESS:
                        // not stale
                        break;
                    case REBUILT_STALE:
                        staleDataSourceIds.add(t.getKey());
                        break;
                    case UNKNOWN:
                        try {
                            // stale if there are files in CaseDB with MIME types
                            if (hasFilesWithMimeType(t.getKey())) {
                                staleDataSourceIds.add(t.getKey());
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error getting MIME types", ex);
                        }

                        break;
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
    public boolean hasFilesWithNoMimeType(long dataSourceId) throws TskCoreException {

        // There are some special files/attributes in the root folder, like $BadClus:$Bad and $Security:$SDS  
        // The IngestTasksScheduler does not push them down to the ingest modules, 
        // and hence they do not have any assigned mimetype
        String whereClause = "data_source_obj_id = " + dataSourceId
                + " AND ( meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue() + ")"
                + " AND ( mime_type IS NULL )"
                + " AND ( meta_addr >= 32 ) "
                + " AND ( parent_path <> '/' )"
                + " AND ( name NOT like '$%:%' )";

        return sleuthKitCase.countFilesWhere(whereClause) > 0;
    }

    public boolean hasFilesWithMimeType(long dataSourceId) throws TskCoreException {

        String whereClause = "data_source_obj_id = " + dataSourceId
                + " AND ( meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue() + ")"
                + " AND ( mime_type IS NOT NULL )";

        return sleuthKitCase.countFilesWhere(whereClause) > 0;
    }

    synchronized private void shutDownDBExecutor() {
        dbExecutor.shutdownNow();
        try {
            dbExecutor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, "Image Gallery failed to shutdown DB Task Executor in a timely fashion.", ex);
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
        if (!dbExecutor.isShutdown()) {
            incrementQueueSize();
            dbExecutor.submit(bgTask).addListener(this::decrementQueueSize, MoreExecutors.directExecutor());
        }
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

    public SleuthkitCase getSleuthKitCase() {
        return sleuthKitCase;
    }

    public ThumbnailCache getThumbsCache() {
        return thumbnailCache;
    }

    /**
     * Indicates whether or not a given file is of interest to the image gallery
     * module (is "drawable") and is not marked as a "known" file (e.g., is not
     * a file in the NSRL hash set).
     *
     * @param file The file.
     *
     * @return True if the file is "drawable" and not "known", false otherwise.
     *
     * @throws FileTypeDetectorInitException If there is an error determining
     *                                       the type of the file.
     */
    private static boolean isDrawableAndNotKnown(AbstractFile abstractFile) throws FileTypeDetector.FileTypeDetectorInitException {
        return (abstractFile.getKnown() != TskData.FileKnown.KNOWN) && FileTypeUtils.isDrawable(abstractFile);
    }

    /**
     * A listener for ingest module application events.
     */
    private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (isListeningEnabled() == false) {
                return;
            }

            /*
             * Updates when individual files are fully analyzed and artifacts
             * are added to a case are only done in "real time" on the host that
             * is running the ingest job. On a remote host, the updates are
             * deferred until the ingest job is complete.
             */
            if (((AutopsyEvent) event).getSourceType() != AutopsyEvent.SourceType.LOCAL) {
                return;
            }

            String eventType = event.getPropertyName();
            switch (IngestManager.IngestModuleEvent.valueOf(eventType)) {
                case FILE_DONE:
                    AbstractFile file = (AbstractFile) event.getNewValue();
                    if (!file.isFile()) {
                        return;
                    }
                    try {
                        if (isDrawableAndNotKnown(file)) {
                            queueDBTask(new ImageGalleryController.UpdateFileTask(file, drawableDB));
                        }
                    } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to determine if file is of interest to the image gallery module, ignoring file (obj_id=%d)", file.getId()), ex); //NON-NLS
                    }
                    break;
                case DATA_ADDED:
                    ModuleDataEvent artifactAddedEvent = (ModuleDataEvent) event.getOldValue();
                    if (CollectionUtils.isNotEmpty(artifactAddedEvent.getArtifacts())) {
                        for (BlackboardArtifact art : artifactAddedEvent.getArtifacts()) {
                            if (artifactAddedEvent.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
                                drawableDB.addExifCache(art.getObjectID());
                            } else if (artifactAddedEvent.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                                drawableDB.addHashSetCache(art.getObjectID());
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * A listener for case application events.
     */
    private class CaseEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            Case.Events eventType = Case.Events.valueOf(event.getPropertyName());
            if (eventType == Case.Events.CURRENT_CASE) {
                if (event.getOldValue() != null) {
                    /*
                     * The old value is set, then the CURRENT_CASE event is a
                     * case closed event.
                     */
                    SwingUtilities.invokeLater(ImageGalleryTopComponent::closeTopComponent);
                }
            } else {
                switch (eventType) {
                    case DATA_SOURCE_ADDED:
                        if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                            Content newDataSource = (Content) event.getNewValue();
                            if (isListeningEnabled()) {
                                drawableDB.insertOrUpdateDataSource(newDataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.UNKNOWN);
                            }
                        }
                        break;
                    case CONTENT_TAG_ADDED:
                        final ContentTagAddedEvent tagAddedEvent = (ContentTagAddedEvent) event;
                        long objId = tagAddedEvent.getAddedTag().getContent().getId();
                        drawableDB.addTagCache(objId); // RJCTODO: Why add the tag to the cache before doing the in DB check?
                        if (drawableDB.isInDB(objId)) {
                            tagsManager.fireTagAddedEvent(tagAddedEvent);
                        }
                        break;
                    case CONTENT_TAG_DELETED:
                        final ContentTagDeletedEvent tagDeletedEvent = (ContentTagDeletedEvent) event;
                        if (drawableDB.isInDB(tagDeletedEvent.getDeletedTagInfo().getContentID())) {
                            tagsManager.fireTagDeletedEvent(tagDeletedEvent);
                        } // RJCTODO: Why not remove the tag from the cache?
                        break;
                    default:
                        logger.log(Level.WARNING, String.format("Received %s event with no subscription", event.getPropertyName())); //NON-NLS
                        break;
                }
            }
        }
    }

    /**
     * A listener for ingest job application events.
     */
    private class IngestJobEventListener implements PropertyChangeListener {

        @NbBundle.Messages({
            "ImageGalleryController.dataSourceAnalyzed.confDlg.msg= A new data source was added and finished ingest.\n"
            + "The image / video database may be out of date. "
            + "Do you want to update the database with ingest results?\n",
            "ImageGalleryController.dataSourceAnalyzed.confDlg.title=Image Gallery"
        })
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            /*
             * Only handling data source analysis events.
             */
            // RJCTODO: This would be less messy if IngestManager supported 
            // subscribing for a subset of events the way case does, and it the 
            // conditional blocks became method calls. 
            if (!(event instanceof DataSourceAnalysisEvent)) {
                return;
            }

            DataSourceAnalysisEvent dataSourceEvent = (DataSourceAnalysisEvent) event;
            Content dataSource = dataSourceEvent.getDataSource();
            if (dataSource == null) {
                logger.log(Level.WARNING, String.format("Failed to handle %s event", event.getPropertyName())); //NON-NLS
                return;
            }

            long dataSourceObjId = dataSource.getId();
            String eventType = dataSourceEvent.getPropertyName();
            try {
                switch (IngestManager.IngestJobEvent.valueOf(eventType)) {
                    case DATA_SOURCE_ANALYSIS_STARTED:
                        if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                            if (isListeningEnabled()) {
                                // Don't update status if it is is already marked as COMPLETE
                                if (drawableDB.getDataSourceDbBuildStatus(dataSourceObjId) != DrawableDB.DrawableDbBuildStatusEnum.COMPLETE) {
                                    drawableDB.insertOrUpdateDataSource(dataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS);
                                }
                                drawableDB.buildFileMetaDataCache();
                            }
                        }
                        break;
                    case DATA_SOURCE_ANALYSIS_COMPLETED:
                        if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                            /*
                             * This node just completed analysis of a data
                             * source. Set the state of the local drawables
                             * database.
                             */
                            if (isListeningEnabled()) {
                                groupManager.resetCurrentPathGroup();
                                if (drawableDB.getDataSourceDbBuildStatus(dataSourceObjId) == DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS) {

                                    // If at least one file in CaseDB has mime type, then set to COMPLETE
                                    // Otherwise, back to UNKNOWN since we assume file type module was not run        
                                    DrawableDB.DrawableDbBuildStatusEnum datasourceDrawableDBStatus
                                            = hasFilesWithMimeType(dataSourceObjId)
                                            ? DrawableDB.DrawableDbBuildStatusEnum.COMPLETE
                                            : DrawableDB.DrawableDbBuildStatusEnum.UNKNOWN;

                                    drawableDB.insertOrUpdateDataSource(dataSource.getId(), datasourceDrawableDBStatus);
                                }
                                drawableDB.freeFileMetaDataCache();
                            }
                        } else if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                            /*
                             * A remote node just completed analysis of a data
                             * source. The local drawables database is therefore
                             * stale. If the image gallery top component is
                             * open, give the user an opportunity to update the
                             * drawables database now.
                             */
                            setCaseStale(true);
                            if (isListeningEnabled()) {
                                SwingUtilities.invokeLater(() -> {
                                    if (ImageGalleryTopComponent.isImageGalleryOpen()) {
                                        int showAnswer = JOptionPane.showConfirmDialog(ImageGalleryTopComponent.getTopComponent(),
                                                Bundle.ImageGalleryController_dataSourceAnalyzed_confDlg_msg(),
                                                Bundle.ImageGalleryController_dataSourceAnalyzed_confDlg_title(),
                                                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                                        switch (showAnswer) {
                                            case JOptionPane.YES_OPTION:
                                                queueDbRebuildTasks();
                                                break;
                                            case JOptionPane.NO_OPTION:
                                            case JOptionPane.CANCEL_OPTION:
                                            default:
                                                break;
                                        }
                                    }
                                });
                            }
                        }
                        break;
                    default:
                        break;
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to handle %s event for %s (objId=%d)", dataSourceEvent.getPropertyName(), dataSource.getName(), dataSourceObjId), ex);
            }
        }
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
     * task that updates one file in database with results from ingest
     */
    static class UpdateFileTask extends BackgroundTask {

        private final AbstractFile file;
        private final DrawableDB taskDB;

        public DrawableDB getTaskDB() {
            return taskDB;
        }

        public AbstractFile getFile() {
            return file;
        }

        UpdateFileTask(AbstractFile f, DrawableDB taskDB) {
            super();
            this.file = f;
            this.taskDB = taskDB;
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
     * Base abstract class for various methods of copying image files data, for
     * a given data source, into the Image gallery DB.
     */
    @NbBundle.Messages({"BulkTask.committingDb.status=committing image/video database",
        "BulkTask.stopCopy.status=Stopping copy to drawable db task.",
        "BulkTask.errPopulating.errMsg=There was an error populating Image Gallery database."})
    abstract static class BulkTransferTask extends BackgroundTask {

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
                    //grab files with supported mime-types
                    + MIMETYPE_CLAUSE //NON-NLS
                    //grab files with image or video mime-types even if we don't officially support them
                    + " OR mime_type LIKE 'video/%' OR mime_type LIKE 'image/%' )" //NON-NLS
                    + " ORDER BY parent_path ";
        }

        /**
         * Do any cleanup for this task.
         */
        abstract void cleanup();

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
            boolean hasFilesWithNoMime = true;
            boolean endedEarly = false;

            try {
                // See if there are any files in the DS w/out a MIME TYPE
                hasFilesWithNoMime = controller.hasFilesWithNoMimeType(dataSourceObjId);

                //grab all files with detected mime types
                final List<AbstractFile> files = getFiles();
                progressHandle.switchToDeterminate(files.size());

                taskDB.insertOrUpdateDataSource(dataSourceObjId, DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS);

                updateProgress(0.0);
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
                        endedEarly = true;
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
                endedEarly = true;
            } finally {
                progressHandle.finish();

                // Mark to REBUILT_STALE if some files didnt' have MIME (ingest was still ongoing) or 
                // if there was cancellation or errors
                DrawableDB.DrawableDbBuildStatusEnum datasourceDrawableDBStatus
                        = ((hasFilesWithNoMime == true) || (endedEarly == true))
                                ? DrawableDB.DrawableDbBuildStatusEnum.REBUILT_STALE
                                : DrawableDB.DrawableDbBuildStatusEnum.COMPLETE;
                taskDB.insertOrUpdateDataSource(dataSourceObjId, datasourceDrawableDBStatus);

                updateMessage("");
                updateProgress(-1.0);
            }
            cleanup();
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
    static class CopyAnalyzedFiles extends BulkTransferTask {

        CopyAnalyzedFiles(long dataSourceObjId, ImageGalleryController controller) {
            super(dataSourceObjId, controller);
            taskDB.buildFileMetaDataCache();
        }

        @Override
        protected void cleanup() {
            taskDB.freeFileMetaDataCache();
            // at the end of the task, set the stale status based on the 
            // cumulative status of all data sources
            controller.setCaseStale(controller.isDataSourcesTableStale());
        }

        @Override
        void processFile(AbstractFile f, DrawableDB.DrawableTransaction tr, CaseDbTransaction caseDbTransaction) throws TskCoreException {
            final boolean known = f.getKnown() == TskData.FileKnown.KNOWN;

            if (known) {
                taskDB.removeFile(f.getId(), tr);  //remove known files
            } else {
                // NOTE: Files are being processed because they have the right MIME type,
                // so we do not need to worry about this calculating them
                if (FileTypeUtils.hasDrawableMIMEType(f)) {
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
