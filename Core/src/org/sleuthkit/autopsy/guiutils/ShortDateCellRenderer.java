/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
import java.text.SimpleDateFormat;
import javax.swing.JTable;
import static javax.swing.SwingConstants.CENTER;

/**
 * A JTable cell renderer that renders a date represented as a long as a
 * center-aligned, short-format date string. It also grays out the cell if the
 * table is disabled.
 */
class ShortDateCellRenderer extends GrayableCellRenderer {

    private static final long serialVersionUID = 1L;
    private static final String FORMAT_STRING = "MM/dd HH:mm"; //NON-NLS
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat(FORMAT_STRING);

    public ShortDateCellRenderer() {
        setHorizontalAlignment(CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value != null) {
            setText(dateFormat.format(value));
        }
        grayCellIfTableNotEnabled(table, isSelected);
        return this;
    }

    void grayCellIfTableNotEnabled(JTable table, boolean isSelected) {
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
