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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;
import javax.swing.event.MouseInputListener;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.VirtualEarthTileFactoryInfo;
import org.jxmapviewer.input.CenterMapListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.viewer.WaypointRenderer;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.datamodel.TskCoreException;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * The map panel. This panel contains the jxmapviewer MapViewer
 */
@SuppressWarnings("deprecation")
final public class MapPanel extends javax.swing.JPanel {

    static final String CURRENT_MOUSE_GEOPOSITION = "CURRENT_MOUSE_GEOPOSITION";

    private static final Logger logger = Logger.getLogger(MapPanel.class.getName());

    private static final long serialVersionUID = 1L;
    private static final Set<Integer> DOT_WAYPOINT_TYPES = new HashSet<>();
    private static final int DOT_SIZE = 12;
    private static final Set<Integer> VERY_SMALL_DOT_WAYPOINT_TYPES = new HashSet<>();
    private static final int VERY_SMALL_DOT_SIZE = 6;

    private boolean zoomChanging;
    private KdTree<MapWaypoint> waypointTree;
    private Set<MapWaypoint> waypointSet;
    private List<Set<MapWaypoint>> tracks = new ArrayList<>();
    private List<Set<MapWaypoint>> areas = new ArrayList<>();

    private Popup currentPopup;
    private final PopupFactory popupFactory;

    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 200;
    private static final int POPUP_MARGIN = 10;

    private BufferedImage whiteWaypointImage;
    private BufferedImage transparentWaypointImage;

    private MapWaypoint currentlySelectedWaypoint;
    private Set<MapWaypoint> currentlySelectedSet;

    static {
        DOT_WAYPOINT_TYPES.add(ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID());
        DOT_WAYPOINT_TYPES.add(ARTIFACT_TYPE.TSK_GPS_TRACK.getTypeID());
        DOT_WAYPOINT_TYPES.add(ARTIFACT_TYPE.TSK_GPS_ROUTE.getTypeID());
        VERY_SMALL_DOT_WAYPOINT_TYPES.add(ARTIFACT_TYPE.TSK_GPS_AREA.getTypeID());
    }

    /**
     * Creates new form MapPanel
     */
    @Messages({
        "MapPanel_connection_failure_message=Failed to connect to new geolocation map tile source.",
        "MapPanel_connection_failure_message_title=Connection Failure"
    })
    public MapPanel() {
        initComponents();

        zoomChanging = false;
        currentPopup = null;
        popupFactory = new PopupFactory();

        try {
            whiteWaypointImage = ImageIO.read(getClass().getResource("/org/sleuthkit/autopsy/images/waypoint_white.png"));
            transparentWaypointImage = ImageIO.read(getClass().getResource("/org/sleuthkit/autopsy/images/waypoint_transparent.png"));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to load geolocation waypoint images", ex);
        }

        // ComponentListeners do not have a concept of resize event "complete"
        // therefore if we move the popup as the window resizes there will be 
        // a weird blinking behavior.  Using the CompnentResizeEndListener the
        // popup will move to its corner one the resize is completed.
        this.addComponentListener(new ComponentResizeEndListener() {
            @Override
            public void resizeTimedOut() {
                if(currentPopup != null) {
                    showDetailsPopup();
                }
            }
        });

        UserPreferences.addChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                try {
                    // Tell the old factory to cleanup
                    mapViewer.getTileFactory().dispose();

                    mapViewer.setTileFactory(getTileFactory());
                    initializeZoomSlider();
                } catch (GeoLocationDataException ex) {
                    logger.log(Level.SEVERE, "Failed to connect to new geolocation tile server.", ex); //NON-NLS
                    JOptionPane.showMessageDialog(MapPanel.this,
                            Bundle.MapPanel_connection_failure_message(),
                            Bundle.MapPanel_connection_failure_message_title(),
                            JOptionPane.ERROR_MESSAGE);
                    MessageNotifyUtil.Notify.error(
                            Bundle.MapPanel_connection_failure_message_title(),
                            Bundle.MapPanel_connection_failure_message());
                }
            }
        });
    }

    /**
     * Get a list of the waypoints that are currently visible in the viewport.
     *
     * @return A list of waypoints or empty list if none were found.
     */
    List<MapWaypoint> getVisibleWaypoints() {

        Rectangle viewport = mapViewer.getViewportBounds();
        List<MapWaypoint> waypoints = new ArrayList<>();

        Iterator<MapWaypoint> iterator = waypointTree.iterator();
        while (iterator.hasNext()) {
            MapWaypoint waypoint = iterator.next();
            if (viewport.contains(mapViewer.getTileFactory().geoToPixel(waypoint.getPosition(), mapViewer.getZoom()))) {
                waypoints.add(waypoint);
            }
        }

        return waypoints;
    }

    /**
     * Initialize the map.
     */
    void initMap() throws GeoLocationDataException {

        TileFactory tileFactory = getTileFactory();
        mapViewer.setTileFactory(tileFactory);

        // Add Mouse interactions
        MouseInputListener mia = new MapPanMouseInputListener(mapViewer);
        mapViewer.addMouseListener(mia);
        mapViewer.addMouseMotionListener(mia);

        mapViewer.addMouseListener(new CenterMapListener(mapViewer));
        mapViewer.addMouseWheelListener(new ZoomMouseWheelListenerCursor(mapViewer));

        // Listen to the map for a change in zoom so that we can update the slider.
        mapViewer.addPropertyChangeListener("zoom", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                zoomSlider.setValue(mapViewer.getZoom());
            }
        });

        zoomSlider.setMinimum(tileFactory.getInfo().getMinimumZoomLevel());
        zoomSlider.setMaximum(tileFactory.getInfo().getMaximumZoomLevel());

        setZoom(tileFactory.getInfo().getMaximumZoomLevel() - 1);

        mapViewer.setCenterPosition(new GeoPosition(0, 0));

        initializePainter();
    }

    void initializePainter() {
        // Basic painters for the way points. 
        WaypointPainter<MapWaypoint> waypointPainter = new WaypointPainter<MapWaypoint>() {
            @Override
            public Set<MapWaypoint> getWaypoints() {
                if (currentlySelectedWaypoint != null) {
                    waypointSet.remove(currentlySelectedWaypoint);
                    waypointSet.add(currentlySelectedWaypoint);
                }
                return waypointSet;
            }
        };
        waypointPainter.setRenderer(new MapWaypointRenderer());

        ArrayList<Painter<JXMapViewer>> painters = new ArrayList<>();
        painters.add(new MapAreaRenderer(areas));
        painters.add(new MapTrackRenderer(tracks));
        painters.add(waypointPainter);

        CompoundPainter<JXMapViewer> compoundPainter = new CompoundPainter<>(painters);
        mapViewer.setOverlayPainter(compoundPainter);
    }

    /**
     * Setup the zoom slider based on the current tileFactory.
     */
    void initializeZoomSlider() {
        TileFactory tileFactory = mapViewer.getTileFactory();
        zoomSlider.setMinimum(tileFactory.getInfo().getMinimumZoomLevel());
        zoomSlider.setMaximum(tileFactory.getInfo().getMaximumZoomLevel());

        zoomSlider.repaint();
        zoomSlider.revalidate();
    }

    /**
     * Create the TileFactory object based on the user preference.
     *
     * @return
     */
    private TileFactory getTileFactory() throws GeoLocationDataException {
        switch (GeolocationSettingsPanel.GeolocationDataSourceType.getOptionForValue(UserPreferences.getGeolocationtTileOption())) {
            case ONLINE_USER_DEFINED_OSM_SERVER:
                return new DefaultTileFactory(createOnlineOSMFactory(UserPreferences.getGeolocationOsmServerAddress()));
            case OFFLINE_OSM_ZIP:
                return new DefaultTileFactory(createOSMZipFactory(UserPreferences.getGeolocationOsmZipPath()));
            case OFFILE_MBTILES_FILE:
                return new MBTilesTileFactory(UserPreferences.getGeolocationMBTilesFilePath());
            default:
                return new DefaultTileFactory(new VirtualEarthTileFactoryInfo(VirtualEarthTileFactoryInfo.MAP));
        }
    }

    /**
     * Create the TileFactoryInfo for an online OSM tile server.
     *
     * @param address Tile server address
     *
     * @return TileFactoryInfo object for server address.
     *
     * @throws GeoLocationDataException
     */
    private TileFactoryInfo createOnlineOSMFactory(String address) throws GeoLocationDataException {
        if (address.isEmpty()) {
            throw new GeoLocationDataException("Invalid user preference for OSM user define tile server. Address is an empty string.");
        } else {
            TileFactoryInfo info = new OSMTileFactoryInfo("User Defined Server", address);
            return info;
        }
    }

    /**
     * Create the TileFactoryInfo for OSM zip File
     *
     * @param path Path to zip file.
     *
     * @return TileFactoryInfo for zip file.
     *
     * @throws GeoLocationDataException
     */
    private TileFactoryInfo createOSMZipFactory(String path) throws GeoLocationDataException {
        if (path.isEmpty()) {
            throw new GeoLocationDataException("Invalid OSM tile Zip file. User preference value is empty string.");
        } else {
            File file = new File(path);
            if (!file.exists() || !file.canRead()) {
                throw new GeoLocationDataException("Invalid OSM tile zip file.  Unable to read file: " + path);
            }

            String zipPath = path.replaceAll("\\\\", "/");

            return new OSMTileFactoryInfo("ZIP archive", "jar:file:/" + zipPath + "!");  //NON-NLS
        }
    }

    /**
     * Stores the given List of MapWaypoint in a KdTree object.
     *
     * @param waypoints List of waypoints
     */
    void setWaypoints(Set<MapWaypoint> waypoints) {
        waypointTree = new KdTree<>();
        this.waypointSet = waypoints;
        for (MapWaypoint waypoint : waypoints) {
            waypointTree.add(waypoint);
        }
        mapViewer.repaint();
    }

    /**
     * Stores the given List of tracks from which to draw paths later
     *
     * @param tracks
     */
    void setTracks(List<Set<MapWaypoint>> tracks) {
        this.tracks = tracks;
    }
    
    /**
     * Stores the given list of areas from which to draw paths later
     *
     * @param areas
     */
    void setAreas(List<Set<MapWaypoint>> areas) {
        this.areas = areas;
    }    

    /**
     * Set the current zoom level.
     *
     * @param zoom
     */
    void setZoom(int zoom) {
        zoomChanging = true;
        mapViewer.setZoom(zoom);
        zoomSlider.setValue(zoom);
        zoomChanging = false;
    }

    /**
     * Remove all of the way points from the map.
     */
    void clearWaypoints() {
        waypointTree = null;
        currentlySelectedWaypoint = null;
        currentlySelectedSet = null;
        if (currentPopup != null) {
            currentPopup.hide();
        }
        mapViewer.repaint();
    }

    /**
     * Finds the waypoint nearest to the given and point and shows the popup
     * menu for that waypoint.
     *
     * @param point Current mouse click location
     */
    private void showPopupMenu(Point point) {
        try {
            List<MapWaypoint> waypoints = findClosestWaypoint(point);
            MapWaypoint waypoint = null;
            if (waypoints.size() > 0) {
                waypoint = waypoints.get(0);
            }
            showPopupMenu(waypoint, point);
            // Change the details popup to the currently selected point only if 
            // it the popup is currently visible
            if (waypoint != null && !waypoint.equals(currentlySelectedWaypoint)) {
                currentlySelectedWaypoint = waypoint;
                if (currentPopup != null) {
                    showDetailsPopup();
                }
                mapViewer.repaint();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to show popup for waypoint", ex);
        }
    }

    /**
     * Show the popup menu for the given waypoint and location.
     *
     * @param waypoint Selected waypoint
     * @param point    Current mouse click location
     */
    private void showPopupMenu(MapWaypoint waypoint, Point point) throws TskCoreException {
        if (waypoint == null) {
            return;
        }

        JMenuItem[] items = waypoint.getMenuItems();
        JPopupMenu popupMenu = new JPopupMenu();
        for (JMenuItem menu : items) {

            if (menu != null) {
                popupMenu.add(menu);
            } else {
                popupMenu.add(new JSeparator());
            }
        }
        popupMenu.show(mapViewer, point.x, point.y);
    }

    /**
     * Show the detailsPopup for the currently selected waypoint.
     */
    private void showDetailsPopup() {
        if (currentlySelectedWaypoint != null) {
            if (currentPopup != null) {
                currentPopup.hide();
            }

            WaypointDetailPanel detailPane = new WaypointDetailPanel();
            detailPane.setWaypoint(currentlySelectedWaypoint);
            detailPane.setPreferredSize(new Dimension(POPUP_WIDTH, POPUP_HEIGHT));
            detailPane.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (currentPopup != null) {
                        currentPopup.hide();
                        currentPopup = null;
                    }
                }

            });

            Point popupLocation = getLocationForDetailsPopup();

            currentPopup = popupFactory.getPopup(this, detailPane, popupLocation.x, popupLocation.y);
            currentPopup.show();

        } else {
            if (currentPopup != null) {
                currentPopup.hide();
            }
        }

        mapViewer.revalidate();
        mapViewer.repaint();
    }

    /**
     * Calculate the upper left corner on the screen for the details popup.
     *
     * @return Upper left corner location for the details popup.
     */
    private Point getLocationForDetailsPopup() {
        Point panelCorner = this.getLocationOnScreen();
        int width = this.getWidth();

        int popupX = panelCorner.x + width - POPUP_WIDTH - POPUP_MARGIN;
        int popupY = panelCorner.y + POPUP_MARGIN;

        return new Point(popupX, popupY);
    }

    /**
     * Find the waypoint that is closest to the given mouse click point.
     *
     * @param clickPoint The mouse click point
     *
     * @return A waypoint that is within 10 pixels of the given point, or null
     *         if none was found.
     */
    private List<MapWaypoint> findClosestWaypoint(Point clickPoint) {
        if (waypointTree == null) {
            return new ArrayList<>();
        }

        // Convert the mouse click location to latitude & longitude
        GeoPosition geopos = mapViewer.convertPointToGeoPosition(clickPoint);

        // Get the nearest neightbors to the point
        Collection<MapWaypoint> waypoints = waypointTree.nearestNeighbourSearch(1, MapWaypoint.getDummyWaypoint(geopos));

        if (waypoints == null || waypoints.isEmpty()) {
            return null;
        }

        Iterator<MapWaypoint> iterator = waypoints.iterator();

        // These may be the points closest to the lat/lon location that was
        // clicked, but that doesn't mean they are close in terms of pixles.
        List<MapWaypoint> closestPoints = new ArrayList<>();
        while (iterator.hasNext()) {
            MapWaypoint nextWaypoint = iterator.next();
            Point2D point = mapViewer.convertGeoPositionToPoint(nextWaypoint.getPosition());
            int pointX = (int) point.getX();
            int pointY = (int) point.getY();
            Rectangle rect;
            if (DOT_WAYPOINT_TYPES.contains(nextWaypoint.getArtifactTypeID())) {
                rect = new Rectangle(
                        pointX - (DOT_SIZE / 2),
                        pointY - (DOT_SIZE / 2),
                        DOT_SIZE,
                        DOT_SIZE
                );
            } else if (VERY_SMALL_DOT_WAYPOINT_TYPES.contains(nextWaypoint.getArtifactTypeID())) {
                rect = new Rectangle(
                        pointX - (VERY_SMALL_DOT_SIZE / 2),
                        pointY - (VERY_SMALL_DOT_SIZE / 2),
                        VERY_SMALL_DOT_SIZE,
                        VERY_SMALL_DOT_SIZE
                );
            } else {
                rect = new Rectangle(
                        pointX - (whiteWaypointImage.getWidth() / 2),
                        pointY - whiteWaypointImage.getHeight(),
                        whiteWaypointImage.getWidth(),
                        whiteWaypointImage.getHeight()
                );
            }

            if (rect.contains(clickPoint)) {
                closestPoints.add(nextWaypoint);
            }
        }

        return closestPoints;
    }

    /**
     * Abstract listener class to listen for the completion of a resize event.
     *
     * ComponentListener does not provide support for listening for the
     * completion of a resize event. In order to provide this functionality
     * ComponentResizeEndListener as a time that is restarted every time
     * componentResize is called. When the timer finally runs out the assumption
     * is that the resize has completed.
     */
    public abstract class ComponentResizeEndListener
            extends ComponentAdapter
            implements ActionListener {

        private final Timer timer;
        private static final int DEFAULT_TIMEOUT = 200;

        /**
         * Constructs a new Listener with a default timeout of DEFAULT_TIMEOUT
         * milliseconds.
         */
        public ComponentResizeEndListener() {
            this(DEFAULT_TIMEOUT);
        }

        /**
         * Constructs a new listener with the given timeout value.
         *
         * @param delayMS timeout value in milliseconds
         */
        public ComponentResizeEndListener(int delayMS) {
            timer = new Timer(delayMS, this);
            timer.setRepeats(false);
            timer.setCoalesce(false);
        }

        @Override
        public void componentResized(ComponentEvent e) {
            timer.restart();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            resizeTimedOut();
        }

        /**
         * Called when the resize event has completed or timed out
         */
        public abstract void resizeTimedOut();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        mapViewer = new org.jxmapviewer.JXMapViewer();
        zoomPanel = new javax.swing.JPanel();
        zoomSlider = new javax.swing.JSlider();
        javax.swing.JButton zoomInBtn = new javax.swing.JButton();
        javax.swing.JButton zoomOutBtn = new javax.swing.JButton();

        setFocusable(false);
        setLayout(new java.awt.BorderLayout());

        mapViewer.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseMoved(java.awt.event.MouseEvent evt) {
                mapViewerMouseMoved(evt);
            }
        });
        mapViewer.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                mapViewerMouseClicked(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                mapViewerMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                mapViewerMouseReleased(evt);
            }
        });
        mapViewer.setLayout(new java.awt.GridBagLayout());

        zoomPanel.setFocusable(false);
        zoomPanel.setOpaque(false);
        zoomPanel.setRequestFocusEnabled(false);
        zoomPanel.setLayout(new java.awt.GridBagLayout());

        zoomSlider.setMaximum(15);
        zoomSlider.setMinimum(10);
        zoomSlider.setMinorTickSpacing(1);
        zoomSlider.setOrientation(javax.swing.JSlider.VERTICAL);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setSnapToTicks(true);
        zoomSlider.setInverted(true);
        zoomSlider.setMinimumSize(new java.awt.Dimension(35, 100));
        zoomSlider.setOpaque(false);
        zoomSlider.setPreferredSize(new java.awt.Dimension(35, 190));
        zoomSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zoomSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        zoomPanel.add(zoomSlider, gridBagConstraints);

        zoomInBtn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/plus-grey.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(zoomInBtn, org.openide.util.NbBundle.getMessage(MapPanel.class, "MapPanel.zoomInBtn.text")); // NOI18N
        zoomInBtn.setBorder(null);
        zoomInBtn.setBorderPainted(false);
        zoomInBtn.setFocusPainted(false);
        zoomInBtn.setRequestFocusEnabled(false);
        zoomInBtn.setRolloverEnabled(false);
        zoomInBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInBtnActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        zoomPanel.add(zoomInBtn, gridBagConstraints);

        zoomOutBtn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/minus-grey.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(zoomOutBtn, org.openide.util.NbBundle.getMessage(MapPanel.class, "MapPanel.zoomOutBtn.text")); // NOI18N
        zoomOutBtn.setBorder(null);
        zoomOutBtn.setBorderPainted(false);
        zoomOutBtn.setFocusPainted(false);
        zoomOutBtn.setRequestFocusEnabled(false);
        zoomOutBtn.setRolloverEnabled(false);
        zoomOutBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutBtnActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        zoomPanel.add(zoomOutBtn, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        mapViewer.add(zoomPanel, gridBagConstraints);

        add(mapViewer, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void zoomSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zoomSliderStateChanged
        if (!zoomChanging) {
            setZoom(zoomSlider.getValue());
        }
    }//GEN-LAST:event_zoomSliderStateChanged

    private void mapViewerMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapViewerMousePressed
        if (evt.isPopupTrigger()) {
            showPopupMenu(evt.getPoint());
        }
    }//GEN-LAST:event_mapViewerMousePressed

    private void mapViewerMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapViewerMouseReleased
        if (evt.isPopupTrigger()) {
            showPopupMenu(evt.getPoint());
        }
    }//GEN-LAST:event_mapViewerMouseReleased

    private void mapViewerMouseMoved(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapViewerMouseMoved
        GeoPosition geopos = mapViewer.convertPointToGeoPosition(evt.getPoint());
        firePropertyChange(CURRENT_MOUSE_GEOPOSITION, null, geopos);
    }//GEN-LAST:event_mapViewerMouseMoved

    private void mapViewerMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapViewerMouseClicked
        if (!evt.isPopupTrigger() && SwingUtilities.isLeftMouseButton(evt)) {
            List<MapWaypoint> waypoints = findClosestWaypoint(evt.getPoint());
            if (waypoints.size() > 0) {
                MapWaypoint selection = waypoints.get(0);
                currentlySelectedWaypoint = selection;
                currentlySelectedSet = null;
                for (Set<MapWaypoint> track : tracks) {
                    if (track.contains(selection)) {
                        currentlySelectedSet = track;
                        break;
                    }
                }
                if (currentlySelectedSet == null) {
                    for (Set<MapWaypoint> area : areas) {
                        if (area.contains(selection)) {
                            currentlySelectedSet = area;
                            break;
                        }
                    }
                }
            } else {
                currentlySelectedWaypoint = null;
                currentlySelectedSet = null;
            }
            showDetailsPopup();
        }
    }//GEN-LAST:event_mapViewerMouseClicked

    private void zoomInBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInBtnActionPerformed
        int currentValue = mapViewer.getZoom();
        setZoom(currentValue - 1);
    }//GEN-LAST:event_zoomInBtnActionPerformed

    private void zoomOutBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutBtnActionPerformed
        int currentValue = mapViewer.getZoom();
        setZoom(currentValue + 1);
    }//GEN-LAST:event_zoomOutBtnActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.jxmapviewer.JXMapViewer mapViewer;
    private javax.swing.JPanel zoomPanel;
    private javax.swing.JSlider zoomSlider;
    // End of variables declaration//GEN-END:variables

    /**
     * Renderer for the map waypoints.
     */
    private class MapWaypointRenderer implements WaypointRenderer<MapWaypoint> {

        private final Map<Color, BufferedImage> dotImageCache = new HashMap<>();
        private final Map<Color, BufferedImage> verySmallDotImageCache = new HashMap<>();
        private final Map<Color, BufferedImage> waypointImageCache = new HashMap<>();

        /**
         *
         * @param waypoint the waypoint for which to get the color selected
         *
         * @return the color that this waypoint should be rendered
         */
        private Color getColor(MapWaypoint waypoint) {
            Color baseColor = waypoint.getColor();
            if (waypoint.equals(currentlySelectedWaypoint)
                    || (currentlySelectedSet != null && currentlySelectedSet.contains(waypoint))) {
                // Highlight this waypoint since it is selected
                return Color.YELLOW;
            } else {
                return baseColor;
            }
        }

        /**
         * Creates a dot image with the specified color
         *
         * @param color the color of the new image
         * @param s the size of the dot
         *
         * @return the new dot image
         */
        private BufferedImage createTrackDotImage(Color color, int s) {

            BufferedImage ret = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = ret.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(1, 1, s - 2, s - 2);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1));
            g.drawOval(1, 1, s - 2, s - 2);
            g.dispose();
            return ret;
        }

        /**
         * Creates a waypoint image with the specified color
         *
         * @param color the color of the new image
         *
         * @return the new waypoint image
         */
        private BufferedImage createWaypointImage(Color color) {
            int w = whiteWaypointImage.getWidth();
            int h = whiteWaypointImage.getHeight();

            BufferedImage ret = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

            Graphics2D g = ret.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(whiteWaypointImage, 0, 0, null);
            g.setComposite(AlphaComposite.SrcIn);
            g.setColor(color);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcAtop);
            g.drawImage(transparentWaypointImage, 0, 0, null);
            g.dispose();
            return ret;
        }

        @Override
        public void paintWaypoint(Graphics2D g, JXMapViewer map, MapWaypoint waypoint) {
            Color color = getColor(waypoint);
            BufferedImage image;
            Point2D point = map.getTileFactory().geoToPixel(waypoint.getPosition(), map.getZoom());
            int x = (int) point.getX();
            int y = (int) point.getY();

            if (DOT_WAYPOINT_TYPES.contains(waypoint.getArtifactTypeID())) {
                image = dotImageCache.computeIfAbsent(color, k -> {
                    return createTrackDotImage(color, DOT_SIZE);
                });
                // Center the dot on the GPS coordinate
                y -= image.getHeight() / 2;
            } else if (VERY_SMALL_DOT_WAYPOINT_TYPES.contains(waypoint.getArtifactTypeID())) {
                image = verySmallDotImageCache.computeIfAbsent(color, k -> {
                    return createTrackDotImage(color, VERY_SMALL_DOT_SIZE);
                });
                // Center the dot on the GPS coordinate
                y -= image.getHeight() / 2;
            } else {
                image = waypointImageCache.computeIfAbsent(color, k -> {
                    return createWaypointImage(color);
                });
                // Align the bottom of the pin with the GPS coordinate
                y -= image.getHeight();
            }
            // Center image horizontally on image
            x -= image.getWidth() / 2;

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.drawImage(image, x, y, null);
            g2d.dispose();
        }
    }

    /**
     * Renderer for map track routes
     */
    private class MapTrackRenderer implements Painter<JXMapViewer> {

        private final List<Set<MapWaypoint>> tracks;

        MapTrackRenderer(List<Set<MapWaypoint>> tracks) {
            this.tracks = tracks;
        }

        private void drawRoute(Set<MapWaypoint> track, Graphics2D g, JXMapViewer map) {
            int lastX = 0;
            int lastY = 0;

            boolean first = true;

            for (MapWaypoint wp : track) {
                Point2D p = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
                int thisX = (int) p.getX();
                int thisY = (int) p.getY();

                if (first) {
                    first = false;
                } else {
                    g.drawLine(lastX, lastY, thisX, thisY);
                }

                lastX = thisX;
                lastY = thisY;
            }
        }

        @Override
        public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
            Graphics2D g2d = (Graphics2D) g.create();

            Rectangle bounds = map.getViewportBounds();
            g2d.translate(-bounds.x, -bounds.y);

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2));

            for (Set<MapWaypoint> track : tracks) {
                drawRoute(track, g2d, map);
            }

            g2d.dispose();
        }
    }
    
    /**
     * Renderer for map areas
     */
    private class MapAreaRenderer implements Painter<JXMapViewer> {

        private final List<Set<MapWaypoint>> areas;

        MapAreaRenderer(List<Set<MapWaypoint>> areas) {
            this.areas = areas;
        }

        /**
         * Shade in the area on the map.
         * 
         * @param area The waypoints defining the outline of the area.
         * @param g    Graphics2D
         * @param map  JXMapViewer
         */
        private void drawArea(Set<MapWaypoint> area, Graphics2D g, JXMapViewer map) {
            if (area.isEmpty()) {
                return;
            }
            boolean first = true;
            
            GeneralPath polygon = new GeneralPath(GeneralPath.WIND_EVEN_ODD, area.size());

            for (MapWaypoint wp : area) {
                Point2D p = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
                int thisX = (int) p.getX();
                int thisY = (int) p.getY();

                if (first) {
                    polygon.moveTo(thisX, thisY);
                    first = false;
                } else {
                    polygon.lineTo(thisX, thisY);
                }
            }
            polygon.closePath();
            
            Color areaColor = area.iterator().next().getColor();
            final double maxColorValue = 255.0;
            g.setPaint(new Color((float)(areaColor.getRed() / maxColorValue), 
                    (float)(areaColor.getGreen() / maxColorValue), 
                    (float)(areaColor.getBlue() / maxColorValue),
                    .2f));
            g.fill(polygon);
            g.draw(polygon);
        }

        @Override
        public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
            Graphics2D g2d = (Graphics2D) g.create();

            Rectangle bounds = map.getViewportBounds();
            g2d.translate(-bounds.x, -bounds.y);

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2));

            for (Set<MapWaypoint> area : areas) {
                drawArea(area, g2d, map);
            }

            g2d.dispose();
        }
    }    
}
