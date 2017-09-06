/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
import java.io.IOException;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.Stage;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.COMPLETED;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.DELETED;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.PENDING;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.PROCESSING;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;

/**
 *
 * @author dgrove
 */
public final class AutoIngestMonitor extends Observable implements PropertyChangeListener {
    
    private static final int NUM_INPUT_SCAN_SCHEDULING_THREADS = 1;
    private static final String INPUT_SCAN_SCHEDULER_THREAD_NAME = "AIM-input-scan-scheduler-%d";
    private static final String INPUT_SCAN_THREAD_NAME = "AIM-input-scan-%d";
    private static int DEFAULT_JOB_PRIORITY = 0;
    private static final String AUTO_INGEST_THREAD_NAME = "AIM-job-processing-%d";
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();
    private static final String EVENT_CHANNEL_NAME = "Auto-Ingest-Manager-Events";
    private static final Set<String> EVENT_LIST = new HashSet<>(Arrays.asList(new String[]{
        Event.JOB_STATUS_UPDATED.toString(),
        Event.JOB_COMPLETED.toString(),
        Event.CASE_PRIORITIZED.toString(),
        Event.JOB_STARTED.toString()}));
    private static final long JOB_STATUS_EVENT_INTERVAL_SECONDS = 10;
    private static final String JOB_STATUS_PUBLISHING_THREAD_NAME = "AIM-job-status-event-publisher-%d";
    private static final long MAX_MISSED_JOB_STATUS_UPDATES = 10;
    private static final java.util.logging.Logger SYS_LOGGER = AutoIngestSystemLogger.getLogger();
    
    private static AutoIngestMonitor instance;
    private final AutopsyEventPublisher eventPublisher;
    private final Object scanMonitor;
    private final ScheduledThreadPoolExecutor inputScanSchedulingExecutor;
    private final ExecutorService inputScanExecutor;
    private final ExecutorService jobProcessingExecutor;
    private final ScheduledThreadPoolExecutor jobStatusPublishingExecutor;
    private final ConcurrentHashMap<String, Instant> hostNamesToLastMsgTime;
    private final ConcurrentHashMap<String, AutoIngestJob> hostNamesToRunningJobs;
    private final Object jobsLock;
    @GuardedBy("jobsLock") private final Map<String, Set<Path>> casesToManifests;
    @GuardedBy("jobsLock") private List<AutoIngestJob> pendingJobs;
    @GuardedBy("jobsLock") private AutoIngestJob currentJob;
    @GuardedBy("jobsLock") private List<AutoIngestJob> completedJobs;
    private CoordinationService coordinationService;
    private Path rootInputDirectory;
    private Path rootOutputDirectory;
    private volatile State state;
    private volatile ErrorState errorState;

    /**
     * Gets a singleton auto ingest monitor responsible for processing auto
     * ingest jobs defined by manifest files that can be added to any level of a
     * designated input directory tree.
     *
     * @return A singleton AutoIngestMonitor instance.
     */
    synchronized static AutoIngestMonitor getInstance() {
        if (instance == null) {
            instance = new AutoIngestMonitor();
        }
        return instance;
    }
    
    /**
     * Constructs an auto ingest monitor responsible for processing auto ingest
     * jobs defined by manifest files that can be added to any level of a
     * designated input directory tree.
     */
    public AutoIngestMonitor() {
        SYS_LOGGER.log(Level.INFO, "Initializing auto ingest");
        state = State.IDLE;
        eventPublisher = new AutopsyEventPublisher();
        scanMonitor = new Object();
        inputScanSchedulingExecutor = new ScheduledThreadPoolExecutor(NUM_INPUT_SCAN_SCHEDULING_THREADS, new ThreadFactoryBuilder().setNameFormat(INPUT_SCAN_SCHEDULER_THREAD_NAME).build());
        inputScanExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(INPUT_SCAN_THREAD_NAME).build());
        jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(AUTO_INGEST_THREAD_NAME).build());
        jobStatusPublishingExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(JOB_STATUS_PUBLISHING_THREAD_NAME).build());
        hostNamesToRunningJobs = new ConcurrentHashMap<>();
        hostNamesToLastMsgTime = new ConcurrentHashMap<>();
        jobsLock = new Object();
        casesToManifests = new HashMap<>();
        pendingJobs = new ArrayList<>();
        completedJobs = new ArrayList<>();
        rootOutputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeResultsFolder());
        errorState = ErrorState.NONE;
    }

    /**
     * Starts up auto ingest.
     *
     * @throws AutoIngestMonitorStartupException if there is a problem starting
     *                                           auto ingest.
     */
    void startUp() throws AutoIngestMonitor.AutoIngestMonitorStartupException {
        SYS_LOGGER.log(Level.INFO, "Auto ingest starting");
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestMonitorStartupException("Failed to get coordination service", ex);
        }
        try {
            eventPublisher.openRemoteEventChannel(EVENT_CHANNEL_NAME);
            SYS_LOGGER.log(Level.INFO, "Opened auto ingest event channel");
        } catch (AutopsyEventException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Failed to open auto ingest event channel", ex);
            throw new AutoIngestMonitorStartupException("Failed to open auto ingest event channel", ex);
        }
        rootInputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeImageFolder());
        rootOutputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeResultsFolder());
        inputScanSchedulingExecutor.scheduleAtFixedRate(new InputDirScanSchedulingTask(), 0, AutoIngestUserPreferences.getMinutesOfInputScanInterval(), TimeUnit.MINUTES);
        jobStatusPublishingExecutor.scheduleAtFixedRate(new PeriodicJobStatusEventTask(), JOB_STATUS_EVENT_INTERVAL_SECONDS, JOB_STATUS_EVENT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        eventPublisher.addSubscriber(EVENT_LIST, instance);
        state = State.RUNNING;
        errorState = ErrorState.NONE;
    }

    /**
     * Gets the error state of the autop ingest monitor.
     *
     * @return The error state, may be NONE.
     */
    ErrorState getErrorState() {
        return errorState;
    }

    /**
     * Handles auto ingest events published by other auto ingest nodes.
     *
     * @param event An auto ingest event from another node.
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event instanceof AutopsyEvent) {
            if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                if (event instanceof AutoIngestJobStartedEvent) {
                    handleRemoteJobStartedEvent((AutoIngestJobStartedEvent) event);
                } else if (event instanceof AutoIngestJobStatusEvent) {
                    handleRemoteJobStatusEvent((AutoIngestJobStatusEvent) event);
                } else if (event instanceof AutoIngestJobCompletedEvent) {
                    handleRemoteJobCompletedEvent((AutoIngestJobCompletedEvent) event);
                } else if (event instanceof AutoIngestCasePrioritizedEvent) {
                    handleRemoteCasePrioritizationEvent((AutoIngestCasePrioritizedEvent) event);
                } else if (event instanceof AutoIngestCaseDeletedEvent) {
                    handleRemoteCaseDeletedEvent((AutoIngestCaseDeletedEvent) event);
                }
            }
        }
    }

    /**
     * Processes a job started event from another node by removing the job from
     * the pending queue, if it is present, and adding the job in the event to
     * the collection of jobs running on other hosts.
     * <p>
     * Note that the processing stage of the job will be whatever it was when
     * the job was serialized for inclusion in the event message.
     *
     * @param event A job started from another auto ingest node.
     */
    private void handleRemoteJobStartedEvent(AutoIngestJobStartedEvent event) {
        String hostName = event.getJob().getNodeName();
        hostNamesToLastMsgTime.put(hostName, Instant.now());
        synchronized (jobsLock) {
            Path manifestFilePath = event.getJob().getNodeData().getManifestFilePath();
            for (Iterator<AutoIngestJob> iterator = pendingJobs.iterator(); iterator.hasNext();) {
                AutoIngestJob pendingJob = iterator.next();
                if (pendingJob.getNodeData().getManifestFilePath().equals(manifestFilePath)) {
                    iterator.remove();
                    break;
                }
            }
        }
        hostNamesToRunningJobs.put(event.getJob().getNodeName(), event.getJob());
        setChanged();
        notifyObservers(Event.JOB_STARTED);
    }

    /**
     * Processes a job status event from another node by adding the job in the
     * event to the collection of jobs running on other hosts.
     * <p>
     * Note that the processing stage of the job will be whatever it was when
     * the job was serialized for inclusion in the event message.
     *
     * @param event An job status event from another auto ingest node.
     */
    private void handleRemoteJobStatusEvent(AutoIngestJobStatusEvent event) {
        String hostName = event.getJob().getNodeName();
        hostNamesToLastMsgTime.put(hostName, Instant.now());
        hostNamesToRunningJobs.put(hostName, event.getJob());
        setChanged();
        notifyObservers(Event.JOB_STATUS_UPDATED);
    }

    /**
     * Processes a job completed event from another node by removing the job in
     * the event from the collection of jobs running on other hosts and adding
     * it to the list of completed jobs.
     * <p>
     * Note that the processing stage of the job will be whatever it was when
     * the job was serialized for inclusion in the event message.
     *
     * @param event An job completed event from another auto ingest node.
     */
    private void handleRemoteJobCompletedEvent(AutoIngestJobCompletedEvent event) {
        String hostName = event.getJob().getNodeName();
        hostNamesToLastMsgTime.put(hostName, Instant.now());
        hostNamesToRunningJobs.remove(hostName);
        if (event.shouldRetry() == false) {
            synchronized (jobsLock) {
                completedJobs.add(event.getJob());
            }
        }
        //scanInputDirsNow();
        setChanged();
        notifyObservers(Event.JOB_COMPLETED);
    }

    /**
     * Processes a job/case prioritization event from another node by triggering
     * an immediate input directory scan.
     *
     * @param event A prioritization event from another auto ingest node.
     */
    private void handleRemoteCasePrioritizationEvent(AutoIngestCasePrioritizedEvent event) {
        String hostName = event.getNodeName();
        hostNamesToLastMsgTime.put(hostName, Instant.now());
        scanInputDirsNow();
        setChanged();
        notifyObservers(Event.CASE_PRIORITIZED);
    }

    /**
     * Processes a case deletin event from another node by triggering an
     * immediate input directory scan.
     *
     * @param event A case deleted event from another auto ingest node.
     */
    private void handleRemoteCaseDeletedEvent(AutoIngestCaseDeletedEvent event) {
        String hostName = event.getNodeName();
        hostNamesToLastMsgTime.put(hostName, Instant.now());
        scanInputDirsNow();
        setChanged();
        notifyObservers(Event.CASE_DELETED);
    }

    /**
     * Gets snapshots of the pending jobs queue, running jobs list, and
     * completed jobs list. Any of these collection can be excluded by passing a
     * null for the correspioding in/out list parameter.
     *
     * @param pendingJobs   A list to be populated with pending jobs, can be
     *                      null.
     * @param runningJobs   A list to be populated with running jobs, can be
     *                      null.
     * @param completedJobs A list to be populated with competed jobs, can be
     *                      null.
     */
    void getJobs(List<AutoIngestJob> pendingJobs, List<AutoIngestJob> runningJobs, List<AutoIngestJob> completedJobs) {
        synchronized (jobsLock) {
            if (null != pendingJobs) {
                pendingJobs.clear();
                pendingJobs.addAll(this.pendingJobs);
            }
            if (null != runningJobs) {
                runningJobs.clear();
                if (null != currentJob) {
                    runningJobs.add(currentJob);
                }
                for (AutoIngestJob job : hostNamesToRunningJobs.values()) {
                    runningJobs.add(job);
                    runningJobs.sort(new AutoIngestJob.AlphabeticalComparator());
                }
            }
            if (null != completedJobs) {
                completedJobs.clear();
                completedJobs.addAll(this.completedJobs);
            }
        }
    }

    /**
     * An instance of this runnable is responsible for periodically sending auto
     * ingest job status event to remote auto ingest nodes and timing out stale
     * remote jobs. The auto ingest job status event is sent only if auto ingest
     * monitor has a currently running auto ingest job.
     */
    private final class PeriodicJobStatusEventTask implements Runnable {

        private final long MAX_SECONDS_WITHOUT_UPDATE = JOB_STATUS_EVENT_INTERVAL_SECONDS * MAX_MISSED_JOB_STATUS_UPDATES;

        private PeriodicJobStatusEventTask() {
            SYS_LOGGER.log(Level.INFO, "Periodic status publishing task started");
        }

        @Override
        public void run() {

            try {
                synchronized (jobsLock) {
                    if (currentJob != null) {
                        setChanged();
                        notifyObservers(AutoIngestMonitor.Event.JOB_STATUS_UPDATED);
                        eventPublisher.publishRemotely(new AutoIngestJobStatusEvent(currentJob));
                    }

                    if (AutoIngestUserPreferences.getStatusDatabaseLoggingEnabled()) {
                        String message;
                        boolean isError = false;
                        if (getErrorState().equals(AutoIngestMonitor.ErrorState.NONE)) {
                            if (currentJob != null) {
                                message = "Processing " + currentJob.getNodeData().getDataSourceFileName()
                                        + " for case " + currentJob.getNodeData().getCaseName();
                            } else {
                                message = "Paused or waiting for next case";
                            }
                        } else {
                            message = getErrorState().toString();
                            isError = true;
                        }
                        try {
                            StatusDatabaseLogger.logToStatusDatabase(message, isError);
                        } catch (SQLException | UserPreferencesException ex) {
                            SYS_LOGGER.log(Level.WARNING, "Failed to update status database", ex);
                        }
                    }
                }

                // check whether any remote nodes have timed out
                for (AutoIngestJob job : hostNamesToRunningJobs.values()) {
                    if (isStale(hostNamesToLastMsgTime.get(job.getNodeName()))) {
                        // remove the job from remote job running map.
                        /*
                         * NOTE: there is theoretically a check-then-act race
                         * condition but I don't it's worth introducing another
                         * lock because of it. If a job status update is
                         * received after we check the last message fileTime
                         * stamp (i.e. "check") but before we remove the remote
                         * job (i.e. "act") then the remote job will get added
                         * back into hostNamesToRunningJobs as a result of
                         * processing the job status update.
                         */
                        SYS_LOGGER.log(Level.WARNING, "Auto ingest node {0} timed out while processing folder {1}",
                                new Object[]{job.getNodeName(), job.getNodeData().getManifestFilePath().toString()});
                        hostNamesToRunningJobs.remove(job.getNodeName());
                        setChanged();
                        notifyObservers(AutoIngestMonitor.Event.JOB_COMPLETED);
                    }
                }

            } catch (Exception ex) {
                SYS_LOGGER.log(Level.SEVERE, "Unexpected exception in PeriodicJobStatusEventTask", ex); //NON-NLS
            }
        }

        /**
         * Determines whether or not the fileTime since the last message from
         * node is greater than the maximum acceptable interval between
         * messages.
         *
         * @return True or false.
         */
        boolean isStale(Instant lastUpdateTime) {
            return (Duration.between(lastUpdateTime, Instant.now()).toMillis() / 1000 > MAX_SECONDS_WITHOUT_UPDATE);
        }
    }

    /**
     * Triggers an immediate scan of the input directories.
     */
    void scanInputDirsNow() {
        if (State.RUNNING != state) {
            return;
        }
        inputScanExecutor.submit(new InputDirScanTask());
    }

    /**
     * Start a scan of the input directories and wait for scan to complete.
     */
    void scanInputDirsAndWait() {
        if (State.RUNNING != state) {
            return;
        }
        SYS_LOGGER.log(Level.INFO, "Starting input scan of {0}", rootInputDirectory);
        InputDirScanner scanner = new InputDirScanner();
        scanner.scan();
        SYS_LOGGER.log(Level.INFO, "Completed input scan of {0}", rootInputDirectory);
    }

    /**
     * Bumps the priority of all pending ingest jobs for a specified case.
     *
     * @param caseName The name of the case to be prioritized.
     */
    void prioritizeCase(final String caseName) {

        if (state != State.RUNNING) {
            return;
        }

        List<AutoIngestJob> prioritizedJobs = new ArrayList<>();
        int maxPriority = 0;
        synchronized (jobsLock) {
            for (AutoIngestJob job : pendingJobs) {
                if (job.getNodeData().getPriority() > maxPriority) {
                    maxPriority = job.getNodeData().getPriority();
                }
                if (job.getNodeData().getCaseName().equals(caseName)) {
                    prioritizedJobs.add(job);
                }
            }
            if (!prioritizedJobs.isEmpty()) {
                ++maxPriority;
                for (AutoIngestJob job : prioritizedJobs) {
                    String manifestNodePath = job.getNodeData().getManifestFilePath().toString();
                    try {
                        ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                        nodeData.setPriority(maxPriority);
                        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                    } catch (ManifestNodeDataException ex) {
                        SYS_LOGGER.log(Level.WARNING, String.format("Unable to use node data for %s", manifestNodePath), ex);
                    } catch (CoordinationServiceException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Coordination service error while prioritizing %s", manifestNodePath), ex);
                    } catch (InterruptedException ex) {
                        SYS_LOGGER.log(Level.SEVERE, "Unexpected interrupt while updating coordination service node data for {0}", manifestNodePath);
                    }
                    job.getNodeData().setPriority(maxPriority);
                }
            }

            Collections.sort(pendingJobs, new AutoIngestJob.PriorityComparator());
        }

        if (!prioritizedJobs.isEmpty()) {
            new Thread(() -> {
                eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
            }).start();
        }
    }

    /**
     * Bumps the priority of an auto ingest job.
     *
     * @param manifestPath The manifest file path for the job to be prioritized.
     */
    void prioritizeJob(Path manifestPath) {
        if (state != State.RUNNING) {
            return;
        }

        int maxPriority = 0;
        AutoIngestJob prioritizedJob = null;
        synchronized (jobsLock) {
            for (AutoIngestJob job : pendingJobs) {
                if (job.getNodeData().getPriority() > maxPriority) {
                    maxPriority = job.getNodeData().getPriority();
                }
                if (job.getNodeData().getManifestFilePath().equals(manifestPath)) {
                    prioritizedJob = job;
                }
            }
            if (null != prioritizedJob) {
                ++maxPriority;
                String manifestNodePath = prioritizedJob.getNodeData().getManifestFilePath().toString();
                try {
                    ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                    nodeData.setPriority(maxPriority);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                } catch (ManifestNodeDataException ex) {
                    SYS_LOGGER.log(Level.WARNING, String.format("Unable to use node data for %s", manifestPath), ex);
                } catch (CoordinationServiceException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Coordination service error while prioritizing %s", manifestNodePath), ex);
                } catch (InterruptedException ex) {
                    SYS_LOGGER.log(Level.SEVERE, "Unexpected interrupt while updating coordination service node data for {0}", manifestNodePath);
                }
                prioritizedJob.getNodeData().setPriority(maxPriority);
            }

            Collections.sort(pendingJobs, new AutoIngestJob.PriorityComparator());
        }

        if (null != prioritizedJob) {
            final String caseName = prioritizedJob.getNodeData().getCaseName();
            new Thread(() -> {
                eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
            }).start();
        }
    }

    /**
     * Get the current snapshot of the job lists.
     *
     * @return Snapshot of jobs lists
     */
    JobsSnapshot getCurrentJobsSnapshot() {
        synchronized (jobsLock) {
            List<AutoIngestJob> runningJobs = new ArrayList<>();
            getJobs(null, runningJobs, null);
            return new JobsSnapshot(pendingJobs, runningJobs, completedJobs);
        }
    }

    /**
     * A task that submits an input directory scan task to the input directory
     * scan task executor.
     */
    private final class InputDirScanSchedulingTask implements Runnable {

        /**
         * Constructs a task that submits an input directory scan task to the
         * input directory scan task executor.
         */
        private InputDirScanSchedulingTask() {
            SYS_LOGGER.log(Level.INFO, "Periodic input scan scheduling task started");
        }

        /**
         * Submits an input directory scan task to the input directory scan task
         * executor.
         */
        @Override
        public void run() {
            scanInputDirsNow();
        }
    }

    /**
     * A task that scans the input directory tree and refreshes the pending jobs
     * queue and the completed jobs list. Crashed job recovery is perfomed as
     * needed.
     */
    private final class InputDirScanTask implements Callable<Void> {

        /**
         * Scans the input directory tree and refreshes the pending jobs queue
         * and the completed jobs list. Crashed job recovery is performed as
         * needed.
         */
        @Override
        public Void call() throws Exception {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            SYS_LOGGER.log(Level.INFO, "Starting input scan of {0}", rootInputDirectory);
            InputDirScanner scanner = new InputDirScanner();
            scanner.scan();
            SYS_LOGGER.log(Level.INFO, "Completed input scan of {0}", rootInputDirectory);
            setChanged();
            notifyObservers(Event.INPUT_SCAN_COMPLETED);
            return null;
        }

    }

    /**
     * A FileVisitor that searches the input directories for manifest files. The
     * search results are used to refresh the pending jobs queue and the
     * completed jobs list. Crashed job recovery is performed as needed.
     */
    private final class InputDirScanner {   // DLG: Replace with task that calls regularly / reusable by refresh

        private final List<AutoIngestJob> newPendingJobsList = new ArrayList<>();
        private final List<AutoIngestJob> newCompletedJobsList = new ArrayList<>();
        
        /**
         * Searches the input directories for manifest files. The search results
         * are used to refresh the pending jobs queue and the completed jobs
         * list.
         */
        private void scan() {
            synchronized (jobsLock) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                try {
                    newPendingJobsList.clear();
                    newCompletedJobsList.clear();
                    
                    List<String> manifestList = coordinationService.getNodeList(
                            CoordinationService.CategoryNode.MANIFESTS);
                    
                    for(int i=0; i < manifestList.size(); i++) {
                        visitFile(Paths.get(manifestList.get(i)));  // DLG: Just call CoordinationService
                    }
                    
                    Collections.sort(newPendingJobsList, new AutoIngestJob.PriorityComparator());
                    AutoIngestMonitor.this.pendingJobs = newPendingJobsList;
                    AutoIngestMonitor.this.completedJobs = newCompletedJobsList;

                } catch (Exception ex) {
                    /*
                     * NOTE: Need to catch all exceptions here. Otherwise
                     * uncaught exceptions will propagate up to the calling
                     * thread and may stop it from running.
                     */
                    SYS_LOGGER.log(Level.SEVERE, String.format("Error scanning the input directory %s", rootInputDirectory), ex);
                }
            }
            synchronized (scanMonitor) {
                scanMonitor.notify();
            }
        }
        
        private FileVisitResult visitFile(Path filePath) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }

            Manifest manifest = parseManifestFile(filePath);    // DLG: No longer use this

            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }

            if(manifest != null) {
                String caseName = manifest.getCaseName();
                Path manifestPath = manifest.getFilePath();

                if (casesToManifests.containsKey(caseName)) {
                    Set<Path> manifestPathSet = casesToManifests.get(caseName);
                    manifestPathSet.add(manifestPath);
                } else {
                    Set<Path> manifestPathSet = new HashSet<>();
                    manifestPathSet.add(manifestPath);
                    casesToManifests.put(caseName, manifestPathSet);
                }

                /*
                 * Add a job to the pending jobs queue, the completed jobs list,
                 * or do crashed job recovery, as required.
                 */
                try {
                    addJob(manifest);
                } catch (CoordinationService.CoordinationServiceException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Error getting node data for %s", manifestPath), ex);
                    return CONTINUE;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return TERMINATE;
                }
            }

            if (!Thread.currentThread().isInterrupted()) {
                return CONTINUE;
            }

            return TERMINATE;
        }

        private Manifest parseManifestFile(Path filePath) throws IOException {
            Manifest manifest = null;

            for (ManifestFileParser parser : Lookup.getDefault().lookupAll(ManifestFileParser.class)) {
                if (parser.fileIsManifest(filePath)) {
                    try {
                        manifest = parser.parse(filePath);
                        break;
                    } catch (ManifestFileParser.ManifestFileParserException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to parse %s with parser %s", filePath, parser.getClass().getCanonicalName()), ex);
                    }
                }
            }
            
            return manifest;
        }
        
        /**
         * Add a job to the pending jobs queue, the completed jobs list,
         * or do crashed job recovery, as required.
         */
        private void addJob(Manifest manifest) throws CoordinationService.CoordinationServiceException, InterruptedException {
            Path manifestPath = manifest.getFilePath();
            
            byte[] rawData = coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString());
            if (null != rawData) {
                try {
                    ManifestNodeData nodeData = new ManifestNodeData(rawData);
                    if (nodeData.coordSvcNodeDataWasSet()) {
                        ManifestNodeData.ProcessingStatus processingStatus = nodeData.getStatus();
                        switch (processingStatus) {
                            case PENDING:
                                addPendingJob(nodeData);
                                break;
                            case PROCESSING:
                                doRecoveryIfCrashed(nodeData);
                                break;
                            case COMPLETED:
                                addCompletedJob(nodeData);
                                break;
                            case DELETED:
                                // Do nothing - we dont'want to add it to any job list or do recovery
                                break;
                            default:
                                SYS_LOGGER.log(Level.SEVERE, "Unknown ManifestNodeData.ProcessingStatus");
                                break;
                        }
                    } else {
                        addNewPendingJob(manifest);
                    }
                } catch(ManifestNodeDataException ex) {
                    SYS_LOGGER.log(Level.WARNING, String.format("Unable to use node data for %s", manifestPath), ex);
                }
            } else {
                addNewPendingJob(manifest);
            }
        }

        /**
         * Adds a job to process a manifest to the pending jobs queue.
         *
         * @param nodeData The data stored in the coordination service node for
         *                 the manifest.
         */
        private void addPendingJob(ManifestNodeData nodeData) {
            Path caseDirectory = PathUtils.findCaseDirectory(rootOutputDirectory, nodeData.getCaseName());
            nodeData.setCompletedDate(new Date(0));
            nodeData.setErrorsOccurred(false);
            newPendingJobsList.add(new AutoIngestJob(nodeData, caseDirectory, LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING));
        }

        /**
         * Adds a job to process a manifest to the pending jobs queue.
         *
         * @param manifest The manifest.
         *
         * @throws InterruptedException if the thread running the input
         *                              directory scan task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private void addNewPendingJob(Manifest manifest) throws InterruptedException {
            // TODO (JIRA-1960): This is something of a hack, grabbing the lock to create the node.
            // Is use of Curator.create().forPath() possible instead? 
            try (CoordinationService.Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString())) {
                if (null != manifestLock) {
                    ManifestNodeData newNodeData = new ManifestNodeData(manifest, PENDING, DEFAULT_JOB_PRIORITY, 0, new Date(0), false);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString(), newNodeData.toArray());
                    newPendingJobsList.add(new AutoIngestJob(newNodeData, null, LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING));
                }
            } catch (CoordinationService.CoordinationServiceException ex) {
                SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to set node data for %s", manifest.getFilePath()), ex);
            }
        }

        /**
         * Does crash recovery for a manifest, if required. The criterion for
         * crash recovery is a manifest with coordination service node data
         * indicating it is being processed for which an exclusive lock on the
         * node can be acquired. If this condition is true, it is probable that
         * the node that was processing the job crashed and the processing
         * status was not updated.
         *
         * @param nodeData
         *
         * @throws InterruptedException if the thread running the input
         *                              directory scan task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private void doRecoveryIfCrashed(ManifestNodeData nodeData) throws InterruptedException {
            String manifestPath = nodeData.getManifestFilePath().toString();
            if (nodeData.coordSvcNodeDataWasSet() && ManifestNodeData.ProcessingStatus.PROCESSING == nodeData.getStatus()) {
                SYS_LOGGER.log(Level.SEVERE, "Attempting crash recovery for {0}", manifestPath);
                int numberOfCrashes = nodeData.getNumberOfCrashes();
                ++numberOfCrashes;
                nodeData.setNumberOfCrashes(numberOfCrashes);
                nodeData.setCompletedDate(new Date(0));
                nodeData.setErrorsOccurred(true);
                if (numberOfCrashes <= AutoIngestUserPreferences.getMaxNumTimesToProcessImage()) {
                    nodeData.setStatus(PENDING);
                    Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, nodeData.getCaseName());
                    newPendingJobsList.add(new AutoIngestJob(nodeData, caseDirectoryPath, LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING));
                    if (null != caseDirectoryPath) {
                        try {
                            AutoIngestAlertFile.create(caseDirectoryPath);
                        } catch (AutoIngestAlertFile.AutoIngestAlertFileException ex) {
                            SYS_LOGGER.log(Level.SEVERE, String.format("Error creating alert file for crashed job for %s", manifestPath), ex);
                        }
                        try {
                            new AutoIngestJobLogger(nodeData.getManifestFilePath(), nodeData.getDataSourceFileName(), caseDirectoryPath).logCrashRecoveryWithRetry();
                        } catch (AutoIngestJobLogger.AutoIngestJobLoggerException ex) {
                            SYS_LOGGER.log(Level.SEVERE, String.format("Error creating case auto ingest log entry for crashed job for %s", manifestPath), ex);
                        }
                    }
                } else {
                    nodeData.setStatus(COMPLETED);
                    Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, nodeData.getCaseName());
                    newCompletedJobsList.add(new AutoIngestJob(nodeData, caseDirectoryPath, LOCAL_HOST_NAME, AutoIngestJob.Stage.COMPLETED));
                    if (null != caseDirectoryPath) {
                        try {
                            AutoIngestAlertFile.create(caseDirectoryPath);
                        } catch (AutoIngestAlertFile.AutoIngestAlertFileException ex) {
                            SYS_LOGGER.log(Level.SEVERE, String.format("Error creating alert file for crashed job for %s", manifestPath), ex);
                        }
                        try {
                            new AutoIngestJobLogger(nodeData.getManifestFilePath(), nodeData.getDataSourceFileName(), caseDirectoryPath).logCrashRecoveryNoRetry();
                        } catch (AutoIngestJobLogger.AutoIngestJobLoggerException ex) {
                            SYS_LOGGER.log(Level.SEVERE, String.format("Error creating case auto ingest log entry for crashed job for %s", manifestPath), ex);
                        }
                    }
                }
                try {
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath, nodeData.toArray());
                } catch (CoordinationService.CoordinationServiceException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to set node data for %s", manifestPath), ex);
                }
            }
        }

        /**
         * Adds a job to process a manifest to the completed jobs list.
         *
         * @param nodeData The data stored in the coordination service node for
         *                 the manifest.
         */
        private void addCompletedJob(ManifestNodeData nodeData) {
            Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, nodeData.getCaseName());
            if (null != caseDirectoryPath) {
                newCompletedJobsList.add(new AutoIngestJob(nodeData, caseDirectoryPath, LOCAL_HOST_NAME, AutoIngestJob.Stage.COMPLETED));
            } else {
                SYS_LOGGER.log(Level.WARNING, String.format("Job completed for %s, but cannot find case directory, ignoring job", nodeData.getManifestFilePath()));
            }
        }
    }

    /*
     * The possible states of an auto ingest monitor.
     */
    private enum State {
        IDLE,
        RUNNING,
        SHUTTING_DOWN;
    }

    /*
     * Events published by an auto ingest monitor. The events are published
     * locally to auto ingest monitor clients that register as observers and are
     * broadcast to other auto ingest nodes.
     */
    enum Event {

        INPUT_SCAN_COMPLETED,
        JOB_STARTED,
        JOB_STATUS_UPDATED,
        JOB_COMPLETED,
        CASE_PRIORITIZED,
        CASE_DELETED,
        PAUSED_BY_REQUEST,
        PAUSED_FOR_SYSTEM_ERROR,
        RESUMED
    }

    /**
     * The current auto ingest error state.
     */
    private enum ErrorState {
        NONE("None"),
        COORDINATION_SERVICE_ERROR("Coordination service error"),
        SHARED_CONFIGURATION_DOWNLOAD_ERROR("Shared configuration download error"),
        SERVICES_MONITOR_COMMUNICATION_ERROR("Services monitor communication error"),
        DATABASE_SERVER_ERROR("Database server error"),
        KEYWORD_SEARCH_SERVER_ERROR("Keyword search server error"),
        CASE_MANAGEMENT_ERROR("Case management error"),
        ANALYSIS_STARTUP_ERROR("Analysis startup error"),
        FILE_EXPORT_ERROR("File export error"),
        ALERT_FILE_ERROR("Alert file error"),
        JOB_LOGGER_ERROR("Job logger error"),
        DATA_SOURCE_PROCESSOR_ERROR("Data source processor error"),
        UNEXPECTED_EXCEPTION("Unknown error");

        private final String desc;

        private ErrorState(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return desc;
        }
    }

    /**
     * A snapshot of the pending jobs queue, running jobs list, and completed
     * jobs list.
     */
    static final class JobsSnapshot {

        private final List<AutoIngestJob> pendingJobs;
        private final List<AutoIngestJob> runningJobs;
        private final List<AutoIngestJob> completedJobs;

        /**
         * Constructs a snapshot of the pending jobs queue, running jobs list,
         * and completed jobs list.
         *
         * @param pendingJobs   The pending jobs queue.
         * @param runningJobs   The running jobs list.
         * @param completedJobs The cmopleted jobs list.
         */
        private JobsSnapshot(List<AutoIngestJob> pendingJobs, List<AutoIngestJob> runningJobs, List<AutoIngestJob> completedJobs) {
            this.pendingJobs = new ArrayList<>(pendingJobs);
            this.runningJobs = new ArrayList<>(runningJobs);
            this.completedJobs = new ArrayList<>(completedJobs);
        }

        /**
         * Gets the snapshot of the pending jobs queue.
         *
         * @return The jobs collection.
         */
        List<AutoIngestJob> getPendingJobs() {
            return Collections.unmodifiableList(this.pendingJobs);
        }

        /**
         * Gets the snapshot of the running jobs list.
         *
         * @return The jobs collection.
         */
        List<AutoIngestJob> getRunningJobs() {
            return Collections.unmodifiableList(this.runningJobs);
        }

        /**
         * Gets the snapshot of the completed jobs list.
         *
         * @return The jobs collection.
         */
        List<AutoIngestJob> getCompletedJobs() {
            return Collections.unmodifiableList(this.completedJobs);
        }

    }

    static final class AutoIngestMonitorStartupException extends Exception {

        private static final long serialVersionUID = 1L;

        private AutoIngestMonitorStartupException(String message) {
            super(message);
        }

        private AutoIngestMonitorStartupException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
