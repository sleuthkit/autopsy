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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import org.sleuthkit.autopsy.timeline.events.ViewInTimelineRequestedEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractTimeLineView;
import org.sleuthkit.autopsy.timeline.ui.listvew.datamodel.CombinedEvent;
import org.sleuthkit.autopsy.timeline.ui.listvew.datamodel.ListViewModel;

/**
 * An AbstractTimeLineView that uses a TableView to display events.
 */
public class ListViewPane extends AbstractTimeLineView {

    private final ListTimeline listTimeline;
    private final ListViewModel listViewModel;

    /**
     * Constructor
     *
     * @param controller
     */
    public ListViewPane(TimeLineController controller) {
        super(controller);
        listViewModel = new ListViewModel(getEventsModel());
        listTimeline = new ListTimeline(controller);

        //initialize chart;
        setCenter(listTimeline);

    }

    @Override
    protected Task<Boolean> getNewUpdateTask() {
        return new ListUpdateTask();
    }

    /**
     * This method is supposed to clear all the data from this View, but it
     * might have been interfering with the "View in Timeline" action and was
     * not strictly necessary so this implementation is a no-op.
     */
    @Override
    protected void clearData() {

    }

    @Override
    final protected ViewMode getViewMode() {
        return ViewMode.LIST;
    }

    @Override
    protected ImmutableList<Node> getSettingsControls() {
        return ImmutableList.of();
    }

    @Override
    protected ImmutableList<Node> getTimeNavigationControls() {
        return ImmutableList.copyOf(listTimeline.getTimeNavigationControls());
    }

    @Override
    protected boolean hasCustomTimeNavigationControls() {
        return true;
    }

    @Subscribe
    public void handleViewInTimelineRequested(ViewInTimelineRequestedEvent event) {
        listTimeline.selectEvents(event.getEventIDs());
    }

    private class ListUpdateTask extends ViewRefreshTask<Interval> {

        @NbBundle.Messages({
            "ListViewPane.loggedTask.queryDb=Retrieving event data",
            "ListViewPane.loggedTask.name=Updating List View",
            "ListViewPane.loggedTask.updateUI=Populating view"})
        ListUpdateTask() {
            super(Bundle.ListViewPane_loggedTask_name(), true);
        }

        @Override
        protected Boolean call() throws Exception {
            super.call();
            if (isCancelled()) {
                return null;
            }

            EventsModel eventsModel = getEventsModel();

            Set<Long> selectedEventIDs;
            TimeLineController controller = getController();
            //grab the currently selected event
            synchronized (controller) {
                selectedEventIDs = ImmutableSet.copyOf(controller.getSelectedEventIDs());
            }

            //clear the chart and set the time range.
            resetView(eventsModel.getTimeRange());

            //get the combined events to be displayed
            updateMessage(Bundle.ListViewPane_loggedTask_queryDb());
            List<CombinedEvent> combinedEvents = listViewModel.getCombinedEvents();

            updateMessage(Bundle.ListViewPane_loggedTask_updateUI());
            Platform.runLater(() -> {
                //put the combined events into the table.
                listTimeline.setCombinedEvents(combinedEvents);
                //restore the selected events
                listTimeline.selectEvents(selectedEventIDs);
            });

            return combinedEvents.isEmpty() == false;
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
