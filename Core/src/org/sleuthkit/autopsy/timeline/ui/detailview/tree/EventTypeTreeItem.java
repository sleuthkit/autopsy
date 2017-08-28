/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 * abstract EventTreeItem for event types
 */
abstract class EventTypeTreeItem extends EventsTreeItem {

    /**
     * The event type for this tree item.
     */
    private final EventType eventType;

    /**
     * Constructor
     *
     * @param eventType  the event type for this tree item
     * @param comparator the initial comparator used to sort the children of
     *                   this tree item
     */
    EventTypeTreeItem(EventType eventType, Comparator<TreeItem<TimeLineEvent>> comparator) {
        super(comparator);
        this.eventType = eventType;
    }

    @Override
    void sort(Comparator<TreeItem<TimeLineEvent>> comp, Boolean recursive) {
        setComparator(comp);
        if (recursive) {
            //sort childrens children
            getChildren().stream()
                    .map(EventsTreeItem.class::cast)
                    .forEach(ti -> ti.sort(comp, true));
        }
    }

    @Override
    String getDisplayText() {
        return eventType.getDisplayName();
    }

    @Override
    EventType getEventType() {
        return eventType;
    }
}
