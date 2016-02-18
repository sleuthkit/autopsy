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
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.MultiEvent;
import org.sleuthkit.autopsy.timeline.datamodel.Event;

/**
 *
 */
class EventDescriptionTreeItem extends NavTreeItem {

    /**
     * maps a description to the child item of this item with that description
     */
    private final Map<String, EventDescriptionTreeItem> childMap = new HashMap<>();
    private final Event bundle;
    private Comparator<TreeItem<Event>> comparator = TreeComparator.Description;

    public Event getEvent() {
        return bundle;
    }

    EventDescriptionTreeItem(Event g, Comparator<TreeItem<Event>> comp) {
        bundle = g;
        comparator = comp;
        setValue(g);
    }

    @Override
    public long getCount() {
        return getValue().getCount();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void insert(Deque<MultiEvent<?>> path) {
        MultiEvent<?> head = path.removeFirst();
        EventDescriptionTreeItem treeItem = childMap.computeIfAbsent(head.getDescription(), description -> {
            EventDescriptionTreeItem newTreeItem = new EventDescriptionTreeItem(head, comparator);
            newTreeItem.setExpanded(true);
            childMap.put(description, newTreeItem);
            getChildren().add(newTreeItem);
            resort(comparator, false);
            return newTreeItem;
        });

        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    void remove(Deque<MultiEvent<?>> path) {
        MultiEvent<?> head = path.removeFirst();
        EventDescriptionTreeItem descTreeItem = childMap.get(head.getDescription());
        if (path.isEmpty() == false) {
            descTreeItem.remove(path);
        }
        if (descTreeItem.getChildren().isEmpty()) {
            childMap.remove(head.getDescription());
            getChildren().remove(descTreeItem);
        }
    }

    @Override
    void resort(Comparator<TreeItem<Event>> comp, Boolean recursive) {
        this.comparator = comp;
        FXCollections.sort(getChildren(), comp);
        if (recursive) {
            childMap.values().forEach(ti -> ti.resort(comp, true));
        }
    }

    @Override
    public NavTreeItem findTreeItemForEvent(Event t) {

        if (getValue().getEventType() == t.getEventType()
                && getValue().getDescription().equals(t.getDescription())) {
            return this;
        } else {
            for (EventDescriptionTreeItem child : childMap.values()) {
                final NavTreeItem findTreeItemForEvent = child.findTreeItemForEvent(t);
                if (findTreeItemForEvent != null) {
                    return findTreeItemForEvent;
                }
            }
        }
        return null;
    }

}
