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
package org.sleuthkit.autopsy.geolocation;

import org.jxmapviewer.viewer.GeoPosition;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Basic parent class for representing a Blackboard artifact waypoint.
 * 
 */
class DefaultArtifactWaypoint implements ArtifactWaypoint{
    private final BlackboardArtifact artifact;
    private String label;
    private long timestamp;
    private GeoPosition position;
    
    /**
     * Construct a default way point object.
     * 
     * @param artifact The artifact that the waypoint is for.
     */
    DefaultArtifactWaypoint(BlackboardArtifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public String getLabel() {
        return label;
    }
    
    void setLabel(String label) {
        this.label = label;
    }

    @Override
    public BlackboardArtifact getArtifact() {
        return artifact;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public GeoPosition getPosition() {
        return position;
    }
    
    /**
     * Set the GeoPosition for the waypoint
     * 
     * @param position GeoPosition for the waypoint
     */
    void setPosition(GeoPosition position) {
        this.position = position;
    }
    
    /**
     * Create and set the GeoPosition for the way point.
     * 
     * @param latitude double latitude value
     * @param longitude double logitude value
     */
    void setPosition(double latitude, double longitude) {
        position = new GeoPosition(latitude, longitude);
    }
}
