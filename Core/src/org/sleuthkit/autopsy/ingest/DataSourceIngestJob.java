/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestJobInfo.IngestJobStatusType;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.IngestModuleInfo.IngestModuleType;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet;

/**
 * Encapsulates a data source and the ingest module pipelines used to process
 * it.
 */
final class DataSourceIngestJob {

    private static final Logger logger = Logger.getLogger(DataSourceIngestJob.class.getName());

    /**
     * These fields define a data source ingest job: the parent ingest job, an
     * ID, the user's ingest job settings, and the data source to be processed.
     */
    private final IngestJob parentJob;
    private static final AtomicLong nextJobId = new AtomicLong(0L);
    private final long id;
    private final IngestJobSettings settings;
    private final Content dataSource;

    /**
     * A data source ingest job runs in stages.
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
    private volatile Stages stage = DataSourceIngestJob.Stages.INITIALIZATION;
    private final Object stageCompletionCheckLock = new Object();

    /**
     * A data source ingest job has separate data source level ingest module
     * pipelines for the first and second processing stages. Longer running,
     * lower priority modules belong in the second stage pipeline, although this
     * cannot be enforced. Note that the pipelines for both stages are created
     * at job start up to allow for verification that they both can be started
     * up without errors.
     */
    private final Object dataSourceIngestPipelineLock = new Object();
    private DataSourceIngestPipeline firstStageDataSourceIngestPipeline;
    private DataSourceIngestPipeline secondStageDataSourceIngestPipeline;
    private DataSourceIngestPipeline currentDataSourceIngestPipeline;

    /**
     * A data source ingest job has a collection of identical file level ingest
     * module pipelines, one for each file level ingest thread in the ingest
     * manager. A blocking queue is used to dole out the pipelines to the
     * threads and an ordinary list is used when the ingest job needs to access
     * the pipelines to query their status.
     */
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelinesQueue = new LinkedBlockingQueue<>();
    private final List<FileIngestPipeline> fileIngestPipelines = new ArrayList<>();

    /**
     * A data source ingest job supports cancellation of either the currently
     * running data source level ingest module or the entire ingest job.
     *
     * TODO: The currentDataSourceIngestModuleCancelled field and all of the
     * code concerned with it is a hack to avoid an API change. The next time an
     * API change is legal, a cancel() method needs to be added to the
     * IngestModule interface and this field should be removed. The "ingest job
     * is canceled" queries should also be removed from the IngestJobContext
     * class.
     */
    private volatile boolean currentDataSourceIngestModuleCancelled;
    private final List<String> cancelledDataSourceIngestModules = new CopyOnWriteArrayList<>();
    private volatile boolean cancelled;
    private volatile IngestJob.CancellationReason cancellationReason = IngestJob.CancellationReason.NOT_CANCELLED;

    /**
     * A data source ingest job uses the task scheduler singleton to create and
     * queue the ingest tasks that make up the job.
     */
    private static final IngestTasksScheduler taskScheduler = IngestTasksScheduler.getInstance();

    /**
     * A data source ingest job can run interactively using NetBeans progress
     * handles.
     */
    private final boolean doUI;

    /**
     * A data source ingest job uses these fields to report data source level
     * ingest progress.
     */
    private final Object dataSourceIngestProgressLock = new Object();
    private ProgressHandle dataSourceIngestProgress;

    /**
     * A data source ingest job uses these fields to report file level ingest
     * progress.
     */
    private final Object fileIngestProgressLock = new Object();
    private final List<String> filesInProgress = new ArrayList<>();
    private long estimatedFilesToProcess;
    private long processedFiles;
    private ProgressHandle fileIngestProgress;
    private String currentFileIngestModule = "";
    private String currentFileIngestTask = "";
    private final List<IngestModuleInfo> ingestModules = new ArrayList<>();
    private IngestJobInfo ingestJob;

    /**
     * A data source ingest job uses this field to report its creation time.
     */
    private final long createTime;

    /**
     * Constructs an object that encapsulates a data source and the ingest
     * module pipelines used to process it.
     *
     * @param parentJob        The ingest job of which this data source ingest
     *                         job is a part.
     * @param dataSource       The data source to be ingested.
     * @param settings         The settings for the ingest job.
     * @param runInteractively Whether or not this job should use NetBeans
     *                         progress handles.
     */
    DataSourceIngestJob(IngestJob parentJob, Content dataSource, IngestJobSettings settings, boolean runInteractively) {
        this.parentJob = parentJob;
        this.id = DataSourceIngestJob.nextJobId.getAndIncrement();
        this.dataSource = dataSource;
        this.settings = settings;
        this.doUI = runInteractively;
        this.createTime = new Date().getTime();
        this.createIngestPipelines();
    }

    /**
     * Creates the file and data source ingest pipelines.
     */
    private void createIngestPipelines() {
        List<IngestModuleTemplate> ingestModuleTemplates = this.settings.getEnabledIngestModuleTemplates();

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
                FileIngestPipeline pipeline = new FileIngestPipeline(this, fileIngestModuleTemplates);
                this.fileIngestPipelinesQueue.put(pipeline);
                this.fileIngestPipelines.add(pipeline);
            }
        } catch (InterruptedException ex) {
            /**
             * The current thread was interrupted while blocked on a full queue.
             * Blocking should actually never happen here, but reset the
             * interrupted flag rather than just swallowing the exception.
             */
            Thread.currentThread().interrupt();
        }
        try {
            SleuthkitCase skCase = Case.getOpenCase().getSleuthkitCase();
            this.addIngestModules(firstStageDataSourceModuleTemplates, IngestModuleType.DATA_SOURCE_LEVEL, skCase);
            this.addIngestModules(fileIngestModuleTemplates, IngestModuleType.FILE_LEVEL, skCase);
            this.addIngestModules(secondStageDataSourceModuleTemplates, IngestModuleType.DATA_SOURCE_LEVEL, skCase);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to add ingest modules to database.", ex);
        }
    }

    private void addIngestModules(List<IngestModuleTemplate> templates, IngestModuleType type, SleuthkitCase skCase) throws TskCoreException {
        for (IngestModuleTemplate module : templates) {
            ingestModules.add(skCase.addIngestModule(module.getModuleName(), FactoryClassNameNormalizer.normalize(module.getModuleFactory().getClass().getCanonicalName()), type, module.getModuleFactory().getModuleVersionNumber()));
        }
    }

    /**
     * Uses an input collection of ingest module templates and a pipeline
     * configuration, i.e., an ordered list of ingest module factory class
     * names, to create an ordered output list of ingest module templates for an
     * ingest pipeline. The ingest module templates are removed from the input
     * collection as they are added to the output collection.
     *
     * @param ingestModuleTemplates A mapping of ingest module factory class
     *                              names to ingest module templates.
     * @param pipelineConfig        An ordered list of ingest module factory
     *                              class names representing an ingest pipeline.
     *
     * @return An ordered list of ingest module templates, i.e., an
     *         uninstantiated pipeline.
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
     * Gets the identifier of this job.
     *
     * @return The job identifier.
     */
    long getId() {
        return this.id;
    }

    /**
     * Get the ingest execution context identifier.
     *
     * @return The context string.
     */
    String getExecutionContext() {
        return this.settings.getExecutionContext();
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
     * Queries whether or not unallocated space should be processed as part of
     * this job.
     *
     * @return True or false.
     */
    boolean shouldProcessUnallocatedSpace() {
        return this.settings.getProcessUnallocatedSpace();
    }

    /**
     * Gets the selected file ingest filter from settings.
     *
     * @return True or false.
     */
    FilesSet getFileIngestFilter() {
        return this.settings.getFileFilter();
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
     * Checks to see if this job has a file level ingest pipeline.
     *
     * @return True or false.
     */
    private boolean hasFileIngestPipeline() {
        if (!this.fileIngestPipelines.isEmpty()) {
            return !this.fileIngestPipelines.get(0).isEmpty();
        }
        return false;
    }

    /**
     * Starts up the ingest pipelines for this job.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    List<IngestModuleError> start() {
        List<IngestModuleError> errors = startUpIngestPipelines();
        if (errors.isEmpty()) {
            if (this.hasFirstStageDataSourceIngestPipeline() || this.hasFileIngestPipeline()) {
                logger.log(Level.INFO, "Starting first stage analysis for {0} (jobId={1})", new Object[]{dataSource.getName(), this.id}); //NON-NLS
                this.startFirstStage();
            } else if (this.hasSecondStageDataSourceIngestPipeline()) {
                logger.log(Level.INFO, "Starting second stage analysis for {0} (jobId={1}), no first stage configured", new Object[]{dataSource.getName(), this.id}); //NON-NLS
                this.startSecondStage();
            }
            try {
                this.ingestJob = Case.getOpenCase().getSleuthkitCase().addIngestJob(dataSource, NetworkUtils.getLocalHostName(), ingestModules, new Date(this.createTime), new Date(0), IngestJobStatusType.STARTED, "");
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Failed to add ingest job to database.", ex);
            }
        }
        return errors;
    }

    /**
     * Starts up each of the ingest pipelines for this job to collect any file
     * and data source level ingest modules errors that might occur.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestPipelines() {
        List<IngestModuleError> errors = new ArrayList<>();

        /*
         * Start the data-source-level ingest module pipelines.
         */
        errors.addAll(this.firstStageDataSourceIngestPipeline.startUp());
        errors.addAll(this.secondStageDataSourceIngestPipeline.startUp());

        /*
         * If the data-source-level ingest pipelines were successfully started,
         * start the Start the file-level ingest pipelines (one per file ingest
         * thread).
         */
        if (errors.isEmpty()) {
            for (FileIngestPipeline pipeline : this.fileIngestPipelinesQueue) {
                errors.addAll(pipeline.startUp());
                if (!errors.isEmpty()) {
                    /*
                     * If there are start up errors, the ingest job will not
                     * proceed, so shut down any file ingest pipelines that did
                     * start up.
                     */
                    while (!this.fileIngestPipelinesQueue.isEmpty()) {
                        FileIngestPipeline startedPipeline = this.fileIngestPipelinesQueue.poll();
                        if (startedPipeline.isRunning()) {
                            List<IngestModuleError> shutDownErrors = startedPipeline.shutDown();
                            if (!shutDownErrors.isEmpty()) {
                                /*
                                 * The start up errors will ultimately be
                                 * reported to the user for possible remedy, but
                                 * the shut down errors are logged here.
                                 */
                                logIngestModuleErrors(shutDownErrors);
                            }
                        }
                    }
                    break;
                }
            }
        }

        return errors;
    }

    /**
     * Starts the first stage of this job.
     */
    private void startFirstStage() {
        this.stage = DataSourceIngestJob.Stages.FIRST;

        if (this.hasFileIngestPipeline()) {
            synchronized (this.fileIngestProgressLock) {
                this.estimatedFilesToProcess = this.dataSource.accept(new GetFilesCountVisitor());
            }
        }

        if (this.doUI) {
            /**
             * Start one or both of the first stage ingest progress bars.
             */
            if (this.hasFirstStageDataSourceIngestPipeline()) {
                this.startDataSourceIngestProgressBar();
            }
            if (this.hasFileIngestPipeline()) {
                this.startFileIngestProgressBar();
            }
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
            logger.log(Level.INFO, "Scheduling first stage data source and file level analysis tasks for {0} (jobId={1})", new Object[]{dataSource.getName(), this.id}); //NON-NLS
            DataSourceIngestJob.taskScheduler.scheduleIngestTasks(this);
        } else if (this.hasFirstStageDataSourceIngestPipeline()) {
            logger.log(Level.INFO, "Scheduling first stage data source level analysis tasks for {0} (jobId={1}), no file level analysis configured", new Object[]{dataSource.getName(), this.id}); //NON-NLS
            DataSourceIngestJob.taskScheduler.scheduleDataSourceIngestTask(this);
        } else {
            logger.log(Level.INFO, "Scheduling file level analysis tasks for {0} (jobId={1}), no first stage data source level analysis configured", new Object[]{dataSource.getName(), this.id}); //NON-NLS
            DataSourceIngestJob.taskScheduler.scheduleFileIngestTasks(this);

            /**
             * No data source ingest task has been scheduled for this stage, and
             * it is possible, if unlikely, that no file ingest tasks were
             * actually scheduled since there are files that get filtered out by
             * the tasks scheduler. In this special case, an ingest thread will
             * never get to check for completion of this stage of the job, so do
             * it now.
             */
            this.checkForStageCompleted();
        }
    }

    /**
     * Starts the second stage of this ingest job.
     */
    private void startSecondStage() {
        logger.log(Level.INFO, "Starting second stage analysis for {0} (jobId={1})", new Object[]{dataSource.getName(), this.id}); //NON-NLS
        this.stage = DataSourceIngestJob.Stages.SECOND;
        if (this.doUI) {
            this.startDataSourceIngestProgressBar();
        }
        synchronized (this.dataSourceIngestPipelineLock) {
            this.currentDataSourceIngestPipeline = this.secondStageDataSourceIngestPipeline;
        }
        logger.log(Level.INFO, "Scheduling second stage data source level analysis tasks for {0} (jobId={1})", new Object[]{dataSource.getName(), this.id}); //NON-NLS
        DataSourceIngestJob.taskScheduler.scheduleDataSourceIngestTask(this);
    }

    /**
     * Starts a data source level ingest progress bar for this job.
     */
    private void startDataSourceIngestProgressBar() {
        if (this.doUI) {
            synchronized (this.dataSourceIngestProgressLock) {
                String displayName = NbBundle.getMessage(this.getClass(),
                        "IngestJob.progress.dataSourceIngest.initialDisplayName",
                        this.dataSource.getName());
                this.dataSourceIngestProgress = ProgressHandle.createHandle(displayName, new Cancellable() {
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
                        JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(), panel, dialogTitle, JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
                        if (panel.cancelAllDataSourceIngestModules()) {
                            DataSourceIngestJob.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
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
    }

    /**
     * Starts the file level ingest progress bar for this job.
     */
    private void startFileIngestProgressBar() {
        if (this.doUI) {
            synchronized (this.fileIngestProgressLock) {
                String displayName = NbBundle.getMessage(this.getClass(),
                        "IngestJob.progress.fileIngest.displayName",
                        this.dataSource.getName());
                this.fileIngestProgress = ProgressHandle.createHandle(displayName, new Cancellable() {
                    @Override
                    public boolean cancel() {
                        // If this method is called, the user has already pressed 
                        // the cancel button on the progress bar and the OK button
                        // of a cancelation confirmation dialog supplied by 
                        // NetBeans. 
                        DataSourceIngestJob.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        return true;
                    }
                });
                this.fileIngestProgress.start();
                this.fileIngestProgress.switchToDeterminate((int) this.estimatedFilesToProcess);
            }
        }
    }

    /**
     * Checks to see if the ingest tasks for the current stage of this job are
     * completed and does a stage transition if they are.
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
     * Shuts down the first stage ingest pipelines and progress bars for this
     * job and starts the second stage, if appropriate.
     */
    private void finishFirstStage() {
        logger.log(Level.INFO, "Finished first stage analysis for {0} (jobId={1})", new Object[]{dataSource.getName(), this.id}); //NON-NLS

        // Shut down the file ingest pipelines. Note that no shut down is
        // required for the data source ingest pipeline because data source 
        // ingest modules do not have a shutdown() method.
        List<IngestModuleError> errors = new ArrayList<>();
        while (!this.fileIngestPipelinesQueue.isEmpty()) {
            FileIngestPipeline pipeline = fileIngestPipelinesQueue.poll();
            if (pipeline.isRunning()) {
                errors.addAll(pipeline.shutDown());
            }
        }
        if (!errors.isEmpty()) {
            logIngestModuleErrors(errors);
        }

        if (this.doUI) {
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
        logger.log(Level.INFO, "Finished analysis for {0} (jobId={1})", new Object[]{dataSource.getName(), this.id}); //NON-NLS
        this.stage = DataSourceIngestJob.Stages.FINALIZATION;

        if (this.doUI) {
            // Finish the second stage data source ingest progress bar, if it hasn't 
            // already been finished.
            synchronized (this.dataSourceIngestProgressLock) {
                if (this.dataSourceIngestProgress != null) {
                    this.dataSourceIngestProgress.finish();
                    this.dataSourceIngestProgress = null;
                }
            }
        }
        if (this.cancelled) {
            try {
                ingestJob.setIngestJobStatus(IngestJobStatusType.CANCELLED);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to set ingest status for ingest job in database.", ex);
            }
        } else {
            try {
                ingestJob.setIngestJobStatus(IngestJobStatusType.COMPLETED);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to set ingest status for ingest job in database.", ex);
            }
        }
        try {
            this.ingestJob.setEndDateTime(new Date());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to set end date for ingest job in database.", ex);
        }
        this.parentJob.dataSourceJobFinished(this);

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
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(this.currentDataSourceIngestPipeline.process(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }
                }
            }

            if (this.doUI) {
                /**
                 * Shut down the data source ingest progress bar right away.
                 * Data source-level processing is finished for this stage.
                 */
                synchronized (this.dataSourceIngestProgressLock) {
                    if (null != this.dataSourceIngestProgress) {
                        this.dataSourceIngestProgress.finish();
                        this.dataSourceIngestProgress = null;
                    }
                }
            }

        } finally {
            DataSourceIngestJob.taskScheduler.notifyTaskCompleted(task);
            this.checkForStageCompleted();
        }
    }

    /**
     * Passes a file from the data source for this job through the file level
     * ingest pipeline.
     *
     * @param task A file ingest task.
     *
     * @throws InterruptedException if the thread executing this code is
     *                              interrupted while blocked on taking from or
     *                              putting to the file ingest pipelines
     *                              collection.
     */
    void process(FileIngestTask task) throws InterruptedException {
        try {
            if (!this.isCancelled()) {
                FileIngestPipeline pipeline = this.fileIngestPipelinesQueue.take();
                if (!pipeline.isEmpty()) {
                    AbstractFile file = task.getFile();

                    synchronized (this.fileIngestProgressLock) {
                        ++this.processedFiles;
                        if (this.doUI) {
                            /**
                             * Update the file ingest progress bar.
                             */
                            if (this.processedFiles <= this.estimatedFilesToProcess) {
                                this.fileIngestProgress.progress(file.getName(), (int) this.processedFiles);
                            } else {
                                this.fileIngestProgress.progress(file.getName(), (int) this.estimatedFilesToProcess);
                            }
                            this.filesInProgress.add(file.getName());
                        }
                    }

                    /**
                     * Run the file through the pipeline.
                     */
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.process(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }

                    if (this.doUI && !this.cancelled) {
                        synchronized (this.fileIngestProgressLock) {
                            /**
                             * Update the file ingest progress bar again, in
                             * case the file was being displayed.
                             */
                            this.filesInProgress.remove(file.getName());
                            if (this.filesInProgress.size() > 0) {
                                this.fileIngestProgress.progress(this.filesInProgress.get(0));
                            } else {
                                this.fileIngestProgress.progress("");
                            }
                        }
                    }
                }
                this.fileIngestPipelinesQueue.put(pipeline);
            }
        } finally {
            DataSourceIngestJob.taskScheduler.notifyTaskCompleted(task);
            this.checkForStageCompleted();
        }
    }

    /**
     * Adds more files from the data source for this job to the job, i.e., adds
     * extracted or carved files. Not currently supported for the second stage
     * of the job.
     *
     * @param files A list of the files to add.
     */
    void addFiles(List<AbstractFile> files) {
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
         * which case a completion check would not be necessary, so this is a
         * bit of defensive programming.
         */
        this.checkForStageCompleted();
    }

    /**
     * Updates the display name shown on the current data source level ingest
     * progress bar for this job.
     *
     * @param displayName The new display name.
     */
    void updateDataSourceIngestProgressBarDisplayName(String displayName) {
        if (this.doUI && !this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                this.dataSourceIngestProgress.setDisplayName(displayName);
            }
        }
    }

    /**
     * Switches the data source level ingest progress bar for this job to
     * determinate mode. This should be called if the total work units to
     * process the data source is known.
     *
     * @param workUnits Total number of work units for the processing of the
     *                  data source.
     */
    void switchDataSourceIngestProgressBarToDeterminate(int workUnits) {
        if (this.doUI && !this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.switchToDeterminate(workUnits);
                }
            }
        }
    }

    /**
     * Switches the data source level ingest progress bar for this job to
     * indeterminate mode. This should be called if the total work units to
     * process the data source is unknown.
     */
    void switchDataSourceIngestProgressBarToIndeterminate() {
        if (this.doUI && !this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.switchToIndeterminate();
                }
            }
        }
    }

    /**
     * Updates the data source level ingest progress bar for this job with the
     * number of work units performed, if in the determinate mode.
     *
     * @param workUnits Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(int workUnits) {
        if (this.doUI && !this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.progress("", workUnits);
                }
            }
        }
    }

    /**
     * Updates the data source level ingest progress for this job with a new
     * task name, where the task name is the "subtitle" under the display name.
     *
     * @param currentTask The task name.
     */
    void advanceDataSourceIngestProgressBar(String currentTask) {
        if (this.doUI && !this.cancelled) {
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != this.dataSourceIngestProgress) {
                    this.dataSourceIngestProgress.progress(currentTask);
                }
            }
        }
    }

    /**
     * Updates the data source level ingest progress bar for this with a new
     * task name and the number of work units performed, if in the determinate
     * mode. The task name is the "subtitle" under the display name.
     *
     * @param currentTask The task name.
     * @param workUnits   Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(String currentTask, int workUnits) {
        if (this.doUI && !this.cancelled) {
            synchronized (this.fileIngestProgressLock) {
                this.dataSourceIngestProgress.progress(currentTask, workUnits);
            }
        }
    }

    /**
     * Queries whether or not a temporary cancellation of data source level
     * ingest in order to stop the currently executing data source level ingest
     * module is in effect for this job.
     *
     * @return True or false.
     */
    boolean currentDataSourceIngestModuleIsCancelled() {
        return this.currentDataSourceIngestModuleCancelled;
    }

    /**
     * Rescind a temporary cancellation of data source level ingest that was
     * used to stop a single data source level ingest module for this job.
     *
     * @param moduleDisplayName The display name of the module that was stopped.
     */
    void currentDataSourceIngestModuleCancellationCompleted(String moduleDisplayName) {
        this.currentDataSourceIngestModuleCancelled = false;
        this.cancelledDataSourceIngestModules.add(moduleDisplayName);

        if (this.doUI) {
            /**
             * A new progress bar must be created because the cancel button of
             * the previously constructed component is disabled by NetBeans when
             * the user selects the "OK" button of the cancellation confirmation
             * dialog popped up by NetBeans when the progress bar cancel button
             * is pressed.
             */
            synchronized (this.dataSourceIngestProgressLock) {
                this.dataSourceIngestProgress.finish();
                this.dataSourceIngestProgress = null;
                this.startDataSourceIngestProgressBar();
            }
        }
    }

    /**
     * Gets the currently running data source level ingest module for this job.
     *
     * @return The currently running module, may be null.
     */
    DataSourceIngestPipeline.PipelineModule getCurrentDataSourceIngestModule() {
        if (null != this.currentDataSourceIngestPipeline) {
            return this.currentDataSourceIngestPipeline.getCurrentlyRunningModule();
        } else {
            return null;
        }
    }

    /**
     * Requests a temporary cancellation of data source level ingest for this
     * job in order to stop the currently executing data source ingest module.
     */
    void cancelCurrentDataSourceIngestModule() {
        this.currentDataSourceIngestModuleCancelled = true;
    }

    /**
     * Requests cancellation of ingest, i.e., a shutdown of the data source
     * level and file level ingest pipelines.
     *
     * @param reason The cancellation reason.
     */
    void cancel(IngestJob.CancellationReason reason) {
        this.cancelled = true;
        this.cancellationReason = reason;
        DataSourceIngestJob.taskScheduler.cancelPendingTasksForIngestJob(this);
        
        if (this.doUI) {
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != dataSourceIngestProgress) {
                    dataSourceIngestProgress.setDisplayName(NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataSourceIngest.initialDisplayName", dataSource.getName()));
                    dataSourceIngestProgress.progress(NbBundle.getMessage(this.getClass(), "IngestJob.progress.cancelling"));
                }
            }

            synchronized (this.fileIngestProgressLock) {
                if (null != this.fileIngestProgress) {
                    this.fileIngestProgress.setDisplayName(NbBundle.getMessage(this.getClass(), "IngestJob.progress.fileIngest.displayName", this.dataSource.getName()));
                    this.fileIngestProgress.progress(NbBundle.getMessage(this.getClass(), "IngestJob.progress.cancelling"));
                }
            }
        }
    }

    /**
     * Set the current module name being run and the file name it is running on.
     * To be used for more detailed cancelling.
     *
     * @param moduleName Name of module currently running.
     * @param taskName   Name of file the module is running on.
     */
    void setCurrentFileIngestModule(String moduleName, String taskName) {
        this.currentFileIngestModule = moduleName;
        this.currentFileIngestTask = taskName;
    }

    /**
     * Queries whether or not cancellation, i.e., a shutdown of the data source
     * level and file level ingest pipelines for this job, has been requested.
     *
     * @return True or false.
     */
    boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Gets the reason this job was cancelled.
     *
     * @return The cancellation reason, may be not cancelled.
     */
    IngestJob.CancellationReason getCancellationReason() {
        return this.cancellationReason;
    }

    /**
     * Write ingest module errors to the log.
     *
     * @param errors The errors.
     */
    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            DataSourceIngestJob.logger.log(Level.SEVERE, String.format("%s experienced an error analyzing %s (jobId=%d)", error.getModuleDisplayName(), dataSource.getName(), this.id), error.getThrowable()); //NON-NLS
        }
    }

    /**
     * Gets a snapshot of this jobs state and performance.
     *
     * @return An ingest job statistics object.
     */
    Snapshot getSnapshot(boolean getIngestTasksSnapshot) {
        return new Snapshot(getIngestTasksSnapshot);
    }

    /**
     * Stores basic diagnostic statistics for a data source ingest job.
     */
    final class Snapshot {

        private final String dataSource;
        private final long jobId;
        private final long jobStartTime;
        private final long snapShotTime;
        private final DataSourceIngestPipeline.PipelineModule dataSourceLevelIngestModule;
        private boolean fileIngestRunning;
        private Date fileIngestStartTime;
        private final long processedFiles;
        private final long estimatedFilesToProcess;
        private final IngestTasksScheduler.IngestJobTasksSnapshot tasksSnapshot;
        private final boolean jobCancelled;
        private final IngestJob.CancellationReason jobCancellationReason;
        private final List<String> cancelledDataSourceModules;

        /**
         * Constructs an object to store basic diagnostic statistics for a data
         * source ingest job.
         */
        Snapshot(boolean getIngestTasksSnapshot) {
            this.dataSource = DataSourceIngestJob.this.dataSource.getName();
            this.jobId = DataSourceIngestJob.this.id;
            this.jobStartTime = DataSourceIngestJob.this.createTime;
            this.dataSourceLevelIngestModule = DataSourceIngestJob.this.getCurrentDataSourceIngestModule();

            /**
             * Determine whether file ingest is running at the time of this
             * snapshot and determine the earliest file ingest level pipeline
             * start time, if file ingest was started at all.
             */
            for (FileIngestPipeline pipeline : DataSourceIngestJob.this.fileIngestPipelines) {
                if (pipeline.isRunning()) {
                    this.fileIngestRunning = true;
                }
                Date pipelineStartTime = pipeline.getStartTime();
                if (null != pipelineStartTime && (null == this.fileIngestStartTime || pipelineStartTime.before(this.fileIngestStartTime))) {
                    this.fileIngestStartTime = pipelineStartTime;
                }
            }

            this.jobCancelled = cancelled;
            this.jobCancellationReason = cancellationReason;
            this.cancelledDataSourceModules = new ArrayList<>(DataSourceIngestJob.this.cancelledDataSourceIngestModules);

            if (getIngestTasksSnapshot) {
                synchronized (DataSourceIngestJob.this.fileIngestProgressLock) {
                    this.processedFiles = DataSourceIngestJob.this.processedFiles;
                    this.estimatedFilesToProcess = DataSourceIngestJob.this.estimatedFilesToProcess;
                    this.snapShotTime = new Date().getTime();
                }
                this.tasksSnapshot = DataSourceIngestJob.taskScheduler.getTasksSnapshotForJob(this.jobId);

            } else {
                this.processedFiles = 0;
                this.estimatedFilesToProcess = 0;
                this.snapShotTime = new Date().getTime();
                this.tasksSnapshot = null;
            }
        }

        /**
         * Gets time these statistics were collected.
         *
         * @return The statistics collection time as number of milliseconds
         *         since January 1, 1970, 00:00:00 GMT.
         */
        long getSnapshotTime() {
            return snapShotTime;
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
         * @return The start time as number of milliseconds since January 1,
         *         1970, 00:00:00 GMT.
         */
        long getJobStartTime() {
            return jobStartTime;
        }

        DataSourceIngestPipeline.PipelineModule getDataSourceLevelIngestModule() {
            return this.dataSourceLevelIngestModule;
        }

        boolean fileIngestIsRunning() {
            return this.fileIngestRunning;
        }

        Date fileIngestStartTime() {
            return this.fileIngestStartTime;
        }

        /**
         * Gets files per second throughput since the ingest job that is the
         * subject of this snapshot started.
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
         * Gets an estimate of the files that still need to be processed for
         * this job.
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
         * Gets a list of the display names of any canceled data source level
         * ingest modules
         *
         * @return A list of canceled data source level ingest module display
         *         names, possibly empty.
         */
        List<String> getCancelledDataSourceIngestModules() {
            return Collections.unmodifiableList(this.cancelledDataSourceModules);
        }

    }

}
