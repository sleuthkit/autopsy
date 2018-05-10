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
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

public class EncryptionDetectionTest extends NbTestCase {

    private static final String CASE_NAME = "EncryptionDetectionTest";
    private static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), CASE_NAME);
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
        CaseUtils.createCase(CASE_DIRECTORY_PATH, CASE_NAME);
        ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
        IngestUtils.addDataSource(dataSourceProcessor, IMAGE_PATH);
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
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
}
