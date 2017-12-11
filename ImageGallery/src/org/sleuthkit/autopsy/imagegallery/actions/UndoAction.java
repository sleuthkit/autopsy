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
package org.sleuthkit.autopsy.imagegallery.actions;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;

/**
 * Action that undoes the last undoable command
 */
@NbBundle.Messages({"UndoAction.displayName=Undo"})
public class UndoAction extends Action {

    private static final Image UNDO_IMAGE = new Image("/org/sleuthkit/autopsy/imagegallery/images/undo.png", 16, 16, true, true, true); //NON-NLS

    public UndoAction(ImageGalleryController controller) {
        super(Bundle.UndoAction_displayName());
        setGraphic(new ImageView(UNDO_IMAGE));
        setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCodeCombination.CONTROL_DOWN));
        setEventHandler(actionEvent -> controller.getUndoManager().undo());
        disabledProperty().bind(controller.getUndoManager().undosAvailableProperty().lessThanOrEqualTo(0));
    }
}
