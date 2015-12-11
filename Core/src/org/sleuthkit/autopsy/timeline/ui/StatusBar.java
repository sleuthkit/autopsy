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
package org.sleuthkit.autopsy.timeline.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 * simple status bar that only shows one possible message determined by
 * {@link TimeLineController#newEventsFlag}
 */
public class StatusBar extends ToolBar {

    private TimeLineController controller;

    @FXML
    private Label refreshLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Region spacer;

    @FXML
    private Label taskLabel;

    @FXML
    private Label messageLabel;

    public StatusBar(TimeLineController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "StatusBar.fxml"); // NON-NLS
    }

    @FXML
    void initialize() {
        assert refreshLabel != null : "fx:id=\"refreshLabel\" was not injected: check your FXML file 'StatusBar.fxml'."; // NON-NLS
        assert progressBar != null : "fx:id=\"progressBar\" was not injected: check your FXML file 'StatusBar.fxml'."; // NON-NLS
        assert spacer != null : "fx:id=\"spacer\" was not injected: check your FXML file 'StatusBar.fxml'."; // NON-NLS
        assert taskLabel != null : "fx:id=\"taskLabel\" was not injected: check your FXML file 'StatusBar.fxml'."; // NON-NLS
        assert messageLabel != null : "fx:id=\"messageLabel\" was not injected: check your FXML file 'StatusBar.fxml'."; // NON-NLS
        refreshLabel.setVisible(false);
        refreshLabel.setText(NbBundle.getMessage(this.getClass(), "StatusBar.refreshLabel.text"));
        messageLabel.setText(NbBundle.getMessage(this.getClass(), "StatusBar.messageLabel.text"));
        taskLabel.setText(NbBundle.getMessage(this.getClass(), "StatusBar.taskLabel.text"));
        taskLabel.setVisible(false);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        refreshLabel.visibleProperty().bind(this.controller.getNewEventsFlag());
        refreshLabel.managedProperty().bind(this.controller.getNewEventsFlag());
        taskLabel.textProperty().bind(this.controller.taskTitleProperty());
        messageLabel.textProperty().bind(this.controller.taskMessageProperty());
        progressBar.progressProperty().bind(this.controller.taskProgressProperty());
        taskLabel.visibleProperty().bind(this.controller.getTasks().emptyProperty().not());

        statusLabel.textProperty().bind(this.controller.getStatusProperty());
        statusLabel.visibleProperty().bind(statusLabel.textProperty().isNotEmpty());

    }
}
