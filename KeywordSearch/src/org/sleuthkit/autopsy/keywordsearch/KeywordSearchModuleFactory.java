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

package org.sleuthkit.autopsy.keywordsearch;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSetttingsPanel;

/**
 * An ingest module factory that creates file ingest modules that do keyword 
 * searching.
 */
@ServiceProvider(service=IngestModuleFactory.class)
public class KeywordSearchModuleFactory extends IngestModuleFactoryAdapter {
    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }
    
    static String getModuleName() {
        return NbBundle.getMessage(KeywordSearchIngestModule.class, "KeywordSearchIngestModule.moduleName");        
    }
    
    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(KeywordSearchIngestModule.class, "KeywordSearchIngestModule.moduleDescription");        
    }
    
    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();        
    }
       
    @Override
    public boolean hasModuleSettingsPanel() {
        return true;
    }
    
    @Override
    public IngestModuleIngestJobSettingsPanel getModuleSettingsPanel(IngestModuleIngestJobSettings ingestJobOptions) {
        KeywordSearchJobSettingsPanel ingestOptionsPanel = new KeywordSearchJobSettingsPanel();  
        ingestOptionsPanel.load();
        return ingestOptionsPanel; 
    }
    
    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;    
    }
    
    @Override
    public IngestModuleGlobalSetttingsPanel getGlobalSettingsPanel() {
        KeywordSearchGlobalSettingsPanel globalOptionsPanel = new KeywordSearchGlobalSettingsPanel();
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }    
    
    @Override
    public boolean isFileIngestModuleFactory() {
        return true;            
    }
    
    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings ingestJobOptions) {
        return new KeywordSearchIngestModule();
    }
}
