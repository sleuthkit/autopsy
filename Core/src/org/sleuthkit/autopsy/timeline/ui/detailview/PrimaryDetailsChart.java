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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;

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
public final class PrimaryDetailsChart extends DetailsChartLane<EventStripe> {

    private static final int PROJECTED_LINE_Y_OFFSET = 5;
    private static final int PROJECTED_LINE_STROKE_WIDTH = 5;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final Map<EventCluster, Line> projectionMap = new ConcurrentHashMap<>();

    PrimaryDetailsChart(DetailViewPane parentPane, DateAxis dateAxis, final Axis<EventStripe> verticalAxis) {
        super(parentPane, dateAxis, verticalAxis, true);

//        filteredEvents.zoomParametersProperty().addListener(o -> {
//            selectedNodes.clear();
//            projectionMap.clear();
//            controller.selectEventIDs(Collections.emptyList());
//        });
//        //this is needed to allow non circular binding of the guideline and timerangeRect heights to the height of the chart
//        //TODO: seems like a hack, can we remove? -jm
//        boundsInLocalProperty().addListener((Observable observable) -> {
//            setPrefHeight(boundsInLocalProperty().get().getHeight());
//        });
        //add listener for events that should trigger layout
        getController().getQuickHideFilters().addListener(layoutInvalidationListener);

        selectedNodes.addListener((ListChangeListener.Change<? extends EventNodeBase<?>> change) -> {
            while (change.next()) {
                change.getRemoved().forEach(removedNode -> {
                    removedNode.getEvent().getClusters().forEach(cluster -> {
                        Line removedLine = projectionMap.remove(cluster);
                        getChartChildren().removeAll(removedLine);
                    });

                });
                change.getAddedSubList().forEach(addedNode -> {
                    for (EventCluster range : addedNode.getEvent().getClusters()) {
                        double y = dateAxis.getLayoutY() + PROJECTED_LINE_Y_OFFSET;
                        Line line =
                                new Line(dateAxis.localToParent(getXForEpochMillis(range.getStartMillis()), 0).getX(), y,
                                        dateAxis.localToParent(getXForEpochMillis(range.getEndMillis()), 0).getX(), y);
                        line.setStroke(addedNode.getEventType().getColor().deriveColor(0, 1, 1, .5));
                        line.setStrokeWidth(PROJECTED_LINE_STROKE_WIDTH);
                        line.setStrokeLineCap(StrokeLineCap.ROUND);
                        projectionMap.put(range, line);
                        getChartChildren().add(line);
                    }
                });
            }
        });
    }

    public ObservableList<EventStripe> getEventStripes() {
        return events;
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
            newNode = new EventStripeNode(PrimaryDetailsChart.this, eventStripe, null);
        }
        Platform.runLater(() -> {
            events.add(eventStripe);
            nodes.add(newNode);
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
            events.removeAll(new StripeFlattener().apply(removedNode).collect(Collectors.toList()));
            nodes.removeAll(removedNode);
            nodeGroup.getChildren().removeAll(removedNode);
            data.setNode(null);
        });
    }

    private double getParentXForEpochMillis(Long epochMillis) {
        return getXAxis().localToParent(getXForEpochMillis(epochMillis), 0).getX();
    }

    void doAdditionalLayout() {
        for (final Map.Entry<EventCluster, Line> entry : projectionMap.entrySet()) {
            final EventCluster cluster = entry.getKey();
            final Line line = entry.getValue();

            line.setStartX(getParentXForEpochMillis(cluster.getStartMillis()));
            line.setEndX(getParentXForEpochMillis(cluster.getEndMillis()));

            line.setStartY(getXAxis().getLayoutY() + PROJECTED_LINE_Y_OFFSET);
            line.setEndY(getXAxis().getLayoutY() + PROJECTED_LINE_Y_OFFSET);
        }
    }

}
