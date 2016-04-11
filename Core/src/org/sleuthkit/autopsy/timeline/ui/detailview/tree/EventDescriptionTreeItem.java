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
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 *
 */
class EventDescriptionTreeItem extends EventsTreeItem {

    /**
     * maps a description to the child item of this item with that description
     */
    private final Map<String, EventDescriptionTreeItem> childMap = new HashMap<>();

    EventDescriptionTreeItem(EventStripe stripe, Comparator<TreeItem<TimeLineEvent>> comp) {
        super(comp);
        setValue(stripe);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void insert(Deque<EventStripe> path) {
        EventStripe head = path.removeFirst();
        String substringAfter = StringUtils.substringAfter(head.getDescription(), head.getParentStripe().map(EventStripe::getDescription).orElse(""));
        EventDescriptionTreeItem treeItem = childMap.computeIfAbsent(substringAfter,
                description -> configureNewTreeItem(new EventDescriptionTreeItem(head, getComparator()))
        );

        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    @Override
    void remove(Deque<EventStripe> path) {
        EventStripe head = path.removeFirst();
        String substringAfter = StringUtils.substringAfter(head.getDescription(), head.getParentStripe().map(EventStripe::getDescription).orElse(""));
        EventDescriptionTreeItem descTreeItem = childMap.get(substringAfter);
        if (path.isEmpty() == false) {
            descTreeItem.remove(path);
        }
        if (descTreeItem.getChildren().isEmpty()) {
            childMap.remove(head.getDescription());
            getChildren().remove(descTreeItem);
        }
    }

    @Override
    void resort(Comparator<TreeItem<TimeLineEvent>> comp, Boolean recursive) {
        setComparator(comp);
        FXCollections.sort(getChildren(), comp);
        if (recursive) {
            childMap.values().forEach(ti -> ti.resort(comp, true));
        }
    }

    @Override
    public EventsTreeItem findTreeItemForEvent(TimeLineEvent event) {

        if (getValue().getEventType() == event.getEventType()
                && getValue().getDescription().equals(event.getDescription())) {
            return this;
        } else {
            for (EventDescriptionTreeItem child : childMap.values()) {
                final EventsTreeItem findTreeItemForEvent = child.findTreeItemForEvent(event);
                if (findTreeItemForEvent != null) {
                    return findTreeItemForEvent;
                }
            }
        }
        return null;
    }

    @Override
    String getDisplayText() {
        String text = getValue().getDescription() + " (" + getValue().getSize() + ")"; // NON-NLS

        TreeItem<TimeLineEvent> parent = getParent();
        if (parent != null && parent.getValue() != null && (parent instanceof EventDescriptionTreeItem)) {
            text = StringUtils.substringAfter(text, parent.getValue().getDescription());
        }
        return text;
    }

    @Override
    EventType getEventType() {
        return getValue().getEventType();
    }
}
