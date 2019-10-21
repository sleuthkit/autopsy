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
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Static functions for the creations of geolocation points for artifacts.
 *
 */
public class GeolocationManager {

    /**
     * Add a private constructor to silence codacy warning about making this
     * class a utility class as I suspect this class may grow when filtering is
     * added.
     */
    private GeolocationManager() {

    }

    /**
     * Returns a list of Waypoints for the artifacts with geolocation
     * information.
     *
     * List will include artifacts of type: TSK_GPS_TRACKPOINT TSK_GPS_SEARCH
     * TSK_GPS_LAST_KNOWN_LOCATION TSK_GPS_BOOKMARK TSK_METADATA_EXIF
     *
     * @param skCase        Currently open SleuthkitCase
     * @param includeRoutes True to include the points at are in TSK_GPS_ROUTE
     *                      objects
     *
     * @return List of BlackboardArtifactPoints
     *
     * @throws TskCoreException
     */
    static public List<Waypoint> getWaypoints(SleuthkitCase skCase) throws TskCoreException {
        List<Waypoint> points = new ArrayList<>();

        points.addAll(getBasicPoints(skCase));

        return points;
    }

    /**
     * Gets the list of Routes from the TSK_GPS_ROUTE artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Route objects, empty list will be returned if no Routes
     *         where found
     *
     * @throws TskCoreException
     */
    static public List<Route> getGPSRoutes(SleuthkitCase skCase) throws TskCoreException {
        List<Route> routes = new ArrayList<>();
        List<BlackboardArtifact> artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE);
        for (BlackboardArtifact artifact : artifacts) {
            Route route = new Route(artifact);
            routes.add(route);
        }
        return routes;
    }

    /**
     * Get a list of Waypoints for the GPS artifacts. Artifacts that will be
     * included: TSK_GPS_TRACKPOINT TSK_GPS_SEARCH TSK_GPS_LAST_KNOWN_LOCATION
     * TSK_GPS_BOOKMARK
     *
     * Waypoint objects will be created and added to the list only for artifacts
     * with TSK_GEO_LONGITUDE and TSK_LATITUDE attributes.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of BlackboardArtifactPoints for above artifacts or empty
     *         list if none where found.
     *
     * @throws TskCoreException
     */
    static private List<Waypoint> getBasicPoints(SleuthkitCase skCase) throws TskCoreException {

        List<Waypoint> points = new ArrayList<>();

        List<BlackboardArtifact> artifacts = new ArrayList<>();
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT));
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH));
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION));
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK));
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));

        for (BlackboardArtifact artifact : artifacts) {
            BlackboardArtifactWaypoint point = new BlackboardArtifactWaypoint(artifact);
            // Only add to the list if the point has a valid latitude 
            // and longitude. 
            if (point.getLatitude() != null && point.getLongitude() != null) {
                points.add(point);
            }
        }

        return points;
    }
}
