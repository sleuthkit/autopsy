/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.james.mime4j.MimeException;
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
import org.sleuthkit.autopsy.thunderbirdparser.EmailMessage.AttachedEmailMessage;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.Relationship;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments.FileAttachment;

/**
 * File-level ingest module that detects MBOX, PST, and vCard files based on
 * signature. Understands Thunderbird folder layout to provide additional
 * structure and metadata.
 */
public final class ThunderbirdMboxFileIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(ThunderbirdMboxFileIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private FileManager fileManager;
    private IngestJobContext context;
    private Blackboard blackboard;
    private CommunicationArtifactsHelper communicationArtifactsHelper;
    
    // A cache of custom attributes for the VcardParser unique to each ingest run, but consistent across threads.
    private static ConcurrentMap<String, BlackboardAttribute.Type> customAttributeCache = new ConcurrentHashMap<>();
    private static Object customAttributeCacheLock = new Object();
    
    private static final int MBOX_SIZE_TO_SPLIT = 1048576000;
    private Case currentCase;

    /**
     * Empty constructor.
     */
    ThunderbirdMboxFileIngestModule() {
    }

    @Override
    @Messages({"ThunderbirdMboxFileIngestModule.noOpenCase.errMsg=Exception while getting open case."})
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        
        synchronized(customAttributeCacheLock) {
            if (!customAttributeCache.isEmpty()) {
                customAttributeCache.clear();
            }
        }
        
        try {
            currentCase = Case.getCurrentCaseThrows();
            fileManager = Case.getCurrentCaseThrows().getServices().getFileManager();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            throw new IngestModuleException(Bundle.ThunderbirdMboxFileIngestModule_noOpenCase_errMsg(), ex);
        }
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {

        blackboard = currentCase.getSleuthkitCase().getBlackboard();

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
        boolean isEMLFile = false;

        try {
            byte[] t = new byte[64];
            if (abstractFile.getSize() > 64) {
                int byteRead = abstractFile.read(t, 0, 64);
                if (byteRead > 0) {
                    isMbox = MboxParser.isValidMimeTypeMbox(t, abstractFile);
                    isEMLFile = EMLParser.isEMLFile(abstractFile, t);
                }
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, null, ex);
        }

        boolean isPstFile = PstParser.isPstFile(abstractFile);
        boolean isVcardFile = VcardParser.isVcardFile(abstractFile);

        if (context.fileIngestIsCancelled()) {
            return ProcessResult.OK;
        }

        if (isMbox || isEMLFile || isPstFile || isVcardFile) {
            try {
                communicationArtifactsHelper = new CommunicationArtifactsHelper(currentCase.getSleuthkitCase(),
                        EmailParserModuleFactory.getModuleName(), abstractFile, Account.Type.EMAIL, context.getJobId());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to create CommunicationArtifactsHelper for file with object id = %d", abstractFile.getId()), ex);
                return ProcessResult.ERROR;
            }
        }

        if (isMbox) {
            return processMBox(abstractFile);
        }

        if (isEMLFile) {
            return processEMLFile(abstractFile);
        }

        if (isPstFile) {
            return processPst(abstractFile);
        }

        if (isVcardFile) {
            return processVcard(abstractFile);
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

        try (PstParser parser = new PstParser(services)) {
            try {
                ContentUtils.writeToFile(abstractFile, file, context::fileIngestIsCancelled);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed writing pst file to disk.", ex); //NON-NLS
                return ProcessResult.OK;
            }

            PstParser.ParseResult result = parser.open(file, abstractFile.getId());

            switch (result) {
                case OK:
                    Iterator<EmailMessage> pstMsgIterator = parser.getEmailMessageIterator();
                    if (pstMsgIterator != null) {
                        processEmails(parser.getPartialEmailMessages(), pstMsgIterator, abstractFile);
                        if (context.fileIngestIsCancelled()) {
                            return ProcessResult.OK;
                        }
                    } else {
                        // sometimes parser returns ParseResult=OK but there are no messages
                        postErrorMessage(
                                NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processPst.errProcFile.msg",
                                        abstractFile.getName()),
                                NbBundle.getMessage(this.getClass(),
                                        "ThunderbirdMboxFileIngestModule.processPst.errProcFile.details"));
                        logger.log(Level.INFO, "PSTParser failed to parse {0}", abstractFile.getName()); //NON-NLS
                        return ProcessResult.ERROR;
                    }
                    break;

                case ENCRYPT:
                    // encrypted pst: Add encrypted file artifact
                    try {

                    String encryptionFileLevel = NbBundle.getMessage(this.getClass(), 
                                        "ThunderbirdMboxFileIngestModule.encryptionFileLevel");
                    BlackboardArtifact artifact = abstractFile.newAnalysisResult(
                            BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, 
                            Score.SCORE_NOTABLE, null, null, encryptionFileLevel, Arrays.asList(
                                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, 
                                        EmailParserModuleFactory.getModuleName(), 
                                        encryptionFileLevel)
                            ))
                            .getAnalysisResult();

                    try {
                        // index the artifact for keyword search
                        blackboard.postArtifact(artifact, EmailParserModuleFactory.getModuleName(), context.getJobId());
                    } catch (Blackboard.BlackboardException ex) {
                        MessageNotifyUtil.Notify.error(Bundle.ThunderbirdMboxFileIngestModule_processPst_indexError_message(), artifact.getDisplayName());
                        logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
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
        } catch (Exception ex) {
            logger.log(Level.WARNING, String.format("Failed to close temp pst file %s", file.getAbsolutePath()));
        } finally {
            file.delete();
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

        if (abstractFile.getSize() < MBOX_SIZE_TO_SPLIT) {

            try {
                ContentUtils.writeToFile(abstractFile, file, context::fileIngestIsCancelled);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed writing mbox file to disk.", ex); //NON-NLS
                return ProcessResult.OK;
            }

            try {
                processMboxFile(file, abstractFile, emailFolder);
                if (context.fileIngestIsCancelled()) {
                    return ProcessResult.OK;
                }
            } finally {
                file.delete();
            }
        } else {

            List<Long> mboxSplitOffsets = new ArrayList<>();
            try {
                mboxSplitOffsets = findMboxSplitOffset(abstractFile, file);
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Failed finding split offsets for mbox file {0}.", fileName), ex); //NON-NLS
                return ProcessResult.OK;
            }

            long startingOffset = 0;
            for (Long mboxSplitOffset : mboxSplitOffsets) {
                File splitFile = new File(fileName + "-" + mboxSplitOffset);
                try {
                    ContentUtils.writeToFile(abstractFile, splitFile, context::fileIngestIsCancelled, startingOffset, mboxSplitOffset);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Failed writing split mbox file to disk.", ex); //NON-NLS
                    return ProcessResult.OK;
                }
                try {
                    processMboxFile(splitFile, abstractFile, emailFolder);
                    startingOffset = mboxSplitOffset;
                } finally {
                    splitFile.delete();
                }

                if (context.fileIngestIsCancelled()) {
                    return ProcessResult.OK;
                }
            }
        }

        return ProcessResult.OK;
    }

    private List<Long> findMboxSplitOffset(AbstractFile abstractFile, File file) throws IOException {

        List<Long> mboxSplitOffset = new ArrayList<>();

        byte[] buffer = new byte[7];
        ReadContentInputStream in = new ReadContentInputStream(abstractFile);
        in.skip(MBOX_SIZE_TO_SPLIT);
        int len = in.read(buffer);
        while (len != -1) {
            len = in.read(buffer);
            if (buffer[0] == 13 && buffer[1] == 10 && buffer[2] == 70 && buffer[3] == 114
                    && buffer[4] == 111 && buffer[5] == 109 && buffer[6] == 32) {
                mboxSplitOffset.add(in.getCurPosition() - 5);
                in.skip(MBOX_SIZE_TO_SPLIT);
            }
        }

        return mboxSplitOffset;

    }

    private void processMboxFile(File file, AbstractFile abstractFile, String emailFolder) {

        try (MboxParser emailIterator = MboxParser.getEmailIterator(emailFolder, file, abstractFile.getId())) {
            List<EmailMessage> emails = new ArrayList<>();
            if (emailIterator != null) {
                while (emailIterator.hasNext()) {
                    if (context.fileIngestIsCancelled()) {
                        return;
                    }
                    EmailMessage emailMessage = emailIterator.next();
                    if (emailMessage != null) {
                        emails.add(emailMessage);
                    }
                }

                String errors = emailIterator.getErrors();
                if (!errors.isEmpty()) {
                    postErrorMessage(
                            NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.processMBox.errProcFile.msg2",
                                    abstractFile.getName()), errors);
                }
            }
            processEmails(emails, MboxParser.getEmailIterator(emailFolder, file, abstractFile.getId()), abstractFile);
        } catch (Exception ex) {
            logger.log(Level.WARNING, String.format("Failed to close mbox temp file %s", file.getAbsolutePath()));
        }

    }

    /**
     * Parse and extract data from a vCard file.
     *
     * @param abstractFile The content to be processed.
     *
     * @return 'ERROR' whenever a NoCurrentCaseException is encountered;
     *         otherwise 'OK'.
     */
    @Messages({
        "# {0} - file name",
        "# {1} - file ID",
        "ThunderbirdMboxFileIngestModule.errorMessage.outOfDiskSpace=Out of disk space. Cannot copy '{0}' (id={1}) to parse."
    })
    private ProcessResult processVcard(AbstractFile abstractFile) {
        try {
            VcardParser parser = new VcardParser(currentCase, context, customAttributeCache);
            parser.parse(abstractFile);
        } catch (IOException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, String.format("Exception while parsing the file '%s' (id=%d).", abstractFile.getName(), abstractFile.getId()), ex); //NON-NLS
            return ProcessResult.OK;
        }
        return ProcessResult.OK;
    }

    private ProcessResult processEMLFile(AbstractFile abstractFile) {
        try {
            EmailMessage message = EMLParser.parse(abstractFile);

            if (message == null) {
                return ProcessResult.OK;
            }

            List<AbstractFile> derivedFiles = new ArrayList<>();

            AccountFileInstanceCache accountFileInstanceCache = new AccountFileInstanceCache(abstractFile, currentCase);
            createEmailArtifact(message, abstractFile, accountFileInstanceCache, derivedFiles);
            accountFileInstanceCache.clear();

            if (derivedFiles.isEmpty() == false) {
                for (AbstractFile derived : derivedFiles) {
                    services.fireModuleContentEvent(new ModuleContentEvent(derived));
                }
            }
            context.addFilesToJob(derivedFiles);

        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Error reading eml file %s", abstractFile.getName()), ex);
            return ProcessResult.ERROR;
        } catch (MimeException ex) {
            logger.log(Level.WARNING, String.format("Error reading eml file %s", abstractFile.getName()), ex);
            return ProcessResult.ERROR;
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
     *
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
     * @param partialEmailsForThreading
     * @param fullMessageIterator
     * @param abstractFile
     */
    private void processEmails(List<EmailMessage> partialEmailsForThreading, Iterator<EmailMessage> fullMessageIterator,
            AbstractFile abstractFile) {

        // Create cache for accounts
        AccountFileInstanceCache accountFileInstanceCache = new AccountFileInstanceCache(abstractFile, currentCase);

        // Putting try/catch around this to catch any exception and still allow
        // the creation of the artifacts to continue.
        try {
            EmailMessageThreader.threadMessages(partialEmailsForThreading);
        } catch (Exception ex) {
            logger.log(Level.WARNING, String.format("Exception thrown parsing emails from %s", abstractFile.getName()), ex);
        }

        List<AbstractFile> derivedFiles = new ArrayList<>();

        int msgCnt = 0;
        while (fullMessageIterator.hasNext()) {
            if (context.fileIngestIsCancelled()) {
                return;
            }

            EmailMessage current = fullMessageIterator.next();

            if (current == null) {
                continue;
            }

            if (partialEmailsForThreading.size() > msgCnt) {
                EmailMessage threaded = partialEmailsForThreading.get(msgCnt++);

                if (threaded.getMessageID().equals(current.getMessageID())
                        && threaded.getSubject().equals(current.getSubject())) {
                    current.setMessageThreadID(threaded.getMessageThreadID());
                }
            }
            createEmailArtifact(current, abstractFile, accountFileInstanceCache, derivedFiles);
        }

        if (derivedFiles.isEmpty() == false) {
            for (AbstractFile derived : derivedFiles) {
                if (context.fileIngestIsCancelled()) {
                    return;
                }
                services.fireModuleContentEvent(new ModuleContentEvent(derived));
            }
        }
        context.addFilesToJob(derivedFiles);
    }

    void createEmailArtifact(EmailMessage email, AbstractFile abstractFile, AccountFileInstanceCache accountFileInstanceCache, List<AbstractFile> derivedFiles) {
        BlackboardArtifact msgArtifact = addEmailArtifact(email, abstractFile, accountFileInstanceCache);

        if ((msgArtifact != null) && (email.hasAttachment())) {
            derivedFiles.addAll(handleAttachments(email.getAttachments(), abstractFile, msgArtifact));

            for (EmailMessage.Attachment attach : email.getAttachments()) {
                if (attach instanceof AttachedEmailMessage) {
                    createEmailArtifact(((AttachedEmailMessage) attach).getEmailMessage(), abstractFile, accountFileInstanceCache, derivedFiles);
                }
            }
        }
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
    @NbBundle.Messages({
        "ThunderbirdMboxFileIngestModule.handleAttch.addAttachmentsErrorMsg=Failed to add attachments to email message."
    })
    private List<AbstractFile> handleAttachments(List<EmailMessage.Attachment> attachments, AbstractFile abstractFile, BlackboardArtifact messageArtifact) {
        List<AbstractFile> files = new ArrayList<>();
        List<FileAttachment> fileAttachments = new ArrayList<>();
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
                        size, cTime, crTime, aTime, mTime, true, abstractFile, "",
                        EmailParserModuleFactory.getModuleName(), EmailParserModuleFactory.getModuleVersion(), "", encodingType);

                files.add(df);

                fileAttachments.add(new FileAttachment(df));
            } catch (TskCoreException ex) {
                postErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.handleAttch.errMsg",
                                abstractFile.getName()),
                        NbBundle.getMessage(this.getClass(),
                                "ThunderbirdMboxFileIngestModule.handleAttch.errMsg.details", filename));
                logger.log(Level.INFO, "", ex);
            }
        }

        try {
            communicationArtifactsHelper.addAttachments(messageArtifact, new MessageAttachments(fileAttachments, Collections.emptyList()));
        } catch (TskCoreException ex) {
            postErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.handleAttch.addAttachmentsErrorMsg"),
                    "");
            logger.log(Level.INFO, "Failed to add attachments to email message.", ex);
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
     * Add a blackboard artifact for the given e-mail message.
     *
     * @param email                    The e-mail message.
     * @param abstractFile             The associated file.
     * @param accountFileInstanceCache The current cache of account instances.
     *
     * @return The generated e-mail message artifact.
     */
    @Messages({"ThunderbirdMboxFileIngestModule.addArtifact.indexError.message=Failed to index email message detected artifact for keyword search."})
    private BlackboardArtifact addEmailArtifact(EmailMessage email, AbstractFile abstractFile, AccountFileInstanceCache accountFileInstanceCache) {
        BlackboardArtifact bbart = null;
        List<BlackboardAttribute> bbattributes = new ArrayList<>();
        String to = email.getRecipients();
        String cc = email.getCc();
        String bcc = email.getBcc();
        String from = email.getSender();
        long dateL = email.getSentDate();
        String headers = email.getHeaders();
        String body = email.getTextBody();
        String bodyHTML = email.getHtmlBody();
        String rtf = email.getRtfBody();
        String subject = email.getSubject();
        long id = email.getId();
        String localPath = email.getLocalPath();
        String threadID = email.getMessageThreadID();

        List<String> senderAddressList = new ArrayList<>();
        String senderAddress;
        senderAddressList.addAll(findEmailAddresess(from));

        if (context.fileIngestIsCancelled()) {
            return null;
        }

        AccountFileInstance senderAccountInstance = null;

        if (senderAddressList.size() == 1) {
            senderAddress = senderAddressList.get(0);
            try {
                senderAccountInstance = accountFileInstanceCache.getAccountInstance(senderAddress, context);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to create account for email address  " + senderAddress, ex); //NON-NLS
            }
        } else {
            logger.log(Level.WARNING, "Failed to find sender address, from  = {0}", from); //NON-NLS
        }

        if (context.fileIngestIsCancelled()) {
            return null;
        }

        List<String> recipientAddresses = new ArrayList<>();
        recipientAddresses.addAll(findEmailAddresess(to));
        recipientAddresses.addAll(findEmailAddresess(cc));
        recipientAddresses.addAll(findEmailAddresess(bcc));

        List<AccountFileInstance> recipientAccountInstances = new ArrayList<>();
        for (String addr : recipientAddresses) {
            if (context.fileIngestIsCancelled()) {
                return null;
            }
            try {
                AccountFileInstance recipientAccountInstance = accountFileInstanceCache.getAccountInstance(addr, context);
                recipientAccountInstances.add(recipientAccountInstance);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to create account for email address  " + addr, ex); //NON-NLS
            }
        }

        addArtifactAttribute(headers, ATTRIBUTE_TYPE.TSK_HEADERS, bbattributes);
        addArtifactAttribute(from, ATTRIBUTE_TYPE.TSK_EMAIL_FROM, bbattributes);
        addArtifactAttribute(to, ATTRIBUTE_TYPE.TSK_EMAIL_TO, bbattributes);
        addArtifactAttribute(subject, ATTRIBUTE_TYPE.TSK_SUBJECT, bbattributes);

        addArtifactAttribute(dateL, ATTRIBUTE_TYPE.TSK_DATETIME_RCVD, bbattributes);
        addArtifactAttribute(dateL, ATTRIBUTE_TYPE.TSK_DATETIME_SENT, bbattributes);

        addArtifactAttribute(body, ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN, bbattributes);

        addArtifactAttribute(((id < 0L) ? NbBundle.getMessage(this.getClass(), "ThunderbirdMboxFileIngestModule.notAvail") : String.valueOf(id)),
                ATTRIBUTE_TYPE.TSK_MSG_ID, bbattributes);

        addArtifactAttribute(((localPath.isEmpty() == false) ? localPath : ""),
                ATTRIBUTE_TYPE.TSK_PATH, bbattributes);

        addArtifactAttribute(cc, ATTRIBUTE_TYPE.TSK_EMAIL_CC, bbattributes);
        addArtifactAttribute(bodyHTML, ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML, bbattributes);
        addArtifactAttribute(rtf, ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_RTF, bbattributes);
        addArtifactAttribute(threadID, ATTRIBUTE_TYPE.TSK_THREAD_ID, bbattributes);

        try {
            if (context.fileIngestIsCancelled()) {
                return null;
            }

            bbart = abstractFile.newDataArtifact(
                    new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG), 
                    bbattributes);

            if (context.fileIngestIsCancelled()) {
                return null;
            }

            // Add account relationships
            currentCase.getSleuthkitCase().getCommunicationsManager().addRelationships(senderAccountInstance, recipientAccountInstances, bbart, Relationship.Type.MESSAGE, dateL);

            if (context.fileIngestIsCancelled()) {
                return null;
            }

            try {
                // index the artifact for keyword search
                blackboard.postArtifact(bbart, EmailParserModuleFactory.getModuleName(), context.getJobId());
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bbart.getArtifactID(), ex); //NON-NLS
                MessageNotifyUtil.Notify.error(Bundle.ThunderbirdMboxFileIngestModule_addArtifact_indexError_message(), bbart.getDisplayName());
            }
        } catch (TskCoreException | TskDataException ex) {
            logger.log(Level.WARNING, null, ex);
        }

        return bbart;
    }

    /**
     * Add an attribute of a specified type to a supplied Collection.
     *
     * @param stringVal    The attribute value.
     * @param attrType     The type of attribute to be added.
     * @param bbattributes The Collection to which the attribute will be added.
     */
    static void addArtifactAttribute(String stringVal, BlackboardAttribute.Type attrType, Collection<BlackboardAttribute> bbattributes) {
        if (stringVal.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(attrType, EmailParserModuleFactory.getModuleName(), stringVal));
        }
    }

    /**
     * Add an attribute of a specified type to a supplied Collection.
     *
     * @param stringVal    The attribute value.
     * @param attrType     The type of attribute to be added.
     * @param bbattributes The Collection to which the attribute will be added.
     */
    static void addArtifactAttribute(String stringVal, ATTRIBUTE_TYPE attrType, Collection<BlackboardAttribute> bbattributes) {
        if (stringVal.isEmpty() == false) {
            bbattributes.add(new BlackboardAttribute(attrType, EmailParserModuleFactory.getModuleName(), stringVal));
        }
    }

    /**
     * Add an attribute of a specified type to a supplied Collection.
     *
     * @param longVal      The attribute value.
     * @param attrType     The type of attribute to be added.
     * @param bbattributes The Collection to which the attribute will be added.
     */
    static void addArtifactAttribute(long longVal, ATTRIBUTE_TYPE attrType, Collection<BlackboardAttribute> bbattributes) {
        if (longVal > 0) {
            bbattributes.add(new BlackboardAttribute(attrType, EmailParserModuleFactory.getModuleName(), longVal));
        }
    }

    /**
     * Cache for storing AccountFileInstance. The idea is that emails will be
     * used multiple times in a file and we shouldn't do a database lookup each
     * time.
     */
    static private class AccountFileInstanceCache {

        private final Map<String, AccountFileInstance> cacheMap;
        private final AbstractFile file;
        private final Case currentCase;

        /**
         * Create a new cache. Caches are linked to a specific file.
         *
         * @param file
         * @param currentCase
         */
        AccountFileInstanceCache(AbstractFile file, Case currentCase) {
            cacheMap = new HashMap<>();
            this.file = file;
            this.currentCase = currentCase;
        }

        /**
         * Get the account file instance from the cache or the database.
         *
         * @param email The email for this account.
         * @param context The current ingest job context.
         *
         * @return The corresponding AccountFileInstance
         *
         * @throws TskCoreException
         */
        AccountFileInstance getAccountInstance(String email, IngestJobContext context) throws TskCoreException {
            if (cacheMap.containsKey(email)) {
                return cacheMap.get(email);
            }

            AccountFileInstance accountInstance
                    = currentCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.EMAIL, email,
                            EmailParserModuleFactory.getModuleName(), file, null, context.getJobId());
            cacheMap.put(email, accountInstance);
            return accountInstance;
        }

        /**
         * Clears the cache.
         */
        void clear() {
            cacheMap.clear();
        }
    }

    /**
     * Post an error message for the user.
     *
     * @param subj    The error subject.
     * @param details The error details.
     */
    void postErrorMessage(String subj, String details) {
        IngestMessage ingestMessage = IngestMessage.createErrorMessage(EmailParserModuleFactory.getModuleVersion(), subj, details);
        services.postMessage(ingestMessage);
    }

    /**
     * Get the IngestServices object.
     *
     * @return The IngestServices object.
     */
    IngestServices getServices() {
        return services;
    }

    @Override
    public void shutDown() {
        synchronized(customAttributeCacheLock) {
            if (!customAttributeCache.isEmpty()) {
                customAttributeCache.clear();
            }
        }
    }

}
