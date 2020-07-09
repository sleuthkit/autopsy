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

import java.util.logging.Level;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class ZoomToEvents extends Action {

    private static final Logger logger = Logger.getLogger(ZoomToEvents.class.getName());
    private static final Image MAGNIFIER_OUT = new Image("/org/sleuthkit/autopsy/timeline/images/magnifier-zoom-out-red.png", 16, 16, true, true); //NOI18N NON-NLS

    @NbBundle.Messages({"ZoomToEvents.action.text=Zoom to events",
        "ZoomToEvents.longText=Zoom out to show the nearest events.",
        "ZoomToEvents.disabledProperty.errorMessage=Error getting spanning interval."})
    public ZoomToEvents(final TimeLineController controller) {
        super(Bundle.ZoomToEvents_action_text());
        setLongText(Bundle.ZoomToEvents_longText());
        setGraphic(new ImageView(MAGNIFIER_OUT));
        setEventHandler(actionEvent -> {
            try {
                controller.zoomOutToActivity();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error invoking ZoomToEvents action", ex);
                new Alert(Alert.AlertType.ERROR, "Error zomming").showAndWait();
            }
        });

        //disable action when the current time range already encompases the entire case.
        disabledProperty().bind(new BooleanBinding() {
            private final EventsModel eventsModel = controller.getEventsModel();

            {
                bind(eventsModel.modelParamsProperty());
            }

            @Override
            protected boolean computeValue() {
                try {
                    //TODO: do a db query to see if using this action will actually result in viewable events
                    return eventsModel.getTimeRange().contains(eventsModel.getSpanningInterval());
                } catch (TskCoreException ex) {
                    new Alert(Alert.AlertType.ERROR, Bundle.ZoomToEvents_disabledProperty_errorMessage()).showAndWait();
                    logger.log(Level.SEVERE, "Error getting spanning interval.", ex);
                    return true;
                }
            }
        });
    }
}
