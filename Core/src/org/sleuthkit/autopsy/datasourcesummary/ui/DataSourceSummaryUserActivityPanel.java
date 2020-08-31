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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceUserActivitySummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceTopProgramsSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceUserActivitySummary.TopAccountResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceUserActivitySummary.TopDeviceAttachedResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceUserActivitySummary.TopWebSearchResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TopDomainsResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TopProgramsResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.HorizontalAlign;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.datamodel.DataSource;

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
    "DataSourceSummaryUserActivityPanel_TopDomainsTableModel_lastAccess_header=Last Access",
    "DataSourceSummaryUserActivityPanel_noDataExists=No communication data exists",
    
    "DataSourceSummaryUserActivityPanel_TopWebSearchTableModel_searchString_header=Search String",
    "DataSourceSummaryUserActivityPanel_TopWebSearchTableModel_dateAccessed_header=Date Accessed",
    "DataSourceSummaryUserActivityPanel_TopWebSearchTableModel_translatedResult_header=Translated",

    "DataSourceSummaryUserActivityPanel_TopDeviceAttachedTableModel_deviceId_header=Device Id",
    "DataSourceSummaryUserActivityPanel_TopDeviceAttachedTableModel_makeModel_header=Make and Model",
    "DataSourceSummaryUserActivityPanel_TopDeviceAttachedTableModel_dateAccessed_header=Last Accessed",

    "DataSourceSummaryUserActivityPanel_TopAccountTableModel_accountType_header=Account Type",
    "DataSourceSummaryUserActivityPanel_TopAccountTableModel_lastAccess_header=Last Accessed",
})
public class DataSourceSummaryUserActivityPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;
    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
    private static final int TOP_PROGS_COUNT = 10;
    private static final int TOP_DOMAINS_COUNT = 10;
    private static final int TOP_SEARCHES_COUNT = 10;
    private static final int TOP_ACCOUNTS_COUNT = 5;
    private static final int TOP_DEVICES_COUNT = 10;

    private static String getFormatted(Date date) {
        return date == null ? "" : DATETIME_FORMAT.format(date);
    }
    
    
    private final JTablePanel<TopProgramsResult> topProgramsTable;
    private final JTablePanel<TopDomainsResult> recentDomainsTable;
    private final JTablePanel<TopWebSearchResult> topWebSearchesTable;
    private final JTablePanel<TopDeviceAttachedResult> topDevicesAttachedTable;
    private final JTablePanel<TopAccountResult> topAccountsTable;

    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;
    private final List<JTablePanel<?>> tables;

    /**
     * Creates a new DataSourceUserActivityPanel.
     */
    public DataSourceSummaryUserActivityPanel() {
        this(new DataSourceTopProgramsSummary(), new DataSourceUserActivitySummary());
    }

    /**
     * Creates a new DataSourceSummaryUserActivityPanel.
     *
     * @param topProgramsData Class from which to obtain top programs data.
     * @param topDomainsData  Class from which to obtain recent domains data.
     */
    public DataSourceSummaryUserActivityPanel(DataSourceTopProgramsSummary topProgramsData, DataSourceUserActivitySummary topDomainsData) {
        // set up recent programs table 
        this.topProgramsTable = JTablePanel.getJTablePanel(Arrays.asList(
                // program name column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopProgramsTableModel_name_header(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getProgramName())
                                    .setTooltip(prog.getProgramPath());
                        },
                        250),
                // program folder column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopProgramsTableModel_folder_header(),
                        (prog) -> {
                            return new DefaultCellModel(
                                    topProgramsData.getShortFolderName(
                                            prog.getProgramPath(),
                                            prog.getProgramName()));
                        },
                        150),
                // run count column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopProgramsTableModel_count_header(),
                        (prog) -> {
                            String runTimes = prog.getRunTimes() == null ? "" : Long.toString(prog.getRunTimes());
                            return new DefaultCellModel(runTimes)
                                    .setHorizontalAlignment(HorizontalAlign.RIGHT);
                        },
                        80),
                // last run date column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopProgramsTableModel_lastrun_header(),
                        (prog) -> {
                            return new DefaultCellModel(getFormatted(prog.getLastRun()))
                                    .setHorizontalAlignment(HorizontalAlign.RIGHT);
                        },
                        150)
        ));

        // set up recent domains table
        this.recentDomainsTable = JTablePanel.getJTablePanel(Arrays.asList(
                // domain column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDomainsTableModel_domain_header(),
                        (recentDomain) -> new DefaultCellModel(recentDomain.getDomain()),
                        250),
                
                // url column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDomainsTableModel_url_header(),
                        (recentDomain) -> new DefaultCellModel(recentDomain.getUrl()),
                        250),
                
                // last accessed column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDomainsTableModel_lastAccess_header(),
                        (recentDomain) -> {
                            return new DefaultCellModel(getFormatted(recentDomain.getLastVisit()))
                                    .setHorizontalAlignment(HorizontalAlign.RIGHT);
                        },
                        150)
        ));

        // top web searches table
        this.topWebSearchesTable = JTablePanel.getJTablePanel(Arrays.asList(
                // search string column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopWebSearchTableModel_searchString_header(),
                        (webSearch) -> new DefaultCellModel(webSearch.getSearchString()),
                        250
                ),
                // last accessed
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopWebSearchTableModel_dateAccessed_header(),
                        (webSearch) -> new DefaultCellModel(getFormatted(webSearch.getDateAccessed()))
                                .setHorizontalAlignment(HorizontalAlign.RIGHT),
                        150
                ),
                // translated value
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopWebSearchTableModel_translatedResult_header(),
                        (webSearch) -> new DefaultCellModel(webSearch.getTranslatedResult()),
                        250
                )
        ));

        // top devices attached table
        this.topDevicesAttachedTable = JTablePanel.getJTablePanel(Arrays.asList(
                // device id column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDeviceAttachedTableModel_deviceId_header(),
                        (device) -> new DefaultCellModel(device.getDeviceId()),
                        250
                ),
                // last accessed
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDeviceAttachedTableModel_dateAccessed_header(),
                        (device) -> new DefaultCellModel(getFormatted(device.getDateAccessed()))
                                .setHorizontalAlignment(HorizontalAlign.RIGHT),
                        150
                ),
                // make and model
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopDeviceAttachedTableModel_makeModel_header(),
                        (device) -> {
                            String make = StringUtils.isBlank(device.getDeviceMake()) ? "" : device.getDeviceMake().trim();
                            String model = StringUtils.isBlank(device.getDeviceModel()) ? "" : device.getDeviceModel().trim();
                            String makeModelString = (make.isEmpty() || model.isEmpty()) ?
                                    make + model :
                                    String.format("%s - %s", make, model);
                            return new DefaultCellModel(makeModelString);
                        },
                        250
                )
        ));
        
        // top accounts table
        this.topAccountsTable = JTablePanel.getJTablePanel(Arrays.asList(
                // account type column
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopAccountTableModel_accountType_header(),
                        (account) -> new DefaultCellModel(account.getAccountType()),
                        250
                ),
                // last accessed
                new ColumnModel<>(
                        Bundle.DataSourceSummaryUserActivityPanel_TopAccountTableModel_lastAccess_header(),
                        (account) -> new DefaultCellModel(getFormatted(account.getLastAccess()))
                                .setHorizontalAlignment(HorizontalAlign.RIGHT),
                        150
                )
        ));
              
                        
        this.tables = Arrays.asList(
                topProgramsTable,
                recentDomainsTable,
                topWebSearchesTable,
                topDevicesAttachedTable,
                topAccountsTable
        );

        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                // top programs query
                new DataFetchComponents<DataSource, List<TopProgramsResult>>(
                        (dataSource) -> topProgramsData.getTopPrograms(dataSource, TOP_PROGS_COUNT),
                        (result) -> topProgramsTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.DataSourceSummaryUserActivityPanel_noDataExists())),
                // top domains query
                new DataFetchComponents<DataSource, List<TopDomainsResult>>(
                        (dataSource) -> topDomainsData.getRecentDomains(dataSource, TOP_DOMAINS_COUNT),
                        (result) -> recentDomainsTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.DataSourceSummaryUserActivityPanel_noDataExists())),
                // top web searches query
                new DataFetchComponents<DataSource, List<TopWebSearchResult>>(
                        (dataSource) -> topDomainsData.getMostRecentWebSearches(dataSource, TOP_SEARCHES_COUNT),
                        (result) -> topWebSearchesTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.DataSourceSummaryUserActivityPanel_noDataExists())),
                // top devices query
                new DataFetchComponents<DataSource, List<TopDeviceAttachedResult>>(
                        (dataSource) -> topDomainsData.getRecentDevices(dataSource, TOP_DEVICES_COUNT),
                        (result) -> topDevicesAttachedTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.DataSourceSummaryUserActivityPanel_noDataExists())),
                // top accounts query
                new DataFetchComponents<DataSource, List<TopAccountResult>>(
                        (dataSource) -> topDomainsData.getRecentAccounts(dataSource, TOP_ACCOUNTS_COUNT),
                        (result) -> topAccountsTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
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

        javax.swing.JScrollPane contentScrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel contentPanel = new javax.swing.JPanel();
        javax.swing.JLabel programsRunLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel topProgramsTablePanel = topProgramsTable;
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel recentDomainsLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel recentDomainsTablePanel = recentDomainsTable;
        javax.swing.Box.Filler filler4 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel recentDomainsLabel1 = new javax.swing.JLabel();
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel recentDomainsTablePanel1 = recentDomainsTable;
        javax.swing.Box.Filler filler6 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel recentDomainsLabel2 = new javax.swing.JLabel();
        javax.swing.Box.Filler filler7 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel recentDomainsTablePanel2 = recentDomainsTable;
        javax.swing.Box.Filler filler8 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel recentDomainsLabel3 = new javax.swing.JLabel();
        javax.swing.Box.Filler filler9 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel recentDomainsTablePanel3 = recentDomainsTable;

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
        contentPanel.add(filler4);

        recentDomainsLabel1.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(recentDomainsLabel1, org.openide.util.NbBundle.getMessage(DataSourceSummaryUserActivityPanel.class, "DataSourceSummaryUserActivityPanel.recentDomainsLabel1.text")); // NOI18N
        contentPanel.add(recentDomainsLabel1);
        contentPanel.add(filler5);

        recentDomainsTablePanel1.setAlignmentX(0.0F);
        recentDomainsTablePanel1.setMaximumSize(new java.awt.Dimension(700, 187));
        recentDomainsTablePanel1.setMinimumSize(new java.awt.Dimension(700, 187));
        recentDomainsTablePanel1.setPreferredSize(new java.awt.Dimension(700, 187));
        contentPanel.add(recentDomainsTablePanel1);
        contentPanel.add(filler6);

        recentDomainsLabel2.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(recentDomainsLabel2, org.openide.util.NbBundle.getMessage(DataSourceSummaryUserActivityPanel.class, "DataSourceSummaryUserActivityPanel.recentDomainsLabel2.text")); // NOI18N
        contentPanel.add(recentDomainsLabel2);
        contentPanel.add(filler7);

        recentDomainsTablePanel2.setAlignmentX(0.0F);
        recentDomainsTablePanel2.setMaximumSize(new java.awt.Dimension(700, 187));
        recentDomainsTablePanel2.setMinimumSize(new java.awt.Dimension(700, 187));
        recentDomainsTablePanel2.setPreferredSize(new java.awt.Dimension(700, 187));
        contentPanel.add(recentDomainsTablePanel2);
        contentPanel.add(filler8);

        recentDomainsLabel3.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(recentDomainsLabel3, org.openide.util.NbBundle.getMessage(DataSourceSummaryUserActivityPanel.class, "DataSourceSummaryUserActivityPanel.recentDomainsLabel3.text")); // NOI18N
        contentPanel.add(recentDomainsLabel3);
        contentPanel.add(filler9);

        recentDomainsTablePanel3.setAlignmentX(0.0F);
        recentDomainsTablePanel3.setMaximumSize(new java.awt.Dimension(700, 187));
        recentDomainsTablePanel3.setMinimumSize(new java.awt.Dimension(700, 187));
        recentDomainsTablePanel3.setPreferredSize(new java.awt.Dimension(700, 187));
        contentPanel.add(recentDomainsTablePanel3);

        contentScrollPane.setViewportView(contentPanel);

        add(contentScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
