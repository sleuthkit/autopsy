/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.commons.collections4.CollectionUtils;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceDeletedEvent;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.autopsy.coreutils.Logger;
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
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagSet;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Instances of this class are responsible for fulfilling the controller role in
 * an MVC pattern implementation where the model is the drawables database for a
 * case plus the image gallery tables in the case database, and the view is the
 * image gallery top component.
 */
public final class ImageGalleryController {

    private static final Logger logger = Logger.getLogger(ImageGalleryController.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_STARTED, IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestModuleEvent.DATA_ADDED, IngestManager.IngestModuleEvent.FILE_DONE);
    
    /*
     * The file limit for image gallery. If the selected data source (or all
     * data sources, if that option is selected) has more than this many files
     * in the tsk_files table, the user cannot use the image gallery.
     */
    private static final long FILE_LIMIT = 6_000_000;

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(
            Case.Events.CURRENT_CASE,
            Case.Events.DATA_SOURCE_ADDED,
            Case.Events.CONTENT_TAG_ADDED,
            Case.Events.CONTENT_TAG_DELETED,
            Case.Events.DATA_SOURCE_DELETED
    );

    /*
     * There is an image gallery controller per case. It is created during the
     * opening of case resources and destroyed during the closing of case
     * resources.
     */
    private static final Object controllersByCaseLock = new Object();
    @GuardedBy("controllersByCaseLock")
    private static final Map<String, ImageGalleryController> controllersByCase = new HashMap<>();

    /**
     * A flag that controls whether or not the image gallery controller is
     * handling various application events. Set to true by default.
     */
    private final SimpleBooleanProperty listeningEnabled;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ReadOnlyBooleanWrapper modelStale;

    private final ReadOnlyBooleanWrapper metaDataCollapsed;
    private final SimpleDoubleProperty thumbnailSizeProp;
    private final ReadOnlyBooleanWrapper regroupDisabled;
    private final ReadOnlyIntegerWrapper dbTaskQueueSize;
    private final History<GroupViewState> historyManager;
    private final UndoRedoManager undoManager;
    private final Case theCase;
    private final SleuthkitCase caseDb;
    private final CaseEventListener caseEventListener;
    private final IngestJobEventListener ingestJobEventListener;
    private final IngestModuleEventListener ingestModuleEventListener;
    private volatile ImageGalleryTopComponent topComponent;
    private FileIDSelectionModel selectionModel;
    private ThumbnailCache thumbnailCache;
    private DrawableDB drawableDB;
    private GroupManager groupManager;
    private HashSetManager hashSetManager;
    private CategoryManager categoryManager;
    private DrawableTagsManager tagsManager;
    private ListeningExecutorService dbExecutor;

    /**
     * Creates an image gallery controller for a case. The controller will
     * create/open the model for the case: a local drawables database and the
     * image gallery tables in the case database.
     *
     * @param theCase The case.
     *
     * @throws TskCoreException If there is an issue creating/opening a local
     *                          drawables database for the case or the image
     *                          gallery tables in the case database.
     */
    static void createController(Case theCase) throws TskCoreException {
        synchronized (controllersByCaseLock) {
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
     * @return The controller or null if it does not exist.
     */
    public static ImageGalleryController getController(Case theCase) {
        synchronized (controllersByCaseLock) {
            return controllersByCase.get(theCase.getName());
        }
    }

    /**
     * Shuts down the image gallery controller for a case. The controller closes
     * the model for the case: a local drawables database and the image gallery
     * tables in the case database.
     *
     * @param theCase The case.
     */
    static void shutDownController(Case theCase) {
        ImageGalleryController controller = null;
        synchronized (controllersByCaseLock) {
            if (controllersByCase.containsKey(theCase.getName())) {
                controller = controllersByCase.remove(theCase.getName());
            }
        }
        if (controller != null) {
            controller.shutDown();
        }
    }

    /**
     * Constructs an object that is responsible for fulfilling the controller
     * role in an MVC pattern implementation where the model is the drawables
     * database for a case plus the image gallery tables in the case database,
     * and the view is the image gallery top component.
     *
     * @param theCase The case.
     *
     * @throws TskCoreException If there is an error constructing the
     *                          controller.
     */
    ImageGalleryController(@Nonnull Case theCase) throws TskCoreException {
        this.theCase = Objects.requireNonNull(theCase);
        caseDb = theCase.getSleuthkitCase();
        listeningEnabled = new SimpleBooleanProperty(false);
        modelStale = new ReadOnlyBooleanWrapper(false);
        metaDataCollapsed = new ReadOnlyBooleanWrapper(false);
        thumbnailSizeProp = new SimpleDoubleProperty(100);
        regroupDisabled = new ReadOnlyBooleanWrapper(false);
        dbTaskQueueSize = new ReadOnlyIntegerWrapper(0);
        historyManager = new History<>();
        undoManager = new UndoRedoManager();
        setListeningEnabled(ImageGalleryModule.isEnabledforCase(theCase));
        caseEventListener = new CaseEventListener();
        ingestJobEventListener = new IngestJobEventListener();
        ingestModuleEventListener = new IngestModuleEventListener();
    }

    void startUp() throws TskCoreException {
        selectionModel = new FileIDSelectionModel(this);
        thumbnailCache = new ThumbnailCache(this);

        TagSet categoryTagSet = getCategoryTagSet();
        /*
         * TODO (JIRA-5212): The next two lines need to be executed in this
         * order. Why? This suggests there is some inappropriate coupling
         * between the DrawableDB and GroupManager classes.
         */
        groupManager = new GroupManager(this);
        drawableDB = DrawableDB.getDrawableDB(this, categoryTagSet);
        categoryManager = new CategoryManager(this, categoryTagSet);
        tagsManager = new DrawableTagsManager(this);
        tagsManager.registerListener(groupManager);
        tagsManager.registerListener(categoryManager);
        hashSetManager = new HashSetManager(drawableDB);
        setModelIsStale(isDataSourcesTableStale());
        dbExecutor = getNewDBExecutor();

        listeningEnabled.addListener((observable, wasPreviouslyEnabled, isEnabled) -> {
            try {
                /*
                 * For multi-user cases, this listener does nothing because
                 * rebuilding the drawables database is deferred until the Image
                 * Gallery tool is opened.
                 */
                if (isEnabled && !wasPreviouslyEnabled
                        && (Case.getCurrentCaseThrows().getCaseType() == CaseType.SINGLE_USER_CASE)
                        && isDataSourcesTableStale()) {
                    rebuildDrawablesDb();
                }
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Exception while getting open case.", ex);
            }
        });

        viewStateProperty().addListener((Observable observable) -> {
            selectionModel.clearSelection();
            undoManager.clear();
        });

        /*
         * Disable regrouping when drawables database tasks are enqueued.
         */
        dbTaskQueueSize.addListener(obs -> this.updateRegroupDisabled());

        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, caseEventListener);
        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, ingestJobEventListener);
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, ingestModuleEventListener);

        SwingUtilities.invokeLater(() -> {
            topComponent = ImageGalleryTopComponent.getTopComponent();
        });
    }

    /**
     * Shuts down this image gallery controller.
     */
    public synchronized void shutDown() {
        logger.log(Level.INFO, String.format("Shutting down image gallery controller for case %s (%s)", theCase.getDisplayName(), theCase.getName()));
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, caseEventListener);
        IngestManager.getInstance().removeIngestJobEventListener(ingestJobEventListener);
        IngestManager.getInstance().removeIngestModuleEventListener(ingestModuleEventListener);
        selectionModel.clearSelection();
        thumbnailCache.clearCache();
        historyManager.clear();
        groupManager.reset();
        shutDownDBExecutor();
        drawableDB.close();
        logger.log(Level.INFO, String.format("Completed shut down of image gallery controller for case %s (%s)", theCase.getDisplayName(), theCase.getName()));
    }

    /**
     * Gets the case that provides the model (the local drawables database and
     * the image gallery tables in the case database) for this controller.
     *
     * @return The case.
     */
    public Case getCase() {
        return theCase;
    }

    /**
     * Gets the drawables database that is part of the model for this
     * controller.
     *
     * @return The drawables database.
     */
    public DrawableDB getDrawablesDatabase() {
        return drawableDB;
    }

    /**
     * Gets the case database that provides part of the model for this
     * controller.
     *
     * @return The case database.
     */
    public SleuthkitCase getCaseDatabase() {
        return caseDb;
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
     * Sets a flag indicating whether the model is "stale" for any data source
     * in the current case. The model is a local drawables database and the
     * image gallery tables in the case database.
     *
     * @param isStale True if the model is "stale" for any data source in the
     *                current case.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    void setModelIsStale(Boolean isStale) {
        Platform.runLater(() -> {
            modelStale.set(isStale);
        });
    }

    /**
     * Gets the boolean property that is set to true if the model is "stale" for
     * any data source in the current case. The model is a local drawables
     * database and the image gallery tables in the case database.
     *
     * @return The property that is set to true if the model is "stale" for any
     *         data source in the current case.
     */
    public ReadOnlyBooleanProperty modelIsStaleProperty() {
        return modelStale.getReadOnlyProperty();
    }

    /**
     * Gets the state of the flag that is set if the Model is "stale" for any
     * data source in the case. The model is a local drawables database and the
     * image gallery tables in the case database.
     *
     * @return True if the model is "stale" for any data source in the current
     *         case.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean modelIsStale() {
        return modelStale.get();
    }

    /**
     * Gets the state of the image group display area in the UI.
     *
     * @return The current state.
     */
    public GroupViewState getViewState() {
        return historyManager.getCurrentState();
    }

    /**
     * Gets the state of the image group display area in the UI.
     *
     * @return The current state.
     */
    public ReadOnlyObjectProperty<GroupViewState> viewStateProperty() {
        return historyManager.currentState();
    }

    /**
     * Should the "forward" button on the history be enabled?
     *
     * @return True or false.
     */
    public ReadOnlyBooleanProperty getCanAdvance() {
        return historyManager.getCanAdvance();
    }

    /**
     * Should the "Back" button on the history be enabled?
     *
     * @return True or false.
     */
    public ReadOnlyBooleanProperty getCanRetreat() {
        return historyManager.getCanRetreat();
    }

    /**
     * Displays the passed in image group. Causes this group to get recorded in
     * the history queue and observers of the current state will be notified and
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
    public void rebuildDrawablesDb() {
        queueDBTask(new DrawableFileUpdateTask(this));
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
        if ((null == getDrawablesDatabase()) || (null == getCaseDatabase())) {
            return staleDataSourceIds;
        }

        try {
            Map<Long, DrawableDbBuildStatusEnum> knownDataSourceIds = getDrawablesDatabase().getDataSourceDbBuildStatus();

            List<DataSource> dataSources = getCaseDatabase().getDataSources();
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
        if ((null == getDrawablesDatabase()) || (null == getCaseDatabase())) {
            return dataSourceStatusMap;
        }

        try {
            Map<Long, DrawableDbBuildStatusEnum> knownDataSourceIds = getDrawablesDatabase().getDataSourceDbBuildStatus();

            List<DataSource> dataSources = getCaseDatabase().getDataSources();
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

        return caseDb.countFilesWhere(whereClause) > FILE_LIMIT;

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

        return caseDb.countFilesWhere(whereClause) > 0;
    }

    public boolean hasFilesWithMimeType(long dataSourceId) throws TskCoreException {

        String whereClause = "data_source_obj_id = " + dataSourceId
                + " AND ( meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue() + ")"
                + " AND ( mime_type IS NOT NULL )";

        return caseDb.countFilesWhere(whereClause) > 0;
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
                new ThreadFactoryBuilder().setNameFormat("ImageGallery-DB-Worker-Thread-%d").build()));
    }

    /**
     * add InnerTask to the queue that the worker thread gets its work from
     *
     * @param bgTask
     */
    public synchronized void queueDBTask(Runnable bgTask) {
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
     * Returns the TagSet with the image gallery categories.
     *
     * @return Category TagSet.
     *
     * @throws TskCoreException
     */
    private TagSet getCategoryTagSet() throws TskCoreException {
        List<TagSet> tagSetList = getCaseDatabase().getTaggingManager().getTagSets();
        if (tagSetList != null && !tagSetList.isEmpty()) {
            for (TagSet set : tagSetList) {
                if (set.getName().equals(ImageGalleryService.PROJECT_VIC_TAG_SET_NAME)) {
                    return set;
                }
            }
            // If we get to here the Project VIC Test set wasn't found;
            throw new TskCoreException("Error loading Project VIC tag set: Tag set not found.");
        } else {
            throw new TskCoreException("Error loading Project VIC tag set: Tag set not found.");
        }
    }


    /**
     * A listener for ingest module application events.
     */
    private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            /*
             * Disable regrouping when ingest is running.
             */
            Platform.runLater(ImageGalleryController.this::updateRegroupDisabled);

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
                            queueDBTask(new UpdateDrawableFileTask(file, drawableDB));
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
                if (event.getOldValue() != null) { // Case closed event
                    if (topComponent != null) {
                        topComponent.closeForCurrentCase();
                    }
                    SwingUtilities.invokeLater(ImageGalleryTopComponent::closeTopComponent);
                }
            } else {
                switch (eventType) {
                    case DATA_SOURCE_ADDED:
                        if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                            Content newDataSource = (Content) event.getNewValue();
                            if (isListeningEnabled()) {
                                try {
                                    // If the data source already exists and has a status other than UNKNOWN, don’t overwrite it. 
                                    if(drawableDB.getDataSourceDbBuildStatus(newDataSource.getId()) == DrawableDB.DrawableDbBuildStatusEnum.UNKNOWN) {
                                        drawableDB.insertOrUpdateDataSource(newDataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.UNKNOWN);
                                    }
                                } catch (SQLException | TskCoreException ex) {
                                    logger.log(Level.SEVERE, String.format("Error updating datasources table (data source object ID = %d, status = %s)", newDataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.UNKNOWN.toString()), ex); //NON-NLS
                                }
                            }
                        }
                        break;
                    case DATA_SOURCE_DELETED:
                        if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                            final DataSourceDeletedEvent dataSourceDeletedEvent = (DataSourceDeletedEvent) event;
                            long dataSourceObjId = dataSourceDeletedEvent.getDataSourceId();
                            try {
                                drawableDB.deleteDataSource(dataSourceObjId);
                            } catch (SQLException | TskCoreException ex) {
                                logger.log(Level.SEVERE, String.format("Failed to delete data source (obj_id = %d)", dataSourceObjId), ex); //NON-NLS
                            }
                        }
                        break;
                    case CONTENT_TAG_ADDED:
                        final ContentTagAddedEvent tagAddedEvent = (ContentTagAddedEvent) event;
                        long objId = tagAddedEvent.getAddedTag().getContent().getId();
                        drawableDB.addTagCache(objId); // TODO (JIRA-5216): Why add the tag to the cache before doing the in DB check?
                        if (drawableDB.isInDB(objId)) {
                            tagsManager.fireTagAddedEvent(tagAddedEvent);
                        }
                        break;
                    case CONTENT_TAG_DELETED:
                        final ContentTagDeletedEvent tagDeletedEvent = (ContentTagDeletedEvent) event;
                        if (drawableDB.isInDB(tagDeletedEvent.getDeletedTagInfo().getContentID())) {
                            tagsManager.fireTagDeletedEvent(tagDeletedEvent);
                        } // TODO (JIRA-5216): Why not remove the tag from the cache?
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
             * Disable regrouping when ingest is running.
             */
            Platform.runLater(ImageGalleryController.this::updateRegroupDisabled);

            /*
             * Only handling data source analysis events.
             */
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
                        handleDataSourceAnalysisStarted(dataSourceEvent);
                        break;
                    case DATA_SOURCE_ANALYSIS_COMPLETED:
                        handleDataSourceAnalysisCompleted(dataSourceEvent);
                        break;
                    default:
                        break;
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.SEVERE, String.format("Failed to handle %s event for %s (objId=%d)", dataSourceEvent.getPropertyName(), dataSource.getName(), dataSourceObjId), ex);
            }
        }
    }

    /**
     * Handles a data source analysis started event by adding the data source to
     * the drawables database.
     *
     * @param event The event.
     *
     * @throws TskCoreException If there is an error adding the data source to
     *                          the database.
     */
    private void handleDataSourceAnalysisStarted(DataSourceAnalysisEvent event) throws TskCoreException, SQLException {
        if (event.getSourceType() == AutopsyEvent.SourceType.LOCAL && isListeningEnabled()) {
            Content dataSource = event.getDataSource();
            long dataSourceObjId = dataSource.getId();
            if (drawableDB.getDataSourceDbBuildStatus(dataSourceObjId) != DrawableDB.DrawableDbBuildStatusEnum.COMPLETE) {
                drawableDB.insertOrUpdateDataSource(dataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS);
            }
            drawableDB.buildFileMetaDataCache();
        }
    }

    /**
     * Handles a data source analysis completed event by updating the state of
     * the data source stored in the drawables database if the event is local or
     * prompting the user to do a refresh if the event is remote.
     *
     * @param event The event.
     *
     * @throws TskCoreException If there is an error updating the state ot the
     *                          data source in the database.
     */
    private void handleDataSourceAnalysisCompleted(DataSourceAnalysisEvent event) throws TskCoreException, SQLException {
        if (event.getSourceType() == AutopsyEvent.SourceType.LOCAL) {
            Content dataSource = event.getDataSource();
            long dataSourceObjId = dataSource.getId();
            /*
             * This node just completed analysis of a data source. Set the state
             * of the local drawables database.
             */
            if (isListeningEnabled()) {
                queueDBTask(new HandleDataSourceAnalysisCompleteTask(dataSourceObjId, this));
            }
        } else if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.REMOTE) {
            /*
             * A remote node just completed analysis of a data source. The local
             * drawables database is therefore stale. If the image gallery top
             * component is open, give the user an opportunity to update the
             * drawables database now.
             */
            setModelIsStale(true);
            if (isListeningEnabled()) {
                SwingUtilities.invokeLater(() -> {
                    if (ImageGalleryTopComponent.isImageGalleryOpen()) {
                        int showAnswer = JOptionPane.showConfirmDialog(ImageGalleryTopComponent.getTopComponent(),
                                Bundle.ImageGalleryController_dataSourceAnalyzed_confDlg_msg(),
                                Bundle.ImageGalleryController_dataSourceAnalyzed_confDlg_title(),
                                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                        switch (showAnswer) {
                            case JOptionPane.YES_OPTION:
                                rebuildDrawablesDb();
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
    }

}
