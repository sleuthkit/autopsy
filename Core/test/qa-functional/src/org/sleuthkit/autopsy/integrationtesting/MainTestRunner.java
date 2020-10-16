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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.autopsy.datasourceprocessors.DataSourceProcessorUtility;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Main entry point for running integration tests. Handles processing
 * parameters, ingesting data sources for cases, and running items implementing
 * IntegrationTests.
 */
public class MainTestRunner extends TestCase {

    private static final Logger logger = Logger.getLogger(MainTestRunner.class.getName()); 
    private static final String CONFIG_FILE_KEY = "integrationConfigFile";
    private static final EnvironmentSetupUtil SETUP_UTIL = new EnvironmentSetupUtil();
    
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
            config = getConfigFromFile(configFile);
            if (config.getWorkingDirectory() == null) {
                config.setWorkingDirectory(new File(configFile).getParentFile().getAbsolutePath());
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "There was an error processing integration test config at " + configFile, ex);
            return;
        }

        if (config == null) {
            logger.log(Level.WARNING, "No properly formatted config found at " + configFile);
        }

        if (!CollectionUtils.isEmpty(config.getCases())) {
            for (CaseConfig caseConfig : config.getCases()) {
                for (CaseType caseType : IntegrationCaseType.getCaseTypes(caseConfig.getCaseTypes())) {
                    // create an autopsy case for each case in the config and for each case type for the specified case.
                    // then run ingest for the case.
                    Case autopsyCase = runIngest(config, caseConfig, caseType);
                    if (autopsyCase == null || autopsyCase != Case.getCurrentCase()) {
                        logger.log(Level.WARNING,
                                String.format("Case was not properly ingested or setup correctly for environment.  Case is %s and current case is %s.",
                                        autopsyCase, Case.getCurrentCase()));
                        return;
                    }

                    // once ingested, run integration tests to produce output.
                    String caseName = autopsyCase.getName();

                    runIntegrationTests(config, caseConfig, caseType);

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

    /**
     * Create a case and run ingest with the current case.
     *
     * @param config The overall configuration.
     * @param caseConfig The configuration for the case.
     * @param caseType The type of case.
     * @return The currently open case after ingest.
     */
    private Case runIngest(IntegrationTestConfig config, CaseConfig caseConfig, CaseType caseType) {
        Case openCase = null;
        switch (caseType) {
            case SINGLE_USER_CASE:
                openCase = CaseUtils.createAsCurrentCase(caseConfig.getCaseName());
                break;
            case MULTI_USER_CASE:
            // TODO
            default:
                throw new IllegalArgumentException("Unknown case type: " + caseType);
        }

        if (openCase == null) {
            logger.log(Level.WARNING, String.format("No case could be created for %s of type %s.", caseConfig.getCaseName(), caseType));
            return null;
        }

        addDataSourcesToCase(PathUtil.getAbsolutePaths(config.getWorkingDirectory(), caseConfig.getDataSourceResources()), caseConfig.getCaseName());
        
        
        try {
            IngestJobSettings ingestJobSettings = SETUP_UTIL.setupEnvironment(config, caseConfig);
            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("There was an error while ingesting datasources for case %s", caseConfig.getCaseName()), ex);
        }

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

    /**
     * Deserializes the json config specified at the given path into the java
     * equivalent IntegrationTestConfig object.
     *
     * @param filePath The path to the config.
     * @return The java object.
     * @throws IOException If there is an error opening the file.
     */
    private IntegrationTestConfig getConfigFromFile(String filePath) throws IOException {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        return gson.fromJson(new FileReader(new File(filePath)), IntegrationTestConfig.class);
    }

    /**
     * Runs the integration tests and serializes results to disk.
     *
     * @param config The overall config.
     * @param caseConfig The configuration for a particular case.
     * @param caseType The case type (single user / multi user) to create.
     */
    private void runIntegrationTests(IntegrationTestConfig config, CaseConfig caseConfig, CaseType caseType) {
        // this will capture output results
        OutputResults results = new OutputResults();

        // run through each ConsumerIntegrationTest
        for (IntegrationTests testGroup : Lookup.getDefault().lookupAll(IntegrationTests.class)) {

            // if test should not be included in results, skip it.
            if (!caseConfig.getTestConfig()
                    .hasIncludedTest(testGroup.getClass().getCanonicalName())) {
                continue;
            }

            List<Method> testMethods = Stream.of(testGroup.getClass().getMethods())
                    .filter((method) -> method.getAnnotation(IntegrationTest.class) != null)
                    .collect(Collectors.toList());

            if (CollectionUtils.isEmpty(testMethods)) {
                continue;
            }

            testGroup.setupClass();
            for (Method testMethod : testMethods) {
                Object serializableResult = runIntegrationTestMethod(testGroup, testMethod);
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

        // write the results for the case to a file
        serializeFile(
                results, 
                PathUtil.getAbsolutePath(config.getWorkingDirectory(), config.getRootTestOutputPath()), 
                caseConfig.getCaseName(), 
                getCaseTypeId(caseType)
        );
    }

    /**
     * The name of the CaseType to be used in the filename during serialization.
     *
     * @param caseType The case type.
     * @return The identifier to be used in a file name.
     */
    private String getCaseTypeId(CaseType caseType) {
        if (caseType == null) {
            return "";
        }

        switch (caseType) {
            case SINGLE_USER_CASE:
                return "singleUser";
            case MULTI_USER_CASE:
                return "multiUser";
            default:
                throw new IllegalArgumentException("Unknown case type: " + caseType);
        }
    }

    /**
     * Runs a test method in a test suite on the current case.
     *
     * @param testGroup The test suite to which the method belongs.
     * @param testMethod The java reflection method to run.
     */
    private Object runIntegrationTestMethod(IntegrationTests testGroup, Method testMethod) {
        testGroup.setup();

        // run the test method and get the results
        Object serializableResult = null;

        try {
            serializableResult = testMethod.invoke(testGroup);
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

    /**
     * Used by yaml serialization to properly represent objects.
     */
    private static final Representer MAP_REPRESENTER = new Representer() {
        @Override
        protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
            // don't show class name in yaml
            if (!classTags.containsKey(javaBean.getClass())) {
                addClassTag(javaBean.getClass(), Tag.MAP);
            }

            return super.representJavaBean(properties, javaBean);
        }
    };

    /**
     * Used by yaml serialization to properly represent objects.
     */
    private static final DumperOptions DUMPER_OPTS = new DumperOptions() {
        {
            // Show each property on a new line.
            setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            // allow for serializing properties that only have getters.
            setAllowReadOnlyProperties(true);
        }
    };

    /**
     * The actual yaml serializer that is used.
     */
    private static final Yaml YAML_SERIALIZER = new Yaml(MAP_REPRESENTER, DUMPER_OPTS);

    /**
     * Serializes results of a test to a yaml file.
     *
     * @param results The results to serialize.
     * @param outputFolder The folder where the yaml should be written.
     * @param caseName The name of the case (used to determine filename).
     * @param caseType The type of case (used to determine filename).
     */
    private void serializeFile(OutputResults results, String outputFolder, String caseName, String caseType) {
        String outputExtension = ".yml";
        Path outputPath = Paths.get(outputFolder, String.format("%s-%s%s", caseName, caseType, outputExtension));

        try {
            FileWriter writer = new FileWriter(outputPath.toFile());
            YAML_SERIALIZER.dump(results.getSerializableData(), writer);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "There was an error writing results to outputPath: " + outputPath, ex);
        }
    }
}
