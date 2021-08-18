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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.MessageFormat;
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

    private static final String DB_NAME = "event_log";
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
    public static EventLogManager getInstance() throws EventLogException {
        if (instance == null) {
            String dbName = DB_NAME;
            CaseDbConnectionInfo connectionInfo = getConnectionInfo();

            String host = connectionInfo.getHost();
            String userName = connectionInfo.getUserName();
            String password = connectionInfo.getPassword();
            String port = connectionInfo.getPort();

            verifyOrCreatePgDb(host, port, userName, password, dbName);

            ComboPooledDataSource dataSource = getDataSource(host, port, userName, password, dbName);

            verifyOrCreateSchema(dataSource, dbName);

            instance = new EventLogManager(dataSource);
        }

        return instance;
    }

    /**
     * Obtains the connection info from user preferences.
     *
     * @return The connection preferences.
     *
     * @throws EventLogException
     */
    static CaseDbConnectionInfo getConnectionInfo() throws EventLogException {
        try {
            return UserPreferences.getDatabaseConnectionInfo();
        } catch (UserPreferencesException ex) {
            throw new EventLogException("An error occurred while fetching multiuser settings.", ex);
        }
    }

    /**
     * Verifies that the proper schema exists in the postgres database or
     * creates it.
     *
     * @param dataSource The data source.
     * @param dbName     The database name for error reporting purposes.
     *
     * @throws EventLogException
     */
    static void verifyOrCreateSchema(DataSource dataSource, String dbName) throws EventLogException {
        try (final Connection dbConn = dataSource.getConnection()) {
            if (!createDbSchema(dbConn)) {
                throw new EventLogException("Unable to create schema for: " + dbName);
            }
        } catch (SQLException ex) {
            throw new EventLogException(MessageFormat.format(
                    "An error occurred while verifying that schema in database {0} was properly configured.", dbName),
                    ex);
        }
    }

    /**
     * Verifies that the postgres database exists in the specified server or
     * creates the database.
     *
     * @param host     The pg host.
     * @param port     The pg port.
     * @param userName The username to use to connect pg.
     * @param password The password to use to connect to pg.
     * @param dbName   The name of the pg database. If empty, the root
     *                 "postgres" database is used.
     *
     * @throws EventLogException
     */
    static void verifyOrCreatePgDb(String host, String port, String userName, String password, String dbName) throws EventLogException {
        try (Connection pgConn = getPgConnection(host, port, userName, password, Optional.empty())) {
            if (!verifyDatabaseExists(pgConn, dbName)) {
                if (!createDatabase(pgConn, dbName, userName)) {
                    throw new EventLogException("Unable to create EventLogManager database: " + dbName);
                }
            }
        } catch (SQLException | ClassNotFoundException ex) {
            throw new EventLogException(MessageFormat.format("An error occurred while verifying that postgres database {0} exists.", dbName), ex);
        }
    }

    /**
     * Returns a pooled c3po data source that can be used for connections.
     *
     * @param host     The pg host.
     * @param port     The pg port.
     * @param userName The username to use to connect pg.
     * @param password The password to use to connect to pg.
     * @param dbName   The name of the database to connect to.
     *
     * @return The pooled connection.
     */
    static ComboPooledDataSource getDataSource(String host, String port, String userName, String password, String dbName) {
        ComboPooledDataSource cpds = new ComboPooledDataSource();
        cpds.setJdbcUrl(getPgConnectionString(host, port, Optional.of(dbName)));
        cpds.setUser(userName);
        cpds.setPassword(password);
        return cpds;
    }

    /**
     * Creates a postgres jdbc url to use for pg connections.
     *
     * @param host   The pg hostname.
     * @param port   The pg port.
     * @param dbName The name of the pg database. If empty, the root "postgres"
     *               database is used.
     *
     * @return The jdbc url.
     */
    private static String getPgConnectionString(String host, String port, Optional<String> dbName) {
        return PG_JDBC_BASE_URI + host + ":" + port + "/" + dbName.orElse("postgres");
    }

    /**
     * Returns a standard db connection to the postgres database specified by
     * settings.
     *
     * @param host     The pg host.
     * @param port     The pg port.
     * @param userName The username to use to connect pg.
     * @param password The password to use to connect to pg.
     * @param dbName   The name of the pg database. If empty, the root
     *                 "postgres" database is used.
     *
     * @return
     *
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    static Connection getPgConnection(String host, String port, String userName, String password, Optional<String> dbName) throws ClassNotFoundException, SQLException {
        String url = getPgConnectionString(host, port, dbName);

        Properties props = new Properties();
        props.setProperty("user", userName);
        props.setProperty("password", password);

        Class.forName(PG_JDBC_DRIVER);
        return DriverManager.getConnection(url, props);
    }

    /**
     * Verify that the given postgres database exists.
     *
     * @param conn   The pg connection to the root 'postgres' database.
     * @param dbName The database name to verify exists.
     *
     * @return True if exists.
     *
     * @throws SQLException
     */
    static boolean verifyDatabaseExists(Connection conn, String dbName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) = lower(?) LIMIT 1")) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Creates a postgres database.
     *
     * @param conn     The root 'postgres' database connection.
     * @param dbName   The name of the database to create.
     * @param userName The owner of the database.
     *
     * @return True if successful.
     *
     * @throws SQLException
     */
    private static boolean createDatabase(Connection conn, String dbName, String userName) throws SQLException {
        String sql = "CREATE DATABASE %s OWNER %s"; // NON-NLS
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(sql, dbName, userName));
        }

        return true;
    }

    /**
     * Runs sql queries to instantiate the necessary schema if not already
     * created. This method can be run even if database has been previously
     * instantiated.
     *
     * @param conn The database connection.
     *
     * @return True if successful.
     *
     * @throws SQLException
     */
    private static boolean createDbSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            stmt.execute("CREATE TABLE IF NOT EXISTS cases(case_id SERIAL PRIMARY KEY, name TEXT)");

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS case_name_idx ON cases(name)");

            stmt.execute("CREATE TABLE IF NOT EXISTS jobs(\n"
                    + "	job_id SERIAL PRIMARY KEY, \n"
                    + "	data_source_name TEXT, \n"
                    + "	start_time TIMESTAMP WITHOUT TIME ZONE, \n"
                    + "	end_time TIMESTAMP WITHOUT TIME ZONE,\n"
                    + "	status SMALLINT,\n"
                    + "	case_id INTEGER,\n"
                    + "	FOREIGN KEY(case_id) REFERENCES cases(case_id) ON DELETE CASCADE\n"
                    + ")");

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS jobs_case_ds_idx ON jobs(case_id, data_source_name);");

            stmt.execute("CREATE TABLE IF NOT EXISTS db_versions(\n"
                    + "	major_version INTEGER, \n"
                    + "	minor_version INTEGER, \n"
                    + "	revision INTEGER, \n"
                    + "	creation_date TIMESTAMP WITHOUT TIME ZONE\n"
                    + ")");

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS db_versions_idx ON db_versions(major_version, minor_version, revision)");

            stmt.execute("INSERT INTO db_versions(major_version, minor_version, revision, creation_date) VALUES(1, 0, 0, NOW()) ON CONFLICT DO NOTHING");

            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        }

        return true;
    }

    private final ComboPooledDataSource dataSource;

    /**
     * Main constructor.
     *
     * @param dataSource The pooled data source connection to use. This assumes
     *                   that database and schema exist.
     */
    EventLogManager(ComboPooledDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Closes down connection pool.
     */
    void close() {
        this.dataSource.resetPoolManager(true);
    }

    /**
     * Creates a case record given the result set. Expects 'case_id' and 'name'.
     *
     * @param rs The result set.
     *
     * @return The case record.
     *
     * @throws SQLException
     */
    private CaseRecord getCaseRecord(ResultSet rs) throws SQLException {
        return new CaseRecord(rs.getLong("case_id"), rs.getString("name"));
    }

    /**
     * Returns the case record denoted by the case name or creates a new entry
     * and returns the record.
     *
     * @param caseName The unique name of the case.
     *
     * @return The case record in the event log of the given case name.
     *
     * @throws SQLException
     */
    public CaseRecord getOrCreateCaseRecord(String caseName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                // taken from https://stackoverflow.com/a/40325406
                PreparedStatement query = conn.prepareStatement("WITH ins AS (\n"
                        + "   INSERT INTO cases(name) VALUES(?)\n"
                        + "   ON CONFLICT DO NOTHING\n"
                        + "   RETURNING name, case_id\n"
                        + "   )\n"
                        + "SELECT name, case_id FROM ins\n"
                        + "UNION ALL\n"
                        + "SELECT name, case_id FROM cases WHERE name = ?")) {

            query.setString(1, caseName);
            query.setString(2, caseName);

            try (ResultSet queryResults = query.executeQuery()) {
                if (!queryResults.next()) {
                    throw new SQLException(MessageFormat.format(
                            "Expected a case to be created or previous to be returned, but received no results caseName: {0}.",
                            caseName));
                }
                return getCaseRecord(queryResults);
            }
        }
    }

    /**
     * Returns the job record denoted by the case id and data source or creates
     * a new entry.
     *
     * @param caseId         The case id in the event log.
     * @param dataSourceName The data source name (caseId and dataSourceName
     *                       must be unique)
     *
     * @return The found or created job record.
     *
     * @throws SQLException
     */
    public JobRecord getOrCreateJobRecord(long caseId, String dataSourceName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                // taken from https://stackoverflow.com/a/40325406
                PreparedStatement query = conn.prepareStatement("WITH ins AS (\n"
                        + "   INSERT INTO jobs(data_source_name, start_time, end_time, status, case_id) VALUES(?, NULL, NULL, ?, ?)\n"
                        + "   ON CONFLICT DO NOTHING\n"
                        + "   RETURNING *\n"
                        + "   )\n"
                        + "SELECT j.job_id, j.data_source_name, j.start_time, j.end_time, j.status, j.case_id, c.name AS case_name\n"
                        + "FROM (SELECT job_id, data_source_name, start_time, end_time, status, case_id FROM ins\n"
                        + "UNION ALL\n"
                        + "(SELECT job_id, data_source_name, start_time, end_time, status, case_id FROM jobs\n"
                        + "WHERE case_id = ? AND data_source_name = ?)) j\n"
                        + "INNER JOIN cases c ON c.case_id = j.case_id ")) {

            query.setString(1, dataSourceName);
            query.setInt(2, JobStatus.PENDING.getDbVal());
            query.setLong(3, caseId);
            query.setLong(4, caseId);
            query.setString(5, dataSourceName);

            try (ResultSet queryResults = query.executeQuery()) {
                if (!queryResults.next()) {
                    throw new SQLException(MessageFormat.format(
                            "Expected a job to be created or previous to be returned, but received no results caseId: {0}, dataSourceName: {1}.",
                            caseId, dataSourceName));
                }
                return getJobRecord(queryResults);
            }
        }
    }

    /**
     * Set job status for the given job.
     *
     * @param jobId     The job id.
     * @param newStatus The status to set for the job.
     * @param date      The timestamp for when.
     *
     * @return The job record if an update is made.
     *
     * @throws SQLException
     */
    public Optional<JobRecord> setJobStatus(long jobId, JobStatus newStatus, Date date) throws SQLException {

        String updateStr;
        switch (newStatus) {
            case RUNNING:
                updateStr = "UPDATE jobs SET status = ?, start_time = ? WHERE job_id = ?";
                break;
            case DONE:
                updateStr = "UPDATE jobs SET status = ?, end_time = ? WHERE job_id = ?";
                break;
            case PENDING:
            default:
                updateStr = "UPDATE jobs SET status = ? WHERE job_id = ?";
                break;
        }

        String fullClause = "WITH updated AS (" + updateStr + " RETURNING *) "
                + "SELECT "
                + "updated.job_id, "
                + "updated.data_source_name, "
                + "updated.start_time, "
                + "updated.end_time, "
                + "updated.status, "
                + "updated.case_id, "
                + "cases.name AS case_name "
                + "FROM updated INNER JOIN cases ON cases.case_id = updated.case_id ";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement updateStmt = conn.prepareStatement(fullClause)) {

            updateStmt.setInt(1, newStatus.getDbVal());

            switch (newStatus) {
                case RUNNING:
                case DONE:
                    updateStmt.setTimestamp(2, new Timestamp(date.getTime()));
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

    /**
     * Retrieves all the jobs of the given status.
     *
     * @param status The status.
     *
     * @return The list of job records.
     *
     * @throws SQLException
     */
    public List<JobRecord> getJobs(JobStatus status) throws SQLException {
        List<JobRecord> toReturn = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement query = conn.prepareStatement(
                        "SELECT "
                        + "jobs.job_id, "
                        + "jobs.data_source_name, "
                        + "jobs.start_time, "
                        + "jobs.end_time, "
                        + "jobs.status, "
                        + "jobs.case_id, "
                        + "cases.name AS case_name "
                        + "FROM jobs INNER JOIN cases ON cases.case_id = jobs.case_id "
                        + "WHERE jobs.status = ?")) {

            query.setInt(1, status.getDbVal());

            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    toReturn.add(getJobRecord(rs));
                }
            }
        }

        return toReturn;
    }

    /**
     * Returns a job record from the given result set.
     *
     * @param rs The result set. It is expected to have all columns of jobs as
     *           well as 'case_name' which is the name of the case signified by
     *           the 'case_id'.
     *
     * @return The record.
     *
     * @throws SQLException
     */
    private JobRecord getJobRecord(ResultSet rs) throws SQLException {
        return new JobRecord(rs.getLong("job_id"),
                rs.getLong("case_id"),
                rs.getString("case_name"),
                rs.getString("data_source_name"),
                Optional.ofNullable(rs.getTimestamp("start_time")),
                Optional.ofNullable(rs.getTimestamp("end_time")),
                JobStatus.getFromDbVal(rs.getInt("status")).orElse(null));
    }

}
