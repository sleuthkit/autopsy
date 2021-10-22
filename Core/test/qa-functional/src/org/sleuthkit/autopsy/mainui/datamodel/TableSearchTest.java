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
import java.util.Set;
import java.util.stream.Collectors;
import junit.framework.Assert;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.TestUtilsException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskCoreException;

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
    
    // Values for the hash set hit tests
    private static final String HASH_SET_1 = "Hash Set 1";
    private static final String HASH_SET_2 = "Hash Set 2";
    private static final String HASH_HIT_VALUE = "aefe58b6dc38bbd7f2b7861e7e8f7539";
    
    // Values for the keyword hit tests
    private static final String KEYWORD_SET_1 = "Keyword Set 1";
    private static final String KEYWORD_SET_2 = "Keyword Set 2";
    private static final String KEYWORD = "bomb";  
    private static final String KEYWORD_PREVIEW = "There is a bomb.";
    
    // Extension and MIME type test
    private static final String CUSTOM_MIME_TYPE = "fake/type";
    private static final String CUSTOM_MIME_TYPE_FILE_NAME = "test.fake";
    private static final String CUSTOM_EXTENSION = "fake";
    private static final Set<String> CUSTOM_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("." + CUSTOM_EXTENSION))); //NON-NLS
    private static final Set<String> EMPTY_RESULT_SET_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(".blah", ".blah2", ".crazy"))); //NON-NLS
    
    /////////////////////////////////////////////////
    // Data to be used across the test methods.
    // These are initialized in setUpCaseDatabase().
    /////////////////////////////////////////////////
    Case openCase = null;          // The case for testing
    SleuthkitCase db = null;       // The case database
    Blackboard blackboard = null;  // The blackboard

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
    }

    /**
     * Create a case and add sample data.
     */
    private void setUpCaseDatabase() {
        try {
            // Create a test case
            openCase = CaseUtils.createAsCurrentCase("testTableSearchCase");
            db = openCase.getSleuthkitCase();
            blackboard = db.getBlackboard();

            // Add two logical files data sources
            SleuthkitCase.CaseDbTransaction trans = db.beginTransaction();
            dataSource1 = db.addLocalFilesDataSource("devId1", "C:\\Fake\\Path\\1", "EST", null, trans);
            dataSource2 = db.addLocalFilesDataSource("devId2", "C:\\Fake\\Path\\2", "EST", null, trans);
            dataSource3 = db.addLocalFilesDataSource("devId3", "C:\\Fake\\Path\\3", "EST", null, trans);
            trans.commit();

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

            AbstractFile customFile = db.addLocalFile(CUSTOM_MIME_TYPE_FILE_NAME, "", 67000000, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderB1);
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
            fileA1.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_KEYWORD_HIT, Score.SCORE_NOTABLE, 
                    null, KEYWORD_SET_1, null, attrs);  
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_SET_NAME, MODULE_NAME, KEYWORD_SET_2));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_KEYWORD, MODULE_NAME, "keyword2"));
            fileA3.newAnalysisResult(
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
            
        } catch (TestUtilsException | TskCoreException | BlackboardException ex) {
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

            DataArtifactTableSearchResultsDTO results = dataArtifactDAO.getDataArtifactsForTable(param, false);
            assertEquals(BlackboardArtifact.Type.TSK_CONTACT, results.getArtifactType());
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());
            
            // Get contacts from data source 2
            param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_CONTACT, dataSource2.getId());
            results = dataArtifactDAO.getDataArtifactsForTable(param, false);
            assertEquals(BlackboardArtifact.Type.TSK_CONTACT, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get bookmarks from data source 2
            param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, dataSource2.getId());
            results = dataArtifactDAO.getDataArtifactsForTable(param, false);
            assertEquals(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, results.getArtifactType());
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get all custom artifacts
            param = new DataArtifactSearchParam(customDataArtifactType, null);
            results = dataArtifactDAO.getDataArtifactsForTable(param, false);
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
            SearchResultsDTO results = viewsDAO.getFilesByMime(param, false);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());

            // Get jpeg files from data source 1
            param = new FileTypeMimeSearchParams("image/jpeg", dataSource1.getId());
            results = viewsDAO.getFilesByMime(param, false);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get jpeg files from data source 2
            param = new FileTypeMimeSearchParams("image/jpeg", dataSource2.getId());
            results = viewsDAO.getFilesByMime(param, false);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Search for mime type that should produce no results
            param = new FileTypeMimeSearchParams("blah/blah", null);
            results = viewsDAO.getFilesByMime(param, false);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get plain text files from all data sources
            param = new FileTypeMimeSearchParams("text/plain", null);
            results = viewsDAO.getFilesByMime(param, false);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());

            // Get the custom file by MIME type
            param = new FileTypeMimeSearchParams(CUSTOM_MIME_TYPE, null);
            results = viewsDAO.getFilesByMime(param, false);
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
            FileTypeSizeSearchParams param = new FileTypeSizeSearchParams(FileTypeSizeSearchParams.FileSizeFilter.SIZE_50_200, dataSource1.getId());
            SearchResultsDTO results = viewsDAO.getFilesBySize(param, false);
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());

            // Get "200MB - 1GB" files from data source 1
            param = new FileTypeSizeSearchParams(FileTypeSizeSearchParams.FileSizeFilter.SIZE_200_1000, dataSource1.getId());
            results = viewsDAO.getFilesBySize(param, false);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get "200MB - 1GB" files from data source 2
            param = new FileTypeSizeSearchParams(FileTypeSizeSearchParams.FileSizeFilter.SIZE_200_1000, dataSource2.getId());
            results = viewsDAO.getFilesBySize(param, false);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get "1GB+" files from all data sources
            param = new FileTypeSizeSearchParams(FileTypeSizeSearchParams.FileSizeFilter.SIZE_1000_, null);
            results = viewsDAO.getFilesBySize(param, false);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get "50 - 200MB" files from all data sources
            param = new FileTypeSizeSearchParams(FileTypeSizeSearchParams.FileSizeFilter.SIZE_50_200, null);
            results = viewsDAO.getFilesBySize(param, false);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
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
            
            AnalysisResultTableSearchResultsDTO results = analysisResultDAO.getAnalysisResultsForTable(param, false);
            assertEquals(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, results.getArtifactType());
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            // Get encryption detected artifacts from data source 2
            param = new AnalysisResultSearchParam(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, dataSource2.getId());
            results = analysisResultDAO.getAnalysisResultsForTable(param, false);
            assertEquals(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Get all custom artifacts
            param = new AnalysisResultSearchParam(customAnalysisResultType, null);
            results = analysisResultDAO.getAnalysisResultsForTable(param, false);
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
            AnalysisResultTableSearchResultsDTO results = analysisResultDAO.getHashHitsForTable(hashParam, false);
            assertEquals(BlackboardArtifact.Type.TSK_HASHSET_HIT, results.getArtifactType());
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());
            
            hashParam = new HashHitSearchParam(dataSource2.getId(), HASH_SET_1);
            results = analysisResultDAO.getHashHitsForTable(hashParam, false);
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
            KeywordHitSearchParam kwParam = new KeywordHitSearchParam(null, KEYWORD_SET_1, "", "");
            AnalysisResultTableSearchResultsDTO results = analysisResultDAO.getKeywordHitsForTable(kwParam, false);
            assertEquals(BlackboardArtifact.Type.TSK_KEYWORD_HIT, results.getArtifactType());
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());
            
            kwParam = new KeywordHitSearchParam(dataSource2.getId(), KEYWORD_SET_1, "", "");
            results = analysisResultDAO.getKeywordHitsForTable(kwParam, false);
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
            
            // Check that the artifact, source content and linked file are correct
            assertEquals(keywordHitAnalysisResult, analysisResultRowDTO.getAnalysisResult());
            assertEquals(keywordHitSource, analysisResultRowDTO.getSrcContent());
            
            // Check that the keyword and preview are present
            assertTrue(analysisResultRowDTO.getCellValues().contains(KEYWORD));
            assertTrue(analysisResultRowDTO.getCellValues().contains(KEYWORD_PREVIEW));
            
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
            SearchResultsDTO results = viewsDAO.getFilesByExtension(param, false);
            assertEquals(3, results.getTotalResultsCount());
            assertEquals(3, results.getItems().size());

            // Get Word documents from data source 1
            param = new FileTypeExtensionsSearchParams(FileExtDocumentFilter.AUT_DOC_OFFICE, dataSource1.getId());
            results = viewsDAO.getFilesByExtension(param, false);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get image/jpeg files from data source 1
            param = new FileTypeExtensionsSearchParams(FileExtRootFilter.TSK_IMAGE_FILTER, dataSource1.getId());
            results = viewsDAO.getFilesByExtension(param, false);
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());

            // Get text documents from all data sources
            param = new FileTypeExtensionsSearchParams(FileExtRootFilter.TSK_DOCUMENT_FILTER, null);
            results = viewsDAO.getFilesByExtension(param, false);
            assertEquals(4, results.getTotalResultsCount());
            assertEquals(4, results.getItems().size());

            // Get jpeg files from data source 2
            param = new FileTypeExtensionsSearchParams(FileExtRootFilter.TSK_IMAGE_FILTER, dataSource2.getId());
            results = viewsDAO.getFilesByExtension(param, false);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Search for file extensions that should produce no results
            param = new FileTypeExtensionsSearchParams(CustomRootFilter.EMPTY_RESULT_SET_FILTER, null);
            results = viewsDAO.getFilesByExtension(param, false);
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());

            // Get the custom file by extension
            param = new FileTypeExtensionsSearchParams(CustomRootFilter.CUSTOM_FILTER, null);
            results = viewsDAO.getFilesByExtension(param, false);
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
    }
}
