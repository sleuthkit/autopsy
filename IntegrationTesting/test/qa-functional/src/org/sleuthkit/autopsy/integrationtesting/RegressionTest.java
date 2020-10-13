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
package org.sleuthkit.autopsy.integrationtesting;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.Test;
import junit.framework.TestCase;
import org.netbeans.jemmy.Timeouts;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.integrationtesting.AutopsyTestCases;

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

    private static final Logger logger = Logger.getLogger(RegressionTest.class.getName()); // DO NOT USE AUTOPSY LOGGER

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
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(RegressionTest.class).
                clusters(".*").
                enableModules(".*");
        
        conf = conf.addTest("testOpen");

        return NbModuleSuite.create(conf);

    }

    public void testOpen() {
        logger.log(Level.SEVERE, "Lets get it started");
    }
}
