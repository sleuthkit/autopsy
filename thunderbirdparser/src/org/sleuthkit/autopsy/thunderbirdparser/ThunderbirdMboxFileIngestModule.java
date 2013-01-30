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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstract.*;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.xml.sax.SAXException;
import org.apache.commons.lang.StringEscapeUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;


public class ThunderbirdMboxFileIngestModule implements IngestModuleAbstractFile {

    private static final Logger logger = Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName());
    private static ThunderbirdMboxFileIngestModule instance = null;
    private IngestServices services;
    private static int messageId = 0;
    private Case currentCase;
    private static final String MODULE_NAME = "Thunderbird Parser";
    private final String hashDBModuleName = "Hash Lookup";
    
    final public static String MODULE_VERSION = "1.0";
    
    private String args;
    
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
                services.getAbstractFileModuleResult(hashDBModuleName);

        if (abstractFile.accept(getIsFileKnown) == true) {
            return ProcessResult.OK; //file is known, stop processing it
        } else if (hashDBResult == IngestModuleAbstractFile.ProcessResult.ERROR) {
            return ProcessResult.ERROR;  //file has read error, stop processing it
        }
        
        if (abstractFile.isVirtual() ) {
            return ProcessResult.OK;
        }
        
        final FsContent fsContent = (FsContent) abstractFile;

        try {
            byte[] t = new byte[64];
            if(fsContent.getSize() > 64) {
                int byteRead = fsContent.read(t, 0, 64);
                if (byteRead > 0) {
                    isMbox = mbox.isValidMimeTypeMbox(t);
                }
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, null, ex);
        }


        if (isMbox) {
            logger.log(Level.INFO, "ThunderbirdMboxFileIngestModule: Parsing {0}", fsContent.getName());
            
            String mboxName = fsContent.getName();
            String msfName = mboxName + ".msf";
            //Long mboxId = fsContent.getId();
            String mboxPath = fsContent.getParentPath();
            Long msfId = 0L;
            currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tskCase = currentCase.getSleuthkitCase();
            
            
            try {
                ResultSet resultset = tskCase.runQuery("SELECT obj_id FROM tsk_files WHERE parent_path = '" + mboxPath + "' and name = '" + msfName + "'");
                if (! resultset.next()) {
                    logger.log(Level.WARNING, "Could not find msf file in mbox dir: " + mboxPath + " file: " + msfName);
                    tskCase.closeRunQuery(resultset);
                    return ProcessResult.OK;
                }
                else {
                    msfId = resultset.getLong(1);
                    tskCase.closeRunQuery(resultset);
                }

            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Could not find msf file in mbox dir: " + mboxPath + " file: " + msfName);
            }

            try {
                Content msfContent = tskCase.getContentById(msfId);
                if (msfContent != null) {
                    ContentUtils.writeToFile(msfContent, new File(currentCase.getTempDirectory() + File.separator + msfName));
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to obtain msf file for mbox parsing:" + msfName, ex);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to obtain msf file for mbox parsing:" + msfName, ex);
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
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_TO.getTypeID(), MODULE_NAME, to));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID(), MODULE_NAME, cc));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID(), MODULE_NAME, bcc));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_FROM.getTypeID(), MODULE_NAME, from));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID(), MODULE_NAME, content.replaceAll("\\<[^>]*>", "")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML.getTypeID(), MODULE_NAME, content));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_ID.getTypeID(), MODULE_NAME, StringEscapeUtils.escapeHtml(emailId)));
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_REPLY_ID.getTypeID(), MODULE_NAME, "",));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID(), MODULE_NAME, date));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID(), MODULE_NAME, date));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID(), MODULE_NAME, subject));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), MODULE_NAME, folderPath));
                    BlackboardArtifact bbart;
                    try {
                        bbart = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);
                        bbart.addAttributes(bbattributes);
                    } catch (TskCoreException ex) {
                        Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
                    }
                    services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG));
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

        //module specific cleanup due completion here
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getDescription() {
        return "This module detects and parses mbox Thunderbird files and populates email artifacts in the blackboard.";
    }
    
    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public String getArguments() {
        return args;
    }

    @Override
    public void setArguments(String args) {
        this.args = args;
    }
	

    @Override
    public void init(IngestModuleInit initContext) {
        logger.log(Level.INFO, "init()");
        services = IngestServices.getDefault();

        currentCase = Case.getCurrentCase();
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