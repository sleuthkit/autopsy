/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javax.swing.SwingUtilities;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;

/**
 * Wraps {@link ExternalViewerAction} in a ControlsFX {@link Action} with
 * appropriate text and graphic
 */
@NbBundle.Messages({"MediaViewImagePanel.externalViewerButton.text=Open in External Viewer",
    "OpenExternalViewerAction.displayName=External Viewer"})
public class OpenExternalViewerAction extends Action {

    private static final Image EXTERNAL = new Image(OpenExternalViewerAction.class.getResource("/org/sleuthkit/autopsy/imagegallery/images/external.png").toExternalForm()); //NON-NLS
    public static final KeyCombination EXTERNAL_VIEWER_SHORTCUT = new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN);
    private static final ActionEvent ACTION_EVENT = new ActionEvent(OpenExternalViewerAction.class, ActionEvent.ACTION_PERFORMED, ""); //Swing ActionEvent //NOI18N

    public OpenExternalViewerAction(DrawableFile file) {
        super(Bundle.OpenExternalViewerAction_displayName());

        /**
         * TODO: why is the name passed to the action? it means we duplicate
         * this string all over the place -jm
         */
        ExternalViewerAction externalViewerAction = new ExternalViewerAction(Bundle.MediaViewImagePanel_externalViewerButton_text(), new FileNode(file.getAbstractFile()));

        setLongText(Bundle.MediaViewImagePanel_externalViewerButton_text());
        setEventHandler(actionEvent
                -> //fx ActionEvent
                SwingUtilities.invokeLater(() -> externalViewerAction.actionPerformed(ACTION_EVENT))
        );
        setGraphic(new ImageView(EXTERNAL));
        setAccelerator(EXTERNAL_VIEWER_SHORTCUT);
    }

}
