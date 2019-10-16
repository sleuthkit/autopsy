/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation.datamodel;

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * 
 */
public class Route {
    private final BlackboardArtifact artifact;
    private final List<BlackboardArtifactPoint> waypoints;
    private long timestamp;
    private String details;
    private Double altitude = null;

    /**
     * Construct a route for the given artifact.
     *
     * @param artifact TSK_GPS_ROUTE artifact object
     */
    protected Route(BlackboardArtifact artifact) {
        this.artifact = artifact;
        waypoints = new ArrayList<>();
    }

    /**
     * Get the list of way points for this route;
     *
     * @return List of ArtifactWaypoints for this route
     */
    public List<BlackboardArtifactPoint> getRoute() {
        return waypoints;
    }
    
    public String getDetails() {
        return details;
    }
    
    public BlackboardArtifact getArtifact() {
        return artifact;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public Double getAltitude() {
        return altitude;
    }

    /**
     * Initialize the route.
     */
    protected void initRoute() throws TskCoreException {
        if (artifact == null) {
            return;
        }
        
        Double latitude;
        Double longitude;
        
        latitude = getDoubleAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START);
        longitude = getDoubleAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START);
        
        if (latitude != null && longitude != null) {
            waypoints.add(new RoutePoint(this, latitude, longitude, "Start"));
        }
        
        latitude = getDoubleAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END);
        longitude = getDoubleAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END);
        
        if (latitude != null && longitude != null) {
            waypoints.add(new RoutePoint(this, latitude, longitude, "End"));
        }

        altitude = getDoubleAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);

        // Get the creation date
        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME));
        Long dateTime = attribute != null ? attribute.getValueLong() : null;
        if (dateTime != null) {
            timestamp = dateTime * 1000;
        }
    }
    
    private Double getDoubleAttribute(BlackboardAttribute.ATTRIBUTE_TYPE type)  throws TskCoreException{
       BlackboardAttribute attribute;
        
       attribute = artifact.getAttribute(new BlackboardAttribute.Type(type));
       return attribute != null ? attribute.getValueDouble() : null; 
    }

}
