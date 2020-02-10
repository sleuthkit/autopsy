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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * A Route represents a TSK_GPS_ROUTE artifact which has a start and end point
 * however the class was written with the assumption that routes may have
 * more that two points.
 *
 */
public class Route extends GeoPath{
    private final Long timestamp;

    // This list is not expected to change after construction so the 
    // constructor will take care of creating an unmodifiable List
    private final List<Waypoint.Property> propertiesList;

    /**
     * Construct a route for the given artifact.
     *
     * @param artifact TSK_GPS_ROUTE artifact object
     */
    @Messages({
        // This is the original static hardcoded label from the 
        // original kml-report code
        "Route_Label=As-the-crow-flies Route"
    })
    Route(BlackboardArtifact artifact) throws GeoLocationDataException {
        super(artifact, Bundle.Route_Label());
        
        Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap = Waypoint.getAttributesFromArtifactAsMap(artifact);
        
        addToPath(getRouteStartPoint(artifact, attributeMap));
        addToPath(getRouteEndPoint(artifact, attributeMap));
             
        BlackboardAttribute attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
        timestamp = attribute != null ? attribute.getValueLong() : null;

        propertiesList = Waypoint.createGeolocationProperties(attributeMap);
    }

    /**
     * Get the list of way points for this route;
     *
     * @return List an unmodifiableList of ArtifactWaypoints for this route
     */
    public List<Waypoint> getRoute() {
        return getPath();
    }

    /**
     * Get the "Other attributes" for this route. The map will contain display
     * name, formatted value pairs. This list is unmodifiable.
     *
     * @return Map of key, value pairs.
     */
    public List<Waypoint.Property> getOtherProperties() {
        return Collections.unmodifiableList(propertiesList);
    }

    public Long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get the route start point.
     *
     * @param artifact
     * @param attributeMap Map of artifact attributes for this waypoint.
     * 
     * An exception will be thrown if longitude or latitude is null.
     *
     * @return Start waypoint 
     *
     * @throws GeoLocationDataException.
     */
    @Messages({
        "Route_Start_Label=Start"
    })

    private Waypoint getRouteStartPoint(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        BlackboardAttribute latitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START);
        BlackboardAttribute longitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START);
        BlackboardAttribute altitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
        BlackboardAttribute pointTimestamp = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);

        if (latitude != null && longitude != null) {
            return new Waypoint(artifact,  
                    Bundle.Route_Start_Label(), 
                    pointTimestamp != null ? pointTimestamp.getValueLong() : null, 
                    latitude.getValueDouble(), 
                    longitude.getValueDouble(),
                    altitude != null ? altitude.getValueDouble() : null,
                    null, attributeMap, this);
        } else {
            throw new GeoLocationDataException("Unable to create route start point, invalid longitude and/or latitude");
        }
    }

    /**
     * Get the route End point.
     * 
     *  An exception will be thrown if longitude or latitude is null.
     *
     * @param attributeMap Map of artifact attributes for this waypoint
     *
     * @return The end waypoint
     *
     * @throws GeoLocationDataException
     */
    @Messages({
        "Route_End_Label=End"
    })
    private Waypoint getRouteEndPoint(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        BlackboardAttribute latitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END);
        BlackboardAttribute longitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END);
        BlackboardAttribute altitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
        BlackboardAttribute pointTimestamp = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);

        if (latitude != null && longitude != null) {

            return new Waypoint(artifact,  
                    Bundle.Route_End_Label(), 
                    pointTimestamp != null ? pointTimestamp.getValueLong() : null, 
                    latitude.getValueDouble(), 
                    longitude.getValueDouble(),
                    altitude != null ? altitude.getValueDouble() : null,
                    null, attributeMap, this);
        } else {
            throw new GeoLocationDataException("Unable to create route end point, invalid longitude and/or latitude");
        }
    }
}
