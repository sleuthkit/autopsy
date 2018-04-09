/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.testing;

import java.io.File;
import java.io.IOException;
import junit.framework.Test;
import junit.framework.TestCase;
import org.netbeans.jemmy.Timeouts;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * This test expects the following system properties to be set: img_path: The
 * fully qualified path to the image file (if split, the first file) out_path:
 * The location where the case will be stored nsrl_path: Path to the nsrl
 * database known_bad_path: Path to a database of known bad hashes keyword_path:
 * Path to a keyword list xml file ignore_unalloc: Boolean whether to ignore
 * unallocated space or not
 *
 * Without these properties set, the test will fail to run correctly. To run
 * this test correctly, you should use the script 'regression.py' located in the
 * 'script' directory of the Testing module.
 */
public class RegressionTest extends TestCase {

    private static final Logger logger = Logger.getLogger(RegressionTest.class.getName());
    private static final AutopsyTestCases autopsyTests = new AutopsyTestCases(Boolean.parseBoolean(System.getProperty("isMultiUser")));

    /**
     * Constructor required by JUnit
     */
    public RegressionTest(String name) {
        super(name);
    }

    /**
     * Creates suite from particular test cases.
     */
    public static Test suite() {
        // run tests with specific configuration
        File img_path = new File(AutopsyTestCases.getEscapedPath(System.getProperty("img_path")));
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(RegressionTest.class).
                clusters(".*").
                enableModules(".*");
        if (img_path.isFile()) {
            conf = conf.addTest("testNewCaseWizardOpen",
                    "testNewCaseWizard",
                    "testStartAddImageFileDataSource",
                    "testConfigureIngest1",
                    "testConfigureHash",
                    "testConfigureIngest2",
                    "testConfigureSearch",
                    "testAddSourceWizard1",
                    "testIngest",
                    "testExpandDataSourcesTree", //After do ingest, before generate report, we expand Data Sources node
                    "testGenerateReportToolbar",
                    "testGenerateReportButton");
        }

        if (img_path.isDirectory()) {
            conf = conf.addTest("testNewCaseWizardOpen",
                    "testNewCaseWizard",
                    "testStartAddLogicalFilesDataSource",
                    "testConfigureIngest1",
                    "testConfigureHash",
                    "testConfigureIngest2",
                    "testConfigureSearch",
                    "testAddSourceWizard1",
                    "testIngest",
                    "testExpandDataSourcesTree", 
                    "testGenerateReportToolbar",
                    "testGenerateReportButton");
        }

        return NbModuleSuite.create(conf);

    }

    /**
     * Method called before each test case.
     */
    @Override
    public void setUp() {
        logger.info("########  " + AutopsyTestCases.getEscapedPath(System.getProperty("img_path")) + "  #######");
        Timeouts.setDefault("ComponentOperator.WaitComponentTimeout", 1000000);
    }

    /**
     * Method called after each test case.
     */
    @Override
    public void tearDown() {
    }

    public void testNewCaseWizardOpen() {
        autopsyTests.testNewCaseWizardOpen("Welcome");
    }

    public void testNewCaseWizard() {
        autopsyTests.testNewCaseWizard();
    }
    
    public void testStartAddImageFileDataSource() {
        autopsyTests.testStartAddImageFileDataSource();
    }

    public void testStartAddLogicalFilesDataSource() {
        autopsyTests.testStartAddLogicalFilesDataSource();
    }

    public void testAddSourceWizard1() {
        autopsyTests.testAddSourceWizard1();
    }

    public void testConfigureIngest1() {
        autopsyTests.testConfigureIngest1();
    }

    public void testConfigureHash() {
        autopsyTests.testConfigureHash();
    }

    public void testConfigureIngest2() {
        autopsyTests.testConfigureIngest2();
    }

    public void testConfigureSearch() {
        autopsyTests.testConfigureSearch();
    }

    public void testIngest() {
        autopsyTests.testIngest();
    }

    public void testExpandDataSourcesTree() {
        autopsyTests.testExpandDataSourcesTree();
    }
    public void testGenerateReportToolbar() {
        autopsyTests.testGenerateReportToolbar();
    }

    public void testGenerateReportButton() throws IOException {
        autopsyTests.testGenerateReportButton();
    }
}
