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
import org.sleuthkit.autopsy.timeline.ui.AbstractTimeLineView;

/**
 * An AbstractTimeLineView that uses a TableView to represent the events.
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
        setSettingsNodes(new ListViewPane.ListViewSettingsPane().getChildrenUnmodifiable());

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

    private static class ListViewSettingsPane extends Parent {
    }

    private class ListUpdateTask extends ViewRefreshTask<Interval> {

        ListUpdateTask() {
            super("List update task", true);
        }

        @Override
        protected Boolean call() throws Exception {
            super.call();
            if (isCancelled()) {
                return null;
            }

            FilteredEventsModel eventsModel = getEventsModel();

            //grab the currently selected event
            Long selectedEventID = listTimeline.getSelectedEventID();

            //clear the chart and set the time range.
            resetView(eventsModel.getTimeRange());

            updateMessage("Querying DB for events");
            //get the IDs of th events to be displayed
            List<Long> eventIDs = eventsModel.getEventIDs();
            updateMessage("Updating UI");
            Platform.runLater(() -> {
                //put the event IDs into the table.
                listTimeline.setEventIDs(eventIDs);
                //restore the selected event
                listTimeline.selectEventID(selectedEventID);
            });

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
