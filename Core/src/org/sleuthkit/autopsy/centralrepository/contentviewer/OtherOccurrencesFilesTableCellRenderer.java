/*
 * Central Repository
 *
 * Copyright 2015-2019 Basis Technology Corp.
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

import java.awt.Color;
import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * Renderer for cells in the files section of the other occurrences data content viewer
 */
public class OtherOccurrencesFilesTableCellRenderer implements TableCellRenderer {

    public static final DefaultTableCellRenderer DEFAULT_RENDERER = new DefaultTableCellRenderer();

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {
        Component renderer = DEFAULT_RENDERER.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);
        ((JComponent) renderer).setOpaque(true);
        Color foreground, background;
        if (isSelected) {
            foreground = Color.WHITE;
            background = new Color(51,153,255);
        } else {
//            TskData.FileKnown knownStatus = ((OtherOccurrencesFilesTableModel)table.getModel()).getKnownStatusForRow(table.convertRowIndexToModel(row));
//            if (knownStatus.equals(TskData.FileKnown.BAD)) {
//                    foreground = Color.WHITE;
//                    background = Color.RED;
//            } else if (knownStatus.equals(TskData.FileKnown.UNKNOWN)) {
//                    foreground = Color.BLACK;
//                    background = Color.WHITE;
//            } else {
                    foreground = Color.BLACK;
                    background = Color.WHITE;
//            }
        }
        renderer.setForeground(foreground);
        renderer.setBackground(background);
        return renderer;
    }
}
