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
package org.sleuthkit.autopsy.casemodule;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * An object that converts case data for a case directory coordination service
 * node to and from byte arrays.
 */
public class CaseNodeData {

    private static final int CURRENT_VERSION = 0;
    
    private int version;
    private boolean errorsOccurred;

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
                }
            } catch (BufferUnderflowException ex) {
                throw new InvalidDataException("Node data is incomplete", ex);
            }
        }
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
     * Gets the node data version number.
     *
     * @return The version number.
     */
    public int getVersion() {
        return this.version;
    }
    
    /**
     * Gets the node data as a byte array that can be sent to the coordination
     * service.
     *
     * @return The node data as a byte array.
     */
    public byte[] toArray() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        
        buffer.putInt(this.version);
        buffer.put((byte)(this.errorsOccurred ? 0x80 : 0));
        
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
