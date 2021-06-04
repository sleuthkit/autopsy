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

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.plaf.LayerUI;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * A table that displays a list of items and also can display messages for
 * loading, load error, and not loaded.
 */
public class JTablePanel<T> extends AbstractLoadableComponent<List<T>> {

    private static final int EXTRA_ROW_HEIGHT = 4;

    /**
     * An event that wraps a swing MouseEvent also providing context within the
     * table cell.
     */
    public static class CellMouseEvent {

        private final MouseEvent e;
        private final JTable table;
        private final int row;
        private final int col;
        private final Object cellValue;

        /**
         * Main constructor.
         *
         * @param e         The underlying mouse event.
         * @param table     The table that was the target of the mouse event.
         * @param row       The row within the table that the event occurs.
         * @param col       The column within the table that the event occurs.
         * @param cellValue The value within the cell.
         */
        public CellMouseEvent(MouseEvent e, JTable table, int row, int col, Object cellValue) {
            this.e = e;
            this.table = table;
            this.row = row;
            this.col = col;
            this.cellValue = cellValue;
        }

        /**
         * @return The underlying mouse event.
         */
        public MouseEvent getMouseEvent() {
            return e;
        }

        /**
         * @return The table that was the target of the mouse event.
         */
        public JTable getTable() {
            return table;
        }

        /**
         * @return The row within the table that the event occurs.
         */
        public int getRow() {
            return row;
        }

        /**
         * @return The column within the table that the event occurs.
         */
        public int getCol() {
            return col;
        }

        /**
         * @return The value within the cell.
         */
        public Object getCellValue() {
            return cellValue;
        }
    }

    /**
     * Handles mouse events for cells in the table.
     */
    public interface CellMouseListener {

        /**
         * Handles mouse events at a cell level for the table.
         *
         * @param e The event containing information about the cell, the mouse
         *          event, and the table.
         */
        void mouseClicked(CellMouseEvent e);
    }

    /**
     * This LayerUI is used to display the contents of a child JLabel. Inspired
     * by TableWaitLayerTest (Animating a Busy Indicator):
     * https://docs.oracle.com/javase/tutorial/uiswing/misc/jlayer.html.
     */
    private static class Overlay extends LayerUI<JComponent> {

        private static final long serialVersionUID = 1L;
        private final BaseMessageOverlay overlayDelegate = new BaseMessageOverlay();

        /**
         * Sets this layer visible when painted. In order to be shown in UI,
         * this component needs to be repainted.
         *
         * @param visible Whether or not it is visible.
         */
        void setVisible(boolean visible) {
            overlayDelegate.setVisible(visible);
        }

        /**
         * Sets the message to be displayed in the child jlabel.
         *
         * @param message The message to be displayed.
         */
        void setMessage(String message) {
            overlayDelegate.setMessage(message);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            super.paint(g, c);
            overlayDelegate.paintOverlay(g, c.getWidth(), c.getHeight());
        }
    }

    private static final long serialVersionUID = 1L;

    private static final CellModelTableCellRenderer DEFAULT_CELL_RENDERER = new CellModelTableCellRenderer();

    /**
     * Generates a TableColumnModel based on the column definitions.
     *
     * @param columns The column definitions.
     *
     * @return The corresponding TableColumnModel to be used with a JTable.
     */
    public static <T, C extends GuiCellModel> TableColumnModel getTableColumnModel(List<ColumnModel<T, C>> columns) {
        TableColumnModel tableModel = new DefaultTableColumnModel();

        for (int i = 0; i < columns.size(); i++) {
            TableColumn col = new TableColumn(i);
            ColumnModel<T, C> model = columns.get(i);
            // if a preferred width is specified in the column definition, 
            // set the underlying TableColumn preferred width.
            if (model.getWidth() != null && model.getWidth() >= 0) {
                col.setPreferredWidth(model.getWidth());
            }

            // set the title
            col.setHeaderValue(model.getHeaderTitle());

            // use the cell model renderer in this instance
            col.setCellRenderer(DEFAULT_CELL_RENDERER);

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
    public static <T, C extends GuiCellModel> ListTableModel<T> getTableModel(List<ColumnModel<T, C>> columns) {
        List<Function<T, ? extends Object>> columnRenderers = columns.stream()
                .map((colModel) -> colModel.getCellRenderer())
                .collect(Collectors.toList());

        return new DefaultListTableModel<>(columnRenderers);
    }

    /**
     * Generates a JTablePanel corresponding to the provided column definitions
     * where 'T' is the object representing each row.
     *
     * @param columns The column definitions.
     *
     * @return The corresponding JTablePanel.
     */
    public static <T, C extends GuiCellModel> JTablePanel<T> getJTablePanel(List<ColumnModel<T, C>> columns) {
        ListTableModel<T> tableModel = getTableModel(columns);
        JTablePanel<T> resultTable = new JTablePanel<>(tableModel)
                .setColumnModel(getTableColumnModel(columns))
                .setCellListener(CellModelTableCellRenderer.getMouseListener());

        return resultTable;
    }

    private JScrollPane tableScrollPane;
    private Overlay overlayLayer;
    private ListTableModel<T> tableModel;
    private JTable table;
    private CellMouseListener cellListener = null;
    private Function<T, ? extends Object> keyFunction = (rowItem) -> rowItem;

    /**
     * Panel constructor.
     *
     * @param tableModel The model to use for the table.
     */
    public JTablePanel(ListTableModel<T> tableModel) {
        this();
        setModel(tableModel);
        table.setRowHeight(table.getRowHeight() + EXTRA_ROW_HEIGHT);
    }

    /**
     * Default constructor.
     */
    public JTablePanel() {
        initComponents();
        this.table.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                // make sure click event isn't primary button and table is present
                if (cellListener != null) {
                    int row = table.rowAtPoint(e.getPoint());
                    int col = table.columnAtPoint(e.getPoint());

                    // make sure there is a value at the row,col of click event.
                    if (tableModel != null
                            && row >= 0 && row < tableModel.getRowCount()
                            && col >= 0 && col < tableModel.getColumnCount()) {

                        Object cellValue = tableModel.getValueAt(row, col);
                        cellListener.mouseClicked(new CellMouseEvent(e, table, row, col, cellValue));
                    }
                }
            }
        });
        table.setGridColor(javax.swing.UIManager.getDefaults().getColor("InternalFrame.borderColor"));
    }

    /**
     * Set the table model. This method must be called prior to calling
     * setResultList.
     *
     * @param tableModel
     *
     * @return As a utility, returns this.
     */
    public final JTablePanel<T> setModel(ListTableModel<T> tableModel) {
        if (tableModel == null) {
            throw new IllegalArgumentException("Null table model passed to setModel");
        }

        this.tableModel = tableModel;
        table.setModel(tableModel);
        return this;
    }

    /**
     * @return The current listener for mouse events. The events provided to
     *         this listener will have cell and table context.
     */
    public CellMouseListener getCellListener() {
        return cellListener;
    }

    /**
     * Sets the current listener for mouse events.
     *
     * @param cellListener The event listener that will receive these events
     *                     with cell and table context.
     *
     * @return
     */
    public JTablePanel<T> setCellListener(CellMouseListener cellListener) {
        this.cellListener = cellListener;
        return this;
    }

    /**
     * @return The underlying JTable's column model.
     */
    public TableColumnModel getColumnModel() {
        return this.table.getColumnModel();
    }

    /**
     * Sets the underlying JTable's column model.
     *
     * @param columnModel The table column model to use with the JTable.
     *
     * @return As a utility, returns this.
     */
    public JTablePanel<T> setColumnModel(TableColumnModel columnModel) {
        this.table.setColumnModel(columnModel);
        return this;
    }

    /**
     * @return The function for determining the key for a data row. This key is
     *         used to maintain current selection in the table despite changing
     *         rows.
     */
    public Function<T, ? extends Object> getKeyFunction() {
        return keyFunction;
    }

    /**
     * Sets the function for determining the key for a data row. This key is
     * used to maintain current selection in the table despite changing rows.
     *
     * @param keyFunction The function to determine the key of a row.
     *
     * @return As a utility, returns this.
     */
    public JTablePanel<T> setKeyFunction(Function<T, ? extends Object> keyFunction) {
        if (keyFunction == null) {
            throw new IllegalArgumentException("Key function must be non-null");
        }

        this.keyFunction = keyFunction;
        return this;
    }

    /**
     * Returns the selected items or null if no item is selected.
     *
     * @return The selected items or null if no item is selected.
     */
    public List<T> getSelectedItems() {
        int selectedRow = this.table.getSelectedRow();
        int count = this.table.getSelectedRowCount();
        if (selectedRow < 0 || this.tableModel == null || selectedRow + count > this.tableModel.getDataRows().size()) {
            return null;
        } else {
            return this.tableModel.getDataRows().subList(selectedRow, selectedRow + count);
        }
    }

    @Override
    protected synchronized void setResults(List<T> data) {
        // get previously selected value
        int prevSelectedRow = this.table.getSelectedRow();
        List<T> tableRows = this.tableModel.getDataRows();
        T prevValue = (tableRows != null && prevSelectedRow >= 0 && prevSelectedRow < tableRows.size())
                ? this.tableModel.getDataRows().get(prevSelectedRow)
                : null;

        Object prevKeyValue = (prevValue == null) ? null : this.keyFunction.apply(prevValue);

        // set the list of data to be shown as either the data or an empty list 
        // on null.
        List<T> dataToSet = (data == null) ? Collections.emptyList() : data;

        // set the underlying table model's data.
        this.tableModel.setDataRows(dataToSet);

        // set the row to selected value if the value is found
        if (prevKeyValue != null) {
            for (int objIndex = 0; objIndex < dataToSet.size(); objIndex++) {
                Object thisKey = this.keyFunction.apply(dataToSet.get(objIndex));
                if (prevKeyValue.equals(thisKey)) {
                    this.table.setRowSelectionInterval(objIndex, objIndex);
                    break;
                }
            }
        }

    }

    @Override
    protected void setMessage(boolean visible, String message) {
        this.overlayLayer.setVisible(visible);
        this.overlayLayer.setMessage(message);
    }

    /**
     * Initialize the gui components.
     */
    private void initComponents() {
        table = new JTable();
        table.getTableHeader().setReorderingAllowed(false);
        overlayLayer = new Overlay();
        tableScrollPane = new JScrollPane(table);
        JLayer<JComponent> dualLayer = new JLayer<>(tableScrollPane, overlayLayer);
        setLayout(new BorderLayout());
        add(dualLayer, BorderLayout.CENTER);
    }
}
