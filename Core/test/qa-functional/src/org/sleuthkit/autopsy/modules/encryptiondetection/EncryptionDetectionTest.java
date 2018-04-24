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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import static junit.framework.Assert.assertFalse;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import junit.framework.Test;
import org.apache.commons.io.FileUtils;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.testutils.DataSourceProcessorRunner;
import org.sleuthkit.autopsy.testutils.DataSourceProcessorRunner.ProcessorCallback;
import org.sleuthkit.autopsy.testutils.IngestJobRunner;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

public class EncryptionDetectionTest extends NbTestCase {

    private static final String CASE_NAME = "EncryptionDetectionTest";
    private static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), CASE_NAME);
    private static final File CASE_DIR = new File(CASE_DIRECTORY_PATH.toString());
    private final Path IMAGE_PATH = Paths.get(this.getDataDir().toString(), "password_detection_test.img");

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
        // Delete the test directory, if it exists
        if (CASE_DIRECTORY_PATH.toFile().exists()) {
            try {
                FileUtils.deleteDirectory(CASE_DIRECTORY_PATH.toFile());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex);
            }
        }
        assertFalse("Unable to delete existing test directory", CASE_DIRECTORY_PATH.toFile().exists());

        // Create the test directory
        CASE_DIRECTORY_PATH.toFile().mkdirs();
        assertTrue("Unable to create test directory", CASE_DIRECTORY_PATH.toFile().exists());

        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, CASE_DIRECTORY_PATH.toString(), new CaseDetails(CASE_NAME));
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        assertTrue(CASE_DIR.exists());
        ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
        try {
            ProcessorCallback callBack = DataSourceProcessorRunner.runDataSourceProcessor(dataSourceProcessor, IMAGE_PATH);
            List<Content> dataSourceContent = callBack.getDataSourceContent();
            assertEquals(1, dataSourceContent.size());
            List<String> errorMessages = callBack.getErrorMessages();
            assertEquals(0, errorMessages.size());
        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);

        }
    }

    @Override
    public void tearDown() {
        try {
            Case.closeCurrentCase();
            //Seems like we need some time to close the case.
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                
            }

            FileUtils.deleteDirectory(CASE_DIR);
        } catch (CaseActionException | IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        assertFalse(CASE_DIR.exists());
    }

    /**
     * Test the Encryption Detection module's password protection detection.
     */
    public void testPasswordProtection() {
        try {
            Case openCase = Case.getOpenCase();
            runIngestJob(openCase.getDataSources(), new EncryptionDetectionModuleFactory());
            FileManager fileManager = openCase.getServices().getFileManager();
            Blackboard bb = openCase.getServices().getBlackboard();
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

    private void runIngestJob(List<Content> datasources, IngestModuleFactory factory) {
        IngestModuleIngestJobSettings settings = factory.getDefaultIngestJobSettings();
        IngestModuleTemplate template = new IngestModuleTemplate(factory, settings);
        template.setEnabled(true);
        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(template);
        IngestJobSettings ingestJobSettings = new IngestJobSettings(EncryptionDetectionTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates);
        try {
            List<IngestModuleError> errs = IngestJobRunner.runIngestJob(datasources, ingestJobSettings);
            assertEquals(0, errs.size());
        } catch (InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

}
