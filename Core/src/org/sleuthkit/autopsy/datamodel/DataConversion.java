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

/**
 * Helper methods for converting data.
 */
public class DataConversion {

    public static String byteArrayToHex(byte[] array, int length, long offset, Font font) {
        if (array == null) {
            return "";
        } else {
            String base = new String(array, 0, length);

            StringBuilder buff = new StringBuilder();
            int count = 0;
            int extra = base.length() % 16;
            String sub = "";
            char subchar;

            //commented out code can be used as a base for generating hex length based on
            //offset/length/file size
            //String hex = Long.toHexString(length + offset);
            //double hexMax = Math.pow(16, hex.length());
            double hexMax = Math.pow(16, 6);
            while (count < base.length() - extra) {
                buff.append("0x");
                buff.append(Long.toHexString((long) (offset + count + hexMax)).substring(1));
                buff.append(": ");
                for (int i = 0; i < 16; i++) {
                    buff.append(Integer.toHexString((((int) base.charAt(count + i)) & 0xff) + 256).substring(1).toUpperCase());
                    buff.append("  ");
                    if (i == 7) {
                        buff.append("  ");
                    }
                }
                sub = base.substring(count, count + 16);
                for (int i = 0; i < 16; i++) {
                    subchar = sub.charAt(i);
                    if (!font.canDisplay(subchar)) {
                        sub.replace(subchar, '.');
                    }

                    // replace all unprintable characters with "."
                    int dec = (int) subchar;
                    if (dec < 32 || dec > 126) {
                        sub = sub.replace(subchar, '.');
                    }
                }
                buff.append("  " + sub + "\n");
                count += 16;

            }
            if (base.length() % 16 != 0) {
                buff.append("0x" + Long.toHexString((long) (offset + count + hexMax)).substring(1) + ": ");
            }
            for (int i = 0; i < 16; i++) {
                if (i < extra) {
                    buff.append(Integer.toHexString((((int) base.charAt(count + i)) & 0xff) + 256).substring(1) + "  ");
                } else {
                    buff.append("    ");
                }
                if (i == 7) {
                    buff.append("  ");
                }
            }
            sub = base.substring(count, count + extra);
            for (int i = 0; i < extra; i++) {
                subchar = sub.charAt(i);
                if (!font.canDisplay(subchar)) {
                    sub.replace(subchar, '.');
                }
            }
            buff.append("  " + sub);
            return buff.toString();
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
