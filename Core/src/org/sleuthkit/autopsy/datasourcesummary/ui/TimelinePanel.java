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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.apache.commons.collections.CollectionUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineDataSourceUtils;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary.DailyActivityAmount;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary.TimelineSummaryData;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartPanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartPanel.BarChartItem;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartPanel.BarChartSeries;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartPanel.OrderedKey;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableLabel;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineModule;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.autopsy.timeline.utils.FilterUtils;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineFilter;
import org.sleuthkit.datamodel.TimelineFilter.DataSourceFilter;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A tab shown in data source summary displaying information about a data
 * source's timeline events.
 */
@Messages({
    "TimelinePanel_earliestLabel_title=Earliest",
    "TimelinePanel_latestLabel_title=Latest",
    "TimlinePanel_last30DaysChart_title=Last 30 Days",
    "TimlinePanel_last30DaysChart_fileEvts_title=File Events",
    "TimlinePanel_last30DaysChart_artifactEvts_title=Artifact Events",})
public class TimelinePanel extends BaseDataSourceSummaryPanel {

    private static final Logger logger = Logger.getLogger(TimelinePanel.class.getName());
    private static final long serialVersionUID = 1L;
    private static final DateFormat EARLIEST_LATEST_FORMAT = getUtcFormat("MMM d, yyyy");
    private static final DateFormat CHART_FORMAT = getUtcFormat("MMM d");
    private static final int MOST_RECENT_DAYS_COUNT = 30;

    /**
     * Creates a DateFormat formatter that uses UTC for time zone.
     *
     * @param formatString The date format string.
     * @return The data format.
     */
    private static DateFormat getUtcFormat(String formatString) {
        return new SimpleDateFormat(formatString, Locale.getDefault());
    }

    // components displayed in the tab
    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();
    private final LoadableLabel earliestLabel = new LoadableLabel(Bundle.TimelinePanel_earliestLabel_title());
    private final LoadableLabel latestLabel = new LoadableLabel(Bundle.TimelinePanel_latestLabel_title());
    private final BarChartPanel last30DaysChart = new BarChartPanel(Bundle.TimlinePanel_last30DaysChart_title(), "", "");
    private final OpenTimelineAction openTimelineAction = new OpenTimelineAction();
    private final TimelineDataSourceUtils timelineUtils = TimelineDataSourceUtils.getInstance();

    // all loadable components on this tab
    private final List<LoadableComponent<?>> loadableComponents = Arrays.asList(earliestLabel, latestLabel, last30DaysChart);

    // actions to load data for this tab
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    public TimelinePanel() {
        this(new TimelineSummary());
    }

    /**
     * Creates new form PastCasesPanel
     */
    public TimelinePanel(TimelineSummary timelineData) {
        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> timelineData.getData(dataSource, MOST_RECENT_DAYS_COUNT),
                        (result) -> handleResult(result))
        );

        initComponents();
        setupChartClickListener();
    }

    /**
     * Formats a date using a DateFormat. In the event that the date is null,
     * returns a null string.
     *
     * @param date The date to format.
     * @param formatter The DateFormat to use to format the date.
     * @return The formatted string generated from the formatter or null if the
     * date is null.
     */
    private static String formatDate(Date date, DateFormat formatter) {
        return date == null ? null : formatter.format(date);
    }

    private static final Color FILE_EVT_COLOR = new Color(228, 22, 28);
    private static final Color ARTIFACT_EVT_COLOR = new Color(21, 227, 100);

    /**
     * Converts DailyActivityAmount data retrieved from TimelineSummary into
     * data to be displayed as a bar chart.
     *
     * @param recentDaysActivity The data retrieved from TimelineSummary.
     * @return The data to be displayed in the BarChart.
     */
    private List<BarChartSeries> parseChartData(List<DailyActivityAmount> recentDaysActivity) {
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
            String formattedDate = (i == 0 || i == recentDaysActivity.size() - 1)
                    ? formatDate(curItem.getDay(), CHART_FORMAT) : "";

            OrderedKey thisKey = new OrderedKey(formattedDate, i);
            fileEvtCounts.add(new BarChartItem(thisKey, fileAmt));
            artifactEvtCounts.add(new BarChartItem(thisKey, artifactAmt));
        }

        return Arrays.asList(
                new BarChartSeries(Bundle.TimlinePanel_last30DaysChart_fileEvts_title(), FILE_EVT_COLOR, fileEvtCounts),
                new BarChartSeries(Bundle.TimlinePanel_last30DaysChart_artifactEvts_title(), ARTIFACT_EVT_COLOR, artifactEvtCounts));
    }

    /**
     * Handles displaying the result for each displayable item in the
     * TimelinePanel by breaking the TimelineSummaryData result into its
     * constituent parts and then sending each data item to the pertinent
     * component.
     *
     * @param result The result to be displayed on this tab.
     */
    private void handleResult(DataFetchResult<TimelineSummaryData> result) {
        earliestLabel.showDataFetchResult(DataFetchResult.getSubResult(result, r -> formatDate(r.getMinDate(), EARLIEST_LATEST_FORMAT)));
        latestLabel.showDataFetchResult(DataFetchResult.getSubResult(result, r -> formatDate(r.getMaxDate(), EARLIEST_LATEST_FORMAT)));
        last30DaysChart.showDataFetchResult(DataFetchResult.getSubResult(result, r -> parseChartData(r.getMostRecentDaysActivity())));
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        onNewDataSource(dataFetchComponents, loadableComponents, dataSource);
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
        javax.swing.JLabel activityRangeLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel earliestLabelPanel = earliestLabel;
        javax.swing.JPanel latestLabelPanel = latestLabel;
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JPanel last30DaysPanel = last30DaysChart;
        clickToNavLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        mainContentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainContentPanel.setLayout(new javax.swing.BoxLayout(mainContentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        ingestRunningPanel.setAlignmentX(0.0F);
        ingestRunningPanel.setMaximumSize(new java.awt.Dimension(32767, 25));
        ingestRunningPanel.setMinimumSize(new java.awt.Dimension(10, 25));
        ingestRunningPanel.setPreferredSize(new java.awt.Dimension(10, 25));
        mainContentPanel.add(ingestRunningPanel);

        activityRangeLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(activityRangeLabel, org.openide.util.NbBundle.getMessage(TimelinePanel.class, "TimelinePanel.activityRangeLabel.text")); // NOI18N
        mainContentPanel.add(activityRangeLabel);
        activityRangeLabel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(TimelinePanel.class, "PastCasesPanel.notableFileLabel.text")); // NOI18N

        filler1.setAlignmentX(0.0F);
        mainContentPanel.add(filler1);

        earliestLabelPanel.setAlignmentX(0.0F);
        earliestLabelPanel.setMaximumSize(new java.awt.Dimension(32767, 20));
        earliestLabelPanel.setMinimumSize(new java.awt.Dimension(100, 20));
        earliestLabelPanel.setPreferredSize(new java.awt.Dimension(100, 20));
        mainContentPanel.add(earliestLabelPanel);

        latestLabelPanel.setAlignmentX(0.0F);
        latestLabelPanel.setMaximumSize(new java.awt.Dimension(32767, 20));
        latestLabelPanel.setMinimumSize(new java.awt.Dimension(100, 20));
        latestLabelPanel.setPreferredSize(new java.awt.Dimension(100, 20));
        mainContentPanel.add(latestLabelPanel);

        filler2.setAlignmentX(0.0F);
        mainContentPanel.add(filler2);

        last30DaysPanel.setAlignmentX(0.0F);
        last30DaysPanel.setMaximumSize(new java.awt.Dimension(600, 300));
        last30DaysPanel.setMinimumSize(new java.awt.Dimension(600, 300));
        last30DaysPanel.setPreferredSize(new java.awt.Dimension(600, 300));
        last30DaysPanel.setVerifyInputWhenFocusTarget(false);
        mainContentPanel.add(last30DaysPanel);

        org.openide.awt.Mnemonics.setLocalizedText(clickToNavLabel, org.openide.util.NbBundle.getMessage(TimelinePanel.class, "TimelinePanel.clickToNavLabel.text")); // NOI18N
        mainContentPanel.add(clickToNavLabel);

        filler5.setAlignmentX(0.0F);
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

    private void setupChartClickListener() {
        this.last30DaysChart.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.last30DaysChart.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                TimelinePanel.this.notifyParentClose();
                openTimelineAction.performAction();
                try {
                    TimeLineController controller = TimeLineModule.getController();
                    DataSource dataSource = getDataSource();
                    if (dataSource != null) {
                        controller.pushFilters(timelineUtils.getDataSourceFilterState(dataSource));
                    }
                } catch (NoCurrentCaseException | TskCoreException ex) {
                    logger.log(Level.WARNING, "Unable to open Timeline view", ex);
                }
            }
        });
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel clickToNavLabel;
    // End of variables declaration//GEN-END:variables
}
