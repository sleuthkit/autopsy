/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-19 Basis Technology Corp.
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
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
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
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.actions.AddManualEvent;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailsViewModel;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventStripe;

/**
 * A TimeLineChart that implements the visual aspects of the DetailView
 */
final class DetailsChart extends Control implements TimeLineChart<DateTime> {

    ///chart axes
    private final DateAxis detailsChartDateAxis;
    private final DateAxis pinnedDateAxis;
    private final Axis<EventStripe> verticalAxis;

    /**
     * Property that holds the interval selector if one is active
     */
    private final SimpleObjectProperty<IntervalSelector<? extends DateTime>> intervalSelectorProp = new SimpleObjectProperty<>();

    /**
     * ObservableSet of GuieLines displayed in this chart
     */
    private final ObservableSet<GuideLine> guideLines = FXCollections.observableSet();

    /**
     * Predicate used to determine if a EventNode should be highlighted. Can be
     * a combination of conditions such as: be in the selectedNodes list OR have
     * a particular description, but it must include be in the selectedNodes
     * (selectedNodes::contains).
     */
    private final SimpleObjectProperty<Predicate<EventNodeBase<?>>> highlightPredicate = new SimpleObjectProperty<>(dummy -> false);

    /**
     * An ObservableList of the Nodes that are selected in this chart.
     */
    private final ObservableList<EventNodeBase<?>> selectedNodes;

    /**
     * An ObservableList representing all the events in the tree as a flat list
     * of events whose roots are in the eventStripes lists
     *
     */
    private final ObservableList<DetailViewEvent> nestedEvents = FXCollections.observableArrayList();

    /**
     * Aggregates all the settings related to the layout of this chart as one
     * object.
     */
    private final DetailsChartLayoutSettings layoutSettings;
    private final DetailsViewModel detailsViewModel;

    DetailsViewModel getDetailsViewModel() {
        return detailsViewModel;
    }

    /**
     * The main controller object for this instance of the Timeline UI.
     */
    private final TimeLineController controller;

    /**
     * An ObservableList of root event stripes to display in the chart.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<EventStripe> rootEventStripes = FXCollections.observableArrayList();

    /**
     * Constructor
     *
     * @param detailsViewModel     The DetailsViewModel to use for this chart.
     * @param controller           The TimeLineController for this chart.
     * @param detailsChartDateAxis The DateAxis to use in this chart.
     * @param pinnedDateAxis       The DateAxis to use for the pinned lane. It
     *                             will not be shown on screen, but must not be
     *                             null or the same as the detailsChartDateAxis.
     * @param verticalAxis         An Axis<EventStripe> to use as the vertical
     *                             axis in the primary lane.
     * @param selectedNodes        An ObservableList<EventNodeBase<?>>, that
     *                             will be used to keep track of the nodes
     *                             selected in this chart.
     */
    DetailsChart(DetailsViewModel detailsViewModel, TimeLineController controller, DateAxis detailsChartDateAxis, DateAxis pinnedDateAxis, Axis<EventStripe> verticalAxis, ObservableList<EventNodeBase<?>> selectedNodes) {
        this.detailsViewModel = detailsViewModel;
        this.controller = controller;
        this.layoutSettings = new DetailsChartLayoutSettings(controller);
        this.detailsChartDateAxis = detailsChartDateAxis;
        this.verticalAxis = verticalAxis;
        this.pinnedDateAxis = pinnedDateAxis;
        this.selectedNodes = selectedNodes;

        EventsModel eventsModel = getController().getEventsModel();

        /*
         * If the time range is changed, clear the guide line and the interval
         * selector, since they may not be in view any more.
         */
        eventsModel.timeRangeProperty().addListener(observable -> clearTimeBasedUIElements());

        //if the view paramaters change, clear the selection
        eventsModel.modelParamsProperty().addListener(observable -> getSelectedNodes().clear());
    }

    /**
     * Get the DateTime represented by the given x-position in this chart.
     *
     *
     * @param xPos The x-position to get the DataTime for.
     *
     * @return The DateTime represented by the given x-position in this chart.
     */
    DateTime getDateTimeForPosition(double xPos) {
        return getXAxis().getValueForDisplay(getXAxis().parentToLocal(xPos, 0).getX());
    }

    /**
     * Add an EventStripe to the list of root stripes.
     *
     * @param stripe The EventStripe to add.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void addStripe(EventStripe stripe) {
        rootEventStripes.add(stripe);
        nestedEvents.add(stripe);
    }

    /**
     * Remove the given GuideLine from this chart.
     *
     * @param guideLine The GuideLine to remove.
     */
    void clearGuideLine(GuideLine guideLine) {
        guideLines.remove(guideLine);
    }

    @Override
    public ObservableList<EventNodeBase<?>> getSelectedNodes() {
        return selectedNodes;
    }

    /**
     * Get the DetailsChartLayoutSettings for this chart.
     *
     * @return The DetailsChartLayoutSettings for this chart.
     */
    DetailsChartLayoutSettings getLayoutSettings() {
        return layoutSettings;
    }

    /**
     * Set the Predicate used to determine if a EventNode should be highlighted.
     * Can be a combination of conditions such as: be in the selectedNodes list
     * OR have a particular description, but it must include be in the
     * selectedNodes (selectedNodes::contains).
     *
     * @param highlightPredicate The Predicate used to determine which nodes to
     *                           highlight.
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
    public ObservableList<DetailViewEvent> getAllNestedEvents() {
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

    @Override
    public void clearIntervalSelector() {
        intervalSelectorProp.set(null);
    }

    @Override
    public IntervalSelector<DateTime> newIntervalSelector() {
        return new DetailIntervalSelector(this);
    }

    @Override
    public IntervalSelector<? extends DateTime> getIntervalSelector() {
        return intervalSelectorProp.get();
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends DateTime> newIntervalSelector) {
        intervalSelectorProp.set(newIntervalSelector);
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

        long selectedTimeMillis = getXAxis().getValueForDisplay(getXAxis().parentToLocal(mouseEvent.getX(), 0).getX()).getMillis();

        //make and assign a new context menu based on the given mouseEvent
        setContextMenu(ActionUtils.createContextMenu(Arrays.asList(
                new PlaceMarkerAction(this, mouseEvent),
                new AddManualEvent(controller, selectedTimeMillis),
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
     * @return The ObservableList of root EventStripes.
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
        protected Interval adjustInterval(Interval interval) {
            return interval;
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

    /**
     * The Skin for DetailsChart that implements the visual display of the
     * chart.
     */
    static private class DetailsChartSkin extends SkinBase<DetailsChart> {

        /**
         * If the pinned lane is visible this is the minimum height.
         */
        private static final int MIN_PINNED_LANE_HEIGHT = 50;

        /*
         * The ChartLane for the main area of this chart. It is affected by all
         * the view settings.
         */
        private final PrimaryDetailsChartLane primaryLane;

        /**
         * Container for the primary Lane that adds a vertical ScrollBar
         */
        private final ScrollingLaneWrapper primaryView;

        /*
         * The ChartLane for the area of this chart that shows pinned eventsd.
         * It is not affected any filters.
         */
        private final PinnedEventsChartLane pinnedLane;

        /**
         * Container for the pinned Lane that adds a vertical ScrollBar
         */
        private final ScrollingLaneWrapper pinnedView;

        /**
         * Shows the two lanes with the primary lane as the master, and the
         * pinned lane as the details view above the primary lane. Used to show
         * and hide the pinned lane with a slide in/out animation.
         */
        private final MasterDetailPane masterDetailPane;

        /**
         * Root Pane of this skin,
         */
        private final Pane rootPane;

        /**
         * The divider position of masterDetailPane is saved when the pinned
         * lane is hidden so it can be restored when the pinned lane is shown
         * again.
         */
        private double dividerPosition = .1;

        @NbBundle.Messages("DetailViewPane.pinnedLaneLabel.text=Pinned Events")
        DetailsChartSkin(DetailsChart chart) {
            super(chart);
            //initialize chart lanes;
            primaryLane = new PrimaryDetailsChartLane(chart, getSkinnable().detailsChartDateAxis, getSkinnable().verticalAxis);
            primaryView = new ScrollingLaneWrapper(primaryLane);
            pinnedLane = new PinnedEventsChartLane(chart, getSkinnable().pinnedDateAxis, new EventAxis<>(Bundle.DetailViewPane_pinnedLaneLabel_text()));
            pinnedView = new ScrollingLaneWrapper(pinnedLane);

            pinnedLane.setMinHeight(MIN_PINNED_LANE_HEIGHT);
            pinnedLane.maxVScrollProperty().addListener(maxVScroll -> syncPinnedHeight());

            //assemble scene graph
            masterDetailPane = new MasterDetailPane(Side.TOP, primaryView, pinnedView, false);
            masterDetailPane.setDividerPosition(dividerPosition);
            masterDetailPane.prefHeightProperty().bind(getSkinnable().heightProperty());
            masterDetailPane.prefWidthProperty().bind(getSkinnable().widthProperty());
            rootPane = new Pane(masterDetailPane);
            getChildren().add(rootPane);

            //maintain highlighted effect on correct nodes
            getSkinnable().highlightPredicate.addListener((observable, oldPredicate, newPredicate) -> {
                primaryLane.getAllNodes().forEach(primaryNode -> primaryNode.applyHighlightEffect(newPredicate.test(primaryNode)));
                pinnedLane.getAllNodes().forEach(pinnedNode -> pinnedNode.applyHighlightEffect(newPredicate.test(pinnedNode)));
            });

            //configure mouse listeners
            TimeLineChart.MouseClickedHandler<DateTime, DetailsChart> mouseClickedHandler = new TimeLineChart.MouseClickedHandler<>(getSkinnable());
            TimeLineChart.ChartDragHandler<DateTime, DetailsChart> chartDragHandler = new TimeLineChart.ChartDragHandler<>(getSkinnable());
            configureMouseListeners(primaryLane, mouseClickedHandler, chartDragHandler);
            configureMouseListeners(pinnedLane, mouseClickedHandler, chartDragHandler);

            //show and hide pinned lane in response to settings property change
            getSkinnable().getLayoutSettings().pinnedLaneShowing().addListener(observable -> syncPinnedLaneShowing());
            syncPinnedLaneShowing();

            //show and remove interval selector in sync with control state change
            getSkinnable().intervalSelectorProp.addListener((observable, oldIntervalSelector, newIntervalSelector) -> {
                rootPane.getChildren().remove(oldIntervalSelector);
                if (null != newIntervalSelector) {
                    rootPane.getChildren().add(newIntervalSelector);
                }
            });

            //show and remove guidelines in sync with control state change
            getSkinnable().guideLines.addListener((SetChangeListener.Change<? extends GuideLine> change) -> {
                if (change.wasRemoved()) {
                    rootPane.getChildren().remove(change.getElementRemoved());
                }
                if (change.wasAdded()) {
                    rootPane.getChildren().add(change.getElementAdded());
                }
            });
        }

        /**
         * Sync the allowed height of the pinned lane's scroll pane to the lanes
         * actual height.
         */
        private void syncPinnedHeight() {
            pinnedView.setMinHeight(MIN_PINNED_LANE_HEIGHT);
            pinnedView.setMaxHeight(pinnedLane.maxVScrollProperty().get() + 30);
        }

        /**
         * Add the given listeners to the given chart lane
         *
         * @param chartLane           The Chart lane to add the listeners to.
         * @param mouseClickedHandler The MouseClickedHandler to add to chart.
         * @param chartDragHandler    The ChartDragHandler to add to the chart
         *                            as pressed, released, dragged, and clicked
         *                            handler.
         */
        static private void configureMouseListeners(final DetailsChartLane<?> chartLane, final TimeLineChart.MouseClickedHandler<DateTime, DetailsChart> mouseClickedHandler, final TimeLineChart.ChartDragHandler<DateTime, DetailsChart> chartDragHandler) {
            chartLane.setOnMousePressed(chartDragHandler);
            chartLane.setOnMouseReleased(chartDragHandler);
            chartLane.setOnMouseDragged(chartDragHandler);
            chartLane.setOnMouseClicked(chartDragHandler);
            chartLane.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
        }

        /**
         * Show the pinned lane if and only if the settings object says it
         * should be.
         */
        private void syncPinnedLaneShowing() {
            boolean pinnedLaneShowing = getSkinnable().getLayoutSettings().isPinnedLaneShowing();
            if (pinnedLaneShowing == false) {
                //Save  the divider position for later.
                dividerPosition = masterDetailPane.getDividerPosition();
            }

            masterDetailPane.setShowDetailNode(pinnedLaneShowing);

            if (pinnedLaneShowing) {
                syncPinnedHeight();
                //Restore the devider position.
                masterDetailPane.setDividerPosition(dividerPosition);
            }
        }
    }
}
