/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.datamodel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 * A container for several events that have the same timestamp and description
 * and are backed by the same file. Used in the ListView to coalesce the file
 * system events for a file when they have the same timestamp.
 */
public class CombinedEvent {

    private final long fileID;
    private final long epochMillis;
    private final String description;
    private final Map<EventType, Long> eventTypeMap = new HashMap<>();

    /**
     * Constructor
     *
     * @param epochMillis The timestamp for this event, in millis from the Unix
     *                    epoch.
     * @param description The full description shared by all the combined events
     * @param fileID      The ID of the file all the combined events are for.
     * @param eventMap    A map from EventType to the ID of the event for the
     *                    given file ID with that type.
     */
    public CombinedEvent(long epochMillis, String description, long fileID, Map<EventType, Long> eventMap) {
        this.epochMillis = epochMillis;
        this.description = description;
        eventTypeMap.putAll(eventMap);
        this.fileID = fileID;
    }

    /**
     * Get the timestamp of this event as millis from the Unix epoch.
     *
     * @return The timestamp of this event as millis from the Unix epoch.
     */
    public long getStartMillis() {
        return epochMillis;
    }

    /**
     * Get the full description shared by all the combined events.
     *
     * @return The full description shared by all the combined events.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the obj ID of the file shared by the combined events.
     *
     * @return The obj ID of the file shared by the combined events.
     */
    public long getFileID() {
        return fileID;
    }

    /**
     * Get the types of the combined events.
     *
     * @return The types of the combined events.
     */
    public Set<EventType> getEventTypes() {
        return eventTypeMap.keySet();
    }

    /**
     * Get the event IDs of the combined events.
     *
     * @return The event IDs of the combined events.
     */
    public Collection<Long> getEventIDs() {
        return eventTypeMap.values();
    }

    /**
     * Get the event ID of one event that is representative of all the combined
     * events. It can be used to look up a SingleEvent with more details, for
     * example.
     *
     * @return An arbitrary representative event ID for the combined events.
     */
    public Long getRepresentitiveEventID() {
        return eventTypeMap.values().stream().findFirst().get();
    }
}
