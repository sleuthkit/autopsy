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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import junit.framework.Test;
import org.apache.commons.io.FileUtils;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.EmbeddedFileExtractorModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.DataSourceProcessorRunner;
import org.sleuthkit.autopsy.testutils.IngestJobRunner;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

public class EmbeddedFileTest extends NbTestCase {

    private static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "EmbeddedFileTest");
    private static final File CASE_DIR = new File(CASE_DIRECTORY_PATH.toString());
    private final Path IMAGE_PATH = Paths.get(this.getDataDir().toString(),"embedded.vhd");
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
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, CASE_DIRECTORY_PATH.toString(), new CaseDetails("EmbeddedFileTest"));
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        assertTrue(CASE_DIR.exists());
        ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
        try {
            DataSourceProcessorRunner.ProcessorCallback callBack = DataSourceProcessorRunner.runDataSourceProcessor(dataSourceProcessor, IMAGE_PATH);
            List<Content> dataSourceContent = callBack.getDataSourceContent();
            assertEquals(1, dataSourceContent.size());
            List<String> errorMessages = callBack.getErrorMessages();
            assertEquals(0, errorMessages.size());
        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            
        }
        
        try {
            openCase = Case.getOpenCase();
            runIngestJob(openCase.getDataSources());
        } catch (NoCurrentCaseException | TskCoreException ex) {
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
            } catch (Exception ex) {

            }

            FileUtils.deleteDirectory(CASE_DIR);
        } catch (CaseActionException | IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        assertFalse(CASE_DIR.exists());
    }
    
    public void testEncription() {
        try {
            List<AbstractFile> results = openCase.getSleuthkitCase().findAllFilesWhere("name LIKE '%%'");            
            String protectedName1 = "password_protected.zip";
            String protectedName2 = "level1_protected.zip";
            String protectedName3 =  "42.zip";
            assertEquals(2207, results.size());
            int passwdProtectedZips = 0;
            for (AbstractFile file : results) {
                //.zip file has artifact TSK_ENCRYPTION_DETECTED
                if (file.getName().equalsIgnoreCase(protectedName1) || file.getName().equalsIgnoreCase(protectedName2) || file.getName().equalsIgnoreCase(protectedName3)){
                    ArrayList<BlackboardArtifact> artifacts = file.getAllArtifacts();
                    assertEquals(1, artifacts.size());
                    for (BlackboardArtifact artifact : artifacts) {
                        assertEquals(artifact.getArtifactTypeID(), BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID());
                        passwdProtectedZips++;
                    }
                } else {//No other files has artifact defined
                    assertEquals(0, file.getAllArtifacts().size());
                }
                
                
            }
            //Make sure 3 password protected zip files has been tested: password_protected.zip, level1_protected.zip and 42.zip that we download for bomb testing.
            assertEquals(3, passwdProtectedZips);
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
        
    }

    private void runIngestJob(List<Content> datasources) {
        IngestModuleTemplate embeddedTemplate = getIngestModuleTemplate(new EmbeddedFileExtractorModuleFactory());
        IngestModuleTemplate hashLookupTemplate = getIngestModuleTemplate(new HashLookupModuleFactory());

        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(embeddedTemplate);
        templates.add(hashLookupTemplate);
        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates);
        try {
            List<IngestModuleError> errs = IngestJobRunner.runIngestJob(datasources, ingestJobSettings);
            for (IngestModuleError err : errs) {
                System.out.println(String.format("Error: %s: %s.", err.getModuleDisplayName(), err.toString()));
            }
            assertEquals(0, errs.size());
        } catch (InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }        
    }

    private IngestModuleTemplate getIngestModuleTemplate(IngestModuleFactoryAdapter factory) {
        IngestModuleIngestJobSettings settings = factory.getDefaultIngestJobSettings();
        IngestModuleTemplate template = new IngestModuleTemplate(factory, settings);
        template.setEnabled(true);
        return template;
    }
}
