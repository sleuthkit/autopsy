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

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.plaf.LayerUI;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.CellModel;

/**
 * A table that displays a list of items and also can display messages for
 * loading, load error, and not loaded.
 */
@Messages({
    "JTablePanel_loadingMessage_defaultText=Loading results...",
    "JTablePanel_errorMessage_defaultText=There was an error loading results.",
    "JTablePanel_noDataExists_defaultText=No data exists.",})
public class JTablePanel<T> extends JPanel {

    /**
     * JTables don't allow displaying messages. So this LayerUI is used to
     * display the contents of a child JLabel. Inspired by TableWaitLayerTest
     * (Animating a Busy Indicator):
     * https://docs.oracle.com/javase/tutorial/uiswing/misc/jlayer.html.
     */
    private static class Overlay extends LayerUI<JComponent> {

        private static final long serialVersionUID = 1L;

        private final JLabel label;
        private boolean visible;

        /**
         * Main constructor for the Overlay.
         */
        Overlay() {
            label = new JLabel();
            label.setHorizontalAlignment(JLabel.CENTER);
            label.setVerticalAlignment(JLabel.CENTER);
            label.setOpaque(false);

        }

        /**
         * @return Whether or not this message overlay should be visible.
         */
        boolean isVisible() {
            return visible;
        }

        /**
         * Sets this layer visible when painted. In order to be shown in UI,
         * this component needs to be repainted.
         *
         * @param visible Whether or not it is visible.
         */
        void setVisible(boolean visible) {
            this.visible = visible;
        }

        /**
         * Sets the message to be displayed in the child jlabel.
         *
         * @param message The message to be displayed.
         */
        void setMessage(String message) {
            label.setText(String.format("<html><div style='text-align: center;'>%s</div></html>",
                    message == null ? "" : message));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            // Paint the underlying view.
            super.paint(g, c);

            if (!visible) {
                return;
            }

            int w = c.getWidth();
            int h = c.getHeight();

            // paint the jlabel if visible.
            label.setBounds(0, 0, w, h);
            label.paint(g);
        }
    }

    /**
     * Describes aspects of a column which can be used with getTableModel or
     * getJTablePanel. 'T' represents the object that will represent rows in the
     * table.
     */
    public static class ColumnModel<T> {

        private final String headerTitle;
        private final Function<T, CellModelTableCellRenderer.CellModel> cellRenderer;
        private final Integer width;

        /**
         * Constructor for a DataResultColumnModel.
         *
         * @param headerTitle  The title for the column.
         * @param cellRenderer The method that generates a CellModel for the
         *                     column based on the data.
         */
        public ColumnModel(String headerTitle, Function<T, CellModelTableCellRenderer.CellModel> cellRenderer) {
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
        public ColumnModel(String headerTitle, Function<T, CellModelTableCellRenderer.CellModel> cellRenderer, Integer width) {
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

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(JTablePanel.class.getName());

    private static final String DEFAULT_LOADING_MESSAGE = Bundle.JTablePanel_loadingMessage_defaultText();
    private static final String DEFAULT_ERROR_MESSAGE = Bundle.JTablePanel_errorMessage_defaultText();
    private static final String DEFAULT_NO_RESULTS_MESSAGE = Bundle.JTablePanel_noDataExists_defaultText();

    private static final CellModelTableCellRenderer DEFAULT_CELL_RENDERER = new CellModelTableCellRenderer();

    /**
     * Generates a TableColumnModel based on the column definitions.
     *
     * @param columns The column definitions.
     *
     * @return The corresponding TableColumnModel to be used with a JTable.
     */
    public static <T> TableColumnModel getTableColumnModel(List<ColumnModel<T>> columns) {
        TableColumnModel tableModel = new DefaultTableColumnModel();

        for (int i = 0; i < columns.size(); i++) {
            TableColumn col = new TableColumn(i);
            ColumnModel<T> model = columns.get(i);
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
    public static <T> ListTableModel<T> getTableModel(List<ColumnModel<T>> columns) {
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
    public static <T> JTablePanel<T> getJTablePanel(List<ColumnModel<T>> columns) {
        ListTableModel<T> tableModel = getTableModel(columns);
        JTablePanel<T> resultTable = new JTablePanel<>(tableModel);
        return resultTable.setColumnModel(getTableColumnModel(columns));
    }

    /**
     * @return The default error message.
     */
    public static String getDefaultErrorMessage() {
        return DEFAULT_ERROR_MESSAGE;
    }

    /**
     * @return The default message for no results.
     */
    public static String getDefaultNoResultsMessage() {
        return DEFAULT_NO_RESULTS_MESSAGE;
    }

    private JScrollPane tableScrollPane;
    private Overlay overlayLayer;
    private ListTableModel<T> tableModel;
    private JTable table;

    /**
     * Panel constructor.
     *
     * @param tableModel The model to use for the table.
     */
    public JTablePanel(ListTableModel<T> tableModel) {
        this();
        setModel(tableModel);
    }

    /**
     * Default constructor.
     */
    public JTablePanel() {
        initComponents();
    }

    /**
     * Set the table model. This method must be called prior to calling 
     * setResultList.
     *
     * @param tableModel
     */
    public final void setModel(ListTableModel<T> tableModel) {
        if (tableModel == null) {
            throw new IllegalArgumentException("Null table model passed to setModel");
        }

        this.tableModel = tableModel;
        table.setModel(tableModel);
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
     * Sets the data to be shown in the JTable. Repaint is not handled in this
     * method and should be handled separately.
     *
     * @param data The list of data objects to be shown.
     */
    private void setResultList(List<T> data) {
        
        if(tableModel == null) {
            throw new IllegalStateException("ListTableModel has not be initialized");
        }
        
        // set the list of data to be shown as either the data or an empty list 
        // on null.
        List<T> dataToSet = (data == null) ? Collections.emptyList() : data;

        // since the data is being reset, scroll to the top.
        tableScrollPane.getVerticalScrollBar().setValue(0);

        // set the underlying table model's data.
        this.tableModel.setDataRows(dataToSet);
    }

    /**
     * Sets the message and visibility of the overlay. Repaint is not handled in
     * this method and should be handled separately.
     *
     * @param visible The visibility of the overlay.
     * @param message The message in the overlay.
     */
    private void setOverlay(boolean visible, String message) {
        this.overlayLayer.setVisible(visible);
        this.overlayLayer.setMessage(message);
    }

    /**
     * Clears the results from the underlying JTable and shows the provided
     * message.
     *
     * @param message The message to be shown.
     */
    public synchronized void showMessage(String message) {
        setResultList(null);
        setOverlay(true, message);
        repaint();
    }

    /**
     * Shows a default loading message on the table. This will clear any results
     * in the table.
     */
    public void showDefaultLoadingMessage() {
        showMessage(DEFAULT_LOADING_MESSAGE);
    }

    /**
     * Shows the list as rows of data in the table. If overlay message will be
     * cleared if present.
     *
     * @param data The data to be shown where each item represents a row of
     *             data.
     */
    public synchronized void showResults(List<T> data) {
        setOverlay(false, null);
        setResultList(data);
        repaint();
    }

    /**
     * Shows the data in a DataFetchResult. If there was an error during the
     * operation, the errorMessage will be displayed. If the operation completed
     * successfully and no data is present, noResultsMessage will be shown.
     * Otherwise, the data will be shown as rows in the table.
     *
     * @param result           The DataFetchResult.
     * @param errorMessage     The error message to be shown in the event of an
     *                         error.
     * @param noResultsMessage The message to be shown if there are no results
     *                         but the operation completed successfully.
     */
    public void showDataFetchResult(DataFetchResult<List<T>> result, String errorMessage, String noResultsMessage) {
        if (result == null) {
            logger.log(Level.SEVERE, "Null data processor result received.");
            return;
        }

        switch (result.getResultType()) {
            case SUCCESS:
                if (result.getData() == null || result.getData().isEmpty()) {
                    showMessage(noResultsMessage);
                } else {
                    showResults(result.getData());
                }
                break;
            case ERROR:
                // if there is an error, log accordingly, set result list to 
                // empty and display error message
                logger.log(Level.WARNING, "An exception was caused while results were loaded.", result.getException());
                showMessage(errorMessage);
                break;
            default:
                // an unknown loading state was specified.  log accordingly.
                logger.log(Level.SEVERE, "No known loading state was found in result.");
                break;
        }
    }

    /**
     * Shows the data in a DataFetchResult. If there was an error during the
     * operation, the DEFAULT_ERROR_MESSAGE will be displayed. If the operation
     * completed successfully and no data is present, DEFAULT_NO_RESULTS_MESSAGE
     * will be shown. Otherwise, the data will be shown as rows in the table.
     *
     * @param result The DataFetchResult.
     */
    public void showDataFetchResult(DataFetchResult<List<T>> result) {
        showDataFetchResult(result, DEFAULT_ERROR_MESSAGE, DEFAULT_NO_RESULTS_MESSAGE);
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
