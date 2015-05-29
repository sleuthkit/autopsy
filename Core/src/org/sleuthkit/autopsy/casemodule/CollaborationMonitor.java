/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.netbeans.api.progress.ProgressHandle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * A monitor needs to listen to local event messages so that it can create and
 * delete (possibly even update) a list of current tasks. Each task will have a
 * unique task id and a task description string. Periodically, the monitor
 * broadcasts its current tasks.
 *
 * When a collaborating monitor gets a tasks message from another monitor, it
 * must start or update a tasks list for the host. Inspecting the tasks in the
 * message, it must do the following:
 *
 * - If there is a new task, start a progress bar.
 *
 * - If there is a task that already is in progress, update the progress bar.
 *
 * - If a task is no longer in the tasks lists finish, the progress bar.
 *
 * Each time a tasks message is received, the time of the message is recorded.
 * If a certain interval without a message occurs, the tasks for that host are
 * flushed.
 */
/**
 * RJCTODO
 */
final class CollaborationMonitor {

    private static final String EVENT_CHANNEL_NAME = "%s-Collaboration-Monitor-Events";
    private static final String COLLABORATION_EVENT = "COLLABORATION_MONITOR_EVENT";
    private static final String HEARTBEAT_THREAD_NAME = "collab-monitor-heartbeat-%d";
    private static final long HEARTBEAT_INTERVAL_SECS = 1;
    private static final String CRASH_DETECTION_THREAD_NAME = "collab-monitor-crash-detector-%d";
    private static final long CRASH_DETECTION_INTERVAL_SECS = 60;
    private static final long EXECUTOR_TERMINATION_WAIT_SECS = 30;
    private static final Logger logger = Logger.getLogger(CollaborationMonitor.class.getName());
    private final String hostName;
    private final MonitorEventListener eventListener;
    private final AutopsyEventPublisher eventPublisher;
    private final ScheduledThreadPoolExecutor heartbeatExecutor;
    private final ScheduledThreadPoolExecutor crashDetectionExecutor;
    private Map<String, Task> dataSourceAddTasks;
    Object dataSourceAddTasksLock;
    private Map<Long, Task> dataSourceIngestTasks;
    Object dataSourceIngestTasksLock;
    private Map<String, List<Task>> collaboratorTasks; 
    Object collaboratorTasksLock;
    private CaseEventListener caseEventListener;
    private IngestJobEventListener ingestEventListener;

    /**
     * RJCTODO
     */
    CollaborationMonitor() throws CollaborationMonitorException {
        hostName = getLocalHostName();
        eventListener = new MonitorEventListener();
        eventPublisher = new AutopsyEventPublisher();
        eventPublisher.addSubscriber(COLLABORATION_EVENT, eventListener);
        try {
            eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, Case.getCurrentCase().getName()));
        } catch (AutopsyEventException ex) {
            // RJCTODO:
            throw new CollaborationMonitorException("Failed to initialize", ex);
        }
        heartbeatExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(HEARTBEAT_THREAD_NAME).build());
        heartbeatExecutor.scheduleAtFixedRate(new HeartbeatTask(), HEARTBEAT_INTERVAL_SECS, HEARTBEAT_INTERVAL_SECS, TimeUnit.SECONDS);
        crashDetectionExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(CRASH_DETECTION_THREAD_NAME).build());
        heartbeatExecutor.scheduleAtFixedRate(new CrashDetectionTask(), CRASH_DETECTION_INTERVAL_SECS, CRASH_DETECTION_INTERVAL_SECS, TimeUnit.SECONDS);
        IngestManager.getInstance().addIngestJobEventListener(ingestEventListener);
        Case.addEventSubscriber("", caseEventListener); // RJCTODO
    }

    /**
     * RJCTODO
     */
    void stop() {
        stopExecutor(crashDetectionExecutor, CRASH_DETECTION_THREAD_NAME);
        stopExecutor(heartbeatExecutor, HEARTBEAT_THREAD_NAME);
        eventPublisher.removeSubscriber(COLLABORATION_EVENT, eventListener);
        eventPublisher.closeRemoteEventChannel();
    }

    private void updateDataSourceTasks(AutopsyEvent event) {
        
    }
    
    
    /**
     * RJCTODO
     * @param event 
     */
    private void updateCollaboratorTasks(CollaborationEvent event) {
        /**
         * RJCTODO
         */
    }
    
    /**
     * RJCTODO
     */
    private static void stopExecutor(ScheduledThreadPoolExecutor executor, String name) {
        if (null != executor) {
            executor.shutdownNow();
            try {
                while (!executor.awaitTermination(EXECUTOR_TERMINATION_WAIT_SECS, TimeUnit.SECONDS)) {
                    logger.log(Level.WARNING, "Waited at least thirty seconds for {0} executor to shut down, continuing to wait", name); //NON-NLS
                }
            } catch (InterruptedException ex) {
                // RJCTODO:
            }
        }
    }

    /**
     * RJCTODO
     *
     * @return
     */
    private static String getLocalHostName() {
        String hostName;
        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            // RJCTODO: Log info message
            hostName = System.getenv("COMPUTERNAME");
        }
        return hostName;
    }

    private static final class Task implements Serializable {

        private static final long serialVersionUID = 1L;
        long id;
        String status;
    }

    /**
     * RJCTODO
     */
    private final class CaseEventListener implements PropertyChangeListener {

        /**
         * RJCTODO
         *
         * @param evt
         */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            /**
             * RJCTODO: When local ADDING_DATA_SOURCE (new) event is received,
             * create a new task. When local DATA_SOURCE_ADDED event is
             * received, delete the task. The new ADDING_DATA_SOURCE events can
             * have full image paths to match up with the Content objects on the
             * DATA_SOURCE_ADDED events.
             */
        }

    }

    /**
     * RJCTODO
     */
    private final class IngestJobEventListener implements PropertyChangeListener {

        /**
         * RJCTODO
         *
         * @param evt
         */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            /**
             * RJCTODO: When local DATA_SOURCE_INGEST_STARTED event (new) is
             * received, create new data source analysis tasks. When local
             * DATA_SOURCE_INGEST_COMPLETED (new) or
             * DATA_SOURCE_INGEST_CANCELLED (new) is received, delete the task.
             * These new events can have data source ingest job ids and data
             * source names.
             */
        }

    }

    /**
     * RJCTODO
     */
    private final class MonitorEventListener implements PropertyChangeListener {

        /**
         * RJCTODO
         *
         * @param evt
         */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            /**
             * RJCTODO: Update tasks lists, progress bars
             */
        }

    }

    /**
     * RJCTODO
     */
    private final class HeartbeatTask implements Runnable {

        /**
         * RJCTODO
         */
        @Override
        public void run() {
//            eventPublisher.publish(new CollaborationEvent(hostName, EventType.HEARTBEAT));
        }
    }

    /**
     * RJCTODO
     */
    private final class CrashDetectionTask implements Runnable {

        /**
         * RJCTODO
         */
        @Override
        public void run() {
            // RJCTODO: Check for crashed collaborators and services
        }
    }

    /**
     * RJCTODO
     */
    private static final class CollaborationEvent extends AutopsyEvent implements Serializable {

        private static final long serialVersionUID = 1L;
        List<Task> currentTasks;

        /**
         * RJCTODO
         *
         */
        CollaborationEvent(String hostName) {
            super(COLLABORATION_EVENT, hostName, null);
        }
    }

    static final class CollaborationMonitorException extends Exception {

        CollaborationMonitorException(String message) {
            super(message);
        }

        CollaborationMonitorException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

}
