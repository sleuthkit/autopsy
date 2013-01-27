/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import java.nio.charset.Charset;
import java.util.logging.*;
import org.openide.modules.Places;

/**
 * Custom Autopsy logger wrapper over java.util.logging.Logger with default file
 * streams logging to autopsy.log (general high level messages),
 * autopsy_traces.log (also including exception traces).
 * In development build, those are also redirected to console / messages log.
 *
 * Contains a utility method to log user actions to autopsy_actions.log via noteAction()
 * 
 * Use like java.util.logging.Logger API, get a
 * org.sleuthkit.autopsy.coreutils.Logger handle using factory method
 * org.sleuthkit.autopsy.coreutils.Logger.getLogger(String name) passing
 * component/module/class name.
 *
 * If logging behavior is to be customized, you can add or remove handlers or
 * filters from the provided Logger object.
 */
public class Logger extends java.util.logging.Logger {

    private static final String LOG_ENCODING = PlatformUtil.getLogFileEncoding();
    private static final String LOG_DIR = PlatformUtil.getLogDirectory();
    static final int LOG_SIZE = 0; // in bytes, zero is unlimited
    static final int LOG_FILE_COUNT = 10;
    
    //File Handlers which point to the output logs
    private static  final FileHandler traces = initTraces();
    private static  final FileHandler normal = initNormal();
    private static final Handler console = new java.util.logging.ConsoleHandler();
    private static final java.util.logging.Logger actionsLogger = initActionsLogger();
   

    /**
     * Main messages log file name
     */
    public static final String messagesLog = "autopsy.log";
    /**
     * Detailed exception trace log file name
     */
    public static final String tracesLog = "autopsy_traces.log";
    
    /**
     * Action logger file name
     */
    public static final String actionsLog = "autopsy_actions.log";

    /**
     * Static blocks to get around compile errors such as "variable might not
     * have been initialized
   *
     */
    //<editor-fold defaultstate="visible" desc="static block initializers">
    private static FileHandler initTraces() {

        try {

            FileHandler f = new FileHandler(LOG_DIR + tracesLog, LOG_SIZE, LOG_FILE_COUNT);
            f.setEncoding(LOG_ENCODING);
            f.setFormatter(new SimpleFormatter());
            return f;
        } catch (IOException e) {
            throw new RuntimeException("Error initializing traces logger", e);
        }
    }

    private static FileHandler initNormal() {
        try {
            FileHandler f = new FileHandler(LOG_DIR + messagesLog, LOG_SIZE, LOG_FILE_COUNT);
            f.setEncoding(LOG_ENCODING);
            f.setFormatter(new SimpleFormatter());
            return f;
        } catch (IOException e) {
            throw new RuntimeException("Error initializing normal logger", e);
        }
    }
    
    private static java.util.logging.Logger initActionsLogger() {
        try {
            FileHandler f = new FileHandler(LOG_DIR + actionsLog, LOG_SIZE, LOG_FILE_COUNT);
            f.setEncoding(LOG_ENCODING);
            f.setFormatter(new SimpleFormatter());
            java.util.logging.Logger _actionsLogger = java.util.logging.Logger.getLogger("Actions");
            _actionsLogger.setUseParentHandlers(false);
            _actionsLogger.addHandler(f);
            _actionsLogger.addHandler(console);
            return _actionsLogger;
        } catch (IOException e) {
            throw new RuntimeException("Error initializing actions logger", e);
        }
    }

    //</editor-fold>
    private Logger(java.util.logging.Logger log) {
        super(log.getName(), log.getResourceBundleName());
        //do forward to messages, so that IDE window shows them
        if (Version.getBuildType() == Version.Type.DEVELOPMENT) {
            addHandler(console);
        }
        setUseParentHandlers(false); //do not forward to parent logger, sharing static handlers anyway
        //addHandler(new AutopsyExceptionHandler());
        addHandler(normal);
        addHandler(traces);
    }


       
    
    /**
     * Log an action to autopsy_actions.log
     * @param actionClass class where user triggered action occurs
     */
    public static void noteAction(Class<?> actionClass) {
        actionsLogger.log(Level.INFO, "Action performed: {0}", actionClass.getName());
    }

    /**
     * Factory method to retrieve a org.sleuthkit.autopsy.coreutils.Logger
     * instance The logger logs by default to autopsy.log and
     * autopsy_traces.log. Add/remove handlers if the desired behavior should be
     * different.
     *
     * @param name ID for the logger or empty string for a root logger
     * @return org.sleuthkit.autopsy.coreutils.Logger instance
     */
    public static Logger getLogger(String name) {
        Logger l = new Logger(java.util.logging.Logger.getLogger(name));
        return l;
    }

    /**
     * Factory method to retrieve a org.sleuthkit.autopsy.coreutils.Logger
     * instance
     *
     * @param name ID for the logger or empty string for a root logger
     * @param resourceBundleName bundle name associated with the logger
     * @return org.sleuthkit.autopsy.coreutils.Logger instance
     */
    public static Logger getLogger(String name, String resourceBundleName) {
        return new Logger(Logger.getLogger(name, resourceBundleName));
    }

    @Override
    public void log(Level level, String message, Throwable thrown) {
        super.log(level, message + "\nException:  " + thrown.toString());
        removeHandler(normal);
        super.log(level, message, thrown);
        addHandler(normal);
    }

    @Override
    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        removeHandler(normal);
        super.throwing(sourceClass, sourceMethod, thrown);
        addHandler(normal);
    }
}
