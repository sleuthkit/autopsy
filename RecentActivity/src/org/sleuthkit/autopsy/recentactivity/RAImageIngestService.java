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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceImage;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.FileSystem;


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
        //logger.log(Level.INFO, "process() " + this.toString());
        manager.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + image.getName()));
        
        ExtractAll ext = new ExtractAll();
         Case currentCase = Case.getCurrentCase(); // get the most updated case
         SleuthkitCase sCurrentCase = currentCase.getSleuthkitCase();
           //long imageId = image.getId();
         Collection<FileSystem> imageFS = sCurrentCase.getFileSystems(image);
         List<String> imgIds = new LinkedList<String>();
         for(FileSystem img : imageFS ){
             Long tempID = img.getId();
              imgIds.add(tempID.toString());
         }
         
        try {
            //do the work for(FileSystem img : imageFS )
             ext.extractToBlackboard(controller, imgIds);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting recent activity", e);
            manager.postMessage(IngestMessage.createErrorMessage(++messageId, this, "Error extracting recent activity data"));
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
        return "Recent Activity";
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
