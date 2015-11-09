/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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

import javafx.scene.Cursor;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;

/**
 *
 */
@NbBundle.Messages({"GuideLine.tooltip.text={0}\nRight-click to remove.\nDrag to reposition."})
class GuideLine extends Line {

    private static final Tooltip CHART_DEFAULT_TOOLTIP = AbstractVisualizationPane.getDefaultTooltip();

    private Tooltip tooltip = new Tooltip();

    private double startLayoutX;
    private double dragStartX = 0;
    private final EventDetailsChart chart;

    /**
     *
     * @param startX
     * @param startY
     * @param endX
     * @param endY
     * @param chart
     */
    GuideLine(double startX, double startY, double endX, double endY, EventDetailsChart chart) {
        super(startX, startY, endX, endY);
        this.chart = chart;
        //TODO: assign via css
        setCursor(Cursor.E_RESIZE);
        getStrokeDashArray().setAll(5.0, 5.0);
        setStroke(Color.RED);
        setOpacity(.5);
        setStrokeWidth(3);

        Tooltip.install(this, tooltip);
        tooltip.setOnShowing(windowEvent -> tooltip.setText(Bundle.GuideLine_tooltip_text(getDateTimeAsString())));
        setOnMouseEntered(entered -> Tooltip.uninstall(chart, CHART_DEFAULT_TOOLTIP));
        setOnMouseExited(exited -> Tooltip.install(chart, CHART_DEFAULT_TOOLTIP));
        setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.SECONDARY
                    && mouseEvent.isStillSincePress() == false) {
                chart.clearGuideLine();
                mouseEvent.consume();
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
