/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.awt.EventQueue;
import java.beans.PropertyVetoException;
import javax.swing.ListSelectionModel;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ListSelectionListener;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.datasourceSummary.DataSourceSummaryNode.DataSourceSummaryEntryNode;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;


/**
 * Panel which allows viewing and selecting of Data Sources and some of their
 * related information.
 */
final class DataSourceBrowser extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataSourceBrowser.class.getName());
    private final Outline outline;
    private final org.openide.explorer.view.OutlineView outlineView;
    private final ExplorerManager explorerManager;
    private final List<DataSourceSummary> dataSourceSummaryList;

    /**
     * Creates new form DataSourceBrowser
     */
    DataSourceBrowser(Map<Long, String> usageMap) {
        initComponents();
        explorerManager = new ExplorerManager();
        outlineView = new org.openide.explorer.view.OutlineView();
        this.setVisible(true);
        outlineView.setPropertyColumns(
                Bundle.DataSourceSummaryNode_column_type_header(), Bundle.DataSourceSummaryNode_column_type_header(),
                Bundle.DataSourceSummaryNode_column_files_header(), Bundle.DataSourceSummaryNode_column_files_header(),
                Bundle.DataSourceSummaryNode_column_results_header(), Bundle.DataSourceSummaryNode_column_results_header(),
                Bundle.DataSourceSummaryNode_column_tags_header(), Bundle.DataSourceSummaryNode_column_tags_header());
        outline = outlineView.getOutline();
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outline.setRootVisible(false);
        dataSourceSummaryList = getDataSourceSummaryList(usageMap);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.DataSourceSummaryNode_column_dataSourceName_header());
        add(outlineView, java.awt.BorderLayout.CENTER);
        explorerManager.setRootContext(new DataSourceSummaryNode(dataSourceSummaryList));
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
    private List<DataSourceSummary> getDataSourceSummaryList(Map<Long, String> usageMap) {
        List<DataSourceSummary> summaryList = new ArrayList<>();
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            final Map<Long, Long> fileCountsMap = DataSourceInfoUtilities.getCountsOfFiles(skCase);
            final Map<Long, Long> artifactCountsMap = DataSourceInfoUtilities.getCountsOfArtifacts(skCase);
            final Map<Long, Long> tagCountsMap = DataSourceInfoUtilities.getCountsOfTags(skCase);
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
        if (selectedNode.length == 1) {
            return ((DataSourceSummaryEntryNode) selectedNode[0]).getDataSource();
        }
        return null;
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
