/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
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
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
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
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Manages the creation and execution of ingest jobs, i.e., the processing of
 * data sources by ingest modules.
 *
 * Every ingest job that is submitted to the ingest manager is passed to an
 * ingest task scheduler to be broken down into data source level and file level
 * ingest job tasks. The ingest job tasks that are put into queues for execution
 * by the ingest manager's executors. The process of starting an ingest job is
 * handled by a single-threaded executor, the processing of data source level
 * ingest tasks is handled by another single-threaded executor, and the
 * processing of file level ingest jobs is handled by an executor with a
 * configurable number of threads.
 *
 * The ingest manager publishes two kinds of application events: ingest job
 * events and ingest module events. Ingest job events are published when an
 * ingest job changes states, e.g., an ingest job is started or completed.
 * Ingest module events are published on behalf of ingest modules working on an
 * ingest job, when content or an artifact is added to the current case. Each of
 * the two event types is handled by a separate event publisher with its own
 * remote event channel, but all event publishing is handled by a dedicated,
 * single-threaded executor.
 *
 * The ingest manager uses an ingest monitor to determine when system resources
 * are under pressure. If the ingest monitor detects such a situation, it calls
 * back to the ingest manager to cancel all ingest jobs in progress.
 *
 * The ingest manager uses a service monitor to watch for service outages. If a
 * key services goes down, the ingest manager cancels all ingest jobs in
 * progress.
 *
 * The ingest manager provides access to a top component that is used as in
 * "inbox" by ingest modules for the purpose of posting messages for the user. A
 * count of the posts is used to enforce a cap on the number of messages posted,
 * to avoid bogging down the application.
 *
 * The ingest manager supports reporting of ingest processing progress by
 * collecting snapshots of the activities of the ingest threads, overall ingest
 * job progress, and ingest module run times.
 */
@ThreadSafe
public class IngestManager implements IngestProgressSnapshotProvider {

    private final static Logger logger = Logger.getLogger(IngestManager.class.getName());
    private final static String INGEST_JOB_EVENT_CHANNEL_NAME = "%s-Ingest-Job-Events"; //NON-NLS
    private final static Set<String> INGEST_JOB_EVENT_NAMES = Stream.of(IngestJobEvent.values()).map(IngestJobEvent::toString).collect(Collectors.toSet());
    private final static String INGEST_MODULE_EVENT_CHANNEL_NAME = "%s-Ingest-Module-Events"; //NON-NLS
    private final static Set<String> INGEST_MODULE_EVENT_NAMES = Stream.of(IngestModuleEvent.values()).map(IngestModuleEvent::toString).collect(Collectors.toSet());
    private final static int MAX_ERROR_MESSAGE_POSTS = 200;
    @GuardedBy("IngestManager.class")
    private static IngestManager instance;
    private final int numberOfFileIngestThreads;
    private final AtomicLong nextIngestManagerTaskId = new AtomicLong(0L);
    private final ExecutorService startIngestJobsExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("IM-start-ingest-jobs-%d").build()); //NON-NLS;
    @GuardedBy("startIngestJobFutures")
    private final Map<Long, Future<Void>> startIngestJobFutures = new ConcurrentHashMap<>();
    @GuardedBy("ingestJobsById")
    private final Map<Long, IngestJob> ingestJobsById = new HashMap<>();
    private final ExecutorService dataSourceLevelIngestJobTasksExecutor;
    private final ExecutorService fileLevelIngestJobTasksExecutor;
    private final ExecutorService resultIngestTasksExecutor;
    private final ExecutorService eventPublishingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("IM-ingest-events-%d").build()); //NON-NLS;
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    private final ServicesMonitor servicesMonitor = ServicesMonitor.getInstance();
    private final AutopsyEventPublisher jobEventPublisher = new AutopsyEventPublisher();
    private final AutopsyEventPublisher moduleEventPublisher = new AutopsyEventPublisher();
    private final Object ingestMessageBoxLock = new Object();
    private final AtomicLong ingestErrorMessagePosts = new AtomicLong(0L);
    private final ConcurrentHashMap<Long, IngestThreadActivitySnapshot> ingestThreadActivitySnapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ingestModuleRunTimes = new ConcurrentHashMap<>();
    private volatile IngestMessageTopComponent ingestMessageBox;
    private volatile boolean caseIsOpen;

    /**
     * Gets the manager of the creation and execution of ingest jobs, i.e., the
     * processing of data sources by ingest modules.
     *
     * @return A singleton ingest manager object.
     */
    public synchronized static IngestManager getInstance() {
        if (null == instance) {
            instance = new IngestManager();
            instance.subscribeToServiceMonitorEvents();
            instance.subscribeToCaseEvents();
        }
        return instance;
    }

    /**
     * Constructs a manager of the creation and execution of ingest jobs, i.e.,
     * the processing of data sources by ingest modules.
     */
    private IngestManager() {
        /*
         * Submit a single Runnable ingest manager task for processing data
         * source level ingest job tasks to the data source level ingest job
         * tasks executor.
         */
        dataSourceLevelIngestJobTasksExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("IM-data-source-ingest-%d").build()); //NON-NLS;        
        long threadId = nextIngestManagerTaskId.incrementAndGet();
        dataSourceLevelIngestJobTasksExecutor.submit(new ExecuteIngestJobTasksTask(threadId, IngestTasksScheduler.getInstance().getDataSourceIngestTaskQueue()));
        ingestThreadActivitySnapshots.put(threadId, new IngestThreadActivitySnapshot(threadId));

        /*
         * Submit a configurable number of Runnable ingest manager tasks for
         * processing file level ingest job tasks to the file level ingest job
         * tasks executor.
         */
        numberOfFileIngestThreads = UserPreferences.numberOfFileIngestThreads();
        fileLevelIngestJobTasksExecutor = Executors.newFixedThreadPool(numberOfFileIngestThreads, new ThreadFactoryBuilder().setNameFormat("IM-file-ingest-%d").build()); //NON-NLS
        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
            threadId = nextIngestManagerTaskId.incrementAndGet();
            fileLevelIngestJobTasksExecutor.submit(new ExecuteIngestJobTasksTask(threadId, IngestTasksScheduler.getInstance().getFileIngestTaskQueue()));
            ingestThreadActivitySnapshots.put(threadId, new IngestThreadActivitySnapshot(threadId));
        }

        resultIngestTasksExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("IM-results-ingest-%d").build()); //NON-NLS;        
        threadId = nextIngestManagerTaskId.incrementAndGet();
        resultIngestTasksExecutor.submit(new ExecuteIngestJobTasksTask(threadId, IngestTasksScheduler.getInstance().getResultIngestTaskQueue()));
        // RJCTODO
        // ingestThreadActivitySnapshots.put(threadId, new IngestThreadActivitySnapshot(threadId));
        // RJCTODO: Where is the shut down code?
    }

    /**
     * Subscribes the ingest manager to events published by its service monitor.
     * The event handler cancels all ingest jobs if a key service goes down.
     */
    private void subscribeToServiceMonitorEvents() {
        PropertyChangeListener propChangeListener = (PropertyChangeEvent evt) -> {
            if (evt.getNewValue().equals(ServicesMonitor.ServiceStatus.DOWN.toString())) {
                /*
                 * The application services considered to be key services are
                 * only necessary for multi-user cases.
                 */
                try {
                    if (Case.getCurrentCaseThrows().getCaseType() != Case.CaseType.MULTI_USER_CASE) {
                        return;
                    }
                } catch (NoCurrentCaseException noCaseOpenException) {
                    return;
                }

                String serviceDisplayName = ServicesMonitor.Service.valueOf(evt.getPropertyName()).getDisplayName();
                logger.log(Level.SEVERE, "Service {0} is down, cancelling all running ingest jobs", serviceDisplayName); //NON-NLS
                if (isIngestRunning() && RuntimeProperties.runningWithGUI()) {
                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                    NbBundle.getMessage(this.getClass(), "IngestManager.cancellingIngest.msgDlg.text"),
                                    NbBundle.getMessage(this.getClass(), "IngestManager.serviceIsDown.msgDlg.text", serviceDisplayName),
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
                cancelAllIngestJobs(IngestJob.CancellationReason.SERVICES_DOWN);
            }
        };

        /*
         * The key services for multi-user cases are currently the case database
         * server and the Solr server. The Solr server is a key service not
         * because search is essential, but because the coordination service
         * (ZooKeeper) is running embedded within the Solr server.
         */
        Set<String> servicesList = new HashSet<>();
        servicesList.add(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString());
        servicesList.add(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString());
        this.servicesMonitor.addSubscriber(servicesList, propChangeListener);
    }

    /**
     * Subscribes the ingest manager to current case (current case
     * opened/closed) events.
     */
    private void subscribeToCaseEvents() {
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent event) -> {
            if (event.getNewValue() != null) {
                handleCaseOpened();
            } else {
                handleCaseClosed();
            }
        });
    }

    /**
     * Handles a current case opened event by clearing the ingest messages
     * inbox, opening a remote event channel for the current case, and
     * registering to receive events from the event bus for the case database.
     *
     * Note that current case change events are published in a strictly
     * serialized manner, i.e., one event at a time, synchronously.
     */
    void handleCaseOpened() {
        caseIsOpen = true;
        clearIngestMessageBox();
        try {
            Case openedCase = Case.getCurrentCaseThrows();
            String channelPrefix = openedCase.getName();
            if (Case.CaseType.MULTI_USER_CASE == openedCase.getCaseType()) {
                jobEventPublisher.openRemoteEventChannel(String.format(INGEST_JOB_EVENT_CHANNEL_NAME, channelPrefix));
                moduleEventPublisher.openRemoteEventChannel(String.format(INGEST_MODULE_EVENT_CHANNEL_NAME, channelPrefix));
            }
            openedCase.getSleuthkitCase().registerForEvents(this);
        } catch (NoCurrentCaseException | AutopsyEventException ex) {
            logger.log(Level.SEVERE, "Failed to open remote events channel", ex); //NON-NLS
            MessageNotifyUtil.Notify.error(NbBundle.getMessage(IngestManager.class, "IngestManager.OpenEventChannel.Fail.Title"),
                    NbBundle.getMessage(IngestManager.class, "IngestManager.OpenEventChannel.Fail.ErrMsg"));
        }
    }

    /**
     * Handles artifacts posted events published by the Sleuth Kit layer
     * blackboard via the event bus for the case database.
     *
     * @param tskEvent A Sleuth Kit data model ArtifactsPostedEvent from the
     *                 case database event bus.
     */
    @Subscribe
    void handleArtifactsPosted(Blackboard.ArtifactsPostedEvent tskEvent) {
        for (BlackboardArtifact.Type artifactType : tskEvent.getArtifactTypes()) {
            ModuleDataEvent legacyEvent = new ModuleDataEvent(tskEvent.getModuleName(), artifactType, tskEvent.getArtifacts(artifactType));
            AutopsyEvent autopsyEvent = new BlackboardPostEvent(legacyEvent);
            eventPublishingExecutor.submit(new PublishEventTask(autopsyEvent, moduleEventPublisher));
        }
    }

    /**
     * Handles a current case closed event by cancelling all ingest jobs for the
     * case, unregistering from receiving events from the case database, closing
     * the remote event channel for the case, and clearing the ingest messages
     * inbox.
     *
     * Note that current case change events are published in a strictly
     * serialized manner, i.e., one event at a time, synchronously.
     */
    void handleCaseClosed() {
        /*
         * TODO (JIRA-2227): IngestManager should wait for cancelled ingest jobs
         * to complete when a case is closed.
         */
        cancelAllIngestJobs(IngestJob.CancellationReason.CASE_CLOSED);
        Case.getCurrentCase().getSleuthkitCase().unregisterForEvents(this);
        jobEventPublisher.closeRemoteEventChannel();
        moduleEventPublisher.closeRemoteEventChannel();
        caseIsOpen = false;
        clearIngestMessageBox();
    }

    /**
     * Creates an ingest stream from the given ingest settings for a data
     * source.
     *
     * @param dataSource The data source
     * @param settings   The ingest job settings.
     *
     * @return The newly created ingest stream.
     *
     * @throws TskCoreException if there was an error starting the ingest job.
     */
    public IngestStream openIngestStream(DataSource dataSource, IngestJobSettings settings) throws TskCoreException {
        IngestJob job = new IngestJob(dataSource, IngestJob.Mode.STREAMING, settings);
        IngestJobInputStream stream = new IngestJobInputStream(job);
        if (stream.getIngestJobStartResult().getJob() != null) {
            return stream;
        } else if (stream.getIngestJobStartResult().getModuleErrors().isEmpty()) {
            for (IngestModuleError error : stream.getIngestJobStartResult().getModuleErrors()) {
                logger.log(Level.SEVERE, String.format("%s ingest module startup error for %s", error.getModuleDisplayName(), dataSource.getName()), error.getThrowable());
            }
            throw new TskCoreException("Error starting ingest modules");
        } else {
            throw new TskCoreException("Error starting ingest modules", stream.getIngestJobStartResult().getStartupException());
        }
    }

    /**
     * Gets the number of file ingest threads the ingest manager is using to do
     * ingest jobs.
     *
     * @return The number of file ingest threads.
     */
    public int getNumberOfFileIngestThreads() {
        return numberOfFileIngestThreads;
    }

    /**
     * Queues an ingest job for for one or more data sources.
     *
     * @param dataSources The data sources to analyze.
     * @param settings    The settings for the ingest job.
     */
    public void queueIngestJob(Collection<Content> dataSources, IngestJobSettings settings) {
        if (caseIsOpen) {
            IngestJob job = new IngestJob(dataSources, settings);
            if (job.hasIngestPipeline()) {
                long taskId = nextIngestManagerTaskId.incrementAndGet();
                Future<Void> task = startIngestJobsExecutor.submit(new StartIngestJobTask(taskId, job));
                synchronized (startIngestJobFutures) {
                    startIngestJobFutures.put(taskId, task);
                }
            }
        }
    }

    /**
     * Queues an ingest job for for a data source. Either all of the files in
     * the data source or a given subset of the files will be analyzed.
     *
     * @param dataSource The data source to analyze.
     * @param files      A subset of the files for the data source.
     * @param settings   The settings for the ingest job.
     */
    public void queueIngestJob(Content dataSource, List<AbstractFile> files, IngestJobSettings settings) {
        if (caseIsOpen) {
            IngestJob job = new IngestJob(dataSource, files, settings);
            if (job.hasIngestPipeline()) {
                long taskId = nextIngestManagerTaskId.incrementAndGet();
                Future<Void> task = startIngestJobsExecutor.submit(new StartIngestJobTask(taskId, job));
                synchronized (startIngestJobFutures) {
                    startIngestJobFutures.put(taskId, task);
                }
            }
        }
    }

    /**
     * Immediately starts an ingest job for one or more data sources.
     *
     * @param dataSources The data sources to process.
     * @param settings    The settings for the ingest job.
     *
     * @return The IngestJobStartResult describing the results of attempting to
     *         start the ingest job.
     */
    public IngestJobStartResult beginIngestJob(Collection<Content> dataSources, IngestJobSettings settings) {
        if (caseIsOpen) {
            IngestJob job = new IngestJob(dataSources, settings);
            if (job.hasIngestPipeline()) {
                return startIngestJob(job);
            }
            return new IngestJobStartResult(null, new IngestManagerException("No ingest pipeline created, likely due to no ingest modules being enabled"), null); //NON-NLS
        }
        return new IngestJobStartResult(null, new IngestManagerException("No case open"), null); //NON-NLS
    }

    /**
     * Starts an ingest job for one or more data sources.
     *
     * @param job The ingest job to start.
     *
     * @return An IngestJobStartResult describing the results of attempting to
     *         start the ingest job.
     */
    @NbBundle.Messages({
        "IngestManager.startupErr.dlgTitle=Ingest Module Startup Failure",
        "IngestManager.startupErr.dlgMsg=Unable to start up one or more ingest modules, ingest cancelled.",
        "IngestManager.startupErr.dlgSolution=Please disable the failed modules or fix the errors before restarting ingest.",
        "IngestManager.startupErr.dlgErrorList=Errors:"
    })
    IngestJobStartResult startIngestJob(IngestJob job) {

        // initialize IngestMessageInbox, if it hasn't been initialized yet. This can't be done in
        // the constructor because that ends up freezing the UI on startup (JIRA-7345).
        if (SwingUtilities.isEventDispatchThread()) {
            initIngestMessageInbox();
        } else {
            try {
                SwingUtilities.invokeAndWait(() -> initIngestMessageInbox());
            } catch (InterruptedException ex) {
                // ignore interruptions
            } catch (InvocationTargetException ex) {
                logger.log(Level.WARNING, "There was an error starting ingest message inbox", ex);
            }
        }

        List<IngestModuleError> errors = null;
        Case openCase;
        try {
            openCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            return new IngestJobStartResult(null, new IngestManagerException("Exception while getting open case.", ex), Collections.<IngestModuleError>emptyList()); //NON-NLS
        }
        if (openCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            try {
                if (!servicesMonitor.getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString()).equals(ServicesMonitor.ServiceStatus.UP.toString())) {
                    if (RuntimeProperties.runningWithGUI()) {
                        EventQueue.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                String serviceDisplayName = ServicesMonitor.Service.REMOTE_CASE_DATABASE.getDisplayName();
                                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                        NbBundle.getMessage(this.getClass(), "IngestManager.cancellingIngest.msgDlg.text"),
                                        NbBundle.getMessage(this.getClass(), "IngestManager.serviceIsDown.msgDlg.text", serviceDisplayName),
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                    return new IngestJobStartResult(null, new IngestManagerException("Ingest aborted. Remote database is down"), Collections.<IngestModuleError>emptyList()); //NON-NLS
                }
            } catch (ServicesMonitor.ServicesMonitorException ex) {
                return new IngestJobStartResult(null, new IngestManagerException("Database server is down", ex), Collections.<IngestModuleError>emptyList()); //NON-NLS
            }
        }

        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }

        synchronized (ingestJobsById) {
            ingestJobsById.put(job.getId(), job);
        }
        IngestManager.logger.log(Level.INFO, "Starting ingest job {0}", job.getId()); //NON-NLS
        try {
            errors = job.start();
        } catch (InterruptedException ex) {
            return new IngestJobStartResult(null, new IngestManagerException("Interrupted while starting ingest", ex), errors); //NON-NLS
        }
        if (errors.isEmpty()) {
            this.fireIngestJobStarted(job.getId());
        } else {
            synchronized (ingestJobsById) {
                this.ingestJobsById.remove(job.getId());
            }
            for (IngestModuleError error : errors) {
                logger.log(Level.SEVERE, String.format("Error starting %s ingest module for job %d", error.getModuleDisplayName(), job.getId()), error.getThrowable()); //NON-NLS
            }
            IngestManager.logger.log(Level.SEVERE, "Ingest job {0} could not be started", job.getId()); //NON-NLS
            if (RuntimeProperties.runningWithGUI()) {
                final StringBuilder message = new StringBuilder(1024);
                message.append(Bundle.IngestManager_startupErr_dlgMsg()).append("\n"); //NON-NLS
                message.append(Bundle.IngestManager_startupErr_dlgSolution()).append("\n\n"); //NON-NLS
                message.append(Bundle.IngestManager_startupErr_dlgErrorList()).append("\n"); //NON-NLS
                for (IngestModuleError error : errors) {
                    String moduleName = error.getModuleDisplayName();
                    String errorMessage = error.getThrowable().getLocalizedMessage();
                    message.append(moduleName).append(": ").append(errorMessage).append("\n"); //NON-NLS
                }
                message.append("\n\n");
                EventQueue.invokeLater(() -> {
                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), message, Bundle.IngestManager_startupErr_dlgTitle(), JOptionPane.ERROR_MESSAGE);
                });
            }
            return new IngestJobStartResult(null, new IngestManagerException("Errors occurred while starting ingest"), errors); //NON-NLS
        }

        return new IngestJobStartResult(job, null, errors);
    }

    /**
     * Cleans up for a completed ingest job.
     *
     * @param job The completed job.
     */
    void finishIngestJob(IngestJob job
    ) {
        long jobId = job.getId();
        synchronized (ingestJobsById) {
            ingestJobsById.remove(jobId);
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
     * Queries whether or not any ingest jobs are in progress at the time of the
     * call.
     *
     * @return True or false.
     */
    public boolean isIngestRunning() {
        synchronized (ingestJobsById) {
            return !ingestJobsById.isEmpty();
        }
    }

    /**
     * Cancels all ingest jobs in progress.
     *
     * @param reason The cancellation reason.
     */
    public void cancelAllIngestJobs(IngestJob.CancellationReason reason) {
        synchronized (startIngestJobFutures) {
            startIngestJobFutures.values().forEach((handle) -> {
                handle.cancel(true);
            });
        }
        synchronized (ingestJobsById) {
            this.ingestJobsById.values().forEach((job) -> {
                job.cancel(reason);
            });
        }
    }

    /**
     * Adds an ingest job event property change listener.
     *
     * @param listener The PropertyChangeListener to be added.
     */
    public void addIngestJobEventListener(final PropertyChangeListener listener) {
        jobEventPublisher.addSubscriber(INGEST_JOB_EVENT_NAMES, listener);
    }

    /**
     * Adds an ingest job event property change listener for the given event
     * types.
     *
     * @param eventTypes The event types to listen for
     * @param listener   The PropertyChangeListener to be added
     */
    public void addIngestJobEventListener(Set<IngestJobEvent> eventTypes, final PropertyChangeListener listener) {
        eventTypes.forEach((IngestJobEvent event) -> {
            jobEventPublisher.addSubscriber(event.toString(), listener);
        });
    }

    /**
     * Removes an ingest job event property change listener.
     *
     * @param listener The PropertyChangeListener to be removed.
     */
    public void removeIngestJobEventListener(final PropertyChangeListener listener) {
        jobEventPublisher.removeSubscriber(INGEST_JOB_EVENT_NAMES, listener);
    }

    /**
     * Removes an ingest job event property change listener.
     *
     * @param eventTypes The event types to stop listening for
     * @param listener   The PropertyChangeListener to be removed.
     */
    public void removeIngestJobEventListener(Set<IngestJobEvent> eventTypes, final PropertyChangeListener listener) {
        eventTypes.forEach((IngestJobEvent event) -> {
            jobEventPublisher.removeSubscriber(event.toString(), listener);
        });
    }

    /**
     * Adds an ingest module event property change listener.
     *
     * @param listener The PropertyChangeListener to be added.
     */
    public void addIngestModuleEventListener(final PropertyChangeListener listener) {
        moduleEventPublisher.addSubscriber(INGEST_MODULE_EVENT_NAMES, listener);
    }

    /**
     * Adds an ingest module event property change listener for given event
     * types.
     *
     * @param eventTypes The event types to listen for
     * @param listener   The PropertyChangeListener to be removed.
     */
    public void addIngestModuleEventListener(Set<IngestModuleEvent> eventTypes, final PropertyChangeListener listener) {
        eventTypes.forEach((IngestModuleEvent event) -> {
            moduleEventPublisher.addSubscriber(event.toString(), listener);
        });
    }

    /**
     * Removes an ingest module event property change listener.
     *
     * @param listener The PropertyChangeListener to be removed.
     */
    public void removeIngestModuleEventListener(final PropertyChangeListener listener) {
        moduleEventPublisher.removeSubscriber(INGEST_MODULE_EVENT_NAMES, listener);
    }

    /**
     * Removes an ingest module event property change listener.
     *
     * @param eventTypes The event types to stop listening for
     * @param listener   The PropertyChangeListener to be removed.
     */
    public void removeIngestModuleEventListener(Set<IngestModuleEvent> eventTypes, final PropertyChangeListener listener) {
        moduleEventPublisher.removeSubscriber(INGEST_MODULE_EVENT_NAMES, listener);
    }

    /**
     * Publishes an ingest job event signifying an ingest job started.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobStarted(long ingestJobId) {
        AutopsyEvent event = new AutopsyEvent(IngestJobEvent.STARTED.toString(), ingestJobId, null);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Publishes an ingest event signifying an ingest job finished.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCompleted(long ingestJobId) {
        AutopsyEvent event = new AutopsyEvent(IngestJobEvent.COMPLETED.toString(), ingestJobId, null);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Publishes an ingest event signifying an ingest job was canceled.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCancelled(long ingestJobId) {
        AutopsyEvent event = new AutopsyEvent(IngestJobEvent.CANCELLED.toString(), ingestJobId, null);
        eventPublishingExecutor.submit(new PublishEventTask(event, jobEventPublisher));
    }

    /**
     * Publishes an ingest job event signifying analysis of a data source
     * started.
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
     * Publishes an ingest job event signifying analysis of a data source
     * finished.
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
     * Publishes an ingest job event signifying analysis of a data source was
     * canceled.
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
     * Publishes an ingest module event signifying the ingest of a file was
     * completed.
     *
     * @param file The file.
     */
    void fireFileIngestDone(AbstractFile file) {
        AutopsyEvent event = new FileAnalyzedEvent(file);
        eventPublishingExecutor.submit(new PublishEventTask(event, moduleEventPublisher));
    }

    /**
     * Publishes an ingest module event signifying discovery of additional
     * content by an ingest module.
     *
     * @param moduleDataEvent A ModuleContentEvent with the details of the new
     *                        content.
     */
    void fireIngestModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        AutopsyEvent event = new ContentChangedEvent(moduleContentEvent);
        eventPublishingExecutor.submit(new PublishEventTask(event, moduleEventPublisher));
    }

    /**
     * Causes the ingest manager to get the top component used to display ingest
     * inbox messages. Used to be called by the custom installer for this
     * package once the window system is initialized, but that results in a lot
     * of UI components being initialized, which freezes the UI for a long
     * period of time(JIRA-7345). Instead we are now initializing
     * IngestMessageInbox immediately prior to running first ingest job.
     */
    void initIngestMessageInbox() {
        synchronized (this.ingestMessageBoxLock) {
            ingestMessageBox = IngestMessageTopComponent.findInstance();
        }
    }

    /**
     * Posts a message to the ingest messages inbox.
     *
     * @param message The message to be posted.
     */
    void postIngestMessage(IngestMessage message) {
        synchronized (this.ingestMessageBoxLock) {
            if (ingestMessageBox != null && RuntimeProperties.runningWithGUI()) {
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

    /*
     * Clears the ingest messages inbox.
     */
    private void clearIngestMessageBox() {
        synchronized (this.ingestMessageBoxLock) {
            if (null != ingestMessageBox) {
                ingestMessageBox.clearMessages();
            }
            ingestErrorMessagePosts.set(0);
        }
    }

    /**
     * Updates the ingest progress snapshot when a new ingest module starts
     * working on a data source level ingest task.
     *
     * @param task              The data source ingest task.
     * @param currentModuleName The display name of the currently processing
     *                          module.
     */
    void setIngestTaskProgress(DataSourceIngestTask task, String currentModuleName) {
        IngestThreadActivitySnapshot prevSnap = ingestThreadActivitySnapshots.get(task.getThreadId());
        IngestThreadActivitySnapshot newSnap = new IngestThreadActivitySnapshot(task.getThreadId(), task.getIngestJobPipeline().getId(), currentModuleName, task.getDataSource());
        ingestThreadActivitySnapshots.put(task.getThreadId(), newSnap);

        /*
         * Update the total run time for the PREVIOUS ingest module in the
         * pipeline, which has now finished its processing for the task.
         */
        incrementModuleRunTime(prevSnap.getActivity(), newSnap.getStartTime().getTime() - prevSnap.getStartTime().getTime());
    }

    /**
     * Updates the ingest progress snapshot when a new ingest module starts
     * working on a file ingest task.
     *
     * @param task              The file ingest task.
     * @param currentModuleName The display name of the currently processing
     *                          module.
     */
    void setIngestTaskProgress(FileIngestTask task, String currentModuleName) {
        IngestThreadActivitySnapshot prevSnap = ingestThreadActivitySnapshots.get(task.getThreadId());
        IngestThreadActivitySnapshot newSnap;
        try {
            newSnap = new IngestThreadActivitySnapshot(task.getThreadId(), task.getIngestJobPipeline().getId(), currentModuleName, task.getDataSource(), task.getFile());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting file from file ingest task", ex);
            newSnap = new IngestThreadActivitySnapshot(task.getThreadId(), task.getIngestJobPipeline().getId(), currentModuleName, task.getDataSource());
        }
        ingestThreadActivitySnapshots.put(task.getThreadId(), newSnap);

        /*
         * Update the total run time for the PREVIOUS ingest module in the
         * pipeline, which has now finished its processing for the task.
         */
        incrementModuleRunTime(prevSnap.getActivity(), newSnap.getStartTime().getTime() - prevSnap.getStartTime().getTime());
    }

    /**
     * Updates the ingest progress snapshot when an ingest task is completed.
     *
     * @param task The ingest task.
     */
    void setIngestTaskProgressCompleted(IngestTask task) {
        IngestThreadActivitySnapshot prevSnap = ingestThreadActivitySnapshots.get(task.getThreadId());
        IngestThreadActivitySnapshot newSnap = new IngestThreadActivitySnapshot(task.getThreadId());
        ingestThreadActivitySnapshots.put(task.getThreadId(), newSnap);

        /*
         * Update the total run time for the LAST ingest module in the pipeline,
         * which has now finished its processing for the task.
         */
        incrementModuleRunTime(prevSnap.getActivity(), newSnap.getStartTime().getTime() - prevSnap.getStartTime().getTime());
    }

    /**
     * Updates the cumulative run time for a given ingest module.
     *
     * @param moduleDisplayName The diplay name of the ingest module.
     * @param duration
     */
    void incrementModuleRunTime(String moduleDisplayName, Long duration) {
        if (moduleDisplayName.equals("IDLE")) { //NON-NLS
            return;
        }

        synchronized (ingestModuleRunTimes) {
            Long prevTimeL = ingestModuleRunTimes.get(moduleDisplayName);
            long prevTime = 0;
            if (prevTimeL != null) {
                prevTime = prevTimeL;
            }
            prevTime += duration;
            ingestModuleRunTimes.put(moduleDisplayName, prevTime);
        }
    }

    /**
     * Gets the cumulative run times for the ingest module.
     *
     * @return Map of module name to run time (in milliseconds)
     */
    @Override
    public Map<String, Long> getModuleRunTimes() {
        synchronized (ingestModuleRunTimes) {
            Map<String, Long> times = new HashMap<>(ingestModuleRunTimes);
            return times;
        }
    }

    /**
     * Gets snapshots of the current state of each ingest manager ingest task
     * (ingest thread).
     *
     * @return A collection of ingest manager ingest task snapshots.
     */
    @Override
    public List<IngestThreadActivitySnapshot> getIngestThreadActivitySnapshots() {
        return new ArrayList<>(ingestThreadActivitySnapshots.values());
    }

    /**
     * Gets snapshots of the state of all running ingest jobs.
     *
     * @return A list of ingest job state snapshots.
     */
    @Override
    public List<Snapshot> getIngestJobSnapshots() {
        List<Snapshot> snapShots = new ArrayList<>();
        synchronized (ingestJobsById) {
            ingestJobsById.values().forEach((job) -> {
                snapShots.addAll(job.getDataSourceIngestJobSnapshots());
            });
        }
        return snapShots;
    }

    /**
     * Gets the free disk space of the drive to which ingest data is being
     * written, as reported by the ingest monitor.
     *
     * @return Free disk space, -1 if unknown.
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
                    synchronized (ingestJobsById) {
                        ingestJobsById.remove(job.getId());
                    }
                    return null;
                }

                if (RuntimeProperties.runningWithGUI()) {
                    final String displayName = NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsTask.run.displayName");
                    this.progress = ProgressHandle.createHandle(displayName, new Cancellable() {
                        @Override
                        public boolean cancel() {
                            if (progress != null) {
                                progress.setDisplayName(NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsTask.run.cancelling", displayName));
                            }
                            synchronized (startIngestJobFutures) {
                                Future<?> handle = startIngestJobFutures.remove(threadId);
                                handle.cancel(true);
                            }
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
                synchronized (startIngestJobFutures) {
                    startIngestJobFutures.remove(threadId);
                }
            }
        }

    }

    /**
     * Executes ingest jobs by acting as a consumer for an ingest tasks queue.
     */
    private final class ExecuteIngestJobTasksTask implements Runnable {

        private final long threadId;
        private final BlockingIngestTaskQueue tasks;

        ExecuteIngestJobTasksTask(long threadId, BlockingIngestTaskQueue tasks) {
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

        @Override
        public void run() {
            publisher.publish(event);
        }

    }

    /**
     * A snapshot of the current activity of an ingest job task execution task
     * running in an ingest thread.
     */
    @Immutable
    public static final class IngestThreadActivitySnapshot implements Serializable {

        private static final long serialVersionUID = 1L;

        private final long threadId;
        private final Date startTime;
        private final String activity;
        private final String dataSourceName;
        private final String fileName;
        private final long jobId;

        /**
         * A snapshot of the current activity of an idle ingest job task
         * execution task running in an ingest thread.
         *
         * @param threadId The ingest manager task/thread id for the
         *                 task/thread.
         */
        IngestThreadActivitySnapshot(long threadId) {
            this.threadId = threadId;
            startTime = new Date();
            this.activity = NbBundle.getMessage(this.getClass(), "IngestManager.IngestThreadActivitySnapshot.idleThread");
            this.dataSourceName = "";
            this.fileName = "";
            this.jobId = 0;
        }

        /**
         * A snapshot of the current activity of an ingest job data source level
         * task execution task running in an ingest thread.
         *
         * @param threadId   The ingest manager task/thread id for the
         *                   task/thread.
         * @param jobId      The ingest job id.
         * @param activity   A short description of the current activity.
         * @param dataSource The data source that is the subject of the task.
         */
        IngestThreadActivitySnapshot(long threadId, long jobId, String activity, Content dataSource) {
            this.threadId = threadId;
            this.jobId = jobId;
            startTime = new Date();
            this.activity = activity;
            this.dataSourceName = dataSource.getName();
            this.fileName = "";
        }

        /**
         * A snapshot of the current activity of an ingest job file level task
         * execution task running in an ingest thread.
         *
         * @param threadId   The ingest manager task/thread id for the
         *                   task/thread.
         * @param jobId      The ingest job id.
         * @param activity   A short description of the current activity.
         * @param dataSource The data source that is the source of the file that
         *                   is the subject of the task.
         * @param file       The file that is the subject of the task.
         */
        IngestThreadActivitySnapshot(long threadId, long jobId, String activity, Content dataSource, AbstractFile file) {
            this.threadId = threadId;
            this.jobId = jobId;
            startTime = new Date();
            this.activity = activity;
            this.dataSourceName = dataSource.getName();
            this.fileName = file.getName();
        }

        /**
         * Gets the ingest job id.
         *
         * @return The ingest job id.
         */
        long getIngestJobId() {
            return jobId;
        }

        /**
         * Gets the ingest manager task/thread id for the task/thread.
         *
         * @return The task/thread id.
         */
        long getThreadId() {
            return threadId;
        }

        /**
         * Gets the start date and time for the current activity.
         *
         * @return The start date and time.
         */
        Date getStartTime() {
            return startTime;
        }

        /**
         * Gets the THE short description of the current activity.
         *
         * @return The short description of the current activity.
         */
        String getActivity() {
            return activity;
        }

        /**
         * Gets the display name of the data source that is either the subject
         * of the task or is the source of the file that is the subject of the
         * task.
         *
         * @return The data source display name.
         */
        String getDataSourceName() {
            return dataSourceName;
        }

        /**
         * Gets the file, if any, that is the subject of the task.
         *
         * @return The fiel name, may be the empty string.
         */
        String getFileName() {
            return fileName;
        }

    }

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
    }

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
         * a case. For example, if a module adds an extracted or carved file to
         * a case, the module should fire this event. The old value of the
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
        instance.addIngestJobEventListener(listener);
        instance.addIngestModuleEventListener(listener);
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
        instance.removeIngestJobEventListener(listener);
        instance.removeIngestModuleEventListener(listener);
    }

    /**
     * Starts an ingest job that will process a collection of data sources.
     *
     * @param dataSources The data sources to process.
     * @param settings    The settings for the ingest job.
     *
     * @return The ingest job that was started on success or null on failure.
     *
     * @deprecated. Use beginIngestJob() instead.
     */
    @Deprecated
    public synchronized IngestJob startIngestJob(Collection<Content> dataSources, IngestJobSettings settings) {
        return beginIngestJob(dataSources, settings).getJob();
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

}
