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
package org.sleuthkit.autopsy.casemodule;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagewriter.ImageWriterService;
import org.sleuthkit.autopsy.imagewriter.ImageWriterSettings;
import org.sleuthkit.datamodel.AddDataSourceCallbacks;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/*
 * A runnable that adds an image data source to the case database.
 */
class AddImageTask implements Runnable {

    private final Logger logger = Logger.getLogger(AddImageTask.class.getName());
    private final ImageDetails imageDetails;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final AddDataSourceCallbacks addDataSourceCallbacks;
    private final AddImageTaskCallback addImageTaskCallback;
    private boolean criticalErrorOccurred;

    /*
     * The cancellation requested flag and SleuthKit add image process are
     * guarded by a monitor (called a lock here to avoid confusion with the
     * progress monitor) to synchronize cancelling the process (setting the flag
     * and calling its stop method) and calling either its commit or revert
     * method. The built-in monitor of the add image process can't be used for
     * this because it is already used to synchronize its run (init part),
     * commit, revert, and currentDirectory methods.
     *
     * TODO (AUT-2021): Merge SleuthkitJNI.AddImageProcess and AddImageTask
     */
    private final Object tskAddImageProcessLock;
    @GuardedBy("tskAddImageProcessLock")
    private boolean tskAddImageProcessStopped;
    private SleuthkitJNI.CaseDbHandle.AddImageProcess tskAddImageProcess;

    /**
     * Constructs a runnable task that adds an image to the case database.
     * 
     * @param imageDetails         Holds all data about the image.
     * @param progressMonitor      Progress monitor to report progress during
     *                             processing.
     * @param addDataSourceCallbacks  Callback for sending data to the ingest pipeline if an ingest stream is being used.
     * @param addImageTaskCallback    Callback for dealing with add image task completion.
     */
    AddImageTask(ImageDetails imageDetails, DataSourceProcessorProgressMonitor progressMonitor, AddDataSourceCallbacks addDataSourceCallbacks,  
            AddImageTaskCallback addImageTaskCallback) {
        this.imageDetails = imageDetails;
        this.addDataSourceCallbacks = addDataSourceCallbacks;
        this.addImageTaskCallback = addImageTaskCallback;
        this.progressMonitor = progressMonitor;
        tskAddImageProcessLock = new Object();
    }

    /**
     * Adds the image to the case database.
     */
    @Override
    public void run() {
        Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, String.format("Failed to start AddImageTask for %s, no current case", imageDetails.getImagePath()), ex);
            return;
        }
        progressMonitor.setIndeterminate(true);
        progressMonitor.setProgress(0);
        String imageWriterPath = "";
        if (imageDetails.imageWriterSettings != null) {
            imageWriterPath = imageDetails.imageWriterSettings.getPath();
        }
        List<String> errorMessages = new ArrayList<>();
        List<Content> newDataSources = new ArrayList<>();
        try {
            synchronized (tskAddImageProcessLock) {
                if (!tskAddImageProcessStopped) {
                    tskAddImageProcess = currentCase.getSleuthkitCase().makeAddImageProcess(imageDetails.timeZone, true, imageDetails.ignoreFatOrphanFiles, imageWriterPath);
                } else {
                    return;
                }
            }
            Thread progressUpdateThread = new Thread(new ProgressUpdater(progressMonitor, tskAddImageProcess));
            progressUpdateThread.start();
            runAddImageProcess(errorMessages);
            progressUpdateThread.interrupt();
            finishAddImageProcess(errorMessages, newDataSources);
            progressMonitor.setProgress(100);
        } finally {
            DataSourceProcessorCallback.DataSourceProcessorResult result;
            if (criticalErrorOccurred) {
                result = DataSourceProcessorResult.CRITICAL_ERRORS;
            } else if (!errorMessages.isEmpty()) {
                result = DataSourceProcessorResult.NONCRITICAL_ERRORS;
            } else {
                result = DataSourceProcessorResult.NO_ERRORS;
            }
            addImageTaskCallback.onCompleted(result, errorMessages, newDataSources);
        }
    }

    /*
     * Attempts to cancel adding the image to the case database.
     */
    public void cancelTask() {
        synchronized (tskAddImageProcessLock) {
            tskAddImageProcessStopped = true;
            if (null != tskAddImageProcess) {
                try {
                    /*
                     * All this does is set a flag that will make the TSK add
                     * image process exit when the flag is checked between
                     * processing steps. The state of the flag is not
                     * accessible, so record it here so that it is known that
                     * the revert method of the process object needs to be
                     * called.
                     */
                    tskAddImageProcess.stop();

                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error cancelling adding image %s to the case database", imageDetails.getImagePath()), ex); //NON-NLS
                }
            }
        }
    }

    /**
     * Runs the TSK add image process.
     *
     * @param errorMessages Error messages, if any, are added to this list for
     *                      eventual return via the callback.
     */
    private void runAddImageProcess(List<String> errorMessages) {
        try {
            tskAddImageProcess.run(imageDetails.deviceId, imageDetails.image, imageDetails.sectorSize, this.addDataSourceCallbacks);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Critical error occurred adding image %s", imageDetails.getImagePath()), ex); //NON-NLS
            criticalErrorOccurred = true;
            errorMessages.add(ex.getMessage());
        } catch (TskDataException ex) {
            logger.log(Level.WARNING, String.format("Non-critical error occurred adding image %s", imageDetails.getImagePath()), ex); //NON-NLS
            errorMessages.add(ex.getMessage());
        }
    }

    /**
     * Handle the results of the TSK add image process. 
     * The image will be in the database even if a critical error occurred or
     * the user canceled.
     *
     * @param errorMessages  Error messages, if any, are added to this list for
     *                       eventual return via the callback.
     * @param newDataSources If the new image is successfully committed, it is
     *                       added to this list for eventual return via the
     *                       callback.
     *
     * @return
     */
    private void finishAddImageProcess(List<String> errorMessages, List<Content> newDataSources) {
        synchronized (tskAddImageProcessLock) {
            Image newImage = imageDetails.image;
            String verificationError = newImage.verifyImageSize();
            if (!verificationError.isEmpty()) {
                errorMessages.add(verificationError);
            }
            if (imageDetails.imageWriterSettings != null) {
                ImageWriterService.createImageWriter(newImage.getId(), imageDetails.imageWriterSettings);
            }
            newDataSources.add(newImage);

            // If the add image process was cancelled don't do any further processing here
            if (tskAddImageProcessStopped) {
                return;
            }

            if (!StringUtils.isBlank(imageDetails.md5)) {
                try {
                    newImage.setMD5(imageDetails.md5);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to add MD5 hash for image data source %s (objId=%d)", newImage.getName(), newImage.getId()), ex);
                    errorMessages.add(ex.getMessage());
                    criticalErrorOccurred = true;
                } catch (TskDataException ignored) {
                    /*
                     * The only reasonable way for this to happen at
                     * present is through C/C++ processing of an EWF
                     * image, which is not an error.
                     */
                }
            }
            if (!StringUtils.isBlank(imageDetails.sha1)) {
                try {
                    newImage.setSha1(imageDetails.sha1);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to add SHA1 hash for image data source %s (objId=%d)", newImage.getName(), newImage.getId()), ex);
                    errorMessages.add(ex.getMessage());
                    criticalErrorOccurred = true;
                } catch (TskDataException ignored) {
                    /*
                     * The only reasonable way for this to happen at
                     * present is through C/C++ processing of an EWF
                     * image, which is not an error.
                     */
                }
            }
            if (!StringUtils.isBlank(imageDetails.sha256)) {
                try {
                    newImage.setSha256(imageDetails.sha256);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to add SHA256 for image data source %s (objId=%d)", newImage.getName(), newImage.getId()), ex);
                    errorMessages.add(ex.getMessage());
                    criticalErrorOccurred = true;
                } catch (TskDataException ignored) {
                    /*
                     * The only reasonable way for this to happen at
                     * present is through C/C++ processing of an EWF
                     * image, which is not an error.
                     */
                }
            }
        }
    }

    /**
     * A Runnable that updates the progress monitor with the name of the
     * directory currently being processed by the SleuthKit add image process.
     */
    private class ProgressUpdater implements Runnable {

        private final DataSourceProcessorProgressMonitor progressMonitor;
        private final SleuthkitJNI.CaseDbHandle.AddImageProcess tskAddImageProcess;

        /**
         * Constructs a Runnable that updates the progress monitor with the name
         * of the directory currently being processed by the SleuthKit.
         *
         * @param progressMonitor
         * @param tskAddImageProcess
         */
        ProgressUpdater(DataSourceProcessorProgressMonitor progressMonitor, SleuthkitJNI.CaseDbHandle.AddImageProcess tskAddImageProcess) {
            this.progressMonitor = progressMonitor;
            this.tskAddImageProcess = tskAddImageProcess;
        }

        /**
         * Updates the progress monitor with the name of the directory currently
         * being processed by the SleuthKit add image process.
         */
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String currDir = tskAddImageProcess.currentDirectory();
                    if (currDir != null) {
                        if (!currDir.isEmpty()) {
                            progressMonitor.setProgressText(
                                    NbBundle.getMessage(this.getClass(), "AddImageTask.run.progress.adding",
                                            currDir));
                        }
                    }
                    /*
                     * The sleep here throttles the UI updates and provides a
                     * non-standard mechanism for completing this task by
                     * interrupting the thread in which it is running.
                     *
                     * TODO (AUT-1870): Replace this with giving the task to a
                     * java.util.concurrent.ScheduledThreadPoolExecutor that is
                     * shut down when the main task completes.
                     */
                    Thread.sleep(500);
                }
            } catch (InterruptedException expected) {
            }
        }
    }

    /**
     * Utility class to hold image data.
     */
    static class ImageDetails {
        String deviceId;
        Image image;
        int sectorSize;
        String timeZone;
        boolean ignoreFatOrphanFiles;
        String md5;
        String sha1; 
        String sha256;
        ImageWriterSettings imageWriterSettings;
        
        ImageDetails(String deviceId, Image image, int sectorSize, String timeZone, boolean ignoreFatOrphanFiles, String md5, String sha1, String sha256, ImageWriterSettings imageWriterSettings) {
            this.deviceId = deviceId;
            this.image = image;
            this.sectorSize = sectorSize;
            this.timeZone = timeZone;
            this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
            this.md5 = md5;
            this.sha1 = sha1; 
            this.sha256 = sha256; 
            this.imageWriterSettings = imageWriterSettings;
        }
	
        String getImagePath() {
            if (image.getPaths().length > 0) {
                return image.getPaths()[0];
            }
            return "Unknown data source path";
        }
    }
}
