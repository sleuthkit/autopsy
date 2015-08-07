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

import org.sleuthkit.autopsy.timeline.events.type.EventType;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
public class TimeLineEvent {

    private final Long eventID;

    private final Long fileID;

    private final Long time;

    private final Long artifactID;

    private final EventType subType;

    private final String fullDescription, medDescription, shortDescription;

    private final TskData.FileKnown known;

    private final boolean hashHit;

    public TimeLineEvent(Long eventID, Long objID, Long artifactID, Long time, EventType type, String fullDescription, String medDescription, String shortDescription, TskData.FileKnown known, boolean hashHit) {
        this.eventID = eventID;
        this.fileID = objID;
        this.artifactID = artifactID;
        this.time = time;
        this.subType = type;

        this.fullDescription = fullDescription;
        this.medDescription = medDescription;
        this.shortDescription = shortDescription;
        this.known = known;
        this.hashHit = hashHit;
    }

    public boolean isHashHit() {
        return hashHit;
    }

    public Long getArtifactID() {
        return artifactID;
    }

    public Long getEventID() {
        return eventID;
    }

    public Long getFileID() {
        return fileID;
    }

    /**
     * @return the time in seconds from unix epoch
     */
    public Long getTime() {
        return time;
    }

    public EventType getType() {
        return subType;
    }

    public String getFullDescription() {
        return fullDescription;
    }

    public String getMedDescription() {
        return medDescription;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public TskData.FileKnown getKnown() {
        return known;
    }
}
