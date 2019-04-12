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
import java.util.Set;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle;

/**
 * Model for cells in the data sources section of the other occurrences data
 * content viewer
 */
final class OtherOccurrencesDataSourcesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private final Set<DataSourceColumnItem> dataSourceSet = new LinkedHashSet<>();

    /**
     * Create a table model for displaying data source names
     */
    OtherOccurrencesDataSourcesTableModel() {
        // This constructor is intentionally empty.
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public int getRowCount() {
        return dataSourceSet.size();
    }

    @NbBundle.Messages({"OtherOccurrencesDataSourcesTableModel.dataSourceName=Data Source Name",
        "OtherOccurrencesDataSourcesTableModel.noData=No Data."})
    @Override
    public String getColumnName(int colIdx) {
        return Bundle.OtherOccurrencesDataSourcesTableModel_dataSourceName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        //if anything would prevent this from working we will consider it no data for the sake of simplicity
        if (dataSourceSet.isEmpty() || rowIdx < 0
                || rowIdx >= dataSourceSet.size()
                || !(dataSourceSet.toArray()[rowIdx] instanceof DataSourceColumnItem)) {
            return Bundle.OtherOccurrencesDataSourcesTableModel_noData();
        }
        return ((DataSourceColumnItem) dataSourceSet.toArray()[rowIdx]).getDataSourceName();
    }

    /**
     * Get the device id of the data source shown at the specified row index
     *
     * @param rowIdx the row index of the data source you want the device id for
     *
     * @return the device id of the specified data source or an empty string if
     *         a device id could not be retrieved
     */
    String getDeviceIdForRow(int rowIdx) {
        //if anything would prevent this from working we will return an empty string 
        if (dataSourceSet.isEmpty() || rowIdx < 0
                || rowIdx >= dataSourceSet.size()
                || !(dataSourceSet.toArray()[rowIdx] instanceof DataSourceColumnItem)) {
            return "";
        }
        return ((DataSourceColumnItem) dataSourceSet.toArray()[rowIdx]).getDeviceId();
    }

    /**
     * Get the case name of the data source shown at the specified row index
     *
     * @param rowIdx the row index of the data source you want the case name for
     *
     * @return the case name of the specified data source or an empty string if
     *         a case name could not be retrieved
     */
    String getCaseNameForRow(int rowIdx) {
        //if anything would prevent this from working we will return an empty string 
        if (dataSourceSet.isEmpty() || rowIdx < 0
                || rowIdx >= dataSourceSet.size()
                || !(dataSourceSet.toArray()[rowIdx] instanceof DataSourceColumnItem)) {
            return "";
        }
        return ((DataSourceColumnItem) dataSourceSet.toArray()[rowIdx]).getCaseName();
    }

    @Override
    public Class<String> getColumnClass(int colIdx) {
        return String.class;
    }

    /**
     * Add data source information to the table of unique data sources
     *
     * @param newNodeData data to add to the table
     */
    void addNodeData(OtherOccurrenceNodeData newNodeData) {
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

    /**
     * Private class for storing data source information in a way that
     * facilitates de-duping.
     */
    private final class DataSourceColumnItem {

        private final String caseName;
        private final String deviceId;
        private final String dataSourceName;

        /**
         * Create a DataSourceColumnItem given an
         * OtherOccurrenceNodeInstanceData object
         *
         * @param nodeData the OtherOccurrenceNodeInstanceData which contains
         *                 the data source information
         */
        private DataSourceColumnItem(OtherOccurrenceNodeInstanceData nodeData) {
            this(nodeData.getCaseName(), nodeData.getDeviceID(), nodeData.getDataSourceName());
        }

        /**
         * Create a DataSourceColumnItem given a case name, device id, and data
         * source name
         *
         * @param caseName       the name of the case the data source exists in
         * @param deviceId       the name of the device id for the data source
         * @param dataSourceName the name of the data source
         */
        private DataSourceColumnItem(String caseName, String deviceId, String dataSourceName) {
            this.caseName = caseName;
            this.deviceId = deviceId;
            this.dataSourceName = dataSourceName;
        }

        /**
         * Get the device id
         *
         * @return the data source's device id
         */
        private String getDeviceId() {
            return deviceId;
        }

        /**
         * Get the data source name
         *
         * @return the data source's name
         */
        private String getDataSourceName() {
            return dataSourceName;
        }

        /**
         * Get the name of the case the data source exists in
         *
         * @return the name of the case the data source is in
         */
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
