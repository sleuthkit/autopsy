/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.dsp;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DefaultAddDataSourceCallbacks;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskFileRange;

/**
 *
 * A runnable that adds multiple images to the case database
 *
 */
@Messages({
    "AddMultipleImagesTask.fsTypeUnknownErr=Cannot determine file system type"
})
class AddMultipleImagesTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(AddMultipleImagesTask.class.getName());
    public static final String TSK_FS_TYPE_UNKNOWN_ERR_MSG = Bundle.AddMultipleImagesTask_fsTypeUnknownErr();
    private static final long TWO_GB = 2000000000L;
    private final String deviceId;
    private final List<String> imageFilePaths;
    private final String timeZone;
    private final Host host;
    private final long chunkSize = TWO_GB;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final Case currentCase;
    private boolean criticalErrorOccurred;
    private SleuthkitJNI.CaseDbHandle.AddImageProcess addImageProcess = null;
    private List<String> errorMessages = new ArrayList<>();
    private DataSourceProcessorResult result;
    private List<Content> newDataSources = new ArrayList<>();
    private Image currentImage = null;

    /*
     * The cancellation requested flag and SleuthKit add image process are
     * guarded by a lock to synchronize cancelling the process (setting the flag
     * and calling its stop method) and calling either its commit or revert
     * method. 
     */
    private final Object tskAddImageProcessLock;
    @GuardedBy("tskAddImageProcessLock")
    private boolean tskAddImageProcessStopped;

    /**
     * Constructs a runnable that adds multiple image files to a case database.
     * If Sleuth Kit fails to find a filesystem in any of input image files, the
     * file is added to the case as a local/logical file instead.
     *
     * @param deviceId        An ASCII-printable identifier for the device
     *                        associated with the data source that is intended
     *                        to be unique across multiple cases (e.g., a UUID).
     * @param imageFilePaths  The paths of the multiple output files.
     * @param timeZone        The time zone to use when processing dates and
     *                        times for the image, obtained from
     *                        java.util.TimeZone.getID.
     * @param host            Host for this data source (may be null).
     * @param progressMonitor Progress monitor for reporting progress during
     *                        processing.
     *
     * @throws NoCurrentCaseException The exception if there is no open case.
     */
    @Messages({
        "# {0} - file", "AddMultipleImagesTask.addingFileAsLogicalFile=Adding: {0} as an unallocated space file.",
        "# {0} - deviceId", "# {1} - exceptionMessage",
        "AddMultipleImagesTask.errorAddingImgWithoutFileSystem=Error adding images without file systems for device {0}: {1}",})
    AddMultipleImagesTask(String deviceId, List<String> imageFilePaths, String timeZone, Host host,
            DataSourceProcessorProgressMonitor progressMonitor) throws NoCurrentCaseException {
        this.deviceId = deviceId;
        this.imageFilePaths = imageFilePaths;
        this.timeZone = timeZone;
        this.host = host;
        this.progressMonitor = progressMonitor;
        currentCase = Case.getCurrentCaseThrows();
        this.criticalErrorOccurred = false;
        tskAddImageProcessLock = new Object();
    }

    @Messages({
        "AddMultipleImagesTask.cancelled=Cancellation: Add image process reverted",
        "# {0} - image path",
        "AddMultipleImagesTask.imageError=Error adding image {0} to the database"
    })
    @Override
    public void run() {
        errorMessages = new ArrayList<>();
        newDataSources = new ArrayList<>();
        List<Content> emptyDataSources = new ArrayList<>();
        
        /*
         * Try to add the input image files as images.
         */
        List<String> corruptedImageFilePaths = new ArrayList<>();
        progressMonitor.setIndeterminate(true);
        for (String imageFilePath : imageFilePaths) {
            try {
                currentImage = SleuthkitJNI.addImageToDatabase(currentCase.getSleuthkitCase(), new String[]{imageFilePath}, 
                    0, timeZone, "", "", "", deviceId, host);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error adding image " + imageFilePath + " to database", ex);
                errorMessages.add(Bundle.AddMultipleImagesTask_imageError(imageFilePath));
                result = DataSourceProcessorResult.CRITICAL_ERRORS;
            }
            
            synchronized (tskAddImageProcessLock) {

                if (!tskAddImageProcessStopped) {
                    addImageProcess = currentCase.getSleuthkitCase().makeAddImageProcess(timeZone, false, false, "");
                } else {
                    return;
                }
            }
            run(imageFilePath, currentImage, corruptedImageFilePaths, errorMessages);
            finishAddImageProcess(imageFilePath, errorMessages, newDataSources);
            synchronized (tskAddImageProcessLock) {
                if (tskAddImageProcessStopped) {
                    errorMessages.add(Bundle.AddMultipleImagesTask_cancelled());
                    result = DataSourceProcessorResult.CRITICAL_ERRORS;
                    newDataSources = emptyDataSources;
                    return;
                }
            }
        }
    
        /*
         * Try to add any input image files that did not have file systems as a
         * single an unallocated space file with the device id as the root virtual
         * directory name.
         */
        if (!tskAddImageProcessStopped && !corruptedImageFilePaths.isEmpty()) {
            SleuthkitCase caseDatabase;
            caseDatabase = currentCase.getSleuthkitCase();
            try {
                progressMonitor.setProgressText(Bundle.AddMultipleImagesTask_addingFileAsLogicalFile(corruptedImageFilePaths.toString()));

                Image dataSource = caseDatabase.addImageInfo(0, corruptedImageFilePaths, timeZone);
                newDataSources.add(dataSource);
                List<TskFileRange> fileRanges = new ArrayList<>();

                long imageSize = dataSource.getSize();
                int sequence = 0;
                //start byte and end byte
                long start = 0;
                if (chunkSize > 0 && imageSize >= TWO_GB) {
                    for (double size = TWO_GB; size < dataSource.getSize(); size += TWO_GB) {
                        fileRanges.add(new TskFileRange(start, TWO_GB, sequence));
                        start += TWO_GB;
                        sequence++;
                    }
                } 
                double leftoverSize = imageSize - sequence * TWO_GB;
                fileRanges.add(new TskFileRange(start, (long)leftoverSize, sequence));

                caseDatabase.addLayoutFiles(dataSource, fileRanges);
            } catch (TskCoreException ex) {
                errorMessages.add(Bundle.AddMultipleImagesTask_errorAddingImgWithoutFileSystem(deviceId, ex.getLocalizedMessage()));
                criticalErrorOccurred = true;
            }
        }

        /*
         * This appears to be the best that can be done to indicate completion
         * with the DataSourceProcessorProgressMonitor in its current form.
         */
        progressMonitor.setProgress(0);
        progressMonitor.setProgress(100);

        if (criticalErrorOccurred) {
            result = DataSourceProcessorResult.CRITICAL_ERRORS;
        } else if (!errorMessages.isEmpty()) {
            result = DataSourceProcessorResult.NONCRITICAL_ERRORS;
        } else {
            result = DataSourceProcessorResult.NO_ERRORS;
        }
    }

    /**
     * Attempts to cancel the processing of the input image files. May result in
     * partial processing of the input.
     */
    void cancelTask() {
        LOGGER.log(Level.WARNING, "AddMultipleImagesTask cancelled, processing may be incomplete"); // NON-NLS
        synchronized (tskAddImageProcessLock) {
            tskAddImageProcessStopped = true;
            if (addImageProcess != null) {
                try {
                    /*
                     * All this does is set a flag that will make the TSK add
                     * image process exit when the flag is checked between
                     * processing steps. The state of the flag is not
                     * accessible, so record it here so that it is known that
                     * the revert method of the process object needs to be
                     * called.
                     */
                    addImageProcess.stop();
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Cancellation: addImagePRocess.stop failed", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Attempts to add an input image to the case.
     *
     * @param imageFilePath            Path to the image.
     * @param image                    The image.
     * @param corruptedImageFilePaths  If the image cannot be added because
     *                                 Sleuth Kit cannot detect a filesystem,
     *                                 the image file path is added to this list
     *                                 for later addition as an unallocated space file.
     * @param errorMessages            If there are any error messages, the
     *                                 error messages are added to this list for
     *                                 eventual return to the caller via the getter
     *                                 method.
     */
    @Messages({
        "# {0} - imageFilePath", "AddMultipleImagesTask.adding=Adding: {0}",
        "# {0} - imageFilePath", "# {1} - deviceId", "# {2} - exceptionMessage", "AddMultipleImagesTask.criticalErrorAdding=Critical error adding {0} for device {1}: {2}",
        "# {0} - imageFilePath", "# {1} - deviceId", "# {2} - exceptionMessage", "AddMultipleImagesTask.criticalErrorReverting=Critical error reverting add image process for {0} for device {1}: {2}",
        "# {0} - imageFilePath", "# {1} - deviceId", "# {2} - exceptionMessage", "AddMultipleImagesTask.nonCriticalErrorAdding=Non-critical error adding {0} for device {1}: {2}",})
    private void run(String imageFilePath, Image image, List<String> corruptedImageFilePaths, List<String> errorMessages) {
        /*
         * Try to add the image to the case database as a data source.
         */
        progressMonitor.setProgressText(Bundle.AddMultipleImagesTask_adding(imageFilePath));
        try {
            addImageProcess.run(deviceId, image, 0, new DefaultAddDataSourceCallbacks());
        } catch (TskCoreException ex) {
            if (ex.getMessage().contains(TSK_FS_TYPE_UNKNOWN_ERR_MSG)) {
                /*
                 * If Sleuth Kit failed to add the image because it did not find
                 * a file system, save the image path so it can be added to the
                 * case as an unallocated space file. All other
                 * errors are critical.
                 */
                corruptedImageFilePaths.add(imageFilePath);
            } else {
                errorMessages.add(Bundle.AddMultipleImagesTask_criticalErrorAdding(imageFilePath, deviceId, ex.getLocalizedMessage()));
                criticalErrorOccurred = true;
            }
        } catch (TskDataException ex) {
            errorMessages.add(Bundle.AddMultipleImagesTask_nonCriticalErrorAdding(imageFilePath, deviceId, ex.getLocalizedMessage()));
        }
    }
    
    /**
     * Finishes TSK add image process. 
     * The image will always be in the database regardless of whether the user
     * canceled or a critical error occurred. 
     *
     * @param imageFilePath  The image file path.
     * @param errorMessages  Error messages, if any, are added to this list for
     *                       eventual return via the getter method.
     * @param newDataSources If the new image is successfully committed, it is
     *                       added to this list for eventual return via the
     *                       getter method.
     */
    private void finishAddImageProcess(String imageFilePath, List<String> errorMessages, List<Content> newDataSources) {
        synchronized (tskAddImageProcessLock) {        
            /*
             * Add the new image to the list of new data
             * sources to be returned via the getter method.
             */
            newDataSources.add(currentImage);

            // Do no further processing if the user canceled
            if (tskAddImageProcessStopped) {
                return;
            }

            /*
             * Verify the size of the new image. Note that it may not be what is
             * expected, but at least part of it was added to the case.
             */
            String verificationError = currentImage.verifyImageSize();
            if (!verificationError.isEmpty()) {
                errorMessages.add(Bundle.AddMultipleImagesTask_nonCriticalErrorAdding(imageFilePath, deviceId, verificationError));
            }
        }
    }

    /**
     * Return the error messages from the AddMultipleImagesTask run
     * @return List of error message
     */
    public List<String> getErrorMessages() {
        return errorMessages;
    }

    /**
     * Return the result the AddMultipleImagesTask run
     * @return The result of the run
     */
    public DataSourceProcessorResult getResult() {
        return result;
    }

    /**
     * Return the new data sources the AddMultipleImagesTask run
     * @return The new data sources of the run
     */
    public List<Content> getNewDataSources() {
        return newDataSources;
    }    
}
