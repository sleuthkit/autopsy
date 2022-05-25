/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

/**
 * Provides utility methods for creating, updating, and deleting Java properties
 * files with paths such as %USERDIR%/Config/[module name].properties, where
 * "module name" is intended to be a module name and the properties file is
 * intended to be a settings file for the module.
 *
 * Very coarse-grained thread safety is provided by these utilities if all
 * modules confine themselves to their use when manipulating their settings
 * files, with the consequence of serializing all such operations across the
 * entire application.
 * 
 * TODO (JIRA-5964): The error handling in this class is not consistent with
 * Autopsy error handling policy.
 */
public class ModuleSettings {

    private final static Logger logger = Logger.getLogger(ModuleSettings.class.getName());
    private final static String MODULE_DIR_PATH = PlatformUtil.getUserConfigDirectory();
    private final static String SETTINGS_FILE_EXT = ".properties";

    /*
     * These SHOULD NOT be public and DO NOT belong in this file. They are being
     * retained only for the sake of backwards compatibility.
     */
    public static final String DEFAULT_CONTEXT = "GeneralContext"; //NON-NLS
    public static final String MAIN_SETTINGS = "Case"; //NON-NLS
    public static final String CURRENT_CASE_TYPE = "Current_Case_Type"; //NON-NLS

    /**
     * Makes a new settings file for a module.
     *
     * @param moduleName The module name.
     *
     * @return True if the settings file was created, false if the file already
     *         existed or could not be created.
     */
    public static synchronized boolean makeConfigFile(String moduleName) {
        if (!configExists(moduleName)) {
            File propPath = new File(getSettingsFilePath(moduleName));
            File parent = new File(propPath.getParent());
            if (!parent.exists()) {
                parent.mkdirs();
            }
            
            Properties props = new Properties();
            try {
                propPath.createNewFile();
                try (FileOutputStream fos = new FileOutputStream(propPath)) {
                    props.store(fos, "Created module settings file");
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Failed to create module settings file at %s)", propPath), ex); //NON-NLS
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Indicates whether or not a settings file exists for a given module.
     *
     * @param moduleName The module name.
     *
     * @return True or false.
     */
    public static synchronized boolean configExists(String moduleName) {
        return new File(getSettingsFilePath(moduleName)).exists();
    }

    /**
     * Determines whether or not a given setting exists in the settings file for
     * a module.
     *
     * @param moduleName  The module name.
     * @param settingName The name of the setting (property).
     *
     * @return True if the setting file exists, can be read, and contains the
     *         specified setting (property), false otherwise.
     */
    public static synchronized boolean settingExists(String moduleName, String settingName) {
        if (!configExists(moduleName)) {
            return false;
        }
        
        try {
            Properties props = fetchProperties(moduleName);
            return (props.getProperty(settingName) != null);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Failed to get %s setting from module settings file at %s)", settingName, getSettingsFilePath(moduleName)), ex); //NON-NLS
            return false;
        }
    }

    /**
     * Constructs a settings file path for a given module.
     *
     * @param moduleName The module name.
     *
     * @return The settings file path as a string.
     */
    static String getSettingsFilePath(String moduleName) {
        return Paths.get(MODULE_DIR_PATH, moduleName + SETTINGS_FILE_EXT).toString();
    }

    /**
     * Gets the value of a setting (property) from a module settings file.
     *
     * NOTE: If the settings file does not already exist, it is created.
     *
     * @param moduleName  The module name.
     * @param settingName The setting name.
     *
     * @return The value of the setting or null if the file cannot be read or
     *         the setting is not found.
     */
    public static synchronized String getConfigSetting(String moduleName, String settingName) {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
        }
        
        try {
            Properties props = fetchProperties(moduleName);
            return props.getProperty(settingName);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Failed to get %s setting from module settings file at %s)", settingName, getSettingsFilePath(moduleName)), ex); //NON-NLS
            return null;
        }
    }

    /**
     * Gets the settings (properties) from a module settings file.
     *
     * NOTE: If the settings file does not already exist, it is created.
     *
     * @param moduleName The module name.
     *
     * @return A mapping of setting names to setting values from the settings
     *         file, may be empty.
     */
    public static synchronized Map<String, String> getConfigSettings(String moduleName) {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
        }
        
        try {
            Properties props = fetchProperties(moduleName);
            Set<String> keys = props.stringPropertyNames();
            Map<String, String> map = new HashMap<>();
            for (String s : keys) {
                map.put(s, props.getProperty(s));
            }
            return map;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Failed to get settings from module settings file at %s)", getSettingsFilePath(moduleName)), ex); //NON-NLS
            return null;
        }
    }

    /**
     * Adds a mapping of setting name to setting values to a module settings
     * file.
     *
     * NOTE: If the settings file does not already exist, it is created.
     *
     * @param moduleName The module name.
     * @param settings   The module settings.
     */
    public static synchronized void setConfigSettings(String moduleName, Map<String, String> settings) {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
        }
        
        try {
            Properties props = fetchProperties(moduleName);
            for (Map.Entry<String, String> kvp : settings.entrySet()) {
                props.setProperty(kvp.getKey(), kvp.getValue());
            }
            
            File path = new File(getSettingsFilePath(moduleName));
            try (FileOutputStream fos = new FileOutputStream(path)) {
                props.store(fos, "Set settings (batch)"); //NON-NLS
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error writing to module settings file at %s)", getSettingsFilePath(moduleName)), ex); //NON-NLS
        }
    }

    /**
     * Sets the value of a setting (property) in a module settings file.
     *
     * NOTE: If the settings file does not already exist, it is created.
     *
     * @param moduleName  The module name.
     * @param settingName The setting name.
     * @param settingVal  The setting value.
     */
    public static synchronized void setConfigSetting(String moduleName, String settingName, String settingVal) {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
        }
        try {
            Properties props = fetchProperties(moduleName);
            props.setProperty(settingName, settingVal);
            File path = new File(getSettingsFilePath(moduleName));
            try (FileOutputStream fos = new FileOutputStream(path)) {
                props.store(fos, "Set " + settingName); //NON-NLS
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error writing %s setting to module settings file at %s)", settingName, getSettingsFilePath(moduleName)), ex); //NON-NLS
        }
    }

    /**
     * Removes a setting (property) in a module settings file.
     *
     * @param moduleName  The module name.
     * @param settingName The setting name.
     */
    public static synchronized void removeProperty(String moduleName, String settingName) {
        try {
            if (getConfigSetting(moduleName, settingName) != null) {
                Properties props = fetchProperties(moduleName);
                props.remove(settingName);
                File path = new File(getSettingsFilePath(moduleName));
                try (FileOutputStream fos = new FileOutputStream(path)) {
                    props.store(fos, "Removed " + settingName); //NON-NLS
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error removing %s setting from module settings file at %s)", settingName, getSettingsFilePath(moduleName)), ex); //NON-NLS
        }
    }

    /**
     * Gets the contents of a module settings file as a Properties object.
     *
     * @param moduleName The module name.
     *
     * @return The Properties object.
     *
     * @throws IOException If there is a problem reading the settings file.
     */
    private static synchronized Properties fetchProperties(String moduleName) throws IOException {
        Properties props;
        try (InputStream inputStream = new FileInputStream(getSettingsFilePath(moduleName))) {
            props = new Properties();
            props.load(inputStream);
        }
        return props;
    }

    /**
     * Gets a File object for a module settings (properties) file.
     *
     * @param moduleName The module name.
     *
     * @return The File object or null if the file does not exist.
     */
    public static synchronized File getPropertyFile(String moduleName) {
        File configFile = null;
        if (configExists(moduleName)) {
            configFile = new File(getSettingsFilePath(moduleName));
        }
        return configFile;
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ModuleSettings() {
    }

}
