/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandlineingest;

import com.google.gson.GsonBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.LifecycleManager;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import static org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
import static org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSource;
import org.sleuthkit.autopsy.datasourceprocessors.AddDataSourceCallback;
import org.sleuthkit.autopsy.datasourceprocessors.DataSourceProcessorUtility;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobStartResult;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.ingest.IngestProfiles;
import org.sleuthkit.autopsy.ingest.IngestProfiles.IngestProfile;
import org.sleuthkit.autopsy.ingest.profile.IngestProfilePaths;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager;
import org.sleuthkit.autopsy.report.infrastructure.ReportGenerator;
import org.sleuthkit.autopsy.report.infrastructure.ReportProgressIndicator;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Allows Autopsy to be invoked with command line arguments. Arguments exist to
 * cause Autopsy to create a case, add a specified data source, run ingest on
 * that data source, list all data sources in the case, and generate reports.
 */
public class CommandLineIngestManager extends CommandLineManager {

    private static final Logger LOGGER = Logger.getLogger(CommandLineIngestManager.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.CANCELLED, IngestManager.IngestJobEvent.COMPLETED);
    private Case caseForJob = null;
    private AutoIngestDataSource dataSource = null;

    static final int CL_SUCCESS = 0;
    static final int CL_RUN_FAILURE = -1;
    static final int CL_PROCESS_FAILURE = -2;

    public CommandLineIngestManager() {
    }

    public void start() {
        new Thread(new JobProcessingTask()).start();
    }

    void stop() {
        stop(CL_SUCCESS);
    }

    void stop(int errorCode) {
        try {
            // close current case if there is one open
            Case.closeCurrentCase();
        } catch (CaseActionException ex) {
            LOGGER.log(Level.WARNING, "Unable to close the case while shutting down command line ingest manager", ex); //NON-NLS
        }

        // shut down Autopsy
        if (errorCode == CL_SUCCESS) {
            LifecycleManager.getDefault().exit();
        } else {
            LifecycleManager.getDefault().exit(errorCode);
        }
    }

    private final class JobProcessingTask implements Runnable {

        private final Object ingestLock;

        private JobProcessingTask() {
            ingestLock = new Object();
            try {
                RuntimeProperties.setRunningWithGUI(false);
                LOGGER.log(Level.INFO, "Set running with desktop GUI runtime property to false");
            } catch (RuntimeProperties.RuntimePropertiesException ex) {
                LOGGER.log(Level.SEVERE, "Failed to set running with desktop GUI runtime property to false", ex);
            }
        }

        /**
         * Requests the list of command line commands from command line options
         * processor and executes the commands one by one.
         */
        @Override
        public void run() {
            LOGGER.log(Level.INFO, "Job processing task started");
            int errorCode = CL_SUCCESS;

            try {
                // read command line inputs
                LOGGER.log(Level.INFO, "Autopsy is running from command line"); //NON-NLS
                List<CommandLineCommand> commands = null;

                // first look up all OptionProcessors and get input data from CommandLineOptionProcessor
                Collection<? extends OptionProcessor> optionProcessors = Lookup.getDefault().lookupAll(OptionProcessor.class);
                Iterator<? extends OptionProcessor> optionsIterator = optionProcessors.iterator();
                while (optionsIterator.hasNext()) {
                    // find CommandLineOptionProcessor
                    OptionProcessor processor = optionsIterator.next();
                    if (processor instanceof CommandLineOptionProcessor) {
                        // check if we are running from command line                       
                        commands = ((CommandLineOptionProcessor) processor).getCommands();
                    }
                }
                try {
                    if (commands == null || commands.isEmpty()) {
                        LOGGER.log(Level.SEVERE, "No command line commands specified");
                        System.out.println("No command line commands specified");
                        errorCode = CL_RUN_FAILURE;
                        return;
                    }

                    // Commands are already stored in order in which they should be executed                
                    for (CommandLineCommand command : commands) {
                        CommandLineCommand.CommandType type = command.getType();
                        switch (type) {
                            case CREATE_CASE:
                            try {
                                LOGGER.log(Level.INFO, "Processing 'Create Case' command");
                                System.out.println("Processing 'Create Case' command");
                                Map<String, String> inputs = command.getInputs();
                                String baseCaseName = inputs.get(CommandLineCommand.InputType.CASE_NAME.name());
                                String rootOutputDirectory = inputs.get(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name());
                                CaseType caseType = CaseType.SINGLE_USER_CASE;
                                String caseTypeString = inputs.get(CommandLineCommand.InputType.CASE_TYPE.name());
                                if (caseTypeString != null && caseTypeString.equalsIgnoreCase(CommandLineOptionProcessor.CASETYPE_MULTI)) {
                                    caseType = CaseType.MULTI_USER_CASE;
                                }
                                caseForJob = createCase(baseCaseName, rootOutputDirectory, caseType);

                                String outputDirPath = getOutputDirPath(caseForJob);
                                OutputGenerator.saveCreateCaseOutput(caseForJob, outputDirPath, baseCaseName);
                            } catch (CaseActionException ex) {
                                String baseCaseName = command.getInputs().get(CommandLineCommand.InputType.CASE_NAME.name());
                                LOGGER.log(Level.SEVERE, "Error creating or opening case " + baseCaseName, ex);
                                System.out.println("Error creating or opening case " + baseCaseName);
                                // Do not process any other commands
                                errorCode = CL_RUN_FAILURE;
                                return;
                            }
                            break;
                            case ADD_DATA_SOURCE:
                                try {
                                LOGGER.log(Level.INFO, "Processing 'Add Data Source' command");
                                System.out.println("Processing 'Add Data Source' command");
                                Map<String, String> inputs = command.getInputs();

                                // open the case, if it hasn't been already opened by CREATE_CASE command
                                if (caseForJob == null) {
                                    // find case output directory by name and open the case
                                    String baseCaseName = inputs.get(CommandLineCommand.InputType.CASE_NAME.name());
                                    String rootOutputDirectory = inputs.get(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name());
                                    caseForJob = openExistingCase(baseCaseName, rootOutputDirectory);
                                }

                                String dataSourcePath = inputs.get(CommandLineCommand.InputType.DATA_SOURCE_PATH.name());
                                dataSource = new AutoIngestDataSource(UUID.randomUUID().toString(), Paths.get(dataSourcePath));
                                runDataSourceProcessor(caseForJob, dataSource);

                                String outputDirPath = getOutputDirPath(caseForJob);
                                OutputGenerator.saveAddDataSourceOutput(caseForJob, dataSource, outputDirPath);
                            } catch (InterruptedException | AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException | CaseActionException ex) {
                                String dataSourcePath = command.getInputs().get(CommandLineCommand.InputType.DATA_SOURCE_PATH.name());
                                LOGGER.log(Level.SEVERE, "Error adding data source " + dataSourcePath, ex);
                                System.out.println("Error adding data source " + dataSourcePath);
                                // Do not process any other commands
                                errorCode = CL_RUN_FAILURE;
                                return;
                            }
                            break;
                            case RUN_INGEST:
                                try {
                                LOGGER.log(Level.INFO, "Processing 'Run Ingest' command");
                                System.out.println("Processing 'Run Ingest' command");
                                Map<String, String> inputs = command.getInputs();

                                // open the case, if it hasn't been already opened by CREATE_CASE command
                                if (caseForJob == null) {
                                    // find case output directory by name and open the case
                                    String baseCaseName = inputs.get(CommandLineCommand.InputType.CASE_NAME.name());
                                    String rootOutputDirectory = inputs.get(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name());
                                    caseForJob = openExistingCase(baseCaseName, rootOutputDirectory);
                                }

                                // populate the AutoIngestDataSource structure, if that hasn't been done by ADD_DATA_SOURCE command
                                if (dataSource == null) {

                                    String dataSourceId = inputs.get(CommandLineCommand.InputType.DATA_SOURCE_ID.name());
                                    Long dataSourceObjId = Long.valueOf(dataSourceId);

                                    // get Content object for the data source
                                    Content content = null;
                                    try {
                                        content = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(dataSourceObjId);
                                    } catch (TskCoreException ex) {
                                        LOGGER.log(Level.SEVERE, "Exception while trying to find data source with object ID " + dataSourceId, ex);
                                        System.out.println("Exception while trying to find data source with object ID " + dataSourceId);
                                        // Do not process any other commands
                                        errorCode = CL_RUN_FAILURE;
                                        return;
                                    }

                                    if (content == null) {
                                        LOGGER.log(Level.SEVERE, "Unable to find data source with object ID {0}", dataSourceId);
                                        System.out.println("Unable to find data source with object ID " + dataSourceId);
                                        // Do not process any other commands
                                        return;
                                    }

                                    // populate the AutoIngestDataSource structure
                                    dataSource = new AutoIngestDataSource("", Paths.get(content.getName()));
                                    List<Content> contentList = Arrays.asList(new Content[]{content});
                                    List<String> errorList = new ArrayList<>();
                                    dataSource.setDataSourceProcessorOutput(NO_ERRORS, errorList, contentList);
                                }

                                // run ingest
                                String ingestProfile = inputs.get(CommandLineCommand.InputType.INGEST_PROFILE_NAME.name());
                                analyze(dataSource, ingestProfile);
                            } catch (InterruptedException | CaseActionException | AnalysisStartupException ex) {
                                String dataSourcePath = command.getInputs().get(CommandLineCommand.InputType.DATA_SOURCE_PATH.name());
                                LOGGER.log(Level.SEVERE, "Error running ingest on data source " + dataSourcePath, ex);
                                System.out.println("Error running ingest on data source " + dataSourcePath);
                                // Do not process any other commands
                                errorCode = CL_RUN_FAILURE;
                                return;
                            }
                            break;

                            case LIST_ALL_DATA_SOURCES:
                                try {
                                LOGGER.log(Level.INFO, "Processing 'List All Data Sources' command");
                                System.out.println("Processing 'List All Data Sources' command");
                                Map<String, String> inputs = command.getInputs();

                                // open the case, if it hasn't been already opened by CREATE_CASE command
                                if (caseForJob == null) {
                                    // find case output directory by name and open the case
                                    String baseCaseName = inputs.get(CommandLineCommand.InputType.CASE_NAME.name());
                                    String rootOutputDirectory = inputs.get(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name());
                                    caseForJob = openExistingCase(baseCaseName, rootOutputDirectory);
                                }

                                String outputDirPath = getOutputDirPath(caseForJob);
                                OutputGenerator.listAllDataSources(caseForJob, outputDirPath);
                            } catch (CaseActionException ex) {
                                String baseCaseName = command.getInputs().get(CommandLineCommand.InputType.CASE_NAME.name());
                                String rootOutputDirectory = command.getInputs().get(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name());
                                String msg = "Error opening case " + baseCaseName + " in directory: " + rootOutputDirectory;
                                LOGGER.log(Level.SEVERE, msg, ex);
                                System.out.println(msg);
                                errorCode = CL_RUN_FAILURE;
                                // Do not process any other commands
                                return;
                            }
                            break;

                            case GENERATE_REPORTS:
                                try {
                                LOGGER.log(Level.INFO, "Processing 'Generate Reports' command");
                                System.out.println("Processing 'Generate Reports' command");
                                Map<String, String> inputs = command.getInputs();

                                // open the case, if it hasn't been already opened by CREATE_CASE command
                                if (caseForJob == null) {
                                    // find case output directory by name and open the case
                                    String baseCaseName = inputs.get(CommandLineCommand.InputType.CASE_NAME.name());
                                    String rootOutputDirectory = inputs.get(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name());
                                    caseForJob = openExistingCase(baseCaseName, rootOutputDirectory);
                                }
                                // generate reports
                                String reportName = inputs.get(CommandLineCommand.InputType.REPORT_PROFILE_NAME.name());
                                if (reportName == null) {
                                    reportName = CommandLineIngestSettingsPanel.getDefaultReportingConfigName();
                                }

                                // generate reports
                                ReportProgressIndicator progressIndicator = new ReportProgressIndicator(new CommandLineProgressIndicator());
                                ReportGenerator generator = new ReportGenerator(reportName, progressIndicator);
                                generator.generateReports();
                            } catch (CaseActionException ex) {
                                String baseCaseName = command.getInputs().get(CommandLineCommand.InputType.CASE_NAME.name());
                                String rootOutputDirectory = command.getInputs().get(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name());
                                String msg = "Error opening case " + baseCaseName + " in directory: " + rootOutputDirectory;
                                LOGGER.log(Level.SEVERE, msg, ex);
                                System.out.println(msg);
                                errorCode = CL_RUN_FAILURE;
                                // Do not process any other commands
                                return;
                            } catch (Exception ex) {
                                String msg = "An exception occurred while generating report: " + ex.getMessage();
                                LOGGER.log(Level.WARNING, msg, ex);
                                System.out.println(msg);
                                errorCode = CL_RUN_FAILURE;
                                // Do not process any other commands
                                return;
                            }
                            break;
                            case LIST_ALL_INGEST_PROFILES:
                                List<IngestProfile> profiles = IngestProfiles.getIngestProfiles();
                                GsonBuilder gb = new GsonBuilder();
                                System.out.println("Listing ingest profiles");
                                for (IngestProfile profile : profiles) {
                                    String jsonText = gb.create().toJson(profile);
                                    System.out.println(jsonText);
                                }
                                System.out.println("Ingest profile list complete");
                                break;
                            default:
                                break;
                        }
                    }
                } catch (Throwable ex) {
                    /*
                     * Unexpected runtime exceptions firewall. This task is
                     * designed to be able to be run in an executor service
                     * thread pool without calling get() on the task's
                     * Future<Void>, so this ensures that such errors get
                     * logged.
                     */
                    LOGGER.log(Level.SEVERE, "Unexpected error", ex);
                    System.out.println("Unexpected error. Exiting...");
                    errorCode = CL_RUN_FAILURE;
                } finally {
                    try {
                        Case.closeCurrentCase();
                    } catch (CaseActionException ex) {
                        LOGGER.log(Level.WARNING, "Exception while closing case", ex);
                        System.out.println("Exception while closing case");
                    }
                }

            } finally {
                LOGGER.log(Level.INFO, "Job processing task finished");
                System.out.println("Job processing task finished");

                // shut down Autopsy
                stop(errorCode);
            }
        }

        /**
         * Passes the data source for the current job through a data source
         * processor that adds it to the case database.
         *
         * @param caseForJob The case
         * @param dataSource The data source.
         *
         * @throws AutoIngestDataSourceProcessorException if there was a DSP
         *                                                processing error.
         *
         * @throws InterruptedException                   running the job
         *                                                processing task while
         *                                                blocking, i.e., if
         *                                                auto ingest is
         *                                                shutting down.
         */
        private void runDataSourceProcessor(Case caseForJob, AutoIngestDataSource dataSource) throws InterruptedException, AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException {

            LOGGER.log(Level.INFO, "Adding data source {0} ", dataSource.getPath().toString());

            // Get an ordered list of data source processors to try
            List<AutoIngestDataSourceProcessor> validDataSourceProcessors;
            try {
                validDataSourceProcessors = DataSourceProcessorUtility.getOrderedListOfDataSourceProcessors(dataSource.getPath());
            } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
                LOGGER.log(Level.SEVERE, "Exception while determining best data source processor for {0}", dataSource.getPath());
                // rethrow the exception. 
                throw ex;
            }

            // did we find a data source processor that can process the data source
            if (validDataSourceProcessors.isEmpty()) {
                // This should never happen. We should add all unsupported data sources as logical files.
                LOGGER.log(Level.SEVERE, "Unsupported data source {0}", dataSource.getPath());  // NON-NLS
                return;
            }

            DataSourceProcessorProgressMonitor progressMonitor = new DoNothingDSPProgressMonitor();
            synchronized (ingestLock) {
                // Try each DSP in decreasing order of confidence
                for (AutoIngestDataSourceProcessor selectedProcessor : validDataSourceProcessors) {
                    UUID taskId = UUID.randomUUID();
                    caseForJob.notifyAddingDataSource(taskId);
                    DataSourceProcessorCallback callBack = new AddDataSourceCallback(caseForJob, dataSource, taskId, ingestLock);
                    caseForJob.notifyAddingDataSource(taskId);
                    LOGGER.log(Level.INFO, "Identified data source type for {0} as {1}", new Object[]{dataSource.getPath(), selectedProcessor.getDataSourceType()});
                    selectedProcessor.process(dataSource.getDeviceId(), dataSource.getPath(), progressMonitor, callBack);
                    ingestLock.wait();

                    // at this point we got the content object(s) from the current DSP.
                    // check whether the data source was processed successfully
                    if ((dataSource.getResultDataSourceProcessorResultCode() == CRITICAL_ERRORS)
                            || dataSource.getContent().isEmpty()) {
                        // move onto the the next DSP that can process this data source
                        logDataSourceProcessorResult(dataSource);
                        continue;
                    }

                    logDataSourceProcessorResult(dataSource);
                    return;
                }
                // If we get to this point, none of the processors were successful
                LOGGER.log(Level.SEVERE, "All data source processors failed to process {0}", dataSource.getPath());
                // Throw an exception. It will get caught & handled upstream and will result in AIM auto-pause.
                throw new AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException("Failed to process " + dataSource.getPath() + " with all data source processors");
            }
        }

        /**
         * Logs the results of running a data source processor on the data
         * source for the current job.
         *
         * @param dataSource The data source.
         */
        private void logDataSourceProcessorResult(AutoIngestDataSource dataSource) {

            DataSourceProcessorCallback.DataSourceProcessorResult resultCode = dataSource.getResultDataSourceProcessorResultCode();
            if (null != resultCode) {
                switch (resultCode) {
                    case NO_ERRORS:
                        LOGGER.log(Level.INFO, "Added data source to case");
                        if (dataSource.getContent().isEmpty()) {
                            LOGGER.log(Level.SEVERE, "Data source failed to produce content");
                        }
                        break;

                    case NONCRITICAL_ERRORS:
                        for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                            LOGGER.log(Level.WARNING, "Non-critical error running data source processor for {0}: {1}", new Object[]{dataSource.getPath(), errorMessage});
                        }
                        LOGGER.log(Level.INFO, "Added data source to case");
                        if (dataSource.getContent().isEmpty()) {
                            LOGGER.log(Level.SEVERE, "Data source failed to produce content");
                        }
                        break;

                    case CRITICAL_ERRORS:
                        for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                            LOGGER.log(Level.SEVERE, "Critical error running data source processor for {0}: {1}", new Object[]{dataSource.getPath(), errorMessage});
                        }
                        LOGGER.log(Level.SEVERE, "Failed to add data source to case");
                        break;
                }
            } else {
                LOGGER.log(Level.WARNING, "No result code for data source processor for {0}", dataSource.getPath());
            }
        }

        /**
         * Analyzes the data source content returned by the data source
         * processor using the configured set of data source level and file
         * level analysis modules. If an ingest profile is specified, load that
         * profile (profile = ingest context + ingest filter) for ingest.
         * Otherwise use baseline configuration.
         *
         * @param dataSource        The data source to analyze.
         * @param ingestProfileName Name of ingest profile to use (optional)
         *
         * @throws AnalysisStartupException if there is an error analyzing the
         *                                  data source.
         * @throws InterruptedException     if the thread running the job
         *                                  processing task is interrupted while
         *                                  blocked, i.e., if auto ingest is
         *                                  shutting down.
         */
        private void analyze(AutoIngestDataSource dataSource, String ingestProfileName) throws AnalysisStartupException, InterruptedException {

            LOGGER.log(Level.INFO, "Starting ingest modules analysis for {0} ", dataSource.getPath());

            // configure ingest profile and file filter
            IngestProfiles.IngestProfile selectedProfile = null;
            FilesSet selectedFileSet = null;
            if (!ingestProfileName.isEmpty()) {
                selectedProfile = getSelectedProfile(ingestProfileName);
                if (selectedProfile == null) {
                    // unable to find the user specified profile
                    LOGGER.log(Level.SEVERE, "Unable to find ingest profile: {0}. Ingest cancelled!", ingestProfileName);
                    System.out.println("Unable to find ingest profile: " + ingestProfileName + ". Ingest cancelled!");
                    throw new AnalysisStartupException("Unable to find ingest profile: " + ingestProfileName + ". Ingest cancelled!");
                }

                // get FileSet filter associated with this profile
                selectedFileSet = getSelectedFilter(selectedProfile.getFileIngestFilter());
                if (selectedFileSet == null) {
                    // unable to find the user specified profile
                    LOGGER.log(Level.SEVERE, "Unable to find file filter {0} for ingest profile: {1}. Ingest cancelled!", new Object[]{selectedProfile.getFileIngestFilter(), ingestProfileName});
                    System.out.println("Unable to find file filter " + selectedProfile.getFileIngestFilter() + " for ingest profile: " + ingestProfileName + ". Ingest cancelled!");
                    throw new AnalysisStartupException("Unable to find file filter " + selectedProfile.getFileIngestFilter() + " for ingest profile: " + ingestProfileName + ". Ingest cancelled!");
                }
            }

            IngestJobEventListener ingestJobEventListener = new IngestJobEventListener();
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, ingestJobEventListener);
            try {
                synchronized (ingestLock) {
                    IngestJobSettings ingestJobSettings;
                    if (selectedProfile == null || selectedFileSet == null) {
                        // use baseline configuration
                        ingestJobSettings = new IngestJobSettings(UserPreferences.getCommandLineModeIngestModuleContextString());
                    } else {
                        // load the custom ingest 
                        ingestJobSettings = new IngestJobSettings(IngestProfilePaths.getInstance().getIngestProfilePrefix() + selectedProfile.toString());
                        ingestJobSettings.setFileFilter(selectedFileSet);
                    }

                    List<String> settingsWarnings = ingestJobSettings.getWarnings();
                    if (settingsWarnings.isEmpty()) {
                        IngestJobStartResult ingestJobStartResult = IngestManager.getInstance().beginIngestJob(dataSource.getContent(), ingestJobSettings);
                        IngestJob ingestJob = ingestJobStartResult.getJob();
                        if (null != ingestJob) {
                            /*
                             * Block until notified by the ingest job event
                             * listener or until interrupted because auto ingest
                             * is shutting down. For very small jobs, it is
                             * possible that ingest has completed by the time we
                             * get here, so check periodically in case the event
                             * was missed.
                             */
                            while (IngestManager.getInstance().isIngestRunning()) {
                                ingestLock.wait(60000);  // Check every minute
                            }

                            LOGGER.log(Level.INFO, "Finished ingest modules analysis for {0} ", dataSource.getPath());
                            IngestJob.ProgressSnapshot jobSnapshot = ingestJob.getSnapshot();
                            IngestJob.ProgressSnapshot.DataSourceProcessingSnapshot snapshot = jobSnapshot.getDataSourceProcessingSnapshot();
                            if (!snapshot.isCancelled()) {
                                List<String> cancelledModules = snapshot.getCancelledDataSourceIngestModules();
                                if (!cancelledModules.isEmpty()) {
                                    LOGGER.log(Level.WARNING, String.format("Ingest module(s) cancelled for %s", dataSource.getPath()));
                                    for (String module : snapshot.getCancelledDataSourceIngestModules()) {
                                        LOGGER.log(Level.WARNING, String.format("%s ingest module cancelled for %s", module, dataSource.getPath()));
                                    }
                                }
                                LOGGER.log(Level.INFO, "Analysis of data source completed");
                            } else {
                                LOGGER.log(Level.WARNING, "Analysis of data source cancelled");
                                IngestJob.CancellationReason cancellationReason = snapshot.getCancellationReason();
                                if (IngestJob.CancellationReason.NOT_CANCELLED != cancellationReason && IngestJob.CancellationReason.USER_CANCELLED != cancellationReason) {
                                    throw new AnalysisStartupException(String.format("Analysis cancelled due to %s for %s", cancellationReason.getDisplayName(), dataSource.getPath()));
                                }
                            }
                        } else if (!ingestJobStartResult.getModuleErrors().isEmpty()) {
                            for (IngestModuleError error : ingestJobStartResult.getModuleErrors()) {
                                LOGGER.log(Level.SEVERE, String.format("%s ingest module startup error for %s", error.getModuleDisplayName(), dataSource.getPath()), error.getThrowable());
                            }
                            LOGGER.log(Level.SEVERE, "Failed to analyze data source due to ingest job startup error");
                            throw new AnalysisStartupException(String.format("Error(s) during ingest module startup for %s", dataSource.getPath()));
                        } else {
                            LOGGER.log(Level.SEVERE, String.format("Ingest manager ingest job start error for %s", dataSource.getPath()), ingestJobStartResult.getStartupException());
                            throw new AnalysisStartupException("Ingest manager error starting job", ingestJobStartResult.getStartupException());
                        }
                    } else {
                        for (String warning : settingsWarnings) {
                            LOGGER.log(Level.SEVERE, "Ingest job settings error for {0}: {1}", new Object[]{dataSource.getPath(), warning});
                        }
                        LOGGER.log(Level.SEVERE, "Failed to analyze data source due to settings errors");
                        throw new AnalysisStartupException("Error(s) in ingest job settings");
                    }
                }
            } finally {
                IngestManager.getInstance().removeIngestJobEventListener(ingestJobEventListener);
            }
        }

        /**
         * Gets the specified ingest profile from the list of all existing
         * ingest profiles.
         *
         * @param ingestProfileName Ingest profile name
         *
         * @return IngestProfile object, or NULL if the profile doesn't exist
         */
        private IngestProfiles.IngestProfile getSelectedProfile(String ingestProfileName) {

            IngestProfiles.IngestProfile selectedProfile = null;
            // lookup the profile by name
            for (IngestProfiles.IngestProfile profile : IngestProfiles.getIngestProfiles()) {
                if (profile.toString().equalsIgnoreCase(ingestProfileName)) {
                    // found the profile
                    selectedProfile = profile;
                    break;
                }
            }
            return selectedProfile;
        }

        /**
         * Gets the specified file filter from the list of all existing file
         * filters (custom and standard).
         *
         * @param filterName Name of the file filter
         *
         * @return FilesSet object, or NULL if the filter doesn't exist
         */
        private FilesSet getSelectedFilter(String filterName) {
            try {
                Map<String, FilesSet> fileIngestFilters = FilesSetsManager.getInstance()
                        .getCustomFileIngestFilters();
                for (FilesSet fSet : FilesSetsManager.getStandardFileIngestFilters()) {
                    fileIngestFilters.put(fSet.getName(), fSet);
                }
                return fileIngestFilters.get(filterName);
            } catch (FilesSetsManager.FilesSetsManagerException ex) {
                LOGGER.log(Level.SEVERE, "Failed to get file ingest filter: " + filterName, ex); //NON-NLS
                return null;
            }
        }

        /**
         * An ingest job event listener that allows the job processing task to
         * block until the analysis of a data source by the data source level
         * and file level ingest modules is completed.
         * <p>
         * Note that the ingest job can spawn "child" ingest jobs (e.g., if an
         * embedded virtual machine is found), so the job processing task must
         * remain blocked until ingest is no longer running.
         */
        private class IngestJobEventListener implements PropertyChangeListener {

            /**
             * Listens for local ingest job completed or cancelled events and
             * notifies the job processing thread when such an event occurs and
             * there are no "child" ingest jobs running.
             *
             * @param event
             */
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (AutopsyEvent.SourceType.LOCAL == ((AutopsyEvent) event).getSourceType()) {
                    String eventType = event.getPropertyName();
                    if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString()) || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                        synchronized (ingestLock) {
                            ingestLock.notify();
                        }
                    }
                }
            }
        };

        /**
         * A data source processor progress monitor does nothing. There is
         * currently no mechanism for showing or recording data source processor
         * progress during an ingest job.
         */
        private class DoNothingDSPProgressMonitor implements DataSourceProcessorProgressMonitor {

            /**
             * Does nothing.
             *
             * @param indeterminate
             */
            @Override
            public void setIndeterminate(final boolean indeterminate) {
            }

            /**
             * Does nothing.
             *
             * @param progress
             */
            @Override
            public void setProgress(final int progress) {
            }

            /**
             * Does nothing.
             *
             * @param text
             */
            @Override
            public void setProgressText(final String text) {
            }
        }

        /**
         * Exception type thrown when there is a problem analyzing a data source
         * with data source level and file level ingest modules for an ingest
         * job.
         */
        private final class AnalysisStartupException extends Exception {

            private static final long serialVersionUID = 1L;

            private AnalysisStartupException(String message) {
                super(message);
            }

            private AnalysisStartupException(String message, Throwable cause) {
                super(message, cause);
            }
        }
    }
}
