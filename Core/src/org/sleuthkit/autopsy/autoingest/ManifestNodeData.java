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
package org.sleuthkit.autopsy.autoingest;

import java.nio.ByteBuffer;

/**
 * RJCTODO
 */
// RJCTODO: Consider making this encapsulate the locking as well, and to set the data as well
final class ManifestNodeData {

    enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
    }

    private static final int DEFAULT_PRIORITY = 0;
    private final boolean nodeDataIsSet;
    private ProcessingStatus status;
    private int priority;
    private int numberOfCrashes;

    /**
     * RJCTODO
     *
     * @param nodeData
     */
    ManifestNodeData(byte[] nodeData) {
        ByteBuffer buffer = ByteBuffer.wrap(nodeData);
        this.nodeDataIsSet = buffer.hasRemaining();
        if (this.nodeDataIsSet) {
            int rawStatus = buffer.getInt();
            if (ProcessingStatus.PENDING.ordinal() == rawStatus) {
                this.status = ProcessingStatus.PENDING;
            } else if (ProcessingStatus.PROCESSING.ordinal() == rawStatus) {
                this.status = ProcessingStatus.PROCESSING;
            } else if (ProcessingStatus.COMPLETED.ordinal() == rawStatus) {
                this.status = ProcessingStatus.COMPLETED;
            }
            this.priority = buffer.getInt();
            this.numberOfCrashes = buffer.getInt();
        } else {
            this.status = ProcessingStatus.PENDING;
            this.priority = DEFAULT_PRIORITY;
            this.numberOfCrashes = 0;
        }
    }

    /**
     * RJCTODO
     */
    ManifestNodeData(ProcessingStatus status, int priority, int numberOfCrashes) {
        this.nodeDataIsSet = false;
        this.status = status;
        this.priority = priority;
        this.numberOfCrashes = numberOfCrashes;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    boolean isSet() {
        return this.nodeDataIsSet;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    ProcessingStatus getStatus() {
        return this.status;
    }

    /**
     *
     * @param status
     */
    void setStatus(ProcessingStatus status) {
        this.status = status;
    }

    /**
     *
     * @return
     */
    int getPriority() {
        return this.priority;
    }

    /**
     *
     * @param priority
     */
    void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    int getNumberOfCrashes() {
        return this.numberOfCrashes;
    }

    /**
     * RJCTODO
     *
     * @param attempts
     */
    void setNumberOfCrashes(int attempts) {
        this.numberOfCrashes = attempts;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    byte[] toArray() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 3);
        buffer.putInt(this.status.ordinal());
        buffer.putInt(this.priority);
        buffer.putInt(this.numberOfCrashes);
        return buffer.array();
    }

}
