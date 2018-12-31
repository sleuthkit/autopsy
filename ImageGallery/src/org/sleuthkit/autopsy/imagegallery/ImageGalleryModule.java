/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import javafx.application.Platform;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.commons.collections4.CollectionUtils;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.FILE_DONE;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * This class is reponsible for handling selected application events for the
 * image gallery module, managing the image gallery module's per case MVC
 * controller and keeping track of the following state: the module name, the
 * module output directory and whether or not the ingest gallery module is
 * enabled for the current case.
 */
@NbBundle.Messages({"ImageGalleryModule.moduleName=Image Gallery"})
public class ImageGalleryModule {

    private static final Logger logger = Logger.getLogger(ImageGalleryModule.class.getName());
    private static final String MODULE_NAME = Bundle.ImageGalleryModule_moduleName();
    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(
            Case.Events.CURRENT_CASE,
            Case.Events.DATA_SOURCE_ADDED,
            Case.Events.CONTENT_TAG_ADDED,
            Case.Events.CONTENT_TAG_DELETED
    );
    private static final Object controllerLock = new Object();
    @GuardedBy("controllerLock")
    private static ImageGalleryController controller;

    /**
     * Creates an image gallery controller for a case, assumed to be the current
     * case. As a result, creates/opens a local drawables database and
     * creates/updates the Image Gallery tables in the case database.
     *
     * @param currentCase The current case.
     *
     * @throws TskCoreException
     */
    static void createController(Case currentCase) throws TskCoreException {
        synchronized (controllerLock) {
            controller = new ImageGalleryController(currentCase);
        }
    }

    /**
     * Shuts down the current image gallery controller.
     */
    static void shutDownController() {
        synchronized (controllerLock) {
            if (controller != null) {
                controller.shutDown();
            }
        }
    }

    /**
     * Gets the per case image gallery controller for the current case. The
     * controller is changed in the case event listener.
     *
     * @return The image gallery controller for the current case.
     *
     * @throws TskCoreException If there is a problem creating the controller.
     */
    public static ImageGalleryController getController() throws TskCoreException {
        synchronized (controllerLock) {
            if (controller == null) {
                try {
                    createController(Case.getCurrentCaseThrows());
                } catch (NoCurrentCaseException ex) {
                    throw new TskCoreException("Failed to get current case", ex);
                }
            }
            return controller;
        }
    }

    /**
     * Sets the implicit exit property attribute of the JavaFX runtime to false
     * and sets up listeners for application events. It is invoked at
     * application start up by virtue of the OnStart annotation on the OnStart
     * class in this package.
     */
    static void onStart() {
        Platform.setImplicitExit(false);
        IngestManager.getInstance().addIngestJobEventListener(new IngestJobEventListener());
        IngestManager.getInstance().addIngestModuleEventListener(new IngestModuleEventListener());
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, new CaseEventListener());
    }

    /**
     * Gets the image gallery module name.
     *
     * @return The module name,
     */
    static String getModuleName() {
        return MODULE_NAME;
    }

    /**
     * Gets the path to the image gallery module output folder for a given case.
     *
     * @param theCase The case.
     *
     * @return The path to the image gallery module output folder for the case.
     */
    public static Path getModuleOutputDir(Case theCase) {
        return Paths.get(theCase.getModuleDirectory(), getModuleName());
    }

    /**
     * Prevents instantiation.
     */
    private ImageGalleryModule() {
    }

    /**
     * Indicates whether or not the image gallery module is enabled for a given
     * case.
     *
     * @param theCase The case.
     *
     * @return True or false.
     */
    static boolean isEnabledforCase(@Nonnull Case theCase) {
        String enabledforCaseProp = new PerCaseProperties(theCase).getConfigSetting(ImageGalleryModule.MODULE_NAME, PerCaseProperties.ENABLED);
        return isNotBlank(enabledforCaseProp) ? Boolean.valueOf(enabledforCaseProp) : ImageGalleryPreferences.isEnabledByDefault();
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
    static private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            /*
             * Only process individual files and artifacts in "real time" on the
             * node that is running the ingest job. On a remote node, image
             * files are processed as a group when the ingest job is complete.
             */
            if (((AutopsyEvent) event).getSourceType() != AutopsyEvent.SourceType.LOCAL) {
                return;
            }

            ImageGalleryController currentController;
            try {
                currentController = getController();
                // RJCTODO: If a closed controller had a method that could be
                // queried to determine whether it was shut down, we could 
                // bail out here. The older code that used to try to check for
                // a current case was flawed; there was no guarantee the current
                // case was the same case associated with the event.
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to handle %s event", event.getPropertyName()), ex); //NON-NLS
                return;
            }

            if (currentController.isListeningEnabled() == false) {
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
                            currentController.queueDBTask(new ImageGalleryController.UpdateFileTask(file, currentController.getDatabase()));
                        }
                    } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to determine if file is of interest to the image gallery module, ignoring file (obj_id=%d)", file.getId()), ex); //NON-NLS
                    }
                    break;
                case DATA_ADDED:
                    ModuleDataEvent artifactAddedEvent = (ModuleDataEvent) event.getOldValue();
                    if (CollectionUtils.isNotEmpty(artifactAddedEvent.getArtifacts())) {
                        DrawableDB drawableDB = currentController.getDatabase();
                        for (BlackboardArtifact art : artifactAddedEvent.getArtifacts()) {
                            if (artifactAddedEvent.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
                                drawableDB.addExifCache(art.getObjectID());
                            } else if (artifactAddedEvent.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
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
    // RJCTODO: This code would be easier to read if there were two case event 
    // listeners, one that handled CURRENT_CASE events and one that handled 
    // the other events. Or event better, move the handling of Case events other 
    // than CURRENT_CASE into ImageGalleryController.
    static private class CaseEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            Case.Events eventType = Case.Events.valueOf(event.getPropertyName());
            if (eventType == Case.Events.CURRENT_CASE && event.getOldValue() != null) {
                SwingUtilities.invokeLater(ImageGalleryTopComponent::closeTopComponent);
            } else {
                ImageGalleryController currentController;
                try {
                    currentController = getController();
                    // RJCTODO: I think it would be best to move handling of these 
                    // case events into the controller class and have the controller 
                    // instance register/unregister as a listener when it is 
                    // contructed and shuts down. This will improve the encapsulation 
                    // of ImageGalleryController and allow it to check its own open/closed 
                    // state before handling an event. 
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to handle %s event", event.getPropertyName()), ex); //NON-NLS
                    return;
                }

                switch (eventType) {
                    case DATA_SOURCE_ADDED:
                        if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                            Content newDataSource = (Content) event.getNewValue();
                            if (currentController.isListeningEnabled()) {
                                currentController.getDatabase().insertOrUpdateDataSource(newDataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.UNKNOWN);
                            }
                        }
                        break;
                    case CONTENT_TAG_ADDED:
                        final ContentTagAddedEvent tagAddedEvent = (ContentTagAddedEvent) event;
                        long objId = tagAddedEvent.getAddedTag().getContent().getId();
                        DrawableDB drawableDB = currentController.getDatabase();
                        drawableDB.addTagCache(objId); // RJCTODO: Why add the tag to the cache before doing the in DB check?
                        if (drawableDB.isInDB(objId)) {
                            currentController.getTagsManager().fireTagAddedEvent(tagAddedEvent);
                        }
                        break;
                    case CONTENT_TAG_DELETED:
                        final ContentTagDeletedEvent tagDeletedEvent = (ContentTagDeletedEvent) event;
                        if (currentController.getDatabase().isInDB(tagDeletedEvent.getDeletedTagInfo().getContentID())) {
                            currentController.getTagsManager().fireTagDeletedEvent(tagDeletedEvent);
                        } // RJCTODO: Why not remove the tag from the cache?
                        break;
                    default:
                        logger.log(Level.SEVERE, String.format("Received %s event with no subscription", event.getPropertyName())); //NON-NLS
                        break;
                }
            }
        }
    }

    /**
     * A listener for ingest job application events.
     */
    static private class IngestJobEventListener implements PropertyChangeListener {

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

            ImageGalleryController controller;
            try {
                controller = getController();
                // RJCTODO: I think it would be best to move handling of these 
                // case events into the controller class and have the controller 
                // instance register/unregister as a listener when it is 
                // contructed and shuts down. This will improve the encapsulation 
                // of ImageGalleryController and allow it to check its own open/closed 
                // state before handling an event.                 
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to handle %s event", event.getPropertyName()), ex); //NON-NLS
                return;
            }

            DataSourceAnalysisEvent dataSourceEvent = (DataSourceAnalysisEvent) event;
            Content dataSource = dataSourceEvent.getDataSource();
            if (dataSource == null) {
                logger.log(Level.SEVERE, String.format("Failed to handle %s event", event.getPropertyName())); //NON-NLS
                return;
            }

            long dataSourceObjId = dataSource.getId();
            String eventType = dataSourceEvent.getPropertyName();
            try {
                switch (IngestManager.IngestJobEvent.valueOf(eventType)) {
                    case DATA_SOURCE_ANALYSIS_STARTED:
                        if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                            if (controller.isListeningEnabled()) {
                                DrawableDB drawableDb = controller.getDatabase();
                                // Don't update status if it is is already marked as COMPLETE
                                if (drawableDb.getDataSourceDbBuildStatus(dataSourceObjId) != DrawableDB.DrawableDbBuildStatusEnum.COMPLETE) {
                                    drawableDb.insertOrUpdateDataSource(dataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS);
                                }
                                drawableDb.buildFileMetaDataCache();
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
                            if (controller.isListeningEnabled()) {
                                DrawableDB drawableDb = controller.getDatabase();
                                if (drawableDb.getDataSourceDbBuildStatus(dataSourceObjId) == DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS) {

                                    // If at least one file in CaseDB has mime type, then set to COMPLETE
                                    // Otherwise, back to UNKNOWN since we assume file type module was not run        
                                    DrawableDB.DrawableDbBuildStatusEnum datasourceDrawableDBStatus
                                            = controller.hasFilesWithMimeType(dataSourceObjId)
                                            ? DrawableDB.DrawableDbBuildStatusEnum.COMPLETE
                                            : DrawableDB.DrawableDbBuildStatusEnum.UNKNOWN;

                                    controller.getDatabase().insertOrUpdateDataSource(dataSource.getId(), datasourceDrawableDBStatus);
                                }
                                controller.getDatabase().freeFileMetaDataCache();
                            }
                        } else if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                            /*
                             * A remote node just completed analysis of a data
                             * source. The local drawables database is therefore
                             * stale. If the image gallery top component is
                             * open, give the user an opportunity to update the
                             * drawables database now.
                             */
                            controller.setCaseStale(true);
                            if (controller.isListeningEnabled()) {
                                SwingUtilities.invokeLater(() -> {
                                    if (ImageGalleryTopComponent.isImageGalleryOpen()) {
                                        int showAnswer = JOptionPane.showConfirmDialog(ImageGalleryTopComponent.getTopComponent(),
                                                Bundle.ImageGalleryController_dataSourceAnalyzed_confDlg_msg(),
                                                Bundle.ImageGalleryController_dataSourceAnalyzed_confDlg_title(),
                                                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                                        switch (showAnswer) {
                                            case JOptionPane.YES_OPTION:
                                                controller.rebuildDB();
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
}
