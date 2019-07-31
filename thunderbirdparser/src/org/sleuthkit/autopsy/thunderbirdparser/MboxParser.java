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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.CharConversionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.mboxiterator.CharBufferWrapper;
import org.apache.james.mime4j.mboxiterator.MboxIterator;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.EncodedFileOutputStream;

/**
 * An Iterator for parsing mbox files. Wraps an instance of MBoxEmailIterator.
 */
class MboxParser implements Iterator<EmailMessage> {

    private static final Logger logger = Logger.getLogger(MboxParser.class.getName());
    private final DefaultMessageBuilder messageBuilder;
    private final List<String> errorList = new ArrayList<>();

    /**
     * The mime type string for html text.
     */
    private static final String HTML_TYPE = "text/html"; //NON-NLS

    /**
     * The local path of the mbox file.
     */
    private String localPath;

    private Iterator<EmailMessage> emailIterator = null;

    private MboxParser(String localPath) {
        this.localPath = localPath;
        messageBuilder = new DefaultMessageBuilder();
        MimeConfig config = MimeConfig.custom().setMaxLineLen(-1).build();
        // disable line length checks.
        messageBuilder.setMimeEntityConfig(config);
    }

    static boolean isValidMimeTypeMbox(byte[] buffer) {
        return (new String(buffer)).startsWith("From "); //NON-NLS
    }

    /**
     * Returns an instance of MBoxParser that will iterate and return
     * EMailMessage objects with only the information needed for threading
     * emails.
     *
     * @param localPath String path to the mboxFile
     * @param mboxFile  The mboxFile to parse
     *
     * @return Instance of MboxParser
     */
    static MboxParser getThreadInfoIterator(String localPath, File mboxFile) {
        MboxParser parser = new MboxParser(localPath);
        parser.createIterator(mboxFile, 0, false);
        return parser;
    }

    /**
     * Returns an instance of MBoxParser that will iterate "whole"
     * EmailMessages.
     *
     * @param localPath String path to the mboxFile
     * @param mboxFile  The mboxFile to parse
     * @param fileID    The fileID of the abstractFile that mboxFile was found
     *
     * @return Instance of MboxParser
     */
    static MboxParser getEmailIterator(String localPath, File mboxFile, long fileID) {
        MboxParser parser = new MboxParser(localPath);
        parser.createIterator(mboxFile, fileID, true);

        return parser;
    }

    /**
     * Creates the real Iterator object instance.
     *
     * @param mboxFile The mboxFile to parse
     * @param fileID   The fileID of the abstractFile that mboxFile was found
     * @param wholeMsg True if EmailMessage should have the whole message, not
     *                 just the thread information.
     */
    private void createIterator(File mboxFile, long fileID, boolean wholeMsg) {
        // Detect possible charsets
        List<CharsetEncoder> encoders = getPossibleEncoders(mboxFile);

        // Loop through the possible encoders and find the first one that works.
        // That will usually be one of the first ones.
        for (CharsetEncoder encoder : encoders) {
            try {
                Iterable<CharBufferWrapper> mboxIterable = MboxIterator.fromFile(mboxFile).charset(encoder.charset()).build();
                if (mboxIterable != null) {
                    emailIterator = new MBoxEmailIterator(mboxIterable.iterator(), encoder, fileID, wholeMsg);
                }
                break;
            } catch (CharConversionException | UnsupportedCharsetException ex) {
                // Not the right encoder
            } catch (IllegalArgumentException ex) {
                // Not the right encoder
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Failed to open mbox file: %s %d", mboxFile.getName(), fileID), ex); //NON-NLS
                addErrorMessage(NbBundle.getMessage(this.getClass(), "MboxParser.parse.errMsg.failedToReadFile"));
            }
        }
    }

    @Override
    public boolean hasNext() {
        return emailIterator != null && emailIterator.hasNext();
    }

    @Override
    public EmailMessage next() {
        return emailIterator != null ? emailIterator.next() : null;
    }

    String getErrors() {
        String result = "";
        for (String msg: errorList) {
            result += "<li>" + msg + "</li>"; 
        }
        return result;
    }

    /**
     * Use the information stored in the given mime4j message to populate an
     * EmailMessage.
     *
     * @param msg
     *
     * @return
     */
    private EmailMessage extractEmail(Message msg, long fileID) {
        EmailMessage email = new EmailMessage();
        // Basic Info
        email.setSender(getAddresses(msg.getFrom()));
        email.setRecipients(getAddresses(msg.getTo()));
        email.setBcc(getAddresses(msg.getBcc()));
        email.setCc(getAddresses(msg.getCc()));
        email.setSubject(msg.getSubject());
        email.setSentDate(msg.getDate());
        email.setLocalPath(localPath);
        email.setMessageID(msg.getMessageId());

        Field field = msg.getHeader().getField("in-reply-to"); //NON-NLS
        String inReplyTo = null;

        if (field != null) {
            inReplyTo = field.getBody();
            email.setInReplyToID(inReplyTo);
        }

        field = msg.getHeader().getField("references");
        if (field != null) {
            List<String> references = new ArrayList<>();
            for (String id : field.getBody().split(">")) {
                references.add(id.trim() + ">");
            }

            if (!references.contains(inReplyTo)) {
                references.add(inReplyTo);
            }

            email.setReferences(references);
        }

        // Body
        if (msg.isMultipart()) {
            handleMultipart(email, (Multipart) msg.getBody(), fileID);
        } else {
            handleTextBody(email, (TextBody) msg.getBody(), msg.getMimeType(), msg.getHeader().getFields());
        }

        return email;
    }

    /**
     * Extract the subject, inReplyTo, message-ID and references from the
     * Message object and returns them in a new EmailMessage object.
     *
     * @param msg Message object
     *
     * @return EmailMessage instance with only some of the message information
     */
    private EmailMessage extractPartialEmail(Message msg) {
        EmailMessage email = new EmailMessage();
        email.setSubject(msg.getSubject());
        email.setMessageID(msg.getMessageId());

        Field field = msg.getHeader().getField("in-reply-to"); //NON-NLS
        String inReplyTo = null;

        if (field != null) {
            inReplyTo = field.getBody();
            email.setInReplyToID(inReplyTo);
        }

        field = msg.getHeader().getField("references");
        if (field != null) {
            List<String> references = new ArrayList<>();
            for (String id : field.getBody().split(">")) {
                references.add(id.trim() + ">");
            }

            if (!references.contains(inReplyTo)) {
                references.add(inReplyTo);
            }

            email.setReferences(references);
        }

        return email;
    }

    /**
     * Handle a multipart mime message. Recursively calls handleMultipart if one
     * of the body parts is another multipart. Otherwise, calls the correct
     * method to extract information out of each part of the body.
     *
     * @param email
     * @param multi
     */
    private void handleMultipart(EmailMessage email, Multipart multi, long fileID) {
        List<Entity> entities = multi.getBodyParts();
        for (int index = 0; index < entities.size(); index++) {
            Entity e = entities.get(index);
            if (e.isMultipart()) {
                handleMultipart(email, (Multipart) e.getBody(), fileID);
            } else if (e.getDispositionType() != null
                    && e.getDispositionType().equals(ContentDispositionField.DISPOSITION_TYPE_ATTACHMENT)) {
                handleAttachment(email, e, fileID, index);
            } else if (e.getMimeType().equals(HTML_TYPE)
                    || e.getMimeType().equals(ContentTypeField.TYPE_TEXT_PLAIN)) {
                handleTextBody(email, (TextBody) e.getBody(), e.getMimeType(), e.getHeader().getFields());
            } else {
                // Ignore other types.
            }
        }
    }

    /**
     * Extract text out of a body part of the message.
     *
     * Handles text and html mime types. Throws away all other types. (only
     * other example I've seen is text/calendar)
     *
     * @param email
     * @param tb
     * @param type  The Mime type of the body.
     */
    private void handleTextBody(EmailMessage email, TextBody tb, String type, List<Field> fields) {
        BufferedReader r;
        try {
            r = new BufferedReader(tb.getReader());
            StringBuilder bodyString = new StringBuilder();
            StringBuilder headersString = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                bodyString.append(line).append("\n");
            }

            headersString.append("\n-----HEADERS-----\n");
            for (Field field : fields) {
                String nextLine = field.getName() + ": " + field.getBody();
                headersString.append("\n").append(nextLine);
            }
            headersString.append("\n\n---END HEADERS--\n\n");

            email.setHeaders(headersString.toString());

            switch (type) {
                case ContentTypeField.TYPE_TEXT_PLAIN:
                    email.setTextBody(bodyString.toString());
                    break;
                case HTML_TYPE:
                    email.setHtmlBody(bodyString.toString());
                    break;
                default:
                    // Not interested in other text types.
                    break;
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error getting text body of mbox message", ex); //NON-NLS
        }
    }

    /**
     * Extract the attachment out of the given entity. Should only be called if
     * e.getDispositionType() == "attachment"
     *
     * @param email
     * @param e
     */
    @NbBundle.Messages({"MboxParser.handleAttch.noOpenCase.errMsg=Exception while getting open case."})
    private void handleAttachment(EmailMessage email, Entity e, long fileID, int index) {
        String outputDirPath;
        String relModuleOutputPath;
        try {
            outputDirPath = ThunderbirdMboxFileIngestModule.getModuleOutputPath() + File.separator;
            relModuleOutputPath = ThunderbirdMboxFileIngestModule.getRelModuleOutputPath() + File.separator;
        } catch (NoCurrentCaseException ex) {
            addErrorMessage(Bundle.MboxParser_handleAttch_noOpenCase_errMsg());
            logger.log(Level.SEVERE, Bundle.MboxParser_handleAttch_noOpenCase_errMsg(), ex); //NON-NLS
            return;
        }
        String filename = FileUtil.escapeFileName(e.getFilename());

        // also had some crazy long names, so make random one if we get those.
        // also from Japanese image that had encoded name
        if (filename.length() > 64) {
            filename = UUID.randomUUID().toString();
        }

        String uniqueFilename = fileID + "-" + index + "-" + email.getSentDate() + "-" + filename;
        String outPath = outputDirPath + uniqueFilename;
        EncodedFileOutputStream fos;
        BinaryBody bb;
        try {
            fos = new EncodedFileOutputStream(new FileOutputStream(outPath), TskData.EncodingType.XOR1);
        } catch (IOException ex) {
            addErrorMessage(
                    NbBundle.getMessage(this.getClass(),
                            "MboxParser.handleAttch.errMsg.failedToCreateOnDisk", outPath));
            logger.log(Level.WARNING, "Failed to create file output stream for: " + outPath, ex); //NON-NLS
            return;
        }

        try {
            Body b = e.getBody();
            if (b instanceof BinaryBody) {
                bb = (BinaryBody) b;
                bb.writeTo(fos);
            } else {
                // This could potentially be other types. Only seen this once.
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to write mbox email attachment to disk.", ex); //NON-NLS
            addErrorMessage(NbBundle.getMessage(this.getClass(), "MboxParser.handleAttch.failedWriteToDisk", filename));
            return;
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to close file output stream", ex); //NON-NLS
            }
        }

        EmailMessage.Attachment attach = new EmailMessage.Attachment();
        attach.setName(filename);
        attach.setLocalPath(relModuleOutputPath + uniqueFilename);
        attach.setSize(new File(outPath).length());
        attach.setEncodingType(TskData.EncodingType.XOR1);
        email.addAttachment(attach);
    }

    /**
     * Get a String representation of the MailboxList (which is a list of email
     * addresses).
     *
     * @param mailboxList
     *
     * @return
     */
    private String getAddresses(MailboxList mailboxList) {
        if (mailboxList == null) {
            return "";
        }
        StringBuilder addresses = new StringBuilder();
        for (Mailbox m : mailboxList) {
            addresses.append(m.toString()).append("; ");
        }
        return addresses.toString();
    }

    /**
     * Get a String representation of the AddressList (which is a list of email
     * addresses).
     *
     * @param addressList
     *
     * @return
     */
    private String getAddresses(AddressList addressList) {
        return (addressList == null) ? "" : getAddresses(addressList.flatten());
    }

    /**
     * Get a list of the possible encoders for the given mboxFile using Tika's
     * CharsetDetector. At a minimum, returns the standard built in charsets.
     *
     * @param mboxFile
     *
     * @return
     */
    private List<CharsetEncoder> getPossibleEncoders(File mboxFile) {
        InputStream is;
        List<CharsetEncoder> possibleEncoders = new ArrayList<>();

        possibleEncoders.add(StandardCharsets.ISO_8859_1.newEncoder());
        possibleEncoders.add(StandardCharsets.US_ASCII.newEncoder());
        possibleEncoders.add(StandardCharsets.UTF_16.newEncoder());
        possibleEncoders.add(StandardCharsets.UTF_16BE.newEncoder());
        possibleEncoders.add(StandardCharsets.UTF_16LE.newEncoder());
        possibleEncoders.add(StandardCharsets.UTF_8.newEncoder());

        try {
            is = new BufferedInputStream(new FileInputStream(mboxFile));
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, "Failed to find mbox file while detecting charset"); //NON-NLS
            return possibleEncoders;
        }

        try {
            CharsetDetector detector = new CharsetDetector();
            detector.setText(is);
            CharsetMatch[] matches = detector.detectAll();
            for (CharsetMatch match : matches) {
                try {
                    possibleEncoders.add(Charset.forName(match.getName()).newEncoder());
                } catch (UnsupportedCharsetException | IllegalCharsetNameException ex) {
                    // Don't add unsupported charsets to the list
                }
            }
            return possibleEncoders;
        } catch (IOException | IllegalArgumentException ex) {
            logger.log(Level.WARNING, "Failed to detect charset of mbox file.", ex); //NON-NLS
            return possibleEncoders;
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to close input stream"); //NON-NLS
            }
        }
    }

    private void addErrorMessage(String msg) {
        errorList.add(msg);
    }

    /**
     * An Interator for mbox email messages.
     */
    final class MBoxEmailIterator implements Iterator<EmailMessage> {

        private final Iterator<CharBufferWrapper> mboxIterator;
        private final CharsetEncoder encoder;
        private final long fileID;
        private final boolean wholeMsg;

        MBoxEmailIterator(Iterator<CharBufferWrapper> mboxIter, CharsetEncoder encoder, long fileID, boolean wholeMsg) {
            mboxIterator = mboxIter;
            this.encoder = encoder;
            this.fileID = fileID;
            this.wholeMsg = wholeMsg;
        }

        @Override
        public boolean hasNext() {
            return (mboxIterator != null && encoder != null) && mboxIterator.hasNext();
        }

        @Override
        public EmailMessage next() {
            CharBufferWrapper messageBuffer = mboxIterator.next();

            try {
                Message msg = messageBuilder.parseMessage(messageBuffer.asInputStream(encoder.charset()));
                if (wholeMsg) {
                    return extractEmail(msg, fileID);
                } else {
                    return extractPartialEmail(msg);
                }
            } catch (RuntimeException | IOException ex) {
                logger.log(Level.WARNING, "Failed to get message from mbox: {0}", ex.getMessage()); //NON-NLS
            }
            return null;
        }

    }
}
