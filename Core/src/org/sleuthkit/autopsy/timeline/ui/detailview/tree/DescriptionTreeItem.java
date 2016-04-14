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
import javafx.collections.FXCollections;
import javafx.scene.control.TreeItem;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 * EventsTreeItem for specific event descriptions
 */
class DescriptionTreeItem extends EventsTreeItem<String, DescriptionTreeItem> {

    DescriptionTreeItem(TimeLineEvent stripe, Comparator<TreeItem<TimeLineEvent>> comp) {
        super(comp);
        setValue(stripe);
    }

    @Override
    public void insert(Deque<TimeLineEvent> path) {
        TimeLineEvent head = path.removeFirst();
        String substringAfter = StringUtils.substringAfter(head.getDescription(), head.getParentStripe().map(EventStripe::getDescription).orElse(""));
        DescriptionTreeItem treeItem = childMap.computeIfAbsent(substringAfter,
                description -> configureNewTreeItem(new DescriptionTreeItem(head, getComparator()))
        );

        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    @Override
    void remove(Deque<TimeLineEvent> path) {
        TimeLineEvent head = path.removeFirst();
        String substringAfter = StringUtils.substringAfter(head.getDescription(), head.getParentStripe().map(EventStripe::getDescription).orElse(""));
        DescriptionTreeItem descTreeItem = childMap.get(substringAfter);

        if (descTreeItem != null) {
            if (path.isEmpty() == false) {
                descTreeItem.remove(path);
            }
            if (descTreeItem.getChildren().isEmpty()) {
                childMap.remove(substringAfter);
                getChildren().remove(descTreeItem);
            }
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
    public EventsTreeItem<?, ?> findTreeItemForEvent(TimeLineEvent event) {
        if (getValue().getEventType() == event.getEventType()
                && getValue().getDescription().equals(event.getDescription())) {
            return this;
        } else {
            return super.findTreeItemForEvent(event);
        }
    }

    @Override
    String getDisplayText() {
        String text = getValue().getDescription() + " (" + getValue().getSize() + ")"; // NON-NLS

        TreeItem<TimeLineEvent> parent = getParent();
        if (parent != null && parent.getValue() != null && (parent instanceof DescriptionTreeItem)) {
            text = StringUtils.substringAfter(text, parent.getValue().getDescription());
        }
        return text;
    }

    @Override
    EventType getEventType() {
        return getValue().getEventType();
    }
}
