/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.directorytree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Model that stores the External Viewer rules for Mime types and extensions.
 * Located at Tools -> Options -> External Viewer
 */
class ExternalViewerGlobalSettingsTableModel extends AbstractTableModel {

    private final List<ExternalViewerRule> rules;
    private final String[] columnNames;

    public ExternalViewerGlobalSettingsTableModel(String... columnNames) {
        this.columnNames = Arrays.copyOf(columnNames, columnNames.length);
        this.rules = new ArrayList<>();
    }

    /**
     * Stores a new external viewer rule.
     *
     * @param rule User-inputted rule
     */
    public void addRule(ExternalViewerRule rule) {
        rules.add(rule);
        fireTableRowsInserted(getRowCount() - 1, getRowCount() - 1);
    }

    /**
     * Returns the number of rules stored in this model.
     *
     * @return Integer denoting row count in table model
     */
    @Override
    public int getRowCount() {
        return rules.size();
    }

    /**
     * Returns the column name at the given index.
     *
     * @param columnIndex
     *
     * @return Column name
     */
    @Override
    public String getColumnName(int columnIndex) {
        return columnNames[columnIndex];
    }

    /**
     * Retrieves column class type. As for now, this is only type string.
     *
     * @param columnIndex
     *
     * @return String.class
     */
    @Override
    public Class<String> getColumnClass(int columnIndex) {
        return String.class;
    }

    /**
     * Retrieves the number of columns in this table model.
     *
     * @return Integer denoting column count
     */
    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    /**
     * Retrieves value at a given row and column in the table.
     *
     * @param rowIndex    Desired row index
     * @param columnIndex Desired column index
     *
     * @return A generic pointer to the underlying data.
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            return rules.get(rowIndex).getName();
        } else {
            return rules.get(rowIndex).getExePath();
        }
    }

    /**
     * Gets an entire rule instance from a given index.
     *
     * @param rowIndex Desired row
     *
     * @return User-inputted rule at the desired rowIndex
     */
    public ExternalViewerRule getRuleAt(int rowIndex) {
        return rules.get(rowIndex);
    }

    /**
     * Replaces an existing rule in the table.
     *
     * @param rowIndex Desired row index
     * @param rule     New rule to overwrite the old.
     */
    public void setRule(int rowIndex, ExternalViewerRule rule) {
        rules.set(rowIndex, rule);
        fireTableDataChanged();
    }

    /**
     * Removes the rule from the table model.
     *
     * @param rowIndex Desired row index to delete
     */
    public void removeRule(int rowIndex) {
        rules.remove(rowIndex);
        fireTableDataChanged();
    }

    /**
     * This table model is not editable.
     *
     * @param rowIndex
     * @param colIndex
     *
     * @return False
     */
    @Override
    public boolean isCellEditable(int rowIndex, int colIndex) {
        return false;
    }

    /**
     * Tests containment of a given rule in the table model.
     * 
     * @param rule Rule in question
     *
     * @return
     */
    public boolean containsRule(ExternalViewerRule rule) {
        return rules.contains(rule);
    }
}
