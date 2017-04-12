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
import java.io.File;
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
import java.util.Collection;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.Case.IllegalCaseNameException;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.core.ServicesMonitor.ServicesMonitorException;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestAlertFile.AutoIngestAlertFileException;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException;
import org.sleuthkit.autopsy.experimental.autoingest.FileExporter.FileExportException;
import org.sleuthkit.autopsy.experimental.autoingest.ManifestFileParser.ManifestFileParserException;
import org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.COMPLETED;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.DELETED;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.PENDING;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.PROCESSING;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;
import org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration;
import org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration.SharedConfigurationException;
import org.sleuthkit.autopsy.framework.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.framework.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestJob.CancellationReason;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobStartResult;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.datamodel.Content;

/**
 * An auto ingest manager is responsible for processing auto ingest jobs defined
 * by manifest files that can be added to any level of a designated input
 * directory tree.
 * <p>
 * Each manifest file specifies a co-located data source and a case to which the
 * data source is to be added. The case directories for the cases reside in a
 * designated output directory tree.
 * <p>
 * There should be at most one auto ingest manager per host (auto ingest node).
 * Multiple auto ingest nodes may be combined to form an auto ingest cluster.
 * The activities of the auto ingest nodes in a cluster are coordinated by way
 * of a coordination service and the nodes communicate via event messages.
 */
public final class AutoIngestManager extends Observable implements PropertyChangeListener {

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
    private static AutoIngestManager instance;
    private final AutopsyEventPublisher eventPublisher;
    private final Object scanMonitor;
    private final ScheduledThreadPoolExecutor inputScanSchedulingExecutor;
    private final ExecutorService inputScanExecutor;
    private final ExecutorService jobProcessingExecutor;
    private final ScheduledThreadPoolExecutor jobStatusPublishingExecutor;
    private final ConcurrentHashMap<String, Instant> hostNamesToLastMsgTime;
    private final ConcurrentHashMap<String, AutoIngestJob> hostNamesToRunningJobs;
    private final Object jobsLock;
    @GuardedBy("jobsLock")
    private final Map<String, Set<Path>> casesToManifests;
    @GuardedBy("jobsLock")
    private List<AutoIngestJob> pendingJobs;
    @GuardedBy("jobsLock")
    private AutoIngestJob currentJob;
    @GuardedBy("jobsLock")
    private List<AutoIngestJob> completedJobs;
    private CoordinationService coordinationService;
    private JobProcessingTask jobProcessingTask;
    private Future<?> jobProcessingTaskFuture;
    private Path rootInputDirectory;
    private Path rootOutputDirectory;
    private volatile State state;
    private volatile ErrorState errorState;

    /**
     * Gets a singleton auto ingest manager responsible for processing auto
     * ingest jobs defined by manifest files that can be added to any level of a
     * designated input directory tree.
     *
     * @return A singleton AutoIngestManager instance.
     */
    synchronized static AutoIngestManager getInstance() {
        if (instance == null) {
            instance = new AutoIngestManager();
        }
        return instance;
    }

    /**
     * Constructs an auto ingest manager responsible for processing auto ingest
     * jobs defined by manifest files that can be added to any level of a
     * designated input directory tree.
     */
    private AutoIngestManager() {
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
        try {
            RuntimeProperties.setRunningWithGUI(false);
            SYS_LOGGER.log(Level.INFO, "Set running with desktop GUI runtime property to false");
        } catch (RuntimeProperties.RuntimePropertiesException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Failed to set running with desktop GUI runtime property to false", ex);
        }
    }

    /**
     * Starts up auto ingest.
     *
     * @throws AutoIngestManagerStartupException if there is a problem starting
     *                                           auto ingest.
     */
    void startUp() throws AutoIngestManagerStartupException {
        SYS_LOGGER.log(Level.INFO, "Auto ingest starting");
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestManagerStartupException("Failed to get coordination service", ex);
        }
        try {
            eventPublisher.openRemoteEventChannel(EVENT_CHANNEL_NAME);
            SYS_LOGGER.log(Level.INFO, "Opened auto ingest event channel");
        } catch (AutopsyEventException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Failed to open auto ingest event channel", ex);
            throw new AutoIngestManagerStartupException("Failed to open auto ingest event channel", ex);
        }
        rootInputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeImageFolder());
        rootOutputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeResultsFolder());
        inputScanSchedulingExecutor.scheduleAtFixedRate(new InputDirScanSchedulingTask(), 0, AutoIngestUserPreferences.getMinutesOfInputScanInterval(), TimeUnit.MINUTES);
        jobProcessingTask = new JobProcessingTask();
        jobProcessingTaskFuture = jobProcessingExecutor.submit(jobProcessingTask);
        jobStatusPublishingExecutor.scheduleAtFixedRate(new PeriodicJobStatusEventTask(), JOB_STATUS_EVENT_INTERVAL_SECONDS, JOB_STATUS_EVENT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        eventPublisher.addSubscriber(EVENT_LIST, instance);
        state = State.RUNNING;
        errorState = ErrorState.NONE;
    }

    /**
     * Gets the state of the auto ingest manager: idle, running, shutting dowm.
     *
     * @return The state.
     */
    State getState() {
        return state;
    }

    /**
     * Gets the error state of the autop ingest manager.
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
            Path manifestFilePath = event.getJob().getManifest().getFilePath();
            for (Iterator<AutoIngestJob> iterator = pendingJobs.iterator(); iterator.hasNext();) {
                AutoIngestJob pendingJob = iterator.next();
                if (pendingJob.getManifest().getFilePath().equals(manifestFilePath)) {
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
     * Shuts down auto ingest.
     */
    void shutDown() {
        if (State.RUNNING != state) {
            return;
        }
        SYS_LOGGER.log(Level.INFO, "Auto ingest shutting down");
        state = State.SHUTTING_DOWN;
        try {
            eventPublisher.removeSubscriber(EVENT_LIST, instance);
            stopInputFolderScans();
            stopJobProcessing();
            eventPublisher.closeRemoteEventChannel();
            cleanupJobs();

        } catch (InterruptedException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Auto ingest interrupted during shut down", ex);
        }
        SYS_LOGGER.log(Level.INFO, "Auto ingest shut down");
        state = State.IDLE;
    }

    /**
     * Cancels any input scan scheduling tasks and input scan tasks and shuts
     * down their executors.
     */
    private void stopInputFolderScans() throws InterruptedException {
        inputScanSchedulingExecutor.shutdownNow();
        inputScanExecutor.shutdownNow();
        while (!inputScanSchedulingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            SYS_LOGGER.log(Level.WARNING, "Auto ingest waited at least thirty seconds for input scan scheduling executor to shut down, continuing to wait"); //NON-NLS
        }
        while (!inputScanExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            SYS_LOGGER.log(Level.WARNING, "Auto ingest waited at least thirty seconds for input scan executor to shut down, continuing to wait"); //NON-NLS
        }
    }

    /**
     * Cancels the job processing task and shuts down its executor.
     */
    private void stopJobProcessing() throws InterruptedException {
        synchronized (jobsLock) {
            if (null != currentJob) {
                cancelCurrentJob();
            }
            jobProcessingTaskFuture.cancel(true);
            jobProcessingExecutor.shutdown();
        }
        while (!jobProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            SYS_LOGGER.log(Level.WARNING, "Auto ingest waited at least thirty seconds for job processing executor to shut down, continuing to wait"); //NON-NLS
        }
    }

    /**
     * Clears the job lists and resets the current job to null.
     */
    private void cleanupJobs() {
        synchronized (jobsLock) {
            pendingJobs.clear();
            currentJob = null;
            completedJobs.clear();
        }
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
     * Pauses processing of the pending jobs queue. The currently running job
     * will continue to run to completion.
     */
    void pause() {
        if (State.RUNNING != state) {
            return;
        }
        jobProcessingTask.requestPause();
    }

    /**
     * Resumes processing of the pending jobs queue.
     */
    void resume() {
        if (State.RUNNING != state) {
            return;
        }
        jobProcessingTask.requestResume();
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
                if (job.getPriority() > maxPriority) {
                    maxPriority = job.getPriority();
                }
                if (job.getManifest().getCaseName().equals(caseName)) {
                    prioritizedJobs.add(job);
                }
            }
            if (!prioritizedJobs.isEmpty()) {
                ++maxPriority;
                for (AutoIngestJob job : prioritizedJobs) {
                    String manifestNodePath = job.getManifest().getFilePath().toString();
                    try {
                        ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                        nodeData.setPriority(maxPriority);
                        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                    } catch (CoordinationServiceException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Coordination service error while prioritizing %s", manifestNodePath), ex);
                    } catch (InterruptedException ex) {
                        SYS_LOGGER.log(Level.SEVERE, "Unexpected interrupt while updating coordination service node data for {0}", manifestNodePath);
                    }
                    job.setPriority(maxPriority);
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
                if (job.getPriority() > maxPriority) {
                    maxPriority = job.getPriority();
                }
                if (job.getManifest().getFilePath().equals(manifestPath)) {
                    prioritizedJob = job;
                }
            }
            if (null != prioritizedJob) {
                ++maxPriority;
                String manifestNodePath = prioritizedJob.getManifest().getFilePath().toString();
                try {
                    ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath));
                    nodeData.setPriority(maxPriority);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
                } catch (CoordinationServiceException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Coordination service error while prioritizing %s", manifestNodePath), ex);
                } catch (InterruptedException ex) {
                    SYS_LOGGER.log(Level.SEVERE, "Unexpected interrupt while updating coordination service node data for {0}", manifestNodePath);
                }
                prioritizedJob.setPriority(maxPriority);
            }

            Collections.sort(pendingJobs, new AutoIngestJob.PriorityComparator());
        }

        if (null != prioritizedJob) {
            final String caseName = prioritizedJob.getManifest().getCaseName();
            new Thread(() -> {
                eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
            }).start();
        }
    }

    /**
     * Reprocesses a completed auto ingest job.
     *
     * @param manifestPath The manifiest file path for the completed job.
     *
     */
    void reprocessJob(Path manifestPath) {
        AutoIngestJob completedJob = null;
        synchronized (jobsLock) {
            for (Iterator<AutoIngestJob> iterator = completedJobs.iterator(); iterator.hasNext();) {
                AutoIngestJob job = iterator.next();
                if (job.getManifest().getFilePath().equals(manifestPath)) {
                    completedJob = job;
                    iterator.remove();
                    break;
                }
            }

            if (null != completedJob && null != completedJob.getCaseDirectoryPath()) {
                try {
                    ManifestNodeData nodeData = new ManifestNodeData(PENDING, DEFAULT_JOB_PRIORITY, 0, new Date(0), true);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString(), nodeData.toArray());
                    pendingJobs.add(new AutoIngestJob(completedJob.getManifest(), completedJob.getCaseDirectoryPath(), DEFAULT_JOB_PRIORITY, LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING, new Date(0), true));
                } catch (CoordinationServiceException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Coordination service error while reprocessing %s", manifestPath), ex);
                    completedJobs.add(completedJob);
                } catch (InterruptedException ex) {
                    SYS_LOGGER.log(Level.SEVERE, "Unexpected interrupt while updating coordination service node data for {0}", manifestPath);
                    completedJobs.add(completedJob);
                }
            }

            Collections.sort(pendingJobs, new AutoIngestJob.PriorityComparator());
        }
    }

    /**
     * Deletes a case. This includes deleting the case directory, the text
     * index, and the case database. This does not include the directories
     * containing the data sources and their manifests.
     *
     * @param caseName          The name of the case.
     * @param caseDirectoryPath The path to the case directory.
     *
     * @return A result code indicating success, partial success, or failure.
     */
    CaseDeletionResult deleteCase(String caseName, Path caseDirectoryPath) {
        if (state != State.RUNNING) {
            return CaseDeletionResult.FAILED;
        }

        CaseDeletionResult result = CaseDeletionResult.FULLY_DELETED;
        List<Lock> manifestFileLocks = new ArrayList<>();
        try {
            synchronized (jobsLock) {
                /*
                 * Get the case metadata.
                 */
                CaseMetadata metaData;
                Path caseMetaDataFilePath = Paths.get(caseDirectoryPath.toString(), caseName + CaseMetadata.getFileExtension());
                try {
                    metaData = new CaseMetadata(caseMetaDataFilePath);
                } catch (CaseMetadata.CaseMetadataException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Failed to get case metadata file %s for case %s at %s", caseMetaDataFilePath, caseName, caseDirectoryPath), ex);
                    return CaseDeletionResult.FAILED;
                }

                /*
                 * Do a fresh input directory scan.
                 */
                InputDirScanner scanner = new InputDirScanner();
                scanner.scan();
                Set<Path> manifestPaths = casesToManifests.get(caseName);
                if (null == manifestPaths) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("No manifest paths found for case %s at %s", caseName, caseDirectoryPath));
                    return CaseDeletionResult.FAILED;
                }

                /*
                 * Get exclusive locks on all of the manifests for the case.
                 * This will exclude other auot ingest nodes from doing anything
                 * with the case.
                 */
                for (Path manifestPath : manifestPaths) {
                    try {
                        Lock lock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString());
                        if (null != lock) {
                            manifestFileLocks.add(lock);
                        } else {
                            return CaseDeletionResult.FAILED;
                        }
                    } catch (CoordinationServiceException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to acquire manifest lock for %s for case %s at %s", manifestPath, caseName, caseDirectoryPath), ex);
                        return CaseDeletionResult.FAILED;
                    }
                }

                try {
                    /*
                     * Physically delete the case.
                     */
                    Case.deleteCase(metaData);
                } catch (CaseActionException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Failed to physically delete case %s at %s", caseName, caseDirectoryPath), ex);
                    return CaseDeletionResult.FAILED;
                }

                /*
                 * Mark each job (manifest file) as deleted
                 */
                for (Path manifestPath : manifestPaths) {
                    try {
                        ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString()));
                        nodeData.setStatus(ManifestNodeData.ProcessingStatus.DELETED);
                        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString(), nodeData.toArray());
                    } catch (InterruptedException | CoordinationServiceException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to set delete flag on manifest data for %s for case %s at %s", manifestPath, caseName, caseDirectoryPath), ex);
                        return CaseDeletionResult.PARTIALLY_DELETED;
                    }
                }

                /*
                 * Remove the jobs for the case from the pending jobs queue and
                 * completed jobs list.
                 */
                removeJobs(manifestPaths, pendingJobs);
                removeJobs(manifestPaths, completedJobs);
                casesToManifests.remove(caseName);
            }

            eventPublisher.publishRemotely(new AutoIngestCaseDeletedEvent(caseName, LOCAL_HOST_NAME));
            setChanged();
            notifyObservers(Event.CASE_DELETED);
            return result;

        } finally {
            /*
             * Always release the manifest locks, regardless of the outcome.
             */
            for (Lock lock : manifestFileLocks) {
                try {
                    lock.release();
                } catch (CoordinationServiceException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Failed to release manifest file lock when deleting case %s at %s", caseName, caseDirectoryPath), ex);
                }
            }
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
     * Removes a set of auto ingest jobs from a collection of jobs.
     *
     * @param manifestPaths The manifest file paths for the jobs.
     * @param jobs          The collection of jobs.
     */
    private void removeJobs(Set<Path> manifestPaths, List<AutoIngestJob> jobs) {
        for (Iterator<AutoIngestJob> iterator = jobs.iterator(); iterator.hasNext();) {
            AutoIngestJob job = iterator.next();
            Path manifestPath = job.getManifest().getFilePath();
            if (manifestPaths.contains(manifestPath)) {
                iterator.remove();
            }
        }
    }

    /**
     * Starts the process of cancelling the current job.
     *
     * Note that the current job is included in the running list for a while
     * because it can take some time for the automated ingest process for the
     * job to be shut down in an orderly fashion.
     */
    void cancelCurrentJob() {
        if (State.RUNNING != state) {
            return;
        }
        synchronized (jobsLock) {
            if (null != currentJob) {
                currentJob.cancel();
                SYS_LOGGER.log(Level.INFO, "Cancelling automated ingest for manifest {0}", currentJob.getManifest().getFilePath());
            }
        }
    }

    /**
     * Cancels the currently running data-source-level ingest module for the
     * current job.
     */
    void cancelCurrentDataSourceLevelIngestModule() {
        if (State.RUNNING != state) {
            return;
        }
        synchronized (jobsLock) {
            if (null != currentJob) {
                IngestJob ingestJob = currentJob.getIngestJob();
                if (null != ingestJob) {
                    IngestJob.DataSourceIngestModuleHandle moduleHandle = ingestJob.getSnapshot().runningDataSourceIngestModule();
                    if (null != moduleHandle) {
                        currentJob.setStage(AutoIngestJob.Stage.CANCELLING_MODULE);
                        moduleHandle.cancel();
                        SYS_LOGGER.log(Level.INFO, "Cancelling {0} module for manifest {1}", new Object[]{moduleHandle.displayName(), currentJob.getManifest().getFilePath()});
                    }
                }
            }
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
     * completed jobs list. Crashed job recovery is perfomed as needed.
     */
    private final class InputDirScanner implements FileVisitor<Path> {

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
                    Files.walkFileTree(rootInputDirectory, EnumSet.of(FOLLOW_LINKS), Integer.MAX_VALUE, this);
                    Collections.sort(newPendingJobsList, new AutoIngestJob.PriorityComparator());
                    AutoIngestManager.this.pendingJobs = newPendingJobsList;
                    AutoIngestManager.this.completedJobs = newCompletedJobsList;

                } catch (IOException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Error scanning the input directory %s", rootInputDirectory), ex);
                }
            }
            synchronized (scanMonitor) {
                scanMonitor.notify();
            }
        }

        /**
         * Invoked for an input directory before entries in the directory are
         * visited. Checks if the task thread has been interrupted because auto
         * ingest is shutting down and terminates the scan if that is the case.
         *
         * @param dirPath  The directory about to be visited.
         * @param dirAttrs The basic file attributes of the directory about to
         *                 be visited.
         *
         * @return TERMINATE if the task thread has been interrupted, CONTINUE
         *         if it has not.
         *
         * @throws IOException if an I/O error occurs, but this implementation
         *                     does not throw.
         */
        @Override
        public FileVisitResult preVisitDirectory(Path dirPath, BasicFileAttributes dirAttrs) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }
            return CONTINUE;
        }

        /**
         * Invoked for a file in a directory. If the file is a manifest file,
         * creates a pending pending or completed auto ingest job for the
         * manifest, based on the data stored in the coordination service node
         * for the manifest.
         * <p>
         * Note that the mapping of case names to manifest paths that is used
         * for case deletion is updated as well.
         *
         * @param filePath The path of the file.
         * @param attrs    The file system attributes of the file.
         *
         * @return TERMINATE if auto ingest is shutting down, CONTINUE if it has
         *         not.
         *
         * @throws IOException if an I/O error occurs, but this implementation
         *                     does not throw.
         */
        @Override
        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }

            Manifest manifest = null;
            for (ManifestFileParser parser : Lookup.getDefault().lookupAll(ManifestFileParser.class)) {
                if (parser.fileIsManifest(filePath)) {
                    try {
                        manifest = parser.parse(filePath);
                        break;
                    } catch (ManifestFileParserException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to parse %s with parser %s", filePath, parser.getClass().getCanonicalName()), ex);
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    return TERMINATE;
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }

            if (null != manifest) {
                /*
                 * Update the mapping of case names to manifest paths that is
                 * used for case deletion.
                 */
                String caseName = manifest.getCaseName();
                Path manifestPath = manifest.getFilePath();
                if (casesToManifests.containsKey(caseName)) {
                    Set<Path> manifestPaths = casesToManifests.get(caseName);
                    manifestPaths.add(manifestPath);
                } else {
                    Set<Path> manifestPaths = new HashSet<>();
                    manifestPaths.add(manifestPath);
                    casesToManifests.put(caseName, manifestPaths);
                }

                /*
                 * Add a job to the pending jobs queue, the completed jobs list,
                 * or do crashed job recovery, as required.
                 */
                try {
                    byte[] rawData = coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString());
                    if (null != rawData) {
                        ManifestNodeData nodeData = new ManifestNodeData(rawData);
                        if (nodeData.coordSvcNodeDataWasSet()) {
                            ProcessingStatus processingStatus = nodeData.getStatus();
                            switch (processingStatus) {
                                case PENDING:
                                    addPendingJob(manifest, nodeData);
                                    break;
                                case PROCESSING:
                                    doRecoveryIfCrashed(manifest);
                                    break;
                                case COMPLETED:
                                    addCompletedJob(manifest, nodeData);
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
                    } else {
                        addNewPendingJob(manifest);
                    }
                } catch (CoordinationServiceException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Error getting node data for %s", manifestPath), ex);
                    return CONTINUE;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return TERMINATE;
                }
            }

            if (!Thread.currentThread().isInterrupted()) {
                return CONTINUE;
            } else {
                return TERMINATE;
            }
        }

        /**
         * Adds a job to process a manifest to the pending jobs queue.
         *
         * @param manifest The manifest.
         * @param nodeData The data stored in the coordination service node for
         *                 the manifest.
         */
        private void addPendingJob(Manifest manifest, ManifestNodeData nodeData) {
            Path caseDirectory = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
            newPendingJobsList.add(new AutoIngestJob(manifest, caseDirectory, nodeData.getPriority(), LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING, new Date(0), false));
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
            try (Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString())) {
                if (null != manifestLock) {
                    ManifestNodeData newNodeData = new ManifestNodeData(PENDING, DEFAULT_JOB_PRIORITY, 0, new Date(0), false);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString(), newNodeData.toArray());
                    newPendingJobsList.add(new AutoIngestJob(manifest, null, DEFAULT_JOB_PRIORITY, LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING, new Date(0), false));
                }
            } catch (CoordinationServiceException ex) {
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
         * @param manifest
         *
         * @throws InterruptedException if the thread running the input
         *                              directory scan task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private void doRecoveryIfCrashed(Manifest manifest) throws InterruptedException {
            String manifestPath = manifest.getFilePath().toString();
            try {
                Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath);
                if (null != manifestLock) {
                    try {
                        ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath));
                        if (nodeData.coordSvcNodeDataWasSet() && ProcessingStatus.PROCESSING == nodeData.getStatus()) {
                            SYS_LOGGER.log(Level.SEVERE, "Attempting crash recovery for {0}", manifestPath);
                            int numberOfCrashes = nodeData.getNumberOfCrashes();
                            ++numberOfCrashes;
                            nodeData.setNumberOfCrashes(numberOfCrashes);
                            if (numberOfCrashes <= AutoIngestUserPreferences.getMaxNumTimesToProcessImage()) {
                                nodeData.setStatus(PENDING);
                                Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
                                newPendingJobsList.add(new AutoIngestJob(manifest, caseDirectoryPath, nodeData.getPriority(), LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING, new Date(0), true));
                                if (null != caseDirectoryPath) {
                                    try {
                                        AutoIngestAlertFile.create(caseDirectoryPath);
                                    } catch (AutoIngestAlertFileException ex) {
                                        SYS_LOGGER.log(Level.SEVERE, String.format("Error creating alert file for crashed job for %s", manifestPath), ex);
                                    }
                                    try {
                                        new AutoIngestJobLogger(manifest.getFilePath(), manifest.getDataSourceFileName(), caseDirectoryPath).logCrashRecoveryWithRetry();
                                    } catch (AutoIngestJobLoggerException ex) {
                                        SYS_LOGGER.log(Level.SEVERE, String.format("Error creating case auto ingest log entry for crashed job for %s", manifestPath), ex);
                                    }
                                }
                            } else {
                                nodeData.setStatus(COMPLETED);
                                Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
                                newCompletedJobsList.add(new AutoIngestJob(manifest, caseDirectoryPath, nodeData.getPriority(), LOCAL_HOST_NAME, AutoIngestJob.Stage.COMPLETED, new Date(), true));
                                if (null != caseDirectoryPath) {
                                    try {
                                        AutoIngestAlertFile.create(caseDirectoryPath);
                                    } catch (AutoIngestAlertFileException ex) {
                                        SYS_LOGGER.log(Level.SEVERE, String.format("Error creating alert file for crashed job for %s", manifestPath), ex);
                                    }
                                    try {
                                        new AutoIngestJobLogger(manifest.getFilePath(), manifest.getDataSourceFileName(), caseDirectoryPath).logCrashRecoveryNoRetry();
                                    } catch (AutoIngestJobLoggerException ex) {
                                        SYS_LOGGER.log(Level.SEVERE, String.format("Error creating case auto ingest log entry for crashed job for %s", manifestPath), ex);
                                    }
                                }
                            }
                            try {
                                coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath, nodeData.toArray());
                            } catch (CoordinationServiceException ex) {
                                SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to set node data for %s", manifestPath), ex);
                            }
                        }
                    } catch (CoordinationServiceException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to get node data for %s", manifestPath), ex);
                    } finally {
                        try {
                            manifestLock.release();
                        } catch (CoordinationServiceException ex) {
                            SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to release exclusive lock for %s", manifestPath), ex);
                        }
                    }
                }
            } catch (CoordinationServiceException ex) {
                SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to get exclusive lock for %s", manifestPath), ex);
            }
        }

        /**
         * Adds a job to process a manifest to the completed jobs list.
         *
         * @param manifest The manifest.
         * @param nodeData The data stored in the coordination service node for
         *                 the manifest.
         */
        private void addCompletedJob(Manifest manifest, ManifestNodeData nodeData) {
            Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
            if (null != caseDirectoryPath) {
                newCompletedJobsList.add(new AutoIngestJob(manifest, caseDirectoryPath, nodeData.getPriority(), LOCAL_HOST_NAME, AutoIngestJob.Stage.COMPLETED, nodeData.getCompletedDate(), nodeData.getErrorsOccurred()));
            } else {
                SYS_LOGGER.log(Level.WARNING, String.format("Job completed for %s, but cannot find case directory, ignoring job", manifest.getFilePath()));
            }
        }

        /**
         * Invoked for a file that could not be visited because an I/O exception
         * was thrown when visiting a file. Logs the exceptino and checks if the
         * task thread has been interrupted because auto ingest is shutting down
         * and terminates the scan if that is the case.
         *
         * @param file The file.
         * @param ex   The exception.
         *
         * @return TERMINATE if auto ingest is shutting down, CONTINUE if it has
         *         not.
         *
         * @throws IOException if an I/O error occurs, but this implementation
         *                     does not throw.
         */
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException ex) throws IOException {
            SYS_LOGGER.log(Level.SEVERE, String.format("Error while visiting %s during input directories scan", file.toString()), ex);
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }
            return CONTINUE;
        }

        /**
         * Invoked for an input directory after entries in the directory are
         * visited. Checks if the task thread has been interrupted because auto
         * ingest is shutting down and terminates the scan if that is the case.
         *
         * @param dirPath  The directory about to be visited.
         * @param dirAttrs The basic file attributes of the directory about to
         *                 be visited.
         *
         * @return TERMINATE if the task thread has been interrupted, CONTINUE
         *         if it has not.
         *
         * @throws IOException if an I/O error occurs, but this implementation
         *                     does not throw.
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dirPath, IOException unused) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }
            return CONTINUE;
        }

    }

    /**
     * A single instance of this job processing task is used by the auto ingest
     * manager to process auto ingest jobs. The task does a blocking take from a
     * completion service for the input directory scan tasks that refresh the
     * pending jobs queue.
     * <p>
     * The job processing task can be paused between jobs (it waits on the
     * monitor of its pause lock object) and resumed (by notifying the monitor
     * of its pause lock object). This supports doing things that should be done
     * between jobs: orderly shutdown of auto ingest and changes to the ingest
     * configuration (settings). Note that the ingest configuration may be
     * specific to the host machine or shared between multiple nodes, in which
     * case it is downloaded from a specified location before each job.
     * <p>
     * The task pauses itself if system errors occur, e.g., problems with the
     * coordination service, database server, Solr server, etc. The idea behind
     * this is to avoid attempts to process jobs when the auto ingest system is
     * not in a state to produce consistent and correct results. It is up to a
     * system administrator to examine the auto ingest system logs, etc., to
     * find a remedy for the problem and then resume the task.
     * <p>
     * Note that the task also waits on the monitor of its ingest lock object
     * both when the data source processor and the analysis modules are running
     * in other threads. Notifies are done via a data source processor callback
     * and an ingest job event handler, respectively.
     */
    private final class JobProcessingTask implements Runnable {

        private static final String AUTO_INGEST_MODULE_OUTPUT_DIR = "AutoIngest";
        private final Object ingestLock;
        private final Object pauseLock;
        @GuardedBy("pauseLock")
        private boolean pauseRequested;
        @GuardedBy("pauseLock")
        private boolean waitingForInputScan;

        /**
         * Constructs a job processing task used by the auto ingest manager to
         * process auto ingest jobs.
         */
        private JobProcessingTask() {
            ingestLock = new Object();
            pauseLock = new Object();
            errorState = ErrorState.NONE;
        }

        /**
         * Processes auto ingest jobs, blocking on a completion service for
         * input directory scan tasks and waiting on a pause lock object when
         * paused by a client or because of a system error. The task is also in
         * a wait state when the data source processor or the analysis modules
         * for a job are running.
         */
        @Override
        public void run() {
            SYS_LOGGER.log(Level.INFO, "Job processing task started");
            while (true) {
                try {
                    if (jobProcessingTaskFuture.isCancelled()) {
                        break;
                    }
                    waitForInputDirScan();
                    if (jobProcessingTaskFuture.isCancelled()) {
                        break;
                    }
                    try {
                        processJobs();
                    } catch (Exception ex) { // Exception firewall
                        if (jobProcessingTaskFuture.isCancelled()) {
                            break;
                        }
                        if (ex instanceof CoordinationServiceException) {
                            errorState = ErrorState.COORDINATION_SERVICE_ERROR;
                        } else if (ex instanceof SharedConfigurationException) {
                            errorState = ErrorState.SHARED_CONFIGURATION_DOWNLOAD_ERROR;
                        } else if (ex instanceof ServicesMonitorException) {
                            errorState = ErrorState.SERVICES_MONITOR_COMMUNICATION_ERROR;
                        } else if (ex instanceof DatabaseServerDownException) {
                            errorState = ErrorState.DATABASE_SERVER_ERROR;
                        } else if (ex instanceof KeywordSearchServerDownException) {
                            errorState = ErrorState.KEYWORD_SEARCH_SERVER_ERROR;
                        } else if (ex instanceof CaseManagementException) {
                            errorState = ErrorState.CASE_MANAGEMENT_ERROR;
                        } else if (ex instanceof AnalysisStartupException) {
                            errorState = ErrorState.ANALYSIS_STARTUP_ERROR;
                        } else if (ex instanceof FileExportException) {
                            errorState = ErrorState.FILE_EXPORT_ERROR;
                        } else if (ex instanceof AutoIngestAlertFileException) {
                            errorState = ErrorState.ALERT_FILE_ERROR;
                        } else if (ex instanceof AutoIngestJobLoggerException) {
                            errorState = ErrorState.JOB_LOGGER_ERROR;
                        } else if (ex instanceof AutoIngestDataSourceProcessorException) {
                            errorState = ErrorState.DATA_SOURCE_PROCESSOR_ERROR;
                        } else if (ex instanceof InterruptedException) {
                            throw (InterruptedException) ex;
                        } else {
                            errorState = ErrorState.UNEXPECTED_EXCEPTION;
                        }
                        SYS_LOGGER.log(Level.SEVERE, "Auto ingest system error", ex);
                        pauseForSystemError();
                    }
                } catch (InterruptedException ex) {
                    break;
                }
            }
            SYS_LOGGER.log(Level.INFO, "Job processing task stopped");
        }

        /**
         * Makes a request to suspend job processing. The request will not be
         * serviced immediately if the task is doing a job.
         */
        private void requestPause() {
            synchronized (pauseLock) {
                SYS_LOGGER.log(Level.INFO, "Job processing pause requested");
                pauseRequested = true;
                if (waitingForInputScan) {
                    /*
                     * If the flag is set, the job processing task is blocked
                     * waiting for an input directory scan to complete, so
                     * notify any observers that the task is paused. This works
                     * because as soon as the task stops waiting for a scan to
                     * complete, it checks the pause requested flag. If the flag
                     * is set, the task immediately waits on the pause lock
                     * object.
                     */
                    setChanged();
                    notifyObservers(Event.PAUSED_BY_REQUEST);
                }
            }
        }

        /**
         * Makes a request to resume job processing.
         */
        private void requestResume() {
            synchronized (pauseLock) {
                SYS_LOGGER.log(Level.INFO, "Job processing resume requested");
                pauseRequested = false;
                if (waitingForInputScan) {
                    /*
                     * If the flag is set, the job processing task is blocked
                     * waiting for an input directory scan to complete, but
                     * notify any observers that the task is resumed anyway.
                     * This works because as soon as the task stops waiting for
                     * a scan to complete, it checks the pause requested flag.
                     * If the flag is not set, the task immediately begins
                     * processing the pending jobs queue.
                     */
                    setChanged();
                    notifyObservers(Event.RESUMED);
                }
                pauseLock.notifyAll();
            }
        }

        /**
         * Checks for a request to suspend jobs processing. If there is one,
         * blocks until resumed or interrupted.
         *
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private void pauseIfRequested() throws InterruptedException {
            synchronized (pauseLock) {
                if (pauseRequested) {
                    SYS_LOGGER.log(Level.INFO, "Job processing paused by request");
                    pauseRequested = false;
                    setChanged();
                    notifyObservers(Event.PAUSED_BY_REQUEST);
                    pauseLock.wait();
                    SYS_LOGGER.log(Level.INFO, "Job processing resumed after pause request");
                    setChanged();
                    notifyObservers(Event.RESUMED);
                }
            }
        }

        /**
         * Pauses auto ingest to allow a sys admin to address a system error.
         *
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private void pauseForSystemError() throws InterruptedException {
            synchronized (pauseLock) {
                SYS_LOGGER.log(Level.SEVERE, "Job processing paused for system error");
                setChanged();
                notifyObservers(Event.PAUSED_FOR_SYSTEM_ERROR);
                pauseLock.wait();
                errorState = ErrorState.NONE;
                SYS_LOGGER.log(Level.INFO, "Job processing resumed after system error");
                setChanged();
                notifyObservers(Event.RESUMED);
            }
        }

        /**
         * Waits until an input directory scan has completed, with pause request
         * checks before and after the wait.
         *
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private void waitForInputDirScan() throws InterruptedException {
            synchronized (pauseLock) {
                pauseIfRequested();
                /*
                 * The waiting for scan flag is needed for the special case of a
                 * client making a pause request while this task is blocked on
                 * the input directory scan task completion service. Although,
                 * the task is unable to act on the request until the next scan
                 * completes, when it unblocks it will check the pause requested
                 * flag and promptly pause if the flag is set. Thus, setting the
                 * waiting for scan flag allows a pause request in a client
                 * thread to responsively notify any observers that processing
                 * is already effectively paused.
                 */
                waitingForInputScan = true;
            }
            SYS_LOGGER.log(Level.INFO, "Job processing waiting for input scan completion");
            synchronized (scanMonitor) {
                scanMonitor.wait();
            }
            SYS_LOGGER.log(Level.INFO, "Job processing finished wait for input scan completion");
            synchronized (pauseLock) {
                waitingForInputScan = false;
                pauseIfRequested();
            }
        }

        /**
         * Processes jobs until the pending jobs queue is empty.
         *
         * @throws CoordinationServiceException     if there is an error
         *                                          acquiring or releasing
         *                                          coordination service locks
         *                                          or setting coordination
         *                                          service node data.
         * @throws SharedConfigurationException     if there is an error while
         *                                          downloading shared
         *                                          configuration.
         * @throws ServicesMonitorException         if there is an error
         *                                          querying the services
         *                                          monitor.
         * @throws DatabaseServerDownException      if the database server is
         *                                          down.
         * @throws KeywordSearchServerDownException if the Solr server is down.
         * @throws CaseManagementException          if there is an error
         *                                          creating, opening or closing
         *                                          the case for the job.
         * @throws AnalysisStartupException         if there is an error
         *                                          starting analysis of the
         *                                          data source by the data
         *                                          source level and file level
         *                                          ingest modules.
         * @throws FileExportException              if there is an error
         *                                          exporting files.
         * @throws AutoIngestAlertFileException     if there is an error
         *                                          creating an alert file.
         * @throws AutoIngestJobLoggerException     if there is an error writing
         *                                          to the auto ingest log for
         *                                          the case.
         * @throws InterruptedException             if the thread running the
         *                                          job processing task is
         *                                          interrupted while blocked,
         *                                          i.e., if auto ingest is
         *                                          shutting down.
         */
        private void processJobs() throws CoordinationServiceException, SharedConfigurationException, ServicesMonitorException, DatabaseServerDownException, KeywordSearchServerDownException, CaseManagementException, AnalysisStartupException, FileExportException, AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException {
            SYS_LOGGER.log(Level.INFO, "Started processing pending jobs queue");
            Lock manifestLock = JobProcessingTask.this.dequeueAndLockNextJob();
            while (null != manifestLock) {
                try {
                    if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                        return;
                    }
                    processJob();
                } finally {
                    manifestLock.release();
                }
                if (jobProcessingTaskFuture.isCancelled()) {
                    return;
                }
                pauseIfRequested();
                if (jobProcessingTaskFuture.isCancelled()) {
                    return;
                }
                manifestLock = JobProcessingTask.this.dequeueAndLockNextJob();
            }
        }

        /**
         * Inspects the pending jobs queue, looking for the next job that is
         * ready for processing. If such a job is found, it is removed from the
         * queue, made the current job, and a coordination service lock on the
         * manifest for the job is returned.
         * <p>
         * Note that two passes through the queue may be made, the first
         * enforcing the maximum concurrent jobs per case setting, the second
         * ignoring this constraint. This policy override prevents idling nodes
         * when jobs are queued.
         * <p>
         * Holding the manifest lock does the following: a) signals to all auto
         * ingest nodes, including this one, that the job is in progress so it
         * does not get put in pending jobs queues or completed jobs lists by
         * input directory scans and b) prevents deletion of the input directory
         * and the case directory because exclusive manifest locks for all of
         * the manifests for a case must be acquired for delete operations.
         *
         * @return A manifest file lock if a ready job was found, null
         *         otherwise.
         *
         * @throws CoordinationServiceException if there is an error while
         *                                      acquiring or releasing a
         *                                      manifest file lock.
         * @throws InterruptedException         if the thread is interrupted
         *                                      while reading the lock data
         */
        private Lock dequeueAndLockNextJob() throws CoordinationServiceException, InterruptedException {
            SYS_LOGGER.log(Level.INFO, "Checking pending jobs queue for ready job, enforcing max jobs per case");
            Lock manifestLock;
            synchronized (jobsLock) {
                manifestLock = dequeueAndLockNextJob(true);
                if (null != manifestLock) {
                    SYS_LOGGER.log(Level.INFO, "Dequeued job for {0}", currentJob.getManifest().getFilePath());
                } else {
                    SYS_LOGGER.log(Level.INFO, "No ready job");
                    SYS_LOGGER.log(Level.INFO, "Checking pending jobs queue for ready job, not enforcing max jobs per case");
                    manifestLock = dequeueAndLockNextJob(false);
                    if (null != manifestLock) {
                        SYS_LOGGER.log(Level.INFO, "Dequeued job for {0}", currentJob.getManifest().getFilePath());
                    } else {
                        SYS_LOGGER.log(Level.INFO, "No ready job");
                    }
                }
            }
            return manifestLock;
        }

        /**
         * Inspects the pending jobs queue, looking for the next job that is
         * ready for processing. If such a job is found, it is removed from the
         * queue, made the current job, and a coordination service lock on the
         * manifest for the job is returned.
         *
         * @param enforceMaxJobsPerCase Whether or not to enforce the maximum
         *                              concurrent jobs per case setting.
         *
         * @return A manifest file lock if a ready job was found, null
         *         otherwise.
         *
         * @throws CoordinationServiceException if there is an error while
         *                                      acquiring or releasing a
         *                                      manifest file lock.
         * @throws InterruptedException         if the thread is interrupted
         *                                      while reading the lock data
         */
        private Lock dequeueAndLockNextJob(boolean enforceMaxJobsPerCase) throws CoordinationServiceException, InterruptedException {
            Lock manifestLock = null;
            synchronized (jobsLock) {
                Iterator<AutoIngestJob> iterator = pendingJobs.iterator();
                while (iterator.hasNext()) {
                    AutoIngestJob job = iterator.next();
                    Path manifestPath = job.getManifest().getFilePath();
                    manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString());
                    if (null == manifestLock) {
                        /*
                         * Skip the job. If it is exclusively locked for
                         * processing or deletion by another node, the remote
                         * job event handlers or the next input scan will flush
                         * it out of the pending queue.
                         */
                        continue;
                    }

                    ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString()));
                    if (!nodeData.getStatus().equals(PENDING)) {
                        /*
                         * Due to a timing issue or a missed event, a
                         * non-pending job has ended up on the pending queue.
                         * Skip the job and remove it from the queue.
                         */
                        iterator.remove();
                        continue;
                    }

                    if (enforceMaxJobsPerCase) {
                        int currentJobsForCase = 0;
                        for (AutoIngestJob runningJob : hostNamesToRunningJobs.values()) {
                            if (0 == job.getManifest().getCaseName().compareTo(runningJob.getManifest().getCaseName())) {
                                ++currentJobsForCase;
                            }
                        }
                        if (currentJobsForCase >= AutoIngestUserPreferences.getMaxConcurrentJobsForOneCase()) {
                            manifestLock.release();
                            manifestLock = null;
                            continue;
                        }
                    }
                    iterator.remove();
                    currentJob = job;
                    break;
                }
            }
            return manifestLock;
        }

        /**
         * Processes and auto ingest job.
         *
         * @throws CoordinationServiceException     if there is an error
         *                                          acquiring or releasing
         *                                          coordination service locks
         *                                          or setting coordination
         *                                          service node data.
         * @throws SharedConfigurationException     if there is an error while
         *                                          downloading shared
         *                                          configuration.
         * @throws ServicesMonitorException         if there is an error
         *                                          querying the services
         *                                          monitor.
         * @throws DatabaseServerDownException      if the database server is
         *                                          down.
         * @throws KeywordSearchServerDownException if the Solr server is down.
         * @throws CaseManagementException          if there is an error
         *                                          creating, opening or closing
         *                                          the case for the job.
         * @throws AnalysisStartupException         if there is an error
         *                                          starting analysis of the
         *                                          data source by the data
         *                                          source level and file level
         *                                          ingest modules.
         * @throws FileExportException              if there is an error
         *                                          exporting files.
         * @throws AutoIngestAlertFileException     if there is an error
         *                                          creating an alert file.
         * @throws AutoIngestJobLoggerException     if there is an error writing
         *                                          to the auto ingest log for
         *                                          the case.
         * @throws InterruptedException             if the thread running the
         *                                          job processing task is
         *                                          interrupted while blocked,
         *                                          i.e., if auto ingest is
         *                                          shutting down.
         */
        private void processJob() throws CoordinationServiceException, SharedConfigurationException, ServicesMonitorException, DatabaseServerDownException, KeywordSearchServerDownException, CaseManagementException, AnalysisStartupException, FileExportException, AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException {
            Manifest manifest = currentJob.getManifest();
            String manifestPath = manifest.getFilePath().toString();
            ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath));
            nodeData.setStatus(PROCESSING);
            coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath, nodeData.toArray());
            SYS_LOGGER.log(Level.INFO, "Started processing of {0}", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.STARTING);
            setChanged();
            notifyObservers(Event.JOB_STARTED);
            eventPublisher.publishRemotely(new AutoIngestJobStartedEvent(currentJob));
            try {
                if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                    return;
                }
                attemptJob();

            } finally {
                if (jobProcessingTaskFuture.isCancelled()) {
                    currentJob.cancel();
                }

                nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath));
                if (currentJob.isCompleted() || currentJob.isCancelled()) {
                    nodeData.setStatus(COMPLETED);
                    Date completedDate = new Date();
                    currentJob.setCompletedDate(completedDate);
                    nodeData.setCompletedDate(currentJob.getCompletedDate());
                    nodeData.setErrorsOccurred(currentJob.hasErrors());
                } else {
                    // The job may get retried
                    nodeData.setStatus(PENDING);
                }
                coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath, nodeData.toArray());

                boolean retry = (!currentJob.isCancelled() && !currentJob.isCompleted());
                SYS_LOGGER.log(Level.INFO, "Completed processing of {0}, retry = {1}", new Object[]{manifestPath, retry});
                if (currentJob.isCancelled()) {
                    Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
                    if (null != caseDirectoryPath) {
                        AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                        AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifest.getFilePath(), manifest.getDataSourceFileName(), caseDirectoryPath);
                        jobLogger.logJobCancelled();
                    }
                }
                synchronized (jobsLock) {
                    if (!retry) {
                        completedJobs.add(currentJob);
                    }
                    eventPublisher.publishRemotely(new AutoIngestJobCompletedEvent(currentJob, retry));
                    currentJob = null;
                    setChanged();
                    notifyObservers(Event.JOB_COMPLETED);
                }
            }
        }

        /**
         * Attempts processing of an auto ingest job.
         *
         * @throws CoordinationServiceException     if there is an error
         *                                          acquiring or releasing
         *                                          coordination service locks
         *                                          or setting coordination
         *                                          service node data.
         * @throws SharedConfigurationException     if there is an error while
         *                                          downloading shared
         *                                          configuration.
         * @throws ServicesMonitorException         if there is an error
         *                                          querying the services
         *                                          monitor.
         * @throws DatabaseServerDownException      if the database server is
         *                                          down.
         * @throws KeywordSearchServerDownException if the Solr server is down.
         * @throws CaseManagementException          if there is an error
         *                                          creating, opening or closing
         *                                          the case for the job.
         * @throws AnalysisStartupException         if there is an error
         *                                          starting analysis of the
         *                                          data source by the data
         *                                          source level and file level
         *                                          ingest modules.
         * @throws InterruptedException             if the thread running the
         *                                          job processing task is
         *                                          interrupted while blocked,
         *                                          i.e., if auto ingest is
         *                                          shutting down.
         */
        private void attemptJob() throws CoordinationServiceException, SharedConfigurationException, ServicesMonitorException, DatabaseServerDownException, KeywordSearchServerDownException, CaseManagementException, AnalysisStartupException, FileExportException, AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException {
            updateConfiguration();
            if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }
            verifyRequiredSevicesAreRunning();
            if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }
            Case caseForJob = openCase();
            try {
                if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                    return;
                }
                runIngestForJob(caseForJob);

            } finally {
                try {
                    Case.closeCurrentCase();
                } catch (CaseActionException ex) {
                    Manifest manifest = currentJob.getManifest();
                    throw new CaseManagementException(String.format("Error closing case %s for %s", manifest.getCaseName(), manifest.getFilePath()), ex);
                }
            }
        }

        /**
         * Updates the ingest system settings by downloading the latest version
         * of the settings if using shared configuration.
         *
         * @throws SharedConfigurationException if there is an error downloading
         *                                      shared configuration.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void updateConfiguration() throws SharedConfigurationException, InterruptedException {
            if (AutoIngestUserPreferences.getSharedConfigEnabled()) {
                Manifest manifest = currentJob.getManifest();
                Path manifestPath = manifest.getFilePath();
                SYS_LOGGER.log(Level.INFO, "Downloading shared configuration for {0}", manifestPath);
                currentJob.setStage(AutoIngestJob.Stage.UPDATING_SHARED_CONFIG);
                new SharedConfiguration().downloadConfiguration();
            }
        }

        /**
         * Checks the availability of the services required to process an
         * automated ingest job.
         *
         * @throws ServicesMonitorException    if there is an error querying the
         *                                     services monitor.
         * @throws DatabaseServerDownException if the database server is down.
         * @throws SolrServerDownException     if the keyword search server is
         *                                     down.
         */
        private void verifyRequiredSevicesAreRunning() throws ServicesMonitorException, DatabaseServerDownException, KeywordSearchServerDownException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Checking services availability for {0}", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.CHECKING_SERVICES);
            if (!isServiceUp(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString())) {
                throw new DatabaseServerDownException("Case database server is down");
            }
            if (!isServiceUp(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString())) {
                throw new KeywordSearchServerDownException("Keyword search server is down");
            }
        }

        /**
         * Tests service of interest to verify that it is running.
         *
         * @param serviceName Name of the service.
         *
         * @return True if the service is running, false otherwise.
         *
         * @throws ServicesMonitorException if there is an error querying the
         *                                  services monitor.
         */
        private boolean isServiceUp(String serviceName) throws ServicesMonitorException {
            return (ServicesMonitor.getInstance().getServiceStatus(serviceName).equals(ServicesMonitor.ServiceStatus.UP.toString()));
        }

        /**
         * Creates or opens the case for the current auto ingest job, acquiring
         * an exclusive lock on the case name during the operation.
         * <p>
         * IMPORTANT: The case name lock is used to ensure that only one auto
         * ingest node at a time can attempt to create/open/delete a given case.
         * The case name lock must be acquired both here and during case
         * deletion.
         *
         * @return The case on success, null otherwise.
         *
         * @throws CoordinationServiceException if there is an error acquiring
         *                                      or releasing the case name lock.
         * @throws CaseManagementException      if there is an error opening the
         *                                      case.
         * @throws InterruptedException         if the thread running the auto
         *                                      ingest job processing task is
         *                                      interrupted while blocked, i.e.,
         *                                      if auto ingest is shutting down.
         */
        private Case openCase() throws CoordinationServiceException, CaseManagementException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            String caseDisplayName = manifest.getCaseName();
            String caseName;
            try {
                caseName = Case.displayNameToCaseName(caseDisplayName);
            } catch (IllegalCaseNameException ex) {
                throw new CaseManagementException(String.format("Error creating or opening case %s for %s", manifest.getCaseName(), manifest.getFilePath()), ex);
            }
            SYS_LOGGER.log(Level.INFO, "Opening case {0} ({1}) for {2}", new Object[]{caseDisplayName, caseName, manifest.getFilePath()});
            currentJob.setStage(AutoIngestJob.Stage.OPENING_CASE);
            try {
                Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, caseName);
                if (null != caseDirectoryPath) {
                    Path metadataFilePath = caseDirectoryPath.resolve(caseName + CaseMetadata.getFileExtension());
                    Case.openAsCurrentCase(metadataFilePath.toString());
                } else {
                    caseDirectoryPath = PathUtils.createCaseFolderPath(rootOutputDirectory, caseName);
                    Case.createAsCurrentCase(caseDirectoryPath.toString(), caseName, "", "", CaseType.MULTI_USER_CASE);
                    /*
                     * Sleep a bit before releasing the lock to ensure that the
                     * new case folder is visible on the network.
                     */
                    Thread.sleep(AutoIngestUserPreferences.getSecondsToSleepBetweenCases() * 1000);
                }
                currentJob.setCaseDirectoryPath(caseDirectoryPath);
                Case caseForJob = Case.getCurrentCase();
                SYS_LOGGER.log(Level.INFO, "Opened case {0} for {1}", new Object[]{caseForJob.getName(), manifest.getFilePath()});
                return caseForJob;

            } catch (CaseActionException ex) {
                throw new CaseManagementException(String.format("Error creating or opening case %s (%s) for %s", manifest.getCaseName(), caseName, manifest.getFilePath()), ex);
            } catch (IllegalStateException ex) {
                /*
                 * Deal with the unfortunate fact that Case.getCurrentCase
                 * throws IllegalStateException.
                 */
                throw new CaseManagementException(String.format("Error getting current case %s (%s) for %s", caseName, manifest.getCaseName(), manifest.getFilePath()), ex);
            }
        }

        /**
         * Runs the ingest porocess for the current job.
         *
         * @param caseForJob The case for the job.
         *
         * @throws CoordinationServiceException if there is an error acquiring
         *                                      or releasing coordination
         *                                      service locks or setting
         *                                      coordination service node data.
         * @throws AnalysisStartupException     if there is an error starting
         *                                      analysis of the data source by
         *                                      the data source level and file
         *                                      level ingest modules.
         * @throws FileExportException          if there is an error exporting
         *                                      files.
         * @throws AutoIngestAlertFileException if there is an error creating an
         *                                      alert file.
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void runIngestForJob(Case caseForJob) throws CoordinationServiceException, AnalysisStartupException, FileExportException, AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException {
            Manifest manifest = currentJob.getManifest();
            String manifestPath = manifest.getFilePath().toString();
            try {
                if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                    return;
                }
                ingestDataSource(caseForJob);

            } finally {
                currentJob.setCompleted();
            }
        }

        /**
         * Ingests the data source specified in the manifest of the current job
         * by using an appropriate data source processor to add the data source
         * to the case database, passing the data source to the underlying
         * ingest manager for analysis by data source and file level analysis
         * modules, and exporting any files from the data source that satisfy
         * the user-defined file export rules.
         *
         * @param caseForJob The case for the job.
         *
         * @throws AnalysisStartupException     if there is an error starting
         *                                      analysis of the data source by
         *                                      the data source level and file
         *                                      level ingest modules.
         * @throws FileExportException          if there is an error exporting
         *                                      files.
         * @throws AutoIngestAlertFileException if there is an error creating an
         *                                      alert file.
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void ingestDataSource(Case caseForJob) throws AnalysisStartupException, FileExportException, AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException {
            if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }

            DataSource dataSource = identifyDataSource(caseForJob);
            if (null == dataSource) {
                currentJob.setStage(AutoIngestJob.Stage.COMPLETED);
                return;
            }

            if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }

            runDataSourceProcessor(caseForJob, dataSource);
            if (dataSource.getContent().isEmpty()) {
                currentJob.setStage(AutoIngestJob.Stage.COMPLETED);
                return;
            }

            if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }

            try {
                analyze(dataSource);
            } finally {
                /*
                 * Sleep to allow ingest event subscribers to do their event
                 * handling.
                 */
                Thread.sleep(AutoIngestUserPreferences.getSecondsToSleepBetweenCases() * 1000);
            }

            if (currentJob.isCancelled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }

            exportFiles(dataSource);
        }

        /**
         * Identifies the type of the data source specified in the manifest for
         * the current job and extracts it if required.
         *
         * @return A data source object.
         *
         * @throws AutoIngestAlertFileException if there is an error creating an
         *                                      alert file.
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the auto
         *                                      ingest job processing task is
         *                                      interrupted while blocked, i.e.,
         *                                      if auto ingest is shutting down.
         */
        private DataSource identifyDataSource(Case caseForJob) throws AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Identifying data source for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.IDENTIFYING_DATA_SOURCE);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            Path dataSourcePath = manifest.getDataSourcePath();
            File dataSource = dataSourcePath.toFile();
            if (!dataSource.exists()) {
                SYS_LOGGER.log(Level.SEVERE, "Missing data source for {0}", manifestPath);
                currentJob.setErrorsOccurred(true);
                AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                jobLogger.logMissingDataSource();
                return null;
            }
            String deviceId = manifest.getDeviceId();
            return new DataSource(deviceId, dataSourcePath);
        }

        /**
         * Passes the data source for the current job through a data source
         * processor that adds it to the case database.
         *
         * @param dataSource The data source.
         *
         * @throws AutoIngestAlertFileException if there is an error creating an
         *                                      alert file.
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void runDataSourceProcessor(Case caseForJob, DataSource dataSource) throws InterruptedException, AutoIngestAlertFileException, AutoIngestJobLoggerException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Adding data source for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.ADDING_DATA_SOURCE);
            UUID taskId = UUID.randomUUID();
            DataSourceProcessorCallback callBack = new AddDataSourceCallback(caseForJob, dataSource, taskId);
            DataSourceProcessorProgressMonitor progressMonitor = new DoNothingDSPProgressMonitor();
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {
                caseForJob.notifyAddingDataSource(taskId);

                // lookup all AutomatedIngestDataSourceProcessors 
                Collection<? extends AutoIngestDataSourceProcessor> processorCandidates = Lookup.getDefault().lookupAll(AutoIngestDataSourceProcessor.class);

                Map<AutoIngestDataSourceProcessor, Integer> validDataSourceProcessorsMap = new HashMap<>();
                for (AutoIngestDataSourceProcessor processor : processorCandidates) {
                    try {
                        int confidence = processor.canProcess(dataSource.getPath());
                        if (confidence > 0) {
                            validDataSourceProcessorsMap.put(processor, confidence);
                        }
                    } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
                        SYS_LOGGER.log(Level.SEVERE, "Exception while determining whether data source processor {0} can process {1}", new Object[]{processor.getDataSourceType(), dataSource.getPath()});
                        // rethrow the exception. It will get caught & handled upstream and will result in AIM auto-pause.
                        throw ex;
                    }
                }

                // did we find a data source processor that can process the data source
                if (validDataSourceProcessorsMap.isEmpty()) {
                    // This should never happen. We should add all unsupported data sources as logical files.
                    AutoIngestAlertFile.create(caseDirectoryPath);
                    currentJob.setErrorsOccurred(true);
                    jobLogger.logFailedToIdentifyDataSource();
                    SYS_LOGGER.log(Level.WARNING, "Unsupported data source {0} for {1}", new Object[]{dataSource.getPath(), manifestPath});  // NON-NLS
                    return;
                }

                // Get an ordered list of data source processors to try
                List<AutoIngestDataSourceProcessor> validDataSourceProcessors = validDataSourceProcessorsMap.entrySet().stream()
                        .sorted(Map.Entry.<AutoIngestDataSourceProcessor, Integer>comparingByValue().reversed())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                synchronized (ingestLock) {
                    // Try each DSP in decreasing order of confidence
                    for (AutoIngestDataSourceProcessor selectedProcessor : validDataSourceProcessors) {
                        jobLogger.logDataSourceProcessorSelected(selectedProcessor.getDataSourceType());
                        SYS_LOGGER.log(Level.INFO, "Identified data source type for {0} as {1}", new Object[]{manifestPath, selectedProcessor.getDataSourceType()});
                        try {
                            selectedProcessor.process(dataSource.getDeviceId(), dataSource.getPath(), progressMonitor, callBack);
                            ingestLock.wait();
                            return;
                        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
                            // Log that the current DSP failed and set the error flag. We consider it an error
                            // if a DSP fails even if a later one succeeds since we expected to be able to process
                            // the data source which each DSP on the list.
                            AutoIngestAlertFile.create(caseDirectoryPath);
                            currentJob.setErrorsOccurred(true);
                            jobLogger.logDataSourceProcessorError(selectedProcessor.getDataSourceType());
                            SYS_LOGGER.log(Level.SEVERE, "Exception while processing {0} with data source processor {1}", new Object[]{dataSource.getPath(), selectedProcessor.getDataSourceType()});
                        }
                    }
                    // If we get to this point, none of the processors were successful
                    SYS_LOGGER.log(Level.SEVERE, "All data source processors failed to process {0}", dataSource.getPath());
                    jobLogger.logFailedToAddDataSource();
                    // Throw an exception. It will get caught & handled upstream and will result in AIM auto-pause.
                    throw new AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException("Failed to process " + dataSource.getPath() + " with all data source processors");
                }
            } finally {
                currentJob.setDataSourceProcessor(null);
                logDataSourceProcessorResult(dataSource);
            }
        }

        /**
         * Logs the results of running a data source processor on the data
         * source for the current job.
         *
         * @param dataSource The data source.
         *
         * @throws AutoIngestAlertFileException if there is an error creating an
         *                                      alert file.
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void logDataSourceProcessorResult(DataSource dataSource) throws AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            DataSourceProcessorResult resultCode = dataSource.getResultDataSourceProcessorResultCode();
            if (null != resultCode) {
                switch (resultCode) {
                    case NO_ERRORS:
                        jobLogger.logDataSourceAdded();
                        if (dataSource.getContent().isEmpty()) {
                            currentJob.setErrorsOccurred(true);
                            AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                            jobLogger.logNoDataSourceContent();
                        }
                        break;

                    case NONCRITICAL_ERRORS:
                        for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                            SYS_LOGGER.log(Level.WARNING, "Non-critical error running data source processor for {0}: {1}", new Object[]{manifestPath, errorMessage});
                        }
                        jobLogger.logDataSourceAdded();
                        if (dataSource.getContent().isEmpty()) {
                            currentJob.setErrorsOccurred(true);
                            AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                            jobLogger.logNoDataSourceContent();
                        }
                        break;

                    case CRITICAL_ERRORS:
                        for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                            SYS_LOGGER.log(Level.SEVERE, "Critical error running data source processor for {0}: {1}", new Object[]{manifestPath, errorMessage});
                        }
                        currentJob.setErrorsOccurred(true);
                        AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                        jobLogger.logFailedToAddDataSource();
                        break;
                }
            } else {
                /*
                 * TODO (JIRA-1711): Use cancellation feature of data source
                 * processors that support cancellation. This should be able to
                 * be done by adding a transient reference to the DSP to
                 * AutoIngestJob and calling cancel on the DSP, if not null, in
                 * cancelCurrentJob.
                 */
                SYS_LOGGER.log(Level.WARNING, "Cancellation while waiting for data source processor for {0}", manifestPath);
                currentJob.setErrorsOccurred(true);
                AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                jobLogger.logDataSourceProcessorCancelled();
            }
        }

        /**
         * Analyzes the data source content returned by the data source
         * processor using the configured set of data source level and file
         * level analysis modules.
         *
         * @param dataSource The data source to analyze.
         *
         * @throws AnalysisStartupException     if there is an error analyzing
         *                                      the data source.
         * @throws AutoIngestAlertFileException if there is an error creating an
         *                                      alert file.
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void analyze(DataSource dataSource) throws AnalysisStartupException, AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Starting ingest modules analysis for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.ANALYZING_DATA_SOURCE);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            IngestJobEventListener ingestJobEventListener = new IngestJobEventListener();
            IngestManager.getInstance().addIngestJobEventListener(ingestJobEventListener);
            try {
                synchronized (ingestLock) {
                    IngestJobSettings ingestJobSettings = new IngestJobSettings(AutoIngestUserPreferences.getAutoModeIngestModuleContextString());
                    List<String> settingsWarnings = ingestJobSettings.getWarnings();
                    if (settingsWarnings.isEmpty()) {
                        IngestJobStartResult ingestJobStartResult = IngestManager.getInstance().beginIngestJob(dataSource.getContent(), ingestJobSettings);
                        IngestJob ingestJob = ingestJobStartResult.getJob();
                        if (null != ingestJob) {
                            currentJob.setIngestJob(ingestJob);
                            /*
                             * Block until notified by the ingest job event
                             * listener or until interrupted because auto ingest
                             * is shutting down.
                             */
                            ingestLock.wait();
                            IngestJob.ProgressSnapshot jobSnapshot = ingestJob.getSnapshot();
                            for (IngestJob.ProgressSnapshot.DataSourceProcessingSnapshot snapshot : jobSnapshot.getDataSourceSnapshots()) {
                                if (!snapshot.isCancelled()) {
                                    List<String> cancelledModules = snapshot.getCancelledDataSourceIngestModules();
                                    if (!cancelledModules.isEmpty()) {
                                        SYS_LOGGER.log(Level.WARNING, String.format("Ingest module(s) cancelled for %s", manifestPath));
                                        currentJob.setErrorsOccurred(true);
                                        AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                                        for (String module : snapshot.getCancelledDataSourceIngestModules()) {
                                            SYS_LOGGER.log(Level.WARNING, String.format("%s ingest module cancelled for %s", module, manifestPath));
                                            jobLogger.logIngestModuleCancelled(module);
                                        }
                                    }
                                    jobLogger.logAnalysisCompleted();
                                } else {
                                    currentJob.setStage(AutoIngestJob.Stage.CANCELLING);
                                    currentJob.setErrorsOccurred(true);
                                    AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                                    jobLogger.logAnalysisCancelled();
                                    CancellationReason cancellationReason = snapshot.getCancellationReason();
                                    if (CancellationReason.NOT_CANCELLED != cancellationReason && CancellationReason.USER_CANCELLED != cancellationReason) {
                                        throw new AnalysisStartupException(String.format("Analysis cacelled due to %s for %s", cancellationReason.getDisplayName(), manifestPath));
                                    }
                                }
                            }
                        } else if (!ingestJobStartResult.getModuleErrors().isEmpty()) {
                            for (IngestModuleError error : ingestJobStartResult.getModuleErrors()) {
                                SYS_LOGGER.log(Level.SEVERE, String.format("%s ingest module startup error for %s", error.getModuleDisplayName(), manifestPath), error.getThrowable());
                            }
                            currentJob.setErrorsOccurred(true);
                            AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                            jobLogger.logIngestModuleStartupErrors();
                            throw new AnalysisStartupException(String.format("Error(s) during ingest module startup for %s", manifestPath));
                        } else {
                            SYS_LOGGER.log(Level.SEVERE, String.format("Ingest manager ingest job start error for %s", manifestPath), ingestJobStartResult.getStartupException());
                            currentJob.setErrorsOccurred(true);
                            AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                            jobLogger.logAnalysisStartupError();
                            throw new AnalysisStartupException("Ingest manager error starting job", ingestJobStartResult.getStartupException());
                        }
                    } else {
                        for (String warning : settingsWarnings) {
                            SYS_LOGGER.log(Level.SEVERE, "Ingest job settings error for {0}: {1}", new Object[]{manifestPath, warning});
                        }
                        currentJob.setErrorsOccurred(true);
                        AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                        jobLogger.logIngestJobSettingsErrors();
                        throw new AnalysisStartupException("Error(s) in ingest job settings");
                    }
                }
            } finally {
                IngestManager.getInstance().removeIngestJobEventListener(ingestJobEventListener);
                currentJob.setIngestJob(null);
            }
        }

        /**
         * Exports any files from the data source for the current job that
         * satisfy any user-defined file export rules.
         *
         * @param dataSource The data source.
         *
         * @throws FileExportException          if there is an error exporting
         *                                      the files.
         * @throws AutoIngestAlertFileException if there is an error creating an
         *                                      alert file.
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void exportFiles(DataSource dataSource) throws FileExportException, AutoIngestAlertFileException, AutoIngestJobLoggerException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Exporting files for {0}", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.EXPORTING_FILES);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {
                FileExporter fileExporter = new FileExporter();
                if (fileExporter.isEnabled()) {
                    fileExporter.process(manifest.getDeviceId(), dataSource.getContent(), currentJob::isCancelled);
                    jobLogger.logFileExportCompleted();
                } else {
                    SYS_LOGGER.log(Level.WARNING, "Exporting files not enabled for {0}", manifestPath);
                    jobLogger.logFileExportDisabled();
                }
            } catch (FileExportException ex) {
                SYS_LOGGER.log(Level.SEVERE, String.format("Error doing file export for %s", manifestPath), ex);
                currentJob.setErrorsOccurred(true);
                AutoIngestAlertFile.create(caseDirectoryPath); // Do this first, it is more important than the case log
                jobLogger.logFileExportError();
            }
        }

        /**
         * A "callback" that collects the results of running a data source
         * processor on a data source and unblocks the job processing thread
         * when the data source processor finishes running in its own thread.
         */
        @Immutable
        class AddDataSourceCallback extends DataSourceProcessorCallback {

            private final Case caseForJob;
            private final DataSource dataSourceInfo;
            private final UUID taskId;

            /**
             * Constructs a "callback" that collects the results of running a
             * data source processor on a data source and unblocks the job
             * processing thread when the data source processor finishes running
             * in its own thread.
             *
             * @param caseForJob     The case for the current job.
             * @param dataSourceInfo The data source
             * @param taskId         The task id to associate with ingest job
             *                       events.
             */
            AddDataSourceCallback(Case caseForJob, DataSource dataSourceInfo, UUID taskId) {
                this.caseForJob = caseForJob;
                this.dataSourceInfo = dataSourceInfo;
                this.taskId = taskId;
            }

            /**
             * Called by the data source processor when it finishes running in
             * its own thread.
             *
             * @param result            The result code for the processing of
             *                          the data source.
             * @param errorMessages     Any error messages generated during the
             *                          processing of the data source.
             * @param dataSourceContent The content produced by processing the
             *                          data source.
             */
            @Override
            public void done(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> dataSourceContent) {
                if (!dataSourceContent.isEmpty()) {
                    caseForJob.notifyDataSourceAdded(dataSourceContent.get(0), taskId);
                } else {
                    caseForJob.notifyFailedAddingDataSource(taskId);
                }
                dataSourceInfo.setDataSourceProcessorOutput(result, errorMessages, dataSourceContent);
                dataSourceContent.addAll(dataSourceContent);
                synchronized (ingestLock) {
                    ingestLock.notify();
                }
            }

            /**
             * Called by the data source processor when it finishes running in
             * its own thread, if that thread is the AWT (Abstract Window
             * Toolkit) event dispatch thread (EDT).
             *
             * @param result            The result code for the processing of
             *                          the data source.
             * @param errorMessages     Any error messages generated during the
             *                          processing of the data source.
             * @param dataSourceContent The content produced by processing the
             *                          data source.
             */
            @Override
            public void doneEDT(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> dataSources) {
                done(result, errorMessages, dataSources);
            }

        }

        /**
         * A data source processor progress monitor does nothing. There is
         * currently no mechanism for showing or recording data source processor
         * progress during an auto ingest job.
         */
        private class DoNothingDSPProgressMonitor implements DataSourceProcessorProgressMonitor {

            /**
             * Does nothing.
             *
             * @param indeterminate
             */
            @Override
            public void setIndeterminate(final boolean indeterminate) {
            }

            /**
             * Does nothing.
             *
             * @param progress
             */
            @Override
            public void setProgress(final int progress) {
            }

            /**
             * Does nothing.
             *
             * @param text
             */
            @Override
            public void setProgressText(final String text) {
            }

        }

        /**
         * An ingest job event listener that allows the job processing task to
         * block until the analysis of a data source by the data source level
         * and file level ingest modules is completed.
         * <p>
         * Note that the ingest job can spawn "child" ingest jobs (e.g., if an
         * embedded virtual machine is found), so the job processing task must
         * remain blocked until ingest is no longer running.
         */
        private class IngestJobEventListener implements PropertyChangeListener {

            /**
             * Listens for local ingest job completed or cancelled events and
             * notifies the job processing thread when such an event occurs and
             * there are no "child" ingest jobs running.
             *
             * @param event
             */
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (AutopsyEvent.SourceType.LOCAL == ((AutopsyEvent) event).getSourceType()) {
                    String eventType = event.getPropertyName();
                    if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString()) || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                        synchronized (ingestLock) {
                            if (!IngestManager.getInstance().isIngestRunning()) {
                                ingestLock.notify();
                            }
                        }
                    }
                }
            }
        };

        /**
         * Exception thrown when the services monitor reports that the database
         * server is down.
         */
        private final class DatabaseServerDownException extends Exception {

            private static final long serialVersionUID = 1L;

            private DatabaseServerDownException(String message) {
                super(message);
            }

            private DatabaseServerDownException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        /**
         * Exception type thrown when the services monitor reports that the
         * keyword search server is down.
         */
        private final class KeywordSearchServerDownException extends Exception {

            private static final long serialVersionUID = 1L;

            private KeywordSearchServerDownException(String message) {
                super(message);
            }

            private KeywordSearchServerDownException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        /**
         * Exception type thrown when there is a problem creating/opening the
         * case for an auto ingest job.
         */
        private final class CaseManagementException extends Exception {

            private static final long serialVersionUID = 1L;

            private CaseManagementException(String message) {
                super(message);
            }

            private CaseManagementException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        /**
         * Exception type thrown when there is a problem analyzing a data source
         * with data source level and file level ingest modules for an auto
         * ingest job.
         */
        private final class AnalysisStartupException extends Exception {

            private static final long serialVersionUID = 1L;

            private AnalysisStartupException(String message) {
                super(message);
            }

            private AnalysisStartupException(String message, Throwable cause) {
                super(message, cause);
            }
        }

    }

    /**
     * An instance of this runnable is responsible for periodically sending auto
     * ingest job status event to remote auto ingest nodes and timing out stale
     * remote jobs. The auto ingest job status event is sent only if auto ingest
     * manager has a currently running auto ingest job.
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
                        notifyObservers(Event.JOB_STATUS_UPDATED);
                        eventPublisher.publishRemotely(new AutoIngestJobStatusEvent(currentJob));
                    }

                    if (AutoIngestUserPreferences.getStatusDatabaseLoggingEnabled()) {
                        String message;
                        boolean isError = false;
                        if (getErrorState().equals(ErrorState.NONE)) {
                            if (currentJob != null) {
                                message = "Processing " + currentJob.getManifest().getDataSourceFileName()
                                        + " for case " + currentJob.getManifest().getCaseName();
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
                                new Object[]{job.getNodeName(), job.getManifest().getFilePath().toString()});
                        hostNamesToRunningJobs.remove(job.getNodeName());
                        setChanged();
                        notifyObservers(Event.JOB_COMPLETED);
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

    /*
     * The possible states of an auto ingest manager.
     */
    private enum State {
        IDLE,
        RUNNING,
        SHUTTING_DOWN;
    }

    /*
     * Events published by an auto ingest manager. The events are published
     * locally to auto ingest manager clients that register as observers and are
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

    enum CaseDeletionResult {
        FAILED,
        PARTIALLY_DELETED,
        FULLY_DELETED
    }

    @ThreadSafe
    private static final class DataSource {

        private final String deviceId;
        private final Path path;
        private DataSourceProcessorResult resultCode;
        private List<String> errorMessages;
        private List<Content> content;

        DataSource(String deviceId, Path path) {
            this.deviceId = deviceId;
            this.path = path;
        }

        String getDeviceId() {
            return deviceId;
        }

        Path getPath() {
            return this.path;
        }

        synchronized void setDataSourceProcessorOutput(DataSourceProcessorResult result, List<String> errorMessages, List<Content> content) {
            this.resultCode = result;
            this.errorMessages = new ArrayList<>(errorMessages);
            this.content = new ArrayList<>(content);
        }

        synchronized DataSourceProcessorResult getResultDataSourceProcessorResultCode() {
            return resultCode;
        }

        synchronized List<String> getDataSourceProcessorErrorMessages() {
            return new ArrayList<>(errorMessages);
        }

        synchronized List<Content> getContent() {
            return new ArrayList<>(content);
        }

    }

    static final class AutoIngestManagerStartupException extends Exception {

        private static final long serialVersionUID = 1L;

        private AutoIngestManagerStartupException(String message) {
            super(message);
        }

        private AutoIngestManagerStartupException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
