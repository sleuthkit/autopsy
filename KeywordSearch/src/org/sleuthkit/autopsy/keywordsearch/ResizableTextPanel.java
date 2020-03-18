/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

/**
 * This interface allows for retrieving current text size and setting the new text size
 * for a panel.
 */
interface ResizableTextPanel {

    /**
     * Retrieves the font size (in px).
     * @return  the font size (in px).
     */
    int getTextSize();

    /**
     * Sets the font size (in px).
     * @param newSize   the new font size (in px).
     */
    void setTextSize(int newSize);
    
}
