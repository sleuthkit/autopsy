/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.effect.Lighting;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;
import javax.swing.JOptionPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Seconds;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.ColorUtilities;
import org.sleuthkit.autopsy.timeline.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getColor;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getImagePath;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * Customized StackedBarChart<String, Number> used to display the event counts
 * in CountsViewPane.
 */
final class EventCountsChart extends StackedBarChart<String, Number> implements TimeLineChart<String> {

    private static final Effect SELECTED_NODE_EFFECT = new Lighting();
    private ContextMenu chartContextMenu;

    private final TimeLineController controller;
    private final FilteredEventsModel filteredEvents;

    private IntervalSelector<? extends String> intervalSelector;

    final ObservableList<Node> selectedNodes;

    /**
     * the RangeDivisionInfo for the currently displayed time range, used to
     * correct the interval provided by intervalSelector by padding the end with
     * one 'period'
     */
    private RangeDivisionInfo rangeInfo;

    EventCountsChart(TimeLineController controller, CategoryAxis dateAxis, NumberAxis countAxis, ObservableList<Node> selectedNodes) {
        super(dateAxis, countAxis);
        this.controller = controller;
        this.filteredEvents = controller.getEventsModel();

        //configure constant properties on axes and chart
        dateAxis.setAnimated(true);
        dateAxis.setLabel(null);
        dateAxis.setTickLabelsVisible(false);
        dateAxis.setTickLabelGap(0);

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

        this.selectedNodes = selectedNodes;

        getController().getEventsModel().timeRangeProperty().addListener(o -> {
            clearIntervalSelector();
        });
    }

    @Override
    public void clearContextMenu() {
        chartContextMenu = null;
    }

    @Override
    public ContextMenu getContextMenu(MouseEvent clickEvent) {
        if (chartContextMenu != null) {
            chartContextMenu.hide();
        }

        chartContextMenu = ActionUtils.createContextMenu(
                Arrays.asList(TimeLineChart.newZoomHistoyActionGroup(controller)));
        chartContextMenu.setAutoHide(true);
        return chartContextMenu;
    }

    @Override
    public TimeLineController getController() {
        return controller;
    }

    @Override
    public void clearIntervalSelector() {
        getChartChildren().remove(intervalSelector);
        intervalSelector = null;
    }

    @Override
    public IntervalSelector<? extends String> getIntervalSelector() {
        return intervalSelector;
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends String> newIntervalSelector) {
        intervalSelector = newIntervalSelector;
        //Add a listener that sizes the interval selector to its preferred size.
        intervalSelector.prefHeightProperty().addListener(observable -> newIntervalSelector.autosize());
        getChartChildren().add(getIntervalSelector());
    }

    @Override
    public CountsIntervalSelector newIntervalSelector() {
        return new CountsIntervalSelector(this);
    }

    @Override
    public ObservableList<Node> getSelectedNodes() {
        return selectedNodes;
    }

    void setRangeInfo(RangeDivisionInfo rangeInfo) {
        this.rangeInfo = rangeInfo;
    }

    Effect getSelectionEffect() {
        return SELECTED_NODE_EFFECT;
    }

    /**
     * Add the bar click handler,tooltip, border styling and hover effect to the
     * node generated by StackedBarChart.
     *
     * @param series
     * @param itemIndex
     * @param item
     */
    @NbBundle.Messages({
        "# {0} - count",
        "# {1} - event type displayname",
        "# {2} - start date time",
        "# {3} - end date time",
        "CountsViewPane.tooltip.text={0} {1} events\nbetween {2}\nand     {3}"})
    @Override
    protected void dataItemAdded(Series<String, Number> series, int itemIndex, Data<String, Number> item) {
        ExtraData extraValue = (ExtraData) item.getExtraValue();
        EventType eventType = extraValue.getEventType();
        Interval interval = extraValue.getInterval();
        long count = extraValue.getRawCount();

        item.nodeProperty().addListener(observable -> {
            final Node node = item.getNode();
            if (node != null) {
                node.setStyle("-fx-border-width: 2; "
                              + " -fx-border-color: " + ColorUtilities.getRGBCode(getColor(eventType.getSuperType())) + "; "
                              + " -fx-bar-fill: " + ColorUtilities.getRGBCode(getColor(eventType))); // NON-NLS
                node.setCursor(Cursor.HAND);

                final Tooltip tooltip = new Tooltip(Bundle.CountsViewPane_tooltip_text(
                        count, eventType.getDisplayName(),
                        item.getXValue(),
                        interval.getEnd().toString(rangeInfo.getTickFormatter())));
                tooltip.setGraphic(new ImageView(getImagePath(eventType)));
                Tooltip.install(node, tooltip);

                node.setOnMouseEntered(mouseEntered -> node.setEffect(new DropShadow(10, getColor(eventType))));
                node.setOnMouseExited(mouseExited -> node.setEffect(selectedNodes.contains(node) ? SELECTED_NODE_EFFECT : null));
                node.setOnMouseClicked(new BarClickHandler(item));
            }
        });
        super.dataItemAdded(series, itemIndex, item); //To change body of generated methods, choose Tools | Templates.
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
            RangeDivisionInfo iInfo = RangeDivisionInfo.getRangeDivisionInfo(i, TimeLineController.getJodaTimeZone());
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

    /**
     * EventHandler for click events on nodes representing a bar(segment) in the
     * stacked bar chart.
     *
     * Concurrency Policy: This only accesses immutable state or javafx nodes
     * (from the jfx thread) and the internally synchronized
     * {@link TimeLineController}
     *
     * TODO: review for thread safety -jm
     */
    private class BarClickHandler implements EventHandler<MouseEvent> {

        private ContextMenu barContextMenu;

        private final Interval interval;

        private final EventType type;

        private final Node node;

        private final String startDateString;

        BarClickHandler(XYChart.Data<String, Number> data) {
            EventCountsChart.ExtraData extraData = (EventCountsChart.ExtraData) data.getExtraValue();
            this.interval = extraData.getInterval();
            this.type = extraData.getEventType();
            this.node = data.getNode();
            this.startDateString = data.getXValue();
        }

        @NbBundle.Messages({"Timeline.ui.countsview.menuItem.selectTimeRange=Select Time Range"})
        class SelectIntervalAction extends Action {

            SelectIntervalAction() {
                super(Bundle.Timeline_ui_countsview_menuItem_selectTimeRange());
                setEventHandler(action -> {
                    controller.selectTimeAndType(interval, EventType.ROOT_EVEN_TYPE);
                    selectedNodes.clear();
                    for (XYChart.Series<String, Number> s : getData()) {
                        s.getData().forEach((XYChart.Data<String, Number> d) -> {
                            if (startDateString.contains(d.getXValue())) {
                                selectedNodes.add(d.getNode());
                            }
                        });
                    }
                });
            }
        }

        @NbBundle.Messages({"Timeline.ui.countsview.menuItem.selectEventType=Select Event Type"})
        class SelectTypeAction extends Action {

            SelectTypeAction() {
                super(Bundle.Timeline_ui_countsview_menuItem_selectEventType());
                setEventHandler(action -> {
                    controller.selectTimeAndType(filteredEvents.getSpanningInterval(), type);
                    selectedNodes.clear();
                    getData().stream().filter(series -> series.getName().equals(type.getDisplayName()))
                            .findFirst()
                            .ifPresent(series -> series.getData().forEach(data -> selectedNodes.add(data.getNode())));
                });
            }
        }

        @NbBundle.Messages({"Timeline.ui.countsview.menuItem.selectTimeandType=Select Time and Type"})
        class SelectIntervalAndTypeAction extends Action {

            SelectIntervalAndTypeAction() {
                super(Bundle.Timeline_ui_countsview_menuItem_selectTimeandType());
                setEventHandler(action -> {
                    controller.selectTimeAndType(interval, type);
                    selectedNodes.setAll(node);
                });
            }
        }

        @NbBundle.Messages({"Timeline.ui.countsview.menuItem.zoomIntoTimeRange=Zoom into Time Range"})
        class ZoomToIntervalAction extends Action {

            ZoomToIntervalAction() {
                super(Bundle.Timeline_ui_countsview_menuItem_zoomIntoTimeRange());
                setEventHandler(action -> {
                    if (interval.toDuration().isShorterThan(Seconds.ONE.toStandardDuration()) == false) {
                        controller.pushTimeRange(interval);
                    }
                });
            }
        }

        @Override
        @NbBundle.Messages({
            "CountsViewPane.detailSwitchMessage=There is no temporal resolution smaller than Seconds.\nWould you like to switch to the Details view instead?",
            "CountsViewPane.detailSwitchTitle=\"Switch to Details View?"})
        public void handle(final MouseEvent e) {
            e.consume();
            if (e.getClickCount() == 1) {     //single click => selection
                if (e.getButton().equals(MouseButton.PRIMARY)) {
                    controller.selectTimeAndType(interval, type);
                    selectedNodes.setAll(node);
                } else if (e.getButton().equals(MouseButton.SECONDARY)) {
                    getContextMenu(e).hide();

                    if (barContextMenu == null) {
                        barContextMenu = new ContextMenu();
                        barContextMenu.setAutoHide(true);
                        barContextMenu.getItems().addAll(
                                ActionUtils.createMenuItem(new SelectIntervalAction()),
                                ActionUtils.createMenuItem(new SelectTypeAction()),
                                ActionUtils.createMenuItem(new SelectIntervalAndTypeAction()),
                                new SeparatorMenuItem(),
                                ActionUtils.createMenuItem(new ZoomToIntervalAction()));

                        barContextMenu.getItems().addAll(getContextMenu(e).getItems());
                    }

                    barContextMenu.show(node, e.getScreenX(), e.getScreenY());

                }
            } else if (e.getClickCount() >= 2) {  //double-click => zoom in time
                if (interval.toDuration().isLongerThan(Seconds.ONE.toStandardDuration())) {
                    controller.pushTimeRange(interval);
                } else {

                    int showConfirmDialog = JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
                            Bundle.CountsViewPane_detailSwitchMessage(),
                            Bundle.CountsViewPane_detailSwitchTitle(), JOptionPane.YES_NO_OPTION);
                    if (showConfirmDialog == JOptionPane.YES_OPTION) {
                        controller.setViewMode(ViewMode.DETAIL);
                    }
                }
            }
        }
    }

    /**
     * Encapsulate extra data stuffed into each {@link Data} item to give click
     * handler and tooltip access to more info.
     */
    static class ExtraData {

        private final Interval interval;
        private final EventType eventType;
        private final long rawCount;

        ExtraData(Interval interval, EventType eventType, long rawCount) {
            this.interval = interval;
            this.eventType = eventType;
            this.rawCount = rawCount;
        }

        public long getRawCount() {
            return rawCount;
        }

        public Interval getInterval() {
            return interval;
        }

        public EventType getEventType() {
            return eventType;
        }

    }
}
