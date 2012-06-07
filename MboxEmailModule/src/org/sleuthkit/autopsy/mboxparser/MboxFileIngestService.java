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
package org.sleuthkit.autopsy.mboxparser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tika.exception.TikaException;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstract.*;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskException;
import org.xml.sax.SAXException;

public class MboxFileIngestService implements IngestServiceAbstractFile {

    private static final Logger logger = Logger.getLogger(MboxFileIngestService.class.getName());
    private static MboxFileIngestService instance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;

    public static synchronized MboxFileIngestService getDefault() {
        if (instance == null) {
            instance = new MboxFileIngestService();
        }
        return instance;
    }

    @Override
    public ProcessResult process(AbstractFile fsContent) {
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + fsContent.getName()));
        MboxEmailParser mbox = new MboxEmailParser();
        boolean isMbox = false;

        try {
            byte[] t = new byte[(int) 128];
            int byteRead = fsContent.read(t, 0, 128);
            isMbox = mbox.detectMediaTypeFromBytes(t, fsContent.getName());
        } catch (TskException ex) {
            Logger.getLogger(MboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
        }


        if (isMbox) {
            try {
                ReadContentInputStream contentStream = new ReadContentInputStream(fsContent);
                mbox.parse(contentStream);

            } catch (FileNotFoundException ex) {
                Logger.getLogger(MboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(MboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SAXException ex) {
                Logger.getLogger(MboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
            } catch (TikaException ex) {
                Logger.getLogger(MboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    
    return ProcessResult.OK ;
}
@Override
        public void complete() {
        logger.log(Level.INFO, "complete()");
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "COMPLETE"));

        //service specific cleanup due completion here
    }

    @Override
        public String getName() {
        return "Mbox Parser";
    }

    @Override
        public String getDescription() {
        return "This class parses through a file to determine if it is an mbox file and if so, populates an email artifact for it in the blackboard.";
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

        //service specific cleanup due interruption here
    }

    @Override
        public ServiceType getType() {
        return ServiceType.Image;
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