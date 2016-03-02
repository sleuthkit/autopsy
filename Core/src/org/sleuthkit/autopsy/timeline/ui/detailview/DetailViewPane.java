/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.MissingResourceException;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import org.sleuthkit.autopsy.timeline.ui.ContextMenuProvider;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailsChartLane.HideDescriptionAction;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailsChartLane.UnhideDescriptionAction;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 * Controller class for a {@link PrimaryDetailsChart} based implementation of a
 * TimeLineView.
 *
 * This class listens to changes in the assigned {@link FilteredEventsModel} and
 * updates the internal {@link PrimaryDetailsChart} to reflect the currently
 * requested events.
 *
 * Concurrency Policy: Access to the private members clusterChart, dateAxis,
 * EventTypeMap, and dataSets is all linked directly to the ClusterChart which
 * must only be manipulated on the JavaFx thread.
 */
public class DetailViewPane extends AbstractVisualizationPane<DateTime, EventStripe, EventNodeBase<?>, PrimaryDetailsChart> implements ContextMenuProvider<DateTime> {

    private final static Logger LOGGER = Logger.getLogger(DetailViewPane.class.getName());

    private final DateAxis detailsChartDateAxis = new DateAxis();
    private final DateAxis pinnedDateAxis = new DateAxis();
    private final Axis<EventStripe> verticalAxis = new EventAxis<>("All Events");

    private MultipleSelectionModel<TreeItem<TimeLineEvent>> treeSelectionModel;
    private final ObservableList<EventNodeBase<?>> highlightedNodes = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    private final ScrollingLaneWrapper mainView;
    private final ScrollingLaneWrapper pinnedView;
    private final DetailViewLayoutSettings layoutSettings;
    private final PinnedEventsChart pinnedChart;
    private final MasterDetailPane masterDetailPane;
    private double dividerPosition = .1;
    private static final int MIN_PINNED_LANE_HEIGHT = 50;
    private ContextMenu contextMenu;
    private IntervalSelector<? extends DateTime> intervalSelector;
    private final Pane rootPane;

    public ObservableList<EventStripe> getEventStripes() {
        return chart.getEventStripes();
    }

    private static class DetailIntervalSelector extends IntervalSelector<DateTime> {

        DetailIntervalSelector(ContextMenuProvider<DateTime> chart) {
            super(chart);
        }

        @Override
        protected String formatSpan(DateTime date) {
            return date.toString(TimeLineController.getZonedFormatter());
        }

        @Override
        protected Interval adjustInterval(Interval i) {
            return i;
        }

        @Override
        protected DateTime parseDateTime(DateTime date) {
            return date;
        }
    }

    @Override
    public ContextMenu getContextMenu() {
        return contextMenu;
    }

    @Override
    public ContextMenu getChartContextMenu(MouseEvent mouseEvent) throws MissingResourceException {
        if (contextMenu != null) {
            contextMenu.hide();
        }

        contextMenu = ActionUtils.createContextMenu(Arrays.asList(new PlaceMarkerAction(mouseEvent),
                TimeLineChart.newZoomHistoyActionGroup(controller)));
        contextMenu.setAutoHide(true);
        return contextMenu;
    }

    DetailViewLayoutSettings getLayoutSettings() {
        return layoutSettings;
    }

    @Override
    protected void resetData() {
        for (XYChart.Series<DateTime, EventStripe> s : dataSeries) {
            s.getData().forEach(chart::removeDataItem);
            s.getData().clear();
        }

        mainView.reset();
        pinnedView.reset();
    }

    public DetailViewPane(TimeLineController controller, Pane partPane, Pane contextPane, Region bottomLeftSpacer) {
        super(controller, partPane, contextPane, bottomLeftSpacer);
        layoutSettings = new DetailViewLayoutSettings();

        //initialize chart;
        chart = new PrimaryDetailsChart(this, detailsChartDateAxis, verticalAxis);
        chart.setData(dataSeries);
        setChartClickHandler(); //can we push this into chart

        mainView = new ScrollingLaneWrapper(chart);
        pinnedChart = new PinnedEventsChart(this, pinnedDateAxis, new EventAxis<>("Pinned Events"));
        pinnedView = new ScrollingLaneWrapper(pinnedChart);
        pinnedChart.setMinHeight(MIN_PINNED_LANE_HEIGHT);
        pinnedView.setMinHeight(MIN_PINNED_LANE_HEIGHT);

        masterDetailPane = new MasterDetailPane(Side.TOP, mainView, pinnedView, false);
        masterDetailPane.setDividerPosition(dividerPosition);
        masterDetailPane.prefHeightProperty().bind(heightProperty());
        masterDetailPane.prefWidthProperty().bind(widthProperty());
        rootPane = new Pane(masterDetailPane);
        setCenter(rootPane);

        settingsNodes = new ArrayList<>(new DetailViewSettingsPane().getChildrenUnmodifiable());
        //bind layout fo axes and spacers
        detailsChartDateAxis.getTickMarks().addListener((Observable observable) -> layoutDateLabels());
        detailsChartDateAxis.getTickSpacing().addListener(observable -> layoutDateLabels());

        verticalAxis.setAutoRanging(false); //prevent XYChart.updateAxisRange() from accessing dataSeries on JFX thread causing ConcurrentModificationException
        bottomLeftSpacer.minWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));
        bottomLeftSpacer.prefWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));
        bottomLeftSpacer.maxWidthProperty().bind(verticalAxis.widthProperty().add(verticalAxis.tickLengthProperty()));

        //maintain highlighted effect on correct nodes
        highlightedNodes.addListener((ListChangeListener.Change<? extends EventNodeBase<?>> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(eventNode -> eventNode.applyHighlightEffect());
                change.getRemoved().forEach(eventNode -> eventNode.clearHighlightEffect());
            }
        });

        selectedNodes.addListener((Observable observable) -> {
            highlightedNodes.clear();
            for (EventNodeBase<?> selectedNode : selectedNodes) {
                highlightedNodes.add(selectedNode);
            }
            controller.selectEventIDs(selectedNodes.stream()
                    .flatMap(detailNode -> detailNode.getEventIDs().stream())
                    .collect(Collectors.toList()));
        });

        filteredEvents.zoomParametersProperty().addListener(o -> {
            clearIntervalSelector();
            selectedNodes.clear();
            controller.selectEventIDs(Collections.emptyList());
        });

        TimeLineChart.MouseClickedHandler<DateTime, DetailViewPane> mouseClickedHandler = new TimeLineChart.MouseClickedHandler<>(this);
        TimeLineChart.ChartDragHandler<DateTime, DetailViewPane> chartDragHandler = new TimeLineChart.ChartDragHandler<>(this);
        configureMouseListeners(chart, mouseClickedHandler, chartDragHandler);
        configureMouseListeners(pinnedChart, mouseClickedHandler, chartDragHandler);

    }

    @Override
    public void clearIntervalSelector() {
        rootPane.getChildren().remove(intervalSelector);
        intervalSelector = null;
    }

    @Override
    public IntervalSelector<DateTime> newIntervalSelector() {
        return new DetailIntervalSelector(this);
    }

    @Override
    public IntervalSelector<? extends DateTime> getIntervalSelector() {
        return intervalSelector;
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends DateTime> newIntervalSelector) {
        intervalSelector = newIntervalSelector;
        rootPane.getChildren().add(getIntervalSelector());
    }

    public void setSelectionModel(MultipleSelectionModel<TreeItem<TimeLineEvent>> selectionModel) {
        this.treeSelectionModel = selectionModel;

        treeSelectionModel.getSelectedItems().addListener((Observable observable) -> {
            highlightedNodes.clear();
            for (TreeItem<TimeLineEvent> tn : treeSelectionModel.getSelectedItems()) {
                String description = tn.getValue().getDescription();
                for (EventNodeBase<?> n : chart.getNodes(eventNode -> eventNode.hasDescription(description))) {
                    highlightedNodes.add(n);
                }
                for (EventNodeBase<?> n : pinnedChart.getNodes(eventNode -> eventNode.hasDescription(description))) {
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
    public Axis<DateTime> getXAxis() {
        return detailsChartDateAxis;
    }

    @Override
    protected double getTickSpacing() {
        return detailsChartDateAxis.getTickSpacing().get();
    }

    @Override
    protected String getTickMarkLabel(DateTime value) {
        return detailsChartDateAxis.getTickMarkLabel(value);
    }

    @Override
    protected Task<Boolean> getUpdateTask() {
        return new DetailsUpdateTask();
    }

    @Override
    protected Effect getSelectionEffect() {
        return null;
    }

    @Override
    protected void applySelectionEffect(EventNodeBase<?> c1, Boolean selected) {
        c1.applySelectionEffect(selected);

    }

    DateTime getDateTimeForPosition(double layoutX) {
        return chart.getDateTimeForPosition(layoutX);
    }

    void clearGuideLine(GuideLine guideLine) {
        rootPane.getChildren().remove(guideLine);
        guideLine = null;
    }

    /**
     *
     * @param chartLane           the value of chartLane
     * @param mouseClickedHandler the value of mouseClickedHandler
     * @param chartDragHandler1   the value of chartDragHandler1
     */
    private void configureMouseListeners(final DetailsChartLane<?> chartLane, final TimeLineChart.MouseClickedHandler<DateTime, DetailViewPane> mouseClickedHandler, final TimeLineChart.ChartDragHandler<DateTime, DetailViewPane> chartDragHandler) {
        chartLane.setOnMousePressed(chartDragHandler);
        chartLane.setOnMouseReleased(chartDragHandler);
        chartLane.setOnMouseDragged(chartDragHandler);
        chartLane.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
    }
    private static final Image MARKER = new Image("/org/sleuthkit/autopsy/timeline/images/marker.png", 16, 16, true, true, true); //NON-NLS

    class PlaceMarkerAction extends Action {

        private GuideLine guideLine;

        @NbBundle.Messages({"EventDetailChart.chartContextMenu.placeMarker.name=Place Marker"})
        PlaceMarkerAction(MouseEvent clickEvent) {
            super(Bundle.EventDetailChart_chartContextMenu_placeMarker_name());

            setGraphic(new ImageView(MARKER)); // NON-NLS
            setEventHandler(actionEvent -> {
                if (guideLine == null) {
                    guideLine = new GuideLine(DetailViewPane.this);
                    guideLine.relocate(sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                    addGuideLine(guideLine);

                } else {
                    guideLine.relocate(sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                }
            });
        }

    }

    private void addGuideLine(GuideLine guideLine) {
        rootPane.getChildren().add(guideLine);
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

        @FXML
        private ToggleButton pinnedEventsToggle;

        DetailViewSettingsPane() {
            FXMLConstructor.construct(DetailViewSettingsPane.this, "DetailViewSettingsPane.fxml"); // NON-NLS
        }

        @FXML
        void initialize() {
            assert bandByTypeBox != null : "fx:id=\"bandByTypeBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert oneEventPerRowBox != null : "fx:id=\"oneEventPerRowBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert truncateAllBox != null : "fx:id=\"truncateAllBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            assert truncateWidthSlider != null : "fx:id=\"truncateAllSlider\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; // NON-NLS
            bandByTypeBox.selectedProperty().bindBidirectional(layoutSettings.bandByTypeProperty());
            truncateAllBox.selectedProperty().bindBidirectional(layoutSettings.truncateAllProperty());
            oneEventPerRowBox.selectedProperty().bindBidirectional(layoutSettings.oneEventPerRowProperty());
            truncateSliderLabel.disableProperty().bind(truncateAllBox.selectedProperty().not());
            truncateSliderLabel.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.truncateSliderLabel.text"));
            final InvalidationListener sliderListener = o -> {
                if (truncateWidthSlider.isValueChanging() == false) {
                    layoutSettings.truncateWidthProperty().set(truncateWidthSlider.getValue());
                }
            };
            truncateWidthSlider.valueProperty().addListener(sliderListener);
            truncateWidthSlider.valueChangingProperty().addListener(sliderListener);

            descrVisibility.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
                if (newToggle == countsRadio) {
                    layoutSettings.descrVisibilityProperty().set(DescriptionVisibility.COUNT_ONLY);
                } else if (newToggle == showRadio) {
                    layoutSettings.descrVisibilityProperty().set(DescriptionVisibility.SHOWN);
                } else if (newToggle == hiddenRadio) {
                    layoutSettings.descrVisibilityProperty().set(DescriptionVisibility.HIDDEN);
                }
            });

            advancedLayoutOptionsButtonLabel.setText(
                    NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.advancedLayoutOptionsButtonLabel.text"));
            bandByTypeBox.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.bandByTypeBox.text"));
            bandByTypeBoxMenuItem.setText(
                    NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.bandByTypeBoxMenuItem.text"));
            oneEventPerRowBox.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.oneEventPerRowBox.text"));
            oneEventPerRowBoxMenuItem.setText(
                    NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.oneEventPerRowBoxMenuItem.text"));
            truncateAllBox.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPan.truncateAllBox.text"));
            truncateAllBoxMenuItem.setText(
                    NbBundle.getMessage(DetailViewPane.class, "DetailViewPan.truncateAllBoxMenuItem.text"));
            truncateSliderLabelMenuItem.setText(
                    NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.truncateSlideLabelMenuItem.text"));
            descVisibilitySeparatorMenuItem.setText(
                    NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.descVisSeparatorMenuItem.text"));
            showRadioMenuItem.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.showRadioMenuItem.text"));
            showRadio.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.showRadio.text"));
            countsRadioMenuItem.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.countsRadioMenuItem.text"));
            countsRadio.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.countsRadio.text"));
            hiddenRadioMenuItem.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.hiddenRadioMenuItem.text"));
            hiddenRadio.setText(NbBundle.getMessage(DetailViewPane.class, "DetailViewPane.hiddenRadio.text"));

            pinnedEventsToggle.selectedProperty().addListener(observable -> {
                boolean selected = pinnedEventsToggle.isSelected();
                if (selected == false) {
                    dividerPosition = masterDetailPane.getDividerPosition();
                }
                pinnedView.maxHeightProperty().unbind();;
                masterDetailPane.setShowDetailNode(selected);
                if (selected) {
                    pinnedView.setMinHeight(MIN_PINNED_LANE_HEIGHT);
                    pinnedView.maxHeightProperty().bind(pinnedChart.maxVScrollProperty().add(30));
                    masterDetailPane.setDividerPosition(dividerPosition);
                }
            });

            controller.getPinnedEvents().addListener((SetChangeListener.Change<? extends TimeLineEvent> change) -> {
                if (change.getSet().size() == 0) {
                    pinnedEventsToggle.setSelected(false);
                } else {
                    pinnedEventsToggle.setSelected(true);
                }
            });
        }

    }

    public Action newUnhideDescriptionAction(String description, DescriptionLoD descriptionLoD) {
        return new UnhideDescriptionAction(description, descriptionLoD, chart);
    }

    public Action newHideDescriptionAction(String description, DescriptionLoD descriptionLoD) {
        return new HideDescriptionAction(description, descriptionLoD, chart);

    }

    @NbBundle.Messages({
        "DetailViewPane.loggedTask.queryDb=Retreiving event data",
        "DetailViewPane.loggedTask.name=Updating Details View",
        "DetailViewPane.loggedTask.updateUI=Populating visualization",
        "DetailViewPane.loggedTask.continueButton=Continue",
        "DetailViewPane.loggedTask.backButton=Back (Cancel)",
        "# {0} - number of events",
        "DetailViewPane.loggedTask.prompt=You are about to show details for {0} events.  This might be very slow or even crash Autopsy.\n\nDo you want to continue?"})
    private class DetailsUpdateTask extends VisualizationUpdateTask<Interval> {

        DetailsUpdateTask() {
            super(Bundle.DetailViewPane_loggedTask_name(), true);
        }

        @Override
        protected Boolean call() throws Exception {
            super.call();
            if (isCancelled()) {
                return null;
            }

            resetChart(getTimeRange());

            updateMessage(Bundle.DetailViewPane_loggedTask_queryDb());
            List<EventStripe> eventStripes = filteredEvents.getEventStripes();
            if (eventStripes.size() > 2000) {
                Task<ButtonType> task = new Task<ButtonType>() {

                    @Override
                    protected ButtonType call() throws Exception {
                        ButtonType ContinueButtonType = new ButtonType(Bundle.DetailViewPane_loggedTask_continueButton(), ButtonBar.ButtonData.OK_DONE);
                        ButtonType back = new ButtonType(Bundle.DetailViewPane_loggedTask_backButton(), ButtonBar.ButtonData.CANCEL_CLOSE);

                        Alert alert = new Alert(Alert.AlertType.WARNING, Bundle.DetailViewPane_loggedTask_prompt(eventStripes.size()), ContinueButtonType, back);
                        alert.setHeaderText("");
                        alert.initModality(Modality.APPLICATION_MODAL);
                        alert.initOwner(getScene().getWindow());
                        ButtonType orElse = alert.showAndWait().orElse(back);
                        if (orElse == back) {
                            DetailsUpdateTask.this.cancel();
                        }
                        return orElse;
                    }
                };
                Platform.runLater(task);
                ButtonType get = task.get();
            }
            updateMessage(Bundle.DetailViewPane_loggedTask_updateUI());
            final int size = eventStripes.size();
            for (int i = 0; i < size; i++) {
                if (isCancelled()) {
                    return null;
                }
                updateProgress(i, size);
                final EventStripe cluster = eventStripes.get(i);
                final XYChart.Data<DateTime, EventStripe> dataItem = new XYChart.Data<>(new DateTime(cluster.getStartMillis()), cluster);
                getSeries(cluster.getEventType()).getData().add(dataItem);
                chart.addDataItem(dataItem);
            }

            return eventStripes.isEmpty() == false;
        }

        @Override
        protected void cancelled() {
            super.cancelled();
            controller.retreat();
        }

        @Override
        protected void setDateAxisValues(Interval timeRange) {
            detailsChartDateAxis.setRange(timeRange, true);
            pinnedDateAxis.setRange(timeRange, true);
        }
    }
}
