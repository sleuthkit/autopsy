/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package zookeepernodemigration;

import java.io.Serializable;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import javax.lang.model.type.TypeKind;

/**
 * An object that converts auto ingest job data for an auto ingest job
 * coordination service node to and from byte arrays.
 */
final class AutoIngestJobNodeData {

    private static final int CURRENT_VERSION = 2;
    private static final int DEFAULT_PRIORITY = 0;

    /*
     * This number is the sum of each piece of data, based on it's type. For the
     * types boolean, int, and long, values 1, 4, and 8 will be added
     * respectively. For String objects, the length of the string, plus either a
     * byte or short respesenting the length of the string, will be added.
     *
     * This field is used to set the size of the buffer during the byte array
     * creation in the 'toArray()' method. Since the final size of the array
     * isn't immediately known at the time of creation, this number is used to
     * create an array as large as could possibly be needed to store all the
     * data. This avoids the need to continuously enlarge the buffer. Once the
     * buffer has all the necessary data, it will be resized as appropriate.
     */
    private static final int MAX_POSSIBLE_NODE_DATA_SIZE = 131637;

    /*
     * Version 0 fields.
     */
    private int processingStatus;
    private int priority;
    private int numberOfCrashes;
    private long completedDate;
    private boolean errorsOccurred;

    /*
     * Version 1 fields.
     */
    private int version;
    private String manifestFilePath;    // 'short' length used in byte array
    private long manifestFileDate;
    private String caseName;            // 'byte' length used in byte array
    private String deviceId;            // 'byte' length used in byte array
    private String dataSourcePath;      // 'short' length used in byte array
    private String caseDirectoryPath;   // 'short' length used in byte array
    private String processingHostName;  // 'short' length used in byte array
    private byte processingStage;
    private long processingStageStartDate;
    private String processingStageDetailsDescription;   // 'byte' length used in byte array
    private long processingStageDetailsStartDate;
    
    /*
     * Version 2 fields.
     */
    private long dataSourceSize;
    
    /**
     * Processing statuses for an auto ingest job.
     */
    enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        DELETED
    }
    
    /**
     * Processing stages for an auto ingest job.
     */
    enum Stage {

        PENDING("Pending"),
        STARTING("Starting"),
        UPDATING_SHARED_CONFIG("Updating shared configuration"),
        CHECKING_SERVICES("Checking services"),
        OPENING_CASE("Opening case"),
        IDENTIFYING_DATA_SOURCE("Identifying data source type"),
        ADDING_DATA_SOURCE("Adding data source"),
        ANALYZING_DATA_SOURCE("Analyzing data source"),
        ANALYZING_FILES("Analyzing files"),
        EXPORTING_FILES("Exporting files"),
        CANCELLING_MODULE("Cancelling module"),
        CANCELLING("Cancelling"),
        COMPLETED("Completed");

        private final String displayText;

        private Stage(String displayText) {
            this.displayText = displayText;
        }

        String getDisplayText() {
            return displayText;
        }

    }
    
    
    /**
     * Processing stage details for an auto ingest job.
     */
    static final class StageDetails implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String description;
        private final Date startDate;

        StageDetails(String description, Date startDate) {
            this.description = description;
            this.startDate = startDate;
        }

        String getDescription() {
            return this.description;
        }

        Date getStartDate() {
            return new Date(this.startDate.getTime());
        }

    }

    /**
     * Gets the current version of the auto ingest job coordination service node
     * data.
     *
     * @return The version number.
     */
    static int getCurrentVersion() {
        return AutoIngestJobNodeData.CURRENT_VERSION;
    }

    /**
     * Uses a coordination service node data to construct an object that
     * converts auto ingest job data for an auto ingest job coordination service
     * node to and from byte arrays.
     *
     * @param nodeData The raw bytes received from the coordination service.
     */
    AutoIngestJobNodeData(byte[] nodeData) throws InvalidDataException {
        if (null == nodeData || nodeData.length == 0) {
            throw new InvalidDataException(null == nodeData ? "Null nodeData byte array" : "Zero-length nodeData byte array");
        }

        /*
         * Set default values for all fields.
         */
        this.processingStatus = ProcessingStatus.PENDING.ordinal();
        this.priority = DEFAULT_PRIORITY;
        this.numberOfCrashes = 0;
        this.completedDate = 0L;
        this.errorsOccurred = false;
        this.version = 0;
        this.manifestFilePath = "";
        this.manifestFileDate = 0L;
        this.caseName = "";
        this.deviceId = "";
        this.dataSourcePath = "";
        this.caseDirectoryPath = "";
        this.processingHostName = "";
        this.processingStage = (byte) Stage.PENDING.ordinal();
        this.processingStageStartDate = 0L;
        this.processingStageDetailsDescription = "";
        this.processingStageDetailsStartDate = 0L;
        this.dataSourceSize = 0L;

        /*
         * Get fields from node data.
         */
        ByteBuffer buffer = ByteBuffer.wrap(nodeData);
        try {
            if (buffer.hasRemaining()) {
                /*
                 * Get version 0 fields.
                 */
                this.processingStatus = buffer.getInt();
                this.priority = buffer.getInt();
                this.numberOfCrashes = buffer.getInt();
                this.completedDate = buffer.getLong();
                int errorFlag = buffer.getInt();
                this.errorsOccurred = (1 == errorFlag);
            }

            if (buffer.hasRemaining()) {
                /*
                 * Get version 1 fields.
                 */
                this.version = buffer.getInt();
                this.deviceId = getStringFromBuffer(buffer, TypeKind.BYTE);
                this.caseName = getStringFromBuffer(buffer, TypeKind.BYTE);
                this.caseDirectoryPath = getStringFromBuffer(buffer, TypeKind.SHORT);
                this.manifestFileDate = buffer.getLong();
                this.manifestFilePath = getStringFromBuffer(buffer, TypeKind.SHORT);
                this.dataSourcePath = getStringFromBuffer(buffer, TypeKind.SHORT);
                this.processingStage = buffer.get();
                this.processingStageStartDate = buffer.getLong();
                this.processingStageDetailsDescription = getStringFromBuffer(buffer, TypeKind.BYTE);
                this.processingStageDetailsStartDate = buffer.getLong();
                this.processingHostName = getStringFromBuffer(buffer, TypeKind.SHORT);
            }

            if (buffer.hasRemaining()) {
                /*
                 * Get version 2 fields.
                 */
                this.dataSourceSize = buffer.getLong();
            }

        } catch (BufferUnderflowException ex) {
            throw new InvalidDataException("Node data is incomplete", ex);
        }
    }

    /**
     * Gets the processing status of the job.
     *
     * @return The processing status.
     */
    ProcessingStatus getProcessingStatus() {
        return ProcessingStatus.values()[this.processingStatus];
    }

    /**
     * Sets the processing status of the job.
     *
     * @param processingSatus The processing status.
     */
    void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus.ordinal();
    }

    /**
     * Gets the priority of the job.
     *
     * @return The priority.
     */
    int getPriority() {
        return this.priority;
    }

    /**
     * Sets the priority of the job. A higher number indicates a higheer
     * priority.
     *
     * @param priority The priority.
     */
    void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Gets the number of times the job has crashed during processing.
     *
     * @return The number of crashes.
     */
    int getNumberOfCrashes() {
        return this.numberOfCrashes;
    }

    /**
     * Sets the number of times the job has crashed during processing.
     *
     * @param numberOfCrashes The number of crashes.
     */
    void setNumberOfCrashes(int numberOfCrashes) {
        this.numberOfCrashes = numberOfCrashes;
    }

    /**
     * Gets the date the job was completed. A completion date equal to the epoch
     * (January 1, 1970, 00:00:00 GMT), i.e., Date.getTime() returns 0L,
     * indicates the job has not been completed.
     *
     * @return The job completion date.
     */
    Date getCompletedDate() {
        return new Date(this.completedDate);
    }

    /**
     * Sets the date the job was completed. A completion date equal to the epoch
     * (January 1, 1970, 00:00:00 GMT), i.e., Date.getTime() returns 0L,
     * indicates the job has not been completed.
     *
     * @param completedDate The job completion date.
     */
    void setCompletedDate(Date completedDate) {
        this.completedDate = completedDate.getTime();
    }

    /**
     * Gets whether or not any errors occurred during the processing of the job.
     *
     * @return True or false.
     */
    boolean getErrorsOccurred() {
        return this.errorsOccurred;
    }

    /**
     * Sets whether or not any errors occurred during the processing of job.
     *
     * @param errorsOccurred True or false.
     */
    void setErrorsOccurred(boolean errorsOccurred) {
        this.errorsOccurred = errorsOccurred;
    }

    /**
     * Gets the node data version number.
     *
     * @return The version number.
     */
    int getVersion() {
        return this.version;
    }

    /**
     * Gets the device ID of the device associated with the data source for the
     * job.
     *
     * @return The device ID.
     */
    String getDeviceId() {
        return this.deviceId;
    }

    /**
     * Sets the device ID of the device associated with the data source for the
     * job.
     *
     * @param deviceId The device ID.
     */
    void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Gets the case name.
     *
     * @return The case name.
     */
    String getCaseName() {
        return this.caseName;
    }

    /**
     * Sets the case name.
     *
     * @param caseName The case name.
     */
    void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    /**
     * Sets the path to the case directory of the case associated with the job.
     *
     * @param caseDirectoryPath The path to the case directory.
     */
    synchronized void setCaseDirectoryPath(Path caseDirectoryPath) {
        if (caseDirectoryPath == null) {
            this.caseDirectoryPath = "";
        } else {
            this.caseDirectoryPath = caseDirectoryPath.toString();
        }
    }

    /**
     * Gets the path to the case directory of the case associated with the job.
     *
     * @return The case directory path or an empty string path if the case
     *         directory has not been created yet.
     */
    synchronized Path getCaseDirectoryPath() {
        if (!caseDirectoryPath.isEmpty()) {
            return Paths.get(caseDirectoryPath);
        } else {
            return Paths.get("");
        }
    }

    /**
     * Gets the date the manifest was created.
     *
     * @return The date the manifest was created.
     */
    Date getManifestFileDate() {
        return new Date(this.manifestFileDate);
    }

    /**
     * Sets the date the manifest was created.
     *
     * @param manifestFileDate The date the manifest was created.
     */
    void setManifestFileDate(Date manifestFileDate) {
        this.manifestFileDate = manifestFileDate.getTime();
    }

    /**
     * Gets the manifest file path.
     *
     * @return The manifest file path.
     */
    Path getManifestFilePath() {
        return Paths.get(this.manifestFilePath);
    }

    /**
     * Sets the manifest file path.
     *
     * @param manifestFilePath The manifest file path.
     */
    void setManifestFilePath(Path manifestFilePath) {
        if (manifestFilePath != null) {
            this.manifestFilePath = manifestFilePath.toString();
        } else {
            this.manifestFilePath = "";
        }
    }

    /**
     * Gets the path of the data source for the job.
     *
     * @return The data source path.
     */
    Path getDataSourcePath() {
        return Paths.get(dataSourcePath);
    }

    /**
     * Get the file name portion of the path of the data source for the job.
     *
     * @return The data source file name.
     */
    public String getDataSourceFileName() {
        return Paths.get(dataSourcePath).getFileName().toString();
    }

    /**
     * Sets the path of the data source for the job.
     *
     * @param dataSourcePath The data source path.
     */
    void setDataSourcePath(Path dataSourcePath) {
        if (dataSourcePath != null) {
            this.dataSourcePath = dataSourcePath.toString();
        } else {
            this.dataSourcePath = "";
        }
    }

    /**
     * Get the processing stage of the job.
     *
     * @return The processing stage.
     */
    Stage getProcessingStage() {
        return Stage.values()[this.processingStage];
    }

    /**
     * Sets the processing stage job.
     *
     * @param processingStage The processing stage.
     */
    void setProcessingStage(Stage processingStage) {
        this.processingStage = (byte) processingStage.ordinal();
    }

    /**
     * Gets the processing stage start date.
     *
     * @return The processing stage start date.
     */
    Date getProcessingStageStartDate() {
        return new Date(this.processingStageStartDate);
    }

    /**
     * Sets the processing stage start date.
     *
     * @param processingStageStartDate The processing stage start date.
     */
    void setProcessingStageStartDate(Date processingStageStartDate) {
        this.processingStageStartDate = processingStageStartDate.getTime();
    }

    /**
     * Get the processing stage details.
     *
     * @return A processing stage details object.
     */
    StageDetails getProcessingStageDetails() {
        return new StageDetails(this.processingStageDetailsDescription, new Date(this.processingStageDetailsStartDate));
    }

    /**
     * Sets the details of the current processing stage.
     *
     * @param stageDetails A stage details object.
     */
    void setProcessingStageDetails(StageDetails stageDetails) {
        this.processingStageDetailsDescription = stageDetails.getDescription();
        this.processingStageDetailsStartDate = stageDetails.getStartDate().getTime();
    }

    /**
     * Gets the processing host name, may be the empty string.
     *
     * @return The processing host. The empty string if the job is not currently
     *         being processed.
     */
    String getProcessingHostName() {
        return this.processingHostName;
    }

    /**
     * Sets the processing host name. May be the empty string.
     *
     * @param processingHost The processing host name. The empty string if the
     *                       job is not currently being processed.
     */
    void setProcessingHostName(String processingHost) {
        this.processingHostName = processingHost;
    }
    
    /**
     * Gets the total size of the data source.
     * 
     * @return The data source size.
     */
    long getDataSourceSize() {
        return this.dataSourceSize;
    }
    
    /**
     * Sets the total size of the data source.
     * 
     * @param dataSourceSize The data source size.
     */
    void setDataSourceSize(long dataSourceSize) {
        this.dataSourceSize = dataSourceSize;
    }

    /**
     * Gets the node data as a byte array that can be sent to the coordination
     * service.
     *
     * @return The node data as a byte array.
     */
    byte[] toArray() {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_POSSIBLE_NODE_DATA_SIZE);

        // Write data (compatible with version 0)
        buffer.putInt(this.processingStatus);
        buffer.putInt(this.priority);
        buffer.putInt(this.numberOfCrashes);
        buffer.putLong(this.completedDate);
        buffer.putInt(this.errorsOccurred ? 1 : 0);

        if (this.version >= 1) {
            // Write version
            buffer.putInt(this.version);

            // Write data
            putStringIntoBuffer(deviceId, buffer, TypeKind.BYTE);
            putStringIntoBuffer(caseName, buffer, TypeKind.BYTE);
            putStringIntoBuffer(caseDirectoryPath, buffer, TypeKind.SHORT);
            buffer.putLong(this.manifestFileDate);
            putStringIntoBuffer(manifestFilePath, buffer, TypeKind.SHORT);
            putStringIntoBuffer(dataSourcePath, buffer, TypeKind.SHORT);
            buffer.put(this.processingStage);
            buffer.putLong(this.processingStageStartDate);
            putStringIntoBuffer(this.processingStageDetailsDescription, buffer, TypeKind.BYTE);
            buffer.putLong(this.processingStageDetailsStartDate);
            putStringIntoBuffer(processingHostName, buffer, TypeKind.SHORT);
            
            if (this.version >= 2) {
                buffer.putLong(this.dataSourceSize);
            }
        }

        // Prepare the array
        byte[] array = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(array, 0, array.length);

        return array;
    }

    /**
     * This method retrieves a string from a given buffer. Depending on the type
     * specified, either a 'byte' or a 'short' will first be read out of the
     * buffer which gives the length of the string so it can be properly parsed.
     *
     * @param buffer     The buffer from which the string will be read.
     * @param lengthType The size of the length data.
     *
     * @return The string read from the buffer.
     */
    private String getStringFromBuffer(ByteBuffer buffer, TypeKind lengthType) {
        int length = 0;
        String output = "";

        switch (lengthType) {
            case BYTE:
                length = buffer.get();
                break;
            case SHORT:
                length = buffer.getShort();
                break;
        }

        if (length > 0) {
            byte[] array = new byte[length];
            buffer.get(array, 0, length);
            output = new String(array);
        }

        return output;
    }

    /**
     * This method puts a given string into a given buffer. Depending on the
     * type specified, either a 'byte' or a 'short' will be inserted prior to
     * the string which gives the length of the string so it can be properly
     * parsed.
     *
     * @param stringValue The string to write to the buffer.
     * @param buffer      The buffer to which the string will be written.
     * @param lengthType  The size of the length data.
     */
    private void putStringIntoBuffer(String stringValue, ByteBuffer buffer, TypeKind lengthType) {
        switch (lengthType) {
            case BYTE:
                buffer.put((byte) stringValue.length());
                break;
            case SHORT:
                buffer.putShort((short) stringValue.length());
                break;
        }

        buffer.put(stringValue.getBytes());
    }

    final static class InvalidDataException extends Exception {

        private static final long serialVersionUID = 1L;

        private InvalidDataException(String message) {
            super(message);
        }

        private InvalidDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
