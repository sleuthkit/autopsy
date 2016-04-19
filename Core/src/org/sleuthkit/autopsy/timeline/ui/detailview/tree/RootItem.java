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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 * TreeItem for the root of all the events in the EventsTree.
 */
class RootItem extends EventsTreeItem {

    /**
     * A map of the children BaseTypeTreeItems, keyed by EventType.
     */
    private final Map<EventType, BaseTypeTreeItem> childMap = new HashMap<>();

    RootItem(Comparator<TreeItem<TimeLineEvent>> comp) {
        super(comp);
    }

    /**
     * Recursive method to add a grouping at a given path.
     *
     * @param event stripe to add
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void insert(TimeLineEvent event) {
        insert(getTreePath(event));
    }

    void remove(TimeLineEvent event) {
        remove(getTreePath(event));
    }

    private static List<TimeLineEvent> getTreePath(TimeLineEvent event) {
        List<TimeLineEvent> path = new ArrayList<>();
        path.add(0, event);
        Optional<EventStripe> parentOptional = event.getParentStripe();
        while (parentOptional.isPresent()) {
            EventStripe parent = parentOptional.get();
            path.add(0, parent);
            parentOptional = parent.getParentStripe();
        }
        return path;
    }

    @Override
    void sort(Comparator<TreeItem<TimeLineEvent>> comp, Boolean recursive) {
        setComparator(comp);
        childMap.values().forEach(ti -> ti.sort(comp, true));
    }

    @Override
    String getDisplayText() {
        return "";
    }

    @Override
    EventType getEventType() {
        return null;
    }

    @Override
    void remove(List<TimeLineEvent> path) {
        TimeLineEvent event = path.get(0);
        BaseTypeTreeItem typeTreeItem = childMap.get(event.getEventType().getBaseType());
        if (typeTreeItem != null) {
            typeTreeItem.remove(path);

            if (typeTreeItem.getChildren().isEmpty()) {
                childMap.remove(event.getEventType().getBaseType());
                getChildren().remove(typeTreeItem);
            }
        }
    }

    @Override
    void insert(List<TimeLineEvent> path) {
        TimeLineEvent event = path.get(0);
        BaseTypeTreeItem treeItem = childMap.computeIfAbsent(event.getEventType().getBaseType(),
                baseType -> configureNewTreeItem(new BaseTypeTreeItem(event, getComparator()))
        );
        treeItem.insert(path);
    }
}
