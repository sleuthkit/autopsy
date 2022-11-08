/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.event.TableColumnModelListener;
import javax.swing.text.View;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.netbeans.swing.etable.ETable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import java.util.Locale;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.discovery.ui.AbstractArtifactDetailsPanel;
//import org.sleuthkit.autopsy.contentviewers.Bundle;

/**
 * This class displays a Blackboard artifact as a table of its attributes.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class DefaultTableArtifactContentViewer extends AbstractArtifactDetailsPanel implements ArtifactContentViewer {

    @NbBundle.Messages({
        "DefaultTableArtifactContentViewer.attrsTableHeader.type=Type",
        "DefaultTableArtifactContentViewer.attrsTableHeader.value=Value",
        "DefaultTableArtifactContentViewer.attrsTableHeader.sources=Source(s)",
        "DataContentViewerArtifact.failedToGetSourcePath.message=Failed to get source file path from case database",
        "DataContentViewerArtifact.failedToGetAttributes.message=Failed to get some or all attributes from case database"
    })

    private final static Logger logger = Logger.getLogger(DefaultTableArtifactContentViewer.class.getName());

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMN_HEADERS = {
        Bundle.DefaultTableArtifactContentViewer_attrsTableHeader_type(),
        Bundle.DefaultTableArtifactContentViewer_attrsTableHeader_value(),
        Bundle.DefaultTableArtifactContentViewer_attrsTableHeader_sources()};
    private static final int[] COLUMN_WIDTHS = {100, 800, 100};
    private static final int CELL_BOTTOM_MARGIN = 5;
    private static final int CELL_RIGHT_MARGIN = 1;

    public DefaultTableArtifactContentViewer() {
        initResultsTable();
        initComponents();
        resultsTableScrollPane.setViewportView(resultsTable);
        customizeComponents();
        resetComponents();
        resultsTable.setDefaultRenderer(Object.class, new MultiLineTableCellRenderer());
    }

    private void initResultsTable() {
        resultsTable = new ETable();
        resultsTable.setModel(new javax.swing.table.DefaultTableModel() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        });
        resultsTable.setCellSelectionEnabled(true);
        resultsTable.getTableHeader().setReorderingAllowed(false);
        resultsTable.setColumnHidingAllowed(false);
        resultsTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        resultsTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {

            @Override
            public void columnAdded(TableColumnModelEvent e) {
                // do nothing
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
                // do nothing
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
                // do nothing
            }

            @Override
            public void columnMarginChanged(ChangeEvent e) {
                updateRowHeights(); //When the user changes column width we may need to resize row height
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
                // do nothing
            }
        });
        resultsTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_NEXT_COLUMN);

    }

    /**
     * Sets the row heights to the heights of the content in their Value column.
     */
    private void updateRowHeights() {
        int valueColIndex = -1;
        for (int col = 0; col < resultsTable.getColumnCount(); col++) {
            if (resultsTable.getColumnName(col).equals(COLUMN_HEADERS[1])) {
                valueColIndex = col;
            }
        }
        if (valueColIndex != -1) {
            for (int row = 0; row < resultsTable.getRowCount(); row++) {
                Component comp = resultsTable.prepareRenderer(
                        resultsTable.getCellRenderer(row, valueColIndex), row, valueColIndex);
                final int rowHeight;
                if (comp instanceof JTextArea) {
                    final JTextArea tc = (JTextArea) comp;
                    final View rootView = tc.getUI().getRootView(tc);
                    java.awt.Insets i = tc.getInsets();
                    rootView.setSize(resultsTable.getColumnModel().getColumn(valueColIndex)
                            .getWidth() - (i.left + i.right + CELL_RIGHT_MARGIN), //current width minus borders
                            Integer.MAX_VALUE);
                    rowHeight = (int) rootView.getPreferredSpan(View.Y_AXIS);
                } else {
                    rowHeight = comp.getPreferredSize().height;
                }
                if (rowHeight > 0) {
                    resultsTable.setRowHeight(row, rowHeight + CELL_BOTTOM_MARGIN);
                }
            }
        }
    }

    /**
     * Update the column widths so that the Value column has most of the space.
     */
    private void updateColumnSizes() {
        Enumeration<TableColumn> columns = resultsTable.getColumnModel().getColumns();
        while (columns.hasMoreElements()) {
            TableColumn col = columns.nextElement();
            if (col.getHeaderValue().equals(COLUMN_HEADERS[0])) {
                col.setPreferredWidth(COLUMN_WIDTHS[0]);
            } else if (col.getHeaderValue().equals(COLUMN_HEADERS[1])) {
                col.setPreferredWidth(COLUMN_WIDTHS[1]);
            } else if (col.getHeaderValue().equals(COLUMN_HEADERS[2])) {
                col.setPreferredWidth(COLUMN_WIDTHS[2]);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rightClickMenu = new javax.swing.JPopupMenu();
        copyMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        resultsTableScrollPane = new javax.swing.JScrollPane();

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(DefaultTableArtifactContentViewer.class, "DefaultTableArtifactContentViewer.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(DefaultTableArtifactContentViewer.class, "DefaultTableArtifactContentViewer.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setPreferredSize(new java.awt.Dimension(0, 0));

        resultsTableScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        resultsTableScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        resultsTableScrollPane.setMinimumSize(new java.awt.Dimension(0, 0));
        resultsTableScrollPane.setPreferredSize(new java.awt.Dimension(0, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(resultsTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(resultsTableScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 58, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JScrollPane resultsTableScrollPane;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JMenuItem selectAllMenuItem;
    // End of variables declaration//GEN-END:variables
    private ETable resultsTable;

    private void customizeComponents() {
        resultsTable.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(copyMenuItem)) {
                    StringBuilder selectedText = new StringBuilder(512);
                    for (int row : resultsTable.getSelectedRows()) {
                        for (int col : resultsTable.getSelectedColumns()) {
                            selectedText.append((String) resultsTable.getValueAt(row, col));
                            selectedText.append('\t');
                        }
                        //if its the last row selected don't add a new line
                        if (row != resultsTable.getSelectedRows()[resultsTable.getSelectedRows().length - 1]) {
                            selectedText.append(System.lineSeparator());
                        }
                    }
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selectedText.toString()), null);
                } else if (jmi.equals(selectAllMenuItem)) {
                    resultsTable.selectAll();
                }
            }
        };
        copyMenuItem.addActionListener(actList);

        selectAllMenuItem.addActionListener(actList);
    }

    /**
     * Resets the components to an empty view state.
     */
    private void resetComponents() {

        ((DefaultTableModel) resultsTable.getModel()).setRowCount(0);
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void setArtifact(BlackboardArtifact artifact) {
        try {
            ResultsTableArtifact resultsTableArtifact = artifact == null ? null : new ResultsTableArtifact(artifact, artifact.getParent());

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateView(resultsTableArtifact);
                }
            });

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error getting parent content for artifact (artifact_id=%d, obj_id=%d)", artifact.getArtifactID(), artifact.getObjectID()), ex);
        }

    }

    @Override
    public boolean isSupported(BlackboardArtifact artifact) {
        // This viewer supports all artifacts.
        return true;
    }

    /**
     * This class is a container to hold the data necessary for the artifact
     * being viewed.
     */
    private class ResultsTableArtifact {

        private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        private String[][] rowData = null;
        private final String artifactDisplayName;
        private final Content content;

        ResultsTableArtifact(BlackboardArtifact artifact, Content content) {
            artifactDisplayName = artifact.getDisplayName();
            this.content = content;
            addRows(artifact);
        }

        ResultsTableArtifact(String errorMsg) {
            artifactDisplayName = errorMsg;
            rowData = new String[1][3];
            rowData[0] = new String[]{"", errorMsg, ""};
            content = null;
        }

        private String[][] getRows() {
            return rowData;
        }

        private void addRows(BlackboardArtifact artifact) {
            List<String[]> rowsToAdd = new ArrayList<>();
            try {
                /*
                 * Add rows for each attribute.
                 */
                for (BlackboardAttribute attr : artifact.getAttributes()) {
                    /*
                     * Attribute value column.
                     */
                    String value;
                    switch (attr.getAttributeType().getValueType()) {

                        // Use Autopsy date formatting settings, not TSK defaults
                        case DATETIME:
                            value = TimeZoneUtils.getFormattedTime(attr.getValueLong());
                            break;
                        case JSON:
                            // Get the attribute's JSON value and convert to indented multiline display string
                            String jsonVal = attr.getValueString();
                            JsonObject json = JsonParser.parseString(jsonVal).getAsJsonObject();

                            value = toJsonDisplayString(json, "");
                            break;

                        case STRING:
                        case INTEGER:
                        case LONG:
                        case DOUBLE:
                        case BYTE:
                        default:
                            value = attr.getDisplayString();
                            break;
                    }
                    /*
                     * Attribute sources column.
                     */
                    String sources = StringUtils.join(attr.getSources(), ", ");
                    rowsToAdd.add(new String[]{attr.getAttributeType().getDisplayName(), value, sources});
                }
                /*
                 * Add a row for the source content path.
                 */
                String path = "";
                try {
                    if (null != content) {
                        path = content.getUniquePath();
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error getting source content path for artifact (artifact_id=%d, obj_id=%d)", artifact.getArtifactID(), artifact.getObjectID()), ex);
                    path = Bundle.DataContentViewerArtifact_failedToGetSourcePath_message();
                }
                rowsToAdd.add(new String[]{"Source File Path", path, ""});
                /*
                 * Add a row for the artifact id.
                 */
                rowsToAdd.add(new String[]{"Artifact ID", Long.toString(artifact.getArtifactID()), ""});
            } catch (TskCoreException ex) {
                rowsToAdd.add(new String[]{"", Bundle.DataContentViewerArtifact_failedToGetAttributes_message(), ""});
            }
            rowData = rowsToAdd.toArray(new String[0][0]);
        }

        /**
         * @return the artifactDisplayName
         */
        String getArtifactDisplayName() {
            return artifactDisplayName;
        }

        private static final String INDENT_RIGHT = "    ";
        private static final String NEW_LINE = "\n";

        /**
         * Recursively converts a JSON element into an indented multi-line
         * display string.
         *
         * @param element     JSON element to convert
         * @param startIndent Starting indentation for the element.
         *
         * @return A multi-line display string.
         */
        private String toJsonDisplayString(JsonElement element, String startIndent) {

            StringBuilder sb = new StringBuilder("");
            JsonObject obj = element.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                appendJsonElementToString(entry.getKey(), entry.getValue(), startIndent, sb);
            }

            String returnString = sb.toString();
            if (startIndent.length() == 0 && returnString.startsWith(NEW_LINE)) {
                returnString = returnString.substring(NEW_LINE.length());
            }
            return returnString;
        }

        /**
         * Converts the given JSON element into string and appends to the given
         * string builder.
         *
         * @param jsonKey
         * @param jsonElement
         * @param startIndent Starting indentation for the element.
         * @param sb          String builder to append to.
         */
        private void appendJsonElementToString(String jsonKey, JsonElement jsonElement, String startIndent, StringBuilder sb) {
            if (jsonElement.isJsonArray()) {
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                if (jsonArray.size() > 0) {
                    int count = 1;
                    sb.append(NEW_LINE).append(String.format("%s%s", startIndent, jsonKey));
                    for (JsonElement arrayMember : jsonArray) {
                        sb.append(NEW_LINE).append(String.format("%s%d", startIndent.concat(INDENT_RIGHT), count));
                        sb.append(toJsonDisplayString(arrayMember, startIndent.concat(INDENT_RIGHT).concat(INDENT_RIGHT)));
                        count++;
                    }
                }
            } else if (jsonElement.isJsonObject()) {
                sb.append(NEW_LINE).append(String.format("%s%s %s", startIndent, jsonKey, toJsonDisplayString(jsonElement.getAsJsonObject(), startIndent + INDENT_RIGHT)));
            } else if (jsonElement.isJsonPrimitive()) {
                String attributeName = jsonKey;
                String attributeValue;
                if (attributeName.toUpperCase().contains("DATETIME")) {
                    attributeValue = TimeZoneUtils.getFormattedTime(Long.parseLong(jsonElement.getAsString()));
                } else {
                    attributeValue = jsonElement.getAsString();
                }
                sb.append(NEW_LINE).append(String.format("%s%s = %s", startIndent, attributeName, attributeValue));
            } else if (jsonElement.isJsonNull()) {
                sb.append(NEW_LINE).append(String.format("%s%s = null", startIndent, jsonKey));
            }
        }
    }

    /**
     * Updates the table view with the given artifact data.
     *
     * It should be called on EDT.
     *
     * @param resultsTableArtifact Artifact data to display in the view.
     */
    private void updateView(ResultsTableArtifact resultsTableArtifact) {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        DefaultTableModel tModel = ((DefaultTableModel) resultsTable.getModel());
        String[][] rows = resultsTableArtifact == null ? new String[0][0] : resultsTableArtifact.getRows();
        tModel.setDataVector(rows, COLUMN_HEADERS);
        updateColumnSizes();
        updateRowHeights();
        resultsTable.clearSelection();
        this.setCursor(null);
    }

    /**
     * TableCellRenderer for displaying multiline text.
     */
    private class MultiLineTableCellRenderer implements javax.swing.table.TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(javax.swing.JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            javax.swing.JTextArea jtex = new javax.swing.JTextArea();
            if (value instanceof String) {
                jtex.setText((String) value);
                jtex.setLineWrap(true);
                jtex.setWrapStyleWord(false);
            }
            //cell backgroud color when selected
            if (isSelected) {
                jtex.setBackground(javax.swing.UIManager.getColor("Table.selectionBackground"));
            } else {
                jtex.setBackground(javax.swing.UIManager.getColor("Table.background"));
            }
            return jtex;
        }
    }
}
