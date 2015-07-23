/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
                activeProperty = item.getActiveProperty();
                checkBox.selectedProperty().bindBidirectional(activeProperty);
                checkBox.disableProperty().bind(item.getDisabledProperty());
                setGraphic(checkBox);
            }
        });
    }
}
