/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import org.sleuthkit.datamodel.timeline.DescriptionLoD;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * A 'collection' of {@link EventCluster}s, all having the same type,
 * description, and zoom levels, but not necessarily close together in time.
 */
public final class EventStripe implements MultiEvent<EventCluster> {

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

	public static EventStripe merge(EventStripe u, EventStripe v) { //NOPMD
		Preconditions.checkNotNull(u);
		Preconditions.checkNotNull(v);
		Preconditions.checkArgument(Objects.equals(u.description, v.description));
		Preconditions.checkArgument(Objects.equals(u.lod, v.lod));
		Preconditions.checkArgument(Objects.equals(u.type, v.type));
		Preconditions.checkArgument(Objects.equals(u.parent, v.parent));
		return new EventStripe(u, v);
	}

	public EventStripe withParent(EventCluster parent) {
		if (Objects.nonNull(this.parent)) {
			throw new IllegalStateException("Event Stripe already has a parent!");
		}
		return new EventStripe(parent, this.type, this.description, this.lod, clusters, eventIDs, tagged, hashHits);
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

	public EventStripe(EventCluster cluster) {
		this.clusters = ImmutableSortedSet.orderedBy(Comparator.comparing(EventCluster::getStartMillis))
				.add(cluster.withParent(this)).build();

		type = cluster.getEventType();
		description = cluster.getDescription();
		lod = cluster.getDescriptionLoD();
		eventIDs = cluster.getEventIDs();
		tagged = cluster.getEventIDsWithTags();
		hashHits = cluster.getEventIDsWithHashHits();
		this.parent = null;
	}

	private EventStripe(EventStripe u, EventStripe v) { //NOPMD
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
		parent = u.getParent().orElse(v.getParent().orElse(null));
	}

	@Override
	public Optional<EventCluster> getParent() {
		return Optional.ofNullable(parent);
	}

	@Override
	public Optional<EventStripe> getParentStripe() {
		if (getParent().isPresent()) {
			return getParent().get().getParent();
		} else {
			return Optional.empty();
		}
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
	public ImmutableSet<Long> getEventIDs() {
		return eventIDs;
	}

	@Override
	public ImmutableSet<Long> getEventIDsWithHashHits() {
		return hashHits;
	}

	@Override
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
	public ImmutableSortedSet< EventCluster> getClusters() {
		return clusters;
	}

	@Override
	public String toString() {
		return "EventStripe{" + "description=" + description + ", eventIDs=" + (Objects.isNull(eventIDs) ? 0 : eventIDs.size()) + '}'; //NON-NLS
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 79 * hash + Objects.hashCode(this.clusters);
		hash = 79 * hash + Objects.hashCode(this.type);
		hash = 79 * hash + Objects.hashCode(this.description);
		hash = 79 * hash + Objects.hashCode(this.lod);
		hash = 79 * hash + Objects.hashCode(this.eventIDs);
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
		final EventStripe other = (EventStripe) obj;
		if (!Objects.equals(this.description, other.description)) {
			return false;
		}
		if (!Objects.equals(this.clusters, other.clusters)) {
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
