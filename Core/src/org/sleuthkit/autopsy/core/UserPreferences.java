/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.core;

import java.nio.file.Paths;
import org.sleuthkit.autopsy.coreutils.TextConverter;
import java.util.prefs.BackingStoreException;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;
import org.python.icu.util.TimeZone;
import org.sleuthkit.autopsy.machinesettings.UserMachinePreferences;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.TextConverterException;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.TskData.DbType;

/**
 * Provides convenient access to a Preferences node for user preferences with
 * default values.
 */
public final class UserPreferences {

    private static final Preferences preferences = NbPreferences.forModule(UserPreferences.class);
    public static final String KEEP_PREFERRED_VIEWER = "KeepPreferredViewer"; // NON-NLS    
    public static final String HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE = "HideKnownFilesInDataSourcesTree"; //NON-NLS 
    public static final String HIDE_KNOWN_FILES_IN_VIEWS_TREE = "HideKnownFilesInViewsTree"; //NON-NLS 
    public static final String HIDE_SLACK_FILES_IN_DATA_SRCS_TREE = "HideSlackFilesInDataSourcesTree"; //NON-NLS 
    public static final String HIDE_SLACK_FILES_IN_VIEWS_TREE = "HideSlackFilesInViewsTree"; //NON-NLS 
    public static final String DISPLAY_TIMES_IN_LOCAL_TIME = "DisplayTimesInLocalTime"; //NON-NLS
    public static final String TIME_ZONE_FOR_DISPLAYS = "TimeZoneForDisplays"; //NON-NLS
    public static final String NUMBER_OF_FILE_INGEST_THREADS = "NumberOfFileIngestThreads"; //NON-NLS
    public static final String IS_MULTI_USER_MODE_ENABLED = "IsMultiUserModeEnabled"; //NON-NLS
    public static final String EXTERNAL_DATABASE_HOSTNAME_OR_IP = "ExternalDatabaseHostnameOrIp"; //NON-NLS
    public static final String EXTERNAL_DATABASE_PORTNUMBER = "ExternalDatabasePortNumber"; //NON-NLS
    public static final String EXTERNAL_DATABASE_NAME = "ExternalDatabaseName"; //NON-NLS
    public static final String EXTERNAL_DATABASE_USER = "ExternalDatabaseUsername"; //NON-NLS
    public static final String EXTERNAL_DATABASE_PASSWORD = "ExternalDatabasePassword"; //NON-NLS
    public static final String EXTERNAL_DATABASE_TYPE = "ExternalDatabaseType"; //NON-NLS
    private static final String SOLR8_SERVER_HOST = "Solr8ServerHost"; //NON-NLS
    private static final String SOLR8_SERVER_PORT = "Solr8ServerPort"; //NON-NLS
    private static final String SOLR4_SERVER_HOST = "IndexingServerHost"; //NON-NLS
    private static final String SOLR4_SERVER_PORT = "IndexingServerPort"; //NON-NLS
    private static final String ZK_SERVER_HOST = "ZookeeperServerHost"; //NON-NLS
    private static final String ZK_SERVER_PORT = "ZookeeperServerPort"; //NON-NLS
    private static final String MESSAGE_SERVICE_PASSWORD = "MessageServicePassword"; //NON-NLS
    private static final String MESSAGE_SERVICE_USER = "MessageServiceUser"; //NON-NLS
    private static final String MESSAGE_SERVICE_HOST = "MessageServiceHost"; //NON-NLS
    private static final String MESSAGE_SERVICE_PORT = "MessageServicePort"; //NON-NLS
    public static final String TEXT_TRANSLATOR_NAME = "TextTranslatorName";
    public static final String OCR_TRANSLATION_ENABLED = "OcrTranslationEnabled";
    public static final String PROCESS_TIME_OUT_ENABLED = "ProcessTimeOutEnabled"; //NON-NLS
    public static final String PROCESS_TIME_OUT_HOURS = "ProcessTimeOutHours"; //NON-NLS
    private static final int DEFAULT_PROCESS_TIMEOUT_HR = 60;
    private static final String DEFAULT_PORT_STRING = "61616";
    private static final int DEFAULT_PORT_INT = 61616;
    private static final String APP_NAME = "AppName";
    public static final String SETTINGS_PROPERTIES = "AutoIngest";
    private static final String MODE = "AutopsyMode"; // NON-NLS
    private static final String MAX_NUM_OF_LOG_FILE = "MaximumNumberOfLogFiles";
    private static final int LOG_FILE_NUM_INT = 10;
    public static final String GROUP_ITEMS_IN_TREE_BY_DATASOURCE = "GroupItemsInTreeByDataSource"; //NON-NLS
    public static final String SHOW_ONLY_CURRENT_USER_TAGS = "ShowOnlyCurrentUserTags";
    public static final String HIDE_SCO_COLUMNS = "HideCentralRepoCommentsAndOccurrences"; //The key for this setting pre-dates the settings current functionality //NON-NLS
    public static final String DISPLAY_TRANSLATED_NAMES = "DisplayTranslatedNames";
    private static final boolean DISPLAY_TRANSLATED_NAMES_DEFAULT = true;
    public static final String EXTERNAL_HEX_EDITOR_PATH = "ExternalHexEditorPath";
    public static final String SOLR_MAX_JVM_SIZE = "SolrMaxJVMSize";
    public static final String RESULTS_TABLE_PAGE_SIZE = "ResultsTablePageSize";
    private static final String GEO_TILE_OPTION = "GeolocationTileOption";
    private static final String GEO_OSM_TILE_ZIP_PATH = "GeolocationOsmZipPath";
    private static final String GEO_OSM_SERVER_ADDRESS = "GeolocationOsmServerAddress";
    private static final String GEO_MBTILES_FILE_PATH = "GeolcoationMBTilesFilePath";
    private static final String HEALTH_MONITOR_REPORT_PATH = "HealthMonitorReportPath";
    
    // Prevent instantiation.
    private UserPreferences() {
    }

    public enum SelectedMode {

        STANDALONE,
        AUTOINGEST
    };

    /**
     * Get mode from persistent storage.
     *
     * @return SelectedMode Selected mode.
     */
    public static SelectedMode getMode() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, MODE)) {
            int ordinal = Integer.parseInt(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, MODE));
            return UserPreferences.SelectedMode.values()[ordinal];
        }
        return UserPreferences.SelectedMode.STANDALONE;
    }

    /**
     * Set mode to persistent storage.
     *
     * @param mode Selected mode.
     */
    public static void setMode(SelectedMode mode) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, MODE, Integer.toString(mode.ordinal()));
    }

    /**
     * Reload all preferences from disk. This is only needed if the preferences
     * file is being directly modified on disk while Autopsy is running.
     *
     * @throws BackingStoreException
     */
    public static void reloadFromStorage() throws BackingStoreException {
        preferences.sync();
    }

    /**
     * Saves the current preferences to storage. This is only needed if the
     * preferences files are going to be copied to another location while
     * Autopsy is running.
     *
     * @throws BackingStoreException
     */
    public static void saveToStorage() throws BackingStoreException {
        preferences.flush();
    }

    public static void addChangeListener(PreferenceChangeListener listener) {
        preferences.addPreferenceChangeListener(listener);
    }

    public static void removeChangeListener(PreferenceChangeListener listener) {
        preferences.removePreferenceChangeListener(listener);
    }

    public static boolean keepPreferredContentViewer() {
        return preferences.getBoolean(KEEP_PREFERRED_VIEWER, false);
    }

    public static void setKeepPreferredContentViewer(boolean value) {
        preferences.putBoolean(KEEP_PREFERRED_VIEWER, value);
    }

    public static boolean hideKnownFilesInDataSourcesTree() {
        return preferences.getBoolean(HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE, false);
    }

    public static void setHideKnownFilesInDataSourcesTree(boolean value) {
        preferences.putBoolean(HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE, value);
    }

    public static boolean hideKnownFilesInViewsTree() {
        return preferences.getBoolean(HIDE_KNOWN_FILES_IN_VIEWS_TREE, true);
    }

    public static void setHideKnownFilesInViewsTree(boolean value) {
        preferences.putBoolean(HIDE_KNOWN_FILES_IN_VIEWS_TREE, value);
    }

    public static boolean hideSlackFilesInDataSourcesTree() {
        return preferences.getBoolean(HIDE_SLACK_FILES_IN_DATA_SRCS_TREE, true);
    }

    public static void setHideSlackFilesInDataSourcesTree(boolean value) {
        preferences.putBoolean(HIDE_SLACK_FILES_IN_DATA_SRCS_TREE, value);
    }

    public static boolean hideSlackFilesInViewsTree() {
        return preferences.getBoolean(HIDE_SLACK_FILES_IN_VIEWS_TREE, true);
    }

    public static void setHideSlackFilesInViewsTree(boolean value) {
        preferences.putBoolean(HIDE_SLACK_FILES_IN_VIEWS_TREE, value);
    }

    public static boolean displayTimesInLocalTime() {
        return preferences.getBoolean(DISPLAY_TIMES_IN_LOCAL_TIME, true);
    }

    public static void setDisplayTimesInLocalTime(boolean value) {
        preferences.putBoolean(DISPLAY_TIMES_IN_LOCAL_TIME, value);
    }

    public static String getTimeZoneForDisplays() {
        return preferences.get(TIME_ZONE_FOR_DISPLAYS, TimeZone.GMT_ZONE.getID());
    }

    public static void setTimeZoneForDisplays(String timeZone) {
        preferences.put(TIME_ZONE_FOR_DISPLAYS, timeZone);
    }

    public static int numberOfFileIngestThreads() {
        return preferences.getInt(NUMBER_OF_FILE_INGEST_THREADS, 2);
    }

    public static void setNumberOfFileIngestThreads(int value) {
        preferences.putInt(NUMBER_OF_FILE_INGEST_THREADS, value);
    }

    @Deprecated
    public static boolean groupItemsInTreeByDatasource() {
        return preferences.getBoolean(GROUP_ITEMS_IN_TREE_BY_DATASOURCE, false);
    }

    @Deprecated
    public static void setGroupItemsInTreeByDatasource(boolean value) {
        preferences.putBoolean(GROUP_ITEMS_IN_TREE_BY_DATASOURCE, value);
    }

    /**
     * Get the user preference which identifies whether tags should be shown for
     * only the current user or all users.
     *
     * @return true for just the current user, false for all users
     */
    public static boolean showOnlyCurrentUserTags() {
        return preferences.getBoolean(SHOW_ONLY_CURRENT_USER_TAGS, false);
    }

    /**
     * Set the user preference which identifies whether tags should be shown for
     * only the current user or all users.
     *
     * @param value - true for just the current user, false for all users
     */
    public static void setShowOnlyCurrentUserTags(boolean value) {
        preferences.putBoolean(SHOW_ONLY_CURRENT_USER_TAGS, value);
    }

    /**
     * Get the user preference which identifies whether the (S)core, (C)omments,
     * and (O)ccurrences columns should be populated and displayed in the result
     * view.
     *
     * @return True if hiding SCO columns; otherwise false.
     */
    public static boolean getHideSCOColumns() {
        return preferences.getBoolean(HIDE_SCO_COLUMNS, false);
    }

    /**
     * Set the user preference which identifies whether the (S)core, (C)omments,
     * and (O)ccurrences columns should be populated and displayed in the result
     * view.
     *
     * @param value The value of which to assign to the user preference.
     */
    public static void setHideSCOColumns(boolean value) {
        preferences.putBoolean(HIDE_SCO_COLUMNS, value);
    }

    public static void setDisplayTranslatedFileNames(boolean value) {
        preferences.putBoolean(DISPLAY_TRANSLATED_NAMES, value);
    }

    public static boolean displayTranslatedFileNames() {
        return preferences.getBoolean(DISPLAY_TRANSLATED_NAMES, DISPLAY_TRANSLATED_NAMES_DEFAULT);
    }

    /**
     * Reads persisted case database connection info.
     *
     * @return An object encapsulating the database connection info.
     *
     * @throws org.sleuthkit.autopsy.core.UserPreferencesException
     */
    public static CaseDbConnectionInfo getDatabaseConnectionInfo() throws UserPreferencesException {
        DbType dbType;
        try {
            dbType = DbType.valueOf(preferences.get(EXTERNAL_DATABASE_TYPE, "POSTGRESQL")); //NON-NLS
        } catch (Exception ex) {
            dbType = DbType.SQLITE;
        }
        try {
            return new CaseDbConnectionInfo(
                    preferences.get(EXTERNAL_DATABASE_HOSTNAME_OR_IP, ""),
                    preferences.get(EXTERNAL_DATABASE_PORTNUMBER, "5432"),
                    preferences.get(EXTERNAL_DATABASE_USER, ""),
                    TextConverter.convertHexTextToText(preferences.get(EXTERNAL_DATABASE_PASSWORD, "")),
                    dbType);
        } catch (TextConverterException ex) {
            throw new UserPreferencesException("Failure converting password hex text to text.", ex); // NON-NLS
        }
    }

    /**
     * Persists case database connection info.
     *
     * @param connectionInfo An object encapsulating the database connection
     *                       info.
     *
     * @throws org.sleuthkit.autopsy.core.UserPreferencesException
     */
    public static void setDatabaseConnectionInfo(CaseDbConnectionInfo connectionInfo) throws UserPreferencesException {
        preferences.put(EXTERNAL_DATABASE_HOSTNAME_OR_IP, connectionInfo.getHost());
        preferences.put(EXTERNAL_DATABASE_PORTNUMBER, connectionInfo.getPort());
        preferences.put(EXTERNAL_DATABASE_USER, connectionInfo.getUserName());
        try {
            preferences.put(EXTERNAL_DATABASE_PASSWORD, TextConverter.convertTextToHexText(connectionInfo.getPassword()));
        } catch (TextConverterException ex) {
            throw new UserPreferencesException("Failure converting text to password hext text", ex); // NON-NLS
        }
        preferences.put(EXTERNAL_DATABASE_TYPE, connectionInfo.getDbType().toString());
    }

    public static void setIsMultiUserModeEnabled(boolean enabled) {
        preferences.putBoolean(IS_MULTI_USER_MODE_ENABLED, enabled);
    }

    public static boolean getIsMultiUserModeEnabled() {
        return preferences.getBoolean(IS_MULTI_USER_MODE_ENABLED, false);
    }

    public static String getIndexingServerHost() {
        return preferences.get(SOLR8_SERVER_HOST, "");
    }

    public static void setIndexingServerHost(String hostName) {
        preferences.put(SOLR8_SERVER_HOST, hostName);
    }

    public static String getIndexingServerPort() {
        return preferences.get(SOLR8_SERVER_PORT, "8983");
    }

    public static void setIndexingServerPort(int port) {
        preferences.putInt(SOLR8_SERVER_PORT, port);
    }
    
    public static String getSolr4ServerHost() {
        return preferences.get(SOLR4_SERVER_HOST, "");
    }

    public static void setSolr4ServerHost(String hostName) {
        preferences.put(SOLR4_SERVER_HOST, hostName);
    }    
    
    public static String getSolr4ServerPort() {
        return preferences.get(SOLR4_SERVER_PORT, "");
    }

    public static void setSolr4ServerPort(String port) {
        preferences.put(SOLR4_SERVER_PORT, port);
    }    
    
    public static String getZkServerHost() {
        return preferences.get(ZK_SERVER_HOST, "");
    }
    
    public static void setZkServerHost(String hostName) {
        preferences.put(ZK_SERVER_HOST, hostName);
    }

    public static String getZkServerPort() {
        return preferences.get(ZK_SERVER_PORT, "9983");
    }

    public static void setZkServerPort(String port) {
        preferences.put(ZK_SERVER_PORT, port);
    }
    
    public static void setTextTranslatorName(String textTranslatorName) {
        preferences.put(TEXT_TRANSLATOR_NAME, textTranslatorName);
    }

    public static String getTextTranslatorName() {
        return preferences.get(TEXT_TRANSLATOR_NAME, null);
    }
    
    public static void setUseOcrInTranslation(boolean enableOcr) {
        preferences.putBoolean(OCR_TRANSLATION_ENABLED, enableOcr);
    }

    public static boolean getUseOcrInTranslation() {
        return preferences.getBoolean(OCR_TRANSLATION_ENABLED, true);
    }    

    /**
     * Persists message service connection info.
     *
     * @param info An object encapsulating the message service info.
     *
     * @throws org.sleuthkit.autopsy.core.UserPreferencesException
     */
    public static void setMessageServiceConnectionInfo(MessageServiceConnectionInfo info) throws UserPreferencesException {
        preferences.put(MESSAGE_SERVICE_HOST, info.getHost());
        preferences.put(MESSAGE_SERVICE_PORT, Integer.toString(info.getPort()));
        preferences.put(MESSAGE_SERVICE_USER, info.getUserName());
        try {
            preferences.put(MESSAGE_SERVICE_PASSWORD, TextConverter.convertTextToHexText(info.getPassword()));
        } catch (TextConverterException ex) {
            throw new UserPreferencesException("Failed to convert password text to hex text.", ex);
        }
    }

    /**
     * Reads persisted message service connection info.
     *
     * @return An object encapsulating the message service info.
     *
     * @throws org.sleuthkit.autopsy.core.UserPreferencesException
     */
    public static MessageServiceConnectionInfo getMessageServiceConnectionInfo() throws UserPreferencesException {
        int port;
        try {
            port = Integer.parseInt(preferences.get(MESSAGE_SERVICE_PORT, DEFAULT_PORT_STRING));
        } catch (NumberFormatException ex) {
            // if there is an error parsing the port number, use the default port number
            port = DEFAULT_PORT_INT;
        }

        try {
            return new MessageServiceConnectionInfo(
                    preferences.get(MESSAGE_SERVICE_HOST, ""),
                    port,
                    preferences.get(MESSAGE_SERVICE_USER, ""),
                    TextConverter.convertHexTextToText(preferences.get(MESSAGE_SERVICE_PASSWORD, "")));
        } catch (TextConverterException ex) {
            throw new UserPreferencesException("Failed to convert password hex text to text.", ex);
        }
    }

    /**
     * Reads persisted process time out value.
     *
     * @return int Process time out value (hours).
     */
    public static int getProcessTimeOutHrs() {
        int timeOut = preferences.getInt(PROCESS_TIME_OUT_HOURS, DEFAULT_PROCESS_TIMEOUT_HR);
        if (timeOut < 0) {
            timeOut = 0;
        }
        return timeOut;
    }

    /**
     * Stores persisted process time out value.
     *
     * @param value Persisted process time out value (hours).
     */
    public static void setProcessTimeOutHrs(int value) {
        if (value < 0) {
            value = 0;
        }
        preferences.putInt(PROCESS_TIME_OUT_HOURS, value);
    }

    /**
     * Reads persisted setting of whether process time out functionality is
     * enabled.
     *
     * @return boolean True if process time out is functionality enabled, false
     *         otherwise.
     */
    public static boolean getIsTimeOutEnabled() {
        boolean enabled = preferences.getBoolean(PROCESS_TIME_OUT_ENABLED, false);
        return enabled;
    }

    /**
     * Stores persisted setting of whether process time out functionality is
     * enabled.
     *
     * @param enabled Persisted setting of whether process time out
     *                functionality is enabled.
     */
    public static void setIsTimeOutEnabled(boolean enabled) {
        preferences.putBoolean(PROCESS_TIME_OUT_ENABLED, enabled);
    }

    /**
     * Get the display name for this program
     *
     * @return Name of this program
     */
    public static String getAppName() {
        return preferences.get(APP_NAME, Version.getName());
    }

    /**
     * Set the display name for this program
     *
     * @param name Display name
     */
    public static void setAppName(String name) {
        preferences.put(APP_NAME, name);
    }

    /**
     * get the maximum number of log files to save
     *
     * @return Number of log files
     */
    public static int getLogFileCount() {
        return preferences.getInt(MAX_NUM_OF_LOG_FILE, LOG_FILE_NUM_INT);
    }

    /**
     * get the default number of log files to save
     *
     * @return LOG_FILE_COUNT
     */
    public static int getDefaultLogFileCount() {
        return LOG_FILE_NUM_INT;
    }

    /**
     * Set the maximum number of log files to save
     *
     * @param count number of log files
     */
    public static void setLogFileCount(int count) {
        preferences.putInt(MAX_NUM_OF_LOG_FILE, count);
    }

    /**
     * Get the maximum JVM heap size (in MB) for the embedded Solr server.
     *
     * @return Saved value or default (512)
     */
    public static int getMaxSolrVMSize() {
        return preferences.getInt(SOLR_MAX_JVM_SIZE, 512);
    }

    /**
     * Set the maximum JVM heap size (in MB) for the embedded Solr server.
     *
     * @param maxSize
     */
    public static void setMaxSolrVMSize(int maxSize) {
        preferences.putInt(SOLR_MAX_JVM_SIZE, maxSize);
    }

    /**
     * Get the maximum number of results to display in a result table.
     *
     * @return Saved value or default (10,000).
     */
    public static int getResultsTablePageSize() {
        return preferences.getInt(RESULTS_TABLE_PAGE_SIZE, 10_000);
    }

    /**
     * Set the maximum number of results to display in a result table.
     *
     * @param pageSize
     */
    public static void setResultsTablePageSize(int pageSize) {
        preferences.putInt(RESULTS_TABLE_PAGE_SIZE, pageSize);
    }

    /**
     * Set the HdX path.
     *
     * @param executablePath User-inputted path to HxD executable
     */
    public static void setExternalHexEditorPath(String executablePath) {
        preferences.put(EXTERNAL_HEX_EDITOR_PATH, executablePath);
    }

    /**
     * Retrieves the HdXEditor path set by the User. If not found, the default
     * will be the default install location of HxD.
     *
     * @return Path to HdX
     */
    public static String getExternalHexEditorPath() {
        return preferences.get(EXTERNAL_HEX_EDITOR_PATH, Paths.get("C:", "Program Files", "HxD", "HxD.exe").toString());
    }
    
    /**
     * Set the geolocation tile server option.
     * 
     * @param option 
     */
    public static void setGeolocationTileOption(int option) {
        preferences.putInt(GEO_TILE_OPTION, option);
    }

    /**
     * Retrieves the Geolocation tile option.  If not found, the value will
     * default to 0.
     * @return 
     */
    public static int getGeolocationtTileOption() {
        return preferences.getInt(GEO_TILE_OPTION, 0);
    }

    /**
     * Sets the path to the OSM tile zip file.
     * 
     * @param absolutePath 
     */
    public static void setGeolocationOsmZipPath(String absolutePath) {
        preferences.put(GEO_OSM_TILE_ZIP_PATH, absolutePath);
    }

    /**
     * Retrieves the path for the OSM tile zip file or returns empty string if
     * none was found.
     * 
     * @return Path to zip file
     */
    public static String getGeolocationOsmZipPath() {
        return preferences.get(GEO_OSM_TILE_ZIP_PATH, "");
    }

    /**
     * Sets the address of geolocation window user defined OSM server data source.
     * 
     * @param address 
     */
    public static void setGeolocationOsmServerAddress(String address) {
        preferences.put(GEO_OSM_SERVER_ADDRESS, address);
    }

    /**
     * Retrieves the address to the OSM server or null if one was not found.
     * 
     * @return Address of OSM server
     */
    public static String getGeolocationOsmServerAddress() {
        return preferences.get(GEO_OSM_SERVER_ADDRESS, "");
    }
    
    /**
     * Sets the path for Geolocation MBTiles data source file.
     * 
     * @param absolutePath 
     */
    public static void setGeolocationMBTilesFilePath(String absolutePath) {
        preferences.put(GEO_MBTILES_FILE_PATH, absolutePath);
    }
    
    /**
     * Retrieves the path for the Geolocation MBTiles data source file.
     * 
     * @return Absolute path to  MBTiles file or empty string if none was found.
     */
    public static String getGeolocationMBTilesFilePath() {
        return preferences.get(GEO_MBTILES_FILE_PATH, "");
    }
    
    /**
     * Retrieves the root application temp directory.
     * 
     * @return The absolute path to the application temp directory.
     */
    public static String getAppTempDirectory() {
        return Paths.get(UserMachinePreferences.getBaseTempDirectory(), getAppName())
                .toAbsolutePath().toString();
    }
    
    /**
     * Set the last used health monitor report path.
     *
     * @param reportPath Last used health monitor report path.
     */
    public static void setHealthMonitorReportPath(String reportPath) {
        preferences.put(HEALTH_MONITOR_REPORT_PATH, reportPath);
    }

    /**
     * Gets the last used health monitor report path.
     *
     * @return Last used health monitor report path. Empty string if no value has been recorded.
     */
    public static String getHealthMonitorReportPath() {
        return preferences.get(HEALTH_MONITOR_REPORT_PATH, "");
    }
}