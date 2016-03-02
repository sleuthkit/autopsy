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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.scene.chart.Axis;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;

/**
 *
 */
public final class PinnedEventsChart extends DetailsChartLane<TimeLineEvent> {

    /**
     *
     * @param controller     the value of controller
     * @param dateAxis       the value of dateAxis
     * @param verticalAxis   the value of verticalAxis
     * @param selectedNodes1 the value of selectedNodes1
     */
    PinnedEventsChart(DetailViewPane parentPane, DateAxis dateAxis, final Axis<TimeLineEvent> verticalAxis) {
        super(parentPane, dateAxis, verticalAxis, false);

        final Series<DateTime, TimeLineEvent> series = new Series<>();
        setData(FXCollections.observableArrayList());
        getData().add(series);

//        //this is needed to allow non circular binding of the guideline and timerangeRect heights to the height of the chart
//        //TODO: seems like a hack, can we remove? -jm
//        boundsInLocalProperty().addListener((Observable observable) -> {
//            setPrefHeight(boundsInLocalProperty().get().getHeight());
//        });
        getController().getPinnedEvents().addListener((SetChangeListener.Change<? extends TimeLineEvent> change) -> {
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

    }

    @Override
    public ObservableList<EventStripe> getEventStripes() {
        return FXCollections.emptyObservableList();
    }

//    @Override
//    public double layoutEventBundleNodes(final Collection<? extends EventNodeBase<?>> nodes, final double minY) {
//        // map from y-ranges to maximum x
//        TreeRangeMap<Double, Double> maxXatY = TreeRangeMap.create();
//
//        // maximum y values occupied by any of the given nodes,  updated as nodes are layed out.
//        double localMax = minY;
//
//        //for each node do a recursive layout to size it and then position it in first available slot
//        for (EventNodeBase<?> eventNode : nodes) {
//            //is the node hiden by a quick hide filter?
//
//            layoutBundleHelper(eventNode);
//            //get computed height and width
//            double h = eventNode.getBoundsInLocal().getHeight();
//            double w = eventNode.getBoundsInLocal().getWidth();
//            //get left and right x coords from axis plus computed width
//            double xLeft = getXForEpochMillis(eventNode.getStartMillis()) - eventNode.getLayoutXCompensation();
//            double xRight = xLeft + w + MINIMUM_EVENT_NODE_GAP;
//
//            //initial test position
//            double yTop = (layoutSettings.getOneEventPerRow())
//                    ? (localMax + MINIMUM_EVENT_NODE_GAP)// if onePerRow, just put it at end
//                    : computeYTop(minY, h, maxXatY, xLeft, xRight);
//
//            localMax = Math.max(yTop + h, localMax);
//
//            if ((xLeft != eventNode.getLayoutX()) || (yTop != eventNode.getLayoutY())) {
//                //animate node to new position
//                eventNode.animateTo(xLeft, yTop);
//            }
//
//        }
//        return localMax; //return new max
//    }
//    @Override
//    protected void layoutPlotChildren() {
//        setCursor(Cursor.WAIT);
//        maxY.set(0);
//
////        //These don't change during a layout pass and are expensive to compute per node.  So we do it once at the start
////        activeQuickHidefilters = getController().getQuickHideFilters().stream()
////                .filter(AbstractFilter::isActive)
////                .map(DescriptionFilter::getDescription)
////                .collect(Collectors.toSet());
//        //This dosn't change during a layout pass and is expensive to compute per node.  So we do it once at the start
//        descriptionWidth = layoutSettings.getTruncateAll() ? layoutSettings.getTruncateWidth() : USE_PREF_SIZE;
//
//        if (layoutSettings.getBandByType()) {
//            sortedNodes.stream()
//                    .collect(Collectors.groupingBy(EventNodeBase<?>::getEventType)).values()
//                    .forEach(inputNodes -> maxY.set(layoutEventBundleNodes(inputNodes, maxY.get())));
//        } else {
//            maxY.set(layoutEventBundleNodes(sortedNodes.sorted(Comparator.comparing(EventNodeBase<?>::getStartMillis)), 0));
//        }
//        setCursor(null);
//    }
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
            nodes.add(eventNode);
            nodeGroup.getChildren().add(eventNode);
            data.setNode(eventNode);

        });
    }

    static EventNodeBase<?> createNode(DetailsChartLane<?> chart, TimeLineEvent event) {
        if (event instanceof SingleEvent) {
            return new SingleEventNode(chart, (SingleEvent) event, null);
        } else if (event instanceof EventCluster) {
            return new EventClusterNode(chart, (EventCluster) event, null);
        } else {
            return new EventStripeNode(chart, (EventStripe) event, null);
        }
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
            nodes.removeAll(removedNode);
            nodeGroup.getChildren().removeAll(removedNode);
            data.setNode(null);
        });
    }

    @Override
    void doAdditionalLayout() {
    }

}
