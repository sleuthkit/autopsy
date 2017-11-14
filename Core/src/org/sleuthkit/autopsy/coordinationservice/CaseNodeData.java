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
package org.sleuthkit.autopsy.coordinationservice;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * An object that converts case data for a case directory coordination service
 * node to and from byte arrays.
 */
public final class CaseNodeData {

    private static final int CURRENT_VERSION = 0;
    
    private int version;
    private boolean errorsOccurred;
    private List<Long> dataSourceSizeList;

    /**
     * Gets the current version of the case directory coordination service node
     * data.
     *
     * @return The version number.
     */
    public static int getCurrentVersion() {
        return CaseNodeData.CURRENT_VERSION;
    }
    
    /**
     * Uses coordination service node data to construct an object that converts
     * case data for a case directory coordination service node to and from byte
     * arrays.
     *
     * @param nodeData The raw bytes received from the coordination service.
     * 
     * @throws InvalidDataException If the node data buffer is smaller than
     *                              expected.
     */
    public CaseNodeData(byte[] nodeData) throws InvalidDataException {
        if(nodeData == null || nodeData.length == 0) {
            this.version = CURRENT_VERSION;
            this.errorsOccurred = false;
            this.dataSourceSizeList = new ArrayList<>(0);
        } else {
            /*
             * Get fields from node data.
             */
            ByteBuffer buffer = ByteBuffer.wrap(nodeData);
            try {
                if (buffer.hasRemaining()) {
                    this.version = buffer.getInt();

                    /*
                     * Flags bit format: 76543210
                     * 0-6 --> reserved for future use
                     * 7 --> errorsOccurred
                     */
                    byte flags = buffer.get();
                    this.errorsOccurred = (flags < 0);
                    
                    short nDataSources = buffer.getShort();
                    this.dataSourceSizeList = new ArrayList<>(0);
                    for(int i=0; i < nDataSources; i++) {
                        this.dataSourceSizeList.add(buffer.getLong());
                    }
                }
            } catch (BufferUnderflowException ex) {
                throw new InvalidDataException("Node data is incomplete", ex);
            }
        }
    }

    /**
     * Gets the node data version number.
     *
     * @return The version number.
     */
    public int getVersion() {
        return this.version;
    }

    /**
     * Gets whether or not any errors occurred during the processing of the job.
     *
     * @return True or false.
     */
    public boolean getErrorsOccurred() {
        return this.errorsOccurred;
    }

    /**
     * Sets whether or not any errors occurred during the processing of job.
     *
     * @param errorsOccurred True or false.
     */
    public void setErrorsOccurred(boolean errorsOccurred) {
        this.errorsOccurred = errorsOccurred;
    }
    
    /**
     * Gets the data source size list.
     * 
     * @return The data source size list.
     */
    public List<Long> getDataSourceSizeList() {
        return this.dataSourceSizeList;
    }
    
    /**
     * Gets the node data as a byte array that can be sent to the coordination
     * service.
     *
     * @return The node data as a byte array.
     */
    public byte[] toArray() {
        int bufferSize = 7 + (this.dataSourceSizeList.size() << 3);
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        
        buffer.putInt(this.version);
        buffer.put((byte)(this.errorsOccurred ? 0x80 : 0));
        
        buffer.putShort((short)this.dataSourceSizeList.size());
        for (Long size : this.dataSourceSizeList) {
            buffer.putLong(size);
        }
        
        // Prepare the array
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
