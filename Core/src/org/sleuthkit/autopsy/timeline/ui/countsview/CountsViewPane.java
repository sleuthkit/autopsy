/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.countsview;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.controlsfx.control.PopOver;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import org.sleuthkit.autopsy.timeline.ui.AbstractTimelineChart;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * FXML Controller class for a StackedBarChart<String,Number> based
 * implementation of a TimeLineChart.
 *
 * This class listens to changes in the assigned FilteredEventsModel and updates
 * the internal EventCountsChart to reflect the currently requested events.
 *
 * This class captures input from the user in the form of mouse clicks on graph
 * bars, and forwards them to the assigned TimeLineController
 *
 * Concurrency Policy: Access to the private members stackedBarChart, countAxis,
 * dateAxis, EventTypeMap, and dataSets affects the stackedBarChart so they all
 * must only be manipulated on the JavaFx thread (through
 * Platform.runLater(java.lang.Runnable). The FilteredEventsModel should
 * encapsulate all need synchronization internally.
 */
public class CountsViewPane extends AbstractTimelineChart<String, Number, Node, EventCountsChart> {

    private static final Logger logger = Logger.getLogger(CountsViewPane.class.getName());

    private final NumberAxis countAxis = new NumberAxis();
    private final CategoryAxis dateAxis = new CategoryAxis(FXCollections.<String>observableArrayList());

    private final SimpleObjectProperty<Scale> scaleProp = new SimpleObjectProperty<>(Scale.LOGARITHMIC);

    @Override
    protected String getTickMarkLabel(String labelValueString) {
        return labelValueString;
    }

    @Override
    protected Boolean isTickBold(String value) {
        return dataSeries.stream().flatMap(series -> series.getData().stream())
                .anyMatch(data -> data.getXValue().equals(value) && data.getYValue().intValue() > 0);
    }

    @Override
    protected Task<Boolean> getNewUpdateTask() {
        return new CountsUpdateTask();
    }

    /**
     * Constructor
     *
     * @param controller The TimelineController for this view.
     */
    @NbBundle.Messages({
        "# {0} - scale name",
        "CountsViewPane.numberOfEvents=Number of Events ({0})"})
    public CountsViewPane(TimeLineController controller) {
        super(controller);

        setChart(new EventCountsChart(controller, dateAxis, countAxis, getSelectedNodes()));
        getChart().setData(dataSeries);
        Tooltip.install(getChart(), getDefaultTooltip());

        dateAxis.getTickMarks().addListener((Observable tickMarks) -> layoutDateLabels());
        dateAxis.categorySpacingProperty().addListener((Observable spacing) -> layoutDateLabels());
        dateAxis.getCategories().addListener((Observable categories) -> layoutDateLabels());

        //bind tick visibility to scaleProp
        BooleanBinding scaleIsLinear = scaleProp.isEqualTo(Scale.LINEAR);
        countAxis.tickLabelsVisibleProperty().bind(scaleIsLinear);
        countAxis.tickMarkVisibleProperty().bind(scaleIsLinear);
        countAxis.minorTickVisibleProperty().bind(scaleIsLinear);
        scaleProp.addListener(scale -> {
            refresh();
            syncAxisScaleLabel();
        });
        syncAxisScaleLabel();
    }

    @Override
    final protected NumberAxis getYAxis() {
        return countAxis;
    }

    @Override
    final protected CategoryAxis getXAxis() {
        return dateAxis;
    }

    @Override
    protected double getTickSpacing() {
        return dateAxis.getCategorySpacing();
    }

    @Override
    protected void applySelectionEffect(Node c1, Boolean applied) {
        c1.setEffect(applied ? getChart().getSelectionEffect() : null);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @Override
    protected void clearData() {
        for (XYChart.Series<String, Number> series : dataSeries) {
            series.getData().clear();
        }
        dataSeries.clear();
        eventTypeToSeriesMap.clear();
        createSeries();
    }

    @Override
    final protected ViewMode getViewMode() {
        return ViewMode.COUNTS;
    }

    @Override
    protected ImmutableList<Node> getSettingsControls() {
        return ImmutableList.copyOf(new CountsViewSettingsPane().getChildrenUnmodifiable());
    }

    @Override
    protected boolean hasCustomTimeNavigationControls() {
        return false;
    }

    @Override
    protected ImmutableList<Node> getTimeNavigationControls() {
        return ImmutableList.of();
    }

    /**
     * Set the appropriate label on the vertical axis, depending on the selected
     * scale.
     */
    private void syncAxisScaleLabel() {
        countAxis.setLabel(Bundle.CountsViewPane_numberOfEvents(scaleProp.get().getDisplayName()));
    }

    /**
     * Enum for the Scales available in the Counts View.
     */
    @NbBundle.Messages({
        "ScaleType.Linear=Linear",
        "ScaleType.Logarithmic=Logarithmic"})
    private static enum Scale implements Function<Long, Double> {

        LINEAR(Bundle.ScaleType_Linear()) {
            @Override
            public Double apply(Long inValue) {
                return inValue.doubleValue();
            }
        },
        LOGARITHMIC(Bundle.ScaleType_Logarithmic()) {
            @Override
            public Double apply(Long inValue) {
                return Math.log10(inValue) + 1;
            }
        };

        private final String displayName;

        /**
         * Constructor
         *
         * @param displayName The display name for this Scale.
         */
        Scale(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Get the display name of this ScaleType
         *
         * @return The display name.
         */
        private String getDisplayName() {
            return displayName;
        }
    }

    @Override
    protected double getAxisMargin() {
        return dateAxis.getStartMargin() + dateAxis.getEndMargin();
    }

    /*
     * A Pane that contains widgets to adjust settings specific to a
     * CountsViewPane
     */
    private class CountsViewSettingsPane extends HBox {

        @FXML
        private RadioButton logRadio;
        @FXML
        private RadioButton linearRadio;
        @FXML
        private ToggleGroup scaleGroup;

        @FXML
        private Label scaleLabel;

        @FXML
        private ImageView logImageView;
        @FXML
        private ImageView linearImageView;

        @FXML
        @NbBundle.Messages({
            "CountsViewPane.logRadio.text=Logarithmic",
            "CountsViewPane.scaleLabel.text=Scale:",
            "CountsViewPane.scaleHelp.label.text=Scales:   ",
            "CountsViewPane.linearRadio.text=Linear",
            "CountsViewPane.scaleHelpLinear=The linear scale is good for many use cases.  When this scale is selected, the height of the bars represents the counts in a linear, one-to-one fashion, and the y-axis is labeled with values. When the range of values is very large, time periods with low counts may have a bar that is too small to see.  To help the user detect this, the labels for date ranges with events are bold.  To see bars that are too small, there are three options:  adjust the window size so that the timeline has more vertical space, adjust the time range shown so that time periods with larger bars are excluded, or adjust the scale setting to logarithmic.",
            "CountsViewPane.scaleHelpLog=The logarithmic scale represents the number of events in a non-linear way that compresses the difference between large and small numbers. Note that even with the logarithmic scale, an extremely large difference in counts may still produce bars too small to see.  In this case the only option may be to filter events to reduce the difference in counts.  NOTE: Because the logarithmic scale is applied to each event type separately, the meaning of the height of the combined bar is not intuitive, and to emphasize this, no labels are shown on the y-axis with the logarithmic scale. The logarithmic scale should be used to quickly compare the counts ",
            "CountsViewPane.scaleHelpLog2=across time within a type, or across types for one time period, but not both.",
            "CountsViewPane.scaleHelpLog3= The actual counts (available in tooltips or the result viewer) should be used for absolute comparisons.  Use the logarithmic scale with care."})
        void initialize() {
            assert logRadio != null : "fx:id=\"logRadio\" was not injected: check your FXML file 'CountsViewSettingsPane.fxml'."; // NON-NLS
            assert linearRadio != null : "fx:id=\"linearRadio\" was not injected: check your FXML file 'CountsViewSettingsPane.fxml'."; // NON-NLS
            scaleLabel.setText(Bundle.CountsViewPane_scaleLabel_text());
            linearRadio.setText(Bundle.CountsViewPane_linearRadio_text());
            logRadio.setText(Bundle.CountsViewPane_logRadio_text());

            scaleGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
                if (newToggle == linearRadio) {
                    scaleProp.set(Scale.LINEAR);
                } else if (newToggle == logRadio) {
                    scaleProp.set(Scale.LOGARITHMIC);
                }
            });
            logRadio.setSelected(true);

            //make a popup help "window" with a description of the log scale.
            logImageView.setCursor(Cursor.HAND);
            logImageView.setOnMouseClicked(clicked -> {
                Text text = new Text(Bundle.CountsViewPane_scaleHelpLog());
                Text text2 = new Text(Bundle.CountsViewPane_scaleHelpLog2());
                Font baseFont = text.getFont();
                text2.setFont(Font.font(baseFont.getFamily(), FontWeight.BOLD, FontPosture.ITALIC, baseFont.getSize()));
                Text text3 = new Text(Bundle.CountsViewPane_scaleHelpLog3());
                showPopoverHelp(logImageView,
                        Bundle.CountsViewPane_logRadio_text(),
                        logImageView.getImage(),
                        new TextFlow(text, text2, text3));
            });

            //make a popup help "window" with a description of the linear scale.
            linearImageView.setCursor(Cursor.HAND);
            linearImageView.setOnMouseClicked(clicked -> {
                Text text = new Text(Bundle.CountsViewPane_scaleHelpLinear());
                text.setWrappingWidth(480);  //This is a hack to fix the layout.
                showPopoverHelp(linearImageView,
                        Bundle.CountsViewPane_linearRadio_text(),
                        linearImageView.getImage(), text);
            });
        }

        /**
         * Constructor
         */
        CountsViewSettingsPane() {
            FXMLConstructor.construct(this, "CountsViewSettingsPane.fxml"); // NON-NLS
        }
    }

    /**
     *
     * Static utility to to show a Popover with the given Node as owner.
     *
     * @param owner       The owner of the Popover
     * @param headerText  A short String that will be shown in the top-left
     *                    corner of the Popover.
     * @param headerImage An Image that will be shown at the top-right corner of
     *                    the Popover.
     * @param content     The main content of the Popover, shown in the
     *                    bottom-center
     *
     */
    private static void showPopoverHelp(final Node owner, final String headerText, final Image headerImage, final Node content) {
        Pane borderPane = new BorderPane(null, null, new ImageView(headerImage),
                content,
                new Label(headerText));
        borderPane.setPadding(new Insets(10));
        borderPane.setPrefWidth(500);

        PopOver popOver = new PopOver(borderPane);
        popOver.setDetachable(false);
        popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);

        popOver.show(owner);
    }

    /**
     * Task that clears the Chart, fetches new data according to the current
     * ZoomState and loads it into the Chart
     *
     */
    @NbBundle.Messages({
        "CountsViewPane.loggedTask.name=Updating Counts View",
        "CountsViewPane.loggedTask.updatingCounts=Populating view"})
    private class CountsUpdateTask extends ViewRefreshTask<List<String>> {

        CountsUpdateTask() {
            super(Bundle.CountsViewPane_loggedTask_name(), true);
        }

        @Override
        protected void succeeded() {
            super.succeeded();
            layoutDateLabels();
        }

        @Override
        protected Boolean call() throws Exception {
            super.call();
            if (isCancelled()) {
                return null;
            }
            EventsModel eventsModel = getEventsModel();

            final RangeDivision rangeInfo = RangeDivision.getRangeDivision(eventsModel.getTimeRange(), TimeLineController.getJodaTimeZone());
            getChart().setRangeInfo(rangeInfo);  //do we need this.  It seems like a hack.
            List<Interval> intervals = rangeInfo.getIntervals(TimeLineController.getJodaTimeZone());

            //clear old data, and reset ranges and series
            resetView(Lists.transform(intervals, interval -> interval.getStart().toString(rangeInfo.getTickFormatter())));

            updateMessage(Bundle.CountsViewPane_loggedTask_updatingCounts());
            int chartMax = 0;
            int numIntervals = intervals.size();
            Scale activeScale = scaleProp.get();

            /*
             * For each interval, query the database for event counts and add
             * the counts to the chart. Doing this in chunks might seem
             * inefficient but it lets us reuse more cached results as the user
             * navigates to overlapping views.
             */
            for (int i = 0; i < numIntervals; i++) {
                if (isCancelled()) {
                    return null;
                }
                updateProgress(i, numIntervals);
                final Interval interval = intervals.get(i);
                int maxPerInterval = 0;

                //query for current interval
                Map<TimelineEventType, Long> eventCounts = eventsModel.getEventCounts(interval);

                //for each type add data to graph
                for (final TimelineEventType eventType : eventCounts.keySet()) {
                    if (isCancelled()) {
                        return null;
                    }

                    final Long count = eventCounts.get(eventType);
                    if (count > 0) {
                        final String intervalCategory = interval.getStart().toString(rangeInfo.getTickFormatter());
                        final double adjustedCount = activeScale.apply(count);

                        final XYChart.Data<String, Number> dataItem
                                = new XYChart.Data<>(intervalCategory, adjustedCount,
                                        new EventCountsChart.ExtraData(interval, eventType, count));
                        Platform.runLater(() -> getSeries(eventType).getData().add(dataItem));
                        maxPerInterval += adjustedCount;
                    }
                }
                chartMax = Math.max(chartMax, maxPerInterval);
            }

            //adjust vertical axis according to scale type and max counts
            double countAxisUpperbound = 1 + chartMax * 1.2;
            double tickUnit = Scale.LINEAR.equals(activeScale)
                    ? Math.pow(10, Math.max(0, Math.floor(Math.log10(chartMax)) - 1))
                    : Double.MAX_VALUE;

            Platform.runLater(() -> {
                countAxis.setTickUnit(tickUnit);
                countAxis.setUpperBound(countAxisUpperbound);
            });

            return chartMax > 0;  // are there events
        }

        @Override
        protected void setDateValues(List<String> categories) {
            dateAxis.getCategories().setAll(categories);
        }
    }
}
