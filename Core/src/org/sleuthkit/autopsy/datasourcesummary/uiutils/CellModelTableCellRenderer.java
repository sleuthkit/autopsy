/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import org.apache.commons.collections.CollectionUtils;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.GuiCellModel.MenuItem;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.CellMouseEvent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.CellMouseListener;

/**
 * A Table cell renderer that renders a cell of a table based off of the
 * CellModel interface provided within this class.
 */
public class CellModelTableCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_ALIGNMENT = JLabel.LEFT;
    private static final Border DEFAULT_BORDER = BorderFactory.createEmptyBorder(2, 4, 2, 4);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {

        JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof GuiCellModel) {
            return getTableCellRendererComponent(c, (GuiCellModel) value);
        } else {
            return c;
        }

    }

    /**
     * Customizes the jlabel to match the column model and cell model provided.
     *
     * @param defaultCell The cell to customize that will be displayed in the
     *                    jtable.
     * @param cellModel   The cell model for this cell.
     *
     * @return The provided defaultCell.
     */
    protected Component getTableCellRendererComponent(JLabel defaultCell, GuiCellModel cellModel) {
        defaultCell.setText(cellModel.getText());
        defaultCell.setToolTipText(cellModel.getTooltip());
        // sets the JLabel alignment (left, center, right) or default alignment
        // if no alignment is specified
        int alignment = (cellModel.getHorizontalAlignment() == null)
                ? DEFAULT_ALIGNMENT
                : cellModel.getHorizontalAlignment().getJLabelAlignment();
        defaultCell.setHorizontalAlignment(alignment);
        defaultCell.setBorder(DEFAULT_BORDER);
        return defaultCell;
    }

    /**
     * The default cell mouse listener that triggers popups for non-primary
     * button events.
     */
    private static final CellMouseListener DEFAULT_CELL_MOUSE_LISTENER = new CellMouseListener() {

        @Override
        public void mouseClicked(CellMouseEvent cellEvent) {
            if (cellEvent.getCellValue() instanceof GuiCellModel && cellEvent.getMouseEvent().getButton() != MouseEvent.BUTTON1) {
                cellEvent.getTable().setRowSelectionInterval(cellEvent.getRow(), cellEvent.getRow());
                GuiCellModel cellModel = (GuiCellModel) cellEvent.getCellValue();
                List<MenuItem> menuItems = cellModel.getPopupMenu();

                // if there are menu items, show a popup menu for 
                // this item with all the menu items.
                if (CollectionUtils.isNotEmpty(menuItems)) {
                    final JPopupMenu popupMenu = new JPopupMenu();
                    for (MenuItem mItem : menuItems) {
                        JMenuItem jMenuItem = new JMenuItem(mItem.getTitle());
                        if (mItem.getAction() != null) {
                            jMenuItem.addActionListener((evt) -> mItem.getAction().run());
                        }
                        popupMenu.add(jMenuItem);
                    }
                    popupMenu.show(cellEvent.getTable(), cellEvent.getMouseEvent().getX(), cellEvent.getMouseEvent().getY());
                }
            }
        }
    };

    /**
     * @return The default cell mouse listener that triggers popups for
     *         non-primary button events.
     */
    public static CellMouseListener getMouseListener() {
        return DEFAULT_CELL_MOUSE_LISTENER;
    }
}
