package org.sleuthkit.autopsy.datasourceprocessors;

/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2018 Basis Technology Corp.
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
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskFileRange;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

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
    @Messages({"AddRawImageTask.progress.add.text=Adding raw image: ",
               "AddRawImageTask.image.critical.error.adding=Critical error adding ",
               "AddRawImageTask.for.device=for device ",
               "AddRawImageTask.image.notExisting=is not existing.",
               "AddRawImageTask.image.noncritical.error.adding=Non-critical error adding ",
               "AddRawImageTask.noOpenCase.errMsg=No open case available."})
    private void addImageToCase(List<Content> dataSources, List<String> errorMessages) {
        SleuthkitCase caseDatabase;
        try {
            caseDatabase = Case.getOpenCase().getSleuthkitCase();
        } catch (NoCurrentCaseException ex) {
            errorMessages.add(Bundle.AddRawImageTask_noOpenCase_errMsg());
            logger.log(Level.SEVERE, Bundle.AddRawImageTask_noOpenCase_errMsg(), ex);
            criticalErrorOccurred = true;
            return;
        }
        progressMonitor.setProgressText(Bundle.AddRawImageTask_progress_add_text() + imageFilePath);
        List<String> imageFilePaths = new ArrayList<>();
        File imageFile = Paths.get(imageFilePath).toFile();
        if (!imageFile.exists()) {
            String errorMessage = Bundle.AddRawImageTask_image_critical_error_adding() + imageFilePath + Bundle.AddRawImageTask_for_device() 
                    + deviceId + Bundle.AddRawImageTask_image_notExisting();
            errorMessages.add(errorMessage);
            logger.log(Level.SEVERE, errorMessage);
            criticalErrorOccurred = true;
            return;
        }        
        imageFilePaths.add(imageFilePath); 
        try { 
            caseDatabase.acquireSingleUserCaseWriteLock();
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
                errorMessages.add(Bundle.AddRawImageTask_image_noncritical_error_adding() + imageFilePaths + Bundle.AddRawImageTask_for_device() +  deviceId + ":" + verificationError);
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
            String errorMessage = Bundle.AddRawImageTask_image_critical_error_adding() + imageFilePaths + Bundle.AddRawImageTask_for_device() + deviceId + ":" + ex.getLocalizedMessage();
            errorMessages.add(errorMessage);
            logger.log(Level.SEVERE, errorMessage, ex);
            criticalErrorOccurred = true;
        } finally {
            caseDatabase.releaseSingleUserCaseWriteLock();
        }

    }    
}
