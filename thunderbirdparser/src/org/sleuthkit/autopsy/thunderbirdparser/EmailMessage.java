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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.sleuthkit.datamodel.TskData;

/**
 * A Record to hold generic information about email messages, regardless of the
 * original format or source.
 *
 * @author jwallace
 */
class EmailMessage {

    private String recipients = "";
    private String bcc = "";
    private String cc = "";
    private String sender = "";
    private String subject = "";
    private String headers = "";
    private String textBody = "";
    private String htmlBody = "";
    private String rtfBody = "";
    private String localPath = "";
    private boolean hasAttachment = false;
    private long sentDate = 0L;
    private final List<Attachment> attachments = new ArrayList<>();
    private long id = -1L;
    private String messageID = "";
    private String inReplyToID = "";
    private List<String> references = new ArrayList<>();
    private String simplifiedSubject = "";
    private boolean replySubject = false;
    private String messageThreadID = "";

    boolean hasAttachment() {
        return hasAttachment;
    }

    String getRecipients() {
        return recipients;
    }

    void setRecipients(String recipients) {
        if (recipients != null) {
            this.recipients = recipients;
        }
    }

    String getSender() {
        return sender;
    }

    void setSender(String sender) {
        if (sender != null) {
            this.sender = sender;
        }
    }

    String getSubject() {
        return subject;
    }

    void setSubject(String subject) {
        if (subject != null) {
            this.subject = subject;
            if (subject.matches("^[R|r][E|e].*?:.*")) {
                this.simplifiedSubject = subject.replaceAll("[R|r][E|e].*?:", "").trim();
                replySubject = true;
            } else {
                this.simplifiedSubject = subject;
            }
        } else {
            this.simplifiedSubject = "";
        }
    }

    /**
     * Returns the orginal subject with the "RE:" stripped off".
     *
     * @return Message subject with the "RE" stripped off
     */
    String getSimplifiedSubject() {
        return simplifiedSubject;
    }

    /**
     * Returns whether or not the message subject started with "RE:"
     *
     * @return true if the original subject started with RE otherwise false.
     */
    boolean isReplySubject() {
        return replySubject;
    }

    String getHeaders() {
        return headers;
    }

    void setHeaders(String headers) {
        if (headers != null) {
            this.headers = headers;
        }
    }

    String getTextBody() {
        return textBody;
    }

    void setTextBody(String textBody) {
        if (textBody != null) {
            this.textBody = textBody;
        }
    }

    String getHtmlBody() {
        return htmlBody;
    }

    void setHtmlBody(String htmlBody) {
        if (htmlBody != null) {
            this.htmlBody = htmlBody;
        }
    }

    String getRtfBody() {
        return rtfBody;
    }

    void setRtfBody(String rtfBody) {
        if (rtfBody != null) {
            this.rtfBody = rtfBody;
        }
    }

    long getSentDate() {
        return sentDate;
    }

    void setSentDate(Date sentDate) {
        if (sentDate != null) {
            this.sentDate = sentDate.getTime() / 1000;
        }
    }

    void setSentDate(long sentDate) {
        this.sentDate = sentDate;
    }

    String getBcc() {
        return bcc;
    }

    void setBcc(String bcc) {
        if (bcc != null) {
            this.bcc = bcc;
        }
    }

    String getCc() {
        return cc;
    }

    void setCc(String cc) {
        if (cc != null) {
            this.cc = cc;
        }
    }

    void addAttachment(Attachment a) {
        attachments.add(a);
        hasAttachment = true;
    }

    List<Attachment> getAttachments() {
        return attachments;
    }

    long getId() {
        return id;
    }

    void setId(long id) {
        this.id = id;
    }

    String getLocalPath() {
        return localPath;
    }

    void setLocalPath(String localPath) {
        if (localPath != null) {
            this.localPath = localPath;
        }
    }

    /**
     * Returns the value of the Message-ID header field of this message or empty
     * string if it is not present.
     *
     * @return the identifier of this message.
     */
    String getMessageID() {
        return messageID;
    }

    /**
     * Sets the identifier of this message.
     *
     * @param messageID identifer of this message
     */
    void setMessageID(String messageID) {
        if (messageID != null) {
            this.messageID = messageID;
        } else {
            this.messageID = "";
        }
    }

    /**
     * Returns the messageID of the parent message or empty String if not
     * present.
     *
     * @return the idenifier of the message parent
     */
    String getInReplyToID() {
        return inReplyToID;
    }

    /**
     * Sets the messageID of the parent message.
     *
     * @param inReplyToID messageID of the parent message.
     */
    void setInReplyToID(String inReplyToID) {
        this.inReplyToID = inReplyToID;
    }

    /**
     * Returns a list of Message-IDs listing the parent, grandparent,
     * great-grandparent, and so on, of this message.
     *
     * @return The reference list or empty string if none is available.
     */
    List<String> getReferences() {
        return references;
    }

    /**
     * Set the list of reference message-IDs from the email message header.
     *
     * @param references
     */
    void setReferences(List<String> references) {
        this.references = references;
    }

    /**
     * Sets the ThreadID of this message.
     *
     * @param threadID - the thread ID to set
     */
    void setMessageThreadID(String threadID) {
        this.messageThreadID = threadID;
    }

    /**
     * Returns the ThreadID for this message.
     *
     * @return - the message thread ID or "" is non is available
     */
    String getMessageThreadID() {
        return this.messageThreadID;
    }

    /**
     * A Record to hold generic information about attachments.
     *
     * Used to populate the fields of a derived file.
     *
     * @author jwallace
     */
    static class Attachment {

        private String name = "";

        private String localPath = "";

        private long size = 0L;

        private long crTime = 0L;

        private long cTime = 0L;

        private long aTime = 0L;

        private long mTime = 0L;

        private TskData.EncodingType encodingType = TskData.EncodingType.NONE;

        String getName() {
            return name;
        }

        void setName(String name) {
            if (name != null) {
                this.name = name;
            }
        }

        String getLocalPath() {
            return localPath;
        }

        void setLocalPath(String localPath) {
            if (localPath != null) {
                this.localPath = localPath;
            }
        }

        long getSize() {
            return size;
        }

        void setSize(long size) {
            this.size = size;
        }

        long getCrTime() {
            return crTime;
        }

        void setCrTime(long crTime) {
            this.crTime = crTime;
        }

        void setCrTime(Date crTime) {
            if (crTime != null) {
                this.crTime = crTime.getTime() / 1000;
            }
        }

        long getcTime() {
            return cTime;
        }

        void setcTime(long cTime) {
            this.cTime = cTime;
        }

        void setcTime(Date cTime) {
            if (cTime != null) {
                this.cTime = cTime.getTime() / 1000;
            }
        }

        long getaTime() {
            return aTime;
        }

        void setaTime(long aTime) {
            this.aTime = aTime;
        }

        void setaTime(Date aTime) {
            if (aTime != null) {
                this.aTime = aTime.getTime() / 1000;
            }
        }

        long getmTime() {
            return mTime;
        }

        void setmTime(long mTime) {
            this.mTime = mTime;
        }

        void setmTime(Date mTime) {
            if (mTime != null) {
                this.mTime = mTime.getTime() / 1000;
            }
        }

        void setEncodingType(TskData.EncodingType encodingType) {
            this.encodingType = encodingType;
        }

        TskData.EncodingType getEncodingType() {
            return encodingType;
        }

    }
    
    static class AttachedEmailMessage extends Attachment {
        private final EmailMessage emailMessage;
        
        AttachedEmailMessage(EmailMessage emailMessage) {
            this.emailMessage = emailMessage;
        }
        
        EmailMessage getEmailMessage() {
            return emailMessage;
        }
    }
}
