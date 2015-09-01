/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;

class EventTypeTreeItem extends NavTreeItem {

    /**
     * maps a description to the child item of this item with that description
     */
    private final Map<String, EventDescriptionTreeItem> childMap = new ConcurrentHashMap<>();

    private final Comparator<TreeItem<NavTreeNode>> comparator = TreeComparator.Description;

    EventTypeTreeItem(EventCluster g) {
        setValue(new NavTreeNode(g.getType().getBaseType(), g.getType().getBaseType().getDisplayName(), 0));
    }

    @Override
    public int getCount() {
        return getValue().getCount();
    }

    /**
     * Recursive method to add a grouping at a given path.
     *
     * @param path Full path (or subset not yet added) to add
     * @param g    Group to add
     * @param tree True if it is part of a tree (versus a list)
     */
    @Override
    public void insert(EventCluster g) {

        EventDescriptionTreeItem treeItem = childMap.get(g.getDescription());
        if (treeItem == null) {
            final EventDescriptionTreeItem newTreeItem = new EventDescriptionTreeItem(g);
            newTreeItem.setExpanded(true);
            childMap.put(g.getDescription(), newTreeItem);

            Platform.runLater(() -> {
                synchronized (getChildren()) {
                    getChildren().add(newTreeItem);
                    FXCollections.sort(getChildren(), comparator);
                }
            });
        } else {
            treeItem.insert(g);
        }
        Platform.runLater(() -> {
            NavTreeNode value1 = getValue();
            setValue(new NavTreeNode(value1.getType().getBaseType(), value1.getType().getBaseType().getDisplayName(), childMap.values().stream().mapToInt(EventDescriptionTreeItem::getCount).sum()));
        });

    }

    @Override
    public TreeItem<NavTreeNode> findTreeItemForEvent(EventBundle t) {
        if (t.getType().getBaseType() == getValue().getType().getBaseType()) {

            for (TreeItem<NavTreeNode> child : getChildren()) {
                final TreeItem<NavTreeNode> findTreeItemForEvent = ((NavTreeItem) child).findTreeItemForEvent(t);
                if (findTreeItemForEvent != null) {
                    return findTreeItemForEvent;
                }
            }
        }
        return null;
    }

    @Override
    public void resort(Comparator<TreeItem<NavTreeNode>> comp) {
        FXCollections.sort(getChildren(), comp);
    }
}
