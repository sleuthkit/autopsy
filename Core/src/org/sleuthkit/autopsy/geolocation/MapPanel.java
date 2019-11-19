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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;
import javax.swing.event.MouseInputListener;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.input.CenterMapListener;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The map panel. This panel contains the jxmapviewer MapViewer
 */
final public class MapPanel extends javax.swing.JPanel {

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
    public MapPanel() {
        initComponents();
        initMap();

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
    }

    /**
     * Initialize the map.
     */
    private void initMap() {

        TileFactoryInfo info = new OSMTileFactoryInfo();
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
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
        mapViewer.setAddressLocation(new GeoPosition(0, 0));

        // Basic painters for the way points. 
        WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<Waypoint>() {
            @Override
            public Set<Waypoint> getWaypoints() {
                Set<Waypoint> set = new HashSet<>();
                if (waypointTree != null) {
                    Iterator<MapWaypoint> iterator = waypointTree.iterator();
                    while (iterator.hasNext()) {
                        set.add(iterator.next());
                    }
                }
                return set;
            }
        };

        mapViewer.setOverlayPainter(waypointPainter);
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
     * Centers the view of the map on the given location.
     *
     * @param waypoint Location to center the map
     */
    void setCenterLocation(Waypoint waypoint) {
        mapViewer.setCenterPosition(waypoint.getPosition());
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
                showDetailsPopup();
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
        popupMenu.show(this, point.x, point.y);
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
        infoPanel = new javax.swing.JPanel();
        cordLabel = new javax.swing.JLabel();

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

        infoPanel.setLayout(new java.awt.BorderLayout());

        org.openide.awt.Mnemonics.setLocalizedText(cordLabel, org.openide.util.NbBundle.getMessage(MapPanel.class, "MapPanel.cordLabel.text")); // NOI18N
        infoPanel.add(cordLabel, java.awt.BorderLayout.EAST);

        add(infoPanel, java.awt.BorderLayout.SOUTH);
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
        cordLabel.setText(geopos.toString());
    }//GEN-LAST:event_mapViewerMouseMoved

    private void mapViewerMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapViewerMouseClicked
        currentlySelectedWaypoint = findClosestWaypoint(evt.getPoint());
        showDetailsPopup();
    }//GEN-LAST:event_mapViewerMouseClicked


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel cordLabel;
    private javax.swing.JPanel infoPanel;
    private org.jxmapviewer.JXMapViewer mapViewer;
    private javax.swing.JPanel zoomPanel;
    private javax.swing.JSlider zoomSlider;
    // End of variables declaration//GEN-END:variables
}
