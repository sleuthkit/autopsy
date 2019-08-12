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
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Utility class responsible for managing serialization and deserialization of
 * all of the settings that make up a reporting configuration in an atomic,
 * thread safe way.
 */
final class ReportingConfigLoader {

    private static final String REPORT_CONFIG_FOLDER = "ReportingConfigs"; //NON-NLS
    private static final String REPORT_CONFIG_FOLDER_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), ReportingConfigLoader.REPORT_CONFIG_FOLDER).toAbsolutePath().toString();
    private static final String REPORT_CONFIG_FILE_EXTENSION = ".settings";

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

        // construct the file path
        Path reportFilePath = Paths.get(ReportingConfigLoader.REPORT_CONFIG_FOLDER_PATH, configName + REPORT_CONFIG_FILE_EXTENSION);
        File reportFile = reportFilePath.toFile();

        // Return null if a reporting configuration for the given name does not exist.
        if (!reportFile.exists()) {
            return null;
        }

        if (!reportFile.isFile()|| !reportFile.canRead()) {
            throw new ReportConfigException("Unable to read reporting configuration file " + reportFilePath.toString());
        }

        // read in the configuration
        ReportingConfig config = null;
        try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(reportFilePath.toString()))) {
            config = (ReportingConfig) in.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            throw new ReportConfigException("Unable to read reporting configuration " + reportFilePath.toString(), ex);
        }

        return config;
    }

    /**
     * Serialize all of the settings that make up a reporting configuration in
     * an atomic, thread safe way.
     *
     * @param config ReportingConfig object to serialize to disk
     * @throws ReportConfigException if an error occurred while saving the
     * configuration
     */
    static synchronized void saveConfig(ReportingConfig config) throws ReportConfigException {

        // construct the configuration directory path
        Path pathToConfigDir = Paths.get(ReportingConfigLoader.REPORT_CONFIG_FOLDER_PATH);

        // create configuration directory 
        try {
            Files.createDirectories(pathToConfigDir); // does not throw if directory already exists
        } catch (IOException | SecurityException ex) {
            throw new ReportConfigException("Failed to create reporting configuration directory " + pathToConfigDir.toString(), ex);
        }
        
        // save the configuration
        String filePath = pathToConfigDir.toString() + File.separator + config.getName() + REPORT_CONFIG_FILE_EXTENSION;
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath))) {
            out.writeObject(config);
        } catch (IOException ex) {
            throw new ReportConfigException("Unable to save reporting configuration " + filePath, ex);
        }
    }

}
