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

import com.pff.PSTAttachment;
import com.pff.PSTException;
import com.pff.PSTMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

/**
 * File-level ingest module that detects MBOX files based on signature. 
 * Understands Thunderbird folder layout to provide additional structure and metadata.
 */
public class ThunderbirdMboxFileIngestModule extends IngestModuleAbstractFile {

    private static final Logger logger = Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName());
    private static ThunderbirdMboxFileIngestModule instance = null;
    private IngestServices services;
    private static final String MODULE_NAME = "Email Parser";
    private final String hashDBModuleName = "Hash Lookup";
    final public static String MODULE_VERSION = Version.getVersion();
    private int messageId = 0;
    private FileManager fileManager;

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
            return processMBox(abstractFile, ingestContext);
        }
        
        int extIndex = abstractFile.getName().lastIndexOf(".");
        String ext = (extIndex == -1 ? "" : abstractFile.getName().substring(extIndex));
        if (PstParser.isPstFile(abstractFile)) {
            return processPst(ingestContext, abstractFile);
        }
        
        return ProcessResult.OK;
    }

    /**
     * Processes a pst/ost data file and extracts and adds email artifacts.
     * 
     * @param abstractFile The pst/ost data file to process.
     * @return 
     */
    private ProcessResult processPst(PipelineContext<IngestModuleAbstractFile>ingestContext, AbstractFile abstractFile) {
        String fileName = getTempPath() + File.separator + abstractFile.getName()
                + "-" + String.valueOf(abstractFile.getId());
        File file = new File(fileName);
        
        if (abstractFile.getSize() >= services.getFreeDiskSpace()) {
            logger.log(Level.WARNING, "Not enough disk space to write file to disk.");
            IngestMessage msg = IngestMessage.createErrorMessage(messageId++, this, getName(), "Out of disk space. Can't copy " + abstractFile.getName() + " to parse.");
            services.postMessage(msg);
            return ProcessResult.OK;
        }
        
        try {
            ContentUtils.writeToFile(abstractFile, file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing pst file to disk.", ex);
            return ProcessResult.OK;
        }
        
        PstParser parser = new PstParser();
        PstParser.ParseResult result = parser.parse(file);
        
        if (result == PstParser.ParseResult.OK) {
            // parse success: Process email and add artifacts
            processPstMessages(parser.getResults(), abstractFile, ingestContext);
        } else if (result == PstParser.ParseResult.ENCRYPT) {
            // encrypted pst: Add encrypted file artifact
            try {
                BlackboardArtifact generalInfo = abstractFile.getGenInfoArtifact();
                generalInfo.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID(),
                        MODULE_NAME, "File-level Encryption"));
            } catch (TskCoreException ex) {
                logger.log(Level.INFO, "Failed to add encryption attribute to file: " + abstractFile.getName());
            }
        } else {
            // parsing error: log message
            IngestMessage msg = IngestMessage.createErrorMessage(messageId++, this, getName(), 
                    "Failed to parse outlook data file: " + abstractFile.getName() + ".<br/>" + 
                    "Only files from Outlook 2003 and later are supported.");
            services.postMessage(msg);
            logger.log(Level.INFO, "PSTParser failed to parse " + abstractFile.getName());
            return ProcessResult.ERROR;
        }
        
        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: " + file.getName());
        }

        return ProcessResult.OK;
    }
    
        /**
     * Parse and extract email messages and attachments from an MBox file.
     * @param abstractFile
     * @param ingestContext
     * @return 
     */
    private ProcessResult processMBox(AbstractFile abstractFile, PipelineContext<IngestModuleAbstractFile>ingestContext) {
        String mboxFileName = abstractFile.getName();
        String mboxParentDir = abstractFile.getParentPath();
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
        
        String fileName = getTempPath() + File.separator + abstractFile.getName()
                + "-" + String.valueOf(abstractFile.getId());
        File file = new File(fileName);
        
        if (abstractFile.getSize() >= services.getFreeDiskSpace()) {
            logger.log(Level.WARNING, "Not enough disk space to write file to disk.");
            IngestMessage msg = IngestMessage.createErrorMessage(messageId++, this, getName(), "Out of disk space. Can't copy " + abstractFile.getName() + " to parse.");
            services.postMessage(msg);
            return ProcessResult.OK;
        }
        
        try {
            ContentUtils.writeToFile(abstractFile, file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing mbox file to disk.", ex);
            return ProcessResult.OK;
        }
        
        MboxParser parser = new MboxParser(emailFolder);
        List<EmailMessage> emails = parser.parse(file);
        
        processEmails(emails, abstractFile, ingestContext);
        
        return ProcessResult.OK;
    }
    
    /**
     * Process the results of the PstParser. Handles adding email artifacts and
     * extracting and adding attachments as derived files.
     * 
     * @param parser
     * @param abstractFile
     * @param ingestContext 
     */
    private void processPstMessages(Map<PSTMessage, String> parseResults, AbstractFile abstractFile, PipelineContext<IngestModuleAbstractFile> ingestContext) {
        boolean added = false;
        List<AbstractFile> derivedFiles = new ArrayList<>();
        for (Entry<PSTMessage, String> emailInfo : parseResults.entrySet()) {
            PSTMessage email = emailInfo.getKey();
            added = addPstArtifact(abstractFile, email, emailInfo.getValue());
            if (email.hasAttachments()) {
                handlePstAttachments(abstractFile, email, derivedFiles);
            }
        }

        if (added) {
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG));
        }
        
        if (derivedFiles.isEmpty() == false) {
            services.fireModuleContentEvent(new ModuleContentEvent(abstractFile));
            for (AbstractFile derived : derivedFiles) {
                services.scheduleFile(derived, ingestContext);
            }
        }
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
     * Extracts attachments and creates derived files.
     * 
     * @param abstractFile
     * @param email
     * @param toPopulate derived files should be added to this list when created.
     */
    private void handlePstAttachments(AbstractFile abstractFile, PSTMessage email, List<AbstractFile> toPopulate) {
        int numberOfAttachments = email.getNumberOfAttachments();
        String outputDirPath = getModuleOutputPath() + File.separator;
        for (int x = 0; x < numberOfAttachments; x++) {
            try {
                PSTAttachment attach = email.getAttachment(x);
                long size = attach.getAttachSize();
                if (size >= services.getFreeDiskSpace()) {
                    continue;
                }
                // both long and short filenames can be used for attachments
                String filename = attach.getLongFilename();
                if (filename.isEmpty()) {
                    filename = attach.getFilename();
                }
                filename = email.getDescriptorNodeId() + "-" + filename;
                String outPath = outputDirPath + filename;
                extractPstAttachment(attach, outPath);
                
                long crTime = attach.getCreationTime().getTime() / 1000;
                long mTime = attach.getModificationTime().getTime() / 1000;
                String relPath = getRelModuleOutputPath() + File.separator + filename;
                
                DerivedFile df = fileManager.addDerivedFile(filename, relPath, 
                        size, 0L, crTime, 0L, mTime, true, abstractFile, "", 
                        MODULE_NAME, MODULE_VERSION, "");
                
                toPopulate.add(df);
            } catch (PSTException | IOException ex) {
                IngestMessage msg = IngestMessage.createErrorMessage(messageId++, this, getName(), "Failed to extract attachment from " + abstractFile.getName());
                services.postMessage(msg);
                logger.log(Level.WARNING, "Failed to extract attachment.");
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to create derived file under abstract file: " + abstractFile.getName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Extracts a PSTAttachment to the module output directory.
     * 
     * @param attach
     * @param outPath
     * @return
     * @throws IOException
     * @throws PSTException 
     */
    private void extractPstAttachment(PSTAttachment attach, String outPath) throws IOException, PSTException{
        InputStream attachmentStream = attach.getFileInputStream();
        FileOutputStream out = new FileOutputStream(outPath);
        // 8176 is the block size used internally and should give the best performance
        int bufferSize = 8176;
        byte[] buffer = new byte[bufferSize];
        int count = attachmentStream.read(buffer);
        while (count == bufferSize) {
            out.write(buffer);
            count = attachmentStream.read(buffer);
        }
        byte[] endBuffer = new byte[count];
        System.arraycopy(buffer, 0, endBuffer, 0, count);
        out.write(endBuffer);
        out.close();
        attachmentStream.close();
    }
    
    /**
     * Pretty-Print "From" field of an outlook email message.
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
    public static String getTempPath() {
        String tmpDir = Case.getCurrentCase().getTempDirectory() + File.separator
                + "EmailParser";
        File dir = new File(tmpDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return tmpDir;
    }
    
    public static String getModuleOutputPath() {
        String outDir = Case.getCurrentCase().getModulesOutputDirAbsPath() + File.separator + 
                        MODULE_NAME;
        File dir = new File(outDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return outDir;
    }
    
    public static String getRelModuleOutputPath() {
        return Case.getModulesOutputDirRelPath() + File.separator + 
                MODULE_NAME;
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
        fileManager = Case.getCurrentCase().getServices().getFileManager();
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
    
    /**
     * Take the extracted information in the email messages and add the 
     * appropriate artifacts and derived files.
     * @param emails
     * @param abstractFile
     * @param ingestContext 
     */
    private void processEmails(List<EmailMessage> emails, AbstractFile abstractFile, PipelineContext<IngestModuleAbstractFile>ingestContext) {
        List<AbstractFile> derivedFiles = new ArrayList<>();
        for (EmailMessage email : emails) {
            if (email.hasAttachment()) {
                derivedFiles.addAll(handleAttachments(email.getAttachments(), abstractFile));
            }
            addArtifact(email, abstractFile);
        }
        
        if (derivedFiles.isEmpty() == false) {
            for (AbstractFile derived : derivedFiles) {
                services.fireModuleContentEvent(new ModuleContentEvent(abstractFile));
                services.scheduleFile(derived, ingestContext);
            }
        }
        services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG));
    }
    
    /**
     * Add the given attachments as derived files and reschedule them for ingest.
     * @param attachments
     * @param abstractFile
     * @return 
     */
    private List<AbstractFile> handleAttachments(List<Attachment> attachments, AbstractFile abstractFile) {
        List<AbstractFile> files = new ArrayList<>();
        for (Attachment attach : attachments) {
            String filename = attach.getName();
            long crTime = attach.getCrTime();
            long mTime = attach.getmTime();
            long aTime = attach.getaTime();
            long cTime = attach.getcTime();
            String relPath = attach.getLocalPath();
            long size = attach.getSize();

            try {
                DerivedFile df = fileManager.addDerivedFile(filename, relPath, 
                        size, cTime, crTime, aTime, mTime, true, abstractFile, "", 
                        MODULE_NAME, MODULE_VERSION, "");
                files.add(df);
            } catch (TskCoreException ex) {
                // JWTODO
                logger.log(Level.INFO, "", ex);
            }
        }
        return files;
    }
    
    /**
     * Add a blackboard artifact for the given email message.
     * @param email
     * @param abstractFile 
     */
    private void addArtifact(EmailMessage email, AbstractFile abstractFile) {
        List<BlackboardAttribute> bbattributes = new ArrayList<>();
        String to = email.getRecipients();
        String cc = email.getCc();
        String bcc = email.getBcc();
        String from = email.getSender();
        long dateL = email.getSentDate();
        String body = email.getTextBody();
        String bodyHTML = email.getHtmlBody();
        String rtf = email.getRtfBody();
        String subject = email.getSubject();
        long id = email.getId();
        String localPath = email.getLocalPath();
        
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
        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_ID.getTypeID(), MODULE_NAME, ((id < 0L) ? "Not available" : String.valueOf(id))));
        if (subject.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID(), MODULE_NAME, subject));
        }
        if (localPath.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), MODULE_NAME, localPath));
        } else {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), MODULE_NAME, "/foo/bar"));
        }
        
        try {
            BlackboardArtifact bbart;
            bbart = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);
            bbart.addAttributes(bbattributes);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, null, ex);
        }
    }
    
}