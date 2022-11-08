/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this testFile except in compliance with the License.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoPlatforms;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.SqliteCentralRepoSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbChoice;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.utils.DataSourceLoader;
import org.sleuthkit.autopsy.modules.dataSourceIntegrity.DataSourceIntegrityModuleFactory;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.EmbeddedFileExtractorModuleFactory;
import org.sleuthkit.autopsy.modules.fileextmismatch.FileExtMismatchDetectorModuleFactory;
import org.sleuthkit.autopsy.modules.interestingitems.InterestingItemsIngestModuleFactory;
import org.sleuthkit.autopsy.modules.photoreccarver.PhotoRecCarverIngestModuleFactory;
import org.sleuthkit.autopsy.modules.vmextractor.VMExtractorIngestModuleFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.RdbmsCentralRepoFactory;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.modules.pictureanalyzer.PictureAnalyzerIngestModuleFactory;
import org.sleuthkit.autopsy.testutils.TestUtilsException;

/**
 * Utilities for testing intercase correlation feature.
 *
 * This will be more useful when we add more flesh out the intercase correlation
 * features and need to add more tests. In particular, testing scenarios where
 * we need different cases to be the current case will suggest that we create
 * additional test classes, and we will want to import this utility in each new
 * intercase test file.
 *
 * Description of Test Data: (Note: files of the same name and extension are
 * identical; files of the same name and differing extension are not identical.)
 *
 * Case 1 +Data Set 1 - Hash-0.dat [testFile of size 0] - Hash-A.jpg -
 * Hash-A.pdf
 *
 * +Data Set2 - Hash-0.dat [testFile of size 0] - Hash-A.jpg - Hash-A.pdf
 *
 * Case 2 +Data Set 1 - Hash-B.jpg - Hash-B.pdf +Data Set 2 - Hash-A.jpg -
 * Hash-A.pdf - Hash_D.doc
 *
 * Case 3 +Data Set 1 - Hash-A.jpg - Hash-A.pdf - Hash-C.jpg - Hash-C.pdf -
 * Hash-D.jpg +Data Set 2 - Hash-C.jpg - Hash-C.pdf - Hash-D.doc
 *
 * Frequency Breakdown (ratio of datasources a given file appears in to total
 * number of datasources):
 *
 * Hash-0.dat - moot; these are always excluded Hash-A.jpg - 4/6 Hash-A.pdf -
 * 4/6 Hash-B.jpg - 1/6 Hash-B.pdf - 1/6 Hash-C.jpg - 2/6 Hash-C.pdf - 2/6
 * Hash_D.doc - 2/6 Hash-D.jpg - 1/6
 *
 */
class InterCaseTestUtils {

    private static final Path CENTRAL_REPO_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "InterCaseCommonFilesSearchTest");
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

    final Path attrCase1Path;
    final Path attrCase2Path;
    final Path attrCase3Path;
    final Path attrCase4Path;

    static final String ATTR_CASE1 = "CommonFilesAttrs_img1_v1.vhd";
    static final String ATTR_CASE2 = "CommonFilesAttrs_img2_v1.vhd";
    static final String ATTR_CASE3 = "CommonFilesAttrs_img3_v1.vhd";
    static final String ATTR_CASE4 = "CommonFilesAttrs_img4_v1.vhd";

    private final ImageDSProcessor imageDSProcessor;

    private final IngestJobSettings hashAndFileType;
    private final IngestJobSettings hashAndNoFileType;
    private final IngestJobSettings kitchenShink;

    CorrelationAttributeInstance.Type FILE_TYPE;
    CorrelationAttributeInstance.Type DOMAIN_TYPE;
    CorrelationAttributeInstance.Type USB_ID_TYPE;
    CorrelationAttributeInstance.Type EMAIL_TYPE;
    CorrelationAttributeInstance.Type PHONE_TYPE;

    InterCaseTestUtils(NbTestCase testCase) {

        this.case1DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE1_DATASET_1);
        this.case1DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE1_DATASET_2);
        this.case2DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE2_DATASET_1);
        this.case2DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE2_DATASET_2);
        this.case3DataSet1Path = Paths.get(testCase.getDataDir().toString(), CASE3_DATASET_1);
        this.case3DataSet2Path = Paths.get(testCase.getDataDir().toString(), CASE3_DATASET_2);

        this.attrCase1Path = Paths.get(testCase.getDataDir().toString(), ATTR_CASE1);
        this.attrCase2Path = Paths.get(testCase.getDataDir().toString(), ATTR_CASE2);
        this.attrCase3Path = Paths.get(testCase.getDataDir().toString(), ATTR_CASE3);
        this.attrCase4Path = Paths.get(testCase.getDataDir().toString(), ATTR_CASE4);

        this.imageDSProcessor = new ImageDSProcessor();

        final IngestModuleTemplate pictureAnalyzerTemplate = IngestUtils.getIngestModuleTemplate(new PictureAnalyzerIngestModuleFactory());
        final IngestModuleTemplate embeddedFileExtractorTemplate = IngestUtils.getIngestModuleTemplate(new EmbeddedFileExtractorModuleFactory());
        final IngestModuleTemplate interestingItemsTemplate = IngestUtils.getIngestModuleTemplate(new InterestingItemsIngestModuleFactory());
        final IngestModuleTemplate mimeTypeLookupTemplate = IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory());
        final IngestModuleTemplate hashLookupTemplate = IngestUtils.getIngestModuleTemplate(new HashLookupModuleFactory());
        final IngestModuleTemplate vmExtractorTemplate = IngestUtils.getIngestModuleTemplate(new VMExtractorIngestModuleFactory());
        final IngestModuleTemplate photoRecTemplate = IngestUtils.getIngestModuleTemplate(new PhotoRecCarverIngestModuleFactory());
        final IngestModuleTemplate dataSourceIntegrityTemplate = IngestUtils.getIngestModuleTemplate(new DataSourceIntegrityModuleFactory());
        final IngestModuleTemplate eamDbTemplate = IngestUtils.getIngestModuleTemplate(new org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleFactory());
        final IngestModuleTemplate fileExtMismatchDetectorTemplate = IngestUtils.getIngestModuleTemplate(new FileExtMismatchDetectorModuleFactory());
        //TODO we need to figure out how to get ahold of these objects because they are required for properly filling the CR with test data
//        final IngestModuleTemplate objectDetectorTemplate = IngestUtils.getIngestModuleTemplate(new org.sleuthkit.autopsy.experimental.objectdetection.ObjectDetectionModuleFactory());
//        final IngestModuleTemplate emailParserTemplate = IngestUtils.getIngestModuleTemplate(new org.sleuthkit.autopsy.thunderbirdparser.EmailParserModuleFactory());
//        final IngestModuleTemplate recentActivityTemplate = IngestUtils.getIngestModuleTemplate(new org.sleuthkit.autopsy.recentactivity.RecentActivityExtracterModuleFactory());
//        final IngestModuleTemplate keywordSearchTemplate = IngestUtils.getIngestModuleTemplate(new org.sleuthkit.autopsy.keywordsearch.KeywordSearchModuleFactory());

        //hash and mime
        ArrayList<IngestModuleTemplate> hashAndMimeTemplate = new ArrayList<>(2);
        hashAndMimeTemplate.add(hashLookupTemplate);
        hashAndMimeTemplate.add(mimeTypeLookupTemplate);
        hashAndMimeTemplate.add(eamDbTemplate);

        this.hashAndFileType = new IngestJobSettings(InterCaseTestUtils.class.getCanonicalName(), IngestType.FILES_ONLY, hashAndMimeTemplate);

        //hash and no mime
        ArrayList<IngestModuleTemplate> hashAndNoMimeTemplate = new ArrayList<>(1);
        hashAndNoMimeTemplate.add(hashLookupTemplate);
        hashAndMimeTemplate.add(eamDbTemplate);

        this.hashAndNoFileType = new IngestJobSettings(InterCaseTestUtils.class.getCanonicalName(), IngestType.FILES_ONLY, hashAndNoMimeTemplate);

        //kitchen sink
        ArrayList<IngestModuleTemplate> kitchenSink = new ArrayList<>();
        kitchenSink.add(pictureAnalyzerTemplate);
        kitchenSink.add(embeddedFileExtractorTemplate);
        kitchenSink.add(interestingItemsTemplate);
        kitchenSink.add(mimeTypeLookupTemplate);
        kitchenSink.add(hashLookupTemplate);
        kitchenSink.add(vmExtractorTemplate);
        kitchenSink.add(photoRecTemplate);
        kitchenSink.add(dataSourceIntegrityTemplate);
        kitchenSink.add(eamDbTemplate);
        kitchenSink.add(fileExtMismatchDetectorTemplate);
        //TODO this list should probably be populated by way of loading the appropriate modules based on finding all of the @ServiceProvider(service = CentralRepoIngestModuleFactory.class) types
//        kitchenSink.add(objectDetectorTemplate);
//        kitchenSink.add(emailParserTemplate);
//        kitchenSink.add(recentActivityTemplate);
//        kitchenSink.add(keywordSearchTemplate);

        this.kitchenShink = new IngestJobSettings(InterCaseTestUtils.class.getCanonicalName(), IngestType.ALL_MODULES, kitchenSink);
    }

    void setupCorrelationTypes() {
        try {
            Collection<CorrelationAttributeInstance.Type> types = CentralRepository.getInstance().getDefinedCorrelationTypes();

            //TODO use ids instead of strings
            FILE_TYPE = types.stream().filter(type -> type.getDisplayName().equals("Files")).findAny().get();
            DOMAIN_TYPE = types.stream().filter(type -> type.getDisplayName().equals("Domains")).findAny().get();
            USB_ID_TYPE = types.stream().filter(type -> type.getDisplayName().equals("USB Devices")).findAny().get();
            EMAIL_TYPE = types.stream().filter(type -> type.getDisplayName().equals("Email Addresses")).findAny().get();
            PHONE_TYPE = types.stream().filter(type -> type.getDisplayName().equals("Phone Numbers")).findAny().get();

        } catch (CentralRepoException ex) {
            Assert.fail(ex.getMessage());

            //none of this really matters but satisfies the compiler
            FILE_TYPE = null;
            DOMAIN_TYPE = null;
            USB_ID_TYPE = null;
            EMAIL_TYPE = null;
            PHONE_TYPE = null;
        }
    }

    void clearTestDir() {
        if (CENTRAL_REPO_DIRECTORY_PATH.toFile().exists()) {
            try {
                if (CentralRepository.isEnabled()) {
                    CentralRepository.getInstance().shutdownConnections();
                }
                FileUtil.deleteDir(CENTRAL_REPO_DIRECTORY_PATH.toFile());
            } catch (CentralRepoException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }
        }
    }

    Map<Long, String> getDataSourceMap() throws NoCurrentCaseException, TskCoreException, SQLException {
        return DataSourceLoader.getAllDataSources();
    }

    Map<String, Integer> getCaseMap() throws CentralRepoException {

        if (CentralRepository.isEnabled()) {
            Map<String, Integer> mapOfCaseIdsToCase = new HashMap<>();

            for (CorrelationCase correlationCase : CentralRepository.getInstance().getCases()) {
                mapOfCaseIdsToCase.put(correlationCase.getDisplayName(), correlationCase.getID());
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

    IngestJobSettings getIngestSettingsForKitchenSink() {
        return this.kitchenShink;
    }

    void enableCentralRepo() throws CentralRepoException {

        CentralRepoDbUtil.setUseCentralRepo(true);
        SqliteCentralRepoSettings crSettings = new SqliteCentralRepoSettings();
        crSettings.setDbName(CR_DB_NAME);
        crSettings.setDbDirectory(CENTRAL_REPO_DIRECTORY_PATH.toString());
        if (!crSettings.dbDirectoryExists()) {
            crSettings.createDbDirectory();
        }

        RdbmsCentralRepoFactory centralRepoSchemaFactory = new RdbmsCentralRepoFactory(CentralRepoPlatforms.SQLITE, crSettings);
        centralRepoSchemaFactory.initializeDatabaseSchema();
        centralRepoSchemaFactory.insertDefaultDatabaseContent();

        crSettings.saveSettings();
        CentralRepoDbManager.saveDbChoice(CentralRepoDbChoice.SQLITE);
    }

    /**
     * Create the cases defined by caseNames and caseDataSourcePaths and ingest
     * each with the given settings. Null settings are permitted but IngestUtils
     * will not be run.
     *
     * The length of caseNames and caseDataSourcePaths should be the same, and
     * cases should appear in the same order.
     *
     * @param caseNames list case names
     * @param caseDataSourcePaths two dimensional array listing the datasources
     * in each case
     * @param ingestJobSettings HashLookup FileType etc...
     * @param caseReferenceToStore
     */
    Case createCases(String[] caseNames, Path[][] caseDataSourcePaths, IngestJobSettings ingestJobSettings, String caseReferenceToStore) throws TskCoreException {

        Case currentCase = null;

        if (caseNames.length != caseDataSourcePaths.length) {
            Assert.fail(new IllegalArgumentException("caseReferenceToStore should be one of the values given in the 'cases' parameter.").getMessage());
        }

        String lastCaseName = null;
        Path[] lastPathsForCase = null;
        //iterate over the collections above, creating cases, and storing
        //  just one of them for future reference
        for (int i = 0; i < caseNames.length; i++) {
            String caseName = caseNames[i];
            Path[] pathsForCase = caseDataSourcePaths[i];

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
            //hang onto this case and dont close it
            currentCase = this.createCase(lastCaseName, ingestJobSettings, true, lastPathsForCase);
        }

        if (currentCase == null) {
            Assert.fail(new IllegalArgumentException("caseReferenceToStore should be one of the values given in the 'cases' parameter.").getMessage());
            return null;
        } else {
            return currentCase;
        }
    }

    private Case createCase(String caseName, IngestJobSettings ingestJobSettings, boolean keepAlive, Path... dataSetPaths) throws TskCoreException {
        try {
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
                CaseUtils.closeCurrentCase();
                return null;
            }
        } catch (TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }

        return null;
    }

    static boolean verifyInstanceCount(CommonAttributeCountSearchResults searchDomain, int instanceCount) {
        int tally = 0;
        for (Map.Entry<Integer, CommonAttributeValueList> entry : searchDomain.getMetadata().entrySet()) {
            entry.getValue().displayDelayedMetadata();
            for (CommonAttributeValue value : entry.getValue().getMetadataList()) {

                tally += value.getInstanceCount();
            }
        }
        return tally == instanceCount;
    }

    static int getInstanceCount(CommonAttributeCountSearchResults searchDomain, String fileName, String dataSource, String crCase) {
        int tally = 0;
        for (Map.Entry<Integer, CommonAttributeValueList> entry : searchDomain.getMetadata().entrySet()) {
            entry.getValue().displayDelayedMetadata();
            for (CommonAttributeValue value : entry.getValue().getMetadataList()) {

                for (AbstractCommonAttributeInstance commonAttribute : value.getInstances()) {

                    if (commonAttribute instanceof CentralRepoCommonAttributeInstance) {
                        CentralRepoCommonAttributeInstance results = (CentralRepoCommonAttributeInstance) commonAttribute;
                        for (DisplayableItemNode din : results.generateNodes()) {

                            if (din instanceof CentralRepoCommonAttributeInstanceNode) {

                                CentralRepoCommonAttributeInstanceNode node = (CentralRepoCommonAttributeInstanceNode) din;
                                CorrelationAttributeInstance instance = node.getCorrelationAttributeInstance();

                                final String fullPath = instance.getFilePath();
                                final File testFile = new File(fullPath);

                                final String testCaseName = instance.getCorrelationCase().getDisplayName();

                                final String testFileName = testFile.getName();

                                final String testDataSource = instance.getCorrelationDataSource().getName();

                                boolean sameFileName = testFileName.equalsIgnoreCase(fileName);
                                boolean sameDataSource = testDataSource.equalsIgnoreCase(dataSource);
                                boolean sameCrCase = TimeStampUtils.removeTimeStamp(testCaseName).equalsIgnoreCase(crCase);

                                if (sameFileName && sameDataSource && sameCrCase) {
                                    tally++;
                                }
                            }

                            if (din instanceof CaseDBCommonAttributeInstanceNode) {

                                CaseDBCommonAttributeInstanceNode node = (CaseDBCommonAttributeInstanceNode) din;
                                AbstractFile file = node.getContent();

                                final String testFileName = file.getName();
                                final String testCaseName = node.getCase();
                                final String testDataSource = node.getDataSource();

                                boolean sameFileName = testFileName.equalsIgnoreCase(fileName);
                                boolean sameCaseName = TimeStampUtils.removeTimeStamp(testCaseName).equalsIgnoreCase(crCase);
                                boolean sameDataSource = testDataSource.equalsIgnoreCase(dataSource);

                                if (sameFileName && sameDataSource && sameCaseName) {
                                    tally++;
                                }
                            }
                        }
                    } else {
                        Assert.fail("Unable to cast AbstractCommonAttributeInstanceNode to InterCaseCommonAttributeSearchResults.");
                    }
                }
            }
        }
        return tally;
    }

    /**
     * Close the currently open case, delete the case directory, delete the
     * central repo db.
     */
    void tearDown() {
        try {
            CaseUtils.closeCurrentCase();
        } catch (TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Is everything in metadata a result of the given attribute type?
     *
     * @param metadata
     * @param attributeType
     *
     * @return true if yes, else false
     */
    boolean areAllResultsOfType(CommonAttributeCountSearchResults metadata, CorrelationAttributeInstance.Type attributeType) {
        for (CommonAttributeValueList matches : metadata.getMetadata().values()) {
            for (CommonAttributeValue value : matches.getMetadataList()) {
                return value
                        .getInstances()
                        .stream()
                        .allMatch(inst -> inst.getCorrelationAttributeInstanceType().equals(attributeType));
            }
            return false;
        }
        return false;
    }
}
