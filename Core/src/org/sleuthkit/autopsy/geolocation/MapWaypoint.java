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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.JMenuItem;
import org.jxmapviewer.viewer.GeoPosition;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.geolocation.datamodel.Route;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.geolocation.datamodel.ArtifactWaypoint;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A Wrapper for the datamodel Waypoint class that implements the jxmapviewer
 * Waypoint interfact for use in the map.
 *
 */
final class MapWaypoint extends KdTree.XYZPoint implements org.jxmapviewer.viewer.Waypoint {

    private static final Logger logger = Logger.getLogger(MapWaypoint.class.getName());

    private final ArtifactWaypoint dataModelWaypoint;
    private final GeoPosition position;

    /**
     * Private constructor for MapWaypoint
     *
     * @param dataModelWaypoint The datamodel waypoint to wrap
     */
    private MapWaypoint(ArtifactWaypoint dataModelWaypoint) {
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
    static List<MapWaypoint> getWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<ArtifactWaypoint> points = ArtifactWaypoint.getAllWaypoints(skCase);

        List<Route> routes = Route.getRoutes(skCase);
        for (Route route : routes) {
            points.addAll(route.getRoute());
        }

        List<MapWaypoint> mapPoints = new ArrayList<>();

        for (ArtifactWaypoint point : points) {
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

    /**
     * Returns a list of JMenuItems for this Waypoint. When creating a menu the 
     * list may contain nulls which should be removed or replaced with JSeparators
     * 
     * @return
     * @throws TskCoreException 
     */
    JMenuItem[] getMenuItems() throws TskCoreException{
        List<JMenuItem> menuItems = new ArrayList<>();
        BlackboardArtifact artifact = dataModelWaypoint.getArtifact();
        Content content = artifact.getSleuthkitCase().getContentById(artifact.getObjectID());

        menuItems.addAll(getTimelineMenuItems(dataModelWaypoint.getArtifact()));
        
        List<Action> actions = DataModelActionsFactory.getActions(content, true);
        for (Action action : actions) {
            if (action == null) {
                menuItems.add(null);
            } else if (action instanceof ExportCSVAction) {
                // Do nothing we don't need this menu item.
            } else if(action instanceof AddContentTagAction) {//|| action instanceof AddBlackboardArtifactTagAction) {
                menuItems.add(((AddContentTagAction)action).getMenuForContent(Arrays.asList((AbstractFile)content)));
               
            } else if(action instanceof AddBlackboardArtifactTagAction) {
                menuItems.add(((AddBlackboardArtifactTagAction)action).getMenuForContent(Arrays.asList(artifact)));
            } else if(action instanceof ExternalViewerShortcutAction) {
                // Replace with an ExternalViewerAction
                ExternalViewerAction newAction = new ExternalViewerAction("title", new FileNode((AbstractFile)content));
                menuItems.add(new JMenuItem(newAction));
            } else {
               menuItems.add(new JMenuItem(action));
            }
        }
        menuItems.add(DeleteFileContentTagAction.getInstance().getMenuForFiles(Arrays.asList((AbstractFile)content)));
        menuItems.add(DeleteFileBlackboardArtifactTagAction.getInstance().getMenuForArtifacts(Arrays.asList(artifact)));
        

        return menuItems.toArray(new JMenuItem[menuItems.size()]);
    }

    private List<JMenuItem> getTimelineMenuItems(BlackboardArtifact artifact) {
        List<JMenuItem> menuItems = new ArrayList<>();
        //if this artifact has a time stamp add the action to view it in the timeline
        try {
            if (ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact)) {
                menuItems.add(new JMenuItem(new ViewArtifactInTimelineAction(artifact)));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error getting arttribute(s) from blackboard artifact %d.", artifact.getArtifactID()), ex); //NON-NLS
        }

        return menuItems;
    }
}
