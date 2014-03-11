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
import java.util.concurrent.ConcurrentHashMap;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * RJCTODO
 */
class IngestPipelines {

    private final long ingestJobId;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private final ConcurrentHashMap<Integer, FileIngestPipeline> fileIngestPipelines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, DataSourceIngestPipeline> dataSourceIngestPipelines = new ConcurrentHashMap<>();

    IngestPipelines(long ingestJobId, List<IngestModuleTemplate> ingestModuleTemplates) {
        this.ingestJobId = ingestJobId;
        this.ingestModuleTemplates = ingestModuleTemplates;
    }

    // RJCTODO: Added provisionally, should not need, may need way to check if pipelines are running
    List<IngestModuleTemplate> getIngestModuleTemplates() {
        return ingestModuleTemplates;
    }

    void startUp() {
        for (FileIngestPipeline pipeline : fileIngestPipelines.values()) {
            pipeline.stop();
        }        
    }
    
    void ingestFile(int threadId, AbstractFile file) {
        FileIngestPipeline pipeline;
        if (!fileIngestPipelines.containsKey(threadId)) {
            pipeline = new FileIngestPipeline();
            fileIngestPipelines.put(threadId, pipeline);
        } else {
            pipeline = fileIngestPipelines.get(threadId);
        }
        pipeline.ingestFile(file);
    }

    void stopFileIngestPipeline() {
        // RJCTODO        
        for (FileIngestPipeline pipeline : fileIngestPipelines.values()) {
            pipeline.stop();
        }
    }

    void completeFileIngestPipeline() {
        // RJCTODO        
        for (FileIngestPipeline pipeline : fileIngestPipelines.values()) {
            pipeline.complete();
        }
    }

    private class FileIngestPipeline {

        private List<FileIngestModule> ingestModules = new ArrayList<>();

        private FileIngestPipeline() {
            for (IngestModuleTemplate moduleTemplate : ingestModuleTemplates) {
                IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                if (moduleFactory.isFileIngestModuleFactory()) {
                    IngestModuleIngestJobOptions ingestOptions = moduleTemplate.getIngestOptions();
                    FileIngestModule module = moduleFactory.createFileIngestModule(ingestOptions);
                    module.startUp(new IngestModuleProcessingContext(ingestJobId, moduleFactory));
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

        void stop() {
            for (FileIngestModule module : ingestModules) {
                module.shutDown(true);
            }
        }

        void complete() {
            for (FileIngestModule module : ingestModules) {
                module.shutDown(false);
            }
        }
    }

    private class DataSourceIngestPipeline {

        private final List<DataSourceIngestModule> modules = new ArrayList<>();

        private DataSourceIngestPipeline() {
            for (IngestModuleTemplate moduleTemplate : ingestModuleTemplates) {
                IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                if (moduleFactory.isDataSourceIngestModuleFactory()) {
                    IngestModuleIngestJobOptions ingestOptions = moduleTemplate.getIngestOptions();
                    DataSourceIngestModule module = moduleFactory.createDataSourceIngestModule(ingestOptions);
                    module.startUp(new IngestModuleProcessingContext(ingestJobId, moduleFactory));
                    modules.add(module);
                }
            }
        }
    }
}
