/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.listvew.datamodel;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * A container for several events that have the same timestamp and description
 * and are backed by the same file. Used in the ListView to coalesce the file
 * system events for a file when they have the same timestamp.
 */
public class CombinedEvent {

    private final long epochMillis;

    /**
     * A map from EventType to event ID.
     */
    private final Map<TimelineEventType, Long> eventTypeMap = new HashMap<>();

    /**
     * Constructor
     *
     * @param epochMillis The timestamp for this event, in millis from the Unix
     *                    epoch.
     * @param eventMap    A map from EventType to event ID.
     */
    public CombinedEvent(long epochMillis, Map<TimelineEventType, Long> eventMap) {
        this.epochMillis = epochMillis;
        eventTypeMap.putAll(eventMap);

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
     * Get the types of the combined events.
     *
     * @return The types of the combined events.
     */
    public Set<TimelineEventType> getEventTypes() {
        return eventTypeMap.keySet();
    }

    /**
     * Get the event IDs of the combined events.
     *
     * @return The event IDs of the combined events.
     */
    public Set<Long> getEventIDs() {
        return Collections.unmodifiableSet(new HashSet<>(eventTypeMap.values()));
    }

    /**
     * Get the event ID of one event that is representative of all the combined
     * events. It can be used to look up a TimelineEvent with more details, for
     * example: whether the file is tagged or a hash hit.
     *
     * @return An arbitrary representative event ID for the combined events.
     */
    public Long getRepresentativeEventID() {
        return eventTypeMap.values().stream().findFirst().get();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (int) (this.epochMillis ^ (this.epochMillis >>> 32));
        hash = 53 * hash + Objects.hashCode(this.eventTypeMap);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CombinedEvent other = (CombinedEvent) obj;

        if (this.epochMillis != other.epochMillis) {
            return false;
        }

        return Objects.equals(this.eventTypeMap, other.eventTypeMap);
    }
}
