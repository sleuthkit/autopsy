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
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
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
    private final MemoryMXBean memoryManager = ManagementFactory.getMemoryMXBean();

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
        timer = new Timer(INITIAL_INTERVAL_MS, new MonitorAction());
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

    //TODO add support to monitor multiple drives, e.g. user dir drive in addition to Case drive
    private class MonitorAction implements ActionListener {

        private final static long MIN_FREE_DISK_SPACE = 100L * 1024 * 1024; //100MB
        private File root = new File(File.separator); //default, roto dir where autopsy runs

        MonitorAction() {
            //find drive where case is located
            setMonitorDir();

            //update monitor dir if case changed
            Case.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    String changed = evt.getPropertyName();
                    Object newValue = evt.getNewValue();

                    if (changed.equals(Case.CASE_CURRENT_CASE)) {
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
         * check disk space
         *
         * @return true if OK, false otherwise
         */
        private boolean checkDiskSpace() {
            long freeSpace;
            try {
                freeSpace = root.getFreeSpace();
            } catch (SecurityException e) {
                logger.log(Level.WARNING, "Unable to check for free disk space (permission issue)", e);
                return true; //OK
            }
            //logger.log(Level.INFO, "Checking free disk apce: " + freeSpace + " need: " + Long.toString(MIN_FREE_DISK_SPACE));
            return freeSpace > MIN_FREE_DISK_SPACE;
        }

        /**
         * Monitor memory usage and print to memory log
         */
        private void monitorMemory() {

            final Runtime runTime = Runtime.getRuntime();
            final long maxMemory = runTime.maxMemory();
            final long totalMemory = runTime.totalMemory();
            final long freeMemory = runTime.freeMemory();
            MONITOR_LOGGER.log(Level.INFO, "Physical memory (max, total, free): "
                    + Long.toString(maxMemory) + ", " + Long.toString(totalMemory)
                    + ", " + Long.toString(freeMemory));

            final MemoryUsage heap = memoryManager.getHeapMemoryUsage();
            final MemoryUsage nonHeap = memoryManager.getNonHeapMemoryUsage();

            MONITOR_LOGGER.log(Level.INFO, "Java heap memory: " + heap.toString() + ", Java non-heap memory: " + nonHeap.toString());

        }
    }
}
