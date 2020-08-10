/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2020 Basis Technology Corp.
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

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Stores basic diagnostic statistics for a data source ingest job.
 */
public final class Snapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String dataSource;
    private final long jobId;
    private final long jobStartTime;
    private final long snapShotTime;
    transient private final DataSourceIngestPipeline.PipelineModule dataSourceLevelIngestModule;
    private final boolean fileIngestRunning;
    private final Date fileIngestStartTime;
    private final long processedFiles;
    private final long estimatedFilesToProcess;
    private final IngestTasksScheduler.IngestJobTasksSnapshot tasksSnapshot;
    transient private final boolean jobCancelled;
    transient private final IngestJob.CancellationReason jobCancellationReason;
    transient private final List<String> cancelledDataSourceModules;

    /**
     * Constructs an object to store basic diagnostic statistics for a data
     * source ingest job.
     */
    Snapshot(String dataSourceName, long jobId, long jobStartTime, DataSourceIngestPipeline.PipelineModule dataSourceIngestModule,
            boolean fileIngestRunning, Date fileIngestStartTime,
            boolean jobCancelled, IngestJob.CancellationReason cancellationReason, List<String> cancelledModules,
            long processedFiles, long estimatedFilesToProcess,
            long snapshotTime, IngestTasksScheduler.IngestJobTasksSnapshot tasksSnapshot) {
        this.dataSource = dataSourceName;
        this.jobId = jobId;
        this.jobStartTime = jobStartTime;
        this.dataSourceLevelIngestModule = dataSourceIngestModule;

        this.fileIngestRunning = fileIngestRunning;
        this.fileIngestStartTime = fileIngestStartTime;
        this.jobCancelled = jobCancelled;
        this.jobCancellationReason = cancellationReason;
        this.cancelledDataSourceModules = cancelledModules;

        this.processedFiles = processedFiles;
        this.estimatedFilesToProcess = estimatedFilesToProcess;
        this.snapShotTime = snapshotTime;
        this.tasksSnapshot = tasksSnapshot;
    }

    /**
     * Gets time these statistics were collected.
     *
     * @return The statistics collection time as number of milliseconds since
     *         January 1, 1970, 00:00:00 GMT.
     */
    long getSnapshotTime() {
        return snapShotTime;
    }

    /**
     * Gets the name of the data source associated with the ingest job that is
     * the subject of this snapshot.
     *
     * @return A data source name string.
     */
    String getDataSource() {
        return dataSource;
    }

    /**
     * Gets the identifier of the ingest job that is the subject of this
     * snapshot.
     *
     * @return The ingest job id.
     */
    long getJobId() {
        return this.jobId;
    }

    /**
     * Gets the time the ingest job was started.
     *
     * @return The start time as number of milliseconds since January 1, 1970,
     *         00:00:00 GMT.
     */
    long getJobStartTime() {
        return jobStartTime;
    }

    DataSourceIngestPipeline.PipelineModule getDataSourceLevelIngestModule() {
        return this.dataSourceLevelIngestModule;
    }

    boolean getFileIngestIsRunning() {
        return this.fileIngestRunning;
    }

    Date getFileIngestStartTime() {
        return this.fileIngestStartTime;
    }

    /**
     * Gets files per second throughput since the ingest job that is the subject
     * of this snapshot started.
     *
     * @return Files processed per second (approximate).
     */
    double getSpeed() {
        return (double) processedFiles / ((snapShotTime - jobStartTime) / 1000);
    }

    /**
     * Gets the number of files processed for the job so far.
     *
     * @return The number of processed files.
     */
    long getFilesProcessed() {
        return processedFiles;
    }

    /**
     * Gets an estimate of the files that still need to be processed for this
     * job.
     *
     * @return The estimate.
     */
    long getFilesEstimated() {
        return estimatedFilesToProcess;
    }

    long getRootQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getRootQueueSize();
    }

    long getDirQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getDirectoryTasksQueueSize();
    }

    long getFileQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getFileQueueSize();
    }

    long getDsQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getDsQueueSize();
    }
    
     long getStreamingQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getStreamingQueueSize();
    }   

    long getRunningListSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getRunningListSize();
    }

    boolean isCancelled() {
        return this.jobCancelled;
    }

    /**
     * Gets the reason this job was cancelled.
     *
     * @return The cancellation reason, may be not cancelled.
     */
    IngestJob.CancellationReason getCancellationReason() {
        return this.jobCancellationReason;
    }

    /**
     * Gets a list of the display names of any canceled data source level ingest
     * modules
     *
     * @return A list of canceled data source level ingest module display names,
     *         possibly empty.
     */
    List<String> getCancelledDataSourceIngestModules() {
        return Collections.unmodifiableList(this.cancelledDataSourceModules);
    }

}
