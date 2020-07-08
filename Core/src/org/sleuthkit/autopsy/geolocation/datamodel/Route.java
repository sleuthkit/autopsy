/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil.InvalidJsonException;
import org.sleuthkit.datamodel.blackboardutils.attributes.GeoWaypoints;

/**
 * A Route represents a TSK_GPS_ROUTE artifact which has a start and end point
 * however the class was written with the assumption that routes may have more
 * than two points.
 *
 */
public class Route extends GeoPath {

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

        BlackboardAttribute attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
        timestamp = attribute != null ? attribute.getValueLong() : null;

        propertiesList = Waypoint.createGeolocationProperties(attributeMap);
        
        createRoute(artifact, attributeMap);
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

    /**
     * Returns the route timestamp.
     *
     * @return Route timestamp
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the route waypoint attributes from the map and creates the list of
     * route waypoints.
     *
     * @param artifact     Route artifact
     * @param attributeMap Map of artifact attributes
     *
     * @throws GeoLocationDataException
     */
    @Messages({
        "Route_point_label=Waypoints for route"
    })
    private void createRoute(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        BlackboardAttribute attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_WAYPOINTS);

        String label = getLabel();
        if (label == null || label.isEmpty()) {
            label = Bundle.Route_point_label();
        } else {
            label = String.format("%s: %s", Bundle.Route_point_label(), label);
        }

        if (attribute != null) {
            GeoWaypoints waypoints;
            try {
                waypoints = BlackboardJsonAttrUtil.fromAttribute(attribute, GeoWaypoints.class);
            } catch (InvalidJsonException ex) {
                throw new GeoLocationDataException(String.format("Unable to parse waypoints in TSK_GEO_WAYPOINTS attribute (artifact object ID =%d)", artifact.getId()), ex);
            }
            for (GeoWaypoints.Waypoint waypoint : waypoints) {
                String name = waypoint.getName();
                Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> map = attributeMap;
                if(name != null && !name.isEmpty()) {
                    BlackboardAttribute pointNameAtt = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION, "", name);
                    map = new HashMap<>(attributeMap);
                    map.put(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION, pointNameAtt);
                }
                addToPath(new Waypoint(artifact, label, timestamp, waypoint.getLatitude(), waypoint.getLongitude(), waypoint.getAltitude(), null, map, this));
            }
        } else {
            Waypoint start = getRouteStartPoint(artifact, attributeMap);
            Waypoint end = getRouteEndPoint(artifact, attributeMap);

            addToPath(start);
            addToPath(end);
        }
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

        if (latitude != null && longitude != null) {
            return new Waypoint(artifact,
                    Bundle.Route_Start_Label(),
                    timestamp,
                    latitude.getValueDouble(),
                    longitude.getValueDouble(),
                    altitude != null ? altitude.getValueDouble() : null,
                    null,
                    attributeMap, this);
        } else {
            throw new GeoLocationDataException("Unable to create route start point, invalid longitude and/or latitude");
        }
    }

    /**
     * Get the route End point.
     *
     * An exception will be thrown if longitude or latitude is null.
     *
     * @param artifact
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

        if (latitude != null && longitude != null) {

            return new Waypoint(artifact,
                    Bundle.Route_End_Label(),
                    timestamp,
                    latitude.getValueDouble(),
                    longitude.getValueDouble(),
                    altitude != null ? altitude.getValueDouble() : null,
                    null,
                    attributeMap,
                    this);
        } else {
            throw new GeoLocationDataException("Unable to create route end point, invalid longitude and/or latitude");
        }
    }
}
