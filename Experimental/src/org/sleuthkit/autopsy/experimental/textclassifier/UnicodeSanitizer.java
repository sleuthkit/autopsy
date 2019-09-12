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

package org.sleuthkit.autopsy.experimental.textclassifier;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Standardizes Unicode strings and cleans up invalid Unicode.
 */
public class UnicodeSanitizer {
    private static final Charset UTF_16 = StandardCharsets.UTF_16;

    /**
     * This method standardizes Unicode strings.
     * 1. Combining characters are merged together according to
     * {@line Normalizer.Form.NFKC}.
     * 2. Invalid UTF-16 characters character sequences are replaced with
     * U+FFFD / �.
     * 3. Characters that are either invalid UTF-8 are replaced with caret "^".
     * Some valid characters outside the multilingual plane may also be
     * replaced with "^"
     *
     * @param s The string to cleanup.
     * @return A string with all the same content as s, but where code points
     *         are normalized and cleaned up.
     */
    static String sanitize(String s) {
        String normStr = Normalizer.normalize(s, Normalizer.Form.NFKC);
        return sanitizeToUTF8(replaceInvalidUTF16(normStr)).toString();
    }

    /**
     * Cleanup invalid codepoint sequences by replacing them with the default
     * replacement character: U+FFFD / �.
     *
     * @param s The string to cleanup.
     *
     * @return A StringBuilder with the same content as s but where all invalid
     *         code points have been replaced.
     */
    private static StringBuilder replaceInvalidUTF16(String s) {
        /* encode the string to UTF-16 which does the replacement, see
         * Charset.encode(), then decode back to a StringBuilder. */
        return new StringBuilder(UTF_16.decode(UTF_16.encode(s)));
    }

    /**
     * Sanitize the given StringBuilder by replacing non-UTF-8 characters with
     * caret '^'
     *
     * @param sb the StringBuilder to sanitize
     */
    private static StringBuilder sanitizeToUTF8(StringBuilder sb) {
        final int length = sb.length();
        for (int i = 0; i < length; i++) {
            if (isValidSolrUTF8(sb.charAt(i)) == false) {
                sb.replace(i, i + 1, "^");
            }
        }
        return sb;
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
     * @param ch the character to test
     *
     * @return Returns true if the character is valid UTF-8, false if not.
     */
    private static boolean isValidSolrUTF8(char ch) {
        return ((ch <= 0xFDD0 || ch >= 0xFDEF) && (ch > 0x1F || ch == 0x9 || ch == 0xA || ch == 0xD) && (ch != 0xFFFF) && (ch != 0xFFFE));
    }
}
