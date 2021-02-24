/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.util.List;

/**
 *
 * @author gregd
 */
public class TableTemplate<T, C extends CellModel> {

    private final List<ColumnModel<T, C>> columns;
    private final String tabName;

    public TableTemplate(List<ColumnModel<T, C>> columns, String tabName) {
        this.columns = columns;
        this.tabName = tabName;
    }

    public List<ColumnModel<T, C>> getColumns() {
        return columns;
    }

    public String getTabName() {
        return tabName;
    }
}
