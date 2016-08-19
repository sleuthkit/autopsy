/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TreeMap;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Settings for the export of files based on user-defined export rules.
 */
final class FileExportSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_MASTER_CATALOG_NAME = "interim";
    private static final String DEFAULT_EXPORT_COMPLETED_FILE_NAME = "EXTRACTION_FINISHED";
    private static final String DEFAULT_RULES_EVALUATED_NAME = "SORTER_FINISHED";
    private static final String SETTINGS_DIRECTORY = org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration.FILE_EXPORTER_FOLDER;
    private static final String SETTINGS_FILE_NAME = org.sleuthkit.autopsy.experimental.configuration.SharedConfiguration.FILE_EXPORTER_SETTINGS_FILE;
    private TreeMap<String, FileExportRuleSet> ruleSets;
    private String filesRootDirectory;
    private String reportsRootDirectory;
    private String masterCatalogName;
    private String exportCompletedFlagFileName;
    private String rulesEvaluatedFlagFileName;
    private boolean enabled;

    /**
     * Saves the file export settings to secondary storage. Existing settings
     * are overwritten.
     *
     * @param settings The settings to save.
     *
     * @throws
     * org.sleuthkit.autopsy.autoingest.FileExportSettings.PersistenceException
     */
    static synchronized void store(FileExportSettings settings) throws PersistenceException {
        Path folderPath = Paths.get(PlatformUtil.getUserConfigDirectory(), SETTINGS_DIRECTORY);
        Path filePath = Paths.get(folderPath.toString(), SETTINGS_FILE_NAME);
        try {
            Files.createDirectories(folderPath);
            try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath.toString()))) {
                out.writeObject(settings);
            }
        } catch (IOException ex) {
            throw new PersistenceException(String.format("Failed to write settings to %s", filePath), ex);
        }
    }

    /**
     * Reads the file export settings from secondary storage.
     *
     * @return The settings.
     *
     * @throws
     * org.sleuthkit.autopsy.autoingest.FileExportSettings.PersistenceException
     */
    static synchronized FileExportSettings load() throws PersistenceException {
        Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), SETTINGS_DIRECTORY, SETTINGS_FILE_NAME);
        try {
            // if the File Exporter settings file doesn't exist, return default settings
            if (!filePath.toFile().exists()) {
                return new FileExportSettings();
            }
            
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePath.toString()))) {
                FileExportSettings settings = (FileExportSettings) in.readObject();
                return settings;
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new PersistenceException(String.format("Failed to read settings from %s", filePath), ex);

        }
    }

    /**
     * Constructs an instance of the settings for the export of files based on
     * user-defined export rules.
     */
    FileExportSettings() {
        enabled = false;
        ruleSets = new TreeMap<>();
        filesRootDirectory = null;
        reportsRootDirectory = null;
        masterCatalogName = DEFAULT_MASTER_CATALOG_NAME;
        exportCompletedFlagFileName = DEFAULT_EXPORT_COMPLETED_FILE_NAME;
        rulesEvaluatedFlagFileName = DEFAULT_RULES_EVALUATED_NAME;
    }

    /**
     * Sets file export enabled state.
     *
     * @param state The state to set.
     */
    void setFileExportEnabledState(boolean state) {
        this.enabled = state;
    }

    /**
     * Gets file export enabled state.
     *
     * @return The enabled state.
     */
    boolean getFileExportEnabledState() {
        return this.enabled;
    }
    
        
    /**
     * Sets the file export rules.
     *
     * @param ruleSets A map of rules with name keys, sorted by name.
     *
     */
    void setRuleSets(TreeMap<String, FileExportRuleSet> ruleSets) {
        this.ruleSets = ruleSets;
    }

    /**
     * Gets the file export rules.
     *
     * @return A map of rules with name keys, sorted by name.
     */
    TreeMap<String, FileExportRuleSet> getRuleSets() {
        return this.ruleSets;
    }

    /**
     * Sets the root file export directory.
     *
     * @param filesRootDirectory The path of the root directory for files
     *                           export.
     */
    void setFilesRootDirectory(Path filesRootDirectory) {
        this.filesRootDirectory = filesRootDirectory.toString();
    }

    /**
     * Gets the root file output directory.
     *
     * @return The path of the root directory for files export, may be null.
     */
    Path getFilesRootDirectory() {
        if (null != filesRootDirectory) {
            return Paths.get(this.filesRootDirectory);
        } else {
            return null;
        }
    }

    /**
     * Sets the root reports (file catalogs) directory.
     *
     * @param reportsRootDirectory The path of the root directory for reports
     *                             (file catalogs).
     */
    void setReportsRootDirectory(Path reportsRootDirectory) {
        this.reportsRootDirectory = reportsRootDirectory.toString();
    }

    /**
     * Gets the root file output directory.
     *
     * @return The path of the root directory for reports (file catalogs), may
     *         be null.
     */
    Path getReportsRootDirectory() {
        if (null != this.reportsRootDirectory) {
            return Paths.get(this.reportsRootDirectory);
        } else {
            return null;
        }
    }

    /**
     * Sets the name of the master catalog of exported files.
     *
     * @param name The catalog name.
     */
    void setMasterCatalogName(String name) {
        this.masterCatalogName = name;
    }

    /**
     * Gets the name of the master catalog of exported files.
     *
     * @return The catalog name.
     */
    String getMasterCatalogName() {
        return this.masterCatalogName;
    }

    /**
     * Sets the name of the file written to indicate file export is completed.
     *
     * @param fileName The file name.
     */
    void setExportCompletedFlagFileName(String fileName) {
        this.exportCompletedFlagFileName = fileName;
    }

    /**
     * Gets the name of the file written to indicate file export is completed.
     *
     * @return The file name.
     */
    String getExportCompletedFlagFileName() {
        return this.exportCompletedFlagFileName;
    }

    /**
     * Sets the name of the file written to indicate file export rule evaluation
     * is completed.
     *
     * @param fileName The file name.
     */
    void setRulesEvaluatedFlagFileName(String fileName) {
        this.rulesEvaluatedFlagFileName = fileName;
    }

    /**
     * Gets the name of the file written to indicate file export rule evaluation
     * is completed.
     *
     * @return The file name.
     */
    String getRulesEvaluatedFlagFileName() {
        return rulesEvaluatedFlagFileName;
    }

    /**
     * Exception thrown if there is a problem storing or loading the settings.
     */
    public final static class PersistenceException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception.
         *
         * @param message The exception message.
         */
        private PersistenceException(String message) {
            super(message);
        }

        /**
         * Constructs an exception.
         *
         * @param message The exception message.
         * @param cause   The exception cause.
         */
        private PersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
