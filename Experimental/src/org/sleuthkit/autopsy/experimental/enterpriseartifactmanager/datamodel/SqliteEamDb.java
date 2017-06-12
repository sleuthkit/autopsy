/*
 * Enterprise Artifact Manager
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.dbcp2.BasicDataSource;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Sqlite implementation of the enterprise artifact manager database
 */
public class SqliteEamDb extends AbstractSqlEamDb {

    private final static Logger LOGGER = Logger.getLogger(SqliteEamDb.class.getName());

    private static SqliteEamDb instance;

    protected static final String PRAGMA_SYNC_OFF = "PRAGMA synchronous = OFF";
    protected static final String PRAGMA_SYNC_NORMAL = "PRAGMA synchronous = NORMAL";
    private static final String PRAGMA_JOURNAL_WAL = "PRAGMA journal_mode = WAL";
    private static final String PRAGMA_READ_UNCOMMITTED_TRUE = "PRAGMA read_uncommitted = True";
    private static final String PRAGMA_ENCODING_UTF8 = "PRAGMA encoding = 'UTF-8'";
    private static final String PRAGMA_PAGE_SIZE_4096 = "PRAGMA page_size = 4096";
    private static final String PRAGMA_FOREIGN_KEYS_ON = "PRAGMA foreign_keys = ON";
    private BasicDataSource connectionPool = null;

    private final SqliteEamDbSettings dbSettings;

    public synchronized static SqliteEamDb getInstance() {
        if (instance == null) {
            instance = new SqliteEamDb();
        }

        return instance;
    }

    private SqliteEamDb() {
        dbSettings = new SqliteEamDbSettings();
        updateSettings();
    }

    @Override
    public void updateSettings() {
        synchronized (this) {
            dbSettings.loadSettings();
            bulkArtifactsThreshold = dbSettings.getBulkThreshold();
        }
    }

    @Override
    public void saveSettings() {
        synchronized (this) {
            dbSettings.saveSettings();
        }
    }

    @Override
    public void reset() throws EamDbException {
        Connection conn = connect();

        try {
            Statement dropContent = conn.createStatement();
            dropContent.executeUpdate("DELETE FROM organizations");
            dropContent.executeUpdate("DELETE FROM cases");
            dropContent.executeUpdate("DELETE FROM data_sources");
            dropContent.executeUpdate("DELETE FROM global_reference_sets");
            dropContent.executeUpdate("DELETE FROM global_files");
            dropContent.executeUpdate("DELETE FROM artifact_types");
            dropContent.executeUpdate("DELETE FROM db_info");

            String instancesTemplate = "DELETE FROM %s_instances";
            for (EamArtifact.Type type : DEFAULT_ARTIFACT_TYPES) {
                dropContent.executeUpdate(String.format(instancesTemplate, type.getName().toLowerCase()));
            }

            dropContent.executeUpdate("VACUUM");
            insertDefaultContent();

        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to reset database.", ex);
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * Setup a connection pool for db connections.
     *
     */
    private void setupConnectionPool() throws EamDbException {
        connectionPool = new BasicDataSource();
        connectionPool.setDriverClassName(dbSettings.getDriver());

        StringBuilder connectionURL = new StringBuilder();
        connectionURL.append(dbSettings.getJDBCBaseURI());
        connectionURL.append(dbSettings.getDbDirectory());
        connectionURL.append(File.separator);
        connectionURL.append(dbSettings.getDbName());

        connectionPool.setUrl(connectionURL.toString());

        // tweak pool configuration
        connectionPool.setInitialSize(50);
        connectionPool.setMaxTotal(-1);
        connectionPool.setMaxIdle(-1);
        connectionPool.setMaxWaitMillis(1000);
        connectionPool.setValidationQuery(dbSettings.getValidationQuery());
    }

    /**
     * Verify the EAM db directory exists. If it doesn't, then create it.
     *
     * @throws EamDbException
     */
    private void verifyDBDirectory() throws EamDbException {
        File dbDir = new File(dbSettings.getDbDirectory());
        if (!dbDir.exists()) {
            LOGGER.log(Level.INFO, "sqlite directory does not exist, creating it at {0}.", dbSettings.getDbDirectory()); // NON-NLS
            try {
                Files.createDirectories(dbDir.toPath());
            } catch (IOException ex) {
                throw new EamDbException("Failed to create sqlite database directory. ", ex); // NON-NLS
            }
        } else if (dbDir.exists() && !dbDir.isDirectory()) {
            LOGGER.log(Level.INFO, "Failed to create sqlite database directory. Path already exists and is not a directory: {0}", dbSettings.getDbDirectory()); // NON-NLS
        }

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
    @Override
    protected void initializeDatabaseSchema() throws EamDbException {
        // The "id" column is an alias for the built-in 64-bit int "rowid" column.
        // It is autoincrementing by default and must be of type "integer primary key".
        // We've omitted the autoincrement argument because we are not currently
        // using the id value to search for specific rows, so we do not care
        // if a rowid is re-used after an existing rows was previously deleted.
        StringBuilder createOrganizationsTable = new StringBuilder();
        createOrganizationsTable.append("CREATE TABLE IF NOT EXISTS organizations (");
        createOrganizationsTable.append("id integer primary key autoincrement NOT NULL,");
        createOrganizationsTable.append("org_name character varying(50) NOT NULL,");
        createOrganizationsTable.append("poc_name character varying(50) NOT NULL,");
        createOrganizationsTable.append("poc_email character varying(50) NOT NULL,");
        createOrganizationsTable.append("poc_phone character varying(20) NOT NULL");
        createOrganizationsTable.append(")");

        // TODO: The organizations will only have a small number of rows, so
        // determine if an index is worthwhile.
        StringBuilder createCasesTable = new StringBuilder();
        createCasesTable.append("CREATE TABLE IF NOT EXISTS cases (");
        createCasesTable.append("id integer primary key autoincrement NOT NULL,");
        createCasesTable.append("case_uid character varying(50) NOT NULL,");
        createCasesTable.append("org_id integer,");
        createCasesTable.append("case_name character varying(50) NOT NULL,");
        createCasesTable.append("creation_date character varying(30) NOT NULL,");
        createCasesTable.append("case_number character varying(20) NOT NULL,");
        createCasesTable.append("examiner_name character varying(50) NOT NULL,");
        createCasesTable.append("examiner_email character varying(50) NOT NULL,");
        createCasesTable.append("examiner_phone character varying(20) NOT NULL,");
        createCasesTable.append("notes character varying(400) NOT NULL,");
        createCasesTable.append("foreign key (org_id) references organizations(id) on update set null on delete set null,");
        createCasesTable.append("CONSTRAINT case_uid_unique UNIQUE(case_uid)");
        createCasesTable.append(")");

        // TODO: when there are few cases in the cases table, these indices may not be worthwhile
        String casesIdx1 = "CREATE INDEX IF NOT EXISTS cases_org_id ON cases (org_id)";
        String casesIdx2 = "CREATE INDEX IF NOT EXISTS cases_case_uid ON cases (case_uid)";

        StringBuilder createDataSourcesTable = new StringBuilder();
        createDataSourcesTable.append("CREATE TABLE IF NOT EXISTS data_sources (");
        createDataSourcesTable.append("id integer primary key autoincrement NOT NULL,");
        createDataSourcesTable.append("device_id character varying(50) NOT NULL,");
        createDataSourcesTable.append("name character varying(50) NOT NULL,");
        createDataSourcesTable.append("CONSTRAINT device_id_unique UNIQUE(device_id)");
        createDataSourcesTable.append(")");

        String dataSourceIdx1 = "CREATE INDEX IF NOT EXISTS data_sources_name ON data_sources (name)";

        StringBuilder createGlobalReferenceSetsTable = new StringBuilder();
        createGlobalReferenceSetsTable.append("CREATE TABLE IF NOT EXISTS global_reference_sets (");
        createGlobalReferenceSetsTable.append("id integer primary key autoincrement NOT NULL,");
        createGlobalReferenceSetsTable.append("org_id integer,");
        createGlobalReferenceSetsTable.append("set_name character varying(100) NOT NULL,");
        createGlobalReferenceSetsTable.append("version character varying(20) NOT NULL,");
        createGlobalReferenceSetsTable.append("import_date character varying(30) NOT NULL,");
        createGlobalReferenceSetsTable.append("foreign key (org_id) references organizations(id) on update set null on delete set null");
        createGlobalReferenceSetsTable.append(")");

        String globalReferenceSetsIdx1 = "CREATE INDEX IF NOT EXISTS global_reference_sets_org_id ON global_reference_sets (org_id)";

        StringBuilder createGlobalFilesTable = new StringBuilder();
        createGlobalFilesTable.append("CREATE TABLE IF NOT EXISTS global_files (");
        createGlobalFilesTable.append("id integer primary key autoincrement NOT NULL,");
        createGlobalFilesTable.append("global_reference_set_id integer,");
        createGlobalFilesTable.append("value character varying(100) NOT NULL,");
        createGlobalFilesTable.append("known_status character varying(10) NOT NULL,");
        createGlobalFilesTable.append("comment character varying(400) NOT NULL,");
        createGlobalFilesTable.append("CONSTRAINT global_files_multi_unique UNIQUE(global_reference_set_id, value)");
        createGlobalFilesTable.append("foreign key (global_reference_set_id) references global_reference_sets(id) on update set null on delete set null");
        createGlobalFilesTable.append(")");

        String globalFilesIdx1 = "CREATE INDEX IF NOT EXISTS global_files_value ON global_files (value)";
        String globalFilesIdx2 = "CREATE INDEX IF NOT EXISTS global_files_value_known_status ON global_files (value, known_status)";

        StringBuilder createArtifactTypesTable = new StringBuilder();
        createArtifactTypesTable.append("CREATE TABLE IF NOT EXISTS artifact_types (");
        createArtifactTypesTable.append("id integer primary key autoincrement NOT NULL,");
        createArtifactTypesTable.append("name character varying(20) NOT NULL,");
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
        createArtifactInstancesTableTemplate.append("value character varying(100) NOT NULL,");
        createArtifactInstancesTableTemplate.append("file_path character varying(256) NOT NULL,");
        createArtifactInstancesTableTemplate.append("known_status character varying(10) NOT NULL,");
        createArtifactInstancesTableTemplate.append("comment character varying(400) NOT NULL,");
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
        createDbInfoTable.append("name character varying(50) NOT NULL,");
        createDbInfoTable.append("value character varying(50) NOT NULL");
        createDbInfoTable.append(")");

        // NOTE: the db_info table currenly only has 1 row, so having an index
        // provides no benefit.
        Connection conn = null;
        try {
            conn = connectionPool.getConnection();
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
            throw new EamDbException("Error initializing db schema.", ex); // NON-NLS
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * Lazily setup Singleton connection on first request.
     *
     * @return A connection from the connection pool.
     *
     * @throws EamDbException
     */
    @Override
    protected Connection connect() throws EamDbException {
        synchronized (this) {
            if (!dbSettings.isEnabled()) {
                throw new EamDbException("Enterprise artifact manager is not enabled"); // NON-NLS
            }

            if (connectionPool == null) {
                verifyDBDirectory();
                setupConnectionPool();
                confirmDatabaseSchema();
            }

            try {
                return connectionPool.getConnection();
            } catch (SQLException ex) {
                throw new EamDbException("Error getting connection from connection pool.", ex); // NON-NLS
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return dbSettings.isEnabled();
    }

    @Override
    public List<String> getBadTags() {
        return dbSettings.getBadTags();
    }

    @Override
    public void setBadTags(List<String> badTags) {
        dbSettings.setBadTags(badTags);
    }

}
