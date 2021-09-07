/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineDataSourceUtils;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary.DailyActivityAmount;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary.TimelineSummaryData;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartPanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartSeries.OrderedKey;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartSeries.BarChartItem;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartSeries;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableLabel;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineModule;
import org.sleuthkit.datamodel.DataSource;
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
    "TimlinePanel_last30DaysChart_artifactEvts_title=Result Events",})
public class TimelinePanel extends BaseDataSourceSummaryPanel {

    private static final Logger logger = Logger.getLogger(TimelinePanel.class.getName());
    private static final long serialVersionUID = 1L;

    private static final String EARLIEST_LATEST_FORMAT_STR = "MMM d, yyyy";
    private static final DateFormat EARLIEST_LATEST_FORMAT = TimelineSummary.getUtcFormat(EARLIEST_LATEST_FORMAT_STR);
    private static final DateFormat CHART_FORMAT = TimelineSummary.getUtcFormat("MMM d, yyyy");
    private static final int MOST_RECENT_DAYS_COUNT = 30;

    // components displayed in the tab
    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();
    private final LoadableLabel earliestLabel = new LoadableLabel(Bundle.TimelinePanel_earliestLabel_title());
    private final LoadableLabel latestLabel = new LoadableLabel(Bundle.TimelinePanel_latestLabel_title());
    private final BarChartPanel last30DaysChart = new BarChartPanel(Bundle.TimlinePanel_last30DaysChart_title(), "", "");
    private final TimelineDataSourceUtils timelineUtils = TimelineDataSourceUtils.getInstance();

    // all loadable components on this tab
    private final List<LoadableComponent<?>> loadableComponents = Arrays.asList(earliestLabel, latestLabel, last30DaysChart);

    private final DataFetcher<DataSource, TimelineSummaryData> dataFetcher;

    // actions to load data for this tab
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    public TimelinePanel() {
        this(new TimelineSummaryGetter());
    }

    /**
     * Creates new form PastCasesPanel
     */
    public TimelinePanel(TimelineSummaryGetter timelineData) {
        super(timelineData);

        dataFetcher = (dataSource) -> timelineData.getData(dataSource, MOST_RECENT_DAYS_COUNT);

        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                new DataFetchWorker.DataFetchComponents<>(dataFetcher, (result) -> handleResult(result)));

        initComponents();
    }

    private static final Color FILE_EVT_COLOR = new Color(228, 22, 28);
    private static final Color ARTIFACT_EVT_COLOR = new Color(21, 227, 100);

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
    private List<BarChartSeries> parseChartData(List<DailyActivityAmount> recentDaysActivity, boolean showIntermediateDates) {
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

    private final Object timelineBtnLock = new Object();
    private TimelineSummaryData curTimelineData = null;

    /**
     * Handles displaying the result for each displayable item in the
     * TimelinePanel by breaking the TimelineSummaryData result into its
     * constituent parts and then sending each data item to the pertinent
     * component.
     *
     * @param result The result to be displayed on this tab.
     */
    private void handleResult(DataFetchResult<TimelineSummaryData> result) {
        earliestLabel.showDataFetchResult(DataFetchResult.getSubResult(result, r -> TimelineSummary.formatDate(r.getMinDate(), EARLIEST_LATEST_FORMAT)));
        latestLabel.showDataFetchResult(DataFetchResult.getSubResult(result, r -> TimelineSummary.formatDate(r.getMaxDate(), EARLIEST_LATEST_FORMAT)));
        last30DaysChart.showDataFetchResult(DataFetchResult.getSubResult(result, r -> parseChartData(r.getMostRecentDaysActivity(), false)));

        if (result != null
                && result.getResultType() == DataFetchResult.ResultType.SUCCESS
                && result.getData() != null) {

            synchronized (this.timelineBtnLock) {
                this.curTimelineData = result.getData();
                this.viewInTimelineBtn.setEnabled(true);
            }
        } else {
            synchronized (this.timelineBtnLock) {
                this.viewInTimelineBtn.setEnabled(false);
            }
        }
    }

    /**
     * Action that occurs when 'View in Timeline' button is pressed.
     */
    private void openFilteredChart() {
        DataSource dataSource = null;
        Date minDate = null;
        Date maxDate = null;

        // get date from current timelineData if that data exists.
        synchronized (this.timelineBtnLock) {
            if (curTimelineData == null) {
                return;
            }

            dataSource = curTimelineData.getDataSource();
            if (CollectionUtils.isNotEmpty(curTimelineData.getMostRecentDaysActivity())) {
                minDate = curTimelineData.getMostRecentDaysActivity().get(0).getDay();
                maxDate = curTimelineData.getMostRecentDaysActivity().get(curTimelineData.getMostRecentDaysActivity().size() - 1).getDay();
                // set outer bound to end of day instead of beginning
                if (maxDate != null) {
                    maxDate = new Date(maxDate.getTime() + 1000 * 60 * 60 * 24);
                }
            }
        }

        openFilteredChart(dataSource, minDate, maxDate);
    }

    /**
     * Action that occurs when 'View in Timeline' button is pressed.
     *
     * @param dataSource The data source to filter to.
     * @param minDate    The min date for the zoom of the window.
     * @param maxDate    The max date for the zoom of the window.
     */
    private void openFilteredChart(DataSource dataSource, Date minDate, Date maxDate) {
        OpenTimelineAction openTimelineAction = CallableSystemAction.get(OpenTimelineAction.class);
        if (openTimelineAction == null) {
            logger.log(Level.WARNING, "No OpenTimelineAction provided by CallableSystemAction; taking no redirect action.");
        }

        // notify dialog (if in dialog) should close.
        TimelinePanel.this.notifyParentClose();

        Interval timeSpan = null;

        try {
            final TimeLineController controller = TimeLineModule.getController();

            if (dataSource != null) {
                controller.pushFilters(timelineUtils.getDataSourceFilterState(dataSource));
            }

            if (minDate != null && maxDate != null) {
                timeSpan = new Interval(new DateTime(minDate), new DateTime(maxDate));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to view time range in Timeline view", ex);
        }

        try {
            openTimelineAction.showTimeline(timeSpan);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "An unexpected exception occurred while opening the timeline.", ex);
        }
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
        viewInTimelineBtn = new javax.swing.JButton();
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

        org.openide.awt.Mnemonics.setLocalizedText(viewInTimelineBtn, org.openide.util.NbBundle.getMessage(TimelinePanel.class, "TimelinePanel.viewInTimelineBtn.text")); // NOI18N
        viewInTimelineBtn.setEnabled(false);
        viewInTimelineBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewInTimelineBtnActionPerformed(evt);
            }
        });
        mainContentPanel.add(viewInTimelineBtn);

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

    private void viewInTimelineBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewInTimelineBtnActionPerformed
        openFilteredChart();
    }//GEN-LAST:event_viewInTimelineBtnActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton viewInTimelineBtn;
    // End of variables declaration//GEN-END:variables

}
