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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * Runs tests against the pg database specified in settings to test the
 * EventLogManager.
 */
public class EventLogManagerTests {

    /**
     * The tables in the database.
     */
    private static final List<String> ALL_TABLES = Arrays.asList(
            "cases",
            "jobs",
            "db_versions"
    );

    /**
     * Throws an exception with the given error.
     *
     * @param message The error message with MessageFormat formatting.
     * @param args    The additional args to format in with MessageFormat.
     *
     * @throws IllegalStateException
     */
    private static void onErr(String message, Object... args) throws IllegalStateException {
        throw new IllegalStateException(MessageFormat.format(message, args));
    }

    /**
     * Throws an exception if not true.
     *
     * @param passesOnTrue Must pass or exception is thrown.
     * @param message      The error message with MessageFormat formatting.
     * @param args         The additional args to format in with MessageFormat.
     *
     * @throws IllegalStateException
     */
    private static void assertTrue(boolean passesOnTrue, String message, Object... args) throws IllegalStateException {
        if (!passesOnTrue) {
            onErr(message, args);
        }
    }

    /**
     * Returns a formatted string listing all objects.
     *
     * @param records The records list to display as string.
     *
     * @return The formatted string.
     */
    private static String getStr(List<? extends Object> records) {
        return "[ " + records.stream().map(r -> r.toString()).collect(Collectors.joining(", ")) + " ]";
    }

    /**
     * Converts a result set row to an object of a type.
     *
     * @param <T>
     */
    private interface RsConverter<T> {

        /**
         * Converts a result set row to an object of a type.
         *
         * @param rs The result set.
         *
         * @return The type to convert to.
         *
         * @throws SQLException
         */
        T convert(ResultSet rs) throws SQLException;
    }

    /**
     * Converts a query on the data source to a list of objects.
     *
     * @param <T>
     * @param ds        The data source to provide connection.
     * @param query     The query string.
     * @param converter The means to convert to an object.
     *
     * @return The list of objects.
     *
     * @throws SQLException
     */
    private static <T> List<T> getList(DataSource ds, String query, RsConverter<T> converter) throws SQLException {
        try (Connection conn = ds.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            List<T> toRet = new ArrayList<>();
            while (rs.next()) {
                toRet.add(converter.convert(rs));
            }
            return toRet;
        }
    }

    /**
     * Runs all tests.
     *
     * @param host     The pg host.
     * @param port     The pg port.
     * @param userName The pg user name.
     * @param pword    The pg password.
     * @param dbName   The pg database.
     *
     * @throws ClassNotFoundException
     * @throws SQLException
     * @throws EventLogException
     */
    public void runTests(String host, String port, String userName, String pword, String dbName) throws ClassNotFoundException, SQLException, EventLogException {
        DataSource testDs = verifyDbAndSchemaTest(host, port, userName, pword, dbName);

        EventLogManager manager = new EventLogManager(testDs);

        List<CaseRecord> caseRecords = createCasesTest(manager, testDs);

        createRecordsUpdateStatusTest(caseRecords, manager);

        // drop db
        try (Connection conn = EventLogManager.getPgConnection(host, port, userName, pword, Optional.empty());
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP DATABASE " + dbName);
        }
    }

    /**
     * Creates case records with the EventLogManager, verifies their creation,
     * and returns the created cases.
     *
     * @param manager The manager.
     * @param testDs  The test data source in order to verify tables independent
     *                of the event log manager.
     *
     * @return The created cases.
     *
     * @throws SQLException
     * @throws IllegalStateException
     */
    private List<CaseRecord> createCasesTest(EventLogManager manager, DataSource testDs) throws SQLException, IllegalStateException {
        String case1Str = "Case_1";
        String case2Str = "Case_2";
        CaseRecord case1 = manager.getOrCreateCaseRecord(case1Str);
        manager.getOrCreateCaseRecord(case2Str);
        List<CaseRecord> caseRecords = getList(testDs, "SELECT case_id, name FROM cases",
                (rs) -> new CaseRecord(rs.getLong("case_id"), rs.getString("name")))
                .stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
        assertTrue(caseRecords.size() == 2, "Expected 2 cases created; received {0} instead", getStr(caseRecords));
        assertTrue(caseRecords.get(0).getName().equals(case1Str), "Expected first case to be {0} but was {1}", case1Str, caseRecords.get(0).getName());
        assertTrue(caseRecords.get(1).getName().equals(case2Str), "Expected first case to be {0} but was {1}", case2Str, caseRecords.get(1).getName());
        // verify repeat case returns previous
        CaseRecord repeatCase = manager.getOrCreateCaseRecord(case1Str);
        assertTrue(case1.getId() == repeatCase.getId(), "Expected repeat case (id: {0}) to have same id as case1 (id: {1})", repeatCase.getId(), case1.getId());
        return caseRecords;
    }

    /**
     * Tests that the database is not initially present so data is not
     * overwritten, creates the database and schema with the event log manager.
     *
     * @param host     The pg host.
     * @param port     The pg port.
     * @param userName The pg user name.
     * @param pword    The pg password.
     * @param dbName   The pg database.
     *
     * @return The data source to use for connections to the created database.
     *
     * @throws EventLogException
     * @throws IllegalStateException
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private DataSource verifyDbAndSchemaTest(String host, String port, String userName, String pword, String dbName) throws EventLogException, IllegalStateException, SQLException, ClassNotFoundException {
        //CaseDbConnectionInfo conn = EventLogManager.getConnectionInfo();

        // if db exists throw (shouldn't exist on start)
        try (Connection conn = EventLogManager.getPgConnection(host, port, userName, pword, Optional.empty())) {
            if (EventLogManager.verifyDatabaseExists(conn, dbName)) {
                onErr("Database '{0}' shouldn't exist when running tests.  "
                        + "Please drop the database and try again.", dbName);
            }

            // verify or create database (check externally)
            EventLogManager.verifyOrCreatePgDb(host, port, userName, pword, dbName);

            if (!EventLogManager.verifyDatabaseExists(conn, dbName)) {
                onErr("Unable to create database '{0}'", dbName);
            }
        }
        DataSource testDs = EventLogManager.getDataSource(host, port, userName, pword, dbName);
        // verify or create schema (check externally) and verify that the schema isn't changed.
        for (int i = 0; i < 2; i++) {
            EventLogManager.verifyOrCreateSchema(testDs, dbName);
            String tableListStr = ALL_TABLES.stream()
                    .map(t -> "'" + t + "'")
                    .collect(Collectors.joining(", "));
            String queryStr = MessageFormat.format(
                    "SELECT table_name "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = 'public' "
                    + "AND LOWER(table_name) IN ({0})", tableListStr);
            List<String> dbTables = getList(testDs, queryStr, (rs) -> rs.getString("table_name"));
            assertTrue(dbTables.size() == ALL_TABLES.size(), "Expected tables to be: {0} but received: {1}", getStr(ALL_TABLES), getStr(dbTables));
        }

        return testDs;
    }

    /**
     * Creates job records in the database and updates status verifying at each
     * step.
     *
     * @param caseRecords The case records.
     * @param manager     The manager.
     *
     * @throws SQLException
     */
    private void createRecordsUpdateStatusTest(List<CaseRecord> caseRecords, EventLogManager manager) throws SQLException {
        // create 12 (6 for each case) records (verify in db)
        String dsPrefix = "ds_";
        Map<Long, List<JobRecord>> jobRecords = new HashMap<>();
        for (CaseRecord c : caseRecords) {
            List<JobRecord> recList = new ArrayList<>();
            jobRecords.put(c.getId(), recList);
            for (int jIdx = 0; jIdx < 6; jIdx++) {
                recList.add(manager.getOrCreateJobRecord(jIdx, dsPrefix + jIdx));
            }
        }

        List<JobRecord> allRecords = jobRecords.values().stream()
                .flatMap(lst -> lst.stream())
                .collect(Collectors.toList());

        verifyStatus(manager, allRecords, null, null);

        // set initial 4 records for each case to running
        List<JobRecord> running1 = setStatus(manager, jobRecords, 4, JobStatus.RUNNING);

        // get pending by removing running from all
        List<JobRecord> pending1 = allRecords.stream()
                .filter(r
                        -> !running1.stream()
                        .filter(runItem -> runItem.getId() == r.getId())
                        .findFirst()
                        .isPresent())
                .collect(Collectors.toList());

        verifyStatus(manager, pending1, running1, null);

        // set initial 2 of each case (which should have been running) to done
        List<JobRecord> done2 = setStatus(manager, jobRecords, 2, JobStatus.DONE);

        // get running by filtering done
        List<JobRecord> running2 = allRecords.stream()
                .filter(r
                        -> !done2.stream()
                        .filter(runItem -> runItem.getId() == r.getId())
                        .findFirst()
                        .isPresent())
                .collect(Collectors.toList());

        verifyStatus(manager, pending1, running2, done2);
    }

    /**
     * Set status for first {count} jobs in each list of job records to the
     * status.
     *
     * @param manager    The manager.
     * @param jobRecords The job records mapping to case id.
     * @param count      The count of records in each value list to change.
     * @param status     The status to change to.
     *
     * @return The altered records after change.
     *
     * @throws SQLException
     */
    private List<JobRecord> setStatus(EventLogManager manager, Map<Long, List<JobRecord>> jobRecords, int count, JobStatus status) throws SQLException {
        // switch 8 to running (verify in db and get status)
        Date startDate = new Date();
        List<JobRecord> changed = new ArrayList<>();
        for (List<JobRecord> records : jobRecords.values()) {
            for (int jIdx = 0; jIdx < count; jIdx++) {
                JobRecord curRec = records.get(jIdx);
                JobRecord updatedRecord = manager.setJobStatus(curRec.getId(), JobStatus.RUNNING, startDate).get();
                assertTrue(curRec.getId() == updatedRecord.getId(), "Expected updated id to be equal, but changed from {0} to {1}", curRec.getId(), updatedRecord.getId());
                changed.add(updatedRecord);
            }
        }
        return changed;
    }

    /**
     * Verifies counts and expected ids for statuses of records.
     *
     * @param manager            The manager to use to retrieve items.
     * @param expectedPendingIds The expected pending ids. Null is equivalent to
     *                           empty list.
     * @param expectedRunningIds The expected running ids. Null is equivalent to
     *                           empty list.
     * @param expectedDoneIds    The expected done ids. Null is equivalent to
     *                           empty list.
     *
     * @throws SQLException
     */
    private static void verifyStatus(EventLogManager manager, List<JobRecord> expectedPendingIds, List<JobRecord> expectedRunningIds, List<JobRecord> expectedDoneIds) throws SQLException {
        List<Pair<JobStatus, List<JobRecord>>> itemsToCheck = Arrays.asList(
                Pair.of(JobStatus.PENDING, expectedPendingIds),
                Pair.of(JobStatus.RUNNING, expectedRunningIds),
                Pair.of(JobStatus.DONE, expectedDoneIds));

        for (Pair<JobStatus, List<JobRecord>> statusCounts : itemsToCheck) {
            List<JobRecord> fromDatabase = manager.getJobs(statusCounts.getKey());

            List<JobRecord> expectedJobs = statusCounts.getValue() == null ? Collections.emptyList() : statusCounts.getValue();
            Set<Long> expectedIds = expectedJobs.stream()
                    .map(r -> r.getId())
                    .collect(Collectors.toSet());

            assertTrue(fromDatabase.size() == expectedIds.size(),
                    "Expected {0} items but received {1} of {2}",
                    expectedIds.size(),
                    fromDatabase.size(),
                    getStr(fromDatabase));

            List<JobRecord> intersection = fromDatabase.stream()
                    .filter(r -> !expectedIds.contains(r.getId()))
                    .collect(Collectors.toList());

            assertTrue(intersection.size() == 0, "Expected all ids to overlap, but the following records were not expected: {0}", intersection);
        }
    }
}
