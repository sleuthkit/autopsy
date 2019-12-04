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
import java.util.Optional;
import java.util.logging.Level;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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
import org.controlsfx.control.Notifications;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Seconds;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ColorUtilities;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.PromptDialogManager;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getColor;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getImagePath;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * Customized StackedBarChart<String, Number> used to display the event counts
 * in CountsViewPane.
 */
final class EventCountsChart extends StackedBarChart<String, Number> implements TimeLineChart<String> {

    private static final Logger logger = Logger.getLogger(EventCountsChart.class.getName());
    private static final Effect SELECTED_NODE_EFFECT = new Lighting();
    private ContextMenu chartContextMenu;

    private final TimeLineController controller;
    private final EventsModel filteredEvents;

    private IntervalSelector<? extends String> intervalSelector;

    final ObservableList<Node> selectedNodes;

    /**
     * the RangeDivisionInfo for the currently displayed time range, used to
     * correct the interval provided by intervalSelector by padding the end with
     * one 'period'
     */
    private RangeDivision rangeInfo;

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

    void setRangeInfo(RangeDivision rangeInfo) {
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
        TimelineEventType eventType = extraValue.getEventType();
        Interval interval = extraValue.getInterval();
        long count = extraValue.getRawCount();

        item.nodeProperty().addListener(observable -> {
            final Node node = item.getNode();
            if (node != null) {
                node.setStyle("-fx-border-width: 2; "
                              + " -fx-border-color: " + ColorUtilities.getRGBCode(getColor(eventType.getParent())) + "; "
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
            RangeDivision iInfo = RangeDivision.getRangeDivision(i, TimeLineController.getJodaTimeZone());
            final long lowerBound = iInfo.getLowerBound();
            final long upperBound = iInfo.getUpperBound();
            final DateTime lowerDate = new DateTime(lowerBound, TimeLineController.getJodaTimeZone());
            final DateTime upperDate = new DateTime(upperBound, TimeLineController.getJodaTimeZone());
            //add extra block to end that gets cut of by conversion from string/category.
            return new Interval(lowerDate, upperDate.plus(countsChart.rangeInfo.getPeriodSize().toUnitPeriod()));
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
     * (from the jfx thread) and the internally synchronized TimeLineController
     *
     * TODO: review for thread safety -jm
     */
    private class BarClickHandler implements EventHandler<MouseEvent> {

        private ContextMenu barContextMenu;

        private final Interval interval;

        private final TimelineEventType type;

        private final Node node;

        private final String startDateString;

        BarClickHandler(XYChart.Data<String, Number> data) {
            EventCountsChart.ExtraData extraData = (EventCountsChart.ExtraData) data.getExtraValue();
            this.interval = extraData.getInterval();
            this.type = extraData.getEventType();
            this.node = data.getNode();
            this.startDateString = data.getXValue();
        }

        @NbBundle.Messages({"Timeline.ui.countsview.menuItem.selectTimeRange=Select Time Range",
            "SelectIntervalAction.errorMessage=Error selecting interval."})
        class SelectIntervalAction extends Action {

            SelectIntervalAction() {
                super(Bundle.Timeline_ui_countsview_menuItem_selectTimeRange());
                setEventHandler(action -> {
                    try {
                        controller.selectTimeAndType(interval, TimelineEventType.ROOT_EVENT_TYPE);

                    } catch (TskCoreException ex) {
                        Notifications.create().owner(getScene().getWindow())
                                .text(Bundle.SelectIntervalAction_errorMessage())
                                .showError();
                        logger.log(Level.SEVERE, "Error selecting interval.", ex);
                    }
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

        @NbBundle.Messages({"Timeline.ui.countsview.menuItem.selectEventType=Select Event Type",
            "SelectTypeAction.errorMessage=Error selecting type."})
        class SelectTypeAction extends Action {

            SelectTypeAction() {
                super(Bundle.Timeline_ui_countsview_menuItem_selectEventType());
                setEventHandler(action -> {
                    try {
                        controller.selectTimeAndType(filteredEvents.getSpanningInterval(), type);

                    } catch (TskCoreException ex) {
                        Notifications.create().owner(getScene().getWindow())
                                .text(Bundle.SelectTypeAction_errorMessage())
                                .showError();
                        logger.log(Level.SEVERE, "Error selecting type.", ex);
                    }
                    selectedNodes.clear();
                    getData().stream().filter(series -> series.getName().equals(type.getDisplayName()))
                            .findFirst()
                            .ifPresent(series -> series.getData().forEach(data -> selectedNodes.add(data.getNode())));
                });
            }
        }

        @NbBundle.Messages({"Timeline.ui.countsview.menuItem.selectTimeandType=Select Time and Type",
            "SelectIntervalAndTypeAction.errorMessage=Error selecting interval and type."})
        class SelectIntervalAndTypeAction extends Action {

            SelectIntervalAndTypeAction() {
                super(Bundle.Timeline_ui_countsview_menuItem_selectTimeandType());
                setEventHandler(action -> {
                    try {
                        controller.selectTimeAndType(interval, type);

                    } catch (TskCoreException ex) {
                        Notifications.create().owner(getScene().getWindow())
                                .text(Bundle.SelectIntervalAndTypeAction_errorMessage())
                                .showError();
                        logger.log(Level.SEVERE, "Error selecting interval and type.", ex);
                    }
                    selectedNodes.setAll(node);
                });
            }
        }

        @NbBundle.Messages({"Timeline.ui.countsview.menuItem.zoomIntoTimeRange=Zoom into Time Range",
            "ZoomToIntervalAction.errorMessage=Error zooming to interval."})
        class ZoomToIntervalAction extends Action {

            ZoomToIntervalAction() {
                super(Bundle.Timeline_ui_countsview_menuItem_zoomIntoTimeRange());
                setEventHandler(action -> {
                    try {
                        if (interval.toDuration().isShorterThan(Seconds.ONE.toStandardDuration()) == false) {
                            controller.pushTimeRange(interval);
                        }
                    } catch (TskCoreException ex) {
                        Notifications.create().owner(getScene().getWindow())
                                .text(Bundle.ZoomToIntervalAction_errorMessage())
                                .showError();
                        logger.log(Level.SEVERE, "Error zooming to interval.", ex);
                    }
                });
            }
        }

        @Override
        @NbBundle.Messages({
            "CountsViewPane.detailSwitchMessage=There is no temporal resolution smaller than Seconds.\nWould you like to switch to the Details view instead?",
            "CountsViewPane.detailSwitchTitle=\"Switch to Details View?",
            "BarClickHandler.selectTimeAndType.errorMessage=Error selecting time and type.",
            "BarClickHandler_zoomIn_errorMessage=Error zooming in."})
        public void handle(final MouseEvent e) {
            e.consume();
            if (e.getClickCount() == 1) {     //single click => selection
                if (e.getButton().equals(MouseButton.PRIMARY)) {
                    try {
                        controller.selectTimeAndType(interval, type);
                    } catch (TskCoreException ex) {
                        Notifications.create().owner(getScene().getWindow())
                                .text(Bundle.BarClickHandler_selectTimeAndType_errorMessage())
                                .showError();
                        logger.log(Level.SEVERE, "Error selecting time and type.", ex);
                    }
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
                    try {
                        controller.pushTimeRange(interval);
                    } catch (TskCoreException ex) {
                        Notifications.create().owner(getScene().getWindow())
                                .text(Bundle.BarClickHandler_zoomIn_errorMessage())
                                .showError();
                        logger.log(Level.SEVERE, "Error zooming in.", ex);
                    }
                } else {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Bundle.CountsViewPane_detailSwitchMessage(), ButtonType.YES, ButtonType.NO);
                    alert.setTitle(Bundle.CountsViewPane_detailSwitchTitle());
                    PromptDialogManager.setDialogIcons(alert);

                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            controller.setViewMode(ViewMode.DETAIL);
                        }
                    });
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
        private final TimelineEventType eventType;
        private final long rawCount;

        ExtraData(Interval interval, TimelineEventType eventType, long rawCount) {
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

        public TimelineEventType getEventType() {
            return eventType;
        }
    }
}
