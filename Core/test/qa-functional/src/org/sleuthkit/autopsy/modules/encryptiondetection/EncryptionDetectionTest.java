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
            IngestJobSettings ingestJobSettings = new IngestJobSettings(EncryptionDetectionTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates);
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
     */
    public void testVeriCryptSupport() {
        try {
            CaseUtils.createCase(VERICRYPT_DETECTION_CASE_NAME);
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            List<String> errorMessages = IngestUtils.addDataSource(dataSourceProcessor, VERICRYPT_DETECTION_IMAGE_PATH);
            String joinedErrors = String.join(System.lineSeparator(), errorMessages);
            //there will be 1 expected error regarding the encrypted partition not having a file system
            assertEquals(joinedErrors, 1, errorMessages.size());
            Case openCase = Case.getOpenCase();
            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new EncryptionDetectionModuleFactory()));
            //determine how to configure settings from here WJS-TODO
            IngestJobSettings ingestJobSettings = new IngestJobSettings(EncryptionDetectionTest.class.getCanonicalName(), IngestType.ALL_MODULES, templates);
            IngestUtils.runIngestJob(openCase.getDataSources(), ingestJobSettings);
            //WJS-TODO test results
            //There should be a single file system for the un-incrypted partition
            //There should be a 2 TSK_ENCRYPTION_SUSPECTED artifacts
            //one for the container file
            //one for the encrypted partition

        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

}
