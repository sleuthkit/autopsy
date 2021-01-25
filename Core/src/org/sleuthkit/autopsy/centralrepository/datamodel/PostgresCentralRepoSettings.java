/*
 * Central Repository
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Settings for the Postgres implementation of the Central Repository database
 *
 * NOTE: This is public scope because the options panel calls it directly to
 * set/get
 */
public final class PostgresCentralRepoSettings implements CentralRepoDbConnectivityManager {

    private final static Logger LOGGER = Logger.getLogger(PostgresCentralRepoSettings.class.getName());
    private final static String VALIDATION_QUERY = "SELECT version()"; // NON-NLS
    private final static String JDBC_BASE_URI = "jdbc:postgresql://"; // NON-NLS
    private final static String JDBC_DRIVER = "org.postgresql.Driver"; // NON-NLS
    
    
    private final PostgresSettingsLoader loader;
    private PostgresConnectionSettings connSettings;
    
    private static PostgresSettingsLoader getLoaderFromSaved() throws CentralRepoException {
        CentralRepoDbChoice choice = CentralRepoDbManager.getSavedDbChoice();
        if (choice == CentralRepoDbChoice.POSTGRESQL_CUSTOM)
            return PostgresSettingsLoader.CUSTOM_SETTINGS_LOADER;
        else if (choice == CentralRepoDbChoice.POSTGRESQL_MULTIUSER)
            return PostgresSettingsLoader.MULTIUSER_SETTINGS_LOADER;
        else
            throw new CentralRepoException("cannot load or save postgres settings for selection: " + choice);
    }
    
    /**
     * This method loads the settings with a custom {@link PostgresSettingsLoader PostgresSettingsLoader} object.
     * @param loader    The loader to be used.
     */
    public PostgresCentralRepoSettings(PostgresSettingsLoader loader) {
        this.loader = loader;
        loadSettings();
    }
    
    /**
     * This is the default constructor that loads settings from selected db choice.
     */
    public PostgresCentralRepoSettings() throws CentralRepoException {
        this(getLoaderFromSaved());
    }

    
    @Override
    public void loadSettings() {
        this.connSettings = loader.loadSettings();
    }

    @Override
    public void saveSettings() {
        loader.saveSettings(connSettings);
    }
    
    
    @Override
    public String toString() {
        return String.format("PostgresCentralRepoSettings: [db type: postgres, host: %s:%d, db name: %s, username: %s]",
            getHost(), getPort(), getDbName(), getUserName());
    }
    
    
    /**
     * @return the VALIDATION_QUERY
     */
    String getValidationQuery() {
        return VALIDATION_QUERY;
    }

    /**
     * @return the POSTGRES_DRIVER
     */
    String getDriver() {
        return JDBC_DRIVER;
    }

    /**
     * @return the JDBC_BASE_URI
     */
    String getJDBCBaseURI() {
        return JDBC_BASE_URI;
    }

    
    /**
     * Get the full connection URL as a String
     *
     * @param usePostgresDb Connect to the 'postgres' database when testing
     *                      connectivity and creating the main database.
     *
     * @return
     */
    String getConnectionURL(boolean usePostgresDb) {
        StringBuilder url = new StringBuilder()
                .append(getJDBCBaseURI())
                .append(getHost())
                .append(":") // NON-NLS
                .append(getPort())
                .append("/"); // NON-NLS
        if (usePostgresDb) {
            url.append("postgres"); // NON-NLS
        } else {
            url.append(getDbName());
        }

        return url.toString();
    }

    /**
     * Use the current settings to get an ephemeral client connection for
     * testing.
     *
     * @return Connection or null.
     */
    Connection getEphemeralConnection(boolean usePostgresDb) {
        Connection conn;
        try {
            String url = getConnectionURL(usePostgresDb);
            Properties props = new Properties();
            props.setProperty("user", getUserName());
            props.setProperty("password", getPassword());

            Class.forName(getDriver());
            conn = DriverManager.getConnection(url, props);
        } catch (ClassNotFoundException | SQLException ex) {
            // TODO: Determine why a connection failure (ConnectionException) re-throws
            // the SQLException and does not print this log message?
            LOGGER.log(Level.SEVERE, "Failed to acquire ephemeral connection to postgresql.", ex); // NON-NLS
            conn = null;
        }
        return conn;
    }

    /**
     * Use the current settings and the validation query to test the connection
     * to the database.
     *
     * @return true if successfull connection, else false.
     */
    @Override
    public boolean verifyConnection() {
        Connection conn = getEphemeralConnection(true);
        if (null == conn) {
            return false;
        }

        boolean result = CentralRepoDbUtil.executeValidationQuery(conn, VALIDATION_QUERY);
        CentralRepoDbUtil.closeConnection(conn);
        return result;
    }

    /**
     * Check to see if the database exists.
     *
     * @return true if exists, else false
     */
    @Override
    public boolean verifyDatabaseExists() {
        Connection conn = getEphemeralConnection(true);
        if (null == conn) {
            return false;
        }

        String sql = "SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) = lower(?) LIMIT 1"; // NON-NLS
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setString(1, getDbName());
            rs = ps.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to execute database existance query.", ex); // NON-NLS
            return false;
        } finally {
            CentralRepoDbUtil.closeStatement(ps);
            CentralRepoDbUtil.closeResultSet(rs);
            CentralRepoDbUtil.closeConnection(conn);
        }
        return false;
    }

    /**
     * Use the current settings and the schema version query to test the
     * database schema.
     *
     * @return true if successful connection, else false.
     */
    @Override
    public boolean verifyDatabaseSchema() {
        Connection conn = getEphemeralConnection(false);
        if (null == conn) {
            return false;
        }

        boolean result = CentralRepoDbUtil.schemaVersionIsSet(conn);

        CentralRepoDbUtil.closeConnection(conn);
        return result;
    }

    @Override
    public boolean createDatabase() {
        Connection conn = getEphemeralConnection(true);
        if (null == conn) {
            return false;
        }

        String sql = "CREATE DATABASE %s OWNER %s"; // NON-NLS
        try {
            Statement stmt;
            stmt = conn.createStatement();
            stmt.execute(String.format(sql, getDbName(), getUserName()));
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to execute create database statement.", ex); // NON-NLS
            return false;
        } finally {
            CentralRepoDbUtil.closeConnection(conn);
        }
        return true;

    }

    @Override
    public boolean deleteDatabase() {
        Connection conn = getEphemeralConnection(true);
        if (null == conn) {
            return false;
        }

        String sql = "DROP DATABASE %s"; // NON-NLS
        try {
            Statement stmt;
            stmt = conn.createStatement();
            stmt.execute(String.format(sql, getDbName()));
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to execute drop database statement.", ex); // NON-NLS
            return false;
        } finally {
            CentralRepoDbUtil.closeConnection(conn);
        }
        return true;

    }


    /**
     * @return the host
     */
    public String getHost() {
        return connSettings.getHost();
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) throws CentralRepoException {
        connSettings.setHost(host);
    }

    /**
     * @return the port
     */
    public int getPort() {
        return connSettings.getPort();
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) throws CentralRepoException {
        connSettings.setPort(port);
    }

    /**
     * To prevent issues where one command can honor case and another cannot, we
     * will force the dbname to lower case.
     *
     * @return the dbName
     */
    public String getDbName() {
        return connSettings.getDbName() == null ? null : connSettings.getDbName().toLowerCase();
    }

    /**
     * @param dbName the dbName to set
     */
    public void setDbName(String dbName) throws CentralRepoException {
        connSettings.setDbName(dbName);
    }

    /**
     * @return the bulkThreshold
     */
    int getBulkThreshold() {
        return connSettings.getBulkThreshold();
    }

    /**
     * @param bulkThreshold the bulkThreshold to set
     */
    public void setBulkThreshold(int bulkThreshold) throws CentralRepoException {
        connSettings.setBulkThreshold(bulkThreshold);
    }

    /**
     * @return the userName
     */
    public String getUserName() {
        return connSettings.getUserName();
    }

    /**
     * @param userName the userName to set
     */
    public void setUserName(String userName) throws CentralRepoException {
        connSettings.setUserName(userName);
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return connSettings.getPassword();
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) throws CentralRepoException {
        connSettings.setPassword(password);
    }

    @Override
    public DatabaseTestResult testStatus() {
        if (verifyConnection()) {
            if (verifyDatabaseExists()) {
                if (verifyDatabaseSchema()) {
                    return DatabaseTestResult.TESTED_OK;
                } else {
                    return DatabaseTestResult.SCHEMA_INVALID;
                }
            } else {
                return DatabaseTestResult.DB_DOES_NOT_EXIST;
            }
        } else {
            return DatabaseTestResult.CONNECTION_FAILED;
        }
    }
}
