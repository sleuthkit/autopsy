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
}
