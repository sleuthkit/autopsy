/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogRecord;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Autopsy specialization of the Java Logger class with a custom file handler.
 * Note that the custom loggers are not obtained from the global log manager.
 */
public final class Logger extends java.util.logging.Logger {

    private static final String LOG_ENCODING = PlatformUtil.getLogFileEncoding();
    private static final int LOG_SIZE = 0; // In bytes, zero is unlimited
    private static final String LOG_FILE_NAME = "autopsy.log"; //NON-NLS
    private static final Map<String, Logger> namesToLoggers = new HashMap<>();
    private static final Handler consoleHandler = new java.util.logging.ConsoleHandler();
    private static FileHandler logFileHandler = createFileHandlerWithTraces(PlatformUtil.getLogDirectory());

    /**
     * Creates a custom file handler with a custom message formatter that
     * includes stack traces.
     *
     * @param logDirectory The directory where the log files should reside.
     *
     * @return A custom file handler.
     */
    private static FileHandler createFileHandlerWithTraces(String logDirectory) {
        String logFilePath = Paths.get(logDirectory, LOG_FILE_NAME).toString();
        try {
            FileHandler fileHandler = new FileHandler(logFilePath, LOG_SIZE, UserPreferences.getLogFileCount());
            fileHandler.setEncoding(LOG_ENCODING);
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
            return fileHandler;
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Error initializing file handler for %s", logFilePath), ex); //NON-NLS
        }
    }

    /**
     * Sets the log directory where the log files will be written.
     *
     * @param directoryPath The path to the desired log directory as a string.
     */
    synchronized public static void setLogDirectory(String directoryPath) {
        /*
         * Create a file handler for the new directory and swap it into all of
         * the existing loggers using thread-safe Logger methods. The new
         * handlers are added before the old handlers so that no messages will
         * be lost, but this makes it possible for log messages to be written
         * via the old handlers if logging calls are interleaved with the
         * add/remove handler calls (currently, the base class handlers
         * collection is a CopyOnWriteArrayList).
         */
        FileHandler newFileHandler = createFileHandlerWithTraces(directoryPath);
        for (Logger logger : namesToLoggers.values()) {
            logger.addHandler(newFileHandler);
            logger.removeHandler(logFileHandler);
        }

        /*
         * Close the old file handler and save reference to the new handler
         * so they can be added to any new loggers. This swap is why this method
         * and the two overloads of getLogger() are synchronized, serializing
         * access to logFileHandler.
         */
        logFileHandler.close();
        logFileHandler = newFileHandler;
    }

    /**
     * Finds or creates a customized logger. Hides the base class factory
     * method.
     *
     * @param name A name for the logger. This should normally be a
     *             dot-separated name based on a package name or class name.
     *
     * @return org.sleuthkit.autopsy.coreutils.Logger instance
     */
    synchronized public static Logger getLogger(String name) {
        return getLogger(name, null);
    }

    /**
     * Finds or creates a customized logger. Hides the base class factory
     * method.
     *
     * @param name               A name for the logger. This should normally be
     *                           a dot-separated name based on a package name or
     *                           class name.
     * @param resourceBundleName Name of ResourceBundle to be used for
     *                           localizing messages for this logger. May be
     *                           null.
     *
     * @return org.sleuthkit.autopsy.coreutils.Logger instance
     */
    synchronized public static Logger getLogger(String name, String resourceBundleName) {
        if (!namesToLoggers.containsKey(name)) {
            Logger logger = new Logger(name, resourceBundleName);
            logger.addHandler(logFileHandler);
            namesToLoggers.put(name, logger);
        }
        return namesToLoggers.get(name);
    }

    /**
     * Constructs a customized logger.
     *
     * @param name               A name for the logger. This should normally be
     *                           a dot-separated name based on a package name or
     *                           class name.
     * @param resourceBundleName Name of ResourceBundle to be used for
     *                           localizing messages for this logger. May be
     *                           null.
     */
    private Logger(String name, String resourceBundleName) {
        super(name, resourceBundleName);
        super.setUseParentHandlers(false);
        if (Version.getBuildType() == Version.Type.DEVELOPMENT) {
            super.addHandler(consoleHandler);
        }
    }
}
