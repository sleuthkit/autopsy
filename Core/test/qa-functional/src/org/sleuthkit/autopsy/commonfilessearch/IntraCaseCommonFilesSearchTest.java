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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import static junit.framework.Assert.assertFalse;
import org.apache.commons.io.FileUtils;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.testutils.CaseUtils;

/**
 *
 * @author bsweeney
 */
public class IntraCaseCommonFilesSearchTest extends NbTestCase {
    
    private static final String CASE_NAME = "IntraCaseCommonFilesSearchTest";
    private static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), CASE_NAME);
    private static final File CASE_DIR = new File(CASE_DIRECTORY_PATH.toString());
    private final Path IMAGE_PATH_1 = Paths.get(this.getDataDir().toString(), "3776", "3776-1.e01.ad1");
    private final Path IMAGE_PATH_2 = Paths.get(this.getDataDir().toString(), "3776", "3776-2.e01.ad1");
    private final Path IMAGE_PATH_3 = Paths.get(this.getDataDir().toString(), "3776", "3776-3.e01.ad1");
    private final Path IMAGE_PATH_4 = Paths.get(this.getDataDir().toString(), "3776", "3776-4.e01.ad1");
    
    public IntraCaseCommonFilesSearchTest(String name) {
        super(name);
    }
    
    @Override
    public void setUp(){

        CaseUtils.createCase(CASE_DIRECTORY_PATH);   
    }
    
    /**
     * Add images #1, #2, #3, and #4 to case. Do not ingest.
     * Find all matches & all file types. Confirm no matches are found (since there are no hashes to match).
     * Find all matches on image #1 & all file types. Confirm no matches.
     */
    public void testOne(){
        
    }
    
    
    /**
     * Add #1, #2, #3, and #4 to case and ingest with hash algorithm.
     * Find all matches & all file types. Confirm file.jpg is found on all three and file.docx is found on two.
     * Find matches on ‘#1’ & all file types. Confirm same results.
     * Find matches on ‘#2 & all file types: Confirm file.jpg.
     * Find matches on ‘#3’ & all file types: Confirm file.jpg and file.docx.
     * Find matches on #4 & all file types: Confirm nothing is found
     */
    public void testTwo(){
        
    }
    
    /**
     * Add #1 and #4 to case and ingest.
     * Find all matches & all file types. Confirm nothing matches
     */
    public void testThree(){
        
    }
    
    @Override
    public void tearDown(){
        CaseUtils.closeCase();
        CaseUtils.deleteCaseDir(CASE_DIRECTORY_PATH);
    }
    
}
