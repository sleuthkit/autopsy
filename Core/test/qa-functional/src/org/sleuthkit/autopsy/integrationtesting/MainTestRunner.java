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
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.cxf.common.util.CollectionUtils;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.autopsy.datasourceprocessors.DataSourceProcessorUtility;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.integrationtesting.config.ConfigDeserializer;
import org.sleuthkit.autopsy.integrationtesting.config.EnvConfig;
import org.sleuthkit.autopsy.integrationtesting.config.TestingConfig;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;
import static ucar.unidata.util.Format.i;

/**
 * Main entry point for running integration tests. Handles processing
 * parameters, ingesting data sources for cases, and running items implementing
 * IntegrationTests.
 */
public class MainTestRunner extends TestCase {

    private static final Logger logger = Logger.getLogger(MainTestRunner.class.getName());
    private static final String CONFIG_FILE_KEY = "integrationConfigFile";
    private static final ConfigDeserializer configDeserializer = new ConfigDeserializer();
    private static final ConfigurationModuleManager configurationModuleManager = new ConfigurationModuleManager();

    /**
     * Constructor required by JUnit
     */
    public MainTestRunner(String name) {
        super(name);
    }

    /**
     * Creates suite from particular test cases.
     */
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(MainTestRunner.class).
                clusters(".*").
                enableModules(".*");

        return NbModuleSuite.create(conf.addTest("runIntegrationTests"));
    }

    /**
     * Main entry point for running all integration tests.
     */
    public void runIntegrationTests() {
        // The config file location is specified as a system property.  A config is necessary to run this properly.
        String configFile = System.getProperty(CONFIG_FILE_KEY);
        IntegrationTestConfig config;
        try {
            config = configDeserializer.getConfigFromFile(configFile);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "There was an error processing integration test config at " + configFile, ex);
            return;
        }

        if (config == null) {
            logger.log(Level.WARNING, "No properly formatted config found at " + configFile);
        }

        EnvConfig envConfig = config.getEnvConfig();

        if (!CollectionUtils.isEmpty(config.getTestSuites())) {
            for (TestSuiteConfig testSuiteConfig : config.getTestSuites()) {
                String caseName = testSuiteConfig.getName();

                for (CaseType caseType : IntegrationCaseType.getCaseTypes(testSuiteConfig.getCaseTypes())) {
                    // create an autopsy case for each case in the config and for each case type for the specified case.
                    // then run ingest for the case.
                    Case autopsyCase = createCaseWithDataSources(envConfig, caseName, caseType, testSuiteConfig.getDataSources());

                    if (autopsyCase == null || autopsyCase != Case.getCurrentCase()) {
                        logger.log(Level.WARNING,
                                String.format("Case was not properly ingested or setup correctly for environment.  Case is %s and current case is %s.",
                                        autopsyCase, Case.getCurrentCase()));
                        return;
                    }

                    Pair<IngestJobSettings, List<ConfigurationModule<?>>> configurationResult
                            = configurationModuleManager.runConfigurationModules(caseName, testSuiteConfig);

                    IngestJobSettings ingestSettings = configurationResult.first();
                    List<ConfigurationModule<?>> configModules = configurationResult.second();

                    runIngest(autopsyCase, ingestSettings, caseName);

                    // once ingested, run integration tests to produce output.
                    OutputResults results = runIntegrationTests(testSuiteConfig.getIntegrationTests());

                    configurationModuleManager.revertConfigurationModules(configModules);

                    String outputFolder = PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getRootTestOutputPath());
                            
                    // write the results for the case to a file
                    results.serializeToFile(
                            Paths.get(outputFolder, testSuiteConfig.getRelativeOutputPath()).toString(),
                            testSuiteConfig.getName(),
                            caseType
                    );

                    try {
                        Case.closeCurrentCase();
                    } catch (CaseActionException ex) {
                        logger.log(Level.WARNING, "There was an error while trying to close current case: {0}", caseName);
                        return;
                    }
                }
            }
        }
    }

    private Case createCaseWithDataSources(EnvConfig envConfig, String caseName, CaseType caseType, List<String> dataSourcePaths) {
        Case openCase = null;
        String uniqueCaseName = String.format("%s_%s", caseName, TimeStampUtils.createTimeStamp());
        String outputFolder = PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getRootCaseOutputPath());
        String caseOutputFolder = Paths.get(outputFolder, uniqueCaseName).toString();
        File caseOutputFolderFile = new File(caseOutputFolder);
        if (!caseOutputFolderFile.exists()) {
            caseOutputFolderFile.mkdirs();
        }

        switch (caseType) {
            case SINGLE_USER_CASE: {
                try {
                    Case.createAsCurrentCase(
                            Case.CaseType.SINGLE_USER_CASE,
                            caseOutputFolder,
                            new CaseDetails(uniqueCaseName));
                    openCase = Case.getCurrentCaseThrows();
                } catch (CaseActionException | NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Unable to create integration test case for " + caseName, ex);
                }
            }
            break;
            
            case MULTI_USER_CASE:
            // TODO
            default:
                throw new IllegalArgumentException("Unknown case type: " + caseType);
        }

        if (openCase == null) {
            logger.log(Level.WARNING, String.format("No case could be created for %s of type %s.", caseName, caseType));
            return null;
        }

        addDataSourcesToCase(PathUtil.getAbsolutePaths(envConfig.getWorkingDirectory(), dataSourcePaths), caseName);
        return openCase;
    }

    /**
     * Adds the data sources to the current case.
     *
     * @param pathStrings The list of paths for the data sources.
     * @param caseName The name of the case.
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

            IngestUtils.addDataSource(processors.get(0), path);
        }
    }

    private Case runIngest(Case openCase, IngestJobSettings ingestJobSettings, String caseName) {
        try {
            // IngestJobSettings ingestJobSettings = SETUP_UTIL.setupEnvironment(envConfig, testSuiteConfig);
            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("There was an error while ingesting datasources for case %s", caseName), ex);
        }

        return openCase;
    }

    /**
     * Runs the integration tests and serializes results to disk.
     *
     * @param envConfig The overall config.
     * @param testSuiteConfig The configuration for a particular case.
     * @param caseType The case type (single user / multi user) to create.
     */
    private OutputResults runIntegrationTests(TestingConfig testSuiteConfig) {
        // this will capture output results
        OutputResults results = new OutputResults();

        // run through each ConsumerIntegrationTest
        for (IntegrationTests testGroup : Lookup.getDefault().lookupAll(IntegrationTests.class)) {

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
                if (testMethod.getParameters().length > 1) {
                    throw new IllegalArgumentException(String.format("Could not call method %s in class %s.  Multiple parameters cannot be handled.",
                            testMethod.getName(), testGroup.getClass().getCanonicalName()));
                } else if (testMethod.getParameters().length > 0) {
                    parameters = new Object[]{configDeserializer.convertToObj(parametersMap, testMethod.getParameters().getClass())};
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
     */
    private Object runIntegrationTestMethod(IntegrationTests testGroup, Method testMethod, Object[] parameters) {
        testGroup.setup();

        // run the test method and get the results
        Object serializableResult = null;

        try {
            serializableResult = testMethod.invoke(testGroup, parameters);
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            logger.log(Level.WARNING,
                    String.format("test method %s in %s could not be properly invoked",
                            testMethod.getName(), testGroup.getClass().getCanonicalName()),
                    ex);

            serializableResult = ex;
        } catch (InvocationTargetException ex) {
            serializableResult = ex.getCause();
        }

        testGroup.tearDown();

        return serializableResult;
    }
}
