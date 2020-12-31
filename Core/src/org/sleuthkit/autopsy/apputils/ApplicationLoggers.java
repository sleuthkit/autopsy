/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.apputils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * A utility that creates and stores application loggers.
 *
 * TODO (Jira-7175): This code is the third copy of code that originally
 * appeared in org.sleuthkit.autopsy.coreutils.Logger. The second copy is in
 * org.sleuthkit.autopsy.experimental.autoingest.AutoIngestSystemLogger. This
 * class should allow the replacement of AutoIngestSystemLogger and the
 * elimination of duplicate code in coreutils.Logger through delegation
 * (maintaining the public API for coreutils.Logger).
 */
final public class ApplicationLoggers {

    private static final int LOG_SIZE = 50000000; // In bytes, zero is unlimited.
    private static final int LOG_FILE_COUNT = 10;
    private static final String NEWLINE = System.lineSeparator();
    private static final Map<String, Logger> loggers = new HashMap<>();

    /**
     * Gets the logger for a given application log file. The log file will be
     * located in the var/log directory of the platform user directory and will
     * have a name of the form [log name].log.
     *
     * @return The logger.
     */
    synchronized public static Logger getLogger(String logName) {
        Logger logger;
        if (loggers.containsKey(logName)) {
            logger = loggers.get(logName);
        } else {
            logger = Logger.getLogger(logName);
            Path logFilePath = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), "var", "log", String.format("%s.log", logName));
            try {
                FileHandler fileHandler = new FileHandler(logFilePath.toString(), LOG_SIZE, LOG_FILE_COUNT);
                fileHandler.setEncoding(PlatformUtil.getLogFileEncoding());
                fileHandler.setFormatter(new Formatter() {
                    @Override
                    public String format(LogRecord record) {
                        Throwable thrown = record.getThrown();
                        String stackTrace = ""; //NON-NLS
                        while (thrown != null) {
                            stackTrace += thrown.toString() + NEWLINE;
                            for (StackTraceElement traceElem : record.getThrown().getStackTrace()) {
                                stackTrace += "\t" + traceElem.toString() + NEWLINE; //NON-NLS
                            }
                            thrown = thrown.getCause();
                        }
                        return (new Timestamp(record.getMillis())).toString() + " " //NON-NLS
                                + record.getSourceClassName() + " " //NON-NLS
                                + record.getSourceMethodName() + NEWLINE
                                + record.getLevel() + ": " //NON-NLS
                                + this.formatMessage(record) + NEWLINE
                                + stackTrace;
                    }
                });
                logger.addHandler(fileHandler);
                logger.setUseParentHandlers(false);
            } catch (SecurityException | IOException ex) {
                throw new RuntimeException(String.format("Error initializing file handler for %s", logFilePath), ex); //NON-NLS
            }
            loggers.put(logName, logger);
        }
        return logger;
    }

    /**
     * Prevents instantiation of this utility class.
     */
    private ApplicationLoggers() {
    }

}
