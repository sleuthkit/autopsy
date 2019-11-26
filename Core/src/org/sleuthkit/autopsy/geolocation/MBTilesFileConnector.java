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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;

/**
 * Wraps the connection to the MBTiles file\sqlite db.
 */
final class MBTilesFileConnector {

    private final static String DB_URL = "jdbc:sqlite:%s";
    private final static String TILE_QUERY = "SELECT tile_data FROM images WHERE tile_id = '%s'";
    private final static String FORMAT_QUERY = "SELECT value FROM metadata WHERE name='format'";
    private TileFactoryInfo factoryInfo;
    private final ConnectionPool pool;

    /**
     * Returns whether or not the file at the given path is a mbtiles file.
     *
     * @param filePath Absolute path the the file.
     *
     * @return True if the file is an mbtiles file
     *
     * @throws SQLException
     */
    static boolean isValidMBTileRasterFile(String filePath) throws SQLException {
        Path p = Paths.get(filePath);
        if (!p.toFile().exists()) {
            return false;
        }

        String path = filePath.replaceAll("\\\\", "/");
        String url = String.format(DB_URL, path);

        try (Connection connection = DriverManager.getConnection(url)) {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(FORMAT_QUERY)) {
                if (resultSet.next()) {
                    String format = resultSet.getString(1);
                    return format.equals("jpg");
                }
            }
        }
        return false;
    }

    /**
     * Construct a new connection to the MBTile file.
     *
     * @param tileFilePath MBTiles file absolute path
     *
     * @throws GeoLocationDataException
     */
    MBTilesFileConnector(String tileFilePath) throws GeoLocationDataException {
        try {
            pool = new ConnectionPool(tileFilePath);
            factoryInfo = new MBTilesInfo();
        } catch (SQLException ex) {
            throw new GeoLocationDataException(String.format("Unable to create sql connection to %s", tileFilePath), ex);
        }
    }

    /**
     * Returns the TileFacortyInfo object for the MBTile file.
     *
     * @return TileFactoryInfo object or null if the connection has been closed.
     */
    TileFactoryInfo getInfo() {
        return factoryInfo;
    }

    /**
     * Get the tile for the given tileID.
     *
     * @param tileID String tile ID in the format of zoom/x/y
     *
     * @return The tile image byte array or an empty array if the tile was not
     *         found.
     *
     * @throws GeoLocationDataException
     */
    byte[] getTileBytes(String tileID) throws GeoLocationDataException {
        String query = String.format(TILE_QUERY, tileID);

        Connection connection = pool.getConnection();
        if (connection != null) {
            try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
                if (resultSet.next()) {
                    return resultSet.getBytes(1);
                }
            } catch (SQLException ex) {
                throw new GeoLocationDataException(String.format("Failed to get tile %s", tileID), ex);
            } finally {
                pool.releaseConnection(connection);
            }
        }
        return new byte[0];
    }

    /**
     * Close the connection to the MBTile file.
     */
    void closeConnection() {
        try {
            pool.closeConnections();
        } catch (SQLException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * Overload the existing OSMTileFacotyInfo to return a "url" specific to
     * MBTiles.
     */
    private final class MBTilesInfo extends OSMTileFactoryInfo {

        MBTilesInfo() {
            super("MBTilesFile", "");
        }

        @Override
        public String getTileUrl(int x, int y, int zoom) {
            // OSM zoom levels are reversed from how the TileFactory deals with 
            // them.
            int osmZoom = getTotalMapZoom() - zoom;
            return String.format("%d/%d/%d", osmZoom, x, y);
        }
    }

    /**
     * A ConnectionPool to manage the connections to the mbtile\sql file.
     */
    private final class ConnectionPool {

        private static final int POOL_SIZE = 5;

        private final List<Connection> poolConnections;
        private final List<Connection> usedConnections;

        /**
         * Construct a new ConnectionPool and initialize the connections.
         * 
         * @param filePath AbsolutePath to the MBTilesFile.
         * @throws SQLException 
         */
        ConnectionPool(String filePath) throws SQLException {
            usedConnections = new ArrayList<>();

            String path = filePath.replaceAll("\\\\", "/");
            String url = String.format(DB_URL, path);

            poolConnections = new ArrayList<>(POOL_SIZE);
            for (int idx = 0; idx < POOL_SIZE; idx++) {
                poolConnections.add(DriverManager.getConnection(url));
            }
        }

        /**
         * Returns a connection a to the tile file.
         * 
         * @return  A valid connection to the db or null if one is 
         *          not currently available.
         */
        synchronized Connection getConnection() {
            Connection connection = null;
            if (!poolConnections.isEmpty()) {
                connection = poolConnections.remove(poolConnections.size() - 1);
                usedConnections.add(connection);
            }
            return connection;
        }

        /**
         * Frees the connection.
         * 
         * The connection is removed from the list of used connections and 
         * returns the connection to the pool.
         * 
         * @param connection The connection to be freed.
         * 
         * @return True if the connections was freed.
         */
        synchronized boolean releaseConnection(Connection connection) {
            if (usedConnections.contains(connection)) {
                poolConnections.add(connection);
                return usedConnections.remove(connection);
            }

            return false;
        }

        /**
         * Closes all the connections to the db.
         * 
         * @throws SQLException 
         */
        void closeConnections() throws SQLException {
            for (Connection conn : usedConnections) {
                conn.close();
            }

            for (Connection conn : usedConnections) {
                conn.close();
            }
        }
    }
}
