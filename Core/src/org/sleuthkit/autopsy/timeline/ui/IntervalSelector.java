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

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 * Visually represents a 'selected' time range, and allows mouse interactions
 * with it.
 *
 * @param <X> the type of values along the x axis this is a selector for
 *
 * This abstract class requires concrete implementations to implement template
 * methods to handle formating and date 'lookup' of the generic x-axis type
 */
public abstract class IntervalSelector<X> extends BorderPane {

    private static final Image ClEAR_INTERVAL_ICON = new Image("/org/sleuthkit/autopsy/timeline/images/cross-script.png", 16, 16, true, true, true);
    private static final Image ZOOM_TO_INTERVAL_ICON = new Image("/org/sleuthkit/autopsy/timeline/images/magnifier-zoom-fit.png", 16, 16, true, true, true);
    private static final double STROKE_WIDTH = 3;
    private static final double HALF_STROKE = STROKE_WIDTH / 2;

    /**
     * the Axis this is a selector over
     */
    public final TimeLineChart<X> chart;

    private Tooltip tooltip;
    /////////drag state
    private DragPosition dragPosition;
    private double startLeft;
    private double startDragX;
    private double startWidth;
    /////////end drag state
    private final TimeLineController controller;

    @FXML
    private Label startLabel;

    @FXML
    private Label endLabel;

    @FXML
    private Button closeButton;

    @FXML
    private Button zoomButton;

    public IntervalSelector(TimeLineChart<X> chart) {
        this.chart = chart;
        this.controller = chart.getController();
        FXMLConstructor.construct(this, IntervalSelector.class, "IntervalSelector.fxml"); // NON-NLS
    }

    @FXML
    void initialize() {
        assert startLabel != null : "fx:id=\"startLabel\" was not injected: check your FXML file 'IntervalSelector.fxml'.";
        assert endLabel != null : "fx:id=\"endLabel\" was not injected: check your FXML file 'IntervalSelector.fxml'.";
        assert closeButton != null : "fx:id=\"closeButton\" was not injected: check your FXML file 'IntervalSelector.fxml'.";
        assert zoomButton != null : "fx:id=\"zoomButton\" was not injected: check your FXML file 'IntervalSelector.fxml'.";

        setMaxHeight(USE_PREF_SIZE);
        setMinHeight(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        setMinWidth(USE_PREF_SIZE);

        closeButton.visibleProperty().bind(hoverProperty());
        zoomButton.visibleProperty().bind(hoverProperty());

        widthProperty().addListener((o) -> this.updateStartAndEnd());
        layoutXProperty().addListener((o) -> this.updateStartAndEnd());
        updateStartAndEnd();

        setOnMouseMoved((MouseEvent event) -> {
            Point2D parentMouse = getParent().sceneToLocal(new Point2D(event.getSceneX(), event.getSceneY()));
            final double diffX = getLayoutX() - parentMouse.getX();
            if (Math.abs(diffX) <= HALF_STROKE) {
                setCursor(Cursor.W_RESIZE);
            } else if (Math.abs(diffX + getWidth()) <= HALF_STROKE) {
                setCursor(Cursor.E_RESIZE);
            } else {
                setCursor(Cursor.HAND);
            }
            event.consume();
        });

        setOnMousePressed((MouseEvent event) -> {
            Point2D parentMouse = getParent().sceneToLocal(new Point2D(event.getSceneX(), event.getSceneY()));
            final double diffX = getLayoutX() - parentMouse.getX();
            startDragX = event.getScreenX();
            startWidth = getWidth();
            startLeft = getLayoutX();
            if (Math.abs(diffX) <= HALF_STROKE) {
                dragPosition = IntervalSelector.DragPosition.LEFT;
            } else if (Math.abs(diffX + getWidth()) <= HALF_STROKE) {
                dragPosition = IntervalSelector.DragPosition.RIGHT;
            } else {
                dragPosition = IntervalSelector.DragPosition.CENTER;
            }
            event.consume();
        });
        setOnMouseDragged((MouseEvent event) -> {
            double dX = event.getScreenX() - startDragX;
            switch (dragPosition) {
                case CENTER:
                    setLayoutX(startLeft + dX);
                    break;
                case LEFT:
                    setLayoutX(startLeft + dX);
                    setPrefWidth(startWidth - dX);
                    autosize();
                    break;
                case RIGHT:
                    setPrefWidth(startWidth + dX);
                    autosize();
                    break;
            }
            event.consume();
            System.out.println(dX);
        });

        ActionUtils.configureButton(new ZoomToSelectedIntervalAction(), zoomButton);
        ActionUtils.configureButton(new ClearSelectedIntervalAction(), closeButton);

        //have to add handler rather than use convenience methods so that charts can listen for dismisal click
        setOnMouseClicked((MouseEvent event) -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                chart.clearIntervalSelector();
                event.consume();
            }
            if (event.getClickCount() >= 2) {
                zoomToSelectedInterval();
                event.consume();
            }
        });
    }

    private void zoomToSelectedInterval() {
        //convert to DateTimes, using max/min if null(off axis)
        DateTime start = parseDateTime(getSpanStart());
        DateTime end = parseDateTime(getSpanEnd());
        Interval i = adjustInterval(start.isBefore(end) ? new Interval(start, end) : new Interval(end, start));
        controller.pushTimeRange(i);
    }

    /**
     *
     * @param i the interval represented by this selector
     *
     * @return a modified version of {@code i} adjusted to suite the needs of
     *         the concrete implementation
     */
    protected abstract Interval adjustInterval(Interval i);

    /**
     * format a string representation of the given x-axis value to use in the
     * tooltip
     *
     * @param date a x-axis value of type X
     *
     * @return a string representation of the given x-axis value
     */
    protected abstract String formatSpan(final X date);

    /**
     * parse an x-axis value to a {@link DateTime}
     *
     * @param date a x-axis value of type X
     *
     * @return a {@link DateTime} corresponding to the given x-axis value
     */
    protected abstract DateTime parseDateTime(X date);

    @NbBundle.Messages(value = {"# {0} - start timestamp",
        "# {1} - end timestamp",
        "Timeline.ui.TimeLineChart.tooltip.text=Double-click to zoom into range:\n{0} to {1}\nRight-click to clear."})
    private void updateStartAndEnd() {
        String startString = formatSpan(getSpanStart());
        String endString = formatSpan(getSpanEnd());
        startLabel.setText(startString);
        endLabel.setText(endString);

        Tooltip.uninstall(this, tooltip);
        tooltip = new Tooltip(Bundle.Timeline_ui_TimeLineChart_tooltip_text(startString, endString));
        Tooltip.install(this, tooltip);
    }

    /**
     * @return the value along the x-axis corresponding to the left edge of the
     *         selector
     */
    public X getSpanEnd() {
        return chart.getXAxis().getValueForDisplay(chart.getXAxis().parentToLocal(getBoundsInParent().getMaxX(), 0).getX());
    }

    /**
     * @return the value along the x-axis corresponding to the right edge of the
     *         selector
     */
    public X getSpanStart() {
        return chart.getXAxis().getValueForDisplay(chart.getXAxis().parentToLocal(getBoundsInParent().getMinX(), 0).getX());
    }

    /**
     * enum to represent whether the drag is a left/right-edge modification or a
     * horizontal slide triggered by dragging the center
     */
    private enum DragPosition {

        LEFT,
        CENTER,
        RIGHT
    }

    private class ZoomToSelectedIntervalAction extends Action {

        @NbBundle.Messages("IntervalSelector.ZoomAction.name=Zoom")
        ZoomToSelectedIntervalAction() {
            super(Bundle.IntervalSelector_ZoomAction_name());
            setGraphic(new ImageView(ZOOM_TO_INTERVAL_ICON));
            setEventHandler((ActionEvent t) -> {
                zoomToSelectedInterval();
            });
        }
    }

    private class ClearSelectedIntervalAction extends Action {

        @NbBundle.Messages("IntervalSelector.ClearSelectedIntervalAction.tooltTipText=Clear Selected Interval")
        ClearSelectedIntervalAction() {
            super("");
            setLongText(Bundle.IntervalSelector_ClearSelectedIntervalAction_tooltTipText());
            setGraphic(new ImageView(ClEAR_INTERVAL_ICON));
            setEventHandler((ActionEvent t) -> {
                chart.clearIntervalSelector();
            });
        }
    }
}
