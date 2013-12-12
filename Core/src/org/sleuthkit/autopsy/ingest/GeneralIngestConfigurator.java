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
import java.util.List;
import javax.swing.JPanel;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.Content;

@ServiceProvider(service = IngestConfigurator.class)
public class GeneralIngestConfigurator implements IngestConfigurator { 
   
    public static final String ENABLED_INGEST_MODULES_KEY = "Enabled_Ingest_Modules";
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
        List<String> messages = new ArrayList<>();  
        
        // If there is no enabled ingest modules setting for this user, default to enabling all
        // of the ingest modules the IngestManager has loaded.
        if (ModuleSettings.settingExists(moduleContext, ENABLED_INGEST_MODULES_KEY) == false) {
            String defaultSetting = moduleListToCsv(IngestManager.getDefault().enumerateAllModules());
            ModuleSettings.setConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY, defaultSetting);
        }        
        
        // Get the enabled ingest modules setting, check for missing modules, and pass the setting to
        // the UI component.
        List<IngestModuleAbstract> allModules = IngestManager.getDefault().enumerateAllModules();
        String[] enabledModuleNames = ModuleSettings.getConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY).split(", ");
        List<IngestModuleAbstract> enabledModules = new ArrayList<>();
        for (String moduleName : enabledModuleNames) {
            if (moduleName.equals("Thunderbird Parser") 
                    || moduleName.equals("MBox Parser")) {
                moduleName = "Email Parser";
            }
            
            IngestModuleAbstract moduleFound =  null;
            for (IngestModuleAbstract module : allModules) {
                if (moduleName.equals(module.getName())) {
                    moduleFound = module;
                    break;
                }
            }
            if (moduleFound != null) {
                enabledModules.add(moduleFound);
            }
            else {
                messages.add(moduleName + " was previously enabled, but could not be found");
            }
        }        
        ingestDialogPanel.setEnabledIngestModules(enabledModules);                            

        // If there is no process unallocated space flag setting, default it to false.
        if (ModuleSettings.settingExists(moduleContext, PARSE_UNALLOC_SPACE_KEY) == false) {
            ModuleSettings.setConfigSetting(moduleContext, PARSE_UNALLOC_SPACE_KEY, "false");
        }        
        
        // Get the process unallocated space flag setting and pass it to the UI component.
        boolean processUnalloc = Boolean.parseBoolean(ModuleSettings.getConfigSetting(moduleContext, PARSE_UNALLOC_SPACE_KEY));
        ingestDialogPanel.setProcessUnallocSpaceEnabled(processUnalloc);        
        
        return messages;
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
            manager.execute(modulesToStart, contentToIngest);
        }
    }

    @Override
    public boolean isIngestRunning() {
        return manager.isIngestRunning();
    }        
}
