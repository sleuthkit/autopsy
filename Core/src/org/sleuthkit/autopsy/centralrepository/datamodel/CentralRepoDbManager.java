/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.awt.Cursor;
import java.io.File;
import java.sql.SQLException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.optionspanel.EamDbSettingsDialog;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;


public class CentralRepoDbManager {
    private static final String CENTRAL_REPO_DB_NAME = "central_repository";
    private static final String CENTRAL_REPO_SQLITE_EXT = ".db";
    
    private static final Logger logger = Logger.getLogger(CentralRepoDbManager.class.getName());

    public static String getDefaultSqliteDbName() {
        return CENTRAL_REPO_DB_NAME + CENTRAL_REPO_SQLITE_EXT;
    }
        
    /**
     * Upgrade the current Central Reposity schema to the newest version. If the
     * upgrade fails, the Central Repository will be disabled and the current
     * settings will be cleared.
     */
    @NbBundle.Messages(value = {"EamDbUtil.centralRepoDisabled.message= The Central Repository has been disabled.", "EamDbUtil.centralRepoUpgradeFailed.message=Failed to upgrade Central Repository.", "EamDbUtil.centralRepoConnectionFailed.message=Unable to connect to Central Repository.", "EamDbUtil.exclusiveLockAquisitionFailure.message=Unable to acquire exclusive lock for Central Repository."})
    public static void upgradeDatabase() throws CentralRepoException {
        if (!CentralRepository.isEnabled()) {
            // TODO
//            EamDbSettingsDialog dialog = new EamDbSettingsDialog();
//            dialog.promptUserForSetup();
        }
        CentralRepository db = null;
        CoordinationService.Lock lock = null;
        //get connection
        try {
            try {
                db = CentralRepository.getInstance();
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error updating central repository, unable to make connection", ex);
                throw new CentralRepoException("Error updating central repository, unable to make connection", Bundle.EamDbUtil_centralRepoConnectionFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
            }
            //get lock necessary for upgrade
            if (db != null) {
                try {
                    // This may return null if locking isn't supported, which is fine. It will
                    // throw an exception if locking is supported but we can't get the lock
                    // (meaning the database is in use by another user)
                    lock = db.getExclusiveMultiUserDbLock();
                    //perform upgrade
                } catch (CentralRepoException ex) {
                    logger.log(Level.SEVERE, "Error updating central repository, unable to acquire exclusive lock", ex);
                    throw new CentralRepoException("Error updating central repository, unable to acquire exclusive lock", Bundle.EamDbUtil_exclusiveLockAquisitionFailure_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
                }
                try {
                    db.upgradeSchema();
                } catch (CentralRepoException ex) {
                    logger.log(Level.SEVERE, "Error updating central repository", ex);
                    throw new CentralRepoException("Error updating central repository", ex.getUserMessage() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error updating central repository", ex);
                    throw new CentralRepoException("Error updating central repository", Bundle.EamDbUtil_centralRepoUpgradeFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
                } catch (IncompatibleCentralRepoException ex) {
                    logger.log(Level.SEVERE, "Error updating central repository", ex);
                    throw new CentralRepoException("Error updating central repository", ex.getMessage() + "\n\n" + Bundle.EamDbUtil_centralRepoUpgradeFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
                } finally {
                    if (lock != null) {
                        try {
                            lock.release();
                        } catch (CoordinationService.CoordinationServiceException ex) {
                            logger.log(Level.SEVERE, "Error releasing database lock", ex);
                        }
                    }
                }
            } else {
                throw new CentralRepoException("Unable to connect to database", Bundle.EamDbUtil_centralRepoConnectionFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message());
            }
        } catch (CentralRepoException ex) {
            // Disable the central repo and clear the current settings.
            try {
                if (null != CentralRepository.getInstance()) {
                    CentralRepository.getInstance().shutdownConnections();
                }
            } catch (CentralRepoException ex2) {
                logger.log(Level.SEVERE, "Error shutting down central repo connection pool", ex2);
            }
            CentralRepoPlatforms.setSelectedPlatform(CentralRepoPlatforms.DISABLED.name());
            CentralRepoPlatforms.saveSelectedPlatform();
            throw ex;
        }
    }
    
    
    
    
    private DatabaseTestResult testingStatus;
    private CentralRepoPlatforms selectedPlatform;

    private PostgresCentralRepoSettings dbSettingsPostgres;
    private SqliteCentralRepoSettings dbSettingsSqlite;
    
    private boolean configurationChanged = false;
    
    
    public CentralRepoDbManager() {
        dbSettingsPostgres = new PostgresCentralRepoSettings();
        dbSettingsSqlite = new SqliteCentralRepoSettings();
        selectedPlatform = CentralRepoPlatforms.getSelectedPlatform();
        if (selectedPlatform == null || selectedPlatform.equals(CentralRepoPlatforms.DISABLED)) {
            selectedPlatform = CentralRepoPlatforms.POSTGRESQL;
        }
    }

    public PostgresCentralRepoSettings getDbSettingsPostgres() {
        return dbSettingsPostgres;
    }

    public SqliteCentralRepoSettings getDbSettingsSqlite() {
        return dbSettingsSqlite;
    }
    
    /**
     * Returns if changes to the central repository configuration were
     * successfully applied
     *
     * @return true if the database configuration was successfully changed false
     * if it was not
     */
    public boolean wasConfigurationChanged() {
        return configurationChanged;
    }
    
    public boolean createDb() throws CentralRepoException {
        boolean dbCreated = true;
        switch (selectedPlatform) {
            case POSTGRESQL:
                // if postgres database does not exist, attempt to create it
                if (!dbSettingsPostgres.verifyDatabaseExists()) {
                    dbCreated = dbSettingsPostgres.createDatabase();
                }

                // if the database still isn't created, we have a problem
                if (!dbCreated) {
                    logger.severe("Unable to create Postgres database for Central Repository at " + 
                            dbSettingsPostgres.getHost() + ":" + dbSettingsPostgres.getPort() + " with db name: " + 
                            dbSettingsPostgres.getDbName() + " and username: " + dbSettingsPostgres.getUserName());
                    
                    throw new CentralRepoException("Unable to create Postgres database for Central Repository.");
                }
                    
                
                boolean result = dbSettingsPostgres.initializeDatabaseSchema()
                        && dbSettingsPostgres.insertDefaultDatabaseContent();

                // if unable to initialize the schema, there's a problem as well
                if (!result) {
                    // Remove the incomplete database
                    if (dbCreated) {
                        dbSettingsPostgres.deleteDatabase();
                    }

                    String schemaError = "Unable to initialize database schema or insert contents into central repository.";
                    logger.severe(schemaError);
                    throw new CentralRepoException(schemaError);
                }

                break;
            case SQLITE:
                
                // if sqlite db does not exist, try to creat it
                if (!dbSettingsSqlite.dbDirectoryExists()) {
                    dbCreated = dbSettingsSqlite.createDbDirectory();
                }
                
                // if the database still isn't created, we have a problem
                if (!dbCreated) {
                    logger.severe("Unable to create Sqlite database for Central Repository at " + 
                        dbSettingsSqlite.getDbDirectory());
                    
                    throw new CentralRepoException("Unable to create SQLite database for Central Repository.");
                }
                
                result = dbSettingsSqlite.initializeDatabaseSchema()
                        && dbSettingsSqlite.insertDefaultDatabaseContent();

                if (!result) {
                    if (dbCreated) {
                        dbSettingsSqlite.deleteDatabase();
                    }
                    
                    String schemaError = "Unable to initialize database schema or insert contents into central repository.";
                    logger.severe(schemaError);
                    throw new CentralRepoException(schemaError);
                }
                break;
        }
        testingStatus = DatabaseTestResult.TESTEDOK;
        return true;
    }

    
    /**
     * saves a new central repository based on current settings
     */
    @NbBundle.Messages({"CentralRepoDbManager.connectionErrorMsg.text=Failed to connect to central repository database."})
    public void saveNewCentralRepo() throws CentralRepoException {
        /**
         * We have to shutdown the previous platform's connection pool first;
         * assuming it wasn't DISABLED. This will close any existing idle
         * connections.
         *
         * The next use of an EamDb API method will start a new connection pool
         * using those new settings.
         */
        try {
            CentralRepository previousDbManager = CentralRepository.getInstance();
            if (null != previousDbManager) {
                // NOTE: do not set/save the seleted platform before calling this.
                CentralRepository.getInstance().shutdownConnections();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Failed to close database connections in previously selected platform.", ex); // NON-NLS
            throw ex;
        }

        // Even if we fail to close the existing connections, make sure that we
        // save the new connection settings, so an Autopsy restart will correctly
        // start with the new settings.
        CentralRepoPlatforms.setSelectedPlatform(selectedPlatform.name());
        CentralRepoPlatforms.saveSelectedPlatform();

        switch (selectedPlatform) {
            case POSTGRESQL:
                // save the new PostgreSQL settings
                dbSettingsPostgres.saveSettings();
                // Load those newly saved settings into the postgres db manager instance
                //  in case we are still using the same instance.
                try {
                    CentralRepository.getInstance().updateSettings();
                    configurationChanged = true;
                } catch (CentralRepoException ex) {
                    logger.log(Level.SEVERE, Bundle.CentralRepoDbManager_connectionErrorMsg_text(), ex); //NON-NLS
                    return;
                }

                break;
            case SQLITE:
                // save the new SQLite settings
                dbSettingsSqlite.saveSettings();
                // Load those newly saved settings into the sqlite db manager instance
                //  in case we are still using the same instance.
                try {
                    CentralRepository.getInstance().updateSettings();
                    configurationChanged = true;
                } catch (CentralRepoException ex) {
                    logger.log(Level.SEVERE, Bundle.CentralRepoDbManager_connectionErrorMsg_text(), ex);  //NON-NLS
                    return;
                }
                break;
            case DISABLED:
                break;
        }
    }

    public DatabaseTestResult getStatus() {
        return testingStatus;
    }

    public CentralRepoPlatforms getSelectedPlatform() {
        return selectedPlatform;
    }

    public void clearStatus() {
        testingStatus = DatabaseTestResult.UNTESTED;
    }



    public void setSelectedPlatform(CentralRepoPlatforms newSelected) {
        selectedPlatform = newSelected;
        testingStatus = DatabaseTestResult.UNTESTED;
    }
    
    
    /**
     * Tests whether or not the database settings are valid.
     *
     * @return True or false.
     */
    public boolean testDatabaseSettingsAreValid (
            String tbDbHostname, String tbDbPort, String tbDbUsername, String tfDatabasePath, String jpDbPassword) throws CentralRepoException, NumberFormatException {
        
        boolean result = true;
        StringBuilder guidanceText = new StringBuilder();

        switch (selectedPlatform) {
            case POSTGRESQL:
                    dbSettingsPostgres.setHost(tbDbHostname);
                    dbSettingsPostgres.setPort(Integer.parseInt(tbDbPort));
                    dbSettingsPostgres.setDbName(CENTRAL_REPO_DB_NAME);
                    dbSettingsPostgres.setUserName(tbDbUsername);
                    dbSettingsPostgres.setPassword(jpDbPassword);
                break;
            case SQLITE:
                    File databasePath = new File(tfDatabasePath);
                    dbSettingsSqlite.setDbName(getDefaultSqliteDbName());
                    dbSettingsSqlite.setDbDirectory(databasePath.getPath());
                break;
            default:
                throw new IllegalStateException("Central Repo has an unknown selected platform: " + selectedPlatform);
        }
        
        return result;
    }

    public DatabaseTestResult testStatus() {
        switch (selectedPlatform) {
            case POSTGRESQL:
                if (dbSettingsPostgres.verifyConnection()) {
                    if (dbSettingsPostgres.verifyDatabaseExists()) {
                        if (dbSettingsPostgres.verifyDatabaseSchema()) {
                            testingStatus = DatabaseTestResult.TESTEDOK;
                        } else {
                            testingStatus = DatabaseTestResult.SCHEMA_INVALID;
                        }
                    } else {
                        testingStatus = DatabaseTestResult.DB_DOES_NOT_EXIST;
                    }
                } else {
                    testingStatus = DatabaseTestResult.CONNECTION_FAILED;
                }
                break;
            case SQLITE:
                if (dbSettingsSqlite.dbFileExists()) {
                    if (dbSettingsSqlite.verifyConnection()) {
                        if (dbSettingsSqlite.verifyDatabaseSchema()) {
                            testingStatus = DatabaseTestResult.TESTEDOK;
                        } else {
                            testingStatus = DatabaseTestResult.SCHEMA_INVALID;
                        }
                    } else {
                        testingStatus = DatabaseTestResult.SCHEMA_INVALID;
                    }
                } else {
                    testingStatus = DatabaseTestResult.DB_DOES_NOT_EXIST;
                }
                break;
        }

        return testingStatus;
    }
}
