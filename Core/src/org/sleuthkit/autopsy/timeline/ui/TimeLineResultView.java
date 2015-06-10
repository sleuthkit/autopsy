/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui;

import java.util.HashSet;
import java.util.Set;
import javafx.beans.Observable;
import javax.swing.SwingUtilities;
import org.joda.time.format.DateTimeFormatter;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.events.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.explorernodes.EventRootNode;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;

/**
 * Since it was too hard to derive from {@link DataResultPanel}, this class
 * implements {@link TimeLineView}, listens to the events/state of a the
 * assigned {@link FilteredEventsModel} and acts appropriately on its
 * {@link DataResultPanel}. That is, this class acts as a sort of
 * bridge/adapter between a FilteredEventsModel instance and a
 * DataResultPanel instance.
 */
public class TimeLineResultView implements TimeLineView {

    /** the {@link DataResultPanel} that is the real view proxied by this
     * class */
    private final DataResultPanel dataResultPanel;

    private TimeLineController controller;

    private FilteredEventsModel filteredEvents;

    private Set<Long> selectedEventIDs = new HashSet<>();

    public DataResultPanel getDataResultPanel() {
        return dataResultPanel;
    }

    public TimeLineResultView(DataContent dataContent) {
        dataResultPanel = DataResultPanel.createInstanceUninitialized("", "", Node.EMPTY, 0, dataContent);
    }

    /** Set the Controller for this class. Also sets the model provided by the
     * controller as the model for this view.
     *
     * @param controller
     */
    @Override
    public void setController(TimeLineController controller) {
        this.controller = controller;

        //set up listeners on relevant properties
        TimeLineController.getTimeZone().addListener((Observable observable) -> {
            dataResultPanel.setPath(getSummaryString());
        });

        controller.getSelectedEventIDs().addListener((Observable o) -> {
            refresh();
        });

        setModel(controller.getEventsModel());
    }

    /** Set the Model for this View
     *
     * @param filteredEvents */
    @Override
    synchronized public void setModel(final FilteredEventsModel filteredEvents) {
        this.filteredEvents = filteredEvents;
    }

    /** @return a String representation of all the Events displayed */
    private String getSummaryString() {
        if (controller.getSelectedTimeRange().get() != null) {
            final DateTimeFormatter zonedFormatter = TimeLineController.getZonedFormatter();
            return NbBundle.getMessage(this.getClass(), "TimeLineResultView.startDateToEndDate.text",
                                       controller.getSelectedTimeRange().get().getStart()
                                                 .withZone(TimeLineController.getJodaTimeZone())
                                                 .toString(zonedFormatter),
                                       controller.getSelectedTimeRange().get().getEnd()
                                                 .withZone(TimeLineController.getJodaTimeZone())
                                                 .toString(zonedFormatter));
        }
        return "";
    }

    /** refresh this view with the events selected in the controller */
    public final void refresh() {

        Set<Long> newSelectedEventIDs = new HashSet<>(controller.getSelectedEventIDs());
        if (selectedEventIDs.equals(newSelectedEventIDs) == false) {
            selectedEventIDs = newSelectedEventIDs;
            final EventRootNode root = new EventRootNode(
                    NbBundle.getMessage(this.getClass(), "Timeline.node.root"), selectedEventIDs,
                    filteredEvents);

            //this must be in edt or exception is thrown
            SwingUtilities.invokeLater(() -> {
                dataResultPanel.setPath(getSummaryString());
                dataResultPanel.setNode(root);
            });
        }
    }
}
