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
import javafx.scene.Cursor;
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
import javafx.scene.control.ToggleButton;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualization;
import org.sleuthkit.autopsy.timeline.ui.countsview.CountsViewPane;
import org.sleuthkit.autopsy.timeline.ui.detailview.tree.NavTreeNode;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;

/**
 * FXML Controller class for a {@link EventDetailChart} based implementation of
 * a TimeLineView.
 *
 * This class listens to changes in the assigned {@link FilteredEventsModel} and
 * updates the internal {@link EventDetailChart} to reflect the currently
 * requested events.
 *
 * This class captures input from the user in the form of mouse clicks on graph
 * bars, and forwards them to the assigned {@link TimeLineController}
 *
 * Concurrency Policy: Access to the private members clusterChart, dateAxis,
 * EventTypeMap, and dataSets is all linked directly to the ClusterChart which
 * must only be manipulated on the JavaFx thread (through {@link Platform#runLater(java.lang.Runnable)
 * }
 *
 * {@link CountsChartPane#filteredEvents} should encapsulate all needed
 * synchronization internally.
 *
 * TODO: refactor common code out of this class and CountsChartPane into
 * {@link AbstractVisualization}
 */
public class DetailViewPane extends AbstractVisualization<DateTime, EventCluster, DetailViewNode<?>, EventDetailChart> {

    private final static Logger LOGGER = Logger.getLogger(CountsViewPane.class.getName());

    private MultipleSelectionModel<TreeItem<NavTreeNode>> treeSelectionModel;

    //these three could be injected from fxml but it was causing npe's
    private final DateAxis dateAxis = new DateAxis();

    private final Axis<EventCluster> verticalAxis = new EventAxis();

    //private access to barchart data
    private final Map<EventType, XYChart.Series<DateTime, EventCluster>> eventTypeToSeriesMap = new ConcurrentHashMap<>();

    private final ScrollBar vertScrollBar = new ScrollBar();

    private final Region region = new Region();

    private final ObservableList<EventCluster> aggregatedEvents = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    private final ObservableList<DetailViewNode<?>> highlightedNodes = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    public ObservableList<EventCluster> getAggregatedEvents() {
        return aggregatedEvents;
    }

    public DetailViewPane(Pane partPane, Pane contextPane, Region spacer) {
        super(partPane, contextPane, spacer);
        chart = new EventDetailChart(dateAxis, verticalAxis, selectedNodes);
        setChartClickHandler();
        chart.setData(dataSets);
        setCenter(chart);

        chart.setPrefHeight(USE_COMPUTED_SIZE);

        settingsNodes = new ArrayList<>(new DetailViewSettingsPane().getChildrenUnmodifiable());

        vertScrollBar.setOrientation(Orientation.VERTICAL);
        VBox vBox = new VBox();
        VBox.setVgrow(vertScrollBar, Priority.ALWAYS);
        vBox.getChildren().add(vertScrollBar);
        vBox.getChildren().add(region);
        setRight(vBox);

        dateAxis.setAutoRanging(false);
        region.minHeightProperty().bind(dateAxis.heightProperty());
        vertScrollBar.visibleAmountProperty().bind(chart.heightProperty().multiply(100).divide(chart.getMaxVScroll()));
        requestLayout();

        highlightedNodes.addListener((ListChangeListener.Change<? extends DetailViewNode<?>> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(aeNode -> {
                    aeNode.applyHighlightEffect(true);
                });
                change.getRemoved().forEach(aeNode -> {
                    aeNode.applyHighlightEffect(false);
                });
            }
        });
        //request focus for keyboard scrolling
        setOnMouseClicked((MouseEvent t) -> {
            requestFocus();
        });

        //These scroll related handlers don't affect any other view or the model, so they are handled internally
        //mouse wheel scroll handler
        this.onScrollProperty().set((ScrollEvent t) -> {
            vertScrollBar.valueProperty().set(Math.max(0, Math.min(100, vertScrollBar.getValue() - t.getDeltaY() / 200.0)));
        });

        this.setOnKeyPressed((KeyEvent t) -> {
            switch (t.getCode()) {
                case PAGE_UP:
                    incrementScrollValue(-70);
                    break;
                case PAGE_DOWN:
                    incrementScrollValue(70);
                    break;
                case KP_UP:
                case UP:
                    incrementScrollValue(-10);
                    break;
                case KP_DOWN:
                case DOWN:
                    incrementScrollValue(10);
                    break;
            }
            t.consume();
        });

        //scrollbar handler
        this.vertScrollBar.valueProperty().addListener((o, oldValue, newValue) -> {
            chart.setVScroll(newValue.doubleValue() / 100.0);
        });
        spacer.minWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));
        spacer.prefWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));
        spacer.maxWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));

        dateAxis.setTickLabelsVisible(false);

        dateAxis.getTickMarks().addListener((Observable observable) -> {
            layoutDateLabels();
        });
        dateAxis.getTickSpacing().addListener((Observable observable) -> {
            layoutDateLabels();
        });

        dateAxis.setTickLabelGap(0);

        selectedNodes.addListener((Observable observable) -> {
            highlightedNodes.clear();
            selectedNodes.stream().forEach((tn) -> {
                for (DetailViewNode<?> n : chart.getNodes((DetailViewNode<?> t)
                        -> t.getDescription().equals(tn.getDescription()))) {
                    highlightedNodes.add(n);
                }
            });
        });

    }

    @Override
    public synchronized void setModel(FilteredEventsModel filteredEvents) {
        super.setModel(filteredEvents);
    }

    private void incrementScrollValue(int factor) {
        vertScrollBar.valueProperty().set(Math.max(0, Math.min(100, vertScrollBar.getValue() + factor * (chart.getHeight() / chart.getMaxVScroll().get()))));
    }

    public void setSelectionModel(MultipleSelectionModel<TreeItem<NavTreeNode>> selectionModel) {
        this.treeSelectionModel = selectionModel;

        treeSelectionModel.getSelectedItems().addListener((Observable observable) -> {
            highlightedNodes.clear();
            for (TreeItem<NavTreeNode> tn : treeSelectionModel.getSelectedItems()) {
                for (DetailViewNode<?> n : chart.getNodes((DetailViewNode<?> t)
                        -> t.getDescription().equals(tn.getValue().getDescription()))) {
                    highlightedNodes.add(n);
                }
            }
        });
    }

    public ObservableList<DescriptionFilter> getBundleFilters() {
        return chart.getBundleFilters();
    }

    @Override
    protected Boolean isTickBold(DateTime value) {
        return false;
    }

    @Override
    protected Axis<EventCluster> getYAxis() {
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
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private XYChart.Series<DateTime, EventCluster> getSeries(final EventType et) {
        return eventTypeToSeriesMap.computeIfAbsent(et, (EventType t) -> {
            XYChart.Series<DateTime, EventCluster> series = new XYChart.Series<>();
            series.setName(et.getDisplayName());
            dataSets.add(series);
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
                Platform.runLater(() -> {
                    if (isCancelled() == false) {
                        setCursor(Cursor.WAIT);
                    }
                });

                updateProgress(-1, 1);
                updateMessage(NbBundle.getMessage(this.getClass(), "DetailViewPane.loggedTask.preparing"));

                final RangeDivisionInfo rangeInfo = RangeDivisionInfo.getRangeDivisionInfo(filteredEvents.timeRangeProperty().get());
                final long lowerBound = rangeInfo.getLowerBound();
                final long upperBound = rangeInfo.getUpperBound();

                updateMessage(NbBundle.getMessage(this.getClass(), "DetailViewPane.loggedTask.queryDb"));
                aggregatedEvents.setAll(filteredEvents.getAggregatedEvents());

                Platform.runLater(() -> {
                    if (isCancelled()) {
                        return;
                    }
                    dateAxis.setLowerBound(new DateTime(lowerBound, TimeLineController.getJodaTimeZone()));
                    dateAxis.setUpperBound(new DateTime(upperBound, TimeLineController.getJodaTimeZone()));
                    vertScrollBar.setValue(0);
                    eventTypeToSeriesMap.clear();
                    dataSets.clear();
                });
                final int size = aggregatedEvents.size();
                int i = 0;
                for (final EventCluster e : aggregatedEvents) {
                    if (isCancelled()) {
                        break;
                    }
                    updateProgress(i++, size);
                    updateMessage(NbBundle.getMessage(this.getClass(), "DetailViewPane.loggedTask.updateUI"));
                    final XYChart.Data<DateTime, EventCluster> xyData = new BarChart.Data<>(new DateTime(e.getSpan().getStartMillis()), e);

                    Platform.runLater(() -> {
                        if (isCancelled() == false) {
                            getSeries(e.getEventType()).getData().add(xyData);
                        }
                    });
                }

                Platform.runLater(() -> {
                    setCursor(Cursor.NONE);
                    layoutDateLabels();
                    updateProgress(1, 1);
                });
                return aggregatedEvents.isEmpty() == false;
            }
        };
    }

    @Override
    protected Effect getSelectionEffect() {
        return null;
    }

    @Override
    protected void applySelectionEffect(DetailViewNode<?> c1, Boolean selected) {
        chart.applySelectionEffect(c1, selected);
    }

    private class DetailViewSettingsPane extends HBox {

        @FXML
        private ToggleButton testToggle;

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
            FXMLConstructor.construct(this, "DetailViewSettingsPane.fxml"); // NON-NLS
        }

        @FXML
        void initialize() {
            assert bandByTypeBox != null : "fx:id=\"bandByTypeBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert oneEventPerRowBox != null : "fx:id=\"oneEventPerRowBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert truncateAllBox != null : "fx:id=\"truncateAllBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert truncateWidthSlider != null : "fx:id=\"truncateAllSlider\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            testToggle.selectedProperty().bindBidirectional(chart.alternateLayoutProperty());
            testToggle.selectedProperty().addListener((Observable observable) -> {
                filteredEvents.refresh();
            });
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
                    chart.getDescrVisibility().set(DescriptionVisibility.COUNT_ONLY);
                } else if (newToggle == showRadio) {
                    chart.getDescrVisibility().set(DescriptionVisibility.SHOWN);
                } else if (newToggle == hiddenRadio) {
                    chart.getDescrVisibility().set(DescriptionVisibility.HIDDEN);
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

}
