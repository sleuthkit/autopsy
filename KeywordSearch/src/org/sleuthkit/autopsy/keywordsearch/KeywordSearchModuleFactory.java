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
    public Serializable getDefaultPerIngestJobOptions() {
        return new IngestOptions();        
    }
    
    @Override
    public boolean providesIngestOptionsPanels() {
        return true;
    }
    
    @Override
    public JPanel getIngestOptionsPanel(Serializable ingestOptions) {
        KeywordSearchIngestSimplePanel ingestOptionsPanel = new KeywordSearchIngestSimplePanel();  
        ingestOptionsPanel.load();
        return ingestOptionsPanel; 
    }
    
    @Override
    public Serializable getIngestOptionsFromPanel(JPanel ingestOptionsPanel) throws IngestModuleFactory.InvalidOptionsException {
        if (!(ingestOptionsPanel instanceof KeywordSearchIngestSimplePanel)) {
            throw new IllegalArgumentException("JPanel not a KeywordSearchIngestSimplePanel");
        }

        KeywordSearchIngestSimplePanel panel = (KeywordSearchIngestSimplePanel)ingestOptionsPanel;
        panel.store();
                
        return new IngestOptions(); // RJCTODO
    }
    
    @Override
    public boolean providesResourcesConfigPanels() {
        return true;    
    }
    
    @Override
    public JPanel getGlobalOptionsPanel() {
        KeywordSearchConfigurationPanel globalOptionsPanel = new KeywordSearchConfigurationPanel();
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }    
    
    @Override
    public void saveGlobalOptionsFromPanel(JPanel globalOptionsPanel) throws IngestModuleFactory.InvalidOptionsException {
        if (!(globalOptionsPanel instanceof KeywordSearchConfigurationPanel)) {
            throw new IngestModuleFactory.InvalidOptionsException(""); // RJCTODO
        }
        
        KeywordSearchConfigurationPanel panel = (KeywordSearchConfigurationPanel)globalOptionsPanel;
        panel.store();
    }
    
    @Override
    public boolean isFileIngestModuleFactory() {
        return true;            
    }
    
    @Override
    public FileIngestModule createFileIngestModule(Serializable ingestOptions) throws IngestModuleFactory.InvalidOptionsException {
        return new KeywordSearchIngestModule();
    }
    
    private static class IngestOptions implements Serializable {
        // RJCTODO: Any options here?
        //        boolean alwaysCalcHashes = true;
//        ArrayList<String> hashSetNames = new ArrayList<>();
    }    
}
