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
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 *
 */
class RootItem extends EventsTreeItem {

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

    /**
     * Recursive method to add a grouping at a given path.
     *
     * @param stripe stripe to add
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void insert(EventStripe stripe) {

        EventTypeTreeItem treeItem = childMap.computeIfAbsent(stripe.getEventType().getBaseType(),
                baseType -> {
                    EventTypeTreeItem newTreeItem = new EventTypeTreeItem(stripe, comparator);
                    newTreeItem.setExpanded(true);
                    getChildren().add(newTreeItem);
                    return newTreeItem;
                });
        treeItem.insert(getTreePath(stripe));
    }

    void remove(EventStripe stripe) {
        EventTypeTreeItem typeTreeItem = childMap.get(stripe.getEventType().getBaseType());
        if (typeTreeItem != null) {
            typeTreeItem.remove(getTreePath(stripe));

            if (typeTreeItem.getChildren().isEmpty()) {
                childMap.remove(stripe.getEventType().getBaseType());
                getChildren().remove(typeTreeItem);
            }
        }
    }

    static Deque< EventStripe> getTreePath(EventStripe event) {
        Deque<EventStripe> path = new ArrayDeque<>();
        path.addFirst(event);
        Optional<EventStripe> parentOptional = event.getParentStripe();
        while (parentOptional.isPresent()) {
            EventStripe parent = parentOptional.get();
            path.addFirst(parent);
            parentOptional = parent.getParentStripe();
        }
        return path;
    }

    @Override
    void resort(Comparator<TreeItem<TimeLineEvent>> comp, Boolean recursive) {
        comparator = comp;
        childMap.values().forEach(ti -> ti.resort(comp, true));
    }

    @Override
    public EventsTreeItem findTreeItemForEvent(TimeLineEvent t) {
        for (EventTypeTreeItem child : childMap.values()) {
            final EventsTreeItem findTreeItemForEvent = child.findTreeItemForEvent(t);
            if (findTreeItemForEvent != null) {
                return findTreeItemForEvent;
            }
        }
        return null;
    }
}
