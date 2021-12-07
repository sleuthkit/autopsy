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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
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
 * Executes an ingest job by orchestrating the construction, start up, running,
 * and shut down of the ingest module pipelines that perform the ingest tasks
 * for the job.
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
     * These fields are the identity of this object: the ingest job to be
     * executed, the ingest job settings, and the data source to be analyzed by
     * the ingest module pipelines. Optionally, there is a set of files to be
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
     *
     * There are one or more identical file ingest module pipelines, based on
     * the number of file ingest threads in the ingest manager. References to
     * the file ingest pipelines are put into two collections, each with its own
     * purpose. A blocking queue allows file ingest threads to take and return
     * file ingest pipeline copies, as they work through the file ingest tasks
     * for the job. Having the same number of pipelines as file ingest threads
     * ensures that a thread will never be idle, as long as there are file
     * ingest tasks still to do, regardless of the number of ingest jobs in
     * progress. Additionally, a fixed list is used to cycle through the file
     * ingest module pipelines when making ingest progress snapshots.
     *
     * There is at most one data artifact ingest module pipeline.
     *
     * There is at most one analysis result ingest module pipeline.
     */
    private DataSourceIngestPipeline highPriorityDataSourceIngestPipeline;
    private DataSourceIngestPipeline lowPriorityDataSourceIngestPipeline;
    private volatile DataSourceIngestPipeline currentDataSourceIngestPipeline;
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelinesQueue = new LinkedBlockingQueue<>();
    private final List<FileIngestPipeline> fileIngestPipelines = new ArrayList<>();
    private DataArtifactIngestPipeline dataArtifactIngestPipeline;
    private AnalysisResultIngestPipeline analysisResultIngestPipeline;

    /*
     * An ingest job transistion through several states during its execution.
     */
    private static enum IngestJobState {
        /*
         * In this once-per-job state, the ingest module pipelines for the
         * ingest job are constructed per the ingest job settings. This state
         * ends when all of the ingest module pipelines for the ingest job are
         * ready to run.
         */
        PIPELINES_STARTING_UP,
        /*
         * This state is unique to a streaming mode ingest job. In this state,
         * file ingest module pipelines are analyzing files streamed to them via
         * addStreamedFiles(). If the ingest job is configured to have a data
         * artifact and/or analysis result ingest pipeline, those pipelines are
         * also analyzing any data artifacts and/or analysis results generated
         * by the file ingest modules. The transition out of this state occurs
         * when addStreamedDataSource() is called.
         */
        STREAMED_FILE_ANALYSIS_ONLY,
        /*
         * In this state, file ingest module pipelines and/or a pipeline of
         * higher-priority data source level ingest modules are running. If the
         * ingest job is configured to have a data artifact and/or analysis
         * result ingest pipeline, those pipelines are also analyzing any data
         * artifacts and/or analysis results generated by the file and/or data
         * source level ingest modules. The transition out of this state occurs
         * when all of the currently scheduled ingest tasks are completed.
         */
        FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS,
        /**
         * In this state, a pipeline of lower-priority, usually long-running
         * data source level ingest ingest modules is running. If the ingest job
         * is configured to have a data artifact and/or analysis result ingest
         * pipeline, those pipelines are also analyzing any data artifacts
         * and/or analysis results generated by the data source level ingest
         * modules. he transition out of this state occurs when all of the
         * currently scheduled ingest tasks are completed.
         */
        LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS,
        /**
         * In this state, the ingest job executor is shutting down ingest
         * modules pipelines, either during transitions between states in which
         * analysis is performed, or at the end of the ingest job.
         */
        PIPELINES_SHUTTING_DOWN
    };
    private final ReentrantReadWriteLock jobStateLock = new ReentrantReadWriteLock();
    private volatile IngestJobState jobState = IngestJobExecutor.IngestJobState.PIPELINES_STARTING_UP;

    /*
     * The ingest job executor interacts with the ingest task scheduler to
     * create ingest tasks for job. The scheduler queues the ingest tasks for
     * the ingest manager's ingest threads.
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
     *
     * Note that the DataSourceIngestModule interface does not currently have a
     * cancel() API. As a consequence, cancelling an individual data source
     * ingest module requires setting and then unsetting a cancellation flag.
     * Because of this, there is no ironclad guarantee that the correct module
     * will be cancelled. We are relying on the module being long-running to
     * avoid a race condition between module cancellation and the transition of
     * the execution of a data source level ingest task to another module.
     */
    private volatile boolean currentDataSourceIngestModuleCancelled;
    private final List<String> cancelledDataSourceIngestModules = new CopyOnWriteArrayList<>();
    private volatile boolean jobCancelled;
    private volatile IngestJob.CancellationReason cancellationReason = IngestJob.CancellationReason.NOT_CANCELLED;

    /*
     * If running in the NetBeans thick client application version of Autopsy,
     * NetBeans progress handles (i.e., progress bars) are used to display
     * ingest job progress in the lower right hand corner of the main
     * application window.
     *
     * A layer of abstraction to allow alternate representations of progress
     * could be used here, as it is in other places in the application (see
     * implementations and usage of
     * org.sleuthkit.autopsy.progress.ProgressIndicator interface). This would
     * better decouple this object from the application's presentation layer.
     */
    private final boolean usingNetBeansGUI;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private ProgressHandle dataSourceIngestProgressBar;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final List<String> filesInProgress = new ArrayList<>();
    private volatile long estimatedFilesToProcess;
    private volatile long processedFiles;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private ProgressHandle fileIngestProgressBar;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private ProgressHandle artifactIngestProgressBar;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
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
     * construction, start up, running, and shut down of the ingest module
     * pipelines that perform the ingest tasks for the job.
     *
     * @param ingestJob  The ingest job.
     * @param dataSource The data source that is the subject of the ingest job.
     * @param files      A subset of the files from the data source. If the list
     *                   is empty, ALL of the files in the data source are an
     *                   analyzed.
     * @param settings   The ingest job settings.
     *
     * @throws InterruptedException The exception is thrown if the thread in
     *                              which the pipeline is being created is
     *                              interrupted.
     */
    IngestJobExecutor(IngestJob ingestJob, Content dataSource, List<AbstractFile> files, IngestJobSettings settings) throws InterruptedException {
        if (!(dataSource instanceof DataSource)) {
            throw new IllegalArgumentException("Passed dataSource that does not implement the DataSource interface"); //NON-NLS
        }
        // RJCTODO: Refactor so that only the job is passed in and the other params are obtained from the job.
        this.ingestJob = ingestJob;
        this.dataSource = (DataSource) dataSource;
        this.files = new ArrayList<>();
        this.files.addAll(files);
        this.settings = settings;
        usingNetBeansGUI = RuntimeProperties.runningWithGUI();
        createTime = new Date().getTime();
        jobStateLock.writeLock().lock();
        try {
            jobState = IngestJobState.PIPELINES_STARTING_UP;
            createIngestModulePipelines();
        } finally {
            jobStateLock.writeLock().unlock();
        }
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
        Map<String, IngestModuleTemplate> javaResultModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonResultModuleTemplates = new LinkedHashMap<>();
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
            if (template.isAnalysisResultIngestModuleTemplate()) {
                addModuleTemplateToSortingMap(javaResultModuleTemplates, jythonResultModuleTemplates, template);
            }
        }

        /**
         * Take the module templates that have pipeline configuration entries
         * out of the buckets and add them to ingest module pipeline templates
         * in the order prescribed by the pipeline configuration. There is
         * currently no pipeline configuration file support for data artifact or
         * analysis result ingest module pipelines.
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
         * template, Java modules are added before Jython modules, and Core
         * Autopsy modules are added before third party modules.
         */
        addToIngestPipelineTemplate(firstStageDataSourcePipelineTemplate, javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates);
        addToIngestPipelineTemplate(filePipelineTemplate, javaFileModuleTemplates, jythonFileModuleTemplates);
        addToIngestPipelineTemplate(artifactPipelineTemplate, javaArtifactModuleTemplates, jythonArtifactModuleTemplates);
        addToIngestPipelineTemplate(resultsPipelineTemplate, javaResultModuleTemplates, jythonResultModuleTemplates);

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
        dataArtifactIngestPipeline = new DataArtifactIngestPipeline(this, artifactPipelineTemplate);
        analysisResultIngestPipeline = new AnalysisResultIngestPipeline(this, resultsPipelineTemplate);
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
     * Checks to see if there is at least one high priority data source level
     * ingest module to run.
     *
     * @return True or false.
     */
    private boolean hasHighPriorityDataSourceIngestModules() {
        jobStateLock.readLock().lock();
        try {
            return (highPriorityDataSourceIngestPipeline.isEmpty() == false);
        } finally {
            jobStateLock.readLock().unlock();
        }
    }

    /**
     * Checks to see if there is at least one low priority data source level
     * ingest module to run.
     *
     * @return True or false.
     */
    private boolean hasLowPriorityDataSourceIngestModules() {
        jobStateLock.readLock().lock();
        try {
            return (lowPriorityDataSourceIngestPipeline.isEmpty() == false);
        } finally {
            jobStateLock.readLock().unlock();
        }
    }

    /**
     * Checks to see if there is at least one file ingest module to run.
     *
     * @return True or false.
     */
    private boolean hasFileIngestModules() {
        jobStateLock.readLock().lock();
        try {
            if (!fileIngestPipelines.isEmpty()) {
                return !fileIngestPipelines.get(0).isEmpty();
            }
            return false;
        } finally {
            jobStateLock.readLock().unlock();
        }
    }

    /**
     * Checks to see if there is at least one data artifact ingest module to
     * run.
     *
     * @return True or false.
     */
    private boolean hasDataArtifactIngestModules() {
        jobStateLock.readLock().lock();
        try {
            return (dataArtifactIngestPipeline.isEmpty() == false);
        } finally {
            jobStateLock.readLock().unlock();
        }
    }

    /**
     * Checks to see if there is at least one analysis result ingest module to
     * run.
     *
     * @return True or false.
     */
    private boolean hasAnalysisResultIngestModules() {
        jobStateLock.readLock().lock();
        try {
            return (analysisResultIngestPipeline.isEmpty() == false);
        } finally {
            jobStateLock.readLock().unlock();
        }
    }

    /**
     * Determnines which ingest job stage to start in and starts up the ingest
     * module pipelines for all of the stages.
     *
     * @return A collection of ingest module startup errors, empty on success.
     */
    List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = startUpIngestModulePipelines();
        if (errors.isEmpty()) {
            recordIngestJobStartUpInfo();
            if (hasHighPriorityDataSourceIngestModules() || hasFileIngestModules() || hasDataArtifactIngestModules() || hasAnalysisResultIngestModules()) {
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
     * Starts up the ingest module pipelines. Note that ALL of the pipelines are
     * started, so that any and all start up errors can be returned to the
     * caller. It is important to capture all of the errors, because the ingest
     * job will be automatically cancelled, and the errors will be reported to
     * the user. This allows the user to either address the issues, or to
     * disable the modules that can't start up, and attempt the job again.
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
        errors.addAll(startUpIngestModulePipeline(dataArtifactIngestPipeline));
        errors.addAll(startUpIngestModulePipeline(analysisResultIngestPipeline));
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
     * already been added to the case database by the data source processor
     * (DSP) and analysis starts in the file and high priority data source level
     * analysis stage.
     */
    private void startBatchModeAnalysis() {
        jobStateLock.writeLock().lock();
        try {
            logInfoMessage("Starting ingest job in batch mode"); //NON-NLS            
            jobState = IngestJobState.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS;
            currentDataSourceIngestPipeline = highPriorityDataSourceIngestPipeline;

            if (hasFileIngestModules()) {
                /*
                 * Do an estimate of the total number of files to be analyzed.
                 * This will be used to estimate of how many files remain to be
                 * analyzed as each file ingest task is completed. The numbers
                 * are estimates because analysis can add carved files and/or
                 * derived files to the job.
                 */
                if (files.isEmpty()) {
                    /*
                     * Do a count of the files the data source processor (DSP)
                     * has added to the case database.
                     */
                    estimatedFilesToProcess = dataSource.accept(new GetFilesCountVisitor());
                    taskScheduler.scheduleFileIngestTasks(this, files);
                } else {
                    /*
                     * Otherwise, this job is analyzing a user-specified subset
                     * of the files in the data source.
                     */
                    estimatedFilesToProcess = files.size();
                    taskScheduler.scheduleFileIngestTasks(this, Collections.emptyList());
                }
                startFileIngestProgressBar();
            }

            if (hasHighPriorityDataSourceIngestModules()) {
                taskScheduler.scheduleDataSourceIngestTask(this);
                startDataSourceIngestProgressBar();
            }

            if (hasDataArtifactIngestModules()) {
                /*
                 * Note that even if there are no other ingest module pipelines,
                 * analysis of any data artifacts already in the case database
                 * will be performed.
                 */
                taskScheduler.scheduleDataArtifactIngestTasks(this);
                startDataArtifactIngestProgressBar();
            }

            if (hasAnalysisResultIngestModules()) {
                /*
                 * Note that even if there are no other ingest module pipelines,
                 * analysis of any analysis results already in the case database
                 * will be performed.
                 */
                taskScheduler.scheduleAnalysisResultIngestTasks(this);
                startAnalysisResultIngestProgressBar();
            }

            /*
             * Check for stage completion. This is necessary because it is
             * possible that none of the tasks that were just scheduled will
             * actually make it to task execution, due to the file filter or
             * other ingest job settings. If that happens, there will never be
             * another stage completion check for this job in an ingest thread
             * executing an ingest task, so such a job would run forever without
             * a check here.
             */
            checkForStageCompleted();

        } finally {
            jobStateLock.writeLock().unlock();
        }
    }

    /**
     * Starts analysis for a streaming mode ingest job. Streaming mode is
     * typically used to allow a data source processor (DSP) to streams file to
     * this ingest job executor as it adds the files to the case database. This
     * alternative to waiting until the DSP completes its processing allows file
     * level analysis to begin before data source level analysis.
     */
    private void startStreamingModeAnalysis() {
        jobStateLock.writeLock().lock();
        try {
            logInfoMessage("Starting ingest job in streaming mode"); //NON-NLS
            jobState = IngestJobState.STREAMED_FILE_ANALYSIS_ONLY;

            if (hasFileIngestModules()) {
                /*
                 * Start the file ingest progress bar, but do not schedule any
                 * file or data source ingest tasks. File ingest tasks will
                 * instead be scheduled as files are streamed in via
                 * addStreamedFiles(), and a data source ingest task will be
                 * scheduled later, via addStreamedDataSource().
                 *
                 * Note that because estimated files remaining to process still
                 * has its initial value of zero, the file ingest progress bar
                 * will start in the "indeterminate" state. A rough estimate of
                 * the files to be processed will be computed later, when all of
                 * the files have been added to the case database, as signalled
                 * by a call to the addStreamedDataSource().
                 */
                estimatedFilesToProcess = 0;
                startFileIngestProgressBar();
            }

            if (hasDataArtifactIngestModules()) {
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

            if (hasAnalysisResultIngestModules()) {
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
        } finally {
            jobStateLock.writeLock().unlock();
        }
    }

    /**
     * Signals in streaming mode that all of the files have been added to the
     * case database and streamed in to this ingest job executor, and the data
     * source is now ready for analysis.
     */
    void addStreamedDataSource() {
        jobStateLock.writeLock().lock();
        try {
            logInfoMessage("Starting full first stage analysis in streaming mode"); //NON-NLS
            jobState = IngestJobExecutor.IngestJobState.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS;

            if (hasFileIngestModules()) {
                /*
                 * For ingest job progress reporting purposes, do a count of the
                 * files the data source processor has added to the case
                 * database.
                 */
                estimatedFilesToProcess = dataSource.accept(new GetFilesCountVisitor());
                switchFileIngestProgressBarToDeterminate();
            }

            currentDataSourceIngestPipeline = highPriorityDataSourceIngestPipeline;
            if (hasHighPriorityDataSourceIngestModules()) {
                /*
                 * Start a data source level ingest progress bar in the lower
                 * right hand corner of the main application window. The file,
                 * data artifact, and analysis result ingest progress bars were
                 * already started in startStreamingModeAnalysis().
                 */
                startDataSourceIngestProgressBar();

                /*
                 * Schedule a task for the data source.
                 */
                IngestJobExecutor.taskScheduler.scheduleDataSourceIngestTask(this);
            } else {
                /*
                 * If no data source level ingest task is scheduled at this
                 * time, and all of the file level and artifact ingest tasks
                 * scheduled during the initial file streaming stage have
                 * already been executed, there will never be a stage completion
                 * check in an ingest thread executing an ingest task for this
                 * job, so such a job would run forever without a check here.
                 */
                checkForStageCompleted();
            }
        } finally {
            jobStateLock.writeLock().unlock();
        }
    }

    /**
     * Starts low priority data source analysis.
     */
    private void startLowPriorityDataSourceAnalysis() {
        jobStateLock.writeLock().lock();
        try {
            if (hasLowPriorityDataSourceIngestModules()) {
                logInfoMessage("Starting low priority data source analysis"); //NON-NLS
                jobState = IngestJobExecutor.IngestJobState.LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS;
                currentDataSourceIngestPipeline = lowPriorityDataSourceIngestPipeline;
                startDataSourceIngestProgressBar();
                taskScheduler.scheduleDataSourceIngestTask(this);
            }
        } finally {
            jobStateLock.writeLock().unlock();
        }
    }

    /**
     * Starts a NetBeans progress bar for data artifacts analysis in the lower
     * right hand corner of the main application window. The progress bar
     * provides the user with a task cancellation button. Pressing it cancels
     * the entire ingest job. Analysis already completed at the time that
     * cancellation occurs is NOT discarded.
     */
    private void startDataArtifactIngestProgressBar() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                artifactIngestProgressBar = ProgressHandle.createHandle(NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataArtifactIngest.displayName", this.dataSource.getName()), new Cancellable() {
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
     * the entire ingest job. Analysis already completed at the time that
     * cancellation occurs is NOT discarded.
     */
    @NbBundle.Messages({
        "# {0} - data source name",
        "IngestJob_progress_analysisResultIngest_displayName=Analyzing analysis results from {0}"
    })
    private void startAnalysisResultIngestProgressBar() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                resultIngestProgressBar = ProgressHandle.createHandle(Bundle.IngestJob_progress_analysisResultIngest_displayName(dataSource.getName()), new Cancellable() {
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
     * Starts a NetBeans progress bar for data source level analysis in the
     * lower right hand corner of the main application window. The progress bar
     * provides the user with a task cancellation button. Pressing it cancels
     * either the currently running data source level ingest module, or the
     * entire ingest job. Analysis already completed at the time that
     * cancellation occurs is NOT discarded.
     */
    private void startDataSourceIngestProgressBar() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                dataSourceIngestProgressBar = ProgressHandle.createHandle(NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataSourceIngest.initialDisplayName", dataSource.getName()), new Cancellable() {
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
     * Starts a NetBeans progress bar for file analysis in the lower right hand
     * corner of the main application window. The progress bar provides the user
     * with a task cancellation button. Pressing it cancels the entire ingest
     * job. Analysis already completed at the time that cancellation occurs is
     * NOT discarded.
     */
    private void startFileIngestProgressBar() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                fileIngestProgressBar = ProgressHandle.createHandle(NbBundle.getMessage(getClass(), "IngestJob.progress.fileIngest.displayName", dataSource.getName()), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        new Thread(() -> {
                            IngestJobExecutor.this.cancel(IngestJob.CancellationReason.USER_CANCELLED);
                        }).start();
                        return true;
                    }
                });
                fileIngestProgressBar.start();
                fileIngestProgressBar.switchToDeterminate((int) estimatedFilesToProcess);
            });
        }
    }

    /**
     * Finishes the first stage ingest progress bars.
     */
    private void finishFirstStageProgressBars() {
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
     * Checks to see if the ingest tasks for the current stage of this job are
     * completed and does a stage transition if they are.
     */
    private void checkForStageCompleted() {
        jobStateLock.writeLock().lock();
        try {
            if (jobState == IngestJobState.STREAMED_FILE_ANALYSIS_ONLY) {
                return;
            }
            if (taskScheduler.currentTasksAreCompleted(getIngestJobId())) {
                switch (jobState) {
                    case FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS:
                        finishFileAndHighPriorityDataSrcAnalysis();
                        break;
                    case LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS:
                        shutDown();
                        break;
                }
            }
        } finally {
            jobStateLock.writeLock().unlock();
        }
    }

    /**
     * Shuts down the file and high-priority data source level ingest pipelines
     * and progress bars for this job and starts the low-priority data source
     * level analysis stage, if appropriate.
     */
    private void finishFileAndHighPriorityDataSrcAnalysis() {
        jobStateLock.writeLock().lock();
        try {
            jobState = IngestJobState.PIPELINES_SHUTTING_DOWN;
            shutDownIngestModulePipeline(currentDataSourceIngestPipeline);
            while (!fileIngestPipelinesQueue.isEmpty()) {
                FileIngestPipeline pipeline = fileIngestPipelinesQueue.poll();
                shutDownIngestModulePipeline(pipeline);
            }
            finishFirstStageProgressBars();
            logInfoMessage("Finished file and high-priority data source analysis"); //NON-NLS        

            if (!jobCancelled && hasLowPriorityDataSourceIngestModules()) {
                startLowPriorityDataSourceAnalysis();
            } else {
                shutDown();
            }
        } finally {
            jobStateLock.writeLock().unlock();
        }
    }

    /**
     * Shuts down the ingest module pipelines and ingest job progress bars.
     */
    private void shutDown() {
        jobStateLock.writeLock().lock();
        try {
            logInfoMessage("Finished all ingest tasks"); //NON-NLS        
            jobState = IngestJobExecutor.IngestJobState.PIPELINES_SHUTTING_DOWN;
            shutDownIngestModulePipeline(currentDataSourceIngestPipeline);
            shutDownIngestModulePipeline(dataArtifactIngestPipeline);
            shutDownIngestModulePipeline(analysisResultIngestPipeline);

            finishAllProgressBars();

            try {
                if (ingestJobInfo != null) {
                    if (jobCancelled) {
                        ingestJobInfo.setIngestJobStatus(IngestJobStatusType.CANCELLED);
                    } else {
                        ingestJobInfo.setIngestJobStatus(IngestJobStatusType.COMPLETED);
                    }
                    ingestJobInfo.setEndDateTime(new Date());
                }
            } catch (TskCoreException ex) {
                logErrorMessage(Level.WARNING, "Failed to set job end date in case database", ex);
            }

            ingestJob.notifyIngestPipelinesShutDown();
        } finally {
            jobStateLock.writeLock().unlock();
        }
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
     *             the data source ingest pipeline.
     */
    void execute(DataSourceIngestTask task) {
        jobStateLock.readLock().lock();
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
            jobStateLock.readLock().unlock();
            checkForStageCompleted();
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
        jobStateLock.readLock().lock();
        try {
            if (!isCancelled()) {
                FileIngestPipeline pipeline = fileIngestPipelinesQueue.take();
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
                        fileIngestPipelinesQueue.put(pipeline);
                        return;
                    }

                    /**
                     * Run the file through the modules in the file ingest
                     * pipeline.
                     */
                    final String fileName = file.getName();
                    processedFiles++;
                    updateFileIngestProgressForFileTaskStarted(fileName);
                    List<IngestModuleError> errors = new ArrayList<>();
                    errors.addAll(pipeline.performTask(task));
                    if (!errors.isEmpty()) {
                        logIngestModuleErrors(errors, file);
                    }
                    updateFileProgressBarForFileTaskCompleted(fileName);
                }
                fileIngestPipelinesQueue.put(pipeline);
            }
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, String.format("File ingest thread interrupted during execution of file ingest job (file object ID = %d, thread ID = %d)", task.getFileId(), task.getThreadId()), ex);
            Thread.currentThread().interrupt();
        } finally {
            taskScheduler.notifyTaskCompleted(task);
            jobStateLock.readLock().unlock();
            checkForStageCompleted();
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
        jobStateLock.readLock().lock();
        try {
            if (!isCancelled() && !dataArtifactIngestPipeline.isEmpty()) {
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(dataArtifactIngestPipeline.performTask(task));
                if (!errors.isEmpty()) {
                    logIngestModuleErrors(errors);
                }
            }
        } finally {
            taskScheduler.notifyTaskCompleted(task);
            jobStateLock.readLock().unlock();
            checkForStageCompleted();
        }
    }

    /**
     * Passes an analyisis result from the data source for the ingest job
     * through the analysis result ingest module pipeline.
     *
     * @param task An analysis result ingest task encapsulating the analysis
     *             result and the analysis result ingest pipeline.
     */
    void execute(AnalysisResultIngestTask task) {
        jobStateLock.readLock().lock();
        try {
            if (!isCancelled() && !analysisResultIngestPipeline.isEmpty()) {              
                List<IngestModuleError> errors = new ArrayList<>();
                errors.addAll(analysisResultIngestPipeline.performTask(task));
                if (!errors.isEmpty()) {
                    logIngestModuleErrors(errors);
                }             
            }
        } finally {
            taskScheduler.notifyTaskCompleted(task);
            jobStateLock.readLock().unlock();
            checkForStageCompleted();
        }
    }

    /**
     * Streams in files for analysis as part of a streaming mode ingest job.
     *
     * @param fileObjIds The object IDs of the files.
     */
    void addStreamedFiles(List<Long> fileObjIds) {
        if (!isCancelled() && hasFileIngestModules()) {
            if (jobState.equals(IngestJobState.STREAMED_FILE_ANALYSIS_ONLY)) {
                IngestJobExecutor.taskScheduler.scheduleStreamedFileIngestTasks(this, fileObjIds);
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
        if (!isCancelled() && hasFileIngestModules()) {
            if (jobState.equals(IngestJobState.STREAMED_FILE_ANALYSIS_ONLY) || jobState.equals(IngestJobState.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS)) {
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
        if (!isCancelled() && hasDataArtifactIngestModules()) {
            if (jobState.equals(IngestJobState.STREAMED_FILE_ANALYSIS_ONLY) || jobState.equals(IngestJobState.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS) || jobState.equals(IngestJobState.LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS)) {
                taskScheduler.scheduleDataArtifactIngestTasks(this, artifacts);
            } else {
                logErrorMessage(Level.SEVERE, "Attempt to add data artifacts to job during stage " + jobState.toString() + " not supported");
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
        if (!isCancelled() && hasAnalysisResultIngestModules()) {
            if (jobState.equals(IngestJobState.STREAMED_FILE_ANALYSIS_ONLY) || jobState.equals(IngestJobState.FILE_AND_HIGH_PRIORITY_DATA_SRC_LEVEL_ANALYSIS) || jobState.equals(IngestJobState.LOW_PRIORITY_DATA_SRC_LEVEL_ANALYSIS)) {
                taskScheduler.scheduleAnalysisResultIngestTasks(this, results);
            } else {
                logErrorMessage(Level.SEVERE, "Attempt to add analysis results to job during stage " + jobState.toString() + " not supported");
            }
        }
    }

    /**
     * Updates the display name shown on the current data source level ingest
     * progress bar for this job, if the job has not been cancelled. This is
     * intended to be called by data source level ingest modules and the display
     * name should reference the ingest module name.
     *
     * @param displayName The new display name.
     */
    void updateDataSourceIngestProgressBarDisplayName(String displayName) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.setDisplayName(displayName);
                }
            });
        }
    }

    /**
     * Switches the current data source level ingest progress bar to determinate
     * mode, if the job has not been cancelled. This should be called if the
     * total work units to process the data source is known. This is intended to
     * be called by data source level ingest modules in conjunction with
     * updateDataSourceIngestProgressBarDisplayName().
     *
     * @param workUnits Total number of work units for the processing of the
     *                  data source.
     */
    void switchDataSourceIngestProgressBarToDeterminate(int workUnits) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.switchToDeterminate(workUnits);
                }
            });
        }
    }

    /**
     * Switches the current data source level ingest progress bar to
     * indeterminate mode, if the job has not been cancelled. This should be
     * called if the total work units to process the data source is unknown.
     * This is intended to be called by data source level ingest modules in
     * conjunction with updateDataSourceIngestProgressBarDisplayName().
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
     * Updates the current data source level ingest progress bar with the number
     * of work units performed, if in the determinate mode, and the job has not
     * been cancelled. This is intended to be called by data source level ingest
     * modules that have called
     * switchDataSourceIngestProgressBarToDeterminate().
     *
     * @param workUnits Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(int workUnits) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress("", workUnits);
                }
            });
        }
    }

    /**
     * Updates the current data source level ingest progress bar with a new task
     * name, where the task name is the "subtitle" under the display name, if
     * the job has not been cancelled.
     *
     * @param currentTask The task name.
     */
    void advanceDataSourceIngestProgressBar(String currentTask) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress(currentTask);
                }
            });
        }
    }

    /**
     * Updates the current data source level ingest progress bar with a new task
     * name and the number of work units performed, if in the determinate mode,
     * and the job has not been cancelled. The task name is the "subtitle" under
     * the display name.
     *
     * @param currentTask The task name.
     * @param workUnits   Number of work units performed.
     */
    void advanceDataSourceIngestProgressBar(String currentTask, int workUnits) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.progress(currentTask, workUnits);
                }
            });
        }
    }

    /**
     * Switches the file ingest progress bar to determinate mode, using the
     * estimated number of files to process as the number of work units.
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
     * Updates the current file ingest progress bar upon start of analysis of a
     * file, if the job has not been cancelled.
     *
     * @param fileName The name of the file.
     */
    private void updateFileIngestProgressForFileTaskStarted(String fileName) {
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
     * of a file, if the job has not been cancelled.
     *
     * @param fileName The name of the file.
     */
    private void updateFileProgressBarForFileTaskCompleted(String fileName) {
        if (usingNetBeansGUI && !jobCancelled) {
            SwingUtilities.invokeLater(() -> {
                filesInProgress.remove(fileName);
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
     * Displays a "cancelling" message on all of the current ingest message
     * progress bars.
     */
    private void displayCancellingProgressMessages() {
        if (usingNetBeansGUI) {
            SwingUtilities.invokeLater(() -> {
                if (dataSourceIngestProgressBar != null) {
                    dataSourceIngestProgressBar.setDisplayName(NbBundle.getMessage(getClass(), "IngestJob.progress.dataSourceIngest.initialDisplayName", dataSource.getName()));
                    dataSourceIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
                if (fileIngestProgressBar != null) {
                    fileIngestProgressBar.setDisplayName(NbBundle.getMessage(getClass(), "IngestJob.progress.fileIngest.displayName", dataSource.getName()));
                    fileIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
                if (artifactIngestProgressBar != null) {
                    artifactIngestProgressBar.setDisplayName(NbBundle.getMessage(getClass(), "IngestJob.progress.dataArtifactIngest.displayName", dataSource.getName()));
                    artifactIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
                if (resultIngestProgressBar != null) {
                    resultIngestProgressBar.setDisplayName(Bundle.IngestJob_progress_analysisResultIngest_displayName(dataSource.getName()));
                    resultIngestProgressBar.progress(NbBundle.getMessage(getClass(), "IngestJob.progress.cancelling"));
                }
            });
        }
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
            SwingUtilities.invokeLater(() -> {
                /**
                 * A new progress bar must be created because the cancel button
                 * of the previously constructed component is disabled by
                 * NetBeans when the user selects the "OK" button of the
                 * cancellation confirmation dialog popped up by NetBeans when
                 * the progress bar cancel button is pressed.
                 */
                dataSourceIngestProgressBar.finish();
                dataSourceIngestProgressBar = null;
                startDataSourceIngestProgressBar();
            });
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
     * Requests cancellation of the ingest job. All pending ingest tasks for the
     * job will be cancelled, but any tasks already in progress in ingest
     * threads will run to completion. This could take a while if the ingest
     * modules executing the tasks are not checking the ingest job cancellation
     * flag via the ingest joib context.
     *
     * @param reason The cancellation reason.
     */
    void cancel(IngestJob.CancellationReason reason) {
        jobCancelled = true;
        cancellationReason = reason;
        displayCancellingProgressMessages();
        IngestJobExecutor.taskScheduler.cancelPendingFileTasksForIngestJob(getIngestJobId());
        synchronized (threadRegistrationLock) {
            for (Thread thread : pausedIngestThreads) {
                thread.interrupt();
            }
            pausedIngestThreads.clear();
        }
        checkForStageCompleted();
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
     * Writes an info message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param message The message.
     */
    private void logInfoMessage(String message) {
        logger.log(Level.INFO, String.format("%s (data source = %s, data source object Id = %d, job id = %d)", message, dataSource.getName(), dataSource.getId(), getIngestJobId())); //NON-NLS        
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
        logger.log(level, String.format("%s (data source = %s, data source object Id = %d, ingest job id = %d)", message, dataSource.getName(), dataSource.getId(), getIngestJobId()), throwable); //NON-NLS
    }

    /**
     * Writes an error message to the application log that includes the data
     * source name, data source object id, and the job id.
     *
     * @param level   The logging level for the message.
     * @param message The message.
     */
    private void logErrorMessage(Level level, String message) {
        logger.log(level, String.format("%s (data source = %s, data source object Id = %d, ingest job id %d)", message, dataSource.getName(), dataSource.getId(), getIngestJobId())); //NON-NLS
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
        IngestTasksScheduler.IngestTasksSnapshot tasksSnapshot = null;
        if (includeIngestTasksSnapshot) {
            processedFilesCount = processedFiles;
            estimatedFilesToProcessCount = estimatedFilesToProcess;
            snapShotTime = new Date().getTime();
            tasksSnapshot = taskScheduler.getTasksSnapshotForJob(getIngestJobId());
        }
        return new Snapshot(
                dataSource.getName(),
                getIngestJobId(),
                createTime,
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
