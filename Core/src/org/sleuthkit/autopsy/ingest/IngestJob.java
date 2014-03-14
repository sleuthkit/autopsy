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
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.sleuthkit.datamodel.Content;

/**
 * Encapsulates a data source and the ingest module pipelines to be used to
 * ingest the data source.
 */
class IngestJob {

    private final long id;
    private final Content dataSource;
    private final List<IngestModuleTemplate> ingestModuleTemplates;
    private final boolean processUnallocatedSpace;
    private final HashMap<Long, FileIngestPipeline> fileIngestPipelines = new HashMap<>();
    private final HashMap<Long, DataSourceIngestPipeline> dataSourceIngestPipelines = new HashMap<>();
    private FileIngestPipeline initialFileIngestPipeline = null;
    private DataSourceIngestPipeline initialDataSourceIngestPipeline = null;
    private volatile boolean cancelled;

    IngestJob(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.ingestModuleTemplates = ingestModuleTemplates;
        this.processUnallocatedSpace = processUnallocatedSpace;
        this.cancelled = false;
    }

    long getId() {
        return id;
    }

    Content getDataSource() {
        return dataSource;
    }

    boolean getProcessUnallocatedSpace() {
        return this.processUnallocatedSpace;
    }

    synchronized void cancel() {
        this.cancelled = true;
    }

    synchronized boolean isCancelled() { // RJCTODO: It seems like this is only used in the pipelines, where it no longer belongs, I think...
        return this.cancelled;
    }

    synchronized List<IngestModuleError> startUpIngestPipelines() throws Exception {
        // Create at least one instance of each pipeline type now to make 
        // reasonably sure the ingest modules can be started.
        List<IngestModuleError> errors = new ArrayList<>();
        errors.addAll(this.initialDataSourceIngestPipeline.startUp());
        errors.addAll(this.initialFileIngestPipeline.startUp());
        return errors;
    }

    synchronized FileIngestPipeline getFileIngestPipelineForThread(long threadId) {
        FileIngestPipeline pipeline;
        if (null != this.initialFileIngestPipeline) {
            pipeline = this.initialFileIngestPipeline;
            this.initialDataSourceIngestPipeline = null;
            fileIngestPipelines.put(threadId, pipeline);
        } else if (!fileIngestPipelines.containsKey(threadId)) {
            pipeline = new FileIngestPipeline(this, this.ingestModuleTemplates);
            fileIngestPipelines.put(threadId, pipeline);
        } else {
            pipeline = fileIngestPipelines.get(threadId);
        }
        return pipeline;
    }

    synchronized DataSourceIngestPipeline getDataSourceIngestPipelineForThread(long threadId) {
        DataSourceIngestPipeline pipeline;
        if (null != this.initialDataSourceIngestPipeline) {
            pipeline = this.initialDataSourceIngestPipeline;
            this.initialDataSourceIngestPipeline = null;
            dataSourceIngestPipelines.put(threadId, pipeline);
        } else if (!dataSourceIngestPipelines.containsKey(threadId)) {
            pipeline = new DataSourceIngestPipeline(this, this.ingestModuleTemplates);
            dataSourceIngestPipelines.put(threadId, pipeline);
        } else {
            pipeline = dataSourceIngestPipelines.get(threadId);
        }
        return pipeline;
    }

    synchronized List<IngestModuleError> releaseIngestPipelinesForThread(long threadId) {
        List<IngestModuleError> errors = new ArrayList<>();

        DataSourceIngestPipeline dataSourceIngestPipeline = dataSourceIngestPipelines.get(threadId);
        if (dataSourceIngestPipeline != null) {
            errors.addAll(dataSourceIngestPipeline.shutDown(this.cancelled));
        }
        this.dataSourceIngestPipelines.remove(threadId);

        FileIngestPipeline fileIngestPipeline = fileIngestPipelines.get(threadId);
        if (fileIngestPipeline != null) {
            errors.addAll(fileIngestPipeline.shutDown(this.cancelled));
        }
        this.fileIngestPipelines.remove(threadId);

        return errors;
    }

    synchronized boolean arePipelinesShutDown() {
        return (dataSourceIngestPipelines.isEmpty() && fileIngestPipelines.isEmpty());
    }
}
