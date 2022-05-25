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
package org.sleuthkit.autopsy.core.configpath;

import java.nio.file.Paths;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Path to sharable config.
 */
public class SharedConfigPath {

    private static final SharedConfigPath instance = new SharedConfigPath();

    private static final String SHARED_FOLDER = "SharableConfig";
    private static final String SHARED_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), SHARED_FOLDER).toAbsolutePath().toString();

    private SharedConfigPath() {
    }

    /**
     * @return An instance of this class.
     */
    public static SharedConfigPath getInstance() {
        return instance;
    }

    /**
     * @return The path to a folder for config items that can be shared between
     *         different instances.
     */
    public String getSharedConfigPath() {
        return SHARED_PATH;
    }
    
    /**
     * @return The folder in user config for shared config.
     */
    public String getSharedConfigFolder() {
        return SHARED_FOLDER;
    }
}
