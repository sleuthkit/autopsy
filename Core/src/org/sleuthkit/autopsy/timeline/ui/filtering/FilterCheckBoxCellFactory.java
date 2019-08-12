/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.IndexedCell;
import org.sleuthkit.autopsy.timeline.ui.AbstractFXCellFactory;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;

class FilterCheckBoxCellFactory< X extends FilterState< ?>> extends AbstractFXCellFactory<X, X> {

    private final CheckBox checkBox = new CheckBox();
    private Property<Boolean> selectedProperty;
    private ReadOnlyBooleanProperty disabledProperty;

    @Override
    protected void configureCell(IndexedCell<? extends X> cell, X item, boolean empty, Supplier<X> supplier) {
        if (selectedProperty != null) {
            checkBox.selectedProperty().unbindBidirectional(selectedProperty);
            selectedProperty = null;
        }
        if (disabledProperty != null) {
            checkBox.disableProperty().unbind();
            disabledProperty = null;
        }

        if (item == null) {
            cell.setGraphic(null);
        } else {
            checkBox.setText(item.getDisplayName());
            disabledProperty = item.disabledProperty();
            checkBox.disableProperty().bind(disabledProperty);
            if (item.selectedProperty() instanceof Property<?>) {
                selectedProperty = item.selectedProperty();
                checkBox.selectedProperty().bindBidirectional(selectedProperty);
            }

            cell.setGraphic(checkBox);
        }
    }
}
