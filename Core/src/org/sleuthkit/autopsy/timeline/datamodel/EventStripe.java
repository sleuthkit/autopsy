/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedSet;
import javax.annotation.concurrent.Immutable;
import org.python.google.common.base.Objects;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 * A 'collection' of {@link EventCluster}s, all having the same type,
 * description, and zoom levels, but not necessarily close together in time.
 */
@Immutable
public final class EventStripe implements EventBundle<EventCluster> {

    public static EventStripe merge(EventStripe u, EventStripe v) {
        Preconditions.checkNotNull(u);
        Preconditions.checkNotNull(v);
        Preconditions.checkArgument(Objects.equal(u.description, v.description));
        Preconditions.checkArgument(Objects.equal(u.lod, v.lod));
        Preconditions.checkArgument(Objects.equal(u.type, v.type));
        Preconditions.checkArgument(Objects.equal(u.parent, v.parent));
        return new EventStripe(u, v);
    }

    private final EventCluster parent;

    private final ImmutableSortedSet<EventCluster> clusters;

    /**
     * the type of all the events
     */
    private final EventType type;

    /**
     * the common description of all the events
     */
    private final String description;

    /**
     * the description level of detail that the events were clustered at.
     */
    private final DescriptionLoD lod;

    /**
     * the set of ids of the events
     */
    private final ImmutableSet<Long> eventIDs;

    /**
     * the ids of the subset of events that have at least one tag applied to
     * them
     */
    private final ImmutableSet<Long> tagged;

    /**
     * the ids of the subset of events that have at least one hash set hit
     */
    private final ImmutableSet<Long> hashHits;

    public EventStripe withParent(EventCluster parent) {
        EventStripe eventStripe = new EventStripe(parent, this.type, this.description, this.lod, clusters, eventIDs, tagged, hashHits);
        return eventStripe;
    }

    private EventStripe(EventCluster parent, EventType type, String description, DescriptionLoD lod, SortedSet<EventCluster> clusters, ImmutableSet<Long> eventIDs, ImmutableSet<Long> tagged, ImmutableSet<Long> hashHits) {
        this.parent = parent;
        this.type = type;
        this.description = description;
        this.lod = lod;
        this.clusters = ImmutableSortedSet.copyOf(Comparator.comparing(EventCluster::getStartMillis), clusters);

        this.eventIDs = eventIDs;
        this.tagged = tagged;
        this.hashHits = hashHits;
    }

    public EventStripe(EventCluster cluster, EventCluster parent) {
        this.clusters = ImmutableSortedSet.orderedBy(Comparator.comparing(EventCluster::getStartMillis))
                .add(cluster).build();

        type = cluster.getEventType();
        description = cluster.getDescription();
        lod = cluster.getDescriptionLoD();
        eventIDs = cluster.getEventIDs();
        tagged = cluster.getEventIDsWithTags();
        hashHits = cluster.getEventIDsWithHashHits();
        this.parent = parent;
    }

    private EventStripe(EventStripe u, EventStripe v) {
        clusters = ImmutableSortedSet.orderedBy(Comparator.comparing(EventCluster::getStartMillis))
                .addAll(u.getClusters())
                .addAll(v.getClusters())
                .build();

        type = u.getEventType();
        description = u.getDescription();
        lod = u.getDescriptionLoD();
        eventIDs = ImmutableSet.<Long>builder()
                .addAll(u.getEventIDs())
                .addAll(v.getEventIDs())
                .build();
        tagged = ImmutableSet.<Long>builder()
                .addAll(u.getEventIDsWithTags())
                .addAll(v.getEventIDsWithTags())
                .build();
        hashHits = ImmutableSet.<Long>builder()
                .addAll(u.getEventIDsWithHashHits())
                .addAll(v.getEventIDsWithHashHits())
                .build();
        parent = u.getParentBundle().orElse(v.getParentBundle().orElse(null));
    }

    @Override
    public Optional<EventCluster> getParentBundle() {
        return Optional.ofNullable(parent);
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

    @Override
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ImmutableSet<Long> getEventIDs() {
        return eventIDs;
    }

    @Override
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ImmutableSet<Long> getEventIDsWithHashHits() {
        return hashHits;
    }

    @Override
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ImmutableSet<Long> getEventIDsWithTags() {
        return tagged;
    }

    @Override
    public long getStartMillis() {
        return clusters.first().getStartMillis();
    }

    @Override
    public long getEndMillis() {
        return clusters.last().getEndMillis();
    }

    @Override
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ImmutableSortedSet< EventCluster> getClusters() {
        return clusters;
    }

    @Override
    public String toString() {
        return "EventStripe{" + "description=" + description + ", eventIDs=" + eventIDs.size() + '}'; //NON-NLS
    }
}
