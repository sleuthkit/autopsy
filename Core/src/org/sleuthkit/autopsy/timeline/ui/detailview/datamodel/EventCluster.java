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
package org.sleuthkit.autopsy.timeline.ui.detailview.datamodel;

import com.google.common.collect.Sets;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.TimelineEvent;
import com.google.common.collect.ImmutableSortedSet;
import static com.google.common.collect.Sets.union;

/**
 * Represents a set of other events clustered together. All the sub events
 * should have the same type and matching descriptions at the designated "zoom
 * level", and be "close together" in time.
 */
public class EventCluster implements MultiEvent<EventStripe> {

    final private EventStripe parent;

    /**
     * the smallest time interval containing all the clustered events
     */
    final private Interval span;

    /**
     * the type of all the clustered events
     */
    final private EventType type;

    /**
     * the common description of all the clustered events
     */
    final private String description;

    /**
     * the description level of detail that the events were clustered at.
     */
    private final DescriptionLoD lod;

    /**
     * the set of ids of the clustered events
     */
    final private Set<Long> eventIDs;

    /**
     * the ids of the subset of clustered events that have at least one tag
     * applied to them
     */
    private final Set<Long> tagged;

    /**
     * the ids of the subset of clustered events that have at least one hash set
     * hit
     */
    private final Set<Long> hashHits;

    /**
     * merge two event clusters into one new event cluster.
     *
     * @param cluster1
     * @param cluster2
     *
     * @return a new event cluster that is the result of merging the given
     *         events clusters
     */
    public static EventCluster merge(EventCluster cluster1, EventCluster cluster2) {

        if (cluster1.getEventType() != cluster2.getEventType()) {
            throw new IllegalArgumentException("event clusters are not compatible: they have different types");
        }

        if (!cluster1.getDescription().equals(cluster2.getDescription())) {
            throw new IllegalArgumentException("event clusters are not compatible: they have different descriptions");
        }

        Interval spanningInterval = IntervalUtils.span(cluster1.span, cluster2.span);
        
        Set<Long> idsUnion = union(cluster1.getEventIDs(), cluster2.getEventIDs()).immutableCopy();
        Set<Long> hashHitsUnion = union(cluster1.getEventIDsWithHashHits(), cluster2.getEventIDsWithHashHits()).immutableCopy();
        Set<Long> taggedUnion = union(cluster1.getEventIDsWithTags(), cluster2.getEventIDsWithTags()).immutableCopy();

//        Set<Long> idsUnion = Sets.union(cluster1.getEventIDs(), cluster2.getEventIDs());
//        Set<Long> hashHitsUnion = Sets.union(cluster1.getEventIDsWithHashHits(), cluster2.getEventIDsWithHashHits());
//        Set<Long> taggedUnion = Sets.union(cluster1.getEventIDsWithTags(), cluster2.getEventIDsWithTags());

        return new EventCluster(spanningInterval,
                cluster1.getEventType(), idsUnion, hashHitsUnion, taggedUnion,
                cluster1.getDescription(), cluster1.lod);
    }

    private EventCluster(Interval spanningInterval, EventType type, Set<Long> eventIDs,
                         Set<Long> hashHits, Set<Long> tagged, String description, DescriptionLoD lod,
                         EventStripe parent) {

        this.span = spanningInterval;

        this.type = type;
        this.hashHits = hashHits;
        this.tagged = tagged;
        this.description = description;
        this.eventIDs = eventIDs;
        this.lod = lod;
        this.parent = parent;
    }

    public EventCluster(Interval spanningInterval, EventType type, Set<Long> eventIDs,
                        Set<Long> hashHits, Set<Long> tagged, String description, DescriptionLoD lod) {
        this(spanningInterval, type, eventIDs, hashHits, tagged, description, lod, null);
    }

    public EventCluster(TimelineEvent event, EventType type, DescriptionLoD lod) {
        this(new Interval(event.getStartMillis(), event.getEndMillis()),
                type,
                singleton(event.getEventID()),
                event.isHashHit() ? singleton(event.getEventID()) : emptySet(),
                event.isTagged() ? singleton(event.getEventID()) : emptySet(),
                event.getDescription(lod),
                lod);

    }

    /**
     * get the EventStripe (if any) that contains this cluster
     *
     * @return an Optional containg the parent stripe of this cluster, or is
     *         empty if the cluster has no parent set.
     */
    @Override
    public Optional<EventStripe> getParent() {
        return Optional.ofNullable(parent);
    }

    /**
     * get the EventStripe (if any) that contains this cluster
     *
     * @return an Optional containg the parent stripe of this cluster, or is
     *         empty if the cluster has no parent set.
     */
    @Override
    public Optional<EventStripe> getParentStripe() {
        //since this clusters parent must be an event stripe just delegate to getParent();
        return getParent();
    }

    public Interval getSpan() {
        return span;
    }

    @Override
    public long getStartMillis() {
        return span.getStartMillis();
    }

    @Override
    public long getEndMillis() {
        return span.getEndMillis();
    }

    @Override
    public Set<Long> getEventIDs() {
        return eventIDs;
    }

    @Override
    public Set<Long> getEventIDsWithHashHits() {
        return hashHits;
    }

    @Override
    public Set<Long> getEventIDsWithTags() {
        return tagged;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public EventType getEventType() {
        return type;
    }

    @Override
    public DescriptionLoD getDescriptionLoD() {
        return lod;
    }

    /**
     * return a new EventCluster identical to this one, except with the given
     * EventBundle as the parent.
     *
     * @param parent
     *
     * @return a new EventCluster identical to this one, except with the given
     *         EventBundle as the parent.
     */
    public EventCluster withParent(EventStripe parent) {

        return new EventCluster(span, type, eventIDs, hashHits, tagged, description, lod, parent);
    }

    @Override
    public SortedSet<EventCluster> getClusters() {
//        return DetailsViewModel.copyAsSortedSet(singleton(this), Comparator.comparing(cluster -> true));
        return ImmutableSortedSet.orderedBy(Comparator.comparing(EventCluster::getStartMillis)).add(this).build();
    }

    @Override
    public String toString() {
        return "EventCluster{" + "description=" + description + ", eventIDs=" + eventIDs.size() + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.type);
        hash = 23 * hash + Objects.hashCode(this.description);
        hash = 23 * hash + Objects.hashCode(this.lod);
        hash = 23 * hash + Objects.hashCode(this.eventIDs);
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
        final EventCluster other = (EventCluster) obj;
        if (!Objects.equals(this.description, other.description)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (this.lod != other.lod) {
            return false;
        }
        return Objects.equals(this.eventIDs, other.eventIDs);
    }
}
