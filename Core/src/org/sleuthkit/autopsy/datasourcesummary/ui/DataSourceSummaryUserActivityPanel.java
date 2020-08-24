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

import java.awt.Component;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceTopDomainsSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceTopProgramsSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TopDomainsResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TopProgramsResult;
import org.sleuthkit.autopsy.guiutils.internal.DataFetchWorker;
import org.sleuthkit.autopsy.guiutils.internal.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.guiutils.internal.DataLoadingResult;
import org.sleuthkit.autopsy.guiutils.internal.DataResultJTable;
import org.sleuthkit.autopsy.guiutils.internal.DefaultPojoListTableDataModel;
import org.sleuthkit.autopsy.guiutils.internal.DefaultPojoListTableDataModel.DefaultCellModel;
import org.sleuthkit.autopsy.guiutils.internal.DefaultPojoListTableDataModel.DefaultColumnModel;
import org.sleuthkit.autopsy.guiutils.internal.DefaultPojoListTableDataModel.HorizontalAlign;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.autopsy.guiutils.internal.SwingWorkerSequentialRunner;

/**
 * A panel to display user activity.
 */
@Messages({
    "DataSourceSummaryUserActivityPanel_tab_title=User Activity",
    "DataSourceSummaryUserActivityPanel_TopProgramsTableModel_name_header=Program",
    "DataSourceSummaryUserActivityPanel_TopProgramsTableModel_folder_header=Folder",
    "DataSourceSummaryUserActivityPanel_TopProgramsTableModel_count_header=Run Times",
    "DataSourceSummaryUserActivityPanel_TopProgramsTableModel_lastrun_header=Last Run",
    "DataSourceSummaryUserActivityPanel_TopDomainsTableModel_domain_header=Domain",
    "DataSourceSummaryUserActivityPanel_TopDomainsTableModel_url_header=URL",
    "DataSourceSummaryUserActivityPanel_TopDomainsTableModel_lastAccess_header=Last Access",})
public class DataSourceSummaryUserActivityPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
    private static final int TOP_PROGS_COUNT = 10;
    private static final int TOP_DOMAINS_COUNT = 10;

    private final SwingWorkerSequentialRunner loader = new SwingWorkerSequentialRunner();
    private final DataResultJTable<TopProgramsResult> topProgramsTable;
    private final DataResultJTable<TopDomainsResult> recentDomainsTable;
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    private DataSource dataSource;

    /**
     * Creates new form DataSourceUserActivityPanel
     */
    public DataSourceSummaryUserActivityPanel() {
        this(new DataSourceTopProgramsSummary(), new DataSourceTopDomainsSummary());
    }

    public DataSourceSummaryUserActivityPanel(DataSourceTopProgramsSummary topProgramsData, DataSourceTopDomainsSummary topDomainsData) {
        // set up recent programs table 
        this.topProgramsTable = new DataResultJTable<>(new DefaultPojoListTableDataModel<>(Arrays.asList(
                new DefaultColumnModel<TopProgramsResult>(
                        (prog) -> new DefaultCellModel(prog.getProgramName())
                                .setTooltip(prog.getProgramPath()),
                        Bundle.DataSourceSummaryUserActivityPanel_TopProgramsTableModel_name_header())
                        .setWidth(250),
                new DefaultColumnModel<TopProgramsResult>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopProgramsTableModel_folder_header(),
                        (prog) -> topProgramsData.getShortFolderName(prog.getProgramPath(), prog.getProgramName()))
                        .setWidth(150),
                new DefaultColumnModel<TopProgramsResult>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopProgramsTableModel_count_header(),
                        (prog) -> prog.getRunTimes() == null ? "" : Long.toString(prog.getRunTimes()))
                        .setWidth(80)
                        .setCellHorizontalAlignment(HorizontalAlign.RIGHT),
                new DefaultColumnModel<TopProgramsResult>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopProgramsTableModel_lastrun_header(),
                        (prog) -> prog.getLastRun() == null ? "" : DATETIME_FORMAT.format(prog.getLastRun()))
                        .setWidth(150)
                        .setCellHorizontalAlignment(HorizontalAlign.RIGHT)
        )));

        // set up recent domains table
        recentDomainsTable = new DataResultJTable<>(new DefaultPojoListTableDataModel<>(Arrays.asList(
                new DefaultColumnModel<TopDomainsResult>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDomainsTableModel_domain_header(),
                        (d) -> d.getDomain())
                        .setWidth(250),
                new DefaultColumnModel<TopDomainsResult>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDomainsTableModel_url_header(),
                        (d) -> d.getUrl())
                        .setWidth(250),
                new DefaultColumnModel<TopDomainsResult>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDomainsTableModel_lastAccess_header(),
                        (d) -> DATETIME_FORMAT.format(d.getLastVisit()))
                        .setWidth(150)
                        .setCellHorizontalAlignment(HorizontalAlign.RIGHT)
        )));

        // set up acquisition methods
        dataFetchComponents = Arrays.asList(
                new DataFetchComponents<DataSource, List<TopProgramsResult>>(
                        (dataSource) -> topProgramsData.getTopPrograms(dataSource, TOP_PROGS_COUNT),
                        topProgramsTable::setResult),
                new DataFetchComponents<DataSource, List<TopDomainsResult>>(
                        (dataSource) -> topDomainsData.getRecentDomains(dataSource, TOP_DOMAINS_COUNT),
                        recentDomainsTable::setResult)
        );

        initComponents();
    }

    /**
     * The datasource currently used as the model in this panel.
     *
     * @return The datasource currently being used as the model in this panel.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets datasource to visualize in the panel.
     *
     * @param dataSource The datasource to use in this panel.
     */
    public void setDataSource(final DataSource dataSource) {
        this.dataSource = dataSource;
        if (dataSource == null || !Case.isCaseOpen()) {
            dataFetchComponents.forEach((item) -> item.getResultHandler()
                    .accept(DataLoadingResult.getLoaded(null)));

        } else {
            dataFetchComponents.forEach((item) -> item.getResultHandler()
                    .accept(DataLoadingResult.getLoading()));

            List<DataFetchWorker<?, ?>> workers = dataFetchComponents
                    .stream()
                    .map((components) -> new DataFetchWorker<>(components, dataSource))
                    .collect(Collectors.toList());

            loader.resetLoad(workers);
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

        javax.swing.JScrollPane contentScrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel contentPanel = new javax.swing.JPanel();
        javax.swing.JLabel programsRunLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel topProgramsTablePanel = topProgramsTable;
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel recentDomainsLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel recentDomainsTablePanel = recentDomainsTable;

        setMaximumSize(null);
        setLayout(new java.awt.BorderLayout());

        contentScrollPane.setMaximumSize(null);
        contentScrollPane.setMinimumSize(null);

        contentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.setMaximumSize(new java.awt.Dimension(720, 450));
        contentPanel.setMinimumSize(new java.awt.Dimension(720, 450));
        contentPanel.setLayout(new javax.swing.BoxLayout(contentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        programsRunLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(programsRunLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryUserActivityPanel.class, "DataSourceSummaryUserActivityPanel.programsRunLabel.text")); // NOI18N
        programsRunLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(programsRunLabel);
        contentPanel.add(filler1);

        topProgramsTablePanel.setAlignmentX(0.0F);
        topProgramsTablePanel.setMaximumSize(new java.awt.Dimension(700, 187));
        topProgramsTablePanel.setMinimumSize(new java.awt.Dimension(700, 187));
        topProgramsTablePanel.setPreferredSize(new java.awt.Dimension(700, 187));
        contentPanel.add(topProgramsTablePanel);
        contentPanel.add(filler3);

        recentDomainsLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(recentDomainsLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryUserActivityPanel.class, "DataSourceSummaryUserActivityPanel.recentDomainsLabel.text")); // NOI18N
        contentPanel.add(recentDomainsLabel);
        contentPanel.add(filler2);

        recentDomainsTablePanel.setAlignmentX(0.0F);
        recentDomainsTablePanel.setMaximumSize(new java.awt.Dimension(700, 187));
        recentDomainsTablePanel.setMinimumSize(new java.awt.Dimension(700, 187));
        recentDomainsTablePanel.setPreferredSize(new java.awt.Dimension(700, 187));
        contentPanel.add(recentDomainsTablePanel);

        contentScrollPane.setViewportView(contentPanel);

        add(contentScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
