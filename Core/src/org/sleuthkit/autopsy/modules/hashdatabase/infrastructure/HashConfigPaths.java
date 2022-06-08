/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase.infrastructure;

import java.nio.file.Paths;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Paths for hash config files.
 */
public class HashConfigPaths {

    private static final HashConfigPaths instance = new HashConfigPaths();

    private static final String HASHSET_FOLDER = "HashLookup";
    private static final String SERIALIZATION_FILE_NAME = "hashLookup.settings"; //NON-NLS   
    
    private static final String HASH_CONFIG_PATH = Paths.get(PlatformUtil.getModuleConfigDirectory(), HASHSET_FOLDER).toAbsolutePath().toString();

    private static final String XML_FILE_NAME = "hashsets.xml"; //NON-NLS
    private static final String XML_FILE_PATH = Paths.get(HASH_CONFIG_PATH, XML_FILE_NAME).toAbsolutePath().toString();
    
    private static final String HASH_DATABASE_DEFAULT_FOLDER = "HashDatabases";
    private static final String HASH_DATABASE_DEFAULT_PATH = Paths.get(HASH_CONFIG_PATH, HASH_DATABASE_DEFAULT_FOLDER).toString();
    
    private static final String SERIALIZATION_FILE_PATH = Paths.get(HASH_CONFIG_PATH, SERIALIZATION_FILE_NAME).toString(); //NON-NLS
    
    
    private HashConfigPaths() {
    }

    /**
     * @return Singleton instance of this class.
     */
    public static HashConfigPaths getInstance() {
        return instance;
    }

    /**
     * @return The base path to the config file.
     */
    public String getBasePath() {
        return HASH_CONFIG_PATH;
    }
    
    /**
     * @return The default hash database path.
     */
    public String getDefaultDbPath() {
        return HASH_DATABASE_DEFAULT_PATH;
    }

    /**
     * @return The path to the serialized settings file.
     */
    public String getSettingsPath() {
        return SERIALIZATION_FILE_PATH;
    }
    
    /**
     * @return The path to the xml settings file.
     */
    public String getXmlSettingsPath() {
        return XML_FILE_PATH;
    }
}
