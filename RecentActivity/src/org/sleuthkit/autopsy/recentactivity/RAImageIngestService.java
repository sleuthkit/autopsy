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
package org.sleuthkit.autopsy.recentactivity;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceImage;
import org.sleuthkit.datamodel.Image;

/**
 * Example implementation of an image ingest service 
 * 
 */
public final class RAImageIngestService implements IngestServiceImage {

    private static final Logger logger = Logger.getLogger(RAImageIngestService.class.getName());
    private static RAImageIngestService defaultInstance = null;
    private IngestManager manager;
    private static int messageId = 0;

    //public constructor is required
    //as multiple instances are created for processing multiple images simultenously
    public RAImageIngestService() {
    }

    //default instance used for service registration
    public static synchronized RAImageIngestService getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new RAImageIngestService();
        }
        return defaultInstance;
    }

    @Override
    public void process(Image image, IngestImageWorkerController controller) {
        logger.log(Level.INFO, "process() " + this.toString());

        manager.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + image.getName()));

        //service specific Image processing code here
        //example:
        ExtractAll ext = new ExtractAll();
        int count = ext.getExtractCount();
        //if we know amount of work units, we can switch to determinate and update progress bar
        int filesToProcess = count;
        controller.switchToDeterminate(filesToProcess);
        int processedFiles = 0;

        while (filesToProcess-- > 0) {

            //check if should terminate on every loop iteration
            if (controller.isCancelled()) {
                return;
            }
            try {
                //do the work
                ext.extractToBlackboard();
                //post message to user if found something interesting
                manager.postMessage(IngestMessage.createMessage(processedFiles, MessageType.INFO, this, "Processed " + image.getName() + ": " + Integer.toString(processedFiles)));

                //update progress
                controller.progress(++processedFiles);
            } 
            catch (Error e) {
            }
        }


    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete() " + this.toString());

        final IngestMessage msg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, "completed image processing");
        manager.postMessage(msg);

        //service specific cleanup due to completion here
    }

    @Override
    public String getName() {
        return "Recent Activity Service";
    }

    @Override
    public void init(IngestManager manager) {
        logger.log(Level.INFO, "init() " + this.toString());
        this.manager = manager;

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
}
