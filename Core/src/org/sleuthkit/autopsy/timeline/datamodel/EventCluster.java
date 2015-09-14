/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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

import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;

/**
 * Represents a set of other (TimeLineEvent) events aggregated together. All the
 * sub events should have the same type and matching descriptions at the
 * designated 'zoom level'.
 */
@Immutable
public class EventCluster implements EventBundle {

    /**
     * the smallest time interval containing all the aggregated events
     */
    final private Interval span;

    /**
     * the type of all the aggregted events
     */
    final private EventType type;

    /**
     * the common description of all the aggregated events
     */
    final private String description;

    /**
     * the description level of detail that the events were aggregated at.
     */
    private final DescriptionLOD lod;

    /**
     * the set of ids of the aggregated events
     */
    final private Set<Long> eventIDs;

    /**
     * the ids of the subset of aggregated events that have at least one tag
     * applied to them
     */
    private final Set<Long> tagged;

    /**
     * the ids of the subset of aggregated events that have at least one hash
     * set hit
     */
    private final Set<Long> hashHits;

    public EventCluster(Interval spanningInterval, EventType type, Set<Long> eventIDs, Set<Long> hashHits, Set<Long> tagged, String description, DescriptionLOD lod) {

        this.span = spanningInterval;
        this.type = type;
        this.hashHits = hashHits;
        this.tagged = tagged;
        this.description = description;
        this.eventIDs = eventIDs;
        this.lod = lod;
    }

    /**
     * @return the actual interval from the first event to the last event
     */
    public Interval getSpan() {
        return span;
    }

    public long getStartMillis() {
        return span.getStartMillis();
    }

    public long getEndMillis() {
        return span.getEndMillis();
    }

    public Set<Long> getEventIDs() {
        return Collections.unmodifiableSet(eventIDs);
    }

    public Set<Long> getEventIDsWithHashHits() {
        return Collections.unmodifiableSet(hashHits);
    }

    public Set<Long> getEventIDsWithTags() {
        return Collections.unmodifiableSet(tagged);
    }

    public String getDescription() {
        return description;
    }

    public EventType getEventType() {
        return type;
    }

    @Override
    public DescriptionLOD getDescriptionLOD() {
        return lod;
    }

    /**
     * merge two aggregate events into one new aggregate event.
     *
     * @param cluster1
     * @param aggEVent2
     *
     * @return a new aggregate event that is the result of merging the given
     *         events
     */
    public static EventCluster merge(EventCluster cluster1, EventCluster cluster2) {

        if (cluster1.getEventType() != cluster2.getEventType()) {
            throw new IllegalArgumentException("aggregate events are not compatible they have different types");
        }

        if (!cluster1.getDescription().equals(cluster2.getDescription())) {
            throw new IllegalArgumentException("aggregate events are not compatible they have different descriptions");
        }
        Sets.SetView<Long> idsUnion = Sets.union(cluster1.getEventIDs(), cluster2.getEventIDs());
        Sets.SetView<Long> hashHitsUnion = Sets.union(cluster1.getEventIDsWithHashHits(), cluster2.getEventIDsWithHashHits());
        Sets.SetView<Long> taggedUnion = Sets.union(cluster1.getEventIDsWithTags(), cluster2.getEventIDsWithTags());

        return new EventCluster(IntervalUtils.span(cluster1.span, cluster2.span), cluster1.getEventType(), idsUnion, hashHitsUnion, taggedUnion, cluster1.getDescription(), cluster1.lod);
    }

    Range<Long> getRange() {
        if (getEndMillis() > getStartMillis()) {
            return Range.closedOpen(getSpan().getStartMillis(), getSpan().getEndMillis());
        } else {
            return Range.singleton(getStartMillis());
        }
    }

    @Override
    public Iterable<Range<Long>> getRanges() {
        return Collections.singletonList(getRange());
    }

}
