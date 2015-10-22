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

import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 * FXML Controller class for timezone picker ui
 *
 */
public class TimeZonePanel extends TitledPane {

    @FXML
    private RadioButton localRadio;

    @FXML
    private RadioButton otherRadio;

    @FXML
    private ToggleGroup localOtherGroup;

    static private String getTimeZoneString(final TimeZone timeZone) {
        final String id = ZoneOffset.ofTotalSeconds(timeZone.getOffset(System.currentTimeMillis()) / 1000).getId();
        final String timeZoneString = "(GMT" + ("Z".equals(id) ? "+00:00" : id) + ") " + timeZone.getID() + " [" + timeZone.getDisplayName(timeZone.observesDaylightTime() && timeZone.inDaylightTime(new Date()), TimeZone.SHORT) + "]"; // NON-NLS
        return timeZoneString;
    }

    @FXML
    @NbBundle.Messages({"TimeZonePanel.title=Display Times In:"})
    public void initialize() {
        setText(Bundle.TimeZonePanel_title());
//        localRadio.setText("Local Time Zone: " + getTimeZoneString(TimeZone.getDefault()));
        localRadio.setText(NbBundle.getMessage(this.getClass(), "TimeZonePanel.localRadio.text"));
        otherRadio.setText(NbBundle.getMessage(this.getClass(), "TimeZonePanel.otherRadio.text"));
        // The text field for this TimeZonePanel (TitlePane) object is set by the instantiating class (TimeLineTopComponent).

        localOtherGroup.selectedToggleProperty().addListener(
                (ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue) -> {
                    if (newValue == localRadio) {
                        TimeLineController.setTimeZone(TimeZone.getDefault());
                    } else {
                        TimeLineController.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
                    }
                });
    }

    public TimeZonePanel() {
        FXMLConstructor.construct(this, "TimeZonePanel.fxml"); // NON-NLS
    }
}
