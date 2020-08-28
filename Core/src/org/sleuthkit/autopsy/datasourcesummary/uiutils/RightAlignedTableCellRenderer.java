/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JTable;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.guiutils.GrayableCellRenderer;

/**
 * Cell Renderer which extends grayable cell renderer to inherit highlighting
 * but right aligns the contents will display the value of the cell or if the
 * cell contains a NodeProperty the value of that NodeProperty sets text to
 * empty string if null.
 */
public class RightAlignedTableCellRenderer extends GrayableCellRenderer {

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
