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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A Route represents a TSK_GPS_ROUTE artifact which has a start and end point
 * however the class was written with the assumption that some routes may have
 * more that two points.
 *
 */
@Messages({
    "Route_Label=As-the-crow-flies Route",
    "Route_Start_Label=Start",
    "Route_End_Label=End"
})
public final class Route {

    private final List<Waypoint> points;
    private final Long timestamp;
    private final Double altitude;

    // This list is not expected to change after construction so the 
    // constructor will take care of creating an unmodifiable List
    private final List<Waypoint.Property> immutablePropertiesList;

    /**
     * Gets the list of Routes from the TSK_GPS_ROUTE artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Route objects, empty list will be returned if no Routes
     *         where found
     *
     * @throws GeoLocationDataException
     */
    static public List<Route> getRoutes(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = null;
        try {
            artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE);
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get artifacts for type: TSK_GPS_BOOKMARK", ex);
        }

        List<Route> routes = new ArrayList<>();
        for (BlackboardArtifact artifact : artifacts) {
            Route route = new Route(artifact);
            routes.add(route);
        }
        return routes;
    }

    /**
     * Construct a route for the given artifact.
     *
     * @param artifact TSK_GPS_ROUTE artifact object
     */
    Route(BlackboardArtifact artifact) throws GeoLocationDataException {
        points = new ArrayList<>();

        Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap = ArtifactUtils.getAttributesFromArtifactAsMap(artifact);

        Waypoint point = getRouteStartPoint(attributeMap);

        if (point != null) {
            points.add(point);
        }

        point = getRouteEndPoint(attributeMap);

        if (point != null) {
            points.add(point);
        }

        BlackboardAttribute attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
        altitude = attribute != null ? attribute.getValueDouble() : null;

        attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
        timestamp = attribute != null ? attribute.getValueLong() : null;

        immutablePropertiesList = Collections.unmodifiableList(GeolocationUtils.createGeolocationProperties(attributeMap));
    }

    /**
     * Get the list of way points for this route;
     *
     * @return List an unmodifiableList of ArtifactWaypoints for this route
     */
    public List<Waypoint> getRoute() {
        return Collections.unmodifiableList(points);
    }

    /**
     * Get the timestamp for this Route
     *
     * @return The timestamp (epoch seconds) or null if none was set.
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the altitude for this route.
     *
     * @return The Double altitude value or null if none was set.
     */
    public Double getAltitude() {
        return altitude;
    }

    /**
     * Get the "Other attributes" for this route. The map will contain display
     * name, formatted value pairs. This list is unmodifiable.
     *
     * @return Map of key, value pairs.
     */
    public List<Waypoint.Property> getOtherProperties() {
        return immutablePropertiesList;
    }

    /**
     * Get the route label.
     *
     * This will return the original hard coded label from the KML report:
     * As-the-crow-flies Route
     */
    public String getLabel() {
        return Bundle.Route_Label();
    }

    /**
     * Get the route start point.
     *
     * @param attributeMap Map of artifact attributes for this waypoint
     *
     * @return Start RoutePoint 
     *
     * @throws GeoLocationDataException when longitude or latitude is null
     */
    private Waypoint getRouteStartPoint(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        BlackboardAttribute latitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START);
        BlackboardAttribute longitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START);

        if (latitude != null && longitude != null) {
            return new RoutePoint(this, latitude.getValueDouble(), longitude.getValueDouble(), Bundle.Route_Start_Label());
        } else {
            throw new GeoLocationDataException("Unable to create route start point, invalid longitude and/or latitude");
        }
    }

    /**
     * Get the route End point.
     *
     * @param attributeMap Map of artifact attributes for this waypoint
     *
     * @return End RoutePoint or null if valid longitude and latitude are not
     *         found
     *
     * @throws GeoLocationDataException when longitude or latitude is null
     */
    private Waypoint getRouteEndPoint(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        BlackboardAttribute latitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END);
        BlackboardAttribute longitude = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END);

        if (latitude != null && longitude != null) {
            return new RoutePoint(this, latitude.getValueDouble(), longitude.getValueDouble(), Bundle.Route_End_Label());
        }else {
            throw new GeoLocationDataException("Unable to create route end point, invalid longitude and/or latitude");
        }
    }
}
