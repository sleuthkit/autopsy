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

import org.sleuthkit.autopsy.integrationtesting.config.TestSuiteConfig;
import org.sleuthkit.autopsy.integrationtesting.config.IntegrationTestConfig;
import org.sleuthkit.autopsy.integrationtesting.config.IntegrationCaseType;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.autopsy.datasourceprocessors.DataSourceProcessorUtility;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryService;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.integrationtesting.config.ConfigDeserializer;
import org.sleuthkit.autopsy.integrationtesting.config.EnvConfig;
import org.sleuthkit.autopsy.integrationtesting.config.ParameterizedResourceConfig;
import org.sleuthkit.autopsy.integrationtesting.config.TestingConfig;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Main entry point for running integration tests. Handles processing
 * parameters, ingesting data sources for cases, and running items implementing
 * IntegrationTests.
 */
public class MainTestRunner extends TestCase {

    private static final Logger logger = Logger.getLogger(MainTestRunner.class.getName());
    private static final String CONFIG_FILE_KEY = "integrationConfigFile";
    private static final ConfigDeserializer configDeserializer = new ConfigDeserializer();
    private static final IngestModuleFactoryService ingestModuleFactories = new IngestModuleFactoryService();

    private static final IngestType DEFAULT_INGEST_FILTER_TYPE = IngestType.ALL_MODULES;
    private static final Set<String> DEFAULT_EXCLUDED_MODULES = Stream.of("Plaso").collect(Collectors.toSet());

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

                    Pair<IngestJobSettings, List<ConfigurationModule<?>>> configurationResult = runConfigurationModules(caseName, testSuiteConfig);
                    IngestJobSettings ingestSettings = configurationResult.first();
                    List<ConfigurationModule<?>> configModules = configurationResult.second();

                    runIngest(autopsyCase, ingestSettings, caseName);

                    // once ingested, run integration tests to produce output.
                    OutputResults results = runIntegrationTests(testSuiteConfig.getIntegrationTests());

                    revertConfigurationModules(configModules);

                    // write the results for the case to a file
                    results.serializeToFile(
                            PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getRootTestOutputPath()),
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

    private void revertConfigurationModules(List<ConfigurationModule<?>> configModules) {
        List<ConfigurationModule<?>> reversed = new ArrayList<>(configModules);
        Collections.reverse(reversed);
        for (ConfigurationModule<?> configModule : reversed) {
            configModule.revert();
        }
    }

    private String getProfileName(String caseName) {
        return String.format("integrationTestProfile-%s", caseName);
    }

    private IngestJobSettings getDefaultIngestConfig(String caseName) {
        return new IngestJobSettings(
                getProfileName(caseName),
                DEFAULT_INGEST_FILTER_TYPE,
                ingestModuleFactories.getFactories().stream()
                        .filter((f) -> !DEFAULT_EXCLUDED_MODULES.contains(f.getModuleDisplayName()))
                        .map(f -> new IngestModuleTemplate(f, f.getDefaultIngestJobSettings()))
                        .collect(Collectors.toList())
        );
    }

    private Pair<IngestJobSettings, List<ConfigurationModule<?>>> runConfigurationModules(String caseName, TestSuiteConfig config) {
        if (CollectionUtils.isEmpty(config.getConfigurationModules())) {
            return Pair.of(getDefaultIngestConfig(caseName), Collections.emptyList());
        }

        IngestJobSettings curConfig = new IngestJobSettings(
                getProfileName(caseName),
                DEFAULT_INGEST_FILTER_TYPE,
                Collections.emptyList());

        List<ConfigurationModule<?>> configurationModuleCache = new ArrayList<>();

        for (ParameterizedResourceConfig configModule : config.getConfigurationModules()) {
            Pair<IngestJobSettings, ConfigurationModule<?>> ingestResult = runConfigurationModule(curConfig, configModule, caseName);
            if (ingestResult != null) {
                curConfig = ingestResult.first() == null ? curConfig : ingestResult.first();
                if (ingestResult.second() != null) {
                    configurationModuleCache.add(ingestResult.second());
                }
            }
        }
        return Pair.of(curConfig, configurationModuleCache);
    }

    private Pair<IngestJobSettings, ConfigurationModule<?>> runConfigurationModule(IngestJobSettings curConfig, ParameterizedResourceConfig configModule, String caseName) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(configModule.getResource());
        } catch (ClassNotFoundException ex) {
            logger.log(Level.WARNING, "Unable to find module: " + configModule.getResource(), ex);
            return null;
        }

        if (clazz == null || !ConfigurationModule.class.isAssignableFrom(clazz)) {
            logger.log(Level.WARNING, String.format("%s does not seem to be an instance of a configuration module.", configModule.getResource()));
            return null;
        }

        Type configurationModuleType = Stream.of(clazz.getGenericInterfaces())
                .filter(type -> type.getClass().equals(ConfigurationModule.class) && type instanceof ParameterizedType && type instanceof Class)
                .map(type -> ((ParameterizedType) type).getActualTypeArguments()[0])
                .findFirst()
                .orElse(null);

        if (configurationModuleType == null) {
            logger.log(Level.SEVERE, String.format("Could not determine generic type of config module: %s", configModule.getResource()));
            return null;
        }

        
        ConfigurationModule<?> configModuleObj = null;
        Object result = null;
        try {
            configModuleObj = (ConfigurationModule<?>) clazz.newInstance();
            Method m = clazz.getMethod("configure", IngestJobSettings.class, (Class<?>) configurationModuleType);
            result = m.invoke(configModuleObj, curConfig, configDeserializer.convertToObj(configModule.getParameters(), configurationModuleType));
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException |  InstantiationException ex) {
            logger.log(Level.SEVERE, String.format("There was an error calling configure method on Configuration Module %s", configModule.getResource()), ex);
        }
        
        if (result instanceof IngestJobSettings) {
            return Pair.of(curConfig, configModuleObj);
        } else {
            logger.log(Level.SEVERE, String.format("Could not retrieve IngestJobSettings from %s", configModule.getResource()));
            return null;
        }
    }

    private Case createCaseWithDataSources(EnvConfig envConfig, String caseName, CaseType caseType, List<String> dataSourcePaths) {
        Case openCase = null;
        switch (caseType) {
            case SINGLE_USER_CASE:
                openCase = CaseUtils.createAsCurrentCase(caseName);
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
