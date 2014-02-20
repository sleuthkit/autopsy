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
import java.util.List;
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

        List<IngestModuleAbstract> loadedModules = IngestManager.getDefault().enumerateAllModules();
                
        // Get the enabled ingest modules setting from the user's config file. 
        // If there is no such setting yet, create the default setting of all
        // loaded ingest modules enabled.
        if (ModuleSettings.settingExists(moduleContext, ENABLED_INGEST_MODULES_KEY) == false) {
            ModuleSettings.setConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY, moduleListToCsv(loadedModules));
        }
        ArrayList<String> enabledModuleNames = new ArrayList<>();
        String enabledModulesSetting = ModuleSettings.getConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY);
        if (!enabledModulesSetting.isEmpty()) {
            enabledModuleNames.addAll(Arrays.asList(enabledModulesSetting.split(", ")));
        }

        // Get the disabled ingest modules setting from the user's config file. 
        // If there is no such setting yet, create the default setting of no
        // ingest modules disabled.
        if (ModuleSettings.settingExists(moduleContext, DISABLED_INGEST_MODULES_KEY) == false) {
            ModuleSettings.setConfigSetting(moduleContext, DISABLED_INGEST_MODULES_KEY, "");
        }   
        ArrayList<String> disabledModuleNames = new ArrayList<>();
        String disabledModulesSetting = ModuleSettings.getConfigSetting(moduleContext, DISABLED_INGEST_MODULES_KEY);
        if (!disabledModulesSetting.isEmpty()) {
            disabledModuleNames.addAll(Arrays.asList(disabledModulesSetting.split(", ")));
        }
        
        // Check for ingest modules that were loaded, but do not appear in the 
        // settings. If any such modules are found, consider them to be enabled 
        // by default.
        for (IngestModuleAbstract module : loadedModules) {            
            boolean found = false;
            
            // Is the module in the enabled list?
            for (String moduleName : enabledModuleNames) {
                if (module.getName().equals(moduleName)) {
                    found = true;
                    break;
                }
            }                
            
            // If not, is the module in the diabled list?
            if (!found) {
                for (String moduleName : disabledModuleNames) {
                    if (module.getName().equals(moduleName)) {
                        found = true;
                        break;
                    }
                }
            }
            
            // If the module is not in either list, add it to the enabled list.
            if (!found) {
                enabledModuleNames.add(module.getName());
            }
        }            
        
        // Now use the enabled module names to create a list of enabled ingest 
        // modules for the ingest dialog panel (the panel has also grabbed the 
        // list of loaded ingest modules and will use the list created here as 
        // a filter, so a list of disabled modules is not created). Also, 
        // identify any modules that appear in the enabled modules setting, but 
        // are not loaded.
        List<IngestModuleAbstract> enabledModules = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();  
        for (String moduleName : enabledModuleNames) {
            // Map some old core module names to the current core module names.
            if (moduleName.equals("Thunderbird Parser") || moduleName.equals("MBox Parser")) {
                moduleName = "Email Parser";
            }            
            if (moduleName.equals("File Extension Mismatch Detection") || moduleName.equals("Extension Mismatch Detector")) {
                moduleName = "File Extension Mismatch Detector";                
            }

            IngestModuleAbstract moduleFound =  null;
            for (IngestModuleAbstract module : loadedModules) {
                if (moduleName.equals(module.getName())) {
                    moduleFound = module;
                    break;
                }
            }
            
            if (null != moduleFound) {
                enabledModules.add(moduleFound);
            }
            else {
                errorMessages.add(moduleName + " was previously enabled, but could not be found");
                disabledModuleNames.add(moduleName);
            }
        }
        
        // Update the enabled/disabled ingest module settings. This way the
        // settings will be up to date, even if save() is never called.
        ModuleSettings.setConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY, moduleNamesListToCsv(enabledModuleNames));
        ModuleSettings.setConfigSetting(moduleContext, DISABLED_INGEST_MODULES_KEY, moduleNamesListToCsv(disabledModuleNames));                

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

    private static String moduleListToCsv(List<IngestModuleAbstract> lst) {
        if (lst == null || lst.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lst.size() - 1; ++i) {
            sb.append(lst.get(i).getName()).append(", ");
        }
        
        // and the last one
        sb.append(lst.get(lst.size() - 1).getName());
        
        return sb.toString();
    }
        
    private static String moduleNamesListToCsv(List<String> lst) {
        if (lst == null || lst.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lst.size() - 1; ++i) {
            sb.append(lst.get(i)).append(", ");
        }
        
        // and the last one
        sb.append(lst.get(lst.size() - 1));
        
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
