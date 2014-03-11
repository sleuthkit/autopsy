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

import java.io.Serializable;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobOptions;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobOptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleResourcesConfigPanel;

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
        return NbBundle.getMessage(KeywordSearchIngestModule.class, "HashDbInKeywordSearchIngestModulegestModule.moduleDescription");        
    }
    
    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();        
    }
       
    @Override
    public boolean providesIngestJobOptionsPanels() {
        return true;
    }
    
    @Override
    public IngestModuleIngestJobOptionsPanel getIngestJobOptionsPanel(IngestModuleIngestJobOptions ingestJobOptions) {
        KeywordSearchIngestSimplePanel ingestOptionsPanel = new KeywordSearchIngestSimplePanel();  
        ingestOptionsPanel.load();
        return ingestOptionsPanel; 
    }
    
    @Override
    public boolean providesResourcesConfigPanels() {
        return true;    
    }
    
    @Override
    public IngestModuleResourcesConfigPanel getResourcesConfigPanel() {
        KeywordSearchConfigurationPanel globalOptionsPanel = new KeywordSearchConfigurationPanel();
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }    
    
    @Override
    public boolean isFileIngestModuleFactory() {
        return true;            
    }
    
    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobOptions ingestJobOptions) {
        return new KeywordSearchIngestModule();
    }
}
