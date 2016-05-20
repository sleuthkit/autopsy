/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 * Simple status bar that shows a possible message determined by
 * TimeLineController.statusMessageProperty() and progress of background tasks.
 */
public class StatusBar extends ToolBar {

    private final TimeLineController controller;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressBar progressBar;
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
        assert progressBar != null : "fx:id=\"progressBar\" was not injected: check your FXML file 'StatusBar.fxml'."; // NON-NLS
        assert taskLabel != null : "fx:id=\"taskLabel\" was not injected: check your FXML file 'StatusBar.fxml'."; // NON-NLS
        assert messageLabel != null : "fx:id=\"messageLabel\" was not injected: check your FXML file 'StatusBar.fxml'."; // NON-NLS

        taskLabel.setVisible(false);
        taskLabel.textProperty().bind(this.controller.taskTitleProperty());
        taskLabel.visibleProperty().bind(this.controller.getTasks().emptyProperty().not());

        messageLabel.textProperty().bind(this.controller.taskMessageProperty());
        progressBar.progressProperty().bind(this.controller.taskProgressProperty());

        statusLabel.textProperty().bind(this.controller.statusMessageProperty());
        statusLabel.visibleProperty().bind(statusLabel.textProperty().isNotEmpty());
    }
}
