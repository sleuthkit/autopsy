/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import javax.lang.model.type.TypeKind;

/**
 * A coordination service node data transfer object for an auto ingest job.
 */
final class AutoIngestJobData implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int NODE_DATA_VERSION = 1;
    private static final int MAX_POSSIBLE_NODE_DATA_SIZE = 131493;
    private static final int DEFAULT_PRIORITY = 0;

    /*
     * Version 0 fields.
     */
    private final boolean coordSvcNodeDataWasSet;
    private int processingStatus;
    private int priority;
    private int numberOfCrashes;
    private long completedDate;
    private boolean errorsOccurred;

    /*
     * Version 1 fields.
     */
    private int version;
    private String deviceId;
    private String caseName;
    private String caseDirectoryPath;
    private long manifestFileDate;
    private String manifestFilePath;
    private String dataSourcePath;
    private byte processingStage;
    private long processingStageStartDate;
    private String processingHost;

    //DLG: Add caseDirectoryPath from AutoIngestJob
    /*
     * DLG: DONE! Rename class to AutoIngestJobData - Add String
     * caseDirectoryPath. Needed to locate case auto ingest log and later, for
     * case deletion
     *
     * DLG: Add String processingStage, long processingStageStartDate, String
     * processingHost fields. These three fields are needed to populate running
     * jobs table; use of auto ingest job data is not enough, because there
     * would be no data until a status event was received by the auto ingest
     * monitor.
     *
     * DLG: Update the AutoIngestManager code that creates ZK nodes for auto ingest
     * jobs to write the new fields described above to new nodes
     *
     * DLG: Update the AutoIngestManager code that publishes auto ingest status
     * events for the current job to update the the processing status fields
     * described above in addition to publishing AutoIngestJobStatusEvents.
     * Probably also need to write this data initially when a jo becomes the
     * current job.
     */
    /**
     * Constructs a coordination service node data data transfer object for an
     * auto ingest manifest from the raw bytes obtained from the coordination
     * service.
     *
     * @param nodeData The raw bytes received from the coordination service.
     */
    AutoIngestJobData(byte[] nodeData) throws AutoIngestJobDataException {
        ByteBuffer buffer = ByteBuffer.wrap(nodeData);
        this.coordSvcNodeDataWasSet = buffer.hasRemaining();
        if (this.coordSvcNodeDataWasSet) {
            this.processingStatus = buffer.getInt();
            this.priority = buffer.getInt();
            this.numberOfCrashes = buffer.getInt();
            this.completedDate = buffer.getLong();
            int errorFlag = buffer.getInt();
            this.errorsOccurred = (1 == errorFlag);
        } else {
            this.processingStatus = ProcessingStatus.PENDING.ordinal();
            this.priority = DEFAULT_PRIORITY;
            this.numberOfCrashes = 0;
            this.completedDate = 0L;
            this.errorsOccurred = false;
        }

        if (buffer.hasRemaining()) {
            /*
             * There are more than 24 bytes in the buffer, so we assume the
             * version is greater than '0'.
             */
            this.version = buffer.getInt();
            if (this.version > NODE_DATA_VERSION) {
                throw new AutoIngestJobDataException(String.format("Node data version %d is not suppored.", this.version));
            }
            this.deviceId = getStringFromBuffer(buffer, TypeKind.BYTE);
            this.caseName = getStringFromBuffer(buffer, TypeKind.BYTE);
            this.caseDirectoryPath = getStringFromBuffer(buffer, TypeKind.SHORT);
            this.manifestFileDate = buffer.getLong();
            this.manifestFilePath = getStringFromBuffer(buffer, TypeKind.SHORT);
            this.dataSourcePath = getStringFromBuffer(buffer, TypeKind.SHORT);
            this.processingStage = buffer.get();
            this.processingStageStartDate = buffer.getLong();
            this.processingHost = getStringFromBuffer(buffer, TypeKind.SHORT);
        } else {
            this.version = 0;
            this.deviceId = "";
            this.caseName = "";
            this.caseDirectoryPath = "";
            this.manifestFileDate = 0L;
            this.manifestFilePath = "";
            this.dataSourcePath = "";
            this.processingStage = (byte)ProcessingStage.PENDING.ordinal();
            this.processingStageStartDate = 0L;
            this.processingHost = "";
        }
    }

    /**
     * Constructs a coordination service node data data transfer object for an
     * auto ingest manifest from values provided by the auto ingest system.
     *
     * @param manifest        The manifest
     * @param status          The processing status of the manifest.
     * @param priority        The priority of the manifest.
     * @param numberOfCrashes The number of times auto ingest jobs for the
     *                        manifest have crashed during processing.
     * @param completedDate   The date the auto ingest job for the manifest was
     *                        completed.
     * @param errorsOccurred  Boolean to determine if errors have occurred.
     */
    AutoIngestJobData(Manifest manifest, ProcessingStatus status, int priority, int numberOfCrashes, Date completedDate, boolean errorOccurred) {
        this.coordSvcNodeDataWasSet = false;
        this.processingStatus = status.ordinal();
        this.priority = priority;
        this.numberOfCrashes = numberOfCrashes;
        this.completedDate = completedDate.getTime();
        this.errorsOccurred = errorOccurred;

        this.version = NODE_DATA_VERSION;
        this.deviceId = manifest.getDeviceId();
        this.caseName = manifest.getCaseName();
        this.caseDirectoryPath = "";  //DLG:
        this.manifestFileDate = manifest.getDateFileCreated().getTime();
        this.manifestFilePath = manifest.getFilePath().toString();
        this.dataSourcePath = manifest.getDataSourcePath().toString();
        this.processingStage = (byte)ProcessingStage.PENDING.ordinal();   //DLG:
        this.processingStageStartDate = 0L; //DLG:
        this.processingHost = ""; //DLG:
    }

    /**
     * Indicates whether or not the coordination service node data was set,
     * i.e., this object was constructed from raw bytes from the ccordination
     * service node for the manifest.
     *
     * @return True or false.
     */
    boolean coordSvcNodeDataWasSet() {
        return this.coordSvcNodeDataWasSet;
    }

    /**
     * Gets the processing status of the manifest
     *
     * @return The processing status of the manifest.
     */
    ProcessingStatus getProcessingStatus() {
        return ProcessingStatus.values()[this.processingStatus];
    }

    /**
     * Sets the processing status of the manifest
     *
     * @param processingSatus The processing status of the manifest.
     */
    void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus.ordinal();
    }

    /**
     * Gets the priority of the manifest.
     *
     * @return The priority of the manifest.
     */
    int getPriority() {
        return this.priority;
    }

    /**
     * Sets the priority of the manifest. A higher number indicates a higheer
     * priority.
     *
     * @param priority The priority of the manifest.
     */
    void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Gets the number of times auto ingest jobs for the manifest have crashed
     * during processing.
     *
     * @return The number of times auto ingest jobs for the manifest have
     *         crashed during processing.
     */
    int getNumberOfCrashes() {
        return this.numberOfCrashes;
    }

    /**
     * Sets the number of times auto ingest jobs for the manifest have crashed
     * during processing.
     *
     * @param numberOfCrashes The number of times auto ingest jobs for the
     *                        manifest have crashed during processing.
     */
    void setNumberOfCrashes(int numberOfCrashes) {
        this.numberOfCrashes = numberOfCrashes;
    }

    /**
     * Gets the date the auto ingest job for the manifest was completed.
     *
     * @return The date the auto ingest job for the manifest was completed. The
     *         epoch (January 1, 1970, 00:00:00 GMT) indicates the date is not
     *         set, i.e., Date.getTime() returns 0L.
     */
    Date getCompletedDate() {
        return new Date(this.completedDate);
    }

    /**
     * Sets the date the auto ingest job for the manifest was completed.
     *
     * @param completedDate The date the auto ingest job for the manifest was
     *                      completed. Use the epoch (January 1, 1970, 00:00:00
     *                      GMT) to indicate the date is not set, i.e., new
     *                      Date(0L).
     */
    void setCompletedDate(Date completedDate) {
        this.completedDate = completedDate.getTime();
    }

    /**
     * Queries whether or not any errors occurred during the processing of the
     * auto ingest job for the manifest.
     *
     * @return True or false.
     */
    boolean getErrorsOccurred() {
        return this.errorsOccurred;
    }

    /**
     * Sets whether or not any errors occurred during the processing of the auto
     * ingest job for the manifest.
     *
     * @param errorsOccurred True or false.
     */
    void setErrorsOccurred(boolean errorsOccurred) {
        this.errorsOccurred = errorsOccurred;
    }

    /**
     * Get the node data version.
     *
     * @return The node data version.
     */
    int getVersion() {
        return this.version;
    }

    /**
     * Set the node data version.
     *
     * @param version The node data version.
     */
    void setVersion(int version) {
        this.version = version;
    }

    /**
     * Get the device ID.
     *
     * @return The device ID.
     */
    String getDeviceId() {
        return this.deviceId;
    }

    /**
     * Set the device ID.
     *
     * @param deviceId The device ID.
     */
    void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Get the case name.
     *
     * @return The case name.
     */
    String getCaseName() {
        return this.caseName;
    }

    /**
     * Set the case name.
     *
     * @param caseName The case name.
     */
    void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    /**
     * Queries whether or not a case directory path has been set for this auto
     * ingest job.
     *
     * @return True or false
     */
    synchronized boolean hasCaseDirectoryPath() {
        return (false == this.caseDirectoryPath.isEmpty());
    }

    /**
     * Sets the path to the case directory of the case associated with this job.
     *
     * @param caseDirectoryPath The path to the case directory.
     */
    synchronized void setCaseDirectoryPath(Path caseDirectoryPath) {
        if(caseDirectoryPath == null) {
            this.caseDirectoryPath = "";
        } else {
            this.caseDirectoryPath = caseDirectoryPath.toString();
        }
    }

    /**
     * Gets the path to the case directory of the case associated with this job,
     * may be null.
     *
     * @return The case directory path or null if the case directory has not
     *         been created yet.
     */
    synchronized Path getCaseDirectoryPath() {
        if (!caseDirectoryPath.isEmpty()) {
            return Paths.get(caseDirectoryPath);
        } else {
            return null;
        }
    }

    /**
     * Gets the date the manifest was created.
     *
     * @return The date the manifest was created. The epoch (January 1, 1970,
     *         00:00:00 GMT) indicates the date is not set, i.e., Date.getTime()
     *         returns 0L.
     */
    Date getManifestFileDate() {
        return new Date(this.manifestFileDate);
    }

    /**
     * Sets the date the manifest was created.
     *
     * @param manifestFileDate The date the manifest was created. Use the epoch
     *                         (January 1, 1970, 00:00:00 GMT) to indicate the
     *                         date is not set, i.e., new Date(0L).
     */
    void setManifestFileDate(Date manifestFileDate) {
        this.manifestFileDate = manifestFileDate.getTime();
    }

    /**
     * Get the manifest file path.
     *
     * @return The manifest file path.
     */
    Path getManifestFilePath() {
        return Paths.get(this.manifestFilePath);
    }

    /**
     * Set the manifest file path.
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
     * Get the data source path.
     *
     * @return The data source path.
     */
    Path getDataSourcePath() {
        return Paths.get(dataSourcePath);
    }

    /**
     * Get the file name portion of the data source path.
     *
     * @return The data source file name.
     */
    public String getDataSourceFileName() {
        return Paths.get(dataSourcePath).getFileName().toString();
    }

    /**
     * Set the data source path.
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
     * Get the processing stage.
     *
     * @return The processing stage.
     */
    ProcessingStage getProcessingStage() {
        return ProcessingStage.values()[this.processingStage];
    }

    /**
     * Set the processing stage.
     *
     * @param processingStage The processing stage.
     */
    void setProcessingStage(ProcessingStage processingStage) {
        this.processingStage = (byte)processingStage.ordinal();
    }

    /**
     * Get the processing stage start date.
     *
     * @return The processing stage start date.
     */
    Date getProcessingStageStartDate() {
        return new Date(this.processingStageStartDate);
    }

    /**
     * Set the processing stage start date.
     *
     * @param processingStageStartDate The processing stage start date.
     */
    void setProcessingStageStartDate(Date processingStageStartDate) {
        this.processingStageStartDate = processingStageStartDate.getTime();
    }

    /**
     * Get the processing host.
     *
     * @return The processing host.
     */
    String getProcessingHost() {
        return this.processingHost;
    }

    /**
     * Set the processing host.
     *
     * @param processingHost The processing host.
     */
    void setProcessingHost(String processingHost) {
        this.processingHost = processingHost;
    }
    
    /**
     * This method will upgrade the node data to the latest version.
     * 
     * @param manifest The manifest.
     * @param caseDirectoryPath The case directory path.
     * @param processingHost The host name.
     * @param processingStage The processing stage.
     */
    public void upgradeNode(Manifest manifest, Path caseDirectoryPath, String processingHost, ProcessingStage processingStage) {
        if(this.version < NODE_DATA_VERSION) {
            this.setVersion(NODE_DATA_VERSION);
            this.setDeviceId(manifest.getDeviceId());
            this.setCaseName(manifest.getCaseName());
            this.setCaseDirectoryPath(caseDirectoryPath);
            this.setManifestFileDate(manifest.getDateFileCreated());
            this.setManifestFilePath(manifest.getFilePath());
            this.setDataSourcePath(manifest.getDataSourcePath());
            this.setProcessingStage(processingStage);
            this.setProcessingStageStartDate(manifest.getDateFileCreated());
            this.setProcessingHost(processingHost);
        }
    }

    /**
     * Gets the node data as raw bytes that can be sent to the coordination
     * service.
     *
     * @return The manifest node data as a byte array.
     */
    byte[] toArray() {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_POSSIBLE_NODE_DATA_SIZE);

        // Write data (compatible with version 0)
        buffer.putInt(this.processingStatus);
        buffer.putInt(this.priority);
        buffer.putInt(this.numberOfCrashes);
        buffer.putLong(this.completedDate);
        buffer.putInt(this.errorsOccurred ? 1 : 0);

        if (this.version > 0) {
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
            putStringIntoBuffer(processingHost, buffer, TypeKind.SHORT);
        }

        // Prepare the array
        byte[] array = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(array, 0, array.length);

        return array;
    }

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

    /**
     * Processing status for the auto ingest job for the manifest.
     */
    enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        DELETED
    }

    enum ProcessingStage {

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
        CANCELING_MODULE("Canceling module"),
        CANCELING("Canceling"),
        COMPLETED("Completed");

        private final String displayText;

        private ProcessingStage(String displayText) {
            this.displayText = displayText;
        }

        String getDisplayText() {
            return displayText;
        }

    }

}
