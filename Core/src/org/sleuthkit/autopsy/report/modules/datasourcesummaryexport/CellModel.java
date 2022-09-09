/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.datasourcesummaryexport;

import javax.swing.JLabel;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

/**
 * Basic interface for a cell model.
 */
interface CellModel {

    /**
     * Describes the horizontal alignment.
     */
    enum HorizontalAlign {
        LEFT(JLabel.LEFT, HorizontalAlignment.LEFT),
        CENTER(JLabel.CENTER, HorizontalAlignment.CENTER),
        RIGHT(JLabel.RIGHT, HorizontalAlignment.RIGHT);

        private final int jlabelAlignment;
        private final HorizontalAlignment poiAlignment;

        /**
         * Constructor for a HorizontalAlign enum.
         *
         * @param jlabelAlignment The corresponding JLabel horizontal alignment
         * number.
         * @param poiAlignment Horizontal alignment for Apache POI.
         */
        HorizontalAlign(int jlabelAlignment, HorizontalAlignment poiAlignment) {
            this.jlabelAlignment = jlabelAlignment;
            this.poiAlignment = poiAlignment;
        }

        /**
         * @return The corresponding JLabel horizontal alignment (i.e.
         * JLabel.LEFT).
         */
        int getJLabelAlignment() {
            return this.jlabelAlignment;
        }

        /**
         * @return Horizontal alignment for Apache POI.
         */
        HorizontalAlignment getPoiAlignment() {
            return poiAlignment;
        }
    }

    /**
     * @return The root data object.
     */
    Object getData();

    /**
     * @return The text to be shown in the cell.
     */
    default String getText() {
        Object data = getData();
        return (data == null) ? null : data.toString();
    }

    /**
     * @return The horizontal alignment for the text in the cell.
     */
    HorizontalAlign getHorizontalAlignment();
    
    /**
     * @return The format string to be used with Apache POI during excel
     * export or null if none necessary.
     */
    String getExcelFormatString();
}
