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
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.chart.Axis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.effect.Effect;
import static javafx.scene.input.KeyCode.DOWN;
import static javafx.scene.input.KeyCode.KP_DOWN;
import static javafx.scene.input.KeyCode.KP_UP;
import static javafx.scene.input.KeyCode.PAGE_DOWN;
import static javafx.scene.input.KeyCode.PAGE_UP;
import static javafx.scene.input.KeyCode.UP;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.action.Action;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 * Controller class for a {@link EventDetailsChart} based implementation of a
 * TimeLineView.
 *
 * This class listens to changes in the assigned {@link FilteredEventsModel} and
 * updates the internal {@link EventDetailsChart} to reflect the currently
 * requested events.
 *
 * Concurrency Policy: Access to the private members clusterChart, dateAxis,
 * EventTypeMap, and dataSets is all linked directly to the ClusterChart which
 * must only be manipulated on the JavaFx thread.
 */
public class DetailViewPane extends AbstractVisualizationPane<DateTime, EventStripe, EventBundleNodeBase<?, ?, ?>, EventDetailsChart> {

    private final static Logger LOGGER = Logger.getLogger(DetailViewPane.class.getName());

    private static final double LINE_SCROLL_PERCENTAGE = .10;
    private static final double PAGE_SCROLL_PERCENTAGE = .70;

    private final DateAxis dateAxis = new DateAxis();
    private final Axis<EventStripe> verticalAxis = new EventAxis();
    private final ScrollBar vertScrollBar = new ScrollBar();
    private final Region scrollBarSpacer = new Region();

    private MultipleSelectionModel<TreeItem<EventBundle<?>>> treeSelectionModel;
    private final ObservableList<EventBundleNodeBase<?, ?, ?>> highlightedNodes = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    //private access to barchart data
    private final Map<EventType, XYChart.Series<DateTime, EventStripe>> eventTypeToSeriesMap = new ConcurrentHashMap<>();

    public ObservableList<EventBundle<?>> getEventBundles() {
        return chart.getEventBundles();
    }

    public DetailViewPane(TimeLineController controller, Pane partPane, Pane contextPane, Region bottomLeftSpacer) {
        super(controller, partPane, contextPane, bottomLeftSpacer);
        //initialize chart;
        chart = new EventDetailsChart(controller, dateAxis, verticalAxis, selectedNodes);

        setChartClickHandler(); //can we push this into chart
        chart.setData(dataSeries);
        setCenter(chart);

        settingsNodes = new ArrayList<>(new DetailViewSettingsPane().getChildrenUnmodifiable());
        //bind layout fo axes and spacers
        dateAxis.setTickLabelGap(0);
        dateAxis.setAutoRanging(false);
        dateAxis.setTickLabelsVisible(false);
        dateAxis.getTickMarks().addListener((Observable observable) -> layoutDateLabels());
        dateAxis.getTickSpacing().addListener(observable -> layoutDateLabels());

        bottomLeftSpacer.minWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));
        bottomLeftSpacer.prefWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));
        bottomLeftSpacer.maxWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));

        scrollBarSpacer.minHeightProperty().bind(dateAxis.heightProperty());

        //configure scrollbar
        vertScrollBar.setOrientation(Orientation.VERTICAL);
        vertScrollBar.maxProperty().bind(chart.maxVScrollProperty().subtract(chart.heightProperty()));
        vertScrollBar.visibleAmountProperty().bind(chart.heightProperty());
        vertScrollBar.visibleProperty().bind(vertScrollBar.visibleAmountProperty().greaterThanOrEqualTo(0));
        VBox.setVgrow(vertScrollBar, Priority.ALWAYS);
        setRight(new VBox(vertScrollBar, scrollBarSpacer));

        //interpret scroll events to the scrollBar
        this.setOnScroll(scrollEvent ->
                vertScrollBar.valueProperty().set(clampScroll(vertScrollBar.getValue() - scrollEvent.getDeltaY())));

        //request focus for keyboard scrolling
        setOnMouseClicked(mouseEvent -> requestFocus());

        //interpret scroll related keys to scrollBar
        this.setOnKeyPressed((KeyEvent t) -> {
            switch (t.getCode()) {
                case PAGE_UP:
                    incrementScrollValue(-PAGE_SCROLL_PERCENTAGE);
                    t.consume();
                    break;
                case PAGE_DOWN:
                    incrementScrollValue(PAGE_SCROLL_PERCENTAGE);
                    t.consume();
                    break;
                case KP_UP:
                case UP:
                    incrementScrollValue(-LINE_SCROLL_PERCENTAGE);
                    t.consume();
                    break;
                case KP_DOWN:
                case DOWN:
                    incrementScrollValue(LINE_SCROLL_PERCENTAGE);
                    t.consume();
                    break;
            }
        });

        //scrollbar value change handler.  This forwards changes in scroll bar to chart
        this.vertScrollBar.valueProperty().addListener(observable -> chart.setVScroll(vertScrollBar.getValue()));

        //maintain highlighted effect on correct nodes
        highlightedNodes.addListener((ListChangeListener.Change<? extends EventBundleNodeBase<?, ?, ?>> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(node -> {
                    node.applyHighlightEffect(true);
                });
                change.getRemoved().forEach(node -> {
                    node.applyHighlightEffect(false);
                });
            }
        });

        selectedNodes.addListener((Observable observable) -> {
            highlightedNodes.clear();
            selectedNodes.stream().forEach((tn) -> {
                for (EventBundleNodeBase<?, ?, ?> n : chart.getNodes((EventBundleNodeBase<?, ?, ?> t) ->
                        t.getDescription().equals(tn.getDescription()))) {
                    highlightedNodes.add(n);
                }
            });
        });
    }

    private void incrementScrollValue(double factor) {
        vertScrollBar.valueProperty().set(clampScroll(vertScrollBar.getValue() + factor * chart.getHeight()));
    }

    private Double clampScroll(Double value) {
        return Math.max(0, Math.min(vertScrollBar.getMax() + 50, value));
    }

    public void setSelectionModel(MultipleSelectionModel<TreeItem<EventBundle<?>>> selectionModel) {
        this.treeSelectionModel = selectionModel;

        treeSelectionModel.getSelectedItems().addListener((Observable observable) -> {
            highlightedNodes.clear();
            for (TreeItem<EventBundle<?>> tn : treeSelectionModel.getSelectedItems()) {

                for (EventBundleNodeBase<?, ?, ?> n : chart.getNodes((EventBundleNodeBase<?, ?, ?> t) ->
                        t.getDescription().equals(tn.getValue().getDescription()))) {
                    highlightedNodes.add(n);
                }
            }
        });
    }

    @Override
    protected Boolean isTickBold(DateTime value) {
        return false;
    }

    @Override
    protected Axis<EventStripe> getYAxis() {
        return verticalAxis;
    }

    @Override
    protected Axis<DateTime> getXAxis() {
        return dateAxis;
    }

    @Override
    protected double getTickSpacing() {
        return dateAxis.getTickSpacing().get();
    }

    @Override
    protected String getTickMarkLabel(DateTime value) {
        return dateAxis.getTickMarkLabel(value);
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
//    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private XYChart.Series<DateTime, EventStripe> getSeries(final EventType et) {
        return eventTypeToSeriesMap.computeIfAbsent(et, (EventType t) -> {
            XYChart.Series<DateTime, EventStripe> series = new XYChart.Series<>();
            series.setName(et.getDisplayName());
//            Platform.runLater(() -> {
            dataSeries.add(series);
//            });

            return series;
        });
    }

    @Override
    protected Task<Boolean> getUpdateTask() {

        return new LoggedTask<Boolean>(NbBundle.getMessage(this.getClass(), "DetailViewPane.loggedTask.name"), true) {

            @Override
            protected Boolean call() throws Exception {
                if (isCancelled()) {
                    return null;
                }
//                else {
//                    Platform.runLater(new Runnable() {
//
//                        public void run() {
//                            ProgressIndicator progressIndicator = new ProgressIndicator(-1);
//                            progressIndicator.progressProperty().bind(progressProperty());
//                            progressIndicator.setOpacity(.5);
//                            setCenter(new StackPane(chart, progressIndicator));
//                            setCursor(Cursor.WAIT);
//                        }
//                    });
//                }

                updateProgress(-1, 1);
                updateMessage(NbBundle.getMessage(this.getClass(), "DetailViewPane.loggedTask.preparing"));

                final RangeDivisionInfo rangeInfo = RangeDivisionInfo.getRangeDivisionInfo(filteredEvents.timeRangeProperty().get());
                final long lowerBound = rangeInfo.getLowerBound();
                final long upperBound = rangeInfo.getUpperBound();

                updateMessage(NbBundle.getMessage(this.getClass(), "DetailViewPane.loggedTask.queryDb"));
                dataSeries.clear();
                Platform.runLater(new Runnable() {

                    public void run() {
                        if (isCancelled()) {
                            return;
                        }
                        dateAxis.setLowerBound(new DateTime(lowerBound, TimeLineController.getJodaTimeZone()));
                        dateAxis.setUpperBound(new DateTime(upperBound, TimeLineController.getJodaTimeZone()));
                        vertScrollBar.setValue(0);
                        eventTypeToSeriesMap.clear();
                    }
                });

                List<EventStripe> eventClusters = filteredEvents.getEventClusters();

                final int size = eventClusters.size();
                updateMessage(NbBundle.getMessage(this.getClass(), "DetailViewPane.loggedTask.updateUI"));
                for (int i = 0; i < size; i++) {
                    if (isCancelled()) {
                        break;
                    }
                    final EventStripe cluster = eventClusters.get(i);
                    updateProgress(i, size);
                    final XYChart.Data<DateTime, EventStripe> xyData = new BarChart.Data<>(new DateTime(cluster.getStartMillis()), cluster);

//                        Platform.runLater(() -> {
                    getSeries(cluster.getEventType()).getData().add(xyData);
//                        });
                }
                updateProgress(1, 1);
                return eventClusters.isEmpty() == false;
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                layoutDateLabels();
            }
        };
    }

    @Override

    protected Effect getSelectionEffect() {
        return null;
    }

    @Override
    protected void applySelectionEffect(EventBundleNodeBase<?, ?, ?> c1, Boolean selected) {
        c1.applySelectionEffect(selected);
    }

    private class DetailViewSettingsPane extends HBox {

        @FXML
        private RadioButton hiddenRadio;

        @FXML
        private RadioButton showRadio;

        @FXML
        private ToggleGroup descrVisibility;

        @FXML
        private RadioButton countsRadio;

        @FXML
        private CheckBox bandByTypeBox;

        @FXML
        private CheckBox oneEventPerRowBox;

        @FXML
        private CheckBox truncateAllBox;

        @FXML
        private Slider truncateWidthSlider;

        @FXML
        private Label truncateSliderLabel;

        @FXML
        private MenuButton advancedLayoutOptionsButtonLabel;

        @FXML
        private CustomMenuItem bandByTypeBoxMenuItem;

        @FXML
        private CustomMenuItem oneEventPerRowBoxMenuItem;

        @FXML
        private CustomMenuItem truncateAllBoxMenuItem;

        @FXML
        private CustomMenuItem truncateSliderLabelMenuItem;

        @FXML
        private CustomMenuItem showRadioMenuItem;

        @FXML
        private CustomMenuItem countsRadioMenuItem;

        @FXML
        private CustomMenuItem hiddenRadioMenuItem;

        @FXML
        private SeparatorMenuItem descVisibilitySeparatorMenuItem;

        DetailViewSettingsPane() {
            FXMLConstructor.construct(DetailViewSettingsPane.this, "DetailViewSettingsPane.fxml"); // NON-NLS
        }

        @FXML
        void initialize() {
            assert bandByTypeBox != null : "fx:id=\"bandByTypeBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert oneEventPerRowBox != null : "fx:id=\"oneEventPerRowBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert truncateAllBox != null : "fx:id=\"truncateAllBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert truncateWidthSlider != null : "fx:id=\"truncateAllSlider\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            bandByTypeBox.selectedProperty().bindBidirectional(chart.bandByTypeProperty());
            truncateAllBox.selectedProperty().bindBidirectional(chart.truncateAllProperty());
            oneEventPerRowBox.selectedProperty().bindBidirectional(chart.oneEventPerRowProperty());
            truncateSliderLabel.disableProperty().bind(truncateAllBox.selectedProperty().not());
            truncateSliderLabel.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.truncateSliderLabel.text"));
            final InvalidationListener sliderListener = o -> {
                if (truncateWidthSlider.isValueChanging() == false) {
                    chart.getTruncateWidth().set(truncateWidthSlider.getValue());
                }
            };
            truncateWidthSlider.valueProperty().addListener(sliderListener);
            truncateWidthSlider.valueChangingProperty().addListener(sliderListener);

            descrVisibility.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
                if (newToggle == countsRadio) {
                    chart.descrVisibilityProperty().set(DescriptionVisibility.COUNT_ONLY);
                } else if (newToggle == showRadio) {
                    chart.descrVisibilityProperty().set(DescriptionVisibility.SHOWN);
                } else if (newToggle == hiddenRadio) {
                    chart.descrVisibilityProperty().set(DescriptionVisibility.HIDDEN);
                }
            });

            advancedLayoutOptionsButtonLabel.setText(
                    NbBundle.getMessage(this.getClass(), "DetailViewPane.advancedLayoutOptionsButtonLabel.text"));
            bandByTypeBox.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.bandByTypeBox.text"));
            bandByTypeBoxMenuItem.setText(
                    NbBundle.getMessage(this.getClass(), "DetailViewPane.bandByTypeBoxMenuItem.text"));
            oneEventPerRowBox.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.oneEventPerRowBox.text"));
            oneEventPerRowBoxMenuItem.setText(
                    NbBundle.getMessage(this.getClass(), "DetailViewPane.oneEventPerRowBoxMenuItem.text"));
            truncateAllBox.setText(NbBundle.getMessage(this.getClass(), "DetailViewPan.truncateAllBox.text"));
            truncateAllBoxMenuItem.setText(
                    NbBundle.getMessage(this.getClass(), "DetailViewPan.truncateAllBoxMenuItem.text"));
            truncateSliderLabelMenuItem.setText(
                    NbBundle.getMessage(this.getClass(), "DetailViewPane.truncateSlideLabelMenuItem.text"));
            descVisibilitySeparatorMenuItem.setText(
                    NbBundle.getMessage(this.getClass(), "DetailViewPane.descVisSeparatorMenuItem.text"));
            showRadioMenuItem.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.showRadioMenuItem.text"));
            showRadio.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.showRadio.text"));
            countsRadioMenuItem.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.countsRadioMenuItem.text"));
            countsRadio.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.countsRadio.text"));
            hiddenRadioMenuItem.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.hiddenRadioMenuItem.text"));
            hiddenRadio.setText(NbBundle.getMessage(this.getClass(), "DetailViewPane.hiddenRadio.text"));
        }
    }

    public Action newUnhideDescriptionAction(String description, DescriptionLoD descriptionLoD) {
        return chart.new UnhideDescriptionAction(description, descriptionLoD);
    }

    public Action newHideDescriptionAction(String description, DescriptionLoD descriptionLoD) {
        return chart.new HideDescriptionAction(description, descriptionLoD);
    }
}
