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
package org.sleuthkit.autopsy.timeline.ui;

import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.chart.Axis;
import javafx.scene.chart.Chart;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;

/** Interface for TimeLineViews that are 'charts'.
 *
 * @param <X> the type of values along the horizontal axis */
public interface TimeLineChart<X> extends TimeLineView {

    IntervalSelector<? extends X> getIntervalSelector();

    void setIntervalSelector(IntervalSelector<? extends X> newIntervalSelector);

    /** derived classes should implement this so as to supply an appropriate
     * subclass of {@link IntervalSelector}
     *
     * @param x    the initial x position of the new interval selector
     * @param axis the axis the new interval selector will be over
     *
     * @return a new interval selector
     */
    IntervalSelector<X> newIntervalSelector(double x, Axis<X> axis);

    /** clear any references to previous interval selectors , including removing
     * the interval selector from the ui / scene-graph */
    void clearIntervalSelector();

    /**
     * drag handler class used by {@link TimeLineChart}s to create
     * {@link IntervalSelector}s
     *
     * @param <X> the type of values along the horizontal axis
     * @param <Y> the type of chart this is a drag handler for
     */
    class ChartDragHandler<X, Y extends Chart & TimeLineChart<X>> implements EventHandler<MouseEvent> {

        private final Y chart;

        private final Axis<X> dateAxis;

        private double startX;  //hanlder mainstains position of drag start

        public ChartDragHandler(Y chart, Axis<X> dateAxis) {
            this.chart = chart;
            this.dateAxis = dateAxis;
        }

        @Override
        public void handle(MouseEvent t) {
            if (t.getButton() == MouseButton.SECONDARY) {

                if (t.getEventType() == MouseEvent.MOUSE_PRESSED) {
                    //caputure  x-position, incase we are repositioning existing selector
                    startX = t.getX();
                    chart.setCursor(Cursor.E_RESIZE);
                } else if (t.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                    if (chart.getIntervalSelector() == null) {
                        //make new interval selector
                        chart.setIntervalSelector(chart.newIntervalSelector(t.getX(), dateAxis));
                        chart.getIntervalSelector().heightProperty().bind(chart.heightProperty().subtract(dateAxis.heightProperty().subtract(dateAxis.tickLengthProperty())));
                        chart.getIntervalSelector().addEventHandler(MouseEvent.MOUSE_CLICKED, (MouseEvent event) -> {
                            if (event.getButton() == MouseButton.SECONDARY) {
                                chart.clearIntervalSelector();
                                event.consume();
                            }
                        });
                        startX = t.getX();
                    } else {
                        //resize/position existing selector
                        if (t.getX() > startX) {
                            chart.getIntervalSelector().setX(startX);
                            chart.getIntervalSelector().setWidth(t.getX() - startX);
                        } else {
                            chart.getIntervalSelector().setX(t.getX());
                            chart.getIntervalSelector().setWidth(startX - t.getX());
                        }
                    }
                } else if (t.getEventType() == MouseEvent.MOUSE_RELEASED) {
                    chart.setCursor(Cursor.DEFAULT);
                }
                t.consume();
            }
        }
    }

    /** Visually represents a 'selected' time range, and allows mouse
     * interactions with it.
     *
     * @param <X> the type of values along the x axis this is a selector for
     *
     * This abstract class requires concrete implementations to implement
     * hook methods to handle formating and date 'lookup' of the
     * generic x-axis type */
    static abstract class IntervalSelector<X> extends Rectangle {

        private static final double STROKE_WIDTH = 3;

        private static final double HALF_STROKE = STROKE_WIDTH / 2;

        /** the Axis this is a selector over */
        private final Axis<X> dateAxis;

        protected Tooltip tooltip;

        /////////drag state
        private DragPosition dragPosition;

        private double startLeft;

        private double startX;

        private double startWidth;
        /////////end drag state

        /**
         *
         * @param x          the initial x position of this selector
         * @param height     the initial height of this selector
         * @param axis       the {@link Axis<X>} this is a selector over
         * @param controller the controller to invoke when this selector is
         *                   double clicked
         */
        public IntervalSelector(double x, double height, Axis<X> axis, TimeLineController controller) {
            super(x, 0, x, height);
            dateAxis = axis;
            setStroke(Color.BLUE);
            setStrokeWidth(STROKE_WIDTH);
            setFill(Color.BLUE.deriveColor(0, 1, 1, 0.5));
            setOpacity(0.5);
            widthProperty().addListener(o -> {
                setTooltip();
            });
            xProperty().addListener(o -> {
                setTooltip();
            });
            setTooltip();

            setOnMouseMoved((MouseEvent event) -> {
                Point2D localMouse = sceneToLocal(new Point2D(event.getSceneX(), event.getSceneY()));
                final double diffX = getX() - localMouse.getX();
                if (Math.abs(diffX) <= HALF_STROKE || Math.abs(diffX + getWidth()) <= HALF_STROKE) {
                    setCursor(Cursor.E_RESIZE);
                } else {
                    setCursor(Cursor.HAND);
                }
            });
            setOnMousePressed((MouseEvent event) -> {
                Point2D localMouse = sceneToLocal(new Point2D(event.getSceneX(), event.getSceneY()));
                final double diffX = getX() - localMouse.getX();
                startX = event.getX();
                startWidth = getWidth();
                startLeft = getX();
                if (Math.abs(diffX) <= HALF_STROKE) {
                    dragPosition = IntervalSelector.DragPosition.LEFT;
                } else if (Math.abs(diffX + getWidth()) <= HALF_STROKE) {
                    dragPosition = IntervalSelector.DragPosition.RIGHT;
                } else {
                    dragPosition = IntervalSelector.DragPosition.CENTER;
                }
            });
            setOnMouseDragged((MouseEvent event) -> {
                double dX = event.getX() - startX;
                switch (dragPosition) {
                    case CENTER:
                        setX(startLeft + dX);
                        break;
                    case LEFT:
                        setX(startLeft + dX);
                        setWidth(startWidth - dX);
                        break;
                    case RIGHT:
                        setWidth(startWidth + dX);
                        break;
                }
            });
            //have to add handler rather than use convenience methods so that charts can listen for dismisal click
            addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {

                public void handle(MouseEvent event) {
                    if (event.getClickCount() >= 2) {
                        //convert to DateTimes, using max/min if null(off axis)
                        DateTime start = parseDateTime(getSpanStart());
                        DateTime end = parseDateTime(getSpanEnd());

                        Interval i = adjustInterval(start.isBefore(end) ? new Interval(start, end) : new Interval(end, start));

                        controller.pushTimeRange(i);
                    }
                }
            });
        }

        /**
         *
         * @param i the interval represented by this selector
         *
         * @return a modified version of {@code i} adjusted to suite the needs
         *         of the concrete implementation
         */
        protected abstract Interval adjustInterval(Interval i);

        /** format a string representation of the given x-axis value to use in
         * the tooltip
         *
         * @param date a x-axis value of type X
         *
         * @return a string representation of the given x-axis value */
        protected abstract String formatSpan(final X date);

        /** parse an x-axis value to a {@link DateTime}
         *
         * @param date a x-axis value of type X
         *
         * @return a {@link DateTime} corresponding to the given x-axis value */
        protected abstract DateTime parseDateTime(X date);

        private void setTooltip() {
            final X start = getSpanStart();
            final X end = getSpanEnd();
            Tooltip.uninstall(this, tooltip);
            tooltip = new Tooltip(
                    NbBundle.getMessage(this.getClass(), "Timeline.ui.TimeLineChart.tooltip.text", formatSpan(start),
                                        formatSpan(end)));
            Tooltip.install(this, tooltip);
        }

        /** @return the value along the x-axis corresponding to the left edge of
         *          the selector */
        public X getSpanEnd() {
            return dateAxis.getValueForDisplay(dateAxis.parentToLocal(getBoundsInParent().getMaxX(), 0).getX());
        }

        /** @return the value along the x-axis corresponding to the right edge
         *          of the selector */
        public X getSpanStart() {
            return dateAxis.getValueForDisplay(dateAxis.parentToLocal(getBoundsInParent().getMinX(), 0).getX());
        }

        /** enum to represent whether the drag is a left/right-edge modification
         * or a horizontal slide triggered by dragging the center */
        private enum DragPosition {

            LEFT, CENTER, RIGHT
        }
    }
}
