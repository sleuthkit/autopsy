/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;

/**
 *
 */
public final class PinnedEventsChart extends DetailsChartLane<TimeLineEvent> {

    /**
     *
     * @param controller     the value of controller
     * @param dateAxis       the value of dateAxis
     * @param verticalAxis   the value of verticalAxis
     * @param selectedNodes1 the value of selectedNodes1
     */
    PinnedEventsChart(DetailViewPane parentPane, DateAxis dateAxis, final Axis<TimeLineEvent> verticalAxis) {
        super(parentPane, dateAxis, verticalAxis, false);

        final Series<DateTime, TimeLineEvent> series = new Series<>();
        setData(FXCollections.observableArrayList());
        getData().add(series);

//        //this is needed to allow non circular binding of the guideline and timerangeRect heights to the height of the chart
//        //TODO: seems like a hack, can we remove? -jm
//        boundsInLocalProperty().addListener((Observable observable) -> {
//            setPrefHeight(boundsInLocalProperty().get().getHeight());
//        });
        getController().getPinnedEvents().addListener((SetChangeListener.Change<? extends TimeLineEvent> change) -> {
            if (change.wasAdded()) {
                TimeLineEvent elementAdded = change.getElementAdded();
                Data<DateTime, TimeLineEvent> data1 = new Data<>(new DateTime(elementAdded.getStartMillis()), elementAdded);
                series.getData().add(data1);
                addDataItem(data1);
            }
            if (change.wasRemoved()) {
                TimeLineEvent elementRemoved = change.getElementRemoved();
                Data<DateTime, TimeLineEvent> data1 = new Data<>(new DateTime(elementRemoved.getStartMillis()), elementRemoved);
                series.getData().removeIf(t -> elementRemoved.equals(t.getYValue()));
                removeDataItem(data1);
            }

            requestChartLayout();
        });

        for (TimeLineEvent event : getController().getPinnedEvents()) {
            addDataItem(new XYChart.Data<>(new DateTime(event.getStartMillis()), event));
        }

    }

    @Override
    public ObservableList<EventStripe> getEventStripes() {
        return FXCollections.emptyObservableList();
    }

    /**
     * add a dataitem to this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param data
     */
    void addDataItem(Data<DateTime, TimeLineEvent> data) {
        final TimeLineEvent event = data.getYValue();

        EventNodeBase<?> eventNode = createNode(PinnedEventsChart.this, event);
        eventMap.put(event, eventNode);
        Platform.runLater(() -> {
            events.add(event);
            nodes.add(eventNode);
            nodeGroup.getChildren().add(eventNode);
            data.setNode(eventNode);

        });
    }

    /**
     * remove a data item from this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param data
     */
    void removeDataItem(Data<DateTime, TimeLineEvent> data) {
        EventNodeBase<?> removedNode = eventMap.remove(data.getYValue());
        Platform.runLater(() -> {
            events.removeAll(data.getYValue());
            nodes.removeAll(removedNode);
            nodeGroup.getChildren().removeAll(removedNode);
            data.setNode(null);
        });
    }

    @Override
    void doAdditionalLayout() {
    }

}
