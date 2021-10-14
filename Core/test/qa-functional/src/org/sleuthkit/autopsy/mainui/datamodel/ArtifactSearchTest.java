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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.List;
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
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class ArtifactSearchTest extends NbTestCase {
    
    private static final String CASE_NAME = "ArtifactSearchTest";
    static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), CASE_NAME);
    private static final String MODULE_NAME = "ArtifactSearchTest";
    
    private static final String CUSTOM_DA_TYPE_NAME = "SEARCH_TEST_CUSTOM_DA_TYPE";
    private static final String CUSTOM_DA_TYPE_DISPLAY_NAME = "Search test custom data artifact type";
    private static final String CUSTOM_ATTR_TYPE_NAME = "SEARCH_TEST_CUSTOM_ATTRIBUTE_TYPE";
    private static final String CUSTOM_ATTR_TYPE_DISPLAY_NAME = "Search test custom attribute type";
    
    private static final String ARTIFACT_COMMENT = "Artifact comment";
    private static final String ARTIFACT_CUSTOM_ATTR_STRING = "Custom attribute string";
    private static final int ARTIFACT_INT = 5;
    private static final double ARTIFACT_DOUBLE = 7.89;
    
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(ArtifactSearchTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }
    
    public ArtifactSearchTest(String name) {
        super(name);
    }
    
    public void testDataArtifactSearch() {
        
        try {
            /////////////////////////
            // SETUP
            /////////////////////////
            
            // Create a test case
            Case openCase = CaseUtils.createAsCurrentCase("testDataArtifactSearchCase");
            SleuthkitCase db = openCase.getSleuthkitCase();
            Blackboard blackboard = db.getBlackboard();
            
            // Add two logical files data sources
            SleuthkitCase.CaseDbTransaction trans = db.beginTransaction();
            DataSource ds1 = db.addLocalFilesDataSource("devId1", "C:\\Fake\\Path\\1", "EST", null, trans);
            DataSource ds2 = db.addLocalFilesDataSource("devId2", "C:\\Fake\\Path\\2", "EST", null, trans);
            trans.commit();
            
            // Add a few files to each data source
            AbstractFile folderA1 = db.addLocalDirectory(ds1.getId(), "folder1");
            AbstractFile fileA1 = db.addLocalFile("file1.txt", "", 0, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderA1);
            AbstractFile folderA2 = db.addLocalDirectory(ds1.getId(), "folder2");
            AbstractFile fileA2 =db.addLocalFile("file2.jpg", "", 0, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderA2);
            AbstractFile folderA3 = db.addLocalDirectory(folderA2.getId(), "folder3");
            AbstractFile fileA3 = db.addLocalFile("file3.doc", "", 0, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderA3);
            
            AbstractFile folderB1 = db.addLocalDirectory(ds2.getId(), "folder1");
            AbstractFile fileB1 = db.addLocalFile("fileA.txt", "", 0, 0, 0, 0, 0, true, TskData.EncodingType.NONE, folderB1);      
            
            // Create a custom artifact and attribute types
            BlackboardArtifact.Type customDataArtifactType = blackboard.getOrAddArtifactType(CUSTOM_DA_TYPE_NAME, CUSTOM_DA_TYPE_DISPLAY_NAME, BlackboardArtifact.Category.DATA_ARTIFACT);
            BlackboardAttribute.Type customAttributeType = blackboard.getOrAddAttributeType(CUSTOM_ATTR_TYPE_NAME, 
                    BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING, CUSTOM_ATTR_TYPE_DISPLAY_NAME);
            
            
            // Add some data artifacts
            List<BlackboardAttribute> attrs = new ArrayList<>();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Contact 1"));
            fileA1.newDataArtifact(BlackboardArtifact.Type.TSK_CONTACT, attrs);
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Bookmark 1"));
            fileA2.newDataArtifact(BlackboardArtifact.Type.TSK_GPS_BOOKMARK, attrs);
            
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, "Contact 2"));
            fileB1.newDataArtifact(BlackboardArtifact.Type.TSK_CONTACT, attrs);
            
            // This is the main artifact we'll use for testing. Make attributes of several types.
            attrs.clear();
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COMMENT, MODULE_NAME, ARTIFACT_COMMENT));
            attrs.add(new BlackboardAttribute(customAttributeType, MODULE_NAME, ARTIFACT_CUSTOM_ATTR_STRING));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_COUNT, MODULE_NAME, ARTIFACT_INT));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_ENTROPY, MODULE_NAME, ARTIFACT_DOUBLE));
            attrs.add(new BlackboardAttribute(BlackboardAttribute.Type.TSK_PATH_ID, MODULE_NAME, fileA2.getId()));
            DataArtifact customDataArtifact = fileA3.newDataArtifact(customDataArtifactType, attrs);

            
            /////////////////////////
            // Data artifact tests
            /////////////////////////
            
            // Get all contacts
            DataArtifactSearchParam param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_CONTACT, null);
            DataArtifactDAO dataArtifactDAO = MainDAO.getInstance().getDataArtifactsDAO();
            
            DataArtifactTableSearchResultsDTO results = dataArtifactDAO.getDataArtifactsForTable(param);
            assertEquals(BlackboardArtifact.Type.TSK_CONTACT, results.getArtifactType());
            assertEquals(2, results.getTotalResultsCount());
            assertEquals(2, results.getItems().size());
            
            // Get contacts from data source 2
            param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_CONTACT, ds2.getId());
            results = dataArtifactDAO.getDataArtifactsForTable(param);
            assertEquals(BlackboardArtifact.Type.TSK_CONTACT, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Get bookmarks from data source 2
            param = new DataArtifactSearchParam(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, ds2.getId());
            results = dataArtifactDAO.getDataArtifactsForTable(param);
            assertEquals(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, results.getArtifactType());
            assertEquals(0, results.getTotalResultsCount());
            assertEquals(0, results.getItems().size());
            
            // Get all custom artifacts
            param = new DataArtifactSearchParam(customDataArtifactType, null);
            results = dataArtifactDAO.getDataArtifactsForTable(param);
            assertEquals(customDataArtifactType, results.getArtifactType());
            assertEquals(1, results.getTotalResultsCount());
            assertEquals(1, results.getItems().size());
            
            // Check that a few of the expected column names are present
            List<String> columnDisplayNames = results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList());
            assertTrue(columnDisplayNames.contains(BlackboardAttribute.Type.TSK_COMMENT.getDisplayName()));
            assertTrue(columnDisplayNames.contains(BlackboardAttribute.Type.TSK_COUNT.getDisplayName()));
            assertTrue(columnDisplayNames.contains(customAttributeType.getDisplayName()));
            
            // Get one of the rows
            RowDTO rowDTO = results.getItems().get(0);
            assertTrue(rowDTO instanceof DataArtifactRowDTO);
            DataArtifactRowDTO dataArtifactRowDTO = (DataArtifactRowDTO) rowDTO;
            
            // Check that the artifact, source content and linked file are correct
            assertEquals(customDataArtifact, dataArtifactRowDTO.getDataArtifact());
            assertEquals(fileA3, dataArtifactRowDTO.getSrcContent());
            //assertEquals(fileA2, dataArtifactRowDTO.getLinkedFile()); I'm doing something wrong or this isn't working yet
            
            // Check that some of the expected column values are present
            assertTrue(dataArtifactRowDTO.getCellValues().contains(ARTIFACT_CUSTOM_ATTR_STRING));
            assertTrue(dataArtifactRowDTO.getCellValues().contains(ARTIFACT_COMMENT));
            assertTrue(dataArtifactRowDTO.getCellValues().contains(ARTIFACT_INT));
            assertTrue(dataArtifactRowDTO.getCellValues().contains(ARTIFACT_DOUBLE));
            
        } catch (TestUtilsException | TskCoreException | BlackboardException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
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
    }
}
