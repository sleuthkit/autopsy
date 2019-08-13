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
package org.sleuthkit.autopsy.timeline.events;

import java.util.Set;
import org.joda.time.Interval;

/**
 * Encapsulates the result of the ShowInTimelineDialog: a Set of event IDs and
 * an Interval.
 */
public final class ViewInTimelineRequestedEvent {

    private final Set<Long> eventIDs;
    private final Interval range;

    /**
     * Constructor
     *
     * @param eventIDs The event IDs to include.
     * @param range    The Interval to show.
     */
    public ViewInTimelineRequestedEvent(Set<Long> eventIDs, Interval range) {
        this.eventIDs = eventIDs;
        this.range = range;
    }

    /**
     * Get the event IDs.
     *
     * @return The event IDs
     */
    public Set<Long> getEventIDs() {
        return eventIDs;
    }

    /**
     * Get the Interval.
     *
     * @return The Interval.
     */
    public Interval getInterval() {
        return range;
    }
}
