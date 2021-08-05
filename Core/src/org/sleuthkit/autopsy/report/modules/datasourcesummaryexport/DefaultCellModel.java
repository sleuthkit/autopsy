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
 * The default cell model.
 */
class DefaultCellModel<T> implements CellModel {

    private final T data;
    private final String text;
    private CellModel.HorizontalAlign horizontalAlignment;
    private final String excelFormatString;

    /**
     * Main constructor.
     *
     * @param data The data to be displayed in the cell.
     */
    DefaultCellModel(T data) {
        this(data, null, null);
    }

    /**
     * Constructor.
     *
     * @param data            The data to be displayed in the cell.
     * @param stringConverter The means of converting that data to a string or
     *                        null to use .toString method on object.
     */
    DefaultCellModel(T data, Function<T, String> stringConverter) {
        this(data, stringConverter, null);
    }

    /**
     * Constructor.
     *
     * @param data              The data to be displayed in the cell.
     * @param stringConverter   The means of converting that data to a string or
     *                          null to use .toString method on object.
     * @param excelFormatString The apache poi excel format string to use with
     *                          the data.
     *
     * NOTE: Only certain data types can be exported. See
     * ExcelTableExport.createCell() for types.
     */
    DefaultCellModel(T data, Function<T, String> stringConverter, String excelFormatString) {
        this.data = data;
        this.excelFormatString = excelFormatString;

        if (stringConverter == null) {
            text = this.data == null ? "" : this.data.toString();
        } else {
            text = stringConverter.apply(this.data);
        }
    }

    @Override
    public T getData() {
        return this.data;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public HorizontalAlign getHorizontalAlignment() {
        return horizontalAlignment;
    }
    
    @Override
    public String getExcelFormatString() {
        return this.excelFormatString;
    }    

    /**
     * Sets the horizontal alignment for this cell model.
     *
     * @param alignment The horizontal alignment for the cell model.
     *
     * @return As a utility, returns this.
     */
    DefaultCellModel<T> setHorizontalAlignment(CellModel.HorizontalAlign alignment) {
        this.horizontalAlignment = alignment;
        return this;
    }

    @Override
    public String toString() {
        return getText();
    }
}
