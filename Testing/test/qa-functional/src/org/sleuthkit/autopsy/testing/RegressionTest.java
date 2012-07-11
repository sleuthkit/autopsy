/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JTextField;
import junit.framework.Test;
import org.netbeans.jellytools.JellyTestCase;
import org.netbeans.jellytools.MainWindowOperator;
import org.netbeans.jellytools.NbDialogOperator;
import org.netbeans.jellytools.WizardOperator;
import org.netbeans.jemmy.Timeout;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JCheckBoxOperator;
import org.netbeans.jemmy.operators.JDialogOperator;
import org.netbeans.jemmy.operators.JFileChooserOperator;
import org.netbeans.jemmy.operators.JTableOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstract;
/**
 * This test expects the following system properties to be set:
 * img_path: The fully qualified path to the image file (if split, the first file)
 * out_path: The location where the case will be stored
 * nsrl_path: Path to the nsrl database
 * known_bad_path: Path to a database of known bad hashes
 * keyword_path: Path to a keyword list xml file
 * ignore_unalloc: Boolean whether to ignore unallocated space or not
 * 
 * Without these properties set, the test will fail to run correctly.
 * To run this test correctly, you should use the script 'regression.py'
 * located in the 'script' directory of the Testing module.
 */
public class RegressionTest extends JellyTestCase{
    
    private static final Logger logger = Logger.getLogger(RegressionTest.class.getName());
    
    /** Constructor required by JUnit */
    public RegressionTest(String name) {
        super(name);
    }
    
    
    /** Creates suite from particular test cases. */
    public static Test suite() {
        // run tests with specific configuration
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(RegressionTest.class).
                clusters(".*").
                enableModules(".*");
        conf = conf.addTest("testNewCaseWizardOpen",
                "testNewCaseWizard",
                "testAddImageWizard1",
                "testConfigureIngest1",
                "testConfigureHash",
                "testConfigureIngest2",
                "testConfigureSearch",
                "testConfigureIngest2a",
                "testIngest",
                "testGenerateReportToolbar",
                "testGenerateReportButton"       
                );
        return  NbModuleSuite.create(conf);

               
    }

    /** Method called before each test case. */
    @Override
    public void setUp() {
        
        logger.info("########  " + System.getProperty("img_path") + "  #######");
    }

    /** Method called after each test case. */
    @Override
    public void tearDown() {
    }
    
    @org.junit.Test(timeout = 360000)
    public void testNewCaseWizardOpen() {
        logger.info("New Case");
        NbDialogOperator nbdo = new NbDialogOperator("Welcome");
        JButtonOperator jbo = new JButtonOperator(nbdo, 0); // the "New Case" button
        jbo.clickMouse();
    }
    
    @org.junit.Test(timeout = 360000)
    public void testNewCaseWizard() {
        logger.info("New Case Wizard");
        WizardOperator wo = new WizardOperator("New Case Information");
        JTextFieldOperator jtfo1 = new JTextFieldOperator(wo, 1);
        jtfo1.typeText("AutopsyTestCase"); // Name the case "AutopsyTestCase"
        JTextFieldOperator jtfo0 = new JTextFieldOperator(wo, 0);
        jtfo0.typeText(System.getProperty("out_path"));
        wo.btNext().clickMouse();
        JTextFieldOperator jtfo2 = new JTextFieldOperator(wo, 0);
        jtfo2.typeText("000"); // Set the case number
        JTextFieldOperator jtfo3 = new JTextFieldOperator(wo, 1);
        jtfo3.typeText("Examiner 1"); // Set the case examiner
        wo.btFinish().clickMouse();
    }
    @org.junit.Test(timeout = 360000)
    public void testAddImageWizard1() {
        logger.info("AddImageWizard 1");
        WizardOperator wo = new WizardOperator("Add Image");
        JTextFieldOperator jtfo0 = new JTextFieldOperator(wo, 0);
        String imageDir = System.getProperty("img_path");
        ((JTextField)jtfo0.getSource()).setText(imageDir);
        wo.btNext().clickMouse();
        long start = System.currentTimeMillis();
        while(!wo.btNext().isEnabled()) {
            new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        }
        logger.info("Add image took " + (System.currentTimeMillis()-start) + "ms");
        wo.btNext().clickMouse();
    }
    @org.junit.Test(timeout = 360000)
    public void testConfigureIngest1() {
        logger.info("Ingest 1");
        WizardOperator wo = new WizardOperator("Add Image");
        JTableOperator jto = new JTableOperator(wo, 0);
        int row = jto.findCellRow("Hash Lookup", 1, 0);
        jto.clickOnCell(row, 1);
        JButtonOperator jbo1 = new JButtonOperator(wo, "Advanced");
        jbo1.pushNoBlock();
    }
    
    @org.junit.Test(timeout = 360000)
    public void testConfigureHash() {
        logger.info("Hash Configure");
        JDialog hashMainDialog = JDialogOperator.waitJDialog("Hash Database Configuration", false, false);
        JDialogOperator hashMainDialogOperator = new JDialogOperator(hashMainDialog);
        List<String> databases = new ArrayList<String>();
        databases.add(System.getProperty("nsrl_path"));
        databases.add(System.getProperty("known_bad_path"));
        for (String database : databases) {
            JButtonOperator importButtonOperator = new JButtonOperator(hashMainDialogOperator, "Import");
            importButtonOperator.pushNoBlock();
            JDialog addDatabaseDialog = JDialogOperator.waitJDialog("Add Hash Database", false, false);
            JDialogOperator addDatabaseDialogOperator = new JDialogOperator(addDatabaseDialog);
            JButtonOperator browseButtonOperator = new JButtonOperator(addDatabaseDialogOperator, "Browse", 0);
            browseButtonOperator.pushNoBlock();
            JFileChooserOperator fileChooserOperator = new JFileChooserOperator();
            fileChooserOperator.chooseFile(database);
            JButtonOperator okButtonOperator = new JButtonOperator(addDatabaseDialogOperator, "OK", 0);
            okButtonOperator.pushNoBlock();
            new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        }
        // Used if the database has no index
        //JDialog jd3 = JDialogOperator.waitJDialog("No Index Exists", false, false);
        //JDialogOperator jdo3 = new JDialogOperator(jd3);
        //JButtonOperator jbo3 = new JButtonOperator(jdo3, "Yes", 0);
        new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        //jbo3.pushNoBlock();
        JButtonOperator jbo4 = new JButtonOperator(hashMainDialogOperator, "OK", 0);
        jbo4.pushNoBlock();
    }
    
    @org.junit.Test(timeout = 360000)
    public void testConfigureIngest2() {
        logger.info("Ingest 2");
        WizardOperator wo = new WizardOperator("Add Image");
        JTableOperator jto = new JTableOperator(wo, 0);
        int row = jto.findCellRow("Keyword Search", 1, 0);
        jto.clickOnCell(row, 1);
        JButtonOperator jbo1 = new JButtonOperator(wo, "Advanced");
        jbo1.pushNoBlock();
    }
    

    
    @org.junit.Test(timeout = 360000)
    public void testConfigureSearch() {
        logger.info("Search Configure");
        JDialog jd = JDialogOperator.waitJDialog("Keyword List Configuration", false, false);
        JDialogOperator jdo = new JDialogOperator(jd);
        String words = System.getProperty("keyword_path");
        JButtonOperator jbo0 = new JButtonOperator(jdo, "Import List", 0);
        jbo0.pushNoBlock();
        JFileChooserOperator jfco0 = new JFileChooserOperator();
        jfco0.chooseFile(words);
        JCheckBoxOperator jcbo = new JCheckBoxOperator(jdo, "Use during ingest", 0);
        jcbo.doClick();
        JButtonOperator jbo2 = new JButtonOperator(jdo, "OK", 0);
        jbo2.pushNoBlock();
        WizardOperator wo = new WizardOperator("Add Image");
        JCheckBoxOperator jbco0 = new JCheckBoxOperator(wo, "Process Unallocated Space");
        jbco0.setSelected(Boolean.parseBoolean(System.getProperty("ignore_unalloc"))); //ignore unallocated space or not. Set with Regression.py -u
        wo.btNext().clickMouse();
        wo.btFinish().clickMouse();
    }
    

    
    @org.junit.Test(timeout = 360000)
    public void testIngest() {
        logger.info("Ingest 3");
        long start = System.currentTimeMillis();
        IngestManager man = IngestManager.getDefault();
        while(man.isEnqueueRunning()) {
            new Timeout("pausing", 5000).sleep(); // give it a second (or five) to process
        }
        logger.info("Enqueue took " + (System.currentTimeMillis()-start) + "ms");
        while(man.isIngestRunning()) {
            
            new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        }
        new Timeout("pausing", 15000).sleep(); // give it a second (or fifteen) to process
        boolean sleep = true;
        while (sleep) {
            new Timeout("pausing", 5000).sleep(); // give it a second (or five) to process
            sleep = false;

            for (IngestServiceAbstract serv : IngestManager.enumerateAbstractFileServices()) {
                sleep = sleep || serv.hasBackgroundJobsRunning();
            }
        }
        logger.info("Ingest (including enqueue) took " + (System.currentTimeMillis()-start) + "ms");
        new Timeout("pausing", 5000).sleep(); // allow keyword search to finish saving artifacts, just in case
        
    }
    
    @org.junit.Test(timeout = 360000)
    public void testGenerateReportToolbar() {

        logger.info("Generate Report Toolbars");
        // Force the action if necessary:
        //new Action("Tools|Generate Report", null).perform();
        //new Timeout("pausing", 1000).sleep();
        MainWindowOperator mwo = MainWindowOperator.getDefault();
        JButtonOperator jbo = new JButtonOperator(mwo, "Generate Report");
        jbo.pushNoBlock();
        new Timeout("pausing", 1000).sleep();
    }
    
    @org.junit.Test(timeout = 360000)
    public void testGenerateReportButton() {
        logger.info("Generate Report Button");
        JDialog reportDialog = JDialogOperator.waitJDialog("Generate Report", false, false);
        JDialogOperator reportDialogOperator = new JDialogOperator(reportDialog);
        JCheckBoxOperator jcbo0 = new JCheckBoxOperator(reportDialogOperator, "Excel");
        jcbo0.doClick();
        JCheckBoxOperator jcbo1 = new JCheckBoxOperator(reportDialogOperator, "Default XML");
        jcbo1.doClick();
        JButtonOperator jbo0 = new JButtonOperator(reportDialogOperator, "Generate Report");
        jbo0.pushNoBlock();
        new Timeout("pausing", 1000).sleep(); // Give it a second to open
        
        JDialog previewDialog = JDialogOperator.waitJDialog("Report Preview", false, false);
        JDialogOperator previewDialogOperator = new JDialogOperator(previewDialog);
        JButtonOperator jbo1 = new JButtonOperator(previewDialogOperator, "Close");
        jbo1.pushNoBlock();
        new Timeout("pausing", 1000).sleep(); // Give the program a second to idle for ascetics
    }
}