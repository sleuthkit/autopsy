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
package org.sleuthkit.autopsy.experimental.autoingest;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus;
import static org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus.DELETED;
import static org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus.PENDING;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestManager.CaseDeletionResult;
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
        AutoIngestManager.Event.RESUMED.toString()}));
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
        coordSvcQueryExecutor.scheduleWithFixedDelay(new CoordinationServiceQueryTask(), 0, CORRD_SVC_QUERY_INERVAL_MINS, TimeUnit.MINUTES);
        eventPublisher.addSubscriber(EVENT_LIST, this);

        // Publish an event that asks running nodes to send their state.
        eventPublisher.publishRemotely(new AutoIngestRequestNodeStateEvent(AutoIngestManager.Event.REPORT_STATE));
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
            notifyObservers(jobsSnapshot);
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
            for (AutoIngestJob runningJob : jobsSnapshot.getRunningJobs()) {
                if (runningJob.equals(job)) {
                    runningJob.setIngestJobsSnapshot(job.getIngestJobSnapshots());
                    runningJob.setIngestThreadSnapshot(job.getIngestThreadActivitySnapshots());
                    runningJob.setModuleRuntimesSnapshot(job.getModuleRunTimes());
                    runningJob.setProcessingStage(job.getProcessingStage(), job.getProcessingStageStartDate());
                    runningJob.setProcessingStatus(job.getProcessingStatus());
                }
            }
            setChanged();
            notifyObservers(jobsSnapshot);
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
            notifyObservers(jobsSnapshot);
        }
    }

    /**
     * Handles an auto ingest job/case prioritization event.
     *
     * @param event A job/case prioritization event.
     */
    private void handleCasePrioritizationEvent(AutoIngestCasePrioritizedEvent event) {
        coordSvcQueryExecutor.submit(new CoordinationServiceQueryTask());
    }

    /**
     * Handles a case deletion event.
     *
     * @param event A job/case deletion event.
     */
    private void handleCaseDeletedEvent(AutoIngestCaseDeletedEvent event) {
        coordSvcQueryExecutor.submit(new CoordinationServiceQueryTask());
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
     * Gets the auto ingest monitor's current snapshot of the pending jobs
     * queue, running jobs list, and completed jobs list for an auto ingest
     * cluster.
     *
     * @return The snapshot.
     */
    JobsSnapshot getJobsSnapshot() {
        synchronized (jobsLock) {
            return jobsSnapshot;
        }
    }

    /**
     * Gets the current state of known AIN's in the system.
     *
     * @return
     */
    List<AutoIngestNodeState> getNodeStates() {
        return nodeStates.values().stream().collect(Collectors.toList());
    }

    /**
     * Makes the auto ingest monitor's refresh its current snapshot of the
     * pending jobs queue, running jobs list, and completed jobs list for an
     * auto ingest cluster.
     *
     * @return The refreshed snapshot.
     */
    JobsSnapshot refreshJobsSnapshot() {
        synchronized (jobsLock) {
            jobsSnapshot = queryCoordinationService();
            return jobsSnapshot;
        }
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
                            break;
                        default:
                            LOGGER.log(Level.SEVERE, "Unknown AutoIngestJobData.ProcessingStatus");
                            break;
                    }
                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Unexpected interrupt while retrieving coordination service node data for '%s'", node), ex);
                } catch (AutoIngestJobNodeData.InvalidDataException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Unable to use node data for '%s'", node), ex);
                } catch (AutoIngestJob.AutoIngestJobException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to create a job for '%s'", node), ex);
                }
            }

            return newJobsSnapshot;

        } catch (CoordinationServiceException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get node list from coordination service", ex);
            return new JobsSnapshot();
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
     * @return The latest jobs snapshot.
     */
    JobsSnapshot deprioritizeCase(final String caseName) throws AutoIngestMonitorException {
        List<AutoIngestJob> jobsToDeprioritize = new ArrayList<>();

        synchronized (jobsLock) {
            for (AutoIngestJob pendingJob : jobsSnapshot.getPendingJobs()) {
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
                }

                /*
                 * Publish a deprioritization event.
                 */
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
                }).start();
            }
            return jobsSnapshot;
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
     * @return The latest jobs snapshot.
     */
    JobsSnapshot prioritizeCase(final String caseName) throws AutoIngestMonitorException {
        List<AutoIngestJob> jobsToPrioritize = new ArrayList<>();
        int highestPriority = 0;
        synchronized (jobsLock) {
            for (AutoIngestJob pendingJob : jobsSnapshot.getPendingJobs()) {
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
                }

                /*
                 * Publish a prioritization event.
                 */
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
                }).start();
            }
            return jobsSnapshot;
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
     * @return The latest jobs snapshot.
     */
    JobsSnapshot deprioritizeJob(AutoIngestJob job) throws AutoIngestMonitorException {
        synchronized (jobsLock) {
            AutoIngestJob jobToDeprioritize = null;
            /*
             * Make sure the job is still in the pending jobs queue.
             */
            for (AutoIngestJob pendingJob : jobsSnapshot.getPendingJobs()) {
                if (pendingJob.equals(job)) {
                    jobToDeprioritize = job;
                    break;
                }
            }

            /*
             * If the job was still in the pending jobs queue, bump its
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

                /*
                 * Publish a deprioritization event.
                 */
                final String caseName = job.getManifest().getCaseName();
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
                }).start();

            }
            return jobsSnapshot;
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
     * @return The latest jobs snapshot.
     */
    JobsSnapshot prioritizeJob(AutoIngestJob job) throws AutoIngestMonitorException {
        synchronized (jobsLock) {
            int highestPriority = 0;
            AutoIngestJob jobToPrioritize = null;
            /*
             * Get the highest known priority and make sure the job is still in
             * the pending jobs queue.
             */
            for (AutoIngestJob pendingJob : jobsSnapshot.getPendingJobs()) {
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

                /*
                 * Publish a prioritization event.
                 */
                final String caseName = job.getManifest().getCaseName();
                new Thread(() -> {
                    eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
                }).start();

            }
            return jobsSnapshot;
        }
    }

    /**
     * Send an event to tell a remote node to cancel the given job.
     *
     * @param job
     */
    void cancelJob(AutoIngestJob job) {
        new Thread(() -> {
            eventPublisher.publishRemotely(new AutoIngestJobCancelEvent(job));
        }).start();
    }

    /**
     * Reprocess the given job.
     *
     * @param job
     */
    void reprocessJob(AutoIngestJob job) throws AutoIngestMonitorException {
        synchronized (jobsLock) {
            if (!jobsSnapshot.getCompletedJobs().contains(job)) {
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
                    eventPublisher.publishRemotely(new AutoIngestJobReprocessEvent(job));
                }).start();

            }
        }
    }

    /**
     * Deletes a case. This includes deleting the case directory, the text
     * index, and the case database. This does not include the directories
     * containing the data sources and their manifests.
     *
     * @param job The job whose case you want to delete
     *
     * @return A result code indicating success, partial success, or failure.
     */
    CaseDeletionResult deleteCase(AutoIngestJob job) {
        synchronized (jobsLock) {
            String caseName = job.getManifest().getCaseName();
            Path metadataFilePath = job.getCaseDirectoryPath().resolve(caseName + CaseMetadata.getFileExtension());

            try {
                CaseMetadata metadata = new CaseMetadata(metadataFilePath);
                Case.deleteCase(metadata);

            } catch (CaseMetadata.CaseMetadataException ex) {
                LOGGER.log(Level.SEVERE, String.format("Failed to get case metadata file %s for case %s at %s", metadataFilePath.toString(), caseName, job.getCaseDirectoryPath().toString()), ex);
                return CaseDeletionResult.FAILED;
            } catch (CaseActionException ex) {
                LOGGER.log(Level.SEVERE, String.format("Failed to physically delete case %s at %s", caseName, job.getCaseDirectoryPath().toString()), ex);
                return CaseDeletionResult.FAILED;
            }

            // Update the state of completed jobs associated with this case to indicate
            // that the case has been deleted
            for (AutoIngestJob completedJob : jobsSnapshot.getCompletedJobs()) {
                if (caseName.equals(completedJob.getManifest().getCaseName())) {
                    try {
                        completedJob.setProcessingStatus(DELETED);
                        AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(completedJob);
                        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, completedJob.getManifest().getFilePath().toString(), nodeData.toArray());
                    } catch (CoordinationServiceException | InterruptedException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Failed to update completed job node data for %s when deleting case %s", completedJob.getManifest().getFilePath().toString(), caseName), ex);
                        return CaseDeletionResult.PARTIALLY_DELETED;
                    }
                }
            }

            // Remove jobs associated with this case from the completed jobs collection.
            jobsSnapshot.completedJobs.removeIf((AutoIngestJob completedJob) -> 
                completedJob.getManifest().getCaseName().equals(caseName));

            // Publish a message to update auto ingest nodes.
            eventPublisher.publishRemotely(new AutoIngestCaseDeletedEvent(caseName, LOCAL_HOST_NAME));
        }

        return CaseDeletionResult.FULLY_DELETED;
    }

    /**
     * Send the given control event to the given node.
     *
     * @param eventType The type of control event to send.
     * @param nodeName  The name of the node to send it to.
     */
    private void sendControlEventToNode(ControlEventType eventType, String nodeName) {
        new Thread(() -> {
            eventPublisher.publishRemotely(new AutoIngestNodeControlEvent(eventType, nodeName, LOCAL_HOST_NAME, System.getProperty("user.name")));
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
     * A task that queries the coordination service for auto ingest manifest
     * node data and converts it to auto ingest jobs for publication top its
     * observers.
     */
    private final class CoordinationServiceQueryTask implements Runnable {

        /**
         * Queries the coordination service for auto ingest manifest node data
         * and converts it to auto ingest jobs for publication top its
         * observers.
         */
        @Override
        public void run() {
            if (!Thread.currentThread().isInterrupted()) {
                synchronized (jobsLock) {
                    jobsSnapshot = queryCoordinationService();
                    setChanged();
                    notifyObservers(jobsSnapshot);
                }
            }
        }

    }

    /**
     * A snapshot of the pending jobs queue, running jobs list and completed
     * jobs list for an auto ingest cluster.
     */
    static final class JobsSnapshot {

        private final Set<AutoIngestJob> pendingJobs = new HashSet<>();
        private final Set<AutoIngestJob> runningJobs = new HashSet<>();
        private final Set<AutoIngestJob> completedJobs = new HashSet<>();

        /**
         * Gets the snapshot of the pending jobs queue for an auto ingest
         * cluster.
         *
         * @return The pending jobs queue.
         */
        List<AutoIngestJob> getPendingJobs() {
            return new ArrayList<>(this.pendingJobs);
        }

        /**
         * Gets the snapshot of the running jobs list for an auto ingest
         * cluster.
         *
         * @return The running jobs list.
         */
        List<AutoIngestJob> getRunningJobs() {
            return new ArrayList<>(this.runningJobs);
        }

        /**
         * Gets the snapshot of the completed jobs list for an auto ingest
         * cluster.
         *
         * @return The completed jobs list.
         */
        List<AutoIngestJob> getCompletedJobs() {
            return new ArrayList<>(this.completedJobs);
        }

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
        }

        String getName() {
            return nodeName;
        }

        State getState() {
            return nodeState;
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
