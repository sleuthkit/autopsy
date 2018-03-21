/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.events.AddingDataSourceEvent;
import org.sleuthkit.autopsy.casemodule.events.AddingDataSourceFailedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisCompletedEvent;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisStartedEvent;

/**
 * A collaboration monitor listens to local events and translates them into
 * collaboration tasks that are broadcast to collaborating nodes and informs the
 * user of collaboration tasks on other nodes using progress bars.
 */
final class CollaborationMonitor {

    private static final String EVENT_CHANNEL_NAME = "%s-Collaboration-Monitor-Events"; //NON-NLS
    private static final String COLLABORATION_MONITOR_EVENT = "COLLABORATION_MONITOR_EVENT"; //NON-NLS
    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.ADDING_DATA_SOURCE,
            Case.Events.DATA_SOURCE_ADDED, Case.Events.ADDING_DATA_SOURCE_FAILED);
    private static final int NUMBER_OF_PERIODIC_TASK_THREADS = 2;
    private static final String PERIODIC_TASK_THREAD_NAME = "collab-monitor-periodic-tasks-%d"; //NON-NLS
    private static final long HEARTBEAT_INTERVAL_MINUTES = 1;
    private static final long MAX_MISSED_HEARTBEATS = 5;
    private static final long STALE_TASKS_DETECT_INTERVAL_MINS = 2;
    private static final long EXECUTOR_TERMINATION_WAIT_SECS = 30;
    private static final Logger logger = Logger.getLogger(CollaborationMonitor.class.getName());
    private final String hostName;
    private final LocalTasksManager localTasksManager;
    private final RemoteTasksManager remoteTasksManager;
    private final AutopsyEventPublisher eventPublisher;
    private final ScheduledThreadPoolExecutor periodicTasksExecutor;

    /**
     * Constructs a collaboration monitor that listens to local events and
     * translates them into collaboration tasks that are broadcast to
     * collaborating nodes, informs the user of collaboration tasks on other
     * nodes using progress bars, and monitors the health of key collaboration
     * services.
     *
     * @param eventChannelPrefix The prefix for the remote events channel.
     *
     * @throws
     * org.sleuthkit.autopsy.casemodule.CollaborationMonitor.CollaborationMonitorException
     */
    CollaborationMonitor(String eventChannelPrefix) throws CollaborationMonitorException {
        /**
         * Get the local host name so it can be used to identify the source of
         * collaboration tasks broadcast by this node.
         */
        hostName = NetworkUtils.getLocalHostName();

        /**
         * Create an event publisher that will be used to communicate with
         * collaboration monitors on other nodes working on the case.
         */
        eventPublisher = new AutopsyEventPublisher();
        try {
            eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, eventChannelPrefix));
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
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, localTasksManager);

        /**
         * Start periodic tasks that:
         *
         * 1. Send heartbeats to collaboration monitors on other nodes.<br>
         * 2. Check for stale remote tasks.<br>
         */
        periodicTasksExecutor = new ScheduledThreadPoolExecutor(NUMBER_OF_PERIODIC_TASK_THREADS, new ThreadFactoryBuilder().setNameFormat(PERIODIC_TASK_THREAD_NAME).build());
        periodicTasksExecutor.scheduleWithFixedDelay(new HeartbeatTask(), HEARTBEAT_INTERVAL_MINUTES, HEARTBEAT_INTERVAL_MINUTES, TimeUnit.MINUTES);
        periodicTasksExecutor.scheduleWithFixedDelay(new StaleTaskDetectionTask(), STALE_TASKS_DETECT_INTERVAL_MINS, STALE_TASKS_DETECT_INTERVAL_MINS, TimeUnit.MINUTES);
    }

    /**
     * Shuts down this collaboration monitor.
     */
    void shutdown() {
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

        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, localTasksManager);
        IngestManager.getInstance().removeIngestJobEventListener(localTasksManager);

        if (null != eventPublisher) {
            eventPublisher.removeSubscriber(COLLABORATION_MONITOR_EVENT, remoteTasksManager);
            eventPublisher.closeRemoteEventChannel();
        }

        remoteTasksManager.shutdown();
    }

    /**
     * The local tasks manager listens to local events and translates them into
     * tasks it broadcasts to collaborating nodes. Note that all access to the
     * task collections is synchronized since they may be accessed by both the
     * threads publishing property change events and by the heartbeat task
     * thread.
     */
    private final class LocalTasksManager implements PropertyChangeListener {

        private long nextTaskId;
        private final Map<Integer, Task> eventIdsToAddDataSourceTasks;
        private final Map<Long, Task> jobIdsTodataSourceAnalysisTasks;

        /**
         * Constructs a local tasks manager that listens to local events and
         * translates them into tasks that can be broadcast to collaborating
         * nodes.
         */
        LocalTasksManager() {
            nextTaskId = 0;
            eventIdsToAddDataSourceTasks = new HashMap<>();
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
            if (AutopsyEvent.SourceType.LOCAL == ((AutopsyEvent) event).getSourceType()) {
                String eventName = event.getPropertyName();
                if (eventName.equals(Case.Events.ADDING_DATA_SOURCE.toString())) {
                    addDataSourceAddTask((AddingDataSourceEvent) event);
                } else if (eventName.equals(Case.Events.ADDING_DATA_SOURCE_FAILED.toString())) {
                    removeDataSourceAddTask(((AddingDataSourceFailedEvent) event).getAddingDataSourceEventId());
                } else if (eventName.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                    removeDataSourceAddTask(((DataSourceAddedEvent) event).getAddingDataSourceEventId());
                } else if (eventName.equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_STARTED.toString())) {
                    addDataSourceAnalysisTask((DataSourceAnalysisStartedEvent) event);
                } else if (eventName.equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED.toString())) {
                    removeDataSourceAnalysisTask((DataSourceAnalysisCompletedEvent) event);
                }
            }
        }

        /**
         * Adds an adding data source task to the collection of local tasks and
         * publishes the updated collection to any collaborating nodes.
         *
         * @param event An adding data source event.
         */
        synchronized void addDataSourceAddTask(AddingDataSourceEvent event) {
            String status = NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.addingDataSourceStatus.msg", hostName);
            eventIdsToAddDataSourceTasks.put(event.getEventId().hashCode(), new Task(++nextTaskId, status));
            eventPublisher.publishRemotely(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Removes an adding data source task from the collection of local tasks
         * and publishes the updated collection to any collaborating nodes.
         *
         * @param eventId An event id to pair a data source added or adding data
         *                source failed event with an adding data source event.
         */
        synchronized void removeDataSourceAddTask(UUID eventId) {
            eventIdsToAddDataSourceTasks.remove(eventId.hashCode());
            eventPublisher.publishRemotely(new CollaborationEvent(hostName, getCurrentTasks()));
        }

        /**
         * Adds a data source analysis task to the collection of local tasks and
         * publishes the updated collection to any collaborating nodes.
         *
         * @param event A data source analysis started event.
         */
        synchronized void addDataSourceAnalysisTask(DataSourceAnalysisStartedEvent event) {
            String status = NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.analyzingDataSourceStatus.msg", hostName, event.getDataSource().getName());
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
            eventIdsToAddDataSourceTasks.values().stream().forEach((task) -> {
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
     * synchronized since it may be accessed by both the threads publishing
     * property change events and by the thread running periodic checks for
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
         * Updates the remote tasks in response to a collaboration event
         * received from another node.
         *
         * @param event The collaboration event.
         */
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (event.getPropertyName().equals(COLLABORATION_MONITOR_EVENT)) {
                updateTasks((CollaborationEvent) event);
            }
        }

        /**
         * Finishes the progress bars for all remote tasks.
         */
        synchronized void shutdown() {
            finishAllTasks();
        }

        /**
         * Updates the remote tasks to reflect a collaboration event received
         * from another node.
         *
         * @param event The collaboration event.
         */
        synchronized void updateTasks(CollaborationEvent event) {
            RemoteTasks tasksForHost = hostsToTasks.get(event.getHostName());
            if (null != tasksForHost) {
                tasksForHost.update(event);
            } else {
                hostsToTasks.put(event.getHostName(), new RemoteTasks(event));
            }
        }

        /**
         * Finishes the progress bars any remote tasks that have gone stale,
         * i.e., tasks for which updates have ceased, presumably because the
         * collaborating node has gone down or there is a network issue.
         */
        synchronized void finishStaleTasks() {
            for (Iterator<Map.Entry<String, RemoteTasks>> it = hostsToTasks.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, RemoteTasks> entry = it.next();
                RemoteTasks tasksForHost = entry.getValue();
                if (tasksForHost.isStale()) {
                    tasksForHost.finishAllTasks();
                    it.remove();
                }
            }
        }

        /**
         * Finishes the progress bars for all remote tasks.
         */
        synchronized void finishAllTasks() {
            for (Iterator<Map.Entry<String, RemoteTasks>> it = hostsToTasks.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, RemoteTasks> entry = it.next();
                RemoteTasks tasksForHost = entry.getValue();
                tasksForHost.finishAllTasks();
                it.remove();
            }
        }

        /**
         * A collection of progress bars for tasks on a collaborating node.
         */
        private final class RemoteTasks {

            private final long MAX_MINUTES_WITHOUT_UPDATE = HEARTBEAT_INTERVAL_MINUTES * MAX_MISSED_HEARTBEATS;
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
                    ProgressHandle progress = ProgressHandle.createHandle(event.getHostName());
                    progress.start();
                    progress.progress(task.getStatus());
                    taskIdsToProgressBars.put(task.getId(), progress);
                });
            }

            /**
             * Updates this remote tasks collection.
             *
             * @param event A collaboration event from the collaborating node
             *              associated with these tasks.
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
                        progress = ProgressHandle.createHandle(event.getHostName());
                        progress.start();
                        progress.progress(task.getStatus());
                        taskIdsToProgressBars.put(task.getId(), progress);
                    }
                });

                /**
                 * If a task is no longer in the task list from the remote node,
                 * it is finished. Remove the progress bars for finished tasks.
                 */
                for (Iterator<Map.Entry<Long, ProgressHandle>> iterator = taskIdsToProgressBars.entrySet().iterator(); iterator.hasNext();) {
                    Map.Entry<Long, ProgressHandle> entry = iterator.next();
                    if (!remoteTasks.containsKey(entry.getKey())) {
                        ProgressHandle progress = entry.getValue();
                        progress.finish();
                        iterator.remove();
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
                return Duration.between(lastUpdateTime, Instant.now()).toMinutes() >= MAX_MINUTES_WITHOUT_UPDATE;
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
            try {
                eventPublisher.publishRemotely(new CollaborationEvent(hostName, localTasksManager.getCurrentTasks()));
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unexpected exception in HeartbeatTask", ex); //NON-NLS
            }
        }
    }

    /**
     * A Runnable task that periodically deals with any remote tasks that have
     * gone stale, i.e., tasks for which updates have ceased, presumably because
     * the collaborating node has gone down or there is a network issue.
     */
    private final class StaleTaskDetectionTask implements Runnable {

        /**
         * Check for stale remote tasks and clean them up, if found.
         */
        @Override
        public void run() {
            try {
                remoteTasksManager.finishStaleTasks();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unexpected exception in StaleTaskDetectionTask", ex); //NON-NLS
            }
        }
    }

    /**
     * An Autopsy event to be sent in event messages to the collaboration
     * monitors on other Autopsy nodes.
     */
    private final static class CollaborationEvent extends AutopsyEvent implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String hostName;
        private final Map<Long, Task> currentTasks;

        /**
         * Constructs an Autopsy event to be sent in event messages to the
         * collaboration monitors on other Autopsy nodes.
         *
         * @param hostName     The name of the host sending the event.
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
    private final static class Task implements Serializable {

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
         *         node.
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

    /**
     * Custom exception class for the collaboration monitor.
     */
    final static class CollaborationMonitorException extends Exception {

        /**
         * Constructs and instance of the custom exception class for the
         * collaboration monitor.
         *
         * @param message Exception message.
         */
        CollaborationMonitorException(String message) {
            super(message);
        }

        /**
         * Constructs and instance of the custom exception class for the
         * collaboration monitor.
         *
         * @param message   Exception message.
         * @param throwable Exception cause.
         */
        CollaborationMonitorException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

}
