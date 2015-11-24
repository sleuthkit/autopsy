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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
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

    private final SortedSet<EventCluster> clusters = new TreeSet<>(Comparator.comparing(EventCluster::getStartMillis));

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
    private final Set<Long> eventIDs = new HashSet<>();

    /**
     * the ids of the subset of events that have at least one tag applied to
     * them
     */
    private final Set<Long> tagged = new HashSet<>();

    /**
     * the ids of the subset of events that have at least one hash set hit
     */
    private final Set<Long> hashHits = new HashSet<>();

    public EventStripe withParent(EventCluster parent) {
        EventStripe eventStripe = new EventStripe(parent, this.type, this.description, this.lod);
        eventStripe.clusters.addAll(clusters);
        eventStripe.eventIDs.addAll(eventIDs);
        eventStripe.tagged.addAll(tagged);
        eventStripe.hashHits.addAll(hashHits);
        return eventStripe;
    }

    private EventStripe(EventCluster parent, EventType type, String description, DescriptionLoD lod) {
        this.parent = parent;
        this.type = type;
        this.description = description;
        this.lod = lod;
    }

    public EventStripe(EventCluster cluster, EventCluster parent) {
        clusters.add(cluster);

        type = cluster.getEventType();
        description = cluster.getDescription();
        lod = cluster.getDescriptionLoD();
        eventIDs.addAll(cluster.getEventIDs());
        tagged.addAll(cluster.getEventIDsWithTags());
        hashHits.addAll(cluster.getEventIDsWithHashHits());
        this.parent = parent;
    }

    private EventStripe(EventStripe u, EventStripe v) {
        clusters.addAll(u.clusters);
        clusters.addAll(v.clusters);
        type = u.getEventType();
        description = u.getDescription();
        lod = u.getDescriptionLoD();
        eventIDs.addAll(u.getEventIDs());
        eventIDs.addAll(v.getEventIDs());
        tagged.addAll(u.getEventIDsWithTags());
        tagged.addAll(v.getEventIDsWithTags());
        hashHits.addAll(u.getEventIDsWithHashHits());
        hashHits.addAll(v.getEventIDsWithHashHits());
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
    public Set<Long> getEventIDs() {
        return Collections.unmodifiableSet(eventIDs);
    }

    @Override
    public Set<Long> getEventIDsWithHashHits() {
        return Collections.unmodifiableSet(hashHits);
    }

    @Override
    public Set<Long> getEventIDsWithTags() {
        return Collections.unmodifiableSet(tagged);
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
    public SortedSet< EventCluster> getClusters() {
        return Collections.unmodifiableSortedSet(clusters);
    }

    @Override
    public String toString() {
        return "EventStripe{" + "description=" + description + ", eventIDs=" + eventIDs.size() + '}';
    }
}
