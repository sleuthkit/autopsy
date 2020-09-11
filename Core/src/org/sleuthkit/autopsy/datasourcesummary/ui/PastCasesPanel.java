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
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourcePastCasesSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourcePastCasesSummary.NotCentralRepoIngestedException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult.ResultType;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tab shown in data source summary displaying information about a datasource
 * and how it pertains to other cases.
 */
@Messages({
    "PastCasesPanel_caseColumn_title=Case",
    "PastCasesPanel_countColumn_title=Count",
    "PastCasesPanel_onNoCrIngest_message=No results will be shown because the Central Repository module was not run."

})
public class PastCasesPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PastCasesPanel.class.getName());

    private static final ColumnModel<Pair<String, Long>> CASE_COL = new ColumnModel<>(
            Bundle.PastCasesPanel_caseColumn_title(),
            (pair) -> new DefaultCellModel(pair.getKey()),
            300
    );

    private static final ColumnModel<Pair<String, Long>> COUNT_COL = new ColumnModel<>(
            Bundle.PastCasesPanel_countColumn_title(),
            (pair) -> new DefaultCellModel(String.valueOf(pair.getValue())),
            100
    );

    private static final List<ColumnModel<Pair<String, Long>>> DEFAULT_COLUMNS = Arrays.asList(CASE_COL, COUNT_COL);

    private final JTablePanel<Pair<String, Long>> notableFileTable = JTablePanel.getJTablePanel(DEFAULT_COLUMNS);

    private final JTablePanel<Pair<String, Long>> sameIdTable = JTablePanel.getJTablePanel(DEFAULT_COLUMNS);

    private final List<JTablePanel<?>> tables = Arrays.asList(
            notableFileTable,
            sameIdTable
    );

    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    public PastCasesPanel() {
        this(new DataSourcePastCasesSummary());
    }

    /**
     * Creates new form PastCasesPanel
     */
    public PastCasesPanel(DataSourcePastCasesSummary pastCaseData) {
        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                // hashset hits loading components
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> pastCaseData.getPastCasesWithNotableFile(dataSource),
                        (result) -> handleResult(notableFileTable, result)),
                // keyword hits loading components
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> pastCaseData.getPastCasesWithSameId(dataSource),
                        (result) -> handleResult(sameIdTable, result))
        );

        initComponents();
    }

    /**
     * handles displaying the result for the table. If a
     * NotCentralRepoIngestedException is thrown, then an appropriate message is
     * shown. Otherwise, this method uses the tables default showDataFetchResult
     * method.
     *
     * @param table  The table.
     * @param result The result.
     */
    private <T> void handleResult(JTablePanel<T> table, DataFetchResult<List<T>> result) {
        if (result.getResultType() == ResultType.ERROR && result.getException() instanceof NotCentralRepoIngestedException) {
            table.showMessage(Bundle.PastCasesPanel_onNoCrIngest_message());
        } else {
            table.showDataFetchResult(result);
        }
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
            this.tables.forEach((table) -> table.showDefaultLoadingMessage());

            // create swing workers to run for each table
            List<DataFetchWorker<?, ?>> workers = dataFetchComponents
                    .stream()
                    .map((components) -> new DataFetchWorker<>(components, dataSource))
                    .collect(Collectors.toList());

            // submit swing workers to run
            submit(workers);
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

        javax.swing.JScrollPane mainScrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel mainContentPanel = new javax.swing.JPanel();
        javax.swing.JLabel notableFileLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(32767, 2));
        javax.swing.JPanel notableFilePanel = notableFileTable;
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(32767, 20));
        javax.swing.JLabel sameIdLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(32767, 2));
        javax.swing.JPanel sameIdPanel = sameIdTable;
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        mainContentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainContentPanel.setLayout(new javax.swing.BoxLayout(mainContentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        org.openide.awt.Mnemonics.setLocalizedText(notableFileLabel, org.openide.util.NbBundle.getMessage(PastCasesPanel.class, "PastCasesPanel.notableFileLabel.text")); // NOI18N
        mainContentPanel.add(notableFileLabel);
        notableFileLabel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(PastCasesPanel.class, "PastCasesPanel.notableFileLabel.text")); // NOI18N

        mainContentPanel.add(filler1);

        notableFilePanel.setAlignmentX(0.0F);
        notableFilePanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        notableFilePanel.setMinimumSize(new java.awt.Dimension(100, 106));
        notableFilePanel.setPreferredSize(new java.awt.Dimension(100, 106));
        mainContentPanel.add(notableFilePanel);
        mainContentPanel.add(filler2);

        org.openide.awt.Mnemonics.setLocalizedText(sameIdLabel, org.openide.util.NbBundle.getMessage(PastCasesPanel.class, "PastCasesPanel.sameIdLabel.text")); // NOI18N
        mainContentPanel.add(sameIdLabel);
        mainContentPanel.add(filler3);

        sameIdPanel.setAlignmentX(0.0F);
        sameIdPanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        sameIdPanel.setMinimumSize(new java.awt.Dimension(100, 106));
        sameIdPanel.setPreferredSize(new java.awt.Dimension(100, 106));
        mainContentPanel.add(sameIdPanel);
        mainContentPanel.add(filler5);

        mainScrollPane.setViewportView(mainContentPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
