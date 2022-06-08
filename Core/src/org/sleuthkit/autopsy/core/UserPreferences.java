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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.sleuthkit.autopsy.coreutils.TextConverter;
import java.util.prefs.BackingStoreException;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import java.util.prefs.PreferenceChangeListener;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.openide.util.Lookup;
import org.python.icu.util.TimeZone;
import org.sleuthkit.autopsy.appservices.AutopsyService;
import org.sleuthkit.autopsy.machinesettings.UserMachinePreferences;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.TextConverterException;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.TskData.DbType;

/**
 * Provides convenient access to a Preferences node for user preferences with
 * default values.
 */
public final class UserPreferences {

    /**
     * Returns the path to the preferences for the identifier in the user config
     * directory.
     *
     * @param identifier The identifier.
     *
     * @return The path to the preference file.
     */
    private static String getConfigPreferencePath(String identifier) {
        return Paths.get(PlatformUtil.getUserConfigDirectory(), identifier + ".properties").toString();
    }

    /**
     * Returns the path to the preferences for the identifier in the shared
     * preference directory.
     *
     * @param identifier The identifier.
     *
     * @return The path to the preference file.
     */
    private static String getSharedPreferencePath(String identifier) {
        return Paths.get(PlatformUtil.getModuleConfigDirectory(), identifier + ".properties").toString();
    }
        
    private static final String VIEW_PREFERENCE_PATH = getSharedPreferencePath("ViewPreferences");
    private static final String MACHINE_SPECIFIC_PREFERENCE_PATH = getConfigPreferencePath("MachineSpecificPreferences");
    private static final String MODE_PREFERENCE_PATH = getConfigPreferencePath("ModePreferences");
    private static final String EXTERNAL_SERVICE_PREFERENCE_PATH = getSharedPreferencePath("ExternalServicePreferences");

    private static final ConfigProperties viewPreferences = new ConfigProperties(VIEW_PREFERENCE_PATH);
    private static final ConfigProperties machineSpecificPreferences = new ConfigProperties(MACHINE_SPECIFIC_PREFERENCE_PATH);
    private static final ConfigProperties modePreferences = new ConfigProperties(MODE_PREFERENCE_PATH);
    private static final ConfigProperties externalServicePreferences = new ConfigProperties(EXTERNAL_SERVICE_PREFERENCE_PATH);

    static {
        // perform initial load to ensure disk preferences are loaded
        try {
            // make shared directory paths if they don't exist.
            new File(PlatformUtil.getModuleConfigDirectory()).mkdirs();
            viewPreferences.load();
            machineSpecificPreferences.load();
            modePreferences.load();
            externalServicePreferences.load();
        } catch (IOException ex) {
            // can't log because logger requires UserPreferences.  
            // This shouldn't really be thrown unless their is a file access 
            // issue within the user config directory.
            ex.printStackTrace();
        }
    }

    // view preferences
    public static final String KEEP_PREFERRED_VIEWER = "KeepPreferredViewer"; // NON-NLS    
    public static final String HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE = "HideKnownFilesInDataSourcesTree"; //NON-NLS 
    public static final String HIDE_KNOWN_FILES_IN_VIEWS_TREE = "HideKnownFilesInViewsTree"; //NON-NLS 
    public static final String HIDE_SLACK_FILES_IN_DATA_SRCS_TREE = "HideSlackFilesInDataSourcesTree"; //NON-NLS 
    public static final String HIDE_SLACK_FILES_IN_VIEWS_TREE = "HideSlackFilesInViewsTree"; //NON-NLS 
    public static final String DISPLAY_TIMES_IN_LOCAL_TIME = "DisplayTimesInLocalTime"; //NON-NLS
    public static final String TIME_ZONE_FOR_DISPLAYS = "TimeZoneForDisplays"; //NON-NLS
    public static final String GROUP_ITEMS_IN_TREE_BY_DATASOURCE = "GroupItemsInTreeByDataSource"; //NON-NLS
    public static final String SHOW_ONLY_CURRENT_USER_TAGS = "ShowOnlyCurrentUserTags";
    public static final String HIDE_SCO_COLUMNS = "HideCentralRepoCommentsAndOccurrences"; //The key for this setting pre-dates the settings current functionality //NON-NLS
    public static final String DISPLAY_TRANSLATED_NAMES = "DisplayTranslatedNames";
    private static final boolean DISPLAY_TRANSLATED_NAMES_DEFAULT = true;
    public static final String EXTERNAL_HEX_EDITOR_PATH = "ExternalHexEditorPath";
    public static final String RESULTS_TABLE_PAGE_SIZE = "ResultsTablePageSize";

    // machine-specific settings
    public static final String NUMBER_OF_FILE_INGEST_THREADS = "NumberOfFileIngestThreads"; //NON-NLS
    public static final String PROCESS_TIME_OUT_ENABLED = "ProcessTimeOutEnabled"; //NON-NLS
    public static final String PROCESS_TIME_OUT_HOURS = "ProcessTimeOutHours"; //NON-NLS
    private static final int DEFAULT_PROCESS_TIMEOUT_HR = 60;
    private static final String MAX_NUM_OF_LOG_FILE = "MaximumNumberOfLogFiles";
    private static final int LOG_FILE_NUM_INT = 10;
    public static final String SOLR_MAX_JVM_SIZE = "SolrMaxJVMSize";
    private static final int DEFAULT_SOLR_HEAP_SIZE_MB_64BIT_PLATFORM = 2048;
    private static final int DEFAULT_SOLR_HEAP_SIZE_MB_32BIT_PLATFORM = 512;
    private static final String HEALTH_MONITOR_REPORT_PATH = "HealthMonitorReportPath";
    private static final String TEMP_FOLDER = "Temp";
    private static final String GEO_OSM_TILE_ZIP_PATH = "GeolocationOsmZipPath";
    private static final String GEO_MBTILES_FILE_PATH = "GeolcoationMBTilesFilePath";

    // mode and enabled
    public static final String SETTINGS_PROPERTIES = "AutoIngest";
    private static final String MODE = "AutopsyMode"; // NON-NLS
    private static final String APP_NAME = "AppName";

    // external services preferences
    public static final String IS_MULTI_USER_MODE_ENABLED = "IsMultiUserModeEnabled"; //NON-NLS
    private static final String GEO_TILE_OPTION = "GeolocationTileOption";
    public static final String OCR_TRANSLATION_ENABLED = "OcrTranslationEnabled";
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
    private static final String DEFAULT_PORT_STRING = "61616";
    private static final int DEFAULT_PORT_INT = 61616;
    private static final String GEO_OSM_SERVER_ADDRESS = "GeolocationOsmServerAddress";

    // view preference keys used for moving from legacy files to new files
    private static final List<String> VIEW_PREFERENCE_KEYS = Arrays.asList(
            KEEP_PREFERRED_VIEWER,
            HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE,
            HIDE_KNOWN_FILES_IN_VIEWS_TREE,
            HIDE_SLACK_FILES_IN_DATA_SRCS_TREE,
            HIDE_SLACK_FILES_IN_VIEWS_TREE,
            DISPLAY_TIMES_IN_LOCAL_TIME,
            TIME_ZONE_FOR_DISPLAYS,
            GROUP_ITEMS_IN_TREE_BY_DATASOURCE,
            SHOW_ONLY_CURRENT_USER_TAGS,
            HIDE_SCO_COLUMNS,
            DISPLAY_TRANSLATED_NAMES,
            EXTERNAL_HEX_EDITOR_PATH,
            RESULTS_TABLE_PAGE_SIZE
    );

    // machine preference keys used for moving from legacy files to new files
    private static final List<String> MACHINE_PREFERENCE_KEYS = Arrays.asList(
            NUMBER_OF_FILE_INGEST_THREADS,
            PROCESS_TIME_OUT_ENABLED,
            PROCESS_TIME_OUT_HOURS,
            MAX_NUM_OF_LOG_FILE,
            SOLR_MAX_JVM_SIZE,
            HEALTH_MONITOR_REPORT_PATH,
            TEMP_FOLDER,
            GEO_OSM_TILE_ZIP_PATH,
            GEO_MBTILES_FILE_PATH
    );

    // mode preference keys used for moving from legacy files to new files
    private static final List<String> MODE_PREFERENCE_KEYS = Arrays.asList(
            SETTINGS_PROPERTIES,
            MODE,
            APP_NAME
    );

    // external service preference keys used for moving from legacy files to new files
    private static final List<String> EXTERNAL_SERVICE_KEYS = Arrays.asList(
            IS_MULTI_USER_MODE_ENABLED,
            GEO_TILE_OPTION,
            OCR_TRANSLATION_ENABLED,
            EXTERNAL_DATABASE_HOSTNAME_OR_IP,
            EXTERNAL_DATABASE_PORTNUMBER,
            EXTERNAL_DATABASE_NAME,
            EXTERNAL_DATABASE_USER,
            EXTERNAL_DATABASE_PASSWORD,
            EXTERNAL_DATABASE_TYPE,
            SOLR8_SERVER_HOST,
            SOLR8_SERVER_PORT,
            SOLR4_SERVER_HOST,
            SOLR4_SERVER_PORT,
            ZK_SERVER_HOST,
            ZK_SERVER_PORT,
            MESSAGE_SERVICE_PASSWORD,
            MESSAGE_SERVICE_USER,
            MESSAGE_SERVICE_HOST,
            MESSAGE_SERVICE_PORT,
            TEXT_TRANSLATOR_NAME,
            GEO_OSM_SERVER_ADDRESS
    );

    private static final String LEGACY_CONFIG_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), "Preferences", "org", "sleuthkit", "autopsy", "core.properties").toString();

    static void updateConfig() {
        List<Triple<File, ConfigProperties, List<String>>> fileAndKeys = Arrays.asList(
                Triple.of(new File(MODE_PREFERENCE_PATH), modePreferences, MODE_PREFERENCE_KEYS),
                Triple.of(new File(MACHINE_SPECIFIC_PREFERENCE_PATH), machineSpecificPreferences, MACHINE_PREFERENCE_KEYS),
                Triple.of(new File(VIEW_PREFERENCE_PATH), viewPreferences, VIEW_PREFERENCE_KEYS),
                Triple.of(new File(EXTERNAL_SERVICE_PREFERENCE_PATH), externalServicePreferences, EXTERNAL_SERVICE_KEYS)
        );

        boolean newSettingsExist = fileAndKeys.stream().anyMatch(triple -> triple.getLeft().exists());

        File oldSettingsFile = new File(LEGACY_CONFIG_PATH);
        // copy old settings to new location.
        if (oldSettingsFile.exists() && !newSettingsExist) {
            try {
                Properties allProperties = new Properties();
                try (FileInputStream oldPropsStream = new FileInputStream(oldSettingsFile)) {
                    allProperties.load(oldPropsStream);
                }

                for (Triple<File, ConfigProperties, List<String>> fileKeys : fileAndKeys) {
                    ConfigProperties newProperties = fileKeys.getMiddle();
                    for (String key : fileKeys.getRight()) {
                        String val = allProperties.getProperty(key);
                        if (val != null) {
                            newProperties.put(key, val);
                        }
                    }

                }
            } catch (IOException ex) {
                // can't log because logger requires UserPreferences.  
                // This shouldn't really be thrown unless their is a file access 
                // issue within the user config directory.
                ex.printStackTrace();
            }
        }
    }

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
        viewPreferences.sync();
        machineSpecificPreferences.sync();
        modePreferences.sync();
        externalServicePreferences.sync();
    }

    /**
     * Saves the current preferences to storage. This is only needed if the
     * preferences files are going to be copied to another location while
     * Autopsy is running.
     *
     * @throws BackingStoreException
     */
    public static void saveToStorage() throws BackingStoreException {
        viewPreferences.flush();
        machineSpecificPreferences.flush();
        modePreferences.flush();
        externalServicePreferences.flush();
    }

    public static void addChangeListener(PreferenceChangeListener listener) {
        viewPreferences.addPreferenceChangeListener(listener);
        machineSpecificPreferences.addPreferenceChangeListener(listener);
        modePreferences.addPreferenceChangeListener(listener);
        externalServicePreferences.addPreferenceChangeListener(listener);
    }

    public static void removeChangeListener(PreferenceChangeListener listener) {
        viewPreferences.removePreferenceChangeListener(listener);
        machineSpecificPreferences.removePreferenceChangeListener(listener);
        modePreferences.removePreferenceChangeListener(listener);
        externalServicePreferences.removePreferenceChangeListener(listener);
    }

    public static boolean keepPreferredContentViewer() {
        return viewPreferences.getBoolean(KEEP_PREFERRED_VIEWER, false);
    }

    public static void setKeepPreferredContentViewer(boolean value) {
        viewPreferences.putBoolean(KEEP_PREFERRED_VIEWER, value);
    }

    public static boolean hideKnownFilesInDataSourcesTree() {
        return viewPreferences.getBoolean(HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE, false);
    }

    public static void setHideKnownFilesInDataSourcesTree(boolean value) {
        viewPreferences.putBoolean(HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE, value);
    }

    public static boolean hideKnownFilesInViewsTree() {
        return viewPreferences.getBoolean(HIDE_KNOWN_FILES_IN_VIEWS_TREE, true);
    }

    public static void setHideKnownFilesInViewsTree(boolean value) {
        viewPreferences.putBoolean(HIDE_KNOWN_FILES_IN_VIEWS_TREE, value);
    }

    public static boolean hideSlackFilesInDataSourcesTree() {
        return viewPreferences.getBoolean(HIDE_SLACK_FILES_IN_DATA_SRCS_TREE, true);
    }

    public static void setHideSlackFilesInDataSourcesTree(boolean value) {
        viewPreferences.putBoolean(HIDE_SLACK_FILES_IN_DATA_SRCS_TREE, value);
    }

    public static boolean hideSlackFilesInViewsTree() {
        return viewPreferences.getBoolean(HIDE_SLACK_FILES_IN_VIEWS_TREE, true);
    }

    public static void setHideSlackFilesInViewsTree(boolean value) {
        viewPreferences.putBoolean(HIDE_SLACK_FILES_IN_VIEWS_TREE, value);
    }

    public static boolean displayTimesInLocalTime() {
        return viewPreferences.getBoolean(DISPLAY_TIMES_IN_LOCAL_TIME, true);
    }

    public static void setDisplayTimesInLocalTime(boolean value) {
        viewPreferences.putBoolean(DISPLAY_TIMES_IN_LOCAL_TIME, value);
    }

    public static String getTimeZoneForDisplays() {
        return viewPreferences.get(TIME_ZONE_FOR_DISPLAYS, TimeZone.GMT_ZONE.getID());
    }

    public static void setTimeZoneForDisplays(String timeZone) {
        viewPreferences.put(TIME_ZONE_FOR_DISPLAYS, timeZone);
    }

    public static int numberOfFileIngestThreads() {
        return machineSpecificPreferences.getInt(NUMBER_OF_FILE_INGEST_THREADS, 2);
    }

    public static void setNumberOfFileIngestThreads(int value) {
        machineSpecificPreferences.putInt(NUMBER_OF_FILE_INGEST_THREADS, value);
    }

    @Deprecated
    public static boolean groupItemsInTreeByDatasource() {
        return viewPreferences.getBoolean(GROUP_ITEMS_IN_TREE_BY_DATASOURCE, false);
    }

    @Deprecated
    public static void setGroupItemsInTreeByDatasource(boolean value) {
        viewPreferences.putBoolean(GROUP_ITEMS_IN_TREE_BY_DATASOURCE, value);
    }

    /**
     * Get the user preference which identifies whether tags should be shown for
     * only the current user or all users.
     *
     * @return true for just the current user, false for all users
     */
    public static boolean showOnlyCurrentUserTags() {
        return viewPreferences.getBoolean(SHOW_ONLY_CURRENT_USER_TAGS, false);
    }

    /**
     * Set the user preference which identifies whether tags should be shown for
     * only the current user or all users.
     *
     * @param value - true for just the current user, false for all users
     */
    public static void setShowOnlyCurrentUserTags(boolean value) {
        viewPreferences.putBoolean(SHOW_ONLY_CURRENT_USER_TAGS, value);
    }

    /**
     * Get the user preference which identifies whether the (S)core, (C)omments,
     * and (O)ccurrences columns should be populated and displayed in the result
     * view.
     *
     * @return True if hiding SCO columns; otherwise false.
     */
    public static boolean getHideSCOColumns() {
        return viewPreferences.getBoolean(HIDE_SCO_COLUMNS, false);
    }

    /**
     * Set the user preference which identifies whether the (S)core, (C)omments,
     * and (O)ccurrences columns should be populated and displayed in the result
     * view.
     *
     * @param value The value of which to assign to the user preference.
     */
    public static void setHideSCOColumns(boolean value) {
        viewPreferences.putBoolean(HIDE_SCO_COLUMNS, value);
    }

    public static void setDisplayTranslatedFileNames(boolean value) {
        viewPreferences.putBoolean(DISPLAY_TRANSLATED_NAMES, value);
    }

    public static boolean displayTranslatedFileNames() {
        return viewPreferences.getBoolean(DISPLAY_TRANSLATED_NAMES, DISPLAY_TRANSLATED_NAMES_DEFAULT);
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
            dbType = DbType.valueOf(externalServicePreferences.get(EXTERNAL_DATABASE_TYPE, "POSTGRESQL")); //NON-NLS
        } catch (Exception ex) {
            dbType = DbType.SQLITE;
        }
        try {
            return new CaseDbConnectionInfo(
                    externalServicePreferences.get(EXTERNAL_DATABASE_HOSTNAME_OR_IP, ""),
                    externalServicePreferences.get(EXTERNAL_DATABASE_PORTNUMBER, "5432"),
                    externalServicePreferences.get(EXTERNAL_DATABASE_USER, ""),
                    TextConverter.convertHexTextToText(externalServicePreferences.get(EXTERNAL_DATABASE_PASSWORD, "")),
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
        externalServicePreferences.put(EXTERNAL_DATABASE_HOSTNAME_OR_IP, connectionInfo.getHost());
        externalServicePreferences.put(EXTERNAL_DATABASE_PORTNUMBER, connectionInfo.getPort());
        externalServicePreferences.put(EXTERNAL_DATABASE_USER, connectionInfo.getUserName());
        try {
            externalServicePreferences.put(EXTERNAL_DATABASE_PASSWORD, TextConverter.convertTextToHexText(connectionInfo.getPassword()));
        } catch (TextConverterException ex) {
            throw new UserPreferencesException("Failure converting text to password hext text", ex); // NON-NLS
        }
        externalServicePreferences.put(EXTERNAL_DATABASE_TYPE, connectionInfo.getDbType().toString());
    }

    public static void setIsMultiUserModeEnabled(boolean enabled) {
        externalServicePreferences.putBoolean(IS_MULTI_USER_MODE_ENABLED, enabled);
    }

    public static boolean getIsMultiUserModeEnabled() {
        return isMultiUserSupported() && externalServicePreferences.getBoolean(IS_MULTI_USER_MODE_ENABLED, false);
    }

    private static Boolean multiUserSupported = null;

    /**
     * Checks to see if SolrSearchService is a registered AutopsyService. If the
     * module is not found, the keyword search module and solr services have
     * likely been excluded from the build. In that event, services relying on
     * Solr (a.k.a. multiuser cases) will be disabled.
     *
     * @return True if multi user cases are supported.
     */
    public static boolean isMultiUserSupported() {
        if (multiUserSupported == null) {
            // looks for any SolrSearchService present in AutopsyService.
            multiUserSupported = Lookup.getDefault().lookupAll(AutopsyService.class).stream()
                    .anyMatch(obj -> obj.getClass().getName().equalsIgnoreCase("org.sleuthkit.autopsy.keywordsearch.SolrSearchService"));
        }

        return multiUserSupported;
    }

    public static String getIndexingServerHost() {
        return externalServicePreferences.get(SOLR8_SERVER_HOST, "");
    }

    public static void setIndexingServerHost(String hostName) {
        externalServicePreferences.put(SOLR8_SERVER_HOST, hostName);
    }

    public static String getIndexingServerPort() {
        return externalServicePreferences.get(SOLR8_SERVER_PORT, "8983");
    }

    public static void setIndexingServerPort(int port) {
        externalServicePreferences.putInt(SOLR8_SERVER_PORT, port);
    }

    public static String getSolr4ServerHost() {
        return externalServicePreferences.get(SOLR4_SERVER_HOST, "");
    }

    public static void setSolr4ServerHost(String hostName) {
        externalServicePreferences.put(SOLR4_SERVER_HOST, hostName);
    }

    public static String getSolr4ServerPort() {
        return externalServicePreferences.get(SOLR4_SERVER_PORT, "");
    }

    public static void setSolr4ServerPort(String port) {
        externalServicePreferences.put(SOLR4_SERVER_PORT, port);
    }

    public static String getZkServerHost() {
        return externalServicePreferences.get(ZK_SERVER_HOST, "");
    }

    public static void setZkServerHost(String hostName) {
        externalServicePreferences.put(ZK_SERVER_HOST, hostName);
    }

    public static String getZkServerPort() {
        return externalServicePreferences.get(ZK_SERVER_PORT, "9983");
    }

    public static void setZkServerPort(String port) {
        externalServicePreferences.put(ZK_SERVER_PORT, port);
    }

    public static void setTextTranslatorName(String textTranslatorName) {
        externalServicePreferences.put(TEXT_TRANSLATOR_NAME, textTranslatorName);
    }

    public static String getTextTranslatorName() {
        return externalServicePreferences.get(TEXT_TRANSLATOR_NAME, null);
    }

    public static void setUseOcrInTranslation(boolean enableOcr) {
        externalServicePreferences.putBoolean(OCR_TRANSLATION_ENABLED, enableOcr);
    }

    public static boolean getUseOcrInTranslation() {
        return externalServicePreferences.getBoolean(OCR_TRANSLATION_ENABLED, true);
    }

    /**
     * Persists message service connection info.
     *
     * @param info An object encapsulating the message service info.
     *
     * @throws org.sleuthkit.autopsy.core.UserPreferencesException
     */
    public static void setMessageServiceConnectionInfo(MessageServiceConnectionInfo info) throws UserPreferencesException {
        externalServicePreferences.put(MESSAGE_SERVICE_HOST, info.getHost());
        externalServicePreferences.put(MESSAGE_SERVICE_PORT, Integer.toString(info.getPort()));
        externalServicePreferences.put(MESSAGE_SERVICE_USER, info.getUserName());
        try {
            externalServicePreferences.put(MESSAGE_SERVICE_PASSWORD, TextConverter.convertTextToHexText(info.getPassword()));
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
            port = Integer.parseInt(externalServicePreferences.get(MESSAGE_SERVICE_PORT, DEFAULT_PORT_STRING));
        } catch (NumberFormatException ex) {
            // if there is an error parsing the port number, use the default port number
            port = DEFAULT_PORT_INT;
        }

        try {
            return new MessageServiceConnectionInfo(
                    externalServicePreferences.get(MESSAGE_SERVICE_HOST, ""),
                    port,
                    externalServicePreferences.get(MESSAGE_SERVICE_USER, ""),
                    TextConverter.convertHexTextToText(externalServicePreferences.get(MESSAGE_SERVICE_PASSWORD, "")));
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
        int timeOut = machineSpecificPreferences.getInt(PROCESS_TIME_OUT_HOURS, DEFAULT_PROCESS_TIMEOUT_HR);
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
        machineSpecificPreferences.putInt(PROCESS_TIME_OUT_HOURS, value);
    }

    /**
     * Reads persisted setting of whether process time out functionality is
     * enabled.
     *
     * @return boolean True if process time out is functionality enabled, false
     *         otherwise.
     */
    public static boolean getIsTimeOutEnabled() {
        boolean enabled = machineSpecificPreferences.getBoolean(PROCESS_TIME_OUT_ENABLED, false);
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
        machineSpecificPreferences.putBoolean(PROCESS_TIME_OUT_ENABLED, enabled);
    }

    /**
     * Get the display name for this program
     *
     * @return Name of this program
     */
    public static String getAppName() {
        return modePreferences.get(APP_NAME, Version.getName());
    }

    /**
     * Set the display name for this program
     *
     * @param name Display name
     */
    public static void setAppName(String name) {
        modePreferences.put(APP_NAME, name);
    }

    /**
     * get the maximum number of log files to save
     *
     * @return Number of log files
     */
    public static int getLogFileCount() {
        return machineSpecificPreferences.getInt(MAX_NUM_OF_LOG_FILE, LOG_FILE_NUM_INT);
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
        machineSpecificPreferences.putInt(MAX_NUM_OF_LOG_FILE, count);
    }

    /**
     * Get the maximum JVM heap size (in MB) for the embedded Solr server. The
     * returned value depends on the platform (64bit vs 32bit).
     *
     * @return Saved value or default (2 GB for 64bit platforms, 512MB for
     *         32bit)
     */
    public static int getMaxSolrVMSize() {
        if (PlatformUtil.is64BitJVM()) {
            return machineSpecificPreferences.getInt(SOLR_MAX_JVM_SIZE, DEFAULT_SOLR_HEAP_SIZE_MB_64BIT_PLATFORM);
        } else {
            return machineSpecificPreferences.getInt(SOLR_MAX_JVM_SIZE, DEFAULT_SOLR_HEAP_SIZE_MB_32BIT_PLATFORM);
        }
    }

    /**
     * Set the maximum JVM heap size (in MB) for the embedded Solr server.
     *
     * @param maxSize
     */
    public static void setMaxSolrVMSize(int maxSize) {
        machineSpecificPreferences.putInt(SOLR_MAX_JVM_SIZE, maxSize);
    }

    /**
     * Get the maximum number of results to display in a result table.
     *
     * @return Saved value or default (10,000).
     */
    public static int getResultsTablePageSize() {
        return viewPreferences.getInt(RESULTS_TABLE_PAGE_SIZE, 10_000);
    }

    /**
     * Set the maximum number of results to display in a result table.
     *
     * @param pageSize
     */
    public static void setResultsTablePageSize(int pageSize) {
        viewPreferences.putInt(RESULTS_TABLE_PAGE_SIZE, pageSize);
    }

    /**
     * Set the HdX path.
     *
     * @param executablePath User-inputted path to HxD executable
     */
    public static void setExternalHexEditorPath(String executablePath) {
        viewPreferences.put(EXTERNAL_HEX_EDITOR_PATH, executablePath);
    }

    /**
     * Retrieves the HdXEditor path set by the User. If not found, the default
     * will be the default install location of HxD.
     *
     * @return Path to HdX
     */
    public static String getExternalHexEditorPath() {
        return viewPreferences.get(EXTERNAL_HEX_EDITOR_PATH, Paths.get("C:", "Program Files", "HxD", "HxD.exe").toString());
    }

    /**
     * Set the geolocation tile server option.
     *
     * @param option
     */
    public static void setGeolocationTileOption(int option) {
        externalServicePreferences.putInt(GEO_TILE_OPTION, option);
    }

    /**
     * Retrieves the Geolocation tile option. If not found, the value will
     * default to 0.
     *
     * @return
     */
    public static int getGeolocationtTileOption() {
        return externalServicePreferences.getInt(GEO_TILE_OPTION, 0);
    }

    /**
     * Sets the path to the OSM tile zip file.
     *
     * @param absolutePath
     */
    public static void setGeolocationOsmZipPath(String absolutePath) {
        machineSpecificPreferences.put(GEO_OSM_TILE_ZIP_PATH, absolutePath);
    }

    /**
     * Retrieves the path for the OSM tile zip file or returns empty string if
     * none was found.
     *
     * @return Path to zip file
     */
    public static String getGeolocationOsmZipPath() {
        return machineSpecificPreferences.get(GEO_OSM_TILE_ZIP_PATH, "");
    }

    /**
     * Sets the address of geolocation window user defined OSM server data
     * source.
     *
     * @param address
     */
    public static void setGeolocationOsmServerAddress(String address) {
        externalServicePreferences.put(GEO_OSM_SERVER_ADDRESS, address);
    }

    /**
     * Retrieves the address to the OSM server or null if one was not found.
     *
     * @return Address of OSM server
     */
    public static String getGeolocationOsmServerAddress() {
        return externalServicePreferences.get(GEO_OSM_SERVER_ADDRESS, "");
    }

    /**
     * Sets the path for Geolocation MBTiles data source file.
     *
     * @param absolutePath
     */
    public static void setGeolocationMBTilesFilePath(String absolutePath) {
        machineSpecificPreferences.put(GEO_MBTILES_FILE_PATH, absolutePath);
    }

    /**
     * Retrieves the path for the Geolocation MBTiles data source file.
     *
     * @return Absolute path to MBTiles file or empty string if none was found.
     */
    public static String getGeolocationMBTilesFilePath() {
        return machineSpecificPreferences.get(GEO_MBTILES_FILE_PATH, "");
    }

    /**
     * @return A subdirectory of java.io.tmpdir.
     */
    private static File getSystemTempDirFile() {
        return Paths.get(System.getProperty("java.io.tmpdir"), getAppName(), TEMP_FOLDER).toFile();
    }

    /**
     * Retrieves the application temp directory and ensures the directory
     * exists.
     *
     * @return The absolute path to the application temp directory.
     */
    public static String getAppTempDirectory() {
        // NOTE: If this code changes, Case.getTempDirectory() should likely be checked
        // as well.  See JIRA 7505 for more information.
        File appTempDir = null;
        switch (UserMachinePreferences.getTempDirChoice()) {
            case CUSTOM:
                String customDirectory = UserMachinePreferences.getCustomTempDirectory();
                appTempDir = (StringUtils.isBlank(customDirectory))
                        ? null
                        : Paths.get(customDirectory, getAppName(), TEMP_FOLDER).toFile();
                break;
            case SYSTEM:
            default:
                // at this level, if the case directory is specified for a temp
                // directory, return the system temp directory instead.
                appTempDir = getSystemTempDirFile();
                break;
        }

        appTempDir = appTempDir == null ? getSystemTempDirFile() : appTempDir;

        if (!appTempDir.exists()) {
            appTempDir.mkdirs();
        }

        return appTempDir.getAbsolutePath();
    }

    /**
     * Set the last used health monitor report path.
     *
     * @param reportPath Last used health monitor report path.
     */
    public static void setHealthMonitorReportPath(String reportPath) {
        machineSpecificPreferences.put(HEALTH_MONITOR_REPORT_PATH, reportPath);
    }

    /**
     * Gets the last used health monitor report path.
     *
     * @return Last used health monitor report path. Empty string if no value
     *         has been recorded.
     */
    public static String getHealthMonitorReportPath() {
        return machineSpecificPreferences.get(HEALTH_MONITOR_REPORT_PATH, "");
    }
}
