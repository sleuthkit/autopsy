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
package org.sleuthkit.autopsy.timeline.ui.countsview;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.Effect;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailViewPane;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;

/**
 * FXML Controller class for a {@link StackedBarChart<String,Number>} based
 * implementation of a {@link TimeLineView}.
 *
 * This class listens to changes in the assigned {@link FilteredEventsModel} and
 * updates the internal {@link StackedBarChart} to reflect the currently
 * requested events.
 *
 * This class captures input from the user in the form of mouse clicks on graph
 * bars, and forwards them to the assigned {@link TimeLineController} *
 *
 * Concurrency Policy: Access to the private members stackedBarChart, countAxis,
 * dateAxis, EventTypeMap, and dataSets affects the stackedBarChart so they all
 * must only be manipulated on the JavaFx thread (through {@link Platform#runLater(java.lang.Runnable)}
 *
 * {@link CountsChartPane#filteredEvents} should encapsulate all need
 * synchronization internally.
 *
 * TODO: refactor common code out of this class and {@link DetailViewPane} into
 * {@link AbstractVisualizationPane}
 */
public class CountsViewPane extends AbstractVisualizationPane<String, Number, Node, EventCountsChart> {

    private static final Logger LOGGER = Logger.getLogger(CountsViewPane.class.getName());

    private final NumberAxis countAxis = new NumberAxis();
    private final CategoryAxis dateAxis = new CategoryAxis(FXCollections.<String>observableArrayList());

    private final SimpleObjectProperty<ScaleType> scale = new SimpleObjectProperty<>(ScaleType.LOGARITHMIC);

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

    public CountsViewPane(TimeLineController controller, Pane partPane, Pane contextPane, Region spacer) {
        super(controller, partPane, contextPane, spacer);
        chart = new EventCountsChart(controller, dateAxis, countAxis, selectedNodes);
//        setChartClickHandler();
        chart.setData(dataSeries);
        setCenter(chart);

        Tooltip.install(chart, getDefaultTooltip());

        settingsNodes = new ArrayList<>(new CountsViewSettingsPane().getChildrenUnmodifiable());

        dateAxis.getTickMarks().addListener((Observable observable) -> {
            layoutDateLabels();
        });
        dateAxis.categorySpacingProperty().addListener((Observable observable) -> {
            layoutDateLabels();
        });
        dateAxis.getCategories().addListener((Observable observable) -> {
            layoutDateLabels();
        });

        spacer.minWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));
        spacer.prefWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));
        spacer.maxWidthProperty().bind(countAxis.widthProperty().add(countAxis.tickLengthProperty()).add(dateAxis.startMarginProperty().multiply(2)));

        scale.addListener(o -> {
            countAxis.tickLabelsVisibleProperty().bind(scale.isEqualTo(ScaleType.LINEAR));
            countAxis.tickMarkVisibleProperty().bind(scale.isEqualTo(ScaleType.LINEAR));
            countAxis.minorTickVisibleProperty().bind(scale.isEqualTo(ScaleType.LINEAR));
            update();
        });

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
    protected Effect getSelectionEffect() {
        return chart.getSelectionEffect();
    }

    @Override
    protected void applySelectionEffect(Node c1, Boolean applied) {
        if (applied) {
            c1.setEffect(getSelectionEffect());
        } else {
            c1.setEffect(null);

        }
    }

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
        void initialize() {
            assert logRadio != null : "fx:id=\"logRadio\" was not injected: check your FXML file 'CountsViewSettingsPane.fxml'."; // NON-NLS
            assert linearRadio != null : "fx:id=\"linearRadio\" was not injected: check your FXML file 'CountsViewSettingsPane.fxml'."; // NON-NLS
            logRadio.setSelected(true);
            scaleGroup.selectedToggleProperty().addListener(observable -> {
                if (scaleGroup.getSelectedToggle() == linearRadio) {
                    scale.set(ScaleType.LINEAR);
                }
                if (scaleGroup.getSelectedToggle() == logRadio) {
                    scale.set(ScaleType.LOGARITHMIC);
                }
            });

            logRadio.setText(NbBundle.getMessage(CountsViewPane.class, "CountsViewPane.logRadio.text"));
            linearRadio.setText(NbBundle.getMessage(CountsViewPane.class, "CountsViewPane.linearRadio.text"));
            scaleLabel.setText(NbBundle.getMessage(CountsViewPane.class, "CountsViewPane.scaleLabel.text"));
        }

        CountsViewSettingsPane() {
            FXMLConstructor.construct(this, "CountsViewSettingsPane.fxml"); // NON-NLS
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @Override
    protected void resetData() {
        for (XYChart.Series<String, Number> s : dataSeries) {
            s.getData().clear();
        }

        dataSeries.clear();
        eventTypeToSeriesMap.clear();
        createSeries();
    }

    private static enum ScaleType implements Function<Long, Double> {

        LINEAR(Long::doubleValue),
        LOGARITHMIC(t -> Math.log10(t) + 1);

        private final Function<Long, Double> func;

        ScaleType(Function<Long, Double> func) {
            this.func = func;
        }

        @Override
        public Double apply(Long t) {
            return func.apply(t);
        }
    }

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
            List<String> categories = Lists.transform(intervals, rangeInfo::formatForTick);

            //clear old data, and reset ranges and series
            resetChart(categories);

            updateMessage(Bundle.CountsViewPane_loggedTask_updatingCounts());
            int chartMax = 0;
            int numIntervals = intervals.size();
            /*
             * for each interval query database for event counts and add to
             * chart.
             *
             * Doing this in chunks might seem inefficient but it lets us reuse
             * more cached results as the user navigates to overlapping viewws
             *
             * //TODO: implement similar chunked caching in DetailsView -jm
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
                        final double adjustedCount = scale.get().apply(count);

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
            double tickUnit = ScaleType.LINEAR.equals(scale.get())
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
