package org.sleuthkit.autopsy.integrationtesting;

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
import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.integrationtesting.IntegrationTestService.IntegrationTestDiffException;
import org.sleuthkit.autopsy.integrationtesting.IntegrationTestService.IntegrationTestServiceException;

/**
 * Main entry point for running integration tests. Handles processing
 * parameters, ingesting data sources for cases, and running items implementing
 * IntegrationTests.
 */
public class TestRunner extends TestCase {

    /**
     * Constructor required by JUnit
     */
    public TestRunner(String name) {
        super(name);
    }

    /**
     * Creates suite from particular test cases.
     */
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(TestRunner.class).
                clusters(".*").
                enableModules(".*");

        return NbModuleSuite.create(conf.addTest("runIntegrationTests"));
    }

    public void runIntegrationTests() {
        IntegrationTestService testService = new IntegrationTestService();
        try {
            testService.runIntegrationTests();
        } catch (IntegrationTestDiffException ex) {
            Assert.fail(ex.getMessage());
        } catch (IntegrationTestServiceException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail("An exception occurred that prevented the operation of these integration tests: " + ex.getMessage());
        }
    }

}
