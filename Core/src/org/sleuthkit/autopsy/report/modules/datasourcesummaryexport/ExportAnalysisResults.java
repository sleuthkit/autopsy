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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.AnalysisSummary;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelSheetExport;
import static org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExportAction.getTableExport;
import org.sleuthkit.datamodel.DataSource;

/**
 * Class to export data hash set hits, keyword hits, and interesting item hits
 * within a datasource.
 */
@Messages({
    "ExportAnalysisResults_keyColumn_title=Name",
    "ExportAnalysisResults_countColumn_title=Count",
    "ExportAnalysisResults_keywordSearchModuleName=Keyword Search",
    "ExportAnalysisResults_hashsetHits_tabName=Hashset Hits",
    "ExportAnalysisResults_keywordHits_tabName=Keyword Hits",
    "ExportAnalysisResults_interestingItemHits_tabName=Interesting Item Hits",})
class ExportAnalysisResults {

    // Default Column definitions for each table
    private static final List<ColumnModel<Pair<String, Long>, DefaultCellModel<?>>> DEFAULT_COLUMNS = Arrays.asList(
            new ColumnModel<>(
                    Bundle.ExportAnalysisResults_keyColumn_title(),
                    (pair) -> new DefaultCellModel<>(pair.getKey()),
                    300
            ),
            new ColumnModel<>(
                    Bundle.ExportAnalysisResults_countColumn_title(),
                    (pair) -> new DefaultCellModel<>(pair.getValue()),
                    100
            )
    );
    
    private final AnalysisSummary analysisSummary;
    
    ExportAnalysisResults() {
        analysisSummary = new AnalysisSummary();
    }

    List<ExcelSheetExport> getExports(DataSource dataSource) {

        DataFetcher<DataSource, List<Pair<String, Long>>> hashsetsFetcher = (ds) -> analysisSummary.getHashsetCounts(ds);
        DataFetcher<DataSource, List<Pair<String, Long>>> keywordsFetcher = (ds) -> analysisSummary.getKeywordCounts(ds);
        DataFetcher<DataSource, List<Pair<String, Long>>> interestingItemsFetcher = (ds) -> analysisSummary.getInterestingItemCounts(ds);

        return Stream.of(
                getTableExport(hashsetsFetcher, DEFAULT_COLUMNS, Bundle.ExportAnalysisResults_hashsetHits_tabName(), dataSource),
                getTableExport(keywordsFetcher, DEFAULT_COLUMNS, Bundle.ExportAnalysisResults_keywordHits_tabName(), dataSource),
                getTableExport(interestingItemsFetcher, DEFAULT_COLUMNS, Bundle.ExportAnalysisResults_interestingItemHits_tabName(), dataSource))
                .filter(sheet -> sheet != null)
                .collect(Collectors.toList());
    }
}
