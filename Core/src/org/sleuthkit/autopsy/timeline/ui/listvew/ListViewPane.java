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
package org.sleuthkit.autopsy.timeline.ui.listvew;

import java.util.List;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.CombinedEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractTimeLineView;

/**
 * @param <X>         The type of data plotted along the x axis
 * @param <Y>         The type of data plotted along the y axis
 * @param <NodeType>  The type of nodes used to represent data items
 * @param <ChartType> The type of the TimeLineChart<X> this class uses to plot
 *                    the data. Must extend Region.
 *
 * TODO: this is becoming (too?) closely tied to the notion that there is a
 * XYChart doing the rendering. Is this a good idea? -jm
 *
 * TODO: pull up common history context menu items out of derived classes? -jm
 *
 * public abstract class AbstractVisualizationPane<X, Y, NodeType extends Node,
 * ChartType extends Region & TimeLineChart<X>> extends BorderPane {
 */
public class ListViewPane extends AbstractTimeLineView {

    private final ListTimeline listChart;

    /**
     * Constructor
     *
     * @param controller
     */
    public ListViewPane(TimeLineController controller) {
        super(controller);
        listChart = new ListTimeline(controller);

        //initialize chart;
        setCenter(listChart);
        setSettingsNodes(new ListViewPane.ListViewSettingsPane().getChildrenUnmodifiable());

        //keep controller's list of selected event IDs in sync with this list's
        listChart.getSelectedEventIDs().addListener((Observable selectedIDs) -> {
            controller.selectEventIDs(listChart.getSelectedEventIDs());
        });
    }

    @Override
    protected Task<Boolean> getNewUpdateTask() {
        return new ListUpdateTask();
    }

    @Override
    protected void clearData() {
        listChart.clear();
    }

    private static class ListViewSettingsPane extends Parent {

        ListViewSettingsPane() {
        }
    }

    private class ListUpdateTask extends ViewRefreshTask<Interval> {

        ListUpdateTask() {
            super("List update task", true);
        }

        @Override
        protected Boolean call() throws Exception {
            super.call(); //To change body of generated methods, choose Tools | Templates.
            if (isCancelled()) {
                return null;
            }
            FilteredEventsModel eventsModel = getEventsModel();

            //clear the chart and set the horixontal axis
            resetView(eventsModel.getTimeRange());

            updateMessage("Querying db for events");
            //get the event stripes to be displayed
            List<CombinedEvent> mergedEvents = eventsModel.getCombinedEvents();
            Platform.runLater(() -> listChart.setMergedEvents(mergedEvents));

            updateMessage("updating ui");
            return mergedEvents.isEmpty() == false;
        }

        @Override
        protected void cancelled() {
            super.cancelled();
            getController().retreat();
        }

        @Override
        protected void setDateValues(Interval timeRange) {
        }

    }
}
