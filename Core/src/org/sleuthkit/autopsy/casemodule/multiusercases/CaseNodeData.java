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
 * Case data stored in a case directory coordination service node.
 */
public final class CaseNodeData {

    private static final int MAJOR_VERSION = 2;
    private static final int MINOR_VERSION = 0;
    private static final Logger logger = Logger.getLogger(CaseNodeData.class.getName());

    /*
     * Version 0 fields. Note that version 0 node data was only written to the
     * coordination service node if an auto ingest job error occurred.
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

    /*
     * Version 2 fields.
     */
    private int minorVersion;

    /**
     * Creates case node data from the metadata for a case and writes it to the
     * appropriate case directory coordination service node, which must already
     * exist.
     *
     * @param metadata The case metadata.
     *
     * @return The case node data that was written to the coordination service
     *         node.
     *
     * @throws CaseNodeDataException If there is an error creating or writing
     *                               the case node data.
     * @throws InterruptedException  If the current thread is interrupted while
     *                               waiting for the coordination service.
     */
    public static CaseNodeData createCaseNodeData(final CaseMetadata metadata) throws CaseNodeDataException, InterruptedException {
        try {
            final CaseNodeData nodeData = new CaseNodeData(metadata);
            CoordinationService.getInstance().setNodeData(CoordinationService.CategoryNode.CASES, nodeData.getDirectory().toString(), nodeData.toArray());
            return nodeData;

        } catch (ParseException | IOException | CoordinationServiceException ex) {
            throw new CaseNodeDataException(String.format("Error creating case node data for coordination service node with path %s", metadata.getCaseDirectory().toUpperCase()), ex); //NON-NLS
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
     *
     * @throws CaseNodeDataException If there is an error reading or writing the
     *                               case node data.
     * @throws InterruptedException  If the current thread is interrupted while
     *                               waiting for the coordination service.
     */
    public static CaseNodeData readCaseNodeData(String nodePath) throws CaseNodeDataException, InterruptedException {
        try {
            CaseNodeData nodeData;
            final byte[] nodeBytes = CoordinationService.getInstance().getNodeData(CoordinationService.CategoryNode.CASES, nodePath);
            if (nodeBytes != null && nodeBytes.length > 0) {
                try {
                    nodeData = new CaseNodeData(nodeBytes);
                } catch (IOException ex) {
                    /*
                     * The existing case node data is corrupted.
                     */
                    logger.log(Level.WARNING, String.format("Error reading node data for coordination service node with path %s, will attempt to replace it", nodePath.toUpperCase()), ex); //NON-NLS
                    final CaseMetadata metadata = getCaseMetadata(nodePath);
                    nodeData = createCaseNodeData(metadata);
                    logger.log(Level.INFO, String.format("Replaced corrupt node data for coordination service node with path %s", nodePath.toUpperCase())); //NON-NLS
                }
            } else {
                /*
                 * The case node data is missing. Version 0 node data was only
                 * written to the coordination service node if an auto ingest
                 * job error occurred.
                 */
                logger.log(Level.INFO, String.format("Missing node data for coordination service node with path %s, will attempt to create it", nodePath.toUpperCase())); //NON-NLS
                final CaseMetadata metadata = getCaseMetadata(nodePath);
                nodeData = createCaseNodeData(metadata);
                logger.log(Level.INFO, String.format("Created node data for coordination service node with path %s", nodePath.toUpperCase())); //NON-NLS
            }
            if (nodeData.getVersion() < CaseNodeData.MAJOR_VERSION) {
                nodeData = upgradeCaseNodeData(nodePath, nodeData);
            }
            return nodeData;

        } catch (CaseNodeDataException | CaseMetadataException | ParseException | IOException | CoordinationServiceException ex) {
            throw new CaseNodeDataException(String.format("Error reading/writing node data coordination service node with path %s", nodePath.toUpperCase()), ex); //NON-NLS
        }
    }

    /**
     * Writes case data to a case directory coordination service node. Obtain
     * the case data to be updated and written by calling createCaseNodeData()
     * or readCaseNodeData().
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
            CoordinationService.getInstance().setNodeData(CoordinationService.CategoryNode.CASES, nodeData.getDirectory().toString(), nodeData.toArray());

        } catch (IOException | CoordinationServiceException ex) {
            throw new CaseNodeDataException(String.format("Error writing node data coordination service node with path %s", nodeData.getDirectory().toString().toUpperCase()), ex); //NON-NLS
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
     * @throws CaseNodeDataException If the case meta data file or case
     *                               directory do not exist.
     * @throws CaseMetadataException If the case metadata cannot be read.
     */
    private static CaseNodeData upgradeCaseNodeData(String nodePath, CaseNodeData oldNodeData) throws CaseNodeDataException, CaseMetadataException, ParseException, IOException, CoordinationServiceException, InterruptedException {
        CaseNodeData nodeData;
        switch (oldNodeData.getVersion()) {
            case 0:
                /*
                 * Version 0 node data consisted of only the version number and
                 * the errors occurred flag and was only written when an auto
                 * ingest job error occurred. To upgrade from version 0, the
                 * version 1 fields need to be set from the case metadata and
                 * the errors occurred flag needs to be carried forward. Note
                 * that the last accessed date gets advanced to now, since it is
                 * otherwise unknown.
                 */
                final CaseMetadata metadata = getCaseMetadata(nodePath);
                nodeData = new CaseNodeData(metadata);
                nodeData.setErrorsOccurred(oldNodeData.getErrorsOccurred());
                break;
            case 1:
                /*
                 * Version 1 node data did not have a minor version number
                 * field.
                 */
                oldNodeData.setMinorVersion(MINOR_VERSION);
                nodeData = oldNodeData;
                break;
            default:
                nodeData = oldNodeData;
                break;
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
        final Path caseDirectoryPath = Paths.get(nodePath);
        final File caseDirectory = caseDirectoryPath.toFile();
        if (!caseDirectory.exists()) {
            throw new CaseNodeDataException("Case directory does not exist"); // NON-NLS
        }
        final Path metadataFilePath = CaseMetadata.getCaseMetadataFilePath(caseDirectoryPath);
        if (metadataFilePath == null) {
            throw new CaseNodeDataException("Case meta data file does not exist"); // NON-NLS            
        }
        return new CaseMetadata(metadataFilePath);
    }

    /**
     * Uses case metadata to construct the case data to store in a case
     * directory coordination service node.
     *
     * @param metadata The case meta data.
     *
     * @throws ParseException If there is an error parsing dates from string
     *                        representations of dates in the meta data.
     */
    private CaseNodeData(CaseMetadata metadata) throws ParseException {
        this.version = MAJOR_VERSION;
        this.errorsOccurred = false;
        this.directory = Paths.get(metadata.getCaseDirectory());
        this.createDate = CaseMetadata.getDateFormat().parse(metadata.getCreatedDate());
        this.lastAccessDate = new Date();
        this.name = metadata.getCaseName();
        this.displayName = metadata.getCaseDisplayName();
        this.deletedItemFlags = 0;
        this.minorVersion = MINOR_VERSION;
    }

    /**
     * Uses the raw bytes from a case directory coordination service node to
     * construct a case node data object.
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
            if (this.version == 1) {
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
            if (this.version > 1) {
                this.minorVersion = inputStream.readInt();
            }
        }
    }

    /**
     * Gets the version number of this node data.
     *
     * @return The version number.
     */
    private int getVersion() {
        return this.version;
    }

    /**
     * Sets the minor version number of this node data.
     *
     * @param minorVersion The version number.
     */
    private void setMinorVersion(int minorVersion) {
        this.minorVersion = minorVersion;
    }

    /**
     * Gets whether or not any errors occurred during the processing of any auto
     * ingest job for the case.
     *
     * @return True or false.
     */
    public boolean getErrorsOccurred() {
        return this.errorsOccurred;
    }

    /**
     * Sets whether or not any errors occurred during the processing of any auto
     * ingest job for the case.
     *
     * @param errorsOccurred True or false.
     */
    public void setErrorsOccurred(boolean errorsOccurred) {
        this.errorsOccurred = errorsOccurred;
    }

    /**
     * Gets the path of the case directory.
     *
     * @return The case directory path.
     */
    public Path getDirectory() {
        return this.directory;
    }

    /**
     * Gets the date the case was created.
     *
     * @return The create date.
     */
    public Date getCreateDate() {
        return new Date(this.createDate.getTime());
    }

    /**
     * Gets the date the case was last accessed.
     *
     * @return The last access date.
     */
    public Date getLastAccessDate() {
        return new Date(this.lastAccessDate.getTime());
    }

    /**
     * Sets the date the case was last accessed.
     *
     * @param lastAccessDate The last access date.
     */
    public void setLastAccessDate(Date lastAccessDate) {
        this.lastAccessDate = new Date(lastAccessDate.getTime());
    }

    /**
     * Gets the unique and immutable name of the case.
     *
     * @return The case name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Gets the display name of the case.
     *
     * @return The case display name.
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Sets the display name of the case.
     *
     * @param displayName The case display name.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Checks whether a given deleted item flag is set for the case.
     *
     * @param flag The flag to check.
     *
     * @return True or false.
     */
    public boolean isDeletedFlagSet(DeletedFlags flag) {
        return (this.deletedItemFlags & flag.getValue()) == flag.getValue();
    }

    /**
     * Sets a given deleted item flag.
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
     * @throws IOException If there is an error writing the node data to the
     *                     array.
     */
    private byte[] toArray() throws IOException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); DataOutputStream outputStream = new DataOutputStream(byteStream)) {
            outputStream.writeInt(this.version);
            outputStream.writeByte((byte) (this.errorsOccurred ? 0x80 : 0));
            outputStream.writeUTF(this.directory.toString());
            outputStream.writeLong(this.createDate.getTime());
            outputStream.writeLong(this.lastAccessDate.getTime());
            outputStream.writeUTF(this.name);
            outputStream.writeUTF(this.displayName);
            outputStream.writeShort(this.deletedItemFlags);
            outputStream.writeInt(this.minorVersion);
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
