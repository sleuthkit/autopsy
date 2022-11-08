/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageWriter;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.EncodedFileOutputStream;
import org.sleuthkit.datamodel.TskData;

/**
 * Super class for email parsers that can use the james.mime4J.Message objects.
 */
class MimeJ4MessageParser implements AutoCloseable{

    private static final Logger logger = Logger.getLogger(MimeJ4MessageParser.class.getName());

    /**
     * The mime type string for html text.
     */
    private static final String HTML_TYPE = "text/html"; //NON-NLS
    private DefaultMessageBuilder messageBuilder = null;
    private final List<String> errorList = new ArrayList<>();

    /**
     * The local path of the email message(s) file.
     */
    private String localPath;

    DefaultMessageBuilder getMessageBuilder() {
        if (messageBuilder == null) {
            messageBuilder = new DefaultMessageBuilder();
            MimeConfig config = MimeConfig.custom().setMaxLineLen(-1).setMaxHeaderLen(-1).setMaxHeaderCount(-1).build();
            // disable line length checks.
            messageBuilder.setMimeEntityConfig(config);
        }

        return messageBuilder;
    }

    /**
     * Sets the local path of the email messages file.
     *
     * @param localPath Local path of the file the email messages
     */
    final void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    /**
     * Gets the local path.
     *
     * @return
     */
    String getLocalPath() {
        return localPath;
    }

    /**
     * Get a list of the parsing error message.
     *
     * @return String containing all of the parse error message. Empty string is
     *         returned if there are no error messages.
     */
    String getErrors() {
        String result = "";
        for (String msg : errorList) {
            result += "<li>" + msg + "</li>";
        }
        return result;
    }

    /**
     * Adds a message to the error Message list.
     *
     * @param msg Message to add to the list.
     */
    void addErrorMessage(String msg) {
        errorList.add(msg);
    }

    /**
     * Use the information stored in the given mime4j message to populate an
     * EmailMessage.
     *
     * @param msg The Message to extract data from.
     *
     * @return EmailMessage for the Message.
     */
    EmailMessage extractEmail(Message msg, String localPath, long sourceFileID) {
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
            handleMultipart(email, (Multipart) msg.getBody(), sourceFileID);
        } else {
            if(msg.getBody() instanceof TextBody) {
                handleTextBody(email, (TextBody) msg.getBody(), msg.getMimeType(), msg.getHeader().getFields());
            } else  {
               handleAttachment(email, msg, sourceFileID, 1);
            }
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
    EmailMessage extractPartialEmail(Message msg) {
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
            } else if ((e.getMimeType().equals(HTML_TYPE) && (email.getHtmlBody() == null || email.getHtmlBody().isEmpty()))
                    || (e.getMimeType().equals(ContentTypeField.TYPE_TEXT_PLAIN) && (email.getTextBody() == null || email.getTextBody().isEmpty()))) {
                    handleTextBody(email, (TextBody) e.getBody(), e.getMimeType(), e.getHeader().getFields());
            } else {               
                handleAttachment(email, e, fileID, index);
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
    @NbBundle.Messages({"MimeJ4MessageParser.handleAttch.noOpenCase.errMsg=Exception while getting open case."})
    private void handleAttachment(EmailMessage email, Entity e, long fileID, int index) {
        String outputDirPath;
        String relModuleOutputPath;
        try {
            outputDirPath = ThunderbirdMboxFileIngestModule.getModuleOutputPath() + File.separator;
            relModuleOutputPath = ThunderbirdMboxFileIngestModule.getRelModuleOutputPath() + File.separator;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, Bundle.MimeJ4MessageParser_handleAttch_noOpenCase_errMsg(), ex); //NON-NLS
            return;
        }
        String filename = e.getFilename();
        
        if (filename == null) {
            filename = "attachment" + e.hashCode();
            logger.log(Level.WARNING, String.format("Attachment has no file name using '%s'", filename));
        }
        
        filename = FileUtil.escapeFileName(filename);

        // also had some crazy long names, so make random one if we get those.
        // also from Japanese image that had encoded name
        if (filename.length() > 64) {
            filename = UUID.randomUUID().toString();
        }

        String uniqueFilename = fileID + "-" + index + "-" + email.getSentDate() + "-" + filename;
        String outPath = outputDirPath + uniqueFilename;
        
        Body body = e.getBody();
        if (body != null) {
            long fileLength;
            try (EncodedFileOutputStream fos = new EncodedFileOutputStream(new FileOutputStream(outPath), TskData.EncodingType.XOR1)) {
                
                EmailMessage.Attachment attach;
                MessageWriter msgWriter = new DefaultMessageWriter();
                
                if(body instanceof Message) {
                    msgWriter.writeMessage((Message)body, fos);
                    attach = new EmailMessage.AttachedEmailMessage(extractEmail((Message)body, email.getLocalPath(), fileID));
                } else {
                    msgWriter.writeBody(body, fos);
                    attach = new EmailMessage.Attachment();
                }
                fileLength = fos.getBytesWritten();
                attach.setName(filename);
                attach.setLocalPath(relModuleOutputPath + uniqueFilename);
                attach.setSize(fileLength);
                attach.setEncodingType(TskData.EncodingType.XOR1);
                email.addAttachment(attach);

            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to create file output stream for: " + outPath, ex); //NON-NLS
            }
        } 
    }

    /**
     * Get a String representation of the MailboxList (which is a list of email
     * addresses).
     *
     * @param mailboxList
     *
     * @return String list of email addresses separated by a ; or empty string
     *         if no addresses were found.
     */
    private static String getAddresses(MailboxList mailboxList) {
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
     * @return String list of email addresses separated by a ; or empty string
     *         if no addresses were found.
     */
    private static String getAddresses(AddressList addressList) {
        return (addressList == null) ? "" : getAddresses(addressList.flatten());
    }

    @Override
    public void close() throws IOException{
        
    }
}
