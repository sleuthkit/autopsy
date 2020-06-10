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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Provides an ingest module with services specific to the ingest job of which
 * the module is a part.
 */
public final class IngestJobContext {

    private final IngestJobPipeline ingestJobPipeline;

    IngestJobContext(IngestJobPipeline ingestJobPipeline) {
        this.ingestJobPipeline = ingestJobPipeline;
    }

    /**
     * Gets the ingest job execution context identifier.
     *
     * @return The context string.
     */
    public String getExecutionContext() {
        return this.ingestJobPipeline.getExecutionContext();
    }
        
    /**
     * Gets the data source associated with this context.
     *
     * @return The data source.
     */
    public Content getDataSource() {
        return this.ingestJobPipeline.getDataSource();
    }

    /**
     * Gets the identifier of the ingest job associated with this context.
     *
     * @return The ingest job identifier.
     */
    public long getJobId() {
        return this.ingestJobPipeline.getId();
    }

    /**
     * Queries whether or not cancellation of the data source ingest part of the
     * ingest job associated with this context has been requested.
     *
     * @return True or false.
     *
     * @deprecated Use dataSourceIngestIsCancelled() or fileIngestIsCancelled()
     * instead.
     */
    @Deprecated
    public boolean isJobCancelled() {
        return this.dataSourceIngestIsCancelled();
    }

    /**
     * Allows a data source ingest module to determine whether or not
     * cancellation of the data source ingest part of the ingest job associated
     * with this context has been requested.
     *
     * @return True or false.
     */
    public boolean dataSourceIngestIsCancelled() {
        return this.ingestJobPipeline.currentDataSourceIngestModuleIsCancelled() || this.ingestJobPipeline.isCancelled();
    }

    /**
     * Allows a file ingest module to determine whether or not cancellation of
     * the file ingest part of the ingest job associated with this context has
     * been requested.
     *
     * @return True or false.
     */
    public boolean fileIngestIsCancelled() {
        return this.ingestJobPipeline.isCancelled();
    }

    /**
     * Queries whether or not unallocated space should be processed for the
     * ingest job associated with this context.
     *
     * @return True or false.
     */
    public boolean processingUnallocatedSpace() {
        return this.ingestJobPipeline.shouldProcessUnallocatedSpace();
    }

    /**
     * Adds one or more files, i.e., extracted or carved files, to the ingest
     * job associated with this context.
     *
     * @param files The files to be added.
     *
     * @deprecated use addFilesToJob() instead
     */
    @Deprecated
    public void scheduleFiles(List<AbstractFile> files) {
        this.addFilesToJob(files);
    }

    /**
     * Adds one or more files, i.e., extracted or carved files, to the ingest
     * job associated with this context.
     *
     * @param files The files to be added.
     */
    public void addFilesToJob(List<AbstractFile> files) {
        this.ingestJobPipeline.addFiles(files);
    }

}
