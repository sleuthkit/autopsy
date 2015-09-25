/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 *
 */
class RootItem extends NavTreeItem {

    /**
     * maps a description to the child item of this item with that description
     */
    private final Map<EventType, EventTypeTreeItem> childMap = new HashMap<>();

    /**
     * the comparator if any used to sort the children of this item
     */
//    private TreeNodeComparators comp;
    RootItem() {

    }

    @Override
    public int getCount() {
        return getValue().getCount();
    }

    /**
     * Recursive method to add a grouping at a given path.
     *
     * @param g Group to add
     */
    @Override
    public void insert(EventBundle g) {

        EventTypeTreeItem treeItem = childMap.get(g.getEventType().getBaseType());
        if (treeItem == null) {
            final EventTypeTreeItem newTreeItem = new EventTypeTreeItem(g);
            newTreeItem.setExpanded(true);
            childMap.put(g.getEventType().getBaseType(), newTreeItem);
            newTreeItem.insert(g);

            Platform.runLater(() -> {
                synchronized (getChildren()) {
                    getChildren().add(newTreeItem);
                    getChildren().sort(TreeComparator.Type);
                }
            });
        } else {
            treeItem.insert(g);
        }
    }

    @Override
    public void resort(Comparator<TreeItem<NavTreeNode>> comp) {
        childMap.values().forEach((ti) -> {
            ti.resort(comp);
        });
    }

    @Override
    public TreeItem<NavTreeNode> findTreeItemForEvent(EventBundle t) {
        for (TreeItem<NavTreeNode> child : getChildren()) {
            final TreeItem<NavTreeNode> findTreeItemForEvent = ((NavTreeItem) child).findTreeItemForEvent(t);
            if (findTreeItemForEvent != null) {
                return findTreeItemForEvent;
            }
        }
        return null;
    }
}
