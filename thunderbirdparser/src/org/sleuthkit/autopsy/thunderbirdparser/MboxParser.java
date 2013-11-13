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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
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

/**
 *
 * @author jwallace
 */
public class MboxParser {
    private static final Logger logger = Logger.getLogger(MboxParser.class.getName());
    private MessageBuilder messageBuilder;
    
    private static final String HTML_TYPE = "text/html";
    private String localPath = null;
    MboxParser() {
        messageBuilder = new DefaultMessageBuilder();
    }
    
    MboxParser(String localPath) {
        this();
        this.localPath = localPath;
    }
    
    List<EmailMessage> parse(File mboxFile) {
        //JWTODO: detect charset
        CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        List<EmailMessage> emails = new ArrayList<>();
        try {
            for (CharBufferWrapper message : MboxIterator.fromFile(mboxFile).charset(encoder.charset()).build()) {
                try {
                    Message msg = messageBuilder.parseMessage(message.asInputStream(encoder.charset()));
                    emails.add(extractEmail(msg));
                } catch (MimeException ex) {
                    logger.log(Level.WARNING, "Failed to get message from mbox.", ex);
                }
            }
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, "couldn't find mbox file.", ex);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error getting messsages from mbox file.");
        }
        
        return emails;
    }
    
    private EmailMessage extractEmail(Message msg) {
        EmailMessage email = new EmailMessage();
        // Basic Info
        email.setSender(getAddresses(msg.getFrom()));
        email.setRecipients(getAddresses(msg.getTo()));
        email.setBcc(getAddresses(msg.getBcc()));
        email.setCc(getAddresses(msg.getCc()));
        email.setSubject(msg.getSubject());
        email.setSentDate(msg.getDate());
        if (localPath != null) {
            email.setLocalPath(localPath);
        }
        
        // Body
        if (msg.isMultipart()) {
            handleMultipart(email, (Multipart) msg.getBody());
        } else {
            handleTextBody(email, (TextBody) msg.getBody(), msg.getMimeType());
        }
        
        return email;
    }
    
    private void handleMultipart(EmailMessage email, Multipart multi) {
        for (Entity e : multi.getBodyParts()) {
            if (e.isMultipart()) {
                handleMultipart(email, (Multipart) e.getBody());
            } else if (e.getDispositionType() != null 
                    && e.getDispositionType().equals(ContentDispositionField.DISPOSITION_TYPE_ATTACHMENT)) {
                handleAttachment(email, e);
            } else if (e.getMimeType().equals(HTML_TYPE) ||
                    e.getMimeType().equals(ContentTypeField.TYPE_TEXT_PLAIN)) {
                handleTextBody(email, (TextBody) e.getBody(), e.getMimeType());
            } else {
                logger.log(Level.INFO, "Found unrecognized entity: " + e); 
            }
        }
    }
    
    private void handleTextBody(EmailMessage email, TextBody tb, String type) {
        BufferedReader r;
        try {
            r = new BufferedReader(tb.getReader());
            StringBuilder bodyString = new StringBuilder();
            String line = "";
            while ((line = r.readLine()) != null) {
                bodyString.append(line).append("\n");
            }

            switch (type) {
                case ContentTypeField.TYPE_TEXT_PLAIN:
                    email.setTextBody(bodyString.toString());
                    break;
                case HTML_TYPE:
                    email.setHtmlBody(bodyString.toString());
                    break;
                default:
                    logger.log(Level.INFO, "Found unrecognized mime type: " + type);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error getting text body of mbox message", ex);
        }
    }
    
    private void handleAttachment(EmailMessage email, Entity e) {
        String outputDirPath = ThunderbirdMboxFileIngestModule.getModuleOutputPath() + File.separator;
        String filename = e.getFilename();
        String outPath = outputDirPath + filename;
        FileOutputStream fos;
        BinaryBody bb;
        try {
            fos = new FileOutputStream(outPath);
        } catch (FileNotFoundException ex) {
            logger.log(Level.INFO, "", ex);
            return;
        }
        
        try {
            bb = (BinaryBody) e.getBody();
            bb.writeTo(fos);
        } catch (IOException ex) {
            logger.log(Level.INFO, "", ex);
            return;
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                logger.log(Level.INFO, "Failed to close file output stream", ex);
            }
        }
        
        Attachment attach = new Attachment();
        attach.setName(filename);
        attach.setLocalPath(ThunderbirdMboxFileIngestModule.getRelModuleOutputPath() 
                + File.separator + filename);
        // JWTODO: find appropriate constant or make one.
//        ContentDispositionField disposition = (ContentDispositionField) e.getHeader().getField("Content-Disposition");
//        if (disposition != null) {
//            attach.setSize(disposition.getSize());
//            attach.setCrTime(disposition.getCreationDate());
//            attach.setmTime(disposition.getModificationDate());
//            attach.setaTime(disposition.getReadDate());
//        }
        email.addAttachment(attach);
    }

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
    
    private String getAddresses(AddressList addressList) {
        return (addressList == null) ? "" : getAddresses(addressList.flatten());
    }
}
