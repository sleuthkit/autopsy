/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskData;

/**
 * IngestManager sets up and manages ingest services
 * runs them in a background thread
 * notifies services when work is complete or should be interrupted
 * processes messages from services via messenger proxy  and posts them to GUI
 * 
 */
public class IngestManager {

    enum UpdateFrequency {

        FAST(20),
        AVG(10),
        SLOW(5);
        private final int time;

        UpdateFrequency(int time) {
            this.time = time;
        }

        int getTime() {
            return time;
        }
    };
    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private IngestManagerStats stats;
    private volatile UpdateFrequency updateFrequency = UpdateFrequency.AVG;
    private boolean processUnallocSpace = true;
    //queues
    private final ImageQueue imageQueue = new ImageQueue();   // list of services and images to analyze
    private final AbstractFileQueue AbstractFileQueue = new AbstractFileQueue();
    private final Object queuesLock = new Object();
    //workers
    private IngestAbstractFileThread AbstractFileIngester;
    private List<IngestImageThread> imageIngesters;
    private SwingWorker<Object,Void> queueWorker;
    //services
    final List<IngestServiceImage> imageServices = enumerateImageServices();
    final List<IngestServiceAbstractFile> AbstractFileServices = enumerateAbstractFileServices();
    // service return values
    private final Map<String, IngestServiceAbstractFile.ProcessResult> AbstractFileServiceResults = new HashMap<String, IngestServiceAbstractFile.ProcessResult>();
    //manager proxy
    final IngestManagerProxy managerProxy = new IngestManagerProxy(this);
    //notifications
    private final static PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);
    //monitor
    private final IngestMonitor ingestMonitor = new IngestMonitor();

    private enum IngestManagerEvents {

        SERVICE_STARTED, SERVICE_COMPLETED, SERVICE_STOPPED, SERVICE_HAS_DATA
    };
    public final static String SERVICE_STARTED_EVT = IngestManagerEvents.SERVICE_STARTED.name();
    public final static String SERVICE_COMPLETED_EVT = IngestManagerEvents.SERVICE_COMPLETED.name();
    public final static String SERVICE_STOPPED_EVT = IngestManagerEvents.SERVICE_STOPPED.name();
    public final static String SERVICE_HAS_DATA_EVT = IngestManagerEvents.SERVICE_HAS_DATA.name();
    //ui
    private IngestUI ui = null;
    //singleton
    private static IngestManager instance;

    private IngestManager() {
        imageIngesters = new ArrayList<IngestImageThread>();
    }

    public static synchronized IngestManager getDefault() {
        if (instance == null) {
            logger.log(Level.INFO, "creating manager instance");
            instance = new IngestManager();
        }
        return instance;
    }

    void initUI() {
        ui = IngestMessageTopComponent.findInstance();
    }

    /**
     * Add property change listener to listen to ingest events
     * @param l PropertyChangeListener to add
     */
    public static synchronized void addPropertyChangeListener(final PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public static synchronized void fireServiceEvent(String eventType, String serviceName) {
        pcs.firePropertyChange(eventType, serviceName, null);
    }

    public static synchronized void fireServiceDataEvent(ServiceDataEvent serviceDataEvent) {
        pcs.firePropertyChange(SERVICE_HAS_DATA_EVT, serviceDataEvent, null);
    }

    IngestServiceAbstractFile.ProcessResult getAbstractFileServiceResult(String serviceName) {
        synchronized (AbstractFileServiceResults) {
            if (AbstractFileServiceResults.containsKey(serviceName)) {
                return AbstractFileServiceResults.get(serviceName);
            } else {
                return IngestServiceAbstractFile.ProcessResult.UNKNOWN;
            }
        }
    }

    /**
     * Multiple image version of execute, enqueues multiple images and associated services at once
     * @param services services to execute on every image
     * @param images images to execute services on
     */
    void execute(final List<IngestServiceAbstract> services, final List<Image> images) {
        logger.log(Level.INFO, "Will enqueue number of images: " + images.size() + " to " + services.size() + " services.");

        if (!isIngestRunning()) {
            ui.clearMessages();
        }

        queueWorker = new EnqueueWorker(services, images);
        queueWorker.execute();

        ui.restoreMessages();
        //logger.log(Level.INFO, "Queues: " + imageQueue.toString() + " " + AbstractFileQueue.toString());
    }

    /**
     * IngestManager entry point, enqueues image to be processed.
     * Spawns background thread which enumerates all sorted files and executes chosen services per file in a pre-determined order.
     * Notifies services when work is complete or should be interrupted using complete() and stop() calls.
     * Does not block and can be called multiple times to enqueue more work to already running background process.
     * @param services services to execute on the image
     * @param image image to execute services on
     */
    void execute(final List<IngestServiceAbstract> services, final Image image) {
        List<Image> images = new ArrayList<Image>();
        images.add(image);
        logger.log(Level.INFO, "Will enqueue image: " + image.getName());
        execute(services, images);
    }

    /**
     * Starts the needed worker threads.
     * 
     * if AbstractFile service is still running, do nothing and allow it to consume queue
     * otherwise start /restart AbstractFile worker
     * 
     * image workers run per (service,image).  Check if one for the (service,image) is already running
     * otherwise start/restart the worker
     */
    private synchronized void startAll() {
        logger.log(Level.INFO, "Image queue: " + this.imageQueue.toString());
        logger.log(Level.INFO, "File queue: " + this.AbstractFileQueue.toString());

        if (!ingestMonitor.isRunning()) {
            ingestMonitor.start();
        }

        //image ingesters
        // cycle through each image in the queue
        while (hasNextImage()) {
            //dequeue
            // get next image and set of services
            final Map.Entry<Image, List<IngestServiceImage>> qu =
                    this.getNextImage();


            // check if each service for this image is already running
            //synchronized (this) {
            for (IngestServiceImage quService : qu.getValue()) {
                boolean alreadyRunning = false;
                for (IngestImageThread worker : imageIngesters) {
                    // ignore threads that are on different images
                    if (!worker.getImage().equals(qu.getKey())) {
                        continue; //check next worker
                    }
                    //same image, check service (by name, not id, since different instances)
                    if (worker.getService().getName().equals(quService.getName())) {
                        alreadyRunning = true;
                        logger.log(Level.INFO, "Image Ingester <" + qu.getKey() + ", " + quService.getName() + "> is already running");
                        break;
                    }
                }
                //checked all workers
                if (alreadyRunning == false) {
                    logger.log(Level.INFO, "Starting new image Ingester <" + qu.getKey() + ", " + quService.getName() + ">");
                    IngestImageThread newImageWorker = new IngestImageThread(this, qu.getKey(), quService);

                    imageIngesters.add(newImageWorker);

                    //image services are now initialized per instance
                    quService.init(managerProxy);
                    newImageWorker.execute();
                    IngestManager.fireServiceEvent(SERVICE_STARTED_EVT, quService.getName());
                }
            }
        }
        //}


        //AbstractFile ingester
        boolean startAbstractFileIngester = false;
        if (hasNextAbstractFile()) {
            if (AbstractFileIngester
                    == null) {
                startAbstractFileIngester = true;
                logger.log(Level.INFO, "Starting initial AbstractFile ingester");
            } //if worker had completed, restart it in case data is still enqueued
            else if (AbstractFileIngester.isDone()) {
                startAbstractFileIngester = true;
                logger.log(Level.INFO, "Restarting AbstractFile ingester");
            }
        } else {
            logger.log(Level.INFO, "no new AbstractFile enqueued, no ingester needed");
        }

        if (startAbstractFileIngester) {
            stats = new IngestManagerStats();
            AbstractFileIngester = new IngestAbstractFileThread();
            //init all fs services, everytime new worker starts
            for (IngestServiceAbstractFile s : AbstractFileServices) {
                s.init(managerProxy);
            }
            AbstractFileIngester.execute();
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

        //stop service workers
        if (AbstractFileIngester != null) {
            //send signals to all file services
            for (IngestServiceAbstractFile s : this.AbstractFileServices) {
                if (isServiceRunning(s)) {
                    try {
                        s.stop();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Exception while stopping service: " + s.getName(), e);
                    }
                }

            }
            //stop fs ingester thread
            boolean cancelled = AbstractFileIngester.cancel(true);
            if (!cancelled) {
                logger.log(Level.WARNING, "Unable to cancel file ingest worker");
            } else {
                AbstractFileIngester = null;
            }
        }

        List<IngestImageThread> toStop = new ArrayList<IngestImageThread>();
        toStop.addAll(imageIngesters);


        for (IngestImageThread imageWorker : toStop) {
            IngestServiceImage s = imageWorker.getService();
            
            //stop the worker thread if thread is running
            boolean cancelled = imageWorker.cancel(true);
            if (!cancelled) {
                logger.log(Level.INFO, "Unable to cancel image ingest worker for service: " + imageWorker.getService().getName() + " img: " + imageWorker.getImage().getName());
            }
            
            //stop notification to service to cleanup resources
            if (isServiceRunning(s)) {
                try {
                    imageWorker.getService().stop();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Exception while stopping service: " + s.getName(), e);
                }
            }
           
        }

        logger.log(Level.INFO, "stopped all");
    }

    /**
     * test if any of image of AbstractFile ingesters are running
     * @return true if any service is running, false otherwise
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

    public synchronized boolean isEnqueueRunning() {
        if (queueWorker != null && !queueWorker.isDone()) {
            return true;
        }
        return false;
    }

    public synchronized boolean isFileIngestRunning() {
        if (AbstractFileIngester != null && !AbstractFileIngester.isDone()) {
            return true;
        }
        return false;
    }

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
     * check if the service is running (was started and not yet complete/stopped)
     * give a complete answer, i.e. it's already consumed all files
     * but it might have background threads running
     * 
     */
    public boolean isServiceRunning(final IngestServiceAbstract service) {

        if (service.getType() == IngestServiceAbstract.ServiceType.AbstractFile) {

            synchronized (queuesLock) {
                if (AbstractFileQueue.hasServiceEnqueued((IngestServiceAbstractFile) service)) {
                    //has work enqueued, so running
                    return true;
                } else {
                    //not in the queue, but could still have bkg work running
                    return service.hasBackgroundJobsRunning();
                }
            }

        } else {
            //image service
            synchronized (this) {
                if (imageIngesters.isEmpty()) {
                    return false;
                }
                IngestImageThread imt = null;
                for (IngestImageThread ii : imageIngesters) {
                    if (ii.getService().equals(service)) {
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
     * returns the current minimal update frequency setting in minutes
     * Services should call this at init() to get current setting
     * and use the setting to change notification and data refresh intervals
     */
    UpdateFrequency getUpdateFrequency() {
        return updateFrequency;
    }

    /**
     * set new minimal update frequency services should use
     * @param frequency to use in minutes
     */
    void setUpdateFrequency(UpdateFrequency frequency) {
        this.updateFrequency = frequency;
    }
    
    /**
     * returns if manager is currently configured to process unalloc space
     * @return true if process unaloc space is set
     */
    boolean getProcessUnallocSpace() {
        return processUnallocSpace;
    }
    
    /**
     * Sets process unalloc space setting on the manager
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
     * Service publishes message using InegestManager handle
     * Does not block.
     * The message gets enqueued in the GUI thread and displayed in a widget
     * IngestService should make an attempt not to publish the same message multiple times.
     * Viewer will attempt to identify duplicate messages and filter them out (slower)
     */
    void postMessage(final IngestMessage message) {

        if (stats != null) {
            //record the error for stats, if stats are running
            if (message.getMessageType() == MessageType.ERROR) {
                stats.addError(message.getSource());
            }
        }
        ui.displayMessage(message);
    }

    /**
     * helper to return all image services managed (using Lookup API) sorted in Lookup position order
     */
    public static List<IngestServiceImage> enumerateImageServices() {
        List<IngestServiceImage> ret = new ArrayList<IngestServiceImage>();
        for (IngestServiceImage list : Lookup.getDefault().lookupAll(IngestServiceImage.class)) {
            ret.add(list);
        }
        return ret;
    }

    /**
     * helper to return all file/dir services managed (using Lookup API) sorted in Lookup position order
     */
    public static List<IngestServiceAbstractFile> enumerateAbstractFileServices() {
        List<IngestServiceAbstractFile> ret = new ArrayList<IngestServiceAbstractFile>();
        for (IngestServiceAbstractFile list : Lookup.getDefault().lookupAll(IngestServiceAbstractFile.class)) {
            ret.add(list);
        }
        return ret;
    }

    /**
     * Queue up an image to be processed by a given service. 
     * @param service Service to analyze image
     * @param image Image to analyze
     */
    private void addImage(IngestServiceImage service, Image image) {
        synchronized (queuesLock) {
            imageQueue.enqueue(image, service);
        }
    }

    /**
     * Queue up an image to be processed by a given File service.
     * @param service
     * @param image 
     */
    private void addAbstractFile(IngestServiceAbstractFile service, Collection<AbstractFile> AbstractFiles) {
        synchronized (queuesLock) {
            for (AbstractFile AbstractFile : AbstractFiles) {
                AbstractFileQueue.enqueue(AbstractFile, service);
            }
        }
    }

    /**
     * get next file/dir and associated list of services to process
     * the queue of AbstractFile to process is maintained internally 
     * and could be dynamically sorted as data comes in
     */
    private Map.Entry<AbstractFile, List<IngestServiceAbstractFile>> getNextAbstractFile() {
        Map.Entry<AbstractFile, List<IngestServiceAbstractFile>> ret = null;
        synchronized (queuesLock) {
            ret = AbstractFileQueue.dequeue();
        }
        return ret;
    }

    private boolean hasNextAbstractFile() {
        boolean ret = false;
        synchronized (queuesLock) {
            ret = AbstractFileQueue.hasNext();
        }
        return ret;
    }

    private int getNumAbstractFiles() {
        int ret = 0;
        synchronized (queuesLock) {
            ret = AbstractFileQueue.getCount();
        }
        return ret;
    }

    private void emptyAbstractFiles() {
        synchronized (queuesLock) {
            AbstractFileQueue.empty();
        }
    }

    private void emptyImages() {
        synchronized (queuesLock) {
            imageQueue.empty();
        }
    }

    /**
     * get next Image/Service pair to process
     * the queue of Images to process is maintained internally 
     * and could be dynamically sorted as data comes in
     */
    private Map.Entry<Image, List<IngestServiceImage>> getNextImage() {
        Map.Entry<Image, List<IngestServiceImage>> ret = null;
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
        static final List<Pattern> lowPriorityPaths = new ArrayList<Pattern>();
        static final List<Pattern> mediumPriorityPaths = new ArrayList<Pattern>();
        static final List<Pattern> highPriorityPaths = new ArrayList<Pattern>();

        static {
            lowPriorityPaths.add(Pattern.compile("^\\/Windows", Pattern.CASE_INSENSITIVE));

            mediumPriorityPaths.add(Pattern.compile("^\\/Program Files", Pattern.CASE_INSENSITIVE));
            mediumPriorityPaths.add(Pattern.compile("^pagefile", Pattern.CASE_INSENSITIVE));
            mediumPriorityPaths.add(Pattern.compile("^hiberfil", Pattern.CASE_INSENSITIVE));

            highPriorityPaths.add(Pattern.compile("^\\/Users", Pattern.CASE_INSENSITIVE));
            highPriorityPaths.add(Pattern.compile("^\\/Documents and Settings", Pattern.CASE_INSENSITIVE));
            highPriorityPaths.add(Pattern.compile("^\\/home", Pattern.CASE_INSENSITIVE));
            highPriorityPaths.add(Pattern.compile("^\\/ProgramData", Pattern.CASE_INSENSITIVE));
            highPriorityPaths.add(Pattern.compile("^\\/Windows\\/Temp", Pattern.CASE_INSENSITIVE));
        }

        static Priority getPriority(final AbstractFile abstractFile) {
            if(!abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.FS)) {
                return Priority.MEDIUM;
            }
            final String path = ((FsContent)abstractFile).getParentPath();
            
            if(path == null){
                return Priority.MEDIUM;
            }

            for (Pattern p : highPriorityPaths) {
                Matcher m = p.matcher(path);
                if (m.find()) {
                    return Priority.HIGH;
                }
            }

            for (Pattern p : mediumPriorityPaths) {
                Matcher m = p.matcher(path);
                if (m.find()) {
                    return Priority.MEDIUM;
                }
            }

            for (Pattern p : lowPriorityPaths) {
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
     * manages queue of pending AbstractFile and list of associated IngestServiceAbstractFile to use on that content
     * sorted based on AbstractFilePriotity
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
        final TreeMap<AbstractFile, List<IngestServiceAbstractFile>> AbstractFileUnits = new TreeMap<AbstractFile, List<IngestServiceAbstractFile>>(sorter);

        void enqueue(AbstractFile AbstractFile, IngestServiceAbstractFile service) {
            //AbstractFileUnits.put(AbstractFile, Collections.singletonList(service));
            List<IngestServiceAbstractFile> services = AbstractFileUnits.get(AbstractFile);
            if (services == null) {
                services = new ArrayList<IngestServiceAbstractFile>();
                AbstractFileUnits.put(AbstractFile, services);
            }
            services.add(service);
        }

        void enqueue(AbstractFile AbstractFile, List<IngestServiceAbstractFile> services) {

            List<IngestServiceAbstractFile> oldServices = AbstractFileUnits.get(AbstractFile);
            if (oldServices == null) {
                oldServices = new ArrayList<IngestServiceAbstractFile>();
                AbstractFileUnits.put(AbstractFile, oldServices);
            }
            oldServices.addAll(services);
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
         * Returns next AbstractFile and list of associated services
         * @return 
         */
        Map.Entry<AbstractFile, List<IngestServiceAbstractFile>> dequeue() {
            if (!hasNext()) {
                throw new UnsupportedOperationException("AbstractFile processing queue is empty");
            }

            //logger.log(Level.INFO, "DEQUE: " + remove.content.getParentPath() + " SIZE: " + toString());
            return (AbstractFileUnits.pollFirstEntry());
        }

        /**
         * checks if the service has any work enqueued
         * @param service to check for 
         * @return true if the service is enqueued to do work
         */
        boolean hasServiceEnqueued(IngestServiceAbstractFile service) {
            for (List<IngestServiceAbstractFile> list : AbstractFileUnits.values()) {
                if (list.contains(service)) {
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
            /*for (QueueUnit<AbstractFile, IngestServiceAbstractFile> u : AbstractFileUnits) {
            sb.append(u.toString());
            sb.append("\n");
            }*/
            return sb.toString();
        }
    }

    /**
     * manages queue of pending Images and IngestServiceImage to use on that image.
     * image / service pairs are added one at a time and internally, it keeps track of all
     * services for a given image.
     */
    private class ImageQueue {

        final Comparator<Image> sorter = new Comparator<Image>() {

            @Override
            public int compare(Image q1, Image q2) {
                return (int) (q2.getId() - q1.getId());

            }
        };
        private TreeMap<Image, List<IngestServiceImage>> imageUnits = new TreeMap<Image, List<IngestServiceImage>>(sorter);

        void enqueue(Image image, IngestServiceImage service) {
            List<IngestServiceImage> services = imageUnits.get(image);
            if (services == null) {
                services = new ArrayList<IngestServiceImage>();
                imageUnits.put(image, services);
            }
            services.add(service);
        }

        void enqueue(Image image, List<IngestServiceImage> services) {
            List<IngestServiceImage> oldServices = imageUnits.get(image);
            if (oldServices == null) {
                oldServices = new ArrayList<IngestServiceImage>();
                imageUnits.put(image, oldServices);
            }
            oldServices.addAll(services);
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
         * Return a QueueUnit that contains an image and set of services to run on it.
         * @return 
         */
        Map.Entry<Image, List<IngestServiceImage>> dequeue() {
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
    private static class IngestManagerStats {

        Date startTime;
        Date endTime;
        int errorsTotal;
        Map<IngestServiceAbstract, Integer> errors;
        private static DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        IngestManagerStats() {
            errors = new HashMap<IngestServiceAbstract, Integer>();
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
                sb.append("Errors per service:");
                for (IngestServiceAbstract service : errors.keySet()) {
                    final int errorsService = errors.get(service);
                    sb.append("\t").append(service.getName()).append(": ").append(errorsService).append(EOL);
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
            sb.append("Errors per service:");
            for (IngestServiceAbstract service : errors.keySet()) {
            final int errorsService = errors.get(service);
            sb.append("\t").append(service.getName()).append(": ").append(errorsService).append("<br />");
            }
            }
             * */

            sb.append("</html>");
            return sb.toString();
        }

        void start() {
            startTime = new Date();
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

        synchronized void addError(IngestServiceAbstract source) {
            ++errorsTotal;
            Integer curServiceErrorI = errors.get(source);
            if (curServiceErrorI == null) {
                errors.put(source, 1);
            } else {
                errors.put(source, curServiceErrorI + 1);
            }
        }
    }

//ingester worker for AbstractFile queue
//worker runs until AbstractFile queue is consumed
//and if needed, new instance is created and started when data arrives
    private class IngestAbstractFileThread extends SwingWorker<Object,Void> {

        private Logger logger = Logger.getLogger(IngestAbstractFileThread.class.getName());
        private ProgressHandle progress;

        @Override
        protected Object doInBackground() throws Exception {

            logger.log(Level.INFO, "Starting background processing");
            stats.start();

            //notify main thread services started
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    for (IngestServiceAbstractFile s : AbstractFileServices) {
                        IngestManager.fireServiceEvent(SERVICE_STARTED_EVT, s.getName());
                    }
                }
            });

            final String displayName = "File Ingest";
            progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {

                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Filed ingest cancelled by user.");
                    if (progress != null)
                        progress.setDisplayName(displayName + " (Cancelling...)");
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
                Map.Entry<AbstractFile, List<IngestServiceAbstractFile>> unit = getNextAbstractFile();
                //clear return values from services for last file
                synchronized (AbstractFileServiceResults) {
                    AbstractFileServiceResults.clear();
                }

                final AbstractFile fileToProcess = unit.getKey();

                progress.progress(fileToProcess.getName(), processedFiles);

                for (IngestServiceAbstractFile service : unit.getValue()) {
                    if (isCancelled()) {
                        logger.log(Level.INFO, "Terminating file ingest due to cancellation.");
                        return null;
                    }


                    try {
                        IngestServiceAbstractFile.ProcessResult result = service.process(fileToProcess);
                        //handle unconditional stop
                        if (result == IngestServiceAbstractFile.ProcessResult.STOP) {
                            break;
                            //will skip other services and start to process next file
                        }

                        //store the result for subsequent services for this file
                        synchronized (AbstractFileServiceResults) {
                            AbstractFileServiceResults.put(service.getName(), result);
                        }

                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Exception from service: " + service.getName(), e);
                        stats.addError(service);
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
                //notify services of completion
                if (!this.isCancelled()) {
                    for (IngestServiceAbstractFile s : AbstractFileServices) {
                        s.complete();
                        IngestManager.fireServiceEvent(SERVICE_COMPLETED_EVT, s.getName());
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
                    //ui.displayReport(stats.toHtmlString());
                    IngestManager.this.postMessage(IngestMessage.createManagerMessage("File Ingest Complete", stats.toHtmlString()));
                }
            }

        }

        private void handleInterruption() {
            for (IngestServiceAbstractFile s : AbstractFileServices) {
                if (isServiceRunning(s)) {
                    try {
                        s.stop();
                    }
                    catch (Exception e) {
                        logger.log(Level.WARNING, "Exception while stopping service: " + s.getName(), e);
                    }
                }
                IngestManager.fireServiceEvent(SERVICE_STOPPED_EVT, s.getName());
            }
            //empty queues
            emptyAbstractFiles();
        }
    }

    /* Thread that adds image/file and service pairs to queues */
    private class EnqueueWorker extends SwingWorker<Object,Void> {

        List<IngestServiceAbstract> services;
        final List<Image> images;
        int total;

        EnqueueWorker(final List<IngestServiceAbstract> services, final List<Image> images) {
            this.services = services;
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
                    if (progress != null)
                        progress.setDisplayName(displayName + " (Cancelling...)");
                    return EnqueueWorker.this.cancel(true);
                }
            });

            total = services.size() * images.size();
            progress.start(total);
            //progress.switchToIndeterminate();
            queueAll(services, images);
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

        private void queueAll(List<IngestServiceAbstract> services, final List<Image> images) {
            int processed = 0;
            for (Image image : images) {
                final String imageName = image.getName();
                Collection<AbstractFile> files = null;
                for (IngestServiceAbstract service : services) {
                    if (isCancelled()) {
                        logger.log(Level.INFO, "Terminating ingest queueing due to cancellation.");
                        return;
                    }
                    final String serviceName = service.getName();
                    progress.progress(serviceName + " " + imageName, processed);
                    switch (service.getType()) {
                        case Image:
                            //enqueue a new instance of image service
                            try {
                                final IngestServiceImage newServiceInstance = (IngestServiceImage) (service.getClass()).newInstance();
                                addImage(newServiceInstance, image);
                                logger.log(Level.INFO, "Added image " + image.getName() + " with service " + service.getName());
                            } catch (InstantiationException e) {
                                logger.log(Level.SEVERE, "Cannot instantiate service: " + service.getName(), e);
                            } catch (IllegalAccessException e) {
                                logger.log(Level.SEVERE, "Cannot instantiate service: " + service.getName(), e);
                            }

                            //addImage((IngestServiceImage) service, image);
                            break;
                        case AbstractFile:
                            if (files == null) {
                                long start = System.currentTimeMillis();
                                files = new GetAllFilesContentVisitor(processUnallocSpace).visit(image);
                                logger.info("Get all files took " + (System.currentTimeMillis() - start) + "ms");
                            }
                            //enqueue the same singleton AbstractFile service
                            logger.log(Level.INFO, "Adding image " + image.getName() + " with " + files.size() + " number of AbstractFile to service " + service.getName());
                            addAbstractFile((IngestServiceAbstractFile) service, files);
                            break;
                        default:
                            logger.log(Level.SEVERE, "Unexpected service type: " + service.getType().name());
                    }
                    progress.progress(serviceName + " " + imageName, ++processed);
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
