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

/**
 * This is an interface to load or save postgres settings.
 */
public interface PostgresSettingsLoader {
    /**
     * This method loads the current settings.
     * @return      The settings that were loaded.
     */
    PostgresConnectionSettings loadSettings();

    /**
     * This method saves the current settings.
     * @param settings      The settings to save.
     */
    void saveSettings(PostgresConnectionSettings settings);
    
    PostgresSettingsLoader CUSTOM_SETTINGS_LOADER = new Custom();
    PostgresSettingsLoader MULTIUSER_SETTINGS_LOADER = new MultiUser();
    CentralRepoPostgresSettingsUtil SETTINGS_UTIL = CentralRepoPostgresSettingsUtil.getInstance();

    
    /**
     * This class loads and saves custom postgres settings.
     */
    class Custom implements PostgresSettingsLoader {
        @Override
        public PostgresConnectionSettings loadSettings() {
            return SETTINGS_UTIL.loadCustomSettings();
        }

        @Override
        public void saveSettings(PostgresConnectionSettings settings) {
            SETTINGS_UTIL.saveCustomSettings(settings);
        }
    }
    
    
    /**
     * This class loads multi-user postgres settings to be used with central repo.
     * NOTE: This class does not save settings on save operation as this is merely a proxy.
     */
    class MultiUser implements PostgresSettingsLoader {

        @Override
        public PostgresConnectionSettings loadSettings() {
            return SETTINGS_UTIL.loadMultiUserSettings();
        }

        /**
         * NOTE: This action does not do anything.  There is no need to save since
         * this is just a proxy to multi user settings.
         * @param settings  The settings to save.
         */
        @Override
        public void saveSettings(PostgresConnectionSettings settings) {}
    }
}
