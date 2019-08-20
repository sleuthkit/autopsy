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

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 * An action that navigates forward through the history.
 */
//TODO: This and the corresponding imageanalyzer action are identical except for the type of the controller...  abstract something! -jm
public class Forward extends Action {

    private static final Image FORWARD_IMAGE = new Image("/org/sleuthkit/autopsy/timeline/images/arrow.png", 16, 16, true, true, true); // NON-NLS

    private final TimeLineController controller;

    @NbBundle.Messages({"Forward.text=Forward",
        "# {0} - action accelerator keys ",
        "Forward.longText=Forward: {0}\nGo forward to the next view settings."})
    public Forward(TimeLineController controller) {
        super(Bundle.Forward_text());
        this.controller = controller;

        setGraphic(new ImageView(FORWARD_IMAGE));
        setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCodeCombination.ALT_DOWN));
        setLongText(Bundle.Forward_longText(getAccelerator().getDisplayText()));
        setEventHandler(actionEvent -> controller.advance());

        disabledProperty().bind(controller.canAdvanceProperty().not());
    }
}
