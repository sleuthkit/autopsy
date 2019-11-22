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
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;

/**
 *  Wraps the connection to the MBTiles file\sqlite db.
 */
final class MBTilesFileConnector {
    private final static String DB_URL = "jdbc:sqlite:%s";
    private final static String TILE_QUERY = "SELECT tile_data FROM images WHERE tile_id = '%s'";
    private final static String FORMAT_QUERY = "SELECT value FROM metadata WHERE name='format'";
    private TileFactoryInfo factoryInfo;
    private Connection dbConnection = null;
    
    /**
     * Returns whether or not the file at the given path is a mbtiles file.
     * 
     * @param filePath Absolute path the the file.
     * 
     * @return True if the file is an mbtiles file
     * 
     * @throws SQLException 
     */
    static boolean isValidMBTileRasterFile(String filePath) throws SQLException{
        String path = filePath.replaceAll("\\\\", "/");
        String url = String.format(DB_URL, path);
        
        Path p = Paths.get(filePath);
        if( !p.toFile().exists()) {
            return false;
        }
        
        try(Connection connection = DriverManager.getConnection(url)) {
            try(Statement statement = connection.createStatement()){
                ResultSet resultSet = statement.executeQuery(FORMAT_QUERY);
                if(resultSet.next()) {
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
    MBTilesFileConnector(String tileFilePath) throws GeoLocationDataException{
        try{
            String path = tileFilePath.replaceAll("\\\\", "/");
            String url = String.format(DB_URL, path);
            dbConnection =  DriverManager.getConnection(url);
            factoryInfo = new MBTilesInfo();
        } catch(SQLException ex) {
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
     * @param tileID    String tile ID in the format of 
     *                  zoom/x/y
     * 
     * @return  The tile image byte array or null if the tile was not found
     * 
     * @throws GeoLocationDataException 
     */
    byte[] getTileBytes(String tileID)  throws GeoLocationDataException{
        String query = String.format(TILE_QUERY, tileID);
        
        try (Statement statement = dbConnection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(query);
            if (resultSet.next()) {
                return resultSet.getBytes(1);
            } 
        } catch (SQLException ex) {
            throw new GeoLocationDataException(String.format("Failed to get tile %s", tileID), ex);
        }

        return null;
    }
    
    /**
     * Close the connection to the MBTile file.
     */
    void closeConnection() {

        if(dbConnection != null) {
            try {
                dbConnection.close();
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
            dbConnection = null;
            factoryInfo = null;
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
}
