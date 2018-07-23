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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbPlatformEnum;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.SqliteEamDbSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.commonfilesearch.CommonAttributeSearchResults;
import org.sleuthkit.autopsy.commonfilesearch.DataSourceLoader;
import org.sleuthkit.autopsy.commonfilesearch.CommonAttributeValue;

/**
 * Utilities for testing intercase correlation feature.
 * 
 * Description of Test Data:
 * (Note: files of the same name and extension are identical; 
 * files of the same name and differing extension are not identical.)
 * 
 * Case 1
 *  +Data Set 1
 *      - Hash-0.dat    [file of size 0]
 *      - Hash-A.jpg
 *      - Hash-A.pdf
 *  +Data Set2
 *      - Hash-0.dat    [file of size -0]
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
 *      - Hash-C.jpg    [we should never find these!]
 *      - Hash-C.pdf    [we should never find these!]
 *      - Hash-D.jpg
 *  +Data Set 2
 *      - Hash-C.jpg    [we should never find these!]
 *      - Hash-C.pdf
 *      - Hash.D-doc
 */
class InterCaseUtils {

    private static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "InterCaseCommonFilesSearchTest");
    private static final String CR_DB_NAME = "testcentralrepo.db";

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

    static final String CASE1_DATASET_1 = "c1ds1_v1.vhd";
    static final String CASE1_DATASET_2 = "c1ds2_v1.vhd";
    static final String CASE2_DATASET_1 = "c2ds1_v1.vhd";
    static final String CASE2_DATASET_2 = "c2ds2_v1.vhd";
    static final String CASE3_DATASET_1 = "c3ds1_v1.vhd";
    static final String CASE3_DATASET_2 = "c3ds2_v1.vhd";

    private final ImageDSProcessor imageDSProcessor;

    private final IngestJobSettings hashAndFileType;
    private final IngestJobSettings hashAndNoFileType;
    private final DataSourceLoader dataSourceLoader;

    InterCaseUtils(NbTestCase testCase) {

        this.case1DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE1_DATASET_1);
        this.case1DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE1_DATASET_2);
        this.case2DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE1_DATASET_1);
        this.case2DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE2_DATASET_2);
        this.case3DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE3_DATASET_1);
        this.case3DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE3_DATASET_2);

        this.imageDSProcessor = new ImageDSProcessor();

        final IngestModuleTemplate hashLookupTemplate = IngestUtils.getIngestModuleTemplate(new HashLookupModuleFactory());
        final IngestModuleTemplate mimeTypeLookupTemplate = IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory());
        final IngestModuleTemplate eamDbTemplate = IngestUtils.getIngestModuleTemplate(new org.sleuthkit.autopsy.centralrepository.ingestmodule.IngestModuleFactory());

        ArrayList<IngestModuleTemplate> hashAndMimeTemplate = new ArrayList<>(2);
        hashAndMimeTemplate.add(hashLookupTemplate);
        hashAndMimeTemplate.add(mimeTypeLookupTemplate);
        hashAndMimeTemplate.add(eamDbTemplate);

        this.hashAndFileType = new IngestJobSettings(InterCaseUtils.class.getCanonicalName(), IngestType.FILES_ONLY, hashAndMimeTemplate);

        ArrayList<IngestModuleTemplate> hashAndNoMimeTemplate = new ArrayList<>(1);
        hashAndNoMimeTemplate.add(hashLookupTemplate);
        hashAndMimeTemplate.add(eamDbTemplate);

        this.hashAndNoFileType = new IngestJobSettings(InterCaseUtils.class.getCanonicalName(), IngestType.FILES_ONLY, hashAndNoMimeTemplate);

        this.dataSourceLoader = new DataSourceLoader();
    }

    void clearTestDir(){
        if(CASE_DIRECTORY_PATH.toFile().exists()){
            try{
                FileUtils.deleteDirectory(CASE_DIRECTORY_PATH.toFile());
            } catch(IOException  ex){
                Assert.fail(ex);
            }
        }
        CASE_DIRECTORY_PATH.toFile().exists();
    }
    
    Map<Long, String> getDataSourceMap() throws NoCurrentCaseException, TskCoreException, SQLException {
        return this.dataSourceLoader.getDataSourceMap();
    }

    Map<Integer, String> getCaseMap() throws EamDbException {

        if (EamDb.isEnabled()) {
            Map<Integer, String> mapOfCaseIdsToCase = new HashMap<>();

            for (CorrelationCase caze : EamDb.getInstance().getCases()) {
                mapOfCaseIdsToCase.put(caze.getID(), caze.getDisplayName());
            }
            return mapOfCaseIdsToCase;
        } else {
            //it is reasonable that this might happen...
            //  for example when we test the feature in the absence of an enabled eamdb 
            return new HashMap<>(0);
        }
    }

    IngestJobSettings getIngestSettingsForHashAndFileType() {
        return this.hashAndFileType;
    }

    IngestJobSettings getIngestSettingsForHashAndNoFileType() {
        return this.hashAndNoFileType;
    }

    void enableCentralRepo() throws EamDbException {

        SqliteEamDbSettings crSettings = new SqliteEamDbSettings();
        crSettings.setDbName(CR_DB_NAME);
        crSettings.setDbDirectory(CASE_DIRECTORY_PATH.toString());
        if (!crSettings.dbDirectoryExists()) {
            crSettings.createDbDirectory();
        }
        
        crSettings.initializeDatabaseSchema();
        crSettings.insertDefaultDatabaseContent();

        crSettings.saveSettings();

        EamDbUtil.setUseCentralRepo(true);
        EamDbPlatformEnum.setSelectedPlatform(EamDbPlatformEnum.SQLITE.name());
        EamDbPlatformEnum.saveSelectedPlatform();
    }

    /**
     * Create 3 cases and ingest each with the given settings. Null settings are
     * permitted but IngestUtils will not be run.
     *
     * @param ingestJobSettings HashLookup FileType etc...
     * @param caseReferenceToStore
     */
    Case createCases(IngestJobSettings ingestJobSettings, String caseReferenceToStore) throws TskCoreException {

        Case currentCase = null;

        String[] cases = new String[]{
            CASE1,
            CASE2,
            CASE3};

        Path[][] paths = {
            {this.case1DataSet1Path, this.case1DataSet2Path},
            {this.case2DataSet1Path, this.case2DataSet2Path},
            {this.case3DataSet1Path, this.case3DataSet2Path}};

        String lastCaseName = null;
        Path[] lastPathsForCase = null;
        //iterate over the collections above, creating cases, and storing
        //  just one of them for future reference
        for (int i = 0; i < cases.length; i++) {
            String caseName = cases[i];
            Path[] pathsForCase = paths[i];

            if (caseName.equals(caseReferenceToStore)) {
                //put aside and do this one last so we can hang onto the case
                lastCaseName = caseName;
                lastPathsForCase = pathsForCase;
            } else {
                //dont hang onto this case; close it
                this.createCase(caseName, ingestJobSettings, false, pathsForCase);
            }
        }

        if (lastCaseName != null && lastPathsForCase != null) {
            //hang onto this caes and dont close it
            currentCase = this.createCase(lastCaseName, ingestJobSettings, true, lastPathsForCase);
        }

        if (currentCase == null) {
            Assert.fail(new IllegalArgumentException("caseReferenceToStore should be one of: CASE1, CASE2, CASE3"));
            return null;
        } else {
            return currentCase;
        }
    }

    private Case createCase(String caseName, IngestJobSettings ingestJobSettings, boolean keepAlive, Path... dataSetPaths) throws TskCoreException {

        Case caze = CaseUtils.createAsCurrentCase(caseName);
        for (Path dataSetPath : dataSetPaths) {
            IngestUtils.addDataSource(this.imageDSProcessor, dataSetPath);
        }
        if (ingestJobSettings != null) {
            IngestUtils.runIngestJob(caze.getDataSources(), ingestJobSettings);
        }
        if (keepAlive) {
            return caze;
        } else {
            CaseUtils.closeCurrentCase(false);
            return null;
        }
    }
    
    static boolean verifyInstanceExistanceAndCount(CommonAttributeSearchResults searchDomain, String fileName, String dataSource, String crCase, int instanceCount){
        
        int tally = 0;
        
        for(Map.Entry<Integer, List<CommonAttributeValue>> file : searchDomain.getValues().entrySet()){
            
            //Collection<IntraCaseCommonAttributeSearchResults> fileInstances = file.getValue();
            
//            for(IntraCaseCommonAttributeSearchResults fileInstance : fileInstances){
//                
//                
//            }
        }
        
        return false;
    }
    
    static Map<Long, String> mapFileInstancesToDataSource(CommonAttributeSearchResults metadata){
        return IntraCaseUtils.mapFileInstancesToDataSources(metadata);
    }
    
//    static List<AbstractFile> getFiles(Set<String> md5s){
//        
//    }

    /**
     * Close the currently open case, delete the case directory, delete the
     * central repo db.
     */
    void tearDown() {
        
        CaseUtils.closeCurrentCase(false);
        
        String[] cases  = new String[]{CASE1,CASE2,CASE3};
        
        try {
            for(String caze : cases){
                CaseUtils.deleteCaseDir(new File(caze));
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
}
