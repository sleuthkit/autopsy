/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tab shown in data source summary displaying hash set hits, keyword hits,
 * and interesting item hits within a datasource.
 */
@Messages({
    "AnalysisPanel_keyColumn_title=Name",
    "AnalysisPanel_countColumn_title=Count",
    "AnalysisPanel_keywordSearchModuleName=Keyword Search",
    "AnalysisPanel_hashsetHits_tabName=Hashset Hits",
    "AnalysisPanel_keywordHits_tabName=Keyword Hits",
    "AnalysisPanel_interestingItemHits_tabName=Interesting Item Hits",})
public class AnalysisPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;

    // Default Column definitions for each table
    private static final List<ColumnModel<Pair<String, Long>, DefaultCellModel<?>>> DEFAULT_COLUMNS = Arrays.asList(
            new ColumnModel<>(
                    Bundle.AnalysisPanel_keyColumn_title(),
                    (pair) -> new DefaultCellModel<>(pair.getKey()),
                    300
            ),
            new ColumnModel<>(
                    Bundle.AnalysisPanel_countColumn_title(),
                    (pair) -> new DefaultCellModel<>(pair.getValue()),
                    100
            )
    );

    // Identifies the key in the records for the tables.
    private static final Function<Pair<String, Long>, String> DEFAULT_KEY_PROVIDER = (pair) -> pair.getKey();

    private final DataFetcher<DataSource, List<Pair<String, Long>>> hashsetsFetcher;
    private final DataFetcher<DataSource, List<Pair<String, Long>>> keywordsFetcher;
    private final DataFetcher<DataSource, List<Pair<String, Long>>> interestingItemsFetcher;

    private final JTablePanel<Pair<String, Long>> hashsetHitsTable
            = JTablePanel.getJTablePanel(DEFAULT_COLUMNS)
                    .setKeyFunction(DEFAULT_KEY_PROVIDER);

    private final JTablePanel<Pair<String, Long>> keywordHitsTable
            = JTablePanel.getJTablePanel(DEFAULT_COLUMNS)
                    .setKeyFunction(DEFAULT_KEY_PROVIDER);

    private final JTablePanel<Pair<String, Long>> interestingItemsTable
            = JTablePanel.getJTablePanel(DEFAULT_COLUMNS)
                    .setKeyFunction(DEFAULT_KEY_PROVIDER);

    private final List<JTablePanel<?>> tables = Arrays.asList(
            hashsetHitsTable,
            keywordHitsTable,
            interestingItemsTable
    );

    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();

    /**
     * All of the components necessary for data fetch swing workers to load data
     * for each table.
     */
    private final List<DataFetchWorker.DataFetchComponents<DataSource, ?>> dataFetchComponents;

    /**
     * Creates a new DataSourceUserActivityPanel.
     */
    public AnalysisPanel() {
        this(new AnalysisSummaryGetter());
    }

    public AnalysisPanel(AnalysisSummaryGetter analysisData) {
        super(analysisData);

        hashsetsFetcher = (dataSource) -> analysisData.getHashsetCounts(dataSource);
        keywordsFetcher = (dataSource) -> analysisData.getKeywordCounts(dataSource);
        interestingItemsFetcher = (dataSource) -> analysisData.getInterestingItemCounts(dataSource);

        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                // hashset hits loading components
                new DataFetchWorker.DataFetchComponents<>(
                        hashsetsFetcher,
                        (result) -> hashsetHitsTable.showDataFetchResult(result)),
                // keyword hits loading components
                new DataFetchWorker.DataFetchComponents<>(
                        keywordsFetcher,
                        (result) -> keywordHitsTable.showDataFetchResult(result)),
                // interesting item hits loading components
                new DataFetchWorker.DataFetchComponents<>(
                        interestingItemsFetcher,
                        (result) -> interestingItemsTable.showDataFetchResult(result))
        );

        initComponents();
    }

    @Override
    public void close() {
        ingestRunningLabel.unregister();
        super.close();
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        onNewDataSource(dataFetchComponents, tables, dataSource);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane mainScrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel mainContentPanel = new javax.swing.JPanel();
        javax.swing.JPanel ingestRunningPanel = ingestRunningLabel;
        javax.swing.JLabel hashsetHitsLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(32767, 2));
        javax.swing.JPanel hashSetHitsPanel = hashsetHitsTable;
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(32767, 20));
        javax.swing.JLabel keywordHitsLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler4 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(32767, 2));
        javax.swing.JPanel keywordHitsPanel = keywordHitsTable;
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(32767, 20));
        javax.swing.JLabel interestingItemLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler6 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(32767, 2));
        javax.swing.JPanel interestingItemPanel = interestingItemsTable;
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        mainContentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainContentPanel.setMaximumSize(new java.awt.Dimension(32767, 452));
        mainContentPanel.setMinimumSize(new java.awt.Dimension(200, 452));
        mainContentPanel.setLayout(new javax.swing.BoxLayout(mainContentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        ingestRunningPanel.setAlignmentX(0.0F);
        ingestRunningPanel.setMaximumSize(new java.awt.Dimension(32767, 25));
        ingestRunningPanel.setMinimumSize(new java.awt.Dimension(10, 25));
        ingestRunningPanel.setPreferredSize(new java.awt.Dimension(10, 25));
        mainContentPanel.add(ingestRunningPanel);

        org.openide.awt.Mnemonics.setLocalizedText(hashsetHitsLabel, org.openide.util.NbBundle.getMessage(AnalysisPanel.class, "AnalysisPanel.hashsetHitsLabel.text")); // NOI18N
        mainContentPanel.add(hashsetHitsLabel);
        mainContentPanel.add(filler1);

        hashSetHitsPanel.setAlignmentX(0.0F);
        hashSetHitsPanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        hashSetHitsPanel.setMinimumSize(new java.awt.Dimension(10, 106));
        hashSetHitsPanel.setPreferredSize(new java.awt.Dimension(10, 106));
        mainContentPanel.add(hashSetHitsPanel);
        mainContentPanel.add(filler2);

        org.openide.awt.Mnemonics.setLocalizedText(keywordHitsLabel, org.openide.util.NbBundle.getMessage(AnalysisPanel.class, "AnalysisPanel.keywordHitsLabel.text")); // NOI18N
        mainContentPanel.add(keywordHitsLabel);
        mainContentPanel.add(filler4);

        keywordHitsPanel.setAlignmentX(0.0F);
        keywordHitsPanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        keywordHitsPanel.setMinimumSize(new java.awt.Dimension(10, 106));
        keywordHitsPanel.setPreferredSize(new java.awt.Dimension(10, 106));
        mainContentPanel.add(keywordHitsPanel);
        mainContentPanel.add(filler5);

        org.openide.awt.Mnemonics.setLocalizedText(interestingItemLabel, org.openide.util.NbBundle.getMessage(AnalysisPanel.class, "AnalysisPanel.interestingItemLabel.text")); // NOI18N
        mainContentPanel.add(interestingItemLabel);
        mainContentPanel.add(filler6);

        interestingItemPanel.setAlignmentX(0.0F);
        interestingItemPanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        interestingItemPanel.setMinimumSize(new java.awt.Dimension(10, 106));
        interestingItemPanel.setPreferredSize(new java.awt.Dimension(10, 106));
        mainContentPanel.add(interestingItemPanel);
        mainContentPanel.add(filler3);

        mainScrollPane.setViewportView(mainContentPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 756, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
