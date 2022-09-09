/*
 * Central Repository
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import junit.framework.Test;
import junit.framework.TestCase;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;

/**
 * Functional tests for the Central Repository data model.
 *
 * TODO (JIRA-4241): All of the tests in this class are commented out until the
 * Image Gallery tool cleans up its drawable database connection
 * deterministically, instead of in a finalizer. As it is now, case deletion can
 * fail due to an open drawable database file handles, and that makes the tests
 * fail.
 */
public class CentralRepoDatamodelTest extends TestCase {

    // Classloader for qa functional tests is having trouble with loading NbBundle.  
    // Path is hard-coded to avoid that issue instead of using 
    // CentralRepoSettings.getInstance().getModuleSettingsKey()
    private static final String PROPERTIES_FILE = "ModuleConfig/CentralRepository/CentralRepository";
    private static final String CR_DB_NAME = "testcentralrepo.db";

    private static final Path testDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "CentralRepoDatamodelTest");
    private static final int DEFAULT_BULK_THRESHOLD = 1000; // hard coded from EamDb

    SqliteCentralRepoSettings dbSettingsSqlite;
    
    private static final long CASE_1_DATA_SOURCE_1_ID = 11;
    private static final long CASE_1_DATA_SOURCE_2_ID = 12;
    private static final long CASE_2_DATA_SOURCE_1_ID = 21;

    private CorrelationCase case1;
    private CorrelationCase case2;
    private CorrelationDataSource dataSource1fromCase1;
    private CorrelationDataSource dataSource2fromCase1;
    private CorrelationDataSource dataSource1fromCase2;
    private CentralRepoOrganization org1;
    private CentralRepoOrganization org2;
    CorrelationAttributeInstance.Type fileType;
    CorrelationAttributeInstance.Type usbDeviceType;

    private Map<String, String> propertiesMap = null;

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CentralRepoDatamodelTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    @Override
    public void setUp() {
        dbSettingsSqlite = new SqliteCentralRepoSettings();

        // Delete the test directory, if it exists
        if (testDirectory.toFile().exists()) {
            try {
                if (CentralRepository.isEnabled()) {
                    CentralRepository.getInstance().shutdownConnections();
                }
                FileUtil.deleteDir(testDirectory.toFile());
            } catch (CentralRepoException ex) {
                Assert.fail(ex.getMessage());
            }
        }
        assertFalse("Unable to delete existing test directory", testDirectory.toFile().exists());

        // Create the test directory
        testDirectory.toFile().mkdirs();
        assertTrue("Unable to create test directory", testDirectory.toFile().exists());

        // Save the current central repo settings
        propertiesMap = ModuleSettings.getConfigSettings(PROPERTIES_FILE);

        try {
            dbSettingsSqlite.setDbName(CR_DB_NAME);
            dbSettingsSqlite.setDbDirectory(testDirectory.toString());
            if (!dbSettingsSqlite.dbDirectoryExists()) {
                dbSettingsSqlite.createDbDirectory();
            }

            assertTrue("Failed to created central repo directory " + dbSettingsSqlite.getDbDirectory(), dbSettingsSqlite.dbDirectoryExists());

            RdbmsCentralRepoFactory factory = new RdbmsCentralRepoFactory(CentralRepoPlatforms.SQLITE, dbSettingsSqlite);
            boolean result = factory.initializeDatabaseSchema()
                    && factory.insertDefaultDatabaseContent();

            assertTrue("Failed to initialize central repo database", result);

            dbSettingsSqlite.saveSettings();
            CentralRepoDbUtil.setUseCentralRepo(true);
            CentralRepoDbManager.saveDbChoice(CentralRepoDbChoice.SQLITE);
            CentralRepository.getInstance().updateSettings();
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        Path crDbFilePath = Paths.get(testDirectory.toString(), CR_DB_NAME);
        assertTrue("Failed to create central repo database at " + crDbFilePath, crDbFilePath.toFile().exists());

        // Set up some default objects to be used by the tests
        try {
            case1 = new CorrelationCase("case1_uuid", "case1");
            case1 = CentralRepository.getInstance().newCase(case1);
            assertTrue("Failed to create test object case1", case1 != null);

            case2 = new CorrelationCase("case2_uuid", "case2");
            case2 = CentralRepository.getInstance().newCase(case2);
            assertTrue("Failed to create test object case2", case2 != null);

            dataSource1fromCase1 = new CorrelationDataSource(case1, "dataSource1_deviceID", "dataSource1", CASE_1_DATA_SOURCE_1_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource1fromCase1);
            dataSource1fromCase1 = CentralRepository.getInstance().getDataSource(case1, dataSource1fromCase1.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource1fromCase1", dataSource1fromCase1 != null);

            dataSource2fromCase1 = new CorrelationDataSource(case1, "dataSource2_deviceID", "dataSource2", CASE_1_DATA_SOURCE_2_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource2fromCase1);
            dataSource2fromCase1 = CentralRepository.getInstance().getDataSource(case1, dataSource2fromCase1.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource2fromCase1", dataSource2fromCase1 != null);

            dataSource1fromCase2 = new CorrelationDataSource(case2, "dataSource3_deviceID", "dataSource3", CASE_2_DATA_SOURCE_1_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource1fromCase2);
            dataSource1fromCase2 = CentralRepository.getInstance().getDataSource(case2, dataSource1fromCase2.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource1fromCase2", dataSource1fromCase2 != null);

            org1 = new CentralRepoOrganization("org1");
            org1 = CentralRepository.getInstance().newOrganization(org1);

            org2 = new CentralRepoOrganization("org2");
            org2 = CentralRepository.getInstance().newOrganization(org2);

            // Store the file type object for later use
            fileType = CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
            assertTrue("getCorrelationTypeById(FILES_TYPE_ID) returned null", fileType != null);
            usbDeviceType = CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.USBID_TYPE_ID);
            assertTrue("getCorrelationTypeById(USBID_TYPE_ID) returned null", usbDeviceType != null);

        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    @Override
    public void tearDown() {

        // Restore the original properties
        ModuleSettings.setConfigSettings(PROPERTIES_FILE, propertiesMap);

        // Close and delete the test case and central repo db
        try {
            if (CentralRepository.isEnabled()) {
                CentralRepository.getInstance().shutdownConnections();
            }
            FileUtil.deleteDir(testDirectory.toFile());
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
        assertFalse("Error deleting test directory " + testDirectory.toString(), testDirectory.toFile().exists());
    }

    /**
     * Test the notable status of artifacts
     * addArtifactInstance(CorrelationAttribute eamArtifact) tests: - Test that
     * two artifacts created with BAD status still have it when fetched from the
     * database - Test that two artifacts created with BAD and KNOWN status
     * still have the correct status when fetched from the database
     * setAttributeInstanceKnownStatus(CorrelationAttribute eamArtifact,
     * TskData.FileKnown knownStatus) tests: - Test updating status - Test
     * updating artifact with two instances - Test updating null artifact - Test
     * updating artifact with null known status - Test updating artifact with
     * null case - Test updating artifact with null data source
     * getArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType,
     * String value) tests: - Test getting two notable instances - Test getting
     * notable instances where one instance is notable and the other is known -
     * Test getting notable instances with null type - Test getting notable
     * instances with null value
     * getCountArtifactInstancesKnownBad(CorrelationAttributeInstance.Type
     * aType, String value) tests: - Test getting count of two notable instances
     * - Test getting notable instance count where one instance is notable and
     * the other is known - Test getting notable instance count with null type -
     * Test getting notable instance count with null value
     * getListCasesHavingArtifactInstancesKnownBad(CorrelationAttributeInstance.Type
     * aType, String value) tests: - Test getting cases with notable instances
     * (all instances are notable) - Test getting cases with notable instances
     * (only one instance is notable) - Test getting cases with null type - Test
     * getting cases with null value
     */
    public void testNotableArtifactStatus() {

        String notableHashInBothCases = "e34a8899ef6468b74f8a1048419ccc8b";
        String notableHashInOneCaseKnownOther = "d293f2f5cebcb427cde3bb95db5e1797";
        String hashToChangeToNotable = "23bd4ea37ec6304e75ac723527472a0f";

        // Add two instances with notable status
        try {
            CorrelationAttributeInstance attr1 = new CorrelationAttributeInstance(fileType, notableHashInBothCases, case1, dataSource1fromCase1, "path1",
                    "", TskData.FileKnown.BAD,
                    0L);
            CentralRepository.getInstance().addArtifactInstance(attr1);
            CorrelationAttributeInstance attr2 = new CorrelationAttributeInstance(fileType, notableHashInBothCases, case2, dataSource1fromCase2, "path2",
                    "", TskData.FileKnown.BAD,
                    0L);

            CentralRepository.getInstance().addArtifactInstance(attr2);

            List<CorrelationAttributeInstance> attrs = CentralRepository.getInstance().getArtifactInstancesByTypeValue(fileType, notableHashInBothCases);
            assertTrue("getArtifactInstancesByTypeValue returned " + attrs.size() + " values - expected 2", attrs.size() == 2);
            for (CorrelationAttributeInstance a : attrs) {
                assertTrue("Artifact did not have expected BAD status", a.getKnownStatus().equals(TskData.FileKnown.BAD));
            }
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Add two instances with one notable, one known
        try {
            CorrelationAttributeInstance attr1 = new CorrelationAttributeInstance(fileType, notableHashInOneCaseKnownOther, case1, dataSource1fromCase1, "path3",
                    "", TskData.FileKnown.BAD,
                    0L);

            CentralRepository.getInstance().addArtifactInstance(attr1);

            CorrelationAttributeInstance attr2 = new CorrelationAttributeInstance(fileType, notableHashInOneCaseKnownOther, case2, dataSource1fromCase2, "path4",
                    "", TskData.FileKnown.KNOWN,
                    0L);

            CentralRepository.getInstance().addArtifactInstance(attr2);

            List<CorrelationAttributeInstance> attrs = CentralRepository.getInstance().getArtifactInstancesByTypeValue(fileType, notableHashInOneCaseKnownOther);
            assertTrue("getArtifactInstancesByTypeValue returned " + attrs.size() + " values - expected 2", attrs.size() == 2);
            for (CorrelationAttributeInstance a : attrs) {
                if (case1.getCaseUUID().equals(a.getCorrelationCase().getCaseUUID())) {
                    assertTrue("Artifact did not have expected BAD status", a.getKnownStatus().equals(TskData.FileKnown.BAD));
                } else if (case2.getCaseUUID().equals(a.getCorrelationCase().getCaseUUID())) {
                    assertTrue("Artifact did not have expected KNOWN status", a.getKnownStatus().equals(TskData.FileKnown.KNOWN));
                } else {
                    fail("getArtifactInstancesByTypeValue returned unexpected case");
                }
            }
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Add an artifact and then update its status
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, hashToChangeToNotable, case1, dataSource1fromCase2, "path5",
                    "", TskData.FileKnown.KNOWN,
                    0L);

            CentralRepository.getInstance().addArtifactInstance(attr);

            CentralRepository.getInstance().setAttributeInstanceKnownStatus(attr, TskData.FileKnown.BAD);

            List<CorrelationAttributeInstance> attrs = CentralRepository.getInstance().getArtifactInstancesByTypeValue(fileType, hashToChangeToNotable);
            assertTrue("getArtifactInstancesByTypeValue returned " + attrs.size() + " values - expected 1", attrs.size() == 1);
            assertTrue("Artifact status did not change to BAD", attrs.get(0).getKnownStatus().equals(TskData.FileKnown.BAD));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Try to update artifact with two CorrelationAttributeInstance instances
        try {
            CorrelationAttributeInstance attr1 = new CorrelationAttributeInstance(fileType, randomHash(), case1, dataSource1fromCase1, BAD_PATH,
                    "", TskData.FileKnown.KNOWN,
                    0L);
            CorrelationAttributeInstance attr2 = new CorrelationAttributeInstance(fileType, randomHash(), case1, dataSource1fromCase2, BAD_PATH,
                    "", TskData.FileKnown.KNOWN,
                    0L);

            CentralRepository.getInstance().setAttributeInstanceKnownStatus(attr1, TskData.FileKnown.BAD);
            CentralRepository.getInstance().setAttributeInstanceKnownStatus(attr2, TskData.FileKnown.BAD);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Assert.fail("setArtifactInstanceKnownStatus threw an exception for sequential Correlation Attribute Instances updates");
        }

        // Try to update null artifact
        try {
            CentralRepository.getInstance().setAttributeInstanceKnownStatus(null, TskData.FileKnown.BAD);
            fail("setArtifactInstanceKnownStatus failed to throw exception for null correlation attribute");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        }

        // Try to update artifact with null known status
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, randomHash(), case1, dataSource1fromCase1, BAD_PATH,
                    "", TskData.FileKnown.KNOWN,
                    0L);

            CentralRepository.getInstance().setAttributeInstanceKnownStatus(attr, null);
            fail("setArtifactInstanceKnownStatus failed to throw exception for null known status");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Try to update artifact with null case
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, randomHash(), null, dataSource1fromCase1, BAD_PATH,
                    "", TskData.FileKnown.KNOWN,
                    0L);

            CentralRepository.getInstance().setAttributeInstanceKnownStatus(attr, TskData.FileKnown.BAD);
            fail("setArtifactInstanceKnownStatus failed to throw exception for null case");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Try to update artifact with null data source
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, randomHash(), case1, null, BAD_PATH,
                    "", TskData.FileKnown.KNOWN,
                    0L);

            CentralRepository.getInstance().setAttributeInstanceKnownStatus(attr, TskData.FileKnown.BAD);
            fail("setArtifactInstanceKnownStatus failed to throw exception for null case");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test getting count of two notable instances
        try {
            long count = CentralRepository.getInstance().getCountArtifactInstancesKnownBad(fileType, notableHashInBothCases);
            assertTrue("getCountArtifactInstancesKnownBad returned " + count + " values - expected 2", count == 2);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting notable instance count where one instance is notable and the other is known
        try {
            long count = CentralRepository.getInstance().getCountArtifactInstancesKnownBad(fileType, notableHashInOneCaseKnownOther);
            assertTrue("getCountArtifactInstancesKnownBad returned " + count + " values - expected 1", count == 1);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting notable instance count with null type
        try {
            CentralRepository.getInstance().getCountArtifactInstancesKnownBad(null, notableHashInOneCaseKnownOther);
            fail("getCountArtifactInstancesKnownBad failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException, got CorrelationAttributeNormalizationException: " + ex.getMessage());
        }

        // Test getting notable instance count with null value (should throw an exception)
        try {
            CentralRepository.getInstance().getCountArtifactInstancesKnownBad(fileType, null);
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException; received CorrelationAttributeNormalizationException: " + ex.getMessage());
        }

        // Test getting cases with notable instances (all instances are notable)
        try {
            List<String> cases = CentralRepository.getInstance().getListCasesHavingArtifactInstancesKnownBad(fileType, notableHashInBothCases);
            assertTrue("getListCasesHavingArtifactInstancesKnownBad returned " + cases.size() + " values - expected 2", cases.size() == 2);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting cases with notable instances (only one instance is notable)
        try {
            List<String> cases = CentralRepository.getInstance().getListCasesHavingArtifactInstancesKnownBad(fileType, notableHashInOneCaseKnownOther);
            assertTrue("getListCasesHavingArtifactInstancesKnownBad returned " + cases.size() + " values - expected 1", cases.size() == 1);
            assertTrue("getListCasesHavingArtifactInstancesKnownBad returned unexpected case " + cases.get(0), case1.getDisplayName().equals(cases.get(0)));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting cases with null type
        try {
            CentralRepository.getInstance().getListCasesHavingArtifactInstancesKnownBad(null, notableHashInOneCaseKnownOther);
            fail("getListCasesHavingArtifactInstancesKnownBad failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException instead of a CorrelationAttributeNormalizationException: " + ex.getMessage());
        }

        // Test getting cases with null value (should throw exception)
        try {
            List<String> cases = CentralRepository.getInstance().getListCasesHavingArtifactInstancesKnownBad(fileType, null);
            assertTrue("getListCasesHavingArtifactInstancesKnownBad returned " + cases.size() + " values - expected ", cases.isEmpty());
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException; received CorrelationAttributeNormalizationException: " + ex.getMessage());
        }
    }
    private static final String BAD_PATH = "badPath";
    private static final String FILE_PATH = "/";

    /**
     * Test the methods associated with bulk artifacts (addAttributeInstanceBulk
     * and commitAttributeInstancesBulk). First test the normal use case of a
     * large number of valid artifacts getting added. Next test the error
     * conditions: - Test preparing artifact with null type - Test preparing
     * artifact with null case - Test preparing artifact with null data source -
     * Test preparing artifact with null path - Test preparing artifact with
     * null known status
     */
    public void testBulkArtifacts() {

        // Test normal addition of bulk artifacts
        // Steps:
        // - Make a list of artifacts roughly half the threshold size
        // - Call addAttributeInstanceBulk on all of them
        // - Verify that nothing has been written to the database
        // - Make a list of artifacts equal to the threshold size
        // - Call addAttributeInstanceBulk on all of them
        // - Verify that the bulk threshold number of them were written to the database
        // - Call commitAttributeInstancesBulk to insert the remainder
        // - Verify that the database now has all the artifacts
        try {
            // Make sure there are no artifacts in the database to start
            long originalArtifactCount = CentralRepository.getInstance().getCountArtifactInstancesByCaseDataSource(dataSource1fromCase1);
            assertTrue("getCountArtifactInstancesByCaseDataSource returned non-zero count", originalArtifactCount == 0);

            // Create the first list, which will have (bulkThreshold / 2) entries
            List<CorrelationAttributeInstance> list1 = new ArrayList<>();
            for (int i = 0; i < DEFAULT_BULK_THRESHOLD / 2; i++) {
                String value = randomHash();
                String path = "C:\\bulkInsertPath1\\file" + String.valueOf(i);

                CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, value, case1, dataSource1fromCase1, path,
                        null, TskData.FileKnown.UNKNOWN, 0L);
                list1.add(attr);
            }

            // Queue up the current list. There should not be enough to trigger the insert
            for (CorrelationAttributeInstance attr : list1) {
                CentralRepository.getInstance().addAttributeInstanceBulk(attr);
            }

            // Check that nothing has been written yet
            assertTrue("Artifacts written to database before threshold was reached",
                    originalArtifactCount == CentralRepository.getInstance().getCountArtifactInstancesByCaseDataSource(dataSource1fromCase1));

            // Make a second list with length equal to bulkThreshold
            List<CorrelationAttributeInstance> list2 = new ArrayList<>();
            for (int i = 0; i < DEFAULT_BULK_THRESHOLD; i++) {
                String value = randomHash();
                String path = "C:\\bulkInsertPath2\\file" + String.valueOf(i);

                CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, value, case1, dataSource1fromCase1, path,
                        null, TskData.FileKnown.UNKNOWN, 0L);
                list2.add(attr);
            }

            // Queue up the current list. This will trigger an insert partway through
            for (CorrelationAttributeInstance attr : list2) {
                CentralRepository.getInstance().addAttributeInstanceBulk(attr);
            }

            // There should now be bulkThreshold artifacts in the database
            long count = CentralRepository.getInstance().getCountArtifactInstancesByCaseDataSource(dataSource1fromCase1);
            assertTrue("Artifact count " + count + " does not match bulkThreshold " + DEFAULT_BULK_THRESHOLD, count == DEFAULT_BULK_THRESHOLD);

            // Now call commitAttributeInstancesBulk() to insert the rest of queue
            CentralRepository.getInstance().commitAttributeInstancesBulk();
            count = CentralRepository.getInstance().getCountArtifactInstancesByCaseDataSource(dataSource1fromCase1);
            int expectedCount = list1.size() + list2.size();
            assertTrue("Artifact count " + count + " does not match expected count " + expectedCount, count == expectedCount);

        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test preparing artifact with null type
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(null, randomHash(), null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addAttributeInstanceBulk(attr);
            fail("prepareBulkArtifact failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException, got CorrelationAttributeNormalizationException: " + ex.getMessage());
        }

        // Test preparing artifact with null case
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, randomHash(), null, dataSource1fromCase1, "path",
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addAttributeInstanceBulk(attr);
            CentralRepository.getInstance().commitAttributeInstancesBulk();
            fail("bulkInsertArtifacts failed to throw exception for null case");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test preparing artifact with null data source
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, randomHash(), case1, null, "path",
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addAttributeInstanceBulk(attr);
            CentralRepository.getInstance().commitAttributeInstancesBulk();
            fail("prepareBulkArtifact failed to throw exception for null data source");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test preparing artifact with null path
        // CorrelationAttributeInstance will throw an exception
        try {
            new CorrelationAttributeInstance(fileType, randomHash(), case1, dataSource1fromCase1, null,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            fail("CorrelationAttributeInstance failed to throw exception for null path");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test preparing artifact with null known status
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, randomHash(), case1, dataSource1fromCase1, "path", "comment", null,
                    0L);
            CentralRepository.getInstance().addAttributeInstanceBulk(attr);
            CentralRepository.getInstance().commitAttributeInstancesBulk();
            fail("prepareBulkArtifact failed to throw exception for null known status");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
    }

    /**
     * Test most methods related to artifacts
     * addArtifactInstance(CorrelationAttribute eamArtifact) tests: - Test
     * adding artifact with one instance - Test adding artifact with one
     * instance in each data source - Test adding artifact with two instances in
     * the same data source - Test adding email artifact - Test adding phone
     * artifact - Test adding domain artifact - Test adding device artifact -
     * Test adding artifact with null case - Test adding artifact with invalid
     * case ID - Test adding artifact with null data source - Test adding
     * artifact with invalid data source ID - Test adding artifact with null
     * path - Test adding artifact with null known status - Test adding artifact
     * with null correlation type - Test adding artifact with null value
     * getArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type aType,
     * String value) tests: - Test getting three expected instances - Test
     * getting no expected instances - Test with null type - Test with null
     * value getArtifactInstancesByPath(CorrelationAttributeInstance.Type aType,
     * String filePath) tests: - Test with existing path - Test with
     * non-existent path - Test with null type - Test with null path
     * getCountArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type
     * aType, String value) tests: - Test getting three expected instances -
     * Test getting no expected instances - Test with null type - Test with null
     * value getFrequencyPercentage(CorrelationAttribute corAttr) tests: - Test
     * value in every data source - Test value in one data source twice - Test
     * email - Test value in no data sources - Test with null type - Test with
     * null attribute
     * getCountArtifactInstancesByCaseDataSource(CorrelationDataSource
     * correlationDataSource) tests: - Test data source with seven instances -
     * Test with null case UUID - Test with null device ID
     * getCountUniqueCaseDataSourceTuplesHavingTypeValue(CorrelationAttributeInstance.Type
     * aType, String value) tests: - Test value in every data source - Test
     * value in one data source twice - Test value in no data sources - Test
     * with null type - Test with null value
     */
    public void testArtifacts() {

        //the hash value of all 0s has not been inserted
        final String unusedHashValue = "00000000000000000000000000000000";

        String inAllDataSourcesHash = "6cddb0e31787b79cfdcc0676b98a71ce";
        String inAllDataSourcesPath = "C:\\files\\path0.txt";
        String inDataSource1twiceHash = "b2f5ff47436671b6e533d8dc3614845d";
        String inDataSource1twicePath1 = "C:\\files\\path1.txt";
        String inDataSource1twicePath2 = "C:\\files\\path2.txt";
        String onlyInDataSource3Hash = "2af54305f183778d87de0c70c591fae4";
        String onlyInDataSource3Path = "C:\\files\\path3.txt";
        String callbackTestFilePath1 = "C:\\files\\processinstancecallback\\path1.txt";
        String callbackTestFilePath2 = "C:\\files\\processinstancecallback\\path2.txt";
        String callbackTestFileHash = "fb9dd8f04dacd3e82f4917f1a002223c";

        // These will all go in dataSource1fromCase1
        String emailValue = "test@gmail.com";
        String emailPath = "C:\\files\\emailPath.txt";
        String phoneValue = "202-555-1234";
        String phonePath = "C:\\files\\phonePath.txt";
        String domainValue = "www.mozilla.com";
        String domainPath = "C:\\files\\domainPath.txt";
        String devIdValue = "94B21234";
        String devIdPath = "C:\\files\\devIdPath.txt";

        // Store the email type
        CorrelationAttributeInstance.Type emailType;  //used again for other portions of this test
        try {
            emailType = CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.EMAIL_TYPE_ID);
            assertEquals("Unexpected Correlation Type retrieved for Email type id", CorrelationAttributeInstance.EMAIL_TYPE_ID, emailType.getId());
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error thrown while attempting to get email attribute " + ex.getMessage());
            return;
        }

        // Test adding attribute with one instance
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, onlyInDataSource3Hash, case2, dataSource1fromCase2, onlyInDataSource3Path,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error thrown while attempting to add file attribute from single datasource the first time " + ex.getMessage());
        }

        // Test adding attribute with an instance in each data source
        try {
            CorrelationAttributeInstance attr1 = new CorrelationAttributeInstance(fileType, inAllDataSourcesHash, case1, dataSource1fromCase1, inAllDataSourcesPath,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr1);
            CorrelationAttributeInstance attr2 = new CorrelationAttributeInstance(fileType, inAllDataSourcesHash, case1, dataSource2fromCase1, inAllDataSourcesPath,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr2);
            CorrelationAttributeInstance attr3 = new CorrelationAttributeInstance(fileType, inAllDataSourcesHash, case2, dataSource1fromCase2, inAllDataSourcesPath,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr3);

        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error thrown while attempting to add file attribute from all 3 datasources " + ex.getMessage());
        }

        // Test adding attribute with two instances in one data source
        try {
            CorrelationAttributeInstance attr1 = new CorrelationAttributeInstance(fileType, inDataSource1twiceHash, case1, dataSource1fromCase1, inDataSource1twicePath1,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr1);
            CorrelationAttributeInstance attr2 = new CorrelationAttributeInstance(fileType, inDataSource1twiceHash, case1, dataSource1fromCase1, inDataSource1twicePath2,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr2);

        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error thrown while attempting to add file attribute from single datasource the second time " + ex.getMessage());
        }

        // Test adding the other types
        // Test adding an email artifact
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(emailType, emailValue, case1, dataSource1fromCase1, emailPath,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error thrown while attempting to add email attribute from single datasource " + ex.getMessage());
        }

        // Test adding a phone artifact
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(
                    CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.PHONE_TYPE_ID),
                    phoneValue,
                    case1, dataSource1fromCase1, phonePath,
                    null, TskData.FileKnown.UNKNOWN, 0L);

            CentralRepository.getInstance().addArtifactInstance(attr);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error thrown while attempting to add phone attribute from single datasource " + ex.getMessage());
        }

        // Test adding a domain artifact
        try {
            CorrelationAttributeInstance.Type type = CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.DOMAIN_TYPE_ID);
            assertEquals("Unexpected Correlation Type retrieved for Domain type id", CorrelationAttributeInstance.DOMAIN_TYPE_ID, type.getId());
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(
                    type,
                    domainValue,
                    case1, dataSource1fromCase1, domainPath,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error thrown while attempting to add domain attribute from single datasource " + ex.getMessage());
        }

        // Test adding a device ID artifact
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(
                    CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.USBID_TYPE_ID),
                    devIdValue,
                    case1, dataSource1fromCase1, devIdPath,
                    null, TskData.FileKnown.UNKNOWN, 0L);

            CentralRepository.getInstance().addArtifactInstance(attr);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error thrown while attempting to add device ID attribute from single datasource " + ex.getMessage());
        }

        // Test CorrelationAttributeInstance creation
        try {
            new CorrelationAttributeInstance(fileType, randomHash(),
                    null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
        } catch (CorrelationAttributeNormalizationException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Creating correlation attribute instance " + ex.getMessage());
        }

        // Test adding instance with null case
        try {
            CorrelationAttributeInstance failAttrInst = new CorrelationAttributeInstance(fileType, "badInstances", null, dataSource1fromCase2, BAD_PATH,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(failAttrInst);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown making and adding a CorrelationAttributeInstance with null case and was not");
        } catch (CentralRepoException ex) {
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown making and adding a CorrelationAttributeInstance with null case, EamDbException was thrown instead " + ex.getMessage());
        } catch (CorrelationAttributeNormalizationException ex) {
            // This is the expected behavior
        }

        // Test adding instance with invalid case ID
        try {
            CorrelationCase badCase = new CorrelationCase("badCaseUuid", "badCaseName");
            CorrelationAttributeInstance failAttrInst2 = new CorrelationAttributeInstance(fileType, randomHash(), badCase, dataSource1fromCase2, BAD_PATH,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(failAttrInst2);
            fail("Error EamDbException was expected to be thrown making and adding a CorrelationAttributeInstance with invalid case ID and was not");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            fail("Error EamDbException was expected to be thrown making and adding a CorrelationAttributeInstance with invalid case ID, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test adding instance with null data source
        try {
            CorrelationAttributeInstance failAttrInst3 = new CorrelationAttributeInstance(fileType, randomHash(), case1, null, BAD_PATH,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(failAttrInst3);
            fail("Error EamDbException was expected to be thrown making and adding a CorrelationAttributeInstance with null data source and was not");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            fail("Error EamDbException was expected to be thrown making and adding a CorrelationAttributeInstance with null data source, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test adding instance with invalid data source ID
        try {
            CorrelationDataSource badDS = new CorrelationDataSource(case1, "badDSUuid", "badDSName",
                    0L, null, null, null);
            CorrelationAttributeInstance failAttrInst4 = new CorrelationAttributeInstance(fileType, randomHash(), case1, badDS, BAD_PATH,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(failAttrInst4);
            fail("Error EamDbException was expected to be thrown making and adding a CorrelationAttributeInstance with invalid data source ID and was not");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            fail("Error EamDbException was expected to be thrown making and adding a CorrelationAttributeInstance with invalid data source ID, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test adding instance with null path
        // This will fail in the CorrelationAttributeInstance constructor
        try {
            new CorrelationAttributeInstance(fileType, randomHash(), case1, dataSource1fromCase1, null,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            fail("Error EamDbException was expected to be thrown making a CorrelationAttributeInstance with null path and was not");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            fail("Error EamDbException was expected to be thrown making a CorrelationAttributeInstance with null path, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test adding instance with null known status
        try {
            CorrelationAttributeInstance failAttrInst5 = new CorrelationAttributeInstance(fileType, "badInstances", case1, dataSource1fromCase1, null, "comment", null,
                    0L);
            CentralRepository.getInstance().addArtifactInstance(failAttrInst5);
            fail("Error EamDbException was expected to be thrown making and adding a CorrelationAttributeInstance with null known status and was not");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            fail("Error EamDbException was expected to be thrown making and adding a CorrelationAttributeInstance with null known status, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test CorrelationAttribute failure cases
        // Test null type
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(null, randomHash(),
                    null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().addArtifactInstance(attr);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown making and adding a CorrelationAttributeInstance with null type and was not");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CentralRepoException was expected to be thrown making and adding a CorrelationAttributeInstance with null type, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test null value
        // This will fail in the CorrelationAttribute constructor
        try {
            new CorrelationAttributeInstance(fileType, null,
                    null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown making a CorrelationAttributeInstance with null type and was not");
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CentralRepoException was expected to be thrown making a CorrelationAttributeInstance with null type, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        }

        // Test getting instances with expected results
        try {
            List<CorrelationAttributeInstance> instances = CentralRepository.getInstance().getArtifactInstancesByTypeValue(fileType, inAllDataSourcesHash);
            assertEquals("Unexpected number of fileType instances gotten by type value for hash that should be in all 3 data sources", 3, instances.size());

            // This test works because all the instances of this hash were set to the same path
            for (CorrelationAttributeInstance inst : instances) {
                assertTrue("getArtifactInstancesByTypeValue returned file instance with unexpected path " + inst.getFilePath() + " expected " + inAllDataSourcesPath,
                        inAllDataSourcesPath.equalsIgnoreCase(inst.getFilePath()));
            }
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error exception thrown while getting attributes by type value " + ex.getMessage());
        }

        // Test getting instances with mismatched data / data-type and expect an exception
        try {
            CentralRepository.getInstance().getArtifactInstancesByTypeValue(emailType, inAllDataSourcesHash);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown attempting to get email type attributes with a hash value");
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown attempting to get email type attributes with a hash value, EamDbException was thrown instead " + ex.getMessage());
        } catch (CorrelationAttributeNormalizationException ex) {
            //this is expected
        }

        // Test getting instances with null type
        try {
            CentralRepository.getInstance().getArtifactInstancesByTypeValue(null, inAllDataSourcesHash);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown attempting to get null type attributes with a hash value");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CentralRepoException was expected to be thrown attempting to get null type attributes with a hash value, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test getting instances with null value
        try {
            CentralRepository.getInstance().getArtifactInstancesByTypeValue(fileType, null);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown attempting to get file type attributes with a null value");
        } catch (CentralRepoException ex) {
            //this is expected
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CentralRepoExceptionx was expected to be thrown attempting to get file type attributes with a null value, CentralRepoException was thrown instead " + ex.getMessage());
        }

        // Test getting instance count with path that should produce results
        try {
            long count = CentralRepository.getInstance().getCountArtifactInstancesByTypeValue(fileType, inAllDataSourcesHash);
            assertEquals("Unexpected number of fileType instances retrieved when getting count of file type value for hash that should be in all 3 data sources", 3, count);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown while getting count of file type value for hash that should exist" + ex.getMessage());
        }

        // Test getting instance count with path that should not produce results
        try {
            long count = CentralRepository.getInstance().getCountArtifactInstancesByTypeValue(fileType, unusedHashValue);
            assertEquals("Unexpected number of fileType instances retrieved when getting count of file type value for hash that should not be in any data sources", 0, count);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown while getting count of file type value for hash that should not exist" + ex.getMessage());
        }

        // Test getting instance count with null type
        try {
            CentralRepository.getInstance().getCountArtifactInstancesByTypeValue(null, inAllDataSourcesHash);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown attempting to get count of null type attributes with a hash value");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CentralRepoException was expected to be thrown attempting to get count of null type attributes with a hash value, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test getting instance count with null value
        try {
            CentralRepository.getInstance().getCountArtifactInstancesByTypeValue(fileType, null);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown attempting to get count of file type attributes with a null hash value");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CentralRepoExceptionwas expected to be thrown attempting to get count of null type attributes with a null hash value, CorrelationAttributeNormalizationException  was thrown instead " + ex.getMessage());
        }

        // Test getting frequency of value that is in all three data sources
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, inAllDataSourcesHash,
                    null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
            int freq = CentralRepository.getInstance().getFrequencyPercentage(attr);
            assertEquals("Unexpected frequency value of file type returned for value that should exist in all data sources", 100, freq);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown while getting frequency of file type value for hash that should exist in all data sources" + ex.getMessage());
        }

        // Test getting frequency of value that appears twice in a single data source
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, inDataSource1twiceHash,
                    null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
            int freq = CentralRepository.getInstance().getFrequencyPercentage(attr);
            assertEquals("Unexpected frequency value of file type returned for value that should exist in one of three data sources", 33, freq);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown while getting frequency of file type value for hash that should exist in one of three data sources" + ex.getMessage());
        }

        // Test getting frequency of non-file type
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(emailType, emailValue,
                    null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
            int freq = CentralRepository.getInstance().getFrequencyPercentage(attr);
            assertEquals("Unexpected frequency value of email type returned for value that should exist in one of three data sources", 33, freq);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown while getting frequency of eamil type value for value that should exist in one of three data sources" + ex.getMessage());
        }

        // Test getting frequency of non-existent value
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(fileType, unusedHashValue,
                    null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
            int freq = CentralRepository.getInstance().getFrequencyPercentage(attr);
            assertEquals("Unexpected frequency value of file type returned for value that should not exist in any data sources", 0, freq);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown while getting frequency of eamil type value for value that should not exist in any data sources" + ex.getMessage());
        }

        // Test getting frequency with null type
        try {
            CorrelationAttributeInstance attr = new CorrelationAttributeInstance(null, "randomValue",
                    null, null, FILE_PATH, null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository.getInstance().getFrequencyPercentage(attr);
            fail("Error Exception was expected to be thrown when getting frequency of null type attribute");
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            // This is the expected behavior
        }

        // Test getting frequency with null attribute
        try {
            CentralRepository.getInstance().getFrequencyPercentage(null);
            fail("Error EamDbException was expected to be thrown when getting frequency of null attribute");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error EamDbException was expected to be thrown when getting frequency of null attribute, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
            fail(ex.getMessage());
        }

        // Test updating a correlation attribute instance comment
        try {
            String comment = "new comment";

            CorrelationAttributeInstance correlationAttribute = CentralRepository.getInstance().getCorrelationAttributeInstance(
                    usbDeviceType, case1, dataSource1fromCase1, devIdValue, devIdPath);
            assertNotNull("Correlation Attribute returned was null when it should not have been", correlationAttribute);

            correlationAttribute.setComment(comment);
            CentralRepository.getInstance().updateAttributeInstanceComment(correlationAttribute);

            // Get a fresh copy to verify the update.
            correlationAttribute = CentralRepository.getInstance().getCorrelationAttributeInstance(
                    usbDeviceType, case1, dataSource1fromCase1, devIdValue, devIdPath);
            assertEquals("Comment was not successfully set to expected value",
                    comment, correlationAttribute.getComment());
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown when setting and getting comment for attribute " + ex.getMessage());
        }

        // Test getting count for dataSource1fromCase1 (includes all types)
        try {
            long count = CentralRepository.getInstance().getCountArtifactInstancesByCaseDataSource(dataSource1fromCase1);
            assertEquals("Unexpected count of artifact instances retrieved when getting count for case 1, data source 1", 7, count);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error EamDbException thrown when getting count of artifact instances for case 1, data source 1" + ex.getMessage());
        }

        // Test getting data source count for entry that is in all three
        try {
            long count = CentralRepository.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(fileType, inAllDataSourcesHash);
            assertEquals("Unexpected count of data sources retrieved when getting count for file types with a hash that should exist in all three data sources", 3, count);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown when getting count of data sources for file types with a hash that should exist in all three data sources" + ex.getMessage());
        }

        // Test getting data source count for entry that is in one data source twice
        try {
            long count = CentralRepository.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(fileType, inDataSource1twiceHash);
            assertEquals("Unexpected count of data sources retrieved when getting count for file types with a hash that should exist in a single data source twice", 1, count);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown when getting count of data sources for file types with a hash that should exist in a single data source twice" + ex.getMessage());
        }

        // Test getting data source count for entry that is not in any data sources
        try {
            long count = CentralRepository.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(fileType, unusedHashValue);
            assertEquals("Unexpected count of data sources retrieved when getting count for file types with a hash that should not exist in any data source", 0, count);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown when getting count of data sources for file types with a hash that should not exist in any data source" + ex.getMessage());
        }

        // Test getting data source count for null type
        try {
            CentralRepository.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(null, unusedHashValue);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown when getting number of datasources containing null type attribute");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CentralRepoException was expected to be thrown when getting number of datasources containing null type attribute, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test getting data source count for null value
        try {
            CentralRepository.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(fileType, null);
            fail("Error CorrelationAttributeNormalizationException was expected to be thrown when getting number of datasources containing file type attribute with null hash");
        } catch (CentralRepoException ex) {
            //this is expected
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Error CentralRepoException was expected to be thrown when getting number of datasources containing file type attribute with null hash, CorrelationAttributeNormalizationException was thrown instead " + ex.getMessage());
        }

        // Test running processinstance which queries all rows from instances table
        try {
            // Add two instances to the central repository and use the callback query to verify we can see them
            CorrelationAttributeInstance attr1 = new CorrelationAttributeInstance(fileType, callbackTestFileHash, case1, dataSource1fromCase1, callbackTestFilePath1,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CorrelationAttributeInstance attr2 = new CorrelationAttributeInstance(fileType, callbackTestFileHash, case1, dataSource1fromCase1, callbackTestFilePath2,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository DbManager = CentralRepository.getInstance();
            DbManager.addArtifactInstance(attr1);
            DbManager.addArtifactInstance(attr2);
            AttributeInstanceTableCallback instancetableCallback = new AttributeInstanceTableCallback();
            DbManager.processInstanceTable(fileType, instancetableCallback);
            int count1 = instancetableCallback.getCounter();
            int count2 = instancetableCallback.getCounterNamingConvention();
            //expects 2 rows to match the naming convention to of been processed, expects at least one row not matching the naming convention to be processed
            //if the test code is changed to add additional Correlation Attributes which also have "processinstancecallback" in their path the first of these comparisons will need to change
            assertEquals("Counter for items matching naming convention from AttributeInstaceTableCallback indicates an unexepected number of results when processed with DbManager.processInstanceTable", 2, count2);
            assertTrue("Counter for items which do not match naming convention from AttributeInstaceTableCallback indicates an unexepected number of results when processed with DbManager.processInstanceTable. Count indicated: " + count1 + " - expected a number greater than 0", count1 > 0);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown when calling processInstanceTable " + ex.getMessage());
        }

        try {
            //test null inputs
            CentralRepository.getInstance().processInstanceTable(null, null);
            fail("Error EamDbException was expected to be thrown when calling processInstanceTable with null inputs");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        }

        // Test running processinstance which queries all rows from instances table
        try {
            // Add two instances to the central repository and use the callback query to verify we can see them
            CorrelationAttributeInstance attr1 = new CorrelationAttributeInstance(fileType, callbackTestFileHash, case1, dataSource1fromCase1, callbackTestFilePath1,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CorrelationAttributeInstance attr2 = new CorrelationAttributeInstance(fileType, callbackTestFileHash, case1, dataSource1fromCase1, callbackTestFilePath2,
                    null, TskData.FileKnown.UNKNOWN, 0L);
            CentralRepository DbManager = CentralRepository.getInstance();
            //these redundant addArtifactInstance calls allow code to be rearranged if necessary
            DbManager.addArtifactInstance(attr1);
            DbManager.addArtifactInstance(attr2);
            AttributeInstanceTableCallback instancetableCallback = new AttributeInstanceTableCallback();
            DbManager.processInstanceTableWhere(fileType, "value='" + callbackTestFileHash + "'", instancetableCallback);
            int count1 = instancetableCallback.getCounter();
            //naming convention counts 
            int count2 = instancetableCallback.getCounterNamingConvention();
            //this has only processed the rows where the value is equal to the specified value, which should only be the two rows with the naming convention checked for
            //if the test code is changed to add additional Correlation Attributes with the same callbackTestFileHash value that is used here these comparisons will need to change
            assertEquals("Counter for items matching naming convention from AttributeInstaceTableCallback indicates an unexepected number of results when processed with DbManager.processInstanceTableWhere", 2, count2);
            assertEquals("Counter for items which do not match naming convention from AttributeInstaceTableCallback indicates an unexepected number of results when processed with DbManager.processInstanceTableWhere", 0, count1);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("Error Exception thrown when calling processInstanceTableWhere " + ex.getMessage());
        }
        try {
            //test null inputs
            CentralRepository.getInstance().processInstanceTableWhere(null, null, null);
            fail("Error EamDbException was expected to be thrown when calling processInstanceTableWhere with null inputs");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        }
    }

    /**
     * Test methods related to correlation types
     * newCorrelationType(CorrelationAttributeInstance.Type newType) tests: -
     * Test with valid data - Test with duplicate data - Test with null name -
     * Test with null db name - Test with null type getDefinedCorrelationTypes()
     * tests: - Test that the expected number are returned
     * getEnabledCorrelationTypes() tests: - Test that the expected number are
     * returned getSupportedCorrelationTypes() tests: - Test that the expected
     * number are returned getCorrelationTypeById(int typeId) tests: - Test with
     * valid ID - Test with invalid ID
     * updateCorrelationType(CorrelationAttributeInstance.Type aType) tests: -
     * Test with existing type - Test with non-existent type - Test updating to
     * null name - Test with null type
     */
    public void testCorrelationTypes() {

        CorrelationAttributeInstance.Type customType;
        String customTypeName = "customType";
        String customTypeDb = "custom_type";

        // Test new type with valid data
        try {
            customType = new CorrelationAttributeInstance.Type(customTypeName, customTypeDb, false, false);
            customType.setId(CentralRepository.getInstance().newCorrelationType(customType));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test new type with duplicate data
        try {
            CorrelationAttributeInstance.Type temp = new CorrelationAttributeInstance.Type(customTypeName, customTypeDb, false, false);
            CentralRepository.getInstance().newCorrelationType(temp);
            fail("newCorrelationType failed to throw exception for duplicate name/db table");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test new type with null name
        try {
            CorrelationAttributeInstance.Type temp = new CorrelationAttributeInstance.Type(null, "temp_type", false, false);
            CentralRepository.getInstance().newCorrelationType(temp);
            fail("newCorrelationType failed to throw exception for null name table");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test new type with null db name
        // The constructor should fail in this case
        try {
            new CorrelationAttributeInstance.Type("temp", null, false, false);
            Assert.fail("CorrelationAttributeInstance.Type failed to throw exception for null db table name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test new type with null type
        try {
            CentralRepository.getInstance().newCorrelationType(null);
            fail("newCorrelationType failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test getting all correlation types
        try {
            List<CorrelationAttributeInstance.Type> types = CentralRepository.getInstance().getDefinedCorrelationTypes();

            // We expect 11 total - 10 default and the custom one made earlier
            // Note: this test will need to be updated based on the current default items defined in the correlation_types table
            assertTrue("getDefinedCorrelationTypes returned " + types.size() + " entries - expected 30", types.size() == 30);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting enabled correlation types
        try {
            List<CorrelationAttributeInstance.Type> types = CentralRepository.getInstance().getEnabledCorrelationTypes();

            // We expect 10 - the custom type is disabled
            // Note: this test will need to be updated based on the current default items defined in the correlation_types table
            assertTrue("getDefinedCorrelationTypes returned " + types.size() + " enabled entries - expected 29", types.size() == 29);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting supported correlation types
        try {
            List<CorrelationAttributeInstance.Type> types = CentralRepository.getInstance().getSupportedCorrelationTypes();

            // We expect 10 - the custom type is not supported
            // Note: this test will need to be updated based on the current default items defined in the correlation_types table
            assertTrue("getDefinedCorrelationTypes returned " + types.size() + " supported entries - expected 29", types.size() == 29);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting the type with a valid ID
        try {
            CorrelationAttributeInstance.Type temp = CentralRepository.getInstance().getCorrelationTypeById(customType.getId());
            assertTrue("getCorrelationTypeById returned type with unexpected name " + temp.getDisplayName(), customTypeName.equals(temp.getDisplayName()));
            assertTrue("getCorrelationTypeById returned type with unexpected db table name " + temp.getDbTableName(), customTypeDb.equals(temp.getDbTableName()));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting the type with a invalid ID
        try {
            CentralRepository.getInstance().getCorrelationTypeById(5555);
            fail("getCorrelationTypeById failed to throw exception for invalid ID");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test updating a valid type
        try {
            String newName = "newName";
            String newDbTable = "new_db_table";
            customType.setDisplayName(newName);
            customType.setDbTableName(newDbTable);
            customType.setEnabled(true); // These were originally false
            customType.setSupported(true);

            CentralRepository.getInstance().updateCorrelationType(customType);

            // Get a fresh copy from the database
            CorrelationAttributeInstance.Type temp = CentralRepository.getInstance().getCorrelationTypeById(customType.getId());

            assertTrue("updateCorrelationType failed to update name", newName.equals(temp.getDisplayName()));
            assertTrue("updateCorrelationType failed to update db table name", newDbTable.equals(temp.getDbTableName()));
            assertTrue("updateCorrelationType failed to update enabled status", temp.isEnabled());
            assertTrue("updateCorrelationType failed to update supported status", temp.isSupported());
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test updating a type with an invalid ID
        // Nothing should happen
        try {
            CorrelationAttributeInstance.Type temp = new CorrelationAttributeInstance.Type(customTypeName, customTypeDb, false, false);
            temp.setId(12345);
            CentralRepository.getInstance().updateCorrelationType(temp);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test updating a type to a null name
        try {
            customType.setDisplayName(null);
            CentralRepository.getInstance().updateCorrelationType(customType);
            fail("updateCorrelationType failed to throw exception for null name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test updating a null type
        try {
            customType.setDisplayName(null);
            CentralRepository.getInstance().updateCorrelationType(customType);
            fail("updateCorrelationType failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }
    }

    /**
     * Test the methods related to organizations newOrganization(EamOrganization
     * eamOrg) tests: - Test with just org name - Test with org name and poc
     * info - Test adding duplicate org - Test adding null org - Test adding org
     * with null name getOrganizations() tests: - Test getting the list of orgs
     * getOrganizationByID(int orgID) tests: - Test with valid ID - Test with
     * invalid ID updateOrganization(EamOrganization updatedOrganization) tests:
     * - Test updating valid org - Test updating invalid org - Test updating
     * null org - Test updating org to null name
     * deleteOrganization(EamOrganization organizationToDelete) tests: - Test
     * deleting org that isn't in use - Test deleting org that is in use - Test
     * deleting invalid org - Test deleting null org
     */
    public void testOrganizations() {

        CentralRepoOrganization orgA;
        String orgAname = "orgA";
        CentralRepoOrganization orgB;
        String orgBname = "orgB";
        String orgBpocName = "pocName";
        String orgBpocEmail = "pocEmail";
        String orgBpocPhone = "pocPhone";

        // Test adding a basic organization
        try {
            orgA = new CentralRepoOrganization(orgAname);
            orgA = CentralRepository.getInstance().newOrganization(orgA);
            assertTrue("Organization ID is still -1 after adding to db", orgA.getOrgID() != -1);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test adding an organization with additional fields
        try {
            orgB = new CentralRepoOrganization(orgBname, orgBpocName, orgBpocEmail, orgBpocPhone);
            orgB = CentralRepository.getInstance().newOrganization(orgB);
            assertTrue("Organization ID is still -1 after adding to db", orgB.getOrgID() != -1);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test adding a duplicate organization
        try {
            CentralRepoOrganization temp = new CentralRepoOrganization(orgAname);
            CentralRepository.getInstance().newOrganization(temp);
            fail("newOrganization failed to throw exception for duplicate org name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test adding null organization
        try {
            CentralRepository.getInstance().newOrganization(null);
            fail("newOrganization failed to throw exception for null org");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test adding organization with null name
        try {
            CentralRepoOrganization temp = new CentralRepoOrganization(null);
            CentralRepository.getInstance().newOrganization(temp);
            fail("newOrganization failed to throw exception for null name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test getting organizations
        // We expect five - the default org, two from setUp, and two from this method
        try {
            List<CentralRepoOrganization> orgs = CentralRepository.getInstance().getOrganizations();
            assertTrue("getOrganizations returned null list", orgs != null);
            assertTrue("getOrganizations returned " + orgs.size() + " orgs - expected 5", orgs.size() == 5);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting org with valid ID
        try {
            CentralRepoOrganization temp = CentralRepository.getInstance().getOrganizationByID(orgB.getOrgID());
            assertTrue("getOrganizationByID returned null for valid ID", temp != null);
            assertTrue("getOrganizationByID returned unexpected name for organization", orgBname.equals(temp.getName()));
            assertTrue("getOrganizationByID returned unexpected poc name for organization", orgBpocName.equals(temp.getPocName()));
            assertTrue("getOrganizationByID returned unexpected poc email for organization", orgBpocEmail.equals(temp.getPocEmail()));
            assertTrue("getOrganizationByID returned unexpected poc phone for organization", orgBpocPhone.equals(temp.getPocPhone()));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting org with invalid ID
        try {
            CentralRepository.getInstance().getOrganizationByID(12345);
            fail("getOrganizationByID failed to throw exception for invalid ID");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test updating valid org
        try {
            String newName = "newOrgName";
            String newPocName = "newPocName";
            String newPocEmail = "newPocEmail";
            String newPocPhone = "newPocPhone";
            orgA.setName(newName);
            orgA.setPocName(newPocName);
            orgA.setPocEmail(newPocEmail);
            orgA.setPocPhone(newPocPhone);

            CentralRepository.getInstance().updateOrganization(orgA);

            CentralRepoOrganization copyOfA = CentralRepository.getInstance().getOrganizationByID(orgA.getOrgID());

            assertTrue("getOrganizationByID returned null for valid ID", copyOfA != null);
            assertTrue("updateOrganization failed to update org name", newName.equals(copyOfA.getName()));
            assertTrue("updateOrganization failed to update poc name", newPocName.equals(copyOfA.getPocName()));
            assertTrue("updateOrganization failed to update poc email", newPocEmail.equals(copyOfA.getPocEmail()));
            assertTrue("updateOrganization failed to update poc phone", newPocPhone.equals(copyOfA.getPocPhone()));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test updating invalid org
        try {
            CentralRepoOrganization temp = new CentralRepoOrganization("invalidOrg");
            CentralRepository.getInstance().updateOrganization(temp);
            fail("updateOrganization worked for invalid ID");
        } catch (CentralRepoException ex) {
            // this is the expected behavior  
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test updating null org
        try {
            CentralRepository.getInstance().updateOrganization(null);
            fail("updateOrganization failed to throw exception for null org");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test updating org to null name
        try {
            CentralRepoOrganization copyOfA = CentralRepository.getInstance().getOrganizationByID(orgA.getOrgID());
            copyOfA.setName(null);
            CentralRepository.getInstance().updateOrganization(copyOfA);
            fail("updateOrganization failed to throw exception for null name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test deleting existing org that isn't in use
        try {
            CentralRepoOrganization orgToDelete = new CentralRepoOrganization("deleteThis");
            orgToDelete = CentralRepository.getInstance().newOrganization(orgToDelete);
            int orgCount = CentralRepository.getInstance().getOrganizations().size();

            CentralRepository.getInstance().deleteOrganization(orgToDelete);
            assertTrue("getOrganizations returned unexpected count after deletion", orgCount - 1 == CentralRepository.getInstance().getOrganizations().size());
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test deleting existing org that is in use
        try {
            // Make a new org
            CentralRepoOrganization inUseOrg = new CentralRepoOrganization("inUseOrg");
            inUseOrg = CentralRepository.getInstance().newOrganization(inUseOrg);

            // Make a reference set that uses it
            CentralRepoFileSet tempSet = new CentralRepoFileSet(inUseOrg.getOrgID(), "inUseOrgTest", "1.0", TskData.FileKnown.BAD, false, fileType);
            CentralRepository.getInstance().newReferenceSet(tempSet);

            // It should now throw an exception if we try to delete it
            CentralRepository.getInstance().deleteOrganization(inUseOrg);
            fail("deleteOrganization failed to throw exception for in use organization");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test deleting non-existent org
        try {
            CentralRepoOrganization temp = new CentralRepoOrganization("temp");
            CentralRepository.getInstance().deleteOrganization(temp);
            fail("deleteOrganization failed to throw exception for non-existent organization");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test deleting null org
        try {
            CentralRepository.getInstance().deleteOrganization(null);
            fail("deleteOrganization failed to throw exception for null organization");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }
    }

    /**
     * Tests for adding / retrieving reference instances Only the files type is
     * currently implemented addReferenceInstance(EamGlobalFileInstance
     * eamGlobalFileInstance, CorrelationAttributeInstance.Type correlationType)
     * tests: - Test adding multiple valid entries - Test invalid reference set
     * ID - Test null hash (EamGlobalFileInstance constructor) - Test null known
     * status (EamGlobalFileInstance constructor) - Test null correlation type
     * bulkInsertReferenceTypeEntries(Set<EamGlobalFileInstance>
     * globalInstances, CorrelationAttributeInstance.Type contentType) tests: -
     * Test with large valid list - Test with null list - Test with invalid
     * reference set ID - Test with null correlation type
     * getReferenceInstancesByTypeValue(CorrelationAttributeInstance.Type aType,
     * String aValue) tests: - Test with valid entries - Test with non-existent
     * value - Test with invalid type - Test with null type - Test with null
     * value isFileHashInReferenceSet(String hash, int referenceSetID)tests: -
     * Test existing hash/ID - Test non-existent (but valid) hash/ID - Test
     * invalid ID - Test null hash isValueInReferenceSet(String value, int
     * referenceSetID, int correlationTypeID) tests: - Test existing value/ID -
     * Test non-existent (but valid) value/ID - Test invalid ID - Test null
     * value - Test invalid type ID
     * isArtifactKnownBadByReference(CorrelationAttributeInstance.Type aType,
     * String value) tests: - Test notable value - Test known value - Test
     * non-existent value - Test null value - Test null type - Test invalid type
     */
    public void testReferenceSetInstances() {

        // After the two initial testing blocks, the reference sets should contain:
        // notableSet1 - notableHash1, inAllSetsHash
        // notableSet2 - inAllSetsHash
        // knownSet1 - knownHash1, inAllSetsHash
        CentralRepoFileSet notableSet1;
        int notableSet1id;
        CentralRepoFileSet notableSet2;
        int notableSet2id;
        CentralRepoFileSet knownSet1;
        int knownSet1id;

        String notableHash1 = "d46feecd663c41648dbf690d9343cf4b";
        String knownHash1 = "39c844daee70485143da4ff926601b5b";
        String inAllSetsHash = "6449b39bb23c42879fa0c243726e27f7";

        CorrelationAttributeInstance.Type emailType;

        // Store the email type object for later use
        try {
            emailType = CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.EMAIL_TYPE_ID);
            assertTrue("getCorrelationTypeById(EMAIL_TYPE_ID) returned null", emailType != null);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Set up a few reference sets
        try {
            notableSet1 = new CentralRepoFileSet(org1.getOrgID(), "notable set 1", "1.0", TskData.FileKnown.BAD, false, fileType);
            notableSet1id = CentralRepository.getInstance().newReferenceSet(notableSet1);
            notableSet2 = new CentralRepoFileSet(org1.getOrgID(), "notable set 2", "2.4", TskData.FileKnown.BAD, false, fileType);
            notableSet2id = CentralRepository.getInstance().newReferenceSet(notableSet2);
            knownSet1 = new CentralRepoFileSet(org1.getOrgID(), "known set 1", "5.5.4", TskData.FileKnown.KNOWN, false, fileType);
            knownSet1id = CentralRepository.getInstance().newReferenceSet(knownSet1);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test adding file instances with valid data
        try {
            CentralRepoFileInstance temp = new CentralRepoFileInstance(notableSet1id, inAllSetsHash, TskData.FileKnown.BAD, "comment1");
            CentralRepository.getInstance().addReferenceInstance(temp, fileType);

            temp = new CentralRepoFileInstance(notableSet2id, inAllSetsHash, TskData.FileKnown.BAD, "comment2");
            CentralRepository.getInstance().addReferenceInstance(temp, fileType);

            temp = new CentralRepoFileInstance(knownSet1id, inAllSetsHash, TskData.FileKnown.KNOWN, "comment3");
            CentralRepository.getInstance().addReferenceInstance(temp, fileType);

            temp = new CentralRepoFileInstance(notableSet1id, notableHash1, TskData.FileKnown.BAD, "comment4");
            CentralRepository.getInstance().addReferenceInstance(temp, fileType);

            temp = new CentralRepoFileInstance(knownSet1id, knownHash1, TskData.FileKnown.KNOWN, "comment5");
            CentralRepository.getInstance().addReferenceInstance(temp, fileType);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test adding file instance with invalid reference set ID
        try {
            CentralRepoFileInstance temp = new CentralRepoFileInstance(2345, inAllSetsHash, TskData.FileKnown.BAD, "comment");
            CentralRepository.getInstance().addReferenceInstance(temp, fileType);
            fail("addReferenceInstance failed to throw exception for invalid ID");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test creating file instance with null hash
        // Since it isn't possible to get a null hash into the EamGlobalFileInstance, skip trying to
        // call addReferenceInstance and just test the EamGlobalFileInstance constructor
        try {
            new CentralRepoFileInstance(notableSet1id, null, TskData.FileKnown.BAD, "comment");
            fail("EamGlobalFileInstance failed to throw exception for null hash");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException; received CorrelationAttributeNormalizationException: " + ex.getMessage());
        }

        // Test adding file instance with null known status
        // Since it isn't possible to get a null known status into the EamGlobalFileInstance, skip trying to
        // call addReferenceInstance and just test the EamGlobalFileInstance constructor
        try {
            new CentralRepoFileInstance(notableSet1id, inAllSetsHash, null, "comment");
            fail("EamGlobalFileInstance failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test adding file instance with null correlation type
        try {
            CentralRepoFileInstance temp = new CentralRepoFileInstance(notableSet1id, inAllSetsHash, TskData.FileKnown.BAD, "comment");
            CentralRepository.getInstance().addReferenceInstance(temp, null);
            fail("addReferenceInstance failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test bulk insert with large valid set
        try {
            // Create a list of global file instances. Make enough that the bulk threshold should be hit once.
            Set<CentralRepoFileInstance> instances = new HashSet<>();
            for (int i = 0; i < DEFAULT_BULK_THRESHOLD * 1.5; i++) {
                String hash = randomHash();
                instances.add(new CentralRepoFileInstance(notableSet2id, hash, TskData.FileKnown.BAD, null));
            }

            // Insert the list
            CentralRepository.getInstance().bulkInsertReferenceTypeEntries(instances, fileType);

            // There's no way to get a count of the number of entries in the database, so just do a spot check
            if (DEFAULT_BULK_THRESHOLD > 10) {
                String hash = instances.stream().findFirst().get().getMD5Hash();
                assertTrue("Sample bulk insert instance not found", CentralRepository.getInstance().isFileHashInReferenceSet(hash, notableSet2id));
            }
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test bulk add file instance with null list
        try {
            CentralRepository.getInstance().bulkInsertReferenceTypeEntries(null, fileType);
            fail("bulkInsertReferenceTypeEntries failed to throw exception for null list");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test bulk add file instance with invalid reference set ID
        try {
            Set<CentralRepoFileInstance> tempSet = new HashSet<>(Arrays.asList(new CentralRepoFileInstance(2345, inAllSetsHash, TskData.FileKnown.BAD, "comment")));
            CentralRepository.getInstance().bulkInsertReferenceTypeEntries(tempSet, fileType);
            fail("bulkInsertReferenceTypeEntries failed to throw exception for invalid ID");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test bulk add file instance with null correlation type
        try {
            Set<CentralRepoFileInstance> tempSet = new HashSet<>(Arrays.asList(new CentralRepoFileInstance(notableSet1id, inAllSetsHash, TskData.FileKnown.BAD, "comment")));
            CentralRepository.getInstance().bulkInsertReferenceTypeEntries(tempSet, null);
            fail("bulkInsertReferenceTypeEntries failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }

        // Test getting reference instances with valid data
        try {
            List<CentralRepoFileInstance> temp = CentralRepository.getInstance().getReferenceInstancesByTypeValue(fileType, inAllSetsHash);
            assertTrue("getReferenceInstancesByTypeValue returned " + temp.size() + " instances - expected 3", temp.size() == 3);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting reference instances with non-existent data
        try {
            List<CentralRepoFileInstance> temp = CentralRepository.getInstance().getReferenceInstancesByTypeValue(fileType, randomHash());
            assertTrue("getReferenceInstancesByTypeValue returned " + temp.size() + " instances for non-existent value - expected 0", temp.isEmpty());
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting reference instances an invalid type (the email table is not yet implemented)
        try {
            CentralRepository.getInstance().getReferenceInstancesByTypeValue(emailType, inAllSetsHash);
            fail("getReferenceInstancesByTypeValue failed to throw exception for invalid table");
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test getting reference instances with null type
        try {
            CentralRepository.getInstance().getReferenceInstancesByTypeValue(null, inAllSetsHash);
            fail("getReferenceInstancesByTypeValue failed to throw exception for null type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException; received CorrelationAttributeNormalizationException: " + ex.getMessage());
        }

        // Test getting reference instances with null value
        try {
            List<CentralRepoFileInstance> temp = CentralRepository.getInstance().getReferenceInstancesByTypeValue(fileType, null);
            fail("we should get an exception here");
        } catch (CentralRepoException ex) {
            //this is expected
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException; received CorrelationAttributeNormalizationException: " + ex.getMessage());
        }

        // Test checking existing hash/ID
        try {
            assertTrue("isFileHashInReferenceSet returned false for valid data", CentralRepository.getInstance().isFileHashInReferenceSet(knownHash1, knownSet1id));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test checking non-existent (but valid) hash/ID
        try {
            assertFalse("isFileHashInReferenceSet returned true for non-existent data", CentralRepository.getInstance().isFileHashInReferenceSet(knownHash1, notableSet1id));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test checking invalid reference set ID
        try {
            assertFalse("isFileHashInReferenceSet returned true for invalid data", CentralRepository.getInstance().isFileHashInReferenceSet(knownHash1, 5678));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test checking null hash
        try {
            CentralRepository.getInstance().isFileHashInReferenceSet(null, knownSet1id);
            fail("This should throw an exception");
        } catch (CentralRepoException ex) {
            //this is expected
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("Expected CentralRepoException; received CorrelationAttributeNormalizationException: " + ex.getMessage());
        }

        // Test checking existing hash/ID
        try {
            assertTrue("isValueInReferenceSet returned false for valid data",
                    CentralRepository.getInstance().isValueInReferenceSet(knownHash1, knownSet1id, fileType.getId()));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test checking non-existent (but valid) hash/ID
        try {
            assertFalse("isValueInReferenceSet returned true for non-existent data",
                    CentralRepository.getInstance().isValueInReferenceSet(knownHash1, notableSet1id, fileType.getId()));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test checking invalid reference set ID
        try {
            assertFalse("isValueInReferenceSet returned true for invalid data",
                    CentralRepository.getInstance().isValueInReferenceSet(knownHash1, 5678, fileType.getId()));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test checking null hash
        try {
            CentralRepository.getInstance().isValueInReferenceSet(null, knownSet1id, fileType.getId());
            fail("we should get an exception here");
        } catch (CentralRepoException ex) {
            //this is expected
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("CentralRepoException expected; CorrelationAttributeNormalizationException received: " + ex.getMessage());
        }

        // Test checking invalid type
        try {
            CentralRepository.getInstance().isValueInReferenceSet(knownHash1, knownSet1id, emailType.getId());
            fail("isValueInReferenceSet failed to throw exception for invalid type");
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        } catch (CorrelationAttributeNormalizationException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test known bad with notable data
        try {
            assertTrue("isArtifactKnownBadByReference returned false for notable value",
                    CentralRepository.getInstance().isArtifactKnownBadByReference(fileType, notableHash1));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test known bad with known data
        try {
            assertFalse("isArtifactKnownBadByReference returned true for known value",
                    CentralRepository.getInstance().isArtifactKnownBadByReference(fileType, knownHash1));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test known bad with non-existent data
        try {
            assertFalse("isArtifactKnownBadByReference returned true for non-existent value",
                    CentralRepository.getInstance().isArtifactKnownBadByReference(fileType, randomHash()));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test known bad with null hash
        try {
            CentralRepository.getInstance().isArtifactKnownBadByReference(fileType, null);
            fail("we should have thrown an exception");
        } catch (CentralRepoException ex) {
            //this is expected
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("CentralRepoException expected; CorrelationAttributeNormalizationException received: " + ex.getMessage());
        }

        // Test known bad with null type
        try {
            CentralRepository.getInstance().isArtifactKnownBadByReference(null, knownHash1);
            fail("isArtifactKnownBadByReference failed to throw exception from null type");
        } catch (CentralRepoException ex) {
            //this is expected
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("CentralRepoException expected; CorrelationAttributeNormalizationException received: " + ex.getMessage());
        }

        // Test known bad with invalid type
        try {
            CentralRepository.getInstance().isArtifactKnownBadByReference(emailType, null);
            fail("should get an exception here");
        } catch (CentralRepoException ex) {
            //this is expected
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        } catch (CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail("CentralRepoException expected; CorrelationAttributeNormalizationException received: " + ex.getMessage());
        }
    }

    /**
     * Test method for the methods related to reference sets (does not include
     * instance testing) Only the files type is currently implemented
     * newReferenceSet(EamGlobalSet eamGlobalSet) tests: - Test creating notable
     * reference set - Test creating known reference set - Test creating
     * duplicate reference set - Test creating almost duplicate reference set -
     * Test with invalid org ID - Test with null name - Test with null version -
     * Test with null known status - Test with null file type
     * referenceSetIsValid(int referenceSetID, String referenceSetName, String
     * version) tests: - Test on existing reference set - Test on invalid
     * reference set - Test with null name - Test with null version
     * referenceSetExists(String referenceSetName, String version) tests: - Test
     * on existing reference set - Test on invalid reference set - Test with
     * null name - Test with null version getReferenceSetByID(int globalSetID)
     * tests: - Test with valid ID - Test with invalid ID
     * getAllReferenceSets(CorrelationAttributeInstance.Type correlationType)
     * tests: - Test getting all file sets - Test getting all email sets - Test
     * with null type parameter deleteReferenceSet(int referenceSetID) tests: -
     * Test on valid reference set ID - Test on invalid reference set ID
     * getReferenceSetOrganization(int referenceSetID) tests: - Test on valid
     * reference set ID - Test on invalid reference set ID
     */
    public void testReferenceSets() {
        String set1name = "referenceSet1";
        String set1version = "1.0";
        CentralRepoFileSet set1;
        int set1id;
        String set2name = "referenceSet2";
        CentralRepoFileSet set2;
        CentralRepoFileSet set3;

        // Test creating a notable reference set
        try {
            set1 = new CentralRepoFileSet(org1.getOrgID(), set1name, set1version, TskData.FileKnown.BAD, false, fileType);
            set1id = CentralRepository.getInstance().newReferenceSet(set1);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test creating a known reference set
        try {
            set2 = new CentralRepoFileSet(org2.getOrgID(), set2name, "", TskData.FileKnown.KNOWN, false, fileType);
            CentralRepository.getInstance().newReferenceSet(set2);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test creating a reference set with the same name and version
        try {
            CentralRepoFileSet temp = new CentralRepoFileSet(org1.getOrgID(), set1name, "1.0", TskData.FileKnown.BAD, false, fileType);
            CentralRepository.getInstance().newReferenceSet(temp);
            fail("newReferenceSet failed to throw exception from duplicate name/version pair");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test creating a reference set with the same name but different version
        try {
            set3 = new CentralRepoFileSet(org1.getOrgID(), set1name, "2.0", TskData.FileKnown.BAD, false, fileType);
            CentralRepository.getInstance().newReferenceSet(set3);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test creating a reference set with invalid org ID
        try {
            CentralRepoFileSet temp = new CentralRepoFileSet(5000, "tempName", "", TskData.FileKnown.BAD, false, fileType);
            CentralRepository.getInstance().newReferenceSet(temp);
            fail("newReferenceSet failed to throw exception from invalid org ID");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test creating a reference set with null name
        try {
            CentralRepoFileSet temp = new CentralRepoFileSet(org2.getOrgID(), null, "", TskData.FileKnown.BAD, false, fileType);
            CentralRepository.getInstance().newReferenceSet(temp);
            fail("newReferenceSet failed to throw exception from null name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test creating a reference set with null version
        try {
            CentralRepoFileSet temp = new CentralRepoFileSet(org2.getOrgID(), "tempName", null, TskData.FileKnown.BAD, false, fileType);
            CentralRepository.getInstance().newReferenceSet(temp);
            fail("newReferenceSet failed to throw exception from null version");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test creating a reference set with null file known status
        try {
            CentralRepoFileSet temp = new CentralRepoFileSet(org2.getOrgID(), "tempName", "", null, false, fileType);
            CentralRepository.getInstance().newReferenceSet(temp);
            fail("newReferenceSet failed to throw exception from null file known status");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test creating a reference set with null file type
        try {
            CentralRepoFileSet temp = new CentralRepoFileSet(org2.getOrgID(), "tempName", "", TskData.FileKnown.BAD, false, null);
            CentralRepository.getInstance().newReferenceSet(temp);
            fail("newReferenceSet failed to throw exception from null file type");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        }

        // Test validation with a valid reference set
        try {
            assertTrue("referenceSetIsValid returned false for valid reference set", CentralRepository.getInstance().referenceSetIsValid(set1id, set1name, set1version));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test validation with an invalid reference set
        try {
            assertFalse("referenceSetIsValid returned true for invalid reference set", CentralRepository.getInstance().referenceSetIsValid(5000, set1name, set1version));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test validation with a null name
        try {
            assertFalse("referenceSetIsValid returned true with null name", CentralRepository.getInstance().referenceSetIsValid(set1id, null, set1version));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test validation with a null version
        try {
            assertFalse("referenceSetIsValid returned true with null version", CentralRepository.getInstance().referenceSetIsValid(set1id, set1name, null));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test existence with a valid reference set
        try {
            assertTrue("referenceSetExists returned false for valid reference set", CentralRepository.getInstance().referenceSetExists(set1name, set1version));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test existence with an invalid reference set
        try {
            assertFalse("referenceSetExists returned true for invalid reference set", CentralRepository.getInstance().referenceSetExists(set1name, "5.5"));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test existence with null name
        try {
            assertFalse("referenceSetExists returned true for null name", CentralRepository.getInstance().referenceSetExists(null, "1.0"));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test existence with null version
        try {
            assertFalse("referenceSetExists returned true for null version", CentralRepository.getInstance().referenceSetExists(set1name, null));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting global set with valid ID
        try {
            CentralRepoFileSet temp = CentralRepository.getInstance().getReferenceSetByID(set1id);
            assertTrue("getReferenceSetByID returned null for valid ID", temp != null);
            assertTrue("getReferenceSetByID returned set with incorrect name and/or version",
                    set1name.equals(temp.getSetName()) && set1version.equals(temp.getVersion()));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting global set with invalid ID
        try {
            CentralRepoFileSet temp = CentralRepository.getInstance().getReferenceSetByID(1234);
            assertTrue("getReferenceSetByID returned non-null result for invalid ID", temp == null);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting all file reference sets
        try {
            List<CentralRepoFileSet> referenceSets = CentralRepository.getInstance().getAllReferenceSets(fileType);
            assertTrue("getAllReferenceSets(FILES) returned unexpected number", referenceSets.size() == 3);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting all email reference sets
        try {
            List<CentralRepoFileSet> referenceSets = CentralRepository.getInstance().getAllReferenceSets(CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.EMAIL_TYPE_ID));
            assertTrue("getAllReferenceSets(EMAIL) returned unexpected number", referenceSets.isEmpty());
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test null argument to getAllReferenceSets
        try {
            CentralRepository.getInstance().getAllReferenceSets(null);
            fail("getAllReferenceSets failed to throw exception from null type argument");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test deleting an existing reference set
        // First: create a new reference set, check that it's in the database, and get the number of reference sets
        // Second: delete the reference set, check that it is no longer in the database, and the total number of sets decreased by one
        try {
            CentralRepoFileSet setToDelete = new CentralRepoFileSet(org1.getOrgID(), "deleteThis", "deleteThisVersion", TskData.FileKnown.BAD, false, fileType);
            int setToDeleteID = CentralRepository.getInstance().newReferenceSet(setToDelete);
            assertTrue("setToDelete wasn't found in database", CentralRepository.getInstance().referenceSetIsValid(setToDeleteID, setToDelete.getSetName(), setToDelete.getVersion()));
            int currentCount = CentralRepository.getInstance().getAllReferenceSets(fileType).size();

            CentralRepository.getInstance().deleteReferenceSet(setToDeleteID);
            assertFalse("Deleted reference set was found in database", CentralRepository.getInstance().referenceSetIsValid(setToDeleteID, setToDelete.getSetName(), setToDelete.getVersion()));
            assertTrue("Unexpected number of reference sets in database after deletion", currentCount - 1 == CentralRepository.getInstance().getAllReferenceSets(fileType).size());

        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test deleting a non-existent reference set
        // The expectation is that nothing will happen
        try {
            int currentCount = CentralRepository.getInstance().getAllReferenceSets(fileType).size();
            CentralRepository.getInstance().deleteReferenceSet(1234);
            assertTrue("Number of reference sets changed after deleting non-existent set", currentCount == CentralRepository.getInstance().getAllReferenceSets(fileType).size());
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting reference set organization for valid ID with org set
        try {
            CentralRepoOrganization org = CentralRepository.getInstance().getReferenceSetOrganization(set1id);
            assertTrue("getReferenceSetOrganization returned null for valid set", org != null);
            assertTrue("getReferenceSetOrganization returned the incorrect organization", org.getOrgID() == org1.getOrgID());
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting reference set organization for non-existent reference set
        try {
            CentralRepository.getInstance().getReferenceSetOrganization(4567);
            fail("getReferenceSetOrganization failed to throw exception for invalid reference set ID");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }
    }

    /**
     * Test method for the methods related to the data sources table
     * newDataSource(CorrelationDataSource eamDataSource) tests: - Test with
     * valid data - Test with duplicate data - Test with duplicate device ID and
     * name but different case - Test with invalid case ID - Test with null
     * device ID - Test with null name getDataSource(CorrelationCase
     * correlationCase, String dataSourceDeviceId) tests: - Test with valid data
     * - Test with non-existent data - Test with null correlationCase - Test
     * with null device ID getDataSources()tests: - Test that the count and
     * device IDs are as expected getCountUniqueDataSources() tests: - Test that
     * the result is as expected
     */
    public void testDataSources() {
        final String dataSourceAname = "dataSourceA";
        final String dataSourceAid = "dataSourceA_deviceID";
        CorrelationDataSource dataSourceA;
        CorrelationDataSource dataSourceB;

        // Test creating a data source with valid case, name, and ID
        try {
            dataSourceA = new CorrelationDataSource(case2, dataSourceAid, dataSourceAname,
                    0L, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSourceA);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test creating a data source with the same case, name, and ID
        try {
            CorrelationDataSource temp = new CorrelationDataSource(case2, dataSourceAid, dataSourceAname,
                    0L, null, null, null);
            CentralRepository.getInstance().newDataSource(temp);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }

        // Test creating a data source with the same name and ID but different case
        try {
            dataSourceB = new CorrelationDataSource(case1, dataSourceAid, dataSourceAname,
                    0L, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSourceB);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
            return;
        }
        
        // Test creating a data source with an invalid case ID
        try {
            CorrelationCase correlationCase = new CorrelationCase("1", "test");
            CorrelationDataSource temp = new CorrelationDataSource(correlationCase, "tempID", "tempName",
                    0L, null, null, null);
            CentralRepository.getInstance().newDataSource(temp);
            fail("newDataSource did not throw exception from invalid case ID");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }
        
        // Test creating a data source with null device ID
        try {
            CorrelationDataSource temp = new CorrelationDataSource(case2, null, "tempName",
                    0L, null, null, null);
            CentralRepository.getInstance().newDataSource(temp);
            fail("newDataSource did not throw exception from null device ID");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

         // Test creating a data source with null name
        try {
            CorrelationDataSource temp = new CorrelationDataSource(case2, "tempID", null,
                    0L, null, null, null);
            CentralRepository.getInstance().newDataSource(temp);
            fail("newDataSource did not throw exception from null name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test getting a data source with valid case and ID
        try {
            CorrelationDataSource temp = CentralRepository.getInstance().getDataSource(case2, 0L);
            assertTrue("Failed to get data source", temp != null);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting a data source with non-existent ID
        try {
            CorrelationDataSource temp = CentralRepository.getInstance().getDataSource(case2, 9999L);
            assertTrue("getDataSource returned non-null value for non-existent data source", temp == null);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting a data source with a null case
        try {
            CentralRepository.getInstance().getDataSource(null, 0L);
            fail("getDataSource did not throw exception from null case");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Test getting a data source with an invalid ID
        try {
            CorrelationDataSource temp = CentralRepository.getInstance().getDataSource(case2, -1L);
            assertTrue("getDataSource returned non-null value for null data source", temp == null);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test getting the list of data sources
        // There should be five data sources, and we'll check for the expected device IDs
        try {
            List<CorrelationDataSource> dataSources = CentralRepository.getInstance().getDataSources();
            List<String> devIdList
                    = dataSources.stream().map(c -> c.getDeviceID()).collect(Collectors.toList());
            assertTrue("getDataSources returned unexpected number of data sources", dataSources.size() == 5);
            assertTrue("getDataSources is missing expected data sources",
                    devIdList.contains(dataSourceAid)
                    && devIdList.contains(dataSource1fromCase1.getDeviceID())
                    && devIdList.contains(dataSource2fromCase1.getDeviceID())
                    && devIdList.contains(dataSource1fromCase2.getDeviceID()));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test the data source count
        try {
            assertTrue("getCountUniqueDataSources returned unexpected number of data sources",
                    CentralRepository.getInstance().getCountUniqueDataSources() == 5);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Test method for the methods related to the cases table
     * newCase(CorrelationCase eamCase) tests: - Test valid data - Test null
     * UUID - Test null case name - Test repeated UUID newCase(Case autopsyCase)
     * tests: - Test valid data - Test null autopsyCase
     * updateCase(CorrelationCase eamCase) tests: - Test with valid data,
     * checking all fields - Test null eamCase getCase(Case autopsyCase) tests:
     * - Test with current Autopsy case getCaseByUUID(String caseUUID) - Test
     * with UUID that is in the database - Test with UUID that is not in the
     * database - Test with null UUID getCases() tests: - Test getting all
     * cases, checking the count and fields
     * bulkInsertCases(List<CorrelationCase> cases) - Test on a list of cases
     * larger than the bulk insert threshold. - Test on a null list
     */
    public void testCases() {
        final String caseAname = "caseA";
        final String caseAuuid = "caseA_uuid";
        CorrelationCase caseA;
        CorrelationCase caseB;

        try {
            // Set up an Autopsy case for testing
            try {
                Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, testDirectory.toString(), new CaseDetails("CentralRepoDatamodelTestCase"));
            } catch (CaseActionException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }
            assertTrue("Failed to create test case", testDirectory.toFile().exists());

            // Test creating a case with valid name and uuid
            try {
                caseA = new CorrelationCase(caseAuuid, caseAname);
                caseA = CentralRepository.getInstance().newCase(caseA);
                assertTrue("Failed to create case", caseA != null);
            } catch (CentralRepoException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
                return;
            }

            // Test null uuid
            try {
                CorrelationCase tempCase = new CorrelationCase(null, "nullUuidCase");
                CentralRepository.getInstance().newCase(tempCase);
                fail("newCase did not throw expected exception from null uuid");
            } catch (CentralRepoException ex) {
                // This is the expected behavior
                assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
            }

            // Test null name
            try {
                CorrelationCase tempCase = new CorrelationCase("nullCaseUuid", null);
                CentralRepository.getInstance().newCase(tempCase);
                fail("newCase did not throw expected exception from null name");
            } catch (CentralRepoException ex) {
                // This is the expected behavior
                assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
            }

            // Test creating a case with an already used UUID
            // This should just return the existing case object. Check that the total 
            // number of cases does not increase.
            try {
                int nCases = CentralRepository.getInstance().getCases().size();
                CorrelationCase tempCase = new CorrelationCase(caseAuuid, "newCaseWithSameUUID");
                tempCase = CentralRepository.getInstance().newCase(tempCase);
                assertTrue("newCase returned null for existing UUID", tempCase != null);
                assertTrue("newCase created a new case for an already existing UUID", nCases == CentralRepository.getInstance().getCases().size());
            } catch (CentralRepoException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }

            // Test creating a case from an Autopsy case
            // The case may already be in the database - the result is the same either way
            try {
                caseB = CentralRepository.getInstance().newCase(Case.getCurrentCaseThrows());
                assertTrue("Failed to create correlation case from Autopsy case", caseB != null);
            } catch (CentralRepoException | NoCurrentCaseException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
                return;
            }

            // Test null Autopsy case
            try {
                Case nullCase = null;
                CentralRepository.getInstance().newCase(nullCase);
                fail("newCase did not throw expected exception from null case");
            } catch (CentralRepoException ex) {
                // This is the expected behavior
                assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
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

                CentralRepository.getInstance().updateCase(caseA);

                // Retrievex a new copy of the case from the database to check that the 
                // fields were properly updated
                CorrelationCase updatedCase = CentralRepository.getInstance().getCaseByUUID(caseA.getCaseUUID());

                assertTrue("updateCase failed to update case number", caseNumber.equals(updatedCase.getCaseNumber()));
                assertTrue("updateCase failed to update creation date", creationDate.equals(updatedCase.getCreationDate()));
                assertTrue("updateCase failed to update display name", displayName.equals(updatedCase.getDisplayName()));
                assertTrue("updateCase failed to update examiner email", examinerEmail.equals(updatedCase.getExaminerEmail()));
                assertTrue("updateCase failed to update examiner name", examinerName.equals(updatedCase.getExaminerName()));
                assertTrue("updateCase failed to update examiner phone number", examinerPhone.equals(updatedCase.getExaminerPhone()));
                assertTrue("updateCase failed to update notes", notes.equals(updatedCase.getNotes()));
                assertTrue("updateCase failed to update org (org is null)", updatedCase.getOrg() != null);
                assertTrue("updateCase failed to update org (org ID is wrong)", org1.getOrgID() == updatedCase.getOrg().getOrgID());
            } catch (CentralRepoException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }

            // Test update case with null case
            try {
                CentralRepository.getInstance().updateCase(null);
                fail("updateCase did not throw expected exception from null case");
            } catch (CentralRepoException ex) {
                // This is the expected behavior
                assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
            }

            // Test getting a case from an Autopsy case
            try {
                CorrelationCase tempCase = CentralRepository.getInstance().getCase(Case.getCurrentCaseThrows());
                assertTrue("getCase returned null for current Autopsy case", tempCase != null);
            } catch (CentralRepoException | NoCurrentCaseException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }

            // Test getting a case by UUID
            try {
                CorrelationCase tempCase = CentralRepository.getInstance().getCaseByUUID(caseAuuid);
                assertTrue("Failed to get case by UUID", tempCase != null);
            } catch (CentralRepoException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }

            // Test getting a case with a non-existent UUID
            try {
                CorrelationCase tempCase = CentralRepository.getInstance().getCaseByUUID("badUUID");
                assertTrue("getCaseByUUID returned non-null case for non-existent UUID", tempCase == null);
            } catch (CentralRepoException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }

            // Test getting the list of cases
            // The test is to make sure the three cases we know are in the database are in the list
            try {
                List<CorrelationCase> caseList = CentralRepository.getInstance().getCases();
                List<String> uuidList
                        = caseList.stream().map(c -> c.getCaseUUID()).collect(Collectors.toList());
                assertTrue("getCases is missing data for existing cases", uuidList.contains(case1.getCaseUUID())
                        && uuidList.contains(case2.getCaseUUID()) && (uuidList.contains(caseA.getCaseUUID()))
                        && uuidList.contains(caseB.getCaseUUID()));
            } catch (CentralRepoException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }

            // Test bulk case insert
            try {
                // Create a list of correlation cases. Make enough that the bulk threshold should be hit once.
                List<CorrelationCase> cases = new ArrayList<>();
                String bulkTestUuid = "bulkTestUUID_";
                String bulkTestName = "bulkTestName_";
                for (int i = 0; i < DEFAULT_BULK_THRESHOLD * 1.5; i++) {
                    String name = bulkTestUuid + String.valueOf(i);
                    String uuid = bulkTestName + String.valueOf(i);
                    cases.add(new CorrelationCase(uuid, name));
                }

                // Get the current case count
                int nCases = CentralRepository.getInstance().getCases().size();

                // Insert the big list of cases
                CentralRepository.getInstance().bulkInsertCases(cases);

                // Check that the case count is what is expected
                assertTrue("bulkInsertCases did not insert the expected number of cases", nCases + cases.size() == CentralRepository.getInstance().getCases().size());
            } catch (CentralRepoException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }

            // Test bulk case insert with null list
            try {
                CentralRepository.getInstance().bulkInsertCases(null);
                fail("bulkInsertCases did not throw expected exception from null list");
            } catch (CentralRepoException ex) {
                // This is the expected behavior
                assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
            }
        } finally {
            try {
                Case.closeCurrentCase();
                // This seems to help in allowing the Autopsy case to be deleted
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {

                }
            } catch (CaseActionException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }
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
            CentralRepository.getInstance().newDbInfo(name1, value1);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Test null name
        try {
            CentralRepository.getInstance().newDbInfo(null, value1);
            fail("newDbInfo did not throw expected exception from null name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
        }

        // Test null value
        try {
            CentralRepository.getInstance().newDbInfo(name2, null);
            fail("newDbInfo did not throw expected exception from null value");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Try getting the dbInfo entry that should exist
        try {
            String tempVal = CentralRepository.getInstance().getDbInfo(name1);
            assertTrue("dbInfo value for name1 does not match", value1.equals(tempVal));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Try getting the dbInfo entry that should not exist
        try {
            String tempVal = CentralRepository.getInstance().getDbInfo(name3);
            assertTrue("dbInfo value is unexpectedly non-null given non-existent name", tempVal == null);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Try getting dbInfo for a null value
        try {
            String tempVal = CentralRepository.getInstance().getDbInfo(null);
            assertTrue("dbInfo value is unexpectedly non-null given null name", tempVal == null);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Try updating an existing value to a valid new value
        try {
            CentralRepository.getInstance().updateDbInfo(name1, value2);
            assertTrue("dbInfo value failed to update to expected value", value2.equals(CentralRepository.getInstance().getDbInfo(name1)));
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Try updating an existing value to null
        try {
            CentralRepository.getInstance().updateDbInfo(name1, null);
            fail("updateDbInfo did not throw expected exception from null value");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }

        // Try updating a null name
        // This seems like SQLite would throw an exception, but it does not 
        try {
            CentralRepository.getInstance().updateDbInfo(null, value1);
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        // Try updating the value for a non-existant name
        try {
            CentralRepository.getInstance().updateDbInfo(name1, null);
            fail("updateDbInfo did not throw expected exception from non-existent name");
        } catch (CentralRepoException ex) {
            // This is the expected behavior
            assertTrue(THIS_IS_THE_EXPECTED_BEHAVIOR, true);
        }
    }
    private static final String THIS_IS_THE_EXPECTED_BEHAVIOR = "This is the expected behavior.";

    private static String randomHash() {

        String[] chars = {"a", "b", "c", "d", "e", "f", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

        Random random = new Random();
        IntStream ints = random.ints(32, 0, chars.length - 1);

        Iterator<Integer> it = ints.iterator();

        StringBuilder md5 = new StringBuilder(32);

        while (it.hasNext()) {
            Integer i = it.next();
            String character = chars[i];
            md5.append(character);
        }

        return md5.toString();
    }

    public class AttributeInstanceTableCallback implements InstanceTableCallback {

        int counterNamingConvention = 0;
        int counter = 0;

        @Override
        public void process(ResultSet resultSet) {
            try {
                while (resultSet.next()) {
                    if (InstanceTableCallback.getFilePath(resultSet).toLowerCase().contains("processinstancecallback")) {
                        counterNamingConvention++;
                    } else {
                        counter++;
                    }
                }
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        public int getCounter() {
            return counter;
        }

        public int getCounterNamingConvention() {
            return counterNamingConvention;
        }
    }
}
