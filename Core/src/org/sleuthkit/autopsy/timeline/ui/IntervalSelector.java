/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-18 Basis Technology Corp.
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

import java.util.logging.Level;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.chart.Axis;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.controlsfx.control.Notifications;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.datamodel.TskCoreException;

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

    private static final Logger logger = Logger.getLogger(IntervalSelector.class.getName());

    private static final Image CLEAR_INTERVAL_ICON = new Image("/org/sleuthkit/autopsy/timeline/images/cross-script.png", 16, 16, true, true, true); //NON-NLS
    private static final Image ZOOM_TO_INTERVAL_ICON = new Image("/org/sleuthkit/autopsy/timeline/images/magnifier-zoom-fit.png", 16, 16, true, true, true); //NON-NLS
    private static final double STROKE_WIDTH = 3;
    private static final double HALF_STROKE = STROKE_WIDTH / 2;

    /**
     * the Axis this is a selector over
     */
    public final IntervalSelectorProvider<X> chart;

    private Tooltip tooltip;
    /////////drag state
    private DragPosition dragPosition;
    private double startLeft;
    private double startDragX;
    private double startWidth;

    private final BooleanProperty isDragging = new SimpleBooleanProperty(false);
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

    @FXML
    private BorderPane bottomBorder;

    public IntervalSelector(IntervalSelectorProvider<X> chart) {
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

        BooleanBinding showingControls = zoomButton.hoverProperty().or(bottomBorder.hoverProperty().or(hoverProperty())).and(isDragging.not());
        closeButton.visibleProperty().bind(showingControls);
        closeButton.managedProperty().bind(showingControls);
        zoomButton.visibleProperty().bind(showingControls);
        zoomButton.managedProperty().bind(showingControls);

        widthProperty().addListener(observable -> {
            updateStartAndEnd();
            if (startLabel.getWidth() + zoomButton.getWidth() + endLabel.getWidth() > getWidth() - 10) {
                this.setCenter(zoomButton);
                bottomBorder.setCenter(new Rectangle(10, 10, Color.TRANSPARENT));
            } else {
                bottomBorder.setCenter(zoomButton);
            }
            BorderPane.setAlignment(zoomButton, Pos.BOTTOM_CENTER);
        });
        layoutXProperty().addListener(observable -> this.updateStartAndEnd());
        updateStartAndEnd();

        setOnMouseMoved(mouseMove -> {
            Point2D parentMouse = getLocalMouseCoords(mouseMove);
            final double diffX = getLayoutX() - parentMouse.getX();
            if (Math.abs(diffX) <= HALF_STROKE) {
                setCursor(Cursor.W_RESIZE);
            } else if (Math.abs(diffX + getWidth()) <= HALF_STROKE) {
                setCursor(Cursor.E_RESIZE);
            } else {
                setCursor(Cursor.HAND);
            }
            mouseMove.consume();
        });

        setOnMousePressed(mousePress -> {
            Point2D parentMouse = getLocalMouseCoords(mousePress);
            final double diffX = getLayoutX() - parentMouse.getX();
            startDragX = mousePress.getScreenX();
            startWidth = getWidth();
            startLeft = getLayoutX();
            if (Math.abs(diffX) <= HALF_STROKE) {
                dragPosition = IntervalSelector.DragPosition.LEFT;
            } else if (Math.abs(diffX + getWidth()) <= HALF_STROKE) {
                dragPosition = IntervalSelector.DragPosition.RIGHT;
            } else {
                dragPosition = IntervalSelector.DragPosition.CENTER;
            }
            mousePress.consume();
        });

        setOnMouseReleased((MouseEvent mouseRelease) -> {
            isDragging.set(false);
            mouseRelease.consume();
        });

        setOnMouseDragged(mouseDrag -> {
            isDragging.set(true);
            double deltaX = mouseDrag.getScreenX() - startDragX;
            switch (dragPosition) {
                case CENTER:
                    setLayoutX(startLeft + deltaX);
                    break;
                case LEFT:
                    if (deltaX > startWidth) {
                        startDragX = mouseDrag.getScreenX();
                        startWidth = 0;
                        dragPosition = DragPosition.RIGHT;
                    } else {
                        setLayoutX(startLeft + deltaX);
                        setPrefWidth(startWidth - deltaX);
                        autosize();
                    }
                    break;
                case RIGHT:
                    Point2D parentMouse = getLocalMouseCoords(mouseDrag);
                    if (parentMouse.getX() < startLeft) {
                        dragPosition = DragPosition.LEFT;
                        startDragX = mouseDrag.getScreenX();
                        startWidth = 0;
                    } else {
                        setPrefWidth(startWidth + deltaX);
                        autosize();
                    }
                    break;
            }
            mouseDrag.consume();
        });

        setOnMouseClicked(mouseClick -> {
            if (mouseClick.getButton() == MouseButton.SECONDARY) {
                chart.clearIntervalSelector();
            } else if (mouseClick.getClickCount() >= 2) {
                zoomToSelectedInterval();
                mouseClick.consume();
            }
        });

        ActionUtils.configureButton(new ZoomToSelectedIntervalAction(), zoomButton);
        ActionUtils.configureButton(new ClearSelectedIntervalAction(), closeButton);
    }

    private Point2D getLocalMouseCoords(MouseEvent mouseEvent) {
        return getParent().sceneToLocal(new Point2D(mouseEvent.getSceneX(), mouseEvent.getSceneY()));
    }

    @NbBundle.Messages({
        "IntervalSelector.zoomToSelectedInterval.errorMessage=Error zooming in to the selected interval."})
    private void zoomToSelectedInterval() {
        //convert to DateTimes, using max/min if null(off axis)
        DateTime start = parseDateTime(getSpanStart());
        DateTime end = parseDateTime(getSpanEnd());
        Interval interval = adjustInterval(start.isBefore(end) ? new Interval(start, end) : new Interval(end, start));
        try {
            controller.pushTimeRange(interval);
        } catch (TskCoreException ex) {
            Notifications.create().owner(getScene().getWindow())
                    .text(Bundle.IntervalSelector_zoomToSelectedInterval_errorMessage())
                    .showError();
            logger.log(Level.SEVERE, "Error zooming in to the selected interval.");
        }
    }

    /**
     *
     * @param interval the interval represented by this selector
     *
     * @return a modified version of {@code i} adjusted to suite the needs of
     *         the concrete implementation
     */
    protected abstract Interval adjustInterval(Interval interval);

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
     * parse an x-axis value to a DateTime
     *
     * @param date a x-axis value of type X
     *
     * @return a DateTime corresponding to the given x-axis value
     */
    protected abstract DateTime parseDateTime(X date);

    @NbBundle.Messages(value = {"# {0} - start timestamp",
        "# {1} - end timestamp",
        "Timeline.ui.TimeLineChart.tooltip.text=Double-click to zoom into range:\n{0} to {1}.\n\nRight-click to close."})
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
        return getValueForDisplay(getBoundsInParent().getMaxX());
    }

    /**
     * @return the value along the x-axis corresponding to the right edge of the
     *         selector
     */
    public X getSpanStart() {
        return getValueForDisplay(getBoundsInParent().getMinX());
    }

    private X getValueForDisplay(final double displayX) {
        return chart.getXAxis().getValueForDisplay(chart.getXAxis().parentToLocal(displayX, 0).getX());
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
            setEventHandler(actionEvent -> zoomToSelectedInterval());
        }
    }

    private class ClearSelectedIntervalAction extends Action {

        @NbBundle.Messages("IntervalSelector.ClearSelectedIntervalAction.tooltTipText=Clear Selected Interval")
        ClearSelectedIntervalAction() {
            super("");
            setLongText(Bundle.IntervalSelector_ClearSelectedIntervalAction_tooltTipText());
            setGraphic(new ImageView(CLEAR_INTERVAL_ICON));
            setEventHandler(ationEvent -> chart.clearIntervalSelector());
        }
    }

    public interface IntervalSelectorProvider<X> {

        TimeLineController getController();

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
         * Clear any references to previous interval selectors , including
         * removing the interval selector from the UI / scene-graph.
         */
        void clearIntervalSelector();

        Axis<X> getXAxis();
    }
}
