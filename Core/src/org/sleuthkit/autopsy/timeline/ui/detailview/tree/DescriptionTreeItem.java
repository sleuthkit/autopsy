/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-19 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.scene.control.TreeItem;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventStripe;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * EventsTreeItem for specific event descriptions
 */
class DescriptionTreeItem extends EventsTreeItem {

    /**
     * A map of the children DescriptionTreeItem, keyed by description.
     */
    private final Map<String, DescriptionTreeItem> childMap = new HashMap<>();

    /**
     * Constructor
     *
     * @param event      the event that backs this tree item
     * @param comparator the initial comparator used to sort the children of
     *                   this tree item
     */
    DescriptionTreeItem(DetailViewEvent event, Comparator<TreeItem<DetailViewEvent>> comparator) {
        super(comparator);
        setValue(event);
    }

    @Override
    public void insert(List<DetailViewEvent> path) {
        DetailViewEvent head = path.remove(0);

        //strip off parent description
        String substringAfter = StringUtils.substringAfter(head.getDescription(), head.getParentStripe().map(EventStripe::getDescription).orElse(""));

        //create or get existing tree item for the description
        DescriptionTreeItem treeItem = childMap.computeIfAbsent(substringAfter,
                description -> configureNewTreeItem(new DescriptionTreeItem(head, getComparator()))
        );

        //insert rest of path in to tree item
        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    @Override
    void remove(List<DetailViewEvent> path) {
        DetailViewEvent head = path.remove(0);
        //strip off parent description
        String substringAfter = StringUtils.substringAfter(head.getDescription(), head.getParentStripe().map(EventStripe::getDescription).orElse(""));

        DescriptionTreeItem descTreeItem = childMap.get(substringAfter);

        //remove path from child too 
        if (descTreeItem != null) {
            if (path.isEmpty() == false) {
                descTreeItem.remove(path);
            }
            //if child item has no children, remove it also.
            if (descTreeItem.getChildren().isEmpty()) {
                childMap.remove(substringAfter);
                getChildren().remove(descTreeItem);
            }
        }
    }

    @Override
    void sort(Comparator<TreeItem<DetailViewEvent>> comparator, Boolean recursive) {
        setComparator(comparator);
        FXCollections.sort(getChildren(), comparator); //sort children with new comparator
        if (recursive) {
            //resort children's children
            childMap.values().forEach(treeItem -> treeItem.sort(comparator, true));
        }
    }

    @Override
    public EventsTreeItem findTreeItemForEvent(DetailViewEvent event) {
        if (getValue().getEventType() == event.getEventType()
            && getValue().getDescription().equals(event.getDescription())) {
            //if this tree item match the given event, return this.
            return this;
        } else {
            //search children
            return super.findTreeItemForEvent(event);
        }
    }

    @Override
    String getDisplayText() {

        String text = getValue().getDescription() + " (" + getValue().getSize() + ")"; // NON-NLS

        TreeItem<DetailViewEvent> parent = getParent();
        if (parent != null && parent.getValue() != null && (parent instanceof DescriptionTreeItem)) {
            //strip off parent description
            text = StringUtils.substringAfter(text, parent.getValue().getDescription());
        }
        return text;
    }

    @Override
    TimelineEventType getEventType() {
        return getValue().getEventType();
    }
}
