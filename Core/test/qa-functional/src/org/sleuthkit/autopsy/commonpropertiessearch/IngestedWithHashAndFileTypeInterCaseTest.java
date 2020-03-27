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
import java.util.Set;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.commonpropertiessearch.AbstractCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.AllInterCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeCountSearchResults;
import org.sleuthkit.autopsy.commonpropertiessearch.SingleInterCaseCommonAttributeSearcher;
import static org.sleuthkit.autopsy.commonpropertiessearch.InterCaseTestUtils.*;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Tests with case 3 as the current case.
 *
 * If I use the search all cases option: One node for Hash A (1_1_A.jpg,
 * 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case 1: One node for
 * Hash A (1_1_A.jpg, 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case
 * 2: No matches If I only search in the current case (existing mode), allowing
 * all data sources: One node for Hash C (3_1_C.jpg, 3_2_C.jpg)
 *
 */
public class IngestedWithHashAndFileTypeInterCaseTest extends NbTestCase {

    private final InterCaseTestUtils utils;

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestedWithHashAndFileTypeInterCaseTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public IngestedWithHashAndFileTypeInterCaseTest(String name) {
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
//                CASE3};
//
//            Path[][] paths = {
//                {this.utils.case1DataSet1Path, this.utils.case1DataSet2Path},
//                {this.utils.case2DataSet1Path, this.utils.case2DataSet2Path},
//                {this.utils.case3DataSet1Path, this.utils.case3DataSet2Path}};
//
//            this.utils.createCases(cases, paths, this.utils.getIngestSettingsForHashAndFileType(), InterCaseTestUtils.CASE3);
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
     * Search All cases with no file type filtering.
     */
    public void testOne() {
//        try {
//            AbstractCommonAttributeSearcher builder = new AllInterCaseCommonAttributeSearcher(false, false, this.utils.FILE_TYPE, 0);
//            CommonAttributeCountSearchResults metadata = builder.findMatchesByCount();
//
//            assertTrue("Results should not be empty", metadata.size() != 0);
//
//            //case 1 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_0_DAT, CASE1_DATASET_1, CASE1", 0, getInstanceCount(metadata, HASH_0_DAT, CASE1_DATASET_1, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE1_DATASET_1, CASE1", 1, getInstanceCount(metadata, HASH_A_PDF, CASE1_DATASET_1, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE1_DATASET_1, CASE1", 1, getInstanceCount(metadata, HASH_A_JPG, CASE1_DATASET_1, CASE1));
//
//            //case 1 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_0_DAT, CASE1_DATASET_2, CASE1", 0, getInstanceCount(metadata, HASH_0_DAT, CASE1_DATASET_2, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE1_DATASET_2, CASE1", 1, getInstanceCount(metadata, HASH_A_PDF, CASE1_DATASET_2, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE1_DATASET_2, CASE1", 1, getInstanceCount(metadata, HASH_A_JPG, CASE1_DATASET_2, CASE1));
//
//            //case 2 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_B_PDF, CASE2_DATASET_1, CASE2", 0, getInstanceCount(metadata, HASH_B_PDF, CASE2_DATASET_1, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_B_JPG, CASE2_DATASET_1, CASE2", 0, getInstanceCount(metadata, HASH_B_JPG, CASE2_DATASET_1, CASE2));
//
//            //case 2 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE2_DATASET_2, CASE2", 1, getInstanceCount(metadata, HASH_A_PDF, CASE2_DATASET_2, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE2_DATASET_2, CASE2", 1, getInstanceCount(metadata, HASH_A_JPG, CASE2_DATASET_2, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_D_DOC, CASE2_DATASET_2, CASE2", 1, getInstanceCount(metadata, HASH_D_DOC, CASE2_DATASET_2, CASE2));
//
//            //case 3 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE3_DATASET_1, CASE3", 1, getInstanceCount(metadata, HASH_A_JPG, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE3_DATASET_1, CASE3", 1, getInstanceCount(metadata, HASH_A_PDF, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_JPG, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_C_JPG, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_PDF, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_C_PDF, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_D_JPG, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_D_JPG, CASE3_DATASET_1, CASE3));
//
//            //case 3 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_C_JPG, CASE3_DATASET_2, CASE3", 0, getInstanceCount(metadata, HASH_C_JPG, CASE3_DATASET_2, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_PDF, CASE3_DATASET_2, CASE3", 0, getInstanceCount(metadata, HASH_C_PDF, CASE3_DATASET_2, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_D_DOC, CASE3_DATASET_2, CASE3", 1, getInstanceCount(metadata, HASH_D_DOC, CASE3_DATASET_2, CASE3));
//
//        } catch (TskCoreException | NoCurrentCaseException | SQLException | EamDbException ex) {
//            Exceptions.printStackTrace(ex);
//            Assert.fail(ex.getMessage());
//        }
    }

    /**
     * Search All cases with no file type filtering.
     */
    public void testTwo() {
//        try {
//            int matchesMustAlsoBeFoundInThisCase = 0;
//            
//            // Filter out the time stamp to get the correct case name.
//            Set<String> caseNames = this.utils.getCaseMap().keySet();
//            for (String caseName : caseNames) {
//                if (caseName.substring(0, caseName.length() - 20).equalsIgnoreCase(CASE2)) {
//                    // Case match found. Get the number of matches.
//                    matchesMustAlsoBeFoundInThisCase = this.utils.getCaseMap().get(caseName);
//                }
//            }
//            CorrelationAttributeInstance.Type fileType = CorrelationAttributeInstance.getDefaultCorrelationTypes().get(0);
//            AbstractCommonAttributeSearcher builder = new SingleInterCaseCommonAttributeSearcher(matchesMustAlsoBeFoundInThisCase, false, false, fileType, 0);
//
//            CommonAttributeCountSearchResults metadata = builder.findMatchesByCount();
//
//            assertTrue("Results should not be empty", metadata.size() != 0);
//
//            //case 1 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_0_DAT, CASE1_DATASET_1, CASE1", 0, getInstanceCount(metadata, HASH_0_DAT, CASE1_DATASET_1, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE1_DATASET_1, CASE1", 1, getInstanceCount(metadata, HASH_A_PDF, CASE1_DATASET_1, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE1_DATASET_1, CASE1", 1, getInstanceCount(metadata, HASH_A_JPG, CASE1_DATASET_1, CASE1));
//
//            //case 1 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_0_DAT, CASE1_DATASET_2, CASE1", 0, getInstanceCount(metadata, HASH_0_DAT, CASE1_DATASET_2, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE1_DATASET_2, CASE1", 1, getInstanceCount(metadata, HASH_A_PDF, CASE1_DATASET_2, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE1_DATASET_2, CASE1", 1, getInstanceCount(metadata, HASH_A_JPG, CASE1_DATASET_2, CASE1));
//
//            //case 2 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_B_PDF, CASE2_DATASET_1, CASE2", 0, getInstanceCount(metadata, HASH_B_PDF, CASE2_DATASET_1, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_B_JPG, CASE2_DATASET_1, CASE2", 0, getInstanceCount(metadata, HASH_B_JPG, CASE2_DATASET_1, CASE2));
//
//            //case 2 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE2_DATASET_2, CASE2", 1, getInstanceCount(metadata, HASH_A_PDF, CASE2_DATASET_2, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE2_DATASET_2, CASE2", 1, getInstanceCount(metadata, HASH_A_JPG, CASE2_DATASET_2, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_D_DOC, CASE2_DATASET_2, CASE2", 1, getInstanceCount(metadata, HASH_D_DOC, CASE2_DATASET_2, CASE2));
//
//            //case 3 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE3_DATASET_1, CASE3", 1, getInstanceCount(metadata, HASH_A_JPG, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE3_DATASET_1, CASE3", 1, getInstanceCount(metadata, HASH_A_PDF, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_JPG, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_C_JPG, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_PDF, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_C_PDF, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_D_JPG, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_D_JPG, CASE3_DATASET_1, CASE3));
//
//            //case 3 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_C_JPG, CASE3_DATASET_2, CASE3", 0, getInstanceCount(metadata, HASH_C_JPG, CASE3_DATASET_2, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_PDF, CASE3_DATASET_2, CASE3", 0, getInstanceCount(metadata, HASH_C_PDF, CASE3_DATASET_2, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_D_DOC, CASE3_DATASET_2, CASE3", 1, getInstanceCount(metadata, HASH_D_DOC, CASE3_DATASET_2, CASE3));
//
//        } catch (TskCoreException | NoCurrentCaseException | SQLException | EamDbException ex) {
//            Exceptions.printStackTrace(ex);
//            Assert.fail(ex.getMessage());
//        }
    }

    /**
     * We should be able to observe that certain files are no longer returned in
     * the result set since they exist too frequently
     */
    public void testThree() {
//        try {
//
//            CorrelationAttributeInstance.Type fileType = CorrelationAttributeInstance.getDefaultCorrelationTypes().get(0);
//            AbstractCommonAttributeSearcher builder = new AllInterCaseCommonAttributeSearcher(false, false, fileType, 50);
//
//            CommonAttributeCountSearchResults metadata = builder.findMatchesByCount();
//            metadata.filterMetadata();
//            assertTrue("Results should not be empty", metadata.size() != 0);
//
//            //case 1 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_0_DAT, CASE1_DATASET_1, CASE1", 0, getInstanceCount(metadata, HASH_0_DAT, CASE1_DATASET_1, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE1_DATASET_1, CASE1", 0, getInstanceCount(metadata, HASH_A_PDF, CASE1_DATASET_1, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE1_DATASET_1, CASE1", 0, getInstanceCount(metadata, HASH_A_JPG, CASE1_DATASET_1, CASE1));
//
//            //case 1 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_0_DAT, CASE1_DATASET_2, CASE1", 0, getInstanceCount(metadata, HASH_0_DAT, CASE1_DATASET_2, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE1_DATASET_2, CASE1", 0, getInstanceCount(metadata, HASH_A_PDF, CASE1_DATASET_2, CASE1));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE1_DATASET_2, CASE1", 0, getInstanceCount(metadata, HASH_A_JPG, CASE1_DATASET_2, CASE1));
//
//            //case 2 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_B_PDF, CASE2_DATASET_1, CASE2", 0, getInstanceCount(metadata, HASH_B_PDF, CASE2_DATASET_1, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_B_JPG, CASE2_DATASET_1, CASE2", 0, getInstanceCount(metadata, HASH_B_JPG, CASE2_DATASET_1, CASE2));
//
//            //case 2 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE2_DATASET_2, CASE2", 0, getInstanceCount(metadata, HASH_A_PDF, CASE2_DATASET_2, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE2_DATASET_2, CASE2", 0, getInstanceCount(metadata, HASH_A_JPG, CASE2_DATASET_2, CASE2));
//            assertEquals("Verify Existence or Count failed for HASH_D_DOC, CASE2_DATASET_2, CASE2", 1, getInstanceCount(metadata, HASH_D_DOC, CASE2_DATASET_2, CASE2));
//
//            //case 3 data set 1
//            assertEquals("Verify Existence or Count failed for HASH_A_JPG, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_A_JPG, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_A_PDF, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_A_PDF, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_JPG, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_C_JPG, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_PDF, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_C_PDF, CASE3_DATASET_1, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_D_JPG, CASE3_DATASET_1, CASE3", 0, getInstanceCount(metadata, HASH_D_JPG, CASE3_DATASET_1, CASE3));
//
//            //case 3 data set 2
//            assertEquals("Verify Existence or Count failed for HASH_C_JPG, CASE3_DATASET_2, CASE3", 0, getInstanceCount(metadata, HASH_C_JPG, CASE3_DATASET_2, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_C_PDF, CASE3_DATASET_2, CASE3", 0, getInstanceCount(metadata, HASH_C_PDF, CASE3_DATASET_2, CASE3));
//            assertEquals("Verify Existence or Count failed for HASH_D_DOC, CASE3_DATASET_2, CASE3", 1, getInstanceCount(metadata, HASH_D_DOC, CASE3_DATASET_2, CASE3));
//
//        } catch (TskCoreException | NoCurrentCaseException | SQLException | EamDbException ex) {
//            Exceptions.printStackTrace(ex);
//            Assert.fail(ex.getMessage());
//        }
    }
}
