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
import org.sleuthkit.autopsy.ingest.IngestManager.IngestJobEvent;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.FILE_DONE;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * static definitions, utilities, and listeners for the ImageGallery module
 */
@NbBundle.Messages({"ImageGalleryModule.moduleName=Image Gallery"})
public class ImageGalleryModule {

    private static final Logger logger = Logger.getLogger(ImageGalleryModule.class.getName());

    private static final String MODULE_NAME = Bundle.ImageGalleryModule_moduleName();

    private static final Object controllerLock = new Object();
    private static ImageGalleryController controller;

    /**
     * Gets the per case singleton image gallery controller for the current
     * case.
     *
     * @return The image gallery controller.
     *
     * @throws NoCurrentCaseException If there is no current case.
     * @throws TskCoreException       If there is a problem creating the
     *                                controller.
     */
    public static ImageGalleryController getController() throws TskCoreException, NoCurrentCaseException {
        synchronized (controllerLock) {
            if (controller == null) {
                controller = new ImageGalleryController(Case.getCurrentCaseThrows());
            }
            return controller;
        }
    }

    /**
     * Shuts down the per case singleton image gallery controller for the
     * current case (FRAGILE!).
     */
    public static void shutDownController() {
        synchronized (controllerLock) {
            if (controller != null) {
                controller.shutDown();
            }
            controller = null;
        }
    }

    /**
     * Sets the implicit exit property attribute of the JavaFX Runtime to false
     * and sets up listeners for application events. It is invoked at
     * application start up by virtue of the OnStart annotation on the OnStart
     * class in this package.
     */
    static void onStart() {
        Platform.setImplicitExit(false);
        logger.info("Setting up ImageGallery listeners"); //NON-NLS
        IngestManager.getInstance().addIngestJobEventListener(new IngestJobEventListener());
        IngestManager.getInstance().addIngestModuleEventListener(new IngestModuleEventListener());
        Case.addPropertyChangeListener(new CaseEventListener());
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
     * @return The path to the module output folder for the case.
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
     * Indicates whether or not the image gallery module is handling application
     * events for a given case.
     *
     * @param theCase
     *
     * @return True if application event handling is enabled for the given case,
     *         false otherwise.
     */
    static boolean isEnabledforCase(Case theCase) {
        if (theCase != null) {
            String enabled = new PerCaseProperties(theCase).getConfigSetting(ImageGalleryModule.MODULE_NAME, PerCaseProperties.ENABLED);
            return isNotBlank(enabled) ? Boolean.valueOf(enabled) : ImageGalleryPreferences.isEnabledByDefault();
        } else {
            return false;
        }
    }

    /**
     * Indicates whether or not a given file is supported by the image gallery
     * (is "drawable") and is not marked as a "known" file (e.g., is a file in
     * the NSRL hash set).
     *
     * @param file The file.
     *
     * @return True if the file is "drawable" and not "known", false otherwise.
     *
     * @throws FileTypeDetectorInitExceptioif there is an error determining the
     *                                        type of the file.
     */
    private static boolean isDrawableAndNotKnown(AbstractFile file) throws FileTypeDetector.FileTypeDetectorInitException {
        return (file.getKnown() != TskData.FileKnown.KNOWN) && FileTypeUtils.isDrawable(file);
    }

    /**
     * A listener for ingest module application events.
     */
    static private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            /*
             * If running in "headless" mode, there is no need to process any
             * ingest module events during the current session.
             *
             * Note that this check cannot be done earlier on start up because
             * the "headless" property may not have been set yet.
             */
            if (RuntimeProperties.runningWithGUI() == false) {
                IngestManager.getInstance().removeIngestModuleEventListener(this);
                return;
            }

            /*
             * Only process individual files and artifacts in "real time" on the
             * node that is running the ingest job. On a remote node, image
             * files are processed enbloc when the ingest job is complete.
             */
            if (((AutopsyEvent) evt).getSourceType() != AutopsyEvent.SourceType.LOCAL) {
                return;
            }

            /*
             * Get the image gallery controller for the current case, and check
             * whether event handling is enabled for the image gallery for this
             * case.
             */
            String eventType = evt.getPropertyName();
            ImageGalleryController controller;
            try {
                controller = getController();
                if (controller.isListeningEnabled() == false) {
                    return;
                }
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, String.format("Attempted to handle ingest module event (%s) with no current case", eventType), ex); //NON-NLS
                return;
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Unable to get image gallery controller to handle ingest module event (%s)", eventType), ex); //NON-NLS
                return;
            }

            /*
             * Add analyzed files to the drawables database and add selected
             * artifacts to the artifacts cache.
             */
            switch (IngestManager.IngestModuleEvent.valueOf(eventType)) {
                case FILE_DONE:
                    AbstractFile file = (AbstractFile) evt.getNewValue();
                    if (file.isFile()) {
                        try {
                            // Update the entry if it is a picture and not in NSRL
                            if (isDrawableAndNotKnown(file)) {
                                controller.queueDBTask(new ImageGalleryController.UpdateFileTask(file, controller.getDatabase()));
                            } // Remove it from the DB if it is no longer relevant, but had the correct extension
                            else if (FileTypeUtils.getAllSupportedExtensions().contains(file.getNameExtension())) {
                                /*
                                 * Doing this check results in fewer tasks
                                 * queued up, and faster completion of db
                                 * update. This file would have gotten scooped
                                 * up in initial grab, but actually we don't
                                 * need it.
                                 */
                                controller.queueDBTask(new ImageGalleryController.RemoveFileTask(file, controller.getDatabase()));
                            }
                        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                            logger.log(Level.SEVERE, String.format("Error determining file type for %s (obj_id = %s), FILE_DONE event not handled", file.getName(), file.getId()), ex); //NON-NLS
                        }
                    }
                    break;
                case DATA_ADDED:
                    ModuleDataEvent mde = (ModuleDataEvent) evt.getOldValue();
                    if (mde.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
                        DrawableDB drawableDB = controller.getDatabase();
                        for (BlackboardArtifact art : mde.getArtifacts()) {
                            drawableDB.addExifCache(art.getObjectID());
                        }
                    } else if (mde.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                        DrawableDB drawableDB = controller.getDatabase();
                        for (BlackboardArtifact art : mde.getArtifacts()) {
                            drawableDB.addHashSetCache(art.getObjectID());
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
    static private class CaseEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            /*
             * If running in "headless" mode, there is no need to process any
             * case events during the current session.
             *
             * Note that this check cannot be done earlier on start up because
             * the "headless" property may not have been set yet.
             */
            if (RuntimeProperties.runningWithGUI() == false) {
                Case.removePropertyChangeListener(this);
                return;
            }

            /*
             * Ignore everything other than a subset of case events.
             */
            Case.Events eventType = Case.Events.valueOf(evt.getPropertyName());
            if ((eventType != Case.Events.CURRENT_CASE)
                    && (eventType != Case.Events.DATA_SOURCE_ADDED)
                    && (eventType != Case.Events.CONTENT_TAG_ADDED)
                    && (eventType != Case.Events.CONTENT_TAG_DELETED)) {
                return;
            }

            /*
             * Get the image gallery controller for the current case. Note that
             * this has the SIDE EFFECT of creating the controller, so there is
             * no need to do anything more for a CURRENT_CASE(_OPENED) event.
             */
            ImageGalleryController controller;
            try {
                controller = getController();
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, String.format("Attempted to handle case event (%s) with no current case", eventType), ex); //NON-NLS
                return;
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Unable to get image gallery controller to handle case event (%s)", eventType), ex); //NON-NLS
                return;
            }

            /*
             * Preprocess the files in a data source when it is added (local
             * node only; processing is completed in FILE_DONE event handler)
             * and add and delete content tags from the cache.
             */
            switch (eventType) {
                case CURRENT_CASE:
                    if (evt.getNewValue() != null) {
                        /*
                         * CURRENT_CASE(_CLOSED) event. Shut down the controller
                         * for the case and close the top component, if it is
                         * open.
                         */
                        SwingUtilities.invokeLater(ImageGalleryTopComponent::closeTopComponent);
                        controller.shutDown();
                    }
                    break;
                case DATA_SOURCE_ADDED:
                    /*
                     * For a data source added to the case locally, prepopulate
                     * the drawable database with file data. Extraneous data
                     * will be removed file by file in the FILE_DONE event
                     * handler.
                     */
                    if (controller.isListeningEnabled()) {
                        if (((AutopsyEvent) evt).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                            Content newDataSource = (Content) evt.getNewValue();
                            controller.queueDBTask(new ImageGalleryController.PrePopulateDataSourceFiles(newDataSource.getId(), controller));
                        }
                    }
                    break;
                case CONTENT_TAG_ADDED:
                    if (controller.isListeningEnabled()) {
                        final ContentTagAddedEvent tagAddedEvent = (ContentTagAddedEvent) evt;
                        long objId = tagAddedEvent.getAddedTag().getContent().getId();
                        DrawableDB drawableDB = controller.getDatabase();
                        drawableDB.addTagCache(objId);
                        if (controller.getDatabase().isInDB(objId)) {
                            controller.getTagsManager().fireTagAddedEvent(tagAddedEvent);
                        }
                    }
                    break;
                case CONTENT_TAG_DELETED:
                    if (controller.isListeningEnabled()) {
                        final ContentTagDeletedEvent tagDeletedEvent = (ContentTagDeletedEvent) evt;
                        if (controller.getDatabase().isInDB(tagDeletedEvent.getDeletedTagInfo().getContentID())) {
                            controller.getTagsManager().fireTagDeletedEvent(tagDeletedEvent);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Listener for ingest job events.
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
            /*
             * If running in "headless" mode, there is no need to process any
             * ingest job events during the current session.
             *
             * Note that this check cannot be done earlier on start up because
             * the "headless" property may not have been set yet.
             */
            if (RuntimeProperties.runningWithGUI() == false) {
                Case.removePropertyChangeListener(this);
                return;
            }

            /*
             * Only the completed ingest of a data source added by another node
             * is of interest here. Processing the files of a data source added
             * locally is done over time.by a combination of DATA_SOURCE_ADDED
             * and FILE_DONE event handlers.
             */
            IngestJobEvent eventType = IngestJobEvent.valueOf(evt.getPropertyName());
            if (eventType != IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED
                    || ((AutopsyEvent) evt).getSourceType() != AutopsyEvent.SourceType.REMOTE) {
                return;
            }

            /*
             * Get the image gallery controller for the current case, and check
             * whether event handling is enabled for the image gallery for this
             * case.
             */
            ImageGalleryController controller;
            try {
                controller = getController();
                if (controller.isListeningEnabled() == false) {
                    return;
                }
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, String.format("Attempted to ingest job event (%s) with no current case", eventType), ex); //NON-NLS
                return;
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Unable to get image gallery controller to handle ingest job event (%s)", eventType), ex); //NON-NLS
                return;
            }

            /*
             * A remote node added a new data source and just finished ingest on
             * it. Mark the drawables database as stale, and give the user the
             * option to update it if the GUI is open.
             */
            controller.setStale(true);
            if (controller.isListeningEnabled() && ImageGalleryTopComponent.isImageGalleryOpen()) {
                SwingUtilities.invokeLater(() -> {
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
                });
            }
        }
    }
}
