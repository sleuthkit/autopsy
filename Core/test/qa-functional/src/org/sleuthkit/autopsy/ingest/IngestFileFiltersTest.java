/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
import java.util.List;
import static junit.framework.Assert.assertFalse;
import junit.framework.TestCase;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import junit.framework.Test;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.testutils.*;
import org.sleuthkit.autopsy.testutils.DataSourceProcessorRunner.ProcessorCallback;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

public class IngestFileFiltersTest extends TestCase {

    private static final Path caseDirectoryPath = Paths.get(System.getProperty("java.io.tmpdir"), "IngestFileFiltersTest");
    private static final File CASE_DIR = new File(caseDirectoryPath.toString());
    private static final Path imagePath = Paths.get("test/filter_test1.img");
    private final ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
    
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestFileFiltersTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    @Override
    @BeforeClass
    public void setUp() {
        // Delete the test directory, if it exists
        if (caseDirectoryPath.toFile().exists()) {
            try {
                FileUtils.deleteDirectory(caseDirectoryPath.toFile());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex);
            }
        }
        assertFalse("Unable to delete existing test directory", caseDirectoryPath.toFile().exists());
 
        // Create the test directory
        caseDirectoryPath.toFile().mkdirs();
        assertTrue("Unable to create test directory", caseDirectoryPath.toFile().exists());

        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, caseDirectoryPath.toString(), new CaseDetails("IngestFiltersTest"));
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }        
        assertTrue(CASE_DIR.exists());
        ProcessorCallback callBack = null;
        try {
            callBack = DataSourceProcessorRunner.runDataSourceProcessor(dataSourceProcessor, imagePath);
        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
            
        }
        List<Content> dataSourceContent = callBack.getDataSourceContent();
        assertEquals(1, dataSourceContent.size());
        List<String> errorMessages = callBack.getErrorMessages();
        assertEquals(0, errorMessages.size());
    }

    @Override
    @AfterClass
    public void tearDown() {
        try {
            Case.closeCurrentCase();
            FileUtils.deleteDirectory(CASE_DIR);

        } catch (CaseActionException | IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        assertFalse(CASE_DIR.exists());
    }
    
    public void testFileNotFound() {
        try {
            FileManager fm = Case.getOpenCase().getServices().getFileManager();
            List<AbstractFile> results = fm.findFiles("noFound");
            assertEquals(0, results.size());
            
        } catch (TskCoreException | NoCurrentCaseException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    public void testFileFound() {
        try {
            FileManager fm = Case.getOpenCase().getServices().getFileManager();
            List<AbstractFile> results = fm.findFiles("file.jpg", "dir1");
            assertEquals(1, results.size());
            assertEquals("file.jpg", results.get(0).getName());
        } catch (TskCoreException | NoCurrentCaseException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
}
