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
package org.sleuthkit.autopsy.casemodule;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Properties;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;

/**
 * Read and update case preference file values.
 */
public final class CasePreferences {

    private static final String SETTINGS_FILE = "CasePreferences.properties"; //NON-NLS
    private static final String KEY_GROUP_BY_DATA_SOURCE = "groupByDataSource"; //NON-NLS
    private static final String VALUE_TRUE = "true"; //NON-NLS
    private static final String VALUE_FALSE = "false"; //NON-NLS

    private static final Logger logger = Logger.getLogger(CasePreferences.class.getName());

    private static Boolean groupItemsInTreeByDataSource = false;

    /**
     * Prevent instantiation.
     */
    private CasePreferences() {
    }

    static {
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            if (evt.getNewValue() != null) {
                loadFromStorage((Case) evt.getNewValue());
            } else {
                saveToStorage((Case) evt.getOldValue());
                clear();
            }
        });
        try {
            loadFromStorage(Case.getCurrentCaseThrows());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "No current case open.", ex);
        }
    }

    /**
     * Get the 'groupItemsInTreeByDataSource' value. This can be true, false, or
     * null.
     *
     * @return The value.
     */
    public static Boolean getGroupItemsInTreeByDataSource() {
        return groupItemsInTreeByDataSource;
    }

    /**
     * Set the 'groupItemsInTreeByDataSource' value to true or false.
     *
     * @param value The value to use for the value change.
     */
    public static void setGroupItemsInTreeByDataSource(boolean value) {
        groupItemsInTreeByDataSource = value;
        if (Case.isCaseOpen()) {
            DirectoryTreeTopComponent.getDefault().refreshContentTreeSafe();
        }
    }

    /**
     * Load case preferences from the settings file.
     */
    private static void loadFromStorage(Case currentCase) {
        Path settingsFile = Paths.get(currentCase.getConfigDirectory(), SETTINGS_FILE); //NON-NLS
        if (settingsFile.toFile().exists()) {
            // Read the settings
            try (InputStream inputStream = Files.newInputStream(settingsFile)) {
                Properties props = new Properties();
                props.load(inputStream);
                String groupByDataSourceValue = props.getProperty(KEY_GROUP_BY_DATA_SOURCE);
                if (groupByDataSourceValue != null) {
                    switch (groupByDataSourceValue) {
                        case VALUE_TRUE:
                            groupItemsInTreeByDataSource = true;
                            break;
                        case VALUE_FALSE:
                            groupItemsInTreeByDataSource = false;
                            break;
                        default:
                            logger.log(Level.WARNING, String.format("Unexpected value '%s' for key '%s'. Using 'null' instead.",
                                    groupByDataSourceValue, KEY_GROUP_BY_DATA_SOURCE));
                            groupItemsInTreeByDataSource = false;
                            break;
                    }
                } else {
                    groupItemsInTreeByDataSource = false;
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error reading settings file", ex);
            }
        }
    }

    /**
     * Reset all values to their default states.
     */
    private static void clear() {
        groupItemsInTreeByDataSource = false;
    }

    /**
     * Store case preferences in the settings file.
     */
    private static void saveToStorage(Case currentCase) {
        Path settingsFile = Paths.get(currentCase.getConfigDirectory(), SETTINGS_FILE); //NON-NLS
        Properties props = new Properties();
        if (groupItemsInTreeByDataSource != null) {
            props.setProperty(KEY_GROUP_BY_DATA_SOURCE, (groupItemsInTreeByDataSource ? VALUE_TRUE : VALUE_FALSE));
        }

        try (OutputStream fos = Files.newOutputStream(settingsFile)) {
            props.store(fos, ""); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error writing settings file", ex);
        }
    }
}
