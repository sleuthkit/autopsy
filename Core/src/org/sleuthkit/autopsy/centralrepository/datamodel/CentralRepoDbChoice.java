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

import org.openide.util.NbBundle.Messages;

/**
 * This represents a database choices available for central repo.
 */
@Messages({
    "CentralRepoDbChoice.Disabled.Text=Disabled",
    "CentralRepoDbChoice.Sqlite.Text=SQLite",
    "CentralRepoDbChoice.PostgreSQL_Multiuser.Text=PostgreSQL using multi-user settings",
    "CentralRepoDbChoice.PostgreSQL.Text=Custom PostgreSQL",
})
public enum CentralRepoDbChoice {
    DISABLED("Disabled", Bundle.CentralRepoDbChoice_Disabled_Text(), CentralRepoPlatforms.DISABLED),
    SQLITE("Sqlite", Bundle.CentralRepoDbChoice_Sqlite_Text(), CentralRepoPlatforms.SQLITE),
    POSTGRESQL_MULTIUSER("PostgreSQL_Multiuser", Bundle.CentralRepoDbChoice_PostgreSQL_Multiuser_Text(), CentralRepoPlatforms.POSTGRESQL),
    POSTGRESQL_CUSTOM("PostgreSQL", Bundle.CentralRepoDbChoice_PostgreSQL_Text(), CentralRepoPlatforms.POSTGRESQL);

    public static final CentralRepoDbChoice[] DB_CHOICES = new CentralRepoDbChoice[]{
        SQLITE, POSTGRESQL_MULTIUSER, POSTGRESQL_CUSTOM
    };


    private final String settingKey;
    private final String title;
    private final CentralRepoPlatforms platform;

    CentralRepoDbChoice(String key, String title, CentralRepoPlatforms platform) {
        this.settingKey = key;
        this.title = title;
        this.platform = platform;
    }

    /**
     * This is the value of this setting when saved to central repo properties.
     * @return      The value associated with this choice.
     */
    public String getSettingKey() {
        return settingKey;
    }

    /**
     * This is the human-readable title for this choice.
     * @return  The human-readable title for this choice.
     */
    public String getTitle() {
        return title;
    }

    /**
     * This represents the database type (i.e. Postgres, SQLite) associated with this choice.
     * @return  The database type associated with this choice.
     */
    public CentralRepoPlatforms getDbPlatform() {
        return platform;
    }
}
