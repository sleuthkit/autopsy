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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

public class EncryptionDetectionTest extends NbTestCase {

    private static final String PASSWORD_DETECTION_CASE_NAME = "PasswordDetectionTest";
    private static final String VERICRYPT_DETECTION_CASE_NAME = "VeriCryptDetectionTest";

    private final Path PASSWORD_DETECTION_IMAGE_PATH = Paths.get(this.getDataDir().toString(), "password_detection_test.img");
    private final Path VERICRYPT_DETECTION_IMAGE_PATH = Paths.get(this.getDataDir().toString(), "vericrypt_detection_test.vhd");

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
    public void setUp() {
    }

    @Override
    public void tearDown() {
        CaseUtils.closeCase();
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

            Case openCase = Case.getOpenCase();
            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new EncryptionDetectionModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(PASSWORD_DETECTION_CASE_NAME, IngestType.FILES_ONLY, templates);
            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);

            FileManager fileManager = openCase.getServices().getFileManager();
            List<AbstractFile> results = fileManager.findFiles("%%", "ole2");
            results.addAll(fileManager.findFiles("%%", "ooxml"));
            results.addAll(fileManager.findFiles("%%", "pdf"));

            for (AbstractFile file : results) {
                /*
                 * Process only non-slack files.
                 */
                if (file.isFile() && !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)) {
                    /*
                     * Determine which assertions to use for the file based on
                     * its name.
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
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Test the Encryption Detection module's detection of vericrypt encrypted
     * container files and partitions.
     *
     * Test passes if the following are true.
     *
     * 1. A partition was detected without a file system by checking for the
     * error. 2. Only 1 data source exsists in the case, to ensure a stale case
     * did not get used. 3. One volume has a TSK_ENCRYPTION_SUSPECTED artifact
     * associated with it. 4. A single file named vericrpytContainerFile exists.
     * 5. The file named vericrpytContainerFile has a TSK_ENCRYPTION_SUSPECTED
     * artifact associated with it.
     */
    public void testVeriCryptSupport() {
        try {
            CaseUtils.createCase(VERICRYPT_DETECTION_CASE_NAME);
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            List<String> errorMessages = IngestUtils.addDataSource(dataSourceProcessor, VERICRYPT_DETECTION_IMAGE_PATH);
            String joinedErrors;
            if (errorMessages.isEmpty()) {
                joinedErrors = "Encrypted partition did not cause error, it was expected to";
            } else {
                joinedErrors = String.join(System.lineSeparator(), errorMessages);
            }
            //there will be 1 expected error regarding the encrypted partition not having a file system
            assertEquals(joinedErrors, 1, errorMessages.size());

            Case openCase = Case.getOpenCase();
            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new EncryptionDetectionModuleFactory()));
            //image includes an encrypted container file with size greater than 5 mb so default settings detect it
            IngestJobSettings ingestJobSettings = new IngestJobSettings(VERICRYPT_DETECTION_CASE_NAME, IngestType.ALL_MODULES, templates);

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
            assertEquals("No tsk encryption detected artifacts were created for any volume", 1, numberOfEncryptedVolumes);

            //ensure the encrypyted container file was also detected correctly
            FileManager fileManager = openCase.getServices().getFileManager();
            List<AbstractFile> results = fileManager.findFiles("vericrpytContainerFile");
            assertEquals("Expected 1 file named vericryptContainerFile to exist in test image", 1, results.size());
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
