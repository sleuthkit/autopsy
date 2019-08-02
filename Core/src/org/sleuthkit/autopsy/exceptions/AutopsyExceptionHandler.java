/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.exceptions;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import javax.swing.JOptionPane;
import org.openide.util.lookup.ServiceProvider;
import org.netbeans.core.NbErrorManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 * Replaces default NetBeans exception handler. Displays messages in a dialog.
 */
@ServiceProvider(service = Handler.class, supersedes = "org.netbeans.core.NbErrorManager")
public class AutopsyExceptionHandler extends Handler {

    static final int INFO_VALUE = Level.INFO.intValue();
    static final int WARNING_VALUE = Level.WARNING.intValue();
    static final int SEVERE_VALUE = Level.SEVERE.intValue();
    static final Handler nbErrorManager = new NbErrorManager(); // Default NetBeans handler
    static final Version.Type buildType = Version.getBuildType();
    private final Logger logger = Logger.getLogger(AutopsyExceptionHandler.class.getName());

    public AutopsyExceptionHandler() {
        super();

        this.setLevel(Level.SEVERE);
        /*
         * if (buildType == Version.Type.DEVELOPMENT) //for dev builds, show
         * dialogs for WARNING and above this.setLevel(Level.WARNING); else
         * //for production builds, show dialogs for SEVERE and above (TODO in
         * future consider not show any, explicit dialogs should be in place)
         * this.setLevel(Level.SEVERE);
         */

        this.setFilter(new ExceptionFilter());
        this.setFormatter(new SimpleFormatter());
    }

    @Override
    public void publish(LogRecord record) {

        if (isLoggable(record)) {
            final String title = getTitleForLevelValue(record.getLevel().intValue());
            final String message = formatExplanation(record);

            if (record.getMessage() != null) {
                // Throwable was anticipated, caught and logged. Display log message and throwable message.
                MessageNotifyUtil.Notify.error(title, message);
                logger.log(Level.SEVERE, "Unexpected error: " + title + ", " + message); //NON-NLS
            } else {
                // Throwable (unanticipated) error. Use built-in exception handler to offer details, stacktrace.
                nbErrorManager.publish(record);
            }
        }
    }

    /**
     * Filter only accepts records with exceptions attached.
     */
    private static class ExceptionFilter implements Filter {

        @Override
        public boolean isLoggable(LogRecord record) {
            // True if there is an uncaught exception being thrown.
            return record.getThrown() != null;
        }
    }

    /**
     *
     * @param record A LogRecord with both a message and associated Throwable
     *               set.
     *
     * @return A String containing the log message and the cause of the
     *         Throwable (if there is one).
     */
    private String formatExplanation(LogRecord record) {
        final String logMessage = getFormatter().formatMessage(record);
        String explanation = record.getThrown().getMessage();
        String causeMessage = (explanation != null) ? "\nCaused by: " + explanation : ""; //NON-NLS

        return logMessage + causeMessage;
    }

//    It's harder to do this cleanly than I thought, because Exceptions
//    initialized with no message copy and prepend the cause's message
//
//    private String recursiveExplanation(Throwable e) {
//        String message = e.getMessage();
//        String explanation = (message != null) ? "\nCaused by: " + message : "";
//        Throwable cause = e.getCause();
//        if (cause == null) {
//            return explanation;
//        } else {
//            return explanation + recursiveExplanation(cause);
//        }
//    }
    private static int getMessageTypeForLevelValue(int levelValue) {
        if (levelValue >= SEVERE_VALUE) {
            return JOptionPane.ERROR_MESSAGE;
        } else if (levelValue >= WARNING_VALUE) {
            return JOptionPane.WARNING_MESSAGE;
        } else {
            return JOptionPane.INFORMATION_MESSAGE;
        }
    }

    private static String getTitleForLevelValue(int levelValue) {
        if (levelValue >= SEVERE_VALUE) {
            return "Error"; //NON-NLS
        } else if (levelValue >= WARNING_VALUE) {
            return "Warning"; //NON-NLS
        } else {
            return "Message"; //NON-NLS
        }
    }

    @Override
    public void flush() {
        // no buffer to flush
    }

    @Override
    public void close() throws SecurityException {
        // no resources to close
    }
}
