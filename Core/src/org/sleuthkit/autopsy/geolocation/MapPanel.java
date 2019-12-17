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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.viewer.WaypointRenderer;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.datamodel.TskCoreException;
import javax.imageio.ImageIO;
import org.jxmapviewer.viewer.DefaultWaypointRenderer;

/**
 * The map panel. This panel contains the jxmapviewer MapViewer
 */
final public class MapPanel extends javax.swing.JPanel {

    static final String CURRENT_MOUSE_GEOPOSITION = "CURRENT_MOUSE_GEOPOSITION";
    private static final Logger logger = Logger.getLogger(MapPanel.class.getName());

    private static final long serialVersionUID = 1L;
    private boolean zoomChanging;
    private KdTree<MapWaypoint> waypointTree;

    private Popup currentPopup;
    private final PopupFactory popupFactory;

    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 200;
    private static final int POPUP_MARGIN = 10;

    private MapWaypoint currentlySelectedWaypoint;

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

        // ComponentListeners do not have a concept of resize event "complete"
        // therefore if we move the popup as the window resizes there will be 
        // a weird blinking behavior.  Using the CompnentResizeEndListener the
        // popup will move to its corner one the resize is completed.
        this.addComponentListener(new ComponentResizeEndListener() {
            @Override
            public void resizeTimedOut() {
                showDetailsPopup();
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
        MouseInputListener mia = new PanMouseInputListener(mapViewer);
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

        // Basic painters for the way points. 
        WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<Waypoint>() {
            @Override
            public Set<Waypoint> getWaypoints() {
                //To assure that the currentlySelectedWaypoint is visible it needs
                // to be painted last. LinkedHashSet has a predicable ordering.
                Set<Waypoint> set = new LinkedHashSet<>();
                if (waypointTree != null) {
                    Iterator<MapWaypoint> iterator = waypointTree.iterator();
                    while (iterator.hasNext()) {
                        MapWaypoint point = iterator.next();
                        if (point != currentlySelectedWaypoint) {
                            set.add(point);
                        }
                    }
                    // Add the currentlySelectedWaypoint to the end so that
                    // it will be painted last.
                    if (currentlySelectedWaypoint != null) {
                        set.add(currentlySelectedWaypoint);
                    }
                }
                return set;
            }
        };
        
        try {
            waypointPainter.setRenderer(new MapWaypointRenderer());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to load waypoint image resource, using DefaultWaypointRenderer", ex);
            waypointPainter.setRenderer(new DefaultWaypointRenderer());
        }

        mapViewer.setOverlayPainter(waypointPainter);
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
     * @param zipPath Path to zip file.
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
    void setWaypoints(List<MapWaypoint> waypoints) {
        waypointTree = new KdTree<>();

        for (MapWaypoint waypoint : waypoints) {
            waypointTree.add(waypoint);
        }

        mapViewer.repaint();
    }

    /**
     * Set the current zoom level.
     *
     * @param zoom
     */
    void setZoom(int zoom) {
        zoomChanging = true;
        mapViewer.setZoom(zoom);
        zoomSlider.setValue((zoomSlider.getMaximum() + zoomSlider.getMinimum()) - zoom);
        zoomChanging = false;
    }

    /**
     * Remove all of the way points from the map.
     */
    void clearWaypoints() {
        waypointTree = null;
        currentlySelectedWaypoint = null;
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
            MapWaypoint waypoint = findClosestWaypoint(point);
            showPopupMenu(waypoint, point);
            // Change the details popup to the currently selected point only if 
            // it the popup is currently visible
            if (waypoint != null && !waypoint.equals(currentlySelectedWaypoint)) {
                currentlySelectedWaypoint = waypoint;
                if(currentPopup != null) {
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
            
            mapViewer.repaint();
        }
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
     * @param mouseClickPoint The mouse click point
     *
     * @return A waypoint that is within 10 pixels of the given point, or null
     *         if none was found.
     */
    private MapWaypoint findClosestWaypoint(Point mouseClickPoint) {
        if (waypointTree == null) {
            return null;
        }

        // Convert the mouse click location to latitude & longitude
        GeoPosition geopos = mapViewer.getTileFactory().pixelToGeo(mouseClickPoint, mapViewer.getZoom());

        // Get the 5 nearest neightbors to the point
        Collection<MapWaypoint> waypoints = waypointTree.nearestNeighbourSearch(20, MapWaypoint.getDummyWaypoint(geopos));

        if (waypoints == null || waypoints.isEmpty()) {
            return null;
        }

        Iterator<MapWaypoint> iterator = waypoints.iterator();

        // These maybe the points closest to lat/log was clicked but 
        // that doesn't mean they are close in terms of pixles.
        while (iterator.hasNext()) {
            MapWaypoint nextWaypoint = iterator.next();

            Point2D point = mapViewer.getTileFactory().geoToPixel(nextWaypoint.getPosition(), mapViewer.getZoom());

            Rectangle rect = mapViewer.getViewportBounds();
            Point converted_gp_pt = new Point((int) point.getX() - rect.x,
                    (int) point.getY() - rect.y);

            if (converted_gp_pt.distance(mouseClickPoint) < 10) {
                return nextWaypoint;
            }
        }

        return null;
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
         * Called when the resize event has completed\timed out
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

        zoomSlider.setMaximum(15);
        zoomSlider.setMinimum(10);
        zoomSlider.setMinorTickSpacing(1);
        zoomSlider.setOrientation(javax.swing.JSlider.VERTICAL);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setSnapToTicks(true);
        zoomSlider.setMinimumSize(new java.awt.Dimension(35, 100));
        zoomSlider.setOpaque(false);
        zoomSlider.setPreferredSize(new java.awt.Dimension(35, 190));
        zoomSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zoomSliderStateChanged(evt);
            }
        });

        javax.swing.GroupLayout zoomPanelLayout = new javax.swing.GroupLayout(zoomPanel);
        zoomPanel.setLayout(zoomPanelLayout);
        zoomPanelLayout.setHorizontalGroup(
            zoomPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(zoomPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(zoomSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        zoomPanelLayout.setVerticalGroup(
            zoomPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, zoomPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(zoomSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

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
        GeoPosition geopos = mapViewer.getTileFactory().pixelToGeo(evt.getPoint(), mapViewer.getZoom());
        firePropertyChange(CURRENT_MOUSE_GEOPOSITION, null, geopos);
    }//GEN-LAST:event_mapViewerMouseMoved

    private void mapViewerMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapViewerMouseClicked
        if(!evt.isPopupTrigger() && (evt.getButton() == MouseEvent.BUTTON1)) {
            currentlySelectedWaypoint = findClosestWaypoint(evt.getPoint());
            showDetailsPopup();
        }
    }//GEN-LAST:event_mapViewerMouseClicked


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.jxmapviewer.JXMapViewer mapViewer;
    private javax.swing.JPanel zoomPanel;
    private javax.swing.JSlider zoomSlider;
    // End of variables declaration//GEN-END:variables

    /**
     * Renderer for the map waypoints.
     */
    private class MapWaypointRenderer implements WaypointRenderer<Waypoint> {
        private final BufferedImage defaultWaypointImage;
        private final BufferedImage selectedWaypointImage;
        
        /**
         * Construct a WaypointRenederer
         * 
         * @throws IOException 
         */
        MapWaypointRenderer() throws IOException {
            defaultWaypointImage = ImageIO.read(getClass().getResource("/org/sleuthkit/autopsy/images/waypoint_teal.png"));
            selectedWaypointImage = ImageIO.read(getClass().getResource("/org/sleuthkit/autopsy/images/waypoint_yellow.png"));
        }
        
        @Override
        public void paintWaypoint(Graphics2D gd, JXMapViewer jxmv, Waypoint waypoint) {
            Point2D point = jxmv.getTileFactory().geoToPixel(waypoint.getPosition(), jxmv.getZoom());

            int x = (int)point.getX();
            int y = (int)point.getY();
            
            BufferedImage image = (waypoint == currentlySelectedWaypoint ? selectedWaypointImage: defaultWaypointImage);

            (gd.create()).drawImage(image, x -image.getWidth() / 2, y -image.getHeight(), null);
        }
        
    }
}
