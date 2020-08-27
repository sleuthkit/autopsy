/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;

/**
 * A TableModel for a JTable designed to show a list of data where each item in
 * the list represents a row.
 */
public class DefaultListTableModel<T> extends AbstractTableModel implements ListTableModel<T> {

    private static final long serialVersionUID = 1L;
    private final List<Function<T, ? extends Object>> columns;
    private List<T> dataRows = Collections.emptyList();

    /**
     * Main constructor.
     *
     * @param columns A list of functions where the index of each function
     *                represents the data to be displayed at each column index.
     *                The data displayed at row 'r' and column 'c' will be the
     *                result of columns.get(c).apply(dataRows.get(r)).
     */
    public DefaultListTableModel(List<Function<T, ? extends Object>> columns) {
        this.columns = columns;
    }

    @Override
    public List<T> getDataRows() {
        return dataRows;
    }

    @Override
    public void setDataRows(List<T> dataRows) {
        this.dataRows = dataRows == null ? Collections.emptyList() : new ArrayList<>(dataRows);
        super.fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return dataRows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        // if index requested is null, return null
        if (rowIndex < 0 || rowIndex >= dataRows.size() || columnIndex < 0 || columnIndex >= columns.size()) {
            return null;
        }

        // otherwise, get the corresponding row and use the corresponding 
        // column function to get the value
        return columns.get(columnIndex).apply(dataRows.get(rowIndex));
    }
}
