/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.Timer;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * Monitors disk space and memory and cancels ingest if disk space runs low.
 * <p>
 * Note: This should be a singleton and currently is used as such, with the
 * only instance residing in the IngestManager class.
 */
public final class IngestMonitor {

    public static final int DISK_FREE_SPACE_UNKNOWN = -1;
    private static final int INITIAL_INTERVAL_MS = 60000; //1 min.
    private static final int MAX_LOG_FILES = 3;
    private static final java.util.logging.Logger MONITOR_LOGGER = java.util.logging.Logger.getLogger("monitor"); //NON-NLS
    private final Logger logger = Logger.getLogger(IngestMonitor.class.getName());
    private Timer timer;
    private MonitorTimerAction timerAction;

    /**
     * Constructs an object that monitors disk space and memory and cancels
     * ingest if disk space runs low.
     */
    IngestMonitor() {
        /*
         * Setup a separate memory usage logger.
         */
        try {
            FileHandler monitorLogHandler = new FileHandler(PlatformUtil.getUserDirectory().getAbsolutePath() + "/var/log/monitor.log", 0, MAX_LOG_FILES); //NON-NLS
            monitorLogHandler.setFormatter(new SimpleFormatter());
            monitorLogHandler.setEncoding(PlatformUtil.getLogFileEncoding());
            MONITOR_LOGGER.setUseParentHandlers(false);
            MONITOR_LOGGER.addHandler(monitorLogHandler);
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, "Failed to create memory usage logger", ex); //NON-NLS
        }
    }

    /**
     * Starts the ingest timerAction.
     */
    void start() {
        timerAction = new MonitorTimerAction();
        timer = new Timer(INITIAL_INTERVAL_MS, timerAction);
        timer.start();
    }

    /**
     * Stops the ingest timerAction.
     */
    void stop() {
        if (null != timer) {
            timer.stop();
        }
    }

    /**
     * Checks whether or not the ingest timerAction is running
     *
     * @return True or false
     */
    boolean isRunning() {
        return (null != timer && timer.isRunning());
    }

    /**
     * Gets the free space, in bytes, of the drive where the case folder for the
     * current case resides.
     *
     * @return Free space in bytes or -1 if free sapce could not be determined.
     */
    long getFreeSpace() {
        try {
            return timerAction.getFreeSpace();
        } catch (SecurityException e) {
            logger.log(Level.WARNING, "Error checking for free disk space on ingest data drive", e); //NON-NLS
            return DISK_FREE_SPACE_UNKNOWN;
        }
    }

    /**
     * An action that is called every time the ingest monitor's timer expires.
     * It does the actual monitoring.
     */
    private class MonitorTimerAction implements ActionListener {

        private final static long MIN_FREE_DISK_SPACE = 100L * 1024 * 1024; // 100MB
        private File root;

        MonitorTimerAction() {
            findRootDirectoryForCurrentCase();
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
                if (evt instanceof AutopsyEvent) {
                    AutopsyEvent event = (AutopsyEvent) evt;
                    if (AutopsyEvent.SourceType.LOCAL == event.getSourceType() && event.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                        /*
                         * The new value of the event will be non-null if a new
                         * case has been opened.
                         */
                        if (null != evt.getNewValue()) {
                            findRootDirectoryForCurrentCase((Case) evt.getNewValue());
                        }
                    }
                }
            });
        }

        /**
         * Determines the root directory of the case folder for the current case
         * and sets it as the directory to monitor.
         */
        private void findRootDirectoryForCurrentCase() {
            try {
                Case currentCase = Case.getOpenCase();
                findRootDirectoryForCurrentCase(currentCase);
            } catch (NoCurrentCaseException unused) {
                /*
                 * Case.getOpenCase() throws NoCurrentCaseException when there
                 * is no case.
                 */
                root = new File(File.separator);
                logMonitoredRootDirectory();
            }
        }

        /**
         * Determines the root directory of the case folder for the current case
         * and sets it as the directory to monitor.
         *
         * @param currentCase The current case.
         */
        private void findRootDirectoryForCurrentCase(Case currentCase) {
            File curDir = new File(currentCase.getCaseDirectory());
            File parentDir = curDir.getParentFile();
            while (null != parentDir) {
                curDir = parentDir;
                parentDir = curDir.getParentFile();
            }
            root = curDir;
            logMonitoredRootDirectory();
        }

        /**
         * Writes an info message to the Autopsy log identifying the root
         * directory being monitored.
         */
        private void logMonitoredRootDirectory() {
            logger.log(Level.INFO, "Monitoring disk space of {0}", root.getAbsolutePath()); //NON-NLS
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            /*
             * Skip monitoring if ingest is not running.
             */
            final IngestManager manager = IngestManager.getInstance();
            if (manager.isIngestRunning() == false) {
                return;
            }

            logMemoryUsage();
            if (!enoughDiskSpace()) {
                /*
                 * Shut down ingest by cancelling all ingest jobs.
                 */
                manager.cancelAllIngestJobs(IngestJob.CancellationReason.OUT_OF_DISK_SPACE);
                String diskPath = root.getAbsolutePath();
                IngestServices.getInstance().postMessage(IngestMessage.createManagerErrorMessage(
                        NbBundle.getMessage(this.getClass(), "IngestMonitor.mgrErrMsg.lowDiskSpace.title", diskPath),
                        NbBundle.getMessage(this.getClass(), "IngestMonitor.mgrErrMsg.lowDiskSpace.msg", diskPath)));
                MONITOR_LOGGER.log(Level.SEVERE, "Stopping ingest due to low disk space on {0}", diskPath); //NON-NLS
                logger.log(Level.SEVERE, "Stopping ingest due to low disk space on {0}", diskPath); //NON-NLS
            }
        }

        /**
         * Writes current message usage to the memory usage log.
         */
        private void logMemoryUsage() {
            MONITOR_LOGGER.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
        }
        
        /**
         * Writes current disk space usage of the drive where case dir resides to log.
         */        
        private void logDiskSpaceUsage() {
            final long freeSpace = root.getFreeSpace();
            logger.log(Level.INFO, "Available disk space on drive where case dir resides is {0} (bytes)", freeSpace);  //NON-NLS
        }

        /**
         * Determines whether there is enough disk space to continue running
         * ingest.
         *
         * @return true if OK, false otherwise
         */
        private boolean enoughDiskSpace() {
            long freeSpace;
            try {
                freeSpace = getFreeSpace();
            } catch (SecurityException e) {
                logger.log(Level.WARNING, "Unable to check for free disk space (permission issue)", e); //NON-NLS
                return true; //OK
            }

            if (freeSpace == DISK_FREE_SPACE_UNKNOWN) {
                return true;
            } else {
                return freeSpace > MIN_FREE_DISK_SPACE;
            }
        }

        /**
         * Get free space in bytes of the drive where case dir resides, or -1 if
         * unknown
         *
         * @return free space in bytes
         */
        private long getFreeSpace() throws SecurityException {
            // always return "UNKNOWN", see note below
            return DISK_FREE_SPACE_UNKNOWN;
            
            /* NOTE: use and accuracy of this code for network drives needs to be investigated and validated
            final long freeSpace = root.getFreeSpace();
            if (0 == freeSpace) {
                // Check for a network drive, some network filesystems always
                // return zero.
                final String monitoredPath = root.getAbsolutePath();
                if (monitoredPath.startsWith("\\\\") || monitoredPath.startsWith("//")) {
                    return DISK_FREE_SPACE_UNKNOWN;
                }
            }
            return freeSpace;*/
        }
    }

}
