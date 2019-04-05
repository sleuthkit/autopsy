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

import java.util.LinkedHashSet;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle;

final class OtherOccurrencesDataSourcesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    @NbBundle.Messages({"OtherOccurrencesDataSourcesTableModel.dataSourceName=Data Source Name",
        "OtherOccurrencesDataSourcesTableModel.noData=No Data."})

    private final LinkedHashSet<DataSourceColumnItem> dataSourceSet = new LinkedHashSet<>();

    OtherOccurrencesDataSourcesTableModel() {

    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public int getRowCount() {
        return dataSourceSet.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return Bundle.OtherOccurrencesDataSourcesTableModel_dataSourceName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (0 == dataSourceSet.size()) {
            return Bundle.OtherOccurrencesDataSourcesTableModel_noData();
        }
        return ((DataSourceColumnItem) dataSourceSet.toArray()[rowIdx]).getDataSourceName();
    }

    public String getDeviceIdForRow(int rowIdx) {
        return ((DataSourceColumnItem) dataSourceSet.toArray()[rowIdx]).getDeviceId();
    }
    
    public String getCaseNameForRow(int rowIdx){
        return ((DataSourceColumnItem) dataSourceSet.toArray()[rowIdx]).getCaseName();
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
//        String key = createDataSourceKey((OtherOccurrenceNodeInstanceData)newNodeData);
        dataSourceSet.add(new DataSourceColumnItem((OtherOccurrenceNodeInstanceData) newNodeData));
        fireTableDataChanged();
    }

    /**
     * Clear the node data table.
     */
    void clearTable() {
        dataSourceSet.clear();
        fireTableDataChanged();
    }

    private final class DataSourceColumnItem {

        private final String caseName;
        private final String deviceId;
        private final String dataSourceName;

        private DataSourceColumnItem(OtherOccurrenceNodeInstanceData nodeData) {
            this(nodeData.getCaseName(), nodeData.getDeviceID(), nodeData.getDataSourceName());
        }

        private DataSourceColumnItem(String caseName, String deviceId, String dataSourceName) {
            this.caseName = caseName;
            this.deviceId = deviceId;
            this.dataSourceName = dataSourceName;
        }

        private String getDeviceId() {
            return deviceId;
        }

        private String getDataSourceName() {
            return dataSourceName;
        }

        private String getCaseName() {
            return caseName;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DataSourceColumnItem
                    && caseName.equals(((DataSourceColumnItem) other).getCaseName())
                    && dataSourceName.equals(((DataSourceColumnItem) other).getDataSourceName())
                    && deviceId.equals(((DataSourceColumnItem) other).getDeviceId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(caseName, deviceId, dataSourceName);
        }
    }

}
