/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.effect.Lighting;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javax.swing.JOptionPane;
import org.controlsfx.control.action.ActionGroup;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Seconds;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ColorUtilities;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.VisualizationMode;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.Forward;
import org.sleuthkit.autopsy.timeline.events.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.events.type.EventType;
import org.sleuthkit.autopsy.timeline.events.type.RootEventType;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualization;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;

/**
 * FXML Controller class for a {@link StackedBarChart<String,Number>} based
 * implementation of a {@link TimeLineView}.
 *
 * This class listens to changes in the assigned {@link FilteredEventsModel} and
 * updates the internal {@link StackedBarChart} to reflect the currently
 * requested events.
 *
 * This class captures input from the user in the form of mouse clicks on graph
 * bars, and forwards them to the assigned {@link TimeLineController} *
 *
 * Concurrency Policy: Access to the private members stackedBarChart, countAxis,
 * dateAxis, EventTypeMap, and dataSets affects the stackedBarChart so they all
 * must only be manipulated on the JavaFx thread (through {@link Platform#runLater(java.lang.Runnable)}
 *
 * {@link CountsChartPane#filteredEvents} should encapsulate all need
 * synchronization internally.
 *
 * TODO: refactor common code out of this class and ClusterChartPane into
 * AbstractChartView
 */
public class CountsViewPane extends AbstractVisualization<String, Number, Node, EventCountsChart> {

    private static final Effect SELECTED_NODE_EFFECT = new Lighting();

    private static final Logger LOGGER = Logger.getLogger(CountsViewPane.class.getName());

    private final NumberAxis countAxis = new NumberAxis();

    private final CategoryAxis dateAxis = new CategoryAxis(FXCollections.<String>observableArrayList());

    private final SimpleObjectProperty<ScaleType> scale = new SimpleObjectProperty<>(ScaleType.LOGARITHMIC);

    //private access to barchart data
    private final Map<EventType, XYChart.Series<String, Number>> eventTypeMap = new ConcurrentHashMap<>();

    @Override
    protected String getTickMarkLabel(String labelValueString) {
        return labelValueString;
    }

    @Override
    protected Boolean isTickBold(String value) {
        return dataSets.stream().flatMap((series) -> series.getData().stream())
                .anyMatch((data) -> data.getXValue().equals(value) && data.getYValue().intValue() > 0);
    }

    private ContextMenu getContextMenu() {

        ContextMenu chartContextMenu = ActionUtils.createContextMenu(Arrays.asList(new ActionGroup(
                NbBundle.getMessage(this.getClass(), "Timeline.ui.countsview.contextMenu.ActionGroup.zoomHistory.title"),
                new Back(controller), new Forward(controller))));
        chartContextMenu.setAutoHide(true);
        return chartContextMenu;
    }

    @Override
    protected Task<Boolean> getUpdateTask() {
        return new LoggedTask<Boolean>(NbBundle.getMessage(this.getClass(), "CountsViewPane.loggedTask.name"), true) {

            @Override
            protected Boolean call() throws Exception {
                if (isCancelled()) {
                    return null;
                }
                updateProgress(-1, 1);
                updateMessage(NbBundle.getMessage(this.getClass(), "CountsViewPane.loggedTask.prepUpdate"));
                Platform.runLater(() -> {
                    setCursor(Cursor.WAIT);
                });

                final RangeDivisionInfo rangeInfo = RangeDivisionInfo.getRangeDivisionInfo(filteredEvents.timeRange().get());
                chart.setRangeInfo(rangeInfo);
                //extend range to block bounderies (ie day, month, year)
                final long lowerBound = rangeInfo.getLowerBound();
                final long upperBound = rangeInfo.getUpperBound();
                final Interval timeRange = new Interval(new DateTime(lowerBound, TimeLineController.getJodaTimeZone()), new DateTime(upperBound, TimeLineController.getJodaTimeZone()));

                int max = 0;
                int p = 0; // progress counter

                //clear old data, and reset ranges and series
                Platform.runLater(() -> {
                    updateMessage(NbBundle.getMessage(this.getClass(), "CountsViewPane.loggedTask.resetUI"));
                    eventTypeMap.clear();
                    dataSets.clear();
                    dateAxis.getCategories().clear();

                    DateTime start = timeRange.getStart();
                    while (timeRange.contains(start)) {
                        //add bar/'category' label for the current interval
                        final String dateString = start.toString(rangeInfo.getTickFormatter());
                        dateAxis.getCategories().add(dateString);

                        //increment for next iteration
                        start = start.plus(rangeInfo.getPeriodSize().getPeriod());
                    }

                    //make all series to ensure they get created in consistent order
                    EventType.allTypes.forEach(CountsViewPane.this::getSeries);
                });

                DateTime start = timeRange.getStart();
                while (timeRange.contains(start)) {

                    final String dateString = start.toString(rangeInfo.getTickFormatter());
                    DateTime end = start.plus(rangeInfo.getPeriodSize().getPeriod());
                    final Interval interval = new Interval(start, end);

                    //query for current range
                    Map<EventType, Long> eventCounts = filteredEvents.getEventCounts(interval);

                    //increment for next iteration
                    start = end;

                    int dateMax = 0; //used in max tracking

                    //for each type add data to graph
                    for (final EventType et : eventCounts.keySet()) {
                        if (isCancelled()) {
                            return null;
                        }

                        final Long count = eventCounts.get(et);
                        final int fp = p++;
                        if (count > 0) {
                            final double adjustedCount = count == 0 ? 0 : scale.get().adjust(count);

                            dateMax += adjustedCount;
                            final XYChart.Data<String, Number> xyData = new BarChart.Data<>(dateString, adjustedCount);

                            xyData.nodeProperty().addListener((Observable o) -> {
                                final Node node = xyData.getNode();
                                if (node != null) {
                                    node.setStyle("-fx-border-width: 2; -fx-border-color: " + ColorUtilities.getRGBCode(et.getSuperType().getColor()) + "; -fx-bar-fill: " + ColorUtilities.getRGBCode(et.getColor())); // NON-NLS
                                    node.setCursor(Cursor.HAND);

                                    node.setOnMouseEntered((MouseEvent event) -> {
                                        //defer tooltip creation till needed, this had a surprisingly large impact on speed of loading the chart
                                        final Tooltip tooltip = new Tooltip(
                                                NbBundle.getMessage(this.getClass(), "CountsViewPane.tooltip.text",
                                                        count,
                                                        et.getDisplayName(),
                                                        dateString,
                                                        interval.getEnd().toString(
                                                                rangeInfo.getTickFormatter())));
                                        tooltip.setGraphic(new ImageView(et.getFXImage()));
                                        Tooltip.install(node, tooltip);
                                        node.setEffect(new DropShadow(10, et.getColor()));
                                    });
                                    node.setOnMouseExited((MouseEvent event) -> {
                                        if (selectedNodes.contains(node)) {
                                            node.setEffect(SELECTED_NODE_EFFECT);
                                        } else {
                                            node.setEffect(null);
                                        }
                                    });

                                    node.addEventHandler(MouseEvent.MOUSE_CLICKED, new BarClickHandler(node, dateString, interval, et));
                                }
                            });

                            max = Math.max(max, dateMax);

                            final double fmax = max;

                            Platform.runLater(() -> {
                                updateMessage(
                                        NbBundle.getMessage(this.getClass(), "CountsViewPane.loggedTask.updatingCounts"));
                                getSeries(et).getData().add(xyData);
                                if (scale.get().equals(ScaleType.LINEAR)) {
                                    countAxis.setTickUnit(Math.pow(10, Math.max(0, Math.floor(Math.log10(fmax)) - 1)));
                                } else {
                                    countAxis.setTickUnit(Double.MAX_VALUE);
                                }
                                countAxis.setUpperBound(1 + fmax * 1.2);
                                layoutDateLabels();
                                updateProgress(fp, rangeInfo.getPeriodsInRange());
                            });
                        } else {
                            final double fmax = max;

                            Platform.runLater(() -> {
                                updateMessage(
                                        NbBundle.getMessage(this.getClass(), "CountsViewPane.loggedTask.updatingCounts"));
                                updateProgress(fp, rangeInfo.getPeriodsInRange());
                            });
                        }
                    }
                }

                Platform.runLater(() -> {
                    updateMessage(NbBundle.getMessage(this.getClass(), "CountsViewPane.loggedTask.wrappingUp"));
                    updateProgress(1, 1);
                    layoutDateLabels();
                    setCursor(Cursor.NONE);
                });

                return max > 0;
            }
        };
    }

    public CountsViewPane(Pane partPane, Pane contextPane, Region spacer) {
        super(partPane, contextPane, spacer);
        chart = new EventCountsChart(dateAxis, countAxis);
        setChartClickHandler();
        chart.setData(dataSets);
        setCenter(chart);

        settingsNodes = new ArrayList<>(new CountsViewSettingsPane().getChildrenUnmodifiable());

        dateAxis.getTickMarks().addListener((Observable observable) -> {
            layoutDateLabels();
        });
        dateAxis.categorySpacingProperty().addListener((Observable observable) -> {
            layoutDateLabels();
        });
        dateAxis.getCategories().addListener((Observable observable) -> {
            layoutDateLabels();
        });

        spacer.minWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));
        spacer.prefWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));
        spacer.maxWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));

        scale.addListener(o -> {
            countAxis.tickLabelsVisibleProperty().bind(scale.isEqualTo(ScaleType.LINEAR));
            countAxis.tickMarkVisibleProperty().bind(scale.isEqualTo(ScaleType.LINEAR));
            countAxis.minorTickVisibleProperty().bind(scale.isEqualTo(ScaleType.LINEAR));
            update();
        });
    }

    @Override
    protected NumberAxis getYAxis() {
        return countAxis;
    }

    @Override
    protected CategoryAxis getXAxis() {
        return dateAxis;
    }

    @Override
    protected double getTickSpacing() {
        return dateAxis.getCategorySpacing();
    }

    @Override
    protected Effect getSelectionEffect() {
        return SELECTED_NODE_EFFECT;
    }

    @Override
    protected void applySelectionEffect(Node c1, Boolean applied) {
        if (applied) {
            c1.setEffect(getSelectionEffect());
        } else {
            c1.setEffect(null);
        }
    }

    /**
     * NOTE: Because this method modifies data directly used by the chart, this
     * method should only be called from JavaFX thread!
     *
     * @param et the EventType to get the series for
     *
     * @return a Series object to contain all the events with the given
     *         EventType
     */
    private XYChart.Series<String, Number> getSeries(final EventType et) {
        XYChart.Series<String, Number> series = eventTypeMap.get(et);
        if (series == null) {
            series = new XYChart.Series<>();
            series.setName(et.getDisplayName());
            eventTypeMap.put(et, series);

            dataSets.add(series);
        }
        return series;

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

        public BarClickHandler(Node node, String dateString, Interval countInterval, EventType type) {
            this.interval = countInterval;
            this.type = type;
            this.node = node;
            this.startDateString = dateString;
        }

        @Override
        public void handle(final MouseEvent e) {
            e.consume();
            if (e.getClickCount() == 1) {     //single click => selection
                if (e.getButton().equals(MouseButton.PRIMARY)) {
                    controller.selectTimeAndType(interval, type);
                    selectedNodes.setAll(node);
                } else if (e.getButton().equals(MouseButton.SECONDARY)) {
                    Platform.runLater(() -> {
                        chart.getContextMenu().hide();

                        if (barContextMenu == null) {
                            barContextMenu = new ContextMenu();
                            barContextMenu.setAutoHide(true);
                            barContextMenu.getItems().addAll(
                                    new MenuItem(NbBundle.getMessage(this.getClass(),
                                                    "Timeline.ui.countsview.menuItem.selectTimeRange")) {
                                        {
                                            setOnAction((ActionEvent t) -> {
                                                controller.selectTimeAndType(interval, RootEventType.getInstance());

                                                selectedNodes.clear();
                                                for (XYChart.Series<String, Number> s : dataSets) {
                                                    s.getData().forEach((XYChart.Data<String, Number> d) -> {
                                                        if (startDateString.contains(d.getXValue())) {
                                                            selectedNodes.add(d.getNode());
                                                        }
                                                    });
                                                }
                                            });
                                        }
                                    },
                                    new MenuItem(NbBundle.getMessage(this.getClass(),
                                                    "Timeline.ui.countsview.menuItem.selectEventType")) {
                                        {
                                            setOnAction((ActionEvent t) -> {
                                                controller.selectTimeAndType(filteredEvents.getSpanningInterval(), type);

                                                selectedNodes.clear();
                                                eventTypeMap.get(type).getData().forEach((d) -> {
                                                    selectedNodes.add(d.getNode());

                                                });
                                            });
                                        }
                                    },
                                    new MenuItem(NbBundle.getMessage(this.getClass(),
                                                    "Timeline.ui.countsview.menuItem.selectTimeandType")) {
                                        {
                                            setOnAction((ActionEvent t) -> {
                                                controller.selectTimeAndType(interval, type);
                                                selectedNodes.setAll(node);
                                            });
                                        }
                                    },
                                    new SeparatorMenuItem(),
                                    new MenuItem(NbBundle.getMessage(this.getClass(),
                                                    "Timeline.ui.countsview.menuItem.zoomIntoTimeRange")) {
                                        {
                                            setOnAction((ActionEvent t) -> {
                                                if (interval.toDuration().isShorterThan(Seconds.ONE.toStandardDuration()) == false) {
                                                    controller.pushTimeRange(interval);
                                                }
                                            });
                                        }
                                    });
                            barContextMenu.getItems().addAll(getContextMenu().getItems());
                        }

                        barContextMenu.show(node, e.getScreenX(), e.getScreenY());
                    });

                }
            } else if (e.getClickCount() >= 2) {  //double-click => zoom in time
                if (interval.toDuration().isLongerThan(Seconds.ONE.toStandardDuration())) {
                    controller.pushTimeRange(interval);
                } else {

                    int showConfirmDialog = JOptionPane.showConfirmDialog(null,
                            NbBundle.getMessage(CountsViewPane.class, "CountsViewPane.detailSwitchMessage"),
                            NbBundle.getMessage(CountsViewPane.class, "CountsViewPane.detailSwitchTitle"), JOptionPane.YES_NO_OPTION);
                    if (showConfirmDialog == JOptionPane.YES_OPTION) {
                        controller.setViewMode(VisualizationMode.DETAIL);
                    }

                    /*
                     * //I would like to use the JAvafx dialog, but it doesn't
                     * block the ui (because it is embeded in a TopComponent)
                     * -jm
                     *
                     * final Dialogs.CommandLink yes = new
                     * Dialogs.CommandLink("Yes", "switch to Details view");
                     * final Dialogs.CommandLink no = new
                     * Dialogs.CommandLink("No", "return to Counts view with a
                     * resolution of Seconds"); Action choice = Dialogs.create()
                     * .title("Switch to Details View?") .masthead("There is no
                     * temporal resolution smaller than Seconds.")
                     * .message("Would you like to switch to the Details view
                     * instead?") .showCommandLinks(Arrays.asList(yes, no));
                     *
                     * if (choice == yes) {
                     * controller.setViewMode(VisualizationMode.DETAIL); }
                     */
                }
            }
        }
    }

    private class CountsViewSettingsPane extends HBox {

        @FXML
        private RadioButton logRadio;

        @FXML
        private RadioButton linearRadio;

        @FXML
        private ToggleGroup scaleGroup;

        @FXML
        private Label scaleLabel;

        @FXML
        void initialize() {
            assert logRadio != null : "fx:id=\"logRadio\" was not injected: check your FXML file 'CountsViewSettingsPane.fxml'."; // NON-NLS
            assert linearRadio != null : "fx:id=\"linearRadio\" was not injected: check your FXML file 'CountsViewSettingsPane.fxml'."; // NON-NLS
            logRadio.setSelected(true);
            scaleGroup.selectedToggleProperty().addListener(observable -> {
                if (scaleGroup.getSelectedToggle() == linearRadio) {
                    scale.set(ScaleType.LINEAR);
                }
                if (scaleGroup.getSelectedToggle() == logRadio) {
                    scale.set(ScaleType.LOGARITHMIC);
                }
            });

            logRadio.setText(NbBundle.getMessage(this.getClass(), "CountsViewPane.logRadio.text"));
            linearRadio.setText(NbBundle.getMessage(this.getClass(), "CountsViewPane.linearRadio.text"));
            scaleLabel.setText(NbBundle.getMessage(this.getClass(), "CountsViewPane.scaleLabel.text"));
        }

        public CountsViewSettingsPane() {
            FXMLConstructor.construct(this, "CountsViewSettingsPane.fxml"); // NON-NLS
        }
    }

    private static enum ScaleType {

        LINEAR(t -> t.doubleValue()),
        LOGARITHMIC(t -> Math.log10(t) + 1);

        private final Function<Long, Double> func;

        ScaleType(Function<Long, Double> func) {
            this.func = func;
        }

        double adjust(Long c) {
            return func.apply(c);
        }
    }
}
