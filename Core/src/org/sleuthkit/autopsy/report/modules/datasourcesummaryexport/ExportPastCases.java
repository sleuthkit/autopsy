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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.PastCasesSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.PastCasesSummary.PastCasesResult;
import static org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExportAction.getFetchResult;
import static org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExportAction.getTableExport;
import org.sleuthkit.datamodel.DataSource;

/**
 * Class to export information about a datasource and how it pertains to other
 * cases.
 */
@Messages({
    "ExportPastCases_caseColumn_title=Case",
    "ExportPastCases_countColumn_title=Count",
    "ExportPastCases_notableFileTable_tabName=Cases with common notable items",
    "ExportPastCases_seenResultsTable_tabName=Cases with the same addresses",
    "ExportPastCases_seenDevicesTable_tabName=Cases with the same device IDs",})
class ExportPastCases {

    private final PastCasesSummary pastSummary;

    // model for column indicating the case
    private static final ColumnModel<Pair<String, Long>, DefaultCellModel<?>> CASE_COL = new ColumnModel<>(
            Bundle.ExportPastCases_caseColumn_title(),
            (pair) -> new DefaultCellModel<>(pair.getKey()),
            300
    );

    // model for column indicating the count
    private static final ColumnModel<Pair<String, Long>, DefaultCellModel<?>> COUNT_COL = new ColumnModel<>(
            Bundle.ExportPastCases_countColumn_title(),
            (pair) -> new DefaultCellModel<>(pair.getValue()),
            100
    );

    // the template for columns in both tables in this tab
    private static List<ColumnModel<Pair<String, Long>, DefaultCellModel<?>>> DEFAULT_TEMPLATE
            = Arrays.asList(CASE_COL, COUNT_COL);

    ExportPastCases() {
        pastSummary = new PastCasesSummary();
    }

    List<ExcelExport.ExcelSheetExport> getExports(DataSource dataSource) {
        DataFetcher<DataSource, PastCasesResult> pastCasesFetcher = (ds) -> pastSummary.getPastCasesData(ds);
        PastCasesResult result = getFetchResult(pastCasesFetcher, "Past cases sheets", dataSource);
        if (result == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(
                getTableExport(DEFAULT_TEMPLATE, Bundle.ExportPastCases_notableFileTable_tabName(), result.getPreviouslyNotable()),
                getTableExport(DEFAULT_TEMPLATE, Bundle.ExportPastCases_seenResultsTable_tabName(), result.getPreviouslySeenResults()),
                getTableExport(DEFAULT_TEMPLATE, Bundle.ExportPastCases_seenDevicesTable_tabName(), result.getPreviouslySeenDevices())
        );
    }
}
