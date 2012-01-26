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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;

/**
 * IngestManager sets up and manages ingest services
 * runs them in a background thread
 * notifies services when work is complete or should be interrupted
 * processes messages from services in postMessage() and posts them to GUI
 * 
 */
public class IngestManager {

    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private IngestTopComponent tc;
    private IngestManagerStats stats;
    private int updateFrequency;
    //queues
    private final Object queueLock = new Object();
    private final ImageQueue imageQueue = new ImageQueue();
    private final FsContentQueue fsContentQueue = new FsContentQueue();
    private IngestThread ingester;
    final Collection<IngestServiceImage> imageServices = enumerateImageServices();
    final Collection<IngestServiceFsContent> fsContentServices = enumerateFsContentServices();

    /**
     * 
     * @param tc handle to Ingest top component
     */
    IngestManager(IngestTopComponent tc) {
        this.tc = tc;

        //one time initialization of services
        for (IngestServiceImage s : imageServices) {
            s.init(this);
        }
        for (IngestServiceFsContent s : fsContentServices) {
            s.init(this);
        }
    }

    /**
     * IngestManager entry point, enqueues image to be processed.
     * Spawns background thread which enumerates all sorted files and executes chosen services per file in a pre-determined order.
     * Notifies services when work is complete or should be interrupted using complete() and stop() calls.
     * Does not block and can be called multiple times to enqueue more work to already running background process.
     */
    void execute(Collection<IngestServiceAbstract> services, final Collection<Image> images) {

        for (Image image : images) {
            for (IngestServiceAbstract service : services) {
                switch (service.getType()) {
                    case Image:
                        addImage((IngestServiceImage) service, image);
                        break;
                    case FsContent:
                        addFsContent((IngestServiceFsContent) service, image);
                        break;
                    default:
                        logger.log(Level.SEVERE, "Unexpected service type: " + service.getType().name());
                }
            }
        }

        logger.log(Level.INFO, "Queues: " + imageQueue.toString() + " " + fsContentQueue.toString());

        boolean start = false;
        if (ingester == null) {
            start = true;

        } //if worker had completed, restart it in case data is still enqueued
        else if (ingester.isDone()
                && (hasNextFsContent() || hasNextImage())) {
            logger.log(Level.INFO, "Restarting ingester thread.");
            start = true;
        } else {
            logger.log(Level.INFO, "Ingester is still running");
        }

        if (start) {
            logger.log(Level.INFO, "Starting new ingester.");
            ingester = new IngestThread();
            stats = new IngestManagerStats();
            ingester.execute();
        }
    }

    /**
     * returns the current minimal update frequency setting
     * Services should call this between processing iterations to get current setting
     * and use the setting to change notification and data refresh intervals
     */
    public synchronized int getUpdateFrequency() {
        return updateFrequency;
    }

    /**
     * set new minimal update frequency services should use
     * @param frequency 
     */
    synchronized void setUpdateFrequency(int frequency) {
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
    public synchronized void postMessage(final IngestMessage message) {

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

    private void addImage(IngestServiceImage service, Image image) {

        synchronized (queueLock) {
            imageQueue.enqueue(image, service);
            //queueLock.notifyAll();
        }


    }

    private void addFsContent(IngestServiceFsContent service, Image image) {
        Collection<FsContent> fsContents = new GetAllFilesContentVisitor().visit(image);
        synchronized (queueLock) {
            for (FsContent fsContent : fsContents) {
                fsContentQueue.enqueue(fsContent, service);
            }
            //queueLock.notifyAll();
        }
        //logger.log(Level.INFO, fsContentQueue.toString());
    }

    /**
     * get next file/dir to process
     * the queue of FsContent to process is maintained internally 
     * and could be dynamically sorted as data comes in
     */
    private QueueUnit<FsContent, IngestServiceFsContent> getNextFsContent() {
        QueueUnit<FsContent, IngestServiceFsContent> ret = null;
        synchronized (queueLock) {
            ret = fsContentQueue.dequeue();

        }
        return ret;
    }

    private boolean hasNextFsContent() {
        boolean ret = false;
        synchronized (queueLock) {
            ret = fsContentQueue.hasNext();
        }
        return ret;
    }

    private int getNumFsContents() {
        int ret = 0;
        synchronized (queueLock) {
            ret = fsContentQueue.getCount();
        }
        return ret;
    }

    /**
     * get next Image to process
     * the queue of Images to process is maintained internally 
     * and could be dynamically sorted as data comes in
     */
    private QueueUnit<Image, IngestServiceImage> getNextImage() {
        QueueUnit<Image, IngestServiceImage> ret = null;
        synchronized (queueLock) {
            ret = imageQueue.dequeue();
        }
        return ret;
    }

    private boolean hasNextImage() {
        boolean ret = false;
        synchronized (queueLock) {
            ret = imageQueue.hasNext();
        }
        return ret;
    }

    private int getNumImages() {
        int ret = 0;
        synchronized (queueLock) {
            ret = imageQueue.getCount();
        }
        return ret;
    }

    //manages queue of pending FsContent and IngestServiceFsContent to use on that content
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
        public String toString() {
            return "FsContentQueue, size: " + Integer.toString(fsContentUnits.size());
        }
    }

    //manages queue of pending Images and IngestServiceImage to use on that image
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

        QueueUnit<Image, IngestServiceImage> dequeue() {
            if (!hasNext()) {
                throw new UnsupportedOperationException("Image processing queue is empty");
            }

            return imageUnits.remove(0);
        }

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
        public String toString() {
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
        //this assumes that there is one singleton instance of each type of service
        final void addAll(Collection<S> services) {
            this.services.addAll(services);
        }

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
            StringBuilder sb = new StringBuilder();
            if (startTime != null) {
                sb.append("Start time: ").append(dateFormatter.format(startTime)).append("\n");
            }
            if (endTime != null) {
                sb.append("End time: ").append(dateFormatter.format(endTime)).append("\n");
            }
            sb.append("Total ingest time: ").append(getTotalTimeString()).append("\n");
            sb.append("Total errors: ").append(errorsTotal).append("\n");
            if (errorsTotal > 0) {
                sb.append("Errors per service:");
                for (IngestServiceAbstract service : errors.keySet()) {
                    final int errorsService = errors.get(service);
                    sb.append("\t").append(service.getName()).append(": ").append(errorsService).append("\n");
                }
            }
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

        String getTotalTimeString() {
            long ms = getTotalTime();
            long hours = TimeUnit.MILLISECONDS.toHours(ms);
            ms -= TimeUnit.HOURS.toMillis(hours);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
            ms -= TimeUnit.MINUTES.toMillis(minutes);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(ms);
            final StringBuilder sb = new StringBuilder();
            sb.append(hours<10?"0":"").append(hours).append(':').append(minutes<10?"0":"")
                    .append(minutes).append(':').append(seconds<10?"0":"").append(seconds);
            return sb.toString();
        }

        void addError(IngestServiceAbstract source) {
            ++errorsTotal;
            int curServiceError = errors.get(source);
            errors.put(source, curServiceError + 1);
        }
    }

    //ingester worker doing work in background
    //in current design, worker runs until queues are consumed
    //and if needed, it is restarted when data arrives
    private class IngestThread extends SwingWorker {

        private Logger logger = Logger.getLogger(IngestThread.class.getName());
        private ProgressHandle progress;

        @Override
        protected Object doInBackground() throws Exception {

            logger.log(Level.INFO, "Starting background processing");
            stats.start();

            progress = ProgressHandleFactory.createHandle("Ingesting");

            progress.start();
            progress.switchToIndeterminate();
            int numImages = getNumImages();
            progress.switchToDeterminate(numImages);
            int processedImages = 0;
            //process image queue
            while (hasNextImage()) {
                QueueUnit<Image, IngestServiceImage> unit = getNextImage();
                for (IngestServiceImage service : unit.services) {
                    if (isCancelled()) {
                        for (IngestServiceImage s : imageServices) {
                            s.stop();
                        }
                        return null;
                    }

                    try {
                        service.process(unit.content);
                        //check if new files enqueued
                        int newImages = getNumImages();
                        if (newImages > numImages) {
                            numImages = newImages + processedImages + 1;
                            progress.switchToIndeterminate();
                            progress.switchToDeterminate(numImages);

                        }
                        progress.progress("Images (" + service.getName() + ")", ++processedImages);
                        --numImages;
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Exception from service: " + service.getName(), e);
                        stats.addError(service);
                    }
                }
            }

            progress.switchToIndeterminate();
            int numFsContents = getNumFsContents();
            progress.switchToDeterminate(numFsContents);
            int processedFiles = 0;
            //process fscontents queue
            progress.progress("Running file ingest services.");
            while (hasNextFsContent()) {
                QueueUnit<FsContent, IngestServiceFsContent> unit = getNextFsContent();
                for (IngestServiceFsContent service : unit.services) {
                    if (isCancelled()) {
                        for (IngestServiceFsContent s : fsContentServices) {
                            s.stop();
                        }
                        return null;
                    }
                    try {
                        service.process(unit.content);
                        int newFsContents = getNumFsContents();
                        if (newFsContents > numFsContents) {
                            //update progress bar if new enqueued
                            numFsContents = newFsContents + processedFiles + 1;
                            progress.switchToIndeterminate();
                            progress.switchToDeterminate(numFsContents);

                        }
                        progress.progress("Files (" + service.getName() + ")", ++processedFiles);
                        --numFsContents;
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Exception from service: " + service.getName(), e);
                        stats.addError(service);
                    }
                }
            }

            stats.end();
            logger.log(Level.INFO, "Done background processing");
            return null;
        }

        @Override
        protected void done() {
            try {
                super.get(); //block and get all exceptions thrown while doInBackground()

                logger.log(Level.INFO, "STATS: " + stats.toString());

                //notify services of completion
                for (IngestServiceImage s : imageServices) {
                    s.complete();
                }

                for (IngestServiceFsContent s : fsContentServices) {
                    s.complete();
                }

            } catch (InterruptedException ex) {
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Fatal error during ingest.", ex);

            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Fatal error during ingest.", ex);
            } finally {
                progress.finish();
            }

        }

        @Override
        protected void process(List chunks) {
            super.process(chunks);
        }
    }
}
