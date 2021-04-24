/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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

import com.google.common.collect.Iterables;
import com.pff.PSTAttachment;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.IngestServices;
import static org.sleuthkit.autopsy.thunderbirdparser.ThunderbirdMboxFileIngestModule.getRelModuleOutputPath;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.EncodedFileOutputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Parser for extracting emails from pst/ost Mircosoft Outlook data files.
 *
 * @author jwallace
 */
class PstParser  implements AutoCloseable{

    private static final Logger logger = Logger.getLogger(PstParser.class.getName());
    /**
     * First four bytes of a pst file.
     */
    private static int PST_HEADER = 0x2142444E;

    private final IngestServices services;

    private PSTFile pstFile;
    private long fileID;

    private int failureCount = 0;
    
    private final List<String> errorList = new ArrayList<>();

    PstParser(IngestServices services) {
        this.services = services;
    }

    enum ParseResult {

        OK, ERROR, ENCRYPT;
    }

    /**
     * Create an instance of PSTFile for the given File object.
     *
     * The constructor for PSTFile object will throw a generic PSTException if
     * the file is encrypted.
     * <a href=https://github.com/rjohnsondev/java-libpst/blob/5436a7abc8ac8c1622bf5dba0f4f9428fdbcd634/src/main/java/com/pff/PSTFile.java>
     * PSTFile.java</a>
     *
     * @param file   File to open
     * @param fileID File id for use when creating the EmailMessage objects
     *
     * @return ParserResult value OK if the PSTFile was successfully created,
     *         ENCRYPT will be returned for PSTExceptions that matches at
     *         specific message or IllegalArgumentExceptions
     */
    ParseResult open(File file, long fileID) {
        if (file == null) {
            return ParseResult.ERROR;
        }

        try {
            pstFile = new PSTFile(file);
        } catch (PSTException ex) {
            // This is the message thrown from the PSTFile constructor if it
            // detects that the file is encrypted. 
            if (ex.getMessage().equals("Only unencrypted and compressable PST files are supported at this time")) { //NON-NLS
                logger.log(Level.INFO, "Found encrypted PST file."); //NON-NLS
                return ParseResult.ENCRYPT;
            }
            if (ex.getMessage().toLowerCase().startsWith("unable to")) {
                logger.log(Level.WARNING, ex.getMessage());
                logger.log(Level.WARNING, String.format("Error in parsing PST file %s, file may be empty or corrupt", file.getName()));
                return ParseResult.ERROR;
            }
            String msg = file.getName() + ": Failed to create internal java-libpst PST file to parse:\n" + ex.getMessage(); //NON-NLS
            logger.log(Level.WARNING, msg, ex);
            return ParseResult.ERROR;
        } catch (IOException ex) {
            String msg = file.getName() + ": Failed to create internal java-libpst PST file to parse:\n" + ex.getMessage(); //NON-NLS
            logger.log(Level.WARNING, msg, ex);
            return ParseResult.ERROR;
        } catch (IllegalArgumentException ex) { // Not sure if this is true, was in previous version of code.
            logger.log(Level.INFO, "Found encrypted PST file."); //NON-NLS
            return ParseResult.ENCRYPT;
        }

        return ParseResult.OK;
    }
    
    @Override
    public void close() throws IOException{
        if(pstFile != null) {
            RandomAccessFile file = pstFile.getFileHandle();
            if(file != null) {
                file.close();
            }
        }
    }

    /**
     * Creates an EmailMessage iterator for pstFile. These Email objects will be
     * complete and with all available information.
     *
     * @return A instance of an EmailMessage Iterator
     */
    Iterator<EmailMessage> getEmailMessageIterator() {
        if (pstFile == null) {
            return null;
        }

        Iterable<EmailMessage> iterable = null;

        try {
            iterable = getEmailMessageIterator(pstFile.getRootFolder(), "\\", fileID, true);
        } catch (PSTException | IOException ex) {
            logger.log(Level.WARNING, String.format("Exception thrown while parsing fileID: %d", fileID), ex);
        }

        if (iterable == null) {
            return null;
        }

        return iterable.iterator();
    }

    /**
     * Get a List of EmailMessages which contain only the information needed for
     * threading the emails.
     *
     * @return A list of EmailMessage or an empty list if non were found.
     */
    List<EmailMessage> getPartialEmailMessages() {
        List<EmailMessage> messages = new ArrayList<>();
        Iterator<EmailMessage> iterator = getPartialEmailMessageIterator();
        if (iterator != null) {
            while (iterator.hasNext()) {
                messages.add(iterator.next());
            }
        }

        return messages;
    }

    /**
     * Returns string containing the list of the current parse errors
     *
     * @return String error list, empty string if no errors exist.
     */
    String getErrors() {
        String result = "";
        for (String msg: errorList) {
            result += "<li>" + msg + "</li>"; 
        }
        return result;
    }

    /**
     * Returns the count of parse errors.
     *
     * @return Integer count of parse errors.
     */
    int getFailureCount() {
        return failureCount;
    }

    /**
     * Get an Iterator which will iterate over the PSTFile, but return
     * EmailMessages with only the information needed for putting the emails
     * into threads.
     *
     * @return A EmailMessage iterator or null if no messages where found
     */
    private Iterator<EmailMessage> getPartialEmailMessageIterator() {
        if (pstFile == null) {
            return null;
        }

        Iterable<EmailMessage> iterable = null;

        try {
            iterable = getEmailMessageIterator(pstFile.getRootFolder(), "\\", fileID, false);
        } catch (PSTException | IOException ex) {
            logger.log(Level.WARNING, String.format("Exception thrown while parsing fileID: %d", fileID), ex);
        }

        if (iterable == null) {
            return null;
        }

        return iterable.iterator();
    }

    /**
     * Creates an Iterable object of Email messages for the given folder.
     *
     * @param folder       PSTFolder to process
     * @param path         String path to folder
     * @param fileID       FileID of the AbstractFile folder was found in
     * @param partialEmail Whether or not fill the EMailMessage with all data
     *
     * @return An Iterable for iterating email message, or null if there were no
     *         messages or children in folder.
     *
     * @throws PSTException
     * @throws IOException
     */
    private Iterable<EmailMessage> getEmailMessageIterator(PSTFolder folder, String path, long fileID, boolean wholeMsg) throws PSTException, IOException {
        Iterable<EmailMessage> iterable = null;

        if (folder.getContentCount() > 0) {
            iterable = new PstEmailIterator(folder, path, fileID, wholeMsg).getIterable();
        }

        if (folder.hasSubfolders()) {
            List<PSTFolder> subFolders = folder.getSubFolders();
            for (PSTFolder subFolder : subFolders) {
                String newpath = path + "\\" + subFolder.getDisplayName();
                Iterable<EmailMessage> subIterable = getEmailMessageIterator(subFolder, newpath, fileID, wholeMsg);
                if (subIterable == null) {
                    continue;
                }

                if (iterable != null) {
                    iterable = Iterables.concat(iterable, subIterable);
                } else {
                    iterable = subIterable;
                }

            }
        }

        return iterable;
    }

    /**
     * Create an EmailMessage from a PSTMessage.
     *
     * @param msg       PSTMessage object to parse
     * @param localPath Path to local file
     *
     * @return EmailMessage object.
     */
    private EmailMessage extractEmailMessage(PSTMessage msg, String localPath, long fileID) {
        EmailMessage email = new EmailMessage();
        String toAddress = msg.getDisplayTo();
        String ccAddress = msg.getDisplayCC();
        String bccAddress = msg.getDisplayBCC();
        String receivedByName = msg.getReceivedByName();
        String receivedBySMTPAddress = msg.getReceivedBySMTPAddress();
        
        if (toAddress.contains(receivedByName)) {
            toAddress = toAddress.replace(receivedByName, receivedBySMTPAddress);
        }
        if (ccAddress.contains(receivedByName)) {
            ccAddress = ccAddress.replace(receivedByName, receivedBySMTPAddress);
        }
        if (bccAddress.contains(receivedByName)) {
            bccAddress = bccAddress.replace(receivedByName, receivedBySMTPAddress);
        }
        email.setRecipients(toAddress);
        email.setCc(ccAddress);
        email.setBcc(bccAddress);
        email.setSender(getSender(msg.getSenderName(), msg.getSentRepresentingSMTPAddress()));
        email.setSentDate(msg.getMessageDeliveryTime());
        email.setTextBody(msg.getBody());
        if (false == msg.getTransportMessageHeaders().isEmpty()) {
            email.setHeaders("\n-----HEADERS-----\n\n" + msg.getTransportMessageHeaders() + "\n\n---END HEADERS--\n\n");
        }
        email.setHtmlBody(msg.getBodyHTML());
        String rtf = "";
        try {
            rtf = msg.getRTFBody();
        } catch (PSTException | IOException ex) {
            logger.log(Level.INFO, "Failed to get RTF content from pst email."); //NON-NLS
        }
        email.setRtfBody(rtf);
        email.setLocalPath(localPath);
        email.setSubject(msg.getSubject());
        email.setId(msg.getDescriptorNodeId());
        email.setMessageID(msg.getInternetMessageId());

        String inReplyToID = msg.getInReplyToId();
        email.setInReplyToID(inReplyToID);

        if (msg.hasAttachments()) {
            extractAttachments(email, msg, fileID);
        }

        List<String> references = extractReferences(msg.getTransportMessageHeaders());
        if (inReplyToID != null && !inReplyToID.isEmpty()) {
            if (references == null) {
                references = new ArrayList<>();
                references.add(inReplyToID);
            } else if (!references.contains(inReplyToID)) {
                references.add(inReplyToID);
            }
        }
        email.setReferences(references);

        return email;
    }

    /**
     * Create an EmailMessage from a PSTMessage with only the information needed
     * for threading emails.
     *
     * @return EmailMessage object with only some information, not all of the
     *         msg.
     */
    private EmailMessage extractPartialEmailMessage(PSTMessage msg) {
        EmailMessage email = new EmailMessage();
        email.setSubject(msg.getSubject());
        email.setId(msg.getDescriptorNodeId());
        email.setMessageID(msg.getInternetMessageId());
        String inReplyToID = msg.getInReplyToId();
        email.setInReplyToID(inReplyToID);
        List<String> references = extractReferences(msg.getTransportMessageHeaders());
        if (inReplyToID != null && !inReplyToID.isEmpty()) {
            if (references == null) {
                references = new ArrayList<>();
                references.add(inReplyToID);
            } else if (!references.contains(inReplyToID)) {
                references.add(inReplyToID);
            }
        }
        email.setReferences(references);

        return email;
    }

    /**
     * Add the attachments within the PSTMessage to the EmailMessage.
     *
     * @param email EmailMessage object to have attachment added
     * @param msg   PSTMessage object with the attachments
     */
    @NbBundle.Messages({"PstParser.noOpenCase.errMsg=Exception while getting open case."})
    private void extractAttachments(EmailMessage email, PSTMessage msg, long fileID) {
        int numberOfAttachments = msg.getNumberOfAttachments();
        String outputDirPath;
        try {
            outputDirPath = ThunderbirdMboxFileIngestModule.getModuleOutputPath() + File.separator;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }
        for (int x = 0; x < numberOfAttachments; x++) {
            String filename = "";
            try {
                PSTAttachment attach = msg.getAttachment(x);
                long size = attach.getAttachSize();
                long freeSpace = services.getFreeDiskSpace();
                if ((freeSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN) && (size >= freeSpace)) {
                    continue;
                }
                // both long and short filenames can be used for attachments
                filename = attach.getLongFilename();
                if (filename.isEmpty()) {
                    filename = attach.getFilename();
                }
                String uniqueFilename = fileID + "-" + msg.getDescriptorNodeId() + "-" + attach.getContentId() + "-" + FileUtil.escapeFileName(filename);
                String outPath = outputDirPath + uniqueFilename;
                saveAttachmentToDisk(attach, outPath);

                EmailMessage.Attachment attachment = new EmailMessage.Attachment();

                long crTime = attach.getCreationTime() != null ? attach.getCreationTime().getTime() / 1000 : 0;
                long mTime = attach.getModificationTime() != null ? attach.getModificationTime().getTime() / 1000 : 0;
                String relPath = getRelModuleOutputPath() + File.separator + uniqueFilename;
                attachment.setName(filename);
                attachment.setCrTime(crTime);
                attachment.setmTime(mTime);
                attachment.setLocalPath(relPath);
                attachment.setSize(attach.getFilesize());
                attachment.setEncodingType(TskData.EncodingType.XOR1);
                email.addAttachment(attachment);
            } catch (PSTException | IOException | NullPointerException ex) {
                /**
                 * Swallowing null pointer as it is caused by a problem with
                 * getting input stream (library problem).
                 */
                addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "PstParser.extractAttch.errMsg.failedToExtractToDisk",
                                filename));
                logger.log(Level.WARNING, "Failed to extract attachment from pst file.", ex); //NON-NLS
            } catch (NoCurrentCaseException ex) {
                addErrorMessage(Bundle.PstParser_noOpenCase_errMsg());
                logger.log(Level.SEVERE, Bundle.PstParser_noOpenCase_errMsg(), ex); //NON-NLS
            }
        }
    }

    /**
     * Extracts a PSTAttachment to the module output directory.
     *
     * @param attach  PSTAttachment object to be parsed
     * @param outPath Location to write attachments
     *
     * @throws IOException
     * @throws PSTException
     */
    private void saveAttachmentToDisk(PSTAttachment attach, String outPath) throws IOException, PSTException {
        try (InputStream attachmentStream = attach.getFileInputStream();
                EncodedFileOutputStream out = new EncodedFileOutputStream(new FileOutputStream(outPath), TskData.EncodingType.XOR1)) {
            // 8176 is the block size used internally and should give the best performance
            int bufferSize = 8176;
            byte[] buffer = new byte[bufferSize];
            int count = attachmentStream.read(buffer);

            if (count == -1) {
                throw new IOException("attachmentStream invalid (read() fails). File " + attach.getLongFilename() + " skipped");
            }

            while (count == bufferSize) {
                out.write(buffer);
                count = attachmentStream.read(buffer);
            }
            if (count != -1) {
                byte[] endBuffer = new byte[count];
                System.arraycopy(buffer, 0, endBuffer, 0, count);
                out.write(endBuffer);
            }
        }
    }

    /**
     * Pretty-Print "From" field of an outlook email message.
     *
     * @param name Sender's Name
     * @param addr Sender's Email address
     *
     * @return
     */
    private String getSender(String name, String addr) {
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
     * Identify a file as a pst/ost file by it's header.
     *
     * @param file
     *
     * @return
     */
    public static boolean isPstFile(AbstractFile file) {
        byte[] buffer = new byte[4];
        try {
            int read = file.read(buffer, 0, 4);
            if (read != 4) {
                return false;
            }
            ByteBuffer bb = ByteBuffer.wrap(buffer);
            return bb.getInt() == PST_HEADER;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Exception while detecting if a file is a pst file."); //NON-NLS
            return false;
        }
    }

    /**
     * Adds passed in string to the error message with formatting.
     *
     * @param msg String message to add
     */
    private void addErrorMessage(String msg) {
        errorList.add(msg);
    }

    /**
     * Returns the references value from the email header.
     *
     * @param emailHeader
     *
     * @return A list of message-IDs
     */
    private List<String> extractReferences(String emailHeader) {
        Scanner scanner = new Scanner(emailHeader);
        StringBuilder buffer = null;
        while (scanner.hasNextLine()) {
            String token = scanner.nextLine();

            if (token.matches("^References:.*")) {
                buffer = new StringBuilder();
                buffer.append((token.substring(token.indexOf(':') + 1)).trim());
            } else if (buffer != null) {
                if (token.matches("^\\w+:.*$")) {
                    List<String> references = new ArrayList<>();
                    for (String id : buffer.toString().split(">")) {
                        references.add(id.trim() + ">");
                    }
                    return references;
                } else {
                    buffer.append(token.trim());
                }
            }
        }

        return null;
    }

    /**
     * A iterator for processing the PST email folder structure and returning
     * instances of the EmailMessage object.
     */
    private final class PstEmailIterator implements Iterator<EmailMessage> {

        private final PSTFolder folder;
        private EmailMessage currentMsg;
        private EmailMessage nextMsg;

        private final String currentPath;
        private final long fileID;
        private final boolean wholeMsg;

        /**
         * Class constructor, initializes the "next" message;
         *
         * @param folder PSTFolder object to iterate across
         * @param path   String path value to the location of folder
         * @param fileID Long fileID of the abstract file this PSTFolder was
         *               found
         */
        PstEmailIterator(PSTFolder folder, String path, long fileID, boolean wholeMsg) {
            this.folder = folder;
            this.fileID = fileID;
            this.currentPath = path;
            this.wholeMsg = wholeMsg;

            if (folder.getContentCount() > 0) {
                try {
                    PSTMessage message = (PSTMessage) folder.getNextChild();
                    if (message != null) {
                        if (wholeMsg) {
                            nextMsg = extractEmailMessage(message, currentPath, fileID);
                        } else {
                            nextMsg = extractPartialEmailMessage(message);
                        }
                    }
                } catch (PSTException | IOException ex) {
                    failureCount++;
                    logger.log(Level.WARNING, String.format("Unable to extract emails for path: %s file ID: %d ", path, fileID), ex);
                }
            }
        }

        @Override
        public boolean hasNext() {
            return nextMsg != null;
        }

        @Override
        public EmailMessage next() {

            currentMsg = nextMsg;

            try {
                PSTMessage message = (PSTMessage) folder.getNextChild();
                if (message != null) {
                    if (wholeMsg) {
                        nextMsg = extractEmailMessage(message, currentPath, fileID);
                    } else {
                        nextMsg = extractPartialEmailMessage(message);
                    }
                } else {
                    nextMsg = null;
                }
            } catch (PSTException | IOException ex) {
                logger.log(Level.WARNING, String.format("Unable to extract emails for path: %s file ID: %d ", currentPath, fileID), ex);
                failureCount++;
                nextMsg = null;
            }

            return currentMsg;
        }

        /**
         * Get a wrapped Iterable version of PstEmailIterator
         *
         * @return Iterable wrapping this class
         */
        Iterable<EmailMessage> getIterable() {
            return new Iterable<EmailMessage>() {
                @Override
                public Iterator<EmailMessage> iterator() {
                    return PstEmailIterator.this;
                }
            };
        }

    }
}
