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
 *
 * 
 */
public class GeolocationManager {
    
    static public List<BlackboardArtifactPoint> getPoints(SleuthkitCase skCase, boolean includeRoute) throws TskCoreException {
        List<BlackboardArtifactPoint> points = new ArrayList<>();
        
        points.addAll(getSimplePoints(skCase));
        points.addAll(getEXIFPoints(skCase));
        
        if(includeRoute) {
            points.addAll(getGPSRouteWaypoints(skCase));
        }
        
        
        return points;
    }
    
    static public List<Route> getGPSRoutes(SleuthkitCase skCase) throws TskCoreException{
        List<Route> routes = new ArrayList<>();
        List<BlackboardArtifact> artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE);
         for (BlackboardArtifact artifact : artifacts) {
            Route route = new Route(artifact);
            route.initRoute();
            routes.add(route);
         }
        return routes;
    }

    /**
     *
     * @param skCase
     *
     * @return
     *
     * @throws TskCoreException
     */
    static private List<BlackboardArtifactPoint> getSimplePoints(SleuthkitCase skCase) throws TskCoreException {

        List<BlackboardArtifactPoint> points = new ArrayList<>();

        // TSK_GPS_TRACKPOINT, TSK_GPS_SEARCH, TSK_GPS_LAST_KNOWN_LOCATION 
        // and TSK_GPS_BOOKMARK have similar attributes and can be processed
        // similarly
        List<BlackboardArtifact> artifacts = new ArrayList<>();
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT));
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH));
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION));
        artifacts.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK));

        for (BlackboardArtifact artifact : artifacts) {
            BlackboardArtifactPoint point = new SimplePoint(artifact);
            point.initPosition();
            // Good point only if it has the location
            if(point.getLatitude() != null && point.getLongitude() != null) {
                points.add(point);
            }
        }

        return points;
    }

    /**
     *
     * @param skCase
     *
     * @return
     *
     * @throws TskCoreException
     */
    static private List<BlackboardArtifactPoint> getEXIFPoints(SleuthkitCase skCase) throws TskCoreException {
        List<BlackboardArtifactPoint> points = new ArrayList<>();
        List<BlackboardArtifact> artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF);
        for (BlackboardArtifact artifact : artifacts) {
            BlackboardArtifactPoint point = new EXIFMetadataPoint(artifact);

            point.initPosition();
            if(point.getLatitude() != null && point.getLongitude() != null) {
                points.add(point);
            }
        }

        return points;
    }
    
         /**
     *
     * @param skCase
     *
     * @return
     *
     * @throws TskCoreException
     */
    static private List<BlackboardArtifactPoint> getGPSRouteWaypoints(SleuthkitCase skCase) throws TskCoreException {
        List<BlackboardArtifactPoint> points = new ArrayList<>();

        for (Route route : getGPSRoutes(skCase)) {
            points.addAll(route.getRoute());
        }

        return points;
    }
    
}
