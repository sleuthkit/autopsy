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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagewriter.ImageWriterService;
import org.sleuthkit.autopsy.imagewriter.ImageWriterSettings;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/*
 * A runnable that adds an image data source to the case database.
 */
class AddImageTask implements Runnable {

    private final Logger logger = Logger.getLogger(AddImageTask.class.getName());
    private final String deviceId;
    private final String imagePath;
    private final int sectorSize;
    private final String timeZone;
    private final ImageWriterSettings imageWriterSettings;
    private final boolean ignoreFatOrphanFiles;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
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
     * @param deviceId             An ASCII-printable identifier for the device
     *                             associated with the data source that is
     *                             intended to be unique across multiple cases
     *                             (e.g., a UUID).
     * @param imagePath            Path to the image file.
     * @param sectorSize           The sector size (use '0' for autodetect).
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     * @param imageWriterPath      Path that a copy of the image should be
     *                             written to. Use empty string to disable image
     *                             writing
     * @param progressMonitor      Progress monitor to report progress during
     *                             processing.
     * @param callback             Callback to call when processing is done.
     */
    AddImageTask(String deviceId, String imagePath, int sectorSize, String timeZone, boolean ignoreFatOrphanFiles, ImageWriterSettings imageWriterSettings,
            DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.imagePath = imagePath;
        this.sectorSize = sectorSize;
        this.timeZone = timeZone;
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        this.imageWriterSettings = imageWriterSettings;
        this.callback = callback;
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
            currentCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            return;
        }
        progressMonitor.setIndeterminate(true);
        progressMonitor.setProgress(0);
        String imageWriterPath = "";
        if (imageWriterSettings != null) {
            imageWriterPath = imageWriterSettings.getPath();
        }
        List<String> errorMessages = new ArrayList<>();
        List<Content> newDataSources = new ArrayList<>();
        try {
            currentCase.getSleuthkitCase().acquireSingleUserCaseWriteLock();
            synchronized (tskAddImageProcessLock) {
                if (!tskAddImageProcessStopped) {  //if we have already cancelled don't bother making an addImageProcess
                    tskAddImageProcess = currentCase.getSleuthkitCase().makeAddImageProcess(timeZone, true,
                            ignoreFatOrphanFiles, imageWriterPath);
                } else {
                    return; //we have already cancelled so we do not want to add the image, returning will execute the finally block 
                }
            }
            Thread progressUpdateThread = new Thread(new ProgressUpdater(progressMonitor, tskAddImageProcess));
            progressUpdateThread.start();
            runAddImageProcess(errorMessages);
            progressUpdateThread.interrupt();
            commitOrRevertAddImageProcess(currentCase, errorMessages, newDataSources);
            progressMonitor.setProgress(100);
        } finally {
            currentCase.getSleuthkitCase().releaseSingleUserCaseWriteLock();
            DataSourceProcessorCallback.DataSourceProcessorResult result;
            if (criticalErrorOccurred) {
                result = DataSourceProcessorResult.CRITICAL_ERRORS;
            } else if (!errorMessages.isEmpty()) {
                result = DataSourceProcessorResult.NONCRITICAL_ERRORS;
            } else {
                result = DataSourceProcessorResult.NO_ERRORS;
            }
            callback.done(result, errorMessages, newDataSources);
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
                    logger.log(Level.SEVERE, String.format("Error cancelling adding image %s to the case database", imagePath), ex); //NON-NLS
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
            tskAddImageProcess.run(deviceId, new String[]{imagePath}, sectorSize);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Critical error occurred adding image %s", imagePath), ex); //NON-NLS
            criticalErrorOccurred = true;
            errorMessages.add(ex.getMessage());
        } catch (TskDataException ex) {
            logger.log(Level.WARNING, String.format("Non-critical error occurred adding image %s", imagePath), ex); //NON-NLS
            errorMessages.add(ex.getMessage());
        }
    }

    /**
     * Commits or reverts the results of the TSK add image process. If the
     * process was stopped before it completed or there was a critical error the
     * results are reverted, otherwise they are committed.
     *
     * @param currentCase    The current case.
     * @param errorMessages  Error messages, if any, are added to this list for
     *                       eventual return via the callback.
     * @param newDataSources If the new image is successfully committed, it is
     *                       added to this list for eventual return via the
     *                       callback.
     *
     * @return
     */
    private void commitOrRevertAddImageProcess(Case currentCase, List<String> errorMessages, List<Content> newDataSources) {
        synchronized (tskAddImageProcessLock) {
            if (tskAddImageProcessStopped || criticalErrorOccurred) {
                try {
                    tskAddImageProcess.revert();
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error reverting adding image %s to the case database", imagePath), ex); //NON-NLS
                    errorMessages.add(ex.getMessage());
                    criticalErrorOccurred = true;
                }
            } else {
                try {
                    long imageId = tskAddImageProcess.commit();
                    if (imageId != 0) {
                        Image newImage = currentCase.getSleuthkitCase().getImageById(imageId);
                        String verificationError = newImage.verifyImageSize();
                        if (!verificationError.isEmpty()) {
                            errorMessages.add(verificationError);
                        }
                        if (imageWriterSettings != null) {
                            ImageWriterService.createImageWriter(imageId, imageWriterSettings);
                        }
                        newDataSources.add(newImage);
                    } else {
                        String errorMessage = String.format("Error commiting adding image %s to the case database, no object id returned", imagePath); //NON-NLS
                        logger.log(Level.SEVERE, errorMessage);
                        errorMessages.add(errorMessage);
                        criticalErrorOccurred = true;
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error committing adding image %s to the case database", imagePath), ex); //NON-NLS
                    errorMessages.add(ex.getMessage());
                    criticalErrorOccurred = true;
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

}
