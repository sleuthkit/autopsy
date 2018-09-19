/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Provides access to per-case module properties/settings.
 */
class PerCaseProperties {

    public static final String ENABLED = "enabled"; //NON-NLS

    private final Case theCase;

    PerCaseProperties(Case c) {
        this.theCase = c;
    }

    /**
     * Makes a new config file of the specified name. Do not include the
     * extension.
     *
     * @param moduleName - The name of the config file to make
     *
     * @return True if successfully created, false if already exists or an error
     *         is thrown.
     */
    public synchronized boolean makeConfigFile(String moduleName) {
        if (!configExists(moduleName)) {
            Path propPath = getPropertyPath(moduleName);
            Path parent = propPath.getParent();

            Properties props = new Properties();
            try {
                if (!Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                Files.createFile(propPath);

                try (OutputStream fos = Files.newOutputStream(propPath)) {
                    props.store(fos, "");
                }
            } catch (IOException e) {
                Logger.getLogger(PerCaseProperties.class.getName()).log(Level.WARNING, "Was not able to create a new properties file.", e); //NON-NLS
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Determines if a given properties file exists or not.
     *
     * @param moduleName - The name of the config file to evaluate
     *
     * @return true if the config exists, false otherwise.
     */
    public synchronized boolean configExists(String moduleName) {
        Path get = Paths.get(theCase.getModuleDirectory(), moduleName, theCase.getName() + ".properties"); //NON-NLS
        return Files.exists(get);
    }

    public synchronized boolean settingExists(String moduleName, String settingName) {
        if (!configExists(moduleName)) {
            return false;
        }
        try {
            Properties props = fetchProperties(moduleName);
            return (props.getProperty(settingName) != null);
        } catch (IOException e) {
            return false;
        }

    }

    /**
     * Returns the path of the given properties file.
     *
     * @param moduleName - The name of the config file to evaluate
     *
     * @return The path of the given config file. Returns null if the config
     *         file doesn't exist.
     */
    private synchronized Path getPropertyPath(String moduleName) {
        return Paths.get(theCase.getModuleDirectory(), moduleName, theCase.getName() + ".properties"); //NON-NLS
    }

    /**
     * Returns the given properties file's setting as specific by settingName.
     *
     * @param moduleName  - The name of the config file to read from.
     * @param settingName - The setting name to retrieve.
     *
     * @return - the value associated with the setting.
     *
     * @throws IOException
     */
    public synchronized String getConfigSetting(String moduleName, String settingName) {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.INFO, "File did not exist. Created file [" + moduleName + ".properties]"); //NON-NLS NON-NLS
        }

        try {
            Properties props = fetchProperties(moduleName);

            return props.getProperty(settingName);
        } catch (IOException e) {
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.WARNING, "Could not read config file [" + moduleName + "]", e); //NON-NLS
            return null;
        }

    }

    /**
     * Returns the given properties file's map of settings.
     *
     * @param moduleName - the name of the config file to read from.
     *
     * @return - the map of all key:value pairs representing the settings of the
     *         config.
     *
     * @throws IOException
     */
    public synchronized Map< String, String> getConfigSettings(String moduleName) {

        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.INFO, "File did not exist. Created file [" + moduleName + ".properties]"); //NON-NLS NON-NLS
        }
        try {
            Properties props = fetchProperties(moduleName);

            Set<String> keys = props.stringPropertyNames();
            Map<String, String> map = new HashMap<>();

            for (String s : keys) {
                map.put(s, props.getProperty(s));
            }

            return map;
        } catch (IOException e) {
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.WARNING, "Could not read config file [" + moduleName + "]", e); //NON-NLS
            return null;
        }
    }

    /**
     * Sets the given properties file to the given setting map.
     *
     * @param moduleName - The name of the module to be written to.
     * @param settings   - The mapping of all key:value pairs of settings to add
     *                   to the config.
     */
    public synchronized void setConfigSettings(String moduleName, Map<String, String> settings) {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.INFO, "File did not exist. Created file [" + moduleName + ".properties]"); //NON-NLS NON-NLS
        }
        try {
            Properties props = fetchProperties(moduleName);

            for (Map.Entry<String, String> kvp : settings.entrySet()) {
                props.setProperty(kvp.getKey(), kvp.getValue());
            }

            try (OutputStream fos = Files.newOutputStream(getPropertyPath(moduleName))) {
                props.store(fos, "Changed config settings(batch)"); //NON-NLS
            }
        }
        catch (ClosedByInterruptException e) {
            // not logging exception because this often happens when case is closed and stack does not help with debugging
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.WARNING, "Properties not saved because of interrupt"); //NON-NLS
        }
        catch (IOException e) {
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.WARNING, "Property file exists for [" + moduleName + "] at [" + getPropertyPath(moduleName) + "] but could not be loaded.", e); //NON-NLS NON-NLS NON-NLS
        }
    }

    /**
     * Sets the given properties file to the given settings.
     *
     * @param moduleName  - The name of the module to be written to.
     * @param settingName - The name of the setting to be modified.
     * @param settingVal  - the value to set the setting to.
     */
    public synchronized void setConfigSetting(String moduleName, String settingName, String settingVal) {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.INFO, "File did not exist. Created file [" + moduleName + ".properties]"); //NON-NLS NON-NLS
        }

        try {
            Properties props = fetchProperties(moduleName);

            props.setProperty(settingName, settingVal);

            try (OutputStream fos = Files.newOutputStream(getPropertyPath(moduleName))) {
                props.store(fos, "Changed config settings(single)"); //NON-NLS
            }
        }
        catch (ClosedByInterruptException e) {
            // not logging exception because this often happens when case is closed and stack does not help with debugging
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.WARNING, "Property {0} not saved because of interrupt", settingName); //NON-NLS
        }
        catch (IOException e) {
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.WARNING, "Property file exists for [" + moduleName + "] at [" + getPropertyPath(moduleName) + "] but could not be loaded.", e); //NON-NLS NON-NLS NON-NLS
        }
    }

    /**
     * Removes the given key from the given properties file.
     *
     * @param moduleName - The name of the properties file to be modified.
     * @param key        - the name of the key to remove.
     */
    public synchronized void removeProperty(String moduleName, String key) {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.INFO, "File did not exist. Created file [" + moduleName + ".properties]"); //NON-NLS NON-NLS
        }

        try {
            if (getConfigSetting(moduleName, key) != null) {
                Properties props = fetchProperties(moduleName);

                props.remove(key);
                try (OutputStream fos = Files.newOutputStream(getPropertyPath(moduleName))) {
                    props.store(fos, "Removed " + key); //NON-NLS
                }
            }
        } catch (IOException e) {
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.WARNING, "Could not remove property from file, file not found", e); //NON-NLS
        }
    }

    /**
     * Returns the properties file as specified by moduleName.
     *
     * @param moduleName
     *
     * @return Properties file as specified by moduleName.
     *
     * @throws IOException
     */
    private Properties fetchProperties(String moduleName) throws IOException {
        if (!configExists(moduleName)) {
            makeConfigFile(moduleName);
            Logger.getLogger(PerCaseProperties.class.getName()).log(Level.INFO, "File did not exist. Created file [" + moduleName + ".properties]"); //NON-NLS NON-NLS
        }
        Properties props;
        try (InputStream inputStream = Files.newInputStream(getPropertyPath(moduleName))) {
            props = new Properties();
            props.load(inputStream);
        }
        return props;
    }
}
