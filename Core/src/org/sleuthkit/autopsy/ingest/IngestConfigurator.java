/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2014 Basis Technology Corp.
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
 * RJCTODO: Improve comment Controller to allow a user to set context-sensitive
 * ingest module options, enable/disable ingest modules, and set general ingest
 * options. Provides an ingest module model class and instances of a UI
 * component to its clients (Model-View-Controller design pattern).
 */
public class IngestConfigurator {

    private static final String ENABLED_INGEST_MODULES_KEY = "Enabled_Ingest_Modules";
    private static final String DISABLED_INGEST_MODULES_KEY = "Disabled_Ingest_Modules";
    private static final String PARSE_UNALLOC_SPACE_KEY = "Process_Unallocated_Space";
    private final String context;
    private List<String> missingIngestModuleErrorMessages = new ArrayList<>();
    private IngestConfigurationPanel ingestConfigPanel = null;
    private List<Content> contentToIngest = null; // RJCTODO: Remove if start() method removed

    /**
     * RJCTODO
     *
     * @param context
     */
    public IngestConfigurator(String context) {
        this.context = context;

        // Get the ingest module factories discovered by the ingest module 
        // loader.
        // RJCTODO: Put in name uniqueness test/solution in loader!
        List<IngestModuleFactory> moduleFactories = IngestModuleLoader.getDefault().getIngestModuleFactories();
        HashSet<String> loadedModuleNames = new HashSet<>();
        for (IngestModuleFactory moduleFactory : moduleFactories) {
            loadedModuleNames.add(moduleFactory.getModuleDisplayName());
        }

        // Get the enabled and disabled ingest modules settings for the current
        // context. The default settings make all ingest modules enabled. 
        HashSet<String> enabledModuleNames = getModulesNamesFromSetting(ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(loadedModuleNames));
        HashSet<String> disabledModuleNames = getModulesNamesFromSetting(DISABLED_INGEST_MODULES_KEY, "");

        // Create ingest module templates for the current context.
        HashSet<String> knownModuleNames = new HashSet<>();
        List<IngestModuleTemplate> moduleTemplates = new ArrayList<>();
        for (IngestModuleFactory moduleFactory : moduleFactories) {
            // NOTE: In the future, this code will be modified to get the 
            // resources configuration and ingest job options for each module 
            // for the current context; for now just get the defaults.
            IngestModuleSettings ingestOptions = moduleFactory.getDefaultIngestJobOptions();
            IngestModuleTemplate moduleTemplate = new IngestModuleTemplate(moduleFactory, ingestOptions);
            String moduleName = moduleTemplate.getIngestModuleFactory().getModuleDisplayName();
            if (enabledModuleNames.contains(moduleName)) {
                moduleTemplate.setEnabled(true);
            } else if (disabledModuleNames.contains(moduleName)) {
                moduleTemplate.setEnabled(true);
            } else {
                // The module factory was loaded, but the module name does not
                // appear in the enabled/disabled module settings. Treat the
                // module as a new module and enable it by default.
                moduleTemplate.setEnabled(true);
                enabledModuleNames.add(moduleName);
            }
            moduleTemplates.add(moduleTemplate);
            knownModuleNames.add(moduleName);
        }

        // Check for missing modules and update the enabled/disabled ingest 
        // module settings for any missing modules.
        for (String moduleName : enabledModuleNames) {
            if (!knownModuleNames.contains(moduleName)) {
                missingIngestModuleErrorMessages.add(moduleName + " was previously enabled, but could not be found");
                enabledModuleNames.remove(moduleName);
                disabledModuleNames.add(moduleName); // RJCTODO: Is this the right behavior?                
            }
        }
        ModuleSettings.setConfigSetting(context, ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(enabledModuleNames));
        ModuleSettings.setConfigSetting(context, DISABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(disabledModuleNames));

        // Get the process unallocated space flag setting. If the setting does
        // not exist yet, default it to false.
        if (ModuleSettings.settingExists(context, PARSE_UNALLOC_SPACE_KEY) == false) {
            ModuleSettings.setConfigSetting(context, PARSE_UNALLOC_SPACE_KEY, "false");
        }
        boolean processUnallocatedSpace = Boolean.parseBoolean(ModuleSettings.getConfigSetting(context, PARSE_UNALLOC_SPACE_KEY));

        // Make the configuration panel for the current context (view).
        ingestConfigPanel = new IngestConfigurationPanel(moduleTemplates, processUnallocatedSpace);
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public List<String> getMissingIngestModuleErrorMessages() {
        return missingIngestModuleErrorMessages;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public JPanel getIngestConfigPanel() {
        return ingestConfigPanel;
    }

    /**
     * RJCTODO
     *
     * @throws
     * org.sleuthkit.autopsy.ingest.IngestConfigurator.IngestConfigurationException
     */
    public void save() {
        List<IngestModuleTemplate> moduleTemplates = ingestConfigPanel.getIngestModuleTemplates();

        // Save the enabled/disabled ingest module settings for the current context.
        HashSet<String> enabledModuleNames = new HashSet<>();
        HashSet<String> disabledModuleNames = new HashSet<>();
        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
            String moduleName = moduleTemplate.getIngestModuleFactory().getModuleDisplayName();
            if (moduleTemplate.isEnabled()) {
                enabledModuleNames.add(moduleName);
            } else {
                disabledModuleNames.add(moduleName);
            }
        }
        ModuleSettings.setConfigSetting(context, ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(enabledModuleNames));
        ModuleSettings.setConfigSetting(context, DISABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(disabledModuleNames));

        // Save the process unallocated space setting for the current context.
        String processUnalloc = Boolean.toString(ingestConfigPanel.getProcessUnallocSpace());
        ModuleSettings.setConfigSetting(context, PARSE_UNALLOC_SPACE_KEY, processUnalloc);

        // NOTE: In the future, this code will be modified to persist the ingest 
        // options for each ingest module for the current context.        
    }

    // RJCTODO: If time permits, make it so that this class is not responsible
    // starting and running the ingest - probably need to do this anyway, at 
    // least if the IngestConfigurator interface goes away and this becomes the
    // IngestConfigurator class.
    public void setContent(List<Content> inputContent) {
        this.contentToIngest = inputContent;
    }

    // RJCTODO: If time permits, make it so that this class is not responsible
    // starting and running the ingest - probably need to do this anyway, at 
    // least if the IngestConfigurator interface goes away and this becomes the
    // IngestConfigurator class.
    public void start() {
        // Filter out the disabled module tremplates.
        List<IngestModuleTemplate> moduleTemplates = ingestConfigPanel.getIngestModuleTemplates();
        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
            if (!moduleTemplate.isEnabled()) {
                moduleTemplates.remove(moduleTemplate);
            }
        }

        if (!moduleTemplates.isEmpty() && null != contentToIngest) {
            IngestManager.getDefault().scheduleDataSourceTasks(contentToIngest, moduleTemplates, ingestConfigPanel.getProcessUnallocSpace());
        }
    }

    // RJCTODO: If time permits, make it so that this class is not responsible
    // starting and running the ingest - probably need to do this anyway, at 
    // least if the IngestConfigurator interface goes away and this becomes the
    // IngestConfigurator class.
    public boolean isIngestRunning() {
        return IngestManager.getDefault().isIngestRunning();
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

    private HashSet<String> getModulesNamesFromSetting(String key, String defaultSetting) {
        // Get the ingest modules setting from the user's config file. 
        // If there is no such setting yet, create the default setting.
        if (ModuleSettings.settingExists(context, key) == false) {
            ModuleSettings.setConfigSetting(context, key, defaultSetting);
        }
        HashSet<String> moduleNames = new HashSet<>();
        String modulesSetting = ModuleSettings.getConfigSetting(context, key);
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
                    case "Extension Mismatch Detector":
                        moduleNames.add("File Extension Mismatch Detector");
                        break;
                    default:
                        moduleNames.add(name);
                }
            }
        }
        return moduleNames;
    }
}
