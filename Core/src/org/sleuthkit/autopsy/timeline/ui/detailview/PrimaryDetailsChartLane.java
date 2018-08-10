/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.logging.Level;
import javafx.collections.ListChangeListener;
import javafx.scene.chart.Axis;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import org.controlsfx.control.Notifications;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.ui.ContextMenuProvider;
import static org.sleuthkit.autopsy.timeline.ui.EventTypeUtils.getColor;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventStripe;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Custom implementation of XYChart to graph events on a horizontal timeline.
 *
 * The horizontal DateAxis controls the tick-marks and the horizontal layout of
 * the nodes representing events. The vertical NumberAxis does nothing (although
 * a custom implementation could help with the vertical layout?)
 *
 * Series help organize events for the banding by event type, we could add a
 * node to contain each band if we need a place for per band controls.
 *
 * //TODO: refactor the projected lines to a separate class. -jm
 */
public final class PrimaryDetailsChartLane extends DetailsChartLane<EventStripe> implements ContextMenuProvider {

    private static final Logger logger = Logger.getLogger(PrimaryDetailsChartLane.class.getName());

    private static final int PROJECTED_LINE_Y_OFFSET = 5;
    private static final int PROJECTED_LINE_STROKE_WIDTH = 5;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final Map<EventCluster, Line> projectionMap = new ConcurrentHashMap<>();

    @NbBundle.Messages({"PrimaryDetailsChartLane.stripeChangeListener.errorMessage=Error adding stripe to chart lane."})
    PrimaryDetailsChartLane(DetailsChart parentChart, DateAxis dateAxis, final Axis<EventStripe> verticalAxis) {
        super(parentChart, dateAxis, verticalAxis, true);

        //add listener for events that should trigger layout
        getController().getQuickHideFilters().addListener(layoutInvalidationListener);

        parentChart.getRootEventStripes().addListener((ListChangeListener.Change<? extends EventStripe> change) -> {
            while (change.next()) {
                try {
                    for (EventStripe stripe : change.getAddedSubList()) {
                        addEvent(stripe);
                    }
                } catch (TskCoreException ex) {
                    Notifications.create().owner(getScene().getWindow())
                            .text(Bundle.PrimaryDetailsChartLane_stripeChangeListener_errorMessage()).showError();
                    logger.log(Level.SEVERE, "Error adding stripe to chart lane.", ex);
                }
                change.getRemoved().forEach(this::removeEvent);
            }
            requestChartLayout();
        });
        for (EventStripe stripe : parentChart.getRootEventStripes()) {
            try {
                addEvent(stripe);
            } catch (TskCoreException ex) {
                Notifications.create().owner(getScene().getWindow())
                        .text(Bundle.PrimaryDetailsChartLane_stripeChangeListener_errorMessage())
                        .showError();
                logger.log(Level.SEVERE, "Error adding stripe to chart lane.", ex);
            }
        }
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
                        double y = dateAxis.getLayoutY() + PROJECTED_LINE_Y_OFFSET; //NOPMD y is standard coord name
                        Line line
                                = new Line(dateAxis.localToParent(getXForEpochMillis(range.getStartMillis()), 0).getX(), y,
                                        dateAxis.localToParent(getXForEpochMillis(range.getEndMillis()), 0).getX(), y);
                        line.setStroke(getColor(addedNode.getEventType()).deriveColor(0, 1, 1, .5));
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
