/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import junit.framework.Test;
import junit.framework.TestCase;
import org.netbeans.jellytools.MainWindowOperator;
import org.netbeans.jellytools.NbDialogOperator;
import org.netbeans.jellytools.WizardOperator;
import org.netbeans.jemmy.Timeout;
import org.netbeans.jemmy.Timeouts;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.operators.JCheckBoxOperator;
import org.netbeans.jemmy.operators.JDialogOperator;
import org.netbeans.jemmy.operators.JFileChooserOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTableOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.keywordsearch.*;

/**
 * This test expects the following system properties to be set: img_path: The
 * fully qualified path to the image file (if split, the first file) out_path:
 * The location where the case will be stored nsrl_path: Path to the nsrl
 * database known_bad_path: Path to a database of known bad hashes keyword_path:
 * Path to a keyword list xml file ignore_unalloc: Boolean whether to ignore
 * unallocated space or not
 *
 * Without these properties set, the test will fail to run correctly. To run
 * this test correctly, you should use the script 'regression.py' located in the
 * 'script' directory of the Testing module.
 */
public class RegressionTest extends TestCase {

    private static final Logger logger = Logger.getLogger(RegressionTest.class.getName());
    long start;

    /**
     * Constructor required by JUnit
     */
    public RegressionTest(String name) {
        super(name);
    }

    /**
     * Creates suite from particular test cases.
     */
    public static Test suite() {
        // run tests with specific configuration
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(RegressionTest.class).
                clusters(".*").
                enableModules(".*");
        conf = conf.addTest("testNewCaseWizardOpen",
                "testNewCaseWizard",
                "testStartAddDataSource",
                "testConfigureIngest1",
                "testConfigureHash",
                "testConfigureIngest2",
                "testConfigureSearch",
                "testAddSourceWizard1",
                "testIngest",
                "testGenerateReportToolbar",
                "testGenerateReportButton");
        return NbModuleSuite.create(conf);


    }

    /**
     * Method called before each test case.
     */
    @Override
    public void setUp() {

        logger.info("########  " + System.getProperty("img_path") + "  #######");
        Timeouts.setDefault("ComponentOperator.WaitComponentTimeout", 1000000);
    }

    /**
     * Method called after each test case.
     */
    @Override
    public void tearDown() {
    }

    public void testNewCaseWizardOpen() {
        logger.info("New Case");
        NbDialogOperator nbdo = new NbDialogOperator("Welcome");
        JButtonOperator jbo = new JButtonOperator(nbdo, 0); // the "New Case" button
        jbo.pushNoBlock();
    }

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
        start = System.currentTimeMillis();
        wo.btFinish().clickMouse();
    }

    public void testStartAddDataSource() {
        logger.info("Starting Add Image process");
        WizardOperator wo = new WizardOperator("Add Data");
        JTextFieldOperator jtfo0 = new JTextFieldOperator(wo, 0);
        String img_path = System.getProperty("img_path");
        if (img_path.startsWith("\\")) {
            img_path = "\\" + img_path;
        }
        String imageDir = img_path;
        ((JTextField) jtfo0.getSource()).setText(imageDir);
        wo.btNext().clickMouse();
    }

    public void testAddSourceWizard1() {
        WizardOperator wo = new WizardOperator("Add Data");
        while (!wo.btFinish().isEnabled()) {
            new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        }
        logger.info("Add image took " + (System.currentTimeMillis() - start) + "ms");
        wo.btFinish().clickMouse();
    }

    public void testConfigureIngest1() {
        logger.info("Ingest 1");
        WizardOperator wo = new WizardOperator("Add Data");
        JTableOperator jto = new JTableOperator(wo, 0);
        int row = jto.findCellRow("Hash Lookup", 1, 0);
        jto.clickOnCell(row, 1);
        JButtonOperator jbo1 = new JButtonOperator(wo, "Advanced");
        jbo1.pushNoBlock();
    }

    public void testConfigureHash() {
        logger.info("Hash Configure");
        JDialog hashMainDialog = JDialogOperator.waitJDialog("Hash Set Configuration", false, false);
        JDialogOperator hashMainDialogOperator = new JDialogOperator(hashMainDialog);
        List<String> databases = new ArrayList<String>();
        databases.add(System.getProperty("nsrl_path"));
        databases.add(System.getProperty("known_bad_path"));
        for (String database : databases) {
            JButtonOperator importButtonOperator = new JButtonOperator(hashMainDialogOperator, "Import");
            importButtonOperator.pushNoBlock();
            JDialog addDatabaseDialog = JDialogOperator.waitJDialog("Import Hash Database", false, false);
            JDialogOperator addDatabaseDialogOperator = new JDialogOperator(addDatabaseDialog);
            JButtonOperator browseButtonOperator = new JButtonOperator(addDatabaseDialogOperator, "Open...", 0);
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

    public void testConfigureIngest2() {
        logger.info("Ingest 2");
        WizardOperator wo = new WizardOperator("Add Data");
        JTableOperator jto = new JTableOperator(wo, 0);
        int row = jto.findCellRow("Keyword Search", 1, 0);
        jto.clickOnCell(row, 1);
        JButtonOperator jbo1 = new JButtonOperator(wo, "Advanced");
        jbo1.pushNoBlock();
    }

    public void testConfigureSearch() {
        logger.info("Search Configure");
        JDialog jd = JDialogOperator.waitJDialog("Advanced Keyword Search Configuration", false, false);
        JDialogOperator jdo = new JDialogOperator(jd);
        //setListForIngest();
        String words = System.getProperty("keyword_path");
        JButtonOperator jbo0 = new JButtonOperator(jdo, "Import List", 0);
        jbo0.pushNoBlock();
        JFileChooserOperator jfco0 = new JFileChooserOperator();
        jfco0.chooseFile(words);
        JTableOperator jto = new JTableOperator(jdo, 0);
        jto.clickOnCell(0, 0);
        JCheckBoxOperator jcbo = new JCheckBoxOperator(jdo, "Use during ingest", 0);
        if (!(jcbo.isSelected())) {
            jcbo.doClick();
        }
        new Timeout("pausing", 1000).sleep(); // give it a second to process
        if (Boolean.parseBoolean(System.getProperty("mugen_mode"))) {
            JTabbedPaneOperator jtpo = new JTabbedPaneOperator(jdo);
            jtpo.selectPage("String Extraction");
            JCheckBoxOperator jcbo0 = new JCheckBoxOperator(jtpo, "Arabic (Arabic)");
            jcbo0.doClick();
            JCheckBoxOperator jcbo1 = new JCheckBoxOperator(jtpo, "Han (Chinese, Japanese, Korean)");
            jcbo1.doClick();
            new Timeout("pausing", 1000).sleep(); // give it a second to process
        }
        JButtonOperator jbo2 = new JButtonOperator(jdo, "OK", 0);
        jbo2.pushNoBlock();
        WizardOperator wo = new WizardOperator("Add Data");
        JCheckBoxOperator jbco0 = new JCheckBoxOperator(wo, "Process Unallocated Space");
        if (Boolean.parseBoolean(System.getProperty("ignore_unalloc"))) {
            jbco0.doClick();
        }
        wo.btNext().clickMouse();
    }

    public void testIngest() {
        logger.info("Ingest 3");
        long start = System.currentTimeMillis();
        IngestManager man = IngestManager.getDefault();
        while (man.isEnqueueRunning()) {
            new Timeout("pausing", 5000).sleep(); // give it a second (or five) to process
        }
        logger.info("Enqueue took " + (System.currentTimeMillis() - start) + "ms");
        while (man.isIngestRunning()) {
            new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        }
        new Timeout("pausing", 15000).sleep(); // give it a second (or fifteen) to process
        while (man.areModulesRunning()) {
           new Timeout("pausing", 5000).sleep(); // give it a second (or five) to process
        }
        
        logger.info("Ingest (including enqueue) took " + (System.currentTimeMillis() - start) + "ms");
        // allow keyword search to finish saving artifacts, just in case
        //   but randomize the timing so that we don't always get the same error
        //   consistently, making it seem like default behavior
        Random rand = new Random();
        new Timeout("pausing", 10000 + (rand.nextInt(15000) + 5000)).sleep();
        screenshot("Finished Ingest");

    }

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

    public void testGenerateReportButton() throws IOException {
        logger.info("Generate Report Button");
        JDialog reportDialog = JDialogOperator.waitJDialog("Generate Report", false, false);
        JDialogOperator reportDialogOperator = new JDialogOperator(reportDialog);
        JListOperator listOperator = new JListOperator(reportDialogOperator);
        JButtonOperator jbo0 = new JButtonOperator(reportDialogOperator, "Next");
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datenotime = dateFormat.format(date);
        listOperator.clickOnItem(1, 1);
        new Timeout("pausing", 1000).sleep();
        jbo0.pushNoBlock();
        new Timeout("pausing", 1000).sleep();
        JButtonOperator jbo1 = new JButtonOperator(reportDialogOperator, "Finish");
        jbo1.pushNoBlock();
        new Timeout("pausing", 500).sleep();
        JDialog previewDialog = JDialogOperator.waitJDialog("Progress", false, false);
        screenshot("Progress");
        JDialogOperator previewDialogOperator = new JDialogOperator(previewDialog);
        JLabel waiter = JLabelOperator.waitJLabel(previewDialog, "Complete", false, false);
        JButtonOperator jbo2 = new JButtonOperator(previewDialogOperator, "Close");
        jbo2.pushNoBlock();
        new Timeout("pausing", 3000).sleep(); // Give the program a second to idle to be safe
        System.setProperty("ReportStr", datenotime);
        screenshot("Done Testing");
    }

    public void screenshot(String name) {
        logger.info("Taking screenshot.");
        try {
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage capture = new Robot().createScreenCapture(screenRect);
            String outPath = System.getProperty("out_path");
            ImageIO.write(capture, "png", new File(outPath + "\\" + name + ".png"));
            new Timeout("pausing", 1000).sleep(); // give it a second to save
        } catch (IOException ex) {
            logger.log(Level.WARNING, "IOException taking screenshot.", ex);
        } catch (AWTException ex) {
            logger.log(Level.WARNING, "AWTException taking screenshot.", ex);

        }
    }

    private void setListForIngest() {
        KeywordSearchListsXML curr = KeywordSearchListsXML.getCurrent();
        curr.setUseForIngest("URLs", true);
    }
}
