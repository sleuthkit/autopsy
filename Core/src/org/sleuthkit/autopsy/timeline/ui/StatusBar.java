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
package org.sleuthkit.autopsy.timeline.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.events.FilteredEventsModel;

/**
 * simple status bar that only shows one possible message determined by
 * {@link TimeLineController#newEventsFlag}
 */
public class StatusBar extends ToolBar implements TimeLineView {

    private TimeLineController controller;

    @FXML
    private Label refreshLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Region spacer;

    @FXML
    private Label taskLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private HBox refreshBox;
    @FXML
    private Button refreshButton;

    public StatusBar() {
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
        taskLabel.setVisible(false);
        HBox.setHgrow(spacer, Priority.ALWAYS);
    }

    @Override
    public void setController(TimeLineController controller) {
        this.controller = controller;

        refreshBox.visibleProperty().bind(this.controller.getNewEventsFlag());
        taskLabel.textProperty().bind(this.controller.getTaskTitle());
        messageLabel.textProperty().bind(this.controller.getMessage());
        progressBar.progressProperty().bind(this.controller.getProgress());
        taskLabel.visibleProperty().bind(this.controller.getTasks().emptyProperty().not());
        setModel(controller.getEventsModel());
    }

    @Override
    public void setModel(FilteredEventsModel filteredEvents) {
        refreshButton.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent event) {
                filteredEvents.refresh()   }
        });
    }
}
