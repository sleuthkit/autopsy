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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstract.*;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.xml.sax.SAXException;
import org.apache.commons.lang.StringEscapeUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;


public class ThunderbirdMboxFileIngestModule implements IngestModuleAbstractFile {

    private static final Logger logger = Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName());
    private static ThunderbirdMboxFileIngestModule instance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;
    private static final String classname = "Thunderbird Parser";
    private final String hashDBModuleName = "Hash Lookup";
    private final GetIsFileKnownVisitor getIsFileKnown = new GetIsFileKnownVisitor();

    public static synchronized ThunderbirdMboxFileIngestModule getDefault() {
        if (instance == null) {
            instance = new ThunderbirdMboxFileIngestModule();
        }
        return instance;
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        ThunderbirdEmailParser mbox = new ThunderbirdEmailParser();
        boolean isMbox = false;

        IngestModuleAbstractFile.ProcessResult hashDBResult = 
                managerProxy.getAbstractFileModuleResult(hashDBModuleName);

        if (abstractFile.accept(getIsFileKnown) == true) {
            return ProcessResult.OK; //file is known, stop processing it
        } else if (hashDBResult == IngestModuleAbstractFile.ProcessResult.ERROR) {
            return ProcessResult.ERROR;  //file has read error, stop processing it
        }

        try {
            byte[] t = new byte[64];
            if(abstractFile.getSize() > 64) {
                int byteRead = abstractFile.read(t, 0, 64);
                isMbox = mbox.isValidMimeTypeMbox(t);
            }
        } catch (TskException ex) {
            Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
        }


        if (isMbox) {
            managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Processing " + abstractFile.getName()));
            String mboxName = abstractFile.getName();
            String msfName = mboxName + ".msf";
            Long mboxId = abstractFile.getId();
            String mboxPath = "";
            Long msfId = 0L;
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tskCase = currentCase.getSleuthkitCase();
            try {
                ResultSet rs = tskCase.runQuery("select parent_path from tsk_files where obj_id = '" + mboxId.toString() + "'");
                mboxPath = rs.getString("parent_path");
                Statement s = rs.getStatement();
                rs.close();
                if (s != null) {
                    s.close();
                }
                rs.close();
                rs.getStatement().close();

                ResultSet resultset = tskCase.runQuery("select obj_id from tsk_files where parent_path = '" + mboxPath + "' and name = '" + msfName + "'");
                msfId = resultset.getLong("obj_id");
                Statement st = resultset.getStatement();
                resultset.close();
                if (st != null) {
                    st.close();
                }
                resultset.close();
                resultset.getStatement().close();

            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Error while trying to get parent path for:" + this.getClass().getName(), ex);
            }

            try {
                Content msfContent = tskCase.getContentById(msfId);
                ContentUtils.writeToFile(msfContent, new File(currentCase.getTempDirectory() + File.separator + msfName));
            } catch (IOException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to obtain msf file for mbox parsing:" + this.getClass().getName(), ex);
            }
            int index = 0;
            String replace = "";
            boolean a = mboxPath.indexOf("/ImapMail/") > 0;
            boolean b = mboxPath.indexOf("/Mail/") > 0;
            if (b == true) {
                index = mboxPath.indexOf("/Mail/");
                replace = "/Mail";
            } else if (a == true) {
                index = mboxPath.indexOf("/ImapMail/");
                replace = "/ImapMail";
            } else {
                replace = "";

            }
            String folderPath = mboxPath.substring(index);
            folderPath = folderPath.replaceAll(replace, "");
            folderPath = folderPath + mboxName;
            folderPath = folderPath.replaceAll(".sbd", "");
//            Reader reader = null;
//            try {
//                reader = new FileReader(currentCase.getTempDirectory() + File.separator + msfName);
//            } catch (FileNotFoundException ex) {
//                Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
//            }
//            MorkDocument morkDocument = new MorkDocument(reader);
//            List<Dict> dicts = morkDocument.getDicts();
//            for(Dict dict : dicts){
//            String path = dict.getValue("81").toString();
//             String account = dict.getValue("8D").toString();
//                    }
            String emailId = "";
            String content = "";
            String from = "";
            String to = "";
            String stringDate = "";
            Long date = 0L;
            String subject = "";
            String cc = "";
            String bcc = "";
            try {
                ReadContentInputStream contentStream = new ReadContentInputStream(abstractFile);
                mbox.parse(contentStream);
                HashMap<String, Map<String, String>> emailMap = new HashMap<String, Map<String, String>>();
                emailMap = mbox.getAllEmails();
                for (Entry<String, Map<String, String>> entry : emailMap.entrySet()) {
                    Map<String, String> propertyMap = new HashMap<String, String>();
                    emailId = ((entry.getKey() != null) ? entry.getKey() : "Not Available");
                    propertyMap = entry.getValue();
                    content = ((propertyMap.get("content") != null) ? propertyMap.get("content") : "");
                    from = ((propertyMap.get(Metadata.AUTHOR) != null) ? propertyMap.get(Metadata.AUTHOR) : "");
                    to = ((propertyMap.get(Metadata.MESSAGE_TO) != null) ? propertyMap.get(Metadata.MESSAGE_TO) : "");
                    stringDate = ((propertyMap.get("date") != null) ? propertyMap.get("date") : "");
                    if (!"".equals(stringDate)) {
                        date = mbox.getDateCreated(stringDate);
                    }
                    subject = ((propertyMap.get(Metadata.SUBJECT) != null) ? propertyMap.get(Metadata.SUBJECT) : "");
                    cc = ((propertyMap.get(Metadata.MESSAGE_CC) != null) ? propertyMap.get(Metadata.MESSAGE_CC) : "");
                    bcc = ((propertyMap.get(Metadata.MESSAGE_BCC) != null) ? propertyMap.get(Metadata.MESSAGE_BCC) : "");

                    Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_TO.getTypeID(), classname, to));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID(), classname, cc));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID(), classname, bcc));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_FROM.getTypeID(), classname, from));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID(), classname, content.replaceAll("\\<[^>]*>", "")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML.getTypeID(), classname, content));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_ID.getTypeID(), classname, StringEscapeUtils.escapeHtml(emailId)));
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_REPLY_ID.getTypeID(), classname, "",));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID(), classname, date));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID(), classname, date));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID(), classname, subject));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), classname, folderPath));
                    BlackboardArtifact bbart;
                    try {
                        bbart = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);
                        bbart.addAttributes(bbattributes);
                    } catch (TskCoreException ex) {
                        Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
                    }
                    IngestManagerProxy.fireModuleDataEvent(new ModuleDataEvent(classname, BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG));
                }
            } catch (FileNotFoundException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
            } catch (SAXException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
            } catch (TikaException ex) {
                Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
            }
        }

        return ProcessResult.OK;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "COMPLETE"));

        //module specific cleanup due completion here
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

        //module specific initialization here
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

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

   /**
     * Process content hierarchy and return true if content is a file and is set as known
     */
    private class GetIsFileKnownVisitor extends ContentVisitor.Default<Boolean> {

        @Override
        protected Boolean defaultVisit(Content cntnt) {
            return false;
        }
        
        @Override
        public Boolean visit(org.sleuthkit.datamodel.File file) {
            return file.getKnown() == TskData.FileKnown.KNOWN;
        }
        
    }
}