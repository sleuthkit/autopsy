/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Message that ingestmodule wants to send to IngetInbox to notify user about
 * something. Submitted to user via IngestServices. Create using factory
 * methods.
 */
public class IngestMessage {

    /**
     * Level of message.
     */
    public enum MessageType {

        DATA, INFO, WARNING, ERROR
    };

    private long ID;
    private MessageType messageType;
    private String source;
    private String subject;
    private String detailsHtml;
    private String uniqueKey;
    private BlackboardArtifact data;
    private Date datePosted;
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static int managerMessageId = 0;
    private static AtomicLong nextMessageID = new AtomicLong(0);

    /**
     * Private constructor used by factory methods
     */
    private IngestMessage(long ID, MessageType messageType, String source, String subject, String detailsHtml, String uniqueKey) {
        this.ID = ID;
        this.source = source;
        this.messageType = messageType;
        this.subject = subject;
        this.detailsHtml = detailsHtml;
        if (uniqueKey == null) {
            this.uniqueKey = "";
        } else {
            this.uniqueKey = uniqueKey;
        }
        datePosted = new Date();
    }

    //getters
    public long getID() {
        return ID;
    }

    public String getSource() {
        return source;
    }

    public String getSubject() {
        return subject;
    }

    public String getDetails() {
        return detailsHtml;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public BlackboardArtifact getData() {
        return data;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public Date getDatePosted() {
        return datePosted;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Long.toString(ID)).append(": ");
        sb.append(NbBundle.getMessage(this.getClass(), "IngestMessage.toString.type.text", messageType.name()));
        if (source != null) //can be null for manager messages
        {
            sb.append(source);
        }
        sb.append(
                NbBundle.getMessage(this.getClass(), "IngestMessage.toString.date.text", dateFormat.format(datePosted)));
        sb.append(NbBundle.getMessage(this.getClass(), "IngestMessage.toString.subject.text", subject));
        if (detailsHtml != null) {
            sb.append(NbBundle.getMessage(this.getClass(), "IngestMessage.toString.details.text", detailsHtml));
        }
        if (data != null) {
            sb.append(NbBundle.getMessage(this.getClass(), "IngestMessage.toString.data.text", data.toString()));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final IngestMessage other = (IngestMessage) obj;
        if (this.ID != other.ID) {
            return false;
        }
        if (this.messageType != other.messageType) {
            return false;
        }
        if (this.source != other.source && (this.source == null || !this.source.equals(other.source))) {
            return false;
        }
        if ((this.subject == null) ? (other.subject != null) : !this.subject.equals(other.subject)) {
            return false;
        }
        if ((this.detailsHtml == null) ? (other.detailsHtml != null) : !this.detailsHtml.equals(other.detailsHtml)) {
            return false;
        }
        if ((this.uniqueKey == null) ? (other.uniqueKey != null) : !this.uniqueKey.equals(other.uniqueKey)) {
            return false;
        }
        if (this.data != other.data && (this.data == null || !this.data.equals(other.data))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + (int) (this.ID ^ (this.ID >>> 32));
        hash = 59 * hash + (this.messageType != null ? this.messageType.hashCode() : 0);
        hash = 59 * hash + (this.source != null ? this.source.hashCode() : 0);
        hash = 59 * hash + (this.subject != null ? this.subject.hashCode() : 0);
        hash = 59 * hash + (this.detailsHtml != null ? this.detailsHtml.hashCode() : 0);
        hash = 59 * hash + (this.uniqueKey != null ? this.uniqueKey.hashCode() : 0);
        hash = 59 * hash + (this.data != null ? this.data.hashCode() : 0);
        return hash;
    }

    /**
     * Create a message of specified type
     *
     * @param messageType message type
     * @param source      originating module
     * @param subject     message subject to be displayed
     * @param detailsHtml html formatted detailed message (without leading and
     *                    closing &lt;html&gt; tags), for instance, a
     *                    human-readable representation of the data. Or null.
     *
     * @return
     */
    public static IngestMessage createMessage(MessageType messageType, String source, String subject, String detailsHtml) {
        if (messageType == null || source == null || subject == null) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(IngestMessage.class, "IngestMessage.exception.typeSrcSubjNotNull.msg"));
        }
        long ID = nextMessageID.getAndIncrement();
        return new IngestMessage(ID, messageType, source, subject, detailsHtml, null);
    }

    /**
     * Create a simple message with a subject only
     *
     * @param messageType message type
     * @param source      originating module
     * @param subject     message subject to be displayed
     *
     * @return
     */
    public static IngestMessage createMessage(MessageType messageType, String source, String subject) {
        return createMessage(messageType, source, subject, null);
    }

    /**
     * Create error message
     *
     * @param source      originating module
     * @param subject     message subject to be displayed
     * @param detailsHtml html formatted detailed message (without leading and
     *                    closing &lt;html&gt; tags), for instance, a
     *                    human-readable representation of the data. Or null
     *
     * @return
     */
    public static IngestMessage createErrorMessage(String source, String subject, String detailsHtml) {
        if (source == null || subject == null) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(IngestMessage.class, "IngestMessage.exception.srcSubjNotNull.msg"));
        }
        long ID = nextMessageID.getAndIncrement();
        return new IngestMessage(ID, MessageType.ERROR, source, subject, detailsHtml, null);
    }

    /**
     * Create warning message
     *
     * @param source      originating module
     * @param subject     message subject to be displayed
     * @param detailsHtml html formatted detailed message (without leading and
     *                    closing &lt;html&gt; tags), for instance, a
     *                    human-readable representation of the data. Or null
     *
     * @return
     */
    public static IngestMessage createWarningMessage(String source, String subject, String detailsHtml) {
        if (source == null || subject == null) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(IngestMessage.class, "IngestMessage.exception.srcSubjNotNull.msg"));
        }
        long ID = nextMessageID.getAndIncrement();
        return new IngestMessage(ID, MessageType.WARNING, source, subject, detailsHtml, null);
    }

    /**
     *
     * @param source      originating module
     * @param subject     message subject to be displayed
     * @param detailsHtml html formatted detailed message (without leading and
     *                    closing &lt;html&gt; tags), for instance, a
     *                    human-readable representation of the data. Or null.
     * @param uniqueKey   Key used to group similar messages together. Shoudl be
     *                    unique to the analysis. For example, hits for the same
     *                    keyword in a keyword search would use the keyword as
     *                    this unique value so that they can be grouped.
     * @param data        blackboard artifact associated with the message, the
     *                    same as fired in ModuleDataEvent by the module
     *
     * @return
     */
    public static IngestMessage createDataMessage(String source, String subject, String detailsHtml, String uniqueKey, BlackboardArtifact data) {
        if (source == null || subject == null || detailsHtml == null || data == null) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(IngestMessage.class, "IngestMessage.exception.srcSubjDetailsDataNotNull.msg"));
        }

        long ID = nextMessageID.getAndIncrement();
        IngestMessage im = new IngestMessage(ID, MessageType.DATA, source, subject, detailsHtml, uniqueKey);
        im.data = data;
        return im;
    }

    /**
     * Used by IngestMager to post status messages.
     *
     * @param subject     message subject to be displayed
     * @param detailsHtml html formatted detailed message (without leading and
     *                    closing &lt;html&gt; tags), for instance, a
     *                    human-readable representation of the data. Or null.
     *
     */
    static IngestMessage createManagerMessage(String subject, String detailsHtml) {
        return new IngestMessage(++managerMessageId, MessageType.INFO, null, subject, detailsHtml, null);
    }

    /**
     * Used by IngestMager to post error messages.
     */
    static IngestMessage createManagerErrorMessage(String subject, String detailsHtml) {
        return new IngestMessage(++managerMessageId, MessageType.ERROR, null, subject, detailsHtml, null);
    }
}
