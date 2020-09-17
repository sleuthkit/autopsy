/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2017 Basis Technology Corp.
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
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;

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
    private final String manifestFileName;
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
     * @param manifestPath       The manifest for the auto ingest job.
     * @param dataSourceFileName The file name of the data source for the auto
     *                           ingest job.
     * @param caseDirectoryPath  The absolute path to the case directory.
     */
    AutoIngestJobLogger(Path manifestPath, String dataSourceFileName, Path caseDirectoryPath) {
        this.manifestPath = manifestPath;
        manifestFileName = manifestPath.getFileName().toString();
        this.dataSourceFileName = dataSourceFileName;
        this.caseDirectoryPath = caseDirectoryPath; 
        hostName = NetworkUtils.getLocalHostName();
    }

    /**
     * Logs the cancellation of an auto ingest job during processing.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logJobCancelled() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.WARNING, "Auto ingest job cancelled during processing");
    }

    /**
     * Logs the presence of a manifest file without a matching data source.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logMissingDataSource() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Data source file not found");
    }

    /**
     * Logs a failure to extract an archived data source.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logFailedToExtractDataSource() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Failed to extract data source from archive");
    }

    /**
     * Logs a failure to parse a Cellebrite logical report data source.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logFailedToParseLogicalReportDataSource() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Failed to parse Cellebrite logical report data source");
    }

    /**
     * Logs a failure to identify data source processor for the data source.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logFailedToIdentifyDataSource() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, String.format("Failed to identify data source"));
    }

    /**
     * Logs cancellation of the addition of a data source to the case database.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logDataSourceProcessorCancelled() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.WARNING, "Cancelled adding data source to case");
    }

    /**
     * Logs selection of a data source processor
     *
     * @param dsp Name of the data source processor
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logDataSourceProcessorSelected(String dsp) throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.INFO, "Using data source processor: " + dsp);
    }
    
    /**
     * Log that a data source is being skipped.
     * 
     * @param dataSourceName The name of the data source
     * 
     * @throws AutoIngestJobLogger.AutoIngestJobLoggerException
     * @throws InterruptedException 
     */
    void logSkippingDataSource(String dataSourceName) throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.INFO, "File type can not currently be processed");
    }    

    /**
     * Logs the failure of the selected data source processor.
     *
     * @param dsp Name of the data source processor
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logDataSourceProcessorError(String dsp) throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Error processing with data source processor: " + dsp);
    }

    /**
     * Logs the addition of a data source to the case database.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logDataSourceAdded() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.INFO, "Added data source to case");
    }

    /**
     * Logs an failure adding a data source to the case database.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logFailedToAddDataSource() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Failed to add data source to case");
    }

    /**
     * Logs failure of a data source to produce content.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logNoDataSourceContent() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Data source failed to produce content");
    }

    /**
     * Logs failure to analyze a data source due to ingest job settings errors.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logIngestJobSettingsErrors() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Failed to analyze data source due to settings errors");
    }
    
    /**
     * Logs failure to analyze a data source, possibly due to ingest job settings errors.
     * Used with streaming ingest since incorrect settings are the most likely cause
     * of the error.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logProbableIngestJobSettingsErrors() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Failed to analyze data source, probably due to ingest settings errors");
    }    

    /**
     * Logs failure to analyze a data source due to ingest module startup
     * errors.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logIngestModuleStartupErrors() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Failed to analyze data source due to ingest module startup errors");
    }

    /**
     * Logs failure to analyze a data source because the analysis could not be
     * started due to an ingest manager exception.
     *
     * @param ex The ingest manager exception.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logAnalysisStartupError() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Failed to analyze data source due to ingest job startup error");
    }

    /**
     * Logs the completion of analysis of a data source by the ingest modules.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logAnalysisCompleted() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.INFO, "Analysis of data source completed");
    }

    /**
     * Logs the cancellation of analysis of a data source by an individual
     * ingest module.
     *
     * @param cancelledModuleName The display name of the cancelled ingest
     *                            module.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logIngestModuleCancelled(String cancelledModuleName) throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.WARNING, String.format("%s analysis of data source cancelled", cancelledModuleName));
    }

    /**
     * Logs the cancellation of analysis of a data source by the ingest modules.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logAnalysisCancelled() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.WARNING, "Analysis of data source cancelled");
    }

    /**
     * Logs completion of file export.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logFileExportCompleted() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.INFO, "Automated file export completed");
    }

    /**
     * Logs failure to complete file export.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logFileExportError() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Error exporting files");
    }

    /**
     * Logs discovery of a crashed auto ingest job for which recovery will be
     * attempted.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logCrashRecoveryWithRetry() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Detected crash while processing, reprocessing");
    }

    /**
     * Logs discovery of a crashed auto ingest job for which recovery will not
     * be attempted because the retry limit for the job has been reached.
     *
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    void logCrashRecoveryNoRetry() throws AutoIngestJobLoggerException, InterruptedException {
        log(MessageCategory.ERROR, "Detected crash while processing, reached retry limit for processing");
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
     * @throws AutoIngestJobLoggerException if there is an error writing the log
     *                                      message.
     * @throws InterruptedException         if interrupted while blocked waiting
     *                                      to acquire an exclusive lock on the
     *                                      log file.
     */
    private void log(MessageCategory category, String message) throws AutoIngestJobLoggerException, InterruptedException {
        Path logPath = getLogPath(caseDirectoryPath);
        try (Lock lock = CoordinationService.getInstance().tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, logPath.toString(), LOCK_TIME_OUT, LOCK_TIME_OUT_UNIT)) {
            if (null != lock) {
                File logFile = logPath.toFile();
                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, logFile.exists())), true)) {
                    writer.println(String.format("%-8s: %s %s: %s: %s: %s", category.toString(), logDateFormat.format((Date.from(Instant.now()).getTime())), hostName, manifestFileName, dataSourceFileName, message));
                } catch (IOException ex) {
                    throw new AutoIngestJobLoggerException(String.format("Failed to write case auto ingest log message (\"%s\") for %s", message, manifestPath), ex);
                }
            } else {
                throw new AutoIngestJobLoggerException(String.format("Failed to write case auto ingest log message (\"%s\") for %s due to time out acquiring log lock", message, manifestPath));
            }
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestJobLoggerException(String.format("Failed to write case auto ingest log message (\"%s\") for %s", message, manifestPath), ex);
        }
    }

    /**
     * Exception thrown when there is a problem writing a log message.
     */
    final static class AutoIngestJobLoggerException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to throw when there is a problem writing a
         * log message.
         *
         * @param message The exception message.
         */
        private AutoIngestJobLoggerException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to throw when there is a problem writing a
         * log message.
         *
         * @param message The exception message.
         * @param cause   The cause of the exception, if it was an exception.
         */
        private AutoIngestJobLoggerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
