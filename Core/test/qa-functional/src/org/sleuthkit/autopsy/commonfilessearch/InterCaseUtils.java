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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbPlatformEnum;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.commonfilesearch.DataSourceLoader;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for testing intercase correlation feature.
 * 
 * Description of Test Data:
 * (Note: files of the same name and extension are identical; 
 * files of the same name and differing extension are not identical.)
 * 
 * Case 1
 *  +Data Set 1
 *      - Hash-0.dat [file of size 0]
 *      - Hash-A.jpg
 *      - Hash-A.pdf
 *  +Data Set2
 *      - Hash-0.dat [file of size -0]
 *      - Hash-A.jpg
 *      - Hash-A.pdf
 * Case 2
 *  +Data Set 1
 *      - Hash-A.jpg
 *      - Hash-A.pdf
 *  +Data Set 2
 *      - Hash-A.jpg
 *      - Hash-A.pdf
 *      - Hash_D.doc
 * Case 3
 *  +Data Set 1
 *      - Hash-A.jpg
 *      - Hash-A.pdf
 *      - Hash-C.jpg
 *      - Hash-C.pdf
 *      - Hash-D.jpg
 *  +Data Set 2
 *      - Hash-C.jpg
 *      - Hash-C.pdf
 *      - Hash.D-doc
 */
public class InterCaseUtils {
    
    private static final String CASE_NAME = "InterCaseCommonFilesSearchTest";
    static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), CASE_NAME);

    static final String CASE1 = "Case1";
    static final String CASE2 = "Case2";
    static final String CASE3 = "Case3";
    static final String CASE4 = "Case4";
    
    final Path case1DataSet1Path;
    final Path case1DataSet2Path;
    final Path case2DataSet1Path;
    final Path case2DataSet2Path;
    final Path case3DataSet1Path;
    final Path case3DataSet2Path;
    
    static final String HASH_0_DAT = "Hash-0.dat";
    static final String HASH_A_JPG = "Hash-A.jpg";
    static final String HASH_A_PDF = "Hash-A.pdf";
    static final String HASH_B_JPG = "Hash-B.jpg";
    static final String HASH_B_PDF = "Hash-B.pdf";
    static final String HASH_C_JPG = "Hash-C.jpg";
    static final String HASH_C_PDF = "Hash-C.pdf";
    static final String HASH_D_JPG = "Hash-D.jpg";
    static final String HASH_D_DOC = "Hash-D.doc";
    
    static final String CASE1_DATASET_1 = "c1ds1.vhd";
    static final String CASE1_DATASET_2 = "c1ds2.vhd";
    static final String CASE2_DATASET_1 = "c2ds1.vhd";
    static final String CASE2_DATASET_2 = "c2ds2.vhd";
    static final String CASE3_DATASET_1 = "c3ds1.vhd";
    static final String CASE3_DATASET_2 = "c3ds2.vhd";
    
    private final DataSourceLoader dataSourceLoader;
    private final ImageDSProcessor imageDSProcessor;
    
    private Case caseReference;
    
    InterCaseUtils(NbTestCase testCase){
        
        this.case1DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE1_DATASET_1);
        this.case1DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE1_DATASET_2);
        this.case2DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE1_DATASET_1);
        this.case2DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE2_DATASET_2);
        this.case3DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE3_DATASET_1);
        this.case3DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE3_DATASET_2);
        
        this.dataSourceLoader = new DataSourceLoader();
        this.imageDSProcessor = new ImageDSProcessor();
    }
    
    void enableCentralRepo(){
        EamDbUtil.setUseCentralRepo(true);
        EamDbPlatformEnum.setSelectedPlatform(EamDbPlatformEnum.SQLITE.name());
        EamDbPlatformEnum.saveSelectedPlatform();
    }
    
    /**
     * Create a case and ingest each with the given settings.  Null settings
     * are permitted but IngestUtils will not be run.
     * 
     * @param ingestJobSettings HashLookup FileType etc...
     */
    void createCases(IngestJobSettings ingestJobSettings){
        
        try {
            this.createCase(CASE1, ingestJobSettings, false, new Path[]{});
            this.createCase(CASE2, ingestJobSettings, false, new Path[]{});
            this.createCase(CASE3, ingestJobSettings, false, new Path[]{});
            this.caseReference = this.createCase(CASE4, ingestJobSettings, true, new Path[]{});
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            //TODO fail test
        }        
    }
    
    private Case createCase(String caseName, IngestJobSettings ingestJobSettings, boolean keepAlive, Path... dataSetPaths) throws TskCoreException{
        
        Case caze = CaseUtils.createAsCurrentCase(caseName);
        for(Path dataSetPath : dataSetPaths){
            IngestUtils.addDataSource(this.imageDSProcessor, dataSetPath);
        }
        if(ingestJobSettings != null){
            IngestUtils.runIngestJob(caze.getDataSources(), ingestJobSettings);
        }
        if(keepAlive){
           return caze; 
        } else {
            CaseUtils.closeCurrentCase(false);
            return null;
        }
    }
    
    /**
     * TODO some more cool verbiage
     * Could be null if createCases has not yet been run.
     * @return 
     */
    Case getCaseReference() throws Exception{
        if(this.caseReference == null){
            throw new Exception("Must run createCases(...) first.");
        } else {
            return this.caseReference;
        }
    }
    
    void tearDown(){
        //close cases
        //delete case dirs
        //delete central repo db
    }
    
}
