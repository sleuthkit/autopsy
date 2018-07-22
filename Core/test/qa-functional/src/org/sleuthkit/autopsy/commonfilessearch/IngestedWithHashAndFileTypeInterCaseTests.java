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

import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.commonfilesearch.AbstractCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonfilesearch.AllInterCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonfilesearch.CommonAttributeSearchResults;

/**
 * If I use the search all cases option: One node for Hash A (1_1_A.jpg,
 * 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case 1: One node for
 * Hash A (1_1_A.jpg, 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case
 * 2: No matches If I only search in the current case (existing mode), allowing
 * all data sources: One node for Hash C (3_1_C.jpg, 3_2_C.jpg)
 */
public class IngestedWithHashAndFileTypeInterCaseTests extends NbTestCase {
    
    private final InterCaseUtils utils;
    
    private Case currentCase;
    
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestedWithHashAndFileTypeInterCaseTests.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }
    
    public IngestedWithHashAndFileTypeInterCaseTests(String name) {
        super(name);
        this.utils = new InterCaseUtils(this);
    }
    
    @Override
    public void setUp(){
        this.utils.clearTestDir();
        try {
            this.utils.enableCentralRepo();
            this.currentCase = this.utils.createCases(this.utils.getIngestSettingsForHashAndFileType(), InterCaseUtils.CASE3);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    @Override
    public void tearDown(){
        this.utils.tearDown();
    }
    
    /**
     * Search All
     * 
     * One node for Hash A (1_1_A.jpg, 1_2_A.jpg, 3_1_A.jpg)
     */
    public void testOne() {
        try {
            
            AbstractCommonAttributeSearcher builder = new AllInterCaseCommonAttributeSearcher(false, false);
            
            CommonAttributeSearchResults metadata = builder.findFiles();
            
            assertTrue("Results should not be empty", metadata.size() != 0);
            
            //assertTrue("")
            
            
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex); 
            Assert.fail(ex);
        }
    }
}
