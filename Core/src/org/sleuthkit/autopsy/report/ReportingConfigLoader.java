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
package org.sleuthkit.autopsy.report;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Utility class responsible for managing serialization and deserialization of
 * all of the settings that make up a reporting configuration in an atomic,
 * thread safe way.
 */
final class ReportingConfigLoader {

    private static final Logger logger = Logger.getLogger(ReportingConfigLoader.class.getName());
    private static final String REPORT_CONFIG_FOLDER = "ReportingConfigs"; //NON-NLS
    private static final String REPORT_CONFIG_FOLDER_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), ReportingConfigLoader.REPORT_CONFIG_FOLDER).toAbsolutePath().toString();
    private static final String REPORT_SETTINGS_FILE_EXTENSION = ".settings";
    private static final String TABLE_REPORT_CONFIG_FILE = "TableReportSettings.settings";
    private static final String FILE_REPORT_CONFIG_FILE = "FileReportSettings.settings";
    private static final String MODULE_CONFIG_FILE = "ModuleConfigs.settings";

    /**
     * Deserialize all of the settings that make up a reporting configuration in
     * an atomic, thread safe way.
     *
     * @param configName Name of the reporting configuration
     * @return ReportingConfig object if a persisted configuration exists, null
     * otherwise
     * @throws ReportConfigException if an error occurred while reading the
     * configuration
     */
    static synchronized ReportingConfig loadConfig(String configName) throws ReportConfigException {

        // construct the configuration directory path
        Path reportDirPath = Paths.get(ReportingConfigLoader.REPORT_CONFIG_FOLDER_PATH, configName);
        File reportDirectory = reportDirPath.toFile();

        // Return null if a reporting configuration for the given name does not exist.
        if (!reportDirectory.exists()) {
            return null;
        }

        if (!reportDirectory.isDirectory() || !reportDirectory.canRead()) {
            throw new ReportConfigException("Unable to read reporting configuration directory " + reportDirPath.toString());
        }

        // read in the configuration
        ReportingConfig config = new ReportingConfig(configName);
        
        // read table report settings
        String filePath = reportDirPath.toString() + File.separator + TABLE_REPORT_CONFIG_FILE;
        try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePath))) {
            config.setTableReportSettings((TableReportSettings) in.readObject());
        } catch (IOException | ClassNotFoundException ex) {
            throw new ReportConfigException("Unable to read table report settings " + filePath, ex);
        }
        
        // read file report settings
        filePath = reportDirPath.toString() + File.separator + FILE_REPORT_CONFIG_FILE;
        try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePath))) {
            config.setFileReportSettings((FileReportSettings) in.readObject());
        } catch (IOException | ClassNotFoundException ex) {
            throw new ReportConfigException("Unable to read file report settings " + filePath, ex);
        }
        
        // read map of module configuration objects
        Map<String, ReportModuleConfig> moduleConfigs = null;
        filePath = reportDirPath.toString() + File.separator + MODULE_CONFIG_FILE;
        try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePath))) {
            moduleConfigs = (Map<String, ReportModuleConfig>) in.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            throw new ReportConfigException("Unable to read module configurations map " + filePath, ex);
        }
        
        if (moduleConfigs == null || moduleConfigs.isEmpty()) {
            return config;
        }
        
        // read each ReportModuleSettings object individually
        for (Iterator<Entry<String, ReportModuleConfig>> iterator = moduleConfigs.entrySet().iterator(); iterator.hasNext();) {
            ReportModuleConfig moduleConfig = iterator.next().getValue();
            filePath = reportDirPath.toString() + File.separator + moduleConfig.getModuleClassName() + REPORT_SETTINGS_FILE_EXTENSION;
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePath))) {
                moduleConfig.setModuleSettings((ReportModuleSettings) in.readObject());
            } catch (IOException | ClassNotFoundException ex) {
                /* NOTE: we do not want to re-throw the exception because we do not 
                want a single error while reading in a (3rd party) report module 
                to prevent us from reading the entire reporting configuration.*/
                logger.log(Level.SEVERE, "Unable to read module settings " + filePath, ex);
                iterator.remove();
            }
        }
        
        config.setModuleConfigs(moduleConfigs);
        
        return config;
    }

    /**
     * Serialize all of the settings that make up a reporting configuration in
     * an atomic, thread safe way.
     *
     * @param reportConfig ReportingConfig object to serialize to disk
     * @throws ReportConfigException if an error occurred while saving the
     * configuration
     */
    static synchronized void saveConfig(ReportingConfig reportConfig) throws ReportConfigException {

        if (reportConfig == null) {
            throw new ReportConfigException("Reporting configuration is NULL");
        }
        
        // construct the configuration directory path
        Path pathToConfigDir = Paths.get(ReportingConfigLoader.REPORT_CONFIG_FOLDER_PATH, reportConfig.getName());

        // create configuration directory 
        try {
            Files.createDirectories(pathToConfigDir); // does not throw if directory already exists
        } catch (IOException | SecurityException ex) {
            throw new ReportConfigException("Failed to create reporting configuration directory " + pathToConfigDir.toString(), ex);
        }

        // save table report settings
        String filePath = pathToConfigDir.toString() + File.separator + TABLE_REPORT_CONFIG_FILE;
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath))) {
            out.writeObject(reportConfig.getTableReportSettings());
        } catch (IOException ex) {
            throw new ReportConfigException("Unable to save table report configuration " + filePath, ex);
        }

        // save file report settings
        filePath = pathToConfigDir.toString() + File.separator + FILE_REPORT_CONFIG_FILE;
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath))) {
            out.writeObject(reportConfig.getFileReportSettings());
        } catch (IOException ex) {
            throw new ReportConfigException("Unable to save file report configuration " + filePath, ex);
        }

        // save map of module configuration objects
        filePath = pathToConfigDir.toString() + File.separator + MODULE_CONFIG_FILE;        
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath))) {
            out.writeObject(reportConfig.getModuleConfigs());
        } catch (IOException ex) {
            throw new ReportConfigException("Unable to save module configurations map " + filePath, ex);
        }

        // save each ReportModuleSettings object individually
        /* NOTE: This is done to protect us from errors in reading/writing 3rd 
        party report module settings. If we were to serialize the entire ReportingConfig 
        object, then a single error while reading in a 3rd party report module 
        would prevent us from reading the entire reporting configuration.*/
        if (reportConfig.getModuleConfigs() == null) {
            return;
        }
        for (ReportModuleConfig moduleConfig : reportConfig.getModuleConfigs().values()) {
            ReportModuleSettings settings = moduleConfig.getModuleSettings();
            filePath = pathToConfigDir.toString() + File.separator + moduleConfig.getModuleClassName() + REPORT_SETTINGS_FILE_EXTENSION;
            try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath))) {
                out.writeObject(settings);
            } catch (IOException ex) {
                throw new ReportConfigException("Unable to save module settings " + filePath, ex);
            }
        }
    }

}
