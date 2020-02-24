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
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.TextConverter;
import org.sleuthkit.autopsy.coreutils.TextConverterException;
import static org.sleuthkit.autopsy.centralrepository.datamodel.RdbmsCentralRepo.SOFTWARE_CR_DB_SCHEMA_VERSION;

/**
 * Settings for the Postgres implementation of the Central Repository database
 *
 * NOTE: This is public scope because the options panel calls it directly to
 * set/get
 */
public final class PostgresCentralRepoSettings implements CentralRepoDbSettings {

    private final static Logger LOGGER = Logger.getLogger(PostgresCentralRepoSettings.class.getName());
    private final static String DEFAULT_HOST = ""; // NON-NLS
    private final static int DEFAULT_PORT = 5432;
    private final static String DEFAULT_DBNAME = "central_repository"; // NON-NLS
    private final static String DEFAULT_USERNAME = "";
    private final static String DEFAULT_PASSWORD = "";
    private final static String VALIDATION_QUERY = "SELECT version()"; // NON-NLS
    private final static String JDBC_BASE_URI = "jdbc:postgresql://"; // NON-NLS
    private final static String JDBC_DRIVER = "org.postgresql.Driver"; // NON-NLS
    private final static String DB_NAMES_REGEX = "[a-z][a-z0-9_]*"; // only lower case
    private final static String DB_USER_NAMES_REGEX = "[a-zA-Z]\\w*";
    private String host;
    private int port;
    private String dbName;
    private int bulkThreshold;
    private String userName;
    private String password;

    public PostgresCentralRepoSettings() {
        loadSettings();
    }

    @Override
    public String toString() {
        return String.format("PostgresCentralRepoSettings: [db type: postgres, host: %s:%d, db name: %s, username: %s]",
            getHost(), getPort(), getDbName(), getUserName());
    }
    
    public void loadSettings() {
        host = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.host"); // NON-NLS
        if (host == null || host.isEmpty()) {
            host = DEFAULT_HOST;
        }

        try {
            String portString = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.port"); // NON-NLS
            if (portString == null || portString.isEmpty()) {
                port = DEFAULT_PORT;
            } else {
                port = Integer.parseInt(portString);
                if (port < 0 || port > 65535) {
                    port = DEFAULT_PORT;
                }
            }
        } catch (NumberFormatException ex) {
            port = DEFAULT_PORT;
        }

        dbName = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.dbName"); // NON-NLS
        if (dbName == null || dbName.isEmpty()) {
            dbName = DEFAULT_DBNAME;
        }

        try {
            String bulkThresholdString = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.bulkThreshold"); // NON-NLS
            if (bulkThresholdString == null || bulkThresholdString.isEmpty()) {
                this.bulkThreshold = RdbmsCentralRepo.DEFAULT_BULK_THRESHHOLD;
            } else {
                this.bulkThreshold = Integer.parseInt(bulkThresholdString);
                if (getBulkThreshold() <= 0) {
                    this.bulkThreshold = RdbmsCentralRepo.DEFAULT_BULK_THRESHHOLD;
                }
            }
        } catch (NumberFormatException ex) {
            this.bulkThreshold = RdbmsCentralRepo.DEFAULT_BULK_THRESHHOLD;
        }

        userName = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.user"); // NON-NLS
        if (userName == null || userName.isEmpty()) {
            userName = DEFAULT_USERNAME;
        }

        password = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.password"); // NON-NLS
        if (password == null || password.isEmpty()) {
            password = DEFAULT_PASSWORD;
        } else {
            try {
                password = TextConverter.convertHexTextToText(password);
            } catch (TextConverterException ex) {
                LOGGER.log(Level.WARNING, "Failed to convert password from hex text to text.", ex);
                password = DEFAULT_PASSWORD;
            }
        }
    }

    public void saveSettings() {
        ModuleSettings.setConfigSetting("CentralRepository", "db.postgresql.host", getHost()); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.postgresql.port", Integer.toString(port)); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.postgresql.dbName", getDbName()); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.postgresql.bulkThreshold", Integer.toString(getBulkThreshold())); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.postgresql.user", getUserName()); // NON-NLS
        try {
            ModuleSettings.setConfigSetting("CentralRepository", "db.postgresql.password", TextConverter.convertTextToHexText(getPassword())); // NON-NLS
        } catch (TextConverterException ex) {
            LOGGER.log(Level.SEVERE, "Failed to convert password from text to hex text.", ex);
        }
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
        StringBuilder url = new StringBuilder();
        url.append(getJDBCBaseURI());
        url.append(getHost());
        url.append("/"); // NON-NLS
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
            LOGGER.log(Level.SEVERE, "Failed to acquire ephemeral connection to postgresql."); // NON-NLS
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

   



   















    boolean isChanged() {
        String hostString = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.host"); // NON-NLS
        String portString = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.port"); // NON-NLS
        String dbNameString = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.dbName"); // NON-NLS
        String bulkThresholdString = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.bulkThreshold"); // NON-NLS
        String userNameString = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.user"); // NON-NLS
        String userPasswordString = ModuleSettings.getConfigSetting("CentralRepository", "db.postgresql.password"); // NON-NLS

        return !host.equals(hostString) || !Integer.toString(port).equals(portString)
                || !dbName.equals(dbNameString) || !Integer.toString(bulkThreshold).equals(bulkThresholdString)
                || !userName.equals(userNameString) || !password.equals(userPasswordString);
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) throws CentralRepoException {
        if (null != host && !host.isEmpty()) {
            this.host = host;
        } else {
            throw new CentralRepoException("Invalid host name. Cannot be empty."); // NON-NLS
        }
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) throws CentralRepoException {
        if (port > 0 && port < 65535) {
            this.port = port;
        } else {
            throw new CentralRepoException("Invalid port. Must be a number greater than 0."); // NON-NLS
        }
    }

    /**
     * To prevent issues where one command can honor case and another cannot, we
     * will force the dbname to lower case.
     *
     * @return the dbName
     */
    public String getDbName() {
        return dbName.toLowerCase();
    }

    /**
     * @param dbName the dbName to set
     */
    public void setDbName(String dbName) throws CentralRepoException {
        if (dbName == null || dbName.isEmpty()) {
            throw new CentralRepoException("Invalid database name. Cannot be empty."); // NON-NLS
        } else if (!Pattern.matches(DB_NAMES_REGEX, dbName)) {
            throw new CentralRepoException("Invalid database name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."); // NON-NLS
        }

        this.dbName = dbName.toLowerCase();
    }

    /**
     * @return the bulkThreshold
     */
    int getBulkThreshold() {
        return bulkThreshold;
    }

    /**
     * @param bulkThreshold the bulkThreshold to set
     */
    public void setBulkThreshold(int bulkThreshold) throws CentralRepoException {
        if (bulkThreshold > 0) {
            this.bulkThreshold = bulkThreshold;
        } else {
            throw new CentralRepoException("Invalid bulk threshold."); // NON-NLS
        }
    }

    /**
     * @return the userName
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @param userName the userName to set
     */
    public void setUserName(String userName) throws CentralRepoException {
        if (userName == null || userName.isEmpty()) {
            throw new CentralRepoException("Invalid user name. Cannot be empty."); // NON-NLS
        } else if (!Pattern.matches(DB_USER_NAMES_REGEX, userName)) {
            throw new CentralRepoException("Invalid user name. Name must start with a letter and can only contain letters, numbers, and '_'."); // NON-NLS
        }
        this.userName = userName;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) throws CentralRepoException {
        if (password == null || password.isEmpty()) {
            throw new CentralRepoException("Invalid user password. Cannot be empty."); // NON-NLS
        }
        this.password = password;
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

}
