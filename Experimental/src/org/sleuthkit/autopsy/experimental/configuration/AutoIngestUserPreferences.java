/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.configuration;

import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Provides convenient access to a Preferences node for auto ingest user preferences
 * with default values.
 */
public final class AutoIngestUserPreferences {


    public enum SelectedMode {

        STANDALONE,
        AUTOMATED,
        REVIEW
    };

    private static final String SETTINGS_PROPERTIES = "AutoIngest";
    private static final String MODE = "AutopsyMode"; // NON-NLS
    private static final String JOIN_AUTO_MODE_CLUSTER = "JoinAutoModeCluster"; // NON-NLS
    private static final String AUTO_MODE_IMAGES_FOLDER = "AutoModeImageFolder"; // NON-NLS
    private static final String AUTO_MODE_RESULTS_FOLDER = "AutoModeResultsFolder"; // NON-NLS
    private static final String SHARED_CONFIG_FOLDER = "SharedSettingsFolder"; // NON-NLS
    private static final String SHARED_CONFIG_ENABLED = "SharedSettingsEnabled"; // NON-NLS
    private static final String SHARED_CONFIG_MASTER = "SharedSettingsMaster"; // NON-NLS
    private static final String AUTO_MODE_CONTEXT_STRING = "AutoModeContext"; // NON-NLS
    private static final String SLEEP_BETWEEN_CASES_TIME = "SleepBetweenCasesTime"; // NON-NLS
    private static final String SHOW_TOOLS_WARNING = "ShowToolsWarning"; // NON-NLS
    private static final String MAX_NUM_TIMES_TO_PROCESS_IMAGE = "MaxNumTimesToAttemptToProcessImage"; // NON-NLS
    private static final String MAX_CONCURRENT_NODES_FOR_ONE_CASE = "MaxConcurrentNodesForOneCase"; // NON-NLS
    private static final String STATUS_DATABASE_LOGGING_ENABLED = "StatusDatabaseLoggingEnabled"; // NON-NLS
    private static final String LOGGING_DB_HOSTNAME_OR_IP = "LoggingHostnameOrIP"; // NON-NLS
    private static final String LOGGING_PORT = "LoggingPort"; // NON-NLS
    private static final String LOGGING_USERNAME = "LoggingUsername"; // NON-NLS
    private static final String LOGGING_PASSWORD = "LoggingPassword"; // NON-NLS
    private static final String LOGGING_DATABASE_NAME = "LoggingDatabaseName"; // NON-NLS
    private static final String INPUT_SCAN_INTERVAL_TIME = "IntervalBetweenInputScan"; // NON-NLS

    // Prevent instantiation.
    private AutoIngestUserPreferences() {
    }

    /**
     * Get mode from persistent storage.
     *
     * @return SelectedMode Selected mode.
     */
    public static SelectedMode getMode() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, MODE)) {
            int ordinal = Integer.parseInt(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, MODE));
            return SelectedMode.values()[ordinal];
        }
        return SelectedMode.STANDALONE;
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
     * Get "Join Automated Ingest Cluster" setting from persistent storage.
     *
     * @return SelectedMode Selected setting.
     */
    public static boolean getJoinAutoModeCluster() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, JOIN_AUTO_MODE_CLUSTER)) {
            return Boolean.parseBoolean(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, JOIN_AUTO_MODE_CLUSTER));
        }
        return false;
    }

    /**
     * Set "Join Automated Ingest Cluster" setting to persistent storage.
     *
     * @param join boolean value of whether to join auto ingest cluster or not
     */
    public static void setJoinAutoModeCluster(boolean join) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, JOIN_AUTO_MODE_CLUSTER, Boolean.toString(join));
    }
    
    /**
     * Get input folder for automated mode from persistent storage.
     *
     * @return String Selected input folder.
     */
    public static String getAutoModeImageFolder() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, AUTO_MODE_IMAGES_FOLDER)) {
            return ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, AUTO_MODE_IMAGES_FOLDER);
        }
        return "";
    }

    /**
     * Set input image folder for automated mode from persistent storage.
     *
     * @param folder Selected input folder.
     */
    public static void setAutoModeImageFolder(String folder) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, AUTO_MODE_IMAGES_FOLDER, folder);
    }

    /**
     * Get results folder for automated mode from persistent storage.
     *
     * @return String Selected output folder.
     */
    public static String getAutoModeResultsFolder() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, AUTO_MODE_RESULTS_FOLDER)) {
            return ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, AUTO_MODE_RESULTS_FOLDER);
        }
        return "";
    }

    /**
     * Set results folder for automated mode from persistent storage.
     *
     * @param folder Selected output folder.
     */
    public static void setAutoModeResultsFolder(String folder) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, AUTO_MODE_RESULTS_FOLDER, folder);
    }

    /**
     * Get shared config folder for automated mode from persistent
     * storage.
     *
     * @return String Selected settings folder.
     */
    public static String getSharedConfigFolder() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, SHARED_CONFIG_FOLDER)) {
            return ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, SHARED_CONFIG_FOLDER);
        }
        return "";
    }

    /**
     * Set shared config folder for automated mode from persistent
     * storage.
     */
    public static void setSharedConfigFolder(String folder) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, SHARED_CONFIG_FOLDER, folder);
    }

    /**
     * Get shared config checkbox state for automated mode from
     * persistent storage.
     *
     * @return Boolean true if shared settings are enabled.
     */
    public static Boolean getSharedConfigEnabled() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, SHARED_CONFIG_ENABLED)) {
            return Boolean.parseBoolean(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, SHARED_CONFIG_ENABLED));
        }
        return false;
    }

    /**
     * Save shared config checkbox state for automated mode to persistent
     * storage.
     *
     * @param sharedSettingsEnabled true = use shared settings in auto-ingest
     *                              mode
     */
    public static void setSharedConfigEnabled(boolean sharedSettingsEnabled) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, SHARED_CONFIG_ENABLED, Boolean.toString(sharedSettingsEnabled));
    }

    /**
     * Get shared config master checkbox state for automated mode from
     * persistent storage.
     *
     * @return true if this node is set as a shared configuration master
     */
    public static Boolean getSharedConfigMaster() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, SHARED_CONFIG_MASTER)) {
            return Boolean.parseBoolean(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, SHARED_CONFIG_MASTER));
        }
        return false;
    }

    /**
     * Save shared config master checkbox state to persistent storage.
     *
     * @param sharedSettingsMaster true = this node can upload configuration
     */
    public static void setSharedConfigMaster(boolean sharedSettingsMaster) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, SHARED_CONFIG_MASTER, Boolean.toString(sharedSettingsMaster));
    }

    /**
     * Get context string for automated mode ingest module settings.
     *
     * @return String Context string for automated mode ingest module
     *         settings.
     */
    public static String getAutoModeIngestModuleContextString() {
        return AUTO_MODE_CONTEXT_STRING;
    }

    /**
     * Save whether tools warning dialog should be shown on startup.
     *
     * @param showToolsWarning true = show warning dialog, false = don't show
     */
    public static void setShowToolsWarning(boolean showToolsWarning) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, SHOW_TOOLS_WARNING, Boolean.toString(showToolsWarning));
    }

    /**
     * Retrieve tools warning dialog setting.
     *
     * @return
     */
    public static boolean getShowToolsWarning() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, SHOW_TOOLS_WARNING)) {
            return Boolean.parseBoolean(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, SHOW_TOOLS_WARNING));
        }
        return true;
    }

    /**
     * Get the configured time to sleep between cases to prevent
     * database locks
     *
     * @return int the value in seconds, default is 30 seconds.
     */
    public static int getSecondsToSleepBetweenCases() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, SLEEP_BETWEEN_CASES_TIME)) {
            return Integer.parseInt(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, SLEEP_BETWEEN_CASES_TIME));
        }
        return 30;
    }

    /**
     * Sets the wait time used by auto ingest nodes to ensure proper
     * synchronization of node operations in circumstances where delays may
     * occur, e.g., network file system latency effects on the visibility of
     * newly created shared directories and files.
     *
     * @param value value the number of seconds to sleep between cases
     */
    public static void setSecondsToSleepBetweenCases(int value) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, SLEEP_BETWEEN_CASES_TIME, Integer.toString(value));
    }

    /**
     * Get maximum number of times to attempt processing an image folder. This
     * is used to avoid endless attempts to process an image folder with corrupt
     * data that causes a crash.
     *
     * @return int maximum number of attempts, default is 2.
     */
    public static int getMaxNumTimesToProcessImage() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, MAX_NUM_TIMES_TO_PROCESS_IMAGE)) {
            return Integer.parseInt(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, MAX_NUM_TIMES_TO_PROCESS_IMAGE));
        }
        return 2;
    }

    /**
     * Set the maximum number of times to attempt to reprocess an image. This is
     * used to avoid endless attempts to process an image folder with corrupt
     * data that causes a crash.
     *
     * @param retries the number of retries to allow
     */
    public static void setMaxNumTimesToProcessImage(int retries) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, MAX_NUM_TIMES_TO_PROCESS_IMAGE, Integer.toString(retries));
    }

    /**
     * Get maximum number of concurrent ingest nodes allowable for one case at a
     * time.
     *
     * @return maximum number of concurrent nodes for one case. Default is 3.
     */
    public static int getMaxConcurrentJobsForOneCase() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, MAX_CONCURRENT_NODES_FOR_ONE_CASE)) {
            return Integer.parseInt(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, MAX_CONCURRENT_NODES_FOR_ONE_CASE));
        }
        return 3;
    }

    /**
     * Get maximum number of concurrent ingest nodes allowable for one case at a
     * time.
     *
     * @param numberOfNodes the number of concurrent nodes to allow for one case
     */
    public static void setMaxConcurrentIngestNodesForOneCase(int numberOfNodes) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, MAX_CONCURRENT_NODES_FOR_ONE_CASE, Integer.toString(numberOfNodes));
    }

    /**
     * Get status database logging checkbox state for automated ingest mode from
     * persistent storage.
     *
     * @return Boolean true if database logging is enabled.
     */
    public static Boolean getStatusDatabaseLoggingEnabled() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, STATUS_DATABASE_LOGGING_ENABLED)) {
            return Boolean.parseBoolean(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, STATUS_DATABASE_LOGGING_ENABLED));
        }
        return false;
    }

    /**
     * Save status database logging checkbox state for automated ingest mode to
     * persistent storage.
     *
     * @param databaseLoggingEnabled true = use database logging in auto-ingest
     *                               mode
     */
    public static void setStatusDatabaseLoggingEnabled(boolean databaseLoggingEnabled) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, STATUS_DATABASE_LOGGING_ENABLED, Boolean.toString(databaseLoggingEnabled));
    }

    /**
     * Get the logging database hostname from persistent storage.
     *
     * @return Logging database hostname or IP
     */
    public static String getLoggingDatabaseHostnameOrIP() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, LOGGING_DB_HOSTNAME_OR_IP)) {
            return ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, LOGGING_DB_HOSTNAME_OR_IP);
        }
        return "";
    }

    /**
     * Save the logging database hostname to persistent storage.
     *
     * @param hostname Logging database hostname or IP
     */
    public static void setLoggingDatabaseHostnameOrIP(String hostname) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, LOGGING_DB_HOSTNAME_OR_IP, hostname);
    }

    /**
     * Get the logging database port from persistent storage.
     *
     * @return logging database port
     */
    public static String getLoggingPort() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, LOGGING_PORT)) {
            return ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, LOGGING_PORT);
        }
        return "";
    }

    /**
     * Save the logging database port to persistent storage.
     *
     * @param port Logging database port
     */
    public static void setLoggingPort(String port) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, LOGGING_PORT, port);
    }

    /**
     * Get the logging database username from persistent storage.
     *
     * @return logging database username
     */
    public static String getLoggingUsername() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, LOGGING_USERNAME)) {
            return ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, LOGGING_USERNAME);
        }
        return "";
    }

    /**
     * Save the logging database username to persistent storage.
     *
     * @param username Logging database username
     */
    public static void setLoggingUsername(String username) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, LOGGING_USERNAME, username);
    }

    /**
     * Get the logging database password from persistent storage.
     *
     * @return logging database password
     */
    public static String getLoggingPassword() throws UserPreferencesException {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, LOGGING_PASSWORD)) {
            return TextConverter.convertHexTextToText(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, LOGGING_PASSWORD));
        }
        return "";
    }

    /**
     * Save the logging database password to persistent storage.
     *
     * @param password Logging database password
     */
    public static void setLoggingPassword(String password) throws UserPreferencesException {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, LOGGING_PASSWORD, TextConverter.convertTextToHexText(password));
    }

    /**
     * Get the logging database name from persistent storage.
     *
     * @return logging database name
     */
    public static String getLoggingDatabaseName() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, LOGGING_DATABASE_NAME)) {
            return ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, LOGGING_DATABASE_NAME);
        }
        return "";
    }

    /**
     * Save the logging database name to persistent storage.
     *
     * @param name Logging database name
     */
    public static void setLoggingDatabaseName(String name) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, LOGGING_DATABASE_NAME, name);
    }

    /**
     * Get the configured time for input scan interval
     *
     * @return int the value in minutes, default is 60 minutes.
     */
    public static int getMinutesOfInputScanInterval() {
        if (ModuleSettings.settingExists(SETTINGS_PROPERTIES, INPUT_SCAN_INTERVAL_TIME)) {
            return Integer.parseInt(ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, INPUT_SCAN_INTERVAL_TIME));
        }
        return 60;
    }

    /**
     * Set the configured time for input scan interval
     *
     * @param value the number of minutes for input interval
     */
    public static void setMinutesOfInputScanInterval(int value) {
        ModuleSettings.setConfigSetting(SETTINGS_PROPERTIES, INPUT_SCAN_INTERVAL_TIME, Integer.toString(value));
    }
    
    /**
     * Copied from Autopsy UserPreferences - can be removed once everything is merged together.
     * Provides ability to convert text to hex text.
     */
    static final class TextConverter {

        private static final char[] TMP = "hgleri21auty84fwe".toCharArray(); //NON-NLS
        private static final byte[] SALT = {
            (byte) 0xde, (byte) 0x33, (byte) 0x10, (byte) 0x12,
            (byte) 0xde, (byte) 0x33, (byte) 0x10, (byte) 0x12,};

        /**
         * Convert text to hex text.
         *
         * @param property Input text string.
         *
         * @return Converted hex string.
         *
         * @throws org.sleuthkit.autopsy.core.UserPreferencesException
         */
        static String convertTextToHexText(String property) throws UserPreferencesException {
            try {
                SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES"); //NON-NLS
                SecretKey key = keyFactory.generateSecret(new PBEKeySpec(TMP));
                Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES"); //NON-NLS
                pbeCipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
                return base64Encode(pbeCipher.doFinal(property.getBytes("UTF-8")));
            } catch (Exception ex) {
                throw new UserPreferencesException("Error encrypting text");
            }
        }

        private static String base64Encode(byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }

        /**
         * Convert hex text back to text.
         *
         * @param property Input hex text string.
         *
         * @return Converted text string.
         *
         * @throws org.sleuthkit.autopsy.core.UserPreferencesException
         */
        static String convertHexTextToText(String property) throws UserPreferencesException {
            try {
                SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES"); //NON-NLS
                SecretKey key = keyFactory.generateSecret(new PBEKeySpec(TMP));
                Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES"); //NON-NLS
                pbeCipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
                return new String(pbeCipher.doFinal(base64Decode(property)), "UTF-8");
            } catch (Exception ex) {
                throw new UserPreferencesException("Error decrypting text");
            }
        }

        private static byte[] base64Decode(String property) {
            return Base64.getDecoder().decode(property);
        }
    }
}
