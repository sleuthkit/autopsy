/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-18 Basis Technology Corp.
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
import java.util.List;
import java.util.Optional;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventStripe;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * A node in the nav tree. Manages inserts and resorts. Has parents and
 * children. Does not have graphical properties these are configured in
 * EventsTree.EventTreeCell. Each EventsTreeItem has a DetailViewEvent which has a
 * type, description , count, etc.
 */
abstract class EventsTreeItem extends TreeItem<DetailViewEvent> {

    /**
     * the comparator if any used to sort the children of this item
     */
    private Comparator<TreeItem<DetailViewEvent>> comparator;

    /**
     * Constructor
     *
     * @param comparator the initial comparator used to sort the children of
     *                   this tree item
     */
    EventsTreeItem(Comparator<TreeItem<DetailViewEvent>> comparator) {
        this.comparator = comparator;
    }

    /**
     * Get the comparator currently used to sort this tree items children.
     *
     * @return the comparator currently used to sort this tree items children.
     */
    public Comparator<TreeItem<DetailViewEvent>> getComparator() {
        return comparator;
    }

    final protected void setComparator(Comparator<TreeItem<DetailViewEvent>> comparator) {
        this.comparator = comparator;
    }

    /**
     * Sort this tree item's children.
     *
     * @param comparator the comparator to use to sort this tree item's children
     * @param recursive  if true: sort the children's children , etc using the
     *                   same given comparator
     */
    abstract void sort(Comparator<TreeItem<DetailViewEvent>> comparator, Boolean recursive);

    /**
     * Get the tree item for the given event if on exists in this tree (item)
     *
     * @param event the event to find a tree item for
     *
     * @return an EventsTreeItem for the given eventm or null if there is none
     *         in this tree (item). Could return this tree item.
     */
    public EventsTreeItem findTreeItemForEvent(DetailViewEvent event) {
        for (TreeItem<DetailViewEvent> child : getChildren()) {
            final EventsTreeItem findTreeItemForEvent = ((EventsTreeItem) child).findTreeItemForEvent(event);
            if (findTreeItemForEvent != null) {
                return findTreeItemForEvent;
            }
        }
        return null;
    }

    /**
     * Get the text to display in the tree for this item.
     *
     * @return the display text for this item.
     */
    abstract String getDisplayText();

    /**
     * Get the EventType of this tree item.
     *
     * @return the EventType of this tree item.
     */
    abstract TimelineEventType getEventType();

    /**
     * Remove the event represented by the given path from this tree item and
     * all of this tree item's children.
     *
     * @param path A representation of an event as a path from its root
     *             EventStripe though the tree, as returned by
     *             getTreePath(event)
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    abstract void remove(List<DetailViewEvent> path);

    /**
     * Insert the event represented by the given path into this tree item and
     * all of this tree item's children.
     *
     * @param path A representation of an event as a path from its root
     *             EventStripe though the tree, as returned by
     *             getTreePath(event)
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    abstract void insert(List<DetailViewEvent> path);

    /**
     * Get the tree path from the root event stripe of the given event to the
     * given event itself
     *
     * @param event the event to get a tree path for
     *
     * @return A representation of an event as a path from its root EventStripe
     *         though the tree. The root is the first item and the event itself
     *         is the last item in the list.
     */
    static List<DetailViewEvent> getTreePath(DetailViewEvent event) {
        List<DetailViewEvent> path = new ArrayList<>();
        path.add(0, event);
        Optional<EventStripe> parentOptional = event.getParentStripe();
        while (parentOptional.isPresent()) {
            EventStripe parent = parentOptional.get();
            path.add(0, parent);
            parentOptional = parent.getParentStripe();
        }
        return path;
    }

    /**
     * Configure initial properties that all EventsTreeItems share.
     *
     * @param <T>         the type of tree item
     * @param newTreeItem a new tree item of type T
     *
     * @return the given tree item with initial properties configured
     */
    <T extends EventsTreeItem> T configureNewTreeItem(T newTreeItem) {
        getChildren().add(newTreeItem);
        newTreeItem.setExpanded(true);
        sort(getComparator(), false);
        return newTreeItem;
    }
}
