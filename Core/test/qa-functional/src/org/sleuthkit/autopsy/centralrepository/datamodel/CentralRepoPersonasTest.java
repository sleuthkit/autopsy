/*
 * Central Repository
 *
 * Copyright 2020 Basis Technology Corp.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import junit.framework.Assert;
import static junit.framework.Assert.assertTrue;
import junit.framework.TestCase;
import junit.framework.Test;
import org.apache.commons.io.FileUtils;

import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import static org.sleuthkit.autopsy.centralrepository.datamodel.PersonaHelper.addAccountToPersona;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.TskData;


/**
 *
 * Tests the Persona API in CentralRepository.
 */
public class CentralRepoPersonasTest  extends TestCase {
    
     private final Path testDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "CentralRepoDatamodelTest");
     
     
    private static final long CASE_1_DATA_SOURCE_1_ID = 11;
    private static final long CASE_1_DATA_SOURCE_2_ID = 12;
    private static final long CASE_2_DATA_SOURCE_1_ID = 21;

    private static final String PHONE_NUM_1 = "+1 441-231-2552";
    
    
    private static final String FACEBOOK_ID_CATDOG = "BalooSherkhan";
    private static final String EMAIL_ID_1 = "rkipling@junglebook.com";
    
    
    private static final String DOG_PERSONA_NAME = "Baloo McDog";
    private static final String CAT_PERSONA_NAME = "SherKhan";
     
    private CorrelationCase case1;
    private CorrelationCase case2;
    private CorrelationDataSource dataSource1fromCase1;
    private CorrelationDataSource dataSource2fromCase1;
    private CorrelationDataSource dataSource1fromCase2;
    private CentralRepoOrganization org1;
    private CentralRepoOrganization org2;
    
    private CentralRepoAccount.CentralRepoAccountType phoneAccountType;
    private CentralRepoAccount.CentralRepoAccountType emailAccountType;
    private CentralRepoAccount.CentralRepoAccountType facebookAccountType;
    private CentralRepoAccount.CentralRepoAccountType textnowAccountType;
    private CentralRepoAccount.CentralRepoAccountType whatsAppAccountType;
    private CentralRepoAccount.CentralRepoAccountType skypeAccountType;
    
     
    private CorrelationAttributeInstance.Type phoneInstanceType;
    private CorrelationAttributeInstance.Type emailInstanceType;
    private CorrelationAttributeInstance.Type facebookInstanceType;
    private CorrelationAttributeInstance.Type textnowInstanceType;
    private CorrelationAttributeInstance.Type whatsAppInstanceType;
    private CorrelationAttributeInstance.Type skypeInstanceType;
    
    
     // NbModuleSuite requires these tests use Junit 3.8
    // Extension of the TestCase class is how tests were defined and used
    // in Junit 3.8
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CentralRepoPersonasTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }
    
    // This function is run before every test, NOT before the entire collection of
    // tests defined in this class are run.
    @Override
    public void setUp() throws CentralRepoException, IOException {
        // Tear down the previous run, if need be.
        if (Files.exists(testDirectory)) {
            tearDown();
        }

        // Create the test directory
        Files.createDirectory(testDirectory);

        final String CR_DB_NAME = "testcentralrepo.db";

       
        
        SqliteCentralRepoSettings sqliteSettings = new SqliteCentralRepoSettings();
        sqliteSettings.setDbName(CR_DB_NAME);
        sqliteSettings.setDbDirectory(testDirectory.toString());

        if (!sqliteSettings.dbDirectoryExists() && !sqliteSettings.createDbDirectory()) {
            Assert.fail("Failed to create central repo directory.");
        }

        RdbmsCentralRepoFactory factory = new RdbmsCentralRepoFactory(CentralRepoPlatforms.SQLITE, sqliteSettings);
        if (!factory.initializeDatabaseSchema() || !factory.insertDefaultDatabaseContent()) {
            Assert.fail("Failed to initialize central repo database");
        }

        sqliteSettings.saveSettings();
        CentralRepoDbUtil.setUseCentralRepo(true);
        CentralRepoDbManager.saveDbChoice(CentralRepoDbChoice.SQLITE);

        Path crDbFilePath = Paths.get(testDirectory.toString(), CR_DB_NAME);
        if (!Files.exists(crDbFilePath)) {
            Assert.fail("Failed to create central repo database, should be located at + " + crDbFilePath);
        }
        
        // clear caches to match the clean slate database.
        CentralRepository.getInstance().clearCaches();
         
        // Add current logged in user to examiners table - since we delete the DB after every test.
        CentralRepository.getInstance().updateExaminers();
        
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

            // get some correltion types for different account types, for later use
            phoneAccountType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.PHONE.getTypeName());
            phoneInstanceType = CentralRepository.getInstance().getCorrelationTypeById(phoneAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(PHONE) returned null", phoneInstanceType != null);
            
            emailAccountType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.EMAIL.getTypeName());
            emailInstanceType = CentralRepository.getInstance().getCorrelationTypeById(emailAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(EMAIL) returned null", emailInstanceType != null);
            
            facebookAccountType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.FACEBOOK.getTypeName());
            facebookInstanceType = CentralRepository.getInstance().getCorrelationTypeById(facebookAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(FACEBOOK) returned null", facebookInstanceType != null);
            
            textnowAccountType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.TEXTNOW.getTypeName());
            textnowInstanceType = CentralRepository.getInstance().getCorrelationTypeById(textnowAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(TEXTNOW) returned null", textnowInstanceType != null);
            
            whatsAppAccountType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.WHATSAPP.getTypeName());
            whatsAppInstanceType = CentralRepository.getInstance().getCorrelationTypeById(whatsAppAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(WHATSAPP) returned null", whatsAppInstanceType != null);
            
            skypeAccountType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.SKYPE.getTypeName());
            skypeInstanceType = CentralRepository.getInstance().getCorrelationTypeById(skypeAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(SKYPE) returned null", skypeInstanceType != null);
            
            
        } catch (CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
        
    }
    
    // This function is run after every test, NOT after the entire collection of 
    // tests defined in the class are run.
    @Override
    public void tearDown() throws CentralRepoException, IOException {
        // Close and delete the test case and central repo db
        if (CentralRepository.isEnabled()) {
            CentralRepository.getInstance().shutdownConnections();
        }
        FileUtils.deleteDirectory(testDirectory.toFile());
    }
    
    
    /**
     * Basic tests for:
     *  - Persona creation,
     *  - adding aliases and metadata
     *  - add additional accounts to Persona
     *  - Get Persona(s) by account
     *  - get Account(s) by Persona
     * 
     */
    public void testBasicPersonaCreation() {
            
        //final String DATE_FORMAT_STRING = "yyyy/MM/dd HH:mm:ss"; //NON-NLS
        //final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING, Locale.US);
            
        try {
            
            // Step 1: Create an account
            CentralRepoAccount phoneAccount1 = CentralRepository.getInstance()
                    .getOrCreateAccount(phoneAccountType, PHONE_NUM_1);
            
            
            // Step 2: Create a Persona for the Account
           
            String comment = "The best dog ever";
            Persona.PersonaStatus status = Persona.PersonaStatus.ACTIVE;
            PersonaAccount pa1 = PersonaHelper.createPersonaForAccount(DOG_PERSONA_NAME, comment , status, phoneAccount1, "Because I said so", Persona.Confidence.LOW );
                

            Persona dogPersona = pa1.getPersona();
            
            // Verify Persona name, status etc.
            Assert.assertEquals(DOG_PERSONA_NAME, pa1.getPersona().getName());
            Assert.assertEquals(status.name(), dogPersona.getStatus().name());
            Assert.assertTrue(dogPersona.getExaminer().getLoginName().equalsIgnoreCase(pa1.getExaminer().getLoginName()));

            // Assert that Persona was created within the last 10 mins 
            Assert.assertTrue(Instant.now().toEpochMilli() - pa1.getDateAdded() < 600 * 1000);
            Assert.assertEquals(pa1.getConfidence(), Persona.Confidence.LOW);

            // Step 3. Add Persona Aliases
            PersonaAlias alias1 = PersonaHelper.addPersonaAlias(dogPersona, "Good Boy", "Coz he's is the best dog ever", Persona.Confidence.MEDIUM);
            PersonaAlias alias2 = PersonaHelper.addPersonaAlias(dogPersona, "WoofWoof", "How many dumb comments can I come up with?", Persona.Confidence.LOW);

            Assert.assertNotNull(alias1);
            Assert.assertNotNull(alias2);

            // get all aliases for persona
            Collection<PersonaAlias> aliases = PersonaHelper.getPersonaAliases(dogPersona.getId());
            Assert.assertEquals(2, aliases.size());
            for (PersonaAlias alias: aliases) {
                //System.out.println("Alias: "+ alias.getAlias()) ;
                Assert.assertFalse(alias.getAlias().isEmpty());
            }
            
            
            //Step 4: Add Persona metadata
            PersonaMetadata metadata1 = PersonaHelper.addPersonaMetadata(dogPersona, "Color", "Black", "He's got thick black hair.", Persona.Confidence.MEDIUM);
            PersonaMetadata metadata2 = PersonaHelper.addPersonaMetadata(dogPersona, "Gender", "Male", "Because...", Persona.Confidence.LOW);

            Assert.assertNotNull(metadata1);
            Assert.assertNotNull(metadata2);

            // get all metadata for persona 
            Collection<PersonaMetadata> metadataList = PersonaHelper.getPersonaMetadata(dogPersona.getId());
            Assert.assertEquals(2, metadataList.size());
            for (PersonaMetadata md: metadataList) {
                //System.out.println(String.format("Metadata: %s : %s", md.getName(), md.getValue())) ;
                Assert.assertFalse(md.getName().isEmpty());
                Assert.assertFalse(md.getValue().isEmpty());
            }
            
            
            // Step 5: associate another account with same persona
            CentralRepoAccount catdogFBAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(facebookAccountType, FACEBOOK_ID_CATDOG);
            
            // Add account to persona
            addAccountToPersona( dogPersona,  catdogFBAccount,  "Looks like dog, barks like a dog...",  Persona.Confidence.MEDIUM);
            
             // Get all acounts for the persona...
            Collection<PersonaAccount> personaAccounts = PersonaHelper.getPersonaAccountsForPersona(dogPersona.getId());
            
            Assert.assertEquals(2, personaAccounts.size());
            
            for (PersonaAccount pa: personaAccounts) {
                //System.out.println(String.format("PersonaAccount: Justification = %s : Date Added = %s", pa.getJustification(), DATE_FORMAT.format(new Date(pa.getDateAdded())))) ;
                Assert.assertFalse(pa.getJustification().isEmpty());
                Assert.assertFalse(pa.getAccount().getTypeSpecificId().isEmpty());
                Assert.assertTrue(pa.getDateAdded() > 0);
                Assert.assertTrue(pa.getPersona().getCreatedDate()> 0);
            }
            
            // Step 6: Create a Second Persona, that shares a common account with another persona
           
            String comment2 = "The fiercest cat alive.";
            PersonaAccount pa2 = PersonaHelper.createPersonaForAccount(CAT_PERSONA_NAME, comment2 , Persona.PersonaStatus.ACTIVE, catdogFBAccount, "Smells like a cat.", Persona.Confidence.LOW );
            Assert.assertNotNull(pa2);
            Assert.assertTrue(pa2.getPersona().getName().equalsIgnoreCase(CAT_PERSONA_NAME));
            
            
            // Get ALL personas for an account
            Collection<PersonaAccount> personaAccounts2 = PersonaHelper.getPersonaAccountsForAccount(catdogFBAccount.getAccountId());
            
            Assert.assertEquals(2, personaAccounts2.size());
            for (PersonaAccount pa: personaAccounts2) {
                //System.out.println(String.format("PersonaAccount: Justification = %s : Date Added = %s", pa.getJustification(), DATE_FORMAT.format(new Date(pa.getDateAdded())))) ;
                Assert.assertFalse(pa.getJustification().isEmpty());
                Assert.assertFalse(pa.getAccount().getTypeSpecificId().isEmpty());
                Assert.assertTrue(pa.getDateAdded() > 0);
                Assert.assertTrue(pa.getPersona().getCreatedDate()> 0);
                Assert.assertFalse(pa.getPersona().getName().isEmpty());
            }
            
            
        } catch (CentralRepoException ex) {
             Assert.fail("Didn't expect an exception here. Exception: " + ex);
        }
    }
    
    /**
     * Tests Personas & X_Accounts and X_instances in the context of Case/data source.
     */
    public void testPersonaWithCases() {
        
        try {
        // Create an account
        CentralRepoAccount catdogFBAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(facebookAccountType, FACEBOOK_ID_CATDOG);
        
        
        // Create account instance attribute for that account, on Case 1, DS 1
        CorrelationAttributeInstance fbAcctInstance1 = new CorrelationAttributeInstance(facebookInstanceType, FACEBOOK_ID_CATDOG,
                    -1, 
                    case1,
                    dataSource1fromCase1,
                    "path1",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1001L,
                    catdogFBAccount.getAccountId());
        CentralRepository.getInstance().addArtifactInstance(fbAcctInstance1);
        
            
            
        
        // Create account instance attribute for that account, on Case 1, DS 2
        CorrelationAttributeInstance fbAcctInstance2 = new CorrelationAttributeInstance(facebookInstanceType, FACEBOOK_ID_CATDOG,
                    -1,
                    case1,
                    dataSource2fromCase1,
                    "path2",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1002L, catdogFBAccount.getAccountId());
        
            CentralRepository.getInstance().addArtifactInstance(fbAcctInstance2);
            
            
        // Create account instance attribute for that account, on Case 1, DS 2
        CorrelationAttributeInstance fbAcctInstance3 = new CorrelationAttributeInstance(facebookInstanceType, FACEBOOK_ID_CATDOG,
                    -1,
                    case2,
                    dataSource1fromCase2,
                    "path3",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1003L, catdogFBAccount.getAccountId());
        CentralRepository.getInstance().addArtifactInstance(fbAcctInstance3);
        
        
        // Create Persona for that account
         
        String comment = "The best dog ever";
        Persona.PersonaStatus status = Persona.PersonaStatus.ACTIVE;
        PersonaAccount pa1 = PersonaHelper.createPersonaForAccount(DOG_PERSONA_NAME, 
                                comment , 
                                status, catdogFBAccount, "Because I said so", Persona.Confidence.LOW );
               
        
        // Test that getting all Personas for Case 1  includes the persona above
        // Test that getting all Personas for Case 2  includes the persona above
        // Test that getting all Personas for DS 1  includes the persona above
        // Test that getting all Personas for DS 2  includes the persona above
        
        
        // Test that getting cases for the Persona returns Case 1 & 2
        // Test that getting data sources for the Persona returns Case1_DS1 & Case1_DS2 & Case2_DS1
        
        }
         catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
        
    }
    /**
     * Tests edge cases, error cases
     */
    public void testPersonaCreationEdgeCases() {
         
        // Test1: create Persona without specifying a name 
        {
            try {
                // Create an email account
                CentralRepoAccount emailAccount1 = CentralRepository.getInstance()
                        .getOrCreateAccount(emailAccountType, EMAIL_ID_1);

                // Create a Persona with no name
                PersonaAccount pa1 = PersonaHelper.createPersonaForAccount(null, "A persona with no name",
                        Persona.PersonaStatus.ACTIVE, emailAccount1, "The person lost his name", Persona.Confidence.LOW);
                
                // Verify Persona has a default name
                Assert.assertEquals("NoName", pa1.getPersona().getName());
                
            } catch (CentralRepoException ex) {
                Assert.fail("No name persona test failed. Exception: " + ex);
            }
        }
        
        

     }
}
