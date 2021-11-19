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
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.JOptionPane;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.IngestTasksScheduler.IngestTasksSnapshot;
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
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DataSource;

/**
 * Executes an ingest job by orchestrating the construction, start up, ingest
 * task execution, and shut down of the ingest module pipelines for an ingest
 * job.
 */
final class IngestJobExecutor {

    private static final String AUTOPSY_MODULE_PREFIX = "org.sleuthkit.autopsy";
    private static final Logger logger = Logger.getLogger(IngestJobExecutor.class.getName());

    /*
     * A regular expression for identifying the proxy classes Jython generates
     * for ingest module factories written using Python. For example:
     * org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14
     */
    private static final Pattern JYTHON_MODULE_REGEX = Pattern.compile("org\\.python\\.proxies\\.(.+?)\\$(.+?)(\\$[0-9]*)?$");

    /*
     * These fields are the identity of this object: the parent ingest job, the
     * user's ingest job settings, and the data source to be analyzed by the
     * ingest module pipelines. Optionally, there is a set of files to be
     * analyzed instead of analyzing all of the files in the data source.
     */
    private final IngestJob ingestJob;
    private final IngestJobSettings settings;
    private DataSource dataSource;
    private final List<AbstractFile> files;
    private final long createTime;

    /*
     * There are separate pipelines for high-priority and low priority data
     * source level ingest modules. These pipelines are run sequentially, not
     * simultaneously.
     */
    private DataSourceIngestPipeline highPriorityDataSourceIngestPipeline;
    private DataSourceIngestPipeline lowPriorityDataSourceIngestPipeline;
    private volatile DataSourceIngestPipeline currentDataSourceIngestPipeline;

    /*
     * There are one or more identical file ingest module pipelines, based on
     * the number of file ingest threads in the ingest manager. References to
     * the file ingest pipelines are put into two collections, each with its own
     * purpose. A blocking queue allows file ingest threads to take and return
     * file ingest pipelines as they work through the file ingest tasks for one
     * or more ingest jobs. Having the same number of pipelines as threads
     * ensures that a file ingest thread will never be idle as long as there are
     * file ingest tasks still to do, regardless of the number of ingest jobs in
     * progress. Additionally, a fixed list is used to cycle through the file
     * ingest module pipelines to make ingest progress snapshots.
     */
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelinesQueue = new LinkedBlockingQueue<>();
    private final List<FileIngestPipeline> fileIngestPipelines = new ArrayList<>();

    /*
     * There is at most one data artifact ingest module pipeline.
     */
    private DataArtifactIngestPipeline artifactIngestPipeline;

    /*
     * There is at most one analysis result ingest module pipeline.
     */
    private AnalysisResultIngestPipeline resultIngestPipeline;

    /*
     * The construction, start up, execution, and shut down of the ingest module
     * pipelines for an ingest job is done in stages.
     */
    private static enum IngestJobStage {
        /*
         * In this stage, the ingest module pipelines are constructed per the
         * user's ingest job settings. This stage ends when all of the ingest
         * module pipelines for the ingest job are ready to run.
         */
        PIPELINES_START_UP,
        /*
         * This stage is unique to a streaming mode ingest job. In this stage,
         * file ingest module pipelines are analyzing files streamed to them via
         * addStreamedFiles(). If the ingest job is configured to have a data
         * artifact ingest pipeline, that pipeline is also analyzing any data
         * artifacts generated by the file ingest modules. This stage ends when
         * addStreamedDataSource() is called.
         */
        STREAMED_FILE_ANALYSIS_ONLY,
        /*
         * In this stage, file ingest module pipelines and/or a pipeline of
         * higher-priority data source level ingest modules are running. If the
         * ingest job is configured to have a data artifact ingest pipeline,
         * that pipeline is also analyzing any data artifacts generated by the
         * file and/or data source level ingest modules.
         */
        FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS,
        /**
         * In this stage, a pipeline of lower-priority, usually long-running
         * data source level ingest ingest modules is running. If the ingest job
         * is configured to have a data artifact ingest pipeline, that pipeline
         * is also analyzing any data artifacts generated by the data source
         * level ingest modules.
         */
        LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS,
        /**
         * In this stage, The pipeline is shutting down its ingest modules.
         */
        PIPELINES_SHUT_DOWN
    };

    /*
     * The stage field is volatile to allow it to be read by multiple threads.
     * So the stage transition lock is used not to guard the stage field, but to
     * coordinate stage transitions.
     */
    private volatile IngestJobStage stage = IngestJobExecutor.IngestJobStage.PIPELINES_START_UP;
    private final Object stageTransitionLock = new Object();

    /*
     * During each stage of the ingest job, this object interacts with the
     * ingest task scheduler to create ingest tasks for analyzing the data
     * source, files and data artifacts that are the subject of the ingest job.
     * The scheduler queues the tasks for the ingest manager's ingest threads.
     * The ingest tasks are the units of work for the ingest module pipelines.
     */
    private static final IngestTasksScheduler taskScheduler = IngestTasksScheduler.getInstance();

    /*
     * Two levels of ingest job cancellation are supported: 1) cancellation of
     * analysis by individual data source level ingest modules, and 2)
     * cancellation of all remaining analysis by all of the ingest modules.
     * Cancellation works by setting flags that are checked by the ingest module
     * pipelines every time they transition from one module to another. Ingest
     * modules are also expected to check these flags (via the ingest job
     * context) and stop processing if they are set. This approach to
     * cancellation means that there can be a variable length delay between a
     * cancellation request and its fulfillment. Analysis already completed at
     * the time that cancellation occurs is NOT discarded.
     */
    private volatile boolean currentDataSourceIngestModuleCancelled;
    private final List<String> cancelledDataSourceIngestModules = new CopyOnWriteArrayList<>();
    private volatile boolean jobCancelled;
    private volatile IngestJob.CancellationReason cancellationReason = IngestJob.CancellationReason.NOT_CANCELLED;

    /*
     * If running in the NetBeans thick client application version of Autopsy,
     * NetBeans progress bars are used to display ingest job progress in the
     * lower right hand corner of the main application window. A layer of
     * abstraction to allow alternate representations of progress could be used
     * here, as it is in other places in the application, to better decouple
     * this object from the application's presentation layer.
     */
    private final boolean usingNetBeansGUI;
    private final Object dataSourceIngestProgressLock = new Object();
    private ProgressHandle dataSourceIngestProgressBar;
    private final Object fileIngestProgressLock = new Object();
    private final List<String> filesInProgress = new ArrayList<>();
    private long estimatedFilesToProcess;
    private long processedFiles;
    private ProgressHandle fileIngestProgressBar;
    private final Object artifactIngestProgressLock = new Object();
    private ProgressHandle artifactIngestProgressBar;
    private final Object resultIngestProgressLock = new Object();
    private ProgressHandle resultIngestProgressBar;

    /*
     * The ingest job details that are stored to the case database are tracked
     * using this object and are recorded in the database when the ingest module
     * pipelines are started up and shut down.
     */
    private volatile IngestJobInfo ingestJobInfo;

    /*
     * Ingest module pipelines register and unregister the ingest thread they
     * are running in when a scheduled ingest pause occurs and the threads are
     * made to sleep. This allows interruption of these sleeping threads if the
     * ingest job is canceled while paused.
     */
    private final Object threadRegistrationLock = new Object();
    @GuardedBy("threadRegistrationLock")
    private final Set<Thread> pausedIngestThreads = new HashSet<>();

    /**
     * Constructs an object that executes an ingest job by orchestrating the
     * construction, start up, ingest task execution, and shut down of the
     * ingest module pipelines for an ingest job.
     *
     *
     * @param ingestJob  The ingest job.
     * @param dataSource The data source.
     * @param files      A subset of the files from the data source. If the list
     *                   is empty, ALL of the files in the data source are an
     *                   analyzed.
     * @param settings   The ingest job settings.
     *
     * @throws InterruptedException Exception thrown if the thread in which the
     *                              pipeline is being created is interrupted.
     */
    IngestJobExecutor(IngestJob ingestJob, Content dataSource, List<AbstractFile> files, IngestJobSettings settings) throws InterruptedException {
        if (!(dataSource instanceof DataSource)) {
            throw new IllegalArgumentException("Passed dataSource that does not implement the DataSource interface"); //NON-NLS
        }
        this.ingestJob = ingestJob;
        this.dataSource = (DataSource) dataSource;
        this.files = new ArrayList<>();
        this.files.addAll(files);
        this.settings = settings;
        usingNetBeansGUI = RuntimeProperties.runningWithGUI();
        createTime = new Date().getTime();
        stage = IngestJobStage.PIPELINES_START_UP;
        createIngestModulePipelines();
    }

    /**
     * Sorts ingest module templates so that core Autopsy ingest modules come
     * before third party ingest modules and ingest modules implemented using
     * Java come before ingest modules implemented using Jython.
     *
     * @param sortedModules The output list to hold the sorted modules.
     * @param javaModules   The input ingest module templates for modules
     *                      implemented using Java.
     * @param jythonModules The ingest module templates for modules implemented
     *                      using Jython.
     */
    private static void addToIngestPipelineTemplate(final List<IngestModuleTemplate> sortedModules, final Map<String, IngestModuleTemplate> javaModules, final Map<String, IngestModuleTemplate> jythonModules) {
        final List<IngestModuleTemplate> autopsyModules = new ArrayList<>();
        final List<IngestModuleTemplate> thirdPartyModules = new ArrayList<>();
        Stream.concat(javaModules.entrySet().stream(), jythonModules.entrySet().stream()).forEach((templateEntry) -> {
            if (templateEntry.getKey().startsWith(AUTOPSY_MODULE_PREFIX)) {
                autopsyModules.add(templateEntry.getValue());
            } else {
                thirdPartyModules.add(templateEntry.getValue());
            }
        });
        sortedModules.addAll(autopsyModules);
        sortedModules.addAll(thirdPartyModules);
    }

    /**
     * Extracts a module class name from a Jython module proxy class name. For
     * example, a Jython class name such
     * "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14"
     * will be parsed to return
     * "GPX_Parser_Module.GPXParserFileIngestModuleFactory."
     *
     * @param className The canonical class name.
     *
     * @return The Jython proxu class name or null if the extraction fails.
     */
    private static String getModuleNameFromJythonClassName(String className) {
        Matcher m = JYTHON_MODULE_REGEX.matcher(className);
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
    private static void addModuleTemplateToSortingMap(Map<String, IngestModuleTemplate> mapping, Map<String, IngestModuleTemplate> jythonMapping, IngestModuleTemplate template) {
        String className = template.getModuleFactory().getClass().getCanonicalName();
        String jythonName = getModuleNameFromJythonClassName(className);
        if (jythonName != null) {
            jythonMapping.put(jythonName, template);
        } else {
            mapping.put(className, template);
        }
    }

    /**
     * Creates the ingest module pipelines for the ingest job.
     *
     * @throws InterruptedException Exception thrown if the thread in which the
     *                              pipeline is being created is interrupted.
     */
    private void createIngestModulePipelines() throws InterruptedException {
        /*
         * Get the enabled ingest module templates from the ingest job settings.
         */
        List<IngestModuleTemplate> enabledTemplates = settings.getEnabledIngestModuleTemplates();

        /**
         * Sort the ingest module templates into buckets based on the module
         * types the template can be used to create. A template may go into more
         * than one bucket. Each bucket actually consists of two collections:
         * one for Java modules and one for Jython modules.
         */
        Map<String, IngestModuleTemplate> javaDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> javaFileModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonFileModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> javaArtifactModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonArtifactModuleTemplates = new LinkedHashMap<>();
        for (IngestModuleTemplate template : enabledTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                addModuleTemplateToSortingMap(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, template);
            }
            if (template.isFileIngestModuleTemplate()) {
                addModuleTemplateToSortingMap(javaFileModuleTemplates, jythonFileModuleTemplates, template);
            }
            if (template.isDataArtifactIngestModuleTemplate()) {
                addModuleTemplateToSortingMap(javaArtifactModuleTemplates, jythonArtifactModuleTemplates, template);
            }
        }

        /**
         * Take the module templates that have pipeline configuration entries
         * out of the buckets and add them to ingest module pipeline templates
         * in the order prescribed by the pipeline configuration.
         */
        IngestPipelinesConfiguration pipelineConfig = IngestPipelinesConfiguration.getInstance();
        List<IngestModuleTemplate> firstStageDataSourcePipelineTemplate = createIngestPipelineTemplate(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, pipelineConfig.getStageOneDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> secondStageDataSourcePipelineTemplate = createIngestPipelineTemplate(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, pipelineConfig.getStageTwoDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> filePipelineTemplate = createIngestPipelineTemplate(javaFileModuleTemplates, jythonFileModuleTemplates, pipelineConfig.getFileIngestPipelineConfig());
        List<IngestModuleTemplate> artifactPipelineTemplate = new ArrayList<>();
        List<IngestModuleTemplate> resultsPipelineTemplate = new ArrayList<>();

        /**
         * Add any ingest module templates remaining in the buckets to the
         * appropriate ingest module pipeline templates. Data source level
         * ingest modules templates that were not listed in the pipeline
         * configuration are added to the first stage data source pipeline
         * template, Java modules are added before Jython modules and Core
         * Autopsy modules are added before third party modules.
         */
        addToIngestPipelineTemplate(firstStageDataSourcePipelineTemplate, javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates);
        addToIngestPipelineTemplate(filePipelineTemplate, javaFileModuleTemplates, jythonFileModuleTemplates);
        addToIngestPipelineTemplate(artifactPipelineTemplate, javaArtifactModuleTemplates, jythonArtifactModuleTemplates);
        addToIngestPipelineTemplate(resultsPipelineTemplate, javaArtifactModuleTemplates, jythonArtifactModuleTemplates);

        /**
         * Construct the ingest module pipelines from the ingest module pipeline
         * templates.
         */
        highPriorityDataSourceIngestPipeline = new DataSourceIngestPipeline(this, firstStageDataSourcePipelineTemplate);
        lowPriorityDataSourceIngestPipeline = new DataSourceIngestPipeline(this, secondStageDataSourcePipelineTemplate);
        int numberOfFileIngestThreads = IngestManager.getInstance().getNumberOfFileIngestThreads();
        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
            FileIngestPipeline pipeline = new FileIngestPipeline(this, filePipelineTemplate);
            fileIngestPipelinesQueue.put(pipeline);
            fileIngestPipelines.add(pipeline);
        }
        artifactIngestPipeline = new DataArtifactIngestPipeline(this, artifactPipelineTemplate);
        resultIngestPipeline = new AnalysisResultIngestPipeline(this, resultsPipelineTemplate);
    }

    /**
     * Creates an ingest module pipeline template that can be used to construct
     * an ingest module pipeline.
     *
     * @param javaIngestModuleTemplates   Ingest module templates for ingest
     *                                    modules implemented using Java.
     * @param jythonIngestModuleTemplates Ingest module templates for ingest
     *                                    modules implemented using Jython.
     * @param pipelineConfig              An ordered list of the ingest modules
     *                                    that belong in the ingest pipeline for
     *                                    which the template is being created.
     *
     * @return An ordered list of ingest module templates, i.e., a template for
     *         creating ingest module pipelines.
     */
    private static List<IngestModuleTemplate> createIngestPipelineTemplate(Map<String, IngestModuleTemplate> javaIngestModuleTemplates, Map<String, IngestModuleTemplate> jythonIngestModuleTemplates, List<String> pipelineConfig) {
        List<IngestModuleTemplate> pipelineTemplate = new ArrayList<>();
        for (String moduleClassName : pipelineConfig) {
            if (javaIngestModuleTemplates.containsKey(moduleClassName)) {
                pipelineTemplate.add(javaIngestModuleTemplates.remove(moduleClassName));
            } else if (jythonIngestModuleTemplates.containsKey(moduleClassName)) {
                pipelineTemplate.add(jythonIngestModuleTemplates.remove(moduleClassName));
            }
        }
        return pipelineTemplate;
    }

    /**
     * Gets the ID of the ingest job that owns this object.
     *
     * @return The ID.
     */
    long getIngestJobId() {
        return ingestJob.getId();
    }

    /**
     * Gets the ingest job execution context name.
     *
     * @return The context name.
     */
    String getExecutionContext() {
        return settings.getExecutionContext();
    }

    /**
     * Gets the data source of the ingest job.
     *
     * @return The data source.
     */
    DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Queries whether or not unallocated space should be processed for the
     * ingest job.
     *
     * @return True or false.
     */
    boolean shouldProcessUnallocatedSpace() {
        return settings.getProcessUnallocatedSpace();
    }

    /**
     * Gets the file ingest filter for the ingest job.
     *
     * @return The filter.
     */
    FilesSet getFileIngestFilter() {
        return settings.getFileFilter();
    }

    /**
     * Checks to see if there is at least one ingest module to run.
     *
     * @return True or false.
     */
    boolean hasIngestModules() {
        return hasFileIngestModules()
                || hasHighPriorityDataSourceIngestModules()
                || hasLowPriorityDataSourceIngestModules()
                || hasDataArtifactIngestModules();
    }

    /**
     * Checks to see if there is at least one data source level ingest module to
     * run.
     *
     * @return True or false.
     */
    boolean hasDataSourceIngestModules() {
        if (stage == IngestJobStage.LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS) {
            return hasLowPriorityDataSourceIngestModules();
        } else {
            return hasHighPriorityDataSourceIngestModules();
        }
    }

    /**
     * Checks to see if there is at least one high priority data source level
     * ingest module to run.
     *
     * @return True or false.
     */
    private boolean hasHighPriorityDataSourceIngestModules() {
        return (highPriorityDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if there is at least one low priority data source level
     * ingest module to run.
     *
     * @return True or false.
     */
    private boolean hasLowPriorityDataSourceIngestModules() {
        return (lowPriorityDataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if there is at least one file ingest module to run.
     *
     * @return True or false.
     */
    boolean hasFileIngestModules() {
        if (!fileIngestPipelines.isEmpty()) {
            return !fileIngestPipelines.get(0).isEmpty();
        }
        return false;
    }

    /**
     * Checks to see if there is at least one data artifact ingest module to
     * run.
     *
     * @return True or false.
     */
    boolean hasDataArtifactIngestModules() {
        return (artifactIngestPipeline.isEmpty() == false);
    }

    /**
     * Checks to see if there is at least one analysis result ingest module to
     * run.
     *
     * @return True or false.
     */
    boolean hasAnalysisResultIngestModules() {
        return (resultIngestPipeline.isEmpty() == false);
    }

    /**
     * Determnines which inges job stage to start in and starts up the ingest
     * module pipelines.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = startUpIngestModulePipelines();
        if (errors.isEmpty()) {
            recordIngestJobStartUpInfo();
            if (hasHighPriorityDataSourceIngestModules() || hasFileIngestModules() || hasDataArtifactIngestModules()) {
                if (ingestJob.getIngestMode() == IngestJob.Mode.STREAMING) {
                    startStreamingModeAnalysis();
                } else {
                    startBatchModeAnalysis();
                }
            } else if (hasLowPriorityDataSourceIngestModules()) {
                startLowPriorityDataSourceAnalysis();
            }
        }
        return errors;
    }

    /**
     * Starts up the ingest module pipelines in this ingest. Note that all of
     * the child pipelines are started so that any and all start up errors can
     * be returned to the caller. It is important to capture all of the errors,
     * because the ingest job will be automatically cancelled and the errors
     * will be reported to the user so either the issues can be addressed or the
     * modules that can't start up can be disabled before the ingest job is
     * attempted again.
     *
     * @return A list of ingest module startup errors, empty on success.
     */
    private List<IngestModuleError> startUpIngestModulePipelines() {
        List<IngestModuleError> errors = new ArrayList<>();
        errors.addAll(startUpIngestModulePipeline(highPriorityDataSourceIngestPipeline));
        errors.addAll(startUpIngestModulePipeline(lowPriorityDataSourceIngestPipeline));
        for (FileIngestPipeline pipeline : fileIngestPipelines) {
            List<IngestModuleError> filePipelineErrors = startUpIngestModulePipeline(pipeline);
            if (!filePipelineErrors.isEmpty()) {
                /*
                 * If one file pipeline copy can't start up, assume that none of
                 * them will be able to start up for the same reason.
                 */
                errors.addAll(filePipelineErrors);
                break;
            }
        }
        errors.addAll(startUpIngestModulePipeline(artifactIngestPipeline));
        errors.addAll(startUpIngestModulePipeline(resultIngestPipeline));
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
     * @return The ingest module type, may be IngestModuleType.MULTIPLE.
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
     * Starts analysis for a batch mode ingest job. For a batch mode job, all of
     * the files in the data source (excepting carved and derived files) have
     * already been added to the case database by the data source processor and
     * analysis starts in the file and high priority data source level analysis
     * stage.
     */
    private void startBatchModeAnalysis() {
        synchronized (stageTransitionLock) {
            logInfoMessage(String.format("Starting analysis in batch mode for %s (objID=%d, jobID=%d)", dataSource.getName(), dataSource.getId(), ingestJob.getId())); //NON-NLS            
            stage = IngestJobStage.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS;

            if (hasFileIngestModules()) {
                /*
                 * Do a count of the files the data source processor has added
                 * to the case database. This number will be used to estimate
                 * how many files remain to be analyzed as each file ingest task
                 * is completed.
                 */
                long filesToProcess;
                if (files.isEmpty()) {
                    filesToProcess = dataSource.accept(new GetFilesCountVisitor());
                } else {
                    filesToProcess = files.size();
                }
                synchronized (fileIngestProgressLock) {
                    estimatedFilesToProcess = filesToProcess;
                }
            }

            if (usingNetBeansGUI) {
                /*
                 * Start ingest progress bars in the lower right hand corner of
                 * the main application window.
                 */
                if (hasFileIngestModules()) {
                    startFileIngestProgressBar();
                }
                if (hasHighPriorityDataSourceIngestModules()) {
                    startDataSourceIngestProgressBar();
                }
                if (hasDataArtifactIngestModules()) {
                    startArtifactIngestProgressBar();
                }
                if (hasAnalysisResultIngestModules()) {
                    startResultIngestProgressBar();
                }
            }

            /*
             * Make the high priority data source level ingest module pipeline
             * the current data source level ingest module pipeline.
             */
            currentDataSourceIngestPipeline = highPriorityDataSourceIngestPipeline;

            /*
             * Schedule ingest tasks and then immediately check for stage
             * completion. This is necessary because it is possible that zero
             * tasks will actually make it to task execution due to the file
             * filter or other ingest job settings. In that case, there will
             * never be a stage completion check in an ingest thread executing
             * an ingest task, so such a job would run forever without a check
             * here.
             */
            if (!files.isEmpty() && hasFileIngestModules()) {
                taskScheduler.scheduleFileIngestTasks(this, files);
            } else if (hasHighPriorityDataSourceIngestModules() || hasFileIngestModules() || hasDataArtifactIngestModules()) {
                taskScheduler.scheduleIngestTasks(this);
            }
            checkForStageCompleted();
        }
    }

    /**
     * Starts analysis for a streaming mode ingest job. For a streaming mode
     * job, the data source processor streams files in as it adds them to the
     * case database and file analysis can begin before data source level
     * analysis.
     */
    private void startStreamingModeAnalysis() {
        synchronized (stageTransitionLock) {
            logInfoMessage("Starting data source level analysis in streaming mode"); //NON-NLS
            stage = IngestJobStage.STREAMED_FILE_ANALYSIS_ONLY;

            if (usingNetBeansGUI) {
                /*
                 * Start ingest progress bars in the lower right hand corner of
                 * the main application window.
                 */
                if (hasFileIngestModules()) {
                    /*
                     * Note that because estimated files remaining to process
                     * still has its initial value of zero, the progress bar
                     * will start in the "indeterminate" state. An estimate of
                     * the files to process can be computed later, when all of
                     * the files have been added ot the case database.
                     */
                    startFileIngestProgressBar();
                }
                if (hasDataArtifactIngestModules()) {
                    startArtifactIngestProgressBar();
                }
                if (hasAnalysisResultIngestModules()) {
                    startResultIngestProgressBar();
                }                
            }

            if (hasDataArtifactIngestModules()) {
                /*
                 * Schedule artifact ingest tasks for any artifacts currently in
                 * the case database. This needs to be done before any files or
                 * the data source are streamed in to avoid analyzing the data
                 * artifacts added to the case database by those tasks twice.
                 */
                taskScheduler.scheduleDataArtifactIngestTasks(this);
                taskScheduler.scheduleAnalysisResultIngestTasks(this);
            }
        }
    }

    /**
     * Signals in streaming mode that all of the files have been added to the
     * case database and streamed in, and the data source is now ready for
     * analysis.
     */
    void startStreamingModeDataSourceAnalysis() {
        synchronized (stageTransitionLock) {
            logInfoMessage("Starting full first stage analysis in streaming mode"); //NON-NLS
            stage = IngestJobExecutor.IngestJobStage.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS;
            currentDataSourceIngestPipeline = highPriorityDataSourceIngestPipeline;

            if (hasFileIngestModules()) {
                /*
                 * Do a count of the files the data source processor has added
                 * to the case database. This number will be used to estimate
                 * how many files remain to be analyzed as each file ingest task
                 * is completed.
                 */
                long filesToProcess = dataSource.accept(new GetFilesCountVisitor());
                synchronized (fileIngestProgressLock) {
                    estimatedFilesToProcess = filesToProcess;
                    if (usingNetBeansGUI && fileIngestProgressBar != null) {
                        fileIngestProgressBar.switchToDeterminate((int) estimatedFilesToProcess);
                    }
                }
            }

            if (usingNetBeansGUI) {
                /*
                 * Start a data source level ingest progress bar in the lower
                 * right hand corner of the main application window. The file
                 * and data artifact ingest progress bars were already started
                 * in startStreamingModeAnalysis().
                 */
                if (hasHighPriorityDataSourceIngestModules()) {
                    startDataSourceIngestProgressBar();
                }
            }

            currentDataSourceIngestPipeline = highPriorityDataSourceIngestPipeline;
            if (hasHighPriorityDataSourceIngestModules()) {
                IngestJobExecutor.taskScheduler.scheduleDataSourceIngestTask(this);
            } else {
                /*
                 * If no data source level ingest task is scheduled at this time
                 * and all of the file level and artifact ingest tasks scheduled
                 * during the initial file streaming stage have already
                 * executed, there will never be a stage completion check in an
                 * ingest thread executing an ingest task, so such a job would
                 * run forever without a check here.
                 */
                checkForStageCompleted();
            }
        }
    }

    /**
     * Starts low priority data source analysis.
     */
    private void startLowPriorityDataSourceAnalysis() {
        synchronized (stageTransitionLock) {
            if (hasLowPriorityDataSourceIngestModules()) {
                logInfoMessage(String.format("Starting low priority data source analysis for %s (objID=%d, jobID=%d)", dataSource.getName(), dataSource.getId(), ingestJob.getId())); //NON-NLS
                stage = IngestJobExecutor.IngestJobStage.LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS;

                if (usingNetBeansGUI) {
                    startDataSourceIngestProgressBar();
                }

                currentDataSourceIngestPipeline = lowPriorityDataSourceIngestPipeline;
                taskScheduler.scheduleDataSourceIngestTask(this);
            }
        }
    }

    /**
     * Starts a data artifacts analysis NetBeans progress bar in the lower right
     * hand corner of the main application window. The progress bar provides the
     * user with a task cancellation button. Pressing it cancels the ingest job.
     * Analysis already completed at the time that cancellation occurs is NOT
     * discarded.
     */
    private void startArtifactIngestProgressBar() {
        if (usingNetBeansGUI) {
            String displayName = NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataArtifactIngest.displayName", dataSource.getName());
            startArtifactIngestProgressBar(artifactIngestProgressLock, artifactIngestProgressBar, displayName);
        }
    }

    /**
     * Starts a data artifacts analysis NetBeans progress bar in the lower right
     * hand corner of the main application window. The progress bar provides the
     * user with a task cancellation button. Pressing it cancels the ingest job.
     * Analysis already completed at the time that cancellation occurs is NOT
     * discarded.
     */
    @NbBundle.Messages({
        "# {0} - data source name", "IngestJob.progress.analysisResultIngest.displayName=Analyzing data artifacts from {0}"
    })
    private void startResultIngestProgressBar() {
        if (usingNetBeansGUI) {
            String displayName = NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataArtifactIngest.displayName", dataSource.getName());
            startArtifactIngestProgressBar(resultIngestProgressLock, resultIngestProgressBar, displayName);
        }
    }

    /**
     * Starts a data artifacts or analysis results analysis NetBeans progress
     * bar in the lower right hand corner of the main application window. The
     * progress bar provides the user with a task cancellation button. Pressing
     * it cancels the ingest job. Analysis already completed at the time that
     * cancellation occurs is NOT discarded.
     *
     * @param progressBarLock The lock for the progress bar.
     * @param progressBar     The progress bar.
     * @param displayName     The display name for the progress bar.
     */
    private void startArtifactIngestProgressBar(Object progressBarLock, ProgressHandle progressBar, String displayName) {
        if (usingNetBeansGUI) {
            synchronized (progressBarLock) {
                progressBar = ProgressHandle.createHandle(displayName, new Cancellable() {
                    @Override
                    public boolean cancel() {
                        IngestJobExecutor.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        return true;
                    }
                });
                progressBar.start();
                progressBar.switchToIndeterminate();
            }
        }
    }

    /**
     * Starts a data source level analysis NetBeans progress bar in the lower
     * right hand corner of the main application window. The progress bar
     * provides the user with a task cancellation button. Pressing it cancels
     * either the currently running data source level ingest module or the
     * entire ingest job. Analysis already completed at the time that
     * cancellation occurs is NOT discarded.
     */
    private void startDataSourceIngestProgressBar() {
        if (usingNetBeansGUI) {
            synchronized (dataSourceIngestProgressLock) {
                String displayName = NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataSourceIngest.initialDisplayName", dataSource.getName());
                dataSourceIngestProgressBar = ProgressHandle.createHandle(displayName, new Cancellable() {
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
                            IngestJobExecutor.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        } else {
                            IngestJobExecutor.this.cancelCurrentDataSourceIngestModule();
                        }
                        return true;
                    }
                });
                dataSourceIngestProgressBar.start();
                dataSourceIngestProgressBar.switchToIndeterminate();
            }
        }
    }

    /**
     * Starts a file analysis NetBeans progress bar in the lower right hand
     * corner of the main application window. The progress bar provides the user
     * with a task cancellation button. Pressing it cancels the ingest job.
     * Analysis already completed at the time that cancellation occurs is NOT
     * discarded.
     */
    private void startFileIngestProgressBar() {
        if (usingNetBeansGUI) {
            synchronized (fileIngestProgressLock) {
                String displayName = NbBundle.getMessage(getClass(), "IngestJob.progress.fileIngest.displayName", dataSource.getName());
                fileIngestProgressBar = ProgressHandle.createHandle(displayName, new Cancellable() {
                    @Override
                    public boolean cancel() {
                        IngestJobExecutor.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        return true;
                    }
                });
                fileIngestProgressBar.start();
                fileIngestProgressBar.switchToDeterminate((int) this.estimatedFilesToProcess);
            }
        }
    }

    /**
     * Checks to see if the ingest tasks for the current stage of this job are
     * completed and does a stage transition if they are.
     */
    private void checkForStageCompleted() {
        synchronized (stageTransitionLock) {
            if (stage == IngestJobStage.STREAMED_FILE_ANALYSIS_ONLY) {
                return;
            }
            if (taskScheduler.currentTasksAreCompleted(this)) {
                switch (stage) {
                    case FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS:
                        finishFileAndHighPriorityDataSrcAnalysis();
                        break;
                    case LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS:
                        shutDown();
                        break;
                }
            }
        }
    }

    /**
     * Shuts down the file and high-priority data source level ingest pipelines
     * and progress bars for this job and starts the low-priority data source
     * level analysis stage, if appropriate.
     */
    private void finishFileAndHighPriorityDataSrcAnalysis() {
        synchronized (stageTransitionLock) {
            logInfoMessage("Finished file and high-priority data source analysis"); //NON-NLS        

            shutDownIngestModulePipeline(currentDataSourceIngestPipeline);
            while (!fileIngestPipelinesQueue.isEmpty()) {
                FileIngestPipeline pipeline = fileIngestPipelinesQueue.poll();
                shutDownIngestModulePipeline(pipeline);
            }

            if (usingNetBeansGUI) {
                synchronized (dataSourceIngestProgressLock) {
                    if (dataSourceIngestProgressBar != null) {
                        dataSourceIngestProgressBar.finish();
                        dataSourceIngestProgressBar = null;
                    }
                }

                synchronized (fileIngestProgressLock) {
                    if (fileIngestProgressBar != null) {
                        fileIngestProgressBar.finish();
                        fileIngestProgressBar = null;
                    }
                }
            }

            if (!jobCancelled && hasLowPriorityDataSourceIngestModules()) {
                startLowPriorityDataSourceAnalysis();
            } else {
                shutDown();
            }
        }
    }

    /**
     * Shuts down the ingest module pipelines and progress bars.
     */
    private void shutDown() {
        synchronized (stageTransitionLock) {
            logInfoMessage("Finished all tasks"); //NON-NLS        
            stage = IngestJobExecutor.IngestJobStage.PIPELINES_SHUT_DOWN;

            shutDownIngestModulePipeline(currentDataSourceIngestPipeline);
            shutDownIngestModulePipeline(artifactIngestPipeline);
            shutDownIngestModulePipeline(resultIngestPipeline);

            if (usingNetBeansGUI) {
                synchronized (dataSourceIngestProgressLock) {
                    if (dataSourceIngestProgressBar != null) {
                        dataSourceIngestProgressBar.finish();
                        dataSourceIngestProgressBar = null;
                    }
                }

                synchronized (fileIngestProgressLock) {
                    if (fileIngestProgressBar != null) {
                        fileIngestProgressBar.finish();
                        fileIngestProgressBar = null;
                    }
                }

                synchronized (artifactIngestProgressLock) {
                    if (artifactIngestProgressBar != null) {
                        artifactIngestProgressBar.finish();
                        artifactIngestProgressBar = null;
                    }
                }

                synchronized (resultIngestProgressLock) {
                    if (resultIngestProgressBar != null) {
                        resultIngestProgressBar.finish();
                        resultIngestProgressBar = null;
                    }
                }
            }

            if (ingestJobInfo != null) {
                if (jobCancelled) {
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
        }

        ingestJob.notifyIngestPipelinesShutDown();
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
     * Passes the data source for the ingest job through the currently active
     * data source level ingest module pipeline (high-priority or low-priority).
     *
     * @param task A data source ingest task encapsulating the data source and
     *             the data source ingest pipeline to use to execute the task.
     */
    void execute(DataSourceIngestTask task) {
        try {
            if (!isCancelled()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(currentDataSourceIngestPipeline.performTask(task));
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
     * Passes a file from the data source for the ingest job through a file
     * ingest module pipeline.
     *
     * @param task A file ingest task encapsulating the file and the file ingest
     *             pipeline to use to execute the task.
     */
    void execute(FileIngestTask task) {
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
                        if (usingNetBeansGUI) {
                            if (processedFiles <= estimatedFilesToProcess) {
                                fileIngestProgressBar.progress(file.getName(), (int) processedFiles);
                            } else {
                                fileIngestProgressBar.progress(file.getName(), (int) estimatedFilesToProcess);
                            }
                            filesInProgress.add(file.getName());
                        }
                    }

                    /**
                     * Run the file through the modules in the pipeline.
                     */
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.performTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors, file);
                    }

                    if (usingNetBeansGUI && !jobCancelled) {
                        synchronized (fileIngestProgressLock) {
                            /**
                             * Update the file ingest progress bar again, in
                             * case the file was being displayed.
                             */
                            filesInProgress.remove(file.getName());
                            if (filesInProgress.size() > 0) {
                                fileIngestProgressBar.progress(filesInProgress.get(0));
                            } else {
                                fileIngestProgressBar.progress("");
                            }
                        }
                    }
                }
                fileIngestPipelinesQueue.put(pipeline);
            }
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, String.format("Unexpected interrupt of file ingest thread during execution of file ingest job (file obj ID = %d)", task.getFileId()), ex);
            Thread.currentThread().interrupt();
        } finally {
            taskScheduler.notifyTaskCompleted(task);
            checkForStageCompleted();
        }
    }

    /**
     * Passes a data artifact from the data source for the ingest job through
     * the data artifact ingest module pipeline.
     *
     * @param task A data artifact ingest task encapsulating the data artifact
     *             and the data artifact ingest pipeline to use to execute the
     *             task.
     */
    void execute(DataArtifactIngestTask task) {
        try {
            if (!isCancelled() && !artifactIngestPipeline.isEmpty()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(artifactIngestPipeline.performTask(task));
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
     * Passes an analyisis result from the data source for the ingest job
     * through the analysis result ingest module pipeline.
     *
     * @param task An analysis result ingest task encapsulating the analysis
     *             result and the analysis result ingest pipeline to use to
     *             execute the task.
     */
    void execute(AnalysisResultIngestTask task) {
        try {
            if (!isCancelled() && !resultIngestPipeline.isEmpty()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(resultIngestPipeline.performTask(task));
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
     * Adds some streamed files for analysis as part of a streaming mode ingest
     * job.
     *
     * @param fileObjIds The object IDs of the files.
     */
    void addStreamedFiles(List<Long> fileObjIds) {
        if (hasFileIngestModules()) {
            if (stage.equals(IngestJobStage.STREAMED_FILE_ANALYSIS_ONLY)) {
                IngestJobExecutor.taskScheduler.scheduleStreamedFileIngestTasks(this, fileObjIds);
            } else {
                logErrorMessage(Level.SEVERE, "Adding streaming files to job during stage " + stage.toString() + " not supported");
            }
        }
    }

    /**
     * Adds additional files (e.g., extracted or carved files) for analysis.
     *
     * @param files A list of the files to add.
     */
    void addFiles(List<AbstractFile> files) {
        if (stage.equals(IngestJobStage.STREAMED_FILE_ANALYSIS_ONLY)
                || stage.equals(IngestJobStage.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS)) {
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
     * Adds data artifacts for analysis.
     *
     * @param artifacts
     */
    void addDataArtifacts(List<DataArtifact> artifacts) {
        List<DataArtifact> artifactsToAnalyze = new ArrayList<>(artifacts);
        if (stage.equals(IngestJobStage.STREAMED_FILE_ANALYSIS_ONLY)
                || stage.equals(IngestJobStage.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS)
                || stage.equals(IngestJobStage.LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS)) {
            taskScheduler.scheduleDataArtifactIngestTasks(this, artifactsToAnalyze);
        } else {
            logErrorMessage(Level.SEVERE, "Adding data artifacts to job during stage " + stage.toString() + " not supported");
        }

        /**
         * The intended clients of this method are ingest modules running code
         * in an ingest thread that is holding a reference to a "primary" ingest
         * task that was the source of the data artifacts, in which case a
         * completion check would not be necessary, so this is a bit of
         * defensive programming.
         */
        checkForStageCompleted();
    }

    /**
     * Adds analysis results for analysis.
     *
     * @param results The analysis results.
     */
    void addAnalysisResults(List<AnalysisResult> results) {
        List<AnalysisResult> resultsToAnalyze = new ArrayList<>(results);
        if (stage.equals(IngestJobStage.STREAMED_FILE_ANALYSIS_ONLY)
                || stage.equals(IngestJobStage.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS)
                || stage.equals(IngestJobStage.LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS)) {
            taskScheduler.scheduleAnalysisResultIngestTasks(this, resultsToAnalyze);
        } else {
            logErrorMessage(Level.SEVERE, "Adding analysis results to job during stage " + stage.toString() + " not supported");
        }

        /**
         * The intended clients of this method are ingest modules running code
         * in an ingest thread that is holding a reference to a "primary" ingest
         * task that was the source of the analysis results, in which case a
         * completion check would not be necessary, so this is a bit of
         * defensive programming.
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
        if (usingNetBeansGUI && !jobCancelled) {
            synchronized (dataSourceIngestProgressLock) {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.setDisplayName(displayName);
                }
            }
        }
    }

    /**
     * Switches the current data source level ingest progress bar to determinate
     * mode. This should be called if the total work units to process the data
     * source is known.
     *
     * @param workUnits Total number of work units for the processing of the
     *                  data source.
     */
    void switchDataSourceIngestProgressBarToDeterminate(int workUnits) {
        if (usingNetBeansGUI && !jobCancelled) {
            synchronized (dataSourceIngestProgressLock) {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.switchToDeterminate(workUnits);
                }
            }
        }
    }

    /**
     * Switches the current data source level ingest progress bar to
     * indeterminate mode. This should be called if the total work units to
     * process the data source is unknown.
     */
    void switchDataSourceIngestProgressBarToIndeterminate() {
        if (usingNetBeansGUI && !jobCancelled) {
            synchronized (dataSourceIngestProgressLock) {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.switchToIndeterminate();
                }
            }
        }
    }

    /**
     * Updates the current data source level ingest progress bar with the number
     * of work units performed, if in the determinate mode.
     *
     * @param workUnits Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(int workUnits) {
        if (usingNetBeansGUI && !jobCancelled) {
            synchronized (dataSourceIngestProgressLock) {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress("", workUnits);
                }
            }
        }
    }

    /**
     * Updates the current data source level ingest progress bar with a new task
     * name, where the task name is the "subtitle" under the display name.
     *
     * @param currentTask The task name.
     */
    void advanceDataSourceIngestProgressBar(String currentTask) {
        if (usingNetBeansGUI && !jobCancelled) {
            synchronized (dataSourceIngestProgressLock) {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress(currentTask);
                }
            }
        }
    }

    /**
     * Updates the current data source level ingest progress bar with a new task
     * name and the number of work units performed, if in the determinate mode.
     * The task name is the "subtitle" under the display name.
     *
     * @param currentTask The task name.
     * @param workUnits   Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(String currentTask, int workUnits) {
        if (usingNetBeansGUI && !jobCancelled) {
            synchronized (dataSourceIngestProgressLock) {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress(currentTask, workUnits);
                }
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
        return currentDataSourceIngestModuleCancelled;
    }

    /**
     * Rescinds a temporary cancellation of data source level ingest that was
     * used to stop a single data source level ingest module for this job.
     *
     * @param moduleDisplayName The display name of the module that was stopped.
     */
    void currentDataSourceIngestModuleCancellationCompleted(String moduleDisplayName) {
        currentDataSourceIngestModuleCancelled = false;
        cancelledDataSourceIngestModules.add(moduleDisplayName);

        if (usingNetBeansGUI) {
            /**
             * A new progress bar must be created because the cancel button of
             * the previously constructed component is disabled by NetBeans when
             * the user selects the "OK" button of the cancellation confirmation
             * dialog popped up by NetBeans when the progress bar cancel button
             * is pressed.
             */
            synchronized (dataSourceIngestProgressLock) {
                dataSourceIngestProgressBar.finish();
                dataSourceIngestProgressBar = null;
                startDataSourceIngestProgressBar();
            }
        }
    }

    /**
     * Gets the currently running data source level ingest module for this job.
     *
     * @return The currently running module, may be null.
     */
    DataSourceIngestPipeline.DataSourcePipelineModule getCurrentDataSourceIngestModule() {
        if (currentDataSourceIngestPipeline != null) {
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
        currentDataSourceIngestModuleCancelled = true;
    }

    /**
     * Requests cancellation of ingest, i.e., a shutdown of the data source
     * level and file level ingest pipelines.
     *
     * @param reason The cancellation reason.
     */
    void cancel(IngestJob.CancellationReason reason) {
        jobCancelled = true;
        cancellationReason = reason;
        IngestJobExecutor.taskScheduler.cancelPendingFileTasksForIngestJob(getIngestJobId());

        if (usingNetBeansGUI) {
            synchronized (dataSourceIngestProgressLock) {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.setDisplayName(NbBundle.getMessage(getClass(), "IngestJob.progress.dataSourceIngest.initialDisplayName", dataSource.getName()));
                    dataSourceIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
            }

            synchronized (this.fileIngestProgressLock) {
                if (null != this.fileIngestProgressBar) {
                    this.fileIngestProgressBar.setDisplayName(NbBundle.getMessage(getClass(), "IngestJob.progress.fileIngest.displayName", dataSource.getName()));
                    this.fileIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
            }
        }

        synchronized (threadRegistrationLock) {
            for (Thread thread : pausedIngestThreads) {
                thread.interrupt();
            }
            pausedIngestThreads.clear();
        }

        /*
         * If a data source had no tasks in progress it may now be complete.
         */
        checkForStageCompleted();
    }

    /**
     * Queries whether or not cancellation, i.e., a shut down of the data source
     * level and file level ingest pipelines for this job, has been requested.
     *
     * @return True or false.
     */
    boolean isCancelled() {
        return jobCancelled;
    }

    /**
     * Gets the reason this job was cancelled.
     *
     * @return The cancellation reason, may be not cancelled.
     */
    IngestJob.CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    /**
     * Writes an info message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param message The message.
     */
    private void logInfoMessage(String message) {
        logger.log(Level.INFO, String.format("%s (data source = %s, object Id = %d, job id = %d)", message, dataSource.getName(), dataSource.getId(), getIngestJobId())); //NON-NLS        
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
        logger.log(level, String.format("%s (data source = %s, object Id = %d, ingest job id = %d)", message, dataSource.getName(), dataSource.getId(), getIngestJobId()), throwable); //NON-NLS
    }

    /**
     * Writes an error message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param level   The logging level for the message.
     * @param message The message.
     */
    private void logErrorMessage(Level level, String message) {
        logger.log(level, String.format("%s (data source = %s, object Id = %d, ingest job id %d)", message, dataSource.getName(), dataSource.getId(), getIngestJobId())); //NON-NLS
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
            logErrorMessage(Level.SEVERE, String.format("%s experienced an error during analysis while processing file %s, object ID %d", error.getModuleDisplayName(), file.getName(), file.getId()), error.getThrowable()); //NON-NLS
        }
    }

    /**
     * Gets a snapshot of some basic diagnostic statistics.
     *
     * @param includeIngestTasksSnapshot Whether or not to include ingest task
     *                                   stats in the snapshot.
     *
     * @return The snapshot.
     */
    Snapshot getDiagnosticStatsSnapshot(boolean includeIngestTasksSnapshot) {
        /*
         * Determine whether file ingest is running at the time of this snapshot
         * and determine the earliest file ingest module pipeline start time, if
         * file ingest was started at all.
         */
        boolean fileIngestRunning = false;
        Date fileIngestStartTime = null;
        for (FileIngestPipeline pipeline : fileIngestPipelines) {
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
        IngestTasksSnapshot tasksSnapshot = null;
        if (includeIngestTasksSnapshot) {
            synchronized (fileIngestProgressLock) {
                processedFilesCount = processedFiles;
                estimatedFilesToProcessCount = estimatedFilesToProcess;
                snapShotTime = new Date().getTime();
            }
            tasksSnapshot = taskScheduler.getTasksSnapshotForJob(getIngestJobId());
        }

        return new Snapshot(dataSource.getName(),
                getIngestJobId(), createTime,
                getCurrentDataSourceIngestModule(),
                fileIngestRunning, fileIngestStartTime,
                jobCancelled, cancellationReason, cancelledDataSourceIngestModules,
                processedFilesCount, estimatedFilesToProcessCount, snapShotTime, tasksSnapshot);
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
