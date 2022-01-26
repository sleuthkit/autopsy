/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import junit.framework.Assert;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.TestUtilsException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.HostManager;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.OsAccountManager;
import org.sleuthkit.datamodel.OsAccountRealm;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;


/**
 *
 */
public class TableSearchTest extends NbTestCase {

    private static final String MODULE_NAME = "TableSearchTest";

    // Custom artifact and attribute names and display names
    private static final String CUSTOM_DA_TYPE_NAME = "SEARCH_TEST_CUSTOM_DA_TYPE";
    private static final String CUSTOM_DA_TYPE_DISPLAY_NAME = "Search test custom data artifact type";
    private static final String CUSTOM_AR_TYPE_NAME = "SEARCH_TEST_CUSTOM_AR_TYPE";
    private static final String CUSTOM_AR_TYPE_DISPLAY_NAME = "Search test custom analysis result type";
    private static final String CUSTOM_ATTR_TYPE_NAME = "SEARCH_TEST_CUSTOM_ATTRIBUTE_TYPE";
    private static final String CUSTOM_ATTR_TYPE_DISPLAY_NAME = "Search test custom attribute type";

    // Values used for attributes in the artifact tests
    private static final String ARTIFACT_COMMENT = "Artifact comment";
    private static final String ARTIFACT_CUSTOM_ATTR_STRING = "Custom attribute string";
    private static final int ARTIFACT_INT = 5;
    private static final double ARTIFACT_DOUBLE = 7.89;
    private static final String ARTIFACT_CONCLUSION = "Test conclusion";
    private static final String ARTIFACT_CONFIGURATION = "Test configuration";
    private static final String ARTIFACT_JUSTIFICATION = "Test justification";
    private static final Score ARTIFACT_SCORE = Score.SCORE_LIKELY_NOTABLE;
    private static final long ARTIFACT_COUNT_WEB_BOOKMARK = 125;
    private static final long ARTIFACT_COUNT_YARA = 150;
    
    // Values for the hash set hit tests
    private static final String HASH_SET_1 = "Hash Set 1";
    private static final String HASH_SET_2 = "Hash Set 2";
    private static final String HASH_HIT_VALUE = "aefe58b6dc38bbd7f2b7861e7e8f7539";
    
    // Values for the keyword hit tests
    private static final String KEYWORD_SET_1 = "Keyword Set 1";
    private static final String KEYWORD_SET_2 = "Keyword Set 2";
    private static final String KEYWORD = "bomb";  
    private static final String KEYWORD_REGEX = "bomb*";  
    private static final String KEYWORD_PREVIEW = "There is a bomb.";
    
    // Extension and MIME type test
    private static AbstractFile customFile;
    private static final String CUSTOM_MIME_TYPE = "fake/type";
    private static final String CUSTOM_MIME_TYPE_FILE_NAME = "test.fake";
    private static final String CUSTOM_EXTENSION = "fake";
    private static final Set<String> CUSTOM_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("." + CUSTOM_EXTENSION))); //NON-NLS
    private static final Set<String> EMPTY_RESULT_SET_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(".blah", ".blah2", ".crazy"))); //NON-NLS
    
    // Tag test
    private static final String TAG_COMMENT = "Tag comment";
    private static final String TAG_DESCRIPTION = "Tag description";
    private static final String MD5_COLUMN = "MD5 Hash";
    private static final String FILE_PATH_COLUMN = "File Path";
    private static final String MODIFIED_TIME_COLUMN = "Modified Time";
    private static final String SOURCE_NAME_COLUMN = "Source Name";
    private static final String SOURCE_FILE_PATH_COLUMN = "Source File Path";
    
    // File system test
    private static final String PERSON_NAME = "Person1";
    private static final String PERSON_HOST_NAME1 = "Host for Person A";
    private static final String PERSON_HOST_NAME2 = "Host for Person B";
    
    // OS Accounts test
    private static final String REALM_NAME_COLUMN = "Realm Name";
    private static final String HOST_COLUMN = "Host";
    
    // Communications accounts test
    private static final String ACCOUNT_TYPE_COLUMN = "Account Type";
    private static final String ID_COLUMN = "ID";
    private static final String EMAIL_A = "aaa@yahoo.com";
    private static final String EMAIL_B = "bbb@gmail.com";
    private static final String EMAIL_C = "ccc@funmail.com";
    private static final String PHONENUM_1 = "1117771111";
    private static final String PHONENUM_2 = "2223337777";

    /////////////////////////////////////////////////
    // Data to be used across the test methods.
    // These are initialized in setUpCaseDatabase().
    /////////////////////////////////////////////////
    Case openCase = null;          // The case for testing
    SleuthkitCase db = null;       // The case database
    Blackboard blackboard = null;  // The blackboard
    TagsManager tagsManager = null;// Tags manager
    OsAccountManager accountMgr = null;

    DataSource dataSource1 = null; // A local files data source
    DataSource dataSource2 = null; // A local files data source
    DataSource dataSource3 = null; // A local files data source
    
    BlackboardArtifact.Type customDataArtifactType = null;   // A custom data artifact type
    BlackboardArtifact.Type customAnalysisResultType = null; // A custom analysis result type
    BlackboardAttribute.Type customAttributeType = null;     // A custom attribute type
    
    // Data artifact test
    DataArtifact customDataArtifact = null;            // A custom data artifact in dataSource1
    Content customDataArtifactSourceFile = null;  // The source of customDataArtifact
    AbstractFile customDataArtifactLinkedFile = null;  // The linked file of customDataArtifact
    
    // Analysis result test
    AnalysisResult customAnalysisResult = null;     // A custom analysis result in dataSource 1
    Content customAnalysisResultSource = null;    // The source of customDataArtifact
    
    // Hash hits test
    AnalysisResult hashHitAnalysisResult = null;  // A hash hit 
    Content fileWithHashHit = null;               // The file associated with the hash hit above
    
    // Keyword hits test
    AnalysisResult keywordHitAnalysisResult = null; // A keyword hit
    Content keywordHitSource = null;                 // The source of the keyword hit above
    
    // File system test
    Host fsTestHostA = null;       // A host
    Image fsTestImageA = null;     // An image
    VolumeSystem fsTestVsA = null; // A volume system
    Volume fsTestVolumeA1 = null;  // A volume
    Volume fsTestVolumeA2 = null;  // Another volume
    Volume fsTestVolumeA3 = null;  // Another volume
    FileSystem fsTestFsA = null;   // A file system
    AbstractFile fsTestRootDirA = null;  // The root directory
    Image fsTestImageB = null;    // Another image
    Volume fsTestVolumeB1 = null; // Another volume
    Pool fsTestPoolB = null;       // A pool
    Person person1 = null;         // A person
    Host personHost1 = null;       // A host belonging to the above person

    // Tags test
    TagName knownTag1 = null;
    TagName tag2 = null;
    
    // OS Accounts test
    OsAccount osAccount1 = null;

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(TableSearchTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public TableSearchTest(String name) {
        super(name);
    }

    // Main search method
    public void testTableSearches() {
        // Set up the database
        setUpCaseDatabase();

        // Run tests
        dataArtifactSearchTest();
        analysisResultSearchTest();
        hashHitSearchTest();
        keywordHitSearchTest();
        mimeSearchTest();
        extensionSearchTest();
        sizeSearchTest();
        fileSystemTest();
        tagsTest();
        OsAccountsTest();
        commAccountsSearchTest();
    }

    /**
     * Create a case and add sample data.
     */
    private void setUpCaseDatabase() {
        SleuthkitCase.CaseDbTransaction trans = null;
        try {
            // Create a test case
            openCase = CaseUtils.createAsCurrentCase("testTableSearchCase");
            db = openCase.getSleuthkitCase();
            blackboard = db.getBlackboard();
            tagsManager = openCase.getServices().getTagsManager();
            accountMgr = openCase.getSleuthkitCase().getOsAccountManager();

            // Add two logical files data sources
            trans = db.beginTransaction();
            dataSource1 = db.addLocalFilesDataSource("devId1", "C:\\Fake\\Path\\1", "EST", null, trans);
            dataSource2 = db.addLocalFilesDataSource("devId2", "C:\\Fake\\Path\\2", "EST", null, trans);
            dataSource3 = db.addLocalFilesDataSource("devId3", "C:\\Fake\\Path\\3", "EST", null, trans);
            trans.commit();
            trans = null;

            // Add files
            AbstractFile folderA1 = db.addLocalDirectory(dataSource1.getId(), "folder1");
            AbstractFile fileA1 = db.addLocalFile("file1.txt", "", 10, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderA1);
            fileA1.setMIMEType("text/plain");
            fileA1.save();
            AbstractFile folderA2 = db.addLocalDirectory(dataSource1.getId(), "folder2");
            AbstractFile fileA2 = db.addLocalFile("file2.jpg", "", 60000000, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderA2);
            fileA2.setMIMEType("image/jpeg");
            fileA2.save();
            AbstractFile folderA3 = db.addLocalDirectory(folderA2.getId(), "folder3");
            AbstractFile fileA3 = db.addLocalFile("file3.doc", "", 150000000, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderA3);
            fileA3.setMIMEType("application/msword");
            fileA3.save();
            AbstractFile fileA4 = db.addLocalFile("file4.txt", "", 100, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderA3);
            fileA4.setMIMEType("text/plain");
            fileA4.save();

            AbstractFile folderB1 = db.addLocalDirectory(dataSource2.getId(), "folder1");
            AbstractFile fileB1 = db.addLocalFile("fileA.txt", "", 210000000, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderB1);
            fileB1.setMIMEType("text/plain");
            fileB1.save();

            customFile = db.addLocalFile(CUSTOM_MIME_TYPE_FILE_NAME, "", 67000000, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderB1);
            customFile.setMIMEType(CUSTOM_MIME_TYPE);
            customFile.save();

            // Create a custom artifact and attribute types
            customDataArtifactType = blackboard.getOrAddArtifactType(CUSTOM_DA_TYPE_NAME, CUSTOM_DA_TYPE_DISPLAY_NAME, BlackboardArtifact.Category.DATA_ARTIFACT);

            customAnalysisResultType = blackboard.getOrAddArtifactType(CUSTOM_AR_TYPE_NAME, CUSTOM_AR_TYPE_DISPLAY_NAME, BlackboardArtifact.Category.ANALYSIS_RESULT);
            customAttributeType = blackboard.getOrAddAttributeType(CUSTOM_ATTR_TYPE_NAME, 
                    BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING, CUSTOM_ATTR_TYPE_DISPLAY_NAME);

            // Add data artifacts
            // DataSource1: contact, bookmark, and custom type
            // DataSource2: contact
            List<BlackboardAttribute> attrs = new ArrayList<>();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Contact 1"));
            fileA1.newDataArtifact(BlackboardArtifact.Type.TSK_CONTACT, attrs);

            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Bookmark 1"));
            fileA2.newDataArtifact(BlackboardArtifact.Type.TSK_GPS_BOOKMARK, attrs);

            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Contact 2"));
            fileB1.newDataArtifact(BlackboardArtifact.Type.TSK_CONTACT, attrs);

            // This is the main artifact for the DataArtifact test. Make attributes of several types.
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, ARTIFACT_COMMENT));
            attrs.add(new BlackboardAttribute(customAttributeType, MODULE_NAME, ARTIFACT_CUSTOM_ATTR_STRING));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COUNT, MODULE_NAME, ARTIFACT_INT));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_ENTROPY, MODULE_NAME, ARTIFACT_DOUBLE));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_PATH_ID, MODULE_NAME, fileA2.getId()));
            customDataArtifact = fileA3.newDataArtifact(customDataArtifactType, attrs);
            customDataArtifactSourceFile = fileA3;
            customDataArtifactLinkedFile = fileA2;
            
            // Add a lot of web bookmark data artifacts
            for (int i = 0;i < ARTIFACT_COUNT_WEB_BOOKMARK;i++) {
                attrs.clear();
                attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, Integer.toString(i)));
                fileA1.newDataArtifact(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, attrs);
            }
            
            // Add analysis results
            // Data source 1: Encryption detected (2), custom type
            // Data source 2: Encryption detected
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Encryption detected 1"));
            fileA1.newAnalysisResult(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, Score.SCORE_NONE, "conclusion", "configuration", "justification", attrs);
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Encryption detected 1"));
            fileA2.newAnalysisResult(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, Score.SCORE_LIKELY_NOTABLE, "conclusion", "configuration", "justification", attrs);
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Encryption detected 2"));
            fileB1.newAnalysisResult(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, Score.SCORE_NOTABLE, "conclusion", "configuration", "justification", attrs);
            
            // This is the main artifact for the AnalysisResult test. Make attributes of several types.
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, ARTIFACT_COMMENT));
            attrs.add(new BlackboardAttribute(customAttributeType, MODULE_NAME, ARTIFACT_CUSTOM_ATTR_STRING));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COUNT, MODULE_NAME, ARTIFACT_INT));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_ENTROPY, MODULE_NAME, ARTIFACT_DOUBLE));
            customAnalysisResult = customDataArtifact.newAnalysisResult(customAnalysisResultType, ARTIFACT_SCORE, ARTIFACT_CONCLUSION, ARTIFACT_CONFIGURATION, ARTIFACT_JUSTIFICATION, attrs).getAnalysisResult();
            customAnalysisResultSource = customDataArtifact;
            
            // Add a lot of YARA hit analysis results
            for (int i = 0;i < ARTIFACT_COUNT_YARA;i++) {
                attrs.clear();
                attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, Integer.toString(i)));
                fileA1.newAnalysisResult(BlackboardArtifact.Type.TSK_YARA_HIT, Score.SCORE_NOTABLE, "conclusion", "configuration", "justification", attrs);
            }
            
            // Add hash hits
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, HASH_SET_1));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_HASH_MD5, MODULE_NAME, "43fffda5c5edd8e9c647f1df476717de"));
            fileA1.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_HASHSET_HIT, Score.SCORE_NOTABLE, 
                    null, HASH_SET_1, null, attrs);
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, HASH_SET_1));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_HASH_MD5, MODULE_NAME, "b7cde263cc1b5df5a13aeec742637a89"));
            fileA2.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_HASHSET_HIT, Score.SCORE_NOTABLE, 
                    null, HASH_SET_1, null, attrs);    
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, HASH_SET_2));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_HASH_MD5, MODULE_NAME, "333510c92f8cd755f163328c2bac81fe"));
            fileA3.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_HASHSET_HIT, Score.SCORE_NONE, 
                    null, HASH_SET_2, null, attrs); 
            
            // This is the artifact that will get most of the testing
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, HASH_SET_1));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_HASH_MD5, MODULE_NAME, HASH_HIT_VALUE));
            hashHitAnalysisResult = fileB1.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_HASHSET_HIT, Score.SCORE_NOTABLE, 
                    null, HASH_SET_1, null, attrs).getAnalysisResult();  
            fileWithHashHit = fileB1;
            
            // Add keyword hits
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, KEYWORD_SET_1));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD, MODULE_NAME, "keyword1"));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, TskData.KeywordSearchQueryType.LITERAL.getType()));
            fileA1.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_KEYWORD_HIT, Score.SCORE_NOTABLE, 
                    null, KEYWORD_SET_1, null, attrs);  
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, KEYWORD_SET_2));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD, MODULE_NAME, "keyword2"));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, TskData.KeywordSearchQueryType.LITERAL.getType()));
            fileA3.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_KEYWORD_HIT, Score.SCORE_NOTABLE, 
                    null, KEYWORD_SET_2, null, attrs);
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, KEYWORD_SET_2));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD, MODULE_NAME, KEYWORD));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD_REGEXP, MODULE_NAME, KEYWORD_REGEX));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD_PREVIEW, MODULE_NAME, KEYWORD_PREVIEW));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, TskData.KeywordSearchQueryType.REGEX.getType()));
            fileB1.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_KEYWORD_HIT, Score.SCORE_NOTABLE, 
                    null, KEYWORD_SET_2, null, attrs);            
            
            // This is the artifact that will get most of the testing. It is in data source 2 and has the previous hash hit as source.
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, KEYWORD_SET_1));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD, MODULE_NAME, KEYWORD));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD_PREVIEW, MODULE_NAME, KEYWORD_PREVIEW));
            keywordHitAnalysisResult = hashHitAnalysisResult.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_KEYWORD_HIT, Score.SCORE_NOTABLE, 
                    null, KEYWORD_SET_1, null, attrs).getAnalysisResult();
            keywordHitSource = hashHitAnalysisResult;
            
            // Create a normal image
            // fsTestImageA (Host: fsTestHostA)
            // - fsTestVsA
            // -- fsTestVolumeA1
            // --- fsTestFsA
            // ---- fsTestRootDirA
            // ----- (3 files)
            // -- fsTestVolumeA2
            // -- fsTestVolumeA3
            fsTestHostA = db.getHostManager().newHost("File system test host");
            trans = db.beginTransaction();
            fsTestImageA = db.addImage(TskData.TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_DETECT, 512, 1024, "image1", Arrays.asList("C:\\Fake\\Path\\4"),
                    "EST", null, null, null, "deviceID12345", fsTestHostA, trans);
            fsTestVsA = db.addVolumeSystem(fsTestImageA.getId(), TskData.TSK_VS_TYPE_ENUM.TSK_VS_TYPE_DOS, 0, 1024, trans);
            fsTestVolumeA1 = db.addVolume(fsTestVsA.getId(), 0, 0, 512, "Test vol A1", 0, trans);
            fsTestVolumeA2 = db.addVolume(fsTestVsA.getId(), 1, 512, 512, "Test vol A2", 0, trans);
            fsTestVolumeA3 = db.addVolume(fsTestVsA.getId(), 2, 1024, 512, "Test vol A3", 0, trans);
            long rootInum = 1;
            fsTestFsA = db.addFileSystem(fsTestVolumeA1.getId(), 0, TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_EXT2, 512, 1, 
                    rootInum, rootInum, 10, "Test file system", trans);
            trans.commit();
            trans = null;
            fsTestRootDirA = db.addFileSystemFile(fsTestImageA.getId(), fsTestFsA.getId(), 
                    "", rootInum, 0, 
                    TskData.TSK_FS_ATTR_TYPE_ENUM.TSK_FS_ATTR_TYPE_DEFAULT, 0, 
                    TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC, (short)0, 0, 
                    0, 0, 0, 0, false, fsTestFsA);
            db.addFileSystemFile(fsTestImageA.getId(), fsTestFsA.getId(), 
                    "Test file 1", 0, 0, 
                    TskData.TSK_FS_ATTR_TYPE_ENUM.TSK_FS_ATTR_TYPE_DEFAULT, 0, 
                    TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC, (short)0, 123, 
                    0, 0, 0, 0, true, fsTestRootDirA);
            db.addFileSystemFile(fsTestImageA.getId(), fsTestFsA.getId(), 
                    "Test file 2", 0, 0, 
                    TskData.TSK_FS_ATTR_TYPE_ENUM.TSK_FS_ATTR_TYPE_DEFAULT, 0, 
                    TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC, (short)0, 456, 
                    0, 0, 0, 0, true, fsTestRootDirA);
            db.addFileSystemFile(fsTestImageA.getId(), fsTestFsA.getId(), 
                    "Test file 3", 0, 0, 
                    TskData.TSK_FS_ATTR_TYPE_ENUM.TSK_FS_ATTR_TYPE_DEFAULT, 0, 
                    TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC, (short)0, 789, 
                    0, 0, 0, 0, true, fsTestRootDirA);
        
            // Create an image with some odd structures for testing
            trans = db.beginTransaction();
            fsTestImageB = db.addImage(TskData.TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_DETECT, 512, 1024, "image2", Arrays.asList("C:\\Fake\\Path\\5"),
                    "EST", null, null, null, "deviceID678", fsTestHostA, trans);
            
            // Images can have VS, pool, FS, file, artifact, and report children.
            // Add a VS, pool, and local file
            VolumeSystem vsB = db.addVolumeSystem(fsTestImageB.getId(), TskData.TSK_VS_TYPE_ENUM.TSK_VS_TYPE_BSD, 0, 2048, trans);
            db.addPool(fsTestImageB.getId(), TskData.TSK_POOL_TYPE_ENUM.TSK_POOL_TYPE_APFS, trans);
            db.addLocalFile("Test local file B1", "C:\\Fake\\Path\\6", 6000, 0, 0, 0, 0, 
                    true, TskData.EncodingType.NONE, fsTestImageB, trans);
            
            // Volumes can have pool, FS, file, and artifact children
            fsTestVolumeB1 = db.addVolume(vsB.getId(), 0, 0, 512, "Test vol B1", 0, trans);
            fsTestPoolB = db.addPool(fsTestVolumeB1.getId(), TskData.TSK_POOL_TYPE_ENUM.TSK_POOL_TYPE_APFS, trans);
            db.addLocalFile("Test local file B2", "C:\\Fake\\Path\\7", 7000, 0, 0, 0, 0, 
                    true, TskData.EncodingType.NONE, fsTestVolumeB1, trans);
            
            // Pools can have VS, file, and artifact children
            VolumeSystem vsB2 = db.addVolumeSystem(fsTestPoolB.getId(), TskData.TSK_VS_TYPE_ENUM.TSK_VS_TYPE_GPT, 0, 2048, trans);
            db.addVolume(vsB2.getId(), 0, 0, 512, "Test vol B2", 0, trans);
            db.addLocalFile("Test local file B3", "C:\\Fake\\Path\\8", 8000, 0, 0, 0, 0, 
                    true, TskData.EncodingType.NONE, fsTestPoolB, trans);
      
            trans.commit();
            trans = null;
            
            // Create a person associated with two hosts
            person1 = db.getPersonManager().newPerson(PERSON_NAME);
            personHost1 = db.getHostManager().newHost(PERSON_HOST_NAME1);
            Host personHost2 = db.getHostManager().newHost(PERSON_HOST_NAME2);
            db.getPersonManager().addHostsToPerson(person1, Arrays.asList(personHost1, personHost2));

            // Add tags ----
            knownTag1 = tagsManager.addTagName("Tag 1", TAG_DESCRIPTION, TagName.HTML_COLOR.RED, TskData.FileKnown.KNOWN);
            tag2 = tagsManager.addTagName("Tag 2", "Descrition");
            
            // Tag the custom artifacts in data source 1
            openCase.getServices().getTagsManager().addBlackboardArtifactTag(customDataArtifact, knownTag1, TAG_COMMENT);
            openCase.getServices().getTagsManager().addBlackboardArtifactTag(customAnalysisResult, tag2, "Comment 2");
            
            // Tag file in data source 1
            openCase.getServices().getTagsManager().addContentTag(fileA2, tag2);
            openCase.getServices().getTagsManager().addContentTag(fileA3, tag2);            
            
            // Tag file in data source 2
            openCase.getServices().getTagsManager().addContentTag(fileB1, tag2);
            
            // Tag the custom file in data source 2
            openCase.getServices().getTagsManager().addContentTag(customFile, knownTag1);
            
            // Add OS Accounts ---------------------            
            HostManager hostMrg = openCase.getSleuthkitCase().getHostManager();                        
            Host host1 = hostMrg.getHostByDataSource(dataSource1);            
            OsAccount osAccount2 = accountMgr.newWindowsOsAccount("S-1-5-21-647283-46237-200", null, null, host1, OsAccountRealm.RealmScope.LOCAL);
            accountMgr.newOsAccountInstance(osAccount2, dataSource1, OsAccountInstance.OsAccountInstanceType.ACCESSED);
            OsAccount osAccount3 = accountMgr.newWindowsOsAccount("S-1-5-21-647283-46237-300", null, null, host1, OsAccountRealm.RealmScope.UNKNOWN);
            accountMgr.newOsAccountInstance(osAccount3, dataSource1, OsAccountInstance.OsAccountInstanceType.REFERENCED);
            
            Host host2 = hostMrg.getHostByDataSource(dataSource2);
            osAccount1 = accountMgr.newWindowsOsAccount("S-1-5-21-647283-46237-100", null, null, host2, OsAccountRealm.RealmScope.DOMAIN);
            accountMgr.newOsAccountInstance(osAccount1, dataSource2, OsAccountInstance.OsAccountInstanceType.LAUNCHED);
            
                                
            openCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.EMAIL, EMAIL_A, "Test Module", fileA1, Collections.emptyList(), null);
            openCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.EMAIL, EMAIL_B, "Test Module", fileA2, Collections.emptyList(), null);
            openCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.DEVICE, "devId1", "Test Module", fileA2, Collections.emptyList(), null);
            openCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.PHONE, PHONENUM_1, "Test Module", fileA2, Collections.emptyList(), null);
            
            openCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.EMAIL, EMAIL_C, "Test Module", customFile, Collections.emptyList(), null);
            openCase.getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.PHONE, PHONENUM_2, "Test Module", customFile, Collections.emptyList(), null);

        } catch (TestUtilsException | TskCoreException | BlackboardException | TagsManager.TagNameAlreadyExistsException | OsAccountManager.NotUserSIDException ex) {
            if (trans != null) {
                try {
                    trans.rollback();
                } catch (TskCoreException ex2) {
                    Exceptions.printStackTrace(ex2);
                }
            }
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    public void dataArtifactSearchTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);

        try {
            // Get all contacts
            DataArtifactSearchParam param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_CONTACT, null);
            DataArtifactDAO dataArtifactDAO = MainDAO.getInstance().getDataArtifactsDAO();

            DataArtifactTableSearchResultsDTO results = dataArtifactDAO.getDataArtifactsForTable(param, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_CONTACT, results.getArtifactType());
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());
            
            // Get contacts from data source 2
            param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_CONTACT, dataSource2.getId());
            results = dataArtifactDAO.getDataArtifactsForTable(param, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_CONTACT, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get bookmarks from data source 2
            param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, dataSource2.getId());
            results = dataArtifactDAO.getDataArtifactsForTable(param, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, results.getArtifactType());
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get all custom artifacts
            param = new DataArtifactSearchParam(customDataArtifactType, null);
            results = dataArtifactDAO.getDataArtifactsForTable(param, 0, null);
            assertEquals(customDataArtifactType, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Check that a few of the expected column names are present
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains(BlackboardAttribute.Type.TSK_COMMENT.getDisplayName()));
            assertTrue(columnDisplayNames.contains(BlackboardAttribute.Type.TSK_COUNT.getDisplayName()));
            assertTrue(columnDisplayNames.contains(customAttributeType.getDisplayName()));
            
            // Check that the analysis result columns are not present
            assertFalse(columnDisplayNames.contains("Justification"));
            assertFalse(columnDisplayNames.contains("Conclusion"));

            // Get one of the rows
            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof DataArtifactRowDTO);
            DataArtifactRowDTO dataArtifactRowDTO = (DataArtifactRowDTO) rowDTO;

            // Check that the artifact, source content and linked file are correct
            assertEquals(customDataArtifact, dataArtifactRowDTO.getDataArtifact());
            assertEquals(customDataArtifactSourceFile, dataArtifactRowDTO.getSrcContent());
            //assertEquals(customDataArtifactLinkedFile, dataArtifactRowDTO.getLinkedFile()); I'm doing something wrong or this isn't working yet

            // Check that some of the expected column values are present
            assertTrue(dataArtifactRowDTO.getCellValues().contains(ARTIFACT_CUSTOM_ATTR_STRING));
            assertTrue(dataArtifactRowDTO.getCellValues().contains(ARTIFACT_COMMENT));
            assertTrue(dataArtifactRowDTO.getCellValues().contains(ARTIFACT_INT));
            assertTrue(dataArtifactRowDTO.getCellValues().contains(ARTIFACT_DOUBLE));

            // Test paging
            Long pageSize = new Long(100);
            assertTrue(ARTIFACT_COUNT_WEB_BOOKMARK > pageSize);
            
            // Get the first page
            param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, null);
            results = dataArtifactDAO.getDataArtifactsForTable(param, 0, pageSize);
            assertEquals(ARTIFACT_COUNT_WEB_BOOKMARK, results.getTotalResultsCount());
            assertEquals(pageSize.longValue(), results.getItems().size());
            
            // Save all artifact IDs from the first page
            Set<Long> firstPageObjIds = new HashSet<>();
            for (RowDTO row : results.getItems()) {
                assertTrue(row instanceof DataArtifactRowDTO);
                DataArtifactRowDTO dataRow = (DataArtifactRowDTO) row;
                assertTrue(dataRow.getDataArtifact() != null);
                firstPageObjIds.add(dataRow.getDataArtifact().getId());
            }
            assertEquals(pageSize.longValue(), firstPageObjIds.size());
         
            // Get the second page
            param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, null);
            results = dataArtifactDAO.getDataArtifactsForTable(param, pageSize, pageSize);
            assertEquals(ARTIFACT_COUNT_WEB_BOOKMARK, results.getTotalResultsCount());
            assertEquals(ARTIFACT_COUNT_WEB_BOOKMARK - pageSize, results.getItems().size());
            
            // Make sure no artifacts from the second page appeared on the first
            for (RowDTO row : results.getItems()) {
                assertTrue(row instanceof DataArtifactRowDTO);
                DataArtifactRowDTO dataRow = (DataArtifactRowDTO) row;
                assertTrue(dataRow.getDataArtifact() != null);
                assertFalse("Data artifact ID: " + dataRow.getDataArtifact().getId()  + " appeared on both page 1 and page 2", 
                        firstPageObjIds.contains(dataRow.getDataArtifact().getId()));
            }
            
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
    
    public void commAccountsSearchTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);

        try {
            CommAccountsDAO commAccountsDAO = MainDAO.getInstance().getCommAccountsDAO();

            // Get emails from all data sources
            CommAccountsSearchParams param = new CommAccountsSearchParams(Account.Type.EMAIL, null);
            SearchResultsDTO results = commAccountsDAO.getCommAcounts(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // Get device accounts from data source 1
            param = new CommAccountsSearchParams(Account.Type.DEVICE, dataSource1.getId());
            results = commAccountsDAO.getCommAcounts(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get email accounts from data source 2
            param = new CommAccountsSearchParams(Account.Type.EMAIL, dataSource2.getId());
            results = commAccountsDAO.getCommAcounts(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Check that a few of the expected column names are present
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains(ACCOUNT_TYPE_COLUMN));
            assertTrue(columnDisplayNames.contains(ID_COLUMN));
            
            // Get the row
            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof DataArtifactRowDTO);
            DataArtifactRowDTO accountResultRowDTO = (DataArtifactRowDTO) rowDTO;

            // Check that some of the expected result column values are present
            assertTrue(accountResultRowDTO.getCellValues().contains(EMAIL_C));            
            assertTrue(accountResultRowDTO.getCellValues().contains(customFile.getName()));
            
            // Get phone accounts from all data sources
            param = new CommAccountsSearchParams(Account.Type.PHONE, null);
            results = commAccountsDAO.getCommAcounts(param, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());

            // Get phone accounts from data source 2
            param = new CommAccountsSearchParams(Account.Type.PHONE, dataSource2.getId());
            results = commAccountsDAO.getCommAcounts(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Get the row
            rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof DataArtifactRowDTO);
            accountResultRowDTO = (DataArtifactRowDTO) rowDTO;

            // Check that some of the expected result column values are present
            assertTrue(accountResultRowDTO.getCellValues().contains(PHONENUM_2));            
            assertTrue(accountResultRowDTO.getCellValues().contains(customFile.getName()));            
            
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }    

    public void mimeSearchTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);

        try {
            ViewsDAO viewsDAO = MainDAO.getInstance().getViewsDAO();

            // Get plain text files from data source 1
            FileTypeMimeSearchParams param = new FileTypeMimeSearchParams("text/plain", dataSource1.getId());
            SearchResultsDTO results = viewsDAO.getFilesByMime(param, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());

            // Get jpeg files from data source 1
            param = new FileTypeMimeSearchParams("image/jpeg", dataSource1.getId());
            results = viewsDAO.getFilesByMime(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get jpeg files from data source 2
            param = new FileTypeMimeSearchParams("image/jpeg", dataSource2.getId());
            results = viewsDAO.getFilesByMime(param, 0, null);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Search for mime type that should produce no results
            param = new FileTypeMimeSearchParams("blah/blah", null);
            results = viewsDAO.getFilesByMime(param, 0, null);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get plain text files from all data sources
            param = new FileTypeMimeSearchParams("text/plain", null);
            results = viewsDAO.getFilesByMime(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());

            // Get the custom file by MIME type
            param = new FileTypeMimeSearchParams(CUSTOM_MIME_TYPE, null);
            results = viewsDAO.getFilesByMime(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof FileRowDTO);
            FileRowDTO fileRowDTO = (FileRowDTO) rowDTO;

            assertEquals(CUSTOM_MIME_TYPE_FILE_NAME, fileRowDTO.getFileName());
            assertEquals(CUSTOM_EXTENSION, fileRowDTO.getExtension());
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
    
    public void sizeSearchTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);

        try {
            ViewsDAO viewsDAO = MainDAO.getInstance().getViewsDAO();

            // Get "50 - 200MB" files from data source 1
            FileTypeSizeSearchParams param = new FileTypeSizeSearchParams(FileSizeFilter.SIZE_50_200, dataSource1.getId());
            SearchResultsDTO results = viewsDAO.getFilesBySize(param, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());

            // Get "200MB - 1GB" files from data source 1
            param = new FileTypeSizeSearchParams(FileSizeFilter.SIZE_200_1000, dataSource1.getId());
            results = viewsDAO.getFilesBySize(param, 0, null);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get "200MB - 1GB" files from data source 2
            param = new FileTypeSizeSearchParams(FileSizeFilter.SIZE_200_1000, dataSource2.getId());
            results = viewsDAO.getFilesBySize(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get "1GB+" files from all data sources
            param = new FileTypeSizeSearchParams(FileSizeFilter.SIZE_1000_, null);
            results = viewsDAO.getFilesBySize(param, 0, null);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get "50 - 200MB" files from all data sources
            param = new FileTypeSizeSearchParams(FileSizeFilter.SIZE_50_200, null);
            results = viewsDAO.getFilesBySize(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }    
    
    public void tagsTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);

        try {
            TagsDAO tagsDAO = MainDAO.getInstance().getTagsDAO();

            // Get "Tag1" file tags from data source 1
            TagsSearchParams param = new TagsSearchParams(knownTag1, TagsSearchParams.TagType.FILE, dataSource1.getId());
            SearchResultsDTO results = tagsDAO.getTags(param, 0, null);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get "Tag2" file tags from data source 1
            param = new TagsSearchParams(tag2, TagsSearchParams.TagType.FILE, dataSource1.getId());
            results = tagsDAO.getTags(param, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());

            // Get "Tag2" file tags from all data sources
            param = new TagsSearchParams(tag2, TagsSearchParams.TagType.FILE, null);
            results = tagsDAO.getTags(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // Get "Tag1" file tags from data source 2
            param = new TagsSearchParams(knownTag1, TagsSearchParams.TagType.FILE, dataSource2.getId());
            results = tagsDAO.getTags(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Get the row
            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof BaseRowDTO);
            BaseRowDTO tagResultRowDTO = (BaseRowDTO) rowDTO;

            // Check that the file tag is for the custom file
            assertTrue(tagResultRowDTO.getCellValues().contains(customFile.getName()));            
            
            // Check that a few of the expected file tag column names are present
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains(MD5_COLUMN));
            assertTrue(columnDisplayNames.contains(FILE_PATH_COLUMN));
            assertTrue(columnDisplayNames.contains(MODIFIED_TIME_COLUMN));
            
            // Check that the result tag columns are not present
            assertFalse(columnDisplayNames.contains(SOURCE_NAME_COLUMN));
            assertFalse(columnDisplayNames.contains(SOURCE_FILE_PATH_COLUMN));
            
            // Get "Tag1" result tags from data source 2
            param = new TagsSearchParams(knownTag1, TagsSearchParams.TagType.RESULT, dataSource2.getId());
            results = tagsDAO.getTags(param, 0, null);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());
            
            // Get "Tag2" result tags from data source 1
            param = new TagsSearchParams(tag2, TagsSearchParams.TagType.RESULT, dataSource1.getId());
            results = tagsDAO.getTags(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get "Tag1" result tags from data source 1
            param = new TagsSearchParams(knownTag1, TagsSearchParams.TagType.RESULT, dataSource1.getId());
            results = tagsDAO.getTags(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Get the row
            rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof BaseRowDTO);
            tagResultRowDTO = (BaseRowDTO) rowDTO;

            // Check that some of the expected result tag column values are present
            assertTrue(tagResultRowDTO.getCellValues().contains(TAG_COMMENT));
            
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
    
    public void OsAccountsTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);

        try {
            OsAccountsDAO accountsDAO = MainDAO.getInstance().getOsAccountsDAO();

            // Get OS Accounts from data source 1
            OsAccountsSearchParams param = new OsAccountsSearchParams(dataSource1.getId());
            SearchResultsDTO results = accountsDAO.getAccounts(param, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());

            // Get OS Accounts from all data sources
            param = new OsAccountsSearchParams(null);
            results = accountsDAO.getAccounts(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // Get OS Accounts from data source 1
            param = new OsAccountsSearchParams(dataSource2.getId());
            results = accountsDAO.getAccounts(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Get the row
            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof BaseRowDTO);
            BaseRowDTO osAccountRowDTO = (BaseRowDTO) rowDTO;

            // Check that the result is for the custom OS Account
            Optional<String> addr = osAccount1.getAddr();
            assertTrue(osAccountRowDTO.getCellValues().contains(addr.get()));            
            
            // Check that a few of the expected OS Account column names are present
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains(REALM_NAME_COLUMN));
            assertTrue(columnDisplayNames.contains(HOST_COLUMN));

        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }     
    
    public void analysisResultSearchTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);
        
        try {
            // Get all encryption detected artifacts
            AnalysisResultSearchParam param = new AnalysisResultSearchParam(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, null);
            AnalysisResultDAO analysisResultDAO = MainDAO.getInstance().getAnalysisResultDAO();
            
            AnalysisResultTableSearchResultsDTO results = analysisResultDAO.getAnalysisResultsForTable(param, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, results.getArtifactType());
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // Get encryption detected artifacts from data source 2
            param = new AnalysisResultSearchParam(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, dataSource2.getId());
            results = analysisResultDAO.getAnalysisResultsForTable(param, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Get all custom artifacts
            param = new AnalysisResultSearchParam(customAnalysisResultType, null);
            results = analysisResultDAO.getAnalysisResultsForTable(param, 0, null);
            assertEquals(customAnalysisResultType, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Check that a few of the expected column names are present
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains(BlackboardAttribute.Type.TSK_COMMENT.getDisplayName()));
            assertTrue(columnDisplayNames.contains(BlackboardAttribute.Type.TSK_COUNT.getDisplayName()));
            assertTrue(columnDisplayNames.contains(customAttributeType.getDisplayName()));
            
            // Get the row
            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof AnalysisResultRowDTO);
            AnalysisResultRowDTO analysisResultRowDTO = (AnalysisResultRowDTO) rowDTO;
            
            // Check that the artifact, source content and linked file are correct
            assertEquals(customAnalysisResult, analysisResultRowDTO.getAnalysisResult());
            assertEquals(customAnalysisResultSource, analysisResultRowDTO.getSrcContent());
            
            // Check that some of the expected column values are present
            assertTrue(analysisResultRowDTO.getCellValues().contains(ARTIFACT_CUSTOM_ATTR_STRING));
            assertTrue(analysisResultRowDTO.getCellValues().contains(ARTIFACT_COMMENT));
            assertTrue(analysisResultRowDTO.getCellValues().contains(ARTIFACT_INT));
            assertTrue(analysisResultRowDTO.getCellValues().contains(ARTIFACT_DOUBLE));
            assertTrue(analysisResultRowDTO.getCellValues().contains(ARTIFACT_JUSTIFICATION));
            assertTrue(analysisResultRowDTO.getCellValues().contains(ARTIFACT_CONFIGURATION));
            assertTrue(analysisResultRowDTO.getCellValues().contains(ARTIFACT_CONCLUSION));
            
             // Test paging
            Long pageSize = new Long(100);
            assertTrue(ARTIFACT_COUNT_YARA > pageSize);
            
            // Get the first page
            param = new AnalysisResultSearchParam(BlackboardArtifact.Type.TSK_YARA_HIT, null);
            results = analysisResultDAO.getAnalysisResultsForTable(param, 0, pageSize);
            assertEquals(ARTIFACT_COUNT_YARA, results.getTotalResultsCount());
            assertEquals(pageSize.longValue(), results.getItems().size());
            
            // Save all artifact IDs from the first page
            Set<Long> firstPageObjIds = new HashSet<>();
            for (RowDTO row : results.getItems()) {
                assertTrue(row instanceof AnalysisResultRowDTO);
                AnalysisResultRowDTO analysisRow = (AnalysisResultRowDTO) row;
                assertTrue(analysisRow.getAnalysisResult() != null);
                firstPageObjIds.add(analysisRow.getAnalysisResult().getId());
            }
            assertEquals(pageSize.longValue(), firstPageObjIds.size());
         
            // Get the second page
            param = new AnalysisResultSearchParam(BlackboardArtifact.Type.TSK_YARA_HIT, null);
            results = analysisResultDAO.getAnalysisResultsForTable(param, pageSize, pageSize);
            assertEquals(ARTIFACT_COUNT_YARA, results.getTotalResultsCount());
            assertEquals(ARTIFACT_COUNT_YARA - pageSize, results.getItems().size());
            
            // Make sure no artifacts from the second page appeared on the first
            for (RowDTO row : results.getItems()) {
                assertTrue(row instanceof AnalysisResultRowDTO);
                AnalysisResultRowDTO analysisRow = (AnalysisResultRowDTO) row;
                assertTrue(analysisRow.getAnalysisResult() != null);
                assertFalse("Analysis result ID: " + analysisRow.getAnalysisResult().getId()  + " appeared on both page 1 and page 2", 
                        firstPageObjIds.contains(analysisRow.getAnalysisResult().getId()));
            }
            
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
            
    private void hashHitSearchTest() {
        try {
            // Test hash set hits
            AnalysisResultDAO analysisResultDAO = MainDAO.getInstance().getAnalysisResultDAO();
            HashHitSearchParam hashParam = new HashHitSearchParam(null, HASH_SET_1);
            AnalysisResultTableSearchResultsDTO results = analysisResultDAO.getAnalysisResultConfigResults(hashParam, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_HASHSET_HIT, results.getArtifactType());
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            hashParam = new HashHitSearchParam(dataSource2.getId(), HASH_SET_1);
            results = analysisResultDAO.getAnalysisResultConfigResults(hashParam, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_HASHSET_HIT, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Check that a few of the expected column names are present
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains("Justification"));
            assertTrue(columnDisplayNames.contains(BlackboardAttribute.Type.TSK_HASH_MD5.getDisplayName()));
            
            // Get the row
            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof AnalysisResultRowDTO);
            AnalysisResultRowDTO analysisResultRowDTO = (AnalysisResultRowDTO) rowDTO;
            
            // Check that the artifact, source content and linked file are correct
            assertEquals(hashHitAnalysisResult, analysisResultRowDTO.getAnalysisResult());
            assertEquals(fileWithHashHit, analysisResultRowDTO.getSrcContent());
            
            // Check that the hash is present
            assertTrue(analysisResultRowDTO.getCellValues().contains(HASH_HIT_VALUE));
            
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }        
    }
    
    private void keywordHitSearchTest() {
        try {
            // Test keyword set hits
            AnalysisResultDAO analysisResultDAO = MainDAO.getInstance().getAnalysisResultDAO();
            KeywordHitSearchParam kwParam = new KeywordHitSearchParam(null, KEYWORD_SET_1, "keyword1", "", TskData.KeywordSearchQueryType.LITERAL);
            AnalysisResultTableSearchResultsDTO results = analysisResultDAO.getKeywordHitsForTable(kwParam, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_KEYWORD_HIT, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            kwParam = new KeywordHitSearchParam(dataSource1.getId(), KEYWORD_SET_2, "keyword2", "", TskData.KeywordSearchQueryType.LITERAL);
            results = analysisResultDAO.getKeywordHitsForTable(kwParam, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_KEYWORD_HIT, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            kwParam = new KeywordHitSearchParam(dataSource2.getId(), KEYWORD_SET_2, KEYWORD, KEYWORD_REGEX, TskData.KeywordSearchQueryType.REGEX);
            results = analysisResultDAO.getKeywordHitsForTable(kwParam, 0, null);
            assertEquals(BlackboardArtifact.Type.TSK_KEYWORD_HIT, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Check that a few of the expected column names are present
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains("Justification"));
            assertTrue(columnDisplayNames.contains(BlackboardAttribute.Type.TSK_KEYWORD.getDisplayName()));
            
            // Get the row
            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof AnalysisResultRowDTO);
            AnalysisResultRowDTO analysisResultRowDTO = (AnalysisResultRowDTO) rowDTO;
            
            // Check that the keyword and preview are present
            assertTrue(analysisResultRowDTO.getCellValues().contains(KEYWORD));
            assertTrue(analysisResultRowDTO.getCellValues().contains(KEYWORD_PREVIEW));
            assertTrue(analysisResultRowDTO.getCellValues().contains(KEYWORD_REGEX));
            
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }        
    }

    public void extensionSearchTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);

        try {
            ViewsDAO viewsDAO = MainDAO.getInstance().getViewsDAO();

            // Get all text documents from data source 1
            FileTypeExtensionsSearchParams param = new FileTypeExtensionsSearchParams(FileExtRootFilter.TSK_DOCUMENT_FILTER, dataSource1.getId());
            SearchResultsDTO results = viewsDAO.getFilesByExtension(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());

            // Get Word documents from data source 1
            param = new FileTypeExtensionsSearchParams(FileExtDocumentFilter.AUT_DOC_OFFICE, dataSource1.getId());
            results = viewsDAO.getFilesByExtension(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get image/jpeg files from data source 1
            param = new FileTypeExtensionsSearchParams(FileExtRootFilter.TSK_IMAGE_FILTER, dataSource1.getId());
            results = viewsDAO.getFilesByExtension(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get text documents from all data sources
            param = new FileTypeExtensionsSearchParams(FileExtRootFilter.TSK_DOCUMENT_FILTER, null);
            results = viewsDAO.getFilesByExtension(param, 0, null);
            assertEquals(4, results.getTotalResultsCount());
            assertEquals(4, results.getItems().size());

            // Get jpeg files from data source 2
            param = new FileTypeExtensionsSearchParams(FileExtRootFilter.TSK_IMAGE_FILTER, dataSource2.getId());
            results = viewsDAO.getFilesByExtension(param, 0, null);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Search for file extensions that should produce no results
            param = new FileTypeExtensionsSearchParams(CustomRootFilter.EMPTY_RESULT_SET_FILTER, null);
            results = viewsDAO.getFilesByExtension(param, 0, null);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get the custom file by extension
            param = new FileTypeExtensionsSearchParams(CustomRootFilter.CUSTOM_FILTER, null);
            results = viewsDAO.getFilesByExtension(param, 0, null);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof FileRowDTO);
            FileRowDTO fileRowDTO = (FileRowDTO) rowDTO;

            assertEquals(CUSTOM_MIME_TYPE_FILE_NAME, fileRowDTO.getFileName());
            assertEquals(CUSTOM_EXTENSION, fileRowDTO.getExtension());
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
    
    private void fileSystemTest() {
        // Quick test that everything is initialized
        assertTrue(db != null);

        try {
            FileSystemDAO fileSystemDAO = MainDAO.getInstance().getFileSystemDAO();
            
            // There are 4 hosts not associated with a person
            FileSystemPersonSearchParam personParam = new FileSystemPersonSearchParam(null);
            BaseSearchResultsDTO results = fileSystemDAO.getHostsForTable(personParam, 0, null);
            assertEquals(4, results.getTotalResultsCount());
            assertEquals(4, results.getItems().size());
            
            // Person1 is associated with two hosts
            personParam = new FileSystemPersonSearchParam(person1.getPersonId());
            results = fileSystemDAO.getHostsForTable(personParam, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());
            
            // Check that the name of the first host is present
            RowDTO row = results.getItems().get(0);
            assertTrue(row.getCellValues().contains(PERSON_HOST_NAME1));
            
            // HostA is associated with two images
            FileSystemHostSearchParam hostParam = new FileSystemHostSearchParam(fsTestHostA.getHostId());
            results = fileSystemDAO.getContentForTable(hostParam, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());
            
            // ImageA has one volume system child, which has three volumes that will be displayed
            FileSystemContentSearchParam param = new FileSystemContentSearchParam(fsTestImageA.getId());
            results = fileSystemDAO.getContentForTable(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // VsA has three volume children (this should match the previous search)
            param = new FileSystemContentSearchParam(fsTestVsA.getId());
            results = fileSystemDAO.getContentForTable(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // VolumeA1 has a file system child, which in turn has a root directory child with three file children
            param = new FileSystemContentSearchParam(fsTestVolumeA1.getId());
            results = fileSystemDAO.getContentForTable(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // FsA has a root directory child with three file children (this should match the previous search)
            param = new FileSystemContentSearchParam(fsTestFsA.getId());
            results = fileSystemDAO.getContentForTable(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // The root dir contains three files
            param = new FileSystemContentSearchParam(fsTestRootDirA.getId());
            results = fileSystemDAO.getContentForTable(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // ImageB has VS (which will display one volume), pool, and one local file children
            param = new FileSystemContentSearchParam(fsTestImageB.getId());
            results = fileSystemDAO.getContentForTable(param, 0, null);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // Check that we have the "Type" column from the Pool and the "Known" column from the file
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains("Type"));
            assertTrue(columnDisplayNames.contains("Known"));
            
            // fsTestVolumeB1 has pool and one local file children
            param = new FileSystemContentSearchParam(fsTestVolumeB1.getId());
            results = fileSystemDAO.getContentForTable(param, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());
            
            // fsTestPoolB has VS (which will display one volume) and local file children
            param = new FileSystemContentSearchParam(fsTestPoolB.getId());
            results = fileSystemDAO.getContentForTable(param, 0, null);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());
            
         } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }           
    }

    private enum CustomRootFilter implements FileExtSearchFilter {

        CUSTOM_FILTER(0, "CUSTOM_FILTER", "Test", CUSTOM_EXTENSIONS), //NON-NLS
        EMPTY_RESULT_SET_FILTER(1, "EMPTY_RESULT_SET_FILTER", "Test", EMPTY_RESULT_SET_EXTENSIONS); //NON-NLS
        final int id;
        final String name;
        final String displayName;
        final Set<String> filter;

        private CustomRootFilter(int id, String name, String displayName, Set<String> filter) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getId() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public Set<String> getFilter() {
            return this.filter;
        }
    }

    @Override
    public void tearDown() {
        try {
            CaseUtils.closeCurrentCase();
        } catch (TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
        openCase = null;
        db = null;
        blackboard = null;
        tagsManager = null;
        accountMgr = null;
    }
}
