/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2019 Basis Technology Corp.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.IngestTasksScheduler.IngestJobTasksSnapshot;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestJobInfo.IngestJobStatusType;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.IngestModuleInfo.IngestModuleType;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet;
import org.sleuthkit.autopsy.python.FactoryClassNameNormalizer;

/**
 * Encapsulates a data source and the ingest module pipelines used to process
 * it.
 */
final class IngestJobPipeline {

    private static String AUTOPSY_MODULE_PREFIX = "org.sleuthkit.autopsy";

    private static final Logger logger = Logger.getLogger(IngestJobPipeline.class.getName());

    // to match something like: "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14"
    private static final Pattern JYTHON_REGEX = Pattern.compile("org\\.python\\.proxies\\.(.+?)\\$(.+?)(\\$[0-9]*)?$");

    /**
     * These fields define a data source ingest job: the parent ingest job, an
     * ID, the user's ingest job settings, and the data source to be analyzed.
     * Optionally, there is a set of files to be analyzed instead of analyzing
     * all of the files in the data source.
     */
    private final IngestJob parentJob;
    private static final AtomicLong nextJobId = new AtomicLong(0L);
    private final long id;
    private final IngestJobSettings settings;
    private Content dataSource = null;
    private final IngestJob.Mode ingestMode;
    private final List<AbstractFile> files = new ArrayList<>();

    /**
     * A data source ingest job runs in stages.
     */
    private static enum Stages {

        /**
         * Setting up for processing.
         */
        INITIALIZATION,
        /**
         * Running only file ingest modules (used only for streaming ingest)
         */
        FIRST_STAGE_FILES_ONLY,
        /**
         * Running high priority data source level ingest modules and file level
         * ingest modules.
         */
        FIRST_STAGE_FILES_AND_DATASOURCE,
        /**
         * Running lower priority, usually long-running, data source level
         * ingest modules.
         */
        SECOND_STAGE,
        /**
         * Cleaning up.
         */
        FINALIZATION
    };
    private volatile Stages stage = IngestJobPipeline.Stages.INITIALIZATION;
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
    private volatile IngestJobInfo ingestJob;

    /**
     * A data source ingest job uses this field to report its creation time.
     */
    private final long createTime;

    /**
     * Constructs an object that encapsulates a data source and the ingest
     * module pipelines used to analyze it.
     *
     * @param parentJob  The ingest job of which this data source ingest job is
     *                   a part.
     * @param dataSource The data source to be ingested.
     * @param settings   The settings for the ingest job.
     */
    IngestJobPipeline(IngestJob parentJob, Content dataSource, IngestJobSettings settings) {
        this(parentJob, dataSource, Collections.emptyList(), settings);
    }

    /**
     * Constructs an object that encapsulates a data source and the ingest
     * module pipelines used to analyze it. Either all of the files in the data
     * source or a given subset of the files will be analyzed.
     *
     * @param parentJob  The ingest job of which this data source ingest job is
     *                   a part.
     * @param dataSource The data source to be ingested.
     * @param files      A subset of the files for the data source.
     * @param settings   The settings for the ingest job.
     */
    IngestJobPipeline(IngestJob parentJob, Content dataSource, List<AbstractFile> files, IngestJobSettings settings) {
        this.parentJob = parentJob;
        this.id = IngestJobPipeline.nextJobId.getAndIncrement();
        this.dataSource = dataSource;
        this.files.addAll(files);
        this.ingestMode = parentJob.getIngestMode();
        this.settings = settings;
        this.doUI = RuntimeProperties.runningWithGUI();
        this.createTime = new Date().getTime();
        this.stage = Stages.INITIALIZATION;
        this.createIngestPipelines();
    }

    /**
     * Adds ingest modules to a list with autopsy modules first and third party
     * modules next.
     *
     * @param dest      The destination for the modules to be added.
     * @param src       A map of fully qualified class name mapped to the
     *                  IngestModuleTemplate.
     * @param jythonSrc A map of fully qualified class name mapped to the
     *                  IngestModuleTemplate for jython modules.
     */
    private static void addOrdered(final List<IngestModuleTemplate> dest,
            final Map<String, IngestModuleTemplate> src, final Map<String, IngestModuleTemplate> jythonSrc) {

        final List<IngestModuleTemplate> autopsyModules = new ArrayList<>();
        final List<IngestModuleTemplate> thirdPartyModules = new ArrayList<>();

        Stream.concat(src.entrySet().stream(), jythonSrc.entrySet().stream()).forEach((templateEntry) -> {
            if (templateEntry.getKey().startsWith(AUTOPSY_MODULE_PREFIX)) {
                autopsyModules.add(templateEntry.getValue());
            } else {
                thirdPartyModules.add(templateEntry.getValue());
            }
        });

        dest.addAll(autopsyModules);
        dest.addAll(thirdPartyModules);
    }

    /**
     * Takes a classname like
     * "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14"
     * and provides "GPX_Parser_Module.GPXParserFileIngestModuleFactory" or null
     * if not in jython package.
     *
     * @param canonicalName The canonical name.
     *
     * @return The jython name or null if not in jython package.
     */
    private static String getJythonName(String canonicalName) {
        Matcher m = JYTHON_REGEX.matcher(canonicalName);
        if (m.find()) {
            return String.format("%s.%s", m.group(1), m.group(2));
        } else {
            return null;
        }
    }

    /**
     * Adds a template to the appropriate map. If the class is a jython class,
     * then it is added to the jython map. Otherwise, it is added to the
     * mapping.
     *
     * @param mapping       Mapping for non-jython objects.
     * @param jythonMapping Mapping for jython objects.
     * @param template      The template to add.
     */
    private static void addModule(Map<String, IngestModuleTemplate> mapping,
            Map<String, IngestModuleTemplate> jythonMapping, IngestModuleTemplate template) {

        String className = template.getModuleFactory().getClass().getCanonicalName();
        String jythonName = getJythonName(className);
        if (jythonName != null) {
            jythonMapping.put(jythonName, template);
        } else {
            mapping.put(className, template);
        }
    }

    /**
     * Creates the file and data source ingest pipelines.
     */
    private void createIngestPipelines() {
        List<IngestModuleTemplate> ingestModuleTemplates = this.settings.getEnabledIngestModuleTemplates();

        /**
         * Make mappings of ingest module factory class names to templates.
         */
        Map<String, IngestModuleTemplate> dataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> fileModuleTemplates = new LinkedHashMap<>();

        // mappings for jython modules.  These mappings are only used to determine modules in the pipelineconfig.xml.
        Map<String, IngestModuleTemplate> jythonDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonFileModuleTemplates = new LinkedHashMap<>();

        for (IngestModuleTemplate template : ingestModuleTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                addModule(dataSourceModuleTemplates, jythonDataSourceModuleTemplates, template);
            }
            if (template.isFileIngestModuleTemplate()) {
                addModule(fileModuleTemplates, jythonFileModuleTemplates, template);
            }
        }

        /**
         * Use the mappings and the ingest pipelines configuration to create
         * ordered lists of ingest module templates for each ingest pipeline.
         */
        IngestPipelinesConfiguration pipelineConfigs = IngestPipelinesConfiguration.getInstance();
        List<IngestModuleTemplate> firstStageDataSourceModuleTemplates = IngestJobPipeline.getConfiguredIngestModuleTemplates(
                dataSourceModuleTemplates, jythonDataSourceModuleTemplates, pipelineConfigs.getStageOneDataSourceIngestPipelineConfig());

        List<IngestModuleTemplate> fileIngestModuleTemplates = IngestJobPipeline.getConfiguredIngestModuleTemplates(
                fileModuleTemplates, jythonFileModuleTemplates, pipelineConfigs.getFileIngestPipelineConfig());

        List<IngestModuleTemplate> secondStageDataSourceModuleTemplates = IngestJobPipeline.getConfiguredIngestModuleTemplates(
                dataSourceModuleTemplates, null, pipelineConfigs.getStageTwoDataSourceIngestPipelineConfig());

        /**
         * Add any module templates that were not specified in the pipelines
         * configuration to an appropriate pipeline - either the first stage
         * data source ingest pipeline or the file ingest pipeline.
         */
        addOrdered(firstStageDataSourceModuleTemplates, dataSourceModuleTemplates, jythonDataSourceModuleTemplates);
        addOrdered(fileIngestModuleTemplates, fileModuleTemplates, jythonFileModuleTemplates);

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
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            this.addIngestModules(firstStageDataSourceModuleTemplates, IngestModuleType.DATA_SOURCE_LEVEL, skCase);
            this.addIngestModules(fileIngestModuleTemplates, IngestModuleType.FILE_LEVEL, skCase);
            this.addIngestModules(secondStageDataSourceModuleTemplates, IngestModuleType.DATA_SOURCE_LEVEL, skCase);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logErrorMessage(Level.WARNING, "Failed to add ingest modules listing to case database", ex);
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
     * @param ingestModuleTemplates       A mapping of ingest module factory
     *                                    class names to ingest module
     *                                    templates.
     * @param jythonIngestModuleTemplates A mapping of jython processed class
     *                                    names to jython ingest module
     *                                    templates.
     * @param pipelineConfig              An ordered list of ingest module
     *                                    factory class names representing an
     *                                    ingest pipeline.
     *
     * @return An ordered list of ingest module templates, i.e., an
     *         uninstantiated pipeline.
     */
    private static List<IngestModuleTemplate> getConfiguredIngestModuleTemplates(
            Map<String, IngestModuleTemplate> ingestModuleTemplates, Map<String, IngestModuleTemplate> jythonIngestModuleTemplates, List<String> pipelineConfig) {
        List<IngestModuleTemplate> templates = new ArrayList<>();
        for (String moduleClassName : pipelineConfig) {
            if (ingestModuleTemplates != null && ingestModuleTemplates.containsKey(moduleClassName)) {
                templates.add(ingestModuleTemplates.remove(moduleClassName));
            } else if (jythonIngestModuleTemplates != null && jythonIngestModuleTemplates.containsKey(moduleClassName)) {
                templates.add(jythonIngestModuleTemplates.remove(moduleClassName));
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
        if (dataSource == null) {
            // TODO - Remove once data source is always present during initialization
            throw new IllegalStateException("Ingest started before setting data source");
        }
        List<IngestModuleError> errors = startUpIngestPipelines();
        if (errors.isEmpty()) {
            try {
                this.ingestJob = Case.getCurrentCaseThrows().getSleuthkitCase().addIngestJob(dataSource, NetworkUtils.getLocalHostName(), ingestModules, new Date(this.createTime), new Date(0), IngestJobStatusType.STARTED, "");
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logErrorMessage(Level.WARNING, "Failed to add ingest job info to case database", ex); //NON-NLS
            }

            if (this.hasFirstStageDataSourceIngestPipeline() || this.hasFileIngestPipeline()) {
                if (ingestMode == IngestJob.Mode.BATCH) {
                    logInfoMessage("Starting first stage analysis"); //NON-NLS
                    this.startFirstStage();
                } else {
                    logInfoMessage("Preparing for first stage analysis"); //NON-NLS
                    this.startFileIngestStreaming();
                }
            } else if (this.hasSecondStageDataSourceIngestPipeline()) {
                logInfoMessage("Starting second stage analysis"); //NON-NLS
                this.startSecondStage();
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
        this.stage = IngestJobPipeline.Stages.FIRST_STAGE_FILES_AND_DATASOURCE;

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
            logInfoMessage("Scheduling first stage data source and file level analysis tasks"); //NON-NLS
            IngestJobPipeline.taskScheduler.scheduleIngestTasks(this);
        } else if (this.hasFirstStageDataSourceIngestPipeline()) {
            logInfoMessage("Scheduling first stage data source level analysis tasks"); //NON-NLS
            IngestJobPipeline.taskScheduler.scheduleDataSourceIngestTask(this);
        } else {
            logInfoMessage("Scheduling file level analysis tasks, no first stage data source level analysis configured"); //NON-NLS
            IngestJobPipeline.taskScheduler.scheduleFileIngestTasks(this, this.files);

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
     * Prepares for file ingest. Used for streaming ingest. Does not schedule
     * any file tasks - those will come from calls to addStreamingIngestFiles().
     */
    private void startFileIngestStreaming() {
        synchronized (this.stageCompletionCheckLock) {
            this.stage = IngestJobPipeline.Stages.FIRST_STAGE_FILES_ONLY;
        }

        if (this.hasFileIngestPipeline()) {
            synchronized (this.fileIngestProgressLock) {
                this.estimatedFilesToProcess = 0; // Set to indeterminate until the data source is complete
            }
        }

        if (this.doUI) {
            if (this.hasFileIngestPipeline()) {
                this.startFileIngestProgressBar();
            }
        }

        logInfoMessage("Waiting for streaming files"); //NON-NLS
    }

    /**
     * Start data source ingest. Used for streaming ingest when the data source
     * is not ready when ingest starts.
     */
    private void startDataSourceIngestStreaming() {

        // Now that the data source is complete, we can get the estimated number of
        // files and switch to a determinate progress bar.
        synchronized (fileIngestProgressLock) {
            if (null != this.fileIngestProgress) {
                estimatedFilesToProcess = dataSource.accept(new GetFilesCountVisitor());
                fileIngestProgress.switchToDeterminate((int) estimatedFilesToProcess);
            }
        }

        if (this.doUI) {
            /**
             * Start the first stage data source ingest progress bar.
             */
            if (this.hasFirstStageDataSourceIngestPipeline()) {
                this.startDataSourceIngestProgressBar();
            }
        }

        /**
         * Make the first stage data source level ingest pipeline the current
         * data source level pipeline.
         */
        synchronized (this.dataSourceIngestPipelineLock) {
            this.currentDataSourceIngestPipeline = this.firstStageDataSourceIngestPipeline;
        }

        logInfoMessage("Scheduling first stage data source level analysis tasks"); //NON-NLS
        synchronized (this.stageCompletionCheckLock) {
            this.stage = IngestJobPipeline.Stages.FIRST_STAGE_FILES_AND_DATASOURCE;
            IngestJobPipeline.taskScheduler.scheduleDataSourceIngestTask(this);
        }
    }

    /**
     * Starts the second stage of this ingest job.
     */
    private void startSecondStage() {
        logInfoMessage("Starting second stage analysis"); //NON-NLS        
        this.stage = IngestJobPipeline.Stages.SECOND_STAGE;
        if (this.doUI) {
            this.startDataSourceIngestProgressBar();
        }
        synchronized (this.dataSourceIngestPipelineLock) {
            this.currentDataSourceIngestPipeline = this.secondStageDataSourceIngestPipeline;
        }
        logInfoMessage("Scheduling second stage data source level analysis tasks"); //NON-NLS        
        IngestJobPipeline.taskScheduler.scheduleDataSourceIngestTask(this);
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
                        String dialogTitle = NbBundle.getMessage(IngestJobPipeline.this.getClass(), "IngestJob.cancellationDialog.title");
                        JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(), panel, dialogTitle, JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
                        if (panel.cancelAllDataSourceIngestModules()) {
                            IngestJobPipeline.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        } else {
                            IngestJobPipeline.this.cancelCurrentDataSourceIngestModule();
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
                        IngestJobPipeline.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
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
        if (ingestMode == IngestJob.Mode.BATCH) {
            checkForStageCompletedBatch();
        } else {
            checkForStageCompletedStreaming();
        }
    }

    /**
     * Checks to see if the ingest tasks for the current stage of this job are
     * completed and does a stage transition if they are.
     */
    private void checkForStageCompletedBatch() {
        synchronized (this.stageCompletionCheckLock) {
            if (IngestJobPipeline.taskScheduler.currentTasksAreCompleted(this)) {
                switch (this.stage) {
                    case FIRST_STAGE_FILES_AND_DATASOURCE:
                        this.finishFirstStage();
                        break;
                    case SECOND_STAGE:
                        this.finish();
                        break;
                }
            }
        }
    }

    /**
     * Checks to see if the ingest tasks for the current stage of this job are
     * completed and does a stage transition if they are.
     */
    private void checkForStageCompletedStreaming() {
        synchronized (this.stageCompletionCheckLock) {
            if (IngestJobPipeline.taskScheduler.currentTasksAreCompleted(this)) {
                switch (this.stage) {
                    case FIRST_STAGE_FILES_ONLY:
                        // Nothing to do here - need to wait for the data source
                        break;
                    case FIRST_STAGE_FILES_AND_DATASOURCE:
                        // Finish file and data source ingest, start second stage (if applicable)
                        this.finishFirstStage();
                        break;
                    case SECOND_STAGE:
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
        logInfoMessage("Finished first stage analysis"); //NON-NLS        

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
        logInfoMessage("Finished analysis"); //NON-NLS        
        this.stage = IngestJobPipeline.Stages.FINALIZATION;

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
        if (ingestJob != null) {
            if (this.cancelled) {
                try {
                    ingestJob.setIngestJobStatus(IngestJobStatusType.CANCELLED);
                } catch (TskCoreException ex) {
                    logErrorMessage(Level.WARNING, "Failed to update ingest job status in case database", ex);
                }
            } else {
                try {
                    ingestJob.setIngestJobStatus(IngestJobStatusType.COMPLETED);
                } catch (TskCoreException ex) {
                    logErrorMessage(Level.WARNING, "Failed to update ingest job status in case database", ex);
                }
            }
            try {
                this.ingestJob.setEndDateTime(new Date());
            } catch (TskCoreException ex) {
                logErrorMessage(Level.WARNING, "Failed to set job end date in case database", ex);
            }
        }
        this.parentJob.ingestJobPipelineFinished(this);
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
            IngestJobPipeline.taskScheduler.notifyTaskCompleted(task);
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
                    AbstractFile file;
                    try {
                        file = task.getFile();
                    } catch (TskCoreException ex) {
                        // In practice, this task would never have been enqueued since the file
                        // lookup would have failed there.
                        List<IngestModuleError> errors = new ArrayList<>();
                        errors.add(new IngestModuleError("Ingest Job Pipeline", ex));
                        logIngestModuleErrors(errors);
                        this.fileIngestPipelinesQueue.put(pipeline);
                        return;
                    }

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
            IngestJobPipeline.taskScheduler.notifyTaskCompleted(task);
            this.checkForStageCompleted();
        }
    }

    /**
     * Add a list of files (by object ID) to the ingest queue. Must call start()
     * prior to adding files.
     *
     * @param fileObjIds List of newly added file IDs.
     */
    void addStreamingIngestFiles(List<Long> fileObjIds) {

        // Return if there are no file ingest modules enabled.
        if (!hasFileIngestPipeline()) {
            return;
        }

        if (stage.equals(Stages.FIRST_STAGE_FILES_ONLY)) {
            IngestJobPipeline.taskScheduler.scheduleStreamedFileIngestTasks(this, fileObjIds);
        } else {
            logErrorMessage(Level.SEVERE, "Adding streaming files to job during stage " + stage.toString() + " not supported");
        }
    }

    /**
     * Starts data source ingest. Should be called after the data source
     * processor has finished (i.e., all files are in the database)
     */
    void processStreamingIngestDataSource() {
        startDataSourceIngestStreaming();
        checkForStageCompleted();
    }

    /**
     * Adds more files from the data source for this job to the job, e.g., adds
     * extracted or carved files. Not currently supported for the second stage
     * of the job.
     *
     * @param files A list of the files to add.
     */
    void addFiles(List<AbstractFile> files) {
        if (stage.equals(Stages.FIRST_STAGE_FILES_ONLY)
                || stage.equals(Stages.FIRST_STAGE_FILES_AND_DATASOURCE)) {
            IngestJobPipeline.taskScheduler.fastTrackFileIngestTasks(this, files);
        } else {
            logErrorMessage(Level.SEVERE, "Adding files to job during second stage analysis not supported");
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
        IngestJobPipeline.taskScheduler.cancelPendingTasksForIngestJob(this);

        if (this.doUI) {
            synchronized (this.dataSourceIngestProgressLock) {
                if (null != dataSourceIngestProgress) {
                    dataSourceIngestProgress.setDisplayName(NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataSourceIngest.initialDisplayName", this.dataSource.getName()));
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
        
        // If a data source had no tasks in progress it may now be complete.
        checkForStageCompleted();
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
     * Writes an info message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param message The message.
     */
    private void logInfoMessage(String message) {
        logger.log(Level.INFO, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id = %d)", message, this.dataSource.getName(), this.dataSource.getId(), id, ingestJob.getIngestJobId())); //NON-NLS        
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
        logger.log(level, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id = %d)", message, this.dataSource.getName(), this.dataSource.getId(), id, ingestJob.getIngestJobId()), throwable); //NON-NLS
    }

    /**
     * Writes an error message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param level   The logging level for the message.
     * @param message The message.
     */
    private void logErrorMessage(Level level, String message) {
        logger.log(level, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id %d)", message, this.dataSource.getName(), this.dataSource.getId(), id, ingestJob.getIngestJobId())); //NON-NLS
    }

    /**
     * Write ingest module errors to the log.
     *
     * @param errors The errors.
     */
    private void logIngestModuleErrors(List<IngestModuleError> errors) {
        for (IngestModuleError error : errors) {
            logErrorMessage(Level.SEVERE, String.format("%s experienced an error during analysis", error.getModuleDisplayName()), error.getThrowable()); //NON-NLS
        }
    }

    /**
     * Gets a snapshot of this jobs state and performance.
     *
     * @return An ingest job statistics object.
     */
    Snapshot getSnapshot(boolean getIngestTasksSnapshot) {
        /**
         * Determine whether file ingest is running at the time of this snapshot
         * and determine the earliest file ingest level pipeline start time, if
         * file ingest was started at all.
         */
        boolean fileIngestRunning = false;
        Date fileIngestStartTime = null;

        for (FileIngestPipeline pipeline : this.fileIngestPipelines) {
            if (pipeline.isRunning()) {
                fileIngestRunning = true;
            }
            Date pipelineStartTime = pipeline.getStartTime();
            if (null != pipelineStartTime && (null == fileIngestStartTime || pipelineStartTime.before(fileIngestStartTime))) {
                fileIngestStartTime = pipelineStartTime;
            }
        }

        long processedFilesCount = 0;
        long estimatedFilesToProcessCount = 0;
        long snapShotTime = new Date().getTime();
        IngestJobTasksSnapshot tasksSnapshot = null;

        if (getIngestTasksSnapshot) {
            synchronized (fileIngestProgressLock) {
                processedFilesCount = this.processedFiles;
                estimatedFilesToProcessCount = this.estimatedFilesToProcess;
                snapShotTime = new Date().getTime();
            }
            tasksSnapshot = IngestJobPipeline.taskScheduler.getTasksSnapshotForJob(id);

        }

        return new Snapshot(this.dataSource.getName(), id, createTime,
                getCurrentDataSourceIngestModule(), fileIngestRunning, fileIngestStartTime,
                cancelled, cancellationReason, cancelledDataSourceIngestModules,
                processedFilesCount, estimatedFilesToProcessCount, snapShotTime, tasksSnapshot);
    }
}
