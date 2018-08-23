/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilessearch;

import java.sql.SQLException;
import java.util.Map;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.commonfilesearch.AbstractCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonfilesearch.AllInterCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonfilesearch.CommonAttributeSearchResults;
import org.sleuthkit.autopsy.commonfilesearch.SingleInterCaseCommonAttributeSearcher;
import static org.sleuthkit.autopsy.commonfilessearch.InterCaseTestUtils.*;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Tests with case 3 as the current case.
 * 
 * If I use the search all cases option: One node for Hash A (1_1_A.jpg,
 * 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case 1: One node for
 * Hash A (1_1_A.jpg, 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case
 * 2: No matches If I only search in the current case (existing mode), allowing
 * all data sources: One node for Hash C (3_1_C.jpg, 3_2_C.jpg)
 */
public class IngestedWithHashAndFileTypeInterCaseTests extends NbTestCase {
    
    private final InterCaseTestUtils utils;
        
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestedWithHashAndFileTypeInterCaseTests.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }
    
    public IngestedWithHashAndFileTypeInterCaseTests(String name) {
        super(name);
        this.utils = new InterCaseTestUtils(this);
    }
    
    @Override
    public void setUp(){
        this.utils.clearTestDir();
        try {
            this.utils.enableCentralRepo();
            this.utils.createCases(this.utils.getIngestSettingsForHashAndFileType(), InterCaseTestUtils.CASE3);
        } catch (TskCoreException | EamDbException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    @Override
    public void tearDown(){
        this.utils.clearTestDir();
        this.utils.tearDown();
    }
    
    /**
     * Search All cases with no file type filtering.
     */
    public void testOne() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            
            //note that the params false and false are presently meaningless because that feature is not supported yet
            AbstractCommonAttributeSearcher builder = new AllInterCaseCommonAttributeSearcher(dataSources, false, false, 0);
            
            CommonAttributeSearchResults metadata = builder.findFiles();
            
            assertTrue("Results should not be empty", metadata.size() != 0);
            
            //case 1 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_0_DAT, CASE1_DATASET_1, CASE1, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE1_DATASET_1, CASE1, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE1_DATASET_1, CASE1, 1));
            
            //case 1 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_0_DAT, CASE1_DATASET_2, CASE1, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE1_DATASET_2, CASE1, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE1_DATASET_2, CASE1, 1));
            
            //case 2 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_B_PDF, CASE2_DATASET_1, CASE2, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_B_JPG, CASE2_DATASET_1, CASE2, 0));
            
            //case 2 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE2_DATASET_2, CASE2, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE2_DATASET_2, CASE2, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_DOC, CASE2_DATASET_2, CASE2, 1));
            
            //case 3 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE3_DATASET_1, CASE3, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE3_DATASET_1, CASE3, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_JPG, CASE3_DATASET_1, CASE3, 0));            
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_PDF, CASE3_DATASET_1, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_JPG, CASE3_DATASET_1, CASE3, 0));
            
            //case 3 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_JPG, CASE3_DATASET_2, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_PDF, CASE3_DATASET_2, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_DOC, CASE3_DATASET_2, CASE3, 1)); 
            
            
        } catch (TskCoreException | NoCurrentCaseException | SQLException | EamDbException ex) {
            Exceptions.printStackTrace(ex); 
            Assert.fail(ex.getMessage());
        }
    }
    
    /**
     * Search All cases with no file type filtering.
     */
    public void testTwo() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            
            int matchesMustAlsoBeFoundInThisCase = this.utils.getCaseMap().get(CASE2);
                        
            AbstractCommonAttributeSearcher builder = new SingleInterCaseCommonAttributeSearcher(matchesMustAlsoBeFoundInThisCase, dataSources, false, false, 0);
            
            CommonAttributeSearchResults metadata = builder.findFiles();
            
            assertTrue("Results should not be empty", metadata.size() != 0);
            
            //case 1 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_0_DAT, CASE1_DATASET_1, CASE1, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE1_DATASET_1, CASE1, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE1_DATASET_1, CASE1, 1));
            
            //case 1 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_0_DAT, CASE1_DATASET_2, CASE1, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE1_DATASET_2, CASE1, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE1_DATASET_2, CASE1, 1));
            
            //case 2 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_B_PDF, CASE2_DATASET_1, CASE2, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_B_JPG, CASE2_DATASET_1, CASE2, 0));
            
            //case 2 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE2_DATASET_2, CASE2, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE2_DATASET_2, CASE2, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_DOC, CASE2_DATASET_2, CASE2, 1));
            
            //case 3 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE3_DATASET_1, CASE3, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE3_DATASET_1, CASE3, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_JPG, CASE3_DATASET_1, CASE3, 0));            
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_PDF, CASE3_DATASET_1, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_JPG, CASE3_DATASET_1, CASE3, 0));
            
            //case 3 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_JPG, CASE3_DATASET_2, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_PDF, CASE3_DATASET_2, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_DOC, CASE3_DATASET_2, CASE3, 1)); 
            
            
        } catch (TskCoreException | NoCurrentCaseException | SQLException | EamDbException ex) {
            Exceptions.printStackTrace(ex); 
            Assert.fail(ex.getMessage());
        }
    }
    
    /**
     * We should be able to observe that certain files o no longer returned
     * in the result set since they do not appear frequently enough.
     */
    public void testThree(){
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            
            //note that the params false and false are presently meaningless because that feature is not supported yet
            AbstractCommonAttributeSearcher builder = new AllInterCaseCommonAttributeSearcher(dataSources, false, false, 50);
            
            CommonAttributeSearchResults metadata = builder.findFiles();
            
            assertTrue("Results should not be empty", metadata.size() != 0);
            
            //case 1 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_0_DAT, CASE1_DATASET_1, CASE1, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE1_DATASET_1, CASE1, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE1_DATASET_1, CASE1, 1));
            
            //case 1 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_0_DAT, CASE1_DATASET_2, CASE1, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE1_DATASET_2, CASE1, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE1_DATASET_2, CASE1, 1));
            
            //case 2 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_B_PDF, CASE2_DATASET_1, CASE2, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_B_JPG, CASE2_DATASET_1, CASE2, 0));
            
            //case 2 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE2_DATASET_2, CASE2, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE2_DATASET_2, CASE2, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_DOC, CASE2_DATASET_2, CASE2, 0));
            
            //case 3 data set 1
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_JPG, CASE3_DATASET_1, CASE3, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_A_PDF, CASE3_DATASET_1, CASE3, 1));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_JPG, CASE3_DATASET_1, CASE3, 0));            
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_PDF, CASE3_DATASET_1, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_JPG, CASE3_DATASET_1, CASE3, 0));
            
            //case 3 data set 2
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_JPG, CASE3_DATASET_2, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_C_PDF, CASE3_DATASET_2, CASE3, 0));
            assertTrue(verifyInstanceExistanceAndCount(metadata, HASH_D_DOC, CASE3_DATASET_2, CASE3, 0)); 
            
        } catch (TskCoreException | NoCurrentCaseException | SQLException | EamDbException ex) {
            Exceptions.printStackTrace(ex); 
            Assert.fail(ex);
        }
    }
}
