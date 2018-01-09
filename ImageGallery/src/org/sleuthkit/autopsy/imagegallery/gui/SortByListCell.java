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
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupSortBy;

public class SortByListCell extends ListCell<GroupSortBy> {
    
    @Override
    protected void updateItem(GroupSortBy t, boolean bln) {
        super.updateItem(t, bln);
        if (t != null) {
            setText(t.getDisplayName());
            setGraphic(new ImageView(t.getIcon()));
        } else {
            setText(null);
            setGraphic(null);
        }
    }
}
