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
 * 
 */
public class CentralRepoDbChoice {
    public static final CentralRepoDbChoice DISABLED = new CentralRepoDbChoice("Disabled", null);

    public static final CentralRepoDbChoice SQLITE = new CentralRepoDbChoice("Sqlite", CentralRepoPlatforms.SQLITE);

    public static final CentralRepoDbChoice POSTGRESQL_MULTIUSER = 
        new CentralRepoDbChoice("PostgreSQL_Multiuser", "PostgreSQL using multi-user settings", CentralRepoPlatforms.POSTGRESQL);

    public static final CentralRepoDbChoice POSTGRESQL_CUSTOM = 
        new CentralRepoDbChoice("PostgreSQL", "Custom PostgreSQL", CentralRepoPlatforms.POSTGRESQL);

    public static final CentralRepoDbChoice[] CHOICES = new CentralRepoDbChoice[]{
        DISABLED, SQLITE, POSTGRESQL_MULTIUSER, POSTGRESQL_CUSTOM
    };

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
    
    CentralRepoDbChoice(String key, CentralRepoPlatforms platform) {
        this(key, key, platform);
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getTitle() {
        return title;
    }

    public CentralRepoPlatforms getDbPlatform() {
        return platform;
    }
}
