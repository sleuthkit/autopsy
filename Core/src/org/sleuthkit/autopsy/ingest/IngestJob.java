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
     */
    IngestJob(Collection<Content> dataSources, IngestJobSettings settings) {
        this.id = IngestJob.nextId.getAndIncrement();
        this.dataSourceJobs = new HashMap<>();
        for (Content dataSource : dataSources) {
            DataSourceIngestJob dataSourceIngestJob = new DataSourceIngestJob(this, dataSource, settings);
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
    synchronized public Snapshot getSnapshot() {
        // TODO: There are race conditions here that can be easily fixed by 
        // eliminating the child jobs per plan.
        DataSourceIngestModuleHandle moduleHandle = null;
        Date fileAnalysisStartTime = null;
        for (DataSourceIngestJob dataSourceJob : this.dataSourceJobs.values()) {
            /**
             * Only one data source ingest module should be running at a time,
             * so grab the first one. There is a race condition here.
             */
            DataSourceIngestPipeline.DataSourceIngestModuleDecorator module = dataSourceJob.getCurrentDataSourceIngestModule();
            if (null != module) {
                moduleHandle = new DataSourceIngestModuleHandle(dataSourceJob.getId(), module);
            }
            
            
        }

        return new Snapshot(moduleHandle, fileAnalysisStartTime, this.cancelled);
    }

    /**
     * Gets snapshots of the state and performance of this ingest job's child
     * data source ingest jobs.
     *
     * @return A list of data source ingest job snapshots.
     */
    synchronized List<DataSourceIngestJob.Snapshot> getDetailedSnapshot() {
        List<DataSourceIngestJob.Snapshot> snapshots = new ArrayList<>();
        for (DataSourceIngestJob dataSourceJob : this.dataSourceJobs.values()) {
            snapshots.add(dataSourceJob.getSnapshot());
        }
        return snapshots;
    }

    /**
     *
     * @param module
     */
    synchronized public void cancelIngestModule(DataSourceIngestModuleHandle module) {
        DataSourceIngestJob dataSourceJob = this.dataSourceJobs.get(module.dataSourceIngestJobId);
        // RJCTODO: check equality, etc.
    }

    /**
     * Requests cancellation of this ingest job, i.e., a shutdown of its data
     * source level and file level ingest pipelines.
     */
    synchronized public void cancel() {
        for (DataSourceIngestJob job : this.dataSourceJobs.values()) {
            job.cancel();
        }
        this.cancelled = true; // RJCTODO: make children push cancellation up
    }

    /**
     * Queries whether or not cancellation of this ingest job, i.e., a shutdown
     * of its data source level and file level ingest pipelines, has been
     * requested.
     *
     * @return True or false.
     */
    synchronized public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * A thread-safe snapshot of ingest job progress.
     */
    public static final class Snapshot {

        private final DataSourceIngestModuleHandle dataSourceModule;
        private final Date fileAnalysisStartTime;
        private final boolean cancelled;

        private Snapshot(DataSourceIngestModuleHandle dataSourceModule, Date fileAnalysisStartTime, boolean cancelled) {
            this.dataSourceModule = dataSourceModule;
            this.fileAnalysisStartTime = fileAnalysisStartTime;
            this.cancelled = cancelled;
        }

        public DataSourceIngestModuleHandle runningDataSourceIngestModule() {
            /**
             * It is safe to hand out this reference because the object is
             * immutable and the mutable
             * DataSourceIngestPipeline.DataSourceIngestModuleDecorator is
             * hidden from public access.
             */
            return this.dataSourceModule;
        }

        public boolean fileAnalysisIsRunning() {
            return (null != this.fileAnalysisStartTime);
        }

        public Date fileAnalysisStartTime() {
            return new Date(this.fileAnalysisStartTime.getTime());
        }

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
        private final DataSourceIngestPipeline.DataSourceIngestModuleDecorator module;

        private DataSourceIngestModuleHandle(long dataSourceIngestJobId, DataSourceIngestPipeline.DataSourceIngestModuleDecorator module) {
            this.dataSourceIngestJobId = dataSourceIngestJobId;
            this.module = module;
        }

        public String displayName() {
            return this.module.getDisplayName();
        }

        public Date startTime() {
            // RJCTODO
            return new Date();
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
