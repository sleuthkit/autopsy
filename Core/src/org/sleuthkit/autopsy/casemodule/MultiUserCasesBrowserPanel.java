/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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

import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumnModel;
import org.netbeans.swing.etable.ETableColumn;
import org.netbeans.swing.etable.ETableColumnModel;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.explorer.ExplorerManager;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coordinationservice.CaseNodeData;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.EmptyNode;
import java.util.concurrent.ExecutionException;
import org.openide.explorer.view.OutlineView;

/**
 * A JPanel with a scroll pane child component that contains a NetBeans
 * OutlineView that can be used to display a list of the multi-user cases known
 * to the coordination service.
 */
@SuppressWarnings("PMD.SingularField") // Matisse-generated UI widgets cause lots of false positives
class MultiUserCasesBrowserPanel extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(MultiUserCasesBrowserPanel.class.getName());
    private ExplorerManager explorerManager; // RJCTODO: COnsider making this final
    private final Outline outline;
    private final OutlineView outlineView;
    private LoadCaseListWorker loadCaseListWorker;

    /**
     * Constructs a JPanel with a scroll pane child component that contains a
     * NetBeans OutlineView that can be used to display a list of the multi-user
     * cases known to the coordination service.
     */
    MultiUserCasesBrowserPanel() {
        outlineView = new org.openide.explorer.view.OutlineView();
        initComponents();
        outline = outlineView.getOutline();
        customizeOutlineView();
    }

    /**
     * Configures the the table of cases and its columns.
     */
    private void customizeOutlineView() {
        outlineView.setPropertyColumns(
                Bundle.CaseNode_column_createTime(), Bundle.CaseNode_column_createTime(), // RJCTODO: Move these into this file?
                Bundle.CaseNode_column_path(), Bundle.CaseNode_column_path());
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.CaseNode_column_name());
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumnModel columnModel = outline.getColumnModel();
        int pathColumnIndex = 0;
        int dateColumnIndex = 0;
        for (int index = 0; index < columnModel.getColumnCount(); index++) {
            if (columnModel.getColumn(index).getHeaderValue().toString().equals(Bundle.CaseNode_column_path())) {
                pathColumnIndex = index;
            } else if (columnModel.getColumn(index).getHeaderValue().toString().equals(Bundle.CaseNode_column_createTime())) {
                dateColumnIndex = index;
            }
        }

        /*
         * Hide path column by default (user can unhide it)
         */
        ETableColumn column = (ETableColumn) columnModel.getColumn(pathColumnIndex);
        ((ETableColumnModel) columnModel).setColumnHidden(column, true);
        outline.setRootVisible(false);

        /*
         * Sort on Created date column in descending order by default.
         */
        outline.setColumnSorted(dateColumnIndex, false, 1);

        if (null == explorerManager) {
            explorerManager = new ExplorerManager();
        }

        caseTableScrollPane.setViewportView(outlineView);
        this.setVisible(true);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    /**
     * Gets the list of cases known to the review mode cases manager and
     * refreshes the cases table.
     */
    @NbBundle.Messages({
        "MultiUserCasesBrowserPanel.waitNode.message=Please Wait..."
    })
    void refreshCases() {
        if (loadCaseListWorker == null || loadCaseListWorker.isDone()) {
            /*
             * RJCTODO: Explain this or move the data fetching into the create
             * keys method of the nodes...
             */
            EmptyNode emptyNode = new EmptyNode(Bundle.MultiUserCasesBrowserPanel_waitNode_message());
            explorerManager.setRootContext(emptyNode);

            loadCaseListWorker = new LoadCaseListWorker();
            loadCaseListWorker.execute();
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
     * A background task that gets the multi-user case data from the case
     * directory lock coordination service nodes.
     */
    private class LoadCaseListWorker extends SwingWorker<List<CaseNodeData>, Void> {

        private static final String CASE_AUTO_INGEST_LOG_NAME = "AUTO_INGEST_LOG.TXT"; //NON-NLS
        private static final String RESOURCES_LOCK_SUFFIX = "_RESOURCES"; //NON-NLS

        @Override
        protected List<CaseNodeData> doInBackground() throws Exception {
            final List<CaseNodeData> cases = new ArrayList<>();
            final CoordinationService coordinationService = CoordinationService.getInstance();
            final List<String> nodeList = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
            for (String nodeName : nodeList) {
                /*
                 * Ignore case name lock nodes.
                 */
                final Path nodeNameAsPath = Paths.get(nodeName);
                if (!(nodeNameAsPath.toString().contains("\\") || nodeNameAsPath.toString().contains("//"))) {
                    continue;
                }

                /*
                 * Ignore case auto ingest log lock nodes and resource lock
                 * nodes.
                 */
                final String lastNodeNameComponent = nodeNameAsPath.getFileName().toString();
                if (lastNodeNameComponent.equals(CASE_AUTO_INGEST_LOG_NAME) || lastNodeNameComponent.endsWith(RESOURCES_LOCK_SUFFIX)) {
                    continue;
                }

                /*
                 * Get the data from the case directory lock node. This data may not exist
                 * for "legacy" nodes. If it is missing, create it.
                 */
                try {
                    CaseNodeData nodeData;
                    byte[] nodeBytes = CoordinationService.getInstance().getNodeData(CoordinationService.CategoryNode.CASES, nodeName);
                    if (nodeBytes != null && nodeBytes.length > 0) {
                        nodeData = new CaseNodeData(nodeBytes);
                        if (nodeData.getVersion() > 0) {
                            cases.add(nodeData);
                        } else {
                            nodeData = createNodeDataFromCaseMetadata(nodeName);
                        }
                    } else {
                        nodeData = createNodeDataFromCaseMetadata(nodeName);
                    }
                    cases.add(nodeData);

                } catch (CoordinationService.CoordinationServiceException | CaseNodeData.InvalidDataException | IOException | CaseMetadata.CaseMetadataException ex) {
                    logger.log(Level.SEVERE, String.format("Error getting coordination service node data for %s", nodeName), ex);
                }

            }
            return cases;
        }

        @Override
        protected void done() {
            try {
                final List<CaseNodeData> cases = get();
                EventQueue.invokeLater(() -> {
                    MultiUserCasesRootNode caseListNode = new MultiUserCasesRootNode(cases);
                    explorerManager.setRootContext(caseListNode);
                });
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "Unexpected interrupt during background processing", ex);
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Error during background processing", ex);
            }
        }

        private CaseNodeData createNodeDataFromCaseMetadata(String nodeName) throws IOException, CaseMetadata.CaseMetadataException, ParseException, CoordinationService.CoordinationServiceException, InterruptedException {
            Path caseDirectoryPath = Paths.get(nodeName).toRealPath(LinkOption.NOFOLLOW_LINKS);
            File caseDirectory = caseDirectoryPath.toFile();
            if (caseDirectory.exists()) {
                File[] files = caseDirectory.listFiles();
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(CaseMetadata.getFileExtension())) {
                        CaseMetadata metadata = new CaseMetadata(Paths.get(file.getAbsolutePath()));
                        CaseNodeData nodeData = new CaseNodeData(metadata);
                        CoordinationService coordinationService = CoordinationService.getInstance();
                        coordinationService.setNodeData(CoordinationService.CategoryNode.CASES, nodeName, nodeData.toArray());
                    }
                }
            }
            throw new IOException(String.format("Could not find case metadata file for %s", nodeName));
        }

    }

}
