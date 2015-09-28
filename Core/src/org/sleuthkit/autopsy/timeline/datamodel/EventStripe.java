/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.datamodel;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeMap;
import com.google.common.collect.TreeRangeSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.python.google.common.base.Objects;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;

/**
 *
 */
@Immutable
public final class EventStripe implements EventBundle {

    final private EventBundle parent;

    @Override
    public Optional<EventBundle> getParentBundle() {
        return Optional.ofNullable(parent);
    }

    private final RangeSet<Long> spans = TreeRangeSet.create();
    private final RangeMap<Long, EventCluster> spanMap = TreeRangeMap.create();

    /**
     * the type of all the aggregted events
     */
    private final EventType type;

    /**
     * the common description of all the aggregated events
     */
    private final String description;

    /**
     * the description level of detail that the events were aggregated at.
     */
    private final DescriptionLOD lod;

    /**
     * the set of ids of the aggregated events
     */
    private final Set<Long> eventIDs = new HashSet<>();

    /**
     * the ids of the subset of aggregated events that have at least one tag
     * applied to them
     */
    private final Set<Long> tagged = new HashSet<>();

    /**
     * the ids of the subset of aggregated events that have at least one hash
     * set hit
     */
    private final Set<Long> hashHits = new HashSet<>();

    public EventStripe(EventCluster cluster) {
        spans.add(cluster.getRange());
        spanMap.put(cluster.getRange(), cluster);
        type = cluster.getEventType();
        description = cluster.getDescription();
        lod = cluster.getDescriptionLOD();
        eventIDs.addAll(cluster.getEventIDs());
        tagged.addAll(cluster.getEventIDsWithTags());
        hashHits.addAll(cluster.getEventIDsWithHashHits());
        parent = cluster.getParentBundle().orElse(null);
    }

    private EventStripe(EventStripe u, EventStripe v) {
        spans.addAll(u.spans);
        spans.addAll(v.spans);
        spanMap.putAll(u.spanMap);
        spanMap.putAll(v.spanMap);
        type = u.getEventType();
        description = u.getDescription();
        lod = u.getDescriptionLOD();
        eventIDs.addAll(u.getEventIDs());
        eventIDs.addAll(v.getEventIDs());
        tagged.addAll(u.getEventIDsWithTags());
        tagged.addAll(v.getEventIDsWithTags());
        hashHits.addAll(u.getEventIDsWithHashHits());
        hashHits.addAll(v.getEventIDsWithHashHits());
        parent = u.getParentBundle().orElse(null);
    }

    public static EventStripe merge(EventStripe u, EventStripe v) {
        Preconditions.checkNotNull(u);
        Preconditions.checkNotNull(v);
        Preconditions.checkArgument(Objects.equal(u.description, v.description));
        Preconditions.checkArgument(Objects.equal(u.lod, v.lod));
        Preconditions.checkArgument(Objects.equal(u.type, v.type));
        Preconditions.checkArgument(Objects.equal(u.parent, v.parent));
        return new EventStripe(u, v);
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
    public DescriptionLOD getDescriptionLOD() {
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
        return spans.span().lowerEndpoint();
    }

    @Override
    public long getEndMillis() {
        return spans.span().upperEndpoint();
    }

    @Override
    public Iterable<Range<Long>> getRanges() {
        return spans.asRanges();
    }
}
