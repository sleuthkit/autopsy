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
package org.sleuthkit.autopsy.timeline.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.MaskerPane;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 * Abstract base class for TimeLineChart based visualizations.
 *
 * @param <X>         The type of data plotted along the x axis
 * @param <Y>         The type of data plotted along the y axis
 * @param <NodeType>  The type of nodes used to represent data items
 * @param <ChartType> The type of the TimeLineChart<X> this class uses to plot
 *                    the data. Must extend Region.
 *
 * TODO: this is becoming (too?) closely tied to the notion that their is a
 * XYChart doing the rendering. Is this a good idea? -jm
 *
 * TODO: pull up common history context menu items out of derived classes? -jm
 */
public abstract class AbstractVisualizationPane<X, Y, NodeType extends Node, ChartType extends Region & TimeLineChart<X>> extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(AbstractVisualizationPane.class.getName());

    @NbBundle.Messages("AbstractVisualization.Default_Tooltip.text=Drag the mouse to select a time interval to zoom into.\nRight-click for more actions.")
    private static final Tooltip DEFAULT_TOOLTIP = new Tooltip(Bundle.AbstractVisualization_Default_Tooltip_text());
    private static final Border ONLY_LEFT_BORDER = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(0, 0, 0, 1)));

    /**
     * Get the tool tip to use for this visualization when no more specific
     * Tooltip is needed.
     *
     * @return The default Tooltip.
     */
    public static Tooltip getDefaultTooltip() {
        return DEFAULT_TOOLTIP;
    }

    /**
     * Boolean property that holds true if the visualization may not represent
     * the current state of the DB, because, for example, tags have been updated
     * but the vis. was not refreshed.
     */
    private final ReadOnlyBooleanWrapper outOfDate = new ReadOnlyBooleanWrapper(false);

    /**
     * Boolean property that holds true if the visualization does not show any
     * events with the current zoom and filter settings.
     */
    private final ReadOnlyBooleanWrapper hasVisibleEvents = new ReadOnlyBooleanWrapper(true);

    /**
     * Access to chart data via series
     */
    protected final ObservableList<XYChart.Series<X, Y>> dataSeries = FXCollections.<XYChart.Series<X, Y>>observableArrayList();
    protected final Map<EventType, XYChart.Series<X, Y>> eventTypeToSeriesMap = new HashMap<>();

    private ChartType chart;

    //// replacement axis label componenets
    private final Pane specificLabelPane = new Pane(); // container for the specfic labels in the decluttered axis
    private final Pane contextLabelPane = new Pane();// container for the contextual labels in the decluttered axis
    private final Region spacer = new Region();

    /**
     * task used to reload the content of this visualization
     */
    private Task<Boolean> updateTask;

    final private TimeLineController controller;
    final private FilteredEventsModel filteredEvents;

    final private ObservableList<NodeType> selectedNodes = FXCollections.observableArrayList();

    /**
     * Listener that is attached to various properties that should trigger a vis
     * update when they change.
     */
    private InvalidationListener updateListener = any -> refresh();

    /**
     * Does the visualization represent an out-of-date state of the DB. It might
     * if, for example, tags have been updated but the vis. was not refreshed.
     *
     * @return True if the visualization does not represent the curent state of
     *         the DB.
     */
    public boolean isOutOfDate() {
        return outOfDate.get();
    }

    /**
     * Set this visualization out of date because, for example, tags have been
     * updated but the vis. was not refreshed.
     */
    void setOutOfDate() {
        outOfDate.set(true);
    }

    /**
     * Get a ReadOnlyBooleanProperty that holds true if this visualization does
     * not represent the current state of the DB>
     *
     * @return A ReadOnlyBooleanProperty that holds the out-of-date state for
     *         this visualization.
     */
    public ReadOnlyBooleanProperty outOfDateProperty() {
        return outOfDate.getReadOnlyProperty();
    }

    /**
     * The visualization nodes that are selected.
     *
     * @return An ObservableList<NodeType> of the nodes that are selected in
     *         this visualization.
     */
    protected ObservableList<NodeType> getSelectedNodes() {
        return selectedNodes;
    }

    /**
     * List of Nodes to insert into the toolbar. This should be set in an
     * implementations constructor.
     */
    private List<Node> settingsNodes;

    /**
     * Get a List of nodes containing settings widgets to insert into this
     * visualization's header.
     *
     * @return The List of settings Nodes.
     */
    protected List<Node> getSettingsNodes() {
        return Collections.unmodifiableList(settingsNodes);
    }

    /**
     * Set the List of nodes containing settings widgets to insert into this
     * visualization's header.
     *
     *
     * @param settingsNodes The List of nodes containing settings widgets to
     *                      insert into this visualization's header.
     */
    protected void setSettingsNodes(List<Node> settingsNodes) {
        this.settingsNodes = new ArrayList<>(settingsNodes);
    }

    /**
     * Get the TimelineController for this visualization.
     *
     * @return The TimelineController for this visualization.
     */
    protected TimeLineController getController() {
        return controller;
    }

    /**
     * Get the CharType that implements this visualization.
     *
     * @return The CharType that implements this visualization.
     */
    protected ChartType getChart() {
        return chart;
    }

    /**
     * Get the FilteredEventsModel for this visualization.
     *
     * @return The FilteredEventsModel for this visualization.
     */
    protected FilteredEventsModel getEventsModel() {
        return filteredEvents;
    }

    /**
     * Set the ChartType that implements this visualization.
     *
     * @param chart The ChartType that implements this visualization.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    protected void setChart(ChartType chart) {
        this.chart = chart;
        setCenter(chart);
    }

    /**
     * A property that indicates whether there are any events visible in this
     * visualization with the current view parameters.
     *
     * @return A property that indicates whether there are any events visible in
     *         this visualization with the current view parameters.
     */
    ReadOnlyBooleanProperty hasVisibleEventsProperty() {
        return hasVisibleEvents.getReadOnlyProperty();
    }

    /**
     * Are there are any events visible in this visualization with the current
     * view parameters?
     *
     * @return True if there are events visible in this visualization with the
     *         current view parameters.
     */
    boolean hasVisibleEvents() {
        return hasVisibleEventsProperty().get();
    }

    /**
     * Apply this visualization's 'selection effect' to the given node.
     *
     * @param node The node to apply the 'effect' to.
     */
    protected void applySelectionEffect(NodeType node) {
        applySelectionEffect(node, true);
    }

    /**
     * Remove this visualization's 'selection effect' from the given node.
     *
     * @param node The node to remvoe the 'effect' from.
     */
    protected void removeSelectionEffect(NodeType node) {
        applySelectionEffect(node, Boolean.FALSE);
    }

    /**
     * Should the tick mark at the given value be bold, because it has
     * interesting data associated with it?
     *
     * @param value A value along this visualization's x axis
     *
     * @return True if the tick label for the given value should be bold ( has
     *         relevant data), false otherwise
     */
    abstract protected Boolean isTickBold(X value);

    /**
     * Apply this visualization's 'selection effect' to the given node, if
     * applied is true. If applied is false, remove the affect
     *
     * @param node    The node to apply the 'effect' to
     * @param applied True if the effect should be applied, false if the effect
     *                should not
     */
    abstract protected void applySelectionEffect(NodeType node, Boolean applied);

    /**
     * Get a new background Task that fetches the appropriate data and loads it
     * into this visualization.
     *
     * @return A new task to execute on a background thread to reload this
     *         visualization with different data.
     */
    abstract protected Task<Boolean> getNewUpdateTask();

    /**
     * Get the label that should be used for a tick mark at the given value.
     *
     * @param tickValue The value to get a label for.
     *
     * @return a String to use for a tick mark label given a tick value.
     */
    abstract protected String getTickMarkLabel(X tickValue);

    /**
     * Get the spacing, in pixels, between tick marks of the horizontal axis.
     * This will be used to layout the decluttered replacement labels.
     *
     * @return The spacing, in pixels, between tick marks of the horizontal axis
     */
    abstract protected double getTickSpacing();

    /**
     * Get the X-Axis of this Visualization's chart
     *
     * @return The horizontal axis used by this Visualization's chart
     */
    abstract protected Axis<X> getXAxis();

    /**
     * Get the Y-Axis of this Visualization's chart
     *
     * @return The vertical axis used by this Visualization's chart
     */
    abstract protected Axis<Y> getYAxis();

    /**
     * Get the total amount of space (in pixels) the x-axis uses to pad the left
     * and right sides. This value is used to keep decluttered axis aligned
     * correctly.
     *
     * @return The x-axis margin (in pixels)
     */
    abstract protected double getAxisMargin();

    /**
     * Clear all data items from this chart.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    abstract protected void clearChartData();

    /**
     * Refresh this visualization based on current state of zoom / filters.
     * Primarily this invokes the background VisualizationUpdateTask returned by
     * getUpdateTask(), which derived classes must implement.
     *
     * TODO: replace this logic with a javafx Service ? -jm
     */
    protected final synchronized void refresh() {
        if (updateTask != null) {
            updateTask.cancel(true);
            updateTask = null;
        }
        updateTask = getNewUpdateTask();
        updateTask.stateProperty().addListener((Observable observable) -> {
            switch (updateTask.getState()) {
                case CANCELLED:
                case FAILED:
                case READY:
                case RUNNING:
                case SCHEDULED:
                    break;
                case SUCCEEDED:
                    try {
                        this.hasVisibleEvents.set(updateTask.get());
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Unexpected exception updating visualization", ex); //NON-NLS
                    }
                    break;
            }
        });
        controller.monitorTask(updateTask);
    }

    /**
     * Dispose of this visualization and any resources it holds onto.
     */
    final synchronized void dispose() {

        //cancel and gc updateTask
        if (updateTask != null) {
            updateTask.cancel(true);
            updateTask = null;
        }
        //remvoe and gc updateListener
        this.filteredEvents.zoomParametersProperty().removeListener(updateListener);
        TimeLineController.getTimeZone().removeListener(updateListener);
        updateListener = null;

        filteredEvents.unRegisterForEvents(this);
    }

    /**
     * Make a series for each event type in a consistent order.
     */
    protected final void createSeries() {
        for (EventType eventType : EventType.allTypes) {
            XYChart.Series<X, Y> series = new XYChart.Series<>();
            series.setName(eventType.getDisplayName());
            eventTypeToSeriesMap.put(eventType, series);
            dataSeries.add(series);
        }
    }

    /**
     * Get the series for the given EventType.
     *
     * @param et The EventType to get the series for
     *
     * @return A Series object to contain all the events with the given
     *         EventType
     */
    protected final XYChart.Series<X, Y> getSeries(final EventType et) {
        return eventTypeToSeriesMap.get(et);
    }

    /**
     * Constructor
     *
     * @param controller The TimelineController for this visualization.
     */
    protected AbstractVisualizationPane(TimeLineController controller) {
        this.controller = controller;
        this.filteredEvents = controller.getEventsModel();
        this.filteredEvents.registerForEvents(this);
        this.filteredEvents.zoomParametersProperty().addListener(updateListener);
        Platform.runLater(() -> {
            VBox vBox = new VBox(specificLabelPane, contextLabelPane);
            vBox.setFillWidth(false);
            HBox hBox = new HBox(spacer, vBox);
            hBox.setFillHeight(false);
            setBottom(hBox);
            DoubleBinding spacerSize = getYAxis().widthProperty().add(getYAxis().tickLengthProperty()).add(getAxisMargin());
            spacer.minWidthProperty().bind(spacerSize);
            spacer.prefWidthProperty().bind(spacerSize);
            spacer.maxWidthProperty().bind(spacerSize);
        });

        createSeries();

        selectedNodes.addListener((ListChangeListener.Change<? extends NodeType> change) -> {
            while (change.next()) {
                change.getRemoved().forEach(node -> applySelectionEffect(node, false));
                change.getAddedSubList().forEach(node -> applySelectionEffect(node, true));
            }
        });

        TimeLineController.getTimeZone().addListener(updateListener);

        //show tooltip text in status bar
        hoverProperty().addListener(hoverProp -> controller.setStatusMessage(isHover() ? DEFAULT_TOOLTIP.getText() : ""));

    }

    /**
     * Iterate through the list of tick-marks building a two level structure of
     * replacement tick mark labels. (Visually) upper level has most
     * detailed/highest frequency part of date/time (specific label). Second
     * level has rest of date/time grouped by unchanging part (contextual
     * label).
     *
     * eg:
     *
     * October-October-31_September-01_September-02_September-03
     *
     * becomes:
     *
     * _________30_________31___________01___________02___________03
     *
     * _________October___________|_____________September___________
     *
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    protected synchronized void layoutDateLabels() {
        //clear old labels
        contextLabelPane.getChildren().clear();
        specificLabelPane.getChildren().clear();
        //since the tickmarks aren't necessarily in value/position order,
        //make a copy of the list sorted by position along axis
        SortedList<Axis.TickMark<X>> tickMarks = getXAxis().getTickMarks().sorted(Comparator.comparing(Axis.TickMark::getPosition));

        if (tickMarks.isEmpty() == false) {
            //get the spacing between ticks in the underlying axis
            double spacing = getTickSpacing();

            //initialize values from first tick
            TwoPartDateTime dateTime = new TwoPartDateTime(getTickMarkLabel(tickMarks.get(0).getValue()));
            String lastSeenContextLabel = dateTime.context;

            //x-positions (pixels) of the current branch and leaf labels
            double specificLabelX = 0;

            if (dateTime.context.isEmpty()) {
                //if there is only one part to the date (ie only year), just add a label for each tick
                for (Axis.TickMark<X> t : tickMarks) {
                    addSpecificLabel(new TwoPartDateTime(getTickMarkLabel(t.getValue())).specifics,
                            spacing,
                            specificLabelX,
                            isTickBold(t.getValue())
                    );
                    specificLabelX += spacing;  //increment x
                }
            } else {
                //there are two parts so ...
                //initialize additional state
                double contextLabelX = 0;
                double contextLabelWidth = 0;

                for (Axis.TickMark<X> t : tickMarks) {
                    //split the label into a TwoPartDateTime
                    dateTime = new TwoPartDateTime(getTickMarkLabel(t.getValue()));

                    //if we are still in the same context
                    if (lastSeenContextLabel.equals(dateTime.context)) {
                        //increment context width
                        contextLabelWidth += spacing;
                    } else {// we are on to a new context, so ...
                        addContextLabel(lastSeenContextLabel, contextLabelWidth, contextLabelX);
                        //and then update label, x-pos, and width
                        lastSeenContextLabel = dateTime.context;
                        contextLabelX += contextLabelWidth;
                        contextLabelWidth = spacing;
                    }
                    //add the specific label (highest frequency part)
                    addSpecificLabel(dateTime.specifics, spacing, specificLabelX, isTickBold(t.getValue()));

                    //increment specific position
                    specificLabelX += spacing;
                }
                //we have reached end so add label for current context
                addContextLabel(lastSeenContextLabel, contextLabelWidth, contextLabelX);
            }
        }
        //request layout since we have modified scene graph structure
        requestParentLayout();
    }

    /**
     * Add a Text Node to the specific label container for the decluttered axis
     * labels.
     *
     * @param labelText  The String to add.
     * @param labelWidth The width, in pixels, of the space available for the
     *                   text.
     * @param labelX     The horizontal position, in pixels, in the specificPane
     *                   of the text.
     * @param bold       True if the text should be bold, false otherwise.
     */
    private synchronized void addSpecificLabel(String labelText, double labelWidth, double labelX, boolean bold) {
        Text label = new Text(" " + labelText + " "); //NON-NLS
        label.setTextAlignment(TextAlignment.CENTER);
        label.setFont(Font.font(null, bold ? FontWeight.BOLD : FontWeight.NORMAL, 10));
        //position label accounting for width
        label.relocate(labelX + labelWidth / 2 - label.getBoundsInLocal().getWidth() / 2, 0);
        label.autosize();

        if (specificLabelPane.getChildren().isEmpty()) {
            //just add first label
            specificLabelPane.getChildren().add(label);
        } else {
            //otherwise don't actually add the label if it would intersect with previous label

            final Node lastLabel = specificLabelPane.getChildren().get(specificLabelPane.getChildren().size() - 1);

            if (false == lastLabel.getBoundsInParent().intersects(label.getBoundsInParent())) {
                specificLabelPane.getChildren().add(label);
            }
        }
    }

    /**
     * Add a Label Node to the contextual label container for the decluttered
     * axis labels.
     *
     * @param labelText  The String to add.
     * @param labelWidth The width, in pixels, of the space to use for the label
     * @param labelX     The horizontal position, in pixels, in the specificPane
     *                   of the text
     */
    private synchronized void addContextLabel(String labelText, double labelWidth, double labelX) {
        Label label = new Label(labelText);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setFont(Font.font(10));
        //use a leading ellipse since that is the lowest frequency part,
        //and can be infered more easily from other surrounding labels
        label.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        //force size
        label.setMinWidth(labelWidth);
        label.setPrefWidth(labelWidth);
        label.setMaxWidth(labelWidth);
        label.relocate(labelX, 0);

        if (labelX == 0) { // first label has no border
            label.setBorder(null);
        } else {  // subsequent labels have border on left to create dividers
            label.setBorder(ONLY_LEFT_BORDER);
        }

        contextLabelPane.getChildren().add(label);
    }

    /**
     * A simple data object used to represent a partial date as up to two parts.
     * A low frequency part (context) containing all but the most specific
     * element, and a highest frequency part containing the most specific
     * element. If there is only one part, it will be in the context and the
     * specifics will equal an empty string
     */
    @Immutable
    private static final class TwoPartDateTime {

        /**
         * The low frequency part of a date/time eg 2001-May-4
         */
        private final String context;

        /**
         * The highest frequency part of a date/time eg 14 (2pm)
         */
        private final String specifics;

        /**
         * Constructor
         *
         * @param dateString The Date/Time to represent, formatted as per
         *                   RangeDivisionInfo.getTickFormatter().
         */
        TwoPartDateTime(String dateString) {
            //find index of separator to split on
            int splitIndex = StringUtils.lastIndexOfAny(dateString, " ", "-", ":"); //NON-NLS
            if (splitIndex < 0) { // there is only one part
                specifics = dateString;
                context = ""; //NON-NLS
            } else { //split at index
                specifics = StringUtils.substring(dateString, splitIndex + 1);
                context = StringUtils.substring(dateString, 0, splitIndex);
            }
        }
    }

    /**
     * Base class for Tasks that refresh a visualization when the view settings
     * change.
     *
     * @param <AxisValuesType> The type of a single object that can represent
     *                         the range of data displayed along the X-Axis.
     */
    abstract protected class VisualizationRefreshTask<AxisValuesType> extends LoggedTask<Boolean> {

        private final Node center;

        /**
         * Constructor
         *
         * @param taskName        The name of this task.
         * @param logStateChanges Whether or not task state changes should be
         *                        logged.
         */
        protected VisualizationRefreshTask(String taskName, boolean logStateChanges) {
            super(taskName, logStateChanges);
            this.center = getCenter();
        }

        /**
         * Sets initial progress value and message and shows blocking progress
         * indicator over the visualization. Derived Tasks should be sure to
         * call this as part of their call() implementation.
         *
         * @return True
         *
         * @throws Exception If there is an unhandled exception during the
         *                   background operation
         */
        @NbBundle.Messages({"VisualizationUpdateTask.preparing=Analyzing zoom and filter settings"})
        @Override
        protected Boolean call() throws Exception {
            updateProgress(-1, 1);
            updateMessage(Bundle.VisualizationUpdateTask_preparing());
            Platform.runLater(() -> {
                MaskerPane maskerPane = new MaskerPane();
                maskerPane.textProperty().bind(messageProperty());
                maskerPane.progressProperty().bind(progressProperty());
                setCenter(new StackPane(center, maskerPane));
                setCursor(Cursor.WAIT);
            });

            return true;
        }

        /**
         * Updates the horizontal axis and removes the blocking progress
         * indicator. Derived Tasks should be sure to call this as part of their
         * succeeded() implementation.
         */
        @Override
        protected void succeeded() {
            super.succeeded();
            outOfDate.set(false);
            layoutDateLabels();
            cleanup();
        }

        /**
         * Removes the blocking progress indicator. Derived Tasks should be sure
         * to call this as part of their cancelled() implementation.
         */
        @Override
        protected void cancelled() {
            super.cancelled();
            cleanup();
        }

        /**
         * Removes the blocking progress indicator. Derived Tasks should be sure
         * to call this as part of their failed() implementation.
         */
        @Override
        protected void failed() {
            super.failed();
            cleanup();
        }

        /**
         * Removes the blocking progress indicator and reset the cursor to the
         * default.
         */
        private void cleanup() {
            setCenter(center); //clear masker pane installed in call()
            setCursor(Cursor.DEFAULT);
        }

        /**
         * Clears the chart data and sets the horizontal axis range. For use
         * within the derived implementation of the call() method.
         *
         * @param axisValues
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.NOT_UI)
        protected void resetChart(AxisValuesType axisValues) {
            Platform.runLater(() -> {
                clearChartData();
                setDateAxisValues(axisValues);
            });
        }

        /**
         * Set the horizontal range that this chart will show.
         *
         * @param values A single object representing the range that this chart
         *               will show.
         */
        abstract protected void setDateAxisValues(AxisValuesType values);
    }
}
