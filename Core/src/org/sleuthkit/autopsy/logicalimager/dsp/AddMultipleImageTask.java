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
package org.sleuthkit.autopsy.logicalimager.dsp;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * 
 * A runnable that adds multiple images to the case database
 * 
 */
@Messages({
    "AddMultipleImageTask.fsTypeUnknownErr=Cannot determine file system type"
})
class AddMultipleImageTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(AddMultipleImageTask.class.getName());
    public static final String TSK_FS_TYPE_UNKNOWN_ERR_MSG = Bundle.AddMultipleImageTask_fsTypeUnknownErr();
    private final String deviceId;
    private final List<String> imageFilePaths;
    private final String timeZone;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
    private final Case currentCase;
    private boolean criticalErrorOccurred;
    private volatile boolean cancelled;
    
    /**
     * Constructs a runnable that adds multiple image files
     * to a case database. If Sleuth Kit fails to find a filesystem
     * in any of input image files, the file is added to the case as a
     * local/logical file instead.
     *
     * @param deviceId        An ASCII-printable identifier for the device
     *                        associated with the data source that is intended
     *                        to be unique across multiple cases (e.g., a UUID).
     * @param imageFilePaths  The paths of the multiple output files.
     * @param timeZone        The time zone to use when processing dates and
     *                        times for the image, obtained from
     *                        java.util.TimeZone.getID.
     * @param progressMonitor Progress monitor for reporting progress during
     *                        processing.
     * @param callback        Callback to call when processing is done.
     * @throws NoCurrentCaseException   The exception if there is no open case.
     */
    @Messages({
        "# {0} - file", "AddMultipleImageTask.addingFileAsLogicalFile=Adding: {0} as logical file",
        "# {0} - deviceId", "# {1} - exceptionMessage", 
        "AddMultipleImageTask.errorAddingImgWithoutFileSystem=Error adding images without file systems for device %s: %s",
    })    
    AddMultipleImageTask(String deviceId, List<String> imageFilePaths, String timeZone, 
            DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) throws NoCurrentCaseException {
        this.deviceId = deviceId;
        this.imageFilePaths = imageFilePaths;
        this.timeZone = timeZone;
        this.callback = callback;
        this.progressMonitor = progressMonitor;
        currentCase = Case.getCurrentCaseThrows();
    }

    @Override
    public void run() {
        /*
         * Try to add the input image files as images.
         */
        List<Content> newDataSources = new ArrayList<>();
        List<String> localFileDataSourcePaths = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        currentCase.getSleuthkitCase().acquireSingleUserCaseWriteLock();
        try {
            progressMonitor.setIndeterminate(true);
            for (String imageFilePath : imageFilePaths) {
                if (!cancelled) {
                    addImageToCase(imageFilePath, newDataSources, localFileDataSourcePaths, errorMessages);
                }
            }
        } finally {
            currentCase.getSleuthkitCase().releaseSingleUserCaseWriteLock();
        }

        /*
         * Try to add any input image files that did not have file systems as a
         * single local/logical files set with the device id as the root virtual
         * directory name.
         */
        if (!cancelled && !localFileDataSourcePaths.isEmpty()) {
            FileManager fileManager = currentCase.getServices().getFileManager();
            FileManager.FileAddProgressUpdater progressUpdater = (final AbstractFile newFile) -> {
                progressMonitor.setProgressText(Bundle.AddMultipleImageTask_addingFileAsLogicalFile(Paths.get(newFile.getParentPath(), newFile.getName())));
            };
            try {
                LocalFilesDataSource localFilesDataSource = fileManager.addLocalFilesDataSource(deviceId, "", timeZone, localFileDataSourcePaths, progressUpdater);
                newDataSources.add(localFilesDataSource);
            } catch (TskCoreException | TskDataException ex) {
                errorMessages.add(Bundle.AddMultipleImageTask_errorAddingImgWithoutFileSystem(deviceId, ex.getLocalizedMessage()));
                criticalErrorOccurred = true;
            }
        }

        /*
         * This appears to be the best that can be done to indicate completion
         * with the DataSourceProcessorProgressMonitor in its current form.
         */
        progressMonitor.setProgress(0);
        progressMonitor.setProgress(100);

        /*
         * Pass the results back via the callback.
         */
        DataSourceProcessorResult result;
        if (criticalErrorOccurred) {
            result = DataSourceProcessorResult.CRITICAL_ERRORS;
        } else if (!errorMessages.isEmpty()) {
            result = DataSourceProcessorResult.NONCRITICAL_ERRORS;
        } else {
            result = DataSourceProcessorResult.NO_ERRORS;
        }
        callback.done(result, errorMessages, newDataSources);
        criticalErrorOccurred = false;
    }

    /**
     * Attempts to cancel the processing of the input image files. May result in
     * partial processing of the input.
     */
    public void cancelTask() {
        LOGGER.log(Level.WARNING, "AddMultipleImageTask cancelled, processing may be incomplete"); // NON-NLS
        cancelled = true;
    }

    /**
     * Attempts to add an input image to the case.
     *
     * @param imageFilePath            The image file path.
     * @param newDataSources           If the image is added, a data source is
     *                                 added to this list for eventual return to
     *                                 the caller via the callback.
     * @param localFileDataSourcePaths If the image cannot be added because
     *                                 Sleuth Kit cannot detect a filesystem, the
     *                                 image file path is added to this list for
     *                                 later addition as a part of a
     *                                 local/logical files data source.
     * @param errorMessages            If there are any error messages, the
     *                                 error messages are added to this list for
     *                                 eventual return to the caller via the
     *                                 callback.
     */
    @Messages({
        "# {0} - imageFilePath", "AddMultipleImageTask.adding=Adding: {0}",
        "# {0} - imageFilePath", "# {1} - deviceId", "# {2} - exceptionMessage", "AddMultipleImageTask.criticalErrorAdding=Critical error adding {0} for device {1}: {2}",
        "# {0} - imageFilePath", "# {1} - deviceId", "# {2} - exceptionMessage", "AddMultipleImageTask.criticalErrorReverting=Critical error reverting add image process for {0} for device {1}: {2}",
        "# {0} - imageFilePath", "# {1} - deviceId", "# {2} - exceptionMessage", "AddMultipleImageTask.nonCriticalErrorAdding=Non-critical error adding {0} for device {1}: {2}",
    })
    private void addImageToCase(String imageFilePath, List<Content> newDataSources, List<String> localFileDataSourcePaths, List<String> errorMessages) {
        /*
         * Try to add the image to the case database as a data source.
         */
        progressMonitor.setProgressText(Bundle.AddMultipleImageTask_adding(imageFilePath));
        SleuthkitCase caseDatabase = currentCase.getSleuthkitCase();
        SleuthkitJNI.CaseDbHandle.AddImageProcess addImageProcess = caseDatabase.makeAddImageProcess(timeZone, false, false, "");
        try {
            addImageProcess.run(deviceId, new String[]{imageFilePath});
        } catch (TskCoreException ex) {
            if (ex.getMessage().contains(TSK_FS_TYPE_UNKNOWN_ERR_MSG)) {
                /*
                 * If Sleuth Kit failed to add the image because it did not find
                 * a file system, save the image path so it can be added to the
                 * case as part of a local/logical files data source. All other
                 * errors are critical.
                 */
                localFileDataSourcePaths.add(imageFilePath);
            } else {
                errorMessages.add(Bundle.AddMultipleImageTask_criticalErrorAdding(imageFilePath, deviceId, ex.getLocalizedMessage()));
                criticalErrorOccurred = true;
            }
            /*
             * Either way, the add image process needs to be reverted.
             */
            try {
                addImageProcess.revert();
            } catch (TskCoreException e) {
                errorMessages.add(Bundle.AddMultipleImageTask_criticalErrorReverting(imageFilePath, deviceId, e.getLocalizedMessage()));
                criticalErrorOccurred = true;
            }
            return;
        } catch (TskDataException ex) {
            errorMessages.add(Bundle.AddMultipleImageTask_nonCriticalErrorAdding(imageFilePath, deviceId, ex.getLocalizedMessage()));
        }

        /*
         * Try to commit the results of the add image process, retrieve the new
         * image from the case database, and add it to the list of new data
         * sources to be returned via the callback.
         */
        try {
            long imageId = addImageProcess.commit();
            Image dataSource = caseDatabase.getImageById(imageId);
            newDataSources.add(dataSource);

            /*
             * Verify the size of the new image. Note that it may not be what is
             * expected, but at least part of it was added to the case.
             */
            String verificationError = dataSource.verifyImageSize();
            if (!verificationError.isEmpty()) {
                errorMessages.add(Bundle.AddMultipleImageTask_nonCriticalErrorAdding(imageFilePath, deviceId, verificationError));
            }
        } catch (TskCoreException ex) {
            /*
             * The add image process commit failed or querying the case database
             * for the newly added image failed. Either way, this is a critical
             * error.
             */
            errorMessages.add(Bundle.AddMultipleImageTask_criticalErrorAdding(imageFilePath, deviceId, ex.getLocalizedMessage()));
            criticalErrorOccurred = true;
        }
    }

}