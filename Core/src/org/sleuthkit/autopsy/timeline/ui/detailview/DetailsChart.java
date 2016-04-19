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

import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.function.Predicate;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Side;
import javafx.scene.chart.Axis;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.python.google.common.collect.Iterables;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 * A TimeLineChart that implements the visual aspects of the DetailView
 */
final class DetailsChart extends Control implements TimeLineChart<DateTime> {

    ///chart axes
    private final DateAxis detailsChartDateAxis;
    private final DateAxis pinnedDateAxis;
    private final Axis<EventStripe> verticalAxis;

    /**
     * property that holds the interval selector if one is active
     */
    private final SimpleObjectProperty<IntervalSelector<? extends DateTime>> intervalSelector = new SimpleObjectProperty<>();

    /**
     * Predicate used to determine if a EventNode should be highlighted. Can be
     * a combination of conditions such as: be in the selectedNodes list OR have
     * a particular description, but it must include be in the selectedNodes
     * (selectedNodes::contains).
     */
    private final SimpleObjectProperty<Predicate<EventNodeBase<?>>> highlightPredicate = new SimpleObjectProperty<>((x) -> false);

    /**
     * an ObservableList of the Nodes that are selected in this chart.
     */
    private final ObservableList<EventNodeBase<?>> selectedNodes;

    /**
     * an ObservableList representing all the events in the tree as a flat list
     * of events whose roots are in the eventStripes lists
     *
     */
    private final ObservableList<TimeLineEvent> nestedEvents = FXCollections.observableArrayList();

    /**
     * Aggregates all the settings related to the layout of this chart as one
     * object.
     */
    private final DetailsChartLayoutSettings layoutSettings;

    /**
     * The main controller object for this instance of the Timeline UI.
     */
    private final TimeLineController controller;

    /**
     * an ObservableList of root event stripes to display in the chart. Must
     * only be modified on the JFX Thread.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<EventStripe> rootEventStripes = FXCollections.observableArrayList();

    DetailsChart(TimeLineController controller, DateAxis detailsChartDateAxis, DateAxis pinnedDateAxis, Axis<EventStripe> verticalAxis, ObservableList<EventNodeBase<?>> selectedNodes) {
        this.controller = controller;
        this.layoutSettings = new DetailsChartLayoutSettings(controller);
        this.detailsChartDateAxis = detailsChartDateAxis;
        this.verticalAxis = verticalAxis;
        this.pinnedDateAxis = pinnedDateAxis;
        this.selectedNodes = selectedNodes;

        FilteredEventsModel eventsModel = getController().getEventsModel();
        /*
         * if the time range is changed, clear the guide line and the interval
         * selector, since they may not be in view any more.
         */
        eventsModel.timeRangeProperty().addListener(o -> clearTimeBasedUIElements());

        //if the view paramaters change, clear the selection
        eventsModel.zoomParametersProperty().addListener(o -> getSelectedNodes().clear());
    }

    /**
     * Get the DateTime represented by the given x-position in this chart.
     *
     *
     * @param xPos the x-position to get the DataTime for
     *
     * @return the DateTime represented by the given x-position in this chart.
     */
    DateTime getDateTimeForPosition(double xPos) {
        return getXAxis().getValueForDisplay(getXAxis().parentToLocal(xPos, 0).getX());
    }

    /**
     * Add an EventStripe to the list of root stripes.
     *
     * @param stripe the EventStripe to add.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void addStripe(EventStripe stripe) {
        rootEventStripes.add(stripe);
        nestedEvents.add(stripe);
    }

    /**
     *
     * Remove the given GuideLine from this chart.
     *
     * @param guideLine the GuideLine to remove
     */
    void clearGuideLine(GuideLine guideLine) {
        guideLines.remove(guideLine);
    }

    @Override
    public ObservableList<EventNodeBase<?>> getSelectedNodes() {
        return selectedNodes;
    }

    /**
     * Get the DetailsCharLayoutSettings for this chart.
     *
     * @return the DetailsCharLayoutSettings for this chart.
     */
    DetailsChartLayoutSettings getLayoutSettings() {
        return layoutSettings;
    }

    /**
     *
     * Set the Predicate used to determine if a EventNode should be highlighted.
     * Can be a combination of conditions such as: be in the selectedNodes list
     * OR have a particular description, but it must include be in the
     * selectedNodes (selectedNodes::contains).
     *
     * @param highlightPredicate the Predicate used to determine which nodes to
     *                           highlight
     */
    void setHighlightPredicate(Predicate<EventNodeBase<?>> highlightPredicate) {
        this.highlightPredicate.set(highlightPredicate);
    }

    /**
     * Remove all the events from this chart.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void reset() {
        rootEventStripes.clear();
        nestedEvents.clear();
    }

    /**
     * Get the tree of event stripes flattened into a list
     */
    public ObservableList<TimeLineEvent> getAllNestedEvents() {
        return nestedEvents;
    }

    /**
     * Clear any time based UI elements (GuideLines, IntervalSelector,...) from
     * this chart.
     */
    private void clearTimeBasedUIElements() {
        guideLines.clear();
        clearIntervalSelector();
    }

    /**
     * ObservableSet of GuieLines displayed in this chart
     */
    private final ObservableSet<GuideLine> guideLines = FXCollections.observableSet();

    @Override
    public void clearIntervalSelector() {
        intervalSelector.set(null);
    }

    @Override
    public IntervalSelector<DateTime> newIntervalSelector() {
        return new DetailIntervalSelector(this);
    }

    @Override
    public IntervalSelector<? extends DateTime> getIntervalSelector() {
        return intervalSelector.get();
    }

    private SimpleObjectProperty<IntervalSelector<? extends DateTime>> intervalSelector() {
        return intervalSelector;
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends DateTime> newIntervalSelector) {
        intervalSelector.set(newIntervalSelector);
    }

    @Override
    public Axis<DateTime> getXAxis() {
        return detailsChartDateAxis;
    }

    @Override
    public TimeLineController getController() {
        return controller;
    }

    @Override
    public void clearContextMenu() {
        setContextMenu(null);
    }

    @Override
    public ContextMenu getContextMenu(MouseEvent mouseEvent) throws MissingResourceException {
        //get the current context menu and hide it if it is not null
        ContextMenu contextMenu = getContextMenu();
        if (contextMenu != null) {
            contextMenu.hide();
        }

        //make and assign a new context menu based on the given mouseEvent
        setContextMenu(ActionUtils.createContextMenu(Arrays.asList(
                new PlaceMarkerAction(this, mouseEvent),
                ActionUtils.ACTION_SEPARATOR,
                TimeLineChart.newZoomHistoyActionGroup(getController())
        )));
        return getContextMenu();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DetailsChartSkin(this);
    }

    /**
     * Get the ObservableList of root EventStripes.
     *
     * @return the ObservableList of root EventStripes.
     */
    ObservableList<EventStripe> getRootEventStripes() {
        return rootEventStripes;
    }

    /**
     * Implementation of IntervalSelector for use with a DetailsChart
     */
    private static class DetailIntervalSelector extends IntervalSelector<DateTime> {

        /**
         * Constructor
         *
         * @param chart the chart this IntervalSelector belongs to.
         */
        DetailIntervalSelector(IntervalSelector.IntervalSelectorProvider<DateTime> chart) {
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

    /**
     * Action that places a GuideLine at the location clicked by the user.
     */
    static private class PlaceMarkerAction extends Action {

        private static final Image MARKER = new Image("/org/sleuthkit/autopsy/timeline/images/marker.png", 16, 16, true, true, true); //NON-NLS
        private GuideLine guideLine;

        @NbBundle.Messages({"PlaceMArkerAction.name=Place Marker"})
        PlaceMarkerAction(DetailsChart chart, MouseEvent clickEvent) {
            super(Bundle.PlaceMArkerAction_name());

            setGraphic(new ImageView(MARKER)); // NON-NLS
            setEventHandler(actionEvent -> {
                if (guideLine == null) {
                    guideLine = new GuideLine(chart);
                    guideLine.relocate(chart.sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                    chart.guideLines.add(guideLine);

                } else {
                    guideLine.relocate(chart.sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                }
            });
        }
    }

    @NbBundle.Messages({"HideDescriptionAction.displayName=Hide",
        "HideDescriptionAction.displayMsg=Hide this group from the details view."})
    static class HideDescriptionAction extends Action {

        static final Image HIDE = new Image("/org/sleuthkit/autopsy/timeline/images/eye--minus.png"); // NON-NLS

        HideDescriptionAction(String description, DescriptionLoD descriptionLoD, DetailsChart chart) {
            super(Bundle.HideDescriptionAction_displayName());
            setLongText(Bundle.HideDescriptionAction_displayMsg());
            setGraphic(new ImageView(HIDE));
            setEventHandler((ActionEvent t) -> {
                final DescriptionFilter testFilter = new DescriptionFilter(
                        descriptionLoD,
                        description,
                        DescriptionFilter.FilterMode.EXCLUDE);

                DescriptionFilter descriptionFilter = chart.getController().getQuickHideFilters().stream()
                        .filter(testFilter::equals)
                        .findFirst().orElseGet(() -> {
                            testFilter.selectedProperty().addListener(observable -> chart.requestLayout());
                            chart.getController().getQuickHideFilters().add(testFilter);
                            return testFilter;
                        });
                descriptionFilter.setSelected(true);
            });
        }
    }

    @NbBundle.Messages({"UnhideDescriptionAction.displayName=Unhide"})
    static class UnhideDescriptionAction extends Action {

        static final Image SHOW = new Image("/org/sleuthkit/autopsy/timeline/images/eye--plus.png"); // NON-NLS

        UnhideDescriptionAction(String description, DescriptionLoD descriptionLoD, DetailsChart chart) {
            super(Bundle.UnhideDescriptionAction_displayName());
            setGraphic(new ImageView(SHOW));
            setEventHandler((ActionEvent t) ->
                    chart.getController().getQuickHideFilters().stream()
                    .filter(descriptionFilter -> descriptionFilter.getDescriptionLoD().equals(descriptionLoD)
                            && descriptionFilter.getDescription().equals(description))
                    .forEach(descriptionfilter -> descriptionfilter.setSelected(false))
            );
        }
    }

    static private class DetailsChartSkin extends SkinBase<DetailsChart> {

        private static final int MIN_PINNED_LANE_HEIGHT = 50;

        private final PrimaryDetailsChartLane primaryLane;
        private final ScrollingLaneWrapper mainView;
        private final PinnedEventsChartLane pinnedLane;
        private final ScrollingLaneWrapper pinnedView;
        private final MasterDetailPane masterDetailPane;
        private final Pane rootPane;

        private double dividerPosition = .1;

        private IntervalSelector<? extends DateTime> intervalSelector;

        @NbBundle.Messages("DetailViewPane.pinnedLaneLabel.text=Pinned Events")
        DetailsChartSkin(DetailsChart chart) {
            super(chart);
            //initialize chart;
            primaryLane = new PrimaryDetailsChartLane(chart, getSkinnable().detailsChartDateAxis, getSkinnable().verticalAxis);

            mainView = new ScrollingLaneWrapper(primaryLane);

            pinnedLane = new PinnedEventsChartLane(chart, getSkinnable().pinnedDateAxis, new EventAxis<>(Bundle.DetailViewPane_pinnedLaneLabel_text()));
            pinnedView = new ScrollingLaneWrapper(pinnedLane);
            pinnedLane.setMinHeight(MIN_PINNED_LANE_HEIGHT);
            pinnedLane.maxVScrollProperty().addListener((Observable observable) -> syncPinnedHeight());
            syncPinnedHeight();

            masterDetailPane = new MasterDetailPane(Side.TOP, mainView, pinnedView, false);
            masterDetailPane.setDividerPosition(dividerPosition);
            masterDetailPane.prefHeightProperty().bind(getSkinnable().heightProperty());
            masterDetailPane.prefWidthProperty().bind(getSkinnable().widthProperty());

            rootPane = new Pane(masterDetailPane);
            getChildren().add(rootPane);

            //maintain highlighted effect on correct nodes
            getSkinnable().highlightPredicate.addListener((observable, oldPredicate, newPredicate) -> {
                getAllEventNodes().forEach(eNode ->
                        eNode.applyHighlightEffect(newPredicate.test(eNode)));
            });

            TimeLineChart.MouseClickedHandler<DateTime, DetailsChart> mouseClickedHandler = new TimeLineChart.MouseClickedHandler<>(getSkinnable());
            TimeLineChart.ChartDragHandler<DateTime, DetailsChart> chartDragHandler = new TimeLineChart.ChartDragHandler<>(getSkinnable());
            configureMouseListeners(primaryLane, mouseClickedHandler, chartDragHandler);
            configureMouseListeners(pinnedLane, mouseClickedHandler, chartDragHandler);

            getSkinnable().getLayoutSettings().pinnedLaneShowing().addListener(observable -> {
                boolean selected = getSkinnable().getLayoutSettings().isPinnedLaneShowing();
                if (selected == false) {
                    dividerPosition = masterDetailPane.getDividerPosition();
                }
                masterDetailPane.setShowDetailNode(selected);
                if (selected) {
                    syncPinnedHeight();
                    masterDetailPane.setDividerPosition(dividerPosition);
                }
            });

            getSkinnable().intervalSelector().addListener(observable -> {
                if (getSkinnable().getIntervalSelector() == null) {
                    rootPane.getChildren().remove(intervalSelector);
                    intervalSelector = null;
                } else {
                    rootPane.getChildren().add(getSkinnable().getIntervalSelector());
                    intervalSelector = getSkinnable().getIntervalSelector();
                }
            });

            getSkinnable().guideLines.addListener((SetChangeListener.Change<? extends GuideLine> change) -> {
                if (change.wasRemoved()) {
                    rootPane.getChildren().remove(change.getElementRemoved());
                }
                if (change.wasAdded()) {
                    rootPane.getChildren().add(change.getElementAdded());
                }
            });
        }

        private Iterable<EventNodeBase<?>> getAllEventNodes() {
            return Iterables.concat(primaryLane.getAllNodes(), pinnedLane.getAllNodes());
        }

        private void syncPinnedHeight() {
            pinnedView.setMinHeight(MIN_PINNED_LANE_HEIGHT);
            pinnedView.setMaxHeight(pinnedLane.maxVScrollProperty().get() + 30);
        }

        /**
         *
         * @param chartLane           the value of chartLane
         * @param mouseClickedHandler the value of mouseClickedHandler
         * @param chartDragHandler1   the value of chartDragHandler1
         */
        static private void configureMouseListeners(final DetailsChartLane<?> chartLane, final TimeLineChart.MouseClickedHandler<DateTime, DetailsChart> mouseClickedHandler, final TimeLineChart.ChartDragHandler<DateTime, DetailsChart> chartDragHandler) {
            chartLane.setOnMousePressed(chartDragHandler);
            chartLane.setOnMouseReleased(chartDragHandler);
            chartLane.setOnMouseDragged(chartDragHandler);
            chartLane.setOnMouseClicked(chartDragHandler);
            chartLane.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
        }
    }
}
