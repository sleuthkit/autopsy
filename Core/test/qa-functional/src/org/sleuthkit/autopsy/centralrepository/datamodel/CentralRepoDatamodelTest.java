/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.io.IOException;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 *
 */
public class CentralRepoDatamodelTest extends TestCase{
    
    private static final String PROPERTIES_FILE = "CentralRepository";
    private static final String CR_DB_NAME = "testcentralrepo.db";
    private static final Path testDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "CentralRepoDatamodelTest");
    
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
      
        boolean errorsOccurred;
        SqliteEamDbSettings dbSettingsSqlite = new SqliteEamDbSettings();
        
        // Delete the test directory, if it exists
        if(testDirectory.toFile().exists()){
            try{
                FileUtils.deleteDirectory(testDirectory.toFile());
            } catch (IOException ex){
                Exceptions.printStackTrace(ex);
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
        }        
        assertTrue("Failed to create test case", testDirectory.toFile().exists());
       
        errorsOccurred = false;
        try{
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
            
            EamDb.getInstance().getCases();
        } catch (Exception ex){
            errorsOccurred = true;
            Exceptions.printStackTrace(ex);
        }
        
        assertFalse("Failed to create/initialize central repo database", errorsOccurred);
        Path crDbFilePath = Paths.get(testDirectory.toString(), CR_DB_NAME);
        assertTrue("Failed to create central repo database at " + crDbFilePath, crDbFilePath.toFile().exists());
        
        // Set up some default objects to be used by the tests
        errorsOccurred = false;
        try{
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
            org1.setOrgID((int)EamDb.getInstance().newOrganization(org1));
            
            org2 = new EamOrganization("org2");
            org2.setOrgID((int)EamDb.getInstance().newOrganization(org2));
            
            
        } catch (EamDbException ex){
            Exceptions.printStackTrace(ex);
            errorsOccurred = true;
        }
        
        assertFalse("Failed to create default database objects", errorsOccurred);
        

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
            Exceptions.printStackTrace(ex);
        }
        assertFalse("Error deleting test directory " + testDirectory.toString(), testDirectory.toFile().exists());
    }
    
    public void test1(){
        System.out.println("It's test 1");
    }
    
   // public void test2(){
        
   // }
    
}
