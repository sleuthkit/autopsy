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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.datamodel.Image;

/**
 * worker for every ingest image service
 * there is a separate instance per image / service pair
 */
public class IngestImageThread extends SwingWorker {

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

        logger.log(Level.INFO, "Starting background processing");

        progress = ProgressHandleFactory.createHandle(service.getName() + " image id:" + image.getId(), new Cancellable() {

            @Override
            public boolean cancel() {
                return IngestImageThread.this.cancel(true);
            }
        });

        progress.start();
        progress.switchToIndeterminate();

        controller = new IngestImageWorkerController(this, progress);

        if (isCancelled()) {
            return null;
        }
        try {
            service.process(image, controller);
        } catch (Exception e) {
            logger.log(Level.INFO, "Exception in service: " + service.getName() + " image: " + image.getName(), e);
        }
        logger.log(Level.INFO,
                "Done background processing");
        return null;
    }

    @Override
    protected void done() {
        try {
            super.get(); //block and get all exceptions thrown while doInBackground()
            //notify services of completion
            if (!this.isCancelled()) {
                service.complete();
                IngestManager.firePropertyChange(IngestManager.SERVICE_COMPLETED_EVT, service.getName());
            }
        } catch (CancellationException e) {
            //task was cancelled
            handleInterruption();
        } catch (InterruptedException ex) {
            handleInterruption();
        } catch (ExecutionException ex) {
            handleInterruption();
            logger.log(Level.SEVERE, "Fatal error during image ingest from sevice: " + service.getName() + " image: " + image.getName(), ex);
        } catch (Exception ex) {
            handleInterruption();
            logger.log(Level.SEVERE, "Fatal error during image ingest in service: " + service.getName() + " image: " + image.getName(), ex);
        } finally {
            progress.finish();

            //cleanup queues (worker and image/service)
            manager.removeImageIngestWorker(this);
        }
    }
    
    private void handleInterruption() {
        service.stop();
        IngestManager.firePropertyChange(IngestManager.SERVICE_STOPPED_EVT, service.getName());
    }
}
