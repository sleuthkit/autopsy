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
import org.openide.util.NbBundle.Messages;

public class OtherOccurrencesCasesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private final List<CorrelationCaseWrapper> correlationCaseList = new ArrayList<>();

    OtherOccurrencesCasesTableModel() {
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
        return correlationCaseList.size();
    }

    @Override
    public String getColumnName(int colIdx) {
        return TableColumns.values()[colIdx].columnName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (0 == correlationCaseList.size()) {
            return Bundle.OtherOccurrencesCasesTableModel_noData();
        }

        CorrelationCaseWrapper caseWrapper = correlationCaseList.get(rowIdx);
        TableColumns columnId = TableColumns.values()[colIdx];
        return mapCorrelationCase(caseWrapper, columnId);
    }

    /**
     * Map a column ID to the value in that cell for node instance data.
     *
     * @param correlationCaseWrapper The node instance data.
     * @param columnId The ID of the cell column.
     *
     * @return The value in the cell.
     */
    @Messages({"OtherOccurrencesCasesTableModel.noData=No Data."})
    private Object mapCorrelationCase(CorrelationCaseWrapper correlationCaseWrapper, TableColumns columnId) {
        String value = Bundle.OtherOccurrencesCasesTableModel_noData();

        switch (columnId) {
            case CASE_NAME:
                value = correlationCaseWrapper.getMessage();
                break;
            default: //Use default "No data" value.
                break;
        }
        return value;
    }

    Object getRow(int rowIdx) {
        return correlationCaseList.get(rowIdx);
    }

    @Override
    public Class<String> getColumnClass(int colIdx) {
        return String.class;
    }

    /**
     * Add one correlated instance object to the table
     *
     * @param newCorrelationCaseWrapper data to add to the table
     */
    void addCorrelationCase(CorrelationCaseWrapper newCorrelationCaseWrapper) {
        correlationCaseList.add(newCorrelationCaseWrapper);
        fireTableDataChanged();
    }

    /**
     * Clear the node data table.
     */
    void clearTable() {
        correlationCaseList.clear();
        fireTableDataChanged();
    }

    @Messages({"OtherOccurrencesCasesTableModel.case=Case",})
    enum TableColumns {
        // Ordering here determines displayed column order in Content Viewer.
        // If order is changed, update the CellRenderer to ensure correct row coloring.
        CASE_NAME(Bundle.OtherOccurrencesCasesTableModel_case(), 100);

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
    }
}
