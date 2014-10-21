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
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Encapsulates a data source to be processed and the settings, ingest module
 * pipelines, and progress bars that are used to process it.
 */
final class IngestJob {

    private static final Logger logger = Logger.getLogger(IngestJob.class.getName());
    private static final IngestScheduler ingestTaskScheduler = IngestScheduler.getInstance();

    // These fields define the ingest job and the work it entails.
    private final long id;
    private final Content dataSource;
    private final boolean processUnallocatedSpace;
    private DataSourceIngestPipeline dataSourceIngestPipeline;
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines;

    // These fields are used to update the ingest progress UI components. The
    // filesInProgress collection contains the names of the files that are in
    // the file ingest pipelines and the two file counter fields are used to
    // used to update the file ingest progress component's progress bar.
    private ProgressHandle dataSourceIngestProgress;
    private final List<String> filesInProgress;
    private long estimatedFilesToProcess;
    private long processedFiles;
    private ProgressHandle fileIngestProgress;

    // These fields support cancellation of all or part of the ingest job. The
    // lock object is used to enable the three flags to be treated as one state
    // variable subject and to support atomic cancellation operations.
    private boolean dataSourceIngestPipelineInterrupted;
    private boolean dataSourceIngestCancelled;
    private boolean fileIngestCancelled;
    private final Object cancellationLock;

    // This field is used for generating ingest job diagnostic data.
    private final long startTime;

    /**
     * Constructs an ingest job.
     *
     * @param id The identifier assigned to the job by the ingest manager.
     * @param dataSource The data source to be ingested.
     * @param processUnallocatedSpace Whether or not unallocated space should be
     * processed during the ingest job.
     */
    IngestJob(long id, Content dataSource, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.processUnallocatedSpace = processUnallocatedSpace;
        this.cancellationLock = new Object();
        this.fileIngestPipelines = new LinkedBlockingQueue<>();

        this.filesInProgress = new ArrayList<>();
        this.estimatedFilesToProcess = 0L;
        this.processedFiles = 0L;

        this.dataSourceIngestPipelineInterrupted = false;
        this.dataSourceIngestCancelled = false;
        this.fileIngestCancelled = false;

        this.startTime = new Date().getTime();
    }

    /**
     * Gets the identifier assigned to the ingest job by the ingest manager.
     *
     * @return The ingest job identifier.
     */
    long getId() {
        return id;
    }

    /**
     * Gets the data source to be ingested by this job.
     *
     * @return A reference to a Content object representing the data source.
     */
    Content getDataSource() {
        return dataSource;
    }

    /**
     * Queries whether or not unallocated space should be processed as part of
     * this job.
     *
     * @return True if unallocated space is to be processed, false otherwise.
     */
    boolean shouldProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }

    /**
     * Starts up the ingest pipelines and ingest progress bars.
     *
     * @return A collection of ingest module startup errors, empty on success.
     * @throws InterruptedException
     */
    List<IngestModuleError> start(List<IngestModuleTemplate> ingestModuleTemplates) throws InterruptedException {
        createIngestPipelines(ingestModuleTemplates);
        List<IngestModuleError> errors = startUpIngestPipelines();
        if (errors.isEmpty()) {
            if (!dataSourceIngestPipeline.isEmpty()) {
                startDataSourceIngestProgressBar();
            }
            if (!fileIngestPipelines.peek().isEmpty()) {
                startFileIngestProgressBar();
            }
        }
        return errors;
    }

    /**
     * Creates the file and data source ingest pipelines.
     *
     * @param ingestModuleTemplates Ingest module templates to use to populate
     * the pipelines.
     * @throws InterruptedException
     */
    private void createIngestPipelines(List<IngestModuleTemplate> ingestModuleTemplates) throws InterruptedException {
        dataSourceIngestPipeline = new DataSourceIngestPipeline(this, ingestModuleTemplates);
        int numberOfPipelines = IngestManager.getInstance().getNumberOfFileIngestThreads();
        for (int i = 0; i < numberOfPipelines; ++i) {
            fileIngestPipelines.put(new FileIngestPipeline(this, ingestModuleTemplates));
        }
    }

    /**
     * Starts up each of the file and data source ingest modules to collect
     * possible errors.
     *
     * @return A collection of ingest module startup errors, empty on success.
     * @throws InterruptedException
     */
    private List<IngestModuleError> startUpIngestPipelines() throws InterruptedException {
        List<IngestModuleError> errors = new ArrayList<>();
        errors.addAll(dataSourceIngestPipeline.startUp());
        for (FileIngestPipeline pipeline : fileIngestPipelines) {
            errors.addAll(pipeline.startUp());
            if (!errors.isEmpty()) {
                // No need to accumulate presumably redundant errors.
                while (!fileIngestPipelines.isEmpty()) {
                    pipeline = fileIngestPipelines.poll();
                    List<IngestModuleError> shutDownErrors = pipeline.shutDown();
                    if (!shutDownErrors.isEmpty()) {
                        logIngestModuleErrors(shutDownErrors);
                    }
                }
                break;
            }
        }
        logIngestModuleErrors(errors);
        return errors;
    }

    /**
     * Starts the data source ingest progress bar.
     */
    private void startDataSourceIngestProgressBar() {
        final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.dataSourceIngest.initialDisplayName",
                dataSource.getName());
        dataSourceIngestProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                DataSourceIngestCancellationPanel panel = new DataSourceIngestCancellationPanel();
                // RJCTODO: Need bundle for dialog title
                // RJCTODO: Consider text changes per story in JIRA
                // RJCTODO: Consider current module issues
                JOptionPane.showConfirmDialog(null, panel, "Cancel Ingest", JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
                switch (panel.getCancelRequest()) {
                    case CURRENT_DATA_SOURCE_MODULE:
                        IngestJob.this.interruptDataSourceIngestPipeline();
                        break;
                    case DATA_SOURCE_INGEST:
                        IngestJob.this.cancelDataSourceIngest();
                        break;
                    case JOB:
                        IngestJob.this.cancel();
                        break;
                }
                return true;
            }
        });
        dataSourceIngestProgress.start();
        dataSourceIngestProgress.switchToIndeterminate();
    }

    /**
     * Starts the file ingest progress bar.
     */
    private void startFileIngestProgressBar() {
        final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.fileIngest.displayName",
                dataSource.getName());
        fileIngestProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {

            @Override
            public boolean cancel() {
                FileIngestCancellationPanel panel = new FileIngestCancellationPanel();
                // RJCTODO: Need bundle for dialog title
                // RJCTODO: Consider text changes per story in JIRA
                JOptionPane.showConfirmDialog(null, panel, "Cancel Ingest", JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
                switch (panel.getCancelRequest()) {
                    case FILE_INGEST:
                        IngestJob.this.cancelFileIngest();
                        break;
                    case JOB:
                        IngestJob.this.cancel();
                        break;
                }
                return true;
            }
        });
        estimatedFilesToProcess = dataSource.accept(new GetFilesCountVisitor());
        fileIngestProgress.start();
        fileIngestProgress.switchToDeterminate((int) estimatedFilesToProcess);
    }

    /**
     * Checks to see if this job has a data source ingest pipeline.
     *
     * @return True or false.
     */
    boolean hasDataSourceIngestPipeline() {
        return (dataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if the job has a file ingest pipeline.
     *
     * @return True or false.
     */
    boolean hasFileIngestPipeline() {
        return (fileIngestPipelines.peek().isEmpty() == false);
    }

    /**
     * Executes the data source ingest module pipeline for the data source
     * associated with this job by doing a data source ingest task provided by
     * the ingest scheduler.
     *
     * @param task A data source ingest task.
     * @throws InterruptedException
     */
    void process(DataSourceIngestTask task) throws InterruptedException {
        try {
            if (!this.jobIsCancelled() && !this.dataSourceIngestIsCancelled() && !this.dataSourceIngestPipeline.isEmpty()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(dataSourceIngestPipeline.process(task, dataSourceIngestProgress));
                if (!errors.isEmpty()) {
                    logIngestModuleErrors(errors);
                }
            }

            // All data source ingest jobs are done for this task, so shut down 
            // the progress bar
            if (null != dataSourceIngestProgress) {
                dataSourceIngestProgress.finish();
                // RJCTODO: What about this comment?
                // This is safe because this method will be called at most once 
                // per ingest job and finish() will not be called while that single 
                // data source ingest task has not been reported complete by this
                // code to the ingest scheduler.
                dataSourceIngestProgress = null;
            }
        } finally {
            ingestTaskScheduler.notifyTaskCompleted(task);
        }
    }

    /**
     * Executes the file ingest module pipeline for a file in the data source
     * associated with this job by doing a file ingest task provided by the
     * ingest scheduler.
     *
     * @param task A file ingest task.
     * @throws InterruptedException
     */
    void process(FileIngestTask task) throws InterruptedException {
        try {
            if (!this.jobIsCancelled() && !this.fileIngestIsCancelled()) {
                // Get a file ingest pipeline not currently in use by another
                // file ingest thread.
                FileIngestPipeline pipeline = this.fileIngestPipelines.take();
                if (!pipeline.isEmpty()) {
                    // Get the file to process.
                    AbstractFile file = task.getFile();

                    // Update the state used to drive the file ingest progress bar.
                    synchronized (this) {
                        ++this.processedFiles;
                        if (this.processedFiles <= this.estimatedFilesToProcess) {
                            this.fileIngestProgress.progress(file.getName(), (int) this.processedFiles);
                        } else {
                            this.fileIngestProgress.progress(file.getName(), (int) this.estimatedFilesToProcess);
                        }
                        this.filesInProgress.add(file.getName());
                    }

                    // Run the file through the pipeline.
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.process(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }

                    // Update the file ingest progress bar in case the file was 
                    // being displayed.
                    synchronized (this) {
                        filesInProgress.remove(file.getName());
                        if (filesInProgress.size() > 0) {
                            fileIngestProgress.progress(filesInProgress.get(0));
                        } else {
                            fileIngestProgress.progress("");
                        }
                    }
                }

                // Relinquish the pipeline so it can be reused by another file 
                // ingest thread.
                fileIngestPipelines.put(pipeline);
            }
        } finally {
            ingestTaskScheduler.notifyTaskCompleted(task);
        }
    }

    /**
     * Shuts down the ingest pipelines and progress bars for this job.
     */
    void finish() {
        // Shut down the file ingest pipelines. Note that no shut down is
        // required for the data source ingest pipeline because data source 
        // ingest modules do not have a shutdown() method.
        List<IngestModuleError> errors = new ArrayList<>();
        while (!fileIngestPipelines.isEmpty()) {
            FileIngestPipeline pipeline = fileIngestPipelines.poll();
            errors.addAll(pipeline.shutDown());
        }
        if (!errors.isEmpty()) {
            logIngestModuleErrors(errors);
        }

        // Finish the data source ingest progress bar, if it hasn't already 
        // been finished.
        if (dataSourceIngestProgress != null) {
            dataSourceIngestProgress.finish();
        }

        // Finish the file ingest progress bar, if it hasn't already 
        // been finished.
        if (fileIngestProgress != null) {
            fileIngestProgress.finish();
        }
    }

    /**
     * Write ingest module errors to the log.
     *
     * @param errors The errors.
     */
    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            logger.log(Level.SEVERE, error.getModuleDisplayName() + " experienced an error", error.getModuleError()); //NON-NLS
        }
    }

    /**
     * Requests an interrupt of the data source ingest pipeline in order to stop
     * the currently executing data source ingest module.
     */
    void interruptDataSourceIngestPipeline() {
        synchronized (this.cancellationLock) {
            this.dataSourceIngestPipelineInterrupted = true;
        }
    }

    /**
     * Determines whether a request to interrupt the data source ingest pipeline
     * was made.
     *
     * @return True or false.
     */
    boolean dataSourceIngestPipelineIsInterrupted() {
        synchronized (this.cancellationLock) {
            return this.dataSourceIngestPipelineInterrupted;
        }
    }

    /**
     * Requests an resume of the data source ingest pipeline.
     */
    ProgressHandle resumeDataSourceIngestPipeline() {
        synchronized (this.cancellationLock) {
            this.dataSourceIngestPipelineInterrupted = false;
            this.dataSourceIngestProgress.finish();
            this.startDataSourceIngestProgressBar();
            return this.dataSourceIngestProgress;
        }
    }

    /**
     * Requests cancellation of data source ingest, i.e., a shutdown of the data
     * source ingest pipeline.
     */
    void cancelDataSourceIngest() {
        synchronized (this.cancellationLock) {
            if (dataSourceIngestProgress != null) {
                final String displayName = NbBundle.getMessage(this.getClass(),
                        "IngestJob.progress.dataSourceIngest.initialDisplayName",
                        dataSource.getName());
                dataSourceIngestProgress.setDisplayName(
                        NbBundle.getMessage(this.getClass(),
                                "IngestJob.progress.cancelling",
                                displayName));
            }
            this.dataSourceIngestCancelled = true;
            IngestJob.ingestTaskScheduler.discardPendingTasksForJob(this, IngestScheduler.TaskType.DATA_SOURCE_INGEST);
        }
    }

    /**
     * Queries whether or not cancellation of data source ingest i.e., a
     * shutdown of the data source ingest pipeline, has been requested.
     *
     * @return True or false.
     */
    boolean dataSourceIngestIsCancelled() {
        synchronized (this.cancellationLock) {
            return this.dataSourceIngestCancelled;
        }
    }

    /**
     * Requests cancellation of file ingest, i.e., a shutdown of the file ingest
     * pipeline.
     */
    void cancelFileIngest() {
        synchronized (this.cancellationLock) {
            if (this.fileIngestProgress != null) {
                final String displayName = NbBundle.getMessage(this.getClass(),
                        "IngestJob.progress.fileIngest.displayName",
                        this.dataSource.getName());
                this.fileIngestProgress.setDisplayName(
                        NbBundle.getMessage(this.getClass(), "IngestJob.progress.cancelling",
                                displayName));
            }
            this.fileIngestCancelled = true;
            IngestJob.ingestTaskScheduler.discardPendingTasksForJob(this, IngestScheduler.TaskType.FILE_INGEST);
        }
    }

    /**
     * Queries whether or not cancellation of file ingest i.e., a shutdown of
     * the file ingest pipeline, has been requested
     *
     * @return True or false.
     */
    boolean fileIngestIsCancelled() {
        synchronized (this.cancellationLock) {
            return this.fileIngestCancelled;
        }
    }

    /**
     * Requests cancellation of ingest, i.e., a shutdown of the data source and
     * file ingest pipelines.
     */
    void cancel() {
        synchronized (this.cancellationLock) {
            this.cancelDataSourceIngest();
            this.cancelFileIngest();
        }
    }

    /**
     * Queries whether or not cancellation of ingest i.e., a shutdown of the
     * data source and file ingest pipelines, has been requested
     *
     * @return True or false.
     */
    boolean jobIsCancelled() {
        synchronized (this.cancellationLock) {
            return this.dataSourceIngestCancelled && this.fileIngestCancelled;
        }
    }

    /**
     * Get some basic performance statistics on this job.
     *
     * @return An ingest job statistics object.
     */
    IngestJobStats getStats() {
        return new IngestJobStats();
    }

    /**
     * Stores basic diagnostic statistics for an ingest job.
     */
    class IngestJobStats {

        private final long startTime;
        private final long processedFiles;
        private final long estimatedFilesToProcess;
        private final long snapShotTime;

        IngestJobStats() {
            synchronized (IngestJob.this) {
                this.startTime = IngestJob.this.startTime;
                this.processedFiles = IngestJob.this.processedFiles;
                this.estimatedFilesToProcess = IngestJob.this.estimatedFilesToProcess;
                this.snapShotTime = new Date().getTime();
            }
        }

        /**
         * Get files per second throughput since job started.
         *
         * @return
         */
        double getSpeed() {
            return (double) processedFiles / ((snapShotTime - startTime) / 1000);
        }

        long getStartTime() {
            return startTime;
        }

        /**
         * Get time these statistics were collected
         *
         * @return
         */
        long getSnapshotTime() {
            return snapShotTime;
        }

        /**
         * Number of files processed by job so far
         *
         * @return
         */
        long getFilesProcessed() {
            return processedFiles;
        }

        long getFilesEstimated() {
            return estimatedFilesToProcess;
        }
    }

}
