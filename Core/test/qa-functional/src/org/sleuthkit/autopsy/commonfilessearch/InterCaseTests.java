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

/**
 *
 * If I use the search all cases option: One node for Hash A (1_1_A.jpg,
 * 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case 1: One node for
 * Hash A (1_1_A.jpg, 1_2_A.jpg, 3_1_A.jpg) If I search for matches only in Case
 * 2: No matches If I only search in the current case (existing mode), allowing
 * all data sources: One node for Hash C (3_1_C.jpg, 3_2_C.jpg)
 *
 * @author bsweeney
 */
public class InterCaseTests extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(InterCaseTests.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public InterCaseTests(String name) {
        super(name);
    }
    
    @Override
    public void setUp(){
        
    }

}
