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
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 *
 */
class GuideLine extends Line {

    private final DateAxis dateAxis;

    private double startLayoutX;

    protected Tooltip tooltip;

    private double dragStartX = 0;

    GuideLine(double startX, double startY, double endX, double endY, DateAxis axis) {
        super(startX, startY, endX, endY);
        dateAxis = axis;
        setCursor(Cursor.E_RESIZE);
        getStrokeDashArray().setAll(5.0, 5.0);
        setStroke(Color.RED);
        setOpacity(.5);
        setStrokeWidth(3);

        setOnMouseEntered((MouseEvent event) -> {
            setTooltip();
        });

        setOnMousePressed((MouseEvent event) -> {
            startLayoutX = getLayoutX();
            dragStartX = event.getScreenX();
        });
        setOnMouseDragged((MouseEvent event) -> {
            double dX = event.getScreenX() - dragStartX;

            relocate(startLayoutX + dX, 0);
        });
    }

    private void setTooltip() {
        Tooltip.uninstall(this, tooltip);
        tooltip = new Tooltip(
                NbBundle.getMessage(this.getClass(), "Timeline.ui.detailview.tooltip.text", formatSpan(getDateTime())));
        Tooltip.install(this, tooltip);
    }

    private String formatSpan(DateTime date) {
        return date.toString(TimeLineController.getZonedFormatter());
    }

    private DateTime getDateTime() {
        return dateAxis.getValueForDisplay(dateAxis.parentToLocal(getLayoutX(), 0).getX());
    }

}
