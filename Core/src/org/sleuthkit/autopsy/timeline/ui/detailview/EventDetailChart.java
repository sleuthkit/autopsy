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

import com.google.common.collect.Collections2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
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
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionGroup;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.Forward;
import org.sleuthkit.autopsy.timeline.datamodel.AggregateEvent;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
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
 * //TODO: refactor the projected lines to a separate class. -jm
 */
public final class EventDetailChart extends XYChart<DateTime, AggregateEvent> implements TimeLineChart<DateTime> {

    private static final int PROJECTED_LINE_Y_OFFSET = 5;

    private static final int PROJECTED_LINE_STROKE_WIDTH = 5;

    /**
     * true == layout each event type in its own band, false == mix all the
     * events together during layout
     */
    private final SimpleBooleanProperty bandByType = new SimpleBooleanProperty(false);

    // I don't like having these package visible, but it was the easiest way to
    private ContextMenu chartContextMenu;

    private TimeLineController controller;

    private FilteredEventsModel filteredEvents;

    /**
     * how much detail of the description to show in the ui
     */
    private final SimpleObjectProperty<DescriptionVisibility> descrVisibility = new SimpleObjectProperty<>(DescriptionVisibility.SHOWN);

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
    private final InvalidationListener layoutInvalidationListener = (
            Observable o) -> {
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
     * the group that all event nodes are added to. This facilitates scrolling
     * by allowing a single translation of this group.
     */
    private final Group nodeGroup = new Group();

    /**
     * map from event to node
     */
    private final Map<AggregateEvent, AggregateEventNode> nodeMap = new TreeMap<>((
            AggregateEvent o1,
            AggregateEvent o2) -> {
                int comp = Long.compare(o1.getSpan().getStartMillis(), o2.getSpan().getStartMillis());
                if (comp != 0) {
                    return comp;
                } else {
                    return Comparator.comparing(AggregateEvent::hashCode).compare(o1, o2);
                }
            });

    /**
     * true == enforce that no two events can share the same 'row', leading to
     * sparser but possibly clearer layout. false == put unrelated events in the
     * same 'row', creating a denser more compact layout
     */
    private final SimpleBooleanProperty oneEventPerRow = new SimpleBooleanProperty(false);

    private final ObservableMap<AggregateEventNode, Line> projectionMap = FXCollections.observableHashMap();

    /**
     * flag indicating whether this chart actually needs a layout pass
     */
    @GuardedBy(value = "this")
    private boolean requiresLayout = true;

    final ObservableList<AggregateEventNode> selectedNodes;

    /**
     * list of series of data added to this chart TODO: replace this with a map
     * from name to series? -jm
     */
    private final ObservableList<Series<DateTime, AggregateEvent>> seriesList
            = FXCollections.<Series<DateTime, AggregateEvent>>observableArrayList();

    private final ObservableList<Series<DateTime, AggregateEvent>> sortedSeriesList = seriesList
            .sorted((s1, s2) -> {
                final List<String> collect = EventType.allTypes.stream().map(EventType::getDisplayName).collect(Collectors.toList());
                return Integer.compare(collect.indexOf(s1.getName()), collect.indexOf(s2.getName()));
            });

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

    EventDetailChart(DateAxis dateAxis, final Axis<AggregateEvent> verticalAxis, ObservableList<AggregateEventNode> selectedNodes) {
        super(dateAxis, verticalAxis);
        dateAxis.setAutoRanging(false);

        //yAxis.setVisible(false);//TODO: why doesn't this hide the vertical axis, instead we have to turn off all parts individually? -jm
        verticalAxis.setTickLabelsVisible(false);
        verticalAxis.setTickMarkVisible(false);

        setLegendVisible(false);
        setPadding(Insets.EMPTY);
        setAlternativeColumnFillVisible(true);

        //all nodes are added to nodeGroup to facilitate scrolling rather than to getPlotChildren() directly
        getPlotChildren().add(nodeGroup);

        //bind listener to events that should trigger layout
        widthProperty().addListener(layoutInvalidationListener);
        heightProperty().addListener(layoutInvalidationListener);
//        boundsInLocalProperty().addListener(layoutInvalidationListener);
        bandByType.addListener(layoutInvalidationListener);
        oneEventPerRow.addListener(layoutInvalidationListener);
        truncateAll.addListener(layoutInvalidationListener);
        truncateWidth.addListener(layoutInvalidationListener);
        descrVisibility.addListener(layoutInvalidationListener);

        //this is needed to allow non circular binding of the guideline and timerangRect heights to the height of the chart
        boundsInLocalProperty().addListener((Observable observable) -> {
            setPrefHeight(boundsInLocalProperty().get().getHeight());
        });

        //set up mouse listeners
        final EventHandler<MouseEvent> clickHandler = (MouseEvent clickEvent) -> {
            if (chartContextMenu != null) {
                chartContextMenu.hide();
            }
            if (clickEvent.getButton() == MouseButton.SECONDARY && clickEvent.isStillSincePress()) {

                chartContextMenu = ActionUtils.createContextMenu(Arrays.asList(new Action(
                        NbBundle.getMessage(this.getClass(), "EventDetailChart.chartContextMenu.placeMarker.name")) {
                            {
                                setGraphic(new ImageView(new Image("/org/sleuthkit/autopsy/timeline/images/marker.png", 16, 16, true, true, true))); // NON-NLS
                                setEventHandler((ActionEvent t) -> {
                                    if (guideLine == null) {
                                        guideLine = new GuideLine(0, 0, 0, getHeight(), dateAxis);
                                        guideLine.relocate(clickEvent.getX(), 0);
                                        guideLine.endYProperty().bind(heightProperty().subtract(dateAxis.heightProperty().subtract(dateAxis.tickLengthProperty())));

                                        getChartChildren().add(guideLine);

                                        guideLine.setOnMouseClicked((MouseEvent event) -> {
                                            if (event.getButton() == MouseButton.SECONDARY) {
                                                clearGuideLine();
                                                event.consume();
                                            }
                                        });
                                    } else {
                                        guideLine.relocate(clickEvent.getX(), 0);
                                    }
                                });
                            }

                        }, new ActionGroup(
                                NbBundle.getMessage(this.getClass(), "EventDetailChart.contextMenu.zoomHistory.name"),
                                new Back(controller),
                                new Forward(controller))));
                chartContextMenu.setAutoHide(true);
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

        projectionMap.addListener((MapChangeListener.Change<? extends AggregateEventNode, ? extends Line> change) -> {
            final Line valueRemoved = change.getValueRemoved();
            if (valueRemoved != null) {
                getChartChildren().removeAll(valueRemoved);
            }
            final Line valueAdded = change.getValueAdded();
            if (valueAdded != null) {
                getChartChildren().add(valueAdded);
            }
        });

        this.selectedNodes = selectedNodes;
        this.selectedNodes.addListener((
                ListChangeListener.Change<? extends AggregateEventNode> c) -> {
                    while (c.next()) {
                        c.getRemoved().forEach((AggregateEventNode t) -> {
                            projectionMap.remove(t);
                        });
                        c.getAddedSubList().forEach((AggregateEventNode t) -> {
                            Line line = new Line(dateAxis.localToParent(dateAxis.getDisplayPosition(new DateTime(t.getEvent().getSpan().getStartMillis(), TimeLineController.getJodaTimeZone())), 0).getX(), dateAxis.getLayoutY() + PROJECTED_LINE_Y_OFFSET,
                                    dateAxis.localToParent(dateAxis.getDisplayPosition(new DateTime(t.getEvent().getSpan().getEndMillis(), TimeLineController.getJodaTimeZone())), 0).getX(), dateAxis.getLayoutY() + PROJECTED_LINE_Y_OFFSET
                            );
                            line.setStroke(t.getEvent().getType().getColor().deriveColor(0, 1, 1, .5));
                            line.setStrokeWidth(PROJECTED_LINE_STROKE_WIDTH);
                            line.setStrokeLineCap(StrokeLineCap.ROUND);
                            projectionMap.put(t, line);
                        });

                    }

                    this.controller.selectEventIDs(selectedNodes.stream()
                            .flatMap((AggregateEventNode aggNode) -> aggNode.getEvent().getEventIDs().stream())
                            .collect(Collectors.toList()));
                });

        requestChartLayout();
    }

    @Override
    public void clearIntervalSelector() {
        getChartChildren().remove(intervalSelector);
        intervalSelector = null;
    }

    public synchronized SimpleBooleanProperty getBandByType() {
        return bandByType;
    }

    @Override
    public synchronized void setController(TimeLineController controller) {
        this.controller = controller;
        setModel(this.controller.getEventsModel());
    }

    @Override
    public void setModel(FilteredEventsModel filteredEvents) {

        if (this.filteredEvents != filteredEvents) {
            filteredEvents.zoomParamtersProperty().addListener(o -> {
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

    public synchronized SimpleBooleanProperty getOneEventPerRow() {
        return oneEventPerRow;
    }

    public synchronized SimpleBooleanProperty getTruncateAll() {
        return truncateAll;
    }

    synchronized void setEventOnePerRow(Boolean t1) {
        oneEventPerRow.set(t1);
    }

    synchronized void setTruncateAll(Boolean t1) {
        truncateAll.set(t1);

    }

    @Override
    protected synchronized void dataItemAdded(Series<DateTime, AggregateEvent> series, int i, Data<DateTime, AggregateEvent> data) {
        final AggregateEvent aggEvent = data.getYValue();
        AggregateEventNode eventNode = nodeMap.get(aggEvent);
        if (eventNode == null) {
            eventNode = new AggregateEventNode(aggEvent, null, this);

            eventNode.setLayoutX(getXAxis().getDisplayPosition(new DateTime(aggEvent.getSpan().getStartMillis())));
            data.setNode(eventNode);
            nodeMap.put(aggEvent, eventNode);
            nodeGroup.getChildren().add(eventNode);
            requiresLayout = true;
        }
    }

    @Override
    protected synchronized void dataItemChanged(Data<DateTime, AggregateEvent> data) {
        //TODO: can we use this to help with local detail level adjustment -jm
        throw new UnsupportedOperationException("Not supported yet."); // NON-NLS //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected synchronized void dataItemRemoved(Data<DateTime, AggregateEvent> data, Series<DateTime, AggregateEvent> series) {
        nodeMap.remove(data.getYValue());
        nodeGroup.getChildren().remove(data.getNode());
        data.setNode(null);
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
            double minY = 0;

            maxY.set(0.0);

            if (bandByType.get() == false) {

                ObservableList<Node> nodes = FXCollections.observableArrayList(nodeMap.values());
                FXCollections.sort(nodes, new StartTimeComparator());
                layoutNodes(nodes, minY, 0);
//                layoutNodes(new ArrayList<>(nodeMap.values()), minY, 0);
            } else {
                for (Series<DateTime, AggregateEvent> s : sortedSeriesList) {
                    ObservableList<Node> nodes = FXCollections.observableArrayList(Collections2.transform(s.getData(), Data::getNode));

                    FXCollections.sort(nodes, new StartTimeComparator());
                    layoutNodes(nodes.filtered((Node n) -> n != null), minY, 0);
                    minY = maxY.get();
                }
            }
            setCursor(null);
            requiresLayout = false;
        }
        layoutProjectionMap();
    }

    @Override
    protected synchronized void seriesAdded(Series<DateTime, AggregateEvent> series, int i) {
        for (int j = 0; j < series.getData().size(); j++) {
            dataItemAdded(series, j, series.getData().get(j));
        }
        seriesList.add(series);
        requiresLayout = true;
    }

    @Override
    protected synchronized void seriesRemoved(Series<DateTime, AggregateEvent> series) {
        for (int j = 0; j < series.getData().size(); j++) {
            dataItemRemoved(series.getData().get(j), series);
        }
        seriesList.remove(series);
        requiresLayout = true;
    }

    synchronized SimpleObjectProperty<DescriptionVisibility> getDescrVisibility() {
        return descrVisibility;
    }

    synchronized ReadOnlyDoubleProperty getMaxVScroll() {
        return maxY.getReadOnlyProperty();
    }

    Iterable<AggregateEventNode> getNodes(Predicate<AggregateEventNode> p) {
        List<AggregateEventNode> nodes = new ArrayList<>();

        for (AggregateEventNode node : nodeMap.values()) {
            checkNode(node, p, nodes);
        }

        return nodes;
    }

    Iterable<AggregateEventNode> getAllNodes() {
        return getNodes(x -> true);
    }

    synchronized SimpleDoubleProperty getTruncateWidth() {
        return truncateWidth;
    }

    synchronized void setVScroll(double d) {
        final double h = maxY.get() - (getHeight() * .9);
        nodeGroup.setTranslateY(-d * h);
    }

    private static void checkNode(AggregateEventNode node, Predicate<AggregateEventNode> p, List<AggregateEventNode> nodes) {
        if (node != null) {
            if (p.test(node)) {
                nodes.add(node);
            }
            for (Node n : node.getSubNodePane().getChildrenUnmodifiable()) {
                checkNode((AggregateEventNode) n, p, nodes);
            }
        }
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
    private synchronized double layoutNodes(final List<Node> nodes, final double minY, final double xOffset) {
        //hash map from y value to right most occupied x value.  This tells you for a given 'row' what is the first avaialable slot
        Map<Integer, Double> maxXatY = new HashMap<>();
        double localMax = minY;
        //for each node lay size it and position it in first available slot
        for (Node n : nodes) {
            final AggregateEventNode tlNode = (AggregateEventNode) n;
            tlNode.setDescriptionVisibility(descrVisibility.get());

            AggregateEvent ie = tlNode.getEvent();
            final double rawDisplayPosition = getXAxis().getDisplayPosition(new DateTime(ie.getSpan().getStartMillis()));
            //position of start and end according to range of axis
            double xPos = rawDisplayPosition - xOffset;
            double layoutNodesResultHeight = 0;
            if (tlNode.getSubNodePane().getChildren().isEmpty() == false) {
                FXCollections.sort(tlNode.getSubNodePane().getChildren(), new StartTimeComparator());
                layoutNodesResultHeight = layoutNodes(tlNode.getSubNodePane().getChildren(), 0, rawDisplayPosition);
            }
            double xPos2 = getXAxis().getDisplayPosition(new DateTime(ie.getSpan().getEndMillis())) - xOffset;
            double span = xPos2 - xPos;

            //size timespan border
            tlNode.setSpanWidth(span);
            if (truncateAll.get()) { //if truncate option is selected limit width of description label
                tlNode.setDescriptionWidth(Math.max(span, truncateWidth.get()));
            } else { //else set it unbounded
                tlNode.setDescriptionWidth(USE_PREF_SIZE);//20 + new Text(tlNode.getDisplayedDescription()).getLayoutBounds().getWidth());
            }
            tlNode.autosize(); //compute size of tlNode based on constraints and event data

            //get position of right edge of node ( influenced by description label)
            double xRight = xPos + tlNode.getWidth();

            //get the height of the node
            final double h = layoutNodesResultHeight == 0 ? tlNode.getHeight() : layoutNodesResultHeight + DEFAULT_ROW_HEIGHT;
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
                    //check each pixel from bottom to top.
                    for (double y = yPos2; y >= yPos; y--) {
                        final Double maxX = maxXatY.get((int) y);
                        if (maxX != null && maxX >= xPos - 4) {
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
                    new KeyValue(tlNode.layoutXProperty(), xPos),
                    new KeyValue(tlNode.layoutYProperty(), yPos)));

            tm.play();
//            tlNode.relocate(xPos, yPos);
        }
        maxY.set(Math.max(maxY.get(), localMax));
        return localMax - minY;
    }
    private static final int DEFAULT_ROW_HEIGHT = 24;

    private void layoutProjectionMap() {
        for (final Map.Entry<AggregateEventNode, Line> entry : projectionMap.entrySet()) {
            final AggregateEventNode aggNode = entry.getKey();
            final Line line = entry.getValue();

            line.setStartX(getParentXForValue(new DateTime(aggNode.getEvent().getSpan().getStartMillis(), TimeLineController.getJodaTimeZone())));
            line.setEndX(getParentXForValue(new DateTime(aggNode.getEvent().getSpan().getEndMillis(), TimeLineController.getJodaTimeZone())));
            line.setStartY(getXAxis().getLayoutY() + PROJECTED_LINE_Y_OFFSET);
            line.setEndY(getXAxis().getLayoutY() + PROJECTED_LINE_Y_OFFSET);
        }
    }

    private double getParentXForValue(DateTime dt) {
        return getXAxis().localToParent(getXAxis().getDisplayPosition(dt), 0).getX();
    }

    /**
     * @return the controller
     */
    public TimeLineController getController() {
        return controller;
    }

    /**
     * @return the filteredEvents
     */
    public FilteredEventsModel getFilteredEvents() {
        return filteredEvents;
    }

    /**
     * @return the chartContextMenu
     */
    public ContextMenu getChartContextMenu() {
        return chartContextMenu;
    }

    private static class StartTimeComparator implements Comparator<Node> {

        @Override
        public int compare(Node n1, Node n2) {

            if (n1 == null) {
                return 1;
            } else if (n2 == null) {
                return -1;
            } else {

                return Long.compare(((AggregateEventNode) n1).getEvent().getSpan().getStartMillis(),
                        (((AggregateEventNode) n2).getEvent().getSpan().getStartMillis()));
            }
        }

    }

    private class DetailIntervalSelector extends IntervalSelector<DateTime> {

        public DetailIntervalSelector(double x, double height, Axis<DateTime> axis, TimeLineController controller) {
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

    synchronized void setRequiresLayout(boolean b) {
        requiresLayout = true;
    }

    /**
     * make this accessible to AggregateEventNode
     */
    @Override
    protected void requestChartLayout() {
        super.requestChartLayout();
    }
}
