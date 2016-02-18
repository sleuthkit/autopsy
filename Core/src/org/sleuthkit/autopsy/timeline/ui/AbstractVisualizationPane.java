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

import com.google.common.eventbus.Subscribe;
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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.Effect;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.MaskerPane;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.events.RefreshRequestedEvent;

/**
 * Abstract base class for {@link Chart} based {@link TimeLineView}s used in the
 * main visualization area.
 *
 * @param <X>         the type of data plotted along the x axis
 * @param <Y>         the type of data plotted along the y axis
 * @param <NodeType>  the type of nodes used to represent data items
 * @param <ChartType> the type of the {@link XYChart<X,Y>} this class uses to
 *                    plot the data.
 *
 * TODO: this is becoming (too?) closely tied to the notion that their is a
 * {@link XYChart} doing the rendering. Is this a good idea? -jm TODO: pull up
 * common history context menu items out of derived classes? -jm
 */
public abstract class AbstractVisualizationPane<X, Y, NodeType extends Node, ChartType extends XYChart<X, Y> & TimeLineChart<X>> extends BorderPane {

    @NbBundle.Messages("AbstractVisualization.Default_Tooltip.text=Drag the mouse to select a time interval to zoom into.\nRight-click for more actions.")
    private static final Tooltip DEFAULT_TOOLTIP = new Tooltip(Bundle.AbstractVisualization_Default_Tooltip_text());
    private static final Logger LOGGER = Logger.getLogger(AbstractVisualizationPane.class.getName());

    public static Tooltip getDefaultTooltip() {
        return DEFAULT_TOOLTIP;
    }

    protected final SimpleBooleanProperty hasEvents = new SimpleBooleanProperty(true);

    /**
     * access to chart data via series
     */
    protected final ObservableList<XYChart.Series<X, Y>> dataSeries = FXCollections.<XYChart.Series<X, Y>>observableArrayList();
    protected final Map<EventType, XYChart.Series<X, Y>> eventTypeToSeriesMap = new HashMap<>();

    protected ChartType chart;

    //// replacement axis label componenets
    private final Pane leafPane; // container for the leaf lables in the declutterd axis
    private final Pane branchPane;// container for the branch lables in the declutterd axis
    protected final Region spacer;

    /**
     * task used to reload the content of this visualization
     */
    private Task<Boolean> updateTask;

    final protected TimeLineController controller;

    final protected FilteredEventsModel filteredEvents;

    final protected ObservableList<NodeType> selectedNodes = FXCollections.observableArrayList();

    private InvalidationListener invalidationListener = (Observable observable) -> {
        update();
    };

    public ObservableList<NodeType> getSelectedNodes() {
        return selectedNodes;
    }

    /**
     * list of {@link Node}s to insert into the toolbar. This should be set in
     * an implementations constructor.
     */
    protected List<Node> settingsNodes;

    /**
     * @return the list of nodes containing settings widgets to insert into this
     *         visualization's header
     */
    protected List<Node> getSettingsNodes() {
        return Collections.unmodifiableList(settingsNodes);
    }

    /**
     * @param value a value along this visualization's x axis
     *
     * @return true if the tick label for the given value should be bold ( has
     *         relevant data), false* otherwise
     */
    abstract protected Boolean isTickBold(X value);

    /**
     * apply this visualization's 'selection effect' to the given node
     *
     * @param node    the node to apply the 'effect' to
     * @param applied true if the effect should be applied, false if the effect
     *                should
     */
    abstract protected void applySelectionEffect(NodeType node, Boolean applied);

    /**
     * @return a task to execute on a background thread to reload this
     *         visualization with different data.
     */
    abstract protected Task<Boolean> getUpdateTask();

    /**
     * @return return the {@link Effect} applied to 'selected nodes' in this
     *         visualization, or null if selection is visualized via another
     *         mechanism
     */
    abstract protected Effect getSelectionEffect();

    /**
     * @param tickValue
     *
     * @return a String to use for a tick mark label given a tick value
     */
    abstract protected String getTickMarkLabel(X tickValue);

    /**
     * the spacing (in pixels) between tick marks of the horizontal axis. This
     * will be used to layout the decluttered replacement labels.
     *
     * @return the spacing in pixels between tick marks of the horizontal axis
     */
    abstract protected double getTickSpacing();

    /**
     * @return the horizontal axis used by this Visualization's chart
     */
    abstract protected Axis<X> getXAxis();

    /**
     * @return the vertical axis used by this Visualization's chart
     */
    abstract protected Axis<Y> getYAxis();

    abstract protected void resetData();

    /**
     * update this visualization based on current state of zoom / filters.
     * Primarily this invokes the background {@link VisualizationUpdateTask}
     * returned by {@link #getUpdateTask()}, which derived classes must
     * implement.
     *
     * TODO: replace this logic with a {@link Service} ? -jm
     */
    final synchronized public void update() {
        if (updateTask != null) {
            updateTask.cancel(true);
            updateTask = null;
        }
        updateTask = getUpdateTask();
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
                        this.hasEvents.set(updateTask.get());
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Unexpected exception updating visualization", ex); //NOI18N NON-NLS
                    }
                    break;
            }
        });
        controller.monitorTask(updateTask);
    }

    final synchronized public void dispose() {
        if (updateTask != null) {
            updateTask.cancel(true);
        }
        this.filteredEvents.zoomParametersProperty().removeListener(invalidationListener);
        invalidationListener = null;
    }

    /**
     * make a series for each event type in a consistent order
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
     *
     * @param et the EventType to get the series for
     *
     * @return a Series object to contain all the events with the given
     *         EventType
     */
    protected final XYChart.Series<X, Y> getSeries(final EventType et) {
        return eventTypeToSeriesMap.get(et);
    }

    protected AbstractVisualizationPane(TimeLineController controller, Pane partPane, Pane contextPane, Region spacer) {
        this.controller = controller;
        this.filteredEvents = controller.getEventsModel();
        this.filteredEvents.registerForEvents(this);
        this.filteredEvents.zoomParametersProperty().addListener(invalidationListener);
        this.leafPane = partPane;
        this.branchPane = contextPane;
        this.spacer = spacer;

        createSeries();

        selectedNodes.addListener((ListChangeListener.Change<? extends NodeType> c) -> {
            while (c.next()) {
                c.getRemoved().forEach(n -> applySelectionEffect(n, false));
                c.getAddedSubList().forEach(n -> applySelectionEffect(n, true));
            }
        });

        TimeLineController.getTimeZone().addListener(invalidationListener);

        //show tooltip text in status bar
        hoverProperty().addListener(observable -> controller.setStatus(isHover() ? DEFAULT_TOOLTIP.getText() : ""));

    }

    @Subscribe
    public void handleRefreshRequested(RefreshRequestedEvent event) {
        update();
    }

    /**
     * iterate through the list of tick-marks building a two level structure of
     * replacement tick marl labels. (Visually) upper level has most
     * detailed/highest frequency part of date/time. Second level has rest of
     * date/time grouped by unchanging part. eg:
     *
     *
     * october-30_october-31_september-01_september-02_september-03
     *
     * becomes
     *
     * _________30_________31___________01___________02___________03
     *
     * _________october___________|_____________september___________
     *
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public synchronized void layoutDateLabels() {

        //clear old labels
        branchPane.getChildren().clear();
        leafPane.getChildren().clear();
        //since the tickmarks aren't necessarily in value/position order,
        //make a clone of the list sorted by position along axis
        ObservableList<Axis.TickMark<X>> tickMarks = FXCollections.observableArrayList(getXAxis().getTickMarks());
        tickMarks.sort(Comparator.comparing(Axis.TickMark::getPosition));

        if (tickMarks.isEmpty() == false) {
            //get the spacing between ticks in the underlying axis
            double spacing = getTickSpacing();

            //initialize values from first tick
            TwoPartDateTime dateTime = new TwoPartDateTime(getTickMarkLabel(tickMarks.get(0).getValue()));
            String lastSeenBranchLabel = dateTime.branch;
            //cumulative width of the current branch label

            //x-positions (pixels) of the current branch and leaf labels
            double leafLabelX = 0;

            if (dateTime.branch.isEmpty()) {
                //if there is only one part to the date (ie only year), just add a label for each tick
                for (Axis.TickMark<X> t : tickMarks) {
                    assignLeafLabel(new TwoPartDateTime(getTickMarkLabel(t.getValue())).leaf,
                            spacing,
                            leafLabelX,
                            isTickBold(t.getValue())
                    );

                    leafLabelX += spacing;  //increment x
                }
            } else {
                //there are two parts so ...
                //initialize additional state
                double branchLabelX = 0;
                double branchLabelWidth = 0;

                for (Axis.TickMark<X> t : tickMarks) {               //for each tick

                    //split the label into a TwoPartDateTime
                    dateTime = new TwoPartDateTime(getTickMarkLabel(t.getValue()));

                    //if we are still on the same branch
                    if (lastSeenBranchLabel.equals(dateTime.branch)) {
                        //increment branch width
                        branchLabelWidth += spacing;
                    } else {// we are on to a new branch, so ...
                        assignBranchLabel(lastSeenBranchLabel, branchLabelWidth, branchLabelX);
                        //and then update label, x-pos, and width
                        lastSeenBranchLabel = dateTime.branch;
                        branchLabelX += branchLabelWidth;
                        branchLabelWidth = spacing;
                    }
                    //add the label for the leaf (highest frequency part)
                    assignLeafLabel(dateTime.leaf, spacing, leafLabelX, isTickBold(t.getValue()));

                    //increment leaf position
                    leafLabelX += spacing;
                }
                //we have reached end so add branch label for current branch
                assignBranchLabel(lastSeenBranchLabel, branchLabelWidth, branchLabelX);
            }
        }
        //request layout since we have modified scene graph structure
        requestParentLayout();
    }

    protected void setChartClickHandler() {
        chart.addEventHandler(MouseEvent.MOUSE_CLICKED, (MouseEvent event) -> {
            if (event.getButton() == MouseButton.PRIMARY && event.isStillSincePress()) {
                selectedNodes.clear();
            }
        });
    }

    /**
     * add a {@link Text} node to the leaf container for the decluttered axis
     * labels
     *
     * @param labelText  the string to add
     * @param labelWidth the width of the space available for the text
     * @param labelX     the horizontal position in the partPane of the text
     * @param bold       true if the text should be bold, false otherwise
     */
    private synchronized void assignLeafLabel(String labelText, double labelWidth, double labelX, boolean bold) {

        Text label = new Text(" " + labelText + " "); //NOI18N
        label.setTextAlignment(TextAlignment.CENTER);
        label.setFont(Font.font(null, bold ? FontWeight.BOLD : FontWeight.NORMAL, 10));
        //position label accounting for width
        label.relocate(labelX + labelWidth / 2 - label.getBoundsInLocal().getWidth() / 2, 0);
        label.autosize();

        if (leafPane.getChildren().isEmpty()) {
            //just add first label
            leafPane.getChildren().add(label);
        } else {
            //otherwise don't actually add the label if it would intersect with previous label
            final Text lastLabel = (Text) leafPane.getChildren().get(leafPane.getChildren().size() - 1);

            if (!lastLabel.getBoundsInParent().intersects(label.getBoundsInParent())) {
                leafPane.getChildren().add(label);
            }
        }
    }

    /**
     * add a {@link Label} node to the branch container for the decluttered axis
     * labels
     *
     * @param labelText  the string to add
     * @param labelWidth the width of the space to use for the label
     * @param labelX     the horizontal position in the partPane of the text
     */
    private synchronized void assignBranchLabel(String labelText, double labelWidth, double labelX) {

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
            label.setStyle("-fx-border-width: 0 0 0 0 ; -fx-border-color:black;"); // NON-NLS //NOI18N
        } else {  // subsequent labels have border on left to create dividers
            label.setStyle("-fx-border-width: 0 0 0 1; -fx-border-color:black;"); // NON-NLS //NOI18N
        }

        branchPane.getChildren().add(label);
    }

    /**
     * A simple data object used to represent a partial date as up to two parts.
     * A low frequency part (branch) containing all but the most specific
     * element, and a highest frequency part (leaf) containing the most specific
     * element. The branch and leaf names come from thinking of the space of all
     * date times as a tree with higher frequency information further from the
     * root. If there is only one part, it will be in the branch and the leaf
     * will equal an empty string
     */
    @Immutable
    private static final class TwoPartDateTime {

        /**
         * the low frequency part of a date/time eg 2001-May-4
         */
        private final String branch;

        /**
         * the highest frequency part of a date/time eg 14 (2pm)
         */
        private final String leaf;

        TwoPartDateTime(String dateString) {
            //find index of separator to spit on
            int splitIndex = StringUtils.lastIndexOfAny(dateString, " ", "-", ":"); //NOI18N
            if (splitIndex < 0) { // there is only one part
                leaf = dateString;
                branch = ""; //NOI18N
            } else { //split at index
                leaf = StringUtils.substring(dateString, splitIndex + 1);
                branch = StringUtils.substring(dateString, 0, splitIndex);
            }
        }
    }

    protected Interval getTimeRange() {
        return filteredEvents.timeRangeProperty().get();
    }

    abstract protected class VisualizationUpdateTask<AxisValuesType> extends LoggedTask<Boolean> {

        protected VisualizationUpdateTask(String taskName, boolean logStateChanges) {
            super(taskName, logStateChanges);
        }

        /**
         * Sets initial progress value and message and shows blocking progress
         * indicator over the visualization. Derived Tasks should be sure to
         * call this as part of their call() implementation.
         *
         * @return true
         *
         * @throws Exception
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
        private final Node center = getCenter();

        /**
         * updates the horisontal axis and removes the blocking progress
         * indicator. Derived Tasks should be sure to call this as part of their
         * succeeded() implementation.
         */
        @Override
        protected void succeeded() {
            super.succeeded();
            layoutDateLabels();

            Platform.runLater(() -> {
                setCenter(center); //clear masker pane
                setCursor(Cursor.DEFAULT);
            });
        }

        /**
         * Clears the chart data and sets the horisontal axis range. For use
         * within the derived impementation of {@link #call() }
         *
         * @param axisValues
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.NOT_UI)
        protected void resetChart(AxisValuesType axisValues) {
            resetData();
            Platform.runLater(() -> {
                setDateAxisValues(axisValues);
            });

        }

        abstract protected void setDateAxisValues(AxisValuesType values);
    }
}
