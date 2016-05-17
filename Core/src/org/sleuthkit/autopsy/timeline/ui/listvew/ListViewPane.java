/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.listvew;

import java.util.List;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
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

    private final ListChart listChart;

    /**
     * Constructor
     *
     * @param controller
     */
    public ListViewPane(TimeLineController controller) {
        super(controller);
        listChart = new ListChart(controller);

        //initialize chart;
        setCenter(listChart);
        setSettingsNodes(new ListViewPane.ListViewSettingsPane().getChildrenUnmodifiable());
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
            List<Long> eventIDs = eventsModel.getEventIDs();
            Platform.runLater(() -> listChart.setEventIDs(eventIDs));

            updateMessage("updating ui");
            return eventIDs.isEmpty() == false;
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
