/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
import javafx.application.Platform;
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
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;

/**
 *
 */
public final class PinnedEventsChart extends XYChart<DateTime, SingleEvent> implements DetailsChart {

    @Override
    public ContextMenu getChartContextMenu() {
        return chartContextMenu;
    }
    private ContextMenu chartContextMenu;

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

    private static final String styleSheet = GuideLine.class.getResource("EventsDetailsChart.css").toExternalForm(); //NON-NLS

    private final TimeLineController controller;
    private final FilteredEventsModel filteredEvents;
    /**
     * the group that all event nodes are added to. This facilitates scrolling
     * by allowing a single translation of this group.
     */
    private final Group nodeGroup = new Group();
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<SingleEvent> events = FXCollections.observableArrayList();
    private final ObservableList< SingleEventNode> eventNodes = FXCollections.observableArrayList();
    private final ObservableList< SingleEventNode> sortedEventNodes = eventNodes.sorted(Comparator.comparing(SingleEventNode::getStartMillis));
    private double descriptionWidth;

    Map<Long, SingleEventNode> eventMap = new HashMap<>();

    /**
     *
     * @param controller     the value of controller
     * @param dateAxis       the value of dateAxis
     * @param verticalAxis   the value of verticalAxis
     * @param selectedNodes1 the value of selectedNodes1
     */
    PinnedEventsChart(TimeLineController controller, DateAxis dateAxis, final Axis<SingleEvent> verticalAxis) {
        super(dateAxis, verticalAxis);

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
        final Series<DateTime, SingleEvent> series = new Series<>();
        setData(FXCollections.observableArrayList());
        getData().add(series);
//        //add listener for events that should trigger layout
//        bandByType.addListener(layoutInvalidationListener);
//        oneEventPerRow.addListener(layoutInvalidationListener);
//        truncateAll.addListener(layoutInvalidationListener);
//        truncateWidth.addListener(layoutInvalidationListener);
//        descrVisibility.addListener(layoutInvalidationListener);
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
        controller.getPinnedEventIDs().addListener((SetChangeListener.Change<? extends Long> change) -> {
            if (change.wasAdded()) {
                SingleEvent eventById = controller.getEventsModel().getEventById(change.getElementAdded());
                Data<DateTime, SingleEvent> data1 = new Data<>(new DateTime(eventById.getStartMillis()), eventById);
                series.getData().add(data1);
                addDataItem(data1);
            }
            if (change.wasRemoved()) {
                final SingleEvent eventById = controller.getEventsModel().getEventById(change.getElementRemoved());
                Data<DateTime, SingleEvent> data1 = new Data<>(new DateTime(eventById.getStartMillis()), eventById);
                series.getData().removeIf(t -> eventById.equals(t.getYValue()));
                removeDataItem(data1);
            }

            requestChartLayout();
        });

//        this.selectedNodes = selectedNodes;
//        selectedNodes.addListener(new SelectionChangeHandler());
    }

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
        return FXCollections.observableArrayList();
    }

    @Override
    protected void dataItemAdded(Series<DateTime, SingleEvent> series, int itemIndex, Data<DateTime, SingleEvent> item) {
    }

    @Override
    protected void dataItemRemoved(Data<DateTime, SingleEvent> item, Series<DateTime, SingleEvent> series) {
    }

    @Override
    protected void dataItemChanged(Data<DateTime, SingleEvent> item) {
    }

    @Override
    protected void seriesAdded(Series<DateTime, SingleEvent> series, int seriesIndex) {
    }

    @Override
    protected void seriesRemoved(Series<DateTime, SingleEvent> series) {
    }
    /**
     * the maximum y value used so far during the most recent layout pass
     */
    private final ReadOnlyDoubleWrapper maxY = new ReadOnlyDoubleWrapper(0.0);

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
        descriptionWidth = /*
                 * truncateAll.get() ? truncateWidth.get() :
                 */ USE_PREF_SIZE;

//        if (bandByType.get()) {
//            sortedStripeNodes.stream()
//                    .collect(Collectors.groupingBy(EventStripeNode::getEventType)).values()
//                    .forEach(inputNodes -> maxY.set(layoutEventBundleNodes(inputNodes, maxY.get())));
//        } else {
        maxY.set(layoutEventBundleNodes(sortedEventNodes.sorted(Comparator.comparing(SingleEventNode::getStartMillis)), 0));
//        }
        setCursor(null);
    }
    private static final int MINIMUM_EVENT_NODE_GAP = 4;
    private final static int MINIMUM_ROW_HEIGHT = 24;

    private double getXForEpochMillis(Long millis) {
        DateTime dateTime = new DateTime(millis);
        return getXAxis().getDisplayPosition(dateTime);
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
            double yTop = computeYTop(minY, h, maxXatY, xLeft, xRight);

            localMax = Math.max(yTop + h, localMax);

            if ((xLeft != eventNode.getLayoutX()) || (yTop != eventNode.getLayoutY())) {
                //animate node to new position
                eventNode.animateTo(xLeft, yTop);
            }

        }
        return localMax; //return new max
    }

    /**
     * expose as protected
     */
    @Override
    protected void requestChartLayout() {
        super.requestChartLayout();
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
//        eventNode.setDescriptionVisibility(descrVisibility.get());
//        eventNode.setMaxDescriptionWidth(descriptionWidth);

        //do recursive layout
        eventNode.layoutChildren();
    }

    /**
     * add a dataitem to this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param data
     */
    void addDataItem(Data<DateTime, SingleEvent> data) {
        final SingleEvent event = data.getYValue();

        SingleEventNode eventNode = new SingleEventNode(PinnedEventsChart.this, event, null);
        eventMap.put(event.getEventID(), eventNode);
        Platform.runLater(() -> {
            events.add(event);
            eventNodes.add(eventNode);
            nodeGroup.getChildren().add(eventNode);
            data.setNode(eventNode);

        });
    }

    /**
     * remove a data item from this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param data
     */
    void removeDataItem(Data<DateTime, SingleEvent> data) {
        SingleEventNode removedNode = eventMap.remove(data.getYValue().getEventID());
        Platform.runLater(() -> {
            events.removeAll(data.getYValue());
            eventNodes.removeAll(removedNode);
            nodeGroup.getChildren().removeAll(removedNode);
            data.setNode(null);
        });
    }

}
