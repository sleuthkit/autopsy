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
import java.util.HashSet;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.concurrent.Task;
import javafx.scene.Node;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import org.sleuthkit.autopsy.timeline.datamodel.CombinedEvent;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.ui.AbstractTimeLineView;

/**
 * An AbstractTimeLineView that uses a TableView to display events.
 */
public class ListViewPane extends AbstractTimeLineView {

    private final ListTimeline listTimeline;

    /**
     * Constructor
     *
     * @param controller
     */
    public ListViewPane(TimeLineController controller) {
        super(controller);
        listTimeline = new ListTimeline(controller);

        //initialize chart;
        setCenter(listTimeline);

        //keep controller's list of selected event IDs in sync with this list's
        listTimeline.getSelectedEventIDs().addListener((Observable selectedIDs) -> {
            controller.selectEventIDs(listTimeline.getSelectedEventIDs());
        });
    }

    @Override
    protected Task<Boolean> getNewUpdateTask() {
        return new ListUpdateTask();
    }

    @Override
    protected void clearData() {
        listTimeline.clear();
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
        return ImmutableList.copyOf(listTimeline.getNavControls());
    }

    @Override
    protected boolean hasCustomTimeNavigationControls() {
        return true;
    }

    private class ListUpdateTask extends ViewRefreshTask<Interval> {

        @NbBundle.Messages({
            "ListViewPane.loggedTask.queryDb=Retreiving event data",
            "ListViewPane.loggedTask.name=Updating Details View",
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

            FilteredEventsModel eventsModel = getEventsModel();

            //grab the currently selected event
            HashSet<CombinedEvent> selectedEvents = new HashSet<>(listTimeline.getSelectedEvents());

            //clear the chart and set the time range.
            resetView(eventsModel.getTimeRange());

            //get the combined events to be displayed
            updateMessage(Bundle.ListViewPane_loggedTask_queryDb());
            List<CombinedEvent> combinedEvents = eventsModel.getCombinedEvents();

            updateMessage(Bundle.ListViewPane_loggedTask_updateUI());
            Platform.runLater(() -> {
                //put the combined events into the table.
                listTimeline.setCombinedEvents(combinedEvents);
                //restore the selected event
                listTimeline.selectEvents(selectedEvents);
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
