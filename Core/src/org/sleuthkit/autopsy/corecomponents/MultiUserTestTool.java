/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.util.Lookup;
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
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobStartResult;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Test tool that creates a multi user case, database, KWS index, runs ingest,
 * etc. If errors are encountered during this process, provides a message
 * regarding the problem and possible causes.
 */
class MultiUserTestTool {

    private static final String CASE_NAME = "Test_MU_Settings";
    private static final Logger LOGGER = Logger.getLogger(MultiUserTestTool.class.getName());
    private static final String TEST_FILE_NAME = "Test.txt";
    private static final Object INGEST_LOCK = new Object();

    static final String RESULT_SUCCESS = "Success";

    static String runTest(String rootOutputDirectory) {

        // Create a case in the output folder.
        Case caseForJob;
        try {
            caseForJob = createCase(CASE_NAME, rootOutputDirectory);
        } catch (CaseActionException ex) {
            LOGGER.log(Level.SEVERE, "Unable to create case", ex);
            return "Unable to create case";
        }

        if (caseForJob == null) {
            LOGGER.log(Level.SEVERE, "Error creating multi user case");
            return "Error creating multi user case";
        }

        try {
            // Verify that DB was created. etc
            String getDatabaseInfoQuery = "select * from tsk_db_info";
            try (SleuthkitCase.CaseDbQuery queryResult = caseForJob.getSleuthkitCase().executeQuery(getDatabaseInfoQuery)) {
                ResultSet resultSet = queryResult.getResultSet();
                // check if we got a result
                if (resultSet.next() == false) {
                    // we got a result so we are able to read from the database
                    return "Case database was not successfully initialized";
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Unable to read from case database", ex);
                return "Unable to read from case database";
            }

            // Make a text file in a temp folder with just the text "Test" in it. 
            String tempFilePath = caseForJob.getTempDirectory() + File.separator + TEST_FILE_NAME;
            try {
                FileUtils.writeStringToFile(new File(tempFilePath), "Test", Charset.forName("UTF-8"));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Unable to create a file in case output directory", ex);
                return "Unable to create a file in case output directory";
            }

            //  Add it as a logical file set data source.
            AutoIngestDataSource dataSource = new AutoIngestDataSource("", Paths.get(tempFilePath));
            try {
                String error = runLogicalFilesDSP(caseForJob, dataSource);
                if (!error.isEmpty()) {
                    LOGGER.log(Level.SEVERE, error);
                    return error;
                }
                
                // ELTODO  DELETE
                dataSource = new AutoIngestDataSource("", Paths.get("C:\\TEST\\Inputs\\Test archivedsp\\Test 6.zip"));
                error = runLogicalFilesDSP(caseForJob, dataSource);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "Unable to add test file as data source to case", ex);
                return "Unable to add test file as data source to case";
            }

            // Verify that Solr was able to create the core and is able to write to it
            FileManager fileManager = caseForJob.getServices().getFileManager();
            AbstractFile file = null;
            List<AbstractFile> listOfFiles = null;
            try {
                listOfFiles = fileManager.findFiles(TEST_FILE_NAME);
                if (listOfFiles == null || listOfFiles.isEmpty()) {
                    LOGGER.log(Level.SEVERE, "Unable to read test file info from case database");
                    return "Unable to read test file info from case database";
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Unable to read test file info from case database", ex);
                return "Unable to read test file info from case database";
            }

            file = listOfFiles.get(0);

            // write to KWS index
            KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
            try {
                kwsService.index(file);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Unable to write to Keword Search index", ex);
                return "Unable to write to Keword Search index";
            }

            // Run ingest on that data source and report errors if the modules could not start.           
            try {
                String error = analyze(dataSource);
                if (!error.isEmpty()) {
                    LOGGER.log(Level.SEVERE, error);
                    return error;
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "Unable to run ingest on test data source", ex);
                return "Unable to run ingest on test data source";
            }
            //} catch (Throwable ex) {
        } finally {
            // Close and delete the case.
            /* ELTODO try {
                Case.deleteCurrentCase();
            } catch (CaseActionException ex) {
                LOGGER.log(Level.SEVERE, "Unable to delete test case", ex);
                return "Unable to delete test case";
            } */
        }

        return RESULT_SUCCESS;
    }

    private static Case createCase(String baseCaseName, String rootOutputDirectory) throws CaseActionException {

        String caseDirectoryPath = rootOutputDirectory + File.separator + baseCaseName + "_" + TimeStampUtils.createTimeStamp();

        // Create the case directory
        Case.createCaseDirectory(caseDirectoryPath, Case.CaseType.MULTI_USER_CASE);

        CaseDetails caseDetails = new CaseDetails(baseCaseName);
        Case.createAsCurrentCase(Case.CaseType.MULTI_USER_CASE, caseDirectoryPath, caseDetails);

        Case caseForJob = Case.getCurrentCase();
        return caseForJob;
    }

    /**
     * Passes the data source for the current job Logical Files data source
     * processor that adds it to the case database.
     *
     * @param caseForJob The case
     * @param dataSource The data source.
     *
     * @return Error String if there was an error, empty string if the data
     * source was added successfully
     *
     * @throws InterruptedException if the thread running the job processing
     * task is interrupted while blocked, i.e., if ingest is shutting down.
     */
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
                return "Test data source failed to produce content";
            }

            if ((dataSource.getResultDataSourceProcessorResultCode() == CRITICAL_ERRORS)) {
                for (String errorMessage : dataSource.getDataSourceProcessorErrorMessages()) {
                    LOGGER.log(Level.SEVERE, "Critical error running data source processor on test data source: {0}", errorMessage);
                }
                return "Critical error running data source processor on test data source: " + dataSource.getDataSourceProcessorErrorMessages().get(0);
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
     * source was analyzed successfully
     *
     * @throws InterruptedException if the thread running the job processing
     * task is interrupted while blocked, i.e., if auto ingest is shutting down.
     */
    private static String analyze(AutoIngestDataSource dataSource) throws InterruptedException {

        LOGGER.log(Level.INFO, "Starting ingest modules analysis for {0} ", dataSource.getPath());
        IngestJobEventListener ingestJobEventListener = new IngestJobEventListener();
        IngestManager.getInstance().addIngestJobEventListener(ingestJobEventListener);
        try {
            synchronized (INGEST_LOCK) {
                IngestJobSettings ingestJobSettings = new IngestJobSettings("DummyExecutionContext");
                List<String> settingsWarnings = ingestJobSettings.getWarnings();
                if (settingsWarnings.isEmpty()) {
                    IngestJobStartResult ingestJobStartResult = IngestManager.getInstance().beginIngestJob(dataSource.getContent(), ingestJobSettings);
                    IngestJob ingestJob = ingestJobStartResult.getJob();
                    if (null != ingestJob) {
                        /*
                             * Block until notified by the ingest job event
                             * listener or until interrupted because auto ingest
                             * is shutting down.
                         */
                        INGEST_LOCK.wait();
                        LOGGER.log(Level.INFO, "Finished ingest modules analysis for {0} ", dataSource.getPath());
                        IngestJob.ProgressSnapshot jobSnapshot = ingestJob.getSnapshot();
                        for (IngestJob.ProgressSnapshot.DataSourceProcessingSnapshot snapshot : jobSnapshot.getDataSourceSnapshots()) {
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
                                    return "Ingest cancelled due to " + cancellationReason.getDisplayName();
                                }
                            }
                        }
                    } else if (!ingestJobStartResult.getModuleErrors().isEmpty()) {
                        for (IngestModuleError error : ingestJobStartResult.getModuleErrors()) {
                            LOGGER.log(Level.SEVERE, String.format("%s ingest module startup error for %s", error.getModuleDisplayName(), dataSource.getPath()), error.getThrowable());
                        }
                        LOGGER.log(Level.SEVERE, "Failed to analyze data source due to ingest job startup error");
                        return "Failed to analyze data source due to ingest job startup error";
                    } else {
                        LOGGER.log(Level.SEVERE, String.format("Ingest manager ingest job start error for %s", dataSource.getPath()), ingestJobStartResult.getStartupException());
                        return "Ingest manager error while starting ingest job";
                    }
                } else {
                    for (String warning : settingsWarnings) {
                        LOGGER.log(Level.SEVERE, "Ingest job settings error for {0}: {1}", new Object[]{dataSource.getPath(), warning});
                    }
                    return "Failed to analyze data source due to ingest settings errors";
                }
            }
        } finally {
            IngestManager.getInstance().removeIngestJobEventListener(ingestJobEventListener);
        }
        // ingest completed successfully
        return "";
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
                        INGEST_LOCK.notify();
                    }
                }
            }
        }
    };
}
