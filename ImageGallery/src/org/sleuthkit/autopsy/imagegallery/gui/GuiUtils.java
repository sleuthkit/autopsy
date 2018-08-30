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
package org.sleuthkit.autopsy.imagegallery.gui;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.controlsfx.control.action.Action;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

/**
 * Static utility methods for working with GUI components
 */
public final class GuiUtils {

    private final static Logger logger = Logger.getLogger(GuiUtils.class.getName());

    /** Image to use as title bar icon in dialogs */
    private static final Image AUTOPSY_ICON;

    static {
        Image tempImg = null;
        try {
            tempImg = new Image(new URL("nbresloc:/org/netbeans/core/startup/frame.gif").openStream()); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to load branded icon for progress dialog.", ex); //NON-NLS
        }
        AUTOPSY_ICON = tempImg;
    }

    private GuiUtils() {
    }

    /**
     * Create a MenuItem that performs the given action and also set the Action
     * as the action for the given Button. Usefull to have a SplitMenuButton
     * remember the last chosen menu item as its action.
     *
     * @param button
     * @param action
     *
     * @return
     */
    public static MenuItem createAutoAssigningMenuItem(ButtonBase button, Action action) {

        MenuItem menuItem = new MenuItem(action.getText(), action.getGraphic());
        menuItem.setOnAction(actionEvent -> {
            action.handle(actionEvent);
            button.setText(action.getText());
            button.setGraphic(action.getGraphic());
            button.setOnAction(action);
        });
        return menuItem;
    }

    /**
     * Set the title bar icon for the given Dialog to be the Autopsy logo icon.
     *
     * @param dialog The dialog to set the title bar icon for.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public static void setDialogIcons(Dialog<?> dialog) {
        ((Stage) dialog.getDialogPane().getScene().getWindow()).getIcons().setAll(AUTOPSY_ICON);
    }
}
