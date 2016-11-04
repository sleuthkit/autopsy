package org.sleuthkit.autopsy.datasourceprocessors;

/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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


import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskFileRange;

/*
 * A runnable that adds a raw data source to a case database. 
 */
final class AddRawImageTask implements Runnable {

    private static final Logger logger = Logger.getLogger(AddRawImageTask.class.getName());
    private final String deviceId;
    private final String imageFilePath;
    private final String timeZone;
    private final long chunkSize;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
    private boolean criticalErrorOccurred;
    private boolean tskAddImageProcessStopped;
    private final Object tskAddImageProcessLock;
    private SleuthkitJNI.CaseDbHandle.AddImageProcess tskAddImageProcess;
    private static final long TWO_GB = 2000000000L;
   
    /**
     * Constructs a runnable that adds a raw data source to a case database.
     *
     * @param deviceId                 An ASCII-printable identifier for the
     *                                 device associated with the data source
     *                                 that is intended to be unique across
     *                                 multiple cases (e.g., a UUID).
     * @param imageFilePath            Path to a Raw data source file.
     * @param timeZone                 The time zone to use when processing dates
     *                                 and times for the image, obtained from
     *                                 java.util.TimeZone.getID.
     * @param breakupChunks            2GB or not breakup.
     * @param progressMonitor          Progress monitor for reporting
     *                                 progressMonitor during processing.
     * @param callback                 Callback to call when processing is done.
     */
    AddRawImageTask(String deviceId, String imageFilePath, String timeZone, long chunkSize, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.imageFilePath = imageFilePath;
        this.timeZone = timeZone;
        this.chunkSize = chunkSize;
        this.callback = callback;
        this.progressMonitor = progressMonitor;
        tskAddImageProcessLock = new Object();
    }

    /**
     * Adds a raw data source to a case database.
     */
    @Override
    public void run() {
        /*
         * Process the input image file.
         */
        progressMonitor.setIndeterminate(true);
        progressMonitor.setProgress(0);
        List<Content> newDataSources = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        addImageToCase(newDataSources, errorMessages);

        progressMonitor.setProgress(100);

        /**
         * Return the results via the callback passed to the constructor.
         */
        DataSourceProcessorCallback.DataSourceProcessorResult result;
        if (criticalErrorOccurred) {
            result = DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
        } else if (!errorMessages.isEmpty()) {
            result = DataSourceProcessorCallback.DataSourceProcessorResult.NONCRITICAL_ERRORS;
        } else {
            result = DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS;
        }
        callback.done(result, errorMessages, newDataSources);
        criticalErrorOccurred = false;
    }

    /**
     * Attempts to add the input image to the case.
     *
     * @param newDataSources If the image is added, a data source is added to
     *                       this list for eventual return to the caller via the
     *                       callback.
     * @param errorMessages  If there are any error messages, the error messages
     *                       are added to this list for eventual return to the
     *                       caller via the callback.
     */
    private void addImageToCase(List<Content> dataSources, List<String> errorMessages) {
        progressMonitor.setProgressText(String.format("Adding raw image: %s", imageFilePath));
        List<String> imageFilePaths = new ArrayList<>();
        SleuthkitCase caseDatabase = Case.getCurrentCase().getSleuthkitCase();
        caseDatabase.acquireExclusiveLock();

        File imageFile = Paths.get(imageFilePath).toFile();
        if (!imageFile.exists()) {
            errorMessages.add(String.format("Critical error adding %s for device %s is not existing.", imageFilePath, deviceId));
            criticalErrorOccurred = true;
            return;
        }

        imageFilePaths.add(imageFilePath);
        
        try {
            /*
             * Get Image that will be added to case
             */
            Image dataSource = caseDatabase.addImageInfo(0, imageFilePaths, timeZone); //TODO: change hard coded deviceId.
            dataSources.add(dataSource);
            List<TskFileRange> fileRanges = new ArrayList<>();
            
            /*
             * Verify the size of the new image. Note that it may not be what is
             * expected, but at least part of it was added to the case.
             */
            String verificationError = dataSource.verifyImageSize();
            if (!verificationError.isEmpty()) {
                errorMessages.add(String.format("Non-critical error adding %s for device %s: %s", imageFilePaths, deviceId, verificationError));
            }

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
            errorMessages.add(String.format("Critical error adding %s for device %s: %s", imageFilePaths, deviceId, ex.getLocalizedMessage()));
            criticalErrorOccurred = true;
        } finally {
            caseDatabase.releaseExclusiveLock();
        }

    }    
    /**
     * Attempts to cancel the processing of the input image file. May result in
     * partial processing of the input.
     */
    void cancelTask() {
        logger.log(Level.WARNING, "AddRAWImageTask cancelled, processing may be incomplete");
        synchronized (tskAddImageProcessLock) {
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
                    tskAddImageProcessStopped = true;
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error cancelling adding image %s to the case database", imageFilePath), ex); //NON-NLS
                }
            }
        }
    }

}
