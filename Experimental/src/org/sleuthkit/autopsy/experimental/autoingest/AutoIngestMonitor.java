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
package org.sleuthkit.autopsy.experimental.autoingest;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus;

/**
 * An auto ingest monitor responsible for monitoring and reporting the
 * processing of auto ingest jobs.
 */
public final class AutoIngestMonitor extends Observable implements PropertyChangeListener {

    private static final Logger LOGGER = Logger.getLogger(AutoIngestMonitor.class.getName());
    private static final int NUM_COORD_SVC_QUERY_THREADS = 1;
    private static final String COORD_SVC_QUERY_THREAD_NAME = "AIM-coord-svc-query-thread-%d"; //NON-NLS
    private static final int CORRD_SVC_QUERY_INERVAL_MINS = 5;
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();
    private static final String EVENT_CHANNEL_NAME = "Auto-Ingest-Manager-Events"; //NON-NLS
    private static final Set<String> EVENT_LIST = new HashSet<>(Arrays.asList(new String[]{
        AutoIngestManager.Event.JOB_STATUS_UPDATED.toString(),
        AutoIngestManager.Event.JOB_COMPLETED.toString(),
        AutoIngestManager.Event.CASE_PRIORITIZED.toString(),
        AutoIngestManager.Event.JOB_STARTED.toString()}));
    private final AutopsyEventPublisher eventPublisher;
    private CoordinationService coordinationService;
    private final ScheduledThreadPoolExecutor coordSvcQueryExecutor;
    private final Object jobsLock;
    @GuardedBy("jobsLock")
    private JobsSnapshot jobsSnapshot;

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
        coordSvcQueryExecutor.scheduleAtFixedRate(new CoordinationServiceQueryTask(), 0, CORRD_SVC_QUERY_INERVAL_MINS, TimeUnit.MINUTES);
        eventPublisher.addSubscriber(EVENT_LIST, this);
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
            notifyObservers(null);
        }
    }

    /**
     * Handles an auto ingest job status event.
     *
     * @param event A auto ingest job status event.
     */
    private void handleJobStatusEvent(AutoIngestJobStatusEvent event) {
        synchronized (jobsLock) {
            jobsSnapshot.addOrReplaceRunningJob(event.getJob());
            setChanged();
            notifyObservers(null);
        }
    }

    /**
     * Handles an auto ingest job completed event.
     *
     * @param event A auto ingest job completed event.
     */
    private void handleJobCompletedEvent(AutoIngestJobCompletedEvent event) {
        synchronized (jobsLock) {
            jobsSnapshot.removeRunningJob(event.getJob());
            jobsSnapshot.addOrReplaceCompletedJob(event.getJob());
            setChanged();
            notifyObservers(null);
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
     * @param event A job/case prioritization event.
     */
    private void handleCaseDeletedEvent(AutoIngestCaseDeletedEvent event) {
        coordSvcQueryExecutor.submit(new CoordinationServiceQueryTask());
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
     * Makes the auto ingest monitor's refresh its current snapshot of the
     * pending jobs queue, running jobs list, and completed jobs list for an
     * auto ingest cluster.
     *
     * @return The refreshed snapshot.
     */
    JobsSnapshot refreshJobsSnapshot() {
        JobsSnapshot newJobsSnapshot = queryCoordinationService();
        synchronized (jobsLock) {
            jobsSnapshot = newJobsSnapshot;
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
                }
            }
            return newJobsSnapshot;
        } catch (CoordinationServiceException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get node list from coordination service", ex);
            return new JobsSnapshot();
        }
    }

    /**
     * Bumps the priority of an auto ingest job.
     *
     * @param job The job to be prioritized.
     */
    JobsSnapshot prioritizeJob(AutoIngestJob job) throws AutoIngestMonitorException {
        int highestPriority = 0;
        AutoIngestJob prioritizedJob = null;
        synchronized (jobsLock) {
            /*
             * Get the highest known priority and make sure the job is still in
             * the pending jobs queue.
             */
            for (AutoIngestJob pendingJob : jobsSnapshot.getPendingJobs()) {
                if (pendingJob.getPriority() > highestPriority) {
                    highestPriority = pendingJob.getPriority();
                }
                if (pendingJob.equals(job)) {
                    prioritizedJob = job;
                }
            }

            /*
             * If the job was still in the pending jobs queue, bump its
             * priority.
             */
            if (null != prioritizedJob) {
                ++highestPriority;
                String manifestNodePath = job.getManifest().getFilePath().toString();
                try {
                    AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                    nodeData.setPriority(highestPriority);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                } catch (AutoIngestJobNodeData.InvalidDataException | CoordinationServiceException | InterruptedException ex) {
                    throw new AutoIngestMonitorException("Error bumping priority for job " + job.toString(), ex);
                }
                prioritizedJob.setPriority(highestPriority);

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
                JobsSnapshot newJobsSnapshot = queryCoordinationService();
                synchronized (jobsLock) {
                    jobsSnapshot = newJobsSnapshot;
                    setChanged();
                    notifyObservers(null);
                }
            }
        }

    }

    /**
     * A snapshot of the pending jobs queue, running jobs list, and completed
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
         * @param job The auot ingest job.
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
         * @param job The auot ingest job.
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
         * @param job The auot ingest job.
         */
        private void removeCompletedJob(AutoIngestJob job) {
            this.pendingJobs.remove(job);
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
