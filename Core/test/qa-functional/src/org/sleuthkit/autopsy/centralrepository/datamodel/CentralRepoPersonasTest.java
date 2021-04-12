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
import java.util.Optional;
import junit.framework.Assert;
import static junit.framework.Assert.assertTrue;
import junit.framework.TestCase;
import junit.framework.Test;

import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.InvalidAccountIDException;
import org.sleuthkit.datamodel.TskData;


/**
 *
 * Tests the Persona API in CentralRepository.
 */
public class CentralRepoPersonasTest  extends TestCase {
    
     private final Path testDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "CentralRepoDatamodelTest");
     
    
    private static final String CASE_1_UUID = "case1_uuid";
    private static final String CASE_2_UUID = "case2_uuid";
    private static final String CASE_3_UUID = "case3_uuid";
    private static final String CASE_4_UUID = "case4_uuid";
    
    private static final String DS1_DEVICEID = "dataSource1_deviceID";
    private static final String DS2_DEVICEID = "dataSource2_deviceID";
    private static final String DS3_DEVICEID = "dataSource3_deviceID";
    private static final String DS4_DEVICEID = "dataSource4_deviceID";
    private static final String DS5_DEVICEID = "dataSource5_deviceID";
    private static final String DS6_DEVICEID = "dataSource6_deviceID";
         
  
    private static final long CASE_1_DATA_SOURCE_1_ID = 11;
    private static final long CASE_1_DATA_SOURCE_2_ID = 12;
    private static final long CASE_2_DATA_SOURCE_1_ID = 21;
    
    private static final long CASE_3_DATA_SOURCE_1_ID = 31;
    private static final long CASE_3_DATA_SOURCE_2_ID = 32;
    
    private static final long CASE_4_DATA_SOURCE_1_ID = 41;
    

    private static final String PHONE_NUM_1 = "+1 441-231-2552";
    
    
    private static final String FACEBOOK_ID_CATDOG = "BalooSherkhan";
   
    private static final String DOG_EMAIL_ID = "superpupper@junglebook.com";
    private static final String CAT_WHATSAPP_ID = "1112223333@s.whatsapp.net";
    private static final String EMAIL_ID_1 = "rkipling@jungle.book";
    
    private static final String HOLMES_SKYPE_ID = "live:holmes@221baker.com";
     
    
    private static final String DOG_PERSONA_NAME = "Baloo McDog";
    private static final String CAT_PERSONA_NAME = "SherKhan";
    private static final String HOLMES_PERSONA_NAME = "Sherlock Holmes";
    
     
    private CorrelationCase case1;
    private CorrelationCase case2;
    private CorrelationCase case3;
    private CorrelationCase case4;
    
    private CorrelationDataSource dataSource1fromCase1;
    private CorrelationDataSource dataSource2fromCase1;
    private CorrelationDataSource dataSource1fromCase2;
    
    private CorrelationDataSource dataSource1fromCase3;
    private CorrelationDataSource dataSource2fromCase3;
    private CorrelationDataSource dataSource1fromCase4;
    
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
        CentralRepository.getInstance().updateSettings();
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
        
         
        // Set up some default objects to be used by the tests
        try {
            case1 = new CorrelationCase(CASE_1_UUID, "case1");
            case1 = CentralRepository.getInstance().newCase(case1);
            assertTrue("Failed to create test object case1", case1 != null);

            case2 = new CorrelationCase(CASE_2_UUID, "case2");
            case2 = CentralRepository.getInstance().newCase(case2);
            assertTrue("Failed to create test object case2", case2 != null);
            
            case3 = new CorrelationCase(CASE_3_UUID, "case3");
            case3 = CentralRepository.getInstance().newCase(case3);
            assertTrue("Failed to create test object case3", case3 != null);
            
            case4 = new CorrelationCase(CASE_4_UUID, "case4");
            case4 = CentralRepository.getInstance().newCase(case4);
            assertTrue("Failed to create test object case4", case4 != null);
            
            dataSource1fromCase1 = new CorrelationDataSource(case1, DS1_DEVICEID, "dataSource1", CASE_1_DATA_SOURCE_1_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource1fromCase1);
            dataSource1fromCase1 = CentralRepository.getInstance().getDataSource(case1, dataSource1fromCase1.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource1fromCase1", dataSource1fromCase1 != null);

            dataSource2fromCase1 = new CorrelationDataSource(case1, DS2_DEVICEID, "dataSource2", CASE_1_DATA_SOURCE_2_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource2fromCase1);
            dataSource2fromCase1 = CentralRepository.getInstance().getDataSource(case1, dataSource2fromCase1.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource2fromCase1", dataSource2fromCase1 != null);

            dataSource1fromCase2 = new CorrelationDataSource(case2, DS3_DEVICEID, "dataSource3", CASE_2_DATA_SOURCE_1_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource1fromCase2);
            dataSource1fromCase2 = CentralRepository.getInstance().getDataSource(case2, dataSource1fromCase2.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource1fromCase2", dataSource1fromCase2 != null);

            dataSource1fromCase3 = new CorrelationDataSource(case3, DS4_DEVICEID, "dataSource4", CASE_3_DATA_SOURCE_1_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource1fromCase3);
            dataSource1fromCase3 = CentralRepository.getInstance().getDataSource(case3, dataSource1fromCase3.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource1fromCase3", dataSource1fromCase3 != null);

            dataSource2fromCase3 = new CorrelationDataSource(case3, DS5_DEVICEID, "dataSource5", CASE_3_DATA_SOURCE_2_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource2fromCase3);
            dataSource2fromCase3 = CentralRepository.getInstance().getDataSource(case3, dataSource2fromCase3.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource2fromCase3", dataSource2fromCase3 != null);
            
            dataSource1fromCase4 = new CorrelationDataSource(case4, DS6_DEVICEID, "dataSource6", CASE_4_DATA_SOURCE_1_ID, null, null, null);
            CentralRepository.getInstance().newDataSource(dataSource1fromCase4);
            dataSource1fromCase4 = CentralRepository.getInstance().getDataSource(case4, dataSource1fromCase4.getDataSourceObjectID());
            assertTrue("Failed to create test object dataSource1fromCase4", dataSource1fromCase4 != null);
            
            org1 = new CentralRepoOrganization("org1");
            org1 = CentralRepository.getInstance().newOrganization(org1);

            org2 = new CentralRepoOrganization("org2");
            org2 = CentralRepository.getInstance().newOrganization(org2);

            // get some correltion types for different account types, for later use
            Optional<CentralRepoAccount.CentralRepoAccountType> optType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.PHONE.getTypeName());
            assertTrue(optType.isPresent());
            phoneAccountType = optType.get();
            phoneInstanceType = CentralRepository.getInstance().getCorrelationTypeById(phoneAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(PHONE) returned null", phoneInstanceType != null);
            
            optType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.EMAIL.getTypeName());
            assertTrue(optType.isPresent());
            emailAccountType = optType.get();
            emailInstanceType = CentralRepository.getInstance().getCorrelationTypeById(emailAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(EMAIL) returned null", emailInstanceType != null);
            
            optType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.FACEBOOK.getTypeName());
            assertTrue(optType.isPresent());
            facebookAccountType = optType.get();
            facebookInstanceType = CentralRepository.getInstance().getCorrelationTypeById(facebookAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(FACEBOOK) returned null", facebookInstanceType != null);
            
            optType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.TEXTNOW.getTypeName());
            assertTrue(optType.isPresent());
            textnowAccountType = optType.get();
            textnowInstanceType = CentralRepository.getInstance().getCorrelationTypeById(textnowAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(TEXTNOW) returned null", textnowInstanceType != null);
            
            optType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.WHATSAPP.getTypeName());
            assertTrue(optType.isPresent());
            whatsAppAccountType = optType.get();
            whatsAppInstanceType = CentralRepository.getInstance().getCorrelationTypeById(whatsAppAccountType.getCorrelationTypeId());
            assertTrue("getCorrelationTypeById(WHATSAPP) returned null", whatsAppInstanceType != null);
            
            optType = CentralRepository.getInstance().getAccountTypeByName( Account.Type.SKYPE.getTypeName());
            assertTrue(optType.isPresent());
            skypeAccountType = optType.get();
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
    public void tearDown() throws CentralRepoException {
        // Close and delete the test case and central repo db
        if (CentralRepository.isEnabled()) {
            CentralRepository.getInstance().shutdownConnections();
        }
        FileUtil.deleteDir(testDirectory.toFile());
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
            Persona dogPersona = Persona.createPersonaForAccount(DOG_PERSONA_NAME, comment , status, phoneAccount1, "Because I said so", Persona.Confidence.LOW );
                

            // Verify Persona name, status etc.
            Assert.assertEquals(DOG_PERSONA_NAME, dogPersona.getName());
            Assert.assertEquals(status.name(), dogPersona.getStatus().name());
            
            // Assert that the persona was created by the currently logged in user
            Assert.assertTrue(dogPersona.getExaminer().getLoginName().equalsIgnoreCase(System.getProperty("user.name")));

            // Assert that Persona was created within the last 10 mins 
            Assert.assertTrue(Instant.now().toEpochMilli() - dogPersona.getCreatedDate() < 600 * 1000);

            // Step 3. Add Persona Aliases
            PersonaAlias alias1 = dogPersona.addAlias("Good Boy", "Coz he's is the best dog ever", Persona.Confidence.MODERATE);
            PersonaAlias alias2 = dogPersona.addAlias("WoofWoof", "How many dumb comments can I come up with?", Persona.Confidence.LOW);

            Assert.assertNotNull(alias1);
            Assert.assertNotNull(alias2);

            // get all aliases for persona
            Collection<PersonaAlias> aliases = dogPersona.getAliases();
            Assert.assertEquals(2, aliases.size());
            for (PersonaAlias alias: aliases) {
                //System.out.println("Alias: "+ alias.getAlias()) ;
                Assert.assertFalse(alias.getAlias().isEmpty());
            }
            
            
            //Step 4: Add Persona metadata
            PersonaMetadata metadata1 = dogPersona.addMetadata("Color", "Black", "He's got thick black hair.", Persona.Confidence.MODERATE);
            PersonaMetadata metadata2 = dogPersona.addMetadata("Gender", "Male", "Because...", Persona.Confidence.LOW);

            Assert.assertNotNull(metadata1);
            Assert.assertNotNull(metadata2);

            // get all metadata for persona 
            Collection<PersonaMetadata> metadataList = dogPersona.getMetadata();
            Assert.assertEquals(2, metadataList.size());
            for (PersonaMetadata md: metadataList) {
                //System.out.println(String.format("Metadata: %s : %s", md.getName(), md.getValue())) ;
                Assert.assertFalse(md.getName().isEmpty());
                Assert.assertFalse(md.getValue().isEmpty());
            }
            
            
            // Step 5: associate another account with same persona
            CentralRepoAccount catdogFBAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(facebookAccountType, FACEBOOK_ID_CATDOG);
            
            // Add an account to persona
            dogPersona.addAccount(catdogFBAccount,  "Looks like dog, barks like a dog...",  Persona.Confidence.MODERATE);
            
             // Get all acounts for the persona...
            Collection<PersonaAccount> personaAccounts = dogPersona.getPersonaAccounts();
            
            Assert.assertEquals(2, personaAccounts.size());
            
            for (PersonaAccount pa: personaAccounts) {
                //System.out.println(String.format("PersonaAccount: Justification = %s : Date Added = %s", pa.getJustification(), DATE_FORMAT.format(new Date(pa.getDateAdded())))) ;
                Assert.assertFalse(pa.getJustification().isEmpty());
                Assert.assertFalse(pa.getAccount().getIdentifier().isEmpty());
                Assert.assertTrue(pa.getDateAdded() > 0);
                Assert.assertTrue(pa.getPersona().getCreatedDate()> 0);
            }
            
            // Step 6: Create a Second Persona, that shares a common account with another persona
           
            String comment2 = "The fiercest cat alive.";
            Persona catPersona = Persona.createPersonaForAccount(CAT_PERSONA_NAME, comment2 , Persona.PersonaStatus.ACTIVE, catdogFBAccount, "Smells like a cat.", Persona.Confidence.LOW );
            Assert.assertNotNull(catPersona);
            Assert.assertTrue(catPersona.getName().equalsIgnoreCase(CAT_PERSONA_NAME));
            
            
            // Get ALL personas for an account
            Collection<PersonaAccount> personaAccounts2 = PersonaAccount.getPersonaAccountsForAccount(catdogFBAccount.getId());
            
            Assert.assertEquals(2, personaAccounts2.size());
            for (PersonaAccount pa: personaAccounts2) {
                //System.out.println(String.format("PersonaAccount: Justification = %s : Date Added = %s", pa.getJustification(), DATE_FORMAT.format(new Date(pa.getDateAdded())))) ;
                Assert.assertFalse(pa.getJustification().isEmpty());
                Assert.assertFalse(pa.getAccount().getIdentifier().isEmpty());
                Assert.assertTrue(pa.getDateAdded() > 0);
                Assert.assertTrue(pa.getPersona().getCreatedDate()> 0);
                Assert.assertFalse(pa.getPersona().getName().isEmpty());
            }
            
            // Remove the account from the catPersona
            catPersona.removeAccount(catPersona.getPersonaAccounts().iterator().next());
            
            // Confirm the account was removed
            Assert.assertTrue(catPersona.getPersonaAccounts().isEmpty());
            
        } catch (InvalidAccountIDException | CentralRepoException ex) {
             Assert.fail("Didn't expect an exception here. Exception: " + ex);
        }
    }
    
     /**
     * Tests Persona alias and metadata.
     * 
     */
    public void testPersonaAliasesAndMetadata() {
            
        
        try {
            
            // Step 1: Create an account
            CentralRepoAccount phoneAccount1 = CentralRepository.getInstance()
                    .getOrCreateAccount(phoneAccountType, PHONE_NUM_1);
            
            
            // Step 2: Create a Persona for the Account
            String comment = "The best dog ever";
            Persona.PersonaStatus status = Persona.PersonaStatus.ACTIVE;
            Persona dogPersona = Persona.createPersonaForAccount(DOG_PERSONA_NAME, comment , status, phoneAccount1, "Because I said so", Persona.Confidence.LOW );
                

            // Step 3. Add Persona Aliases
            PersonaAlias alias1 = dogPersona.addAlias("Good Boy", "Coz he's is the best dog ever", Persona.Confidence.MODERATE);
            PersonaAlias alias2 = dogPersona.addAlias("WoofWoof", "How many dumb comments can I come up with?", Persona.Confidence.LOW);

            Assert.assertNotNull(alias1);
            Assert.assertNotNull(alias2);

            //Step 4: Add Persona metadata
            PersonaMetadata metadata1 = dogPersona.addMetadata("Color", "Black", "He's got thick black hair.", Persona.Confidence.MODERATE);
            PersonaMetadata metadata2 = dogPersona.addMetadata("Gender", "Male", "Because...", Persona.Confidence.LOW);

            Assert.assertNotNull(metadata1);
            Assert.assertNotNull(metadata2);

             // get all aliases for persona1
            Collection<PersonaAlias> dogAliases1 = dogPersona.getAliases();
            Assert.assertEquals(2, dogAliases1.size());
            for (PersonaAlias alias: dogAliases1) {
                //System.out.println(" Dog Alias: "+ alias.getAlias()) ;
                Assert.assertFalse(alias.getAlias().isEmpty());
            }
            // get all metadata for persona1
            Collection<PersonaMetadata> dogMetadataList = dogPersona.getMetadata();
            Assert.assertEquals(2, dogMetadataList.size());
            for (PersonaMetadata md: dogMetadataList) {
                //System.out.println(String.format("Metadata: %s : %s", md.getName(), md.getValue())) ;
                Assert.assertFalse(md.getName().isEmpty());
                Assert.assertFalse(md.getValue().isEmpty());
            }
            
            
            // Step 5: Create another account
            CentralRepoAccount catdogFBAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(facebookAccountType, FACEBOOK_ID_CATDOG);
            
            // Add an account to persona
            dogPersona.addAccount(catdogFBAccount,  "Looks like dog, barks like a dog...",  Persona.Confidence.MODERATE);
            
            
            // Step 6: Create a Second Persona
           
            String comment2 = "The fiercest cat alive.";
            Persona catPersona = Persona.createPersonaForAccount(CAT_PERSONA_NAME, comment2 , Persona.PersonaStatus.ACTIVE, catdogFBAccount, "Smells like a cat.", Persona.Confidence.LOW );
            Assert.assertNotNull(catPersona);
            Assert.assertTrue(catPersona.getName().equalsIgnoreCase(CAT_PERSONA_NAME));
            
          
              // Add Persona Aliases
            PersonaAlias catAlias1 = catPersona.addAlias("CutieKitty", "Because", Persona.Confidence.MODERATE);
            Assert.assertNotNull(catAlias1);
          
            
            //Step 4: Add Persona metadata
            PersonaMetadata catMetadata1 = catPersona.addMetadata("Color", "White", "White as snow.", Persona.Confidence.MODERATE);
            PersonaMetadata catMetadata2 = catPersona.addMetadata("Breed", "Persian", "Just Because...", Persona.Confidence.LOW);
            PersonaMetadata catMetadata3 = catPersona.addMetadata("Legs", "Four", "I counted", Persona.Confidence.HIGH);
              
            Assert.assertNotNull(catMetadata1);
            Assert.assertNotNull(catMetadata2);
            Assert.assertNotNull(catMetadata3);
            
           
             // get all aliases for persona2
            Collection<PersonaAlias> catAliases1 = catPersona.getAliases();
            Assert.assertEquals(1, catAliases1.size());
            for (PersonaAlias alias: dogAliases1) {
                //System.out.println("Alias: "+ alias.getAlias()) ;
                Assert.assertFalse(alias.getAlias().isEmpty());
            }
            // get all metadata for persona2
            Collection<PersonaMetadata> catMetadataList = catPersona.getMetadata();
            Assert.assertEquals(3, catMetadataList.size());
            for (PersonaMetadata md: catMetadataList) {
                //System.out.println(String.format("Metadata: %s : %s", md.getName(), md.getValue())) ;
                Assert.assertFalse(md.getName().isEmpty());
                Assert.assertFalse(md.getValue().isEmpty());
            }
            
            // delete the cat alias from catPersona
            catPersona.removeAlias(catAlias1);
            
            // confirm catPersona no longer has any aliases
            Assert.assertTrue(catPersona.getAliases().isEmpty());
            
            // delete catMetadata2 from catPersona
            catPersona.removeMetadata(catMetadata2);
            
            // confirm catPersona no longer has catMetadata2
            Assert.assertEquals(2, catPersona.getMetadata().size());
            Assert.assertFalse(catPersona.getMetadata().contains(catMetadata2));
            
            // Create a 3rd account and persona
              CentralRepoAccount holmesSkypeAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(skypeAccountType, HOLMES_SKYPE_ID);
      
            // Create a person for the Skype account
            Persona holmesPersona = Persona.createPersonaForAccount(HOLMES_PERSONA_NAME,
                    "Has a Pipe in his mouth.", Persona.PersonaStatus.ACTIVE,
                    holmesSkypeAccount, "The name says it all.", Persona.Confidence.LOW);

            // This persona has no aliases or metadata. Verify 
            // get all aliases for holmesPersona
            Collection<PersonaAlias> holmesAliases = holmesPersona.getAliases();
            Assert.assertEquals(0, holmesAliases.size());

            // get all metadata for holmesPersona
            Collection<PersonaMetadata> holmesMetadataList = holmesPersona.getMetadata();
            Assert.assertEquals(0, holmesMetadataList.size());

            
        } catch (InvalidAccountIDException | CentralRepoException ex) {
             Assert.fail("Didn't expect an exception here. Exception: " + ex);
        }
    }
    
    /**
     * Tests Personas & X_Accounts and X_instances in the context of Case/data source.
     *  There are 4 Cases. 
     *  - Case1 has 2 data sources, case2 has 1.
     *  - Case3 has 2 data sources, case4 has 1.
     *  There are 3 personas - A Cat, a Dog, and Sherlock Holmes. 
     *  Cat & Dog share a FB account - with 3 instances split over the 2 cases - Case1 & Case2.
     *  Dog has his his own email account - with one instance in Case1
     *  Cat has his own WhatsApp account. - with 2 instances - in Case1 & Case2
     *  Sherlock has a Skype account - with 1 instance in Case 3.
     *  Case 4 has no personas or accounts
     */
    public void testPersonaWithCases() {
        
        try {
        // Create an account - Cat and Dog have a shared FB account
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
                    catdogFBAccount.getId());
        CentralRepository.getInstance().addArtifactInstance(fbAcctInstance1);
        
            
        // Create account instance attribute for that account, on Case 1, DS 2
        CorrelationAttributeInstance fbAcctInstance2 = new CorrelationAttributeInstance(facebookInstanceType, FACEBOOK_ID_CATDOG,
                    -1,
                    case1,
                    dataSource2fromCase1,
                    "path2",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1002L, catdogFBAccount.getId());
        
        CentralRepository.getInstance().addArtifactInstance(fbAcctInstance2);
            
            
        // Create account instance attribute for that account, on Case 1, DS 2
        CorrelationAttributeInstance fbAcctInstance3 = new CorrelationAttributeInstance(facebookInstanceType, FACEBOOK_ID_CATDOG,
                    -1,
                    case2,
                    dataSource1fromCase2,
                    "path3",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1003L, catdogFBAccount.getId());
        CentralRepository.getInstance().addArtifactInstance(fbAcctInstance3);
        
        
        // Create Persona for the Dog, using the shared FB account
        String comment = "The best dog ever";
        Persona.PersonaStatus status = Persona.PersonaStatus.ACTIVE;
        Persona dogPersona = Persona.createPersonaForAccount(DOG_PERSONA_NAME, 
                                comment , 
                                status, catdogFBAccount, "Because I said so", Persona.Confidence.LOW );
        
               
        
      
        // create a second persona for the same account - Cat has the same FB account as dog
        String comment2 = "The fiercest cat alive.";
        Persona catPersona = Persona.createPersonaForAccount(CAT_PERSONA_NAME, 
                                 comment2 , Persona.PersonaStatus.ACTIVE, 
                                 catdogFBAccount, "Smells like a cat.", Persona.Confidence.LOW );
        Assert.assertNotNull(catPersona);
        Assert.assertTrue(catPersona.getName().equalsIgnoreCase(CAT_PERSONA_NAME));
        
        
        
        // Add a 2nd account to the Dog - dog has his own email 
         CentralRepoAccount dogEmailAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(emailAccountType, DOG_EMAIL_ID);
         
         // Add an instance of dog email 
         CorrelationAttributeInstance dogEmailAcctInstance = new CorrelationAttributeInstance(emailInstanceType, DOG_EMAIL_ID,
                    -1,
                    case1,
                    dataSource2fromCase1,
                    "path3",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1002L, 
                  dogEmailAccount.getId());
        
        CentralRepository.getInstance().addArtifactInstance(dogEmailAcctInstance);
        
        PersonaAccount pa3 = dogPersona.addAccount(dogEmailAccount,  "Thats definitely a dog email account",  Persona.Confidence.MODERATE);
        Assert.assertNotNull(pa3);
        Assert.assertTrue(pa3.getPersona().getName().equalsIgnoreCase(DOG_PERSONA_NAME));
        
        
        // create a WhatsApp account for cat, add 2 instances, and then add that to Cat persona
        CentralRepoAccount catWhatsAppAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(whatsAppAccountType, CAT_WHATSAPP_ID);
         
         // Add 2 instances of cat whatsApp  
        CorrelationAttributeInstance catWhatsAppAccountInstance1 = new CorrelationAttributeInstance(whatsAppInstanceType, CAT_WHATSAPP_ID,
                    -1,
                    case1,
                    dataSource1fromCase1,
                    "path4",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1005L, 
                  catWhatsAppAccount.getId());
        CentralRepository.getInstance().addArtifactInstance(catWhatsAppAccountInstance1);
        
         CorrelationAttributeInstance catWhatsAppAccountInstance2 = new CorrelationAttributeInstance(whatsAppInstanceType, CAT_WHATSAPP_ID,
                    -1,
                    case2,
                    dataSource1fromCase2,
                    "path5",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1006L, 
                  catWhatsAppAccount.getId());
        CentralRepository.getInstance().addArtifactInstance(catWhatsAppAccountInstance2);
        
        
        PersonaAccount pa4 = catPersona.addAccount(catWhatsAppAccount,  "The cat has a WhatsApp account",  Persona.Confidence.MODERATE);
        Assert.assertNotNull(pa4);
        Assert.assertTrue(pa4.getPersona().getName().equalsIgnoreCase(CAT_PERSONA_NAME));
        
        
        
        Collection<PersonaAccount> dogPersonaAccounts =  dogPersona.getPersonaAccounts();
        Assert.assertEquals(2, dogPersonaAccounts.size()); // Dog has 2 accounts.
        for (PersonaAccount pa : dogPersonaAccounts) {
                Assert.assertTrue(pa.getAccount().getIdentifier().equalsIgnoreCase(FACEBOOK_ID_CATDOG)
                        || pa.getAccount().getIdentifier().equalsIgnoreCase(DOG_EMAIL_ID));
                // System.out.println("Dog Account id : " + acct.getTypeSpecificId());
            }
        
        
        Collection<PersonaAccount> catPersonaAccounts =  catPersona.getPersonaAccounts();
        Assert.assertEquals(2, catPersonaAccounts.size()); // cat has 2 accounts.
        for (PersonaAccount pa:catPersonaAccounts) {
            //System.out.println("Cat Account id : " + acct.getTypeSpecificId());
            Assert.assertTrue(pa.getAccount().getIdentifier().equalsIgnoreCase(FACEBOOK_ID_CATDOG)
                        || pa.getAccount().getIdentifier().equalsIgnoreCase(CAT_WHATSAPP_ID));
        }
        
        // create account and Persona for Sherlock Holmes.
       // Create a Skype Account
        CentralRepoAccount holmesSkypeAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(skypeAccountType, HOLMES_SKYPE_ID);
         
         // Add an instance of Skype account to Case3/DS1
         CorrelationAttributeInstance skypeAcctInstance = new CorrelationAttributeInstance(skypeInstanceType, HOLMES_SKYPE_ID,
                    -1,
                    case3,
                    dataSource1fromCase3,
                    "path8",
                    "",
                    TskData.FileKnown.UNKNOWN,
                    1011L, 
                  holmesSkypeAccount.getId());
        CentralRepository.getInstance().addArtifactInstance(skypeAcctInstance);
        
        
        // Create a person for the Skype account
        Persona holmesPersona = Persona.createPersonaForAccount(HOLMES_PERSONA_NAME, 
                                 "Has a Pipe in his mouth." , Persona.PersonaStatus.ACTIVE, 
                                 holmesSkypeAccount, "The name says it all.", Persona.Confidence.LOW );
        
        Assert.assertNotNull(holmesPersona);
        Assert.assertTrue(holmesPersona.getName().equalsIgnoreCase(HOLMES_PERSONA_NAME));
        
        
     
        // Test that getting cases for Persona 
        Collection<CorrelationCase> dogCases = dogPersona.getCases();
        Assert.assertEquals(2, dogCases.size()); // dog appears in 2 cases.
        for (CorrelationCase dc: dogCases) {
            Assert.assertTrue(dc.getCaseUUID().equalsIgnoreCase(CASE_1_UUID) 
                             || dc.getCaseUUID().equalsIgnoreCase(CASE_2_UUID));
            //System.out.println("Dog Case UUID : " + dc.getCaseUUID());
        }
        
        Collection<CorrelationCase> catCases = catPersona.getCases();
        Assert.assertEquals(2, catCases.size()); // cat appears in 2 cases.
        for (CorrelationCase cc: catCases) {
             Assert.assertTrue(cc.getCaseUUID().equalsIgnoreCase(CASE_1_UUID) 
                             || cc.getCaseUUID().equalsIgnoreCase(CASE_2_UUID));
            //System.out.println("Cat Case UUID : " + cc.getCaseUUID());
        }
        
        Collection<CorrelationCase> holmesCases = holmesPersona.getCases();
        Assert.assertEquals(1, holmesCases.size()); // Holmes appears in 1 case.
        for (CorrelationCase hc: holmesCases) {
            Assert.assertTrue(hc.getCaseUUID().equalsIgnoreCase(CASE_3_UUID));
            //System.out.println("Holmes Case UUID : " + hc.getCaseUUID());
        }
        
        
        // Test that getting data sources for the Persona - 
        Collection<CorrelationDataSource> dogDatasources = dogPersona.getDataSources();
        Assert.assertEquals(3, dogDatasources.size()); // dog appears in 2 cases in 3 data sources.
        for (CorrelationDataSource dds: dogDatasources) {
            Assert.assertTrue(dds.getDeviceID().equalsIgnoreCase(DS1_DEVICEID)
                    || dds.getDeviceID().equalsIgnoreCase(DS2_DEVICEID)
                    || dds.getDeviceID().equalsIgnoreCase(DS3_DEVICEID));
            //System.out.println("Dog DS DeviceID : " + dds.getDeviceID());
        }
        
         Collection<CorrelationDataSource> catDatasources = catPersona.getDataSources();
        Assert.assertEquals(3, catDatasources.size()); // cat appears in 2 cases in 3 data sources.
        for (CorrelationDataSource cds: catDatasources) {
            Assert.assertTrue(cds.getDeviceID().equalsIgnoreCase(DS1_DEVICEID)
                    || cds.getDeviceID().equalsIgnoreCase(DS2_DEVICEID)
                    || cds.getDeviceID().equalsIgnoreCase(DS3_DEVICEID));
            //System.out.println("Cat DS DeviceID : " + cds.getDeviceID());
        }
        
        Collection<CorrelationDataSource> holmesDatasources = holmesPersona.getDataSources();
        Assert.assertEquals(1, holmesDatasources.size()); // Holmes appears in 1 cases in 1 data source.
        for (CorrelationDataSource hds: holmesDatasources) {
             Assert.assertTrue(hds.getDeviceID().equalsIgnoreCase(DS4_DEVICEID));
            //System.out.println("Holmes DS DeviceID : " + hds.getDeviceID());
        }
        
        // Test getting peronas by case.
         
        // Test that getting all Personas for Case 1  - Case1 has 2 persona - Cat & Dog
        Collection<Persona> case1Persona = Persona.getPersonasForCase(case1);
        Assert.assertEquals(2, case1Persona.size()); // 
        
        // Test that getting all Personas for Case 2  - Case2 has 2 persona - Cat & Dog
        Collection<Persona> case2Persona = Persona.getPersonasForCase(case2);
        Assert.assertEquals(2, case2Persona.size()); // 
        
         // Test that getting all Personas for Case 3  - Case3 has 1 persona - Holmes
        Collection<Persona> case3Persona = Persona.getPersonasForCase(case3);
        Assert.assertEquals(1, case3Persona.size()); // 
        
         // Test that getting all Personas for Case 4  - Case4 has no persona
        Collection<Persona> case4Persona = Persona.getPersonasForCase(case4);
        Assert.assertEquals(0, case4Persona.size()); // 
          
    
        // Test getting peronas by data source.
    
        // Test that getting all Personas for DS 1 
        Collection<Persona> ds1Persona = Persona.getPersonasForDataSource(dataSource1fromCase1);
        Assert.assertEquals(2, ds1Persona.size()); // 
        
        Collection<Persona> ds2Persona = Persona.getPersonasForDataSource(dataSource2fromCase1);
        Assert.assertEquals(2, ds2Persona.size()); // 
        
        Collection<Persona> ds3Persona = Persona.getPersonasForDataSource(dataSource1fromCase2);
        Assert.assertEquals(2, ds3Persona.size()); // 
        
        Collection<Persona> ds4Persona = Persona.getPersonasForDataSource(dataSource1fromCase3);
        Assert.assertEquals(1, ds4Persona.size()); // 
        
        Collection<Persona> ds5Persona = Persona.getPersonasForDataSource(dataSource2fromCase3);
        Assert.assertEquals(0, ds5Persona.size()); // 
        
        Collection<Persona> ds6Persona = Persona.getPersonasForDataSource(dataSource1fromCase4);
        Assert.assertEquals(0, ds6Persona.size()); // 
        
        
        }
         catch (CentralRepoException | CorrelationAttributeNormalizationException | InvalidAccountIDException ex) {
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
                Persona persona = Persona.createPersonaForAccount(null, "A persona with no name",
                        Persona.PersonaStatus.ACTIVE, emailAccount1, "The person lost his name", Persona.Confidence.LOW);
                
                // Verify Persona has a default name
                Assert.assertEquals(Persona.getDefaultName(), persona.getName());
                
            } catch (InvalidAccountIDException | CentralRepoException ex) {
                Assert.fail("No name persona test failed. Exception: " + ex);
            }
        }
        
        
     }
    
    /**
     * Tests searching of Persona by persona name.
     */
     public void testPersonaSearchByName() {
         
        // Test1: create Personas with similar names.
        {
            try {
                // Create an email account
                CentralRepoAccount emailAccount1 = CentralRepository.getInstance()
                        .getOrCreateAccount(emailAccountType, EMAIL_ID_1);

                // Create all personas with same comment.
                final String personaComment = "Creator of Jungle Book.";
                
                // Create a Persona with name "Rudyard Kipling"
                Persona.createPersonaForAccount("Rudyard Kipling", personaComment,
                        Persona.PersonaStatus.ACTIVE, emailAccount1, "", Persona.Confidence.LOW);
                
                // Create a Persona with name "Rudy"
                Persona.createPersonaForAccount("Rudy", personaComment,
                        Persona.PersonaStatus.ACTIVE, emailAccount1, "", Persona.Confidence.LOW);
                
                 
                // Create a Persona with name "Kipling Senior"
                Persona.createPersonaForAccount("Kipling Senior", personaComment,
                        Persona.PersonaStatus.ACTIVE, emailAccount1, "", Persona.Confidence.LOW);
                
                // Create a Persona with name "Senor Kipling"
                Persona.createPersonaForAccount("Senor Kipling", personaComment,
                        Persona.PersonaStatus.ACTIVE, emailAccount1, "", Persona.Confidence.LOW);
                
                
                // Test 1 Search "kipling" - expect 3 matches
                Collection<Persona> personaSearchResult = Persona.getPersonaByName("kipling");
                Assert.assertEquals(3, personaSearchResult.size());
                for (Persona p: personaSearchResult) {
                    Assert.assertTrue(p.getComment().equalsIgnoreCase(personaComment));
                }
                
                // Search 'Rudy' -  expect 2 matches
                personaSearchResult = Persona.getPersonaByName("Rudy");
                Assert.assertEquals(2, personaSearchResult.size());
               
                
                // Search 'Sen' - expect 2 matches
                personaSearchResult = Persona.getPersonaByName("Sen");
                Assert.assertEquals(2, personaSearchResult.size());
                
                
                // Search 'IPL' - expect 3 matches
                personaSearchResult = Persona.getPersonaByName("IPL");
                Assert.assertEquals(3, personaSearchResult.size());
                
              
                // Serach "Rudyard Kipling" - expect 1 match
                personaSearchResult = Persona.getPersonaByName("Rudyard Kipling");
                Assert.assertEquals(1, personaSearchResult.size());
                Assert.assertTrue(personaSearchResult.iterator().next().getName().equalsIgnoreCase("Rudyard Kipling"));
                
                // Search '' - expect ALL (4) to match
                personaSearchResult = Persona.getPersonaByName("");
                Assert.assertEquals(4, personaSearchResult.size());
                
                
            } catch (InvalidAccountIDException | CentralRepoException ex) {
                Assert.fail("No name persona test failed. Exception: " + ex);
            }
        }
        
        
     }
     
     
    /**
     * Tests searching of Persona by account identifier substrings.
     */
     public void testPersonaSearchByAccountIdentifier() {
         
        // Test1: create Personas with similar names.
        {
            try {
                // Create an email account1
                CentralRepoAccount emailAccount1 = CentralRepository.getInstance()
                        .getOrCreateAccount(emailAccountType, "joeexotic555@yahoo.com");

                // Create all personas with same comment.
                final String personaComment = "Comment used to create a persona";
                
                // Create a Persona with name "Joe Exotic" associated with the email address
                Persona.createPersonaForAccount("Joe Exotic", personaComment,
                        Persona.PersonaStatus.ACTIVE, emailAccount1, "", Persona.Confidence.LOW);
                
                // Create a Persona with name "Tiger King" associated with the email address
                Persona.createPersonaForAccount("Tiger King", personaComment,
                        Persona.PersonaStatus.ACTIVE, emailAccount1, "", Persona.Confidence.LOW);
                

                
                // Create an phone account with number "+1 999 555 3366"
                CentralRepoAccount phoneAccount1 = CentralRepository.getInstance()
                        .getOrCreateAccount(phoneAccountType, "+1 999 555 3366");

               
                // Create a Persona with name "Carol Baskin" associated 
                Persona.createPersonaForAccount("Carol Baskin", personaComment,
                        Persona.PersonaStatus.ACTIVE, phoneAccount1, "", Persona.Confidence.LOW);
                
                // Create a Persona with no name  assoctaed with 
                Persona.createPersonaForAccount(null, personaComment,
                        Persona.PersonaStatus.ACTIVE, phoneAccount1, "", Persona.Confidence.LOW);
                
                
                
                 // Create another email account1
                CentralRepoAccount emailAccount2 = CentralRepository.getInstance()
                        .getOrCreateAccount(emailAccountType, "jodoe@mail.com");

             
              
                // Create a Persona with name "John Doe" associated with the email address
                Persona.createPersonaForAccount("John Doe", personaComment,
                        Persona.PersonaStatus.ACTIVE, emailAccount2, "", Persona.Confidence.LOW);
                
                Persona.createPersonaForAccount("Joanne Doe", personaComment,
                        Persona.PersonaStatus.ACTIVE, emailAccount2, "", Persona.Confidence.LOW);
                
                
                
                // Test1 Search on 'joe' - should get 2 
                Collection<PersonaAccount> personaSearchResult = PersonaAccount.getPersonaAccountsForIdentifierLike("joe");
                Assert.assertEquals(2, personaSearchResult.size());
                for (PersonaAccount pa: personaSearchResult) {
                    Assert.assertTrue(pa.getAccount().getIdentifier().contains("joe"));
                }
                
                //  Search on 'exotic' - should get 2 
                personaSearchResult = PersonaAccount.getPersonaAccountsForIdentifierLike("exotic");
                Assert.assertEquals(2, personaSearchResult.size());
                for (PersonaAccount pa: personaSearchResult) {
                    Assert.assertTrue(pa.getAccount().getIdentifier().contains("exotic"));
                }
                
                // Test1 Search on '999' - should get 2 
                personaSearchResult = PersonaAccount.getPersonaAccountsForIdentifierLike("999");
                Assert.assertEquals(2, personaSearchResult.size());
                for (PersonaAccount pa: personaSearchResult) {
                    Assert.assertTrue(pa.getAccount().getIdentifier().contains("999"));
                }
                
                // Test1 Search on '555' - should get 4
                personaSearchResult = PersonaAccount.getPersonaAccountsForIdentifierLike("555");
                Assert.assertEquals(4, personaSearchResult.size());
                for (PersonaAccount pa: personaSearchResult) {
                    Assert.assertTrue(pa.getAccount().getIdentifier().contains("555"));
                }
                
                // Test1 Search on 'doe' - should get 2
                personaSearchResult = PersonaAccount.getPersonaAccountsForIdentifierLike("doe");
                Assert.assertEquals(2, personaSearchResult.size());
                for (PersonaAccount pa: personaSearchResult) {
                    Assert.assertTrue(pa.getAccount().getIdentifier().contains("doe"));
                }
                 
                // Test1 Search on '@' - should get 4
                personaSearchResult = PersonaAccount.getPersonaAccountsForIdentifierLike("@");
                Assert.assertEquals(4, personaSearchResult.size());
                for (PersonaAccount pa: personaSearchResult) {
                    Assert.assertTrue(pa.getAccount().getIdentifier().contains("@"));
                }
                
                // Test1 Search on '' - should get ALL (6)
                personaSearchResult = PersonaAccount.getPersonaAccountsForIdentifierLike("");
                Assert.assertEquals(6, personaSearchResult.size());
                
                
            } catch (InvalidAccountIDException | CentralRepoException ex) {
                Assert.fail("No name persona test failed. Exception: " + ex);
            }
        }
        
     }
     
     /**
      * Tests searching of accounts.
      */
     public void testAccountSearchByAccountIdentifier() {
         
        // Test1: create some accounts.
        {
            try {
                // Create an email account1
                CentralRepository.getInstance()
                        .getOrCreateAccount(emailAccountType, "joeexotic555@yahoo.com");

             
                // Create an phone account with number "+1 999 555 3366"
                CentralRepository.getInstance()
                        .getOrCreateAccount(phoneAccountType, "+1 999 555 3366");

               
                 // Create another email account1
                CentralRepository.getInstance()
                        .getOrCreateAccount(emailAccountType, "jodoe@mail.com");

               
                 // create a WhatsApp account for cat, add 2 instances, and then add that to Cat persona
                CentralRepository.getInstance()
                    .getOrCreateAccount(whatsAppAccountType, CAT_WHATSAPP_ID);
                
                
                
                // Get all accounts - should get 4 
                Collection<CentralRepoAccount> allAccounts = CentralRepoAccount.getAllAccounts();
                Assert.assertEquals(4, allAccounts.size());
                
                // Get accounts with substring 555
                Collection<CentralRepoAccount> accountsWithSubstring555 = CentralRepoAccount.getAccountsWithIdentifierLike("555");
                Assert.assertEquals(2, accountsWithSubstring555.size());
                for (CentralRepoAccount acc: accountsWithSubstring555) {
                    Assert.assertTrue(acc.getIdentifier().contains("555"));
                }
                
                 // Get accounts with substring 'jo'
                Collection<CentralRepoAccount> accountsWithSubstringJo = CentralRepoAccount.getAccountsWithIdentifierLike("JO");
                Assert.assertEquals(2, accountsWithSubstringJo.size());
                for (CentralRepoAccount acc: accountsWithSubstringJo) {
                    Assert.assertTrue(acc.getIdentifier().contains("jo"));
                }
                
                // Get account with exact match 
                Collection<CentralRepoAccount> accountsWithKnownIdentifier = CentralRepoAccount.getAccountsWithIdentifier("joeexotic555@yahoo.com");
                Assert.assertEquals(1, accountsWithKnownIdentifier.size());
                for (CentralRepoAccount acc: accountsWithKnownIdentifier) {
                    Assert.assertTrue(acc.getIdentifier().contains("joeexotic555@yahoo.com"));
                }
                
                // Get account with exact match 
                Collection<CentralRepoAccount> accountsWithKnownIdentifier2 = CentralRepoAccount.getAccountsWithIdentifier(CAT_WHATSAPP_ID);
                Assert.assertEquals(1, accountsWithKnownIdentifier2.size());
                for (CentralRepoAccount acc: accountsWithKnownIdentifier2) {
                    Assert.assertTrue(acc.getIdentifier().contains(CAT_WHATSAPP_ID));
                }
                
                // Get account that doesnt exists
                Collection<CentralRepoAccount> accountsWithUnknownIdentifier = CentralRepoAccount.getAccountsWithIdentifier("IdDoesntExist");
                Assert.assertEquals(0, accountsWithUnknownIdentifier.size());
                
                
            } catch (InvalidAccountIDException | CentralRepoException ex) {
                Assert.fail("No name persona test failed. Exception: " + ex);
            }
        }
        
     }
      
     /**
     * Tests the getOrInsertExaminer() api.
     */
     public void testExaminers() {
         
         try {
             String examinerName = "abcdefg";
             CentralRepoExaminer examiner = CentralRepository.getInstance().getOrInsertExaminer(examinerName);
             Assert.assertTrue(examiner.getLoginName().equalsIgnoreCase(examinerName));
             
             examinerName = "";
             examiner = CentralRepository.getInstance().getOrInsertExaminer(examinerName);
             Assert.assertTrue(examiner.getLoginName().equalsIgnoreCase(examinerName));
             
             examinerName = "D'Aboville";
             examiner = CentralRepository.getInstance().getOrInsertExaminer(examinerName);
             Assert.assertTrue(examiner.getLoginName().equalsIgnoreCase(examinerName));
             
         } catch (CentralRepoException ex) {
             Assert.fail("Examiner tests failed. Exception: " + ex);
         }
         
     }
}
