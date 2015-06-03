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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.casemodule.events.AddingDataSourceEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

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
    private static final String COLLABORATION_MONITOR_EVENT = "COLLABORATION_MONITOR_EVENT";
    private static final String HEARTBEAT_THREAD_NAME = "collab-monitor-heartbeat-%d";
    private static final long MINUTES_BETWEEN_HEARTBEATS = 1;
    private static final String CRASH_DETECTION_THREAD_NAME = "collab-monitor-crash-detector-%d";
    private static final long CRASH_DETECTION_INTERVAL_SECS = 60;
    private static final long EXECUTOR_TERMINATION_WAIT_SECS = 30;
    private static final Logger logger = Logger.getLogger(CollaborationMonitor.class.getName());
    private final String hostName;
    private final LocalTasksManager localTasksManager;
    private final RemoteTasksManager remoteTasksManager;
    private final AutopsyEventPublisher eventPublisher;
    private final ScheduledThreadPoolExecutor heartbeatExecutor;
    private final ScheduledThreadPoolExecutor crashDetectionExecutor;

    /**
     * RJCTODO
     */
    CollaborationMonitor() throws CollaborationMonitorException {
        hostName = getLocalHostName();
        remoteTasksManager = new RemoteTasksManager();
        eventPublisher = new AutopsyEventPublisher();
        eventPublisher.addSubscriber(COLLABORATION_MONITOR_EVENT, remoteTasksManager);
        try {
            eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, Case.getCurrentCase().getName()));
        } catch (AutopsyEventException ex) {
            // RJCTODO:
            throw new CollaborationMonitorException("Failed to initialize", ex);
        }

        heartbeatExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(HEARTBEAT_THREAD_NAME).build());
        heartbeatExecutor.scheduleAtFixedRate(new HeartbeatTask(), MINUTES_BETWEEN_HEARTBEATS, MINUTES_BETWEEN_HEARTBEATS, TimeUnit.MINUTES);
        crashDetectionExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(CRASH_DETECTION_THREAD_NAME).build());
        heartbeatExecutor.scheduleAtFixedRate(new CrashDetectionTask(), CRASH_DETECTION_INTERVAL_SECS, CRASH_DETECTION_INTERVAL_SECS, TimeUnit.SECONDS);

        localTasksManager = new LocalTasksManager();
        IngestManager.getInstance().addIngestJobEventListener(localTasksManager);
        Case.addEventSubscriber("", localTasksManager); // RJCTODO: Fill in events of interest                        
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

    /**
     *
     */
    private void subscribeToRemoteEvents() {

    }

    /**
     *
     */
    private void subscribeToLocalEvents() {
    }

    /**
     * RJCTODO
     */
    void stop() {
        stopExecutor(crashDetectionExecutor, CRASH_DETECTION_THREAD_NAME);
        stopExecutor(heartbeatExecutor, HEARTBEAT_THREAD_NAME);
        eventPublisher.removeSubscriber(COLLABORATION_MONITOR_EVENT, remoteTasksManager);
        eventPublisher.closeRemoteEventChannel();
    }

    private void updateDataSourceTasks(AutopsyEvent event) {

    }

    /**
     * RJCTODO
     *
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
                    logger.log(Level.WARNING, "Waited at least thirty seconds for {0} executor to shut down, continuing to wait", name); //NON-NLS RJCTODO
                }
            } catch (InterruptedException ex) {
                // RJCTODO:
            }
        }
    }

    /**
     * The local tasks manager listens to local events and tracks them as
     * collaboration tasks that can be broadcast to collaborating nodes.
     */
    private final class LocalTasksManager implements PropertyChangeListener {

        private final AtomicLong nextTaskId;
        private final Map<Integer, Task> uuidsToAddDataSourceTasks;
        private final Map<Long, Task> jobIdsTodataSourceAnalysisTasks;

        /**
         * Constructs a local tasks manager that listens to local events and
         * tracks them as collaboration tasks that can be broadcast to
         * collaborating nodes.
         */
        LocalTasksManager() {
            nextTaskId = new AtomicLong(0L);
            uuidsToAddDataSourceTasks = new HashMap<>();
            jobIdsTodataSourceAnalysisTasks = new HashMap<>();
        }

        /**
         * Updates the collection of tasks this node is performing in response
         * to receiving an event in the form of a property change.
         *
         * @param evt A PropertyChangeEvent.
         */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventName = evt.getPropertyName();
            if (eventName.equals(Case.Events.ADDING_DATA_SOURCE.toString())) {
                addDataSourceAddTask(evt);
            } else if (eventName.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                addDataSourceAddTask(evt);
            } else if (eventName.equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_STARTED.toString())) {
                addDataSourceAnalysisTask(evt);
            } else if (eventName.equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED.toString())) {
                removeDataSourceAnalysisTask(evt);
            } else if (eventName.equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_CANCELLED.toString())) {
                removeDataSourceAnalysisTask(evt);
            }
        }

        /**
         * Add a task for adding the data source and publishes the updated local
         * tasks list to any collaborating nodes.
         *
         * @param evt RJCTODO
         */
        synchronized void addDataSourceAddTask(PropertyChangeEvent evt) {
            AddingDataSourceEvent event = (AddingDataSourceEvent) evt;
            String status = String.format("%s adding data source", hostName); // RJCTODO: Bundle
            uuidsToAddDataSourceTasks.put(event.getDataSourceId().hashCode(), new Task(nextTaskId.getAndIncrement(), status));
            eventPublisher.publish(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Removes the task for adding the data source and publishes the updated
         * local tasks list to any collaborating nodes.
         *
         * @param evt RJCTODO
         */
        synchronized void removeDataSourceAddTask(PropertyChangeEvent evt) {
            DataSourceAddedEvent event = (DataSourceAddedEvent) evt;
            uuidsToAddDataSourceTasks.remove(event.getDataSourceId().hashCode());
            eventPublisher.publish(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * RJCTODO
         *
         * @param evt
         */
        synchronized void addDataSourceAnalysisTask(PropertyChangeEvent evt) {
            eventPublisher.publish(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * RJCTODO
         *
         * @param evt
         */
        synchronized void removeDataSourceAnalysisTask(PropertyChangeEvent evt) {
            eventPublisher.publish(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Gets the current local tasks.
         *
         * @return A mapping of task IDs to tasks
         */
        synchronized Map<Long, Task> getCurrentTasks() {
            Map<Long, Task> currentTasks = new HashMap<>();
            uuidsToAddDataSourceTasks.values().stream().forEach((task) -> {
                currentTasks.put(task.getId(), task);
            });
            jobIdsTodataSourceAnalysisTasks.values().stream().forEach((task) -> {
                currentTasks.put(task.getId(), task);
            });
            return currentTasks;
        }
    }

    /**
     * RJCTODO
     */
    private final class RemoteTasksManager implements PropertyChangeListener {

        private Map<String, RemoteTasks> hostToTasks;

        /**
         * RJCTODO
         *
         * @param event
         */
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (event.getPropertyName().equals(COLLABORATION_MONITOR_EVENT)) {
                updateTasks((CollaborationEvent) event);
            }
        }

        synchronized void updateTasks(CollaborationEvent event) {
            RemoteTasks tasks = hostToTasks.get(event.getHostName());
            if (null != tasks) {
                // Update time stamp
            } else {
                hostToTasks.put(event.getHostName(), new RemoteTasks(event)); // Pass in task list
            }
            // Lookup the RemoteTasks for this host.
            // If found
            //      update the time stamp
            //      for each key in task keyset
            //          if task id not in current list
            //              finish progress, discard task
            //          else
            //              update progress bar with new text
            //              update time stamp
            //          endif

            // if none found
            //      add to task map for host
            // else
        }

        /**
         * A collection of progress bars for tasks on a collaborating node,
         * obtained from a collaboration event and bundled with the time of the
         * last update.
         */
        class RemoteTasks {

            private final Duration MAX_MINUTES_WITHOUT_UPDATE = Duration.ofSeconds(MINUTES_BETWEEN_HEARTBEATS * 5);
            private Instant lastUpdateTime;
            private Map<Long, ProgressHandle> taskIdsToProgressBars;

            /**
             * RJCTODO
             *
             * @param event
             */
            RemoteTasks(CollaborationEvent event) {
                lastUpdateTime = Instant.now();
                event.getCurrentTasks().values().stream().forEach((task) -> {
                    ProgressHandle progress = ProgressHandleFactory.createHandle(event.getHostName());
                    progress.start();
                    progress.progress(task.getStatus());
                    taskIdsToProgressBars.put(task.getId(), progress);
                });
            }

            /**
             * Updates this remotes tasks collection.
             *
             * @param event A collaboration event from the collaborating node
             * associated with these tasks.
             */
            void update(CollaborationEvent event) {
                /**
                 * Update the timestamp.
                 */
                lastUpdateTime = Instant.now();

                Map<Long, Task> currentTasks = event.getCurrentTasks();

                /**
                 * Create or update the progress bars for the current tasks of
                 * the node that published the event.
                 */
                currentTasks.values().stream().forEach((task) -> {
                    ProgressHandle progress = taskIdsToProgressBars.get(task.getId());
                    if (null != progress) {
                        progress.progress(task.getStatus());
                    } else {
                        progress = ProgressHandleFactory.createHandle(event.getHostName());
                        progress.start();
                        progress.progress(task.getStatus());
                        taskIdsToProgressBars.put(task.getId(), progress);
                    }
                });

                /**
                 * If a task is no longer in the task list from the remote node,
                 * it is finished. Remove the progress bars for finished tasks.
                 */
                for (Long id : taskIdsToProgressBars.keySet()) {
                    if (!currentTasks.containsKey(id)) {
                        ProgressHandle progress = taskIdsToProgressBars.remove(id);
                        progress.finish();
                    }
                }
            }

            /**
             * Determines whether or not the time since the last update of this
             * remote tasks collection is greater than the maximum acceptable
             * interval between updates.
             *
             * @return True or false.
             */
            boolean isStale() {
                return MAX_MINUTES_WITHOUT_UPDATE.compareTo(Duration.between(lastUpdateTime, Instant.now())) >= 0;
            }
        }

    }

    /**
     * A Runnable task that periodically publishes the local tasks in progress
     * for the current case on this node, providing a heartbeat message for
     * other nodes. The current local tasks are included in the heartbeat
     * message so that nodes that have just joined the event channel know what
     * this node is doing when they join the collaboration on the current case.
     */
    private final class HeartbeatTask implements Runnable {

        /**
         * Publish a heartbeat message.
         */
        @Override
        public void run() {
            eventPublisher.publish(new CollaborationEvent(hostName, localTasksManager.getCurrentTasks()));
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
        private final String hostName;
        private final Map<Long, Task> currentTasks;

        /**
         * RJCTODO
         *
         * @param hostName
         * @param currentTasks
         */
        CollaborationEvent(String hostName, Map<Long, Task> currentTasks) {
            super(COLLABORATION_MONITOR_EVENT, null, null);
            this.hostName = hostName;
            this.currentTasks = currentTasks;
        }

        /**
         * RJCTODO
         *
         * @return
         */
        String getHostName() {
            return hostName;
        }

        /**
         * RJCTODO
         *
         * @return
         */
        Map<Long, Task> getCurrentTasks() {
            return currentTasks;
        }

    }

    /**
     * RJCTODO
     */
    private static final class Task implements Serializable {

        private static final long serialVersionUID = 1L;
        private final long id;
        private final String status;

        Task(long id, String status) {
            this.id = id;
            this.status = status;
        }

        long getId() {
            return id;
        }

        String getStatus() {
            return status;
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
