/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui;

import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.chart.Axis;
import javafx.scene.chart.Chart;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.controlsfx.control.action.ActionGroup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.Forward;

/**
 * Interface for TimeLineViews that are 'charts'.
 *
 * @param <X> the type of values along the horizontal axis
 */
public interface TimeLineChart<X> extends TimeLineView {

    IntervalSelector<? extends X> getIntervalSelector();

    void setIntervalSelector(IntervalSelector<? extends X> newIntervalSelector);

    /**
     * derived classes should implement this so as to supply an appropriate
     * subclass of {@link IntervalSelector}
     *
     * @return a new interval selector
     */
    IntervalSelector<X> newIntervalSelector();

    /**
     * clear any references to previous interval selectors , including removing
     * the interval selector from the ui / scene-graph
     */
    void clearIntervalSelector();

    public Axis<X> getXAxis();

    public TimeLineController getController();

    ContextMenu getChartContextMenu();

    ContextMenu getChartContextMenu(MouseEvent m);

    /**
     * drag handler class used by {@link TimeLineChart}s to create
     * {@link IntervalSelector}s
     *
     * @param <X> the type of values along the horizontal axis
     * @param <Y> the type of chart this is a drag handler for
     */
    static class ChartDragHandler<X, Y extends Chart & TimeLineChart<X>> implements EventHandler<MouseEvent> {

        private final Y chart;

        private double startX;  //hanlder mainstains position of drag start

        public ChartDragHandler(Y chart) {
            this.chart = chart;
        }

        @Override
        public void handle(MouseEvent mouseEvent) {
            EventType<? extends MouseEvent> mouseEventType = mouseEvent.getEventType();
            if (mouseEventType == MouseEvent.MOUSE_PRESSED) {
                //caputure  x-position, incase we are repositioning existing selector
                startX = mouseEvent.getX();
                chart.setCursor(Cursor.H_RESIZE);
            } else if (mouseEventType == MouseEvent.MOUSE_DRAGGED) {
                if (chart.getIntervalSelector() == null) {
                    //make new interval selector
                    chart.setIntervalSelector(chart.newIntervalSelector());
                    chart.getIntervalSelector().prefHeightProperty().bind(chart.heightProperty());
                    startX = mouseEvent.getX();
                    chart.getIntervalSelector().relocate(startX, 0);
                } else {
                    //resize/position existing selector
                    if (mouseEvent.getX() > startX) {
                        chart.getIntervalSelector().relocate(startX, 0);
                        chart.getIntervalSelector().setPrefWidth(mouseEvent.getX() - startX);
                    } else {
                        chart.getIntervalSelector().relocate(mouseEvent.getX(), 0);
                        chart.getIntervalSelector().setPrefWidth(startX - mouseEvent.getX());
                    }
                }
                chart.getIntervalSelector().autosize();
            } else if (mouseEventType == MouseEvent.MOUSE_RELEASED) {
                chart.setCursor(Cursor.DEFAULT);
            } else if (mouseEventType == MouseEvent.MOUSE_CLICKED) {
                chart.setCursor(Cursor.DEFAULT);
            }
        }

    }

    static class MouseClickedHandler<X, C extends Chart & TimeLineChart<X>> implements EventHandler<MouseEvent> {

        private final C chart;

        public MouseClickedHandler(C chart) {
            this.chart = chart;
        }

        @Override
        public void handle(MouseEvent clickEvent) {
            if (chart.getChartContextMenu() != null) {
                chart.getChartContextMenu().hide();
            }
            if (clickEvent.getButton() == MouseButton.SECONDARY && clickEvent.isStillSincePress()) {
                chart.getChartContextMenu(clickEvent);
                chart.setOnMouseMoved(this);
                chart.getChartContextMenu().show(chart, clickEvent.getScreenX(), clickEvent.getScreenY());
                clickEvent.consume();
            }
        }
    }

    @NbBundle.Messages({"TimeLineChart.zoomHistoryActionGroup.name=Zoom History"})
    static ActionGroup newZoomHistoyActionGroup(TimeLineController controller) {
        return new ActionGroup(Bundle.TimeLineChart_zoomHistoryActionGroup_name(),
                new Back(controller),
                new Forward(controller));
    }
}
