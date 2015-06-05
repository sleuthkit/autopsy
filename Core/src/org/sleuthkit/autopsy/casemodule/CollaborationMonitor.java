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
 * A collaboration monitor listens to local events and represents them as
 * collaboration tasks that are broadcast to collaborating nodes, informs the
 * user of collaboration tasks on other nodes using progress bars, and monitors
 * the health of the key collaboration services.
 */
final class CollaborationMonitor {

    private static final String EVENT_CHANNEL_NAME = "%s-Collaboration-Monitor-Events";
    private static final String COLLABORATION_MONITOR_EVENT = "COLLABORATION_MONITOR_EVENT";
    private static final Set<String> CASE_EVENTS_OF_INTEREST = new HashSet<>(Arrays.asList(new String[]{Case.Events.ADDING_DATA_SOURCE.toString(), Case.Events.DATA_SOURCE_ADDED.toString()}));
    private static final int NUMBER_OF_PERIODIC_TASK_THREADS = 1;
    private static final String PERIODIC_TASK_THREAD_NAME = "collab-monitor-periodic-tasks-%d";
    private static final long HEARTBEAT_INTERVAL_MINUTES = 1;
    private static final long MAX_MISSED_HEARTBEATS = 5;
    private static final long STALE_TASKS_DETECTION_INTERVAL_MINUTES = 2;
    private static final long CRASH_DETECTION_INTERVAL_MINUTES = 5;
    private static final long EXECUTOR_TERMINATION_WAIT_SECS = 30;
    private static final Logger logger = Logger.getLogger(CollaborationMonitor.class.getName());
    private final String hostName;
    private final LocalTasksManager localTasksManager;
    private final RemoteTasksManager remoteTasksManager;
    private final AutopsyEventPublisher eventPublisher;
    private final ScheduledThreadPoolExecutor periodicTasksExecutor;

    /**
     * Constructs a collaboration monitor that listens to local events and
     * represents them as collaboration tasks that are broadcast to
     * collaborating nodes, informs the user of collaboration tasks on other
     * nodes using progress bars, and monitors the health of the key
     * collaboration services.
     */
    CollaborationMonitor() throws CollaborationMonitorException {
        /**
         * Get the local host name so it can be used to identify the source of
         * collaboration tasks broadcast by this node.
         */
        hostName = getHostName();

        /**
         * Create an event publisher that will be used to communicate with
         * collaboration monitors on other nodes working on the case.
         */
        eventPublisher = new AutopsyEventPublisher();
        try {
            Case openedCase = Case.getCurrentCase();
            String channelPrefix = openedCase.getTextIndexName();
            eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, channelPrefix));
        } catch (AutopsyEventException ex) {
            throw new CollaborationMonitorException("Failed to initialize", ex);
        }

        /**
         * Create a remote tasks manager to track and display the progress of
         * remote tasks.
         */
        remoteTasksManager = new RemoteTasksManager();
        eventPublisher.addSubscriber(COLLABORATION_MONITOR_EVENT, remoteTasksManager);

        /**
         * Create a local tasks manager to track and broadcast local tasks.
         */
        localTasksManager = new LocalTasksManager();
        IngestManager.getInstance().addIngestJobEventListener(localTasksManager);
        Case.addEventSubscriber(CASE_EVENTS_OF_INTEREST, localTasksManager);

        /**
         * Start periodic tasks that:
         *
         * 1. Send heartbeats to collaboration monitors on other nodes.<br>
         * 2. Check for stale remote tasks.<br>
         * 3. Check the availability of key collaboration services.<br>
         */
        periodicTasksExecutor = new ScheduledThreadPoolExecutor(NUMBER_OF_PERIODIC_TASK_THREADS, new ThreadFactoryBuilder().setNameFormat(PERIODIC_TASK_THREAD_NAME).build());
        periodicTasksExecutor.scheduleAtFixedRate(new HeartbeatTask(), HEARTBEAT_INTERVAL_MINUTES, HEARTBEAT_INTERVAL_MINUTES, TimeUnit.MINUTES);
        periodicTasksExecutor.scheduleAtFixedRate(new StaleTaskDetectionTask(), STALE_TASKS_DETECTION_INTERVAL_MINUTES, STALE_TASKS_DETECTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
        periodicTasksExecutor.scheduleAtFixedRate(new CrashDetectionTask(), CRASH_DETECTION_INTERVAL_MINUTES, CRASH_DETECTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
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
        } catch (UnknownHostException notUsed) {
            name = System.getenv("COMPUTERNAME");
        }
        return name;
    }

    /**
     * Shuts down this collaboration monitor.
     */
    void stop() {
        if (null != periodicTasksExecutor) {
            periodicTasksExecutor.shutdownNow();
            try {
                while (!periodicTasksExecutor.awaitTermination(EXECUTOR_TERMINATION_WAIT_SECS, TimeUnit.SECONDS)) {
                    logger.log(Level.WARNING, "Waited at least {0} seconds for periodic tasks executor to shut down, continuing to wait", EXECUTOR_TERMINATION_WAIT_SECS); //NON-NLS
                }
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "Unexpected interrupt while stopping periodic tasks executor", ex); //NON-NLS
            }
        }

        if (null != eventPublisher) {
            eventPublisher.removeSubscriber(COLLABORATION_MONITOR_EVENT, remoteTasksManager);
            eventPublisher.closeRemoteEventChannel();
        }

        // RJCTODO: Shut down other stuff?
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
         * Translates events into updates of the collection of local tasks this
         * node is broadcasting to other nodes.
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
         * Adds an adding data source task to the collection of local tasks and
         * publishes the updated collection to any collaborating nodes.
         *
         * @param event An adding data source event.
         */
        synchronized void addDataSourceAddTask(AddingDataSourceEvent event) {
            String status = String.format("%s adding data source", hostName); // RJCTODO: Bundle
            uuidsToAddDataSourceTasks.put(event.getDataSourceId().hashCode(), new Task(++nextTaskId, status));
            eventPublisher.publishRemotely(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Removes an adding data source task from the collection of local tasks
         * and publishes the updated collection to any collaborating nodes.
         *
         * @param event A data source added event
         */
        synchronized void removeDataSourceAddTask(DataSourceAddedEvent event) {
            uuidsToAddDataSourceTasks.remove(event.getDataSourceId().hashCode());
            eventPublisher.publishRemotely(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Adds a data source analysis task to the collection of local tasks and
         * publishes the updated collection to any collaborating nodes.
         *
         * @param event A data source analysis started event.
         */
        synchronized void addDataSourceAnalysisTask(DataSourceAnalysisStartedEvent event) {
            String status = String.format("%s analyzing %s", hostName, event.getDataSource().getName()); // RJCTODO: Bundle
            jobIdsTodataSourceAnalysisTasks.put(event.getDataSourceIngestJobId(), new Task(++nextTaskId, status));
            eventPublisher.publishRemotely(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Removes a data source analysis task from the collection of local
         * tasks and publishes the updated collection to any collaborating
         * nodes.
         *
         * @param event A data source analysis completed event.
         */
        synchronized void removeDataSourceAnalysisTask(DataSourceAnalysisCompletedEvent event) {
            jobIdsTodataSourceAnalysisTasks.remove(event.getDataSourceIngestJobId());
            eventPublisher.publishRemotely(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Gets the current local tasks.
         *
         * @return A mapping of task IDs to tasks, may be empty.
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
     * Listens for collaboration event messages broadcast by collaboration
     * monitors on other nodes and translates them into remote tasks represented
     * locally using progress bars. Note that all access to the remote tasks is
     * synchronized since it may be accessed by a JMS thread running code in the
     * Autopsy event publisher and by a thread running periodic checks for
     * "stale" tasks.
     */
    private final class RemoteTasksManager implements PropertyChangeListener {

        private final Map<String, RemoteTasks> hostsToTasks;

        /**
         * Constructs an object that listens for collaboration event messages
         * broadcast by collaboration monitors on other nodes and translates
         * them into remote tasks represented locally using progress bars.
         */
        RemoteTasksManager() {
            hostsToTasks = new HashMap<>();
        }

        /**
         * Update the remote tasks based to reflect a collaboration event
         * received from another node.
         *
         * @param event A collaboration event.
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
            RemoteTasks tasks = hostsToTasks.get(event.getHostName());
            if (null != tasks) {
                tasks.update(event);
            } else {
                hostsToTasks.put(event.getHostName(), new RemoteTasks(event));
            }
        }

        /**
         * RJCTODO
         */
        synchronized void finishStaleTasks() {
            for (Iterator<Map.Entry<String, RemoteTasks>> it = hostsToTasks.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, RemoteTasks> entry = it.next();
                RemoteTasks tasks = entry.getValue();
                if (tasks.isStale()) {
                    tasks.finishAllTasks();
                    it.remove();
                }
            }
        }

        /**
         * A collection of progress bars for tasks on a collaborating node.
         */
        class RemoteTasks {

            private final Duration MAX_MINUTES_WITHOUT_UPDATE = Duration.ofSeconds(HEARTBEAT_INTERVAL_MINUTES * MAX_MISSED_HEARTBEATS);
            private Instant lastUpdateTime;
            private Map<Long, ProgressHandle> taskIdsToProgressBars;

            /**
             * Construct a set of progress bars to represent remote tasks for a
             * particular host.
             *
             * @param event A collaboration event.
             */
            RemoteTasks(CollaborationEvent event) {
                /**
                 * Set the initial value of the last update time stamp.
                 */
                lastUpdateTime = Instant.now();
                
                taskIdsToProgressBars = new HashMap<>();
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
                 * Update the last update timestamp.
                 */
                lastUpdateTime = Instant.now();


                /**
                 * Create or update the progress bars for the current tasks of
                 * the node that published the event.
                 */
                Map<Long, Task> remoteTasks = event.getCurrentTasks();
                remoteTasks.values().stream().forEach((task) -> {
                    ProgressHandle progress = taskIdsToProgressBars.get(task.getId());
                    if (null != progress) {
                        /**
                         * Update the existing progress bar.
                         */
                        progress.progress(task.getStatus());
                    } else {
                        /**
                         * A new task, create a progress bar.
                         */
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
                    if (!remoteTasks.containsKey(id)) {
                        ProgressHandle progress = taskIdsToProgressBars.remove(id);
                        progress.finish();
                    }
                }
            }

            /**
             * Unconditionally finishes the entire set or remote tasks. To be 
             * used when a host drops off unexpectedly.
             */
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
            eventPublisher.publishRemotely(new CollaborationEvent(hostName, localTasksManager.getCurrentTasks()));
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
            remoteTasksManager.finishStaleTasks();
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
