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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.sleuthkit.datamodel.Content;

/**
 * Runs a collection of data sources through a set of ingest modules specified
 * via ingest job settings.
 */
public final class IngestJob {

    private static final AtomicLong nextId = new AtomicLong(0L);
    private final long id;
    private final Map<Long, DataSourceIngestJob> dataSourceJobs;
    private boolean cancelled;

    /**
     * Constructs an ingest job that runs a collection of data sources through a
     * set of ingest modules specified via ingest job settings.
     *
     * @param dataSources The data sources to be ingested.
     * @param settings The ingest job settings.
     * @param doUI Whether or not to do UI interactions.
     */
    IngestJob(Collection<Content> dataSources, IngestJobSettings settings, boolean doUI) {
        this.id = IngestJob.nextId.getAndIncrement();
        this.dataSourceJobs = new HashMap<>();
        for (Content dataSource : dataSources) {
            DataSourceIngestJob dataSourceIngestJob = new DataSourceIngestJob(this, dataSource, settings, doUI);
            this.dataSourceJobs.put(dataSourceIngestJob.getId(), dataSourceIngestJob);
        }
    }

    /**
     * Gets the unique identifier assigned to this ingest job.
     *
     * @return The job identifier.
     */
    public long getId() {
        return this.id;
    }

    /**
     * Gets a snapshot of the state and performance of this ingest job.
     *
     * @return The snapshot.
     */
    synchronized public ProgressSnapshot getSnapshot() {
        /**
         * There are race conditions in the code that follows, but they are not
         * important because this is just a coarse-grained status report. If
         * stale data is returned in any single snapshot, it will be corrected
         * in subsequent snapshots.
         */
        DataSourceIngestModuleHandle moduleHandle = null;
        boolean fileIngestRunning = false;
        Date fileIngestStartTime = null;
        for (DataSourceIngestJob dataSourceJob : this.dataSourceJobs.values()) {
            DataSourceIngestPipeline.PipelineModule module = dataSourceJob.getCurrentDataSourceIngestModule();
            if (null != module) {
                moduleHandle = new DataSourceIngestModuleHandle(dataSourceJob.getId(), module);
            }

            // RJCTODO: For each data source job, check for a running flag and 
            // get the oldest start data for the start dates, if any.
        }

        return new ProgressSnapshot(moduleHandle, fileIngestRunning, fileIngestStartTime, this.cancelled);
    }

    /**
     * Gets snapshots of the state and performance of this ingest job's child
     * data source ingest jobs.
     *
     * @return A list of data source ingest job progress snapshots.
     */
    synchronized List<DataSourceIngestJob.Snapshot> getDetailedSnapshot() { // RJCTODO: Consider renaming
        List<DataSourceIngestJob.Snapshot> snapshots = new ArrayList<>();
        for (DataSourceIngestJob dataSourceJob : this.dataSourceJobs.values()) {
            snapshots.add(dataSourceJob.getSnapshot());
        }
        return snapshots;
    }

    /**
     * Requests cancellation of a specific data source level ingest module.
     * Returns immediately, but there may be a delay before the ingest module
     * responds by stopping processing, if it is still running when the request
     * is made.
     *
     * @param module The handle of the data source ingest module to be canceled,
     * which can obtained from a progress snapshot.
     */
    
// RJCTODO    
    
    /**
     * Requests cancellation of the data source level and file level ingest
     * modules of this ingest job. Returns immediately, but there may be a delay
     * before all of the ingest modules respond by stopping processing.
     */
    synchronized public void cancel() {
        for (DataSourceIngestJob job : this.dataSourceJobs.values()) {
            job.cancel();
        }
        this.cancelled = true;
    }

    /**
     * Queries whether or not cancellation of the data source level and file
     * level ingest modules of this ingest job has been requested.
     *
     * @return True or false.
     */
    synchronized public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * A snapshot of ingest job progress.
     */
    public static final class ProgressSnapshot {

        private final DataSourceIngestModuleHandle dataSourceModule;
        private final boolean fileIngestRunning;
        private final Date fileIngestStartTime;
        private final boolean cancelled;

        /**
         * Constructs a snapshot of ingest job progress.
         *
         * @param dataSourceModule The currently running data source level
         * ingest module, may be null
         * @param fileIngestRunning Whether or not file ingest is currently
         * running.
         * @param fileIngestStartTime The start time of file level ingest, may
         * be null
         * @param cancelled Whether or not a cancellation request has been
         * issued.
         */
        private ProgressSnapshot(DataSourceIngestModuleHandle dataSourceModule, boolean fileIngestRunning, Date fileIngestStartTime, boolean cancelled) {
            this.dataSourceModule = dataSourceModule;
            this.fileIngestRunning = fileIngestRunning;
            this.fileIngestStartTime = fileIngestStartTime;
            this.cancelled = cancelled;
        }

        /**
         * Gets a handle to the currently running data source level ingest
         * module at the time the snapshot is taken.
         *
         * @return The handle, may be null.
         */
        public DataSourceIngestModuleHandle runningDataSourceIngestModule() {
            /**
             * It is safe to hand out this reference because the object is
             * immutable.
             */
            return this.dataSourceModule;
        }

        /**
         * Queries whether or not file level ingest is running at the time the
         * snapshot is taken.
         *
         * @return True or false.
         */
        public boolean fileIngestIsRunning() {
            return this.fileIngestRunning;
        }

        /**
         * Gets the time that file level ingest started.
         *
         * @return The start time, may be null.
         */
        public Date fileIngestStartTime() {
            return new Date(this.fileIngestStartTime.getTime());
        }

        /**
         * Queries whether or not a cancellation request has been issued.
         *
         * @return True or false.
         */
        public boolean isCancelled() {
            return this.cancelled;
        }

    }

    /**
     * A handle to a data source level ingest module that can be used to get
     * basic information about the module and to request cancellation, i.e.,
     * shut down, of the module.
     */
    public static class DataSourceIngestModuleHandle {

        private final long dataSourceIngestJobId;
        private final DataSourceIngestPipeline.PipelineModule module;

        /**
         * Constructs a handle to a data source level ingest module that can be
         * used to get basic information about the module and to request
         * cancellation of the module.
         */
        private DataSourceIngestModuleHandle(long dataSourceIngestJobId, DataSourceIngestPipeline.PipelineModule module) {
            this.dataSourceIngestJobId = dataSourceIngestJobId;
            this.module = module;
        }

        /**
         * Gets the display name of the data source level ingest module
         * associated with this handle.
         *
         * @return The display name.
         */
        public String displayName() {
            return this.module.getDisplayName();
        }

        /**
         * Returns the time the data source level ingest module associated with
         * this handle began processing.
         *
         * @return The module start time.
         */
        public Date startTime() {
            return this.module.getStartTime();
        }
        
        public void cancel() {
            // RJCTODO:
        }

    }

    /**
     * Starts up the ingest pipelines and ingest progress bars for this job.
     *
     * @return A collection of ingest module start up errors, empty on success.
     */
    List<IngestModuleError> start() {
        boolean hasIngestPipeline = false;
        List<IngestModuleError> errors = new ArrayList<>();
        for (DataSourceIngestJob dataSourceJob : this.dataSourceJobs.values()) {
            errors.addAll(dataSourceJob.start());
            hasIngestPipeline = dataSourceJob.hasIngestPipeline();
        }
        return errors;
    }

    /**
     * Checks to see if this ingest job has at least one ingest pipeline.
     *
     * @return True or false.
     */
    boolean hasIngestPipeline() {
        boolean hasIngestPipeline = false;
        for (DataSourceIngestJob dataSourceJob : this.dataSourceJobs.values()) {
            if (dataSourceJob.hasIngestPipeline()) {
                hasIngestPipeline = true;
                break;
            }
        }
        return hasIngestPipeline;
    }

    /**
     * Provides a callback for completed data source ingest jobs, allowing the
     * ingest job to notify the ingest manager when it is complete.
     *
     * @param dataSourceIngestJob A completed data source ingest job.
     */
    synchronized void dataSourceJobFinished(DataSourceIngestJob dataSourceIngestJob) {
        this.dataSourceJobs.remove(dataSourceIngestJob.getId());
        if (this.dataSourceJobs.isEmpty()) {
            IngestManager.getInstance().finishJob(this);
        }
    }

}
