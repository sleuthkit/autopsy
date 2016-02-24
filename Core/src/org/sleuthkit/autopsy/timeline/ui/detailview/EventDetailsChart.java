/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;

/**
 * Custom implementation of {@link XYChart} to graph events on a horizontal
 * timeline.
 *
 * The horizontal {@link DateAxis} controls the tick-marks and the horizontal
 * layout of the nodes representing events. The vertical {@link NumberAxis} does
 * nothing (although a custom implementation could help with the vertical
 * layout?)
 *
 * Series help organize events for the banding by event type, we could add a
 * node to contain each band if we need a place for per band controls.
 *
 * NOTE: It was too hard to control the threading of this chart via the
 * complicated default listeners. Instead clients should use null {@link #addDataItem(javafx.scene.chart.XYChart.Data)
 * } and {@link #removeDataItem(javafx.scene.chart.XYChart.Data) } to add and
 * remove data.
 *
 * //TODO: refactor the projected lines to a separate class. -jm
 */
public final class EventDetailsChart extends XYChart<DateTime, EventStripe> implements DetailsChart {

    private static final String styleSheet = GuideLine.class.getResource("EventsDetailsChart.css").toExternalForm(); //NON-NLS

    private static final Image MARKER = new Image("/org/sleuthkit/autopsy/timeline/images/marker.png", 16, 16, true, true, true); //NON-NLS
    private static final int PROJECTED_LINE_Y_OFFSET = 5;
    private static final int PROJECTED_LINE_STROKE_WIDTH = 5;
    private static final int MINIMUM_EVENT_NODE_GAP = 4;
    private final static int MINIMUM_ROW_HEIGHT = 24;

    private final TimeLineController controller;
    private final FilteredEventsModel filteredEvents;

    private ContextMenu chartContextMenu;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)//at start of layout pass
    private Set<String> activeQuickHidefilters;
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)//at start of layout pass
    private double descriptionWidth;
    private final DetailViewLayoutSettings layoutSettings;

    @Override
    public ContextMenu getChartContextMenu() {
        return chartContextMenu;
    }

    /**
     * a user positionable vertical line to help compare events
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private Line guideLine;

    /**
     * * the user can drag out a time range to zoom into and this
     * {@link IntervalSelector} is the visual representation of it while the
     * user is dragging
     */
    private IntervalSelector<? extends DateTime> intervalSelector;

    /**
     * listener that triggers chart layout pass
     */
    private final InvalidationListener layoutInvalidationListener = (Observable o) -> {
        layoutPlotChildren();
    };

    /**
     * the maximum y value used so far during the most recent layout pass
     */
    private final ReadOnlyDoubleWrapper maxY = new ReadOnlyDoubleWrapper(0.0);

    final ObservableList<EventNodeBase<?>> selectedNodes;
    /**
     * the group that all event nodes are added to. This facilitates scrolling
     * by allowing a single translation of this group.
     */
    private final Group nodeGroup = new Group();

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<EventStripe> eventStripes = FXCollections.observableArrayList();
    private final ObservableList< EventNodeBase<?>> stripeNodes = FXCollections.observableArrayList();
    private final ObservableList<  EventNodeBase<?>> sortedStripeNodes = stripeNodes.sorted(Comparator.comparing(EventNodeBase<?>::getStartMillis));
    private final Map<EventCluster, Line> projectionMap = new ConcurrentHashMap<>();

    EventDetailsChart(TimeLineController controller, DateAxis dateAxis, final Axis<EventStripe> verticalAxis, ObservableList<EventNodeBase<?>> selectedNodes, DetailViewLayoutSettings layoutSettings) {
        super(dateAxis, verticalAxis);
        this.layoutSettings = layoutSettings;

        this.controller = controller;
        this.filteredEvents = this.controller.getEventsModel();

        sceneProperty().addListener(observable -> {
            Scene scene = getScene();
            if (scene != null && scene.getStylesheets().contains(styleSheet) == false) {
                scene.getStylesheets().add(styleSheet);
            }
        });

        filteredEvents.zoomParametersProperty().addListener(o -> {
            clearGuideLine();
            clearIntervalSelector();
            selectedNodes.clear();
            projectionMap.clear();
            controller.selectEventIDs(Collections.emptyList());
        });

        Tooltip.install(this, AbstractVisualizationPane.getDefaultTooltip());

        dateAxis.setAutoRanging(false);
        verticalAxis.setVisible(false);//TODO: why doesn't this hide the vertical axis, instead we have to turn off all parts individually? -jm
        verticalAxis.setTickLabelsVisible(false);
        verticalAxis.setTickMarkVisible(false);
        setLegendVisible(false);
        setPadding(Insets.EMPTY);
        setAlternativeColumnFillVisible(true);

        //all nodes are added to nodeGroup to facilitate scrolling rather than to getPlotChildren() directly
        getPlotChildren().add(nodeGroup);

        //add listener for events that should trigger layout
        layoutSettings.bandByTypeProperty().addListener(layoutInvalidationListener);
        layoutSettings.oneEventPerRowProperty().addListener(layoutInvalidationListener);
        layoutSettings.truncateAllProperty().addListener(layoutInvalidationListener);
        layoutSettings.truncateAllProperty().addListener(layoutInvalidationListener);
        layoutSettings.descrVisibilityProperty().addListener(layoutInvalidationListener);
        getController().getQuickHideFilters().addListener(layoutInvalidationListener);

        //this is needed to allow non circular binding of the guideline and timerangeRect heights to the height of the chart
        //TODO: seems like a hack, can we remove? -jm
        boundsInLocalProperty().addListener((Observable observable) -> {
            setPrefHeight(boundsInLocalProperty().get().getHeight());
        });

        ChartDragHandler<DateTime, EventDetailsChart> chartDragHandler = new ChartDragHandler<>(this);
        setOnMousePressed(chartDragHandler);
        setOnMouseReleased(chartDragHandler);
        setOnMouseDragged(chartDragHandler);

        setOnMouseClicked(new MouseClickedHandler<>(this));

        this.selectedNodes = selectedNodes;
        this.selectedNodes.addListener(new SelectionChangeHandler());
    }

    @Override
    public void requestTimelineChartLayout() {
        requestChartLayout();
    }

    public ObservableList<EventNodeBase<?>> getSelectedNodes() {
        return selectedNodes;
    }

    @Override
    public Node asNode() {
        return this;
    }

    public ObservableList<EventStripe> getEventStripes() {
        return eventStripes;
    }

    @Override
    public TimeLineController getController() {
        return controller;
    }

    @Override
    public ContextMenu getChartContextMenu(MouseEvent clickEvent) throws MissingResourceException {
        if (chartContextMenu != null) {
            chartContextMenu.hide();
        }

        chartContextMenu = ActionUtils.createContextMenu(Arrays.asList(new PlaceMarkerAction(clickEvent),
                TimeLineChart.newZoomHistoyActionGroup(controller)));
        chartContextMenu.setAutoHide(true);
        return chartContextMenu;
    }

    @Override
    public void clearIntervalSelector() {
        getChartChildren().remove(intervalSelector);
        intervalSelector = null;
    }

    @Override
    public IntervalSelector<DateTime> newIntervalSelector() {
        return new DetailIntervalSelector(this);
    }

    /**
     * get the DateTime along the x-axis that corresponds to the given
     * x-coordinate in the coordinate system of this {@link EventDetailsChart}
     *
     * @param x a x-coordinate in the space of this {@link EventDetailsChart}
     *
     * @return the DateTime along the x-axis corresponding to the given x value
     *         (in the space of this {@link EventDetailsChart}
     */
    public DateTime getDateTimeForPosition(double x) {
        return getXAxis().getValueForDisplay(getXAxis().parentToLocal(x, 0).getX());
    }

    @Override
    public IntervalSelector<? extends DateTime> getIntervalSelector() {
        return intervalSelector;
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends DateTime> newIntervalSelector) {
        intervalSelector = newIntervalSelector;
        getChartChildren().add(getIntervalSelector());
    }

    /**
     * @see note in main section of class JavaDoc
     *
     * @param series
     * @param i
     */
    @Override
    protected void seriesAdded(Series<DateTime, EventStripe> series, int i) {

    }

    /**
     * @see note in main section of class JavaDoc
     *
     * @param series
     */
    @Override
    protected void seriesRemoved(Series<DateTime, EventStripe> series) {

    }

    /**
     * @see note in main section of class JavaDoc
     *
     * @param series
     * @param itemIndex
     * @param item
     */
    @Override
    protected void dataItemAdded(Series<DateTime, EventStripe> series, int itemIndex, Data<DateTime, EventStripe> item) {
    }

    /**
     * @see note in main section of class JavaDoc
     *
     *
     * @param item
     * @param series
     */
    @Override
    protected void dataItemRemoved(Data<DateTime, EventStripe> item, Series<DateTime, EventStripe> series) {
    }

    /**
     * @see note in main section of class JavaDoc
     *
     * @param item
     */
    @Override
    protected void dataItemChanged(Data<DateTime, EventStripe> item) {
    }

    /**
     * add a dataitem to this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param data
     */
    void addDataItem(Data<DateTime, EventStripe> data) {
        final EventStripe eventStripe = data.getYValue();
        EventNodeBase<?> newNode;
        if (eventStripe.getEventIDs().size() == 1) {
            newNode = new SingleEventNode(this, controller.getEventsModel().getEventById(Iterables.getOnlyElement(eventStripe.getEventIDs())), null);
        } else {
            newNode = new EventStripeNode(EventDetailsChart.this, eventStripe, null);
        }
        Platform.runLater(() -> {
            eventStripes.add(eventStripe);
            stripeNodes.add(newNode);
            nodeGroup.getChildren().add(newNode);
            data.setNode(newNode);
        });
    }

    /**
     * remove a data item from this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param data
     */
    void removeDataItem(Data<DateTime, EventStripe> data) {
        Platform.runLater(() -> {
            EventNodeBase<?> removedNode = (EventNodeBase<?>) data.getNode();
            eventStripes.removeAll(new StripeFlattener().apply(removedNode).collect(Collectors.toList()));
            stripeNodes.removeAll(removedNode);
            nodeGroup.getChildren().removeAll(removedNode);
            data.setNode(null);
        });
    }

    @Override
    protected void layoutPlotChildren() {
        setCursor(Cursor.WAIT);
        maxY.set(0);

        //These don't change during a layout pass and are expensive to compute per node.  So we do it once at the start
        activeQuickHidefilters = getController().getQuickHideFilters().stream()
                .filter(AbstractFilter::isActive)
                .map(DescriptionFilter::getDescription)
                .collect(Collectors.toSet());

        //This dosn't change during a layout pass and is expensive to compute per node.  So we do it once at the start
        descriptionWidth = layoutSettings.getTruncateAll() ? layoutSettings.getTruncateWidth() : USE_PREF_SIZE;

        if (layoutSettings.getBandByType()) {
            sortedStripeNodes.stream()
                    .collect(Collectors.groupingBy(EventNodeBase<?>::getEventType)).values()
                    .forEach(inputNodes -> maxY.set(layoutEventBundleNodes(inputNodes, maxY.get())));
        } else {
            maxY.set(layoutEventBundleNodes(sortedStripeNodes.sorted(Comparator.comparing(EventNodeBase<?>::getStartMillis)), 0));
        }
        layoutProjectionMap();
        setCursor(null);
    }

    public ReadOnlyDoubleProperty maxVScrollProperty() {
        return maxY.getReadOnlyProperty();
    }

    /**
     * @return all the nodes that pass the given predicate
     */
    synchronized Iterable<EventNodeBase<?>> getNodes(Predicate<EventNodeBase<?>> p) {
        //use this recursive function to flatten the tree of nodes into an single stream.
        Function<EventNodeBase<?>, Stream<EventNodeBase<?>>> stripeFlattener =
                new Function<EventNodeBase<?>, Stream<EventNodeBase<?>>>() {
                    @Override
                    public Stream<EventNodeBase<?>> apply(EventNodeBase<?> node) {
                        return Stream.concat(
                                Stream.of(node),
                                node.getSubNodes().stream().flatMap(this::apply));
                    }
                };

        return sortedStripeNodes.stream()
                .flatMap(stripeFlattener)
                .filter(p).collect(Collectors.toList());
    }

    public synchronized void setVScroll(double vScrollValue) {
        nodeGroup.setTranslateY(-vScrollValue);
    }

    void clearGuideLine() {
        getChartChildren().remove(guideLine);
        guideLine = null;
    }

    /**
     * Layout the nodes in the given list, starting form the given minimum y
     * coordinate via the following algorithm:
     *
     * We start with a list of nodes (each representing an event) sorted by span
     * start time of the underlying event
     *
     * - initialize empty map (maxXatY) from y-ranges to max used x-value
     *
     * - for each node:
     *
     * -- size the node based on its children (use this algorithm recursively)
     *
     * -- get the event's start position from the dateaxis
     *
     * -- to position node: check if maxXatY is to the left of the left x coord:
     * if maxXatY is less than the left x coord, good, put the current node
     * here, mark right x coord as maxXatY, go to next node ; if maxXatY is
     * greater than the left x coord, increment y position, do check again until
     * maxXatY less than left x coord.
     *
     * @param nodes            collection of nodes to layout, sorted by event
     *                         start time
     * @param minY             the minimum y coordinate to position the nodes
     *                         at.
     * @param descriptionWidth the value of the maximum description width to set
     *                         for each node.
     *
     * @return the maximum y coordinate used by any of the layed out nodes.
     */
    public double layoutEventBundleNodes(final Collection<? extends EventNodeBase<?>> nodes, final double minY) {
        // map from y-ranges to maximum x
        TreeRangeMap<Double, Double> maxXatY = TreeRangeMap.create();

        // maximum y values occupied by any of the given nodes,  updated as nodes are layed out.
        double localMax = minY;

        //for each node do a recursive layout to size it and then position it in first available slot
        for (EventNodeBase<?> bundleNode : nodes) {
            //is the node hiden by a quick hide filter?
            boolean quickHide = activeQuickHidefilters.contains(bundleNode.getDescription());
            if (quickHide) {
                //hide it and skip layout
                bundleNode.setVisible(false);
                bundleNode.setManaged(false);
            } else {
                layoutBundleHelper(bundleNode);
                //get computed height and width
                double h = bundleNode.getBoundsInLocal().getHeight();
                double w = bundleNode.getBoundsInLocal().getWidth();
                //get left and right x coords from axis plus computed width
                double xLeft = getXForEpochMillis(bundleNode.getStartMillis()) - bundleNode.getLayoutXCompensation();
                double xRight = xLeft + w + MINIMUM_EVENT_NODE_GAP;

                //initial test position
                double yTop = (layoutSettings.getOneEventPerRow())
                        ? (localMax + MINIMUM_EVENT_NODE_GAP)// if onePerRow, just put it at end
                        : computeYTop(minY, h, maxXatY, xLeft, xRight);

                localMax = Math.max(yTop + h, localMax);

                if ((xLeft != bundleNode.getLayoutX()) || (yTop != bundleNode.getLayoutY())) {
                    //animate node to new position
                    bundleNode.animateTo(xLeft, yTop);
                }
            }
        }
        return localMax; //return new max
    }

    /**
     * Given information about the current layout pass so far and about a
     * particular node, compute the y position of that node.
     *
     *
     * @param yMin    the smallest (towards the top of the screen) y position to
     *                consider
     * @param h       the height of the node we are trying to position
     * @param maxXatY a map from y ranges to the max x within that range. NOTE:
     *                This map will be updated to include the node in question.
     * @param xLeft   the left x-cord of the node to position
     * @param xRight  the left x-cord of the node to position
     *
     * @return the y position for the node in question.
     *
     *
     */
    private double computeYTop(double yMin, double h, TreeRangeMap<Double, Double> maxXatY, double xLeft, double xRight) {
        double yTop = yMin;
        double yBottom = yTop + h;
        //until the node is not overlapping any others try moving it down.
        boolean overlapping = true;
        while (overlapping) {
            overlapping = false;
            //check each pixel from bottom to top.
            for (double y = yBottom; y >= yTop; y -= MINIMUM_ROW_HEIGHT) {
                final Double maxX = maxXatY.get(y);
                if (maxX != null && maxX >= xLeft - MINIMUM_EVENT_NODE_GAP) {
                    //if that pixel is already used
                    //jump top to this y value and repeat until free slot is found.
                    overlapping = true;
                    yTop = y + MINIMUM_EVENT_NODE_GAP;
                    yBottom = yTop + h;
                    break;
                }
            }
        }
        maxXatY.put(Range.closed(yTop, yBottom), xRight);
        return yTop;
    }

    /**
     *
     * Set layout paramaters on the given node and layout its children
     *
     * @param eventNode        the Node to layout
     * @param descriptionWdith the maximum width for the description text
     */
    private void layoutBundleHelper(final EventNodeBase< ?> eventNode) {
        //make sure it is shown
        eventNode.setVisible(true);
        eventNode.setManaged(true);
        //apply advanced layout description visibility options
        eventNode.setDescriptionVisibility(layoutSettings.getDescrVisibility());
        eventNode.setMaxDescriptionWidth(descriptionWidth);

        //do recursive layout
        eventNode.layoutChildren();
    }

    /**
     * expose as protected
     */
    @Override
    protected void requestChartLayout() {
        super.requestChartLayout();
    }

    private double getXForEpochMillis(Long millis) {
        DateTime dateTime = new DateTime(millis);
        return getXAxis().getDisplayPosition(dateTime);
    }

    private double getParentXForEpochMillis(Long epochMillis) {
        return getXAxis().localToParent(getXForEpochMillis(epochMillis), 0).getX();
    }

    private void layoutProjectionMap() {
        for (final Map.Entry<EventCluster, Line> entry : projectionMap.entrySet()) {
            final EventCluster cluster = entry.getKey();
            final Line line = entry.getValue();

            line.setStartX(getParentXForEpochMillis(cluster.getStartMillis()));
            line.setEndX(getParentXForEpochMillis(cluster.getEndMillis()));

            line.setStartY(getXAxis().getLayoutY() + PROJECTED_LINE_Y_OFFSET);
            line.setEndY(getXAxis().getLayoutY() + PROJECTED_LINE_Y_OFFSET);
        }
    }

    /**
     * @return the filteredEvents
     */
    public FilteredEventsModel getFilteredEvents() {
        return filteredEvents;

    }

    static private class DetailIntervalSelector extends IntervalSelector<DateTime> {

        DetailIntervalSelector(EventDetailsChart chart) {
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

    private class PlaceMarkerAction extends Action {

        @NbBundle.Messages({"EventDetailChart.chartContextMenu.placeMarker.name=Place Marker"})
        PlaceMarkerAction(MouseEvent clickEvent) {
            super(Bundle.EventDetailChart_chartContextMenu_placeMarker_name());

            setGraphic(new ImageView(MARKER)); // NON-NLS
            setEventHandler(actionEvent -> {
                if (guideLine == null) {
                    guideLine = new GuideLine(EventDetailsChart.this);
                    guideLine.relocate(sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                    getChartChildren().add(guideLine);

                } else {
                    guideLine.relocate(sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                }
            });
        }
    }

    private class SelectionChangeHandler implements ListChangeListener<EventNodeBase<?>> {

        private final Axis<DateTime> dateAxis;

        SelectionChangeHandler() {
            dateAxis = getXAxis();
        }

        @Override
        public void onChanged(ListChangeListener.Change<? extends EventNodeBase<?>> change) {
            while (change.next()) {
                change.getRemoved().forEach((EventNodeBase<?> removedNode) -> {
                    removedNode.getEvent().getClusters().forEach(cluster -> {
                        Line removedLine = projectionMap.remove(cluster);
                        getChartChildren().removeAll(removedLine);
                    });

                });
                change.getAddedSubList().forEach((EventNodeBase<?> addedNode) -> {

                    for (EventCluster range : addedNode.getEvent().getClusters()) {

                        Line line = new Line(dateAxis.localToParent(dateAxis.getDisplayPosition(new DateTime(range.getStartMillis(), TimeLineController.getJodaTimeZone())), 0).getX(), dateAxis.getLayoutY() + PROJECTED_LINE_Y_OFFSET,
                                dateAxis.localToParent(dateAxis.getDisplayPosition(new DateTime(range.getEndMillis(), TimeLineController.getJodaTimeZone())), 0).getX(), dateAxis.getLayoutY() + PROJECTED_LINE_Y_OFFSET
                        );
                        line.setStroke(addedNode.getEventType().getColor().deriveColor(0, 1, 1, .5));
                        line.setStrokeWidth(PROJECTED_LINE_STROKE_WIDTH);
                        line.setStrokeLineCap(StrokeLineCap.ROUND);
                        projectionMap.put(range, line);
                        getChartChildren().add(line);
                    }
                });
            }
            EventDetailsChart.this.controller.selectEventIDs(selectedNodes.stream()
                    .flatMap(detailNode -> detailNode.getEventIDs().stream())
                    .collect(Collectors.toList()));
        }
    }

}
