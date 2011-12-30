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

    public static String byteArrayToHex(byte[] array, long length, long offset, Font font) {
        if (array == null) {
            return "";
        } else {
            String base = new String(array);

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
                buff.append("0x" + Long.toHexString((long) (offset + count + hexMax)).substring(1) + ": ");
                for (int i = 0; i < 16; i++) {
                    buff.append(Integer.toHexString((((int) base.charAt(count + i)) & 0xff) + 256).substring(1).toUpperCase() + "  ");
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

    /*
     * Gets only the printable string from the given characters
     *
     * The definition of printable are:
     *  -- All of the letters, numbers, and punctuation.
     *  -- space and tab
     *  -- It does NOT include newlines or control chars.
     *  -- When looking for ASCII strings, they evaluate each byte and when they find four or more printable characters they get printed out with a newline in between each string.
     *  -- When looking for Unicode strings, they evaluate each two byte sequence and look for four or more printable characters…
     *
     * @param args          the bytes that the string read from
     * @param parameter     the "length" parameter for the string
     *
     * @author jantonius
     */
    public static String getString(byte[] args, int parameter) {

        /*
        // these encoding might be needed for later
        // Note: if not used, can be deleted
        CharsetEncoder asciiEncoder =
        Charset.forName("US-ASCII").newEncoder(); // or "ISO-8859-1" for ISO Latin 1
        
        CharsetEncoder utf8Encoder =
        Charset.forName("UTF-8").newEncoder();
         */

        final StringBuilder result = new StringBuilder();
        StringBuilder temp = new StringBuilder();
        int counter = 0;
        //char[] converted = new java.lang.System.Text.Encoding.ASCII.GetString(args).ToCharArray();

        final char NL = (char) 10; // ASCII char for new line
        final String NLS = Character.toString(NL);
        for (int i = 0; i < args.length; i++) {
            char tempChar = (char) args[i];
            int dec = (int) tempChar;

            // the printable ASCII chars are dec 32-126
            // and we want to include TAB as well (dec 9)
            if (!((dec < 32 || dec > 126) && dec != 9)) {
                temp.append(tempChar);
                ++counter;
            } else {
                if (counter >= parameter) {
                    // add to the result and also add the new line at the end
                    result.append(temp);
                    result.append(NLS);

                    // reset the temp and counter
                    temp = new StringBuilder();
                    counter = 0;
                }
                // reset the temp and counter
                temp = new StringBuilder();
                counter = 0;
            }
        }

        result.append(temp);
        return result.toString();
    }

    /**
     * Converts the given paths into the formatted path. This mainly used for
     * the paths for the "new directory table" and "new output view".
     *
     * @param paths  the given paths
     * @param index  the starting index of the given paths
     * @return path  the formatted path
     *
     * @author jantonius
     */
    public static String getformattedPath(String[] paths, int index) {
        String result = "";
        for (int i = index; i < paths.length; i++) {
            result = result + "\\" + paths[i];
        }
        return result;
    }
}
