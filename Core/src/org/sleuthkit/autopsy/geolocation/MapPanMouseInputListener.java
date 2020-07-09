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

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import org.jxmapviewer.JXMapViewer;

/**
 *  MouseInputListener for panning a JXMapViewer 
 * 
 *  This class is adapted from org.jxmapviewer.input.PanMouseInputListener.
 */
final class MapPanMouseInputListener extends MouseInputAdapter {

    private Point prev;
    private final JXMapViewer viewer;
    private Cursor priorCursor;
    private boolean dragging = false;

    /**
     * Construct a new listener.
     * 
     * @param viewer 
     */
    MapPanMouseInputListener(JXMapViewer viewer) {
        this.viewer = viewer;
    }

    @Override
    public void mousePressed(MouseEvent evt) {
        if (!SwingUtilities.isLeftMouseButton(evt)) {
            return;
        }
        if (!viewer.isPanningEnabled()) {
            return;
        }

        // Store the current click point and current cursor
        prev = evt.getPoint();
        priorCursor = viewer.getCursor();
    }

    @Override
    public void mouseDragged(MouseEvent evt) {
        if (!SwingUtilities.isLeftMouseButton(evt)) {
            return;
        }

        if (!viewer.isPanningEnabled()) {
            return;
        }

        // If the map wasn't previously being dragged, set the cursor 
        if (!dragging) {
            viewer.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            dragging = true;
        }

        // Figure out the new map center
        Point current = evt.getPoint();
        double x = viewer.getCenter().getX();
        double y = viewer.getCenter().getY();

        if (prev != null) {
            x += prev.x - current.x;
            y += prev.y - current.y;
        }

        int maxHeight = (int) (viewer.getTileFactory().getMapSize(viewer.getZoom()).getHeight() * viewer
                .getTileFactory().getTileSize(viewer.getZoom()));
        if (y > maxHeight) {
            y = maxHeight;
        }

        prev = current;
        viewer.setCenter(new Point2D.Double(x, y));
        viewer.repaint();
    }

    @Override
    public void mouseReleased(MouseEvent evt) {
        if (!SwingUtilities.isLeftMouseButton(evt)) {
            return;
        }

        prev = null;
        
        // If we were dragging set the cursor back
        if (dragging) {
            viewer.setCursor(priorCursor);
            dragging = false;
        }
    }
}
