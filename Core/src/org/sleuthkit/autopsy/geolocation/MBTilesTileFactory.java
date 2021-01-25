/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.jxmapviewer.viewer.Tile;
import org.jxmapviewer.viewer.TileCache;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.util.GeoUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;

/**
 * This TileFactory class borrows from org.jxmapviewer.viewer.AbstractTileFactory, changing to support
 * the getting the tiles from a database instead the web.
 * 
 * @see <a href="https://github.com/msteiger/jxmapviewer2/blob/master/jxmapviewer2/src/main/java/org/jxmapviewer/viewer/AbstractTileFactory.java">AbstractTileFactory</a>
 *
 */
final class MBTilesTileFactory extends TileFactory {

    private static final Logger logger = Logger.getLogger(MBTilesTileFactory.class.getName());

    private volatile int pendingTiles = 0;
    private static final int THREAD_POOL_SIZE = 4;
    private ExecutorService service;

    private final Map<String, Tile> tileMap;

    private final TileCache cache;

    private MBTilesFileConnector connector;

    /**
     * Construct a new TileFactory for the MBTiles file at the given location.
     *
     * @param filePath
     *
     * @throws GeoLocationDataException
     */
    MBTilesTileFactory(String filePath) throws GeoLocationDataException {
        this(new MBTilesFileConnector(filePath));
    }

    /**
     * Construct a new TileFacotry.
     *
     * @param connector
     */
    private MBTilesTileFactory(MBTilesFileConnector connector) {
        super(connector.getInfo());
        this.connector = connector;
        cache = new TileCache();
        tileMap = new HashMap<>();
    }

    /**
     * Returns the tile that is located at the given tile point for this zoom.
     *
     * @param x    Tile column
     * @param y    Tile row
     * @param zoom Current zoom level
     *
     * @return A new Tile object
     *
     */
    @Override
    public Tile getTile(int x, int y, int zoom) {
        return getTile(x, y, zoom, true);
    }

    /**
     * Returns the tile object for the given location.
     *
     * @param tpx
     * @param tpy
     * @param zoom
     * @param eagerLoad
     *
     * @return
     */
    private Tile getTile(int tpx, int tpy, int zoom, boolean eagerLoad) {
        // wrap the tiles horizontally --> mod the X with the max width
        // and use that
        int tileX = tpx;// tilePoint.getX();
        int numTilesWide = (int) getMapSize(zoom).getWidth();
        if (tileX < 0) {
            tileX = numTilesWide - (Math.abs(tileX) % numTilesWide);
        }

        tileX %= numTilesWide;
        int tileY = tpy;

        String url = getInfo().getTileUrl(tileX, tileY, zoom);

        Tile.Priority pri = Tile.Priority.High;
        if (!eagerLoad) {
            pri = Tile.Priority.Low;
        }
        Tile tile;
        if (!tileMap.containsKey(url)) {
            // If its not a valid tile location return an empty tile.
            if (!GeoUtil.isValidTile(tileX, tileY, zoom, getInfo())) {
                tile = new MBTilesTile(tileX, tileY, zoom);
            } else {
                tile = new MBTilesTile(tileX, tileY, zoom, url, pri);
                startLoading(tile);
            }
            tileMap.put(url, tile);
        } else {
            tile = tileMap.get(url);
            // If the tile is in the tileMap, but the image is not loaded yet,
            // bump the priority.
            if (tile.getPriority() == Tile.Priority.Low && eagerLoad && !tile.isLoaded()) {
                promote(tile);
            }
        }

        return tile;
    }

    /**
     * Returns the TileCache.
     *
     * @return the tile cache
     */
    TileCache getTileCache() {
        return cache;
    }

    /**
     * ==== Threaded Tile loading code ===
     */
    /**
     * Thread pool for loading the tiles
     */
    private final BlockingQueue<Tile> tileQueue = new PriorityBlockingQueue<>(5, new Comparator<Tile>() {
        @Override
        public int compare(Tile o1, Tile o2) {
            if (o1.getPriority() == Tile.Priority.Low && o2.getPriority() == Tile.Priority.High) {
                return 1;
            }
            if (o1.getPriority() == Tile.Priority.High && o2.getPriority() == Tile.Priority.Low) {
                return -1;
            }
            return 0;

        }
    });

    /**
     * Subclasses may override this method to provide their own executor
     * services. This method will be called each time a tile needs to be loaded.
     * Implementations should cache the ExecutorService when possible.
     *
     * @return ExecutorService to load tiles with
     */
    synchronized ExecutorService getService() {
        if (service == null) {
            // System.out.println("creating an executor service with a threadpool of size " + threadPoolSize);
            service = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new ThreadFactory() {
                private int count = 0;

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "tile-pool-" + count++);
                    t.setPriority(Thread.MIN_PRIORITY);
                    t.setDaemon(true);
                    return t;
                }
            });
        }
        return service;
    }

    @Override
    public void dispose() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
    }

    @Override
    protected synchronized void startLoading(Tile tile) {
        if (tile.isLoading()) {
            return;
        }
        pendingTiles++;
        tile.setLoading(true);
        try {
            tileQueue.put(tile);
            getService().submit(new TileRunner());
        } catch (InterruptedException ex) {

        }
    }

    /**
     * Increase the priority of this tile so it will be loaded sooner.
     *
     * @param tile the tile
     */
    synchronized void promote(Tile tile) {
        if (tileQueue.contains(tile)) {
            try {
                tileQueue.remove(tile);
                tile.setPriority(Tile.Priority.High);
                tileQueue.put(tile);
            } catch (InterruptedException ex) {

            }
        }
    }

    /**
     * @return the number of pending (loading or queues) tiles
     */
    int getPendingTiles() {
        return pendingTiles;
    }

    /**
     * An inner class which actually loads the tiles. Used by the thread queue.
     * Subclasses can override this via createTileRunner(Tile) if
     * necessary.
     */
    private class TileRunner implements Runnable {

        /**
         * Gets the full URI of a tile.
         *
         * @param tile the tile
         *
         * @throws URISyntaxException if the URI is invalid
         * @return a URI for the tile
         */
        protected URI getURI(Tile tile) throws URISyntaxException {
            if (tile.getURL() == null) {
                return null;
            }
            return new URI(tile.getURL());
        }

        @Override
        public void run() {
            /*
             * Attempt to load the tile from . If loading fails, retry two more
             * times. If all attempts fail, nothing else is done. This way, if
             * there is some kind of failure, the pooled thread can try to load
             * other tiles.
             */
            final Tile tile = tileQueue.remove();

            int remainingAttempts = 3;
            while (!tile.isLoaded() && remainingAttempts > 0) {
                remainingAttempts--;
                try {
                    URI uri = getURI(tile);
                    BufferedImage img = cache.get(uri);
                    if (img == null) {
                        img = getImage(uri);
                    }
                    if (img != null) {
                        addImageToTile(tile, img);
                    }
                } catch (OutOfMemoryError memErr) {
                    cache.needMoreMemory();
                } catch (IOException | GeoLocationDataException | InterruptedException | InvocationTargetException | URISyntaxException ex) {
                    if (remainingAttempts == 0) {
                        logger.log(Level.SEVERE, String.format("Failed to load a tile at URL: %s, stopping", tile.getURL()), ex);
                    } else {
                        logger.log(Level.WARNING, "Failed to load a tile at URL: " + tile.getURL() + ", retrying", ex);
                    }
                }
            }
            tile.setLoading(false);
        }
    }
    
    /**
     * 
     * @param uri
     * @return
     * @throws IOException
     * @throws GeoLocationDataException 
     */
    private BufferedImage getImage(URI uri ) throws IOException, GeoLocationDataException{
        BufferedImage img = null;
        byte[] bimg = connector.getTileBytes(uri.toString());
        if (bimg != null && bimg.length > 0) {
            img = ImageIO.read(new ByteArrayInputStream(bimg));
            cache.put(uri, bimg, img);
        }
        return img;
    }
    
    /**
     * 
     * @param tile
     * @param image
     * @throws InterruptedException
     * @throws InvocationTargetException 
     */
    private void addImageToTile(Tile tile, BufferedImage image) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                if (tile instanceof MBTilesTile) {
                    ((MBTilesTile) tile).setImage(image);
                }
                pendingTiles--;
                fireTileLoadedEvent(tile);
            }
        });
    }
}
