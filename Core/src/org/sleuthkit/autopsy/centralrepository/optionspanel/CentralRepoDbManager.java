/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.awt.Cursor;
import java.io.File;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoPlatforms;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.PostgresCentralRepoSettings;
import org.sleuthkit.autopsy.centralrepository.datamodel.SqliteCentralRepoSettings;
import org.sleuthkit.autopsy.centralrepository.optionspanel.DatabaseTestResult;
import org.sleuthkit.autopsy.coreutils.Logger;


public class CentralRepoDbManager {
    private static final String CENTRAL_REPO_DB_NAME = "central_repository";
    private static final String CENTRAL_REPO_SQLITE_EXT = ".db";
    
    private static final Logger logger = Logger.getLogger(CentralRepoDbManager.class.getName());
    
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
    
    /**
     * prompts user based on testing status (i.e. failure to connect, invalid schema, db does not exist, etc.)
     * @param warnDoesNotExist whether or not to prompt the user should the database not exist (otherwise silently create the db)
     * @return whether or not the ultimate status after prompts is okay to continue
     */
    @NbBundle.Messages({"EamDbSettingsDialog.okButton.corruptDatabaseExists.title=Error Loading Database",
        "EamDbSettingsDialog.okButton.corruptDatabaseExists.message=Database exists but is not the right format. Manually delete it or choose a different path (if applicable).",
        "EamDbSettingsDialog.okButton.createDbDialog.title=Database Does Not Exist",
        "EamDbSettingsDialog.okButton.createDbDialog.message=Database does not exist, would you like to create it?",
        "EamDbSettingsDialog.okButton.databaseConnectionFailed.title=Database Connection Failed",
        "EamDbSettingsDialog.okButton.databaseConnectionFailed.message=Unable to connect to database please check your settings and try again."})
    private boolean promptTestStatusWarnings(boolean warnDoesNotExist) {
        if (testingStatus == DatabaseTestResult.CONNECTION_FAILED) {
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.EamDbSettingsDialog_okButton_databaseConnectionFailed_message(),
                    Bundle.EamDbSettingsDialog_okButton_databaseConnectionFailed_title(),
                    JOptionPane.WARNING_MESSAGE);
        } else if (testingStatus == DatabaseTestResult.SCHEMA_INVALID) {
            // There's an existing database or file, but it's not in our format. 
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.EamDbSettingsDialog_okButton_corruptDatabaseExists_message(),
                    Bundle.EamDbSettingsDialog_okButton_corruptDatabaseExists_title(),
                    JOptionPane.WARNING_MESSAGE);
        } else if (testingStatus == DatabaseTestResult.DB_DOES_NOT_EXIST) {
            //database doesn't exist do you want to create
            boolean createDb = (!warnDoesNotExist || 
                    JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.EamDbSettingsDialog_okButton_createDbDialog_message(),
                    Bundle.EamDbSettingsDialog_okButton_createDbDialog_title(),
                    JOptionPane.YES_NO_OPTION));
            
            if (createDb)
                createDb();
        }

        return (testingStatus == DatabaseTestResult.TESTEDOK);
    }

    
    
    
    

    @NbBundle.Messages({"EamDbSettingsDialog.okButton.createDbError.title=Unable to Create Database",
        "EamDbSettingsDialog.okButton.createSQLiteDbError.message=Unable to create SQLite Database, please ensure location exists and you have write permissions and try again.",
        "EamDbSettingsDialog.okButton.createPostgresDbError.message=Unable to create Postgres Database, please ensure address, port, and login credentials are correct for Postgres server and try again."})
    private boolean createDb() {
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
