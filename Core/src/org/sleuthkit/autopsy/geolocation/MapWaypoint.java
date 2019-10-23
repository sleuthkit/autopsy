/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.geolocation;

import java.util.ArrayList;
import java.util.List;
import org.jxmapviewer.viewer.GeoPosition;
import org.sleuthkit.autopsy.geolocation.datamodel.Route;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.geolocation.datamodel.Waypoint;

/**
 *
 * 
 */
final class MapWaypoint implements org.jxmapviewer.viewer.Waypoint{

    private final Waypoint dataModelWaypoint;
    private final GeoPosition position;
    
    private MapWaypoint(Waypoint dataModelWaypoint) {
        this.dataModelWaypoint = dataModelWaypoint;
        position = new GeoPosition(dataModelWaypoint.getLatitude(), dataModelWaypoint.getLongitude());
    }
    
    static List<org.jxmapviewer.viewer.Waypoint> getWaypoints(SleuthkitCase skCase) throws TskCoreException{
        List<Waypoint> points = Waypoint.getAllWaypoints(skCase);
        
        List<Route> routes = Route.getRoutes(skCase);
        for(Route route: routes) {
            points.addAll(route.getRoute());
        }
        
        List<org.jxmapviewer.viewer.Waypoint> mapPoints = new ArrayList<>();
        
        for(Waypoint point: points) {
            mapPoints.add(new MapWaypoint(point));
        }
        
        return mapPoints;
    }
    
    @Override
    public GeoPosition getPosition() {
        return position;
    }
    
    String getDisplayName() {
        return dataModelWaypoint.getLabel();
    }
    
}
