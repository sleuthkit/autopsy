/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
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
     * as the action for the given Button. Useful to have a SplitMenuButton
     * remember the last chosen menu item as its action.
     *
     * @param button
     * @param action
     *
     * @return
     */
    public static MenuItem createAutoAssigningMenuItem(ButtonBase button, Action action) {
        Label mainLabel = new Label(action.getText(), action.getGraphic());
        
        String hkDisplayText = action.getAccelerator() != null ? action.getAccelerator().getDisplayText() : "";
        Label hotKeyLabel = new Label(hkDisplayText);
        
        hotKeyLabel.setMaxWidth(100);

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);

        ColumnConstraints column1 = new ColumnConstraints();
        column1.setHalignment(HPos.LEFT);
        grid.getColumnConstraints().add(column1); 

        ColumnConstraints column2 = new ColumnConstraints();
        column2.setHalignment(HPos.RIGHT);
        column2.setMaxWidth(Double.MAX_VALUE);
        grid.getColumnConstraints().add(column2); 
        
        grid.add(mainLabel, 0, 0);
        grid.add(hotKeyLabel, 1, 0);
        grid.setMaxWidth(Double.MAX_VALUE);

        MenuItem menuItem = new CustomMenuItem(grid);
        ActionUtils.configureMenuItem(action, menuItem);
        menuItem.setOnAction(actionEvent -> {
            action.handle(actionEvent);
            button.setText(action.getText());
            button.setGraphic(menuItem.getGraphic());
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
