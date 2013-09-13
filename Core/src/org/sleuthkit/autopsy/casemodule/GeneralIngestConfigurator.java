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

package org.sleuthkit.autopsy.casemodule;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.ingest.IngestDialogPanel;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstract;
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
        this.moduleContext = IngestManager.MODULE_PROPERTIES; // Hard-code this for now.
        ingestDialogPanel = new IngestDialogPanel();
        ingestDialogPanel.setContext(moduleContext);
        manager = IngestManager.getDefault();
        loadSettings();
    }
    
    @Override
    public void setContext(String context) {
        moduleContext = context;
        ingestDialogPanel.setContext(moduleContext);
        reload();
    }
    
    @Override
    public JPanel getIngestConfigPanel() {
        return ingestDialogPanel;
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

        // Start the ingest.
        if (!modulesToStart.isEmpty()) {
            manager.execute(modulesToStart, contentToIngest);
        }
    }

    @Override
    public void save() {
        // Save the user's configuration of the currently selected ingest module.
        IngestModuleAbstract currentModule = ingestDialogPanel.getCurrentIngestModule();
        if (currentModule != null && currentModule.hasSimpleConfiguration()) {
            currentModule.saveSimpleConfiguration();
        }
        
        // Save the user's configuration of the set of enabled ingest modules.
        String enabledModulesCsvList = moduleListToCsv(ingestDialogPanel.getModulesToStart());
        ModuleSettings.setConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY, enabledModulesCsvList);
        
        // Save the user's general ingest configuration.
        String processUnalloc = Boolean.toString(ingestDialogPanel.processUnallocSpaceEnabled());
        ModuleSettings.setConfigSetting(moduleContext, PARSE_UNALLOC_SPACE_KEY, processUnalloc);
    }

    @Override
    public void reload() {
        loadSettings();
    }

    @Override
    public boolean isIngestRunning() {
        return manager.isIngestRunning();
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
    
    private static List<IngestModuleAbstract> csvToModuleList(String csv) {
        List<IngestModuleAbstract> modules = new ArrayList<>();
        
        if (csv == null || csv.isEmpty()) {
            return modules;
        }
        
        String[] moduleNames = csv.split(", ");
        List<IngestModuleAbstract> allModules = IngestManager.getDefault().enumerateAllModules();
        for (String moduleName : moduleNames) {
            boolean moduleFound = false;
            for (IngestModuleAbstract module : allModules) {
                if (moduleName.equals(module.getName())) {
                    modules.add(module);
                    moduleFound = true;
                    break;
                }
            }
            if (moduleFound == false) {
                JOptionPane.showMessageDialog(null, "Failed to find and load " + moduleName + " module", "Ingest Module Not Found", JOptionPane.ERROR_MESSAGE);
            }
        }
        
        return modules;
    }

    private void loadSettings() {
        loadEnabledIngestModulesSetting();
        
        boolean processUnalloc = Boolean.parseBoolean(ModuleSettings.getConfigSetting(moduleContext, PARSE_UNALLOC_SPACE_KEY));
        ingestDialogPanel.setProcessUnallocSpaceEnabled(processUnalloc);        
    }
    
    private void loadEnabledIngestModulesSetting() {
        // If there is no enabled ingest modules setting for this user, default to enabling all
        // of the ingest modules the IngestManager has loaded.
        if (ModuleSettings.settingExists(moduleContext, ENABLED_INGEST_MODULES_KEY) == false) {
            String defaultSetting = moduleListToCsv(IngestManager.getDefault().enumerateAllModules());
            ModuleSettings.setConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY, defaultSetting);
        }        
        
        String enabledModulesSetting = ModuleSettings.getConfigSetting(moduleContext, ENABLED_INGEST_MODULES_KEY);
        ingestDialogPanel.setEnabledIngestModules(csvToModuleList(enabledModulesSetting));                            
    }
}
