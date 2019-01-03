/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-19 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview.tree;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;

/**
 * EventTreeItem for sub event types
 */
public class SubTypeTreeItem extends EventTypeTreeItem {

    /**
     * A map of the children DescriptionTreeItem, keyed by description string.
     */
    private final Map<String, DescriptionTreeItem> childMap = new HashMap<>();

    /**
     * Constructor
     *
     * @param event      the event that backs this tree item
     * @param comparator the initial comparator used to sort the children of
     *                   this tree item
     */
    SubTypeTreeItem(DetailViewEvent event, Comparator<TreeItem<DetailViewEvent>> comparator) {
        super(event.getEventType(), comparator);
    }

    @Override
    public void insert(List<DetailViewEvent> path) {
        DetailViewEvent head = path.remove(0);
        DescriptionTreeItem treeItem = childMap.computeIfAbsent(head.getDescription(),
                description -> configureNewTreeItem(new DescriptionTreeItem(head, getComparator()))
        );

        //insert path into  tree item
        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    @Override
    void remove(List<DetailViewEvent> path) {
        DetailViewEvent head = path.remove(0);
        DescriptionTreeItem descTreeItem = childMap.get(head.getDescription());

        //remove path from child item
        if (descTreeItem != null) {
            if (path.isEmpty() == false) {
                descTreeItem.remove(path);
            }

            //if child item has no children, remove it also.
            if (descTreeItem.getChildren().isEmpty()) {
                childMap.remove(head.getDescription());
                getChildren().remove(descTreeItem);
            }
        }
    }
}
