/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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

import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
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
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;

/**
 *
 */
public final class PinnedEventsChart extends XYChart<DateTime, TimeLineEvent> implements DetailsChart {

    private static final String styleSheet = GuideLine.class.getResource("EventsDetailsChart.css").toExternalForm(); //NON-NLS
    private static final int MINIMUM_EVENT_NODE_GAP = 4;
    private static final int MINIMUM_ROW_HEIGHT = 24;

    private static EventNodeBase<?> createNode(PinnedEventsChart chart, TimeLineEvent event) {
        if (event instanceof SingleEvent) {
            return new SingleEventNode(chart, (SingleEvent) event, null);
        } else if (event instanceof EventCluster) {
            return new EventClusterNode(chart, (EventCluster) event, null);
        } else {
            return new EventStripeNode(chart, (EventStripe) event, null);
        }
    }
    private Map<TimeLineEvent, EventNodeBase<?>> eventMap = new HashMap<>();
    private ContextMenu chartContextMenu;

    private final TimeLineController controller;
    private final FilteredEventsModel filteredEvents;
    /**
     * the group that all event nodes are added to. This facilitates scrolling
     * by allowing a single translation of this group.
     */
    private final Group nodeGroup = new Group();
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<TimeLineEvent> events = FXCollections.observableArrayList();
    private final ObservableList< EventNodeBase<?>> eventNodes = FXCollections.observableArrayList();
    private final ObservableList< EventNodeBase<?>> sortedEventNodes = eventNodes.sorted(Comparator.comparing(EventNodeBase::getStartMillis));
    private double descriptionWidth;

    /**
     * the maximum y value used so far during the most recent layout pass
     */
    private final ReadOnlyDoubleWrapper maxY = new ReadOnlyDoubleWrapper(0.0);
    private final ObservableList<EventNodeBase<?>> selectedNodes;
    private final DetailViewLayoutSettings layoutSettings;
    /**
     * listener that triggers chart layout pass
     */
    private final InvalidationListener layoutInvalidationListener = (Observable o) -> {
        layoutPlotChildren();
    };

    /**
     *
     * @param controller     the value of controller
     * @param dateAxis       the value of dateAxis
     * @param verticalAxis   the value of verticalAxis
     * @param selectedNodes1 the value of selectedNodes1
     */
    PinnedEventsChart(TimeLineController controller, DateAxis dateAxis, final Axis<TimeLineEvent> verticalAxis, ObservableList<EventNodeBase<?>> selectedNodes, DetailViewLayoutSettings layoutSettings) {
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
        final Series<DateTime, TimeLineEvent> series = new Series<>();
        setData(FXCollections.observableArrayList());
        getData().add(series);
//        //add listener for events that should trigger layout
        layoutSettings.bandByTypeProperty().addListener(layoutInvalidationListener);
        layoutSettings.oneEventPerRowProperty().addListener(layoutInvalidationListener);
        layoutSettings.truncateAllProperty().addListener(layoutInvalidationListener);
        layoutSettings.truncateAllProperty().addListener(layoutInvalidationListener);
        layoutSettings.descrVisibilityProperty().addListener(layoutInvalidationListener);
        getController().getQuickHideFilters().addListener(layoutInvalidationListener);
//        getController().getQuickHideFilters().addListener(layoutInvalidationListener);

//        //this is needed to allow non circular binding of the guideline and timerangeRect heights to the height of the chart
//        //TODO: seems like a hack, can we remove? -jm
//        boundsInLocalProperty().addListener((Observable observable) -> {
//            setPrefHeight(boundsInLocalProperty().get().getHeight());
//        });
//        ChartDragHandler<DateTime, EventDetailsChart> chartDragHandler = new ChartDragHandler<>(this);
//        setOnMousePressed(chartDragHandler);
//        setOnMouseReleased(chartDragHandler);
//        setOnMouseDragged(chartDragHandler);
//
//        setOnMouseClicked(new MouseClickedHandler<>(this));
        controller.getPinnedEvents().addListener((SetChangeListener.Change<? extends TimeLineEvent> change) -> {
            if (change.wasAdded()) {
                TimeLineEvent elementAdded = change.getElementAdded();
                Data<DateTime, TimeLineEvent> data1 = new Data<>(new DateTime(elementAdded.getStartMillis()), elementAdded);
                series.getData().add(data1);
                addDataItem(data1);
            }
            if (change.wasRemoved()) {
                TimeLineEvent elementRemoved = change.getElementRemoved();
                Data<DateTime, TimeLineEvent> data1 = new Data<>(new DateTime(elementRemoved.getStartMillis()), elementRemoved);
                series.getData().removeIf(t -> elementRemoved.equals(t.getYValue()));
                removeDataItem(data1);
            }

            requestChartLayout();
        });
        this.selectedNodes = selectedNodes;
    }

    @Override
    public ContextMenu getChartContextMenu() {
        return chartContextMenu;
    }

    @Override
    public ContextMenu getChartContextMenu(MouseEvent clickEvent) throws MissingResourceException {
        if (chartContextMenu != null) {
            chartContextMenu.hide();
        }

        chartContextMenu = ActionUtils.createContextMenu(Arrays.asList(//new EventDetailsChart.PlaceMarkerAction(clickEvent),
                TimeLineChart.newZoomHistoyActionGroup(controller)));
        chartContextMenu.setAutoHide(true);
        return chartContextMenu;
    }
//    final ObservableList<EventNodeBase<?>> selectedNodes;

    @Override
    public Node asNode() {
        return this;
    }

    @Override
    public ObservableList<EventStripe> getEventStripes() {
        return FXCollections.emptyObservableList();
    }

    @Override
    public IntervalSelector<? extends DateTime> getIntervalSelector() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends DateTime> newIntervalSelector) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public IntervalSelector<DateTime> newIntervalSelector() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearIntervalSelector() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TimeLineController getController() {
        return controller;
    }

    @Override
    public void requestTimelineChartLayout() {
        requestChartLayout();
    }

    @Override
    public ObservableList<EventNodeBase<?>> getSelectedNodes() {
        return selectedNodes;
    }

    @Override
    public double layoutEventBundleNodes(final Collection<? extends EventNodeBase<?>> nodes, final double minY) {
        // map from y-ranges to maximum x
        TreeRangeMap<Double, Double> maxXatY = TreeRangeMap.create();

        // maximum y values occupied by any of the given nodes,  updated as nodes are layed out.
        double localMax = minY;

        //for each node do a recursive layout to size it and then position it in first available slot
        for (EventNodeBase<?> eventNode : nodes) {
            //is the node hiden by a quick hide filter?

            layoutBundleHelper(eventNode);
            //get computed height and width
            double h = eventNode.getBoundsInLocal().getHeight();
            double w = eventNode.getBoundsInLocal().getWidth();
            //get left and right x coords from axis plus computed width
            double xLeft = getXForEpochMillis(eventNode.getStartMillis()) - eventNode.getLayoutXCompensation();
            double xRight = xLeft + w + MINIMUM_EVENT_NODE_GAP;

            //initial test position
            double yTop = (layoutSettings.getOneEventPerRow())
                    ? (localMax + MINIMUM_EVENT_NODE_GAP)// if onePerRow, just put it at end
                    : computeYTop(minY, h, maxXatY, xLeft, xRight);

            localMax = Math.max(yTop + h, localMax);

            if ((xLeft != eventNode.getLayoutX()) || (yTop != eventNode.getLayoutY())) {
                //animate node to new position
                eventNode.animateTo(xLeft, yTop);
            }

        }
        return localMax; //return new max
    }

    @Override
    protected void dataItemAdded(Series<DateTime, TimeLineEvent> series, int itemIndex, Data<DateTime, TimeLineEvent> item) {
    }

    @Override
    protected void dataItemRemoved(Data<DateTime, TimeLineEvent> item, Series<DateTime, TimeLineEvent> series) {
    }

    @Override
    protected void dataItemChanged(Data<DateTime, TimeLineEvent> item) {
    }

    @Override
    protected void seriesAdded(Series<DateTime, TimeLineEvent> series, int seriesIndex) {
    }

    @Override
    protected void seriesRemoved(Series<DateTime, TimeLineEvent> series) {
    }

    @Override
    protected void layoutPlotChildren() {
        setCursor(Cursor.WAIT);
        maxY.set(0);

//        //These don't change during a layout pass and are expensive to compute per node.  So we do it once at the start
//        activeQuickHidefilters = getController().getQuickHideFilters().stream()
//                .filter(AbstractFilter::isActive)
//                .map(DescriptionFilter::getDescription)
//                .collect(Collectors.toSet());
        //This dosn't change during a layout pass and is expensive to compute per node.  So we do it once at the start
        descriptionWidth = layoutSettings.getTruncateAll() ? layoutSettings.getTruncateWidth() : USE_PREF_SIZE;

        if (layoutSettings.getBandByType()) {
            sortedEventNodes.stream()
                    .collect(Collectors.groupingBy(EventNodeBase<?>::getEventType)).values()
                    .forEach(inputNodes -> maxY.set(layoutEventBundleNodes(inputNodes, maxY.get())));
        } else {
            maxY.set(layoutEventBundleNodes(sortedEventNodes.sorted(Comparator.comparing(EventNodeBase<?>::getStartMillis)), 0));
        }
        setCursor(null);
    }

    /**
     * expose as protected
     */
    @Override
    protected void requestChartLayout() {
        super.requestChartLayout();
    }

    public synchronized void setVScroll(double vScrollValue) {
        nodeGroup.setTranslateY(-vScrollValue);
    }

    public ReadOnlyDoubleProperty maxVScrollProperty() {
        return maxY.getReadOnlyProperty();
    }

    /**
     * add a dataitem to this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param data
     */
    void addDataItem(Data<DateTime, TimeLineEvent> data) {
        final TimeLineEvent event = data.getYValue();

        EventNodeBase<?> eventNode = createNode(PinnedEventsChart.this, event);
        eventMap.put(event, eventNode);
        Platform.runLater(() -> {
            events.add(event);
            eventNodes.add(eventNode);
            nodeGroup.getChildren().add(eventNode);
            data.setNode(eventNode);

        });
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

        return sortedEventNodes.stream()
                .flatMap(stripeFlattener)
                .filter(p).collect(Collectors.toList());
    }

    /**
     * remove a data item from this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param data
     */
    void removeDataItem(Data<DateTime, TimeLineEvent> data) {
        EventNodeBase<?> removedNode = eventMap.remove(data.getYValue());
        Platform.runLater(() -> {
            events.removeAll(data.getYValue());
            eventNodes.removeAll(removedNode);
            nodeGroup.getChildren().removeAll(removedNode);
            data.setNode(null);
        });
    }

    private double getXForEpochMillis(Long millis) {
        DateTime dateTime = new DateTime(millis);
        return getXAxis().getDisplayPosition(dateTime);
    }

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

    private void layoutBundleHelper(final EventNodeBase<?> eventNode) {
        //make sure it is shown
        eventNode.setVisible(true);
        eventNode.setManaged(true);
        //apply advanced layout description visibility options
        eventNode.setDescriptionVisibility(layoutSettings.getDescrVisibility());
        eventNode.setMaxDescriptionWidth(descriptionWidth);

        //do recursive layout
        eventNode.layoutChildren();
    }
}
