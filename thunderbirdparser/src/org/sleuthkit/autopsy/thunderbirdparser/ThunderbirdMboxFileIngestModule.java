/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
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
    private static final String MODULE_NAME = NbBundle.getMessage(ThunderbirdMboxFileIngestModule.class,
                                                                  "ThunderbirdMboxFileIngestModule.moduleName");
    private final String hashDBModuleName = NbBundle.getMessage(ThunderbirdMboxFileIngestModule.class,
                                                                "ThunderbirdMboxFileIngestModule.hashDbModuleName");
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
                    isMbox = MboxParser.isValidMimeTypeMbox(t);
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
            IngestMessage msg = IngestMessage.createErrorMessage(messageId++, this, getName(),
                                                                 NbBundle.getMessage(this.getClass(),
                                                                                     "ThunderbirdMboxFileIngestModule.processPst.errMsg.outOfDiskSpace",
                                                                                     abstractFile.getName()));
            services.postMessage(msg);
            return ProcessResult.OK;
        }
        
        try {
            ContentUtils.writeToFile(abstractFile, file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing pst file to disk.", ex);
            return ProcessResult.OK;
        }
        
        PstParser parser = new PstParser(services);
        PstParser.ParseResult result = parser.parse(file);
        
        if (result == PstParser.ParseResult.OK) {
            // parse success: Process email and add artifacts
            processEmails(parser.getResults(), abstractFile, ingestContext);
        } else if (result == PstParser.ParseResult.ENCRYPT) {
            // encrypted pst: Add encrypted file artifact
            try {
                BlackboardArtifact generalInfo = abstractFile.getGenInfoArtifact();
                generalInfo.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID(),
                        MODULE_NAME,
                        NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.encryptionFileLevel")));
            } catch (TskCoreException ex) {
                logger.log(Level.INFO, "Failed to add encryption attribute to file: {0}", abstractFile.getName());
            }
        } else {
            // parsing error: log message
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processPst.errProcFile.msg",
                                        abstractFile.getName()),
                    NbBundle.getMessage(this.getClass(),
                                        "ThunderbirdMboxFileIngestModule.processPst.errProcFile.details"));
            logger.log(Level.INFO, "PSTParser failed to parse {0}", abstractFile.getName());
            return ProcessResult.ERROR;
        }
        
        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: {0}", file.getName());
        }
        
        String errors = parser.getErrors();
        if (errors.isEmpty() == false) {
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processPst.errProcFile.msg2",
                                        abstractFile.getName()), errors);
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
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processMBox.errProcFile.msg",
                                        abstractFile.getName()),
                    NbBundle.getMessage(this.getClass(),
                                        "ThunderbirdMboxFileIngestModule.processMBox.errProfFile.details"));
            return ProcessResult.OK;
        }
        
        try {
            ContentUtils.writeToFile(abstractFile, file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing mbox file to disk.", ex);
            return ProcessResult.OK;
        }
        
        MboxParser parser = new MboxParser(services, emailFolder);
        List<EmailMessage> emails = parser.parse(file);
        
        processEmails(emails, abstractFile, ingestContext);
        
        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: " + file.getName());
        }
        
        String errors = parser.getErrors();
        if (errors.isEmpty() == false) {
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processMBox.errProcFile.msg2",
                                        abstractFile.getName()), errors);
        }
        
        return ProcessResult.OK;
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
        return NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.getDesc.text");
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }


    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
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
                postErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.handleAttch.errMsg",
                                            abstractFile.getName()),
                        NbBundle.getMessage(this.getClass(),
                                            "ThunderbirdMboxFileIngestModule.handleAttch.errMsg.details", filename));
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
        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MSG_ID.getTypeID(), MODULE_NAME, ((id < 0L) ? NbBundle
                .getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.notAvail") : String.valueOf(id))));
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
    
    void postErrorMessage(String subj, String details) {
        IngestMessage ingestMessage = IngestMessage.createErrorMessage(messageId++, this, subj, details);
        services.postMessage(ingestMessage);
    }
    
    IngestServices getServices() {
        return services;
    }
}