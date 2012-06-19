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
package org.sleuthkit.autopsy.thunderbirdparser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstract.*;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.xml.sax.SAXException;

public class ThunderbirdMboxFileIngestService implements IngestServiceAbstractFile {

    private static final Logger logger = Logger.getLogger(ThunderbirdMboxFileIngestService.class.getName());
    private static ThunderbirdMboxFileIngestService instance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;
    private static final String classname = "Thunderbird Parser";

    public static synchronized ThunderbirdMboxFileIngestService getDefault() {
        if (instance == null) {
            instance = new ThunderbirdMboxFileIngestService();
        }
        return instance;
    }

    @Override
    public ProcessResult process(AbstractFile fsContent) {
        ThunderbirdEmailParser mbox = new ThunderbirdEmailParser();
        boolean isMbox = false;

        try {
            byte[] t = new byte[(int) 128];
            int byteRead = fsContent.read(t, 0, 128);
            isMbox = mbox.isValidMimeTypeMbox(t);
        } catch (TskException ex) {
            Logger.getLogger(ThunderbirdMboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
        }


        if (isMbox) {
            managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + fsContent.getName()));
            try {
                String emailId = "";
                Map<String, String> propertyMap = new HashMap<String, String>();
                String content = "";
                String from = "";
                String to = "";
                String stringDate = "";
                Long date = 0L;
                String subject = "";
                String cc = "";
                String bcc = "";
                ReadContentInputStream contentStream = new ReadContentInputStream(fsContent);
                mbox.parse(contentStream);
                HashMap<String, Map<String, String>> emailMap = new HashMap<String,Map<String,String>>();
                emailMap = mbox.getAllEmails();
                for (Entry<String, Map<String, String>> entry : emailMap.entrySet()) {
                    emailId = ((entry.getKey().toString() != null) ? entry.getKey().toString() : "");
                    propertyMap = entry.getValue();
                    content = ((propertyMap.get("content") != null) ? propertyMap.get("content") :"");
                    from = ((propertyMap.get(Metadata.AUTHOR) != null) ? propertyMap.get(Metadata.AUTHOR) :"");
                    to = ((propertyMap.get(Metadata.MESSAGE_TO) != null) ? propertyMap.get(Metadata.MESSAGE_TO) :"");
                    stringDate = ((propertyMap.get("date") != null) ? propertyMap.get("date") :"");
                    if(!"".equals(stringDate)){
                    date = mbox.getDateCreated(stringDate);
                    }
                    subject = ((propertyMap.get(Metadata.SUBJECT) != null) ? propertyMap.get(Metadata.SUBJECT) :"");
                    cc = ((propertyMap.get(Metadata.MESSAGE_CC) != null) ? propertyMap.get(Metadata.MESSAGE_CC) :"");
                    bcc =((propertyMap.get(Metadata.MESSAGE_BCC) != null) ? propertyMap.get(Metadata.MESSAGE_BCC) :"");
                }

                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_TO.getTypeID(), classname, "", to));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID(), classname, "", cc));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID(), classname, "", bcc));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_FROM.getTypeID(), classname, "", from));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID(), classname, "", content));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML.getTypeID(), classname, "", content));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_ID.getTypeID(), classname, "", emailId));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_REPLY_ID.getTypeID(), classname, "",));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID(), classname, "", date));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID(), classname, "", date));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID(), classname, "", subject));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), classname, "", "/Account1/Folder1"));
                BlackboardArtifact bbart;
                try {
                    bbart = fsContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);
                    bbart.addAttributes(bbattributes);
                } catch (TskCoreException ex) {
                    Logger.getLogger(ThunderbirdMboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
                }



                IngestManager.fireServiceDataEvent(new ServiceDataEvent(classname, BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SAXException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
            } catch (TikaException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return ProcessResult.OK;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "COMPLETE"));

        //service specific cleanup due completion here
    }

    @Override
    public String getName() {
        return "Thunderbird Parser";
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