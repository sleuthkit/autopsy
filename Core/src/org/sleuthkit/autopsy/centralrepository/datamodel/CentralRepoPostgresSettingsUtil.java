/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
 *
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.TextConverter;
import org.sleuthkit.autopsy.coreutils.TextConverterException;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;

/**
 * This class handles saving and loading of postgres settings for central repository.
 */
public class CentralRepoPostgresSettingsUtil {
    private final static Logger LOGGER = Logger.getLogger(CentralRepoPostgresSettingsUtil.class.getName());

    private static final String PASSWORD_KEY = "db.postgresql.password";
    private static final String BULK_THRESHOLD_KEY = "db.postgresql.bulkThreshold";
    private static final String PORT_KEY = "db.postgresql.port";
    private static final String USER_KEY = "db.postgresql.user";
    private static final String DBNAME_KEY = "db.postgresql.dbName";
    private static final String HOST_KEY = "db.postgresql.host";

    private static final String MODULE_KEY = "CentralRepository";

    private static CentralRepoPostgresSettingsUtil instance = null;
    
    /**
     * This method retrieves a singleton instance of this class.
     * @return      The singleton instance of this class.
     */
    public static synchronized CentralRepoPostgresSettingsUtil getInstance() {
        if (instance == null)
            instance = new CentralRepoPostgresSettingsUtil();
        
        return instance;
    }
    
    private CentralRepoPostgresSettingsUtil() {}
    
    private void logException(TryHandler handler) {
        try {
            handler.operation();
        }
        catch (CentralRepoException | NumberFormatException e) {
            LOGGER.log(Level.WARNING, "There was an error in converting central repo postgres settings", e);
        }
    }
    
    /**
     * This interface represents an action that potentially throws an exception.
     */
    private interface TryHandler {
        void operation() throws CentralRepoException, NumberFormatException;
    }
    
    /**
     * This method loads multi-user settings to be used as a postgres connection to central repository.  If
     * settings could not be loaded, default values will be returned.
     * 
     * @return      The settings loaded from multi-user settings.
     */
    public PostgresConnectionSettings loadMultiUserSettings() {
        PostgresConnectionSettings settings = new PostgresConnectionSettings();
        
        CaseDbConnectionInfo muConn;
        try {
            muConn = UserPreferences.getDatabaseConnectionInfo();
        } catch (UserPreferencesException ex) {
            LOGGER.log(Level.SEVERE, "Failed to import settings from multi-user settings.", ex);
            return settings;
        }
        
        logException(() -> settings.setHost(muConn.getHost()));
        logException(() -> settings.setDbName(PostgresConnectionSettings.DEFAULT_DBNAME));
        logException(() -> settings.setUserName(muConn.getUserName()));
        
        logException(() -> settings.setPort(Integer.parseInt(muConn.getPort())));
        logException(() -> settings.setBulkThreshold(RdbmsCentralRepo.DEFAULT_BULK_THRESHHOLD));
        
        logException(() -> settings.setPassword(muConn.getPassword()));
        
        return settings;
    }
    
    
    /**
     * This method loads the custom postgres settings for central repository.  If
     * settings could not be loaded, default values will be returned.
     * 
     * @return      The settings loaded from custom postgres settings.
     */
    public PostgresConnectionSettings loadCustomSettings() {
        PostgresConnectionSettings settings = new PostgresConnectionSettings();
        Map<String, String> keyVals = ModuleSettings.getConfigSettings(MODULE_KEY);
        
        
        logException(() -> settings.setHost(keyVals.get(HOST_KEY)));
        logException(() -> settings.setDbName(keyVals.get(DBNAME_KEY)));
        logException(() -> settings.setUserName(keyVals.get(USER_KEY)));
        
        logException(() -> settings.setPort(Integer.parseInt(keyVals.get(PORT_KEY))));
        logException(() -> settings.setBulkThreshold(Integer.parseInt(keyVals.get((BULK_THRESHOLD_KEY)))));
        
        String passwordHex = keyVals.get(PASSWORD_KEY);
        String password;
        try {
            password = TextConverter.convertHexTextToText(passwordHex);
        } catch (TextConverterException ex) {
            LOGGER.log(Level.WARNING, "Failed to convert password from hex text to text.", ex);
            password = null;
        }
        
        final String finalPassword = password;
        logException(() -> settings.setPassword(finalPassword));
        return settings;
    }

    /**
     * This method saves the settings for a custom postgres central repository connection.
     * @param settings      The settings to save.
     */
    public void saveCustomSettings(PostgresConnectionSettings settings) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(HOST_KEY, settings.getHost());
        map.put(PORT_KEY, Integer.toString(settings.getPort()));
        map.put(DBNAME_KEY, settings.getDbName());
        map.put(BULK_THRESHOLD_KEY, Integer.toString(settings.getBulkThreshold()));
        map.put(USER_KEY, settings.getUserName());
        try {
            map.put(PASSWORD_KEY, TextConverter.convertTextToHexText(settings.getPassword())); // NON-NLS
        } catch (TextConverterException ex) {
            LOGGER.log(Level.SEVERE, "Failed to convert password from text to hex text.", ex);
        }
        
        ModuleSettings.setConfigSettings(MODULE_KEY, map);
    }

    /**
     * This method checks if saved settings differ from the in-memory object provided in the 'settings' parameter.
     * @param settings  The in-memory object.
     * @return  Whether or not settings parameter differs from saved custom settings.
     */
    public boolean areCustomSettingsChanged(PostgresConnectionSettings settings) {
        PostgresConnectionSettings saved = loadCustomSettings();
        return saved.equals(settings);
    }
}
