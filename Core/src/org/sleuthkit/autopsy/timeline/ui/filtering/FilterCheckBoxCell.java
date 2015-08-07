/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.filtering;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeTableCell;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;

/**
 * A {@link TreeTableCell} that represents the active state of a
 * {@link AbstractFilter} as a checkbox
 */
class FilterCheckBoxCell extends TreeTableCell<AbstractFilter, AbstractFilter> {

    private final CheckBox checkBox = new CheckBox();
    private SimpleBooleanProperty activeProperty;

    @Override
    protected void updateItem(AbstractFilter item, boolean empty) {
        super.updateItem(item, empty);
        Platform.runLater(() -> {
            if (activeProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(activeProperty);
            }
            checkBox.disableProperty().unbind();
            if (item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getDisplayName());
                activeProperty = item.getSelectedProperty();
                checkBox.selectedProperty().bindBidirectional(activeProperty);
                checkBox.disableProperty().bind(item.getDisabledProperty());
                setGraphic(checkBox);
            }
        });
    }
}
