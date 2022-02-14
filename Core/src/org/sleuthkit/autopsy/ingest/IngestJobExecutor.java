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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestJobInfo.IngestJobStatusType;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.IngestModuleInfo.IngestModuleType;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet;
import org.sleuthkit.autopsy.python.FactoryClassNameNormalizer;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DataSource;

/**
 * Executes an ingest job by orchestrating the construction, start up, running,
 * and shut down of the ingest module pipelines that perform the ingest tasks
 * for a given ingest job.
 */
final class IngestJobExecutor {

    private static enum IngestJobState {
        PIPELINES_STARTING_UP,
        ACCEPTING_STREAMED_CONTENT_AND_ANALYZING,
        ANALYZING,
        PIPELINES_SHUTTING_DOWN
    };
    private static final Logger logger = Logger.getLogger(IngestJobExecutor.class.getName());
    private final IngestJob ingestJob;
    private final long createTime;
    private final boolean usingNetBeansGUI;
    private final IngestTasksScheduler taskScheduler = IngestTasksScheduler.getInstance();
    private final Object threadRegistrationLock = new Object();
    @GuardedBy("threadRegistrationLock")
    private final Set<Thread> pausedIngestThreads = new HashSet<>();
    private final List<String> cancelledDataSourceIngestModules = new CopyOnWriteArrayList<>();
    private final Object tierTransitionLock = new Object();
    private final List<IngestModuleTier> ingestModuleTiers = new ArrayList<>();
    private volatile int moduleTierIndex = 0;
    private volatile IngestJobState jobState = IngestJobExecutor.IngestJobState.PIPELINES_STARTING_UP;
    private volatile long estimatedFilesToProcess = 0;
    private volatile long processedFiles = 0;
    private volatile boolean currentDataSourceIngestModuleCancelled = false;
    private volatile boolean jobCancelled = false;
    private volatile IngestJob.CancellationReason cancellationReason = IngestJob.CancellationReason.NOT_CANCELLED;
    private volatile IngestJobInfo casDbingestJobInfo;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private ProgressHandle dataSourceIngestProgressBar;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final List<String> filesInProgress = new ArrayList<>();
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private ProgressHandle fileIngestProgressBar;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private ProgressHandle artifactIngestProgressBar;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private ProgressHandle resultIngestProgressBar;

    /**
     * Constructs an ingest job executor that executes an ingest job by
     * orchestrating the construction, start up, running, and shut down of the
     * ingest module pipelines that perform the ingest tasks for a given ingest
     * job.
     *
     * @param ingestJob The ingest job.
     *
     * @throws InterruptedException The exception is thrown if the thread in
     *                              which the ingest job executor is being
     *                              created is interrupted.
     */
    IngestJobExecutor(IngestJob ingestJob) throws InterruptedException {
        this.ingestJob = ingestJob;
        createTime = new Date().getTime();
        /*
         * If running in the NetBeans thick client application version of
         * Autopsy, NetBeans progress handles (i.e., progress bars) are used to
         * display ingest job progress in the lower right hand corner of the
         * main application window. A layer of abstraction to allow alternate
         * representations of progress could be used here, as it is in some
         * other places in the application (see implementations and usage of the
         * org.sleuthkit.autopsy.progress.ProgressIndicator interface).
         */
        usingNetBeansGUI = RuntimeProperties.runningWithGUI();
    }

    /**
     * Gets the ID of the ingest job that this ingest job executor is executing.
     *
     * @return The ingest job ID.
     */
    long getIngestJobId() {
        return ingestJob.getId();
    }

    /**
     * Gets the execution context name of the ingest job that this ingest job
     * executor is executing.
     *
     * @return The context name.
     */
    String getExecutionContext() {
        return ingestJob.getSettings().getExecutionContext();
    }

    /**
     * Gets the data source for the ingest job that this ingest job executor is
     * executing.
     *
     * @return The data source.
     */
    DataSource getDataSource() {
        return ingestJob.getDataSource();
    }

    /**
     * Queries whether or not unallocated space should be processed for the
     * ingest job that this ingest job executor is executing.
     *
     * @return True or false.
     */
    boolean shouldProcessUnallocatedSpace() {
        return ingestJob.getSettings().getProcessUnallocatedSpace();
    }

    /**
     * Gets the file ingest filter for the ingest job that this ingest job
     * executor is executing.
     *
     * @return The filter.
     */
    FilesSet getFileIngestFilter() {
        return ingestJob.getSettings().getFileFilter();
    }

    /**
     * Contructs and starts up the ingest modules in ALL of the ingest module
     * tiers configured for the ingest job, and if start up is successful,
     * schedules the initial ingest tasks for the ingest job, if any. The reason
     * for starting up everything now is to allow the ingest job to be
     * automatically cancelled, and for the errors to be presented to the user
     * to allow him or her to address the issues, or disable the modules that
     * can't start up, and attempt the job again.
     *
     * @return A list of ingest module startup errors, empty on success.
     *
     * @throws InterruptedException The exception is thrown if the current
     *                              thread is interrupted during the start up
     *                              process.
     */
    List<IngestModuleError> startUp() throws InterruptedException {
        jobState = IngestJobState.PIPELINES_STARTING_UP;
        ingestModuleTiers.addAll(IngestModuleTierBuilder.buildIngestModuleTiers(ingestJob.getSettings(), this));
        List<IngestModuleError> errors = startUpIngestModulePipelines();
        if (errors.isEmpty()) {
            recordIngestJobStartUpInfo();
            /*
             * Start up and execution of the first ingest module tier requires
             * some special treatment due to the differences between streaming
             * and batch mode ingest jobs. Subsequent tiers can be handled
             * generically.
             */
            if (ingestJob.getIngestMode() == IngestJob.Mode.STREAMING) {
                startStreamingModeAnalysis();
            } else {
                startBatchModeAnalysis();
            }
        }
        return errors;
    }

    /**
     * Starts up the ingest module pipelines in all of the ingest module tiers.
     *
     * @return A list of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestModulePipelines() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (IngestModuleTier moduleTier : ingestModuleTiers) {
            Optional<DataSourceIngestPipeline> dataSourcePipeline = moduleTier.getDataSourceIngestPipeline();
            if (dataSourcePipeline.isPresent()) {
                errors.addAll(startUpIngestModulePipeline(dataSourcePipeline.get()));
            }

            for (FileIngestPipeline pipeline : moduleTier.getFileIngestPipelines()) {
                List<IngestModuleError> filePipelineErrors = startUpIngestModulePipeline(pipeline);
                if (!filePipelineErrors.isEmpty()) {
                    /*
                     * If one file pipeline copy can't start up, assume that
                     * none of the other copies will be able to start up, for
                     * the same reason.
                     */
                    errors.addAll(filePipelineErrors);
                    break;
                }
            }

            Optional<DataArtifactIngestPipeline> dataArtifactPipeline = moduleTier.getDataArtifactIngestPipeline();
            if (dataArtifactPipeline.isPresent()) {
                errors.addAll(startUpIngestModulePipeline(dataArtifactPipeline.get()));
            }

            Optional<AnalysisResultIngestPipeline> analysisResultPipeline = moduleTier.getAnalysisResultIngestPipeline();
            if (analysisResultPipeline.isPresent()) {
                errors.addAll(startUpIngestModulePipeline(analysisResultPipeline.get()));
            }
        }
        return errors;
    }

    /**
     * Starts up an ingest module pipeline. If there are any start up errors,
     * the pipeline is immediately shut down.
     *
     * @param pipeline The ingest module pipeline to start up.
     *
     * @return A list of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestModulePipeline(IngestPipeline<?> pipeline) {
        List<IngestModuleError> startUpErrors = pipeline.startUp();
        if (!startUpErrors.isEmpty()) {
            List<IngestModuleError> shutDownErrors = pipeline.shutDown();
            if (!shutDownErrors.isEmpty()) {
                logIngestModuleErrors(shutDownErrors);
            }
        }
        return startUpErrors;
    }

    /**
     * Writes start up data about the ingest job into the case database. The
     * case database returns an object that is retained to allow the addition of
     * a completion time when the ingest job is finished.
     */
    private void recordIngestJobStartUpInfo() {
        try {
            SleuthkitCase caseDb = Case.getCurrentCase().getSleuthkitCase();
            List<IngestModuleInfo> ingestModuleInfoList = new ArrayList<>();
            for (IngestModuleTemplate module : ingestJob.getSettings().getEnabledIngestModuleTemplates()) {
                IngestModuleType moduleType = getIngestModuleTemplateType(module);
                IngestModuleInfo moduleInfo = caseDb.addIngestModule(module.getModuleName(), FactoryClassNameNormalizer.normalize(module.getModuleFactory().getClass().getCanonicalName()), moduleType, module.getModuleFactory().getModuleVersionNumber());
                ingestModuleInfoList.add(moduleInfo);
            }
            casDbingestJobInfo = caseDb.addIngestJob(ingestJob.getDataSource(), NetworkUtils.getLocalHostName(), ingestModuleInfoList, new Date(this.createTime), new Date(0), IngestJobStatusType.STARTED, "");
        } catch (TskCoreException ex) {
            logErrorMessage(Level.SEVERE, "Failed to add ingest job info to case database", ex); //NON-NLS
        }
    }

    /**
     * Determines the type of an ingest modules that can be constructed using a
     * given ingest module template.
     *
     * @param moduleTemplate The ingest module template.
     *
     * @return The ingest module type, may be IngestModuleType.MULTIPLE.
     */
    private static IngestModuleType getIngestModuleTemplateType(IngestModuleTemplate moduleTemplate) {
        IngestModuleType type = null;
        if (moduleTemplate.isDataSourceIngestModuleTemplate()) {
            type = IngestModuleType.DATA_SOURCE_LEVEL;
        }
        if (moduleTemplate.isFileIngestModuleTemplate()) {
            if (type == null) {
                type = IngestModuleType.FILE_LEVEL;
            } else {
                type = IngestModuleType.MULTIPLE;
            }
        }
        if (moduleTemplate.isDataArtifactIngestModuleTemplate()) {
            if (type == null) {
                type = IngestModuleType.DATA_ARTIFACT;
            } else {
                type = IngestModuleType.MULTIPLE;
            }
        }
        return type;
    }

    /**
     * Starts analysis for a batch mode ingest job. For a batch mode job, all of
     * the files in the data source (excepting carved and derived files) have
     * already been added to the case database by the data source processor
     * (DSP).
     */
    private void startBatchModeAnalysis() {
        synchronized (tierTransitionLock) {
            logInfoMessage("Starting ingest job in file batch mode"); //NON-NLS            
            jobState = IngestJobState.ANALYZING;
            IngestModuleTier currentTier = ingestModuleTiers.get(moduleTierIndex);

            if (currentTier.hasDataSourceIngestModules()) {
                startDataSourceIngestProgressBar();
                taskScheduler.scheduleDataSourceIngestTask(this);
            }

            if (currentTier.hasFileIngestModules()) {
                estimateFilesToProcess();
                startFileIngestProgressBar(true);
                taskScheduler.scheduleFileIngestTasks(this, ingestJob.getFiles());
            }

            if (currentTier.hasDataArtifactIngestModules()) {
                /*
                 * Analysis of any data artifacts already in the case database
                 * (possibly added by the DSP) will be performed.
                 */
                startDataArtifactIngestProgressBar();
                taskScheduler.scheduleDataArtifactIngestTasks(this);
            }

            if (currentTier.hasAnalysisResultIngestModules()) {
                /*
                 * Analysis of any analysis results already in the case database
                 * (possibly added by the DSP) will be performed.
                 */
                startAnalysisResultIngestProgressBar();
                taskScheduler.scheduleAnalysisResultIngestTasks(this);
            }

            /*
             * Check for analysis completion. This is necessary because it is
             * possible that none of the tasks that were just scheduled will
             * actually make it to task execution, due to the file filter or
             * other ingest job settings. If that happens, there will never be
             * another analysis completion check for this job in an ingest
             * thread executing an ingest task, so such a job would run forever,
             * doing nothing, without a check here.
             */
            checkForTierCompleted(moduleTierIndex);
        }
    }

    /**
     * Estimates the files to be processed in the current tier.
     */
    private void estimateFilesToProcess() {
        estimatedFilesToProcess = 0;
        processedFiles = 0;
        if (ingestModuleTiers.get(moduleTierIndex).hasFileIngestModules()) {
            /*
             * Do an estimate of the total number of files to be analyzed. This
             * will be used to estimate of how many files remain to be analyzed
             * as each file ingest task is completed. The numbers are estimates
             * because analysis can add carved and/or derived files to the job.
             */
            List<AbstractFile> files = ingestJob.getFiles();
            if (files.isEmpty()) {
                /*
                 * Do a count of the files from the data source that the data
                 * source processor (DSP) has added to the case database.
                 */
                estimatedFilesToProcess = ingestJob.getDataSource().accept(new GetFilesCountVisitor());
            } else {
                /*
                 * Otherwise, this job is analyzing a user-specified subset of
                 * the files in the data source.
                 */
                estimatedFilesToProcess = files.size();
            }
        }
    }

    /**
     * Starts analysis for a streaming mode ingest job. Streaming mode is
     * typically used to allow a data source processor (DSP) to stream files to
     * this ingest job executor as it adds the files to the case database. This
     * alternative to waiting until the DSP completes its processing allows file
     * level analysis to begin before data source level analysis.
     */
    private void startStreamingModeAnalysis() {
        synchronized (tierTransitionLock) {
            logInfoMessage("Starting ingest job in file streaming mode"); //NON-NLS
            jobState = IngestJobState.ACCEPTING_STREAMED_CONTENT_AND_ANALYZING;
            IngestModuleTier currentTier = ingestModuleTiers.get(moduleTierIndex);

            if (currentTier.hasFileIngestModules()) {
                /*
                 * Start the file ingest progress bar, but do not schedule any
                 * file or data source ingest tasks. File ingest tasks will
                 * instead be scheduled as files are streamed in via
                 * addStreamedFiles(), and a data source ingest task will be
                 * scheduled later, via addStreamedDataSource().
                 */
                startFileIngestProgressBar(false);
            }

            if (currentTier.hasDataArtifactIngestModules()) {
                /*
                 * Start the data artifact progress bar and schedule ingest
                 * tasks for any data artifacts currently in the case database.
                 * This needs to be done BEFORE any files or the data source are
                 * streamed in to ensure that any data artifacts added to the
                 * case database by the file and data source ingest tasks are
                 * not analyzed twice. This works here because the ingest
                 * manager has not yet returned the ingest stream object that is
                 * used to call addStreamedFiles() and addStreamedDataSource().
                 */
                startDataArtifactIngestProgressBar();
                taskScheduler.scheduleDataArtifactIngestTasks(this);
            }

            if (currentTier.hasAnalysisResultIngestModules()) {
                /*
                 * Start the analysis result progress bar and schedule ingest
                 * tasks for any analysis results currently in the case
                 * database. This needs to be done BEFORE any files or the data
                 * source are streamed in to ensure that any analysis results
                 * added to the case database by the file and data source ingest
                 * tasks are not analyzed twice. This works here because the
                 * ingest manager has not yet returned the ingest stream object
                 * that is used to call addStreamedFiles() and
                 * addStreamedDataSource().
                 */
                startAnalysisResultIngestProgressBar();
                taskScheduler.scheduleAnalysisResultIngestTasks(this);
            }
        }
    }

    /**
     * Signals in streaming mode that all of the files have been added to the
     * case database and streamed in to this ingest job executor, and the data
     * source is now ready for analysis.
     */
    void addStreamedDataSource() {
        synchronized (tierTransitionLock) {
            logInfoMessage("Data source received in streaming mode ingest job"); //NON-NLS
            jobState = IngestJobExecutor.IngestJobState.ANALYZING;
            IngestModuleTier currentTier = ingestModuleTiers.get(moduleTierIndex);

            if (currentTier.hasFileIngestModules()) {
                estimateFilesToProcess();
                switchFileIngestProgressBarToDeterminate();
                // We don't need to schedule file tasks here because they've already been
                // added as the data source was being processed
            }

            if (currentTier.hasDataSourceIngestModules()) {
                taskScheduler.scheduleDataSourceIngestTask(this);
                startDataSourceIngestProgressBar();
            } else {
                /*
                 * If no data source level ingest task is scheduled at this
                 * time, and all of the file level and artifact ingest tasks
                 * scheduled during the initial file streaming stage have
                 * already been executed, there will never be a stage completion
                 * check in an ingest thread executing an ingest task for this
                 * job, so such a job would run forever, doing nothing, without
                 * a check here.
                 */
                checkForTierCompleted(moduleTierIndex);
            }
        }
    }

    /**
     * Checks to see if the ingest tasks to be executed by the current ingest
     * module tier are completed, and does an appropriate state transition if
     * they are.
     */
    private void checkForTierCompleted(int currentTier) {
        synchronized (tierTransitionLock) {
            if (jobState.equals(IngestJobState.ACCEPTING_STREAMED_CONTENT_AND_ANALYZING)) {
                return;
            }
            if (currentTier < moduleTierIndex) {
                // We likely had a leftover task from the previous tier. Since we've already
                // advanced to the next tier, ignore it.
                return;
            }
            if (taskScheduler.currentTasksAreCompleted(getIngestJobId())) {
                do {
                    shutDownCurrentTier();
                    moduleTierIndex++;
                    if (moduleTierIndex < ingestModuleTiers.size()) {
                        startAnalysisForCurrentTier();
                    } else {
                        shutDown();
                        break;
                    }
                } while (taskScheduler.currentTasksAreCompleted(getIngestJobId())); // Loop again immediately in case the new tier is empty
            }
        }
    }

    /**
     * Schedules ingest tasks and starts progress indicators for the current
     * tier of ingest modules.
     */
    private void startAnalysisForCurrentTier() {
        logInfoMessage(String.format("Scheduling ingest tasks for tier %s of ingest job", moduleTierIndex)); //NON-NLS        
        jobState = IngestJobExecutor.IngestJobState.ANALYZING;
        IngestModuleTier currentTier = ingestModuleTiers.get(moduleTierIndex);

        if (currentTier.hasDataSourceIngestModules()) {
            startDataSourceIngestProgressBar();
            taskScheduler.scheduleDataSourceIngestTask(this);
        }

        if (currentTier.hasFileIngestModules()) {
            estimateFilesToProcess();
            startFileIngestProgressBar(true);
            taskScheduler.scheduleFileIngestTasks(this, ingestJob.getFiles());
        }

        if (currentTier.hasDataArtifactIngestModules()) {
            startDataArtifactIngestProgressBar();
        }

        if (currentTier.hasAnalysisResultIngestModules()) {
            startDataArtifactIngestProgressBar();
        }
    }

    /**
     * Passes the data source for the ingest job through the currently active
     * data source level ingest module pipeline (high-priority or low-priority).
     *
     * @param task A data source ingest task encapsulating the data source and
     *             the data source ingest pipeline.
     */
    void execute(DataSourceIngestTask task) {
        try {
            if (!isCancelled()) {
                Optional<DataSourceIngestPipeline> pipeline = ingestModuleTiers.get(moduleTierIndex).getDataSourceIngestPipeline();
                if (pipeline.isPresent()) {
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.get().performTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }
                }
            }
        } finally {
            // Save the module tier assocaited with this task since it could change after
            // notifyTaskComplete
            int currentTier = moduleTierIndex;
            taskScheduler.notifyTaskCompleted(task);
            checkForTierCompleted(currentTier);
        }
    }

    /**
     * Passes a file from the data source for the ingest job through a file
     * ingest module pipeline.
     *
     * @param task A file ingest task encapsulating the file and the file ingest
     *             pipeline.
     */
    void execute(FileIngestTask task) {
        try {
            if (!isCancelled()) {
                FileIngestPipeline pipeline = ingestModuleTiers.get(moduleTierIndex).takeFileIngestPipeline();
                if (!pipeline.isEmpty()) {
                    /*
                     * Get the file from the task. If the file was streamed in,
                     * the task may only have the file object ID, and a trip to
                     * the case database will be required.
                     */
                    AbstractFile file;
                    try {
                        file = task.getFile();
                    } catch (TskCoreException ex) {
                        List<IngestModuleError> errors = new ArrayList<>();
                        errors.add(new IngestModuleError("Ingest Pipeline", ex));
                        logIngestModuleErrors(errors);
                        ingestModuleTiers.get(moduleTierIndex).returnFileIngestPipeleine(pipeline);
                        return;
                    }

                    /**
                     * Run the file through the modules in the file ingest
                     * pipeline.
                     */
                    final String fileName = file.getName();
                    processedFiles++;
                    updateFileProgressBarForFileTaskStarted(fileName);
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.performTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors, file);
                    }
                    updateFileProgressBarForFileTaskCompleted(fileName);
                }
                ingestModuleTiers.get(moduleTierIndex).returnFileIngestPipeleine(pipeline);
            }
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, String.format("File ingest thread interrupted during execution of file ingest job (file object ID = %d, thread ID = %d)", task.getFileId(), task.getThreadId()), ex);
            Thread.currentThread().interrupt();
        } finally {
            // Save the module tier assocaited with this task since it could change after
            // notifyTaskComplete
            int currentTier = moduleTierIndex;
            taskScheduler.notifyTaskCompleted(task);
            checkForTierCompleted(currentTier);
        }
    }

    /**
     * Passes a data artifact from the data source for the ingest job through
     * the data artifact ingest module pipeline.
     *
     * @param task A data artifact ingest task encapsulating the data artifact
     *             and the data artifact ingest pipeline.
     */
    void execute(DataArtifactIngestTask task) {
        try {
            if (!isCancelled()) {
                Optional<DataArtifactIngestPipeline> pipeline = ingestModuleTiers.get(moduleTierIndex).getDataArtifactIngestPipeline();
                if (pipeline.isPresent()) {
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.get().performTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }
                }
            }
        } finally {
            // Save the module tier assocaited with this task since it could change after
            // notifyTaskComplete
            int currentTier = moduleTierIndex;
            taskScheduler.notifyTaskCompleted(task);
            checkForTierCompleted(currentTier);
        }
    }

    /**
     * Passes an analysis result from the data source for the ingest job
     * through the analysis result ingest module pipeline.
     *
     * @param task An analysis result ingest task encapsulating the analysis
     *             result and the analysis result ingest pipeline.
     */
    void execute(AnalysisResultIngestTask task) {
        try {
            if (!isCancelled()) {
                Optional<AnalysisResultIngestPipeline> pipeline = ingestModuleTiers.get(moduleTierIndex).getAnalysisResultIngestPipeline();
                if (pipeline.isPresent()) {
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.get().performTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }
                }
            }
        } finally {
            // Save the module tier assocaited with this task since it could change after
            // notifyTaskComplete
            int currentTier = moduleTierIndex;
            taskScheduler.notifyTaskCompleted(task);
            checkForTierCompleted(currentTier);
        }
    }

    /**
     * Streams in files for analysis as part of a streaming mode ingest job.
     *
     * @param fileObjIds The object IDs of the files.
     */
    void addStreamedFiles(List<Long> fileObjIds) {
        if (!isCancelled() && ingestModuleTiers.get(moduleTierIndex).hasFileIngestModules()) {
            if (jobState.equals(IngestJobState.ACCEPTING_STREAMED_CONTENT_AND_ANALYZING)) {
                taskScheduler.scheduleStreamedFileIngestTasks(this, fileObjIds);
            } else {
                logErrorMessage(Level.SEVERE, "Adding streaming files to job during stage " + jobState.toString() + " not supported");
            }
        }
    }

    /**
     * Adds additional files produced by ingest modules (e.g., extracted or
     * carved files) for analysis. The intended clients of this method are
     * ingest modules running code in an ingest thread that has not yet notified
     * the ingest task scheduler that the the primary ingest task that is the
     * source of the files is completed. This means that the new tasks will be
     * scheduled BEFORE the primary task has been removed from the scheduler's
     * running tasks list.
     *
     * @param files A list of the files to add.
     */
    void addFiles(List<AbstractFile> files) {
        if (!isCancelled() && ingestModuleTiers.get(moduleTierIndex).hasFileIngestModules()) {
            if (jobState.equals(IngestJobState.ACCEPTING_STREAMED_CONTENT_AND_ANALYZING) || jobState.equals(IngestJobState.ANALYZING)) {
                taskScheduler.scheduleHighPriorityFileIngestTasks(this, files);
            } else {
                logErrorMessage(Level.SEVERE, "Adding files to job during stage " + jobState.toString() + " not supported");
            }
        }
    }

    /**
     * Adds data artifacts for analysis. The intended clients of this method are
     * ingest modules running code in an ingest thread that has not yet notified
     * the ingest task scheduler that the the primary ingest task that is the
     * source of the data artifacts is completed. This means that the new tasks
     * will be scheduled BEFORE the primary task has been removed from the
     * scheduler's running tasks list.
     *
     * @param artifacts The data artifacts.
     */
    void addDataArtifacts(List<DataArtifact> artifacts) {
        if (!isCancelled() && ingestModuleTiers.get(moduleTierIndex).hasDataArtifactIngestModules()) {
            switch (jobState) {
                case ACCEPTING_STREAMED_CONTENT_AND_ANALYZING:
                case ANALYZING:
                    taskScheduler.scheduleDataArtifactIngestTasks(this, artifacts);
                    break;
                case PIPELINES_SHUTTING_DOWN:
                    /*
                    * Don't log an error if there is an attempt to add an
                    * data artifact ingest task in a pipeline shut down
                    * state. This is a work around for dealing with data
                    * artifacts generated by a final keyword search carried out
                    * during ingest module shut down by simply ignoring them.
                    * (Currently these are credit card accounts generated by 
                    * keyword search). Other ideas were to add 
                    * a startShutDown() phase to the ingest module 
                    * life cycle (complicated), or to add a flag
                    * to keyword hit processing to suppress posting the keyword
                    * hit analysis results / data artifacts to the blackboard during a final
                    * search (API changes required to allow firing of the event
                    * to make any GUI refresh).
                    */
                    break;
                default:
                    logErrorMessage(Level.SEVERE, "Attempt to add data artifacts to job during stage " + jobState.toString() + " not supported");
                    break;
            }
        }
    }

    /**
     * Adds analysis results for analysis. The intended clients of this method
     * are ingest modules running code in an ingest thread that has not yet
     * notified the ingest task scheduler that the the primary ingest task that
     * is the source of the analysis results is completed. This means that the
     * new tasks will be scheduled BEFORE the primary task has been removed from
     * the scheduler's running tasks list.
     *
     * @param results The analysis results.
     */
    void addAnalysisResults(List<AnalysisResult> results) {
        if (!isCancelled() && ingestModuleTiers.get(moduleTierIndex).hasAnalysisResultIngestModules()) {
            switch (jobState) {
                case ACCEPTING_STREAMED_CONTENT_AND_ANALYZING:
                case ANALYZING:
                    taskScheduler.scheduleAnalysisResultIngestTasks(this, results);
                    break;
                case PIPELINES_SHUTTING_DOWN:
                    /*
                     * Don't log an error if there is an attempt to add an
                     * analysis result ingest task in a pipeline shut down
                     * state. This is a work around for dealing with analysis
                     * results generated by a final keyword search carried out
                     * during ingest module shut down by simply ignoring them.
                     * Other ideas were to add a startShutDown() phase to the
                     * ingest module life cycle (complicated), or to add a flag
                     * to keyword hit processing to suppress posting the keyword
                     * hit analysis results to the blackboard during a final
                     * search (API changes required to allow firing of the event
                     * to make any GUI refresh).
                     */
                    break;
                default:
                    logErrorMessage(Level.SEVERE, "Attempt to add analysis results to job during stage " + jobState.toString() + " not supported");
            }
        }
    }

    /**
     * Shuts down the ingest module pipelines in the current module tier.
     */
    private void shutDownCurrentTier() {
        // Note that this method is only called while holding the tierTransitionLock, so moduleTierIndex can not change
        // during execution.
        if (moduleTierIndex >= ingestModuleTiers.size()) {
            logErrorMessage(Level.SEVERE, "shutDownCurrentTier called with out-of-bounds moduleTierIndex (" + moduleTierIndex + ")");
            return;
        }
        logInfoMessage(String.format("Finished all ingest tasks for tier %s of ingest job", moduleTierIndex)); //NON-NLS        
        jobState = IngestJobExecutor.IngestJobState.PIPELINES_SHUTTING_DOWN;
        IngestModuleTier moduleTier = ingestModuleTiers.get(moduleTierIndex);

        Optional<DataSourceIngestPipeline> dataSourcePipeline = moduleTier.getDataSourceIngestPipeline();
        if (dataSourcePipeline.isPresent()) {
            shutDownIngestModulePipeline(dataSourcePipeline.get());
        }

        for (FileIngestPipeline pipeline : moduleTier.getFileIngestPipelines()) {
            shutDownIngestModulePipeline(pipeline);
        }

        Optional<DataArtifactIngestPipeline> dataArtifactPipeline = moduleTier.getDataArtifactIngestPipeline();
        if (dataArtifactPipeline.isPresent()) {
            shutDownIngestModulePipeline(dataArtifactPipeline.get());
        }

        Optional<AnalysisResultIngestPipeline> analysisResultPipeline = moduleTier.getAnalysisResultIngestPipeline();
        if (analysisResultPipeline.isPresent()) {
            shutDownIngestModulePipeline(analysisResultPipeline.get());
        }

        finishAllProgressBars();
    }

    /**
     * Shuts down an ingest module pipeline.
     *
     * @param pipeline The pipeline.
     */
    private <T extends IngestTask> void shutDownIngestModulePipeline(IngestPipeline<T> pipeline) {
        if (pipeline.isRunning()) {
            List<IngestModuleError> errors = new ArrayList<>();
            errors.addAll(pipeline.shutDown());
            if (!errors.isEmpty()) {
                logIngestModuleErrors(errors);
            }
        }
    }

    /**
     * Shuts down the ingest module pipelines and ingest job progress bars.
     */
    private void shutDown() {
        logInfoMessage("Finished all ingest tasks for ingest job"); //NON-NLS        
        try {
            if (casDbingestJobInfo != null) {
                if (jobCancelled) {
                    casDbingestJobInfo.setIngestJobStatus(IngestJobStatusType.CANCELLED);
                } else {
                    casDbingestJobInfo.setIngestJobStatus(IngestJobStatusType.COMPLETED);
                }
                casDbingestJobInfo.setEndDateTime(new Date());
            }
        } catch (TskCoreException ex) {
            logErrorMessage(Level.WARNING, "Failed to set job end date in case database", ex);
        }

        ingestJob.notifyIngestPipelinesShutDown();
    }

    /**
     * Gets the currently running data source level ingest module for this job.
     *
     * @return The currently running module, may be null.
     */
    DataSourceIngestPipeline.DataSourcePipelineModule getCurrentDataSourceIngestModule() {
        Optional<DataSourceIngestPipeline> pipeline = getCurrentDataSourceIngestPipelines();
        if (pipeline.isPresent()) {
            return (DataSourceIngestPipeline.DataSourcePipelineModule) pipeline.get().getCurrentlyRunningModule();
        } else {
            return null;
        }
    }

    /**
     * Requests a temporary cancellation of data source level ingest for this
     * job in order to stop the currently executing data source ingest module.
     *
     * Note that the DataSourceIngestModule interface does not currently have a
     * cancel() API. As a consequence, cancelling an individual data source
     * ingest module requires setting and then unsetting the
     * currentDataSourceIngestModuleCancelled flag. Because of this, there is no
     * ironclad guarantee that the correct module will be cancelled. We are
     * relying on the module being long-running to avoid a race condition
     * between module cancellation and the transition of the execution of a data
     * source level ingest task to another module.
     */
    void cancelCurrentDataSourceIngestModule() {
        currentDataSourceIngestModuleCancelled = true;
    }

    /**
     * Queries whether or not a temporary cancellation of data source level
     * ingest in order to stop the currently executing data source level ingest
     * module is in effect for this job.
     *
     * Note that the DataSourceIngestModule interface does not currently have a
     * cancel() API. As a consequence, cancelling an individual data source
     * ingest module requires setting and then unsetting the
     * currentDataSourceIngestModuleCancelled flag. Because of this, there is no
     * ironclad guarantee that the correct module will be cancelled. We are
     * relying on the module being long-running to avoid a race condition
     * between module cancellation and the transition of the execution of a data
     * source level ingest task to another module.
     *
     * @return True or false.
     */
    boolean currentDataSourceIngestModuleIsCancelled() {
        return currentDataSourceIngestModuleCancelled;
    }

    /**
     * Rescinds a temporary cancellation of data source level ingest that was
     * used to stop a single data source level ingest module for this job. The
     * data source ingest progress bar is reset, if the job has not been
     * cancelled.
     *
     * Note that the DataSourceIngestModule interface does not currently have a
     * cancel() API. As a consequence, cancelling an individual data source
     * ingest module requires setting and then unsetting the
     * currentDataSourceIngestModuleCancelled flag. Because of this, there is no
     * ironclad guarantee that the correct module will be cancelled. We are
     * relying on the module being long-running to avoid a race condition
     * between module cancellation and the transition of the execution of a data
     * source level ingest task to another module.
     *
     * @param moduleDisplayName The display name of the module that was stopped.
     */
    void currentDataSourceIngestModuleCancellationCompleted(String moduleDisplayName) {
        currentDataSourceIngestModuleCancelled = false;
        cancelledDataSourceIngestModules.add(moduleDisplayName);
        if (usingNetBeansGUI && !jobCancelled) {
            try {
                // use invokeAndWait to ensure synchronous behavior.  
                // See JIRA-8298 for more information.
                SwingUtilities.invokeAndWait(() -> {
                    /**
                     * A new progress bar must be created because the cancel
                     * button of the previously constructed component is
                     * disabled by NetBeans when the user selects the "OK"
                     * button of the cancellation confirmation dialog popped up
                     * by NetBeans when the progress bar cancel button is
                     * pressed.
                     */
                    dataSourceIngestProgressBar.finish();
                    dataSourceIngestProgressBar = null;
                    startDataSourceIngestProgressBar();
                });
            } catch (InvocationTargetException | InterruptedException ex) {
                logger.log(Level.WARNING, "Cancellation worker cancelled.", ex);
            }
        }
    }

    /**
     * Requests cancellation of the ingest job. All pending ingest tasks for the
     * job will be cancelled, but any tasks already in progress in ingest
     * threads will run to completion. This could take a while if the ingest
     * modules executing the tasks are not checking the ingest job cancellation
     * flag via the ingest joib context. Analysis already completed at the time
     * that cancellation occurs is NOT discarded.
     *
     * @param reason The cancellation reason.
     */
    void cancel(IngestJob.CancellationReason reason) {
        jobCancelled = true;
        cancellationReason = reason;
        displayCancellingProgressMessages();
        taskScheduler.cancelPendingFileTasksForIngestJob(getIngestJobId());
        synchronized (threadRegistrationLock) {
            for (Thread thread : pausedIngestThreads) {
                thread.interrupt();
            }
            pausedIngestThreads.clear();
        }
        checkForTierCompleted(moduleTierIndex);
    }

    /**
     * Queries whether or not cancellation of the ingest job has been requested.
     * Ingest modules executing ingest tasks for this job should check this flag
     * frequently via the ingest job context.
     *
     * @return True or false.
     */
    boolean isCancelled() {
        return jobCancelled;
    }

    /**
     * If the ingest job was cancelled, gets the reason this job was cancelled.
     *
     * @return The cancellation reason, may be "not cancelled."
     */
    IngestJob.CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    /**
     * Starts a NetBeans progress bar for data source level analysis in the
     * lower right hand corner of the main application window. The progress bar
     * provides the user with a task cancellation button. Pressing it cancels
     * either the currently running data source level ingest module, or the
     * entire ingest job.
     */
    private void startDataSourceIngestProgressBar() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                dataSourceIngestProgressBar = ProgressHandle.createHandle(NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataSourceIngest.initialDisplayName", ingestJob.getDataSource().getName()), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        /*
                         * The user has already pressed the cancel button on
                         * this progress bar, and the OK button of a cancelation
                         * confirmation dialog supplied by NetBeans. Find out
                         * whether the user wants to cancel only the currently
                         * executing data source ingest module or the entire
                         * ingest job.
                         */
                        DataSourceIngestCancellationPanel panel = new DataSourceIngestCancellationPanel();
                        String dialogTitle = NbBundle.getMessage(IngestJobExecutor.this.getClass(), "IngestJob.cancellationDialog.title");
                        JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(), panel, dialogTitle, JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
                        if (panel.cancelAllDataSourceIngestModules()) {
                            new Thread(() -> {
                                IngestJobExecutor.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                            }).start();
                        } else {
                            new Thread(() -> {
                                IngestJobExecutor.this.cancelCurrentDataSourceIngestModule();
                            }).start();
                        }
                        return true;
                    }
                });
                dataSourceIngestProgressBar.start();
                dataSourceIngestProgressBar.switchToIndeterminate();
            });
        }
    }

    /**
     * Changes the title (display name) shown on the current data source level
     * ingest progress bar, if the ingest job has not been cancelled.
     *
     * @param title The title to display.
     */
    void changeDataSourceIngestProgressBarTitle(String title) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.setDisplayName(title);
                }
            });
        }
    }

    /**
     * Switches the current data source level ingest progress bar to
     * indeterminate mode, if the ingest job has not been cancelled.
     */
    void switchDataSourceIngestProgressBarToIndeterminate() {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.switchToIndeterminate();
                }
            });
        }
    }

    /**
     * Switches the current data source level ingest progress bar to determinate
     * mode, if the job has not been cancelled.
     *
     * @param workUnitsToDo The total number of work units to be done.
     */
    void switchDataSourceIngestProgressBarToDeterminate(int workUnitsToDo) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.switchToDeterminate(workUnitsToDo);
                }
            });
        }
    }

    /**
     * Updates the current data source level ingest progress bar with new text,
     * and sets the number of work units done so far, if in the determinate
     * mode, and the ingest job has not been cancelled. The text can be changed
     * independently of the total number of work units done by calling
     * updateDataSourceIngestProgressBarText(String newText); likewise,
     * updateDataSourceIngestProgressBar(int workUnitsDone) can be called to
     * update the work units bar without changing the text.
     *
     * IMPORTANT: The progress bar must never be advanced beyond the number of
     * work units to do that were specified when
     * switchDataSourceIngestProgressBarToDeterminate() was called. Doing so has
     * been observed to call cause an infinite loop.
     *
     * @param newText       The new text.
     * @param workUnitsDone The total number of work units done so far.
     */
    void updateDataSourceIngestProgressBar(String newText, int workUnitsDone) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress(newText, workUnitsDone);
                }
            });
        }
    }

    /**
     * Changes the text displayed in the current data source level ingest
     * progress bar, if the job has not been cancelled.
     *
     * @param newText The new text.
     */
    void updateDataSourceIngestProgressBarText(String newText) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress(newText);
                }
            });
        }
    }

    /**
     * Updates the current data source level ingest progress bar with the given
     * total number of work units done so far, if in determinate mode, and the
     * job has not been cancelled. The text displayed in the progress bar is not
     * changed.
     *
     * IMPORTANT: The progress bar must never be advanced beyond the number of
     * work units to do that were specified when
     * switchDataSourceIngestProgressBarToDeterminate() was called. Doing so has
     * been observed to call cause an infinite loop.
     *
     * @param workUnitsDone The total number of work units done so far.
     */
    void updateDataSourceIngestProgressBar(int workUnitsDone) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress("", workUnitsDone);
                }
            });
        }
    }

    /**
     * Starts a NetBeans progress bar for file analysis in the lower right hand
     * corner of the main application window. The progress bar provides the user
     * with a task cancellation button. Pressing it cancels the entire ingest
     * job.
     *
     * @param useDeterminateMode Whether or not to start the progress bar in
     *                           determinate mode with the number of work units
     *                           to be completed set to the estimated number of
     *                           files to process.
     */
    private void startFileIngestProgressBar(boolean useDeterminateMode) {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                fileIngestProgressBar = ProgressHandle.createHandle(NbBundle.getMessage(getClass(), "IngestJob.progress.fileIngest.displayName", ingestJob.getDataSource().getName()), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        new Thread(() -> {
                            IngestJobExecutor.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        }).start();
                        return true;
                    }
                });
                if (useDeterminateMode) {
                    fileIngestProgressBar.start((int) estimatedFilesToProcess);
                } else {
                    fileIngestProgressBar.start();
                }
            });
        }
    }

    /**
     * Switches the file ingest progress bar to determinate mode, using the
     * estimated number of files to process as the total number of work units to
     * be done.
     */
    private void switchFileIngestProgressBarToDeterminate() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                if (fileIngestProgressBar != null) {
                    fileIngestProgressBar.switchToDeterminate((int) estimatedFilesToProcess);
                }
            });
        }
    }

    /**
     * Changes the text of the file ingest progress bar to the given file name,
     * and sets the total number of work units done so far to the number of
     * processed files, if the ingest job has not been cancelled.
     *
     * @param fileName The file name.
     */
    private void updateFileProgressBarForFileTaskStarted(String fileName) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                /*
                 * If processedFiles exceeds estimatedFilesToProcess, i.e., the
                 * max work units set for the progress bar, the progress bar
                 * will go into an infinite loop throwing
                 * IllegalArgumentExceptions in the EDT (NetBeans bug). Also, a
                 * check-then-act race condition needs to be avoided here. This
                 * can be done without guarding processedFiles and
                 * estimatedFilesToProcess with the same lock because
                 * estimatedFilesToProcess does not change after it is used to
                 * switch the progress bar to determinate mode.
                 */
                long processedFilesCapture = processedFiles;
                if (processedFilesCapture <= estimatedFilesToProcess) {
                    fileIngestProgressBar.progress(fileName, (int) processedFilesCapture);
                } else {
                    fileIngestProgressBar.progress(fileName, (int) estimatedFilesToProcess);
                }
                filesInProgress.add(fileName);
            });
        }
    }

    /**
     * Updates the current file ingest progress bar upon completion of analysis
     * of a file, if the job has not been cancelled. Does not update the total
     * number of work units done so far.
     *
     * @param completedFileName The name of the file for which analysis has been
     *                          completed.
     */
    private void updateFileProgressBarForFileTaskCompleted(String completedFileName) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                filesInProgress.remove(completedFileName);
                /*
                 * Display the name of another file in progress, or the empty
                 * string if there are none.
                 */
                if (filesInProgress.size() > 0) {
                    fileIngestProgressBar.progress(filesInProgress.get(0));
                } else {
                    fileIngestProgressBar.progress(""); // NON-NLS
                }
            });
        }
    }

    /**
     * Starts a NetBeans progress bar for data artifacts analysis in the lower
     * right hand corner of the main application window. The progress bar
     * provides the user with a task cancellation button. Pressing it cancels
     * the entire ingest job.
     */
    private void startDataArtifactIngestProgressBar() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                artifactIngestProgressBar = ProgressHandle.createHandle(NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataArtifactIngest.displayName", ingestJob.getDataSource().getName()), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        new Thread(() -> {
                            IngestJobExecutor.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        }).start();
                        return true;
                    }
                });
                artifactIngestProgressBar.start();
                artifactIngestProgressBar.switchToIndeterminate();
            });
        }
    }

    /**
     * Starts a NetBeans progress bar for analysis results analysis in the lower
     * right hand corner of the main application window. The progress bar
     * provides the user with a task cancellation button. Pressing it cancels
     * the entire ingest job.
     */
    @NbBundle.Messages({
        "# {0} - data source name",
        "IngestJob_progress_analysisResultIngest_displayName=Analyzing analysis results from {0}"
    })
    private void startAnalysisResultIngestProgressBar() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                resultIngestProgressBar = ProgressHandle.createHandle(Bundle.IngestJob_progress_analysisResultIngest_displayName(ingestJob.getDataSource().getName()), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        new Thread(() -> {
                            IngestJobExecutor.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        }).start();
                        return true;
                    }
                });
                resultIngestProgressBar.start();
                resultIngestProgressBar.switchToIndeterminate();
            });
        }
    }

    /**
     * Displays a "cancelling" message on all of the current ingest message
     * progress bars.
     */
    private void displayCancellingProgressMessages() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.setDisplayName(NbBundle.getMessage(getClass(), "IngestJob.progress.dataSourceIngest.initialDisplayName", ingestJob.getDataSource().getName()));
                    dataSourceIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
                if (fileIngestProgressBar != null) {
                    fileIngestProgressBar.setDisplayName(NbBundle.getMessage(getClass(), "IngestJob.progress.fileIngest.displayName", ingestJob.getDataSource().getName()));
                    fileIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
                if (artifactIngestProgressBar != null) {
                    artifactIngestProgressBar.setDisplayName(NbBundle.getMessage(getClass(), "IngestJob.progress.dataArtifactIngest.displayName", ingestJob.getDataSource().getName()));
                    artifactIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
                if (resultIngestProgressBar != null) {
                    resultIngestProgressBar.setDisplayName(Bundle.IngestJob_progress_analysisResultIngest_displayName(ingestJob.getDataSource().getName()));
                    resultIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
            });
        }
    }

    /**
     * Finishes all of the ingest progress bars.
     */
    private void finishAllProgressBars() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.finish();
                    dataSourceIngestProgressBar = null;
                }

                if (fileIngestProgressBar != null) {
                    fileIngestProgressBar.finish();
                    fileIngestProgressBar = null;
                }

                if (artifactIngestProgressBar != null) {
                    artifactIngestProgressBar.finish();
                    artifactIngestProgressBar = null;
                }

                if (resultIngestProgressBar != null) {
                    resultIngestProgressBar.finish();
                    resultIngestProgressBar = null;
                }
            });
        }
    }

    /**
     * Writes an info message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param message The message.
     */
    private void logInfoMessage(String message) {
        logger.log(Level.INFO, String.format("%s (data source = %s, data source object ID = %d, job ID = %d)", message, ingestJob.getDataSource().getName(), ingestJob.getDataSource().getId(), getIngestJobId())); //NON-NLS        
    }

    /**
     * Writes an error message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param level     The logging level for the message.
     * @param message   The message.
     * @param throwable The throwable associated with the error.
     */
    private void logErrorMessage(Level level, String message, Throwable throwable) {
        logger.log(level, String.format("%s (data source = %s, data source object ID = %d, ingest job ID = %d)", message, ingestJob.getDataSource().getName(), ingestJob.getDataSource().getId(), getIngestJobId()), throwable); //NON-NLS
    }

    /**
     * Writes an error message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param level   The logging level for the message.
     * @param message The message.
     */
    private void logErrorMessage(Level level, String message) {
        logger.log(level, String.format("%s (data source = %s, data source object ID= %d, ingest job ID %d)", message, ingestJob.getDataSource().getName(), ingestJob.getDataSource().getId(), getIngestJobId())); //NON-NLS
    }

    /**
     * Writes ingest module errors to the log.
     *
     * @param errors The errors.
     */
    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            logErrorMessage(Level.SEVERE, String.format("%s experienced an error during analysis", error.getModuleDisplayName()), error.getThrowable()); //NON-NLS
        }
    }

    /**
     * Write ingest module errors to the log.
     *
     * @param errors The errors.
     * @param file   AbstractFile that caused the errors.
     */
    private void logIngestModuleErrors(List<IngestModuleError> errors, AbstractFile file) {
        for (IngestModuleError error : errors) {
            logErrorMessage(Level.SEVERE, String.format("%s experienced an error during analysis while processing file %s (object ID = %d)", error.getModuleDisplayName(), file.getName(), file.getId()), error.getThrowable()); //NON-NLS
        }
    }
    
    /**
     * Safely gets the file ingest pipelines for the current tier.
     * 
     * @return The file ingest pipelines or empty if ingest has completed/is shutting down.
     */
    Optional<List<FileIngestPipeline>> getCurrentFileIngestPipelines() {
        // Make a local copy in case the tier increments
        int currentModuleTierIndex = moduleTierIndex;
        if (currentModuleTierIndex < ingestModuleTiers.size()) {
            return Optional.of(ingestModuleTiers.get(currentModuleTierIndex).getFileIngestPipelines());
        }
        return Optional.empty();
    }
    
    /**
     * Safely gets the data source ingest pipeline for the current tier.
     * 
     * @return The data source ingest pipeline or empty if ingest has completed/is shutting down.
     */
    Optional<DataSourceIngestPipeline> getCurrentDataSourceIngestPipelines() {
        // Make a local copy in case the tier increments
        int currentModuleTierIndex = moduleTierIndex;
        if (currentModuleTierIndex < ingestModuleTiers.size()) {
            return ingestModuleTiers.get(currentModuleTierIndex).getDataSourceIngestPipeline();
        }
        return Optional.empty();
    }
    

    /**
     * Gets a snapshot of some basic diagnostic statistics for the ingest job
     * this ingest job executor is executing.
     *
     * @param includeIngestTasksSnapshot Whether or not to include ingest task
     *                                   stats in the snapshot.
     *
     * @return The snapshot.
     */
    @Messages({
        "IngestJobExecutor_progress_snapshot_currentTier_shutDown_modifier=shut down",
        "# {0} - tier number",
        "# {1} - job state modifer",
        "IngestJobExecutor_progress_snapshot_currentTier=Tier {0} {1}"
    })
    IngestJobProgressSnapshot getIngestJobProgressSnapshot(boolean includeIngestTasksSnapshot) {
        /*
         * Determine whether file ingest is running at the time of this snapshot
         * and determine the earliest file ingest module pipeline start time, if
         * file ingest was started at all.
         */
        boolean fileIngestRunning = false;
        Date fileIngestStartTime = null;
        Optional<List<FileIngestPipeline>> fileIngestPipelines = getCurrentFileIngestPipelines();
        if (!fileIngestPipelines.isPresent()) {
            // If there are no currently running pipelines, use the original set.
            fileIngestPipelines = Optional.of(ingestModuleTiers.get(0).getFileIngestPipelines());
        }
        for (FileIngestPipeline pipeline : fileIngestPipelines.get()) {
            if (pipeline.isRunning()) {
                fileIngestRunning = true;
            }
            Date pipelineStartTime = pipeline.getStartTime();
            if (pipelineStartTime != null && (fileIngestStartTime == null || pipelineStartTime.before(fileIngestStartTime))) {
                fileIngestStartTime = pipelineStartTime;
            }
        }

        long processedFilesCount = 0;
        long estimatedFilesToProcessCount = 0;
        long snapShotTime = new Date().getTime();
        IngestTasksScheduler.IngestTasksSnapshot tasksSnapshot = null;
        if (includeIngestTasksSnapshot) {
            processedFilesCount = processedFiles;
            estimatedFilesToProcessCount = estimatedFilesToProcess;
            snapShotTime = new Date().getTime();
            tasksSnapshot = taskScheduler.getTasksSnapshotForJob(getIngestJobId());
        }
        return new IngestJobProgressSnapshot(
                ingestJob.getDataSource().getName(),
                getIngestJobId(),
                createTime,
                Bundle.IngestJobExecutor_progress_snapshot_currentTier(moduleTierIndex, jobState.equals(IngestJobState.PIPELINES_SHUTTING_DOWN) ? Bundle.IngestJobExecutor_progress_snapshot_currentTier_shutDown_modifier() : ""),
                getCurrentDataSourceIngestModule(),
                fileIngestRunning,
                fileIngestStartTime,
                jobCancelled,
                cancellationReason,
                cancelledDataSourceIngestModules,
                processedFilesCount,
                estimatedFilesToProcessCount,
                snapShotTime,
                tasksSnapshot);
    }

    /**
     * Registers a sleeping ingest thread so that it can be interrupted if the
     * ingest job is cancelled.
     *
     * @param thread The ingest thread.
     */
    void registerPausedIngestThread(Thread thread) {
        synchronized (threadRegistrationLock) {
            pausedIngestThreads.add(thread);
        }
    }

    /**
     * Unregisters a sleeping ingest thread that was registered so that it could
     * be interrupted if the ingest job was cancelled.
     *
     * @param thread The ingest thread.
     */
    void unregisterPausedIngestThread(Thread thread) {
        synchronized (threadRegistrationLock) {
            pausedIngestThreads.remove(thread);
        }
    }

}
