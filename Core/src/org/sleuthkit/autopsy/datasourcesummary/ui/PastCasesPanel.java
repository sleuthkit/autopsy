/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.PastCasesSummary.PastCasesResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tab shown in data source summary displaying information about a datasource
 * and how it pertains to other cases.
 */
@Messages({
    "PastCasesPanel_caseColumn_title=Case",
    "PastCasesPanel_countColumn_title=Count",
    "PastCasesPanel_onNoCrIngest_message=No results will be shown because the Central Repository module was not run.",})
public class PastCasesPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;

    // model for column indicating the case
    private static final ColumnModel<Pair<String, Long>, DefaultCellModel<?>> CASE_COL = new ColumnModel<>(
            Bundle.PastCasesPanel_caseColumn_title(),
            (pair) -> new DefaultCellModel<>(pair.getKey()),
            300
    );

    // model for column indicating the count
    private static final ColumnModel<Pair<String, Long>, DefaultCellModel<?>> COUNT_COL = new ColumnModel<>(
            Bundle.PastCasesPanel_countColumn_title(),
            (pair) -> new DefaultCellModel<>(pair.getValue()),
            100
    );

    // the template for columns in both tables in this tab
    private static List<ColumnModel<Pair<String, Long>, DefaultCellModel<?>>> DEFAULT_TEMPLATE
            = Arrays.asList(CASE_COL, COUNT_COL);

    private final JTablePanel<Pair<String, Long>> notableFileTable = JTablePanel.getJTablePanel(DEFAULT_TEMPLATE);
    private final JTablePanel<Pair<String, Long>> seenDeviceTable = JTablePanel.getJTablePanel(DEFAULT_TEMPLATE);
    private final JTablePanel<Pair<String, Long>> seenResultTable = JTablePanel.getJTablePanel(DEFAULT_TEMPLATE);

    private final List<JTablePanel<?>> tables = Arrays.asList(
            notableFileTable,
            seenResultTable,
            seenDeviceTable
    );

    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();

    private final DataFetcher<DataSource, PastCasesResult> pastCasesFetcher;

    public PastCasesPanel() {
        this(new PastCasesSummaryGetter());
    }

    /**
     * Creates new form PastCasesPanel
     */
    public PastCasesPanel(PastCasesSummaryGetter pastCaseData) {
        super(pastCaseData);

        this.pastCasesFetcher = (dataSource) -> pastCaseData.getPastCasesData(dataSource);

        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                new DataFetchWorker.DataFetchComponents<>(
                        pastCasesFetcher,
                        (result) -> handleResult(result))
        );

        initComponents();
    }

    /**
     * Handles displaying the result for each table by breaking apart subdata
     * items into seperate results for each table.
     *
     * @param result The result.
     */
    private void handleResult(DataFetchResult<PastCasesResult> result) {
        notableFileTable.showDataFetchResult(DataFetchResult.getSubResult(result, res -> res.getPreviouslyNotable()));
        seenResultTable.showDataFetchResult(DataFetchResult.getSubResult(result, res -> res.getPreviouslySeenResults()));
        seenDeviceTable.showDataFetchResult(DataFetchResult.getSubResult(result, res -> res.getPreviouslySeenDevices()));
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        onNewDataSource(dataFetchComponents, tables, dataSource);
    }

    @Override
    public void close() {
        ingestRunningLabel.unregister();
        super.close();
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
        javax.swing.JLabel notableFileLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel notableFilePanel = notableFileTable;
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel seenResultLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel seenResultPanel = seenResultTable;
        javax.swing.Box.Filler filler4 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel seenDeviceLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel seenDevicePanel = seenDeviceTable;
        javax.swing.Box.Filler filler6 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel warningLabel = new javax.swing.JLabel();

        mainContentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainContentPanel.setLayout(new javax.swing.BoxLayout(mainContentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        ingestRunningPanel.setAlignmentX(0.0F);
        ingestRunningPanel.setMaximumSize(new java.awt.Dimension(32767, 25));
        ingestRunningPanel.setMinimumSize(new java.awt.Dimension(10, 25));
        ingestRunningPanel.setPreferredSize(new java.awt.Dimension(10, 25));
        mainContentPanel.add(ingestRunningPanel);

        org.openide.awt.Mnemonics.setLocalizedText(notableFileLabel, org.openide.util.NbBundle.getMessage(PastCasesPanel.class, "PastCasesPanel.notableFileLabel.text")); // NOI18N
        mainContentPanel.add(notableFileLabel);
        notableFileLabel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(PastCasesPanel.class, "PastCasesPanel.notableFileLabel.text")); // NOI18N

        filler1.setAlignmentX(0.0F);
        mainContentPanel.add(filler1);

        notableFilePanel.setAlignmentX(0.0F);
        notableFilePanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        notableFilePanel.setMinimumSize(new java.awt.Dimension(100, 106));
        notableFilePanel.setPreferredSize(new java.awt.Dimension(100, 106));
        mainContentPanel.add(notableFilePanel);

        filler2.setAlignmentX(0.0F);
        mainContentPanel.add(filler2);

        org.openide.awt.Mnemonics.setLocalizedText(seenResultLabel, org.openide.util.NbBundle.getMessage(PastCasesPanel.class, "PastCasesPanel.seenResultLabel.text")); // NOI18N
        mainContentPanel.add(seenResultLabel);

        filler3.setAlignmentX(0.0F);
        mainContentPanel.add(filler3);

        seenResultPanel.setAlignmentX(0.0F);
        seenResultPanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        seenResultPanel.setMinimumSize(new java.awt.Dimension(100, 106));
        seenResultPanel.setPreferredSize(new java.awt.Dimension(100, 106));
        mainContentPanel.add(seenResultPanel);

        filler4.setAlignmentX(0.0F);
        mainContentPanel.add(filler4);

        org.openide.awt.Mnemonics.setLocalizedText(seenDeviceLabel, org.openide.util.NbBundle.getMessage(PastCasesPanel.class, "PastCasesPanel.seenDeviceLabel.text")); // NOI18N
        mainContentPanel.add(seenDeviceLabel);

        filler5.setAlignmentX(0.0F);
        mainContentPanel.add(filler5);

        seenDevicePanel.setAlignmentX(0.0F);
        seenDevicePanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        seenDevicePanel.setMinimumSize(new java.awt.Dimension(100, 106));
        seenDevicePanel.setPreferredSize(new java.awt.Dimension(100, 106));
        mainContentPanel.add(seenDevicePanel);

        filler6.setAlignmentX(0.0F);
        mainContentPanel.add(filler6);

        org.openide.awt.Mnemonics.setLocalizedText(warningLabel, org.openide.util.NbBundle.getMessage(PastCasesPanel.class, "PastCasesPanel.warningLabel.text")); // NOI18N
        mainContentPanel.add(warningLabel);

        mainScrollPane.setViewportView(mainContentPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
