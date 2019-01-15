/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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

import java.nio.ByteBuffer;
import javax.lang.model.type.TypeKind;

/**
 * Utilities for reading and writing node data.
 */
public final class NodeDataUtils {

    /**
     * This method retrieves a string from a given buffer. Assumes the string
     * was written using putStringIntoBuffer and first reads the the length of
     * the string.
     *
     * @param buffer The buffer from which the string will be read.
     *
     * @return The string read from the buffer.
     */
    public static String getStringFromBuffer(ByteBuffer buffer) {
        String output = "";
        int length = buffer.getInt();
        if (length > 0) {
            byte[] array = new byte[length];
            buffer.get(array, 0, length);
            output = new String(array);
        }
        return output;
    }

    /**
     * This method puts a given string into a given buffer. The length of the
     * string will be inserted prior to the string.
     *
     * @param stringValue The string to write to the buffer.
     * @param buffer      The buffer to which the string will be written.
     */
    public static void putStringIntoBuffer(String stringValue, ByteBuffer buffer) {
        buffer.putInt(stringValue.getBytes().length);
        buffer.put(stringValue.getBytes());
    }

    private NodeDataUtils() {
    }
    
}
