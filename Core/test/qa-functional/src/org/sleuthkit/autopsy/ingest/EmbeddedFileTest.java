/*
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
package org.sleuthkit.autopsy.ingest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.EmbeddedFileExtractorModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.autopsy.testutils.TestUtilsException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Functional tests for embedded files.
 */
public class EmbeddedFileTest extends NbTestCase {

    private static final String CASE_NAME = "EmbeddedFileTest";
    private final Path IMAGE_PATH = Paths.get(this.getDataDir().toString(), "EmbeddedIM_img1_v2.vhd");
    public static final String HASH_VALUE = "098f6bcd4621d373cade4e832627b4f6";
    private static final int DEEP_FOLDER_COUNT = 25;
    private Case openCase;

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(EmbeddedFileTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public EmbeddedFileTest(String name) {
        super(name);
    }

    @Override
    public void setUp() {
        try {
            openCase = CaseUtils.createAsCurrentCase(CASE_NAME);
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, IMAGE_PATH);

            IngestModuleTemplate embeddedTemplate = IngestUtils.getIngestModuleTemplate(new EmbeddedFileExtractorModuleFactory());
            IngestModuleTemplate hashLookupTemplate = IngestUtils.getIngestModuleTemplate(new HashLookupModuleFactory());

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(embeddedTemplate);
            templates.add(hashLookupTemplate);
            IngestJobSettings ingestJobSettings = new IngestJobSettings(EmbeddedFileTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates);

            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);
        } catch (TskCoreException | TestUtilsException ex) {
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

    public void testAll() {
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "----------------------   Embedded File Tests ---------------------");
        runTestEncryptionAndZipBomb();
        runTestExtension();
        runTestBigFolder();
        runTestDeepFolder();
        runTestEmbeddedFile();
        runTestContent();
    }

    private void runTestEncryptionAndZipBomb() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Starting");
            List<AbstractFile> results = openCase.getSleuthkitCase().findAllFilesWhere("name LIKE '%%'");
            final String zipBombSetName = "Possible Zip Bomb";
            final String protectedName1 = "password_protected.zip";
            final String protectedName2 = "level1_protected.zip";
            final String protectedName3 = "42.zip";
            final String depthZipBomb = "DepthTriggerZipBomb.zip";
            final String ratioZipBomb = "RatioTriggerZipBomb.zip";
            int zipBombs = 0;
            assertEquals("The number of files in the test image has changed", 2221, results.size());
            int passwdProtectedZips = 0;
            for (AbstractFile file : results) {
                //.zip file has artifact TSK_ENCRYPTION_DETECTED
                if (file.getName().equalsIgnoreCase(protectedName1) || file.getName().equalsIgnoreCase(protectedName2) || file.getName().equalsIgnoreCase(protectedName3)) {
                    ArrayList<BlackboardArtifact> artifacts = file.getAllArtifacts();
                    assertEquals("Password protected zip file " + file.getName() + " has incorrect number of artifacts", 1, artifacts.size());
                    for (BlackboardArtifact artifact : artifacts) {
                        assertEquals("Artifact for password protected zip file " + file.getName() + " has incorrect type ID", artifact.getArtifactTypeID(), BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID());
                        passwdProtectedZips++;
                    }
                } else if (file.getName().equalsIgnoreCase(depthZipBomb) || file.getName().equalsIgnoreCase(ratioZipBomb)) {
                    ArrayList<BlackboardArtifact> artifacts = file.getAllArtifacts();
                    assertEquals("Zip bomb " + file.getName() + " has incorrect number of artifacts", 1, artifacts.size());
                    for (BlackboardArtifact artifact : artifacts) {
                        assertEquals("Artifact for Zip bomb " + file.getName() + " has incorrect type ID", artifact.getArtifactTypeID(), BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID());
                        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                        assertNotNull("No attribute found for artifact on zip bomb " + file.getName(), attribute);
                        assertEquals("Interesting artifact on file, " + file.getName() + ", does not reflect it being a zip bomb", zipBombSetName, attribute.getDisplayString());
                        zipBombs++;
                    }
                } else {//No other files have artifact defined
                    assertEquals("Unexpected file, " + file.getName() + ", has artifacts", 0, file.getAllArtifacts().size());
                }

            }
            //Make sure 3 password protected zip files have been tested: password_protected.zip, level1_protected.zip and 42.zip that we download for bomb testing.
            assertEquals("Unexpected number of artifacts reflecting password protected zip files found", 3, passwdProtectedZips);
            //Make sure 2 zip bomb files have been tested: DepthTriggerZipBomb.zip and RatioTriggerZipBomb.zip.
            assertEquals("Unexpected number of artifacts reflecting zip bombs found", 2, zipBombs);
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    private void runTestBigFolder() {
        final int numOfFilesToTest = 1000;
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Starting");
            //Get all files under 'big folder' directory except '.' '..' 'slack' files
            List<AbstractFile> results = openCase.getSleuthkitCase().findAllFilesWhere("parent_path LIKE '%big folder/' and name != '.' and name != '..' and extension NOT LIKE '%slack'");
            assertEquals(numOfFilesToTest, results.size()); //There are 1000 files 
            int numOfFilesTested = 0;
            for (AbstractFile file : results) {
                String fileName = file.getName();
                //File name should like file1.txt, file2.txt ... file1000.txt
                String errMsg = String.format("File name %s doesn't follow the expected naming convention: fileNaturalNumber.txt, eg. file234.txt.", fileName);
                assertTrue(errMsg, file.getName().matches("file[1-9]\\d*.txt"));
                String hashValue = file.getMd5Hash();
                //All files have the same hash value
                assertEquals(HASH_VALUE, hashValue);
                numOfFilesTested++;
            }
            //Make sure 1000 files have been tested
            assertEquals(numOfFilesToTest, numOfFilesTested);
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    private void runTestDeepFolder() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Starting");
            //Get all files under 'deep folder' directory except '.' '..'
            List<AbstractFile> results = openCase.getSleuthkitCase().findAllFilesWhere("parent_path LIKE '%deep folder/' and name != '.' and name != '..'");
            assertEquals(1, results.size());
            StringBuffer dirReached = new StringBuffer();
            ArrayList<String> fileReached = new ArrayList<>();
            checkEachFileInDeepFolder(results.get(0), dirReached, fileReached, 0);
            //Check that all 25 folders/files have been reached
            assertEquals(DEEP_FOLDER_COUNT, fileReached.size());
            //Make sure the test reached the last directory 'dir25'. The whole directory is dir1/dir2...dir24/dir25/
            assertTrue(dirReached.toString().startsWith("dir1/dir2/"));
            assertTrue(dirReached.toString().endsWith("dir24/dir25/"));
            //Make sure the test reached the last file.txt in dir1/dir2...dir24/dir25/
            assertTrue(fileReached.get(0).endsWith(dirReached.toString() + "file.txt"));
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    private void runTestEmbeddedFile() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Starting");
            //Query level3.txt under '/ZIP/embedded/level3.zip/'
            List<AbstractFile> results = openCase.getSleuthkitCase().findAllFilesWhere("name = 'level3.txt' and parent_path = '/ZIP/embedded/level3.zip/'");
            assertEquals(1, results.size());

            //Query level2.txt under '/ZIP/embedded/level3.zip/level2.zip/'
            results = openCase.getSleuthkitCase().findAllFilesWhere("name = 'level2.txt' and parent_path = '/ZIP/embedded/level3.zip/level2.zip/'");
            assertEquals(1, results.size());

            //Query level1.txt under '/ZIP/embedded/level3.zip/level2.zip/level1.zip/'
            results = openCase.getSleuthkitCase().findAllFilesWhere("name = 'level1.txt' and parent_path = '/ZIP/embedded/level3.zip/level2.zip/level1.zip/'");
            assertEquals(1, results.size());

            //Confirm that we can reach level1.txt from the embedded folder
            results = openCase.getSleuthkitCase().findAllFilesWhere("parent_path LIKE '%embedded/' and name != '.' and name != '..' and extension NOT LIKE '%slack%'");
            assertEquals(1, results.size());
            assertTrue(checkFileInEmbeddedFolder(results.get(0)));
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    private void runTestContent() {
        final int numOfFilesToTest = 1029;
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Starting");
            //All files with txt extension should have the same hash value, 
            //except the zip file with txt extension and the .txt files extracted from password protected zip shouldn't have hash value
            List<AbstractFile> results = openCase.getSleuthkitCase().findAllFilesWhere(
                    "extension = 'txt' and name != 'zipFileWithTxtExtension.txt' and parent_path NOT LIKE '%_protected%'");
            assertEquals(numOfFilesToTest, results.size());
            int numOfHashTested = 0;
            for (AbstractFile file : results) {
                String fileName = file.getName();
                String errMsg = String.format("File name %s doesn't have the extected hash value %s.", fileName, HASH_VALUE);
                assertEquals(errMsg, HASH_VALUE, file.getMd5Hash());
                numOfHashTested++;
            }
            //Make sure the hash value of 1029 files have been tested
            assertEquals(numOfFilesToTest, numOfHashTested);

        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    private void runTestExtension() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Starting");
            //Query zipFileWithTxtExtension.txt at extension folder
            List<AbstractFile> results = openCase.getSleuthkitCase().findAllFilesWhere("extension = 'txt' and parent_path = '/ZIP/extension/zipFileWithTxtExtension.txt/'");
            assertEquals(1, results.size());
            assertEquals("file.txt wasn't extracted from the file: zipFileWithTxtExtension.txt", "file.txt", results.get(0).getName());
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    private void checkEachFileInDeepFolder(AbstractFile file, StringBuffer dirReached, ArrayList<String> fileReached, int numOfDir) {
        String errMsg = String.format("File/Directory name is not as expected name: %s", file.getName());
        if (file.isDir() && !file.getName().equals(".") && !file.getName().equals("..")) {
            numOfDir++;
            assertEquals(errMsg, String.format("dir%d", numOfDir), file.getName());
            dirReached.append(file.getName()).append("/");
            try {
                List<AbstractFile> children = file.listFiles();
                for (AbstractFile child : children) {
                    checkEachFileInDeepFolder(child, dirReached, fileReached, numOfDir);
                }
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }
        } else if (file.isFile() && !file.getName().endsWith("slack")) {
            assertEquals(errMsg, "file.txt", file.getName());
            fileReached.add(file.getParentPath() + file.getName());
        }
    }

    private boolean checkFileInEmbeddedFolder(AbstractFile file) {
        if (file.getName().equals("level1.txt")) {
            return true;
        } else if (file.getNameExtension().equalsIgnoreCase("zip")) {
            try {
                List<AbstractFile> children = file.listFiles();
                for (AbstractFile child : children) {
                    return checkFileInEmbeddedFolder(child);
                }
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }
        } else {
            assertTrue(file.getNameExtension().equalsIgnoreCase("txt"));
        }

        return false;
    }
}
