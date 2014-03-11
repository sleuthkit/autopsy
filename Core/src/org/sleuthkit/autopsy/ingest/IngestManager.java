/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
//    private IngestManagerStats stats; // RJCTODO: Decide whether to reimplement
    private final IngestScheduler scheduler;
    private IngestAbstractFileProcessor abstractFileIngester;
    private List<IngestDataSourceThread> dataSourceIngesters;
    private SwingWorker<Object, Void> queueWorker;
    private final Map<String, IngestModule.ResultCode> abstractFileModulesRetValues = new HashMap<>();
    private final static PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);
    private final IngestMonitor ingestMonitor = new IngestMonitor();
//    private IngestModuleLoader moduleLoader = null;
    private DataSourceIngestJob currentTask = null; // RJCTODO: Temp glue code, remove
    private long nextDataSourceTaskId = 0;
    public final static String MODULE_PROPERTIES = "ingest";

    /**
     * Possible events about ingest modules Event listeners can get the event
     * name by using String returned by toString() method on the specific event.
     */
    public enum IngestModuleEvent {

        /**
         * Event sent when an ingest module has been started. Second
         * argument of the property change is a string form of the module name
         * and the third argument is null.
         */
        STARTED,
        
        /**
         * Event sent when an ingest module has completed processing by its own 
         * means. Second
         * argument of the property change is a string form of the module name
         * and the third argument is null.
         *
         * This event is generally used by listeners to perform a final data
         * view refresh (listeners need to query all data from the blackboard).
         */
        COMPLETED,
        
        /**
         * Event sent when an ingest module has stopped processing, and likely
         * not all data has been processed. Second argument of the property
         * change is a string form of the module name and third argument is null.
         */
        STOPPED,
        
        /**
         * Event sent when ingest module posts new data to blackboard or somewhere
         * else. Second argument of the
         * property change fired contains ModuleDataEvent object and third
         * argument is null. The object can contain encapsulated new data
         * created by the module. Listener can also query new data as needed.
         */
        DATA,
        
        /**
         * Event send when content changed, either its attributes changed, or
         * new content children have been added.  I.e. from ZIP files or Carved files
         */
        CONTENT_CHANGED,
        
        
        /**
         * Event sent when a file has finished going through a pipeline of modules.
         * Second argument is the object ID. Third argument is null
         */
        FILE_DONE,
        
    };
    //ui
    //Initialized by Installer in AWT thread once the Window System is ready
    private volatile IngestUI ui; // = null; //IngestMessageTopComponent.findInstance();
    //singleton
    private static volatile IngestManager instance;

    private IngestManager() {
        // RJCTODO: Adapt to new paradigm
//        dataSourceIngesters = new ArrayList<IngestDataSourceThread>();

        scheduler = IngestScheduler.getInstance();
    }

    // RJCTODO: May want finer-grained control than simply locking the IngestManager
    synchronized long getNextDataSourceTaskId() {
        return ++nextDataSourceTaskId;
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
     * Add property change listener to listen to ingest events as defined in IngestModuleEvent. 
     *
     * @param l PropertyChangeListener to register
     */
    public static synchronized void addPropertyChangeListener(final PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    static synchronized void fireModuleEvent(String eventType, String moduleName) {
        try {
            pcs.firePropertyChange(eventType, moduleName, null);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Ingest Manager updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
        }
    }
   
    /**
     * Fire event when file is done with a pipeline run
     * @param objId ID of file that is done
     */
    static synchronized void fireFileDone(long objId) {
        try {
            pcs.firePropertyChange(IngestModuleEvent.FILE_DONE.toString(), objId, null);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Ingest Manager updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Fire event for ModuleDataEvent (when modules post data to blackboard, etc.)
     * @param moduleDataEvent 
     */
    static synchronized void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        try {
            pcs.firePropertyChange(IngestModuleEvent.DATA.toString(), moduleDataEvent, null);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Ingest Manager updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Fire event for ModuleContentChanged (when  modules create new content that needs to be analyzed)
     * @param moduleContentEvent 
     */
    static synchronized void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        try {
            pcs.firePropertyChange(IngestModuleEvent.CONTENT_CHANGED.toString(), moduleContentEvent, null);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Ingest manager listener threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Ingest Manager updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Returns the return value from a previously run module on the file being
     * currently analyzed.
     *
     * @param moduleName Name of module.
     * @returns Return value from that module if it was previously run.
     */
    IngestModule.ResultCode getAbstractFileModuleResult(String moduleName) {
        synchronized (abstractFileModulesRetValues) {
            if (abstractFileModulesRetValues.containsKey(moduleName)) {
                return abstractFileModulesRetValues.get(moduleName);
            } else {
                return IngestModule.ResultCode.NOT_SET;
            }
        }
    }

    // RJCTODO: Update comment
    /**
     * Multiple data-sources version of scheduleDataSource() method. Enqueues multiple sources inputs (Content objects) 
     * and associated modules at once
     *
     * @param modules modules to scheduleDataSource on every data source
     * @param inputs input data sources  to enqueue and scheduleDataSource the ingest modules on
     */
    public void scheduleDataSource(final List<Content> dataSources, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        // RJCTODO: If this is useful logging, reimplement
//        logger.log(Level.INFO, "Will enqueue number of inputs: " + inputs.size() 
//                + " to " + modules.size() + " modules.");

        if (!isIngestRunning() && ui != null) {
            ui.clearMessages();
        }

        queueWorker = new EnqueueWorker(dataSources, moduleTemplates, processUnallocatedSpace);
        queueWorker.execute();

        if (ui != null) {
            ui.restoreMessages();
        }
    }

    // RJCTODO: Comment out of date, is this even used?
    /**
     * IngestManager entry point, enqueues data to be processed and starts new ingest
     * as needed, or just enqueues data to an existing pipeline.
     * 
     * Spawns
     * background thread which enumerates all sorted files and executes chosen
     * modules per file in a pre-determined order. Notifies modules when work is
     * complete or should be interrupted using complete() and stop() calls. Does
     * not block and can be called multiple times to enqueue more work to
     * already running background ingest process.
     *
     * @param modules modules to scheduleDataSource on the data source input
     * @param input input data source Content objects to scheduleDataSource the ingest modules on
     */
    public void scheduleDataSource(final Content dataSource, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        List<Content> dataSources = new ArrayList<>();
        dataSources.add(dataSource);
        logger.log(Level.INFO, "Will enqueue input: {0}", dataSource.getName());
        scheduleDataSource(dataSources, moduleTemplates, processUnallocatedSpace);
    }

    // RJCTODO: Fix comment
    /**
     * Schedule a file for ingest and add it to ongoing file ingest process on the same data source. 
     * Scheduler updates the current progress.
     *
     * The file to be added is usually a product of a currently ran ingest. 
     * Now we want to process this new file with the same ingest context.
     *
     * @param file file to be scheduled
     * @param pipelineContext ingest context used to ingest parent of the file
     * to be scheduled
     */
    void scheduleFile(long dataSourceTaskId, AbstractFile file) {
        scheduler.getFileScheduler().scheduleIngestOfDerivedFile(dataSourceTaskId, file);
    }

    /**
     * Starts the File-level Ingest Module pipeline and the Data Source-level Ingest Modules
     * for the queued up data sources and files. 
     *
     * if AbstractFile module is still running, do nothing and allow it to
     * consume queue otherwise start /restart AbstractFile worker
     *
     * data source ingest workers run per (module,content). Checks if one for the same (module,content)
     * is already running otherwise start/restart the worker
     */
    private synchronized void startAll() {
        final IngestScheduler.DataSourceScheduler dataSourceScheduler = scheduler.getDataSourceScheduler();
        final IngestScheduler.FileScheduler fileScheduler = scheduler.getFileScheduler();
        boolean allInited = true;
//        IngestModuleAbstract failedModule = null; RJCTODO: DO not currently have an early module init scheme
//        String errorMessage = "";
        logger.log(Level.INFO, "DataSource queue: {0}", dataSourceScheduler.toString());
        logger.log(Level.INFO, "File queue: {0}", fileScheduler.toString());

        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }

        // RJCTODO: Fix data source ingest
        /////////
        // Start the data source-level ingest modules
//        List<IngestDataSourceThread> newThreads = new ArrayList<>();
        
        // cycle through each data source content in the queue
//        while (dataSourceScheduler.hasNext()) {
//            if (allInited == false) {
//                break;
//            }
//            //dequeue
//            // get next data source content and set of modules
//            final DataSourceTask dataSourceTask = dataSourceScheduler.next();
//
//            // check if each module for this data source content is already running
//            for (IngestModuleDataSource dataSourceTaskModule : dataSourceTask.getModules()) {
//                boolean alreadyRunning = false;
//                for (IngestDataSourceThread worker : dataSourceIngesters) {
//                    // ignore threads that are on different data sources
//                    if (!worker.getContent().equals(dataSourceTask.getDataSource())) {
//                        continue; //check next worker
//                    }
//                    //same data source, check module (by name, not id, since different instances)
//                    if (worker.getModule().getName().equals(dataSourceTaskModule.getName())) {
//                        alreadyRunning = true;
//                        logger.log(Level.INFO, "Data Source Ingester <" + dataSourceTask.getDataSource()
//                                + ", " + dataSourceTaskModule.getName() + "> is already running");
//                        break;
//                    }
//                }
//                //checked all workers
//                if (alreadyRunning == false) {
//                    logger.log(Level.INFO, "Starting new data source Ingester <" + dataSourceTask.getDataSource()
//                            + ", " + dataSourceTaskModule.getName() + ">");
//                    //data source modules are now initialized per instance
//
//                    IngestModuleInit moduleInit = new IngestModuleInit();
//                    
//                    PipelineContext<IngestModuleDataSource> dataSourcepipelineContext =
//                            dataSourceTask.getPipelineContext();
//                    
//                    final IngestDataSourceThread newDataSourceWorker = new IngestDataSourceThread(this,
//                            dataSourcepipelineContext, dataSourceTask.getDataSource(), dataSourceTaskModule, moduleInit);
//                    try {
//                        newDataSourceWorker.init();
//                    } catch (Exception e) {
//                        logger.log(Level.SEVERE, "DataSource ingest module failed init(): " + dataSourceTaskModule.getName(), e);
//                        allInited = false;
//                        failedModule = dataSourceTaskModule;
//                        errorMessage = e.getMessage();
//                        break;
//                    }
//                    dataSourceIngesters.add(newDataSourceWorker);
//                    // Add the worker to the list of new IngestThreads to be started
//                    // if all modules initialize.
//                    newThreads.add(newDataSourceWorker);
//                }
//            }
//        }
        
        // Check to make sure all modules initialized
//        if (allInited == false) {
//            displayInitError(failedModule.getName(), errorMessage);
//            dataSourceIngesters.removeAll(newThreads);
//            return;
//        }

        boolean startAbstractFileIngester = false;
        if (fileScheduler.hasNext()) {
            if (abstractFileIngester == null) {
                startAbstractFileIngester = true;
                logger.log(Level.INFO, "Starting initial AbstractFile ingester");
            } 
            //if worker had completed, restart it in case data is still enqueued
            else if (abstractFileIngester.isDone()) {
                startAbstractFileIngester = true;
                logger.log(Level.INFO, "Restarting AbstractFile ingester");
            }
        } else {
            logger.log(Level.INFO, "no new AbstractFile enqueued, no ingester needed");
        }

        if (startAbstractFileIngester) {
//            stats = new IngestManagerStats(); RJCTODO: This class may or may not be reimplemented
            abstractFileIngester = new IngestAbstractFileProcessor();
            //init all fs modules, everytime new worker starts

            // RJCTODO: Currently don't have an early module init concept quite like this, modules will be initialized
            // when the thread gets its pipeline instance(s) from the data source task.
//            for (IngestModuleAbstractFile s : abstractFileModules) {
//                // This was added at one point to remove the message about non-configured HashDB even 
//                // when HashDB was not enabled.  However, it adds some problems if a second ingest is
//                // kicked off whiel the first is ongoing. If the 2nd ingest has a module enabled that 
//                // was not initially enabled, it will never have init called. We also need to call 
//                // complete and need a similar way of passing down data to that thread to tell it which 
//                // it shoudl call complete on (otherwise it could call complete on a module that never
//                // had init() called. 
//                //if (fileScheduler.hasModuleEnqueued(s) == false) {
//                //    continue;
//                //} 
//                IngestModuleInit moduleInit = new IngestModuleInit();
//                try {
//                    s.init(moduleInit);
//                } catch (Exception e) {
//                    logger.log(Level.SEVERE, "File ingest module failed init(): " + s.getName(), e);
//                    allInited = false;
//                    failedModule = s;
//                    errorMessage = e.getMessage();
//                    break;
//                }
//            }
        }
        
        if (allInited) {
            // RJCTODO: Data source ingest temporarily disabled
            // Start DataSourceIngestModules
//            for (IngestDataSourceThread dataSourceWorker : newThreads) {
//                dataSourceWorker.execute();
//                IngestManager.fireModuleEvent(IngestModuleEvent.STARTED.toString(), dataSourceWorker.getModule().getName());
//            }
            // Start AbstractFileIngestModules
            if (startAbstractFileIngester) {
                abstractFileIngester.execute();
            }
        } else {
            // RJCTODO: DO not really have this failed module conept at this point
//            displayInitError(failedModule.getName(), errorMessage);
//            dataSourceIngesters.removeAll(newThreads);
            abstractFileIngester = null;
        }
    }
    
    /**
     * Open a dialog box to report an initialization error to the user.
     * 
     * @param moduleName The name of the module that failed to initialize.
     * @param errorMessage The message gotten from the exception that was thrown.
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

    /**
     * stop currently running threads if any (e.g. when changing a case)
     */
    synchronized void stopAll() {
        //stop queue worker
        if (queueWorker != null) {
            queueWorker.cancel(true);
            queueWorker = null;
        }

        //empty queues
        scheduler.getFileScheduler().empty();
        scheduler.getDataSourceScheduler().empty();

        //stop module workers
        if (abstractFileIngester != null) {
            // RJCTODO: rework this
            //send signals to all file modules
//            for (IngestModuleAbstractFile s : this.abstractFileModules) {
//                if (isModuleRunning(s)) {
//                    try {
//                        s.stop();
//                    } catch (Exception e) {
//                        logger.log(Level.WARNING, "Unexpected exception while stopping module: " + s.getName(), e);
//                    }
//                }
//
//            }
            //stop fs ingester thread
            boolean cancelled = abstractFileIngester.cancel(true);
            if (!cancelled) {
                logger.log(Level.INFO, "Unable to cancel file ingest worker, likely already stopped");
            }

            abstractFileIngester = null;
        }

        // RJCTODO: Restore or replace use of IngestDataSourceThread 
//        List<IngestDataSourceThread> toStop = new ArrayList<>();
//        toStop.addAll(dataSourceIngesters);
//
//        for (IngestDataSourceThread dataSourceWorker : toStop) {
//            IngestModuleDataSource s = dataSourceWorker.getModule();
//
//            //stop the worker thread if thread is running
//            boolean cancelled = dataSourceWorker.cancel(true);
//            if (!cancelled) {
//                logger.log(Level.INFO, "Unable to cancel data source ingest worker for module: " 
//                        + dataSourceWorker.getModule().getName() + " data source: " + dataSourceWorker.getContent().getName());
//            }
//
//            //stop notification to module to cleanup resources
//            if (isModuleRunning(s)) {
//                try {
//                    dataSourceWorker.getModule().stop();
//                } catch (Exception e) {
//                    logger.log(Level.WARNING, "Exception while stopping module: " + s.getName(), e);
//                }
//            }
//        }

        logger.log(Level.INFO, "stopped all");
    }

    // RJCTODO: This comment is misleading
    /**
     * Test if any ingest modules are running
     *
     * @return true if any module is running, false otherwise
     */
    public synchronized boolean isIngestRunning() {
        if (isEnqueueRunning()) {
            return true;
        } else if (isFileIngestRunning()) {
            return true;
        } else if (isDataSourceIngestRunning()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * check if ingest is currently being enqueued
     */
    public synchronized boolean isEnqueueRunning() {
        if (queueWorker != null && !queueWorker.isDone()) {
            return true;
        }
        return false;
    }

    /**
     * check if the file-level ingest pipeline is running
     */
    public synchronized boolean isFileIngestRunning() {
        if (abstractFileIngester != null && !abstractFileIngester.isDone()) {
            return true;
        }
        return false;
    }

    /**
     * check the status of the data-source-level ingest pipeline
     */
    public synchronized boolean isDataSourceIngestRunning() {
        // RJCTODO: Data source ingest temporarily disabled
//        if (dataSourceIngesters.isEmpty()) {
//            return false;
//        }

        //in case there are still data source ingesters in the queue but already done
//        boolean allDone = true;
//        for (IngestDataSourceThread ii : dataSourceIngesters) {
//            if (ii.isDone() == false) {
//                allDone = false;
//                break;
//            }
//        }
//        if (allDone) {
//            return false;
//        } else {
//            return true;
//        }
        return false;
    }
    
     /**
     * Check if data source scheduler has files in queues
     * @return true if more sources in queues, false otherwise
     */
    // RJCTODO: What is this little wrapper about?
//    public boolean getDataSourceSchedulerHasNext() {
//        return this.scheduler.getDataSourceScheduler().hasNext();
//    }
    
    /**
     * Check if file scheduler has files in queues
     * @return true if more files in queues, false otherwise
     */
    // RJCTODO: What is this little wrapper about?
//    public boolean getFileSchedulerHasNext() {
//        return scheduler.getFileScheduler().hasNext();
//    }
    
    // RJCTODO: May not be used. May or may not reimplement stats class.
    /**
     * returns ingest summary report (how many files ingested, any errors, etc)
     */
//    String getReport() {
//        return stats.toString();
//    }
    
    /**
     * Module publishes message using InegestManager handle Does not block. The
     * message gets enqueued in the GUI thread and displayed in a widget
     * IngestModule should make an attempt not to publish the same message
     * multiple times. Viewer will attempt to identify duplicate messages and
     * filter them out (slower)
     */
    void postMessage(final IngestMessage message) {
        // RJCTODO: May ort may not reimplement stats class
//        if (stats != null) {
//            //record the error for stats, if stats are running
//            if (message.getMessageType() == MessageType.ERROR) {
//                stats.addError(message.getSource());
//            }
//        }
        if (ui != null) {
            ui.displayMessage(message);
        }
    }

    // RJCTODO: Um, what is this? Appears to be used only by PstParser, seems a bit awkward
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

    //data source worker to remove itself when complete or interrupted
    void removeDataSourceIngestWorker(IngestDataSourceThread worker) {
        //remove worker
        synchronized (this) {
            dataSourceIngesters.remove(worker);
        }
    }

// RJCTODO: Decide whether or not to reimplement this class
    /**
     * collects IngestManager statistics during runtime
     */
//    private class IngestManagerStats {
//
//        private Date startTime;
//        private Date endTime;
//        private int errorsTotal;
//        private Map<String, Integer> errors;
//        private final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        private final StopWatch timer = new StopWatch();
//        private IngestModuleAbstract currentModuleForTimer;
//        //file module timing stats, datasource module timers are logged in IngestDataSourceThread class
//        private final Map<String, Long> fileModuleTimers = new HashMap<String, Long>();
//
//        IngestManagerStats() {
//            errors = new HashMap<String, Integer>();
//        }
//
//        /**
//         * records start time of the file process for the module must be
//         * followed by logFileModuleEndProcess for the same module
//         *
//         * @param module to record start time for processing a file
//         */
//        void logFileModuleStartProcess(IngestModuleAbstract module) {
//            timer.reset();
//            timer.start();
//            currentModuleForTimer = module;
//        }
//
//        /**
//         * records stop time of the file process for the module must be preceded
//         * by logFileModuleStartProcess for the same module
//         *
//         * @param module to record stop time for processing a file
//         */
//        void logFileModuleEndProcess(IngestModuleAbstract module) {
//            timer.stop();
//            if (module != currentModuleForTimer) {
//                logger.log(Level.WARNING, "Invalid module passed in to record stop processing: " + module.getName()
//                        + ", expected: " + currentModuleForTimer.getName());
//            } else {
//                final long elapsed = timer.getElapsedTime();
//                final long current = fileModuleTimers.get(module.getName());
//                fileModuleTimers.put(module.getName(), elapsed + current);
//            }
//
//            currentModuleForTimer = null;
//        }
//
//        String getFileModuleStats() {
//            StringBuilder sb = new StringBuilder();
//            for (final String moduleName : fileModuleTimers.keySet()) {
//                sb.append(moduleName).append(" took: ")
//                        .append(fileModuleTimers.get(moduleName) / 1000)
//                        .append(" secs. to process()").append('\n');
//            }
//            return sb.toString();
//        }
//
//        @Override
//        public String toString() {
//            final String EOL = System.getProperty("line.separator");
//            StringBuilder sb = new StringBuilder();
//            if (startTime != null) {
//                sb.append("Start time: ").append(dateFormatter.format(startTime)).append(EOL);
//            }
//            if (endTime != null) {
//                sb.append("End time: ").append(dateFormatter.format(endTime)).append(EOL);
//            }
//            sb.append("Total ingest time: ").append(getTotalTimeString()).append(EOL);
//            sb.append("Total errors: ").append(errorsTotal).append(EOL);
//            if (errorsTotal > 0) {
//                sb.append("Errors per module:");
//                for (String moduleName : errors.keySet()) {
//                    sb.append("\t").append(moduleName).append(": ").append(errors.get(moduleName)).append(EOL);
//                }
//            }
//            return sb.toString();
//        }
//
//        public String toHtmlString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append("<html><body>");
//            sb.append("Ingest time: ").append(getTotalTimeString()).append("<br />");
//            sb.append("Total errors: ").append(errorsTotal).append("<br />");
//            sb.append("<table><tr><th>Module</th><th>Time</th><th>Errors</th></tr>\n");
//            
//            for (final String moduleName : fileModuleTimers.keySet()) {
//                sb.append("<tr><td>").append(moduleName).append("</td><td>");
//                sb.append(msToString(fileModuleTimers.get(moduleName))).append("</td><td>");
//                if (errors.get(moduleName) == null) {
//                    sb.append("0");
//                } else {
//                    sb.append(errors.get(moduleName));
//                }
//                sb.append("</td></tr>\n");
//            }
//            sb.append("</table>");
//            sb.append("</body></html>");
//            return sb.toString();
//        }
//
//        void start() {
//            startTime = new Date();
//
//            for (IngestModuleAbstractFile module : abstractFileModules) {
//                fileModuleTimers.put(module.getName(), 0L);
//            }
//        }
//
//        void end() {
//            endTime = new Date();
//        }
//
//        long getTotalTime() {
//            if (startTime == null || endTime == null) {
//                return 0;
//            }
//            return endTime.getTime() - startTime.getTime();
//        }
//
//        String getStartTimeString() {
//            return dateFormatter.format(startTime);
//        }
//
//        String getEndTimeString() {
//            return dateFormatter.format(endTime);
//        }
//
//        /**
//         * convert time in miliseconds to printable string in XX:YY:ZZ format. 
//         * @param ms
//         * @return 
//         */
//        private String msToString(long ms) {
//            long hours = TimeUnit.MILLISECONDS.toHours(ms);
//            ms -= TimeUnit.HOURS.toMillis(hours);
//            long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
//            ms -= TimeUnit.MINUTES.toMillis(minutes);
//            long seconds = TimeUnit.MILLISECONDS.toSeconds(ms);
//            final StringBuilder sb = new StringBuilder();
//            sb.append(hours < 10 ? "0" : "").append(hours).append(':').append(minutes < 10 ? "0" : "").append(minutes).append(':').append(seconds < 10 ? "0" : "").append(seconds);
//            return sb.toString();
//        }
//        
//        String getTotalTimeString() {
//            long ms = getTotalTime();
//            return msToString(ms);
//        }
//
//        synchronized void addError(IngestModuleAbstract source) {
//            ++errorsTotal;
//            String moduleName = source.getName();
//            Integer curModuleErrorI = errors.get(moduleName);
//            if (curModuleErrorI == null) {
//                errors.put(moduleName, 1);
//            } else {
//                errors.put(moduleName, curModuleErrorI + 1);
//            }
//        }
//    }

    /**
     * File ingest pipeline processor. Worker thread that queries
     * the scheduler for new files.  
     * Modules are assumed to already be initialized. 
     * runs until AbstractFile queue is
     * consumed New instance is created and started when data arrives and
     * previous pipeline completed.
     */
    private class IngestAbstractFileProcessor extends SwingWorker<Object, Void> {

        private Logger logger = Logger.getLogger(IngestAbstractFileProcessor.class.getName());
        private ProgressHandle progress;
        
        @Override
        protected Object doInBackground() throws Exception {

            logger.log(Level.INFO, "Starting background ingest file processor");
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());

            // RJCTODO: This may or may not be reimplemented
//            stats.start();

            // RJCTODO: Interim version of this follows, is it really needed? 
            // Could replace with an ingest started event?
            //notify main thread modules started
//            for (IngestModuleAbstractFile s : abstractFileModules) {
//                IngestManager.fireModuleEvent(IngestModuleEvent.STARTED.toString(), s.getName());
//            }
            if (null != currentTask) {
                List<IngestModuleTemplate> moduleTemplates = currentTask.getIngestPipelines().getIngestModuleTemplates();
                for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
                    if (moduleTemplate.isEnabled()) {
                        IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                        if (moduleFactory.isFileIngestModuleFactory()) {
                            fireModuleEvent(IngestModuleEvent.STARTED.toString(), moduleFactory.getModuleDisplayName());
                        }
                    }
                }
            }

            final String displayName = "File Ingest";
            progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Filed ingest cancelled by user.");
                    if (progress != null) {
                        progress.setDisplayName(displayName + " (Cancelling...)");
                    }
                    return IngestAbstractFileProcessor.this.cancel(true);
                }
            });

            final IngestScheduler.FileScheduler fileScheduler = scheduler.getFileScheduler();

            //initialize the progress bar
            progress.start();
            progress.switchToIndeterminate();
            //set initial totals and processed (to be updated as we process or new files are scheduled)
            int totalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
            progress.switchToDeterminate(totalEnqueuedFiles);
            int processedFiles = 0;
            
            //process AbstractFiles queue
            while (fileScheduler.hasNext()) {
                FileTask fileTask = fileScheduler.next();
                fileTask.execute(1); // RJCTODO: Fake thread id, may not need thread ids
                
                // RJCTODO: This may be obsolete
                //clear return values from modules for last file
//                synchronized (abstractFileModulesRetValues) {
//                    abstractFileModulesRetValues.clear();
//                }

                //logger.log(Level.INFO, "IngestManager: Processing: {0}", fileToProcess.getName());

                // RJCTODO: Note cancellation check after each module runs
//                for (IngestModuleAbstractFile module : dataSourceTask.getModules()) {
//                    //process the file with every file module
//                    if (isCancelled()) {
//                        logger.log(Level.INFO, "Terminating file ingest due to cancellation.");
//                        return null;
//                    }
//                    progress.progress(fileToProcess.getName() + " (" + module.getName() + ")", processedFiles);
//
//                    try {
//                        stats.logFileModuleStartProcess(module);
//                        IngestModuleAbstractFile.ProcessResult result = module.process(filepipelineContext, fileToProcess);
//                        stats.logFileModuleEndProcess(module);
//
//                        //store the result for subsequent modules for this file
//                        synchronized (abstractFileModulesRetValues) {
//                            abstractFileModulesRetValues.put(module.getName(), result);
//                        }
//
//                    } catch (Exception e) {
//                        logger.log(Level.SEVERE, "Error: unexpected exception from module: " + module.getName(), e);
//                        stats.addError(module);
//                    } catch (OutOfMemoryError e) {
//                        logger.log(Level.SEVERE, "Error: out of memory from module: " + module.getName(), e);
//                        stats.addError(module);
//                    }
//                
//                } //end for every module
//                
//                //free the internal file resource after done with every module
//                fileToProcess.close();
//                
//                // notify listeners thsi file is done
//                fireFileDone(fileToProcess.getId());                

                // RJCTODO: Move this to the DataSourceTask -> DataSourceIngestJob, FileIngestJob (?); "task" is usually used for Runnable 
                int newTotalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
                if (newTotalEnqueuedFiles > totalEnqueuedFiles) {
                    //update if new enqueued
                    totalEnqueuedFiles = newTotalEnqueuedFiles + 1;// + processedFiles + 1;
                    //processedFiles = 0; // RJCTODO: Previously commented out
                    //reset // RJCTODO: Previously commented out
                    progress.switchToIndeterminate();
                    progress.switchToDeterminate(totalEnqueuedFiles);
                }
                if (processedFiles < totalEnqueuedFiles) { //fix for now to handle the same datasource Content enqueued twice
                    ++processedFiles;
                }
                //--totalEnqueuedFiles; // RJCTODO: Previously commented out


            } //end of for every AbstractFile
            logger.log(Level.INFO, "IngestManager: Finished processing files");
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get(); //block and get all exceptions thrown while doInBackground()
                //notify modules of completion
                if (!this.isCancelled()) {
//                    for (IngestModuleAbstractFile s : abstractFileModules) {
//                        try {
//                            s.complete();
//                            IngestManager.fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), s.getName());
//                        }
//                        catch (Exception ex) {   
//                            logger.log(Level.SEVERE, "Module " + s.getName() + " threw exception during call to complete()", ex);
//                        }
//                    }
                    if (null != currentTask) {
                        currentTask.getIngestPipelines().completeFileIngestPipeline();
                        List<IngestModuleTemplate> moduleTemplates = currentTask.getIngestPipelines().getIngestModuleTemplates();
                        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
                            if (moduleTemplate.isEnabled()) {
                                IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                                if (moduleFactory.isFileIngestModuleFactory()) {
                                    fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), moduleFactory.getModuleDisplayName());
                                }
                            }
                        }
                    }
                }
                
                // RJCTODO: Running the garbage collector? Really? For the sake of stats?
                logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
                logger.log(Level.INFO, "Freeing jvm heap resources post file pipeline run");
                System.gc();
                logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());

            } catch (CancellationException | InterruptedException e) {
                //task was cancelled
                handleInterruption();
            } catch (ExecutionException ex) {
                handleInterruption();
                logger.log(Level.SEVERE, "Fatal error during ingest.", ex);
            } catch (Exception ex) {
                handleInterruption();
                logger.log(Level.SEVERE, "Fatal error during ingest.", ex);
            } finally {
                    // RJCTODO: Stats may or may not be reimplemented
//                stats.end();
                progress.finish();

                if (!this.isCancelled()) {
                    // RJCTODO: Stats may or may not be reimplemented
//                    logger.log(Level.INFO, "Summary Report: " + stats.toString());
//                    logger.log(Level.INFO, "File module timings: " + stats.getFileModuleStats());
                    if (ui != null) {
                        logger.log(Level.INFO, "Ingest messages count: {0}", ui.getMessagesCount());
                    }
                    // RJCTODO: Stats may or may not be reimplemented
//                    IngestManager.this.postMessage(IngestMessage.createManagerMessage("File Ingest Complete",
//                            stats.toHtmlString()));
                }
            }
        }

        private void handleInterruption() {
//            for (IngestModuleAbstractFile s : abstractFileModules) {
                // RJCTODO: This is going away
//                if (isModuleRunning(s)) {
//                    try {
//                        s.stop();
//                    } catch (Exception e) {
//                        logger.log(Level.WARNING, "Exception while stopping module: " + s.getName(), e);
//                    }
//                }
//                IngestManager.fireModuleEvent(IngestModuleEvent.STOPPED.toString(), s.getName());
//            }
                if (null != currentTask) {
                    currentTask.getIngestPipelines().stopFileIngestPipeline();
                    List<IngestModuleTemplate> moduleTemplates = currentTask.getIngestPipelines().getIngestModuleTemplates();
                    for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
                        if (moduleTemplate.isEnabled()) {
                            IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                            if (moduleFactory.isFileIngestModuleFactory()) {
                                fireModuleEvent(IngestModuleEvent.STOPPED.toString(), moduleFactory.getModuleDisplayName());
                            }
                        }
                    }
                }
            
            
            
            //empty queues
            scheduler.getFileScheduler().empty();
        }
    }

    /**
     * Thread that adds content/file and module pairs to queues.  
     * Starts pipelines when done. */
    private class EnqueueWorker extends SwingWorker<Object, Void> {
        private final List<Content> dataSources;
        private final List<IngestModuleTemplate> moduleTemplates;
        private final boolean processUnallocatedSpace;
        private final Logger logger = Logger.getLogger(EnqueueWorker.class.getName());
        private ProgressHandle progress; // RJCTODO: Is this useful?

        EnqueueWorker(final List<Content> dataSources, final List<IngestModuleTemplate> moduleTemplates, final boolean processUnallocatedSpace) {
            this.dataSources = dataSources;
            this.moduleTemplates = moduleTemplates;
            this. processUnallocatedSpace = processUnallocatedSpace;
        }

        @Override
        protected Object doInBackground() throws Exception {
            final String displayName = "Queueing Ingest";
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
                DataSourceIngestJob dataSourceTask = new DataSourceIngestJob(IngestManager.this.getNextDataSourceTaskId(), dataSource, moduleTemplates, processUnallocatedSpace);                
                logger.log(Level.INFO, "Queing data source ingest task: {0}", dataSourceTask);
                progress.progress("DataSource Ingest" + " " + inputName, processed);
                scheduler.getDataSourceScheduler().schedule(dataSourceTask);
                progress.progress("DataSource Ingest" + " " + inputName, ++processed);
                             
                logger.log(Level.INFO, "Queing file ingest task: {0}", dataSourceTask);
                progress.progress("File Ingest" + " " + inputName, processed);
                scheduler.getFileScheduler().scheduleIngestOfFiles(dataSourceTask);
                progress.progress("File Ingest" + " " + inputName, ++processed);
                
                currentTask = dataSourceTask; // RJCTODO: temporary glue code
            }
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get();
            } 
            catch (CancellationException | InterruptedException | ExecutionException ex) {
                handleInterruption(ex);
            } 
            catch (Exception ex) {
                handleInterruption(ex);
            } 
            finally {
                if (this.isCancelled()) {
                    handleInterruption(new Exception());
                } 
                else {
                    startAll();
                }
                progress.finish();
            }
        }

        private void handleInterruption(Exception ex) {
            logger.log(Level.SEVERE, "Error while enqueing files. ", ex); // RJCTODO: Not really, could be routine cancellation
            scheduler.getFileScheduler().empty();
            scheduler.getDataSourceScheduler().empty();
        }
    }
    
    // RJCTODO: This is a work in progress, the replacement for IngestAbstractFileProcessor
    private class FileIngestWorker implements Runnable {  
        private final int id;
        
        FileIngestWorker(int id) {
            this.id = id;
        }
        
        @Override
        public void run() {
            IngestScheduler.FileScheduler fileScheduler = scheduler.getFileScheduler();
            while (fileScheduler.hasNext()) {
                FileTask fileTask = fileScheduler.next();
                fileTask.execute(id);
            }
                        
//            logger.log(Level.INFO, "IngestManager: Finished processing files"); // RJCTODO: Not true
        }        
    }
}
