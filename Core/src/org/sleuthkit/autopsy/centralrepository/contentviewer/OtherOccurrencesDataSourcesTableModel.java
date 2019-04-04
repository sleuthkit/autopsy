/*
 * Central Repository
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle;

final class OtherOccurrencesDataSourcesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    @NbBundle.Messages({"OtherOccurrencesDataSourcesTableModel.dataSourceName=Data Source Name",
        "OtherOccurrencesDataSourcesTableModel.noData=No Data."})
    enum TableColumns {
        DATASOURCE_NAME(Bundle.OtherOccurrencesDataSourcesTableModel_dataSourceName(), 190);

        private final String columnName;
        private final int columnWidth;

        TableColumns(String columnName, int columnWidth) {
            this.columnName = columnName;
            this.columnWidth = columnWidth;
        }

        public String columnName() {
            return columnName;
        }

        public int columnWidth() {
            return columnWidth;
        }
    };

    private final List<OtherOccurrenceNodeData> nodeDataList = new ArrayList<>();

    OtherOccurrencesDataSourcesTableModel() {

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
    public int getColumnPreferredWidth(int colIdx) {
        return TableColumns.values()[colIdx].columnWidth();
    }

    @Override
    public int getRowCount() {
        return nodeDataList.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return TableColumns.values()[colIdx].columnName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (0 == nodeDataList.size()) {
            return Bundle.OtherOccurrencesDataSourcesTableModel_noData();
        }

        OtherOccurrenceNodeData nodeData = nodeDataList.get(rowIdx);
        TableColumns columnId = TableColumns.values()[colIdx];
        return mapNodeInstanceData((OtherOccurrenceNodeInstanceData) nodeData, columnId);
    }

    public String getDeviceIdForRow(int rowIdx) {
        return ((OtherOccurrenceNodeInstanceData) nodeDataList.get(rowIdx)).getDeviceID();
    }

    /**
     * Map a column ID to the value in that cell for node instance data.
     *
     * @param nodeData The node instance data.
     * @param columnId The ID of the cell column.
     *
     * @return The value in the cell.
     */
    private Object mapNodeInstanceData(OtherOccurrenceNodeInstanceData nodeData, TableColumns columnId) {
        String value = Bundle.OtherOccurrencesDataSourcesTableModel_noData();

        switch (columnId) {
            case DATASOURCE_NAME:
                value = nodeData.getDataSourceName();
                break;
            default: //Use default "No data" value.
                break;
        }
        return value;
    }

    Object getRow(int rowIdx) {
        return nodeDataList.get(rowIdx);
    }

    @Override
    public Class<String> getColumnClass(int colIdx) {
        return String.class;
    }

    /**
     * Add one correlated instance object to the table
     *
     * @param newNodeData data to add to the table
     */
    void addNodeData(OtherOccurrenceNodeData newNodeData) {
        nodeDataList.add(newNodeData);
        fireTableDataChanged();
    }

    /**
     * Clear the node data table.
     */
    void clearTable() {
        nodeDataList.clear();
        fireTableDataChanged();
    }

}
