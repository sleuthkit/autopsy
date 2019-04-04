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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Date;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.CaseMetadata.CaseMetadataException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Case data stored in case directory coordination service nodes.
 */
public final class CaseNodeData {

    private static final int CURRENT_VERSION = 1;
    private static final Logger logger = Logger.getLogger(CaseNodeData.class.getName());

    /*
     * Version 0 fields.
     */
    private int version;
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
     * Creates case node data from the metadata for a case and writes it to the
     * case directory coordination service node, which must already exist.
     *
     * @param metadata The case metadata.
     *
     * @return The case data that was written to the coordination service node.
     *
     * @throws CaseNodeDataException If there is an error creating or writing
     *                               the case node data.
     * @throws InterruptedException  If the current thread is interrupted while
     *                               waiting for the coordination service.
     */
    public static CaseNodeData createCaseNodeData(CaseMetadata metadata) throws CaseNodeDataException, InterruptedException {
        try {
            CaseNodeData nodeData = new CaseNodeData(metadata);
            CoordinationService coordinationService = CoordinationService.getInstance();
            coordinationService.setNodeData(CoordinationService.CategoryNode.CASES, nodeData.getDirectory().toString(), nodeData.toArray());
            return nodeData;

        } catch (ParseException | IOException | CoordinationServiceException ex) {
            throw new CaseNodeDataException(String.format("Failed to create/write case node data for %s", metadata.getCaseDirectory().toUpperCase()), ex); //NON-NLS
        }
    }

    /**
     * Reads case data from a case directory coordination service node. If the
     * data is missing, corrupted, or from an older version of the software, an
     * attempt is made to remedy the situation using the case metadata.
     *
     * @param nodePath The case directory coordination service node path.
     *
     * @return The case node data.
     */
    public static CaseNodeData readCaseNodeData(String nodePath) throws CaseNodeDataException, InterruptedException {
        try {
            CaseNodeData nodeData;
            CoordinationService coordinationService = CoordinationService.getInstance();
            byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, nodePath);
            if (nodeBytes != null && nodeBytes.length > 0) {
                try {
                    nodeData = new CaseNodeData(nodeBytes);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, String.format("Error reading coordination service node data for %s, will attempt to replace it", nodePath), ex); //NON-NLS
                    CaseMetadata metadata = getCaseMetadata(nodePath);
                    nodeData = createCaseNodeData(metadata);
                }
            } else {
                logger.log(Level.INFO, String.format("Missing coordination service node data for %s, will attempt to create it", nodePath)); //NON-NLS
                CaseMetadata metadata = getCaseMetadata(nodePath);
                nodeData = createCaseNodeData(metadata);
            }
            if (nodeData.getVersion() < CaseNodeData.CURRENT_VERSION) {
                nodeData = upgradeNodeData(nodePath, nodeData);
            }
            return nodeData;

        } catch (CaseNodeDataException | CaseMetadataException | ParseException | IOException | CoordinationServiceException ex) {
            throw new CaseNodeDataException(String.format("Failed to create/write case node data for %s", nodePath.toUpperCase()), ex); //NON-NLS
        }
    }

    /**
     * Writes updated case data to a case directory coordination service node.
     * Obtain the case data to be updated by calling createCaseNodeData() or
     * readCaseNodeData().
     *
     * @param nodeData The case node data.
     *
     * @throws CaseNodeDataException If there is an error writing the case node
     *                               data.
     * @throws InterruptedException  If the current thread is interrupted while
     *                               waiting for the coordination service.
     */
    public static void writeCaseNodeData(CaseNodeData nodeData) throws CaseNodeDataException, InterruptedException {
        try {
            CoordinationService coordinationService = CoordinationService.getInstance();
            coordinationService.setNodeData(CoordinationService.CategoryNode.CASES, nodeData.getDirectory().toString(), nodeData.toArray());
        } catch (IOException | CoordinationServiceException ex) {
            throw new CaseNodeDataException(String.format("Failed to write case node data to %s", nodeData.getDirectory().toString().toUpperCase()), ex); //NON-NLS
        }
    }

    /**
     * Upgrades older versions of node data to the current version and writes
     * the data back to the case directory coordination service node.
     *
     * @param nodePath    The case directory coordination service node path.
     * @param oldNodeData The outdated node data.
     *
     * @return The updated node data.
     *
     * @throws CaseNodeDataException If the case neta data file or case
     *                               directory do not exist.
     * @throws CaseMetadataException If the case metadata cannot be read.
     */
    private static CaseNodeData upgradeNodeData(String nodePath, CaseNodeData oldNodeData) throws CaseNodeDataException, CaseMetadataException, ParseException, IOException, CoordinationServiceException, InterruptedException {
        CaseMetadata metadata = getCaseMetadata(nodePath);
        CaseNodeData nodeData = oldNodeData;
        if (oldNodeData.getVersion() == 0) {
            /*
             * Version 0 node data consisted of only the version number and the
             * error occurred flag and was only written when an auto ingest job
             * error occurred. The version 1 fields need to set from the case
             * metadata and the errors occurred flag needs to be carried
             * forward. Note that the last accessed date gets advanced to now,
             * since it is otherwise unknown.
             */
            nodeData = new CaseNodeData(metadata);
            nodeData.setErrorsOccurred(oldNodeData.getErrorsOccurred());
        }
        writeCaseNodeData(nodeData);
        return nodeData;
    }

    /**
     * Gets the metadata for a case.
     *
     * @param nodePath The case directory coordination service node path for the
     *                 case.
     *
     * @return The case metadata.
     *
     * @throws CaseNodeDataException If the case metadata file or the case
     *                               directory does not exist.
     * @throws CaseMetadataException If the case metadata cannot be read.
     */
    private static CaseMetadata getCaseMetadata(String nodePath) throws CaseNodeDataException, CaseMetadataException {
        Path caseDirectoryPath = Paths.get(nodePath);
        File caseDirectory = caseDirectoryPath.toFile();
        if (!caseDirectory.exists()) {
            throw new CaseNodeDataException("Case directory does not exist"); // NON-NLS
        }
        Path metadataFilePath = CaseMetadata.getCaseMetadataFilePath(caseDirectoryPath);
        if (metadataFilePath == null) {
            throw new CaseNodeDataException("Case meta data file does not exist"); // NON-NLS            
        }
        CaseMetadata metadata = new CaseMetadata(metadataFilePath);
        return metadata;
    }

    /**
     * Constucts an object to use for reading and writing case data stored in
     * case directory coordination service nodes from case meta data.
     *
     * @param metadata The case meta data.
     *
     * @throws ParseException If there is an error parsing dates from string
     *                        representations of dates in the meta data.
     */
    private CaseNodeData(CaseMetadata metadata) throws ParseException {
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
     * Constucts an object to use for reading and writing case data stored in
     * case directory coordination service nodes from a byte array read from a
     * case directory coordination service node.
     *
     * @param nodeData The raw bytes received from the coordination service.
     *
     * @throws IOException If there is an error reading the node data.
     */
    private CaseNodeData(byte[] nodeData) throws IOException {
        if (nodeData == null || nodeData.length == 0) {
            throw new IOException(null == nodeData ? "Null node data byte array" : "Zero-length node data byte array");
        }
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(nodeData); DataInputStream inputStream = new DataInputStream(byteStream)) {
            this.version = inputStream.readInt();
            if (this.version > 0) {
                this.errorsOccurred = inputStream.readBoolean();
            } else {
                byte errorsOccurredByte = inputStream.readByte();
                this.errorsOccurred = (errorsOccurredByte < 0);
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
     * Gets the date the case represented by this node data was created.
     *
     * @return The create date.
     */
    public Date getCreateDate() {
        return new Date(this.createDate.getTime());
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
    private byte[] toArray() throws IOException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); DataOutputStream outputStream = new DataOutputStream(byteStream)) {
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

    /**
     * Exception thrown when there is an error reading or writing case node
     * data.
     */
    public static final class CaseNodeDataException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to throw when there is an error reading or
         * writing case node data.
         *
         * @param message The exception message.
         */
        private CaseNodeDataException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to throw when there is an error reading or
         * writing case node data.
         *
         * @param message The exception message.
         * @param cause   The cause of the exception.
         */
        private CaseNodeDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
