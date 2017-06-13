/*
 * Enterprise Artifacts Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Settings for the Postgres implementation of the enterprise artifacts manager
 * database
 */
public final class PostgresEamDbSettings {

    private final static Logger LOGGER = Logger.getLogger(PostgresEamDbSettings.class.getName());
    private final String DEFAULT_HOST = "localhost"; // NON-NLS
    private final int DEFAULT_PORT = 5432;
    private final String DEFAULT_DBNAME = "enterpriseartifactmanagerdb"; // NON-NLS
    private final int DEFAULT_BULK_THRESHHOLD = 1000;
    private final String DEFAULT_USERNAME = "";
    private final String DEFAULT_PASSWORD = "";
    private final String DEFAULT_BAD_TAGS = "Evidence"; // NON-NLS
    private final String VALIDATION_QUERY = "SELECT version()"; // NON-NLS
    private final String JDBC_BASE_URI = "jdbc:postgresql://"; // NON-NLS
    private final String JDBC_DRIVER = "org.postgresql.Driver"; // NON-NLS

    private boolean enabled;
    private String host;
    private int port;
    private String dbName;
    private int bulkThreshold;
    private String userName;
    private String password;
    private List<String> badTags;

    public PostgresEamDbSettings() {
        loadSettings();
    }

    public void loadSettings() {
        enabled = Boolean.valueOf(ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.enabled")); // NON-NLS

        host = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.host"); // NON-NLS
        if (host == null || host.isEmpty()) {
            host = DEFAULT_HOST;
        }

        try {
            String portString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.port"); // NON-NLS
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

        dbName = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.dbName"); // NON-NLS
        if (dbName == null || dbName.isEmpty()) {
            dbName = DEFAULT_DBNAME;
        }

        try {
            String bulkThresholdString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.bulkThreshold"); // NON-NLS
            if (bulkThresholdString == null || bulkThresholdString.isEmpty()) {
                this.bulkThreshold = DEFAULT_BULK_THRESHHOLD;
            } else {
                this.bulkThreshold = Integer.parseInt(bulkThresholdString);
                if (getBulkThreshold() <= 0) {
                    this.bulkThreshold = DEFAULT_BULK_THRESHHOLD;
                }
            }
        } catch (NumberFormatException ex) {
            this.bulkThreshold = DEFAULT_BULK_THRESHHOLD;
        }

        userName = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.user"); // NON-NLS
        if (userName == null || userName.isEmpty()) {
            userName = DEFAULT_USERNAME;
        }

        password = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.password"); // NON-NLS
        if (password == null || password.isEmpty()) {
            password = DEFAULT_PASSWORD;
        }

        String badTagsStr = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.badTags"); // NON-NLS
        if (badTagsStr == null || badTagsStr.isEmpty()) {
            badTagsStr = DEFAULT_BAD_TAGS;
        }
        badTags = new ArrayList<>(Arrays.asList(badTagsStr.split(",")));
    }

    public void saveSettings() {
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.enabled", Boolean.toString(isEnabled())); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.postgresql.host", getHost()); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.postgresql.port", Integer.toString(port)); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.postgresql.dbName", getDbName()); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.postgresql.bulkThreshold", Integer.toString(getBulkThreshold())); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.postgresql.user", getUserName()); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.postgresql.password", getPassword()); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.badTags", String.join(",", badTags)); // NON-NLS
    }

    /**
     * Get the full connection URL as a String
     *
     * @return
     */
    public String getConnectionURL() {
        StringBuilder url = new StringBuilder();
        url.append(getJDBCBaseURI());
        url.append(getHost());
        url.append("/"); // NON-NLS
        url.append(getDbName());
        url.append("?user="); // NON-NLS
        url.append(getUserName());
        url.append("&password="); // NON-NLS
        url.append(getPassword());

        return url.toString();
    }

    public boolean testSettings() {
        // Open a new ephemeral client here to test that we can connect
        ResultSet resultSet = null;
        Connection conn = null;
        try {
            String url = getConnectionURL();
            Class.forName(getDriver());
            conn = DriverManager.getConnection(url);
            Statement tester = conn.createStatement();
            resultSet = tester.executeQuery(getValidationQuery());
            if (resultSet.next()) {
                LOGGER.log(Level.INFO, "Testing connection to postgresql success."); // NON-NLS
            }
        } catch (ClassNotFoundException | SQLException ex) {
            LOGGER.log(Level.INFO, "Testing connection to postgresql failed.", ex); // NON-NLS
            return false;
        } finally {
            if (null != resultSet) {
                try {
                    resultSet.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error closing ResultSet.", ex); // NON-NLS
                }
            }
            if (null != conn) {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error closing test connection.", ex); // NON-NLS
                }
            }
        }

        return true;
    }

    public boolean isChanged() {
        String hostString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.host"); // NON-NLS
        String portString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.port"); // NON-NLS
        String dbNameString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.dbName"); // NON-NLS
        String bulkThresholdString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.bulkThreshold"); // NON-NLS
        String userNameString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.user"); // NON-NLS
        String userPasswordString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.postgresql.password"); // NON-NLS

        return !host.equals(hostString) || !Integer.toString(port).equals(portString)
                || !dbName.equals(dbNameString) || !Integer.toString(bulkThreshold).equals(bulkThresholdString)
                || !userName.equals(userNameString) || !password.equals(userPasswordString);
    }

    /**
     * @return the enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled the enabled to set
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
    public void setHost(String host) throws EamDbException {
        if (null != host && !host.isEmpty()) {
            this.host = host;
        } else {
            throw new EamDbException("Error invalid host for database connection. Cannot be null or empty."); // NON-NLS
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
    public void setPort(int port) throws EamDbException {
        if (port > 0 && port < 65535) {
            this.port = port;
        } else {
            throw new EamDbException("Error invalid port for database connection."); // NON-NLS
        }
    }

    /**
     * @return the dbName
     */
    public String getDbName() {
        return dbName;
    }

    /**
     * @param dbName the dbName to set
     */
    public void setDbName(String dbName) throws EamDbException {
        if (dbName != null && !dbName.isEmpty()) {
            this.dbName = dbName;
        } else {
            throw new EamDbException("Error invalid name for database connection. Cannot be null or empty."); // NON-NLS

        }
    }

    /**
     * @return the bulkThreshold
     */
    public int getBulkThreshold() {
        return bulkThreshold;
    }

    /**
     * @param bulkThreshold the bulkThreshold to set
     */
    public void setBulkThreshold(int bulkThreshold) throws EamDbException {
        if (bulkThreshold > 0) {
            this.bulkThreshold = bulkThreshold;
        } else {
            throw new EamDbException("Error invalid bulk threshold for database connection."); // NON-NLS
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
    public void setUserName(String userName) throws EamDbException {
        if (userName != null && !userName.isEmpty()) {
            this.userName = userName;
        } else {
            throw new EamDbException("Error invalid user name for database connection. Cannot be null or empty."); // NON-NLS
        }
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
    public void setPassword(String password) throws EamDbException {
        if (password != null && !password.isEmpty()) {
            this.password = password;
        } else {
            throw new EamDbException("Error invalid user password for database connection. Cannot be null or empty."); // NON-NLS
        }
    }

    /**
     * @return the badTags
     */
    public List<String> getBadTags() {
        return badTags;
    }

    /**
     * @param badTags the badTags to set
     */
    public void setBadTags(List<String> badTags) {
        this.badTags = badTags;
    }

    /**
     * @return the VALIDATION_QUERY
     */
    public String getValidationQuery() {
        return VALIDATION_QUERY;
    }

    /**
     * @return the POSTGRES_DRIVER
     */
    public String getDriver() {
        return JDBC_DRIVER;
    }

    /**
     * @return the JDBC_BASE_URI
     */
    public String getJDBCBaseURI() {
        return JDBC_BASE_URI;
    }

}
