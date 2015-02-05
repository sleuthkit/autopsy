/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.events.db;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.events.AggregateEvent;
import org.sleuthkit.autopsy.timeline.events.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.events.type.BaseTypes;
import org.sleuthkit.autopsy.timeline.events.type.EventType;
import org.sleuthkit.autopsy.timeline.events.type.RootEventType;
import org.sleuthkit.autopsy.timeline.filters.Filter;
import org.sleuthkit.autopsy.timeline.filters.HideKnownFilter;
import org.sleuthkit.autopsy.timeline.filters.IntersectionFilter;
import org.sleuthkit.autopsy.timeline.filters.TextFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.filters.UnionFilter;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.TskData;
import org.sqlite.SQLiteJDBCLoader;

/**
 * This class provides access to the  Timeline SQLite database. This
 * class borrows a lot of ideas and techniques from {@link  SleuthkitCase},
 * Creating an abstract base class for sqlite databases, or using a higherlevel
 * persistence api may make sense in the future.
 */
public class EventDB {

    private static final String ARTIFACT_ID_COLUMN = "artifact_id"; // NON-NLS

    private static final String BASE_TYPE_COLUMN = "base_type"; // NON-NLS

    private static final String EVENT_ID_COLUMN = "event_id"; // NON-NLS

    //column name constants//////////////////////
    private static final String FILE_ID_COLUMN = "file_id"; // NON-NLS

    private static final String FULL_DESCRIPTION_COLUMN = "full_description"; // NON-NLS

    private static final String KNOWN_COLUMN = "known_state"; // NON-NLS

    private static final String LAST_ARTIFACT_ID_KEY = "last_artifact_id"; // NON-NLS

    private static final String LAST_OBJECT_ID_KEY = "last_object_id"; // NON-NLS

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(EventDB.class.getName());

    private static final String MED_DESCRIPTION_COLUMN = "med_description"; // NON-NLS

    private static final String SHORT_DESCRIPTION_COLUMN = "short_description"; // NON-NLS

    private static final String SUB_TYPE_COLUMN = "sub_type"; // NON-NLS

    private static final String TIME_COLUMN = "time"; // NON-NLS

    private static final String WAS_INGEST_RUNNING_KEY = "was_ingest_running"; // NON-NLS

    static {
        //make sure sqlite driver is loaded // possibly redundant
        try {
            Class.forName("org.sqlite.JDBC"); // NON-NLS
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load sqlite JDBC driver", ex); // NON-NLS
        }
    }

    /**
     * public factory method. Creates and opens a connection to a database at
     * the given path. If a database does not already exist at that path, one is
     * created.
     *
     * @param dbPath
     *
     * @return
     */
    public static EventDB getEventDB(String dbPath) {
        try {
            EventDB eventDB = new EventDB(dbPath + File.separator + "events.db"); // NON-NLS

            return eventDB;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "sql error creating database connection", ex); // NON-NLS
            return null;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "error creating database connection", ex); // NON-NLS
            return null;
        }
    }

    static List<Integer> getActiveSubTypes(TypeFilter filter) {
        if (filter.isActive()) {
            if (filter.getSubFilters().isEmpty()) {
                return Collections.singletonList(RootEventType.allTypes.indexOf(filter.getEventType()));
            } else {
                return filter.getSubFilters().stream().flatMap((Filter t) -> getActiveSubTypes((TypeFilter) t).stream()).collect(Collectors.toList());
            }
        } else {
            return Collections.emptyList();
        }
    }

    static String getSQLWhere(IntersectionFilter filter) {
        return filter.getSubFilters().stream()
                .filter(Filter::isActive)
                .map(EventDB::getSQLWhere)
                .collect(Collectors.joining(" and ", "( ", ")")); // NON-NLS
    }

    static String getSQLWhere(UnionFilter filter) {
        return filter.getSubFilters().stream()
                .filter(Filter::isActive)
                .map(EventDB::getSQLWhere)
                .collect(Collectors.joining(" or ", "( ", ")")); // NON-NLS
    }

    private static String getSQLWhere(Filter filter) {
        //TODO: this is here so that the filters don't depend, even implicitly, on the db, but it leads to some nasty code
        //it would all be much easier if all the getSQLWhere methods where moved to their respective filter classes
        String result = "";
        if (filter == null) {
            return "1";
        } else if (filter instanceof HideKnownFilter) {
            result = getSQLWhere((HideKnownFilter) filter);
        } else if (filter instanceof TextFilter) {
            result = getSQLWhere((TextFilter) filter);
        } else if (filter instanceof TypeFilter) {
            result = getSQLWhere((TypeFilter) filter);
        } else if (filter instanceof IntersectionFilter) {
            result = getSQLWhere((IntersectionFilter) filter);
        } else if (filter instanceof UnionFilter) {
            result = getSQLWhere((UnionFilter) filter);
        } else {
            return "1";
        }
        result = StringUtils.deleteWhitespace(result).equals("(1and1and1)") ? "1" : result; // NON-NLS
        //System.out.println(result);
        return result;
    }

    private static String getSQLWhere(HideKnownFilter filter) {
        return (filter.isActive())
               ? "(known_state is not '" + TskData.FileKnown.KNOWN.getFileKnownValue() + "')" // NON-NLS
               : "1";
    }

    private static String getSQLWhere(TextFilter filter) {
        if (filter.isActive()) {
            if (StringUtils.isBlank(filter.getText())) {
                return "1";
            }
            String strip = StringUtils.strip(filter.getText());
            return "((" + MED_DESCRIPTION_COLUMN + " like '%" + strip + "%') or (" // NON-NLS
                    + FULL_DESCRIPTION_COLUMN + " like '%" + strip + "%') or (" // NON-NLS
                    + SHORT_DESCRIPTION_COLUMN + " like '%" + strip + "%'))"; // NON-NLS
        } else {
            return "1";
        }
    }

    /**
     * generate a sql where clause for the given type filter, while trying to be
     * as simple as possible to improve performance.
     *
     * @param filter
     *
     * @return
     */
    private static String getSQLWhere(TypeFilter filter) {
        if (filter.isActive() == false) {
            return "0";
        } else if (filter.getEventType() instanceof RootEventType) {
            //if the filter is a root filter and all base type filtes and subtype filters are active,
            if (filter.getSubFilters().stream().allMatch(f
                    -> f.isActive() && ((TypeFilter) f).getSubFilters().stream().allMatch(Filter::isActive))) {
                return "1"; //then collapse clause to true
            }
        }
        return "(" + SUB_TYPE_COLUMN + " in (" + StringUtils.join(getActiveSubTypes(filter), ",") + "))"; // NON-NLS
    }

    private volatile Connection con;

    private final String dbPath;

    private PreparedStatement getDBInfoStmt;

    private PreparedStatement getEventByIDStmt;

    private PreparedStatement getMaxTimeStmt;

    private PreparedStatement getMinTimeStmt;

    private PreparedStatement insertRowStmt;

    private final Set<PreparedStatement> preparedStatements = new HashSet<>();

    private PreparedStatement recordDBInfoStmt;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); //use fairness policy

    private final Lock DBLock = rwLock.writeLock(); //using exclusing lock for all db ops for now

    private EventDB(String dbPath) throws SQLException, Exception {
        this.dbPath = dbPath;
        initializeDB();
    }

    @Override
    public void finalize() throws Throwable {
        try {
            closeDBCon();
        } finally {
            super.finalize();
        }
    }

    public Interval getSpanningInterval(Collection<Long> eventIDs) {

        Interval span = null;
        dbReadLock();
        try (Statement stmt = con.createStatement();
             //You can't inject multiple values into one ? paramater in prepared statement,
             //so we make new statement each time...
             ResultSet rs = stmt.executeQuery("select Min(time), Max(time) from events where event_id in (" + StringUtils.join(eventIDs, ", ") + ")");) { // NON-NLS
            while (rs.next()) {
                span = new Interval(rs.getLong("Min(time)"), rs.getLong("Max(time)") + 1, DateTimeZone.UTC); // NON-NLS

            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error executing get spanning interval query.", ex); // NON-NLS
        } finally {
            dbReadUnlock();
        }
        return span;
    }

    EventTransaction beginTransaction() {
        return new EventTransaction();
    }

    void closeDBCon() {
        if (con != null) {
            try {
                closeStatements();
                con.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Failed to close connection to evetns.db", ex); // NON-NLS
            }
        }
        con = null;
    }

    void commitTransaction(EventTransaction tr, Boolean notify) {
        if (tr.isClosed()) {
            throw new IllegalArgumentException("can't close already closed transaction"); // NON-NLS
        }
        tr.commit(notify);
    }

    int countAllEvents() {
        int result = -1;
        dbReadLock();
        //TODO convert this to prepared statement -jm
        try (ResultSet rs = con.createStatement().executeQuery("select count(*) as count from events")) { // NON-NLS
            while (rs.next()) {
                result = rs.getInt("count"); // NON-NLS
                break;
            }
        } catch (SQLException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            dbReadUnlock();
        }
        return result;
    }

    Map<EventType, Long> countEvents(ZoomParams params) {
        if (params.getTimeRange() != null) {
            return countEvents(params.getTimeRange().getStartMillis() / 1000, params.getTimeRange().getEndMillis() / 1000, params.getFilter(), params.getTypeZoomLevel());
        } else {
            return Collections.emptyMap();
        }
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

    //////////////general database logic , mostly borrowed from sleuthkitcase
    void dbWriteLock() {
        //Logger.getLogger("LOCK").log(Level.INFO, "Locking " + rwLock.toString());
        DBLock.lock();
    }

    /**
     * Release previously acquired write lock acquired in this thread using
     * dbWriteLock(). Call in "finally" block to ensure the lock is always
     * released.
     */
    void dbWriteUnlock() {
        //Logger.getLogger("LOCK").log(Level.INFO, "UNLocking " + rwLock.toString());
        DBLock.unlock();
    }

    void dropTable() {
        //TODO: use prepared statement - jm
        dbWriteLock();
        try (Statement createStatement = con.createStatement()) {
            createStatement.execute("drop table if exists events"); // NON-NLS
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "could not drop old events table", ex); // NON-NLS
        } finally {
            dbWriteUnlock();
        }
    }

    List<AggregateEvent> getAggregatedEvents(ZoomParams params) {
        return getAggregatedEvents(params.getTimeRange(), params.getFilter(), params.getTypeZoomLevel(), params.getDescrLOD());
    }

    Interval getBoundingEventsInterval(Interval timeRange, Filter filter) {
        long start = timeRange.getStartMillis() / 1000;
        long end = timeRange.getEndMillis() / 1000;
        final String sqlWhere = getSQLWhere(filter);

        dbReadLock();
        try (Statement stmt = con.createStatement(); //can't use prepared statement because of complex where clause
             ResultSet rs = stmt.executeQuery(" select (select Max(time) from events where time <=" + start + " and " + sqlWhere + ") as start,(select Min(time) from events where time >= " + end + " and " + sqlWhere + ") as end")) { // NON-NLS
            while (rs.next()) {

                long start2 = rs.getLong("start"); // NON-NLS
                long end2 = rs.getLong("end"); // NON-NLS

                if (end2 == 0) {
                    end2 = getMaxTime();
                }
                //System.out.println(start2 + " " + start + " " + end + " " + end2);
                return new Interval(start2 * 1000, (end2 + 1) * 1000, TimeLineController.getJodaTimeZone());
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get MIN time.", ex); // NON-NLS
        } finally {
            dbReadUnlock();
        }
        return null;
    }

    TimeLineEvent getEventById(Long eventID) {
        TimeLineEvent result = null;
        dbReadLock();
        try {
            getEventByIDStmt.clearParameters();
            getEventByIDStmt.setLong(1, eventID);
            try (ResultSet rs = getEventByIDStmt.executeQuery()) {
                while (rs.next()) {
                    result = constructTimeLineEvent(rs);
                    break;
                }
            }
        } catch (SQLException sqlEx) {
            LOGGER.log(Level.SEVERE, "exception while querying for event with id = " + eventID, sqlEx); // NON-NLS
        } finally {
            dbReadUnlock();
        }
        return result;
    }

    Set<Long> getEventIDs(Interval timeRange, Filter filter) {
        return getEventIDs(timeRange.getStartMillis() / 1000, timeRange.getEndMillis() / 1000, filter);
    }

    Set<Long> getEventIDs(Long startTime, Long endTime, Filter filter) {
        if (Objects.equals(startTime, endTime)) {
            endTime++;
        }
        Set<Long> resultIDs = new HashSet<>();

        dbReadLock();
        final String query = "select event_id from events where time >=  " + startTime + " and time <" + endTime + " and " + getSQLWhere(filter); // NON-NLS
        //System.out.println(query);
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                resultIDs.add(rs.getLong(EVENT_ID_COLUMN));
            }

        } catch (SQLException sqlEx) {
            LOGGER.log(Level.SEVERE, "failed to execute query for event ids in range", sqlEx); // NON-NLS
        } finally {
            dbReadUnlock();
        }

        return resultIDs;
    }

    long getLastArtfactID() {
        return getDBInfo(LAST_ARTIFACT_ID_KEY, -1);
    }

    long getLastObjID() {
        return getDBInfo(LAST_OBJECT_ID_KEY, -1);
    }

    /** @return maximum time in seconds from unix epoch */
    Long getMaxTime() {
        dbReadLock();
        try (ResultSet rs = getMaxTimeStmt.executeQuery()) {
            while (rs.next()) {
                return rs.getLong("max"); // NON-NLS
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get MAX time.", ex); // NON-NLS
        } finally {
            dbReadUnlock();
        }
        return -1l;
    }

    /** @return maximum time in seconds from unix epoch */
    Long getMinTime() {
        dbReadLock();
        try (ResultSet rs = getMinTimeStmt.executeQuery()) {
            while (rs.next()) {
                return rs.getLong("min"); // NON-NLS
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get MIN time.", ex); // NON-NLS
        } finally {
            dbReadUnlock();
        }
        return -1l;
    }

    boolean getWasIngestRunning() {
        return getDBInfo(WAS_INGEST_RUNNING_KEY, 0) != 0;
    }

    /**
     * create the table and indices if they don't already exist
     *
     *
     * @return the number of rows in the table , count > 0 indicating an
     *         existing table
     */
    final synchronized void initializeDB() {
        try {
            if (isClosed()) {
                openDBCon();
            }
            configureDB();

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "problem accessing  database", ex); // NON-NLS
        }

        dbWriteLock();
        try {
            try (Statement stmt = con.createStatement()) {
                String sql = "CREATE TABLE if not exists db_info " // NON-NLS
                        + " ( key TEXT, " // NON-NLS
                        + " value INTEGER, " // NON-NLS
                        + "PRIMARY KEY (key))"; // NON-NLS
                stmt.execute(sql);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "problem creating  db_info table", ex); // NON-NLS
            }

            try (Statement stmt = con.createStatement()) {
                String sql = "CREATE TABLE if not exists events " // NON-NLS
                        + " (event_id INTEGER PRIMARY KEY, " // NON-NLS
                        + " file_id INTEGER, " // NON-NLS
                        + " artifact_id INTEGER, " // NON-NLS
                        + " time INTEGER, " // NON-NLS
                        + " sub_type INTEGER, " // NON-NLS
                        + " base_type INTEGER, " // NON-NLS
                        + " full_description TEXT, " // NON-NLS
                        + " med_description TEXT, " // NON-NLS
                        + " short_description TEXT, " // NON-NLS
                        + " known_state INTEGER)"; // NON-NLS
                stmt.execute(sql);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "problem creating  database table", ex); // NON-NLS
            }

            try (Statement stmt = con.createStatement()) {
                String sql = "CREATE INDEX if not exists file_idx ON events(file_id)"; // NON-NLS
                stmt.execute(sql);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "problem creating file_idx", ex); // NON-NLS
            }
            try (Statement stmt = con.createStatement()) {
                String sql = "CREATE INDEX if not exists artifact_idx ON events(artifact_id)"; // NON-NLS
                stmt.execute(sql);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "problem creating artifact_idx", ex); // NON-NLS
            }

            //for common queries the covering indexes below were better, but having the time index 'blocke' them
//            try (Statement stmt = con.createStatement()) {
//                String sql = "CREATE INDEX if not exists time_idx ON events(time)";
//                stmt.execute(sql);
//            } catch (SQLException ex) {
//                LOGGER.log(Level.SEVERE, "problem creating time_idx", ex);
//            }
            try (Statement stmt = con.createStatement()) {
                String sql = "CREATE INDEX if not exists sub_type_idx ON events(sub_type, time)"; // NON-NLS
                stmt.execute(sql);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "problem creating sub_type_idx", ex); // NON-NLS
            }

            try (Statement stmt = con.createStatement()) {
                String sql = "CREATE INDEX if not exists base_type_idx ON events(base_type, time)"; // NON-NLS
                stmt.execute(sql);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "problem creating base_type_idx", ex); // NON-NLS
            }

            try (Statement stmt = con.createStatement()) {
                String sql = "CREATE INDEX if not exists known_idx ON events(known_state)"; // NON-NLS
                stmt.execute(sql);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "problem creating known_idx", ex); // NON-NLS
            }

            try {
                insertRowStmt = prepareStatement(
                        "INSERT INTO events (file_id ,artifact_id, time, sub_type, base_type, full_description, med_description, short_description, known_state) " // NON-NLS
                        + "VALUES (?,?,?,?,?,?,?,?,?)"); // NON-NLS

                getMaxTimeStmt = prepareStatement("select Max(time) as max from events"); // NON-NLS
                getMinTimeStmt = prepareStatement("select Min(time) as min from events"); // NON-NLS
                getEventByIDStmt = prepareStatement("select * from events where event_id =  ?"); // NON-NLS
                recordDBInfoStmt = prepareStatement("insert or replace into db_info (key, value) values (?, ?)"); // NON-NLS
                getDBInfoStmt = prepareStatement("select value from db_info where key = ?"); // NON-NLS
            } catch (SQLException sQLException) {
                LOGGER.log(Level.SEVERE, "failed to prepareStatment", sQLException); // NON-NLS
            }

        } finally {
            dbWriteUnlock();
        }

    }

    void insertEvent(long time, EventType type, Long objID, Long artifactID, String fullDescription, String medDescription, String shortDescription, TskData.FileKnown known) {
        EventTransaction trans = beginTransaction();
        insertEvent(time, type, objID, artifactID, fullDescription, medDescription, shortDescription, known, trans);
        commitTransaction(trans, true);
    }

    /**
     * use transactions to update files
     *
     * @param f
     * @param tr
     */
    void insertEvent(long time, EventType type, Long objID, Long artifactID, String fullDescription, String medDescription, String shortDescription, TskData.FileKnown known, EventTransaction tr) {
        if (tr.isClosed()) {
            throw new IllegalArgumentException("can't update database with closed transaction"); // NON-NLS
        }
        int typeNum;
        int superTypeNum;

        typeNum = RootEventType.allTypes.indexOf(type);
        superTypeNum = type.getSuperType().ordinal();

        dbWriteLock();
        try {

            //"INSERT INTO events (file_id ,artifact_id, time, sub_type, base_type, full_description, med_description, short_description) "
            insertRowStmt.clearParameters();
            if (objID != null) {
                insertRowStmt.setLong(1, objID);
            } else {
                insertRowStmt.setNull(1, Types.INTEGER);
            }
            if (artifactID != null) {
                insertRowStmt.setLong(2, artifactID);
            } else {
                insertRowStmt.setNull(2, Types.INTEGER);
            }
            insertRowStmt.setLong(3, time);

            if (typeNum != -1) {
                insertRowStmt.setInt(4, typeNum);
            } else {
                insertRowStmt.setNull(4, Types.INTEGER);
            }

            insertRowStmt.setInt(5, superTypeNum);
            insertRowStmt.setString(6, fullDescription);
            insertRowStmt.setString(7, medDescription);
            insertRowStmt.setString(8, shortDescription);

            insertRowStmt.setByte(9, known == null ? TskData.FileKnown.UNKNOWN.getFileKnownValue() : known.getFileKnownValue());

            insertRowStmt.executeUpdate();

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "failed to insert event", ex); // NON-NLS
        } finally {
            dbWriteUnlock();
        }
    }

    boolean isClosed() throws SQLException {
        if (con == null) {
            return true;
        }
        return con.isClosed();
    }

    void openDBCon() {
        try {
            if (con == null || con.isClosed()) {
                con = DriverManager.getConnection("jdbc:sqlite:" + dbPath); // NON-NLS
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to open connection to events.db", ex); // NON-NLS
        }
    }

    void recordLastArtifactID(long lastArtfID) {
        recordDBInfo(LAST_ARTIFACT_ID_KEY, lastArtfID);
    }

    void recordLastObjID(Long lastObjID) {
        recordDBInfo(LAST_OBJECT_ID_KEY, lastObjID);
    }

    void recordWasIngestRunning(boolean wasIngestRunning) {
        recordDBInfo(WAS_INGEST_RUNNING_KEY, (wasIngestRunning ? 1 : 0));
    }

    void rollBackTransaction(EventTransaction trans) {
        trans.rollback();
    }

    boolean tableExists() {
        //TODO: use prepared statement - jm
        try (Statement createStatement = con.createStatement();
             ResultSet executeQuery = createStatement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='events'")) { // NON-NLS
            if (executeQuery.getString("name").equals("events") == false) { // NON-NLS
                return false;
            }
        } catch (SQLException ex) {
            Exceptions.printStackTrace(ex);
        }
        return true;
    }

    private void closeStatements() throws SQLException {
        for (PreparedStatement pStmt : preparedStatements) {
            pStmt.close();
        }
    }

    private void configureDB() throws SQLException {
        dbWriteLock();
        //this should match Sleuthkit db setupt
        try (Statement statement = con.createStatement()) {
            //reduce i/o operations, we have no OS crash recovery anyway
            statement.execute("PRAGMA synchronous = OFF;"); // NON-NLS
            //we don't use this feature, so turn it off for minimal speed up on queries
            //this is deprecated and not recomended
            statement.execute("PRAGMA count_changes = OFF;"); // NON-NLS
            //this made a big difference to query speed
            statement.execute("PRAGMA temp_store = MEMORY"); // NON-NLS
            //this made a modest improvement in query speeds
            statement.execute("PRAGMA cache_size = 50000"); // NON-NLS
            //we never delete anything so...
            statement.execute("PRAGMA auto_vacuum = 0"); // NON-NLS
            //allow to query while in transaction - no need read locks
            statement.execute("PRAGMA read_uncommitted = True;"); // NON-NLS
        } finally {
            dbWriteUnlock();
        }

        try {
            LOGGER.log(Level.INFO, String.format("sqlite-jdbc version %s loaded in %s mode", // NON-NLS
                                                 SQLiteJDBCLoader.getVersion(), SQLiteJDBCLoader.isNativeMode()
                                                                                ? "native" : "pure-java")); // NON-NLS
        } catch (Exception exception) {
        }
    }

    private TimeLineEvent constructTimeLineEvent(ResultSet rs) throws SQLException {
        EventType type = RootEventType.allTypes.get(rs.getInt(SUB_TYPE_COLUMN));
        return new TimeLineEvent(rs.getLong(EVENT_ID_COLUMN),
                                 rs.getLong(FILE_ID_COLUMN),
                                 rs.getLong(ARTIFACT_ID_COLUMN),
                                 rs.getLong(TIME_COLUMN),
                                 type,
                                 rs.getString(FULL_DESCRIPTION_COLUMN),
                                 rs.getString(MED_DESCRIPTION_COLUMN),
                                 rs.getString(SHORT_DESCRIPTION_COLUMN),
                                 TskData.FileKnown.valueOf(rs.getByte(KNOWN_COLUMN)));
    }

    /**
     * count all the events with the given options and return a map organizing
     * the counts in a hierarchy from date > eventtype> count
     *
     *
     * @param startTime events before this time will be excluded (seconds from
     *                  unix epoch)
     * @param endTime   events at or after this time will be excluded (seconds
     *                  from unix epoch)
     * @param filter    only events that pass this filter will be counted
     * @param zoomLevel only events of this type or a subtype will be counted
     *                  and the counts will be organized into bins for each of the subtypes of
     *                  the given event type
     *
     * @return a map organizing the counts in a hierarchy from date > eventtype>
     *         count
     */
    private Map<EventType, Long> countEvents(Long startTime, Long endTime, Filter filter, EventTypeZoomLevel zoomLevel) {
        if (Objects.equals(startTime, endTime)) {
            endTime++;
        }

        Map<EventType, Long> typeMap = new HashMap<>();

        //do we want the root or subtype column of the databse
        final boolean useSubTypes = (zoomLevel == EventTypeZoomLevel.SUB_TYPE);

        //get some info about the range of dates requested
        final String queryString = "select count(*), " + (useSubTypes ? SUB_TYPE_COLUMN : BASE_TYPE_COLUMN) // NON-NLS
                + " from events where time >= " + startTime + " and time < " + endTime + " and " + getSQLWhere(filter) // NON-NLS
                + " GROUP BY " + (useSubTypes ? SUB_TYPE_COLUMN : BASE_TYPE_COLUMN); // NON-NLS

        ResultSet rs = null;
        dbReadLock();
        //System.out.println(queryString);
        try (Statement stmt = con.createStatement();) {
            Stopwatch stopwatch = new Stopwatch();
            stopwatch.start();
            rs = stmt.executeQuery(queryString);
            stopwatch.stop();
            // System.out.println(stopwatch.elapsedMillis() / 1000.0 + " seconds");
            while (rs.next()) {

                EventType type = useSubTypes
                                 ? RootEventType.allTypes.get(rs.getInt(SUB_TYPE_COLUMN))
                                 : BaseTypes.values()[rs.getInt(BASE_TYPE_COLUMN)];

                typeMap.put(type, rs.getLong("count(*)")); // NON-NLS
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "error getting count of events from db.", ex); // NON-NLS
        } finally {
            try {
                rs.close();
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
            dbReadUnlock();
        }
        return typeMap;
    }

    /**
     * //TODO: update javadoc //TODO: split this into helper methods
     *
     * get a list of {@link AggregateEvent}s.
     *
     * General algorithm is as follows:
     *
     * - get all aggregate events, via one db query.
     * - sort them into a map from (type, description)-> aggevent
     * - for each key in map, merge the events and accumulate them in a list
     * to return
     *
     *
     * @param timeRange the Interval within in which all returned aggregate
     *                  events will be.
     * @param filter    only events that pass the filter will be included in
     *                  aggregates events returned
     * @param zoomLevel only events of this level will be included
     * @param lod       description level of detail to use when grouping events
     *
     *
     * @return a list of aggregate events within the given timerange, that pass
     *         the supplied filter, aggregated according to the given event type and
     *         description zoom levels
     */
    private List<AggregateEvent> getAggregatedEvents(Interval timeRange, Filter filter, EventTypeZoomLevel zoomLevel, DescriptionLOD lod) {
        String descriptionColumn = getDescriptionColumn(lod);
        final boolean useSubTypes = (zoomLevel.equals(EventTypeZoomLevel.SUB_TYPE));

        //get some info about the time range requested
        RangeDivisionInfo rangeInfo = RangeDivisionInfo.getRangeDivisionInfo(timeRange);
        //use 'rounded out' range
        long start = timeRange.getStartMillis() / 1000;//.getLowerBound();
        long end = timeRange.getEndMillis() / 1000;//Millis();//rangeInfo.getUpperBound();
        if (Objects.equals(start, end)) {
            end++;
        }

        //get a sqlite srtftime format string
        String strfTimeFormat = getStrfTimeFormat(rangeInfo.getPeriodSize());

        //effectively map from type to (map from description to events)
        Map<EventType, SetMultimap< String, AggregateEvent>> typeMap = new HashMap<>();

        //get all agregate events in this time unit
        dbReadLock();
        String query = "select strftime('" + strfTimeFormat + "',time , 'unixepoch'" + (TimeLineController.getTimeZone().get().equals(TimeZone.getDefault()) ? ", 'localtime'" : "") + ") as interval,  group_concat(event_id) as event_ids, Min(time), Max(time),  " + descriptionColumn + ", " + (useSubTypes ? SUB_TYPE_COLUMN : BASE_TYPE_COLUMN) // NON-NLS
                + " from events where time >= " + start + " and time < " + end + " and " + getSQLWhere(filter) // NON-NLS
                + " group by interval, " + (useSubTypes ? SUB_TYPE_COLUMN : BASE_TYPE_COLUMN) + " , " + descriptionColumn // NON-NLS
                + " order by Min(time)"; // NON-NLS
        //System.out.println(query);
        ResultSet rs = null;
        try (Statement stmt = con.createStatement(); // scoop up requested events in groups organized by interval, type, and desription
                ) {

            Stopwatch stopwatch = new Stopwatch();
            stopwatch.start();

            rs = stmt.executeQuery(query);
            stopwatch.stop();
            //System.out.println(stopwatch.elapsedMillis() / 1000.0 + " seconds");
            while (rs.next()) {
                EventType type = useSubTypes ? RootEventType.allTypes.get(rs.getInt(SUB_TYPE_COLUMN)) : BaseTypes.values()[rs.getInt(BASE_TYPE_COLUMN)];

                AggregateEvent aggregateEvent = new AggregateEvent(
                        new Interval(rs.getLong("Min(time)") * 1000, rs.getLong("Max(time)") * 1000, TimeLineController.getJodaTimeZone()), // NON-NLS
                        type,
                        Arrays.asList(rs.getString("event_ids").split(",")), // NON-NLS
                        rs.getString(descriptionColumn), lod);

                //put events in map from type/descrition -> event
                SetMultimap<String, AggregateEvent> descrMap = typeMap.get(type);
                if (descrMap == null) {
                    descrMap = HashMultimap.<String, AggregateEvent>create();
                    typeMap.put(type, descrMap);
                }
                descrMap.put(aggregateEvent.getDescription(), aggregateEvent);
            }

        } catch (SQLException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            try {
                rs.close();
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
            dbReadUnlock();
        }

        //result list to return
        ArrayList<AggregateEvent> aggEvents = new ArrayList<>();

        //save this for use when comparing gap size
        Period timeUnitLength = rangeInfo.getPeriodSize().getPeriod();

        //For each (type, description) key, merge agg events
        for (SetMultimap<String, AggregateEvent> descrMap : typeMap.values()) {
            for (String descr : descrMap.keySet()) {
                //run through the sorted events, merging together adjacent events
                Iterator<AggregateEvent> iterator = descrMap.get(descr).stream()
                        .sorted((AggregateEvent o1, AggregateEvent o2)
                                -> Long.compare(o1.getSpan().getStartMillis(), o2.getSpan().getStartMillis()))
                        .iterator();
                AggregateEvent current = iterator.next();
                while (iterator.hasNext()) {
                    AggregateEvent next = iterator.next();
                    Interval gap = current.getSpan().gap(next.getSpan());

                    //if they overlap or gap is less one quarter timeUnitLength
                    //TODO: 1/4 factor is arbitrary. review! -jm
                    if (gap == null || gap.toDuration().getMillis() <= timeUnitLength.toDurationFrom(gap.getStart()).getMillis() / 4) {
                        //merge them
                        current = AggregateEvent.merge(current, next);
                    } else {
                        //done merging into current, set next as new current
                        aggEvents.add(current);
                        current = next;
                    }
                }
                aggEvents.add(current);
            }
        }

        //at this point we should have a list of aggregate events.
        //one per type/description spanning consecutive time units as determined in rangeInfo
        return aggEvents;
    }

    private long getDBInfo(String key, long defaultValue) {
        dbReadLock();
        try {
            getDBInfoStmt.setString(1, key);

            try (ResultSet rs = getDBInfoStmt.executeQuery()) {
                long result = defaultValue;
                while (rs.next()) {
                    result = rs.getLong("value"); // NON-NLS
                }
                return result;
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "failed to read key: " + key + " from db_info", ex); // NON-NLS
            } finally {
                dbReadUnlock();
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "failed to set key: " + key + " on getDBInfoStmt ", ex); // NON-NLS
        }

        return defaultValue;
    }

    private String getDescriptionColumn(DescriptionLOD lod) {
        switch (lod) {
            case FULL:
                return FULL_DESCRIPTION_COLUMN;
            case MEDIUM:
                return MED_DESCRIPTION_COLUMN;
            case SHORT:
            default:
                return SHORT_DESCRIPTION_COLUMN;
        }
    }

    private String getStrfTimeFormat(TimeUnits info) {
        switch (info) {
            case DAYS:
                return "%Y-%m-%dT00:00:00"; // NON-NLS
            case HOURS:
                return "%Y-%m-%dT%H:00:00"; // NON-NLS
            case MINUTES:
                return "%Y-%m-%dT%H:%M:00"; // NON-NLS
            case MONTHS:
                return "%Y-%m-01T00:00:00"; // NON-NLS
            case SECONDS:
                return "%Y-%m-%dT%H:%M:%S"; // NON-NLS
            case YEARS:
                return "%Y-01-01T00:00:00"; // NON-NLS
            default:
                return "%Y-%m-%dT%H:%M:%S"; // NON-NLS
        }
    }

    private PreparedStatement prepareStatement(String queryString) throws SQLException {
        PreparedStatement prepareStatement = con.prepareStatement(queryString);
        preparedStatements.add(prepareStatement);
        return prepareStatement;
    }

    private void recordDBInfo(String key, long value) {
        dbWriteLock();
        try {
            recordDBInfoStmt.setString(1, key);
            recordDBInfoStmt.setLong(2, value);
            recordDBInfoStmt.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "failed to set dbinfo  key: " + key + " value: " + value, ex); // NON-NLS
        } finally {
            dbWriteUnlock();
        }
    }

    /**
     * inner class that can reference access database connection
     */
    public class EventTransaction {

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
        private EventTransaction() {

            //get the write lock, released in close()
            dbWriteLock();
            try {
                con.setAutoCommit(false);

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "failed to set auto-commit to to false", ex); // NON-NLS
            }

        }

        private void rollback() {
            if (!closed) {
                try {
                    con.rollback();

                } catch (SQLException ex1) {
                    LOGGER.log(Level.SEVERE, "Exception while attempting to rollback!!", ex1); // NON-NLS
                } finally {
                    close();
                }
            }
        }

        private void commit(Boolean notify) {
            if (!closed) {
                try {
                    con.commit();
                    // make sure we close before we update, bc they'll need locks
                    close();

                    if (notify) {
//                        fireNewEvents(newEvents);
                    }
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error commiting events.db.", ex); // NON-NLS
                    rollback();
                }
            }
        }

        private void close() {
            if (!closed) {
                try {
                    con.setAutoCommit(true);
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error setting auto-commit to true.", ex); // NON-NLS
                } finally {
                    closed = true;

                    dbWriteUnlock();
                }
            }
        }

        public Boolean isClosed() {
            return closed;
        }
    }

    public class MultipleTransactionException extends IllegalStateException {

        private static final String CANNOT_HAVE_MORE_THAN_ONE_OPEN_TRANSACTION = "cannot have more than one open transaction"; // NON-NLS

        public MultipleTransactionException() {
            super(CANNOT_HAVE_MORE_THAN_ONE_OPEN_TRANSACTION);
        }

    }
}
