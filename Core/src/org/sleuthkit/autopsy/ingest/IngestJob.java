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
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * IngestJob encapsulates the settings, ingest module pipelines, and progress
 * bars that are used to process a data source when a user chooses to run a set
 * of ingest modules on the data source.
 */
final class IngestJob {

    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private static final IngestScheduler ingestTaskScheduler = IngestScheduler.getInstance();
    private final long id;
    private final Content dataSource;
    private final boolean processUnallocatedSpace;
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines = new LinkedBlockingQueue<>();
    private long estimatedFilesToProcess = 0L; // Guarded by this
    private long processedFiles = 0L; // Guarded by this
    // Contains names of files that are being run. Used to keep progress bar up to date. 
    private final List<String> filesInProgress = new ArrayList<>(); // Guarded by this. 
    private DataSourceIngestPipeline dataSourceIngestPipeline;
    private ProgressHandle dataSourceIngestProgress;
    private ProgressHandle fileIngestProgress;
    private volatile boolean cancelled = false;
    private long startTime;


    IngestJob(long id, Content dataSource, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.processUnallocatedSpace = processUnallocatedSpace;
        startTime = new Date().getTime();
    }

    long getId() {
        return id;
    }

    Content getDataSource() {
        return dataSource;
    }

    boolean shouldProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }

    /**
     * Startup the ingest pipelines and progress bars.
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
     * Create the file and data source ingest pipelines.
     *
     * @param ingestModuleTemplates Ingest module templates to use to populate
     * the pipelines.
     * @throws InterruptedException
     */
    private void createIngestPipelines(List<IngestModuleTemplate> ingestModuleTemplates) throws InterruptedException {
        IngestJobContext context = new IngestJobContext(this);
        dataSourceIngestPipeline = new DataSourceIngestPipeline(context, ingestModuleTemplates);
        int numberOfPipelines = IngestManager.getInstance().getNumberOfFileIngestThreads();
        for (int i = 0; i < numberOfPipelines; ++i) {
            fileIngestPipelines.put(new FileIngestPipeline(context, ingestModuleTemplates));
        }
    }

    /**
     * Startup each of the file and data source ingest modules to collect
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

    private void startDataSourceIngestProgressBar() {
        final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.dataSourceIngest.initialDisplayName",
                dataSource.getName());
        dataSourceIngestProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                handleCancelFromProgressBar();
                return true;
            }
        });
        dataSourceIngestProgress.start();
        dataSourceIngestProgress.switchToIndeterminate();
    }
    
    private void startFileIngestProgressBar() {
        final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.fileIngest.displayName",
                dataSource.getName());
        fileIngestProgress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                handleCancelFromProgressBar();
                return true;
            }
        });
        estimatedFilesToProcess = dataSource.accept(new GetFilesCountVisitor());
        fileIngestProgress.start();
        fileIngestProgress.switchToDeterminate((int) estimatedFilesToProcess);
    }
    
    /**
     * Common steps to cancel a job, regardless of what progress bar was
     * used to cancel the job. 
     */
    private void handleCancelFromProgressBar() {
        cancel();
        
        if (fileIngestProgress != null) {
            final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.fileIngest.displayName",
                dataSource.getName());
            fileIngestProgress.setDisplayName(
                    NbBundle.getMessage(this.getClass(), "IngestJob.progress.cancelling",
                    displayName));
        }
        
        if (dataSourceIngestProgress != null) {
            final String displayName = NbBundle.getMessage(this.getClass(),
                "IngestJob.progress.dataSourceIngest.initialDisplayName",
                dataSource.getName());
            dataSourceIngestProgress.setDisplayName(
                    NbBundle.getMessage(this.getClass(),
                    "IngestJob.progress.cancelling",
                    displayName));
        }
        IngestScheduler.getInstance().cancelIngestJob(IngestJob.this);
    }

    /**
     * Check to see if the job has a data source ingest pipeline.
     *
     * @return True or false.
     */
    boolean hasDataSourceIngestPipeline() {
        return (dataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Check to see if the job has a file ingest pipeline.
     *
     * @return True or false.
     */
    boolean hasFileIngestPipeline() {
        return (fileIngestPipelines.peek().isEmpty() == false);
    }

    /**
     * Executes the data source ingest module pipeline for the data source in the task
     * @param task
     * @throws InterruptedException 
     */
    void process(DataSourceIngestTask task) throws InterruptedException {
        try {
            if (!isCancelled() && !dataSourceIngestPipeline.isEmpty()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(dataSourceIngestPipeline.process(task, dataSourceIngestProgress));
                if (!errors.isEmpty()) {
                    logIngestModuleErrors(errors);
                }
            }
            
            // all data source ingest jobs are done for this task, so shut down progress bar
            if (null != dataSourceIngestProgress) {
                dataSourceIngestProgress.finish();
                // This is safe because this method will be called at most once per
                // ingest job and finish() will not be called while that single 
                // data source ingest task has not been reported complete by this
                // code to the ingest scheduler.
                dataSourceIngestProgress = null;
            }   
        }
        finally {
            ingestTaskScheduler.notifyTaskCompleted(task);
        }
    }

    
    /**
     * Executes the file ingest module pipeline for the file in the task
     * @param task
     * @throws InterruptedException 
     */
    void process(FileIngestTask task) throws InterruptedException {
        try {
            if (!isCancelled()) {
                FileIngestPipeline pipeline = fileIngestPipelines.take();
                if (!pipeline.isEmpty()) {
                    AbstractFile file = task.getFile();
                    synchronized (this) {
                        ++processedFiles;
                        if (processedFiles <= estimatedFilesToProcess) {
                            fileIngestProgress.progress(file.getName(), (int) processedFiles);
                        } else {
                            fileIngestProgress.progress(file.getName(), (int) estimatedFilesToProcess);
                        }
                        filesInProgress.add(file.getName());
                    }
                    
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.process(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }
                    
                    // update the progress bar in case this file was being displayed
                    synchronized (this) {
                        filesInProgress.remove(file.getName());
                        if (filesInProgress.size() > 0) {
                            fileIngestProgress.progress(filesInProgress.get(0));
                        }
                        else {
                            fileIngestProgress.progress("");
                        }
                    }
                }
                fileIngestPipelines.put(pipeline);
            }
        }
        finally {
            ingestTaskScheduler.notifyTaskCompleted(task);
        }
    }

    /**
     * Shutdown pipelines and progress bars
     * 
     */
    void finish() {
        List<IngestModuleError> errors = new ArrayList<>();
        while (!fileIngestPipelines.isEmpty()) {
            FileIngestPipeline pipeline = fileIngestPipelines.poll();
            errors.addAll(pipeline.shutDown());
        }
        if (!errors.isEmpty()) {
            logIngestModuleErrors(errors);
        }
        
        // NOTE: Nothing to do for datasource ingest modules -- no shutdown method

        if (dataSourceIngestProgress != null) {
            dataSourceIngestProgress.finish();
        }
        
        if (fileIngestProgress != null) {
            fileIngestProgress.finish();
        }
    }

    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            logger.log(Level.SEVERE, error.getModuleDisplayName() + " experienced an error", error.getModuleError()); //NON-NLS
        }
    }

    boolean isCancelled() {
        return cancelled;
    }

    void cancel() {
        cancelled = true;
    }
    
    /**
     * Stores basic info for a given ingest job.
     */
    class IngestJobStats {
        private final long startTime;
        private final long processedFiles;
        private final long estimatedFilesToProcess;
        private final long snapShotTime;
        
        IngestJobStats () {
            synchronized (IngestJob.this) {
                this.startTime = IngestJob.this.startTime;
                this.processedFiles = IngestJob.this.processedFiles;
                this.estimatedFilesToProcess = IngestJob.this.estimatedFilesToProcess;
                snapShotTime = new Date().getTime();
            }
        }
        
        /**
         * Get files per second throughput since job started
         * @return 
         */
        double getSpeed() {
            return (double)processedFiles / ((snapShotTime - startTime)/1000);
        }
        
        long getStartTime() {
            return startTime;
        }
        
        /**
         * Get time these stats were collected
         * @return 
         */
        long getSnapshotTime() {
            return snapShotTime;
        }
        
        /**
         * Number of files processed by job so far
         * @return 
         */
        long getFilesProcessed() {
            return processedFiles;
        }
        
        long getFilesEstimated() {
            return estimatedFilesToProcess;
        }
    }
    
    /**
     * Get some basic performance stats on this job
     * @return 
     */
    IngestJobStats getStats() {
        return new IngestJobStats();
    }
}
