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

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
    public void insert(EventBundle g) {

        EventTypeTreeItem treeItem = childMap.computeIfAbsent(g.getEventType().getBaseType(),
                baseType -> {
                    EventTypeTreeItem newTreeItem = new EventTypeTreeItem(g);
                    newTreeItem.setExpanded(true);
                    getChildren().add(newTreeItem);
                    getChildren().sort(TreeComparator.Type);
                    return newTreeItem;
                });
        treeItem.insert(getTreePath(g));
    }

    static Deque<EventBundle> getTreePath(EventBundle g) {
        Deque<EventBundle> path = new ArrayDeque<>();
        Optional<EventBundle> p = Optional.of(g);

        while (p.isPresent()) {
            EventBundle parent = p.get();
            path.addFirst(parent);
            p = parent.getParentBundle();
        }

        return path;
    }

    @Override
    public void resort(Comparator<TreeItem<NavTreeNode>> comp) {
        childMap.values().forEach((ti) -> {
            ti.resort(comp);
        });
    }

    @Override
    public NavTreeItem findTreeItemForEvent(EventBundle t) {
        for (TreeItem<NavTreeNode> child : getChildren()) {
            final NavTreeItem findTreeItemForEvent = ((NavTreeItem) child).findTreeItemForEvent(t);
            if (findTreeItemForEvent != null) {
                return findTreeItemForEvent;
            }
        }
        return null;
    }
}
