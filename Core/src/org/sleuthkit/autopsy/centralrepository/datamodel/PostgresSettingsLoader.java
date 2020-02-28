/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.datamodel;

/**
 *
 * @author gregd
 */
public interface PostgresSettingsLoader {
    PostgresConnectionSettings loadSettings();
    void saveSettings(PostgresConnectionSettings settings);
    
    public static PostgresSettingsLoader CUSTOM_LOADER = new Custom();
    public static PostgresSettingsLoader MULTIUSER_LOADER = new MultiUser();

    
    static class Custom implements PostgresSettingsLoader {
        @Override
        public PostgresConnectionSettings loadSettings() {
            return CentralRepoPostgresSettingsUtil.loadCustomSettings();
        }

        @Override
        public void saveSettings(PostgresConnectionSettings settings) {
            CentralRepoPostgresSettingsUtil.saveCustomSettings(settings);
        }
    }
    
    
    static class MultiUser implements PostgresSettingsLoader {

        @Override
        public PostgresConnectionSettings loadSettings() {
            return CentralRepoPostgresSettingsUtil.loadMultiUserSettings();
        }

        @Override
        public void saveSettings(PostgresConnectionSettings settings) {}
    }
}
