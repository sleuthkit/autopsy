/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.Content;

/**
 * Provides a mechanism for creating and persisting an ingest job configuration
 * for a particular context and for launching ingest jobs that process one or
 * more data sources using the ingest job configuration.
 */
public final class IngestJobLauncher {

    private static final String ENABLED_INGEST_MODULES_KEY = "Enabled_Ingest_Modules"; //NON-NLS
    private static final String DISABLED_INGEST_MODULES_KEY = "Disabled_Ingest_Modules"; //NON-NLS
    private static final String PARSE_UNALLOC_SPACE_KEY = "Process_Unallocated_Space"; //NON-NLS
    private final String launcherContext;
    private final List<String> warnings = new ArrayList<>();
    private IngestJobConfigurationPanel ingestConfigPanel;

    /**
     * Constructs an ingest job launcher that creates and persists an ingest job
     * configuration for a particular context and launches ingest jobs that
     * process one or more data sources using the ingest job configuration.
     *
     * @param launcherContext The context identifier.
     */
    public IngestJobLauncher(String launcherContext) {
        this.launcherContext = launcherContext;

        // Get the ingest module factories discovered by the ingest module 
        // loader.
        List<IngestModuleFactory> moduleFactories = IngestModuleFactoryLoader.getInstance().getIngestModuleFactories();
        HashSet<String> loadedModuleNames = new HashSet<>();
        for (IngestModuleFactory moduleFactory : moduleFactories) {
            loadedModuleNames.add(moduleFactory.getModuleDisplayName());
        }

        // Get the enabled and disabled ingest modules settings for the current
        // context. Observe that the default settings make all loaded ingest 
        // modules enabled. 
        HashSet<String> enabledModuleNames = getModulesNamesFromSetting(ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(loadedModuleNames));
        HashSet<String> disabledModuleNames = getModulesNamesFromSetting(DISABLED_INGEST_MODULES_KEY, "");

        // Check for missing modules. 
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
            warnings.add(String.format("Previously loaded %s module could not be found", moduleName)); //NON-NLS
        }

        // Create ingest module templates.
        List<IngestModuleTemplate> moduleTemplates = new ArrayList<>();
        for (IngestModuleFactory moduleFactory : moduleFactories) {
            String name = moduleFactory.getClass().getCanonicalName();
            IngestModuleTemplate moduleTemplate = new IngestModuleTemplate(moduleFactory, deserializeJobSettings(moduleFactory, launcherContext));
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
            moduleTemplates.add(moduleTemplate);
        }

        // Update the enabled/disabled ingest module settings to reflect any 
        // missing modules or newly discovered modules.        
        ModuleSettings.setConfigSetting(launcherContext, ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(enabledModuleNames));
        ModuleSettings.setConfigSetting(launcherContext, DISABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(disabledModuleNames));

        // Get the process unallocated space flag setting. If the setting does
        // not exist yet, default it to false.
        if (ModuleSettings.settingExists(launcherContext, PARSE_UNALLOC_SPACE_KEY) == false) {
            ModuleSettings.setConfigSetting(launcherContext, PARSE_UNALLOC_SPACE_KEY, "false"); //NON-NLS
        }
        boolean processUnallocatedSpace = Boolean.parseBoolean(ModuleSettings.getConfigSetting(launcherContext, PARSE_UNALLOC_SPACE_KEY));

        // Make the configuration panel for the context.
        ingestConfigPanel = new IngestJobConfigurationPanel(moduleTemplates, processUnallocatedSpace);
    }

    private HashSet<String> getModulesNamesFromSetting(String key, String defaultSetting) {
        // Get the ingest modules setting from the user's config file. 
        // If there is no such setting yet, create the default setting.
        if (ModuleSettings.settingExists(launcherContext, key) == false) {
            ModuleSettings.setConfigSetting(launcherContext, key, defaultSetting);
        }
        HashSet<String> moduleNames = new HashSet<>();
        String modulesSetting = ModuleSettings.getConfigSetting(launcherContext, key);
        if (!modulesSetting.isEmpty()) {
            String[] settingNames = modulesSetting.split(", ");
            for (String name : settingNames) {
                // Map some old core module names to the current core module names.
                switch (name) {
                    case "Thunderbird Parser":
                    case "MBox Parser":
                        moduleNames.add("Email Parser");
                        break;
                    case "File Extension Mismatch Detection":
                        moduleNames.add("Extension Mismatch Detector");
                        break;
                    case "EWF Verify":
                    case "E01 Verify":
                        moduleNames.add("E01 Verifier");
                        break;
                    default:
                        moduleNames.add(name);
                }
            }
        }
        return moduleNames;
    }
        
    private IngestModuleIngestJobSettings deserializeJobSettings(IngestModuleFactory moduleFactory, String context) {
        return moduleFactory.getDefaultIngestJobSettings();
    }

    private void serializeJobSettings(IngestModuleFactory moduleFactory, IngestModuleIngestJobSettings settings) {
    }
        
    /**
     * Gets any warnings generated when the persisted ingest job configuration
     * for the specified context is retrieved and loaded.
     *
     * @return A collection of warning messages.
     */
    public List<String> getIngestJobConfigWarnings() {
        return warnings;
    }

    /**
     * Gets the user interface panel the launcher uses to obtain the user's
     * ingest job configuration for the specified context.
     *
     * @return A JPanel with components that can be used to create an ingest job
     * configuration.
     */
    public JPanel getIngestJobConfigPanel() {
        return ingestConfigPanel;
    }

    /**
     * Persists the ingest job configuration for the specified context.
     */
    public void saveIngestJobConfig() {
        List<IngestModuleTemplate> moduleTemplates = ingestConfigPanel.getIngestModuleTemplates();

        // Save the enabled/disabled ingest module settings for the current context.
        HashSet<String> enabledModuleNames = new HashSet<>();
        HashSet<String> disabledModuleNames = new HashSet<>();
        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
            String moduleName = moduleTemplate.getModuleName();
            if (moduleTemplate.isEnabled()) {
                enabledModuleNames.add(moduleName);
            } else {
                disabledModuleNames.add(moduleName);
            }            
        }
        ModuleSettings.setConfigSetting(launcherContext, ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(enabledModuleNames));
        ModuleSettings.setConfigSetting(launcherContext, DISABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(disabledModuleNames));

        // Save the process unallocated space setting for the current context.
        String processUnalloc = Boolean.toString(ingestConfigPanel.getProcessUnallocSpace());
        ModuleSettings.setConfigSetting(launcherContext, PARSE_UNALLOC_SPACE_KEY, processUnalloc);
    }

    private static String makeCommaSeparatedList(HashSet<String> input) {
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
        
    /**
     * Launches ingest jobs for one or more data sources using the ingest job
     * configuration for the selected context.
     *
     * @param dataSources The data sources to ingest.
     */
    public void startIngestJobs(List<Content> dataSources) {
        // Filter out the disabled ingest module templates.
        List<IngestModuleTemplate> enabledModuleTemplates = new ArrayList<>();
        List<IngestModuleTemplate> moduleTemplates = ingestConfigPanel.getIngestModuleTemplates();
        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
            if (moduleTemplate.isEnabled()) {
                enabledModuleTemplates.add(moduleTemplate);
            }
        }

        if ((!enabledModuleTemplates.isEmpty()) && (dataSources != null) && (!dataSources.isEmpty())) {
            IngestManager.getInstance().startIngestJobs(dataSources, enabledModuleTemplates, ingestConfigPanel.getProcessUnallocSpace());
        }
    }
}
