/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Desktop;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus;
import static org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus.DELETED;
import static org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus.PENDING;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestManager.Event;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestNodeControlEvent.ControlEventType;

/**
 * An auto ingest monitor responsible for monitoring and reporting the
 * processing of auto ingest jobs.
 */
final class AutoIngestMonitor extends Observable implements PropertyChangeListener {

    private static final Logger LOGGER = Logger.getLogger(AutoIngestMonitor.class.getName());
    private static final int DEFAULT_PRIORITY = 0;
    private static final int NUM_COORD_SVC_QUERY_THREADS = 1;
    private static final String COORD_SVC_QUERY_THREAD_NAME = "AIM-coord-svc-query-thread-%d"; //NON-NLS
    private static final int CORRD_SVC_QUERY_INERVAL_MINS = 5;
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();
    private static final String EVENT_CHANNEL_NAME = "Auto-Ingest-Manager-Events"; //NON-NLS
    private static final Set<String> EVENT_LIST = new HashSet<>(Arrays.asList(new String[]{
        AutoIngestManager.Event.JOB_STATUS_UPDATED.toString(),
        AutoIngestManager.Event.JOB_COMPLETED.toString(),
        AutoIngestManager.Event.CASE_PRIORITIZED.toString(),
        AutoIngestManager.Event.JOB_STARTED.toString(),
        AutoIngestManager.Event.RUNNING.toString(),
        AutoIngestManager.Event.PAUSE_REQUESTED.toString(),
        AutoIngestManager.Event.PAUSED_BY_USER_REQUEST.toString(),
        AutoIngestManager.Event.PAUSED_FOR_SYSTEM_ERROR.toString(),
        AutoIngestManager.Event.STARTING_UP.toString(),
        AutoIngestManager.Event.SHUTTING_DOWN.toString(),
        AutoIngestManager.Event.SHUTDOWN.toString(),
        AutoIngestManager.Event.RESUMED.toString(),
        AutoIngestManager.Event.GENERATE_THREAD_DUMP_RESPONSE.toString(),
        AutoIngestManager.Event.OCR_STATE_CHANGE.toString()}));
    private final AutopsyEventPublisher eventPublisher;
    private CoordinationService coordinationService;
    private final ScheduledThreadPoolExecutor coordSvcQueryExecutor;
    private final Object jobsLock;
    @GuardedBy("jobsLock")
    private JobsSnapshot jobsSnapshot;

    private final Map<String, AutoIngestNodeState> nodeStates = new ConcurrentHashMap<>();

    /**
     * Constructs an auto ingest monitor responsible for monitoring and
     * reporting the processing of auto ingest jobs.
     */
    AutoIngestMonitor() {
        eventPublisher = new AutopsyEventPublisher();
        coordSvcQueryExecutor = new ScheduledThreadPoolExecutor(NUM_COORD_SVC_QUERY_THREADS, new ThreadFactoryBuilder().setNameFormat(COORD_SVC_QUERY_THREAD_NAME).build());
        jobsLock = new Object();
        jobsSnapshot = new JobsSnapshot();
    }

    /**
     * Starts up the auto ingest monitor.
     *
     * @throws AutoIngestMonitorException If there is a problem starting the
     *                                    auto ingest monitor.
     */
    void startUp() throws AutoIngestMonitor.AutoIngestMonitorException {
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestMonitorException("Failed to get coordination service", ex); //NON-NLS
        }
        try {
            eventPublisher.openRemoteEventChannel(EVENT_CHANNEL_NAME);
        } catch (AutopsyEventException ex) {
            throw new AutoIngestMonitorException("Failed to open auto ingest event channel", ex); //NON-NLS
        }
        coordSvcQueryExecutor.scheduleWithFixedDelay(new StateRefreshTask(), 0, CORRD_SVC_QUERY_INERVAL_MINS, TimeUnit.MINUTES);
        eventPublisher.addSubscriber(EVENT_LIST, this);

        refreshNodeState();
    }

    /**
     * Shuts down the auto ingest ingest monitor.
     */
    void shutDown() {
        try {
            eventPublisher.removeSubscriber(EVENT_LIST, this);
            coordSvcQueryExecutor.shutdownNow();
            while (!coordSvcQueryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.log(Level.WARNING, "Auto ingest monitor waited at least thirty seconds for coordination service executor to shut down, continuing to wait"); //NON-NLS
            }
            eventPublisher.closeRemoteEventChannel();
        } catch (InterruptedException ex) {
            LOGGER.log(Level.WARNING, "Auto ingest monitor interrupted during shut down", ex); //NON-NLS
        }
    }

    /**
     * Handles auto ingest job events published by the auto ingest nodes in an
     * auto ingest cluster.
     *
     * @param event An auto ingest event from another node.
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event instanceof AutoIngestJobStartedEvent) {
            handleJobStartedEvent((AutoIngestJobStartedEvent) event);
        } else if (event instanceof AutoIngestJobStatusEvent) {
            handleJobStatusEvent((AutoIngestJobStatusEvent) event);
        } else if (event instanceof AutoIngestJobCompletedEvent) {
            handleJobCompletedEvent((AutoIngestJobCompletedEvent) event);
        } else if (event instanceof AutoIngestCasePrioritizedEvent) {
            handleCasePrioritizationEvent((AutoIngestCasePrioritizedEvent) event);
        } else if (event instanceof AutoIngestCaseDeletedEvent) {
            handleCaseDeletedEvent((AutoIngestCaseDeletedEvent) event);
        } else if (event instanceof AutoIngestNodeStateEvent) {
            handleAutoIngestNodeStateEvent((AutoIngestNodeStateEvent) event);
        } else if (event instanceof ThreadDumpResponseEvent) {
            handleRemoteThreadDumpResponseEvent((ThreadDumpResponseEvent) event);
        } else if (event instanceof AutoIngestOcrStateChangeEvent) {
            handleOcrStateChangeEvent((AutoIngestOcrStateChangeEvent) event);
        }
    }

    /**
     * Handles an auto ingest job started event.
     *
     * @param event A auto ingest job started event.
     */
    private void handleJobStartedEvent(AutoIngestJobStartedEvent event) {
        synchronized (jobsLock) {
            jobsSnapshot.removePendingJob(event.getJob());
            jobsSnapshot.addOrReplaceRunningJob(event.getJob());
            setChanged();
            notifyObservers();
        }
    }

    /**
     * Handles an auto ingest job status event.
     *
     * @param event A auto ingest job status event.
     */
    private void handleJobStatusEvent(AutoIngestJobStatusEvent event) {
        synchronized (jobsLock) {
            /*
             * Currently this event is only published for running jobs.
             */
            AutoIngestJob job = event.getJob();
            jobsSnapshot.removePendingJob(job);

            // Update the state of the existing job in the running jobs table
            for (AutoIngestJob runningJob : getRunningJobs()) {
                if (runningJob.equals(job)) {
                    runningJob.setIngestJobsSnapshot(job.getIngestJobSnapshots());
                    runningJob.setIngestThreadSnapshot(job.getIngestThreadActivitySnapshots());
                    runningJob.setModuleRuntimesSnapshot(job.getModuleRunTimes());
                    runningJob.setProcessingStage(job.getProcessingStage(), job.getProcessingStageStartDate());
                    runningJob.setProcessingStatus(job.getProcessingStatus());
                    break;
                }
            }
            setChanged();
            notifyObservers();
        }
    }

    /**
     * Handles an auto ingest job completed event.
     *
     * @param event A auto ingest job completed event.
     */
    private void handleJobCompletedEvent(AutoIngestJobCompletedEvent event) {
        synchronized (jobsLock) {
            AutoIngestJob job = event.getJob();
            jobsSnapshot.removePendingJob(job);
            jobsSnapshot.removeRunningJob(job);
            jobsSnapshot.addOrReplaceCompletedJob(job);
            setChanged();
            notifyObservers();
        }
    }

    /**
     * Handles an OCR state change event.
     *
     * @param event OCR state change event.
     */
    private void handleOcrStateChangeEvent(AutoIngestOcrStateChangeEvent event) {
        coordSvcQueryExecutor.submit(new StateRefreshTask());
    }    
    
    /**
     * Handles an auto ingest job/case prioritization event.
     *
     * @param event A job/case prioritization event.
     */
    private void handleCasePrioritizationEvent(AutoIngestCasePrioritizedEvent event) {
        coordSvcQueryExecutor.submit(new StateRefreshTask());
    }

    /**
     * Handles a case deletion event.
     *
     * @param event A job/case deletion event.
     */
    private void handleCaseDeletedEvent(AutoIngestCaseDeletedEvent event) {
        coordSvcQueryExecutor.submit(new StateRefreshTask());
    }

    /**
     * Handles an auto ingest node state change event.
     *
     * @param event A node state change event.
     */
    private void handleAutoIngestNodeStateEvent(AutoIngestNodeStateEvent event) {
        AutoIngestNodeState oldNodeState = null;
        if (event.getEventType() == AutoIngestManager.Event.SHUTDOWN) {
            // Remove node from collection.
            oldNodeState = nodeStates.remove(event.getNodeName());
        } else {
            // Otherwise either create an entry for the given node name or update
            // an existing entry in the map.
            nodeStates.put(event.getNodeName(), new AutoIngestNodeState(event.getNodeName(), event.getEventType()));
        }
        setChanged();
        // Trigger a dashboard refresh.
        notifyObservers(oldNodeState == null ? nodeStates.get(event.getNodeName()) : oldNodeState);
    }
    
    /**
     * Handles thread dump response event.
     *
     * @param event ThreadDumpResponseEvent
     */
    private void handleRemoteThreadDumpResponseEvent(ThreadDumpResponseEvent event) {
        if (event.getTargetNodeName().compareToIgnoreCase(LOCAL_HOST_NAME) == 0) {
            LOGGER.log(Level.INFO, "Received thread dump response event from machine {0}", event.getOriginalNodeName());
            File dumpFile = createFilePath(event.getOriginalNodeName()).toFile();
            try {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(dumpFile, true))) {
                    writer.write(event.getThreadDump());
                }

                Desktop.getDesktop().open(dumpFile);
            } catch (IOException ex) {
                if (dumpFile != null) {
                    LOGGER.log(Level.WARNING, "Failed to open thread dump file in external viewer: " + dumpFile.getAbsolutePath(), ex);
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to create thread dump file.", ex);
                }
            }
        }
    }

    /**
     * Create the thread dump file path.
     *
     * @return Path for dump file.
     */
    private Path createFilePath(String nodeName) {
        String fileName = "ThreadDumpFromNode_" + nodeName + "_" + TimeStampUtils.createTimeStamp() + ".txt";
        return Paths.get(PlatformUtil.getLogDirectory(), fileName);
    }

    /**
     * Gets the snapshot of the pending jobs queue for an auto ingest cluster.
     *
     * @return The pending jobs queue.
     */
    List<AutoIngestJob> getPendingJobs() {
        synchronized (jobsLock) {
            return new ArrayList<>(jobsSnapshot.pendingJobs);
        }
    }

    /**
     * Gets the snapshot of the running jobs list for an auto ingest cluster.
     *
     * @return The running jobs list.
     */
    List<AutoIngestJob> getRunningJobs() {
        synchronized (jobsLock) {
            return new ArrayList<>(jobsSnapshot.runningJobs);
        }
    }

    /**
     * Gets the snapshot of the completed jobs list for an auto ingest cluster.
     *
     * @return The completed jobs list.
     */
    List<AutoIngestJob> getCompletedJobs() {
        synchronized (jobsLock) {
            return new ArrayList<>(jobsSnapshot.completedJobs);
        }
    }

    /**
     * Gets the current state of known AIN's in the system.
     *
     * @return
     */
    List<AutoIngestNodeState> getNodeStates() {
        // We only report the state for nodes for which we have received
        // a 'state' event in the last 15 minutes.
        return nodeStates.values()
                .stream()
                .filter(s -> s.getLastSeenTime().isAfter(Instant.now().minus(Duration.ofMinutes(15))))
                .collect(Collectors.toList());
    }

    /**
     * Makes the auto ingest monitor's refresh its current snapshot of the
     * pending jobs queue, running jobs list, and completed jobs list for an
     * auto ingest cluster.
     *
     * @return The refreshed snapshot.
     */
    void refreshJobsSnapshot() {
        synchronized (jobsLock) {
            jobsSnapshot = queryCoordinationService();
        }
    }

    /**
     * Ask running auto ingest nodes to report their state.
     */
    private void refreshNodeState() {
        // Publish an event that asks running nodes to send their state.
        eventPublisher.publishRemotely(new AutoIngestRequestNodeStateEvent(AutoIngestManager.Event.REPORT_STATE));
    }

    /**
     * Gets a new snapshot of the pending jobs queue, running jobs list, and
     * completed jobs list for an auto ingest cluster.
     *
     * @return The snapshot.
     */
    private JobsSnapshot queryCoordinationService() {
        try {
            JobsSnapshot newJobsSnapshot = new JobsSnapshot();
            List<String> nodeList = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
            for (String node : nodeList) {
                try {
                    AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, node));
                    if (nodeData.getVersion() < 1) {
                        /*
                         * Ignore version '0' nodes that have not been
                         * "upgraded" since they don't carry enough data.
                         */
                        continue;
                    }
                    AutoIngestJob job = new AutoIngestJob(nodeData);
                    ProcessingStatus processingStatus = nodeData.getProcessingStatus();
                    switch (processingStatus) {
                        case PENDING:
                            newJobsSnapshot.addOrReplacePendingJob(job);
                            break;
                        case PROCESSING:
                            newJobsSnapshot.addOrReplaceRunningJob(job);
                            break;
                        case COMPLETED:
                            newJobsSnapshot.addOrReplaceCompletedJob(job);
                            break;
                        case DELETED:
                            /*
                             * Ignore jobs marked as deleted.
                             */
                            break;
                        default:
                            LOGGER.log(Level.SEVERE, "Unknown AutoIngestJobData.ProcessingStatus");
                            break;
                    }
                } catch (InterruptedException ignore) {
                    LOGGER.log(Level.WARNING, "Interrupt while retrieving coordination service node data");
                    return newJobsSnapshot;
                } catch (AutoIngestJobNodeData.InvalidDataException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Unable to use node data for '%s'", node), ex);
                } catch (AutoIngestJob.AutoIngestJobException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to create a job for '%s'", node), ex);
                }
            }

            return newJobsSnapshot;

        } catch (CoordinationServiceException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get node list from coordination service", ex);
            return new JobsSnapshot();
        }
    }
        
    /**
     * Enables OCR for all pending ingest jobs for a specified case.
     *
     * @param caseName The name of the case to enable OCR.
     *
     * @throws AutoIngestMonitorException If there is an error enabling OCR for the jobs for the case.
     *
     */
    void changeOcrStateForCase(final String caseName, final boolean ocrState) throws AutoIngestMonitorException {
        List<AutoIngestJob> jobsToPrioritize = new ArrayList<>();
        synchronized (jobsLock) {
            for (AutoIngestJob pendingJob : getPendingJobs()) {
                if (pendingJob.getManifest().getCaseName().equals(caseName)) {
                    jobsToPrioritize.add(pendingJob);
                }
            }
            if (!jobsToPrioritize.isEmpty()) {
                for (AutoIngestJob job : jobsToPrioritize) {
                    String manifestNodePath = job.getManifest().getFilePath().toString();
                    try {
                        AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                        nodeData.setOcrEnabled(ocrState);
                        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                    } catch (AutoIngestJobNodeData.InvalidDataException | CoordinationServiceException | InterruptedException ex) {
                        throw new AutoIngestMonitorException("Error enabling OCR for job " + job.toString(), ex);
                    }
                    job.setOcrEnabled(ocrState);

                    /**
                     * Update job object in pending jobs queue
                     */
                    jobsSnapshot.addOrReplacePendingJob(job);
                }

                /*
                 * Publish the OCR enabled event.
                 */
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestOcrStateChangeEvent(LOCAL_HOST_NAME, caseName,
                            AutoIngestManager.getSystemUserNameProperty(), ocrState));
                }).start();
            }
        }
    }

    /**
     * Removes the priority (set to zero) of all pending ingest jobs for a
     * specified case.
     *
     * @param caseName The name of the case to be deprioritized.
     *
     * @throws AutoIngestMonitorException If there is an error removing the
     *                                    priority of the jobs for the case.
     *
     */
    void deprioritizeCase(final String caseName) throws AutoIngestMonitorException {
        List<AutoIngestJob> jobsToDeprioritize = new ArrayList<>();

        synchronized (jobsLock) {
            for (AutoIngestJob pendingJob : getPendingJobs()) {
                if (pendingJob.getManifest().getCaseName().equals(caseName)) {
                    jobsToDeprioritize.add(pendingJob);
                }
            }
            if (!jobsToDeprioritize.isEmpty()) {
                for (AutoIngestJob job : jobsToDeprioritize) {
                    String manifestNodePath = job.getManifest().getFilePath().toString();
                    try {
                        AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                        nodeData.setPriority(DEFAULT_PRIORITY);
                        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                    } catch (AutoIngestJobNodeData.InvalidDataException | CoordinationServiceException | InterruptedException ex) {
                        throw new AutoIngestMonitorException("Error removing priority for job " + job.toString(), ex);
                    }
                    job.setPriority(DEFAULT_PRIORITY);

                    /**
                     * Update job object in pending jobs queue
                     */
                    jobsSnapshot.addOrReplacePendingJob(job);
                }

                /*
                 * Publish a deprioritization event.
                 */
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName,
                            AutoIngestManager.getSystemUserNameProperty(), AutoIngestCasePrioritizedEvent.EventType.CASE_DEPRIORITIZED, ""));
                }).start();
            }
        }
    }

    /**
     * Bumps the priority of all pending ingest jobs for a specified case.
     *
     * @param caseName The name of the case to be prioritized.
     *
     * @throws AutoIngestMonitorException If there is an error bumping the
     *                                    priority of the jobs for the case.
     *
     */
    void prioritizeCase(final String caseName) throws AutoIngestMonitorException {
        List<AutoIngestJob> jobsToPrioritize = new ArrayList<>();
        int highestPriority = 0;
        synchronized (jobsLock) {
            for (AutoIngestJob pendingJob : getPendingJobs()) {
                if (pendingJob.getPriority() > highestPriority) {
                    highestPriority = pendingJob.getPriority();
                }
                if (pendingJob.getManifest().getCaseName().equals(caseName)) {
                    jobsToPrioritize.add(pendingJob);
                }
            }
            if (!jobsToPrioritize.isEmpty()) {
                ++highestPriority;
                for (AutoIngestJob job : jobsToPrioritize) {
                    String manifestNodePath = job.getManifest().getFilePath().toString();
                    try {
                        AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                        nodeData.setPriority(highestPriority);
                        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                    } catch (AutoIngestJobNodeData.InvalidDataException | CoordinationServiceException | InterruptedException ex) {
                        throw new AutoIngestMonitorException("Error bumping priority for job " + job.toString(), ex);
                    }
                    job.setPriority(highestPriority);

                    /**
                     * Update job object in pending jobs queue
                     */
                    jobsSnapshot.addOrReplacePendingJob(job);
                }

                /*
                 * Publish a prioritization event.
                 */
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName,
                            AutoIngestManager.getSystemUserNameProperty(), AutoIngestCasePrioritizedEvent.EventType.CASE_PRIORITIZED, ""));
                }).start();
            }
        }
    }

    /**
     * Removes the priority (set to zero) of an auto ingest job.
     *
     * @param job The job to be deprioritized.
     *
     * @throws AutoIngestMonitorException If there is an error removing the
     *                                    priority of the job.
     *
     */
    void deprioritizeJob(AutoIngestJob job) throws AutoIngestMonitorException {
        synchronized (jobsLock) {
            AutoIngestJob jobToDeprioritize = null;
            /*
             * Make sure the job is still in the pending jobs queue.
             */
            for (AutoIngestJob pendingJob : getPendingJobs()) {
                if (pendingJob.equals(job)) {
                    jobToDeprioritize = job;
                    break;
                }
            }

            /*
             * If the job was still in the pending jobs queue, reset its
             * priority.
             */
            if (null != jobToDeprioritize) {
                String manifestNodePath = job.getManifest().getFilePath().toString();
                try {
                    AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                    nodeData.setPriority(DEFAULT_PRIORITY);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                } catch (AutoIngestJobNodeData.InvalidDataException | CoordinationServiceException | InterruptedException ex) {
                    throw new AutoIngestMonitorException("Error removing priority for job " + job.toString(), ex);
                }
                jobToDeprioritize.setPriority(DEFAULT_PRIORITY);

                /**
                 * Update job object in pending jobs queue
                 */
                jobsSnapshot.addOrReplacePendingJob(jobToDeprioritize);

                /*
                 * Publish a deprioritization event.
                 */
                final String caseName = job.getManifest().getCaseName();
                final String dataSourceName = jobToDeprioritize.getManifest().getDataSourceFileName();
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName,
                            AutoIngestManager.getSystemUserNameProperty(), AutoIngestCasePrioritizedEvent.EventType.JOB_DEPRIORITIZED, dataSourceName));
                }).start();

            }
        }
    }

    /**
     * Bumps the priority of an auto ingest job.
     *
     * @param job The job to be prioritized.
     *
     * @throws AutoIngestMonitorException If there is an error bumping the
     *                                    priority of the job.
     *
     */
    void prioritizeJob(AutoIngestJob job) throws AutoIngestMonitorException {
        synchronized (jobsLock) {
            int highestPriority = 0;
            AutoIngestJob jobToPrioritize = null;
            /*
             * Get the highest known priority and make sure the job is still in
             * the pending jobs queue.
             */
            for (AutoIngestJob pendingJob : getPendingJobs()) {
                if (pendingJob.getPriority() > highestPriority) {
                    highestPriority = pendingJob.getPriority();
                }
                if (pendingJob.equals(job)) {
                    jobToPrioritize = job;
                }
            }

            /*
             * If the job was still in the pending jobs queue, bump its
             * priority.
             */
            if (null != jobToPrioritize) {
                ++highestPriority;
                String manifestNodePath = job.getManifest().getFilePath().toString();
                try {
                    AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                    nodeData.setPriority(highestPriority);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                } catch (AutoIngestJobNodeData.InvalidDataException | CoordinationServiceException | InterruptedException ex) {
                    throw new AutoIngestMonitorException("Error bumping priority for job " + job.toString(), ex);
                }
                jobToPrioritize.setPriority(highestPriority);

                /**
                 * Update job object in pending jobs queue
                 */
                jobsSnapshot.addOrReplacePendingJob(jobToPrioritize);

                /*
                 * Publish a prioritization event.
                 */
                final String caseName = job.getManifest().getCaseName();
                final String dataSourceName = jobToPrioritize.getManifest().getDataSourceFileName();
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName,
                            AutoIngestManager.getSystemUserNameProperty(), AutoIngestCasePrioritizedEvent.EventType.JOB_PRIORITIZED, dataSourceName));
                }).start();

            }
        }
    }

    /**
     * Send an event to tell a remote node to cancel the given job.
     *
     * @param job
     */
    void cancelJob(AutoIngestJob job) {
        new Thread(() -> {
            eventPublisher.publishRemotely(new AutoIngestJobCancelEvent(job, LOCAL_HOST_NAME, AutoIngestManager.getSystemUserNameProperty()));
        }).start();
    }

    /**
     * Reprocess the given job.
     *
     * @param job
     */
    void reprocessJob(AutoIngestJob job) throws AutoIngestMonitorException {
        synchronized (jobsLock) {
            if (!getCompletedJobs().contains(job)) {
                return;
            }

            jobsSnapshot.removeCompletedJob(job);

            /*
             * Add the job to the pending jobs queue and update the coordination
             * service manifest node data for the job.
             */
            if (null != job && !job.getCaseDirectoryPath().toString().isEmpty()) {
                /**
                 * We reset the status, completion date and processing stage but
                 * we keep the original priority.
                 */
                job.setErrorsOccurred(false);
                job.setCompletedDate(new Date(0));
                job.setProcessingStatus(PENDING);
                job.setProcessingStage(AutoIngestJob.Stage.PENDING, Date.from(Instant.now()));
                String manifestNodePath = job.getManifest().getFilePath().toString();
                try {
                    AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(job);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                } catch (CoordinationServiceException | InterruptedException ex) {
                    throw new AutoIngestMonitorException("Error reprocessing job " + job.toString(), ex);
                }

                // Add to pending jobs collection.
                jobsSnapshot.addOrReplacePendingJob(job);

                /*
                 * Publish a reprocess event.
                 */
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestJobReprocessEvent(job, LOCAL_HOST_NAME, AutoIngestManager.getSystemUserNameProperty()));
                }).start();

            }
        }
    }

    /**
     * Send the given control event to the given node.
     *
     * @param eventType The type of control event to send.
     * @param nodeName  The name of the node to send it to.
     */
    private void sendControlEventToNode(ControlEventType eventType, String nodeName) {
        new Thread(() -> {
            eventPublisher.publishRemotely(new AutoIngestNodeControlEvent(eventType, nodeName, LOCAL_HOST_NAME, AutoIngestManager.getSystemUserNameProperty()));
        }).start();
    }

    /**
     * Tell the specified node to pause.
     *
     * @param nodeName
     */
    void pauseAutoIngestNode(String nodeName) {
        sendControlEventToNode(ControlEventType.PAUSE, nodeName);
    }

    /**
     * Tell the specified node to resume.
     *
     * @param nodeName
     */
    void resumeAutoIngestNode(String nodeName) {
        sendControlEventToNode(ControlEventType.RESUME, nodeName);
    }

    /**
     * Tell the specified node to shutdown.
     *
     * @param nodeName
     */
    void shutdownAutoIngestNode(String nodeName) {
        sendControlEventToNode(ControlEventType.SHUTDOWN, nodeName);
    }
    
    /**
     * Tell the specified node to generate a thread dump.
     *
     * @param job
     */
    void generateThreadDump(String nodeName) {
        sendControlEventToNode(ControlEventType.GENERATE_THREAD_DUMP_REQUEST, nodeName);
    }    

    /**
     * A task that updates the state maintained by the monitor. At present this
     * includes auto ingest job and auto ingest node data. The job data is
     * refreshed by querying the coordination service for auto ingest manifest
     * nodes. The auto ingest node data is refreshed by publishing a message
     * asking all nodes to report their state.
     */
    private final class StateRefreshTask implements Runnable {

        @Override
        public void run() {
            if (!Thread.currentThread().isInterrupted()) {
                // Query coordination service for jobs data.
                refreshJobsSnapshot();

                // Ask running auto ingest nodes to report their status.
                refreshNodeState();

                setChanged();
                notifyObservers();
            }
        }

    }

    /**
     * A snapshot of the pending jobs queue, running jobs list and completed
     * jobs list for an auto ingest cluster.
     */
    private static final class JobsSnapshot {

        private final Set<AutoIngestJob> pendingJobs = new HashSet<>();
        private final Set<AutoIngestJob> runningJobs = new HashSet<>();
        private final Set<AutoIngestJob> completedJobs = new HashSet<>();

        /**
         * Adds an auto job to the snapshot of the pending jobs queue for an
         * auto ingest cluster. If an equivalent job already exists, it is
         * removed.
         *
         * @param job The job.
         */
        private void addOrReplacePendingJob(AutoIngestJob job) {
            addOrReplaceJob(this.pendingJobs, job);
        }

        /**
         * Removes a job, if present, in the snapshot of the pending jobs queue
         * for an auto ingest cluster.
         *
         * @param job The auto ingest job.
         */
        private void removePendingJob(AutoIngestJob job) {
            this.pendingJobs.remove(job);
        }

        /**
         * Adds an auto job to the snapshot of the running jobs list for an auto
         * ingest cluster. If an equivalent job already exists, it is removed.
         *
         * @param job The job.
         */
        private void addOrReplaceRunningJob(AutoIngestJob job) {
            addOrReplaceJob(this.runningJobs, job);
        }

        /**
         * Removes a job, if present, in the snapshot of the running jobs list
         * for an auto ingest cluster.
         *
         * @param job The auto ingest job.
         */
        private void removeRunningJob(AutoIngestJob job) {
            this.runningJobs.remove(job);
        }

        /**
         * Adds an auto job to the snapshot of the completed jobs list for an
         * auto ingest cluster. If an equivalent job already exists, it is
         * removed.
         *
         * @param job The job.
         */
        private void addOrReplaceCompletedJob(AutoIngestJob job) {
            addOrReplaceJob(this.completedJobs, job);
        }

        /**
         * Removes a job, if present, in the snapshot of the completed jobs list
         * for an auto ingest cluster.
         *
         * @param job The auto ingest job.
         */
        private void removeCompletedJob(AutoIngestJob job) {
            this.completedJobs.remove(job);
        }

        /**
         * Adds a job to a set. If an equivalent job already exists, it is
         * removed.
         *
         * @param jobSet A set of auto ingest jobs.
         * @param job    The auto ingest job to add.
         */
        private static void addOrReplaceJob(Set<AutoIngestJob> jobSet, AutoIngestJob job) {
            if (jobSet.contains(job)) {
                jobSet.remove(job);
            }
            jobSet.add(job);
        }

    }

    /**
     * Class that represents the state of an AIN for the dashboard.
     */
    static final class AutoIngestNodeState {

        /**
         * The set of AIN states.
         */
        enum State {
            STARTING_UP,
            SHUTTING_DOWN,
            RUNNING,
            PAUSE_REQUESTED,
            PAUSED_BY_REQUEST,
            PAUSED_DUE_TO_SYSTEM_ERROR,
            UNKNOWN
        }

        private final String nodeName;
        private final State nodeState;
        private final Instant lastSeenTime;

        AutoIngestNodeState(String name, Event event) {
            nodeName = name;
            switch (event) {
                case STARTING_UP:
                    nodeState = State.STARTING_UP;
                    break;
                case SHUTTING_DOWN:
                    nodeState = State.SHUTTING_DOWN;
                    break;
                case RUNNING:
                    nodeState = State.RUNNING;
                    break;
                case PAUSED_BY_USER_REQUEST:
                    nodeState = State.PAUSED_BY_REQUEST;
                    break;
                case PAUSED_FOR_SYSTEM_ERROR:
                    nodeState = State.PAUSED_DUE_TO_SYSTEM_ERROR;
                    break;
                case RESUMED:
                    nodeState = State.RUNNING;
                    break;
                case PAUSE_REQUESTED:
                    nodeState = State.PAUSE_REQUESTED;
                    break;
                default:
                    nodeState = State.UNKNOWN;
                    break;
            }
            lastSeenTime = Instant.now();
        }

        String getName() {
            return nodeName;
        }

        State getState() {
            return nodeState;
        }

        Instant getLastSeenTime() {
            return lastSeenTime;
        }
    }

    /**
     * Exception type thrown when there is an error completing an auto ingest
     * monitor operation.
     */
    static final class AutoIngestMonitorException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest monitor operation.
         *
         * @param message The exception message.
         */
        private AutoIngestMonitorException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest monitor operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private AutoIngestMonitorException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
