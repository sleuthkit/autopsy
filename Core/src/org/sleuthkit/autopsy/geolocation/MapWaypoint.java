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

import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import org.jxmapviewer.viewer.GeoPosition;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.actionhelpers.ExtractActionHelper;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.geolocation.datamodel.Route;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.geolocation.datamodel.Waypoint;
import org.sleuthkit.autopsy.geolocation.datamodel.WaypointBuilder;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A Wrapper for the datamodel Waypoint class that implements the jxmapviewer
 * Waypoint interface.
 *
 */
final class MapWaypoint extends KdTree.XYZPoint implements org.jxmapviewer.viewer.Waypoint {

    private static final Logger logger = Logger.getLogger(MapWaypoint.class.getName());
    private final static String HTML_PROP_FORMAT = "<b>%s: </b>%s<br>";
    static private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US);

    private final Waypoint dataModelWaypoint;
    private final GeoPosition position;

    /**
     * Returns a list of waypoints for the currently open case.
     *
     * @param skCase Current case
     *
     * @return list of waypoints, list will be empty if no waypoints were found
     *
     * @throws GeoLocationDataException
     */
    static List<MapWaypoint> getWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<Waypoint> points = WaypointBuilder.getAllWaypoints(skCase);

        List<Route> routes = Route.getRoutes(skCase);
        for (Route route : routes) {
            points.addAll(route.getRoute());
        }

        List<MapWaypoint> mapPoints = new ArrayList<>();

        for (Waypoint point : points) {
            mapPoints.add(new MapWaypoint(point));
        }

        return mapPoints;
    }
    
    /**
     * Returns a list of of MapWaypoint objects for the given list of 
     * datamodel.Waypoint objects.
     * 
     * @param dmWaypoints
     * 
     * @return List of MapWaypoint objects.  List will be empty if dmWaypoints was
     * empty or null.
     */
    static List<MapWaypoint> getWaypoints(List<Waypoint> dmWaypoints) {
        List<MapWaypoint> mapPoints = new ArrayList<>();

        if (dmWaypoints != null) {
        
            for (Waypoint point : dmWaypoints) {
                mapPoints.add(new MapWaypoint(point));
            }
        }

        return mapPoints;
    }

    /**
     * Returns a MapWaypoint without a reference to the datamodel waypoint.
     *
     * @param position Location for new waypoint
     *
     * @return New MapWaypoint with dataModelWaypoint set to null
     */
    static MapWaypoint getDummyWaypoint(GeoPosition position) {
        return new MapWaypoint(position);
    }

    /**
     * Private constructor for MapWaypoint.
     *
     * @param dataModelWaypoint datamodel.Waypoint to wrap
     */
    private MapWaypoint(Waypoint dataModelWaypoint) {
        super(dataModelWaypoint.getLatitude(), dataModelWaypoint.getLongitude());
        this.dataModelWaypoint = dataModelWaypoint;
        position = new GeoPosition(dataModelWaypoint.getLatitude(), dataModelWaypoint.getLongitude());
    }

    /**
     * Construct a waypoint from a GeoPosition with a null datamodel.Waypoint.
     *
     * @param position GeoPosition for the waypoint
     */
    private MapWaypoint(GeoPosition position) {
        super(position.getLatitude(), position.getLongitude());
        dataModelWaypoint = null;
        this.position = position;
    }

    /**
     * Returns the waypoint image or null of one is not currently set.
     *
     * @return the image for this waypoint
     */
    ImageIcon getImage() {
        if (dataModelWaypoint.getImage() != null && ImageUtils.isImageThumbnailSupported(dataModelWaypoint.getImage())) {
            BufferedImage buffImage = ImageUtils.getThumbnail(dataModelWaypoint.getImage(), 150);
            return new ImageIcon(buffImage);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeoPosition getPosition() {
        return position;
    }

    /**
     * Returns the display label for the waypoint.
     *
     * @return Waypoint label
     */
    String getLabel() {
        return dataModelWaypoint.getLabel();
    }

    /**
     * Returns the details of this waypoint formatted as HTML.
     *
     * @return HTML formatted string
     */
    String getHTMLFormattedWaypointDetails() {
        return getFormattedDetails(dataModelWaypoint);
    }

    /**
     * Returns a list of JMenuItems for the waypoint. The list list may contain
     * nulls which should be removed or replaced with JSeparators.
     *
     * @return List of menu items
     *
     * @throws TskCoreException
     */
    JMenuItem[] getMenuItems() throws TskCoreException {
        List<JMenuItem> menuItems = new ArrayList<>();
        BlackboardArtifact artifact = dataModelWaypoint.getArtifact();
        Content content = artifact.getSleuthkitCase().getContentById(artifact.getObjectID());

        menuItems.addAll(getTimelineMenuItems(dataModelWaypoint.getArtifact()));
        menuItems.addAll(getDataModelActionFactoryMenuItems(artifact, content));
        menuItems.add(DeleteFileContentTagAction.getInstance().getMenuForFiles(Arrays.asList((AbstractFile) content)));
        menuItems.add(DeleteFileBlackboardArtifactTagAction.getInstance().getMenuForArtifacts(Arrays.asList(artifact)));

        return menuItems.toArray(new JMenuItem[0]);
    }

    /**
     * Gets the Timeline Menu Items for this artifact.
     *
     * @param artifact Selected artifact
     *
     * @return List of timeline menu items.
     */
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

    /**
     * Use the DateModelActionsFactory to get some of the basic actions for the
     * waypoint. The advantage to using the DataModelActionsFactory is that the
     * menu items can be put in a consistent order with other parts of the UI.
     *
     * @param artifact Artifact for the selected waypoint
     * @param content  Artifact content
     *
     * @return List of JMenuItems for the DataModelActionFactory actions
     */
    @Messages({
        "MayWaypoint_ExternalViewer_label=Open in ExternalViewer"
    })
    private List<JMenuItem> getDataModelActionFactoryMenuItems(BlackboardArtifact artifact, Content content) {
        List<JMenuItem> menuItems = new ArrayList<>();

        List<Action> actions = DataModelActionsFactory.getActions(content, true);
        for (Action action : actions) {
            if (action == null) {
                menuItems.add(null);
            } else if (action instanceof ExportCSVAction) {
                // Do nothing we don't need this menu item.
            } else if (action instanceof AddContentTagAction) {
                menuItems.add(((AddContentTagAction) action).getMenuForContent(Arrays.asList((AbstractFile) content)));
            } else if (action instanceof AddBlackboardArtifactTagAction) {
                menuItems.add(((AddBlackboardArtifactTagAction) action).getMenuForContent(Arrays.asList(artifact)));
            } else if (action instanceof ExternalViewerShortcutAction) {
                // Replace with an ExternalViewerAction
                ExternalViewerAction newAction = new ExternalViewerAction(Bundle.MayWaypoint_ExternalViewer_label(), new FileNode((AbstractFile) content));
                menuItems.add(new JMenuItem(newAction));
            } else if (action instanceof ExtractAction) {
                menuItems.add(new JMenuItem(new WaypointExtractAction((AbstractFile) content)));
            } else {
                menuItems.add(new JMenuItem(action));
            }
        }
        return menuItems;
    }
    
     /**
     * Get the nicely formatted details for the given waypoint.
     * 
     * @param point Waypoint object
     * @param header String details header
     * 
     * @return HTML formatted String of details for given waypoint 
     */
    private String getFormattedDetails(Waypoint point) {
        StringBuilder result = new StringBuilder(); //NON-NLS
        
        result.append("<html>").append(formatAttribute("Name", point.getLabel()));
       
        Long timestamp = point.getTimestamp();
        if (timestamp != null) {
            result.append(formatAttribute("Timestamp", getTimeStamp(timestamp)));
        }

        result.append(formatAttribute("Latitude", point.getLatitude().toString()))
                .append(formatAttribute("Longitude", point.getLongitude().toString()));
       
        if (point.getAltitude() != null) {
            result.append(formatAttribute("Altitude", point.getAltitude().toString()));
        }

        List<Waypoint.Property> list = point.getOtherProperties();
        for(Waypoint.Property prop: list) {
            String value = prop.getValue();
            if(value != null && !value.isEmpty()) {
                result.append(formatAttribute(prop.getDisplayName(), value));
            }
        }
        
        result.append("</html>");

        return result.toString();
    }

    /**
     * Format a title value pair.
     * 
     * @param title Title of the property
     * @param value Value of the property
     * 
     * @return Formatted string with the title and value 
     */
    private String formatAttribute(String title, String value) {
        return String.format(HTML_PROP_FORMAT, title, value);
    }
    
    /**
     * Format a point time stamp (in seconds) to the report format.
     *
     * @param timeStamp The timestamp in epoch seconds.
     *
     * @return The formatted timestamp
     */
    private String getTimeStamp(long timeStamp) {
        return DATE_FORMAT.format(new java.util.Date(timeStamp * 1000));
    }


    /**
     * An action class for Extracting artifact files.
     */
    @Messages({
        "WaypointExtractAction_label=Extract Files(s)"
    })
    final class WaypointExtractAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        final private AbstractFile file;

        WaypointExtractAction(AbstractFile file) {
            super(Bundle.WaypointExtractAction_label());
            this.file = file;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ExtractActionHelper helper = new ExtractActionHelper();
            helper.extract(e, Arrays.asList(file));

        }
    }
}
