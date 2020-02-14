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

    /**
     * Upgrade the current Central Reposity schema to the newest version. If the
     * upgrade fails, the Central Repository will be disabled and the current
     * settings will be cleared.
     */
    @NbBundle.Messages(value = {"EamDbUtil.centralRepoDisabled.message= The Central Repository has been disabled.", "EamDbUtil.centralRepoUpgradeFailed.message=Failed to upgrade Central Repository.", "EamDbUtil.centralRepoConnectionFailed.message=Unable to connect to Central Repository.", "EamDbUtil.exclusiveLockAquisitionFailure.message=Unable to acquire exclusive lock for Central Repository."})
    public static void upgradeDatabase() throws CentralRepoException {
        if (!CentralRepository.isEnabled()) {
            EamDbSettingsDialog dialog = new EamDbSettingsDialog();
            dialog.promptUserForSetup();
        }
        CentralRepository db = null;
        CoordinationService.Lock lock = null;
        //get connection
        try {
            try {
                db = CentralRepository.getInstance();
            } catch (CentralRepoException ex) {
                CentralRepoDbUtil.LOGGER.log(Level.SEVERE, "Error updating central repository, unable to make connection", ex);
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
                    CentralRepoDbUtil.LOGGER.log(Level.SEVERE, "Error updating central repository, unable to acquire exclusive lock", ex);
                    throw new CentralRepoException("Error updating central repository, unable to acquire exclusive lock", Bundle.EamDbUtil_exclusiveLockAquisitionFailure_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
                }
                try {
                    db.upgradeSchema();
                } catch (CentralRepoException ex) {
                    CentralRepoDbUtil.LOGGER.log(Level.SEVERE, "Error updating central repository", ex);
                    throw new CentralRepoException("Error updating central repository", ex.getUserMessage() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
                } catch (SQLException ex) {
                    CentralRepoDbUtil.LOGGER.log(Level.SEVERE, "Error updating central repository", ex);
                    throw new CentralRepoException("Error updating central repository", Bundle.EamDbUtil_centralRepoUpgradeFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
                } catch (IncompatibleCentralRepoException ex) {
                    CentralRepoDbUtil.LOGGER.log(Level.SEVERE, "Error updating central repository", ex);
                    throw new CentralRepoException("Error updating central repository", ex.getMessage() + "\n\n" + Bundle.EamDbUtil_centralRepoUpgradeFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
                } finally {
                    if (lock != null) {
                        try {
                            lock.release();
                        } catch (CoordinationService.CoordinationServiceException ex) {
                            CentralRepoDbUtil.LOGGER.log(Level.SEVERE, "Error releasing database lock", ex);
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
                CentralRepoDbUtil.LOGGER.log(Level.SEVERE, "Error shutting down central repo connection pool", ex2);
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
    


    
    
    
    

    @NbBundle.Messages({"EamDbSettingsDialog.okButton.createDbError.title=Unable to Create Database",
        "EamDbSettingsDialog.okButton.createSQLiteDbError.message=Unable to create SQLite Database, please ensure location exists and you have write permissions and try again.",
        "EamDbSettingsDialog.okButton.createPostgresDbError.message=Unable to create Postgres Database, please ensure address, port, and login credentials are correct for Postgres server and try again."})
    public boolean createDb() {
        boolean result = false;
        boolean dbCreated = true;
        switch (selectedPlatform) {
            case POSTGRESQL:
                if (!dbSettingsPostgres.verifyDatabaseExists()) {
                    dbCreated = dbSettingsPostgres.createDatabase();
                }
                if (dbCreated) {
                    result = dbSettingsPostgres.initializeDatabaseSchema()
                            && dbSettingsPostgres.insertDefaultDatabaseContent();
                }
                if (!result) {
                    // Remove the incomplete database
                    if (dbCreated) {
                        dbSettingsPostgres.deleteDatabase();
                    }

                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                            Bundle.EamDbSettingsDialog_okButton_createPostgresDbError_message(),
                            Bundle.EamDbSettingsDialog_okButton_createDbError_title(),
                            JOptionPane.WARNING_MESSAGE);
                    logger.severe("Unable to initialize database schema or insert contents into central repository.");
                    return false;
                }
                break;
            case SQLITE:
                if (!dbSettingsSqlite.dbDirectoryExists()) {
                    dbCreated = dbSettingsSqlite.createDbDirectory();
                }
                if (dbCreated) {
                    result = dbSettingsSqlite.initializeDatabaseSchema()
                            && dbSettingsSqlite.insertDefaultDatabaseContent();
                }
                if (!result) {
                    if (dbCreated) {
                        dbSettingsSqlite.deleteDatabase();
                    }

                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                            Bundle.EamDbSettingsDialog_okButton_createSQLiteDbError_message(),
                            Bundle.EamDbSettingsDialog_okButton_createDbError_title(),
                            JOptionPane.WARNING_MESSAGE);
                    logger.severe("Unable to initialize database schema or insert contents into central repository.");
                    return false;
                }
                break;
        }
        testingStatus = DatabaseTestResult.TESTEDOK;
        return true;
    }

    
    /**
     * saves a new central repository based on current settings
     */
    @NbBundle.Messages({"EamDbSettingsDialog.okButton.errorTitle.text=Restart Required.",
        "EamDbSettingsDialog.okButton.errorMsg.text=Please restart Autopsy to begin using the new database platform.",
        "EamDbSettingsDialog.okButton.connectionErrorMsg.text=Failed to connect to central repository database."})
    private void saveNewCentralRepo() {
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
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        Bundle.EamDbSettingsDialog_okButton_errorMsg_text(),
                        Bundle.EamDbSettingsDialog_okButton_errorTitle_text(),
                        JOptionPane.WARNING_MESSAGE);
            });
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
                    logger.log(Level.SEVERE, Bundle.EamDbSettingsDialog_okButton_connectionErrorMsg_text(), ex); //NON-NLS
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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
                    logger.log(Level.SEVERE, Bundle.EamDbSettingsDialog_okButton_connectionErrorMsg_text(), ex);  //NON-NLS
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    return;
                }
                break;
            case DISABLED:
                break;
        }
    }

    public DatabaseTestResult getStatus() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public CentralRepoPlatforms getSelectedPlatform() {
        return selectedPlatform;
    }
    
    
    static class DatabaseSettingsValidResult {
        private final String errorMessage;
        private final boolean success;

        public DatabaseSettingsValidResult(String errorMessage, boolean success) {
            this.errorMessage = errorMessage;
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }
    }
    
    
    /**
     * Tests whether or not the database settings are valid.
     *
     * @return True or false.
     */
    private DatabaseSettingsValidResult databaseSettingsAreValid(
            String tbDbHostname, Integer tbDbPort, String tbDbUsername, String tfDatabasePath, String jpDbPassword) {
        
        boolean result = true;
        StringBuilder guidanceText = new StringBuilder();

        switch (selectedPlatform) {
            case POSTGRESQL:
                try {
                    dbSettingsPostgres.setHost(tbDbHostname);
                } catch (CentralRepoException ex) {
                    guidanceText.append(ex.getMessage());
                    result = false;
                }

                try {
                    dbSettingsPostgres.setPort(tbDbPort);
                } catch (NumberFormatException | CentralRepoException ex) {
                    guidanceText.append(ex.getMessage());
                    result = false;
                }

                try {
                    dbSettingsPostgres.setDbName(CENTRAL_REPO_DB_NAME);
                } catch (CentralRepoException ex) {
                    guidanceText.append(ex.getMessage());
                    result = false;
                }

                try {
                    dbSettingsPostgres.setUserName(tbDbUsername);
                } catch (CentralRepoException ex) {
                    guidanceText.append(ex.getMessage());
                    result = false;
                }

                try {
                    dbSettingsPostgres.setPassword(jpDbPassword);
                } catch (CentralRepoException ex) {
                    guidanceText.append(ex.getMessage());
                    result = false;
                }
                break;
            case SQLITE:
                try {
                    File databasePath = new File(tfDatabasePath);
                    dbSettingsSqlite.setDbName(CENTRAL_REPO_DB_NAME + CENTRAL_REPO_SQLITE_EXT);
                    dbSettingsSqlite.setDbDirectory(databasePath.getPath());
                } catch (CentralRepoException ex) {
                    guidanceText.append(ex.getMessage());
                    result = false;
                }
                break;
        }
        
        return new DatabaseSettingsValidResult(guidanceText.toString(), result);
    }

    
    private DatabaseTestResult testDbSettings() {
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
    
    
    /**
     * Returns if changes to the central repository configuration were
     * successfully applied
     *
     * @return true if the database configuration was successfully changed false
     *         if it was not
     */
    boolean wasConfigurationChanged() {
        return configurationChanged;
    }
}
