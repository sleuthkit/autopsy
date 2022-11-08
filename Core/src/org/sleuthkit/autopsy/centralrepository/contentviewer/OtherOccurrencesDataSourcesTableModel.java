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

import org.sleuthkit.autopsy.centralrepository.application.NodeData;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.application.OtherOccurrences;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Model for cells in the data sources section of the other occurrences data
 * content viewer
 */
final class OtherOccurrencesDataSourcesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(OtherOccurrencesDataSourcesTableModel.class.getName());
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
     * Get the case uuid of the case the data source shown at the specified row
     * index exists in
     *
     * @param rowIdx the row index of the data source you want the case uuid for
     *
     * @return the case uuid of the case the specified data source exists in or
     *         an empty string if a device id could not be retrieved
     */
    String getCaseUUIDForRow(int rowIdx) {
        //if anything would prevent this from working we will return an empty string 
        if (dataSourceSet.isEmpty() || rowIdx < 0
                || rowIdx >= dataSourceSet.size()
                || !(dataSourceSet.toArray()[rowIdx] instanceof DataSourceColumnItem)) {
            return "";
        }
        return ((DataSourceColumnItem) dataSourceSet.toArray()[rowIdx]).getCaseUUID();
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
    void addNodeData(NodeData newNodeData) {
        String caseUUID;
        try {
            caseUUID = newNodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID();
        } catch (CentralRepoException ignored) {
            //non central repo nodeData won't have a correlation case
            try {
                caseUUID = Case.getCurrentCaseThrows().getName();
                //place holder value will be used since correlation attribute was unavailble
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get current case", ex);
                caseUUID = OtherOccurrences.getPlaceholderUUID();
            }
        }
        dataSourceSet.add(new DataSourceColumnItem(newNodeData.getCaseName(), newNodeData.getDeviceID(), newNodeData.getDataSourceName(), caseUUID));
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
        private final String caseUUID;

        /**
         * Create a DataSourceColumnItem given a case name, device id, and data
         * source name
         *
         * @param caseName       the name of the case the data source exists in
         * @param deviceId       the device id for the data source
         * @param dataSourceName the name of the data source
         * @param caseUUID       the case uuid for the case the data source
         *                       exists in
         */
        private DataSourceColumnItem(String caseName, String deviceId, String dataSourceName, String caseUUID) {
            this.caseName = caseName;
            this.deviceId = deviceId;
            this.dataSourceName = dataSourceName;
            this.caseUUID = caseUUID;
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

        /**
         * Get the case uuid of the case the data source exists in
         *
         * @return the case UUID
         */
        private String getCaseUUID() {
            return caseUUID;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DataSourceColumnItem
                    && caseName.equals(((DataSourceColumnItem) other).getCaseName())
                    && dataSourceName.equals(((DataSourceColumnItem) other).getDataSourceName())
                    && deviceId.equals(((DataSourceColumnItem) other).getDeviceId())
                    && caseUUID.equals(((DataSourceColumnItem) other).getCaseUUID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(caseName, deviceId, dataSourceName, caseUUID);
        }
    }

}
