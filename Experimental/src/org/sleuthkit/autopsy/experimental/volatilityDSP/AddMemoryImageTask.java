/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.SleuthkitJNI;

/*
 * A runnable that adds a memory image data source to a case database.
 */
final class AddMemoryImageTask implements Runnable {

    private final static Logger logger = Logger.getLogger(AddMemoryImageTask.class.getName());
    private final String deviceId;
    private final String memoryImagePath;
    private final String timeZone;
    private final Host host;
    private final List<String> pluginsToRun;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
    private volatile VolatilityProcessor volatilityProcessor;
    private volatile boolean isCancelled;
    private final String profile;  // empty for autodetect

    /**
     * Constructs a runnable that adds a memory image to a case database.
     *
     * @param deviceId        An ASCII-printable identifier for the device
     *                        associated with the data source that is intended
     *                        to be unique across multiple cases (e.g., a UUID).
     * @param memoryImagePath Path to the memory image file.
     * @param profile         Volatility profile to run or empty string to autodetect
     * @param pluginsToRun    The Volatility plugins to run.
     * @param timeZone        The time zone to use when processing dates and
     *                        times for the image, obtained from
     *                        java.util.TimeZone.getID.
     * @param host            The host for this data source (may be null).
     * @param progressMonitor Progress monitor for reporting progressMonitor
     *                        during processing.
     * @param callback        Callback to call when processing is done.
     */
    AddMemoryImageTask(String deviceId, String memoryImagePath, String profile, List<String> pluginsToRun, String timeZone, Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.memoryImagePath = memoryImagePath;
        this.profile = profile;
        this.pluginsToRun = pluginsToRun;
        this.timeZone = timeZone;
        this.host = host;
        this.callback = callback;
        this.progressMonitor = progressMonitor;
    }

    /**
     * Adds a memory image data source to a case database.
     */
     @Messages({
        "# {0} - exception message",
        "AddMemoryImageTask_errorMessage_criticalException= Critical error: {0}",
    })
   @Override
    public void run() {
        if (isCancelled) {
            return;
        }
        progressMonitor.setIndeterminate(true);
        progressMonitor.setProgress(0);
        List<Content> dataSources = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        boolean criticalErrorOccurred = false;
        try {
            Image dataSource = addImageToCase();
            dataSources.add(dataSource);
            volatilityProcessor = new VolatilityProcessor(memoryImagePath, dataSource, profile, pluginsToRun, progressMonitor);
            volatilityProcessor.run();
        } catch (NoCurrentCaseException | TskCoreException | VolatilityProcessor.VolatilityProcessorException ex) {
            criticalErrorOccurred = true;
            errorMessages.add(Bundle.AddMemoryImageTask_errorMessage_criticalException(ex.getLocalizedMessage()));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, String.format("Critical error processing memory image data source at %s for device %s", memoryImagePath, deviceId), ex);
        }
        errorMessages.addAll(volatilityProcessor.getErrorMessages());
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
        callback.done(result, errorMessages, dataSources);
    }

    /**
     * Attempts to add the input memory image to the case as a data source.
     *
     * @return The Image object representation of the memory image file data
     *         source.
     *
     * @throws NoCurrentCaseException If there is no current case.
     * @throws TskCoreException       If there is an error adding the data
     *                                source to the case database.
     */
    @Messages({
        "# {0} - image file path",
        "AddMemoryImageTask_progressMessage_addingImageFile= Adding memory image {0}",
        "# {0} - image file path",
        "# {1} - device id",
        "AddMemoryImageTask_exceptionMessage_noImageFile= Memory image file {0} for device {1} does not exist"
    })
    private Image addImageToCase() throws NoCurrentCaseException, TskCoreException {
        progressMonitor.setProgressText(Bundle.AddMemoryImageTask_progressMessage_addingImageFile( memoryImagePath));

        SleuthkitCase caseDatabase = Case.getCurrentCaseThrows().getSleuthkitCase();

        /*
         * Verify the memory image file exists.
         */
        File imageFile = Paths.get(memoryImagePath).toFile();
        if (!imageFile.exists()) {
            throw new TskCoreException(Bundle.AddMemoryImageTask_exceptionMessage_noImageFile(memoryImagePath, deviceId));
        }

        /*
         * Add the data source.
         *
         * NOTE: The object id for device passed to
         * SleuthkitCase.addImageInfo is hard-coded to zero for now. This
         * will need to be changed when a Device abstraction is added to the
         * SleuthKit data model.
         */
        Image dataSource = SleuthkitJNI.addImageToDatabase(caseDatabase, new String[]{memoryImagePath}, 0, timeZone, null, null, null, deviceId);
        return dataSource;
    }

    /**
     * Requests cancellation of this task by setting a cancelled flag.
     */
    void cancelTask() {
        isCancelled = true;
        if (volatilityProcessor != null) {
            volatilityProcessor.cancel();
        }
    }
    
}
