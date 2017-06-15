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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Settings for the sqlite implementation of the enterprise artifacts manager database
 */
public final class SqliteEamDbSettings {

    private final static Logger LOGGER = Logger.getLogger(SqliteEamDbSettings.class.getName());
    private final String DEFAULT_DBNAME = "EnterpriseArtifacts.db"; // NON-NLS
    private final String DEFAULT_DBDIRECTORY = PlatformUtil.getUserDirectory() + File.separator + "enterprise_artifacts_manager"; // NON-NLS
    private final int DEFAULT_BULK_THRESHHOLD = 1000;
    private final String DEFAULT_BAD_TAGS = "Evidence"; // NON-NLS
    private final String JDBC_DRIVER = "org.sqlite.JDBC"; // NON-NLS
    private final String JDBC_BASE_URI = "jdbc:sqlite:"; // NON-NLS
    private final String VALIDATION_QUERY = "SELECT count(*) from sqlite_master"; // NON-NLS

    private boolean enabled;
    private String dbName;
    private String dbDirectory;
    private int bulkThreshold;
    private List<String> badTags;

    public SqliteEamDbSettings() {
        loadSettings();
    }

    public void loadSettings() {
        enabled = Boolean.valueOf(ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.enabled")); // NON-NLS

        dbName = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.sqlite.dbName"); // NON-NLS
        if (dbName == null || dbName.isEmpty()) {
            dbName = DEFAULT_DBNAME;
        }

        dbDirectory = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.sqlite.dbDirectory"); // NON-NLS
        if (dbDirectory == null || dbDirectory.isEmpty()) {
            dbDirectory = DEFAULT_DBDIRECTORY;
        }

        try {
            String bulkThresholdString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.sqlite.bulkThreshold"); // NON-NLS
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

        String badTagsStr = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.badTags"); // NON-NLS
        if (badTagsStr == null || badTagsStr.isEmpty()) {
            badTagsStr = DEFAULT_BAD_TAGS;
        }
        badTags = new ArrayList<>(Arrays.asList(badTagsStr.split(",")));
    }

    public void saveSettings() {
        createAndVerifyDirectory();

        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.enabled", Boolean.toString(isEnabled())); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.sqlite.dbName", getDbName()); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.sqlite.dbDirectory", getDbDirectory()); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.sqlite.bulkThreshold", Integer.toString(getBulkThreshold())); // NON-NLS
        ModuleSettings.setConfigSetting("EnterpriseArtifactsManager", "db.badTags", String.join(",", badTags)); // NON-NLS
    }

    public boolean createAndVerifyDirectory() {
        // Ensure dbDirectory is a valid directory
        File dbDir = new File(getDbDirectory());
        if (!dbDir.exists()) {
            LOGGER.log(Level.INFO, "sqlite directory does not exist, creating it at {0}.", getDbDirectory()); // NON-NLS
            try {
                Files.createDirectories(dbDir.toPath());
            } catch (IOException ex) {
                LOGGER.log(Level.INFO, "Failed to create sqlite database directory.", ex); // NON-NLS
                return false;
            }
        } else if (dbDir.exists() && !dbDir.isDirectory()) {
            LOGGER.log(Level.INFO, "Failed to create sqlite database directory. Path already exists and is not a directory."); // NON-NLS
            return false;
        }

        return true;
    }

    /**
     * Get the full connection URL as a String
     *
     * @return
     */
    public String getConnectionURL() {
        StringBuilder url = new StringBuilder();
        url.append(getJDBCBaseURI());
        url.append(getFileNameWithPath());

        return url.toString();
    }

    public boolean testSettings() {
        if (!createAndVerifyDirectory()) {
            return false;
        }

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
                LOGGER.log(Level.INFO, "Testing connection to sqlite success."); // NON-NLS
            }
        } catch (ClassNotFoundException | SQLException ex) {
            LOGGER.log(Level.INFO, "Testing connection to sqlite failed.", ex); // NON-NLS
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
        String dbNameString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.sqlite.dbName"); // NON-NLS
        String dbDirectoryString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.sqlite.dbDirectory"); // NON-NLS
        String bulkThresholdString = ModuleSettings.getConfigSetting("EnterpriseArtifactsManager", "db.sqlite.bulkThreshold"); // NON-NLS

        return !dbName.equals(dbNameString)
                || !dbDirectory.equals(dbDirectoryString)
                || !Integer.toString(bulkThreshold).equals(bulkThresholdString);
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
     * @return the dbName
     */
    public String getDbName() {
        return dbName;
    }

    /**
     * Name of the sqlite db file.
     *
     * @param dbName the dbName to set
     */
    public void setDbName(String dbName) throws EamDbException {
        if (dbName != null && !dbName.isEmpty()) {
            this.dbName = dbName;
        } else {
            throw new EamDbException("Error invalid file name for database. Cannot be null or empty."); // NON-NLS
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
     * @return the dbDirectory
     */
    public String getDbDirectory() {
        return dbDirectory;
    }

    /**
     * Path for directory to hold the sqlite database.
     *
     * User must have WRITE permission to this directory.
     *
     * @param dbDirectory the dbDirectory to set
     */
    public void setDbDirectory(String dbDirectory) throws EamDbException {
        if (dbDirectory != null && !dbDirectory.isEmpty()) {
            this.dbDirectory = dbDirectory;
        } else {
            throw new EamDbException("Error invalid directory for sqlite database. Cannot be null or empty"); // NON-NLS
        }
    }

    /**
     * Join the DbDirectory and the DbName into a full path.
     *
     * @return
     */
    public String getFileNameWithPath() {
        return getDbDirectory() + File.separator + getDbName();
    }

    /**
     * @return the DRIVER
     */
    public String getDriver() {
        return JDBC_DRIVER;
    }

    /**
     * @return the VALIDATION_QUERY
     */
    public String getValidationQuery() {
        return VALIDATION_QUERY;
    }

    /**
     * @return the JDBC_BASE_URI
     */
    public String getJDBCBaseURI() {
        return JDBC_BASE_URI;
    }

}
