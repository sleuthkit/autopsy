/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.awt.Font;
import java.util.Arrays;
import java.util.Formatter;

/**
 * Helper methods for converting data.
 */
public class DataConversion {

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray(); //NON-NLS

    /**
     * Return the hex-dump layout of the passed in byte array. Deprecated
     * because we don't need font
     *
     * @param array       Data to display
     * @param length      Amount of data in array to display
     * @param arrayOffset Offset of where data in array begins as part of a
     *                    bigger file (used for arrayOffset column)
     * @param font        Font that will be used to display the text
     *
     * @return
     */
    @Deprecated
    public static String byteArrayToHex(byte[] array, int length, long arrayOffset, Font font) {
        return byteArrayToHex(array, length, arrayOffset);
    }

    /**
     * Return the hex-dump layout of the passed in byte array.
     *
     * @param array       Data to display
     * @param length      Amount of data in array to display
     * @param arrayOffset Offset of where data in array begins as part of a
     *                    bigger file (used for arrayOffset column)
     *
     * @return
     */
    public static String byteArrayToHex(byte[] array, int length, long arrayOffset) {
        if (array == null) {
            return "";
        } else {
            StringBuilder outputStringBuilder = new StringBuilder();

            // loop through the file in 16-byte increments 
            for (int curOffset = 0; curOffset < length; curOffset += 16) {
                // how many bytes are we displaying on this line
                int lineLen = 16;
                if (length - curOffset < 16) {
                    lineLen = length - curOffset;
                }

                // print the offset column
                //outputStringBuilder.append("0x");
                outputStringBuilder.append(String.format("0x%08x: ", arrayOffset + curOffset)); //NON-NLS
                //outputStringBuilder.append(": ");

                // print the hex columns                
                for (int i = 0; i < 16; i++) {
                    if (i < lineLen) {
                        int v = array[curOffset + i] & 0xFF;
                        outputStringBuilder.append(hexArray[v >>> 4]);
                        outputStringBuilder.append(hexArray[v & 0x0F]);
                    } else {
                        outputStringBuilder.append("  ");
                    }

                    // someday we'll offer the option of these two styles...
                    if (true) {
                        outputStringBuilder.append(" ");
                        if (i % 4 == 3) {
                            outputStringBuilder.append(" ");
                        }
                        if (i == 7) {
                            outputStringBuilder.append(" ");
                        }
                    } // xxd style
                    else {
                        if (i % 2 == 1) {
                            outputStringBuilder.append(" ");
                        }
                    }
                }

                outputStringBuilder.append("  ");

                // print the ascii columns
                String ascii = new String(array, curOffset, lineLen, java.nio.charset.StandardCharsets.US_ASCII);
                for (int i = 0; i < 16; i++) {
                    char c = ' ';
                    if (i < ascii.length()) {
                        c = ascii.charAt(i);
                        int dec = (int) c;

                        if (dec < 32 || dec > 126) {
                            c = '.';
                        }
                    }
                    outputStringBuilder.append(c);
                }

                outputStringBuilder.append("\n");
            }

            return outputStringBuilder.toString();
        }
    }

    protected static String charArrayToByte(char[] array) {
        if (array == null) {
            return "";
        } else {
            String[] binary = new String[array.length];

            for (int i = 0; i < array.length; i++) {
                binary[i] = Integer.toBinaryString(array[i]);
            }
            return Arrays.toString(binary);
        }
    }
}
