/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
import org.openide.util.NbBundle.Messages;

/**
 * Model for cells in data content viewer table
 */
public class DataContentViewerOtherCasesTableModel extends AbstractTableModel {

    @Messages({"DataContentViewerOtherCasesTableModel.case=Case",
        "DataContentViewerOtherCasesTableModel.device=Device",
        "DataContentViewerOtherCasesTableModel.dataSource=Data Source",
        "DataContentViewerOtherCasesTableModel.path=Path",
        "DataContentViewerOtherCasesTableModel.attribute=Matched Attribute",
        "DataContentViewerOtherCasesTableModel.value=Attribute Value",
        "DataContentViewerOtherCasesTableModel.known=Known",
        "DataContentViewerOtherCasesTableModel.comment=Comment",
        "DataContentViewerOtherCasesTableModel.noData=No Data.",})
    enum TableColumns {
        // Ordering here determines displayed column order in Content Viewer.
        // If order is changed, update the CellRenderer to ensure correct row coloring.
        CASE_NAME(Bundle.DataContentViewerOtherCasesTableModel_case(), 100),
        DATA_SOURCE(Bundle.DataContentViewerOtherCasesTableModel_dataSource(), 100),
        ATTRIBUTE(Bundle.DataContentViewerOtherCasesTableModel_attribute(), 125),
        VALUE(Bundle.DataContentViewerOtherCasesTableModel_value(), 200),
        KNOWN(Bundle.DataContentViewerOtherCasesTableModel_known(), 50),
        FILE_PATH(Bundle.DataContentViewerOtherCasesTableModel_path(), 450),
        COMMENT(Bundle.DataContentViewerOtherCasesTableModel_comment(), 200),
        DEVICE(Bundle.DataContentViewerOtherCasesTableModel_device(), 250);

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

    List<OtherOccurrenceNodeData> nodeDataList;

    DataContentViewerOtherCasesTableModel() {
        nodeDataList = new ArrayList<>();
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
            return Bundle.DataContentViewerOtherCasesTableModel_noData();
        }

        OtherOccurrenceNodeData nodeData = nodeDataList.get(rowIdx);
        TableColumns columnId = TableColumns.values()[colIdx];
        if (nodeData instanceof OtherOccurrenceNodeMessageData) {
            return mapNodeMessageData((OtherOccurrenceNodeMessageData) nodeData, columnId);
        }
        return mapNodeInstanceData((OtherOccurrenceNodeInstanceData) nodeData, columnId);
    }

    /**
     * Map a column ID to the value in that cell for node message data.
     *
     * @param nodeData    The node message data.
     * @param columnId The ID of the cell column.
     *
     * @return The value in the cell.
     */
    private Object mapNodeMessageData(OtherOccurrenceNodeMessageData nodeData, TableColumns columnId) {
        if (columnId == TableColumns.CASE_NAME) {
            return nodeData.getDisplayMessage();
        }
        return "";
    }

    /**
     * Map a column ID to the value in that cell for node instance data.
     *
     * @param nodeData    The node instance data.
     * @param columnId The ID of the cell column.
     *
     * @return The value in the cell.
     */
    private Object mapNodeInstanceData(OtherOccurrenceNodeInstanceData nodeData, TableColumns columnId) {
        String value = Bundle.DataContentViewerOtherCasesTableModel_noData();

        switch (columnId) {
            case CASE_NAME:
                if (null != nodeData.getCaseName()) {
                    value = nodeData.getCaseName();
                }
                break;
            case DEVICE:
                if (null != nodeData.getDeviceID()) {
                    value = nodeData.getDeviceID();
                }
                break;
            case DATA_SOURCE:
                if (null != nodeData.getDataSourceName()) {
                    value = nodeData.getDataSourceName();
                }
                break;
            case FILE_PATH:
                value = nodeData.getFilePath();
                break;
            case ATTRIBUTE:
                value = nodeData.getType();
                break;
            case VALUE:
                value = nodeData.getValue();
                break;
            case KNOWN:
                value = nodeData.getKnown().getName();
                break;
            case COMMENT:
                value = nodeData.getComment();
                break;
            default: // This shouldn't occur! Use default "No data" value.
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
