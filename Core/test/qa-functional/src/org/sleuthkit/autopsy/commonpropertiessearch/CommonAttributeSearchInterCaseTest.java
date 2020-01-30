/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.nio.file.Path;
import java.sql.SQLException;
import junit.framework.Assert;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.commonpropertiessearch.AbstractCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.AllInterCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeCountSearchResults;
import static org.sleuthkit.autopsy.commonpropertiessearch.InterCaseTestUtils.CASE1;
import static org.sleuthkit.autopsy.commonpropertiessearch.InterCaseTestUtils.CASE2;
import static org.sleuthkit.autopsy.commonpropertiessearch.InterCaseTestUtils.CASE3;
import static org.sleuthkit.autopsy.commonpropertiessearch.InterCaseTestUtils.CASE4;
import static org.sleuthkit.autopsy.commonpropertiessearch.InterCaseTestUtils.verifyInstanceCount;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Search for commonality in different sorts of attributes (files, usb devices,
 * emails, domains). Observe that frequency filtering works for various types.
 *
 * TODO (JIRA-4166): The testOne tests are commented out because the functional
 * test framework needs to be able to configure the keyword search ingest module
 * to produce instances of the correlation attributes for the tests. This cannot
 * be easily done at present because the keyword search module resides in an NBM
 * with a dependency on the Autopsy-Core NBM; the otherwise obvious solution of
 * publicly exposing the keyword search module settings fails due to a circular
 * dependency.
 */
public class CommonAttributeSearchInterCaseTest extends NbTestCase {

    private final InterCaseTestUtils utils;

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CommonAttributeSearchInterCaseTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public CommonAttributeSearchInterCaseTest(String name) {
        super(name);
        this.utils = new InterCaseTestUtils(this);
    }

//    @Override
//    public void setUp() {
//        this.utils.clearTestDir();
//        try {
//            this.utils.enableCentralRepo();
//
//            String[] cases = new String[]{
//                CASE1,
//                CASE2,
//                CASE3,
//                CASE4};
//
//            Path[][] paths = {
//                {this.utils.attrCase1Path},
//                {this.utils.attrCase2Path},
//                {this.utils.attrCase3Path},
//                {this.utils.attrCase4Path}};
//
//            this.utils.createCases(cases, paths, this.utils.getIngestSettingsForKitchenSink(), InterCaseTestUtils.CASE1);
//        } catch (TskCoreException | EamDbException ex) {
//            Exceptions.printStackTrace(ex);
//            Assert.fail(ex.getMessage());
//        }
//    }
//
//    @Override
//    public void tearDown() {
//        this.utils.clearTestDir();
//        this.utils.tearDown();
//    }

    /**
     * Run a search on the given type and ensure that all results are off that
     * type.
     *
     * No frequency filtering applied.
     *
     * @param type
     */
//    private void assertResultsAreOfType(CorrelationAttributeInstance.Type type) {
//
//        try {
//
//            AbstractCommonAttributeSearcher builder = new AllInterCaseCommonAttributeSearcher(false, false, type, 0);
//
//            CommonAttributeCountSearchResults metadata = builder.findMatchesByCount();
//
//            metadata.size();
//
//            assertFalse(verifyInstanceCount(metadata, 0));
//
//            assertTrue(this.utils.areAllResultsOfType(metadata, type));
//
//        } catch (TskCoreException | NoCurrentCaseException | SQLException | EamDbException ex) {
//            Exceptions.printStackTrace(ex);
//            Assert.fail(ex.getMessage());
//        }
//    }

    /**
     * Test that a search for each type returns results of that type only.
     */
    public void testOne() {
//        assertResultsAreOfType(this.utils.USB_ID_TYPE);
//        assertResultsAreOfType(this.utils.DOMAIN_TYPE);
//        assertResultsAreOfType(this.utils.FILE_TYPE);
//        assertResultsAreOfType(this.utils.EMAIL_TYPE);
//        assertResultsAreOfType(this.utils.PHONE_TYPE);   
    }

    /**
     * Test that the frequency filter behaves reasonably for attributes other
     * than the file type.
     */
    public void testTwo() {
//        try {
//
//            AbstractCommonAttributeSearcher builder;
//            CommonAttributeCountSearchResults metadata;
//
//            builder = new AllInterCaseCommonAttributeSearcher(false, false, this.utils.USB_ID_TYPE, 100);
//            metadata = builder.findMatchesByCount();
//            metadata.size();
//            //assertTrue("This should yield 13 results.", verifyInstanceCount(metadata, 13));
//
//            builder = new AllInterCaseCommonAttributeSearcher(false, false, this.utils.USB_ID_TYPE, 20);
//            metadata = builder.findMatchesByCount();
//            metadata.size();
//            //assertTrue("This should yield no results.", verifyInstanceCount(metadata, 0));
//
//            builder = new AllInterCaseCommonAttributeSearcher(false, false, this.utils.USB_ID_TYPE, 90);
//            metadata = builder.findMatchesByCount();
//            metadata.size();
//            //assertTrue("This should yield 2 results.", verifyInstanceCount(metadata, 2));
//
//        } catch (TskCoreException | NoCurrentCaseException | SQLException | EamDbException ex) {
//            Exceptions.printStackTrace(ex);
//            Assert.fail(ex.getMessage());
//        }
    }
}
