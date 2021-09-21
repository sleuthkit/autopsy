/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import org.sleuthkit.autopsy.datasourcesummary.datamodel.CaseDataSourcesSummary;
import java.awt.Cursor;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.RightAlignedTableCellRenderer;
import java.awt.EventQueue;
import java.beans.PropertyVetoException;
import javax.swing.ListSelectionModel;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.event.ListSelectionListener;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datasourcesummary.ui.DataSourceSummaryNode.DataSourceSummaryEntryNode;
import static javax.swing.SwingConstants.RIGHT;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.TableColumn;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel which allows viewing and selecting of Data Sources and some of their
 * related information.
 */
final class DataSourceBrowser extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataSourceBrowser.class.getName());
    private static final int COUNT_COLUMN_WIDTH = 20;
    private static final int INGEST_STATUS_WIDTH = 50;
    private static final int USAGE_COLUMN_WIDTH = 110;
    private static final int DATA_SOURCE_COLUMN_WIDTH = 280;
    private final Outline outline;
    private final org.openide.explorer.view.OutlineView outlineView;
    private final ExplorerManager explorerManager;
    private final List<DataSourceSummary> dataSourceSummaryList;
    private final RightAlignedTableCellRenderer rightAlignedRenderer = new RightAlignedTableCellRenderer();
    private SwingWorker<Void, Void> rootNodeWorker = null;

    /**
     * Creates new form DataSourceBrowser
     */
    DataSourceBrowser() {
        initComponents();
        rightAlignedRenderer.setHorizontalAlignment(RIGHT);
        explorerManager = new ExplorerManager();
        outlineView = new org.openide.explorer.view.OutlineView();
        this.setVisible(true);
        outlineView.setPropertyColumns(
                Bundle.DataSourceSummaryNode_column_status_header(), Bundle.DataSourceSummaryNode_column_status_header(),
                Bundle.DataSourceSummaryNode_column_type_header(), Bundle.DataSourceSummaryNode_column_type_header(),
                Bundle.DataSourceSummaryNode_column_files_header(), Bundle.DataSourceSummaryNode_column_files_header(),
                Bundle.DataSourceSummaryNode_column_results_header(), Bundle.DataSourceSummaryNode_column_results_header(),
                Bundle.DataSourceSummaryNode_column_tags_header(), Bundle.DataSourceSummaryNode_column_tags_header());
        outline = outlineView.getOutline();
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataSourceSummaryList = new ArrayList<>();
        outline.setRootVisible(false);
        add(outlineView, java.awt.BorderLayout.CENTER);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.DataSourceSummaryNode_column_dataSourceName_header());
        for (TableColumn column : Collections.list(outline.getColumnModel().getColumns())) {
            if (column.getHeaderValue().toString().equals(Bundle.DataSourceSummaryNode_column_files_header())
                    || column.getHeaderValue().toString().equals(Bundle.DataSourceSummaryNode_column_results_header())
                    || column.getHeaderValue().toString().equals(Bundle.DataSourceSummaryNode_column_tags_header())) {
                column.setCellRenderer(rightAlignedRenderer);
                column.setPreferredWidth(COUNT_COLUMN_WIDTH);
            } else if (column.getHeaderValue().toString().equals(Bundle.DataSourceSummaryNode_column_type_header())) {
                column.setPreferredWidth(USAGE_COLUMN_WIDTH);
            } else if (column.getHeaderValue().toString().equals(Bundle.DataSourceSummaryNode_column_status_header())) {
                column.setPreferredWidth(INGEST_STATUS_WIDTH);
            } else {
                column.setPreferredWidth(DATA_SOURCE_COLUMN_WIDTH);
            }
        }
        this.setVisible(true);
    }

    /**
     * Select the datasource which matches the specified data source object id.
     * If the specified id is null or no data source matches the id the first
     * data source will be selected instead.
     *
     * @param dataSourceId the object id of the data source to select, null if
     *                     the first data source should be selected
     */
    void selectDataSource(Long dataSourceId) {
        EventQueue.invokeLater(() -> {
            if (dataSourceId != null) {
                for (Node node : explorerManager.getRootContext().getChildren().getNodes()) {
                    if (node instanceof DataSourceSummaryEntryNode && ((DataSourceSummaryEntryNode) node).getDataSource().getId() == dataSourceId) {
                        try {
                            explorerManager.setExploredContextAndSelection(node, new Node[]{node});
                            return;
                        } catch (PropertyVetoException ex) {
                            logger.log(Level.WARNING, "Failed to select the datasource in the explorer view", ex); //NON-NLS
                        }
                    }
                }
            }
            //if no data source was selected and there are data sources to select select the first one
            if (explorerManager.getRootContext().getChildren().getNodes().length > 0) {
                outline.getSelectionModel().setSelectionInterval(0, 0);
            }
        });
    }

    /**
     * Add the specified observer as an observer of the DataSourceSummaryNode's
     * observable.
     *
     * @param observer the observer which should be added
     */
    void addObserver(Observer observer) {
        ((DataSourceSummaryNode) explorerManager.getRootContext()).addObserver(observer);
    }

    /**
     * Get a list of the DataSourceSummary objects representing the information
     * to be displayed for each DataSource in this browser.
     *
     * @return list containing a DataSourceSummary object for each DataSource in
     *         the current case.
     */
    private List<DataSourceSummary> getDataSourceSummaryList(Map<Long, String> usageMap, Map<Long, Long> fileCountsMap) {
        List<DataSourceSummary> summaryList = new ArrayList<>();

        final Map<Long, Long> artifactCountsMap = CaseDataSourcesSummary.getCountsOfArtifacts();
        final Map<Long, Long> tagCountsMap = CaseDataSourcesSummary.getCountsOfTags();
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            for (DataSource dataSource : skCase.getDataSources()) {
                summaryList.add(new DataSourceSummary(dataSource, usageMap.get(dataSource.getId()),
                        fileCountsMap.get(dataSource.getId()), artifactCountsMap.get(dataSource.getId()), tagCountsMap.get(dataSource.getId())));
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to datasources or their counts, providing empty results", ex);
        }
        return summaryList;
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
     * Get the DataSource which is currently selected in the ExplorerManager
     *
     * @return the selected DataSource
     */
    DataSource getSelectedDataSource() {
        Node selectedNode[] = explorerManager.getSelectedNodes();
        if (selectedNode.length == 1 && selectedNode[0] instanceof DataSourceSummaryEntryNode) {
            return ((DataSourceSummaryEntryNode) selectedNode[0]).getDataSource();
        }
        return null;
    }

    /**
     * Update the DataSourceBrowser to display up to date status information for
     * the data sources.
     *
     * @param dataSourceId the ID of the data source which should be updated
     * @param newStatus    the new status which the data source should have
     */
    void refresh(long dataSourceId, IngestJobInfo.IngestJobStatusType newStatus) {

        //attempt to update the status of any datasources that had status which was STARTED
        for (DataSourceSummary summary : dataSourceSummaryList) {
            if (summary.getDataSource().getId() == dataSourceId) {
                summary.setIngestStatus(newStatus);
            }
        }
        //figure out which nodes were previously selected
        Node[] selectedNodes = explorerManager.getSelectedNodes();
        SwingUtilities.invokeLater(() -> {
            explorerManager.setRootContext(new DataSourceSummaryNode(dataSourceSummaryList));
            List<Node> nodesToSelect = new ArrayList<>();
            for (Node node : explorerManager.getRootContext().getChildren().getNodes()) {
                if (node instanceof DataSourceSummaryEntryNode) {
                    //there should only be one selected node as multi-select is disabled
                    for (Node selectedNode : selectedNodes) {
                        if (((DataSourceSummaryEntryNode) node).getDataSource().equals(((DataSourceSummaryEntryNode) selectedNode).getDataSource())) {
                            nodesToSelect.add(node);
                        }
                    }
                }
            }
            //reselect the previously selected Nodes
            try {
                explorerManager.setSelectedNodes(nodesToSelect.toArray(new Node[nodesToSelect.size()]));
            } catch (PropertyVetoException ex) {
                logger.log(Level.WARNING, "Error selecting previously selected nodes", ex);
            }

        });

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    /**
     * Populate the data source browser with an updated list of the data sources
     * and information about them.
     *
     * @param dsSummaryDialog      The dialog which contains this data source
     *                             browser panel.
     * @param selectedDataSourceId The object id for the data source which
     *                             should be selected.
     */
    void populateBrowser(DataSourceSummaryDialog dsSummaryDialog, Long selectedDataSourceId) {
        dsSummaryDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        if (rootNodeWorker != null && !rootNodeWorker.isDone()) {
            rootNodeWorker.cancel(true);
        }

        dataSourceSummaryList.clear();
        rootNodeWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Map<Long, String> usageMap = CaseDataSourcesSummary.getDataSourceTypes();
                Map<Long, Long> fileCountsMap = CaseDataSourcesSummary.getCountsOfFiles();
                dataSourceSummaryList.addAll(getDataSourceSummaryList(usageMap, fileCountsMap));
                return null;
            }

            @Override
            protected void done() {
                explorerManager.setRootContext(new DataSourceSummaryNode(dataSourceSummaryList));
                selectDataSource(selectedDataSourceId);
                addObserver(dsSummaryDialog);
                dsSummaryDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        };
        rootNodeWorker.execute();
    }

    /**
     * Cancel the worker that updates the data source summary list and updates
     * the data source summary browser.
     */
    void cancel() {
        if (rootNodeWorker != null && !rootNodeWorker.isDone()) {
            rootNodeWorker.cancel(true);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
