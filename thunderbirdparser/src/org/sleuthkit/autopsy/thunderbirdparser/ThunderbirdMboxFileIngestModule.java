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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Relationship;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskException;

/**
 * File-level ingest module that detects MBOX files based on signature.
 * Understands Thunderbird folder layout to provide additional structure and
 * metadata.
 */
public final class ThunderbirdMboxFileIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private FileManager fileManager;
    private IngestJobContext context;
    private Blackboard blackboard;
    private CommunicationsManager communicationsManager;

    ThunderbirdMboxFileIngestModule() {
    }

    @Override
    @Messages({"ThunderbirdMboxFileIngestModule.noOpenCase.errMsg=Exception while getting open case.",
        "ThunderbirdMboxFileIngestModule.noCommsManager.errMsg=Exception while getting communications manager."})
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            fileManager = currentCase.getServices().getFileManager();
            blackboard = currentCase.getSleuthkitCase().getBlackboard();
            communicationsManager = sleuthkitCase.getCommunicationsManager();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            throw new IngestModuleException(Bundle.ThunderbirdMboxFileIngestModule_noOpenCase_errMsg(), ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while getting communications manager.", ex);
            throw new IngestModuleException(Bundle.ThunderbirdMboxFileIngestModule_noCommsManager_errMsg(), ex);
        }
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {

        // skip known
        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        //skip unalloc
        if ((abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS))
            || (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK))) {
            return ProcessResult.OK;
        }

        if ((abstractFile.isFile() == false)) {
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
            return processMBox(abstractFile);
        }

        if (PstParser.isPstFile(abstractFile)) {
            return processPst(abstractFile);
        }

        return ProcessResult.OK;
    }

    /**
     * Processes a pst/ost data file and extracts and adds email artifacts.
     *
     * @param abstractFile The pst/ost data file to process.
     *
     * @return
     */
    @Messages({"ThunderbirdMboxFileIngestModule.processPst.indexError.message=Failed to index encryption detected artifact for keyword search."})
    private ProcessResult processPst(AbstractFile abstractFile) {
        String fileName;
        try {
            fileName = getTempPath() + File.separator + abstractFile.getName()
                       + "-" + String.valueOf(abstractFile.getId());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        File file = new File(fileName);

        long freeSpace = services.getFreeDiskSpace();
        if ((freeSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN) && (abstractFile.getSize() >= freeSpace)) {
            logger.log(Level.WARNING, "Not enough disk space to write file to disk."); //NON-NLS
            IngestMessage msg = IngestMessage.createErrorMessage(EmailParserModuleFactory.getModuleName(), EmailParserModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "ThunderbirdMboxFileIngestModule.processPst.errMsg.outOfDiskSpace",
                            abstractFile.getName()));
            services.postMessage(msg);
            return ProcessResult.OK;
        }

        try {
            ContentUtils.writeToFile(abstractFile, file, context::fileIngestIsCancelled);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing pst file to disk.", ex); //NON-NLS
            return ProcessResult.OK;
        }

        PstParser parser = new PstParser(services);
        PstParser.ParseResult result = parser.parse(file, abstractFile.getId());

        switch (result) {
            case OK:
                try {
                    // parse success: Process email and add artifacts
                    processEmails(parser.getResults(), abstractFile);
                } catch (NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
                    return ProcessResult.ERROR;
                }
                break;
            case ENCRYPT:
                // encrypted pst: Add encrypted file artifact
                try {
                    BlackboardArtifact artifact = abstractFile.newArtifact(TSK_ENCRYPTION_DETECTED);
                    artifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, EmailParserModuleFactory.getModuleName(), NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.encryptionFileLevel")));

                    try {
                        /* Post the encryption artifact, which will index it and
                         * send out a notification event to the
                         */
                        blackboard.postArtifact(artifact, EmailParserModuleFactory.getModuleName());
                    } catch (Blackboard.BlackboardException ex) {
                        MessageNotifyUtil.Notify.error(Bundle.ThunderbirdMboxFileIngestModule_processPst_indexError_message(), artifact.getDisplayName());
                        logger.log(Level.SEVERE, "Unable to post blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.INFO, "Failed to add encryption attribute to file: {0}", abstractFile.getName()); //NON-NLS
                }
                break;
            default:
                // parsing error: log message
                postErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processPst.errProcFile.msg",
                                abstractFile.getName()),
                        NbBundle.getMessage(this.getClass(),
                                "ThunderbirdMboxFileIngestModule.processPst.errProcFile.details"));
                logger.log(Level.INFO, "PSTParser failed to parse {0}", abstractFile.getName()); //NON-NLS
                return ProcessResult.ERROR;
        }

        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: {0}", file.getName()); //NON-NLS
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
     *
     * @param abstractFile
     *
     * @return
     */
    private ProcessResult processMBox(AbstractFile abstractFile) {
        String mboxFileName = abstractFile.getName();
        String mboxParentDir = abstractFile.getParentPath();
        // use the local path to determine the e-mail folder structure
        String emailFolder = "";
        // email folder is everything after "Mail" or ImapMail
        if (mboxParentDir.contains("/Mail/")) { //NON-NLS
            emailFolder = mboxParentDir.substring(mboxParentDir.indexOf("/Mail/") + 5); //NON-NLS
        } else if (mboxParentDir.contains("/ImapMail/")) { //NON-NLS
            emailFolder = mboxParentDir.substring(mboxParentDir.indexOf("/ImapMail/") + 9); //NON-NLS
        }
        emailFolder += mboxFileName;
        emailFolder = emailFolder.replaceAll(".sbd", ""); //NON-NLS

        String fileName;
        try {
            fileName = getTempPath() + File.separator + abstractFile.getName()
                       + "-" + String.valueOf(abstractFile.getId());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        File file = new File(fileName);

        long freeSpace = services.getFreeDiskSpace();
        if ((freeSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN) && (abstractFile.getSize() >= freeSpace)) {
            logger.log(Level.WARNING, "Not enough disk space to write file to disk."); //NON-NLS
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processMBox.errProcFile.msg",
                            abstractFile.getName()),
                    NbBundle.getMessage(this.getClass(),
                            "ThunderbirdMboxFileIngestModule.processMBox.errProfFile.details"));
            return ProcessResult.OK;
        }

        try {
            ContentUtils.writeToFile(abstractFile, file, context::fileIngestIsCancelled);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing mbox file to disk.", ex); //NON-NLS
            return ProcessResult.OK;
        }

        MboxParser parser = new MboxParser(services, emailFolder);
        List<EmailMessage> emails = parser.parse(file, abstractFile.getId());
        try {
            processEmails(emails, abstractFile);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }

        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: {0}", file.getName()); //NON-NLS
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
     *
     * @throws NoCurrentCaseException if there is no open case.
     * @return the temporary folder
     */
    static String getTempPath() throws NoCurrentCaseException {
        String tmpDir = Case.getCurrentCaseThrows().getTempDirectory() + File.separator
                        + "EmailParser"; //NON-NLS
        File dir = new File(tmpDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return tmpDir;
    }

    /**
     * Get a module output folder.
     *
     * @throws NoCurrentCaseException if there is no open case.
     * @return the module output folder
     */
    static String getModuleOutputPath() throws NoCurrentCaseException {
        String outDir = Case.getCurrentCaseThrows().getModuleDirectory() + File.separator
                        + EmailParserModuleFactory.getModuleName();
        File dir = new File(outDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return outDir;
    }

    /**
     * Get a relative path of a module output folder.
     *
     * @throws NoCurrentCaseException if there is no open case.
     * @return the relative path of the module output folder
     */
    static String getRelModuleOutputPath() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getModuleOutputDirectoryRelativePath() + File.separator
               + EmailParserModuleFactory.getModuleName();
    }

    /**
     * Take the extracted information in the email messages and add the
     * appropriate artifacts and derived files.
     *
     * @param emails
     * @param abstractFile
     *
     * @throws NoCurrentCaseException if there is no open case.
     */
    private void processEmails(List<EmailMessage> emails, AbstractFile abstractFile) throws NoCurrentCaseException {
        List<AbstractFile> derivedFiles = new ArrayList<>();

        for (EmailMessage email : emails) {
            BlackboardArtifact msgArtifact = addArtifact(email, abstractFile);

            if ((msgArtifact != null) && (email.hasAttachment())) {
                derivedFiles.addAll(handleAttachments(email.getAttachments(), abstractFile, msgArtifact));
            }
        }

        if (isNotEmpty(derivedFiles)) {
            derivedFiles.forEach(derived -> services.fireModuleContentEvent(new ModuleContentEvent(derived)));
        }
        context.addFilesToJob(derivedFiles);
    }

    /**
     * Add the given attachments as derived files and reschedule them for
     * ingest.
     *
     * @param attachments
     * @param abstractFile
     * @param messageArtifact
     *
     * @return List of attachments
     */
    private List<AbstractFile> handleAttachments(List<EmailMessage.Attachment> attachments, AbstractFile abstractFile, BlackboardArtifact messageArtifact) {
        List<AbstractFile> files = new ArrayList<>();
        for (EmailMessage.Attachment attach : attachments) {
            String filename = attach.getName();
            long crTime = attach.getCrTime();
            long mTime = attach.getmTime();
            long aTime = attach.getaTime();
            long cTime = attach.getcTime();
            String relPath = attach.getLocalPath();
            long size = attach.getSize();
            TskData.EncodingType encodingType = attach.getEncodingType();

            try {
                DerivedFile df = fileManager.addDerivedFile(filename, relPath,
                        size, cTime, crTime, aTime, mTime, true, messageArtifact, "",
                        EmailParserModuleFactory.getModuleName(), EmailParserModuleFactory.getModuleVersion(), "", encodingType);
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
     * Finds and returns a set of unique email addresses found in the input
     * string
     *
     * @param input - input string, like the To/CC line from an email header
     *
     * @return Set<String>: set of email addresses found in the input string
     */
    private Set<String> findEmailAddresess(String input) {
        Pattern p = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(input);
        Set<String> emailAddresses = new HashSet<>();
        while (m.find()) {
            emailAddresses.add(m.group());
        }
        return emailAddresses;
    }

    /**
     * Add a blackboard artifact for the given email message.
     *
     * @param email
     * @param abstractFile
     *
     * @throws NoCurrentCaseException if there is no open case.
     */
    @Messages({"ThunderbirdMboxFileIngestModule.addArtifact.indexError.message=Failed to index email message detected artifact for keyword search."})
    private BlackboardArtifact addArtifact(EmailMessage email, AbstractFile abstractFile) throws NoCurrentCaseException {
        BlackboardArtifact bbart = null;
        List<BlackboardAttribute> bbattributes = new ArrayList<>();
        String to = email.getRecipients();
        String cc = email.getCc();
        String bcc = email.getBcc();
        String from = email.getSender();
        long dateL = email.getSentDate();
        long id = email.getId();

        List<String> senderAddressList = new ArrayList<>();

        senderAddressList.addAll(findEmailAddresess(from));

        AccountFileInstance senderAccountInstance = null;

        if (senderAddressList.size() == 1) {
            String senderAddress = senderAddressList.get(0);
            try {
                senderAccountInstance = communicationsManager.
                        createAccountFileInstance(Account.Type.EMAIL, senderAddress, EmailParserModuleFactory.getModuleName(), abstractFile);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to create account for email address  " + senderAddress, ex); //NON-NLS
            }
        } else {
            logger.log(Level.WARNING, "Failed to find sender address, from  = {0}", from); //NON-NLS
        }

        //TODO: should this be a set?
        List<String> recipientAddresses = new ArrayList<>();
        recipientAddresses.addAll(findEmailAddresess(to));
        recipientAddresses.addAll(findEmailAddresess(cc));
        recipientAddresses.addAll(findEmailAddresess(bcc));

        List<AccountFileInstance> recipientAccountInstances = new ArrayList<>();
        recipientAddresses.forEach(addr -> {
            try {
                AccountFileInstance recipientAccountInstance = communicationsManager
                        .createAccountFileInstance(Account.Type.EMAIL, addr,
                                EmailParserModuleFactory.getModuleName(), abstractFile);
                recipientAccountInstances.add(recipientAccountInstance);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to create account for email address  " + addr, ex); //NON-NLS
            }
        });

        addEmailAttribute(email.getHeaders(), ATTRIBUTE_TYPE.TSK_HEADERS, bbattributes);
        addEmailAttribute(from, ATTRIBUTE_TYPE.TSK_EMAIL_FROM, bbattributes);
        addEmailAttribute(to, ATTRIBUTE_TYPE.TSK_EMAIL_TO, bbattributes);
        addEmailAttribute(cc, ATTRIBUTE_TYPE.TSK_EMAIL_CC, bbattributes);
        addEmailAttribute(email.getSubject(), ATTRIBUTE_TYPE.TSK_SUBJECT, bbattributes);
        addEmailAttribute(dateL, ATTRIBUTE_TYPE.TSK_DATETIME_RCVD, bbattributes);
        addEmailAttribute(dateL, ATTRIBUTE_TYPE.TSK_DATETIME_SENT, bbattributes);
        addEmailAttribute(email.getTextBody(), ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN, bbattributes);
        addEmailAttribute(email.getHtmlBody(), ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML, bbattributes);
        addEmailAttribute(email.getRtfBody(), ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_RTF, bbattributes);

        addEmailAttribute(((id < 0L) ? NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.notAvail") : String.valueOf(id)),
                ATTRIBUTE_TYPE.TSK_MSG_ID, bbattributes);

        addEmailAttribute(StringUtils.defaultIfEmpty(email.getLocalPath(), "/foo/bar"), //TODO: really /foo/bar?
                ATTRIBUTE_TYPE.TSK_PATH, bbattributes);

        try {
            bbart = abstractFile.newArtifact(TSK_EMAIL_MSG);
            bbart.addAttributes(bbattributes);

            // Add account relationships
            communicationsManager.addRelationships(senderAccountInstance, recipientAccountInstances, bbart, Relationship.Type.MESSAGE, dateL);

            try {
                /*
                 * Post the artifact to the blackboard which will index it for
                 * keyword search and send a notification for the UI.
                 */
                blackboard.postArtifact(bbart, EmailParserModuleFactory.getModuleName());
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bbart.getArtifactID(), ex); //NON-NLS
                MessageNotifyUtil.Notify.error(Bundle.ThunderbirdMboxFileIngestModule_addArtifact_indexError_message(), bbart.getDisplayName());
            }
        } catch (TskCoreException | TskDataException ex) {
            logger.log(Level.WARNING, null, ex);
        }

        return bbart;
    }

    private void addEmailAttribute(String stringVal, ATTRIBUTE_TYPE attrType, Collection<BlackboardAttribute> bbattributes) {
        if (StringUtils.isNotBlank(stringVal)) {
            bbattributes.add(new BlackboardAttribute(attrType, EmailParserModuleFactory.getModuleName(), stringVal));
        }
    }

    private void addEmailAttribute(long longVal, ATTRIBUTE_TYPE attrType, Collection<BlackboardAttribute> bbattributes) {
        if (longVal > 0) {
            bbattributes.add(new BlackboardAttribute(attrType, EmailParserModuleFactory.getModuleName(), longVal));
        }
    }

    void postErrorMessage(String subj, String details) {
        IngestMessage ingestMessage = IngestMessage.createErrorMessage(EmailParserModuleFactory.getModuleVersion(), subj, details);
        services.postMessage(ingestMessage);
    }

    @Override
    public void shutDown() {
        // nothing to shut down
    }
}
