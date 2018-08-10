/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016-18 Basis Technology Corp.
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

import java.util.logging.Level;
import javafx.collections.SetChangeListener;
import javafx.scene.chart.Axis;
import org.controlsfx.control.Notifications;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public final class PinnedEventsChartLane extends DetailsChartLane<DetailViewEvent> {

    private static final Logger logger = Logger.getLogger(PinnedEventsChartLane.class.getName());

    /**
     *
     * @param controller     the value of controller
     * @param dateAxis       the value of dateAxis
     * @param verticalAxis   the value of verticalAxis
     * @param selectedNodes1 the value of selectedNodes1
     */
    @NbBundle.Messages({"PinnedChartLane.pinnedEventsListener.errorMessage=Error adding pinned event to lane."})
    PinnedEventsChartLane(DetailsChart parentChart, DateAxis dateAxis, final Axis<DetailViewEvent> verticalAxis) {
        super(parentChart, dateAxis, verticalAxis, false);

        getController().getPinnedEvents().addListener((SetChangeListener.Change<? extends DetailViewEvent> change) -> {
            if (change.wasAdded()) {
                try {
                    addEvent(change.getElementAdded());
                } catch (TskCoreException ex) {
                    Notifications.create().owner(getScene().getWindow())
                            .text(Bundle.PinnedChartLane_pinnedEventsListener_errorMessage()).showError();
                    logger.log(Level.SEVERE, "Error adding pinned event to lane.", ex);
                }
            }
            if (change.wasRemoved()) {
                removeEvent(change.getElementRemoved());
            }
            requestChartLayout();
        });

        for (DetailViewEvent event : getController().getPinnedEvents()) {
            try {
                addEvent(event);
            } catch (TskCoreException ex) {
                Notifications.create().owner(getScene().getWindow())
                        .text(Bundle.PinnedChartLane_pinnedEventsListener_errorMessage())
                        .showError();
                logger.log(Level.SEVERE, "Error adding pinned event to lane.", ex);
            }
        }
        requestChartLayout();
    }

    @Override
    void doAdditionalLayout() {
    }

}
