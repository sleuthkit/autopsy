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
import junit.framework.TestCase;
import junit.framework.Test;
import org.apache.commons.io.FileUtils;

import org.netbeans.junit.NbModuleSuite;
import static org.sleuthkit.autopsy.centralrepository.datamodel.PersonaHelper.addAccountToPersona;
import org.sleuthkit.datamodel.Account;


/**
 *
 * Tests the Persona API in CentralRepository.
 */
public class CentralRepoPersonasTest  extends TestCase {
    
     private final Path testDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "CentralRepoDatamodelTest");
     
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
        
        // Add current logged in user to examiners table - since we delete the DB after every test.
        CentralRepository.getInstance().updateExaminers();
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
            CentralRepoAccount.CentralRepoAccountType phoneAccountType = CentralRepository
                    .getInstance()
                    .getAccountTypeByName( Account.Type.PHONE.getTypeName());
       
            CentralRepoAccount phoneAccount1 = CentralRepository.getInstance()
                    .getOrCreateAccount(phoneAccountType, "+1 441-231-2552");
            
            
            // Step 2: Create a Persona for the Account
            String personaName = "Baloo McDog";
            String comment = "The best dog ever";
            Persona.PersonaStatus status = Persona.PersonaStatus.ACTIVE;
            PersonaAccount pa1 = PersonaHelper.createPersonaForAccount(personaName, comment , status, phoneAccount1, "Because I said so", Persona.Confidence.LOW );
                

            Persona dogPersona = pa1.getPersona();
            
            // Verify Persona name, status etc.
            Assert.assertEquals(personaName, pa1.getPersona().getName());
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

            
            //Step 4: Add Persona metadata
            PersonaMetadata metadata1 = PersonaHelper.addPersonaMetadata(dogPersona, "Color", "Black", "He's got thick black hair.", Persona.Confidence.MEDIUM);
            PersonaMetadata metadata2 = PersonaHelper.addPersonaMetadata(dogPersona, "Gender", "Male", "Because...", Persona.Confidence.LOW);

            Assert.assertNotNull(metadata1);
            Assert.assertNotNull(metadata2);

            // get all aliases for persona
            Collection<PersonaAlias> aliases = PersonaHelper.getPersonaAliases(dogPersona.getId());
            Assert.assertEquals(2, aliases.size());
            for (PersonaAlias alias: aliases) {
                //System.out.println("Alias: "+ alias.getAlias()) ;
                Assert.assertFalse(alias.getAlias().isEmpty());
            }
            
            // get all metadata for persona 
            Collection<PersonaMetadata> metadataList = PersonaHelper.getPersonaMetadata(dogPersona.getId());
            Assert.assertEquals(2, metadataList.size());
            for (PersonaMetadata md: metadataList) {
                //System.out.println(String.format("Metadata: %s : %s", md.getName(), md.getValue())) ;
                Assert.assertFalse(md.getName().isEmpty());
                Assert.assertFalse(md.getValue().isEmpty());
            }
            
            
            // Step 5: associate another account with same persona
            CentralRepoAccount.CentralRepoAccountType facebookAccountType = CentralRepository
                    .getInstance()
                    .getAccountTypeByName(Account.Type.FACEBOOK.getTypeName());
            
            CentralRepoAccount catdogFBAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(facebookAccountType, "BalooSherkhan");
            
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
            
            // Create a Second Persona associated with same account
            String catPersonaName = "SherKhan";
            String comment2 = "The fiercest cat alive.";
           
            PersonaHelper.createPersonaForAccount(catPersonaName, comment2 , Persona.PersonaStatus.ACTIVE, catdogFBAccount, "Smells like a cat.", Persona.Confidence.LOW );
            
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
}
