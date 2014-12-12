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
final class DataSourceIngestJob {

    private static final Logger logger = Logger.getLogger(DataSourceIngestJob.class.getName());
    private static final AtomicLong nextJobId = new AtomicLong(0L);

    /**
     * These fields define a data source ingest job: the parent ingest job, a
     * unique ID, the user's ingest job settings, and the data source to be
     * processed.
     */
    private final IngestJob parentJob;
    private final long id;
    private final IngestJobSettings settings;
    private final Content dataSource;

    /**
     * An ingest job runs in stages.
     */
    private static enum Stages {

        /**
         * Setting up for processing.
         */
        INITIALIZATION,
        /**
         * Running high priority data source level ingest modules and file level
         * ingest modules.
         */
        FIRST,
        /**
         * Running lower priority, usually long-running, data source level
         * ingest modules.
         */
        SECOND,
        /**
         * Cleaning up.
         */
        FINALIZATION
    };
    private Stages stage;
    private final Object stageCompletionCheckLock;

    /**
     * An ingest job has separate data source level ingest module pipelines for
     * each processing stage. Longer running, lower priority modules belong in
     * the second stage pipeline.
     */
    private final Object dataSourceIngestPipelineLock;
    private DataSourceIngestPipeline firstStageDataSourceIngestPipeline;
    private DataSourceIngestPipeline secondStageDataSourceIngestPipeline;
    private DataSourceIngestPipeline currentDataSourceIngestPipeline;

    /**
     * An ingest job has a collection of identical file level ingest module
     * pipelines, one for each file level ingest thread in the ingest manager.
     */
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelines;

    /**
     * An ingest job supports cancellation of either the currently running data
     * source level ingest module or the entire ingest job.
     */
    private volatile boolean currentDataSourceIngestModuleCancelled;
    private volatile boolean cancelled;

    /**
     * An ingest job uses the task scheduler singleton to create and queue the
     * ingest tasks that make up the job.
     */
    private static final IngestTasksScheduler taskScheduler = IngestTasksScheduler.getInstance();

    /**
     * These fields are used to report data source level ingest progress for the
     * ingest job.
     */
    private final Object dataSourceIngestProgressLock;
    private ProgressHandle dataSourceIngestProgress;

    /**
     * These fields are used to report file level ingest task progress for the
     * ingest job.
     */
    private final Object fileIngestProgressLock;
    private final List<String> filesInProgress;
    private long estimatedFilesToProcess;
    private long processedFiles;
    private ProgressHandle fileIngestProgress;

    /**
     * This field is used to record the creation of the ingest job.
     */
    private final long createTime;

    /**
     * Constructs an ingest job.
     *
     * @param parentJob The ingest job of which this data source ingest job is a
     * part.
     * @param dataSource The data source to be ingested.
     * @param settings The settings for the ingest job.
     */
    DataSourceIngestJob(IngestJob parentJob, Content dataSource, IngestJobSettings settings) {
        this.parentJob = parentJob;
        this.id = DataSourceIngestJob.nextJobId.getAndIncrement();
        this.dataSource = dataSource;
        this.settings = settings;
        this.dataSourceIngestPipelineLock = new Object();
        this.fileIngestPipelines = new LinkedBlockingQueue<>();
        this.filesInProgress = new ArrayList<>();
        this.dataSourceIngestProgressLock = new Object();
        this.fileIngestProgressLock = new Object();
        this.stage = DataSourceIngestJob.Stages.INITIALIZATION;
        this.stageCompletionCheckLock = new Object();
        this.createTime = new Date().getTime();
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
     * @return A Content object representing the data source.
     */
    Content getDataSource() {
        return this.dataSource;
    }

    /**
     * Gets whether or not unallocated space should be processed as part of this
     * job.
     *
     * @return True or false.
     */
    boolean shouldProcessUnallocatedSpace() {
        return this.settings.getProcessUnallocatedSpace();
    }

    /**
     * Passes the data source for this job through the currently active data
     * source level ingest pipeline.
     *
     * @param task A data source ingest task wrapping the data source.
     */
    void process(DataSourceIngestTask task) {
        try {
            synchronized (this.dataSourceIngestPipelineLock) {
                if (!this.isCancelled() && !this.currentDataSourceIngestPipeline.isEmpty()) {
                    /**
                     * Run the data source through the pipeline.
                     */
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(this.currentDataSourceIngestPipeline.process(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }
                }
            }

            /**
             * Shut down the data source ingest progress bar right away. Data
             * source-level processing is finished for this stage.
             */
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.finish();
                    this.dataSourceIngestProgress = null;
                }
            }
        } finally {
            /**
             * No matter what happens, do ingest task bookkeeping.
             */
            DataSourceIngestJob.taskScheduler.notifyTaskCompleted(task);
            this.checkForStageCompleted();
        }
    }

    /**
     * Passes a file from the data source for this job through the file level
     * ingest pipeline.
     *
     * @param task A file ingest task.
     * @throws InterruptedException if the thread executing this code is
     * interrupted while blocked on taking from or putting to the file ingest
     * pipelines collection.
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

                    /**
                     * Update the file ingest progress bar.
                     */
                    synchronized (this.fileIngestProgressLock) {
                        ++this.processedFiles;
                        if (this.processedFiles <= this.estimatedFilesToProcess) {
                            this.fileIngestProgress.progress(file.getName(), (int) this.processedFiles);
                        } else {
                            this.fileIngestProgress.progress(file.getName(), (int) this.estimatedFilesToProcess);
                        }
                        this.filesInProgress.add(file.getName());
                    }

                    /**
                     * Run the file through the pipeline.
                     */
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.process(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }

                    /**
                     * Update the file ingest progress bar again, in case the
                     * file was being displayed.
                     */
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

                /**
                 * Relinquish the pipeline so it can be reused by another file
                 * ingest thread.
                 */
                this.fileIngestPipelines.put(pipeline);
            }
        } finally {
            /**
             * No matter what happens, do ingest task bookkeeping.
             */
            DataSourceIngestJob.taskScheduler.notifyTaskCompleted(task);
            this.checkForStageCompleted();
        }
    }

    /**
     * Adds more files to an ingest job, i.e., extracted or carved files. Not
     * currently supported for the second stage of the job.
     *
     * @param files A list of files to add.
     */
    void addFiles(List<AbstractFile> files) {
        /**
         * Note: This implementation assumes that this is being called by an an
         * ingest module running code on an ingest thread that is holding a
         * reference to an ingest task, so no task completion check is done.
         */
        if (DataSourceIngestJob.Stages.FIRST == this.stage) {
            for (AbstractFile file : files) {
                DataSourceIngestJob.taskScheduler.scheduleFileIngestTask(this, file);
            }
        } else {
            DataSourceIngestJob.logger.log(Level.SEVERE, "Adding files during second stage not supported"); //NON-NLS
        }

        /**
         * The intended clients of this method are ingest modules running code
         * on an ingest thread that is holding a reference to an ingest task, in
         * which case a task completion check would not be necessary. This is a
         * bit of defensive programming.
         */
        this.checkForStageCompleted();
    }

    /**
     * Updates the display name of the data source level ingest progress bar.
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
     * Switches the data source level ingest progress bar to determinate mode.
     * This should be called if the total work units to process the data source
     * is known.
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
     * Switches the data source level ingest progress bar to indeterminate mode.
     * This should be called if the total work units to process the data source
     * is unknown.
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
     * Updates the data source level ingest progress bar with the number of work
     * units performed, if in the determinate mode.
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
     * Updates the data source level ingest progress with a new task name, where
     * the task name is the "subtitle" under the display name.
     *
     * @param currentTask The task name.
     */
    void advanceDataSourceIngestProgressBar(String currentTask) {
        if (!this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.progress(currentTask);
                }
            }
        }
    }

    /**
     * Updates the data source level ingest progress bar with a new task name
     * and the number of work units performed, if in the determinate mode. The
     * task name is the "subtitle" under the display name.
     *
     * @param currentTask The task name.
     * @param workUnits Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(String currentTask, int workUnits) {
        if (!this.cancelled) {
            synchronized (this.fileIngestProgressLock) {
                this.dataSourceIngestProgress.progress(currentTask, workUnits);
            }
        }
    }

    /**
     * Queries whether or not a temporary cancellation of data source level
     * ingest in order to stop the currently executing data source level ingest
     * module is in effect.
     *
     * @return True or false.
     */
    boolean currentDataSourceIngestModuleIsCancelled() {
        return this.currentDataSourceIngestModuleCancelled;
    }

    /**
     * Rescind a temporary cancellation of data source level ingest that was
     * used to stop a single data source level ingest module.
     */
    void currentDataSourceIngestModuleCancellationCompleted() {
        this.currentDataSourceIngestModuleCancelled = false;

        /**
         * A new progress bar must be created because the cancel button of the
         * previously constructed component is disabled by NetBeans when the
         * user selects the "OK" button of the cancellation confirmation dialog
         * popped up by NetBeans when the progress bar cancel button was
         * pressed.
         */
        synchronized (this.dataSourceIngestProgressLock) {
            this.dataSourceIngestProgress.finish();
            this.dataSourceIngestProgress = null;
            this.startDataSourceIngestProgressBar();
        }
    }

    /**
     * Requests cancellation of ingest, i.e., a shutdown of the data source
     * level and file level ingest pipelines.
     */
    void cancel() {
        /**
         * Put a cancellation message on data source level ingest progress bar,
         * if it is still running.
         */
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

        /**
         * Put a cancellation message on the file level ingest progress bar, if
         * it is still running.
         */
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
         * Tell the task scheduler to cancel all pending tasks, i.e., tasks not
         * not being performed by an ingest thread.
         */
        DataSourceIngestJob.taskScheduler.cancelPendingTasksForIngestJob(this);
        this.checkForStageCompleted();
    }

    /**
     * Queries whether or not cancellation of ingest i.e., a shutdown of the
     * data source level and file level ingest pipelines, has been requested.
     *
     * @return True or false.
     */
    boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Starts up the ingest pipelines and ingest progress bars.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    List<IngestModuleError> start() {
        this.createIngestPipelines(settings.getEnabledIngestModuleTemplates());
        List<IngestModuleError> errors = startUpIngestPipelines();
        if (errors.isEmpty()) {
            if (this.hasFirstStageDataSourceIngestPipeline() || this.hasFileIngestPipeline()) {
                this.startFirstStage();
            } else if (this.hasSecondStageDataSourceIngestPipeline()) {
                this.startSecondStage();
            }
        }
        return errors;
    }

    /**
     * Creates the file and data source ingest pipelines.
     *
     * @param ingestModuleTemplates Ingest module templates to use to populate
     * the pipelines.
     */
    private void createIngestPipelines(List<IngestModuleTemplate> ingestModuleTemplates) {
        /**
         * Make mappings of ingest module factory class names to templates.
         */
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

        /**
         * Use the mappings and the ingest pipelines configuration to create
         * ordered lists of ingest module templates for each ingest pipeline.
         */
        IngestPipelinesConfiguration pipelineConfigs = IngestPipelinesConfiguration.getInstance();
        List<IngestModuleTemplate> firstStageDataSourceModuleTemplates = DataSourceIngestJob.getConfiguredIngestModuleTemplates(dataSourceModuleTemplates, pipelineConfigs.getStageOneDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> fileIngestModuleTemplates = DataSourceIngestJob.getConfiguredIngestModuleTemplates(fileModuleTemplates, pipelineConfigs.getFileIngestPipelineConfig());
        List<IngestModuleTemplate> secondStageDataSourceModuleTemplates = DataSourceIngestJob.getConfiguredIngestModuleTemplates(dataSourceModuleTemplates, pipelineConfigs.getStageTwoDataSourceIngestPipelineConfig());

        /**
         * Add any module templates that were not specified in the pipelines
         * configuration to an appropriate pipeline - either the first stage
         * data source ingest pipeline or the file ingest pipeline.
         */
        for (IngestModuleTemplate template : dataSourceModuleTemplates.values()) {
            firstStageDataSourceModuleTemplates.add(template);
        }
        for (IngestModuleTemplate template : fileModuleTemplates.values()) {
            fileIngestModuleTemplates.add(template);
        }

        /**
         * Construct the data source ingest pipelines.
         */
        this.firstStageDataSourceIngestPipeline = new DataSourceIngestPipeline(this, firstStageDataSourceModuleTemplates);
        this.secondStageDataSourceIngestPipeline = new DataSourceIngestPipeline(this, secondStageDataSourceModuleTemplates);

        /**
         * Construct the file ingest pipelines, one per file ingest thread.
         */
        try {
            int numberOfFileIngestThreads = IngestManager.getInstance().getNumberOfFileIngestThreads();
            for (int i = 0; i < numberOfFileIngestThreads; ++i) {
                this.fileIngestPipelines.put(new FileIngestPipeline(this, fileIngestModuleTemplates));
            }
        } catch (InterruptedException ex) {
            /**
             * The current thread was interrupted while blocked on a full queue.
             * Blocking should actually never happen here, but reset the
             * interrupted flag rather than just swallowing the exception.
             */
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Use an ordered list of ingest module factory class names to create an
     * ordered output list of ingest module templates for an ingest pipeline.
     * The ingest module templates are removed from the input collection as they
     * are added to the output collection.
     *
     * @param ingestModuleTemplates A mapping of ingest module factory class
     * names to ingest module templates.
     * @param pipelineConfig An ordered list of ingest module factory class
     * names representing an ingest pipeline.
     * @return
     */
    private static List<IngestModuleTemplate> getConfiguredIngestModuleTemplates(Map<String, IngestModuleTemplate> ingestModuleTemplates, List<String> pipelineConfig) {
        List<IngestModuleTemplate> templates = new ArrayList<>();
        for (String moduleClassName : pipelineConfig) {
            if (ingestModuleTemplates.containsKey(moduleClassName)) {
                templates.add(ingestModuleTemplates.remove(moduleClassName));
            }
        }
        return templates;
    }

    /**
     * Starts the first stage of the job.
     */
    private void startFirstStage() {
        this.stage = DataSourceIngestJob.Stages.FIRST;

        /**
         * Start one or both of the first stage ingest progress bars.
         */
        if (this.hasFirstStageDataSourceIngestPipeline()) {
            this.startDataSourceIngestProgressBar();
        }
        if (this.hasFileIngestPipeline()) {
            this.startFileIngestProgressBar();
        }

        /**
         * Make the first stage data source level ingest pipeline the current
         * data source level pipeline.
         */
        synchronized (this.dataSourceIngestPipelineLock) {
            this.currentDataSourceIngestPipeline = this.firstStageDataSourceIngestPipeline;
        }

        /**
         * Schedule the first stage tasks.
         */
        if (this.hasFirstStageDataSourceIngestPipeline() && this.hasFileIngestPipeline()) {
            DataSourceIngestJob.taskScheduler.scheduleIngestTasks(this);
        } else if (this.hasFirstStageDataSourceIngestPipeline()) {
            DataSourceIngestJob.taskScheduler.scheduleDataSourceIngestTask(this);
        } else {
            DataSourceIngestJob.taskScheduler.scheduleFileIngestTasks(this);

            /**
             * No data source ingest task has been scheduled for this stage, and
             * it is possible, if unlikely, that no file ingest tasks were
             * actually scheduled since there are files that get filtered out by
             * the tasks scheduler. In this special case, an ingest thread will
             * never to check for completion of this stage of the job.
             */
            this.checkForStageCompleted();
        }
    }

    /**
     * Starts the second stage of the ingest job.
     */
    private void startSecondStage() {
        this.stage = DataSourceIngestJob.Stages.SECOND;
        this.startDataSourceIngestProgressBar();
        synchronized (this.dataSourceIngestPipelineLock) {
            this.currentDataSourceIngestPipeline = this.secondStageDataSourceIngestPipeline;
        }
        DataSourceIngestJob.taskScheduler.scheduleDataSourceIngestTask(this);
    }

    /**
     * Checks to see if this job has at least one ingest pipeline.
     *
     * @return True or false.
     */
    boolean hasIngestPipeline() {
        return this.hasFirstStageDataSourceIngestPipeline()
                || this.hasFileIngestPipeline()
                || this.hasSecondStageDataSourceIngestPipeline();
    }

    /**
     * Checks to see if this job has a first stage data source level ingest
     * pipeline.
     *
     * @return True or false.
     */
    private boolean hasFirstStageDataSourceIngestPipeline() {
        return (this.firstStageDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if this job has a second stage data source level ingest
     * pipeline.
     *
     * @return True or false.
     */
    private boolean hasSecondStageDataSourceIngestPipeline() {
        return (this.secondStageDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if the job has a file level ingest pipeline.
     *
     * @return True or false.
     */
    private boolean hasFileIngestPipeline() {
        return (this.fileIngestPipelines.peek().isEmpty() == false);
    }

    /**
     * Starts up each of the file and data source level ingest modules to
     * collect possible errors.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestPipelines() {
        List<IngestModuleError> errors = new ArrayList<>();

        // Start up the first stage data source ingest pipeline.
        errors.addAll(this.firstStageDataSourceIngestPipeline.startUp());

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
     * Starts the data source level ingest progress bar.
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
                    String dialogTitle = NbBundle.getMessage(DataSourceIngestJob.this.getClass(), "IngestJob.cancellationDialog.title");
                    JOptionPane.showConfirmDialog(null, panel, dialogTitle, JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
                    if (panel.cancelAllDataSourceIngestModules()) {
                        DataSourceIngestJob.this.cancel();
                    } else {
                        DataSourceIngestJob.this.cancelCurrentDataSourceIngestModule();
                    }
                    return true;
                }
            });
            this.dataSourceIngestProgress.start();
            this.dataSourceIngestProgress.switchToIndeterminate();
        }
    }

    /**
     * Starts the file level ingest progress bar.
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
                    DataSourceIngestJob.this.cancel();
                    return true;
                }
            });
            this.estimatedFilesToProcess = this.dataSource.accept(new GetFilesCountVisitor());
            this.fileIngestProgress.start();
            this.fileIngestProgress.switchToDeterminate((int) this.estimatedFilesToProcess);
        }
    }

    /**
     * Checks to see if the ingest tasks for the current stage are completed and
     * does a stage transition if they are.
     */
    private void checkForStageCompleted() {
        synchronized (this.stageCompletionCheckLock) {
            if (DataSourceIngestJob.taskScheduler.tasksForJobAreCompleted(this)) {
                switch (this.stage) {
                    case FIRST:
                        this.finishFirstStage();
                        break;
                    case SECOND:
                        this.finish();
                        break;
                }
            }
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
        this.stage = DataSourceIngestJob.Stages.FINALIZATION;

        // Finish the second stage data source ingest progress bar, if it hasn't 
        // already been finished.
        synchronized (this.dataSourceIngestProgressLock) {
            if (this.dataSourceIngestProgress != null) {
                this.dataSourceIngestProgress.finish();
                this.dataSourceIngestProgress = null;
            }
        }

        this.parentJob.dataSourceJobFinished(this);
    }

    /**
     * Write ingest module errors to the log.
     *
     * @param errors The errors.
     */
    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            DataSourceIngestJob.logger.log(Level.SEVERE, error.getModuleDisplayName() + " experienced an error", error.getModuleError()); //NON-NLS
        }
    }

    /**
     * Gets the currently running data source level ingest module.
     *
     * @return The currently running module, may be null.
     */
    DataSourceIngestPipeline.DataSourceIngestModuleDecorator getCurrentDataSourceIngestModule() {
        if (null != this.currentDataSourceIngestPipeline) {
            return this.currentDataSourceIngestPipeline.getCurrentlyRunningModule();
        } else {
            return null;
        }
    }

    /**
     * Requests a temporary cancellation of data source level ingest in order to
     * stop the currently executing data source ingest module.
     */
    private void cancelCurrentDataSourceIngestModule() {
        this.currentDataSourceIngestModuleCancelled = true;
    }

    /**
     * Gets a snapshot of this jobs state and performance.
     *
     * @return An ingest job statistics object.
     */
    IngestJobSnapshot getSnapshot() {
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
         * Constructs an object to store basic diagnostic statistics for an
         * ingest job.
         */
        IngestJobSnapshot() {
            this.jobId = DataSourceIngestJob.this.id;
            this.dataSource = DataSourceIngestJob.this.dataSource.getName();
            this.startTime = DataSourceIngestJob.this.createTime;
            synchronized (DataSourceIngestJob.this.fileIngestProgressLock) {
                this.processedFiles = DataSourceIngestJob.this.processedFiles;
                this.estimatedFilesToProcess = DataSourceIngestJob.this.estimatedFilesToProcess;
                this.snapShotTime = new Date().getTime();
            }

            /**
             * Get a snapshot of the tasks currently in progress for this job.
             */
            this.tasksSnapshot = DataSourceIngestJob.taskScheduler.getTasksSnapshotForJob(this.jobId);
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
         * Gets the name of the data source associated with the ingest job that
         * is the subject of this snapshot.
         *
         * @return A data source name string.
         */
        String getDataSource() {
            return dataSource;
        }

        /**
         * Gets files per second throughput since the ingest job that is the
         * subject of this snapshot started.
         *
         * @return Files processed per second (approximate).
         */
        double getSpeed() {
            return (double) processedFiles / ((snapShotTime - startTime) / 1000);
        }

        /**
         * Gets the time the ingest job was started.
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
