/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.events.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.filters.Filter;

/**
 *
 */
public class DefaultFilters extends Action {

    private final TimeLineController controller;

    private FilteredEventsModel eventsModel;

    public DefaultFilters(final TimeLineController controller) {
        super(NbBundle.getMessage(DefaultFilters.class, "DefaultFilters.action.name.text"));
        this.controller = controller;
        eventsModel = controller.getEventsModel();
        disabledProperty().bind(new BooleanBinding() {
            {
                bind(eventsModel.getRequestedZoomParamters());
            }

            @Override
            protected boolean computeValue() {
                return eventsModel.getRequestedZoomParamters().getValue().getFilter().equals(Filter.getDefaultFilter());
            }
        });
        setEventHandler((ActionEvent t) -> {
            controller.applyDefaultFilters();
        });
    }
}
