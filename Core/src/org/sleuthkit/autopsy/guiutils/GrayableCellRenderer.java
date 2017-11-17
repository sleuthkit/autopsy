/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.guiutils;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import static javax.swing.SwingConstants.LEFT;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * A JTable cell renderer that left-aligns cell content and grays out the cell
 * if the table is disabled.
 */
public class GrayableCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    public GrayableCellRenderer() {
        setHorizontalAlignment(LEFT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (null != value) {
            setText(value.toString());
        }
        grayCellIfTableNotEnabled(table, isSelected);
        return this;
    }

    public void grayCellIfTableNotEnabled(JTable table, boolean isSelected) {
        if (table.isEnabled()) {
            /*
             * The table is enabled, make the foreground and background the
             * normal selected or unselected color.
             */
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }
        } else {
            /*
             * The table is disabled, make the foreground and background gray.
             */
            setBackground(Color.lightGray);
            setForeground(Color.darkGray);
        }
    }
    
}
