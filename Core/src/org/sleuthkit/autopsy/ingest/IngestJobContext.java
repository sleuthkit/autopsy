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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Provides an instance of an ingest module with services specific to the ingest
 * job of which the module is a part.
 */
public final class IngestJobContext {

    private static final Logger logger = Logger.getLogger(IngestJobContext.class.getName());
    private static final IngestScheduler scheduler = IngestScheduler.getInstance();
    private final IngestJob ingestJob;

    IngestJobContext(IngestJob ingestJob) {
        this.ingestJob = ingestJob;
    }

    /**
     * Gets the data source associated with this context.
     *
     * @return The data source.
     */
    public Content getDataSource() {
        return ingestJob.getDataSource();
    }

    /**
     * Gets the identifier of the ingest job associated with this context.
     *
     * @return The ingest job identifier.
     */
    public long getJobId() {
        return this.ingestJob.getId();
    }

    /**
     * Queries whether or not cancellation of the data source ingest part of the
     * ingest job associated with this context has been requested.
     *
     * @return True or false.
     * @deprecated Use dataSourceIngestIsCancelled() or fileIngestIsCancelled()
     * or jobIsCancelled()
     */
    @Deprecated
    public boolean isJobCancelled() {
        return this.dataSourceIngestIsCancelled();
    }

    /**
     * Queries whether or not cancellation of the data source ingest part of the
     * ingest job associated with this context has been requested.
     *
     * @return True or false.
     */
    public boolean dataSourceIngestIsCancelled() {
        // For a data source ingest module, both a pipeline interrupt and data 
        // source ingest cancellation require the same response - the module
        // should exit from its process() method as soon as possible.
        return this.ingestJob.dataSourceIngestPipelineIsInterrupted() || this.ingestJob.dataSourceIngestIsCancelled();
    }

    /**
     * Queries whether or not cancellation of the file ingest part of the ingest
     * job associated with this context has been requested.
     *
     * @return True or false.
     */
    public boolean fileIngestIsCancelled() {
        return this.ingestJob.fileIngestIsCancelled();
    }

    /**
     * Queries whether or not cancellation of the ingest job associated with
     * this context has been requested.
     *
     * @return True or false.
     */
    public boolean jobIsCancelled() {
        return this.ingestJob.jobIsCancelled();
    }

    /**
     * Queries whether or not unallocated space should be processed for the
     * ingest job associated with this context.
     *
     * @return True or false.
     */
    public boolean processingUnallocatedSpace() {
        return this.ingestJob.shouldProcessUnallocatedSpace();
    }

    /**
     * Adds one or more files to the files to be passed through the file ingest
     * pipeline of the ingest job associated with this context.
     *
     * @param files The files to be processed by the file ingest pipeline.
     */
    public void scheduleFiles(List<AbstractFile> files) {
        for (AbstractFile file : files) {
            try {
                IngestJobContext.scheduler.scheduleAdditionalFileIngestTask(ingestJob, file);
            } catch (InterruptedException ex) {
                // Ultimately, this method is called by ingest task execution
                // threads running ingest module code. Handle the unexpected
                // interrupt here rather
                Thread.currentThread().interrupt();
                IngestJobContext.logger.log(Level.SEVERE, "File task scheduling unexpectedly interrupted", ex); //NON-NLS
            }
        }
    }

}
