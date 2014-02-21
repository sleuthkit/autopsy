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
 * Controller to allow a user to set context-sensitive ingest module options,
 * enable/disable ingest modules, and set general ingest options. Provides an 
 * ingest module module model class and instances of a UI component to its 
 * clients (Model-View-Controller design pattern).
 */
public class GeneralIngestConfigurator { 
    private static final String ENABLED_INGEST_MODULES_KEY = "Enabled_Ingest_Modules";
    private static final String DISABLED_INGEST_MODULES_KEY = "Disabled_Ingest_Modules";
    private static final String PARSE_UNALLOC_SPACE_KEY = "Process_Unallocated_Space";
    private final IngestManager ingestManager = IngestManager.getDefault();
    private String context = null;
    private boolean processUnallocatedSpace = false;
    private IngestConfigurationPanel ingestConfigPanel = null;
    private List<Content> contentToIngest = null; // RJCTODO: Remove if start() method removed
    
    public class IngestConfigurationException extends Exception {
        IngestConfigurationException(String message) {
            super(message);
        }        
    }
    
    /**
     * RJCTODO
     * @param context 
     */
    public GeneralIngestConfigurator() {
    }
    
    /**
     * RJCTODO
     * @param context
     * @return 
     */
    public List<String> setContext(String context) {
        this.context = context;
        return initializeForContext();
    }    
    
    private List<String> initializeForContext() {
        // Get the enabled and disabled ingest modules settings for the current
        // context. The default settings make all ingest modules enabled. 
        List<IngestModuleFactory> moduleFactories = IngestManager.getDefault().getIngestModuleFactories(); // RJCTODO: Put in uniqueness test in loader!
        HashSet<String> loadedModuleNames = new HashSet<>(); 
        for (IngestModuleFactory moduleFactory : moduleFactories) {            
            loadedModuleNames.add(moduleFactory.getModuleDisplayName());
        }       
        HashSet<String> enabledModuleNames = getModulesNamesFromSetting(ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(loadedModuleNames));        
        HashSet<String> disabledModuleNames = getModulesNamesFromSetting(DISABLED_INGEST_MODULES_KEY, "");        
        
        // Create ingest module templates for the ingest module pipelines and
        // wrap them in ingest module models to pass to the ingest configuration 
        // panel (view). The initial enabled/disabled state of the module models
        // comes from the context-sensitive settings.
        HashSet<String> knownModuleNames = new HashSet<>(); 
        List<IngestModuleModel> modules = new ArrayList<>();
        for (IngestModuleFactory moduleFactory : moduleFactories) {            
            // NOTE: In the future, this code will be modified to get the ingest 
            // options for each modules for the current context; for now just
            // get the default ingest options.
            IngestModuleTemplate moduleTemplate = new IngestModuleTemplate(moduleFactory, moduleFactory.getDefaultIngestOptions());             
            String moduleName = moduleFactory.getModuleDisplayName();            
            IngestModuleModel module = new IngestModuleModel(moduleTemplate, enabledModuleNames.contains(moduleName));
            if (!enabledModuleNames.contains(moduleName) && !enabledModuleNames.contains(moduleName)) {
                // The module factory was loaded, but the module name does not
                // appear in the enabled/disabled module settings. Treat the
                // module as a new module and enable it by default.
                module.setEnabled(true);
                enabledModuleNames.add(moduleName); // RJCTODO: Put in uniqueness test, i.e., check return value!
            }
            modules.add(module);
            knownModuleNames.add(moduleName);
        }
        
        // Check for missing modules and update the enabled/disabled ingest 
        // module settings. This way the settings for the context will be 
        // up-to-date, even if save() is never called.
        List<String> errorMessages = new ArrayList<>();  
        for (String moduleName : enabledModuleNames) {
            if (!knownModuleNames.contains(moduleName)) {
                errorMessages.add(moduleName + " was previously enabled, but could not be found");
                enabledModuleNames.remove(moduleName);
                disabledModuleNames.add(moduleName);                
            }
        }                
        ModuleSettings.setConfigSetting(context, ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(enabledModuleNames));
        ModuleSettings.setConfigSetting(context, DISABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(disabledModuleNames));                

        // Get the process unallocated space flag setting. If the setting does
        // not exist yet, default it to false.
        if (ModuleSettings.settingExists(context, PARSE_UNALLOC_SPACE_KEY) == false) {
            ModuleSettings.setConfigSetting(context, PARSE_UNALLOC_SPACE_KEY, "false");
        }                
        processUnallocatedSpace = Boolean.parseBoolean(ModuleSettings.getConfigSetting(context, PARSE_UNALLOC_SPACE_KEY));

        // Make the configuration panel for the current context (view).
        ingestConfigPanel = new IngestConfigurationPanel(modules, processUnallocatedSpace);
        
        return errorMessages;
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
        
    public JPanel getIngestConfigPanel() throws IngestConfigurationException {
        if (null == context || null == ingestConfigPanel) {
            throw new IngestConfigurationException("Ingest context not set");
        }
                
        return ingestConfigPanel;
    }    
    
    public void save() throws IngestConfigurationException {     
        if (null == context || null == ingestConfigPanel) {
            throw new IngestConfigurationException("Ingest context not set");
        }

        List<IngestModuleModel> modules = ingestConfigPanel.getIngestModules();
        
        // Save the enbaled/disabled ingest module settings for the current context.
        HashSet<String> enabledModuleNames = new HashSet<>();        
        HashSet<String> disabledModuleNames = new HashSet<>();        
        for (IngestModuleModel module : modules) {
            if (module.isEnabled()) {
                enabledModuleNames.add(module.getModuleName());
            }
            else {
                disabledModuleNames.add(module.getModuleName());
            }
        }               
        ModuleSettings.setConfigSetting(context, ENABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(enabledModuleNames));
        ModuleSettings.setConfigSetting(context, DISABLED_INGEST_MODULES_KEY, makeCommaSeparatedList(disabledModuleNames));                
        
        // Save the process unallocated space setting for this context.
        String processUnalloc = Boolean.toString(ingestConfigPanel.getProcessUnallocSpace());
        ModuleSettings.setConfigSetting(context, PARSE_UNALLOC_SPACE_KEY, processUnalloc);
        
        // Get the ingest module options for each ingest module.
        // NOTE: In the future, this code will be modified to persist the ingest 
        // options for each ingest module for the current context.        
        // RJCTODO: Decide whether to set the ingest options here or in the dialog; in the dialog allows corrections by user
//        if (currentModule != null && currentModule.hasSimpleConfiguration()) {
//            currentModule.saveSimpleConfiguration();
//        }        
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
        // Get the list of ingest modules selected by the user.
        // RJCTODO:
//        List<IngestModuleAbstract> modulesToStart = ingestConfigPanel.getModulesToStart();
        List<IngestModuleAbstract> modulesToStart = new ArrayList<>();
        
        // Get the user's selection of whether or not to process unallocated space.
        ingestManager.setProcessUnallocSpace(processUnallocatedSpace);

        if (!modulesToStart.isEmpty() && contentToIngest != null) {
            // Queue the ingest process.
            ingestManager.scheduleDataSource(modulesToStart, contentToIngest);
        }
    }

    // RJCTODO: If time permits, make it so that this class is not responsible
    // starting and running the ingest - probably need to do this anyway, at 
    // least if the IngestConfigurator interface goes away and this becomes the
    // IngestConfigurator class.
    public boolean isIngestRunning() {
        return ingestManager.isIngestRunning();
    }      
    
    /**
     * A model of an ingest module tailored for the view used to configure 
     * ingest modules. 
     */
    static class IngestModuleModel {
        private final IngestModuleTemplate moduleTemplate;
        private final IngestModuleFactory moduleFactory;
        private final JPanel ingestOptionsPanel;
        private final JPanel globalOptionsPanel;
        private boolean enabled = true;

        IngestModuleModel(IngestModuleTemplate moduleTemplate, boolean enabled) {
            this.moduleTemplate = moduleTemplate;
            moduleFactory = moduleTemplate.getIngestModuleFactory();
            if (moduleFactory.providesIngestOptionsPanels()) {
                ingestOptionsPanel = moduleFactory.getIngestOptionsPanel(moduleTemplate.getIngestOptions());
            }
            else {
                ingestOptionsPanel = null;
            }
            if (moduleFactory.providesGlobalOptionsPanels()) {
                globalOptionsPanel = moduleFactory.getGlobalOptionsPanel();
            }
            else {
                globalOptionsPanel = null;
            }
            this.enabled = enabled;
        }
                
        String getModuleName() {
            return moduleFactory.getModuleDisplayName();
        }
        
        String getModuleDescription() {
            return moduleFactory.getModuleDescription();
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        boolean isEnabled() {
            return enabled;
        }       

        boolean hasIngestOptionsPanel() {
            return moduleFactory.providesIngestOptionsPanels();
        }
        
        JPanel getIngestOptionsPanel() {
            return ingestOptionsPanel;
        }
        
        boolean hasGlobalOptionsPanel() {
            return moduleFactory.providesGlobalOptionsPanels();
        }
        
        JPanel getGlobalOptionsPanel() {
            return globalOptionsPanel;
        }      
        
        void saveGlobalOptions() throws IngestModuleFactory.InvalidOptionsException {
            // RJCTODO: Check for null.
            moduleFactory.saveGlobalOptionsFromPanel(globalOptionsPanel);
        }   
        
        private IngestModuleTemplate getIngestModuleTemplate() {
            return moduleTemplate;
        }
    }
}
