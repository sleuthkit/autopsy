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
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;

/**
 * EventTreeItem for sub event types
 */
public class SubTypeTreeItem extends EventTypeTreeItem<EventDescriptionTreeItem> {

    SubTypeTreeItem(TimeLineEvent stripe, Comparator<TreeItem<TimeLineEvent>> comp) {
        super(stripe.getEventType(), comp);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void insert(Deque<TimeLineEvent> path) {
        TimeLineEvent head = path.removeFirst();
        EventDescriptionTreeItem treeItem = childMap.computeIfAbsent(head.getDescription(),
                description -> configureNewTreeItem(new EventDescriptionTreeItem(head, getComparator()))
        );

        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    @Override
    void remove(Deque<TimeLineEvent> path) {
        TimeLineEvent head = path.removeFirst();
        EventsTreeItem descTreeItem = childMap.get(head.getDescription());
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

    /**
     *
     * @param t
     *
     * @return
     */
    @Override
    EventsTreeItem findTreeItemForEvent(TimeLineEvent t) {

        for (EventsTreeItem child : childMap.values()) {
            final EventsTreeItem findTreeItemForEvent = child.findTreeItemForEvent(t);
            if (findTreeItemForEvent != null) {
                return findTreeItemForEvent;
            }
        }
        return null;
    }
}
