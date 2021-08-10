/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.datasourcesummaryexport;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.autopsy.contentutils.UserActivitySummary;
import org.sleuthkit.autopsy.contentutils.UserActivitySummary.LastAccessedArtifact;
import org.sleuthkit.autopsy.contentutils.UserActivitySummary.TopAccountResult;
import org.sleuthkit.autopsy.contentutils.UserActivitySummary.TopDeviceAttachedResult;
import org.sleuthkit.autopsy.contentutils.UserActivitySummary.TopDomainsResult;
import org.sleuthkit.autopsy.contentutils.UserActivitySummary.TopProgramsResult;
import org.sleuthkit.autopsy.contentutils.UserActivitySummary.TopWebSearchResult;
import static org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExportAction.getTableExport;

/**
 * A panel to display user activity.
 */
@Messages({
    "ExportUserActivity_tab_title=User Activity",
    "ExportUserActivity_TopProgramsTableModel_tabName=Recent Programs",
    "ExportUserActivity_TopDomainsTableModel_tabName=Recent Domains",
    "ExportUserActivity_TopWebSearchTableModel_tabName=Recent Web Searches",
    "ExportUserActivity_TopDeviceAttachedTableModel_tabName=Recent Devices Attached",
    "ExportUserActivity_TopAccountTableModel_tabName=Recent Account Types Used",
    "ExportUserActivity_TopProgramsTableModel_name_header=Program",
    "ExportUserActivity_TopProgramsTableModel_folder_header=Folder",
    "ExportUserActivity_TopProgramsTableModel_count_header=Run Times",
    "ExportUserActivity_TopProgramsTableModel_lastrun_header=Last Run",
    "ExportUserActivity_TopDomainsTableModel_domain_header=Domain",
    "ExportUserActivity_TopDomainsTableModel_count_header=Visits",
    "ExportUserActivity_TopDomainsTableModel_lastAccess_header=Last Accessed",
    "ExportUserActivity_TopWebSearchTableModel_searchString_header=Search String",
    "ExportUserActivity_TopWebSearchTableModel_dateAccessed_header=Date Accessed",
    "ExportUserActivity_TopWebSearchTableModel_translatedResult_header=Translated",
    "ExportUserActivity_TopDeviceAttachedTableModel_deviceId_header=Device Id",
    "ExportUserActivity_TopDeviceAttachedTableModel_makeModel_header=Make and Model",
    "ExportUserActivity_TopDeviceAttachedTableModel_dateAccessed_header=Last Accessed",
    "ExportUserActivity_TopAccountTableModel_accountType_header=Account Type",
    "ExportUserActivity_TopAccountTableModel_lastAccess_header=Last Accessed",
    "ExportUserActivity_noDataExists=No communication data exists"})
class ExportUserActivity {

    private static final String DATETIME_FORMAT_STR = "yyyy/MM/dd HH:mm:ss";
    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat(DATETIME_FORMAT_STR, Locale.getDefault());
    private static final int TOP_PROGS_COUNT = 10;
    private static final int TOP_DOMAINS_COUNT = 10;
    private static final int TOP_SEARCHES_COUNT = 10;
    private static final int TOP_ACCOUNTS_COUNT = 5;
    private static final int TOP_DEVICES_COUNT = 10;

    // set up recent programs 
    private static final List<ColumnModel<TopProgramsResult, DefaultCellModel<?>>> topProgramsTemplate = Arrays.asList(
            // program name column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopProgramsTableModel_name_header(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getProgramName());
                    },
                    250),
            // program folder column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopProgramsTableModel_folder_header(),
                    (prog) -> {
                        return new DefaultCellModel<>(
                                getShortFolderName(
                                        prog.getProgramPath(),
                                        prog.getProgramName()));
                    },
                    150),
            // run count column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopProgramsTableModel_count_header(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getRunTimes(), (num) -> num == null ? "" : num.toString());
                    },
                    80),
            // last run date column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopProgramsTableModel_lastrun_header(),
                    getDateFunct(),
                    150)
    );

    // set up recent domains
    private static final List<ColumnModel<TopDomainsResult, DefaultCellModel<?>>> topDomainsTemplate = Arrays.asList(
            // domain column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopDomainsTableModel_domain_header(),
                    (recentDomain) -> {
                        return new DefaultCellModel<>(recentDomain.getDomain());
                    },
                    250),
            // count column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopDomainsTableModel_count_header(),
                    (recentDomain) -> {
                        return new DefaultCellModel<>(recentDomain.getVisitTimes(), (num) -> num == null ? "" : num.toString());
                    },
                    100),
            // last accessed column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopDomainsTableModel_lastAccess_header(),
                    getDateFunct(),
                    150)
    );

    // top web searches
    private static final List<ColumnModel<TopWebSearchResult, DefaultCellModel<?>>> topWebSearchesTemplate = Arrays.asList(
            // search string column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopWebSearchTableModel_searchString_header(),
                    (webSearch) -> {
                        return new DefaultCellModel<>(webSearch.getSearchString());
                    },
                    250
            ),
            // last accessed
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopWebSearchTableModel_dateAccessed_header(),
                    getDateFunct(),
                    150
            ),
            // translated value
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopWebSearchTableModel_translatedResult_header(),
                    (webSearch) -> {
                        return new DefaultCellModel<>(webSearch.getTranslatedResult());
                    },
                    250
            )
    );

    // top devices attached
    private static final List<ColumnModel<TopDeviceAttachedResult, DefaultCellModel<?>>> topDevicesTemplate = Arrays.asList(
            // device id column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopDeviceAttachedTableModel_deviceId_header(),
                    (device) -> {
                        return new DefaultCellModel<>(device.getDeviceId());
                    },
                    250
            ),
            // last accessed
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopDeviceAttachedTableModel_dateAccessed_header(),
                    getDateFunct(),
                    150
            ),
            // make and model
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopDeviceAttachedTableModel_makeModel_header(),
                    (device) -> {
                        String make = StringUtils.isBlank(device.getDeviceMake()) ? "" : device.getDeviceMake().trim();
                        String model = StringUtils.isBlank(device.getDeviceModel()) ? "" : device.getDeviceModel().trim();
                        String makeModelString = (make.isEmpty() || model.isEmpty())
                        ? make + model
                        : String.format("%s - %s", make, model);
                        return new DefaultCellModel<>(makeModelString);
                    },
                    250
            )
    );

    // top accounts
    private static final List<ColumnModel<TopAccountResult, DefaultCellModel<?>>> topAccountsTemplate = Arrays.asList(
            // account type column
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopAccountTableModel_accountType_header(),
                    (account) -> {
                        return new DefaultCellModel<>(account.getAccountType());
                    },
                    250
            ),
            // last accessed
            new ColumnModel<>(
                    Bundle.ExportUserActivity_TopAccountTableModel_lastAccess_header(),
                    getDateFunct(),
                    150
            )
    );

    private ExportUserActivity() {
    }

    private static <T extends LastAccessedArtifact> Function<T, DefaultCellModel<?>> getDateFunct() {
        return (T lastAccessed) -> {
            Function<Date, String> dateParser = (dt) -> dt == null ? "" : DATETIME_FORMAT.format(dt);
            return new DefaultCellModel<>(lastAccessed.getLastAccessed(), dateParser, DATETIME_FORMAT_STR);
        };
    }

    /**
     * Queries DataSourceTopProgramsSummary instance for short folder name.
     *
     * @param path The path for the application.
     * @param appName The application name.
     *
     * @return The underlying short folder name if one exists.
     */
    private static String getShortFolderName(String path, String appName) {
        return UserActivitySummary.getShortFolderName(path, appName);
    }

    static List<ExcelExport.ExcelSheetExport> getExports(DataSource dataSource) {
        
        DataFetcher<DataSource, List<TopProgramsResult>> topProgramsFetcher = (ds) -> UserActivitySummary.getTopPrograms(ds, TOP_PROGS_COUNT);
        DataFetcher<DataSource, List<TopDomainsResult>> topDomainsFetcher = (ds) -> UserActivitySummary.getRecentDomains(ds, TOP_DOMAINS_COUNT);
        DataFetcher<DataSource, List<TopWebSearchResult>> topWebSearchesFetcher = (ds) -> UserActivitySummary.getMostRecentWebSearches(ds, TOP_SEARCHES_COUNT);
        DataFetcher<DataSource, List<TopDeviceAttachedResult>> topDevicesAttachedFetcher = (ds) -> UserActivitySummary.getRecentDevices(ds, TOP_DEVICES_COUNT);
        DataFetcher<DataSource, List<TopAccountResult>> topAccountsFetcher = (ds) -> UserActivitySummary.getRecentAccounts(ds, TOP_ACCOUNTS_COUNT);
        
        return Stream.of(
                getTableExport(topProgramsFetcher, topProgramsTemplate, Bundle.ExportUserActivity_TopProgramsTableModel_tabName(), dataSource),
                getTableExport(topDomainsFetcher, topDomainsTemplate, Bundle.ExportUserActivity_TopDomainsTableModel_tabName(), dataSource),
                getTableExport(topWebSearchesFetcher, topWebSearchesTemplate, Bundle.ExportUserActivity_TopWebSearchTableModel_tabName(), dataSource),
                getTableExport(topDevicesAttachedFetcher, topDevicesTemplate, Bundle.ExportUserActivity_TopDeviceAttachedTableModel_tabName(), dataSource),
                getTableExport(topAccountsFetcher, topAccountsTemplate, Bundle.ExportUserActivity_TopAccountTableModel_tabName(), dataSource))
                .filter(sheet -> sheet != null)
                .collect(Collectors.toList());
    }
}
