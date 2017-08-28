/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;

/**
 *
 */
public class ZoomOut extends Action {

    private static final Image MAGNIFIER_OUT = new Image("/org/sleuthkit/autopsy/timeline/images/magnifier-zoom-out-red.png"); //NOI18N NON-NLS

    @NbBundle.Messages({"ZoomOut.longText=Zoom out to view about 50% more time.",
        "ZoomOut.action.text=Zoom out"})
    public ZoomOut(TimeLineController controller) {
        super(Bundle.ZoomOut_action_text());
        setLongText(Bundle.ZoomOut_longText());
        setGraphic(new ImageView(MAGNIFIER_OUT));
        setEventHandler(actionEvent -> controller.pushZoomOutTime());

        //disable action when the current time range already encompases the entire case.
        disabledProperty().bind(new BooleanBinding() {
            private final FilteredEventsModel eventsModel = controller.getEventsModel();

            {
                bind(eventsModel.zoomParametersProperty(), eventsModel.timeRangeProperty());
            }

            @Override
            protected boolean computeValue() {
                return eventsModel.timeRangeProperty().get().contains(eventsModel.getSpanningInterval());
            }
        });
    }
}
