/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.featureaccess;

import java.io.File;
import java.nio.file.Paths;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Check if access to various features is permitted for the current user.
 */
final public class UserFeatureAccessUtils {

    private final static String MULTI_USER_ACCESS_FILE_NAME = "mualimit"; // NON-NLS
    private final static String MULTI_USER_ACCESS_FILE_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), MULTI_USER_ACCESS_FILE_NAME).toString();

    /**
     * Indicates whether or not the current user is allowed to create or modify
     * (add or delete data sources) multi-user cases.
     *
     * @return True or false.
     */
    public static boolean canCreateOrModifyMultiUserCases() {
        File accessLimitingFile = new File(MULTI_USER_ACCESS_FILE_PATH);
        return !accessLimitingFile.exists();
    }

    /**
     * Private constructor to prevent instatiation of this utility class.
     */
    private UserFeatureAccessUtils() {
    }

}
