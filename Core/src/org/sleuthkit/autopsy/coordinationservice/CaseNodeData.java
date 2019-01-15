/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coordinationservice;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;

/**
 * An object that converts data for a case directory lock coordination service
 * node to and from byte arrays.
 */
public final class CaseNodeData {

    private static final int CURRENT_VERSION = 1;

    /*
     * Version 0 fields.
     */
    private int version;
    private boolean errorsOccurred;

    /*
     * Version 1 fields.
     */
    private Path directory;
    private long createDate;
    private long lastAccessDate;
    private String name;
    private String displayName;
    private short deletedItemFlags;

    /**
     * Gets the current version of the case directory lock coordination service
     * node data.
     *
     * @return The version number.
     */
    public static int getCurrentVersion() {
        return CaseNodeData.CURRENT_VERSION;
    }

    /**
     * Uses a CaseMetadata object to construct an object that converts data for
     * a case directory lock coordination service node to and from byte arrays.
     *
     * @param metadata The case meta data.
     *
     * @throws java.text.ParseException If there is an error parsing dates from
     *                                  string representations of dates in the
     *                                  meta data.
     */
    public CaseNodeData(CaseMetadata metadata) throws ParseException {
        this.version = CURRENT_VERSION;
        this.errorsOccurred = false;
        this.directory = Paths.get(metadata.getCaseDirectory());
        this.createDate = CaseMetadata.getDateFormat().parse(metadata.getCreatedDate()).getTime();
        this.lastAccessDate = new Date().getTime(); // Don't really know.
        this.name = metadata.getCaseName();
        this.displayName = metadata.getCaseDisplayName();
        this.deletedItemFlags = 0;
    }

    /**
     * Uses coordination service node data to construct an object that converts
     * data for a case directory lock coordination service node to and from byte
     * arrays.
     *
     * @param nodeData The raw bytes received from the coordination service.
     *
     * @throws InvalidDataException If the node data buffer is smaller than
     *                              expected.
     */
    public CaseNodeData(byte[] nodeData) throws InvalidDataException {
        if (nodeData == null || nodeData.length == 0) {
            throw new InvalidDataException(null == nodeData ? "Null node data byte array" : "Zero-length node data byte array");
        }

        /*
         * Get the fields from the node data.
         */
        ByteBuffer buffer = ByteBuffer.wrap(nodeData);
        try {
            /*
             * Get version 0 fields.
             */
            this.version = buffer.getInt();

            /*
             * Flags bit format: 76543210 0-6 --> reserved for future use 7 -->
             * errorsOccurred
             */
            byte flags = buffer.get();
            this.errorsOccurred = (flags < 0);

            if (buffer.hasRemaining()) {
                /*
                 * Get version 1 fields.
                 */
                this.directory = Paths.get(NodeDataUtils.getStringFromBuffer(buffer));
                this.createDate = buffer.getLong();
                this.lastAccessDate = buffer.getLong();
                this.name = NodeDataUtils.getStringFromBuffer(buffer);
                this.displayName = NodeDataUtils.getStringFromBuffer(buffer);
                this.deletedItemFlags = buffer.getShort();
            }

        } catch (BufferUnderflowException ex) {
            throw new InvalidDataException("Node data is incomplete", ex);
        }
    }

    /**
     * Gets the node data version number of this node.
     *
     * @return The version number.
     */
    public int getVersion() {
        return this.version;
    }

    /**
     * Gets whether or not any errors occurred during the processing of any auto
     * ingest job for the case represented by this node data.
     *
     * @return True or false.
     */
    public boolean getErrorsOccurred() {
        return this.errorsOccurred;
    }

    /**
     * Sets whether or not any errors occurred during the processing of any auto
     * ingest job for the case represented by this node data.
     *
     * @param errorsOccurred True or false.
     */
    public void setErrorsOccurred(boolean errorsOccurred) {
        this.errorsOccurred = errorsOccurred;
    }

    /**
     * Gets the path of the case directory of the case represented by this node
     * data.
     *
     * @return The case directory path.
     */
    public Path getDirectory() {
        return this.directory;
    }

    /**
     * Sets the path of the case directory of the case represented by this node
     * data.
     *
     * @param caseDirectory The case directory path.
     */
    public void setDirectory(Path caseDirectory) {
        this.directory = caseDirectory;
    }

    /**
     * Gets the date the case represented by this node data was created.
     *
     * @return The create date.
     */
    public Date getCreateDate() {
        return new Date(this.createDate);
    }

    /**
     * Sets the date the case represented by this node data was created.
     *
     * @param createDate The create date.
     */
    public void setCreateDate(Date createDate) {
        this.createDate = createDate.getTime();
    }

    /**
     * Gets the date the case represented by this node data last accessed.
     *
     * @return The last access date.
     */
    public Date getLastAccessDate() {
        return new Date(this.lastAccessDate);
    }

    /**
     * Sets the date the case represented by this node data was last accessed.
     *
     * @param lastAccessDate The last access date.
     */
    public void setLastAccessDate(Date lastAccessDate) {
        this.lastAccessDate = lastAccessDate.getTime();
    }

    /**
     * Gets the unique and immutable (user cannot change it) name of the case
     * represented by this node data.
     *
     * @return The case name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the unique and immutable (user cannot change it) name of the case
     * represented by this node data.
     *
     * @param name The case name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the display name of the case represented by this node data.
     *
     * @return The case display name.
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Sets the display name of the case represented by this node data.
     *
     * @param displayName The case display name.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the node data as a byte array that can be sent to the coordination
     * service.
     *
     * @return The node data as a byte array.
     */
    public byte[] toArray() {
        int bufferSize = Integer.BYTES; // version
        bufferSize += 1; // errorsOccurred
        bufferSize += this.directory.toString().getBytes().length; // directory
        bufferSize += Long.BYTES; // createDate
        bufferSize += Long.BYTES; // lastAccessDate
        bufferSize += this.name.getBytes().length; // name
        bufferSize += this.displayName.getBytes().length; // displayName
        bufferSize += Short.BYTES; // deletedItemFlags
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        buffer.putInt(this.version);
        buffer.put((byte) (this.errorsOccurred ? 0x80 : 0));

        if (this.version >= 1) {
            NodeDataUtils.putStringIntoBuffer(this.directory.toString(), buffer);
            buffer.putLong(this.createDate);
            buffer.putLong(this.lastAccessDate);
            NodeDataUtils.putStringIntoBuffer(name, buffer);
            NodeDataUtils.putStringIntoBuffer(displayName, buffer);
            buffer.putShort(deletedItemFlags);
        }

        byte[] array = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(array, 0, array.length);
        return array;
    }

    public final static class InvalidDataException extends Exception {

        private static final long serialVersionUID = 1L;

        private InvalidDataException(String message) {
            super(message);
        }

        private InvalidDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
