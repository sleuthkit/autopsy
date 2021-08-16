/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.eventlog;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import javax.sql.DataSource;

/**
 * Captures auto ingest events like start of ingest job or completion.
 */
public class EventLogManager {

    private static final String DB_NAME = "AutoIngestEventLog";
    private static final String PG_JDBC_BASE_URI = "jdbc:postgresql://";
    private static final String PG_JDBC_DRIVER = "org.postgresql.Driver";

    private static EventLogManager instance;

    
    /**
     * Returns singleton instance of the event log manager for auto ingest.
     *
     * @return The singleton instance.
     *
     * @throws UserPreferencesException
     */
    public static EventLogManager getInstance() throws UserPreferencesException, PropertyVetoException, ClassNotFoundException, SQLException {
        if (instance == null) {
            CaseDbConnectionInfo connectionInfo = UserPreferences.getDatabaseConnectionInfo();
            String host = connectionInfo.getHost();
            String userName = connectionInfo.getUserName();
            String password = connectionInfo.getPassword();
            String port = connectionInfo.getPort();

            try (Connection pgConn = getPgConnection(host, port, userName, password, Optional.empty())) {
                if (!verifyDatabaseExists(pgConn)) {
                    if (!createDatabase(pgConn, DB_NAME, userName)) {
                        throw new SQLException("Unable to create EventLogManager database: " + DB_NAME);
                    }
                }
            }

            DataSource dataSource = getDataSource(host, port, userName, password, DB_NAME);
            try (Connection dbConn = dataSource.getConnection()) {
                if (!createDbSchema(dbConn)) {
                    throw new SQLException("Unable to create schema for: " + DB_NAME);
                }
            }

            instance = new EventLogManager(dataSource);
        }

        return instance;
    }

    private static ComboPooledDataSource getDataSource(String host, String port, String userName, String password, String dbName) throws PropertyVetoException {
        ComboPooledDataSource cpds = new ComboPooledDataSource();
        cpds.setJdbcUrl(getPgConnectionString(host, port, Optional.of(dbName)));
        cpds.setUser(userName);
        cpds.setPassword(password);
        return cpds;
    }

    private static String getPgConnectionString(String host, String port, Optional<String> dbName) {
        return PG_JDBC_BASE_URI + host + ":" + port + "/" + dbName.orElse("postgres");
    }

    private static Connection getPgConnection(String host, String port, String userName, String password, Optional<String> dbName)
            throws ClassNotFoundException, SQLException {

        String url = getPgConnectionString(host, port, dbName);

        Properties props = new Properties();
        props.setProperty("user", userName);
        props.setProperty("password", password);

        Class.forName(PG_JDBC_DRIVER);
        return DriverManager.getConnection(url, props);
    }

    private static boolean verifyDatabaseExists(Connection conn) throws SQLException, ClassNotFoundException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) = lower(?) LIMIT 1")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean createDatabase(Connection conn, String dbName, String userName) throws ClassNotFoundException, SQLException {
        String sql = "CREATE DATABASE %s OWNER %s"; // NON-NLS
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(sql, dbName, userName));
        }

        return true;
    }

    private static boolean createDbSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            stmt.execute("CREATE TABLE IF NOT EXISTS cases("
                    + "case_id SERIAL PRIMARY KEY, "
                    + "name TEXT "
                    + ")");

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ON cases(name)");

            stmt.execute("CREATE TABLE IF NOT EXISTS jobs ("
                    + "job_id SERIAL PRIMARY KEY, "
                    + "data_source_name TEXT, "
                    + "start_time TIMESTAMP WITHOUT TIME ZONE, "
                    + "end_time TIMESTAMP WITHOUT TIME ZONE, "
                    + "status SMALLINT, "
                    + "case_id INTEGER, "
                    + "FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE"
                    + ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS ON jobs(case_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS db_versions("
                    + "major_version INTEGER, "
                    + "minor_version INTEGER, "
                    + "revision INTEGER, "
                    + "creation_date TIMESTAMP WITHOUT TIME ZONE"
                    + ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS ON db_versions(major_version, minor_version, revision)");

            stmt.execute("INSERT INTO db_versions(major_version, minor_version, revision, creation_date) "
                    + "VALUES(1, 0, 0, NOW()) ON CONFLICT DO NOTHING");

            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        }

        return true;
    }

    private final DataSource dataSource;

    private EventLogManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private CaseRecord getCaseRecord(ResultSet rs) throws SQLException {
        return new CaseRecord(rs.getLong("case_id"), rs.getString("name"));
    }

    public CaseRecord getOrCreateCaseRecord(String caseName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement query = conn.prepareStatement("SELECT case_id, name FROM cases WHERE name = ?")) {
            conn.setAutoCommit(false);
            query.setString(1, caseName);

            try (ResultSet queryResults = query.executeQuery()) {
                if (queryResults.next()) {
                    CaseRecord record = getCaseRecord(queryResults);
                    conn.commit();
                    return record;
                }

                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO cases(name) VALUES (?) RETURNING case_id, name")) {
                    query.setString(1, caseName);

                    try (ResultSet insertResults = insert.executeQuery()) {
                        if (!queryResults.next()) {
                            throw new SQLException("There was an error inserting into cases table with name of " + caseName);
                        }

                        CaseRecord record = getCaseRecord(insertResults);
                        conn.commit();
                        return record;

                    }
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public JobRecord getOrCreateJobRecord(long caseId, String dataSourceName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement query = conn.prepareStatement("SELECT "
                        + "jobs.job_id, "
                        + "jobs.data_source_name, "
                        + "jobs.start_time, "
                        + "jobs.end_time, "
                        + "jobs.status, "
                        + "jobs.case_id, "
                        + "cases.name AS case_name "
                        + "FROM jobs INNER JOIN cases ON cases.case_id = jobs.case_id "
                        + "WHERE cases.case_id = ? AND jobs.data_source_name = ?")) {

            conn.setAutoCommit(false);
            query.setString(1, dataSourceName);
            query.setLong(2, caseId);

            try (ResultSet queryResults = query.executeQuery()) {
                if (queryResults.next()) {
                    JobRecord record = getJobRecord(queryResults);
                    conn.commit();
                    return record;
                }

                // taken from https://stackoverflow.com/a/49536257
                try (PreparedStatement insert = conn.prepareStatement("WITH inserted AS ("
                        + "INSERT INTO jobs(data_source_name, start_time, end_time, status, case_id) VALUES(?, NULL, NULL, ?, ?) "
                        + "RETURNING *) "
                        + "SELECT "
                        + "inserted.job_id, "
                        + "inserted.data_source_name, "
                        + "inserted.start_time, "
                        + "inserted.end_time, "
                        + "inserted.status, "
                        + "inserted.case_id, "
                        + "cases.name AS case_name "
                        + "FROM inserted INNER JOIN cases ON cases.case_id = inserted.case_id ")) {
                    query.setString(1, dataSourceName);
                    query.setInt(2, JobStatus.PENDING.getDbVal());
                    query.setLong(3, caseId);

                    try (ResultSet insertResults = insert.executeQuery()) {
                        if (!queryResults.next()) {
                            throw new SQLException(MessageFormat.format(
                                    "There was an error inserting into jobs table with case id: {0} and data source name: {1}",
                                    caseId, dataSourceName));
                        }

                        JobRecord record = getJobRecord(insertResults);
                        conn.commit();
                        return record;
                    }
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

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

        try (Connection conn = dataSource.getConnection();
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
        try (Connection conn = dataSource.getConnection();
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

    private JobRecord getJobRecord(ResultSet rs) throws SQLException {
        return new JobRecord(rs.getLong("job_id"),
                rs.getLong("case_id"),
                rs.getString("case_name"),
                rs.getString("data_source_name"),
                Optional.ofNullable(rs.getObject("start_time", Instant.class)),
                Optional.ofNullable(rs.getObject("end_time", Instant.class)),
                JobStatus.getFromDbVal(rs.getInt("status")).orElse(null));
    }

}
