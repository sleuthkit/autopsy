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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleImage;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.Image;

/**
 * Example implementation of an image ingest service 
 * 
 */
public final class ExampleImageIngestModule implements IngestModuleImage {

    private static final Logger logger = Logger.getLogger(ExampleImageIngestModule.class.getName());
    private static ExampleImageIngestModule defaultInstance = null;
    private IngestServices services;
    private static int messageId = 0;

    //public constructor is required
    //as multiple instances are created for processing multiple images simultenously
    public ExampleImageIngestModule() {
    }

    //default instance used for service registration
    public static synchronized ExampleImageIngestModule getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new ExampleImageIngestModule();
        }
        return defaultInstance;
    }

    @Override
    public void process(Image image, IngestImageWorkerController controller) {
        logger.log(Level.INFO, "process() " + this.toString());

        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + image.getName()));

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
                services.postMessage(IngestMessage.createMessage(processedFiles, MessageType.INFO, this, "Processed " + image.getName() + ": " + Integer.toString(processedFiles)));

                //update progress
                controller.progress(++processedFiles);
            } catch (InterruptedException e) {
            }
        }


    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete() " + this.toString());

        final IngestMessage msg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Complete");
        services.postMessage(msg);

        //service specific cleanup due to completion here
    }

    @Override
    public String getName() {
        return "Example Image Service";
    }
    
    @Override
    public String getDescription() {
        return "Example Image Service description";
    }

    @Override
    public void init(IngestModuleInit initContext) {
        logger.log(Level.INFO, "init() " + this.toString());
        services = IngestServices.getDefault();

        //service specific initialization here

    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Stopped"));

        //service specific cleanup due to interruption here
    }

    @Override
    public ModuleType getType() {
        return ModuleType.Image;
    }

     @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }
    
    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return null;
    }
    
    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        return null;
    }
    
    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
    
    
    @Override
    public void saveAdvancedConfiguration() {
    }
    
    @Override
    public void saveSimpleConfiguration() {
    }
}
