/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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

    private final IngestJobExecutor ingestJobExecutor;

    /**
     * Constructs an ingest job context object that provides an ingest module
     * with services specific to the ingest job of which the module is a part.
     *
     * @param ingestJobExecutor The ingest executor for the job.
     */
    IngestJobContext(IngestJobExecutor ingestJobExecutor) {
        this.ingestJobExecutor = ingestJobExecutor;
    }

    /**
     * Gets the execution context identifier of the ingest job.
     *
     * @return The context string.
     */
    public String getExecutionContext() {
        return ingestJobExecutor.getExecutionContext();
    }

    /**
     * Gets the data source for the ingest job.
     *
     * @return The data source.
     */
    public Content getDataSource() {
        return ingestJobExecutor.getDataSource();
    }

    /**
     * Gets the unique identifier for the ingest job.
     *
     * @return The ID.
     */
    public long getJobId() {
        return ingestJobExecutor.getIngestJobId();
    }

    /**
     * Indicates whether or not cancellation of the ingest job has been
     * requested.
     *
     * @return True or false.
     *
     * @deprecated Modules should call a type-specific cancellation check method
     * instead.
     */
    @Deprecated
    public boolean isJobCancelled() {
        return ingestJobExecutor.isCancelled();
    }

    /**
     * Indicates whether or not cancellation of the currently running data
     * source level ingest module has been requested. Data source level ingest
     * modules should check this periodically and break off processing if the
     * method returns true.
     *
     * @return True or false.
     */
    public boolean dataSourceIngestIsCancelled() {
        return ingestJobExecutor.currentDataSourceIngestModuleIsCancelled() || ingestJobExecutor.isCancelled();
    }

    /**
     * Indicates whether or not cancellation of the currently running file level
     * ingest module has been requested. File level ingest modules should check
     * this periodically and break off processing if the method returns true.
     *
     * @return True or false.
     */
    public boolean fileIngestIsCancelled() {
        /*
         * It is not currently possible to cancel individual file ingest
         * modules.
         */
        return ingestJobExecutor.isCancelled();
    }

    /**
     * Checks whether or not cancellation of the currently running data artifact
     * ingest module for the ingest job has been requested. Data artifact ingest
     * modules should check this periodically and break off processing if the
     * method returns true.
     *
     * @return True or false.
     */
    public boolean dataArtifactIngestIsCancelled() {
        /*
         * It is not currently possible to cancel individual data artifact
         * ingest modules.
         */
        return ingestJobExecutor.isCancelled();
    }

    /**
     * Queries whether or not unallocated space should be processed for the
     * ingest job.
     *
     * @return True or false.
     */
    public boolean processingUnallocatedSpace() {
        return ingestJobExecutor.shouldProcessUnallocatedSpace();
    }

    /**
     * Adds one or more files, i.e., extracted or carved files, to the ingest
     * job associated with this context.
     *
     * @param files The files to be added.
     *
     * @deprecated use addFilesToJob() instead.
     */
    @Deprecated
    public void scheduleFiles(List<AbstractFile> files) {
        addFilesToJob(files);
    }

    /**
     * Adds one or more files, e.g., extracted or carved files, to the ingest
     * job for processing by its file ingest modules.
     *
     * @param files The files.
     */
    public void addFilesToJob(List<AbstractFile> files) {
        ingestJobExecutor.addFiles(files);
    }

}
