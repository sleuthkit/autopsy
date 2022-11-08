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

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * A snapshot of the progress of an ingest job.
 */
public final class IngestJobProgressSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String dataSource;
    private final long jobId;
    private final long jobStartTime;
    private final long snapShotTime;
    private final String currentIngestModuleTier;
    transient private final DataSourceIngestPipeline.DataSourcePipelineModule dataSourceLevelIngestModule;
    private final boolean fileIngestRunning;
    private final Date fileIngestStartTime;
    private final long processedFiles;
    private final long estimatedFilesToProcess;
    private final IngestTasksScheduler.IngestTasksSnapshot tasksSnapshot;
    transient private final boolean jobCancelled;
    transient private final IngestJob.CancellationReason jobCancellationReason;
    transient private final List<String> cancelledDataSourceModules;

    /**
     * Constructs a snapshot of the progress of an ingest job.
     */
    IngestJobProgressSnapshot(
            String dataSourceName,
            long jobId,
            long jobStartTime,
            String currentIngestModuleTier,
            DataSourceIngestPipeline.DataSourcePipelineModule dataSourceIngestModule,
            boolean fileIngestRunning,
            Date fileIngestStartTime,
            boolean jobCancelled,
            IngestJob.CancellationReason cancellationReason,
            List<String> cancelledModules,
            long processedFiles,
            long estimatedFilesToProcess,
            long snapshotTime,
            IngestTasksScheduler.IngestTasksSnapshot tasksSnapshot) {
        this.dataSource = dataSourceName;
        this.jobId = jobId;
        this.jobStartTime = jobStartTime;
        this.currentIngestModuleTier = currentIngestModuleTier;
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
     * Gets the time this snapshot was taken.
     *
     * @return The statistics collection time as number of milliseconds since
     *         January 1, 1970, 00:00:00 GMT.
     */
    long getSnapshotTime() {
        return snapShotTime;
    }

    /**
     * Gets the name of the data source for the ingest job.
     *
     * @return The data source name.
     */
    String getDataSource() {
        return dataSource;
    }

    /**
     * Gets the identifier of the ingest job.
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

    /**
     * Get the current ingest module tier.
     *
     * @return The current ingest module tier.
     */
    String getCurrentIngestModuleTier() {
        return currentIngestModuleTier;
    }

    /**
     * Gets a handle to the currently running data source level ingest module at
     * the time this snapshot was taken.
     *
     * @return The data source ingest module handle, may be null.
     */
    DataSourceIngestPipeline.DataSourcePipelineModule getDataSourceLevelIngestModule() {
        return this.dataSourceLevelIngestModule;
    }

    /**
     * Gets whether or not file level analysis was in progress at the time this
     * snapshot was taken.
     *
     * @return True or false.
     */
    boolean getFileIngestIsRunning() {
        return this.fileIngestRunning;
    }

    /**
     * Gets the time that file level analysis was started.
     *
     * @return The start time.
     */
    // RJCTODO: How is this affected by ingest module tiers?
    Date getFileIngestStartTime() {
        return new Date(fileIngestStartTime.getTime());
    }

    /**
     * Gets files per second throughput since the ingest job started.
     *
     * @return Files processed per second (approximate).
     */
    // RJCTODO: How is this affected by ingest module tiers?
    double getFilesProcessedPerSec() {
        return (double) processedFiles / ((snapShotTime - jobStartTime) / 1000);
    }

    /**
     * Gets the total number of files processed so far.
     *
     * @return The number of processed files.
     */
    // RJCTODO: How is this affected by ingest module tiers?
    long getFilesProcessed() {
        return processedFiles;
    }

    /**
     * Gets an estimate of the total number files that need to be processed.
     *
     * @return The estimate.
     */
    // RJCTODO: How is this affected by ingest module tiers?
    long getFilesEstimated() {
        return estimatedFilesToProcess;
    }

    /**
     * Gets the number of data source level ingest tasks for the ingest job that
     * are currently in the data source ingest thread queue of the ingest tasks
     * scheduler.
     *
     * @return The number of data source ingest tasks.
     */
    long getDsQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getDataSourceQueueSize();
    }

    /**
     * Gets the number of file ingest tasks for the ingest job that are
     * currently in the root level queue of the ingest tasks scheduler.
     *
     * @return The number of file ingest tasks.
     */
    long getRootQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getRootQueueSize();
    }

    /**
     * Gets the number of file ingest tasks for the ingest job that are
     * currently in the directory level queue of the ingest tasks scheduler.
     *
     * @return The number of file ingest tasks.
     */
    long getDirQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getDirQueueSize();
    }

    /**
     * Gets the number of file ingest tasks for the ingest job that are
     * currently in the streamed files queue of the ingest tasks scheduler.
     *
     * @return The number of file ingest tasks.
     */
    long getStreamingQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getStreamedFilesQueueSize();
    }

    /**
     * Gets the number of file ingest tasks for the ingest job that are
     * currently in the file ingest threads queue of the ingest tasks scheduler.
     *
     * @return The number of file ingest tasks.
     */
    long getFileQueueSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getFileQueueSize();
    }

    /**
     * Gets the number of data artifact ingest tasks for the ingest job that are
     * currently in the data artifact ingest thread queue of the ingest tasks
     * scheduler.
     *
     * @return The number of data artifact ingest tasks.
     */
    long getDataArtifactTasksQueueSize() {
        if (tasksSnapshot == null) {
            return 0;
        }
        return tasksSnapshot.getArtifactsQueueSize();
    }

    /**
     * Gets the number of analysis result ingest tasks for the ingest job that
     * are currently in the analysis result ingest thread queue of the ingest
     * tasks scheduler.
     *
     * @return The number of analysis result ingest tasks.
     */
    long getAnalysisResultTasksQueueSize() {
        if (tasksSnapshot == null) {
            return 0;
        }
        return tasksSnapshot.getResultsQueueSize();
    }

    /**
     * Gets the number of ingest tasks for the ingest job that are currently in
     * the tasks in progress list of the ingest tasks scheduler.
     *
     * @return The number of file ingest tasks.
     */
    long getRunningListSize() {
        if (null == this.tasksSnapshot) {
            return 0;
        }
        return this.tasksSnapshot.getProgressListSize();
    }

    /**
     * Gets whether or not the job has been cancelled.
     *
     * @return True or false.
     */
    boolean isCancelled() {
        return this.jobCancelled;
    }

    /**
     * Gets the reason the job was cancelled.
     *
     * @return The cancellation reason, may be not cancelled.
     */
    IngestJob.CancellationReason getCancellationReason() {
        return this.jobCancellationReason;
    }

    /**
     * Gets a list of the display names of any canceled data source level ingest
     * modules.
     *
     * @return A list of canceled data source level ingest module display names,
     *         possibly empty.
     */
    List<String> getCancelledDataSourceIngestModules() {
        return Collections.unmodifiableList(this.cancelledDataSourceModules);
    }

}
