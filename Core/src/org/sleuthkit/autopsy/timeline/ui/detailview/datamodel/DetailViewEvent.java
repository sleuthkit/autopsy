/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * An event of the timeline. Concrete implementations may represent single
 * events or multiple events grouped together based on some common properties
 * (for example close together in time and or having similar descriptions or
 * event types). Note that for SingleEvents or events that are all simultaneous,
 * the start time may be equal to the end time.
 */
public interface DetailViewEvent {

    /**
     * Get a description of this event. Implementations may choose what level of
     * description to provide.
     *
     * @return A description of this event.
     */
    public String getDescription();

    /**
     * Get the Description level of detail at which all single events of this
     * event have the same description, ie, what level of detail was used to
     * group these events.
     *
     * @return the description level of detail of the given events
     */
    public TimelineLevelOfDetail getDescriptionLevel();

    /**
     * get the EventStripe (if any) that contains this event.
     *
     * @return an Optional containing the parent stripe of this event, or is
     *         empty if the event has no parent stripe.
     */
    public Optional<EventStripe> getParentStripe();

    /**
     * Get the id(s) of this event as a set.
     *
     * @return a Set containing the event id(s) of this event.
     */
    Set<Long> getEventIDs();

    /**
     * Get the id(s) of this event that have hash hits associated with them.
     *
     * @return a Set containing the event id(s) of this event that have hash
     *         hits associated with them.
     */
    Set<Long> getEventIDsWithHashHits();

    /**
     * Get the id(s) of this event that have tags associated with them.
     *
     * @return a Set containing the event id(s) of this event that have tags
     *         associated with them.
     */
    Set<Long> getEventIDsWithTags();

    /**
     * Get the EventType of this event.
     *
     * @return the EventType of this event.
     */
    TimelineEventType getEventType();

    /**
     * Get the start time of this event as milliseconds from the Unix Epoch.
     *
     * @return the start time of this event as milliseconds from the Unix Epoch.
     */
    long getEndMillis();

    /**
     * Get the end time of this event as milliseconds from the Unix Epoch.
     *
     * @return the end time of this event as milliseconds from the Unix Epoch.
     */
    long getStartMillis();

    /**
     * Get the number of SingleEvents this event contains.
     *
     * @return the number of SingleEvents this event contains.
     */
    default int getSize() {
        return getEventIDs().size();
    }

    /**
     * Get the EventClusters that make up this event. May be null for
     * SingleEvents, or return a refernece to this event if it is an
     * EventCluster
     *
     * @return The EventClusters that make up this event.
     */
    SortedSet<EventCluster> getClusters();

    static class StartComparator implements Comparator<DetailViewEvent> {

        @Override
        public int compare(DetailViewEvent o1, DetailViewEvent o2) {
            return Long.compare(o1.getStartMillis(), o2.getStartMillis());
        }
    }
}
