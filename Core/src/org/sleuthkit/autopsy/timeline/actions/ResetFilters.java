/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.actions;

import javafx.beans.binding.BooleanBinding;
import javafx.event.ActionEvent;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 * Action that resets the filters to their initial/default state.
 */
@NbBundle.Messages({"ResetFilters.text=Reset all filters",
    "RestFilters.longText=Reset all filters to their default state."})
public class ResetFilters extends Action {

    private EventsModel eventsModel;

    public ResetFilters(final TimeLineController controller) {
        this(Bundle.ResetFilters_text(), controller);
    }

    public ResetFilters(String text, TimeLineController controller) {
        super(text);
        setLongText(Bundle.RestFilters_longText());
        eventsModel = controller.getEventsModel();
        disabledProperty().bind(new BooleanBinding() {
            {
                bind(eventsModel.modelParamsProperty());
            }

            @Override
            protected boolean computeValue() {
                return eventsModel.modelParamsProperty().getValue().getEventFilterState().equals(eventsModel.getDefaultEventFilterState());
            }
        });
        setEventHandler((ActionEvent t) -> {
            controller.applyDefaultFilters();
        });
    }
}
