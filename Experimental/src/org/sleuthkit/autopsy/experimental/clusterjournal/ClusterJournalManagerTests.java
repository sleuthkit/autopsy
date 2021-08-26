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
package org.sleuthkit.autopsy.experimental.clusterjournal;

import com.mchange.v2.c3p0.ComboPooledDataSource;
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
 * ClusterJournalManager.
 */
public class ClusterJournalManagerTests {

    /**
     * The tables in the database.
     */
    private static final List<String> ALL_TABLES = Arrays.asList(
            "cases",
            "ingest_jobs",
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
     * @throws ClusterJournalException
     */
    public void runTests(String host, String port, String userName, String pword, String dbName) throws ClassNotFoundException, SQLException, ClusterJournalException {
        ClusterJournalManager manager = null;
        try {
            ComboPooledDataSource testDs = verifyDbAndSchemaTest(host, port, userName, pword, dbName);

            manager = new ClusterJournalManager(testDs);

            List<CaseRecord> caseRecords = createCasesTest(manager, testDs);

            createJobsUpdateStatusTest(caseRecords, manager, testDs);

        } finally {
            // drop db when done
            if (manager != null) {
                manager.close();
                manager = null;
            }

            try (Connection conn = ClusterJournalManager.getPgConnection(host, port, userName, pword, Optional.empty());
                    Statement stmt = conn.createStatement()) {
                stmt.execute("DROP DATABASE " + dbName);
            }
        }
    }

    /**
     * Creates case records with the ClusterJournalManager, verifies their
     * creation, and returns the created cases.
     *
     * @param manager The manager.
     * @param testDs  The test data source in order to verify tables independent
     *                of the cluster journal manager.
     *
     * @return The created cases.
     *
     * @throws SQLException
     * @throws IllegalStateException
     */
    private List<CaseRecord> createCasesTest(ClusterJournalManager manager, DataSource testDs) throws SQLException, IllegalStateException {
        String case1Str = "Case_1";
        String case2Str = "Case_2";
        Date caseDate = new Date();
        CaseRecord case1 = manager.getOrCreateCaseRecord(case1Str, Optional.of(caseDate));
        manager.getOrCreateCaseRecord(case2Str, Optional.of(caseDate));
        List<CaseRecord> caseRecords = getList(testDs, "SELECT case_id, name, created_date FROM cases",
                (rs) -> new CaseRecord(rs.getLong("case_id"), rs.getString("name"), Optional.ofNullable(rs.getTimestamp("created_date"))))
                .stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
        assertTrue(caseRecords.size() == 2, "Expected 2 cases created; received {0} instead", getStr(caseRecords));
        assertTrue(caseRecords.get(0).getName().equals(case1Str), "Expected first case to be {0} but was {1}", case1Str, caseRecords.get(0).getName());

        assertTrue(caseDate.equals(caseRecords.get(0).getCreatedDate().get()),
                "Expected first case to have created date of  {0} but was {1}",
                caseDate,
                caseRecords.get(0).getCreatedDate().get());
        assertTrue(caseRecords.get(1).getName().equals(case2Str), "Expected first case to be {0} but was {1}", case2Str, caseRecords.get(1).getName());

        assertTrue(caseDate.equals(caseRecords.get(1).getCreatedDate().get()),
                "Expected second case to have created date of  {0} but was {1}",
                caseDate,
                caseRecords.get(1).getCreatedDate().get());

        // verify repeat case returns previous
        CaseRecord repeatCase = manager.getOrCreateCaseRecord(case1Str, Optional.of(new Date()));
        assertTrue(case1.getId() == repeatCase.getId(), "Expected repeat case (id: {0}) to have same id as case1 (id: {1})", repeatCase.getId(), case1.getId());
        return caseRecords;
    }

    /**
     * Tests that the database is not initially present so data is not
     * overwritten, creates the database and schema with the cluster journal
     * manager.
     *
     * @param host     The pg host.
     * @param port     The pg port.
     * @param userName The pg user name.
     * @param pword    The pg password.
     * @param dbName   The pg database.
     *
     * @return The data source to use for connections to the created database.
     *
     * @throws ClusterJournalException
     * @throws IllegalStateException
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private ComboPooledDataSource verifyDbAndSchemaTest(String host, String port, String userName, String pword, String dbName) throws ClusterJournalException, IllegalStateException, SQLException, ClassNotFoundException {
        // if db exists throw (shouldn't exist on start)
        try (Connection conn = ClusterJournalManager.getPgConnection(host, port, userName, pword, Optional.empty())) {
            if (ClusterJournalManager.verifyDatabaseExists(conn, dbName)) {
                onErr("Database {0} shouldn't exist when running tests.  "
                        + "Please drop the database and try again.", dbName);
            }

            // verify or create database (check externally)
            ClusterJournalManager.verifyOrCreatePgDb(host, port, userName, pword, dbName);

            if (!ClusterJournalManager.verifyDatabaseExists(conn, dbName)) {
                onErr("Unable to create database {0}", dbName);
            }
        }
        ComboPooledDataSource testDs = ClusterJournalManager.getDataSource(host, port, userName, pword, dbName);
        // verify or create schema (check externally) and verify that the schema isn't changed.
        for (int i = 0; i < 2; i++) {
            ClusterJournalManager.verifyOrCreateSchema(testDs, dbName);
            String tableListStr = ALL_TABLES.stream()
                    .map(t -> "'" + t + "'")
                    .collect(Collectors.joining(", "));
            String queryStr = String.format(
                    "SELECT table_name "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = 'public' "
                    + "AND LOWER(table_name) IN (%s)", tableListStr);
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
    private void createJobsUpdateStatusTest(List<CaseRecord> caseRecords, ClusterJournalManager manager, ComboPooledDataSource testDs) throws SQLException {
        // create 12 (6 for each case) records (verify in db)
        String dsPrefix = "ds_";
        Map<Long, List<IngestJobRecord>> jobRecords = new HashMap<>();
        for (CaseRecord c : caseRecords) {
            List<IngestJobRecord> recList = new ArrayList<>();
            jobRecords.put(c.getId(), recList);
            for (int jIdx = 0; jIdx < 6; jIdx++) {
                IngestJobRecord created = manager.getOrCreateJobRecord(c.getId(), dsPrefix + jIdx);
                assertTrue(created.getCaseName().equals(c.getName()),
                        "Expected case names to be equal.  Received {0}; expected {1}.",
                        created.getCaseName(), c.getName());

                assertTrue(created.getDataSourceName().equals(dsPrefix + jIdx),
                        "Expected data source names to be equal.  Received {0}; expected {1}.",
                        created.getDataSourceName(), dsPrefix + jIdx);

                recList.add(created);
            }
        }

        List<IngestJobRecord> allRecords = jobRecords.values().stream()
                .flatMap(lst -> lst.stream())
                .collect(Collectors.toList());

        verifyStatus(manager, testDs, allRecords, null, null, null);

        // set initial 4 records for each case to running
        List<IngestJobRecord> running1 = setStatus(manager, jobRecords, 4, IngestJobStatus.RUNNING);

        // get pending by removing running from all
        List<IngestJobRecord> pending1 = allRecords.stream()
                .filter(r
                        -> !running1.stream()
                        .filter(runItem -> runItem.getId() == r.getId())
                        .findFirst()
                        .isPresent())
                .collect(Collectors.toList());

        verifyStatus(manager, testDs, pending1, running1, null, null);

        // set initial 2 of each case (which should have been running) to done
        List<IngestJobRecord> done2 = setStatus(manager, jobRecords, 2, IngestJobStatus.DONE);

        // get running by filtering done
        List<IngestJobRecord> running2 = running1.stream()
                .filter(r
                        -> !done2.stream()
                        .filter(runItem -> runItem.getId() == r.getId())
                        .findFirst()
                        .isPresent())
                .collect(Collectors.toList());

        verifyStatus(manager, testDs, pending1, running2, done2, null);

        IngestJobRecord erroneous = manager.setJobError(done2.get(0).getId(), true).get();

        assertTrue(done2.get(0).getCaseName().equals(erroneous.getCaseName())
                && done2.get(0).getCaseId() == erroneous.getCaseId()
                && done2.get(0).getDataSourceName().equals(erroneous.getDataSourceName()),
                "Expected erroneous sent and received to be equivalent except for status changes but received {0} when previous was {1}.",
                done2.get(0), erroneous);

        verifyStatus(manager, testDs, pending1, running2, done2, Arrays.asList(erroneous));
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
    private List<IngestJobRecord> setStatus(ClusterJournalManager manager, Map<Long, List<IngestJobRecord>> jobRecords, int count, IngestJobStatus status) throws SQLException {
        // switch 8 to running (verify in db and get status)
        Date startDate = new Date();
        List<IngestJobRecord> changed = new ArrayList<>();
        for (List<IngestJobRecord> records : jobRecords.values()) {
            for (int jIdx = 0; jIdx < count; jIdx++) {
                IngestJobRecord curRec = records.get(jIdx);
                IngestJobRecord updatedRecord = manager.setJobStatus(curRec.getId(), status, startDate).get();
                assertTrue(curRec.getId() == updatedRecord.getId(),
                        "Expected updated id to be equal, but changed from {0} to {1}",
                        curRec.getId(), updatedRecord.getId());
                assertTrue(curRec.getCaseName().equals(updatedRecord.getCaseName())
                        && updatedRecord.getCaseId() == curRec.getCaseId()
                        && updatedRecord.getDataSourceName().equals(curRec.getDataSourceName()),
                        "Expected sent and received to be equivalent except for status changes but received {0} when previous was {1}.",
                        updatedRecord, curRec);

                if (status == IngestJobStatus.DONE) {
                    assertTrue(startDate.equals(updatedRecord.getEndTime().get()),
                            "Expected end time of {0} but received {1}.",
                            startDate, updatedRecord.getEndTime().get());
                } else if (status == IngestJobStatus.RUNNING) {
                    assertTrue(startDate.equals(updatedRecord.getStartTime().get()),
                            "Expected end time of {0} but received {1}.",
                            startDate, updatedRecord.getStartTime().get());
                }

                changed.add(updatedRecord);
            }
        }
        return changed;
    }

    /**
     * Verifies counts and expected ids for statuses of records.
     *
     * @param manager            The manager to use to retrieve items.
     * @param testDs             The database connection pool.
     * @param expectedPendingIds The expected pending ids. Null is equivalent to
     *                           empty list.
     * @param expectedRunningIds The expected running ids. Null is equivalent to
     *                           empty list.
     * @param expectedDoneIds    The expected done ids. Null is equivalent to
     *                           empty list.
     * @param erroneous          The records with errors.
     *
     * @throws SQLException
     */
    private static void verifyStatus(ClusterJournalManager manager, ComboPooledDataSource testDs, List<IngestJobRecord> expectedPendingIds,
            List<IngestJobRecord> expectedRunningIds, List<IngestJobRecord> expectedDoneIds, List<IngestJobRecord> erroneousRecords) throws SQLException {

        List<Pair<IngestJobStatus, List<IngestJobRecord>>> itemsToCheck = Arrays.asList(
                Pair.of(IngestJobStatus.PENDING, expectedPendingIds),
                Pair.of(IngestJobStatus.RUNNING, expectedRunningIds),
                Pair.of(IngestJobStatus.DONE, expectedDoneIds));

        for (Pair<IngestJobStatus, List<IngestJobRecord>> statusCounts : itemsToCheck) {
            List<IngestJobRecord> fromDatabase = manager.getJobs(statusCounts.getKey());

            List<IngestJobRecord> expectedJobs = statusCounts.getValue() == null ? Collections.emptyList() : statusCounts.getValue();
            Set<Long> expectedIds = expectedJobs.stream()
                    .map(r -> r.getId())
                    .collect(Collectors.toSet());

            assertTrue(fromDatabase.size() == expectedIds.size(),
                    "Expected {0} items but received {1} of {2}",
                    expectedIds.size(),
                    fromDatabase.size(),
                    getStr(fromDatabase));

            List<IngestJobRecord> difference = fromDatabase.stream()
                    .filter(r -> !expectedIds.contains(r.getId()))
                    .collect(Collectors.toList());

            assertTrue(difference.size() == 0, "Expected all ids to overlap, but the following records were not expected: {0}", difference);
        }

        // handle error records
        List<Long> errorIds = new ArrayList<>();

        try (Connection conn = testDs.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT job_id FROM ingest_jobs WHERE error_occurred = TRUE")) {
            while (rs.next()) {
                errorIds.add(rs.getLong("job_id"));
            }
        }
        List<IngestJobRecord> expectedErrors = erroneousRecords == null ? Collections.emptyList() : erroneousRecords;

        assertTrue(errorIds.size() == expectedErrors.size(),
                "Expected {0} items but received {1} of {2}",
                expectedErrors.size(),
                errorIds.size(),
                getStr(errorIds));

        
        List<IngestJobRecord> difference = expectedErrors.stream()
                .filter(r -> !errorIds.contains(r.getId()))
                .collect(Collectors.toList());

        assertTrue(difference.size() == 0, "Expected all error ids to overlap, but the following were expected and not seen: {0}", difference);
    }
}
