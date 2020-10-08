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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import org.sleuthkit.autopsy.integrationtesting.config.IntegrationTestConfig;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.cxf.common.util.CollectionUtils;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.autopsy.datasourceprocessors.DataSourceProcessorUtility;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.integrationtesting.config.CaseConfig;
import org.sleuthkit.autopsy.integrationtesting.config.IntegrationCaseType;
import org.sleuthkit.autopsy.modules.encryptiondetection.EncryptionDetectionModuleFactory;
import org.sleuthkit.autopsy.modules.encryptiondetection.EncryptionDetectionTest;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Main entry point for running integration tests. Handles processing
 * parameters, ingesting data sources for cases, and running items implementing
 * IntegrationTests.
 */
public class MainTestRunner extends TestCase {

    private static final Logger logger = Logger.getLogger(MainTestRunner.class.getName()); // DO NOT USE AUTOPSY LOGGER
    private static final String CONFIG_FILE_KEY = "CONFIG_FILE_KEY";

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

    public void runIntegrationTests() {
        String configFile = System.getProperty(CONFIG_FILE_KEY);
        IntegrationTestConfig config;
        try {
            config = getConfigFromFile(configFile);
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
                    Case autopsyCase = runIngest(caseConfig, caseType);
                    if (autopsyCase == null || autopsyCase != Case.getCurrentCase()) {
                        logger.log(Level.WARNING,
                                String.format("Case was not properly ingested or setup correctly for environment.  Case is %s and current case is %s.",
                                        autopsyCase, Case.getCurrentCase()));
                        return;
                    }

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

    private Case runIngest(CaseConfig caseConfig, CaseType caseType) {
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

        addDataSourcesToCase(caseConfig.getDataSourceResources(), caseConfig.getCaseName());
        try {
            IngestUtils.runIngestJob(openCase.getDataSources(), getIngestSettings(caseConfig));
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("There was an error while ingesting datasources for case %s", caseConfig.getCaseName()), ex);
        }
        
        return openCase;
    }

    
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

    private IntegrationTestConfig getConfigFromFile(String filePath) throws IOException {
        ObjectMapper om = new ObjectMapper();
        try (FileInputStream jsonSrc = new FileInputStream(filePath)) {
            return om.readValue(jsonSrc, IntegrationTestConfig.class);
        }
    }

    private IngestJobSettings getIngestSettings(CaseConfig caseConfig) {
        // TODO
        ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
        IngestUtils.addDataSource(dataSourceProcessor, BITLOCKER_DETECTION_IMAGE_PATH);
        IngestModuleFactory ingestModuleFactory = new EncryptionDetectionModuleFactory();
        IngestModuleIngestJobSettings settings = ingestModuleFactory.getDefaultIngestJobSettings();
        IngestModuleTemplate template = new IngestModuleTemplate(ingestModuleFactory, settings);
        template.setEnabled(true);
        List<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(template);
        IngestJobSettings ingestJobSettings = new IngestJobSettings(EncryptionDetectionTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates);
    }

    private void runIntegrationTests(IntegrationTestConfig config, CaseConfig caseConfig, CaseType caseType) {
        // this will capture output results
        OutputResults results = new OutputResults();

        // run through each ConsumerIntegrationTest
        for (IntegrationTests testGroup : Lookup.getDefault().lookupAll(IntegrationTests.class)) {

            // if test should not be included in results, skip it.
            if (!caseConfig.getTestConfig().hasIncludedTest(testGroup.getClass().getCanonicalName())) {
                continue;
            }

            List<Method> testMethods = getIntegrationTestMethods(testGroup);
            if (CollectionUtils.isEmpty(testMethods)) {
                continue;
            }

            testGroup.setupClass();
            for (Method testMethod : testMethods) {
                runIntegrationTestMethod(results, testGroup, testMethod);
            }
            testGroup.tearDownClass();
        }

        // write the results for the case to a file
        serializeFile(results, config.getRootTestOutputPath(), caseConfig.getCaseName(), getCaseTypeId(caseType));
    }

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

    private void runIntegrationTestMethod(OutputResults results, IntegrationTests testGroup, Method testMethod) {
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

        // add the results and capture the package, class, 
        // and method of the test for easy location of failed tests
        results.addResult(
                testGroup.getClass().getPackage().getName(),
                testGroup.getClass().getSimpleName(),
                testMethod.getName(),
                serializableResult);
    }

    private List<Method> getIntegrationTestMethods(IntegrationTests testGroup) {
        return Stream.of(testGroup.getClass().getMethods())
                .filter((method) -> method.getAnnotation(IntegrationTest.class) != null)
                .collect(Collectors.toList());
    }

    private void serializeFile(OutputResults results, String outputFolder, String caseName, String caseType) {
        String outputExtension = ".yml";
        Path outputPath = Paths.get(outputFolder, String.format("%s-%s%s", caseName, caseType, outputExtension));
        ObjectMapper om = new ObjectMapper(new YAMLFactory());

        try {
            om.writeValue(outputPath.toFile(), results.getSerializableData());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "There was an error writing results to outputPath: " + outputPath, ex);
        }
    }
}
