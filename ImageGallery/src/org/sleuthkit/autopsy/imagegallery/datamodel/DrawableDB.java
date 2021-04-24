/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.SortOrder;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileTypeUtils;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryModule;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupKey;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupSortBy;
import static org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupSortBy.GROUP_BY_VALUE;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbAccessQueryCallback;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.DbType;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.VersionNumber;
import org.sqlite.SQLiteJDBCLoader;
import java.util.stream.Collectors;
import org.sleuthkit.datamodel.TagSet;

/**
 * Provides access to the image gallery database and selected tables in the case
 * database.
 */
public final class DrawableDB {

    private static final Logger logger = Logger.getLogger(DrawableDB.class.getName());

    /*
     * Schema version management constants.
     */
    private static final VersionNumber IG_STARTING_SCHEMA_VERSION = new VersionNumber(1, 0, 0); // Historical - DO NOT CHANGE
    private static final VersionNumber IG_SCHEMA_VERSION = new VersionNumber(1, 2, 0); // Current schema version
    private static final String IG_SCHEMA_MAJOR_VERSION_KEY = "IG_SCHEMA_MAJOR_VERSION";
    private static final String IG_SCHEMA_MINOR_VERSION_KEY = "IG_SCHEMA_MINOR_VERSION";
    private static final String IG_CREATION_SCHEMA_MAJOR_VERSION_KEY = "IG_CREATION_SCHEMA_MAJOR_VERSION";
    private static final String IG_CREATION_SCHEMA_MINOR_VERSION_KEY = "IG_CREATION_SCHEMA_MINOR_VERSION";
    private static final String DB_INFO_TABLE_NAME = "image_gallery_db_info";

    /*
     * The image gallery stores data in both the case database and the image
     * gallery database. The use of image gallery tables in the case database
     * enables sharing of selected data between users of multi-user cases. This
     * is necessary because the image gallery database is otherwise private to
     * one node/machine.
     *
     * TODO: Consider refactoring to separate the image gallery database code
     * from the case database code.
     */
    private static final String CASE_DB_GROUPS_TABLENAME = "image_gallery_groups"; //NON-NLS
    private static final String CASE_DB_GROUPS_SEEN_TABLENAME = "image_gallery_groups_seen"; //NON-NLS
    private final SleuthkitCase caseDb;

    /*
     * The image gallery database is an SQLite database, so it has a local file
     * path. For multi-user cases, there is a private image gallery database for
     * each node/machine.
     */
    private final Path dbPath;

    /*
     * The write lock of a reentrant read-write lock is used to serialize access
     * to the image gallery database. Empirically, this provides better
     * performance than relying on internal SQLite locking.
     */
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); //use fairness policy
    private final Lock dbLock = rwLock.writeLock();
    @GuardedBy("dbLock")
    private Connection con;

    /*
     * Prepared statements.
     */
    private List<PreparedStatement> preparedStatements = new ArrayList<>();
    private PreparedStatement selectCountDataSourceIDs;
    private PreparedStatement insertDataSourceStmt;
    private PreparedStatement updateDataSourceStmt;
    private PreparedStatement deleteDataSourceStmt;
    private PreparedStatement insertFileStmt;
    private PreparedStatement updateFileStmt;
    private PreparedStatement deleteFileStmt;
    private PreparedStatement insertHashSetStmt;
    private PreparedStatement selectHashSetStmt;
    private PreparedStatement selectHashSetNamesStmt;
    private PreparedStatement insertHashHitStmt;
    private PreparedStatement deleteHashHitStmt;
    private PreparedStatement pathGroupStmt; // Not unused, used via collections below
    private PreparedStatement nameGroupStmt; // Not unused, used via collections below
    private PreparedStatement createdTimeGroupStmt; // Not unused, used via collections below
    private PreparedStatement modifiedTimeGroupStmt; // Not unused, used via collections below
    private PreparedStatement makeGroupStmt; // Not unused, used via collections below
    private PreparedStatement modelGroupStmt; // Not unused, used via collections below
    private PreparedStatement analyzedGroupStmt; // Not unused, used via collections below
    private PreparedStatement hashSetGroupStmt; // Not unused, used via collections below
    private PreparedStatement pathGroupFilterByDataSrcStmt; // Not unused, used via collections below
    private final Map<DrawableAttribute<?>, PreparedStatement> groupStatementMap = new HashMap<>();
    private final Map<DrawableAttribute<?>, PreparedStatement> groupStatementFilterByDataSrcMap = new HashMap<>();

    /*
     * Various caches are used to reduce the need for database queries.
     */
    private final Cache<String, Boolean> groupCache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();
    private final Cache<GroupKey<?>, Boolean> groupSeenCache = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();
    private final Set<Long> hasTagsCache = new HashSet<>(); // Object IDs of files with tags
    private final Set<Long> hasHashHitsCache = new HashSet<>(); // Object IDs of files with hash set hits
    private final Set<Long> hasExifDataCache = new HashSet<>(); // Object IDs of files with EXIF data (make/model)
    private final Object cacheLock = new Object();
    private boolean areCachesLoaded = false;
    private int cacheBuildCount = 0; // Number of tasks that requested the caches be built

    /*
     * This class is coupled to the image gallery controller and group manager.
     *
     * TODO: It would be better to reduce the coupling so that the controller
     * and group manager call this class, but this class does not call them.
     */
    private final ImageGalleryController controller;
    private final GroupManager groupManager;

    /*
     * Make sure the SQLite JDBC driver is loaded.
     */
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, "Failed to load sqlite JDBC driver", ex); //NON-NLS
        }
    }

    /**
     * Enum for tracking the status of the image gallery database with respect
     * to the data sources in the case.
     *
     * IMPORTANT: ADD NEW STATUSES TO THE END OF THE LIST
     *
     * TODO: I'm (RC) not sure why this is required, it looks like the enum
     * element names are stored in the image gallery database. Are the raw
     * cardinal values used somewhere?
     */
    public enum DrawableDbBuildStatusEnum {
        /**
         * The data source has been added to the database, but no other data
         * pertaining to it has been added.
         */
        UNKNOWN,
        /**
         * Analysis (an ingest job or image gallery database rebuild) for the
         * data source is in progress.
         */
        IN_PROGRESS,
        /**
         * Analysis (an ingest job or image gallery database rebuild) for the
         * data source has been completed and at least one file in the data
         * source has a MIME type (ingest filters may have been applied, so some
         * files may not have been typed).
         */
        COMPLETE,
        /**
         * Analysis (an ingest job or image gallery database rebuild) for the
         * data source has been completed, but the files for the data source
         * were not assigned a MIME type (file typing was not enabled).
         */
        REBUILT_STALE;
    }

    private void dbWriteLock() {
        dbLock.lock();
    }

    private void dbWriteUnlock() {
        dbLock.unlock();
    }

    /**
     * Constructs an object that provides access to the drawables database and
     * selected tables in the case database. If the specified drawables database
     * does not already exist, it is created.
     *
     * @param dbPath     The path to the drawables database file.
     * @param controller The controller for the IMage Gallery tool.
     *
     * @throws IOException      The database directory could not be created.
     * @throws SQLException     The drawables database could not be created or
     *                          opened.
     * @throws TskCoreException The drawables database or the case database
     *                          could not be correctly initialized for Image
     *                          Gallery use.
     */
    private DrawableDB(Path dbPath, ImageGalleryController controller, TagSet standardCategories) throws IOException, SQLException, TskCoreException {
        this.dbPath = dbPath;
        this.controller = controller;
        caseDb = this.controller.getCaseDatabase();
        groupManager = this.controller.getGroupManager();
        Files.createDirectories(this.dbPath.getParent());
        dbWriteLock();
        try {
            con = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString()); //NON-NLS
            if (!initializeDBSchema() || !upgradeDBSchema() || !prepareStatements() || !initializeStandardGroups(standardCategories) || !removeDeletedDataSources() || !initializeImageList()) {
                close();
                throw new TskCoreException("Failed to initialize drawables database for Image Gallery use"); //NON-NLS
            }
        } finally {
            dbWriteUnlock();
        }
    }

    private boolean prepareStatements() {
        try {
            insertDataSourceStmt = prepareStatement("INSERT INTO datasources (ds_obj_id, drawable_db_build_status) VALUES (?,?) ON CONFLICT (ds_obj_id) DO UPDATE SET drawable_db_build_status = ?"); //NON-NLS            
            deleteDataSourceStmt = prepareStatement("DELETE FROM datasources where ds_obj_id = ?"); //NON-NLS
            insertFileStmt = prepareStatement("INSERT OR IGNORE INTO drawable_files (obj_id, data_source_obj_id, path, name, created_time, modified_time, make, model, analyzed) VALUES (?,?,?,?,?,?,?,?,?)"); //NON-NLS
            updateFileStmt = prepareStatement("INSERT INTO drawable_files (obj_id, data_source_obj_id, path, name, created_time, modified_time, make, model, analyzed) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (obj_id) DO UPDATE SET data_source_obj_id = ?, path = ?, name = ?, created_time = ?, modified_time = ?, make = ?, model = ?, analyzed = ?"); //NON-NLS
            deleteFileStmt = prepareStatement("DELETE FROM drawable_files WHERE obj_id = ?"); //NON-NLS
            insertHashSetStmt = prepareStatement("INSERT OR IGNORE INTO hash_sets (hash_set_name)  VALUES (?)"); //NON-NLS
            selectHashSetStmt = prepareStatement("SELECT hash_set_id FROM hash_sets WHERE hash_set_name = ?"); //NON-NLS
            selectHashSetNamesStmt = prepareStatement("SELECT DISTINCT hash_set_name FROM hash_sets"); //NON-NLS
            insertHashHitStmt = prepareStatement("INSERT OR IGNORE INTO hash_set_hits (hash_set_id, obj_id) VALUES (?,?)"); //NON-NLS
            deleteHashHitStmt = prepareStatement("DELETE FROM hash_set_hits WHERE obj_id = ?"); //NON-NLS
            pathGroupStmt = prepareStatement("SELECT obj_id , analyzed FROM drawable_files WHERE path  = ? ", DrawableAttribute.PATH); //NON-NLS
            nameGroupStmt = prepareStatement("SELECT obj_id , analyzed FROM drawable_files WHERE  name  = ? ", DrawableAttribute.NAME); //NON-NLS
            createdTimeGroupStmt = prepareStatement("SELECT obj_id , analyzed FROM drawable_files WHERE created_time  = ? ", DrawableAttribute.CREATED_TIME); //NON-NLS
            modifiedTimeGroupStmt = prepareStatement("SELECT obj_id , analyzed FROM drawable_files WHERE  modified_time  = ? ", DrawableAttribute.MODIFIED_TIME); //NON-NLS
            makeGroupStmt = prepareStatement("SELECT obj_id , analyzed FROM drawable_files WHERE make  = ? ", DrawableAttribute.MAKE); //NON-NLS
            modelGroupStmt = prepareStatement("SELECT obj_id , analyzed FROM drawable_files WHERE model  = ? ", DrawableAttribute.MODEL); //NON-NLS
            analyzedGroupStmt = prepareStatement("SELECT obj_id , analyzed FROM drawable_files WHERE analyzed = ?", DrawableAttribute.ANALYZED); //NON-NLS
            hashSetGroupStmt = prepareStatement("SELECT drawable_files.obj_id AS obj_id, analyzed FROM drawable_files ,  hash_sets , hash_set_hits  WHERE drawable_files.obj_id = hash_set_hits.obj_id AND hash_sets.hash_set_id = hash_set_hits.hash_set_id AND hash_sets.hash_set_name = ?", DrawableAttribute.HASHSET); //NON-NLS
            pathGroupFilterByDataSrcStmt = prepareFilterByDataSrcStatement("SELECT obj_id , analyzed FROM drawable_files WHERE path  = ? AND data_source_obj_id = ?", DrawableAttribute.PATH);
            return true;

        } catch (TskCoreException | SQLException ex) {
            logger.log(Level.SEVERE, "Failed to prepare all statements", ex); //NON-NLS
            return false;
        }
    }

    private boolean initializeStandardGroups(TagSet standardCategories) {
        CaseDbTransaction caseDbTransaction = null;
        try {
            caseDbTransaction = caseDb.beginTransaction();

            for(TagName tagName: standardCategories.getTagNames()) {
                insertGroup(tagName.getDisplayName(), DrawableAttribute.CATEGORY, caseDbTransaction);
            }
            caseDbTransaction.commit();
            return true;
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to insert standard groups", ex); //NON-NLS
            if (null != caseDbTransaction) {
                try {
                    caseDbTransaction.rollback();
                } catch (TskCoreException ex2) {
                    logger.log(Level.SEVERE, "Failed to roll back case DB transaction", ex2);
                }
            }
            return false;
        }
    }

    /**
     * create PreparedStatement with the supplied string, and add the new
     * statement to the list of PreparedStatements used in {@link DrawableDB#closeStatements()
     *
     * @param stmtString the string representation of the sqlite statement to
     *                   prepare
     *
     * @return the prepared statement
     *
     * @throws SQLException if unable to prepare the statement
     */
    private PreparedStatement prepareStatement(String stmtString) throws TskCoreException, SQLException {
        dbWriteLock();
        try {
            if (isClosed()) {
                throw new TskCoreException("The drawables database is closed");
            }
            PreparedStatement statement = con.prepareStatement(stmtString);
            preparedStatements.add(statement);
            return statement;
        } catch (SQLException ex) {
            throw new SQLException(String.format("Error preparing statement %s", stmtString, ex));
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * calls {@link DrawableDB#prepareStatement(java.lang.String) ,
     *  and then add the statement to the groupStatmentMap used to lookup
     * statements by the attribute/column they group on
     *
     * @param stmtString the string representation of the sqlite statement to
     *                   prepare
     * @param attr       the {@link DrawableAttribute} this query groups by
     *
     * @return the prepared statement
     *
     * @throws SQLExceptionif unable to prepare the statement
     */
    private PreparedStatement prepareStatement(String stmtString, DrawableAttribute<?> attr) throws TskCoreException, SQLException {
        PreparedStatement statement = prepareStatement(stmtString);
        if (attr != null) {
            groupStatementMap.put(attr, statement);
        }
        return statement;
    }

    /**
     * calls {@link DrawableDB#prepareStatement(java.lang.String) ,
     *  and then add the statement to the groupStatementFilterByDataSrcMap map used to lookup
     * statements by the attribute/column they group on
     *
     * @param stmtString the string representation of the sqlite statement to
     *                   prepare
     * @param attr       the {@link DrawableAttribute} this query groups by
     *      *
     * @return the prepared statement
     *
     * @throws SQLExceptionif unable to prepare the statement
     */
    private PreparedStatement prepareFilterByDataSrcStatement(String stmtString, DrawableAttribute<?> attr) throws TskCoreException, SQLException {
        PreparedStatement statement = prepareStatement(stmtString);
        if (attr != null) {
            groupStatementFilterByDataSrcMap.put(attr, statement);
        }
        return statement;
    }

    private void setQueryParams(PreparedStatement statement, GroupKey<?> groupKey) throws SQLException {

        statement.setObject(1, groupKey.getValue());

        if (groupKey.getDataSource().isPresent()
                && (groupKey.getAttribute() == DrawableAttribute.PATH)) {
            statement.setObject(2, groupKey.getDataSourceObjId());
        }
    }

    /**
     * Removes any data sources from the local drawables database that have been
     * deleted from the case database. This is necessary for multi-user cases
     * where the case database is shared, but each user has his or her own local
     * drawables database and may not have had the case open when a data source
     * was deleted.
     *
     * @return True on success, false on failure.
     */
    private boolean removeDeletedDataSources() {
        dbWriteLock();
        try (SleuthkitCase.CaseDbQuery caseDbQuery = caseDb.executeQuery("SELECT obj_id FROM data_source_info"); //NON-NLS
                Statement drawablesDbStmt = con.createStatement()) {
            /*
             * Get the data source object IDs from the case database.
             */
            ResultSet caseDbResults = caseDbQuery.getResultSet();
            Set<Long> currentDataSourceObjIDs = new HashSet<>();
            while (caseDbResults.next()) {
                currentDataSourceObjIDs.add(caseDbResults.getLong(1));
            }

            /*
             * Get the data source object IDs from the drawables database and
             * determine which ones, if any, have been deleted from the case
             * database.
             */
            List<Long> staleDataSourceObjIDs = new ArrayList<>();
            try (ResultSet drawablesDbResults = drawablesDbStmt.executeQuery("SELECT ds_obj_id FROM datasources")) { //NON-NLS
                while (drawablesDbResults.next()) {
                    long dataSourceObjID = drawablesDbResults.getLong(1);
                    if (!currentDataSourceObjIDs.contains(dataSourceObjID)) {
                        staleDataSourceObjIDs.add(dataSourceObjID);
                    }
                }
            }

            /*
             * Delete the surplus data sources from this local drawables
             * database. The delete cascades.
             */
            if (!staleDataSourceObjIDs.isEmpty()) {
                String deleteCommand = "DELETE FROM datasources where ds_obj_id IN (" + StringUtils.join(staleDataSourceObjIDs, ',') + ")"; //NON-NLS
                drawablesDbStmt.execute(deleteCommand);
            }
            return true;

        } catch (TskCoreException | SQLException ex) {
            logger.log(Level.SEVERE, "Failed to remove deleted data sources from drawables database", ex); //NON-NLS
            return false;

        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * Public factory method. Creates and opens a connection to a new database *
     * at the given path. If there is already a db at the path, it is checked
     * for compatibility, and deleted if it is incompatible, before a connection
     * is opened.
     *
     * @param controller
     *
     * @return A DrawableDB for the given controller.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public static DrawableDB getDrawableDB(ImageGalleryController controller, TagSet standardCategories) throws TskCoreException {
        Path dbPath = ImageGalleryModule.getModuleOutputDir(controller.getCase()).resolve("drawable.db");
        try {
            deleteDatabaseIfOlderVersion(dbPath);
        } catch (SQLException ex) {
            throw new TskCoreException("Failed to check for obsolete drawables database schema", ex); //NON-NLS
        } catch (IOException ex) {
            throw new TskCoreException("Failed to delete obsolete drawables database", ex); //NON-NLS
        }

        try {
            return new DrawableDB(dbPath, controller, standardCategories);
        } catch (IOException ex) {
            throw new TskCoreException("Failed to create drawables database directory", ex); //NON-NLS
        } catch (SQLException ex) {
            throw new TskCoreException("Failed to create/open the drawables database", ex); //NON-NLS
        }
    }
    
    /**
     * Checks if the specified table exists in Drawable DB
     *
     * @param tableName table to check
     *
     * @return true if the table exists in the database
     *
     * @throws SQLException
     */
    private boolean doesTableExist(String tableName) throws SQLException {
        ResultSet tableQueryResults = null;
        boolean tableExists = false;
        try (Statement stmt = con.createStatement()) {
            tableQueryResults = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'");  //NON-NLS
            while (tableQueryResults.next()) {
                if (tableQueryResults.getString("name").equalsIgnoreCase(tableName)) {
                    tableExists = true;
                    break;
                }
            }
        } finally {
            if (tableQueryResults != null) {
                tableQueryResults.close();
            }
        }
        return tableExists;
    }

    private static void deleteDatabaseIfOlderVersion(Path dbPath) throws SQLException, IOException {
        if (Files.exists(dbPath)) {
            boolean hasDrawableFilesTable = false;
            boolean hasDataSourceIdColumn = false;
            try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString())) {
                Statement stmt = con.createStatement();
                try (ResultSet tableQueryResults = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) { //NON-NLS
                    while (tableQueryResults.next()) {
                        if ("drawable_files".equals(tableQueryResults.getString("name"))) {
                            hasDrawableFilesTable = true;
                            break;
                        }
                    }
                }
                if (hasDrawableFilesTable) {
                    try (ResultSet results = stmt.executeQuery("PRAGMA table_info('drawable_files')")) {
                        while (results.next()) {
                            if ("data_source_obj_id".equals(results.getString("name"))) {
                                hasDataSourceIdColumn = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!hasDrawableFilesTable || !hasDataSourceIdColumn) {
                Files.delete(dbPath);
            }
        }
    }

    private void setPragmas() throws SQLException {
        dbWriteLock();
        try {
            if (isClosed()) {
                throw new SQLException("The drawables database is closed");
            }

            //this should match Sleuthkit db setupt
            try (Statement statement = con.createStatement()) {
                //reduce i/o operations, we have no OS crash recovery anyway
                statement.execute("PRAGMA synchronous = OFF;"); //NON-NLS
                //allow to query while in transaction - no need read locks
                statement.execute("PRAGMA read_uncommitted = True;"); //NON-NLS

                //used for data source deletion
                statement.execute("PRAGMA foreign_keys = ON"); //NON-NLS

                //TODO: test this
                statement.execute("PRAGMA journal_mode  = MEMORY"); //NON-NLS

                //we don't use this feature, so turn it off for minimal speed up on queries
                //this is deprecated and not recomended
                statement.execute("PRAGMA count_changes = OFF;"); //NON-NLS
                //this made a big difference to query speed
                statement.execute("PRAGMA temp_store = MEMORY"); //NON-NLS
                //this made a modest improvement in query speeds
                statement.execute("PRAGMA cache_size = 50000"); //NON-NLS
                //we never delete anything so...
                statement.execute("PRAGMA auto_vacuum = 0"); //NON-NLS
            }

            try {
                logger.log(Level.INFO, String.format("sqlite-jdbc version %s loaded in %s mode", //NON-NLS
                        SQLiteJDBCLoader.getVersion(), SQLiteJDBCLoader.isNativeMode()
                        ? "native" : "pure-java")); //NON-NLS
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "exception while checking sqlite-jdbc version and mode", exception); //NON-NLS
            }

        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * create the table and indices if they don't already exist
     *
     * @return the number of rows in the table , count > 0 indicating an
     *         existing table
     */
    private boolean initializeDBSchema() {
        dbWriteLock();
        try {
            boolean drawableDbTablesExist = true;

            if (isClosed()) {
                logger.log(Level.SEVERE, "The drawables database is closed"); //NON-NLS
                return false;
            }

            try {
                setPragmas();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to set pragmas", ex); //NON-NLS
                return false;
            }

            /*
             * Create tables in the drawables database.
             */
            try (Statement stmt = con.createStatement()) {

                // Check if the database is new or an existing database
                drawableDbTablesExist = doesTableExist("drawable_files");
                if (false == doesTableExist(DB_INFO_TABLE_NAME)) {
                    try {
                        VersionNumber ig_creation_schema_version = drawableDbTablesExist
                                ? IG_STARTING_SCHEMA_VERSION
                                : IG_SCHEMA_VERSION;

                        stmt.execute("CREATE TABLE IF NOT EXISTS " + DB_INFO_TABLE_NAME + " (name TEXT PRIMARY KEY, value TEXT NOT NULL)");

                        // backfill creation schema ver
                        stmt.execute(String.format("INSERT INTO %s (name, value) VALUES ('%s', '%s')", DB_INFO_TABLE_NAME, IG_CREATION_SCHEMA_MAJOR_VERSION_KEY, ig_creation_schema_version.getMajor()));
                        stmt.execute(String.format("INSERT INTO %s (name, value) VALUES ('%s', '%s')", DB_INFO_TABLE_NAME, IG_CREATION_SCHEMA_MINOR_VERSION_KEY, ig_creation_schema_version.getMinor()));

                        // set current schema ver: at DB initialization - current version is same as starting version
                        stmt.execute(String.format("INSERT INTO %s (name, value) VALUES ('%s', '%s')", DB_INFO_TABLE_NAME, IG_SCHEMA_MAJOR_VERSION_KEY, ig_creation_schema_version.getMajor()));
                        stmt.execute(String.format("INSERT INTO %s (name, value) VALUES ('%s', '%s')", DB_INFO_TABLE_NAME, IG_SCHEMA_MINOR_VERSION_KEY, ig_creation_schema_version.getMinor()));

                    } catch (SQLException ex) {
                        logger.log(Level.SEVERE, "Failed to create ig_db_info table", ex); //NON-NLS
                        return false;
                    }
                }

                try {
                    String sql = "CREATE TABLE IF NOT EXISTS datasources " //NON-NLS
                            + "( id INTEGER PRIMARY KEY, " //NON-NLS
                            + " ds_obj_id BIGINT UNIQUE NOT NULL, "
                            + " drawable_db_build_status VARCHAR(128) )"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to create datasources table", ex); //NON-NLS
                    return false;
                }

                try {
                    String sql = "CREATE TABLE  if not exists drawable_files " //NON-NLS
                            + "( obj_id BIGINT PRIMARY KEY, " //NON-NLS
                            + " data_source_obj_id BIGINT NOT NULL, "
                            + " path TEXT, " //NON-NLS
                            + " name TEXT, " //NON-NLS
                            + " created_time integer, " //NON-NLS
                            + " modified_time integer, " //NON-NLS
                            + " make TEXT DEFAULT NULL, " //NON-NLS
                            + " model TEXT DEFAULT NULL, " //NON-NLS
                            + " analyzed integer DEFAULT 0, " //NON-NLS
                            + " FOREIGN KEY (data_source_obj_id) REFERENCES datasources(ds_obj_id) ON DELETE CASCADE)"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to create drawable_files table", ex); //NON-NLS
                    return false;
                }

                try {
                    String sql = "CREATE TABLE if not exists hash_sets " //NON-NLS
                            + "( hash_set_id INTEGER primary key," //NON-NLS
                            + " hash_set_name TEXT UNIQUE NOT NULL)"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to create hash_sets table", ex); //NON-NLS
                    return false;
                }

                try {
                    String sql = "CREATE TABLE if not exists hash_set_hits " //NON-NLS
                            + "(hash_set_id INTEGER REFERENCES hash_sets(hash_set_id) not null, " //NON-NLS
                            + " obj_id BIGINT NOT NULL, " //NON-NLS
                            + " PRIMARY KEY (hash_set_id, obj_id), " //NON-NLS
                            + " FOREIGN KEY (obj_id) REFERENCES drawable_files(obj_id) ON DELETE CASCADE)"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to create hash_set_hits table", ex); //NON-NLS
                    return false;
                }

                try {
                    String sql = "CREATE INDEX if not exists path_idx ON drawable_files(path)"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to create path_idx", ex); //NON-NLS
                }

                try {
                    String sql = "CREATE INDEX if not exists name_idx ON drawable_files(name)"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to create name_idx", ex); //NON-NLS
                }

                try {
                    String sql = "CREATE INDEX if not exists make_idx ON drawable_files(make)"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to create  make_idx", ex); //NON-NLS
                }

                try {
                    String sql = "CREATE  INDEX if not exists model_idx ON drawable_files(model)"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to create model_idx", ex); //NON-NLS
                }

                try {
                    String sql = "CREATE  INDEX if not exists analyzed_idx ON drawable_files(analyzed)"; //NON-NLS
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Failed to create analyzed_idx", ex); //NON-NLS
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to create statement", ex); //NON-NLS
                return false;
            }

            /*
             * Create tables in the case database.
             */
            String autogenKeyType = (DbType.POSTGRESQL == caseDb.getDatabaseType()) ? "BIGSERIAL" : "INTEGER";

            try {
                boolean caseDbTablesExist = caseDb.getCaseDbAccessManager().tableExists(CASE_DB_GROUPS_TABLENAME);
                VersionNumber ig_creation_schema_version = caseDbTablesExist
                        ? IG_STARTING_SCHEMA_VERSION
                        : IG_SCHEMA_VERSION;

                String tableSchema = "( id " + autogenKeyType + " PRIMARY KEY, "
                        + " name TEXT UNIQUE NOT NULL,"
                        + " value TEXT NOT NULL )";
                caseDb.getCaseDbAccessManager().createTable(DB_INFO_TABLE_NAME, tableSchema);

                // backfill creation version
                String creationMajorVerSQL = String.format(" (name, value) VALUES ('%s', '%s')", IG_CREATION_SCHEMA_MAJOR_VERSION_KEY, ig_creation_schema_version.getMajor());
                String creationMinorVerSQL = String.format(" (name, value) VALUES ('%s', '%s')", IG_CREATION_SCHEMA_MINOR_VERSION_KEY, ig_creation_schema_version.getMinor());

                // set current version - at the onset, current version is same as creation version
                String currentMajorVerSQL = String.format(" (name, value) VALUES ('%s', '%s')", IG_SCHEMA_MAJOR_VERSION_KEY, ig_creation_schema_version.getMajor());
                String currentMinorVerSQL = String.format(" (name, value) VALUES ('%s', '%s')", IG_SCHEMA_MINOR_VERSION_KEY, ig_creation_schema_version.getMinor());

                if (DbType.POSTGRESQL == caseDb.getDatabaseType()) {
                    creationMajorVerSQL += " ON CONFLICT DO NOTHING ";
                    creationMinorVerSQL += " ON CONFLICT DO NOTHING ";

                    currentMajorVerSQL += " ON CONFLICT DO NOTHING ";
                    currentMinorVerSQL += " ON CONFLICT DO NOTHING ";
                }

                caseDb.getCaseDbAccessManager().insert(DB_INFO_TABLE_NAME, creationMajorVerSQL);
                caseDb.getCaseDbAccessManager().insert(DB_INFO_TABLE_NAME, creationMinorVerSQL);

                caseDb.getCaseDbAccessManager().insert(DB_INFO_TABLE_NAME, currentMajorVerSQL);
                caseDb.getCaseDbAccessManager().insert(DB_INFO_TABLE_NAME, currentMinorVerSQL);

            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to create ig_db_info table in Case database", ex); //NON-NLS
                return false;
            }

            try {
                String tableSchema
                        = "( group_id " + autogenKeyType + " PRIMARY KEY, " //NON-NLS
                        + " data_source_obj_id BIGINT DEFAULT 0, "
                        + " value TEXT not null, " //NON-NLS
                        + " attribute TEXT not null, " //NON-NLS
                        + " is_analyzed integer DEFAULT 0, "
                        + " UNIQUE(data_source_obj_id, value, attribute) )"; //NON-NLS

                caseDb.getCaseDbAccessManager().createTable(CASE_DB_GROUPS_TABLENAME, tableSchema);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to create %s table in case database", CASE_DB_GROUPS_TABLENAME), ex); //NON-NLS
                return false;
            }
            try {

                String tableSchema
                        = "( id " + autogenKeyType + " PRIMARY KEY, " //NON-NLS
                        + " group_id integer not null, " //NON-NLS
                        + " examiner_id integer not null, " //NON-NLS
                        + " seen integer DEFAULT 0, " //NON-NLS
                        + " UNIQUE(group_id, examiner_id),"
                        + " FOREIGN KEY(group_id) REFERENCES " + CASE_DB_GROUPS_TABLENAME + "(group_id) ON DELETE CASCADE,"
                        + " FOREIGN KEY(examiner_id) REFERENCES  tsk_examiners(examiner_id)"
                        + " )"; //NON-NLS

                caseDb.getCaseDbAccessManager().createTable(CASE_DB_GROUPS_SEEN_TABLENAME, tableSchema);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to create %s table in case database", CASE_DB_GROUPS_SEEN_TABLENAME), ex); //NON-NLS
                return false;
            }

            return true;

        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * Gets the Schema version from DrawableDB
     *
     * @return image gallery schema version in DrawableDB
     *
     * @throws SQLException
     * @throws TskCoreException
     */
    private VersionNumber getDrawableDbIgSchemaVersion() throws SQLException, TskCoreException {

        Statement statement = con.createStatement();
        ResultSet resultSet = null;

        try {
            int majorVersion = -1;
            String majorVersionStr = null;
            resultSet = statement.executeQuery(String.format("SELECT value FROM %s  WHERE name='%s'", DB_INFO_TABLE_NAME, IG_SCHEMA_MAJOR_VERSION_KEY));
            if (resultSet.next()) {
                majorVersionStr = resultSet.getString("value");
                try {
                    majorVersion = Integer.parseInt(majorVersionStr);
                } catch (NumberFormatException ex) {
                    throw new TskCoreException("Bad value for schema major version = " + majorVersionStr, ex);
                }
            } else {
                throw new TskCoreException("Failed to read schema major version from ig_db_info table");
            }

            int minorVersion = -1;
            String minorVersionStr = null;
            resultSet = statement.executeQuery(String.format("SELECT value FROM %s  WHERE name='%s'", DB_INFO_TABLE_NAME, IG_SCHEMA_MINOR_VERSION_KEY));
            if (resultSet.next()) {
                minorVersionStr = resultSet.getString("value");
                try {
                    minorVersion = Integer.parseInt(minorVersionStr);
                } catch (NumberFormatException ex) {
                    throw new TskCoreException("Bad value for schema minor version = " + minorVersionStr, ex);
                }
            } else {
                throw new TskCoreException("Failed to read schema minor version from ig_db_info table");
            }

            return new VersionNumber(majorVersion, minorVersion, 0);
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
        }
    }

    /**
     * Gets the ImageGallery schema version from CaseDB
     *
     * @return image gallery schema version in CaseDB
     *
     * @throws SQLException
     * @throws TskCoreException
     */
    private VersionNumber getCaseDbIgSchemaVersion() throws TskCoreException {

        // Callback to process result of get version query
        class GetSchemaVersionQueryResultProcessor implements CaseDbAccessQueryCallback {

            private int version = -1;

            int getVersion() {
                return version;
            }

            @Override
            public void process(ResultSet resultSet) {
                try {
                    if (resultSet.next()) {
                        String versionStr = resultSet.getString("value");
                        try {
                            version = Integer.parseInt(versionStr);
                        } catch (NumberFormatException ex) {
                            logger.log(Level.SEVERE, "Bad value for version = " + versionStr, ex);
                        }
                    } else {
                        logger.log(Level.SEVERE, "Failed to get version");
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get version", ex); //NON-NLS
                }
            }
        }

        GetSchemaVersionQueryResultProcessor majorVersionResultProcessor = new GetSchemaVersionQueryResultProcessor();
        GetSchemaVersionQueryResultProcessor minorVersionResultProcessor = new GetSchemaVersionQueryResultProcessor();

        String versionQueryTemplate = "value FROM %s WHERE name = \'%s\' ";
        caseDb.getCaseDbAccessManager().select(String.format(versionQueryTemplate, DB_INFO_TABLE_NAME, IG_SCHEMA_MAJOR_VERSION_KEY), majorVersionResultProcessor);
        caseDb.getCaseDbAccessManager().select(String.format(versionQueryTemplate, DB_INFO_TABLE_NAME, IG_SCHEMA_MINOR_VERSION_KEY), minorVersionResultProcessor);

        return new VersionNumber(majorVersionResultProcessor.getVersion(), minorVersionResultProcessor.getVersion(), 0);
    }

    /**
     * Updates the IG schema version in the Drawable DB
     *
     * @param version     new version number
     * @param transaction transaction under which the update happens
     *
     * @throws SQLException
     */
    private void updateDrawableDbIgSchemaVersion(VersionNumber version, DrawableTransaction transaction) throws SQLException, TskCoreException {

        if (transaction == null) {
            throw new TskCoreException("Schema version update must be done in a transaction");
        }

        dbWriteLock();
        try {
            Statement statement = con.createStatement();

            // update schema version
            statement.execute(String.format("UPDATE %s  SET value = '%s' WHERE name = '%s'", DB_INFO_TABLE_NAME, version.getMajor(), IG_SCHEMA_MAJOR_VERSION_KEY));
            statement.execute(String.format("UPDATE %s  SET value = '%s' WHERE name = '%s'", DB_INFO_TABLE_NAME, version.getMinor(), IG_SCHEMA_MINOR_VERSION_KEY));

            statement.close();
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * Updates the IG schema version in CaseDB
     *
     * @param version           new version number
     * @param caseDbTransaction transaction to use to update the CaseDB
     *
     * @throws SQLException
     */
    private void updateCaseDbIgSchemaVersion(VersionNumber version, CaseDbTransaction caseDbTransaction) throws TskCoreException {

        String updateSQLTemplate = " SET value = %s  WHERE name = '%s' ";
        caseDb.getCaseDbAccessManager().update(DB_INFO_TABLE_NAME, String.format(updateSQLTemplate, version.getMajor(), IG_SCHEMA_MAJOR_VERSION_KEY), caseDbTransaction);
        caseDb.getCaseDbAccessManager().update(DB_INFO_TABLE_NAME, String.format(updateSQLTemplate, version.getMinor(), IG_SCHEMA_MINOR_VERSION_KEY), caseDbTransaction);
    }

    /**
     * Upgrades the DB schema.
     *
     * @return true if the upgrade is successful
     *
     * @throws SQLException
     *
     */
    private boolean upgradeDBSchema() throws TskCoreException, SQLException {

        // Read current version from the DBs
        VersionNumber drawableDbIgSchemaVersion = getDrawableDbIgSchemaVersion();
        VersionNumber caseDbIgSchemaVersion = getCaseDbIgSchemaVersion();

        // Upgrade Schema in both DrawableDB and CaseDB
        CaseDbTransaction caseDbTransaction = caseDb.beginTransaction();
        DrawableTransaction transaction = beginTransaction();

        try {
            caseDbIgSchemaVersion = upgradeCaseDbIgSchema1dot0TO1dot1(caseDbIgSchemaVersion, caseDbTransaction);
            drawableDbIgSchemaVersion = upgradeDrawableDbIgSchema1dot0TO1dot1(drawableDbIgSchemaVersion, transaction);

            // update the versions in the tables
            updateCaseDbIgSchemaVersion(caseDbIgSchemaVersion, caseDbTransaction);
            updateDrawableDbIgSchemaVersion(drawableDbIgSchemaVersion, transaction);

            caseDbTransaction.commit();
            caseDbTransaction = null;
            commitTransaction(transaction, false);
            transaction = null;
        } catch (TskCoreException | SQLException ex) {
            if (null != caseDbTransaction) {
                try {
                    caseDbTransaction.rollback();
                } catch (TskCoreException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back case db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            if (null != transaction) {
                try {
                    rollbackTransaction(transaction);
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back drawables db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            throw ex;
        }
        return true;
    }

    /**
     * Upgrades IG tables in CaseDB from 1.0 to 1.1 Does nothing if the incoming
     * version is not 1.0
     *
     * @param currVersion       version to upgrade from
     * @param caseDbTransaction transaction to use for all updates
     *
     * @return new version number
     *
     * @throws TskCoreException
     */
    private VersionNumber upgradeCaseDbIgSchema1dot0TO1dot1(VersionNumber currVersion, CaseDbTransaction caseDbTransaction) throws TskCoreException {

        // Upgrade if current version is 1.0
        // or 1.1 - a bug in versioning alllowed some databases to be versioned as 1.1 without the actual corresponding upgrade.  This allows such databases to be fixed, if needed.
        if (!(currVersion.getMajor() == 1
                && (currVersion.getMinor() == 0 || currVersion.getMinor() == 1))) {
            return currVersion;
        }

        // Add a 'is_analyzed' column to groups table in CaseDB
        String alterSQL = " ADD COLUMN is_analyzed integer DEFAULT 1 "; //NON-NLS
        if (false == caseDb.getCaseDbAccessManager().columnExists(CASE_DB_GROUPS_TABLENAME, "is_analyzed", caseDbTransaction)) {
            caseDb.getCaseDbAccessManager().alterTable(CASE_DB_GROUPS_TABLENAME, alterSQL, caseDbTransaction);
        }
        return new VersionNumber(1, 1, 0);
    }

    /**
     * Upgrades IG tables in DrawableDB from 1.0 to 1.1 Does nothing if the
     * incoming version is not 1.0
     *
     * @param currVersion version to upgrade from
     * @param transaction transaction to use for all updates
     *
     * @return new version number
     *
     * @throws TskCoreException
     */
    private VersionNumber upgradeDrawableDbIgSchema1dot0TO1dot1(VersionNumber currVersion, DrawableTransaction transaction) throws TskCoreException {

        if (currVersion.getMajor() != 1
                || currVersion.getMinor() != 0) {
            return currVersion;
        }

        // There are no changes in DrawableDB schema in 1.0 -> 1.1
        return new VersionNumber(1, 1, 0);
    }

    @Override
    protected void finalize() throws Throwable {
        /*
         * This finalizer is a safety net for freeing this resource. See
         * "Effective Java" by Joshua Block, Item #7.
         */
        dbWriteLock();
        try {
            if (!isClosed()) {
                logger.log(Level.SEVERE, "Closing drawable.db in finalizer, this should never be necessary"); //NON-NLS
                try {
                    close();
                } finally {
                    super.finalize();
                }
            }
        } finally {
            dbWriteUnlock();
        }
    }

    public void close() {
        dbWriteLock();
        try {
            if (!isClosed()) {
                logger.log(Level.INFO, "Closing the drawable.db"); //NON-NLS
                for (PreparedStatement pStmt : preparedStatements) {
                    try {
                        pStmt.close();
                    } catch (SQLException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to close prepared statement %s for drawable.db", pStmt.toString()), ex); //NON-NLS
                    }
                }
                try {
                    con.close();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to close connection to drawable.db", ex); //NON-NLS
                }
            }
        } finally {
            con = null;
            dbWriteUnlock();
        }
    }

    private boolean isClosed() {
        dbWriteLock();
        try {
            return ((con == null) || (con.isClosed()));
        } catch (SQLException unused) {
            return false;
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * get the names of the hashsets that the given fileID belongs to
     *
     * @param fileID the fileID to get all the Hashset names for
     *
     * @return a set of hash set names, each of which the given file belongs to
     *
     * @throws TskCoreException
     *
     *
     * //TODO: this is mostly a cut and paste from *
     * AbstractContent.getHashSetNames, is there away to dedupe?
     */
    Set<String> getHashSetsForFile(long fileID) throws TskCoreException {
        Set<String> hashNames = new HashSet<>();
        ArrayList<BlackboardArtifact> artifacts = caseDb.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT, fileID);

        for (BlackboardArtifact a : artifacts) {
            BlackboardAttribute attribute = a.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
            if (attribute != null) {
                hashNames.add(attribute.getValueString());
            }
        }
        return Collections.unmodifiableSet(hashNames);
    }

    /**
     * get all the hash set names used in the db
     *
     * @return a set of the names of all the hash sets that have hash set hits
     */
    public Set<String> getHashSetNames() {
        Set<String> names = new HashSet<>();
        // "SELECT DISTINCT hash_set_name FROM hash_sets"
        dbWriteLock();
        try (ResultSet rs = selectHashSetNamesStmt.executeQuery();) {
            while (rs.next()) {
                names.add(rs.getString("hash_set_name"));
            }
        } catch (SQLException sQLException) {
            logger.log(Level.WARNING, "failed to get hash set names", sQLException); //NON-NLS
        } finally {
            dbWriteUnlock();
        }
        return names;
    }

    static private String getGroupIdQuery(GroupKey<?> groupKey) {
        // query to find the group id from attribute/value
        return String.format(" SELECT group_id FROM " + CASE_DB_GROUPS_TABLENAME
                + " WHERE attribute = \'%s\' AND value = \'%s\' AND data_source_obj_id = %d",
                SleuthkitCase.escapeSingleQuotes(groupKey.getAttribute().attrName.toString()),
                SleuthkitCase.escapeSingleQuotes(groupKey.getValueDisplayName()),
                (groupKey.getAttribute() == DrawableAttribute.PATH) ? groupKey.getDataSourceObjId() : 0);
    }

    /**
     * Returns true if the specified group has been any examiner
     *
     * @param groupKey
     *
     * @return
     */
    public boolean isGroupSeen(GroupKey<?> groupKey) {
        return isGroupSeenByExaminer(groupKey, -1);
    }

    /**
     * Returns true if the specified group has been seen by the specified
     * examiner
     *
     * @param groupKey   - key to identify the group
     * @param examinerId
     *
     * @return true if the examine has this group, false otherwise
     */
    public boolean isGroupSeenByExaminer(GroupKey<?> groupKey, long examinerId) {

        // Callback to process result of seen query
        class GroupSeenQueryResultProcessor extends CompletableFuture<Boolean> implements CaseDbAccessQueryCallback {

            @Override
            public void process(ResultSet resultSet) {
                try {
                    if (resultSet != null) {
                        while (resultSet.next()) {
                            complete(resultSet.getInt("count") > 0); //NON-NLS;
                            return;
                        }
                    }
                } catch (SQLException ex) {
                    completeExceptionally(ex);
                }
            }
        }
        // Callback to process result of seen query
        GroupSeenQueryResultProcessor queryResultProcessor = new GroupSeenQueryResultProcessor();

        try {
            String groupSeenQueryStmt = "COUNT(*) as count FROM " + CASE_DB_GROUPS_SEEN_TABLENAME
                    + " WHERE seen = 1 "
                    + " AND group_id in ( " + getGroupIdQuery(groupKey) + ")"
                    + (examinerId > 0 ? " AND examiner_id = " + examinerId : "");// query to find the group id from attribute/value 

            caseDb.getCaseDbAccessManager().select(groupSeenQueryStmt, queryResultProcessor);
            return queryResultProcessor.get();
        } catch (ExecutionException | InterruptedException | TskCoreException ex) {
            String msg = String.format("Failed to get is group seen for group key %s", groupKey.getValueDisplayName()); //NON-NLS
            logger.log(Level.SEVERE, msg, ex);
        }

        return false;
    }

    /**
     * Record in the DB that the group with the given key is seen by given
     * examiner id.
     *
     * @param groupKey   key identifying the group.
     * @param examinerID examiner id.
     *
     * @throws TskCoreException
     */
    public void markGroupSeen(GroupKey<?> groupKey, long examinerID) throws TskCoreException {

        /*
         * Check the groupSeenCache to see if the seen status for this group was
         * set recently. If recently set to seen, there's no need to update it
         */
        Boolean cachedValue = groupSeenCache.getIfPresent(groupKey);
        if (cachedValue != null && cachedValue == true) {
            return;
        }

        // query to find the group id from attribute/value
        String innerQuery = String.format("( SELECT group_id FROM " + CASE_DB_GROUPS_TABLENAME//NON-NLS
                + " WHERE attribute = \'%s\' AND value = \'%s\' and data_source_obj_id = %d )", //NON-NLS
                SleuthkitCase.escapeSingleQuotes(groupKey.getAttribute().attrName.toString()),
                SleuthkitCase.escapeSingleQuotes(groupKey.getValueDisplayName()),
                groupKey.getAttribute() == DrawableAttribute.PATH ? groupKey.getDataSourceObjId() : 0);

        String insertSQL = String.format(" (group_id, examiner_id, seen) VALUES (%s, %d, %d)", innerQuery, examinerID, 1); //NON-NLS
        if (DbType.POSTGRESQL == caseDb.getDatabaseType()) {
            insertSQL += String.format(" ON CONFLICT (group_id, examiner_id) DO UPDATE SET seen = %d", 1); //NON-NLS
        }

        caseDb.getCaseDbAccessManager().insertOrUpdate(CASE_DB_GROUPS_SEEN_TABLENAME, insertSQL);

        groupSeenCache.put(groupKey, true);
    }

    /**
     * Record in the DB that given group is unseen. The group is marked unseen
     * for ALL examiners that have seen the group.
     *
     * @param groupKey key identifying the group.
     *
     * @throws TskCoreException
     */
    public void markGroupUnseen(GroupKey<?> groupKey) throws TskCoreException {

        /*
         * Check the groupSeenCache to see if the seen status for this group was
         * set recently. If recently set to unseen, there's no need to update it
         */
        Boolean cachedValue = groupSeenCache.getIfPresent(groupKey);
        if (cachedValue != null && cachedValue == false) {
            return;
        }

        String updateSQL = String.format(" SET seen = 0 WHERE group_id in ( " + getGroupIdQuery(groupKey) + ")"); //NON-NLS
        caseDb.getCaseDbAccessManager().update(CASE_DB_GROUPS_SEEN_TABLENAME, updateSQL);

        groupSeenCache.put(groupKey, false);
    }

    /**
     * Sets the isAnalysed flag in the groups table for the given group to true.
     *
     * @param groupKey group key.
     *
     * @throws TskCoreException
     */
    public void markGroupAnalyzed(GroupKey<?> groupKey) throws TskCoreException {

        String updateSQL = String.format(" SET is_analyzed = %d "
                + " WHERE attribute = \'%s\' AND value = \'%s\' and data_source_obj_id = %d ",
                1,
                SleuthkitCase.escapeSingleQuotes(groupKey.getAttribute().attrName.toString()),
                SleuthkitCase.escapeSingleQuotes(groupKey.getValueDisplayName()),
                groupKey.getAttribute() == DrawableAttribute.PATH ? groupKey.getDataSourceObjId() : 0);

        caseDb.getCaseDbAccessManager().update(CASE_DB_GROUPS_TABLENAME, updateSQL);
    }

    /**
     * Removes a file from the drawables databse.
     *
     * @param id The object id of the file.
     *
     * @return True or false.
     *
     * @throws TskCoreException
     * @throws SQLException
     */
    public void removeFile(long id) throws TskCoreException, SQLException {
        DrawableTransaction trans = null;
        try {
            trans = beginTransaction();
            removeFile(id, trans);
            commitTransaction(trans, true);
        } catch (TskCoreException | SQLException ex) {
            if (null != trans) {
                try {
                    rollbackTransaction(trans);
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back drawables db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            throw ex;
        }
    }

    /**
     * Updates the image file.
     *
     * @param f file to update.
     *
     * @throws TskCoreException
     * @throws SQLException
     */
    public void updateFile(DrawableFile f) throws TskCoreException, SQLException {
        DrawableTransaction trans = null;
        CaseDbTransaction caseDbTransaction = null;
        try {
            trans = beginTransaction();
            caseDbTransaction = caseDb.beginTransaction();
            updateFile(f, trans, caseDbTransaction);
            caseDbTransaction.commit();
            commitTransaction(trans, true);
        } catch (TskCoreException | SQLException ex) {
            if (null != caseDbTransaction) {
                try {
                    caseDbTransaction.rollback();
                } catch (TskCoreException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back case db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            if (null != trans) {
                try {
                    rollbackTransaction(trans);
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back drawables db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            throw ex;
        }
    }

    /**
     * Update an existing entry (or make a new one) into the DB that includes
     * group information. Called when a file has been analyzed or during a bulk
     * rebuild
     *
     * @param f                 file to update
     * @param tr
     * @param caseDbTransaction
     */
    public void updateFile(DrawableFile f, DrawableTransaction tr, CaseDbTransaction caseDbTransaction) {
        insertOrUpdateFile(f, tr, caseDbTransaction, true);
    }

    /**
     * Populate caches based on current state of Case DB
     */
    public void buildFileMetaDataCache() {

        synchronized (cacheLock) {
            cacheBuildCount++;
            if (areCachesLoaded == true) {
                return;
            }

            try {
                // get tags
                try (SleuthkitCase.CaseDbQuery dbQuery = caseDb.executeQuery("SELECT obj_id FROM content_tags")) {
                    ResultSet rs = dbQuery.getResultSet();
                    while (rs.next()) {
                        long id = rs.getLong("obj_id");
                        hasTagsCache.add(id);
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error getting tags from DB", ex); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error executing query to get tags", ex); //NON-NLS
            }

            try {
                // hash sets
                try (SleuthkitCase.CaseDbQuery dbQuery = caseDb.executeQuery("SELECT obj_id FROM blackboard_artifacts WHERE artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID())) {
                    ResultSet rs = dbQuery.getResultSet();
                    while (rs.next()) {
                        long id = rs.getLong("obj_id");
                        hasHashHitsCache.add(id);
                    }

                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error getting hashsets from DB", ex); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error executing query to get hashsets", ex); //NON-NLS
            }

            try {
                // EXIF
                try (SleuthkitCase.CaseDbQuery dbQuery = caseDb.executeQuery("SELECT obj_id FROM blackboard_artifacts WHERE artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID())) {
                    ResultSet rs = dbQuery.getResultSet();
                    while (rs.next()) {
                        long id = rs.getLong("obj_id");
                        hasExifDataCache.add(id);
                    }

                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error getting EXIF from DB", ex); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error executing query to get EXIF", ex); //NON-NLS
            }

            areCachesLoaded = true;
        }
    }

    /**
     * Add a file to cache of files that have EXIF data
     *
     * @param objectID ObjId of file with EXIF
     */
    public void addExifCache(long objectID) {
        synchronized (cacheLock) {
            // bail out if we are not maintaining caches
            if (cacheBuildCount == 0) {
                return;
            }
            hasExifDataCache.add(objectID);
        }
    }

    /**
     * Add a file to cache of files that have hash set hits
     *
     * @param objectID ObjId of file with hash set
     */
    public void addHashSetCache(long objectID) {
        synchronized (cacheLock) {
            // bail out if we are not maintaining caches
            if (cacheBuildCount == 0) {
                return;
            }
            hasHashHitsCache.add(objectID);
        }
    }

    /**
     * Add a file to cache of files that have tags
     *
     * @param objectID ObjId of file with tags
     */
    public void addTagCache(long objectID) {
        synchronized (cacheLock) {
            // bail out if we are not maintaining caches
            if (cacheBuildCount == 0) {
                return;
            }
            hasTagsCache.add(objectID);
        }
    }

    /**
     * Free the cached case DB data
     */
    public void freeFileMetaDataCache() {
        synchronized (cacheLock) {
            // dont' free these if there is another task still using them
            if (--cacheBuildCount > 0) {
                return;
            }

            areCachesLoaded = false;
            hasTagsCache.clear();
            hasHashHitsCache.clear();
            hasExifDataCache.clear();
        }
    }

    /**
     * Update (or insert) a file in(to) the drawable db. Weather this is an
     * insert or an update depends on the given prepared statement. This method
     * also inserts hash set hits and groups into their respective tables for
     * the given file.
     *
     * //TODO: this is a kinda weird design, is their a better way? //TODO:
     * implement batch version -jm
     *
     * @param f                 The file to insert.
     * @param tr                a transaction to use, must not be null
     * @param caseDbTransaction
     * @param addGroups         True if groups for file should be inserted into
     *                          db too
     */
    private void insertOrUpdateFile(DrawableFile f, @Nonnull DrawableTransaction tr, @Nonnull CaseDbTransaction caseDbTransaction, boolean addGroups) {

        PreparedStatement stmt;

        if (tr.isCompleted()) {
            throw new IllegalArgumentException("can't update database with closed transaction");
        }

        // assume that we are doing an update if we are adding groups - i.e. not pre-populating
        if (addGroups) {
            stmt = updateFileStmt;
        } else {
            stmt = insertFileStmt;
        }

        // get data from caches. Default to true and force the DB lookup if we don't have caches
        boolean hasExif = true;
        boolean hasHashSet = true;
        boolean hasTag = true;
        synchronized (cacheLock) {
            if (areCachesLoaded) {
                hasExif = hasExifDataCache.contains(f.getId());
                hasHashSet = hasHashHitsCache.contains(f.getId());
                hasTag = hasTagsCache.contains(f.getId());
            }
        }

        // if we are going to just add basic data, then mark flags that we do not have metadata to prevent lookups
        if (addGroups == false) {
            hasExif = false;
            hasHashSet = false;
            hasTag = false;
        }

        dbWriteLock();
        try {
            if (addGroups) {
                // "INSERT INTO drawable_files (obj_id, data_source_obj_id, path, name, created_time, modified_time, make, model, analyzed) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (obj_id) DO UPDATE SET data_source_obj_id = ?, path = ?, name = ?, created_time = ?, modified_time = ?, make = ?, model = ?, analyzed = ?"
                stmt.setLong(1, f.getId());
                stmt.setLong(2, f.getAbstractFile().getDataSourceObjectId());
                stmt.setLong(10, f.getAbstractFile().getDataSourceObjectId());
                stmt.setString(3, f.getDrawablePath());
                stmt.setString(11, f.getDrawablePath());
                stmt.setString(4, f.getName());
                stmt.setString(12, f.getName());
                stmt.setLong(5, f.getCrtime());
                stmt.setLong(13, f.getCrtime());
                stmt.setLong(6, f.getMtime());
                stmt.setLong(14, f.getMtime());
                if (hasExif) {
                    stmt.setString(7, f.getMake());
                    stmt.setString(15, f.getMake());
                    stmt.setString(8, f.getModel());
                    stmt.setString(16, f.getModel());
                } else {
                    stmt.setString(7, "");
                    stmt.setString(15, "");
                    stmt.setString(8, "");
                    stmt.setString(16, "");
                }
                stmt.setBoolean(9, f.isAnalyzed());
                stmt.setBoolean(17, f.isAnalyzed());
            } else {
                // "INSERT OR IGNORE/ INTO drawable_files (obj_id, data_source_obj_id, path, name, created_time, modified_time, make, model, analyzed)"
                stmt.setLong(1, f.getId());
                stmt.setLong(2, f.getAbstractFile().getDataSourceObjectId());
                stmt.setString(3, f.getDrawablePath());
                stmt.setString(4, f.getName());
                stmt.setLong(5, f.getCrtime());
                stmt.setLong(6, f.getMtime());
                if (hasExif) {
                    stmt.setString(7, f.getMake());
                    stmt.setString(8, f.getModel());
                } else {
                    stmt.setString(7, "");
                    stmt.setString(8, "");
                }
                stmt.setBoolean(9, f.isAnalyzed());
            }

            stmt.executeUpdate();

            // Update the list of file IDs in memory
            addImageFileToList(f.getId());

            // update the groups if we are not doing pre-populating
            if (addGroups) {

                // Update the hash set tables
                if (hasHashSet) {
                    try {
                        for (String name : f.getHashSetNames()) {

                            // "insert or ignore into hash_sets (hash_set_name)  values (?)"
                            insertHashSetStmt.setString(1, name);
                            insertHashSetStmt.executeUpdate();

                            //TODO: use nested select to get hash_set_id rather than seperate statement/query
                            //"select hash_set_id from hash_sets where hash_set_name = ?"
                            selectHashSetStmt.setString(1, name);
                            try (ResultSet rs = selectHashSetStmt.executeQuery()) {
                                while (rs.next()) {
                                    int hashsetID = rs.getInt("hash_set_id"); //NON-NLS
                                    //"insert or ignore into hash_set_hits (hash_set_id, obj_id) values (?,?)";
                                    insertHashHitStmt.setInt(1, hashsetID);
                                    insertHashHitStmt.setLong(2, f.getId());
                                    insertHashHitStmt.executeUpdate();
                                    break;
                                }
                            }
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "failed to insert/update hash hits for file" + f.getContentPathSafe(), ex); //NON-NLS
                    }
                }

                //and update all groups this file is in
                for (DrawableAttribute<?> attr : DrawableAttribute.getGroupableAttrs()) {
                    // skip attributes that we do not have data for
                    if ((attr == DrawableAttribute.TAGS) && (hasTag == false)) {
                        continue;
                    } else if ((attr == DrawableAttribute.MAKE || attr == DrawableAttribute.MODEL) && (hasExif == false)) {
                        continue;
                    }
                    Collection<? extends Comparable<?>> vals = attr.getValue(f);
                    for (Comparable<?> val : vals) {
                        if ((null != val) && (val.toString().isEmpty() == false)) {
                            if (attr == DrawableAttribute.PATH) {
                                insertGroup(f.getAbstractFile().getDataSource().getId(), val.toString(), attr, caseDbTransaction);
                            } else {
                                insertGroup(val.toString(), attr, caseDbTransaction);
                            }
                        }
                    }
                }
            }

            // @@@ Consider storing more than ID so that we do not need to requery each file during commit
            tr.addUpdatedFile(f.getId());

        } catch (SQLException | NullPointerException | TskCoreException ex) {
            /*
             * This is one of the places where we get an error if the case is
             * closed during processing, which doesn't need to be reported here.
             */
            if (Case.isCaseOpen()) {
                logger.log(Level.SEVERE, "failed to insert/update file" + f.getContentPathSafe(), ex); //NON-NLS
            }

        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * Gets all data source object ids from datasources table, and their
     * DrawableDbBuildStatusEnum
     *
     * @return map of known data source object ids, and their db status
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public Map<Long, DrawableDbBuildStatusEnum> getDataSourceDbBuildStatus() throws TskCoreException {
        Statement statement = null;
        Map<Long, DrawableDbBuildStatusEnum> map = new HashMap<>();
        dbWriteLock();
        try {
            if (isClosed()) {
                throw new TskCoreException("The drawables database is closed");
            }
            statement = con.createStatement();
            ResultSet rs = statement.executeQuery("SELECT ds_obj_id, drawable_db_build_status FROM datasources "); //NON-NLS
            while (rs.next()) {
                map.put(rs.getLong("ds_obj_id"), DrawableDbBuildStatusEnum.valueOf(rs.getString("drawable_db_build_status")));
            }
        } catch (SQLException e) {
            throw new TskCoreException("SQLException while getting data source object ids", e);
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error closing statement ", ex); //NON-NLS
                }
            }
            dbWriteUnlock();
        }
        return map;
    }

    /**
     * Get the build status for the given data source. Will return UNKNOWN if
     * the data source is not yet in the database.
     *
     * @param dataSourceId
     *
     * @return The status of the data source or UKNOWN if it is not found.
     *
     * @throws TskCoreException
     */
    public DrawableDbBuildStatusEnum getDataSourceDbBuildStatus(Long dataSourceId) throws TskCoreException {
        Map<Long, DrawableDbBuildStatusEnum> statusMap = getDataSourceDbBuildStatus();
        if (statusMap.containsKey(dataSourceId) == false) {
            return DrawableDbBuildStatusEnum.UNKNOWN;
        }
        return statusMap.get(dataSourceId);
    }

    /**
     * Inserts the given data source object ID and its status into the
     * datasources table. If a record for the data source already exists, an
     * update of the status is done instead.
     *
     * @param dataSourceObjectID A data source object ID from the case database.
     * @param status             The status of the data source with respect to
     *                           populating the image gallery database.
     */
    public void insertOrUpdateDataSource(long dataSourceObjectID, DrawableDbBuildStatusEnum status) throws SQLException {
        dbWriteLock();
        try {
            // INSERT INTO datasources (ds_obj_id, drawable_db_build_status) values (?,?) ON CONFLICT(ds_obj_id) DO UPDATE SET drawable_db_build_status = ?;
            insertDataSourceStmt.setLong(1, dataSourceObjectID);
            insertDataSourceStmt.setString(2, status.name());
            insertDataSourceStmt.setString(3, status.name());
            insertDataSourceStmt.executeUpdate();
        } finally {
            dbWriteUnlock();
        }
    }

    public DrawableTransaction beginTransaction() throws TskCoreException, SQLException {
        return new DrawableTransaction();
    }

    /**
     *
     * @param tr
     * @param notifyGM If true, notify GroupManager about the changes.
     */
    public void commitTransaction(DrawableTransaction tr, Boolean notifyGM) throws SQLException {
        if (tr.isCompleted()) {
            throw new IllegalArgumentException("Attempt to commit completed transaction");
        }
        tr.commit(notifyGM);
    }

    public void rollbackTransaction(DrawableTransaction tr) throws SQLException {
        if (tr.isCompleted()) {
            throw new IllegalArgumentException("Attempt to roll back completed transaction");
        }
        tr.rollback();
    }

    public Boolean areFilesAnalyzed(Collection<Long> fileIds) throws SQLException {
        dbWriteLock();
        try {
            if (isClosed()) {
                throw new SQLException("The drawables database is closed");
            }
            try (Statement stmt = con.createStatement()) {
                //Can't make this a preprared statement because of the IN ( ... )
                ResultSet analyzedQuery = stmt.executeQuery("SELECT COUNT(analyzed) AS analyzed FROM drawable_files WHERE analyzed = 1 AND obj_id IN (" + StringUtils.join(fileIds, ", ") + ")"); //NON-NLS
                while (analyzedQuery.next()) {
                    return analyzedQuery.getInt("analyzed") == fileIds.size();
                }
                return false;
            }
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * Returns whether or not the given group is analyzed and ready to be
     * viewed.
     *
     * @param groupKey group key.
     *
     * @return true if the group is analyzed.
     *
     * @throws SQLException
     * @throws TskCoreException
     */
    public Boolean isGroupAnalyzed(GroupKey<?> groupKey) throws SQLException, TskCoreException {

        // Callback to process result of isAnalyzed query
        class IsGroupAnalyzedQueryResultProcessor implements CaseDbAccessQueryCallback {

            private boolean isAnalyzed = false;

            boolean getIsAnalyzed() {
                return isAnalyzed;
            }

            @Override
            public void process(ResultSet resultSet) {
                try {
                    if (resultSet.next()) {
                        isAnalyzed = resultSet.getInt("is_analyzed") == 1 ? true : false;
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get group is_analyzed", ex); //NON-NLS
                }
            }
        }

        IsGroupAnalyzedQueryResultProcessor queryResultProcessor = new IsGroupAnalyzedQueryResultProcessor();
        try {
            String groupAnalyzedQueryStmt = String.format("is_analyzed FROM " + CASE_DB_GROUPS_TABLENAME
                    + " WHERE attribute = \'%s\' AND value = \'%s\' and data_source_obj_id = %d ",
                    SleuthkitCase.escapeSingleQuotes(groupKey.getAttribute().attrName.toString()),
                    SleuthkitCase.escapeSingleQuotes(groupKey.getValueDisplayName()),
                    groupKey.getAttribute() == DrawableAttribute.PATH ? groupKey.getDataSourceObjId() : 0);

            caseDb.getCaseDbAccessManager().select(groupAnalyzedQueryStmt, queryResultProcessor);
            return queryResultProcessor.getIsAnalyzed();
        } catch (TskCoreException ex) {
            String msg = String.format("Failed to get group is_analyzed for group key %s", groupKey.getValueDisplayName()); //NON-NLS
            logger.log(Level.SEVERE, msg, ex);
        }

        return false;
    }

    /**
     * Find and return list of all ids of files matching the specific Where
     * clause
     *
     * @param sqlWhereClause a SQL where clause appropriate for the desired
     *                       files (do not begin the WHERE clause with the word
     *                       WHERE!)
     *
     * @return a list of file ids each of which satisfy the given WHERE clause
     *
     * @throws TskCoreException
     */
    public Set<Long> findAllFileIdsWhere(String sqlWhereClause) throws TskCoreException {
        dbWriteLock();
        try {
            if (isClosed()) {
                throw new TskCoreException("The drawables database is closed");
            }
            try (Statement statement = con.createStatement()) {
                ResultSet rs = statement.executeQuery("SELECT obj_id FROM drawable_files WHERE " + sqlWhereClause);
                Set<Long> ret = new HashSet<>();
                while (rs.next()) {
                    ret.add(rs.getLong(1));
                }
                return ret;
            } catch (SQLException ex) {
                throw new TskCoreException(String.format("Failed to query file id for WHERE clause %s", sqlWhereClause), ex);
            }
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * Return the number of files matching the given clause.
     *
     * @param sqlWhereClause a SQL where clause appropriate for the desired
     *                       files (do not begin the WHERE clause with the word
     *                       WHERE!)
     *
     * @return Number of files matching the given where clause
     *
     * @throws TskCoreException
     */
    public long countFilesWhere(String sqlWhereClause) throws TskCoreException {
        dbWriteLock();
        try {
            if (isClosed()) {
                throw new TskCoreException("The drawables database is closed");
            }
            try (Statement statement = con.createStatement()) {
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS COUNT FROM drawable_files WHERE " + sqlWhereClause);
                return rs.getLong("COUNT");
            } catch (SQLException e) {
                throw new TskCoreException("SQLException thrown when calling 'DrawableDB.countFilesWhere(): " + sqlWhereClause, e);
            }
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * Get all the values that are in db for the given attribute.
     *
     *
     * @param <A>        The type of values for the given attribute.
     * @param groupBy    The attribute to get the values for.
     * @param sortBy     The way to sort the results. Only GROUP_BY_VAL and
     *                   FILE_COUNT are supported.
     * @param sortOrder  Sort ascending or descending.
     * @param dataSource
     *
     * @return Map of data source (or null of group by attribute ignores data
     *         sources) to list of unique group values
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    @SuppressWarnings("unchecked")
    public <A extends Comparable<A>> Multimap<DataSource, A> findValuesForAttribute(DrawableAttribute<A> groupBy, GroupSortBy sortBy, SortOrder sortOrder, DataSource dataSource) throws TskCoreException {

        switch (groupBy.attrName) {
            case ANALYZED:
            case CATEGORY:
            case HASHSET:
                //these are somewhat special cases for now as they have fixed values, or live in the main autopsy database
                //they should have special handling at a higher level of the stack.
                throw new UnsupportedOperationException();
            default:
                dbWriteLock();
                try {
                    if (isClosed()) {
                        throw new TskCoreException("The drawables database is closed");
                    }
                    //TODO: convert this to prepared statement 
                    StringBuilder query = new StringBuilder("SELECT data_source_obj_id, " + groupBy.attrName.toString() + ", COUNT(*) FROM drawable_files "); //NON-NLS

                    // skip any null/blank values
                    query.append("WHERE LENGTH(" + groupBy.attrName.toString() + ") > 0 ");

                    if (dataSource != null) {
                        query.append(" AND data_source_obj_id = ").append(dataSource.getId());
                    }

                    query.append(" GROUP BY data_source_obj_id, ").append(groupBy.attrName.toString());

                    String orderByClause = "";

                    if (sortBy == GROUP_BY_VALUE) {
                        orderByClause = " ORDER BY " + groupBy.attrName.toString();
                    } else if (sortBy == GroupSortBy.FILE_COUNT) {
                        orderByClause = " ORDER BY COUNT(*)";
                    }

                    query.append(orderByClause);

                    if (orderByClause.isEmpty() == false) {
                        String sortOrderClause = "";

                        switch (sortOrder) {
                            case DESCENDING:
                                sortOrderClause = " DESC"; //NON-NLS
                                break;
                            case ASCENDING:
                                sortOrderClause = " ASC"; //NON-NLS
                                break;
                            default:
                                orderByClause = "";
                        }
                        query.append(sortOrderClause);
                    }

                    try (Statement stmt = con.createStatement()) {
                        ResultSet results = stmt.executeQuery(query.toString());
                        Multimap<DataSource, A> values = HashMultimap.create();
                        while (results.next()) {
                            /*
                             * I don't like that we have to do this cast to A
                             * here, but can't think of a better alternative at
                             * the momment unless something has gone seriously
                             * wrong, we know this should be of type A even if
                             * JAVA doesn't
                             */
                            values.put(caseDb.getDataSource(results.getLong("data_source_obj_id")),
                                    (A) results.getObject(groupBy.attrName.toString()));
                        }
                        return values;
                    } catch (SQLException | TskDataException ex) {
                        throw new TskCoreException("Unable to get values for attribute", ex); //NON-NLS
                    }

                } finally {
                    dbWriteUnlock();
                }
        }
    }

    /**
     * Insert new group into DB
     *
     * @param value             Value of the group (unique to the type)
     * @param groupBy           Type of the grouping (CATEGORY, MAKE, etc.)
     * @param caseDbTransaction transaction to use for CaseDB insert/updates
     *
     * @throws TskCoreException
     */
    private void insertGroup(final String value, DrawableAttribute<?> groupBy, CaseDbTransaction caseDbTransaction) throws TskCoreException {
        insertGroup(0, value, groupBy, caseDbTransaction);
    }

    /**
     * Insert new group into DB
     *
     * @param ds_obj_id         data source object id
     * @param value             Value of the group (unique to the type)
     * @param groupBy           Type of the grouping (CATEGORY, MAKE, etc.)
     * @param caseDbTransaction transaction to use for CaseDB insert/updates
     */
    private void insertGroup(long ds_obj_id, final String value, DrawableAttribute<?> groupBy, CaseDbTransaction caseDbTransaction) throws TskCoreException {
        /*
         * Check the groups cache to see if the group has already been added to
         * the case database.
         */
        String cacheKey = Long.toString(ds_obj_id) + "_" + value + "_" + groupBy.getDisplayName();
        if (groupCache.getIfPresent(cacheKey) != null) {
            return;
        }

        int isAnalyzed = (groupBy == DrawableAttribute.PATH) ? 0 : 1;
        String insertSQL = String.format(" (data_source_obj_id, value, attribute, is_analyzed) VALUES (%d, \'%s\', \'%s\', %d)",
                ds_obj_id, SleuthkitCase.escapeSingleQuotes(value), SleuthkitCase.escapeSingleQuotes(groupBy.attrName.toString()), isAnalyzed);
        if (DbType.POSTGRESQL == caseDb.getDatabaseType()) {
            insertSQL += " ON CONFLICT DO NOTHING";
        }
        caseDb.getCaseDbAccessManager().insert(CASE_DB_GROUPS_TABLENAME, insertSQL, caseDbTransaction);
        groupCache.put(cacheKey, Boolean.TRUE);
    }

    /**
     * @param id the obj_id of the file to return
     *
     * @return a DrawableFile for the given obj_id
     *
     * @throws TskCoreException if unable to get a file from the currently open
     *                          {@link SleuthkitCase}
     */
    public DrawableFile getFileFromID(Long id) throws TskCoreException {
        AbstractFile f = caseDb.getAbstractFileById(id);
        try {
            return DrawableFile.create(f, areFilesAnalyzed(Collections.singleton(id)), isVideoFile(f));
        } catch (SQLException ex) {
            throw new TskCoreException(String.format("Failed to get file (id=%d)", id), ex);
        }
    }

    public Set<Long> getFileIDsInGroup(GroupKey<?> groupKey) throws TskCoreException {

        if (groupKey.getAttribute().isDBColumn == false) {
            switch (groupKey.getAttribute().attrName) {
                case MIME_TYPE:
                    return groupManager.getFileIDsWithMimeType((String) groupKey.getValue());
                case CATEGORY:
                    return groupManager.getFileIDsWithCategory((TagName) groupKey.getValue());
                case TAGS:
                    return groupManager.getFileIDsWithTag((TagName) groupKey.getValue());
            }
        }
        Set<Long> files = new HashSet<>();
        dbWriteLock();
        try {
            PreparedStatement statement = getGroupStatment(groupKey);
            setQueryParams(statement, groupKey);

            try (ResultSet valsResults = statement.executeQuery()) {
                while (valsResults.next()) {
                    files.add(valsResults.getLong("obj_id"));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "failed to get file for group:" + groupKey.getAttribute() + " == " + groupKey.getValue(), ex); //NON-NLS
        } finally {
            dbWriteUnlock();
        }

        return files;
    }

    private PreparedStatement getGroupStatment(GroupKey<?> groupKey) {
        DrawableAttribute<?> groupBy = groupKey.getAttribute();
        if ((groupBy == DrawableAttribute.PATH) && groupKey.getDataSource().isPresent()) {

            return this.groupStatementFilterByDataSrcMap.get(groupBy);
        }

        return groupStatementMap.get(groupBy);
    }

    public long countAllFiles() throws TskCoreException {
        return countFilesWhere(" 1 ");
    }

    /**
     * delete the row with obj_id = id.
     *
     * @param id the obj_id of the row to be deleted
     */
    public void removeFile(long id, DrawableTransaction tr) {
        if (tr.isCompleted()) {
            throw new IllegalArgumentException("Attempt to use a completed transaction");
        }
        dbWriteLock();
        try {
            // Update the list of file IDs in memory
            removeImageFileFromList(id);

            //"delete from hash_set_hits where (obj_id = " + id + ")"
            deleteHashHitStmt.setLong(1, id);
            deleteHashHitStmt.executeUpdate();

            //"delete from drawable_files where (obj_id = " + id + ")"
            deleteFileStmt.setLong(1, id);
            deleteFileStmt.executeUpdate();
            tr.addRemovedFile(id);

        } catch (SQLException ex) {
            logger.log(Level.WARNING, "failed to delete row for obj_id = " + id, ex); //NON-NLS
        } finally {
            dbWriteUnlock();

        }
    }

    /**
     * Deletes a cascading delete of a data source, starting from the
     * datasources table.
     *
     * @param dataSourceID The object ID of the data source to delete.
     *
     * @throws SQLException
     * @throws TskCoreException
     */
    public void deleteDataSource(long dataSourceID) throws SQLException, TskCoreException {
        dbWriteLock();
        DrawableTransaction trans = null;
        String whereClause = "WHERE data_source_obj_id = " + dataSourceID;
        String tableName = "image_gallery_groups";
        try {
            trans = beginTransaction();
            deleteDataSourceStmt.setLong(1, dataSourceID);
            deleteDataSourceStmt.executeUpdate();
            if (caseDb.getCaseDbAccessManager().tableExists(tableName)) {
                caseDb.getCaseDbAccessManager().delete(tableName, whereClause);
            }
            commitTransaction(trans, true);
        } catch (SQLException | TskCoreException ex) {
            if (null != trans) {
                try {
                    rollbackTransaction(trans);
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to roll back drawables db transaction after error: %s", ex.getMessage()), ex2); //NON-NLS
                }
            }
            throw ex;
        } finally {
            dbWriteUnlock();

        }
    }

    public class MultipleTransactionException extends IllegalStateException {

        public MultipleTransactionException() {
            super("cannot have more than one open transaction");//NON-NLS
        }
    }

    /**
     * For performance reasons, keep a list of all file IDs currently in the
     * drawable database. Otherwise the database is queried many times to
     * retrieve the same data.
     */
    @GuardedBy("fileIDlist")
    private final Set<Long> fileIDsInDB = new HashSet<>();

    public boolean isInDB(Long id) {
        synchronized (fileIDsInDB) {
            return fileIDsInDB.contains(id);
        }
    }

    private void addImageFileToList(Long id) {
        synchronized (fileIDsInDB) {
            fileIDsInDB.add(id);
        }
    }

    private void removeImageFileFromList(Long id) {
        synchronized (fileIDsInDB) {
            fileIDsInDB.remove(id);
        }
    }

    public int getNumberOfImageFilesInList() {
        synchronized (fileIDsInDB) {
            return fileIDsInDB.size();
        }
    }

    private boolean initializeImageList() {
        dbWriteLock();
        try {
            if (isClosed()) {
                logger.log(Level.SEVERE, "The drawables database is closed"); //NON-NLS
                return false;
            }
            try (Statement stmt = con.createStatement()) {
                ResultSet analyzedQuery = stmt.executeQuery("select obj_id from drawable_files");
                while (analyzedQuery.next()) {
                    addImageFileToList(analyzedQuery.getLong("obj_id"));
                }
                return true;
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to add image file object ids in drawables database to cache", ex); //NON-NLS
                return false;
            }
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * For performance reasons, keep the file type in memory
     */
    private final Map<Long, Boolean> videoFileMap = new ConcurrentHashMap<>();

    /**
     * is this File a video file?
     *
     * @param f check if this file is a video. will return false for null file.
     *
     * @return returns true if this file is a video as determined by {@link ImageGalleryModule#isVideoFile(org.sleuthkit.datamodel.AbstractFile)
     *         } but caches the result. returns false if passed a null AbstractFile
     */
    public boolean isVideoFile(AbstractFile f) {
        return isNull(f) ? false
                : videoFileMap.computeIfAbsent(f.getId(), id -> FileTypeUtils.hasVideoMIMEType(f));
    }

    /**
     * get the number of files with the given category.
     *
     * NOTE: although the category data is stored in autopsy as Tags, this
     * method is provided on DrawableDb to provide a single point of access for
     * ImageGallery data.
     *
     * //TODO: think about moving this and similar methods that don't actually
     * get their data form the drawabledb to a layer wrapping the drawable db:
     * something like ImageGalleryCaseData?
     *
     * @param cat the category to count the number of files for
     *
     * @return the number of the with the given category
     */
    public long getCategoryCount(TagName tagName) {
        try {
            if (nonNull(tagName)) {
                return caseDb.getContentTagsByTagName(tagName).stream()
                        .map(ContentTag::getContent)
                        .map(Content::getId)
                        .filter(this::isInDB)
                        .count();
            }
        } catch (IllegalStateException ex) {
            logger.log(Level.WARNING, "Case closed while getting files", ex); //NON-NLS
        } catch (TskCoreException ex1) {
            logger.log(Level.SEVERE, "Failed to get content tags by tag name.", ex1); //NON-NLS
        }
        return -1;

    }

    /**
     * get the number of files in the given set that are uncategorized(Cat-0).
     *
     * NOTE: although the category data is stored in autopsy as Tags, this
     * method is provided on DrawableDb to provide a single point of access for
     * ImageGallery data.
     *
     * //TODO: think about moving this and similar methods that don't actually
     * get their data form the drawabledb to a layer wrapping the drawable db:
     * something like ImageGalleryCaseData?
     *
     * @param fileIDs the the files ids to count within
     *
     * @return the number of files in the given set with Cat-0
     */
    public long getUncategorizedCount(Collection<Long> fileIDs) throws TskCoreException {

        // if the fileset is empty, return count as 0
        if (fileIDs.isEmpty()) {
            return 0;
        }

        // get a comma seperated list of TagName ids for non zero categories
        DrawableTagsManager tagsManager = controller.getTagsManager();

        String catTagNameIDs = tagsManager.getCategoryTagNames().stream()
                .map(TagName::getId)
                .map(Object::toString)
                .collect(Collectors.joining(",", "(", ")"));

        String fileIdsList = "(" + StringUtils.join(fileIDs, ",") + " )";

        //count the file ids that are in the given list and don't have a non-zero category assigned to them.
        String name
                = "SELECT COUNT(obj_id) as obj_count FROM tsk_files where obj_id IN " + fileIdsList //NON-NLS
                + " AND obj_id NOT IN (SELECT obj_id FROM content_tags WHERE content_tags.tag_name_id IN " + catTagNameIDs + ")"; //NON-NLS
        try (SleuthkitCase.CaseDbQuery executeQuery = caseDb.executeQuery(name);
                ResultSet resultSet = executeQuery.getResultSet();) {
            while (resultSet.next()) {
                return resultSet.getLong("obj_count"); //NON-NLS
            }
        } catch (SQLException ex) {
            throw new TskCoreException("Error getting category count.", ex); //NON-NLS
        }

        return -1;

    }

    /**
     * Encapsulates a drawables database transaction that uses the enclosing
     * DrawableDB object's single JDBC connection. The transaction is begun when
     * the DrawableTransaction object is created; clients MUST call either
     * commit or rollback.
     *
     * IMPORTANT: This transaction must be thread-confined. It acquires and
     * release a lock specific to a single thread.
     */
    public class DrawableTransaction {

        // The files are processed ORDERED BY parent path
        // We want to preserve that order here, so that we can detect a 
        // change in path, and thus mark the path group as analyzed
        // Hence we use a LinkedHashSet here.
        private final Set<Long> updatedFiles = new LinkedHashSet<>();
        private final Set<Long> removedFiles = new LinkedHashSet<>();

        private boolean completed;

        private DrawableTransaction() throws TskCoreException, SQLException {
            dbWriteLock(); // Normally released when commit or rollback is called.
            if (DrawableDB.this.isClosed()) {
                dbWriteUnlock();
                throw new TskCoreException("The drawables database is closed");
            }
            try {
                con.setAutoCommit(false);
                completed = false;
            } catch (SQLException ex) {
                completed = true;
                dbWriteUnlock();
                throw new SQLException("Failed to begin transaction", ex);
            }
        }

        synchronized public void rollback() throws SQLException {
            if (!completed) {
                try {
                    updatedFiles.clear();
                    con.rollback();
                } finally {
                    complete();
                }
            }
        }

        /**
         * Commit changes that happened during this transaction
         *
         * @param notifyGM If true, notify GroupManager about the changes.
         */
        synchronized public void commit(Boolean notifyGM) throws SQLException {
            if (!completed) {
                try {

                    con.commit();

                    /*
                     * Need to close the transaction before notifying the Group
                     * Manager, so that the lock is released.
                     */
                    complete();

                    if (notifyGM) {
                        if (groupManager != null) {
                            groupManager.handleFileUpdate(updatedFiles);
                            groupManager.handleFileRemoved(removedFiles);
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to commit transaction, will attempt rollback", ex); //NON-NLS
                    rollback();
                }
            }
        }

        synchronized private void complete() {
            if (!completed) {
                try {
                    con.setAutoCommit(true);
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to set auto-commit to false", ex); //NON-NLS
                } finally {
                    completed = true;
                    dbWriteUnlock();
                }
            }
        }

        synchronized private Boolean isCompleted() {
            return completed;
        }

        synchronized private void addUpdatedFile(Long f) {
            updatedFiles.add(f);
        }

        synchronized private void addRemovedFile(long id) {
            removedFiles.add(id);
        }
    }
}
