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
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import org.apache.commons.lang3.StringUtils;

/**
 * A Table cell renderer that renders a cell of a table based off of the
 * CellModel interface provided within this class.
 */
public class CellModelTableCellRenderer extends DefaultTableCellRenderer {

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
         *                        number.
         */
        HorizontalAlign(int jlabelAlignment) {
            this.jlabelAlignment = jlabelAlignment;
        }

        /**
         * @return The corresponding JLabel horizontal alignment (i.e.
         *         JLabel.LEFT).
         */
        int getJLabelAlignment() {
            return this.jlabelAlignment;
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
    }

    /**
     * The default cell model.
     */
    public static class DefaultCellModel implements CellModel {

        private final String text;
        private String tooltip;
        private HorizontalAlign horizontalAlignment;

        /**
         * Main constructor.
         *
         * @param text The text to be displayed in the cell.
         */
        public DefaultCellModel(String text) {
            this.text = text;
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
        public String toString() {
            return getText();
        }
    }

    private static int DEFAULT_ALIGNMENT = JLabel.LEFT;

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
     *                    jtable.
     * @param cellModel   The cell model for this cell.
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

        // sets the JLabel alignment (left, center, right) or default alignment
        // if no alignment is specified
        int alignment = (cellModel.getHorizontalAlignment() == null)
                ? DEFAULT_ALIGNMENT
                : cellModel.getHorizontalAlignment().getJLabelAlignment();
        defaultCell.setHorizontalAlignment(alignment);

        return defaultCell;
    }
}
