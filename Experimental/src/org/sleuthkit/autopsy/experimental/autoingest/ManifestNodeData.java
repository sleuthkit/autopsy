/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
 * A coordination service node data transfer object for an auto ingest job
 * manifest. The data include: processing status, priority, the number of times
 * the auto ingest job for the manifest has crashed during processing, and the
 * date the auto ingest job for the manifest was completed.
 */
final class ManifestNodeData implements Serializable {

    private static final int NODE_DATA_VERSION = 2;
    private static final int MAX_POSSIBLE_NODE_DATA_SIZE = 65831;
    
    private static final int DEFAULT_PRIORITY = 0;
    private final boolean coordSvcNodeDataWasSet;
    
    private ProcessingStatus status;
    private int priority;
    private int numberOfCrashes;
    private long completedDate;
    private boolean errorsOccurred;
    
    // These are not used by version '1' nodes.
    private int version;
    private String deviceId;
    private String caseName;
    private long manifestFileDate;
    private String manifestFilePath;
    private String dataSourcePath;
    //DLG: Add caseDirectoryPath from AutoIngestJob

    /**
     * Constructs a coordination service node data data transfer object for an
     * auto ingest manifest from the raw bytes obtained from the coordination
     * service.
     *
     * @param nodeData The raw bytes received from the coordination service.
     */
    ManifestNodeData(byte[] nodeData) throws ManifestNodeDataException {
        ByteBuffer buffer = ByteBuffer.wrap(nodeData);
        this.coordSvcNodeDataWasSet = buffer.hasRemaining();
        if (this.coordSvcNodeDataWasSet) {
            int rawStatus = buffer.getInt();
            if (ProcessingStatus.PENDING.ordinal() == rawStatus) {
                this.status = ProcessingStatus.PENDING;
            } else if (ProcessingStatus.PROCESSING.ordinal() == rawStatus) {
                this.status = ProcessingStatus.PROCESSING;
            } else if (ProcessingStatus.COMPLETED.ordinal() == rawStatus) {
                this.status = ProcessingStatus.COMPLETED;
            }else if (ProcessingStatus.DELETED.ordinal() == rawStatus) {
                this.status = ProcessingStatus.DELETED;
            }
            this.priority = buffer.getInt();
            this.numberOfCrashes = buffer.getInt();
            this.completedDate = buffer.getLong();
            int errorFlag = buffer.getInt();
            this.errorsOccurred = (1 == errorFlag);
        } else {
            this.status = ProcessingStatus.PENDING;
            this.priority = DEFAULT_PRIORITY;
            this.numberOfCrashes = 0;
            this.completedDate = 0L;
            this.errorsOccurred = false;
        }
        
        if(buffer.hasRemaining()) {
            // Version is greater than 1
            this.version = buffer.getInt();
            if(this.version > NODE_DATA_VERSION) {
                throw new ManifestNodeDataException(String.format(
                        "Node data version %d is not suppored.",
                        this.version));
            }
            this.deviceId = getStringFromBuffer(buffer, TypeKind.BYTE);
            this.caseName = getStringFromBuffer(buffer, TypeKind.BYTE);
            this.manifestFileDate = buffer.getLong();
            this.manifestFilePath = getStringFromBuffer(buffer, TypeKind.SHORT);
            this.dataSourcePath = getStringFromBuffer(buffer, TypeKind.SHORT);
        }
        else {
            this.version = 1;
            this.deviceId = "";
            this.caseName = "";
            this.manifestFileDate = 0L;
            this.manifestFilePath = "";
            this.dataSourcePath = "";
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
    ManifestNodeData(Manifest manifest, ProcessingStatus status, int priority, int numberOfCrashes, Date completedDate, boolean errorOccurred) {
        this.coordSvcNodeDataWasSet = false;
        this.status = status;
        this.priority = priority;
        this.numberOfCrashes = numberOfCrashes;
        this.completedDate = completedDate.getTime();
        this.errorsOccurred = errorOccurred;
        
        this.version = NODE_DATA_VERSION;
        this.deviceId = manifest.getDeviceId();
        this.caseName = manifest.getCaseName();
        this.manifestFileDate = manifest.getDateFileCreated().getTime();
        this.manifestFilePath = manifest.getFilePath().toString();
        this.dataSourcePath = manifest.getDataSourcePath().toString();
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
    ProcessingStatus getStatus() {
        return this.status;
    }

    /**
     * Sets the processing status of the manifest
     *
     * @param status The processing status of the manifest.
     */
    void setStatus(ProcessingStatus status) {
        this.status = status;
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
     * Gets the node data as raw bytes that can be sent to the coordination
     * service.
     *
     * @return The manifest node data as a byte array.
     */
    byte[] toArray() {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_POSSIBLE_NODE_DATA_SIZE);
        
        // Write data (compatible with version 0)
        buffer.putInt(this.status.ordinal());
        buffer.putInt(this.priority);
        buffer.putInt(this.numberOfCrashes);
        buffer.putLong(this.completedDate);
        buffer.putInt(this.errorsOccurred ? 1 : 0);
        
        if(this.version > 0) {
            // Write version
            buffer.putInt(this.version);

            // Write data
            putStringIntoBuffer(deviceId, buffer, TypeKind.BYTE);
            putStringIntoBuffer(caseName, buffer, TypeKind.BYTE);
            buffer.putLong(this.manifestFileDate);
            putStringIntoBuffer(manifestFilePath, buffer, TypeKind.SHORT);
            putStringIntoBuffer(dataSourcePath, buffer, TypeKind.SHORT);
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
        
        switch(lengthType) {
            case BYTE:
                length = buffer.get();
                break;
            case SHORT:
                length = buffer.getShort();
                break;
        }
        
        if(length > 0) {
            byte[] array = new byte[length];
            buffer.get(array, 0, length);
            output = new String(array);
        }
        
        return output;
    }
    
    private void putStringIntoBuffer(String stringValue, ByteBuffer buffer, TypeKind lengthType) {
        switch(lengthType) {
            case BYTE:
                buffer.put((byte)stringValue.length());
                break;
            case SHORT:
                buffer.putShort((short)stringValue.length());
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

}
