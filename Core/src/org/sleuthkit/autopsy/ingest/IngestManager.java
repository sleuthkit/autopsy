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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private final IngestJobsManager jobsManager = new IngestJobsManager();
    private final IngestScheduler scheduler = IngestScheduler.getInstance();
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    private ExecutorService startIngestJobsExecutor = null;
    private ExecutorService dataSourceIngestTasksExecutor = null;
    private ExecutorService fileIngestTasksExecutor = null;
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
        createThreadPools();
    }

    /**
     * Finds the ingest messages in box TopComponent. Called by the custom
     * installer for this package once the window system is initialized.
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

        createThreadPools();
        startIngestJobsExecutor.submit(new StartIngestJobsTask(ingestTaskId.incrementAndGet(), dataSources, moduleTemplates, processUnallocatedSpace));

        if (ingestMessageBox != null) {
            ingestMessageBox.restoreMessages();
        }
    }

    /**
     * Test if any ingest jobs are in progress.
     *
     * @return True if any ingest jobs are in progress, false otherwise
     */
    public boolean isIngestRunning() {
        return jobsManager.hasJobs();
    }

    synchronized void addFileToIngestJob(long ingestJobId, AbstractFile file) {
        if (!jobsManager.addFileToIngestJob(ingestJobId, file)) {
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                    "Unable to associate " + file.getName() + " with ingest job, file will not be processed by ingest nodules",
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    synchronized void cancelIngestJobs() {
        new IngestCancellationWorker(1, TimeUnit.SECONDS).execute();
    }

    /**
     * Ingest events.
     */
    public enum IngestEvent {

        // RJCTODO: Update comments
        /**
         * Event sent when an ingest module has been started. Second argument of
         * the property change is a string form of the module name and the third
         * argument is null.
         */
        STARTED,
        /**
         * Event sent when an ingest module has completed processing by its own
         * means. Second argument of the property change is a string form of the
         * module name and the third argument is null.
         *
         * This event is generally used by listeners to perform a final data
         * view refresh (listeners need to query all data from the blackboard).
         */
        COMPLETED,
        /**
         * Event sent when an ingest module has stopped processing, and likely
         * not all data has been processed. Second argument of the property
         * change is a string form of the module name and third argument is
         * null.
         */
        STOPPED,
        /**
         * Event sent when ingest module posts new data to blackboard or
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

    static void fireModuleEvent(String eventType, String moduleName) {
        try {
            pcs.firePropertyChange(eventType, moduleName, null);
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
     * @param objId ID of file that is done
     */
    static void fireFileDone(long objId) {
        try {
            pcs.firePropertyChange(IngestEvent.FILE_DONE.toString(), objId, null);
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

        dataSourceIngestTasksExecutor.submit(new RunDataSourceIngestModulesTask(ingestTaskId.incrementAndGet()));
        
        int numberOfFileTasksRequested = getNumberOfFileIngestThreads();
        for (int i = 0; i < numberOfFileTasksRequested; ++i) {
            fileIngestTasksExecutor.submit(new RunFileSourceIngestModulesTask(ingestTaskId.incrementAndGet()));
        }
    }

    private synchronized void createThreadPools() {
        if (startIngestJobsExecutor == null) {
            startIngestJobsExecutor = Executors.newSingleThreadExecutor();
        }
        if (dataSourceIngestTasksExecutor == null) {
            dataSourceIngestTasksExecutor = Executors.newSingleThreadExecutor();
        }
        if (fileIngestTasksExecutor == null) {
            fileIngestTasksExecutor = Executors.newFixedThreadPool(MAX_NUMBER_OF_FILE_INGEST_THREADS);
        }
    }

    private synchronized boolean shutDownThreadPools(int timeOut, TimeUnit timeOutUnits) {
        boolean success = true;
        if (!shutDownThreadPool(startIngestJobsExecutor, timeOut, timeOutUnits)) {
            success = false;
        }
        if (!shutDownThreadPool(dataSourceIngestTasksExecutor, timeOut, timeOutUnits)) {
            success = false;
        }
        if (!shutDownThreadPool(fileIngestTasksExecutor, timeOut, timeOutUnits)) {
            success = false;
        }
        startIngestJobsExecutor = null;
        dataSourceIngestTasksExecutor = null;
        fileIngestTasksExecutor = null;
        return success;
    }

    private boolean shutDownThreadPool(ExecutorService pool, int waitTime, TimeUnit unit) {
        try {
            pool.shutdownNow();
            return pool.awaitTermination(waitTime, unit);
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt(); // Preserve interrupted status.
            return false;
        }
    }

    private class IngestJobsManager {

        private final HashMap<Long, IngestJob> jobs = new HashMap<>(); // Maps job ids to jobs        

        synchronized void addJob(IngestJob job) {
            jobs.put(job.getId(), job);
        }

        synchronized boolean hasJobs() {
            return (jobs.isEmpty() == false);
        }

        synchronized boolean addFileToIngestJob(long ingestJobId, AbstractFile file) {
            IngestJob job = jobs.get(ingestJobId);
            if (job != null) {
                scheduler.getFileScheduler().scheduleFile(job, file);
                return true;
            } else {
                logger.log(Level.SEVERE, "Unable to map ingest job id (id = {0}) to an ingest job, failed to add file (id = {1})", new Object[]{ingestJobId, file.getId()});
                return false;
            }
        }

        synchronized void cancelJobs() {
            for (IngestJob job : jobs.values()) {
                job.cancel();
            }
        }

        synchronized void reportIngestTaskDone(long taskId) {
            List<Long> completedJobs = new ArrayList<>();
            for (IngestJob job : jobs.values()) {
                job.releaseIngestPipelinesForThread(taskId);
                if (job.areIngestPipelinesShutDown() == true) {
                    completedJobs.add(job.getId());
                }
            }

            for (Long jobId : completedJobs) {
                jobs.remove(jobId);
            }
        }
    }

    private class StartIngestJobsTask implements Runnable {

        private final long id;
        private final List<Content> dataSources;
        private final List<IngestModuleTemplate> moduleTemplates;
        private final boolean processUnallocatedSpace;
        private ProgressHandle progress;
        private volatile boolean finished = false;

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
                int processed = 0;
                for (Content dataSource : dataSources) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final String inputName = dataSource.getName();
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
                        MessageNotifyUtil.Message.error( // RJCTODO: Fix this to show all errors
                                "Failed to start the following ingest modules: " + failedModules.toString() + " .\n\n"
                                + "No ingest modules will be run. Please disable the module "
                                + "or fix the error and restart ingest by right clicking on "
                                + "the data source and selecting Run Ingest Modules.\n\n"
                                + "Error: " + errors.get(0).getModuleError().getMessage());
                        break;
                    }

                    // Save the ingest job for later cleanup of pipelines.
                    jobsManager.addJob(ingestJob);

                    // Queue the data source ingest tasks for the ingest job.
                    progress.progress("DataSource Ingest" + " " + inputName, processed);
                    scheduler.getDataSourceScheduler().schedule(ingestJob);
                    progress.progress("DataSource Ingest" + " " + inputName, ++processed);

                    // Queue the file ingest tasks for the ingest job.
                    progress.progress("File Ingest" + " " + inputName, processed);
                    scheduler.getFileScheduler().scheduleIngestOfFiles(ingestJob);
                    progress.progress("File Ingest" + " " + inputName, ++processed);
                }

                if (!Thread.currentThread().isInterrupted()) {
                    startIngestTasks();
                }
            } catch (Exception ex) {
                String message = String.format("StartIngestJobsTask (id=%d) caught exception", id);
                logger.log(Level.SEVERE, message, ex);
            } finally {
                progress.finish();
            }
        }

        boolean isFinished() {
            return finished;
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
                    DataSourceIngestPipeline pipeline = job.getDataSourceIngestPipelineForThread(id);
                    pipeline.process(job.getDataSourceTaskProgressBar());
                }
            } catch (Exception ex) {
                String message = String.format("RunDataSourceIngestModulesTask (id=%d) caught exception", id);
                logger.log(Level.SEVERE, message, ex);
            } finally {
                jobsManager.reportIngestTaskDone(id);
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
                    FileIngestPipeline pipeline = job.getFileIngestPipelineForThread(id);
                    job.handleFileTaskStarted(task);
                    pipeline.process(task.getFile());
                }
            } catch (Exception ex) {
                String message = String.format("RunFileSourceIngestModulesTask (id=%d) caught exception", id);
                logger.log(Level.SEVERE, message, ex);
            } finally {
                jobsManager.reportIngestTaskDone(id);
            }
        }
    }

    class IngestCancellationWorker extends SwingWorker<Boolean, Void> {

        private final int timeOut;
        private final TimeUnit timeOutUnits;

        IngestCancellationWorker(int timeOut, TimeUnit timeOutUnits) {
            this.timeOut = timeOut;
            this.timeOutUnits = timeOutUnits;
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            // First mark all of the ingest jobs as cancelled. This way the 
            // ingest modules will know they are being shut down due to 
            // cancellation when the cancelled run ingest module tasks release 
            // their pipelines. This also makes sure the lock on the jobs 
            // manager is released before the run ingest module tasks start
            // releasing pipelines.
            jobsManager.cancelJobs();

            // Jettision the remaining data source and file ingest tasks. This
            // will could break the the run ingest module tasks out of their 
            // loops even before the pools mark their threads as interrupted. 
            scheduler.getFileScheduler().empty();
            scheduler.getDataSourceScheduler().empty();

            boolean success = shutDownThreadPools(timeOut, timeOutUnits);

            // Jettision data source and file ingest tasks again to try to 
            // dispose of any tasks that slipped into the queues.
            scheduler.getFileScheduler().empty();
            scheduler.getDataSourceScheduler().empty();

            return success;
        }
        
        // TODO: Add done() override to notify a listener of success or failure
    }
}
