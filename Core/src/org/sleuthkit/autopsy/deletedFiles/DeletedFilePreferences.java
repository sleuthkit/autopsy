/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.deletedFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;

/**
 * Class to store settings related to the display of deleted files.
 */
public class DeletedFilePreferences {

    private static final String SETTINGS_FILE = "DeletedFilePreferences.properties"; //NON-NLS
    private static final String KEY_LIMIT_DELETED_FILES = "limitDeletedFiles"; //NON-NLS
    private static final String KEY_LIMIT_VALUE = "limitValue";
    private static final String VALUE_TRUE = "true"; //NON-NLS
    private static final String VALUE_FALSE = "false"; //NON-NLS
    private static final int DEFAULT_MAX_OBJECTS = 10001;
    private static final Logger logger = Logger.getLogger(CasePreferences.class.getName());
    private static DeletedFilePreferences defaultInstance;
    private static boolean limitDeletedFiles = true;
    private static int deletedFilesLimit = DEFAULT_MAX_OBJECTS;

    /**
     * Get the settings for the display of deleted files.
     *
     * @return defaultInstance with freshly loaded
     */
    public static synchronized DeletedFilePreferences getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new DeletedFilePreferences();
        }
        defaultInstance.loadFromStorage();
        return defaultInstance;
    }

    /**
     * Prevent instantiation.
     */
    private DeletedFilePreferences() {
    }

    /**
     * Get the 'limitDeletedFiles' value. This can be true or false. It will
     * default to true if it was not saved correctly previously.s
     *
     * @return true if the number of deleted files displayed should be limied,
     *         false if it should not be limited.
     */
    public boolean getShouldLimitDeletedFiles() {
        return limitDeletedFiles;
    }

    /**
     * Set the 'limitDeletedFiles' value to true or false.
     *
     * @param value true if the number of deleted files displayed should be
     *              limied, false if it should not be limited.
     */
    public void setShouldLimitDeletedFiles(boolean value) {
        limitDeletedFiles = value;
        saveToStorage();
        DirectoryTreeTopComponent.getDefault().refreshContentTreeSafe();
    }

    /**
     * Get the 'limitValue' value. This is an interger value and will default to
     * DEFAULT_MAX_OBJECTS if it was not previously saved correctly.
     *
     * @return an integer representing the max number of deleted files to display.
     */
    public int getDeletedFilesLimit() {
        return deletedFilesLimit;
    }

    /**
     * Set the 'limitValue' for max number of deleted files to display.
     *
     * @param value an integer representing the max number of deleted files to display.
     */
    public void setDeletedFilesLimit(int value) {
        deletedFilesLimit = value;
        saveToStorage();
        DirectoryTreeTopComponent.getDefault().refreshContentTreeSafe();

    }

    /**
     * Load deleted file preferences from the settings file.
     */
    private void loadFromStorage() {
        Path settingsFile = Paths.get(PlatformUtil.getUserConfigDirectory(), SETTINGS_FILE); //NON-NLS
        if (settingsFile.toFile().exists()) {
            // Read the settings
            try (InputStream inputStream = Files.newInputStream(settingsFile)) {
                Properties props = new Properties();
                props.load(inputStream);
                String limitDeletedFilesValue = props.getProperty(KEY_LIMIT_DELETED_FILES);
                if (limitDeletedFilesValue != null) {
                    switch (limitDeletedFilesValue) {
                        case VALUE_TRUE:
                            limitDeletedFiles = true;
                            break;
                        case VALUE_FALSE:
                            limitDeletedFiles = false;
                            break;
                        default:
                            logger.log(Level.WARNING, String.format("Unexpected value '%s' for limit deleted files using value of true instead",
                                    limitDeletedFilesValue));
                            limitDeletedFiles = true;
                            break;
                    }
                }
                String limitValue = props.getProperty(KEY_LIMIT_VALUE);
                try {
                    if (limitValue != null) {
                        deletedFilesLimit = Integer.valueOf(limitValue);

                    }
                } catch (NumberFormatException ex) {
                    logger.log(Level.INFO, String.format("Unexpected value '%s' for limit, expected an integer using default of 10,001 instead",
                            limitValue));
                    deletedFilesLimit = DEFAULT_MAX_OBJECTS;
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error reading deletedFilesPreferences file", ex);
            }
        }
    }

    /**
     * Store deleted file preferences in the settings file.
     */
    private void saveToStorage() {
        Path settingsFile = Paths.get(PlatformUtil.getUserConfigDirectory(), SETTINGS_FILE); //NON-NLS
        Properties props = new Properties();
        props.setProperty(KEY_LIMIT_DELETED_FILES, (limitDeletedFiles ? VALUE_TRUE : VALUE_FALSE));
        props.setProperty(KEY_LIMIT_VALUE, String.valueOf(deletedFilesLimit));
        try (OutputStream fos = Files.newOutputStream(settingsFile)) {
            props.store(fos, ""); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error writing deletedFilesPreferences file", ex);
        }
    }
}
