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
package org.sleuthkit.autopsy.casemodule.multiusercases;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final int version;
    private boolean errorsOccurred;

    /*
     * Version 1 fields.
     */
    private Path directory;
    private Date createDate;
    private Date lastAccessDate;
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
        this.createDate = CaseMetadata.getDateFormat().parse(metadata.getCreatedDate());
        this.lastAccessDate = new Date();
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
     * @throws IOException If there is an error reading the node data.
     */
    public CaseNodeData(byte[] nodeData) throws IOException {
        if (nodeData == null || nodeData.length == 0) {
            throw new IOException(null == nodeData ? "Null node data byte array" : "Zero-length node data byte array");
        }
        DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(nodeData));
        this.version = inputStream.readInt();
        if (this.version > 0) {
            this.errorsOccurred = inputStream.readBoolean();
        } else {
            short legacyErrorsOccurred = inputStream.readByte();
            this.errorsOccurred = (legacyErrorsOccurred < 0);
        }
        if (this.version > 0) {
            this.directory = Paths.get(inputStream.readUTF());
            this.createDate = new Date(inputStream.readLong());
            this.lastAccessDate = new Date(inputStream.readLong());
            this.name = inputStream.readUTF();
            this.displayName = inputStream.readUTF();
            this.deletedItemFlags = inputStream.readShort();
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
        return new Date(this.createDate.getTime());
    }

    /**
     * Sets the date the case represented by this node data was created.
     *
     * @param createDate The create date.
     */
    public void setCreateDate(Date createDate) {
        this.createDate = new Date(createDate.getTime());
    }

    /**
     * Gets the date the case represented by this node data last accessed.
     *
     * @return The last access date.
     */
    public Date getLastAccessDate() {
        return new Date(this.lastAccessDate.getTime());
    }

    /**
     * Sets the date the case represented by this node data was last accessed.
     *
     * @param lastAccessDate The last access date.
     */
    public void setLastAccessDate(Date lastAccessDate) {
        this.lastAccessDate = new Date(lastAccessDate.getTime());
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
     * Checks whether a deleted item flag is set for the case represented by
     * this node data.
     *
     * @param flag The flag to check.
     *
     * @return
     */
    public boolean isDeletedFlagSet(DeletedFlags flag) {
        return (this.deletedItemFlags & flag.getValue()) == flag.getValue();
    }

    /**
     * Sets a deleted item flag for the case represented by this node data.
     *
     * @param flag The flag to set.
     */
    public void setDeletedFlag(DeletedFlags flag) {
        this.deletedItemFlags |= flag.getValue();
    }

    /**
     * Gets the node data as a byte array that can be sent to the coordination
     * service.
     *
     * @return The node data as a byte array.
     *
     * @throws IOException If there is an error writing the node data.
     */
    public byte[] toArray() throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(byteStream);
        outputStream.writeInt(this.version);
        outputStream.writeBoolean(this.errorsOccurred);
        outputStream.writeUTF(this.directory.toString());
        outputStream.writeLong(this.createDate.getTime());
        outputStream.writeLong(this.lastAccessDate.getTime());
        outputStream.writeUTF(this.name);
        outputStream.writeUTF(this.displayName);
        outputStream.writeShort(this.deletedItemFlags);
        outputStream.flush();
        byteStream.flush();
        return byteStream.toByteArray();
    }

    /**
     * Flags for the various components of a case that can be deleted.
     */
    public enum DeletedFlags {

        TEXT_INDEX(1),
        CASE_DB(2),
        CASE_DIR(4),
        DATA_SOURCES(8),
        MANIFEST_FILE_NODES(16);

        private final short value;

        /**
         * Constructs a flag for a case component that can be deleted.
         *
         * @param value
         */
        private DeletedFlags(int value) {
            this.value = (short) value;
        }

        /**
         * Gets the value of the flag.
         *
         * @return The value as a short.
         */
        private short getValue() {
            return value;
        }

    }

}
