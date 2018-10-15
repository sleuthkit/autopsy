/*
 * Central Repository
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;

/**
 * Model for cells to display correlation case information
 */
class DataSourcesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    /**
     * list of Eam Cases from central repository.
     */
    private final List<CorrelationDataSource> dataSources;

    DataSourcesTableModel() {
        dataSources = new ArrayList<>();
    }

    @Override
    public int getColumnCount() {
        return TableColumns.values().length;
    }

    /**
     * Get the preferred width that has been configured for this column.
     *
     * A value of 0 means that no preferred width has been defined for this
     * column.
     *
     * @param colIdx Column index
     *
     * @return preferred column width >= 0
     */
    int getColumnPreferredWidth(int colIdx) {
        return TableColumns.values()[colIdx].columnWidth();
    }

    @Override
    public int getRowCount() {
        return dataSources.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return TableColumns.values()[colIdx].columnName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (dataSources.isEmpty()) {
            return Bundle.CasesTableModel_noData();
        }

        return mapValueById(rowIdx, TableColumns.values()[colIdx]);
    }

    /**
     * Map a rowIdx and colId to the value in that cell.
     *
     * @param rowIdx Index of row to search
     * @param colId  ID of column to search
     *
     * @return value in the cell
     */
    @Messages({"DataSourcesTableModel.noData=No Cases"})
    private Object mapValueById(int rowIdx, TableColumns colId) {
        CorrelationDataSource dataSource = dataSources.get(rowIdx);
        String value = Bundle.DataSourcesTableModel_noData();

        switch (colId) {
            case DATA_SOURCE:
                value = dataSource.getName();
                break;
            case DEVICE_ID:
                value = dataSource.getDeviceID();
                break;
            default:
                break;
        }
        return value;
    }

    @Override
    public Class<String> getColumnClass(int colIdx) {
        return String.class;
    }

    /**
     * Add one local central repository case to the table.
     *
     * @param eamCase central repository case to add to the table
     */
    void addDataSources(List<CorrelationDataSource> dataSourceList) {
        dataSources.addAll(dataSourceList);
        fireTableDataChanged();
    }

    
    void clearTable() {
        dataSources.clear();
        fireTableDataChanged();
    }

    @Messages({"DataSourcesTableModel.dataSource=Data Source",
        "DataSourcesTableModel.deviceId=Device ID"})
    /**
     * Enum which lists columns of interest from CorrelationCase.
     */
    private enum TableColumns {
        // Ordering here determines displayed column order in Content Viewer.
        // If order is changed, update the CellRenderer to ensure correct row coloring.
        DATA_SOURCE(Bundle.DataSourcesTableModel_dataSource(), 120),
        DEVICE_ID(Bundle.DataSourcesTableModel_deviceId(), 120);

        private final String columnName;
        private final int columnWidth;

        TableColumns(String columnName, int columnWidth) {
            this.columnName = columnName;
            this.columnWidth = columnWidth;
        }

        String columnName() {
            return columnName;
        }

        int columnWidth() {
            return columnWidth;
        }
    }

}
