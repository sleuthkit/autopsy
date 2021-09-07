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

import java.util.function.Function;

/**
 * Describes aspects of a column which can be used with getTableModel or
 * getJTablePanel. 'T' represents the object that will represent rows in the
 * table.
 */
class ColumnModel<T, C extends CellModel> {

    private final String headerTitle;
    private final Function<T, ? extends C> cellRenderer;
    private final Integer width;

    /**
     * Constructor for a DataResultColumnModel.
     *
     * @param headerTitle The title for the column.
     * @param cellRenderer The method that generates a CellModel for the column
     * based on the data.
     */
    ColumnModel(String headerTitle, Function<T, ? extends C> cellRenderer) {
        this(headerTitle, cellRenderer, null);
    }

    /**
     * Constructor for a DataResultColumnModel.
     *
     * @param headerTitle The title for the column.
     * @param cellRenderer The method that generates a CellModel for the column
     * based on the data.
     * @param width The preferred width of the column.
     */
    ColumnModel(String headerTitle, Function<T, ? extends C> cellRenderer, Integer width) {
        this.headerTitle = headerTitle;
        this.cellRenderer = cellRenderer;
        this.width = width;
    }

    /**
     * @return The title for the column.
     */
    String getHeaderTitle() {
        return headerTitle;
    }

    /**
     * @return The method that generates a CellModel for the column based on the
     * data.
     */
    Function<T, ? extends C> getCellRenderer() {
        return cellRenderer;
    }

    /**
     * @return The preferred width of the column (can be null).
     */
    Integer getWidth() {
        return width;
    }
}
