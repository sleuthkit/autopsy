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
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.geolocation.datamodel.Route;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.geolocation.datamodel.Waypoint;

/**
 * A Wrapper for the datamodel Waypoint class that implements the jxmapviewer
 * Waypoint interfact for use in the map.
 * 
 */
final class MapWaypoint extends KdTree.XYZPoint implements org.jxmapviewer.viewer.Waypoint{

    private final Waypoint dataModelWaypoint;
    private final GeoPosition position;
    
    /**
     * Private constructor for MapWaypoint
     * 
     * @param dataModelWaypoint The datamodel waypoint to wrap
     */
    private MapWaypoint(Waypoint dataModelWaypoint) {
        super(dataModelWaypoint.getLatitude(), dataModelWaypoint.getLongitude());
        this.dataModelWaypoint = dataModelWaypoint;
        position = new GeoPosition(dataModelWaypoint.getLatitude(), dataModelWaypoint.getLongitude());
    }
    
    private MapWaypoint(GeoPosition position) {
        super(position.getLatitude(), position.getLongitude());
        dataModelWaypoint = null;
        this.position = position;
    }
    
    /**
     * Gets a list of jxmapviewer waypoints from the current case.
     * 
     * @param skCase Current case
     * 
     * @return List of jxmapviewer waypoints
     * 
     * @throws GeoLocationDataException 
     */
    static List<MapWaypoint> getWaypoints(SleuthkitCase skCase) throws GeoLocationDataException{
        List<Waypoint> points = Waypoint.getAllWaypoints(skCase);
        
        List<Route> routes = Route.getRoutes(skCase);
        for(Route route: routes) {
            points.addAll(route.getRoute());
        }
        
        List<MapWaypoint> mapPoints = new ArrayList<>();
        
        for(Waypoint point: points) {
            mapPoints.add(new MapWaypoint(point));
        }
        
        return mapPoints;
    }
    
    static MapWaypoint getDummyWaypoint(GeoPosition position) {
        return new MapWaypoint(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeoPosition getPosition() {
        return position;
    }
    
    String getLabel() {
        return dataModelWaypoint.getLabel();
    }
}
