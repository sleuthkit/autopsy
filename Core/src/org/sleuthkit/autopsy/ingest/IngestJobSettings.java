/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
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
 * The settings for an ingest job.
 */
public final class IngestJobSettings {

    private static final String ENABLED_MODULES_PROPERTY = "Enabled_Ingest_Modules"; //NON-NLS
    private static final String DISABLED_MODULES_PROPERTY = "Disabled_Ingest_Modules"; //NON-NLS
    private static final String LAST_FILE_INGEST_FILTER_PROPERTY = "Last_File_Ingest_Filter"; //NON-NLS
    private static final String MODULE_SETTINGS_FOLDER = "IngestModuleSettings"; //NON-NLS
    private static final String MODULE_SETTINGS_FOLDER_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), IngestJobSettings.MODULE_SETTINGS_FOLDER).toAbsolutePath().toString();
    private static final String MODULE_SETTINGS_FILE_EXT = ".settings"; //NON-NLS
    private static final CharSequence PYTHON_CLASS_PROXY_PREFIX = "org.python.proxies.".subSequence(0, "org.python.proxies.".length() - 1); //NON-NLS
    private static final Logger logger = Logger.getLogger(IngestJobSettings.class.getName());
    private final IngestType ingestType;
    private final List<IngestModuleTemplate> moduleTemplates = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private String executionContext;
    private FilesSet fileFilter;
    private String moduleSettingsFolderPath;

    /**
     * Gets the path to the module settings folder for a given execution
     * context.
     *
     * Some examples of execution contexts include the Add Data Source wizard,
     * the Run Ingest Modules dialog, and auto ingest. Different execution
     * contexts may have different ingest job settings.
     *
     * @param The execution context identifier.
     *
     * @return The path to the module settings folder
     */
    static Path getSavedModuleSettingsFolder(String executionContext) {
        return Paths.get(IngestJobSettings.MODULE_SETTINGS_FOLDER_PATH, executionContext);
    }

    /**
     * Loads the ingest job settings for a given execution context. If settings
     * for the context have not been previously saved, default settings are
     * used.
     *
     * Some examples of execution contexts include the Add Data Source wizard,
     * the Run Ingest Modules dialog, and auto ingest. Different execution
     * contexts may have different ingest job settings.
     *
     * @param executionContext The execution context identifier.
     */
    public IngestJobSettings(final String executionContext) {
        this(executionContext, IngestType.ALL_MODULES);
    }

    /**
     * Loads the ingest job settings for a given execution context. If settings
     * for the context have not been previously saved, default settings are
     * used.
     *
     * Some examples of execution contexts include the Add Data Source wizard,
     * the Run Ingest Modules dialog, and auto ingest. Different execution
     * contexts may have different ingest job settings.
     *
     * @param executionContext The execution context identifier.
     * @param ingestType       Whether to run all ingest modules, data source
     *                         level ingest modules only, or file level ingest
     *                         modules only.
     */
    public IngestJobSettings(String executionContext, IngestType ingestType) {
        this.ingestType = ingestType;
        if (this.ingestType.equals(IngestType.ALL_MODULES)) {
            this.executionContext = executionContext;
        } else {
            this.executionContext = executionContext + "." + this.ingestType.name();
        }
        this.createSavedModuleSettingsFolder();
        this.load();
    }

    /**
     * Creates entirely new ingest job settings for a given context without
     * saving them.
     *
     * @param executionContext The execution context identifier.
     * @param ingestType       Whether to run all ingest modules, data source
     *                         level ingest modules only, or file level ingest
     *                         modules only.
     * @param moduleTemplates  A collection of ingest module templates for
     *                         creating fully configured ingest modules; each
     *                         template combines an ingest module factory with
     *                         ingest module job settings and an enabled flag.
     */
    public IngestJobSettings(String executionContext, IngestType ingestType, Collection<IngestModuleTemplate> moduleTemplates) {
        this.ingestType = ingestType;
        if (this.ingestType.equals(IngestType.ALL_MODULES)) {
            this.executionContext = executionContext;
        } else {
            this.executionContext = executionContext + "." + this.ingestType.name();
        }
        this.moduleTemplates.addAll(moduleTemplates);
    }

    /**
     * Creates entirely new ingest job settings for a given context without
     * saving them.
     *
     * @param executionContext The execution context identifier.
     * @param ingestType       Whether to run all ingest modules, data source
     *                         level ingest modules only, or file level ingest
     *                         modules only.
     * @param moduleTemplates  A collection of ingest module templates for
     *                         creating fully configured ingest modules; each
     *                         template combines an ingest module factory with
     *                         ingest module job settings and an enabled flag.
     * @param fileFilter       A file filter in the form of a files set.
     */
    public IngestJobSettings(String executionContext, IngestType ingestType, Collection<IngestModuleTemplate> moduleTemplates, FilesSet fileFilter) {
        this(executionContext, ingestType, moduleTemplates);
        this.setFileFilter(fileFilter);
    }

    /**
     * Gets the path to the module settings folder for these ingest job
     * settings.
     *
     * @return The path to the ingest module settings folder.
     */
    public Path getSavedModuleSettingsFolder() {
        return Paths.get(IngestJobSettings.MODULE_SETTINGS_FOLDER_PATH, executionContext);
    }

    /**
     * Saves these ingest job settings.
     */
    public void save() {
        this.createSavedModuleSettingsFolder();
        this.store();
    }

    /**
     * Saves these ingest job settings for use in a different execution context.
     *
     * Some examples of execution contexts include the Add Data Source wizard,
     * the Run Ingest Modules dialog, and auto ingest. Different execution
     * contexts may have different ingest job settings.
     *
     * @param executionContext The new execution context.
     */
    public void saveAs(String executionContext) {
        this.executionContext = executionContext;
        this.createSavedModuleSettingsFolder();
        this.store();
    }

    /**
     * Gets and clears any accumulated warnings associated with the loading or
     * saving of these ingest job settings.
     *
     * @return A list of warning messages, possibly empty.
     */
    public List<String> getWarnings() {
        List<String> warningMessages = new ArrayList<>(this.warnings);
        this.warnings.clear();
        return warningMessages;
    }

    /**
     * Gets the execution context identifier.
     *
     * Some examples of execution contexts include the Add Data Source wizard,
     * the Run Ingest Modules dialog, and auto ingest. Different execution
     * contexts may have different ingest job settings.
     *
     * @return The execution context identifier.
     */
    public String getExecutionContext() {
        return this.executionContext;
    }

    /**
     * Gets the file filter for the ingest job.
     *
     * @return FilesSet The filter as a files set.
     */
    public FilesSet getFileFilter() {
        if (fileFilter == null) {
            fileFilter = FilesSetsManager.getDefaultFilter();
        }
        return fileFilter;
    }

    /**
     * Sets the file filter for the ingest job.
     *
     * @param fileIngestFilter The filter as a files set.
     */
    public void setFileFilter(FilesSet fileIngestFilter) {
        this.fileFilter = fileIngestFilter;
    }

    /**
     * Gets the enabled ingest module templates for the ingest job.
     *
     * @return The list of ingest module templates.
     */
    public List<IngestModuleTemplate> getIngestModuleTemplates() {
        return Collections.unmodifiableList(this.moduleTemplates);
    }

    /**
     * Sets the enabled ingest module templates for the ingest job.
     *
     * @param moduleTemplates The ingest module templates.
     */
    public void setIngestModuleTemplates(List<IngestModuleTemplate> moduleTemplates) {
        this.moduleTemplates.clear();
        this.moduleTemplates.addAll(moduleTemplates);
    }

    /**
     * Gets the enabled ingest module templates for this ingest job.
     *
     * @return The list of enabled ingest module templates.
     */
    public List<IngestModuleTemplate> getEnabledIngestModuleTemplates() {
        List<IngestModuleTemplate> enabledModuleTemplates = new ArrayList<>();
        for (IngestModuleTemplate moduleTemplate : this.moduleTemplates) {
            if (moduleTemplate.isEnabled()) {
                enabledModuleTemplates.add(moduleTemplate);
            }
        }
        return enabledModuleTemplates;
    }

    /**
     * Gets the process unallocated space flag for this ingest job.
     *
     * @return True or false.
     *
     */
    public boolean getProcessUnallocatedSpace() {
        boolean processUnallocated = true;
        if (!Objects.isNull(this.fileFilter)) {
            processUnallocated = (this.fileFilter.ingoresUnallocatedSpace() == false);
        }
        return processUnallocated;
    }

    /**
     * Creates the module folder for these ingest job settings, if it does not
     * already exist.
     */
    private void createSavedModuleSettingsFolder() {
        try {
            Path folder = getSavedModuleSettingsFolder();
            Files.createDirectories(folder);
            this.moduleSettingsFolderPath = folder.toAbsolutePath().toString();
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, "Failed to create ingest module settings directory " + this.moduleSettingsFolderPath, ex); //NON-NLS
            this.warnings.add(NbBundle.getMessage(IngestJobSettings.class, "IngestJobSettings.createModuleSettingsFolder.warning")); //NON-NLS
        }
    }

    /**
     * Loads the saved or default ingest job settings for the execution context
     * into memory.
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
        HashSet<String> enabledModuleNames = getModulesNames(executionContext, IngestJobSettings.ENABLED_MODULES_PROPERTY, makeCsvList(loadedModuleNames));
        HashSet<String> disabledModuleNames = getModulesNames(executionContext, IngestJobSettings.DISABLED_MODULES_PROPERTY, ""); //NON-NLS

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
            logger.log(Level.WARNING, warning);
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
        ModuleSettings.setConfigSetting(this.executionContext, IngestJobSettings.ENABLED_MODULES_PROPERTY, makeCsvList(enabledModuleNames));
        ModuleSettings.setConfigSetting(this.executionContext, IngestJobSettings.DISABLED_MODULES_PROPERTY, makeCsvList(disabledModuleNames));

        /**
         * Restore the last used File Ingest Filter
         */
        if (ModuleSettings.settingExists(this.executionContext, IngestJobSettings.LAST_FILE_INGEST_FILTER_PROPERTY) == false) {
            ModuleSettings.setConfigSetting(this.executionContext, IngestJobSettings.LAST_FILE_INGEST_FILTER_PROPERTY, FilesSetsManager.getDefaultFilter().getName());
        }
        try {
            Map<String, FilesSet> fileIngestFilters = FilesSetsManager.getInstance()
                    .getCustomFileIngestFilters();
            for (FilesSet fSet : FilesSetsManager.getStandardFileIngestFilters()) {
                fileIngestFilters.put(fSet.getName(), fSet);
            }
            this.fileFilter = fileIngestFilters.get(ModuleSettings.getConfigSetting(this.executionContext, IngestJobSettings.LAST_FILE_INGEST_FILTER_PROPERTY));
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            this.fileFilter = FilesSetsManager.getDefaultFilter();
            logger.log(Level.SEVERE, "Failed to get file filter from .properties file, default filter being used", ex); //NON-NLS
        }
    }

    /**
     * Gets a list of enabled module names from the properties file for the
     * execution context of these ingest job settings.
     *
     * @param executionContext The execution context identifier.
     * @param propertyName     The property name.
     * @param defaultSetting   The default list of module names to se and return
     *                         if the property does not exist.
     *
     * @return
     */
    private static HashSet<String> getModulesNames(String executionContext, String propertyName, String defaultSetting) {
        if (ModuleSettings.settingExists(executionContext, propertyName) == false) {
            ModuleSettings.setConfigSetting(executionContext, propertyName, defaultSetting);
        }
        HashSet<String> moduleNames = new HashSet<>();
        String modulesSetting = ModuleSettings.getConfigSetting(executionContext, propertyName);
        if (!modulesSetting.isEmpty()) {
            String[] settingNames = modulesSetting.split(", ");
            for (String name : settingNames) {
                /*
                 * Map some obsolete core ingest module names to the current
                 * core ingest module names.
                 */
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
     * Gets a set which contains all the names of enabled modules for the
     * specified context.
     *
     * @param context -the execution context (profile name) to check
     *
     * @return the names of the enabled modules
     */
    static List<String> getEnabledModules(String context) {
        return new ArrayList<>(getModulesNames(context, ENABLED_MODULES_PROPERTY, ""));
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
        return moduleSettingsFilePath.contains(PYTHON_CLASS_PROXY_PREFIX);
    }

    /**
     * Gets the saved or default ingest job settings for a given ingest module.
     *
     * @param factory The ingest module factory.
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
                    logger.log(Level.WARNING, warning, ex);
                    this.warnings.add(warning);
                }
            } else {
                try (PythonObjectInputStream in = new PythonObjectInputStream(new FileInputStream(settingsFile.getAbsolutePath()))) {
                    settings = (IngestModuleIngestJobSettings) in.readObject();
                } catch (IOException | ClassNotFoundException exception) {
                    String warning = NbBundle.getMessage(IngestJobSettings.class, "IngestJobSettings.moduleSettingsLoad.warning", factory.getModuleDisplayName(), this.executionContext); //NON-NLS
                    logger.log(Level.WARNING, warning, exception);
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
        ModuleSettings.setConfigSetting(this.executionContext, IngestJobSettings.ENABLED_MODULES_PROPERTY, makeCsvList(enabledModuleNames));
        ModuleSettings.setConfigSetting(this.executionContext, IngestJobSettings.DISABLED_MODULES_PROPERTY, makeCsvList(disabledModuleNames));

        /**
         * Save the last used File Ingest Filter setting for this context.
         */
        ModuleSettings.setConfigSetting(this.executionContext, LAST_FILE_INGEST_FILTER_PROPERTY, fileFilter.getName());
    }

    /**
     * Serializes the ingest job settings for a given ingest module.
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
            logger.log(Level.SEVERE, warning, ex);
            this.warnings.add(warning);
        }
    }

    /**
     * Makes a comma-separated values list from a collection of strings.
     *
     * @param collection A collection of strings.
     *
     * @return The contents of the collection as a single string of
     *         comma-separated values.
     */
    private static String makeCsvList(Collection<String> collection) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }

        ArrayList<String> list = new ArrayList<>();
        list.addAll(collection);
        StringBuilder csvList = new StringBuilder();
        for (int i = 0; i < list.size() - 1; ++i) {
            csvList.append(list.get(i)).append(", ");
        }
        csvList.append(list.get(list.size() - 1));
        return csvList.toString();
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

}
