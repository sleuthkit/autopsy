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

import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstract.ServiceType;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Example implementation of a fscontent image ingest service 
 * 
 */
public class ExampleAbstractFileIngestService implements IngestServiceAbstractFile {

    private static final Logger logger = Logger.getLogger(ExampleAbstractFileIngestService.class.getName());
    private static ExampleAbstractFileIngestService instance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;

    //file ingest services require a private constructor
    //to ensure singleton instances
    private ExampleAbstractFileIngestService() {
        
    }
    
    public static synchronized ExampleAbstractFileIngestService getDefault() {
        if (instance == null) {
            instance = new ExampleAbstractFileIngestService();
        }
        return instance;
    }

    @Override
    public ProcessResult process(AbstractFile fsContent) {
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + fsContent.getName()));

        //service specific AbstractFile processing code here
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }

        return ProcessResult.OK;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Complete"));

        //service specific cleanup due completion here
    }

    @Override
    public String getName() {
        return "Example AbstractFile Service";
    }

    @Override
    public String getDescription() {
        return "Example AbstractFile Service description";
    }
    
    

    @Override
    public void init(IngestManagerProxy managerProxy) {
        logger.log(Level.INFO, "init()");
        this.managerProxy = managerProxy;

        //service specific initialization here
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Stopped"));

        //service specific cleanup due interruption here
    }

    @Override
    public ServiceType getType() {
        return ServiceType.AbstractFile;
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
