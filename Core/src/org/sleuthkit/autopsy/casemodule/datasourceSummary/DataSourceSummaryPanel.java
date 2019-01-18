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

import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbAccessQueryCallback;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;

final class DataSourceSummaryPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final List<IngestJobInfo> allIngestJobs = new ArrayList<>();
    private List<IngestJobInfo> ingestJobs = new ArrayList<>();
    private DataSourceTableModel dataSourceTableModel = new DataSourceTableModel();
    private IngestJobTableModel ingestJobTableModel = new IngestJobTableModel();
    private DataSourceSummaryFilesPanel filesPanel = new DataSourceSummaryFilesPanel();
    private DataSourceSummaryDetailsPanel detailsPanel = new DataSourceSummaryDetailsPanel();
    private final List<DataSource> dataSources = new ArrayList<>();
    private final DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static final Logger logger = Logger.getLogger(DataSourceSummaryPanel.class.getName());

    /**
     * Creates new form DataSourceSummaryPanel for displaying a summary of the
     * data sources for the fcurrent case and the contents found for each
     * datasource.
     */
    @Messages({"DataSourceSummaryPanel.getDataSources.error.text=Failed to get the list of datasources for the current case.",
        "DataSourceSummaryPanel.getDataSources.error.title=Load Failure"})
    DataSourceSummaryPanel() {
        initComponents();
        dataSourceTabbedPane.add("Files",filesPanel);
        dataSourceTabbedPane.addTab("Details", detailsPanel);
//        ingestJobsTable.getTableHeader().setReorderingAllowed(false);
//        fileCountsTable.getTableHeader().setReorderingAllowed(false);
        dataSourcesTable.getTableHeader().setReorderingAllowed(false);
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            allIngestJobs.addAll(skCase.getIngestJobs());
            dataSources.addAll(skCase.getDataSources());
            //if for some reason multiple OS_INFO_ARTIFACTS were created with the same parent object id this will only return one OSInfo object for them
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to load ingest jobs.", ex);
            JOptionPane.showMessageDialog(this, Bundle.DataSourceSummaryPanel_getDataSources_error_text(), Bundle.DataSourceSummaryPanel_getDataSources_error_title(), JOptionPane.ERROR_MESSAGE);
        }
        dataSourcesTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                DataSource selectedDataSource = (dataSourcesTable.getSelectedRow() < 0 ? null : dataSources.get(dataSourcesTable.getSelectedRow()));
                gotoDataSourceButton.setEnabled(selectedDataSource != null);
                updateIngestJobs(selectedDataSource);
                filesPanel.updateFilesTableData(selectedDataSource);
                detailsPanel.updateDetailsPanelData(selectedDataSource);

                this.repaint();
            }
        });
    }

    

    /**
     * Update the ingestJobs list with the ingest jobs for the
     * selectedDataSource
     *
     * @param selectedDataSource the datasource to find the ingest jobs for
     */
    @Messages({"DataSourceSummaryPanel.loadIngestJob.error.text=Failed to load ingest jobs.",
        "DataSourceSummaryPanel.loadIngestJob.error.title=Load Failure"})
    private void updateIngestJobs(DataSource selectedDataSource) {
        ingestJobs.clear();
        if (selectedDataSource != null) {
            for (IngestJobInfo ingestJob : allIngestJobs) {
                if (ingestJob.getObjectId() == selectedDataSource.getId()) {
                    ingestJobs.add(ingestJob);
                }
            }
        }
        ingestJobTableModel = new IngestJobTableModel();
//        ingestJobsTable.setModel(ingestJobTableModel);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        closeButton = new javax.swing.JButton();
        gotoDataSourceButton = new javax.swing.JButton();
        dataSourceSummarySplitPane = new javax.swing.JSplitPane();
        dataSourcesScrollPane = new javax.swing.JScrollPane();
        dataSourcesTable = new javax.swing.JTable();
        dataSourceTabbedPane = new javax.swing.JTabbedPane();

        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(DataSourceSummaryPanel.class, "DataSourceSummaryPanel.closeButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(gotoDataSourceButton, org.openide.util.NbBundle.getMessage(DataSourceSummaryPanel.class, "DataSourceSummaryPanel.gotoDataSourceButton.text")); // NOI18N
        gotoDataSourceButton.setEnabled(false);
        gotoDataSourceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gotoDataSourceButtonActionPerformed(evt);
            }
        });

        dataSourceSummarySplitPane.setDividerLocation(100);
        dataSourceSummarySplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        dataSourcesTable.setModel(dataSourceTableModel);
        dataSourcesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        dataSourcesScrollPane.setViewportView(dataSourcesTable);

        dataSourceSummarySplitPane.setLeftComponent(dataSourcesScrollPane);
        dataSourceSummarySplitPane.setRightComponent(dataSourceTabbedPane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(dataSourceSummarySplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 630, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(gotoDataSourceButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(closeButton)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {closeButton, gotoDataSourceButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(dataSourceSummarySplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 231, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(gotoDataSourceButton)
                    .addComponent(closeButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Adds an action listener to the Close button of the panel.
     *
     * @param action
     */
    void addCloseButtonAction(ActionListener action) {
        this.closeButton.addActionListener(action);
        //the gotoDataSourceButton should also close the dialog
        this.gotoDataSourceButton.addActionListener(action);
    }

    /**
     * Select the data source with the specicied data source id. If no data
     * source matches the dataSourceID it will select the first datasource.
     *
     * @param dataSourceID the ID of the datasource to select, null will cause
     *                     the first datasource to be selected
     */
    void selectDataSource(Long dataSourceID) {
        if (dataSourceID != null) {
            for (int i = 0; i < dataSources.size(); i++) {
                if (dataSources.get(i).getId() == dataSourceID) {
                    dataSourcesTable.setRowSelectionInterval(i, i);
                    //scroll down from top of table to where selected datasource is
                    dataSourcesTable.scrollRectToVisible(new Rectangle(dataSourcesTable.getCellRect(i, 0, true)));
                    return;
                }
            }
        }
        //if there are data sources in the list and none were found that matched the specied dataSourceID select the first one
        if (!dataSources.isEmpty()) {
            dataSourcesTable.setRowSelectionInterval(0, 0);
        }
    }

    /**
     * Performed when the Goto Data Source button is clicked, will cause the
     * window to be closed and the data source which was selected to be
     * navigated to in the tree.
     */
    private void gotoDataSourceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gotoDataSourceButtonActionPerformed
        //the dialog will be closed due to the action listener added in addCloseButtonAction
        DataSource selectedDataSource = (dataSourcesTable.getSelectedRow() < 0 ? null : dataSources.get(dataSourcesTable.getSelectedRow()));
        if (selectedDataSource != null) {
            new ViewContextAction("", selectedDataSource).actionPerformed(evt);
        }
    }//GEN-LAST:event_gotoDataSourceButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JSplitPane dataSourceSummarySplitPane;
    private javax.swing.JTabbedPane dataSourceTabbedPane;
    private javax.swing.JScrollPane dataSourcesScrollPane;
    private javax.swing.JTable dataSourcesTable;
    private javax.swing.JButton gotoDataSourceButton;
    // End of variables declaration//GEN-END:variables

    /**
     * Table model for the Data source table, to display all data sources for
     * the current case.
     */
    @Messages({"DataSourceSummaryPanel.DataSourceTableModel.dataSourceName.header=Data Source Name",
        "DataSourceSummaryPanel.DataSourceTableModel.type.header=Type",
        "DataSourceSummaryPanel.DataSourceTableModel.files.header=Files",
        "DataSourceSummaryPanel.DataSourceTableModel.results.header=Results",
        "DataSourceSummaryPanel.DataSourceTableModel.tags.header=Tags"})
    private class DataSourceTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final List<String> columnHeaders = new ArrayList<>();
        private final Map<Long, Long> fileCountsMap;
        private final Map<Long, Long> artifactCountsMap;
        private final Map<Long, Long> tagCountsMap;
        private final Map<Long, String> typesMap;

        /**
         * Create a new DataSourceTableModel for the current case.
         */
        DataSourceTableModel() {
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_dataSourceName_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_type_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_files_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_results_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_tags_header());
            fileCountsMap = getCountsOfFiles();
            artifactCountsMap = getCountsOfArtifacts();
            tagCountsMap = getCountsOfTags();
            typesMap = getDataSourceTypes();
        }

        @Override
        public int getRowCount() {
            return dataSources.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DataSource currentDataSource = dataSources.get(rowIndex);
            Long count;
            switch (columnIndex) {
                case 0:
                    return currentDataSource.getName();
                case 1:
                    return typesMap.get(currentDataSource.getId());
                case 2:
                    //display 0 if no count is found
                    count = fileCountsMap.get(currentDataSource.getId());
                    return count == null ? 0 : count;
                case 3:
                    //display 0 if no count is found
                    count = artifactCountsMap.get(currentDataSource.getId());
                    return count == null ? 0 : count;
                case 4:
                    //display 0 if no count is found
                    count = tagCountsMap.get(currentDataSource.getId());
                    return count == null ? 0 : count;
                default:
                    break;
            }
            return null;
        }

        /**
         * Get a map containing the TSK_DATA_SOURCE_USAGE description attributes
         * associated with each data source in the current case.
         *
         * @return Collection which maps datasource id to a String which
         *         displays a comma seperated list of values of data source
         *         usage types expected to be in the datasource
         */
        private Map<Long, String> getDataSourceTypes() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
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
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get counts of files for all datasources, providing empty results", ex);
                return Collections.emptyMap();
            }
        }

        /**
         * Get a map containing the number of files in each data source in the
         * current case.
         *
         * @return Collection which maps datasource id to a count for the number
         *         of files in the datasource, will only contain entries for
         *         datasources which have at least 1 file
         */
        private Map<Long, Long> getCountsOfFiles() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                DataSourceCountsCallback callback = new DataSourceCountsCallback();
                final String countFilesQuery = "data_source_obj_id, COUNT(*) AS count"
                        + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                        + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                        + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
                skCase.getCaseDbAccessManager().select(countFilesQuery, callback);
                return callback.getMapOfCounts();
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get counts of files for all datasources, providing empty results", ex);
                return Collections.emptyMap();
            }
        }

        /**
         * Get a map containing the number of artifacts in each data source in
         * the current case.
         *
         * @return Collection which maps datasource id to a count for the number
         *         of artifacts in the datasource, will only contain entries for
         *         datasources which have at least 1 artifact
         */
        private Map<Long, Long> getCountsOfArtifacts() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                DataSourceCountsCallback callback = new DataSourceCountsCallback();
                final String countArtifactsQuery = "data_source_obj_id, COUNT(*) AS count"
                        + " FROM blackboard_artifacts WHERE review_status_id !=" + BlackboardArtifact.ReviewStatus.REJECTED.getID()
                        + " GROUP BY data_source_obj_id"; //NON-NLS
                skCase.getCaseDbAccessManager().select(countArtifactsQuery, callback);
                return callback.getMapOfCounts();
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get counts of artifacts for all datasources, providing empty results", ex);
                return Collections.emptyMap();
            }
        }

        /**
         * Get a map containing the number of tags which have been applied in
         * each data source in the current case. Not necessarily the same as the
         * number of items tagged, as an item can have any number of tags.
         *
         * @return Collection which maps datasource id to a count for the number
         *         of tags which have been applied in the datasource, will only
         *         contain entries for datasources which have at least 1 item
         *         tagged.
         */
        private Map<Long, Long> getCountsOfTags() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
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
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get counts of tags for all datasources, providing empty results", ex);
                return Collections.emptyMap();
            }

        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }

        /**
         * Get the map of Data Source ID to counts of items found for a query
         * which selects data_source_obj_id and count(*) with a group by
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
             * @return Collection which maps datasource id to a count for the
             *         number of items found with that datasource id, only
             *         contains entries for datasources with at least 1 item
             *         found.
             */
            Map<Long, Long> getMapOfCounts() {
                return Collections.unmodifiableMap(dataSourceObjIdCounts);
            }

        }

    }

    /**
     * Table model for the Ingest Job table to display ingest jobs for the
     * selected datasource.
     */
    @Messages({"DataSourceSummaryPanel.IngestJobTableModel.StartTime.header=Start Time",
        "DataSourceSummaryPanel.IngestJobTableModel.EndTime.header=End Time",
        "DataSourceSummaryPanel.IngestJobTableModel.IngestStatus.header=Ingest Status"})
    private class IngestJobTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final List<String> columnHeaders = new ArrayList<>();

        /**
         * Create a new IngestJobTableModel
         */
        IngestJobTableModel() {
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_StartTime_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_EndTime_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_IngestStatus_header());
        }

        @Override
        public int getRowCount() {
            return ingestJobs.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestJobInfo currIngestJob = ingestJobs.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return datetimeFormat.format(currIngestJob.getStartDateTime());
                case 1:
                    Date endDate = currIngestJob.getEndDateTime();
                    if (endDate.getTime() == 0) {
                        return "N/A";
                    }
                    return datetimeFormat.format(currIngestJob.getEndDateTime());
                case 2:
                    return currIngestJob.getStatus().getDisplayName();
                default:
                    break;
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }
    }
}
