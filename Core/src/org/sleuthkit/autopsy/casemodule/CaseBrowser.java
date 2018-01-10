/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import org.netbeans.swing.etable.ETableColumn;
import org.netbeans.swing.etable.ETableColumnModel;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.nodes.Node;
import java.awt.EventQueue;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.explorer.ExplorerManager;
import org.sleuthkit.autopsy.coordinationservice.CaseNodeData;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.EmptyNode;

/**
 * A Swing JPanel with a JTabbedPane child component. The tabbed pane contains
 * result viewers.
 *
 * The "main" DataResultPanel for the desktop application has a table viewer
 * (DataResultViewerTable) and a thumbnail viewer (DataResultViewerThumbnail),
 * plus zero to many additional DataResultViewers, since the DataResultViewer
 * interface is an extension point.
 *
 * The "main" DataResultPanel resides in the "main" results view
 * (DataResultTopComponent) that is normally docked into the upper right hand
 * side of the main window of the desktop application.
 *
 * The result viewers in the "main panel" are used to view the child nodes of a
 * node selected in the tree view (DirectoryTreeTopComponent) that is normally
 * docked into the left hand side of the main window of the desktop application.
 *
 * Nodes selected in the child results viewers of a DataResultPanel are
 * displayed in a content view (implementation of the DataContent interface)
 * supplied the panel. The default content view is (DataContentTopComponent) is
 * normally docked into the lower right hand side of the main window, underneath
 * the results view. A custom content view may be specified instead.
 */
class CaseBrowser extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;

    private final Outline outline;
    private ExplorerManager em;
    private final org.openide.explorer.view.OutlineView outlineView;
    private int originalPathColumnIndex = 0;
    private static final Logger LOGGER = Logger.getLogger(CaseBrowser.class.getName());
    private LoadCaseListWorker tableWorker;

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    /**
     * Creates new form CaseBrowser
     */
    CaseBrowser() {
        outlineView = new org.openide.explorer.view.OutlineView();
        initComponents();

        outline = outlineView.getOutline();
        outlineView.setPropertyColumns(
                Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(),
                Bundle.CaseNode_column_status(), Bundle.CaseNode_column_status(),
                Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath());
        customize();

    }

    private void customize() {
        TableColumnModel columnModel = outline.getColumnModel();
        int dateColumnIndex = 0;
        for (int index = 0; index < columnModel.getColumnCount(); index++) {  //get indexes for hidden column and default sorting column
            if (columnModel.getColumn(index).getHeaderValue().toString().equals(Bundle.CaseNode_column_metadataFilePath())) {
                originalPathColumnIndex = index;
            } else if (columnModel.getColumn(index).getHeaderValue().toString().equals(Bundle.CaseNode_column_createdTime())) {
                dateColumnIndex = index;
            }
        }
        ETableColumn column = (ETableColumn) columnModel.getColumn(originalPathColumnIndex);
        ((ETableColumnModel) columnModel).setColumnHidden(column, true);
        outline.setRootVisible(false);

        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.CaseNode_column_name());
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outline.setColumnSorted(dateColumnIndex, false, 1); //it would be nice if the column index wasn't hardcoded
    }

    /**
     * Initializes this panel. Intended to be called by a parent top component
     * when the top component is opened.
     */
    void open() {
        if (null == em) {
            em = new ExplorerManager();
        }
        jScrollPane1.setViewportView(outlineView);
        setColumnWidths();
        this.setVisible(true);
    }

    void setRowSelectionAllowed(boolean allowed) {
        outline.setRowSelectionAllowed(allowed);
    }

    void addListSelectionListener(ListSelectionListener listener) {
        outline.getSelectionModel().addListSelectionListener(listener);
    }

    String getCasePath() {
        int[] selectedRows = outline.getSelectedRows();
        if (selectedRows.length == 1) {
            try {
                return ((Node.Property) outline.getModel().getValueAt(outline.convertRowIndexToModel(selectedRows[0]), originalPathColumnIndex)).getValue().toString();
            } catch (IllegalAccessException | InvocationTargetException ex) {
                System.out.println("THROW");
            }
        }
        return null;
    }

    boolean isRowSelected() {
        return outline.getRowSelectionAllowed() && outline.getSelectedRows().length > 0;
    }

    private void setColumnWidths() {
        int margin = 4;
        int padding = 8;

        final int rows = Math.min(100, outline.getRowCount());

        for (int column = 0; column < outline.getColumnModel().getColumnCount(); column++) {
            int columnWidthLimit = 800;
            int columnWidth = 0;

            // find the maximum width needed to fit the values for the first 100 rows, at most
            for (int row = 0; row < rows; row++) {
                TableCellRenderer renderer = outline.getCellRenderer(row, column);
                Component comp = outline.prepareRenderer(renderer, row, column);
                columnWidth = Math.max(comp.getPreferredSize().width, columnWidth);
            }

            columnWidth += 2 * margin + padding; // add margin and regular padding
            columnWidth = Math.min(columnWidth, columnWidthLimit);

            outline.getColumnModel().getColumn(column).setPreferredWidth(columnWidth);
        }
    }

    /**
     * Gets the list of cases known to the review mode cases manager and
     * refreshes the cases table.
     */
    void refresh() {
        if (tableWorker == null || tableWorker.isDone()) {
            setRowSelectionAllowed(false);
            //create a new TableWorker to and execute it in a background thread if one is not currently working
            //set the table to display text informing the user that the list is being retreived and disable case selection
            EmptyNode emptyNode = new EmptyNode(Bundle.MultiUserCasesPanel_caseListLoading_message());
            em.setRootContext(emptyNode);
            tableWorker = new LoadCaseListWorker();
            tableWorker.execute();
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

        jScrollPane1 = new javax.swing.JScrollPane();

        setMinimumSize(new java.awt.Dimension(0, 5));
        setPreferredSize(new java.awt.Dimension(5, 5));
        setLayout(new java.awt.BorderLayout());

        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane1.setMinimumSize(new java.awt.Dimension(0, 5));
        jScrollPane1.setOpaque(false);
        jScrollPane1.setPreferredSize(new java.awt.Dimension(5, 5));
        add(jScrollPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
    private class LoadCaseListWorker extends SwingWorker<Void, Void> {

        private static final String ALERT_FILE_NAME = "autoingest.alert";
        private Map<CaseMetadata, Boolean> cases;

        /**
         * Gets a list of the cases in the top level case folder
         *
         * @return List of cases.
         *
         * @throws CoordinationServiceException
         */
        private Map<CaseMetadata, Boolean> getCases() throws CoordinationService.CoordinationServiceException {
            Map<CaseMetadata, Boolean> casesMap = new HashMap<>();
            List<String> nodeList = CoordinationService.getInstance().getNodeList(CoordinationService.CategoryNode.CASES);

            for (String node : nodeList) {
                Path casePath = Paths.get(node);
                File caseFolder = casePath.toFile();
                if (caseFolder.exists()) {
                    /*
                     * Search for '*.aut' and 'autoingest.alert' files.
                     */
                    File[] fileArray = caseFolder.listFiles();
                    if (fileArray == null) {
                        continue;
                    }
                    String autFilePath = null;
                    boolean alertFileFound = false;
                    for (File file : fileArray) {
                        String name = file.getName().toLowerCase();
                        if (autFilePath == null && name.endsWith(".aut")) {
                            autFilePath = file.getAbsolutePath();
                            if (!alertFileFound) {
                                continue;
                            }
                        }
                        if (!alertFileFound && name.endsWith(ALERT_FILE_NAME)) {
                            alertFileFound = true;
                        }
                        if (autFilePath != null && alertFileFound) {
                            break;
                        }
                    }

                    if (autFilePath != null) {
                        try {
                            boolean hasAlertStatus = false;
                            if (alertFileFound) {
                                /*
                                 * When an alert file exists, ignore the node
                                 * data and use the ALERT status.
                                 */
                                hasAlertStatus = true;
                            } else {
                                byte[] rawData = CoordinationService.getInstance().getNodeData(CoordinationService.CategoryNode.CASES, node);
                                if (rawData != null && rawData.length > 0) {
                                    /*
                                     * When node data exists, use the status
                                     * stored in the node data.
                                     */
                                    CaseNodeData caseNodeData = new CaseNodeData(rawData);
                                    if (caseNodeData.getErrorsOccurred()) {
                                        hasAlertStatus = true;
                                    }
                                }
                            }

                            CaseMetadata caseMetadata = new CaseMetadata(Paths.get(autFilePath));
                            casesMap.put(caseMetadata, hasAlertStatus);
                        } catch (CaseMetadata.CaseMetadataException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error reading case metadata file '%s'.", autFilePath), ex);
                        } catch (InterruptedException | CaseNodeData.InvalidDataException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error reading case node data for '%s'.", node), ex);
                        }
                    }
                }
            }
            return casesMap;
        }

        @Override
        protected Void doInBackground() throws Exception {

            try {
                cases = getCases();
            } catch (CoordinationService.CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, "Unexpected exception while refreshing the table.", ex); //NON-NLS
            }
            return null;
        }

        @Override
        protected void done() {

            EventQueue.invokeLater(() -> {
                CaseNode caseListNode = new CaseNode(cases);
                em.setRootContext(caseListNode);
                setRowSelectionAllowed(true);
            });
        }
    }
}
