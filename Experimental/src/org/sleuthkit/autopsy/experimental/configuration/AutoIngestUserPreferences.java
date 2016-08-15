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

import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;
import org.openide.util.NbPreferences;

/**
 * Provides convenient access to a Preferences node for auto ingest user preferences
 * with default values.
 */
public final class AutoIngestUserPreferences {


    public enum SelectedMode {

        STANDALONE,
        AUTOMATED,
        REVIEW,
        COPYFILES
    };

    private static final Preferences preferences = NbPreferences.forModule(AutoIngestUserPreferences.class);
    private static final String MODE = "Mode"; // NON-NLS
    private static final String AUTO_MODE_IMAGES_FOLDER = "AutoModeImageFolder"; // NON-NLS
    private static final String AUTO_MODE_RESULTS_FOLDER = "AutoModeResultsFolder"; // NON-NLS
    private static final String SHARED_CONFIG_FOLDER = "SharedSettingsFolder"; // NON-NLS
    private static final String SHARED_CONFIG_ENABLED = "SharedSettingsEnabled"; // NON-NLS
    private static final String SHARED_CONFIG_MASTER = "SharedSettingsMaster"; // NON-NLS
    private static final String AUTO_MODE_CONTEXT_STRING = "AutoModeContext"; // NON-NLS
    private static final String COPY_MODE_SOURCE_FOLDER = "CopyModeSourceFolder"; // NON-NLS
    private static final String SLEEP_BETWEEN_CASES_TIME = "SleepBetweenCasesTime"; // NON-NLS
    private static final String MAX_NUM_TIMES_TO_PROCESS_IMAGE = "MaxNumTimesToAttemptToProcessImage"; // NON-NLS
    private static final String MAX_CONCURRENT_NODES_FOR_ONE_CASE = "MaxConcurrentNodesForOneCase"; // NON-NLS

    // Prevent instantiation.
    private AutoIngestUserPreferences() {
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
     * preferences files are going to be copied to another location while Autopsy
     * is running.
     *
     * @throws BackingStoreException
     */
    public static void saveToStorage() throws BackingStoreException {
        preferences.flush();
    }

    /**
     * Add change listener.
     *
     * @param listener Listener to be added.
     */
    public static void addChangeListener(PreferenceChangeListener listener) {
        preferences.addPreferenceChangeListener(listener);
    }

    /**
     * Remove change listener.
     *
     * @param listener Listener to be removed.
     */
    public static void removeChangeListener(PreferenceChangeListener listener) {
        preferences.removePreferenceChangeListener(listener);
    }

    /**
     * Get mode from persistent storage.
     *
     * @return SelectedMode Selected mode.
     */
    public static SelectedMode getMode() {
        int ordinal = preferences.getInt(MODE, SelectedMode.STANDALONE.ordinal());
        return SelectedMode.values()[ordinal];
    }

    /**
     * Set mode to persistent storage.
     *
     * @param mode Selected mode.
     */
    public static void setMode(SelectedMode mode) {
        preferences.putInt(MODE, mode.ordinal());
    }

    /**
     * Get input folder for automated mode from persistent storage.
     *
     * @return String Selected input folder.
     */
    public static String getAutoModeImageFolder() {
        return preferences.get(AUTO_MODE_IMAGES_FOLDER, "");
    }

    /**
     * Set input image folder for automated mode from persistent storage.
     *
     * @param folder Selected input folder.
     */
    public static void setAutoModeImageFolder(String folder) {
        preferences.put(AUTO_MODE_IMAGES_FOLDER, folder);
    }

    /**
     * Get results folder for automated mode from persistent storage.
     *
     * @return String Selected output folder.
     */
    public static String getAutoModeResultsFolder() {
        return preferences.get(AUTO_MODE_RESULTS_FOLDER, "");
    }

    /**
     * Set results folder for automated mode from persistent storage.
     *
     * @param folder Selected output folder.
     */
    public static void setAutoModeResultsFolder(String folder) {
        preferences.put(AUTO_MODE_RESULTS_FOLDER, folder);
    }

    /**
     * Get shared config folder for automated mode from persistent
     * storage.
     *
     * @return String Selected settings folder.
     */
    public static String getSharedConfigFolder() {
        return preferences.get(SHARED_CONFIG_FOLDER, "");
    }

    /**
     * Set shared config folder for automated mode from persistent
     * storage.
     */
    public static void setSharedConfigFolder(String folder) {
        preferences.put(SHARED_CONFIG_FOLDER, folder);
    }

    /**
     * Get shared config checkbox state for automated mode from
     * persistent storage.
     *
     * @return Boolean true if shared settings are enabled.
     */
    public static Boolean getSharedConfigEnabled() {
        return preferences.getBoolean(SHARED_CONFIG_ENABLED, false);
    }

    /**
     * Save shared config checkbox state for automated mode to persistent
     * storage.
     *
     * @param sharedSettingsEnabled true = use shared settings in auto-ingest
     *                              mode
     */
    public static void setSharedConfigEnabled(boolean sharedSettingsEnabled) {
        preferences.putBoolean(SHARED_CONFIG_ENABLED, sharedSettingsEnabled);
    }

    /**
     * Get shared config master checkbox state for automated mode from
     * persistent storage.
     *
     * @return true if this node is set as a shared configuration master
     */
    public static Boolean getSharedConfigMaster() {
        return preferences.getBoolean(SHARED_CONFIG_MASTER, false);
    }

    /**
     * Save shared config master checkbox state to persistent storage.
     *
     * @param sharedSettingsMaster true = this node can upload configuration
     */
    public static void setSharedConfigMaster(boolean sharedSettingsMaster) {
        preferences.putBoolean(SHARED_CONFIG_MASTER, sharedSettingsMaster);
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
     * Get source folder for Copy Mode from persistent storage.
     *
     * @return String Most recent source folder.
     */
    public static String getCopyModeSourceFolder() {
        return preferences.get(COPY_MODE_SOURCE_FOLDER, "C:\\");
    }

    /**
     * Set source folder for Copy Mode into persistent storage.
     *
     * @param folder Selected source folder.
     */
    public static void setCopyModeSourceFolder(String folder) {
        preferences.put(COPY_MODE_SOURCE_FOLDER, folder);
    }

    /**
     * Get the configured time to sleep between cases to prevent
     * database locks
     *
     * @return int the value in seconds, default is 30 seconds.
     */
    public static int getSecondsToSleepBetweenCases() {
        int answer = Integer.parseInt(preferences.get(SLEEP_BETWEEN_CASES_TIME, "30"));
        return answer;
    }

    /**
     * Set the configured time to sleep between cases to prevent
     * database locks
     *
     * @param int value the number of seconds to sleep between cases
     */
    public static void setSecondsToSleepBetweenCases(int value) {
        preferences.put(SLEEP_BETWEEN_CASES_TIME, Integer.toString(value));
    }

    /**
     * Get maximum number of times to attempt processing an image folder. This
     * is used to avoid endless attempts to process an image folder with corrupt
     * data that causes a crash.
     *
     * @return int maximum number of attempts, default is 2.
     */
    public static int getMaxNumTimesToProcessImage() {
        int answer = Integer.parseInt(preferences.get(MAX_NUM_TIMES_TO_PROCESS_IMAGE, "2"));
        return answer;
    }

    /**
     * Set the maximum number of times to attempt to reprocess an image. This is
     * used to avoid endless attempts to process an image folder with corrupt
     * data that causes a crash.
     *
     * @param retries the number of retries to allow
     */
    public static void setMaxNumTimesToProcessImage(int retries) {
        preferences.putInt(MAX_NUM_TIMES_TO_PROCESS_IMAGE, retries);
    }

    /**
     * Get maximum number of concurrent ingest nodes allowable for one case at a
     * time.
     *
     * @return maximum number of concurrent nodes for one case. Default is 3.
     */
    public static int getMaxConcurrentJobsForOneCase() {
        return Integer.parseInt(preferences.get(MAX_CONCURRENT_NODES_FOR_ONE_CASE, "3"));
    }

    /**
     * Get maximum number of concurrent ingest nodes allowable for one case at a
     * time.
     *
     * @param numberOfNodes the number of concurrent nodes to allow for one case
     */
    public static void setMaxConcurrentIngestNodesForOneCase(int numberOfNodes) {
        preferences.putInt(MAX_CONCURRENT_NODES_FOR_ONE_CASE, numberOfNodes);
    }    
}
