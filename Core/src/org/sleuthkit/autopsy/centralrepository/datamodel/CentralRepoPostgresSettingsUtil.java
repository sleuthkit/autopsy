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
 * handles saving and loading of postgres settings for central repo
 */
public class CentralRepoPostgresSettingsUtil {
    private final static Logger LOGGER = Logger.getLogger(CentralRepoPostgresSettingsUtil.class.getName());
    
    private final static String DEFAULT_HOST = ""; // NON-NLS
    private final static int DEFAULT_PORT = 5432;
    private final static String DEFAULT_DBNAME = "central_repository"; // NON-NLS
    private final static String DEFAULT_USERNAME = "";
    private final static String DEFAULT_PASSWORD = "";

    private static final String PASSWORD_KEY = "db.postgresql.password";
    private static final String BULK_THRESHOLD_KEY = "db.postgresql.bulkThreshold";
    private static final String PORT_KEY = "db.postgresql.port";
    private static final String USER_KEY = "db.postgresql.user";
    private static final String DBNAME_KEY = "db.postgresql.dbName";
    private static final String HOST_KEY = "db.postgresql.host";

    private static final String MODULE_KEY = "CentralRepository";

    
    
    private static String valOrDefault(String val, String defaultVal) {
        if (val == null || val.isEmpty())
            return defaultVal;
        
        return val;
    }
    
    private static int valOrDefault(String val, int defaultVal, Integer min, Integer max) {
        try {
            if (val == null || val.isEmpty()) {
                return defaultVal;
            } else {
                int retVal = Integer.parseInt(val);
                if ((min != null && retVal < min) || (max != null && retVal > max)) {
                    return defaultVal;
                }
                else {
                    return retVal;
                }
            }
        } catch (NumberFormatException ex) {
            return defaultVal;
        }
    }
    
    private static void handleTry(TryHandler handler) {
        try {
            handler.operation();
        }
        catch (CentralRepoException e) {
            LOGGER.log(Level.WARNING, "There was an error in converting central repo postgres settings", e);
        }
    }
    
    /**
     * an action that potentially throws an exception
     */
    private interface TryHandler {
        void operation() throws CentralRepoException;
    }
    
    
    public static PostgresConnectionSettings loadMultiUserSettings() {
        PostgresConnectionSettings settings = new PostgresConnectionSettings();
        
        CaseDbConnectionInfo muConn;
        try {
            muConn = UserPreferences.getDatabaseConnectionInfo();
        } catch (UserPreferencesException ex) {
            LOGGER.log(Level.SEVERE, "Failed to import settings from multi-user settings.", ex);
            return settings;
        }
        
        handleTry(() -> settings.setHost(valOrDefault(muConn.getHost(), DEFAULT_HOST)));
        handleTry(() -> settings.setDbName(DEFAULT_DBNAME));
        handleTry(() -> settings.setUserName(valOrDefault(muConn.getUserName(), DEFAULT_USERNAME)));
        
        handleTry(() -> settings.setPort(valOrDefault(muConn.getPort(), DEFAULT_PORT, 1, 65535)));
        handleTry(() -> settings.setBulkThreshold(RdbmsCentralRepo.DEFAULT_BULK_THRESHHOLD));
        
        handleTry(() -> settings.setPassword(valOrDefault(muConn.getPassword(), DEFAULT_PASSWORD)));
        
        return settings;
    }
    
    
    public static PostgresConnectionSettings loadCustomSettings() {
        PostgresConnectionSettings settings = new PostgresConnectionSettings();
        Map<String, String> keyVals = ModuleSettings.getConfigSettings(MODULE_KEY);
        
        
        handleTry(() -> settings.setHost(valOrDefault(keyVals.get(HOST_KEY), DEFAULT_HOST)));
        handleTry(() -> settings.setDbName(valOrDefault(keyVals.get(DBNAME_KEY), DEFAULT_DBNAME)));
        handleTry(() -> settings.setUserName(valOrDefault(keyVals.get(USER_KEY), DEFAULT_USERNAME)));
        
        handleTry(() -> settings.setPort(valOrDefault(keyVals.get(PORT_KEY), DEFAULT_PORT, 1, 65535)));
        handleTry(() -> settings.setBulkThreshold(valOrDefault(keyVals.get(BULK_THRESHOLD_KEY), RdbmsCentralRepo.DEFAULT_BULK_THRESHHOLD, 1, null)));
        
        String passwordHex = keyVals.get(PASSWORD_KEY);
        String password;
        try {
            password = TextConverter.convertHexTextToText(passwordHex);
        } catch (TextConverterException ex) {
            LOGGER.log(Level.WARNING, "Failed to convert password from hex text to text.", ex);
            password = DEFAULT_PASSWORD;
        }
        
        final String finalPassword = password;
        
        handleTry(() -> settings.setPassword(finalPassword));
        return settings;
    }

    public static void saveCustomSettings(PostgresConnectionSettings settings) {
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
     * checks if saved settings differ from the in-memory object provided in the 'settings' parameter
     * @param settings  the in-memory object
     * @return  whether or not settings parameter differs from saved custom settings
     */
    public static boolean areCustomSettingsChanged(PostgresConnectionSettings settings) {
        PostgresConnectionSettings saved = loadCustomSettings();
        return saved.equals(settings);
    }
}
