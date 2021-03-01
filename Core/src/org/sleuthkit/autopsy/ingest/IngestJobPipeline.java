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
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.DataArtifact;

/**
 * A set of ingest module pipelines for an ingest job.
 */
final class IngestJobPipeline {

    private static String AUTOPSY_MODULE_PREFIX = "org.sleuthkit.autopsy";
    // to match something like: "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14"
    private static final Pattern JYTHON_REGEX = Pattern.compile("org\\.python\\.proxies\\.(.+?)\\$(.+?)(\\$[0-9]*)?$");
    private static final Logger logger = Logger.getLogger(IngestJobPipeline.class.getName());

    private final IngestJob job;
    private static final AtomicLong nextPipelineId = new AtomicLong(0L);
    private final long pipelineId;
    private final IngestJobSettings settings;
    private Content dataSource;
    private final IngestJob.Mode ingestMode;
    private final List<AbstractFile> files = new ArrayList<>();
    private final long createTime;

    /*
     * An ingest job pipeline runs in stages.
     */
    private static enum Stages {

        /*
         * Setting up for processing.
         */
        INITIALIZATION,
        /*
         * Running only file and data artifact ingest modules (used only for
         * streaming ingest).
         */
        FIRST_STAGE_FILES_ONLY,
        /*
         * Running high priority data source level ingest modules, file ingest
         * modules and data artifact ingest modules.
         */
        FIRST_STAGE_FILES_AND_DATASOURCE,
        /**
         * Running lower priority, usually long-running, data source level
         * ingest modules and data artifact ingest modules.
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
     * An ingest job pipeline has separate data source level ingest task
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
     * An ingest job pipeline has a collection of identical file ingest task
     * pipelines, one for each file ingest thread in the ingest manager. The
     * ingest threads take and return task pipelines using a blocking queue. A
     * fixed list of all of the task pipelines is used to allow the job pipeline
     * to cycle through the individual task pipelines to check their status and
     * their progress on their current tasks.
     */
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelinesQueue = new LinkedBlockingQueue<>();
    private final List<FileIngestPipeline> fileIngestPipelines = new ArrayList<>();

    /*
     * An ingest job pipeline has a single data aritfact task pipeline.
     */
    private DataArtifactIngestPipeline artifactIngestPipeline;

    /**
     * An ingest job pipeline supports cancellation of either the currently
     * running data source level ingest module or the entire ingest job.
     * Cancellation works by setting flags that are checked by the ingest task
     * pipelines every time they transition from from one module to another.
     * Modules are also expected to check these flags and stop processing if
     * they are set. This means that there can be a variable length delay
     * between a cancellation request and its fulfillment.
     */
    private volatile boolean currentDataSourceIngestModuleCancelled;
    private final List<String> cancelledDataSourceIngestModules = new CopyOnWriteArrayList<>();
    private volatile boolean cancelled;
    private volatile IngestJob.CancellationReason cancellationReason = IngestJob.CancellationReason.NOT_CANCELLED;

    /**
     * An ingest job pipeline interacts with the ingest task scheduler to
     * determine whether or not there are ingest tasks still to be executed for
     * an ingest job and to schedule additional tasks submitted by ingest
     * modules. For example, a file carving module can add carved files to an
     * ingest job and many modules will add data artifacts to an ingest job.
     */
    private static final IngestTasksScheduler taskScheduler = IngestTasksScheduler.getInstance();

    /**
     * If running in a GUI, an ingest job pipeline report progress and allows a
     * user to cancel a data source level ingest module or the entire ingest job
     * using progress bars in the lower right hand corner of the main
     * application window. This is one of a handful of things that currently
     * couples the ingest code to the presentation layer.
     */
    private final boolean doUI;
    private final Object dataSourceIngestProgressLock = new Object();
    private ProgressHandle dataSourceIngestProgress;
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
     * Constructs a set of ingest module pipelines for an ingest job.
     *
     * @param job        The ingest job.
     * @param dataSource The data source for the ingest job.
     * @param settings   The ingest settings for the ingest job.
     */
    IngestJobPipeline(IngestJob job, Content dataSource, IngestJobSettings settings) {
        this(job, dataSource, Collections.emptyList(), settings);
    }

    /**
     * Constructs a set of ingest module pipelines for an ingest job that
     * process a subset of the files in the job's data source.
     *
     * @param job        The ingest job.
     * @param dataSource The data source for the ingest job.
     * @param files      The subset of the files for the data source. If the
     *                   list is empty, all of the files in the data source are
     *                   processed.
     * @param settings   The ingest settings for the ingest job.
     */
    IngestJobPipeline(IngestJob job, Content dataSource, List<AbstractFile> files, IngestJobSettings settings) {
        this.job = job;
        this.dataSource = dataSource;
        this.settings = settings;
        this.files.addAll(files);
        pipelineId = IngestJobPipeline.nextPipelineId.getAndIncrement();
        ingestMode = job.getIngestMode();
        doUI = RuntimeProperties.runningWithGUI();
        createTime = new Date().getTime();
        stage = Stages.INITIALIZATION;
        currentFileIngestModule = "";
        currentFileIngestTask = "";
        createIngestTaskPipelines();
    }

    /**
     * Adds ingest module templates to an output list with core Autopsy modules
     * first and third party modules next.
     *
     * @param orderedModules The list to populate.
     * @param javaModules    The input ingest module templates for modules
     *                       implemented using Java.
     * @param jythonModules  The input ingest module templates for modules
     *                       implemented using Jython.
     */
    private static void addOrdered(final List<IngestModuleTemplate> orderedModules, final Map<String, IngestModuleTemplate> javaModules, final Map<String, IngestModuleTemplate> jythonModules) {
        final List<IngestModuleTemplate> autopsyModules = new ArrayList<>();
        final List<IngestModuleTemplate> thirdPartyModules = new ArrayList<>();
        Stream.concat(javaModules.entrySet().stream(), jythonModules.entrySet().stream()).forEach((templateEntry) -> {
            if (templateEntry.getKey().startsWith(AUTOPSY_MODULE_PREFIX)) {
                autopsyModules.add(templateEntry.getValue());
            } else {
                thirdPartyModules.add(templateEntry.getValue());
            }
        });
        orderedModules.addAll(autopsyModules);
        orderedModules.addAll(thirdPartyModules);
    }

    /**
     * Takes a Jython proxy class name like
     * "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14"
     * and returns "GPX_Parser_Module.GPXParserFileIngestModuleFactory."
     *
     * @param className The class name.
     *
     * @return The jython name or null if not in jython package.
     */
    private static String getModuleNameFromJythonClassName(String className) {
        Matcher m = JYTHON_REGEX.matcher(className);
        if (m.find()) {
            return String.format("%s.%s", m.group(1), m.group(2));
        } else {
            return null;
        }
    }

    /**
     * Adds an ingest module template to one of two mappings of ingest module
     * factory class names to module templates. One mapping is for ingest
     * modules imnplemented using Java and the other is for ingest modules
     * implemented using Jython.
     *
     * @param mapping       Mapping for Java ingest module templates.
     * @param jythonMapping Mapping for Jython ingest module templates.
     * @param template      The ingest module template.
     */
    private static void addModule(Map<String, IngestModuleTemplate> mapping, Map<String, IngestModuleTemplate> jythonMapping, IngestModuleTemplate template) {
        String className = template.getModuleFactory().getClass().getCanonicalName();
        String jythonName = getModuleNameFromJythonClassName(className);
        if (jythonName != null) {
            jythonMapping.put(jythonName, template);
        } else {
            mapping.put(className, template);
        }
    }

    /**
     * Creates the ingest task pipelines for this ingest job pipeline.
     */
    private void createIngestTaskPipelines() {
        /*
         * Get the complete set of ingest module templates from the ingest job
         * settings. An ingest module template combines an ingest module factory
         * with job level ingest module settings to support the creation of any
         * number of fully configured instances of a given ingest module.
         */
        List<IngestModuleTemplate> enabledIngestModuleTemplates = this.settings.getEnabledIngestModuleTemplates();

        /**
         * Make one mapping of ingest module factory class names to ingest
         * module templates for each type of ingest task pipeline to be created.
         * These mappings are used to go from an ingest module factory class
         * name in the pipeline configuration file to the corresponding ingest
         * module template.
         */
        Map<String, IngestModuleTemplate> unorderedDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> unorderedJythonDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> unorderedFileModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> unorderedJythonFileModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> unorderedArtifactModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> unorderedJythonArtifactModuleTemplates = new LinkedHashMap<>();
        for (IngestModuleTemplate template : enabledIngestModuleTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                addModule(unorderedDataSourceModuleTemplates, unorderedJythonDataSourceModuleTemplates, template);
                continue;
            }
            if (template.isFileIngestModuleTemplate()) {
                addModule(unorderedFileModuleTemplates, unorderedJythonFileModuleTemplates, template);
                continue;
            }
            if (template.isDataArtifactIngestModuleTemplate()) {
                addModule(unorderedArtifactModuleTemplates, unorderedJythonArtifactModuleTemplates, template);
            }
        }

        /**
         * Use the mappings and the entries read from the ingest pipelines
         * configuration file to create ordered lists of ingest module template
         * for each ingest task pipeline.
         */
        IngestPipelinesConfiguration pipelineConfigs = IngestPipelinesConfiguration.getInstance();
        List<IngestModuleTemplate> firstStageDataSourceModuleTemplates = IngestJobPipeline.getConfiguredIngestModuleTemplates(unorderedDataSourceModuleTemplates, unorderedJythonDataSourceModuleTemplates, pipelineConfigs.getStageOneDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> fileIngestModuleTemplates = IngestJobPipeline.getConfiguredIngestModuleTemplates(unorderedFileModuleTemplates, unorderedJythonFileModuleTemplates, pipelineConfigs.getFileIngestPipelineConfig());
        List<IngestModuleTemplate> secondStageDataSourceModuleTemplates = IngestJobPipeline.getConfiguredIngestModuleTemplates(unorderedDataSourceModuleTemplates, null, pipelineConfigs.getStageTwoDataSourceIngestPipelineConfig());

        /**
         * Add any module templates that were not specified in the pipeline
         * configuration file to the appropriate list. Note that no data
         * artifact ingest modules are currently specified in the file and that
         * core Autopsy modules are added first and third party modules are
         * added last.
         */
        addOrdered(firstStageDataSourceModuleTemplates, unorderedDataSourceModuleTemplates, unorderedJythonDataSourceModuleTemplates);
        addOrdered(fileIngestModuleTemplates, unorderedFileModuleTemplates, unorderedJythonFileModuleTemplates);
        addOrdered(fileIngestModuleTemplates, unorderedFileModuleTemplates, unorderedJythonFileModuleTemplates);
        List<IngestModuleTemplate> artifactModuleTemplates = IngestJobPipeline.getConfiguredIngestModuleTemplates(unorderedDataSourceModuleTemplates, null, pipelineConfigs.getStageTwoDataSourceIngestPipelineConfig());

        /**
         * Construct the ingest task pipelines from the ordered lists.
         */
        firstStageDataSourceIngestPipeline = new DataSourceIngestPipeline(this, firstStageDataSourceModuleTemplates);
        secondStageDataSourceIngestPipeline = new DataSourceIngestPipeline(this, secondStageDataSourceModuleTemplates);
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
        artifactIngestPipeline = new DataArtifactIngestPipeline(this, artifactModuleTemplates);

        /*
         * Add the ingest module templates to the "master list" and record the
         * final composition of the ingest task pipelines in the case database.
         */
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            addIngestModules(firstStageDataSourceModuleTemplates, IngestModuleType.DATA_SOURCE_LEVEL, skCase);
            addIngestModules(fileIngestModuleTemplates, IngestModuleType.FILE_LEVEL, skCase);
            addIngestModules(secondStageDataSourceModuleTemplates, IngestModuleType.DATA_SOURCE_LEVEL, skCase);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logErrorMessage(Level.WARNING, "Failed to add ingest modules listing to case database", ex);
        }
    }

    /**
     * Adds a list of ingest module templates for an ingest task pipeline to the
     * "master list" and records the final composition of the pipeline in the
     * case database.
     *
     * @param templates A list of ingest module templates.
     * @param type      The type of the ingest module templates.
     * @param skCase    The case database.
     *
     * @throws TskCoreException
     */
    private void addIngestModules(List<IngestModuleTemplate> templates, IngestModuleType type, SleuthkitCase skCase) throws TskCoreException {
        for (IngestModuleTemplate module : templates) {
            ingestModules.add(skCase.addIngestModule(module.getModuleName(), FactoryClassNameNormalizer.normalize(module.getModuleFactory().getClass().getCanonicalName()), type, module.getModuleFactory().getModuleVersionNumber()));
        }
    }

    /**
     * Uses an input collection of ingest module templates and a pipeline
     * configuration, i.e., an ordered list of ingest module factory class
     * names, to create an ordered output list of ingest module templates for an
     * ingest task pipeline. The ingest module templates are removed from the
     * input collection as they are added to the output collection.
     *
     * @param javaIngestModuleTemplates   A mapping of Java ingest module
     *                                    factory class names to ingest module
     *                                    templates.
     * @param jythonIngestModuleTemplates A mapping of Jython ingest module
     *                                    factory proxy class names to ingest
     *                                    module templates.
     * @param pipelineConfig              An ordered list of ingest module
     *                                    factory class names representing an
     *                                    ingest pipeline, read form the
     *                                    pipeline configuration file.
     *
     * @return An ordered list of ingest module templates, i.e., an
     *         uninstantiated pipeline.
     */
    private static List<IngestModuleTemplate> getConfiguredIngestModuleTemplates(Map<String, IngestModuleTemplate> javaIngestModuleTemplates, Map<String, IngestModuleTemplate> jythonIngestModuleTemplates, List<String> pipelineConfig) {
        List<IngestModuleTemplate> templates = new ArrayList<>();
        for (String moduleClassName : pipelineConfig) {
            if (javaIngestModuleTemplates != null && javaIngestModuleTemplates.containsKey(moduleClassName)) {
                templates.add(javaIngestModuleTemplates.remove(moduleClassName));
            } else if (jythonIngestModuleTemplates != null && jythonIngestModuleTemplates.containsKey(moduleClassName)) {
                templates.add(jythonIngestModuleTemplates.remove(moduleClassName));
            }
        }
        return templates;
    }

    /**
     * Gets the ID of this ingest job pipeline.
     *
     * @return The job ID.
     */
    long getId() {
        return this.pipelineId;
    }

    /**
     * Gets the ingest execution context name.
     *
     * @return The context name.
     */
    String getExecutionContext() {
        return this.settings.getExecutionContext();
    }

    /**
     * Getss the data source to be ingested by this ingest job pipeline.
     *
     * @return A Content object representing the data source.
     */
    Content getDataSource() {
        return this.dataSource;
    }

    /**
     * Queries whether or not unallocated space should be processed by this
     * ingest job pipeline.
     *
     * @return True or false.
     */
    boolean shouldProcessUnallocatedSpace() {
        return this.settings.getProcessUnallocatedSpace();
    }

    /**
     * Gets the file ingest filter for this ingest job pipeline from the ingest
     * job settings.
     *
     * @return The filter.
     */
    FilesSet getFileIngestFilter() {
        return this.settings.getFileFilter();
    }

    /**
     * Checks to see if this ingest job pipeline has at least one ingest task
     * pipeline.
     *
     * @return True or false.
     */
    boolean hasIngestPipeline() {
        return hasFirstStageDataSourceIngestPipeline()
                || hasFileIngestPipeline()
                || hasSecondStageDataSourceIngestPipeline()
                || hasDataArtifactPipeline();
    }

    /**
     * Checks to see if this ingest job pipeline has a first stage data source
     * level ingest pipeline.
     *
     * @return True or false.
     */
    private boolean hasFirstStageDataSourceIngestPipeline() {
        return (this.firstStageDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if this ingest job pipeline has a second stage data source
     * level ingest task pipeline.
     *
     * @return True or false.
     */
    private boolean hasSecondStageDataSourceIngestPipeline() {
        return (this.secondStageDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if this ingest job pipeline has a file level ingest task
     * pipeline.
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
     * Checks to see if this ingest job pipeline has a data artifact ingest task
     * pipeline.
     *
     * @return True or false.
     */
    private boolean hasDataArtifactPipeline() {
        return (artifactIngestPipeline.isEmpty() == false);
    }

    /**
     * Starts up this ingest job pipeline.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    List<IngestModuleError> startUp() {
        if (dataSource == null) {
            throw new IllegalStateException("Ingest started before setting data source");
        }
        List<IngestModuleError> errors = startUpIngestTaskPipelines();
        if (errors.isEmpty()) {
            try {
                ingestJob = Case.getCurrentCaseThrows().getSleuthkitCase().addIngestJob(dataSource, NetworkUtils.getLocalHostName(), ingestModules, new Date(this.createTime), new Date(0), IngestJobStatusType.STARTED, "");
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logErrorMessage(Level.WARNING, "Failed to add ingest job info to case database", ex); //NON-NLS
            }

            startArtifactProcessing();
            if (hasFirstStageDataSourceIngestPipeline() || hasFileIngestPipeline()) {
                if (ingestMode == IngestJob.Mode.BATCH) {
                    logInfoMessage("Starting first stage analysis in batch mode"); //NON-NLS
                    startFirstStage();
                } else {
                    logInfoMessage("Starting first stage analysis in streaming mode"); //NON-NLS
                    startFileIngestStreaming();
                }
            } else if (hasSecondStageDataSourceIngestPipeline()) {
                logInfoMessage("Starting second stage analysis"); //NON-NLS
                startSecondStage();
            }
        }
        return errors;
    }

    /**
     * Starts up each of the ingest task pipelines in this ingest job pipeline.
     * All of the pipelines are started so that any and all start up errors can
     * be returned to the caller. It is important to capture all of the errors
     * since the ingest job will be automatically cancelled and the errors will
     * be reported to the user so the issues can be addressed or the modules
     * that can't start up can be disabled before the ingest job is attempted
     * again.
     *
     * @return A list of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestTaskPipelines() {
        List<IngestModuleError> errors = new ArrayList<>();
        startUpIngestTaskPipeline(firstStageDataSourceIngestPipeline);
        startUpIngestTaskPipeline(secondStageDataSourceIngestPipeline);
        for (FileIngestPipeline pipeline : fileIngestPipelines) {
            List<IngestModuleError> filePipelineErrors = startUpIngestTaskPipeline(pipeline);
            if (!filePipelineErrors.isEmpty()) {
                /*
                 * If one pipeline copy can't start up, assume that none of them
                 * will be able to start up for the same reasons.
                 */
                errors.addAll(filePipelineErrors);
                break;
            }
        }
        errors.addAll(this.artifactIngestPipeline.startUp());
        return errors;
    }

    /**
     * Starts up an ingest task pipeline. If there are any start up errors, the
     * piepline is imediately shut down.
     *
     * @param pipeline The ingest task pipeline to start up.
     *
     * @return A list of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestTaskPipeline(IngestTaskPipeline<?> pipeline) {
        List<IngestModuleError> startUpErrors = pipeline.startUp();
        if (!startUpErrors.isEmpty()) {
            List<IngestModuleError> shutDownErrors = pipeline.shutDown();
            if (!shutDownErrors.isEmpty()) {
                /*
                 * The start up errors will ultimately be reported to the user
                 * for possible remedy, but the shut down errors are logged
                 * here.
                 */
                logIngestModuleErrors(shutDownErrors);
            }
        }
        return startUpErrors;
    }

    /**
     * Schedules data artifact tasks for all of the existing data artifacts for
     * the ingest job's data source, if any. Note that there is a possiblity,
     * just as in with the other item types, that a user may process artifacts
     * more than once if the same ingest job is run more than once.
     */
    private void startArtifactProcessing() {
        Blackboard blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard();
        try {
            List<DataArtifact> artifacts = blackboard.getDataArtifacts(dataSource);
            taskScheduler.scheduleDataArtifactIngestTasks(this, artifacts);
        } catch (TskCoreException ex) {
            // RJCTODO
        }
    }

    /**
     * Starts the first stage of the ingest job.
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

        /*
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
        this.job.ingestJobPipelineFinished(this);
    }

    /**
     * Executes an ingest task for an ingest job by passing the item associated
     * with the task through the appropriate pipeline of ingest modules.
     *
     * @param task The ingest task.
     */
    void execute(IngestTask task) {
        /*
         * The following "switch on actual type" eliminates code duplication in
         * the IngestTask hierarchy. Future work may or may not be able to
         * eliminate the switch.
         */
        if (task instanceof DataSourceIngestTask) {
            executeDataSourceIngestTask((DataSourceIngestTask) task);
        } else if (task instanceof FileIngestTask) {
            executeFileIngestTask((FileIngestTask) task);
        } else if (task instanceof DataArtifactIngestTask) {
            executeDataArtifactIngestTask((DataArtifactIngestTask) task);
        }
    }

    /**
     * Passes a data source for this job through the currently active data
     * source level ingest pipeline.
     *
     * @param task A data source ingest task wrapping the data source.
     */
    private void executeDataSourceIngestTask(DataSourceIngestTask task) {
        try {
            synchronized (this.dataSourceIngestPipelineLock) {
                if (!this.isCancelled() && !this.currentDataSourceIngestPipeline.isEmpty()) {
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(this.currentDataSourceIngestPipeline.performTask(task));
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
     * Passes a file from the data source for this job through the file ingest
     * pipeline.
     *
     * @param task A file ingest task wrapping the file.
     */
    private void executeFileIngestTask(FileIngestTask task) {
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
                    errors.addAll(pipeline.performTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors, file);
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
        } catch (InterruptedException ex) {
            // RJCTODO
        } finally {
            IngestJobPipeline.taskScheduler.notifyTaskCompleted(task);
            this.checkForStageCompleted();
        }
    }

    /**
     * Passes a data artifact from the data source for this job through the data
     * artifact ingest pipeline.
     *
     * @param task A data artifact ingest task wrapping the file.
     */
    private void executeDataArtifactIngestTask(DataArtifactIngestTask task) {
        // RJCTODO
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
     * RJCTODO
     *
     * @param artifacts
     */
    void addDataArtifacts(List<DataArtifact> artifacts) {
        // RJCTODO
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
    DataSourceIngestPipeline.DataSourcePipelineModule getCurrentDataSourceIngestModule() {
        if (null != currentDataSourceIngestPipeline) {
            return (DataSourceIngestPipeline.DataSourcePipelineModule) currentDataSourceIngestPipeline.getCurrentlyRunningModule();
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
        currentFileIngestTask = taskName;
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
        logger.log(Level.INFO, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id = %d)", message, this.dataSource.getName(), this.dataSource.getId(), pipelineId, ingestJob.getIngestJobId())); //NON-NLS        
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
        logger.log(level, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id = %d)", message, this.dataSource.getName(), this.dataSource.getId(), pipelineId, ingestJob.getIngestJobId()), throwable); //NON-NLS
    }

    /**
     * Writes an error message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param level   The logging level for the message.
     * @param message The message.
     */
    private void logErrorMessage(Level level, String message) {
        logger.log(level, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id %d)", message, this.dataSource.getName(), this.dataSource.getId(), pipelineId, ingestJob.getIngestJobId())); //NON-NLS
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
     * Write ingest module errors to the log.
     *
     * @param errors The errors.
     * @param file   AbstractFile that caused the errors.
     */
    private void logIngestModuleErrors(List<IngestModuleError> errors, AbstractFile file) {
        for (IngestModuleError error : errors) {
            logErrorMessage(Level.SEVERE, String.format("%s experienced an error during analysis while processing file %s, object ID %d", error.getModuleDisplayName(), file.getName(), file.getId()), error.getThrowable()); //NON-NLS
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
            tasksSnapshot = IngestJobPipeline.taskScheduler.getTasksSnapshotForJob(pipelineId);

        }

        return new Snapshot(this.dataSource.getName(), pipelineId, createTime,
                getCurrentDataSourceIngestModule(), fileIngestRunning, fileIngestStartTime,
                cancelled, cancellationReason, cancelledDataSourceIngestModules,
                processedFilesCount, estimatedFilesToProcessCount, snapShotTime, tasksSnapshot);
    }

}
