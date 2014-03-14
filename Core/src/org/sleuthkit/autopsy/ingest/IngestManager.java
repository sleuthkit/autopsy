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
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

// RJCTODO: Fix comment
/**
 * IngestManager sets up and manages ingest modules runs them in a background
 * thread notifies modules when work is complete or should be interrupted
 * processes messages from modules via messenger proxy and posts them to GUI.
 *
 * This runs as a singleton and you can access it using the getDefault() method.
 *
 */
public class IngestManager {

    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private static final PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);
    private static IngestManager instance;
    private final IngestScheduler scheduler;
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    private final HashMap<Long, IngestJob> ingestJobs = new HashMap<>();
    private TaskSchedulingWorker taskSchedulingWorker;
    private FileTaskWorker fileTaskWorker;
    private DataSourceTaskWorker dataSourceTaskWorker;
    private long nextDataSourceTaskId = 0;
    private long nextThreadId = 0;
    public final static String MODULE_PROPERTIES = "ingest";
    private volatile IngestUI ingestMessageBox;

    // RJCTODO: Redo eventing for 3.1
    /**
     * Possible events about ingest modules Event listeners can get the event
     * name by using String returned by toString() method on the specific event.
     */
    public enum IngestModuleEvent {

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
    }

    /**
     * Returns reference to singleton instance.
     *
     * @returns Instance of class.
     */
    synchronized public static IngestManager getDefault() {
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
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Ingest Manager updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Fire event when file is done with a pipeline run
     *
     * @param objId ID of file that is done
     */
    static synchronized void fireFileDone(long objId) {
        try {
            pcs.firePropertyChange(IngestModuleEvent.FILE_DONE.toString(), objId, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Ingest Manager updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
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
            pcs.firePropertyChange(IngestModuleEvent.DATA.toString(), moduleDataEvent, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Ingest Manager updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
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
            pcs.firePropertyChange(IngestModuleEvent.CONTENT_CHANGED.toString(), moduleContentEvent, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Ingest Manager updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
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
    void scheduleFileTask(long ingestJobId, AbstractFile file) {
        IngestJob job = this.ingestJobs.get(ingestJobId); // RJCTODO: Consider renaming
        if (job == null) {
            // RJCTODO: Handle severe error
        }

        scheduler.getFileScheduler().scheduleIngestOfDerivedFile(job, file); // RJCTODO: Consider renaming
    }

    /**
     * Starts the File-level Ingest Module pipeline and the Data Source-level
     * Ingest Modules for the queued up data sources and files.
     *
     * if AbstractFile module is still running, do nothing and allow it to
     * consume queue otherwise start /restart AbstractFile worker
     *
     * data source ingest workers run per (module,content). Checks if one for
     * the same (module,content) is already running otherwise start/restart the
     * worker
     */
    private synchronized void startAll() {
        // RJCTODO: What does this do?
        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }

        if (scheduler.getDataSourceScheduler().hasNext()) {
            if (dataSourceTaskWorker == null || dataSourceTaskWorker.isDone()) {
                dataSourceTaskWorker = new DataSourceTaskWorker(getNextThreadId()); // RJCTODO: May not need method call
                dataSourceTaskWorker.execute();
            }
        }

        if (scheduler.getFileScheduler().hasNext()) {
            if (fileTaskWorker == null || fileTaskWorker.isDone()) {
                fileTaskWorker = new FileTaskWorker(getNextThreadId()); // RJCTODO: May not need method call
                fileTaskWorker.execute();
            }
        }
    }

    synchronized void reportThreadDone(long threadId) {
        for (IngestJob job : ingestJobs.values()) {
            job.releaseIngestPipelinesForThread(threadId);
            // RJCTODO: Add logging of errors or send ingest messages 
            if (job.arePipelinesShutDown()) {
                ingestJobs.remove(job.getId());                
            }
        }
    }

    synchronized void stopAll() {
        for (IngestJob job : ingestJobs.values()) {
            job.cancel();
        }

        if (taskSchedulingWorker != null) {
            taskSchedulingWorker.cancel(true);
            taskSchedulingWorker = null;
        }

        scheduler.getFileScheduler().empty();
        scheduler.getDataSourceScheduler().empty();

        if (dataSourceTaskWorker != null) {
            dataSourceTaskWorker.cancel(true);
        }

        if (fileTaskWorker != null) {
            fileTaskWorker.cancel(true);
        }
    }

    /**
     * Test if any ingest modules are running
     *
     * @return true if any module is running, false otherwise
     */
    public synchronized boolean isIngestRunning() {
        return ((taskSchedulingWorker != null && !taskSchedulingWorker.isDone())
                || (fileTaskWorker != null && !fileTaskWorker.isDone())
                || (fileTaskWorker != null && !fileTaskWorker.isDone()));
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
            final String displayName = "Queueing ingest tasks";
            progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Queueing ingest cancelled by user.");
                    if (progress != null) {
                        progress.setDisplayName(displayName + " (Cancelling...)");
                    }
                    return TaskSchedulingWorker.this.cancel(true);
                }
            });

            progress.start(2 * dataSources.size());
            int processed = 0;
            for (Content dataSource : dataSources) {
                final String inputName = dataSource.getName();
                IngestJob ingestJob = new IngestJob(IngestManager.this.getNextDataSourceTaskId(), dataSource, moduleTemplates, processUnallocatedSpace);

                List<IngestModuleError> errors = ingestJob.startUpIngestPipelines();
                if (!errors.isEmpty()) {
                    // RJCTODO: Log all errors. Provide a list of all of the modules
                    // that failed.
                    MessageNotifyUtil.Message.error(
                            "Failed to load " + errors.get(0).getModuleDisplayName() + " ingest module.\n\n"
                            + "No ingest modules will be run. Please disable the module "
                            + "or fix the error and restart ingest by right clicking on "
                            + "the data source and selecting Run Ingest Modules.\n\n"
                            + "Error: " + errors.get(0).getModuleError().getMessage());
                    return null;
                }

                // Save the ingest job for later cleanup of pipelines.
                ingestJobs.put(ingestJob.getId(), ingestJob);

                // Queue the data source ingest tasks for the ingest job.
                logger.log(Level.INFO, "Queueing data source tasks: {0}", ingestJob);
                progress.progress("DataSource Ingest" + " " + inputName, processed);
                scheduler.getDataSourceScheduler().schedule(ingestJob);
                progress.progress("DataSource Ingest" + " " + inputName, ++processed);

                // Queue the file ingest tasks for the ingest job.
                logger.log(Level.INFO, "Queuing file ingest tasks: {0}", ingestJob);
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
                handleInterruption(ex);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error while enqueing files. ", ex);
                handleInterruption(ex);
            } finally {
                if (this.isCancelled()) {
                    handleInterruption(new Exception());
                } else {
                    startAll();
                }
                progress.finish();
            }
        }

        private void handleInterruption(Exception ex) {
            // RJCTODO: This seems broken, should empty only for current job?
            scheduler.getFileScheduler().empty();
            scheduler.getDataSourceScheduler().empty();
        }
    }

    /**
     * Performs data source ingest tasks for one or more ingest jobs on a worker
     * thread.
     */
    class DataSourceTaskWorker extends SwingWorker<Object, Void> {

        private final long id;
        private ProgressHandle progress;

        DataSourceTaskWorker(long threadId) {
            this.id = threadId;
        }

        @Override
        protected Void doInBackground() throws Exception {
            logger.log(Level.INFO, String.format("Data source ingest thread {0} started", this.id));

            // Set up a progress bar with cancel capability. This is one of two 
            // ways that the worker can be canceled. The other way is via a call
            // to IngestManager.stopAll().
            final String displayName = "Data Source";
            progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Data source ingest thread {0} cancelled", DataSourceTaskWorker.this.id);
                    if (progress != null) {
                        progress.setDisplayName(displayName + " (Cancelling...)");
                    }
                    return DataSourceTaskWorker.this.cancel(true);
                }
            });
            progress.start();
            progress.switchToIndeterminate();

            IngestScheduler.DataSourceScheduler scheduler = IngestScheduler.getInstance().getDataSourceScheduler();
            while (scheduler.hasNext()) {
                if (isCancelled()) {
                    logger.log(Level.INFO, "Data source ingest thread {0} cancelled", this.id);
                    return null;
                }

                IngestJob ingestJob = scheduler.next();
                DataSourceIngestPipeline pipeline = ingestJob.getDataSourceIngestPipelineForThread(this.id);
                pipeline.ingestDataSource(this, this.progress);
            }

            logger.log(Level.INFO, "Data source ingest thread {0} completed", this.id);
            IngestManager.getDefault().reportThreadDone(this.id);
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get();
            } catch (CancellationException | InterruptedException e) {
                logger.log(Level.INFO, "Data source ingest thread {0} cancelled", this.id);
                IngestManager.getDefault().reportThreadDone(this.id);
            } catch (Exception ex) {
                String message = String.format("Data source ingest thread {0} experienced a fatal error", this.id);
                logger.log(Level.SEVERE, message, ex);
                IngestManager.getDefault().reportThreadDone(this.id);
            } finally {
                progress.finish();
            }
        }
    }

    /**
     * Performs file ingest tasks for one or more ingest jobs on a worker
     * thread.
     */
    class FileTaskWorker extends SwingWorker<Object, Void> {

        private final long id;
        private ProgressHandle progress;

        FileTaskWorker(long threadId) {
            this.id = threadId;
        }

        @Override
        protected Object doInBackground() throws Exception {
            logger.log(Level.INFO, String.format("File ingest thread {0} started", this.id));

            // Set up a progress bar with cancel capability. This is one of two ways 
            // that the worker can be canceled. The other way is via a call to
            // IngestManager.stopAll().
            final String displayName = "File Ingest";
            progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "File ingest thread {0} cancelled", FileTaskWorker.this.id);
                    if (progress != null) {
                        progress.setDisplayName(displayName + " (Cancelling...)");
                    }
                    return FileTaskWorker.this.cancel(true);
                }
            });
            progress.start();
            progress.switchToIndeterminate();
            IngestScheduler.FileScheduler fileScheduler = IngestScheduler.getInstance().getFileScheduler();
            int totalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
            progress.switchToDeterminate(totalEnqueuedFiles);

            int processedFiles = 0;
            while (fileScheduler.hasNext()) {
                if (isCancelled()) {
                    IngestManager.getDefault().reportThreadDone(this.id);
                    logger.log(Level.INFO, "File ingest thread {0} cancelled", this.id);
                    return null;
                }

                IngestScheduler.FileScheduler.FileTask task = fileScheduler.next();
                AbstractFile file = task.getFile();
                progress.progress(file.getName(), processedFiles);
                FileIngestPipeline pipeline = task.getJob().getFileIngestPipelineForThread(this.id);
                pipeline.ingestFile(file);

                // Update the progress bar.
                int newTotalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
                if (newTotalEnqueuedFiles > totalEnqueuedFiles) {
                    totalEnqueuedFiles = newTotalEnqueuedFiles + 1;
                    progress.switchToIndeterminate();
                    progress.switchToDeterminate(totalEnqueuedFiles);
                }
                if (processedFiles < totalEnqueuedFiles) {
                    ++processedFiles;
                }
            }

            logger.log(Level.INFO, "File ingest thread {0} completed", this.id);
            IngestManager.getDefault().reportThreadDone(this.id);
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get();
            } catch (CancellationException | InterruptedException e) {
                logger.log(Level.INFO, "File ingest thread {0} cancelled", this.id);
                IngestManager.getDefault().reportThreadDone(this.id);
            } catch (Exception ex) {
                String message = String.format("File ingest thread {0} experienced a fatal error", this.id);
                logger.log(Level.SEVERE, message, ex);
                IngestManager.getDefault().reportThreadDone(this.id);
            } finally {
                progress.finish();
            }
        }
    }
}
