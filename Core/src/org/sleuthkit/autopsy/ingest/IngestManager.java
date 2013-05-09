/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestScheduler.FileScheduler.ProcessTask;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Image;

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
    private IngestManagerStats stats;
    private boolean processUnallocSpace = true;
    //queues
    private final IngestScheduler scheduler;
    //workers
    private IngestAbstractFileProcessor abstractFileIngester;
    private List<IngestImageThread> imageIngesters;
    private SwingWorker<Object, Void> queueWorker;
    //modules
    private List<IngestModuleImage> imageModules;
    private List<IngestModuleAbstractFile> abstractFileModules;
    // module return values
    private final Map<String, IngestModuleAbstractFile.ProcessResult> abstractFileModulesRetValues = new HashMap<String, IngestModuleAbstractFile.ProcessResult>();
    //notifications
    private final static PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);
    //monitor
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    //module loader
    private IngestModuleLoader moduleLoader = null;
    //property file name id for the module
    final static String MODULE_PROPERTIES = "ingest";

    /**
     * Possible events about ingest modules Event listeners can get the event
     * name by using String returned by toString() method on the specific event.
     */
    public enum IngestModuleEvent {

        /**
         * Event sent when the ingest module has been started processing. Second
         * argument of the property change fired contains module name String and
         * third argument is null.
         */
        STARTED,
        /**
         * Event sent when the ingest module has completed processing. Second
         * argument of the property change fired contains module name String and
         * third argument is null.
         *
         * This event is generally used by listeners to perform a final data
         * view refresh (listeners need to query all data from the blackboard).
         *
         */
        COMPLETED,
        /**
         * Event sent when the ingest module has stopped processing, and likely
         * not all data has been processed. Second argument of the property
         * change fired contains module name String and third argument is null.
         */
        STOPPED,
        /**
         * Event sent when ingest module has new data. Second argument of the
         * property change fired contains ModuleDataEvent object and third
         * argument is null. The object can contain encapsulated new data
         * created by the module. Listener can also query new data as needed.
         *
         */
        DATA,
        /**
         * Event send when content changed, either its attributes changed, or
         * new content children have been added
         */
        CONTENT_CHANGED
    };
    //ui
    //Initialized by Installer in AWT thread once the Window System is ready
    private volatile IngestUI ui; // = null; //IngestMessageTopComponent.findInstance();
    //singleton
    private static volatile IngestManager instance;

    private IngestManager() {
        imageIngesters = new ArrayList<IngestImageThread>();

        scheduler = IngestScheduler.getInstance();

        //setup current modules and listeners for modules changes
        initModules();

    }

    private void initModules() {
        try {
            moduleLoader = IngestModuleLoader.getDefault();
            abstractFileModules = moduleLoader.getAbstractFileIngestModules();

            moduleLoader.addModulesReloadedListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(IngestModuleLoader.Event.ModulesReloaded.toString())) {
                        //TODO might need to not allow to remove modules if they are running
                        abstractFileModules = moduleLoader.getAbstractFileIngestModules();
                        imageModules = moduleLoader.getImageIngestModules();
                    }
                }
            });
            imageModules = moduleLoader.getImageIngestModules();
        } catch (IngestModuleLoaderException ex) {
            logger.log(Level.SEVERE, "Error getting module loader");
        }


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
     * Add property change listener to listen to ingest events
     *
     * @param l PropertyChangeListener to schedule
     */
    public static synchronized void addPropertyChangeListener(final PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    static synchronized void fireModuleEvent(String eventType, String moduleName) {
        pcs.firePropertyChange(eventType, moduleName, null);
    }

    static synchronized void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        pcs.firePropertyChange(IngestModuleEvent.DATA.toString(), moduleDataEvent, null);
    }

    static synchronized void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        pcs.firePropertyChange(IngestModuleEvent.CONTENT_CHANGED.toString(), moduleContentEvent, null);
    }

    /**
     * Returns the return value from a previously run module on the file being
     * currently analyzed.
     *
     * @param moduleName Name of module.
     * @returns Return value from that module if it was previously run.
     */
    IngestModuleAbstractFile.ProcessResult getAbstractFileModuleResult(String moduleName) {
        synchronized (abstractFileModulesRetValues) {
            if (abstractFileModulesRetValues.containsKey(moduleName)) {
                return abstractFileModulesRetValues.get(moduleName);
            } else {
                return IngestModuleAbstractFile.ProcessResult.UNKNOWN;
            }
        }
    }

    /**
     * Multiple image version of execute() method. Enqueues multiple images and
     * associated modules at once
     *
     * @param modules modules to execute on every image
     * @param images images to execute modules on
     */
    public void execute(final List<IngestModuleAbstract> modules, final List<Image> images) {
        logger.log(Level.INFO, "Will enqueue number of images: " + images.size() + " to " + modules.size() + " modules.");

        if (!isIngestRunning() && ui != null) {
            ui.clearMessages();
        }

        queueWorker = new EnqueueWorker(modules, images);
        queueWorker.execute();

        if (ui != null) {
            ui.restoreMessages();
        }
        //logger.log(Level.INFO, "Queues: " + imageQueue.toString() + " " + AbstractFileQueue.toString());
    }

    /**
     * IngestManager entry point, enqueues image to be processed. Spawns
     * background thread which enumerates all sorted files and executes chosen
     * modules per file in a pre-determined order. Notifies modules when work is
     * complete or should be interrupted using complete() and stop() calls. Does
     * not block and can be called multiple times to enqueue more work to
     * already running background process.
     *
     * @param modules modules to execute on the image
     * @param image image to execute modules on
     */
    public void execute(final List<IngestModuleAbstract> modules, final Image image) {
        List<Image> images = new ArrayList<Image>();
        images.add(image);
        logger.log(Level.INFO, "Will enqueue image: " + image.getName());
        execute(modules, images);
    }

    /**
     * Schedule a file for ingest. Scheduler updates the current progress.
     *
     * The file is usually a product of a recently ran ingest. Now we want to
     * process this file with the same ingest context.
     *
     * @param file file to be scheduled
     * @param pipelineContext ingest context used to ingest parent of the file
     * to be scheduled
     */
    void scheduleFile(AbstractFile file, PipelineContext pipelineContext) {
        scheduler.getFileScheduler().schedule(file, pipelineContext);
    }

    /**
     * Starts the needed worker threads.
     *
     * if AbstractFile module is still running, do nothing and allow it to
     * consume queue otherwise start /restart AbstractFile worker
     *
     * image workers run per (module,image). Check if one for the (module,image)
     * is already running otherwise start/restart the worker
     */
    private synchronized void startAll() {
        final IngestScheduler.ImageScheduler imageScheduler = scheduler.getImageScheduler();
        final IngestScheduler.FileScheduler fileScheduler = scheduler.getFileScheduler();

        logger.log(Level.INFO, "Image queue: " + imageScheduler.toString());
        logger.log(Level.INFO, "File queue: " + fileScheduler.toString());

        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }

        //image ingesters
        // cycle through each image in the queue
        while (imageScheduler.hasNext()) {
            //dequeue
            // get next image and set of modules
            final ScheduledImageTask<IngestModuleImage> imageTask = imageScheduler.next();

            // check if each module for this image is already running
            for (IngestModuleImage taskModule : imageTask.getModules()) {
                boolean alreadyRunning = false;
                for (IngestImageThread worker : imageIngesters) {
                    // ignore threads that are on different images
                    if (!worker.getImage().equals(imageTask.getImage())) {
                        continue; //check next worker
                    }
                    //same image, check module (by name, not id, since different instances)
                    if (worker.getModule().getName().equals(taskModule.getName())) {
                        alreadyRunning = true;
                        logger.log(Level.INFO, "Image Ingester <" + imageTask.getImage()
                                + ", " + taskModule.getName() + "> is already running");
                        break;
                    }
                }
                //checked all workers
                if (alreadyRunning == false) {
                    logger.log(Level.INFO, "Starting new image Ingester <" + imageTask.getImage()
                            + ", " + taskModule.getName() + ">");
                    //image modules are now initialized per instance

                    IngestModuleInit moduleInit = new IngestModuleInit();
                    moduleInit.setModuleArgs(taskModule.getArguments());
                    PipelineContext<IngestModuleImage> imagepipelineContext =
                            new PipelineContext<IngestModuleImage>(imageTask, getProcessUnallocSpace());
                    final IngestImageThread newImageWorker = new IngestImageThread(this,
                            imagepipelineContext, imageTask.getImage(), taskModule, moduleInit);

                    imageIngesters.add(newImageWorker);

                    //wrap the module in a worker, that will run init, process and complete on the module
                    newImageWorker.execute();
                    IngestManager.fireModuleEvent(IngestModuleEvent.STARTED.toString(), taskModule.getName());
                }
            }
        }
        //}


        //AbstractFile ingester
        boolean startAbstractFileIngester = false;
        if (fileScheduler.hasNext()) {
            if (abstractFileIngester
                    == null) {
                startAbstractFileIngester = true;
                logger.log(Level.INFO, "Starting initial AbstractFile ingester");
            } //if worker had completed, restart it in case data is still enqueued
            else if (abstractFileIngester.isDone()) {
                startAbstractFileIngester = true;
                logger.log(Level.INFO, "Restarting AbstractFile ingester");
            }
        } else {
            logger.log(Level.INFO, "no new AbstractFile enqueued, no ingester needed");
        }

        if (startAbstractFileIngester) {
            stats = new IngestManagerStats();
            abstractFileIngester = new IngestAbstractFileProcessor();
            //init all fs modules, everytime new worker starts
            for (IngestModuleAbstractFile s : abstractFileModules) {
                IngestModuleInit moduleInit = new IngestModuleInit();
                moduleInit.setModuleArgs(s.getArguments());
                try {
                    s.init(moduleInit);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "File ingest module failed init(): " + s.getName());
                }
            }
            abstractFileIngester.execute();
        }
    }

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
        scheduler.getImageScheduler().empty();

        //stop module workers
        if (abstractFileIngester != null) {
            //send signals to all file modules
            for (IngestModuleAbstractFile s : this.abstractFileModules) {
                if (isModuleRunning(s)) {
                    try {
                        s.stop();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Unexpected exception while stopping module: " + s.getName(), e);
                    }
                }

            }
            //stop fs ingester thread
            boolean cancelled = abstractFileIngester.cancel(true);
            if (!cancelled) {
                logger.log(Level.INFO, "Unable to cancel file ingest worker, likely already stopped");
            }

            abstractFileIngester = null;

        }

        List<IngestImageThread> toStop = new ArrayList<IngestImageThread>();
        toStop.addAll(imageIngesters);


        for (IngestImageThread imageWorker : toStop) {
            IngestModuleImage s = imageWorker.getModule();

            //stop the worker thread if thread is running
            boolean cancelled = imageWorker.cancel(true);
            if (!cancelled) {
                logger.log(Level.INFO, "Unable to cancel image ingest worker for module: " + imageWorker.getModule().getName() + " img: " + imageWorker.getImage().getName());
            }

            //stop notification to module to cleanup resources
            if (isModuleRunning(s)) {
                try {
                    imageWorker.getModule().stop();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Exception while stopping module: " + s.getName(), e);
                }
            }

        }

        logger.log(Level.INFO, "stopped all");
    }

    /**
     * Test if any ingester modules are running
     *
     * @return true if any module is running, false otherwise
     */
    public synchronized boolean isIngestRunning() {
        if (isEnqueueRunning()) {
            return true;
        } else if (isFileIngestRunning()) {
            return true;
        } else if (isImageIngestRunning()) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Test is any file ingest modules are running.
     *
     * @return true if any ingest modules are running, false otherwise
     */
    public synchronized boolean areModulesRunning() {
        for (IngestModuleAbstract serv : abstractFileModules) {
            if (serv.hasBackgroundJobsRunning()) {
                return true;
            }
        }
        for (IngestImageThread thread : imageIngesters) {
            if (isModuleRunning(thread.getModule())) {
                return false;
            }
        }
        return false;
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
     * check the status of the image-level ingest pipeline
     */
    public synchronized boolean isImageIngestRunning() {
        if (imageIngesters.isEmpty()) {
            return false;
        }

        //in case there are still image ingesters in the queue but already done
        boolean allDone = true;
        for (IngestImageThread ii : imageIngesters) {
            if (ii.isDone() == false) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * check if the module is running (was started and not yet complete/stopped)
     * give a complete answer, i.e. it's already consumed all files but it might
     * have background threads running
     *
     */
    public boolean isModuleRunning(final IngestModuleAbstract module) {

        if (module.getType() == IngestModuleAbstract.ModuleType.AbstractFile) {
            IngestScheduler.FileScheduler fileScheduler = scheduler.getFileScheduler();

            if (fileScheduler.hasModuleEnqueued((IngestModuleAbstractFile) module)) {
                //has work enqueued, so running
                return true;
            } else {
                //not in the queue, but could still have bkg work running
                return module.hasBackgroundJobsRunning();
            }


        } else {
            //image module
            synchronized (this) {
                if (imageIngesters.isEmpty()) {
                    return false;
                }
                IngestImageThread imt = null;
                for (IngestImageThread ii : imageIngesters) {
                    if (ii.getModule().equals(module)) {
                        imt = ii;
                        break;
                    }
                }

                if (imt == null) {
                    return false;
                }

                if (imt.isDone() == false) {
                    return true;
                } else {
                    return false;
                }
            }

        }


    }

    /**
     * returns if manager is currently configured to process unalloc space
     *
     * @return true if process unaloc space is set
     */
    boolean getProcessUnallocSpace() {
        return processUnallocSpace;
    }

    /**
     * Sets process unalloc space setting on the manager
     *
     * @param processUnallocSpace
     */
    void setProcessUnallocSpace(boolean processUnallocSpace) {
        this.processUnallocSpace = processUnallocSpace;
    }

    /**
     * returns ingest summary report (how many files ingested, any errors, etc)
     */
    String getReport() {
        return stats.toString();
    }

    /**
     * Module publishes message using InegestManager handle Does not block. The
     * message gets enqueued in the GUI thread and displayed in a widget
     * IngestModule should make an attempt not to publish the same message
     * multiple times. Viewer will attempt to identify duplicate messages and
     * filter them out (slower)
     */
    void postMessage(final IngestMessage message) {

        if (stats != null) {
            //record the error for stats, if stats are running
            if (message.getMessageType() == MessageType.ERROR) {
                stats.addError(message.getSource());
            }
        }
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

    /**
     * helper to return all loaded image modules managed sorted in order as
     * specified in pipeline_config XML
     */
    public List<IngestModuleImage> enumerateImageModules() {
        return moduleLoader.getImageIngestModules();
    }

    /**
     * helper to return all loaded file modules managed sorted in order as
     * specified in pipeline_config XML
     */
    public List<IngestModuleAbstractFile> enumerateAbstractFileModules() {
        return moduleLoader.getAbstractFileIngestModules();
    }

    //image worker to remove itself when complete or interrupted
    void removeImageIngestWorker(IngestImageThread worker) {
        //remove worker
        synchronized (this) {
            imageIngesters.remove(worker);
        }
    }

    /**
     * collects IngestManager statistics during runtime
     */
    private class IngestManagerStats {

        private Date startTime;
        private Date endTime;
        private int errorsTotal;
        private Map<IngestModuleAbstract, Integer> errors;
        private final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        private final StopWatch timer = new StopWatch();
        private IngestModuleAbstract currentModuleForTimer;
        //file module timing stats, image module timers are logged in IngestImageThread class
        private final Map<String, Long> fileModuleTimers = new HashMap<String, Long>();

        IngestManagerStats() {
            errors = new HashMap<IngestModuleAbstract, Integer>();

        }

        /**
         * records start time of the file process for the module must be
         * followed by logFileModuleEndProcess for the same module
         *
         * @param module to record start time for processing a file
         */
        void logFileModuleStartProcess(IngestModuleAbstract module) {
            timer.reset();
            timer.start();
            currentModuleForTimer = module;
        }

        /**
         * records stop time of the file process for the module must be preceded
         * by logFileModuleStartProcess for the same module
         *
         * @param module to record stop time for processing a file
         */
        void logFileModuleEndProcess(IngestModuleAbstract module) {
            timer.stop();
            if (module != currentModuleForTimer) {
                logger.log(Level.WARNING, "Invalid module passed in to record stop processing: " + module.getName()
                        + ", expected: " + currentModuleForTimer.getName());
            } else {
                final long elapsed = timer.getElapsedTime();
                final long current = fileModuleTimers.get(module.getName());
                fileModuleTimers.put(module.getName(), elapsed + current);
            }

            currentModuleForTimer = null;
        }

        String getFileModuleStats() {
            StringBuilder sb = new StringBuilder();
            for (final String moduleName : fileModuleTimers.keySet()) {
                sb.append(moduleName).append(" took: ")
                        .append(fileModuleTimers.get(moduleName) / 1000)
                        .append(" secs. to process()").append('\n');
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            final String EOL = System.getProperty("line.separator");
            StringBuilder sb = new StringBuilder();
            if (startTime != null) {
                sb.append("Start time: ").append(dateFormatter.format(startTime)).append(EOL);
            }
            if (endTime != null) {
                sb.append("End time: ").append(dateFormatter.format(endTime)).append(EOL);
            }
            sb.append("Total ingest time: ").append(getTotalTimeString()).append(EOL);
            sb.append("Total errors: ").append(errorsTotal).append(EOL);
            if (errorsTotal > 0) {
                sb.append("Errors per module:");
                for (IngestModuleAbstract module : errors.keySet()) {
                    final int errorsModule = errors.get(module);
                    String moduleName;
                    if (module != null) {
                        moduleName = module.getName();
                    } else {
                        //manager message
                        moduleName = "System";
                    }

                    sb.append("\t").append(moduleName).append(": ").append(errorsModule).append(EOL);
                }
            }
            return sb.toString();
        }

        public String toHtmlString() {
            StringBuilder sb = new StringBuilder();
            sb.append("<html>");

            sb.append("Ingest time: ").append(getTotalTimeString()).append("<br />");
            sb.append("Total errors: ").append(errorsTotal).append("<br />");
            /*
             if (errorsTotal > 0) {
             sb.append("Errors per module:");
             for (IngestModuleAbstract module : errors.keySet()) {
             final int errorsModule = errors.get(module);
             sb.append("\t").append(module.getName()).append(": ").append(errorsModule).append("<br />");
             }
             }
             * */

            sb.append("</html>");
            return sb.toString();
        }

        void start() {
            startTime = new Date();

            for (IngestModuleAbstractFile module : abstractFileModules) {
                fileModuleTimers.put(module.getName(), 0L);
            }
        }

        void end() {
            endTime = new Date();
        }

        long getTotalTime() {
            if (startTime == null || endTime == null) {
                return 0;
            }
            return endTime.getTime() - startTime.getTime();
        }

        String getStartTimeString() {
            return dateFormatter.format(startTime);
        }

        String getEndTimeString() {
            return dateFormatter.format(endTime);
        }

        String getTotalTimeString() {
            long ms = getTotalTime();
            long hours = TimeUnit.MILLISECONDS.toHours(ms);
            ms -= TimeUnit.HOURS.toMillis(hours);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
            ms -= TimeUnit.MINUTES.toMillis(minutes);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(ms);
            final StringBuilder sb = new StringBuilder();
            sb.append(hours < 10 ? "0" : "").append(hours).append(':').append(minutes < 10 ? "0" : "").append(minutes).append(':').append(seconds < 10 ? "0" : "").append(seconds);
            return sb.toString();
        }

        synchronized void addError(IngestModuleAbstract source) {
            ++errorsTotal;
            Integer curModuleErrorI = errors.get(source);
            if (curModuleErrorI == null) {
                errors.put(source, 1);
            } else {
                errors.put(source, curModuleErrorI + 1);
            }
        }
    }

    /**
     * File ingest pipeline processor. Worker runs until AbstractFile queue is
     * consumed New instance is created and started when data arrives and
     * previous pipeline completed.
     */
    private class IngestAbstractFileProcessor extends SwingWorker<Object, Void> {

        private Logger logger = Logger.getLogger(IngestAbstractFileProcessor.class.getName());
        //progress  bar
        private ProgressHandle progress;

        @Override
        protected Object doInBackground() throws Exception {

            logger.log(Level.INFO, "Starting background ingest file processor");
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());

            stats.start();

            //notify main thread modules started
            for (IngestModuleAbstractFile s : abstractFileModules) {
                IngestManager.fireModuleEvent(IngestModuleEvent.STARTED.toString(), s.getName());
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
                final ProcessTask fileTask = fileScheduler.next();
                final PipelineContext<IngestModuleAbstractFile> filepipelineContext = fileTask.context;
                final ScheduledImageTask<IngestModuleAbstractFile> fileIngestTask = filepipelineContext.getScheduledTask();
                final AbstractFile fileToProcess = fileTask.file;

                //clear return values from modules for last file
                synchronized (abstractFileModulesRetValues) {
                    abstractFileModulesRetValues.clear();
                }

                logger.log(Level.INFO, "IngestManager: Processing: {0}", fileToProcess.getName());
                progress.progress(fileToProcess.getName(), processedFiles);
                for (IngestModuleAbstractFile module : fileIngestTask.getModules()) {
                    //process the file with every file module
                    if (isCancelled()) {
                        logger.log(Level.INFO, "Terminating file ingest due to cancellation.");
                        return null;
                    }

                    try {
                        stats.logFileModuleStartProcess(module);
                        IngestModuleAbstractFile.ProcessResult result = module.process(filepipelineContext, fileToProcess);
                        stats.logFileModuleEndProcess(module);

                        //store the result for subsequent modules for this file
                        synchronized (abstractFileModulesRetValues) {
                            abstractFileModulesRetValues.put(module.getName(), result);
                        }

                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error: unexpected exception from module: " + module.getName(), e);
                        stats.addError(module);
                    } catch (OutOfMemoryError e) {
                        logger.log(Level.SEVERE, "Error: out of memory from module: " + module.getName(), e);
                        stats.addError(module);
                    }
                } //end for every module

                //free the internal file resource after done with every module
                fileToProcess.close();

                int newTotalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
                if (newTotalEnqueuedFiles > totalEnqueuedFiles) {
                    //update if new enqueued
                    totalEnqueuedFiles = newTotalEnqueuedFiles + 1;// + processedFiles + 1;
                    //processedFiles = 0;
                    //reset
                    progress.switchToIndeterminate();
                    progress.switchToDeterminate(totalEnqueuedFiles);
                }
                if (processedFiles < totalEnqueuedFiles) { //fix for now to handle the same image enqueued twice
                    ++processedFiles;
                }
                //--totalEnqueuedFiles;


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
                    for (IngestModuleAbstractFile s : abstractFileModules) {
                        s.complete();
                        IngestManager.fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), s.getName());
                    }
                }

                logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
                logger.log(Level.INFO, "Freeing jvm heap resources post file pipeline run");
                System.gc();
                logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());

            } catch (CancellationException e) {
                //task was cancelled
                handleInterruption();

            } catch (InterruptedException ex) {
                handleInterruption();
            } catch (ExecutionException ex) {
                handleInterruption();
                logger.log(Level.SEVERE, "Fatal error during ingest.", ex);

            } catch (Exception ex) {
                handleInterruption();
                logger.log(Level.SEVERE, "Fatal error during ingest.", ex);
            } finally {
                stats.end();
                progress.finish();

                if (!this.isCancelled()) {
                    logger.log(Level.INFO, "Summary Report: " + stats.toString());
                    logger.log(Level.INFO, "File module timings: " + stats.getFileModuleStats());
                    if (ui != null) {
                        logger.log(Level.INFO, "Ingest messages count: " + ui.getMessagesCount());
                    }

                    IngestManager.this.postMessage(IngestMessage.createManagerMessage("File Ingest Complete",
                            stats.toHtmlString()));
                }
            }

        }

        private void handleInterruption() {
            for (IngestModuleAbstractFile s : abstractFileModules) {
                if (isModuleRunning(s)) {
                    try {
                        s.stop();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Exception while stopping module: " + s.getName(), e);
                    }
                }
                IngestManager.fireModuleEvent(IngestModuleEvent.STOPPED.toString(), s.getName());
            }
            //empty queues
            scheduler.getFileScheduler().empty();
        }
    }

    /* Thread that adds image/file and module pairs to queues */
    private class EnqueueWorker extends SwingWorker<Object, Void> {

        private List<IngestModuleAbstract> modules;
        private final List<Image> images;
        private final Logger logger = Logger.getLogger(EnqueueWorker.class.getName());

        EnqueueWorker(final List<IngestModuleAbstract> modules, final List<Image> images) {
            this.modules = modules;
            this.images = images;
        }
        private ProgressHandle progress;

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

            progress.start(2 * images.size());
            //progress.switchToIndeterminate();
            queueAll(modules, images);
            return null;
        }

        /* clean up or start the worker threads */
        @Override
        protected void done() {
            try {
                super.get(); //block and get all exceptions thrown while doInBackground()      
            } catch (CancellationException e) {
                //task was cancelled
                handleInterruption(e);
            } catch (InterruptedException ex) {
                handleInterruption(ex);
            } catch (ExecutionException ex) {
                handleInterruption(ex);


            } catch (Exception ex) {
                handleInterruption(ex);

            } finally {
                //queing end
                if (this.isCancelled()) {
                    //empty queues
                    handleInterruption(new Exception());
                } else {
                    //start ingest workers
                    startAll();
                }
                progress.finish();
            }
        }

        private void queueAll(List<IngestModuleAbstract> modules, final List<Image> images) {

            final IngestScheduler.ImageScheduler imageScheduler = scheduler.getImageScheduler();
            final IngestScheduler.FileScheduler fileScheduler = scheduler.getFileScheduler();

            int processed = 0;
            for (Image image : images) {
                final String imageName = image.getName();

                final List<IngestModuleImage> imageMods = new ArrayList<IngestModuleImage>();
                final List<IngestModuleAbstractFile> fileMods = new ArrayList<IngestModuleAbstractFile>();

                for (IngestModuleAbstract module : modules) {
                    if (isCancelled()) {
                        logger.log(Level.INFO, "Terminating ingest queueing due to cancellation.");
                        return;
                    }

                    final String moduleName = module.getName();
                    progress.progress(moduleName + " " + imageName, processed);

                    switch (module.getType()) {
                        case Image:
                            final IngestModuleImage newModuleInstance =
                                    (IngestModuleImage) moduleLoader.getNewIngestModuleInstance(module);
                            if (newModuleInstance != null) {
                                imageMods.add(newModuleInstance);
                            } else {
                                logger.log(Level.INFO, "Error loading module and adding image " + image.getName() + " with module " + module.getName());
                            }

                            break;

                        case AbstractFile:
                            //enqueue the same singleton AbstractFile module
                            logger.log(Level.INFO, "Adding image " + image.getName()
                                    + " number of AbstractFile to module " + module.getName());

                            fileMods.add((IngestModuleAbstractFile) module);
                            break;
                        default:
                            logger.log(Level.SEVERE, "Unexpected module type: " + module.getType().name());
                    }

                }//for modules

                //queue to schedulers
                final boolean processUnalloc = getProcessUnallocSpace();
                final ScheduledImageTask<IngestModuleImage> imageTask = new ScheduledImageTask<IngestModuleImage>(image, imageMods);
                final PipelineContext<IngestModuleImage> imagepipelineContext = new PipelineContext<IngestModuleImage>(imageTask, processUnalloc);
                logger.log(Level.INFO, "Queing image ingest task: " + imageTask);
                progress.progress("Image Ingest" + " " + imageName, processed);
                imageScheduler.schedule(imagepipelineContext);
                progress.progress("Image Ingest" + " " + imageName, ++processed);

                final ScheduledImageTask fTask = new ScheduledImageTask(image, fileMods);
                final PipelineContext<IngestModuleAbstractFile> filepipelineContext = new PipelineContext<IngestModuleAbstractFile>(fTask, processUnalloc);
                logger.log(Level.INFO, "Queing file ingest task: " + fTask);
                progress.progress("File Ingest" + " " + imageName, processed);
                fileScheduler.schedule(filepipelineContext);
                progress.progress("File Ingest" + " " + imageName, ++processed);

            } //for images


            //logger.log(Level.INFO, AbstractFileQueue.printQueue());
        }

        private void handleInterruption(Exception ex) {
            logger.log(Level.SEVERE, "Error while enqueing files. ", ex);
            //empty queues
            scheduler.getFileScheduler().empty();
            scheduler.getImageScheduler().empty();
        }
    }
}
