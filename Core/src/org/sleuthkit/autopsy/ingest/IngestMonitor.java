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
package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.Timer;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Monitor health of the system and stop ingest if necessary
 */
 public class IngestMonitor {

    private static final int INITIAL_INTERVAL_MS = 60000; //1 min.
    private final Logger logger = Logger.getLogger(IngestMonitor.class.getName());
    private Timer timer;
    private static final java.util.logging.Logger MONITOR_LOGGER = java.util.logging.Logger.getLogger("monitor");
    private MonitorAction monitor;
    public static final int DISK_FREE_SPACE_UNKNOWN = -1;

    IngestMonitor() {

        //setup the custom memory logger
        try {
            final int MAX_LOG_FILES = 3;
            FileHandler monitorLogHandler = new FileHandler(PlatformUtil.getUserDirectory().getAbsolutePath() + "/var/log/monitor.log",
                    0, MAX_LOG_FILES);
            monitorLogHandler.setFormatter(new SimpleFormatter());
            monitorLogHandler.setEncoding(PlatformUtil.getLogFileEncoding());
            MONITOR_LOGGER.addHandler(monitorLogHandler);
            //do not forward to the parent autopsy logger
            MONITOR_LOGGER.setUseParentHandlers(false);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } catch (SecurityException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    /**
     * Start the monitor
     */
    void start() {
        monitor = new MonitorAction();
        timer = new Timer(INITIAL_INTERVAL_MS, monitor);
        timer.start();
    }

    /**
     * Stop the monitor
     */
    void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    /**
     * Check if the monitor is running
     *
     * @return true if the monitor is running, false otherwise
     */
    boolean isRunning() {
        return timer != null && timer.isRunning();
    }

    /**
     * Get free space in bytes of the drive where case dir resides
     *
     * @return free space in bytes or -1 if could not be determined.
     */
    long getFreeSpace() {
        try {
            return monitor.getFreeSpace();
        } catch (SecurityException e) {
            logger.log(Level.WARNING, "Error checking for free disk space on ingest data drive", e);
            return DISK_FREE_SPACE_UNKNOWN;
        }
    }

    //TODO add support to monitor multiple drives, e.g. user dir drive in addition to Case drive
    private class MonitorAction implements ActionListener {

        private final static long MIN_FREE_DISK_SPACE = 100L * 1024 * 1024; //100MB
        private File root = new File(File.separator); //default, root dir where autopsy runs

        MonitorAction() {
            //find drive where case is located
            setMonitorDir();

            //update monitor dir if case changed
            Case.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    String changed = evt.getPropertyName();
                    Object newValue = evt.getNewValue();

                    if (changed.equals(Case.Events.CURRENT_CASE.toString())) {
                        if (newValue != null) {
                            setMonitorDir();
                        }

                    }


                }
            });

        }

        private void setMonitorDir() {
            String caseDir = Case.getCurrentCase().getCaseDirectory();
            File curDir = new File(caseDir);
            File tempF = null;
            while ((tempF = curDir.getParentFile()) != null) {
                curDir = tempF;
            }
            root = curDir;
            logger.log(Level.INFO, "Monitoring disk space of case root: " + curDir.getAbsolutePath());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final IngestManager manager = IngestManager.getDefault();

            //runs checks only if ingest is running
            if (manager.isIngestRunning() == false) {
                return;
            }

            monitorMemory();

            if (checkDiskSpace() == false) {
                //stop ingest if running
                final String diskPath = root.getAbsolutePath();
                MONITOR_LOGGER.log(Level.SEVERE, "Stopping ingest due to low disk space on disk " + diskPath);
                logger.log(Level.SEVERE, "Stopping ingest due to low disk space on disk " + diskPath);
                manager.stopAll();
                manager.postMessage(IngestMessage.createManagerErrorMessage("Ingest stopped - low disk space on " + diskPath,
                        "Stopping ingest due to low disk space on disk " + diskPath
                        + ". \nEnsure the Case drive has at least 1GB free space and restart ingest."));
            }
        }

        /**
         * Get free space in bytes of the drive where case dir resides, or -1 if
         * unknown
         *
         * @return free space in bytes
         */
        private long getFreeSpace() throws SecurityException {
            final long freeSpace = root.getFreeSpace();

            if (freeSpace == 0) {
                //check if network drive, some network filesystems always return 0
                final String monitoredPath = root.getAbsolutePath();
                if (monitoredPath.startsWith("\\\\") || monitoredPath.startsWith("//")) {
                    return DISK_FREE_SPACE_UNKNOWN;

                }
            }

            return freeSpace;

        }

        /**
         * check disk space and see if enough to process/continue ingest
         *
         * @return true if OK, false otherwise
         */
        private boolean checkDiskSpace() {
            long freeSpace;
            try {
                freeSpace = getFreeSpace();
            } catch (SecurityException e) {
                logger.log(Level.WARNING, "Unable to check for free disk space (permission issue)", e);
                return true; //OK
            }

            if (freeSpace == DISK_FREE_SPACE_UNKNOWN) {
                return true;
            } else {
                //logger.log(Level.INFO, "Checking free disk apce: " + freeSpace + " need: " + Long.toString(MIN_FREE_DISK_SPACE));
                return freeSpace > MIN_FREE_DISK_SPACE;
            }
        }

        /**
         * Monitor memory usage and print to memory log
         */
        private void monitorMemory() {
            MONITOR_LOGGER.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
        }
    }
}
