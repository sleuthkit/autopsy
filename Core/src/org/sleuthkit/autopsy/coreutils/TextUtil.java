/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.awt.ComponentOrientation;

/**
 * Text utilities
 */
public class TextUtil {

    /**
     * Determine and return text orientation
     *
     * @param text text to determine the text orientation in
     *
     * @return detected text orientation that should be used for this type of
     *         text
     */
    public static ComponentOrientation getTextDirection(String text) {
        int rtl_cnt = 0;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }

            // count the RTL chars
            byte direction = Character.getDirectionality(c);
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT) {
                ++rtl_cnt;
            }
        }

        ComponentOrientation orientation = ComponentOrientation.LEFT_TO_RIGHT;
        if (text.length() > 1024 && rtl_cnt > 50) {
            orientation = ComponentOrientation.RIGHT_TO_LEFT;
        } else if (text.length() <= 1024 && rtl_cnt > text.length() / 4) {
            orientation = ComponentOrientation.RIGHT_TO_LEFT;
        }

        return orientation;
    }
    
    
    /**
     * This method determines if a passed-in Java char (16 bits) is a valid
     * UTF-8 printable character, returning true if so, false if not.
     *
     * Note that this method can have ramifications for characters outside the
     * Unicode Base Multilingual Plane (BMP), which require more than 16 bits.
     * We are using Java characters (16 bits) to look at the data and this will
     * not accurately identify any non-BMP character (larger than 16 bits)
     * ending with 0xFFFF and 0xFFFE. In the interest of a fast solution, we
     * have chosen to ignore the extended planes above Unicode BMP for the time
     * being. The net result of this is some non-BMP characters may be
     * interspersed with '^' characters in Autopsy.
     * 
     * Strip all non-characters
     * http://unicode.org/cldr/utility/list-unicodeset.jsp?a=[:Noncharacter_Code_Point=True:]
     * and non-printable control characters except tabulator, new line and carriage return
     *
     * @param ch the character to test
     *
     * @return Returns true if the character is valid UTF-8, false if not.
     */
    public static boolean isValidSolrUTF8(char ch) {
        return ((ch <= 0xFDD0 || ch >= 0xFDEF) // 0xfdd0 - 0xfdef
                && (ch > 0x1F || ch == 0x9 || ch == 0xA || ch == 0xD)
                && (ch % 0x10000 != 0xFFFF) // 0xffff - 0x10ffff range step 0x10000
                && (ch % 0x10000 != 0xFFFE)); // 0xfffe - 0x10fffe range
    }
}
