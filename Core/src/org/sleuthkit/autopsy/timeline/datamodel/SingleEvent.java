/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.datamodel.TskData;

/**
 * A single event.
 */
@Immutable
public class SingleEvent implements TimeLineEvent {

    private final long eventID;
    private final long fileID;
    private final Long artifactID;
    private final long dataSourceID;

    private final long time;
    private final EventType subType;
    private final ImmutableMap<DescriptionLoD, String> descriptions;

    private final TskData.FileKnown known;
    private final boolean hashHit;
    private final boolean tagged;

    public SingleEvent(long eventID, long dataSourceID, long objID, @Nullable Long artifactID, long time, EventType type, String fullDescription, String medDescription, String shortDescription, TskData.FileKnown known, boolean hashHit, boolean tagged) {
        this.eventID = eventID;
        this.fileID = objID;
        this.artifactID = artifactID == 0 ? null : artifactID;
        this.time = time;
        this.subType = type;
        descriptions = ImmutableMap.<DescriptionLoD, String>of(DescriptionLoD.FULL, fullDescription,
                DescriptionLoD.MEDIUM, medDescription,
                DescriptionLoD.SHORT, shortDescription);

        this.known = known;
        this.hashHit = hashHit;
        this.tagged = tagged;
        this.dataSourceID = dataSourceID;
    }

    public boolean isTagged() {
        return tagged;
    }

    public boolean isHashHit() {
        return hashHit;
    }

    @Nullable
    public Long getArtifactID() {
        return artifactID;
    }

    public long getEventID() {
        return eventID;
    }

    public long getFileID() {
        return fileID;
    }

    /**
     * @return the time in seconds from unix epoch
     */
    public long getTime() {
        return time;
    }

    public EventType getEventType() {
        return subType;
    }

    public String getFullDescription() {
        return getDescription(DescriptionLoD.FULL);
    }

    public String getMedDescription() {
        return getDescription(DescriptionLoD.MEDIUM);
    }

    public String getShortDescription() {
        return getDescription(DescriptionLoD.SHORT);
    }

    public TskData.FileKnown getKnown() {
        return known;
    }

    public String getDescription(DescriptionLoD lod) {
        return descriptions.get(lod);
    }

    public long getDataSourceID() {
        return dataSourceID;
    }

    @Override
    public Set<Long> getEventIDs() {
        return Collections.singleton(eventID);
    }

    @Override
    public Set<Long> getEventIDsWithHashHits() {
        return isHashHit() ? Collections.singleton(eventID) : Collections.emptySet();
    }

    @Override
    public Set<Long> getEventIDsWithTags() {
        return isTagged() ? Collections.singleton(eventID) : Collections.emptySet();
    }

    @Override
    public long getEndMillis() {
        return time * 1000;
    }

    @Override
    public long getStartMillis() {
        return time * 1000;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + (int) (this.eventID ^ (this.eventID >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SingleEvent other = (SingleEvent) obj;
        if (this.eventID != other.eventID) {
            return false;
        }
        return true;
    }

    @Override
    public SortedSet<EventCluster> getClusters() {
        EventCluster eventCluster = new EventCluster(new Interval(time * 1000, time * 1000), subType, getEventIDs(), getEventIDsWithHashHits(), getEventIDsWithTags(), getFullDescription(), DescriptionLoD.FULL);
        return ImmutableSortedSet.orderedBy(Comparator.comparing(EventCluster::getStartMillis)).add(eventCluster).build();
    }

    @Override
    public String getDescription() {
        return getFullDescription();
    }

    @Override
    public DescriptionLoD getDescriptionLoD() {
        return DescriptionLoD.FULL;
    }
}
