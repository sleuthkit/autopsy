/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.collections.ListChangeListener;
import javafx.scene.chart.Axis;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.ui.ContextMenuProvider;
import org.sleuthkit.datamodel.timeline.EventCluster;
import org.sleuthkit.datamodel.timeline.EventStripe;

/**
 * Custom implementation of XYChart to graph events on a horizontal
 * timeline.
 *
 * The horizontal DateAxis controls the tick-marks and the horizontal
 * layout of the nodes representing events. The vertical NumberAxis does
 * nothing (although a custom implementation could help with the vertical
 * layout?)
 *
 * Series help organize events for the banding by event type, we could add a
 * node to contain each band if we need a place for per band controls.
 *
 * //TODO: refactor the projected lines to a separate class. -jm
 */
public final class PrimaryDetailsChartLane extends DetailsChartLane<EventStripe> implements ContextMenuProvider {

    private static final int PROJECTED_LINE_Y_OFFSET = 5;
    private static final int PROJECTED_LINE_STROKE_WIDTH = 5;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final Map<EventCluster, Line> projectionMap = new ConcurrentHashMap<>();

    PrimaryDetailsChartLane(DetailsChart parentChart, DateAxis dateAxis, final Axis<EventStripe> verticalAxis) {
        super(parentChart, dateAxis, verticalAxis, true);

        //add listener for events that should trigger layout
        getController().getQuickHideFilters().addListener(layoutInvalidationListener);

        parentChart.getRootEventStripes().addListener((ListChangeListener.Change<? extends EventStripe> change) -> {
            while (change.next()) {
                change.getAddedSubList().stream().forEach(this::addEvent);
                change.getRemoved().stream().forEach(this::removeEvent);
            }
            requestChartLayout();
        });
        parentChart.getRootEventStripes().stream().forEach(this::addEvent);
        requestChartLayout();

        getSelectedNodes().addListener((ListChangeListener.Change<? extends EventNodeBase<?>> change) -> {
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

    private double getParentXForEpochMillis(Long epochMillis) {
        return getXAxis().localToParent(getXForEpochMillis(epochMillis), 0).getX();
    }

    @Override
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
