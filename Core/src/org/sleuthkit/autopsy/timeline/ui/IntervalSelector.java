/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui;

import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.chart.Axis;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
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
 * This abstract class requires concrete implementations to implement hook
 * methods to handle formating and date 'lookup' of the generic x-axis type
 */
public abstract class IntervalSelector<X> extends BorderPane {

    private static final double STROKE_WIDTH = 3;
    private static final double HALF_STROKE = STROKE_WIDTH / 2;
    /**
     * the Axis this is a selector over
     */
    private final Axis<X> dateAxis;
    private Tooltip tooltip;
    /////////drag state
    private DragPosition dragPosition;
    private double startLeft;
    private double startX;
    private double startWidth;
    /////////end drag state
    private TimeLineController controller;

    public IntervalSelector(Axis<X> dateAxis, TimeLineController controller) {
        this.dateAxis = dateAxis;
        this.controller = controller;
        FXMLConstructor.construct(this, "TimeZonePanel.fxml"); // NON-NLS

    }

    
    
    /**
     *
     * @param x          the initial x position of this selector
     * @param height     the initial height of this selector
     * @param axis       the {@link Axis<X>} this is a selector over
     * @param controller the controller to invoke when this selector is double
     *                   clicked
     */
    public IntervalSelector(double x, double height, Axis<X> axis, TimeLineController controller) {
        
        setMaxHeight(USE_PREF_SIZE);
        setMinHeight(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        setMinWidth(USE_PREF_SIZE);
        dateAxis = axis;
        setBorder(new Border(new BorderStroke(Color.BLUE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(STROKE_WIDTH))));
        //            setStroke(Color.BLUE);
        //            setStrokeWidth(STROKE_WIDTH);
        setBackground(new Background(new BackgroundFill(Color.BLUE.deriveColor(0, 1, 1, 0.5), CornerRadii.EMPTY, Insets.EMPTY)));
        setOpacity(0.5);
        widthProperty().addListener((o) -> {
            setTooltip();
        });
        layoutXProperty().addListener((o) -> {
            setTooltip();
        });
        setTooltip();
        setOnMouseMoved((MouseEvent event) -> {
            Point2D localMouse = sceneToLocal(new Point2D(event.getSceneX(), event.getSceneY()));
            final double diffX = getLayoutX() - localMouse.getX();
            if (Math.abs(diffX) <= HALF_STROKE || Math.abs(diffX + getWidth()) <= HALF_STROKE) {
                //if the mouse is over the stroke, show the resize cursor
                setCursor(Cursor.H_RESIZE);
            } else {
                setCursor(Cursor.HAND);
            }
        });
        setOnMousePressed((MouseEvent event) -> {
            Point2D localMouse = sceneToLocal(new Point2D(event.getSceneX(), event.getSceneY()));
            final double diffX = getLayoutX() - localMouse.getX();
            startX = event.getX();
            startWidth = getWidth();
            startLeft = getLayoutX();
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
                    relocate(startLeft + dX, 0);
                    break;
                case LEFT:
                    relocate(startLeft + dX, 0);
                    setPrefWidth(startWidth - dX);
                    break;
                case RIGHT:
                    setPrefWidth(startWidth + dX);
                    break;
            }
            event.consume();
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
                    event.consume();
                }
            }
        });
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

    @NbBundle.Messages(value = {"# {0} - start timestamp", "# {1} - end timestamp", "Timeline.ui.TimeLineChart.tooltip.text=Double-click to zoom into range:\n{0} to {1}\nRight-click to clear."})
    private void setTooltip() {
        final X start = getSpanStart();
        final X end = getSpanEnd();
        Tooltip.uninstall(this, tooltip);
        tooltip = new Tooltip(Bundle.Timeline_ui_TimeLineChart_tooltip_text(formatSpan(start), formatSpan(end)));
        Tooltip.install(this, tooltip);
    }

    /**
     * @return the value along the x-axis corresponding to the left edge of the
     *         selector
     */
    public X getSpanEnd() {
        return dateAxis.getValueForDisplay(dateAxis.parentToLocal(getBoundsInParent().getMaxX(), 0).getX());
    }

    /**
     * @return the value along the x-axis corresponding to the right edge of the
     *         selector
     */
    public X getSpanStart() {
        return dateAxis.getValueForDisplay(dateAxis.parentToLocal(getBoundsInParent().getMinX(), 0).getX());
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

}
