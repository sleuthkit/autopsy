/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.guiutils;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

/**
 * Utilities for dealing with JavaFX gui components.
 *
 */
public class JavaFXUtils {

    private static final Logger logger = Logger.getLogger(JavaFXUtils.class.getName());

    /**
     * Image to use as title bar icon in dialogs
     */
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

    private JavaFXUtils() {
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

    @NbBundle.Messages(value = {"# {0} - Tool name",
        "JavaFXUtils.showTooManyFiles.contentText="
        + "There are too many files in the DB to ensure reasonable performance."
        + "  {0} will be disabled. ",
        "JavaFXUtils.showTooManyFiles.headerText="})
    public static void showTooManyFiles(String toolName) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION, Bundle.JavaFXUtils_showTooManyFiles_contentText(toolName), ButtonType.OK);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(toolName);
        setDialogIcons(dialog);
        dialog.setHeaderText(Bundle.JavaFXUtils_showTooManyFiles_headerText());
        dialog.showAndWait();
    }

}
