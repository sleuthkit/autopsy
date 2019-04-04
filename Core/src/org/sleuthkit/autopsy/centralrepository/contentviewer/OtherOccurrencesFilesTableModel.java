/*
 * Central Repository
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle.Messages;
import org.apache.commons.io.FilenameUtils;

/**
 * Model for cells in the files section of the other occurrences data content
 * viewer
 */
public class OtherOccurrencesFilesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    @Messages({"OtherOccurrencesFilesTableModel.fileName=File Name",
        "OtherOccurrencesFilesTableModel.noData=No Data."})
    enum TableColumns {
        FILE_NAME(Bundle.OtherOccurrencesFilesTableModel_fileName(), 190);

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

    //  private final List<OtherOccurrenceNodeData> nodeDataList = new ArrayList<>();
    private final List<String> nodeKeys = new ArrayList<>();
    private final Map<String, List<OtherOccurrenceNodeData>> nodeMap = new HashMap<>();

    OtherOccurrencesFilesTableModel() {

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
        return nodeKeys.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return TableColumns.values()[colIdx].columnName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (0 == nodeKeys.size()) {
            return Bundle.OtherOccurrencesFilesTableModel_noData();
        }
        return FilenameUtils.getName(((OtherOccurrenceNodeInstanceData) nodeMap.get(nodeKeys.get(rowIdx)).get(0)).getFilePath());
    }

    List<OtherOccurrenceNodeData> getRow(int rowIdx) {
        return nodeMap.get(nodeKeys.get(rowIdx));
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
        String newNodeKey = createNodeKey((OtherOccurrenceNodeInstanceData) newNodeData);//FilenameUtils.getName(((OtherOccurrenceNodeInstanceData)newNodeData).getFilePath());
        List<OtherOccurrenceNodeData> nodeList = nodeMap.get(newNodeKey);
        if (nodeList == null) {
            nodeKeys.add(newNodeKey);
            nodeList = new ArrayList<>();
        }
        nodeList.add(newNodeData);
        nodeMap.put(newNodeKey, nodeList);
        fireTableDataChanged();
    }

    List<OtherOccurrenceNodeData> getNodeDataList(String nodeKey) {
        return Collections.unmodifiableList(nodeMap.get(nodeKey));
    }

    private String createNodeKey(OtherOccurrenceNodeInstanceData nodeData) {
        return nodeData.getCaseName() + nodeData.getDataSourceName() + nodeData.getDeviceID() + nodeData.getFilePath();
    }

    /**
     * Clear the node data table.
     */
    void clearTable() {
        nodeKeys.clear();
        nodeMap.clear();
        fireTableDataChanged();
    }

}
