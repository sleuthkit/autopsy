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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;

/**
 * IngestManager sets up and manages ingest services
 * runs them in a background thread
 * notifies services when work is complete or should be interrupted
 * processes messages from services via messenger proxy  and posts them to GUI
 * 
 */
public class IngestManager {

    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private IngestTopComponent tc;
    private IngestManagerStats stats;
    private volatile int updateFrequency = 60; //in seconds
    //queues
    private final ImageQueue imageQueue = new ImageQueue();   // list of services and images to analyze
    private final FsContentQueue fsContentQueue = new FsContentQueue();
    private final Object queuesLock = new Object();
    //workers
    private IngestFsContentThread fsContentIngester;
    private List<IngestImageThread> imageIngesters;
    private SwingWorker queueWorker;
    //services
    final Collection<IngestServiceImage> imageServices = enumerateImageServices();
    final Collection<IngestServiceFsContent> fsContentServices = enumerateFsContentServices();
    //manager proxy
    final IngestManagerProxy managerProxy = new IngestManagerProxy(this);
    //notifications
    private final static PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);

    private enum IngestManagerEvents {

        SERVICE_STARTED, SERVICE_COMPLETED, SERVICE_STOPPED, SERVICE_HAS_DATA
    };
    public final static String SERVICE_STARTED_EVT = IngestManagerEvents.SERVICE_STARTED.name();
    public final static String SERVICE_COMPLETED_EVT = IngestManagerEvents.SERVICE_COMPLETED.name();
    public final static String SERVICE_STOPPED_EVT = IngestManagerEvents.SERVICE_STOPPED.name();
    public final static String SERVICE_HAS_DATA_EVT = IngestManagerEvents.SERVICE_HAS_DATA.name();
    //initialization
    //private boolean initialized = false;

    /**
     * 
     * @param tc handle to Ingest top component
     */
    IngestManager(IngestTopComponent tc) {
        this.tc = tc;
        imageIngesters = new ArrayList<IngestImageThread>();
    }

    /**
     * Add property change listener to listen to ingest events
     * @param l PropertyChangeListener to add
     */
    public static synchronized void addPropertyChangeListener(final PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public static synchronized void firePropertyChange(String property, String serviceName) {
        pcs.firePropertyChange(property, serviceName, null);
    }

    /**
     * Multiple image version of execute, enqueues multiple images and associated services at once
     * @param services services to execute on every image
     * @param images images to execute services on
     */
    void execute(final Collection<IngestServiceAbstract> services, final Collection<Image> images) {
        logger.log(Level.INFO, "Will enqueue number of images: " + images.size() );
        /*if (!initialized) {
            //one time initialization of services

            //image services are now initialized per instance
            //for (IngestServiceImage s : imageServices) {
            //    s.init(this);
            //}

            for (IngestServiceFsContent s : fsContentServices) {
                s.init(this);
            }
            initialized = true;
        }*/

        tc.enableStartButton(false);
        logger.log(Level.INFO, "Starting enqueue worker");
        queueWorker = new EnqueueWorker(services, images);
        queueWorker.execute();

        //logger.log(Level.INFO, "Queues: " + imageQueue.toString() + " " + fsContentQueue.toString());
    }

    /**
     * IngestManager entry point, enqueues image to be processed.
     * Spawns background thread which enumerates all sorted files and executes chosen services per file in a pre-determined order.
     * Notifies services when work is complete or should be interrupted using complete() and stop() calls.
     * Does not block and can be called multiple times to enqueue more work to already running background process.
     * @param services services to execute on the image
     * @param image image to execute services on
     */
    void execute(final Collection<IngestServiceAbstract> services, final Image image) {
        Collection<Image> images = new ArrayList<Image>();
        images.add(image);
        logger.log(Level.INFO, "Will enqueue image: " + image.getName() );
        execute(services, images);
    }

    /**
     * Starts the needed worker threads.
     * 
     * if fsContent service is still running, do nothing and allow it to consume queue
     * otherwise start /restart fsContent worker
     * 
     * image workers run per (service,image).  Check if one for the (service,image) is already running
     * otherwise start/restart the worker
     */
    private synchronized void startAll() {
        logger.log(Level.INFO, "Image queue: " + this.imageQueue.toString());
        logger.log(Level.INFO, "File queue: " + this.fsContentQueue.toString());
        
        //image ingesters
        // cycle through each image in the queue
        while (hasNextImage()) {
            //dequeue
            // get next image and set of services
            final QueueUnit<Image, IngestServiceImage> qu =
                    this.getNextImage();
            
            
            // check if each service for this image is already running
            //synchronized (this) {
                for (IngestServiceImage quService : qu.services) {
                    boolean alreadyRunning = false;
                    for (IngestImageThread worker : imageIngesters) {
                        // ignore threads that are on different images
                        if (!worker.getImage().equals(qu.content)) {
                            continue; //check next worker
                        }
                        //same image, check service (by name, not id, since different instances)
                        if (worker.getService().getName().equals(quService.getName())) {
                            alreadyRunning = true;
                            logger.log(Level.INFO, "Image Ingester <" + qu.content + ", " + quService.getName() + "> is already running");
                            break;
                        }
                    }
                    //checked all workers
                    if (alreadyRunning == false) {
                        logger.log(Level.INFO, "Starting new image Ingester <" + qu.content + ", " + quService.getName() + ">");
                        IngestImageThread newImageWorker = new IngestImageThread(this, qu.content, quService);

                        imageIngesters.add(newImageWorker);

                        //image services are now initialized per instance
                        quService.init(managerProxy);
                        newImageWorker.execute();
                        IngestManager.firePropertyChange(SERVICE_STARTED_EVT, quService.getName());
                    }
                }
            }
        //}


        //fsContent ingester
        boolean startFsContentIngester = false;
        if (hasNextFsContent()) {
            if (fsContentIngester
                    == null) {
                startFsContentIngester = true;
                logger.log(Level.INFO, "Starting initial FsContent ingester");
            } //if worker had completed, restart it in case data is still enqueued
            else if (fsContentIngester.isDone()) {
                startFsContentIngester = true;
                logger.log(Level.INFO, "Restarting fsContent ingester");
            }
        } else {
            logger.log(Level.INFO, "no new FsContent enqueued, no ingester needed");
        }

        if (startFsContentIngester) {
            stats = new IngestManagerStats();
            fsContentIngester = new IngestFsContentThread();
            //init all fs services, everytime new worker starts
            for (IngestServiceFsContent s : fsContentServices) {
                s.init(managerProxy);
            }
            fsContentIngester.execute();
        }
    }

    /**
     * stop currently running threads if any (e.g. when changing a case)
     */
    void stopAll() {
        //stop queue worker
        if (queueWorker != null) {
            queueWorker.cancel(true);
            queueWorker = null;
        }

        //empty queues
        emptyFsContents();
        emptyImages();

        //stop service workers
        if (fsContentIngester != null) {
            boolean cancelled = fsContentIngester.cancel(true);
            if (!cancelled) {
                logger.log(Level.WARNING, "Unable to cancel file ingest worker");
            } else {
                fsContentIngester = null;
            }
        }

        List<IngestImageThread> toStop = new ArrayList<IngestImageThread>();
        synchronized (this) {
            toStop.addAll(imageIngesters);
        }

        for (IngestImageThread imageWorker : toStop) {
            boolean cancelled = imageWorker.cancel(true);
            if (!cancelled) {
                logger.log(Level.WARNING, "Unable to cancel image ingest worker for service: " + imageWorker.getService().getName() + " img: " + imageWorker.getImage().getName());
            }
        }

        logger.log(Level.INFO, "stopped all"); 
    }

    /**
     * returns the current minimal update frequency setting in seconds
     * Services should call this at init() to get current setting
     * and use the setting to change notification and data refresh intervals
     */
    int getUpdateFrequency() {
        return updateFrequency;
    }

    /**
     * set new minimal update frequency services should use
     * @param frequency 
     */
    void setUpdateFrequency(int frequency) {
        this.updateFrequency = frequency;
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
     synchronized void postMessage(final IngestMessage message) {

        if (stats != null) {
            //record the error for stats, if stats are running
            if (message.getMessageType() == MessageType.ERROR) {
                stats.addError(message.getSource());
            }
        }

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                tc.displayMessage(message);
            }
        });
    }

    /**
     * helper to return all image services managed (using Lookup API)
     */
    public static Collection<IngestServiceImage> enumerateImageServices() {
        return (Collection<IngestServiceImage>) Lookup.getDefault().lookupAll(IngestServiceImage.class);
    }

    /**
     * helper to return all file/dir services managed (using Lookup API)
     */
    public static Collection<IngestServiceFsContent> enumerateFsContentServices() {
        return (Collection<IngestServiceFsContent>) Lookup.getDefault().lookupAll(IngestServiceFsContent.class);
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
    private void addFsContent(IngestServiceFsContent service, Image image) {
        Collection<FsContent> fsContents = new GetAllFilesContentVisitor().visit(image);
        logger.log(Level.INFO, "Adding image " + image.getName() + " with " + fsContents.size() + " number of fsContent to service " + service.getName());
        synchronized (queuesLock) {
            for (FsContent fsContent : fsContents) {
                fsContentQueue.enqueue(fsContent, service);
            }
        }
    }

    /**
     * get next file/dir and associated list of services to process
     * the queue of FsContent to process is maintained internally 
     * and could be dynamically sorted as data comes in
     */
    private QueueUnit<FsContent, IngestServiceFsContent> getNextFsContent() {
        QueueUnit<FsContent, IngestServiceFsContent> ret = null;
        synchronized (queuesLock) {
            ret = fsContentQueue.dequeue();
        }
        return ret;
    }

    private boolean hasNextFsContent() {
        boolean ret = false;
        synchronized (queuesLock) {
            ret = fsContentQueue.hasNext();
        }
        return ret;
    }

    private int getNumFsContents() {
        int ret = 0;
        synchronized (queuesLock) {
            ret = fsContentQueue.getCount();
        }
        return ret;
    }

    private void emptyFsContents() {
        synchronized (queuesLock) {
            fsContentQueue.empty();
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
    private QueueUnit<Image, IngestServiceImage> getNextImage() {
        QueueUnit<Image, IngestServiceImage> ret = null;
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

    private void initMainProgress(final int maximum) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                tc.initProgress(maximum);
            }
        });
    }

    private void updateMainProgress(final int progress) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                tc.updateProgress(progress);
            }
        });
    }

    //image worker to remove itself when complete or interrupted
    void removeImageIngestWorker(IngestImageThread worker) {
        //remove worker
        synchronized (this) {
            imageIngesters.remove(worker);
        }
    }

    //manages queue of pending FsContent and list of associated IngestServiceFsContent to use on that content
    //TODO in future content sort will be maintained based on priorities
    private class FsContentQueue {

        List<QueueUnit<FsContent, IngestServiceFsContent>> fsContentUnits = new ArrayList<QueueUnit<FsContent, IngestServiceFsContent>>();

        void enqueue(FsContent fsContent, IngestServiceFsContent service) {
            QueueUnit<FsContent, IngestServiceFsContent> found = findFsContent(fsContent);

            if (found != null) {
                //FsContent already enqueued
                //merge services to use with already enqueued image
                found.add(service);
            } else {
                //enqueue brand new FsContent with the services
                found = new QueueUnit<FsContent, IngestServiceFsContent>(fsContent, service);
                fsContentUnits.add(found);
            }
        }

        void enqueue(FsContent fsContent, Collection<IngestServiceFsContent> services) {
            QueueUnit<FsContent, IngestServiceFsContent> found = findFsContent(fsContent);

            if (found != null) {
                //FsContent already enqueued
                //merge services to use with already enqueued FsContent
                found.addAll(services);
            } else {
                //enqueue brand new FsContent with the services
                found = new QueueUnit<FsContent, IngestServiceFsContent>(fsContent, services);
                fsContentUnits.add(found);
            }
        }

        boolean hasNext() {
            return !fsContentUnits.isEmpty();
        }

        int getCount() {
            return fsContentUnits.size();
        }

        void empty() {
            fsContentUnits.clear();
        }

        /**
         * Returns next FsContent and list of associated services
         * @return 
         */
        QueueUnit<FsContent, IngestServiceFsContent> dequeue() {
            if (!hasNext()) {
                throw new UnsupportedOperationException("FsContent processing queue is empty");
            }

            return fsContentUnits.remove(0);
        }

        private QueueUnit<FsContent, IngestServiceFsContent> findFsContent(FsContent fsContent) {
            QueueUnit<FsContent, IngestServiceFsContent> found = null;
            for (QueueUnit<FsContent, IngestServiceFsContent> unit : fsContentUnits) {
                if (unit.content.equals(fsContent)) {
                    found = unit;
                    break;
                }
            }
            return found;
        }

        @Override
        public synchronized String toString() {
            return "FsContentQueue, size: " + Integer.toString(fsContentUnits.size());
        }
    }

    /**
     * manages queue of pending Images and IngestServiceImage to use on that image.
     * image / service pairs are added one at a time and internally, it keeps track of all
     * services for a given image.
     */
    private class ImageQueue {

        List<QueueUnit<Image, IngestServiceImage>> imageUnits = new ArrayList<QueueUnit<Image, IngestServiceImage>>();

        void enqueue(Image image, IngestServiceImage service) {
            QueueUnit<Image, IngestServiceImage> found = findImage(image);

            if (found != null) {
                //image already enqueued
                //merge services to use with already enqueued image
                found.add(service);
            } else {
                //enqueue brand new image with the services
                found = new QueueUnit<Image, IngestServiceImage>(image, service);
                imageUnits.add(found);
            }
        }

        void enqueue(Image image, Collection<IngestServiceImage> services) {
            QueueUnit<Image, IngestServiceImage> found = findImage(image);

            if (found != null) {
                //image already enqueued
                //merge services to use with already enqueued image
                found.addAll(services);
            } else {
                //enqueue brand new image with the services
                found = new QueueUnit<Image, IngestServiceImage>(image, services);
                imageUnits.add(found);
            }
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
        QueueUnit<Image, IngestServiceImage> dequeue() {
            if (!hasNext()) {
                throw new UnsupportedOperationException("Image processing queue is empty");
            }

            return imageUnits.remove(0);
        }

        /**
         * Search existing list to see if an image already has a set of 
         * services associated with it
         * @param image
         * @return 
         */
        private QueueUnit<Image, IngestServiceImage> findImage(Image image) {
            QueueUnit<Image, IngestServiceImage> found = null;
            for (QueueUnit<Image, IngestServiceImage> unit : imageUnits) {
                if (unit.content.equals(image)) {
                    found = unit;
                    break;
                }
            }
            return found;
        }

        @Override
        public synchronized String toString() {
            return "ImageQueue, size: " + Integer.toString(imageUnits.size());
        }
    }

    /**
     * generic representation of queued content (Image or FsContent) and its services
     */
    private class QueueUnit<T, S> {

        T content;
        Set<S> services;

        QueueUnit(T content, S service) {
            this.content = content;
            this.services = new HashSet<S>();
            add(service);
        }

        QueueUnit(T content, Collection<S> services) {
            this.content = content;
            this.services = new HashSet<S>();
            addAll(services);
        }

        //merge services with the current collection of services per image
        //this assumes singleton instances of every service type for correct merge
        //in case of multiple instances, they need to be handled correctly after dequeue()
        final void addAll(Collection<S> services) {
            this.services.addAll(services);
        }

        //this assumes singleton instances of every service type for correct merge
        //in case of multiple instances, they need to be handled correctly after dequeue()
        final void add(S service) {
            this.services.add(service);
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
            if (startTime != null) {
                sb.append("Start time: ").append(dateFormatter.format(startTime)).append("<br />");
            }
            if (endTime != null) {
                sb.append("End time: ").append(dateFormatter.format(endTime)).append("<br />");
            }
            sb.append("Total ingest time: ").append(getTotalTimeString()).append("<br />");
            sb.append("Total errors: ").append(errorsTotal).append("<br />");
            if (errorsTotal > 0) {
                sb.append("Errors per service:");
                for (IngestServiceAbstract service : errors.keySet()) {
                    final int errorsService = errors.get(service);
                    sb.append("\t").append(service.getName()).append(": ").append(errorsService).append("<br />");
                }
            }
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
            }
            else {
                errors.put(source, curServiceErrorI + 1);
            }
        }
    }

//ingester worker for fsContent queue
//worker runs until fsContent queue is consumed
//and if needed, new instance is created and started when data arrives
    private class IngestFsContentThread extends SwingWorker {

        private Logger logger = Logger.getLogger(IngestFsContentThread.class.getName());
        private ProgressHandle progress;

        @Override
        protected Object doInBackground() throws Exception {

            logger.log(Level.INFO, "Starting background processing");
            stats.start();

            //notify main thread services started
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    for (IngestServiceFsContent s : fsContentServices) {
                        IngestManager.firePropertyChange(SERVICE_STARTED_EVT, s.getName());
                    }
                }
            });

            progress = ProgressHandleFactory.createHandle("File Ingest", new Cancellable() {

                @Override
                public boolean cancel() {
                    return IngestFsContentThread.this.cancel(true);
                }
            });

            progress.start();
            progress.switchToIndeterminate();
            int numFsContents = getNumFsContents();
            progress.switchToDeterminate(numFsContents);
            int processedFiles = 0;
            initMainProgress(numFsContents);
            //process fscontents queue
            while (hasNextFsContent()) {
                QueueUnit<FsContent, IngestServiceFsContent> unit = getNextFsContent();
                for (IngestServiceFsContent service : unit.services) {
                    if (isCancelled()) {
                        return null;
                    }
                    try {
                        service.process(unit.content);
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Exception from service: " + service.getName(), e);
                        stats.addError(service);
                    }
                }
                int newFsContents = getNumFsContents();
                if (newFsContents > numFsContents) {
                    //update progress bar if new enqueued
                    numFsContents = newFsContents + processedFiles + 1;
                    progress.switchToIndeterminate();
                    progress.switchToDeterminate(numFsContents);
                    initMainProgress(numFsContents);

                }
                progress.progress(unit.content.getName(), ++processedFiles);
                updateMainProgress(processedFiles);
                --numFsContents;
            }
            logger.log(Level.INFO, "Done background processing");
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get(); //block and get all exceptions thrown while doInBackground()
                //notify services of completion
                if (!this.isCancelled()) {
                    for (IngestServiceFsContent s : fsContentServices) {
                        s.complete();
                        IngestManager.firePropertyChange(SERVICE_COMPLETED_EVT, s.getName());
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
                    tc.displayReport(stats.toHtmlString());
                }
                initMainProgress(0);
            }

        }

        private void handleInterruption() {
            for (IngestServiceFsContent s : fsContentServices) {
                s.stop();
                IngestManager.firePropertyChange(SERVICE_STOPPED_EVT, s.getName());
            }
            //empty queues
            emptyFsContents();

            //reset main progress bar
            initMainProgress(0);
        }
    }

    /* Thread that adds image/file and service pairs to queues */
    private class EnqueueWorker extends SwingWorker {

        Collection<IngestServiceAbstract> services;
        final Collection<Image> images;
        int total;

        EnqueueWorker(final Collection<IngestServiceAbstract> services, final Collection<Image> images) {
            this.services = services;
            this.images = images;
        }
        private ProgressHandle progress;

        @Override
        protected Object doInBackground() throws Exception {
            progress = ProgressHandleFactory.createHandle("Queueing Ingest", new Cancellable() {

                @Override
                public boolean cancel() {
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
                handleInterruption();
            } catch (InterruptedException ex) {
                handleInterruption();
            } catch (ExecutionException ex) {
                handleInterruption();


            } catch (Exception ex) {
                handleInterruption();

            } finally {
                //queing end
                if (this.isCancelled()) {
                    //empty queues
                    handleInterruption();
                } else {
                    //start ingest workers
                    startAll();
                }
                progress.finish();
                tc.enableStartButton(true);
            }
        }

        private void queueAll(Collection<IngestServiceAbstract> services, final Collection<Image> images) {
            int processed = 0;
            for (Image image : images) {
                final String imageName = image.getName();
                for (IngestServiceAbstract service : services) {
                    if (isCancelled()) {
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
                        case FsContent:
                            //enqueue the same singleton fscontent service
                            addFsContent((IngestServiceFsContent) service, image);
                            break;
                        default:
                            logger.log(Level.SEVERE, "Unexpected service type: " + service.getType().name());
                    }
                    progress.progress(serviceName + " " + imageName, ++processed);
                }
            }
        }

        private void handleInterruption() {
            //empty queues
            emptyFsContents();
            emptyImages();
        }
    }
}
