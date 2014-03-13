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

    synchronized void startUpIngestPipelines() throws Exception {
        // Create at least one instance of each pipeline type now to make 
        // reasonably sure the ingest modules can be started.
        this.initialDataSourceIngestPipeline.startUp();
        this.initialFileIngestPipeline.startUp();
    }

    synchronized FileIngestPipeline getFileIngestPipeline(long threadId) {
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

    synchronized DataSourceIngestPipeline getDataSourceIngestPipeline(long threadId) {
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

    synchronized void shutDownIngestPipelines(boolean ingestJobCancelled) { // RJCTODO: Do away with this flag, put cancelled in job?
        for (DataSourceIngestPipeline pipeline : dataSourceIngestPipelines.values()) {
            pipeline.shutDown(ingestJobCancelled);
        }
        for (FileIngestPipeline pipeline : fileIngestPipelines.values()) {
            pipeline.shutDown(ingestJobCancelled);
        }
    }

    synchronized void markAsCancelled() {
        this.cancelled = true;
    }

    synchronized boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public String toString() {
        return "ScheduledTask{ id=" + this.id + ", dataSource=" + this.dataSource + '}';
    }

    // RJCTODO: This is not sufficient! Perhaps this is also not needed?
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final IngestJob other = (IngestJob) obj;
        if (this.dataSource != other.dataSource && (this.dataSource == null || !this.dataSource.equals(other.dataSource))) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 61 * hash + (int) (this.id ^ (this.id >>> 32));
        hash = 61 * hash + Objects.hashCode(this.dataSource);
        hash = 61 * hash + (this.processUnallocatedSpace ? 1 : 0);
        return hash;
    }
}
