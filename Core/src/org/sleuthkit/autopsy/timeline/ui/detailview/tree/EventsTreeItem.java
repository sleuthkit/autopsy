/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 * A node in the nav tree. Manages inserts and resorts. Has parents and
 * children. Does not have graphical properties these are configured in
 * {@link EventTreeCell}. Each NavTreeItem has a EventBundle which has a type,
 * description , count, etc.
 */
abstract class EventsTreeItem<T, S extends EventsTreeItem<?, ?>> extends TreeItem<TimeLineEvent> {

    /**
     * maps a description to the child item of this item with that description
     */
    final Map<T, S> childMap = new HashMap<>();

    /**
     * the comparator if any used to sort the children of this item
     */
    private Comparator<TreeItem<TimeLineEvent>> comparator;

    EventsTreeItem(Comparator<TreeItem<TimeLineEvent>> comparator) {
        this.comparator = comparator;
    }

    public Comparator<TreeItem<TimeLineEvent>> getComparator() {
        return comparator;
    }

    final protected void setComparator(Comparator<TreeItem<TimeLineEvent>> comparator) {
        this.comparator = comparator;
    }

    abstract void resort(Comparator<TreeItem<TimeLineEvent>> comp, Boolean recursive);

    public EventsTreeItem<?, ?> findTreeItemForEvent(TimeLineEvent t) {
        for (S child : childMap.values()) {
            final EventsTreeItem<?, ?> findTreeItemForEvent = child.findTreeItemForEvent(t);
            if (findTreeItemForEvent != null) {
                return findTreeItemForEvent;
            }
        }
        return null;
    }

    abstract String getDisplayText();

    abstract EventType getEventType();

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    abstract void remove(Deque<TimeLineEvent> path);

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    abstract void insert(Deque<TimeLineEvent> path);

    S configureNewTreeItem(S newTreeItem) {
        newTreeItem.setExpanded(true);
        getChildren().add(newTreeItem);
        resort(getComparator(), false);
        return newTreeItem;
    }
}
