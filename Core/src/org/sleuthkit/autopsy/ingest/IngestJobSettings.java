/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.python.util.PythonObjectInputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager;

/**
 * Encapsulates the ingest job settings for a particular execution context.
 * Examples of execution contexts include the add data source wizard and the run
 * ingest modules dialog. Different execution contexts may have different ingest
 * job settings.
 */
public class IngestJobSettings {

    private static final String ENABLED_MODULES_KEY = "Enabled_Ingest_Modules"; //NON-NLS
    private static final String DISABLED_MODULES_KEY = "Disabled_Ingest_Modules"; //NON-NLS
    private static final String LAST_FILE_INGEST_FILTER_KEY = "Last File Ingest Filter Used";
    private static final String MODULE_SETTINGS_FOLDER = "IngestModuleSettings"; //NON-NLS
    private static final String MODULE_SETTINGS_FOLDER_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), IngestJobSettings.MODULE_SETTINGS_FOLDER).toAbsolutePath().toString();
    private static final String MODULE_SETTINGS_FILE_EXT = ".settings"; //NON-NLS
    private static final Logger LOGGER = Logger.getLogger(IngestJobSettings.class.getName());
    private static FilesSet ALL_FILES_INGEST_FILTER = new FilesSet("All Files", "All Files", false, true, Collections.emptyMap()); //NON-NLS
    private static FilesSet ALL_AND_UNALLOC_FILES_INGEST_FILTER = new FilesSet("All Files and Unallocated Space", "All Files and Unallocated Space", false, false, Collections.emptyMap());  //NON-NLS
    private FilesSet fileIngestFilter;
    private final String executionContext;
    private final IngestType ingestType;
    private String moduleSettingsFolderPath;
    private static final CharSequence pythonModuleSettingsPrefixCS = "org.python.proxies.".subSequence(0, "org.python.proxies.".length() - 1); //NON-NLS
    private final List<IngestModuleTemplate> moduleTemplates;
    private final List<String> warnings;

    /**
     * Gets the current FileIngestFilter saved in settings which is represented
     * by a FilesSet
     *
     * @return FilesSet which represents the FileIngestFilter
     */
    protected FilesSet getFileIngestFilter() {
        return fileIngestFilter;
    }

    /**
     * Sets the FileIngestFilter which is currently being used by ingest.
     *
     * @param fileIngestFilter the FilesSet which represents the
     *                         FileIngestFilter
     */
    protected void setFileIngestFilter(FilesSet fileIngestFilter) {
        this.fileIngestFilter = fileIngestFilter;
    }

    /**
     * Get a list of default FileIngestFilters.
     *
     * @return a list of FilesSets which cover default options.
     */
    public static List<FilesSet> getStandardFileIngestFilters() {
        return Arrays.asList(ALL_AND_UNALLOC_FILES_INGEST_FILTER, ALL_FILES_INGEST_FILTER);
    }

    /**
     * The type of ingest modules to run.
     */
    public enum IngestType {

        /**
         * Run both data source level and file-level ingest modules.
         */
        ALL_MODULES,
        /**
         * Run only data source level ingest modules.
         */
        DATA_SOURCE_ONLY,
        /**
         * Run only file level ingest modules.
         */
        FILES_ONLY
    }

    /**
     * Constructs an ingest job settings object for a given execution context.
     * Examples of execution contexts include the add data source wizard and the
     * run ingest modules dialog. Different execution conterxts may have
     * different ingest job settings.
     *
     * @param executionContext The ingest execution context identifier.
     */
    public IngestJobSettings(String executionContext) {
        this.executionContext = executionContext;
        this.ingestType = IngestType.ALL_MODULES;
        this.moduleTemplates = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.createSavedModuleSettingsFolder();
        this.load();
    }

    /**
     * Constructs an ingest job settings object for a given context. Examples of
     * execution contexts include the add data source wizard and the run ingest
     * modules dialog. Different execution conterxts may have different ingest
     * job settings.
     *
     * @param context    The context identifier string.
     * @param ingestType The type of modules ingest is running.
     */
    public IngestJobSettings(String context, IngestType ingestType) {
        this.ingestType = ingestType;

        if (this.ingestType.equals(IngestType.ALL_MODULES)) {
            this.executionContext = context;
        } else {
            this.executionContext = context + "." + this.ingestType.name();
        }

        this.moduleTemplates = new ArrayList<>();

        this.warnings = new ArrayList<>();
        this.createSavedModuleSettingsFolder();
        this.load();
    }

    /**
     * Saves these ingest job settings.
     */
    public void save() {
        this.store();
    }

    /**
     * Gets the ingest execution context identifier. Examples of execution
     * contexts include the add data source wizard and the run ingest modules
     * dialog. Different execution conterxts may have different ingest job
     * settings.
     *
     * @return The execution context identifier.
     */
    String getExecutionContext() {
        return this.executionContext;
    }

    /**
     * Gets and clears any accumulated warnings associated with these ingest job
     * settings.
     *
     * @return A list of warning messages, possibly empty.
     */
    public List<String> getWarnings() {
        List<String> warningMessages = new ArrayList<>(this.warnings);
        this.warnings.clear();
        return warningMessages;
    }

    /**
     * Gets the ingest module templates part of these ingest job settings.
     *
     * @return The list of ingest module templates.
     */
    List<IngestModuleTemplate> getIngestModuleTemplates() {
        return Collections.unmodifiableList(this.moduleTemplates);
    }

    /**
     * Gets the enabled ingest module templates part of these ingest job
     * settings.
     *
     * @return The list of enabled ingest module templates.
     */
    List<IngestModuleTemplate> getEnabledIngestModuleTemplates() {
        List<IngestModuleTemplate> enabledModuleTemplates = new ArrayList<>();
        for (IngestModuleTemplate moduleTemplate : this.moduleTemplates) {
            if (moduleTemplate.isEnabled()) {
                enabledModuleTemplates.add(moduleTemplate);
            }
        }
        return enabledModuleTemplates;
    }

    /**
     * Sets the ingest module templates part of these ingest job settings.
     *
     * @param moduleTemplates The ingest module templates.
     */
    void setIngestModuleTemplates(List<IngestModuleTemplate> moduleTemplates) {
        this.moduleTemplates.clear();
        this.moduleTemplates.addAll(moduleTemplates);
    }

    /**
     * If unallocated space should be processed Gets the the opposite of the
     * File Ingest Filter's skip unallocated space flag. So that the existing
     * logic in PhotoRec Carver and any other modules that may use this will
     * continue to work without modification.
     *
     * @return True for process unallocated space or false for skip unallocated
     *         space.
     */
    boolean getProcessUnallocatedSpace() {
        boolean processUnallocated = true;
        if (!Objects.isNull(this.fileIngestFilter)) {
            processUnallocated = (this.fileIngestFilter.getSkipUnallocatedSpace() == false);
        }
        return processUnallocated;
    }

    /**
     * Returns the path to the ingest module settings folder.
     *
     * @return path to the module settings folder
     */
    public Path getSavedModuleSettingsFolder() {
        return Paths.get(IngestJobSettings.MODULE_SETTINGS_FOLDER_PATH, executionContext);
    }

    /**
     * Creates the folder for saving the individual ingest module settings part
     * of these ingest job settings.
     */
    private void createSavedModuleSettingsFolder() {
        try {
            Path folder = getSavedModuleSettingsFolder();
            Files.createDirectories(folder);
            this.moduleSettingsFolderPath = folder.toAbsolutePath().toString();
        } catch (IOException | SecurityException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create ingest module settings directory " + this.moduleSettingsFolderPath, ex); //NON-NLS
            this.warnings.add(NbBundle.getMessage(IngestJobSettings.class, "IngestJobSettings.createModuleSettingsFolder.warning")); //NON-NLS
        }
    }

    /**
     * Loads the saved or default ingest job settings context into memory.
     */
    private void load() {
        /**
         * Get the ingest module factories discovered by the ingest module
         * loader.
         */
        List<IngestModuleFactory> moduleFactories = new ArrayList<>();
        List<IngestModuleFactory> allModuleFactories = IngestModuleFactoryLoader.getIngestModuleFactories();
        HashSet<String> loadedModuleNames = new HashSet<>();

        // Add modules that are going to be used for this ingest depending on type.
        for (IngestModuleFactory moduleFactory : allModuleFactories) {
            if (this.ingestType.equals(IngestType.ALL_MODULES)) {
                moduleFactories.add(moduleFactory);
            } else if (this.ingestType.equals(IngestType.DATA_SOURCE_ONLY) && moduleFactory.isDataSourceIngestModuleFactory()) {
                moduleFactories.add(moduleFactory);
            } else if (this.ingestType.equals(IngestType.FILES_ONLY) && moduleFactory.isFileIngestModuleFactory()) {
                moduleFactories.add(moduleFactory);
            }
        }

        for (IngestModuleFactory moduleFactory : moduleFactories) {
            loadedModuleNames.add(moduleFactory.getModuleDisplayName());
        }

        /**
         * Get the enabled/disabled ingest modules settings for this context. By
         * default, all loaded modules are enabled.
         */
        HashSet<String> enabledModuleNames = getModulesNamesFromSetting(IngestJobSettings.ENABLED_MODULES_KEY, makeCommaSeparatedValuesList(loadedModuleNames));
        HashSet<String> disabledModuleNames = getModulesNamesFromSetting(IngestJobSettings.DISABLED_MODULES_KEY, ""); //NON-NLS

        /**
         * Check for missing modules and create warnings if any are found.
         */
        List<String> missingModuleNames = new ArrayList<>();
        for (String moduleName : enabledModuleNames) {
            if (!loadedModuleNames.contains(moduleName)) {
                missingModuleNames.add(moduleName);
            }
        }
        for (String moduleName : disabledModuleNames) {
            if (!loadedModuleNames.contains(moduleName)) {
                missingModuleNames.add(moduleName);
            }
        }
        for (String moduleName : missingModuleNames) {
            enabledModuleNames.remove(moduleName);
            disabledModuleNames.remove(moduleName);
            String warning = NbBundle.getMessage(IngestJobSettings.class, "IngestJobSettings.missingModule.warning", moduleName); //NON-NLS
            LOGGER.log(Level.WARNING, warning);
            this.warnings.add(warning);
        }

        /**
         * Create ingest module templates. Each template encapsulates a module
         * factory, the module settings for this context, and an enabled flag.
         */
        for (IngestModuleFactory moduleFactory : moduleFactories) {
            IngestModuleTemplate moduleTemplate = new IngestModuleTemplate(moduleFactory, loadModuleSettings(moduleFactory));
            String moduleName = moduleTemplate.getModuleName();
            if (enabledModuleNames.contains(moduleName)) {
                moduleTemplate.setEnabled(true);
            } else if (disabledModuleNames.contains(moduleName)) {
                moduleTemplate.setEnabled(false);
            } else {
                // The module factory was loaded, but the module name does not
                // appear in the enabled/disabled module settings. Treat the
                // module as a new module and enable it by default.
                moduleTemplate.setEnabled(true);
                enabledModuleNames.add(moduleName);
            }
            this.moduleTemplates.add(moduleTemplate);
        }

        /**
         * Update the enabled/disabled ingest module settings for this context
         * to reflect any missing modules or newly discovered modules.
         */
        ModuleSettings.setConfigSetting(this.executionContext, IngestJobSettings.ENABLED_MODULES_KEY, makeCommaSeparatedValuesList(enabledModuleNames));
        ModuleSettings.setConfigSetting(this.executionContext, IngestJobSettings.DISABLED_MODULES_KEY, makeCommaSeparatedValuesList(disabledModuleNames));

        /**
         * Restore the last used File Ingest Filter
         */
        if (ModuleSettings.settingExists(this.executionContext, IngestJobSettings.LAST_FILE_INGEST_FILTER_KEY) == false) {
            ModuleSettings.setConfigSetting(this.executionContext, IngestJobSettings.LAST_FILE_INGEST_FILTER_KEY, IngestJobSettings.ALL_AND_UNALLOC_FILES_INGEST_FILTER.getName());
        }
        try {
            this.fileIngestFilter = FilesSetsManager.getInstance()
                    .getFileIngestFiltersWithDefaults()
                    .get(ModuleSettings.getConfigSetting(this.executionContext, IngestJobSettings.LAST_FILE_INGEST_FILTER_KEY));
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get File Ingest Filters", ex); //NON-NLS
        }
    }

    /**
     * Gets the module names for a given key within these ingest job settings.
     *
     * @param key            The key string.
     * @param defaultSetting The default list of module names.
     *
     * @return The list of module names associated with the key.
     */
    private HashSet<String> getModulesNamesFromSetting(String key, String defaultSetting) {
        if (ModuleSettings.settingExists(this.executionContext, key) == false) {
            ModuleSettings.setConfigSetting(this.executionContext, key, defaultSetting);
        }
        HashSet<String> moduleNames = new HashSet<>();
        String modulesSetting = ModuleSettings.getConfigSetting(this.executionContext, key);
        if (!modulesSetting.isEmpty()) {
            String[] settingNames = modulesSetting.split(", ");
            for (String name : settingNames) {
                // Map some old core module names to the current core module names.
                switch (name) {
                    case "Thunderbird Parser": //NON-NLS
                    case "MBox Parser": //NON-NLS
                        moduleNames.add("Email Parser"); //NON-NLS
                        break;
                    case "File Extension Mismatch Detection": //NON-NLS
                        moduleNames.add("Extension Mismatch Detector"); //NON-NLS
                        break;
                    case "EWF Verify": //NON-NLS
                    case "E01 Verify": //NON-NLS
                        moduleNames.add("E01 Verifier"); //NON-NLS
                        break;
                    case "Archive Extractor": //NON-NLS
                        moduleNames.add("Embedded File Extractor"); //NON-NLS
                        break;
                    default:
                        moduleNames.add(name);
                }
            }
        }
        return moduleNames;
    }

    /**
     * Determines if the moduleSettingsFilePath is that of a serialized jython
     * instance. Serialized Jython instances (settings saved on the disk)
     * contain "org.python.proxies." in their fileName based on the current
     * implementation.
     *
     * @param moduleSettingsFilePath path to the module settings file.
     *
     * @return True or false
     */
    private boolean isPythonModuleSettingsFile(String moduleSettingsFilePath) {
        return moduleSettingsFilePath.contains(pythonModuleSettingsPrefixCS);
    }

    /**
     * Gets the saved or default ingest job settings for a given ingest module
     * for these ingest job settings.
     *
     * @param factory The ingest module factory for an ingest module.
     *
     * @return The ingest module settings.
     */
    private IngestModuleIngestJobSettings loadModuleSettings(IngestModuleFactory factory) {
        IngestModuleIngestJobSettings settings = null;
        String moduleSettingsFilePath = getModuleSettingsFilePath(factory);
        File settingsFile = new File(moduleSettingsFilePath);
        if (settingsFile.exists()) {
            if (!isPythonModuleSettingsFile(moduleSettingsFilePath)) {
                try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(settingsFile.getAbsolutePath()))) {
                    settings = (IngestModuleIngestJobSettings) in.readObject();
                } catch (IOException | ClassNotFoundException ex) {
                    String warning = NbBundle.getMessage(IngestJobSettings.class, "IngestJobSettings.moduleSettingsLoad.warning", factory.getModuleDisplayName(), this.executionContext); //NON-NLS
                    LOGGER.log(Level.WARNING, warning, ex);
                    this.warnings.add(warning);
                }
            } else {
                try (PythonObjectInputStream in = new PythonObjectInputStream(new FileInputStream(settingsFile.getAbsolutePath()))) {
                    settings = (IngestModuleIngestJobSettings) in.readObject();
                } catch (IOException | ClassNotFoundException exception) {
                    String warning = NbBundle.getMessage(IngestJobSettings.class, "IngestJobSettings.moduleSettingsLoad.warning", factory.getModuleDisplayName(), this.executionContext); //NON-NLS
                    LOGGER.log(Level.WARNING, warning, exception);
                    this.warnings.add(warning);
                }
            }
        }
        if (settings == null) {
            settings = factory.getDefaultIngestJobSettings();
        }
        return settings;
    }

    /**
     * Returns the absolute path for the ingest job settings file for a given
     * ingest module for these ingest job settings.
     *
     * @param factory The ingest module factory for an ingest module.
     *
     * @return The file path.
     */
    private String getModuleSettingsFilePath(IngestModuleFactory factory) {
        String fileName = factory.getClass().getCanonicalName() + IngestJobSettings.MODULE_SETTINGS_FILE_EXT;
        Path path = Paths.get(this.moduleSettingsFolderPath, fileName);
        return path.toAbsolutePath().toString();
    }

    /**
     * Saves the ingest job settings for this context.
     */
    private void store() {
        /**
         * Save the enabled/disabled ingest module settings.
         */
        HashSet<String> enabledModuleNames = new HashSet<>();
        HashSet<String> disabledModuleNames = new HashSet<>();
        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
            saveModuleSettings(moduleTemplate.getModuleFactory(), moduleTemplate.getModuleSettings());
            String moduleName = moduleTemplate.getModuleName();
            if (moduleTemplate.isEnabled()) {
                enabledModuleNames.add(moduleName);
            } else {
                disabledModuleNames.add(moduleName);
            }
        }
        ModuleSettings.setConfigSetting(this.executionContext, ENABLED_MODULES_KEY, makeCommaSeparatedValuesList(enabledModuleNames));
        ModuleSettings.setConfigSetting(this.executionContext, DISABLED_MODULES_KEY, makeCommaSeparatedValuesList(disabledModuleNames));

        /**
         * Save the last used File Ingest Filter setting for this context.
         */
        ModuleSettings.setConfigSetting(this.executionContext, LAST_FILE_INGEST_FILTER_KEY, fileIngestFilter.getName());
    }

    /**
     * Serializes the ingest job settings for this context for a given ingest
     * module.
     *
     * @param factory  The ingest module factory for the module.
     * @param settings The ingest job settings for the ingest module
     */
    private void saveModuleSettings(IngestModuleFactory factory, IngestModuleIngestJobSettings settings) {
        String moduleSettingsFilePath = Paths.get(this.moduleSettingsFolderPath, FactoryClassNameNormalizer.normalize(factory.getClass().getCanonicalName()) + MODULE_SETTINGS_FILE_EXT).toString();
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(moduleSettingsFilePath))) {
            out.writeObject(settings);
        } catch (IOException ex) {
            String warning = NbBundle.getMessage(IngestJobSettings.class, "IngestJobSettings.moduleSettingsSave.warning", factory.getModuleDisplayName(), this.executionContext); //NON-NLS
            LOGGER.log(Level.SEVERE, warning, ex);
            this.warnings.add(warning);
        }
    }

    /**
     * Makes a comma-separated values list from a hash set of strings.
     *
     * @param input A hash set of strings.
     *
     * @return The contents of the hash set as a single string of
     *         comma-separated values.
     */
    private static String makeCommaSeparatedValuesList(HashSet<String> input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        ArrayList<String> list = new ArrayList<>();
        list.addAll(input);
        StringBuilder csvList = new StringBuilder();
        for (int i = 0; i < list.size() - 1; ++i) {
            csvList.append(list.get(i)).append(", ");
        }
        csvList.append(list.get(list.size() - 1));
        return csvList.toString();
    }

}
