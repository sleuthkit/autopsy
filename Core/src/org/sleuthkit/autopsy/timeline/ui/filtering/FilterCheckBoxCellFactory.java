/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering;

import java.util.function.Supplier;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.IndexedCell;
import org.sleuthkit.autopsy.timeline.ui.AbstractFXCellFactory;
import org.sleuthkit.autopsy.timeline.filters.AbstractFilter;

class FilterCheckBoxCellFactory<X extends AbstractFilter> extends AbstractFXCellFactory<X, X> {

    private final CheckBox checkBox = new CheckBox();
    private SimpleBooleanProperty selectedProperty;
    private SimpleBooleanProperty disabledProperty;

    @Override
    protected void configureCell(IndexedCell<? extends X> cell, X item, boolean empty, Supplier<X> supplier) {
        if (selectedProperty != null) {
            checkBox.selectedProperty().unbindBidirectional(selectedProperty);
        }
        if (disabledProperty != null) {
            checkBox.disableProperty().unbindBidirectional(disabledProperty);
        }

        if (item == null) {
            cell.setText(null);
            cell.setGraphic(null);
        } else {
            cell.setText(item.getDisplayName());
            selectedProperty = item.selectedProperty();
            checkBox.selectedProperty().bindBidirectional(selectedProperty);
            disabledProperty = item.getDisabledProperty();
            checkBox.disableProperty().bindBidirectional(disabledProperty);
            cell.setGraphic(checkBox);
        }
    }
}
