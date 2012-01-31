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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceFsContent;
import org.sleuthkit.datamodel.FsContent;

//service provider registered in layer.xml
public final class KeywordSearchIngestService implements IngestServiceFsContent {

    private static final Logger logger = Logger.getLogger(KeywordSearchIngestService.class.getName());
    private static KeywordSearchIngestService instance = null;
    
    private IngestManager manager;
    

    public static synchronized KeywordSearchIngestService getDefault() {
        if (instance == null) {
            instance = new KeywordSearchIngestService();
        }
        return instance;
    }

    @Override
    public void process(FsContent fsContent) {
        //logger.log(Level.INFO, "Processing fsContent: " + fsContent.getName());
        try {
            Thread.sleep(100);
        }
        catch (InterruptedException e) {}
        //manager.postMessage(IngestMessage.createMessage(1, MessageType.INFO, this, "Processing " + fsContent.getName()));
       
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
        manager.postMessage(IngestMessage.createMessage(1, MessageType.INFO, this, "COMPLETE"));
    }

    @Override
    public String getName() {
        return "Keyword Search";
    }

    @Override
    public void init(IngestManager manager) {
        logger.log(Level.INFO, "init()");
        this.manager = manager;
        
        manager.postMessage(IngestMessage.createMessage(1, MessageType.WARNING, this, "INIT"));
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
    }

    @Override
    public ServiceType getType() {
        return ServiceType.FsContent;
    }
}
