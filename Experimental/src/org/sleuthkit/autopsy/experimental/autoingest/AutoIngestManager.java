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
import javax.annotation.concurrent.GuardedBy;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coordinationservice.CaseNodeData;
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
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException;
import org.sleuthkit.autopsy.experimental.autoingest.FileExporter.FileExportException;
import org.sleuthkit.autopsy.experimental.autoingest.ManifestFileParser.ManifestFileParserException;
import static org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus.COMPLETED;
import static org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus.DELETED;
import static org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus.PENDING;
import static org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.ProcessingStatus.PROCESSING;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;
import org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration;
import org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration.SharedConfigurationException;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJob.AutoIngestJobException;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestJob.CancellationReason;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobStartResult;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchModuleException;
import org.sleuthkit.autopsy.keywordsearch.Server;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

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
final class AutoIngestManager extends Observable implements PropertyChangeListener {

    private static final int NUM_INPUT_SCAN_SCHEDULING_THREADS = 1;
    private static final String INPUT_SCAN_SCHEDULER_THREAD_NAME = "AIM-input-scan-scheduler-%d";
    private static final String INPUT_SCAN_THREAD_NAME = "AIM-input-scan-%d";
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
    private static final int DEFAULT_PRIORITY = 0;
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
     * @throws AutoIngestManagerException if there is a problem starting auto
     *                                    ingest.
     */
    void startUp() throws AutoIngestManagerException {
        SYS_LOGGER.log(Level.INFO, "Auto ingest starting");
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestManagerException("Failed to get coordination service", ex);
        }
        try {
            eventPublisher.openRemoteEventChannel(EVENT_CHANNEL_NAME);
            SYS_LOGGER.log(Level.INFO, "Opened auto ingest event channel");
        } catch (AutopsyEventException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Failed to open auto ingest event channel", ex);
            throw new AutoIngestManagerException("Failed to open auto ingest event channel", ex);
        }
        rootInputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeImageFolder());
        rootOutputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeResultsFolder());
        inputScanSchedulingExecutor.scheduleWithFixedDelay(new InputDirScanSchedulingTask(), 0, AutoIngestUserPreferences.getMinutesOfInputScanInterval(), TimeUnit.MINUTES);
        jobProcessingTask = new JobProcessingTask();
        jobProcessingTaskFuture = jobProcessingExecutor.submit(jobProcessingTask);
        jobStatusPublishingExecutor.scheduleWithFixedDelay(new PeriodicJobStatusEventTask(), JOB_STATUS_EVENT_INTERVAL_SECONDS, JOB_STATUS_EVENT_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
        String hostName = event.getJob().getProcessingHostName();
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
        hostNamesToRunningJobs.put(hostName, event.getJob());
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
        AutoIngestJob job = event.getJob();
        synchronized (jobsLock) {
            for (Iterator<AutoIngestJob> iterator = pendingJobs.iterator(); iterator.hasNext();) {
                AutoIngestJob pendingJob = iterator.next();
                if (job.equals(pendingJob)) {
                    iterator.remove();
                    break;
                }
            }
        }
        String hostName = job.getProcessingHostName();
        hostNamesToLastMsgTime.put(hostName, Instant.now());
        hostNamesToRunningJobs.put(hostName, job);
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
        String hostName = event.getJob().getProcessingHostName();
        hostNamesToLastMsgTime.put(hostName, Instant.now());
        hostNamesToRunningJobs.remove(hostName);
        if (event.shouldRetry() == false) {
            synchronized (jobsLock) {
                AutoIngestJob job = event.getJob();
                if (completedJobs.contains(job)) {
                    completedJobs.remove(job);
                }
                completedJobs.add(event.getJob());
            }
        }
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
                    runningJobs.sort(new AutoIngestJob.LocalHostAndCaseComparator());
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
     * Removes the priority (set to zero) of all pending ingest jobs for a
     * specified case.
     *
     * @param caseName The name of the case to be deprioritized.
     *
     * @throws AutoIngestManagerException If there is an error removing the
     *                                    priority of the jobs for the case.
     */
    void deprioritizeCase(final String caseName) throws AutoIngestManagerException {
        if (state != State.RUNNING) {
            return;
        }

        List<AutoIngestJob> jobsToDeprioritize = new ArrayList<>();
        synchronized (jobsLock) {
            for (AutoIngestJob job : pendingJobs) {
                if (job.getManifest().getCaseName().equals(caseName)) {
                    jobsToDeprioritize.add(job);
                }
            }
            if (!jobsToDeprioritize.isEmpty()) {
                for (AutoIngestJob job : jobsToDeprioritize) {
                    int oldPriority = job.getPriority();
                    job.setPriority(DEFAULT_PRIORITY);
                    try {
                        this.updateCoordinationServiceManifestNode(job);
                    } catch (CoordinationServiceException | InterruptedException ex) {
                        job.setPriority(oldPriority);
                        throw new AutoIngestManagerException("Error updating case priority", ex);
                    }
                }
            }

            Collections.sort(pendingJobs, new AutoIngestJob.PriorityComparator());
        }

        if (!jobsToDeprioritize.isEmpty()) {
            new Thread(() -> {
                eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
            }).start();
        }
    }

    /**
     * Bumps the priority of all pending ingest jobs for a specified case.
     *
     * @param caseName The name of the case to be prioritized.
     *
     * @throws AutoIngestManagerException If there is an error bumping the
     *                                    priority of the jobs for the case.
     */
    void prioritizeCase(final String caseName) throws AutoIngestManagerException {

        if (state != State.RUNNING) {
            return;
        }

        List<AutoIngestJob> jobsToPrioritize = new ArrayList<>();
        int maxPriority = 0;
        synchronized (jobsLock) {
            for (AutoIngestJob job : pendingJobs) {
                if (job.getPriority() > maxPriority) {
                    maxPriority = job.getPriority();
                }
                if (job.getManifest().getCaseName().equals(caseName)) {
                    jobsToPrioritize.add(job);
                }
            }
            if (!jobsToPrioritize.isEmpty()) {
                ++maxPriority;
                for (AutoIngestJob job : jobsToPrioritize) {
                    int oldPriority = job.getPriority();
                    job.setPriority(maxPriority);
                    try {
                        this.updateCoordinationServiceManifestNode(job);
                    } catch (CoordinationServiceException | InterruptedException ex) {
                        job.setPriority(oldPriority);
                        throw new AutoIngestManagerException("Error updating case priority", ex);
                    }
                }
            }

            Collections.sort(pendingJobs, new AutoIngestJob.PriorityComparator());
        }

        if (!jobsToPrioritize.isEmpty()) {
            new Thread(() -> {
                eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
            }).start();
        }
    }

    /**
     * Removes the priority (set to zero) of an auto ingest job.
     *
     * @param manifestPath The manifest file path for the job to be
     *                     deprioritized.
     *
     * @throws AutoIngestManagerException If there is an error removing the
     *                                    priority of the job.
     */
    void deprioritizeJob(Path manifestPath) throws AutoIngestManagerException {
        if (state != State.RUNNING) {
            return;
        }

        AutoIngestJob jobToDeprioritize = null;
        synchronized (jobsLock) {
            /*
             * Find the job in the pending jobs list.
             */
            for (AutoIngestJob job : pendingJobs) {
                if (job.getManifest().getFilePath().equals(manifestPath)) {
                    jobToDeprioritize = job;
                }
            }

            /*
             * Remove the priority and update the coordination service manifest
             * node data for the job.
             */
            if (null != jobToDeprioritize) {
                int oldPriority = jobToDeprioritize.getPriority();
                jobToDeprioritize.setPriority(DEFAULT_PRIORITY);
                try {
                    this.updateCoordinationServiceManifestNode(jobToDeprioritize);
                } catch (CoordinationServiceException | InterruptedException ex) {
                    jobToDeprioritize.setPriority(oldPriority);
                    throw new AutoIngestManagerException("Error updating job priority", ex);
                }
            }

            Collections.sort(pendingJobs, new AutoIngestJob.PriorityComparator());
        }

        if (null != jobToDeprioritize) {
            final String caseName = jobToDeprioritize.getManifest().getCaseName();
            new Thread(() -> {
                eventPublisher.publishRemotely(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
            }).start();
        }
    }

    /**
     * Bumps the priority of an auto ingest job.
     *
     * @param manifestPath The manifest file path for the job to be prioritized.
     *
     * @throws AutoIngestManagerException If there is an error bumping the
     *                                    priority of the job.
     */
    void prioritizeJob(Path manifestPath) throws AutoIngestManagerException {
        if (state != State.RUNNING) {
            return;
        }

        int maxPriority = 0;
        AutoIngestJob jobToPrioritize = null;
        synchronized (jobsLock) {
            /*
             * Find the job in the pending jobs list and record the highest
             * existing priority.
             */
            for (AutoIngestJob job : pendingJobs) {
                if (job.getPriority() > maxPriority) {
                    maxPriority = job.getPriority();
                }
                if (job.getManifest().getFilePath().equals(manifestPath)) {
                    jobToPrioritize = job;
                }
            }

            /*
             * Bump the priority by one and update the coordination service
             * manifest node data for the job.
             */
            if (null != jobToPrioritize) {
                ++maxPriority;
                int oldPriority = jobToPrioritize.getPriority();
                jobToPrioritize.setPriority(maxPriority);
                try {
                    this.updateCoordinationServiceManifestNode(jobToPrioritize);
                } catch (CoordinationServiceException | InterruptedException ex) {
                    jobToPrioritize.setPriority(oldPriority);
                    throw new AutoIngestManagerException("Error updating job priority", ex);
                }
            }

            Collections.sort(pendingJobs, new AutoIngestJob.PriorityComparator());
        }

        if (null != jobToPrioritize) {
            final String caseName = jobToPrioritize.getManifest().getCaseName();
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
            /*
             * Find the job in the completed jobs list.
             */
            for (Iterator<AutoIngestJob> iterator = completedJobs.iterator(); iterator.hasNext();) {
                AutoIngestJob job = iterator.next();
                if (job.getManifest().getFilePath().equals(manifestPath)) {
                    completedJob = job;
                    iterator.remove();
                    break;
                }
            }

            /*
             * Add the job to the pending jobs queue and update the coordination
             * service manifest node data for the job.
             */
            if (null != completedJob && !completedJob.getCaseDirectoryPath().toString().isEmpty()) {
                try {
                    /**
                     * We reset the status, completion date and processing stage
                     * but we keep the original priority.
                     */
                    completedJob.setErrorsOccurred(false);
                    completedJob.setCompletedDate(new Date(0));
                    completedJob.setProcessingStatus(PENDING);
                    completedJob.setProcessingStage(AutoIngestJob.Stage.PENDING, Date.from(Instant.now()));
                    updateCoordinationServiceManifestNode(completedJob);
                    pendingJobs.add(completedJob);
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
                        AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString()));
                        AutoIngestJob deletedJob = new AutoIngestJob(nodeData);
                        deletedJob.setProcessingStatus(AutoIngestJob.ProcessingStatus.DELETED);
                        this.updateCoordinationServiceManifestNode(deletedJob);
                    } catch (AutoIngestJobNodeData.InvalidDataException | AutoIngestJobException ex) {
                        SYS_LOGGER.log(Level.WARNING, String.format("Invalid auto ingest job node data for %s", manifestPath), ex);
                        return CaseDeletionResult.PARTIALLY_DELETED;
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
                        currentJob.setProcessingStage(AutoIngestJob.Stage.CANCELLING_MODULE, Date.from(Instant.now()));
                        moduleHandle.cancel();
                        SYS_LOGGER.log(Level.INFO, "Cancelling {0} module for manifest {1}", new Object[]{moduleHandle.displayName(), currentJob.getManifest().getFilePath()});
                    }
                }
            }
        }
    }

    /**
     * Sets the coordination service manifest node.
     *
     * Note that a new auto ingest job node data object will be created from the
     * job passed in. Thus, if the data version of the node has changed, the
     * node will be "upgraded" as well as updated.
     *
     * @param job The auto ingest job.
     */
    void updateCoordinationServiceManifestNode(AutoIngestJob job) throws CoordinationServiceException, InterruptedException {
        AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(job);
        String manifestNodePath = job.getManifest().getFilePath().toString();
        byte[] rawData = nodeData.toArray();
        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, rawData);
    }

    /**
     * Sets the error flag for case node data given a case directory path.
     *
     * @param caseDirectoryPath The case directory path.
     *
     * @throws CoordinationService.CoordinationServiceException
     * @throws InterruptedException
     * @throws CaseNodeData.InvalidDataException
     */
    private void setCaseNodeDataErrorsOccurred(Path caseDirectoryPath) throws CoordinationServiceException, InterruptedException, CaseNodeData.InvalidDataException {
        CaseNodeData caseNodeData = new CaseNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, caseDirectoryPath.toString()));
        caseNodeData.setErrorsOccurred(true);
        byte[] rawData = caseNodeData.toArray();
        coordinationService.setNodeData(CoordinationService.CategoryNode.CASES, caseDirectoryPath.toString(), rawData);
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
         */
        @Override
        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) {
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }

            try {
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
                     * Update the mapping of case names to manifest paths that
                     * is used for case deletion.
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
                     * Add a job to the pending jobs queue, the completed jobs
                     * list, or do crashed job recovery, as required.
                     */
                    try {
                        byte[] rawData = coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString());
                        if (null != rawData && rawData.length > 0) {
                            try {
                                AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(rawData);
                                AutoIngestJob.ProcessingStatus processingStatus = nodeData.getProcessingStatus();
                                switch (processingStatus) {
                                    case PENDING:
                                        addPendingJob(manifest, nodeData);
                                        break;
                                    case PROCESSING:
                                        doRecoveryIfCrashed(manifest, nodeData);
                                        break;
                                    case COMPLETED:
                                        addCompletedJob(manifest, nodeData);
                                        break;
                                    case DELETED:
                                        /*
                                         * Ignore jobs marked as "deleted."
                                         */
                                        break;
                                    default:
                                        SYS_LOGGER.log(Level.SEVERE, "Unknown ManifestNodeData.ProcessingStatus");
                                        break;
                                }
                            } catch (AutoIngestJobNodeData.InvalidDataException | AutoIngestJobException ex) {
                                SYS_LOGGER.log(Level.SEVERE, String.format("Invalid auto ingest job node data for %s", manifestPath), ex);
                            }
                        } else {
                            try {
                                addNewPendingJob(manifest);
                            } catch (AutoIngestJobException ex) {
                                SYS_LOGGER.log(Level.SEVERE, String.format("Invalid manifest data for %s", manifestPath), ex);
                            }
                        }
                    } catch (CoordinationServiceException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Error transmitting node data for %s", manifestPath), ex);
                        return CONTINUE;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return TERMINATE;
                    }
                }

            } catch (Exception ex) {
                // Catch all unhandled and unexpected exceptions. Otherwise one bad file 
                // can stop the entire input folder scanning. Given that the exception is unexpected,
                // I'm hesitant to add logging which requires accessing or de-referencing data.
                SYS_LOGGER.log(Level.SEVERE, "Unexpected exception in file visitor", ex);
                return CONTINUE;
            }

            if (!Thread.currentThread().isInterrupted()) {
                return CONTINUE;
            } else {
                return TERMINATE;
            }
        }

        /**
         * Adds an existing job to the pending jobs queue.
         *
         * @param manifest The manifest for the job.
         * @param nodeData The data stored in the coordination service node for
         *                 the job.
         *
         * @throws InterruptedException if the thread running the input
         *                              directory scan task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private void addPendingJob(Manifest manifest, AutoIngestJobNodeData nodeData) throws InterruptedException, AutoIngestJobException {
            AutoIngestJob job;
            if (nodeData.getVersion() == AutoIngestJobNodeData.getCurrentVersion()) {
                job = new AutoIngestJob(nodeData);
                Path caseDirectory = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
                if (null != caseDirectory) {
                    job.setCaseDirectoryPath(caseDirectory);
                }
            } else {
                job = new AutoIngestJob(manifest);
                job.setPriority(nodeData.getPriority()); // Retain priority, present in all versions of the node data.
                Path caseDirectory = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
                if (null != caseDirectory) {
                    job.setCaseDirectoryPath(caseDirectory);
                }

                /*
                 * Try to upgrade/update the coordination service manifest node
                 * data for the job.
                 *
                 * An exclusive lock is obtained before doing so because another
                 * host may have already found the job, obtained an exclusive
                 * lock, and started processing it. However, this locking does
                 * make it possible that two processing hosts will both try to
                 * obtain the lock to do the upgrade operation at the same time.
                 * If this happens, the host that is holding the lock will
                 * complete the upgrade operation, so there is nothing more for
                 * this host to do.
                 */
                try (Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString())) {
                    if (null != manifestLock) {
                        updateCoordinationServiceManifestNode(job);
                    }
                } catch (CoordinationServiceException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to set node data for %s", manifest.getFilePath()), ex);
                }
            }
            Path caseDirectory = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
            if (null != caseDirectory) {
                job.setCaseDirectoryPath(caseDirectory);
            }
            newPendingJobsList.add(job);
        }

        /**
         * Adds a new job to the pending jobs queue.
         *
         * @param manifest The manifest for the job.
         *
         * @throws InterruptedException if the thread running the input
         *                              directory scan task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private void addNewPendingJob(Manifest manifest) throws InterruptedException, AutoIngestJobException {
            /*
             * Create the coordination service manifest node data for the job.
             * Note that getting the lock will create the node for the job (with
             * no data) if it does not already exist.
             *
             * An exclusive lock is obtained before creating the node data
             * because another host may have already found the job, obtained an
             * exclusive lock, and started processing it. However, this locking
             * does make it possible that two hosts will both try to obtain the
             * lock to do the create operation at the same time. If this
             * happens, the host that is locked out will not add the job to its
             * pending queue for this scan of the input directory, but it will
             * be picked up on the next scan.
             */
            try (Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString())) {
                if (null != manifestLock) {
                    AutoIngestJob job = new AutoIngestJob(manifest);
                    updateCoordinationServiceManifestNode(job);
                    newPendingJobsList.add(job);
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
         * @param manifest    The manifest for upgrading the node.
         * @param jobNodeData The auto ingest job node data.
         *
         * @throws InterruptedException   if the thread running the input
         *                                directory scan task is interrupted
         *                                while blocked, i.e., if auto ingest is
         *                                shutting down.
         * @throws AutoIngestJobException if there is an issue creating a new
         *                                AutoIngestJob object.
         */
        private void doRecoveryIfCrashed(Manifest manifest, AutoIngestJobNodeData jobNodeData) throws InterruptedException, AutoIngestJobException {
            /*
             * Try to get an exclusive lock on the coordination service node for
             * the job. If the lock cannot be obtained, another host in the auto
             * ingest cluster is already doing the recovery.
             */
            String manifestPath = manifest.getFilePath().toString();
            try (Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath)) {
                if (null != manifestLock) {
                    SYS_LOGGER.log(Level.SEVERE, "Attempting crash recovery for {0}", manifestPath);
                    try {
                        Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());

                        /*
                         * Create the recovery job.
                         */
                        AutoIngestJob job = new AutoIngestJob(jobNodeData);
                        int numberOfCrashes = job.getNumberOfCrashes();
                        if (numberOfCrashes <= AutoIngestUserPreferences.getMaxNumTimesToProcessImage()) {
                            ++numberOfCrashes;
                            job.setNumberOfCrashes(numberOfCrashes);
                            if (numberOfCrashes <= AutoIngestUserPreferences.getMaxNumTimesToProcessImage()) {
                                job.setCompletedDate(new Date(0));
                            } else {
                                job.setCompletedDate(Date.from(Instant.now()));
                            }
                        }

                        if (null != caseDirectoryPath) {
                            job.setCaseDirectoryPath(caseDirectoryPath);
                            job.setErrorsOccurred(true);
                            try {
                                setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                            } catch (CaseNodeData.InvalidDataException ex) {
                                SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to get case node data for %s", caseDirectoryPath), ex);
                            }
                        } else {
                            job.setErrorsOccurred(false);
                        }

                        if (numberOfCrashes <= AutoIngestUserPreferences.getMaxNumTimesToProcessImage()) {
                            job.setProcessingStatus(AutoIngestJob.ProcessingStatus.PENDING);
                            if (null != caseDirectoryPath) {
                                try {
                                    new AutoIngestJobLogger(manifest.getFilePath(), manifest.getDataSourceFileName(), caseDirectoryPath).logCrashRecoveryWithRetry();
                                } catch (AutoIngestJobLoggerException ex) {
                                    SYS_LOGGER.log(Level.SEVERE, String.format("Error creating case auto ingest log entry for crashed job for %s", manifestPath), ex);
                                }
                            }
                        } else {
                            job.setProcessingStatus(AutoIngestJob.ProcessingStatus.COMPLETED);
                            if (null != caseDirectoryPath) {
                                try {
                                    new AutoIngestJobLogger(manifest.getFilePath(), manifest.getDataSourceFileName(), caseDirectoryPath).logCrashRecoveryNoRetry();
                                } catch (AutoIngestJobLoggerException ex) {
                                    SYS_LOGGER.log(Level.SEVERE, String.format("Error creating case auto ingest log entry for crashed job for %s", manifestPath), ex);
                                }
                            }
                        }

                        /*
                         * Update the coordination service node for the job. If
                         * this fails, leave the recovery to another host.
                         */
                        try {
                            updateCoordinationServiceManifestNode(job);
                        } catch (CoordinationServiceException ex) {
                            SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to set node data for %s", manifestPath), ex);
                            return;
                        }

                        jobNodeData = new AutoIngestJobNodeData(job);

                        if (numberOfCrashes <= AutoIngestUserPreferences.getMaxNumTimesToProcessImage()) {
                            newPendingJobsList.add(job);
                        } else {
                            newCompletedJobsList.add(new AutoIngestJob(jobNodeData));
                        }

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
         * @param nodeData The data stored in the coordination service node for
         *                 the manifest.
         * @param manifest The manifest for upgrading the node.
         *
         * @throws CoordinationServiceException
         * @throws InterruptedException
         */
        private void addCompletedJob(Manifest manifest, AutoIngestJobNodeData nodeData) throws CoordinationServiceException, InterruptedException, AutoIngestJobException {
            Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
            if (null != caseDirectoryPath) {
                AutoIngestJob job;
                if (nodeData.getVersion() == AutoIngestJobNodeData.getCurrentVersion()) {
                    job = new AutoIngestJob(nodeData);
                    job.setCaseDirectoryPath(caseDirectoryPath);
                } else {
                    /**
                     * Use the manifest rather than the node data here to create
                     * a new AutoIngestJob instance because the AutoIngestJob
                     * constructor that takes a node data object expects the
                     * node data to have fields that do not exist in earlier
                     * versions.
                     */
                    job = new AutoIngestJob(manifest);
                    job.setCaseDirectoryPath(caseDirectoryPath);

                    /**
                     * Update the job with the fields that exist in all versions
                     * of the nodeData.
                     */
                    job.setCompletedDate(nodeData.getCompletedDate());
                    job.setErrorsOccurred(nodeData.getErrorsOccurred());
                    job.setPriority(nodeData.getPriority());
                    job.setNumberOfCrashes(nodeData.getNumberOfCrashes());
                    job.setProcessingStage(AutoIngestJob.Stage.COMPLETED, nodeData.getCompletedDate());
                    job.setProcessingStatus(AutoIngestJob.ProcessingStatus.COMPLETED);

                    /*
                     * Try to upgrade/update the coordination service manifest
                     * node data for the job. It is possible that two hosts will
                     * both try to obtain the lock to do the upgrade operation
                     * at the same time. If this happens, the host that is
                     * holding the lock will complete the upgrade operation.
                     */
                    try (Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString())) {
                        if (null != manifestLock) {
                            updateCoordinationServiceManifestNode(job);
                        }
                    } catch (CoordinationServiceException ex) {
                        SYS_LOGGER.log(Level.SEVERE, String.format("Error attempting to set node data for %s", manifest.getFilePath()), ex);
                    }
                }
                newCompletedJobsList.add(job);

            } else {
                SYS_LOGGER.log(Level.WARNING, String.format("Job completed for %s, but cannot find case directory, ignoring job", nodeData.getManifestFilePath()));
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
         * @param dirPath The directory about to be visited.
         * @param unused  Unused.
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
                        if (ex instanceof CoordinationServiceException || ex instanceof AutoIngestJobNodeData.InvalidDataException) {
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
                        } else if (ex instanceof AutoIngestJobLoggerException) {
                            errorState = ErrorState.JOB_LOGGER_ERROR;
                        } else if (ex instanceof AutoIngestDataSourceProcessorException) {
                            errorState = ErrorState.DATA_SOURCE_PROCESSOR_ERROR;
                        } else if (ex instanceof JobMetricsCollectionException) {
                            errorState = ErrorState.JOB_METRICS_COLLECTION_ERROR;
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
         * @throws CoordinationServiceException               if there is an
         *                                                    error acquiring or
         *                                                    releasing
         *                                                    coordination
         *                                                    service locks or
         *                                                    setting
         *                                                    coordination
         *                                                    service node data.
         * @throws SharedConfigurationException               if there is an
         *                                                    error while
         *                                                    downloading shared
         *                                                    configuration.
         * @throws ServicesMonitorException                   if there is an
         *                                                    error querying the
         *                                                    services monitor.
         * @throws DatabaseServerDownException                if the database
         *                                                    server is down.
         * @throws KeywordSearchServerDownException           if the Solr server
         *                                                    is down.
         * @throws CaseManagementException                    if there is an
         *                                                    error creating,
         *                                                    opening or closing
         *                                                    the case for the
         *                                                    job.
         * @throws AnalysisStartupException                   if there is an
         *                                                    error starting
         *                                                    analysis of the
         *                                                    data source by the
         *                                                    data source level
         *                                                    and file level
         *                                                    ingest modules.
         * @throws FileExportException                        if there is an
         *                                                    error exporting
         *                                                    files.
         * @throws AutoIngestJobLoggerException               if there is an
         *                                                    error writing to
         *                                                    the auto ingest
         *                                                    log for the case.
         * @throws InterruptedException                       if the thread
         *                                                    running the job
         *                                                    processing task is
         *                                                    interrupted while
         *                                                    blocked, i.e., if
         *                                                    auto ingest is
         *                                                    shutting down.
         * @throws AutoIngestJobNodeData.InvalidDataException if there is an
         *                                                    error constructing
         *                                                    auto ingest node
         *                                                    data objects.
         * @throws JobMetricsCollectionException              If there's an
         *                                                    issue trying to
         *                                                    collect metrics
         *                                                    for an auto ingest
         *                                                    job.
         */
        private void processJobs() throws CoordinationServiceException, SharedConfigurationException, ServicesMonitorException, DatabaseServerDownException, KeywordSearchServerDownException, CaseManagementException, AnalysisStartupException, FileExportException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException, AutoIngestJobNodeData.InvalidDataException, CaseNodeData.InvalidDataException, JobMetricsCollectionException {
            SYS_LOGGER.log(Level.INFO, "Started processing pending jobs queue");
            Lock manifestLock = JobProcessingTask.this.dequeueAndLockNextJob();
            while (null != manifestLock) {
                try {
                    if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
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

                    try {
                        AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString()));
                        if (!nodeData.getProcessingStatus().equals(PENDING)) {
                            /*
                             * Due to a timing issue or a missed event, a
                             * non-pending job has ended up on the pending
                             * queue. Skip the job and remove it from the queue.
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
                    } catch (AutoIngestJobNodeData.InvalidDataException ex) {
                        SYS_LOGGER.log(Level.WARNING, String.format("Unable to use node data for %s", manifestPath), ex); // JCTODO: Is this right?
                    }
                }
            }
            return manifestLock;
        }

        /**
         * Processes and auto ingest job.
         *
         * @throws CoordinationServiceException               if there is an
         *                                                    error acquiring or
         *                                                    releasing
         *                                                    coordination
         *                                                    service locks or
         *                                                    setting
         *                                                    coordination
         *                                                    service node data.
         * @throws SharedConfigurationException               if there is an
         *                                                    error while
         *                                                    downloading shared
         *                                                    configuration.
         * @throws ServicesMonitorException                   if there is an
         *                                                    error querying the
         *                                                    services monitor.
         * @throws DatabaseServerDownException                if the database
         *                                                    server is down.
         * @throws KeywordSearchServerDownException           if the Solr server
         *                                                    is down.
         * @throws CaseManagementException                    if there is an
         *                                                    error creating,
         *                                                    opening or closing
         *                                                    the case for the
         *                                                    job.
         * @throws AnalysisStartupException                   if there is an
         *                                                    error starting
         *                                                    analysis of the
         *                                                    data source by the
         *                                                    data source level
         *                                                    and file level
         *                                                    ingest modules.
         * @throws FileExportException                        if there is an
         *                                                    error exporting
         *                                                    files.
         * @throws AutoIngestJobLoggerException               if there is an
         *                                                    error writing to
         *                                                    the auto ingest
         *                                                    log for the case.
         * @throws InterruptedException                       if the thread
         *                                                    running the job
         *                                                    processing task is
         *                                                    interrupted while
         *                                                    blocked, i.e., if
         *                                                    auto ingest is
         *                                                    shutting down.
         * @throws AutoIngestJobNodeData.InvalidDataException if there is an
         *                                                    error constructing
         *                                                    auto ingest node
         *                                                    data objects.
         * @throws JobMetricsCollectionException              If there's an
         *                                                    issue trying to
         *                                                    collect metrics
         *                                                    for an auto ingest
         *                                                    job.
         */
        private void processJob() throws CoordinationServiceException, SharedConfigurationException, ServicesMonitorException, DatabaseServerDownException, KeywordSearchServerDownException, CaseManagementException, AnalysisStartupException, FileExportException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException, CaseNodeData.InvalidDataException, JobMetricsCollectionException {
            Path manifestPath = currentJob.getManifest().getFilePath();
            SYS_LOGGER.log(Level.INFO, "Started processing of {0}", manifestPath);
            currentJob.setProcessingStatus(AutoIngestJob.ProcessingStatus.PROCESSING);
            currentJob.setProcessingStage(AutoIngestJob.Stage.STARTING, Date.from(Instant.now()));
            currentJob.setProcessingHostName(AutoIngestManager.LOCAL_HOST_NAME);
            updateCoordinationServiceManifestNode(currentJob);
            setChanged();
            notifyObservers(Event.JOB_STARTED);
            eventPublisher.publishRemotely(new AutoIngestJobStartedEvent(currentJob));
            try {
                if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
                    return;
                }
                attemptJob();

            } finally {
                if (jobProcessingTaskFuture.isCancelled()) {
                    currentJob.cancel();
                }

                if (currentJob.isCompleted() || currentJob.isCanceled()) {
                    currentJob.setProcessingStatus(AutoIngestJob.ProcessingStatus.COMPLETED);
                    Date completedDate = new Date();
                    currentJob.setCompletedDate(completedDate);
                } else {
                    // The job may get retried
                    currentJob.setProcessingStatus(AutoIngestJob.ProcessingStatus.PENDING);
                }
                currentJob.setProcessingHostName("");
                updateCoordinationServiceManifestNode(currentJob);

                boolean retry = (!currentJob.isCanceled() && !currentJob.isCompleted());
                SYS_LOGGER.log(Level.INFO, "Completed processing of {0}, retry = {1}", new Object[]{manifestPath, retry});
                if (currentJob.isCanceled()) {
                    Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
                    if (null != caseDirectoryPath) {
                        setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                        AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, currentJob.getManifest().getDataSourceFileName(), caseDirectoryPath);
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
         * @throws JobMetricsCollectionException    If there's an issue trying
         *                                          to collect metrics for an
         *                                          auto ingest job.
         */
        private void attemptJob() throws CoordinationServiceException, SharedConfigurationException, ServicesMonitorException, DatabaseServerDownException, KeywordSearchServerDownException, CaseManagementException, AnalysisStartupException, FileExportException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException, CaseNodeData.InvalidDataException, JobMetricsCollectionException {
            updateConfiguration();
            if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }
            verifyRequiredSevicesAreRunning();
            if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }
            Case caseForJob = openCase();
            try {
                if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
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
                Path manifestPath = currentJob.getManifest().getFilePath();
                SYS_LOGGER.log(Level.INFO, "Downloading shared configuration for {0}", manifestPath);
                currentJob.setProcessingStage(AutoIngestJob.Stage.UPDATING_SHARED_CONFIG, Date.from(Instant.now()));
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
            Path manifestPath = currentJob.getManifest().getFilePath();
            SYS_LOGGER.log(Level.INFO, "Checking services availability for {0}", manifestPath);
            currentJob.setProcessingStage(AutoIngestJob.Stage.CHECKING_SERVICES, Date.from(Instant.now()));
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
            String caseName = manifest.getCaseName();
            SYS_LOGGER.log(Level.INFO, "Opening case {0} for {1}", new Object[]{caseName, manifest.getFilePath()});
            currentJob.setProcessingStage(AutoIngestJob.Stage.OPENING_CASE, Date.from(Instant.now()));
            /*
             * Acquire and hold a case name lock so that only one node at as
             * time can scan the output directory at a time. This prevents
             * making duplicate cases for the saem auto ingest case.
             */
            try (Lock caseLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseName, 30, TimeUnit.MINUTES)) {
                if (null != caseLock) {
                    try {
                        Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, caseName);
                        if (null != caseDirectoryPath) {
                            Path metadataFilePath = caseDirectoryPath.resolve(caseName + CaseMetadata.getFileExtension());
                            Case.openAsCurrentCase(metadataFilePath.toString());
                        } else {
                            caseDirectoryPath = PathUtils.createCaseFolderPath(rootOutputDirectory, caseName);
                            
                            // Create the case directory now in case it is needed by selectSolrServerForCase
                            Case.createCaseDirectory(caseDirectoryPath.toString(), CaseType.MULTI_USER_CASE);
                            
                            // If a list of servers exists, choose one to use for this case
                            Server.selectSolrServerForCase(rootOutputDirectory, caseDirectoryPath);
                            
                            CaseDetails caseDetails = new CaseDetails(caseName);
                            Case.createAsCurrentCase(CaseType.MULTI_USER_CASE, caseDirectoryPath.toString(), caseDetails);
                            /*
                             * Sleep a bit before releasing the lock to ensure
                             * that the new case folder is visible on the
                             * network.
                             */
                            Thread.sleep(AutoIngestUserPreferences.getSecondsToSleepBetweenCases() * 1000);
                        }
                        currentJob.setCaseDirectoryPath(caseDirectoryPath);
                        Case caseForJob = Case.getOpenCase();
                        SYS_LOGGER.log(Level.INFO, "Opened case {0} for {1}", new Object[]{caseForJob.getName(), manifest.getFilePath()});
                        return caseForJob;

                    } catch (KeywordSearchModuleException ex) {
                        throw new CaseManagementException(String.format("Error creating solr settings file for case %s for %s", caseName, manifest.getFilePath()), ex);
                    } catch (CaseActionException ex) {
                        throw new CaseManagementException(String.format("Error creating or opening case %s for %s", caseName, manifest.getFilePath()), ex);
                    } catch (NoCurrentCaseException ex) {
                        /*
                         * Deal with the unfortunate fact that
                         * Case.getOpenCase throws NoCurrentCaseException.
                         */
                        throw new CaseManagementException(String.format("Error getting current case %s for %s", caseName, manifest.getFilePath()), ex);
                    }
                } else {
                    throw new CaseManagementException(String.format("Timed out acquiring case name lock for %s for %s", caseName, manifest.getFilePath()));
                }
            }
        }

        /**
         * Runs the ingest process for the current job.
         *
         * @param caseForJob The case for the job.
         *
         * @throws CoordinationServiceException  if there is an error acquiring
         *                                       or releasing coordination
         *                                       service locks or setting
         *                                       coordination service node data.
         * @throws AnalysisStartupException      if there is an error starting
         *                                       analysis of the data source by
         *                                       the data source level and file
         *                                       level ingest modules.
         * @throws FileExportException           if there is an error exporting
         *                                       files.
         * @throws AutoIngestJobLoggerException  if there is an error writing to
         *                                       the auto ingest log for the
         *                                       case.
         * @throws InterruptedException          if the thread running the job
         *                                       processing task is interrupted
         *                                       while blocked, i.e., if auto
         *                                       ingest is shutting down.
         * @throws JobMetricsCollectionException If there's an issue trying to
         *                                       collect metrics for an auto
         *                                       ingest job.
         */
        private void runIngestForJob(Case caseForJob) throws CoordinationServiceException, AnalysisStartupException, FileExportException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException, CaseNodeData.InvalidDataException, JobMetricsCollectionException {
            try {
                if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
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
         * @throws AnalysisStartupException      If there is an error starting
         *                                       analysis of the data source by
         *                                       the data source level and file
         *                                       level ingest modules.
         * @throws FileExportException           If there is an error exporting
         *                                       files.
         * @throws AutoIngestJobLoggerException  If there is an error writing to
         *                                       the auto ingest log for the
         *                                       case.
         * @throws InterruptedException          If the thread running the job
         *                                       processing task is interrupted
         *                                       while blocked, i.e., if auto
         *                                       ingest is shutting down.
         * @throws JobMetricsCollectionException If there's an issue trying to
         *                                       collect metrics for an auto
         *                                       ingest job.
         */
        private void ingestDataSource(Case caseForJob) throws AnalysisStartupException, FileExportException, AutoIngestJobLoggerException, InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException, CaseNodeData.InvalidDataException, CoordinationServiceException, JobMetricsCollectionException {
            if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }

            AutoIngestDataSource dataSource = identifyDataSource();
            if (null == dataSource) {
                currentJob.setProcessingStage(AutoIngestJob.Stage.COMPLETED, Date.from(Instant.now()));
                return;
            }

            if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }

            runDataSourceProcessor(caseForJob, dataSource);
            if (dataSource.getContent().isEmpty()) {
                currentJob.setProcessingStage(AutoIngestJob.Stage.COMPLETED, Date.from(Instant.now()));
                return;
            }

            if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
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

            if (currentJob.isCanceled() || jobProcessingTaskFuture.isCancelled()) {
                return;
            }

            collectMetrics(caseForJob.getSleuthkitCase(), dataSource);
            exportFiles(dataSource);
        }

        /**
         * Identifies the type of the data source specified in the manifest for
         * the current job and extracts it if required.
         *
         * @return A data source object.
         *
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the auto
         *                                      ingest job processing task is
         *                                      interrupted while blocked, i.e.,
         *                                      if auto ingest is shutting down.
         */
        private AutoIngestDataSource identifyDataSource() throws AutoIngestJobLoggerException, InterruptedException, CaseNodeData.InvalidDataException, CoordinationServiceException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Identifying data source for {0} ", manifestPath);
            currentJob.setProcessingStage(AutoIngestJob.Stage.IDENTIFYING_DATA_SOURCE, Date.from(Instant.now()));
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            Path dataSourcePath = manifest.getDataSourcePath();
            File dataSource = dataSourcePath.toFile();
            if (!dataSource.exists()) {
                SYS_LOGGER.log(Level.SEVERE, "Missing data source for {0}", manifestPath);
                currentJob.setErrorsOccurred(true);
                setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                jobLogger.logMissingDataSource();
                return null;
            }
            String deviceId = manifest.getDeviceId();
            return new AutoIngestDataSource(deviceId, dataSourcePath);
        }

        /**
         * Passes the data source for the current job through a data source
         * processor that adds it to the case database.
         *
         * @param dataSource The data source.
         *
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void runDataSourceProcessor(Case caseForJob, AutoIngestDataSource dataSource) throws InterruptedException, AutoIngestJobLoggerException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException, CaseNodeData.InvalidDataException, CoordinationServiceException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Adding data source for {0} ", manifestPath);
            currentJob.setProcessingStage(AutoIngestJob.Stage.ADDING_DATA_SOURCE, Date.from(Instant.now()));
            DataSourceProcessorProgressMonitor progressMonitor = new DoNothingDSPProgressMonitor();
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {

                // Get an ordered list of data source processors to try
                List<AutoIngestDataSourceProcessor> validDataSourceProcessors;
                try {
                    validDataSourceProcessors = DataSourceProcessorUtility.getOrderedListOfDataSourceProcessors(dataSource.getPath());
                } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
                    SYS_LOGGER.log(Level.SEVERE, "Exception while determining best data source processor for {0}", dataSource.getPath());
                    // rethrow the exception. It will get caught & handled upstream and will result in AIM auto-pause.
                    throw ex;
                }

                // did we find a data source processor that can process the data source
                if (validDataSourceProcessors.isEmpty()) {
                    // This should never happen. We should add all unsupported data sources as logical files.
                    setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                    currentJob.setErrorsOccurred(true);
                    jobLogger.logFailedToIdentifyDataSource();
                    SYS_LOGGER.log(Level.WARNING, "Unsupported data source {0} for {1}", new Object[]{dataSource.getPath(), manifestPath});  // NON-NLS
                    return;
                }

                synchronized (ingestLock) {
                    // Try each DSP in decreasing order of confidence
                    for (AutoIngestDataSourceProcessor selectedProcessor : validDataSourceProcessors) {
                        UUID taskId = UUID.randomUUID();
                        caseForJob.notifyAddingDataSource(taskId);
                        DataSourceProcessorCallback callBack = new AddDataSourceCallback(caseForJob, dataSource, taskId, ingestLock);
                        caseForJob.notifyAddingDataSource(taskId);
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
                            setCaseNodeDataErrorsOccurred(caseDirectoryPath);
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
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void logDataSourceProcessorResult(AutoIngestDataSource dataSource) throws AutoIngestJobLoggerException, InterruptedException, CaseNodeData.InvalidDataException, CoordinationServiceException {
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
                            setCaseNodeDataErrorsOccurred(caseDirectoryPath);
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
                            setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                            jobLogger.logNoDataSourceContent();
                        }
                        break;

                    case CRITICAL_ERRORS:
                        for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                            SYS_LOGGER.log(Level.SEVERE, "Critical error running data source processor for {0}: {1}", new Object[]{manifestPath, errorMessage});
                        }
                        currentJob.setErrorsOccurred(true);
                        setCaseNodeDataErrorsOccurred(caseDirectoryPath);
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
                setCaseNodeDataErrorsOccurred(caseDirectoryPath);
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
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void analyze(AutoIngestDataSource dataSource) throws AnalysisStartupException, AutoIngestJobLoggerException, InterruptedException, CaseNodeData.InvalidDataException, CoordinationServiceException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Starting ingest modules analysis for {0} ", manifestPath);
            currentJob.setProcessingStage(AutoIngestJob.Stage.ANALYZING_DATA_SOURCE, Date.from(Instant.now()));
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
                            SYS_LOGGER.log(Level.INFO, "Finished ingest modules analysis for {0} ", manifestPath);
                            IngestJob.ProgressSnapshot jobSnapshot = ingestJob.getSnapshot();
                            for (IngestJob.ProgressSnapshot.DataSourceProcessingSnapshot snapshot : jobSnapshot.getDataSourceSnapshots()) {
                                if (!snapshot.isCancelled()) {
                                    List<String> cancelledModules = snapshot.getCancelledDataSourceIngestModules();
                                    if (!cancelledModules.isEmpty()) {
                                        SYS_LOGGER.log(Level.WARNING, String.format("Ingest module(s) cancelled for %s", manifestPath));
                                        currentJob.setErrorsOccurred(true);
                                        setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                                        for (String module : snapshot.getCancelledDataSourceIngestModules()) {
                                            SYS_LOGGER.log(Level.WARNING, String.format("%s ingest module cancelled for %s", module, manifestPath));
                                            jobLogger.logIngestModuleCancelled(module);
                                        }
                                    }
                                    jobLogger.logAnalysisCompleted();
                                } else {
                                    currentJob.setProcessingStage(AutoIngestJob.Stage.CANCELLING, Date.from(Instant.now()));
                                    currentJob.setErrorsOccurred(true);
                                    setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                                    jobLogger.logAnalysisCancelled();
                                    CancellationReason cancellationReason = snapshot.getCancellationReason();
                                    if (CancellationReason.NOT_CANCELLED != cancellationReason && CancellationReason.USER_CANCELLED != cancellationReason) {
                                        throw new AnalysisStartupException(String.format("Analysis cancelled due to %s for %s", cancellationReason.getDisplayName(), manifestPath));
                                    }
                                }
                            }
                        } else if (!ingestJobStartResult.getModuleErrors().isEmpty()) {
                            for (IngestModuleError error : ingestJobStartResult.getModuleErrors()) {
                                SYS_LOGGER.log(Level.SEVERE, String.format("%s ingest module startup error for %s", error.getModuleDisplayName(), manifestPath), error.getThrowable());
                            }
                            currentJob.setErrorsOccurred(true);
                            setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                            jobLogger.logIngestModuleStartupErrors();
                            throw new AnalysisStartupException(String.format("Error(s) during ingest module startup for %s", manifestPath));
                        } else {
                            SYS_LOGGER.log(Level.SEVERE, String.format("Ingest manager ingest job start error for %s", manifestPath), ingestJobStartResult.getStartupException());
                            currentJob.setErrorsOccurred(true);
                            setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                            jobLogger.logAnalysisStartupError();
                            throw new AnalysisStartupException("Ingest manager error starting job", ingestJobStartResult.getStartupException());
                        }
                    } else {
                        for (String warning : settingsWarnings) {
                            SYS_LOGGER.log(Level.SEVERE, "Ingest job settings error for {0}: {1}", new Object[]{manifestPath, warning});
                        }
                        currentJob.setErrorsOccurred(true);
                        setCaseNodeDataErrorsOccurred(caseDirectoryPath);
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
         * Gather metrics to store in auto ingest job nodes. A SleuthkitCase
         * instance is used to get the content size.
         *
         * @param caseDb     The SleuthkitCase instance.
         * @param dataSource The auto ingest data source.
         *
         * @throws CoordinationServiceException  If there's a problem retrieving
         *                                       data from the coordination
         *                                       service.
         * @throws InterruptedException          If the thread calling the
         *                                       coordination service is
         *                                       interrupted.
         * @throws JobMetricsCollectionException If there's an issue trying to
         *                                       retreive data from the
         *                                       database.
         */
        private void collectMetrics(SleuthkitCase caseDb, AutoIngestDataSource dataSource) throws CoordinationServiceException, InterruptedException, JobMetricsCollectionException {
            /*
             * Get the data source size and store it in the current job.
             */
            List<Content> contentList = dataSource.getContent();
            long dataSourceSize = 0;
            try {
                for (Content content : contentList) {
                    dataSourceSize += ((DataSource) content).getContentSize(caseDb);
                }
            } catch (TskCoreException ex) {
                throw new JobMetricsCollectionException("Unable to get the data content size.", ex);
            }
            currentJob.setDataSourceSize(dataSourceSize);

            /*
             * Create node data from the current job and store it.
             */
            AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(currentJob);
            String manifestNodePath = currentJob.getManifest().getFilePath().toString();
            coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodePath, nodeData.toArray());
        }

        /**
         * Exports any files from the data source for the current job that
         * satisfy any user-defined file export rules.
         *
         * @param dataSource The data source.
         *
         * @throws FileExportException          if there is an error exporting
         *                                      the files.
         * @throws AutoIngestJobLoggerException if there is an error writing to
         *                                      the auto ingest log for the
         *                                      case.
         * @throws InterruptedException         if the thread running the job
         *                                      processing task is interrupted
         *                                      while blocked, i.e., if auto
         *                                      ingest is shutting down.
         */
        private void exportFiles(AutoIngestDataSource dataSource) throws FileExportException, AutoIngestJobLoggerException, InterruptedException, CaseNodeData.InvalidDataException, CoordinationServiceException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            SYS_LOGGER.log(Level.INFO, "Exporting files for {0}", manifestPath);
            currentJob.setProcessingStage(AutoIngestJob.Stage.EXPORTING_FILES, Date.from(Instant.now()));
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {
                FileExporter fileExporter = new FileExporter();
                if (fileExporter.isEnabled()) {
                    fileExporter.process(manifest.getDeviceId(), dataSource.getContent(), currentJob::isCanceled);
                    jobLogger.logFileExportCompleted();
                }
            } catch (FileExportException ex) {
                SYS_LOGGER.log(Level.SEVERE, String.format("Error doing file export for %s", manifestPath), ex);
                currentJob.setErrorsOccurred(true);
                setCaseNodeDataErrorsOccurred(caseDirectoryPath);
                jobLogger.logFileExportError();
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
                            ingestLock.notify();
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

        /**
         * Exception thrown when an issue occurs trying to collect metrics for
         * an auto ingest job.
         */
        private final class JobMetricsCollectionException extends Exception {

            private static final long serialVersionUID = 1L;

            private JobMetricsCollectionException(String message) {
                super(message);
            }

            private JobMetricsCollectionException(String message, Throwable cause) {
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
                        currentJob.getProcessingStageDetails();
                        setChanged();
                        notifyObservers(Event.JOB_STATUS_UPDATED);
                        updateCoordinationServiceManifestNode(currentJob);
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
                    if (isStale(hostNamesToLastMsgTime.get(job.getProcessingHostName()))) {
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
                        hostNamesToRunningJobs.remove(job.getProcessingHostName());
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
    public enum Event {

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
        JOB_LOGGER_ERROR("Job logger error"),
        DATA_SOURCE_PROCESSOR_ERROR("Data source processor error"),
        UNEXPECTED_EXCEPTION("Unknown error"),
        JOB_METRICS_COLLECTION_ERROR("Job metrics collection error");

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

    static final class AutoIngestManagerException extends Exception {

        private static final long serialVersionUID = 1L;

        private AutoIngestManagerException(String message) {
            super(message);
        }

        private AutoIngestManagerException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
