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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
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

/**
 * Test tool that creates a multi user case, database, KWS index, runs ingest,
 * etc. If errors are encountered during this process, provides a message
 * regarding the problem and possible causes.
 */
class MultiUserTestTool {

    private static final String CASE_NAME = "Test_MU_Settings";
    private static final Logger LOGGER = Logger.getLogger(MultiUserTestTool.class.getName());

    static final String RESULT_SUCCESS = "Success";

    private static final Object INGEST_LOCK = new Object();

    static String runTest(String rootOutputDirectory) {

        // 1 (MH) Creates a case in the output folder. Could be hard coded name/time stamp thing.
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
            // 2 (MH) Verifies that Solr was able to create the core. If any of those steps fail, it gives an error message.
            /*KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, "Fake Keyword Search", "Fake Keyword Preview Text"));
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH, "Output Path", rootOutputDirectory));
            BlackboardArtifact bba = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
            bba.addAttributes(attributes);*/

            // Verifies that DB was created. etc
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

            // 3 (NTH) Makes a text file in a temp folder with just the text "Test" in it. 
            String tempFilePath = caseForJob.getTempDirectory() + File.separator + "Test.txt";
            try {
                FileUtils.writeStringToFile(new File(tempFilePath), "Test", Charset.forName("UTF-8"));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Unable to create a file in case output directory", ex);
                return "Unable to create a file in case output directory";
            }

            // 4 (NTH) Adds it as a logical file set data source.
            AutoIngestDataSource dataSource = new AutoIngestDataSource("", Paths.get(tempFilePath));
            try {
                String error = runLogicalFilesDSP(caseForJob, dataSource);
                if (!error.isEmpty()) {
                    LOGGER.log(Level.SEVERE, error);
                    return error;
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "Unable to add test file as data source to case", ex);
                return "Unable to add test file as data source to case";
            }

            // 5 (NTH) Runs ingest on that data source and reports errors if the modules could not start.
            //} catch (Throwable ex) {
        } finally {
            // 6 (MH) Close and delete the case.
            try {
                Case.deleteCurrentCase();
            } catch (CaseActionException ex) {
                LOGGER.log(Level.SEVERE, "Unable to delete test case", ex);
                return "Unable to delete test case";
            }
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
     * @return Error String if there was an error, empty string if the data source was added successfully
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
}
