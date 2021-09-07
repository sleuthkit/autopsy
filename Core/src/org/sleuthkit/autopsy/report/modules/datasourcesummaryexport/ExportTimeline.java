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

import java.awt.Color;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartSeries;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartSeries.BarChartItem;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartSeries.OrderedKey;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary.DailyActivityAmount;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary.TimelineSummaryData;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.KeyValueItemExportable;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.TitledExportable;
import org.sleuthkit.datamodel.DataSource;

/**
 * Class to export information about a data source's timeline events.
 */
@Messages({
    "TimelinePanel_earliestLabel_title=Earliest",
    "TimelinePanel_latestLabel_title=Latest",
    "TimlinePanel_last30DaysChart_title=Last 30 Days",
    "TimlinePanel_last30DaysChart_fileEvts_title=File Events",
    "TimlinePanel_last30DaysChart_artifactEvts_title=Result Events",})
class ExportTimeline {
    
    private final TimelineSummary timelineSummary;

    private static final String EARLIEST_LATEST_FORMAT_STR = "MMM d, yyyy";
    private static final DateFormat EARLIEST_LATEST_FORMAT = TimelineSummary.getUtcFormat(EARLIEST_LATEST_FORMAT_STR);
    private static final DateFormat CHART_FORMAT = TimelineSummary.getUtcFormat("MMM d, yyyy");
    private static final int MOST_RECENT_DAYS_COUNT = 30;   
    
    private static final Color FILE_EVT_COLOR = new Color(228, 22, 28);
    private static final Color ARTIFACT_EVT_COLOR = new Color(21, 227, 100); 

    /**
     * Creates new form PastCasesPanel
     */
    ExportTimeline() {
        timelineSummary = new TimelineSummary();
    }
    
        /**
     * Converts DailyActivityAmount data retrieved from TimelineSummaryGetter
     * into data to be displayed as a bar chart.
     *
     * @param recentDaysActivity    The data retrieved from
     *                              TimelineSummaryGetter.
     * @param showIntermediateDates If true, shows all dates. If false, shows
     *                              only first and last date.
     *
     * @return The data to be displayed in the BarChart.
     */
    private static List<BarChartSeries> parseChartData(List<DailyActivityAmount> recentDaysActivity, boolean showIntermediateDates) {
        // if no data, return null indicating no result.
        if (CollectionUtils.isEmpty(recentDaysActivity)) {
            return null;
        }

        // Create a bar chart item for each recent days activity item
        List<BarChartItem> fileEvtCounts = new ArrayList<>();
        List<BarChartItem> artifactEvtCounts = new ArrayList<>();

        for (int i = 0; i < recentDaysActivity.size(); i++) {
            DailyActivityAmount curItem = recentDaysActivity.get(i);

            long fileAmt = curItem.getFileActivityCount();
            long artifactAmt = curItem.getArtifactActivityCount() * 100;
            String formattedDate = (showIntermediateDates || i == 0 || i == recentDaysActivity.size() - 1)
                    ? TimelineSummary.formatDate(curItem.getDay(), CHART_FORMAT) : "";

            OrderedKey thisKey = new OrderedKey(formattedDate, i);
            fileEvtCounts.add(new BarChartItem(thisKey, fileAmt));
            artifactEvtCounts.add(new BarChartItem(thisKey, artifactAmt));
        }

        return Arrays.asList(
                new BarChartSeries(Bundle.TimlinePanel_last30DaysChart_fileEvts_title(), FILE_EVT_COLOR, fileEvtCounts),
                new BarChartSeries(Bundle.TimlinePanel_last30DaysChart_artifactEvts_title(), ARTIFACT_EVT_COLOR, artifactEvtCounts));
    }

    /**
     * Create a default cell model to be use with excel export in the earliest /
     * latest date format.
     *
     * @param date The date.
     * @return The cell model.
     */
    private static DefaultCellModel<?> getEarliestLatestCell(Date date) {
        return new DefaultCellModel<>(date, (dt) -> dt == null ? "" : EARLIEST_LATEST_FORMAT.format(dt), EARLIEST_LATEST_FORMAT_STR);
    }
    
    @Messages({
        "TimelinePanel_getExports_sheetName=Timeline",
        "TimelinePanel_getExports_activityRange=Activity Range",
        "TimelinePanel_getExports_earliest=Earliest:",
        "TimelinePanel_getExports_latest=Latest:",
        "TimelinePanel_getExports_dateColumnHeader=Date",
        "TimelinePanel_getExports_chartName=Last 30 Days",})
    List<ExcelExport.ExcelSheetExport> getExports(DataSource dataSource) {
        DataFetcher<DataSource, TimelineSummaryData> dataFetcher = (ds) -> timelineSummary.getTimelineSummaryData(ds, MOST_RECENT_DAYS_COUNT);
        TimelineSummaryData summaryData = ExcelExportAction.getFetchResult(dataFetcher, "Timeline", dataSource);
        if (summaryData == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(
                new ExcelSpecialFormatExport(Bundle.TimelinePanel_getExports_sheetName(),
                        Arrays.asList(
                                new TitledExportable(Bundle.TimelinePanel_getExports_activityRange(), Collections.emptyList()),
                                new KeyValueItemExportable(Bundle.TimelinePanel_getExports_earliest(), getEarliestLatestCell(summaryData.getMinDate())),
                                new KeyValueItemExportable(Bundle.TimelinePanel_getExports_latest(), getEarliestLatestCell(summaryData.getMaxDate())),
                                new BarChartExport(Bundle.TimelinePanel_getExports_dateColumnHeader(),
                                        "#,###",
                                        Bundle.TimelinePanel_getExports_chartName(),
                                        parseChartData(summaryData.getMostRecentDaysActivity(), true)))));
    }
}
