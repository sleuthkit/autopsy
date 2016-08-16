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
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;
import java.io.File;
import java.io.FileOutputStream;
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
import org.sleuthkit.autopsy.modules.vmextractor.VirtualMachineFinder;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.swing.filechooser.FileFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FilenameUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.datamodel.Content;
import org.w3c.dom.Document;
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
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobLogger.AutoIngestJobLoggerException;
import org.sleuthkit.autopsy.experimental.autoingest.FileExporter.FileExportException;
import org.sleuthkit.autopsy.experimental.autoingest.ManifestFileParser.ManifestFileParserException;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.PENDING;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.PROCESSING;
import static org.sleuthkit.autopsy.experimental.autoingest.ManifestNodeData.ProcessingStatus.COMPLETED;
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
    private static final String TSK_IS_DRIVE_IMAGE_TOOL_DIR = "tsk_isImageTool";
    private static final String TSK_IS_DRIVE_IMAGE_TOOL_EXE = "tsk_isImageTool.exe";
    private static final int PRIORITIZATION_LOCK_TIME_OUT = 10;
    private static final TimeUnit PRIORITIZATION_LOCK_TIME_OUT_UNIT = TimeUnit.SECONDS;
    private static final java.util.logging.Logger LOGGER = AutoIngestSystemLogger.getLogger();
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
    // refernce should be used for everything else.
    @GuardedBy("jobsLock")
    private volatile AutoIngestJob currentJob;
    @GuardedBy("jobsLock")
    private List<AutoIngestJob> completedJobs;
    private CoordinationService coordinationService;
    private JobProcessingTask jobProcessingTask;
    private Future<?> jobProcessingTaskFuture;
    private Path tskIsImageToolExePath;
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
        LOGGER.log(Level.INFO, "Auto ingest initialized");
    }

    /**
     * Starts up auto ingest.
     *
     * @throws AutoIngestManagerStartupException if there is a problem starting
     *                                           auto ingest.
     */
    void startUp() throws AutoIngestManagerStartupException {
        try {
            coordinationService = CoordinationService.getInstance(CoordinationServiceNamespace.getRoot());
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestManagerStartupException("Failed to get coordination service", ex);
        }
        rootInputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeImageFolder());
        rootOutputDirectory = Paths.get(AutoIngestUserPreferences.getAutoModeResultsFolder());
        tskIsImageToolExePath = locateTskIsImageToolExecutable();
        inputScanSchedulingExecutor.scheduleAtFixedRate(new InputDirScanSchedulingTask(), 0, INPUT_SCAN_INTERVAL, TimeUnit.MILLISECONDS);
        jobProcessingTask = new JobProcessingTask();
        jobProcessingTaskFuture = jobProcessingExecutor.submit(jobProcessingTask);
        jobStatusPublishingExecutor.scheduleAtFixedRate(new PeriodicJobStatusEventTask(), JOB_STATUS_EVENT_INTERVAL_SECONDS, JOB_STATUS_EVENT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        eventPublisher.addSubscriber(EVENT_LIST, instance);
        RuntimeProperties.setCoreComponentsActive(false);
        state = State.RUNNING;
        LOGGER.log(Level.INFO, "Auto ingest started");
    }

    /**
     * Gets the path to the copy of the SleuthKit executable that is used to
     * determine whether or not a drive image has a file system. The tool is
     * installed during Viking installation, so it is assumed that it only needs
     * to be found on start up.
     *
     * @return The path to the executable.
     *
     * @throws AutoIngestManagerStartupException if the executable cannot be
     *                                           found or cannot be executed.
     */
    private static Path locateTskIsImageToolExecutable() throws AutoIngestManagerStartupException {
        if (!PlatformUtil.isWindowsOS()) {
            throw new AutoIngestManagerStartupException("Auto ingest requires a Windows operating system to run");
        }

        final File folder = InstalledFileLocator.getDefault().locate(TSK_IS_DRIVE_IMAGE_TOOL_DIR, AutoIngestManager.class.getPackage().getName(), false);
        if (null == folder) {
            throw new AutoIngestManagerStartupException("Unable to locate SleuthKit image tool installation folder");
        }

        Path executablePath = Paths.get(folder.getAbsolutePath(), TSK_IS_DRIVE_IMAGE_TOOL_EXE);
        File executable = executablePath.toFile();
        if (!executable.exists()) {
            throw new AutoIngestManagerStartupException("Unable to locate SleuthKit image tool");
        }

        if (!executable.canExecute()) {
            throw new AutoIngestManagerStartupException("Unable to run SleuthKit image tool");
        }

        return executablePath;
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
                InputDirScanner scanner = new InputDirScanner();
                scanner.scan();
                setChanged();
                notifyObservers(Event.INPUT_SCAN_COMPLETED);
            }
            return null;
        }

    }

    /**
     * An implementation of java.nio.file.FileVisitor that searches the input
     * directories for manifest files and refreshes the pending jobs queue and
     * the completed jobs list using the search results.
     */
    private final class InputDirScanner implements FileVisitor<Path> {

        private final List<AutoIngestJob> newPendingJobsList = new ArrayList<>();
        private final List<AutoIngestJob> newCompletedJobsList = new ArrayList<>();

        /**
         * Searches the input directories for manifest files and refreshes the
         * pending jobs queue and the completed jobs list using the search
         * results.
         */
        private void scan() {
            synchronized (jobsLock) {
                /*
                 * Check for interruption of the thread running this scan. Scan
                 * task threads are interrupted when auto ingest is shutting
                 * down and scanning should stop.
                 */
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
         * Invoked for a directory before entries in the directory are visited.
         * Overriden to check if the task thread has been interrupted because
         * auto ingest is shutting down.
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
         * Invoked for a file in a directory. If it is a manifest file for a
         * pending or completed auto ingest job,
         *
         * @param filePath The path of the file.
         * @param attrs    The file system attributes of the file.
         *
         * @return TERMINATE if auto ingest is shutting down, CONTINUE if it has
         *         not.
         *
         * @throws IOException if an I/O error occurs.
         */
        @Override
        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
            /*
             * Check for interruption of the thread running this scan. Scan task
             * threads are interrupted when auto ingest is shutting down and
             * scanning should stop.
             */
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }

            /*
             * Can the file be parsed as a manifest file? If not, ignore it.
             */
            ManifestFileParser manifestParser = null;
            for (ManifestFileParser parser : Lookup.getDefault().lookupAll(ManifestFileParser.class)) {
                if (parser.fileIsManifest(filePath)) {
                    manifestParser = parser;
                }
            }
            if (null == manifestParser) {
                return CONTINUE;
            }

            /*
             * Try to acquire a shared lock on the coordination service node for
             * the manifest file and check processing status. If the lock cannot
             * be acquired, it is because the manifest is exclusively locked for
             * a) writing initial state to the coordinaton service node because
             * the manifest is new or b) for processing the data source
             * described by the manifest or c) for deleting the manifest and its
             * corresponding data source. In all of these cases, the manifest
             * can be skipped and considered again in the next scan, assuming it
             * still exists.
             */
            Lock fileLock = null;
            try {
                fileLock = coordinationService.tryGetSharedLock(CoordinationService.CategoryNode.MANIFESTS, filePath.toString());
                if (null != fileLock) {
                    Manifest manifest = manifestParser.parse(filePath);
                    ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, filePath.toString()));
                    if (nodeData.isSet()) {
                        Path caseDirectory = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
                        ManifestNodeData.ProcessingStatus processingStatus = nodeData.getStatus();
                        switch (processingStatus) {
                            case PENDING:
                                newPendingJobsList.add(new AutoIngestJob(manifest, caseDirectory, nodeData.getPriority(), LOCAL_HOST_NAME));
                                break;
                            case PROCESSING:
                                /*
                                 * If the processing status is PROCESSING and it
                                 * was possible to acquire a shared lock on the
                                 * manifest file, the auto ingest node that was
                                 * doing processing crashed. Try upgrading the
                                 * lock to an exclusive lock and do crash
                                 * recovery. Failure to get the exclusive lock
                                 * indicates another node has already started
                                 * the crash recovery in the gap between
                                 * releasing the shared lock and attempting to
                                 * acquire the exclusive lock, in which case the
                                 * manifest file can be skipped for this scan.
                                 */
                                fileLock.release();
                                fileLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, filePath.toString());
                                if (null != fileLock) {
                                    nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, filePath.toString()));
                                    int numberOfCrashes = nodeData.getNumberOfCrashes();
                                    LOGGER.log(Level.SEVERE, "Found crashed auto ingest job for {0}, processing attempts = {1}", new Object[]{filePath, numberOfCrashes});
                                    // RJCTODO: Need lock on case directory?
                                    if (null != caseDirectory) {
                                        // RJCTODO: Write alert file
                                    }
                                    ++numberOfCrashes;
                                    nodeData.setNumberOfCrashes(numberOfCrashes);
                                    if (numberOfCrashes <= AutoIngestUserPreferences.getMaxNumTimesToProcessImage()) {
                                        nodeData.setStatus(PENDING);
                                        newPendingJobsList.add(new AutoIngestJob(manifest, caseDirectory, nodeData.getPriority(), LOCAL_HOST_NAME));
                                    } else {
                                        nodeData.setStatus(COMPLETED);
                                    }
                                    coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, filePath.toString(), nodeData.toArray());
                                    AutoIngestJob newJob = new AutoIngestJob(manifest, caseDirectory, nodeData.getPriority(), LOCAL_HOST_NAME);
                                    if (PENDING == nodeData.getStatus()) {
                                        newCompletedJobsList.add(newJob);
                                    } else {
                                        newCompletedJobsList.add(newJob);
                                    }
                                }
                                break;
                            case COMPLETED:
                                // RJCTODO: PathUtils.findCaseFolder is broken. 
                                // Can the job take responsibility for finding the case directory?
                                Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, manifest.getCaseName());
                                newCompletedJobsList.add(new AutoIngestJob(manifest, caseDirectoryPath, nodeData.getPriority(), LOCAL_HOST_NAME));
                                break;
                            default:
                                // RJCTODO: Report some error
                                break;
                        }
                    } else {
                        /*
                         * The manifest file has no processing status yet, so it
                         * is brand new. Try to upgrade to an exclusive lock to
                         * write the initial status, and then add an auto ingest
                         * job for it to the pending jobs queue. If the lock
                         * cannot be acquired, another auto ingest node got to
                         * it first, so skip it on this scan.
                         */
                        fileLock.release();
                        fileLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, filePath.toString());
                        if (null != fileLock) {
                            ManifestNodeData newNodeData = new ManifestNodeData(PENDING, DEFAULT_JOB_PRIORITY, 0);
                            coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, filePath.toString(), newNodeData.toArray());
                            // RJCTODO: The host name stuff is confusing...
                            // Also, see note about case directory path
                            newPendingJobsList.add(new AutoIngestJob(manifest, null, DEFAULT_JOB_PRIORITY, LOCAL_HOST_NAME));
                        }
                    }
                }
            } catch (InterruptedException ex) {
                /*
                 * Can't propagate this exception, but auto ingest is shutting
                 * down, so restore the interrupted status and terminate the
                 * scan.
                 */
                Thread.currentThread().interrupt();
                return TERMINATE;

            } catch (CoordinationService.CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error attempting to get or release coordination service lock on image folder %s", filePath), ex);
            } catch (ManifestFileParserException ex) {
                //RJCTODO
            } finally {
                if (null != fileLock) {
                    try {
                        fileLock.release();
                    } catch (CoordinationServiceException ex) {
                        // RJCTODO
                    }
                }
            }
            return CONTINUE;
        }

        /**
         * Invoked for a file that could not be visited. This method is invoked
         * if the file's attributes could not be read, the file is a directory
         * that could not be opened, and other reasons.
         */
        /**
         *
         * @param file
         * @param ex
         *
         * @return TERMINATE if auto ingest is shutting down, CONTINUE if it has
         *         not.
         *
         * @throws IOException
         */
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException ex) throws IOException {
            // RJCTODO: Consider additional notification
            LOGGER.log(Level.SEVERE, String.format("Error while visiting %s during input directories scan", file.toString()), ex);

            /*
             * Check for cancellation of the thread running the scan. The scan
             * task threads are cancelled when auto ingest is shutting down.
             */
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }
            return CONTINUE;
        }

        /**
         * Invoked for a directory before entries in the directory are visited.
         * Overriden to check if the task thread has been interrupted because
         * auto ingest is shutting down.
         *
         * @param dirPath The directory that was visited.
         * @param unused  An IOException thrown while visiting the directory.
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
     * pending jobs queue. This means that the task makes exactly one attempt to
     * clear the queue for each completed scan task.
     * <p>
     * The job processing task can be paused between jobs (it waits on the
     * monitor of its pause lock object) and resumed (by notifying the monitor
     * of its pause lock object). This enables changes to the ingest
     * configuration (settings) between jobs. The ingest configuration may be
     * specific to the host machine or shared, in which case it is downloaded
     * from a specified location before each job.
     * <p>
     * The task pauses itself if system errors occur, e.g., problems with the
     * coordination service, database server, Solr server, logging, creation of
     * alert files, etc. The idea behind this is to avoid attempts to process
     * jobs when the auto ingest system is not in a stable state. It is up to a
     * system administrator to examine the auto ingest system logs, etc. to
     * devide a remedy for the problem and then resume the task.
     * <p>
     * Note that the task also waits on the monitor of its ingest lock object
     * when the data source processor and the analysis modules are running for a
     * given job. Notifies are done via a callback and and an event handler,
     * respectively.
     * <p>
     * RJCTODO: What happens when scan tasks back up? The current scan interval
     * is every five minutes.
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
        }

        /**
         * Processes auto ingest jobs, blocking on a completion service for
         * input directory scan tasks and waiting on a pause lock object when
         * paused by a client or because of a system error. The task is also in
         * a wait state when the data source processor and the analysis modules
         * for a job are running.
         */
        @Override
        public void run() {
            LOGGER.log(Level.INFO, "Job processing started");
            while (true) {
                try {
                    try {
                        waitForInputDirScan();
                        processJobs();
                    } catch (SystemErrorException ex) {
                        pauseForSystemError();
                    }
                } catch (InterruptedException ex) {
                    break;
                }
            }
            LOGGER.log(Level.INFO, "Job processing stopped");
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
            LOGGER.log(Level.INFO, "Finished wait for input scan completion");
            synchronized (pauseLock) {
                waitingForInputScan = false;
                pauseIfRequested();
            }
        }

        /**
         * Processes jobs until the pending jobs list is empty, with a client
         * pause request check before and after each job.
         *
         * @throws SystemErrorException if there is a system level error
         *                              processing a job (e.g. problems with the
         *                              coordination service, database server,
         *                              Solr server, logging, creation of alert
         *                              files, etc.).
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        private void processJobs() throws SystemErrorException, InterruptedException {
            Lock manifestLock = getNextJob();
            while (null != manifestLock) {
                Manifest manifest = currentJob.getManifest();
                Path manifestPath = manifest.getFilePath();
                try {
                    LOGGER.log(Level.INFO, "Started processing of {0}", manifestPath);
                    eventPublisher.publishRemotely(new AutoIngestJobStartedEvent(currentJob));
                    processCurrentJob();

                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Processing of %s interrupted" , manifestPath), ex);
                    currentJob.setStage(AutoIngestJob.Stage.CANCELLED);
                    Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
                    try {
                        new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath).logJobCancelled();
                    } catch (AutoIngestJobLoggerException logex) {
                        LOGGER.log(Level.SEVERE, String.format("Error writing to case auto ingest log for %s", manifestPath), logex);
                        throw new SystemErrorException();
                    }
                    throw ex;

                } finally {
                    LOGGER.log(Level.INFO, "Completed processing of {0}", manifestPath);
                    try {
                        try {
                            manifestLock.release();
                        } catch (CoordinationServiceException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error releasing manifest file lock for %s", manifestPath), ex);
                            throw new SystemErrorException();
                        }
                    } finally {
                        synchronized (jobsLock) {
                            boolean completed = AutoIngestJob.Stage.COMPLETED == currentJob.getStage() || AutoIngestJob.Stage.CANCELLED == currentJob.getStage();
                            if (completed) {
                                completedJobs.add(currentJob);
                            }
                            eventPublisher.publishRemotely(new AutoIngestJobCompletedEvent(currentJob, !completed));
                            currentJob = null;
                            setChanged();
                            notifyObservers(Event.JOB_COMPLETED);
                        }
                    }
                }
                pauseIfRequested();
                manifestLock = getNextJob();
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
         * @throws SystemErrorException if there is a system level error
         *                              processing a job (e.g. problems with the
         *                              coordination service, database server,
         *                              Solr server, logging, creation of alert
         *                              files, etc.).
         */
        private Lock getNextJob() throws SystemErrorException {
            LOGGER.log(Level.INFO, "Checking pending jobs queue for ready job");
            Lock manifestLock;
            synchronized (jobsLock) {
                manifestLock = selectNextJob(true);
                if (null == manifestLock) {
                    manifestLock = selectNextJob(false);
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
         * @throws SystemErrorException if there is a system level error
         *                              processing a job (e.g. problems with the
         *                              coordination service, database server,
         *                              Solr server, logging, creation of alert
         *                              files, etc.).
         */
        Lock selectNextJob(boolean enforceMaxJobsPerCase) throws SystemErrorException {
            try {
                Lock manifestLock = null;
                synchronized (jobsLock) {
                    Iterator<AutoIngestJob> iterator = pendingJobs.iterator();
                    while (iterator.hasNext()) {
                        AutoIngestJob job = iterator.next();
                        Path manifestPath = job.getManifest().getFilePath();
                        // RJCTODO: Probably should add a blocking acquire call here 
                        // to mitigate the possibility of skipping a higher priority 
                        // task because it is locked by an input scan. 
                        manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString());
                        if (null == manifestLock) {
                            /*
                             * Skip the job. If it is exclusively locked for
                             * processing or deletion by another node, the
                             * remote job event handlers or the next input scan
                             * will flush it out of the pending queue.
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
            } catch (CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, "Coordination service error during inspection of pending jobs queue", ex);
                throw new SystemErrorException();
            }
        }

        /**
         * Processes the current job. RJCTODO: Update
         *
         * @return The processing status for the job.
         *
         * @throws CoordinationServiceException if there is an error acquiring
         *                                      or releasing coordination
         *                                      service locks.
         * @throws SharedConfigurationException if there is an error downloading
         *                                      shared configuration.
         * @throws ServicesMonitorException     if there is an error querying
         *                                      the services monitor.
         * @throws DatabaseServerDownException  if the database server is down.
         * @throws SolrServerDownException      if the Solr server is down.
         * @throws CaseActionException          if there is an error creating,
         *                                      opening or closing the case for
         *                                      the job.
         * @throws FileExportException          if there is an error exporting
         *                                      the files.
         * @throws InterruptedException         if the thread running the auto
         *                                      ingest job processing task is
         *                                      interrupted while blocked, i.e.,
         *                                      if auto ingest is shutting down.
         */
        private void processCurrentJob() throws SystemErrorException, InterruptedException {
            String manifestPath = currentJob.getManifest().getFilePath().toString();
            Case caseForJob;
            if (configurationUpToDate()) {
                verifyRequiredSevicesAreRunning();
                caseForJob = openCase();
                if (null != caseForJob) {
                    try {
                        ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath));
                        nodeData.setStatus(PROCESSING);
                        coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath, nodeData.toArray());
                        ingestDataSource(caseForJob); // RJCTODO: May need to return a processing status, or may not need to return status this way at all
                        currentJob.setStage(AutoIngestJob.Stage.COMPLETED); // RJCTODO: This is in the wrong place?
                    } catch (CoordinationServiceException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Error closing case for %s", manifestPath), ex);
                        throw new SystemErrorException();
                    } finally {
                        try {
                            caseForJob.closeCase(); // RJCTODO: Need case closed exception?
                        } catch (CaseActionException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error closing case for %s", manifestPath), ex);
                            throw new SystemErrorException();
                        } finally {
                            try {
                                ManifestNodeData nodeData = new ManifestNodeData(coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath));
                                if (AutoIngestJob.Stage.COMPLETED == currentJob.getStage() || AutoIngestJob.Stage.CANCELLED == currentJob.getStage()) {
                                    nodeData.setStatus(COMPLETED);
                                } else {
                                    nodeData.setStatus(PENDING);
                                }
                                coordinationService.setNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath, nodeData.toArray());
                            } catch (CoordinationServiceException ex) {
                                LOGGER.log(Level.SEVERE, String.format("Error closing case for %s", manifestPath), ex);
                                throw new SystemErrorException();
                            }
                        }
                    }
                }
            }
        }

        /**
         * If using shared configuration, downloads the latest version of the
         * settings.
         *
         *
         * @return True on success, false on failure.
         *
         * @throws SystemErrorException if there is an error downloading shared
         *                              configuration.
         * @throws InterruptedException if the thread running the auto ingest
         *                              job processing task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private boolean configurationUpToDate() throws SystemErrorException, InterruptedException {
            if (AutoIngestUserPreferences.getSharedConfigEnabled()) {
                Manifest manifest = currentJob.getManifest();
                Path manifestPath = manifest.getFilePath();
                LOGGER.log(Level.INFO, "Downloading shared configuration for {0}", manifestPath);
                currentJob.setStage(AutoIngestJob.Stage.UPDATING_SHARED_CONFIG);
                try {
                    SharedConfiguration config = new SharedConfiguration();
                    if (SharedConfiguration.SharedConfigResult.LOCKED == config.downloadConfiguration()) {
                        LOGGER.log(Level.WARNING, "Timed out trying to download shared configuration for {0} timed out", manifestPath);
                        return false;
                    }
                } catch (CoordinationServiceException | SharedConfigurationException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error downloading shared configuration for %s", manifestPath), ex);
                    throw new SystemErrorException();
                }
            }
            return true;
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
        private void verifyRequiredSevicesAreRunning() throws SystemErrorException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            LOGGER.log(Level.INFO, "Starting checking services stage for {0}", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.CHECKING_SERVICES);
            try {
                if (!isServiceUp(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString())) {
                    LOGGER.log(Level.SEVERE, "Case database server is down for {0}", manifestPath);
                    throw new SystemErrorException();
                }
                if (!isServiceUp(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString())) {
                    LOGGER.log(Level.SEVERE, "Solr server is down for {0}", manifestPath);
                    throw new SystemErrorException();
                }
            } catch (ServicesMonitorException ex) {
                LOGGER.log(Level.SEVERE, String.format("Services monitor error for %s", manifestPath), ex);
                throw new SystemErrorException();
            } finally {
                LOGGER.log(Level.INFO, "Finished checking services stage for {0}", manifestPath);
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
         * Creates or opens the case for the current automated ingest job as
         * specified in the manifest. Acquires an exclusive lock on the case
         * name during the operation. The case name lock is used so that only
         * one auto ingest node at a time can attempt to create/open/delete the
         * case.
         *
         * @return The case on success, null otherwise.
         *
         * @throws SystemErrorException if there is an error acquiring or
         *                              releasing a coordination service case
         *                              name locks or there is an error creating
         *                              or opening the case for the automated
         *                              ingest job.
         * @throws InterruptedException if the thread running the auto ingest
         *                              job processing task is interrupted while
         *                              blocked, i.e., if auto ingest is
         *                              shutting down.
         */
        private Case openCase() throws SystemErrorException, InterruptedException {
            currentJob.setStage(AutoIngestJob.Stage.OPENING_CASE);

            /*
             * Acquire an exclusive lock on the case name specified in the
             * manifest and create or open the case. The case name lock is used
             * so that only one auto ingest node at a time can attempt to
             * create/open/delete the case.
             */
            Manifest manifest = currentJob.getManifest();
            String caseName = manifest.getCaseName();
            Path caseNameLockPath = rootOutputDirectory.resolve(caseName);
            try (Lock caseNameLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseNameLockPath.toString(), 30, TimeUnit.MINUTES);) {
                if (null == caseNameLock) {
                    LOGGER.log(Level.WARNING, "Auto ingest timed out trying to acquire case name lock for {0}", manifest.getFilePath());
                    return null;
                }
                Path caseDirectoryPath = PathUtils.findCaseDirectory(rootOutputDirectory, caseName);
                if (null != caseDirectoryPath) {
                    Path metadataFilePath = caseDirectoryPath.resolve(manifest.getCaseName() + CaseMetadata.getFileExtension());
                    Case.open(metadataFilePath.toString());
                } else {
                    caseDirectoryPath = PathUtils.createCaseFolderPath(rootOutputDirectory, caseName);
                    Case.create(caseDirectoryPath.toString(), currentJob.getManifest().getCaseName(), "", "", CaseType.MULTI_USER_CASE);
                    /*
                     * Sleep a bit before releasing the lock to ensure that the
                     * new case folder is visible on the network.
                     */
                    Thread.sleep(AutoIngestUserPreferences.getSecondsToSleepBetweenCases() * 1000);
                }
                currentJob.setCaseDirectoryPath(caseDirectoryPath);
                return Case.getCurrentCase();

            } catch (CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, String.format("Coordination service error acquring or releasing case name lock for %s", manifest.getFilePath()), ex);
                addErrorConditionToCase("Coordination service error");
                throw new SystemErrorException();

            } catch (CaseActionException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error creating or opening case for %s", manifest.getFilePath()), ex);
                addErrorConditionToCase("Error creating or opening case");
                throw new SystemErrorException();

            } catch (IllegalStateException ex) {
                /*
                 * Deal with the unfortunate facts that Case.create and
                 * Case.open do not return a Case object and Case.getCurrentCase
                 * throws IllegalStateException.
                 */
                LOGGER.log(Level.SEVERE, String.format("Failed to get current case for %s", manifest.getFilePath()), ex);
                addErrorConditionToCase("Error creating or opening case");
                throw new SystemErrorException();
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
        private void ingestDataSource(Case caseForJob) throws SystemErrorException, InterruptedException {
            try {
                if (!jobIsCancelled()) {
                    DataSource dataSource = identifyDataSource(caseForJob);
                    if (!jobIsCancelled() && null != dataSource) {
                        runDataSourceProcessor(caseForJob, dataSource);
                        if (!jobIsCancelled() && !dataSource.getContent().isEmpty()) {
                            try {
                                analyze(dataSource);
                            } finally {
                                /*
                                 * Sleep to allow ingest event subscribers to do
                                 * their event handling.
                                 */
                                Thread.sleep(AutoIngestUserPreferences.getSecondsToSleepBetweenCases() * 1000); // RJCTODO: Change the setting description
                            }
                            if (!jobIsCancelled()) {
                                exportFiles(dataSource);
                            }
                        }
                    }
                }
            } catch (AutoIngestJobLoggerException ex) {
                // RJCTODO
                throw new SystemErrorException();
            } finally {
                // RJCTODO
            }
        }

        /**
         * Checks to see if either the current job has been cancelled or this
         * auto ingest task has been cancelled because auto ingest is shutting
         * down. If cancellation has occurred, writes an alert file in the case
         * directory for the job and makes an entry in the case auto ingest log.
         *
         * @return True if the job was cancelled, false otherwise.
         *
         * @throws AutoIngestJobLoggerException if there was an error writing to
         *                                      the case auto ingest log.
         * @throws InterruptedException         if the thread running the auto
         *                                      ingest job processing task is
         *                                      interrupted while blocked, i.e.,
         *                                      if auto ingest is shutting down.
         */
        private boolean jobIsCancelled() throws AutoIngestJobLoggerException, InterruptedException {
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
        private DataSource identifyDataSource(Case caseForJob) throws SystemErrorException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            LOGGER.log(Level.INFO, "Starting identifying data source stage for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.IDENTIFYING_DATA_SOURCE);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {
                try {
                    Path dataSourcePath = manifest.getDataSourcePath();
                    File dataSource = dataSourcePath.toFile();
                    if (!dataSource.exists()) {
                        LOGGER.log(Level.SEVERE, "Missing data source for {0}", manifestPath);
                        jobLogger.logMissingDataSource();
                        return null;
                    }
                    String deviceId = manifest.getDeviceId();
                    if (FileFilters.isAcceptedByFilter(dataSource, FileFilters.archiveFilters)) {
                        Path extractedDataSource = extractDataSource(caseForJob, dataSourcePath);
                        LOGGER.log(Level.INFO, "Identified data source type for {0} as {1}", new Object[]{manifestPath, DataSource.Type.CELLEBRITE_PHYSICAL_REPORT});
                        jobLogger.logDataSourceTypeId(DataSource.Type.CELLEBRITE_PHYSICAL_REPORT.toString());
                        return new DataSource(deviceId, extractedDataSource, DataSource.Type.CELLEBRITE_PHYSICAL_REPORT);
                    /*} ELTODO else if (FileFilters.isAcceptedByFilter(dataSource, FileFilters.cellebriteLogicalReportFilters)) {
                        DataSource.Type type = parseCellebriteLogicalReportType(dataSourcePath);
                        if (null != type) {
                            LOGGER.log(Level.INFO, "Identified data source type for {0} as {1}", new Object[]{manifestPath, type});
                            jobLogger.logDataSourceTypeId(type.toString());
                            return new DataSource(deviceId, dataSourcePath, type);
                        }
                    */} else if (VirtualMachineFinder.isVirtualMachine(manifest.getDataSourceFileName())) {
                        LOGGER.log(Level.INFO, "Identified data source type for {0} as {1} (VM)", new Object[]{manifestPath, DataSource.Type.DRIVE_IMAGE});
                        jobLogger.logDataSourceTypeId(DataSource.Type.DRIVE_IMAGE.toString());
                        return new DataSource(deviceId, dataSourcePath, DataSource.Type.DRIVE_IMAGE);
                    } else if (imageHasFileSystem(caseForJob, dataSourcePath)) {
                        LOGGER.log(Level.INFO, "Identified data source type for {0} as {1}", new Object[]{manifestPath, DataSource.Type.DRIVE_IMAGE});
                        jobLogger.logDataSourceTypeId(DataSource.Type.DRIVE_IMAGE.toString());
                        return new DataSource(deviceId, dataSourcePath, DataSource.Type.DRIVE_IMAGE);
                    } else {
                        LOGGER.log(Level.INFO, "Identified data source type for {0} as {1}", new Object[]{manifestPath, DataSource.Type.PHONE_IMAGE});
                        jobLogger.logDataSourceTypeId(DataSource.Type.PHONE_IMAGE.toString());
                        return new DataSource(deviceId, dataSourcePath, DataSource.Type.PHONE_IMAGE);
                    }
                    //ELTODO currently unreachable code
                    //ELTODO LOGGER.log(Level.INFO, "Failed to identify data source type for {0}", manifestPath);
                    //ELTODO jobLogger.logFailedToIdentifyDataSource();
                    //ELTODO return null;

                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error identifying data source for %s", manifestPath), ex);
                    jobLogger.logDataSourceTypeIdError(ex);
                    throw new SystemErrorException();
                }

            } catch (AutoIngestJobLoggerException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error logging data source identification results for %s", manifestPath), ex);
                throw new SystemErrorException();

            } finally {
                LOGGER.log(Level.INFO, "Finished identifying data source stage for {0}", manifestPath);
            }
        }

        /**
         * Extracts the contents of a ZIP archive submitted as a data source to
         * a subdirectory of the auto ingest module output directory.
         *
         * @throws IOException if there is a problem extracting the data source
         *                     from the archive.
         */
        private Path extractDataSource(Case caseForJob, Path dataSourcePath) throws IOException {
            String dataSourceFileNameNoExt = FilenameUtils.removeExtension(dataSourcePath.getFileName().toString());
            Path destinationFolder = Paths.get(caseForJob.getModuleDirectory(),
                    AUTO_INGEST_MODULE_OUTPUT_DIR,
                    dataSourceFileNameNoExt + "_" + TimeStampUtils.createTimeStamp());
            Files.createDirectories(destinationFolder);

            int BUFFER_SIZE = 524288; // Read/write 500KB at a time
            File sourceZipFile = dataSourcePath.toFile();
            ZipFile zipFile;
            zipFile = new ZipFile(sourceZipFile, ZipFile.OPEN_READ);
            Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
            try {
                while (zipFileEntries.hasMoreElements()) {
                    ZipEntry entry = zipFileEntries.nextElement();
                    String currentEntry = entry.getName();
                    File destFile = new File(destinationFolder.toString(), currentEntry);
                    destFile = new File(destinationFolder.toString(), destFile.getName());
                    File destinationParent = destFile.getParentFile();
                    destinationParent.mkdirs();
                    if (!entry.isDirectory()) {
                        BufferedInputStream is = new BufferedInputStream(zipFile.getInputStream(entry));
                        int currentByte;
                        byte data[] = new byte[BUFFER_SIZE];
                        try (FileOutputStream fos = new FileOutputStream(destFile); BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER_SIZE)) {
                            currentByte = is.read(data, 0, BUFFER_SIZE);
                            while (currentByte != -1) {
                                dest.write(data, 0, currentByte);
                                currentByte = is.read(data, 0, BUFFER_SIZE);
                            }
                        }
                    }
                }
            } finally {
                zipFile.close();
            }
            return destinationFolder;
        }

        /**
         * Attempts to parse a data source as a Cellebrite logical report.
         *
         * @param dataSourcePath The path to the data source.
         *
         * @return Type of Cellebrite logical report if the data source is a
         *         valid Cellebrite logical report file, null otherwise.
         */
        private DataSource.Type parseCellebriteLogicalReportType(Path dataSourcePath) {
            String report_type;
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(dataSourcePath.toFile());
                XPathFactory xPathfactory = XPathFactory.newInstance();
                XPath xpath = xPathfactory.newXPath();
                XPathExpression expr = xpath.compile("/reports/report/general_information/report_type/text()");
                report_type = (String) expr.evaluate(doc, XPathConstants.STRING);
                if (report_type.equalsIgnoreCase("sim")) {
                    return DataSource.Type.CELLEBRITE_LOGICAL_SIM;
                } else if (report_type.equalsIgnoreCase("cell")) {
                    return DataSource.Type.CELLEBRITE_LOGICAL_HANDSET;
                } else {
                    return null;
                }
            } catch (Exception ignore) {
                // Not a valid Cellebrite logical report file.
                return null;
            }
        }

        /**
         * Uses the installed tsk_isImageTool executable to determine whether a
         * potential data source has a file system.
         *
         * @param dataSourcePath The path to the data source.
         *
         * @return True or false.
         *
         * @throws IOException if an error occurs while trying to determine if
         *                     the data source has a file system.
         */
        private boolean imageHasFileSystem(Case caseForJob, Path dataSourcePath) throws IOException {
            Path logFileName = Paths.get(caseForJob.getTempDirectory(), "tsk_isImageTool.log"); // RJCTODO: Pass case through to avoid these calls
            File logFile = new File(logFileName.toString());
            Path errFileName = Paths.get(caseForJob.getTempDirectory(), "tsk_isImageTool_err.log");
            File errFile = new File(errFileName.toString());
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "\"" + tskIsImageToolExePath.toString() + "\"",
                    "\"" + dataSourcePath + "\"");
            File directory = new File(tskIsImageToolExePath.getParent().toString());
            processBuilder.directory(directory);
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            int exitValue = ExecUtil.execute(processBuilder);
            Files.delete(logFileName);
            Files.delete(errFileName);
            return (exitValue == 0);
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
        private void runDataSourceProcessor(Case caseForJob, DataSource dataSource) throws SystemErrorException, InterruptedException {
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
                    synchronized (ingestLock) {
                        switch (dataSource.type) {
                            case DRIVE_IMAGE:
                                new ImageDSProcessor().run(dataSource.getDeviceId(),
                                        dataSource.getPath().toString(),
                                        "",
                                        false,
                                        progressMonitor,
                                        callBack);
                                break;

                            // ELTODO plug in data source processor lookup
                            case CELLEBRITE_LOGICAL_HANDSET:
                            case CELLEBRITE_LOGICAL_SIM:
                            case PHONE_IMAGE:
                            case CELLEBRITE_PHYSICAL_REPORT:
                            default:
                                LOGGER.log(Level.SEVERE, "Unsupported data source type {0} for {1}", new Object[]{dataSource.getType(), manifestPath});  // NON-NLS
                        }
                        ingestLock.wait();
                    }
                } finally {
                    String imageType = dataSource.getType().toString();
                    DataSourceProcessorResult resultCode = dataSource.getResultDataSourceProcessorResultCode();
                    if (null != resultCode) {
                        switch (resultCode) {
                            case NO_ERRORS:
                                jobLogger.logDataSourceAdded(dataSource.getType().toString());
                                break;

                            case NONCRITICAL_ERRORS:
                                jobLogger.logDataSourceAdded(dataSource.getType().toString());
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

            } catch (AutoIngestJobLoggerException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error logging adding data source results for %s", manifestPath), ex);
                throw new SystemErrorException();

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
        private void analyze(DataSource dataSource) throws SystemErrorException, InterruptedException {
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
                                // RJCTODO: Do we have an issue here with the logging of results for embedded data sources?
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
                                throw new SystemErrorException();
                            } else {
                                LOGGER.log(Level.SEVERE, String.format("Ingest startup error for %s", manifestPath), ingestJobStartResult.getStartupException());
                                jobLogger.logAnalysisStartupError(ingestJobStartResult.getStartupException());
                                throw new SystemErrorException();
                            }
                        } else {
                            for (String warning : ingestJobSettings.getWarnings()) {
                                LOGGER.log(Level.SEVERE, "Analysis settings error for {0}: {1}", new Object[]{manifestPath, warning});
                            }
                            jobLogger.logIngestJobSettingsErrors(ingestJobSettings.getWarnings());
                            throw new SystemErrorException();
                        }
                    }
                } finally {
                    IngestManager.getInstance().removeIngestJobEventListener(completionListener);
                }
            } catch (AutoIngestJobLoggerException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error logging analysis stage results for %s", manifestPath), ex);
                throw new SystemErrorException();
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
        private void exportFiles(DataSource dataSource) throws SystemErrorException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            LOGGER.log(Level.INFO, "Starting file export stage for {0} ", manifestPath);
            currentJob.setStage(AutoIngestJob.Stage.EXPORTING_FILES);
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath);
            try {
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
                    jobLogger.logFileExportError(ex);
                    throw new SystemErrorException();
                }
            } catch (AutoIngestJobLoggerException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error logging file export results for %s", manifestPath), ex);
                throw new SystemErrorException();
            } finally {
                LOGGER.log(Level.INFO, "Finished file export stage for {0} ", manifestPath);
            }
        }

        /**
         * Adds an exception to the case auto ingest log of the current job and
         * writes an alert file to the case directory of the current job.
         *
         * @param errorMessage An error message describing the error condition.
         *
         * @throws SystemErrorException if there is an error adding the error
         *                              condition to the case.
         * @throws InterruptedException if the thread running the job processing
         *                              task is interrupted while blocked, i.e.,
         *                              if auto ingest is shutting down.
         */
        // RJCTODO: Remove
        private void addErrorConditionToCase(String errorMessage) throws SystemErrorException, InterruptedException {
            Manifest manifest = currentJob.getManifest();
            Path manifestPath = manifest.getFilePath();
            Path caseDirectoryPath = currentJob.getCaseDirectoryPath();
            try {
                new AutoIngestJobLogger(manifestPath, manifest.getDataSourceFileName(), caseDirectoryPath).logErrorCondition(errorMessage);
            } catch (AutoIngestJobLoggerException logex) {
                LOGGER.log(Level.SEVERE, String.format("Error writing to case auto ingest log for %s", manifestPath), logex);
                throw new SystemErrorException();
            }
        }

        /**
         * RJCTODO
         */
        private final class SystemErrorException extends Exception {

            private static final long serialVersionUID = 1L;

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

    private static final class FileFilters {

        //ELTODO private static final List<FileFilter> cellebriteLogicalReportFilters = CellebriteXMLProcessor.getFileFilterList();
        private static final GeneralFilter zipFilter = new GeneralFilter(Arrays.asList(new String[]{".zip"}), "");
        private static final List<FileFilter> archiveFilters = new ArrayList<>();

        static {
            archiveFilters.add(zipFilter);
        }

        private static boolean isAcceptedByFilter(File file, List<FileFilter> filters) {
            for (FileFilter filter : filters) {
                if (filter.accept(file)) {
                    return true;
                }
            }
            return false;
        }

        private FileFilters() {
        }
    }

    @ThreadSafe
    private static final class DataSource {

        private enum Type {

            CELLEBRITE_PHYSICAL_REPORT,
            CELLEBRITE_LOGICAL_HANDSET,
            CELLEBRITE_LOGICAL_SIM,
            DRIVE_IMAGE,
            PHONE_IMAGE,
        }

        private final String deviceId;
        private final Path path;
        private final Type type;
        private DataSourceProcessorResult resultCode;
        private List<String> errorMessages;
        private List<Content> content;

        DataSource(String deviceId, Path path, Type type) {
            this.deviceId = deviceId;
            this.path = path;
            this.type = type;
        }

        String getDeviceId() {
            return deviceId;
        }

        Path getPath() {
            return this.path;
        }

        Type getType() {
            return type;
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
