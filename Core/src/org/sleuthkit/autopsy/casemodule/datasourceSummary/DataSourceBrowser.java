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

import java.awt.event.ActionListener;
import javax.swing.ListSelectionModel;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbAccessQueryCallback;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;

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
    DataSourceBrowser() {
        initComponents();
        explorerManager = new ExplorerManager();
        outlineView = new org.openide.explorer.view.OutlineView();
        dataSourcesScrollPane.setViewportView(outlineView);
        this.setVisible(true);
        outlineView.setPropertyColumns(
                Bundle.DataSourceSummaryNode_column_type_header(), Bundle.DataSourceSummaryNode_column_type_header(),
                Bundle.DataSourceSummaryNode_column_files_header(), Bundle.DataSourceSummaryNode_column_files_header(),
                Bundle.DataSourceSummaryNode_column_results_header(), Bundle.DataSourceSummaryNode_column_results_header(),
                Bundle.DataSourceSummaryNode_column_tags_header(), Bundle.DataSourceSummaryNode_column_tags_header());
        outline = outlineView.getOutline();
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outline.setRootVisible(false);

        dataSourceSummaryList = getDataSourceSummaryList();
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.DataSourceSummaryNode_column_dataSourceName_header());
        dataSourcesScrollPane.setViewportView(outlineView);
        explorerManager.setRootContext(new DataSourceSummaryNode(dataSourceSummaryList));
        this.setVisible(true);
    }

    void addObserver(Observer observer) {
        ((DataSourceSummaryNode)explorerManager.getRootContext()).addObserver(observer);
    }
    
    private List<DataSourceSummary> getDataSourceSummaryList() {
        List<DataSourceSummary> summaryList = new ArrayList<>();
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            final Map<Long, Long> fileCountsMap = getCountsOfFiles(skCase);
            final Map<Long, Long> artifactCountsMap = getCountsOfArtifacts(skCase);
            final Map<Long, Long> tagCountsMap = getCountsOfTags(skCase);
            final Map<Long, String> typesMap = getDataSourceTypes(skCase);
            for (DataSource dataSource : skCase.getDataSources()) {
                summaryList.add(new DataSourceSummary(dataSource, typesMap.get(dataSource.getId()),
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

    DataSource getSelectedDataSource() {
        Node selectedNode[] = explorerManager.getSelectedNodes();
        if (selectedNode.length == 1) {
            return ((DataSourceSummaryEntryNode)selectedNode[0]).getDataSource();
        }
        return null;
    }

    /**
     * Get a map containing the TSK_DATA_SOURCE_USAGE description attributes
     * associated with each data source in the current case.
     *
     * @return Collection which maps datasource id to a String which displays a
     *         comma seperated list of values of data source usage types
     *         expected to be in the datasource
     */
    private Map<Long, String> getDataSourceTypes(SleuthkitCase skCase) {
        try {
            List<BlackboardArtifact> listOfArtifacts = skCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE);
            Map<Long, String> typeMap = new HashMap<>();
            for (BlackboardArtifact typeArtifact : listOfArtifacts) {
                BlackboardAttribute descriptionAttr = typeArtifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DESCRIPTION));
                if (typeArtifact.getDataSource() != null && descriptionAttr != null) {
                    long dsId = typeArtifact.getDataSource().getId();
                    String type = typeMap.get(typeArtifact.getDataSource().getId());
                    if (type == null) {
                        type = descriptionAttr.getValueString();
                    } else {
                        type = type + ", " + descriptionAttr.getValueString();
                    }
                    typeMap.put(dsId, type);
                }
            }
            return typeMap;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get types of files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of files in each data source in the
     * current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         files in the datasource, will only contain entries for
     *         datasources which have at least 1 file
     */
    private Map<Long, Long> getCountsOfFiles(SleuthkitCase skCase) {
        try {
            DataSourceCountsCallback callback = new DataSourceCountsCallback();
            final String countFilesQuery = "data_source_obj_id, COUNT(*) AS count"
                    + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
            skCase.getCaseDbAccessManager().select(countFilesQuery, callback);
            return callback.getMapOfCounts();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get counts of files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of artifacts in each data source in the
     * current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         artifacts in the datasource, will only contain entries for
     *         datasources which have at least 1 artifact
     */
    private Map<Long, Long> getCountsOfArtifacts(SleuthkitCase skCase) {
        try {
            DataSourceCountsCallback callback = new DataSourceCountsCallback();
            final String countArtifactsQuery = "data_source_obj_id, COUNT(*) AS count"
                    + " FROM blackboard_artifacts WHERE review_status_id !=" + BlackboardArtifact.ReviewStatus.REJECTED.getID()
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            skCase.getCaseDbAccessManager().select(countArtifactsQuery, callback);
            return callback.getMapOfCounts();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get counts of artifacts for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of tags which have been applied in each
     * data source in the current case. Not necessarily the same as the number
     * of items tagged, as an item can have any number of tags.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         tags which have been applied in the datasource, will only contain
     *         entries for datasources which have at least 1 item tagged.
     */
    private Map<Long, Long> getCountsOfTags(SleuthkitCase skCase) {
        try {
            DataSourceCountsCallback fileCountcallback = new DataSourceCountsCallback();
            final String countFileTagsQuery = "data_source_obj_id, COUNT(*) AS count"
                    + " FROM content_tags as content_tags, tsk_files as tsk_files"
                    + " WHERE content_tags.obj_id = tsk_files.obj_id"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            skCase.getCaseDbAccessManager().select(countFileTagsQuery, fileCountcallback);
            Map<Long, Long> tagCountMap = new HashMap<>(fileCountcallback.getMapOfCounts());
            DataSourceCountsCallback artifactCountcallback = new DataSourceCountsCallback();
            final String countArtifactTagsQuery = "data_source_obj_id, COUNT(*) AS count"
                    + " FROM blackboard_artifact_tags as artifact_tags,  blackboard_artifacts AS arts"
                    + " WHERE artifact_tags.artifact_id = arts.artifact_id"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            skCase.getCaseDbAccessManager().select(countArtifactTagsQuery, artifactCountcallback);
            //combine the results from the count artifact tags query into the copy of the mapped results from the count file tags query
            artifactCountcallback.getMapOfCounts().forEach((key, value) -> tagCountMap.merge(key, value, (value1, value2) -> value1 + value2));
            return tagCountMap;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get counts of tags for all datasources, providing empty results", ex);
            return Collections.emptyMap();
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

        dataSourcesScrollPane = new javax.swing.JScrollPane();

        setLayout(new java.awt.BorderLayout());
        add(dataSourcesScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;

    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane dataSourcesScrollPane;
    // End of variables declaration//GEN-END:variables

    /**
     * Get the map of Data Source ID to counts of items found for a query which
     * selects data_source_obj_id and count(*) with a group by
     * data_source_obj_id clause.
     */
    private class DataSourceCountsCallback implements CaseDbAccessQueryCallback {

        Map<Long, Long> dataSourceObjIdCounts = new HashMap<>();

        @Override
        public void process(ResultSet rs) {
            try {
                while (rs.next()) {
                    try {
                        dataSourceObjIdCounts.put(rs.getLong("data_source_obj_id"), rs.getLong("count"));
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Unable to get data_source_obj_id or count from result set", ex);
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to get next result for counts by datasource", ex);
            }
        }

        /**
         * Get the processed results
         *
         * @return Collection which maps datasource id to a count for the number
         *         of items found with that datasource id, only contains entries
         *         for datasources with at least 1 item found.
         */
        Map<Long, Long> getMapOfCounts() {
            return Collections.unmodifiableMap(dataSourceObjIdCounts);
        }

    }

}
