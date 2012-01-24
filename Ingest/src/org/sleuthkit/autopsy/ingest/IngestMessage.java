/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.sleuthkit.autopsy.datamodel.KeyValueThing;

/**
 * Representation of text posted by ingest services
 * 
 * Message should have a unique ID within context of originating source
 * 
 */
public class IngestMessage {

    public enum MessageType {

        DATA, INFO, WARNING, ERROR
    };
    
    private long ID;
    private MessageType messageType;
    private IngestServiceAbstract source;
    private String text;
    private KeyValueThing data;

    private IngestMessage(long ID, MessageType messageType, IngestServiceAbstract source, String text) {
        this.ID = ID;
        this.source = source;
        this.messageType = messageType;
        this.text = text;
    }

    //getters
    public long getID() {
        return ID;
    }

    public IngestServiceAbstract getSource() {
        return source;
    }

    public String getText() {
        return text;
    }

    public KeyValueThing getData() {
        return data;
    }

    public MessageType getMessageType() {
        return messageType;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Long.toString(ID)).append(": ");
        sb.append("type: ").append(messageType.name());
        sb.append("source: ").append(source.getName());
        sb.append("text: ").append(text);
        if (data != null)
            sb.append("data: ").append(data.toString());
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
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + (int) (this.ID ^ (this.ID >>> 32));
        hash = 83 * hash + (this.messageType != null ? this.messageType.hashCode() : 0);
        hash = 83 * hash + (this.source != null ? this.source.hashCode() : 0);
        hash = 83 * hash + (this.text != null ? this.text.hashCode() : 0);
        return hash;
    }

   
    

    //factory methods
    public static IngestMessage createMessage(long ID, MessageType messageType, IngestServiceAbstract source, String message) {
        if (messageType == null || source == null || message == null) {
            throw new IllegalArgumentException("message type, source and message cannot be null");
        }
        IngestMessage im = new IngestMessage(ID, messageType, source, message);
        return im;
    }

    public static IngestMessage createErrorMessage(long ID, IngestServiceAbstract source, String message) {
        if (source == null || message == null) {
            throw new IllegalArgumentException("message type, source and message cannot be null");
        }
        IngestMessage im = new IngestMessage(ID, MessageType.ERROR, source, message);
        return im;
    }
    
    public static IngestMessage createDataMessage(long ID, IngestServiceAbstract source, String message, KeyValueThing data) {
        if (source == null || message == null) {
            throw new IllegalArgumentException("source and message cannot be null");
        }
        IngestMessage im = new IngestMessage(ID, MessageType.DATA, source, message);
        im.data = data;
        return im;
    }
    
}
