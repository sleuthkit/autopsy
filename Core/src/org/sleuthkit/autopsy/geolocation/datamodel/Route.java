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
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;

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
        List<BlackboardArtifact> artifacts = ArtifactUtils.getArtifactsForType(skCase, BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE);
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
        Waypoint point = getRouteStartPoint(artifact);

        if (point != null) {
            points.add(point);
        }

        point = getRouteEndPoint(artifact);

        if (point != null) {
            points.add(point);
        }

        altitude = getRouteAltitude(artifact);
        timestamp = getRouteTimestamp(artifact);
        immutablePropertiesList = Collections.unmodifiableList(GeolocationUtils.getOtherGeolocationProperties(artifact));
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
     * @param artifact The BlackboardARtifact object from which this route is
     *                 created
     *
     * @return Start RoutePoint or null if valid longitude and latitude are not
     *         found
     *
     * @throws GeoLocationDataException
     */
    private Waypoint getRouteStartPoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        Double latitude;
        Double longitude;
        RoutePoint point = null;

        latitude = ArtifactUtils.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START);
        longitude = ArtifactUtils.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START);

        if (latitude != null && longitude != null) {
            point = new RoutePoint(this, latitude, longitude, Bundle.Route_Start_Label());
        }

        return point;
    }

    /**
     * Get the route End point.
     *
     * @param artifact The BlackboardARtifact object from which this route is
     *                 created
     *
     * @return End RoutePoint or null if valid longitude and latitude are not
     *         found
     *
     * @throws GeoLocationDataException
     */
    private Waypoint getRouteEndPoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        Double latitude;
        Double longitude;
        RoutePoint point = null;

        latitude = ArtifactUtils.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END);
        longitude = ArtifactUtils.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END);

        if (latitude != null && longitude != null) {
            point = new RoutePoint(this, latitude, longitude, Bundle.Route_End_Label());
        }

        return point;
    }

    /**
     * Get the Altitude for this route.
     *
     * @param artifact The BlackboardARtifact object from which this route is
     *                 created
     *
     * @return The Altitude, or null if none was found
     *
     * @throws GeoLocationDataException
     */
    private Double getRouteAltitude(BlackboardArtifact artifact) throws GeoLocationDataException {
        return ArtifactUtils.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
    }

    /**
     * Get the timestamp for this route.
     *
     * @param artifact The BlackboardARtifact object from which this route is
     *                 created
     *
     * @return The timestamp attribute, or null if none was found
     *
     * @throws GeoLocationDataException
     */
    private Long getRouteTimestamp(BlackboardArtifact artifact) throws GeoLocationDataException {
        return ArtifactUtils.getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
    }

}
