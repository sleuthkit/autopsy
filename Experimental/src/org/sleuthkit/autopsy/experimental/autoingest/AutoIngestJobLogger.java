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
package org.sleuthkit.autopsy.experimental.autoingest;

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
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.experimental.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.experimental.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.experimental.coordinationservice.CoordinationService.CoordinationServiceException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestManagerException;

/**
 * A logger for the processing of an auto ingest job by an auto ingest node. An
 * exclusive coordination service lock on the log file is used to serialize
 * access to it by each auto ingest node so that log entries do not become
 * garbled.
 * <p>
 * Normally, the log messages are written to the case auto ingest log in the
 * case directory. If there is an error writing to the log, the message is
 * preserved by writing it to the auto ingest system log, along with the cause
 * of the error.
 */
@Immutable
final class AutoIngestJobLogger {

    private static final String LOG_FILE_NAME = "auto_ingest_log.txt";
    private static final int LOCK_TIME_OUT = 15;
    private static final TimeUnit LOCK_TIME_OUT_UNIT = TimeUnit.MINUTES;
    private static final String DATE_FORMAT_STRING = "yyyy/MM/dd HH:mm:ss";
    private static final SimpleDateFormat logDateFormat = new SimpleDateFormat(DATE_FORMAT_STRING);
    private final Path manifestPath;
    private final String dataSourceFileName;
    private final Path caseDirectoryPath;
    private final String hostName;

    /**
     * Message category added to log messages to make searching for various
     * classes of messages easier, e.g., to make error messages stand out.
     */
    private enum MessageCategory {
        /**
         * Qualifies a log message about normal automated ingest processing.
         */
        INFO,
        /**
         * Qualifies a log message about an unexpected event or condition during
         * automated ingest processing.
         */
        WARNING,
        /**
         * Qualifies a log message about an error event or condition during
         * automated ingest processing.
         */
        ERROR
    }

    /**
     * Gets the path to the automated ingest log for a case.
     *
     * @param caseDirectoryPath The path to the case directory where the log
     *                          resides.
     *
     * @return The path to the automated ingest case log for the case.
     */
    static Path getLogPath(Path caseDirectoryPath) {
        return Paths.get(caseDirectoryPath.toString(), LOG_FILE_NAME);
    }

    /**
     * Constructs a logger for the processing of an auto ingest job by an auto
     * ingest node. The log messages are written to the case auto ingest log, a
     * user-friendly log of of the automated processing for a case that resides
     * in the case directory.
     *
     * The auto iongest log for a case is not intended to be a comprehensive.
     * Advanced users doing troubleshooting of an automated ingest cluster
     * should also consult the Autopsy and system logs as needed.
     *
     * @param manifestPath      The manifest for the auto ingest job.
     * @param caseDirectoryPath The case directory.
     */
    AutoIngestJobLogger(Path manifestPath, String dataSourceFileName, Path caseDirectoryPath) {
        this.manifestPath = manifestPath;
        this.dataSourceFileName = dataSourceFileName;
        this.caseDirectoryPath = caseDirectoryPath;
        hostName = NetworkUtils.getLocalHostName();
    }

    /**
     * Logs the cancellation of an auto ingest job during processing.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logJobCancelled() throws InterruptedException {
        log(MessageCategory.WARNING, "Auto ingest job cancelled during processing");
    }

    /**
     * Logs the presence of a manifest file without a matching data source.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logMissingDataSource() throws InterruptedException {
        log(MessageCategory.ERROR, "Data source file not found"); // RJCTODO: Check for this
    }

    /**
     * Logs an error identifying the type of a data source.
     *
     * @param dataSource The data source.
     * @param errorMessage The error.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file
     *                              path.
     */
    void logDataSourceTypeIdError(String errorMessage) throws InterruptedException {
        log(MessageCategory.ERROR, String.format("Error identifying data source type: %s", errorMessage));
    }

    /**
     * RJCTODO
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logFailedToIdentifyDataSource() throws InterruptedException {
        log(MessageCategory.ERROR, String.format("Failed to identifying data source type, cannot ingest"));
    }

    /**
     * RJCTODO
     *
     * @param dataSourceType
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logDataSourceTypeId(String dataSourceType) throws InterruptedException {
        log(MessageCategory.INFO, String.format("Identified data source as %s", dataSourceType));
    }

    /**
     * Logs cancellation of the addition of a data source to the case database.
     *
     * @param dataSourceType The data source type.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logDataSourceProcessorCancelled(String dataSourceType) throws InterruptedException { // RJCTODO: Is this used now?
        log(MessageCategory.WARNING, String.format("Cancelled adding data source to case as %s", dataSourceType));
    }

    /**
     * Logs the addition of a data source to the case database.
     *
     * @param dataSourceType The data source type.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logDataSourceAdded(String dataSourceType) throws InterruptedException {
        log(MessageCategory.INFO, String.format("Added data source to case as %s", dataSourceType));
    }

    /**
     * Logs a critical error reported by a data source processor when adding a
     * data source to the case database.
     *
     * @param dataSourceType The data source type.
     * @param errorMessage   The error message.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logDataSourceProcessorError(String dataSourceType, String errorMessage) throws InterruptedException {
        log(MessageCategory.ERROR, String.format("Critical error adding data source to case as %s: %s", dataSourceType, errorMessage));
    }

    /**
     * Logs a non-critical error reported by a data source processor when adding
     * a data source to the case database.
     *
     * @param dataSourceType The data source type.
     * @param errorMessage   The error message.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logDataSourceProcessorWarning(String dataSourceType, String errorMessage) throws InterruptedException {
        log(MessageCategory.WARNING, String.format("Critical error adding data source to case as %s: %s", dataSourceType, errorMessage));
    }

    /**
     * Logs an error adding a data source to the case database.
     *
     * @param dataSourceType The data source type.
     * @param dataSource     The data source.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logFailedToAddDataSource(String dataSourceType) throws InterruptedException { // RJCTODO: Why this and logDataSourceProcessorError? Bd handling of critical vs. non-critical?
        log(MessageCategory.ERROR, String.format("Failed to add data source to case as %s", dataSourceType));
    }

    /**
     * RJCTODO: Document and homogenize messages
     *
     * @param errors
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logIngestJobSettingsErrors(List<String> errors) throws InterruptedException {
        for (String error : errors) {
            log(MessageCategory.ERROR, String.format("Settings error, analysis of data source by ingest modules not started: %s", error));
        }
    }

    /**
     * Logs failure to analyze a data source due to ingest module startup
     * errors.
     *
     * @param errors The ingest module errors.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logIngestModuleStartupErrors(List<IngestModuleError> errors) throws InterruptedException {
        for (IngestModuleError error : errors) {
            log(MessageCategory.ERROR, String.format("Analysis of data source by ingest modules not started, %s startup error: %s", error.getModuleDisplayName(), error.getThrowable().getLocalizedMessage()));
        }
    }

    /**
     * Logs failure to analyze a data source because the analysis could not be
     * started due to an ingest manager exception.
     *
     * @param ex The ingest manager exception.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logAnalysisStartupError(IngestManagerException ex) throws InterruptedException {
        log(MessageCategory.ERROR, String.format("Analysis of data source by ingest modules not started: %s", ex.getLocalizedMessage()));
    }

    /**
     * Logs the completion of analysis of a data source by the ingest modules.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logAnalysisCompleted() throws InterruptedException {
        log(MessageCategory.INFO, "Analysis of data source by ingest modules completed");
    }

    /**
     * Logs the cancellation of analysis of a data source by an individual
     * ingest module.
     *
     * @param cancelledModuleName The display name of the cancelled ingest
     *                            module.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logIngestModuleCancelled(String cancelledModuleName) throws InterruptedException {
        log(MessageCategory.WARNING, String.format("%s analysis of data source cancelled", cancelledModuleName));
    }

    /**
     * Logs the cancellation of analysis of a data source by the ingest modules.
     *
     * @param reason The reason for cancellation.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logAnalysisCancelled(String reason) throws InterruptedException {
        log(MessageCategory.WARNING, String.format("Analysis of data source by ingest modules cancelled: %s", reason));
    }

    /**
     * Logs that automated file export is not enabled.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logFileExportDisabled() throws InterruptedException {
        log(MessageCategory.WARNING, "Automated file export is not enabled");
    }

    /**
     * RJCTODO
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logFileExportCompleted() throws InterruptedException {
        log(MessageCategory.INFO, "Automated file export completed");
    }

    /**
     * RJCTODO
     *
     * @param ex
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logFileExportError(Exception ex) throws InterruptedException {
        log(MessageCategory.ERROR, String.format("Error exporting files: %s", ex.getMessage()));
    }

    /**
     * Logs discovery of a crashed auto ingest job for which recovery will be
     * attempted.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logCrashRecoveryWithRetry() throws InterruptedException {
        log(MessageCategory.ERROR, "Detected crash while processing, reprocessing");
    }

    /**
     * Logs discovery of a crashed auto ingest job for which recovery will not
     * be attempted because the retry limit for the job has been reached.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logCrashRecoveryNoRetry() throws InterruptedException {
        log(MessageCategory.ERROR, "Detected crash while processing, reached retry limit for processing");
    }

    /**
     * Logs an unexpected runtime exception, e.g., an exception caught by the
     * automated ingest job processing exception firewall.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    void logErrorCondition(String message) throws InterruptedException {
        log(MessageCategory.ERROR, message);
    }

    /**
     * Writes a message to the case auto ingest log.
     * <p>
     * An exclusive coordination service lock on the log file is used to
     * serialize access to the log file by each auto ingest node so that log
     * entries do not become garbled.
     *
     * @param category The message category.
     * @param message  The message.
     *
     * @throws InterruptedException if interrupted while blocked waiting to
     *                              acquire an exclusive lock on the log file.
     */
    private void log(MessageCategory category, String message) throws InterruptedException {
        String prefix = String.format("Failed to write case auto ingest message (\"%s\") for %s", message, manifestPath);
        try (Lock lock = CoordinationService.getInstance(CoordinationServiceNamespace.getRoot()).tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, getLogPath(caseDirectoryPath).toString(), LOCK_TIME_OUT, LOCK_TIME_OUT_UNIT)) {
            if (null != lock) {
                File logFile = getLogPath(caseDirectoryPath).toFile();
                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, logFile.exists())), true)) {
                    writer.println(String.format("%s %s: %s\\%s: %-8s: %s", logDateFormat.format((Date.from(Instant.now()).getTime())), hostName, manifestPath, dataSourceFileName, category.toString(), message));
                } catch (IOException ex) {
                    AutoIngestSystemLogger.getLogger().log(Level.SEVERE, String.format("%s due to I/O error", prefix), ex);
                }
            } else {
                AutoIngestSystemLogger.getLogger().log(Level.SEVERE, String.format("%s due to lock timeout", prefix));
            }

        } catch (CoordinationServiceException ex) {
            AutoIngestSystemLogger.getLogger().log(Level.SEVERE, String.format("%s due to coordination service error", prefix), ex);
        }
    }

}
