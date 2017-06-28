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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.TextConverter;
import org.sleuthkit.autopsy.coreutils.TextConverterException;

/**
 * Settings for the Postgres implementation of the Central Repository
 * database
 */
public final class PostgresEamDbSettings {

    private final static Logger LOGGER = Logger.getLogger(PostgresEamDbSettings.class.getName());
    private final String DEFAULT_HOST = "localhost"; // NON-NLS
    private final int DEFAULT_PORT = 5432;
    private final String DEFAULT_DBNAME = "central_repository"; // NON-NLS
    private final int DEFAULT_BULK_THRESHHOLD = 1000;
    private final String DEFAULT_USERNAME = "";
    private final String DEFAULT_PASSWORD = "";
    private final String DEFAULT_BAD_TAGS = "Evidence"; // NON-NLS
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
    private List<String> badTags;

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

        String badTagsStr = ModuleSettings.getConfigSetting("CentralRepository", "db.badTags"); // NON-NLS
        if (badTagsStr == null || badTagsStr.isEmpty()) {
            badTagsStr = DEFAULT_BAD_TAGS;
        }
        badTags = new ArrayList<>(Arrays.asList(badTagsStr.split(",")));
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

        ModuleSettings.setConfigSetting("CentralRepository", "db.badTags", String.join(",", badTags)); // NON-NLS
    }

    /**
     * Get the full connection URL as a String
     *
     * @param usePostgresDb Connect to the 'postgres' database when testing
     * connectivity and creating the main database.
     * 
     * @return
     */
    public String getConnectionURL(boolean usePostgresDb) {
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
     * Use the current settings to get an ephemeral client connection for testing.
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
     * Use the current settings and the validation query 
     * to test the connection to the database.
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
            EamDbUtil.closePreparedStatement(ps);
            EamDbUtil.closeResultSet(rs);
            EamDbUtil.closeConnection(conn);
        }
        return false;
    }
    
    /**
     * Use the current settings and the schema version query 
     * to test the database schema.
     * 
     * @return true if successfull connection, else false.
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
        createOrganizationsTable.append("poc_phone text NOT NULL");
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
        createCasesTable.append("case_number text NOT NULL,");
        createCasesTable.append("examiner_name text NOT NULL,");
        createCasesTable.append("examiner_email text NOT NULL,");
        createCasesTable.append("examiner_phone text NOT NULL,");
        createCasesTable.append("notes text NOT NULL,");
        createCasesTable.append("foreign key (org_id) references organizations(id) on update set null on delete set null");
        createCasesTable.append(")");

        // NOTE: when there are few cases in the cases table, these indices may not be worthwhile
        String casesIdx1 = "CREATE INDEX IF NOT EXISTS cases_org_id ON cases (org_id)";
        String casesIdx2 = "CREATE INDEX IF NOT EXISTS cases_case_uid ON cases (case_uid)";

        StringBuilder createDataSourcesTable = new StringBuilder();
        createDataSourcesTable.append("CREATE TABLE IF NOT EXISTS data_sources (");
        createDataSourcesTable.append("id SERIAL PRIMARY KEY,");
        createDataSourcesTable.append("device_id text NOT NULL,");
        createDataSourcesTable.append("name text NOT NULL,");
        createDataSourcesTable.append("CONSTRAINT device_id_unique UNIQUE (device_id)");
        createDataSourcesTable.append(")");

        String dataSourceIdx1 = "CREATE INDEX IF NOT EXISTS data_sources_name ON data_sources (name)";

        StringBuilder createGlobalReferenceSetsTable = new StringBuilder();
        createGlobalReferenceSetsTable.append("CREATE TABLE IF NOT EXISTS global_reference_sets (");
        createGlobalReferenceSetsTable.append("id SERIAL PRIMARY KEY,");
        createGlobalReferenceSetsTable.append("org_id integer,");
        createGlobalReferenceSetsTable.append("set_name text NOT NULL,");
        createGlobalReferenceSetsTable.append("version text NOT NULL,");
        createGlobalReferenceSetsTable.append("import_date text NOT NULL,");
        createGlobalReferenceSetsTable.append("foreign key (org_id) references organizations(id) on update set null on delete set null");
        createGlobalReferenceSetsTable.append(")");

        String globalReferenceSetsIdx1 = "CREATE INDEX IF NOT EXISTS global_reference_sets_org_id ON global_reference_sets (org_id)";

        StringBuilder createGlobalFilesTable = new StringBuilder();
        createGlobalFilesTable.append("CREATE TABLE IF NOT EXISTS global_files (");
        createGlobalFilesTable.append("id SERIAL PRIMARY KEY,");
        createGlobalFilesTable.append("global_reference_set_id integer,");
        createGlobalFilesTable.append("value text NOT NULL,");
        createGlobalFilesTable.append("known_status text NOT NULL,");
        createGlobalFilesTable.append("comment text NOT NULL,");
        createGlobalFilesTable.append("CONSTRAINT global_files_multi_unique UNIQUE (global_reference_set_id,value),");
        createGlobalFilesTable.append("foreign key (global_reference_set_id) references global_reference_sets(id) on update set null on delete set null");
        createGlobalFilesTable.append(")");

        String globalFilesIdx1 = "CREATE INDEX IF NOT EXISTS global_files_value ON global_files (value)";
        String globalFilesIdx2 = "CREATE INDEX IF NOT EXISTS global_files_value_known_status ON global_files (value, known_status)";

        StringBuilder createArtifactTypesTable = new StringBuilder();
        createArtifactTypesTable.append("CREATE TABLE IF NOT EXISTS correlation_types (");
        createArtifactTypesTable.append("id SERIAL PRIMARY KEY,");
        createArtifactTypesTable.append("display_name text NOT NULL,");
        createArtifactTypesTable.append("db_table_name text NOT NULL,");
        createArtifactTypesTable.append("supported integer NOT NULL,");
        createArtifactTypesTable.append("enabled integer NOT NULL,");
        createArtifactTypesTable.append(")");

        // NOTE: there are API methods that query by one of: name, supported, or enabled.
        // Only name is currently implemented, but, there will only be a small number
        // of artifact_types, so there is no benefit to having any indices.
        StringBuilder createArtifactInstancesTableTemplate = new StringBuilder();
        createArtifactInstancesTableTemplate.append("CREATE TABLE IF NOT EXISTS %s (");
        createArtifactInstancesTableTemplate.append("id SERIAL PRIMARY KEY,");
        createArtifactInstancesTableTemplate.append("case_id integer,");
        createArtifactInstancesTableTemplate.append("data_source_id integer,");
        createArtifactInstancesTableTemplate.append("value text NOT NULL,");
        createArtifactInstancesTableTemplate.append("file_path text NOT NULL,");
        createArtifactInstancesTableTemplate.append("known_status text NOT NULL,");
        createArtifactInstancesTableTemplate.append("comment text NOT NULL,");
        createArtifactInstancesTableTemplate.append("CONSTRAINT %s_multi_unique_ UNIQUE (case_id, data_source_id, value, file_path),");
        createArtifactInstancesTableTemplate.append("foreign key (case_id) references cases(id) on update set null on delete set null,");
        createArtifactInstancesTableTemplate.append("foreign key (data_source_id) references data_sources(id) on update set null on delete set null");
        createArtifactInstancesTableTemplate.append(")");

        // TODO: do we need any more indices?
        String instancesIdx1 = "CREATE INDEX IF NOT EXISTS %s_case_id ON %s (case_id)";
        String instancesIdx2 = "CREATE INDEX IF NOT EXISTS %s_data_source_id ON %s (data_source_id)";
        String instancesIdx3 = "CREATE INDEX IF NOT EXISTS %s_value ON %s (value)";
        String instancesIdx4 = "CREATE INDEX IF NOT EXISTS %s_value_known_status ON %s (value, known_status)";

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

            stmt.execute(createGlobalReferenceSetsTable.toString());
            stmt.execute(globalReferenceSetsIdx1);

            stmt.execute(createGlobalFilesTable.toString());
            stmt.execute(globalFilesIdx1);
            stmt.execute(globalFilesIdx2);

            stmt.execute(createArtifactTypesTable.toString());

            stmt.execute(createDbInfoTable.toString());

            // Create a separate table for each artifact type
            List<EamArtifact.Type> DEFAULT_ARTIFACT_TYPES = EamArtifact.getCorrelationTypes();
            String type_name;
            for (EamArtifact.Type type : DEFAULT_ARTIFACT_TYPES) {
                type_name = type.getDbTableName();
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
        Connection conn = getEphemeralConnection(false);
        if (null == conn) {
            return false;
        }

        boolean result = EamDbUtil.insertDefaultArtifactTypes(conn)
                && EamDbUtil.insertSchemaVersion(conn);
        EamDbUtil.closeConnection(conn);

        return result;
    }

    public boolean isChanged() {
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
     * To prevent issues where one command can honor case and another cannot,
     * we will force the dbname to lower case.
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
            throw new EamDbException("Invalid database name. Name must start with a letter and can only contain letters, numbers, and '_'."); // NON-NLS
        }

        this.dbName = dbName.toLowerCase();
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
