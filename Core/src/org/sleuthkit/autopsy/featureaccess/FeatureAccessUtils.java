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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;

/**
 * Check if access to various features is permitted for the current user and the
 * current case, if any.
 *
 * IMPORTANT: These utilities are not concerned with transitory restrictions on
 * access to a feature, e.g., whether or not ingest is running.
 */
final public class FeatureAccessUtils {

    private final static String MULTIUSER_CASE_RESTRICTED_FILE_NAME = "mualimit"; // NON-NLS
    private final static String MULTIUSER_CASE_RESTRICTED_FILE_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), MULTIUSER_CASE_RESTRICTED_FILE_NAME).toString();
    private static final int DATA_SRC_DEL_MIN_DB_MAJOR_VER = 8;
    private static final int DATA_SRC_DEL_MIN_DB_MINOR_VER = 4;

    /**
     * Indicates whether or not a user can create multi-user cases.
     *
     * @return True or false.
     */
    public static boolean canCreateMultiUserCases() {
        return UserPreferences.getIsMultiUserModeEnabled() && multiUserCaseRestrictionsFileAbsent();
    }

    /**
     * Indicates whether or not a user can add data sources to a case.
     *
     * @return True or false.
     */
    public static boolean canAddDataSources() {
        return currentCaseIsSingleUserCase() || multiUserCaseRestrictionsFileAbsent();
    }

    /**
     * Indicates whether or not a user can delete data sources from a case.
     *
     * @return True or false.
     */
    public static boolean canDeleteDataSources() {
        boolean dataSourceDeletionAllowed = false;
        if (Case.isCaseOpen()) {
            CaseDbSchemaVersionNumber version = Case.getCurrentCase().getSleuthkitCase().getDBSchemaCreationVersion();
            dataSourceDeletionAllowed
                    = ((version.getMajor() > DATA_SRC_DEL_MIN_DB_MAJOR_VER) || (version.getMajor() == DATA_SRC_DEL_MIN_DB_MAJOR_VER && version.getMinor() >= DATA_SRC_DEL_MIN_DB_MINOR_VER))
                    && (currentCaseIsSingleUserCase() || multiUserCaseRestrictionsFileAbsent());
        }
        return dataSourceDeletionAllowed;
    }

    /**
     * Indicates whether or not the current case is a single-user case.
     *
     * @return True or false.
     */
    private static boolean currentCaseIsSingleUserCase() {
        return Case.isCaseOpen() && Case.getCurrentCase().getCaseType() == Case.CaseType.SINGLE_USER_CASE;
    }

    /**
     * Indicates whether or not the current user is allowed to create or modify
     * (add or delete data sources) multi-user cases.
     *
     * @return True or false.
     */
    public static boolean multiUserCaseRestrictionsFileAbsent() {
        File accessLimitingFile = new File(MULTIUSER_CASE_RESTRICTED_FILE_PATH);
        return !accessLimitingFile.exists();
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private FeatureAccessUtils() {
    }

}
