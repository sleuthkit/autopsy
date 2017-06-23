/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Settings for the sqlite implementation of the Central Repository database
 */
public final class SqliteEamDbSettings {

    private final static Logger LOGGER = Logger.getLogger(SqliteEamDbSettings.class.getName());
    private final String DEFAULT_DBNAME = "CentralRepository.db"; // NON-NLS
    private final String DEFAULT_DBDIRECTORY = PlatformUtil.getUserDirectory() + File.separator + "central_repository"; // NON-NLS
    private final int DEFAULT_BULK_THRESHHOLD = 1000;
    private final String DEFAULT_BAD_TAGS = "Evidence"; // NON-NLS
    private final String JDBC_DRIVER = "org.sqlite.JDBC"; // NON-NLS
    private final String JDBC_BASE_URI = "jdbc:sqlite:"; // NON-NLS
    private final String VALIDATION_QUERY = "SELECT count(*) from sqlite_master"; // NON-NLS
    private static final String PRAGMA_SYNC_OFF = "PRAGMA synchronous = OFF";
    private static final String PRAGMA_SYNC_NORMAL = "PRAGMA synchronous = NORMAL";
    private static final String PRAGMA_JOURNAL_WAL = "PRAGMA journal_mode = WAL";
    private static final String PRAGMA_READ_UNCOMMITTED_TRUE = "PRAGMA read_uncommitted = True";
    private static final String PRAGMA_ENCODING_UTF8 = "PRAGMA encoding = 'UTF-8'";
    private static final String PRAGMA_PAGE_SIZE_4096 = "PRAGMA page_size = 4096";
    private static final String PRAGMA_FOREIGN_KEYS_ON = "PRAGMA foreign_keys = ON";
    private final String DB_NAMES_REGEX = "[a-zA-Z]\\w*(\\.db)?";
    private String dbName;
    private String dbDirectory;
    private int bulkThreshold;
    private List<String> badTags;

    public SqliteEamDbSettings() {
        loadSettings();
    }

    public void loadSettings() {
        dbName = ModuleSettings.getConfigSetting("CentralRepository", "db.sqlite.dbName"); // NON-NLS
        if (dbName == null || dbName.isEmpty()) {
            dbName = DEFAULT_DBNAME;
        }

        dbDirectory = ModuleSettings.getConfigSetting("CentralRepository", "db.sqlite.dbDirectory"); // NON-NLS
        if (dbDirectory == null || dbDirectory.isEmpty()) {
            dbDirectory = DEFAULT_DBDIRECTORY;
        }

        try {
            String bulkThresholdString = ModuleSettings.getConfigSetting("CentralRepository", "db.sqlite.bulkThreshold"); // NON-NLS
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

        String badTagsStr = ModuleSettings.getConfigSetting("CentralRepository", "db.badTags"); // NON-NLS
        if (badTagsStr == null || badTagsStr.isEmpty()) {
            badTagsStr = DEFAULT_BAD_TAGS;
        }
        badTags = new ArrayList<>(Arrays.asList(badTagsStr.split(",")));
    }

    public void saveSettings() {
        createDbDirectory();

        ModuleSettings.setConfigSetting("CentralRepository", "db.sqlite.dbName", getDbName()); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.sqlite.dbDirectory", getDbDirectory()); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.sqlite.bulkThreshold", Integer.toString(getBulkThreshold())); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.badTags", String.join(",", badTags)); // NON-NLS
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
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to create sqlite database directory.", ex); // NON-NLS
                return false;
            }
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

    /**
     * Use the current settings to get an ephemeral client connection for testing.
     * 
     * If the directory path does not exist, it will return null.
     * 
     * @return Connection or null.
     */
    private Connection getEphemeralConnection() {
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
     * Use the current settings and the validation query 
     * to test the connection to the database.
     * 
     * @return true if successfull connection, else false.
     */
    public boolean verifyConnection() {
        Connection conn = getEphemeralConnection();
        if (null == conn) {
            return false;
        }
        
        boolean result = EamDbUtil.executeValidationQuery(conn, VALIDATION_QUERY);
        EamDbUtil.closeConnection(conn);
        return result;
    }

    /**
     * Use the current settings and the schema version query 
     * to test the database schema.
     * 
     * @return true if successfull connection, else false.
     */
    public boolean verifyDatabaseSchema() {
        Connection conn = getEphemeralConnection();
        if (null == conn) {
            return false;
        }

        boolean result = EamDbUtil.schemaVersionIsSet(conn);
        EamDbUtil.closeConnection(conn);
        return result;
    }

    /**
     * Initialize the database schema.
     *
     * Requires valid connectionPool.
     *
     * This method is called from within connect(), so we cannot call connect()
     * to get a connection. This method is called after setupConnectionPool(),
     * so it is safe to assume that a valid connectionPool exists. The
     * implementation of connect() is synchronized, so we can safely use the
     * connectionPool object directly.
     */
    public boolean initializeDatabaseSchema() {
        // The "id" column is an alias for the built-in 64-bit int "rowid" column.
        // It is autoincrementing by default and must be of type "integer primary key".
        // We've omitted the autoincrement argument because we are not currently
        // using the id value to search for specific rows, so we do not care
        // if a rowid is re-used after an existing rows was previously deleted.
        StringBuilder createOrganizationsTable = new StringBuilder();
        createOrganizationsTable.append("CREATE TABLE IF NOT EXISTS organizations (");
        createOrganizationsTable.append("id integer primary key autoincrement NOT NULL,");
        createOrganizationsTable.append("org_name text NOT NULL,");
        createOrganizationsTable.append("poc_name text NOT NULL,");
        createOrganizationsTable.append("poc_email text NOT NULL,");
        createOrganizationsTable.append("poc_phone text NOT NULL");
        createOrganizationsTable.append(")");

        // NOTE: The organizations will only have a small number of rows, so
        // an index is probably not worthwhile.

        StringBuilder createCasesTable = new StringBuilder();
        createCasesTable.append("CREATE TABLE IF NOT EXISTS cases (");
        createCasesTable.append("id integer primary key autoincrement NOT NULL,");
        createCasesTable.append("case_uid text NOT NULL,");
        createCasesTable.append("org_id integer,");
        createCasesTable.append("case_name text NOT NULL,");
        createCasesTable.append("creation_date text NOT NULL,");
        createCasesTable.append("case_number text NOT NULL,");
        createCasesTable.append("examiner_name text NOT NULL,");
        createCasesTable.append("examiner_email text NOT NULL,");
        createCasesTable.append("examiner_phone text NOT NULL,");
        createCasesTable.append("notes text NOT NULL,");
        createCasesTable.append("foreign key (org_id) references organizations(id) on update set null on delete set null,");
        createCasesTable.append("CONSTRAINT case_uid_unique UNIQUE(case_uid)");
        createCasesTable.append(")");

        // NOTE: when there are few cases in the cases table, these indices may not be worthwhile
        String casesIdx1 = "CREATE INDEX IF NOT EXISTS cases_org_id ON cases (org_id)";
        String casesIdx2 = "CREATE INDEX IF NOT EXISTS cases_case_uid ON cases (case_uid)";

        StringBuilder createDataSourcesTable = new StringBuilder();
        createDataSourcesTable.append("CREATE TABLE IF NOT EXISTS data_sources (");
        createDataSourcesTable.append("id integer primary key autoincrement NOT NULL,");
        createDataSourcesTable.append("device_id text NOT NULL,");
        createDataSourcesTable.append("name text NOT NULL,");
        createDataSourcesTable.append("CONSTRAINT device_id_unique UNIQUE(device_id)");
        createDataSourcesTable.append(")");

        String dataSourceIdx1 = "CREATE INDEX IF NOT EXISTS data_sources_name ON data_sources (name)";

        StringBuilder createGlobalReferenceSetsTable = new StringBuilder();
        createGlobalReferenceSetsTable.append("CREATE TABLE IF NOT EXISTS global_reference_sets (");
        createGlobalReferenceSetsTable.append("id integer primary key autoincrement NOT NULL,");
        createGlobalReferenceSetsTable.append("org_id integer,");
        createGlobalReferenceSetsTable.append("set_name text NOT NULL,");
        createGlobalReferenceSetsTable.append("version text NOT NULL,");
        createGlobalReferenceSetsTable.append("import_date text NOT NULL,");
        createGlobalReferenceSetsTable.append("foreign key (org_id) references organizations(id) on update set null on delete set null");
        createGlobalReferenceSetsTable.append(")");

        String globalReferenceSetsIdx1 = "CREATE INDEX IF NOT EXISTS global_reference_sets_org_id ON global_reference_sets (org_id)";

        StringBuilder createGlobalFilesTable = new StringBuilder();
        createGlobalFilesTable.append("CREATE TABLE IF NOT EXISTS global_files (");
        createGlobalFilesTable.append("id integer primary key autoincrement NOT NULL,");
        createGlobalFilesTable.append("global_reference_set_id integer,");
        createGlobalFilesTable.append("value text NOT NULL,");
        createGlobalFilesTable.append("known_status text NOT NULL,");
        createGlobalFilesTable.append("comment text NOT NULL,");
        createGlobalFilesTable.append("CONSTRAINT global_files_multi_unique UNIQUE(global_reference_set_id, value)");
        createGlobalFilesTable.append("foreign key (global_reference_set_id) references global_reference_sets(id) on update set null on delete set null");
        createGlobalFilesTable.append(")");

        String globalFilesIdx1 = "CREATE INDEX IF NOT EXISTS global_files_value ON global_files (value)";
        String globalFilesIdx2 = "CREATE INDEX IF NOT EXISTS global_files_value_known_status ON global_files (value, known_status)";

        StringBuilder createArtifactTypesTable = new StringBuilder();
        createArtifactTypesTable.append("CREATE TABLE IF NOT EXISTS artifact_types (");
        createArtifactTypesTable.append("id integer primary key autoincrement NOT NULL,");
        createArtifactTypesTable.append("name text NOT NULL,");
        createArtifactTypesTable.append("supported integer NOT NULL,");
        createArtifactTypesTable.append("enabled integer NOT NULL,");
        createArtifactTypesTable.append("CONSTRAINT artifact_type_name_unique UNIQUE (name)");
        createArtifactTypesTable.append(")");

        // NOTE: there are API methods that query by one of: name, supported, or enabled.
        // Only name is currently implemented, but, there will only be a small number
        // of artifact_types, so there is no benefit to having any indices.
        StringBuilder createArtifactInstancesTableTemplate = new StringBuilder();
        createArtifactInstancesTableTemplate.append("CREATE TABLE IF NOT EXISTS %s_instances (");
        createArtifactInstancesTableTemplate.append("id integer primary key autoincrement NOT NULL,");
        createArtifactInstancesTableTemplate.append("case_id integer,");
        createArtifactInstancesTableTemplate.append("data_source_id integer,");
        createArtifactInstancesTableTemplate.append("value text NOT NULL,");
        createArtifactInstancesTableTemplate.append("file_path text NOT NULL,");
        createArtifactInstancesTableTemplate.append("known_status text NOT NULL,");
        createArtifactInstancesTableTemplate.append("comment text NOT NULL,");
        createArtifactInstancesTableTemplate.append("CONSTRAINT %s_instances_multi_unique UNIQUE(case_id, data_source_id, value, file_path),");
        createArtifactInstancesTableTemplate.append("foreign key (case_id) references cases(id) on update set null on delete set null,");
        createArtifactInstancesTableTemplate.append("foreign key (data_source_id) references data_sources(id) on update set null on delete set null");
        createArtifactInstancesTableTemplate.append(")");

        // TODO: do we need any more indices?
        String instancesIdx1 = "CREATE INDEX IF NOT EXISTS %s_instances_case_id ON %s_instances (case_id)";
        String instancesIdx2 = "CREATE INDEX IF NOT EXISTS %s_instances_data_source_id ON %s_instances (data_source_id)";
        String instancesIdx3 = "CREATE INDEX IF NOT EXISTS %s_instances_value ON %s_instances (value)";
        String instancesIdx4 = "CREATE INDEX IF NOT EXISTS %s_instances_value_known_status ON %s_instances (value, known_status)";

        StringBuilder createDbInfoTable = new StringBuilder();
        createDbInfoTable.append("CREATE TABLE IF NOT EXISTS db_info (");
        createDbInfoTable.append("id integer primary key NOT NULL,");
        createDbInfoTable.append("name text NOT NULL,");
        createDbInfoTable.append("value text NOT NULL");
        createDbInfoTable.append(")");

        // NOTE: the db_info table currenly only has 1 row, so having an index
        // provides no benefit.

        Connection conn = null;
        try {
            conn = getEphemeralConnection();
            if (null == conn) {
                return false;
            }
            Statement stmt = conn.createStatement();
            stmt.execute(PRAGMA_JOURNAL_WAL);
            stmt.execute(PRAGMA_SYNC_OFF);
            stmt.execute(PRAGMA_READ_UNCOMMITTED_TRUE);
            stmt.execute(PRAGMA_ENCODING_UTF8);
            stmt.execute(PRAGMA_PAGE_SIZE_4096);
            stmt.execute(PRAGMA_FOREIGN_KEYS_ON);

            stmt.execute(createOrganizationsTable.toString());

            stmt.execute(createCasesTable.toString());
            stmt.execute(casesIdx1);
            stmt.execute(casesIdx2);

            stmt.execute(createDataSourcesTable.toString());
            stmt.execute(dataSourceIdx1);

            stmt.execute(createGlobalReferenceSetsTable.toString());
            stmt.execute(globalReferenceSetsIdx1);

            stmt.execute(createGlobalFilesTable.toString());
            stmt.execute(globalFilesIdx1);
            stmt.execute(globalFilesIdx2);

            stmt.execute(createArtifactTypesTable.toString());

            stmt.execute(createDbInfoTable.toString());

            // Create a separate table for each artifact type
            List<EamArtifact.Type> DEFAULT_ARTIFACT_TYPES = EamArtifact.getDefaultArtifactTypes();

            String type_name;
            for (EamArtifact.Type type : DEFAULT_ARTIFACT_TYPES) {
                type_name = type.getName();
                stmt.execute(String.format(createArtifactInstancesTableTemplate.toString(), type_name, type_name));
                stmt.execute(String.format(instancesIdx1, type_name, type_name));
                stmt.execute(String.format(instancesIdx2, type_name, type_name));
                stmt.execute(String.format(instancesIdx3, type_name, type_name));
                stmt.execute(String.format(instancesIdx4, type_name, type_name));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error initializing db schema.", ex); // NON-NLS
            return false;
        } finally {
            EamDbUtil.closeConnection(conn);
        }
        return true;
    }

    public boolean insertDefaultDatabaseContent() {
        Connection conn = getEphemeralConnection();
        if (null == conn) {
            return false;
        }

        boolean result = EamDbUtil.insertDefaultArtifactTypes(conn)
                && EamDbUtil.insertSchemaVersion(conn);
        EamDbUtil.closeConnection(conn);
        return result;
    }
    
    public boolean isChanged() {
        String dbNameString = ModuleSettings.getConfigSetting("CentralRepository", "db.sqlite.dbName"); // NON-NLS
        String dbDirectoryString = ModuleSettings.getConfigSetting("CentralRepository", "db.sqlite.dbDirectory"); // NON-NLS
        String bulkThresholdString = ModuleSettings.getConfigSetting("CentralRepository", "db.sqlite.bulkThreshold"); // NON-NLS

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
    public void setDbName(String dbName) throws EamDbException {
        if (dbName == null || dbName.isEmpty()) {
            throw new EamDbException("Invalid database file name. Cannot be null or empty."); // NON-NLS
        } else if (!Pattern.matches(DB_NAMES_REGEX, dbName)) {
            throw new EamDbException("Invalid database file name. Name must start with a letter and can only contain letters, numbers, and '_'."); // NON-NLS
        }

        this.dbName = dbName;
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
            throw new EamDbException("Invalid bulk threshold."); // NON-NLS
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
            throw new EamDbException("Invalid directory for sqlite database. Cannot empty"); // NON-NLS
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
