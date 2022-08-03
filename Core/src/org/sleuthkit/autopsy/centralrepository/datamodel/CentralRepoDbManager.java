/*
 * Central Repository
 *
 * Copyright 2015-2020 Basis Technology Corp.
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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.sql.SQLException;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.CentralRepoSettings;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * This class contains business logic for saving and validating settings for
 * central repository.
 */
public class CentralRepoDbManager {

    private static final Logger logger = Logger.getLogger(CentralRepoDbManager.class.getName());

    private static final String CENTRAL_REPO_DB_NAME = "central_repository";
    private static final String CENTRAL_REPOSITORY_SETTINGS_KEY = CentralRepoSettings.getInstance().getModuleSettingsKey();
    private static final String DB_SELECTED_PLATFORM_KEY = "db.selectedPlatform";
    private static final String DISABLED_DUE_TO_FAILURE_KEY = "disabledDueToFailure";

    private static volatile CentralRepoDbChoice savedChoice = null;

    private static final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(CentralRepoDbManager.class);

    private static final Object dbChoiceLock = new Object();
    private static final Object disabledDueToFailureLock = new Object();

    /**
     * This saves the currently selected database choice and clears any
     * disabledDueToFailure flag.
     *
     * @param choice The choice to save.
     *
     * @return The newly saved choice.
     */
    public static CentralRepoDbChoice saveDbChoice(CentralRepoDbChoice choice) {
        return saveDbChoice(choice, true);
    }

    /**
     * This saves the currently selected database choice.
     *
     * @param choice                  The choice to save.
     * @param clearDisabledDueToError Whether or not to clear the
     *                                'disabledDueToFailure' settings key.
     *
     * @return The newly saved choice.
     */
    public static CentralRepoDbChoice saveDbChoice(CentralRepoDbChoice choice, boolean clearDisabledDueToError) {
        synchronized (dbChoiceLock) {
            // clear disabling due to a failure
            if (clearDisabledDueToError) {
                setDisabledDueToFailure(false);
            }

            // change the settings
            CentralRepoDbChoice newChoice = (choice == null) ? CentralRepoDbChoice.DISABLED : choice;
            CentralRepoDbChoice oldChoice = savedChoice;
            savedChoice = newChoice;
            ModuleSettings.setConfigSetting(CENTRAL_REPOSITORY_SETTINGS_KEY, DB_SELECTED_PLATFORM_KEY, newChoice.getSettingKey());
            propertyChangeSupport.firePropertyChange("savedChoice", oldChoice, newChoice);
            return newChoice;
        }

    }

    /**
     * This method indicates whether or not 'PostgreSQL using multi-user
     * settings' is a valid option.
     *
     * @return True if 'PostgreSQL using multi-user settings' is valid.
     */
    public static boolean isPostgresMultiuserAllowed() {
        // if multi user mode is not enabled, then this cannot be used
        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            return false;
        }
        // also validate the connection as well
        PostgresCentralRepoSettings multiUserSettings
                = new PostgresCentralRepoSettings(PostgresSettingsLoader.MULTIUSER_SETTINGS_LOADER);

        return multiUserSettings.testStatus() == DatabaseTestResult.TESTED_OK;
    }

    /**
     * This method loads the selectedPlatform boolean from the config file if it
     * is set.
     */
    public static CentralRepoDbChoice getSavedDbChoice() {
        synchronized (dbChoiceLock) {
            if (savedChoice == null) {
                String selectedPlatformString = ModuleSettings.getConfigSetting(CENTRAL_REPOSITORY_SETTINGS_KEY, DB_SELECTED_PLATFORM_KEY); // NON-NLS
                savedChoice = fromKey(selectedPlatformString);
            }

            return savedChoice;
        }
    }

    /**
     * This method disables the central repository and indicates through a flag
     * that this was due to a failure during database setup. This is used when
     * re-enabling multi-user as a flag to determine whether or not CR should be
     * re-enabled.
     */
    public static void disableDueToFailure() {
        CentralRepoDbUtil.setUseCentralRepo(false);
        setDisabledDueToFailure(true);
    }

    /**
     * This method sets whether or not the repository has been disabled due to a
     * database setup issue; This is used when re-enabling multi-user as a flag
     * to determine whether or not CR should be re-enabled.
     *
     * @param disabledDueToFailure Whether or not the repository has been
     *                             disabled due to a database setup issue.
     */
    private static void setDisabledDueToFailure(boolean disabledDueToFailure) {
        synchronized (disabledDueToFailureLock) {
            boolean oldValue = isDisabledDueToFailure();
            ModuleSettings.setConfigSetting(CENTRAL_REPOSITORY_SETTINGS_KEY, DISABLED_DUE_TO_FAILURE_KEY, Boolean.toString(disabledDueToFailure));
            propertyChangeSupport.firePropertyChange("disabledDueToFailure", oldValue, disabledDueToFailure);
        }
    }

    /**
     * This method retrieves setting whether or not the repository has been
     * disabled due to a database setup issue; this is used when re-enabling
     * multi-user as a flag to determine whether or not CR should be re-enabled.
     *
     * @return Whether or not the repository has been disabled due to a database
     *         setup issue.
     */
    public static boolean isDisabledDueToFailure() {
        synchronized (disabledDueToFailureLock) {
            return Boolean.toString(true).equals(ModuleSettings.getConfigSetting(CENTRAL_REPOSITORY_SETTINGS_KEY, DISABLED_DUE_TO_FAILURE_KEY));
        }
    }

    /**
     * This method adds a property change listener. NOTE: currently only
     * listening for changes in currently saved db choice and disabling due to
     * failure.
     *
     * @param listener The listener for the event.
     */
    public static void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * This method removes a propert change listener.
     *
     * @param listener The listener to remove.
     */
    public static void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    private static CentralRepoDbChoice fromKey(String keyName) {
        for (CentralRepoDbChoice dbChoice : CentralRepoDbChoice.values()) {
            if (dbChoice.getSettingKey().equalsIgnoreCase(keyName)) {
                return dbChoice;
            }
        }

        return CentralRepoDbChoice.DISABLED;
    }

    /**
     * This method obtains the database connectivity for central repository.
     *
     * @return The CentralRepository object that will be used for connection.
     *
     * @throws CentralRepoException
     */
    private static CentralRepository obtainCentralRepository() throws CentralRepoException {
        //get connection
        try {
            return CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error updating central repository, unable to make connection", ex);
            onUpgradeError("Error updating central repository, unable to make connection",
                    Bundle.EamDbUtil_centralRepoConnectionFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
        }

        // will never be reached
        return null;
    }

    /**
     * This method obtains a central repository lock.
     *
     * @param db The database connection.
     *
     * @return The lock if acquired.
     *
     * @throws CentralRepoException
     */
    private static CoordinationService.Lock obtainCentralRepoLock(CentralRepository db) throws CentralRepoException {
        try {
            // This may return null if locking isn't supported, which is fine. It will
            // throw an exception if locking is supported but we can't get the lock
            // (meaning the database is in use by another user)
            return db.getExclusiveMultiUserDbLock();
            //perform upgrade
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error updating central repository, unable to acquire exclusive lock", ex);
            onUpgradeError("Error updating central repository, unable to acquire exclusive lock",
                    Bundle.EamDbUtil_exclusiveLockAquisitionFailure_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
        }

        // will never be reached
        return null;
    }

    /**
     * This method updates the central repository schema if necessary.
     *
     * @param db   The database connectivity object.
     * @param lock The acquired lock.
     *
     * @throws CentralRepoException
     */
    private static void updatedDbSchema(CentralRepository db, CoordinationService.Lock lock) throws CentralRepoException {
        try {
            db.upgradeSchema();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error updating central repository", ex);
            onUpgradeError("Error updating central repository", ex.getUserMessage() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error updating central repository", ex);
            onUpgradeError("Error updating central repository",
                    Bundle.EamDbUtil_centralRepoUpgradeFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
        } catch (IncompatibleCentralRepoException ex) {
            logger.log(Level.SEVERE, "Error updating central repository", ex);
            onUpgradeError("Error updating central repository",
                    ex.getMessage() + "\n\n" + Bundle.EamDbUtil_centralRepoUpgradeFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), ex);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (CoordinationService.CoordinationServiceException ex) {
                    logger.log(Level.SEVERE, "Error releasing database lock", ex);
                }
            }
        }
    }

    /**
     * This method upgrades the current Central Reposity schema to the newest
     * version. If the upgrade fails, the Central Repository will be disabled
     * and the current settings will be cleared.
     */
    @NbBundle.Messages(value = {"EamDbUtil.centralRepoDisabled.message= The Central Repository has been disabled.", "EamDbUtil.centralRepoUpgradeFailed.message=Failed to upgrade Central Repository.", "EamDbUtil.centralRepoConnectionFailed.message=Unable to connect to Central Repository.", "EamDbUtil.exclusiveLockAquisitionFailure.message=Unable to acquire exclusive lock for Central Repository."})
    public static void upgradeDatabase() throws CentralRepoException {
        if (!CentralRepository.isEnabled()) {
            return;
        }

        CentralRepository db = obtainCentralRepository();

        //get lock necessary for upgrade
        if (db != null) {
            CoordinationService.Lock lock = obtainCentralRepoLock(db);
            updatedDbSchema(db, lock);
        } else {
            onUpgradeError("Unable to connect to database",
                    Bundle.EamDbUtil_centralRepoConnectionFailed_message() + Bundle.EamDbUtil_centralRepoDisabled_message(), null);
        }
    }

    private static void onUpgradeError(String message, String desc, Exception innerException) throws CentralRepoException {
        // Disable the central repo and clear the current settings.
        try {
            if (null != CentralRepository.getInstance()) {
                CentralRepository.getInstance().shutdownConnections();
            }
        } catch (CentralRepoException ex2) {
            logger.log(Level.SEVERE, "Error shutting down central repo connection pool", ex2);
        }
        saveDbChoice(CentralRepoDbChoice.DISABLED, false);
        if (innerException == null) {
            throw new CentralRepoException(message, desc);
        } else {
            throw new CentralRepoException(message, desc, innerException);
        }
    }

    private DatabaseTestResult testingStatus;
    private CentralRepoDbChoice selectedDbChoice;

    private final PostgresCentralRepoSettings dbSettingsPostgres;
    private final PostgresCentralRepoSettings dbSettingsMultiUser;
    private final SqliteCentralRepoSettings dbSettingsSqlite;

    private boolean configurationChanged = false;

    public CentralRepoDbManager() {
        selectedDbChoice = getSavedDbChoice();
        dbSettingsPostgres = new PostgresCentralRepoSettings(PostgresSettingsLoader.CUSTOM_SETTINGS_LOADER);
        dbSettingsMultiUser = new PostgresCentralRepoSettings(PostgresSettingsLoader.MULTIUSER_SETTINGS_LOADER);
        dbSettingsSqlite = new SqliteCentralRepoSettings();
    }

    /**
     * This method retrieves the current multi-user database settings.
     *
     * @return The current multi-user database settings.
     */
    public PostgresCentralRepoSettings getDbSettingsMultiUser() {
        return dbSettingsMultiUser;
    }

    /**
     * This method retrieves the current custom postgres database settings.
     *
     * @return The current custom postgres database settings.
     */
    public PostgresCentralRepoSettings getDbSettingsPostgres() {
        return dbSettingsPostgres;
    }

    /**
     * This method returns the current SQLite database settings for central
     * repository.
     *
     * @return The current SQLite database settings
     */
    public SqliteCentralRepoSettings getDbSettingsSqlite() {
        return dbSettingsSqlite;
    }

    /**
     * This method sets up the sqlite database with default settings.
     *
     * @throws CentralRepoException if unable to successfully set up database.
     */
    public void setupDefaultSqliteDb() throws CentralRepoException {
        // change in-memory settings to default sqlite
        selectedDbChoice = CentralRepoDbChoice.SQLITE;
        dbSettingsSqlite.setupDefaultSettings();

        // if db is not present, attempt to create it
        DatabaseTestResult curStatus = testStatus();
        if (curStatus == DatabaseTestResult.DB_DOES_NOT_EXIST) {
            createDb();
            curStatus = testStatus();
        }

        // the only successful setup status is tested ok
        if (curStatus != DatabaseTestResult.TESTED_OK) {
            throw new CentralRepoException("Unable to successfully create sqlite database");
        }

        // if successfully got here, then save the settings
        CentralRepoDbUtil.setUseCentralRepo(true);
        saveNewCentralRepo();
    }
    
    /**
     * Set up a PostgresDb using the settings for the given database choice
     * enum.
     * 
     * @param choice Type of postgres DB to set up
     * @throws CentralRepoException 
     */
    public void setupPostgresDb(CentralRepoDbChoice choice) throws CentralRepoException {        
        selectedDbChoice = choice;
        DatabaseTestResult curStatus = testStatus();
        if (curStatus == DatabaseTestResult.DB_DOES_NOT_EXIST) {
            createDb();
            curStatus = testStatus();
        }

        // the only successful setup status is tested ok
        if (curStatus != DatabaseTestResult.TESTED_OK) {
            throw new CentralRepoException("Unable to successfully create postgres database. Test failed with: " + curStatus);
        }

        // if successfully got here, then save the settings
        CentralRepoDbUtil.setUseCentralRepo(true);
        saveNewCentralRepo();
    }

    /**
     * This method returns if changes to the central repository configuration
     * were successfully applied.
     *
     * @return Returns true if the database configuration was successfully
     *         changed false if it was not.
     */
    public boolean wasConfigurationChanged() {
        return configurationChanged;
    }

    private CentralRepoDbConnectivityManager getSelectedSettings() throws CentralRepoException {
        if (selectedDbChoice == CentralRepoDbChoice.POSTGRESQL_MULTIUSER) {
            return dbSettingsMultiUser;
        }
        if (selectedDbChoice == CentralRepoDbChoice.POSTGRESQL_CUSTOM) {
            return dbSettingsPostgres;
        }
        if (selectedDbChoice == CentralRepoDbChoice.SQLITE) {
            return dbSettingsSqlite;
        }
        if (selectedDbChoice == CentralRepoDbChoice.DISABLED) {
            return null;
        }

        throw new CentralRepoException("Unknown database type: " + selectedDbChoice);
    }

    private RdbmsCentralRepoFactory getDbFactory() throws CentralRepoException {
        if (selectedDbChoice == CentralRepoDbChoice.POSTGRESQL_MULTIUSER) {
            return new RdbmsCentralRepoFactory(CentralRepoPlatforms.POSTGRESQL, dbSettingsMultiUser);
        }
        if (selectedDbChoice == CentralRepoDbChoice.POSTGRESQL_CUSTOM) {
            return new RdbmsCentralRepoFactory(CentralRepoPlatforms.POSTGRESQL, dbSettingsPostgres);
        }
        if (selectedDbChoice == CentralRepoDbChoice.SQLITE) {
            return new RdbmsCentralRepoFactory(CentralRepoPlatforms.SQLITE, dbSettingsSqlite);
        }
        if (selectedDbChoice == CentralRepoDbChoice.DISABLED) {
            return null;
        }

        throw new CentralRepoException("Unknown database type: " + selectedDbChoice);
    }

    /**
     * This method creates a central repo database if it does not already exist.
     *
     * @return True if successful; false if unsuccessful.
     *
     * @throws CentralRepoException
     */
    public boolean createDb() throws CentralRepoException {
        CentralRepoDbConnectivityManager selectedDbSettings = getSelectedSettings();
        if (selectedDbSettings == null) {
            throw new CentralRepoException("Unable to derive connectivity manager from settings: " + selectedDbChoice);
        }

        boolean result = false;
        boolean dbCreated = true;

        if (!selectedDbSettings.verifyDatabaseExists()) {
            dbCreated = selectedDbSettings.createDatabase();
        }
        if (dbCreated) {
            try {
                RdbmsCentralRepoFactory centralRepoSchemaFactory = getDbFactory();

                result = centralRepoSchemaFactory.initializeDatabaseSchema()
                        && centralRepoSchemaFactory.insertDefaultDatabaseContent();
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Unable to create database for central repository with settings " + selectedDbSettings, ex);
                throw ex;
            }
        }
        if (!result) {
            // Remove the incomplete database
            if (dbCreated) {
                selectedDbSettings.deleteDatabase();
            }

            String schemaError = "Unable to initialize database schema or insert contents into central repository.";
            logger.severe(schemaError);
            throw new CentralRepoException(schemaError);
        }

        testingStatus = DatabaseTestResult.TESTED_OK;
        return true;
    }

    /**
     * This method saves a new central repository based on current settings.
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
        CentralRepoDbUtil.setUseCentralRepo(selectedDbChoice != CentralRepoDbChoice.DISABLED);
        saveDbChoice(selectedDbChoice);

        CentralRepoDbConnectivityManager selectedDbSettings = getSelectedSettings();

        // save the new settings
        selectedDbSettings.saveSettings();
        // Load those newly saved settings into the postgres db manager instance
        //  in case we are still using the same instance.
        if (selectedDbChoice != null && selectedDbChoice != CentralRepoDbChoice.DISABLED) {
            try {
                logger.info("Saving central repo settings for db: " + selectedDbSettings);
                CentralRepository.getInstance().updateSettings();
                configurationChanged = true;
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, Bundle.CentralRepoDbManager_connectionErrorMsg_text(), ex); //NON-NLS
                return;
            }
        }
    }

    /**
     * This method retrieves the current status. Note: this could be a dirty
     * value if testing of the connection has not been performed.
     *
     * @return The current status of the database connection.
     */
    public DatabaseTestResult getStatus() {
        return testingStatus;
    }

    /**
     * This method retrieves the currently selected database choice. NOTE: This
     * choice may not align with the saved setting.
     *
     * @return The currently selected database choice.
     */
    public CentralRepoDbChoice getSelectedDbChoice() {
        return selectedDbChoice;
    }

    /**
     * This method clears the current database testing status.
     */
    public void clearStatus() {
        testingStatus = DatabaseTestResult.UNTESTED;
    }

    /**
     * This method sets the currently selected database choice and sets the
     * testing status to untested.
     *
     * @param newSelected The new database choice.
     */
    public void setSelctedDbChoice(CentralRepoDbChoice newSelected) {
        selectedDbChoice = newSelected;
        testingStatus = DatabaseTestResult.UNTESTED;
    }

    /**
     * This method tests whether or not the settings have been filled in for the
     * UI. NOTE: This does not check the connectivity status of these settings.
     *
     * @return True if database settings are valid.
     */
    public boolean testDatabaseSettingsAreValid(
            String tbDbHostname, String tbDbPort, String tbDbUsername, String tfDatabasePath, String jpDbPassword) throws CentralRepoException, NumberFormatException {

        if (selectedDbChoice == CentralRepoDbChoice.POSTGRESQL_CUSTOM) {
            dbSettingsPostgres.setHost(tbDbHostname);
            dbSettingsPostgres.setPort(Integer.parseInt(tbDbPort));
            dbSettingsPostgres.setDbName(CENTRAL_REPO_DB_NAME);
            dbSettingsPostgres.setUserName(tbDbUsername);
            dbSettingsPostgres.setPassword(jpDbPassword);
        } else if (selectedDbChoice == CentralRepoDbChoice.SQLITE) {
            File databasePath = new File(tfDatabasePath);
            dbSettingsSqlite.setDbName(SqliteCentralRepoSettings.DEFAULT_DBNAME);
            dbSettingsSqlite.setDbDirectory(databasePath.getPath());
        } else if (selectedDbChoice != CentralRepoDbChoice.POSTGRESQL_MULTIUSER) {
            throw new IllegalStateException("Central Repo has an unknown selected platform: " + selectedDbChoice);
        }

        return true;
    }

    /**
     * This method tests the current database settings to see if a valid
     * connection can be made.
     *
     * @return The result of testing the connection.
     */
    public DatabaseTestResult testStatus() {
        try {
            CentralRepoDbConnectivityManager manager = getSelectedSettings();
            if (manager != null) {
                testingStatus = manager.testStatus();
            }
        } catch (CentralRepoException e) {
            logger.log(Level.WARNING, "unable to test status of db connection in central repo", e);
        }

        return testingStatus;
    }
}
