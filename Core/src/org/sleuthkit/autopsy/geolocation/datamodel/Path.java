/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class representing a series of waypoints that form a path.
 */
public class Path {
    
    private final List<Waypoint> path;
    private final String pathName;
    private final BlackboardArtifact artifact;

    /**
     * Gets the list of Routes from the TSK_GPS_ROUTE artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Route objects, empty list will be returned if no Routes
     *         were found
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
     * Gets the list of Routes from the TSK_GPS_TRACK artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     * @param sourceList    List of source to return tracks from, maybe null to
     *                      return tracks from all sources
     *
     * @return List of Route objects, empty list will be returned if no Routes
     *         were found
     *
     * @throws GeoLocationDataException
     */
    static public List<Track> getTracks(SleuthkitCase skCase, List<? extends Content> sourceList) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = null;
         List<Track> tracks = new ArrayList<>();
        try {
            artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACK);
        for (BlackboardArtifact artifact : artifacts) {
            if(sourceList == null || sourceList.contains(artifact.getDataSource())){
                Track route = new Track(artifact);
                tracks.add(route);
            }
        }
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get artifacts for type: TSK_GPS_BOOKMARK", ex);
        }
        return tracks;
    }
   
    /**
     * Path constructor.
     * 
     * @param artifact BlackboardARtifact that this path represents, required
     * @param pathName Name for this path, maybe null or empty string.
     */
    Path(BlackboardArtifact artifact, String pathName) {
        this.path = new ArrayList<>();
        this.pathName = pathName;
        this.artifact = artifact;
    }
    
    /**
     * Adds a Waypoint to the path.
     * 
     * @param point 
     */
    final void addToPath(Waypoint point) {
        path.add(point);
    }
    
    /**
     * Get the list of way points for this route;
     *
     * @return List an unmodifiableList of ArtifactWaypoints for this route
     */
    final public List<Waypoint> getPath() {
        return Collections.unmodifiableList(path);
    }
    
    /**
     * Returns the BlackboardARtifact that this path represents.
     * 
     * @return 
     */
    final BlackboardArtifact getArtifact() {
        return artifact;
    }
    
    /**
     * Returns the label\display name for this path.
     * 
     * @return Path label, empty string
     */
    public String getLabel() {
        return pathName != null ? pathName : "";
    }
}
