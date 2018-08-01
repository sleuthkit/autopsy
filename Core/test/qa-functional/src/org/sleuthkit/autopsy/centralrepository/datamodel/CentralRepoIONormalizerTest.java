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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;

/**
 *  
 * @author bsweeney
 */
public class CentralRepoIONormalizerTest extends NbTestCase {
    
    private static final String EMPTY_STRING = "";
    
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CentralRepoIONormalizerTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }
    
    public CentralRepoIONormalizerTest(String name) {
        super(name);
    }
    
    public void testNormalizeMd5(){
        final String aValidHash = "e34a8899ef6468b74f8a1048419ccc8b";
        final String anInValidHash = "e34asdfa8899ef6468b74f8a1048419ccc8b";
        final String aValidHashWithCaps = "E34A8899EF6468B74F8A1048419CCC8B";
        
        final int FILES_TYPE_ID = CorrelationAttribute.FILES_TYPE_ID;
        
        assertTrue("This hash should just work", CentralRepoIONormalizer.normalize(FILES_TYPE_ID, aValidHash).equals(aValidHash));
        assertTrue("This hash just needs to be converted to lower case", CentralRepoIONormalizer.normalize(CorrelationAttribute.FILES_TYPE_ID, aValidHashWithCaps).equals(aValidHash));
        assertTrue("This should fail", CentralRepoIONormalizer.normalize(FILES_TYPE_ID, anInValidHash).equals(EMPTY_STRING));        
    }
    
    public void testNormalizeDomain(){
        
    }
    
    public void testNormalizeEmail(){
        
    }
    
    public void testNormalizePhone(){
        assertTrue("We haven't acutally tested anything here - TODO.", true);
    }
    
    public void testNormalizeUsbId(){
        
    }    
}
