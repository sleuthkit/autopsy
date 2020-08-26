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
import java.util.logging.Level;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.plaf.LayerUI;
import javax.swing.table.TableColumnModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A table that displays a list of items and also can display messages for
 * loading, load error, and not loaded.
 */
@Messages({
    "DataResultTable_loadingMessage_defaultText=Loading results...",
    "DataResultTable_errorMessage_defaultText=There was an error loading results."
})
public class DataResultTable<T> extends JPanel {

    /**
     * JTables don't allow displaying messages. So this LayerUI is used to
     * display the contents of a child JLabel. Inspired by TableWaitLayerTest
     * (Animating a Busy Indicator):
     * https://docs.oracle.com/javase/tutorial/uiswing/misc/jlayer.html.
     */
    private static class Overlay extends LayerUI<JComponent> {

        private static final long serialVersionUID = 1L;

        private final JLabel child;
        private boolean visible;

        /**
         * Main constructor for the Overlay.
         */
        Overlay() {
            child = new JLabel();
            child.setHorizontalAlignment(JLabel.CENTER);
            child.setVerticalAlignment(JLabel.CENTER);
            child.setOpaque(false);

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
         * @return The child JLabel component.
         */
        JLabel getChild() {
            return child;
        }

        /**
         * Sets the message to be displayed in the child jlabel.
         *
         * @param message The message to be displayed.
         */
        void setMessage(String message) {
            child.setText(String.format("<html><div style='text-align: center;'>%s</div></html>", message));
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
            child.setBounds(0, 0, w, h);
            child.paint(g);
        }
    }

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(DataResultTable.class.getName());

    private static final String DEFAULT_LOADING_MESSAGE = Bundle.DataResultTable_loadingMessage_defaultText();
    private static final String DEFAULT_ERROR_MESSAGE = Bundle.DataResultTable_errorMessage_defaultText();
    private static final String DEFAULT_NO_RESULTS_MESSAGE = "";
    private static final String DEFAULT_NOT_LOADED_MESSAGE = "";

    private final JScrollPane tableScrollPane;
    private final Overlay overlayLayer;
    private final ListTableModel<T> tableModel;
    private final JTable table;

    private String loadingMessage = DEFAULT_LOADING_MESSAGE;
    private String errorMessage = DEFAULT_ERROR_MESSAGE;
    private String noResultsMessage = DEFAULT_NO_RESULTS_MESSAGE;
    private String notLoadedMessage = DEFAULT_NOT_LOADED_MESSAGE;

    /**
     * Main constructor.
     *
     * @param tableModel The model to use for the table.
     */
    public DataResultTable(ListTableModel<T> tableModel) {
        this.tableModel = tableModel;
        this.table = new JTable(tableModel);
        this.table.getTableHeader().setReorderingAllowed(false);

        this.overlayLayer = new Overlay();
        this.tableScrollPane = new JScrollPane(table);
        JLayer<JComponent> dualLayer = new JLayer<JComponent>(tableScrollPane, overlayLayer);
        setLayout(new BorderLayout());
        add(dualLayer, BorderLayout.CENTER);
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
    public DataResultTable<T> setColumnModel(TableColumnModel columnModel) {
        this.table.setColumnModel(columnModel);
        return this;
    }

    /**
     * @return The message shown when loading.
     */
    public String getLoadingMessage() {
        return loadingMessage;
    }

    /**
     * @return The message shown when there is an exception.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * @return The message shown when there are no results.
     */
    public String getNoResultsMessage() {
        return noResultsMessage;
    }

    /**
     * @return The message shown when the table has not been loaded.
     */
    public String getNotLoadedMessage() {
        return notLoadedMessage;
    }

    /**
     * Sets the loading message.
     *
     * @param loadingMessage The loading message.
     *
     * @return As a utility, returns this.
     */
    public DataResultTable<T> setLoadingMessage(String loadingMessage) {
        this.loadingMessage = loadingMessage;
        return this;
    }

    /**
     * Sets the error message
     *
     * @param errorMessage The error message.
     *
     * @return As a utility, returns this.
     */
    public DataResultTable<T> setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    /**
     * Sets the message to be shown when no results are present.
     *
     * @param noResultsMessage The 'no results' message.
     *
     * @return As a utility, returns this.
     */
    public DataResultTable<T> setNoResultsMessage(String noResultsMessage) {
        this.noResultsMessage = noResultsMessage;
        return this;
    }

    /**
     * Sets the 'not loaded' message.
     *
     * @param notLoadedMessage The message to be shown when results are not
     *                         loaded.
     *
     * @return As a utility, returns this.
     */
    public DataResultTable<T> setNotLoadedMessage(String notLoadedMessage) {
        this.notLoadedMessage = notLoadedMessage;
        return this;
    }

    /**
     * Sets the data to be shown in the JTable.
     *
     * @param data The list of data objects to be shown.
     */
    private void setResultList(List<T> data) {
        // set the list of data to be shown as either the data or an empty list 
        // on null.
        List<T> dataToSet = (data != null)
                ? Collections.unmodifiableList(data)
                : Collections.emptyList();

        // since the data is being reset, scroll to the top.
        tableScrollPane.getVerticalScrollBar().setValue(0);

        // set the underlying table model's data.
        this.tableModel.setDataRows(dataToSet);
    }

    /**
     * Sets the message and visibility of the overlay.
     *
     * @param visible The visibility of the overlay.
     * @param message The message in the overlay.
     */
    private void setOverlay(boolean visible, String message) {
        this.overlayLayer.setVisible(visible);
        this.overlayLayer.setMessage(message);
    }

    /**
     * Sets the result to be displayed.
     *
     * @param result The loading result to be displayed.
     *
     * @return As a utility, returns this.
     */
    public DataResultTable<T> setResult(DataLoadingResult<List<T>> result) {
        if (result == null) {
            logger.log(Level.SEVERE, "Null data processor result received.");
            return this;
        }

        switch (result.getState()) {
            case LOADED:
                if (result.getData() == null || result.getData().size() <= 0) {
                    // if loaded and there is no data, set an empty result list
                    // and set the overlay to show no results message.
                    setResultList(null);
                    setOverlay(true, getNoResultsMessage());
                } else {
                    // otherwise show the data and remove the overlay.
                    setResultList(result.getData());
                    setOverlay(false, null);
                }
                break;
            case NOT_LOADED:
                // if results are not loaded, empty results list and display 
                // not loaded message
                setResultList(null);
                setOverlay(true, getNotLoadedMessage());
                break;
            case LOADING:
                // if this table is loading, then set the results to empty and 
                // display the loading message.
                setResultList(null);
                setOverlay(true, getLoadingMessage());
                break;
            case LOAD_ERROR:
                // if there is an error, log accordingly, set result list to 
                // empty and display error message
                logger.log(Level.WARNING, "An exception was caused while results were loaded.", result.getException());
                setResultList(null);
                setOverlay(true, getErrorMessage());
                break;
            default:
                // an unknown loading state was specified.  log accordingly.
                logger.log(Level.SEVERE, "No known loading state was found in result.");
                break;
        }

        // repaint to capture changes.
        repaint();

        return this;
    }
}
