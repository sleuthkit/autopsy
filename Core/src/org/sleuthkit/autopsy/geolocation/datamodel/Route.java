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
import java.util.Map;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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
public class Route {

    private final List<Waypoint> points;
    private final Long timestamp;
    private final Double altitude;
    private final Map<String, String> otherAttributesMap;

    /**
     * Construct a route for the given artifact.
     *
     * @param artifact TSK_GPS_ROUTE artifact object
     */
    protected Route(BlackboardArtifact artifact) throws TskCoreException {
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
        otherAttributesMap = initalizeOtherAttributes(artifact);
    }

    /**
     * Get the list of way points for this route;
     *
     * @return List of ArtifactWaypoints for this route
     */
    public List<Waypoint> getRoute() {
        return points;
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
     * name, formatted value pairs.
     *
     * @return Map of key, value pairs.
     */
    public Map<String, String> getOtherProperties() {
        return otherAttributesMap;
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
     * @throws TskCoreException
     */
    private Waypoint getRouteStartPoint(BlackboardArtifact artifact) throws TskCoreException {
        Double latitude;
        Double longitude;
        BlackboardAttribute attribute;
        RoutePoint point = null;

        attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START));
        latitude = attribute != null ? attribute.getValueDouble() : null;

        attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START));
        longitude = attribute != null ? attribute.getValueDouble() : null;

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
     * @throws TskCoreException
     */
    private Waypoint getRouteEndPoint(BlackboardArtifact artifact) throws TskCoreException {
        Double latitude;
        Double longitude;
        BlackboardAttribute attribute;
        RoutePoint point = null;

        attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END));
        latitude = attribute != null ? attribute.getValueDouble() : null;

        attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END));
        longitude = attribute != null ? attribute.getValueDouble() : null;

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
     * @throws TskCoreException
     */
    private Double getRouteAltitude(BlackboardArtifact artifact) throws TskCoreException {
        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE));
        return attribute != null ? attribute.getValueDouble() : null;
    }

    /**
     * Get the timestamp for this route.
     *
     * @param artifact The BlackboardARtifact object from which this route is
     *                 created
     *
     * @return The timestamp attribute, or null if none was found
     *
     * @throws TskCoreException
     */
    private Long getRouteTimestamp(BlackboardArtifact artifact) throws TskCoreException {
        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME));
        return attribute != null ? attribute.getValueLong() : null;
    }

    /**
     * Retrieve the "Other attributes" for this route. The map will contain
     * display name, formatted value pairs.
     *
     * @param artifact The BlackboardARtifact object from which this route is
     *                 created
     *
     * @return A Map of other attributes for this route.
     *
     * @throws TskCoreException
     */
    private Map<String, String> initalizeOtherAttributes(BlackboardArtifact artifact) throws TskCoreException {
        return GeolocationUtility.getOtherGeolocationAttributes(artifact);
    }
}
