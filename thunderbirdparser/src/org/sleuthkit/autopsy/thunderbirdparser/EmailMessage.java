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


/**
 * A Record to hold generic information about email messages, regardless of 
 * the original format or source.
 * @author jwallace
 */
public class EmailMessage {
    private String recipients = "";
    private String bcc = "";
    private String cc = "";
    private String sender = "";
    private String subject = "";
    private String textBody =  "";
    private String htmlBody = "";
    private String rtfBody = "";
    private String localPath = "";
    private boolean hasAttachment = false;
    private long sentDate = 0L;
    private List<Attachment> attachments = new ArrayList<>();
    private long id = -1L;
    
    boolean hasAttachment() {
        return hasAttachment;
    }

    String getRecipients() {
        return recipients;
    }

    void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    String getSender() {
        return sender;
    }

    void setSender(String sender) {
        this.sender = sender;
    }

    String getSubject() {
        return subject;
    }

    void setSubject(String subject) {
        this.subject = subject;
    }

    String getTextBody() {
        return textBody;
    }

    void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    String getHtmlBody() {
        return htmlBody;
    }

    void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }

    String getRtfBody() {
        return rtfBody;
    }

    void setRtfBody(String rtfBody) {
        this.rtfBody = rtfBody;
    }

    long getSentDate() {
        return sentDate;
    }

    void setSentDate(Date sentDate) {
        this.sentDate = sentDate.getTime() / 1000;
    }
    
    void setSentDate(long sentDate) {
        this.sentDate = sentDate;
    }

    String getBcc() {
        return bcc;
    }

    void setBcc(String bcc) {
        this.bcc = bcc;
    }

    String getCc() {
        return cc;
    }

    void setCc(String cc) {
        this.cc = cc;
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
        this.localPath = localPath;
    }
}

/**
 * A Record to hold generic information about attachments.
 * 
 * Used to populate the fields of a derived file.
 * @author jwallace
 */
class Attachment {
    private String name = "";
    private String localPath = "";
    private long size = 0L;
    private long crTime = 0L;
    private long cTime = 0L;
    private long aTime = 0L;
    private long mTime = 0L;

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getLocalPath() {
        return localPath;
    }

    void setLocalPath(String localPath) {
        this.localPath = localPath;
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
        this.crTime = crTime.getTime() / 1000;
    }

    long getcTime() {
        return cTime;
    }

    void setcTime(long cTime) {
        this.cTime = cTime;
    }
    
    void setcTime(Date cTime) {
        this.cTime = cTime.getTime() / 1000;
    }

    long getaTime() {
        return aTime;
    }

    void setaTime(long aTime) {
        this.aTime = aTime;
    }
    
    void setaTime(Date aTime) {
        this.aTime = aTime.getTime() / 1000;
    }

    long getmTime() {
        return mTime;
    }

    void setmTime(long mTime) {
        this.mTime = mTime;
    }
    
    void setmTime(Date mTime) {
        this.mTime = mTime.getTime() / 1000;
    }
}