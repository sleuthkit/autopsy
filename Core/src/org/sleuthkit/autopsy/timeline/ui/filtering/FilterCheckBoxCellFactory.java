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

import java.util.function.Supplier;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.CheckBox;
import javafx.scene.control.IndexedCell;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;
import org.sleuthkit.autopsy.timeline.ui.AbstractFXCellFactory;

class FilterCheckBoxCellFactory<X extends AbstractFilter> extends AbstractFXCellFactory<X, X> {

    private final CheckBox checkBox = new CheckBox();
    private SimpleBooleanProperty selectedProperty;
    private ObservableBooleanValue disabledProperty;

    @Override
    protected void configureCell(IndexedCell<? extends X> cell, X item, boolean empty, Supplier<X> supplier) {
        if (selectedProperty != null) {
            checkBox.selectedProperty().unbindBidirectional(selectedProperty);
        }
        if (disabledProperty != null) {
            checkBox.disableProperty().unbind();
        }

        if (item == null) {
            cell.setGraphic(null);
        } else {
            checkBox.setText(item.getDisplayName());
//            cell.setText(item.getDisplayName());
            selectedProperty = item.selectedProperty();
            checkBox.selectedProperty().bindBidirectional(selectedProperty);
            disabledProperty = item.disabledProperty();
            checkBox.disableProperty().bind(disabledProperty);
            cell.setGraphic(checkBox);
        }
    }
}
