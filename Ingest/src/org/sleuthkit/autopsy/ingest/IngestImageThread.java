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

//ingester worker for image queue
import java.awt.EventQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.datamodel.Image;

/**
 * worker for every ingest image service there is a separate instance per image
 * / service pair
 */
public class IngestImageThread extends SwingWorker<Object, Void> {

    private Logger logger = Logger.getLogger(IngestImageThread.class.getName());
    private ProgressHandle progress;
    private Image image;
    private IngestServiceImage service;
    private IngestImageWorkerController controller;
    private IngestManager manager;

    IngestImageThread(IngestManager manager, Image image, IngestServiceImage service) {
        this.manager = manager;
        this.image = image;
        this.service = service;
    }

    Image getImage() {
        return image;
    }

    IngestServiceImage getService() {
        return service;
    }

    @Override
    protected Object doInBackground() throws Exception {

        logger.log(Level.INFO, "Starting processing of service: " + service.getName());

        final String displayName = service.getName() + " image id:" + image.getId();
        progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                logger.log(Level.INFO, "Image ingest service " + service.getName() + " cancelled by user.");
                if (progress != null) {
                    progress.setDisplayName(displayName + " (Cancelling...)");
                }
                return IngestImageThread.this.cancel(true);
            }
        });

        progress.start();
        progress.switchToIndeterminate();

        controller = new IngestImageWorkerController(this, progress);

        if (isCancelled()) {
            logger.log(Level.INFO, "Terminating image ingest service " + service.getName() + " due to cancellation.");
            return null;
        }
        final StopWatch timer = new StopWatch();
        timer.start();
        try {
            service.process(image, controller);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Exception in service: " + service.getName() + " image: " + image.getName(), e);
        } finally {
            timer.stop();
            logger.log(Level.INFO, "Done processing of service: " + service.getName() 
                    + " took " + timer.getElapsedTimeSecs() + " secs. to process()" );

            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progress.finish();
                }
            });


            //cleanup queues (worker and image/service)
            manager.removeImageIngestWorker(this);

            if (!this.isCancelled()) {
                logger.log(Level.INFO, "Service " + service.getName() + " completed");
                try {
                    service.complete();
                }
                catch (Exception e) {
                    logger.log(Level.INFO, "Error completing the service " + service.getName(), e);
                }
                IngestManager.fireServiceEvent(IngestModuleEvent.COMPLETED.toString(), service.getName());
            } else {
                logger.log(Level.INFO, "Service " + service.getName() + " stopped");
                try {
                    service.stop();
                }
                catch (Exception e) {
                    logger.log(Level.INFO, "Error stopping the service" + service.getName(), e);
                }
                IngestManager.fireServiceEvent(IngestModuleEvent.STOPPED.toString(), service.getName());
            }

        }
        return null;
    }
}
