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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * RJCTODO
 */
class IngestPipelines {

    private final DataSourceIngestJob ingestJob;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private FileIngestPipeline initialFileIngestPipeline = null;
    private DataSourceIngestPipeline initialDataSourceIngestPipeline = null;
    private final ConcurrentHashMap<Integer, FileIngestPipeline> fileIngestPipelines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, DataSourceIngestPipeline> dataSourceIngestPipelines = new ConcurrentHashMap<>();

    IngestPipelines(DataSourceIngestJob ingestJob, List<IngestModuleTemplate> ingestModuleTemplates) {
        this.ingestJob = ingestJob;
        this.ingestModuleTemplates = ingestModuleTemplates;
        this.initialDataSourceIngestPipeline = null;
        this.initialFileIngestPipeline = null;
    }

    // RJCTODO: Added provisionally, should not need, may need way to check if pipelines are running
    List<IngestModuleTemplate> getIngestModuleTemplates() {
        return ingestModuleTemplates;
    }

    void startUp() {
        // RJCTODO: Add error handling for module startup failure
        // Create at least one instance of each pipeline type now to make sure
        // the ingest modules can be started.
        this.initialDataSourceIngestPipeline = new DataSourceIngestPipeline(this.ingestJob, this.ingestModuleTemplates);
        this.initialFileIngestPipeline = new FileIngestPipeline(this.ingestJob, this.ingestModuleTemplates);
    }

    void ingestFile(int threadId, AbstractFile file) {
        FileIngestPipeline pipeline;
        if (null != this.initialFileIngestPipeline) {
            pipeline = this.initialFileIngestPipeline;
            this.initialDataSourceIngestPipeline = null;
            fileIngestPipelines.put(threadId, pipeline);
        } else if (!fileIngestPipelines.containsKey(threadId)) {
            pipeline = new FileIngestPipeline(this.ingestJob, this.ingestModuleTemplates);
            fileIngestPipelines.put(threadId, pipeline);
        } else {
            pipeline = fileIngestPipelines.get(threadId);
        }
        pipeline.ingestFile(file);
    }

    void shutDown(boolean ingestJobCancelled) { // RJCTODO: Do away with this flag, put cancelled in job?
        for (FileIngestPipeline pipeline : fileIngestPipelines.values()) {
            pipeline.shutDown(ingestJobCancelled);
        }
    }
}
