/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreePath;
import org.netbeans.jellytools.MainWindowOperator;
import org.netbeans.jellytools.NbDialogOperator;
import org.netbeans.jellytools.WizardOperator;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Timeout;
import org.netbeans.jemmy.Timeouts;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JCheckBoxOperator;
import org.netbeans.jemmy.operators.JComboBoxOperator;
import org.netbeans.jemmy.operators.JDialogOperator;
import org.netbeans.jemmy.operators.JFileChooserOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTableOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.netbeans.jemmy.operators.JToggleButtonOperator;
import org.netbeans.jemmy.operators.JTreeOperator;
import org.netbeans.jemmy.operators.JTreeOperator.NoSuchPathException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.TskData;

public class AutopsyTestCases {

    private static final Logger logger = Logger.getLogger(AutopsyTestCases.class.getName());
    private long start;
    
    /**
     * Escapes the slashes in a file or directory path.
     *
     * @param path The path to be escaped.
     *
     * @return escaped path the the file/directory location.
     */
    public static String getEscapedPath(String path) {
        if (path.startsWith("\\\\")) { //already has escaped to \\\\NetworkLocation
            return path;
        }
        if (path.startsWith("\\")) {
            return "\\" + path;
        } else {
            return path;
        }
    }

    public AutopsyTestCases(boolean isMultiUser) {
        start = 0;
        if (isMultiUser) {
            setMultiUserPerferences();
        } else {
            UserPreferences.setIsMultiUserModeEnabled(false);
        }
    }

    public void testNewCaseWizardOpen(String title) {
        logger.info("New Case");
        resetTimeouts("WindowWaiter.WaitWindowTimeout", 240000);
        NbDialogOperator nbdo = new NbDialogOperator(title);
        JButtonOperator jbo = new JButtonOperator(nbdo, 0); // the "New Case" button
        jbo.pushNoBlock();
    }

    public void testNewCaseWizard() {
        logger.info("New Case Wizard");
        WizardOperator wo = new WizardOperator("New Case Information");
        JTextFieldOperator jtfo0 = new JTextFieldOperator(wo, 1);
        jtfo0.typeText("AutopsyTestCase"); // Name the case "AutopsyTestCase"
        JTextFieldOperator jtfo1 = new JTextFieldOperator(wo, 2);
        jtfo1.typeText(getEscapedPath(System.getProperty("out_path")));
        wo.btNext().clickMouse();
        JTextFieldOperator jtfo2 = new JTextFieldOperator(wo, 0);
        jtfo2.typeText("000"); // Set the case number
        JTextFieldOperator jtfo3 = new JTextFieldOperator(wo, 1);
        jtfo3.typeText("Examiner 1"); // Set the case examiner
        start = System.currentTimeMillis();
        wo.btFinish().clickMouse();
    }

    public void testStartAddImageFileDataSource() {
        /*
        * This time out is to give time for creating case database and opening solr index
        */
        new Timeout("pausing", 120000).sleep();
        logger.info("Starting Add Image process");
        resetTimeouts("WindowWaiter.WaitWindowTimeOut", 240000);
        WizardOperator wo = new WizardOperator("Add Data Source");
        while(!wo.btNext().isEnabled()){
            new Timeout("pausing", 1000).sleep(); // give it a second till the Add Data Source dialog enabled
        }
        //select the toggle button for Disk Image or VM File it will be the first button created and proceed to next panel
        JToggleButtonOperator jtbo = new JToggleButtonOperator(wo, 0);
        jtbo.clickMouse();
        wo.btNext().clickMouse();
        JTextFieldOperator jtfo0 = new JTextFieldOperator(wo, 0);
        String img_path = getEscapedPath(System.getProperty("img_path"));
        String imageDir = img_path;
        ((JTextComponent) jtfo0.getSource()).setText(imageDir);
        JComboBoxOperator comboBoxOperator = new JComboBoxOperator(wo, 0);
        comboBoxOperator.setSelectedItem("(GMT-5:00) America/New_York");
        wo.btNext().clickMouse();
    }

    public void testStartAddLogicalFilesDataSource() {
        /*
        * This time out is to give time for creating case database and opening solr index
        */
        new Timeout("pausing", 120000).sleep();
        logger.info("Starting Add Logical Files process");
        WizardOperator wo = new WizardOperator("Add Data Source");
        wo.setTimeouts(resetTimeouts("WindowWaiter.WaitWindowTimeOut", 240000));
        while(!wo.btNext().isEnabled()){
            new Timeout("pausing", 1000).sleep(); // give it a second till the Add Data Source dialog enabled
        }
        //select the toggle button for Logical Files it will be the third button created and proceed to next panel
        JToggleButtonOperator jtbo = new JToggleButtonOperator(wo, 2);
        jtbo.clickMouse();
        wo.btNext().clickMouse();
        JButtonOperator addButtonOperator = new JButtonOperator(wo, "Add");
        addButtonOperator.pushNoBlock();
        JFileChooserOperator fileChooserOperator = new JFileChooserOperator();
        fileChooserOperator.setCurrentDirectory(new File(getEscapedPath(System.getProperty("img_path"))));
        // set the current directory one level above the directory containing logicalFileSet folder.
        fileChooserOperator.goUpLevel();
        fileChooserOperator.chooseFile(new File(getEscapedPath(System.getProperty("img_path"))).getName());
        wo.btNext().clickMouse();
    }

    public void testAddSourceWizard1() {
        WizardOperator wo = new WizardOperator("Add Data Source");
        while (!wo.btFinish().isEnabled()) {
            new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        }
        logger.log(Level.INFO, "Add image took {0}ms", (System.currentTimeMillis() - start));
        wo.btFinish().clickMouse();
    }

    public void testConfigureIngest1() {
        /*
         * This timeout is to allow the setup for the ingest job settings panel
         * to complete.
         */
        new Timeout("pausing", 10000).sleep();

        logger.info("Looking for hash lookup module in ingest job settings panel");
        WizardOperator wo = new WizardOperator("Add Data Source");
        while(!wo.btNext().isEnabled()){
            new Timeout("pausing", 1000).sleep(); // give it a second till the Add Data Source dialog enabled
        }
        JTableOperator jto = new JTableOperator(wo, 0);
        int row = jto.findCellRow("Hash Lookup", 2, 0);
        jto.clickOnCell(row, 1);
        logger.info("Selected hash lookup module in ingest job settings panel");
        JButtonOperator jbo1 = new JButtonOperator(wo, "Global Settings");
        jbo1.pushNoBlock();
        logger.info("Pushed Global Settings button for hash lookup module in ingest job settings panel");
    }

    public void testConfigureHash() {
        logger.info("Hash Configure");
        JDialog hashMainDialog = JDialogOperator.waitJDialog("Global Hash Lookup Settings", false, false);
        JDialogOperator hashMainDialogOperator = new JDialogOperator(hashMainDialog);
        List<String> databases = new ArrayList<>();
        databases.add(getEscapedPath(System.getProperty("nsrl_path")));
        databases.add(getEscapedPath(System.getProperty("known_bad_path")));
        databases.stream().map((database) -> {
            JButtonOperator importButtonOperator = new JButtonOperator(hashMainDialogOperator, "Import");
            importButtonOperator.pushNoBlock();
            JDialog addDatabaseDialog = JDialogOperator.waitJDialog("Import Hash Database", false, false);
            JDialogOperator addDatabaseDialogOperator = new JDialogOperator(addDatabaseDialog);
            JButtonOperator browseButtonOperator = new JButtonOperator(addDatabaseDialogOperator, "Open...", 0);
            browseButtonOperator.pushNoBlock();
            JFileChooserOperator fileChooserOperator = new JFileChooserOperator();
            fileChooserOperator.chooseFile(database);
            JButtonOperator okButtonOperator = new JButtonOperator(addDatabaseDialogOperator, "OK", 0);
            return okButtonOperator;
        }).map((okButtonOperator) -> {
            okButtonOperator.pushNoBlock();
            return okButtonOperator;
        }).forEach((_item) -> {
            new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        });
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
        logger.info("Looking for keyword search module in ingest job settings panel");
        WizardOperator wo = new WizardOperator("Add Data Source");
        while(!wo.btNext().isEnabled()){
            new Timeout("pausing", 1000).sleep(); // give it a second till the Add Data Source dialog enabled
        }
        JTableOperator jto = new JTableOperator(wo, 0);
        int row = jto.findCellRow("Keyword Search", 2, 0);
        jto.clickOnCell(row, 1);
        logger.info("Selected keyword search module in ingest job settings panel");
        JButtonOperator jbo1 = new JButtonOperator(wo, "Global Settings");
        jbo1.pushNoBlock();
        logger.info("Pushed Global Settings button for keyword search module in ingest job settings panel");
    }

    public void testConfigureSearch() {
        logger.info("Search Configure");
        JDialog jd = JDialogOperator.waitJDialog("Global Keyword Search Settings", false, false);
        JDialogOperator jdo = new JDialogOperator(jd);
        String words = getEscapedPath(System.getProperty("keyword_path"));
        JButtonOperator jbo0 = new JButtonOperator(jdo, "Import List", 0);
        jbo0.pushNoBlock();
        JFileChooserOperator jfco0 = new JFileChooserOperator();
        jfco0.chooseFile(words);
        JTableOperator jto = new JTableOperator(jdo, 0);
        jto.clickOnCell(0, 0);
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
        WizardOperator wo = new WizardOperator("Add Data Source");
        new Timeout("pausing", 10000).sleep(); // let things catch up
        wo.btNext().clickMouse();
    }

    public void testIngest() {
        logger.info("Ingest 3");
        new Timeout("pausing", 10000).sleep(); // wait for ingest to actually start
        long startIngest = System.currentTimeMillis();
        IngestManager man = IngestManager.getInstance();
        while (man.isIngestRunning()) {
            new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
        }
        logger.log(Level.INFO, "Ingest (including enqueue) took {0}ms", (System.currentTimeMillis() - startIngest));
        // allow keyword search to finish saving artifacts, just in case
        //   but randomize the timing so that we don't always get the same error
        //   consistently, making it seem like default behavior
        Random rand = new Random();
        new Timeout("pausing", 10000 + (rand.nextInt(15000) + 5000)).sleep();
        screenshot("Finished Ingest");

    }

    public void testExpandDataSourcesTree() {
        logger.info("Data Sources Node");
        MainWindowOperator mwo = MainWindowOperator.getDefault();
        JTreeOperator jto = new JTreeOperator(mwo, "Data Sources");
        String [] nodeNames = {"Data Sources"};
        TreePath tp = jto.findPath(nodeNames);
        expandNodes(jto, tp);
        screenshot("Data Sources Tree");
    }

    public void testGenerateReportToolbar() {
        logger.info("Generate Report Toolbars");
        MainWindowOperator mwo = MainWindowOperator.getDefault();
        JButtonOperator jbo = new JButtonOperator(mwo, "Generate Report");
        jbo.pushNoBlock();
        new Timeout("pausing", 5000).sleep();
    }

    public void testGenerateReportButton() throws IOException {
        logger.info("Generate Report Button");
        resetTimeouts("ComponentOperator.WaitComponentTimeout", 240000);
        JDialog reportDialog = JDialogOperator.waitJDialog("Generate Report", false, false);
        JDialogOperator reportDialogOperator = new JDialogOperator(reportDialog);
        JListOperator listOperator = new JListOperator(reportDialogOperator);
        JButtonOperator jbo0 = new JButtonOperator(reportDialogOperator, "Next");
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datenotime = dateFormat.format(date);
        listOperator.clickOnItem(0, 1);
        jbo0.pushNoBlock();
        new Timeout("pausing", 2000).sleep();
        JButtonOperator jbo1 = new JButtonOperator(reportDialogOperator, "Finish");
        jbo1.pushNoBlock();
        JDialog previewDialog = JDialogOperator.waitJDialog("Progress", false, false);
        screenshot("Progress");
        JDialogOperator previewDialogOperator = new JDialogOperator(previewDialog);
        JLabelOperator.waitJLabel(previewDialog, "Complete", false, false);
        JButtonOperator jbo2 = new JButtonOperator(previewDialogOperator, "Close");
        jbo2.pushNoBlock();
        new Timeout("pausing", 10000).sleep();
        System.setProperty("ReportStr", datenotime);
        screenshot("Done Testing");
    }

    public void screenshot(String name) {
        logger.info("Taking screenshot.");
        try {
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage capture = new Robot().createScreenCapture(screenRect);
            String outPath = getEscapedPath(System.getProperty("out_path"));
            ImageIO.write(capture, "png", new File(outPath + "\\" + name + ".png"));
            new Timeout("pausing", 1000).sleep(); // give it a second to save
        } catch (IOException ex) {
            logger.log(Level.WARNING, "IOException taking screenshot.", ex);
        } catch (AWTException ex) {
            logger.log(Level.WARNING, "AWTException taking screenshot.", ex);

        }
    }
    
    /*
     * Nightly test failed at WindowWaiter.WaitWindowTimeOut because of TimeoutExpiredException. So we 
     * use this conveninent method to override the default Jemmy Timeouts value. 
    */

    private Timeouts resetTimeouts(String name, int value) {
        Timeouts timeouts = JemmyProperties.getCurrentTimeouts();
        timeouts.setTimeout(name, value);
        return timeouts;
    }
    
    private void setMultiUserPerferences() {
        UserPreferences.setIsMultiUserModeEnabled(true);
        //PostgreSQL database settings
        CaseDbConnectionInfo connectionInfo = new CaseDbConnectionInfo(
                System.getProperty("dbHost"),
                System.getProperty("dbPort"),
                System.getProperty("dbUserName"),
                System.getProperty("dbPassword"),
                TskData.DbType.POSTGRESQL);
        try {
            UserPreferences.setDatabaseConnectionInfo(connectionInfo);
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error saving case database connection info", ex); //NON-NLS
        }
        //Solr Index settings
        UserPreferences.setIndexingServerHost(System.getProperty("solrHost"));
        UserPreferences.setIndexingServerPort(Integer.parseInt(System.getProperty("solrPort")));
        //ActiveMQ Message Service Setting, username and password field are empty
        MessageServiceConnectionInfo msgServiceInfo = new MessageServiceConnectionInfo(
                System.getProperty("messageServiceHost"),
                Integer.parseInt(System.getProperty("messageServicePort")),
                "",
                "");
        try {
            UserPreferences.setMessageServiceConnectionInfo(msgServiceInfo);
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error saving messaging service connection info", ex); //NON-NLS
        }
    }
    
    private void expandNodes (JTreeOperator jto, TreePath tp) {
        try {
            jto.expandPath(tp);
            for (TreePath t : jto.getChildPaths(tp)) {
                expandNodes(jto, t);
            }
        } catch (NoSuchPathException ne) {
            logger.log(Level.SEVERE, "Error expanding tree path", ne);
        }
    }
}
