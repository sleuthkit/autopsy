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
package org.sleuthkit.autopsy.ingest.example;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceImage;
import org.sleuthkit.datamodel.Image;

/**
 * Example implementation of an image ingest service 
 * 
 */
public final class ExampleImageIngestService implements IngestServiceImage {

    private static final Logger logger = Logger.getLogger(ExampleImageIngestService.class.getName());
    private static ExampleImageIngestService defaultInstance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;

    //public constructor is required
    //as multiple instances are created for processing multiple images simultenously
    public ExampleImageIngestService() {
    }

    //default instance used for service registration
    public static synchronized ExampleImageIngestService getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new ExampleImageIngestService();
        }
        return defaultInstance;
    }

    @Override
    public void process(Image image, IngestImageWorkerController controller) {
        logger.log(Level.INFO, "process() " + this.toString());

        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + image.getName()));

        //service specific Image processing code here
        //example:

        //if we know amount of work units, we can switch to determinate and update progress bar
        int filesToProcess = 100;
        controller.switchToDeterminate(filesToProcess);
        int processedFiles = 0;

        while (filesToProcess-- > 0) {

            //check if should terminate on every loop iteration
            if (controller.isCancelled()) {
                return;
            }
            try {
                //do the work
                Thread.sleep(500);
                //post message to user if found something interesting
                managerProxy.postMessage(IngestMessage.createMessage(processedFiles, MessageType.INFO, this, "Processed " + image.getName() + ": " + Integer.toString(processedFiles)));

                //update progress
                controller.progress(++processedFiles);
            } catch (InterruptedException e) {
            }
        }


    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete() " + this.toString());

        final IngestMessage msg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, "completed image processing");
        managerProxy.postMessage(msg);

        //service specific cleanup due to completion here
    }

    @Override
    public String getName() {
        return "Example Image Service";
    }

    @Override
    public void init(IngestManagerProxy managerProxy) {
        logger.log(Level.INFO, "init() " + this.toString());
        this.managerProxy = managerProxy;

        //service specific initialization here

    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

        //service specific cleanup due to interruption here
    }

    @Override
    public ServiceType getType() {
        return ServiceType.Image;
    }
    
    @Override
    public void userConfigure() {
        
    }
}
