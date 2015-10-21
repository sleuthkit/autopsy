/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.countsview;

import java.util.Arrays;
import java.util.MissingResourceException;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;

/**
 * Customized {@link StackedBarChart<String, Number>} used to display the event
 * counts in {@link CountsViewPane}
 */
final class EventCountsChart extends StackedBarChart<String, Number> implements TimeLineChart<String> {

    private ContextMenu chartContextMenu;

    @Override
    public ContextMenu getChartContextMenu() {
        return chartContextMenu;
    }

    private final TimeLineController controller;

    private IntervalSelector<? extends String> intervalSelector;

    /**
     * * the RangeDivisionInfo for the currently displayed time range, used to
     * correct the interval provided {@link  EventCountsChart#intervalSelector}
     * by padding the end with one 'period'
     */
    private RangeDivisionInfo rangeInfo;

    EventCountsChart(TimeLineController controller, CategoryAxis dateAxis, NumberAxis countAxis) {
        super(dateAxis, countAxis);
        this.controller = controller;
        //configure constant properties on axes and chart
        dateAxis.setAnimated(true);
        dateAxis.setLabel(null);
        dateAxis.setTickLabelsVisible(false);
        dateAxis.setTickLabelGap(0);

        countAxis.setLabel(NbBundle.getMessage(CountsViewPane.class, "CountsChartPane.numberOfEvents"));
        countAxis.setAutoRanging(false);
        countAxis.setLowerBound(0);
        countAxis.setAnimated(true);
        countAxis.setMinorTickCount(0);
        countAxis.setTickLabelFormatter(new IntegerOnlyStringConverter());

        setAlternativeRowFillVisible(true);
        setCategoryGap(2);
        setLegendVisible(false);
        setAnimated(true);
        setTitle(null);

        ChartDragHandler<String, EventCountsChart> chartDragHandler = new ChartDragHandler<>(this);
        setOnMousePressed(chartDragHandler);
        setOnMouseReleased(chartDragHandler);
        setOnMouseDragged(chartDragHandler);

        setOnMouseClicked(new MouseClickedHandler<>(this));

    }

    @Override
    public void clearIntervalSelector() {
        getChartChildren().remove(intervalSelector);
        intervalSelector = null;
    }

    @Override
    public ContextMenu getChartContextMenu(MouseEvent clickEvent) throws MissingResourceException {
        if (chartContextMenu != null) {
            chartContextMenu.hide();
        }

        chartContextMenu = ActionUtils.createContextMenu(
                Arrays.asList(
                        TimeLineChart.newZoomHistoyActionGroup(controller)));
        chartContextMenu.setAutoHide(true);
        return chartContextMenu;
    }

    @Override
    public TimeLineController getController() {
        return controller;
    }

    @Override
    public IntervalSelector<? extends String> getIntervalSelector() {
        return intervalSelector;
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends String> newIntervalSelector) {
        intervalSelector = newIntervalSelector;
        getChartChildren().add(getIntervalSelector());
    }

    @Override
    public CountsIntervalSelector newIntervalSelector() {
        return new CountsIntervalSelector(this);
    }

    /**
     * used by {@link CountsViewPane#BarClickHandler} to close the context menu
     * when the bar menu is requested
     *
     * @return the context menu for this chart
     */
    ContextMenu getContextMenu() {
        return chartContextMenu;
    }

    void setRangeInfo(RangeDivisionInfo rangeInfo) {
        this.rangeInfo = rangeInfo;
    }

    /**
     * StringConvereter used to 'format' vertical axis labels
     */
    private static class IntegerOnlyStringConverter extends StringConverter<Number> {

        @Override
        public String toString(Number n) {
            //suppress non-integer values
            return n.intValue() == n.doubleValue()
                    ? Integer.toString(n.intValue()) : "";
        }

        @Override
        public Number fromString(String string) {
            //this is unused but here for symmetry
            return Double.valueOf(string).intValue();
        }
    }

    /**
     * Interval Selector for the counts chart, adjusts interval based on
     * rangeInfo to include final period
     */
    final static private class CountsIntervalSelector extends IntervalSelector<String> {

        private final EventCountsChart countsChart;

        CountsIntervalSelector(EventCountsChart chart) {
            super(chart);
            this.countsChart = chart;
        }

        @Override
        protected String formatSpan(String date) {
            return date;
        }

        @Override
        protected Interval adjustInterval(Interval i) {
            //extend range to block bounderies (ie day, month, year)
            RangeDivisionInfo iInfo = RangeDivisionInfo.getRangeDivisionInfo(i);
            final long lowerBound = iInfo.getLowerBound();
            final long upperBound = iInfo.getUpperBound();
            final DateTime lowerDate = new DateTime(lowerBound, TimeLineController.getJodaTimeZone());
            final DateTime upperDate = new DateTime(upperBound, TimeLineController.getJodaTimeZone());
            //add extra block to end that gets cut of by conversion from string/category.
            return new Interval(lowerDate, upperDate.plus(countsChart.rangeInfo.getPeriodSize().getPeriod()));
        }

        @Override
        protected DateTime parseDateTime(String date) {
            return date == null ? new DateTime(countsChart.rangeInfo.getLowerBound()) : countsChart.rangeInfo.getTickFormatter().parseDateTime(date);
        }
    }

}
