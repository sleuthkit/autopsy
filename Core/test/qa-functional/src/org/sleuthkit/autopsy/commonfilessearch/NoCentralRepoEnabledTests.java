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

import java.io.IOException;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.datamodel.TskCoreException;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 *
 * If I use the search all cases option: One node for Hash A (1_1_A.jpg,
 * 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case 1: One node for
 * Hash A (1_1_A.jpg, 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case
 * 2: No matches If I only search in the current case (existing mode), allowing
 * all data sources: One node for Hash C (3_1_C.jpg, 3_2_C.jpg)
 *
 */
public class NoCentralRepoEnabledTests extends NbTestCase {

    private final InterCaseUtils utils;
    private Case currentCase;
    
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(NoCentralRepoEnabledTests.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public NoCentralRepoEnabledTests(String name) {
        super(name);
        this.utils = new InterCaseUtils(this);
    }
    
    @Override
    public void setUp(){
        try {
            this.currentCase = this.utils.createCases(this.utils.getIngestSettingsForHashAndFileType(), InterCaseUtils.CASE1);
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
    
    @Override
    public void tearDown(){
        try {
            this.utils.tearDown();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
    
    void testOne(){
        
    }

}
