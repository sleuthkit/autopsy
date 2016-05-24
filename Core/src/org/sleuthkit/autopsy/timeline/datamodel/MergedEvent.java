/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.datamodel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 * A container for several file-system events that have the same timestamp and
 * description. Used in the ListView
 */
public class MergedEvent {

    private final long fileID;
    private final long epochMillis;
    private final String description;
    private final Map<EventType, Long> eventTypeMap = new HashMap<>();

    public MergedEvent(long epochMillis, String description, long fileID, Map<EventType, Long> eventMap) {
        this.epochMillis = epochMillis;
        this.description = description;
        eventTypeMap.putAll(eventMap);
        this.fileID = fileID;
    }

    public long getEpochMillis() {
        return epochMillis;
    }

    public String getDescription() {
        return description;
    }

    public long getFileID() {
        return fileID;
    }

    public Set<EventType> getEventTypes() {
        return eventTypeMap.keySet();
    }

    public Collection<Long> getEventIDs() {
        return eventTypeMap.values();
    }
}
