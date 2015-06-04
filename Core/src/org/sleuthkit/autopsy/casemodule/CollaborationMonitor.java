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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisCompletedEvent;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisStartedEvent;

/**
 * Listens to local events and tracks them as collaboration tasks that can be
 * broadcast to collaborating nodes, receives event messages from collaborating
 * nodes and translates them into case activity indicators for the user, and
 * monitors the health of the services needed for collaboration so that users
 * can be informed if those service become unavailable.
 */
// TODO: This class probably has too many responsibilities!
final class CollaborationMonitor {

    private static final String EVENT_CHANNEL_NAME = "%s-Collaboration-Monitor-Events";
    private static final String COLLABORATION_MONITOR_EVENT = "COLLABORATION_MONITOR_EVENT";
    private static final Set<String> CASE_EVENTS_OF_INTEREST = new HashSet<>(Arrays.asList(new String[]{Case.Events.ADDING_DATA_SOURCE.toString(), Case.Events.DATA_SOURCE_ADDED.toString()}));
    private static final String HEARTBEAT_THREAD_NAME = "collab-monitor-heartbeat-%d";
    private static final long MINUTES_BETWEEN_HEARTBEATS = 1;
    private static final String STALE_TASKS_DETECTION_THREAD_NAME = "collab-monitor-stale-remote-tasks-detector-%d";
    private static final long STALE_TASKS_DETECTION_INTERVAL_SECS = 60;
    private static final String CRASH_DETECTION_THREAD_NAME = "collab-monitor-crash-detector-%d";
    private static final long CRASH_DETECTION_INTERVAL_SECS = 60;
    private static final long EXECUTOR_TERMINATION_WAIT_SECS = 30;
    private static final Logger logger = Logger.getLogger(CollaborationMonitor.class.getName());
    private final String hostName;
    private final LocalTasksManager localTasksManager;
    private final RemoteTasksManager remoteTasksManager;
    private final AutopsyEventPublisher eventPublisher;
    private final ScheduledThreadPoolExecutor heartbeatExecutor;
    private final ScheduledThreadPoolExecutor staleTasksCheckExecutor;
    private final ScheduledThreadPoolExecutor crashDetectionExecutor;

    /**
     * Constructs an object that listens to local events and tracks them as
     * collaboration tasks that can be broadcast to collaborating nodes,
     * receives event messages from collaborating nodes and translates them into
     * case activity indicators for the user, and monitors the health of the
     * services needed for collaboration so that users can be informed if those
     * service become unavailable.
     */
    CollaborationMonitor() throws CollaborationMonitorException {
        hostName = getHostName();

        /**
         * Create an Autopsy event publisher for the current case. This will be
         * used to communicate with collaboration monitors on other Autopsy
         * nodes working on the case.
         */
        eventPublisher = new AutopsyEventPublisher();
        try {
            eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, Case.getCurrentCase().getName()));
        } catch (AutopsyEventException ex) {
            throw new CollaborationMonitorException("Failed to initialize", ex);
        }

        /**
         * Create a remote tasks manager to track and display the progress of
         * tasks other Autopsy nodes as reported by their collaboration
         * monitors.
         */
        remoteTasksManager = new RemoteTasksManager();
        eventPublisher.addSubscriber(COLLABORATION_MONITOR_EVENT, remoteTasksManager);

        /**
         * Create a local tasks manager to keep track of and broadcast the
         * events happening on this Autopsy node to the collaboration monitors
         * on other nodes.
         */
        localTasksManager = new LocalTasksManager();
        IngestManager.getInstance().addIngestJobEventListener(localTasksManager);
        Case.addEventSubscriber(CASE_EVENTS_OF_INTEREST, localTasksManager);

        // RJCTODO: Do we really need three ScheduledThreadPoolExecutors?
        // Perhaps one is enough....
        /**
         * Start a periodic task on its own thread that will send heartbeat
         * messages to the collaboration monitors on other nodes.
         */
        heartbeatExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(HEARTBEAT_THREAD_NAME).build());
        heartbeatExecutor.scheduleAtFixedRate(new HeartbeatTask(), MINUTES_BETWEEN_HEARTBEATS, MINUTES_BETWEEN_HEARTBEATS, TimeUnit.MINUTES);

        /**
         * Start a periodic task on its own thread that will check for stale
         * remote tasks.
         */
        staleTasksCheckExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(STALE_TASKS_DETECTION_THREAD_NAME).build());
        staleTasksCheckExecutor.scheduleAtFixedRate(new StaleTaskDetectionTask(), STALE_TASKS_DETECTION_INTERVAL_SECS, STALE_TASKS_DETECTION_INTERVAL_SECS, TimeUnit.SECONDS);

        /**
         * Start a periodic task on its own thread that will report on the
         * availability of key collaboration services.
         */
        crashDetectionExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(CRASH_DETECTION_THREAD_NAME).build());
        crashDetectionExecutor.scheduleAtFixedRate(new CrashDetectionTask(), CRASH_DETECTION_INTERVAL_SECS, CRASH_DETECTION_INTERVAL_SECS, TimeUnit.SECONDS);
    }

    /**
     * Determines the name of the local host for use in describing local tasks.
     *
     * @return The host name of this Autopsy node.
     */
    private String getHostName() {
        String name;
        try {
            name = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            // RJCTODO: Log info message
            name = System.getenv("COMPUTERNAME");
        }
        return name;
    }

    /**
     * Shuts down this collaboration monitor.
     */
    void stop() {
        stopExecutor(crashDetectionExecutor, CRASH_DETECTION_THREAD_NAME);
        stopExecutor(heartbeatExecutor, HEARTBEAT_THREAD_NAME);
        // RJCTODO: Shut down other stuff
        eventPublisher.removeSubscriber(COLLABORATION_MONITOR_EVENT, remoteTasksManager);
        eventPublisher.closeRemoteEventChannel();
    }

    /**
     * Gracefully shuts down a scheduled thread pool executor.
     */
    private static void stopExecutor(ScheduledThreadPoolExecutor executor, String name) {
        if (null != executor) {
            executor.shutdownNow();
            try {
                while (!executor.awaitTermination(EXECUTOR_TERMINATION_WAIT_SECS, TimeUnit.SECONDS)) {
                    logger.log(Level.WARNING, "Waited at least {0} seconds for {1} executor to shut down, continuing to wait", new Object[]{EXECUTOR_TERMINATION_WAIT_SECS, name}); //NON-NLS
                }
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "Unexpected interrupt while stopping thread executor", ex); //NON-NLS
                // RJCTODO: Reset?
            }
        }
    }

    /**
     * The local tasks manager listens to local events and translates them into
     * tasks it broadcasts to collaborating nodes. Note that all access to the
     * task collections is synchronized since they may be accessed by both the
     * threads publishing property change events and by the heartbeat thread.
     */
    private final class LocalTasksManager implements PropertyChangeListener {

        private long nextTaskId;
        private final Map<Integer, Task> uuidsToAddDataSourceTasks;
        private final Map<Long, Task> jobIdsTodataSourceAnalysisTasks;

        /**
         * Constructs a local tasks manager that listens to local events and
         * translates them into tasks that can be broadcast to collaborating
         * nodes.
         */
        LocalTasksManager() {
            nextTaskId = 0;
            uuidsToAddDataSourceTasks = new HashMap<>();
            jobIdsTodataSourceAnalysisTasks = new HashMap<>();
        }

        /**
         * Updates the collection of tasks this node is performing in response
         * to receiving an event in the form of a property change.
         *
         * @param event A PropertyChangeEvent.
         */
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            String eventName = event.getPropertyName();
            if (eventName.equals(Case.Events.ADDING_DATA_SOURCE.toString())) {
                addDataSourceAddTask((AddingDataSourceEvent) event);
            } else if (eventName.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                removeDataSourceAddTask((DataSourceAddedEvent) event);
            } else if (eventName.equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_STARTED.toString())) {
                addDataSourceAnalysisTask((DataSourceAnalysisStartedEvent) event);
            } else if (eventName.equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED.toString())) {
                removeDataSourceAnalysisTask((DataSourceAnalysisCompletedEvent) event);
            }
        }

        /**
         * Adds a task for tracking adding the data source and publishes the
         * updated local tasks list to any collaborating nodes.
         *
         * @param evt An adding data source event.
         */
        synchronized void addDataSourceAddTask(AddingDataSourceEvent event) {
            String status = String.format("%s adding data source", hostName); // RJCTODO: Bundle
            uuidsToAddDataSourceTasks.put(event.getDataSourceId().hashCode(), new Task(++nextTaskId, status));
            eventPublisher.publish(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Removes the task for adding the data source and publishes the updated
         * local tasks list to any collaborating nodes.
         *
         * @param evt A data source added event
         */
        synchronized void removeDataSourceAddTask(DataSourceAddedEvent event) {
            uuidsToAddDataSourceTasks.remove(event.getDataSourceId().hashCode());
            eventPublisher.publish(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * RJCTODO
         *
         * @param evt
         */
        synchronized void addDataSourceAnalysisTask(DataSourceAnalysisStartedEvent event) {
            String status = String.format("%s analyzing %s", hostName, event.getDataSource().getName()); // RJCTODO: Bundle
            jobIdsTodataSourceAnalysisTasks.put(event.getDataSourceIngestJobId(), new Task(++nextTaskId, status));
            eventPublisher.publish(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * RJCTODO
         *
         * @param evt
         */
        synchronized void removeDataSourceAnalysisTask(DataSourceAnalysisCompletedEvent event) {
            jobIdsTodataSourceAnalysisTasks.remove(event.getDataSourceIngestJobId());
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
     * The remote tasks manager listens to events broadcast by collaboration
     * monitors on other nodes and translates them into remote tasks represented
     * using progress bars.
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

        /**
         * RJCTODO
         *
         * @param event
         */
        synchronized void updateTasks(CollaborationEvent event) {
            // RJCTODO: This is a little hard to understand, consider some renaming
            RemoteTasks tasks = hostToTasks.get(event.getHostName());
            if (null != tasks) {
                tasks.update(event);
            } else {
                hostToTasks.put(event.getHostName(), new RemoteTasks(event));
            }
        }

        /**
         * RJCTODO
         */
        synchronized void finshStaleTasks() {
            for (Iterator<Map.Entry<String, RemoteTasks>> it = hostToTasks.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, RemoteTasks> entry = it.next();
                RemoteTasks tasks = entry.getValue();
                if (tasks.isStale()) {
                    tasks.finishAllTasks();
                    it.remove();
                }
            }
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
             * Updates this remote tasks collection.
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

            void finishAllTasks() {
                taskIdsToProgressBars.values().stream().forEach((progress) -> {
                    progress.finish();
                });
                taskIdsToProgressBars.clear();
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
     * on this node, providing a heartbeat message for collaboration monitors on
     * other nodes. The current local tasks are included in the heartbeat
     * message so that nodes that have just joined the event channel know what
     * this node is doing, even if they join after the current tasks are begun.
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
    private final class StaleTaskDetectionTask implements Runnable {

        /**
         * RJCTODO
         */
        @Override
        public void run() {
            remoteTasksManager.finshStaleTasks();
        }
    }

    /**
     * A Runnable task that periodically checks the availability of
     * collaboration resources (PostgreSQL server, Solr server, Active MQ
     * message broker) and reports status to the user in case of a gap in
     * service.
     */
    private final class CrashDetectionTask implements Runnable {

        /**
         * Monitor the availability of collaboration resources
         */
        @Override
        public void run() {
            // RJCTODO: Implement this.
        }
    }

    /**
     * An Autopsy event to be sent in event messages to the collaboration
     * monitors on other Autopsy nodes.
     */
    private static final class CollaborationEvent extends AutopsyEvent implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String hostName;
        private final Map<Long, Task> currentTasks;

        /**
         * Constructs an Autopsy event to be sent in event messages to the
         * collaboration monitors on other Autopsy nodes.
         *
         * @param hostName The name of the host sending the event.
         * @param currentTasks The tasks in progress for this Autopsy node.
         */
        CollaborationEvent(String hostName, Map<Long, Task> currentTasks) {
            super(COLLABORATION_MONITOR_EVENT, null, null);
            this.hostName = hostName;
            this.currentTasks = currentTasks;
        }

        /**
         * Gets the host name of the Autopsy node that published this event.
         *
         * @return The host name.
         */
        String getHostName() {
            return hostName;
        }

        /**
         * Gets the current tasks for the Autopsy node that published this
         * event.
         *
         * @return A mapping of task IDs to current tasks
         */
        Map<Long, Task> getCurrentTasks() {
            return currentTasks;
        }

    }

    /**
     * A representation of a task in progress on this Autopsy node.
     */
    private static final class Task implements Serializable {

        private static final long serialVersionUID = 1L;
        private final long id;
        private final String status;

        /**
         * Constructs a representation of a task in progress on this Autopsy
         * node.
         *
         * @param id
         * @param status
         */
        Task(long id, String status) {
            this.id = id;
            this.status = status;
        }

        /**
         * Gets ID of this task.
         *
         * @return A task id, unique to this task for this case and this Autopsy
         * node.
         */
        long getId() {
            return id;
        }

        /**
         * Gets the status of the task at the time this object was constructed.
         *
         * @return A task status string.
         */
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
