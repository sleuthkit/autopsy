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

import java.lang.reflect.InvocationTargetException;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumnModel;
import org.netbeans.swing.etable.ETableColumn;
import org.netbeans.swing.etable.ETableColumnModel;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.nodes.Node;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.explorer.ExplorerManager;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.EmptyNode;

/**
 * A Swing JPanel with a scroll pane child component. The scroll pane contain
 * the table of cases.
 *
 * Used to display a list of multi user cases and allow the user to open one of
 * them.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
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
     * Creates a new CaseBrowser
     */
    CaseBrowser() {
        outlineView = new org.openide.explorer.view.OutlineView();
        initComponents();

        outline = outlineView.getOutline();
        outlineView.setPropertyColumns(
                Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(),
                Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath());
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.CaseNode_column_name());
        customize();

    }

    /**
     * Configures the the table of cases and its columns.
     */
    private void customize() {
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumnModel columnModel = outline.getColumnModel();
        int dateColumnIndex = 0;
        for (int index = 0; index < columnModel.getColumnCount(); index++) {
            //get indexes for created date column and path column
            if (columnModel.getColumn(index).getHeaderValue().toString().equals(Bundle.CaseNode_column_metadataFilePath())) {
                originalPathColumnIndex = index;
            } else if (columnModel.getColumn(index).getHeaderValue().toString().equals(Bundle.CaseNode_column_createdTime())) {
                dateColumnIndex = index;
            }
        }
        //Hide path column by default (user can unhide it)
        ETableColumn column = (ETableColumn) columnModel.getColumn(originalPathColumnIndex);
        ((ETableColumnModel) columnModel).setColumnHidden(column, true);
        outline.setRootVisible(false);

        //Sort on Created date column in descending order by default
        outline.setColumnSorted(dateColumnIndex, false, 1);
        if (null == em) {
            em = new ExplorerManager();
        }
        caseTableScrollPane.setViewportView(outlineView);
        this.setVisible(true);
        outline.setRowSelectionAllowed(false);
    }

    /**
     * Add a listener to changes in case selections in the table
     *
     * @param listener the ListSelectionListener to add
     */
    void addListSelectionListener(ListSelectionListener listener) {
        outline.getSelectionModel().addListSelectionListener(listener);
    }

    /**
     * Get the path to the .aut file for the selected case.
     *
     * @return the full path to the selected case's .aut file
     */
    String getCasePath() {
        int[] selectedRows = outline.getSelectedRows();
        if (selectedRows.length == 1) {
            try {
                return ((Node.Property) outline.getModel().getValueAt(outline.convertRowIndexToModel(selectedRows[0]), originalPathColumnIndex)).getValue().toString();
            } catch (IllegalAccessException | InvocationTargetException ex) {
                LOGGER.log(Level.SEVERE, "Unable to get case path from table.", ex);
            }
        }
        return null;
    }

    /**
     * Check if a row could be and is selected.
     *
     * @return true if a row is selected, false if no row is selected
     */
    boolean isRowSelected() {
        return outline.getRowSelectionAllowed() && outline.getSelectedRows().length > 0;
    }

    @NbBundle.Messages({"CaseBrowser.caseListLoading.message=Please Wait..."})
    /**
     * Gets the list of cases known to the review mode cases manager and
     * refreshes the cases table.
     */
    void refresh() {
        if (tableWorker == null || tableWorker.isDone()) {
            outline.setRowSelectionAllowed(false);
            //create a new TableWorker to and execute it in a background thread if one is not currently working
            //set the table to display text informing the user that the list is being retreived and disable case selection
            EmptyNode emptyNode = new EmptyNode(Bundle.CaseBrowser_caseListLoading_message());
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

        caseTableScrollPane = new javax.swing.JScrollPane();

        setMinimumSize(new java.awt.Dimension(0, 5));
        setPreferredSize(new java.awt.Dimension(5, 5));
        setLayout(new java.awt.BorderLayout());

        caseTableScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        caseTableScrollPane.setMinimumSize(new java.awt.Dimension(0, 5));
        caseTableScrollPane.setOpaque(false);
        caseTableScrollPane.setPreferredSize(new java.awt.Dimension(5, 5));
        add(caseTableScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane caseTableScrollPane;
    // End of variables declaration//GEN-END:variables

    /**
     * Swingworker to fetch the updated List of cases in a background thread
     */
    private class LoadCaseListWorker extends SwingWorker<Void, Void> {

        private List<CaseMetadata> cases;

        /**
         * Gets a list of the cases in the top level case folder
         *
         * @return List of cases.
         *
         * @throws CoordinationServiceException
         */
        private List<CaseMetadata> getCases() throws CoordinationService.CoordinationServiceException {
            List<CaseMetadata> caseList = new ArrayList<>();
            List<String> nodeList = CoordinationService.getInstance().getNodeList(CoordinationService.CategoryNode.CASES);

            for (String node : nodeList) {
                Path casePath;
                try {
                    casePath = Paths.get(node).toRealPath(LinkOption.NOFOLLOW_LINKS);

                    File caseFolder = casePath.toFile();
                    if (caseFolder.exists()) {
                        /*
                         * Search for '*.aut' files.
                         */
                        File[] fileArray = caseFolder.listFiles();
                        if (fileArray == null) {
                            continue;
                        }
                        String autFilePath = null;
                        for (File file : fileArray) {
                            String name = file.getName().toLowerCase();
                            if (autFilePath == null && name.endsWith(".aut")) {
                                try {
                                    caseList.add(new CaseMetadata(Paths.get(file.getAbsolutePath())));
                                } catch (CaseMetadata.CaseMetadataException ex) {
                                    LOGGER.log(Level.SEVERE, String.format("Error reading case metadata file '%s'.", autFilePath), ex);
                                }
                                break;
                            }
                        }
                    }
                } catch (IOException ignore) {
                    //if a path could not be resolved to a real path do add it to the caseList
                }
            }
            return caseList;
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
                MultiUserNode caseListNode = new MultiUserNode(cases);
                em.setRootContext(caseListNode);
                outline.setRowSelectionAllowed(true);
            });
        }
    }
}
