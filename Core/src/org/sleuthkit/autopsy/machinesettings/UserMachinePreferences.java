/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.machinesettings;

import java.io.File;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.coreutils.FileUtil;

/**
 * Provides case-specific settings like the user-specified temp folder.
 */
public final class UserMachinePreferences {

    private static final Preferences preferences = NbPreferences.forModule(UserMachinePreferences.class);

    private static final String TEMP_DIR_KEY = "TempDirectory";

    /**
     * Retrieves a default temporary directory that is a subdirectory of
     * java.io.tmpdir.
     *
     * @return The absolute path to the temp directory.
     */
    private static String getDefaultTempDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().toString();
    }

    /**
     * Retrieves the base user-specified temporary directory.
     *
     * @return The base user-specified temporary directory.
     */
    public static String getBaseTempDirectory() {
        String tempDir = preferences.get(TEMP_DIR_KEY, null);
        return StringUtils.isBlank(tempDir) ? getDefaultTempDirectory() : tempDir;
    }

    /**
     * Checks to see if temporary directory location can be created and is
     * read/write.
     *
     * @param path The location.
     *
     * @return True if this is a valid location for a temp directory.
     *
     * @throws UserMachinePreferencesException If path could not be validated
     *                                         due to mkdirs failure or the
     *                                         directory is not read/write.
     */
    @NbBundle.Messages({
        "# {0} - path",
        "UserMachinePreferences_validateTempDirectory_errorOnCreate_text=There was an error creating the temp directory for path: {0}",
        "# {0} - path",
        "UserMachinePreferences_validateTempDirectory_errorOnReadWrite_text=There was an error reading or writing to temp directory path: {0}"
    })
    private static boolean validateTempDirectory(String path) throws UserMachinePreferencesException {
        if (StringUtils.isBlank(path)) {
            // in this instance, the default path will be used.
            return true;
        }

        File f = new File(path);
        if (!f.exists() && !f.mkdirs()) {
            throw new UserMachinePreferencesException(Bundle.UserMachinePreferences_validateTempDirectory_errorOnCreate_text(path));
        }

        if (!FileUtil.hasReadWriteAccess(Paths.get(path))) {
            throw new UserMachinePreferencesException(Bundle.UserMachinePreferences_validateTempDirectory_errorOnReadWrite_text(path));
        }
        return true;
    }

    /**
     * Sets the base user-specified temporary directory.
     *
     * @param path The path to the directory.
     *
     * @throws UserMachinePreferencesException If the directory cannot be
     *                                         accessed or created.
     */
    public static void setBaseTempDirectory(String path) throws UserMachinePreferencesException {
        validateTempDirectory(path);
        preferences.put(TEMP_DIR_KEY, path);
    }

    private UserMachinePreferences() {
    }
}
