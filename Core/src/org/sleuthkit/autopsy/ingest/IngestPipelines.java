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

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.autopsy.ingest.IngestScheduler.FileScheduler.FileTask;

/**
 * RJCTODO: 
 */
public class IngestPipelines {
    private final long dataSourceTaskId;
    private List<IngestModuleTemplate> fileIngestPipelineTemplate = new ArrayList<>();
    private List<IngestModuleTemplate> dataSourceIngestPipelineTemplate = new ArrayList<>();
    
    IngestPipelines(long dataSourceTaskId, List<IngestModuleTemplate> moduleTemplates) {
        this.dataSourceTaskId = dataSourceTaskId;
        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
            if (moduleTemplate.getIngestModuleFactory().isFileIngestModuleFactory()) {
                fileIngestPipelineTemplate.add(moduleTemplate);
            }
            else {
                dataSourceIngestPipelineTemplate.add(moduleTemplate);                
            }
        }
    }    

    DataSourceIngestPipeline getDataSourceIngestPipeline() {
        return new DataSourceIngestPipeline();
    }
            
    FileIngestPipeline getFileIngestPipeline() {
        return new FileIngestPipeline();
    }
        
    public class DataSourceIngestPipeline {
        private List<DataSourceIngestModule> modules = new ArrayList<>();        

        private DataSourceIngestPipeline() {
            try {
                for (IngestModuleTemplate moduleTemplate : dataSourceIngestPipelineTemplate) {
                    IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                    Serializable ingestOptions = moduleTemplate.getIngestOptions();
                    DataSourceIngestModule module = moduleFactory.createDataSourceIngestModule(ingestOptions);
                    module.init(dataSourceTaskId);
                    modules.add(module);   
                }  
            }
            catch (IngestModuleFactory.InvalidOptionsException ex) {
                // RJCTODO: Is this a stopper condition? What about init?
            }
        }                
    }
        
    public class FileIngestPipeline {
        private List<FileIngestModule> modules = new ArrayList<>();

        private FileIngestPipeline() {
            try {
                for (IngestModuleTemplate moduleTemplate : fileIngestPipelineTemplate) {
                    IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                    Serializable ingestOptions = moduleTemplate.getIngestOptions();
                    FileIngestModule module = moduleFactory.createFileIngestModule(ingestOptions);
                    module.init(dataSourceTaskId);
                    modules.add(module);   
                }  
            }
            catch (IngestModuleFactory.InvalidOptionsException ex) {
                // RJCTODO: Is this a stopper condition? What about init?
            }
        }        
        
        void doTask(FileTask fileTask) {
                final DataSourceTask<IngestModuleAbstractFile> dataSourceTask = fileTask.getDataSourceTask();                
                final AbstractFile fileToProcess = fileTask.getFile();
                
                fileToProcess.close();                            
        }
    }    
}
