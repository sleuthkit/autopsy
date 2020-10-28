/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.integrationtesting;

import java.io.File;
import org.sleuthkit.autopsy.integrationtesting.config.TestSuiteConfig;
import org.sleuthkit.autopsy.integrationtesting.config.IntegrationTestConfig;
import org.sleuthkit.autopsy.integrationtesting.config.IntegrationCaseType;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.cxf.common.util.CollectionUtils;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.autopsy.datasourceprocessors.DataSourceProcessorUtility;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.integrationtesting.DiffService.DiffServiceException;
import org.sleuthkit.autopsy.integrationtesting.config.ConfigDeserializer;
import org.sleuthkit.autopsy.integrationtesting.config.EnvConfig;
import org.sleuthkit.autopsy.integrationtesting.config.TestingConfig;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.autopsy.testutils.TestUtilsException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Main entry point for running integration tests. Handles processing
 * parameters, ingesting data sources for cases, and running items implementing
 * IntegrationTests.
 */
public class IntegrationTestService {

    /**
     * An exception thrown when there is a diff.
     */
    public static class IntegrationTestDiffException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Main constructor.
         *
         * @param message The message for the exception.
         */
        public IntegrationTestDiffException(String message) {
            super(message);
        }
    }

    /**
     * An error that prevented the normal operation of the integration test
     * service.
     */
    public static class IntegrationTestServiceException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Main constructor.
         *
         * @param message The message.
         */
        public IntegrationTestServiceException(String message) {
            super(message);
        }

        /**
         * Main constructor accepting message and exception.
         *
         * @param message The message for the exception.
         * @param exception The inner exception.
         */
        public IntegrationTestServiceException(String message, Throwable exception) {
            super(message, exception);
        }

    }

    private static final Logger logger = Logger.getLogger(IntegrationTestService.class.getName());

    private static final ConfigDeserializer configDeserializer = new ConfigDeserializer();
    private static final DiffService diffService = new DiffService();
    private static final ConfigurationModuleManager configurationModuleManager = new ConfigurationModuleManager();

    /**
     * Main entry point for running all integration tests.
     */
    public void runIntegrationTests() throws IntegrationTestDiffException, IntegrationTestServiceException {
        IntegrationTestConfig config;
        try {
            config = configDeserializer.getIntegrationTestConfig();
        } catch (IOException ex) {
            throw new IntegrationTestServiceException("There was an error processing integration test config", ex);
        }

        EnvConfig envConfig = config.getEnvConfig();

        // iterate through test suites if any exist
        if (!CollectionUtils.isEmpty(config.getTestSuites())) {
            for (TestSuiteConfig testSuiteConfig : config.getTestSuites()) {
                for (CaseType caseType : IntegrationCaseType.getCaseTypes(testSuiteConfig.getCaseTypes())) {
                    try {
                        runIntegrationTestSuite(envConfig, caseType, testSuiteConfig);
                    } catch (CaseActionException | IllegalStateException | NoCurrentCaseException | IllegalArgumentException ex) {
                        logger.log(Level.SEVERE, "There was an error working with current case: " + testSuiteConfig.getName(), ex);
                    }
                }
            }

            String goldPath = PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getRootGoldPath());
            String outputPath = PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getRootTestOutputPath());
            String diffPath = PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getDiffOutputPath());

            try {
                // write diff to file if requested
                if (writeDiff(outputPath, goldPath, diffPath)) {
                    // if there is a diff, throw an exception accordingly
                    throw new IntegrationTestDiffException(String.format("There was a diff between the integration test gold data: %s "
                            + "and the current iteration output data: %s.  Diff file created at %s.",
                            goldPath, outputPath, diffPath));
                }
            } catch (DiffServiceException ex) {
                throw new IntegrationTestServiceException("There was an error while trying to diff output with gold data.", ex);
            }
        }
    }

    /**
     * Runs a single test suite.
     *
     * @param envConfig The integrationt test environment config.
     * @param caseType The type of case (single user, multi user).
     * @param testSuiteConfig The configuration for the case.
     */
    private void runIntegrationTestSuite(EnvConfig envConfig, CaseType caseType, TestSuiteConfig testSuiteConfig) throws CaseActionException, NoCurrentCaseException, IllegalStateException, IllegalArgumentException {

        String caseName = testSuiteConfig.getName();

        // create an autopsy case for each case in the config and for each case type for the specified case.
        // then run ingest for the case.
        Case autopsyCase = createCaseWithDataSources(
                envConfig.getWorkingDirectory(),
                envConfig.getRootCaseOutputPath(),
                caseName,
                caseType,
                testSuiteConfig.getDataSources());
        if (autopsyCase == null || autopsyCase != Case.getCurrentCase()) {
            throw new IllegalStateException(
                    String.format("Case was not properly ingested or setup correctly for environment.  Returned case is %s and current case is %s.",
                            autopsyCase, Case.getCurrentCase()));
        }
        // run configuration modules and get result
        Pair<IngestJobSettings, List<ConfigurationModule<?>>> configurationResult
                = configurationModuleManager.runConfigurationModules(caseName, testSuiteConfig.getConfigurationModules());
        IngestJobSettings ingestSettings = configurationResult.first();
        List<ConfigurationModule<?>> configModules = configurationResult.second();
        // run ingest with ingest settings derived from configuration modules.
        runIngest(autopsyCase, ingestSettings, caseName);
        // once ingested, run integration tests to produce output.
        OutputResults results = runIntegrationTests(testSuiteConfig.getIntegrationTests());
        // revert any autopsy environment changes made by configuration modules.
        configurationModuleManager.revertConfigurationModules(configModules);
        String outputFolder = PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getRootTestOutputPath());
        // write the results for the case to a file
        results.serializeToFile(
                envConfig.getUseRelativeOutput() == true
                ? Paths.get(outputFolder, testSuiteConfig.getRelativeOutputPath()).toString()
                : outputFolder,
                testSuiteConfig.getName(),
                caseType
        );

        Case.closeCurrentCase();
    }

    /**
     * Creates a case with the given data sources.
     *
     * @param workingDirectory The base working directory (if caseOutputPath or
     * dataSourcePaths are relative, this is the working directory).
     * @param caseOutputPath The case output path.
     * @param caseName The name of the case (unique case name appends time
     * stamp).
     * @param caseType The type of case (single user / multi user).
     * @param dataSourcePaths The path to the data sources.
     * @return The generated case that is now the current case.
     */
    private Case createCaseWithDataSources(String workingDirectory, String caseOutputPath, String caseName, CaseType caseType, List<String> dataSourcePaths)
            throws CaseActionException, NoCurrentCaseException, IllegalStateException, IllegalArgumentException {

        Case openCase = null;
        String uniqueCaseName = String.format("%s_%s", caseName, TimeStampUtils.createTimeStamp());
        String outputFolder = PathUtil.getAbsolutePath(workingDirectory, caseOutputPath);
        String caseOutputFolder = Paths.get(outputFolder, uniqueCaseName).toString();
        File caseOutputFolderFile = new File(caseOutputFolder);
        if (!caseOutputFolderFile.exists()) {
            caseOutputFolderFile.mkdirs();
        }

        // create a spe
        switch (caseType) {
            case SINGLE_USER_CASE: {
                Case.createAsCurrentCase(
                        Case.CaseType.SINGLE_USER_CASE,
                        caseOutputFolder,
                        new CaseDetails(uniqueCaseName));
                openCase = Case.getCurrentCaseThrows();
            }
            break;

            case MULTI_USER_CASE:
                throw new IllegalArgumentException("Multiuser cases are not currently supported for integration tests");
            default:
                throw new IllegalArgumentException("Unknown case type: " + caseType);
        }

        addDataSourcesToCase(PathUtil.getAbsolutePaths(workingDirectory, dataSourcePaths), caseName);
        return openCase;
    }

    /**
     * Adds the data sources to the current case.
     *
     * @param pathStrings The list of paths for the data sources.
     * @param caseName The name of the case (used for error messages).
     */
    private void addDataSourcesToCase(List<String> pathStrings, String caseName) {
        for (String strPath : pathStrings) {
            Path path = Paths.get(strPath);
            List<AutoIngestDataSourceProcessor> processors = null;
            try {
                processors = DataSourceProcessorUtility.getOrderedListOfDataSourceProcessors(path);
            } catch (AutoIngestDataSourceProcessorException ex) {
                logger.log(Level.WARNING, String.format("There was an error while adding data source: %s to case %s", strPath, caseName));
            }

            if (CollectionUtils.isEmpty(processors)) {
                continue;
            }

            try {
                IngestUtils.addDataSource(processors.get(0), path);
            } catch (TestUtilsException ex) {
                logger.log(Level.WARNING, String.format("There was an error adding datasource at path: " + strPath), ex);
            }
        }
    }

    /**
     * Runs ingest on the current case.
     *
     * @param openCase The currently open case.
     * @param ingestJobSettings The ingest job settings to be used.
     * @param caseName The name of the case to be used for error messages.
     */
    private void runIngest(Case openCase, IngestJobSettings ingestJobSettings, String caseName) {
        try {
            if (CollectionUtils.isEmpty(openCase.getDataSources())) {
                logger.log(Level.WARNING, String.format("No datasources provided in %s. Not running ingest.", caseName));
            } else {
                IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);
            }
        } catch (TskCoreException | TestUtilsException ex) {
            logger.log(Level.WARNING, String.format("There was an error while ingesting datasources for case %s", caseName), ex);
        }
    }

    /**
     * Runs the integration tests and serializes results to disk.
     *
     * @param testSuiteConfig The configuration for a particular case.
     */
    private OutputResults runIntegrationTests(TestingConfig testSuiteConfig) {
        // this will capture output results
        OutputResults results = new OutputResults();

        // run through each IntegrationTestGroup
        for (IntegrationTestGroup testGroup : Lookup.getDefault().lookupAll(IntegrationTestGroup.class)) {

            // if test should not be included in results, skip it.
            if (!testSuiteConfig.hasIncludedTest(testGroup.getClass().getCanonicalName())) {
                continue;
            }

            List<Method> testMethods = Stream.of(testGroup.getClass().getMethods())
                    .filter((method) -> method.getAnnotation(IntegrationTest.class) != null)
                    .collect(Collectors.toList());

            if (CollectionUtils.isEmpty(testMethods)) {
                continue;
            }

            testGroup.setupClass();
            Map<String, Object> parametersMap = testSuiteConfig.getParameters(testGroup.getClass().getCanonicalName());

            for (Method testMethod : testMethods) {
                Object[] parameters = new Object[0];
                // no more than 1 parameter in a test method.
                if (testMethod.getParameters().length > 1) {
                    logger.log(Level.WARNING, String.format("Could not call method %s in class %s.  Multiple parameters cannot be handled.",
                            testMethod.getName(), testGroup.getClass().getCanonicalName()));
                    continue;
                    // if there is a parameter, deserialize parameters to the specified type.
                } else if (testMethod.getParameters().length > 0) {
                    parameters = new Object[]{configDeserializer.convertToObj(parametersMap, testMethod.getParameterTypes()[0])};
                }

                Object serializableResult = runIntegrationTestMethod(testGroup, testMethod, parameters);
                // add the results and capture the package, class, 
                // and method of the test for easy location of failed tests
                results.addResult(
                        testGroup.getClass().getPackage().getName(),
                        testGroup.getClass().getSimpleName(),
                        testMethod.getName(),
                        serializableResult);
            }

            testGroup.tearDownClass();
        }

        return results;
    }

    /**
     * Runs a test method in a test suite on the current case.
     *
     * @param testGroup The test suite to which the method belongs.
     * @param testMethod The java reflection method to run.
     * @param parameters The parameters to use with this method or none/empty
     * array.
     * @return The results of running the method.
     */
    private Object runIntegrationTestMethod(IntegrationTestGroup testGroup, Method testMethod, Object[] parameters) {
        testGroup.setup();

        // run the test method and get the results
        Object serializableResult = null;

        try {
            serializableResult = testMethod.invoke(testGroup, parameters == null ? new Object[0] : parameters);
        } catch (InvocationTargetException ex) {
            // if the underlying method caused an exception, serialize that exception.
            serializableResult = ex.getCause();
        } catch (Exception ex) {
            // any other exception should also be caught as a firewall exception handler.
            logger.log(Level.WARNING,
                    String.format("test method %s in %s could not be properly invoked",
                            testMethod.getName(), testGroup.getClass().getCanonicalName()),
                    ex);

            serializableResult = ex;
        }

        testGroup.tearDown();

        return serializableResult;
    }

    /**
     * Writes any differences found between gold and output to a diff file. Only
     * works if a gold and diff location are specified in the EnvConfig. If
     * there is a diff, throw an exception.
     *
     * @param outputPath Path to yaml output directory.
     * @param goldPath Path to yaml gold directory.
     * @param diffPath Path to where diff should be written if there is a diff.
     * @return True if a diff was created.
     */
    private boolean writeDiff(String outputPath, String goldPath, String diffPath) throws DiffServiceException {
        if (StringUtils.isBlank(goldPath) || StringUtils.isBlank(diffPath)) {
            logger.log(Level.INFO, "gold path or diff output path not specified.  Not creating diff.");
        }

        File goldDir = new File(goldPath);
        if (!goldDir.exists()) {
            logger.log(Level.WARNING, String.format("Gold does not exist at location: %s.  Not creating diff.", goldDir.toString()));
        }

        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            logger.log(Level.WARNING, String.format("Output path does not exist at location: %s.  Not creating diff.", outputDir.toString()));
        }

        String diff = diffService.diffFilesOrDirs(goldDir, outputDir);
        if (StringUtils.isNotBlank(diff)) {
            try {
                FileUtils.writeStringToFile(new File(diffPath), diff, "UTF-8");
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to write diff file to {0}", diffPath);
            }

            return true;
        }

        return false;
    }
}
