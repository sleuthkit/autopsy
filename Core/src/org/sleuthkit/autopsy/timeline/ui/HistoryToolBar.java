/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.Forward;

/**
 * Toolbar with buttons to go back and forward in the history.
 */
public class HistoryToolBar extends ToolBar {

    @FXML
    private Label historyLabel;
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;

    private final TimeLineController controller;

    /**
     * Constructor
     *
     * @param controller the TimeLineController this ToolBar interacts with.
     */
    public HistoryToolBar(TimeLineController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "HistoryToolBar.fxml");
    }

    @FXML
    @NbBundle.Messages({"HistoryToolBar.historyLabel.text=History"})
    void initialize() {
        assert historyLabel != null : "fx:id=\"historyLabel\" was not injected: check your FXML file 'HistoryToolBar.fxml'.";
        assert backButton != null : "fx:id=\"backButton\" was not injected: check your FXML file 'HistoryToolBar.fxml'.";
        assert forwardButton != null : "fx:id=\"forwardButton\" was not injected: check your FXML file 'HistoryToolBar.fxml'.";

        historyLabel.setText(Bundle.HistoryToolBar_historyLabel_text());

        ActionUtils.configureButton(new Back(controller), backButton);
        ActionUtils.configureButton(new Forward(controller), forwardButton);
    }
}
