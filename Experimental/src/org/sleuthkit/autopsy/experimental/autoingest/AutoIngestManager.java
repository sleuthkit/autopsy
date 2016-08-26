/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Observable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.experimental.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.experimental.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.experimental.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.core.ServicesMonitor.ServicesMonitorException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.ingest.IngestJobStartResult;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.experimental.autoingest.FileExporter.FileExportException;
import org.sleuthkit.autopsy.experimental.autoingest.ManifestFileParser.ManifestFileParserException;
import org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.PENDING;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.PROCESSING;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.COMPLETED;
import org.sleuthkit.autopsy.corecomponentinterfaces.AutomatedIngestDataSourceProcessor;
import org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration.SharedConfigurationException;

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
    private static final String INPUT_SCAN_SCHEDULER_THREAD_NAME = "AIM-folder-scan-scheduler-%d";
    private static final long INPUT_SCAN_INTERVAL = 300000; // 5 minutes
    private static final String INPUT_SCAN_THREAD_NAME = "AIM-folder-scan-%d";
    private static int DEFAULT_JOB_PRIORITY = 0;
    private static final String AUTO_INGEST_THREAD_NAME = "AIM-auto-ingest-%d";
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
    private static final int PRIORITIZATION_LOCK_TIME_OUT = 10;
    private static final TimeUnit PRIORITIZATION_LOCK_TIME_OUT_UNIT = TimeUnit.SECONDS;
    private static final java.util.logging.Logger LOGGER = AutoIngestSystemLogger.getLogger(); // RJCTODO: Rename to systemLogger
    private static AutoIngestManager instance;
    private volatile State state;
    private final AutopsyEventPublisher eventPublisher;
    private final ScheduledThreadPoolExecutor inputScanSchedulingExecutor;
    private final ExecutorService inputScanExecutor;
    private final ExecutorCompletionService<Void> inputScanCompletionService;
    private final ExecutorService jobProcessingExecutor;
    private final ScheduledThreadPoolExecutor jobStatusPublishingExecutor;
    private final ConcurrentHashMap<String, Instant> hostNamesToLastMsgTime;
    private final ConcurrentHashMap<String, AutoIngestJob> hostNamesToRunningJobs;
    private final Object jobsLock;
    @GuardedBy("jobsLock")
    private List<AutoIngestJob> pendingJobs;
    // RJCTODO: Revisit this being volatile vs. fully guarded. 
    // Perhaps the job should be passed through the ingest process and this
    // reference should be used for everything else.
    @GuardedBy("jobsLock")
    private volatile AutoIngestJob currentJob;
    @GuardedBy("jobsLock")
    private List<AutoIngestJob> completedJobs;
    private CoordinationService coordinationService;
    private JobProcessingTask jobProcessingTask;
    private Future<?> jobProcessingTaskFuture;
    private Path rootInputDirectory;
    private Path rootOutputDirectory;

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
        LOGGER.log(Level.INFO, "Initializing auto ingest");
        state = State.IDLE;
        eventPublisher = new AutopsyEventPublisher();
        inputScanSchedulingExecutor = new ScheduledThreadPoolExecutor(NUM_INPUT_SCAN_SCHEDULING_THREADS, new ThreadFactoryBuilder().setNameFormat(INPUT_SCAN_SCHEDULER_THREAD_NAME).build());
        inputScanExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(INPUT_SCAN_THREAD_NAME).build());
        inputScanCompletionService = new ExecutorCompletionService<>(inputScanExecutor);
        jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(AUTO_INGEST_THREAD_NAME).build());
        jobStatusPublishingExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(JOB_STATUS_PUBLISHING_THREAD_NAME).build());
        hostNamesToRunningJobs = new ConcurrentHashMap<>();
        hostNamesToLastMsgTime = new ConcurrentHashMap<>();
        jobsLock = new Object();
        pendingJobs = new ArrayList<>();
        completedJobs = new ArrayList<>();
    }

    /**
     * Starts up auto ingest.
     *
     * @throws AutoIngestManagerStartupException if there is a problem starting
     *                                           auto ingest.
     */
    void startUp() throws AutoIngestManagerStartupException {
        LOGGER.log(Level.INFO, "Auto ingest starting");
        try {
            coordinationService = CoordinationService.getInstance(CoordinationServiceNamespace.getRoot());
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestManagerStartupException("Failed to get coordination service", ex);
        }
        rootInputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeImageFolder());
        rootOutputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeResultsFolder());
        inputScanSchedulingExecutor.scheduleAtFixedRate(new InputDirScanSchedulingTask(), 0, INPUT_SCAN_INTERVAL, TimeUnit.MILLISECONDS);
        jobProcessingTask = new JobProcessingTask();
        jobProcessingTaskFuture = jobProcessingExecutor.submit(jobProcessingTask);
        jobStatusPublishingExecutor.scheduleAtFixedRate(new PeriodicJobStatusEventTask(), JOB_STATUS_EVENT_INTERVAL_SECONDS, JOB_STATUS_EVENT_INTERVAL_SECONDS, TimeUnit.SECONDS); // RJCTODO: This is started before the event channel is opened!
        eventPublisher.addSubscriber(EVENT_LIST, instance);
        RuntimeProperties.setCoreComponentsActive(false);
        state = State.RUNNING;
    }

    /**
     * Opens the remote event channel that enables communication with auto
     * ingest managers on other nodes.
     *
     * @throws AutopsyEventException If the channel could not be opened.
     */
    void establishRemoteCommunications() throws AutopsyEventException {
        try {
            eventPublisher.openRemoteEventChannel(EVENT_CHANNEL_NAME);
            LOGGER.log(Level.INFO, "Opened auto ingest event channel");
        } catch (AutopsyEventException ex) {
            throw ex;
        }
    }

    /**
     * Handles auto ingest events published by other auto ingest nodes.
     *
     * @param event An auto ingest event from another node.
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event instanceof AutopsyEvent) {
            if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.REMOTE) { // RJCTODO: Is this really necessary?
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
     * Processes a job status event from another node by removing the job in the
     * event from the collection of jobs running on other hosts and adding it to
     * the list of completed jobs.
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
        scanInputDirsNow();
        setChanged();
        notifyObservers(Event.JOB_COMPLETED);
    }

    /**
     * RJCTODO
     *
     * @param event
     */
    private void handleRemoteCasePrioritizationEvent(AutoIngestCasePrioritizedEvent event) {
        String hostName = event.getNodeName();
        hostNamesToLastMsgTime.put(hostName, Instant.now());
        scanInputDirsNow();
        setChanged();
        notifyObservers(Event.CASE_PRIORITIZED);
    }

    /**
     * RJCTODO
     *
     * @param event
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
        if (state != State.RUNNING) {
            return;
        }

        LOGGER.log(Level.INFO, "Auto ingest shutting down");
        state = State.SHUTTING_DOWN;
        try {
            eventPublisher.removeSubscriber(EVENT_LIST, instance);
            stopInputFolderScans();
            stopJobProcessing();
            cleanupJobs();
            eventPublisher.closeRemoteEventChannel(); // close remote channel

        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "Auto ingest interrupted during shut down", ex);
        }
        LOGGER.log(Level.INFO, "Auto ingest shut down");
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
            LOGGER.log(Level.WARNING, "Auto ingest waited at least thirty seconds for input scan scheduling executor to shut down, continuing to wait"); //NON-NLS
        }
        while (!inputScanExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            LOGGER.log(Level.WARNING, "Auto ingest waited at least thirty seconds for input scan executor to shut down, continuing to wait"); //NON-NLS
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
            LOGGER.log(Level.WARNING, "Auto ingest waited at least thirty seconds for job processing executor to shut down, continuing to wait"); //NON-NLS
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
     * Gets snapshots of the auto ingest manager's job pending, running and
     * completed jobs. A collection can be excluded by passing a null for the
     * correspioding in/out list parameter.
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
        if (state != State.RUNNING) {
            return;
        }
        inputScanCompletionService.submit(new InputDirScanTask());
    }

    /**
     * Pauses processing of the pending jobs queue. The currently running job
     * will continue to run to completion.
     */
    void pause() {
        if (state != State.RUNNING) {
            return;
        }
        jobProcessingTask.requestPause();
    }

    /**
     * Resumes processing of the pending jobs queue.
     */
    void resume() {
        if (state != State.RUNNING) {
            return;
        }
        jobProcessingTask.requestResume();
    }

    /**
     * Bumps the priority of all pending ingest jobs for a specified case.
     *
     * @param caseName The name of the case to be prioritized.
     *
     * @return A snapshot of the pending jobs queue after prioritization.
     */
    // RJCTODO: Re-implement and document.
    List<AutoIngestJob> prioritizeCase(String caseName) throws IOException {

//        if (state != State.RUNNING) {
        return Collections.emptyList();
//        }

        /*
         * Bump the priority of every manifest associated with the specified
         * case.
         */
        // RJCTODO: Perhaps this needs to be done as follows:
        // 1. Do a scan.
        // 2. Check the highest priority of all jobs in the pending queue by
        //    querying the AutoIngestJob onjects.
        // 3. Calculate a new priority.
        // 4. Set the node data and return the list 
//        Files.walkFileTree(rootInputDirectory, new SimpleFileVisitor<Path>() {
//            @Override
//            public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
//                try {
//                    for (ManifestFileParser parser : Lookup.getDefault().lookupAll(ManifestFileParser.class)) {
//                        if (parser.fileIsManifest(filePath)) {
//                            Manifest manifest = parser.parse(filePath);
//                            if (caseName.equals(manifest.getCaseName())) {
//                                AutoIngestManager.this.prioritizeManifest(filePath);
//                            }
//                        }
//                    }
//                } catch (ManifestFileParserException | CoordinationServiceException | InterruptedException ex) {
//                    // RJCTODO: Logging at a minimum
//                }
//                // RJCTODO: Logging at a minimum
//                return CONTINUE;
//            }
//
//            @Override
//            public FileVisitResult visitFileFailed(Path filePath, IOException ex) throws IOException {
//                LOGGER.log(Level.SEVERE, String.format("Error while visiting %s during prioritization of case ", filePath.toString()), ex);
//                return CONTINUE;
//            }
//        });
        // RJCTODO: Perhaps now this needs to do
        /**
         * Immediately bump all jobs for this case to the top of the pending
         * queue. Note that there is a possibility that the queue will be
         * reordered again as soon as the monitor is released.
         */
//        List<AutoIngestJob> pendingJobsSnapshot = new ArrayList<>();
//        synchronized (jobsLock) {
//            for (AutoIngestJob job : pendingJobs) {
//                if (job.getManifest().getCaseName().equals(caseName)) {
//                    job.setPrioritizedFileTimeStamp(new Date());
//                }
//            }
//            Collections.sort(pendingJobs, prioritizedPendingListComparator);
//            pendingJobsSnapshot.addAll(pendingJobs);
//        }
        /**
         * Publish the event on a separate thread for a speedier return from
         * this method.
         */
//        new Thread(
//                () -> {
//                    eventPublisher.publish(new AutoIngestCasePrioritizedEvent(LOCAL_HOST_NAME, caseName));
//                }
//        ).start();
//        return pendingJobsSnapshot;
    }

    /**
     * Writes or updates a prioritized state file for an image input folder and
     * publishes a prioritization event.
     *
     * @param caseName        The name of the case associated with the input
     *                        image folder to be prioritized.
     * @param imageFolderPath The name of the input image folder to be
     *                        prioritized.
     *
     * @return A snapshot of the pending jobs queue after prioritization.
     *
     * @throws IOException
     */
    // RJCTODO: Re-implement and document.
    List<AutoIngestJob> prioritizeJob(String caseName, String imageFolderPath) throws IOException {

//        if (state != State.RUNNING) {
        return Collections.emptyList();
//        }

//        /*
//         * Write or update the prioritized state file for the folder.
//         */
//        Path inputFolderPath = rootImageFolderPath.resolve(caseName).resolve(imageFolderPath);
//        prioritizeFolder(inputFolderPath);
//
//        /**
//         * Immediately bump this job up to the top of the pending queue. Note
//         * that there is a possibility that the queue will be reordered again as
//         * soon as the monitor is released.
//         */
//        List<AutoIngestJob> pendingJobsSnapshot = new ArrayList<>();
//        synchronized (jobListsMonitor) {
//            for (AutoIngestJob job : pendingJobs) {
//                if (job.getManifest().getFilePath().equals(inputFolderPath)) {
//                    job.setPrioritizedFileTimeStamp(new Date());
//                    break;
//                }
//            }
//            Collections.sort(pendingJobs, prioritizedPendingListComparator);
//            pendingJobsSnapshot.addAll(pendingJobs);
//        }
//
//        /**
//         * Publish the event on a separate thread for a speedier return from
//         * this method.
//         */
//        new Thread(() -> {
//            eventPublisher.publish(new AutoIngestCasePrioritizedEvent(localHostName, caseName));
//        }).start();
//
//        return pendingJobsSnapshot;
    }

    /**
     * Updates the priority of a coordination service manifest node.
     *
     * @param manifestPath The manifest file path.
     *
     * @throws CoordinationServiceException if there is a problem interacting
     *                                      with the coordination service.
     * @throws InterruptedException         if interrupted while trying to get
     *                                      an exclusive lock on the manifest
     *                                      node.
     */
    // RJCTODO: Re-implement and document.
    private void prioritizeManifest(Path manifestPath) throws CoordinationServiceException, InterruptedException {
//        Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString(), PRIORITIZATION_LOCK_TIME_OUT, PRIORITIZATION_LOCK_TIME_OUT_UNIT);
//        if (null != manifestLock) {
//            ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString()));
//            int priority = nodeData.getPriority();
//            ++priority;
//            nodeData.setPriority(priority);
//            coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString(), nodeData.toArray());
//        }
    }

    /**
     * Attempts to delete the output folder for a case.
     *
     * @param caseOutputFolderPath The case output folder path.
     *
     * @parame deleteInput Flag to delete images used as input for the case.
     * @return CaseDeletionResult structure containing deletion status.
     */
    // RJCTODO: Re-implement and document.
    CaseDeletionResult deleteCase(Path caseDirectoryPath, boolean physicallyDeleteImageFolders, String caseMetadataFilePath) {
//        String caseName = PathUtils.caseNameFromCaseDirectoryPath(caseDirectoryPath);
//        Path caseDirectoryPath = rootInputDirectory.resolve(caseName);
//        if (state != State.RUNNING) {
//            return new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.FAILED);
//        }
//
//        Lock caseDirLock = null;
//        List<Lock> manifestFileLocks = new ArrayList<>();
//        try {
//            /**
//             * Start by acquiring the jobs lock. This blocks input directory
//             * scans during the delete operation and makes it possible to remove
//             * jobs from the job lists.
//             */
//            CaseDeletionResult result;
//            synchronized (jobsLock) {
//                /*
//                 * Acquire an exclusive lock on the case directyory so it can be
//                 * safely deleted. This will fail if the case is open for review
//                 * or a deletion operation on this case is already in progress
//                 * on another node.
//                 */
//                caseDirLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseDirectoryPath.toString());
//                if (null == caseDirLock) {
//                    return new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.FAILED);
//                }
//
//                /*
//                 * Do a fresh input directory scan.
//                 */
//                InputDirScanner scanner = new InputDirScanner();
//                scanner.scan();
//
//                /*
//                 * Acquire exclusive locks on all of the manifest files for the
//                 * case so that they can be safely deleted.
//                 */
//                if (!acquireAllExclusiveManifestFileLocks(caseName, pendingJobs, manifestFileLocks)) {
//                    return new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.FAILED);
//                }
//                if (!acquireAllExclusiveManifestFileLocks(caseName, completedJobs, manifestFileLocks)) {
//                    return new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.FAILED);
//                }
//
//                /*
//                 * Try to unload/delete the Solr core from the Solr server.
//                 */
//                CaseMetadata metaData = new CaseMetadata(caseDirectoryPath.resolve(caseMetadataFilePath));
//                unloadSolrCore(caseDirectoryPath, metaData.getTextIndexName());
//
//                /*
//                 * Delete the case folder, which includes the Solr index files.
//                 */
//                File caseDirectory = caseDirectoryPath.toFile();
//                FileUtil.deleteDir(caseDirectory);
//                if (caseDirectory.exists()) {
//                    LOGGER.log(Level.SEVERE, String.format("Failed to either physically or logically cannot delete %s", caseDirectory));
//                    return new CaseDeletionResult(caseName, caseDirectory.toString(), manifest.toString(), CaseDeletionResult.Status.FAILED);
//                }
//
//                /*
//                 * Delete the case database from the database server.
//                 */
//                deleteCaseDatabase(caseDirectoryPath, metaData.getCaseDatabaseName());
//
//                /*
//                 * Finish by deleting the jobs for this case from the job lists
//                 * and deleting the directories associated with the jobs.
//                 */
//                // RJCTODO: Do we need to check for stray manifests for other cases?
//                // Perhaps a loop through the directory to make sure there are no additional manifests?
//                // Do we need special code to delete the data source files?
//                CaseDeletionResult.Status pendingJobsResult = deleteJobsForCase(caseName, pendingJobs);
//                CaseDeletionResult.Status completedJobsResult = deleteJobsForCase(caseName, completedJobs);
//
//                if (CaseDeletionResult.Status.COMPLETED == pendingJobsResult
//                        && CaseDeletionResult.Status.COMPLETED == completedJobsResult) {
//                    if (FileUtil.deleteDir(caseDirectoryPath.toFile())) {
//                        result = new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.COMPLETED);
//                    } else {
//                        result = new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.PARTIALLY_COMPLETED);
//                    }
//                } else {
//                    result = new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.PARTIALLY_COMPLETED);
//                }
//            }
//            /*
//             * Unlock the job lists monitor and then publish a case deleted
//             * event. RJCTODO: Do nodes need to be updated? Removed?
//             */
//            eventPublisher.publishRemotely(new AutoIngestCaseDeletedEvent(result, LOCAL_HOST_NAME));
//            setChanged();
//            notifyObservers(Event.CASE_DELETED);
//            return result;
//
//        } catch (CoordinationServiceException ex) {
//            LOGGER.log(Level.SEVERE, "Unable to get a lock on the case. Unable to delete.", ex);
//            return new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.FAILED);
//
//        } catch (CaseMetadata.CaseMetadataException ex) {
//            LOGGER.log(Level.SEVERE, String.format("Error accessing case metadata for %s", caseDirectoryPath), ex);
//            return new CaseDeletionResult(caseName, caseDirectoryPath.toString(), caseDirectoryPath.toString(), CaseDeletionResult.Status.FAILED);
//
//        } finally {
//            for (Lock lock : manifestFileLocks) {
//                try {
//                    lock.release();
//                } catch (CoordinationServiceException ex) {
//                    // RJCTODO:
//                    // Also, need to make a pause...
//                }
//            }
//            try {
//                if (null != caseDirLock) {
//                    caseDirLock.release();
//                }
//            } catch (CoordinationServiceException ex) {
//                // RJCTODO:
//                // Also, need to make a pause...
//            }
//        }
        return null;
    }

    /**
     * Tries to acquire an exclusive lock on every image folder for a list of
     * jobs for a case.
     *
     * @param caseName The name of the case.
     * @param jobs     The jobs list.
     * @param locks    A collecction to which the locks are to be added.
     *
     * @return True if all of the dsired locks are acquired, false otherwise.
     */
    // RJCTODO: Re-implement
    // RJCTODO: This is pretty fragile, what about skipped folders?
    boolean acquireAllExclusiveManifestFileLocks(String caseName, List<AutoIngestJob> jobs, List<Lock> locks) {
//        for (AutoIngestJob job : jobs) {
//            if (job.getManifest().getCaseName().equals(caseName)) {
//                try {
//                    Lock lock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, job.getManifest().getFilePath().toString());
//                    if (null != lock) {
//                        locks.add(lock);
//                    } else {
//                        return false;
//                    }
//                } catch (CoordinationServiceException ex) {
//                    LOGGER.log(Level.SEVERE, String.format("Coordination service error while trying to acquire exclusive lock on %s", job.getManifest().getFilePath()), ex);
//                    return false;
//                }
//            }
//        }
        return true;
    }

    /**
     * Tries to unload the Solr core for a case. If the core is not unloaded,
     * physical deletion of the case folder will fail.
     *
     * @param caseFolderPath The path to the case folder.
     * @param coreName       The name of the core to unload.
     */
    private void unloadSolrCore(Path caseFolderPath, String coreName) {
        /*
         * Try to unload the Solr core.
         */
        try {
            /*
             * Send a core unload request to the Solr server, with the
             * parameters that request deleting the index and the instance
             * directory (deleteInstanceDir removes everything related to the
             * core, the index directory, the configuration files, etc.) set to
             * true.
             */
            String url = "http://" + UserPreferences.getIndexingServerHost() + ":" + UserPreferences.getIndexingServerPort() + "/solr";
            HttpSolrServer solrServer = new HttpSolrServer(url);
            org.apache.solr.client.solrj.request.CoreAdminRequest.unloadCore(coreName, true, true, solrServer);
        } catch (Exception ex) {
            /*
             * A problem, or the core was already unloaded (e.g., by the server
             * due to resource constraints). If the latter is true, then the
             * index, etc. have not been deleted.
             */
            LOGGER.log(Level.WARNING, String.format("Error unloading/deleting Solr core for %s: %s", caseFolderPath, ex.getMessage())); //NON-NLS
        }
    }

    /**
     * Tries to delete the case database for a case.
     *
     * @param caseFolderPath  The path of the case folder.
     * @param caseDatbaseName The name of the case database.
     */
    private void deleteCaseDatabase(Path caseFolderPath, String caseDatbaseName) {
        try {
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
            Class.forName("org.postgresql.Driver"); //NON-NLS
            try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                    Statement statement = connection.createStatement();) {
                String deleteCommand = "DROP DATABASE \"" + caseDatbaseName + "\""; //NON-NLS
                statement.execute(deleteCommand);

            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, String.format("Unable to delete case database for %s : %s", caseFolderPath, ex.getMessage())); //NON-NLS
            }
        } catch (UserPreferencesException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error accessing case database connection info, unable to delete case database for %s", caseFolderPath), ex); //NON-NLS
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, String.format("Cannot load database driver, unable to delete case database for %s", caseFolderPath), ex); //NON-NLS
        }

    }

    /**
     * Removes all of the auto ingest jobs for a case from a list and attempts
     * to physically or logically delete the image folders associated with the
     * jobs.
     *
     * @param caseName                The name of the case.
     * @param jobs                    The list of jobs.
     * @param physicallyDeleteFolders Whether or not to do physical deletion.
     *
     * @return CaseDeletionResult.Status.COMPLETED if the desired type of
     *         deletion was accomplished for all image folders,
     *         CaseDeletionResult.Status.PARTIALLY_COMPLETED if seom image
     *         folders wwere not deleted.
     */
    // RJCTODO: Reimplement this
    private CaseDeletionResult.Status deleteJobsForCase(String caseName, List<AutoIngestJob> jobs) {
        CaseDeletionResult.Status result = CaseDeletionResult.Status.COMPLETED;
//        for (Iterator<AutoIngestJob> iterator = jobs.iterator(); iterator.hasNext();) {
//            AutoIngestJob job = iterator.next();
//            if (job.getCaseName().equals(caseName)) {
//                Path manifestFilePath = job.getManifest().getFilePath();
//                try {
//                    if (physicallyDeleteFolders) {
//                        if (!FileUtil.deleteDir(manifestFilePath.toFile()) && manifestFilePath.toFile().exists()) {
//                            /*
//                             * Fall back to logical deletion.
//                             */
//                            StateFile.create(manifestFilePath, DELETED);
//                            result = CaseDeletionResult.Status.PARTIALLY_COMPLETED;
//                        }
//                    } else {
//                        /*
//                         * Do logical deletion, as requested.
//                         */
//                        StateFile.create(manifestFilePath, DELETED);
//                    }
//                    iterator.remove();
//                } catch (IOException | SecurityException ex) {
//                    logger.log(Level.SEVERE, String.format("Failed to write DELETED state file to %s", manifestFilePath), ex);
//                    result = CaseDeletionResult.Status.PARTIALLY_COMPLETED;
//                }
//            }
//        }
        return result;
    }

    /**
     * Starts the process of cancelling the current job.
     *
     * @return The cancelled job plus any jobs running on other nodes. The
     *         current job is included in the list because it can take some time
     *         for the automated ingest process for the job to be shut down in
     *         an orderly fashion.
     */
    List<AutoIngestJob> cancelCurrentJob() {
        if (State.RUNNING != state && State.SHUTTING_DOWN != state) {
            return Collections.emptyList();
        }
        synchronized (jobsLock) {
            if (null != currentJob) {
                currentJob.setStage(AutoIngestJob.Stage.CANCELLED);
                LOGGER.log(Level.INFO, "Cancelling automated ingest for manifest {0}", currentJob.getManifest().getFilePath());
                IngestJob ingestJob = currentJob.getIngestJob();
                if (null != ingestJob) {
                    ingestJob.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                }
            }
            List<AutoIngestJob> runningJobs = new ArrayList<>();
            getJobs(null, runningJobs, null);
            return runningJobs;
        }
    }

    /**
     * Cancels the currently running data-source-level ingest module for the
     * current job.
     *
     * @return The current job plus any jobs running on other nodes.
     */
    List<AutoIngestJob> cancelCurrentDataSourceLevelIngestModule() {
        if (State.RUNNING != state) {
            return Collections.emptyList();
        }
        synchronized (jobsLock) {
            if (null != currentJob) {
                IngestJob ingestJob = currentJob.getIngestJob();
                if (null != ingestJob) {
                    IngestJob.DataSourceIngestModuleHandle moduleHandle = ingestJob.getSnapshot().runningDataSourceIngestModule();
                    if (null != moduleHandle) {
                        currentJob.setStage(AutoIngestJob.Stage.CANCELLING_MODULE);
                        moduleHandle.cancel();
                        LOGGER.log(Level.INFO, "Cancelling {0} module for manifest {1}", new Object[]{moduleHandle.displayName(), currentJob.getManifest().getFilePath()});
                    }
                }
            }
            List<AutoIngestJob> runningJobs = new ArrayList<>();
            getJobs(null, runningJobs, null);
            return runningJobs;
        }
    }

    /**
     * Submits an input directory scan task to the input directory scan task
     * completion service.
     */
    private final class InputDirScanSchedulingTask implements Runnable {

        private InputDirScanSchedulingTask() {
            LOGGER.log(Level.INFO, "Periodic input scan scheduling task started");
        }

        @Override
        public void run() {
            scanInputDirsNow();
        }
    }

    /**
     * Scans the input directory tree and refreshes the pending jobs queue and
     * the completed jobs list.
     */
    private final class InputDirScanTask implements Callable<Void> {

        /**
         * @inheritDoc
         */
        @Override
        public Void call() throws Exception {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            synchronized (jobsLock) {
                LOGGER.log(Level.INFO, "Starting input scan of {0}", rootInputDirectory);
                InputDirScanner scanner = new InputDirScanner();
                scanner.scan();
                LOGGER.log(Level.INFO, "Completed input scan of {0}", rootInputDirectory);
                setChanged();
                notifyObservers(Event.INPUT_SCAN_COMPLETED);
            }
            return null;
        }

    }

    /**
     * A FileVisitor that searches the input directories for manifest files. The
     * search results are used to refresh the pending jobs queue and the
     * completed jobs list.
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
                    LOGGER.log(Level.SEVERE, String.format("Error scanning the input directory %s", rootInputDirectory), ex);
                }
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
                        LOGGER.log(Level.SEVERE, String.format("Error attempting to parse %s with parser %s", filePath, parser.getClass().getCanonicalName()), ex);
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
                try {
                    byte[] rawData = coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, filePath.toString());
                    if (null != rawData) {
                        ManifestNodeData nodeData = new ManifestNodeData(rawData);
                        if (nodeData.isSet()) {
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
                                default:
                                    LOGGER.log(Level.SEVERE, "Uknown ManifestNodeData.ProcessingStatus");
                                    break;
                            }
                        } else {
                            addNewPendingJob(manifest);
                        }
                    } else {
                        addNewPendingJob(manifest);
                    }
                } catch (CoordinationServiceException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error getting node data for %s", filePath), ex);
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
            newPendingJobsList.add(new AutoIngestJob(manifest, caseDirectory, nodeData.getPriority(), LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING));
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
            try (Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString())) {
                if (null != manifestLock) {
                    ManifestNodeData newNodeData = new ManifestNodeData(PENDING, DEFAULT_JOB_PRIORITY, 0);
                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifest.getFilePath().toString(), newNodeData.toArray());
                    // RJCTODO: The host name stuff is confusing...
                    newPendingJobsList.add(new AutoIngestJob(manifest, null, DEFAULT_JOB_PRIORITY, LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING)); // RJCTODO: Make sure STARTING is used
                }
            } catch (CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error attempting to set node data for %s", manifest.getFilePath()), ex);
            } // RJCTODO: Can we do curator.create().forPath() or would that be a bad idea if it did work?
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
            String manifestLockPath = manifest.getFilePath().toString();
            try {
                Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestLockPath);
                if (null != manifestLock) {
                    try {
                        ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestLockPath));
                        if (nodeData.isSet() && ProcessingStatus.PROCESSING == nodeData.getStatus()) {
                            LOGGER.log(Level.SEVERE, "Attempting crash recovery for {0}", manifestLockPath);
                            int numberOfCrashes = nodeData.getNumberOfCrashes();
                            ++numberOfCrashes;
                            nodeData.setNumberOfCrashes(numberOfCrashes);
                            if (numberOfCrashes <= AutoIngestUserPreferences.getMaxNumTimesToProcessImage()) {
                                nodeData.setStatus(PENDING);
                                Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
                                newPendingJobsList.add(new AutoIngestJob(manifest, caseDirectoryPath, nodeData.getPriority(), LOCAL_HOST_NAME, AutoIngestJob.Stage.PENDING));
                                if (null != caseDirectoryPath) {
                                    new AutoIngestJobLogger(manifest.getFilePath(), manifest.getDataSourceFileName(), caseDirectoryPath).logCrashRecoveryWithRetry();
                                }
                            } else {
                                nodeData.setStatus(COMPLETED);
                                Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
                                newCompletedJobsList.add(new AutoIngestJob(manifest, caseDirectoryPath, nodeData.getPriority(), LOCAL_HOST_NAME, AutoIngestJob.Stage.COMPLETED));
                                if (null != caseDirectoryPath) {
                                    new AutoIngestJobLogger(manifest.getFilePath(), manifest.getDataSourceFileName(), caseDirectoryPath).logCrashRecoveryNoRetry();
                                }
                            }
                            try {
                                coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestLockPath, nodeData.toArray());
                            } catch (CoordinationServiceException ex) {
                                LOGGER.log(Level.SEVERE, String.format("Error attempting to set node data for %s", manifestLockPath), ex);
                            }
                        }
                    } catch (CoordinationServiceException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Error attempting to get node data for %s", manifestLockPath), ex);
                    } finally {
                        try {
                            manifestLock.release();
                        } catch (CoordinationServiceException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error attempting to release exclusive lock for %s", manifestLockPath), ex);
                        }
                    }
                }
            } catch (CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error attempting to get exclusive lock for %s", manifestLockPath), ex);
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
            newCompletedJobsList.add(new AutoIngestJob(manifest, caseDirectoryPath, nodeData.getPriority(), LOCAL_HOST_NAME, AutoIngestJob.Stage.COMPLETED));
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
            LOGGER.log(Level.SEVERE, String.format("Error while visiting %s during input directories scan", file.toString()), ex);
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
     * of its pause lock object). This enables both the orderly shutdown of auto
     * ingest betwewen jobs and changes to the ingest configuration (settings)
     * between jobs. The ingest configuration may be specific to the host
     * machine or shared, in which case it is downloaded from a specified
     * location before each job.
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
            LOGGER.log(Level.INFO, "Job processing task started");
            while (true) {
                try {
                    waitForInputDirScan();
                    if (jobProcessingTaskFuture.isCancelled()) {
                        break;
                    }
                    if (!processJobs()) {
                        if (jobProcessingTaskFuture.isCancelled()) {
                            break;
                        }
                        pauseForSystemError();
                    }
                    if (jobProcessingTaskFuture.isCancelled()) {
                        break;
                    }
                } catch (InterruptedException ex) {
                    break;
                }
            }
            LOGGER.log(Level.INFO, "Job processing task stopped");
        }

        /**
         * Makes a request to suspend job processing. The request will not be
         * serviced immediately if the task is doing a job.
         */
        private void requestPause() {
            synchronized (pauseLock) {
                LOGGER.log(Level.INFO, "Job processing pause requested");
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
                LOGGER.log(Level.INFO, "Job processing resume requested");
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
                    LOGGER.log(Level.INFO, "Job processing paused by request");
                    pauseRequested = false;
                    setChanged();
                    notifyObservers(Event.PAUSED_BY_REQUEST);
                    pauseLock.wait();
                    LOGGER.log(Level.INFO, "Job processing resumed");
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
                LOGGER.log(Level.SEVERE, "Job processing paused for system error");
                setChanged();
                notifyObservers(Event.PAUSED_FOR_SYSTEM_ERROR);
                pauseLock.wait();
                LOGGER.log(Level.INFO, "Job processing resumed after system error");
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
            LOGGER.log(Level.INFO, "Starting wait for input scan completion");
            inputScanCompletionService.take();
            while (null != inputScanCompletionService.poll()) {
            }
            LOGGER.log(Level.INFO, "Finished wait for input scan completion");
            synchronized (pauseLock) {
                waitingForInputScan = false;
                pauseIfRequested();
            }
        }

        /**
         * Greedily processes jobs from the pending jobs queue, with a client
         * pause request after each job.
         *
         * @return True if job processing runs normally, false if there is an
         *         auto ingest system level error, e.g. problems with the
         *         coordination service, shared configuration, communicating
         *         with the services monitor, database server, Solr server, etc.
         *
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private boolean processJobs() throws InterruptedException {
            Lock manifestLock;
            try {
                manifestLock = getNextJob();
            } catch (CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, "Error acquiring manifest file lock while dequeuing next job", ex);
                return false;
            }
            while (null != manifestLock) {
                Manifest manifest = currentJob.getManifest();
                String manifestPath = manifest.getFilePath().toString();
                LOGGER.log(Level.INFO, "Started processing of {0}", manifestPath);
                currentJob.setStage(AutoIngestJob.Stage.STARTING);
                setChanged();
                notifyObservers(Event.JOB_STARTED);
                eventPublisher.publishRemotely(new AutoIngestJobStartedEvent(currentJob));
                try {
                    try {
                        updateConfiguration();
                    } catch (SharedConfigurationException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Error updating shared configuration for %s", manifestPath), ex);
                        return false;
                    }
                    if (jobProcessingTaskFuture.isCancelled()) {
                        return true;
                    }

                    try {
                        verifyRequiredSevicesAreRunning();
                    } catch (ServicesMonitorException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Error communicating with services monitor for %s", manifestPath), ex);
                        return false;
                    } catch (DatabaseServerDownException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Database server down for %s", manifestPath), ex);
                        return false;
                    } catch (KeywordSearchServerDownException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Keyword search server down for %s", manifestPath), ex);
                        return false;
                    }
                    if (jobProcessingTaskFuture.isCancelled()) {
                        return true;
                    }

                    Case caseForJob = null;
                    try {
                        try {
                            caseForJob = openCase();
                        } catch (CaseOpeningException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error opening case %s for %s", manifest.getCaseName(), manifestPath), ex);
                            return false;
                        }
                        if (jobProcessingTaskFuture.isCancelled()) {
                            return true;
                        }

                        try {
                            ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath));
                            nodeData.setStatus(PROCESSING);
                            coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath, nodeData.toArray());

                            try {
                                ingestDataSource(caseForJob);
                                currentJob.setStage(AutoIngestJob.Stage.COMPLETED);
                            } catch (InterruptedException ex) {
                                LOGGER.log(Level.WARNING, String.format("Job processing interrupted for %s", manifestPath), ex);
                                currentJob.setStage(AutoIngestJob.Stage.CANCELLED);
                                throw ex;

                            } finally {
                                try {
                                    nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath));
                                    if (AutoIngestJob.Stage.COMPLETED == currentJob.getStage() || AutoIngestJob.Stage.CANCELLED == currentJob.getStage()) {
                                        nodeData.setStatus(COMPLETED);
                                    } else {
                                        nodeData.setStatus(PENDING);
                                    }
                                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath, nodeData.toArray());
                                } catch (CoordinationServiceException ex) {
                                    LOGGER.log(Level.SEVERE, String.format("Coordination service error processing %s", manifestPath), ex);
                                    return false;
                                }
                            }

                        } catch (CoordinationServiceException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error updating node data to PROCESSING for %s", manifestPath), ex);
                            return false;
                        }

                    } finally {
                        if (null != caseForJob) {
                            try {
                                caseForJob.closeCase();
                            } catch (CaseActionException ex) {
                                LOGGER.log(Level.SEVERE, String.format("Error closing case for %s", manifestPath), ex);
                                return false;
                            }
                        }
                    }

                } finally {
                    boolean retry = (AutoIngestJob.Stage.COMPLETED != currentJob.getStage() && AutoIngestJob.Stage.CANCELLED != currentJob.getStage());
                    LOGGER.log(Level.INFO, "Completed processing of {0}, retry = {1}", new Object[]{manifestPath, retry});
                    // RJCTODO: Log completion/cancellation here? Otherwise, log something when InterruptedException is caught.
                    eventPublisher.publishRemotely(new AutoIngestJobCompletedEvent(currentJob, retry));
                    synchronized (jobsLock) {
                        if (!retry) {
                            completedJobs.add(currentJob);
                        }
                        currentJob = null;
                        setChanged();
                        notifyObservers(Event.JOB_COMPLETED);  // RJCTODO: Make sure that getJobs always works, even when shutting down/interrupted
                    }
                    try {
                        manifestLock.release();
                    } catch (CoordinationServiceException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Error releasing exclusive manifest file lock for %s", currentJob.getManifest().getFilePath()), ex);
                        return false;
                    }
                }
                if (jobProcessingTaskFuture.isCancelled()) {
                    return true;
                }
                pauseIfRequested();
                if (jobProcessingTaskFuture.isCancelled()) {
                    return true;
                }
                try {
                    manifestLock = getNextJob();
                } catch (CoordinationServiceException ex) {
                    LOGGER.log(Level.SEVERE, "Error acquiring manifest file lock while dequeuing next job", ex);
                    return false;
                }
            }

            return true;
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
         */
        private Lock getNextJob() throws CoordinationServiceException {
            LOGGER.log(Level.INFO, "Checking pending jobs queue for ready job, enforcing max jobs per case");
            Lock manifestLock;
            synchronized (jobsLock) {
                manifestLock = selectNextJob(true);
                if (null != manifestLock) {
                    LOGGER.log(Level.INFO, "Dequeued job for {0}", currentJob.getManifest().getFilePath());
                } else {
                    LOGGER.log(Level.INFO, "No ready job");
                    LOGGER.log(Level.INFO, "Checking pending jobs queue for ready job, not enforcing max jobs per case");
                    manifestLock = selectNextJob(false);
                    if (null != manifestLock) {
                        LOGGER.log(Level.INFO, "Dequeued job for {0}", currentJob.getManifest().getFilePath());
                    } else {
                        LOGGER.log(Level.INFO, "No ready job");
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
         */
        Lock selectNextJob(boolean enforceMaxJobsPerCase) throws CoordinationServiceException {
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
                LOGGER.log(Level.INFO, "Downloading shared configuration for {0}", manifestPath);
                currentJob.setStage(AutoIngestJob.Stage.UPDATING_SHARED_CONFIG);
                new SharedConfiguration().downloadConfiguration();
                LOGGER.log(Level.INFO, "Finished downloading shared configuration for {0}", manifestPath);
            }
        }

        /**
         * Checks the availability of the services required to process an
         * automated ingest job.
         *
         * @throws ServicesMonitorException    if there is an error querying the
         *                                     services monitor.
         * @throws DatabaseServerDownException if the database server is down.
         * @throws SolrServerDownException     if the Solr server is down.
         */
        private void verifyRequiredSevicesAreRunning() throws ServicesMonitorException, DatabaseServerDownException, KeywordSearchServerDownException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            LOGGER.log(Level.INFO, "Checking services for {0}", manifestPath);
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
         * Creates or opens the case for the current auto ingest job. Acquires
         * an exclusive lock on the case name during the operation.
         * <p>
         * IMPORTANT: The case name lock is used to ensure that only one auto
         * ingest node at a time can attempt to create/open/delete a given case.
         * The case name lock must be acquired both here and during case
         * deletion.
         *
         * @return The case on success, null otherwise.
         *
         * @throws CaseOpeningException if there is an error opening the case.
         * @throws InterruptedException if the thread running the auto ingest
         *                              job processing task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private Case openCase() throws CaseOpeningException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            String caseName = manifest.getCaseName();
            LOGGER.log(Level.INFO, "Opening case {0} for {1}", new Object[]{caseName, manifest.getFilePath()});
            currentJob.setStage(AutoIngestJob.Stage.OPENING_CASE);
            Path caseNameLockPath = rootOutputDirectory.resolve(caseName);
            try {
                Lock caseNameLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseNameLockPath.toString(), 30, TimeUnit.MINUTES);
                if (null != caseNameLock) {
                    try {
                        Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, caseName);
                        if (null != caseDirectoryPath) {
                            Path metadataFilePath = caseDirectoryPath.resolve(manifest.getCaseName() + CaseMetadata.getFileExtension());
                            Case.open(metadataFilePath.toString());
                        } else {
                            caseDirectoryPath = PathUtils.createCaseFolderPath(rootOutputDirectory, caseName);
                            Case.create(caseDirectoryPath.toString(), currentJob.getManifest().getCaseName(), "", "", CaseType.MULTI_USER_CASE);
                            /*
                             * Sleep a bit before releasing the lock to ensure
                             * that the new case folder is visible on the
                             * network.
                             */
                            Thread.sleep(AutoIngestUserPreferences.getSecondsToSleepBetweenCases() * 1000);
                        }
                        currentJob.setCaseDirectoryPath(caseDirectoryPath);
                        Case caseForJob = Case.getCurrentCase();
                        try {
                            caseNameLock.release();
                        } catch (CoordinationServiceException ex) {
                            try {
                                caseForJob.closeCase();
                            } catch (CaseActionException casex) {
                                LOGGER.log(Level.SEVERE, String.format("Error closing case %s for %s after failure to release case name lock", caseName, manifest.getFilePath()), casex);
                            }
                            throw new CaseOpeningException(String.format("Error releaseing case name lock for %s for %s", manifest.getCaseName(), manifest.getFilePath()), ex);
                        }
                        return caseForJob;

                    } catch (CaseActionException ex) {
                        throw new CaseOpeningException(String.format("Error creating or opening case %s for %s", manifest.getCaseName(), manifest.getFilePath()), ex);
                    } catch (IllegalStateException ex) {
                        /*
                         * Deal with the unfortunate fact that
                         * Case.getCurrentCase throws IllegalStateException.
                         */
                        throw new CaseOpeningException(String.format("Error getting current case %s for %s", manifest.getCaseName(), manifest.getFilePath()), ex);
                    }

                } else {
                    throw new CaseOpeningException(String.format("Timed out acquiring case name lock for %s for %s", manifest.getCaseName(), manifest.getFilePath()));
                }
            } catch (CoordinationServiceException ex) {
                throw new CaseOpeningException(String.format("Error trying to acquire a case name lock for %s for %s", manifest.getCaseName(), manifest.getFilePath()));
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
         * @throws SystemErrorException if there is an system error ingesting
         *                              the data source.
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private void ingestDataSource(Case caseForJob) throws InterruptedException {
            if (!handleCancellation()) {
                DataSource dataSource = identifyDataSource();
                if (!handleCancellation() && null != dataSource) {
                    runDataSourceProcessor(caseForJob, dataSource);
                    if (!handleCancellation() && !dataSource.getContent().isEmpty()) {
                        try {
                            analyze(dataSource);
                        } finally {
                            /*
                             * Sleep to allow ingest event subscribers to do
                             * their event handling.
                             */
                            Thread.sleep(AutoIngestUserPreferences.getSecondsToSleepBetweenCases() * 1000); // RJCTODO: Change the setting description to be more generic
                        }
                        if (!handleCancellation()) {
                            exportFiles(dataSource);
                        }
                    }
                }
            }
        }

        /**
         * Checks to see if either the current job has been cancelled or this
         * job processing task has been cancelled because auto ingest is
         * shutting down. If cancellation has occurred, makes an entry in the
         * case auto ingest log.
         *
         * @return True if cancellation has occurred, false otherwise.
         *
         * @throws SystemErrorException if there is an error writing to the case
         *                              auto ingest log.
         * @throws InterruptedException if the thread running the auto ingest
         *                              job processing task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private boolean handleCancellation() throws InterruptedException {
            if (AutoIngestJob.Stage.CANCELLED == currentJob.getStage() || jobProcessingTaskFuture.isCancelled()) {
                currentJob.setStage(AutoIngestJob.Stage.CANCELLED);
                Manifest manifest = currentJob.getManifest();
                new AutoIngestJobLogger(manifest.getFilePath(), manifest.getDataSourceFileName(), currentJob.getCaseDirectoryPath()).logJobCancelled();
                return true;
            }
            return false;
        }

        /**
         * Identifies the type of the data source specified in the manifest for
         * the current job and extracts it if required.
         *
         * @return A data source object.
         *
         * @throws IOException          if there was an error extracting or
         *                              reading the data source.
         * @throws InterruptedException if the thread running the auto ingest
         *                              job processing task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private DataSource identifyDataSource() throws InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            LOGGER.log(Level.INFO, "Starting identifying data source stage for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.IDENTIFYING_DATA_SOURCE);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {
                Path dataSourcePath = manifest.getDataSourcePath();
                File dataSource = dataSourcePath.toFile();
                if (!dataSource.exists()) {
                    LOGGER.log(Level.SEVERE, "Missing data source for {0}", manifestPath);
                    jobLogger.logMissingDataSource();
                    return null;
                }
                String deviceId = manifest.getDeviceId();
                return new DataSource(deviceId, dataSourcePath);
            } finally {
                LOGGER.log(Level.INFO, "Finished identifying data source stage for {0}", manifestPath);
            }
        }

        /**
         * Passes the data source for the current job through a data source
         * processor that adds it to the case database.
         *
         * @param dataSource The data source.
         *
         * @throws SystemErrorException if there is an error adding the data
         *                              source.
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private void runDataSourceProcessor(Case caseForJob, DataSource dataSource) throws InterruptedException {
            /**
             * This "callback" collects the results of running the data source
             * processor on the data source and unblocks the auto ingest task
             * thread when the data source processor finishes running in its own
             * thread.
             */
            class AddDataSourceCallback extends DataSourceProcessorCallback {

                private final DataSource dataSourceInfo;
                private final UUID taskId;

                AddDataSourceCallback(DataSource dataSourceInfo, UUID taskId) {
                    this.dataSourceInfo = dataSourceInfo;
                    this.taskId = taskId;
                }

                @Override
                public void done(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> dataSources) {
                    if (!dataSources.isEmpty()) {
                        caseForJob.notifyDataSourceAdded(dataSources.get(0), taskId);
                    } else {
                        caseForJob.notifyFailedAddingDataSource(taskId);
                    }
                    dataSourceInfo.setDataSourceProcessorOutput(result, errorMessages, dataSources);
                    dataSources.addAll(dataSources);
                    synchronized (ingestLock) {
                        ingestLock.notify();
                    }
                }

                @Override
                public void doneEDT(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> dataSources) {
                    done(result, errorMessages, dataSources);
                }
            }

            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            LOGGER.log(Level.INFO, "Starting adding data source stage for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.ADDING_DATA_SOURCE);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            AutomatedIngestDataSourceProcessor selectedProcessor = null;
            try {
                final DataSourceProcessorProgressMonitor progressMonitor = new DataSourceProcessorProgressMonitor() {
                    /*
                     * This data source processor progress monitor does nothing.
                     * There is no UI for showing data source processor progress
                     * during an auto ingest job.
                     */
                    @Override
                    public void setIndeterminate(final boolean indeterminate) {
                    }

                    @Override
                    public void setProgress(final int progress) {
                    }

                    @Override
                    public void setProgressText(final String text) {
                    }
                };
                DataSourceProcessorCallback callBack = new AddDataSourceCallback(dataSource, UUID.randomUUID());
                final UUID taskId = UUID.randomUUID();
                try {
                    caseForJob.notifyAddingDataSource(taskId);
                    
                    // lookup all AutomatedIngestDataSourceProcessors 
                    Collection <? extends AutomatedIngestDataSourceProcessor> processorCandidates = Lookup.getDefault().lookupAll(AutomatedIngestDataSourceProcessor.class);

                    int selectedProcessorConfidence = 0;
                    for (AutomatedIngestDataSourceProcessor processor : processorCandidates) {
                        int confidence = 0;
                        try {
                            confidence = processor.canProcess(dataSource.getPath());
                        } catch (AutomatedIngestDataSourceProcessor.AutomatedIngestDataSourceProcessorException ex) {
                            LOGGER.log(Level.SEVERE, "Exception while determining whether data source processor {0} can process {1}", new Object[]{processor.getDataSourceType(), dataSource.getPath()});
                            // ELTODO - should we auto-pause if one of DSP.canProcess() threw an exception? Probably so...
                            // On the other hand what if we simply weren't able to extract an archive or something?
                            pauseForSystemError();
                            return;
                        }
                        if (confidence > selectedProcessorConfidence)  {
                            selectedProcessor = processor;
                            selectedProcessorConfidence = confidence;
                        }
                    }
                    
                    // did we find a data source processor that can process the data source
                    if (selectedProcessor == null) {
                        jobLogger.logDataSourceTypeIdError("Unsupported data source " + dataSource.getPath() + " for " + manifestPath);
                        LOGGER.log(Level.SEVERE, "Unsupported data source {0} for {1}", new Object[]{dataSource.getPath(), manifestPath});  // NON-NLS
                        return;
                    }
                    
                    synchronized (ingestLock) {
                        LOGGER.log(Level.INFO, "Identified data source type for {0} as {1}", new Object[]{manifestPath, selectedProcessor.getDataSourceType()});
                        try {
                            selectedProcessor.process(dataSource.getDeviceId(), dataSource.getPath(), progressMonitor, callBack);
                        } catch (AutomatedIngestDataSourceProcessor.AutomatedIngestDataSourceProcessorException ex) {
                            LOGGER.log(Level.SEVERE, "Exception while processing {0} with data source processor {1}", new Object[]{dataSource.getPath(), selectedProcessor.getDataSourceType()});
                            jobLogger.logDataSourceProcessorError(selectedProcessor.getDataSourceType(), ex.getMessage());
                            pauseForSystemError();
                            return;
                        }
                        ingestLock.wait();
                    }
                } finally {
                    String imageType;
                    if (selectedProcessor != null) {
                        imageType = selectedProcessor.getDataSourceType();
                    } else {
                        imageType = dataSource.getPath().toString();
                    }
                    DataSourceProcessorResult resultCode = dataSource.getResultDataSourceProcessorResultCode();
                    if (null != resultCode) {
                        switch (resultCode) {
                            case NO_ERRORS:
                                jobLogger.logDataSourceAdded(imageType);
                                break;

                            case NONCRITICAL_ERRORS:
                                jobLogger.logDataSourceAdded(imageType);
                                for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                                    LOGGER.log(Level.WARNING, "Non-critical error running data source processor for {0}: {1}", new Object[]{manifestPath, errorMessage});
                                }
                                for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                                    jobLogger.logDataSourceProcessorWarning(imageType, errorMessage);
                                }
                                break;

                            case CRITICAL_ERRORS:
                                jobLogger.logFailedToAddDataSource(imageType);
                                for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                                    LOGGER.log(Level.SEVERE, "Critical error running data source processor for {0}: {1}", new Object[]{manifestPath, errorMessage});
                                }
                                for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                                    jobLogger.logDataSourceProcessorError(imageType, errorMessage);
                                }
                                break;
                            default:
                                LOGGER.log(Level.SEVERE, "Unrecognized result code {0} running data source processor for {1}", new Object[]{resultCode, manifestPath});
                                break;
                        }

                    } else {
                        /*
                         * TODO (JIRA-1711): Use cancellation feature of data
                         * source processors that support cancellation.
                         */
                        LOGGER.log(Level.WARNING, "Cancellation while waiting for data source processor for {0}", manifestPath);
                        jobLogger.logDataSourceProcessorCancelled(imageType);
                    }
                }

            } finally {
                LOGGER.log(Level.INFO, "Finished adding data source stage for {0}", manifestPath);
            }
        }

        /**
         * Analyzes the data source content returned by the data source
         * processor using the configured set of data source level and file
         * level analysis modules.
         *
         * @param dataSource The data source to analyze.
         *
         * @throws SystemErrorException if there is an error analyzing the data
         *                              source.
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private void analyze(DataSource dataSource) throws InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            LOGGER.log(Level.INFO, "Starting analysis stage for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.ANALYZING_DATA_SOURCE);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {
                PropertyChangeListener completionListener = (PropertyChangeEvent evt) -> {
                    /*
                     * This ingest job event listener allows the job processing
                     * task to block until the ingest job for the data sources
                     * is completed. Note that the ingest job can spawn "child"
                     * ingest jobs (e.g., if an embedded virtual machine is
                     * found), so the task must remain blocked until ingest is
                     * no longer running. Note that synchronization on the
                     * monitor of the ingestInProgressLock object is used to
                     * ensure that the wait/notify sequence on the same monitor
                     * is executed in the proper order.
                     */
                    if (AutopsyEvent.SourceType.LOCAL == ((AutopsyEvent) evt).getSourceType()) {
                        String eventType = evt.getPropertyName();
                        if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                                || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                            synchronized (ingestLock) {
                                if (!IngestManager.getInstance().isIngestRunning()) {
                                    ingestLock.notify();
                                }
                            }
                        }
                    }
                };
                IngestManager.getInstance().addIngestJobEventListener(completionListener);
                try {
                    synchronized (ingestLock) {
                        IngestJobSettings ingestJobSettings = new IngestJobSettings(AutoIngestUserPreferences.getAutoModeIngestModuleContextString());
                        List<String> settingsWarnings = ingestJobSettings.getWarnings();
                        if (settingsWarnings.isEmpty()) {
                            IngestJobStartResult ingestJobStartResult = IngestManager.getInstance().beginIngestJob(dataSource.getContent(), ingestJobSettings);
                            IngestJob ingestJob = ingestJobStartResult.getJob();
                            if (null != ingestJob) {
                                currentJob.setIngestJob(ingestJob);
                                ingestLock.wait();
                                // RJCTODO: Do we have an issue here with the logging of results for embedded data sources? Perhaps this lgging needs to occur as each event is received above?
                                IngestJob.ProgressSnapshot jobSnapshot = ingestJob.getSnapshot();
                                for (IngestJob.ProgressSnapshot.DataSourceProcessingSnapshot snapshot : jobSnapshot.getDataSourceSnapshots()) {
                                    if (!snapshot.isCancelled()) {
                                        for (String module : snapshot.getCancelledDataSourceIngestModules()) {
                                            jobLogger.logIngestModuleCancelled(module);
                                        }
                                        jobLogger.logAnalysisCompleted();
                                    } else {
                                        jobLogger.logAnalysisCancelled(snapshot.getCancellationReason().getDisplayName());
                                    }
                                }
                            } else if (!ingestJobStartResult.getModuleErrors().isEmpty()) {
                                for (IngestModuleError error : ingestJobStartResult.getModuleErrors()) {
                                    LOGGER.log(Level.SEVERE, String.format("%s analysis module startup error for %s", error.getModuleDisplayName(), manifestPath), error.getThrowable());
                                }
                                jobLogger.logIngestModuleStartupErrors(ingestJobStartResult.getModuleErrors());
                                // throw new SystemErrorException(); RJCTODO: Need new type
                            } else {
                                LOGGER.log(Level.SEVERE, String.format("Ingest startup error for %s", manifestPath), ingestJobStartResult.getStartupException());
                                jobLogger.logAnalysisStartupError(ingestJobStartResult.getStartupException());
                                // throw new SystemErrorException(); RJCTODO: Need new type, or flag in job
                            }
                        } else {
                            for (String warning : ingestJobSettings.getWarnings()) {
                                LOGGER.log(Level.SEVERE, "Analysis settings error for {0}: {1}", new Object[]{manifestPath, warning});
                            }
                            jobLogger.logIngestJobSettingsErrors(ingestJobSettings.getWarnings());
                            // throw new SystemErrorException(); RJCTODO: Need new type, or flag in job
                        }
                    }
                } finally {
                    IngestManager.getInstance().removeIngestJobEventListener(completionListener);
                }
            } finally {
                currentJob.setIngestJob(null);
                LOGGER.log(Level.INFO, "Finished analysis stage for {0} ", manifestPath);
            }
        }

        /**
         * Exports any files from the data source for the current job that
         * satisfy any user-defined file export rules.
         *
         * @param dataSource The data source.
         *
         * @throws SystemErrorException if there is an error exporting the
         *                              files.
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private void exportFiles(DataSource dataSource) throws InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            LOGGER.log(Level.INFO, "Starting file export stage for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.EXPORTING_FILES);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {
                FileExporter fileExporter = new FileExporter();
                if (fileExporter.isEnabled()) {
                    LOGGER.log(Level.INFO, "Exporting files for {0}", manifestPath);
                    fileExporter.process(manifest.getDeviceId(), dataSource.getContent());
                    jobLogger.logFileExportCompleted();
                } else {
                    LOGGER.log(Level.WARNING, "Exporting files disabled for {0}", manifestPath);
                    jobLogger.logFileExportDisabled();
                }
            } catch (FileExportException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error doing file export for %s", manifestPath), ex);
                AutoIngestAlertFile.create(caseDirectoryPath);
                jobLogger.logFileExportError(ex);
            } finally {
                LOGGER.log(Level.INFO, "Finished file export stage for {0} ", manifestPath);
            }
        }

        /**
         * Exception type thrown when the services monitor reports that the
         * database server is down.
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
         * case for an auto ingest job..
         */
        private final class CaseOpeningException extends Exception {

            private static final long serialVersionUID = 1L;

            private CaseOpeningException(String message) {
                super(message);
            }

            private CaseOpeningException(String message, Throwable cause) {
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
    private final class PeriodicJobStatusEventTask implements Runnable { // RJCTODO: Rename to StatusPublishingTask, especially when publishing to the system dashboard

        private final long MAX_SECONDS_WITHOUT_UPDATE = JOB_STATUS_EVENT_INTERVAL_SECONDS * MAX_MISSED_JOB_STATUS_UPDATES;

        private PeriodicJobStatusEventTask() {
            LOGGER.log(Level.INFO, "Periodic status publishing task started");
        }

        @Override
        public void run() {

            try {
                synchronized (jobsLock) {
                    if (currentJob == null) {
                        return;
                    }
                    // notify remote AIM nodes about status of current job
                    eventPublisher.publishRemotely(new AutoIngestJobStatusEvent(currentJob));
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
                        LOGGER.log(Level.WARNING, "Auto ingest node {0} timed out while processing folder {1}",
                                new Object[]{job.getNodeName(), job.getManifest().getFilePath().toString()});
                        hostNamesToRunningJobs.remove(job.getNodeName());
                        setChanged();
                        notifyObservers(Event.JOB_COMPLETED);
                    }
                }

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Unexpected exception in PeriodicJobStatusEventTask", ex); //NON-NLS
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
     * broadcast to other auto ingest nodes. // RJCTODO: Is this true?
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
     * The outcome of a case deletion operation.
     */
    public static final class CaseDeletionResult implements Serializable {

        private static final long serialVersionUID = 1L;

        /*
         * A case may be completely deleted, partially deleted, or not deleted
         * at all.
         */
        enum Status {

            /**
             * The case folder could not be either physically or logically
             * (DELETED state file written) deleted.
             */
            FAILED,
            /**
             * The case folder was deleted, but one or more of the image folders
             * for the case could not be either physically or logically (DELETED
             * state file written) deleted.
             */
            PARTIALLY_COMPLETED,
            /**
             * The case folder and all of its image folders were either
             * physically or logically (DELETED state file written) deleted.
             */
            COMPLETED;
        }

        private final String caseName;
        private final String caseFolderPath;
        private final String caseImageFolderPath;
        private final Status status;

        /**
         * Constructs an object that reports the outcome of a case deletion
         * operation.
         *
         * @param caseName            The name of the case.
         * @param caseFolderPath      The case folder path.
         * @param caseImageFolderPath The case image folder path.
         * @param status              An instance of the Status enum. See the
         *                            enum definition for details.
         */
        CaseDeletionResult(String caseName, String caseFolderPath, String caseImageFolderPath, Status status) {
            this.caseName = caseName;
            this.caseFolderPath = caseFolderPath;
            this.caseImageFolderPath = caseImageFolderPath;
            this.status = status;
        }

        /**
         * Gets the name of the case.
         *
         * @return The case name.
         */
        String getCaseName() {
            return caseName;
        }

        /**
         * Gets the case folder path.
         *
         * @return The case folder path.
         */
        String getCaseFolderPath() {
            return caseFolderPath;
        }

        /**
         * Gets the full path of the top level image folder for the case.
         *
         * @return The top level image folder path.
         */
        String getCaseImageFolderPath() {
            return caseImageFolderPath;
        }

        /**
         * Queries the result for its status.
         *
         * @return An instance of the Status enum. See the enum definition for
         *         details.
         */
        Status getCaseDeletionStatus() {
            return status;
        }

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
