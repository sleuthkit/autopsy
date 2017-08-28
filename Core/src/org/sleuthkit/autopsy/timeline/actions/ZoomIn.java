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

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 *
 */
public class ZoomIn extends Action {

    private static final Image MAGNIFIER_IN = new Image("/org/sleuthkit/autopsy/timeline/images/magnifier-zoom-in-green.png"); //NOI18N NON-NLS

    @NbBundle.Messages({"ZoomIn.longText=Zoom in to view about half as much time.",
        "ZoomIn.action.text=Zoom in"})
    public ZoomIn(TimeLineController controller) {
        super(Bundle.ZoomIn_action_text());
        setLongText(Bundle.ZoomIn_longText());
        setGraphic(new ImageView(MAGNIFIER_IN));
        setEventHandler(actionEvent -> {
            controller.pushZoomInTime();
        });
    }
}
