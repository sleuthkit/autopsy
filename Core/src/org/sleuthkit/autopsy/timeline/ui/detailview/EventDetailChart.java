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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import javax.annotation.concurrent.GuardedBy;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionGroup;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.Forward;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

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
 * //TODO: refactor the projected lines to a separate class. -jm
 */
public final class EventDetailChart extends XYChart<DateTime, EventCluster> implements TimeLineChart<DateTime> {

    static final Image HIDE = new Image("/org/sleuthkit/autopsy/timeline/images/eye--minus.png"); // NON-NLS
    static final Image SHOW = new Image("/org/sleuthkit/autopsy/timeline/images/eye--plus.png"); // NON-NLS
    private static final Image MARKER = new Image("/org/sleuthkit/autopsy/timeline/images/marker.png", 16, 16, true, true, true);
    private static final int PROJECTED_LINE_Y_OFFSET = 5;
    private static final int PROJECTED_LINE_STROKE_WIDTH = 5;
    private static final int DEFAULT_ROW_HEIGHT = 24;

    private ContextMenu chartContextMenu;

    private TimeLineController controller;

    private FilteredEventsModel filteredEvents;

    /**
     * a user position-able vertical line to help the compare events
     */
    private Line guideLine;

    /**
     * * the user can drag out a time range to zoom into and this
     * {@link IntervalSelector} is the visual representation of it while the
     * user is dragging
     */
    private IntervalSelector<? extends DateTime> intervalSelector;

    /**
     * listener that triggers layout pass
     */
    private final InvalidationListener layoutInvalidationListener = (Observable o) -> {
        synchronized (EventDetailChart.this) {
            requiresLayout = true;
            requestChartLayout();
        }
    };

    /**
     * the maximum y value used so far during the most recent layout pass
     */
    private final ReadOnlyDoubleWrapper maxY = new ReadOnlyDoubleWrapper(0.0);
    /**
     * flag indicating whether this chart actually needs a layout pass
     */
    @GuardedBy(value = "this")
    private boolean requiresLayout = true;

    final ObservableList<EventBundleNodeBase<?, ?, ?>> selectedNodes;
    /**
     * the group that all event nodes are added to. This facilitates scrolling
     * by allowing a single translation of this group.
     */
    private final Group nodeGroup = new Group();
    private final ObservableList<EventBundle<?>> bundles = FXCollections.observableArrayList();
    private final Map<ImmutablePair<EventType, String>, EventStripe> stripeDescMap = new HashMap<>();
    private final Map<EventStripe, EventStripeNode> stripeNodeMap = new HashMap<>();
    private final Map<EventCluster, Line> projectionMap = new HashMap<>();

    /**
     * list of series of data added to this chart
     *
     * TODO: replace this with a map from name to series? -jm
     */
    private final ObservableList<Series<DateTime, EventCluster>> seriesList =
            FXCollections.<Series<DateTime, EventCluster>>observableArrayList();

    private final ObservableList<Series<DateTime, EventCluster>> sortedSeriesList = seriesList
            .sorted((s1, s2) -> {
                final List<String> collect = EventType.allTypes.stream().map(EventType::getDisplayName).collect(Collectors.toList());
                return Integer.compare(collect.indexOf(s1.getName()), collect.indexOf(s2.getName()));
            });
    /**
     * true == layout each event type in its own band, false == mix all the
     * events together during layout
     */
    private final SimpleBooleanProperty bandByType = new SimpleBooleanProperty(false);
    /**
     * true == enforce that no two events can share the same 'row', leading to
     * sparser but possibly clearer layout. false == put unrelated events in the
     * same 'row', creating a denser more compact layout
     */
    private final SimpleBooleanProperty oneEventPerRow = new SimpleBooleanProperty(false);

    /**
     * how much detail of the description to show in the ui
     */
    private final SimpleObjectProperty<DescriptionVisibility> descrVisibility =
            new SimpleObjectProperty<>(DescriptionVisibility.SHOWN);

    /**
     * true == truncate all the labels to the greater of the size of their
     * timespan indicator or the value of truncateWidth. false == don't truncate
     * the labels, alow them to extend past the timespan indicator and off the
     * edge of the screen
     */
    private final SimpleBooleanProperty truncateAll = new SimpleBooleanProperty(false);

    /**
     * the width to truncate all labels to if truncateAll is true. adjustable
     * via slider if truncateAll is true
     */
    private final SimpleDoubleProperty truncateWidth = new SimpleDoubleProperty(200.0);
    private final SimpleBooleanProperty alternateLayout = new SimpleBooleanProperty(true);

    EventDetailChart(DateAxis dateAxis, final Axis<EventCluster> verticalAxis, ObservableList<EventBundleNodeBase<?, ?, ?>> selectedNodes) {
        super(dateAxis, verticalAxis);
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
        widthProperty().addListener(layoutInvalidationListener);
        heightProperty().addListener(layoutInvalidationListener);
        bandByType.addListener(layoutInvalidationListener);
        oneEventPerRow.addListener(layoutInvalidationListener);
        truncateAll.addListener(layoutInvalidationListener);
        truncateWidth.addListener(layoutInvalidationListener);
        descrVisibility.addListener(layoutInvalidationListener);

        //this is needed to allow non circular binding of the guideline and timerangeRect heights to the height of the chart
        //TODO: seems like a hack, can we remove? -jm
        boundsInLocalProperty().addListener((Observable observable) -> {
            setPrefHeight(boundsInLocalProperty().get().getHeight());
        });

        //set up mouse listeners
        final EventHandler<MouseEvent> clickHandler = (MouseEvent clickEvent) -> {
            if (chartContextMenu != null) {
                chartContextMenu.hide();
            }
            if (clickEvent.getButton() == MouseButton.SECONDARY && clickEvent.isStillSincePress()) {
                getChartContextMenu(clickEvent);
                chartContextMenu.show(EventDetailChart.this, clickEvent.getScreenX(), clickEvent.getScreenY());
                clickEvent.consume();
            }
        };

        setOnMouseClicked(clickHandler);

        //use one handler with an if chain because it maintains state
        final ChartDragHandler<DateTime, EventDetailChart> dragHandler = new ChartDragHandler<>(this, getXAxis());
        setOnMousePressed(dragHandler);
        setOnMouseReleased(dragHandler);
        setOnMouseDragged(dragHandler);

        this.selectedNodes = selectedNodes;
        this.selectedNodes.addListener((
                ListChangeListener.Change<? extends EventBundleNodeBase<?, ?, ?>> change) -> {
                    while (change.next()) {
                        change.getRemoved().forEach((EventBundleNodeBase<?, ?, ?> removedNode) -> {
                            removedNode.getEventBundle().getClusters().forEach(cluster -> {

                                Line removedLine = projectionMap.remove(cluster);
                                getChartChildren().removeAll(removedLine);
                            });

                        });
                        change.getAddedSubList().forEach((EventBundleNodeBase<?, ?, ?> addedNode) -> {

                            for (EventCluster range : addedNode.getEventBundle().getClusters()) {

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

                    this.controller.selectEventIDs(selectedNodes.stream()
                            .flatMap(detailNode -> detailNode.getEventsIDs().stream())
                            .collect(Collectors.toList()));
                });

        requestChartLayout();
    }

    ObservableList<EventBundle<?>> getEventBundles() {
        return bundles;
    }

    TimeLineController getController() {
        return controller;
    }

    @NbBundle.Messages({"EventDetailChart.chartContextMenu.placeMarker.name=Place Marker",
        "EventDetailChart.contextMenu.zoomHistory.name=Zoom History"})
    ContextMenu getChartContextMenu(MouseEvent clickEvent) throws MissingResourceException {
        if (chartContextMenu != null) {
            chartContextMenu.hide();
        }

        chartContextMenu = ActionUtils.createContextMenu(Arrays.asList(new PlaceMarkerAction(clickEvent),
                new ActionGroup(Bundle.EventDetailChart_contextMenu_zoomHistory_name(),
                        new Back(controller),
                        new Forward(controller))));
        chartContextMenu.setAutoHide(true);
        return chartContextMenu;
    }

    @Override
    public void clearIntervalSelector() {
        getChartChildren().remove(intervalSelector);
        intervalSelector = null;
    }

    public synchronized SimpleBooleanProperty bandByTypeProperty() {
        return bandByType;
    }

    @Override
    public synchronized void setController(TimeLineController controller) {
        this.controller = controller;
        setModel(this.controller.getEventsModel());
        getController().getQuickHideFilters().addListener(layoutInvalidationListener);
    }

    @Override
    public void setModel(FilteredEventsModel filteredEvents) {

        if (this.filteredEvents != filteredEvents) {
            filteredEvents.zoomParametersProperty().addListener(o -> {
                clearGuideLine();
                clearIntervalSelector();

                selectedNodes.clear();
                projectionMap.clear();
                controller.selectEventIDs(Collections.emptyList());
            });
        }
        this.filteredEvents = filteredEvents;

    }

    @Override
    public IntervalSelector<DateTime> newIntervalSelector(double x, Axis<DateTime> axis) {
        return new DetailIntervalSelector(x, getHeight() - axis.getHeight() - axis.getTickLength(), axis, controller);
    }

    synchronized void setBandByType(Boolean t1) {
        bandByType.set(t1);
    }

    /**
     * get the DateTime along the x-axis that corresponds to the given
     * x-coordinate in the coordinate system of this {@link EventDetailChart}
     *
     * @param x a x-coordinate in the space of this {@link EventDetailChart}
     *
     * @return the DateTime along the x-axis corresponding to the given x value
     *         (in the space of this {@link EventDetailChart}
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

    SimpleBooleanProperty oneEventPerRowProperty() {
        return oneEventPerRow;
    }

    SimpleDoubleProperty getTruncateWidth() {
        return truncateWidth;
    }

    SimpleBooleanProperty truncateAllProperty() {
        return truncateAll;
    }

    SimpleObjectProperty< DescriptionVisibility> descrVisibilityProperty() {
        return descrVisibility;
    }

    @Override
    protected synchronized void dataItemAdded(Series<DateTime, EventCluster> series, int i, Data<DateTime, EventCluster> data) {
        final EventCluster eventCluster = data.getYValue();
        bundles.add(eventCluster);
        EventStripe eventStripe = stripeDescMap.merge(ImmutablePair.of(eventCluster.getEventType(), eventCluster.getDescription()),
                new EventStripe(eventCluster, null),
                (EventStripe u, EventStripe v) -> {
                    EventStripeNode remove = stripeNodeMap.remove(u);
                    nodeGroup.getChildren().remove(remove);
                    remove = stripeNodeMap.remove(v);
                    nodeGroup.getChildren().remove(remove);
                    return EventStripe.merge(u, v);
                }
        );
        EventStripeNode stripeNode = new EventStripeNode(EventDetailChart.this, eventStripe, null);
        stripeNodeMap.put(eventStripe, stripeNode);
        nodeGroup.getChildren().add(stripeNode);
        data.setNode(stripeNode);

    }

    @Override
    protected synchronized void dataItemChanged(Data<DateTime, EventCluster> data) {
        //TODO: can we use this to help with local detail level adjustment -jm
        throw new UnsupportedOperationException("Not supported yet."); // NON-NLS //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected synchronized void dataItemRemoved(Data<DateTime, EventCluster> data, Series<DateTime, EventCluster> series) {
        EventCluster eventCluster = data.getYValue();
        bundles.removeAll(eventCluster);
        EventStripe removedStripe = stripeDescMap.remove(ImmutablePair.of(eventCluster.getEventType(), eventCluster.getDescription()));
        EventStripeNode removedNode = stripeNodeMap.remove(removedStripe);
        nodeGroup.getChildren().remove(removedNode);

        data.setNode(null);
    }

    synchronized void setRequiresLayout(boolean b) {
        requiresLayout = true;
    }

    /**
     * make this accessible to {@link EventStripeNode}
     */
    @Override
    protected void requestChartLayout() {
        super.requestChartLayout();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

    }

    /**
     * Layout the nodes representing events via the following algorithm.
     *
     * we start with a list of nodes (each representing an event) - sort the
     * list of nodes by span start time of the underlying event - initialize
     * empty map (maxXatY) from y-position to max used x-value - for each node:
     * -- autosize the node (based on text label) -- get the event's start and
     * end positions from the dateaxis -- size the capsule representing event
     * duration -- starting from the top of the chart: --- (1)check if maxXatY
     * is to the left of the start position: -------if maxXatY less than start
     * position , good, put the current node here, mark end position as maxXatY,
     * go to next node -------if maxXatY greater than start position, increment
     * y position, do -------------check(1) again until maxXatY less than start
     * position
     */
    @Override
    protected synchronized void layoutPlotChildren() {
        if (requiresLayout) {
            setCursor(Cursor.WAIT);

            maxY.set(0.0);

            Map<Boolean, List<EventStripeNode>> hiddenPartition;
            if (bandByType.get()) {
                double minY = 0;
                for (Series<DateTime, EventCluster> series : sortedSeriesList) {
                    hiddenPartition = series.getData().stream().map(Data::getNode).map(EventStripeNode.class::cast)
                            .collect(Collectors.partitioningBy(node -> getController().getQuickHideFilters().stream()
                                            .filter(AbstractFilter::isActive)
                                            .anyMatch(filter -> filter.getDescription().equals(node.getDescription()))));

                    layoutStripeNodesHelper(hiddenPartition.get(true), hiddenPartition.get(false), minY, 0);
                    minY = maxY.get();
                }
            } else {
                hiddenPartition = stripeNodeMap.values().stream()
                        .collect(Collectors.partitioningBy(node -> getController().getQuickHideFilters().stream()
                                        .filter(AbstractFilter::isActive)
                                        .anyMatch(filter -> filter.getDescription().equals(node.getDescription()))));
                layoutStripeNodesHelper(hiddenPartition.get(true), hiddenPartition.get(false), 0, 0);
            }
            setCursor(null);
            requiresLayout = false;
        }
        layoutProjectionMap();
    }

    /**
     *
     * @param hiddenNodes the value of hiddenNodes
     * @param shownNodes  the value of shownNodes
     * @param minY        the value of minY
     * @param children    the value of children
     * @param xOffset     the value of xOffset
     *
     * @return the double
     */
    private double layoutStripeNodesHelper(List<EventStripeNode> hiddenNodes, List<EventStripeNode> shownNodes, double minY, final double xOffset) {

        hiddenNodes.forEach((EventStripeNode t) -> {
//            children.remove(t);
            t.setVisible(false);
            t.setManaged(false);
        });

        shownNodes.forEach((EventStripeNode t) -> {
//            if (false == children.contains(t)) {
//                children.add(t);
//            }
            t.setVisible(true);
            t.setManaged(true);
        });

        shownNodes.sort(Comparator.comparing(EventStripeNode::getStartMillis));
        return layoutStripeNodes(shownNodes, minY, xOffset);
    }

    /**
     *
     * @param hiddenNodes the value of hiddenNodes
     * @param shownNodes  the value of shownNodes
     * @param minY        the value of minY
     * @param children    the value of children
     * @param xOffset     the value of xOffset
     *
     * @return the double
     */
    private double layoutClusterNodesHelper(List<EventClusterNode> hiddenNodes, List<EventClusterNode> shownNodes, double minY, final double xOffset) {

        hiddenNodes.forEach((EventClusterNode t) -> {
//            children.remove(t);
            t.setVisible(false);
            t.setManaged(false);
        });

        shownNodes.forEach((EventClusterNode t) -> {
//            if (false == children.contains(t)) {
//                children.add(t);
//            }
            t.setVisible(true);
            t.setManaged(true);
        });

        shownNodes.sort(Comparator.comparing(EventClusterNode::getStartMillis));
        return layoutClusterNodes(shownNodes, minY, xOffset);
    }

    @Override
    protected synchronized void seriesAdded(Series<DateTime, EventCluster> series, int i) {
        for (int j = 0; j < series.getData().size(); j++) {
            dataItemAdded(series, j, series.getData().get(j));
        }
        seriesList.add(series);
        requiresLayout = true;
    }

    @Override
    protected synchronized void seriesRemoved(Series<DateTime, EventCluster> series) {
        for (int j = 0; j < series.getData().size(); j++) {
            dataItemRemoved(series.getData().get(j), series);
        }
        seriesList.remove(series);
        requiresLayout = true;
    }

    ReadOnlyDoubleProperty maxVScrollProperty() {
        return maxY.getReadOnlyProperty();
    }
    Function<EventClusterNode, Stream<EventBundleNodeBase<?, ?, ?>>> clusterFlattener =
            new Function<EventClusterNode, Stream<EventBundleNodeBase<?, ?, ?>>>() {
                @Override
                public Stream<EventBundleNodeBase<?, ?, ?>> apply(EventClusterNode node) {
                    return Stream.concat(
                            Stream.of(node),
                            node.getSubNodes().stream().flatMap(stripeFlattener::apply));
                }
            };

    Function<EventStripeNode, Stream<EventBundleNodeBase<?, ?, ?>>> stripeFlattener =
            new Function<EventStripeNode, Stream<EventBundleNodeBase<?, ?, ?>>>() {

                @Override
                public Stream<EventBundleNodeBase<?, ?, ?>> apply(EventStripeNode node) {
                    return Stream.concat(
                            Stream.of(node),
                            node.getSubNodes().stream().flatMap(clusterFlattener::apply));
                }
            };

    Iterable<EventBundleNodeBase<?, ?, ?>> getNodes(Predicate<EventBundleNodeBase<?, ?, ?>> p) {

        return stripeNodeMap.values().stream()
                .flatMap(stripeFlattener)
                .filter(p).collect(Collectors.toList());
    }

    Iterable<EventBundleNodeBase<?, ?, ?>> getAllNodes() {
        return getNodes(x -> true);
    }

    synchronized void setVScroll(double d) {
        final double h = maxY.get() - (getHeight() * .9);
        nodeGroup.setTranslateY(-d * h);
    }

    private void clearGuideLine() {
        getChartChildren().remove(guideLine);
        guideLine = null;
    }

    /**
     * layout the nodes in the given list, starting form the given minimum y
     * coordinate.
     *
     * @param nodes
     * @param minY
     */
    private synchronized double layoutStripeNodes(final Collection< EventStripeNode> nodes, final double minY, final double xOffset) {
        //hash map from y value to right most occupied x value.  This tells you for a given 'pixel row' what is the first avaialable slot
        Map<Integer, Double> maxXatY = new HashMap<>();
        double localMax = minY;
        //for each node size it and position it in first available slot
        for (EventStripeNode stripeNode : nodes) {

            stripeNode.setDescriptionVisibility(descrVisibility.get());
            double rawDisplayPosition = getXAxis().getDisplayPosition(new DateTime(stripeNode.getStartMillis()));

            //position of start and end according to range of axis
            double startX = rawDisplayPosition - xOffset;
            double layoutNodesResultHeight = 0;
            double span = 0;

            List<Double> spanWidths = new ArrayList<>();
            double x = getXAxis().getDisplayPosition(new DateTime(stripeNode.getStartMillis()));;
            double x2;
            Iterator<EventCluster> ranges = stripeNode.getEventStripe().getClusters().iterator();
            EventCluster cluster = ranges.next();
            do {
                x2 = getXAxis().getDisplayPosition(new DateTime(cluster.getEndMillis()));
                double clusterSpan = x2 - x;
                span += clusterSpan;
                spanWidths.add(clusterSpan);
                if (ranges.hasNext()) {
                    cluster = ranges.next();
                    x = getXAxis().getDisplayPosition(new DateTime(cluster.getStartMillis()));
                    double gapSpan = x - x2;
                    span += gapSpan;
                    spanWidths.add(gapSpan);
                    if (ranges.hasNext() == false) {
                        x2 = getXAxis().getDisplayPosition(new DateTime(cluster.getEndMillis()));
                        clusterSpan = x2 - x;
                        span += clusterSpan;
                        spanWidths.add(clusterSpan);
                    }
                }

            } while (ranges.hasNext());

            stripeNode.setSpanWidths(spanWidths);

            if (truncateAll.get()) { //if truncate option is selected limit width of description label
                stripeNode.setDescriptionWidth(Math.max(span, truncateWidth.get()));
            } else { //else set it unbounded
                stripeNode.setDescriptionWidth(USE_PREF_SIZE);//20 + new Text(tlNode.getDisplayedDescription()).getLayoutBounds().getWidth());
            }
            List<EventClusterNode> subNodes = stripeNode.getSubNodes();
            if (subNodes.isEmpty() == false) {
                Map<Boolean, List<EventClusterNode>> hiddenPartition = subNodes.stream()
                        .collect(Collectors.partitioningBy(testNode -> getController().getQuickHideFilters().stream()
                                        .filter(AbstractFilter::isActive)
                                        .anyMatch(filter -> filter.getDescription().equals(testNode.getDescription()))));

                layoutNodesResultHeight = layoutClusterNodesHelper(hiddenPartition.get(true), hiddenPartition.get(false), minY, rawDisplayPosition);
            }

            stripeNode.autosize(); //compute size of tlNode based on constraints and event data

            //get position of right edge of node ( influenced by description label)
            double xRight = startX + stripeNode.getWidth();

            //get the height of the node
            final double h = layoutNodesResultHeight == 0 ? stripeNode.getHeight() : layoutNodesResultHeight ;
            
            //initial test position
            double yPos = minY;
            double yPos2 = yPos + h;

            if (oneEventPerRow.get()) {
                // if onePerRow, just put it at end
                yPos = (localMax + 2);
                yPos2 = yPos + h;

            } else {
                boolean overlapping = true;
                while (overlapping) {
                    //loop through y values looking for available slot.

                    overlapping = false;
                    //check each pixel from bottom to top.
                    for (double y = yPos2; y >= yPos; y--) {
                        final Double maxX = maxXatY.get((int) y);
                        if (maxX != null && maxX >= startX - 4) {
                            //if that pixel is already used
                            //jump top to this y value and repeat until free slot is found.
                            overlapping = true;
                            yPos = y + 4;
                            yPos2 = yPos + h;
                            break;
                        }
                    }
                }
                //mark used y values
                for (double y = yPos; y <= yPos2; y++) {
                    maxXatY.put((int) y, xRight);
                }
            }
            localMax = Math.max(yPos2, localMax);

            Timeline tm = new Timeline(new KeyFrame(Duration.seconds(1.0),
                    new KeyValue(stripeNode.layoutXProperty(), startX),
                    new KeyValue(stripeNode.layoutYProperty(), yPos)));

            tm.play();
        }
        maxY.set(Math.max(maxY.get(), localMax));
        return localMax - minY;
    }

    /**
     * layout the nodes in the given list, starting form the given minimum y
     * coordinate.
     *
     * @param nodes
     * @param minY
     */
    private synchronized double layoutClusterNodes(final Collection< EventClusterNode> nodes, final double minY, final double xOffset) {
        //hash map from y value to right most occupied x value.  This tells you for a given 'row' what is the first avaialable slot
        Map<Integer, Double> maxXatY = new HashMap<>();
        double localMax = minY;
        //for each node lay size it and position it in first available slot

        for (EventClusterNode clusterNode : nodes) {

            clusterNode.setDescriptionVisibility(descrVisibility.get());
            double rawDisplayPosition = getXAxis().getDisplayPosition(new DateTime(clusterNode.getStartMillis()));

            //position of start and end according to range of axis
            double startX = rawDisplayPosition - xOffset;
            double layoutNodesResultHeight = 0;

            double span = 0;

            List<Double> spanWidths = new ArrayList<>();
            double x = getXAxis().getDisplayPosition(new DateTime(clusterNode.getStartMillis()));;
            double x2;
            Iterator<EventCluster> ranges = clusterNode.getEventCluster().getClusters().iterator();
            EventCluster range = ranges.next();
            do {
                x2 = getXAxis().getDisplayPosition(new DateTime(range.getEndMillis()));
                double clusterSpan = x2 - x;
                span += clusterSpan;
                spanWidths.add(clusterSpan);
                if (ranges.hasNext()) {
                    range = ranges.next();
                    x = getXAxis().getDisplayPosition(new DateTime(range.getStartMillis()));
                    double gapSpan = x - x2;
                    span += gapSpan;
                    spanWidths.add(gapSpan);
                    if (ranges.hasNext() == false) {
                        x2 = getXAxis().getDisplayPosition(new DateTime(range.getEndMillis()));
                        clusterSpan = x2 - x;
                        span += clusterSpan;
                        spanWidths.add(clusterSpan);
                    }
                }

            } while (ranges.hasNext());

            clusterNode.setSpanWidths(spanWidths);

            if (truncateAll.get()) { //if truncate option is selected limit width of description label
                clusterNode.setDescriptionWidth(Math.max(span, truncateWidth.get()));
            } else { //else set it unbounded
                clusterNode.setDescriptionWidth(USE_PREF_SIZE);//20 + new Text(tlNode.getDisplayedDescription()).getLayoutBounds().getWidth());
            }

            List<EventStripeNode> subNodes = clusterNode.getSubNodes();
            if (subNodes.isEmpty() == false) {
                Map<Boolean, List<EventStripeNode>> hiddenPartition = subNodes.stream()
                        .collect(Collectors.partitioningBy(testNode -> getController().getQuickHideFilters().stream()
                                        .filter(AbstractFilter::isActive)
                                        .anyMatch(filter -> filter.getDescription().equals(testNode.getDescription()))));

                layoutNodesResultHeight = layoutStripeNodesHelper(hiddenPartition.get(true), hiddenPartition.get(false), minY, rawDisplayPosition);
            }

            clusterNode.autosize(); //compute size of tlNode based on constraints and event data

            //get position of right edge of node ( influenced by description label)
            double xRight = startX + clusterNode.getWidth();

            //get the height of the node
            final double h = layoutNodesResultHeight == 0 ? clusterNode.getHeight() : layoutNodesResultHeight ;
            //initial test position
            double yPos = minY;

            double yPos2 = yPos + h;

            if (oneEventPerRow.get()) {
                // if onePerRow, just put it at end
                yPos = (localMax + 2);
                yPos2 = yPos + h;

            } else {//else

                boolean overlapping = true;
                while (overlapping) {
                    //loop through y values looking for available slot.

                    overlapping = false;
                    //check each pixel from top to bottom.
                    for (double y = yPos2; y >= yPos; y--) {
                        final Double maxX = maxXatY.get((int) y);
                        if (maxX != null && maxX >= startX - 4) {
                            //if that pixel is already used
                            //jump top to this y value and repeat until free slot is found.
                            overlapping = true;
                            yPos = y + 4;
                            yPos2 = yPos + h;
                            break;
                        }
                    }
                }
                //mark used y values
                for (double y = yPos; y <= yPos2; y++) {
                    maxXatY.put((int) y, xRight);
                }
            }
            localMax = Math.max(yPos2, localMax);

            Timeline tm = new Timeline(new KeyFrame(Duration.seconds(1.0),
                    new KeyValue(clusterNode.layoutXProperty(), startX),
                    new KeyValue(clusterNode.layoutYProperty(), yPos)));

            tm.play();
        }
        maxY.set(Math.max(maxY.get(), localMax));
        return localMax - minY;
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

    private double getParentXForEpochMillis(Long epochMillis) {
        DateTime dateTime = new DateTime(epochMillis, TimeLineController.getJodaTimeZone());
        return getXAxis().localToParent(getXAxis().getDisplayPosition(dateTime), 0).getX();
    }

    /**
     * @return the filteredEvents
     */
    public FilteredEventsModel getFilteredEvents() {
        return filteredEvents;
    }

    Property<Boolean> alternateLayoutProperty() {
        return alternateLayout;
    }

    static private class DetailIntervalSelector extends IntervalSelector<DateTime> {

        DetailIntervalSelector(double x, double height, Axis<DateTime> axis, TimeLineController controller) {
            super(x, height, axis, controller);
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

        PlaceMarkerAction(MouseEvent clickEvent) {
            super(Bundle.EventDetailChart_chartContextMenu_placeMarker_name());

            setGraphic(new ImageView(MARKER)); // NON-NLS
            setEventHandler(actionEvent -> {
                if (guideLine == null) {
                    guideLine = new GuideLine(0, 0, 0, getHeight(), getXAxis());
                    guideLine.relocate(sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                    guideLine.endYProperty().bind(heightProperty().subtract(getXAxis().heightProperty().subtract(getXAxis().tickLengthProperty())));
                    getChartChildren().add(guideLine);
                    guideLine.setOnMouseClicked(mouseEvent -> {
                        if (mouseEvent.getButton() == MouseButton.SECONDARY) {
                            clearGuideLine();
                            mouseEvent.consume();
                        }
                    });
                } else {
                    guideLine.relocate(sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                }
            });
        }
    }

    class HideDescriptionAction extends Action {

        HideDescriptionAction(String description, DescriptionLoD descriptionLoD) {
            super("Hide");
            setGraphic(new ImageView(HIDE));
            setEventHandler((ActionEvent t) -> {
                final DescriptionFilter testFilter = new DescriptionFilter(
                        descriptionLoD,
                        description,
                        DescriptionFilter.FilterMode.EXCLUDE);

                DescriptionFilter descriptionFilter = getController().getQuickHideFilters().stream()
                        .filter(testFilter::equals)
                        .findFirst().orElseGet(() -> {
                            testFilter.selectedProperty().addListener(layoutInvalidationListener);
                            getController().getQuickHideFilters().add(testFilter);
                            return testFilter;
                        });
                descriptionFilter.setSelected(true);

            });
        }
    }

    class UnhideDescriptionAction extends Action {

        UnhideDescriptionAction(String description, DescriptionLoD descriptionLoD) {

            super("Unhide");
            setGraphic(new ImageView(SHOW));
            setEventHandler((ActionEvent t) ->
                    getController().getQuickHideFilters().stream()
                    .filter(descriptionFilter -> descriptionFilter.getDescriptionLoD().equals(descriptionLoD)
                            && descriptionFilter.getDescription().equals(description))
                    .forEach(descriptionfilter -> descriptionfilter.setSelected(false))
            );
        }
    }
}
