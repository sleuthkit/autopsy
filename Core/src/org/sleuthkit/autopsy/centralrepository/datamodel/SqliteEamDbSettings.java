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
import java.nio.file.InvalidPathException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Settings for the sqlite implementation of the Central Repository database
 *
 * NOTE: This is public scope because the options panel calls it directly to
 * set/get
 */
public final class SqliteEamDbSettings {

    private final static Logger LOGGER = Logger.getLogger(SqliteEamDbSettings.class.getName());
    private final String DEFAULT_DBNAME = "central_repository.db"; // NON-NLS
    private final String DEFAULT_DBDIRECTORY = PlatformUtil.getUserDirectory() + File.separator + "central_repository"; // NON-NLS
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
    private final String DB_NAMES_REGEX = "[a-z][a-z0-9_]*(\\.db)?";
    private String dbName;
    private String dbDirectory;
    private int bulkThreshold;

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
                this.bulkThreshold = AbstractSqlEamDb.DEFAULT_BULK_THRESHHOLD;
            } else {
                this.bulkThreshold = Integer.parseInt(bulkThresholdString);
                if (getBulkThreshold() <= 0) {
                    this.bulkThreshold = AbstractSqlEamDb.DEFAULT_BULK_THRESHHOLD;
                }
            }
        } catch (NumberFormatException ex) {
            this.bulkThreshold = AbstractSqlEamDb.DEFAULT_BULK_THRESHHOLD;
        }
    }

    public void saveSettings() {
        createDbDirectory();

        ModuleSettings.setConfigSetting("CentralRepository", "db.sqlite.dbName", getDbName()); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.sqlite.dbDirectory", getDbDirectory()); // NON-NLS
        ModuleSettings.setConfigSetting("CentralRepository", "db.sqlite.bulkThreshold", Integer.toString(getBulkThreshold())); // NON-NLS
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

        boolean result = EamDbUtil.executeValidationQuery(conn, VALIDATION_QUERY);
        EamDbUtil.closeConnection(conn);
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
        createOrganizationsTable.append("poc_phone text NOT NULL,");
        createOrganizationsTable.append("CONSTRAINT org_name_unique UNIQUE (org_name)");
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
        createCasesTable.append("case_number text,");
        createCasesTable.append("examiner_name text,");
        createCasesTable.append("examiner_email text,");
        createCasesTable.append("examiner_phone text,");
        createCasesTable.append("notes text,");
        createCasesTable.append("CONSTRAINT case_uid_unique UNIQUE(case_uid) ON CONFLICT IGNORE,");
        createCasesTable.append("foreign key (org_id) references organizations(id) ON UPDATE SET NULL ON DELETE SET NULL");
        createCasesTable.append(")");

        // NOTE: when there are few cases in the cases table, these indices may not be worthwhile
        String casesIdx1 = "CREATE INDEX IF NOT EXISTS cases_org_id ON cases (org_id)";
        String casesIdx2 = "CREATE INDEX IF NOT EXISTS cases_case_uid ON cases (case_uid)";

        StringBuilder createDataSourcesTable = new StringBuilder();
        createDataSourcesTable.append("CREATE TABLE IF NOT EXISTS data_sources (");
        createDataSourcesTable.append("id integer primary key autoincrement NOT NULL,");
        createDataSourcesTable.append("case_id integer NOT NULL,");
        createDataSourcesTable.append("device_id text NOT NULL,");
        createDataSourcesTable.append("name text NOT NULL,");
        createDataSourcesTable.append("foreign key (case_id) references cases(id) ON UPDATE SET NULL ON DELETE SET NULL,");
        createDataSourcesTable.append("CONSTRAINT datasource_unique UNIQUE (case_id, device_id, name)");
        createDataSourcesTable.append(")");

        String dataSourceIdx1 = "CREATE INDEX IF NOT EXISTS data_sources_name ON data_sources (name)";

        StringBuilder createReferenceSetsTable = new StringBuilder();
        createReferenceSetsTable.append("CREATE TABLE IF NOT EXISTS reference_sets (");
        createReferenceSetsTable.append("id integer primary key autoincrement NOT NULL,");
        createReferenceSetsTable.append("org_id integer NOT NULL,");
        createReferenceSetsTable.append("set_name text NOT NULL,");
        createReferenceSetsTable.append("version text NOT NULL,");
        createReferenceSetsTable.append("known_status integer NOT NULL,");
        createReferenceSetsTable.append("read_only boolean NOT NULL,");
        createReferenceSetsTable.append("type integer NOT NULL,");
        createReferenceSetsTable.append("import_date text NOT NULL,");
        createReferenceSetsTable.append("foreign key (org_id) references organizations(id) ON UPDATE SET NULL ON DELETE SET NULL,");
        createReferenceSetsTable.append("CONSTRAINT hash_set_unique UNIQUE (set_name, version)");
        createReferenceSetsTable.append(")");

        String referenceSetsIdx1 = "CREATE INDEX IF NOT EXISTS reference_sets_org_id ON reference_sets (org_id)";

        // Each "%s" will be replaced with the relevant reference_TYPE table name.
        StringBuilder createReferenceTypesTableTemplate = new StringBuilder();
        createReferenceTypesTableTemplate.append("CREATE TABLE IF NOT EXISTS %s (");
        createReferenceTypesTableTemplate.append("id integer primary key autoincrement NOT NULL,");
        createReferenceTypesTableTemplate.append("reference_set_id integer,");
        createReferenceTypesTableTemplate.append("value text NOT NULL,");
        createReferenceTypesTableTemplate.append("known_status integer NOT NULL,");
        createReferenceTypesTableTemplate.append("comment text,");
        createReferenceTypesTableTemplate.append("CONSTRAINT %s_multi_unique UNIQUE(reference_set_id, value) ON CONFLICT IGNORE,");
        createReferenceTypesTableTemplate.append("foreign key (reference_set_id) references reference_sets(id) ON UPDATE SET NULL ON DELETE SET NULL");
        createReferenceTypesTableTemplate.append(")");

        // Each "%s" will be replaced with the relevant reference_TYPE table name.
        String referenceTypesIdx1 = "CREATE INDEX IF NOT EXISTS %s_value ON %s (value)";
        String referenceTypesIdx2 = "CREATE INDEX IF NOT EXISTS %s_value_known_status ON %s (value, known_status)";

        StringBuilder createCorrelationTypesTable = new StringBuilder();
        createCorrelationTypesTable.append("CREATE TABLE IF NOT EXISTS correlation_types (");
        createCorrelationTypesTable.append("id integer primary key autoincrement NOT NULL,");
        createCorrelationTypesTable.append("display_name text NOT NULL,");
        createCorrelationTypesTable.append("db_table_name text NOT NULL,");
        createCorrelationTypesTable.append("supported integer NOT NULL,");
        createCorrelationTypesTable.append("enabled integer NOT NULL,");
        createCorrelationTypesTable.append("CONSTRAINT correlation_types_names UNIQUE (display_name, db_table_name)");
        createCorrelationTypesTable.append(")");

        String createArtifactInstancesTableTemplate = getCreateArtifactInstancesTableTemplate();

        String instancesCaseIdIdx = getAddCaseIdIndexTemplate();
        String instancesDatasourceIdIdx = getAddDataSourceIdIndexTemplate();
        String instancesValueIdx = getAddValueIndexTemplate();
        String instancesKnownStatusIdx = getAddKnownStatusIndexTemplate();
        String instancesObjectIdIdx = getAddObjectIdIndexTemplate();
        
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

            stmt.execute(createReferenceSetsTable.toString());
            stmt.execute(referenceSetsIdx1);

            stmt.execute(createCorrelationTypesTable.toString());

            stmt.execute(createDbInfoTable.toString());

            // Create a separate instance and reference table for each artifact type
            List<CorrelationAttributeInstance.Type> DEFAULT_CORRELATION_TYPES = CorrelationAttributeInstance.getDefaultCorrelationTypes();

            String reference_type_dbname;
            String instance_type_dbname;
            for (CorrelationAttributeInstance.Type type : DEFAULT_CORRELATION_TYPES) {
                reference_type_dbname = EamDbUtil.correlationTypeToReferenceTableName(type);
                instance_type_dbname = EamDbUtil.correlationTypeToInstanceTableName(type);

                stmt.execute(String.format(createArtifactInstancesTableTemplate, instance_type_dbname, instance_type_dbname));
                stmt.execute(String.format(instancesCaseIdIdx, instance_type_dbname, instance_type_dbname));
                stmt.execute(String.format(instancesDatasourceIdIdx, instance_type_dbname, instance_type_dbname));
                stmt.execute(String.format(instancesValueIdx, instance_type_dbname, instance_type_dbname));
                stmt.execute(String.format(instancesKnownStatusIdx, instance_type_dbname, instance_type_dbname));
                stmt.execute(String.format(instancesObjectIdIdx, instance_type_dbname, instance_type_dbname));

                // FUTURE: allow more than the FILES type
                if (type.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                    stmt.execute(String.format(createReferenceTypesTableTemplate.toString(), reference_type_dbname, reference_type_dbname));
                    stmt.execute(String.format(referenceTypesIdx1, reference_type_dbname, reference_type_dbname));
                    stmt.execute(String.format(referenceTypesIdx2, reference_type_dbname, reference_type_dbname));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error initializing db schema.", ex); // NON-NLS
            return false;
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting default correlation types. Likely due to one or more Type's with an invalid db table name."); // NON-NLS
            return false;
        } finally {
            EamDbUtil.closeConnection(conn);
        }
        return true;
    }
    
    /**
     * Get the template String for creating a new _instances table in a Sqlite
     * central repository. %s will exist in the template where the name of the
     * new table will be addedd.
     *
     * @return a String which is a template for cretating a new _instances table
     */
    static String getCreateArtifactInstancesTableTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        StringBuilder createArtifactInstancesTableTemplate = new StringBuilder();
        createArtifactInstancesTableTemplate.append("CREATE TABLE IF NOT EXISTS %s (");
        createArtifactInstancesTableTemplate.append("id integer primary key autoincrement NOT NULL,");
        createArtifactInstancesTableTemplate.append("case_id integer NOT NULL,");
        createArtifactInstancesTableTemplate.append("data_source_id integer NOT NULL,");
        createArtifactInstancesTableTemplate.append("value text NOT NULL,");
        createArtifactInstancesTableTemplate.append("file_path text NOT NULL,");
        createArtifactInstancesTableTemplate.append("known_status integer NOT NULL,");
        createArtifactInstancesTableTemplate.append("comment text,");
        createArtifactInstancesTableTemplate.append("file_obj_id integer,");
        createArtifactInstancesTableTemplate.append("CONSTRAINT %s_multi_unique UNIQUE(data_source_id, value, file_path) ON CONFLICT IGNORE,");
        createArtifactInstancesTableTemplate.append("foreign key (case_id) references cases(id) ON UPDATE SET NULL ON DELETE SET NULL,");
        createArtifactInstancesTableTemplate.append("foreign key (data_source_id) references data_sources(id) ON UPDATE SET NULL ON DELETE SET NULL");
        createArtifactInstancesTableTemplate.append(")");
        return createArtifactInstancesTableTemplate.toString();
    }

    /**
     * Get the template for creating an index on the case_id column of an
     * instance table. %s will exist in the template where the name of the new
     * table will be addedd.
     *
     * @return a String which is a template for adding an index to the case_id
     *         column of a _instances table
     */
    static String getAddCaseIdIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_case_id ON %s (case_id)";
    }

    /**
     * Get the template for creating an index on the data_source_id column of an
     * instance table. %s will exist in the template where the name of the new
     * table will be addedd.
     *
     * @return a String which is a template for adding an index to the
     *         data_source_id column of a _instances table
     */
    static String getAddDataSourceIdIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_data_source_id ON %s (data_source_id)";
    }

    /**
     * Get the template for creating an index on the value column of an instance
     * table. %s will exist in the template where the name of the new table will
     * be addedd.
     *
     * @return a String which is a template for adding an index to the value
     *         column of a _instances table
     */
    static String getAddValueIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_value ON %s (value)";
    }

    /**
     * Get the template for creating an index on the known_status column of an
     * instance table. %s will exist in the template where the name of the new
     * table will be addedd.
     *
     * @return a String which is a template for adding an index to the
     *         known_status column of a _instances table
     */
    static String getAddKnownStatusIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_value_known_status ON %s (value, known_status)";
    }

    /**
     * Get the template for creating an index on the file_obj_id column of an
     * instance table. %s will exist in the template where the name of the new
     * table will be addedd.
     *
     * @return a String which is a template for adding an index to the file_obj_id
     *         column of a _instances table
     */
    static String getAddObjectIdIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_file_obj_id ON %s (file_obj_id)";
    }

    public boolean insertDefaultDatabaseContent() {
        Connection conn = getEphemeralConnection();
        if (null == conn) {
            return false;
        }

        boolean result = EamDbUtil.insertDefaultCorrelationTypes(conn)
                && EamDbUtil.updateSchemaVersion(conn)
                && EamDbUtil.insertDefaultOrganization(conn);
        EamDbUtil.closeConnection(conn);
        return result;
    }

    boolean isChanged() {
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
            throw new EamDbException("Invalid database file name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."); // NON-NLS
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
    void setBulkThreshold(int bulkThreshold) throws EamDbException {
        if (bulkThreshold > 0) {
            this.bulkThreshold = bulkThreshold;
        } else {
            throw new EamDbException("Invalid bulk threshold."); // NON-NLS
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

}
