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
 * Model for cells to display correlation data source information
 */
class DataSourcesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    /**
     * A list of correlation data sources from central repository.
     */
    private final List<CorrelationDataSource> dataSources;

    /**
     * Create a new DataSourcesTableModel, with an initially empty list of data
     * sources.
     */
    DataSourcesTableModel() {
        dataSources = new ArrayList<>();
    }

    @Override
    public int getColumnCount() {
        return DataSourcesTableColumns.values().length;
    }

    @Override
    public int getRowCount() {
        return dataSources.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return DataSourcesTableColumns.values()[colIdx].columnName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (dataSources.isEmpty()) {
            return Bundle.DataSourcesTableModel_noData();
        }

        return mapValueById(rowIdx, DataSourcesTableColumns.values()[colIdx]);
    }

    /**
     * Map a rowIdx and colId to the value in that cell.
     *
     * @param rowIdx Index of row to search
     * @param colId  ID of column to search
     *
     * @return value in the cell
     */
    @Messages({"DataSourcesTableModel.noData=No Data Sources"})
    private Object mapValueById(int rowIdx, DataSourcesTableColumns colId) {
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
     * Add a list of datasources to the table.
     *
     * @param dataSourceList the list of datasources to add to the table
     */
    void addDataSources(List<CorrelationDataSource> dataSourceList) {
        dataSources.addAll(dataSourceList);
        fireTableDataChanged();
    }

    /**
     * Clear the data sources currently included in the model.
     */
    void clearTable() {
        dataSources.clear();
        fireTableDataChanged();
    }

    @Messages({"DataSourcesTableModel.dataSource=Data Source Name",
        "DataSourcesTableModel.deviceId=Device ID"})
    /**
     * Enum which lists columns of interest from CorrelationDataSource.
     */
    private enum DataSourcesTableColumns {
        // Ordering here determines displayed column order in Content Viewer.
        // If order is changed, update the CellRenderer to ensure correct row coloring.
        DATA_SOURCE(Bundle.DataSourcesTableModel_dataSource()),
        DEVICE_ID(Bundle.DataSourcesTableModel_deviceId());

        private final String columnName;

        /**
         * Make a CasesTableColumns enum item.
         *
         * @param columnName the name of the column.
         */
        DataSourcesTableColumns(String columnName) {
            this.columnName = columnName;
        }

        /**
         * The name displayed in the column header.
         *
         * @return the name of the column.
         */
        String columnName() {
            return columnName;
        }
    }

}
