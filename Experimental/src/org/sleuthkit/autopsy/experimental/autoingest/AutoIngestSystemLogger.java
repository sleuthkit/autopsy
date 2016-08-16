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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * A logger for the auto ingest system log, separate from both the case auto
 * ingest log and the application log.
 */
final class AutoIngestSystemLogger {

    private static final int LOG_SIZE = 0; // In bytes, zero is unlimited
    private static final int LOG_FILE_COUNT = 10;
    private static final Logger LOGGER = Logger.getLogger("AutoIngest"); //NON-NLS
    @GuardedBy("AutoIngestSystemLogger")
    private static boolean configured;

    /**
     * Gets a logger for the auto ingest system log, separate from both the case
     * auto ingest log and the application log.
     *
     * @return The logger.
     */
    synchronized final static Logger getLogger() {
        if (!configured) {
            Path logFilePath = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), "var", "log", "auto_ingest.log");
            try {
                FileHandler fileHandler = new FileHandler(logFilePath.toString(), LOG_SIZE, LOG_FILE_COUNT);
                fileHandler.setEncoding(PlatformUtil.getLogFileEncoding());
                fileHandler.setFormatter(new Formatter() {
                    @Override
                    public String format(LogRecord record) {
                        Throwable thrown = record.getThrown();
                        String stackTrace = ""; //NON-NLS
                        while (thrown != null) {
                            stackTrace += thrown.toString() + "\n";
                            for (StackTraceElement traceElem : record.getThrown().getStackTrace()) {
                                stackTrace += "\t" + traceElem.toString() + "\n"; //NON-NLS
                            }
                            thrown = thrown.getCause();
                        }
                        return (new Timestamp(record.getMillis())).toString() + " " //NON-NLS
                                + record.getSourceClassName() + " " //NON-NLS
                                + record.getSourceMethodName() + "\n" //NON-NLS
                                + record.getLevel() + ": " //NON-NLS
                                + this.formatMessage(record) + "\n" //NON-NLS
                                + stackTrace;
                    }
                });
                LOGGER.addHandler(fileHandler);
                LOGGER.setUseParentHandlers(false);
            } catch (SecurityException | IOException ex) {
                throw new RuntimeException(String.format("Error initializing file handler for %s", logFilePath), ex); //NON-NLS
            }
            configured = true;
        }
        return LOGGER;
    }

    /**
     * Prevents instantiation of this utility class.
     */
    private AutoIngestSystemLogger() {
    }

}
