/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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

import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Provides convenient access to a Preferences node for auto ingest user
 * preferences with default values.
 */
public final class AutoIngestUserPreferences {

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
    private static final int DEFAULT_MAX_TIMES_TO_PROCESS_IMAGE = 0;
    private static final String MAX_CONCURRENT_NODES_FOR_ONE_CASE = "MaxConcurrentNodesForOneCase"; // NON-NLS
    private static final String INPUT_SCAN_INTERVAL_TIME = "IntervalBetweenInputScan"; // NON-NLS

    // Prevent instantiation.
    private AutoIngestUserPreferences() {
    }

    /**
     * Get the value for the given preference name.
     *
     * @param preferenceName
     *
     * @return The preference value if it exists, otherwise an empty string.
     */
    private static String getPreferenceValue(String preferenceName) {
        // User preferences can be overridden through system properties
        // so we check those first. Defaults to empty string if the
        // property doesn't exist.
        String preferenceValue = System.getProperty(UserPreferences.SETTINGS_PROPERTIES + "." + preferenceName, "");

        if (preferenceValue.isEmpty() && ModuleSettings.settingExists(UserPreferences.SETTINGS_PROPERTIES, preferenceName)) {
            preferenceValue = ModuleSettings.getConfigSetting(UserPreferences.SETTINGS_PROPERTIES, preferenceName);
        }
        return preferenceValue;
    }

    /**
     * Get "Join auto ingest cluster" setting from persistent storage.
     *
     * @return SelectedMode Selected setting.
     */
    public static boolean getJoinAutoModeCluster() {
        return Boolean.parseBoolean(getPreferenceValue(JOIN_AUTO_MODE_CLUSTER));
    }

    /**
     * Set "Join auto ingest cluster" setting to persistent storage.
     *
     * @param join boolean value of whether to join auto ingest cluster or not
     */
    public static void setJoinAutoModeCluster(boolean join) {
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, JOIN_AUTO_MODE_CLUSTER, Boolean.toString(join));
    }

    /**
     * Get input folder for automated mode from persistent storage.
     *
     * @return String Selected input folder.
     */
    public static String getAutoModeImageFolder() {
        return getPreferenceValue(AUTO_MODE_IMAGES_FOLDER);
    }

    /**
     * Set input image folder for automated mode from persistent storage.
     *
     * @param folder Selected input folder.
     */
    public static void setAutoModeImageFolder(String folder) {
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, AUTO_MODE_IMAGES_FOLDER, folder);
    }

    /**
     * Get results folder for automated mode from persistent storage.
     *
     * @return String Selected output folder.
     */
    public static String getAutoModeResultsFolder() {
        return getPreferenceValue(AUTO_MODE_RESULTS_FOLDER);
    }

    /**
     * Set results folder for automated mode from persistent storage.
     *
     * @param folder Selected output folder.
     */
    public static void setAutoModeResultsFolder(String folder) {
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, AUTO_MODE_RESULTS_FOLDER, folder);
    }

    /**
     * Get shared config folder for automated mode from persistent storage.
     *
     * @return String Selected settings folder.
     */
    public static String getSharedConfigFolder() {
        return getPreferenceValue(SHARED_CONFIG_FOLDER);
    }

    /**
     * Set shared config folder for automated mode from persistent storage.
     *
     * @param folder the folder which contains the shared configF
     */
    public static void setSharedConfigFolder(String folder) {
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, SHARED_CONFIG_FOLDER, folder);
    }

    /**
     * Get shared config checkbox state for automated mode from persistent
     * storage.
     *
     * @return Boolean true if shared settings are enabled.
     */
    public static Boolean getSharedConfigEnabled() {
        return Boolean.parseBoolean(getPreferenceValue(SHARED_CONFIG_ENABLED));
    }

    /**
     * Save shared config checkbox state for automated mode to persistent
     * storage.
     *
     * @param sharedSettingsEnabled true = use shared settings in auto-ingest
     *                              mode
     */
    public static void setSharedConfigEnabled(boolean sharedSettingsEnabled) {
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, SHARED_CONFIG_ENABLED, Boolean.toString(sharedSettingsEnabled));
    }

    /**
     * Get shared config master checkbox state for automated mode from
     * persistent storage.
     *
     * @return true if this node is set as a shared configuration master
     */
    public static Boolean getSharedConfigMaster() {
        return Boolean.parseBoolean(getPreferenceValue(SHARED_CONFIG_MASTER));
    }

    /**
     * Save shared config master checkbox state to persistent storage.
     *
     * @param sharedSettingsMaster true = this node can upload configuration
     */
    public static void setSharedConfigMaster(boolean sharedSettingsMaster) {
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, SHARED_CONFIG_MASTER, Boolean.toString(sharedSettingsMaster));
    }

    /**
     * Get context string for automated mode ingest module settings.
     *
     * @return String Context string for automated mode ingest module settings.
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
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, SHOW_TOOLS_WARNING, Boolean.toString(showToolsWarning));
    }

    /**
     * Retrieve tools warning dialog setting.
     *
     * @return
     */
    public static boolean getShowToolsWarning() {
        String value = getPreferenceValue(SHOW_TOOLS_WARNING);
        return value.isEmpty() || Boolean.parseBoolean(value);
    }

    /**
     * Get the configured time to sleep between cases to prevent database locks
     *
     * @return int the value in seconds, default is 30 seconds.
     */
    public static int getSecondsToSleepBetweenCases() {
        String value = getPreferenceValue(SLEEP_BETWEEN_CASES_TIME);
        return value.isEmpty() ? 30 : Integer.parseInt(value);
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
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, SLEEP_BETWEEN_CASES_TIME, Integer.toString(value));
    }

    /**
     * Get maximum number of times to attempt processing an image folder. This
     * is used to avoid endless attempts to process an image folder with corrupt
     * data that causes a crash.
     *
     * @return int maximum number of attempts, default is 0.
     */
    public static int getMaxNumTimesToProcessImage() {
        String value = getPreferenceValue(MAX_NUM_TIMES_TO_PROCESS_IMAGE);
        return value.isEmpty() ? DEFAULT_MAX_TIMES_TO_PROCESS_IMAGE : Integer.parseInt(value);
    }

    /**
     * Set the maximum number of times to attempt to reprocess an image. This is
     * used to avoid endless attempts to process an image folder with corrupt
     * data that causes a crash.
     *
     * @param retries the number of retries to allow
     */
    public static void setMaxNumTimesToProcessImage(int retries) {
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, MAX_NUM_TIMES_TO_PROCESS_IMAGE, Integer.toString(retries));
    }

    /**
     * Get maximum number of concurrent ingest nodes allowable for one case at a
     * time.
     *
     * @return maximum number of concurrent nodes for one case. Default is 3.
     */
    public static int getMaxConcurrentJobsForOneCase() {
        if (ModuleSettings.settingExists(UserPreferences.SETTINGS_PROPERTIES, MAX_CONCURRENT_NODES_FOR_ONE_CASE)) {
            return Integer.parseInt(ModuleSettings.getConfigSetting(UserPreferences.SETTINGS_PROPERTIES, MAX_CONCURRENT_NODES_FOR_ONE_CASE));
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
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, MAX_CONCURRENT_NODES_FOR_ONE_CASE, Integer.toString(numberOfNodes));
    }

    /**
     * Get the configured time for input scan interval
     *
     * @return int the value in minutes, default is 60 minutes.
     */
    public static int getMinutesOfInputScanInterval() {
        String value = getPreferenceValue(INPUT_SCAN_INTERVAL_TIME);
        return value.isEmpty() ? 60 : Integer.parseInt(value);
    }

    /**
     * Set the configured time for input scan interval
     *
     * @param value the number of minutes for input interval
     */
    public static void setMinutesOfInputScanInterval(int value) {
        ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, INPUT_SCAN_INTERVAL_TIME, Integer.toString(value));
    }
}
