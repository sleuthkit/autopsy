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

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.table.TableColumnModel;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.CellModel;

/**
 * Utility methods for instantiating aspects of a DataResultTable.
 */
public class DataResultTableUtils {

    /**
     * Describes aspects of a column which can be used with getTableModel or
     * getDataResultTable. 'T' represents the object that will represent rows in
     * the table.
     */
    public static class DataResultColumnModel<T> {

        private final String headerTitle;
        private final Function<T, CellModel> cellRenderer;
        private final Integer width;

        /**
         * Constructor for a DataResultColumnModel.
         *
         * @param headerTitle  The title for the column.
         * @param cellRenderer The method that generates a CellModel for the
         *                     column based on the data.
         */
        public DataResultColumnModel(String headerTitle, Function<T, CellModel> cellRenderer) {
            this(headerTitle, cellRenderer, null);
        }

        /**
         * Constructor for a DataResultColumnModel.
         *
         * @param headerTitle  The title for the column.
         * @param cellRenderer The method that generates a CellModel for the
         *                     column based on the data.
         * @param width        The preferred width of the column.
         */
        public DataResultColumnModel(String headerTitle, Function<T, CellModel> cellRenderer, Integer width) {
            this.headerTitle = headerTitle;
            this.cellRenderer = cellRenderer;
            this.width = width;
        }

        /**
         * @return The title for the column.
         */
        public String getHeaderTitle() {
            return headerTitle;
        }

        /**
         * @return The method that generates a CellModel for the column based on
         *         the data.
         */
        public Function<T, CellModel> getCellRenderer() {
            return cellRenderer;
        }

        /**
         * @return The preferred width of the column (can be null).
         */
        public Integer getWidth() {
            return width;
        }
    }

    private static final CellModelTableCellRenderer renderer = new CellModelTableCellRenderer();

    /**
     * Generates a TableColumnModel based on the column definitions.
     *
     * @param columns The column definitions.
     *
     * @return The corresponding TableColumnModel to be used with a JTable.
     */
    public static <T> TableColumnModel getTableColumnModel(List<DataResultColumnModel<T>> columns) {
        TableColumnModel tableModel = new DefaultTableColumnModel();

        for (int i = 0; i < columns.size(); i++) {
            TableColumn col = new TableColumn(i);
            DataResultColumnModel<T> model = columns.get(i);
            // if a preferred width is specified in the column definition, 
            // set the underlying TableColumn preferred width.
            if (model.getWidth() != null && model.getWidth() >= 0) {
                col.setPreferredWidth(model.getWidth());
            }

            // set the title
            col.setHeaderValue(model.getHeaderTitle());

            // use the cell model renderer in this instance
            col.setCellRenderer(renderer);

            tableModel.addColumn(col);
        }

        return tableModel;
    }

    /**
     * Generates a ListTableModel based on the column definitions provided where
     * 'T' is the object representing each row.
     *
     * @param columns The column definitions.
     *
     * @return The corresponding ListTableModel.
     */
    public static <T> ListTableModel<T> getTableModel(List<DataResultColumnModel<T>> columns) {
        List<Function<T, ? extends Object>> columnRenderers = columns.stream()
                .map((colModel) -> colModel.getCellRenderer())
                .collect(Collectors.toList());

        return new DefaultListTableModel<T>(columnRenderers);
    }

    /**
     * Generates a DataResultTable corresponding to the provided column
     * definitions where 'T' is the object representing each row.
     *
     * @param columns The column definitions.
     *
     * @return The corresponding DataResultTable.
     */
    public static <T> DataResultTable<T> getDataResultTable(List<DataResultColumnModel<T>> columns) {
        ListTableModel<T> tableModel = getTableModel(columns);
        DataResultTable<T> resultTable = new DataResultTable<>(tableModel);
        return resultTable.setColumnModel(getTableColumnModel(columns));
    }

    private DataResultTableUtils() {
    }
}
