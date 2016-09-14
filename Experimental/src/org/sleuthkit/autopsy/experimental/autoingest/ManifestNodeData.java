/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.nio.ByteBuffer;
import java.util.Date;

/**
 * A coordination service node data transfer object for an auto ingest job
 * manifest. The data include: processing status, priority, the number of times
 * the auto ingest job for the manifest has crashed during processing, and the
 * date the auto ingest job for the manifest was completed.
 */
final class ManifestNodeData {

    private static final int DEFAULT_PRIORITY = 0;
    private final boolean coordSvcNodeDataWasSet;
    private ProcessingStatus status;
    private int priority;
    private int numberOfCrashes;
    private long completedDate;
    private boolean errorsOccurred;

    /**
     * Constructs a coordination service node data data transfer object for an
     * auto ingest manifest from the raw bytes obtained from the coordination
     * service.
     *
     * @param nodeData The raw bytes received from the coordination service.
     */
    ManifestNodeData(byte[] nodeData) {
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
    }

    /**
     * Constructs a coordination service node data data transfer object for an
     * auto ingest manifest from values provided by the auto ingest system.
     *
     * @param status          The processing status of the manifest.
     * @param priority        The priority of the manifest.
     * @param numberOfCrashes The number of times auto ingest jobs for the
     *                        manifest have crashed during processing.
     * @param completedDate   The date the auto ingest job for the manifest was
     *                        completed.
     */
    ManifestNodeData(ProcessingStatus status, int priority, int numberOfCrashes, Date completedDate, boolean errorOccurred) {
        this.coordSvcNodeDataWasSet = false;
        this.status = status;
        this.priority = priority;
        this.numberOfCrashes = numberOfCrashes;
        this.completedDate = completedDate.getTime();
        this.errorsOccurred = errorOccurred;
    }

    /**
     * Indicates whether or not the coordination service node data was set,
     * i.e., this object was constructed from raw bytes from the ccordination
     * service node for the manifest.
     *
     * @return True or false.
     */
    // RJCTODO: This is confusing, consider changing the API so that the use case is to
    // check the length of the node data from the coordination service before 
    // constructing an instance of this object. That would be much more clear!
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
     * Gets the node data as raw bytes that can be sent to the coordination
     * service.
     *
     * @return The manifest node data as a byte array.
     */
    byte[] toArray() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 4 + Long.BYTES);
        buffer.putInt(this.status.ordinal());
        buffer.putInt(this.priority);
        buffer.putInt(this.numberOfCrashes);
        buffer.putLong(this.completedDate);
        buffer.putInt(this.errorsOccurred ? 1 : 0);
        return buffer.array();
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
