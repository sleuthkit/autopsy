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

import org.openide.util.NbBundle;
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
        List<String> messages = new ArrayList<>();  
        List<IngestModuleAbstract> allModules = IngestManager.getDefault().enumerateAllModules();
        
        // If there is no enabled ingest modules setting for this user, default to enabling all
        // of the ingest modules the IngestManager has loaded.
        if (ModuleSettings.settingExists(moduleContext, ENABLED_INGEST_MODULES_KEY) == false) {
            String defaultSetting = moduleListToCsv(allModules);
            ModuleSettings.setConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY, defaultSetting);
        }        
        
        String[] enabledModuleNames = ModuleSettings.getConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY).split(", ");
        ArrayList<String> enabledList = new ArrayList<>(Arrays.asList(enabledModuleNames));
        
        // Check for modules that are missing from the config file
        
        String[] disabledModuleNames = null;
        // Older config files won't have the disabled list, so don't assume it exists
        if (ModuleSettings.settingExists(moduleContext, DISABLED_INGEST_MODULES_KEY)) {
            disabledModuleNames = ModuleSettings.getConfigSetting(moduleContext, DISABLED_INGEST_MODULES_KEY).split(", ");
        }
        
        for (IngestModuleAbstract module : allModules) {
            boolean found = false;

            // Check enabled first
            for (String moduleName : enabledModuleNames) {
                if (module.getName().equals(moduleName)) {
                    found = true;
                    break;
                }
            }                

            // Then check disabled
            if (!found && (disabledModuleNames != null)) {
                for (String moduleName : disabledModuleNames) {
                    if (module.getName().equals(moduleName)) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                enabledList.add(module.getName());
                // It will get saved to file later
            }
        }            
        
        // Get the enabled ingest modules setting, check for missing modules, and pass the setting to
        // the UI component.
        List<IngestModuleAbstract> enabledModules = new ArrayList<>();
        for (String moduleName : enabledList) {
            if (moduleName.equals(
                    NbBundle.getMessage(this.getClass(), "GeneralIngestConfigurator.modName.tbirdParser.text"))
                    || moduleName.equals(
                    NbBundle.getMessage(this.getClass(), "GeneralIngestConfigurator.modName.mboxParser.text"))) {
                moduleName = NbBundle.getMessage(this.getClass(), "GeneralIngestConfigurator.modName.emailParser.text");
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
                messages.add(NbBundle.getMessage(this.getClass(), "GeneralIngestConfigurator.enabledMods.notFound.msg",
                                                 moduleName));
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
