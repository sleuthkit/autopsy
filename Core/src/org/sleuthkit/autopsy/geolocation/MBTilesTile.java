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

import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;

import org.jxmapviewer.viewer.Tile;

/**
 * Provides a MBTile specific implementation of Tile.
 *
 * This class borrows functionality from Tile.
 */
final class MBTilesTile extends Tile {

    private SoftReference<BufferedImage> image = new SoftReference<>(null);
    private Priority priority = Priority.High;
    private boolean loaded = false;
    private final String tileID;

    /**
     * Construct an empty tile.
     *
     * @param x
     * @param y
     * @param zoom
     */
    MBTilesTile(int x, int y, int zoom) {
        super(x, y, zoom);
        tileID = null;
    }

    /**
     * Construct a new tile.
     *
     * @param x        Tile row
     * @param y        Tile column
     * @param zoom     Tile Zoom level
     * @param tileID   Tile identifier
     * @param priority Priority for loading the tile
     */
    MBTilesTile(int x, int y, int zoom, String tileID, Priority priority) {
        super(x, y, zoom);
        this.priority = priority;
        this.tileID = tileID;
    }

    /**
     * Sets the image for this Tile.
     *
     * @param image
     */
    void setImage(BufferedImage image) {
        this.image = new SoftReference<>(image);
        setLoaded(true);
    }

    /**
     * Indicates if this tile's underlying image has been successfully loaded
     * yet.
     *
     * @return true if the Tile has been loaded
     */
    @Override
    public synchronized boolean isLoaded() {
        return loaded;
    }

    synchronized void setLoaded(boolean loaded) {
        boolean old = isLoaded();
        this.loaded = loaded;
        firePropertyChange("loaded", old, isLoaded());
    }

    @Override
    public BufferedImage getImage() {
        BufferedImage img = image.get();
        if (img == null) {
            setLoaded(false);
        }
        return img;
    }

    @Override
    public Priority getPriority() {
        return priority;
    }

    @Override
    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    /**
     * Overloading the original version of this function to return the tileID
     * for this tile.
     *
     * @return tileID or null if none was set
     */
    @Override
    public String getURL() {
        return tileID;
    }

}
