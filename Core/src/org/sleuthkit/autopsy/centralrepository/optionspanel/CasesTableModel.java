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
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;

/**
 * Model for cells to display correlation case information
 */
class CasesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    /**
     * list of Eam Cases from central repository.
     */
    private final List<CaseDataSourcesWrapper> eamCases;

    /**
     * Model for cells to display correlation case information
     */
    CasesTableModel() {
        eamCases = new ArrayList<>();
    }

    @Override
    public int getColumnCount() {
        return CaseTableColumns.values().length;
    }

    @Override
    public int getRowCount() {
        return eamCases.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return CaseTableColumns.values()[colIdx].columnName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (eamCases.isEmpty()) {
            return Bundle.CasesTableModel_noData();
        }

        return mapValueById(rowIdx, CaseTableColumns.values()[colIdx]);
    }

    /**
     * Map a rowIdx and colId to the value in that cell.
     *
     * @param rowIdx Index of row to search
     * @param colId  ID of column to search
     *
     * @return value in the cell
     */
    @Messages({"CasesTableModel.noData=No Cases"})
    private Object mapValueById(int rowIdx, CaseTableColumns colId) {
        CaseDataSourcesWrapper eamCase = eamCases.get(rowIdx);
        String value = Bundle.CasesTableModel_noData();

        switch (colId) {
            case CASE_NAME:
                value = eamCase.getDisplayName();
                break;
            case CREATION_DATE:
                value = eamCase.getCreationDate();
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
    void addEamCase(CorrelationCase eamCase, List<CorrelationDataSource> dataSourceList) {
        eamCases.add(new CaseDataSourcesWrapper(eamCase, dataSourceList));
        fireTableDataChanged();
    }

    /**
     * Get the CaseDataSourcesWrapper for the specified index in the table.
     *
     * @param listIndex the idex of the object to get
     *
     * @return A caseDataSourcesWrapper containing the CorrelationCase and the
     *         CorrelationDataSources associated with it for the specified
     *         index.
     */
    CaseDataSourcesWrapper getEamCase(int listIndex) {
        return eamCases.get(listIndex);
    }

    /**
     * Enum which lists columns of interest from CorrelationCase.
     */
    @Messages({"CasesTableModel.case=Case Name",
        "CasesTableModel.creationDate=Creation Date"})
    private enum CaseTableColumns {
        // Ordering here determines displayed column order in Content Viewer.
        // If order is changed, update the CellRenderer to ensure correct row coloring.
        CASE_NAME(Bundle.CasesTableModel_case()),
        CREATION_DATE(Bundle.CasesTableModel_creationDate());

        private final String columnName;

        /**
         * Make a DataSourceTableColumns enum item.
         *
         * @param columnName the name of the column.s
         */
        CaseTableColumns(String columnName) {
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
