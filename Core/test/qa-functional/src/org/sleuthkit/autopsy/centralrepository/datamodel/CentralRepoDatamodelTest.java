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
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
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
import org.sleuthkit.datamodel.TskData;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 *
 */
public class CentralRepoDatamodelTest extends TestCase {

    private static final String PROPERTIES_FILE = "CentralRepository";
    private static final String CR_DB_NAME = "testcentralrepo.db";
    //private static final Path testDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "CentralRepoDatamodelTest");
    private static final Path testDirectory = Paths.get("C:", "Work", "CRDatamodelTest");
    SqliteEamDbSettings dbSettingsSqlite;

    private CorrelationCase case1;
    private CorrelationCase case2;
    private CorrelationDataSource dataSource1fromCase1;
    private CorrelationDataSource dataSource2fromCase1;
    private EamOrganization org1;
    private EamOrganization org2;
    CorrelationAttribute.Type fileType;

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
            
            // Store the file type object for later use
            fileType = EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            assertTrue("getCorrelationTypeById(FILES_TYPE_ID) returned null", fileType != null);

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
                        
            FileUtils.deleteDirectory(testDirectory.toFile());

        } catch (EamDbException | IOException ex) {
        //    } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        assertFalse("Error deleting test directory " + testDirectory.toString(), testDirectory.toFile().exists());
    }
    
    /**
     *  newCorrelationType(CorrelationAttribute.Type newType)
getDefinedCorrelationTypes()
getEnabledCorrelationTypes()
getSupportedCorrelationTypes()
* getCorrelationTypeById(int typeId)
updateCorrelationType(CorrelationAttribute.Type aType)
     */
    public void testCorrelationTypes() {
        
        CorrelationAttribute.Type customType;
        
        // Test new type with valid data
        try{
            customType = new CorrelationAttribute.Type("customType", "custom_type", false, false);
            customType.setId(EamDb.getInstance().newCorrelationType(customType));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
        
        // Test new type with duplicate data
        try{
            CorrelationAttribute.Type temp = new CorrelationAttribute.Type("customType", "custom_type", false, false);
            EamDb.getInstance().newCorrelationType(temp);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test new type with null name
        try{
            CorrelationAttribute.Type temp = new CorrelationAttribute.Type(null, "temp_type", false, false);
            EamDb.getInstance().newCorrelationType(temp);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test new type with null db name
        try{
            CorrelationAttribute.Type temp = new CorrelationAttribute.Type("temp", null, false, false);
            EamDb.getInstance().newCorrelationType(temp);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
    
    /**
     * Test the methods related to organizations
     * newOrganization(EamOrganization eamOrg) tests:
     * - Test with just org name
     * - Test with org name and poc info
     * - Test adding duplicate org
     * - Test adding null org
     * - Test adding org with null name
    * getOrganizations() tests:
    * - Test getting the list of orgs
* getOrganizationByID(int orgID) tests:
* - Test with valid ID
* - Test with invalid ID
* updateOrganization(EamOrganization updatedOrganization) tests:
* - Test updating valid org
* - Test updating invalid org
* - Test updating null org
* - Test updating org to null name
* deleteOrganization(EamOrganization organizationToDelete) tests:
* - Test deleting org that isn't in use
* - Test deleting org that is in use
* - Test deleting invalid org
* - Test deleting null org
     */
    public void testOrganizations() {
        
        EamOrganization orgA;
        String orgAname = "orgA";
        EamOrganization orgB;
        String orgBname = "orgB";
        String orgBpocName = "pocName";
        String orgBpocEmail = "pocEmail";
        String orgBpocPhone = "pocPhone";
        
        // Test adding a basic organization
        try{ 
            orgA = new EamOrganization(orgAname);
            orgA.setOrgID((int) EamDb.getInstance().newOrganization(orgA));
            assertTrue("Organization ID is still -1 after adding to db", orgA.getOrgID() != -1);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
        
        // Test adding an organization with additional fields
        try{ 
            orgB = new EamOrganization(orgBname, orgBpocName, orgBpocEmail, orgBpocPhone);
            orgB.setOrgID((int) EamDb.getInstance().newOrganization(orgB));
            assertTrue("Organization ID is still -1 after adding to db", orgB.getOrgID() != -1);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
        
        // Test adding a duplicate organization
        try{
            EamOrganization temp = new EamOrganization(orgAname);
            EamDb.getInstance().newOrganization(temp);
            Assert.fail("newOrganization failed to throw exception for duplicate org name");
        } catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test adding null organization
        try{
            EamDb.getInstance().newOrganization(null);
            Assert.fail("newOrganization failed to throw exception for null org");
        } catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test adding organization with null name
        try{
            EamOrganization temp = new EamOrganization(null);
            EamDb.getInstance().newOrganization(temp);
            Assert.fail("newOrganization failed to throw exception for null name");
        } catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test getting organizations
        // We expect five - the default org, two from setUp, and two from this method
        try{ 
            List<EamOrganization> orgs = EamDb.getInstance().getOrganizations();
            assertTrue("getOrganizations returned null list", orgs != null);
            assertTrue("getOrganizations returned " + orgs.size() + " orgs - expected 5", orgs.size() == 5);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test getting org with valid ID
        try{ 
            EamOrganization temp = EamDb.getInstance().getOrganizationByID(orgB.getOrgID());
            assertTrue("getOrganizationByID returned null for valid ID", temp != null);
            assertTrue("getOrganizationByID returned unexpected name for organization", orgBname.equals(temp.getName()));
            assertTrue("getOrganizationByID returned unexpected poc name for organization", orgBpocName.equals(temp.getPocName()));
            assertTrue("getOrganizationByID returned unexpected poc email for organization", orgBpocEmail.equals(temp.getPocEmail()));
            assertTrue("getOrganizationByID returned unexpected poc phone for organization", orgBpocPhone.equals(temp.getPocPhone()));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test getting org with invalid ID
        try{ 
            EamOrganization temp = EamDb.getInstance().getOrganizationByID(12345);
            Assert.fail("getOrganizationByID failed to throw exception for invalid ID");
        } catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test updating valid org
        try{ 
            String newName = "newOrgName";
            String newPocName = "newPocName";
            String newPocEmail = "newPocEmail";
            String newPocPhone = "newPocPhone";
            orgA.setName(newName);
            orgA.setPocName(newPocName);
            orgA.setPocEmail(newPocEmail);
            orgA.setPocPhone(newPocPhone);
            
            EamDb.getInstance().updateOrganization(orgA);
            
            EamOrganization copyOfA = EamDb.getInstance().getOrganizationByID(orgA.getOrgID());
            
            assertTrue("getOrganizationByID returned null for valid ID", copyOfA != null);
            assertTrue("updateOrganization failed to update org name", newName.equals(copyOfA.getName()));
            assertTrue("updateOrganization failed to update poc name", newPocName.equals(copyOfA.getPocName()));
            assertTrue("updateOrganization failed to update poc email", newPocEmail.equals(copyOfA.getPocEmail()));
            assertTrue("updateOrganization failed to update poc phone", newPocPhone.equals(copyOfA.getPocPhone()));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test updating invalid org
        // Shouldn't do anything
        try{ 
            EamOrganization temp = new EamOrganization("invalidOrg");
            temp.setOrgID(3434);
            EamDb.getInstance().updateOrganization(temp);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test updating null org
        try{ 
            EamDb.getInstance().updateOrganization(null);
            Assert.fail("updateOrganization failed to throw exception for null org");
        } catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test updating org to null name
        try{ 
            EamOrganization copyOfA = EamDb.getInstance().getOrganizationByID(orgA.getOrgID());
            copyOfA.setName(null);
            EamDb.getInstance().updateOrganization(copyOfA);
            Assert.fail("updateOrganization failed to throw exception for null name");
        } catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test deleting existing org that isn't in use
        try{ 
            EamOrganization orgToDelete = new EamOrganization("deleteThis");
            orgToDelete.setOrgID((int)EamDb.getInstance().newOrganization(orgToDelete));
            int orgCount = EamDb.getInstance().getOrganizations().size();
            
            EamDb.getInstance().deleteOrganization(orgToDelete);
            assertTrue("getOrganizations returned unexpected count after deletion", orgCount - 1 == EamDb.getInstance().getOrganizations().size());
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test deleting existing org that is in use
        try{ 
            // Make a new org
            EamOrganization inUseOrg = new EamOrganization("inUseOrg");
            inUseOrg.setOrgID((int)EamDb.getInstance().newOrganization(inUseOrg));
            
            // Make a reference set that uses it
            EamGlobalSet tempSet = new EamGlobalSet(inUseOrg.getOrgID(), "inUseOrgTest", "1.0", TskData.FileKnown.BAD, false, fileType);
            EamDb.getInstance().newReferenceSet(tempSet);
            
            // It should now throw an exception if we try to delete it
            EamDb.getInstance().deleteOrganization(inUseOrg);
            Assert.fail("deleteOrganization failed to throw exception for in use organization");
        } catch (EamDbException ex){
            // This is the expected behavior
        }        
        
        // Test deleting non-existent org
        // Should do nothing
        try{ 
            EamOrganization temp = new EamOrganization("temp");
            temp.setOrgID(9876);
            EamDb.getInstance().deleteOrganization(temp);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }        
        
        // Test deleting null org
        try{ 
            EamDb.getInstance().deleteOrganization(null);
            Assert.fail("deleteOrganization failed to throw exception for null organization");
        } catch (EamDbException ex){
            // This is the expected behavior
        } 
    }
    
    /**
     * Tests for adding / retrieving reference instances
     * Only the files type is currently implemented
     * addReferenceInstance(EamGlobalFileInstance eamGlobalFileInstance, CorrelationAttribute.Type correlationType) tests:
     * - Test adding multiple valid entries
     * - Test invalid reference set ID
     * - Test null hash (EamGlobalFileInstance constructor)
     * - Test null known status (EamGlobalFileInstance constructor)
     * - Test null correlation type
    * bulkInsertReferenceTypeEntries(Set<EamGlobalFileInstance> globalInstances,  CorrelationAttribute.Type contentType) tests:
    *  - Test with large valid list
    *  - Test with null list
    *  - Test with invalid reference set ID
    *  - Test with null correlation type
    * getReferenceInstancesByTypeValue(CorrelationAttribute.Type aType, String aValue) tests: 
    *  - Test with valid entries
    *  - Test with non-existent value
    *  - Test with invalid type
    *  - Test with null type
    *  - Test with null value
    * isFileHashInReferenceSet(String hash, int referenceSetID)tests:
    *  - Test existing hash/ID
    *  - Test non-existent (but valid) hash/ID
    *  - Test invalid ID
    *  - Test null hash
    * isValueInReferenceSet(String value, int referenceSetID, int correlationTypeID) tests:
    *  - Test existing value/ID
    *  - Test non-existent (but valid) value/ID
    *  - Test invalid ID
    *  - Test null value
    *  - Test invalid type ID
    * isArtifactKnownBadByReference(CorrelationAttribute.Type aType, String value) tests:
    *  - Test notable value
    *  - Test known value
    *  - Test non-existent value
    *  - Test null value
    *  - Test null type
    *  - Test invalid type
     */
    public void testReferenceSetInstances(){
        
        // After the two initial testing blocks, the reference sets should contain:
        // notableSet1 - notableHash1, inAllSetsHash
        // notableSet2 - inAllSetsHash
        // knownSet1 - knownHash1, inAllSetsHash
        EamGlobalSet notableSet1;
        int notableSet1id;
        EamGlobalSet notableSet2;
        int notableSet2id;
        EamGlobalSet knownSet1;
        int knownSet1id;
        
        String notableHash1 =  "d46feecd663c41648dbf690d9343cf4b";
        String knownHash1 =    "39c844daee70485143da4ff926601b5b";
        String inAllSetsHash = "6449b39bb23c42879fa0c243726e27f7";
        
        CorrelationAttribute.Type emailType;
        
        // Store the email type object for later use
        try{ 
            emailType = EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.EMAIL_TYPE_ID);
            assertTrue("getCorrelationTypeById(EMAIL_TYPE_ID) returned null", emailType != null);
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
                
        // Set up a few reference sets
        try {
            notableSet1 = new EamGlobalSet(org1.getOrgID(), "notable set 1", "1.0", TskData.FileKnown.BAD, false, fileType);
            notableSet1id = EamDb.getInstance().newReferenceSet(notableSet1);
            notableSet2 = new EamGlobalSet(org1.getOrgID(), "notable set 2", "2.4", TskData.FileKnown.BAD, false, fileType);
            notableSet2id = EamDb.getInstance().newReferenceSet(notableSet2);
            knownSet1 = new EamGlobalSet(org1.getOrgID(), "known set 1", "5.5.4", TskData.FileKnown.KNOWN, false, fileType);
            knownSet1id = EamDb.getInstance().newReferenceSet(knownSet1);
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
        
        // Test adding file instances with valid data
        try {
            EamGlobalFileInstance temp = new EamGlobalFileInstance(notableSet1id, inAllSetsHash, TskData.FileKnown.BAD, "comment1");
            EamDb.getInstance().addReferenceInstance(temp, fileType);
            
            temp = new EamGlobalFileInstance(notableSet2id, inAllSetsHash, TskData.FileKnown.BAD, "comment2");
            EamDb.getInstance().addReferenceInstance(temp, fileType);
            
            temp = new EamGlobalFileInstance(knownSet1id, inAllSetsHash, TskData.FileKnown.KNOWN, "comment3");
            EamDb.getInstance().addReferenceInstance(temp, fileType);
            
            temp = new EamGlobalFileInstance(notableSet1id, notableHash1, TskData.FileKnown.BAD, "comment4");
            EamDb.getInstance().addReferenceInstance(temp, fileType);
            
            temp = new EamGlobalFileInstance(knownSet1id, knownHash1, TskData.FileKnown.KNOWN, "comment5");
            EamDb.getInstance().addReferenceInstance(temp, fileType);            
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test adding file instance with invalid reference set ID
        try {
            EamGlobalFileInstance temp = new EamGlobalFileInstance(2345, inAllSetsHash, TskData.FileKnown.BAD, "comment");
            EamDb.getInstance().addReferenceInstance(temp, fileType);  
            Assert.fail("addReferenceInstance failed to throw exception for invalid ID");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test creating file instance with null hash
        // Since it isn't possible to get a null hash into the EamGlobalFileInstance, skip trying to
        // call addReferenceInstance and just test the EamGlobalFileInstance constructor
        try {
            EamGlobalFileInstance temp = new EamGlobalFileInstance(notableSet1id, null, TskData.FileKnown.BAD, "comment");
            Assert.fail("EamGlobalFileInstance failed to throw exception for null hash");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test adding file instance with null known status
        // Since it isn't possible to get a null known status into the EamGlobalFileInstance, skip trying to
        // call addReferenceInstance and just test the EamGlobalFileInstance constructor
        try {
            EamGlobalFileInstance temp = new EamGlobalFileInstance(notableSet1id, inAllSetsHash, null, "comment"); 
            Assert.fail("EamGlobalFileInstance failed to throw exception for null type");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test adding file instance with null correlation type
        try {
            EamGlobalFileInstance temp = new EamGlobalFileInstance(notableSet1id, inAllSetsHash, TskData.FileKnown.BAD, "comment");
            EamDb.getInstance().addReferenceInstance(temp, null);  
            Assert.fail("addReferenceInstance failed to throw exception for null type");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test bulk insert with large valid set
        try {
            // Create a list of global file instances. Make enough that the bulk threshold should be hit once.
            Set<EamGlobalFileInstance> instances = new HashSet<>();
            String bulkTestHash = "bulktesthash_";
            for (int i = 0; i < dbSettingsSqlite.getBulkThreshold() * 1.5; i++) {
                String hash = bulkTestHash + String.valueOf(i);
                instances.add(new EamGlobalFileInstance(notableSet2id, hash, TskData.FileKnown.BAD, null));
            }
            
            // Insert the list
            EamDb.getInstance().bulkInsertReferenceTypeEntries(instances, fileType);
            
            // There's no way to get a count of the number of entries in the database, so just do a spot check
            if(dbSettingsSqlite.getBulkThreshold() > 10){
                String hash = bulkTestHash + "10";
                assertTrue("Sample bulk insert instance not found", EamDb.getInstance().isFileHashInReferenceSet(hash, notableSet2id));
            }
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test bulk add file instance with null list
        try {
            EamDb.getInstance().bulkInsertReferenceTypeEntries(null, fileType);  
            Assert.fail("bulkInsertReferenceTypeEntries failed to throw exception for null list");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test bulk add file instance with invalid reference set ID
        try {
            Set<EamGlobalFileInstance> tempSet = new HashSet<>(Arrays.asList(new EamGlobalFileInstance(2345, inAllSetsHash, TskData.FileKnown.BAD, "comment")));
            EamDb.getInstance().bulkInsertReferenceTypeEntries(tempSet, fileType);
            Assert.fail("bulkInsertReferenceTypeEntries failed to throw exception for invalid ID");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test bulk add file instance with null correlation type
        try {
            Set<EamGlobalFileInstance> tempSet = new HashSet<>(Arrays.asList(new EamGlobalFileInstance(notableSet1id, inAllSetsHash, TskData.FileKnown.BAD, "comment")));
            EamDb.getInstance().bulkInsertReferenceTypeEntries(tempSet, null);  
            Assert.fail("bulkInsertReferenceTypeEntries failed to throw exception for null type");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test getting reference instances with valid data
        try {
            List<EamGlobalFileInstance> temp = EamDb.getInstance().getReferenceInstancesByTypeValue(fileType, inAllSetsHash);
            assertTrue("getReferenceInstancesByTypeValue returned " + temp.size() + " instances - expected 3", temp.size() == 3);
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test getting reference instances with non-existent data
        try {
            List<EamGlobalFileInstance> temp = EamDb.getInstance().getReferenceInstancesByTypeValue(fileType, "testHash");
            assertTrue("getReferenceInstancesByTypeValue returned " + temp.size() + " instances for non-existent value - expected 0", temp.isEmpty());
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test getting reference instances an invalid type (the email table is not yet implemented)
        try {
            List<EamGlobalFileInstance> temp = EamDb.getInstance().getReferenceInstancesByTypeValue(emailType, inAllSetsHash);
            Assert.fail("getReferenceInstancesByTypeValue failed to throw exception for invalid table");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test getting reference instances with null type
        try {
            List<EamGlobalFileInstance> temp = EamDb.getInstance().getReferenceInstancesByTypeValue(null, inAllSetsHash);
            Assert.fail("getReferenceInstancesByTypeValue failed to throw exception for null type");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test getting reference instances with null value
        try {
            List<EamGlobalFileInstance> temp = EamDb.getInstance().getReferenceInstancesByTypeValue(fileType, null);
            assertTrue("getReferenceInstancesByTypeValue returned non-empty list given null value", temp.isEmpty());
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking existing hash/ID
        try {
            assertTrue("isFileHashInReferenceSet returned false for valid data", EamDb.getInstance().isFileHashInReferenceSet(knownHash1, knownSet1id));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking non-existent (but valid) hash/ID
        try {
            assertFalse("isFileHashInReferenceSet returned true for non-existent data", EamDb.getInstance().isFileHashInReferenceSet(knownHash1, notableSet1id));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking invalid reference set ID
        try {
            assertFalse("isFileHashInReferenceSet returned true for invalid data", EamDb.getInstance().isFileHashInReferenceSet(knownHash1, 5678));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking null hash
        try {
            assertFalse("isFileHashInReferenceSet returned true for null hash", EamDb.getInstance().isFileHashInReferenceSet(null, knownSet1id));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking existing hash/ID
        try {
            assertTrue("isValueInReferenceSet returned false for valid data", 
                    EamDb.getInstance().isValueInReferenceSet(knownHash1, knownSet1id, fileType.getId()));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking non-existent (but valid) hash/ID
        try {
            assertFalse("isValueInReferenceSet returned true for non-existent data", 
                    EamDb.getInstance().isValueInReferenceSet(knownHash1, notableSet1id, fileType.getId()));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking invalid reference set ID
        try {
            assertFalse("isValueInReferenceSet returned true for invalid data", 
                    EamDb.getInstance().isValueInReferenceSet(knownHash1, 5678, fileType.getId()));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking null hash
        try {
            assertFalse("isValueInReferenceSet returned true for null value", 
                    EamDb.getInstance().isValueInReferenceSet(null, knownSet1id, fileType.getId()));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test checking invalid type
        try {
            EamDb.getInstance().isValueInReferenceSet(knownHash1, knownSet1id, emailType.getId());
            Assert.fail("isValueInReferenceSet failed to throw exception for invalid type");
        } catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test known bad with notable data
        try {
            assertTrue("isArtifactKnownBadByReference returned false for notable value", 
                    EamDb.getInstance().isArtifactKnownBadByReference(fileType, notableHash1));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test known bad with known data
        try {
            assertFalse("isArtifactKnownBadByReference returned true for known value", 
                    EamDb.getInstance().isArtifactKnownBadByReference(fileType, knownHash1));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test known bad with non-existent data
        try {
            assertFalse("isArtifactKnownBadByReference returned true for non-existent value", 
                    EamDb.getInstance().isArtifactKnownBadByReference(fileType, "abcdef"));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test known bad with null hash
        try {
            assertFalse("isArtifactKnownBadByReference returned true for null value", 
                    EamDb.getInstance().isArtifactKnownBadByReference(fileType, null));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test known bad with null type
        try {
            EamDb.getInstance().isArtifactKnownBadByReference(null, knownHash1);
            Assert.fail("isArtifactKnownBadByReference failed to throw exception from null type");
        } catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test known bad with invalid type
        try {
            assertFalse("isArtifactKnownBadByReference returned true for invalid type", EamDb.getInstance().isArtifactKnownBadByReference(emailType, null));
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
    
    /**
     * Test method for the methods related to reference sets (does not include instance testing)
     * Only the files type is currently implemented
     * newReferenceSet(EamGlobalSet eamGlobalSet) tests:
     * - Test creating notable reference set
     * - Test creating known reference set
     * - Test creating duplicate reference set
     * - Test creating almost duplicate reference set
     * - Test with invalid org ID
     * - Test with null name
     * - Test with null version
     * - Test with null known status
     * - Test with null file type
    * referenceSetIsValid(int referenceSetID, String referenceSetName, String version) tests:
    *  - Test on existing reference set
    * - Test on invalid reference set
    * - Test with null name
    * - Test with null version
    * referenceSetExists(String referenceSetName, String version) tests:
    *  - Test on existing reference set
    * - Test on invalid reference set
    * - Test with null name
    * - Test with null version
    * getReferenceSetByID(int globalSetID) tests:
    * - Test with valid ID
    * - Test with invalid ID
    * getAllReferenceSets(CorrelationAttribute.Type correlationType) tests:
    * - Test getting all file sets
    * - Test getting all email sets
    * - Test with null type parameter
    * deleteReferenceSet(int referenceSetID) tests:
    * - Test on valid reference set ID
    * - Test on invalid reference set ID
    * getReferenceSetOrganization(int referenceSetID) tests:
    * - Test on valid reference set ID
    * - Test on invalid reference set ID
     */
    public void testReferenceSets() {
        String set1name = "referenceSet1";
        String set1version = "1.0";
        EamGlobalSet set1;
        int set1id;
        String set2name = "referenceSet2";
        EamGlobalSet set2;
        int set2id;
        EamGlobalSet set3;
        int set3id;
        
        
        // Test creating a notable reference set
        try {
            set1 = new EamGlobalSet(org1.getOrgID(), set1name, set1version, TskData.FileKnown.BAD, false, fileType);
            set1id = EamDb.getInstance().newReferenceSet(set1);
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
        
        // Test creating a known reference set
        try {
            set2 = new EamGlobalSet(org2.getOrgID(), set2name, "", TskData.FileKnown.KNOWN, false, fileType);
            set2id = EamDb.getInstance().newReferenceSet(set2);
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
        
        // Test creating a reference set with the same name and version
        try {
            EamGlobalSet temp = new EamGlobalSet(org1.getOrgID(), set1name, "1.0", TskData.FileKnown.BAD, false, fileType);
            EamDb.getInstance().newReferenceSet(temp);
            Assert.fail("newReferenceSet failed to throw exception from duplicate name/version pair");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test creating a reference set with the same name but different version
        try {
            set3 = new EamGlobalSet(org1.getOrgID(), set1name, "2.0", TskData.FileKnown.BAD, false, fileType);
            set3id = EamDb.getInstance().newReferenceSet(set3);
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            return;
        }
        
        // Test creating a reference set with invalid org ID
        try {
            EamGlobalSet temp = new EamGlobalSet(5000, "tempName", "", TskData.FileKnown.BAD, false, fileType);
            EamDb.getInstance().newReferenceSet(temp);
            Assert.fail("newReferenceSet failed to throw exception from invalid org ID");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test creating a reference set with null name
        try {
            EamGlobalSet temp = new EamGlobalSet(org2.getOrgID(), null, "", TskData.FileKnown.BAD, false, fileType);
            EamDb.getInstance().newReferenceSet(temp);
            Assert.fail("newReferenceSet failed to throw exception from null name");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test creating a reference set with null version
        try {
            EamGlobalSet temp = new EamGlobalSet(org2.getOrgID(), "tempName", null, TskData.FileKnown.BAD, false, fileType);
            EamDb.getInstance().newReferenceSet(temp);
            Assert.fail("newReferenceSet failed to throw exception from null version");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test creating a reference set with null file known status
        try {
            EamGlobalSet temp = new EamGlobalSet(org2.getOrgID(), "tempName", "", null, false, fileType);
            EamDb.getInstance().newReferenceSet(temp);
            Assert.fail("newReferenceSet failed to throw exception from null file known status");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test creating a reference set with null file type
        try {
            EamGlobalSet temp = new EamGlobalSet(org2.getOrgID(), "tempName", "", TskData.FileKnown.BAD, false, null);
            EamDb.getInstance().newReferenceSet(temp);
            Assert.fail("newReferenceSet failed to throw exception from null file type");
        }catch (EamDbException ex){
            // This is the expected behavior
        }
        
        // Test validation with a valid reference set
        try {
            assertTrue("referenceSetIsValid returned false for valid reference set", EamDb.getInstance().referenceSetIsValid(set1id, set1name, set1version));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test validation with an invalid reference set
        try {
            assertFalse("referenceSetIsValid returned true for invalid reference set", EamDb.getInstance().referenceSetIsValid(5000, set1name, set1version));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test validation with a null name
        try {
            assertFalse("referenceSetIsValid returned true with null name", EamDb.getInstance().referenceSetIsValid(set1id, null, set1version));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test validation with a null version
        try {
            assertFalse("referenceSetIsValid returned true with null version", EamDb.getInstance().referenceSetIsValid(set1id, set1name, null));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test existence with a valid reference set
        try {
            assertTrue("referenceSetExists returned false for valid reference set", EamDb.getInstance().referenceSetExists(set1name, set1version));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test existence with an invalid reference set
        try {
            assertFalse("referenceSetExists returned true for invalid reference set", EamDb.getInstance().referenceSetExists(set1name, "5.5"));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test existence with null name
        try {
            assertFalse("referenceSetExists returned true for null name", EamDb.getInstance().referenceSetExists(null, "1.0"));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test existence with null version
        try {
            assertFalse("referenceSetExists returned true for null version", EamDb.getInstance().referenceSetExists(set1name, null));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test getting global set with valid ID
        try {
            EamGlobalSet temp = EamDb.getInstance().getReferenceSetByID(set1id);
            assertTrue("getReferenceSetByID returned null for valid ID", temp != null);
            assertTrue("getReferenceSetByID returned set with incorrect name and/or version", 
                    set1name.equals(temp.getSetName()) && set1version.equals(temp.getVersion()));
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test getting global set with invalid ID
        try {
            EamGlobalSet temp = EamDb.getInstance().getReferenceSetByID(1234);
            assertTrue("getReferenceSetByID returned non-null result for invalid ID", temp == null);
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test getting all file reference sets
        try {
            List<EamGlobalSet> referenceSets = EamDb.getInstance().getAllReferenceSets(fileType);
            assertTrue("getAllReferenceSets(FILES) returned unexpected number", referenceSets.size() == 3);
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
        // Test getting all email reference sets
        try {
            List<EamGlobalSet> referenceSets = EamDb.getInstance().getAllReferenceSets(EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.EMAIL_TYPE_ID));
            assertTrue("getAllReferenceSets(EMAIL) returned unexpected number", referenceSets.isEmpty());
        }catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }        
        
        // Test null argument to getAllReferenceSets
        try {
            EamDb.getInstance().getAllReferenceSets(null);
            Assert.fail("getAllReferenceSets failed to throw exception from null type argument");
        }catch (EamDbException ex){
            // This is the expected behavior
        }  
        
        // Test deleting an existing reference set
        // First: create a new reference set, check that it's in the database, and get the number of reference sets
        // Second: delete the reference set, check that it is no longer in the database, and the total number of sets decreased by one
        try {
            EamGlobalSet setToDelete = new EamGlobalSet(org1.getOrgID(), "deleteThis", "deleteThisVersion", TskData.FileKnown.BAD, false, fileType);
            int setToDeleteID = EamDb.getInstance().newReferenceSet(setToDelete);
            assertTrue("setToDelete wasn't found in database", EamDb.getInstance().referenceSetIsValid(setToDeleteID, setToDelete.getSetName(), setToDelete.getVersion()));
            int currentCount = EamDb.getInstance().getAllReferenceSets(fileType).size();
            
            EamDb.getInstance().deleteReferenceSet(setToDeleteID);            
            assertFalse("Deleted reference set was found in database", EamDb.getInstance().referenceSetIsValid(setToDeleteID, setToDelete.getSetName(), setToDelete.getVersion()));
            assertTrue("Unexpected number of reference sets in database after deletion", currentCount - 1 == EamDb.getInstance().getAllReferenceSets(fileType).size());
            
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }  
        
        // Test deleting a non-existent reference set
        // The expectation is that nothing will happen
        try {
            int currentCount = EamDb.getInstance().getAllReferenceSets(fileType).size();            
            EamDb.getInstance().deleteReferenceSet(1234);            
            assertTrue("Number of reference sets changed after deleting non-existent set", currentCount == EamDb.getInstance().getAllReferenceSets(fileType).size());            
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }  
        
        // Test getting reference set organization for valid ID with org set
        try {
            EamOrganization org = EamDb.getInstance().getReferenceSetOrganization(set1id);
            assertTrue("getReferenceSetOrganization returned null for valid set", org != null);
            assertTrue("getReferenceSetOrganization returned the incorrect organization", org.getOrgID() == org1.getOrgID());
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        } 
        
        // Test getting reference set organization for non-existent reference set
        try {
            EamOrganization org = EamDb.getInstance().getReferenceSetOrganization(4567);
            Assert.fail("getReferenceSetOrganization failed to throw exception for invalid reference set ID");
        } catch (EamDbException ex){
            // This is the expected behavior
        }         
    }
    
    /**
     * Test method for the methods related to the data sources table
     * newDataSource(CorrelationDataSource eamDataSource) tests:
     * - Test with valid data
     * - Test with duplicate data
     * - Test with duplicate device ID and name but different case
     * - Test with invalid case ID
     * - Test with null device ID
     * - Test with null name
     *  getDataSource(CorrelationCase correlationCase, String dataSourceDeviceId) tests:
     * - Test with valid data
     * - Test with non-existent data
     * - Test with null correlationCase
     * - Test with null device ID
    * List<CorrelationDataSource> getDataSources()tests:
    *  - Test that the count and device IDs are as expected
    * Long getCountUniqueDataSources() tests:
    * - Test that the result is as expected
    */
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

        // Test creating a data source with null name
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
        
        try {
            // Set up an Autopsy case for testing
            try {
                Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, testDirectory.toString(), new CaseDetails("CentralRepoDatamodelTestCase"));
            } catch (CaseActionException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex);
            }
            assertTrue("Failed to create test case", testDirectory.toFile().exists());

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
        } finally {
            try {
                Case.closeCurrentCase();
                // This seems to help in allowing the Autopsy case to be deleted
                try{
                Thread.sleep(2000);
                } catch (Exception ex){

                }
            } catch (CaseActionException ex){
                Exceptions.printStackTrace(ex);
                Assert.fail(ex);
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
