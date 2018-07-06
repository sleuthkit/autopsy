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
        "DataContentViewerOtherCasesTableModel.property=Correlation Property",
        "DataContentViewerOtherCasesTableModel.value=Correlation Value",
        "DataContentViewerOtherCasesTableModel.known=Known",
        "DataContentViewerOtherCasesTableModel.comment=Comment",
        "DataContentViewerOtherCasesTableModel.noData=No Data.",})
    enum TableColumns {
        // Ordering here determines displayed column order in Content Viewer.
        // If order is changed, update the CellRenderer to ensure correct row coloring.
        CASE_NAME(Bundle.DataContentViewerOtherCasesTableModel_case(), 100),
        DATA_SOURCE(Bundle.DataContentViewerOtherCasesTableModel_dataSource(), 100),
        PROPERTY(Bundle.DataContentViewerOtherCasesTableModel_property(), 125),
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

        return mapValueById(rowIdx, TableColumns.values()[colIdx]);
    }

    Object getRow(int rowIdx) {
        return nodeDataList.get(rowIdx);
    }

    /**
     * Map a rowIdx and colId to the value in that cell.
     *
     * @param rowIdx Index of row to search
     * @param colId  ID of column to search
     *
     * @return value in the cell
     */
    private Object mapValueById(int rowIdx, TableColumns colId) {
        OtherOccurrenceNodeData nodeData = nodeDataList.get(rowIdx);
        
        if (nodeData instanceof OtherOccurrenceNodeMessageData) {
            if (colId == TableColumns.CASE_NAME) {
                OtherOccurrenceNodeMessageData messageData = (OtherOccurrenceNodeMessageData) nodeData;
                return messageData.getDisplayMessage();
            } else {
                return "";
            }
        }
        
        OtherOccurrenceNodeInstanceData instanceData = (OtherOccurrenceNodeInstanceData) nodeData;
        String value = Bundle.DataContentViewerOtherCasesTableModel_noData();

        switch (colId) {
            case CASE_NAME:
                if (null != instanceData.getCaseName()) {
                    value = instanceData.getCaseName();
                }
                break;
            case DEVICE:
                if (null != instanceData.getDeviceID()) {
                    value = instanceData.getDeviceID();
                }
                break;
            case DATA_SOURCE:
                if (null != instanceData.getDataSourceName()) {
                    value = instanceData.getDataSourceName();
                }
                break;
            case FILE_PATH:
                value = instanceData.getFilePath();
                break;
            case PROPERTY:
                value = instanceData.getType();
                break;
            case VALUE:
                value = instanceData.getValue();
                break;
            case KNOWN:
                value = instanceData.getKnown().getName();
                break;
            case COMMENT:
                value = instanceData.getComment();
                break;
        }
        return value;
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
