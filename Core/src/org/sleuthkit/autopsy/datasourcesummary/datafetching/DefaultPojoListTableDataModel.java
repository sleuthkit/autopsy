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
package org.sleuthkit.autopsy.datasourcesummary.datafetching;

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
 *
 * @author gregd
 */
class DefaultPojoListTableDataModel<T> extends AbstractTableModel implements PojoListTableDataModel<T> {
    enum HorizontalAlign { LEFT, CENTER, RIGHT }
        
    interface CellModel {
        String getText();
        String getTooltip();
    }

    static class DefaultCellModel implements CellModel {
        private final String text;
        private String tooltip;

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
        
        public DefaultCellModel setTooltip(String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        @Override
        public String toString() {
            return getText();
        }
    }

    interface ColumnModel<T> {
        String getTitle();
        Function<T, CellModel> getCellCreator();
        Integer getWidth();
        HorizontalAlign getCellHorizontalAlignment();
    }

    static class DefaultColumnModel<T> implements ColumnModel<T> {
        private final String title;
        private final Function<T, CellModel> cellCreator;
        private Integer width;
        private HorizontalAlign cellHorizontalAlignment;
        
        public DefaultColumnModel(String title, Function<T, String> retriever) {
            this((obj) -> new DefaultCellModel(retriever.apply(obj)), title);
        }

        public DefaultColumnModel(Function<T, CellModel> retriever, String title) {
            this.title = title;
            this.cellCreator = retriever;
        }

        public String getTitle() {
            return title;
        }

        public Function<T, CellModel> getCellCreator() {
            return cellCreator;
        }

        public Integer getWidth() {
            return width;
        }

        public HorizontalAlign getCellHorizontalAlignment() {
            return cellHorizontalAlignment;
        }

        public DefaultColumnModel<T> setWidth(Integer width) {
            this.width = width;
            return this;
        }

        public DefaultColumnModel<T> setCellHorizontalAlignment(HorizontalAlign cellHorizontalAlignment) {
            this.cellHorizontalAlignment = cellHorizontalAlignment;
            return this;
        }
    }

    private final List<ColumnModel<T>> columns;
    private List<T> dataRows = Collections.emptyList();

    DefaultPojoListTableDataModel(List<ColumnModel<T>> columns) {
        this.columns = Collections.unmodifiableList(columns);
    }

    @Override
    public TableColumnModel getTableColumnModel() {
        TableColumnModel toRet = new DefaultTableColumnModel();
        for (int i = 0; i < columns.size(); i++) {
            TableColumn col = new TableColumn(i);
            ColumnModel<T> model = columns.get(i);
            if (model.getWidth() != null && model.getWidth() >= 0) {
                col.setWidth(model.getWidth());
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

    
    protected Component getTableCellRendererComponent(JLabel defaultCell, ColumnModel columnModel, CellModel cellModel) {
        String text = cellModel.getText();
        if (StringUtils.isNotBlank(text)) {
            defaultCell.setText(text);
        }

        String tooltip = cellModel.getTooltip();
        if (StringUtils.isNotBlank(tooltip)) {
            defaultCell.setToolTipText(tooltip);
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
