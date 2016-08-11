/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.autoingest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestManagerException;

/**
 * A logger for the processing of an auto ingest job by an auto ingest node. The
 * log messages are written to the case auto ingest log, a user-friendly log of
 * of the automated processing for a case.
 *
 * The auto ingest log for a case is not intended to be a comprehensive.
 * Advanced users doing troubleshooting of an automated ingest cluster should
 * also consult the Autopsy and system logs as needed.
 */
final class AutoIngestJobLogger {

    static final String ROOT_NAMESPACE = "autopsy"; //ELTODO - remove this after AIM is moved into Autopsy. It belongs there.
    
    private static final Logger autopsyLogger = Logger.getLogger(AutoIngestJobLogger.class.getName());
    private static final String LOG_FILE_NAME = "auto_ingest_log.txt";
    private static final int LOCK_TIME_OUT = 15;
    private static final TimeUnit LOCK_TIME_OUT_UNIT = TimeUnit.MINUTES;
    private static final String DATE_FORMAT_STRING = "yyyy/MM/dd HH:mm:ss";
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT_STRING);
    private final Path imageFolderPath;
    private final Path caseFolderPath;
    private final String hostName;

    private enum MessageLevel {
        INFO, WARNING, ERROR
    }

    /**
     * Gets the path to the auto ingest log for a case.
     *
     * @param caseFolderPath The path to the case folder for the case
     *
     * @return The path to the auto ingest log for the case.
     */
    static Path getLogPath(Path caseFolderPath) {
        return Paths.get(caseFolderPath.toString(), LOG_FILE_NAME);
    }

    /**
     * Constructs a logger for the processing of an auto ingest job by an auto
     * ingest node. The log messages are written to the case auto ingest log, a
     * user-friendly log of of the automated processing for a case.
     *
     * @param imageFolderPath The image folder for the auto ingest job.
     * @param caseFolderPath  The case folder for the case.
     */
    AutoIngestJobLogger(Path imageFolderPath, Path caseFolderPath) {
        this.imageFolderPath = imageFolderPath;
        this.caseFolderPath = caseFolderPath;
        hostName = NetworkUtils.getLocalHostName();
    }

    /**
     * Logs the cancellation of an auto ingest job during processing.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logJobCancelled() throws InterruptedException {
        log(MessageLevel.WARNING, "", "Auto ingest job cancelled during processing");
    }

    /**
     * Logs an error opening or creating a case.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logUnableToOpenCase() throws InterruptedException {
        log(MessageLevel.ERROR, "", "Unable to create or open case");
    }

    /**
     * Logs the lack of at least one manifest file in the image folder.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logMissingManifest() throws InterruptedException {
        log(MessageLevel.ERROR, "", "Missing manifest file");
    }

    /**
     * Logs the presence of a manifest file that matches more than one data
     * source.
     *
     * @param manifestFileName The file name of the ambiguous manifest.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logAmbiguousManifest(String manifestFileName) throws InterruptedException {
        log(MessageLevel.ERROR, "", String.format("Manifest file %s matches multiple data sources", manifestFileName));
    }

    /**
     * Logs the presence of a manifest file without a matching data source.
     *
     * @param manifestFileName
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logMissingDataSource(String manifestFileName) throws InterruptedException {
        log(MessageLevel.ERROR, "", String.format("Data source for manifest file %s is either missing or is not a supported type", manifestFileName));
    }

    /**
     * Logs an error identifying the type of a data source.
     *
     * @param dataSource The data source.
     * @param ex         The error.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logDataSourceTypeIdError(String dataSource, Exception ex) throws InterruptedException {
        log(MessageLevel.ERROR, dataSource, String.format("Unable to identify data source type: %s", ex.getLocalizedMessage()));
    }

    /**
     * Logs cancellation of the addition of a data source to the case database.
     *
     * @param dataSource     The data source.
     * @param dataSourceType The data source type.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logDataSourceProcessorCancelled(String dataSource, String dataSourceType) throws InterruptedException {
        log(MessageLevel.WARNING, dataSource, String.format("Cancelled adding data source to case as %s", dataSourceType));
    }

    /**
     * Logs the addition of a data source to the case database.
     *
     * @param dataSource     The data source.
     * @param dataSourceType The data source type.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logDataSourceAdded(String dataSource, String dataSourceType) throws InterruptedException {
        log(MessageLevel.INFO, dataSource, String.format("Added data source to case as %s", dataSourceType));
    }

    /**
     * Logs an error reported by a data source processor when adding a data
     * source to the case database.
     *
     * @param dataSource     The data source.
     * @param dataSourceType The data source type.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logDataSourceProcessorError(String dataSource, String errorMessage) throws InterruptedException {
        log(MessageLevel.ERROR, dataSource, String.format("Critical error adding data source to case: %s", errorMessage));
    }

    /**
     * Logs an error adding a data source to the case database.
     *
     * @param dataSource     The data source.
     * @param dataSourceType The data source type.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logFailedToAddDataSource(String dataSource, String dataSourceType) throws InterruptedException {
        log(MessageLevel.ERROR, dataSource, String.format("Failed to add data source to case as %s", dataSourceType));
    }

    /**
     * Logs failure to analyze a data source because the analysis could not be
     * started due to an ingest manager exception.
     *
     * @param dataSource The data source.
     * @param ex         The ingest manager exception.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logAnalysisStartupError(String dataSource, IngestManagerException ex) throws InterruptedException {
        log(MessageLevel.ERROR, dataSource, String.format("Analysis of data source by ingest modules not started: %s", ex.getLocalizedMessage()));
    }

    /**
     * Logs failure to analyze a data source due to ingest module startup
     * errors.
     *
     * @param dataSource The data source.
     * @param errors     The ingest module errors.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logIngestModuleStartupErrors(String dataSource, List<IngestModuleError> errors) throws InterruptedException {
        for (IngestModuleError error : errors) {
            log(MessageLevel.ERROR, dataSource, String.format("Analysis of data source by ingest modules not started, %s startup error: %s", error.getModuleDisplayName(), error.getThrowable().getLocalizedMessage()));
        }
    }

    /**
     * Logs the completion of analysis of a data source by the ingest modules.
     *
     * @param dataSource The data source
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logAnalysisCompleted(String dataSource) throws InterruptedException {
        log(MessageLevel.INFO, dataSource, "Analysis of data source by ingest modules completed");
    }

    /**
     * Logs the cancellation of analysis of a data source by an individual
     * ingest module.
     *
     * @param dataSource          The data source.
     * @param cancelledModuleName The display name of the cancelled ingest
     *                            module.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logIngestModuleCancelled(String dataSource, String cancelledModuleName) throws InterruptedException {
        log(MessageLevel.WARNING, dataSource, String.format("%s analysis of data source cancelled", cancelledModuleName));
    }

    /**
     * Logs the cancellation of analysis of a data source by the ingest modules.
     *
     * @param dataSource The data source.
     * @param reason     The reason for cancellation.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logAnalysisCancelled(String dataSource, String reason) throws InterruptedException {
        log(MessageLevel.WARNING, dataSource, String.format("Analysis of data source by ingest modules cancelled: %s", reason));
    }

    /**
     * Logs an automated file export initialization error.
     *
     * @param dataSource The data source.
     * @param ex         The error
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logfileExportStartupError(FileExporter.FileExportException ex) throws InterruptedException {
        log(MessageLevel.ERROR, "", String.format("Automated file export could not be initialized: %s", ex.getLocalizedMessage()));
    }

    /**
     * Logs that automated file export is not enabled.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logFileExportDisabled() throws InterruptedException {
        /*
         * TODO (VIK-1714): Should this be a WARNING with corresponding error
         * state files instead?
         */
        log(MessageLevel.INFO, "", "Automated file export is not enabled");
    }

    /**
     * Logs an automated file export error for a data source.
     *
     * @param dataSource The data source.
     * @param ex         The error
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logFileExportError(String dataSource, FileExporter.FileExportException ex) throws InterruptedException {
        log(MessageLevel.ERROR, dataSource, String.format("Automated file export error for data source: %s", ex.getLocalizedMessage()));
    }

    /**
     * Logs discovery of a crashed auto ingest job for which recovery will be
     * attempted.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logCrashRecoveryWithRetry() throws InterruptedException {
        log(MessageLevel.ERROR, "", "Detected crash while processing, adding data sources again and reprocessing");
    }

    /**
     * Logs discovery of a crashed auto ingest job for which recovery will not
     * be attempted because the retry limit for the job has been reached.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logCrashRecoveryNoRetry() throws InterruptedException {
        log(MessageLevel.ERROR, "", "Detected crash while processing, reached retry limit for processing of image folder");
    }

    /**
     * Logs an unexpected runtime exception, e.g., an exception caught by the
     * auto ingest job processing exception firewall.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logRuntimeException(Exception ex) throws InterruptedException {
        log(MessageLevel.ERROR, "", ex.getLocalizedMessage());
    }

    /**
     * Logs a message for an ingest job.
     *
     * @param dataSource The data source the message is concerned with, may be
     *                   the empty string.
     * @param level      A qualifier, e.g., a message level
     * @param message
     *
     * @throws InterruptedException
     */
    private void log(MessageLevel level, String dataSource, String message) throws InterruptedException {
        /*
         * An exclusive lock on the log file path is used to serialize access to
         * the log file by each auto ingest node so that log entries do not
         * become garbled.
         */
        String logLockPath = getLogPath(caseFolderPath).toString();
//ELTODO        try (Lock lock = CoordinationService.getInstance(AutoIngestManager.ROOT_NAMESPACE).tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, logLockPath, LOCK_TIME_OUT, LOCK_TIME_OUT_UNIT)) {
        try (Lock lock = CoordinationService.getInstance(ROOT_NAMESPACE).tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, logLockPath, LOCK_TIME_OUT, LOCK_TIME_OUT_UNIT)) {
            if (null != lock) {
                File logFile = getLogPath(caseFolderPath).toFile();
                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, logFile.exists())), true)) {
                    writer.println(String.format("%s %s: %s\\%s: %-8s: %s", simpleDateFormat.format((Date.from(Instant.now()).getTime())), hostName, imageFolderPath, dataSource, level.toString(), message));
                }
            } else {
                autopsyLogger.log(Level.SEVERE, String.format("Failed to write message (\"%s\") for processing of %s for %s due to lock timeout", message, imageFolderPath, caseFolderPath));
            }
        } catch (CoordinationServiceException | IOException ex) {
            /*
             * Write to the Autopsy Log here and do not rethrow. Our current
             * policy is to not treat logging issues as show stoppers for auto
             * ingest.
             *
             * TODO (VIK-1707): Is this the right thing to do?
             */
            autopsyLogger.log(Level.SEVERE, String.format("Failed to write case log message (\"%s\") for processing of %s for %s", message, imageFolderPath, caseFolderPath), ex);
        }
    }

}
