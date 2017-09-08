/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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

import javafx.scene.chart.Axis;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.shape.Line;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.AbstractTimelineChart;

/**
 * Subclass of {@link Line} with appropriate behavior (mouse listeners) to act
 * as a visual reference point in the details view.
 */
@NbBundle.Messages({"# {0} - date/time at guideline position",
    "GuideLine.tooltip.text={0}\nRight-click to remove.\nDrag to reposition."})
class GuideLine extends Line {

    private final Tooltip CHART_DEFAULT_TOOLTIP = AbstractTimelineChart.getDefaultTooltip();

    private final Tooltip tooltip = new Tooltip();
    private final DetailsChart chart;

    //used across invocations of mouse event handlers to maintain state
    private double startLayoutX;
    private double dragStartX = 0;

    /**
     * @param chart the chart this GuideLine belongs to.
     */
    GuideLine(DetailsChart chart) {
        super(0, 0, 0, 0);
        this.chart = chart;
        Axis<DateTime> xAxis = chart.getXAxis();
        endYProperty().bind(chart.heightProperty().subtract(xAxis.heightProperty().subtract(xAxis.tickLengthProperty())));

        getStyleClass().add("guide-line"); //NON-NLS

        Tooltip.install(this, tooltip);
        tooltip.setOnShowing(showing -> tooltip.setText(Bundle.GuideLine_tooltip_text(getDateTimeAsString())));

        //this is a hack to override the tooltip of the enclosing chart.
        setOnMouseEntered(entered -> Tooltip.uninstall(chart, CHART_DEFAULT_TOOLTIP));
        setOnMouseExited(exited -> Tooltip.install(chart, CHART_DEFAULT_TOOLTIP));

        setOnMouseClicked(clickedEvent -> {
            if (clickedEvent.getButton() == MouseButton.SECONDARY
                    && clickedEvent.isStillSincePress()) {
                chart.clearGuideLine(this);
                clickedEvent.consume();
            }
        });
        setOnMousePressed(pressedEvent -> {
            startLayoutX = getLayoutX();
            dragStartX = pressedEvent.getScreenX();
        });
        setOnMouseDragged(dragEvent -> {
            double dX = dragEvent.getScreenX() - dragStartX;
            relocate(startLayoutX + dX, 0);
            dragEvent.consume();
        });
    }

    private String getDateTimeAsString() {
        return chart.getDateTimeForPosition(getLayoutX()).toString(TimeLineController.getZonedFormatter());
    }
}
