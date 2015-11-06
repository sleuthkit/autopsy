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

import javafx.beans.binding.StringBinding;
import javafx.scene.Cursor;
import javafx.scene.chart.Axis;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 *
 */
@NbBundle.Messages({"GuideLine.tooltip.text={0}\nRight-click to remove.\nRight-drag to reposition."})
class GuideLine extends Line {

    private final Axis<DateTime> dateAxis;
    private Tooltip tooltip = new Tooltip();

    private double startLayoutX;
    private double dragStartX = 0;

    GuideLine(double startX, double startY, double endX, double endY, Axis<DateTime> axis) {
        super(startX, startY, endX, endY);
        dateAxis = axis;
        //TODO: assign via css
        setCursor(Cursor.E_RESIZE);
        getStrokeDashArray().setAll(5.0, 5.0);
        setStroke(Color.RED);
        setOpacity(.5);
        setStrokeWidth(3);

        Tooltip.install(this, tooltip);
        tooltip.textProperty().bind(new StringBinding() {
            {
                bind(layoutXProperty());
            }

            @Override
            protected String computeValue() {
                return Bundle.GuideLine_tooltip_text(formatSpan(getDateTime()));
            }
        });
//        setOnMouseEntered(enteredEvent -> updateToolTipText());
        setOnMousePressed(pressedEvent -> {
            startLayoutX = getLayoutX();
            dragStartX = pressedEvent.getScreenX();
        });
        setOnMouseDragged(dragEvent -> {
            double dX = dragEvent.getScreenX() - dragStartX;
            relocate(startLayoutX + dX, 0);
//            updateToolTipText();
            dragEvent.consume();
        });
    }

    private void updateToolTipText() {
        Tooltip.uninstall(this, tooltip);

        tooltip = new Tooltip(Bundle.GuideLine_tooltip_text(formatSpan(getDateTime())));
        Tooltip.install(this, tooltip);
    }

    private String formatSpan(DateTime date) {
        return date.toString(TimeLineController.getZonedFormatter());
    }

    private DateTime getDateTime() {
        return dateAxis.getValueForDisplay(dateAxis.parentToLocal(getLayoutX(), 0).getX());
    }

}
