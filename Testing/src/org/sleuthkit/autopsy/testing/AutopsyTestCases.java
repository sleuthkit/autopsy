/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreePath;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.netbeans.jellytools.MainWindowOperator;
import org.netbeans.jellytools.NbDialogOperator;
import org.netbeans.jellytools.WizardOperator;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Timeout;
import org.netbeans.jemmy.TimeoutExpiredException;
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
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.TskData;

public class AutopsyTestCases {

    private static final Logger logger = Logger.getLogger(AutopsyTestCases.class.getName()); // DO NOT USE AUTOPSY LOGGER
    private long start;
    
    // by default, how many minutes jemmy waits for a dialog to appear (default is 1 minute).
    private static final long DIALOG_FIND_TIMEOUT_MINUTES = 5;
    
    static {
        Timeouts.setDefault("Waiter.WaitingTime", DIALOG_FIND_TIMEOUT_MINUTES * 60 * 1000);
    }

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
        try {
            logger.info("New Case");
            setTimeout("WindowWaiter.WaitWindowTimeout", 240000);
            NbDialogOperator nbdo = new NbDialogOperator(title);
            JButtonOperator jbo = new JButtonOperator(nbdo, 0); // the "New Case" button
            jbo.pushNoBlock();
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testNewCaseWizard() {
        try {
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
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testStartAddImageFileDataSource() {
        try {
            /*
             * This time out is to give time for creating case database and
             * opening solr index
             */
            new Timeout("pausing", 120000).sleep();
            logger.info("Starting Add Image process");
            setTimeout("WindowWaiter.WaitWindowTimeOut", 240000);
            WizardOperator wo = new WizardOperator("Add Data Source");
            while (!wo.btNext().isEnabled()) {
                new Timeout("pausing", 1000).sleep(); // give it a second till the Add Data Source dialog enabled
            }

            // pass by host menu with auto-generate host (which should already be selected)
            wo.btNext().clickMouse();

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
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testStartAddLogicalFilesDataSource() {
        try {
            /*
             * This time out is to give time for creating case database and
             * opening solr index
             */
            new Timeout("pausing", 120000).sleep();
            logger.info("Starting Add Logical Files process");
            WizardOperator wo = new WizardOperator("Add Data Source");
            wo.setTimeouts(setTimeout("WindowWaiter.WaitWindowTimeOut", 240000));
            while (!wo.btNext().isEnabled()) {
                new Timeout("pausing", 1000).sleep(); // give it a second till the Add Data Source dialog enabled
            }

            // pass by host menu with auto-generate host (which should already be selected)
            wo.btNext().clickMouse();

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
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testAddSourceWizard1() {
        try {
            WizardOperator wo = new WizardOperator("Add Data Source");
            while (!wo.btFinish().isEnabled()) {
                new Timeout("pausing", 1000).sleep(); // give it a second (or five) to process
            }
            logger.log(Level.INFO, "Add image took {0}ms", (System.currentTimeMillis() - start));
            wo.btFinish().clickMouse();
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testConfigureIngest1() {
        try {
            /*
             * This timeout is to allow the setup for the ingest job settings
             * panel to complete.
             */
            new Timeout("pausing", 10000).sleep();

            logger.info("Looking for hash lookup module in ingest job settings panel");
            WizardOperator wo = new WizardOperator("Add Data Source");
            while (!wo.btNext().isEnabled()) {
                new Timeout("pausing", 1000).sleep(); // give it a second till the Add Data Source dialog enabled
            }
            JTableOperator jto = new JTableOperator(wo, 0);
            int row = jto.findCellRow("Hash Lookup", 2, 0);
            jto.clickOnCell(row, 1);
            logger.info("Selected hash lookup module in ingest job settings panel");
            JButtonOperator jbo1 = new JButtonOperator(wo, "Global Settings");
            jbo1.pushNoBlock();
            logger.info("Pushed Global Settings button for hash lookup module in ingest job settings panel");
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testConfigureHash() {
        try {
            logger.info("Hash Configure");
            JDialog hashMainDialog = JDialogOperator.waitJDialog("Global Hash Lookup Settings", false, false);
            JDialogOperator hashMainDialogOperator = new JDialogOperator(hashMainDialog);
            List<String> databases = new ArrayList<>();
            databases.add(getEscapedPath(System.getProperty("nsrl_path")));
            databases.add(getEscapedPath(System.getProperty("known_bad_path")));
            databases.stream().map((database) -> {
                JButtonOperator importButtonOperator = new JButtonOperator(hashMainDialogOperator, "Import");
                importButtonOperator.pushNoBlock();
                JDialog addDatabaseDialog = JDialogOperator.waitJDialog("Import Hash Set", false, false);
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
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testConfigureIngest2() {
        try {
            logger.info("Looking for keyword search module in ingest job settings panel");
            WizardOperator wo = new WizardOperator("Add Data Source");
            while (!wo.btNext().isEnabled()) {
                new Timeout("pausing", 1000).sleep(); // give it a second till the Add Data Source dialog enabled
            }
            JTableOperator jto = new JTableOperator(wo, 0);
            int row = jto.findCellRow("Keyword Search", 2, 0);
            jto.clickOnCell(row, 1);
            logger.info("Selected keyword search module in ingest job settings panel");
            JButtonOperator jbo1 = new JButtonOperator(wo, "Global Settings");
            jbo1.pushNoBlock();
            logger.info("Pushed Global Settings button for keyword search module in ingest job settings panel");
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testConfigureSearch() {
        try {
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
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testIngest() {
        try {
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
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }

    }

    public void testExpandDataSourcesTree() {
        try {
            logger.info("Data Sources Node");
            MainWindowOperator mwo = MainWindowOperator.getDefault();
            JTreeOperator jto = new JTreeOperator(mwo, "Data Sources");
            String[] nodeNames = {"Data Sources"};
            TreePath tp = jto.findPath(nodeNames);
            expandNodes(jto, tp);
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testGenerateReportToolbar() {
        try {
            logger.info("Generate Report Toolbars");
            MainWindowOperator mwo = MainWindowOperator.getDefault();
            JButtonOperator jbo = new JButtonOperator(mwo, "Generate Report");
            jbo.pushNoBlock();
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void testGenerateReportButton() throws IOException {
        try {
            logger.info("Generate Report Button");
            setTimeout("ComponentOperator.WaitComponentTimeout", 240000);
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
            
            // Next button on the data source selection panel
            JButtonOperator dataSourceSelectionPanelNext = new JButtonOperator(reportDialogOperator, "Next");
            dataSourceSelectionPanelNext.pushNoBlock();
            new Timeout("pausing", 2000).sleep();
   
            JButtonOperator jbo1 = new JButtonOperator(reportDialogOperator, "Finish");
            jbo1.pushNoBlock();
            JDialog previewDialog = JDialogOperator.waitJDialog("Progress", false, false);
            JDialogOperator previewDialogOperator = new JDialogOperator(previewDialog);
            JLabelOperator.waitJLabel(previewDialog, "Complete", false, false);
            JButtonOperator jbo2 = new JButtonOperator(previewDialogOperator, "Close");
            jbo2.pushNoBlock();
            new Timeout("pausing", 10000).sleep();
            System.setProperty("ReportStr", datenotime);
        } catch (TimeoutExpiredException ex) {
            logger.log(Level.SEVERE, "AutopsyTestCases.testNewCaseWizard encountered timed out", ex);
            logSystemDiagnostics();
            screenshot("TimeoutScreenshot");
        }
    }

    public void screenshot(String name) {
        String outPath = getEscapedPath(System.getProperty("out_path"));
        File screenShotFile = new File(outPath + "\\" + name + ".png");
        if (!screenShotFile.exists()) {
            logger.info("Taking screenshot.");
            try {
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                BufferedImage capture = new Robot().createScreenCapture(screenRect);
                ImageIO.write(capture, "png", screenShotFile);
                new Timeout("pausing", 1000).sleep(); // give it a second to save
            } catch (IOException ex) {
                logger.log(Level.WARNING, "IOException taking screenshot.", ex);
            } catch (AWTException ex) {
                logger.log(Level.WARNING, "AWTException taking screenshot.", ex);
            }
        }
    }

    /*
     * Nightly test failed at WindowWaiter.WaitWindowTimeOut because of
     * TimeoutExpiredException. So we use this conveninent method to override
     * the default Jemmy Timeouts value.
     */
    private Timeouts setTimeout(String name, int value) {
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
            logSystemDiagnostics();
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
            logSystemDiagnostics();
        }
        
        UserPreferences.setZkServerHost(System.getProperty("zooKeeperHost"));
        UserPreferences.setZkServerPort(System.getProperty("zooKeeperPort"));
    }

    private void expandNodes(JTreeOperator jto, TreePath tp) {
        try {
            jto.expandPath(tp);
            for (TreePath t : jto.getChildPaths(tp)) {
                expandNodes(jto, t);
            }
        } catch (NoSuchPathException ne) {
            logger.log(Level.SEVERE, "Error expanding tree path", ne);
            logSystemDiagnostics();
        }
    }
    
    
    private void logSystemDiagnostics() {
        logger.log(Level.INFO, getSystemDiagnostics());
    }
    
    private static final String NEWLINE = System.lineSeparator();

    private static final int TOP_NUM = 10;

    private static Set<String> IGNORED_PROCESSES = Stream.of("_Total", "Idle", "Memory Compression").collect(Collectors.toSet());

    
    
    /**
     * @return A string of system diagnostic information.
     * 
     * NOTE: currently only works for windows.
     */
    private static String getSystemDiagnostics() {
        if (PlatformUtil.isWindowsOS()) {
            try {
                List<Map<String, String>> processPerformance = getWmicTable("wmic path Win32_PerfFormattedData_PerfProc_Process get Name,PercentProcessorTime,IOReadBytesPerSec,IOWriteBytesPerSec,WorkingSetPeak").stream()
                        .filter(obj -> !IGNORED_PROCESSES.contains(obj.get("name")))
                        .collect(Collectors.toList());

                List<Pair<String, Long>> cpuUsageProcesses = getKeyValLimited(processPerformance, "name", "percentprocessortime");
                List<Pair<String, Long>> memUsageProcesses = getKeyValLimited(processPerformance, "name", "workingsetpeak");

                List<Triple<String, Long, Long>> ioProcesses = getFilteredLimited(
                        processPerformance,
                        obj -> {
                            String key = obj.get("name");
                            if (key == null) {
                                return null;
                            }

                            try {
                                return Triple.of(key, Long.parseLong(obj.get("ioreadbytespersec")), Long.parseLong(obj.get("iowritebytespersec")));
                            } catch (NumberFormatException | NullPointerException ex) {
                                return null;
                            }

                        },
                        Comparator.comparing(pr -> -(pr.getMiddle() + pr.getRight())));

                String cpuLoad = getWmicString("wmic cpu get loadpercentage", "loadpercentage");
                String cpuCores = getWmicString("wmic cpu get numberofcores", "numberofcores");
                String freePhysicalMemory = getWmicString("wmic OS get FreeSpaceInPagingFiles", "freespaceinpagingfiles"); // in kb
                String totalPhysicalMemory = getWmicString("wmic ComputerSystem get TotalPhysicalMemory", "totalphysicalmemory"); // bytes
                String memUsage;
                try {
                    double freeMemMb = Double.parseDouble(freePhysicalMemory) / 1000;
                    double totalMemMb = Double.parseDouble(totalPhysicalMemory) / 1000 / 1000;
                    memUsage = MessageFormat.format("Free Physical Memory: {0,number,#.##}MB and total physical: {1,number,#.##}MB", freeMemMb, totalMemMb);
                } catch (NumberFormatException ex) {
                    memUsage = MessageFormat.format("Free Physical Memory: \"{0}\" and total physical: \"{1}\"", freePhysicalMemory, totalPhysicalMemory);
                }

                List<Triple<String, Long, String>> networkStatus = getFilteredLimited(
                        getWmicTable("wmic path win32_networkadapter where \"netconnectionstatus = 2 OR NOT errordescription IS NULL\" get netconnectionid, name, speed, maxspeed, errordescription"),
                        (Map<String, String> obj) -> {
                            String name = obj.get("netconnectionid");
                            if (StringUtils.isBlank(name)) {
                                name = obj.get("name");
                            }

                            if (StringUtils.isBlank(name)) {
                                return null;
                            }

                            String errorDescription = obj.get("errordescription");

                            Long speed = 0L;
                            try {
                                speed = Long.parseLong(obj.get("speed"));
                            } catch (NumberFormatException | NullPointerException ex) {
                            }

                            return Triple.of(name, speed, errorDescription);
                        },
                        (a, b) -> StringUtils.compareIgnoreCase(a.getLeft(), b.getRight()));

                List<Pair<String, Long>> diskStatus = getKeyValLimited(
                        getWmicTable("wmic path Win32_PerfFormattedData_PerfDisk_LogicalDisk get AvgDiskQueueLength,Name").stream()
                                .filter(obj -> !IGNORED_PROCESSES.contains(obj.get("name")))
                                .collect(Collectors.toList()),
                        "name",
                        "avgdiskqueuelength");

                return "SYSTEM DIAGNOSTICS:" + NEWLINE
                        + MessageFormat.format("CPU Load Percentage: {0}% with {1} cores", cpuLoad, cpuCores) + NEWLINE
                        + MessageFormat.format("Memory Usage: {0}", memUsage) + NEWLINE
                        + "Disk Usage (disk to average disk queue length): " + NEWLINE
                        + diskStatus.stream().map(pr -> pr.getKey() + ": " + pr.getValue()).collect(Collectors.joining(NEWLINE)) + NEWLINE
                        + NEWLINE
                        + "Network Status (of only connected or error): " + NEWLINE
                        + networkStatus.stream().map(obj -> {
                            String errorString = StringUtils.isBlank(obj.getRight()) ? "" : MessageFormat.format(" (error: {0})", obj.getRight());
                            return MessageFormat.format("{0}: {1,number,#.##}MB/S possible {2}", obj.getLeft(), ((double) obj.getMiddle()) / 1000 / 1000, errorString);
                        }).collect(Collectors.joining(NEWLINE)) + NEWLINE
                        + NEWLINE
                        + "CPU consuming processes: " + NEWLINE
                        + cpuUsageProcesses.stream().map(pr -> MessageFormat.format("{0}: {1}%", pr.getKey(), pr.getValue())).collect(Collectors.joining(NEWLINE)) + NEWLINE
                        + NEWLINE
                        + "Memory consuming processes (working set peak): " + NEWLINE
                        + memUsageProcesses.stream()
                                .map(
                                        pr -> MessageFormat.format(
                                                "{0}: {1,number,#.##}MB",
                                                pr.getKey(),
                                                ((double) pr.getValue()) / 1000 / 1000
                                        )
                                )
                                .collect(Collectors.joining(NEWLINE)) + NEWLINE
                        + NEWLINE
                        + "I/O consuming processes (read/write): " + NEWLINE
                        + ioProcesses.stream()
                                .map(
                                        pr -> MessageFormat.format(
                                                "{0}: {1,number,#.##}MB/{2,number,#.##}MB", pr.getLeft(),
                                                ((double) pr.getMiddle()) / 1000 / 1000,
                                                ((double) pr.getRight()) / 1000 / 1000
                                        )
                                )
                                .collect(Collectors.joining(NEWLINE)) + NEWLINE;
            } catch (Throwable ex) {
                return "SYSTEM DIAGNOSTICS:" + NEWLINE
                        + "Encountered IO exception: " + ex.getMessage() + NEWLINE;
            }

        } else {
            return "System diagnostics only implemented for windows at this time.";
        }
    }

    /**
     * Returns a pair of a string key and long number value limited to TOP_NUM of the highest number values.
     * @param objects The list of objects.
     * @param keyId The id of the key in the map.
     * @param valId The id of the value in the map.
     * @return The highest valued key value pairs.
     */
    private static List<Pair<String, Long>> getKeyValLimited(List<Map<String, String>> objects, String keyId, String valId) {
        return getFilteredLimited(
                objects,
                obj -> {
                    String key = obj.get(keyId);
                    if (key == null) {
                        return null;
                    }

                    try {
                        return Pair.of(key, Long.parseLong(obj.get(valId)));
                    } catch (NumberFormatException | NullPointerException ex) {
                        return null;
                    }
                },
                Comparator.comparing(pr -> -pr.getValue()));
    }

    /**
     * Returns a list of a given type limited to TOP_NUM of the first values.
     * @param objects The objects to sort and filter.
     * @param keyObjMapper Maps the list of map objects to the new new value.
     * @param comparator Comparator determining first values.
     * @return The list capped at TOP_NUM.
     */
    private static <T> List<T> getFilteredLimited(List<Map<String, String>> objects, Function<Map<String, String>, T> keyObjMapper, Comparator<T> comparator) {
        return objects.stream()
                .map(keyObjMapper)
                .filter(a -> a != null)
                .sorted(comparator)
                .limit(TOP_NUM)
                .collect(Collectors.toList());
    }

    /**
     * Runs the command line entry returning standard output.
     * @param cmd The command.
     * @return The standard output.
     * @throws IOException 
     */
    private static String getProcStdOut(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        String output = IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8);
        return output;
    }

    // matches key=value
    private static final Pattern EQUALS_PATTERN = Pattern.compile("^([^=]*)=(.*)$");

    /**
     * Returns a list of maps mapping the wmic header column (lower cased) to
     * the value for the row.
     *
     * @param cmd The wmic command to run.
     *
     * @return The list of rows.
     *
     * @throws IOException
     */
    private static List<Map<String, String>> getWmicTable(String cmd) throws IOException {
        String stdOut = getProcStdOut("cmd", "/c", cmd + " /format:list");

        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> curObj = new HashMap<>();
        for (String line : stdOut.split("\\r?\\n")) {
            // if line, try to parse as key=value
            if (StringUtils.isNotBlank(line)) {
                Matcher matcher = EQUALS_PATTERN.matcher(line);
                if (matcher.find()) {
                    String key = matcher.group(1).trim().toLowerCase();
                    String value = matcher.group(2).trim();
                    curObj.put(key, value);
                }
                // if no line and the object has keys, we have finished an entry, add it to the list.
            } else if (!curObj.isEmpty()) {
                rows.add(curObj);
                curObj = new HashMap<>();
            }
        }

        if (!curObj.isEmpty()) {
            rows.add(curObj);
            curObj = new HashMap<>();
        }

        return rows;
    }

    /**
     * Returns a string from a wmic query.
     * @param wmicQuery The wmic query.
     * @param key The key column to return.
     * @return The first row's value for the given key.
     * @throws IOException 
     */
    private static String getWmicString(String wmicQuery, String key) throws IOException {
        List<Map<String, String>> retVal = getWmicTable(wmicQuery);
        if (retVal != null && !retVal.isEmpty() && retVal.get(0) != null && retVal.get(0).get(key) != null) {
            return retVal.get(0).get(key);
        } else {
            return null;
        }
    }
}
