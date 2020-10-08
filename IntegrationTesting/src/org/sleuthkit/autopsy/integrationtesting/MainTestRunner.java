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

import org.sleuthkit.autopsy.integrationtesting.config.IntegrationTestConfig;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import junit.framework.Test;
import junit.framework.TestCase;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.encryptiondetection.EncryptionDetectionModuleFactory;

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

//    public void runIntegrationTests() {
//        String configFile = System.getProperty(CONFIG_FILE_KEY);
//        IntegrationTestConfig config = getFromConfigFile(configFile);
//        // Set up NetBeans environment
//        Case autopsyCase = runIngest(config);
//        runConsumerIntegrationTests(config);
//        Case.closeCurrentCase();
//    }
//
//    private Case runIngest(IntegrationTestConfig config) {
//        Case openCase = CaseUtils.createAsCurrentCase(BITLOCKER_DETECTION_CASE_NAME);
//        ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
//        IngestUtils.addDataSource(dataSourceProcessor, BITLOCKER_DETECTION_IMAGE_PATH);
//        IngestModuleFactory ingestModuleFactory = new EncryptionDetectionModuleFactory();
//        IngestModuleIngestJobSettings settings = ingestModuleFactory.getDefaultIngestJobSettings();
//        IngestModuleTemplate template = new IngestModuleTemplate(ingestModuleFactory, settings);
//        template.setEnabled(true);
//        List<IngestModuleTemplate> templates = new ArrayList<>();
//        templates.add(template);
//        IngestJobSettings ingestJobSettings = new IngestJobSettings(EncryptionDetectionTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates);
//        IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);
//        return openCase;
//    }
//
//    private void runIntegrationTests(IntegrationTestConfig config) {
//        // this will capture output results
//        OutputResults results = new OutputResults();
//
//        // run through each ConsumerIntegrationTest
//        for (IntegrationTests testGroup
//                : Lookup.getAll(ConsumerIntegrationTests.class)) {
//
//            // if test should not be included in results, skip it.
//            if (!testParams.hasIncludedTest(testGroup.getClass())) {
//                continue;
//            }
//
//            for (Method testMethod : getAllTestMethods(testGroup)) {
//
//                // run the test method and get the results
//                Object serializableResult = null;
//                try {
//                    Object serializableResult = testMethod.run();
//                } catch (Exception e) {
//                    serializableResult = e;
//                }
//
//                // add the results and capture the package, class, 
//                // and method of the test for easy location of failed tests
//                results.addResult(
//                        testGroup.getPackage(),
//                        testGroup.getSimpleName(),
//                        testMethod.getName(),
//                        serializableResult);
//            }
//        }
//
//        // write the results for the case to a file
//        serializeFile(testParams.getOutputPath(),
//                skCase.getName(),
//                results);
//    }
}
