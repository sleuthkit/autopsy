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
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A JTable that displays a list of POJO items and also can display messages for
 * loading, load error, and not loaded.
 */
@Messages({
    "DataResultJTable_loadingMessage_defaultText=Loading results...",
    "DataResultJTable_errorMessage_defaultText=There was an error loading results."
})
class DataResultJTable<T> extends JPanel {

    /**
     * JTables don't allow display messages. So this LayerUI is used to display
     * the contents of a child JLabel. Inspired by TableWaitLayerTest (Animating
     * a Busy Indicator):
     * https://docs.oracle.com/javase/tutorial/uiswing/misc/jlayer.html.
     */
    private static class Overlay extends LayerUI<JComponent> {

        private final JLabel child;
        private boolean visible;

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
         * @param message The message to be displayed.
         */
        void setMessage(String message) {
            child.setText(String.format("<html><div style='text-align: center;'>%s</div></html>", message));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            int w = c.getWidth();
            int h = c.getHeight();

            // Paint the underlying view.
            super.paint(g, c);

            if (!visible) {
                return;
            }

            // paint the jlabel if visible.
            child.setBounds(0, 0, w, h);
            child.paint(g);
        }
    }

    private static final Logger logger = Logger.getLogger(DataResultJTable.class.getName());

    private static final String DEFAULT_LOADING_MESSAGE = Bundle.DataResultJTable_loadingMessage_defaultText();
    private static final String DEFAULT_ERROR_MESSAGE = Bundle.DataResultJTable_errorMessage_defaultText();
    private static final String DEFAULT_NO_RESULTS_MESSAGE = "";
    private static final String DEFAULT_NOT_LOADED_MESSAGE = "";

    private final JTable table;
    private final JScrollPane tableScrollPane;
    private final JLayer<JComponent> dualLayer;
    private final Overlay overlayLayer;

    private String loadingMessage = DEFAULT_LOADING_MESSAGE;
    private String errorMessage = DEFAULT_ERROR_MESSAGE;
    private String noResultsMessage = DEFAULT_NO_RESULTS_MESSAGE;
    private String notLoadedMessage = DEFAULT_NOT_LOADED_MESSAGE;

    private PojoListTableDataModel<T> tableModel = null;

    public DataResultJTable(PojoListTableDataModel<T> tableModel) {
        this.tableModel = tableModel;
        this.table = new JTable(tableModel, tableModel.getTableColumnModel());
        this.table.getTableHeader().setReorderingAllowed(false);

        this.overlayLayer = new Overlay();
        this.tableScrollPane = new JScrollPane(table);
        this.dualLayer = new JLayer<JComponent>(tableScrollPane, overlayLayer);
        setLayout(new BorderLayout());
        add(dualLayer, BorderLayout.CENTER);
    }

    String getLoadingMessage() {
        return loadingMessage;
    }

    String getErrorMessage() {
        return errorMessage;
    }

    String getNoResultsMessage() {
        return noResultsMessage;
    }

    String getNotLoadedMessage() {
        return notLoadedMessage;
    }

    public DataResultJTable<T> setLoadingMessage(String loadingMessage) {
        this.loadingMessage = loadingMessage;
        return this;
    }

    public DataResultJTable<T> setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public DataResultJTable<T> setNoResultsMessage(String noResultsMessage) {
        this.noResultsMessage = noResultsMessage;
        return this;
    }

    public DataResultJTable<T> setNotLoadedMessage(String notLoadedMessage) {
        this.notLoadedMessage = notLoadedMessage;
        return this;
    }

    private void setResultList(List<T> data) {
        List<T> dataToSet = (data != null)
                ? Collections.unmodifiableList(data)
                : Collections.emptyList();

        tableScrollPane.getVerticalScrollBar().setValue(0);
        this.tableModel.setDataRows(dataToSet);
        repaint();
    }

    private void setOverlay(boolean visible, String message) {
        this.overlayLayer.setVisible(visible);
        this.overlayLayer.setMessage(message);
        repaint();
    }

    public void setResult(DataLoadingResult<List<T>> result) {
        if (result == null) {
            logger.log(Level.SEVERE, "Null data processor result received.");
            return;
        }

        switch (result.getState()) {
            case LOADED:
                if (result.getData() == null || result.getData().size() <= 0) {
                    setResultList(null);
                    setOverlay(true, getNoResultsMessage());
                } else {
                    setResultList(result.getData());
                    setOverlay(false, null);

                }
                break;
            case NOT_LOADED:
                setResultList(null);
                setOverlay(true, getNotLoadedMessage());
                break;
            case LOADING:
                setResultList(null);
                setOverlay(true, getLoadingMessage());
                break;
            case LOAD_ERROR:
                setResultList(null);
                setOverlay(true, getErrorMessage());
                break;
            default:
                logger.log(Level.SEVERE, "No known loading state was found in result.");
                break;
        }
    }
}
