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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.util.Arrays;
import java.util.List;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.NonEditableTableModel;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceCountsSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TopDomainsResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TopProgramsResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartPanel;

import org.sleuthkit.datamodel.DataSource;

/**
 * Panel for displaying summary information on the known files present in the
 * specified DataSource
 */
@Messages({
    "DataSourceSummaryCountsPanel.ArtifactCountsTableModel.type.header=Artifact Types",
    "DataSourceSummaryCountsPanel.ArtifactCountsTableModel.count.header=Count",
    "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.type.header=File Types",
    "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.count.header=Count",
    "TypesPanel_fileTypesPieChart_title=File Types",
    "TypesPanel_fileTypesPieChart_title=Artifact Types",
    "TypesPanel_filesByCategoryTable_title=Files by Category",
    "TypesPanel_filesByCategoryTable_labelColumn_title=File Type",
    "TypesPanel_filesByCategoryTable_countColumn_title=Count",
    "TypesPanel_filesByCategoryTable_title=Files by Category",})
class TypesPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;

    private final PieChartPanel fileTypesPieChart = new PieChartPanel(Bundle.TypesPanel_fileTypesPieChart_title());
    private final PieChartPanel artifactTypesPieChart = new PieChartPanel(Bundle.TypesPanel_fileTypesPieChart_title());

    private final JTablePanel<Pair<String, Long>> filesByCategoryTable
            = JTablePanel.getJTablePanel(Arrays.asList(
                    new ColumnModel<>(
                            Bundle.TypesPanel_filesByCategoryTable_labelColumn_title(),
                            (pair) -> pair.getFirst(),
                            250
                    ),
                    new ColumnModel<>(
                            Bundle.TypesPanel_filesByCategoryTable_countColumn_title(),
                            (pair) -> pair.getSecond(),
                            150
                    )
            ));

    private final List<LoadableComponent<?>> loadables = Arrays.asList(
            fileTypesPieChart,
            artifactTypesPieChart,
            filesByCategoryTable
    );

    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;
    
    
    public TypesPanel() {

        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                new DataFetchWorker.DataFetchComponents<DataSource, List<TopProgramsResult>>(
                        (dataSource) -> topProgramsData.getTopPrograms(dataSource, TOP_PROGS_COUNT),
                        (result) -> topProgramsTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.DataSourceSummaryUserActivityPanel_noDataExists())),
                new DataFetchWorker.DataFetchComponents<DataSource, List<TopDomainsResult>>(
                        (dataSource) -> topDomainsData.getRecentDomains(dataSource, TOP_DOMAINS_COUNT),
                        (result) -> recentDomainsTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.DataSourceSummaryUserActivityPanel_noDataExists()))
        );

        initComponents();
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        // if no data source is present or the case is not open,
        // set results for tables to null.
        if (dataSource == null || !Case.isCaseOpen()) {
            this.dataFetchComponents.forEach((item) -> item.getResultHandler()
                    .accept(DataFetchResult.getSuccessResult(null)));

        } else {
            // set tables to display loading screen
            this.loadables.forEach((table) -> table.showDefaultLoadingMessage());

            // create swing workers to run for each table
            List<DataFetchWorker<?, ?>> workers = dataFetchComponents
                    .stream()
                    .map((components) -> new DataFetchWorker<>(components, dataSource))
                    .collect(Collectors.toList());

            // submit swing workers to run
            submit(workers);
        }
    }

    
    @Messages({
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.all.row=All",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.allocated.row=Allocated",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.unallocated.row=Unallocated",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.slack.row=Slack",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.directory.row=Directory"
    })
    private static Object[][] getFileCategoryModel(DataSource selectedDataSource) {
        Long fileCount = zeroIfNull(DataSourceCountsSummary.getCountOfFiles(selectedDataSource));
        Long unallocatedFiles = zeroIfNull(DataSourceCountsSummary.getCountOfUnallocatedFiles(selectedDataSource));
        Long allocatedFiles = zeroIfNull(DataSourceCountsSummary.getCountOfAllocatedFiles(selectedDataSource));
        Long slackFiles = zeroIfNull(DataSourceCountsSummary.getCountOfSlackFiles(selectedDataSource));
        Long directories = zeroIfNull(DataSourceCountsSummary.getCountOfDirectories(selectedDataSource));

        return new Object[][]{
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_all_row(), fileCount},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_allocated_row(), allocatedFiles},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_unallocated_row(), unallocatedFiles},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_slack_row(), slackFiles},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_directory_row(), directories}
        };
    }

    /**
     * Returns 0 if value is null.
     *
     * @param origValue The original value.
     *
     * @return The value or 0 if null.
     */
    private static Long zeroIfNull(Long origValue) {
        return origValue == null ? 0 : origValue;
    }

    /**
     * The counts of different artifact types found in a DataSource.
     *
     * @param selectedDataSource The DataSource.
     *
     * @return The JTable data model of counts of artifact types.
     */
    private static Object[][] getArtifactCountsModel(DataSource selectedDataSource) {
        Map<String, Long> artifactMapping = DataSourceCountsSummary.getCountsOfArtifactsByType(selectedDataSource);
        if (artifactMapping == null) {
            return EMPTY_PAIRS;
        }

        return artifactMapping.entrySet().stream()
                .filter((entrySet) -> entrySet != null && entrySet.getKey() != null)
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map((entrySet) -> new Object[]{entrySet.getKey(), entrySet.getValue()})
                .toArray(Object[][]::new);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane scrollParent = new javax.swing.JScrollPane();
        javax.swing.JPanel parentPanel = new javax.swing.JPanel();
        javax.swing.JScrollPane fileCountsByCategoryScrollPane = new javax.swing.JScrollPane();
        fileCountsByCategoryTable = new javax.swing.JTable();
        javax.swing.JLabel byCategoryLabel = new javax.swing.JLabel();
        javax.swing.JLabel resultsByTypeLabel = new javax.swing.JLabel();
        javax.swing.JScrollPane artifactCountsScrollPane = new javax.swing.JScrollPane();
        artifactCountsTable = new javax.swing.JTable();
        javax.swing.JPanel fileTypePiePanel = fileTypePieChart;
        javax.swing.JPanel filesByCatParent = new javax.swing.JPanel();
        javax.swing.JPanel resultsByTypeParent = new javax.swing.JPanel();

        parentPanel.setMinimumSize(new java.awt.Dimension(840, 320));

        fileCountsByCategoryScrollPane.setViewportView(fileCountsByCategoryTable);

        org.openide.awt.Mnemonics.setLocalizedText(byCategoryLabel, org.openide.util.NbBundle.getMessage(TypesPanel.class, "TypesPanel.byCategoryLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(resultsByTypeLabel, org.openide.util.NbBundle.getMessage(TypesPanel.class, "TypesPanel.resultsByTypeLabel.text")); // NOI18N

        artifactCountsTable.setAutoCreateRowSorter(true);
        artifactCountsScrollPane.setViewportView(artifactCountsTable);

        fileTypePiePanel.setPreferredSize(new java.awt.Dimension(400, 300));

        javax.swing.GroupLayout filesByCatParentLayout = new javax.swing.GroupLayout(filesByCatParent);
        filesByCatParent.setLayout(filesByCatParentLayout);
        filesByCatParentLayout.setHorizontalGroup(
            filesByCatParentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        filesByCatParentLayout.setVerticalGroup(
            filesByCatParentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout resultsByTypeParentLayout = new javax.swing.GroupLayout(resultsByTypeParent);
        resultsByTypeParent.setLayout(resultsByTypeParentLayout);
        resultsByTypeParentLayout.setHorizontalGroup(
            resultsByTypeParentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        resultsByTypeParentLayout.setVerticalGroup(
            resultsByTypeParentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout parentPanelLayout = new javax.swing.GroupLayout(parentPanel);
        parentPanel.setLayout(parentPanelLayout);
        parentPanelLayout.setHorizontalGroup(
            parentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(parentPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fileTypePiePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 400, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(parentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(fileCountsByCategoryScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(byCategoryLabel)
                    .addComponent(filesByCatParent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(parentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(resultsByTypeLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(parentPanelLayout.createSequentialGroup()
                        .addComponent(artifactCountsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 244, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(resultsByTypeParent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        parentPanelLayout.setVerticalGroup(
            parentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(parentPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(parentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(parentPanelLayout.createSequentialGroup()
                        .addComponent(fileTypePiePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(parentPanelLayout.createSequentialGroup()
                        .addGroup(parentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(byCategoryLabel)
                            .addComponent(resultsByTypeLabel))
                        .addGroup(parentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, parentPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(resultsByTypeParent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(148, 148, 148))
                            .addGroup(parentPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(parentPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(artifactCountsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                                    .addGroup(parentPanelLayout.createSequentialGroup()
                                        .addComponent(fileCountsByCategoryScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(31, 31, 31)
                                        .addComponent(filesByCatParent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(0, 0, Short.MAX_VALUE)))
                                .addContainerGap())))))
        );

        scrollParent.setViewportView(parentPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scrollParent)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scrollParent)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable artifactCountsTable;
    private javax.swing.JTable fileCountsByCategoryTable;
    // End of variables declaration//GEN-END:variables
}
