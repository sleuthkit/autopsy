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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
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
        
        if (isMbox == false) {
            return ProcessResult.OK;
        }

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

    @Override
    public void complete() {
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