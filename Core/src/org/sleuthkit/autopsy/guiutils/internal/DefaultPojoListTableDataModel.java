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
package org.sleuthkit.autopsy.guiutils.internal;

import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.apache.commons.lang3.StringUtils;

/**
 * The default implementation of a table model for the DataResultJTable. This
 * class provides a TableModel and TableColumnModel to be used with that class.
 */
public class DefaultPojoListTableDataModel<T> extends AbstractTableModel implements PojoListTableDataModel<T> {

    /**
     * Describes the horizontal alignment.
     */
    public enum HorizontalAlign {
        LEFT, CENTER, RIGHT
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
    }

    /**
     * The default cell model.
     */
    public static class DefaultCellModel implements CellModel {

        private final String text;
        private String tooltip;

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
        public String toString() {
            return getText();
        }
    }

    /**
     * The column model for the table.
     */
    public interface ColumnModel<T> {

        /**
         * @return The column header title.
         */
        String getTitle();

        /**
         * @return A function to generate the contents for the cell based on the
         *         data in the POJO.
         */
        Function<T, CellModel> getCellCreator();

        /**
         * @return The width of the column to provide to the JTable. This can be
         *         left as null.
         */
        Integer getWidth();

        /**
         * @return The horizontal alignment for the text in the cell.
         */
        HorizontalAlign getCellHorizontalAlignment();
    }

    /**
     * The default implementation for the column model.
     */
    public static class DefaultColumnModel<T> implements ColumnModel<T> {

        private final String title;
        private final Function<T, CellModel> cellCreator;
        private Integer width;
        private HorizontalAlign cellHorizontalAlignment;

        /**
         * Main constructor.
         *
         * @param title     The title for the column header.
         * @param retriever Retrieves the value for cell from the POJO provided
         *                  in the row.
         */
        public DefaultColumnModel(String title, Function<T, String> retriever) {
            this((obj) -> new DefaultCellModel(retriever.apply(obj)), title);
        }

        /**
         * Main constructor.
         *
         * @param retriever Generates a cell model based on the POJO provided
         *                  for the row.
         * @param title     The title for the column header.
         */
        public DefaultColumnModel(Function<T, CellModel> retriever, String title) {
            this.title = title;
            this.cellCreator = retriever;
        }

        /**
         * @return The title for the column header.
         */
        public String getTitle() {
            return title;
        }

        /**
         * @return The means of converting the POJO for a row into a cell model
         *         for this column.
         */
        public Function<T, CellModel> getCellCreator() {
            return cellCreator;
        }

        /**
         * @return The width (if any) to specify for this column in the jtable.
         */
        public Integer getWidth() {
            return width;
        }

        /**
         * @return The horizontal alignment of the text in the cells if any.
         */
        public HorizontalAlign getCellHorizontalAlignment() {
            return cellHorizontalAlignment;
        }

        /**
         * Sets the width of the column to provide to the jtable. This method
         * should be called prior to being provided as an argument to the
         * DefaultPojoListTableDataModel.
         *
         * @param width The width of the column.
         *
         * @return As a utility, returns this.
         */
        public DefaultColumnModel<T> setWidth(Integer width) {
            this.width = width;
            return this;
        }

        /**
         * Sets the cell horizontal alignment for the cells in this column. This
         * method should be called prior to being provided as an argument to the
         * DefaultPojoListTableDataModel.
         *
         * @param cellHorizontalAlignment The alignment of text in the cell.
         *
         * @return As a utility, returns this.
         */
        public DefaultColumnModel<T> setCellHorizontalAlignment(HorizontalAlign cellHorizontalAlignment) {
            this.cellHorizontalAlignment = cellHorizontalAlignment;
            return this;
        }
    }

    private final List<ColumnModel<T>> columns;
    private List<T> dataRows = Collections.emptyList();

    /**
     * Main constructor
     *
     * @param columns The model for the columns in this table.
     */
    public DefaultPojoListTableDataModel(List<ColumnModel<T>> columns) {
        this.columns = Collections.unmodifiableList(columns);
    }

    @Override
    public TableColumnModel getTableColumnModel() {
        TableColumnModel toRet = new DefaultTableColumnModel();
        for (int i = 0; i < columns.size(); i++) {
            TableColumn col = new TableColumn(i);
            ColumnModel<T> model = columns.get(i);
            if (model.getWidth() != null && model.getWidth() >= 0) {
                col.setPreferredWidth(model.getWidth());
            }

            col.setHeaderValue(model.getTitle());

            col.setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                        JTable table, Object value,
                        boolean isSelected, boolean hasFocus,
                        int row, int column) {
                    JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (value instanceof CellModel) {
                        return DefaultPojoListTableDataModel.this.getTableCellRendererComponent(c, columns.get(column), (CellModel) value);
                    } else {
                        return c;
                    }
                }
            });
            toRet.addColumn(col);
        }
        return toRet;
    }

    /**
     * Customizes the jlabel to match the column model and cell model provided.
     * @param defaultCell The cell to customize that will be displayed in the jtable.
     * @param columnModel The column model for this cell.
     * @param cellModel The cell model for this cell.
     * @return The provided defaultCell.
     */
    protected Component getTableCellRendererComponent(JLabel defaultCell, ColumnModel columnModel, CellModel cellModel) {
        String text = cellModel.getText();
        if (StringUtils.isNotBlank(text)) {
            defaultCell.setText(text);
        } else {
            defaultCell.setText(null);
        }

        String tooltip = cellModel.getTooltip();
        if (StringUtils.isNotBlank(tooltip)) {
            defaultCell.setToolTipText(tooltip);
        } else {
            defaultCell.setToolTipText(null);
        }

        if (columnModel.getCellHorizontalAlignment() != null) {
            switch (columnModel.getCellHorizontalAlignment()) {
                case LEFT:
                    defaultCell.setHorizontalAlignment(JLabel.LEFT);
                    break;
                case CENTER:
                    defaultCell.setHorizontalAlignment(JLabel.CENTER);
                    break;
                case RIGHT:
                    defaultCell.setHorizontalAlignment(JLabel.RIGHT);
                    break;
            }
        } else {
            defaultCell.setHorizontalAlignment(JLabel.LEFT);   
        }

        return defaultCell;
    }

    @Override
    public List<T> getDataRows() {
        return dataRows;
    }

    @Override
    public void setDataRows(List<T> dataRows) {
        this.dataRows = dataRows == null ? Collections.emptyList() : Collections.unmodifiableList(dataRows);
        super.fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return dataRows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= dataRows.size() || columnIndex < 0 || columnIndex >= columns.size()) {
            return null;
        }

        return columns.get(columnIndex).getCellCreator().apply(dataRows.get(rowIndex));
    }

    @Override
    public String getColumnName(int column) {
        if (column < 0 || column >= columns.size()) {
            return null;
        }

        return columns.get(column).getTitle();
    }

}
