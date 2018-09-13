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
    public static final String GROUP_ITEMS_IN_TREE_BY_DATASOURCE = "GroupItemsInTreeByDataSource"; //NON-NLS
    
    private static final Logger logger = Logger.getLogger(CasePreferences.class.getName());
    
    private static Boolean groupItemsInTreeByDataSource = null;
    
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
    
    public static Boolean getGroupItemsInTreeByDataSource() {
        return groupItemsInTreeByDataSource;
    }
    
    public static void setGroupItemsInTreeByDataSource(boolean value) {
        groupItemsInTreeByDataSource = value;
        DirectoryTreeTopComponent.getDefault().refreshContentTreeSafe();
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
                if (props.getProperty("groupByDataSource", "false").equals("true")) {
                    groupItemsInTreeByDataSource = true;
                } else {
                    groupItemsInTreeByDataSource = false;
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error reading settings file", ex);
            }
        }
    }
    
    private static void clear() {
        groupItemsInTreeByDataSource = null;
    }

    /**
     * Store case preferences in the settings file.
     */
    private static void saveToStorage(Case currentCase) {
        Path settingsFile = Paths.get(currentCase.getConfigDirectory(), SETTINGS_FILE); //NON-NLS
        Properties props = new Properties();
        if (groupItemsInTreeByDataSource != null) {
            props.setProperty("groupByDataSource", (groupItemsInTreeByDataSource ? "true" : "false"));
        }

        try (OutputStream fos = Files.newOutputStream(settingsFile)) {
            props.store(fos, ""); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error writing settings file", ex);
        }
    }
}
