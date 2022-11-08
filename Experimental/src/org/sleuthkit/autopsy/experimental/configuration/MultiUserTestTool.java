/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.configuration;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import static org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.datasourceprocessors.AddDataSourceCallback;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSource;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.casemodule.LocalFilesDSProcessor;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobStartResult;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Test tool that creates a multi user case, database, KWS index, runs ingest,
 * etc. If errors are encountered during this process, provides a message
 * regarding the problem and possible causes.
 */
class MultiUserTestTool {

    private static final String CASE_NAME = "Test_Multi_User_Settings";
    private static final Logger LOGGER = Logger.getLogger(MultiUserTestTool.class.getName());
    private static final String TEST_FILE_NAME = "AutopsyTempFile";
    private static final Object INGEST_LOCK = new Object();
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    static final String MULTI_USER_TEST_SUCCESSFUL = NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.Success");

    private MultiUserTestTool() {
    }

    @NbBundle.Messages({
        "MultiUserTestTool.unableToCreateCase=Unable to create case",
        "MultiUserTestTool.unableToInitializeDatabase=Case database was not successfully initialized",
        "MultiUserTestTool.unableToReadDatabase=Unable to read from case database",
        "MultiUserTestTool.unableCreatFile=Unable to create a file in case output directory",
        "MultiUserTestTool.unableAddFileAsDataSource=Unable to add test file as data source to case",
        "MultiUserTestTool.unableToReadTestFileFromDatabase=Unable to read test file info from case database",
        "MultiUserTestTool.unableToInitializeFilTypeDetector=Unable to initialize File Type Detector",
        "MultiUserTestTool.unableToUpdateKWSIndex=Unable to write to Keyword Search index",
        "MultiUserTestTool.unableToRunIngest=Unable to run ingest on test data source",
        "MultiUserTestTool.unexpectedError=Unexpected error while performing Multi User test",
        "# {0} - serviceName",
        "MultiUserTestTool.serviceDown=Multi User service is down: {0}",
        "# {0} - serviceName",
        "MultiUserTestTool.unableToCheckService=Unable to check Multi User service state: {0}"
    })
    static String runTest(String rootOutputDirectory) {

        // run standard tests for all services. this detects many problems sooner.
        try {
            if (!isServiceUp(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString())) {
                return NbBundle.getMessage(MultiUserTestTool.class, "MultiUserTestTool.serviceDown", ServicesMonitor.Service.REMOTE_CASE_DATABASE.getDisplayName());
            }
        } catch (ServicesMonitor.ServicesMonitorException ex) {
            return NbBundle.getMessage(MultiUserTestTool.class, "MultiUserTestTool.unableToCheckService",
                    ServicesMonitor.Service.REMOTE_CASE_DATABASE.getDisplayName() + ". " + ex.getMessage());
        }

        try {
            if (!isServiceUp(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString())) {
                return NbBundle.getMessage(MultiUserTestTool.class, "MultiUserTestTool.serviceDown", ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.getDisplayName());
            }
        } catch (ServicesMonitor.ServicesMonitorException ex) {
            return NbBundle.getMessage(MultiUserTestTool.class, "MultiUserTestTool.unableToCheckService",
                    ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.getDisplayName() + ". " + ex.getMessage());
        }

        try {
            if (!isServiceUp(ServicesMonitor.Service.MESSAGING.toString())) {
                return NbBundle.getMessage(MultiUserTestTool.class, "MultiUserTestTool.serviceDown", ServicesMonitor.Service.MESSAGING.getDisplayName());
            }
        } catch (ServicesMonitor.ServicesMonitorException ex) {
            return NbBundle.getMessage(MultiUserTestTool.class, "MultiUserTestTool.unableToCheckService",
                    ServicesMonitor.Service.MESSAGING.getDisplayName() + ". " + ex.getMessage());
        }

        // Create a case in the output folder.
        Case caseForJob;
        try {
            caseForJob = createCase(CASE_NAME, rootOutputDirectory);
        } catch (CaseActionException ex) {
            LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableToCreateCase(), ex);
            return Bundle.MultiUserTestTool_unableToCreateCase() + ". " + ex.getMessage();
        }

        if (caseForJob == null) {
            LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableToCreateCase());
            return Bundle.MultiUserTestTool_unableToCreateCase();
        }

        try {
            // Verify that DB was created. etc
            String getDatabaseInfoQuery = "select * from tsk_db_info";
            try (SleuthkitCase.CaseDbQuery queryResult = caseForJob.getSleuthkitCase().executeQuery(getDatabaseInfoQuery)) {
                ResultSet resultSet = queryResult.getResultSet();
                // check if we got a result
                if (resultSet.next() == false) {
                    // we got a result so we are able to read from the database
                    return Bundle.MultiUserTestTool_unableToInitializeDatabase();
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableToReadDatabase(), ex);
                return Bundle.MultiUserTestTool_unableToReadDatabase() + ". " + ex.getMessage();
            }

            // Make a text file in TEMP folder
            Path tempFilePath = Paths.get(System.getProperty("java.io.tmpdir"), TEST_FILE_NAME + "_" + TimeStampUtils.createTimeStamp() + ".txt");
            try {
                FileUtils.writeStringToFile(tempFilePath.toFile(), "Test", Charset.forName("UTF-8"));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableCreatFile(), ex);
                return Bundle.MultiUserTestTool_unableCreatFile() + ". " + ex.getMessage();
            }

            //  Add it as a logical file set data source.
            AutoIngestDataSource dataSource = new AutoIngestDataSource("", tempFilePath);
            try {
                String error = runLogicalFilesDSP(caseForJob, dataSource);
                if (!error.isEmpty()) {
                    LOGGER.log(Level.SEVERE, error);
                    return error;
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableAddFileAsDataSource(), ex);
                return Bundle.MultiUserTestTool_unableAddFileAsDataSource() + ". " + ex.getMessage();
            }

            // Verify that Solr was able to create the core and is able to write to it
            FileManager fileManager = caseForJob.getServices().getFileManager();
            List<AbstractFile> listOfFiles = null;
            try {
                listOfFiles = fileManager.findFiles(tempFilePath.toFile().getName());
                if (listOfFiles == null || listOfFiles.isEmpty()) {
                    LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableToReadTestFileFromDatabase());
                    return Bundle.MultiUserTestTool_unableToReadTestFileFromDatabase();
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableToReadTestFileFromDatabase(), ex);
                return Bundle.MultiUserTestTool_unableToReadTestFileFromDatabase() + ". " + ex.getMessage();
            }

            AbstractFile file = listOfFiles.get(0);

            // Set MIME type of the test file (required to test indexing)
            FileTypeDetector fileTypeDetector = null;
            try {
                fileTypeDetector = new FileTypeDetector();
            } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                return Bundle.MultiUserTestTool_unableToInitializeFilTypeDetector() + ". " + ex.getMessage();
            }
            String mimeType = fileTypeDetector.getMIMEType(file);
            file.setMIMEType(mimeType);

            // write to KWS index
            KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
            try {
                kwsService.index(file);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableToUpdateKWSIndex(), ex);
                return Bundle.MultiUserTestTool_unableToUpdateKWSIndex() + ". " + ex.getMessage();
            }

            // Run ingest on that data source and report errors if the modules could not start.           
            try {
                String error = analyze(dataSource);
                if (!error.isEmpty()) {
                    LOGGER.log(Level.SEVERE, error);
                    return error;
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, Bundle.MultiUserTestTool_unableToRunIngest(), ex);
                return Bundle.MultiUserTestTool_unableToRunIngest() + ". " + ex.getMessage();
            }
        } catch (Exception ex) {
            // unexpected exception firewall
            LOGGER.log(Level.SEVERE, "Unexpected error while performing Multi User test", ex);
            return Bundle.MultiUserTestTool_unexpectedError() + ". " + ex.getMessage();
        } finally {
            // Close and delete the case.
            try {
                Case.deleteCurrentCase();
            } catch (CaseActionException ex) {
                // I don't think this should result in the test being marked as "failed" if everyhitng else went well
                LOGGER.log(Level.WARNING, "Unable to delete test case", ex);
            }
        }

        return MULTI_USER_TEST_SUCCESSFUL;
    }

    /**
     * Creates a new multi user case.
     *
     * @param baseCaseName        Case name (will get time stamp appended to it)
     * @param rootOutputDirectory Full path to directory in which the case will
     *                            be created
     *
     * @return Case object
     *
     * @throws CaseActionException
     */
    private static Case createCase(String baseCaseName, String rootOutputDirectory) throws CaseActionException {

        String caseDirectoryPath = rootOutputDirectory + File.separator + baseCaseName + "_" + TimeStampUtils.createTimeStamp();

        // Create the case directory
        Case.createCaseDirectory(caseDirectoryPath, Case.CaseType.MULTI_USER_CASE);

        CaseDetails caseDetails = new CaseDetails(baseCaseName);
        Case.createAsCurrentCase(Case.CaseType.MULTI_USER_CASE, caseDirectoryPath, caseDetails);
        return Case.getCurrentCase();
    }

    /**
     * Passes the data source for the current job Logical Files data source
     * processor that adds it to the case database.
     *
     * @param caseForJob The case
     * @param dataSource The data source.
     *
     * @return Error String if there was an error, empty string if the data
     *         source was added successfully
     *
     * @throws InterruptedException if the thread running the job processing
     *                              task is interrupted while blocked, i.e., if
     *                              ingest is shutting down.
     */
    @NbBundle.Messages({
        "MultiUserTestTool.noContent=Test data source failed to produce content",
        "# {0} - errorMessage",
        "MultiUserTestTool.criticalError=Critical error running data source processor on test data source: {0}"
    })
    private static String runLogicalFilesDSP(Case caseForJob, AutoIngestDataSource dataSource) throws InterruptedException {

        AutoIngestDataSourceProcessor selectedProcessor = new LocalFilesDSProcessor();
        DataSourceProcessorProgressMonitor progressMonitor = new DoNothingDSPProgressMonitor();
        synchronized (INGEST_LOCK) {
            UUID taskId = UUID.randomUUID();
            caseForJob.notifyAddingDataSource(taskId);
            DataSourceProcessorCallback callBack = new AddDataSourceCallback(caseForJob, dataSource, taskId, INGEST_LOCK);
            caseForJob.notifyAddingDataSource(taskId);
            selectedProcessor.process(dataSource.getDeviceId(), dataSource.getPath(), progressMonitor, callBack);
            INGEST_LOCK.wait();

            // at this point we got the content object(s) from the DSP.
            // check whether the data source was processed successfully
            if (dataSource.getContent().isEmpty()) {
                return Bundle.MultiUserTestTool_noContent();
            }

            if ((dataSource.getResultDataSourceProcessorResultCode() == CRITICAL_ERRORS)) {
                for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                    LOGGER.log(Level.SEVERE, "Critical error running data source processor on test data source: {0}", errorMessage);
                }
                return NbBundle.getMessage(MultiUserTestTool.class, "MultiUserTestTool.criticalError", dataSource.getDataSourceProcessorErrorMessages().get(0));
            }

            return "";
        }
    }

    /**
     * Analyzes the data source content returned by the data source processor
     * using the configured set of data source level and file level analysis
     * modules.
     *
     * @param dataSource The data source to analyze.
     *
     * @return Error String if there was an error, empty string if the data
     *         source was analyzed successfully
     *
     * @throws InterruptedException if the thread running the job processing
     *                              task is interrupted while blocked, i.e., if
     *                              auto ingest is shutting down.
     */
    @NbBundle.Messages({
        "# {0} - cancellationReason",
        "MultiUserTestTool.ingestCancelled=Ingest cancelled due to {0}",
        "MultiUserTestTool.startupError=Failed to analyze data source due to ingest job startup error",
        "MultiUserTestTool.errorStartingIngestJob=Ingest manager error while starting ingest job",
        "MultiUserTestTool.ingestSettingsError=Failed to analyze data source due to ingest settings errors"
    })
    private static String analyze(AutoIngestDataSource dataSource) throws InterruptedException {

        LOGGER.log(Level.INFO, "Starting ingest modules analysis for {0} ", dataSource.getPath());
        IngestJobEventListener ingestJobEventListener = new IngestJobEventListener();
        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, ingestJobEventListener);
        try {
            synchronized (INGEST_LOCK) {
                IngestJobSettings ingestJobSettings = new IngestJobSettings(AutoIngestUserPreferences.getAutoModeIngestModuleContextString());
                List<String> settingsWarnings = ingestJobSettings.getWarnings();
                if (settingsWarnings.isEmpty()) {
                    IngestJobStartResult ingestJobStartResult = IngestManager.getInstance().beginIngestJob(dataSource.getContent(), ingestJobSettings);
                    IngestJob ingestJob = ingestJobStartResult.getJob();
                    if (null != ingestJob) {
                        /*
                         * Block until notified by the ingest job event listener
                         * or until interrupted because auto ingest is shutting
                         * down.
                         */
                        INGEST_LOCK.wait();
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
                                return NbBundle.getMessage(MultiUserTestTool.class, "MultiUserTestTool.ingestCancelled", cancellationReason.getDisplayName());
                            }
                        }
                    } else if (!ingestJobStartResult.getModuleErrors().isEmpty()) {
                        for (IngestModuleError error : ingestJobStartResult.getModuleErrors()) {
                            LOGGER.log(Level.SEVERE, String.format("%s ingest module startup error for %s", error.getModuleDisplayName(), dataSource.getPath()), error.getThrowable());
                        }
                        LOGGER.log(Level.SEVERE, "Failed to analyze data source due to ingest job startup error");
                        return Bundle.MultiUserTestTool_startupError();
                    } else {
                        LOGGER.log(Level.SEVERE, String.format("Ingest manager ingest job start error for %s", dataSource.getPath()), ingestJobStartResult.getStartupException());
                        return Bundle.MultiUserTestTool_errorStartingIngestJob();
                    }
                } else {
                    for (String warning : settingsWarnings) {
                        LOGGER.log(Level.SEVERE, "Ingest job settings error for {0}: {1}", new Object[]{dataSource.getPath(), warning});
                    }
                    return Bundle.MultiUserTestTool_ingestSettingsError();
                }
            }
        } finally {
            IngestManager.getInstance().removeIngestJobEventListener(ingestJobEventListener);
        }
        // ingest completed successfully
        return "";
    }

    /**
     * Tests service of interest to verify that it is running.
     *
     * @param serviceName Name of the service.
     *
     * @return True if the service is running, false otherwise.
     *
     * @throws ServicesMonitorException if there is an error querying the
     *                                  services monitor.
     */
    private static boolean isServiceUp(String serviceName) throws ServicesMonitor.ServicesMonitorException {
        return (ServicesMonitor.getInstance().getServiceStatus(serviceName).equals(ServicesMonitor.ServiceStatus.UP.toString()));
    }

    /**
     * A data source processor progress monitor does nothing. There is currently
     * no mechanism for showing or recording data source processor progress
     * during an ingest job.
     */
    private static class DoNothingDSPProgressMonitor implements DataSourceProcessorProgressMonitor {

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
     * An ingest job event listener that allows the job processing task to block
     * until the analysis of a data source by the data source level and file
     * level ingest modules is completed.
     * <p>
     * Note that the ingest job can spawn "child" ingest jobs (e.g., if an
     * embedded virtual machine is found), so the job processing task must
     * remain blocked until ingest is no longer running.
     */
    private static class IngestJobEventListener implements PropertyChangeListener {

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
                    synchronized (INGEST_LOCK) {
                        INGEST_LOCK.notifyAll();
                    }
                }
            }
        }
    };
}
