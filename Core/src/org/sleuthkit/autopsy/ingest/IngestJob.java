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
    private static final IngestScheduler ingestScheduler = IngestScheduler.getInstance();

    // These fields define the ingest job and the work it entails.
    private final long id;
    private final Content dataSource;
    private final boolean processUnallocatedSpace;
    private DataSourceIngestPipeline dataSourceIngestPipeline;
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines;

    // These fields are used to update the ingest progress UI components. The
    // filesInProgress collection contains the names of the files that are in
    // the file ingest pipelines and the two file counter fields are used to
    // update the file ingest progress bar.
    private ProgressHandle dataSourceIngestProgress;
    private final Object dataSourceIngestProgressLock;
    private final List<String> filesInProgress;
    private long estimatedFilesToProcess;
    private long processedFiles;
    private ProgressHandle fileIngestProgress;
    private final Object fileIngestProgressLock;

    // These fields support cancellation of either the currently running data
    // source ingest module or the entire ingest job. 
    private volatile boolean currentDataSourceIngestModuleCancelled;
    private volatile boolean cancelled;

    // This field is used for generating ingest job diagnostic data.
    private final long startTime;

    /**
     * Constructs an ingest job.
     *
     * @param id The identifier assigned to the job.
     * @param dataSource The data source to be ingested.
     * @param processUnallocatedSpace Whether or not unallocated space should be
     * processed during the ingest job.
     */
    IngestJob(long id, Content dataSource, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.processUnallocatedSpace = processUnallocatedSpace;
        this.fileIngestPipelines = new LinkedBlockingQueue<>();
        this.filesInProgress = new ArrayList<>();
        this.dataSourceIngestProgressLock = new Object();
        this.fileIngestProgressLock = new Object();
        this.startTime = new Date().getTime();
    }

    /**
     * Gets the identifier assigned to the ingest job.
     *
     * @return The ingest job identifier.
     */
    long getId() {
        return this.id;
    }

    /**
     * Gets the data source to be ingested by this job.
     *
     * @return A reference to a Content object representing the data source.
     */
    Content getDataSource() {
        return this.dataSource;
    }

    /**
     * Queries whether or not unallocated space should be processed as part of
     * this job.
     *
     * @return True or false.
     */
    boolean shouldProcessUnallocatedSpace() {
        return this.processUnallocatedSpace;
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
            if (!this.dataSourceIngestPipeline.isEmpty()) {
                startDataSourceIngestProgressBar();
            }
            if (!this.fileIngestPipelines.peek().isEmpty()) {
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
        this.dataSourceIngestPipeline = new DataSourceIngestPipeline(this, ingestModuleTemplates);
        int numberOfFileIngestThreads = IngestManager.getInstance().getNumberOfFileIngestThreads();
        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
            this.fileIngestPipelines.put(new FileIngestPipeline(this, ingestModuleTemplates));
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

        // Start up the data source ingest pipeline.
        errors.addAll(this.dataSourceIngestPipeline.startUp());

        // Start up the file ingest pipelines (one per file ingest thread). 
        for (FileIngestPipeline pipeline : this.fileIngestPipelines) {
            errors.addAll(pipeline.startUp());
            if (!errors.isEmpty()) {
                // If there are start up errors, the ingest job will not proceed 
                // and the errors will ultimately be reported to the user for 
                // possible remedy so shut down the pipelines now that an
                // attempt has been made to start up the data source ingest  
                // pipeline and at least one copy of the file ingest pipeline.
                // pipeline. There is no need to complete starting up all of the 
                // file ingest pipeline copies since any additional start up 
                // errors are likely redundant.
                while (!this.fileIngestPipelines.isEmpty()) {
                    pipeline = this.fileIngestPipelines.poll();
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
        synchronized (this.dataSourceIngestProgressLock) {
            String displayName = NbBundle.getMessage(this.getClass(),
                    "IngestJob.progress.dataSourceIngest.initialDisplayName",
                    this.dataSource.getName());
            this.dataSourceIngestProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    // If this method is called, the user has already pressed 
                    // the cancel button on the progress bar and the OK button
                    // of a cancelation confirmation dialog supplied by 
                    // NetBeans. What remains to be done is to find out whether
                    // the user wants to cancel only the currently executing
                    // data source ingest module or the entire ingest job.
                    DataSourceIngestCancellationPanel panel = new DataSourceIngestCancellationPanel();
                    String dialogTitle = NbBundle.getMessage(IngestJob.this.getClass(), "IngestJob.cancellationDialog.title");
                    JOptionPane.showConfirmDialog(null, panel, dialogTitle, JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
                    if (panel.cancelAllDataSourceIngestModules()) {
                        IngestJob.this.cancel();
                    } else {
                        IngestJob.this.cancelCurrentDataSourceIngestModule();
                    }
                    return true;
                }
            });
            this.dataSourceIngestProgress.start();
            this.dataSourceIngestProgress.switchToIndeterminate();
        }
    }

    /**
     * Starts the file ingest progress bar.
     */
    private void startFileIngestProgressBar() {
        synchronized (this.fileIngestProgressLock) {
            String displayName = NbBundle.getMessage(this.getClass(),
                    "IngestJob.progress.fileIngest.displayName",
                    this.dataSource.getName());
            this.fileIngestProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                @Override
                public boolean cancel() {
                    // If this method is called, the user has already pressed 
                    // the cancel button on the progress bar and the OK button
                    // of a cancelation confirmation dialog supplied by 
                    // NetBeans. 
                    IngestJob.this.cancel();
                    return true;
                }
            });
            this.estimatedFilesToProcess = this.dataSource.accept(new GetFilesCountVisitor());
            this.fileIngestProgress.start();
            this.fileIngestProgress.switchToDeterminate((int) this.estimatedFilesToProcess);
        }
    }

    /**
     * Checks to see if this job has a data source ingest pipeline.
     *
     * @return True or false.
     */
    boolean hasDataSourceIngestPipeline() {
        return (this.dataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if the job has a file ingest pipeline.
     *
     * @return True or false.
     */
    boolean hasFileIngestPipeline() {
        return (this.fileIngestPipelines.peek().isEmpty() == false);
    }

    /**
     * Passes the data source for this job through the data source ingest
     * pipeline.
     *
     * @param task A data source ingest task wrapping the data source.
     * @throws InterruptedException
     */
    void process(DataSourceIngestTask task) throws InterruptedException {
        try {
            if (!this.isCancelled() && !this.dataSourceIngestPipeline.isEmpty()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(this.dataSourceIngestPipeline.process(task));
                if (!errors.isEmpty()) {
                    logIngestModuleErrors(errors);
                }
            }

            // The single data source ingest task for this job is done, so shut 
            // down the data source ingest progress bar right away.  
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.finish();
                    this.dataSourceIngestProgress = null;
                }
            }
        } finally {
            // No matter what happens, let the ingest scheduler know that this
            // task is completed.
            IngestJob.ingestScheduler.notifyTaskCompleted(task);
        }
    }

    /**
     * Updates the display name of the data source ingest progress bar.
     *
     * @param displayName The new display name.
     */
    void updateDataSourceIngestProgressBarDisplayName(String displayName) {
        if (!this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                this.dataSourceIngestProgress.setDisplayName(displayName);
            }
        }
    }

    /**
     * Updates the data source progress bar and switches it to determinate mode.
     *
     * @param workUnits Total number of work units for the processing of the
     * data source.
     */
    void switchDataSourceIngestProgressBarToDeterminate(int workUnits) {
        if (!this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                this.dataSourceIngestProgress.switchToDeterminate(workUnits);
            }
        }
    }

    /**
     * Switches the data source ingest progress bar to indeterminate mode. This
     * should be called if the total work units to process the data source is
     * unknown.
     */
    void switchDataSourceIngestProgressBarToIndeterminate() {
        if (!this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                this.dataSourceIngestProgress.switchToIndeterminate();
            }
        }
    }

    /**
     * Updates the data source ingest progress bar with the number of work units
     * performed, if in the determinate mode.
     *
     * @param workUnits Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(int workUnits) {
        if (!this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                this.dataSourceIngestProgress.progress("", workUnits);
            }
        }
    }

    /**
     * Updates the data source ingest progress bar display name.
     *
     * @param displayName The new display name.
     */
    void advanceDataSourceIngestProgressBar(String displayName) {
        if (!this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                this.dataSourceIngestProgress.progress(displayName);
            }
        }
    }

    /**
     * Updates the progress bar with the number of work units performed, if in
     * the determinate mode.
     *
     * @param message Message to display in sub-title
     * @param workUnits Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(String message, int workUnits) {
        if (!this.cancelled) {
            synchronized (this.fileIngestProgressLock) {
                this.dataSourceIngestProgress.progress(message, workUnits);
            }
        }
    }

    /**
     * Passes the a file from the data source for this job through the file
     * ingest pipeline.
     *
     * @param task A file ingest task.
     * @throws InterruptedException
     */
    void process(FileIngestTask task) throws InterruptedException {
        try {
            if (!this.isCancelled()) {
                // Get a file ingest pipeline not currently in use by another
                // file ingest thread.
                FileIngestPipeline pipeline = this.fileIngestPipelines.take();
                if (!pipeline.isEmpty()) {
                    // Get the file to process.
                    AbstractFile file = task.getFile();

                    // Update the file ingest progress bar.
                    synchronized (this.fileIngestProgressLock) {
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

                    // Update the file ingest progress bar again in case the 
                    // file was being displayed.
                    if (!this.cancelled) {
                        synchronized (this.fileIngestProgressLock) {
                            this.filesInProgress.remove(file.getName());
                            if (this.filesInProgress.size() > 0) {
                                this.fileIngestProgress.progress(this.filesInProgress.get(0));
                            } else {
                                this.fileIngestProgress.progress("");
                            }
                        }
                    }
                }

                // Relinquish the pipeline so it can be reused by another file 
                // ingest thread.
                this.fileIngestPipelines.put(pipeline);
            }
        } finally {
            // No matter what happens, let the ingest scheduler know that this
            // task is completed.
            IngestJob.ingestScheduler.notifyTaskCompleted(task);
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
        while (!this.fileIngestPipelines.isEmpty()) {
            FileIngestPipeline pipeline = fileIngestPipelines.poll();
            errors.addAll(pipeline.shutDown());
        }
        if (!errors.isEmpty()) {
            logIngestModuleErrors(errors);
        }

        // Finish the data source ingest progress bar, if it hasn't already 
        // been finished.
        synchronized (this.dataSourceIngestProgressLock) {
            if (this.dataSourceIngestProgress != null) {
                this.dataSourceIngestProgress.finish();
                this.dataSourceIngestProgress = null;
            }
        }

        // Finish the file ingest progress bar, if it hasn't already 
        // been finished.
        synchronized (this.fileIngestProgressLock) {
            if (this.fileIngestProgress != null) {
                this.fileIngestProgress.finish();
                this.fileIngestProgress = null;
            }
        }
    }

    /**
     * Write ingest module errors to the log.
     *
     * @param errors The errors.
     */
    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            IngestJob.logger.log(Level.SEVERE, error.getModuleDisplayName() + " experienced an error", error.getModuleError()); //NON-NLS
        }
    }

    /**
     * Requests a temporary cancellation of data source ingest in order to stop
     * the currently executing data source ingest module.
     */
    private void cancelCurrentDataSourceIngestModule() {
        this.currentDataSourceIngestModuleCancelled = true;
    }

    /**
     * Determines whether or not a temporary cancellation of data source ingest
     * in order to stop the currently executing data source ingest module is in
     * effect.
     *
     * @return True or false.
     */
    boolean currentDataSourceIngestModuleIsCancelled() {
        return this.currentDataSourceIngestModuleCancelled;
    }

    /**
     * Rescind a temporary cancellation of data source ingest in order to stop
     * the currently executing data source ingest module.
     */
    void currentDataSourceIngestModuleCancellationCompleted() {
        this.currentDataSourceIngestModuleCancelled = false;

        // A new progress bar must be created because the cancel button of the 
        // previously constructed component is disabled by NetBeans when the 
        // user selects the "OK" button of the cancellation confirmation dialog 
        // popped up by NetBeans when the progress bar cancel button was 
        // pressed.
        synchronized (this.dataSourceIngestProgressLock) {
            this.dataSourceIngestProgress.finish();
            this.dataSourceIngestProgress = null;
            this.startDataSourceIngestProgressBar();
        }
    }

    /**
     * Requests cancellation of ingest, i.e., a shutdown of the data source and
     * file ingest pipelines.
     */
    void cancel() {
        // Put a cancellation message on data source ingest progress bar, 
        // if it is still running.
        synchronized (this.dataSourceIngestProgressLock) {
            if (dataSourceIngestProgress != null) {
                final String displayName = NbBundle.getMessage(this.getClass(),
                        "IngestJob.progress.dataSourceIngest.initialDisplayName",
                        dataSource.getName());
                dataSourceIngestProgress.setDisplayName(
                        NbBundle.getMessage(this.getClass(),
                                "IngestJob.progress.cancelling",
                                displayName));
            }
        }

        // Put a cancellation message on the file ingest progress bar, 
        // if it is still running.
        synchronized (this.fileIngestProgressLock) {
            if (this.fileIngestProgress != null) {
                final String displayName = NbBundle.getMessage(this.getClass(),
                        "IngestJob.progress.fileIngest.displayName",
                        this.dataSource.getName());
                this.fileIngestProgress.setDisplayName(
                        NbBundle.getMessage(this.getClass(), "IngestJob.progress.cancelling",
                                displayName));
            }
        }

        // Tell the ingest scheduler to cancel all pending tasks.
        IngestJob.ingestScheduler.cancelIngestJob(this);
    }

    /**
     * Queries whether or not cancellation of ingest i.e., a shutdown of the
     * data source and file ingest pipelines, has been requested
     *
     * @return True or false.
     */
    boolean isCancelled() {
        return this.cancelled;
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

        /**
         * Constructs an object to stores basic diagnostic statistics for an
         * ingest job.
         */
        IngestJobStats() {
            this.startTime = IngestJob.this.startTime;
            synchronized (IngestJob.this.fileIngestProgressLock) {
                this.processedFiles = IngestJob.this.processedFiles;
                this.estimatedFilesToProcess = IngestJob.this.estimatedFilesToProcess;
                this.snapShotTime = new Date().getTime();
            }
        }

        /**
         * Gets files per second throughput since job started.
         *
         * @return Files processed per second (approximate).
         */
        double getSpeed() {
            return (double) processedFiles / ((snapShotTime - startTime) / 1000);
        }

        /**
         * Gets the the ingest job was started.
         *
         * @return The start time as number of milliseconds since January 1,
         * 1970, 00:00:00 GMT.
         */
        long getStartTime() {
            return startTime;
        }

        /**
         * Gets time these statistics were collected.
         *
         * @return The statistics collection time as number of milliseconds
         * since January 1, 1970, 00:00:00 GMT.
         */
        long getSnapshotTime() {
            return snapShotTime;
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
         * Gets an estimate of the files that still need to be processed for
         * this job.
         *
         * @return The estimate.
         */
        long getFilesEstimated() {
            return estimatedFilesToProcess;
        }
    }

}
