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

import com.pff.PSTAttachment;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.IngestModule;
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
class PstParser {

    private static final Logger logger = Logger.getLogger(PstParser.class.getName());
    /**
     * First four bytes of a pst file.
     */
    private static int PST_HEADER = 0x2142444E;
    private IngestServices services;
    /**
     * A map of PSTMessages to their Local path within the file's internal
     * directory structure.
     */
    private List<EmailMessage> results;
    private StringBuilder errors;

    PstParser(IngestServices services) {
        results = new ArrayList<>();
        this.services = services;
        errors = new StringBuilder();
    }

    enum ParseResult {

        OK, ERROR, ENCRYPT;
    }

    /**
     * Parse and extract email messages from the pst/ost file.
     *
     * @param file A pst or ost file.
     *
     * @return ParseResult: OK on success, ERROR on an error, ENCRYPT if failed
     *         because the file is encrypted.
     */
    ParseResult parse(File file, long fileID) {
        PSTFile pstFile;
        long failures;
        try {
            pstFile = new PSTFile(file);
            failures = processFolder(pstFile.getRootFolder(), "\\", true, fileID);
            if (failures > 0) {
                addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "PstParser.parse.errMsg.failedToParseNMsgs", failures));
            }
            return ParseResult.OK;
        } catch (PSTException | IOException ex) {
            String msg = file.getName() + ": Failed to create internal java-libpst PST file to parse:\n" + ex.getMessage(); //NON-NLS
            logger.log(Level.WARNING, msg);
            return ParseResult.ERROR;
        } catch (IllegalArgumentException ex) {
            logger.log(Level.INFO, "Found encrypted PST file."); //NON-NLS
            return ParseResult.ENCRYPT;
        }
    }

    /**
     * Get the results of the parsing.
     *
     * @return
     */
    List<EmailMessage> getResults() {
        return results;
    }

    String getErrors() {
        return errors.toString();
    }

    /**
     * Process this folder and all subfolders, adding every email found to
     * results. Accumulates the folder hierarchy path as it navigates the folder
     * structure.
     *
     * @param folder The folder to navigate and process
     * @param path   The path to the folder within the pst/ost file's directory
     *               structure
     *
     * @throws PSTException
     * @throws IOException
     */
    private long processFolder(PSTFolder folder, String path, boolean root, long fileID) {
        String newPath = (root ? path : path + "\\" + folder.getDisplayName());
        long failCount = 0L; // Number of emails that failed
        if (folder.hasSubfolders()) {
            List<PSTFolder> subFolders;
            try {
                subFolders = folder.getSubFolders();
            } catch (PSTException | IOException ex) {
                subFolders = new ArrayList<>();
                logger.log(Level.INFO, "java-libpst exception while getting subfolders: {0}", ex.getMessage()); //NON-NLS
            }

            for (PSTFolder f : subFolders) {
                failCount += processFolder(f, newPath, false, fileID);
            }
        }

        if (folder.getContentCount() != 0) {
            PSTMessage email;
            // A folder's children are always emails, never other folders.
            try {
                while ((email = (PSTMessage) folder.getNextChild()) != null) {
                    results.add(extractEmailMessage(email, newPath, fileID));
                }
            } catch (PSTException | IOException ex) {
                failCount++;
                logger.log(Level.INFO, "java-libpst exception while getting emails from a folder: {0}", ex.getMessage()); //NON-NLS
            }
        }

        return failCount;
    }

    /**
     * Create an EmailMessage from a PSTMessage.
     *
     * @param msg
     * @param localPath
     *
     * @return
     */
    private EmailMessage extractEmailMessage(PSTMessage msg, String localPath, long fileID) {
        EmailMessage email = new EmailMessage();
        email.setRecipients(msg.getDisplayTo());
        email.setCc(msg.getDisplayCC());
        email.setBcc(msg.getDisplayBCC());
        email.setSender(getSender(msg.getSenderName(), msg.getSenderEmailAddress()));
        email.setSentDate(msg.getMessageDeliveryTime());
        email.setTextBody(msg.getBody());
        if(false == msg.getTransportMessageHeaders().isEmpty()) {
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

        if (msg.hasAttachments()) {
            extractAttachments(email, msg, fileID);
        }

        return email;
    }

    /**
     * Add the attachments within the PSTMessage to the EmailMessage.
     *
     * @param email
     * @param msg
     */
    private void extractAttachments(EmailMessage email, PSTMessage msg, long fileID) {
        int numberOfAttachments = msg.getNumberOfAttachments();
        String outputDirPath;
        try {
            outputDirPath = ThunderbirdMboxFileIngestModule.getModuleOutputPath() + File.separator;
        } catch (NoCurrentCaseException ex) {
                addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "PstParser.extractAttch.errMsg.failedToExtractToDisk",
                                filename));
                logger.log(Level.WARNING, "Failed to extract attachment from pst file.", ex); //NON-NLS
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
                String uniqueFilename = fileID + "-" + msg.getDescriptorNodeId() + "-" + attach.getContentId() + "-" + filename;
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
            } catch (PSTException | IOException | NullPointerException | NoCurrentCaseException ex) {
                /**
                 * Swallowing null pointer as it is caused by a problem with
                 * getting input stream (library problem).
                 */
                addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "PstParser.extractAttch.errMsg.failedToExtractToDisk",
                                filename));
                logger.log(Level.WARNING, "Failed to extract attachment from pst file.", ex); //NON-NLS
            }
        }
    }

    /**
     * Extracts a PSTAttachment to the module output directory.
     *
     * @param attach
     * @param outPath
     *
     * @return
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

    private void addErrorMessage(String msg) {
        errors.append("<li>").append(msg).append("</li>"); //NON-NLS
    }
}
