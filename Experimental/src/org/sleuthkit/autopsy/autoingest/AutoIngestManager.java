/*
 * UNCLASSIFIED
 *
 *  Viking
 *
 *  Copyright (c) 2013-2016 Basis Technology Corporation.
 *  Contact: brianc@basistech.com
 */
package viking.autoingest;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import viking.configuration.VikingUserPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
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
import java.util.Date;
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
import javax.swing.filechooser.FileFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FilenameUtils;
import org.joda.time.DateTime;
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
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.datamodel.Content;
import org.w3c.dom.Document;
import viking.application.CoordinationService;
import viking.application.CoordinationService.CoordinationServiceException;
import viking.application.CoordinationService.Lock;
import static viking.autoingest.StateFile.Type.READY;
import static viking.autoingest.StateFile.Type.PROCESSING;
import static viking.autoingest.StateFile.Type.DONE;
import static viking.autoingest.StateFile.Type.CANCELLED;
import static viking.autoingest.StateFile.Type.ERROR;
import static viking.autoingest.StateFile.Type.INTERRUPTED;
import static viking.autoingest.StateFile.Type.DELETED;
import viking.autoingest.events.AutoIngestCasePrioritizedEvent;
import viking.autoingest.events.AutoIngestJobCompletedEvent;
import viking.autoingest.events.AutoIngestJobStartedEvent;
import viking.autoingest.events.AutoIngestJobStatusEvent;
import viking.configuration.SharedConfiguration;
import viking.cellex.datasourceprocessors.CellebriteXMLProcessor;
import viking.cellex.datasourceprocessors.MPFProcessor;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.ingest.IngestJobStartResult;
import viking.autoingest.FileExporter.FileExportException;
import viking.autoingest.events.AutoIngestCaseDeletedEvent;
import viking.cellex.datasourceprocessors.CellebriteAndroidImageProcessor;
import viking.configuration.SharedConfiguration.SharedConfigurationException;

/**
 * The automated ingest manager is responsible for recognizing when new folders
 * of image files are added to a root image folder and for processing those
 * folders as auto ingest jobs.
 */
public final class AutoIngestManager extends Observable implements PropertyChangeListener {

    /*
     * The possible states of an auto ingest manager.
     */
    enum State {

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

        JOB_STARTED,
        JOB_STATUS_UPDATED,
        JOB_COMPLETED,
        CASE_PRIORITIZED,
        CASE_DELETED,
        PAUSED_BY_REQUEST,
        PAUSED_CASE_DATABASE_SERVICE_DOWN,
        PAUSED_KEYWORD_SEARCH_SERVICE_DOWN,
        PAUSED_COORDINATION_SERVICE_DOWN,
        PAUSED_FAILED_WRITING_STATE_FILES,
        PAUSED_SHARED_CONFIG_ERROR,
        PAUSED_FAILED_TO_START_INGEST_JOB,
        PAUSED_FAILED_TO_START_FILE_EXPORTER,
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

    /**
     * Represents the state of an auto ingest job at any given moment during its
     * lifecycle as it moves from waiting to be processed, through the various
     * stages of processing, to its final completed state.
     */
    static final class JobIngestStatus {

        private enum IngestStatus {

            PENDING("Pending"),
            STARTING("Starting"),
            UPDATING_SHARED_CONFIG("Updating shared configuration"),
            CHECKING_SERVICES("Checking services"),
            OPENING_CASE("Opening case"),
            IDENTIFYING_IMAGES("Identifying images"),
            ADDING_IMAGES("Adding images"),
            ANALYZING_IMAGES("Analyzing images"),
            ANALYZING_FILES("Analyzing files"),
            EXPORTING_FILES("Exporting files"),
            CANCELLING_MODULE("Cancelling module"),
            CANCELLING("Cancelling"),
            COMPLETED("Completed");

            private final String displayText;

            private IngestStatus(String displayText) {
                this.displayText = displayText;
            }

            String getDisplayText() {
                return displayText;
            }

        }

        private IngestStatus ingestStatus;
        private String statusDisplayName;
        private Date startDate;
        private IngestJob ingestJob;
        private boolean cancelled;
        private Date dateCompleted;

        private JobIngestStatus(Date dateCompleted) {
            ingestStatus = IngestStatus.PENDING;
            statusDisplayName = ingestStatus.getDisplayText();
            startDate = DateTime.now().toDate();
            this.dateCompleted = dateCompleted;
        }

        /**
         * Updates displayed status and start fileTime of auto ingest job. Used
         * primarily to display status of remote running jobs.
         *
         * @param newDisplayName Displayed status of the auto ingest job.
         * @param startTime      Start fileTime of the current activity.
         */
        synchronized private void setStatus(String newDisplayName, Date startTime) {
            statusDisplayName = newDisplayName;
            startDate = startTime;
        }

        /**
         * Updates status of auto ingest job. Sets current fileTime as activity
         * start fileTime. Used to update status of local running job.
         *
         * @param newStatus Status of the auto ingest job.
         */
        synchronized private void setStatus(IngestStatus newStatus) {
            if (ingestStatus == IngestStatus.CANCELLING && newStatus != IngestStatus.COMPLETED) {
                /**
                 * Do not overwrite canceling status with anything other than
                 * completed status.
                 */
                return;
            }
            ingestStatus = newStatus;
            statusDisplayName = ingestStatus.getDisplayText();
            startDate = Date.from(Instant.now());
            if (ingestStatus == IngestStatus.COMPLETED) {
                /**
                 * Release the reference for garbage collection since this
                 * object may live for a long time within a completed job.
                 */
                ingestJob = null;
            }
            if (ingestStatus == IngestStatus.COMPLETED) {
                dateCompleted = startDate;
            }
        }

        synchronized private void setIngestJob(IngestJob ingestJob) {
            /**
             * Once this field is set, the ingest job should be used to
             * determine the current activity up until the the job is completed.
             */
            this.ingestJob = ingestJob;
        }

        synchronized AutoIngestJob.Status getStatus() {
            if (null != ingestJob && ingestStatus != IngestStatus.CANCELLING && ingestStatus != IngestStatus.EXPORTING_FILES) {
                String activityDisplayText;
                IngestJob.ProgressSnapshot progress = ingestJob.getSnapshot();
                IngestJob.DataSourceIngestModuleHandle ingestModuleHandle = progress.runningDataSourceIngestModule();
                if (null != ingestModuleHandle) {
                    /**
                     * A first or second stage data source level ingest module
                     * is running. Reporting this takes precedence over
                     * reporting generic file analysis.
                     */
                    startDate = ingestModuleHandle.startTime();
                    if (!ingestModuleHandle.isCancelled()) {
                        activityDisplayText = ingestModuleHandle.displayName();
                    } else {
                        activityDisplayText = String.format(IngestStatus.CANCELLING_MODULE.getDisplayText(), ingestModuleHandle.displayName());
                    }
                } else {
                    /**
                     * If no data source level ingest module is running, then
                     * either it is still the first stage of analysis and file
                     * level ingest modules are running or another ingest job is
                     * still running. Note that there can be multiple ingest
                     * jobs running in parallel. For example, there is an ingest
                     * job created to ingest each extracted virtual machine.
                     */
                    activityDisplayText = IngestStatus.ANALYZING_FILES.getDisplayText();
                    startDate = progress.fileIngestStartTime();
                }
                return new AutoIngestJob.Status(activityDisplayText, startDate);
            } else {
                return new AutoIngestJob.Status(statusDisplayName, startDate);
            }
        }

        synchronized private IngestJob setStatusCancelled() {
            cancelled = true;
            setStatus(JobIngestStatus.IngestStatus.CANCELLING);
            return ingestJob;
        }

        synchronized private IngestJob cancelModule() {
            setStatus(JobIngestStatus.IngestStatus.CANCELLING_MODULE);
            return ingestJob;
        }

        synchronized private boolean isCancelled() {
            return cancelled;
        }

        synchronized Date getDateCompleted() {
            return dateCompleted;
        }

        synchronized Date getDateStarted() {
            return startDate;
        }

    }

    private static final Logger logger = Logger.getLogger(AutoIngestManager.class.getName());
    static final String ROOT_NAMESPACE = "viking";
    private static final AutoIngestJob.PrioritizedPendingListComparator prioritizedPendingListComparator = new AutoIngestJob.PrioritizedPendingListComparator();
    private static final AutoIngestJob.AlphabeticalComparator alphabeticalComparator = new AutoIngestJob.AlphabeticalComparator();
    private static AutoIngestManager instance;
    private volatile State state;

    /*
     * The root image folder is where an auto ingest manager looks for images to
     * process. The folders within this folder are treated as the root image
     * folders for individual cases (the folder names are used as case names).
     * The folder trees within these top level case image folders are
     * periodically searched for images to be processed as auto ingest jobs for
     * a case.
     */
    private Path rootImageFolderPath;

    /*
     * The root case folder is where an auto ingest manager creates case
     * folders. Each case folder created by the manager is given a time stamp
     * suffix to ensure uniqueness.
     */
    private Path rootCaseFolderPath;

    /*
     * An auto ingest manager has a queue of pending auto ingest jobs, a list of
     * running jobs (the current job on this auto ingest node plus the jobs
     * running on other nodes), and a list of completed jobs. Access to these
     * collections is synchronized using a job lists monitor.
     */
    private List<AutoIngestJob> pendingJobs;
    private volatile AutoIngestJob currentJob;
    private final ConcurrentHashMap<String, AutoIngestJob> hostNamesToRunningJobs;
    private List<AutoIngestJob> completedJobs;
    private final Object jobListsMonitor;

    /*
     * An image folder scanner searches the folder tree within the root image
     * folder for image folders associated with ready-to-process, completed, and
     * crashed auto ingest jobs and uses the search results to refresh the
     * pending jobs queue and the completed jobs list. Periodic image folder
     * scans are done in tasks run by a executor wrapped in a completion
     * service.
     */
    private static final int NUMBER_OF_IMAGE_FOLDER_SCAN_SCHEDULING_THREADS = 1;
    private static final String IMAGE_FOLDER_SCAN_SCHEDULER_THREAD_NAME = "AIM-folder-scan-scheduler-%d";
    private static final long IMAGE_FOLDER_SCAN_INTERVAL = 300000; // 5 minutes
    private static final String IMAGE_FOLDER_SCAN_THREAD_NAME = "AIM-folder-scan-%d";
    private final ScheduledThreadPoolExecutor imageFolderScanScheduler;
    private final ExecutorService imageFolderScanExecutor;
    private final ExecutorCompletionService<Void> imageFolderScanCompletionService;

    /*
     * Automated ingest is done by a task that runs in an executor until
     * cancelled. The task is started when the auto ingest manager is started
     * and is cancelled when the auto ingest manager shuts down. The auto ingest
     * task thread blocks on the image folder scan completion service. When a
     * scan completes, it process auto ingest jobs until all of the eligible
     * jobs in the queue are completed.
     */
    private static final String AUTO_INGEST_THREAD_NAME = "AIM-auto-ingest-%d";
    private final ExecutorService autoIngestTaskExecutor;
    private AutoIngestTask autoIngestTask;
    private Future<?> autoIngestTaskFuture;

    /*
     * A task is run in an executor to periodically publish status events for
     * the current auto ingest job so that other auto ingest nodes can include
     * the job in their running jobs list. The events messages from other auto
     * ingest nodes received by this auto ingest manager are used to detect when
     * an auto ingest node goes down so that the list of jobs running on other
     * nodes can be kept up to dater.
     */
    private static final String localHostName = NetworkUtils.getLocalHostName();
    private final AutopsyEventPublisher eventPublisher;
    private static final String EVENT_CHANNEL_NAME = "Auto-Ingest-Manager-Events";
    private static final Set<String> EVENT_LIST = new HashSet<>(Arrays.asList(new String[]{
        Event.JOB_STATUS_UPDATED.toString(),
        Event.JOB_COMPLETED.toString(),
        Event.CASE_PRIORITIZED.toString(),
        Event.JOB_STARTED.toString()}));
    private static final long JOB_STATUS_EVENT_INTERVAL_SECONDS = 10;
    private static final String JOB_STATUS_PUBLISHING_THREAD_NAME = "AIM-job-status-event-publisher-%d";
    private final ScheduledThreadPoolExecutor jobStatusPublishingExecutor;
    private static final long MAX_MISSED_JOB_STATUS_UPDATES = 10;
    private final ConcurrentHashMap<String, Instant> hostNamesToLastMsgTime;

    /*
     * An executable from the SleuthKit is used by the auto ingest tasks to
     * determine whether or not a file in an image folder is an image file.
     */
    private static final String TSK_IS_IMAGE_TOOL_DIR = "tsk_isImageTool";
    private static final String TSK_IS_IMAGE_TOOL_EXE = "tsk_isImageTool.exe";
    private Path tskIsImageToolExePath;

    private CoordinationService coordinationService;

    /**
     * Gets the singleton automated ingest manager responsible for recognizing
     * when new folders of image files are added to a root image folder and for
     * processing those folders as auto ingest jobs.
     *
     * @return A singleton AutoIngestManager object.
     */
    synchronized static AutoIngestManager getInstance() {
        if (instance == null) {
            instance = new AutoIngestManager();
        }
        return instance;
    }

    /**
     * Constructs an automated ingest manager responsible for recognizing when
     * new input folders of image files are added to a root input folder and for
     * processing those folders as auto ingest jobs.
     */
    private AutoIngestManager() {
        jobListsMonitor = new Object();
        pendingJobs = new ArrayList<>();
        completedJobs = new ArrayList<>();
        hostNamesToRunningJobs = new ConcurrentHashMap<>();
        hostNamesToLastMsgTime = new ConcurrentHashMap<>();
        eventPublisher = new AutopsyEventPublisher();
        imageFolderScanScheduler = new ScheduledThreadPoolExecutor(NUMBER_OF_IMAGE_FOLDER_SCAN_SCHEDULING_THREADS, new ThreadFactoryBuilder().setNameFormat(IMAGE_FOLDER_SCAN_SCHEDULER_THREAD_NAME).build());
        imageFolderScanExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(IMAGE_FOLDER_SCAN_THREAD_NAME).build());
        imageFolderScanCompletionService = new ExecutorCompletionService<>(imageFolderScanExecutor);
        autoIngestTaskExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(AUTO_INGEST_THREAD_NAME).build());
        jobStatusPublishingExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(JOB_STATUS_PUBLISHING_THREAD_NAME).build());
        state = State.IDLE;
    }

    /**
     * Starts automated ingest.
     *
     * @throws Exception
     */
    /*
     * TODO (RC): Make throw something more specific than Exception
     */
    void startUp() throws CoordinationServiceException, IOException, Exception {
        rootImageFolderPath = Paths.get(VikingUserPreferences.getAutoModeImageFolder());
        rootCaseFolderPath = Paths.get(VikingUserPreferences.getAutoModeResultsFolder());
        tskIsImageToolExePath = locateTskIsImageToolExecutable();
        coordinationService = CoordinationService.getInstance(ROOT_NAMESPACE);
        RuntimeProperties.setCoreComponentsActive(false);
        imageFolderScanScheduler.scheduleAtFixedRate(new ImageFoldersScanSchedulingTask(), 0, IMAGE_FOLDER_SCAN_INTERVAL, TimeUnit.MILLISECONDS);
        autoIngestTask = new AutoIngestTask();
        autoIngestTaskFuture = autoIngestTaskExecutor.submit(autoIngestTask);
        jobStatusPublishingExecutor.scheduleAtFixedRate(new PeriodicJobStatusEventTask(), JOB_STATUS_EVENT_INTERVAL_SECONDS, JOB_STATUS_EVENT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        eventPublisher.addSubscriber(EVENT_LIST, instance);
        state = State.RUNNING;
        logger.log(Level.INFO, "Automated ingest started");
    }

    /**
     * Gets the path to the copy of the SleuthKit executable that is used to
     * determine whether or not an image has a file system. The tool is
     * installed during Viking installation, so it is assumed that it only needs
     * to be found on start up.
     *
     * @return The path to the executable.
     *
     * @throws Exception if the executable cannot be found or cannot be
     *                   executed.
     */
    private static Path locateTskIsImageToolExecutable() throws Exception {
        /**
         * Note that exception messages are formulated for presentation to the
         * user.
         */
        if (!PlatformUtil.isWindowsOS()) {
            throw new Exception("Viking automated ingest requires a Windows operating system to run.");
        }

        final File folder = InstalledFileLocator.getDefault().locate(TSK_IS_IMAGE_TOOL_DIR, AutoIngestManager.class.getPackage().getName(), false);
        if (null == folder) {
            throw new Exception("Unable to locate SleuthKit image tool installation folder.");
        }

        Path executablePath = Paths.get(folder.getAbsolutePath(), TSK_IS_IMAGE_TOOL_EXE);
        File executable = executablePath.toFile();
        if (!executable.exists()) {
            throw new Exception("Unable to locate SleuthKit image tool.");
        }

        if (!executable.canExecute()) {
            throw new Exception("Unable to run SleuthKit image tool.");
        }

        return executablePath;
    }

    /**
     * Opens the remote event channel that enables communication with auto
     * ingest manager on other nodes.
     *
     * @throws AutopsyEventException If the channel could not be opened.
     */
    void establishRemoteCommunications() throws AutopsyEventException {
        try {
            eventPublisher.openRemoteEventChannel(EVENT_CHANNEL_NAME);
            logger.log(Level.INFO, "Opened auto ingest event channel");
        } catch (AutopsyEventException ex) {
            logger.log(Level.SEVERE, "Failed to open auto ingest event channel", ex);
            throw ex;
        }
    }

    /**
     * Queries the current state of the auto ingest manager.
     *
     * @return The current state of the auto ingest manager.
     */
    State getState() {
        return state;
    }

    /**
     * Publishes an auto ingest job started event both locally and remotely.
     *
     * @param job The job that was started.
     */
    private void publishJobStartedEvent(AutoIngestJob job) {
        eventPublisher.publish(new AutoIngestJobStartedEvent(localHostName,
                job.getCaseName(),
                job.getIngestStatus().getStatus().getActivityStartDate(),
                job.getImageFolderPath(),
                job.getCaseFolderPath(),
                job.getIngestStatus().getStatus().getActivity()));
    }

    /**
     * Publishes an auto ingest job status event both locally and remotely.
     *
     * @param job The job for which status is to be reported.
     */
    private void publishJobStatusEvent(AutoIngestJob job) {
        eventPublisher.publish(new AutoIngestJobStatusEvent(localHostName,
                job.getCaseName(),
                job.getIngestStatus().getStatus().getActivity(),
                job.getIngestStatus().getStatus().getActivityStartDate(),
                job.getImageFolderPath(),
                job.getCaseFolderPath()));
    }

    /**
     * Publishes an auto ingest job completed event both locally and remotely.
     *
     * @param job         The job that was completed.
     * @param shouldRetry Whether ot not the completed job will be requeued and
     *                    tried again.
     */
    private void publishJobCompletedEvent(AutoIngestJob job, boolean shouldRetry) {
        eventPublisher.publish(new AutoIngestJobCompletedEvent(localHostName,
                job.getCaseName(),
                job.getDateCompleted(),
                job.getImageFolderPath(),
                job.getCaseFolderPath(),
                shouldRetry));
    }

    /**
     * Publishes a case deletion event locally and remotely.
     *
     * @param caseName            The name of the case.
     * @param caseFolderPath      The case folder.
     * @param caseImageFolderPath The top level image folder for the case.
     */
    private void publishCaseDeletedEvent(CaseDeletionResult result) {
        eventPublisher.publishRemotely(new AutoIngestCaseDeletedEvent(localHostName, result));
        setChanged();
        notifyObservers(Event.CASE_DELETED);
    }

    /**
     * Handles local and remote auto ingest events.
     *
     * @param evt An event in the form of a PropertyChangeEvent.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt instanceof AutoIngestJobStartedEvent) {
            AutoIngestJobStartedEvent event = (AutoIngestJobStartedEvent) evt;
            if (event.getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                updateLastMessageTimeForNode(event.getNodeName());
                processRemoteAutoIngestJobStartedEvent(event);
            }
            setChanged();
            notifyObservers(Event.JOB_STARTED);
        } else if (evt instanceof AutoIngestJobStatusEvent) {
            AutoIngestJobStatusEvent event = (AutoIngestJobStatusEvent) evt;
            if (event.getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                updateLastMessageTimeForNode(event.getNodeName());
                processRemoteAutoIngestJobStatusEvent(event);
            }
            setChanged();
            notifyObservers(Event.JOB_STATUS_UPDATED);
        } else if (evt instanceof AutoIngestJobCompletedEvent) {
            AutoIngestJobCompletedEvent event = (AutoIngestJobCompletedEvent) evt;
            if (event.getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                updateLastMessageTimeForNode(event.getNodeName());
                processRemoteAutoIngestJobCompletedEvent(event);
            }
            scanImageFoldersNow();
            setChanged();
            notifyObservers(Event.JOB_COMPLETED);
        } else if (evt instanceof AutoIngestCasePrioritizedEvent) {
            AutoIngestCasePrioritizedEvent event = (AutoIngestCasePrioritizedEvent) evt;
            if (event.getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                updateLastMessageTimeForNode(event.getNodeName());
            }
            scanImageFoldersNow();
            setChanged();
            notifyObservers(Event.CASE_PRIORITIZED);
        } else if (evt instanceof AutoIngestCaseDeletedEvent) {
            AutoIngestCaseDeletedEvent event = (AutoIngestCaseDeletedEvent) evt;
            if (event.getSourceType() == AutopsyEvent.SourceType.REMOTE) {
                updateLastMessageTimeForNode(event.getNodeName());
            }
            /*
             *
             */
            scanImageFoldersNow();
            setChanged();
            notifyObservers(Event.CASE_DELETED);
        }
    }

    /**
     * Updates the mapping of host names to times of last message received.
     *
     * @param hostName The name of the host from which a message was just
     *                 received.
     */
    private void updateLastMessageTimeForNode(String hostName) {
        hostNamesToLastMsgTime.put(hostName, Instant.now());
    }

    private void processRemoteAutoIngestJobStartedEvent(AutoIngestJobStartedEvent event) {
        synchronized (jobListsMonitor) {
            // try to locate the job in the pending queue            
            Path inputFolderPath = Paths.get(event.getInputFolderPath());
            for (Iterator<AutoIngestJob> iterator = pendingJobs.iterator(); iterator.hasNext();) {
                AutoIngestJob pendingJob = iterator.next();
                if (pendingJob.getImageFolderPath().equals(inputFolderPath)) {
                    iterator.remove();
                    break;
                }
            }
        }

        /*
         * Create an AutoIngestJob based on info received in job status event
         * and update remote running jobs map. This way we don't have to update
         * each individual field in AutoIngestJob object that may have been
         * found in pending queue.
         */
        addNewJobToRemoteRunningJobsList(event.getNodeName(), event.getCaseName(), event.getJobStatus(), event.getJobStartTime(),
                Paths.get(event.getInputFolderPath()), Paths.get(event.getOutputFolderPath()));
    }

    private void processRemoteAutoIngestJobStatusEvent(AutoIngestJobStatusEvent event) {
        // No need to use jobStatesLock as we are not accessing local pending, running, and completed queues.
        /*
         * Create an AutoIngestJob based on info received in job status event
         * and update remote running job map. This way we don't have to deal
         * with verifying whether it's the same job and updating each individual
         * field.
         */
        addNewJobToRemoteRunningJobsList(event.getNodeName(), event.getCaseName(), event.getJobStatus(), event.getJobStartTime(),
                Paths.get(event.getInputFolderPath()), Paths.get(event.getOutputFolderPath()));
    }

    private void addNewJobToRemoteRunningJobsList(String nodeName, String caseName, String jobStatus, Date jobStartTime, Path inputFolderPath, Path outputFolderPath) {
        AutoIngestJob job = new AutoIngestJob(inputFolderPath, caseName, outputFolderPath, new JobIngestStatus(Date.from(Instant.now())), nodeName);
        job.setIsLocalJob(false); // set flag that this is a remote job
        job.getIngestStatus().setStatus(jobStatus, jobStartTime); // update currently running activity

        // Add the job to remote running job map
        hostNamesToRunningJobs.put(nodeName, job);
    }

    private void processRemoteAutoIngestJobCompletedEvent(AutoIngestJobCompletedEvent event) {
        // If there is a job for the sender node - remove the job from remote running jobs map.
        hostNamesToRunningJobs.remove(event.getNodeName());

        // Create a job based on info received in job completed event. This way we don't have to deal 
        // with verifying whether it's the same job and updating individual fields.
        AutoIngestJob job = new AutoIngestJob(Paths.get(event.getInputFolderPath()), event.getCaseName(),
                Paths.get(event.getCaseResultsPath()), new JobIngestStatus(event.getJobCompletionTime()), event.getNodeName());
        job.setIsLocalJob(false); // set flag that this is a remote job

        if (event.getShouldRetry() == false) {
            // Move the job to completed list.
            synchronized (jobListsMonitor) {
                completedJobs.add(job);
            }
        }
    }

    /**
     * Cancels the currently running job, shuts down the thread pools, and marks
     * unprocessed jobs with READY state files.
     */
    void shutDown() {
        if (state != State.RUNNING) {
            return;
        }
        state = State.SHUTTING_DOWN;

        logger.log(Level.INFO, "AutoIngestManager shutting down");
        try {
            // stop receiving remote events but keep the remote channel open. We may need to send out events.
            eventPublisher.removeSubscriber(EVENT_LIST, instance);
            stopInputFolderScans();
            stopJobProcessing();
            cleanupJobs();
            eventPublisher.closeRemoteEventChannel(); // close remote channel
            logger.log(Level.INFO, "AutoIngestManager shut down");

        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Thread running AutoIngestManager shutDown() unexpectedly interrupted", ex);
        }
        state = State.IDLE;
    }

    /**
     * Shuts down the input folder scan scheduling and input folder scan tasks
     * and their executors. Note that shutdownNow(): "Attempts to stop all
     * actively executing tasks, halts the processing of waiting tasks, and
     * returns a list of the tasks that were awaiting execution."
     */
    private void stopInputFolderScans() throws InterruptedException {
        imageFolderScanScheduler.shutdownNow();
        imageFolderScanExecutor.shutdownNow();
        while (!imageFolderScanScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
            logger.log(Level.WARNING, "Waited at least thirty seconds for input folder scan scheduling executor to shut down, continuing to wait"); //NON-NLS
        }
        while (!imageFolderScanExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            logger.log(Level.WARNING, "Waited at least thirty seconds for input folder scan executor to shut down, continuing to wait"); //NON-NLS
        }
    }

    /**
     * Cancels the auto ingest job processing task and shut down its executor.
     * Note that shutdown(): "Initiates an orderly shutdown in which previously
     * submitted tasks are executed, but no new tasks will be accepted."
     */
    private void stopJobProcessing() throws InterruptedException {
        synchronized (jobListsMonitor) {
            if (null != currentJob) {
                cancelCurrentJob();
            }
            autoIngestTaskFuture.cancel(true);
            autoIngestTaskExecutor.shutdown();
        }
        while (!autoIngestTaskExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            logger.log(Level.WARNING, "Waited at least thirty seconds for auto ingest executor to shut down, continuing to wait"); //NON-NLS
        }
    }

    /**
     * Clears the job lists and resets current job.
     */
    private void cleanupJobs() {
        synchronized (jobListsMonitor) {
            pendingJobs.clear();
            currentJob = null;
            completedJobs.clear();
        }
    }

    /**
     * Gets a snapshot of one or more of the auto ingest manager's job queues.
     *
     * @param pendingJobs   A list to be populated with pending jobs, can be
     *                      null.
     * @param runningJobs   A list to be populated with running jobs, can be
     *                      null.
     * @param completedJobs A list to be populated with competed jobs, can be
     *                      null.
     */
    void getJobs(List<AutoIngestJob> pendingJobs, List<AutoIngestJob> runningJobs, List<AutoIngestJob> completedJobs) {
        synchronized (jobListsMonitor) {
            if (null != pendingJobs) {
                pendingJobs.clear();
                pendingJobs.addAll(this.pendingJobs);
            }
            if (null != runningJobs) {
                runningJobs.clear();
                // add local job if there is one
                if (null != currentJob) {
                    runningJobs.add(currentJob);
                }
                // add remote jobs
                for (AutoIngestJob job : hostNamesToRunningJobs.values()) {
                    runningJobs.add(job);
                    runningJobs.sort(alphabeticalComparator);
                }
            }
            if (null != completedJobs) {
                completedJobs.clear();
                completedJobs.addAll(this.completedJobs);
            }
        }
    }

    /**
     * Submits an image folder scan task
     */
    void scanImageFoldersNow() {
        if (state != State.RUNNING) {
            return;
        }
        imageFolderScanCompletionService.submit(new ImageFoldersScanTask());
    }

    /**
     * Pauses processing of the pending jobs queue. The currently running job
     * will continue to run to completion.
     */
    void pause() {
        if (state != State.RUNNING) {
            return;
        }
        autoIngestTask.requestPause();
    }

    /**
     * Resumes processing of the pending jobs queue.
     */
    void resume() {
        if (state != State.RUNNING) {
            return;
        }
        autoIngestTask.requestResume();
    }

    /**
     * Writes or updates a prioritized state file for an image input folder and
     * publishes a prioritization event.
     *
     * @param caseName The name of the case associated with the input image
     *                 folder to be prioritized.
     *
     * @return A snapshot of the pending jobs queue after prioritization.
     *
     * @throws IOException
     */
    List<AutoIngestJob> prioritizeCase(String caseName) throws IOException {

        if (state != State.RUNNING) {
            return Collections.emptyList();
        }

        /*
         * Write or update the prioritized state files for each input image
         * folder for the case.
         */
        Path caseInputFolderPath = rootImageFolderPath.resolve(caseName);
        Files.walkFileTree(caseInputFolderPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                prioritizeFolder(directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException ex) throws IOException {
                logger.log(Level.SEVERE, String.format("Error while visiting %s during input folder prioritization", file.toString()), ex);
                return CONTINUE;
            }
        });

        /**
         * Immediately bump all jobs for this case to the top of the pending
         * queue. Note that there is a possibility that the queue will be
         * reordered again as soon as the monitor is released.
         */
        List<AutoIngestJob> pendingJobsSnapshot = new ArrayList<>();
        synchronized (jobListsMonitor) {
            for (AutoIngestJob job : pendingJobs) {
                if (job.getCaseName().equals(caseName)) {
                    job.setPrioritizedFileTimeStamp(new Date());
                }
            }
            Collections.sort(pendingJobs, prioritizedPendingListComparator);
            pendingJobsSnapshot.addAll(pendingJobs);
        }

        /**
         * Publish the event on a separate thread for a speedier return from
         * this method.
         */
        new Thread(() -> {
            eventPublisher.publish(new AutoIngestCasePrioritizedEvent(localHostName, caseName));
        }).start();

        return pendingJobsSnapshot;
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
    List<AutoIngestJob> prioritizeFolder(String caseName, String imageFolderPath) throws IOException {

        if (state != State.RUNNING) {
            return Collections.emptyList();
        }

        /*
         * Write or update the prioritized state file for the folder.
         */
        Path inputFolderPath = rootImageFolderPath.resolve(caseName).resolve(imageFolderPath);
        prioritizeFolder(inputFolderPath);

        /**
         * Immediately bump this job up to the top of the pending queue. Note
         * that there is a possibility that the queue will be reordered again as
         * soon as the monitor is released.
         */
        List<AutoIngestJob> pendingJobsSnapshot = new ArrayList<>();
        synchronized (jobListsMonitor) {
            for (AutoIngestJob job : pendingJobs) {
                if (job.getImageFolderPath().equals(inputFolderPath)) {
                    job.setPrioritizedFileTimeStamp(new Date());
                    break;
                }
            }
            Collections.sort(pendingJobs, prioritizedPendingListComparator);
            pendingJobsSnapshot.addAll(pendingJobs);
        }

        /**
         * Publish the event on a separate thread for a speedier return from
         * this method.
         */
        new Thread(() -> {
            eventPublisher.publish(new AutoIngestCasePrioritizedEvent(localHostName, caseName));
        }).start();

        return pendingJobsSnapshot;
    }

    private void prioritizeFolder(Path imageFolderPath) throws IOException {
        if (StateFile.exists(imageFolderPath, StateFile.Type.PRIORITIZED)) {
            BasicFileAttributeView attributes = Files.getFileAttributeView(imageFolderPath.resolve(StateFile.Type.PRIORITIZED.fileName()), BasicFileAttributeView.class);
            FileTime fileTime = FileTime.fromMillis((new Date()).getTime());
            attributes.setTimes(fileTime, fileTime, fileTime);
        } else {
            StateFile.create(imageFolderPath, StateFile.Type.PRIORITIZED);
        }
    }

    /**
     * Attempts to delete the output folder for a case.
     *
     * @param caseOutputFolderPath The case output folder path.
     *
     * @parame deleteInput Flag to delete images used as input for the case.
     * @return CaseDeletionResult structure containing deletion status.
     */
    /**
     *
     * @param caseFolderPath
     * @param physicallyDeleteImageFolders
     * @param caseMetadataFilePath
     *
     * @return
     */
    CaseDeletionResult deleteCase(Path caseFolderPath, boolean physicallyDeleteImageFolders, String caseMetadataFilePath) {
        String caseName = PathUtils.caseNameFromCaseFolderPath(caseFolderPath);
        Path caseImageFolderPath = rootImageFolderPath.resolve(caseName);
        if (state != State.RUNNING) {
            return new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.FAILED);
        }

        Lock caseFolderLock = null;
        List<Lock> imageFolderLocks = new ArrayList<>();
        try {
            /**
             * Start by locking the job lists monitor. This blocks image folder
             * scans during the delete operation and makes it possible to remove
             * auto ingest jobs from the job lists.
             */
            CaseDeletionResult result;
            synchronized (jobListsMonitor) {
                /*
                 * Acquire an exclusive lock on the case folder so it can be
                 * safely deleted. This will fail if the case is open for review
                 * or a deletion operation on this case is already in progress.
                 */
                caseFolderLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseFolderPath.toString());
                if (null == caseFolderLock) {
                    return new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.FAILED);
                }

                /*
                 * Do a fresh jobs scan, because it is possible that the image
                 * copier added image folders in the gap between the last scan
                 * and the acquisition of the case image folders lock.
                 */
                ImageFoldersScanner scanner = new ImageFoldersScanner();
                scanner.scan();

                /*
                 * Acquire exclusive locks on all of the image folders for the
                 * case so that they can be safely deleted.
                 */
                if (!acquireAllExclusiveImageFolderLocks(caseName, pendingJobs, imageFolderLocks)) {
                    return new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.FAILED);
                }
                if (!acquireAllExclusiveImageFolderLocks(caseName, completedJobs, imageFolderLocks)) {
                    return new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.FAILED);
                }

                /*
                 * Try to unload/delete the Solr core from the Solr server.
                 */
                CaseMetadata metaData = new CaseMetadata(caseFolderPath.resolve(caseMetadataFilePath));
                unloadSolrCore(caseFolderPath, metaData.getTextIndexName());

                /*
                 * Delete the case folder, which includes the Solr index files.
                 * If the case folder cannot be physically deleted, then
                 * logically delete it by writing a DELETED state file into it.
                 *
                 * NOTE: The case folder is deleted before the image folders,
                 * because if crash occurred, it would be worse to have a case
                 * missing images than it would be to have some orphaned image
                 * folders, which should get logically deleted by the crash
                 * recovery code.
                 */
                try {
                    if (!FileUtil.deleteDir(caseFolderPath.toFile()) && caseFolderPath.toFile().exists()) {
                        StateFile.create(caseFolderPath, DELETED);
                    }
                } catch (IOException | SecurityException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to either physically or logically cannot delete %s", caseFolderPath), ex);
                    return new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.FAILED);
                }

                /*
                 * Now that the case folder is either physically or logically
                 * deleted, delete the case database from the database server if
                 * the case is a multi-user case (if the case is a legacy
                 * single-user case, the case database was already deleted with
                 * the case folder).
                 */
                if (metaData.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
                    deleteCaseDatabase(caseFolderPath, metaData.getCaseDatabaseName());
                }

                /*
                 * Finish by deleting the jobs for this case from the job lists
                 * and deleting the image folders associated with the jobs.
                 */
                CaseDeletionResult.Status pendingJobsResult = deleteJobsForCase(caseName, pendingJobs, physicallyDeleteImageFolders);
                CaseDeletionResult.Status completedJobsResult = deleteJobsForCase(caseName, completedJobs, physicallyDeleteImageFolders);

                if (CaseDeletionResult.Status.COMPLETED == pendingJobsResult
                        && CaseDeletionResult.Status.COMPLETED == completedJobsResult) {
                    if (physicallyDeleteImageFolders) {
                        if (FileUtil.deleteDir(caseImageFolderPath.toFile())) {
                            result = new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.COMPLETED);
                        } else {
                            result = new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.PARTIALLY_COMPLETED);
                        }
                    } else {
                        result = new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.COMPLETED);
                    }
                } else {
                    result = new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.PARTIALLY_COMPLETED);
                }
            }
            /*
             * Unlock the job lists monitor and then publish a case deleted
             * event.
             */
            publishCaseDeletedEvent(result);
            return result;

        } catch (CoordinationServiceException ex) {
            logger.log(Level.SEVERE, "Unable to get a lock on the case. Unable to delete.", ex);
            return new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.FAILED);

        } catch (CaseMetadata.CaseMetadataException ex) {
            logger.log(Level.SEVERE, String.format("Error accessing case metadata for %s", caseFolderPath), ex);
            return new CaseDeletionResult(caseName, caseFolderPath.toString(), caseImageFolderPath.toString(), CaseDeletionResult.Status.FAILED);

        } finally {
            for (Lock lock : imageFolderLocks) {
                releaseCoordinationServiceLockNoThrow(lock);
            }
            releaseCoordinationServiceLockNoThrow(caseFolderLock);
        }
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
    boolean acquireAllExclusiveImageFolderLocks(String caseName, List<AutoIngestJob> jobs, List<Lock> locks) {
        for (AutoIngestJob job : jobs) {
            if (job.getCaseName().equals(caseName)) {
                Path imageFolderPath = job.getImageFolderPath();
                try {
                    Lock lock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString());
                    if (null != lock) {
                        locks.add(lock);
                    } else {
                        return false;
                    }
                } catch (CoordinationServiceException ex) {
                    logger.log(Level.SEVERE, String.format("Coordination service error while trying to acquire exclusive lock on %s", imageFolderPath), ex);
                    return false;
                }
            }
        }
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
            logger.log(Level.WARNING, String.format("Error unloading/deleting Solr core for %s: %s", caseFolderPath, ex.getMessage())); //NON-NLS
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
                logger.log(Level.WARNING, String.format("Unable to delete case database for %s : %s", caseFolderPath, ex.getMessage())); //NON-NLS
            }
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, String.format("Error accessing case database connection info, unable to delete case database for %s", caseFolderPath), ex); //NON-NLS
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, String.format("Cannot load database driver, unable to delete case database for %s", caseFolderPath), ex); //NON-NLS
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
    private CaseDeletionResult.Status deleteJobsForCase(String caseName, List<AutoIngestJob> jobs, boolean physicallyDeleteFolders) {
        CaseDeletionResult.Status result = CaseDeletionResult.Status.COMPLETED;
        for (Iterator<AutoIngestJob> iterator = jobs.iterator(); iterator.hasNext();) {
            AutoIngestJob job = iterator.next();
            if (job.getCaseName().equals(caseName)) {
                Path imageFolderPath = job.getImageFolderPath();
                try {
                    if (physicallyDeleteFolders) {
                        if (!FileUtil.deleteDir(imageFolderPath.toFile()) && imageFolderPath.toFile().exists()) {
                            /*
                             * Fall back to logical deletion.
                             */
                            StateFile.create(imageFolderPath, DELETED);
                            result = CaseDeletionResult.Status.PARTIALLY_COMPLETED;
                        }
                    } else {
                        /*
                         * Do logical deletion, as requested.
                         */
                        StateFile.create(imageFolderPath, DELETED);
                    }
                    iterator.remove();
                } catch (IOException | SecurityException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to write DELETED state file to %s", imageFolderPath), ex);
                    result = CaseDeletionResult.Status.PARTIALLY_COMPLETED;
                }
            }
        }
        return result;
    }

    /**
     * Cancels the current job and returns the current running jobs list.
     *
     * @return The contents of the running jobs list.
     */
    List<AutoIngestJob> cancelCurrentJob() {
        if (State.RUNNING != state && State.SHUTTING_DOWN != state) {
            return Collections.emptyList();
        }
        synchronized (jobListsMonitor) {
            if (null != currentJob) {
                logger.log(Level.INFO, "Cancelling auto ingest for {0}", currentJob.getImageFolderPath());
                IngestJob ingestJob = currentJob.getIngestStatus().setStatusCancelled();
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
     * @return The contents of the running jobs list.
     */
    List<AutoIngestJob> cancelCurrentDataSourceLevelIngestModule() {
        if (State.RUNNING != state) {
            return Collections.emptyList();
        }
        synchronized (jobListsMonitor) {
            if (null != currentJob) {
                IngestJob ingestJob = currentJob.getIngestStatus().cancelModule();
                IngestJob.DataSourceIngestModuleHandle moduleHandle = ingestJob.getSnapshot().runningDataSourceIngestModule();
                if (null != moduleHandle) {
                    moduleHandle.cancel();
                    logger.log(Level.INFO, "Cancelling {0} for {1}", new Object[]{moduleHandle.displayName(), currentJob.getImageFolderPath()});
                }
            }
            List<AutoIngestJob> runningJobs = new ArrayList<>();
            getJobs(null, runningJobs, null);
            return runningJobs;
        }
    }

    /**
     * Tests whether auto ingest job is part of multi-user case.
     *
     * @param job Auto ingest job of interest.
     *
     * @return True if the job is part of multi-user case, false otherwise.
     */
    private boolean isMultiUserJob(AutoIngestJob job) {
        try {
            Path autFilePath = Paths.get(job.getCaseFolderPath().toString(), job.getCaseName() + CaseMetadata.getFileExtension());
            // if AUT file exists (i.e. existing case), check case type
            if (autFilePath.toFile().exists()) {
                CaseMetadata caseMetadata = new CaseMetadata(autFilePath);
                if (CaseType.SINGLE_USER_CASE == caseMetadata.getCaseType()) {
                    // single user case
                    return false;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Problem checking case type", ex);
            return false;
        }

        // this is a multi-user case (new or existing)
        return true;
    }

    private void writeCancelledStateFiles(AutoIngestJob job) {
        writeStateFiles(job, CANCELLED);
    }

    private void writeErrorStateFiles(AutoIngestJob job) {
        writeStateFiles(job, ERROR);
    }

    private void writeInterruptedStateFiles(AutoIngestJob job) {
        writeStateFiles(job, INTERRUPTED);
    }

    private void writeStateFiles(AutoIngestJob job, StateFile.Type type) {
        synchronized (jobListsMonitor) {
            Path imageFolderPath = job.getImageFolderPath();
            if (imageFolderPath.toFile().exists()) {
                try {
                    StateFile.createIfDoesNotExist(imageFolderPath, type);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, String.format("Failed to create %s state file in %s", type.fileName(), imageFolderPath), ex);
                }
            }
            Path caseFolderPath = job.getCaseFolderPath();
            if (caseFolderPath.toFile().exists()) {
                try {
                    StateFile.createIfDoesNotExist(caseFolderPath, type);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, String.format("Failed to create %s state file in %s", type.fileName(), caseFolderPath), ex);
                }
            }
        }
    }

    /**
     * Releases a coordination service lock.
     *
     * @throws CoordinationServiceException If the release fails.
     */
    private void releaseCoordinationServiceLock(Lock lock) throws CoordinationServiceException {
        if (null != lock) {
            lock.release();
        }
    }

    /**
     * Tries to release a coordination service lock. Logs the exception if it
     * fails, but does not throw.
     */
    private void releaseCoordinationServiceLockNoThrow(Lock lock) {
        try {
            releaseCoordinationServiceLock(lock);
        } catch (CoordinationServiceException ex) {
            logger.log(Level.SEVERE, String.format("Error releasing coordination service lock", lock.getNodePath()), ex);
        }
    }

    /**
     * Instances of this task periodically submit auto ingest jobs scan tasks to
     * a task completion service. The scans produce auto ingest jobs that are
     * consumed by a job processing task that blocks on the task completion
     * service.
     */
    private final class ImageFoldersScanSchedulingTask implements Runnable {

        @Override
        public void run() {
            try {
                scanImageFoldersNow();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unexpected exception in ImageFoldersScanSchedulingTask", ex); //NON-NLS
            }
        }
    }

    /**
     * Instances of this task search refresh the pending jobs queue and the
     * completed jobs list.
     */
    private final class ImageFoldersScanTask implements Callable<Void> {

        /**
         * @inheritDoc
         */
        @Override
        public Void call() throws Exception {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            synchronized (jobListsMonitor) {
                ImageFoldersScanner scanner = new ImageFoldersScanner();
                scanner.scan();

                /*
                 * TODO (RC): It would be better if we notified that a
                 * JOBS_REFRESHED event happened. As it is, this code makes
                 * assumptions about how clients react to various notifications,
                 * so it is fragile and defeats the MVC pattern.
                 */
                setChanged();
                notifyObservers(Event.JOB_COMPLETED);
            }
            return null;
        }

    }

    /**
     * An image folders scanner searches the root images folder for image
     * folders associated with ready-to-process, completed, and crashed auto
     * ingest jobs and uses the search results to refresh the pending jobs queue
     * and the completed jobs list.
     */
    private final class ImageFoldersScanner implements FileVisitor<Path> {

        private final List<AutoIngestJob> newPendingJobsList;
        private final List<AutoIngestJob> newCompletedJobsList;

        private ImageFoldersScanner() {
            newPendingJobsList = new ArrayList<>();
            newCompletedJobsList = new ArrayList<>();
        }

        /*
         * Searches the root images folder for image folders associated with
         * ready-to-process, completed, and crashed auto ingest jobs and uses
         * the search results to refresh the pending jobs queue and the
         * completed jobs list.
         */
        private void scan() {
            synchronized (jobListsMonitor) {
                try {
                    /*
                     * Walk the image folders file tree and create lists of
                     * pending and completed jobs based on the state files in
                     * the image folders.
                     */
                    newPendingJobsList.clear();
                    newCompletedJobsList.clear();
                    Files.walkFileTree(rootImageFolderPath, EnumSet.of(FOLLOW_LINKS), Integer.MAX_VALUE, this);

                    /*
                     * Sort the new pending jobs queue by priority and use it to
                     * replace the old queue.
                     */
                    Collections.sort(newPendingJobsList, prioritizedPendingListComparator);
                    AutoIngestManager.this.pendingJobs = newPendingJobsList;

                    AutoIngestManager.this.completedJobs = newCompletedJobsList;

                } catch (IOException ex) {
                    logger.log(Level.SEVERE, String.format("Error scanning the %s file tree", rootImageFolderPath), ex);
                }
            }

        }

        /**
         * @inheritDoc
         */
        @Override
        public FileVisitResult preVisitDirectory(Path imageFolderPath, BasicFileAttributes folderAttrs) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }
            return CONTINUE;
        }

        /**
         * @inheritDoc
         */
        @Override
        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }
            return CONTINUE;
        }

        /**
         * @inheritDoc
         */
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException ex) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }
            logger.log(Level.WARNING, String.format("Error while visiting %s during image folder scan", file.toString()), ex);
            return CONTINUE;
        }

        /**
         * @inheritDoc
         */
        @Override
        public FileVisitResult postVisitDirectory(Path imageFolderPath, IOException unused) throws IOException {
            /*
             * Check for cancellation of the thread running the scan. The scan
             * task threads are cancelled when auto ingest is shutting down.
             */
            if (Thread.currentThread().isInterrupted()) {
                return TERMINATE;
            }

            /*
             * Ignore the system root image folder and all logically deleted
             * folders, i.e., folders with a DELETED state file that was written
             * when a user wanted to delete the folder but physical deletion
             * failed.
             */
            if (imageFolderPath == rootImageFolderPath || StateFile.exists(imageFolderPath, DELETED)) {
                return CONTINUE;
            }

            /*
             * Ignore image folders that do not contain any of the state files
             * that are used to keep track of job state transitions during
             * processing: READY, PROCESSING, DONE.
             */
            if ((StateFile.exists(imageFolderPath, READY) == false)
                    && (StateFile.exists(imageFolderPath, PROCESSING) == false)
                    && (StateFile.exists(imageFolderPath, DONE) == false)) {
                return CONTINUE;
            }

            /*
             * Try to acquire a shared lock on the image folder. If the lock
             * cannot be acquired, then the folder is either being processed or
             * deleted, and the folder does not belong in either the pending
             * jobs queue or the completed jobs list, so it can be skipped.
             */
            Lock imageFolderLock = null;
            try {
                imageFolderLock = coordinationService.tryGetSharedLock(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString());
                if (null != imageFolderLock) {
                    /*
                     * The shared look on the input folder has been acquired, so
                     * look for various meaningful combinations of state files.
                     * This inspection can be done with a shared lock because
                     * state files are only created or deleted in an image
                     * folder while holding an exclusive lock on the folder.
                     * This is important because it allows more than one auto
                     * ingest node (AIN) at a time to scan the image folders.
                     */
                    if ((StateFile.exists(imageFolderPath, PROCESSING) == true)) {
                        /*
                         * If the folder has a PROCESSING file and it was
                         * possible to acquire a shared lock for it, then the
                         * auto ingest node (AIN) that was processing the folder
                         * crashed. Try upgrading the lock on the image folder
                         * to an exclusive lock. Failure to do so indicates
                         * another node has already started the crash recovery
                         * in the gap between releasing the shared lock and
                         * acquiring the exclusive lock, in which case the
                         * folder should be skipped this time around.
                         */
                        imageFolderLock.release();
                        imageFolderLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString());
                        if (null != imageFolderLock) {
                            /*
                             * The PROCESSING file check must be repeated here
                             * because there was a gap between releasing the
                             * shared lock and acquiring the exclusive lock.
                             */
                            if (StateFile.exists(imageFolderPath, PROCESSING) == true) {
                                if (StateFile.exists(imageFolderPath, READY) == true) {
                                    /*
                                     * The crash happened after the READY file
                                     * was written, but before the PROCESSING
                                     * file was deleted. Ingest was not started.
                                     * Leave the READY file, and add the folder
                                     * to the pending jobs queue. ERROR files do
                                     * not need to be created.
                                     */
                                    logger.log(Level.INFO, "Found crashed auto ingest job for {0}, ingest not started, re-queueing", imageFolderPath);
                                    newPendingJobsList.add(createAutoIngestJob(imageFolderPath));

                                } else if (StateFile.exists(imageFolderPath, DONE) == true) {
                                    /*
                                     * The crash happened after the DONE file
                                     * was written, but before the PROCESSING
                                     * file was deleted. Add the folder to the
                                     * completed jobs list, because the job was
                                     * already done when the node crashed. ERROR
                                     * files do not need to be created (but they
                                     * may already be there).
                                     */
                                    logger.log(Level.INFO, "Found crashed but completed auto ingest job for {0}, cleaning up", imageFolderPath);
                                    Path caseFolderFolderPath = PathUtils.findCaseFolder(rootCaseFolderPath, PathUtils.caseNameFromImageFolderPath(rootImageFolderPath, imageFolderPath));
                                    if (null != caseFolderFolderPath) {
                                        AutoIngestJob job = createAutoIngestJob(imageFolderPath);
                                        job.setCaseFolderPath(caseFolderFolderPath);
                                        newCompletedJobsList.add(job);
                                    } else {
                                        /*
                                         * There is no case folder. Something is
                                         * surprisingly amiss here, probably
                                         * manual deletion of the case folder.
                                         * Logically delete the folder.
                                         */
                                        imageFolderLock.release();
                                        imageFolderLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString());
                                        if (null != imageFolderLock) {
                                            logger.log(Level.SEVERE, "Found crashed but completed auto ingest job for {0} with missing case folder, logically deleting image folder", imageFolderPath);
                                            StateFile.createIfDoesNotExist(imageFolderPath, DELETED);
                                        }
                                    }

                                } else {
                                    /*
                                     * The crash happened while processing was
                                     * underway but incomplete, so processing
                                     * needs to be restarted. There are two
                                     * cases to handle: a crash before or after
                                     * the case was created.
                                     */
                                    logger.log(Level.INFO, "Found crashed auto ingest job for {0}, ingest started, reprocessing", imageFolderPath);
                                    AutoIngestJob job = createAutoIngestJob(imageFolderPath);
                                    Path caseFolderPath = PathUtils.findCaseFolder(rootCaseFolderPath, PathUtils.caseNameFromImageFolderPath(rootImageFolderPath, imageFolderPath));
                                    if (null != caseFolderPath) {
                                        /*
                                         * Lay down ERROR state files and decide
                                         * whether or not to try reprocessing
                                         * the folder.
                                         */
                                        writeErrorStateFiles(job);
                                        try {
                                            int numberOfProcessingAttempts = incrementAndGetProcessingAttempts(imageFolderPath);
                                            if (numberOfProcessingAttempts <= VikingUserPreferences.getMaxNumTimesToProcessImage()) {
                                                StateFile.create(imageFolderPath, READY);
                                                newPendingJobsList.add(job);
                                                new AutoIngestJobLogger(imageFolderPath, caseFolderPath).logCrashRecoveryWithRetry();
                                            } else {
                                                /*
                                                 * Processing of some image in
                                                 * the folder appears to be
                                                 * causing crashes.
                                                 */
                                                logger.log(Level.WARNING, "Maximum number of retry attempts reached for auto ingest job for {0}", imageFolderPath);
                                                StateFile.create(imageFolderPath, DONE);
                                                newCompletedJobsList.add(job);
                                                new AutoIngestJobLogger(imageFolderPath, caseFolderPath).logCrashRecoveryNoRetry();
                                            }
                                        } catch (CoordinationServiceException | IOException | SecurityException logEx) {
                                            logger.log(Level.SEVERE, String.format("Error writing to case auto ingest log in %s when processing %s", caseFolderPath, imageFolderPath), logEx);
                                        }

                                    } else {
                                        /*
                                         * The crash happened before the case
                                         * was created, so a simple "do-over" is
                                         * all that is required.
                                         */
                                        imageFolderLock.release();
                                        imageFolderLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString());
                                        if (null != imageFolderLock) {
                                            StateFile.create(imageFolderPath, READY);
                                            newCompletedJobsList.add(job);
                                        }
                                    }
                                }

                                /*
                                 * Delete the PROCESSING FILE as the last step,
                                 * in case there is a crash during crash
                                 * recovery.
                                 */
                                StateFile.delete(imageFolderPath, PROCESSING);
                            }

                        }
                    } else if (StateFile.exists(imageFolderPath, READY) == true) {
                        /*
                         * If the folder has a READY file and no PROCESSING
                         * file, then add it to the pending jobs queue.
                         */
                        newPendingJobsList.add(createAutoIngestJob(imageFolderPath));

                    } else if (StateFile.exists(imageFolderPath, DONE) == true) {
                        /*
                         * If the folder has a DONE file and no processing file,
                         * then add it to the completed jobs list.
                         */
                        Path caseFolderPath = PathUtils.findCaseFolder(rootCaseFolderPath, PathUtils.caseNameFromImageFolderPath(rootImageFolderPath, imageFolderPath));
                        if (null != caseFolderPath) {
                            AutoIngestJob job = createAutoIngestJob(imageFolderPath);
                            job.setCaseFolderPath(caseFolderPath);
                            newCompletedJobsList.add(job);
                        } else {
                            /*
                             * There is no case folder. Something is
                             * surprisingly amiss here, given that DONE files
                             * are only created after the case is
                             * created/opened. Perhaps a crash occurred during
                             * case deletion, or manual deletion of the case
                             * folder has been done for some reason. Logically
                             * delete the folder.
                             */
                            imageFolderLock.release();
                            imageFolderLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString());
                            if (null != imageFolderLock) {
                                logger.log(Level.WARNING, "Found completed auto ingest job {0} with missing case folder, logically deleting image folder", imageFolderPath);
                                StateFile.createIfDoesNotExist(imageFolderPath, DELETED);
                            }
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
                logger.log(Level.SEVERE, String.format("Error attempting to get or release coordination service lock on image folder %s", imageFolderPath), ex);
            } catch (IOException | SecurityException ex) {
                logger.log(Level.SEVERE, String.format("Error attempting to read/write/delete a state file in image folder %s", imageFolderPath), ex);
            } finally {
                if (null != imageFolderLock) {
                    releaseCoordinationServiceLockNoThrow(imageFolderLock);
                }
            }
            return CONTINUE;
        }

        /**
         * Increments and returns the number of processing attempts for an image
         * folder.
         *
         * @param imageFolderPath Path to the image folder
         *
         * @return The number of processing attempts.
         *
         * @throws InterruptedException
         * @throws CoordinationServiceException
         */
        private int incrementAndGetProcessingAttempts(Path imageFolderPath) throws InterruptedException, CoordinationServiceException {
            int numberOfProcessingAttempts = 0;
            // First get the number of attempts so far, if any.
            ByteBuffer nodeData = ByteBuffer.wrap(coordinationService.getNodeData(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString()));
            if (nodeData.hasRemaining()) {
                numberOfProcessingAttempts = nodeData.getInt();
            } else {
                // There was no data associated with the image folder node so
                // we allocate a buffer to hold an integer.
                nodeData = ByteBuffer.allocate(Integer.BYTES);
            }

            // Update the node with the new value.
            nodeData.clear();
            ++numberOfProcessingAttempts;
            nodeData.putInt(numberOfProcessingAttempts);
            coordinationService.setNodeData(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString(), nodeData.array());
            return numberOfProcessingAttempts;
        }

        /**
         * Creates an auto ingest job for an input image folder.
         *
         * @param inputFolderPath The input image folder path.
         *
         * @return The auto ingest job.
         */
        private AutoIngestJob createAutoIngestJob(Path inputFolderPath) {
            /*
             * TODO (RC): The AutoIngestJob constructor sets the completed date
             * to just before the Posix epoch if no case folder argument is
             * supplied. The assumption here is that the case folder path will
             * be set with a setter before putting this job into the completed
             * list. This is fragile, given that this job will be handed out to
             * clients. The clients must know what fields are valid for a job
             * based on the list that contains it (VIK-1356).
             */
            String caseName = PathUtils.caseNameFromImageFolderPath(rootImageFolderPath, inputFolderPath);
            Date completedDate = new Date(inputFolderPath.resolve(DONE.fileName()).toFile().lastModified());
            return new AutoIngestJob(inputFolderPath, caseName, Paths.get(""), new JobIngestStatus(completedDate), localHostName);
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
                synchronized (jobListsMonitor) {
                    if (currentJob == null || !isMultiUserJob(currentJob)) {
                        return;
                    }
                    // notify remote AIM nodes about status of current job
                    publishJobStatusEvent(currentJob);
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
                        logger.log(Level.WARNING, "Auto ingest node {0} timed out while processing folder {1}",
                                new Object[]{job.getNodeName(), job.getImageFolderPath().toString()});
                        hostNamesToRunningJobs.remove(job.getNodeName());
                        setChanged();
                        notifyObservers(Event.JOB_COMPLETED);
                    }
                }

            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unexpected exception in PeriodicJobStatusEventTask", ex); //NON-NLS
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
     * An instance of this task is used by the auto ingest manager to process
     * auto ingest jobs. The task thread blocks on a completion service for
     * image folder scan tasks, i.e., it processes queued auto ingest jobs each
     * time a periodic folder scan completes, working through all of the jobs
     * queued up by the scan. The task can be paused (but only between jobs) and
     * resumed by clients, and will pause itself if fatal errors occur.
     */
    private final class AutoIngestTask implements Runnable {

        /**
         * A monitor used by the task thread to wait while ingest threads
         * process the data sources for a job.
         */
        private final Object ingestInProgressMonitor;

        /**
         * A monitor used to serialize access to paused state by client threads
         * and the task thread, and to make the task thread pause until it is
         * resumed by a client thread.
         */
        private final Object pauseAndResumeMonitor;

        /*
         * A flag that is set when a client requests suspension of auto ingest
         * job processing. The flag is unset when a client requests processing
         * be resumed.
         */
        private boolean pauseRequestedByClient;

        /**
         * A flag needed for the special case where a client makes a pause
         * request while the task thread is waiting for an image folder scan to
         * complete. The task thread is blocked, so it cannot act on the
         * request, including notifying clients, until the next scan completes,
         * at which time it will check the pause requested by client flag. So
         * this flag tells the pause request code that it should go ahead and
         * notify any observers that processing is, in effect, already paused.
         * This also holds true for resuming.
         */
        private boolean waitingForImageFoldersScan;

        /*
         * Coordination service lock on the image folder for the current job.
         * This is an exclusive lock that prevents other auto ingest nodes from
         * inspecting, processing, or deleting the folder. The lock is
         * ephemeral, so it is released if this node crashes.
         */
        private Lock currentImageFolderLock;

        /*
         * Manifest XML file must be present for each data source and has the
         * following format: <Manifest> <Collection> <Image> <ID/> <!-- This is
         * the field we need to read, empty in this sample --> </Image>
         * </Collection> </Manifest>
         */
        private static final String IMAGE_ID_PATH_IN_MANIFEST_XML = "/Manifest/Collection/Image/ID/text()";

        /*
         * Manifest file has same name as the data source (excluding extension)
         * followed by "_Manifest.xml". For example, hd01.e01 data source must
         * have hd01_Manifest.xml manifest file
         */
        private static final String MANIFEST_FILE_NAME_SIGNATURE = "_Manifest.xml";

        // Folder name inside "ModuleOutput" folder where contents of Cellebrite ZIP archives will be extracted to.
        private static final String AIM_MODULE_OUTPUT_FOLDER_NAME = "AutoIngestManager";

        /**
         * Constructs a task used by the auto ingest manager to process auto
         * ingest jobs.
         */
        private AutoIngestTask() {
            pauseAndResumeMonitor = new Object();
            ingestInProgressMonitor = new Object();
        }

        /**
         * @inheritDoc
         */
        @Override
        public void run() {
            /*
             * Processes auto ingest jobs until this task is cancelled. The
             * nested try blocks within the loop support cancelling and pausing.
             * The outer try catches the InterruptedException that will be
             * thrown if the task is cancelled while the task thread is blocked.
             * The inner try block catches PauseRequiredException and goes into
             * a wait on the pause/resume monitor, without exiting the loop.
             */
            while (true) {
                try {
                    try {
                        /*
                         * Block until an image folder scan has completed, with
                         * client pause request checks before and after. The
                         * waitingForImageFoldersScan flag is set before
                         * starting the wait so that if a client pause or resume
                         * request comes in while this thread is blocked, the
                         * observers can be notified that the request has been
                         * satisfied immediately, instead of waiting for the
                         * next scan to run and complete. This works because
                         * there is a pause state check after the wait.
                         */
                        synchronized (pauseAndResumeMonitor) {
                            if (pauseRequestedByClient) {
                                throw new PauseRequiredException("Auto ingest pause requested by client", Event.PAUSED_BY_REQUEST);
                            }
                            waitingForImageFoldersScan = true;
                        }
                        imageFolderScanCompletionService.take();
                        synchronized (pauseAndResumeMonitor) {
                            waitingForImageFoldersScan = false;
                            if (pauseRequestedByClient) {
                                throw new PauseRequiredException("Auto ingest pause requested by client", Event.PAUSED_BY_REQUEST);
                            }
                        }

                        /*
                         * Process jobs until the pending jobs list is empty, a
                         * client requests a pause, or this task is cancelled.
                         */
                        while (startNextJob(false)) {
                            completeJob();
                            /*
                             * Between jobs, check for task cancellation and
                             * client pause requests.
                             */
                            if (autoIngestTaskFuture.isCancelled()) {
                                return;
                            }
                            synchronized (pauseAndResumeMonitor) {
                                if (pauseRequestedByClient) {
                                    throw new PauseRequiredException("Auto ingest pause requested by client", Event.PAUSED_BY_REQUEST);
                                }
                            }
                        }

                    } catch (PauseRequiredException ex) {
                        /*
                         * Block (pause) until either the monitor is notified in
                         * requestResume() or the thread running this task is
                         * interrupted.
                         */
                        if (ex.getEvent() != Event.PAUSED_BY_REQUEST) {
                            logger.log(Level.SEVERE, "Error requiring pause occurred during auto ingest", ex);
                        }
                        synchronized (pauseAndResumeMonitor) {
                            if (Event.PAUSED_BY_REQUEST == ex.getEvent()) {
                                logger.log(Level.INFO, "Pausing auto ingest by client request");
                                pauseRequestedByClient = false;
                            } else {
                                logger.log(Level.SEVERE, "Pausing auto ingest for error condition", ex);
                            }
                            setChanged();
                            notifyObservers(ex.getEvent());
                            pauseAndResumeMonitor.wait();
                            logger.log(Level.INFO, "Resuming auto ingest");
                            setChanged();
                            notifyObservers(Event.RESUMED);
                        }
                    }
                } catch (InterruptedException unused) {
                    return;
                }
            }
        }

        /**
         * Asks this task to suspend processing of auto ingest jobs. The request
         * will not be serviced immediately if the task thread is doing a job.
         *
         * @param requestedStatus The requested pause status.
         */
        private void requestPause() {
            synchronized (pauseAndResumeMonitor) {
                pauseRequestedByClient = true;
                if (waitingForImageFoldersScan) {
                    /*
                     * The task thread is blocked waiting for an image folder
                     * scan, so report an immediate pause. This works because as
                     * soon as the thread stops waiting, the job processing loop
                     * in the run() method will check the pause requested flag
                     * and behave as it should.
                     */
                    setChanged();
                    notifyObservers(Event.PAUSED_BY_REQUEST);
                }
            }
        }

        /**
         * Asks this task to resume processing auto ingest jobs.
         */
        private void requestResume() {
            synchronized (pauseAndResumeMonitor) {
                pauseRequestedByClient = false;
                if (waitingForImageFoldersScan) {
                    /*
                     * The task thread is blocked waiting for an image folder
                     * scan, so report an immediate pause. This works because as
                     * soon as the thread stops waiting, the job processing loop
                     * in the run() method will check the pause requested flag
                     * and behave as it should.
                     */
                    setChanged();
                    notifyObservers(Event.RESUMED);
                }
                pauseAndResumeMonitor.notifyAll();
            }
        }

        /**
         * Inspects the pending jobs queue, looking for the next job that is
         * ready to be done. If such a job is found, it is started.
         *
         * @param useMaxConcurrentJobsForCase If true, the first available job
         *                                    found will be taken regardless of
         *                                    the maximum jobs per case limit.
         *                                    If false, it will pay attention to
         *                                    the maximum jobs limit
         *
         * @return True if a job was started, false otherwise.
         *
         * @throws InterruptedException   If the thread running this task is
         *                                interrupted while blocked, i.e., if
         *                                auto ingest is shutting down.
         * @throws PauseRequiredException If there is some system error
         *                                condition that requires suspension of
         *                                auto ingest until the problem is
         *                                fixed.
         */
        private boolean startNextJob(boolean useMaxConcurrentJobsForCase) throws InterruptedException, PauseRequiredException {
            try {
                synchronized (jobListsMonitor) {
                    currentJob = null;
                    Iterator<AutoIngestJob> iterator = pendingJobs.iterator();
                    while (iterator.hasNext()) {
                        AutoIngestJob job = iterator.next();
                        Path imageFolderPath = job.getImageFolderPath();

                        /*
                         * Acquire an exclusive lock on the image folder, to be
                         * held until processing for this job is done. If the
                         * lock cannot be acquired, it is already being
                         * inspected, processed, or deleted. Skip the job for
                         * now. It will remain in the queue until a proper
                         * disposition is achieved.
                         */
                        currentImageFolderLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.IMAGES, imageFolderPath.toString());
                        if (null == currentImageFolderLock) {
                            continue;
                        }

                        /*
                         * Make sure the image folder still exists, since it is
                         * possible that is was physically deleted it in the
                         * interval between when this job was added to the queue
                         * and the exclusive lock was acquired.
                         */
                        if (!imageFolderPath.toFile().exists()) {
                            releaseCoordinationServiceLock(currentImageFolderLock);
                            currentImageFolderLock = null;
                            continue;
                        }

                        /*
                         * Make sure there is no DELETED state file in the image
                         * folder, since it is possible that the folder was
                         * logically deleted (a DELETED file indicates either
                         * physical deletion failed or a user chose not to
                         * physically delete the folder) in the interval between
                         * when this job was added to the queue and the
                         * exclusive lock was acquired.
                         */
                        if (StateFile.exists(imageFolderPath, DELETED)) {
                            releaseCoordinationServiceLock(currentImageFolderLock);
                            currentImageFolderLock = null;
                            continue;
                        }

                        /*
                         * Verify that there is still a READY state file in the
                         * image folder, since it is possible that another auto
                         * ingest node did the job and released the image folder
                         * lock in the interval between when this job was added
                         * to the queue and the exclusive lock was acquired.
                         * Note that ERROR, CANCELLED, and INTERRUPTED files are
                         * ignored by this check to allow re-processing during
                         * automated crash recovery or because a user dropped in
                         * a new READY file. Of course, there is still the hard
                         * limit on retries imposed by maxJobRetries.
                         */
                        if (!StateFile.exists(imageFolderPath, READY)
                                || StateFile.exists(imageFolderPath, PROCESSING)
                                || StateFile.exists(imageFolderPath, DONE)) {
                            releaseCoordinationServiceLock(currentImageFolderLock);
                            currentImageFolderLock = null;
                            continue;
                        }

                        // check if we can process this because of max jobs per case
                        if (useMaxConcurrentJobsForCase == false) {
                            int numberOfJobsForCurrentCaseAlreadyInProgress = 0;
                            for (AutoIngestJob runningJobs : hostNamesToRunningJobs.values()) {
                                if (0 == job.getCaseName().compareTo(runningJobs.getCaseName())) {
                                    ++numberOfJobsForCurrentCaseAlreadyInProgress;
                                }
                            }

                            if (numberOfJobsForCurrentCaseAlreadyInProgress >= VikingUserPreferences.getMaxConcurrentJobsForOneCase()) {
                                releaseCoordinationServiceLock(currentImageFolderLock);
                                currentImageFolderLock = null;
                                continue;
                            }
                        }

                        /*
                         * The decks are verified to be cleared for action, so
                         * write the PROCESSING state file and delete the READY
                         * state file. Do the write before the delete so that if
                         * a crash occurs in the interval between the two file
                         * operations, the presence of the two state files will
                         * signal the need for automated crash recovery for this
                         * job.
                         */
                        StateFile.create(job.getImageFolderPath(), PROCESSING);
                        StateFile.delete(job.getImageFolderPath(), READY);

                        /*
                         * Start the job by removing it from the pending queue,
                         * making it the current job, and publishing a job
                         * started event, both locally and to other auto ingest
                         * nodes.
                         */
                        iterator.remove();
                        currentJob = job;
                        currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.STARTING);
                        logger.log(Level.INFO, "Started processing of {0}", imageFolderPath);
                        publishJobStartedEvent(currentJob);

                        return true;
                    }
                }
            } catch (CoordinationServiceException ex) {
                /*
                 * If there is a problem with the coordination service, the
                 * system cannot be guaranteed to function correctly. Pause auto
                 * ingest and wait for human intervention.
                 */
                if (null != currentImageFolderLock) {
                    releaseCoordinationServiceLockNoThrow(currentImageFolderLock);
                    currentImageFolderLock = null;
                }
                throw new PauseRequiredException("Coordination service error", ex, Event.PAUSED_COORDINATION_SERVICE_DOWN);
            } catch (IOException | SecurityException ex) {
                /*
                 * If there is a problem writing state files something is
                 * seriously wrong, e.g., a permissions problem, an unplugged
                 * network cable, or a crashed file server. Pause auto ingest
                 * and wait for human intervention.
                 */
                if (null != currentImageFolderLock) {
                    releaseCoordinationServiceLockNoThrow(currentImageFolderLock);
                    currentImageFolderLock = null;
                }
                throw new PauseRequiredException("Error doing state file I/O", ex, Event.PAUSED_FAILED_WRITING_STATE_FILES);
            }
            if (useMaxConcurrentJobsForCase == false) {
                return startNextJob(true);
            }
            return false;
        }

        /**
         * Completes the processing of an auto ingest job that began in
         * startNextJob().
         *
         * @throws InterruptedException   If the thread running this task is
         *                                interrupted while blocked, i.e., if
         *                                auto ingest is shutting down.
         * @throws PauseRequiredException If there is some system error
         *                                condition that requires suspension of
         *                                auto ingest until the problem is
         *                                fixed.
         */
        private void completeJob() throws InterruptedException, PauseRequiredException {
            boolean requeueJob = true;
            Path imageFolderPath = currentJob.getImageFolderPath();
            Path caseFolderPath = null;
            Lock caseNameLock = null;
            Case caseForJob = null;
            try {
                /*
                 * If using shared settings, download the latest version of the
                 * settings.
                 */
                if (VikingUserPreferences.getSharedConfigEnabled()) {
                    currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.UPDATING_SHARED_CONFIG);
                    SharedConfiguration config = new SharedConfiguration();
                    if (SharedConfiguration.SharedConfigResult.LOCKED == config.downloadConfiguration()) {
                        logger.log(Level.WARNING, "Timed out trying to download shared configuration");
                        return;
                    }
                }

                /*
                 * Check the availability of the required services.
                 */
                currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.CHECKING_SERVICES);
                if (!isServiceUp(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString())) {
                    throw new PauseRequiredException("Case database server is down", Event.PAUSED_CASE_DATABASE_SERVICE_DOWN);
                }
                if (!isServiceUp(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString())) {
                    throw new PauseRequiredException("Keyword search service is down", Event.PAUSED_KEYWORD_SEARCH_SERVICE_DOWN);
                }

                /*
                 * Acquire an exclusive case name lock. This lock is used so
                 * that only one auto ingest node at a time can create any
                 * particular case.
                 */
                currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.OPENING_CASE);
                String caseName = currentJob.getCaseName();
                Path caseNameLockPath = rootCaseFolderPath.resolve(caseName);
                caseNameLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseNameLockPath.toString(), 30, TimeUnit.MINUTES);
                if (null == caseNameLock) {
                    logger.log(Level.WARNING, "Timed out trying to acquire case name lock for {0}, will retry job", imageFolderPath);
                    return;
                }

                /*
                 * Now that the lock has been acquired, find or create the case
                 * for the job.
                 */
                caseFolderPath = PathUtils.findCaseFolder(rootCaseFolderPath, caseName);
                if (null != caseFolderPath) {
                    Path metadataFilePath = caseFolderPath.resolve(currentJob.getCaseName() + CaseMetadata.getFileExtension());
                    Case.open(metadataFilePath.toString());
                } else {
                    caseFolderPath = PathUtils.createCaseFolderPath(rootCaseFolderPath, caseName);
                    Case.create(caseFolderPath.toString(), currentJob.getCaseName(), "", "", CaseType.MULTI_USER_CASE);
                    /*
                     * Sleep a bit before releasing the lock to ensure that the
                     * new case folder is visible on the network.
                     */
                    Thread.sleep(VikingUserPreferences.getSecondsToSleepBetweenCases() * 1000);
                }
                caseNameLock.release();
                caseNameLock = null;
                caseForJob = Case.getCurrentCase();
                currentJob.setCaseFolderPath(caseFolderPath);

                if (currentJob.getIngestStatus().isCancelled()) {
                    requeueJob = false;
                    writeCancelledStateFiles(currentJob);
                    new AutoIngestJobLogger(currentJob.getImageFolderPath(), currentJob.getCaseFolderPath()).logJobCancelled();
                    return;
                }

                /*
                 * Ingest the data sources in the image folder.
                 */
                requeueJob = false;
                ingestDataSources();

            } catch (InterruptedException ex) {
                /*
                 * The thread running this task was interrupted while blocked
                 * with a job running.
                 */
                requeueJob = false;
                writeInterruptedStateFiles(currentJob);
                if (null != caseFolderPath && caseFolderPath.toFile().exists()) {
                    new AutoIngestJobLogger(imageFolderPath, caseFolderPath).logJobCancelled();
                }
                throw ex;

            } catch (SharedConfigurationException ex) {
                throw new PauseRequiredException("Error downloading shared configuration", ex, Event.PAUSED_SHARED_CONFIG_ERROR);

            } catch (CoordinationServiceException ex) {
                /*
                 * There was a problem with the coordination service. The system
                 * cannot be guaranteed to function correctly. Pause auto ingest
                 * and wait for human intervention.
                 */
                throw new PauseRequiredException(String.format("Coordination service error processing %s", imageFolderPath), ex, Event.PAUSED_COORDINATION_SERVICE_DOWN);

            } catch (PauseRequiredException ex) {
                /*
                 * There was a problem updating the shared configuration or one
                 * of the required services is down. Pause auto ingest and wait
                 * for human intervention.
                 */
                throw ex;

            } catch (CaseActionException | IllegalStateException ex) {
                /*
                 * There was a problem problem creating or opening the case
                 * (Case.getCurrentCase throws IllegalStateException when there
                 * is no current case).
                 */
                requeueJob = false;
                logger.log(Level.SEVERE, String.format("Error opening the case for %s", imageFolderPath), ex);
                writeErrorStateFiles(currentJob);
                if (null != caseFolderPath && caseFolderPath.toFile().exists()) {
                    new AutoIngestJobLogger(imageFolderPath, caseFolderPath).logUnableToOpenCase();
                }
            } catch (Exception ex) {
                /*
                 * This catch is an "exception firewall" for the processing of
                 * the job.
                 */
                requeueJob = false;
                logger.log(Level.SEVERE, String.format("Error processing %s", imageFolderPath), ex);
                writeErrorStateFiles(currentJob);
                if (null != caseFolderPath && caseFolderPath.toFile().exists()) {
                    new AutoIngestJobLogger(imageFolderPath, caseFolderPath).logRuntimeException(ex);
                }
            } finally {
                /*
                 * Do the clean up that needs to be done whether or not the
                 * processing threw an exception, starting with closing the case
                 * if it was open. IMPORTANT: Note that any exceptions thrown
                 * during clean up are logged, but not thrown, since they would
                 * hide the InterruptedException or PauseRequiredException
                 * coming out of the preceding catch blocks. Doing the latter
                 * would interfere with the shut down process, and doing the
                 * former would be redundant.
                 */
                if (null != caseForJob) {
                    try {
                        caseForJob.closeCase();
                    } catch (CaseActionException ex) {
                        logger.log(Level.SEVERE, String.format("Error closing case at %s", caseForJob.getCaseDirectory()), ex);
                    }
                }

                /*
                 * Either write a DONE or a READY state file, depending on
                 * whether the job failed to truly start or not. In either case,
                 * delete the PROCESSING file.
                 */
                if (!requeueJob) {
                    try {
                        StateFile.create(imageFolderPath, DONE);
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to write DONE state file to %s", imageFolderPath), ex);
                    }
                } else {
                    try {
                        StateFile.create(imageFolderPath, READY);
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to write READY state file to %s", imageFolderPath), ex);
                    }
                }
                try {
                    StateFile.delete(imageFolderPath, PROCESSING);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to delete PROCESSING state file from %s", imageFolderPath), ex);
                }

                /*
                 * Sleep a bit to allow time for the state file changes to
                 * become visible on the network.
                 */
                Thread.sleep(VikingUserPreferences.getSecondsToSleepBetweenCases() * 1000);

                /*
                 * Try to release all of the coordination service locks that
                 * might still be being held for the processing of this job.
                 */
                if (null != caseNameLock) {
                    releaseCoordinationServiceLockNoThrow(caseNameLock);
                }
                releaseCoordinationServiceLockNoThrow(currentImageFolderLock);
                currentImageFolderLock = null;

                /*
                 * Move the completed job into the completed jobs list and
                 * publish a job completed event.
                 */
                logger.log(Level.INFO, "Completed auto ingest of {0}", imageFolderPath);
                synchronized (jobListsMonitor) {
                    if (!requeueJob) {
                        currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.COMPLETED);
                        completedJobs.add(currentJob);
                    }
                    publishJobCompletedEvent(currentJob, requeueJob);
                    currentJob = null;
                }

            }
        }

        /**
         * Tests service of interest to verify that it is running.
         *
         * @param serviceName Name of the service.
         *
         * @return True if the service is running, false otherwise.
         */
        private boolean isServiceUp(String serviceName) {
            try {
                return (ServicesMonitor.getInstance().getServiceStatus(serviceName).equals(ServicesMonitor.ServiceStatus.UP.toString()));
            } catch (ServicesMonitor.ServicesMonitorException ex) {
                logger.log(Level.SEVERE, String.format("Problem checking service status for %s", serviceName), ex);
                return false;
            }
        }

        /**
         * Identifies the devices in the input folder for the current job, calls
         * appropriate data source processors to add the data sources associated
         * with the devices to the case database, passes the data sources to the
         * ingest manager for analysis by data source and file level ingest
         * modules, and exports the files from the data sources that satisfy
         * user-defined file export rules.
         *
         * @throws InterruptedException   If the thread running this task is
         *                                interrupted while blocked, i.e., if
         *                                automated ingest is shutting down.
         * @throws PauseRequiredException If a problem with the services that
         *                                automated ingest requires is detected.
         */
        private void ingestDataSources() throws InterruptedException, PauseRequiredException {

            try {
                Path imageFolderPath = currentJob.getImageFolderPath();
                Path caseFolderPath = currentJob.getCaseFolderPath();
                AutoIngestJobLogger jobLogger = new AutoIngestJobLogger(imageFolderPath, caseFolderPath);

                /*
                 * Find the data source manifest files in the input folder.
                 */
                logger.log(Level.INFO, "Automated ingest looking for data source manifest files in {0}", imageFolderPath);
                currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.IDENTIFYING_IMAGES);
                List<String> manifestFiles = identifyManifestFiles(imageFolderPath);
                if (manifestFiles.isEmpty()) {
                    writeErrorStateFiles(currentJob);
                    logger.log(Level.WARNING, "Automated ingest found no data source manifest files in {0}", imageFolderPath);
                    jobLogger.logMissingManifest();
                    return;
                }

                if (currentJob.getIngestStatus().isCancelled()) {
                    writeCancelledStateFiles(currentJob);
                    jobLogger.logJobCancelled();
                    return;
                }

                /*
                 * Find the data sources described by the data source manifest
                 * files.
                 */
                logger.log(Level.INFO, "Automated ingest identifying data sources for data source manifest files in {0}", imageFolderPath);
                List<DataSourceInfo> dataSourceInfos = identifyDataSources(imageFolderPath, manifestFiles);
                if (dataSourceInfos.isEmpty()) {
                    logger.log(Level.SEVERE, "Automated ingest found no data sources for data source manifest files in {0}", imageFolderPath);
                    /*
                     * NOTE: The case job log is updated for this error by the
                     * identifyDataSources method where more details are known.
                     */
                    return;
                }

                if (currentJob.getIngestStatus().isCancelled()) {
                    writeCancelledStateFiles(currentJob);
                    jobLogger.logJobCancelled();
                    return;
                }

                /*
                 * Add the data sources to the case database.
                 */
                logger.log(Level.INFO, "Automated ingest adding devices from {0} to case database", imageFolderPath);
                currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.ADDING_IMAGES);
                List<Content> dataSources = addDataSourcesToCaseDatabase(dataSourceInfos);
                if (dataSources.isEmpty()) {
                    logger.log(Level.WARNING, "Automated ingest could not add any data sources from {0}", imageFolderPath);
                    /*
                     * NOTE: The case job log is updated by the
                     * addDataSourcesToCaseDatabase method where more details
                     * are known.
                     */
                    return;
                }

                if (currentJob.getIngestStatus().isCancelled()) {
                    writeCancelledStateFiles(currentJob);
                    jobLogger.logJobCancelled();
                    return;
                }

                /*
                 * Analyze the data sources with data-source-level and
                 * file-level ingest modules.
                 */
                logger.log(Level.INFO, "Automated ingest running ingest modules for {0}", imageFolderPath);
                currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.ANALYZING_IMAGES);
                analyzeDataSourcesWithIngestModules(dataSources);

                if (currentJob.getIngestStatus().isCancelled()) {
                    writeCancelledStateFiles(currentJob);
                    jobLogger.logJobCancelled();
                    return;
                }

                /*
                 * Export any files from the data sources that satisfy the
                 * user-defined file export rules.
                 */
                exportFiles(dataSourceInfos);

            } finally {
                /*
                 * Sleep to allow ingest event subscribers to do their event
                 * handling, possibly on worker threads.
                 */
                Thread.sleep(VikingUserPreferences.getSecondsToSleepBetweenCases() * 1000);
            }
        }

        /**
         * Get manifest XML file name for a given data source file name.
         *
         * @param dataSourceFileName Data source file name
         *
         * @return Manifest XML file name
         */
        private String getManifestFileNameForDataSource(String dataSourceFileName) {
            // Manifest file must be located in the same folder as the data source. It has same 
            // name as the data source (excluding extension) followed by "_Manifest.xml".
            // For example, hd01.e01 data source must have hd01_Manifest.xml manifest file.
            String dataSourceFileNameNoExt = FilenameUtils.removeExtension(dataSourceFileName);
            return dataSourceFileNameNoExt + MANIFEST_FILE_NAME_SIGNATURE;
        }

        /**
         * Extracts device id from the manifest XML file.
         *
         * @param manifestFile Manifest file object
         *
         * @return The device id string if successful, empty string otherwise
         */
        private String readDeviceIdFromManifestFile(File manifestFile) {
            String deviceId = "";
            if (manifestFile.length() > 0) {
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(manifestFile);
                    XPathFactory xPathfactory = XPathFactory.newInstance();
                    XPath xpath = xPathfactory.newXPath();
                    XPathExpression expr = xpath.compile(IMAGE_ID_PATH_IN_MANIFEST_XML);
                    deviceId = (String) expr.evaluate(doc, XPathConstants.STRING);
                } catch (Exception ignored) {
                    /*
                     * Currently manifest files are not required to contain
                     * well-formed XML (or anything at all).
                     */
                }
            }
            return deviceId;
        }

        /**
         * Finds all manifest XML files in target folder.
         *
         * @param imageFolderPath Full path to the target folder
         *
         * @return List of manifest file names in the folder
         *
         * @throws InterruptedException
         */
        private List<String> identifyManifestFiles(Path imageFolderPath) throws InterruptedException {
            String[] files = FileFilters.getAllFilesInFolder(imageFolderPath.toString());
            if (files.length == 0) {
                return Collections.emptyList();
            }

            List<String> manifestFiles = new ArrayList<>();
            for (String file : files) {
                // check whether this is a manifest XML file
                if (!file.endsWith(MANIFEST_FILE_NAME_SIGNATURE)) {
                    continue;
                }

                // add the file to list of manifest files
                manifestFiles.add(file);
            }

            return manifestFiles;
        }

        /**
         * Identifies all data sources within a folder given a list of manifest
         * files to match the data sources to.
         *
         * @param imageFolderPath Full path to the folder containing data
         *                        sources
         * @param manifestFiles   List of manifest file names to match the data
         *                        sources to
         *
         * @return List of data source objects
         *
         * @throws InterruptedException
         */
        private List<DataSourceInfo> identifyDataSources(Path imageFolderPath, List<String> manifestFiles) throws InterruptedException {

            Path caseFolderPath = currentJob.getCaseFolderPath();

            // find all potential data sources in input folder
            List<DataSourceInfo> potentialDataSources = findAllSupportedDataSources(imageFolderPath);

            // find a matchind data source for each manifest file
            List<DataSourceInfo> dataSources = new ArrayList<>();

            for (String manifestFileName : manifestFiles) {

                File manifestFile = Paths.get(imageFolderPath.toString(), manifestFileName).toFile();
                // read data source id from manifest file
                String deviceId = readDeviceIdFromManifestFile(manifestFile);
                if (deviceId.isEmpty()) {
                    // manifest XML file is missing data source ID. Use UUID instead.
                    deviceId = UUID.randomUUID().toString();
                }

                int lastFileNameChar = manifestFileName.lastIndexOf(MANIFEST_FILE_NAME_SIGNATURE);
                if (-1 == lastFileNameChar) {
                    continue;
                }
                String dataSourceNameNoExt = manifestFileName.substring(0, lastFileNameChar);

                // loop over all potential data sources to look for a match(es) for this manifest file
                List<DataSourceInfo> matches = new ArrayList<>();
                for (DataSourceInfo dataSource : potentialDataSources) {
                    // get data source file name without extension
                    String fileNameNoExt = FilenameUtils.removeExtension(dataSource.getFileName());

                    // check if the file name is the same as manifest file
                    if (!dataSourceNameNoExt.equalsIgnoreCase(fileNameNoExt)) {
                        continue;
                    }

                    // update the data source id
                    dataSource.setDeviceID(deviceId);

                    // found a valid data source that matches this manifest file
                    matches.add(dataSource);
                }

                // check number of matches that we found
                if (matches.isEmpty()) {
                    // Record error - missing data source for manifest
                    logger.log(Level.SEVERE, String.format("Data source for manifest XML file is either missing or is not supported: %s//%s",
                            imageFolderPath.toString(), manifestFile.toString()));
                    writeErrorStateFiles(currentJob);
                    new AutoIngestJobLogger(imageFolderPath, caseFolderPath).logMissingDataSource(manifestFileName);
                } else if (matches.size() > 1) {
                    // Record error/warning - manifest matches more than one file
                    logger.log(Level.SEVERE, String.format("Manifest XML file matches multiple data sources: %s//%s",
                            imageFolderPath.toString(), manifestFile.toString()));
                    writeErrorStateFiles(currentJob);
                    new AutoIngestJobLogger(imageFolderPath, caseFolderPath).logAmbiguousManifest(manifestFileName);
                } else {
                    // found the data source that matches this manifest file
                    logger.log(Level.INFO, String.format("Successfully identified data source for manifest XML file: %s//%s",
                            imageFolderPath.toString(), manifestFile.toString()));
                    dataSources.addAll(matches);
                }
            }

            return dataSources;
        }

        /**
         * Gets a listing of all of the files in the input folder of an auto
         * ingest and determines which files are data sources, and of what
         * types. Folders are not included in the listing because an auto ingest
         * job is concerned only with the image files in a single input folder.
         *
         * @param imageFolderPath Full path to the input image folder
         *
         * @return A list of typed data sources.
         *
         * @throws InterruptedException
         */
        private List<DataSourceInfo> findAllSupportedDataSources(Path imageFolderPath) throws InterruptedException {
            Path caseFolderPath = currentJob.getCaseFolderPath();
            String[] files = FileFilters.getAllFilesInFolder(imageFolderPath.toString());
            if (files.length == 0) {
                return Collections.emptyList();
            }

            List<DataSourceInfo> dataSources = new ArrayList<>();
            for (String file : files) {
                if (currentJob.getIngestStatus().isCancelled()) {
                    break;
                }

                try {
                    // check if it is a ZIP archive (contains Cellebrite Android data sources)
                    if (FileFilters.isAcceptedByFiler(new File(file), FileFilters.archiveFiltersList)) {
                        // check that this is a manifest file for this ZIP file (i.e. is this a valid data source or just a random ZIP file).
                        File manifestFile = Paths.get(imageFolderPath.toString(), getManifestFileNameForDataSource(file)).toFile();
                        if (!manifestFile.exists()) {
                            continue; // ZIP file is not a valid data source
                        }

                        // create destination folder and extract ZIP archive
                        Path destinationFolder = handleCellebriteZipArchive(imageFolderPath, file);
                        dataSources.add(new DataSourceInfo(DataSourceInfo.Type.CELLEBRITE_ANDROID_ZIP, file, destinationFolder.toString()));
                        continue;
                    }

                    DataSourceInfo.Type cellXmlType = FileFilters.getCellebriteXmlReportType(file, imageFolderPath);
                    if (cellXmlType != null) {
                        dataSources.add(new DataSourceInfo(cellXmlType, file, imageFolderPath.toString()));
                        continue;
                    }

                    if (!FileFilters.isFirstFileOfDiskOrPhoneImage(file)) {
                        continue;
                    }

                    if (imageHasFileSystem(imageFolderPath.toString(), file)) {
                        dataSources.add(new DataSourceInfo(DataSourceInfo.Type.DISK_IMAGE, file, imageFolderPath.toString()));
                    } else {
                        dataSources.add(new DataSourceInfo(DataSourceInfo.Type.PHONE_IMAGE, file, imageFolderPath.toString()));
                    }
                } catch (Exception ex) {
                    writeErrorStateFiles(currentJob);
                    logger.log(Level.SEVERE, String.format("Error attempting data source identification of %s in %s", file, imageFolderPath), ex);
                    new AutoIngestJobLogger(imageFolderPath, caseFolderPath).logDataSourceTypeIdError(file, ex);
                }
            }

            // check if there are virtual machine files to process
            List<String> vmFilesToIngest = VirtualMachineFinder.identifyVirtualMachines(imageFolderPath);
            for (String file : vmFilesToIngest) {
                dataSources.add(new DataSourceInfo(DataSourceInfo.Type.DISK_IMAGE, file, imageFolderPath.toString()));
            }

            return dataSources;
        }

        /**
         * Uses the installed tsk_isImageTool executable to determine whether a
         * potential data source has a file system.
         *
         * @param folderPath The folder path of the data source file.
         * @param fileName   The name of the data source file.
         *
         * @return True or false.
         *
         * @throws Exception if any error occurs while trying to determine if
         *                   the image has a file system.
         */
        private boolean imageHasFileSystem(String folderPath, String fileName) throws Exception {
            Path logFileName = Paths.get(Case.getCurrentCase().getTempDirectory(), "tsk_isImageTool.log");
            File logFile = new File(logFileName.toString());
            Path errFileName = Paths.get(Case.getCurrentCase().getTempDirectory(), "tsk_isImageTool_err.log");
            File errFile = new File(errFileName.toString());
            Path imageFilePath = Paths.get(folderPath, fileName);
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "\"" + tskIsImageToolExePath.toString() + "\"",
                    "\"" + imageFilePath + "\"");
            File directory = new File(tskIsImageToolExePath.getParent().toString());
            processBuilder.directory(directory);
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            int exitValue = ExecUtil.execute(processBuilder);
            Files.delete(logFileName);
            Files.delete(errFileName);
            return exitValue == 0;
        }

        /**
         * Runs a collection of data sources through the data source processors
         * that add the data sources to the case database.
         *
         * @param dataSourceInfos The data source information.
         *
         * @return The data sources that were added to the case database.
         *
         * @throws InterruptedException If the thread running this task is
         *                              interrupted while blocked, i.e., if
         *                              automated ingest is shutting down.
         */
        private List<Content> addDataSourcesToCaseDatabase(List<DataSourceInfo> dataSourceInfos) throws InterruptedException {

            /**
             * This "callback" collects the results of running a data source
             * processor on a data source and unblocks the thread this code is
             * running in when the data source processor finishes running in its
             * own thread.
             */
            class AddDataSourceCallback extends DataSourceProcessorCallback {

                private final DataSourceInfo dataSourceInfo;
                private final UUID taskId;
                private final List<Content> dataSources;

                AddDataSourceCallback(DataSourceInfo dataSourceInfo, UUID taskId, List<Content> dataSources) {
                    this.dataSourceInfo = dataSourceInfo;
                    this.taskId = taskId;
                    this.dataSources = dataSources;
                }

                @Override
                public void done(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList, List<Content> dataSources) {
                    if (!dataSources.isEmpty()) {
                        Case.getCurrentCase().notifyDataSourceAdded(dataSources.get(0), taskId);
                    } else {
                        Case.getCurrentCase().notifyFailedAddingDataSource(taskId);
                    }
                    dataSourceInfo.setDataSourceProcessorResult(result, errList, dataSources);
                    this.dataSources.addAll(dataSources);
                    synchronized (ingestInProgressMonitor) {
                        ingestInProgressMonitor.notify();
                    }
                }

                @Override
                public void doneEDT(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList, List<Content> contents) {
                    done(result, errList, contents);
                }
            }

            /*
             * This data source processor progress monitor does nothing. There
             * is currently no UI for showing data source processor progress
             * during an auto ingest job.
             */
            final DataSourceProcessorProgressMonitor doNothingProgressMonitor = new DataSourceProcessorProgressMonitor() {
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

            Path inputFolderPath = currentJob.getImageFolderPath();
            final UUID taskId = UUID.randomUUID();
            List<Content> dataSources = new ArrayList<>();
            try {
                for (DataSourceInfo dataSource : dataSourceInfos) {
                    if (currentJob.getIngestStatus().isCancelled()) {
                        break;
                    }
                    logger.log(Level.INFO, "Running data source processor for {1} from {2}",
                            new Object[]{dataSource.fileName, inputFolderPath});
                    Case.getCurrentCase().notifyAddingDataSource(taskId);
                    synchronized (ingestInProgressMonitor) {
                        switch (dataSource.type) {
                            case CELLEBRITE_REPORT_HANDSET:
                                CellebriteXMLProcessor cellebriteXmlHandsetDsp = new CellebriteXMLProcessor();
                                cellebriteXmlHandsetDsp.run(dataSource.getDeviceId(), dataSource.getDeviceId(),
                                        Paths.get(dataSource.getDataSourceFolderPath(), dataSource.getFileName()).toString(),
                                        true, doNothingProgressMonitor, new AddDataSourceCallback(dataSource, taskId, dataSources));
                                break;

                            case CELLEBRITE_REPORT_SIM:
                                CellebriteXMLProcessor cellebriteXmlSimDsp = new CellebriteXMLProcessor();
                                cellebriteXmlSimDsp.run(dataSource.getDeviceId(), dataSource.getDeviceId(),
                                        Paths.get(dataSource.getDataSourceFolderPath(), dataSource.getFileName()).toString(),
                                        false, doNothingProgressMonitor, new AddDataSourceCallback(dataSource, taskId, dataSources));
                                break;

                            case DISK_IMAGE:
                                ImageDSProcessor imageDsp = new ImageDSProcessor();
                                imageDsp.run(dataSource.getDeviceId(),
                                        Paths.get(dataSource.getDataSourceFolderPath(), dataSource.getFileName()).toString(),
                                        "", false, doNothingProgressMonitor, new AddDataSourceCallback(dataSource, taskId, dataSources));
                                break;

                            case PHONE_IMAGE:
                                MPFProcessor mpfDsp = new MPFProcessor();
                                mpfDsp.run(dataSource.getDeviceId(),
                                        Paths.get(dataSource.getDataSourceFolderPath(), dataSource.getFileName()).toString(),
                                        "", false, doNothingProgressMonitor, new AddDataSourceCallback(dataSource, taskId, dataSources));
                                break;

                            case CELLEBRITE_ANDROID_ZIP:
                                CellebriteAndroidImageProcessor cellebriteDsp = new CellebriteAndroidImageProcessor();
                                cellebriteDsp.run(dataSource.getDeviceId(), dataSource.getDataSourceFolderPath(), "", doNothingProgressMonitor,
                                        new AddDataSourceCallback(dataSource, taskId, dataSources));
                                break;

                            default:
                                logger.log(Level.SEVERE, "Unknown data source type {0} for data source {1}", new Object[]{dataSource.type,
                                    Paths.get(dataSource.getDataSourceFolderPath(), dataSource.getFileName()).toString()});  // NON-NLS
                                return null;
                        }
                        ingestInProgressMonitor.wait();
                    }
                }
            } finally {
                logAddDataSourcesResults(dataSourceInfos);
            }
            return dataSources;
        }

        /**
         * Processes a ZIP archive containing Cellebrite Android image files.
         *
         * @param inputFolderPath Full path to folder containing the archive
         * @param fileName        Archive file name
         *
         * @return Full path to folder containing extracted content
         *
         * @throws IOException
         */
        private Path handleCellebriteZipArchive(Path inputFolderPath, String fileName) throws IOException {

            // get file name without extension
            String dataSourceFileNameNoExt = FilenameUtils.removeExtension(fileName);

            // create folder to extract archive to
            Path destinationFolder = Paths.get(Case.getCurrentCase().getModuleDirectory(), AIM_MODULE_OUTPUT_FOLDER_NAME,
                    dataSourceFileNameNoExt + "_" + TimeStampUtils.createTimeStamp());
            Files.createDirectories(destinationFolder);

            // extract contents of ZIP archive into destination folder
            extractZipArchive(destinationFolder, Paths.get(inputFolderPath.toString(), fileName));
            return destinationFolder;
        }

        /**
         * Extract ZIP archive contents into a subfolder
         *
         * @param destinationFolder Full path to destination folder
         * @param archiveFile       Full path to archive file
         *
         * @throws IOException
         */
        private void extractZipArchive(Path destinationFolder, Path archiveFile) throws IOException {

            // create a buffer to improve copy performance.
            int BUFFER = 524288;    // read/write 500KB at a time

            File sourceZipFile = new File(archiveFile.toString());
            ZipFile zipFile;
            // Open Zip file for reading
            zipFile = new ZipFile(sourceZipFile, ZipFile.OPEN_READ);

            // Create an enumeration of the entries in the zip file
            Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();

            try {
                // Process each entry
                while (zipFileEntries.hasMoreElements()) {
                    // grab a zip file entry
                    ZipEntry entry = zipFileEntries.nextElement();

                    String currentEntry = entry.getName();

                    File destFile = new File(destinationFolder.toString(), currentEntry);
                    destFile = new File(destinationFolder.toString(), destFile.getName());

                    // create the parent directory structure if needed
                    File destinationParent = destFile.getParentFile();
                    destinationParent.mkdirs();

                    // extract file if not a directory
                    if (!entry.isDirectory()) {
                        BufferedInputStream is
                                = new BufferedInputStream(zipFile.getInputStream(entry));
                        int currentByte;
                        // establish buffer for writing file
                        byte data[] = new byte[BUFFER];

                        // write the current file to disk
                        try (FileOutputStream fos = new FileOutputStream(destFile);
                                BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER)) {
                            // read and write until last byte is encountered
                            currentByte = is.read(data, 0, BUFFER);
                            while (currentByte != -1) {
                                dest.write(data, 0, currentByte);
                                currentByte = is.read(data, 0, BUFFER);
                            }
                        }
                    }
                }
            } finally {
                // we must always close the zip file.
                zipFile.close();
            }
        }

        /**
         * Records the results of attempting to add a set of data source to the
         * case database.
         *
         * @param dataSourceInfos Data source info objects for the data sources.
         */
        private void logAddDataSourcesResults(List<DataSourceInfo> dataSourceInfos) throws InterruptedException {
            Path imageFolderPath = currentJob.getImageFolderPath();
            Path caseFolderPath = currentJob.getCaseFolderPath();
            AutoIngestJobLogger log = new AutoIngestJobLogger(imageFolderPath, caseFolderPath);
            for (DataSourceInfo dataSource : dataSourceInfos) {
                String imageType = dataSource.getType().toString();
                if (!dataSource.dataSourceProcessorCompleted()) {
                    writeCancelledStateFiles(currentJob);
                    log.logDataSourceProcessorCancelled(dataSource.getFileName(), imageType);
                    break;
                }
                DataSourceProcessorCallback.DataSourceProcessorResult result = dataSource.getDataSourceProcessorResult();
                Level errorLevel;
                if (DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS != result && !dataSource.getDataSourceContent().isEmpty()) {
                    log.logDataSourceAdded(dataSource.getFileName(), imageType);
                    errorLevel = Level.WARNING;
                } else {
                    writeErrorStateFiles(currentJob);
                    /*
                     * Log the data source processor errors and then finish with
                     * a failure to add data source message.
                     */
                    for (String errorMessage : dataSource.getErrorMessages()) {
                        log.logDataSourceProcessorError(dataSource.getFileName(), errorMessage);
                    }
                    log.logFailedToAddDataSource(dataSource.getFileName(), imageType);
                    errorLevel = Level.SEVERE;
                }
                for (String errorMessage : dataSource.getErrorMessages()) {
                    logger.log(errorLevel, "Error running data source processor on {0} from {1}: {2}", new Object[]{dataSource.getFileName(), imageFolderPath, errorMessage});
                }
            }
        }

        /**
         * Analyzes the data sources returned by the data source processors
         * using the configured set of data-source-level and file-level ingest
         * modules.
         *
         * @param dataSources The data sources to analyze.
         * @throws InterruptedException If the thread running this task is
         *                              interrupted while blocked, i.e., if
         *                              automated ingest is shutting down.
         * @throws viking.autoingest.AutoIngestManager.AutoIngestTask.PauseRequiredException 
         *                              If IngestJob fails to start.
         */
        private void analyzeDataSourcesWithIngestModules(List<Content> dataSources) throws InterruptedException, PauseRequiredException {
            /*
             * Create a ingest job event listener to allow this auto ingest task
             * to block until the ingest job for the data sources is completed.
             * Note that the ingest job can spawn "child" ingest jobs (e.g., if
             * an embedded virtual machine is found), so the task must remain
             * blocked until ingest is no longer running.
             */
            PropertyChangeListener completionListener = (PropertyChangeEvent evt) -> {
                if (AutopsyEvent.SourceType.LOCAL == ((AutopsyEvent) evt).getSourceType()) {
                    String eventType = evt.getPropertyName();
                    if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                            || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                        synchronized (ingestInProgressMonitor) {
                            if (!IngestManager.getInstance().isIngestRunning()) {
                                ingestInProgressMonitor.notify();
                            }
                        }
                    }
                }
            };

            /*
             * Try to run an ingest job for the data sources, blocking until the
             * ingest job is completed.
             */
            IngestManager.getInstance().addIngestJobEventListener(completionListener);
            IngestJobStartResult ingestJobStartResult = null;
            try {
                synchronized (ingestInProgressMonitor) {
                    IngestJobSettings ingestJobSettings = new IngestJobSettings(VikingUserPreferences.getAutoModeIngestModuleContextString());
                    for (String warning : ingestJobSettings.getWarnings()) {
                        /*
                         * TODO (VIK-1706): Should probably log the problem in
                         * the case auto ingest log and pause here.
                         */
                        logger.log(Level.WARNING, warning);
                    }
                    ingestJobStartResult = IngestManager.getInstance().beginIngestJob(dataSources, ingestJobSettings);
                    if (null != ingestJobStartResult.getJob()) {
                        currentJob.getIngestStatus().setIngestJob(ingestJobStartResult.getJob());
                        ingestInProgressMonitor.wait();
                    } else {
                        // ingest job failed to start, pause AIM
                        throw new PauseRequiredException("Failed to start ingest job", Event.PAUSED_FAILED_TO_START_INGEST_JOB);
                    }
                }
            } finally {
                IngestManager.getInstance().removeIngestJobEventListener(completionListener);
                if (null != ingestJobStartResult) {
                    logDataSourcesAnalysisResults(dataSources, ingestJobStartResult);
                }
            }
        }

        /**
         * Records the results of analyzing one or more data sources using the
         * configured set of data-source-level and file-level ingest modules.
         *
         * @param dataSources          The data sources.
         * @param ingestJobStartResult The ingest job start result for the data
         *                             sources.
         *
         * @throws InterruptedException if interrupted while blocked writing
         *                              state files or case auto ingest log
         *                              entries.
         */
        private void logDataSourcesAnalysisResults(List<Content> dataSources, IngestJobStartResult ingestJobStartResult) throws InterruptedException {
            Path imageFolderPath = currentJob.getImageFolderPath();
            Path caseFolderPath = currentJob.getCaseFolderPath();
            AutoIngestJobLogger log = new AutoIngestJobLogger(imageFolderPath, caseFolderPath);
            IngestJob ingestJob = ingestJobStartResult.getJob();
            if (null != ingestJob) {
                /*
                 * The ingest job for the data sources was successfully started.
                 * Get the final ingest job state snapshot and log the details.
                 */
                IngestJob.ProgressSnapshot jobSnapshot = ingestJob.getSnapshot();
                for (IngestJob.ProgressSnapshot.DataSourceProcessingSnapshot snapshot : jobSnapshot.getDataSourceSnapshots()) {
                    String dataSource = snapshot.getDataSource();
                    if (!snapshot.isCancelled()) {
                        for (String module : snapshot.getCancelledDataSourceIngestModules()) {
                            writeCancelledStateFiles(currentJob);
                            log.logIngestModuleCancelled(dataSource, module);
                        }
                        log.logAnalysisCompleted(dataSource);
                    } else {
                        writeCancelledStateFiles(currentJob);
                        log.logAnalysisCancelled(dataSource, snapshot.getCancellationReason().getDisplayName());
                    }
                }
            } else {
                /*
                 * There was a problem starting the ingest job.
                 */
                writeErrorStateFiles(currentJob);
                if (!ingestJobStartResult.getModuleErrors().isEmpty()) {
                    for (Content dataSource : dataSources) {
                        log.logIngestModuleStartupErrors(dataSource.getName(), ingestJobStartResult.getModuleErrors());
                    }
                } else {
                    for (Content dataSource : dataSources) {
                        log.logAnalysisStartupError(dataSource.getName(), ingestJobStartResult.getStartupException());
                    }
                }
            }
        }

        /**
         * Exports the files that satisfy the user-defined file export rules
         * from the data sources associated with a collection of devices.
         *
         * @param devices The devices.
         *
         * @throws InterruptedException If the thread running this task is
         *                              interrupted while blocked, i.e., if
         *                              automated ingest is shutting down.
         * @throws PauseRequiredException If File Exporter settings fail to load.
         */
        private void exportFiles(List<DataSourceInfo> devices) throws InterruptedException, PauseRequiredException {
            Path deviceFolderPath = currentJob.getImageFolderPath();
            Path caseFolderPath = currentJob.getCaseFolderPath();

            FileExporter fileExporter;
            try {
                fileExporter = new FileExporter();
            } catch (FileExportException ex) {
                logger.log(Level.SEVERE, String.format("Error initializing File Exporter when processing %s", currentJob.getImageFolderPath()), ex);
                writeErrorStateFiles(currentJob);
                new AutoIngestJobLogger(deviceFolderPath, caseFolderPath).logfileExportStartupError(ex);
                // failed to load file exporter settings, pause AIM
                throw new PauseRequiredException("Failed to load File Exporter settings", Event.PAUSED_FAILED_TO_START_FILE_EXPORTER);
            }

            // check whether File Exporter is enabled
            if (!fileExporter.isEnabled()) {
                new AutoIngestJobLogger(deviceFolderPath, caseFolderPath).logFileExportDisabled();
                return;
            }

            logger.log(Level.INFO, "Automated ingest exporting files for {0}", currentJob.getImageFolderPath());
            currentJob.getIngestStatus().setStatus(JobIngestStatus.IngestStatus.EXPORTING_FILES);
            for (DataSourceInfo device : devices) {
                try {
                    fileExporter.process(device.getDeviceId(), device.getDataSourceContent());
                } catch (FileExportException ex) {
                    logger.log(Level.SEVERE, String.format("Error exporting files when processing %s", currentJob.getImageFolderPath()), ex);
                    writeErrorStateFiles(currentJob);
                    new AutoIngestJobLogger(deviceFolderPath, caseFolderPath).logFileExportError(device.getFileName(), ex);
                }
            }
        }

        /*
         * Exception thrown when a user or system event requires suspension of
         * the job processing loop in the override of the run method. The
         * exception has an event field to indicate the event triggering the
         * pause.
         */
        private final class PauseRequiredException extends Exception {

            private static final long serialVersionUID = 1L;
            private final Event event;

            /**
             * Constructs a pause required exception.
             *
             * @param message A message explaining the exception.
             * @param event   The event associated with the exception.
             */
            private PauseRequiredException(String message, Event event) {
                super(message);
                this.event = event;
            }

            /**
             * Constructs a pause required exception.
             *
             * @param message A message explaining the exception.
             * @param cause   The exception that caused this exception.
             * @param event   The event associated with the exception.
             */
            private PauseRequiredException(String message, Throwable cause, Event event) {
                super(message, cause);
                this.event = event;
            }

            /**
             * Gets the event associated with this pause required exception
             *
             * @return The event.
             */
            private Event getEvent() {
                return event;
            }

        }

    }

    /**
     * A representation of a data source being processed. Its methods are
     * synchronized because instances are published to code that runs in the
     * both the auto ingest task thread and in data source processor task
     * threads (see DataSourceProcessorCallback implementation in
     * AutoIngestTask).
     * <p>
     * Instances of this class are thread-safe.
     */
    private final static class DataSourceInfo {

        private enum Type {

            CELLEBRITE_REPORT_HANDSET,
            CELLEBRITE_REPORT_SIM,
            DISK_IMAGE,
            PHONE_IMAGE,
            CELLEBRITE_ANDROID_ZIP
        }

        final Type type;
        final String fileName;
        final String dataSourceFolderPath;
        boolean dspCompleted;
        DataSourceProcessorCallback.DataSourceProcessorResult result;
        final List<Content> content;
        final List<String> errorMessages;
        private String deviceId;

        DataSourceInfo(Type type, String fileName, String dataSourceFolderPath) {
            this.type = type;
            this.fileName = fileName;
            this.dataSourceFolderPath = dataSourceFolderPath;
            dspCompleted = false;
            result = DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS;
            content = new ArrayList<>();
            errorMessages = new ArrayList<>();
            deviceId = "";
        }

        String getFileName() {
            return fileName;
        }

        Type getType() {
            return type;
        }

        void setDataSourceProcessorResult(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> content) {
            dspCompleted = true;
            this.result = result;
            this.errorMessages.addAll(errorMessages);
            this.content.addAll(content);
        }

        boolean dataSourceProcessorCompleted() {
            return dspCompleted;
        }

        DataSourceProcessorCallback.DataSourceProcessorResult getDataSourceProcessorResult() {
            return result;
        }

        List<String> getErrorMessages() {
            return new ArrayList<>(errorMessages);
        }

        List<Content> getDataSourceContent() {
            return new ArrayList<>(content);
        }

        String getDeviceId() {
            return deviceId;
        }

        void setDeviceID(String deviceId) {
            this.deviceId = deviceId;
        }

        String getDataSourceFolderPath() {
            return dataSourceFolderPath;
        }
    }

    private final static class FileFilters {

        private static final List<String> CELLEBRITE_EXTS = Arrays.asList(new String[]{".bin"});
        private static final GeneralFilter binImageFilter = new GeneralFilter(CELLEBRITE_EXTS, "");
        private static final List<FileFilter> cellebriteImageFiltersList = new ArrayList<>();

        static {
            cellebriteImageFiltersList.add(binImageFilter);
        }

        private static final GeneralFilter rawImageFilter = new GeneralFilter(GeneralFilter.RAW_IMAGE_EXTS, GeneralFilter.RAW_IMAGE_DESC);
        private static final GeneralFilter encaseImageFilter = new GeneralFilter(GeneralFilter.ENCASE_IMAGE_EXTS, GeneralFilter.ENCASE_IMAGE_DESC);
        private static final List<FileFilter> imageFiltersList = new ArrayList<>();

        static {
            imageFiltersList.add(rawImageFilter);
            imageFiltersList.add(encaseImageFilter);
        }

        private static final List<FileFilter> cellebriteXmlFiltersList = CellebriteXMLProcessor.getFileFilterList();

        private static final List<String> ARCHIVE_EXTS = Arrays.asList(new String[]{".zip"});
        private static final GeneralFilter archiveFilter = new GeneralFilter(ARCHIVE_EXTS, "");
        private static final List<FileFilter> archiveFiltersList = new ArrayList<>();

        static {
            archiveFiltersList.add(archiveFilter);
        }

        /**
         * Tests whether or not a file name is the name of a Cellebrite report
         * file.
         *
         * @param fileName   The file name.
         * @param folderPath full path to folder containing the XML file
         *
         * @return Type of Cellebrite report if input is valid Cellebrite XML
         *         report, null otherwise
         */
        private static DataSourceInfo.Type getCellebriteXmlReportType(String fileName, Path folderPath) {
            if (!isAcceptedByFiler(new File(fileName), cellebriteXmlFiltersList)) {
                return null;
            }

            File cellebriteXmlFile = Paths.get(folderPath.toString(), fileName).toFile();
            // read XML header info that is common for both handset and SIM Cellebrite v3.0 reports
            String report_type;
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(cellebriteXmlFile);
                XPathFactory xPathfactory = XPathFactory.newInstance();
                XPath xpath = xPathfactory.newXPath();
                XPathExpression expr = xpath.compile("/reports/report/general_information/report_type/text()");
                report_type = (String) expr.evaluate(doc, XPathConstants.STRING);
                if (report_type.equalsIgnoreCase("sim")) {
                    return DataSourceInfo.Type.CELLEBRITE_REPORT_SIM;
                } else if (report_type.equalsIgnoreCase("cell")) {
                    return DataSourceInfo.Type.CELLEBRITE_REPORT_HANDSET;
                } else {
                    return null;
                }
            } catch (Exception ignore) {
                // not a valid cellebrite XML file
                return null;
            }
        }

        private static boolean isFirstFileOfDiskOrPhoneImage(String fileName) {

            // is file a disk image or phone image (and is NOT part of split image)
            if (!isAcceptedByFiler(new File(fileName), imageFiltersList)) {
                return false;
            }

            return !isPartOfSplitCellebriteImage(fileName);
        }

        private static boolean isAcceptedByFiler(File file, List<FileFilter> filters) {

            for (FileFilter filter : filters) {
                if (filter.accept(file)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isPartOfSplitCellebriteImage(String fileName) {

            // only need to worry about ".bin" images
            if (!isAcceptedByFiler(new File(fileName), cellebriteImageFiltersList)) {
                return false;
            }

            // this needs to identify and handle different Cellebrite scenarios:
            //  i  single image in a single file
            // ii. Single image split over multiple files - just need to pass the first to TSK and it will combine the split image files.
            //       Note there may be more than  than one split images in a single dir, 
            //       e.g. blk0_mmcblk0.bin, blk0_mmcblk0(1).bin......, and blk24_mmcblk1.bin, blk24_mmcblk1(1).bin......
            //iii. Multiple image files - one per volume - need to handle each one separately
            //       e.g. blk0_mmcblk0.bin, mtd0_system.bin, mtd1_cache.bin, mtd2_userdata.bin
            String fNameNoExt = FilenameUtils.removeExtension(fileName.toLowerCase());
            return fNameNoExt.matches("\\w+\\(\\d+\\)");
        }

        private static String[] getAllFilesInFolder(String path) {
            // only returns files, skips folders
            File file = new File(path);
            String[] files = file.list((File current, String name) -> new File(current, name).isFile());
            return files;
        }

        private FileFilters() {
        }
    }

}
