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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Analyzes one or more data sources using a set of ingest modules specified via
 * ingest job settings.
 */
public final class IngestJob {

    /**
     * An ingest job can be cancelled for various reasons.
     */
    public enum CancellationReason {

        NOT_CANCELLED(NbBundle.getMessage(IngestJob.class, "IngestJob.cancelReason.notCancelled.text")),
        USER_CANCELLED(NbBundle.getMessage(IngestJob.class, "IngestJob.cancelReason.cancelledByUser.text")),
        INGEST_MODULES_STARTUP_FAILED(NbBundle.getMessage(IngestJob.class, "IngestJob.cancelReason.ingestModStartFail.text")),
        OUT_OF_DISK_SPACE(NbBundle.getMessage(IngestJob.class, "IngestJob.cancelReason.outOfDiskSpace.text")),
        SERVICES_DOWN(NbBundle.getMessage(IngestJob.class, "IngestJob.cancelReason.servicesDown.text")),
        CASE_CLOSED(NbBundle.getMessage(IngestJob.class, "IngestJob.cancelReason.caseClosed.text"));

        private final String displayName;

        private CancellationReason(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Ingest job mode.
     */
    enum Mode {
        BATCH,
        STREAMING
    }

    private static final Logger logger = Logger.getLogger(IngestJob.class.getName());
    private final static AtomicLong nextId = new AtomicLong(0L);
    private final long id;
    private final Content dataSource;
    private final List<AbstractFile> files = new ArrayList<>();
    private final Mode ingestMode;
    private IngestJobPipeline ingestJobPipeline;
    private final IngestJobSettings settings;
    private volatile CancellationReason cancellationReason;

    /**
     * Constructs an ingest job that analyzes one data source using a set of
     * ingest modules specified via ingest job settings. Either all of the files
     * in the data source or a given subset of the files will be analyzed.
     *
     * @param dataSource The data source to be analyzed.
     * @param files      A subset of the files for the data source.
     * @param settings   The ingest job settings.
     */
    IngestJob(Content dataSource, List<AbstractFile> files, IngestJobSettings settings) {
        this(dataSource, Mode.BATCH, settings);
        this.files.addAll(files);
    }

    /**
     * Constructs an ingest job that analyzes one data source, possibly using an
     * ingest stream.
     *
     * @param settings The ingest job settings.
     */
    IngestJob(Content dataSource, Mode ingestMode, IngestJobSettings settings) {
        this.id = IngestJob.nextId.getAndIncrement();
        this.dataSource = dataSource;
        this.settings = settings;
        this.ingestMode = ingestMode;
        cancellationReason = CancellationReason.NOT_CANCELLED;
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
     * Checks to see if this ingest job has at least one non-empty ingest module
     * pipeline (first or second stage data-source-level pipeline or file-level
     * pipeline).
     *
     * @return True or false.
     */
    boolean hasIngestPipeline() {
        return (!settings.getEnabledIngestModuleTemplates().isEmpty());
    }

    /**
     * Add a set of files (by object ID) to be ingested.
     *
     * @param fileObjIds the list of file IDs
     */
    void addStreamingIngestFiles(List<Long> fileObjIds) {
        if (ingestJobPipeline != null) {
            ingestJobPipeline.addStreamedFiles(fileObjIds);
        } else {
            logger.log(Level.SEVERE, "Attempted to add streaming ingest files with no IngestJobPipeline");
        }
    }

    /**
     * Start data source processing for streaming ingest.
     */
    void processStreamingIngestDataSource() {
        if (ingestJobPipeline != null) {
            ingestJobPipeline.addStreamedDataSource();
        } else {
            logger.log(Level.SEVERE, "Attempted to start data source ingest with no IngestJobPipeline");
        }
    }

    /**
     * Starts this ingest job by starting its ingest module pipelines and
     * scheduling the ingest tasks that make up the job.
     *
     * @return A collection of ingest module start up errors, empty on success.
     */
    List<IngestModuleError> start() throws InterruptedException {
        /*
         * Set up the ingest job pipeline.
         */
        if (files.isEmpty()) {
            ingestJobPipeline = new IngestJobPipeline(this, dataSource, settings);
        } else {
            ingestJobPipeline = new IngestJobPipeline(this, dataSource, files, settings);
        }

        /*
         * Try to start up the ingest job pipeline.
         */
        List<IngestModuleError> errors = new ArrayList<>();
        errors.addAll(ingestJobPipeline.startUp());
        if (errors.isEmpty()) {
            IngestManager.getInstance().fireDataSourceAnalysisStarted(id, ingestJobPipeline.getId(), ingestJobPipeline.getDataSource());
        } else {
            cancel(CancellationReason.INGEST_MODULES_STARTUP_FAILED);
        }
        return errors;
    }

    /**
     * Get the ingest mode for this job (batch or streaming).
     *
     * @return the ingest mode.
     */
    Mode getIngestMode() {
        return ingestMode;
    }

    /**
     * Gets a snapshot of the progress of this ingest job.
     *
     * @return The snapshot.
     */
    public ProgressSnapshot getSnapshot() {
        return new ProgressSnapshot(true);
    }

    /**
     * Gets a snapshot of the progress of this ingest job.
     *
     * @param getIngestTasksSnapshot
     *
     * @return The snapshot.
     */
    public ProgressSnapshot getSnapshot(boolean getIngestTasksSnapshot) {
        return new ProgressSnapshot(getIngestTasksSnapshot);
    }

    /**
     * Gets a snapshot of the progress of this ingest job.
     *
     * @return The ingest job progress snapshot.
     */
    Snapshot getProgressSnapshot() {
        return ingestJobPipeline.getSnapshot(true);
    }

    /**
     * Requests cancellation of this ingest job, which means discarding
     * unfinished tasks and stopping the ingest pipelines. Returns immediately,
     * but there may be a delay before all of the ingest modules in the
     * pipelines respond by stopping processing.
     *
     * @deprecated Use cancel(CancellationReason reason) instead
     */
    @Deprecated
    public void cancel() {
        cancel(CancellationReason.USER_CANCELLED);
    }

    /**
     * Requests cancellation of this ingest job, which means discarding
     * unfinished tasks and stopping the ingest module pipelines. Returns
     * immediately, but there may be a delay before all of the ingest modules in
     * the pipelines respond by stopping processing.
     *
     * @param reason The reason for cancellation.
     */
    public void cancel(CancellationReason reason) {
        cancellationReason = reason;
        /*
         * Cancel the ingest module pipeline. This is done in a separate thread
         * to avoid a potential deadlock. The deadlock is possible because this
         * method can be called in a thread that acquires the ingest manager's
         * ingest jobs list lock and then tries to acquire the ingest pipeline
         * stage transition lock, while an ingest thread that has acquired the
         * stage transition lock is trying to acquire the ingest manager's
         * ingest jobs list lock.
         *
         * RC: What?
         */
        new Thread(() -> {
            ingestJobPipeline.cancel(reason);
        }).start();
    }

    /**
     * Gets the reason this job was cancelled.
     *
     * @return The cancellation reason, may be not cancelled.
     */
    public CancellationReason getCancellationReason() {
        return this.cancellationReason;
    }

    /**
     * Queries whether or not cancellation of this ingest job has been
     * requested.
     *
     * @return True or false.
     */
    public boolean isCancelled() {
        return (CancellationReason.NOT_CANCELLED != this.cancellationReason);
    }

    /**
     * Provides a callback for completed ingest job pipeline, allowing this
     * ingest job to notify the ingest manager when it is complete.
     *
     * @param ingestJobPipeline A completed ingestJobPipeline.
     */
    void notifyIngestPipelineShutDown(IngestJobPipeline ingestJobPipeline) {
        IngestManager ingestManager = IngestManager.getInstance();
        if (!ingestJobPipeline.isCancelled()) {
            ingestManager.fireDataSourceAnalysisCompleted(id, ingestJobPipeline.getId(), ingestJobPipeline.getDataSource());
        } else {
            IngestManager.getInstance().fireDataSourceAnalysisCancelled(id, ingestJobPipeline.getId(), ingestJobPipeline.getDataSource());
        }
        ingestManager.finishIngestJob(this);
    }

    /**
     * A snapshot of the progress of an ingest job.
     */
    public final class ProgressSnapshot {

        private final DataSourceProcessingSnapshot dataSourceProcessingSnapshot;
        private DataSourceIngestModuleHandle dataSourceModule;
        private boolean fileIngestRunning;
        private Date fileIngestStartTime;
        private final boolean jobCancelled;
        private final IngestJob.CancellationReason jobCancellationReason;

        /**
         * A snapshot of the progress of an ingest job on the processing of a
         * data source.
         */
        public final class DataSourceProcessingSnapshot {

            private final Snapshot snapshot;

            private DataSourceProcessingSnapshot(Snapshot snapshot) {
                this.snapshot = snapshot;
            }

            /**
             * Gets the name of the data source that is the subject of this
             * snapshot.
             *
             * @return A data source name string.
             */
            public String getDataSource() {
                return snapshot.getDataSource();
            }

            /**
             * Indicates whether or not the processing of the data source that
             * is the subject of this snapshot was canceled.
             *
             * @return True or false.
             */
            public boolean isCancelled() {
                return snapshot.isCancelled();
            }

            /**
             * Gets the reason this job was cancelled.
             *
             * @return The cancellation reason, may be not cancelled.
             */
            public CancellationReason getCancellationReason() {
                return snapshot.getCancellationReason();
            }

            /**
             * Gets a list of the display names of any canceled data source
             * level ingest modules.
             *
             * @return A list of canceled data source level ingest module
             *         display names, possibly empty.
             */
            public List<String> getCancelledDataSourceIngestModules() {
                return snapshot.getCancelledDataSourceIngestModules();
            }

        }

        /**
         * Constructs a snapshot of ingest job progress.
         */
        private ProgressSnapshot(boolean getIngestTasksSnapshot) {
            dataSourceModule = null;
            fileIngestRunning = false;
            fileIngestStartTime = null;
            Snapshot snapshot = ingestJobPipeline.getSnapshot(getIngestTasksSnapshot);
            dataSourceProcessingSnapshot = new DataSourceProcessingSnapshot(snapshot);
            if (dataSourceModule == null) {
                DataSourceIngestPipeline.DataSourcePipelineModule module = snapshot.getDataSourceLevelIngestModule();
                if (module != null) {
                    dataSourceModule = new DataSourceIngestModuleHandle(ingestJobPipeline, module);
                }
            }
            if (snapshot.getFileIngestIsRunning()) {
                fileIngestRunning = true;
            }
            Date childFileIngestStartTime = snapshot.getFileIngestStartTime();
            if (childFileIngestStartTime != null && (fileIngestStartTime == null || childFileIngestStartTime.before(fileIngestStartTime))) {
                fileIngestStartTime = childFileIngestStartTime;
            }
            jobCancelled = isCancelled();
            jobCancellationReason = cancellationReason;
        }

        /**
         * Gets a handle to the currently running data source level ingest
         * module at the time the snapshot was taken.
         *
         * @return The handle, may be null.
         */
        public DataSourceIngestModuleHandle runningDataSourceIngestModule() {
            return this.dataSourceModule;
        }

        /**
         * Queries whether or not file level ingest was running at the time the
         * snapshot was taken.
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
         * Queries whether or not an ingest job level cancellation request had
         * been issued at the time the snapshot was taken.
         *
         * @return True or false.
         */
        public boolean isCancelled() {
            return this.jobCancelled;
        }

        /**
         * Gets the reason this job was cancelled.
         *
         * @return The cancellation reason, may be not cancelled.
         */
        public CancellationReason getCancellationReason() {
            return this.jobCancellationReason;
        }

        /**
         * Gets snapshots of the progress processing individual data sources.
         *
         * @return The list of snapshots.
         */
        public DataSourceProcessingSnapshot getDataSourceProcessingSnapshot() {
            return dataSourceProcessingSnapshot;
        }

    }

    /**
     * A handle to a data source level ingest module that can be used to get
     * basic information about the module and to request cancellation of the
     * module.
     */
    public static class DataSourceIngestModuleHandle {

        private final IngestJobPipeline ingestJobPipeline;
        private final DataSourceIngestPipeline.DataSourcePipelineModule module;
        private final boolean cancelled;

        /**
         * Constructs a handle to a data source level ingest module that can be
         * used to get basic information about the module and to request
         * cancellation of the module.
         *
         * @param ingestJobPipeline The ingestJobPipeline that owns the data
         *                          source level ingest module.
         * @param module            The data source level ingest module.
         */
        private DataSourceIngestModuleHandle(IngestJobPipeline ingestJobPipeline, DataSourceIngestPipeline.DataSourcePipelineModule module) {
            this.ingestJobPipeline = ingestJobPipeline;
            this.module = module;
            this.cancelled = ingestJobPipeline.currentDataSourceIngestModuleIsCancelled();
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
         * Gets the time the data source level ingest module associated with
         * this handle began processing.
         *
         * @return The module processing start time.
         */
        public Date startTime() {
            return this.module.getProcessingStartTime();
        }

        /**
         * Queries whether or not cancellation of the data source level ingest
         * module associated with this handle has been requested.
         *
         * @return True or false.
         */
        public boolean isCancelled() {
            return this.cancelled;
        }

        /**
         * Requests cancellation of the ingest module associated with this
         * handle. Returns immediately, but there may be a delay before the
         * ingest module responds by stopping processing.
         */
        public void cancel() {
            /**
             * TODO: Cancellation needs to be more precise. The long-term
             * solution is to add a cancel() method to IngestModule and do away
             * with the cancellation queries of IngestJobContext. However, until
             * an API change is legal, a cancel() method can be added to the
             * DataSourceIngestModuleAdapter and FileIngestModuleAdapter classes
             * and an instanceof check can be used to call it, with this code as
             * the default implementation and the fallback. All of the ingest
             * modules participating in this workaround will need to consult the
             * cancelled flag in the adapters.
             */
            if (this.ingestJobPipeline.getCurrentDataSourceIngestModule() == this.module) {
                this.ingestJobPipeline.cancelCurrentDataSourceIngestModule();
            }
        }

    }

}
