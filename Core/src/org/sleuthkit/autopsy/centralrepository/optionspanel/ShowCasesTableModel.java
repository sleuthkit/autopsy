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

/**
 * Model for cells to display correlation case information
 */
public class ShowCasesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    @Messages({"ShowCasesTableModel.case=Case Name",
        "ShowCasesTableModel.creationDate=Creation Date",
        "ShowCasesTableModel.caseNumber=Case Number",
        "ShowCasesTableModel.examinerName=Examiner Name",
        "ShowCasesTableModel.examinerEmail=Examiner Email",
        "ShowCasesTableModel.examinerPhone=Examiner Phone",
        "ShowCasesTableModel.notes=Notes",
        "ShowCasesTableModel.noData=No Cases"})
    /**
     * Enum which lists columns of interest from CorrelationCase.
     */
    enum TableColumns {
        // Ordering here determines displayed column order in Content Viewer.
        // If order is changed, update the CellRenderer to ensure correct row coloring.
        CASE_NAME(Bundle.ShowCasesTableModel_case(), 200),
        CREATION_DATE(Bundle.ShowCasesTableModel_creationDate(), 150),
        CASE_NUMBER(Bundle.ShowCasesTableModel_caseNumber(), 100),
        EXAMINER_NAME(Bundle.ShowCasesTableModel_examinerName(), 200),
        EXAMINER_EMAIL(Bundle.ShowCasesTableModel_examinerEmail(), 100),
        EXAMINER_PHONE(Bundle.ShowCasesTableModel_examinerPhone(), 100),
        NOTES(Bundle.ShowCasesTableModel_notes(), 450);

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

    /**
     * list of Eam Cases from central repository.
     */
    private List<CorrelationCase> eamCases;

    ShowCasesTableModel() {
        eamCases = new ArrayList<>();
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
        return eamCases.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return TableColumns.values()[colIdx].columnName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (eamCases.isEmpty()) {
            return Bundle.ShowCasesTableModel_noData();
        }

        return mapValueById(rowIdx, TableColumns.values()[colIdx]);
    }

    public Object getRow(int rowIdx) {
        return eamCases.get(rowIdx);
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
        CorrelationCase eamCase = eamCases.get(rowIdx);
        String value = Bundle.ShowCasesTableModel_noData();

        switch (colId) {
            case CASE_NAME:
                value = eamCase.getDisplayName();
                break;
            case CREATION_DATE:
                value = eamCase.getCreationDate();
                break;
            case CASE_NUMBER:
                value = eamCase.getCaseNumber();
                break;
            case EXAMINER_NAME:
                value = eamCase.getExaminerName();
                break;
            case EXAMINER_EMAIL:
                value = eamCase.getExaminerEmail();
                break;
            case EXAMINER_PHONE:
                value = eamCase.getExaminerPhone();
                break;
            case NOTES:
                value = eamCase.getNotes();
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
     * @param eamCase central repository case to add to the
     *                   table
     */
    public void addEamCase(CorrelationCase eamCase) {
        eamCases.add(eamCase);
        fireTableDataChanged();
    }

    public void clearTable() {
        eamCases.clear();
        fireTableDataChanged();
    }



}
