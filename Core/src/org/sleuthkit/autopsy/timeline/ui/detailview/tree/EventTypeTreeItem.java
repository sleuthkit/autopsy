/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview.tree;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 *
 */
abstract class EventTypeTreeItem<T extends EventsTreeItem> extends EventsTreeItem {

    /**
     * maps a description to the child item of this item with that description
     */
    final Map<String, T> childMap = new HashMap<>();

    private final EventType eventType;

    EventTypeTreeItem(EventType eventType, Comparator<TreeItem<TimeLineEvent>> comp) {
        super(comp);
        this.eventType = eventType;
    }

    @Override
    void resort(Comparator<TreeItem<TimeLineEvent>> comp, Boolean recursive) {
        setComparator(comp);
        if (recursive) {
            childMap.values().forEach(ti -> ti.resort(comp, true));
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
