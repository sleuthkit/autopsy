/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
import org.apache.james.mime4j.stream.Field;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.EncodedFileOutputStream;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * @author kelly
 */
public abstract class MimeJ4MessageParser {
     private static final Logger logger = Logger.getLogger(MimeJ4MessageParser.class.getName());
     
    /**
     * The mime type string for html text.
     */

    private static final String HTML_TYPE = "text/html"; //NON-NLS
     
     /**
     * Use the information stored in the given mime4j message to populate an
     * EmailMessage.
     *
     * @param msg
     *
     * @return
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
    @NbBundle.Messages({"EMLParser.handleAttch.noOpenCase.errMsg=Exception while getting open case."})
    private static void handleAttachment(EmailMessage email, Entity e, long fileID, int index) {
        String outputDirPath;
        String relModuleOutputPath;
        try {
            outputDirPath = ThunderbirdMboxFileIngestModule.getModuleOutputPath() + File.separator;
            relModuleOutputPath = ThunderbirdMboxFileIngestModule.getRelModuleOutputPath() + File.separator;
        } catch (NoCurrentCaseException ex) {
//            logger.log(Level.SEVERE, Bundle.MboxParser_handleAttch_noOpenCase_errMsg(), ex); //NON-NLS
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
     * @return
     */
    private static String getAddresses(AddressList addressList) {
        return (addressList == null) ? "" : getAddresses(addressList.flatten());
    }
}
