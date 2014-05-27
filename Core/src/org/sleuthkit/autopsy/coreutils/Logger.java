/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2014 Basis Technology Corp.
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
import java.util.logging.*;

/**
 * Autopsy specialization of the Java Logger class with custom file handlers.
 */
public final class Logger extends java.util.logging.Logger {

    private static final String LOG_ENCODING = PlatformUtil.getLogFileEncoding();
    private static final String LOG_DIR = PlatformUtil.getLogDirectory();
    private static final int LOG_SIZE = 0; // In bytes, zero is unlimited
    private static final int LOG_FILE_COUNT = 10;
    private static final String LOG_WITHOUT_STACK_TRACES = "autopsy.log"; //NON-NLS
    private static final String LOG_WITH_STACK_TRACES = "autopsy_traces.log"; //NON-NLS
    private static final FileHandler userFriendlyLogFile = createFileHandler(LOG_WITHOUT_STACK_TRACES);
    private static final FileHandler developersLogFile = createFileHandler(LOG_WITH_STACK_TRACES);
    private static final Handler console = new java.util.logging.ConsoleHandler();

    private static FileHandler createFileHandler(String fileName) {
        try {
            FileHandler f = new FileHandler(LOG_DIR + fileName, LOG_SIZE, LOG_FILE_COUNT);
            f.setEncoding(LOG_ENCODING);
            f.setFormatter(new SimpleFormatter());
            return f;
        } catch (IOException e) {
            throw new RuntimeException("Error initializing " + fileName + " file handler", e); //NON-NLS
        }
    }

    /**
     * Factory method to retrieve a org.sleuthkit.autopsy.coreutils.Logger
     * instance derived from java.util.logging.Logger. Hides the base class
     * factory method.
     *
     * @param name A name for the logger. This should be a dot-separated name
     * and should normally be based on the package name or class name.
     * @return org.sleuthkit.autopsy.coreutils.Logger instance
     */
    public static Logger getLogger(String name) {
        return new Logger(java.util.logging.Logger.getLogger(name));
    }

    /**
     * Factory method to retrieve a org.sleuthkit.autopsy.coreutils.Logger
     * instance derived from java.util.logging.Logger. Hides the base class
     * factory method.
     *
     * @param name A name for the logger. This should be a dot-separated name
     * and should normally be based on the package name or class name.
     * @param resourceBundleName - name of ResourceBundle to be used for
     * localizing messages for this logger. May be null if none of the messages
     * require localization.
     * @return org.sleuthkit.autopsy.coreutils.Logger instance
     */
    public static Logger getLogger(String name, String resourceBundleName) {
        return new Logger(java.util.logging.Logger.getLogger(name, resourceBundleName));
    }

    private Logger(java.util.logging.Logger log) {
        super(log.getName(), log.getResourceBundleName());
        if (Version.getBuildType() == Version.Type.DEVELOPMENT) {
            addHandler(console);
        }
        setUseParentHandlers(false);
        addHandler(userFriendlyLogFile);
        addHandler(developersLogFile);
    }

    @Override
    public void log(Level level, String message, Throwable thrown) {
        logUserFriendlyOnly(level, message, thrown);
        removeHandler(userFriendlyLogFile);
        super.log(level, message, thrown);
        addHandler(userFriendlyLogFile);
    }

    @Override
    public void logp(Level level, String sourceClass, String sourceMethod, String message, Throwable thrown) {
        logUserFriendlyOnly(level, message, thrown);
        removeHandler(userFriendlyLogFile);
        super.logp(level, sourceClass, sourceMethod, message, thrown);
        addHandler(userFriendlyLogFile);
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String message, Throwable thrown) {
        logUserFriendlyOnly(level, message, thrown);
        removeHandler(userFriendlyLogFile);
        super.logrb(level, sourceClass, sourceMethod, bundleName, message, thrown);
        addHandler(userFriendlyLogFile);
    }

    private void logUserFriendlyOnly(Level level, String message, Throwable thrown) {
        removeHandler(developersLogFile);
        super.log(level, "{0}\nException:  {1}", new Object[]{message, thrown.toString()}); //NON-NLS
        addHandler(developersLogFile);        
    }
    
    @Override
    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        removeHandler(userFriendlyLogFile);
        super.throwing(sourceClass, sourceMethod, thrown);
        addHandler(userFriendlyLogFile);
    }
}
