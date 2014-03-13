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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestScheduler.FileScheduler.FileTask;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

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
    private final IngestScheduler scheduler;
    private FileIngestWorker fileIngestWorker;
    private DataSourceIngestWorker dataSourceIngestWorker;
    private SwingWorker<Object, Void> queueWorker;
    private final List<IngestModuleTempApiShim> abstractFileModules = new ArrayList<>();
    private final static PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);
    private final IngestMonitor ingestMonitor = new IngestMonitor();
//    private IngestModuleLoader moduleLoader = null;
    private long nextDataSourceTaskId = 0;
    private long nextThreadId = 0;
    public final static String MODULE_PROPERTIES = "ingest";

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
    //ui
    //Initialized by Installer in AWT thread once the Window System is ready
    private volatile IngestUI ui;
    //singleton
    private static volatile IngestManager instance;

    private IngestManager() {
        this.scheduler = IngestScheduler.getInstance();
    }

    synchronized long getNextDataSourceTaskId() {
        return ++this.nextDataSourceTaskId;
    }

    synchronized long getNextThreadId() {
        return ++this.nextThreadId;
    }

    /**
     * called by Installer in AWT thread once the Window System is ready
     */
    void initUI() {
        if (this.ui == null) {
            this.ui = IngestMessageTopComponent.findInstance();
        }
    }

    /**
     * Returns reference to singleton instance.
     *
     * @returns Instance of class.
     */
    public static IngestManager getDefault() {
        if (instance == null) {
            synchronized (IngestManager.class) {
                if (instance == null) {
                    logger.log(Level.INFO, "creating manager instance");
                    instance = new IngestManager();
                }
            }
        }
        return instance;
    }

    /**
     * Add property change listener to listen to ingest events as defined in
     * IngestModuleEvent.
     *
     * @param l PropertyChangeListener to register
     */
    public static synchronized void addPropertyChangeListener(final PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
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
    void scheduleDataSource(final List<Content> dataSources, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        logger.log(Level.INFO, "Will enqueue {0} data sources for {1} modules.", new Object[]{dataSources.size(), moduleTemplates.size()});

        if (!isIngestRunning() && ui != null) {
            ui.clearMessages();
        }

        queueWorker = new EnqueueWorker(dataSources, moduleTemplates, processUnallocatedSpace);
        queueWorker.execute();

        if (ui != null) {
            ui.restoreMessages();
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
    void scheduleDataSource(final Content dataSource, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        List<Content> dataSources = new ArrayList<>();
        dataSources.add(dataSource);
        logger.log(Level.INFO, "Will enqueue input: {0}", dataSource.getName());
        scheduleDataSource(dataSources, moduleTemplates, processUnallocatedSpace);
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
    void scheduleFile(long dataSourceTaskId, AbstractFile file) {
        scheduler.getFileScheduler().scheduleIngestOfDerivedFile(dataSourceTaskId, file);
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
            if (dataSourceIngestWorker == null || dataSourceIngestWorker.isDone()) {
                dataSourceIngestWorker = new DataSourceIngestWorker(getNextThreadId()); // RJCTODO: May not need method call
                dataSourceIngestWorker.execute();
            }
        }

        if (scheduler.getFileScheduler().hasNext()) {
            if (fileIngestWorker == null || fileIngestWorker.isDone()) {
                fileIngestWorker = new FileIngestWorker(getNextThreadId()); // RJCTODO: May not need method call
                fileIngestWorker.execute();
            }
        }
    }

    /**
     * Open a dialog box to report an initialization error to the user.
     *
     * @param moduleName The name of the module that failed to initialize.
     * @param errorMessage The message gotten from the exception that was
     * thrown.
     */
    // RJCTODO: Do not have an implementation of this early load concept yet
    // Perhaps the thing to do is to create and destroy a pipeline every time
    // a task is created...that would lead to spurious events, though. Could
    // build the first instances of the pieplines per thread?
//    private void displayInitError(String moduleName, String errorMessage) {
//        MessageNotifyUtil.Message.error(
//                "Failed to load " + moduleName + " ingest module.\n\n"
//                + "No ingest modules will be run. Please disable the module "
//                + "or fix the error and restart ingest by right clicking on "
//                + "the data source and selecting Run Ingest Modules.\n\n"
//                + "Error: " + errorMessage);
//    }
    synchronized void stopAll() {
        if (queueWorker != null) {
            queueWorker.cancel(true);
            queueWorker = null;
        }

        scheduler.getFileScheduler().empty();
        scheduler.getDataSourceScheduler().empty();

        if (dataSourceIngestWorker != null) {
            dataSourceIngestWorker.cancel(true);
        }

        if (fileIngestWorker != null) {
            fileIngestWorker.cancel(true);
        }
    }

    /**
     * Test if any ingest modules are running
     *
     * @return true if any module is running, false otherwise
     */
    public synchronized boolean isIngestRunning() {
        return ((queueWorker != null && !queueWorker.isDone())
                || (fileIngestWorker != null && !fileIngestWorker.isDone())
                || (fileIngestWorker != null && !fileIngestWorker.isDone()));
    }

    /**
     * Module publishes message using InegestManager handle Does not block. The
     * message gets enqueued in the GUI thread and displayed in a widget
     * IngestModule should make an attempt not to publish the same message
     * multiple times. Viewer will attempt to identify duplicate messages and
     * filter them out (slower)
     */
    void postIngestMessage(IngestMessage message) {
        if (ui != null) {
            ui.displayMessage(message);
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

    private class EnqueueWorker extends SwingWorker<Object, Void> {

        private final List<Content> dataSources;
        private final List<IngestModuleTemplate> moduleTemplates;
        private final boolean processUnallocatedSpace;
        private final Logger logger = Logger.getLogger(EnqueueWorker.class.getName());
        private ProgressHandle progress;

        EnqueueWorker(List<Content> dataSources, List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
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
                    return EnqueueWorker.this.cancel(true);
                }
            });

            progress.start(2 * dataSources.size());
            int processed = 0;
            for (Content dataSource : dataSources) {
                final String inputName = dataSource.getName();
                IngestJob ingestJob = new IngestJob(IngestManager.this.getNextDataSourceTaskId(), dataSource, moduleTemplates, processUnallocatedSpace);
                logger.log(Level.INFO, "Queueing data source tasks: {0}", ingestJob);
                progress.progress("DataSource Ingest" + " " + inputName, processed);
                scheduler.getDataSourceScheduler().schedule(ingestJob);
                progress.progress("DataSource Ingest" + " " + inputName, ++processed);

                logger.log(Level.INFO, "Queing file ingest task: {0}", ingestJob);
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
            scheduler.getFileScheduler().empty();
            scheduler.getDataSourceScheduler().empty();
        }
    }
}
