/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceImage;
import org.sleuthkit.datamodel.Image;
/**
 *
 * @author Alex
 */
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
/**
 * Example implementation of an image ingest service 
 * 
 */
public final class RAImageIngestService implements IngestServiceImage {

    private static final Logger logger = Logger.getLogger(RAImageIngestService.class.getName());
    private static RAImageIngestService instance = null;
    private IngestManager manager;
    
    private static int messageId = 0;

    public static synchronized RAImageIngestService getDefault() {
        if (instance == null) {
            instance = new RAImageIngestService();
        }
        return instance;
    }

    @Override
    public void process(Image image) {
        manager.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + image.getName()));

        //service specific Image processing code here
        try {
           // Thread.sleep(5000);
         ExtractAll eAll = new ExtractAll(); 
         eAll.extractToBlackboard();
        } 
        catch (Error e) {
        }


    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
        manager.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "COMPLETE"));
        
        //service specific cleanup due completion here
    }

    @Override
    public String getName() {
        return "Recent Activity Service";
    }

    @Override
    public void init(IngestManager manager) {
        logger.log(Level.INFO, "init()");
        this.manager = manager;

        //service specific initialization here
        
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
        
        //service specific cleanup due interruption here
    }

    @Override
    public ServiceType getType() {
        return ServiceType.Image;
    }
}
