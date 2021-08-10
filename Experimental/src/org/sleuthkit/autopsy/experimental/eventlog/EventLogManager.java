/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.experimental.eventlog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.guiutils.StatusIconCellRenderer.Status;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;

/**
 *
 * @author gregd
 */
public class EventLogManager {

    private static final String DB_NAME = "AutoIngestEventLog";
    private static final String PG_JDBC_BASE_URI = "jdbc:postgresql://";
    private static final String PG_JDBC_DRIVER = "org.postgresql.Driver";

    private static EventLogManager instance;

    public static EventLogManager getInstance() throws UserPreferencesException {
        if (instance == null) {
            instance = new EventLogManager(UserPreferences.getDatabaseConnectionInfo());
            instance.initializeDb();
        }

        return instance;
    }

    private final CaseDbConnectionInfo connectionInfo;

    EventLogManager(CaseDbConnectionInfo connectionInfo) {
        this.connectionInfo = connectionInfo;
    }

    void initializeDb() {
        String host = connectionInfo.getHost();
        String userName = connectionInfo.getUserName();
        String password = connectionInfo.getPassword();
        String port = connectionInfo.getPort();

    }

    Connection getPgConnection(String host, String port, String userName, String password, Optional<String> dbName)
            throws ClassNotFoundException, SQLException {

        String url = PG_JDBC_BASE_URI + host + ":" + port + "/" + dbName.orElse("postgres");

        Properties props = new Properties();
        props.setProperty("user", userName);
        props.setProperty("password", password);

        Class.forName(PG_JDBC_DRIVER);
        return DriverManager.getConnection(url, props);
    }

    boolean verifyDatabaseExists(String host, String port, String userName, String password) throws SQLException, ClassNotFoundException {
        try (Connection conn = getPgConnection(host, port, userName, password, Optional.empty());
                PreparedStatement ps = conn.prepareStatement("SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) = lower(?) LIMIT 1")) {

            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
        }

        return false;
    }

    Connection getConnection() {

    }

    boolean createDatabase(String host, String port, String userName, String password) throws ClassNotFoundException, SQLException {
        String sql = "CREATE DATABASE %s OWNER %s"; // NON-NLS
        try (Connection conn = getPgConnection(host, port, userName, password, Optional.empty());
                Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(sql, DB_NAME, userName));
        }

        return true;
    }

    boolean createDbSchema(String host, String port, String userName, String password) throws ClassNotFoundException, SQLException {
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE cases("
                    + "case_id SERIAL PRIMARY KEY, "
                    + "name TEXT "
                    + ")");

            stmt.execute("CREATE UNIQUE INDEX ON cases(name)");

            stmt.execute("CREATE TABLE jobs ("
                    + "job_id SERIAL PRIMARY KEY, "
                    + "data_source_name TEXT, "
                    + "start_time TIMEZONE WITHOUT TIME ZONE, "
                    + "end_time TIMEZONE WITHOUT TIME ZONE, "
                    + "status SMALLINT, "
                    + "case_id INTEGER, "
                    + "FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE"
                    + ")");

            stmt.execute("CREATE INDEX ON jobs(case_id)");
            //stmt.execute("CREATE TABLE db_versions(major_version INTEGER, minor_version INTEGER, revision INTEGER)");
        }
        return true;
    }

    private CaseRecord getCaseRecord(ResultSet rs) throws SQLException {
        return new CaseRecord(rs.getLong("case_id"), rs.getString("name"));
    }

    public CaseRecord getOrCreateCaseRecord(String caseName) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement query = conn.prepareStatement("SELECT case_id, name FROM cases WHERE name = ?")) {
            conn.setAutoCommit(false);
            query.setString(0, caseName);

            try (ResultSet queryResults = query.executeQuery()) {
                if (queryResults.next()) {
                    CaseRecord record = getCaseRecord(queryResults);
                    conn.commit();
                    return record;
                }

                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO cases(name) VALUES (?) RETURNING case_id, name")) {
                    query.setString(0, caseName);

                    try (ResultSet insertResults = insert.executeQuery()) {
                        if (!queryResults.next()) {
                            conn.rollback();
                            throw new SQLException("There was an error inserting into cases table with name of " + caseName);
                        }

                        CaseRecord record = getCaseRecord(insertResults);
                        conn.commit();
                        return record;

                    }
                }

            }
        }
    }

// GVDTODO
//    public List<JobRecord> getJobRecord(long caseId, String dataSourceName) throws SQLException {
//        
//        /*
//                    stmt.execute("CREATE TABLE jobs ("
//                    + "job_id SERIAL PRIMARY KEY, "
//                    + "data_source_name TEXT, "
//                    + "start_time TIMEZONE WITHOUT TIME ZONE, "
//                    + "end_time TIMEZONE WITHOUT TIME ZONE, "
//                    + "status SMALLINT, "
//                    + "case_id INTEGER, "
//                    + "FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE"
//                    + ")");
//        
//        */
//        try (Connection conn = getConnection();
//                PreparedStatement query = conn.prepareStatement("SELECT job_id, data_source_name, start_time, end_time, status, case_id FROM jobs WHERE case_id = ? AND data_source_name = ?")) {
//            conn.setAutoCommit(false);
//            query.setString(0, caseId);
//
//            try (ResultSet queryResults = query.executeQuery()) {
//                if (queryResults.next()) {
//                    CaseRecord record = getCaseRecord(queryResults);
//                    conn.commit();
//                    return record;
//                }
//
//                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO cases(name) VALUES (?) RETURNING case_id, name")) {
//                    query.setString(0, caseName);
//
//                    try (ResultSet insertResults = insert.executeQuery()) {
//                        if (!queryResults.next()) {
//                            conn.rollback();
//                            throw new SQLException("There was an error inserting into cases table with name of " + caseName);
//                        }
//
//                        CaseRecord record = getCaseRecord(insertResults);
//                        conn.commit();
//                        return record;
//
//                    }
//                }
//
//            }
//        }
//    }
    public Optional<JobRecord> setJobStatus(long jobId, JobStatus newStatus, Date date) throws SQLException {
        String returningClause = " RETURNING job_id, data_source_name, start_time, end_time, status, case_id";
        String updateStr;
        switch (newStatus) {
            case RUNNING:
                updateStr = "UPDATE jobs SET status = ?, start_time = ? WHERE job_id = ? AND start_time = NULL" + returningClause;
                break;
            case DONE:
                updateStr = "UPDATE jobs SET status = ?, end_time = ? WHERE job_id = ? AND end_time = NULL" + returningClause;
                break;
            case PENDING:
            default:
                updateStr = "UPDATE jobs SET status = ? WHERE job_id = ? AND start_time = NULL AND end_time = NULL" + returningClause;
                break;
        }

        try (Connection conn = getConnection();
                PreparedStatement updateStmt = conn.prepareStatement(updateStr)) {

            updateStmt.setInt(1, newStatus.getDbVal());

            switch (newStatus) {
                case RUNNING:
                case DONE:
                    updateStmt.setDate(2, new java.sql.Date(date.getTime()));
                    updateStmt.setLong(3, jobId);
                    break;
                case PENDING:
                default:
                    updateStmt.setLong(2, jobId);
                    break;
            }

            try (ResultSet rs = updateStmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(getJobRecord(rs));
            }
        }
    }

    public List<JobRecord> getJobs(JobStatus status) throws SQLException {
        List<JobRecord> toReturn = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement query = conn.prepareStatement("SELECT job_id, data_source_name, start_time, end_time, status, case_id FROM jobs WHERE status = ?")) {

            query.setInt(1, status.getDbVal());

            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    toReturn.add(getJobRecord(rs));
                }
            }
        }
        
        return toReturn;
    }

    // may need case_name
    private JobRecord getJobRecord(ResultSet rs) throws SQLException {

        return new JobRecord(rs.getLong("job_id"), 
                rs.getLong("case_id"), 
                rs.getString("case_name"), 
                rs.getString("data_source_name"), 
                Optional.ofNullable(rs.getObject("start_time", Instant.class)),
                Optional.ofNullable(rs.getObject("end_time", Instant.class)), 
                JobStatus.getFromDbVal(rs.getInt("status")).orElse(null));
    }

    public static class CaseRecord {

        private final long id;
        private final String name;

        public CaseRecord(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class JobRecord {

        private final long id;
        private final long caseId;
        private final String caseName;
        private final String dataSourceName;
        private final Optional<Instant> startTime;
        private final Optional<Instant> endTime;
        private final JobStatus status;

        public JobRecord(long id, long caseId, String caseName, String dataSourceName, Optional<Instant> startTime, Optional<Instant> endTime, JobStatus status) {
            this.id = id;
            this.caseId = caseId;
            this.caseName = caseName;
            this.dataSourceName = dataSourceName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
        }

        public long getId() {
            return id;
        }

        public long getCaseId() {
            return caseId;
        }

        public String getCaseName() {
            return caseName;
        }

        public String getDataSourceName() {
            return dataSourceName;
        }

        public Optional<Instant> getStartTime() {
            return startTime;
        }

        public Optional<Instant> getEndTime() {
            return endTime;
        }

        public JobStatus getStatus() {
            return status;
        }
    }

    public enum JobStatus {
        PENDING(0), RUNNING(1), DONE(2);
        private int dbVal;

        JobStatus(int dbVal) {
            this.dbVal = dbVal;
        }

        int getDbVal() {
            return dbVal;
        }

        public static Optional<JobStatus> getFromDbVal(Integer dbVal) {
            if (dbVal == null) {
                return Optional.empty();
            }

            return Stream.of(JobStatus.values())
                    .filter(s -> s.getDbVal() == dbVal)
                    .findFirst();
        }
    }
}
