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

package org.sleuthkit.autopsy.ingest;

import java.io.Serializable;
import javax.swing.JPanel;

/**
 * An abstract class that provides no-op implementations of various 
 * IngestModuleFactory methods. Provided for the convenience of ingest module
 * developers.
 */
public abstract class AbstractIngestModuleFactory implements IngestModuleFactory {
        
    @Override
    public abstract String getModuleDisplayName();
    
    @Override
    public abstract String getModuleDescription();
    
    @Override
    public abstract String getModuleVersionNumber();
    
    @Override
    public Serializable getDefaultIngestOptions() {
        return new EmptyIngestOptions();
    }
   
    @Override
    public boolean providesIngestOptionsPanels() {
        return false;
    }
    
    @Override
    public JPanel getIngestOptionsPanel(Serializable ingestOptions) {
        throw new UnsupportedOperationException(); 
    }
            
    @Override
    public Serializable getIngestOptionsFromPanel(JPanel ingestOptionsPanel) throws InvalidOptionsException {
        throw new UnsupportedOperationException();         
    }
    
    @Override
    public boolean providesGlobalOptionsPanels() {
        return false;
    }
    
    @Override
    public JPanel getGlobalOptionsPanel() {
        throw new UnsupportedOperationException();         
    }
    
    @Override
    public void saveGlobalOptionsFromPanel(JPanel globalOptionsPanel) throws InvalidOptionsException {
        throw new UnsupportedOperationException();         
    }

    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return false;
    }
                
    @Override
    public DataSourceIngestModule createDataSourceIngestModule(Serializable ingestOptions) throws InvalidOptionsException {
        throw new UnsupportedOperationException();         
    }
    
    @Override
    public boolean isFileIngestModuleFactory() {
        return false;
    }
    
    @Override
    public FileIngestModule createFileIngestModule(Serializable ingestOptions) throws InvalidOptionsException {
        throw new UnsupportedOperationException();         
    }
    
    public static class EmptyIngestOptions implements Serializable {
    }    
}
