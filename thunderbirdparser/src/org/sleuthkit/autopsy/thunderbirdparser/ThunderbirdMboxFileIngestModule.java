/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2013 Basis Technology Corp.
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

import com.pff.PSTException;
import com.pff.PSTMessage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;
import org.xml.sax.SAXException;

/**
 * File-level ingest module that detects MBOX files based on signature. 
 * Understands Thunderbird folder layout to provide additional structure and metadata.
 */
public class ThunderbirdMboxFileIngestModule extends IngestModuleAbstractFile {

    private static final Logger logger = Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName());
    private static ThunderbirdMboxFileIngestModule instance = null;
    private IngestServices services;
    private static final String MODULE_NAME = "MBox Parser";
    private final String hashDBModuleName = "Hash Lookup";
    final public static String MODULE_VERSION = Version.getVersion();

    public static synchronized ThunderbirdMboxFileIngestModule getDefault() {
        if (instance == null) {
            instance = new ThunderbirdMboxFileIngestModule();
        }
        return instance;
    }

    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile>ingestContext, AbstractFile abstractFile) {
        
        // skip known
        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK; 
        }
        
        //skip unalloc
        if(abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return ProcessResult.OK;
        }

        //file has read error, stop processing it
        // @@@ I don't really like this
        // we don't know if Hash was run or if it had lookup errors 
        IngestModuleAbstractFile.ProcessResult hashDBResult =
                services.getAbstractFileModuleResult(hashDBModuleName);
        if (hashDBResult == IngestModuleAbstractFile.ProcessResult.ERROR) {
            return ProcessResult.ERROR;  
        }

        if (abstractFile.isVirtual()) {
            return ProcessResult.OK;
        }
        
        // check its signature
        boolean isMbox = false;
        try {
            byte[] t = new byte[64];
            if (abstractFile.getSize() > 64) {
                int byteRead = abstractFile.read(t, 0, 64);
                if (byteRead > 0) {
                    isMbox = ThunderbirdEmailParser.isValidMimeTypeMbox(t);
                }
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, null, ex);
        }
        
        if (isMbox) {
            return processMBox(abstractFile);
        }
        
        int extIndex = abstractFile.getName().lastIndexOf(".");
        String ext = (extIndex == -1 ? "" : abstractFile.getName().substring(extIndex));
        if (ext.equals(".pst")) {
            // TODO: better way to figure out if its a pst?
            return processPst(abstractFile);
        }
        
        return ProcessResult.OK;
    }
    
    private ProcessResult processMBox(AbstractFile abstractFile) {
        logger.log(Level.INFO, "ThunderbirdMboxFileIngestModule: Parsing {0}", abstractFile.getName());

        String mboxFileName = abstractFile.getName();
        String mboxParentDir = abstractFile.getParentPath();


        // Find the .msf file in the same folder
        // BC: Commented out because results are not being used Oct '13
        //Long msfId = 0L;
        //String msfName = mboxFileName + ".msf";
        //SleuthkitCase tskCase = currentCase.getSleuthkitCase();
        // @@@ We shouldn't bail out here if we dont' find it...
//        try {
//            // @@@ Replace this with a call to FileManager.findFiles()
//            ResultSet resultset = tskCase.runQuery("SELECT obj_id FROM tsk_files WHERE parent_path = '" + mboxParentDir + "' and name = '" + msfName + "'");
//            if (!resultset.next()) {
//                logger.log(Level.WARNING, "Could not find msf file in mbox dir: " + mboxParentDir + " file: " + msfName);
//                tskCase.closeRunQuery(resultset);
//                return ProcessResult.OK;
//            } else {
//                msfId = resultset.getLong(1);
//                tskCase.closeRunQuery(resultset);
//            }
//
//        } catch (SQLException ex) {
//            logger.log(Level.WARNING, "Could not find msf file in mbox dir: " + mboxParentDir + " file: " + msfName);
//        }
//
//        try {
//            Content msfContent = tskCase.getContentById(msfId);
//            if (msfContent != null) {
//                ContentUtils.writeToFile(msfContent, new File(currentCase.getTempDirectory() + File.separator + msfName));
//            }
//        } catch (IOException ex) {
//            logger.log(Level.WARNING, "Unable to obtain msf file for mbox parsing:" + msfName, ex);
//        } catch (TskCoreException ex) {
//            logger.log(Level.WARNING, "Unable to obtain msf file for mbox parsing:" + msfName, ex);
//        }
        
        
        // use the local path to determine the e-mail folder structure
        String emailFolder = "";
        // email folder is everything after "Mail" or ImapMail
        if (mboxParentDir.contains("/Mail/")) {
            emailFolder = mboxParentDir.substring(mboxParentDir.indexOf("/Mail/") + 5);
        } 
        else if (mboxParentDir.contains("/ImapMail/")) {
            emailFolder = mboxParentDir.substring(mboxParentDir.indexOf("/ImapMail/") + 9);    
        } 
        emailFolder = emailFolder + mboxFileName;
        emailFolder = emailFolder.replaceAll(".sbd", "");
        
        boolean errorsFound = false;
        try {
            ReadContentInputStream contentStream = new ReadContentInputStream(abstractFile);
            ThunderbirdEmailParser mbox = new ThunderbirdEmailParser();
            mbox.parse(contentStream);
            
            HashMap<String, Map<String, String>>emailMap = mbox.getAllEmails();
            for (Entry<String, Map<String, String>> entry : emailMap.entrySet()) {
                /* @@@ I'd rather this code be cleaned up a bit so that we check if the value is 
                 * set and then directly add it to the attribute.  otherwise, we end up with a bunch
                 * of "" attribute values. 
                 */
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                String emailId = ((entry.getKey() != null) ? entry.getKey() : "Not Available");
                Map<String, String>propertyMap = entry.getValue();
                String content = ((propertyMap.get("content") != null) ? propertyMap.get("content") : "");
                String from = ((propertyMap.get(Metadata.AUTHOR) != null) ? propertyMap.get(Metadata.AUTHOR) : "");
                String to = ((propertyMap.get(Metadata.MESSAGE_TO) != null) ? propertyMap.get(Metadata.MESSAGE_TO) : "");
                String stringDate = ((propertyMap.get("date") != null) ? propertyMap.get("date") : "");
                Long date = 0L;
                if (stringDate.equals("") == false) {
                    date = mbox.getDateCreated(stringDate);
                }
                String subject = ((propertyMap.get(Metadata.SUBJECT) != null) ? propertyMap.get(Metadata.SUBJECT) : "");
                String cc = ((propertyMap.get(Metadata.MESSAGE_CC) != null) ? propertyMap.get(Metadata.MESSAGE_CC) : "");
                String bcc = ((propertyMap.get(Metadata.MESSAGE_BCC) != null) ? propertyMap.get(Metadata.MESSAGE_BCC) : "");
        
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
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), MODULE_NAME, emailFolder));
                BlackboardArtifact bbart;
                try {
                    bbart = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);
                    bbart.addAttributes(bbattributes);
                    services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG));
                } catch (TskCoreException ex) {
                    Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
                    errorsFound = true;
                }
            }
        } 
        catch (FileNotFoundException ex) {
            Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
            errorsFound = true;
        } 
        catch (IOException ex) {
            Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
            errorsFound = true;
        } 
        catch (SAXException | TikaException ex) {
            Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName()).log(Level.WARNING, null, ex);
            errorsFound = true;
        }
        if (errorsFound) {
            // @@@ RECORD THEM...
            return ProcessResult.ERROR;
        }

        return ProcessResult.OK;
    }

    /**
     * Processes a pst/ost data file and extracts and adds email artifacts.
     * 
     * @param abstractFile The pst/ost data file to process.
     * @return 
     */
    private ProcessResult processPst(AbstractFile abstractFile) {
        String fileName = getTempPath() + File.separator + abstractFile.getName()
                + "-" + String.valueOf(abstractFile.getId());
        File file = new File(fileName);
        
        if (abstractFile.getSize() >= services.getFreeDiskSpace()) {
            logger.log(Level.WARNING, "Not enough disk space to write file to disk.");
            // TODO isn't there a skipped? shouldn't there be?
            return ProcessResult.OK;
        }
        try {
            ContentUtils.writeToFile(abstractFile, file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing pst file to disk.", ex);
            return ProcessResult.OK;
        }
        
        PstParser parser = new PstParser();
        if (parser.parse(file) == false) {
            // TODO Add error message somehow?
            logger.log(Level.INFO, "PSTParser failed to parse " + abstractFile.getName());
            return ProcessResult.ERROR;
        }
        
        for (Entry<PSTMessage, String> emailInfo : parser.getResults().entrySet()) {
            addPstArtifact(abstractFile, emailInfo.getKey(), emailInfo.getValue());
        }
        
        if (parser.getResults().isEmpty() == false) {
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG));
        }
        
        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: " + file.getName());
        }

        return ProcessResult.OK;
    }
    
    /**
     * Add an artifact to the AbstractFile representing the email message
     * retrieved from an outlook data file.
     * 
     * @param abstractFile The outlook data file.
     * @param email The email message.
     * @param localPath The path to the email message within the data files directory structure.
     * @return true if the artifact was created and added successfully.
     */
    private boolean addPstArtifact(AbstractFile abstractFile, PSTMessage email, String localPath) {
        List<BlackboardAttribute> bbattributes = new ArrayList<>();

        String to = email.getDisplayTo();
        String cc = email.getDisplayCC();
        String bcc = email.getDisplayBCC();
        String from = getPstFromAttr(email.getSenderName(), email.getSenderEmailAddress());
        Date date = email.getMessageDeliveryTime();
        long dateL = ((date == null) ? -1 : date.getTime() / 1000);
        String body = email.getBody();
        String bodyHTML = email.getBodyHTML();
        String rtf = "";
        try {
             rtf = email.getRTFBody();
        } catch (PSTException | IOException ex) {
            logger.log(Level.INFO, "Failed to get RTF content from pst email.");
        }
        String subject = email.getSubject();
        
        if (to.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_TO.getTypeID(), MODULE_NAME, to));
        }
        if (cc.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID(), MODULE_NAME, cc));
        }
        if (bcc.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID(), MODULE_NAME, bcc));
        }
        if (from.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_FROM.getTypeID(), MODULE_NAME, from));
        }
        if (dateL > 0) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID(), MODULE_NAME, dateL));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID(), MODULE_NAME, dateL));
        }
        if (body.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID(), MODULE_NAME, body));
        }
        if (bodyHTML.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML.getTypeID(), MODULE_NAME, bodyHTML));
        }
        if (rtf.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_RTF.getTypeID(), MODULE_NAME, rtf));
        }
        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_ID.getTypeID(), MODULE_NAME, email.getDescriptorNodeId()));
        if (subject.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID(), MODULE_NAME, subject));
        }
        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), MODULE_NAME, localPath));
        
        try {
            BlackboardArtifact bbart = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);
            bbart.addAttributes(bbattributes);
            return true;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, null, ex);
            return false;
        }
    }
    
    /**
     * Helper to format the "From" attribute nicely.
     * @param name Sender's Name
     * @param addr Sender's Email address
     * @return 
     */
    private String getPstFromAttr(String name, String addr) {
        if (name.isEmpty() && addr.isEmpty()) {
            return "";
        } else if (name.isEmpty()) {
            return addr;
        } else if (addr.isEmpty()) {
            return name;
        } else {
            return name + ": " + addr;
        }
     }
    
    /**
     * Get a path to a temporary folder.
     * @return 
     */
    private static String getTempPath() {
        String tmpDir = Case.getCurrentCase().getTempDirectory() + File.separator
                + "EmailParser";
        File dir = new File(tmpDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return tmpDir;
    }
    
    
    @Override
    public void complete() {
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getDescription() {
        return "This module detects and parses mbox and pst/ost files and populates email artifacts in the blackboard.";
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }


    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}