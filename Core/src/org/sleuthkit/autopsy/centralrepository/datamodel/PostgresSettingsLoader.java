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
 * interface to load or save postgres settings
 */
public interface PostgresSettingsLoader {
    PostgresConnectionSettings loadSettings();
    void saveSettings(PostgresConnectionSettings settings);
    
    PostgresSettingsLoader CUSTOM_LOADER = new Custom();
    PostgresSettingsLoader MULTIUSER_LOADER = new MultiUser();

    
    /**
     * loads and saves custom postgres settings
     */
    class Custom implements PostgresSettingsLoader {
        @Override
        public PostgresConnectionSettings loadSettings() {
            return CentralRepoPostgresSettingsUtil.loadCustomSettings();
        }

        @Override
        public void saveSettings(PostgresConnectionSettings settings) {
            CentralRepoPostgresSettingsUtil.saveCustomSettings(settings);
        }
    }
    
    
    /**
     * loads multi user postgres settings to be used with central repo
     * NOTE: does not save settings on save operation as this is merely a proxy
     */
    class MultiUser implements PostgresSettingsLoader {

        @Override
        public PostgresConnectionSettings loadSettings() {
            return CentralRepoPostgresSettingsUtil.loadMultiUserSettings();
        }

        /**
         * no need to save since this is just a proxy to multi user settings
         * @param settings  the settings to save
         */
        @Override
        public void saveSettings(PostgresConnectionSettings settings) {}
    }
}
