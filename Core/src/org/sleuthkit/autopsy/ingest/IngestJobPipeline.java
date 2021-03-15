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
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DataSource;

/**
 * An ingest pipeline that works with the ingest tasks scheduler to coordinate
 * the creation, scheduling, and execution of ingest tasks for one of the data
 * sources in an ingest job. An ingest job pipeline has one-to-many child ingest
 * task pipelines. An ingest task pipeline is a sequence of ingest modules of a
 * given type that have been enabled and configured as part of the settings for
 * the ingest job.
 */
final class IngestJobPipeline {

    /*
     * The class names of the proxy classes Jython generates for Python classes
     * look something like:
     * "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14"
     */
    private static final Pattern JYTHON_REGEX = Pattern.compile("org\\.python\\.proxies\\.(.+?)\\$(.+?)(\\$[0-9]*)?$");
    private static final String AUTOPSY_MODULE_PREFIX = "org.sleuthkit.autopsy";

    private static final Logger logger = Logger.getLogger(IngestJobPipeline.class.getName());
    private final IngestJob job;
    private final IngestJobSettings settings;
    private DataSource dataSource;
    private final List<AbstractFile> files;
    private final long createTime;

    /*
     * An ingest pipeline interacts with the ingest task scheduler to schedule
     * initial ingest tasks, determine whether or not there are ingest tasks
     * still to be executed, and to schedule additional tasks submitted by
     * ingest modules. For example, a file carving module can add carved files
     * to an ingest job and many modules will add data artifacts to an ingest
     * job.
     *
     * The pipeline ID is used to associate the pipeline with its tasks. The
     * ingest job ID cannot be used for this because an ingest job may have more
     * than one pipeline (one pipeline per data source).
     */
    private static final IngestTasksScheduler taskScheduler = IngestTasksScheduler.getInstance();
    private static final AtomicLong nextPipelineId = new AtomicLong(0L);
    private final long pipelineId;

    /*
     * An ingest pipeline runs its child ingest task pipelines in stages.
     */
    private static enum Stages {

        /*
         * The ingest pipeline is setting up its child ingest task pipelines.
         */
        INITIALIZATION,
        /*
         * The ingest pipeline is only running its file and result ingest task
         * pipelines because the data source for the job has not been supplied
         * to it yet. This stage is only used for streaming ingest.
         */
        FIRST_STAGE_FILE_AND_RESULTS_TASKS_ONLY,
        /*
         * The ingest pipeline is running its high priority data source level
         * ingest task pipeline, its file ingest task pipelines, and its result
         * ingest task pipelines.
         */
        FIRST_STAGE_ALL_TASKS,
        /**
         * The ingest pipeline is running its lower priority data source level
         * ingest task pipeline and its result task pipelines.
         */
        SECOND_STAGE,
        /**
         * The ingest pipeline is shutting down its child ingest task pipelines.
         */
        FINALIZATION
    };
    private volatile Stages stage = IngestJobPipeline.Stages.INITIALIZATION;
    private final Object stageTransitionLock = new Object();

    /*
     * An ingest pipeline has at most a single data artifact ingest task
     * pipeline.
     *
     * The pipeline may be empty, depending on which modules are enabled in the
     * ingest job settings.
     */
    private DataArtifactIngestPipeline artifactIngestPipeline;

    /**
     * An ingest pipeline has separate data source level ingest task pipelines
     * for the first and second processing stages. Longer running, lower
     * priority modules belong in the second stage pipeline, although this
     * cannot be enforced.
     *
     * Either or both pipelines may be empty, depending on which modules are
     * enabled in the ingest job settings.
     */
    private final Object dataSourceIngestPipelineLock = new Object();
    private DataSourceIngestPipeline firstStageDataSourceIngestPipeline;
    private DataSourceIngestPipeline secondStageDataSourceIngestPipeline;
    private DataSourceIngestPipeline currentDataSourceIngestPipeline;

    /**
     * An ingest pipeline has a collection of identical file ingest task
     * pipelines, one for each file ingest thread in the ingest manager.
     *
     * The ingest threads take and return task pipelines using a blocking queue.
     * Additionally, a fixed list of all of the file pipelines is used to allow
     * cycling through each of the individual task pipelines to check their
     * status.
     *
     * The pipelines may be empty, depending on which modules are enabled in the
     * ingest job settings.
     */
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelinesQueue = new LinkedBlockingQueue<>();
    private final List<FileIngestPipeline> fileIngestPipelines = new ArrayList<>();

    /**
     * An ingest pipeline supports cancellation of either the currently running
     * data source level ingest pipeline or all of its child pipelines.
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
     * If running in a GUI, an ingest pipeline reports progress and allows a
     * user to cancel either an individual data source level ingest module or
     * all of its ingest tasks using progress bars in the lower right hand
     * corner of the main application window. There is also support for ingest
     * process snapshots and recording ingest job details in the case database.
     */
    private final boolean doUI;
    private final Object resultsIngestProgressLock = new Object();
    private ProgressHandle resultsIngestProgress;
    private final Object dataSourceIngestProgressLock = new Object();
    private ProgressHandle dataSourceIngestProgress;
    private final Object fileIngestProgressLock = new Object();
    private final List<String> filesInProgress = new ArrayList<>();
    private long estimatedFilesToProcess;
    private long processedFiles;
    private ProgressHandle fileIngestProgress;
    private volatile IngestJobInfo ingestJobInfo;

    /**
     * Constructs an ingest pipeline that works with the ingest tasks scheduler
     * to coordinate the creation, scheduling, and execution of ingest tasks for
     * one of the data sources in an ingest job. An ingest job pipeline has
     * one-to-many child ingest task pipelines. An ingest task pipeline is a
     * sequence of ingest modules of a given type that have been enabled and
     * configured as part of the settings for the ingest job.
     *
     * @param job        The ingest job.
     * @param dataSource The data source.
     * @param settings   The ingest settings for the ingest job.
     */
    IngestJobPipeline(IngestJob job, Content dataSource, IngestJobSettings settings) {
        this(job, dataSource, Collections.emptyList(), settings);
    }

    /**
     * Constructs an ingest pipeline that works with the ingest tasks scheduler
     * to coordinate the creation, scheduling, and execution of ingest tasks for
     * one of the data sources in an ingest job. An ingest job pipeline has
     * one-to-many child ingest task pipelines. An ingest task pipeline is a
     * sequence of ingest modules of a given type that have been enabled and
     * configured as part of the settings for the ingest job.
     *
     * @param job        The ingest job.
     * @param dataSource The data source for the ingest job.
     * @param files      The subset of the files for the data source. If the
     *                   list is empty, all of the files in the data source are
     *                   processed.
     * @param settings   The ingest settings for the ingest job.
     */
    IngestJobPipeline(IngestJob job, Content dataSource, List<AbstractFile> files, IngestJobSettings settings) {
        if (!(dataSource instanceof DataSource)) {
            throw new IllegalArgumentException("Passed dataSource that does not implement the DataSource interface"); //NON-NLS
        }
        this.job = job;
        this.settings = settings;
        this.dataSource = (DataSource) dataSource;
        this.files = new ArrayList<>();
        this.files.addAll(files);
        pipelineId = IngestJobPipeline.nextPipelineId.getAndIncrement();
        doUI = RuntimeProperties.runningWithGUI();
        createTime = new Date().getTime();
        stage = Stages.INITIALIZATION;
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
    private static void completePipeline(final List<IngestModuleTemplate> orderedModules, final Map<String, IngestModuleTemplate> javaModules, final Map<String, IngestModuleTemplate> jythonModules) {
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
     * Extracts a module class name from a Jython module proxy class name. For
     * example, a Jython class name such
     * "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14"
     * will be parsed to return
     * "GPX_Parser_Module.GPXParserFileIngestModuleFactory."
     *
     * @param className The class name.
     *
     * @return The jython name or null if not in jython package.
     */
    private static String getModuleNameFromJythonClassName(String className) {
        Matcher m = JYTHON_REGEX.matcher(className);
        if (m.find()) {
            return String.format("%s.%s", m.group(1), m.group(2)); //NON-NLS
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
    private static void addIngestModuleTermplateToMaps(Map<String, IngestModuleTemplate> mapping, Map<String, IngestModuleTemplate> jythonMapping, IngestModuleTemplate template) {
        String className = template.getModuleFactory().getClass().getCanonicalName();
        String jythonName = getModuleNameFromJythonClassName(className);
        if (jythonName != null) {
            jythonMapping.put(jythonName, template);
        } else {
            mapping.put(className, template);
        }
    }

    /**
     * Creates the child ingest task pipelines for this ingest pipeline.
     */
    private void createIngestTaskPipelines() {
        /*
         * Get the enabled ingest module templates from the ingest job settings.
         * An ingest module template combines an ingest module factory with job
         * level ingest module settings to support the creation of any number of
         * fully configured instances of a given ingest module.
         *
         * Note that an ingest module factory may be able to create multiple
         * tpyes of ingest modules.
         */
        List<IngestModuleTemplate> enabledTemplates = settings.getEnabledIngestModuleTemplates();

        /**
         * Separate the ingest module templates into buckets based on the module
         * types the ingest module factory can create. A template may go into
         * more than one bucket. The buckets are actually maps of ingest module
         * factory class names to ingest module templates. The maps are used to
         * go from an ingest module factory class name read from the pipeline
         * configuration file to the corresponding ingest module template.
         *
         * There are also two maps for each bucket. One map is for Java modules
         * and the other one is for Jython modules. The templates are separated
         * this way so that Java modules that are not in the pipeline config
         * file can be placed before Jython modules.
         */
        Map<String, IngestModuleTemplate> javaDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> javaFileModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonFileModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> javaArtifactModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonArtifactModuleTemplates = new LinkedHashMap<>();
        for (IngestModuleTemplate template : enabledTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                addIngestModuleTermplateToMaps(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, template);
            }
            if (template.isFileIngestModuleTemplate()) {
                addIngestModuleTermplateToMaps(javaFileModuleTemplates, jythonFileModuleTemplates, template);
            }
            if (template.isDataArtifactIngestModuleTemplate()) {
                addIngestModuleTermplateToMaps(javaArtifactModuleTemplates, jythonArtifactModuleTemplates, template);
            }
        }

        /**
         * Take the module templates that have pipeline configuration file
         * entries out of the buckets and put them in lists representing ingest
         * task pipelines, in the order prescribed by the file.
         *
         * Note that the pipeline configuration file currently only supports
         * specifying data source level and file ingest module pipeline layouts.
         */
        IngestPipelinesConfiguration pipelineConfig = IngestPipelinesConfiguration.getInstance();
        List<IngestModuleTemplate> firstStageDataSourceModuleTemplates = createPipelineFromConfigFile(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, pipelineConfig.getStageOneDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> secondStageDataSourceModuleTemplates = createPipelineFromConfigFile(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, pipelineConfig.getStageTwoDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> fileIngestModuleTemplates = createPipelineFromConfigFile(javaFileModuleTemplates, jythonFileModuleTemplates, pipelineConfig.getFileIngestPipelineConfig());
        List<IngestModuleTemplate> artifactModuleTemplates = new ArrayList<>();

        /**
         * Add any module templates remaining in the buckets to the appropriate
         * ingest task pipeline. Note that any data source level ingest modules
         * that were not listed in the configuration file are added to the first
         * stage data source pipeline, Java modules are added before Jython
         * modules, and Core Autopsy modules are added before third party
         * modules.
         */
        completePipeline(firstStageDataSourceModuleTemplates, javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates);
        completePipeline(fileIngestModuleTemplates, javaFileModuleTemplates, jythonFileModuleTemplates);
        completePipeline(artifactModuleTemplates, javaArtifactModuleTemplates, jythonArtifactModuleTemplates);

        /**
         * Construct the actual ingest task pipelines from the ordered lists.
         */
        firstStageDataSourceIngestPipeline = new DataSourceIngestPipeline(this, firstStageDataSourceModuleTemplates);
        secondStageDataSourceIngestPipeline = new DataSourceIngestPipeline(this, secondStageDataSourceModuleTemplates);
        try {
            int numberOfFileIngestThreads = IngestManager.getInstance().getNumberOfFileIngestThreads();
            for (int i = 0; i < numberOfFileIngestThreads; ++i) {
                FileIngestPipeline pipeline = new FileIngestPipeline(this, fileIngestModuleTemplates);
                fileIngestPipelinesQueue.put(pipeline);
                fileIngestPipelines.add(pipeline);
            }
        } catch (InterruptedException ex) {
            /*
             * RC: This is not incorrect. If this thread is interrupted, the
             * pipeline is incomplete and should not be used. We are currently relying on  
             */
            Thread.currentThread().interrupt();
        }
        artifactIngestPipeline = new DataArtifactIngestPipeline(this, artifactModuleTemplates);
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
    private static List<IngestModuleTemplate> createPipelineFromConfigFile(Map<String, IngestModuleTemplate> javaIngestModuleTemplates, Map<String, IngestModuleTemplate> jythonIngestModuleTemplates, List<String> pipelineConfig) {
        List<IngestModuleTemplate> templates = new ArrayList<>();
        for (String moduleClassName : pipelineConfig) {
            if (javaIngestModuleTemplates.containsKey(moduleClassName)) {
                templates.add(javaIngestModuleTemplates.remove(moduleClassName));
            } else if (jythonIngestModuleTemplates.containsKey(moduleClassName)) {
                templates.add(jythonIngestModuleTemplates.remove(moduleClassName));
            }
        }
        return templates;
    }

    /**
     * Gets the ID of this ingest pipeline.
     *
     * @return The ID.
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
     * Gets the data source to be ingested by this ingest pipeline.
     *
     * @return The data source.
     */
    DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Queries whether or not unallocated space should be processed by this
     * ingest pipeline.
     *
     * @return True or false.
     */
    boolean shouldProcessUnallocatedSpace() {
        return this.settings.getProcessUnallocatedSpace();
    }

    /**
     * Gets the file ingest filter for this ingest pipeline.
     *
     * @return The filter.
     */
    FilesSet getFileIngestFilter() {
        return this.settings.getFileFilter();
    }

    /**
     * Checks to see if this ingest pipeline has at least one ingest module to
     * run.
     *
     * @return True or false.
     */
    boolean hasIngestModules() {
        return hasArtifactIngestModules()
                || hasFileIngestModules()
                || hasFirstStageDataSourceIngestModules()
                || hasSecondStageDataSourceIngestModules();
    }

    /**
     * Checks to see if this ingest pipeline has at least one data artifact
     * ingest module.
     *
     * @return True or false.
     */
    private boolean hasArtifactIngestModules() {
        return (artifactIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if this ingest pipeline has at least one first stage data
     * source level ingest modules.
     *
     * @return True or false.
     */
    private boolean hasFirstStageDataSourceIngestModules() {
        return (firstStageDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if this ingest pipeline has at least one second stage data
     * source level ingest modules.
     *
     * @return True or false.
     */
    private boolean hasSecondStageDataSourceIngestModules() {
        return (secondStageDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if this ingest pipeline has at least one file ingest
     * module.
     *
     * @return True or false.
     */
    private boolean hasFileIngestModules() {
        if (!fileIngestPipelines.isEmpty()) {
            /*
             * Note that the file ingest task pipelines are identical.
             */
            return !fileIngestPipelines.get(0).isEmpty();
        }
        return false;
    }

    /**
     * Starts up this ingest pipeline.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    List<IngestModuleError> startUp() {
        if (dataSource == null) {
            throw new IllegalStateException("Ingest started before setting data source"); //NON-NLS
        }

        List<IngestModuleError> errors = startUpIngestTaskPipelines();
        if (errors.isEmpty()) {
            recordIngestJobStartUpInfo();
            if (hasFirstStageDataSourceIngestModules() || hasFileIngestModules()) {
                if (job.getIngestMode() == IngestJob.Mode.BATCH) {
                    startFirstStageInBatchMode();
                } else {
                    startFirstStageInStreamingMode();
                }
            } else if (hasSecondStageDataSourceIngestModules()) {
                startSecondStage();
            }
        }
        return errors;
    }

    /**
     * Writes start up data about the ingest job into the case database. The
     * case database returns an object that is retained to allow the additon of
     * a completion time when the ingest job is finished.
     */
    void recordIngestJobStartUpInfo() {
        try {
            SleuthkitCase caseDb = Case.getCurrentCase().getSleuthkitCase();
            List<IngestModuleInfo> ingestModuleInfoList = new ArrayList<>();
            for (IngestModuleTemplate module : settings.getEnabledIngestModuleTemplates()) {
                IngestModuleType moduleType = getIngestModuleTemplateType(module);
                IngestModuleInfo moduleInfo = caseDb.addIngestModule(module.getModuleName(), FactoryClassNameNormalizer.normalize(module.getModuleFactory().getClass().getCanonicalName()), moduleType, module.getModuleFactory().getModuleVersionNumber());
                ingestModuleInfoList.add(moduleInfo);
            }
            ingestJobInfo = caseDb.addIngestJob(dataSource, NetworkUtils.getLocalHostName(), ingestModuleInfoList, new Date(this.createTime), new Date(0), IngestJobStatusType.STARTED, "");
        } catch (TskCoreException ex) {
            logErrorMessage(Level.SEVERE, "Failed to add ingest job info to case database", ex); //NON-NLS
        }
    }

    /**
     * Determines the type of ingest modules a given ingest module template
     * supports.
     *
     * @param moduleTemplate The ingest module template.
     *
     * @return The ingest module type. may be IngestModuleType.MULTIPLE.
     */
    private IngestModuleType getIngestModuleTemplateType(IngestModuleTemplate moduleTemplate) {
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
     * Starts up each of the child ingest task pipelines in this ingest
     * pipeline.
     *
     * Note that all of the child pipelines are started so that any and all
     * start up errors can be returned to the caller. It is important to capture
     * all of the errors, because the ingest job will be automatically cancelled
     * and the errors will be reported to the user so either the issues can be
     * addressed or the modules that can't start up can be disabled before the
     * ingest job is attempted again.
     *
     * @return A list of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestTaskPipelines() {
        List<IngestModuleError> errors = new ArrayList<>();
        errors.addAll(startUpIngestTaskPipeline(firstStageDataSourceIngestPipeline));
        errors.addAll(startUpIngestTaskPipeline(secondStageDataSourceIngestPipeline));
        for (FileIngestPipeline pipeline : fileIngestPipelines) {
            List<IngestModuleError> filePipelineErrors = startUpIngestTaskPipeline(pipeline);
            if (!filePipelineErrors.isEmpty()) {
                /*
                 * If one file pipeline copy can't start up, assume that none of
                 * them will be able to start up for the same reason.
                 */
                errors.addAll(filePipelineErrors);
                break;
            }
        }
        errors.addAll(startUpIngestTaskPipeline(artifactIngestPipeline));
        return errors;
    }

    /**
     * Starts up an ingest task pipeline. If there are any start up errors, the
     * pipeline is imediately shut down.
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
                logIngestModuleErrors(shutDownErrors);
            }
        }
        return startUpErrors;
    }

    /**
     * Schedules the first stage results, data source level, and file ingest
     * tasks for the data source.
     */
    private void startFirstStageInBatchMode() {
        logInfoMessage("Starting first stage analysis in batch mode"); //NON-NLS
        stage = Stages.FIRST_STAGE_ALL_TASKS;

        if (hasFileIngestModules()) {
            /*
             * The estimated number of files remaining to be processed is used
             * for ingest snapshots and for the file ingest progress bar.
             */
            synchronized (fileIngestProgressLock) {
                estimatedFilesToProcess = dataSource.accept(new GetFilesCountVisitor());
            }
        }

        /*
         * If running with a GUI, start ingest progress bars in the lower right
         * hand corner of the main application window.
         */
        if (doUI) {
            if (hasFirstStageDataSourceIngestModules()) {
                startDataSourceIngestProgressBar();
            }
            if (hasFileIngestModules()) {
                startFileIngestProgressBar();
            }
            if (hasArtifactIngestModules()) {
                startArtifactIngestProgressBar();
            }
        }

        /**
         * Make the first stage data source level ingest pipeline the current
         * data source level pipeline.
         */
        synchronized (dataSourceIngestPipelineLock) {
            currentDataSourceIngestPipeline = firstStageDataSourceIngestPipeline;
        }

        /**
         * Schedule the first stage ingest tasks.
         */
        if (hasArtifactIngestModules()) {
            /*
             * Create ingest tasks for data artifacts already in the case
             * database. Additional tasks will be created as other ingest
             * modules add data aritfacts.
             */
            taskScheduler.scheduleDataArtifactIngestTasks(this);
        }
        if (hasFirstStageDataSourceIngestModules() && hasFileIngestModules()) {
            taskScheduler.scheduleDataSourceAndFileIngestTasks(this);
        } else if (hasFirstStageDataSourceIngestModules()) {
            taskScheduler.scheduleDataSourceIngestTask(this);
        } else if (hasFileIngestModules() && !files.isEmpty()) {
            taskScheduler.scheduleFileIngestTasks(this, files);
            /**
             * No data source ingest task has been scheduled for this stage, it
             * is possible that no artifact tasks were scheduled, and it is also
             * possible that no file ingest tasks were scheduled when the task
             * scheduler applied the file ingest filter. In this special case in
             * which there are no ingest tasks to do, an ingest thread will
             * never get to check for completion of this stage of the job, so do
             * it now so there is not a do-nothing ingest job that lives
             * forever.
             */
            checkForStageCompleted();
        }
    }

    /**
     * Schedules the first stage results ingest tasks and prepares for streaming
     * file ingest (used for streaming ingest only). Does not schedule any file
     * tasks - those will come from calls to addStreamingIngestFiles().
     */
    private void startFirstStageInStreamingMode() {
        logInfoMessage("Starting first stage analysis in streaming mode"); //NON-NLS
        stage = Stages.FIRST_STAGE_FILE_AND_RESULTS_TASKS_ONLY;

        if (hasFileIngestModules()) {
            /*
             * The estimated number of files remaining to be processed is used
             * for ingest snapshots and for the file ingest progress bar.
             * However, for streaming ingest, it cannot be calculated until the
             * data source is added to this pipeline. Start with zero to signal
             * an unknown value.
             */
            synchronized (fileIngestProgressLock) {
                estimatedFilesToProcess = 0;
            }
        }

        /*
         * If running with a GUI, start ingest progress bars in the lower right
         * hand corner of the main application window.
         */
        if (doUI) {
            if (hasArtifactIngestModules()) {
                startArtifactIngestProgressBar();
            }
            if (hasFileIngestModules()) {
                /*
                 * Note that because estimated files remaining to process has
                 * been set to zero, the progress bar will start in the
                 * "indeterminate" state.
                 */
                startFileIngestProgressBar();
            }
        }

        /**
         * Schedule the first stage ingest tasks for streaming mode. For
         * streaming ingest, file ingest tasks are created as the files are
         * streamed into this pipeline, so there are no tasks to schedule yet.
         * Also, for streaming ingest, the data source is not added to the
         * pipeline right away, so there is no data source level task to
         * schedule yet. So only result ingest tasks can be scheduled at this
         * time.
         */
        if (hasArtifactIngestModules()) {
            /*
             * Create ingest tasks for data artifacts already in the case
             * database. Additional tasks will be created as other ingest
             * modules add data aritfacts.
             */
            taskScheduler.scheduleDataArtifactIngestTasks(this);
        }
    }

    /**
     * Starts a data source level ingest. Used for streaming ingest, in which
     * the data source is not ready when ingest starts.
     */
    private void startDataSourceTaskInStreamingMode() {
        /*
         * Now that the data source processor analysis of the data source is
         * complete, an estimate of the files remaining to be processed can be
         * calculated and the file ingest progress bar in the lower right hand
         * corner of the main application window Fcan be switched from
         * indeterminate to determinate.
         */
        synchronized (fileIngestProgressLock) {
            estimatedFilesToProcess = dataSource.accept(new GetFilesCountVisitor());
            if (doUI && fileIngestProgress != null) {
                fileIngestProgress.switchToDeterminate((int) estimatedFilesToProcess);
            }
        }

        if (doUI) {
            /**
             * Start the first stage data source ingest progress bar in the
             * lower right hand corner of the main application window.
             */
            if (hasFirstStageDataSourceIngestModules()) {
                startDataSourceIngestProgressBar();
            }
        }

        /**
         * Make the first stage data source level ingest pipeline the current
         * data source level pipeline.
         */
        synchronized (this.dataSourceIngestPipelineLock) {
            this.currentDataSourceIngestPipeline = this.firstStageDataSourceIngestPipeline;
        }

        logInfoMessage("Scheduling first stage data source level ingest task in streaming mode"); //NON-NLS
        synchronized (this.stageTransitionLock) {
            stage = IngestJobPipeline.Stages.FIRST_STAGE_ALL_TASKS;
            IngestJobPipeline.taskScheduler.scheduleDataSourceIngestTask(this);
        }
    }

    /**
     * Starts the second stage ingest task pipelines.
     */
    private void startSecondStage() {
        logInfoMessage(String.format("Starting second stage ingest task pipelines for %s (objID=%d, jobID=%d)", dataSource.getName(), job.getId())); //NON-NLS
        stage = IngestJobPipeline.Stages.SECOND_STAGE;
        if (doUI) {
            startDataSourceIngestProgressBar();
        }
        synchronized (dataSourceIngestPipelineLock) {
            currentDataSourceIngestPipeline = secondStageDataSourceIngestPipeline;
        }
        taskScheduler.scheduleDataSourceIngestTask(this);
    }

    /**
     * Starts a progress bar for the results ingest tasks for the ingest job.
     */
    private void startArtifactIngestProgressBar() {
        if (doUI) {
            synchronized (resultsIngestProgressLock) {
                String displayName = NbBundle.getMessage(this.getClass(), "IngestJob.progress.resultsIngest.displayName", this.dataSource.getName());
                resultsIngestProgress = ProgressHandle.createHandle(displayName, new Cancellable() {
                    @Override
                    public boolean cancel() {
                        IngestJobPipeline.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        return true;
                    }
                });
                resultsIngestProgress.start();
                resultsIngestProgress.switchToIndeterminate();
            }
        }
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
        synchronized (stageTransitionLock) {
            /*
             * Note that there is nothing to do here for the
             * FIRST_STAGE_FILE_AND_RESULTS_TASKS_ONLY stage for streaming
             * ingest, because we need to wait for the data source to be added
             * to the job and the transition to FIRST_STAGE_ALL_TASKS.
             */
            if (stage != Stages.FIRST_STAGE_FILE_AND_RESULTS_TASKS_ONLY
                    && IngestJobPipeline.taskScheduler.currentTasksAreCompleted(this)) {
                switch (stage) {
                    case FIRST_STAGE_FILE_AND_RESULTS_TASKS_ONLY:
                        /*
                         * Nothing to do here, need to wait for the data source
                         * to be added to the job and the transition to
                         * FIRST_STAGE_ALL_TASKS.
                         */
                        break;
                    case FIRST_STAGE_ALL_TASKS:
                        /*
                         * Tasks completed:
                         *
                         * 1. The first stage data source level ingest task (if
                         * any).
                         *
                         * 2. All file ingest tasks, including tasks for
                         * carved/derived files submitted by ingest modules in
                         * the first stage via IngestJobContext,addFilesToJob().
                         *
                         * 3. The results tasks for results in the case database
                         * when the job started, plus any result tasks submitted
                         * by the ingest modules in other tasks in the first
                         * stage via IngestJobContext.addDataArtifactsToJob().
                         */
                        finishFirstStage();
                        break;
                    case SECOND_STAGE:
                        /*
                         * Tasks completed:
                         *
                         * 1. The second stage data source level ingest task (if
                         * any).
                         *
                         * 2. The results tasks for any results added by second
                         * stage ingest modules via
                         * IngestJobContext.addDataArtifactsToJob().
                         */
                        shutDown();
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

        shutDownIngestTaskPipeline(currentDataSourceIngestPipeline);
        while (!fileIngestPipelinesQueue.isEmpty()) {
            FileIngestPipeline pipeline = fileIngestPipelinesQueue.poll();
            shutDownIngestTaskPipeline(pipeline);
        }

        finishProgressBar(dataSourceIngestProgress, dataSourceIngestProgressLock);
        finishProgressBar(fileIngestProgress, fileIngestProgressLock);

        if (!cancelled && hasSecondStageDataSourceIngestModules()) {
            startSecondStage();
        } else {
            shutDown();
        }
    }

    /**
     * Shuts down the ingest pipelines and progress bars for this job.
     */
    private void shutDown() {
        logInfoMessage("Finished all tasks"); //NON-NLS        
        stage = IngestJobPipeline.Stages.FINALIZATION;

        shutDownIngestTaskPipeline(currentDataSourceIngestPipeline);
        shutDownIngestTaskPipeline(artifactIngestPipeline);

        finishProgressBar(dataSourceIngestProgress, dataSourceIngestProgressLock);
        finishProgressBar(fileIngestProgress, fileIngestProgressLock);
        finishProgressBar(resultsIngestProgress, resultsIngestProgressLock);

        if (ingestJobInfo != null) {
            if (cancelled) {
                try {
                    ingestJobInfo.setIngestJobStatus(IngestJobStatusType.CANCELLED);
                } catch (TskCoreException ex) {
                    logErrorMessage(Level.WARNING, "Failed to update ingest job status in case database", ex);
                }
            } else {
                try {
                    ingestJobInfo.setIngestJobStatus(IngestJobStatusType.COMPLETED);
                } catch (TskCoreException ex) {
                    logErrorMessage(Level.WARNING, "Failed to update ingest job status in case database", ex);
                }
            }
            try {
                ingestJobInfo.setEndDateTime(new Date());
            } catch (TskCoreException ex) {
                logErrorMessage(Level.WARNING, "Failed to set job end date in case database", ex);
            }
        }

        job.notifyIngestPipelineShutDown(this);
    }

    /**
     * Shuts down an ingest task pipeline.
     *
     * @param pipeline The pipeline.
     */
    private <T extends IngestTask> void shutDownIngestTaskPipeline(IngestTaskPipeline<T> pipeline) {
        if (pipeline.isRunning()) {
            List<IngestModuleError> errors = new ArrayList<>();
            errors.addAll(pipeline.shutDown());
            if (!errors.isEmpty()) {
                logIngestModuleErrors(errors);
            }
        }
    }

    /**
     * Finishes a progress bar.
     *
     * @param progress The progress bar.
     * @param lock     The lock that guards the progress bar.
     */
    private void finishProgressBar(ProgressHandle progress, Object lock) {
        if (doUI) {
            synchronized (lock) {
                if (progress != null) {
                    progress.finish();
                    progress = null;
                }
            }
        }
    }

    /**
     * Executes an ingest task by passing the task to the appropriate ingest
     * task pipeline.
     *
     * @param task The ingest task.
     */
    void execute(IngestTask task) {
        /*
         * NOTE: The following switch on task type enables elimination of code
         * duplication in the IngestTask hierarchy. Future work may or may not
         * be able to eliminate this switch.
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
     * Passes the data source for the ingest job through the currently active
     * data source level ingest task pipeline (first stage or second stage data
     * source ingest modules).
     *
     * @param task A data source ingest task wrapping the data source.
     */
    private void executeDataSourceIngestTask(DataSourceIngestTask task) {
        try {
            synchronized (dataSourceIngestPipelineLock) {
                if (!isCancelled() && !currentDataSourceIngestPipeline.isEmpty()) {
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(currentDataSourceIngestPipeline.executeTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors);
                    }
                }
            }

            if (doUI) {
                /**
                 * Shut down the data source ingest progress bar right away.
                 * Data source-level processing is finished for this stage.
                 */
                synchronized (dataSourceIngestProgressLock) {
                    if (dataSourceIngestProgress != null) {
                        dataSourceIngestProgress.finish();
                        dataSourceIngestProgress = null;
                    }
                }
            }

        } finally {
            taskScheduler.notifyTaskCompleted(task);
            checkForStageCompleted();
        }
    }

    /**
     * Passes a file from the data source for the ingest job through the file
     * ingest task pipeline (file ingest modules).
     *
     * @param task A file ingest task wrapping the file.
     */
    private void executeFileIngestTask(FileIngestTask task) {
        try {
            if (!isCancelled()) {
                FileIngestPipeline pipeline = fileIngestPipelinesQueue.take();
                if (!pipeline.isEmpty()) {
                    /*
                     * Get the file from the task. If the file was "streamed,"
                     * the task may only have the file object ID and a trip to
                     * the case database will be required.
                     */
                    AbstractFile file;
                    try {
                        file = task.getFile();
                    } catch (TskCoreException ex) {
                        List<IngestModuleError> errors = new ArrayList<>();
                        errors.add(new IngestModuleError("Ingest Pipeline", ex));
                        logIngestModuleErrors(errors);
                        fileIngestPipelinesQueue.put(pipeline);
                        return;
                    }

                    synchronized (fileIngestProgressLock) {
                        ++processedFiles;
                        if (doUI) {
                            /**
                             * Update the file ingest progress bar in the lower
                             * right hand corner of the main application window.
                             */
                            if (processedFiles <= estimatedFilesToProcess) {
                                fileIngestProgress.progress(file.getName(), (int) processedFiles);
                            } else {
                                fileIngestProgress.progress(file.getName(), (int) estimatedFilesToProcess);
                            }
                            filesInProgress.add(file.getName());
                        }
                    }

                    /**
                     * Run the file through the modules in the pipeline.
                     */
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.executeTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors, file);
                    }

                    if (doUI && !cancelled) {
                        synchronized (fileIngestProgressLock) {
                            /**
                             * Update the file ingest progress bar again, in
                             * case the file was being displayed.
                             */
                            filesInProgress.remove(file.getName());
                            if (filesInProgress.size() > 0) {
                                fileIngestProgress.progress(filesInProgress.get(0));
                            } else {
                                fileIngestProgress.progress("");
                            }
                        }
                    }
                }
                fileIngestPipelinesQueue.put(pipeline);
            }
        } catch (InterruptedException ex) {
            // RJCTODO This probablly should be logged, interrupt during wait for pipeline copy
            // Also need to reset the flag...
        } finally {
            taskScheduler.notifyTaskCompleted(task);
            checkForStageCompleted();
        }
    }

    /**
     * Passes a data artifact from the data source for the ingest job through
     * the data artifact ingest task pipeline (data artifact ingest modules).
     *
     * @param task A data artifact ingest task wrapping the file.
     */
    private void executeDataArtifactIngestTask(DataArtifactIngestTask task) {
        try {
            if (!isCancelled() && !artifactIngestPipeline.isEmpty()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(artifactIngestPipeline.executeTask(task));
                if (!errors.isEmpty()) {
                    logIngestModuleErrors(errors);
                }
            }
        } finally {
            taskScheduler.notifyTaskCompleted(task);
            checkForStageCompleted();
        }
    }

    /**
     * Adds some subset of the "streamed" files for a stremaing ingest job to
     * this pipeline after startUp() has been called.
     *
     * @param fileObjIds The object IDs of the files.
     */
    void addStreamingIngestFiles(List<Long> fileObjIds) {
        if (hasFileIngestModules()) {
            if (stage.equals(Stages.FIRST_STAGE_FILE_AND_RESULTS_TASKS_ONLY)) {
                IngestJobPipeline.taskScheduler.scheduleStreamedFileIngestTasks(this, fileObjIds);
            } else {
                logErrorMessage(Level.SEVERE, "Adding streaming files to job during stage " + stage.toString() + " not supported");
            }
        }
    }

    /**
     * Adds the data source for a streaming ingest job to this pipeline after
     * startUp() has been called. Intended to be called after the data source
     * processor has finished its processing (i.e., all the file system files
     * for the data source are in the case database).
     */
    void addStreamingIngestDataSource() {
        startDataSourceTaskInStreamingMode();
        checkForStageCompleted();
    }

    /**
     * Adds additional files (e.g., extracted or carved files) for any type of
     * ingest job to this pipeline after startUp() has been called. Not
     * currently supported for second stage of the job.
     *
     * @param files A list of the files to add.
     */
    void addFiles(List<AbstractFile> files) {
        if (stage.equals(Stages.FIRST_STAGE_FILE_AND_RESULTS_TASKS_ONLY)
                || stage.equals(Stages.FIRST_STAGE_ALL_TASKS)) {
            taskScheduler.fastTrackFileIngestTasks(this, files);
        } else {
            logErrorMessage(Level.SEVERE, "Adding streaming files to job during stage " + stage.toString() + " not supported");
        }

        /**
         * The intended clients of this method are ingest modules running code
         * in an ingest thread that is holding a reference to a "primary" ingest
         * task that was the source of the files, in which case a completion
         * check would not be necessary, so this is a bit of defensive
         * programming.
         */
        checkForStageCompleted();
    }

    /**
     * Adds data artifacts for any type of ingest job to this pipeline after
     * startUp() has been called.
     *
     * @param artifacts
     */
    void addDataArtifacts(List<DataArtifact> artifacts) {
        if (stage.equals(Stages.FIRST_STAGE_FILE_AND_RESULTS_TASKS_ONLY)
                || stage.equals(Stages.FIRST_STAGE_ALL_TASKS)
                || stage.equals(Stages.SECOND_STAGE)) {
            taskScheduler.scheduleDataArtifactIngestTasks(this, artifacts);
        } else {
            logErrorMessage(Level.SEVERE, "Adding streaming files to job during stage " + stage.toString() + " not supported");
        }

        /**
         * The intended clients of this method are ingest modules running code
         * in an ingest thread that is holding a reference to a "primary" ingest
         * task that was the source of the files, in which case a completion
         * check would not be necessary, so this is a bit of defensive
         * programming.
         */
        checkForStageCompleted();
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
        if (doUI && !cancelled) {
            synchronized (dataSourceIngestProgressLock) {
                if (null != dataSourceIngestProgress) {
                    dataSourceIngestProgress.progress("", workUnits);
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
        if (doUI && !cancelled) {
            synchronized (dataSourceIngestProgressLock) {
                if (null != dataSourceIngestProgress) {
                    dataSourceIngestProgress.progress(currentTask);
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
        IngestJobPipeline.taskScheduler.cancelPendingFileTasksForIngestJob(this);

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
        logger.log(Level.INFO, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id = %d)", message, this.dataSource.getName(), this.dataSource.getId(), pipelineId, ingestJobInfo.getIngestJobId())); //NON-NLS        
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
        logger.log(level, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id = %d)", message, this.dataSource.getName(), this.dataSource.getId(), pipelineId, ingestJobInfo.getIngestJobId()), throwable); //NON-NLS
    }

    /**
     * Writes an error message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param level   The logging level for the message.
     * @param message The message.
     */
    private void logErrorMessage(Level level, String message) {
        logger.log(level, String.format("%s (data source = %s, objId = %d, pipeline id = %d, ingest job id %d)", message, this.dataSource.getName(), this.dataSource.getId(), pipelineId, ingestJobInfo.getIngestJobId())); //NON-NLS
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
     * Gets a snapshot of this ingest pipelines current state.
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

        return new Snapshot(dataSource.getName(),
                pipelineId, createTime,
                getCurrentDataSourceIngestModule(),
                fileIngestRunning, fileIngestStartTime,
                cancelled, cancellationReason, cancelledDataSourceIngestModules,
                processedFilesCount, estimatedFilesToProcessCount, snapShotTime, tasksSnapshot);
    }

}
