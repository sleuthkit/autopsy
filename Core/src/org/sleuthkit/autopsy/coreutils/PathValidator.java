/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;

/**
 * Validates absolute path (e.g. to a data source or case output folder)
 * depending on case type.
 */
public final class PathValidator {

    private static final Pattern driveLetterPattern = Pattern.compile("^[Cc]:.*$");
    private static final Pattern unixMediaDrivePattern = Pattern.compile("^\\/(media|mnt)\\/.*$");

    /**
     * Checks if the provided path is valid given the case type.
     *
     * @param path     - the path to validate
     * @param caseType - the type of case which the path is being validated for
     *
     * @return - boolean true for valid path, false for invalid path
     */
    public static boolean isValidForCaseType(String path, Case.CaseType caseType) {

        if (caseType == Case.CaseType.MULTI_USER_CASE) {
            // check that path is not on "C:" drive
            if (pathOnCDrive(path)) {
                return false;
            }
        } else {
            // check that path is not a UNC path. Solr 8 does not allow UNC paths for indexes.
            if (UNCPathUtilities.isUNC(path)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidForRunningOnTarget(String path) {
        if (checkForLiveAutopsy()) {
            if (PlatformUtil.isWindowsOS()) {
                if (pathOnCDrive(path)) {
                    return false;
                }
            } else if (System.getProperty("os.name").toLowerCase().contains("nux") && !pathIsMedia(path)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether Autopsy is running from the external disk
     *
     * @return true if Autopsy is running from external USB or CD
     */
    private static boolean checkForLiveAutopsy() {
        return RuntimeProperties.isRunningInTarget();
    }

    /**
     * Checks whether a file path contains "/mnt" or "/media"
     *
     * @param filePath Input file absolute path
     *
     * @return true if path matches the pattern, false otherwise
     */
    private static boolean pathIsMedia(String filePath) {
        Matcher matcher = unixMediaDrivePattern.matcher(filePath);
        return matcher.find();
    }

    /**
     * Checks whether a file path contains drive letter defined by pattern.
     *
     * @param filePath Input file absolute path
     *
     * @return true if path matches the pattern, false otherwise.
     */
    private static boolean pathOnCDrive(String filePath) {
        Matcher m = driveLetterPattern.matcher(filePath);
        return m.find();
    }

    /**
     * Checks if the provided path is valid given the case type.
     *
     * @param path     - the path to validate
     * @param caseType - the type of case which the path is being validated for
     *
     * @return - boolean true for valid path, false for invalid path
     *
     * @deprecated - PathValidator.isValidForMultiUserCase directly replaces
     * PathValidator.isValid
     */
    @Deprecated
    public static boolean isValid(String path, Case.CaseType caseType) {
        return isValidForCaseType(path, caseType);
    }
    
    /**
     * Checks if the provided path is valid given the case type.
     *
     * @param path     - the path to validate
     * @param caseType - the type of case which the path is being validated for
     *
     * @return - boolean true for valid path, false for invalid path
     */
    @Deprecated    
    public static boolean isValidForMultiUserCase(String path, Case.CaseType caseType) {
        return isValidForCaseType(path, caseType);
    }
}
