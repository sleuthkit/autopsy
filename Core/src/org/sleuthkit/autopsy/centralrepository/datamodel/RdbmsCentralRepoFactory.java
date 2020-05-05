/*
 * Central Repository
 *
 * Copyright 2020 Basis Technology Corp.
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona.Confidence;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona.PersonaStatus;
import static org.sleuthkit.autopsy.centralrepository.datamodel.RdbmsCentralRepo.SOFTWARE_CR_DB_SCHEMA_VERSION;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;

/**
 * Creates the CR schema and populates it with initial data.
 *
 */
public class RdbmsCentralRepoFactory {

    private final static Logger LOGGER = Logger.getLogger(RdbmsCentralRepoFactory.class.getName());

   
    private final CentralRepoPlatforms selectedPlatform;
    private final SqliteCentralRepoSettings sqliteCentralRepoSettings;
    private final PostgresCentralRepoSettings postgresCentralRepoSettings;
    

    // SQLite pragmas
    private final static String PRAGMA_SYNC_OFF = "PRAGMA synchronous = OFF";
    private final static String PRAGMA_JOURNAL_WAL = "PRAGMA journal_mode = WAL";
    private final static String PRAGMA_READ_UNCOMMITTED_TRUE = "PRAGMA read_uncommitted = True";
    private final static String PRAGMA_ENCODING_UTF8 = "PRAGMA encoding = 'UTF-8'";
    private final static String PRAGMA_PAGE_SIZE_4096 = "PRAGMA page_size = 4096";
    private final static String PRAGMA_FOREIGN_KEYS_ON = "PRAGMA foreign_keys = ON";


    
    public RdbmsCentralRepoFactory(CentralRepoPlatforms selectedPlatform, SqliteCentralRepoSettings repoSettings) throws CentralRepoException {
        this.selectedPlatform = selectedPlatform;
        this.sqliteCentralRepoSettings = repoSettings;
        this.postgresCentralRepoSettings =  null;
        
    }

     public RdbmsCentralRepoFactory(CentralRepoPlatforms selectedPlatform, PostgresCentralRepoSettings repoSettings) throws CentralRepoException {
        this.selectedPlatform = selectedPlatform;
        this.postgresCentralRepoSettings = repoSettings;
        this.sqliteCentralRepoSettings =  null;
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

        String createArtifactInstancesTableTemplate = getCreateArtifactInstancesTableTemplate(selectedPlatform);
        String createAccountInstancesTableTemplate = getCreateAccountInstancesTableTemplate(selectedPlatform);

        String instancesCaseIdIdx = getAddCaseIdIndexTemplate();
        String instancesDatasourceIdIdx = getAddDataSourceIdIndexTemplate();
        String instancesValueIdx = getAddValueIndexTemplate();
        String instancesKnownStatusIdx = getAddKnownStatusIndexTemplate();
        String instancesObjectIdIdx = getAddObjectIdIndexTemplate();

        // NOTE: the db_info table currenly only has 1 row, so having an index
        // provides no benefit.
        try (Connection conn = this.getEphemeralConnection();) {

            if (null == conn) {
                LOGGER.log(Level.SEVERE, "Cannot initialize CR database, don't have a valid connection."); // NON-NLS
                return false;
            }

            try (Statement stmt = conn.createStatement();) {

                // these setting PRAGMAs are SQLIte spcific
                if (selectedPlatform == CentralRepoPlatforms.SQLITE) {
                    stmt.execute(PRAGMA_JOURNAL_WAL);
                    stmt.execute(PRAGMA_SYNC_OFF);
                    stmt.execute(PRAGMA_READ_UNCOMMITTED_TRUE);
                    stmt.execute(PRAGMA_ENCODING_UTF8);
                    stmt.execute(PRAGMA_PAGE_SIZE_4096);
                    stmt.execute(PRAGMA_FOREIGN_KEYS_ON);
                }

                // Create Organizations table
                stmt.execute(getCreateOrganizationsTableStatement(selectedPlatform));

                // Create Cases table and indexes
                stmt.execute(getCreateCasesTableStatement(selectedPlatform));
                stmt.execute(getCasesOrgIdIndexStatement());
                stmt.execute(getCasesCaseUidIndexStatement());

                stmt.execute(getCreateDataSourcesTableStatement(selectedPlatform));
                stmt.execute(getAddDataSourcesNameIndexStatement());
                stmt.execute(getAddDataSourcesObjectIdIndexStatement());

                stmt.execute(getCreateReferenceSetsTableStatement(selectedPlatform));
                stmt.execute(getReferenceSetsOrgIdIndexTemplate());

                stmt.execute(getCreateCorrelationTypesTableStatement(selectedPlatform));

                stmt.execute(getCreateDbInfoTableStatement(selectedPlatform));
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.SCHEMA_MAJOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMajor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.SCHEMA_MINOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMinor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.CREATION_SCHEMA_MAJOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMajor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.CREATION_SCHEMA_MINOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMinor() + "')");

                // Create account_types and accounts tables which are referred by X_instances tables
                stmt.execute(getCreateAccountTypesTableStatement(selectedPlatform));
                stmt.execute(getCreateAccountsTableStatement(selectedPlatform));

                // Create a separate instance and reference table for each artifact type
                List<CorrelationAttributeInstance.Type> defaultCorrelationTypes = CorrelationAttributeInstance.getDefaultCorrelationTypes();

                String reference_type_dbname;
                String instance_type_dbname;
                for (CorrelationAttributeInstance.Type type : defaultCorrelationTypes) {
                    reference_type_dbname = CentralRepoDbUtil.correlationTypeToReferenceTableName(type);
                    instance_type_dbname = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);

                    // use the correct create table template, based on whether the attribute type represents an account or not.
                    String createTableTemplate = (CentralRepoDbUtil.correlationAttribHasAnAccount(type)) 
                                        ? createAccountInstancesTableTemplate 
                                        : createArtifactInstancesTableTemplate;
                    
                    stmt.execute(String.format(createTableTemplate, instance_type_dbname, instance_type_dbname));
                    
                    stmt.execute(String.format(instancesCaseIdIdx, instance_type_dbname, instance_type_dbname));
                    stmt.execute(String.format(instancesDatasourceIdIdx, instance_type_dbname, instance_type_dbname));
                    stmt.execute(String.format(instancesValueIdx, instance_type_dbname, instance_type_dbname));
                    stmt.execute(String.format(instancesKnownStatusIdx, instance_type_dbname, instance_type_dbname));
                    stmt.execute(String.format(instancesObjectIdIdx, instance_type_dbname, instance_type_dbname));

                    // FUTURE: allow more than the FILES type
                    if (type.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                        stmt.execute(String.format(getReferenceTypesTableTemplate(selectedPlatform), reference_type_dbname, reference_type_dbname));
                        stmt.execute(String.format(getReferenceTypeValueIndexTemplate(), reference_type_dbname, reference_type_dbname));
                        stmt.execute(String.format(getReferenceTypeValueKnownstatusIndexTemplate(), reference_type_dbname, reference_type_dbname));
                    }
                }
                // create Persona tables.
                createPersonaTables(stmt, selectedPlatform);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error initializing db schema.", ex); // NON-NLS
                return false;
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, "Error getting default correlation types. Likely due to one or more Type's with an invalid db table name."); // NON-NLS
                return false;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error connecting to database.", ex); // NON-NLS
            return false;
        }

        return true;
    }

    /**
     * Inserts default data in CR database.
     * 
     * @return True if success, False otherwise.
     */
    public boolean insertDefaultDatabaseContent() {

        boolean result;
        try (Connection conn = this.getEphemeralConnection();) {
            if (null == conn) {
                return false;
            }

            result = CentralRepoDbUtil.insertDefaultCorrelationTypes(conn)
                    && CentralRepoDbUtil.insertDefaultOrganization(conn) 
                    && RdbmsCentralRepoFactory.insertDefaultAccountsTablesContent(conn, selectedPlatform )
                    && insertDefaultPersonaTablesContent(conn, selectedPlatform);

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, String.format("Failed to populate default data in CR tables."), ex);
            return false;
        }

        return result;
    }

    private static String getCreateDbInfoTableStatement(CentralRepoPlatforms selectedPlatform) {
         /*
             * Note that the essentially useless id column in the following
             * table is required for backwards compatibility. Otherwise, the
             * name column could be the primary key.
             */
           
        return "CREATE TABLE db_info ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "name TEXT UNIQUE NOT NULL,"
                + "value TEXT NOT NULL "
                + ")";
                    
    }
    /**
     * Returns Create Table SQL for Organizations table.
     * 
     * @param selectedPlatform CR database platform.
     * 
     * @return SQL string to create Organizations table.
     */
    private static String getCreateOrganizationsTableStatement(CentralRepoPlatforms selectedPlatform) {
        // The "id" column is an alias for the built-in 64-bit int "rowid" column.
        // It is autoincrementing by default and must be of type "integer primary key".
        // We've omitted the autoincrement argument because we are not currently
        // using the id value to search for specific rows, so we do not care
        // if a rowid is re-used after an existing rows was previously deleted.
        
        return "CREATE TABLE IF NOT EXISTS organizations ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "org_name text NOT NULL,"
                + "poc_name text NOT NULL,"
                + "poc_email text NOT NULL,"
                + "poc_phone text NOT NULL,"
                + "CONSTRAINT org_name_unique UNIQUE (org_name)"
                + ")";
    }
    
     /**
     * Returns Create Table SQL for Cases table.
     * 
     * @param selectedPlatform CR database platform.
     * 
     * @return SQL string to create Cases table.
     */
    private static String getCreateCasesTableStatement(CentralRepoPlatforms selectedPlatform) {
        
        return ("CREATE TABLE IF NOT EXISTS cases (")
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "case_uid text NOT NULL,"
                + "org_id integer,"
                + "case_name text NOT NULL,"
                + "creation_date text NOT NULL,"
                + "case_number text,"
                + "examiner_name text,"
                + "examiner_email text,"
                + "examiner_phone text,"
                + "notes text,"
                + "foreign key (org_id) references organizations(id) ON UPDATE SET NULL ON DELETE SET NULL,"
                + "CONSTRAINT case_uid_unique UNIQUE(case_uid)" + getOnConflictIgnoreClause(selectedPlatform)
                + ")";
    }
    
    private static String getCasesOrgIdIndexStatement() {
        return "CREATE INDEX IF NOT EXISTS cases_org_id ON cases (org_id)";
    }
    
    private static String getCasesCaseUidIndexStatement() {
        return "CREATE INDEX IF NOT EXISTS cases_case_uid ON cases (case_uid)";
    }
    
    private static String getCreateReferenceSetsTableStatement(CentralRepoPlatforms selectedPlatform) {
       
        return "CREATE TABLE IF NOT EXISTS reference_sets ("
            + getNumericPrimaryKeyClause("id", selectedPlatform)
            + "org_id integer NOT NULL,"
            + "set_name text NOT NULL,"
            + "version text NOT NULL,"
            + "known_status integer NOT NULL,"
            + "read_only boolean NOT NULL,"
            + "type integer NOT NULL,"
            + "import_date text NOT NULL,"
            + "foreign key (org_id) references organizations(id) ON UPDATE SET NULL ON DELETE SET NULL,"
            + "CONSTRAINT hash_set_unique UNIQUE (set_name, version)"
            + ")";
        
    }
    
    /**
     * 
     * @return 
     */
    private static String getReferenceSetsOrgIdIndexTemplate() {
         return "CREATE INDEX IF NOT EXISTS reference_sets_org_id ON reference_sets (org_id)";
    }
    
    /**
     * Returns the template string to create reference_TYPE tables.
     * 
     * @param selectedPlatform CR database platform.
     * 
     * @return template string to create a reference_TYPE table.
     */
    private static String getReferenceTypesTableTemplate(CentralRepoPlatforms selectedPlatform) {
        // Each "%s" will be replaced with the relevant reference_TYPE table name.

        return "CREATE TABLE IF NOT EXISTS %s ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "reference_set_id integer,"
                + "value text NOT NULL,"
                + "known_status integer NOT NULL,"
                + "comment text,"
                + "CONSTRAINT %s_multi_unique UNIQUE(reference_set_id, value)" + getOnConflictIgnoreClause(selectedPlatform) + ","
                + "foreign key (reference_set_id) references reference_sets(id) ON UPDATE SET NULL ON DELETE SET NULL"
                + ")";
    }
        
    /**
     * Returns SQL string template to create a value index on 
     * ReferenceType table.
     */
    private static String getReferenceTypeValueIndexTemplate() {
        return "CREATE INDEX IF NOT EXISTS %s_value ON %s (value)";
    }
    
    /**
     * Returns SQL string template to create a value/known_status index on 
     * ReferenceType table.
     */
    private static String getReferenceTypeValueKnownstatusIndexTemplate() {
        return "CREATE INDEX IF NOT EXISTS %s_value_known_status ON %s (value, known_status)";
    }
    
    /**
     * Returns the SQL statement to create correlation_types table.
     * 
     * @param selectedPlatform CR database platform.
     * 
     * @return SQL string to create correlation_types table.
     */
    private static String getCreateCorrelationTypesTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS correlation_types ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "display_name text NOT NULL,"
                + "db_table_name text NOT NULL,"
                + "supported integer NOT NULL,"
                + "enabled integer NOT NULL,"
                + "CONSTRAINT correlation_types_names UNIQUE (display_name, db_table_name)"
                + ")";
    }
    /**
     * Get the template String for creating a new _instances table for non account artifacts in 
     * central repository. %s will exist in the template where the name of the
     * new table will be added.
     *
     * @return a String which is a template for creating a new _instances table
     */
    static String getCreateArtifactInstancesTableTemplate(CentralRepoPlatforms selectedPlatform) {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        
        return "CREATE TABLE IF NOT EXISTS %s ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "case_id integer NOT NULL,"
                + "data_source_id integer NOT NULL,"
                + "value text NOT NULL,"
                + "file_path text NOT NULL,"
                + "known_status integer NOT NULL,"
                + "comment text,"
                + "file_obj_id " + getBigIntType(selectedPlatform) + " ," 
                + "CONSTRAINT %s_multi_unique UNIQUE(data_source_id, value, file_path)" + getOnConflictIgnoreClause(selectedPlatform) + ","
                + "foreign key (case_id) references cases(id) ON UPDATE SET NULL ON DELETE SET NULL,"
                + "foreign key (data_source_id) references data_sources(id) ON UPDATE SET NULL ON DELETE SET NULL)";
    }

     /**
     * Get the template String for creating a new _instances table for Accounts in
     * central repository. %s will exist in the template where the name of the
     * new table will be added.
     *
     * @return a String which is a template for creating a _instances table
     */
    static String getCreateAccountInstancesTableTemplate(CentralRepoPlatforms selectedPlatform) {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        
        return "CREATE TABLE IF NOT EXISTS %s ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "case_id integer NOT NULL,"
                + "data_source_id integer NOT NULL,"
                + "account_id " + getBigIntType(selectedPlatform) + " DEFAULT NULL,"
                + "value text NOT NULL,"
                + "file_path text NOT NULL,"
                + "known_status integer NOT NULL,"
                + "comment text,"
                + "file_obj_id " + getBigIntType(selectedPlatform) + " ," 
                + "CONSTRAINT %s_multi_unique UNIQUE(data_source_id, value, file_path)" + getOnConflictIgnoreClause(selectedPlatform) + ","
                + "foreign key (account_id) references accounts(id),"
                + "foreign key (case_id) references cases(id) ON UPDATE SET NULL ON DELETE SET NULL,"
                + "foreign key (data_source_id) references data_sources(id) ON UPDATE SET NULL ON DELETE SET NULL)";
    }
    
    /**
     * Get the statement String for creating a new data_sources table in a
     * Sqlite central repository.
     *
     * @return a String which is a statement for creating a new data_sources
     * table
     */
    static String getCreateDataSourcesTableStatement(CentralRepoPlatforms selectedPlatform) {
        return "CREATE TABLE IF NOT EXISTS data_sources ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "case_id integer NOT NULL,"
                + "device_id text NOT NULL,"
                + "name text NOT NULL,"
                + "datasource_obj_id " + getBigIntType(selectedPlatform) + " ," 
                + "md5 text DEFAULT NULL,"
                + "sha1 text DEFAULT NULL,"
                + "sha256 text DEFAULT NULL,"
                + "foreign key (case_id) references cases(id) ON UPDATE SET NULL ON DELETE SET NULL,"
                + "CONSTRAINT datasource_unique UNIQUE (case_id, datasource_obj_id))";
    }

    /**
     * Get the template for creating an index on the case_id column of an
     * instance table. %s will exist in the template where the name of the new
     * table will be added.
     *
     * @return a String which is a template for adding an index to the case_id
     * column of a _instances table
     */
    static String getAddCaseIdIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_case_id ON %s (case_id)";
    }

    /**
     * Get the template for creating an index on the data_source_id column of an
     * instance table. %s will exist in the template where the name of the new
     * table will be added.
     *
     * @return a String which is a template for adding an index to the
     * data_source_id column of a _instances table
     */
    static String getAddDataSourceIdIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_data_source_id ON %s (data_source_id)";
    }

    /**
     * Get the template for creating an index on the value column of an instance
     * table. %s will exist in the template where the name of the new table will
     * be added.
     *
     * @return a String which is a template for adding an index to the value
     * column of a _instances table
     */
    static String getAddValueIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_value ON %s (value)";
    }

    /**
     * Get the template for creating an index on the known_status column of an
     * instance table. %s will exist in the template where the name of the new
     * table will be added.
     *
     * @return a String which is a template for adding an index to the
     * known_status column of a _instances table
     */
    static String getAddKnownStatusIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_value_known_status ON %s (value, known_status)";
    }

    /**
     * Get the template for creating an index on the file_obj_id column of an
     * instance table. %s will exist in the template where the name of the new
     * table will be added.
     *
     * @return a String which is a template for adding an index to the
     * file_obj_id column of a _instances table
     */
    static String getAddObjectIdIndexTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "CREATE INDEX IF NOT EXISTS %s_file_obj_id ON %s (file_obj_id)";
    }

    /**
     * Get the statement for creating an index on the name column of the
     * data_sources table.
     *
     * @return a String which is a statement for adding an index on the name
     * column of the data_sources table.
     */
    static String getAddDataSourcesNameIndexStatement() {
        return "CREATE INDEX IF NOT EXISTS data_sources_name ON data_sources (name)";
    }

    /**
     * Get the statement for creating an index on the data_sources_object_id
     * column of the data_sources table.
     *
     * @return a String which is a statement for adding an index on the
     * data_sources_object_id column of the data_sources table.
     */
    static String getAddDataSourcesObjectIdIndexStatement() {
        return "CREATE INDEX IF NOT EXISTS data_sources_object_id ON data_sources (datasource_obj_id)";
    }

    /**
     * Builds SQL clause for a numeric primary key. Produces correct SQL based
     * on the selected CR platform/RDMBS.
     *
     * @param pkName name of primary key.
     * @param selectedPlatform The selected platform.
     *
     * @return SQL clause to be used in a Create table statement
     */
    private static String getNumericPrimaryKeyClause(String pkName, CentralRepoPlatforms selectedPlatform) {
        switch (selectedPlatform) {
            case POSTGRESQL:
                return String.format(" %s SERIAL PRIMARY KEY, ", pkName);
            case SQLITE:
                return String.format(" %s integer primary key autoincrement NOT NULL ,", pkName);
            default:
                return "";
        }
        
    }

    /**
     * Returns ON CONFLICT IGNORE clause for the specified database platform.
     *
     *
     * @return SQL clause.
     */
    private static String getOnConflictIgnoreClause(CentralRepoPlatforms selectedPlatform) {
        switch (selectedPlatform) {
            case POSTGRESQL:
                return "";
            case SQLITE:
                return " ON CONFLICT IGNORE ";
            default:
                return "";
        }
    }
    
    /**
     * Returns keyword for big integer for the specified database platform.
     *
     *
     * @return SQL clause.
     */
    static String getBigIntType(CentralRepoPlatforms selectedPlatform) {
        switch (selectedPlatform) {
            case POSTGRESQL:
                return " BIGINT ";
            case SQLITE:
                return " INTEGER ";
            default:
                return "";
        }
    }
    
     private static String getOnConflictDoNothingClause(CentralRepoPlatforms selectedPlatform) {
        switch (selectedPlatform) {
            case POSTGRESQL:
                return "ON CONFLICT DO NOTHING";
            case SQLITE:
                return "";
            default:
                return "";
        }
    }
    /**
     * Returns an ephemeral connection to the CR database.
     * 
     * @return CR database connection
     */
    private Connection getEphemeralConnection() {
        switch (selectedPlatform) {
            case POSTGRESQL:
                return this.postgresCentralRepoSettings.getEphemeralConnection(false);
            case SQLITE:
                return this.sqliteCentralRepoSettings.getEphemeralConnection();
            default:
                return null;
        }
    }
    
     /**
     * Creates the tables for Persona.
     * 
     * @return True if success, False otherwise.
     */
    static boolean createPersonaTables(Statement stmt, CentralRepoPlatforms selectedPlatform) throws SQLException {
        
        stmt.execute(getCreateConfidenceTableStatement(selectedPlatform));
        stmt.execute(getCreateExaminersTableStatement(selectedPlatform));
        stmt.execute(getCreatePersonaStatusTableStatement(selectedPlatform));
        
        stmt.execute(getCreatePersonasTableStatement(selectedPlatform));
        stmt.execute(getCreatePersonaAliasTableStatement(selectedPlatform));
        stmt.execute(getCreatePersonaMetadataTableStatement(selectedPlatform));
        stmt.execute(getCreatePersonaAccountsTableStatement(selectedPlatform));
        
        return true;
    }
    
    
    /**
     * Get the SQL string for creating a new account_types table in a central
     * repository.
     *
     * @return SQL string for creating account_types table
     */
    static String getCreateAccountTypesTableStatement(CentralRepoPlatforms selectedPlatform) {
       
        return "CREATE TABLE IF NOT EXISTS account_types ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "type_name TEXT NOT NULL,"
                + "display_name TEXT NOT NULL,"
                + "correlation_type_id " + getBigIntType(selectedPlatform) + " ,"
                + "CONSTRAINT type_name_unique UNIQUE (type_name),"
                + "FOREIGN KEY (correlation_type_id) REFERENCES correlation_types(id)"
                + ")";
    }
    
    /**
     * Get the SQL String for creating a new confidence table in a central
     * repository.
     *
     * @return SQL string for creating confidence table
     */
    static String getCreateConfidenceTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS confidence ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "confidence_id integer NOT NULL,"
                + "description TEXT,"
                + "CONSTRAINT level_unique UNIQUE (confidence_id)"
                + ")";
    }

    /**
     * Get the SQL String for creating a new examiners table in a central
     * repository.
     *
     * @return SQL string for creating examiners table
     */
    static String getCreateExaminersTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS examiners ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "login_name TEXT NOT NULL,"
                + "display_name TEXT,"
                + "CONSTRAINT login_name_unique UNIQUE(login_name)"
                + ")";
    }
    
    /**
     * Get the SQL String for creating a new persona_status table in a central
     * repository.
     *
     * @return SQL string for creating persona_status table
     */
    static String getCreatePersonaStatusTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS persona_status ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "status_id integer NOT NULL,"
                + "status TEXT NOT NULL,"
                + "CONSTRAINT status_unique UNIQUE(status_id)"
                + ")";
    }
    
    
    /**
     * Get the SQL String for creating a new accounts table in a central
     * repository.
     *
     * @return SQL string for creating accounts table
     */
    static String getCreateAccountsTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS accounts ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "account_type_id integer NOT NULL,"
                + "account_unique_identifier TEXT NOT NULL,"
                + "CONSTRAINT account_unique UNIQUE(account_type_id, account_unique_identifier),"
                + "FOREIGN KEY (account_type_id) REFERENCES account_types(id)"
                + ")";
    }
    
    /**
     * Get the SQL String for creating a new personas table in a central
     * repository.
     *
     * @return SQL string for creating personas table
     */
    static String getCreatePersonasTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS personas ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "uuid TEXT NOT NULL,"
                + "comment TEXT NOT NULL,"
                + "name TEXT NOT NULL,"
                + "created_date " + getBigIntType(selectedPlatform) + " ,"
                + "modified_date " + getBigIntType(selectedPlatform) + " ,"
                + "status_id integer NOT NULL,"
                 + "examiner_id integer NOT NULL,"
                + "CONSTRAINT uuid_unique UNIQUE(uuid),"
                + "FOREIGN KEY (status_id) REFERENCES persona_status(status_id), "
                + "FOREIGN KEY (examiner_id) REFERENCES examiners(id)"
                + ")";
    }
    
    /**
     * Get the SQL String for creating a new persona_alias table in a central
     * repository.
     *
     * @return SQL string for creating persona_alias table
     */
    static String getCreatePersonaAliasTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS persona_alias ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "persona_id " + getBigIntType(selectedPlatform) + " ,"
                + "alias TEXT NOT NULL, "
                + "justification TEXT NOT NULL,"
                + "confidence_id integer NOT NULL,"
                + "date_added " + getBigIntType(selectedPlatform) + " ,"
                + "examiner_id integer NOT NULL,"
                + "FOREIGN KEY (persona_id) REFERENCES personas(id),"
                + "FOREIGN KEY (confidence_id) REFERENCES confidence(confidence_id),"
                + "FOREIGN KEY (examiner_id) REFERENCES examiners(id)"
                + ")";
    }
    
    /**
     * Get the SQL String for creating a new persona_metadata table in a central
     * repository.
     *
     * @return SQL string for creating persona_metadata table
     */
    static String getCreatePersonaMetadataTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS persona_metadata ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "persona_id " + getBigIntType(selectedPlatform) + " ,"
                + "name TEXT NOT NULL,"
                + "value TEXT NOT NULL,"
                + "justification TEXT NOT NULL,"
                + "confidence_id integer NOT NULL,"
                + "date_added " + getBigIntType(selectedPlatform) + " ,"
                + "examiner_id integer NOT NULL,"
                + "CONSTRAINT unique_metadata UNIQUE(persona_id, name),"
                + "FOREIGN KEY (persona_id) REFERENCES personas(id),"
                + "FOREIGN KEY (confidence_id) REFERENCES confidence(confidence_id),"
                + "FOREIGN KEY (examiner_id) REFERENCES examiners(id)"
                + ")";
    }
     
    /**
     * Get the SQL String for creating a new persona_accounts table in a central
     * repository.
     *
     * @return SQL string for creating persona_accounts table
     */
    static String getCreatePersonaAccountsTableStatement(CentralRepoPlatforms selectedPlatform) {

        return "CREATE TABLE IF NOT EXISTS persona_accounts ("
                + getNumericPrimaryKeyClause("id", selectedPlatform)
                + "persona_id " + getBigIntType(selectedPlatform) + " ,"
                + "account_id " + getBigIntType(selectedPlatform) + " ,"
                + "justification TEXT NOT NULL,"
                + "confidence_id integer NOT NULL,"
                + "date_added " + getBigIntType(selectedPlatform) + " ,"
                + "examiner_id integer NOT NULL,"
                + "FOREIGN KEY (persona_id) REFERENCES personas(id),"
                + "FOREIGN KEY (account_id) REFERENCES accounts(id),"
                + "FOREIGN KEY (confidence_id) REFERENCES confidence(confidence_id),"
                + "FOREIGN KEY (examiner_id) REFERENCES examiners(id)"
                + ")";
    }

    
     /**
      * Inserts the default content in persona related tables.
      * 
      * @param conn Database connection to use.
      * @param selectedPlatform The selected platform.
      * 
      * @return True if success, false otherwise.
      */
    static boolean insertDefaultPersonaTablesContent(Connection conn, CentralRepoPlatforms selectedPlatform) {

        try (Statement stmt = conn.createStatement()) {
            // populate the confidence table
            for (Confidence confidence : Persona.Confidence.values()) {
                String sqlString = "INSERT INTO confidence (confidence_id, description) VALUES ( " + confidence.getLevelId() + ", '" + confidence.toString() + "')" //NON-NLS
                        + getOnConflictDoNothingClause(selectedPlatform);
                stmt.execute(sqlString);
            }
            
            // populate the persona_status table
            for (PersonaStatus status : Persona.PersonaStatus.values()) {
                String sqlString = "INSERT INTO persona_status (status_id, status) VALUES ( " + status.getStatusId() + ", '" + status.toString() + "')" //NON-NLS
                        + getOnConflictDoNothingClause(selectedPlatform);
                stmt.execute(sqlString);
            }
            
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, String.format("Failed to populate default data in Persona tables."), ex);
            return false;
        } 
        
        return true;
    }
    
      /**
      * Inserts the default content in accounts related tables.
      * 
      * @param conn Database connection to use.
      * 
      * @return True if success, false otherwise.
      */
    static boolean insertDefaultAccountsTablesContent(Connection conn, CentralRepoPlatforms selectedPlatform) {

        try (Statement stmt = conn.createStatement();) {

            // Populate the account_types table
            for (Account.Type type : Account.Type.PREDEFINED_ACCOUNT_TYPES) {
                if (type != Account.Type.DEVICE) {
                    int correlationTypeId = getCorrelationTypeIdForAccountType(conn, type);
                    if (correlationTypeId > 0) {
                        String sqlString = String.format("INSERT INTO account_types (type_name, display_name, correlation_type_id) VALUES ('%s', '%s', %d)" + getOnConflictDoNothingClause(selectedPlatform),
                                type.getTypeName(), type.getDisplayName(), correlationTypeId);
                        stmt.execute(sqlString);
                    }
                }
            }

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, String.format("Failed to populate default data in account_types table."), ex);
            return false;
        }

        return true;
    }
    
    /**
     * Returns the correlation type id for the given account type, 
     * from the correlation_types table.
     * 
     * @param conn  Connection to use for database query.
     * @param accountType Account type to look for.
     * '
     * @return correlation type id.
     */
    static int getCorrelationTypeIdForAccountType(Connection conn, Account.Type accountType) {

        int typeId = -1;
        if (accountType == Account.Type.EMAIL) {
            typeId = CorrelationAttributeInstance.EMAIL_TYPE_ID;
        } else if (accountType == Account.Type.PHONE) {
            typeId = CorrelationAttributeInstance.PHONE_TYPE_ID;
        } else {
            String querySql = "SELECT * FROM correlation_types WHERE display_name=?";
            try ( PreparedStatement preparedStatementQuery = conn.prepareStatement(querySql)) {
                preparedStatementQuery.setString(1, accountType.getDisplayName());
                try (ResultSet resultSet = preparedStatementQuery.executeQuery();) {
                    if (resultSet.next()) {
                        typeId = resultSet.getInt("id");
                    }
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, String.format("Failed to get correlation typeId for account type %s.", accountType.getTypeName()), ex);
            } 
        }

        return typeId;
    }
}
