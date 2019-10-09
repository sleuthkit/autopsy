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

import java.util.ArrayList;
import java.util.List;
import org.jxmapviewer.viewer.GeoPosition;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * A route is a set of Geolocations with a common parent artifact;
 *
 */
class Route {

    private final BlackboardArtifact artifact;
    private final List<ArtifactWaypoint> waypoints;
    private long timestamp;

    /**
     * Construct a route for the given artifact.
     *
     * @param artifact TSK_GPS_ROUTE artifact object
     */
    Route(BlackboardArtifact artifact) {
        this.artifact = artifact;
        waypoints = new ArrayList<>();
        initRoute();
    }

    /**
     * Construct a route with the given artifact and list of way points.
     *
     * @param artifact  TSK_GPS_ROUTE artifact object
     * @param waypoints List of waypoints for this route
     */
    Route(BlackboardArtifact artifact, List<ArtifactWaypoint> waypoints) {
        this.artifact = artifact;
        this.waypoints = waypoints;
    }

    /**
     * Add a way point to the route.
     *
     * @param point Waypoint to add to the route.
     */
    void addWaypoint(ArtifactWaypoint point) {
        waypoints.add(point);
    }

    /**
     * Get the list of way points for this route;
     *
     * @return List of ArtifactWaypoints for this route
     */
    List<ArtifactWaypoint> getRoute() {
        return waypoints;
    }

    /**
     * Initialize the route.
     */
    private void initRoute() {
        if (artifact == null) {
            return;
        }

        // Get the start logitude and latitude
        Double latitude = GeolocationUtilities.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START);
        Double longitude = GeolocationUtilities.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START);

        if (latitude != null && longitude != null) {
            addWaypoint(new RouteWaypoint(this, latitude, longitude, "Start"));
        }

        // Get the end logitude and latitude
        latitude = GeolocationUtilities.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END);
        longitude = GeolocationUtilities.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END);

        if (latitude != null && longitude != null) {
            addWaypoint(new RouteWaypoint(this, latitude, longitude, "End"));
        }

        // Get the creation date
        Long dateTime = GeolocationUtilities.getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
        if (dateTime != null) {
            timestamp = dateTime * 1000;
        }
    }

    /**
     * Waypoint object for routes.
     */
    class RouteWaypoint implements ArtifactWaypoint {

        private final Route parent;
        private final GeoPosition position;
        private final String label;

        /**
         * Construct a route way point.
         * 
         * @param parent The parent route object.
         * @param latitude Latitude for waypoint
         * @param longitude Longitude for waypoint
         * @param label Way point label.
         */
        RouteWaypoint(Route parent, double latitude, double longitude, String label) {
            this.parent = parent;
            this.position = new GeoPosition(latitude, longitude);
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }

        @Override
        public BlackboardArtifact getArtifact() {
            return parent.artifact;
        }

        @Override
        public long getTimestamp() {
            return parent.timestamp;
        }

        @Override
        public GeoPosition getPosition() {
            return position;
        }
    }
}
