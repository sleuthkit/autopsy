/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 *
 */
public class CentralRepoDatamodelTest extends TestCase {

    private static final String PROPERTIES_FILE = "CentralRepository";
    private static final String CR_DB_NAME = "testcentralrepo.db";
    private static final Path testDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "CentralRepoDatamodelTest");
    SqliteEamDbSettings dbSettingsSqlite;

    private CorrelationCase case1;
    private CorrelationCase case2;
    private CorrelationDataSource dataSource1fromCase1;
    private CorrelationDataSource dataSource2fromCase1;
    private EamOrganization org1;
    private EamOrganization org2;

    private Map<String, String> propertiesMap = null;

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CentralRepoDatamodelTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    @Override
    public void setUp() {
        dbSettingsSqlite = new SqliteEamDbSettings();

        // Delete the test directory, if it exists
        if (testDirectory.toFile().exists()) {
            try {
                FileUtils.deleteDirectory(testDirectory.toFile());
            } catch (IOException ex) {
                Assert.fail(ex);
            }
        }
        assertFalse("Unable to delete existing test directory", testDirectory.toFile().exists());

        // Create the test directory
        testDirectory.toFile().mkdirs();
        assertTrue("Unable to create test directory", testDirectory.toFile().exists());

        // Save the current central repo settings
        propertiesMap = ModuleSettings.getConfigSettings(PROPERTIES_FILE);

        // Set up an Autopsy case for testing
        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, testDirectory.toString(), new CaseDetails("CentralRepoDatamodelTestCase"));
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        assertTrue("Failed to create test case", testDirectory.toFile().exists());

        try {
            dbSettingsSqlite.setDbName(CR_DB_NAME);
            dbSettingsSqlite.setDbDirectory(testDirectory.toString());
            if (!dbSettingsSqlite.dbDirectoryExists()) {
                dbSettingsSqlite.createDbDirectory();
            }

            assertTrue("Failed to created central repo directory " + dbSettingsSqlite.getDbDirectory(), dbSettingsSqlite.dbDirectoryExists());

            boolean result = dbSettingsSqlite.initializeDatabaseSchema()
                    && dbSettingsSqlite.insertDefaultDatabaseContent();

            assertTrue("Failed to initialize central repo database", result);

            dbSettingsSqlite.saveSettings();
            EamDbUtil.setUseCentralRepo(true);
            EamDbPlatformEnum.setSelectedPlatform(EamDbPlatformEnum.SQLITE.name());
            EamDbPlatformEnum.saveSelectedPlatform();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        Path crDbFilePath = Paths.get(testDirectory.toString(), CR_DB_NAME);
        assertTrue("Failed to create central repo database at " + crDbFilePath, crDbFilePath.toFile().exists());

        // Set up some default objects to be used by the tests
        try {
            case1 = new CorrelationCase("case1_uuid", "case1");
            case1 = EamDb.getInstance().newCase(case1);
            assertTrue("Failed to create test object case1", case1 != null);

            case2 = new CorrelationCase("case2_uuid", "case2");
            case2 = EamDb.getInstance().newCase(case2);
            assertTrue("Failed to create test object case2", case2 != null);

            dataSource1fromCase1 = new CorrelationDataSource(case1.getID(), "dataSource1_deviceID", "dataSource1");
            EamDb.getInstance().newDataSource(dataSource1fromCase1);
            dataSource1fromCase1 = EamDb.getInstance().getDataSource(case1, dataSource1fromCase1.getDeviceID());
            assertTrue("Failed to create test object dataSource1fromCase1", dataSource1fromCase1 != null);

            dataSource2fromCase1 = new CorrelationDataSource(case1.getID(), "dataSource2_deviceID", "dataSource2");
            EamDb.getInstance().newDataSource(dataSource2fromCase1);
            dataSource2fromCase1 = EamDb.getInstance().getDataSource(case1, dataSource2fromCase1.getDeviceID());
            assertTrue("Failed to create test object dataSource2fromCase1", dataSource2fromCase1 != null);

            org1 = new EamOrganization("org1");
            org1.setOrgID((int) EamDb.getInstance().newOrganization(org1));

            org2 = new EamOrganization("org2");
            org2.setOrgID((int) EamDb.getInstance().newOrganization(org2));

        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

    }

    @Override
    public void tearDown() {

        // Restore the original properties
        ModuleSettings.setConfigSettings(PROPERTIES_FILE, propertiesMap);

        // Close and delete the test case and central repo db
        try {
            EamDb.getInstance().shutdownConnections();
            Case.closeCurrentCase();
            FileUtils.deleteDirectory(testDirectory.toFile());

        } catch (EamDbException | CaseActionException | IOException ex) {
            //} catch (EamDbException | CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        assertFalse("Error deleting test directory " + testDirectory.toString(), testDirectory.toFile().exists());
    }

    public void testDataSources() {
        final String dataSourceAname = "dataSourceA";
        final String dataSourceAid = "dataSourceA_deviceID";
        CorrelationDataSource dataSourceA;
        CorrelationDataSource dataSourceB;

        // Test creating a data source with valid case, name, and ID
        try {
            dataSourceA = new CorrelationDataSource(case2.getID(), dataSourceAid, dataSourceAname);
            EamDb.getInstance().newDataSource(dataSourceA);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
        
        // Test creating a data source with the same case, name, and ID
        try {
            CorrelationDataSource temp = new CorrelationDataSource(case2.getID(), dataSourceAid, dataSourceAname);
            EamDb.getInstance().newDataSource(temp);
            Assert.fail("newDataSource did not throw exception from duplicate data source");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }
        
        // Test creating a data source with the same name and ID but different case
        try {
            dataSourceB = new CorrelationDataSource(case1.getID(), dataSourceAid, dataSourceAname);
            EamDb.getInstance().newDataSource(dataSourceB);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }

        // Test creating a data source with an invalid case ID
        try {
            CorrelationDataSource temp = new CorrelationDataSource(5000, "tempID", "tempName");
            EamDb.getInstance().newDataSource(temp);
            Assert.fail("newDataSource did not throw exception from invalid case ID");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test creating a data source with null device ID
        try {
            CorrelationDataSource temp = new CorrelationDataSource(case2.getID(), null, "tempName");
            EamDb.getInstance().newDataSource(temp);
            Assert.fail("newDataSource did not throw exception from null device ID");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test creating a data source with null device ID
        try {
            CorrelationDataSource temp = new CorrelationDataSource(case2.getID(), "tempID", null);
            EamDb.getInstance().newDataSource(temp);
            Assert.fail("newDataSource did not throw exception from null name");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test getting a data source with valid case and ID
        try {
            CorrelationDataSource temp = EamDb.getInstance().getDataSource(case2, dataSourceAid);
            assertTrue("Failed to get data source", temp != null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test getting a data source with non-existent ID
        try {
            CorrelationDataSource temp = EamDb.getInstance().getDataSource(case2, "badID");
            assertTrue("getDataSource returned non-null value for non-existent data source", temp == null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test getting a data source with a null case
        try {
            CorrelationDataSource temp = EamDb.getInstance().getDataSource(null, dataSourceAid);
            Assert.fail("getDataSource did not throw exception from null case");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test getting a data source with null ID
        try {
            CorrelationDataSource temp = EamDb.getInstance().getDataSource(case2, null);
            assertTrue("getDataSource returned non-null value for null data source", temp == null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test getting the list of data sources
        // There should be three data sources, and we'll check for the expected device IDs
        try {
            List<CorrelationDataSource> dataSources = EamDb.getInstance().getDataSources();
            List<String> devIdList
                    = dataSources.stream().map(c -> c.getDeviceID()).collect(Collectors.toList());
            assertTrue("getDataSources returned unexpected number of data sources", dataSources.size() == 4);
            assertTrue("getDataSources is missing expected data sources",
                    devIdList.contains(dataSourceAid)
                    && devIdList.contains(dataSource1fromCase1.getDeviceID())
                    && devIdList.contains(dataSource2fromCase1.getDeviceID()));
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test the data source count
        try {
            assertTrue("getCountUniqueDataSources returned unexpected number of data sources", 
                    EamDb.getInstance().getCountUniqueDataSources() == 4);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Test method for the methods related to the cases table
     * newCase(CorrelationCase eamCase) tests: - Test valid data - Test null
     * UUID - Test null case name - Test repeated UUID newCase(Case autopsyCase)
     * tests: - Test valid data - Test null autopsyCase
     * updateCase(CorrelationCase eamCase) tests: - Test with valid data,
     * checking all fields - Test null eamCase getCase(Case autopsyCase) tests:
     * - Test with current Autopsy case getCaseByUUID(String caseUUID)
     * getCases() - Test with UUID that is in the database - Test with UUID that
     * is not in the database - Test with null UUID
     * bulkInsertCases(List<CorrelationCase> cases) - Test on a list of cases
     * larger than the bulk insert threshold. - Test on a null list
     */
    public void testCases() {
        final String caseAname = "caseA";
        final String caseAuuid = "caseA_uuid";
        CorrelationCase caseA;
        CorrelationCase caseB;

        // Test creating a case with valid name and uuid
        try {
            caseA = new CorrelationCase(caseAuuid, caseAname);
            caseA = EamDb.getInstance().newCase(caseA);
            assertTrue("Failed to create case", caseA != null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }

        // Test null uuid
        try {
            CorrelationCase tempCase = new CorrelationCase(null, "nullUuidCase");
            tempCase = EamDb.getInstance().newCase(tempCase);
            Assert.fail("newCase did not throw expected exception from null uuid");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test null name
        try {
            CorrelationCase tempCase = new CorrelationCase("nullCaseUuid", null);
            tempCase = EamDb.getInstance().newCase(tempCase);
            Assert.fail("newCase did not throw expected exception from null name");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test creating a case with an already used UUID
        // This should just return the existing case object. Check that the total 
        // number of cases does not increase.
        try {
            int nCases = EamDb.getInstance().getCases().size();
            CorrelationCase tempCase = new CorrelationCase(caseAuuid, "newCaseWithSameUUID");
            tempCase = EamDb.getInstance().newCase(tempCase);
            assertTrue("newCase returned null for existing UUID", tempCase != null);
            assertTrue("newCase created a new case for an already existing UUID", nCases == EamDb.getInstance().getCases().size());
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test creating a case from an Autopsy case
        // The case may already be in the database - the result is the same either way
        try {
            caseB = EamDb.getInstance().newCase(Case.getCurrentCase());
            assertTrue("Failed to create correlation case from Autopsy case", caseB != null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }

        // Test null Autopsy case
        try {
            Case nullCase = null;
            CorrelationCase tempCase = EamDb.getInstance().newCase(nullCase);
            Assert.fail("newCase did not throw expected exception from null case");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test update case
        // Will update the fields of an existing case object, save it, and then
        // pull a new copy out of the database
        try {
            assertTrue(caseA != null);
            String caseNumber = "12-34-56";
            String creationDate = "01/12/2018";
            String displayName = "Test Case";
            String examinerEmail = "john@sample.com";
            String examinerName = "John Doe";
            String examinerPhone = "123-555-4567";
            String notes = "Notes";

            caseA.setCaseNumber(caseNumber);
            caseA.setCreationDate(creationDate);
            caseA.setDisplayName(displayName);
            caseA.setExaminerEmail(examinerEmail);
            caseA.setExaminerName(examinerName);
            caseA.setExaminerPhone(examinerPhone);
            caseA.setNotes(notes);
            caseA.setOrg(org1);

            EamDb.getInstance().updateCase(caseA);

            // Retrievex a new copy of the case from the database to check that the 
            // fields were properly updated
            CorrelationCase updatedCase = EamDb.getInstance().getCaseByUUID(caseA.getCaseUUID());

            assertTrue("updateCase failed to update case number", caseNumber.equals(updatedCase.getCaseNumber()));
            assertTrue("updateCase failed to update creation date", creationDate.equals(updatedCase.getCreationDate()));
            assertTrue("updateCase failed to update display name", displayName.equals(updatedCase.getDisplayName()));
            assertTrue("updateCase failed to update examiner email", examinerEmail.equals(updatedCase.getExaminerEmail()));
            assertTrue("updateCase failed to update examiner name", examinerName.equals(updatedCase.getExaminerName()));
            assertTrue("updateCase failed to update examiner phone number", examinerPhone.equals(updatedCase.getExaminerPhone()));
            assertTrue("updateCase failed to update notes", notes.equals(updatedCase.getNotes()));
            assertTrue("updateCase failed to update org (org is null)", updatedCase.getOrg() != null);
            assertTrue("updateCase failed to update org (org ID is wrong)", org1.getOrgID() == updatedCase.getOrg().getOrgID());
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test update case with null case
        try {
            EamDb.getInstance().updateCase(null);
            Assert.fail("updateCase did not throw expected exception from null case");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test getting a case from an Autopsy case
        try {
            CorrelationCase tempCase = EamDb.getInstance().getCase(Case.getCurrentCase());
            assertTrue("getCase returned null for current Autopsy case", tempCase != null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test getting a case by UUID
        try {
            CorrelationCase tempCase = EamDb.getInstance().getCaseByUUID(caseAuuid);
            assertTrue("Failed to get case by UUID", tempCase != null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test getting a case with a non-existent UUID
        try {
            CorrelationCase tempCase = EamDb.getInstance().getCaseByUUID("badUUID");
            assertTrue("getCaseByUUID returned non-null case for non-existent UUID", tempCase == null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test getting a case with null UUID
        try {
            CorrelationCase tempCase = EamDb.getInstance().getCaseByUUID(null);
            assertTrue("getCaseByUUID returned non-null case for null UUID", tempCase == null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test getting the list of cases
        // The test is to make sure the three cases we know are in the database are in the list
        try {
            List<CorrelationCase> caseList = EamDb.getInstance().getCases();
            List<String> uuidList
                    = caseList.stream().map(c -> c.getCaseUUID()).collect(Collectors.toList());
            assertTrue("getCases is missing data for existing cases", uuidList.contains(case1.getCaseUUID())
                    && uuidList.contains(case2.getCaseUUID()) && (uuidList.contains(caseA.getCaseUUID()))
                    && uuidList.contains(caseB.getCaseUUID()));
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test bulk case insert
        try {
            // Create a list of correlation cases. Make enough that the bulk threshold should be hit once.
            List<CorrelationCase> cases = new ArrayList<>();
            String bulkTestUuid = "bulkTestUUID_";
            String bulkTestName = "bulkTestName_";
            for (int i = 0; i < dbSettingsSqlite.getBulkThreshold() * 1.5; i++) {
                String name = bulkTestUuid + String.valueOf(i);
                String uuid = bulkTestName + String.valueOf(i);
                cases.add(new CorrelationCase(uuid, name));
            }

            // Get the current case count
            int nCases = EamDb.getInstance().getCases().size();

            // Insert the big list of cases
            EamDb.getInstance().bulkInsertCases(cases);

            // Check that the case count is what is expected
            assertTrue("bulkInsertCases did not insert the expected number of cases", nCases + cases.size() == EamDb.getInstance().getCases().size());
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test bulk case insert with null list
        try {
            EamDb.getInstance().bulkInsertCases(null);
            Assert.fail("bulkInsertCases did not throw expected exception from null list");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }
    }

    /**
     * Test method for the three methods related to the db_info table
     * newDbInfo(String name, String value) tests: - Test valid data - Test null
     * name - Test null value getDbInfo(String name) - Test getting value for
     * existing name - Test getting value for non-existing name - Test getting
     * value for null name updateDbInfo(String name, String value) - Test
     * updating existing name to valid new value - Test updating existing name
     * to null value - Test updating null name - Test updating non-existing name
     * to new value
     */
    public void testDbInfo() {
        final String name1 = "testName1";
        final String name2 = "testName2";
        final String name3 = "testName3";
        final String value1 = "testValue1";
        final String value2 = "testValue2";

        // Test setting a valid value in DbInfo
        try {
            EamDb.getInstance().newDbInfo(name1, value1);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Test null name
        try {
            EamDb.getInstance().newDbInfo(null, value1);
            Assert.fail("newDbInfo did not throw expected exception from null name");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Test null value
        try {
            EamDb.getInstance().newDbInfo(name2, null);
            Assert.fail("newDbInfo did not throw expected exception from null value");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Try getting the dbInfo entry that should exist
        try {
            String tempVal = EamDb.getInstance().getDbInfo(name1);
            assertTrue("dbInfo value for name1 does not match", value1.equals(tempVal));
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Try getting the dbInfo entry that should not exist
        try {
            String tempVal = EamDb.getInstance().getDbInfo(name3);
            assertTrue("dbInfo value is unexpectedly non-null given non-existent name", tempVal == null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Try getting dbInfo for a null value
        try {
            String tempVal = EamDb.getInstance().getDbInfo(null);
            assertTrue("dbInfo value is unexpectedly non-null given null name", tempVal == null);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Try updating an existing value to a valid new value
        try {
            EamDb.getInstance().updateDbInfo(name1, value2);
            assertTrue("dbInfo value failed to update to expected value", value2.equals(EamDb.getInstance().getDbInfo(name1)));
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Try updating an existing value to null
        try {
            EamDb.getInstance().updateDbInfo(name1, null);
            Assert.fail("updateDbInfo did not throw expected exception from null value");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }

        // Try updating a null name
        // This seems like SQLite would throw an exception, but it does not 
        try {
            EamDb.getInstance().updateDbInfo(null, value1);
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }

        // Try updating the value for a non-existant name
        try {
            EamDb.getInstance().updateDbInfo(name1, null);
            Assert.fail("updateDbInfo did not throw expected exception from non-existent name");
        } catch (EamDbException ex) {
            // This is the expected behavior
        }
    }

}
