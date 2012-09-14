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
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstract.ModuleType;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Example implementation of a file ingest module 
 * 
 */
public class ExampleAbstractFileIngestModule implements IngestModuleAbstractFile {

    private static final Logger logger = Logger.getLogger(ExampleAbstractFileIngestModule.class.getName());
    private static ExampleAbstractFileIngestModule instance = null;
    private IngestServices services;
    private static int messageId = 0;

    //file ingest modules require a private constructor
    //to ensure singleton instances
    private ExampleAbstractFileIngestModule() {
        
    }
    
    public static synchronized ExampleAbstractFileIngestModule getDefault() {
        if (instance == null) {
            instance = new ExampleAbstractFileIngestModule();
        }
        return instance;
    }

    @Override
    public ProcessResult process(AbstractFile fsContent) {
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + fsContent.getName()));

        //module specific AbstractFile processing code here
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }

        return ProcessResult.OK;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Complete"));

        //module specific cleanup due completion here
    }

    @Override
    public String getName() {
        return "Example AbstractFile Module";
    }

    @Override
    public String getDescription() {
        return "Example AbstractFile Module description";
    }
    
    

    @Override
    public void init(IngestModuleInit initContext) {
        logger.log(Level.INFO, "init()");
        services = IngestServices.getDefault();

        //module specific initialization here
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Stopped"));

        //module specific cleanup due interruption here
    }

    @Override
    public ModuleType getType() {
        return ModuleType.AbstractFile;
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
