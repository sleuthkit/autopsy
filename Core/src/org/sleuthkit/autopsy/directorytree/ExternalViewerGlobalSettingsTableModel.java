/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.directorytree;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
*
 */
class ExternalViewerGlobalSettingsTableModel extends AbstractTableModel {

    private final ArrayList<ExternalViewerRule> rules;
    private final String[] columnNames;

    public ExternalViewerGlobalSettingsTableModel(String[] columnNames) {
        this.columnNames = columnNames;
        this.rules = new ArrayList<>();
    }

    public void addRule(ExternalViewerRule rule) {
        rules.add(rule);
        fireTableRowsInserted(getRowCount() - 1, getRowCount() - 1);
    }

    @Override
    public int getRowCount() {
        return rules.size();
    }

    @Override
    public String getColumnName(int columnIndex) {
        return columnNames[columnIndex];
    }

    @Override
    public Class<String> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            return rules.get(rowIndex).getName();
        } else {
            return rules.get(rowIndex).getExePath();
        }
    }

    public ExternalViewerRule getRuleAt(int rowIndex) {
        return rules.get(rowIndex);
    }

    public void setRule(int rowIndex, ExternalViewerRule rule) {
        rules.set(rowIndex, rule);
        fireTableDataChanged();
    }

    public void removeRule(int rowIndex) {
        rules.remove(rowIndex);
        fireTableDataChanged();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int colIndex) {
        return false;
    }

    public List<ExternalViewerRule> getRules() {
        return rules;
    }

    public boolean containsRule(ExternalViewerRule rule) {
        return rules.contains(rule);
    }
}
