/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;

/**
 *
 */
public class StatusBar extends AnchorPane {

    private final ImageGalleryController controller;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private ProgressBar fileTaskProgresBar;

    @FXML
    private Label fileUpdateTaskLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label bgTaskLabel;

    @FXML
    private Label staleLabel;

    @FXML
    private ProgressBar bgTaskProgressBar;

    @FXML
    void initialize() {
        assert fileTaskProgresBar != null : "fx:id=\"fileTaskProgresBar\" was not injected: check your FXML file 'StatusBar.fxml'.";
        assert fileUpdateTaskLabel != null : "fx:id=\"fileUpdateTaskLabel\" was not injected: check your FXML file 'StatusBar.fxml'.";
        assert statusLabel != null : "fx:id=\"statusLabel\" was not injected: check your FXML file 'StatusBar.fxml'.";
        assert bgTaskLabel != null : "fx:id=\"uiTaskLabel\" was not injected: check your FXML file 'StatusBar.fxml'.";
        assert bgTaskProgressBar != null : "fx:id=\"uiTaskProgressBar\" was not injected: check your FXML file 'StatusBar.fxml'.";

        fileUpdateTaskLabel.textProperty().bind(controller.getFileUpdateQueueSizeProperty().asString().concat(" File Update Tasks"));//;setText(newSize.toString() + " File Update Tasks");
        fileTaskProgresBar.progressProperty().bind(controller.getFileUpdateQueueSizeProperty().negate());
//        controller.getFileUpdateQueueSizeProperty().addListener((ov, oldSize, newSize) -> {
//            Platform.runLater(() -> {
//
//
//            });
//        });

        bgTaskProgressBar.progressProperty().bind(controller.regroupProgress());

        Platform.runLater(() -> {
            staleLabel.setTooltip(new Tooltip("Some data may be out of date.  Enable listening to ingest in Tools | Options | Image /Video Gallery , after ingest is complete to update."));
        });
        staleLabel.visibleProperty().bind(controller.stale());
    }

    public StatusBar(ImageGalleryController controller) {
        this.controller = controller;
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("StatusBar.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

    }

    public void setLabelText(final String newText) {
        Platform.runLater(() -> {
            statusLabel.setText(newText);
        });
    }

    public String getLabeltext() {
        return statusLabel.getText();
    }
}
