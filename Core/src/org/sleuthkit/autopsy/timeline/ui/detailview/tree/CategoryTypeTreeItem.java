/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
import java.util.function.Supplier;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * EventTreeItem for category event types (file system, misc, web, ...)
 */
class CategoryTypeTreeItem extends EventTypeTreeItem {

    /**
     * A map of the children TreeItems, keyed by EventTypes if the children are
     * SubTypeTreeItems or by String if the children are DescriptionTreeItems.
     */
    private final Map<Object, EventsTreeItem> childMap = new HashMap<>();

    /**
     * Constructor
     *
     * @param event      the event that backs this tree item
     * @param comparator the initial comparator used to sort the children of
     *                   this tree item
     */
    CategoryTypeTreeItem(DetailViewEvent event, Comparator<TreeItem<DetailViewEvent>> comparator) {
        super(event.getEventType().getCategory(), comparator);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @Override
    public void insert(List<DetailViewEvent> path) {
        DetailViewEvent head = path.get(0);

        Supplier< EventsTreeItem> treeItemConstructor;
        String descriptionKey;
        /*
         * if the stripe and this tree item have the same type, create a
         * description tree item, else create a sub-type tree item
         */
        if (head.getEventType().getTypeHierarchyLevel() == TimelineEventType.HierarchyLevel.CATEGORY) {
            descriptionKey = head.getEventType().getDisplayName();
            treeItemConstructor = () -> configureNewTreeItem(new SubTypeTreeItem(head, getComparator()));
        } else {
            descriptionKey = head.getDescription();
            DetailViewEvent stripe = path.remove(0); //remove head of list if we are going straight to description
            treeItemConstructor = () -> configureNewTreeItem(new DescriptionTreeItem(stripe, getComparator()));
        }

        EventsTreeItem treeItem = childMap.computeIfAbsent(descriptionKey, key -> treeItemConstructor.get());

        //insert (rest of) path in to new treeItem
        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    @Override
    void remove(List<DetailViewEvent> path) {
        DetailViewEvent head = path.get(0);

        EventsTreeItem descTreeItem;
        /*
         * if the stripe and this tree item have the same type, get the child
         * item keyed on event type, else keyed on description.
         */
        if (head.getEventType().getTypeHierarchyLevel()== TimelineEventType.HierarchyLevel.CATEGORY) {
            descTreeItem = childMap.get(head.getEventType().getDisplayName());
        } else {
            path.remove(0); //remove head of list if we are going straight to description
            descTreeItem = childMap.get(head.getDescription());
        }

        //remove path from child too 
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
