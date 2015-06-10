/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import com.google.common.collect.Collections2;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.events.type.EventType;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;

/** An event that represent a set of other events aggregated together. All the
 * sub events should have the same type and matching descriptions at the
 * designated 'zoom level'.
 */
@Immutable
public class AggregateEvent {

    final private Interval span;

    final private EventType type;

    final private Set<Long> eventIDs;

    final private String description;

    private final DescriptionLOD lod;

    public AggregateEvent(Interval spanningInterval, EventType type, Set<Long> eventIDs, String description, DescriptionLOD lod) {

        this.span = spanningInterval;
        this.type = type;
        this.description = description;

        this.eventIDs = eventIDs;
        this.lod = lod;
    }

    public AggregateEvent(Interval spanningInterval, EventType type, List<String> events, String description, DescriptionLOD lod) {

        this.span = spanningInterval;
        this.type = type;
        this.description = description;

        this.eventIDs = new HashSet<>(Collections2.transform(events, Long::valueOf));
        this.lod = lod;
    }

    /** @return the actual interval from the first event to the last event */
    public Interval getSpan() {
        return span;
    }

    public Set<Long> getEventIDs() {
        return Collections.unmodifiableSet(eventIDs);
    }

    public String getDescription() {
        return description;
    }

    public EventType getType() {
        return type;
    }

    /**
     * merge two aggregate events into one new aggregate event.
     *
     * @param ag1
     * @param ag2
     *
     * @return
     */
    public static AggregateEvent merge(AggregateEvent ag1, AggregateEvent ag2) {

        if (ag1.getType() != ag2.getType()) {
            throw new IllegalArgumentException("aggregate events are not compatible they have different types");
        }

        if (!ag1.getDescription().equals(ag2.getDescription())) {
            throw new IllegalArgumentException("aggregate events are not compatible they have different descriptions");
        }
        HashSet<Long> ids = new HashSet<>(ag1.getEventIDs());
        ids.addAll(ag2.getEventIDs());

        //TODO: check that types/descriptions are actually the same -jm
        return new AggregateEvent(IntervalUtils.span(ag1.span, ag2.span), ag1.getType(), ids, ag1.getDescription(), ag1.lod);
    }

    public DescriptionLOD getLOD() {
        return lod;
    }
}
