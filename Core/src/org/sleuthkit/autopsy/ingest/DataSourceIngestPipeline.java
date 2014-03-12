/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import org.sleuthkit.datamodel.AbstractFile;

/**
 * RJCTODO
 */
public class DataSourceIngestPipeline {
    private List<DataSourceIngestModule> ingestModules = new ArrayList<>();

    DataSourceIngestPipeline(DataSourceIngestJob ingestJob, List<IngestModuleTemplate> moduleTemplates) {
        for (IngestModuleTemplate template : moduleTemplates) {
            IngestModuleFactory moduleFactory = template.getIngestModuleFactory();
            if (moduleFactory.isDataSourceIngestModuleFactory()) {
                IngestModuleIngestJobOptions ingestOptions = template.getIngestOptions();
                DataSourceIngestModule module = moduleFactory.createDataSourceIngestModule(ingestOptions);
                module.startUp(new IngestModuleProcessingContext(ingestJob, moduleFactory));
                ingestModules.add(module);
            }
        }
    }

    void ingestFile(AbstractFile file) {
        for (FileIngestModule module : ingestModules) {
            module.process(file);
        }
        file.close();
    }

    void shutDown(boolean ingestJobCancelled) {
        for (FileIngestModule module : ingestModules) {
            module.shutDown(ingestJobCancelled);
        }        
    }    
}
