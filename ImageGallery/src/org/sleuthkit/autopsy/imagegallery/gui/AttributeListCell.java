/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;

/**
 */
class AttributeListCell extends ListCell<DrawableAttribute<?>> {

    @Override
    protected void updateItem(DrawableAttribute<?> item, boolean empty) {
        super.updateItem(item, empty); //To change body of generated methods, choose Tools | Templates.
        if (item != null) {
            setText(item.getDisplayName());
            setGraphic(new ImageView(item.getIcon()));
        } else {
            setGraphic(null);
            setText(null);
        }
    }
}
