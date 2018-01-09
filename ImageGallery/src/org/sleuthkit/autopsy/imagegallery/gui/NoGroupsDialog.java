
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

import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

/**
 *
 */
public class NoGroupsDialog extends GridPane {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private BorderPane graphicBorder;

    @FXML
    private Label messageLabel;

    @FXML
    void initialize() {
        assert graphicBorder != null : "fx:id=\"graphicBorder\" was not injected: check your FXML file 'NoGroupsDialog.fxml'.";
        assert messageLabel != null : "fx:id=\"messageLabel\" was not injected: check your FXML file 'NoGroupsDialog.fxml'.";

    }

    private NoGroupsDialog() {
        FXMLConstructor.construct(this, "NoGroupsDialog.fxml"); //NON-NLS
    }

    public NoGroupsDialog(String message) {
        this();
        messageLabel.setText(message);
    }

    public NoGroupsDialog(String message, Node graphic) {
        this();
        messageLabel.setText(message);
        graphicBorder.setCenter(graphic);
    }
}
