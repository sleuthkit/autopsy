/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview.tree;

import java.util.Comparator;
import java.util.Deque;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;

public class SubTypeTreeItem extends EventTypeTreeItem<EventDescriptionTreeItem> {

    SubTypeTreeItem(EventStripe stripe, Comparator<TreeItem<TimeLineEvent>> comp) {
        super(stripe.getEventType(), comp);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void insert(Deque<EventStripe> path) {
        EventStripe head = path.removeFirst();
        EventDescriptionTreeItem treeItem = childMap.computeIfAbsent(head.getDescription(),
                description -> configureNewTreeItem(new EventDescriptionTreeItem(head, getComparator()))
        );

        if (path.isEmpty() == false) {
            treeItem.insert(path);
        }
    }

    @Override
    void remove(Deque<EventStripe> path) {
        EventStripe head = path.removeFirst();
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
