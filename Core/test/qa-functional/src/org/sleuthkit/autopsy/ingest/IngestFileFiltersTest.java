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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.casemodule.Case;
import junit.framework.Test;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.casemodule.LocalFilesDSProcessor;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.EmbeddedFileExtractorModuleFactory;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.ExtensionCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.FullNameCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.MetaTypeCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.ParentPathCondition;
import org.sleuthkit.autopsy.modules.photoreccarver.PhotoRecCarverIngestModuleFactory;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestJobRunner;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.autopsy.testutils.TestUtilsException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * Functional tests for ingest file filters.
 */
public class IngestFileFiltersTest extends NbTestCase {

    private final Path IMAGE_PATH = Paths.get(this.getDataDir().toString(), "IngestFilters_img1_v1.img");
    private final Path ZIPFILE_PATH = Paths.get(this.getDataDir().toString(), "IngestFilters_local1_v1.zip");

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
    public void tearDown() {
        try {
            CaseUtils.closeCurrentCase();
        } catch (TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    public void testBasicDir() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "---- Starting ----");
            Case currentCase = CaseUtils.createAsCurrentCase("IngestFilter_testBasicDir");
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, IMAGE_PATH);

            HashMap<String, Rule> rule = new HashMap<>();
            rule.put("Rule", new Rule("testFileType", null, new MetaTypeCondition(MetaTypeCondition.Type.FILES), new ParentPathCondition("dir1"), null, null, null, null));
            //Filter for dir1 and no unallocated space
            FilesSet dirFilter = new FilesSet("Filter", "Filter to find all files in dir1.", false, true, rule);

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates, dirFilter);
            IngestUtils.runIngestJob(currentCase.getDataSources(), ingestJobSettings);
            FileManager fileManager = currentCase.getServices().getFileManager();
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
        } catch (TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    public void testExtAndDirWithOneRule() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "---- Starting ----");
            Case currentCase = CaseUtils.createAsCurrentCase("IngestFilter_testExtAndDirWithOneRule");
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, IMAGE_PATH);

            HashMap<String, Rule> rules = new HashMap<>();
            rules.put("Rule", new Rule("testExtAndDirWithOneRule", new ExtensionCondition("jpg"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), new ParentPathCondition("dir1"), null, null, null, null));
            //Build the filter that ignore unallocated space and with one rule
            FilesSet filesExtDirsFilter = new FilesSet("Filter", "Filter to find all jpg files in dir1.", false, true, rules);

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates, filesExtDirsFilter);
            IngestUtils.runIngestJob(currentCase.getDataSources(), ingestJobSettings);
            FileManager fileManager = currentCase.getServices().getFileManager();
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
        } catch (TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    public void testExtAndDirWithTwoRules() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "---- Starting ----");
            Case currentCase = CaseUtils.createAsCurrentCase("IngestFilter_testExtAndDirWithTwoRules");

            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, IMAGE_PATH);

            HashMap<String, Rule> rules = new HashMap<>();
            rules.put("rule1", new Rule("FindJpgExtention", new ExtensionCondition("jpg"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null, null));
            rules.put("rule2", new Rule("FindDir1Directory", null, new MetaTypeCondition(MetaTypeCondition.Type.FILES), new ParentPathCondition("dir1"), null, null, null, null));
            //Build the filter that ingnore unallocated space and with 2 rules
            FilesSet filesExtDirsFilter = new FilesSet("Filter", "Filter to find all files in dir1 and all files with jpg extention.", false, true, rules);

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates, filesExtDirsFilter);
            IngestUtils.runIngestJob(currentCase.getDataSources(), ingestJobSettings);
            FileManager fileManager = currentCase.getServices().getFileManager();
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
        } catch (TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    public void testFullFileNameRule() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "---- Starting ----");
            Case currentCase = CaseUtils.createAsCurrentCase("IngestFilter_testFullFileNameRule");
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, IMAGE_PATH);

            HashMap<String, Rule> rules = new HashMap<>();
            rules.put("rule", new Rule("FindFileWithFullName", new FullNameCondition("file.docx"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null, null));
            //Build the filter to find file: file.docx
            FilesSet fullNameFilter = new FilesSet("Filter", "Filter to find file.docx.", false, true, rules);

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates, fullNameFilter);
            IngestUtils.runIngestJob(currentCase.getDataSources(), ingestJobSettings);
            FileManager fileManager = currentCase.getServices().getFileManager();
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
        } catch (TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    public void testCarvingWithExtRuleAndUnallocSpace() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "---- Starting ----");
            Case currentCase = CaseUtils.createAsCurrentCase("IngestFilter_testCarvingWithExtRuleAndUnallocSpace");
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, IMAGE_PATH);

            HashMap<String, Rule> rules = new HashMap<>();
            rules.put("rule1", new Rule("FindJpgExtention", new ExtensionCondition("jpg"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null, null));
            rules.put("rule2", new Rule("FindGifExtention", new ExtensionCondition("gif"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null, null));

            //Build the filter to find files with .jpg and .gif extension and unallocated space
            FilesSet extensionFilter = new FilesSet("Filter", "Filter to files with .jpg and .gif extension.", false, false, rules);

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory()));
            templates.add(IngestUtils.getIngestModuleTemplate(new PhotoRecCarverIngestModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates, extensionFilter);
            IngestUtils.runIngestJob(currentCase.getDataSources(), ingestJobSettings);
            FileManager fileManager = currentCase.getServices().getFileManager();
            List<AbstractFile> results = fileManager.findFiles("%%");
            assertEquals(72, results.size()); 
            int carvedJpgGifFiles = 0;
            for (AbstractFile file : results) {
                if (file.getNameExtension().equalsIgnoreCase("jpg") || file.getNameExtension().equalsIgnoreCase("gif")) { //Unalloc file and .jpg files in dir1, dir2, $CarvedFiles, root directory should have MIME type
                    String errMsg = String.format("File %s (objId=%d) unexpectedly blocked by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty());

                    if (file.getParentPath().startsWith("/$CarvedFiles/")) {
                        carvedJpgGifFiles++;
                    }
                } else if (file.getName().startsWith("Unalloc_")) {
                    String errMsg = String.format("File %s (objId=%d) unexpectedly blocked by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty());
                } else { //All other files should not have MIME type. 
                    String errMsg = String.format("File %s (objId=%d) unexpectedly passed by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() == null);
                }
            }
            //Make sure we have carved jpg/gif files
            assertEquals(2, carvedJpgGifFiles);

        } catch (TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    public void testCarvingNoUnallocatedSpace() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "---- Starting ----");
            Case currentCase = CaseUtils.createAsCurrentCase("IngestFilter_testCarvingNoUnallocatedSpace");
            ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, IMAGE_PATH);

            HashMap<String, Rule> rules = new HashMap<>();
            rules.put("rule1", new Rule("FindJpgExtention", new ExtensionCondition("jpg"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null, null));
            rules.put("rule2", new Rule("FindGifExtention", new ExtensionCondition("gif"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null, null));

            //Build the filter to find files with .jpg and .gif extension
            FilesSet extensionFilter = new FilesSet("Filter", "Filter to files with .jpg and .gif extension.", false, true, rules);

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory()));
            templates.add(IngestUtils.getIngestModuleTemplate(new PhotoRecCarverIngestModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates, extensionFilter);
            try {
                Logger.getLogger(this.getClass().getName()).log(Level.INFO, "*********************  NOTE: A PhotoRec exception is expected below for this test   ****************************");
                List<IngestModuleError> errs = IngestJobRunner.runIngestJob(currentCase.getDataSources(), ingestJobSettings);
                //Ingest fails because Carving wants unallocated space
                assertEquals(1, errs.size());
                assertEquals("PhotoRec Carver", errs.get(0).getModuleDisplayName());
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }
        } catch (TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    public void testEmbeddedJpg() {
        try {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "---- Starting ----");
            Case currentCase = CaseUtils.createAsCurrentCase("IngestFilter_testEmbeddedJpg");
            LocalFilesDSProcessor dataSourceProcessor = new LocalFilesDSProcessor();
            IngestUtils.addDataSource(dataSourceProcessor, ZIPFILE_PATH);

            //Build the filter to find jpg files
            HashMap<String, Rule> rules = new HashMap<>();
            //Extension condition for jpg files
            rules.put("rule1", new Rule("FindJpgExtention", new ExtensionCondition("jpg"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null, null));
            //Extension condition for zip files, because we want test jpg extension filter for extracted files from a zip file 
            rules.put("rule2", new Rule("ZipExtention", new ExtensionCondition("zip"), new MetaTypeCondition(MetaTypeCondition.Type.FILES), null, null, null, null, null));
            FilesSet embeddedFilter = new FilesSet("Filter", "Filter to files with .jpg extension.", false, false, rules);

            ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
            templates.add(IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory()));
            templates.add(IngestUtils.getIngestModuleTemplate(new EmbeddedFileExtractorModuleFactory()));
            IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestFileFiltersTest.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates, embeddedFilter);
            IngestUtils.runIngestJob(currentCase.getDataSources(), ingestJobSettings);
            FileManager fileManager = currentCase.getServices().getFileManager();
            //get all .jpg files in zip file
            List<AbstractFile> results = fileManager.findFiles("%%");
            assertEquals(39, results.size());
            int numTypeJpgFiles = 0;
            for (AbstractFile file : results) {
                if (file.getNameExtension().equalsIgnoreCase("jpg") || file.getNameExtension().equalsIgnoreCase("zip")) {
                    String errMsg = String.format("File %s (objId=%d) unexpectedly blocked by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty());
                    numTypeJpgFiles++;
                } else if (file.isDir() && (file.getType() == TSK_DB_FILES_TYPE_ENUM.DERIVED || file.getType() == TSK_DB_FILES_TYPE_ENUM.LOCAL)) {
                    String errMsg = String.format("File %s (objId=%d) unexpectedly blocked by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() != null && !file.getMIMEType().isEmpty());
                } else {
                    String errMsg = String.format("File %s (objId=%d) unexpectedly passed by the file filter.", file.getName(), file.getId());
                    assertTrue(errMsg, file.getMIMEType() == null);
                }
            }
            //Make sure 10 jpg files and 1 zip file have been typed
            assertEquals(11, numTypeJpgFiles);
        } catch (TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
}
