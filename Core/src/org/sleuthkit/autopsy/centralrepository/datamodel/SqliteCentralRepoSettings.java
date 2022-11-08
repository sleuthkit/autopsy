/*
 * Central Repository
 *
 * Copyright 2015-2020 Basis Technology Corp.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.centralrepository.CentralRepoSettings;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Settings for the sqlite implementation of the Central Repository database
 *
 * NOTE: This is public scope because the options panel calls it directly to
 * set/get
 */
public final class SqliteCentralRepoSettings implements CentralRepoDbConnectivityManager {

    public final static String DEFAULT_DBNAME = CentralRepoSettings.getInstance().getDefaultDbName(); // NON-NLS
    private final static Logger LOGGER = Logger.getLogger(SqliteCentralRepoSettings.class.getName());
    private final Path userConfigDir = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath());
    private final static String DEFAULT_DBDIRECTORY = CentralRepoSettings.getInstance().getDefaultDbPath();
    
    //property names
    private static final String PROFILE_NAME = CentralRepoSettings.getInstance().getModuleSettingsKey();
    private static final String DATABASE_NAME = CentralRepoSettings.getInstance().getDatabaseNameKey(); //NON-NLS
    private static final String DATABASE_PATH = CentralRepoSettings.getInstance().getDatabasePathKey(); //NON-NLS
    private static final String BULK_THRESHOLD = "db.sqlite.bulkThreshold"; //NON-NLS
    
    private final static String JDBC_DRIVER = "org.sqlite.JDBC"; // NON-NLS
    private final static String JDBC_BASE_URI = "jdbc:sqlite:"; // NON-NLS
    private final static String VALIDATION_QUERY = "SELECT count(*) from sqlite_master"; // NON-NLS

    private final static String DB_NAMES_REGEX = "[a-z][a-z0-9_]*(\\.db)?";
    private String dbName;
    private String dbDirectory;
    private int bulkThreshold;

    public SqliteCentralRepoSettings() {
        loadSettings();
    }

    public void loadSettings() {
        dbName = ModuleSettings.getConfigSetting(PROFILE_NAME, DATABASE_NAME); // NON-NLS
        if (dbName == null || dbName.isEmpty()) {
            dbName = DEFAULT_DBNAME;
        }

        dbDirectory = readDbPath(); // NON-NLS
        if (dbDirectory == null || dbDirectory.isEmpty()) {
            dbDirectory = DEFAULT_DBDIRECTORY;
        }

        try {
            String bulkThresholdString = ModuleSettings.getConfigSetting(PROFILE_NAME, BULK_THRESHOLD); // NON-NLS
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
    }

    public String toString() {
        return String.format("SqliteCentralRepoSettings: [db type: sqlite, directory: %s, name: %s]", getDbDirectory(), getDbName());
    }

    /**
     * sets database directory and name to defaults
     */
    public void setupDefaultSettings() {
        dbName = DEFAULT_DBNAME;
        dbDirectory = DEFAULT_DBDIRECTORY;
    }

    public void saveSettings() {
        createDbDirectory();

        ModuleSettings.setConfigSetting(PROFILE_NAME, DATABASE_NAME, getDbName()); // NON-NLS
        saveDbPath(getDbDirectory()); // NON-NLS
        ModuleSettings.setConfigSetting(PROFILE_NAME, BULK_THRESHOLD, Integer.toString(getBulkThreshold())); // NON-NLS
    }

    /**
     * Save CR database path. If the path is inside user directory (e.g.
     * "C:\Users\USER_NAME\AppData\Roaming\autopsy"), trim that off and save it
     * as a relative path (i.e it will not start with a “/” or drive letter). Otherwise,
     * full path is saved. See JIRA-7348.
     *
     * @param fullPath Full path to the SQLite db file.
     */    
    private void saveDbPath(String fullPath) {
        Path relativePath = Paths.get(fullPath);
        // check if the path is within user directory
        if (Paths.get(fullPath).startsWith(userConfigDir)) {
            // relativize the path
            relativePath = userConfigDir.relativize(relativePath);
        }
        // Use properties to persist the logo to use.
        ModuleSettings.setConfigSetting(PROFILE_NAME, DATABASE_PATH, relativePath.toString());        
    }
    
     /**
     * Read CD database path from preferences file. Reverses the path relativization performed 
     * in saveDbPath(). If the stored path starts with either “/” or drive letter, 
     * it is a full path, and is returned to the caller. Otherwise, append current user 
     * directory to the saved relative path. See JIRA-7348.
     *
     * @return Full path to the SQLite CR database file.
     */
    private String readDbPath() {

        String curPath = ModuleSettings.getConfigSetting(PROFILE_NAME, DATABASE_PATH);
        

        //if has been set, validate it's correct, if not set, return null
        if (curPath != null && !curPath.isEmpty()) {
            
            // check if the path is an absolute path (starts with either drive letter or "/")            
            Path driveLetterOrNetwork = Paths.get(curPath).getRoot();            
            if (driveLetterOrNetwork != null) {
                // absolute path
                return curPath;
            }
            
            // Path is a relative path. Reverse path relativization performed in saveDbPath() 
            Path absolutePath = userConfigDir.resolve(curPath);
            curPath = absolutePath.toString();
            if (new File(curPath).canRead() == false) {
                //use default
                LOGGER.log(Level.INFO, "Path to SQLite Central Repository database is not valid: {0}", curPath); //NON-NLS
                curPath = null;
            }
        }

        return curPath;        
    }

    /**
     * Verify that the db file exists.
     *
     * @return true if exists, else false
     */
    public boolean dbFileExists() {
        File dbFile = new File(getFileNameWithPath());
        if (!dbFile.exists()) {
            return false;
        }
        // It's unlikely, but make sure the file isn't actually a directory
        return (!dbFile.isDirectory());
    }

    @Override
    public boolean verifyDatabaseExists() {
        return dbDirectoryExists();
    }

    /**
     * Verify that the db directory path exists.
     *
     * @return true if exists, else false
     */
    public boolean dbDirectoryExists() {
        // Ensure dbDirectory is a valid directory
        File dbDir = new File(getDbDirectory());

        if (!dbDir.exists()) {
            return false;
        } else if (!dbDir.isDirectory()) {
            return false;
        }

        return true;

    }

    /**
     * creates database directory for sqlite database if it does not exist
     *
     * @return whether or not operation occurred successfully
     */
    @Override
    public boolean createDatabase() {
        return createDbDirectory();
    }

    /**
     * Create the db directory if it does not exist.
     *
     * @return true is successfully created or already exists, else false
     */
    public boolean createDbDirectory() {
        if (!dbDirectoryExists()) {
            try {
                File dbDir = new File(getDbDirectory());
                Files.createDirectories(dbDir.toPath());
                LOGGER.log(Level.INFO, "sqlite directory did not exist, created it at {0}.", getDbDirectory()); // NON-NLS
            } catch (IOException | InvalidPathException | SecurityException ex) {
                LOGGER.log(Level.SEVERE, "Failed to create sqlite database directory.", ex); // NON-NLS
                return false;
            }
        }

        return true;
    }

    /**
     * Delete the database
     *
     * @return
     */
    public boolean deleteDatabase() {
        File dbFile = new File(this.getFileNameWithPath());
        return dbFile.delete();
    }

    /**
     * Get the full connection URL as a String
     *
     * @return
     */
    String getConnectionURL() {
        StringBuilder url = new StringBuilder();
        url.append(getJDBCBaseURI());
        url.append(getFileNameWithPath());

        return url.toString();
    }

    /**
     * Use the current settings to get an ephemeral client connection for
     * testing.
     *
     * If the directory path does not exist, it will return null.
     *
     * @return Connection or null.
     */
    Connection getEphemeralConnection() {
        if (!dbDirectoryExists()) {
            return null;
        }

        Connection conn;
        try {
            String url = getConnectionURL();
            Class.forName(getDriver());
            conn = DriverManager.getConnection(url);
        } catch (ClassNotFoundException | SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to acquire ephemeral connection to sqlite.", ex); // NON-NLS
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
    public boolean verifyConnection() {
        Connection conn = getEphemeralConnection();
        if (null == conn) {
            return false;
        }

        boolean result = CentralRepoDbUtil.executeValidationQuery(conn, VALIDATION_QUERY);
        CentralRepoDbUtil.closeConnection(conn);
        return result;
    }

    /**
     * Use the current settings and the schema version query to test the
     * database schema.
     *
     * @return true if successfull connection, else false.
     */
    public boolean verifyDatabaseSchema() {
        Connection conn = getEphemeralConnection();
        if (null == conn) {
            return false;
        }

        boolean result = CentralRepoDbUtil.schemaVersionIsSet(conn);
        CentralRepoDbUtil.closeConnection(conn);
        return result;
    }

    boolean isChanged() {
        String dbNameString = ModuleSettings.getConfigSetting(PROFILE_NAME, DATABASE_NAME); // NON-NLS
        String dbDirectoryString = readDbPath(); // NON-NLS
        String bulkThresholdString = ModuleSettings.getConfigSetting(PROFILE_NAME, BULK_THRESHOLD); // NON-NLS

        return !dbName.equals(dbNameString)
                || !dbDirectory.equals(dbDirectoryString)
                || !Integer.toString(bulkThreshold).equals(bulkThresholdString);
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
    public void setDbName(String dbName) throws CentralRepoException {
        if (dbName == null || dbName.isEmpty()) {
            throw new CentralRepoException("Invalid database file name. Cannot be null or empty."); // NON-NLS
        } else if (!Pattern.matches(DB_NAMES_REGEX, dbName)) {
            throw new CentralRepoException("Invalid database file name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."); // NON-NLS
        }

        this.dbName = dbName;
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
    void setBulkThreshold(int bulkThreshold) throws CentralRepoException {
        if (bulkThreshold > 0) {
            this.bulkThreshold = bulkThreshold;
        } else {
            throw new CentralRepoException("Invalid bulk threshold."); // NON-NLS
        }
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
    public void setDbDirectory(String dbDirectory) throws CentralRepoException {
        if (dbDirectory != null && !dbDirectory.isEmpty()) {
            this.dbDirectory = dbDirectory;
        } else {
            throw new CentralRepoException("Invalid directory for sqlite database. Cannot empty"); // NON-NLS
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
    String getDriver() {
        return JDBC_DRIVER;
    }

    /**
     * @return the VALIDATION_QUERY
     */
    String getValidationQuery() {
        return VALIDATION_QUERY;
    }

    /**
     * @return the JDBC_BASE_URI
     */
    String getJDBCBaseURI() {
        return JDBC_BASE_URI;
    }

    @Override
    public DatabaseTestResult testStatus() {
        if (dbFileExists()) {
            if (verifyConnection()) {
                if (verifyDatabaseSchema()) {
                    return DatabaseTestResult.TESTED_OK;
                } else {
                    return DatabaseTestResult.SCHEMA_INVALID;
                }
            } else {
                return DatabaseTestResult.SCHEMA_INVALID;
            }
        } else {
            return DatabaseTestResult.DB_DOES_NOT_EXIST;
        }
    }
}
