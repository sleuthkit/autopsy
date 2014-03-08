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
import java.util.concurrent.ConcurrentHashMap;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * RJCTODO
 */
class IngestPipelines {

    private final long dataSourceTaskId;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private final ConcurrentHashMap<Integer, FileIngestPipeline> fileIngestPipelines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, DataSourceIngestPipeline> dataSourceIngestPipelines = new ConcurrentHashMap<>();

    IngestPipelines(long dataSourceTaskId, final List<IngestModuleTemplate> ingestModuleTemplates) {
        this.dataSourceTaskId = dataSourceTaskId;
        this.ingestModuleTemplates = ingestModuleTemplates;
    }

    // RJCTODO: Added provisionally
    List<IngestModuleTemplate> getIngestModuleTemplates() {
        return ingestModuleTemplates;
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

    void ingestDataSource(int threadId, Content dataSource) {
        DataSourceIngestPipeline pipeline;
        if (!dataSourceIngestPipelines.containsKey(threadId)) {
            pipeline = new DataSourceIngestPipeline();
            dataSourceIngestPipelines.put(threadId, pipeline);
        } else {
            pipeline = dataSourceIngestPipelines.get(threadId);
        }
        pipeline.ingestDataSource(dataSource);
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
            try {
                for (IngestModuleTemplate moduleTemplate : ingestModuleTemplates) {
                    IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                    if (moduleFactory.isFileIngestModuleFactory()) {
                        IngestModuleIngestJobOptions ingestOptions = moduleTemplate.getIngestOptions();
                        FileIngestModule module = moduleFactory.createFileIngestModule(ingestOptions);
                        module.init(dataSourceTaskId);
                        ingestModules.add(module);
                    }
                }
            } catch (IngestModuleFactory.InvalidOptionsException ex) {
                // RJCTODO: Is this a stopper condition? What about init?
            }
        }

        void init() {
            for (FileIngestModule module : ingestModules) {
                module.init(dataSourceTaskId);
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
                module.jobCancelled();
            }
        }

        void complete() {
            for (FileIngestModule module : ingestModules) {
                module.jobCompleted();
            }
        }
    }

    private class DataSourceIngestPipeline {

        private final List<DataSourceIngestModule> modules = new ArrayList<>();

        private DataSourceIngestPipeline() {
            try {
                for (IngestModuleTemplate moduleTemplate : ingestModuleTemplates) {
                    IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
                    if (moduleFactory.isDataSourceIngestModuleFactory()) {
                        IngestModuleIngestJobOptions ingestOptions = moduleTemplate.getIngestOptions();
                        DataSourceIngestModule module = moduleFactory.createDataSourceIngestModule(ingestOptions);
                        module.init(dataSourceTaskId);
                        modules.add(module);
                    }
                }
            } catch (IngestModuleFactory.InvalidOptionsException ex) {
                // RJCTODO: Is this a stopper condition? What about trial init?
            }
        }

        void ingestDataSource(Content dataSource) {
            // RJCTODO
        }
    }
}
