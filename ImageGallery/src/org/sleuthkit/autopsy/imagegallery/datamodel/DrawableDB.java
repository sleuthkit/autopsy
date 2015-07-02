/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.SortOrder;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryModule;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupKey;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupSortBy;
import static org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupSortBy.GROUP_BY_VALUE;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sqlite.SQLiteJDBCLoader;

/**
 * This class is the public interface to the Image / Video Analyzer SQLite
 * database. This class borrows a lot of ideas and techniques (for good or ill)
 * from {@link  SleuthkitCase}.
 *
 * TODO: Creating an abstract base class for sqlite databases may make sense in
 * the future. see also {@link EventsDB} in the timeline viewer.
 */
public final class DrawableDB {

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(DrawableDB.class.getName());

    //column name constants//////////////////////
    private static final String ANALYZED = "analyzed";

    private static final String OBJ_ID = "obj_id";

    private static final String HASH_SET_NAME = "hash_set_name";

    private final PreparedStatement insertHashSetStmt;

    private final PreparedStatement groupSeenQueryStmt;

    private final PreparedStatement insertGroupStmt;

    private final List<PreparedStatement> preparedStatements = new ArrayList<>();

    private final PreparedStatement removeFileStmt;

    private final PreparedStatement updateGroupStmt;

    private final PreparedStatement selectHashSetStmt;

    private final PreparedStatement selectHashSetNamesStmt;

    private final PreparedStatement insertHashHitStmt;

    private final PreparedStatement updateFileStmt;
    private PreparedStatement insertFileStmt;

    private final PreparedStatement pathGroupStmt;

    private final PreparedStatement nameGroupStmt;

    private final PreparedStatement created_timeGroupStmt;

    private final PreparedStatement modified_timeGroupStmt;

    private final PreparedStatement makeGroupStmt;

    private final PreparedStatement modelGroupStmt;

    private final PreparedStatement analyzedGroupStmt;

    private final PreparedStatement hashSetGroupStmt;

    /**
     * map from {@link DrawableAttribute} to the {@link PreparedStatement} thet
     * is used to select groups for that attribute
     */
    private final Map<DrawableAttribute<?>, PreparedStatement> groupStatementMap = new HashMap<>();

    private GroupManager groupManager;

    private final Path dbPath;

    volatile private Connection con;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); //use fairness policy

    private final Lock DBLock = rwLock.writeLock(); //using exclusing lock for all db ops for now

    static {//make sure sqlite driver is loaded // possibly redundant
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load sqlite JDBC driver", ex);
        }
    }
    private final SleuthkitCase tskCase;
    private final ImageGalleryController controller;

    //////////////general database logic , mostly borrowed from sleuthkitcase
    /**
     * Lock to protect against concurrent write accesses to case database and to
     * block readers while database is in write transaction. Should be utilized
     * by all db code where underlying storage supports max. 1 concurrent writer
     * MUST always call dbWriteUnLock() as early as possible, in the same thread
     * where dbWriteLock() was called
     */
    public void dbWriteLock() {
        //Logger.getLogger("LOCK").log(Level.INFO, "Locking " + rwLock.toString());
        DBLock.lock();
    }

    /**
     * Release previously acquired write lock acquired in this thread using
     * dbWriteLock(). Call in "finally" block to ensure the lock is always
     * released.
     */
    public void dbWriteUnlock() {
        //Logger.getLogger("LOCK").log(Level.INFO, "UNLocking " + rwLock.toString());
        DBLock.unlock();
    }

    /**
     * Lock to protect against read while it is in a write transaction state.
     * Supports multiple concurrent readers if there is no writer. MUST always
     * call dbReadUnLock() as early as possible, in the same thread where
     * dbReadLock() was called.
     */
    void dbReadLock() {
        DBLock.lock();
    }

    /**
     * Release previously acquired read lock acquired in this thread using
     * dbReadLock(). Call in "finally" block to ensure the lock is always
     * released.
     */
    void dbReadUnlock() {
        DBLock.unlock();
    }

    /**
     * @param dbPath the path to the db file
     *
     * @throws SQLException if there is problem creating or configuring the db
     */
    private DrawableDB(Path dbPath, ImageGalleryController controller) throws SQLException, ExceptionInInitializerError, IOException {
        this.dbPath = dbPath;
        this.controller = controller;
        this.tskCase = controller.getSleuthKitCase();
        this.groupManager = controller.getGroupManager();
        Files.createDirectories(dbPath.getParent());
        if (initializeDBSchema()) {
            updateFileStmt = prepareStatement(
                    "INSERT OR REPLACE INTO drawable_files (obj_id , path, name, created_time, modified_time, make, model, analyzed) "
                    + "VALUES (?,?,?,?,?,?,?,?)");
            insertFileStmt = prepareStatement(
                    "INSERT OR IGNORE INTO drawable_files (obj_id , path, name, created_time, modified_time, make, model, analyzed) "
                    + "VALUES (?,?,?,?,?,?,?,?)");

            removeFileStmt = prepareStatement("delete from drawable_files where obj_id = ?");

            pathGroupStmt = prepareStatement("select obj_id , analyzed from drawable_files where  path  = ? ", DrawableAttribute.PATH);
            nameGroupStmt = prepareStatement("select obj_id , analyzed from drawable_files where  name  = ? ", DrawableAttribute.NAME);
            created_timeGroupStmt = prepareStatement("select obj_id , analyzed from drawable_files where  created_time  = ? ", DrawableAttribute.CREATED_TIME);
            modified_timeGroupStmt = prepareStatement("select obj_id , analyzed from drawable_files where  modified_time  = ? ", DrawableAttribute.MODIFIED_TIME);
            makeGroupStmt = prepareStatement("select obj_id , analyzed from drawable_files where  make  = ? ", DrawableAttribute.MAKE);
            modelGroupStmt = prepareStatement("select obj_id , analyzed from drawable_files where  model  = ? ", DrawableAttribute.MODEL);
            analyzedGroupStmt = prepareStatement("Select obj_id , analyzed from drawable_files where analyzed = ?", DrawableAttribute.ANALYZED);
            hashSetGroupStmt = prepareStatement("select drawable_files.obj_id as obj_id, analyzed from drawable_files ,  hash_sets , hash_set_hits  where drawable_files.obj_id = hash_set_hits.obj_id and hash_sets.hash_set_id = hash_set_hits.hash_set_id and hash_sets.hash_set_name = ?", DrawableAttribute.HASHSET);

            updateGroupStmt = prepareStatement("insert or replace into groups (seen, value, attribute) values( ?, ? , ?)");
            insertGroupStmt = prepareStatement("insert or ignore into groups (value, attribute) values (?,?)");

            groupSeenQueryStmt = prepareStatement("select seen from groups where value = ? and attribute = ?");

            selectHashSetNamesStmt = prepareStatement("SELECT DISTINCT hash_set_name FROM hash_sets");
            insertHashSetStmt = prepareStatement("insert or ignore into hash_sets (hash_set_name)  values (?)");
            selectHashSetStmt = prepareStatement("select hash_set_id from hash_sets where hash_set_name = ?");

            insertHashHitStmt = prepareStatement("insert or ignore into hash_set_hits (hash_set_id, obj_id) values (?,?)");

            for (Category cat : Category.values()) {
                insertGroup(cat.getDisplayName(), DrawableAttribute.CATEGORY);
            }
            initializeImageList();
        } else {
            throw new ExceptionInInitializerError();
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
    private PreparedStatement prepareStatement(String stmtString) throws SQLException {
        PreparedStatement prepareStatement = con.prepareStatement(stmtString);
        preparedStatements.add(prepareStatement);
        return prepareStatement;
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
    private PreparedStatement prepareStatement(String stmtString, DrawableAttribute<?> attr) throws SQLException {
        PreparedStatement prepareStatement = prepareStatement(stmtString);
        if (attr != null) {
            groupStatementMap.put(attr, prepareStatement);
        }

        return prepareStatement;
    }

    /**
     * public factory method. Creates and opens a connection to a new database *
     * at the given path.
     *
     * @param dbPath
     *
     * @return
     */
    public static DrawableDB getDrawableDB(Path dbPath, ImageGalleryController controller) {

        try {
            return new DrawableDB(dbPath.resolve("drawable.db"), controller);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "sql error creating database connection", ex);
            return null;
        } catch (ExceptionInInitializerError | IOException ex) {
            LOGGER.log(Level.SEVERE, "error creating database connection", ex);
            return null;
        }
    }

    private void setPragmas() throws SQLException {

        //this should match Sleuthkit db setupt
        try (Statement statement = con.createStatement()) {
            //reduce i/o operations, we have no OS crash recovery anyway
            statement.execute("PRAGMA synchronous = OFF;");
            //allow to query while in transaction - no need read locks
            statement.execute("PRAGMA read_uncommitted = True;");

            //TODO: do we need this?
            statement.execute("PRAGMA foreign_keys = ON");

            //TODO: test this
            statement.execute("PRAGMA journal_mode  = MEMORY");
//
            //we don't use this feature, so turn it off for minimal speed up on queries
            //this is deprecated and not recomended
            statement.execute("PRAGMA count_changes = OFF;");
            //this made a big difference to query speed
            statement.execute("PRAGMA temp_store = MEMORY");
            //this made a modest improvement in query speeds
            statement.execute("PRAGMA cache_size = 50000");
            //we never delete anything so...
            statement.execute("PRAGMA auto_vacuum = 0");
        }

        try {
            LOGGER.log(Level.INFO, String.format("sqlite-jdbc version %s loaded in %s mode",
                    SQLiteJDBCLoader.getVersion(), SQLiteJDBCLoader.isNativeMode()
                            ? "native" : "pure-java"));
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "exception while checking sqlite-jdbc version and mode", exception);
        }

    }

    /**
     * create the table and indices if they don't already exist
     *
     * @return the number of rows in the table , count > 0 indicating an
     *         existing table
     */
    private boolean initializeDBSchema() {
        try {
            if (isClosed()) {
                openDBCon();
            }
            setPragmas();

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "problem accessing  database", ex);
            return false;
        }
        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE TABLE  if not exists drawable_files "
                    + "( obj_id INTEGER PRIMARY KEY, "
                    + " path VARCHAR(255), "
                    + " name VARCHAR(255), "
                    + " created_time integer, "
                    + " modified_time integer, "
                    + " make VARCHAR(255), "
                    + " model VARCHAR(255), "
                    + " analyzed integer DEFAULT 0)";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "problem creating drawable_files table", ex);
            return false;
        }

        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE TABLE  if not exists groups "
                    + "(group_id INTEGER PRIMARY KEY, "
                    + " value VARCHAR(255) not null, "
                    + " attribute VARCHAR(255) not null, "
                    + " seen integer DEFAULT 0, "
                    + " UNIQUE(value, attribute) )";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "problem creating groups table", ex);
            return false;
        }

        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE TABLE  if not exists hash_sets "
                    + "( hash_set_id INTEGER primary key,"
                    + " hash_set_name VARCHAR(255) UNIQUE NOT NULL)";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "problem creating hash_sets table", ex);
            return false;
        }

        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE TABLE  if not exists hash_set_hits "
                    + "(hash_set_id INTEGER REFERENCES hash_sets(hash_set_id) not null, "
                    + " obj_id INTEGER REFERENCES drawable_files(obj_id) not null, "
                    + " PRIMARY KEY (hash_set_id, obj_id))";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "problem creating hash_set_hits table", ex);
            return false;
        }

        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE  INDEX if not exists path_idx ON drawable_files(path)";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "problem creating path_idx", ex);
        }

        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE  INDEX if not exists name_idx ON drawable_files(name)";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "problem creating name_idx", ex);
        }

        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE  INDEX if not exists make_idx ON drawable_files(make)";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "problem creating make_idx", ex);
        }

        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE  INDEX if not exists model_idx ON drawable_files(model)";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "problem creating model_idx", ex);
        }

        try (Statement stmt = con.createStatement()) {
            String sql = "CREATE  INDEX if not exists analyzed_idx ON drawable_files(analyzed)";
            stmt.execute(sql);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "problem creating analyzed_idx", ex);
        }

        return true;
    }

    @Override
    public void finalize() throws Throwable {
        try {
            closeDBCon();
        } finally {
            super.finalize();
        }
    }

    public void closeDBCon() {
        if (con != null) {
            try {
                closeStatements();
                con.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Failed to close connection to drawable.db", ex);
            }
        }
        con = null;
    }

    public void openDBCon() {
        try {
            if (con == null || con.isClosed()) {
                con = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to open connection to drawable.db", ex);
        }
    }

    public boolean isClosed() throws SQLException {
        if (con == null) {
            return true;
        }
        return con.isClosed();
    }

    /**
     * get all the hash set names used in the db
     *
     * @return a set of the names of all the hash sets that have hash set hits
     */
    public Set<String> getHashSetNames() {
        Set<String> names = new HashSet<>();
        // "SELECT DISTINCT hash_set_name FROM hash_sets"
        dbReadLock();
        try (ResultSet rs = selectHashSetNamesStmt.executeQuery();) {
            while (rs.next()) {
                names.add(rs.getString(HASH_SET_NAME));
            }
        } catch (SQLException sQLException) {
            LOGGER.log(Level.WARNING, "failed to get hash set names", sQLException);
        } finally {
            dbReadUnlock();
        }
        return names;
    }

    public boolean isGroupSeen(GroupKey<?> groupKey) {
        dbReadLock();
        try {
            groupSeenQueryStmt.clearParameters();
            groupSeenQueryStmt.setString(1, groupKey.getValueDisplayName());
            groupSeenQueryStmt.setString(2, groupKey.getAttribute().attrName.toString());
            try (ResultSet rs = groupSeenQueryStmt.executeQuery()) {
                while (rs.next()) {
                    return rs.getBoolean("seen");
                }
            }
        } catch (SQLException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            dbReadUnlock();
        }
        return false;
    }

    public void markGroupSeen(GroupKey<?> gk, boolean seen) {
        dbWriteLock();
        try {
            //PreparedStatement updateGroup = con.prepareStatement("update groups set seen = ? where value = ? and attribute = ?");
            updateGroupStmt.clearParameters();
            updateGroupStmt.setBoolean(1, seen);
            updateGroupStmt.setString(2, gk.getValueDisplayName());
            updateGroupStmt.setString(3, gk.getAttribute().attrName.toString());
            updateGroupStmt.execute();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error marking group as seen", ex);
        } finally {
            dbWriteUnlock();
        }
    }

    public boolean removeFile(long id) {
        DrawableTransaction trans = beginTransaction();
        boolean removeFile = removeFile(id, trans);
        commitTransaction(trans, true);
        return removeFile;
    }

    public void updateFile(DrawableFile<?> f) {
        DrawableTransaction trans = beginTransaction();
        updateFile(f, trans);
        commitTransaction(trans, true);
    }

    public void insertFile(DrawableFile<?> f) {
        DrawableTransaction trans = beginTransaction();
        insertFile(f, trans);
        commitTransaction(trans, true);
    }

    public void insertFile(DrawableFile<?> f, DrawableTransaction tr) {
        insertOrUpdateFile(f, tr, insertFileStmt);
    }

    public void updateFile(DrawableFile<?> f, DrawableTransaction tr) {
        insertOrUpdateFile(f, tr, updateFileStmt);
    }

    private void insertOrUpdateFile(DrawableFile<?> f, DrawableTransaction tr, PreparedStatement stmt) {

        //TODO:      implement batch version -jm
        if (tr.isClosed()) {
            throw new IllegalArgumentException("can't update database with closed transaction");
        }

        dbWriteLock();
        try {
            // Update the list of file IDs in memory
            addImageFileToList(f.getId());

            // "INSERT OR IGNORE/ INTO drawable_files (path, name, created_time, modified_time, make, model, analyzed)"
            stmt.setLong(1, f.getId());
            stmt.setString(2, f.getDrawablePath());
            stmt.setString(3, f.getName());
            stmt.setLong(4, f.getCrtime());
            stmt.setLong(5, f.getMtime());
            stmt.setString(6, f.getMake());
            stmt.setString(7, f.getModel());
            stmt.setBoolean(8, f.isAnalyzed());
            stmt.executeUpdate();

            final Collection<String> hashSetNames = getHashSetsForFileFromAutopsy(f.getId());

            for (String name : hashSetNames) {

                // "insert or ignore into hash_sets (hash_set_name)  values (?)"
                insertHashSetStmt.setString(1, name);
                insertHashSetStmt.executeUpdate();

                //TODO: use nested select to get hash_set_id rather than seperate statement/query
                //"select hash_set_id from hash_sets where hash_set_name = ?"
                selectHashSetStmt.setString(1, name);
                try (ResultSet rs = selectHashSetStmt.executeQuery()) {
                    while (rs.next()) {
                        int hashsetID = rs.getInt("hash_set_id");
                        //"insert or ignore into hash_set_hits (hash_set_id, obj_id) values (?,?)";
                        insertHashHitStmt.setInt(1, hashsetID);
                        insertHashHitStmt.setLong(2, f.getId());
                        insertHashHitStmt.executeUpdate();
                        break;
                    }
                }
            }

            //and update all groups this file is in
            for (DrawableAttribute<?> attr : DrawableAttribute.getGroupableAttrs()) {
                Collection<? extends Comparable<?>> vals = attr.getValue(f);
                for (Object val : vals) {
                    insertGroup(val.toString(), attr);
                }
            }

            tr.addUpdatedFile(f.getId());

        } catch (SQLException | NullPointerException ex) {
            // This is one of the places where we get an error if the case is closed during processing,
            // which doesn't need to be reported here.
            if (Case.isCaseOpen()) {
                LOGGER.log(Level.SEVERE, "failed to insert/update file" + f.getName(), ex);
            }
        } finally {
            dbWriteUnlock();
        }
    }

    public DrawableTransaction beginTransaction() {
        return new DrawableTransaction();
    }

    public void commitTransaction(DrawableTransaction tr, Boolean notify) {
        if (tr.isClosed()) {
            throw new IllegalArgumentException("can't close already closed transaction");
        }
        tr.commit(notify);
    }

    public Boolean isFileAnalyzed(DrawableFile<?> f) {
        return isFileAnalyzed(f.getId());
    }

    public Boolean isFileAnalyzed(long fileId) {
        dbReadLock();
        try (Statement stmt = con.createStatement();
                ResultSet analyzedQuery = stmt.executeQuery("select analyzed from drawable_files where obj_id = " + fileId)) {
            while (analyzedQuery.next()) {
                return analyzedQuery.getBoolean(ANALYZED);
            }
        } catch (SQLException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            dbReadUnlock();
        }

        return false;
    }

    public Boolean areFilesAnalyzed(Collection<Long> fileIds) {

        dbReadLock();
        try (Statement stmt = con.createStatement();
                //Can't make this a preprared statement because of the IN ( ... )
                ResultSet analyzedQuery = stmt.executeQuery("select count(analyzed) as analyzed from drawable_files where analyzed = 1 and obj_id in (" + StringUtils.join(fileIds, ", ") + ")")) {
            while (analyzedQuery.next()) {
                return analyzedQuery.getInt(ANALYZED) == fileIds.size();
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "problem counting analyzed files: ", ex);
        } finally {
            dbReadUnlock();
        }

        return false;
    }

    public Boolean isGroupAnalyzed(GroupKey<?> gk) {
        dbReadLock();
        try {
            Set<Long> fileIDsInGroup = getFileIDsInGroup(gk);

            try {
                // In testing, this method appears to be a lot faster than doing one large select statement
                for (Long fileID : fileIDsInGroup) {
                    Statement stmt = con.createStatement();
                    ResultSet analyzedQuery = stmt.executeQuery("select analyzed from drawable_files where obj_id = " + fileID);
                    while (analyzedQuery.next()) {
                        if (analyzedQuery.getInt(ANALYZED) == 0) {
                            return false;
                        }
                    }
                    return true;
                }

            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "problem counting analyzed files: ", ex);
            }

            //// Old method
            //try (Statement stmt = con.createStatement();
            //        //Can't make this a preprared statement because of the IN ( ... )
            //        ResultSet analyzedQuery = stmt.executeQuery("select count(analyzed) as analyzed from drawable_files where analyzed = 1 and obj_id in (" + StringUtils.join(fileIDsInGroup, ", ") + ")")) {
            //    while (analyzedQuery.next()) {
            //        return analyzedQuery.getInt(ANALYZED) == fileIDsInGroup.size();
            //    }
            //} catch (SQLException ex) {
            //    LOGGER.log(Level.WARNING, "problem counting analyzed files: ", ex);
            //}
        } catch (TskCoreException tskCoreException) {
            LOGGER.log(Level.WARNING, "problem counting analyzed files: ", tskCoreException);
        } finally {
            dbReadUnlock();
        }
        return false;
    }

    /**
     * Find and return list of all ids of files matching the specific Where
     * clause
     *
     * @param sqlWhereClause a SQL where clause appropriate for the desired
     *                       files (do not begin the WHERE clause with the word WHERE!)
     *
     * @return a list of file ids each of which satisfy the given WHERE
     *         clause
     *
     * @throws TskCoreException
     */
    public Set<Long> findAllFileIdsWhere(String sqlWhereClause) throws TskCoreException {
        Statement statement = null;
        ResultSet rs = null;
        Set<Long> ret = new HashSet<>();
        dbReadLock();
        try {
            statement = con.createStatement();
            rs = statement.executeQuery("SELECT obj_id FROM drawable_files WHERE " + sqlWhereClause);
            while (rs.next()) {
                ret.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new TskCoreException("SQLException thrown when calling 'DrawableDB.findAllFileIdsWhere(): " + sqlWhereClause, e);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error closing result set after executing  findAllFileIdsWhere", ex);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error closing statement after executing  findAllFileIdsWhere", ex);
                }
            }
            dbReadUnlock();
        }
        return ret;
    }

    /**
     * Return the number of files matching the given clause.
     *
     * @param sqlWhereClause a SQL where clause appropriate for the desired
     *                       files (do not begin the WHERE clause with the word WHERE!)
     *
     * @return Number of files matching the given where clause
     *
     * @throws TskCoreException
     */
    public long countFilesWhere(String sqlWhereClause) throws TskCoreException {
        Statement statement = null;
        ResultSet rs = null;
        dbReadLock();
        try {
            statement = con.createStatement();
            rs = statement.executeQuery("SELECT COUNT (*) FROM drawable_files WHERE " + sqlWhereClause); //NON-NLS
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new TskCoreException("SQLException thrown when calling 'DrawableDB.countFilesWhere(): " + sqlWhereClause, e);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error closing result set after executing countFilesWhere", ex);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error closing statement after executing countFilesWhere", ex);
                }
            }
            dbReadUnlock();
        }
    }

    /**
     * Count the total number of files in the database..
     *
     * @return Total number of files in the database
     *
     * @throws TskCoreException
     */
    public long countFiles() throws TskCoreException {
        Statement statement = null;
        ResultSet rs = null;
        dbReadLock();
        try {
            statement = con.createStatement();
            rs = statement.executeQuery("SELECT COUNT (*) FROM drawable_files"); //NON-NLS
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new TskCoreException("SQLException thrown when calling 'DrawableDB.countFiles(): ", e);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error closing result set after executing countFiles", ex);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error closing statement after executing countFiles", ex);
                }
            }
            dbReadUnlock();
        }
    }

    /**
     *
     *
     *
     * @param groupBy
     * @param sortBy
     * @param sortOrder
     *
     * @return
     */
    public <A extends Comparable<A>> List<A> findValuesForAttribute(DrawableAttribute<A> groupBy, GroupSortBy sortBy, SortOrder sortOrder) {

        List<A> vals = new ArrayList<>();

        switch (groupBy.attrName) {
            case ANALYZED:
            case CATEGORY:
            case HASHSET:
                //these are somewhat special cases for now as they have fixed values, or live in the main autopsy database
                //they should have special handling at a higher level of the stack.
                throw new UnsupportedOperationException();
            default:
                dbReadLock();
                //TODO: convert this to prepared statement 
                StringBuilder query = new StringBuilder("select " + groupBy.attrName.toString() + ", count(*) from drawable_files group by " + groupBy.attrName.toString());

                String orderByClause = "";
                switch (sortBy) {
                    case GROUP_BY_VALUE:
                        orderByClause = " order by " + groupBy.attrName.toString();
                        break;
                    case FILE_COUNT:
                        orderByClause = " order by count(*)";
                        break;
                    case NONE:
//                    case PRIORITY:
                        break;
                }

                query.append(orderByClause);

                if (orderByClause.equals("") == false) {
                    String sortOrderClause = "";

                    switch (sortOrder) {
                        case DESCENDING:
                            sortOrderClause = " DESC";
                            break;
                        case ASCENDING:
                            sortOrderClause = " ASC";
                            break;
                        default:
                            orderByClause = "";
                    }
                    query.append(sortOrderClause);
                }

                try (Statement stmt = con.createStatement();
                        ResultSet valsResults = stmt.executeQuery(query.toString())) {
                    while (valsResults.next()) {
                        vals.add((A) valsResults.getObject(groupBy.attrName.toString()));
                    }
                } catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, "Unable to get values for attribute", ex);
                } finally {
                    dbReadUnlock();
                }
        }

        return vals;
    }

    public void insertGroup(final String value, DrawableAttribute<?> groupBy) {
        dbWriteLock();

        try {
            //PreparedStatement insertGroup = con.prepareStatement("insert or replace into groups (value, attribute, seen) values (?,?,0)");
            insertGroupStmt.clearParameters();
            insertGroupStmt.setString(1, value);
            insertGroupStmt.setString(2, groupBy.attrName.toString());
            insertGroupStmt.execute();
        } catch (SQLException sQLException) {
            // Don't need to report it if the case was closed
            if (Case.isCaseOpen()) {
                LOGGER.log(Level.SEVERE, "Unable to insert group", sQLException);
            }
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * @param id       the obj_id of the file to return
     * @param analyzed the analyzed state of the file
     *
     * @return a DrawableFile for the given obj_id and analyzed state
     *
     * @throws TskCoreException if unable to get a file from the currently open
     *                          {@link SleuthkitCase}
     */
    private DrawableFile<?> getFileFromID(Long id, boolean analyzed) throws TskCoreException {
        try {
            AbstractFile f = tskCase.getAbstractFileById(id);
            return DrawableFile.create(f, analyzed, isVideoFile(f));
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "there is no case open; failed to load file with id: " + id, ex);
            return null;
        }
    }

    /**
     * @param id the obj_id of the file to return
     *
     * @return a DrawableFile for the given obj_id
     *
     * @throws TskCoreException if unable to get a file from the currently open
     *                          {@link SleuthkitCase}
     */
    public DrawableFile<?> getFileFromID(Long id) throws TskCoreException {
        try {
            AbstractFile f = tskCase.getAbstractFileById(id);
            return DrawableFile.create(f,
                    areFilesAnalyzed(Collections.singleton(id)), isVideoFile(f));
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "there is no case open; failed to load file with id: " + id);
            return null;
        }
    }

    public Set<Long> getFileIDsInGroup(GroupKey<?> groupKey) throws TskCoreException {

        if (groupKey.getAttribute().isDBColumn) {
            switch (groupKey.getAttribute().attrName) {
                case CATEGORY:
                    return groupManager.getFileIDsWithCategory((Category) groupKey.getValue());
                case TAGS:
                    return groupManager.getFileIDsWithTag((TagName) groupKey.getValue());
            }
        }
        Set<Long> files = new HashSet<>();
        dbReadLock();
        try {
            PreparedStatement statement = getGroupStatment(groupKey.getAttribute());
            statement.setObject(1, groupKey.getValue());

            try (ResultSet valsResults = statement.executeQuery()) {
                while (valsResults.next()) {
                    files.add(valsResults.getLong(OBJ_ID));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "failed to get file for group:" + groupKey.getAttribute() + " == " + groupKey.getValue(), ex);
        } finally {
            dbReadUnlock();
        }

        return files;
    }

    public List<DrawableFile<?>> getFilesInGroup(GroupKey<?> key) throws TskCoreException {
        List<DrawableFile<?>> files = new ArrayList<>();
        dbReadLock();
        try {
            PreparedStatement statement = null;

            /* I hate this! not flexible/generic/maintainable we could have the
             * DrawableAttribute provide/create/configure the correct statement
             * but they shouldn't be coupled like that -jm */
            switch (key.getAttribute().attrName) {
                case CATEGORY:
                    return getFilesWithCategory((Category) key.getValue());
                default:
                    statement = getGroupStatment(key.getAttribute());
            }

            statement.setObject(1, key.getValue());

            try (ResultSet valsResults = statement.executeQuery()) {
                while (valsResults.next()) {
                    files.add(getFileFromID(valsResults.getLong(OBJ_ID), valsResults.getBoolean(ANALYZED)));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "failed to get file for group:" + key.getAttribute() + " == " + key.getValue(), ex);
        } finally {
            dbReadUnlock();
        }

        return files;
    }

    private void closeStatements() throws SQLException {
        for (PreparedStatement pStmt : preparedStatements) {
            pStmt.close();
        }
    }

    public List<DrawableFile<?>> getFilesWithCategory(Category cat) throws TskCoreException, IllegalArgumentException {
        try {
            List<DrawableFile<?>> files = new ArrayList<>();
            List<ContentTag> contentTags = controller.getTagsManager().getContentTagsByTagName(controller.getTagsManager().getTagName(cat));
            for (ContentTag ct : contentTags) {
                if (ct.getContent() instanceof AbstractFile) {
                    files.add(DrawableFile.create((AbstractFile) ct.getContent(), isFileAnalyzed(ct.getContent().getId()),
                            isVideoFile((AbstractFile) ct.getContent())));
                }
            }
            return files;
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "TSK error getting files in Category:" + cat.getDisplayName(), ex);
            throw ex;
        }
    }

    private PreparedStatement getGroupStatment(DrawableAttribute<?> groupBy) {
        return groupStatementMap.get(groupBy);

    }

    public int countAllFiles() {
        int result = -1;
        dbReadLock();
        try (ResultSet rs = con.createStatement().executeQuery("select count(*) as COUNT from drawable_files")) {
            while (rs.next()) {

                result = rs.getInt("COUNT");
                break;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing SQLite database");
        } finally {
            dbReadUnlock();
        }
        return result;
    }

    /**
     * delete the row with obj_id = id.
     *
     * @param id the obj_id of the row to be deleted
     *
     * @return true if a row was deleted, 0 if not.
     */
    public boolean removeFile(long id, DrawableTransaction tr) {
        if (tr.isClosed()) {
            throw new IllegalArgumentException("can't update database with closed transaction");
        }
        int valsResults = 0;
        dbWriteLock();

        try {
            // Update the list of file IDs in memory
            removeImageFileFromList(id);

            //"delete from drawable_files where (obj_id = " + id + ")"
            removeFileStmt.setLong(1, id);
            removeFileStmt.executeUpdate();
            tr.addRemovedFile(id);

            //TODO: delete from hash_set_hits table also...
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "failed to delete row for obj_id = " + id, ex);
        } finally {
            dbWriteUnlock();
        }

        //indicates succesfull removal of 1 file
        return valsResults == 1;
    }

    public class MultipleTransactionException extends IllegalStateException {

        private static final String CANNOT_HAVE_MORE_THAN_ONE_OPEN_TRANSACTIO = "cannot have more than one open transaction";

        public MultipleTransactionException() {
            super(CANNOT_HAVE_MORE_THAN_ONE_OPEN_TRANSACTIO);
        }
    }

    /**
     * For the given fileID, get the names of all the hashsets that the file is
     * in.
     *
     * @param fileID the fileID to file all the hash sets for
     *
     * @return a set of names, each of which is a hashset that the given file is
     *         in.
     */
    @Nonnull
    public Set<String> getHashSetsForFileFromAutopsy(long fileID) {
        try {
            Set<String> hashNames = new HashSet<>();
            List<BlackboardArtifact> arts = tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT, fileID);

            for (BlackboardArtifact a : arts) {
                List<BlackboardAttribute> attrs = a.getAttributes();
                for (BlackboardAttribute attr : attrs) {
                    if (attr.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        hashNames.add(attr.getValueString());
                    }
                }
            }
            return hashNames;
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "failed to get hash sets for file", ex);
        }
        return Collections.emptySet();
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

    private void initializeImageList() {
        synchronized (fileIDsInDB) {
            dbReadLock();
            try {
                Statement stmt = con.createStatement();
                ResultSet analyzedQuery = stmt.executeQuery("select obj_id from drawable_files");
                while (analyzedQuery.next()) {
                    addImageFileToList(analyzedQuery.getLong(OBJ_ID));
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "problem loading file IDs: ", ex);
            } finally {
                dbReadUnlock();
            }
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
     *         } but caches the result.
     *         returns false if passed a null AbstractFile
     */
    public boolean isVideoFile(AbstractFile f) {

        if (Objects.isNull(f)) {
            return false;
        } else {
            return videoFileMap.computeIfAbsent(f.getId(), (id) -> ImageGalleryModule.isVideoFile(f));
        }
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
    public long getCategoryCount(Category cat) {
        try {
            return tskCase.getContentTagsByTagName(controller.getTagsManager().getTagName(cat)).stream()
                    .map(ContentTag::getContent)
                    .map(Content::getId)
                    .filter(this::isInDB)
                    .count();

        } catch (IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Case closed while getting files");
        } catch (TskCoreException ex1) {
            LOGGER.log(Level.SEVERE, "Failed to get content tags by tag name.", ex1);
        }
        return -1;
    }

    /**
     * inner class that can reference access database connection
     */
    public class DrawableTransaction {

        private final Set<Long> updatedFiles;

        private final Set<Long> removedFiles;

        private boolean closed = false;

        /**
         * factory creation method
         *
         * @param con the {@link  ava.sql.Connection}
         *
         * @return a LogicalFileTransaction for the given connection
         *
         * @throws SQLException
         */
        private DrawableTransaction() {
            this.updatedFiles = new HashSet<>();
            this.removedFiles = new HashSet<>();
            //get the write lock, released in close()
            dbWriteLock();
            try {
                con.setAutoCommit(false);

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "failed to set auto-commit to to false", ex);
            }

        }

        synchronized public void rollback() {
            if (!closed) {
                try {
                    con.rollback();
                    updatedFiles.clear();
                } catch (SQLException ex1) {
                    LOGGER.log(Level.SEVERE, "Exception while attempting to rollback!!", ex1);
                } finally {
                    close();
                }
            }
        }

        synchronized private void commit(Boolean notify) {
            if (!closed) {
                try {
                    con.commit();
                    // make sure we close before we update, bc they'll need locks
                    close();

                    if (notify) {
                        if (groupManager != null) {
                            groupManager.handleFileUpdate(updatedFiles);
                            groupManager.handleFileRemoved(removedFiles);
                        }
                    }
                } catch (SQLException ex) {
                    if (Case.isCaseOpen()) {
                        LOGGER.log(Level.SEVERE, "Error commiting drawable.db.", ex);
                    } else {
                        LOGGER.log(Level.WARNING, "Error commiting drawable.db - case is closed.");
                    }
                    rollback();
                }
            }
        }

        synchronized private void close() {
            if (!closed) {
                try {
                    con.setAutoCommit(true);
                } catch (SQLException ex) {
                    if (Case.isCaseOpen()) {
                        LOGGER.log(Level.SEVERE, "Error setting auto-commit to true.", ex);
                    } else {
                        LOGGER.log(Level.SEVERE, "Error setting auto-commit to true - case is closed");
                    }
                } finally {
                    closed = true;
                    dbWriteUnlock();
                }
            }
        }

        synchronized public Boolean isClosed() {
            return closed;
        }

        synchronized private void addUpdatedFile(Long f) {
            updatedFiles.add(f);
        }

        synchronized private void addRemovedFile(long id) {
            removedFiles.add(id);
        }
    }
}
