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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
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

    /**
     * An ingest job may have multiple stages.
     */
    private enum Stages {

        /**
         * High priority data source ingest modules and file ingest modules.
         */
        FIRST,
        /**
         * Lower priority, usually long-running, data source ingest modules.
         */
        SECOND
    };

    private static final Logger logger = Logger.getLogger(IngestJob.class.getName());
    private static final IngestTasksScheduler taskScheduler = IngestTasksScheduler.getInstance();

    /**
     * These static fields are used for the creation and management of ingest
     * jobs in progress.
     */
    private static volatile boolean jobCreationIsEnabled;
    private static final AtomicLong nextJobId = new AtomicLong(0L);
    private static final ConcurrentHashMap<Long, IngestJob> jobsById = new ConcurrentHashMap<>();

    /**
     * These fields define the ingest job and the work it entails.
     */
    private final long id;
    private final Content dataSource;
    private final boolean processUnallocatedSpace;
    private Stages stage;
    private DataSourceIngestPipeline dataSourceIngestPipeline;
    private DataSourceIngestPipeline firstStageDataSourceIngestPipeline;
    private DataSourceIngestPipeline secondStageDataSourceIngestPipeline;
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines;

    /**
     * These fields are used to update ingest progress UI components for the
     * job. The filesInProgress collection contains the names of the files that
     * are in the file ingest pipelines and the two file counter fields are used
     * to update the file ingest progress bar.
     */
    private ProgressHandle dataSourceIngestProgress;
    private final Object dataSourceIngestProgressLock;
    private final List<String> filesInProgress;
    private long estimatedFilesToProcess;
    private long processedFiles;
    private ProgressHandle fileIngestProgress;
    private final Object fileIngestProgressLock;

    /**
     * These fields support cancellation of either the currently running data
     * source ingest module or the entire ingest job.
     */
    private volatile boolean currentDataSourceIngestModuleCancelled;
    private volatile boolean cancelled;

    /**
     * This field is used for generating ingest job diagnostic data.
     */
    private final long startTime;

    /**
     * Enables and disables ingest job creation.
     *
     * @param enabled True or false.
     */
    static void jobCreationEnabled(boolean enabled) {
        IngestJob.jobCreationIsEnabled = enabled;
    }

    /**
     * Starts an ingest job for a data source.
     *
     * @param dataSource The data source to ingest.
     * @param ingestModuleTemplates The ingest module templates to use to create
     * the ingest pipelines for the job.
     * @param processUnallocatedSpace Whether or not the job should include
     * processing of unallocated space.
     * @return A collection of ingest module start up errors, empty on success.
     */
    static List<IngestModuleError> startJob(Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        List<IngestModuleError> errors = new ArrayList<>();
        if (IngestJob.jobCreationIsEnabled) {
            long jobId = nextJobId.incrementAndGet();
            IngestJob job = new IngestJob(jobId, dataSource, processUnallocatedSpace);
            IngestJob.jobsById.put(jobId, job);
            errors = job.start(ingestModuleTemplates);
            if (errors.isEmpty() && job.hasIngestPipeline()) {
                IngestManager.getInstance().fireIngestJobStarted(jobId);
                IngestJob.logger.log(Level.INFO, "Ingest job {0} started", jobId);
            } else {
                IngestJob.jobsById.remove(jobId);
            }
        }
        return errors;
    }

    /**
     * Queries whether or not ingest jobs are running.
     *
     * @return True or false.
     */
    static boolean ingestJobsAreRunning() {
        return !jobsById.isEmpty();
    }

    /**
     * Gets snapshots of the state of all running ingest jobs.
     *
     * @return A list of ingest job state snapshots.
     */
    static List<IngestJobSnapshot> getJobSnapshots() {
        List<IngestJobSnapshot> snapShots = new ArrayList<>();
        for (IngestJob job : IngestJob.jobsById.values()) {
            snapShots.add(job.getIngestJobSnapshot());
        }
        return snapShots;
    }

    /**
     * Cancels all running ingest jobs.
     */
    static void cancelAllJobs() {
        for (IngestJob job : jobsById.values()) {
            job.cancel();
        }
    }

    /**
     * Constructs an ingest job.
     *
     * @param id The identifier assigned to the job.
     * @param dataSource The data source to be ingested.
     * @param processUnallocatedSpace Whether or not unallocated space should be
     * processed during the ingest job.
     */
    private IngestJob(long id, Content dataSource, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.processUnallocatedSpace = processUnallocatedSpace;
        this.stage = IngestJob.Stages.FIRST;
        this.fileIngestPipelines = new LinkedBlockingQueue<>();
        this.filesInProgress = new ArrayList<>();
        this.dataSourceIngestProgressLock = new Object();
        this.fileIngestProgressLock = new Object();
        this.startTime = new Date().getTime();
    }

    /**
     * Gets the identifier assigned to this job.
     *
     * @return The job identifier.
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
     * Passes the data source for this job through a data source ingest
     * pipeline.
     *
     * @param task A data source ingest task wrapping the data source.
     */
    void process(DataSourceIngestTask task) {
        try {
            if (!this.isCancelled() && !this.dataSourceIngestPipeline.isEmpty()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(this.dataSourceIngestPipeline.process(task));
                if (!errors.isEmpty()) {
                    logIngestModuleErrors(errors);
                }
            }

            // Shut down the data source ingest progress bar right away.  
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.finish();
                    this.dataSourceIngestProgress = null;
                }
            }
        } finally {
            // No matter what happens, let the task scheduler know that this
            // task is completed and check for job completion.
            IngestJob.taskScheduler.notifyTaskCompleted(task);
            if (IngestJob.taskScheduler.tasksForJobAreCompleted(this)) {
                this.handleTasksCompleted();
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
                /**
                 * Get a file ingest pipeline not currently in use by another
                 * file ingest thread.
                 */
                FileIngestPipeline pipeline = this.fileIngestPipelines.take();
                if (!pipeline.isEmpty()) {
                    /**
                     * Get the file to process.
                     */
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
            // No matter what happens, let the task scheduler know that this
            // task is completed and check for job completion.
            IngestJob.taskScheduler.notifyTaskCompleted(task);
            if (IngestJob.taskScheduler.tasksForJobAreCompleted(this)) {
                this.handleTasksCompleted();
            }
        }
    }

    /**
     * Adds more files to an ingest job, i.e., derived or carved files. Not
     * currently supported for the second stage of the job.
     *
     * @param files A list of files to add.
     */
    void addFiles(List<AbstractFile> files) {
        if (IngestJob.Stages.FIRST == this.stage) {
            for (AbstractFile file : files) {
                IngestJob.taskScheduler.scheduleFileIngestTask(this, file);
            }
        } else {
            IngestJob.logger.log(Level.SEVERE, "Adding files during second stage not supported"); //NON-NLS
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
     * Switches the data source progress bar to determinate mode. This should be
     * called if the total work units to process the data source is known.
     *
     * @param workUnits Total number of work units for the processing of the
     * data source.
     */
    void switchDataSourceIngestProgressBarToDeterminate(int workUnits) {
        if (!this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.switchToDeterminate(workUnits);
                }
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
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.switchToIndeterminate();
                }
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
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.progress("", workUnits);
                }
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
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.progress(displayName);
                }
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

        this.cancelled = true;

        /**
         * Tell the task scheduler to cancel all pending tasks.
         */
        IngestJob.taskScheduler.cancelPendingTasksForIngestJob(this);
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
     * Creates the file and data source ingest pipelines.
     *
     * @param ingestModuleTemplates Ingest module templates to use to populate
     * the pipelines.
     */
    private void createIngestPipelines(List<IngestModuleTemplate> ingestModuleTemplates) {
        // Make mappings of ingest module factory class names to templates.
        Map<String, IngestModuleTemplate> dataSourceModuleTemplates = new HashMap<>();
        Map<String, IngestModuleTemplate> fileModuleTemplates = new HashMap<>();
        for (IngestModuleTemplate template : ingestModuleTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                dataSourceModuleTemplates.put(template.getModuleFactory().getClass().getCanonicalName(), template);
            }
            if (template.isFileIngestModuleTemplate()) {
                fileModuleTemplates.put(template.getModuleFactory().getClass().getCanonicalName(), template);
            }
        }

        // Use the mappings and the ingest pipelines configuration to create
        // ordered lists of ingest module templates for each ingest pipeline.
        IngestPipelinesConfiguration pipelineConfigs = IngestPipelinesConfiguration.getInstance();
        List<IngestModuleTemplate> firstStageDataSourceModuleTemplates = this.getConfiguredIngestModuleTemplates(dataSourceModuleTemplates, pipelineConfigs.getStageOneDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> fileIngestModuleTemplates = this.getConfiguredIngestModuleTemplates(fileModuleTemplates, pipelineConfigs.getFileIngestPipelineConfig());
        List<IngestModuleTemplate> secondStageDataSourceModuleTemplates = this.getConfiguredIngestModuleTemplates(dataSourceModuleTemplates, pipelineConfigs.getStageTwoDataSourceIngestPipelineConfig());

        // Add any module templates that were not specified in the pipeline
        // configurations to an appropriate pipeline - either the first stage
        // data source ingest pipeline or the file ingest pipeline.
        for (IngestModuleTemplate template : dataSourceModuleTemplates.values()) {
            firstStageDataSourceModuleTemplates.add(template);
        }
        for (IngestModuleTemplate template : fileModuleTemplates.values()) {
            fileIngestModuleTemplates.add(template);
        }

        // Contruct the data source ingest pipelines.
        this.firstStageDataSourceIngestPipeline = new DataSourceIngestPipeline(this, firstStageDataSourceModuleTemplates);
        this.secondStageDataSourceIngestPipeline = new DataSourceIngestPipeline(this, secondStageDataSourceModuleTemplates);
        this.dataSourceIngestPipeline = firstStageDataSourceIngestPipeline;

        // Construct the file ingest pipelines.
        try {
            int numberOfFileIngestThreads = IngestManager.getInstance().getNumberOfFileIngestThreads();
            for (int i = 0; i < numberOfFileIngestThreads; ++i) {
                this.fileIngestPipelines.put(new FileIngestPipeline(this, fileIngestModuleTemplates));
            }
        } catch (InterruptedException ex) {
            /**
             * The current thread was interrupted while blocked on a full queue.
             * Blocking should never happen here, but reset the interrupted flag
             * rather than just swallowing the exception.
             */
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Use an ordered list of ingest module factory class names to create an
     * ordered subset of a collection ingest module templates. The ingest module
     * templates are removed from the input collection as they are added to the
     * output collection.
     *
     * @param ingestModuleTemplates A mapping of ingest module factory class
     * names to ingest module templates.
     * @param pipelineConfig An ordered list of ingest module factory class
     * names representing an ingest pipeline.
     * @return
     */
    List<IngestModuleTemplate> getConfiguredIngestModuleTemplates(Map<String, IngestModuleTemplate> ingestModuleTemplates, List<String> pipelineConfig) {
        List<IngestModuleTemplate> templates = new ArrayList<>();
        for (String moduleClassName : pipelineConfig) {
            if (ingestModuleTemplates.containsKey(moduleClassName)) {
                templates.add(ingestModuleTemplates.remove(moduleClassName));
            }
        }
        return templates;
    }

    /**
     * Starts up the ingest pipelines and ingest progress bars.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> start(List<IngestModuleTemplate> ingestModuleTemplates) {
        this.createIngestPipelines(ingestModuleTemplates);
        List<IngestModuleError> errors = startUpIngestPipelines();
        if (errors.isEmpty()) {
            if (this.hasFirstStageDataSourceIngestPipeline() || this.hasFileIngestPipeline()) {
                // There is at least one first stage pipeline.
                this.startFirstStage();
            } else if (this.hasSecondStageDataSourceIngestPipeline()) {
                // There is no first stage pipeline, but there is a second stage
                // ingest pipeline.
                this.startSecondStage();
            }
        }
        return errors;
    }

    /**
     * Starts the first stage of the job.
     */
    private void startFirstStage() {
        this.stage = IngestJob.Stages.FIRST;

        /**
         * Start one or both of the first stage progress bars.
         */
        if (this.hasFirstStageDataSourceIngestPipeline()) {
            this.startDataSourceIngestProgressBar();
        }
        if (this.hasFileIngestPipeline()) {
            this.startFileIngestProgressBar();
        }

        /**
         * Schedule the first stage tasks.
         */
        if (this.hasFirstStageDataSourceIngestPipeline() && this.hasFileIngestPipeline()) {
            IngestJob.taskScheduler.scheduleIngestTasks(this);
        } else if (this.hasFirstStageDataSourceIngestPipeline()) {
            IngestJob.taskScheduler.scheduleDataSourceIngestTask(this);
        } else {
            IngestJob.taskScheduler.scheduleFileIngestTasks(this);

            /**
             * No data source ingest task has been scheduled for this stage, and
             * it is possible, if unlikely, that no file ingest tasks were
             * actually scheduled since there are files that get filtered out by
             * the tasks scheduler. In this special case, an ingest thread will
             * never get to make the following check for this stage of the job.
             */
            if (IngestJob.taskScheduler.tasksForJobAreCompleted(this)) {
                this.handleTasksCompleted();
            }
        }
    }

    /**
     * Starts the second stage of the ingest job.
     */
    private void startSecondStage() {
        this.stage = IngestJob.Stages.SECOND;
        this.startDataSourceIngestProgressBar();
        this.dataSourceIngestPipeline = this.secondStageDataSourceIngestPipeline;
        IngestJob.taskScheduler.scheduleDataSourceIngestTask(this);
    }

    /**
     * Checks to see if this job has at least one ingest pipeline.
     *
     * @return True or false.
     */
    private boolean hasIngestPipeline() {
        return this.hasFirstStageDataSourceIngestPipeline()
                || this.hasFileIngestPipeline()
                || this.hasSecondStageDataSourceIngestPipeline();
    }

    /**
     * Checks to see if this job has a first stage data source ingest pipeline.
     *
     * @return True or false.
     */
    private boolean hasFirstStageDataSourceIngestPipeline() {
        return (this.firstStageDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if this job has a second stage data source ingest pipeline.
     *
     * @return True or false.
     */
    private boolean hasSecondStageDataSourceIngestPipeline() {
        return (this.secondStageDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if the job has a file ingest pipeline.
     *
     * @return True or false.
     */
    private boolean hasFileIngestPipeline() {
        return (this.fileIngestPipelines.peek().isEmpty() == false);
    }

    /**
     * Starts up each of the file and data source ingest modules to collect
     * possible errors.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestPipelines() {
        List<IngestModuleError> errors = new ArrayList<>();

        // Start up the first stage data source ingest pipeline.
        errors.addAll(this.dataSourceIngestPipeline.startUp());

        // Start up the second stage data source ingest pipeline.
        errors.addAll(this.secondStageDataSourceIngestPipeline.startUp());

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
     * Handles when all ingest tasks for this job are completed by finishing the
     * current stage and possibly starting the next stage.
     */
    private void handleTasksCompleted() {
        switch (this.stage) {
            case FIRST:
                this.finishFirstStage();
                break;
            case SECOND:
                this.finish();
                break;
        }
    }

    /**
     * Shuts down the first stage ingest pipelines and progress bars and starts
     * the second stage, if appropriate.
     */
    private void finishFirstStage() {
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

        // Finish the first stage data source ingest progress bar, if it hasn't 
        // already been finished.
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

        /**
         * Start the second stage, if appropriate.
         */
        if (!this.cancelled && this.hasSecondStageDataSourceIngestPipeline()) {
            this.startSecondStage();
        } else {
            this.finish();
        }
    }

    /**
     * Shuts down the ingest pipelines and progress bars for this job.
     */
    private void finish() {
        // Finish the second stage data source ingest progress bar, if it hasn't 
        // already been finished.
        synchronized (this.dataSourceIngestProgressLock) {
            if (this.dataSourceIngestProgress != null) {
                this.dataSourceIngestProgress.finish();
                this.dataSourceIngestProgress = null;
            }
        }

        IngestJob.jobsById.remove(this.id);
        if (!this.isCancelled()) {
            logger.log(Level.INFO, "Ingest job {0} completed", this.id);
            IngestManager.getInstance().fireIngestJobCompleted(this.id);
        } else {
            logger.log(Level.INFO, "Ingest job {0} cancelled", this.id);
            IngestManager.getInstance().fireIngestJobCancelled(this.id);
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
     * Gets a snapshot of this jobs state and performance.
     *
     * @return An ingest job statistics object.
     */
    private IngestJobSnapshot getIngestJobSnapshot() {
        return new IngestJobSnapshot();
    }

    /**
     * Stores basic diagnostic statistics for an ingest job.
     */
    class IngestJobSnapshot {

        private final long jobId;
        private final String dataSource;
        private final long startTime;
        private final long processedFiles;
        private final long estimatedFilesToProcess;
        private final long snapShotTime;
        private final IngestTasksScheduler.IngestJobTasksSnapshot tasksSnapshot;

        /**
         * Constructs an object to stores basic diagnostic statistics for an
         * ingest job.
         */
        IngestJobSnapshot() {
            this.jobId = IngestJob.this.id;
            this.dataSource = IngestJob.this.dataSource.getName();
            this.startTime = IngestJob.this.startTime;
            synchronized (IngestJob.this.fileIngestProgressLock) {
                this.processedFiles = IngestJob.this.processedFiles;
                this.estimatedFilesToProcess = IngestJob.this.estimatedFilesToProcess;
                this.snapShotTime = new Date().getTime();
            }
            this.tasksSnapshot = IngestJob.taskScheduler.getTasksSnapshotForJob(this.jobId);
        }

        long getJobId() {
            return this.jobId;
        }

        String getDataSource() {
            return dataSource;
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

        long getRootQueueSize() {
            return this.tasksSnapshot.getRootQueueSize();
        }

        long getDirQueueSize() {
            return this.tasksSnapshot.getDirectoryTasksQueueSize();
        }

        long getFileQueueSize() {
            return this.tasksSnapshot.getFileQueueSize();
        }

        long getDsQueueSize() {
            return this.tasksSnapshot.getDsQueueSize();
        }

        long getRunningListSize() {
            return this.tasksSnapshot.getRunningListSize();
        }

    }

}
