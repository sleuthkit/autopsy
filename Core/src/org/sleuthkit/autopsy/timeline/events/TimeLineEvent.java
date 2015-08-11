/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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

import javax.annotation.Nullable;
import org.python.google.common.collect.ImmutableMap;
import org.python.google.common.collect.Maps;
import org.sleuthkit.autopsy.timeline.events.type.EventType;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
public class TimeLineEvent {

    private final long eventID;
    private final long fileID;
    private final Long artifactID;
    private final long dataSourceID;

    private final long time;
    private final EventType subType;
    private final ImmutableMap<DescriptionLOD, String> descriptions;

    private final TskData.FileKnown known;
    private final boolean hashHit;
    private final boolean tagged;

    public TimeLineEvent(long eventID, long dataSourceID, long objID, @Nullable Long artifactID, long time, EventType type, String fullDescription, String medDescription, String shortDescription, TskData.FileKnown known, boolean hashHit, boolean tagged) {
        this.eventID = eventID;
        this.fileID = objID;
        this.artifactID = artifactID;
        this.time = time;
        this.subType = type;
        descriptions = Maps.immutableEnumMap(ImmutableMap.<DescriptionLOD, String>of(
                DescriptionLOD.FULL, fullDescription,
                DescriptionLOD.MEDIUM, medDescription,
                DescriptionLOD.SHORT, shortDescription));

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

    public EventType getType() {
        return subType;
    }

    public String getFullDescription() {
        return getDescription(DescriptionLOD.FULL);
    }

    public String getMedDescription() {
        return getDescription(DescriptionLOD.MEDIUM);
    }

    public String getShortDescription() {
        return getDescription(DescriptionLOD.SHORT);
    }

    public TskData.FileKnown getKnown() {
        return known;
    }

    public String getDescription(DescriptionLOD lod) {
        return descriptions.get(lod);
    }

    public long getDataSourceID() {
        return dataSourceID;
    }
}
