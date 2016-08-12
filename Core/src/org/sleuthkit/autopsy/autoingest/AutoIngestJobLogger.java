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
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.logging.Level;
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
 * log messages are written to the case auto ingest log in the case directory.
 * When an error message is logges, an alert file is also written to the case
 * directory.
 */
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
         * Qualifies a log message about an unexpected event or condtion during
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
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logJobCancelled() throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.WARNING, "Auto ingest job cancelled during processing");
    }

    /**
     * Logs the presence of a manifest file without a matching data source.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logMissingDataSource() throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, "Data source file not found"); // RJCTODO: Check for this
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
    void logDataSourceTypeIdError(Exception ex) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, String.format("Error identifying data source type: %s", ex.getLocalizedMessage()));
    }

    /**
     * RJCTODO
     * @throws InterruptedException
     * @throws org.sleuthkit.autopsy.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException 
     */
    void logFailedToIdentifyDataSource() throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, String.format("Failed to identifying data source type, cannot ingest"));        
    }
    
    /**
     * RJCTODO
     * @param dataSourceType
     * @throws InterruptedException
     * @throws org.sleuthkit.autopsy.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException 
     */
    void logDataSourceTypeId(String dataSourceType) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.INFO, String.format("Identified data source as %s", dataSourceType));        
    }
        
    /**
     * Logs cancellation of the addition of a data source to the case database.
     *
     * @param dataSourceType The data source type.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logDataSourceProcessorCancelled(String dataSourceType) throws InterruptedException, AutoIngestJobLoggerException { // RJCTODO: Is this used now?
        log(MessageCategory.WARNING, String.format("Cancelled adding data source to case as %s", dataSourceType));
    }

    /**
     * Logs the addition of a data source to the case database.
     *
     * @param dataSourceType The data source type.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logDataSourceAdded(String dataSourceType) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.INFO, String.format("Added data source to case as %s", dataSourceType));
    }

    /**
     * Logs a critical error reported by a data source processor when adding a
     * data source to the case database.
     *
     * @param dataSourceType The data source type.
     * @param errorMessage   The error message.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logDataSourceProcessorError(String dataSourceType, String errorMessage) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, String.format("Critical error adding data source to case as %s: %s", dataSourceType, errorMessage));
    }

    /**
     * Logs a non-critical error reported by a data source processor when adding
     * a data source to the case database.
     *
     * @param dataSourceType The data source type.
     * @param errorMessage   The error message.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logDataSourceProcessorWarning(String dataSourceType, String errorMessage) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.WARNING, String.format("Critical error adding data source to case as %s: %s", dataSourceType, errorMessage));
    }

    /**
     * Logs an error adding a data source to the case database.
     *
     * @param dataSourceType The data source type.
     * @param dataSource     The data source.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logFailedToAddDataSource(String dataSourceType) throws InterruptedException, AutoIngestJobLoggerException { // RJCTODO: Why this and logDataSourceProcessorError? Bd handling of critical vs. non-critical?
        log(MessageCategory.ERROR, String.format("Failed to add data source to case as %s", dataSourceType));
    }

    /**
     * RJCTODO: Document and homogenize messages
     * @param errors
     * @throws InterruptedException
     * @throws org.sleuthkit.autopsy.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException 
     */
    void logIngestJobSettingsErrors(List<String> errors) throws InterruptedException, AutoIngestJobLoggerException {
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
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logIngestModuleStartupErrors(List<IngestModuleError> errors) throws InterruptedException, AutoIngestJobLoggerException {
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
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logAnalysisStartupError(IngestManagerException ex) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, String.format("Analysis of data source by ingest modules not started: %s", ex.getLocalizedMessage()));
    }

    /**
     * Logs the completion of analysis of a data source by the ingest modules.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logAnalysisCompleted() throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.INFO, "Analysis of data source by ingest modules completed");
    }

    /**
     * Logs the cancellation of analysis of a data source by an individual
     * ingest module.
     *
     * @param cancelledModuleName The display name of the cancelled ingest
     *                            module.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logIngestModuleCancelled(String cancelledModuleName) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.WARNING, String.format("%s analysis of data source cancelled", cancelledModuleName));
    }

    /**
     * Logs the cancellation of analysis of a data source by the ingest modules.
     *
     * @param reason The reason for cancellation.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logAnalysisCancelled(String reason) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.WARNING, String.format("Analysis of data source by ingest modules cancelled: %s", reason));
    }

    /**
     * Logs that automated file export is not enabled.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logFileExportDisabled() throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.INFO, "Automated file export is not enabled");
    }

    /**
     * RJCTODO
     * @throws InterruptedException
     * @throws org.sleuthkit.autopsy.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException 
     */
    void logFileExportCompleted() throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.INFO, "Automated file export completed");
    }

    /**
     * RJCTODO
     * @param ex
     * @throws InterruptedException
     * @throws org.sleuthkit.autopsy.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException 
     */
    void logFileExportError(Exception ex) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, String.format("Error exporting files: %s", ex.getMessage()));
    }

    /**
     * Logs discovery of a crashed auto ingest job for which recovery will be
     * attempted.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logCrashRecoveryWithRetry() throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, "Detected crash while processing, reprocessing");
    }

    /**
     * Logs discovery of a crashed auto ingest job for which recovery will not
     * be attempted because the retry limit for the job has been reached.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logCrashRecoveryNoRetry() throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, "Detected crash while processing, reached retry limit for processing");
    }

    /**
     * Logs an unexpected runtime exception, e.g., an exception caught by the
     * automated ingest job processing exception firewall.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws CoordinationServiceException if a problem with the coordination
     *                                      service prevents acquisition of a
     *                                      lock on the log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file.
     */
    void logErrorCondition(String message) throws InterruptedException, AutoIngestJobLoggerException {
        log(MessageCategory.ERROR, message);
    }

    /**
     * Writes a message to the case auto ingest log. If the message is an error
     * message, also creates an alert file in the case directory for the job. If
     * either or both of these operations fail, the details are written to the
     * auto ingest log before a more generic exception is thrown to ensure that
     * no information is lost.
     *
     * @param category The message category.
     *
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     * @throws AutoIngestJobLoggerException if there is a problem writing to the
     *                                      log file and/or creating an alert
     *                                      file.
     */
    /**
     * Writes a message to the case auto ingest loga nd optionally creates an
     * alert file in the case directory for the job. If either or both of these
     * operations fail, the details are written to the system auto ingest log
     * before a more generic exception is thrown to ensure that no information
     * is lost.
     *
     * @param category        The message category.
     * @param message         The message.
     * @param createAlertFile Whether or not to create an alert file.
     *
     * @throws
     * org.sleuthkit.autopsy.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException
     * @throws InterruptedException
     */
    private void log(MessageCategory category, String message) throws AutoIngestJobLoggerException, InterruptedException {
        /*
         * An exclusive lock on the log file path is used to serialize access to
         * the log file by each auto ingest node so that log entries do not
         * become garbled.
         */
        String genericExceptionMessage = String.format("Failed to write to case auto ingest log and/or failed to write alert file for %s", manifestPath);
        String autoIngestLogPrefix = String.format("Failed to write case auto ingest message (\"%s\") for %s", message, manifestPath);
        String logLockPath = getLogPath(caseDirectoryPath).toString();
        try (Lock lock = CoordinationService.getInstance(CoordinationServiceNamespace.getRoot()).tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, logLockPath, LOCK_TIME_OUT, LOCK_TIME_OUT_UNIT)) {
            if (null != lock) {
                File logFile = getLogPath(caseDirectoryPath).toFile();
                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, logFile.exists())), true)) {
                    writer.println(String.format("%s %s: %s\\%s: %-8s: %s", logDateFormat.format((Date.from(Instant.now()).getTime())), hostName, manifestPath, dataSourceFileName, category.toString(), message));
                } catch (Exception ex) {
                    AutoIngestSystemLogger.getLogger().log(Level.SEVERE, String.format("%s due to I/O error", autoIngestLogPrefix), ex);
                    throw new AutoIngestJobLoggerException(genericExceptionMessage, ex);
                }
            } else {
                AutoIngestSystemLogger.getLogger().log(Level.SEVERE, String.format("%s due to lock timeout", autoIngestLogPrefix));
                throw new AutoIngestJobLoggerException(genericExceptionMessage);
            }
        } catch (InterruptedException ex) {
            AutoIngestSystemLogger.getLogger().log(Level.SEVERE, String.format("%s due to interrupt", autoIngestLogPrefix), ex);
            throw ex;

        } catch (CoordinationServiceException ex) {
            AutoIngestSystemLogger.getLogger().log(Level.SEVERE, String.format("%s due to coordination service exception", autoIngestLogPrefix), ex);
            throw new AutoIngestJobLoggerException(genericExceptionMessage);

        } finally {
            if (MessageCategory.INFO != category) {
                try {
                    AutoIngestAlertFile.create(caseDirectoryPath);
                } catch (AutoIngestAlertFile.AutoIngestAlertFileException alertex) {
                    AutoIngestSystemLogger.getLogger().log(Level.SEVERE, String.format("Error creating alert file for %s", manifestPath), alertex);
                    /*
                     * Note that this instance of the generic exception replaces
                     * any instance thrown in the the try bloc, but it does not
                     * matter since the instances are identical.
                     */
                    throw new AutoIngestJobLoggerException(genericExceptionMessage, alertex);
                }
            }
        }
    }

    /**
     * Exception thrown if an automated ingest log message cannot be written.
     */
    static final class AutoIngestJobLoggerException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to throw if an automated ingest log message
         * cannot be written.
         *
         * @param message The exception message.
         */
        public AutoIngestJobLoggerException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to throw if an automated ingest log message
         * cannot be written.
         *
         * @param message The exception message.
         * @param cause   The exception cause, if it was a Throwable.
         */
        public AutoIngestJobLoggerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
