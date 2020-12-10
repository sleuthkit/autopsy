/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.CellMouseEvent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.CellMouseListener;

/**
 * A Table cell renderer that renders a cell of a table based off of the
 * CellModel interface provided within this class.
 */
public class CellModelTableCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    /**
     * Describes the horizontal alignment.
     */
    public enum HorizontalAlign {
        LEFT(JLabel.LEFT),
        CENTER(JLabel.CENTER),
        RIGHT(JLabel.RIGHT);

        private final int jlabelAlignment;

        /**
         * Constructor for a HorizontalAlign enum.
         *
         * @param jlabelAlignment The corresponding JLabel horizontal alignment
         * number.
         */
        HorizontalAlign(int jlabelAlignment) {
            this.jlabelAlignment = jlabelAlignment;
        }

        /**
         * @return The corresponding JLabel horizontal alignment (i.e.
         * JLabel.LEFT).
         */
        int getJLabelAlignment() {
            return this.jlabelAlignment;
        }
    }

    /**
     * A menu item to be used within a popup menu.
     */
    public interface MenuItem {

        /**
         * @return The title for that popup menu item.
         */
        String getTitle();

        /**
         * @return The action if that popup menu item is clicked.
         */
        Runnable getAction();
    }

    /**
     * Default implementation of a menu item.
     */
    public static class DefaultMenuItem implements MenuItem {

        private final String title;
        private final Runnable action;

        /**
         * Main constructor.
         *
         * @param title The title for the menu item.
         * @param action The action should the menu item be clicked.
         */
        public DefaultMenuItem(String title, Runnable action) {
            this.title = title;
            this.action = action;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public Runnable getAction() {
            return action;
        }

    }

    /**
     * Basic interface for a cell model.
     */
    public interface CellModel {

        /**
         * @return The text to be shown in the cell.
         */
        String getText();

        /**
         * @return The tooltip (if any) to be displayed in the cell.
         */
        String getTooltip();

        /**
         * @return The horizontal alignment for the text in the cell.
         */
        HorizontalAlign getHorizontalAlignment();

        /**
         * @return The insets for the cell text.
         */
        Insets getInsets();

        /**
         * @return The popup menu associated with this cell or null if no popup
         * menu should be shown for this cell.
         */
        List<MenuItem> getPopupMenu();
    }

    /**
     * The default cell model.
     */
    public static class DefaultCellModel implements CellModel {

        private final String text;
        private String tooltip;
        private HorizontalAlign horizontalAlignment;
        private Insets insets;
        private List<MenuItem> popupMenu;
        private Supplier<List<MenuItem>> menuItemSupplier;

        /**
         * Main constructor.
         *
         * @param text The text to be displayed in the cell.
         */
        public DefaultCellModel(String text) {
            this.text = text;
            this.tooltip = text;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public String getTooltip() {
            return tooltip;
        }

        /**
         * Sets the tooltip for this cell model.
         *
         * @param tooltip The tooltip for the cell model.
         *
         * @return As a utility, returns this.
         */
        public DefaultCellModel setTooltip(String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        @Override
        public HorizontalAlign getHorizontalAlignment() {
            return horizontalAlignment;
        }

        /**
         * Sets the horizontal alignment for this cell model.
         *
         * @param alignment The horizontal alignment for the cell model.
         *
         * @return As a utility, returns this.
         */
        public DefaultCellModel setHorizontalAlignment(HorizontalAlign alignment) {
            this.horizontalAlignment = alignment;
            return this;
        }

        @Override
        public Insets getInsets() {
            return insets;
        }

        /**
         * Sets the insets for the text within the cell
         *
         * @param insets The insets.
         *
         * @return As a utility, returns this.
         */
        public DefaultCellModel setInsets(Insets insets) {
            this.insets = insets;
            return this;
        }

        @Override
        public List<MenuItem> getPopupMenu() {
            if (popupMenu != null) {
                return Collections.unmodifiableList(popupMenu);
            }

            if (menuItemSupplier != null) {
                return this.menuItemSupplier.get();
            }

            return null;
        }

        /**
         * Sets a function to lazy load the popup menu items.
         *
         * @param menuItemSupplier The lazy load function for popup items.
         * @return
         */
        public DefaultCellModel setPopupMenuRetriever(Supplier<List<MenuItem>> menuItemSupplier) {
            this.menuItemSupplier = menuItemSupplier;
            return this;
        }

        /**
         * Sets the list of items for a popup menu
         *
         * @param popupMenu
         * @return As a utility, returns this.
         */
        public DefaultCellModel setPopupMenu(List<MenuItem> popupMenu) {
            this.popupMenu = popupMenu == null ? null : new ArrayList<>(popupMenu);
            return this;
        }

        @Override
        public String toString() {
            return getText();
        }
    }

    private static final int DEFAULT_ALIGNMENT = JLabel.LEFT;
    private static final Border DEFAULT_BORDER = BorderFactory.createEmptyBorder(1, 5, 1, 5);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {

        JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof CellModel) {
            return getTableCellRendererComponent(c, (CellModel) value);
        } else {
            return c;
        }

    }

    /**
     * Customizes the jlabel to match the column model and cell model provided.
     *
     * @param defaultCell The cell to customize that will be displayed in the
     * jtable.
     * @param cellModel The cell model for this cell.
     *
     * @return The provided defaultCell.
     */
    protected Component getTableCellRendererComponent(JLabel defaultCell, CellModel cellModel) {
        // sets the text for the cell or null if not present.
        String text = cellModel.getText();
        if (StringUtils.isNotBlank(text)) {
            defaultCell.setText(text);
        } else {
            defaultCell.setText(null);
        }

        // sets the tooltip for the cell if present.
        String tooltip = cellModel.getTooltip();
        if (StringUtils.isNotBlank(tooltip)) {
            defaultCell.setToolTipText(tooltip);
        } else {
            defaultCell.setToolTipText(null);
        }

        // sets the padding for cell text within the cell.
        Insets insets = cellModel.getInsets();
        if (insets != null) {
            defaultCell.setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
        } else {
            defaultCell.setBorder(DEFAULT_BORDER);
        }

        // sets the JLabel alignment (left, center, right) or default alignment
        // if no alignment is specified
        int alignment = (cellModel.getHorizontalAlignment() == null)
                ? DEFAULT_ALIGNMENT
                : cellModel.getHorizontalAlignment().getJLabelAlignment();
        defaultCell.setHorizontalAlignment(alignment);

        return defaultCell;
    }

    /**
     * The default cell mouse listener that triggers popups for non-primary
     * button events.
     */
    private static final CellMouseListener DEFAULT_CELL_MOUSE_LISTENER = new CellMouseListener() {

        @Override
        public void mouseClicked(CellMouseEvent cellEvent) {
            if (cellEvent.getCellValue() instanceof CellModel && cellEvent.getMouseEvent().getButton() != MouseEvent.BUTTON1) {
                cellEvent.getTable().setRowSelectionInterval(cellEvent.getRow(), cellEvent.getRow());
                CellModel cellModel = (CellModel) cellEvent.getCellValue();
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
     * non-primary button events.
     */
    public static CellMouseListener getMouseListener() {
        return DEFAULT_CELL_MOUSE_LISTENER;
    }
}
