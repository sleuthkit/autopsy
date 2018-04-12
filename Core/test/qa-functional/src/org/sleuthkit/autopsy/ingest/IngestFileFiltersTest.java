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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static junit.framework.Assert.assertFalse;
import junit.framework.TestCase;
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
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.ExtensionCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.FullNameCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.MetaTypeCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.ParentPathCondition;
import org.sleuthkit.autopsy.testutils.DataSourceProcessorRunner;
import org.sleuthkit.autopsy.testutils.DataSourceProcessorRunner.ProcessorCallback;
import org.sleuthkit.autopsy.testutils.IngestJobRunner;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

public class IngestFileFiltersTest extends NbTestCase {

    private static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "IngestFileFiltersTest");
    private static final File CASE_DIR = new File(CASE_DIRECTORY_PATH.toString());
    private final Path IMAGE_PATH = Paths.get(this.getDataDir().toString(),"filter_test1.img");
    
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestFileFiltersTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public IngestFileFiltersTest(String name) {
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
        assertFalse("Unable to delete existing test directory" CASE_DIRECTORY_PATH.toFile().exists());
 
        // Create the test directory
        CASE_DIRECTORY_PATH.toFile().mkdirs();
        assertTrue("Unable to create test directory", CASE_DIRECTORY_PATH.toFile().exists());

        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, CASE_DIRECTORY_PATH.toString(), new CaseDetails("IngestFiltersTest"));
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
            } catch (Exception ex) {

            }

            FileUtils.deleteDirectory(CASE_DIR);
        } catch (CaseActionException | IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        assertFalse(CASE_DIR.exists());
    }
    
    public void testBasicDir() {
        HashMap<String, Rule> rule = new HashMap<>();
        rule.put("Rule", new Rule("testFileType", null, new MetaTypeCondition(MetaTypeCondition.Type.FILES), new ParentPathCondition("dir1"), null, null, null));
        //Filter for dir1 and no unallocated space
        FilesSet Files_Dirs_Unalloc_Ingest_Filter = new FilesSet("Filter", "Filter to find all files in dir1.", false, true, rule);        

        try {
            Case openCase = Case.getOpenCase();
            runIngestJob(openCase.getDataSources(), Files_Dirs_Unalloc_Ingest_Filter);
            FileManager fileManager = openCase.getServices().getFileManager();
            List<AbstractFile> results = fileManager.findFiles("file.jpg", "dir1");
            String mimeType = results.get(0).getMIMEType();
            assertEquals("image/jpeg", mimeType);
            
            results = fileManager.findFiles("%%");
          
            for (AbstractFile file : results) {
                //All files in dir1 should have MIME type, except '.' '..' and slack files
                if (file.getParentPath().equalsIgnoreCase("/dir1/")) {
                    if (!(file.getName().equals(".") || file.getName().equals("..") || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)) {
                        String errMsg = String.format("File %s (objId=%d) unexpectedly passed by the file filter.", file.getName(), file.getId());
                        assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty());
                    }
                } else { //All files not in dir1 shouldn't have MIME type
                    String errMsg = String.format("File %s (objId=%d) unexpectedly passed by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() == null);
                }
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
    
    public void testExtAndDirWithOneRule() {
        HashMap<String, Rule> rules = new HashMap<>();
        rules.put("Rule", new Rule("testExtAndDirWithOneRule", new ExtensionCondition("jpg"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), new ParentPathCondition("dir1"), null, null, null));
        //Build the filter that ignore unallocated space and with one rule
        FilesSet filesExtDirsFilter = new FilesSet("Filter", "Filter to find all jpg files in dir1.", false, true, rules);
        
        try {
            Case openCase = Case.getOpenCase();
            runIngestJob(openCase.getDataSources(), filesExtDirsFilter); 
            FileManager fileManager = Case.getOpenCase().getServices().getFileManager();            
            List<AbstractFile> results = fileManager.findFiles("%%");
            assertEquals(62, results.size());
            for (AbstractFile file : results) {
                //Files with jpg extension in dir1 should have MIME Type
                if (file.getParentPath().equalsIgnoreCase("/dir1/") && file.getNameExtension().equalsIgnoreCase("jpg")) {
                    String errMsg = String.format("File %s (objId=%d) unexpectedly blocked by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty());
                } else { //All others shouldn't have MIME Type
                    String errMsg = String.format("File %s (objId=%d) unexpectedly passed by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() == null);
                }
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    public void testExtAndDirWithTwoRules() {
        HashMap<String, Rule> rules = new HashMap<>();
        rules.put("rule1", new Rule("FindJpgExtention", new ExtensionCondition("jpg"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null));
        rules.put("rule2", new Rule("FindDir1Directory", null, new MetaTypeCondition(MetaTypeCondition.Type.FILES), new ParentPathCondition("dir1"), null, null, null));
        //Build the filter that ingnore unallocated space and with 2 rules
        FilesSet filesExtDirsFilter = new FilesSet("Filter", "Filter to find all files in dir1 and all files with jpg extention.", false, true, rules);        
          
        try {
            Case openCase = Case.getOpenCase();
            runIngestJob(openCase.getDataSources(), filesExtDirsFilter); 
            FileManager fileManager = Case.getOpenCase().getServices().getFileManager();           
            List<AbstractFile> results = fileManager.findFiles("%%");  
            assertEquals(62, results.size());
            for (AbstractFile file : results) {               
                if (file.getNameExtension().equalsIgnoreCase("jpg")) { //All files with .jpg extension should have MIME type
                    String errMsg = String.format("File %s (objId=%d) unexpectedly blocked by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty()); 
                } else if (file.getParentPath().equalsIgnoreCase("/dir1/")) { 
                    //All files in dir1 should have MIME type except '.' '..' slack files
                    if (file.getName().equals(".") || file.getName().equals("..") || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.SLACK) {
                        String errMsg = String.format("File %s (objId=%d) unexpectedly passed by the file filter.", file.getName(), file.getId());
                        assertTrue(errMsg, file.getMIMEType() == null);
                    } else {
                        String errMsg = String.format("File %s (objId=%d) unexpectedly blocked by the file filter.", file.getName(), file.getId());
                        assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty());
                    }
                } else { //All files that are not in dir1 or not with .jpg extension should not have MIME type
                    String errMsg = String.format("File %s (objId=%d) unexpectedly passed by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() == null);                     
                }
            }
         } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
   }
   
    public void testFullFileNameRule() {
        HashMap<String, Rule> rules = new HashMap<>();
        rules.put("rule", new Rule("FindFileWithFullName", new FullNameCondition("file.docx"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null));
        //Build the filter to find file: file.docx
        FilesSet fullNameFilter = new FilesSet("Filter", "Filter to find file.docx.", false, true, rules);
                 
        try {
            Case openCase = Case.getOpenCase();
            runIngestJob(openCase.getDataSources(), fullNameFilter); 
            FileManager fileManager = Case.getOpenCase().getServices().getFileManager();           
            List<AbstractFile> results = fileManager.findFiles("%%");
            assertEquals(62, results.size());
            for (AbstractFile file : results) {
                //Only file.docx has MIME Type
                if (file.getName().equalsIgnoreCase("file.docx")) {
                    String errMsg = String.format("File %s (objId=%d) unexpectedly blocked by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty());
                } else {
                    String errMsg = String.format("File %s (objId=%d) unexpectedly passed by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() == null);
                }
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
    private void runIngestJob(List<Content> datasources, FilesSet filter) {
        FileTypeIdModuleFactory factory = new FileTypeIdModuleFactory();
        IngestModuleIngestJobSettings settings = factory.getDefaultIngestJobSettings();
        IngestModuleTemplate template = new IngestModuleTemplate(factory, settings);
        template.setEnabled(true);
        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(template);
        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates, filter);
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
        
 }
