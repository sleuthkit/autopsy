/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import static org.sleuthkit.autopsy.centralrepository.datamodel.RdbmsCentralRepo.SOFTWARE_CR_DB_SCHEMA_VERSION;
import org.sleuthkit.autopsy.coreutils.Logger;
//import static org.sleuthkit.autopsy.centralrepository.datamodel.SqliteCentralRepoSettings.getCreateArtifactInstancesTableTemplate;

/**
 * Creates the CR schema and populates it with initial data.
 *
 */
public class RdbmsCentralRepoSchemaFactory {

    private final static Logger LOGGER = Logger.getLogger(RdbmsCentralRepoSchemaFactory.class.getName());

    private static RdbmsCentralRepoSchemaFactory instance;
    private final RdbmsCentralRepo rdbmsCentralRepo;

    private final CentralRepoPlatforms selectedPlatform;

    // SQLite pragmas
    private final static String PRAGMA_SYNC_OFF = "PRAGMA synchronous = OFF";
    private final static String PRAGMA_SYNC_NORMAL = "PRAGMA synchronous = NORMAL";
    private final static String PRAGMA_JOURNAL_WAL = "PRAGMA journal_mode = WAL";
    private final static String PRAGMA_READ_UNCOMMITTED_TRUE = "PRAGMA read_uncommitted = True";
    private final static String PRAGMA_ENCODING_UTF8 = "PRAGMA encoding = 'UTF-8'";
    private final static String PRAGMA_PAGE_SIZE_4096 = "PRAGMA page_size = 4096";
    private final static String PRAGMA_FOREIGN_KEYS_ON = "PRAGMA foreign_keys = ON";

    /**
     * Returns instance of singleton.
     *
     * @throws CentralRepoException
     */
//    public static RdbmsCentralRepoSchemaFactory getInstance() throws CentralRepoException {
//
//        if (instance == null) {
//            instance = new RdbmsCentralRepoSchemaFactory();
//        }
//
//        return instance;
//    }
    public RdbmsCentralRepoSchemaFactory(CentralRepoPlatforms selectedPlatform) throws CentralRepoException {
        //CentralRepoPlatforms selectedPlatform = CentralRepoPlatforms.DISABLED;
        //if (CentralRepoDbUtil.allowUseOfCentralRepository()) {
        //    selectedPlatform = CentralRepoPlatforms.getSelectedPlatform();
        //}

        this.selectedPlatform = selectedPlatform;
        switch (selectedPlatform) {
            case POSTGRESQL:
                rdbmsCentralRepo = PostgresCentralRepo.getInstance();
                break;
            case SQLITE:
                rdbmsCentralRepo = SqliteCentralRepo.getInstance();
                break;
            default:
                throw new CentralRepoException("Central Repo platform disabled.");
        }
    }

    public boolean initializeDatabaseSchema() {
        
        // RA TEMP - call the method from platform specific inner class.  
        // Eventually those two methods need to get unified here
        
        switch (selectedPlatform) {
            case POSTGRESQL:
                // RAMAN TBD
               return PostgresCRSchemaCreator.initializeDatabaseSchema(rdbmsCentralRepo, selectedPlatform);
                //break;
            case SQLITE:
                return SQLiteCRSchemaCreator.initializeDatabaseSchema(rdbmsCentralRepo, selectedPlatform);
                //break;
            default:
                return false;
        }
    }

    // TBD RAMAN - temporary container to store all SQLite methods, till we unify...
    public static class SQLiteCRSchemaCreator {

        /**
         * Initialize the database schema.
         *
         * Requires valid connectionPool.
         *
         * This method is called from within connect(), so we cannot call
         * connect() to get a connection. This method is called after
         * setupConnectionPool(), so it is safe to assume that a valid
         * connectionPool exists. The implementation of connect() is
         * synchronized, so we can safely use the connectionPool object
         * directly.
         */
        public static boolean initializeDatabaseSchema(RdbmsCentralRepo rdbmsCentralRepo, CentralRepoPlatforms selectedPlatform ) {
            // The "id" column is an alias for the built-in 64-bit int "rowid" column.
            // It is autoincrementing by default and must be of type "integer primary key".
            // We've omitted the autoincrement argument because we are not currently
            // using the id value to search for specific rows, so we do not care
            // if a rowid is re-used after an existing rows was previously deleted.
            StringBuilder createOrganizationsTable = new StringBuilder();
            createOrganizationsTable.append("CREATE TABLE IF NOT EXISTS organizations (");
            
            createOrganizationsTable.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            //createOrganizationsTable.append("id integer primary key autoincrement NOT NULL,");
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
            createCasesTable.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            //createCasesTable.append("id integer primary key autoincrement NOT NULL,");
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
            createCasesTable.append("CONSTRAINT case_uid_unique UNIQUE(case_uid)" ).append(getOnConflictIgnoreClause(selectedPlatform));
            createCasesTable.append(")");

            // NOTE: when there are few cases in the cases table, these indices may not be worthwhile
            String casesIdx1 = "CREATE INDEX IF NOT EXISTS cases_org_id ON cases (org_id)";
            String casesIdx2 = "CREATE INDEX IF NOT EXISTS cases_case_uid ON cases (case_uid)";

            StringBuilder createReferenceSetsTable = new StringBuilder();
            createReferenceSetsTable.append("CREATE TABLE IF NOT EXISTS reference_sets (");
            createReferenceSetsTable.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            //createReferenceSetsTable.append("id integer primary key autoincrement NOT NULL,");
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
            createReferenceTypesTableTemplate.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            //createReferenceTypesTableTemplate.append("id integer primary key autoincrement NOT NULL,");
            createReferenceTypesTableTemplate.append("reference_set_id integer,");
            createReferenceTypesTableTemplate.append("value text NOT NULL,");
            createReferenceTypesTableTemplate.append("known_status integer NOT NULL,");
            createReferenceTypesTableTemplate.append("comment text,");
            createReferenceTypesTableTemplate.append("CONSTRAINT %s_multi_unique UNIQUE(reference_set_id, value)").append(getOnConflictIgnoreClause(selectedPlatform)).append(",");
            createReferenceTypesTableTemplate.append("foreign key (reference_set_id) references reference_sets(id) ON UPDATE SET NULL ON DELETE SET NULL");
            createReferenceTypesTableTemplate.append(")");

            // Each "%s" will be replaced with the relevant reference_TYPE table name.
            String referenceTypesIdx1 = "CREATE INDEX IF NOT EXISTS %s_value ON %s (value)";
            String referenceTypesIdx2 = "CREATE INDEX IF NOT EXISTS %s_value_known_status ON %s (value, known_status)";

            StringBuilder createCorrelationTypesTable = new StringBuilder();
            createCorrelationTypesTable.append("CREATE TABLE IF NOT EXISTS correlation_types (");
            createCorrelationTypesTable.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            //createCorrelationTypesTable.append("id integer primary key autoincrement NOT NULL,");
            createCorrelationTypesTable.append("display_name text NOT NULL,");
            createCorrelationTypesTable.append("db_table_name text NOT NULL,");
            createCorrelationTypesTable.append("supported integer NOT NULL,");
            createCorrelationTypesTable.append("enabled integer NOT NULL,");
            createCorrelationTypesTable.append("CONSTRAINT correlation_types_names UNIQUE (display_name, db_table_name)");
            createCorrelationTypesTable.append(")");

            String createArtifactInstancesTableTemplate = getCreateArtifactInstancesTableTemplate(selectedPlatform);

            String instancesCaseIdIdx = getAddCaseIdIndexTemplate();
            String instancesDatasourceIdIdx = getAddDataSourceIdIndexTemplate();
            String instancesValueIdx = getAddValueIndexTemplate();
            String instancesKnownStatusIdx = getAddKnownStatusIndexTemplate();
            String instancesObjectIdIdx = getAddObjectIdIndexTemplate();

            // NOTE: the db_info table currenly only has 1 row, so having an index
            // provides no benefit.
            Connection conn = null;
            try {
                conn = rdbmsCentralRepo.getEphemeralConnection();
                if (null == conn) {
                    return false;
                }
                Statement stmt = conn.createStatement();
                
                // RA TBD: this PRAGMA code is SQLIte spcific
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

                stmt.execute(getCreateDataSourcesTableStatement(selectedPlatform));
                stmt.execute(getAddDataSourcesNameIndexStatement());
                stmt.execute(getAddDataSourcesObjectIdIndexStatement());

                stmt.execute(createReferenceSetsTable.toString());
                stmt.execute(referenceSetsIdx1);

                stmt.execute(createCorrelationTypesTable.toString());

                /*
             * Note that the essentially useless id column in the following
             * table is required for backwards compatibility. Otherwise, the
             * name column could be the primary key.
                 */
                StringBuilder dbInfoTable = new StringBuilder();
                dbInfoTable.append("CREATE TABLE db_info (");
                dbInfoTable.append(getNumericPrimaryKeyClause("id", selectedPlatform));
                dbInfoTable.append("name TEXT UNIQUE NOT NULL,");
                dbInfoTable.append("value TEXT NOT NULL )");

                stmt.execute(dbInfoTable.toString());
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.SCHEMA_MAJOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMajor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.SCHEMA_MINOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMinor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.CREATION_SCHEMA_MAJOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMajor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.CREATION_SCHEMA_MINOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMinor() + "')");

                // Create a separate instance and reference table for each artifact type
                List<CorrelationAttributeInstance.Type> DEFAULT_CORRELATION_TYPES = CorrelationAttributeInstance.getDefaultCorrelationTypes();

                String reference_type_dbname;
                String instance_type_dbname;
                for (CorrelationAttributeInstance.Type type : DEFAULT_CORRELATION_TYPES) {
                    reference_type_dbname = CentralRepoDbUtil.correlationTypeToReferenceTableName(type);
                    instance_type_dbname = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);

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
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, "Error getting default correlation types. Likely due to one or more Type's with an invalid db table name."); // NON-NLS
                return false;
            } finally {
                CentralRepoDbUtil.closeConnection(conn);
            }
            return true;
        }

        

       

        

       

        

        

       

       

       

    }

    // TBD RAMAN - temporary container to store all Pstgres methods, till we unify...
    public static class PostgresCRSchemaCreator {

        /**
         * Initialize the database schema.
         *
         * Requires valid connectionPool.
         *
         * This method is called from within connect(), so we cannot call
         * connect() to get a connection. This method is called after
         * setupConnectionPool(), so it is safe to assume that a valid
         * connectionPool exists. The implementation of connect() is
         * synchronized, so we can safely use the connectionPool object
         * directly.
         */
        public static boolean initializeDatabaseSchema(RdbmsCentralRepo rdbmsCentralRepo, CentralRepoPlatforms selectedPlatform) {
            // The "id" column is an alias for the built-in 64-bit int "rowid" column.
            // It is autoincrementing by default and must be of type "integer primary key".
            // We've omitted the autoincrement argument because we are not currently
            // using the id value to search for specific rows, so we do not care
            // if a rowid is re-used after an existing rows was previously deleted.
            StringBuilder createOrganizationsTable = new StringBuilder();
            createOrganizationsTable.append("CREATE TABLE IF NOT EXISTS organizations (");
            //createOrganizationsTable.append("id SERIAL PRIMARY KEY,");
            createOrganizationsTable.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
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
            createCasesTable.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            //createCasesTable.append("id SERIAL PRIMARY KEY,");
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
            //createCasesTable.append("CONSTRAINT case_uid_unique UNIQUE (case_uid)");
            createCasesTable.append("CONSTRAINT case_uid_unique UNIQUE (case_uid)" ).append(getOnConflictIgnoreClause(selectedPlatform));
            createCasesTable.append(")");

            // NOTE: when there are few cases in the cases table, these indices may not be worthwhile
            String casesIdx1 = "CREATE INDEX IF NOT EXISTS cases_org_id ON cases (org_id)";
            String casesIdx2 = "CREATE INDEX IF NOT EXISTS cases_case_uid ON cases (case_uid)";

            StringBuilder createReferenceSetsTable = new StringBuilder();
            createReferenceSetsTable.append("CREATE TABLE IF NOT EXISTS reference_sets (");
            createReferenceSetsTable.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            //createReferenceSetsTable.append("id SERIAL PRIMARY KEY,");
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
            createReferenceTypesTableTemplate.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            createReferenceTypesTableTemplate.append("reference_set_id integer,");
            createReferenceTypesTableTemplate.append("value text NOT NULL,");
            createReferenceTypesTableTemplate.append("known_status integer NOT NULL,");
            createReferenceTypesTableTemplate.append("comment text,");
            createReferenceTypesTableTemplate.append("CONSTRAINT %s_multi_unique UNIQUE(reference_set_id, value)").append(getOnConflictIgnoreClause(selectedPlatform)).append(",");
            createReferenceTypesTableTemplate.append("foreign key (reference_set_id) references reference_sets(id) ON UPDATE SET NULL ON DELETE SET NULL");
            createReferenceTypesTableTemplate.append(")");

            // Each "%s" will be replaced with the relevant reference_TYPE table name.
            String referenceTypesIdx1 = "CREATE INDEX IF NOT EXISTS %s_value ON %s (value)";
            String referenceTypesIdx2 = "CREATE INDEX IF NOT EXISTS %s_value_known_status ON %s (value, known_status)";

            StringBuilder createCorrelationTypesTable = new StringBuilder();
            createCorrelationTypesTable.append("CREATE TABLE IF NOT EXISTS correlation_types (");
            createCorrelationTypesTable.append(getNumericPrimaryKeyClause("id", selectedPlatform ));
            createCorrelationTypesTable.append("display_name text NOT NULL,");
            createCorrelationTypesTable.append("db_table_name text NOT NULL,");
            createCorrelationTypesTable.append("supported integer NOT NULL,");
            createCorrelationTypesTable.append("enabled integer NOT NULL,");
            createCorrelationTypesTable.append("CONSTRAINT correlation_types_names UNIQUE (display_name, db_table_name)");
            createCorrelationTypesTable.append(")");

            String createArtifactInstancesTableTemplate = getCreateArtifactInstancesTableTemplate(selectedPlatform);

            String instancesCaseIdIdx = getAddCaseIdIndexTemplate();
            String instancesDatasourceIdIdx = getAddDataSourceIdIndexTemplate();
            String instancesValueIdx = getAddValueIndexTemplate();
            String instancesKnownStatusIdx = getAddKnownStatusIndexTemplate();
            String instancesObjectIdIdx = getAddObjectIdIndexTemplate();

            // NOTE: the db_info table currenly only has 1 row, so having an index
            // provides no benefit.
            Connection conn = null;
            try {
                conn = rdbmsCentralRepo.getEphemeralConnection();
                if (null == conn) {
                    return false;
                }
                Statement stmt = conn.createStatement();

                stmt.execute(createOrganizationsTable.toString());

                stmt.execute(createCasesTable.toString());
                stmt.execute(casesIdx1);
                stmt.execute(casesIdx2);

                stmt.execute(getCreateDataSourcesTableStatement(selectedPlatform));
                stmt.execute(getAddDataSourcesNameIndexStatement());
                stmt.execute(getAddDataSourcesObjectIdIndexStatement());

                stmt.execute(createReferenceSetsTable.toString());
                stmt.execute(referenceSetsIdx1);

                stmt.execute(createCorrelationTypesTable.toString());

                /*
                * Note that the essentially useless id column in the following
                * table is required for backwards compatibility. Otherwise, the
                * name column could be the primary key.
                */
                StringBuilder dbInfoTable = new StringBuilder();
                dbInfoTable.append("CREATE TABLE db_info (");
                dbInfoTable.append(getNumericPrimaryKeyClause("id", selectedPlatform));
                dbInfoTable.append("name TEXT UNIQUE NOT NULL,");
                dbInfoTable.append("value TEXT NOT NULL )");

                stmt.execute(dbInfoTable.toString());
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.SCHEMA_MAJOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMajor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.SCHEMA_MINOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMinor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.CREATION_SCHEMA_MAJOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMajor() + "')");
                stmt.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.CREATION_SCHEMA_MINOR_VERSION_KEY + "', '" + SOFTWARE_CR_DB_SCHEMA_VERSION.getMinor() + "')");

                // Create a separate instance and reference table for each correlation type
                List<CorrelationAttributeInstance.Type> DEFAULT_CORRELATION_TYPES = CorrelationAttributeInstance.getDefaultCorrelationTypes();

                String reference_type_dbname;
                String instance_type_dbname;
                for (CorrelationAttributeInstance.Type type : DEFAULT_CORRELATION_TYPES) {
                    reference_type_dbname = CentralRepoDbUtil.correlationTypeToReferenceTableName(type);
                    instance_type_dbname = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);

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
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, "Error getting default correlation types. Likely due to one or more Type's with an invalid db table name."); // NON-NLS
                return false;
            } finally {
                CentralRepoDbUtil.closeConnection(conn);
            }
            return true;
        }
        
        

    }
    
    public boolean insertDefaultDatabaseContent() {
        Connection conn = rdbmsCentralRepo.getEphemeralConnection();
        if (null == conn) {
            return false;
        }

        boolean result = CentralRepoDbUtil.insertDefaultCorrelationTypes(conn) && CentralRepoDbUtil.insertDefaultOrganization(conn);
        CentralRepoDbUtil.closeConnection(conn);
        return result;
    }
    
    /**
         * Get the template String for creating a new _instances table in a
         * Sqlite central repository. %s will exist in the template where the
         * name of the new table will be addedd.
         *
         * @return a String which is a template for cretating a new _instances
         * table
         */
        static String getCreateArtifactInstancesTableTemplate(CentralRepoPlatforms selectedPlatform) {
            // Each "%s" will be replaced with the relevant TYPE_instances table name.
            return "CREATE TABLE IF NOT EXISTS %s ("
                    + getNumericPrimaryKeyClause("id", selectedPlatform )
                    + "case_id integer NOT NULL,"
                    + "data_source_id integer NOT NULL," 
                    + "value text NOT NULL,"
                    + "file_path text NOT NULL,"
                    + "known_status integer NOT NULL,"
                    + "comment text,"
                    + "file_obj_id BIGINT,"
                    + "CONSTRAINT %s_multi_unique UNIQUE(data_source_id, value, file_path)" +  getOnConflictIgnoreClause(selectedPlatform) + ","
                    + "foreign key (case_id) references cases(id) ON UPDATE SET NULL ON DELETE SET NULL,"
                    + "foreign key (data_source_id) references data_sources(id) ON UPDATE SET NULL ON DELETE SET NULL)";
        }
        
        
         /**
         * Get the statement String for creating a new data_sources table in a
         * Sqlite central repository.
         *
         * @return a String which is a statement for creating a new
         * data_sources table
         */
        static String getCreateDataSourcesTableStatement(CentralRepoPlatforms selectedPlatform) {
            return "CREATE TABLE IF NOT EXISTS data_sources ("
                     + getNumericPrimaryKeyClause("id", selectedPlatform )
                    + "case_id integer NOT NULL,"
                    + "device_id text NOT NULL,"
                    + "name text NOT NULL,"
                    + "datasource_obj_id integer,"      // RAMAN TBD: does integer suffice for PostGres.  Old code used integer in some places but used BIGINT in other.
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
     * be addedd.
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
     * table will be addedd.
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
     * table will be addedd.
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
     *
     * @return SQL clause to be used in a Create table statement
     */
    // RA TEMP: selectedPlatform is passed as argument, eventually this should be used from the class and this method should not be static
    private static String getNumericPrimaryKeyClause(String pkName, CentralRepoPlatforms selectedPlatform) {
        switch (selectedPlatform) {
            case POSTGRESQL:
                return String.format(" %s SERIAL PRIMARY KEY, ", pkName);
            case SQLITE:
                return String.format(" %s integer primary key autoincrement NOT NULL ,", pkName);
        }
        return "";
    }
    
     /**
     * Returns ON CONFLICT IGNORE clause for the specified database platform.
     *
     *
     * @return SQL clause.
     */
    // RA TEMP: selectedPlatform is passed as argument, eventually this should be used from the class and this method should not be static
    private static String getOnConflictIgnoreClause(CentralRepoPlatforms selectedPlatform) {
        switch (selectedPlatform) {
            case POSTGRESQL:
                return "";
            case SQLITE:
                return " ON CONFLICT IGNORE ";
        }
        return "";
    }
}
