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

import javafx.collections.SetChangeListener;
import javafx.scene.chart.Axis;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;

/**
 *
 */
public final class PinnedEventsChartLane extends DetailsChartLane<DetailViewEvent> {

    /**
     *
     * @param controller     the value of controller
     * @param dateAxis       the value of dateAxis
     * @param verticalAxis   the value of verticalAxis
     * @param selectedNodes1 the value of selectedNodes1
     */
    PinnedEventsChartLane(DetailsChart parentChart, DateAxis dateAxis, final Axis<DetailViewEvent> verticalAxis) {
        super(parentChart, dateAxis, verticalAxis, false);

//        final Series<DateTime, DetailViewEvent> series = new Series<>();
//        setData(FXCollections.observableArrayList());
//        getData().add(series);
        getController().getPinnedEvents().addListener((SetChangeListener.Change<? extends DetailViewEvent> change) -> {
            if (change.wasAdded()) {
                addEvent(change.getElementAdded());
            }
            if (change.wasRemoved()) {
                removeEvent(change.getElementRemoved());
            }
            requestChartLayout();
        });

        getController().getPinnedEvents().stream().forEach(this::addEvent);
        requestChartLayout();
    }

    @Override
    void doAdditionalLayout() {
    }

}
