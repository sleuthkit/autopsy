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
import java.util.concurrent.Future;
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
    private ExecutorService startIngestJobsExecutor = null;
    private ExecutorService dataSourceIngestTasksExecutor = null;
    private ExecutorService fileIngestTasksExecutor = null;
    private AtomicLong ingestJobId = new AtomicLong(0L);
    private AtomicLong ingestTaskId = new AtomicLong(0L);
    private final HashMap<Long, IngestJob> ingestJobs = new HashMap<>(); // Maps job ids to jobs
    private final HashMap<Long, Future<?>> ingestTasks = new HashMap<>(); // Maps task ids to Runnable tasks
//    private TaskSchedulingWorker taskSchedulingWorker = null;
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
    
    public synchronized static int getNumberOfFileIngestThreads() {
        return userPreferences.getInt(NUMBER_OF_FILE_INGEST_THREADS_KEY, DEFAULT_NUMBER_OF_FILE_INGEST_THREADS);
    }

    public static void setNumberOfFileIngestThreads(int numberOfThreads) {
        if (numberOfThreads < MIN_NUMBER_OF_FILE_INGEST_THREADS
                || numberOfThreads > MAX_NUMBER_OF_FILE_INGEST_THREADS) {
            numberOfThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;
        }

        synchronized (IngestManager.class) {
            userPreferences.putInt(NUMBER_OF_FILE_INGEST_THREADS_KEY, numberOfThreads);
        }
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
    synchronized public static void addPropertyChangeListener(final PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    synchronized public static void removePropertyChangeListener(final PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    synchronized static void fireModuleEvent(String eventType, String moduleName) {
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
    synchronized static void fireFileDone(long objId) {
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
    synchronized static void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
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
    synchronized static void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        try {
            pcs.firePropertyChange(IngestEvent.CONTENT_CHANGED.toString(), moduleContentEvent, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                    NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr.errListenToUpdates.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    synchronized void startIngestJob(final Content dataSource, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        List<Content> dataSources = new ArrayList<>();
        dataSources.add(dataSource);
        startIngestJobs(dataSources, moduleTemplates, processUnallocatedSpace);
    }

    synchronized void startIngestJobs(final List<Content> dataSources, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        if (!isIngestRunning() && ingestMessageBox != null) {
            ingestMessageBox.clearMessages();
        }

        createThreadPools();
                
        long taskId = ingestTaskId.incrementAndGet();
        Future<?> dataSourceIngestTask = startIngestJobsExecutor.submit(new StartIngestJobsTask(taskId, dataSources, moduleTemplates, processUnallocatedSpace));
        ingestTasks.put(taskId, dataSourceIngestTask);

        if (ingestMessageBox != null) {
            ingestMessageBox.restoreMessages();
        }
    }

    synchronized void addFileToIngestJob(long ingestJobId, AbstractFile file) {
        IngestJob job = this.ingestJobs.get(ingestJobId);
        if (job != null) {
            scheduler.getFileScheduler().scheduleFile(job, file);
        } else {
            logger.log(Level.SEVERE, "Unable to map ingest job id (id = {0}) to an ingest job, failed to schedule file (id = {1})", new Object[]{ingestJobId, file.getId()});
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                    "Unable to associate " + file.getName() + " with ingest job, file will not be processed by ingest nodules",
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    private synchronized void startIngestTasks() {
        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }
        
        long taskId = ingestTaskId.incrementAndGet();
        Future<?> dataSourceIngestTask = dataSourceIngestTasksExecutor.submit(new RunDataSourceIngestModulesTask(taskId));
        ingestTasks.put(taskId, dataSourceIngestTask);

        int workersRequested = getNumberOfFileIngestThreads();
        for (int i = 0; i < workersRequested; ++i) {
            taskId = ingestTaskId.incrementAndGet();
            Future<?> fileIngestTask = fileIngestTasksExecutor.submit(new RunFileSourceIngestModulesTask(taskId));
            ingestTasks.put(taskId, fileIngestTask);
        }
    }

    synchronized void reportIngestTaskDone(long taskId) {
        ingestTasks.remove(taskId);

        List<Long> completedJobs = new ArrayList<>();
        for (IngestJob job : ingestJobs.values()) {
            job.releaseIngestPipelinesForThread(taskId);
            if (job.areIngestPipelinesShutDown()) {
                completedJobs.add(job.getId());
            }
        }

        for (Long jobId : completedJobs) {
            ingestJobs.remove(jobId);
        }
    }

    synchronized void cancelIngestTasks() {
//    synchronized void cancelIngestTasks(int waitTime, TimeUnit unit) { // RJCTODO
        // First get the task scheduling worker to stop adding new tasks. 
//        boolean res = shutDownThreadPool(startIngestJobsExecutor, 1, TimeUnit.SECONDS);
//        startIngestJobsExecutor = null;
        
        // Now mark all of the ingest jobs as cancelled. This way the ingest 
        // modules will know they are being shut down due to cancellation when
        // the cancelled ingest workers release their pipelines.
        for (IngestJob job : ingestJobs.values()) {
            job.cancel();
        }

        // Jettision the remaining data tasks. This will dispose of any tasks that
        // the scheduling worker queued up before it was cancelled.
//        scheduler.getFileScheduler().empty();
//        scheduler.getDataSourceScheduler().empty();

        // Cancel all of the ingest module running tasks.
        for (Future<?> task : ingestTasks.values()) {
            task.cancel(true);
        }

//        res = shutDownThreadPool(dataSourceIngestTasksExecutor, 30, TimeUnit.SECONDS);
//        dataSourceIngestTasksExecutor = null;
//        res = shutDownThreadPool(fileIngestTasksExecutor, 30, TimeUnit.SECONDS);
//        fileIngestTasksExecutor =  null;        
        
        // Jettision the remaining tasks again to try to dispose of any tasks 
        // queued up task workers before they were cancelled.
//        scheduler.getFileScheduler().empty();
//        scheduler.getDataSourceScheduler().empty();
    }

    // The following method implementation is adapted from:
    // http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html#awaitTermination(long, java.util.concurrent.TimeUnit)
    private boolean shutDownThreadPool(ExecutorService pool, int waitTime, TimeUnit unit) {
        boolean succeeded = true;
        // Prevent submission of new tasks.
        pool.shutdown();
        try {
            // Wait a while for existing tasks to terminate.
            if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                // Cancel currently executing tasks.
                pool.shutdownNow(); 
                if (!pool.awaitTermination(waitTime, unit)) {
                    succeeded = false;
                }
            }
        } catch (InterruptedException ex) {
            // (Re-)Cancel if current thread also interrupted.
            pool.shutdownNow();
            // Preserve interrupt status.
            Thread.currentThread().interrupt();
        }
        return succeeded;
    }        
        
    /**
     * Test if any ingest jobs are in progress.
     *
     * @return True if any ingest jobs are in progress, false otherwise
     */
    public synchronized boolean isIngestRunning() {
        return (ingestJobs.isEmpty() == false);
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
                        IngestManager.getInstance().cancelIngestTasks();
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
                    ingestJobs.put(ingestJob.getId(), ingestJob);

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
                // RJCTODO:
            } finally {
                // RJCTODO: Release
                progress.finish();
            }
        }

        boolean isFinished() {
            return finished;
        }
    }

    private class RunDataSourceIngestModulesTask implements Runnable {

        private final long id;

        RunDataSourceIngestModulesTask(long threadId) {
            this.id = threadId;
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
                    DataSourceIngestPipeline pipeline = job.getDataSourceIngestPipelineForThread(this.id);
                    pipeline.process(job.getDataSourceTaskProgressBar());
                }
            } catch (Exception ex) {
                String message = String.format("Data source ingest thread (id=%d) caught exception", this.id); // RJCTODO
                logger.log(Level.SEVERE, message, ex);
            } finally {
                IngestManager.getInstance().reportIngestTaskDone(this.id);
            }
        }
    }

    private class RunFileSourceIngestModulesTask implements Runnable {

        private final long id;

        RunFileSourceIngestModulesTask(long taskId) {
            this.id = taskId;
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
                    FileIngestPipeline pipeline = job.getFileIngestPipelineForThread(this.id);
                    job.handleFileTaskStarted(task);
                    pipeline.process(task.getFile());
                }
            } catch (Exception ex) {
                String message = String.format("Data source ingest thread (id=%d) caught exception", this.id); // RJCTODO
                logger.log(Level.SEVERE, message, ex);
            } finally {
                IngestManager.getInstance().reportIngestTaskDone(this.id);
            }
        }
    }

    class IngestCancellationTask implements Runnable {

        @Override
        public void run() {
            // RJCTODO: Run
        }
    }    
}
