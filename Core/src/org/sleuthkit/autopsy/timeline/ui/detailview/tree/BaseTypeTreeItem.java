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
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;

/**
 * EventTreeItem for base event types (file system, misc, web, ...)
 */
class BaseTypeTreeItem extends EventTypeTreeItem {

    /**
     * A map of the children TreeItems, keyed by EventTypes if the children are
     * SubTypeTreeItems or by String if the children are DescriptionTreeItems.
     */
    private final Map<Object, EventsTreeItem> childMap = new HashMap<>();

    BaseTypeTreeItem(TimeLineEvent stripe, Comparator<TreeItem<TimeLineEvent>> comp) {
        super(stripe.getEventType().getBaseType(), comp);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @Override
    public void insert(Deque<TimeLineEvent> path) {
        TimeLineEvent peek = path.peek();
        Supplier< EventsTreeItem> treeItemConstructor;
        String descriptionKey;
        /*
         * if the stripe and this tree item haveS the same type, create an
         * description treeitem, else create a sub-type tree item
         */
        if (peek.getEventType().getZoomLevel() == EventTypeZoomLevel.SUB_TYPE) {
            descriptionKey = peek.getEventType().getDisplayName();
            treeItemConstructor = () -> configureNewTreeItem(new SubTypeTreeItem(peek, getComparator()));
        } else {
            descriptionKey = peek.getDescription();
            TimeLineEvent stripe = path.removeFirst();
            treeItemConstructor = () -> configureNewTreeItem(new DescriptionTreeItem(stripe, getComparator()));
        }

        EventsTreeItem treeItem = childMap.computeIfAbsent(descriptionKey, key -> treeItemConstructor.get());

        //insert (rest of) path in to new treeItem
        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    @Override
    void remove(Deque<TimeLineEvent> path) {

        TimeLineEvent head = path.peek();

        EventsTreeItem descTreeItem;
        if (head.getEventType().getZoomLevel() == EventTypeZoomLevel.SUB_TYPE) {
            descTreeItem = childMap.get(head.getEventType().getDisplayName());
        } else {
            path.removeFirst();
            descTreeItem = childMap.get(head.getDescription());
        }

        if (descTreeItem != null) {
            if (path.isEmpty() == false) {
                descTreeItem.remove(path);
            }
            if (descTreeItem.getChildren().isEmpty()) {
                childMap.remove(head.getDescription());
                getChildren().remove(descTreeItem);
            }
        }
    }
}
