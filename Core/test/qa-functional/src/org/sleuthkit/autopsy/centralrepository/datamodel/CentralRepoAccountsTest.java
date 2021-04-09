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
import java.util.Optional;
import junit.framework.Assert;
import junit.framework.TestCase;
import junit.framework.Test;

import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount.CentralRepoAccountType;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.InvalidAccountIDException;

/**
 * Tests the Account APIs on the Central Repository.
 */
public class CentralRepoAccountsTest extends TestCase {
    
    private final Path testDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "CentralRepoDatamodelTest");

    // NbModuleSuite requires these tests use Junit 3.8
    // Extension of the TestCase class is how tests were defined and used
    // in Junit 3.8
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CentralRepoAccountsTest.class).
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
    }
    
    // This function is run after every test, NOT after the entire collection of 
    // tests defined in the class are run.
    @Override
    public void tearDown() throws CentralRepoException, IOException {
        // Close and delete the test case and central repo db
        if (CentralRepository.isEnabled()) {
            CentralRepository.getInstance().shutdownConnections();
        }

        FileUtil.deleteDir(testDirectory.toFile());
    }

    public void testPredefinedAccountTypes() {
        for (Account.Type expectedAccountType : Account.Type.PREDEFINED_ACCOUNT_TYPES) {
            // Skip DEVICE account, should not be in central repository.
            if(expectedAccountType == Account.Type.DEVICE) continue;
            
            try {
                Optional<CentralRepoAccountType> optCrAccountType = CentralRepository.getInstance()
                        .getAccountTypeByName(expectedAccountType.getTypeName());
                Assert.assertTrue(optCrAccountType.isPresent());
                
                Account.Type actualAccountType = optCrAccountType.get().getAcctType();
                Assert.assertEquals(expectedAccountType, actualAccountType);
            } catch (CentralRepoException ex) {
                Assert.fail("Didn't expect an exception here. Exception: " + ex);
            }
        }
    }
    
    public void testRejectionOfDeviceAccountType() {
        try {
            Account.Type deviceAccount = Account.Type.DEVICE;
            Optional<CentralRepoAccountType> optType = CentralRepository.getInstance()
                    .getAccountTypeByName(deviceAccount.getTypeName());
            Assert.assertFalse(optType.isPresent());
        } catch (CentralRepoException ex) {
            Assert.fail("Didn't expect an exception here. Exception: " + ex);
        }
    }

    public void testNonExistentAccountType() {
        try {
            Optional<CentralRepoAccountType> optType = CentralRepository.getInstance()
                    .getAccountTypeByName("NotARealAccountType");
            Assert.assertFalse(optType.isPresent());
        } catch (CentralRepoException ex) {
            Assert.fail("Didn't expect an exception here. Exception: " + ex);
        }
    }
    
    public void testCreatingAccount() {
        try {
            Account.Type facebookAccountType = Account.Type.FACEBOOK;
            Optional<CentralRepoAccountType> optExpectedAccountType = CentralRepository.getInstance()
                    .getAccountTypeByName(facebookAccountType.getTypeName());
            assertTrue(optExpectedAccountType.isPresent());
            
            // Create the account
            CentralRepository.getInstance()
                    .getOrCreateAccount(optExpectedAccountType.get(), "+1 401-231-2552");
        } catch (InvalidAccountIDException | CentralRepoException ex) {
             Assert.fail("Didn't expect an exception here. Exception: " + ex);
        }
    }
    
    public void testRetreivingAnAccount() {
        try {
            Account.Type facebookAccountType = Account.Type.FACEBOOK;
            Optional<CentralRepoAccountType> optExpectedAccountType = CentralRepository
                    .getInstance()
                    .getAccountTypeByName(facebookAccountType.getTypeName());
            assertTrue(optExpectedAccountType.isPresent());
            
            // Create the account
            CentralRepository.getInstance()
                    .getOrCreateAccount(optExpectedAccountType.get(), "+1 441-231-2552");
            
            // Retrieve the account
            CentralRepoAccount actualAccount = CentralRepository.getInstance()
                    .getOrCreateAccount(optExpectedAccountType.get(), "+1 441-231-2552");
            
            Assert.assertEquals(optExpectedAccountType.get(), actualAccount.getAccountType());
            Assert.assertEquals("+1 441-231-2552", actualAccount.getIdentifier());
        } catch (InvalidAccountIDException | CentralRepoException ex) {
             Assert.fail("Didn't expect an exception here. Exception: " + ex);
        }
    }
}
