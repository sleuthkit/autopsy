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
package org.sleuthkit.autopsy.timeline.ui.countsview;

import com.google.common.collect.Lists;
import java.util.ArrayList;
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
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import static org.sleuthkit.autopsy.timeline.ui.countsview.Bundle.*;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;

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
public class CountsViewPane extends AbstractVisualizationPane<String, Number, Node, EventCountsChart> {

    private static final Logger LOGGER = Logger.getLogger(CountsViewPane.class.getName());

    private final NumberAxis countAxis = new NumberAxis();
    private final CategoryAxis dateAxis = new CategoryAxis(FXCollections.<String>observableArrayList());

    private final SimpleObjectProperty<Scale> scaleProp = new SimpleObjectProperty<>(Scale.LOGARITHMIC);

    @Override
    protected String getTickMarkLabel(String labelValueString) {
        return labelValueString;
    }

    @Override
    protected Boolean isTickBold(String value) {
        return dataSeries.stream().flatMap((series) -> series.getData().stream())
                .anyMatch((data) -> data.getXValue().equals(value) && data.getYValue().intValue() > 0);
    }

    @Override
    protected Task<Boolean> getUpdateTask() {
        return new CountsUpdateTask();
    }

    /**
     * Constructor
     *
     * @param controller  The TimelineController for this visualization
     * @param partPane
     * @param contextPane
     * @param spacer
     */
    @NbBundle.Messages({"CountsViewPane.numberOfEvents=Number of Events ({0})"})
    public CountsViewPane(TimeLineController controller, Pane partPane, Pane contextPane, Region spacer) {
        super(controller, partPane, contextPane, spacer);
        chart = new EventCountsChart(controller, dateAxis, countAxis, selectedNodes);
        chart.setData(dataSeries);
        setCenter(chart);
        Tooltip.install(chart, getDefaultTooltip());

        settingsNodes = new ArrayList<>(new CountsViewSettingsPane().getChildrenUnmodifiable());

        dateAxis.getTickMarks().addListener((Observable tickMarks) -> layoutDateLabels());
        dateAxis.categorySpacingProperty().addListener((Observable spacing) -> layoutDateLabels());
        dateAxis.getCategories().addListener((Observable categories) -> layoutDateLabels());

        spacer.minWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));
        spacer.prefWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));
        spacer.maxWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));

        //bind tick visibility to scaleProp
        BooleanBinding scaleIsLinear = scaleProp.isEqualTo(Scale.LINEAR);
        countAxis.tickLabelsVisibleProperty().bind(scaleIsLinear);
        countAxis.tickMarkVisibleProperty().bind(scaleIsLinear);
        countAxis.minorTickVisibleProperty().bind(scaleIsLinear);
        scaleProp.addListener(scale -> {
            update();
            countAxis.setLabel(Bundle.CountsViewPane_numberOfEvents(scaleProp.get().getDisplayName()));
        });
        countAxis.setLabel(Bundle.CountsViewPane_numberOfEvents(scaleProp.get().getDisplayName()));
    }

    @Override
    protected NumberAxis getYAxis() {
        return countAxis;
    }

    @Override
    protected CategoryAxis getXAxis() {
        return dateAxis;
    }

    @Override
    protected double getTickSpacing() {
        return dateAxis.getCategorySpacing();
    }

    @Override
    protected void applySelectionEffect(Node c1, Boolean applied) {
        c1.setEffect(applied ? chart.getSelectionEffect() : null);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @Override
    protected void clearChartData() {
        for (XYChart.Series<String, Number> series : dataSeries) {
            series.getData().clear();
        }
        dataSeries.clear();
        eventTypeToSeriesMap.clear();
        createSeries();
    }

    /**
     * Enum for the Scales available in the Counts View.
     */
    @NbBundle.Messages({
        "ScaleType.Linear=Linear",
        "ScaleType.Logarithmic=Logarithmic"
    })
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
        public String getDisplayName() {
            return displayName;
        }
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
        @NbBundle.Messages({
            "CountsViewPane.logRadio.text=Logarithmic",
            "CountsViewPane.scaleLabel.text=Scale:",
            "CountsViewPane.linearRadio.text=Linear"})
        void initialize() {
            assert logRadio != null : "fx:id=\"logRadio\" was not injected: check your FXML file 'CountsViewSettingsPane.fxml'."; // NON-NLS
            assert linearRadio != null : "fx:id=\"linearRadio\" was not injected: check your FXML file 'CountsViewSettingsPane.fxml'."; // NON-NLS
            logRadio.setSelected(true);
            scaleGroup.selectedToggleProperty().addListener(observable -> {
                if (scaleGroup.getSelectedToggle() == linearRadio) {
                    scaleProp.set(Scale.LINEAR);
                } else if (scaleGroup.getSelectedToggle() == logRadio) {
                    scaleProp.set(Scale.LOGARITHMIC);
                }
            });

            logRadio.setText(CountsViewPane_logRadio_text());
            linearRadio.setText(CountsViewPane_linearRadio_text());
            scaleLabel.setText(CountsViewPane_scaleLabel_text());
        }

        /**
         * Constructor
         */
        CountsViewSettingsPane() {
            FXMLConstructor.construct(this, "CountsViewSettingsPane.fxml"); // NON-NLS
        }
    }

    /**
     * Task that clears the Chart, fetches new data according to the current
     * ZoomParams and loads it into the Chart
     *
     */
    @NbBundle.Messages({
        "CountsViewPane.loggedTask.name=Updating Counts View",
        "CountsViewPane.loggedTask.updatingCounts=Populating visualization"})
    private class CountsUpdateTask extends VisualizationUpdateTask<List<String>> {

        CountsUpdateTask() {
            super(Bundle.CountsViewPane_loggedTask_name(), true);
        }

        @Override
        protected Boolean call() throws Exception {
            super.call();
            if (isCancelled()) {
                return null;
            }

            final RangeDivisionInfo rangeInfo = RangeDivisionInfo.getRangeDivisionInfo(getTimeRange());
            chart.setRangeInfo(rangeInfo);  //do we need this.  It seems like a hack.
            List<Interval> intervals = rangeInfo.getIntervals();

            //clear old data, and reset ranges and series
            resetChart(Lists.transform(intervals, rangeInfo::formatForTick));

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
                Map<EventType, Long> eventCounts = filteredEvents.getEventCounts(interval);

                //for each type add data to graph
                for (final EventType eventType : eventCounts.keySet()) {
                    if (isCancelled()) {
                        return null;
                    }

                    final Long count = eventCounts.get(eventType);
                    if (count > 0) {
                        final String intervalCategory = rangeInfo.formatForTick(interval);
                        final double adjustedCount = activeScale.apply(count);

                        final XYChart.Data<String, Number> dataItem =
                                new XYChart.Data<>(intervalCategory, adjustedCount,
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
        protected void setDateAxisValues(List<String> categories) {
            dateAxis.getCategories().setAll(categories);
        }
    }
}
