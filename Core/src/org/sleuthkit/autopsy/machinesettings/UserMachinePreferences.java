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
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Provides case-specific settings like the user-specified temp folder.
 *
 * NOTE: The Case class also handles providing a temp directory. When altering
 * code in this class, also look at the Case class as well.
 */
public final class UserMachinePreferences {

    private static final Logger logger = Logger.getLogger(UserMachinePreferences.class.getName());
    private static final Preferences preferences = NbPreferences.forModule(UserMachinePreferences.class);

    /**
     * The user specified choice for where the temp directory should be located.
     */
    public enum TempDirChoice {
        /**
         * A custom location specified with an absolute path by the user.
         */
        CUSTOM,
        /**
         * Equivalent to java.io.tmpdir.
         */
        SYSTEM,
        /**
         * If a case is open, a sub directory of the case.
         */
        CASE;

        /**
         * Returns the temp directory choice that matches the string provided
         * (whitespace and case insensitive).
         *
         * @param val The string value.
         *
         * @return The choice or empty if not found.
         */
        static Optional<TempDirChoice> getValue(String val) {
            if (val == null) {
                return Optional.empty();
            }

            return Stream.of(TempDirChoice.values())
                    .filter(tempChoice -> tempChoice.name().equalsIgnoreCase(val.trim()))
                    .findFirst();
        }
    }

    private static final String CUSTOM_TEMP_DIR_KEY = "TempDirectory";
    private static final String TEMP_DIR_CHOICE_KEY = "TempDirChoice";

    private static final TempDirChoice DEFAULT_CHOICE = TempDirChoice.SYSTEM;

    /**
     * @return The user-specified custom temp directory path or empty string.
     */
    public static String getCustomTempDirectory() {
        return preferences.get(CUSTOM_TEMP_DIR_KEY, "");
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
    public static void setCustomTempDirectory(String path) throws UserMachinePreferencesException {
        validateTempDirectory(path);
        preferences.put(CUSTOM_TEMP_DIR_KEY, path);
    }

    /**
     * @return The user selection for how the temp directory should be handled
     *         (temp directory in case folder, in java.io.tmpdir, custom path).
     *         Guaranteed to be non-null.
     */
    public static TempDirChoice getTempDirChoice() {
        return TempDirChoice.getValue(preferences.get(TEMP_DIR_CHOICE_KEY, null))
                .orElse(DEFAULT_CHOICE);
    }

    /**
     * Sets the temp directory choice (i.e. system, case, custom).
     *
     * @param tempDirChoice The choice (must be non-null).
     *
     * @throws UserMachinePreferencesException
     */
    public static void setTempDirChoice(TempDirChoice tempDirChoice) throws UserMachinePreferencesException {
        if (tempDirChoice == null) {
            throw new UserMachinePreferencesException("Expected non-null temp dir choice");
        }

        preferences.put(TEMP_DIR_CHOICE_KEY, tempDirChoice.name());
    }

    private UserMachinePreferences() {
    }
}
