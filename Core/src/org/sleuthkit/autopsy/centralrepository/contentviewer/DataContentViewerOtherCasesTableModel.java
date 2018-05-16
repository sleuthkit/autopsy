/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;

/**
 * Model for cells in data content viewer table
 */
public class DataContentViewerOtherCasesTableModel extends AbstractTableModel {

    @Messages({"DataContentViewerOtherCasesTableModel.case=Case",
        "DataContentViewerOtherCasesTableModel.device=Device",
        "DataContentViewerOtherCasesTableModel.dataSource=Data Source",
        "DataContentViewerOtherCasesTableModel.path=Path",
        "DataContentViewerOtherCasesTableModel.type=Correlation Type",
        "DataContentViewerOtherCasesTableModel.value=Correlation Value",
        "DataContentViewerOtherCasesTableModel.known=Known",
        "DataContentViewerOtherCasesTableModel.comment=Comment",
        "DataContentViewerOtherCasesTableModel.noData=No Data.",})
    enum TableColumns {
        // Ordering here determines displayed column order in Content Viewer.
        // If order is changed, update the CellRenderer to ensure correct row coloring.
        CASE_NAME(Bundle.DataContentViewerOtherCasesTableModel_case(), 100),
        DATA_SOURCE(Bundle.DataContentViewerOtherCasesTableModel_dataSource(), 100),
        TYPE(Bundle.DataContentViewerOtherCasesTableModel_type(), 100),
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

    List<CorrelationAttribute> eamArtifacts;

    DataContentViewerOtherCasesTableModel() {
        eamArtifacts = new ArrayList<>();
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
        return eamArtifacts.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return TableColumns.values()[colIdx].columnName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (0 == eamArtifacts.size()) {
            return Bundle.DataContentViewerOtherCasesTableModel_noData();
        }

        return mapValueById(rowIdx, TableColumns.values()[colIdx]);
    }

    public Object getRow(int rowIdx) {
        return eamArtifacts.get(rowIdx);
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
        CorrelationAttribute eamArtifact = eamArtifacts.get(rowIdx);
        CorrelationAttributeInstance eamArtifactInstance = eamArtifact.getInstances().get(0);
        String value = Bundle.DataContentViewerOtherCasesTableModel_noData();

        switch (colId) {
            case CASE_NAME:
                if (null != eamArtifactInstance.getCorrelationCase()) {
                    value = eamArtifactInstance.getCorrelationCase().getDisplayName();
                }
                break;
            case DEVICE:
                if (null != eamArtifactInstance.getCorrelationDataSource()) {
                    value = eamArtifactInstance.getCorrelationDataSource().getDeviceID();
                }
                break;
            case DATA_SOURCE:
                if (null != eamArtifactInstance.getCorrelationDataSource()) {
                    value = eamArtifactInstance.getCorrelationDataSource().getName();
                }
                break;
            case FILE_PATH:
                value = eamArtifactInstance.getFilePath();
                break;
            case TYPE:
                value = eamArtifact.getCorrelationType().getDisplayName();
                break;
            case VALUE:
                value = eamArtifact.getCorrelationValue();
                break;
            case KNOWN:
                value = eamArtifactInstance.getKnownStatus().getName();
                break;
            case COMMENT:
                value = eamArtifactInstance.getComment();
                break;
        }
        return value;
    }

    @Override
    public Class<String> getColumnClass(int colIdx) {
        return String.class;
    }

    /**
     * Add one local central repository artifact to the table.
     *
     * @param eamArtifact central repository artifact to add to the
     *                   table
     */
    public void addEamArtifact(CorrelationAttribute eamArtifact) {
        eamArtifacts.add(eamArtifact);
        fireTableDataChanged();
    }

    public void clearTable() {
        eamArtifacts.clear();
        fireTableDataChanged();
    }

}
