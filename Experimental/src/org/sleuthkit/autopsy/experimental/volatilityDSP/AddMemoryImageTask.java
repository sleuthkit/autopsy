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
package org.sleuthkit.autopsy.experimental.volatilityDSP;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.openide.util.NbBundle.Messages;

/*
 * A runnable that adds a raw data source to a case database. 
 */
final class AddMemoryImageTask implements Runnable {

    private static final Logger logger = Logger.getLogger(AddMemoryImageTask.class.getName());
    private final String deviceId;
    private final String imageFilePath;
    private final String timeZone;
    private final List<String> PluginsToRun; 
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
    private boolean criticalErrorOccurred;
    private static final long TWO_GB = 2000000000L;
    private boolean isCancelled = false;
    private VolatilityProcessor volatilityProcessor = null;
   
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
    AddMemoryImageTask(String deviceId, String imageFilePath, List<String> PluginsToRun, String timeZone, long chunkSize, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.imageFilePath = imageFilePath;
        this.PluginsToRun = PluginsToRun;
        this.timeZone = timeZone;
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
        List<String> errorMessages = new ArrayList<>();
        Image dataSource = addImageToCase(errorMessages);

        /* call Volatility to process the image */
        if (dataSource != null) {
            volatilityProcessor = new VolatilityProcessor(imageFilePath, PluginsToRun, dataSource, progressMonitor);
            // @@@ run() needs a way to return if a critical eror occured.
            volatilityProcessor.run();
            List<String> volErrorMsgs = volatilityProcessor.getErrorMessages();
            errorMessages.addAll(volErrorMsgs);
        }

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
        
        List <Content> newDataSources = new ArrayList();
        newDataSources.add(dataSource);
        callback.done(result, errorMessages, newDataSources);
        criticalErrorOccurred = false;
    }

    /**
     * Attempts to add the input image to the case.
     *
     * @param errorMessages  If there are any error messages, the error messages
     *                       are added to this list for eventual return to the
     *                       caller via the callback.
     * @returns Image that was added to DB or null on error
     */
    @Messages({"AddMemoryImageTask.progress.add.text=Adding memory image: ",
               "AddMemoryImageTask.image.critical.error.adding=Critical error adding ",
               "AddMemoryImageTask.for.device=for device ",
               "AddMemoryImageTask.image.notExisting=is not existing.",
               "AddMemoryImageTask.image.noncritical.error.adding=Non-critical error adding "})
    private Image addImageToCase(List<String> errorMessages) {
        progressMonitor.setProgressText(Bundle.AddMemoryImageTask_progress_add_text() + imageFilePath);
        
        SleuthkitCase caseDatabase = Case.getCurrentCase().getSleuthkitCase();
        caseDatabase.acquireExclusiveLock();

        // verify it exists
        File imageFile = Paths.get(imageFilePath).toFile();
        if (!imageFile.exists()) {
            errorMessages.add(Bundle.AddMemoryImageTask_image_critical_error_adding() + imageFilePath + Bundle.AddMemoryImageTask_for_device() 
                    + deviceId + Bundle.AddMemoryImageTask_image_notExisting());
            criticalErrorOccurred = true;
            return null;
        }
        
        try {
            // add it to the DB
            List<String> imageFilePaths = new ArrayList<>();
            imageFilePaths.add(imageFilePath);
            Image dataSource = caseDatabase.addImageInfo(0, imageFilePaths, timeZone); //TODO: change hard coded deviceId.
            return dataSource;
        } catch (TskCoreException ex) {
            errorMessages.add(Bundle.AddMemoryImageTask_image_critical_error_adding() + imageFilePath + Bundle.AddMemoryImageTask_for_device() + deviceId + ":" + ex.getLocalizedMessage());
            criticalErrorOccurred = true;
            return null;
        } finally {
            caseDatabase.releaseExclusiveLock();
        }        
    }

    void cancelTask() {
        if (volatilityProcessor != null) {
            volatilityProcessor.cancel();
        }
        isCancelled = true;
    }
}
