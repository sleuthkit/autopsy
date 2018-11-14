/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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
import java.util.logging.Level;
import javafx.application.Platform;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestJobEvent;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.FILE_DONE;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisCompletedEvent;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisStartedEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/** static definitions, utilities, and listeners for the ImageGallery module */
@NbBundle.Messages({"ImageGalleryModule.moduleName=Image Gallery"})
public class ImageGalleryModule {

    private static final Logger logger = Logger.getLogger(ImageGalleryModule.class.getName());

    private static final String MODULE_NAME = Bundle.ImageGalleryModule_moduleName();

    private static final Object controllerLock = new Object();
    private static ImageGalleryController controller;

    public static ImageGalleryController getController() throws TskCoreException, NoCurrentCaseException {
        synchronized (controllerLock) {
            if (controller == null) {
                controller = new ImageGalleryController(Case.getCurrentCaseThrows());
            }
            return controller;
        }
    }

    /**
     *
     *
     * This method is invoked by virtue of the OnStart annotation on the OnStart
     * class class
     */
    static void onStart() {
        Platform.setImplicitExit(false);
        logger.info("Setting up ImageGallery listeners"); //NON-NLS

        IngestManager.getInstance().addIngestJobEventListener(new IngestJobEventListener());
        IngestManager.getInstance().addIngestModuleEventListener(new IngestModuleEventListener());
        Case.addPropertyChangeListener(new CaseEventListener());
    }

    static String getModuleName() {
        return MODULE_NAME;
    }

    /**
     * get the Path to the Case's ImageGallery ModuleOutput subfolder; ie
     * ".../[CaseName]/ModuleOutput/Image Gallery/"
     *
     * @param theCase the case to get the ImageGallery ModuleOutput subfolder
     *                for
     *
     * @return the Path to the ModuleOuput subfolder for Image Gallery
     */
    public static Path getModuleOutputDir(Case theCase) {
        return Paths.get(theCase.getModuleDirectory(), getModuleName());
    }

    /** provides static utilities, can not be instantiated */
    private ImageGalleryModule() {
    }

    /** is listening enabled for the given case
     *
     * @param c
     *
     * @return true if listening is enabled for the given case, false otherwise
     */
    static boolean isEnabledforCase(Case c) {
        if (c != null) {
            String enabledforCaseProp = new PerCaseProperties(c).getConfigSetting(ImageGalleryModule.MODULE_NAME, PerCaseProperties.ENABLED);
            return isNotBlank(enabledforCaseProp) ? Boolean.valueOf(enabledforCaseProp) : ImageGalleryPreferences.isEnabledByDefault();
        } else {
            return false;
        }
    }

    /**
     * Is the given file 'supported' and not 'known'(nsrl hash hit). If so we
     * should include it in {@link DrawableDB} and UI
     *
     * @param abstractFile
     *
     * @return true if the given {@link AbstractFile} is "drawable" and not
     *         'known', else false
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException
     */
    public static boolean isDrawableAndNotKnown(AbstractFile abstractFile) throws FileTypeDetector.FileTypeDetectorInitException {
        return (abstractFile.getKnown() != TskData.FileKnown.KNOWN) && FileTypeUtils.isDrawable(abstractFile);
    }

    /**
     * Listener for IngestModuleEvents
     */
    static private class IngestModuleEventListener implements PropertyChangeListener {

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

            /* only process individual files in realtime on the node that is
             * running the ingest. on a remote node, image files are processed
             * enblock when ingest is complete */
            if (((AutopsyEvent) evt).getSourceType() != AutopsyEvent.SourceType.LOCAL) {
                return;
            }

            // Bail out if the case is closed
            try {
                if (controller == null || Case.getCurrentCaseThrows() == null) {
                    return;
                }
            } catch (NoCurrentCaseException ex) {
                return;
            }

            if (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName()) == FILE_DONE) {

                // getOldValue has fileID getNewValue has  Abstractfile
                AbstractFile file = (AbstractFile) evt.getNewValue();
                if (false == file.isFile()) {
                    return;
                }

                try {
                    ImageGalleryController con = getController();
                    if (con.isListeningEnabled()) {
                        try {
                            // Update the entry if it is a picture and not in NSRL
                            if (isDrawableAndNotKnown(file)) {
                                con.queueDBTask(new ImageGalleryController.UpdateFileTask(file, controller.getDatabase()));
                            } 
                            // Remove it from the DB if it is no longer relevant, but had the correct extension
                            else if (FileTypeUtils.getAllSupportedExtensions().contains(file.getNameExtension())) {
                                /* Doing this check results in fewer tasks queued
                                 * up, and faster completion of db update. This file
                                 * would have gotten scooped up in initial grab, but
                                 * actually we don't need it */
                                con.queueDBTask(new ImageGalleryController.RemoveFileTask(file, controller.getDatabase()));
                            }
                        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                            logger.log(Level.SEVERE, "Unable to determine if file is drawable and not known.  Not making any changes to DB", ex); //NON-NLS
                            MessageNotifyUtil.Notify.error("Image Gallery Error",
                                    "Unable to determine if file is drawable and not known.  Not making any changes to DB.  See the logs for details.");
                        }
                    }
                } catch (NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Attempted to access ImageGallery with no case open.", ex); //NON-NLS
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting ImageGalleryController.", ex); //NON-NLS
                }
            }
            else if (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName()) == DATA_ADDED) {
                ModuleDataEvent mde = (ModuleDataEvent) evt.getOldValue();

                if (mde.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
                    DrawableDB drawableDB = controller.getDatabase();
                    if (mde.getArtifacts() != null) {
                        for (BlackboardArtifact art : mde.getArtifacts()) {
                            drawableDB.addExifCache(art.getObjectID());
                        }
                    }
                }
                else if (mde.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                    DrawableDB drawableDB = controller.getDatabase();
                    if (mde.getArtifacts() != null) {
                        for (BlackboardArtifact art : mde.getArtifacts()) {
                            drawableDB.addHashSetCache(art.getObjectID());
                        }
                    }
                }
            }
        }
    }

    /**
     * Listener for case events.
     */
    static private class CaseEventListener implements PropertyChangeListener {

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
            ImageGalleryController con;
            try {
                con = getController();
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Attempted to access ImageGallery with no case open.", ex); //NON-NLS
                return;
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting ImageGalleryController.", ex); //NON-NLS
                return;
            }
            switch (Case.Events.valueOf(evt.getPropertyName())) {
                case CURRENT_CASE:
                    synchronized (controllerLock) {
                        // case has changes: close window, reset everything 
                        SwingUtilities.invokeLater(ImageGalleryTopComponent::closeTopComponent);
                        if (controller != null) {
                            controller.reset();
                        }
                        controller = null;

                        Case newCase = (Case) evt.getNewValue();
                        if (newCase != null) {
                            // a new case has been opened: connect db, groupmanager, start worker thread
                            try {
                                controller = new ImageGalleryController(newCase);
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error changing case in ImageGallery.", ex);
                            }
                        }
                    }
                    break;
                case DATA_SOURCE_ADDED:
                    //For a data source added on the local node, prepopulate all file data to drawable database
                    if (((AutopsyEvent) evt).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                        Content newDataSource = (Content) evt.getNewValue();
                        if (con.isListeningEnabled()) { 
                            controller.getDatabase().insertOrUpdateDataSource(newDataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.DEFAULT);
                        }
                    }
                    break;
                case CONTENT_TAG_ADDED:
                    final ContentTagAddedEvent tagAddedEvent = (ContentTagAddedEvent) evt;

                    long objId = tagAddedEvent.getAddedTag().getContent().getId();

                    // update the cache
                    DrawableDB drawableDB = controller.getDatabase();
                    drawableDB.addTagCache(objId);

                    if (con.getDatabase().isInDB(objId)) {
                        con.getTagsManager().fireTagAddedEvent(tagAddedEvent);
                    }
                    break;
                case CONTENT_TAG_DELETED:
                    final ContentTagDeletedEvent tagDeletedEvent = (ContentTagDeletedEvent) evt;
                    if (con.getDatabase().isInDB(tagDeletedEvent.getDeletedTagInfo().getContentID())) {
                        con.getTagsManager().fireTagDeletedEvent(tagDeletedEvent);
                    }
                    break;
                default:
                    //we don't need to do anything for other events.
                    break;
            }
        }
    }

    /**
     * Listener for Ingest Job events.
     */
    static private class IngestJobEventListener implements PropertyChangeListener {

        @NbBundle.Messages({
            "ImageGalleryController.dataSourceAnalyzed.confDlg.msg= A new data source was added and finished ingest.\n"
            + "The image / video database may be out of date. "
            + "Do you want to update the database with ingest results?\n",
            "ImageGalleryController.dataSourceAnalyzed.confDlg.title=Image Gallery"
        })
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            IngestJobEvent eventType = IngestJobEvent.valueOf(evt.getPropertyName());
            
            try {
                ImageGalleryController controller = getController();
            
                if (eventType == IngestJobEvent.DATA_SOURCE_ANALYSIS_STARTED) {
                    
                    if (((AutopsyEvent) evt).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                        if (controller.isListeningEnabled()) {
                        DataSourceAnalysisStartedEvent dataSourceAnalysisStartedEvent = (DataSourceAnalysisStartedEvent) evt;
                            Content dataSource = dataSourceAnalysisStartedEvent.getDataSource();
                           
                            controller.getDatabase().insertOrUpdateDataSource(dataSource.getId(), DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS);   
                        }
                    }
                } else if (eventType == IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED) {
                    
                    if (((AutopsyEvent) evt).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                        if (controller.isListeningEnabled()) {
                            DataSourceAnalysisCompletedEvent dataSourceAnalysisCompletedEvent = (DataSourceAnalysisCompletedEvent) evt;
                            Content dataSource = dataSourceAnalysisCompletedEvent.getDataSource();
                            
                            DrawableDB.DrawableDbBuildStatusEnum datasourceDrawableDBStatus = 
                                    controller.hasFilesWithNoMimetype(dataSource) ?
                                        DrawableDB.DrawableDbBuildStatusEnum.DEFAULT : 
                                        DrawableDB.DrawableDbBuildStatusEnum.COMPLETE;
                            
                            controller.getDatabase().insertOrUpdateDataSource(dataSource.getId(), datasourceDrawableDBStatus);
                        }
                        return;
                    }   
                
                    if (((AutopsyEvent) evt).getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                        // A remote node added a new data source and just finished ingest on it.
                        //drawable db is stale, and if ImageGallery is open, ask user what to do
                        controller.setStale(true);
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
                                            break; //do nothing
                                    }
                                }
                            });
                        }
                    }
                }
            }
            catch (NoCurrentCaseException ex) {
               logger.log(Level.SEVERE, "Attempted to access ImageGallery with no case open.", ex); //NON-NLS
            } catch (TskCoreException ex) {
               logger.log(Level.SEVERE, "Error getting ImageGalleryController.", ex); //NON-NLS
            }
        }
    }
}
