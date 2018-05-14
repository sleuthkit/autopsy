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
package org.sleuthkit.autopsy.modules.encryptiondetection;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.casemodule.Case;
import junit.framework.Test;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

public class EncryptionDetectionTest extends NbTestCase {

    private static final String BITLOCKER_CASE_NAME = "testBitlockerEncryption";
    private static final String PASSWORD_CASE_NAME = "testPasswordProtection";
    
    private static final Path BITLOCKER_CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), BITLOCKER_CASE_NAME);
    private static final Path PASSWORD_CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), PASSWORD_CASE_NAME);
    
    private final Path BITLOCKER_IMAGE_PATH = Paths.get(this.getDataDir().toString(), "encryption_detection_bitlocker_test.vhd");
    private final Path PASSWORD_IMAGE_PATH = Paths.get(this.getDataDir().toString(), "password_detection_test.img");

    private static final String PASSWORD_DETECTION_CASE_NAME = "PasswordDetectionTest";
    private static final String VERACRYPT_DETECTION_CASE_NAME = "VeraCryptDetectionTest";

    private final Path PASSWORD_DETECTION_IMAGE_PATH = Paths.get(this.getDataDir().toString(), "password_detection_test.img");
    private final Path VERACRYPT_DETECTION_IMAGE_PATH = Paths.get(this.getDataDir().toString(), "veracrypt_detection_test.vhd");
  
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(EncryptionDetectionTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public EncryptionDetectionTest(String name) {
        super(name);
    }

    @Override
    public void tearDown() {
        CaseUtils.closeCase();
    }

    /**
     * Test the Encryption Detection module's volume encryption detection.
     */
    public void testBitlockerEncryption() {
        try {
            CaseUtils.createCase(BITLOCKER_CASE_DIRECTORY_PATH, BITLOCKER_CASE_NAME);
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, BITLOCKER_IMAGE_PATH);
            Case openCase = Case.getCurrentCaseThrows();

            /*
             * Create ingest job settings.
             */
            IngestModuleFactory ingestModuleFactory = new EncryptionDetectionModuleFactory();
            IngestModuleIngestJobSettings settings = ingestModuleFactory.getDefaultIngestJobSettings();
            IngestModuleTemplate template = new IngestModuleTemplate(ingestModuleFactory, settings);
            template.setEnabled(true);
            List<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(template);
            IngestJobSettings ingestJobSettings = new IngestJobSettings(EncryptionDetectionTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates);
            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);

            /*
             * Process each volume.
             */
            boolean vol2Found = false;

            String errorMessage;

            Image dataSource = (Image) openCase.getDataSources().get(0);
            List<VolumeSystem> volumeSystems = dataSource.getVolumeSystems();
            for (VolumeSystem volumeSystem : volumeSystems) {
                for (Volume volume : volumeSystem.getVolumes()) {
                    List<BlackboardArtifact> artifactsList = volume.getAllArtifacts();

                    if (volume.getName().equals("vol2")) {
                        vol2Found = true;

                        errorMessage = String.format("Expected one artifact for '%s', but found %d.",
                                volume.getName(), artifactsList.size());
                        assertEquals(errorMessage, 1, artifactsList.size());

                        String artifactTypeName = artifactsList.get(0).getArtifactTypeName();
                        errorMessage = String.format("Unexpected '%s' artifact for '%s'.",
                                artifactTypeName, volume.getName());
                        assertEquals(errorMessage, BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.toString(), artifactTypeName);

                        BlackboardAttribute attribute = artifactsList.get(0).getAttribute(
                                new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT));
                        errorMessage = String.format("Expected a TSK_COMMENT attribute for '%s', but found none.",
                                volume.getName());
                        assertNotNull(errorMessage, attribute);

                        errorMessage = String.format("Unexpected attribute value: \"%s\"", attribute.getValueString());
                        assertEquals(errorMessage, "Bitlocker encryption detected.", attribute.getValueString());
                    } else {
                        errorMessage = String.format("Expected no artifacts for '%s', but found %d.",
                                volume.getName(), artifactsList.size());
                        assertEquals(errorMessage, 0, artifactsList.size());
                    }
                }
            }

            errorMessage = "Expected to find 'vol2', but no such volume exists.";
            assertEquals(errorMessage, true, vol2Found);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Test the Encryption Detection module's password protection detection.
     */
    public void testPasswordProtection() {
        try {
            CaseUtils.createCase(PASSWORD_DETECTION_CASE_NAME);

            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            List<String> errorMessages = IngestUtils.addDataSource(dataSourceProcessor, PASSWORD_DETECTION_IMAGE_PATH);
            String joinedErrors = String.join(System.lineSeparator(), errorMessages);
            assertEquals(joinedErrors, 0, errorMessages.size());

            Case openCase = Case.getCurrentCaseThrows();    

            /*
             * Create ingest job settings.
             */

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new EncryptionDetectionModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(PASSWORD_DETECTION_CASE_NAME, IngestType.FILES_ONLY, templates);
            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);

            /*
             * Purge specific files to be tested.
             */
            FileManager fileManager = openCase.getServices().getFileManager();
            List<List<AbstractFile>> allResults = new ArrayList<>(0);

            List<AbstractFile> ole2Results = fileManager.findFiles("%%", "ole2");
            assertEquals("Unexpected number of OLE2 results.", 11, ole2Results.size());

            List<AbstractFile> ooxmlResults = fileManager.findFiles("%%", "ooxml");
            assertEquals("Unexpected number of OOXML results.", 13, ooxmlResults.size());

            List<AbstractFile> pdfResults = fileManager.findFiles("%%", "pdf");
            assertEquals("Unexpected number of PDF results.", 6, pdfResults.size());

            List<AbstractFile> mdbResults = fileManager.findFiles("%%", "mdb");
            assertEquals("Unexpected number of MDB results.", 25, mdbResults.size());

            List<AbstractFile> accdbResults = fileManager.findFiles("%%", "accdb");
            assertEquals("Unexpected number of ACCDB results.", 10, accdbResults.size());

            allResults.add(ole2Results);
            allResults.add(ooxmlResults);
            allResults.add(pdfResults);
            allResults.add(mdbResults);
            allResults.add(accdbResults);

            for (List<AbstractFile> results : allResults) {
                for (AbstractFile file : results) {
                    /*
                     * Process only non-slack files.
                     */
                    if (file.isFile() && !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)) {
                        /*
                         * Determine which assertions to use for the file based
                         * on its name.
                         */
                        boolean fileProtected = file.getName().split("\\.")[0].endsWith("-protected");
                        List<BlackboardArtifact> artifactsList = file.getAllArtifacts();
                        if (fileProtected) {
                            /*
                             * Check that the protected file has one
                             * TSK_ENCRYPTION_DETECTED artifact.
                             */
                            int artifactsListSize = artifactsList.size();
                            String errorMessage = String.format("File '%s' (objId=%d) has %d artifacts, but 1 was expected.", file.getName(), file.getId(), artifactsListSize);
                            assertEquals(errorMessage, 1, artifactsListSize);

                            String artifactTypeName = artifactsList.get(0).getArtifactTypeName();
                            errorMessage = String.format("File '%s' (objId=%d) has an unexpected '%s' artifact.", file.getName(), file.getId(), artifactTypeName);
                            assertEquals(errorMessage, BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.toString(), artifactTypeName);
                        } else {
                            /*
                             * Check that the unprotected file has no artifacts.
                             */
                            int artifactsListSize = artifactsList.size();
                            String errorMessage = String.format("File '%s' (objId=%d) has %d artifacts, but none were expected.", file.getName(), file.getId(), artifactsListSize);
                            assertEquals(errorMessage, 0, artifactsListSize);
                        }
                    }
                }
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Test the Encryption Detection module's detection of veracrypt encrypted
     * container files and partitions.
     *
     * Test passes if the following are true.
     *
     * 1. A partition was detected without a file system by checking for the
     * error. 2. Only 1 data source exsists in the case, to ensure a stale case
     * did not get used. 3. One volume has a TSK_ENCRYPTION_SUSPECTED artifact
     * associated with it. 4. A single file named veracrpytContainerFile exists.
     * 5. The file named veracrpytContainerFile has a TSK_ENCRYPTION_SUSPECTED
     * artifact associated with it.
     */
    public void testVeraCryptSupport() {
        try {
            CaseUtils.createCase(VERACRYPT_DETECTION_CASE_NAME);
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            List<String> errorMessages = IngestUtils.addDataSource(dataSourceProcessor, VERACRYPT_DETECTION_IMAGE_PATH);
            String joinedErrors;
            if (errorMessages.isEmpty()) {
                joinedErrors = "Encrypted partition did not cause error, it was expected to";
            } else {
                joinedErrors = String.join(System.lineSeparator(), errorMessages);
            }
            //there will be 1 expected error regarding the encrypted partition not having a file system
            assertEquals(joinedErrors, 1, errorMessages.size());

            Case openCase = Case.getCurrentCaseThrows();
            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new EncryptionDetectionModuleFactory()));
            //image includes an encrypted container file with size greater than 5 mb so default settings detect it
            IngestJobSettings ingestJobSettings = new IngestJobSettings(VERACRYPT_DETECTION_CASE_NAME, IngestType.ALL_MODULES, templates);

            assertEquals("Expected only one data source to exist in the Case", 1, openCase.getDataSources().size());
            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);

            //check that one of the partitions has an encrypted volume
            int numberOfEncryptedVolumes = 0;
            for (Content datasource : openCase.getDataSources()) { //data source
                for (Content volumeSystem : datasource.getChildren()) { //volume system 
                    for (Content volume : volumeSystem.getChildren()) { //volumes
                        numberOfEncryptedVolumes += volume.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED).size();
                    }
                }
            }
            assertEquals("One volume should exist with an encryption suspsected artifact", 1, numberOfEncryptedVolumes);

            //ensure the encrypyted container file was also detected correctly
            FileManager fileManager = openCase.getServices().getFileManager();
            List<AbstractFile> results = fileManager.findFiles("veracryptContainerFile");
            assertEquals("Expected 1 file named veracryptContainerFile to exist in test image", 1, results.size());
            int numberOfEncryptedContainers = 0;
            for (AbstractFile file : results) {
                numberOfEncryptedContainers += file.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED).size();
            }
            assertEquals("Encrypted Container file should have one encyption suspected artifact", 1, numberOfEncryptedContainers);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

}
