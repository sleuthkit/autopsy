/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.Content;

@ServiceProvider(service = IngestConfigurator.class)
public class GeneralIngestConfigurator implements IngestConfigurator { 
   
    public static final String ENABLED_INGEST_MODULES_KEY = "Enabled_Ingest_Modules";
    public static final String DISABLED_INGEST_MODULES_KEY = "Disabled_Ingest_Modules";
    public static final String PARSE_UNALLOC_SPACE_KEY = "Process_Unallocated_Space";
    private List<Content> contentToIngest;
    private IngestManager manager;
    private IngestDialogPanel ingestDialogPanel;
    private String moduleContext;
    
    public GeneralIngestConfigurator() {
        this.moduleContext = IngestManager.MODULE_PROPERTIES;
        ingestDialogPanel = new IngestDialogPanel();
        ingestDialogPanel.setContext(moduleContext);
        manager = IngestManager.getDefault();
    }
    
    @Override
    public List<String> setContext(String context) {
        moduleContext = context;
        ingestDialogPanel.setContext(moduleContext);
        return loadSettingsForContext();
    }

    private List<String> loadSettingsForContext() {
        List<IngestModuleFactory> moduleFactories = IngestManager.getDefault().getIngestModuleFactories();

        // Get the enabled and disabled ingest modules settings from the user's 
        // config file. The default settings make all ingest modules enabled. 
        HashSet<String> enabledModuleNames = getModulesNamesFromSetting(ENABLED_INGEST_MODULES_KEY, moduleListToCsv(moduleFactories));        
        HashSet<String> disabledModuleNames = getModulesNamesFromSetting(DISABLED_INGEST_MODULES_KEY, "");        
        
        // Set up a collection of module templates for the view.
        List<IngestModuleTemplate> moduleTemplates = new ArrayList<>();  
        HashSet<String> foundModules = new HashSet<>();
        for (IngestModuleFactory moduleFactory : moduleFactories) {
            String moduleName = moduleFactory.getModuleDisplayName();            
            IngestModuleTemplate moduleTemplate = new IngestModuleTemplate(moduleFactory, null, enabledModuleNames.contains(moduleName));            
            if (!enabledModuleNames.contains(moduleName) && !enabledModuleNames.contains(moduleName)) {
                // The module factory was loaded, but the module name does not
                // appear in the enabled/disabled module settings. Treat the
                // module as a new module and enable it by default.
                moduleTemplate.setEnabled(true);
                enabledModuleNames.add(moduleName);
            }
            foundModules.add(moduleName);
        }
        
        // Check for missing modules and update the enabled/disabled ingest 
        // module settings. This way the settings will be up to date, even if 
        // save() is never called.
        List<String> errorMessages = new ArrayList<>();  
        for (String moduleName : enabledModuleNames) {
            if (!foundModules.contains(moduleName)) {
                errorMessages.add(moduleName + " was previously enabled, but could not be found");
                enabledModuleNames.remove(moduleName);
                disabledModuleNames.add(moduleName);                
            }
        }                
        ModuleSettings.setConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(enabledModuleNames));
        ModuleSettings.setConfigSetting(moduleContext, DISABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(disabledModuleNames));                

        // Get the process unallocated space flag setting. If the setting does
        // not exist yet, default it to false.
        if (ModuleSettings.settingExists(moduleContext, PARSE_UNALLOC_SPACE_KEY) == false) {
            ModuleSettings.setConfigSetting(moduleContext, PARSE_UNALLOC_SPACE_KEY, "false");
        }                
        boolean processUnalloc = Boolean.parseBoolean(ModuleSettings.getConfigSetting(moduleContext, PARSE_UNALLOC_SPACE_KEY));

        // Pass the settings to the nigest dialog panel.
        ingestDialogPanel.setEnabledIngestModules(enabledModules);                            
        ingestDialogPanel.setProcessUnallocSpaceEnabled(processUnalloc);        
        
        return errorMessages;
    }
        
    private HashSet<String> getModulesNamesFromSetting(String key, String defaultSetting) {
        // Get the ingest modules setting from the user's config file. 
        // If there is no such setting yet, create the default setting.
        if (ModuleSettings.settingExists(moduleContext, key) == false) {
            ModuleSettings.setConfigSetting(moduleContext, key, defaultSetting);
        }
        HashSet<String> moduleNames = new HashSet<>();
        String modulesSetting = ModuleSettings.getConfigSetting(moduleContext, key);
        if (!modulesSetting.isEmpty()) {
            String[] settingNames = modulesSetting.split(", ");
            for (String name : settingNames) {
                // Map some old core module names to the current core module names.
                if (name.equals("Thunderbird Parser") || name.equals("MBox Parser")) {
                    moduleNames.add("Email Parser");
                }            
                else if (name.equals("File Extension Mismatch Detection") || name.equals("Extension Mismatch Detector")) {
                    moduleNames.add("File Extension Mismatch Detector");                
                }
                else {
                    moduleNames.add(name);
                }            
            }
        }        
        return moduleNames;        
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
    
   @Override
    public JPanel getIngestConfigPanel() {
       // Note that this panel allows for selecting modules for the ingest process, 
       // specifying the process unallocated space flag, and also specifying settings 
       // for a selected ingest module.
       return ingestDialogPanel;
    }    
    
    @Override
    public void save() {        
        // Save the user's configuration of the set of enabled ingest modules.
        String enabledModulesCsvList = moduleListToCsv(ingestDialogPanel.getModulesToStart());
        ModuleSettings.setConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY, enabledModulesCsvList);
        
        // Save the user's configuration of the set of disabled ingest modules.
        String disabledModulesCsvList = moduleListToCsv(ingestDialogPanel.getDisabledModules());
        ModuleSettings.setConfigSetting(moduleContext, DISABLED_INGEST_MODULES_KEY, disabledModulesCsvList);        
        
        // Save the user's setting for the process unallocated space flag.
        String processUnalloc = Boolean.toString(ingestDialogPanel.processUnallocSpaceEnabled());
        ModuleSettings.setConfigSetting(moduleContext, PARSE_UNALLOC_SPACE_KEY, processUnalloc);
        
        // Save the user's configuration of the currently selected ingest module.
        IngestModuleAbstract currentModule = ingestDialogPanel.getCurrentIngestModule();
        if (currentModule != null && currentModule.hasSimpleConfiguration()) {
            currentModule.saveSimpleConfiguration();
        }        
    }

    private static String moduleListToCsv(List<IngestModuleFactory> lst) {
        if (lst == null || lst.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lst.size() - 1; ++i) {
            sb.append(lst.get(i).getModuleDisplayName()).append(", ");
        }
        
        // and the last one
        sb.append(lst.get(lst.size() - 1).getModuleDisplayName());
        
        return sb.toString();
    }
        
    @Override
    public void setContent(List<Content> inputContent) {
        this.contentToIngest = inputContent;
    }

    @Override
    public void start() {
        // Get the list of ingest modules selected by the user.
        List<IngestModuleAbstract> modulesToStart = ingestDialogPanel.getModulesToStart();
        
        // Get the user's selection of whether or not to process unallocated space.
        manager.setProcessUnallocSpace(ingestDialogPanel.processUnallocSpaceEnabled());

        if (!modulesToStart.isEmpty() && contentToIngest != null) {
            // Queue the ingest process.
            manager.scheduleDataSource(modulesToStart, contentToIngest);
        }
    }

    @Override
    public boolean isIngestRunning() {
        return manager.isIngestRunning();
    }        
}
