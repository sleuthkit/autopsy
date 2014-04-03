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
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import java.util.prefs.Preferences;
import org.openide.util.Exceptions;

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
    private static IngestManager instance;
    private final IngestScheduler scheduler;
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    private final Preferences userPreferences = NbPreferences.forModule(this.getClass());
    private final HashMap<Long, IngestJob> ingestJobs = new HashMap<>();
    private TaskSchedulingWorker taskSchedulingWorker;
    private DataSourceTaskWorker dataSourceTaskWorker;
    private final List<FileTaskWorker> fileTaskWorkers = new ArrayList<>();
    private long nextDataSourceTaskId = 0;
    private long nextThreadId = 0;
    private volatile IngestUI ingestMessageBox;

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

    private IngestManager() {
        this.scheduler = IngestScheduler.getInstance();
        //numberOfFileIngestThreads = user
    }

    /**
     * Returns reference to singleton instance.
     *
     * @returns Instance of class.
     */
    synchronized public static IngestManager getInstance() {
        if (instance == null) {
            instance = new IngestManager();
        }
        return instance;
    }

    /**
     * called by Installer in AWT thread once the Window System is ready
     */
    void initIngestMessageInbox() {
        if (this.ingestMessageBox == null) {
            this.ingestMessageBox = IngestMessageTopComponent.findInstance();
        }
    }

    synchronized private long getNextDataSourceTaskId() {
        return ++this.nextDataSourceTaskId;
    }

    synchronized private long getNextThreadId() {
        return ++this.nextThreadId;
    }

    public synchronized int getNumberOfFileIngestThreads() {
        return userPreferences.getInt(NUMBER_OF_FILE_INGEST_THREADS_KEY, DEFAULT_NUMBER_OF_FILE_INGEST_THREADS);
    }

    public synchronized void setNumberOfFileIngestThreads(int numberOfThreads) {
        if (numberOfThreads < MIN_NUMBER_OF_FILE_INGEST_THREADS
                || numberOfThreads > MAX_NUMBER_OF_FILE_INGEST_THREADS) {
            numberOfThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;
        }
        userPreferences.putInt(null, numberOfThreads);
    }

    /**
     * Add property change listener to listen to ingest events as defined in
     * IngestModuleEvent.
     *
     * @param listener PropertyChangeListener to register
     */
    public static synchronized void addPropertyChangeListener(final PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public static synchronized void removePropertyChangeListener(final PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    static synchronized void fireModuleEvent(String eventType, String moduleName) {
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
    static synchronized void fireFileDone(long objId) {
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
    static synchronized void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
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
    static synchronized void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
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
     * Multiple data-sources version of scheduleDataSource() method. Enqueues
     * multiple sources inputs (Content objects) and associated modules at once
     *
     * @param modules modules to scheduleDataSource on every data source
     * @param inputs input data sources to enqueue and scheduleDataSource the
     * ingest modules on
     */
    void scheduleDataSourceTasks(final List<Content> dataSources, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        if (!isIngestRunning() && ingestMessageBox != null) {
            ingestMessageBox.clearMessages();
        }

        taskSchedulingWorker = new TaskSchedulingWorker(dataSources, moduleTemplates, processUnallocatedSpace);
        taskSchedulingWorker.execute();

        if (ingestMessageBox != null) {
            ingestMessageBox.restoreMessages();
        }
    }

    /**
     * IngestManager entry point, enqueues data to be processed and starts new
     * ingest as needed, or just enqueues data to an existing pipeline.
     *
     * Spawns background thread which enumerates all sorted files and executes
     * chosen modules per file in a pre-determined order. Notifies modules when
     * work is complete or should be interrupted using complete() and stop()
     * calls. Does not block and can be called multiple times to enqueue more
     * work to already running background ingest process.
     *
     * @param modules modules to scheduleDataSource on the data source input
     * @param input input data source Content objects to scheduleDataSource the
     * ingest modules on
     */
    void scheduleDataSourceTask(final Content dataSource, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        List<Content> dataSources = new ArrayList<>();
        dataSources.add(dataSource);
        scheduleDataSourceTasks(dataSources, moduleTemplates, processUnallocatedSpace);
    }

    /**
     * Schedule a file for ingest and add it to ongoing file ingest process on
     * the same data source. Scheduler updates the current progress.
     *
     * The file to be added is usually a product of a currently ran ingest. Now
     * we want to process this new file with the same ingest context.
     *
     * @param file file to be scheduled
     * @param pipelineContext ingest context used to ingest parent of the file
     * to be scheduled
     */
    void scheduleFile(long ingestJobId, AbstractFile file) {
        IngestJob job = this.ingestJobs.get(ingestJobId);
        if (job == null) {
            logger.log(Level.SEVERE, "Unable to map ingest job id (id = {0}) to an ingest job, failed to schedule file (id = {1})", new Object[]{ingestJobId, file.getId()});
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                    "Unable to associate " + file.getName() + " with ingest job, file will not be processed by ingest nodules",
                    MessageNotifyUtil.MessageType.ERROR);
        }

        scheduler.getFileScheduler().scheduleFile(job, file);
    }

    private synchronized void startAll() {
        // Make sure the ingest monitor is running.
        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }

        // Make sure a data source task worker is running.
        // TODO: There is a race condition here with SwingWorker.isDone().
        // The highly unlikely chance that no data source task worker will 
        // run for this job needs to be addressed.
        if (dataSourceTaskWorker == null || dataSourceTaskWorker.isDone()) {
            dataSourceTaskWorker = new DataSourceTaskWorker(getNextThreadId());
            dataSourceTaskWorker.execute();
        }

        // Make sure the requested number of file task workers are running.
        // TODO: There is a race condition here with SwingWorker.isDone().
        // The highly unlikely chance that no file task workers or the wrong
        // number of file task workers will run for this job needs to be 
        // addressed. 
        int workersRequested = getNumberOfFileIngestThreads();
        int workersRunning = 0;
        for (FileTaskWorker worker : fileTaskWorkers) {
            if (worker != null) {
                if (worker.isDone()) {
                    if (workersRunning < workersRequested) {
                        ++workersRunning;
                        worker = new FileTaskWorker(getNextThreadId());
                        worker.execute();
                    } else {
                        worker = null;
                    }
                } else {
                    ++workersRunning;
                }
            } else if (workersRunning < workersRequested) {
                ++workersRunning;
                worker = new FileTaskWorker(getNextThreadId());
                worker.execute();
            }
        }
        if (workersRunning < workersRequested
                && fileTaskWorkers.size() < MAX_NUMBER_OF_FILE_INGEST_THREADS) {
            FileTaskWorker worker = new FileTaskWorker(getNextThreadId());
            worker.execute();
            fileTaskWorkers.add(worker);
        }
    }

    synchronized void reportThreadDone(long threadId) {
        List<Long> completedJobs = new ArrayList<>();
        for (IngestJob job : ingestJobs.values()) {
            job.releaseIngestPipelinesForThread(threadId);
            if (job.areIngestPipelinesShutDown()) {
                completedJobs.add(job.getId());
            }
        }

        for (Long jobId : completedJobs) {
            ingestJobs.remove(jobId);
        }
    }

    synchronized void stopAll() {
        // First get the task scheduling worker to stop adding new tasks. 
        if (taskSchedulingWorker != null) {
            taskSchedulingWorker.cancel(true);
            while (!taskSchedulingWorker.isDone()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
            }
            taskSchedulingWorker = null;
        }

        // Now mark all of the ingest jobs as cancelled. This way the ingest 
        // modules will know they are being shut down due to cancellation when
        // the cancelled ingest workers release their pipelines.
        for (IngestJob job : ingestJobs.values()) {
            job.cancel();
        }

        // Cancel the data source task worker. It will release its pipelines
        // in its done() method and the pipelines will shut down their modules.
        if (dataSourceTaskWorker != null) {
            dataSourceTaskWorker.cancel(true);
            while (!dataSourceTaskWorker.isDone()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
            }
            dataSourceTaskWorker = null;
        }

        // Cancel the file task workers. They will release their pipelines
        // in their done() methods and the pipelines will shut down their 
        // modules.
        for (FileTaskWorker worker : fileTaskWorkers) {
            if (worker != null) {
                worker.cancel(true);
                while (!worker.isDone()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                    }
                }
                worker = null;
            }
        }

        // Jettision the remaining tasks. This will dispose of any tasks that
        // the scheduling worker queued up before it was cancelled.
        scheduler.getFileScheduler().empty();
        scheduler.getDataSourceScheduler().empty();
    }

    /**
     * Test if any ingest modules are running
     *
     * @return true if any module is running, false otherwise
     */
    public synchronized boolean isIngestRunning() {
        // TODO: There is a race condition here with SwingWorker.isDone().
        // It probably needs to be addressed at a later date.
        if (taskSchedulingWorker != null && !taskSchedulingWorker.isDone()) {
            return true;
        }

        if (dataSourceTaskWorker != null && !dataSourceTaskWorker.isDone()) {
            return true;
        }

        for (FileTaskWorker worker : fileTaskWorkers) {
            if (worker != null && !worker.isDone()) {
                return true;
            }
        }

        return false;
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

    private class TaskSchedulingWorker extends SwingWorker<Object, Void> {

        private final List<Content> dataSources;
        private final List<IngestModuleTemplate> moduleTemplates;
        private final boolean processUnallocatedSpace;
        private ProgressHandle progress;

        TaskSchedulingWorker(List<Content> dataSources, List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
            this.dataSources = dataSources;
            this.moduleTemplates = moduleTemplates;
            this.processUnallocatedSpace = processUnallocatedSpace;
        }

        @Override
        protected Object doInBackground() throws Exception {
            // Set up a progress bar that can be used to cancel all of the 
            // ingest jobs currently being performed.
            final String displayName = "Queueing ingest tasks";
            progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Queueing ingest cancelled by user.");
                    if (progress != null) {
                        progress.setDisplayName(displayName + " (Cancelling...)");
                    }
                    IngestManager.getInstance().stopAll();
                    return true;
                }
            });

            progress.start(2 * dataSources.size());
            int processed = 0;
            for (Content dataSource : dataSources) {
                if (isCancelled()) {
                    logger.log(Level.INFO, "Task scheduling thread cancelled");
                    return null;
                }

                final String inputName = dataSource.getName();
                IngestJob ingestJob = new IngestJob(IngestManager.this.getNextDataSourceTaskId(), dataSource, moduleTemplates, processUnallocatedSpace);

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
                    MessageNotifyUtil.Message.error(
                            "Failed to start the following ingest modules: " + failedModules.toString() + " .\n\n"
                            + "No ingest modules will be run. Please disable the module "
                            + "or fix the error and restart ingest by right clicking on "
                            + "the data source and selecting Run Ingest Modules.\n\n"
                            + "Error: " + errors.get(0).getModuleError().getMessage());
                    return null;
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

            return null;
        }

        @Override
        protected void done() {
            try {
                super.get();
            } catch (CancellationException | InterruptedException ex) {
                // IngestManager.stopAll() will dispose of all tasks. 
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error while scheduling ingest jobs", ex);
                MessageNotifyUtil.Message.error("An error occurred while starting ingest. Results may only be partial");
            } finally {
                if (!isCancelled()) {
                    startAll();
                }
                progress.finish();
            }
        }
    }

    /**
     * Performs data source ingest tasks for one or more ingest jobs on a worker
     * thread.
     */
    class DataSourceTaskWorker extends SwingWorker<Object, Void> {

        private final long id;

        DataSourceTaskWorker(long threadId) {
            this.id = threadId;
        }

        @Override
        protected Void doInBackground() throws Exception {
            logger.log(Level.INFO, "Data source ingest thread (id={0}) started", this.id);
            IngestScheduler.DataSourceScheduler scheduler = IngestScheduler.getInstance().getDataSourceScheduler();
            while (scheduler.hasNext()) {
                if (isCancelled()) {
                    logger.log(Level.INFO, "Data source ingest thread (id={0}) cancelled", this.id);
                    return null;
                }
                IngestJob job = scheduler.next();
                DataSourceIngestPipeline pipeline = job.getDataSourceIngestPipelineForThread(this.id);
                pipeline.process(this, job.getDataSourceTaskProgressBar());
            }
            logger.log(Level.INFO, "Data source ingest thread (id={0}) completed", this.id);
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get();
            } catch (CancellationException | InterruptedException e) {
                logger.log(Level.INFO, "Data source ingest thread (id={0}) cancelled", this.id);
            } catch (Exception ex) {
                String message = String.format("Data source ingest thread (id=%d) experienced a fatal error", this.id);
                logger.log(Level.SEVERE, message, ex);
            } finally {
                IngestManager.getInstance().reportThreadDone(this.id);
            }
        }
    }

    /**
     * Performs file ingest tasks for one or more ingest jobs on a worker
     * thread.
     */
    class FileTaskWorker extends SwingWorker<Object, Void> {

        private final long id;

        FileTaskWorker(long threadId) {
            this.id = threadId;
        }

        @Override
        protected Object doInBackground() throws Exception {
            logger.log(Level.INFO, "File ingest thread (id={0}) started", this.id);
            IngestScheduler.FileScheduler fileScheduler = IngestScheduler.getInstance().getFileScheduler();
            while (fileScheduler.hasNext()) {
                if (isCancelled()) {
                    logger.log(Level.INFO, "File ingest thread (id={0}) cancelled", this.id);
                    return null;
                }
                IngestScheduler.FileScheduler.FileTask task = fileScheduler.next();
                IngestJob job = task.getJob();
                FileIngestPipeline pipeline = job.getFileIngestPipelineForThread(this.id);
                job.handleFileTaskStarted(task);
                pipeline.process(task.getFile());
            }
            logger.log(Level.INFO, "File ingest thread (id={0}) completed", this.id);
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get();
            } catch (CancellationException | InterruptedException e) {
                logger.log(Level.INFO, "File ingest thread (id={0}) cancelled", this.id);
            } catch (Exception ex) {
                String message = String.format("File ingest thread {0} experienced a fatal error", this.id);
                logger.log(Level.SEVERE, message, ex);
            } finally {
                IngestManager.getInstance().reportThreadDone(this.id);
            }
        }
    }
}
