/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.listvew;

import java.util.List;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.Axis;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;

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
public class ListViewPane extends AbstractVisualizationPane<Long, SingleEvent, Node, ListChart> {

    /**
     * Constructor
     *
     * @param controller
     */
    public ListViewPane(TimeLineController controller) {
        super(controller);

        //initialize chart;
        setChart(new ListChart(controller));
        setSettingsNodes(new ListViewPane.ListViewSettingsPane().getChildrenUnmodifiable());
    }

    @Override
    protected Boolean isTickBold(Long value) {
        return true;
    }

    @Override
    protected void applySelectionEffect(Node node, Boolean applied) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Task<Boolean> getNewUpdateTask() {
        return new ListUpdateTask();
    }

    @Override
    protected String getTickMarkLabel(Long tickValue) {
        return "";
    }

    @Override
    protected double getTickSpacing() {
        return 0;
    }

    @Override
    protected Axis<Long> getXAxis() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Axis<SingleEvent> getYAxis() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected double getAxisMargin() {
        return 0;
    }

    @Override
    protected void clearChartData() {
        getChart().getItems().clear();
    }

    private static class ListViewSettingsPane extends Parent {

        ListViewSettingsPane() {
        }
    }

    private class ListUpdateTask extends VisualizationRefreshTask<Interval> {

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
            resetChart(eventsModel.getTimeRange());

//            updateMessage(Bundle.DetailViewPane_loggedTask_queryDb());
            //get the event stripes to be displayed
            List<Long> eventIDs = eventsModel.getEventIDs();
            getChart().getItems().setAll(eventIDs);

//            updateMessage(Bundle.DetailViewPane_loggedTask_updateUI());
            return eventIDs.isEmpty() == false;
        }

        @Override
        protected void cancelled() {
            super.cancelled();
            getController().retreat();
        }

        @Override
        protected void setDateAxisValues(Interval timeRange) {
//            detailsChartDateAxis.setRange(timeRange, true);
//            pinnedDateAxis.setRange(timeRange, true);
        }
    }
}
