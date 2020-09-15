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
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TopProgramsSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopAccountResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopDeviceAttachedResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopWebSearchResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TopProgramsSummary.TopProgramsResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopDomainsResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.datamodel.DataSource;

/**
 * A panel to display user activity.
 */
@Messages({
    "UserActivityPanel_tab_title=User Activity",
    "UserActivityPanel_TopProgramsTableModel_name_header=Program",
    "UserActivityPanel_TopProgramsTableModel_folder_header=Folder",
    "UserActivityPanel_TopProgramsTableModel_count_header=Run Times",
    "UserActivityPanel_TopProgramsTableModel_lastrun_header=Last Run",
    "UserActivityPanel_TopDomainsTableModel_domain_header=Domain",
    "UserActivityPanel_TopDomainsTableModel_url_header=URL",
    "UserActivityPanel_TopDomainsTableModel_lastAccess_header=Last Access",
    "UserActivityPanel_noDataExists=No communication data exists",
    "UserActivityPanel_TopWebSearchTableModel_searchString_header=Search String",
    "UserActivityPanel_TopWebSearchTableModel_dateAccessed_header=Date Accessed",
    "UserActivityPanel_TopWebSearchTableModel_translatedResult_header=Translated",
    "UserActivityPanel_TopDeviceAttachedTableModel_deviceId_header=Device Id",
    "UserActivityPanel_TopDeviceAttachedTableModel_makeModel_header=Make and Model",
    "UserActivityPanel_TopDeviceAttachedTableModel_dateAccessed_header=Last Accessed",
    "UserActivityPanel_TopAccountTableModel_accountType_header=Account Type",
    "UserActivityPanel_TopAccountTableModel_lastAccess_header=Last Accessed",})
public class UserActivityPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;
    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
    private static final int TOP_PROGS_COUNT = 10;
    private static final int TOP_DOMAINS_COUNT = 10;
    private static final int TOP_SEARCHES_COUNT = 10;
    private static final int TOP_ACCOUNTS_COUNT = 5;
    private static final int TOP_DEVICES_COUNT = 10;

    /**
     * Gets a string formatted date or returns empty string if the date is null.
     *
     * @param date The date.
     *
     * @return The formatted date string or empty string if the date is null.
     */
    private static String getFormatted(Date date) {
        return date == null ? "" : DATETIME_FORMAT.format(date);
    }

    // set up recent programs table 
    private final JTablePanel<TopProgramsResult> topProgramsTable = JTablePanel.getJTablePanel(Arrays.asList(
            // program name column
            new ColumnModel<TopProgramsResult>(
                    Bundle.UserActivityPanel_TopProgramsTableModel_name_header(),
                    (prog) -> {
                        return new DefaultCellModel(prog.getProgramName())
                                .setTooltip(prog.getProgramPath());
                    },
                    250),
            // program folder column
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopProgramsTableModel_folder_header(),
                    (prog) -> {
                        return new DefaultCellModel(
                                getShortFolderName(
                                        prog.getProgramPath(),
                                        prog.getProgramName()))
                                .setTooltip(prog.getProgramPath());
                    },
                    150),
            // run count column
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopProgramsTableModel_count_header(),
                    (prog) -> {
                        String runTimes = prog.getRunTimes() == null ? "" : Long.toString(prog.getRunTimes());
                        return new DefaultCellModel(runTimes);
                    },
                    80),
            // last run date column
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopProgramsTableModel_lastrun_header(),
                    (prog) -> new DefaultCellModel(getFormatted(prog.getLastRun())),
                    150)
    ))
            .setKeyFunction((prog) -> prog.getProgramPath() + ":" + prog.getProgramName());

    // set up recent domains table
    private final JTablePanel<TopDomainsResult> recentDomainsTable = JTablePanel.getJTablePanel(Arrays.asList(
            // domain column
            new ColumnModel<TopDomainsResult>(
                    Bundle.UserActivityPanel_TopDomainsTableModel_domain_header(),
                    (recentDomain) -> new DefaultCellModel(recentDomain.getDomain()),
                    250),
            // url column
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopDomainsTableModel_url_header(),
                    (recentDomain) -> new DefaultCellModel(recentDomain.getUrl()),
                    250),
            // last accessed column
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopDomainsTableModel_lastAccess_header(),
                    (recentDomain) -> new DefaultCellModel(getFormatted(recentDomain.getLastVisit())),
                    150)
    ))
            .setKeyFunction((domain) -> domain.getDomain());

    // top web searches table
    private final JTablePanel<TopWebSearchResult> topWebSearchesTable = JTablePanel.getJTablePanel(Arrays.asList(
            // search string column
            new ColumnModel<TopWebSearchResult>(
                    Bundle.UserActivityPanel_TopWebSearchTableModel_searchString_header(),
                    (webSearch) -> new DefaultCellModel(webSearch.getSearchString()),
                    250
            ),
            // last accessed
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopWebSearchTableModel_dateAccessed_header(),
                    (webSearch) -> new DefaultCellModel(getFormatted(webSearch.getDateAccessed())),
                    150
            ),
            // translated value
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopWebSearchTableModel_translatedResult_header(),
                    (webSearch) -> new DefaultCellModel(webSearch.getTranslatedResult()),
                    250
            )
    ))
            .setKeyFunction((query) -> query.getSearchString());

    // top devices attached table
    private final JTablePanel<TopDeviceAttachedResult> topDevicesAttachedTable = JTablePanel.getJTablePanel(Arrays.asList(
            // device id column
            new ColumnModel<TopDeviceAttachedResult>(
                    Bundle.UserActivityPanel_TopDeviceAttachedTableModel_deviceId_header(),
                    (device) -> new DefaultCellModel(device.getDeviceId()),
                    250
            ),
            // last accessed
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopDeviceAttachedTableModel_dateAccessed_header(),
                    (device) -> new DefaultCellModel(getFormatted(device.getDateAccessed())),
                    150
            ),
            // make and model
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopDeviceAttachedTableModel_makeModel_header(),
                    (device) -> {
                        String make = StringUtils.isBlank(device.getDeviceMake()) ? "" : device.getDeviceMake().trim();
                        String model = StringUtils.isBlank(device.getDeviceModel()) ? "" : device.getDeviceModel().trim();
                        String makeModelString = (make.isEmpty() || model.isEmpty())
                        ? make + model
                        : String.format("%s - %s", make, model);
                        return new DefaultCellModel(makeModelString);
                    },
                    250
            )
    ))
            .setKeyFunction((topDevice) -> topDevice.getDeviceId());

    // top accounts table
    private final JTablePanel<TopAccountResult> topAccountsTable = JTablePanel.getJTablePanel(Arrays.asList(
            // account type column
            new ColumnModel<TopAccountResult>(
                    Bundle.UserActivityPanel_TopAccountTableModel_accountType_header(),
                    (account) -> new DefaultCellModel(account.getAccountType()),
                    250
            ),
            // last accessed
            new ColumnModel<>(
                    Bundle.UserActivityPanel_TopAccountTableModel_lastAccess_header(),
                    (account) -> new DefaultCellModel(getFormatted(account.getLastAccess())),
                    150
            )
    ))
            .setKeyFunction((topAccount) -> topAccount.getAccountType());

    private final List<JTablePanel<?>> tables = Arrays.asList(
            topProgramsTable,
            recentDomainsTable,
            topWebSearchesTable,
            topDevicesAttachedTable,
            topAccountsTable
    );

    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;
    private final TopProgramsSummary topProgramsData;

    /**
     * Creates a new UserActivityPanel.
     */
    public UserActivityPanel() {
        this(new TopProgramsSummary(), new UserActivitySummary());
    }

    /**
     * Creates a new UserActivityPanel.
     *
     * @param topProgramsData  Class from which to obtain top programs data.
     * @param userActivityData Class from which to obtain remaining user
     *                         activity data.
     */
    public UserActivityPanel(
            TopProgramsSummary topProgramsData,
            UserActivitySummary userActivityData) {

        super(topProgramsData, userActivityData);

        this.topProgramsData = topProgramsData;

        // set up data acquisition methods
        this.dataFetchComponents = Arrays.asList(
                // top programs query
                new DataFetchComponents<DataSource, List<TopProgramsResult>>(
                        (dataSource) -> topProgramsData.getTopPrograms(dataSource, TOP_PROGS_COUNT),
                        (result) -> topProgramsTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.UserActivityPanel_noDataExists())),
                // top domains query
                new DataFetchComponents<DataSource, List<TopDomainsResult>>(
                        (dataSource) -> userActivityData.getRecentDomains(dataSource, TOP_DOMAINS_COUNT),
                        (result) -> recentDomainsTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.UserActivityPanel_noDataExists())),
                // top web searches query
                new DataFetchComponents<DataSource, List<TopWebSearchResult>>(
                        (dataSource) -> userActivityData.getMostRecentWebSearches(dataSource, TOP_SEARCHES_COUNT),
                        (result) -> topWebSearchesTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.UserActivityPanel_noDataExists())),
                // top devices query
                new DataFetchComponents<DataSource, List<TopDeviceAttachedResult>>(
                        (dataSource) -> userActivityData.getRecentDevices(dataSource, TOP_DEVICES_COUNT),
                        (result) -> topDevicesAttachedTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.UserActivityPanel_noDataExists())),
                // top accounts query
                new DataFetchComponents<DataSource, List<TopAccountResult>>(
                        (dataSource) -> userActivityData.getRecentAccounts(dataSource, TOP_ACCOUNTS_COUNT),
                        (result) -> topAccountsTable.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.UserActivityPanel_noDataExists()))
        );

        initComponents();
    }

    /**
     * Queries DataSourceTopProgramsSummary instance for short folder name.
     *
     * @param path    The path for the application.
     * @param appName The application name.
     *
     * @return The underlying short folder name if one exists.
     */
    private String getShortFolderName(String path, String appName) {
        return this.topProgramsData.getShortFolderName(path, appName);
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
        javax.swing.JLabel topWebSearchLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel topWebSearches = topWebSearchesTable;
        javax.swing.Box.Filler filler6 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel topDevicesAttachedLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler7 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel recentDevicesAttached = topDevicesAttachedTable;
        javax.swing.Box.Filler filler8 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel recentAccountsLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler9 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel topAccounts = topAccountsTable;

        setLayout(new java.awt.BorderLayout());

        contentScrollPane.setMaximumSize(null);
        contentScrollPane.setMinimumSize(null);

        contentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.setMaximumSize(new java.awt.Dimension(32767, 450));
        contentPanel.setMinimumSize(new java.awt.Dimension(10, 450));
        contentPanel.setLayout(new javax.swing.BoxLayout(contentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        programsRunLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(programsRunLabel, org.openide.util.NbBundle.getMessage(UserActivityPanel.class, "UserActivityPanel.programsRunLabel.text")); // NOI18N
        programsRunLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(programsRunLabel);
        contentPanel.add(filler1);

        topProgramsTablePanel.setAlignmentX(0.0F);
        topProgramsTablePanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        topProgramsTablePanel.setMinimumSize(new java.awt.Dimension(10, 106));
        topProgramsTablePanel.setPreferredSize(new java.awt.Dimension(10, 106));
        contentPanel.add(topProgramsTablePanel);
        contentPanel.add(filler3);

        recentDomainsLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(recentDomainsLabel, org.openide.util.NbBundle.getMessage(UserActivityPanel.class, "UserActivityPanel.recentDomainsLabel.text")); // NOI18N
        contentPanel.add(recentDomainsLabel);
        contentPanel.add(filler2);

        recentDomainsTablePanel.setAlignmentX(0.0F);
        recentDomainsTablePanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        recentDomainsTablePanel.setMinimumSize(new java.awt.Dimension(10, 106));
        recentDomainsTablePanel.setPreferredSize(new java.awt.Dimension(10, 106));
        contentPanel.add(recentDomainsTablePanel);
        contentPanel.add(filler4);

        topWebSearchLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(topWebSearchLabel, org.openide.util.NbBundle.getMessage(UserActivityPanel.class, "UserActivityPanel.topWebSearchLabel.text")); // NOI18N
        contentPanel.add(topWebSearchLabel);
        contentPanel.add(filler5);

        topWebSearches.setAlignmentX(0.0F);
        topWebSearches.setMaximumSize(new java.awt.Dimension(32767, 106));
        topWebSearches.setMinimumSize(new java.awt.Dimension(10, 106));
        topWebSearches.setPreferredSize(new java.awt.Dimension(10, 106));
        contentPanel.add(topWebSearches);
        contentPanel.add(filler6);

        topDevicesAttachedLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(topDevicesAttachedLabel, org.openide.util.NbBundle.getMessage(UserActivityPanel.class, "UserActivityPanel.topDevicesAttachedLabel.text")); // NOI18N
        contentPanel.add(topDevicesAttachedLabel);
        contentPanel.add(filler7);

        recentDevicesAttached.setAlignmentX(0.0F);
        recentDevicesAttached.setMaximumSize(new java.awt.Dimension(32767, 106));
        recentDevicesAttached.setMinimumSize(new java.awt.Dimension(10, 106));
        recentDevicesAttached.setPreferredSize(new java.awt.Dimension(10, 106));
        contentPanel.add(recentDevicesAttached);
        contentPanel.add(filler8);

        recentAccountsLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(recentAccountsLabel, org.openide.util.NbBundle.getMessage(UserActivityPanel.class, "UserActivityPanel.recentAccountsLabel.text")); // NOI18N
        contentPanel.add(recentAccountsLabel);
        contentPanel.add(filler9);

        topAccounts.setAlignmentX(0.0F);
        topAccounts.setMaximumSize(new java.awt.Dimension(32767, 106));
        topAccounts.setMinimumSize(new java.awt.Dimension(10, 106));
        topAccounts.setPreferredSize(new java.awt.Dimension(10, 106));
        contentPanel.add(topAccounts);

        contentScrollPane.setViewportView(contentPanel);

        add(contentScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
