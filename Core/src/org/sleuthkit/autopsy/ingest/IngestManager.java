/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import java.util.prefs.Preferences;
import javax.swing.SwingWorker;

/**
 * Manages the execution of ingest jobs.
 */
public class IngestManager {

    private static final String NUMBER_OF_FILE_INGEST_THREADS_KEY = "NumberOfFileingestThreads";
    private static final int MIN_NUMBER_OF_FILE_INGEST_THREADS = 1;
    private static final int MAX_NUMBER_OF_FILE_INGEST_THREADS = 4;
    private static final int DEFAULT_NUMBER_OF_FILE_INGEST_THREADS = 2;
    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private static final PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);
    private static final Preferences userPreferences = NbPreferences.forModule(IngestManager.class);
    private static IngestManager instance;
    private final IngestScheduler scheduler = IngestScheduler.getInstance();
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    private final ExecutorService startIngestJobsExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService dataSourceIngestTasksExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService fileIngestTasksExecutor = Executors.newFixedThreadPool(MAX_NUMBER_OF_FILE_INGEST_THREADS);
    private final HashMap<Long, IngestJob> ingestJobs = new HashMap<>(); // Maps job ids to jobs        
    private final HashMap<Long, Future<?>> ingestTasks = new HashMap<>(); // Maps task ids to task cancellation handles        
    private AtomicLong ingestJobId = new AtomicLong(0L);
    private AtomicLong ingestTaskId = new AtomicLong(0L);
    private volatile IngestUI ingestMessageBox;

    /**
     * Gets the IngestManager singleton, creating it if necessary.
     *
     * @returns The IngestManager singleton.
     */
    public synchronized static IngestManager getInstance() {
        if (instance == null) {
            instance = new IngestManager();
        }
        return instance;
    }

    private IngestManager() {
    }

    /**
     * Finds the top component for the ingest messages in box. Called by the
     * custom installer for this package once the window system is initialized.
     */
    void initIngestMessageInbox() {
        if (this.ingestMessageBox == null) {
            this.ingestMessageBox = IngestMessageTopComponent.findInstance();
        }
    }

    public synchronized static int getNumberOfFileIngestThreads() {
        return userPreferences.getInt(NUMBER_OF_FILE_INGEST_THREADS_KEY, DEFAULT_NUMBER_OF_FILE_INGEST_THREADS);
    }

    public synchronized static void setNumberOfFileIngestThreads(int numberOfThreads) {
        if (numberOfThreads < MIN_NUMBER_OF_FILE_INGEST_THREADS
                || numberOfThreads > MAX_NUMBER_OF_FILE_INGEST_THREADS) {
            numberOfThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;
        }

        userPreferences.putInt(NUMBER_OF_FILE_INGEST_THREADS_KEY, numberOfThreads);
    }

    synchronized void startIngestJobs(final List<Content> dataSources, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        if (!isIngestRunning() && ingestMessageBox != null) {
            ingestMessageBox.clearMessages();
        }

        long taskId = ingestTaskId.incrementAndGet();
        Future<?> task = startIngestJobsExecutor.submit(new StartIngestJobsTask(taskId, dataSources, moduleTemplates, processUnallocatedSpace));
        ingestTasks.put(taskId, task);

        if (ingestMessageBox != null) {
            ingestMessageBox.restoreMessages();
        }
    }

    /**
     * Test if any ingest jobs are in progress.
     *
     * @return True if any ingest jobs are in progress, false otherwise
     */
    public boolean isIngestRunning() { // RJCTODO: Address synch or not synch and listeners
        return (ingestJobs.isEmpty() == false);
    }

    synchronized void addFileToIngestJob(long ingestJobId, AbstractFile file) {
        IngestJob job = ingestJobs.get(ingestJobId);
        if (job != null) {
            scheduler.getFileScheduler().scheduleFile(job, file);
        }
    }

    void cancelIngestJobs() {
        new IngestCancellationWorker().execute();
    }

    /**
     * Ingest events.
     */
    public enum IngestEvent {

        /**
         * Property change event fired when an ingest job is started. The ingest
         * job id is in old value field of the PropertyChangeEvent object.
         */
        INGEST_JOB_STARTED,
        /**
         * Property change event fired when an ingest job is completed. The
         * ingest job id is in old value field of the PropertyChangeEvent
         * object.
         */
        INGEST_JOB_COMPLETED,
        /**
         * Property change event fired when an ingest job is canceled. The
         * ingest job id is in old value field of the PropertyChangeEvent
         * object.
         */
        INGEST_JOB_CANCELLED,
        /**
         * Event sent when an ingest module posts new data to blackboard or
         * somewhere else. Second argument of the property change fired contains
         * ModuleDataEvent object and third argument is null. The object can
         * contain encapsulated new data created by the module. Listener can
         * also query new data as needed.
         */
        DATA,
        /**
         * Event send when content changed, either its attributes changed, or
         * new content children have been added. I.e. from ZIP files or Carved
         * files
         */
        CONTENT_CHANGED,
        /**
         * Event sent when a file has finished going through a pipeline of
         * modules. Second argument is the object ID. Third argument is null
         */
        FILE_DONE,
    };

    /**
     * Add property change listener to listen to ingest events.
     *
     * @param listener PropertyChangeListener to register
     */
    public static void addPropertyChangeListener(final PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public static void removePropertyChangeListener(final PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    static void fireIngestJobEvent(String eventType, long jobId) {
        try {
            pcs.firePropertyChange(eventType, jobId, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                    NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr.errListenToUpdates.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Fire event when file is done with a pipeline run
     *
     * @param fileId ID of file that is done
     */
    static void fireFileIngestDone(long fileId) {
        try {
            pcs.firePropertyChange(IngestEvent.FILE_DONE.toString(), fileId, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                    NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr.errListenToUpdates.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Fire event for ModuleDataEvent (when modules post data to blackboard,
     * etc.)
     *
     * @param moduleDataEvent
     */
    static void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        try {
            pcs.firePropertyChange(IngestEvent.DATA.toString(), moduleDataEvent, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                    NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr.errListenToUpdates.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Fire event for ModuleContentChanged (when modules create new content that
     * needs to be analyzed)
     *
     * @param moduleContentEvent
     */
    static void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        try {
            pcs.firePropertyChange(IngestEvent.CONTENT_CHANGED.toString(), moduleContentEvent, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                    NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr.errListenToUpdates.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Module publishes message using InegestManager handle Does not block. The
     * message gets enqueued in the GUI thread and displayed in a widget
     * IngestModule should make an attempt not to publish the same message
     * multiple times. Viewer will attempt to identify duplicate messages and
     * filter them out (slower)
     */
    void postIngestMessage(IngestMessage message) {
        if (ingestMessageBox != null) {
            ingestMessageBox.displayMessage(message);
        }
    }

    /**
     * Get free disk space of a drive where ingest data are written to That
     * drive is being monitored by IngestMonitor thread when ingest is running.
     * Use this method to get amount of free disk space anytime.
     *
     * @return amount of disk space, -1 if unknown
     */
    long getFreeDiskSpace() {
        if (ingestMonitor != null) {
            return ingestMonitor.getFreeSpace();
        } else {
            return -1;
        }
    }

    private synchronized void startIngestTasks() {
        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }

        long taskId = ingestTaskId.incrementAndGet();
        Future<?> task = dataSourceIngestTasksExecutor.submit(new RunDataSourceIngestModulesTask(taskId));
        ingestTasks.put(taskId, task);

        int numberOfFileTasksRequested = getNumberOfFileIngestThreads();
        for (int i = 0; i < numberOfFileTasksRequested; ++i) {
            taskId = ingestTaskId.incrementAndGet();
            task = fileIngestTasksExecutor.submit(new RunFileSourceIngestModulesTask(taskId));
            ingestTasks.put(taskId, task);
        }
    }

    private synchronized void stopIngestTasks() {
        // First mark all of the ingest jobs as cancelled. This way the 
        // ingest modules will know they are being shut down due to 
        // cancellation when the cancelled run ingest module tasks release 
        // their pipelines. 
        for (IngestJob job : ingestJobs.values()) {
            job.cancel();
        }

        // Cancel the run ingest module tasks, setting the state of the threads
        // running them to interrupted.
        for (Future<?> task : ingestTasks.values()) {
            task.cancel(true);
        }

        // Jettision the remaining data source and file ingest tasks.
        scheduler.getFileScheduler().empty();
        scheduler.getDataSourceScheduler().empty();
    }

    synchronized void reportStartIngestJobsTaskDone(long taskId) {
        ingestTasks.remove(taskId);
    }

    synchronized void reportRunIngestModulesTaskDone(long taskId) {
        ingestTasks.remove(taskId);

        List<Long> completedJobs = new ArrayList<>();
        for (IngestJob job : ingestJobs.values()) {
            job.releaseIngestPipelinesForThread(taskId);
            if (job.areIngestPipelinesShutDown() == true) {
                completedJobs.add(job.getId());
            }
        }

        for (Long jobId : completedJobs) {
            IngestJob job = ingestJobs.remove(jobId);
            fireIngestJobEvent(job.isCancelled() ? IngestEvent.INGEST_JOB_CANCELLED.toString() : IngestEvent.INGEST_JOB_COMPLETED.toString(), jobId);
        }
    }

    private class StartIngestJobsTask implements Runnable {

        private final long id;
        private final List<Content> dataSources;
        private final List<IngestModuleTemplate> moduleTemplates;
        private final boolean processUnallocatedSpace;
        private ProgressHandle progress;

        StartIngestJobsTask(long taskId, List<Content> dataSources, List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
            this.id = taskId;
            this.dataSources = dataSources;
            this.moduleTemplates = moduleTemplates;
            this.processUnallocatedSpace = processUnallocatedSpace;
        }

        @Override
        public void run() {
            try {
                final String displayName = "Queueing ingest tasks";
                progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                    @Override
                    public boolean cancel() {
                        if (progress != null) {
                            progress.setDisplayName(displayName + " (Cancelling...)");
                        }
                        IngestManager.getInstance().cancelIngestJobs();
                        return true;
                    }
                });

                progress.start(2 * dataSources.size());
                int workUnitsCompleted = 0;
                for (Content dataSource : dataSources) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    IngestJob ingestJob = new IngestJob(IngestManager.this.ingestJobId.incrementAndGet(), dataSource, moduleTemplates, processUnallocatedSpace);
                    List<IngestModuleError> errors = ingestJob.startUpIngestPipelines();
                    if (!errors.isEmpty()) {
                        StringBuilder failedModules = new StringBuilder();
                        for (int i = 0; i < errors.size(); ++i) {
                            IngestModuleError error = errors.get(i);
                            String moduleName = error.getModuleDisplayName();
                            logger.log(Level.SEVERE, "The " + moduleName + " module failed to start up", error.getModuleError());
                            failedModules.append(moduleName);
                            if ((errors.size() > 1) && (i != (errors.size() - 1))) {
                                failedModules.append(",");
                            }
                        }
                        MessageNotifyUtil.Message.error( // RJCTODO: Fix this to show all errors, probably should specify data source name
                                "Failed to start the following ingest modules: " + failedModules.toString() + " .\n\n"
                                + "No ingest modules will be run. Please disable the module "
                                + "or fix the error and restart ingest by right clicking on "
                                + "the data source and selecting Run Ingest Modules.\n\n"
                                + "Error: " + errors.get(0).getModuleError().getMessage());
                        ingestJob.cancel();
                        break;
                    }

                    // Save the ingest job for later cleanup of pipelines.
                    synchronized (IngestManager.this) {
                        ingestJobs.put(ingestJob.getId(), ingestJob);
                    }

                    // Queue the data source ingest tasks for the ingest job.
                    final String inputName = dataSource.getName();
                    progress.progress("Data source ingest tasks for " + inputName, workUnitsCompleted); // RJCTODO: Improve
                    scheduler.getDataSourceScheduler().schedule(ingestJob);
                    progress.progress("Data source ingest tasks for " + inputName, ++workUnitsCompleted);

                    // Queue the file ingest tasks for the ingest job.
                    progress.progress("Data source ingest tasks for " + inputName, workUnitsCompleted);
                    scheduler.getFileScheduler().scheduleIngestOfFiles(ingestJob);
                    progress.progress("Data source ingest tasks for " + inputName, ++workUnitsCompleted);

                    if (!Thread.currentThread().isInterrupted()) {
                        startIngestTasks();
                        fireIngestJobEvent(IngestEvent.INGEST_JOB_STARTED.toString(), ingestJob.getId());
                    }
                }
            } catch (Exception ex) {
                String message = String.format("StartIngestJobsTask (id=%d) caught exception", id);
                logger.log(Level.SEVERE, message, ex);
                MessageNotifyUtil.Message.error("An error occurred while starting ingest. Results may only be partial");
            } finally {
                progress.finish();
                reportStartIngestJobsTaskDone(id);
            }
        }
    }

    private class RunDataSourceIngestModulesTask implements Runnable {

        private final long id;

        RunDataSourceIngestModulesTask(long taskId) {
            id = taskId;
        }

        @Override
        public void run() {
            try {
                IngestScheduler.DataSourceScheduler scheduler = IngestScheduler.getInstance().getDataSourceScheduler();
                while (scheduler.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    IngestJob job = scheduler.next();
                    job.getDataSourceIngestPipelineForThread(id).process();
                }
            } catch (Exception ex) {
                String message = String.format("RunDataSourceIngestModulesTask (id=%d) caught exception", id);
                logger.log(Level.SEVERE, message, ex);
            } finally {
                reportRunIngestModulesTaskDone(id);
            }
        }
    }

    private class RunFileSourceIngestModulesTask implements Runnable {

        private final long id;

        RunFileSourceIngestModulesTask(long taskId) {
            id = taskId;
        }

        @Override
        public void run() {
            try {
                IngestScheduler.FileScheduler fileScheduler = IngestScheduler.getInstance().getFileScheduler();
                while (fileScheduler.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    IngestScheduler.FileScheduler.FileTask task = fileScheduler.next();
                    IngestJob job = task.getJob();
                    job.updateFileTasksProgressBar(task.getFile().getName());
                    job.getFileIngestPipelineForThread(id).process(task.getFile());
                }
            } catch (Exception ex) {
                String message = String.format("RunFileSourceIngestModulesTask (id=%d) caught exception", id);
                logger.log(Level.SEVERE, message, ex);
            } finally {
                reportRunIngestModulesTaskDone(id);
            }
        }
    }

    class IngestCancellationWorker extends SwingWorker<Void, Void> {

        @Override
        protected Void doInBackground() throws Exception {
            stopIngestTasks();
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get();
            } catch (CancellationException | InterruptedException ex) {
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error while cancelling ingest jobs", ex);
            }
        }
    }
}
