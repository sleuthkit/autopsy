/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.sleuthkit.datamodel.Content;

/**
 * Ingest module that acts on entire image. 
 * These modules are for analysis tasks that do not operate on all files in the disk image.
 * A new instance of this module will be created for each image.  
 * Therefore, image-level modules can assume that the process() method will be called at most once after init() is called. 
 */
public abstract class IngestModuleDataSource extends IngestModuleAbstract {

    @Override
    public ModuleType getType() {
        return ModuleType.DataSource;
    }
    
    /**
     * Called with the data source Content object to analyze. 
     * 
     * Modules typically use FileManager to get specific files to analyze.  
     * 
     * Results should be posted to the blackboard. 
     * The module should also send messages to the ingest inbox with interesting events (data, errors, warnings, infos).
     * The module notifies data viewers by firing events using IngestManagerProxy.fireModuleDataEvent
     * 
     * The module will have its own progress bar while it is running and it should update it with the IngestDataSourceWorkerController object. 
     * 
     * @param pipelineContext Context in which the ingest pipeline is running (Settings, modules, etc)
     * @param dataSource data source to process (such as Image, VirtualDirectory for file etc, etc)
     * @param controller Used to update progress bar and to check if the task has been canceled. 
     */
    abstract public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller);
}
