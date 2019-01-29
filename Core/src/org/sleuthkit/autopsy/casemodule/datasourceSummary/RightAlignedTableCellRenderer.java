/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JTable;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.guiutils.GrayableCellRenderer;

class RightAlignedTableCellRenderer extends GrayableCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setHorizontalAlignment(RIGHT);
        Object cellContents = null;
        if ((value instanceof NodeProperty)) {
            //The Outline view has properties in the cell, the value contained in the property is what we want
            try {
                cellContents = ((Node.Property) value).getValue();
            } catch (IllegalAccessException | InvocationTargetException ex) {
                //Unable to get the value from the NodeProperty cell will appear empty
            }
        } else {
            //JTables contain the value we want directly in the cell
            cellContents = value;
        }
        if (null != cellContents) {
            setText(cellContents.toString());
        } else {
            setText("");
        }
        grayCellIfTableNotEnabled(table, isSelected);
        return this;
    }
}
