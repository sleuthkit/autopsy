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
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.MultiEvent;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
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
    private Comparator<TreeItem<TimeLineEvent>> comparator = TreeComparator.Type.reversed();

    RootItem(Comparator<TreeItem<TimeLineEvent>> comp) {
        this.comparator = comp;
    }

    @Override
    public long getCount() {
        return getValue().getSize();
    }

    /**
     * Recursive method to add a grouping at a given path.
     *
     * @param bundle bundle to add
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void insert(MultiEvent<?> bundle) {

        EventTypeTreeItem treeItem = childMap.computeIfAbsent(bundle.getEventType().getBaseType(),
                baseType -> {
                    EventTypeTreeItem newTreeItem = new EventTypeTreeItem(bundle, comparator);
                    newTreeItem.setExpanded(true);
                    getChildren().add(newTreeItem);
                    return newTreeItem;
                });
        treeItem.insert(getTreePath(bundle));

    }

    void remove(MultiEvent<?> bundle) {
        EventTypeTreeItem typeTreeItem = childMap.get(bundle.getEventType().getBaseType());
        if (typeTreeItem != null) {
            typeTreeItem.remove(getTreePath(bundle));

            if (typeTreeItem.getChildren().isEmpty()) {
                childMap.remove(bundle.getEventType().getBaseType());
                getChildren().remove(typeTreeItem);
            }
        }
    }

    static Deque< MultiEvent<?>> getTreePath(MultiEvent<?> g) {
        Deque<MultiEvent<?>> path = new ArrayDeque<>();
        Optional<? extends MultiEvent<?>> p = Optional.of(g);

        while (p.isPresent()) {
            MultiEvent<?> parent = p.get();
            path.addFirst(parent);
            p = parent.getParentBundle();
        }

        return path;
    }

    @Override
    void resort(Comparator<TreeItem<TimeLineEvent>> comp, Boolean recursive) {
        comparator = comp;
        childMap.values().forEach(ti -> ti.resort(comp, true));
    }

    @Override
    public NavTreeItem findTreeItemForEvent(TimeLineEvent t) {
        for (EventTypeTreeItem child : childMap.values()) {
            final NavTreeItem findTreeItemForEvent = child.findTreeItemForEvent(t);
            if (findTreeItemForEvent != null) {
                return findTreeItemForEvent;
            }
        }
        return null;
    }
}
