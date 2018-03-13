/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JMenuItem;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.event.TableColumnModelListener;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import org.apache.commons.lang.StringUtils;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.netbeans.swing.etable.ETable;

/**
 * Instances of this class display the BlackboardArtifacts associated with the
 * Content represented by a Node. Each BlackboardArtifact is rendered displayed
 * in a JTable representation of its BlackboardAttributes.
 */
@ServiceProvider(service = DataContentViewer.class, position = 7)
public class DataContentViewerArtifact extends javax.swing.JPanel implements DataContentViewer {

    @NbBundle.Messages({
        "DataContentViewerArtifact.attrsTableHeader.type=Type",
        "DataContentViewerArtifact.attrsTableHeader.value=Value",
        "DataContentViewerArtifact.attrsTableHeader.sources=Source(s)",
        "DataContentViewerArtifact.failedToGetSourcePath.message=Failed to get source file path from case database",
        "DataContentViewerArtifact.failedToGetAttributes.message=Failed to get some or all attributes from case database"
    })
    private final static Logger logger = Logger.getLogger(DataContentViewerArtifact.class.getName());
    private final static String WAIT_TEXT = NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.waitText");
    private final static String ERROR_TEXT = NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.errorText");
    private Node currentNode; // @@@ Remove this when the redundant setNode() calls problem is fixed. 
    private int currentPage = 1;
    private final Object lock = new Object();
    private List<ResultsTableArtifact> artifactTableContents; // Accessed by multiple threads, use getArtifactContents() and setArtifactContents()
    SwingWorker<ViewUpdate, Void> currentTask; // Accessed by multiple threads, use startNewTask()
    private static final String[] COLUMN_HEADERS = {
        Bundle.DataContentViewerArtifact_attrsTableHeader_type(),
        Bundle.DataContentViewerArtifact_attrsTableHeader_value(),
        Bundle.DataContentViewerArtifact_attrsTableHeader_sources()};
    private static final int[] COLUMN_WIDTHS = {100, 800, 100};
    private static final int CELL_BOTTOM_MARGIN = 5;

    public DataContentViewerArtifact() {
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
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {

            }

            @Override  
            public void columnMarginChanged(ChangeEvent e) {
                updateRowHeights(); //When the user changes column width we may need to resize row height
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
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
                if (comp instanceof JTextComponent) {
                    final JTextComponent tc = (JTextComponent) comp;
                    final View rootView = tc.getUI().getRootView(tc);
                    java.awt.Insets i = tc.getInsets(null);
                    rootView.setSize(resultsTable.getColumnModel().getColumn(valueColIndex)
                            .getPreferredWidth() - i.left - i.right,
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
        jPanel1 = new javax.swing.JPanel();
        totalPageLabel = new javax.swing.JLabel();
        ofLabel = new javax.swing.JLabel();
        currentPageLabel = new javax.swing.JLabel();
        pageLabel = new javax.swing.JLabel();
        nextPageButton = new javax.swing.JButton();
        pageLabel2 = new javax.swing.JLabel();
        prevPageButton = new javax.swing.JButton();
        resultsTableScrollPane = new javax.swing.JScrollPane();
        artifactLabel = new javax.swing.JLabel();

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setPreferredSize(new java.awt.Dimension(622, 58));

        jPanel1.setPreferredSize(new java.awt.Dimension(620, 58));

        totalPageLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.totalPageLabel.text")); // NOI18N

        ofLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.ofLabel.text")); // NOI18N

        currentPageLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.currentPageLabel.text")); // NOI18N
        currentPageLabel.setMaximumSize(new java.awt.Dimension(18, 14));
        currentPageLabel.setMinimumSize(new java.awt.Dimension(18, 14));
        currentPageLabel.setPreferredSize(new java.awt.Dimension(18, 14));

        pageLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.pageLabel.text")); // NOI18N

        nextPageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward.png"))); // NOI18N
        nextPageButton.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.nextPageButton.text")); // NOI18N
        nextPageButton.setBorderPainted(false);
        nextPageButton.setContentAreaFilled(false);
        nextPageButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_disabled.png"))); // NOI18N
        nextPageButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        nextPageButton.setPreferredSize(new java.awt.Dimension(23, 23));
        nextPageButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_hover.png"))); // NOI18N
        nextPageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextPageButtonActionPerformed(evt);
            }
        });

        pageLabel2.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.pageLabel2.text")); // NOI18N
        pageLabel2.setMaximumSize(new java.awt.Dimension(29, 14));
        pageLabel2.setMinimumSize(new java.awt.Dimension(29, 14));

        prevPageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back.png"))); // NOI18N
        prevPageButton.setText(org.openide.util.NbBundle.getMessage(DataContentViewerArtifact.class, "DataContentViewerArtifact.prevPageButton.text")); // NOI18N
        prevPageButton.setBorderPainted(false);
        prevPageButton.setContentAreaFilled(false);
        prevPageButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_disabled.png"))); // NOI18N
        prevPageButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        prevPageButton.setPreferredSize(new java.awt.Dimension(23, 23));
        prevPageButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_hover.png"))); // NOI18N
        prevPageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevPageButtonActionPerformed(evt);
            }
        });

        resultsTableScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        resultsTableScrollPane.setPreferredSize(new java.awt.Dimension(620, 34));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(currentPageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ofLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(totalPageLabel)
                .addGap(41, 41, 41)
                .addComponent(pageLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(prevPageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(nextPageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(334, Short.MAX_VALUE))
            .addComponent(resultsTableScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                    .addContainerGap(280, Short.MAX_VALUE)
                    .addComponent(artifactLabel)
                    .addContainerGap(84, Short.MAX_VALUE)))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(pageLabel)
                        .addComponent(currentPageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(ofLabel)
                        .addComponent(totalPageLabel))
                    .addComponent(nextPageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(prevPageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pageLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(resultsTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 29, Short.MAX_VALUE)
                .addGap(0, 0, 0))
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup()
                    .addComponent(artifactLabel)
                    .addGap(0, 401, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 622, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void nextPageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextPageButtonActionPerformed
        currentPage = currentPage + 1;
        currentPageLabel.setText(Integer.toString(currentPage));
        artifactLabel.setText(artifactTableContents.get(currentPage - 1).getArtifactDisplayName());
        startNewTask(new SelectedArtifactChangedTask(currentPage));
    }//GEN-LAST:event_nextPageButtonActionPerformed

    private void prevPageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevPageButtonActionPerformed
        currentPage = currentPage - 1;
        currentPageLabel.setText(Integer.toString(currentPage));
        artifactLabel.setText(artifactTableContents.get(currentPage - 1).getArtifactDisplayName());
        startNewTask(new SelectedArtifactChangedTask(currentPage));
    }//GEN-LAST:event_prevPageButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel artifactLabel;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JLabel currentPageLabel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton nextPageButton;
    private javax.swing.JLabel ofLabel;
    private javax.swing.JLabel pageLabel;
    private javax.swing.JLabel pageLabel2;
    private javax.swing.JButton prevPageButton;
    private javax.swing.JScrollPane resultsTableScrollPane;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JLabel totalPageLabel;
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
                            selectedText.append("\t");
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
        currentPage = 1;
        currentPageLabel.setText("");
        artifactLabel.setText("");
        totalPageLabel.setText("");
        ((DefaultTableModel) resultsTable.getModel()).setRowCount(0);
        prevPageButton.setEnabled(false);
        nextPageButton.setEnabled(false);
        currentNode = null;
    }

    @Override
    public void setNode(Node selectedNode) {
        if (currentNode == selectedNode) {
            return;
        }
        currentNode = selectedNode;

        // Make sure there is a node. Null might be passed to reset the viewer.
        if (selectedNode == null) {
            return;
        }

        // Make sure the node is of the correct type.
        Lookup lookup = selectedNode.getLookup();
        Content content = lookup.lookup(Content.class);
        if (content == null) {
            return;
        }

        startNewTask(new SelectedNodeChangedTask(selectedNode));
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerArtifact.title");
    }

    @Override
    public String getToolTip() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerArtifact.toolTip");
    }

    @Override
    public DataContentViewer createInstance() {
        return new DataContentViewerArtifact();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        resetComponents();
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        for (Content content : node.getLookup().lookupAll(Content.class)) {
            if ( (content != null)  && (!(content instanceof BlackboardArtifact)) ){
                try {
                    return content.getAllArtifactsCount() > 0;
                } catch (TskException ex) {
                    logger.log(Level.SEVERE, "Couldn't get count of BlackboardArtifacts for content", ex); //NON-NLS
                }
            }
        }
        return false;
    }

    @Override
    public int isPreferred(Node node) {
        BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);
        // low priority if node doesn't have an artifact (meaning it was found from normal directory
        // browsing, or if the artifact is something that means the user really wants to see the original
        // file and not more details about the artifact
        if ((artifact == null)
                || (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID())
                || (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID())) {
            return 3;
        } else {
            return 5;
        }
    }

    /**
     * This class is a container to hold the data necessary for each of the
     * result pages associated with file or artifact beivng viewed.
     */
    private class ResultsTableArtifact {

        private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
                    String value = "";
                    switch (attr.getAttributeType().getValueType()) {
                        case STRING:
                        case INTEGER:
                        case LONG:
                        case DOUBLE:
                        case BYTE:
                        default:
                            value = attr.getDisplayString();
                            break;
                        // Use Autopsy date formatting settings, not TSK defaults
                        case DATETIME:
                            long epoch = attr.getValueLong();
                            value = "0000-00-00 00:00:00";
                            if (null != content && 0 != epoch) {
                                dateFormatter.setTimeZone(ContentUtils.getTimeZone(content));
                                value = dateFormatter.format(new java.util.Date(epoch * 1000));
                            }
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
    }

    /**
     * Instances of this class are simple containers for view update information
     * generated by a background thread.
     */
    private class ViewUpdate {

        int numberOfPages;
        int currentPage;
        ResultsTableArtifact tableContents;

        ViewUpdate(int numberOfPages, int currentPage, ResultsTableArtifact contents) {
            this.currentPage = currentPage;
            this.numberOfPages = numberOfPages;
            this.tableContents = contents;
        }

        ViewUpdate(int numberOfPages, int currentPage, String errorMsg) {
            this.currentPage = currentPage;
            this.numberOfPages = numberOfPages;
            this.tableContents = new ResultsTableArtifact(errorMsg);
        }
    }

    /**
     * Called from queued SwingWorker done() methods on the EDT thread, so
     * doesn't need to be synchronized.
     *
     * @param viewUpdate A simple container for display update information from
     *                   a background thread.
     */
    private void updateView(ViewUpdate viewUpdate) {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        nextPageButton.setEnabled(viewUpdate.currentPage < viewUpdate.numberOfPages);
        prevPageButton.setEnabled(viewUpdate.currentPage > 1);
        currentPage = viewUpdate.currentPage;
        totalPageLabel.setText(Integer.toString(viewUpdate.numberOfPages));
        currentPageLabel.setText(Integer.toString(currentPage));
        artifactLabel.setText(viewUpdate.tableContents.getArtifactDisplayName());
        DefaultTableModel tModel = ((DefaultTableModel) resultsTable.getModel());
        tModel.setDataVector(viewUpdate.tableContents.getRows(), COLUMN_HEADERS);
        updateColumnSizes();
        updateRowHeights();
        resultsTable.clearSelection();

        this.setCursor(null);
    }

    /**
     * Start a new task on its own background thread, canceling the previous
     * task.
     *
     * @param task A new SwingWorker object to execute as a background thread.
     */
    private synchronized void startNewTask(SwingWorker<ViewUpdate, Void> task) {
        String[][] waitRow = new String[1][3];
        waitRow[0] = new String[]{"", WAIT_TEXT, ""};
        DefaultTableModel tModel = ((DefaultTableModel) resultsTable.getModel());
        tModel.setDataVector(waitRow, COLUMN_HEADERS);
        updateColumnSizes();
        updateRowHeights();
        resultsTable.clearSelection();
        // The output of the previous task is no longer relevant.
        if (currentTask != null) {
            // This call sets a cancellation flag. It does not terminate the background thread running the task. 
            // The task must check the cancellation flag and react appropriately.
            currentTask.cancel(false);
        }

        // Start the new task.
        currentTask = task;
        currentTask.execute();
    }

    /**
     * Populate the cache of artifact represented as ResultsTableArtifacts.
     *
     * @param artifactList A list of ResultsTableArtifact representations of
     *                     artifacts.
     */
    private void setArtifactContents(List<ResultsTableArtifact> artifactList) {
        synchronized (lock) {
            this.artifactTableContents = artifactList;
        }
    }

    /**
     * Retrieve the cache of artifact represented as ResultsTableArtifacts.
     *
     * @return A list of ResultsTableArtifact representations of artifacts.
     */
    private List<ResultsTableArtifact> getArtifactContents() {
        synchronized (lock) {
            return artifactTableContents;
        }
    }

    /**
     * Instances of this class use a background thread to generate a ViewUpdate
     * when a node is selected, changing the set of blackboard artifacts
     * ("results") to be displayed.
     */
    private class SelectedNodeChangedTask extends SwingWorker<ViewUpdate, Void> {

        private final Node selectedNode;

        SelectedNodeChangedTask(Node selectedNode) {
            this.selectedNode = selectedNode;
        }

        @Override
        protected ViewUpdate doInBackground() {
            // Get the lookup for the node for access to its underlying content and
            // blackboard artifact, if any.
            Lookup lookup = selectedNode.getLookup();

            // Get the content. We may get BlackboardArtifacts, ignore those here.
            ArrayList<BlackboardArtifact> artifacts = new ArrayList<>();
            Collection<? extends Content> contents = lookup.lookupAll(Content.class);
            if (contents.isEmpty()) {
                return new ViewUpdate(getArtifactContents().size(), currentPage, ERROR_TEXT);
            }
            Content underlyingContent = null;
            for (Content content : contents) {
                if ( (content != null)  && (!(content instanceof BlackboardArtifact)) ) {
                    // Get all of the blackboard artifacts associated with the content. These are what this
                    // viewer displays.
                    try {
                        artifacts = content.getAllArtifacts();
                        underlyingContent = content;
                        break;
                    } catch (TskException ex) {
                        logger.log(Level.SEVERE, "Couldn't get artifacts", ex); //NON-NLS
                        return new ViewUpdate(getArtifactContents().size(), currentPage, ERROR_TEXT);
                    }
                }
            }
 
            if (isCancelled()) {
                return null;
            }

            // Build the new artifact contents cache.
            ArrayList<ResultsTableArtifact> artifactContents = new ArrayList<>();
            for (BlackboardArtifact artifact : artifacts) {
                artifactContents.add(new ResultsTableArtifact(artifact, underlyingContent));
            }

            // If the node has an underlying blackboard artifact, show it. If not,
            // show the first artifact.
            int index = 0;
            BlackboardArtifact artifact = lookup.lookup(BlackboardArtifact.class);
            if (artifact != null) {
                index = artifacts.indexOf(artifact);
                if (index == -1) {
                    index = 0;
                } else {
                    // if the artifact has an ASSOCIATED ARTIFACT, then we display the associated artifact instead
                    try {
                        for (BlackboardAttribute attr : artifact.getAttributes()) {
                            if (attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                                long assocArtifactId = attr.getValueLong();
                                int assocArtifactIndex = -1;
                                for (BlackboardArtifact art : artifacts) {
                                    if (assocArtifactId == art.getArtifactID()) {
                                        assocArtifactIndex = artifacts.indexOf(art);
                                        break;
                                    }
                                }
                                if (assocArtifactIndex >= 0) {
                                    index = assocArtifactIndex;
                                }
                                break;
                            }
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Couldn't get associated artifact to display in Content Viewer.", ex); //NON-NLS
                    }
                }

            }

            if (isCancelled()) {
                return null;
            }

            // Add one to the index of the artifact content for the corresponding page index.
            ViewUpdate viewUpdate = new ViewUpdate(artifactContents.size(), index + 1, artifactContents.get(index));

            // It may take a considerable amount of time to fetch the attributes of the selected artifact 
            if (isCancelled()) {
                return null;
            }

            // Update the artifact contents cache.
            setArtifactContents(artifactContents);

            return viewUpdate;
        }

        @Override
        protected void done() {
            if (!isCancelled()) {
                try {
                    ViewUpdate viewUpdate = get();
                    if (viewUpdate != null) {
                        updateView(viewUpdate);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.WARNING, "Artifact display task unexpectedly interrupted or failed", ex);                 //NON-NLS
                }
            }
        }
    }

    /**
     * Instances of this class use a background thread to generate a ViewUpdate
     * when the user pages the view to look at another blackboard artifact
     * ("result").
     */
    private class SelectedArtifactChangedTask extends SwingWorker<ViewUpdate, Void> {

        private final int pageIndex;

        SelectedArtifactChangedTask(final int pageIndex) {
            this.pageIndex = pageIndex;
        }

        @Override
        protected ViewUpdate doInBackground() {
            // Get the artifact content to display from the cache. Note that one must be subtracted from the
            // page index to get the corresponding artifact content index.
            List<ResultsTableArtifact> artifactContents = getArtifactContents();
            ResultsTableArtifact artifactContent = artifactContents.get(pageIndex - 1);

            // It may take a considerable amount of time to fetch the attributes of the selected artifact so check for cancellation.
            if (isCancelled()) {
                return null;
            }

            return new ViewUpdate(artifactContents.size(), pageIndex, artifactContent);
        }

        @Override
        protected void done() {
            if (!isCancelled()) {
                try {
                    ViewUpdate viewUpdate = get();
                    if (viewUpdate != null) {
                        updateView(viewUpdate);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.WARNING, "Artifact display task unexpectedly interrupted or failed", ex);                 //NON-NLS
                }
            }
        }
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
                jtex.setWrapStyleWord(true);
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
