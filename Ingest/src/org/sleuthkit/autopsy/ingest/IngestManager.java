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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskData;

/**
 * IngestManager sets up and manages ingest modules runs them in a background
 * thread notifies modules when work is complete or should be interrupted
 * processes messages from modules via messenger proxy and posts them to GUI.
 *
 * This runs as a singleton and you can access it using the getDefault() method.
 *
 */
public class IngestManager {

    /**
     * @Deprecated individual modules are be responsible for maintaining such settings
     */
    enum UpdateFrequency {

        FAST(20),
        AVG(10),
        SLOW(5);
        private final int time;

        /**
        * @Deprecated individual modules are be responsible for maintaining such settings
        */
        UpdateFrequency(int time) {
            this.time = time;
        }

        /**
        * @Deprecated individual modules are be responsible for maintaining such settings
        */
        int getTime() {
            return time;
        }
    };
    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private IngestManagerStats stats;
    private volatile UpdateFrequency updateFrequency = UpdateFrequency.AVG;
    private boolean processUnallocSpace = true;
    //queues
    private final ImageQueue imageQueue = new ImageQueue();   // list of modules and images to analyze
    private final AbstractFileQueue abstractFileQueue = new AbstractFileQueue();
    private final Object queuesLock = new Object();
    //workers
    private IngestAbstractFileThread abstractFileIngester;
    private List<IngestImageThread> imageIngesters;
    private SwingWorker<Object, Void> queueWorker;
    //modules
    private List<IngestModuleImage> imageModules;
    private final List<IngestModuleAbstractFile> abstractFileModules;
    // module return values
    private final Map<String, IngestModuleAbstractFile.ProcessResult> abstractFileModulesRetValues = new HashMap<String, IngestModuleAbstractFile.ProcessResult>();
    //notifications
    private final static PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);
    //monitor
    private final IngestMonitor ingestMonitor = new IngestMonitor();

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
        DATA
    };
    //ui
    //Initialized by Installer in AWT thread once the Window System is ready
    private volatile IngestUI ui; // = null; //IngestMessageTopComponent.findInstance();
    //singleton
    private static volatile IngestManager instance;

    private IngestManager() {
        imageIngesters = new ArrayList<IngestImageThread>();
        abstractFileModules = enumerateAbstractFileModules();
        imageModules = enumerateImageModules();
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
    public static synchronized IngestManager getDefault() {
        if (instance == null) {
            logger.log(Level.INFO, "creating manager instance");
            instance = new IngestManager();
        }
        return instance;
    }


    /**
     * Add property change listener to listen to ingest events
     *
     * @param l PropertyChangeListener to add
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
     * Multiple image version of execute, enqueues multiple images and
     * associated modules at once
     *
     * @param modules modules to execute on every image
     * @param images images to execute modules on
     */
    void execute(final List<IngestModuleAbstract> modules, final List<Image> images) {
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
     * modules per file in a pre-determined order. Notifies modules when work
     * is complete or should be interrupted using complete() and stop() calls.
     * Does not block and can be called multiple times to enqueue more work to
     * already running background process.
     *
     * @param modules modules to execute on the image
     * @param image image to execute modules on
     */
    void execute(final List<IngestModuleAbstract> modules, final Image image) {
        List<Image> images = new ArrayList<Image>();
        images.add(image);
        logger.log(Level.INFO, "Will enqueue image: " + image.getName());
        execute(modules, images);
    }

    /**
     * Starts the needed worker threads.
     *
     * if AbstractFile module is still running, do nothing and allow it to
     * consume queue otherwise start /restart AbstractFile worker
     *
     * image workers run per (module,image). Check if one for the
     * (module,image) is already running otherwise start/restart the worker
     */
    private synchronized void startAll() {
        logger.log(Level.INFO, "Image queue: " + this.imageQueue.toString());
        logger.log(Level.INFO, "File queue: " + this.abstractFileQueue.toString());

        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }

        //image ingesters
        // cycle through each image in the queue
        while (hasNextImage()) {
            //dequeue
            // get next image and set of modules
            final Map.Entry<Image, List<IngestModuleImage>> qu =
                    this.getNextImage();


            // check if each module for this image is already running
            //synchronized (this) {
            for (IngestModuleImage quModule : qu.getValue()) {
                boolean alreadyRunning = false;
                for (IngestImageThread worker : imageIngesters) {
                    // ignore threads that are on different images
                    if (!worker.getImage().equals(qu.getKey())) {
                        continue; //check next worker
                    }
                    //same image, check module (by name, not id, since different instances)
                    if (worker.getModule().getName().equals(quModule.getName())) {
                        alreadyRunning = true;
                        logger.log(Level.INFO, "Image Ingester <" + qu.getKey() + ", " + quModule.getName() + "> is already running");
                        break;
                    }
                }
                //checked all workers
                if (alreadyRunning == false) {
                    logger.log(Level.INFO, "Starting new image Ingester <" + qu.getKey() + ", " + quModule.getName() + ">");
                    IngestImageThread newImageWorker = new IngestImageThread(this, qu.getKey(), quModule);

                    imageIngesters.add(newImageWorker);

                    //image modules are now initialized per instance
                    quModule.init(new IngestModuleInit() );
                    newImageWorker.execute();
                    IngestManager.fireModuleEvent(IngestModuleEvent.STARTED.toString(), quModule.getName());
                }
            }
        }
        //}


        //AbstractFile ingester
        boolean startAbstractFileIngester = false;
        if (hasNextAbstractFile()) {
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
            abstractFileIngester = new IngestAbstractFileThread();
            //init all fs modules, everytime new worker starts
            for (IngestModuleAbstractFile s : abstractFileModules) {
                s.init(new IngestModuleInit() );
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
        emptyAbstractFiles();
        emptyImages();

        //stop module workers
        if (abstractFileIngester != null) {
            //send signals to all file modules
            for (IngestModuleAbstractFile s : this.abstractFileModules) {
                if (isModuleRunning(s)) {
                    try {
                        s.stop();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Exception while stopping module: " + s.getName(), e);
                    }
                }

            }
            //stop fs ingester thread
            boolean cancelled = abstractFileIngester.cancel(true);
            if (!cancelled) {
                logger.log(Level.WARNING, "Unable to cancel file ingest worker");
            } else {
                abstractFileIngester = null;
            }
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
            if(serv.hasBackgroundJobsRunning()) {
                return true;
            }
        }
        for (IngestImageThread thread : imageIngesters) {
            if(isModuleRunning(thread.getModule())) {
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
     * check if the module is running (was started and not yet
     * complete/stopped) give a complete answer, i.e. it's already consumed all
     * files but it might have background threads running
     *
     */
    public boolean isModuleRunning(final IngestModuleAbstract module) {

        if (module.getType() == IngestModuleAbstract.ModuleType.AbstractFile) {

            synchronized (queuesLock) {
                if (abstractFileQueue.hasModuleEnqueued((IngestModuleAbstractFile) module)) {
                    //has work enqueued, so running
                    return true;
                } else {
                    //not in the queue, but could still have bkg work running
                    return module.hasBackgroundJobsRunning();
                }
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
     * returns the current minimal update frequency setting in minutes Modules
     * should call this at init() to get current setting and use the setting to
     * change notification and data refresh intervals
     */
    UpdateFrequency getUpdateFrequency() {
        return updateFrequency;
    }

    /**
     * set new minimal update frequency modules should use
     *
     * @param frequency to use in minutes
     */
    void setUpdateFrequency(UpdateFrequency frequency) {
        this.updateFrequency = frequency;
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
     * helper to return all image modules managed (using Lookup API) sorted in
     * Lookup position order
     */
    public static List<IngestModuleImage> enumerateImageModules() {
        List<IngestModuleImage> ret = new ArrayList<IngestModuleImage>();
        for (IngestModuleImage list : Lookup.getDefault().lookupAll(IngestModuleImage.class)) {
            ret.add(list);
        }
        return ret;
    }

    /**
     * helper to return all file/dir modules managed (using Lookup API) sorted
     * in Lookup position order
     */
    public static List<IngestModuleAbstractFile> enumerateAbstractFileModules() {
        List<IngestModuleAbstractFile> ret = new ArrayList<IngestModuleAbstractFile>();
        for (IngestModuleAbstractFile list : Lookup.getDefault().lookupAll(IngestModuleAbstractFile.class)) {
            ret.add(list);
        }
        return ret;
    }

    /**
     * Queue up an image to be processed by a given module.
     *
     * @param module Module to analyze image
     * @param image Image to analyze
     */
    private void addImage(IngestModuleImage module, Image image) {
        synchronized (queuesLock) {
            imageQueue.enqueue(image, module);
        }
    }

    /**
     * Queue up an image to be processed by a given File module.
     *
     * @param module module for which to enqueue the files
     * @param abstractFiles files to enqueue
     */
    private void addAbstractFile(IngestModuleAbstractFile module, Collection<AbstractFile> abstractFiles) {
        synchronized (queuesLock) {
            for (AbstractFile abstractFile : abstractFiles) {
                abstractFileQueue.enqueue(abstractFile, module);
            }
        }
    }

    /**
     * get next file/dir and associated list of modules to process the queue of
     * AbstractFile to process is maintained internally and could be dynamically
     * sorted as data comes in
     */
    private Map.Entry<AbstractFile, List<IngestModuleAbstractFile>> getNextAbstractFile() {
        Map.Entry<AbstractFile, List<IngestModuleAbstractFile>> ret = null;
        synchronized (queuesLock) {
            ret = abstractFileQueue.dequeue();
        }
        return ret;
    }

    private boolean hasNextAbstractFile() {
        boolean ret = false;
        synchronized (queuesLock) {
            ret = abstractFileQueue.hasNext();
        }
        return ret;
    }

    private int getNumAbstractFiles() {
        int ret = 0;
        synchronized (queuesLock) {
            ret = abstractFileQueue.getCount();
        }
        return ret;
    }

    private void emptyAbstractFiles() {
        synchronized (queuesLock) {
            abstractFileQueue.empty();
        }
    }

    private void emptyImages() {
        synchronized (queuesLock) {
            imageQueue.empty();
        }
    }

    /**
     * get next Image/Module pair to process the queue of Images to process is
     * maintained internally and could be dynamically sorted as data comes in
     */
    private Map.Entry<Image, List<IngestModuleImage>> getNextImage() {
        Map.Entry<Image, List<IngestModuleImage>> ret = null;
        synchronized (queuesLock) {
            ret = imageQueue.dequeue();
        }
        return ret;
    }

    private boolean hasNextImage() {
        boolean ret = false;
        synchronized (queuesLock) {
            ret = imageQueue.hasNext();
        }
        return ret;
    }

    private int getNumImages() {
        int ret = 0;
        synchronized (queuesLock) {
            ret = imageQueue.getCount();
        }
        return ret;
    }

    //image worker to remove itself when complete or interrupted
    void removeImageIngestWorker(IngestImageThread worker) {
        //remove worker
        synchronized (this) {
            imageIngesters.remove(worker);
        }
    }

    /**
     * Priority determination for AbstractFile
     */
    private static class AbstractFilePriotity {

        enum Priority {

            LOW, MEDIUM, HIGH
        };
        static final List<Pattern> LOW_PRI_PATHS = new ArrayList<Pattern>();
        static final List<Pattern> MEDIUM_PRI_PATHS = new ArrayList<Pattern>();
        static final List<Pattern> HIGH_PRI_PATHS = new ArrayList<Pattern>();

        static {
            LOW_PRI_PATHS.add(Pattern.compile("^\\/Windows", Pattern.CASE_INSENSITIVE));

            MEDIUM_PRI_PATHS.add(Pattern.compile("^\\/Program Files", Pattern.CASE_INSENSITIVE));
            MEDIUM_PRI_PATHS.add(Pattern.compile("^pagefile", Pattern.CASE_INSENSITIVE));
            MEDIUM_PRI_PATHS.add(Pattern.compile("^hiberfil", Pattern.CASE_INSENSITIVE));

            HIGH_PRI_PATHS.add(Pattern.compile("^\\/Users", Pattern.CASE_INSENSITIVE));
            HIGH_PRI_PATHS.add(Pattern.compile("^\\/Documents and Settings", Pattern.CASE_INSENSITIVE));
            HIGH_PRI_PATHS.add(Pattern.compile("^\\/home", Pattern.CASE_INSENSITIVE));
            HIGH_PRI_PATHS.add(Pattern.compile("^\\/ProgramData", Pattern.CASE_INSENSITIVE));
            HIGH_PRI_PATHS.add(Pattern.compile("^\\/Windows\\/Temp", Pattern.CASE_INSENSITIVE));
        }

        static Priority getPriority(final AbstractFile abstractFile) {
            if (!abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.FS)) {
                //non-fs files, such as representing unalloc space
                return Priority.MEDIUM;
            }
            final String path = ((FsContent) abstractFile).getParentPath();

            if (path == null) {
                return Priority.MEDIUM;
            }

            for (Pattern p : HIGH_PRI_PATHS) {
                Matcher m = p.matcher(path);
                if (m.find()) {
                    return Priority.HIGH;
                }
            }

            for (Pattern p : MEDIUM_PRI_PATHS) {
                Matcher m = p.matcher(path);
                if (m.find()) {
                    return Priority.MEDIUM;
                }
            }

            for (Pattern p : LOW_PRI_PATHS) {
                Matcher m = p.matcher(path);
                if (m.find()) {
                    return Priority.LOW;
                }
            }

            //default is medium
            return Priority.MEDIUM;
        }
    }

    /**
     * manages queue of pending AbstractFile and list of associated
     * IngestModuleAbstractFile to use on that content sorted based on
     * AbstractFilePriotity
     */
    private class AbstractFileQueue {

        final Comparator<AbstractFile> sorter = new Comparator<AbstractFile>() {
            @Override
            public int compare(AbstractFile q1, AbstractFile q2) {
                AbstractFilePriotity.Priority p1 = AbstractFilePriotity.getPriority(q1);
                AbstractFilePriotity.Priority p2 = AbstractFilePriotity.getPriority(q2);
                if (p1 == p2) {
                    return (int) (q2.getId() - q1.getId());
                } else {
                    return p2.ordinal() - p1.ordinal();
                }

            }
        };
        final TreeMap<AbstractFile, List<IngestModuleAbstractFile>> AbstractFileUnits = new TreeMap<AbstractFile, List<IngestModuleAbstractFile>>(sorter);

        void enqueue(AbstractFile AbstractFile, IngestModuleAbstractFile module) {
            //AbstractFileUnits.put(AbstractFile, Collections.singletonList(module));
            List<IngestModuleAbstractFile> modules = AbstractFileUnits.get(AbstractFile);
            if (modules == null) {
                modules = new ArrayList<IngestModuleAbstractFile>();
                AbstractFileUnits.put(AbstractFile, modules);
            }
            modules.add(module);
        }

        void enqueue(AbstractFile AbstractFile, List<IngestModuleAbstractFile> modules) {

            List<IngestModuleAbstractFile> oldModules = AbstractFileUnits.get(AbstractFile);
            if (oldModules == null) {
                oldModules = new ArrayList<IngestModuleAbstractFile>();
                AbstractFileUnits.put(AbstractFile, oldModules);
            }
            oldModules.addAll(modules);
        }

        boolean hasNext() {
            return !AbstractFileUnits.isEmpty();
        }

        int getCount() {
            return AbstractFileUnits.size();
        }

        void empty() {
            AbstractFileUnits.clear();
        }

        /**
         * Returns next AbstractFile and list of associated modules
         *
         * @return
         */
        Map.Entry<AbstractFile, List<IngestModuleAbstractFile>> dequeue() {
            if (!hasNext()) {
                throw new UnsupportedOperationException("AbstractFile processing queue is empty");
            }

            //logger.log(Level.INFO, "DEQUE: " + remove.content.getParentPath() + " SIZE: " + toString());
            return (AbstractFileUnits.pollFirstEntry());
        }

        /**
         * checks if the module has any work enqueued
         *
         * @param module to check for
         * @return true if the module is enqueued to do work
         */
        boolean hasModuleEnqueued(IngestModuleAbstractFile module) {
            for (List<IngestModuleAbstractFile> list : AbstractFileUnits.values()) {
                if (list.contains(module)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public synchronized String toString() {
            return "AbstractFileQueue, size: " + Integer.toString(AbstractFileUnits.size());
        }

        public String printQueue() {
            StringBuilder sb = new StringBuilder();
            /*for (QueueUnit<AbstractFile, IngestModuleAbstractFile> u : AbstractFileUnits) {
             sb.append(u.toString());
             sb.append("\n");
             }*/
            return sb.toString();
        }
    }

    /**
     * manages queue of pending Images and IngestModuleImage to use on that
     * image. image / module pairs are added one at a time and internally, it
     * keeps track of all modules for a given image.
     */
    private class ImageQueue {

        final Comparator<Image> sorter = new Comparator<Image>() {
            @Override
            public int compare(Image q1, Image q2) {
                return (int) (q2.getId() - q1.getId());

            }
        };
        private TreeMap<Image, List<IngestModuleImage>> imageUnits = new TreeMap<Image, List<IngestModuleImage>>(sorter);

        void enqueue(Image image, IngestModuleImage module) {
            List<IngestModuleImage> modules = imageUnits.get(image);
            if (modules == null) {
                modules = new ArrayList<IngestModuleImage>();
                imageUnits.put(image, modules);
            }
            modules.add(module);
        }

        void enqueue(Image image, List<IngestModuleImage> modules) {
            List<IngestModuleImage> oldModules = imageUnits.get(image);
            if (oldModules == null) {
                oldModules = new ArrayList<IngestModuleImage>();
                imageUnits.put(image, oldModules);
            }
            oldModules.addAll(modules);
        }

        boolean hasNext() {
            return !imageUnits.isEmpty();
        }

        int getCount() {
            return imageUnits.size();
        }

        void empty() {
            imageUnits.clear();
        }

        /**
         * Return a QueueUnit that contains an image and set of modules to run
         * on it.
         *
         * @return
         */
        Map.Entry<Image, List<IngestModuleImage>> dequeue() {
            if (!hasNext()) {
                throw new UnsupportedOperationException("Image processing queue is empty");
            }

            return imageUnits.pollFirstEntry();
        }

        @Override
        public synchronized String toString() {
            return "ImageQueue, size: " + Integer.toString(imageUnits.size());
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
         * records stop time of the file process for the module must be
         * preceded by logFileModuleStartProcess for the same module
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
                    sb.append("\t").append(module.getName()).append(": ").append(errorsModule).append(EOL);
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

//ingester worker for AbstractFile queue
//worker runs until AbstractFile queue is consumed
//and if needed, new instance is created and started when data arrives
    private class IngestAbstractFileThread extends SwingWorker<Object, Void> {

        private Logger logger = Logger.getLogger(IngestAbstractFileThread.class.getName());
        private ProgressHandle progress;

        @Override
        protected Object doInBackground() throws Exception {

            logger.log(Level.INFO, "Starting background processing");
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
                    return IngestAbstractFileThread.this.cancel(true);
                }
            });

            progress.start();
            progress.switchToIndeterminate();
            int numAbstractFiles = getNumAbstractFiles();
            progress.switchToDeterminate(numAbstractFiles);
            int processedFiles = 0;
            //process AbstractFiles queue
            while (hasNextAbstractFile()) {
                Map.Entry<AbstractFile, List<IngestModuleAbstractFile>> unit = getNextAbstractFile();
                //clear return values from modules for last file
                synchronized (abstractFileModulesRetValues) {
                    abstractFileModulesRetValues.clear();
                }

                final AbstractFile fileToProcess = unit.getKey();

                progress.progress(fileToProcess.getName(), processedFiles);

                for (IngestModuleAbstractFile module : unit.getValue()) {
                    //process the file with every file module
                    if (isCancelled()) {
                        logger.log(Level.INFO, "Terminating file ingest due to cancellation.");
                        return null;
                    }


                    try {
                        stats.logFileModuleStartProcess(module);
                        IngestModuleAbstractFile.ProcessResult result = module.process(fileToProcess);
                        stats.logFileModuleEndProcess(module);

                        //store the result for subsequent modules for this file
                        synchronized (abstractFileModulesRetValues) {
                            abstractFileModulesRetValues.put(module.getName(), result);
                        }

                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Exception from module: " + module.getName(), e);
                        stats.addError(module);
                    }
                }
                int newAbstractFiles = getNumAbstractFiles();
                if (newAbstractFiles > numAbstractFiles) {
                    //update progress bar if new enqueued
                    numAbstractFiles = newAbstractFiles + processedFiles + 1;
                    progress.switchToIndeterminate();
                    progress.switchToDeterminate(numAbstractFiles);
                }
                ++processedFiles;
                --numAbstractFiles;
            } //end of this AbstractFile
            logger.log(Level.INFO, "Done background processing");
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

                    IngestManager.this.postMessage(IngestMessage.createManagerMessage("File Ingest Complete", stats.toHtmlString()));
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
            emptyAbstractFiles();
        }
    }

    /* Thread that adds image/file and module pairs to queues */
    private class EnqueueWorker extends SwingWorker<Object, Void> {

        List<IngestModuleAbstract> modules;
        final List<Image> images;
        int total;

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

            total = modules.size() * images.size();
            progress.start(total);
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
            int processed = 0;
            for (Image image : images) {
                final String imageName = image.getName();
                Collection<AbstractFile> files = null;
                for (IngestModuleAbstract module : modules) {
                    if (isCancelled()) {
                        logger.log(Level.INFO, "Terminating ingest queueing due to cancellation.");
                        return;
                    }
                    final String moduleName = module.getName();
                    progress.progress(moduleName + " " + imageName, processed);
                    switch (module.getType()) {
                        case Image:
                            //enqueue a new instance of image module
                            try {
                                final IngestModuleImage newModuleInstance = (IngestModuleImage) (module.getClass()).newInstance();
                                addImage(newModuleInstance, image);
                                logger.log(Level.INFO, "Added image " + image.getName() + " with module " + module.getName());
                            } catch (InstantiationException e) {
                                logger.log(Level.SEVERE, "Cannot instantiate module: " + module.getName(), e);
                            } catch (IllegalAccessException e) {
                                logger.log(Level.SEVERE, "Cannot instantiate module: " + module.getName(), e);
                            }

                            //addImage((IngestModuleImage) module, image);
                            break;
                        case AbstractFile:
                            if (files == null) {
                                long start = System.currentTimeMillis();
                                files = new GetAllFilesContentVisitor(processUnallocSpace).visit(image);
                                logger.info("Get all files took " + (System.currentTimeMillis() - start) + "ms");
                            }
                            //enqueue the same singleton AbstractFile module
                            logger.log(Level.INFO, "Adding image " + image.getName() + " with " + files.size() + " number of AbstractFile to module " + module.getName());
                            addAbstractFile((IngestModuleAbstractFile) module, files);
                            break;
                        default:
                            logger.log(Level.SEVERE, "Unexpected module type: " + module.getType().name());
                    }
                    progress.progress(moduleName + " " + imageName, ++processed);
                }
                if (files != null) {
                    files.clear();
                }
            }

            //logger.log(Level.INFO, AbstractFileQueue.printQueue());

            progress.progress("Sorting files", processed);
        }

        private void handleInterruption(Exception ex) {
            Logger.getLogger(EnqueueWorker.class.getName()).log(Level.INFO, "Exception!", ex);
            //empty queues
            emptyAbstractFiles();
            emptyImages();
        }
    }
}
