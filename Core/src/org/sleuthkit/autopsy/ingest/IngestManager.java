/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.events.BlackboardPostEvent;
import org.sleuthkit.autopsy.ingest.events.ContentChangedEvent;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisCompletedEvent;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisStartedEvent;
import org.sleuthkit.autopsy.ingest.events.FileAnalyzedEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Manages the creation and execution of ingest jobs, i.e., the processing of
 * data sources by ingest modules.
 */
public class IngestManager {

    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private static IngestManager instance;
    private final Object ingestMessageBoxLock = new Object();

    /*
     * The ingest manager maintains a mapping of ingest job ids to running
     * ingest jobs.
     */
    private final Map<Long, IngestJob> jobsById;

    /*
     * Each runnable/callable task the ingest manager submits to its thread
     * pools is given a unique thread/task ID.
     */
    private final AtomicLong nextThreadId;

    /*
     * Ingest jobs may be queued to be started on a pool thread by start ingest
     * job tasks. A mapping of task ids to the Future objects for each task is
     * maintained to allow for task cancellation.
     */
    private final Map<Long, Future<Void>> startIngestJobTasks;
    private final ExecutorService startIngestJobsThreadPool;

    /*
     * Ingest jobs use an ingest task scheduler to break themselves down into
     * data source level and file level tasks. The ingest scheduler puts these
     * ingest tasks into queues for execution on ingest manager pool threads by
     * ingest task executers. There is a single data source level ingest thread
     * and a user configurable number of file level ingest threads.
     */
    private final ExecutorService dataSourceIngestThreadPool;
    private static final int MIN_NUMBER_OF_FILE_INGEST_THREADS = 1;
    private static final int MAX_NUMBER_OF_FILE_INGEST_THREADS = 16;
    private static final int DEFAULT_NUMBER_OF_FILE_INGEST_THREADS = 2;
    private int numberOfFileIngestThreads;
    private final ExecutorService fileIngestThreadPool;

    private static final String JOB_EVENT_CHANNEL_NAME = "%s-Ingest-Job-Events"; //NON-NLS
    private static final String MODULE_EVENT_CHANNEL_NAME = "%s-Ingest-Module-Events"; //NON-NLS
    private static final Set<String> jobEventNames = Stream.of(IngestJobEvent.values())
            .map(IngestJobEvent::toString)
            .collect(Collectors.toSet());
    private static final Set<String> moduleEventNames = Stream.of(IngestModuleEvent.values())
            .map(IngestModuleEvent::toString)
            .collect(Collectors.toSet());
    private AutopsyEventPublisher jobEventPublisher;
    private AutopsyEventPublisher moduleEventPublisher;
    private final ExecutorService eventPublishingExecutor;

    /*
     * The ingest manager uses an ingest monitor to determine when system
     * resources are under pressure. If the monitor detects such a situation, it
     * calls back to the ingest manager to cancel all ingest jobs in progress.
     */
    private final IngestMonitor ingestMonitor;

    /*
     * The ingest manager provides access to a top component that is used by
     * ingest module to post messages for the user. A count of the posts is used
     * as a cap to avoid bogging down the application.
     */
    private static final int MAX_ERROR_MESSAGE_POSTS = 200;
    private volatile IngestMessageTopComponent ingestMessageBox;
    private final AtomicLong ingestErrorMessagePosts;

    /*
     * The ingest manager supports reporting of ingest processing progress by
     * collecting snapshots of the activities of the ingest threads, ingest job
     * progress, and ingest module run times.
     */
    private final ConcurrentHashMap<Long, IngestThreadActivitySnapshot> ingestThreadActivitySnapshots;
    private final ConcurrentHashMap<String, Long> ingestModuleRunTimes;

    /*
     * The ingest job creation capability of the ingest manager can be turned on
     * and off to support an orderly shut down of the application.
     */
    private volatile boolean jobCreationIsEnabled;

    /*
     * Ingest manager subscribes to service outage notifications. If key
     * services are down, ingest manager cancels all ingest jobs in progress.
     */
    private final ServicesMonitor servicesMonitor;

    /**
     * Ingest job events.
     */
    public enum IngestJobEvent {

        /**
         * Property change event fired when an ingest job is started. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        STARTED,
        /**
         * Property change event fired when an ingest job is completed. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        COMPLETED,
        /**
         * Property change event fired when an ingest job is canceled. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        CANCELLED,
        /**
         * Property change event fired when analysis (ingest) of a data source
         * included in an ingest job is started. Both the old and new values of
         * the ProerptyChangeEvent are set to null - cast the
         * PropertyChangeEvent to
         * org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisStartedEvent to
         * access event data.
         */
        DATA_SOURCE_ANALYSIS_STARTED,
        /**
         * Property change event fired when analysis (ingest) of a data source
         * included in an ingest job is completed. Both the old and new values
         * of the ProerptyChangeEvent are set to null - cast the
         * PropertyChangeEvent to
         * org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisCompletedEvent
         * to access event data.
         */
        DATA_SOURCE_ANALYSIS_COMPLETED,
    };

    /**
     * Ingest module events.
     */
    public enum IngestModuleEvent {

        /**
         * Property change event fired when an ingest module adds new data to a
         * case, usually by posting to the blackboard. The old value of the
         * PropertyChangeEvent is a ModuleDataEvent object, and the new value is
         * set to null.
         */
        DATA_ADDED,
        /**
         * Property change event fired when an ingest module adds new content to
         * a case or changes a recorded attribute of existing content. For
         * example, if a module adds an extracted or carved file to a case, the
         * module should fire this event. The old value of the
         * PropertyChangeEvent is a ModuleContentEvent object, and the new value
         * is set to null.
         */
        CONTENT_CHANGED,
        /**
         * Property change event fired when the ingest of a file is completed.
         * The old value of the PropertyChangeEvent is the Autopsy object ID of
         * the file. The new value is the AbstractFile for that ID.
         */
        FILE_DONE,
    };

    /**
     * Gets the manager of the creation and execution of ingest jobs, i.e., the
     * processing of data sources by ingest modules.
     *
     * @return A singleton ingest manager object.
     */
    public synchronized static IngestManager getInstance() {
        if (instance == null) {
            /**
             * Two stage construction to avoid allowing the "this" reference to
             * be prematurely published from the constructor via the Case
             * property change listener.
             */
            instance = new IngestManager();
            instance.subscribeToCaseEvents();
        }
        return instance;
    }

    /**
     * Constructs a manager of the creation and execution of ingest jobs, i.e.,
     * the processing of data sources by ingest modules. The manager immediately
     * submits ingest task executers (Callable objects) to the data source level
     * ingest and file level ingest thread pools. These ingest task executers
     * are simple consumers that will normally run as long as the application
     * runs.
     */
    private IngestManager() {
        this.ingestModuleRunTimes = new ConcurrentHashMap<>();
        this.ingestThreadActivitySnapshots = new ConcurrentHashMap<>();
        this.ingestErrorMessagePosts = new AtomicLong(0L);
        this.ingestMonitor = new IngestMonitor();
        this.eventPublishingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("IM-ingest-events-%d").build()); //NON-NLS
        this.jobEventPublisher = new AutopsyEventPublisher();
        this.moduleEventPublisher = new AutopsyEventPublisher();
        this.dataSourceIngestThreadPool = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("IM-data-source-ingest-%d").build()); //NON-NLS
        this.startIngestJobsThreadPool = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("IM-start-ingest-jobs-%d").build()); //NON-NLS
        this.nextThreadId = new AtomicLong(0L);
        this.jobsById = new HashMap<>();
        this.startIngestJobTasks = new ConcurrentHashMap<>();

        this.servicesMonitor = ServicesMonitor.getInstance();
        subscribeToServiceMonitorEvents();

        this.startDataSourceIngestThread();

        numberOfFileIngestThreads = UserPreferences.numberOfFileIngestThreads();
        if ((numberOfFileIngestThreads < MIN_NUMBER_OF_FILE_INGEST_THREADS) || (numberOfFileIngestThreads > MAX_NUMBER_OF_FILE_INGEST_THREADS)) {
            numberOfFileIngestThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;
            UserPreferences.setNumberOfFileIngestThreads(numberOfFileIngestThreads);
        }
        fileIngestThreadPool = Executors.newFixedThreadPool(numberOfFileIngestThreads, new ThreadFactoryBuilder().setNameFormat("IM-file-ingest-%d").build()); //NON-NLS
        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
            startFileIngestThread();
        }
    }

    /**
     * Submits an ingest task executer Callable to the data source level ingest
     * thread pool.
     */
    private void startDataSourceIngestThread() {
        long threadId = nextThreadId.incrementAndGet();
        dataSourceIngestThreadPool.submit(new ExecuteIngestJobsTask(threadId, IngestTasksScheduler.getInstance().getDataSourceIngestTaskQueue()));
        ingestThreadActivitySnapshots.put(threadId, new IngestThreadActivitySnapshot(threadId));
    }

    /**
     * Submits a ingest task executer Callable to the file level ingest thread
     * pool.
     */
    private void startFileIngestThread() {
        long threadId = nextThreadId.incrementAndGet();
        fileIngestThreadPool.submit(new ExecuteIngestJobsTask(threadId, IngestTasksScheduler.getInstance().getFileIngestTaskQueue()));
        ingestThreadActivitySnapshots.put(threadId, new IngestThreadActivitySnapshot(threadId));
    }

    /**
     * Subscribes this ingest manager to local and remote case-related events.
     */
    private void subscribeToCaseEvents() {
        Case.addEventSubscriber(Case.Events.CURRENT_CASE.toString(), new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (event.getNewValue() != null) {
                    handleCaseOpened();
                } else {
                    handleCaseClosed();
                }
            }
        });
    }

    /**
     * Subscribe ingest manager to service monitor events. Cancels ingest if one
     * of services it's subscribed to goes down.
     */
    private void subscribeToServiceMonitorEvents() {
        PropertyChangeListener propChangeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getNewValue().equals(ServicesMonitor.ServiceStatus.DOWN.toString())) {

                    // check whether a multi-user case is currently being processed
                    try {
                        if (!Case.isCaseOpen() || Case.getCurrentCase().getCaseType() != Case.CaseType.MULTI_USER_CASE) {
                            return;
                        }
                    } catch (IllegalStateException ignore) {
                        // thorown by Case.getCurrentCase() when no case is open
                        return;
                    }

                    // one of the services we subscribed to went down                    
                    String serviceDisplayName = ServicesMonitor.Service.valueOf(evt.getPropertyName()).getDisplayName();
                    logger.log(Level.SEVERE, "Service {0} is down! Cancelling all running ingest jobs", serviceDisplayName); //NON-NLS                  

                    // display notification if running interactively
                    if (isIngestRunning() && RuntimeProperties.coreComponentsAreActive()) {
                        EventQueue.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(null,
                                        NbBundle.getMessage(this.getClass(), "IngestManager.cancellingIngest.msgDlg.text"),
                                        NbBundle.getMessage(this.getClass(), "IngestManager.serviceIsDown.msgDlg.text", serviceDisplayName),
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }

                    // cancel ingest if running
                    cancelAllIngestJobs(IngestJob.CancellationReason.SERVICES_DOWN);
                }
            }
        };

        // subscribe to services of interest
        Set<String> servicesList = new HashSet<>();
        servicesList.add(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString());
        servicesList.add(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString());
        this.servicesMonitor.addSubscriber(servicesList, propChangeListener);
    }

    synchronized void handleCaseOpened() {
        this.jobCreationIsEnabled = true;
        clearIngestMessageBox();
        try {
            /**
             * Use the text index name as the remote event channel name prefix
             * since it is unique, the same as the case database name for a
             * multiuser case, and is readily available through the
             * Case.getTextIndexName() API.
             */
            Case openedCase = Case.getCurrentCase();
            String channelPrefix = openedCase.getTextIndexName();
            if (Case.CaseType.MULTI_USER_CASE == openedCase.getCaseType()) {
                jobEventPublisher.openRemoteEventChannel(String.format(JOB_EVENT_CHANNEL_NAME, channelPrefix));
                moduleEventPublisher.openRemoteEventChannel(String.format(MODULE_EVENT_CHANNEL_NAME, channelPrefix));
            }
        } catch (IllegalStateException | AutopsyEventException ex) {
            logger.log(Level.SEVERE, "Failed to open remote events channel", ex); //NON-NLS
            MessageNotifyUtil.Notify.error(NbBundle.getMessage(IngestManager.class, "IngestManager.OpenEventChannel.Fail.Title"),
                    NbBundle.getMessage(IngestManager.class, "IngestManager.OpenEventChannel.Fail.ErrMsg"));
        }
    }

    synchronized void handleCaseClosed() {
        jobEventPublisher.closeRemoteEventChannel();
        moduleEventPublisher.closeRemoteEventChannel();
        this.jobCreationIsEnabled = false;
        clearIngestMessageBox();
    }

    /**
     * Deprecated, use RuntimeProperties.setCoreComponentsActive instead.
     *
     * @param runInteractively True or false
     *
     * @deprecated
     */
    @Deprecated
    public synchronized void setRunInteractively(boolean runInteractively) {
        RuntimeProperties.setCoreComponentsActive(runInteractively);
    }

    /**
     * Called by the custom installer for this package once the window system is
     * initialized, allowing the ingest manager to get the top component used to
     * display ingest messages.
     */
    void initIngestMessageInbox() {
        synchronized (this.ingestMessageBoxLock) {
            ingestMessageBox = IngestMessageTopComponent.findInstance();
        }
    }

    /**
     * Post a message to the ingest messages in box.
     *
     * @param message The message to be posted.
     */
    void postIngestMessage(IngestMessage message) {
        synchronized (this.ingestMessageBoxLock) {
            if (ingestMessageBox != null && RuntimeProperties.coreComponentsAreActive()) {
                if (message.getMessageType() != IngestMessage.MessageType.ERROR && message.getMessageType() != IngestMessage.MessageType.WARNING) {
                    ingestMessageBox.displayMessage(message);
                } else {
                    long errorPosts = ingestErrorMessagePosts.incrementAndGet();
                    if (errorPosts <= MAX_ERROR_MESSAGE_POSTS) {
                        ingestMessageBox.displayMessage(message);
                    } else if (errorPosts == MAX_ERROR_MESSAGE_POSTS + 1) {
                        IngestMessage errorMessageLimitReachedMessage = IngestMessage.createErrorMessage(
                                NbBundle.getMessage(this.getClass(), "IngestManager.IngestMessage.ErrorMessageLimitReached.title"),
                                NbBundle.getMessage(this.getClass(), "IngestManager.IngestMessage.ErrorMessageLimitReached.subject"),
                                NbBundle.getMessage(this.getClass(), "IngestManager.IngestMessage.ErrorMessageLimitReached.msg", MAX_ERROR_MESSAGE_POSTS));
                        ingestMessageBox.displayMessage(errorMessageLimitReachedMessage);
                    }
                }
            }
        }
    }

    private void clearIngestMessageBox() {
        synchronized (this.ingestMessageBoxLock) {
            if (ingestMessageBox != null) {
                ingestMessageBox.clearMessages();
            }
            ingestErrorMessagePosts.set(0);
        }
    }

    /**
     * Gets the number of file ingest threads the ingest manager will use to do
     * ingest jobs.
     *
     * @return The number of file ingest threads.
     */
    public int getNumberOfFileIngestThreads() {
        return numberOfFileIngestThreads;
    }

    /**
     * Queues an ingest job that will process a collection of data sources. The
     * job will be started on a worker thread.
     *
     * @param dataSources The data sources to process.
     * @param settings    The settings for the ingest job.
     */
    public void queueIngestJob(Collection<Content> dataSources, IngestJobSettings settings) {
        if (jobCreationIsEnabled) {
            IngestJob job = new IngestJob(dataSources, settings, RuntimeProperties.coreComponentsAreActive());
            if (job.hasIngestPipeline()) {
                long taskId = nextThreadId.incrementAndGet();
                Future<Void> task = startIngestJobsThreadPool.submit(new StartIngestJobTask(taskId, job));
                startIngestJobTasks.put(taskId, task);
            }
        }
    }

    /**
     * Starts an ingest job that will process a collection of data sources.
     *
     * @param dataSources The data sources to process.
     * @param settings    The settings for the ingest job.
     *
     * @return The IngestJobStartResult describing the results of attempting to
     *         start the ingest job.
     */
    public synchronized IngestJobStartResult beginIngestJob(Collection<Content> dataSources, IngestJobSettings settings) {
        if (this.jobCreationIsEnabled) {
            IngestJob job = new IngestJob(dataSources, settings, RuntimeProperties.coreComponentsAreActive());
            if (job.hasIngestPipeline()) {
                return this.startIngestJob(job); // Start job
            }
        }
        return new IngestJobStartResult(null, new IngestManagerException("Job creation is not enabled."), null);
    }

    /**
     * Starts an ingest job that will process a collection of data sources.
     *
     * @param dataSources The data sources to process.
     * @param settings    The settings for the ingest job.
     *
     * @return The ingest job that was started on success or null on failure.
     *
     * @Deprecated. Use beginIngestJob() instead.
     */
    @Deprecated
    public synchronized IngestJob startIngestJob(Collection<Content> dataSources, IngestJobSettings settings) {
        return beginIngestJob(dataSources, settings).getJob();
    }

    /**
     * Starts an ingest job for a collection of data sources.
     *
     * @param job The ingest job to start.
     *
     * @return The IngestJobStartResult describing the results of attempting to
     *         start the ingest job.
     */
    @NbBundle.Messages({
        "IngestManager.startupErr.dlgTitle=Ingest Module Startup Failure",
        "IngestManager.startupErr.dlgMsg=Unable to start up one or more ingest modules, ingest cancelled.",
        "IngestManager.startupErr.dlgSolution=Please disable the failed modules or fix the errors before restarting ingest.",
        "IngestManager.startupErr.dlgErrorList=Errors:"
    })
    private IngestJobStartResult startIngestJob(IngestJob job) {
        List<IngestModuleError> errors = null;
        if (this.jobCreationIsEnabled) {
            // multi-user cases must have multi-user database service running            
            if (Case.getCurrentCase().getCaseType() == Case.CaseType.MULTI_USER_CASE) {
                try {
                    if (!servicesMonitor.getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString()).equals(ServicesMonitor.ServiceStatus.UP.toString())) {
                        // display notification if running interactively
                        if (RuntimeProperties.coreComponentsAreActive()) {
                            EventQueue.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    String serviceDisplayName = ServicesMonitor.Service.REMOTE_CASE_DATABASE.getDisplayName();
                                    JOptionPane.showMessageDialog(null,
                                            NbBundle.getMessage(this.getClass(), "IngestManager.cancellingIngest.msgDlg.text"),
                                            NbBundle.getMessage(this.getClass(), "IngestManager.serviceIsDown.msgDlg.text", serviceDisplayName),
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            });
                        }
                        // abort ingest
                        return new IngestJobStartResult(null, new IngestManagerException("Ingest aborted. Remote database is down"), Collections.<IngestModuleError>emptyList());
                    }
                } catch (ServicesMonitor.ServicesMonitorException ex) {
                    return new IngestJobStartResult(null, new IngestManagerException("Database server is down.", ex), Collections.<IngestModuleError>emptyList());
                }
            }

            if (!ingestMonitor.isRunning()) {
                ingestMonitor.start();
            }

            synchronized (jobsById) {
                jobsById.put(job.getId(), job);
            }
            errors = job.start();
            if (errors.isEmpty()) {
                this.fireIngestJobStarted(job.getId());
                IngestManager.logger.log(Level.INFO, "Ingest job {0} started", job.getId()); //NON-NLS
            } else {
                synchronized (jobsById) {
                    this.jobsById.remove(job.getId());
                }
                for (IngestModuleError error : errors) {
                    logger.log(Level.SEVERE, String.format("Error starting %s ingest module for job %d", error.getModuleDisplayName(), job.getId()), error.getThrowable()); //NON-NLS
                }
                IngestManager.logger.log(Level.SEVERE, "Ingest job {0} could not be started", job.getId()); //NON-NLS
                if (RuntimeProperties.coreComponentsAreActive()) {
                    final StringBuilder message = new StringBuilder();
                    message.append(Bundle.IngestManager_startupErr_dlgMsg()).append("\n");
                    message.append(Bundle.IngestManager_startupErr_dlgSolution()).append("\n\n");
                    message.append(Bundle.IngestManager_startupErr_dlgErrorList()).append("\n");
                    for (IngestModuleError error : errors) {
                        String moduleName = error.getModuleDisplayName();
                        String errorMessage = error.getThrowable().getLocalizedMessage();
                        message.append(moduleName).append(": ").append(errorMessage).append("\n");
                    }
                    message.append("\n\n");
                    EventQueue.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, message, Bundle.IngestManager_startupErr_dlgTitle(), JOptionPane.ERROR_MESSAGE);
                    });
                }
                // abort ingest
                return new IngestJobStartResult(null, new IngestManagerException("Errors occurred while starting ingest"), errors);
            }
        }
        return new IngestJobStartResult(job, null, errors);
    }

    synchronized void finishIngestJob(IngestJob job) {
        long jobId = job.getId();
        synchronized (jobsById) {
            jobsById.remove(jobId);
        }
        if (!job.isCancelled()) {
            IngestManager.logger.log(Level.INFO, "Ingest job {0} completed", jobId); //NON-NLS
            fireIngestJobCompleted(jobId);
        } else {
            IngestManager.logger.log(Level.INFO, "Ingest job {0} cancelled", jobId); //NON-NLS
            fireIngestJobCancelled(jobId);
        }
    }

    /**
     * Queries whether or not any ingest jobs are in progress.
     *
     * @return True or false.
     */
    public boolean isIngestRunning() {
        synchronized (jobsById) {
            return !jobsById.isEmpty();
        }
    }

    /**
     * Cancels all ingest jobs in progress.
     *
     * @deprecated Use cancelAllIngestJobs(IngestJob.CancellationReason reason)
     * instead.
     */
    @Deprecated
    public void cancelAllIngestJobs() {
        cancelAllIngestJobs(IngestJob.CancellationReason.USER_CANCELLED);
    }

    /**
     * Cancels all ingest jobs in progress.
     *
     * @param reason The cancellation reason.
     */
    public void cancelAllIngestJobs(IngestJob.CancellationReason reason) {
        /*
         * Cancel the start job tasks.
         */
        for (Future<Void> handle : startIngestJobTasks.values()) {
            handle.cancel(true);
        }

        /*
         * Cancel the jobs in progress.
         */
        synchronized (jobsById) {
            for (IngestJob job : this.jobsById.values()) {
                job.cancel(reason);
            }
        }
    }

    /**
     * Adds an ingest job event property change listener.
     *
     * @param listener The PropertyChangeListener to register.
     */
    public void addIngestJobEventListener(final PropertyChangeListener listener) {
        jobEventPublisher.addSubscriber(jobEventNames, listener);
    }

    /**
     * Removes an ingest job event property change listener.
     *
     * @param listener The PropertyChangeListener to unregister.
     */
    public void removeIngestJobEventListener(final PropertyChangeListener listener) {
        jobEventPublisher.removeSubscriber(jobEventNames, listener);
    }

    /**
     * Adds an ingest module event property change listener.
     *
     * @param listener The PropertyChangeListener to register.
     */
    public void addIngestModuleEventListener(final PropertyChangeListener listener) {
        moduleEventPublisher.addSubscriber(moduleEventNames, listener);
    }

    /**
     * Removes an ingest module event property change listener.
     *
     * @param listener The PropertyChangeListener to unregister.
     */
    public void removeIngestModuleEventListener(final PropertyChangeListener listener) {
        moduleEventPublisher.removeSubscriber(moduleEventNames, listener);
    }

    /**
     * Adds an ingest job and ingest module event property change listener.
     *
     * @param listener The PropertyChangeListener to register.
     *
     * @deprecated Use addIngestJobEventListener() and/or
     * addIngestModuleEventListener().
     */
    @Deprecated
    public static void addPropertyChangeListener(final PropertyChangeListener listener) {
        instance.jobEventPublisher.addSubscriber(jobEventNames, listener);
        instance.moduleEventPublisher.addSubscriber(moduleEventNames, listener);
    }

    /**
     * Removes an ingest job and ingest module event property change listener.
     *
     * @param listener The PropertyChangeListener to unregister.
     *
     * @deprecated Use removeIngestJobEventListener() and/or
     * removeIngestModuleEventListener().
     */
    @Deprecated
    public static void removePropertyChangeListener(final PropertyChangeListener listener) {
        instance.jobEventPublisher.removeSubscriber(jobEventNames, listener);
        instance.moduleEventPublisher.removeSubscriber(moduleEventNames, listener);
    }

    /**
     * Fire an ingest event signifying an ingest job started.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobStarted(long ingestJobId) {
        AutopsyEvent event = new AutopsyEvent(IngestJobEvent.STARTED.toString(), ingestJobId, null);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Fire an ingest event signifying an ingest job finished.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCompleted(long ingestJobId) {
        AutopsyEvent event = new AutopsyEvent(IngestJobEvent.COMPLETED.toString(), ingestJobId, null);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Fire an ingest event signifying an ingest job was canceled.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCancelled(long ingestJobId) {
        AutopsyEvent event = new AutopsyEvent(IngestJobEvent.CANCELLED.toString(), ingestJobId, null);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Fire an ingest event signifying analysis of a data source started.
     *
     * @param ingestJobId           The ingest job id.
     * @param dataSourceIngestJobId The data source ingest job id.
     * @param dataSource            The data source.
     */
    void fireDataSourceAnalysisStarted(long ingestJobId, long dataSourceIngestJobId, Content dataSource) {
        AutopsyEvent event = new DataSourceAnalysisStartedEvent(ingestJobId, dataSourceIngestJobId, dataSource);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Fire an ingest event signifying analysis of a data source finished.
     *
     * @param ingestJobId           The ingest job id.
     * @param dataSourceIngestJobId The data source ingest job id.
     * @param dataSource            The data source.
     */
    void fireDataSourceAnalysisCompleted(long ingestJobId, long dataSourceIngestJobId, Content dataSource) {
        AutopsyEvent event = new DataSourceAnalysisCompletedEvent(ingestJobId, dataSourceIngestJobId, dataSource, DataSourceAnalysisCompletedEvent.Reason.ANALYSIS_COMPLETED);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Fire an ingest event signifying analysis of a data source was canceled.
     *
     * @param ingestJobId           The ingest job id.
     * @param dataSourceIngestJobId The data source ingest job id.
     * @param dataSource            The data source.
     */
    void fireDataSourceAnalysisCancelled(long ingestJobId, long dataSourceIngestJobId, Content dataSource) {
        AutopsyEvent event = new DataSourceAnalysisCompletedEvent(ingestJobId, dataSourceIngestJobId, dataSource, DataSourceAnalysisCompletedEvent.Reason.ANALYSIS_CANCELLED);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Fire an ingest event signifying the ingest of a file is completed.
     *
     * @param file The file that is completed.
     */
    void fireFileIngestDone(AbstractFile file) {
        AutopsyEvent event = new FileAnalyzedEvent(file);
        eventPublishingExecutor.submit(new PublishEventTask(event, moduleEventPublisher));
    }

    /**
     * Fire an event signifying a blackboard post by an ingest module.
     *
     * @param moduleDataEvent A ModuleDataEvent with the details of the posting.
     */
    void fireIngestModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        AutopsyEvent event = new BlackboardPostEvent(moduleDataEvent);
        eventPublishingExecutor.submit(new PublishEventTask(event, moduleEventPublisher));
    }

    /**
     * Fire an event signifying discovery of additional content by an ingest
     * module.
     *
     * @param moduleDataEvent A ModuleContentEvent with the details of the new
     *                        content.
     */
    void fireIngestModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        AutopsyEvent event = new ContentChangedEvent(moduleContentEvent);
        eventPublishingExecutor.submit(new PublishEventTask(event, moduleEventPublisher));
    }

    /**
     * Called each time a module in a data source pipeline starts
     *
     * @param task
     * @param ingestModuleDisplayName
     */
    void setIngestTaskProgress(DataSourceIngestTask task, String ingestModuleDisplayName) {
        ingestThreadActivitySnapshots.put(task.getThreadId(), new IngestThreadActivitySnapshot(task.getThreadId(), task.getIngestJob().getId(), ingestModuleDisplayName, task.getDataSource()));
    }

    /**
     * Called each time a module in a file ingest pipeline starts
     *
     * @param task
     * @param ingestModuleDisplayName
     */
    void setIngestTaskProgress(FileIngestTask task, String ingestModuleDisplayName) {
        IngestThreadActivitySnapshot prevSnap = ingestThreadActivitySnapshots.get(task.getThreadId());
        IngestThreadActivitySnapshot newSnap = new IngestThreadActivitySnapshot(task.getThreadId(), task.getIngestJob().getId(), ingestModuleDisplayName, task.getDataSource(), task.getFile());
        ingestThreadActivitySnapshots.put(task.getThreadId(), newSnap);

        incrementModuleRunTime(prevSnap.getActivity(), newSnap.getStartTime().getTime() - prevSnap.getStartTime().getTime());
    }

    /**
     * Called each time a data source ingest task completes
     *
     * @param task
     */
    void setIngestTaskProgressCompleted(DataSourceIngestTask task) {
        ingestThreadActivitySnapshots.put(task.getThreadId(), new IngestThreadActivitySnapshot(task.getThreadId()));
    }

    /**
     * Called when a file ingest pipeline is complete for a given file
     *
     * @param task
     */
    void setIngestTaskProgressCompleted(FileIngestTask task) {
        IngestThreadActivitySnapshot prevSnap = ingestThreadActivitySnapshots.get(task.getThreadId());
        IngestThreadActivitySnapshot newSnap = new IngestThreadActivitySnapshot(task.getThreadId());
        ingestThreadActivitySnapshots.put(task.getThreadId(), newSnap);
        incrementModuleRunTime(prevSnap.getActivity(), newSnap.getStartTime().getTime() - prevSnap.getStartTime().getTime());
    }

    /**
     * Internal method to update the times associated with each module.
     *
     * @param moduleName
     * @param duration
     */
    private void incrementModuleRunTime(String moduleName, Long duration) {
        if (moduleName.equals("IDLE")) { //NON-NLS
            return;
        }

        synchronized (ingestModuleRunTimes) {
            Long prevTimeL = ingestModuleRunTimes.get(moduleName);
            long prevTime = 0;
            if (prevTimeL != null) {
                prevTime = prevTimeL;
            }
            prevTime += duration;
            ingestModuleRunTimes.put(moduleName, prevTime);
        }
    }

    /**
     * Return the list of run times for each module
     *
     * @return Map of module name to run time (in milliseconds)
     */
    Map<String, Long> getModuleRunTimes() {
        synchronized (ingestModuleRunTimes) {
            Map<String, Long> times = new HashMap<>(ingestModuleRunTimes);
            return times;
        }
    }

    /**
     * Get the stats on current state of each thread
     *
     * @return
     */
    List<IngestThreadActivitySnapshot> getIngestThreadActivitySnapshots() {
        return new ArrayList<>(ingestThreadActivitySnapshots.values());
    }

    /**
     * Gets snapshots of the state of all running ingest jobs.
     *
     * @return A list of ingest job state snapshots.
     */
    List<DataSourceIngestJob.Snapshot> getIngestJobSnapshots() {
        List<DataSourceIngestJob.Snapshot> snapShots = new ArrayList<>();
        synchronized (jobsById) {
            for (IngestJob job : jobsById.values()) {
                snapShots.addAll(job.getDataSourceIngestJobSnapshots());
            }
        }
        return snapShots;
    }

    /**
     * Get the free disk space of the drive where to which ingest data is being
     * written, as reported by the ingest monitor.
     *
     * @return Free disk space, -1 if unknown
     */
    long getFreeDiskSpace() {
        if (ingestMonitor != null) {
            return ingestMonitor.getFreeSpace();
        } else {
            return -1;
        }
    }

    /**
     * Creates and starts an ingest job for a collection of data sources.
     */
    private final class StartIngestJobTask implements Callable<Void> {

        private final long threadId;
        private final IngestJob job;
        private ProgressHandle progress;

        StartIngestJobTask(long threadId, IngestJob job) {
            this.threadId = threadId;
            this.job = job;
        }

        @Override
        public Void call() {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    synchronized (jobsById) {
                        jobsById.remove(job.getId());
                    }
                    return null;
                }

                if (RuntimeProperties.coreComponentsAreActive()) {
                    final String displayName = NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsTask.run.displayName");
                    this.progress = ProgressHandle.createHandle(displayName, new Cancellable() {
                        @Override
                        public boolean cancel() {
                            if (progress != null) {
                                progress.setDisplayName(NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsTask.run.cancelling", displayName));
                            }
                            Future<?> handle = startIngestJobTasks.remove(threadId);
                            handle.cancel(true);
                            return true;
                        }
                    });
                    progress.start();
                }

                startIngestJob(job);
                return null;

            } finally {
                if (null != progress) {
                    progress.finish();
                }
                startIngestJobTasks.remove(threadId);
            }
        }

    }

    /**
     * Executes ingest jobs by acting as a consumer for an ingest tasks queue.
     */
    private final class ExecuteIngestJobsTask implements Runnable {

        private final long threadId;
        private final IngestTaskQueue tasks;

        ExecuteIngestJobsTask(long threadId, IngestTaskQueue tasks) {
            this.threadId = threadId;
            this.tasks = tasks;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    IngestTask task = tasks.getNextTask(); // Blocks.
                    task.execute(threadId);
                } catch (InterruptedException ex) {
                    break;
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
    }

    /**
     * Publishes ingest events to both local and remote subscribers.
     */
    private static final class PublishEventTask implements Runnable {

        private final AutopsyEvent event;
        private final AutopsyEventPublisher publisher;

        /**
         * Constructs an object that publishes ingest events to both local and
         * remote subscribers.
         *
         * @param event     The event to publish.
         * @param publisher The event publisher.
         */
        PublishEventTask(AutopsyEvent event, AutopsyEventPublisher publisher) {
            this.event = event;
            this.publisher = publisher;
        }

        /**
         * @inheritDoc
         */
        @Override
        public void run() {
            publisher.publish(event);
        }

    }

    static final class IngestThreadActivitySnapshot {

        private final long threadId;
        private final Date startTime;
        private final String activity;
        private final String dataSourceName;
        private final String fileName;
        private final long jobId;

        // nothing is running on the thread
        IngestThreadActivitySnapshot(long threadId) {
            this.threadId = threadId;
            startTime = new Date();
            this.activity = NbBundle.getMessage(this.getClass(), "IngestManager.IngestThreadActivitySnapshot.idleThread");
            this.dataSourceName = "";
            this.fileName = "";
            this.jobId = 0;
        }

        // data souce thread
        IngestThreadActivitySnapshot(long threadId, long jobId, String activity, Content dataSource) {
            this.threadId = threadId;
            this.jobId = jobId;
            startTime = new Date();
            this.activity = activity;
            this.dataSourceName = dataSource.getName();
            this.fileName = "";
        }

        // file ingest thread
        IngestThreadActivitySnapshot(long threadId, long jobId, String activity, Content dataSource, AbstractFile file) {
            this.threadId = threadId;
            this.jobId = jobId;
            startTime = new Date();
            this.activity = activity;
            this.dataSourceName = dataSource.getName();
            this.fileName = file.getName();
        }

        long getJobId() {
            return jobId;
        }

        long getThreadId() {
            return threadId;
        }

        Date getStartTime() {
            return startTime;
        }

        String getActivity() {
            return activity;
        }

        String getDataSourceName() {
            return dataSourceName;
        }

        String getFileName() {
            return fileName;
        }

    }

    /**
     * An exception thrown by the ingest manager.
     */
    public final static class IngestManagerException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Creates an exception containing an error message.
         *
         * @param message The message.
         */
        private IngestManagerException(String message) {
            super(message);
        }

        /**
         * Creates an exception containing an error message and a cause.
         *
         * @param message The message
         * @param cause   The cause.
         */
        private IngestManagerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
