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

/**
 * Settings for the Postgres implementation of the Central Repository database
 *
 * NOTE: This is public scope because the options panel calls it directly to
 * set/get
 */
public final class PostgresEamDbSettings {

    private final static Logger LOGGER = Logger.getLogger(PostgresEamDbSettings.class.getName());
    private final String DEFAULT_HOST = ""; // NON-NLS
    private final int DEFAULT_PORT = 5432;
    private final String DEFAULT_DBNAME = "central_repository"; // NON-NLS
    private final String DEFAULT_USERNAME = "";
    private final String DEFAULT_PASSWORD = "";
    private final String VALIDATION_QUERY = "SELECT version()"; // NON-NLS
    private final String JDBC_BASE_URI = "jdbc:postgresql://"; // NON-NLS
    private final String JDBC_DRIVER = "org.postgresql.Driver"; // NON-NLS
    private final String DB_NAMES_REGEX = "[a-z][a-z0-9_]*"; // only lower case
    private final String DB_USER_NAMES_REGEX = "[a-zA-Z]\\w*";
    private String host;
    private int port;
    private String dbName;
    private int bulkThreshold;
    private String userName;
    private String password;

    public PostgresEamDbSettings() {
        loadSettings();
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
    private Connection getEphemeralConnection(boolean usePostgresDb) {
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
    public boolean verifyConnection() {
        Connection conn = getEphemeralConnection(true);
        if (null == conn) {
            return false;
        }

        boolean result = EamDbUtil.executeValidationQuery(conn, VALIDATION_QUERY);
        EamDbUtil.closeConnection(conn);
        return result;
    }

    /**
     * Check to see if the database exists.
     *
     * @return true if exists, else false
     */
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
            EamDbUtil.closeStatement(ps);
            EamDbUtil.closeResultSet(rs);
            EamDbUtil.closeConnection(conn);
        }
        return false;
    }

    /**
     * Use the current settings and the schema version query to test the
     * database schema.
     *
     * @return true if successful connection, else false.
     */
    public boolean verifyDatabaseSchema() {
        Connection conn = getEphemeralConnection(false);
        if (null == conn) {
            return false;
        }

        boolean result = EamDbUtil.schemaVersionIsSet(conn);

        EamDbUtil.closeConnection(conn);
        return result;
    }

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
            EamDbUtil.closeConnection(conn);
        }
        return true;

    }

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
            EamDbUtil.closeConnection(conn);
        }
        return true;

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
        createOrganizationsTable.append("id SERIAL PRIMARY KEY,");
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
        createCasesTable.append("id SERIAL PRIMARY KEY,");
        createCasesTable.append("case_uid text NOT NULL,");
        createCasesTable.append("org_id integer,");
        createCasesTable.append("case_name text NOT NULL,");
        createCasesTable.append("creation_date text NOT NULL,");
        createCasesTable.append("case_number text,");
        createCasesTable.append("examiner_name text,");
        createCasesTable.append("examiner_email text,");
        createCasesTable.append("examiner_phone text,");
        createCasesTable.append("notes text,");
        createCasesTable.append("foreign key (org_id) references organizations(id) ON UPDATE SET NULL ON DELETE SET NULL,");
        createCasesTable.append("CONSTRAINT case_uid_unique UNIQUE (case_uid)");
        createCasesTable.append(")");

        // NOTE: when there are few cases in the cases table, these indices may not be worthwhile
        String casesIdx1 = "CREATE INDEX IF NOT EXISTS cases_org_id ON cases (org_id)";
        String casesIdx2 = "CREATE INDEX IF NOT EXISTS cases_case_uid ON cases (case_uid)";

        StringBuilder createDataSourcesTable = new StringBuilder();
        createDataSourcesTable.append("CREATE TABLE IF NOT EXISTS data_sources (");
        createDataSourcesTable.append("id SERIAL PRIMARY KEY,");
        createDataSourcesTable.append("case_id integer NOT NULL,");
        createDataSourcesTable.append("device_id text NOT NULL,");
        createDataSourcesTable.append("name text NOT NULL,");
        createDataSourcesTable.append("foreign key (case_id) references cases(id) ON UPDATE SET NULL ON DELETE SET NULL,");
        createDataSourcesTable.append("CONSTRAINT datasource_unique UNIQUE (case_id, device_id, name)");
        createDataSourcesTable.append(")");

        String dataSourceIdx1 = "CREATE INDEX IF NOT EXISTS data_sources_name ON data_sources (name)";

        StringBuilder createReferenceSetsTable = new StringBuilder();
        createReferenceSetsTable.append("CREATE TABLE IF NOT EXISTS reference_sets (");
        createReferenceSetsTable.append("id SERIAL PRIMARY KEY,");
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
        createReferenceTypesTableTemplate.append("id SERIAL PRIMARY KEY,");
        createReferenceTypesTableTemplate.append("reference_set_id integer,");
        createReferenceTypesTableTemplate.append("value text NOT NULL,");
        createReferenceTypesTableTemplate.append("known_status integer NOT NULL,");
        createReferenceTypesTableTemplate.append("comment text,");
        createReferenceTypesTableTemplate.append("CONSTRAINT %s_multi_unique UNIQUE (reference_set_id, value),");
        createReferenceTypesTableTemplate.append("foreign key (reference_set_id) references reference_sets(id) ON UPDATE SET NULL ON DELETE SET NULL");
        createReferenceTypesTableTemplate.append(")");

        // Each "%s" will be replaced with the relevant reference_TYPE table name.
        String referenceTypesIdx1 = "CREATE INDEX IF NOT EXISTS %s_value ON %s (value)";
        String referenceTypesIdx2 = "CREATE INDEX IF NOT EXISTS %s_value_known_status ON %s (value, known_status)";

        StringBuilder createCorrelationTypesTable = new StringBuilder();
        createCorrelationTypesTable.append("CREATE TABLE IF NOT EXISTS correlation_types (");
        createCorrelationTypesTable.append("id SERIAL PRIMARY KEY,");
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
        createDbInfoTable.append("id SERIAL PRIMARY KEY NOT NULL,");
        createDbInfoTable.append("name text NOT NULL,");
        createDbInfoTable.append("value text NOT NULL");
        createDbInfoTable.append(")");

        // NOTE: the db_info table currenly only has 1 row, so having an index
        // provides no benefit.
        Connection conn = null;
        try {
            conn = getEphemeralConnection(false);
            if (null == conn) {
                return false;
            }
            Statement stmt = conn.createStatement();

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

            // Create a separate instance and reference table for each correlation type
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
     * Get the template String for creating a new _instances table in a Postgres
     * central repository. %s will exist in the template where the name of the
     * new table will be addedd.
     *
     * @return a String which is a template for cretating a new _instances table
     */
    static String getCreateArtifactInstancesTableTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        StringBuilder createArtifactInstancesTableTemplate = new StringBuilder();
        createArtifactInstancesTableTemplate.append("CREATE TABLE IF NOT EXISTS %s (");
        createArtifactInstancesTableTemplate.append("id SERIAL PRIMARY KEY,");
        createArtifactInstancesTableTemplate.append("case_id integer NOT NULL,");
        createArtifactInstancesTableTemplate.append("data_source_id integer NOT NULL,");
        createArtifactInstancesTableTemplate.append("value text NOT NULL,");
        createArtifactInstancesTableTemplate.append("file_path text NOT NULL,");
        createArtifactInstancesTableTemplate.append("known_status integer NOT NULL,");
        createArtifactInstancesTableTemplate.append("comment text,");
        createArtifactInstancesTableTemplate.append("file_obj_id integer,");
        createArtifactInstancesTableTemplate.append("CONSTRAINT %s_multi_unique_ UNIQUE (data_source_id, value, file_path),");
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
        Connection conn = getEphemeralConnection(false);
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
    public void setHost(String host) throws EamDbException {
        if (null != host && !host.isEmpty()) {
            this.host = host;
        } else {
            throw new EamDbException("Invalid host name. Cannot be empty."); // NON-NLS
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
            throw new EamDbException("Invalid port. Must be a number greater than 0."); // NON-NLS
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
    public void setDbName(String dbName) throws EamDbException {
        if (dbName == null || dbName.isEmpty()) {
            throw new EamDbException("Invalid database name. Cannot be empty."); // NON-NLS
        } else if (!Pattern.matches(DB_NAMES_REGEX, dbName)) {
            throw new EamDbException("Invalid database name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."); // NON-NLS
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
    public void setBulkThreshold(int bulkThreshold) throws EamDbException {
        if (bulkThreshold > 0) {
            this.bulkThreshold = bulkThreshold;
        } else {
            throw new EamDbException("Invalid bulk threshold."); // NON-NLS
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
        if (userName == null || userName.isEmpty()) {
            throw new EamDbException("Invalid user name. Cannot be empty."); // NON-NLS
        } else if (!Pattern.matches(DB_USER_NAMES_REGEX, userName)) {
            throw new EamDbException("Invalid user name. Name must start with a letter and can only contain letters, numbers, and '_'."); // NON-NLS
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
    public void setPassword(String password) throws EamDbException {
        if (password == null || password.isEmpty()) {
            throw new EamDbException("Invalid user password. Cannot be empty."); // NON-NLS
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
