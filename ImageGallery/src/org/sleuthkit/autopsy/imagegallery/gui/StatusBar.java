/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;

/**
 *
 */
public class StatusBar extends AnchorPane {

    @FXML
    private ProgressBar fileTaskProgresBar;
    @FXML
    private Label fileUpdateTaskLabel;
    @FXML
    private Label regroupLabel;
    @FXML
    private Label staleLabel;
    @FXML
    private ProgressBar regroupProgressBar;

    private final ImageGalleryController controller;
    private final GroupManager groupManager;

    @FXML
    @NbBundle.Messages({"StatusBar.fileUpdateTaskLabel.text= File Update Tasks",
        "StatusBar.bgTaskLabel.text=Regrouping",
        "StatuBar.toolTip=Some data may be out of date.  Enable Image Gallery in Tools | Options | Image /Video Gallery , after ingest is complete to update the Image Gallery data."})
    void initialize() {
        assert fileTaskProgresBar != null : "fx:id=\"fileTaskProgresBar\" was not injected: check your FXML file 'StatusBar.fxml'.";
        assert fileUpdateTaskLabel != null : "fx:id=\"fileUpdateTaskLabel\" was not injected: check your FXML file 'StatusBar.fxml'.";
        assert regroupLabel != null : "fx:id=\"regroupLabel\" was not injected: check your FXML file 'StatusBar.fxml'.";
        assert regroupProgressBar != null : "fx:id=\"regroupProgressBar\" was not injected: check your FXML file 'StatusBar.fxml'.";

        fileUpdateTaskLabel.textProperty().bind(controller.getDBTasksQueueSizeProperty().asString().concat(Bundle.StatusBar_fileUpdateTaskLabel_text()));
        fileTaskProgresBar.progressProperty().bind(controller.getDBTasksQueueSizeProperty().negate());

        groupManager.regroupProgress().addListener((ov, oldSize, newSize) -> {
            Platform.runLater(() -> {
                if (groupManager.regroupProgress().lessThan(1.0).get()) {
                    // Regrouping in progress
                    regroupProgressBar.progressProperty().setValue(groupManager.regroupProgress().doubleValue());
                    regroupLabel.setText(groupManager.regroupMessage().get());
                    
                } else {
                    // Clear the progress bar
                    regroupProgressBar.progressProperty().setValue(0.0);
                    regroupLabel.setText("");
                }
                regroupLabel.setTooltip(new Tooltip(regroupLabel.getText()));
            });
        });

        Platform.runLater(() -> staleLabel.setTooltip(new Tooltip(Bundle.StatuBar_toolTip())));
        staleLabel.visibleProperty().bind(controller.modelIsStaleProperty());
    }

    public StatusBar(ImageGalleryController controller) {
        this.controller = controller;
        this.groupManager = controller.getGroupManager();
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("StatusBar.fxml")); //NON-NLS
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
