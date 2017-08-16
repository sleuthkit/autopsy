/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.sleuthkit.autopsy.timeline;

import javafx.scene.control.ListCell;
import javafx.scene.text.Text;

/**
 *
 */
class WrappingListCell extends ListCell<String> {

    @Override
    public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || isEmpty()) {
            setGraphic(null);
        } else {
            Text text = new Text(item);
            text.wrappingWidthProperty().bind(getListView().widthProperty().subtract(20));
            setGraphic(text);
        }
    }

}
