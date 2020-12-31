/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
 *
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.recentactivity.UsbDeviceIdMapper.USBInfo;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;
import static java.util.Locale.US;
import static java.util.TimeZone.getTimeZone;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.recentactivity.ShellBagParser.ShellBag;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_ACCOUNT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Extract windows registry data using regripper. Runs two versions of
 * regripper. One is the generally available set of plug-ins and the second is a
 * set that were customized for Autopsy to produce a more structured output of
 * XML so that we can parse and turn into blackboard artifacts.
 */
@NbBundle.Messages({
    "RegRipperNotFound=Autopsy RegRipper executable not found.",
    "RegRipperFullNotFound=Full version RegRipper executable not found.",
    "Progress_Message_Analyze_Registry=Analyzing Registry Files",
    "Shellbag_Artifact_Display_Name=Shell Bags",
    "Shellbag_Key_Attribute_Display_Name=Key",
    "Shellbag_Last_Write_Attribute_Display_Name=Last Write",
    "Recently_Used_Artifacts_Office_Trustrecords=Stored in TrustRecords because Office security exception was granted",
    "Recently_Used_Artifacts_ArcHistory=Recently opened by 7Zip",
    "Recently_Used_Artifacts_Applets=Recently opened according to Applets registry key",
    "Recently_Used_Artifacts_Mmc=Recently opened according to Windows Management Console MRU",
    "Recently_Used_Artifacts_Winrar=Recently opened according to WinRAR MRU",
    "Recently_Used_Artifacts_Officedocs=Recently opened according to Office MRU",
    "Recently_Used_Artifacts_Adobe=Recently opened according to Adobe MRU",
    "Recently_Used_Artifacts_Mediaplayer=Recently opened according to Media Player MRU",
    "Registry_System_Bam=Recently Executed according to Background Activity Moderator (BAM)"
})
class ExtractRegistry extends Extract {

    private static final String USERNAME_KEY = "Username"; //NON-NLS
    private static final String SID_KEY = "SID"; //NON-NLS
    private static final String RID_KEY = "RID"; //NON-NLS
    private static final String ACCOUNT_CREATED_KEY = "Account Created"; //NON-NLS
    private static final String LAST_LOGIN_KEY = "Last Login Date"; //NON-NLS
    private static final String LOGIN_COUNT_KEY = "Login Count"; //NON-NLS
    private static final String FULL_NAME_KEY = "Full Name"; //NON-NLS
    private static final String USER_COMMENT_KEY = "User Comment"; //NON-NLS
    private static final String ACCOUNT_TYPE_KEY = "Account Type"; //NON-NLS
    private static final String NAME_KEY = "Name"; //NON-NLS
    private static final String PWD_RESET_KEY = "Pwd Rest Date"; //NON-NLS
    private static final String PWD_FAILE_KEY = "Pwd Fail Date"; //NON-NLS
    private static final String INTERNET_NAME_KEY = "InternetName"; //NON-NLS
    private static final String PWD_DOES_NOT_EXPIRE_KEY = "Password does not expire"; //NON-NLS
    private static final String ACCOUNT_DISABLED_KEY = "Account Disabled"; //NON-NLS
    private static final String PWD_NOT_REQUIRED_KEY = "Password not required"; //NON-NLS
    private static final String NORMAL_ACCOUNT_KEY = "Normal user account"; //NON-NLS
    private static final String HOME_DIRECTORY_REQUIRED_KEY = "Home directory required";
    private static final String TEMPORARY_DUPLICATE_ACCOUNT = "Temporary duplicate account";
    private static final String MNS_LOGON_ACCOUNT_KEY = "MNS logon user account";
    private static final String INTERDOMAIN_TRUST_ACCOUNT_KEY = "Interdomain trust account";
    private static final String WORKSTATION_TRUST_ACCOUNT = "Workstation trust account";
    private static final String SERVER_TRUST_ACCOUNT = "Server trust account";
    private static final String ACCOUNT_AUTO_LOCKED = "Account auto locked";
    private static final String PASSWORD_HINT = "Password Hint";

    private static final String[] PASSWORD_SETTINGS_FLAGS = {PWD_DOES_NOT_EXPIRE_KEY, PWD_NOT_REQUIRED_KEY};
    private static final String[] ACCOUNT_SETTINGS_FLAGS = {ACCOUNT_AUTO_LOCKED, HOME_DIRECTORY_REQUIRED_KEY, ACCOUNT_DISABLED_KEY};
    private static final String[] ACCOUNT_TYPE_FLAGS = {NORMAL_ACCOUNT_KEY, SERVER_TRUST_ACCOUNT, WORKSTATION_TRUST_ACCOUNT, INTERDOMAIN_TRUST_ACCOUNT_KEY, MNS_LOGON_ACCOUNT_KEY, TEMPORARY_DUPLICATE_ACCOUNT};

    final private static UsbDeviceIdMapper USB_MAPPER = new UsbDeviceIdMapper();
    final private static String RIP_EXE = "rip.exe";
    final private static String RIP_PL = "rip.pl";
    final private static String RIP_PL_INCLUDE_FLAG = "-I";
    final private static int MS_IN_SEC = 1000;
    final private static String NEVER_DATE = "Never";
    final private static String SECTION_DIVIDER = "-------------------------";
    final private static Logger logger = Logger.getLogger(ExtractRegistry.class.getName());
    private final List<String> rrCmd = new ArrayList<>();
    private final List<String> rrFullCmd = new ArrayList<>();
    private final Path rrHome;  // Path to the Autopsy version of RegRipper
    private final Path rrFullHome; // Path to the full version of RegRipper
    private Content dataSource;
    private IngestJobContext context;
    private Map<String, String> userNameMap;

    private static final String SHELLBAG_ARTIFACT_NAME = "RA_SHELL_BAG"; //NON-NLS
    private static final String SHELLBAG_ATTRIBUTE_LAST_WRITE = "RA_SHELL_BAG_LAST_WRITE"; //NON-NLS
    private static final String SHELLBAG_ATTRIBUTE_KEY = "RA_SHELL_BAG_KEY"; //NON-NLS

    BlackboardArtifact.Type shellBagArtifactType = null;
    BlackboardAttribute.Type shellBagKeyAttributeType = null;
    BlackboardAttribute.Type shellBagLastWriteAttributeType = null;

    ExtractRegistry() throws IngestModuleException {
        moduleName = NbBundle.getMessage(ExtractIE.class, "ExtractRegistry.moduleName.text");

        final File rrRoot = InstalledFileLocator.getDefault().locate("rr", ExtractRegistry.class.getPackage().getName(), false); //NON-NLS
        if (rrRoot == null) {
            throw new IngestModuleException(Bundle.RegRipperNotFound());
        }

        final File rrFullRoot = InstalledFileLocator.getDefault().locate("rr-full", ExtractRegistry.class.getPackage().getName(), false); //NON-NLS
        if (rrFullRoot == null) {
            throw new IngestModuleException(Bundle.RegRipperFullNotFound());
        }

        String executableToRun = RIP_EXE;
        if (!PlatformUtil.isWindowsOS()) {
            executableToRun = RIP_PL;
        }
        rrHome = rrRoot.toPath();
        String rrPath = rrHome.resolve(executableToRun).toString();
        rrFullHome = rrFullRoot.toPath();

        if (!(new File(rrPath).exists())) {
            throw new IngestModuleException(Bundle.RegRipperNotFound());
        }
        String rrFullPath = rrFullHome.resolve(executableToRun).toString();
        if (!(new File(rrFullPath).exists())) {
            throw new IngestModuleException(Bundle.RegRipperFullNotFound());
        }
        if (PlatformUtil.isWindowsOS()) {
            rrCmd.add(rrPath);
            rrFullCmd.add(rrFullPath);
        } else {
            String perl;
            File usrBin = new File("/usr/bin/perl");
            File usrLocalBin = new File("/usr/local/bin/perl");
            if (usrBin.canExecute() && usrBin.exists() && !usrBin.isDirectory()) {
                perl = "/usr/bin/perl";
            } else if (usrLocalBin.canExecute() && usrLocalBin.exists() && !usrLocalBin.isDirectory()) {
                perl = "/usr/local/bin/perl";
            } else {
                throw new IngestModuleException("perl not found in your system");
            }
            rrCmd.add(perl);
            rrCmd.add(RIP_PL_INCLUDE_FLAG);
            rrCmd.add(rrHome.toString());
            rrCmd.add(rrPath);
            rrFullCmd.add(perl);
            rrFullCmd.add(RIP_PL_INCLUDE_FLAG);
            rrFullCmd.add(rrFullHome.toString());
            rrFullCmd.add(rrFullPath);
        }
    }

    /**
     * Search for the registry hives on the system.
     */
    private List<AbstractFile> findRegistryFiles() {
        List<AbstractFile> allRegistryFiles = new ArrayList<>();
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();

        // find the sam hives', process this first so we can map the user id's and sids for later use
        try {
            allRegistryFiles.addAll(fileManager.findFiles(dataSource, "sam", "/system32/config")); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(),
                    "ExtractRegistry.findRegFiles.errMsg.errReadingFile", "sam");
            logger.log(Level.WARNING, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
        }

        // find the user-specific ntuser-dat files
        try {
            allRegistryFiles.addAll(fileManager.findFiles(dataSource, "ntuser.dat")); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'ntuser.dat' file."); //NON-NLS
        }

        // find the user-specific ntuser-dat files
        try {
            allRegistryFiles.addAll(fileManager.findFiles(dataSource, "usrclass.dat")); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Error finding 'usrclass.dat' files."), ex); //NON-NLS
        }

        // find the system hives'
        String[] regFileNames = new String[]{"system", "software", "security"}; //NON-NLS
        for (String regFileName : regFileNames) {
            try {
                allRegistryFiles.addAll(fileManager.findFiles(dataSource, regFileName, "/system32/config")); //NON-NLS
            } catch (TskCoreException ex) {
                String msg = NbBundle.getMessage(this.getClass(),
                        "ExtractRegistry.findRegFiles.errMsg.errReadingFile", regFileName);
                logger.log(Level.WARNING, msg, ex);
                this.addErrorMessage(this.getName() + ": " + msg);
            }
        }
        return allRegistryFiles;
    }

    /**
     * Identifies registry files in the database by mtimeItem, runs regripper on
     * them, and parses the output.
     */
    private void analyzeRegistryFiles() {
        List<AbstractFile> allRegistryFiles = findRegistryFiles();

        // open the log file
        FileWriter logFile = null;
        try {
            logFile = new FileWriter(RAImageIngestModule.getRAOutputPath(currentCase, "reg") + File.separator + "regripper-info.txt"); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        for (AbstractFile regFile : allRegistryFiles) {
            String regFileName = regFile.getName();
            long regFileId = regFile.getId();
            String regFileNameLocal = RAImageIngestModule.getRATempPath(currentCase, "reg") + File.separator + regFileName;
            String outputPathBase = RAImageIngestModule.getRAOutputPath(currentCase, "reg") + File.separator + regFileName + "-regripper-" + Long.toString(regFileId); //NON-NLS
            File regFileNameLocalFile = new File(regFileNameLocal);
            try {
                ContentUtils.writeToFile(regFile, regFileNameLocalFile, context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading registry file '%s' (id=%d).",
                        regFile.getName(), regFileId), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.errMsg.errWritingTemp",
                                this.getName(), regFileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp registry file '%s' for registry file '%s' (id=%d).",
                        regFileNameLocal, regFile.getName(), regFileId), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.errMsg.errWritingTemp",
                                this.getName(), regFileName));
                continue;
            }

            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            try {
                if (logFile != null) {
                    logFile.write(Long.toString(regFileId) + "\t" + regFile.getUniquePath() + "\n");
                }
            } catch (TskCoreException | IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }

            logger.log(Level.INFO, "{0}- Now getting registry information from {1}", new Object[]{moduleName, regFileNameLocal}); //NON-NLS
            RegOutputFiles regOutputFiles = ripRegistryFile(regFileNameLocal, outputPathBase);
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            // parse the autopsy-specific output
            if (regOutputFiles.autopsyPlugins.isEmpty() == false && parseAutopsyPluginOutput(regOutputFiles.autopsyPlugins, regFile) == false) {
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.failedParsingResults",
                                this.getName(), regFileName));
            }

            // create a report for the full output
            if (!regOutputFiles.fullPlugins.isEmpty()) {
                //parse the full regripper output from SAM hive files
                if (regFileNameLocal.toLowerCase().contains("sam") && parseSamPluginOutput(regOutputFiles.fullPlugins, regFile) == false) {
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.failedParsingResults",
                                    this.getName(), regFileName));
                } else if (regFileNameLocal.toLowerCase().contains("ntuser") || regFileNameLocal.toLowerCase().contains("usrclass")) {
                    try {
                        List<ShellBag> shellbags = ShellBagParser.parseShellbagOutput(regOutputFiles.fullPlugins);
                        createShellBagArtifacts(regFile, shellbags);
                        createRecentlyUsedArtifacts(regOutputFiles.fullPlugins, regFile);
                    } catch (IOException | TskCoreException ex) {
                        logger.log(Level.WARNING, String.format("Unable to get shell bags from file %s", regOutputFiles.fullPlugins), ex);
                    }
                } else if (regFileNameLocal.toLowerCase().contains("system") && parseSystemPluginOutput(regOutputFiles.fullPlugins, regFile) == false) {
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.failedParsingResults",
                                    this.getName(), regFileName));
                }
                try {
                    Report report = currentCase.addReport(regOutputFiles.fullPlugins,
                            NbBundle.getMessage(this.getClass(), "ExtractRegistry.parentModuleName.noSpace"),
                            "RegRipper " + regFile.getUniquePath(), regFile); //NON-NLS

                    // Index the report content so that it will be available for keyword search.
                    KeywordSearchService searchService = Lookup.getDefault().lookup(KeywordSearchService.class);
                    if (null == searchService) {
                        logger.log(Level.WARNING, "Keyword search service not found. Report will not be indexed");
                    } else {
                        searchService.index(report);
                        report.close();
                    }
                } catch (TskCoreException e) {
                    this.addErrorMessage("Error adding regripper output as Autopsy report: " + e.getLocalizedMessage()); //NON-NLS
                }
            }
            // delete the hive
            regFileNameLocalFile.delete();
        }

        try {
            if (logFile != null) {
                logFile.close();
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Execute regripper on the given registry.
     *
     * @param regFilePath     Path to local copy of registry
     * @param outFilePathBase Path to location to save output file to. Base
     *                        mtimeItem that will be extended on
     */
    private RegOutputFiles ripRegistryFile(String regFilePath, String outFilePathBase) {
        String autopsyType = "";    // Type argument for rr for autopsy-specific modules
        String fullType;   // Type argument for rr for full set of modules

        RegOutputFiles regOutputFiles = new RegOutputFiles();

        if (regFilePath.toLowerCase().contains("system")) { //NON-NLS
            autopsyType = "autopsysystem"; //NON-NLS
            fullType = "system"; //NON-NLS
        } else if (regFilePath.toLowerCase().contains("software")) { //NON-NLS
            autopsyType = "autopsysoftware"; //NON-NLS
            fullType = "software"; //NON-NLS
        } else if (regFilePath.toLowerCase().contains("ntuser")) { //NON-NLS
            autopsyType = "autopsyntuser"; //NON-NLS
            fullType = "ntuser"; //NON-NLS
        } else if (regFilePath.toLowerCase().contains("sam")) { //NON-NLS
            //fullType sam output files are parsed for user information
            fullType = "sam"; //NON-NLS
        } else if (regFilePath.toLowerCase().contains("security")) { //NON-NLS
            fullType = "security"; //NON-NLS
        } else if (regFilePath.toLowerCase().contains("usrclass")) { //NON-NLS
            fullType = "usrclass"; //NON-NLS
        } else {
            return regOutputFiles;
        }

        // run the autopsy-specific set of modules
        if (!autopsyType.isEmpty()) {
            regOutputFiles.autopsyPlugins = outFilePathBase + "-autopsy.txt"; //NON-NLS
            String errFilePath = outFilePathBase + "-autopsy.err.txt"; //NON-NLS
            logger.log(Level.INFO, "Writing RegRipper results to: {0}", regOutputFiles.autopsyPlugins); //NON-NLS
            executeRegRipper(rrCmd, rrHome, regFilePath, autopsyType, regOutputFiles.autopsyPlugins, errFilePath);
        }
        if (context.dataSourceIngestIsCancelled()) {
            return regOutputFiles;
        }

        // run the full set of rr modules
        if (!fullType.isEmpty()) {
            regOutputFiles.fullPlugins = outFilePathBase + "-full.txt"; //NON-NLS
            String errFilePath = outFilePathBase + "-full.err.txt"; //NON-NLS
            logger.log(Level.INFO, "Writing Full RegRipper results to: {0}", regOutputFiles.fullPlugins); //NON-NLS
            executeRegRipper(rrFullCmd, rrFullHome, regFilePath, fullType, regOutputFiles.fullPlugins, errFilePath);
            try {
                scanErrorLogs(errFilePath);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Unable to run RegRipper on %s", regFilePath), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getName(), regFilePath));
            }
        }
        return regOutputFiles;
    }

    private void scanErrorLogs(String errFilePath) throws IOException {
        File regfile = new File(errFilePath);
        try (BufferedReader reader = new BufferedReader(new FileReader(regfile))) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (line.toLowerCase().contains("error") || line.toLowerCase().contains("@inc")) {
                    logger.log(Level.WARNING, "Regripper file {0} contains errors from run", errFilePath); //NON-NLS

                }
                line = reader.readLine();
            }
        }
    }

    private void executeRegRipper(List<String> regRipperPath, Path regRipperHomeDir, String hiveFilePath, String hiveFileType, String outputFile, String errFile) {
        try {
            List<String> commandLine = new ArrayList<>();
            for (String cmd : regRipperPath) {
                commandLine.add(cmd);
            }
            commandLine.add("-r"); //NON-NLS
            commandLine.add(hiveFilePath);
            commandLine.add("-f"); //NON-NLS
            commandLine.add(hiveFileType);

            ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
            processBuilder.directory(regRipperHomeDir.toFile()); // RegRipper 2.8 has to be run from its own directory
            processBuilder.redirectOutput(new File(outputFile));
            processBuilder.redirectError(new File(errFile));
            ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context, true));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error running RegRipper on %s", hiveFilePath), ex); //NON-NLS
            this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getName(), hiveFilePath));
        }
    }

    // @@@ VERIFY that we are doing the right thing when we parse multiple NTUSER.DAT
    /**
     *
     * @param regFilePath Path to the output file produced by RegRipper.
     * @param regFile     File object for registry that we are parsing (to make
     *                    blackboard artifacts with)
     *
     * @return
     */
    private boolean parseAutopsyPluginOutput(String regFilePath, AbstractFile regFile) {
        FileInputStream fstream = null;
        List<BlackboardArtifact> newArtifacts = new ArrayList<>();
        try {
            // Read the file in and create a Document and elements
            File regfile = new File(regFilePath);
            fstream = new FileInputStream(regfile);
            String regString = new Scanner(fstream, "UTF-8").useDelimiter("\\Z").next(); //NON-NLS
            String startdoc = "<?xml version=\"1.0\"?><document>"; //NON-NLS
            String result = regString.replaceAll("----------------------------------------", "");
            result = result.replaceAll("\\n", ""); //NON-NLS
            result = result.replaceAll("\\r", ""); //NON-NLS
            result = result.replaceAll("'", "&apos;"); //NON-NLS
            result = result.replaceAll("&", "&amp;"); //NON-NLS
            result = result.replace('\0', ' '); // NON-NLS
            String enddoc = "</document>"; //NON-NLS
            String stringdoc = startdoc + result + enddoc;
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(stringdoc)));

            // cycle through the elements in the doc
            Element oroot = doc.getDocumentElement();
            NodeList children = oroot.getChildNodes();
            int len = children.getLength();
            for (int i = 0; i < len; i++) {

                if (context.dataSourceIngestIsCancelled()) {
                    return false;
                }

                Element tempnode = (Element) children.item(i);

                String dataType = tempnode.getNodeName();
                NodeList timenodes = tempnode.getElementsByTagName("mtime"); //NON-NLS
                Long mtime = null;
                if (timenodes.getLength() > 0) {
                    Element timenode = (Element) timenodes.item(0);
                    String etime = timenode.getTextContent();
                    //sometimes etime will be an empty string and therefore can not be parsed into a date
                    if (etime != null && !etime.isEmpty()) {
                        try {
                            mtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(etime).getTime();
                            String Tempdate = mtime.toString();
                            mtime = Long.valueOf(Tempdate) / MS_IN_SEC;
                        } catch (ParseException ex) {
                            logger.log(Level.WARNING, "Failed to parse epoch time when parsing the registry.", ex); //NON-NLS
                        }
                    }
                }

                NodeList artroots = tempnode.getElementsByTagName("artifacts"); //NON-NLS
                if (artroots.getLength() == 0) {
                    // If there isn't an artifact node, skip this entry
                    continue;
                }

                Element artroot = (Element) artroots.item(0);
                NodeList myartlist = artroot.getChildNodes();
                String parentModuleName = RecentActivityExtracterModuleFactory.getModuleName();

                // If all artifact nodes should really go under one Blackboard artifact, need to process it differently
                switch (dataType) {
                    case "WinVersion": //NON-NLS
                        String version = "";
                        String systemRoot = "";
                        String productId = "";
                        String regOwner = "";
                        String regOrg = "";
                        Long installtime = null;
                        for (int j = 0; j < myartlist.getLength(); j++) {
                            Node artchild = myartlist.item(j);
                            // If it has attributes, then it is an Element (based off API)
                            if (artchild.hasAttributes()) {
                                Element artnode = (Element) artchild;

                                String value = artnode.getTextContent();
                                if (value != null) {
                                    value = value.trim();
                                }
                                String name = artnode.getAttribute("name"); //NON-NLS
                                if (name == null) {
                                    continue;
                                }
                                switch (name) {
                                    case "ProductName": // NON-NLS
                                        version = value;
                                        break;
                                    case "CSDVersion": // NON-NLS
                                        // This is dependant on the fact that ProductName shows up first in the module output
                                        version = version + " " + value;
                                        break;
                                    case "SystemRoot": //NON-NLS
                                        systemRoot = value;
                                        break;
                                    case "ProductId": //NON-NLS
                                        productId = value;
                                        break;
                                    case "RegisteredOwner": //NON-NLS
                                        regOwner = value;
                                        break;
                                    case "RegisteredOrganization": //NON-NLS
                                        regOrg = value;
                                        break;
                                    case "InstallDate": //NON-NLS
                                        if (value != null && !value.isEmpty()) {
                                            try {
                                                installtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(value).getTime();
                                                String Tempdate = installtime.toString();
                                                installtime = Long.valueOf(Tempdate) / MS_IN_SEC;
                                            } catch (ParseException e) {
                                                logger.log(Level.WARNING, "RegRipper::Conversion on DateTime -> ", e); //NON-NLS
                                            }
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        try {
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, parentModuleName, version));
                            if (installtime != null) {
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, parentModuleName, installtime));
                            }
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH, parentModuleName, systemRoot));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PRODUCT_ID, parentModuleName, productId));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_OWNER, parentModuleName, regOwner));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ORGANIZATION, parentModuleName, regOrg));

                            // Check if there is already an OS_INFO artifact for this file, and add to that if possible.
                            ArrayList<BlackboardArtifact> results = tskCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_OS_INFO, regFile.getId());
                            if (results.isEmpty()) {
                                BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_OS_INFO);
                                bbart.addAttributes(bbattributes);

                                newArtifacts.add(bbart);
                            } else {
                                results.get(0).addAttributes(bbattributes);
                            }

                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard."); //NON-NLS
                        }
                        break;
                    case "Profiler": // NON-NLS
                        String os = "";
                        String procArch = "";
                        String tempDir = "";
                        for (int j = 0; j < myartlist.getLength(); j++) {
                            Node artchild = myartlist.item(j);
                            // If it has attributes, then it is an Element (based off API)
                            if (artchild.hasAttributes()) {
                                Element artnode = (Element) artchild;

                                String value = artnode.getTextContent().trim();
                                String name = artnode.getAttribute("name"); //NON-NLS
                                switch (name) {
                                    case "OS": // NON-NLS
                                        os = value;
                                        break;
                                    case "PROCESSOR_ARCHITECTURE": // NON-NLS
                                        procArch = value;
                                        break;
                                    case "PROCESSOR_IDENTIFIER": //NON-NLS
                                        break;
                                    case "TEMP": //NON-NLS
                                        tempDir = value;
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        try {
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VERSION, parentModuleName, os));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROCESSOR_ARCHITECTURE, parentModuleName, procArch));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEMP_DIR, parentModuleName, tempDir));

                            // Check if there is already an OS_INFO artifact for this file and add to that if possible
                            ArrayList<BlackboardArtifact> results = tskCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_OS_INFO, regFile.getId());
                            if (results.isEmpty()) {
                                BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_OS_INFO);
                                bbart.addAttributes(bbattributes);

                                newArtifacts.add(bbart);
                            } else {
                                results.get(0).addAttributes(bbattributes);
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error adding os info artifact to blackboard."); //NON-NLS
                        }
                        break;
                    case "CompName": // NON-NLS
                        String compName = "";
                        String domain = "";
                        for (int j = 0; j < myartlist.getLength(); j++) {
                            Node artchild = myartlist.item(j);
                            // If it has attributes, then it is an Element (based off API)
                            if (artchild.hasAttributes()) {
                                Element artnode = (Element) artchild;

                                String value = artnode.getTextContent().trim();
                                String name = artnode.getAttribute("name"); //NON-NLS

                                if (name.equals("ComputerName")) { // NON-NLS
                                    compName = value;
                                } else if (name.equals("Domain")) { // NON-NLS
                                    domain = value;
                                }
                            }
                        }
                        try {
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, parentModuleName, compName));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, parentModuleName, domain));

                            // Check if there is already an OS_INFO artifact for this file and add to that if possible
                            ArrayList<BlackboardArtifact> results = tskCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_OS_INFO, regFile.getId());
                            if (results.isEmpty()) {
                                BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_OS_INFO);
                                bbart.addAttributes(bbattributes);

                                newArtifacts.add(bbart);
                            } else {
                                results.get(0).addAttributes(bbattributes);
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error adding os info artifact to blackboard.", ex); //NON-NLS
                        }
                        break;
                    default:
                        for (int j = 0; j < myartlist.getLength(); j++) {
                            Node artchild = myartlist.item(j);
                            // If it has attributes, then it is an Element (based off API)
                            if (artchild.hasAttributes()) {
                                Element artnode = (Element) artchild;

                                String value = artnode.getTextContent().trim();
                                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

                                switch (dataType) {
                                    case "recentdocs": //NON-NLS
                                        // BlackboardArtifact bbart = tskCase.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                                        // bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", dataType, mtime));
                                        // bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", dataType, mtimeItem));
                                        // bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", dataType, value));
                                        // bbart.addAttributes(bbattributes);
                                        // @@@ BC: Why are we ignoring this...
                                        break;
                                    case "usb": //NON-NLS
                                        try {
                                        Long usbMtime = Long.parseLong(artnode.getAttribute("mtime")); //NON-NLS
                                        usbMtime = Long.valueOf(usbMtime.toString());

                                        BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED);
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, parentModuleName, usbMtime));
                                        String dev = artnode.getAttribute("dev"); //NON-NLS
                                        String make = "";
                                        String model = dev;
                                        if (dev.toLowerCase().contains("vid")) { //NON-NLS
                                            USBInfo info = USB_MAPPER.parseAndLookup(dev);
                                            if (info.getVendor() != null) {
                                                make = info.getVendor();
                                            }
                                            if (info.getProduct() != null) {
                                                model = info.getProduct();
                                            }
                                        }
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE, parentModuleName, make));
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL, parentModuleName, model));
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID, parentModuleName, value));
                                        bbart.addAttributes(bbattributes);

                                        newArtifacts.add(bbart);
                                    } catch (TskCoreException ex) {
                                        logger.log(Level.SEVERE, "Error adding device attached artifact to blackboard.", ex); //NON-NLS
                                    }
                                    break;
                                    case "uninstall": //NON-NLS
                                        Long itemMtime = null;
                                        try {
                                            String mTimeAttr = artnode.getAttribute("mtime");
                                            if (mTimeAttr != null && !mTimeAttr.isEmpty()) {
                                                itemMtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(mTimeAttr).getTime(); //NON-NLS
                                                itemMtime /= MS_IN_SEC;
                                            }
                                        } catch (ParseException ex) {
                                            logger.log(Level.SEVERE, "Failed to parse epoch time for installed program artifact.", ex); //NON-NLS
                                        }

                                        try {
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, parentModuleName, value));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, parentModuleName, itemMtime));
                                            BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
                                            bbart.addAttributes(bbattributes);

                                            newArtifacts.add(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard.", ex); //NON-NLS
                                        }
                                        break;
                                    case "office": //NON-NLS
                                        String officeName = artnode.getAttribute("name"); //NON-NLS

                                        try {
                                            BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                                            // @@@ BC: Consider removing this after some more testing. It looks like an Mtime associated with the root key and not the individual item
                                            if (mtime != null) {
                                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, parentModuleName, mtime));
                                            }
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, parentModuleName, officeName));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE, parentModuleName, value));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, parentModuleName, artnode.getNodeName()));
                                            bbart.addAttributes(bbattributes);

                                            newArtifacts.add(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding recent object artifact to blackboard.", ex); //NON-NLS
                                        }
                                        break;

                                    case "ProcessorArchitecture": //NON-NLS
                                        // Architecture is now included under Profiler
                                        //try {
                                        //    String processorArchitecture = value;
                                        //    if (processorArchitecture.equals("AMD64"))
                                        //        processorArchitecture = "x86-64";

                                        //    BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_OS_INFO);
                                        //    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROCESSOR_ARCHITECTURE.getTypeID(), parentModuleName, processorArchitecture));
                                        //    bbart.addAttributes(bbattributes);
                                        //} catch (TskCoreException ex) {
                                        //    logger.log(Level.SEVERE, "Error adding os info artifact to blackboard."); //NON-NLS
                                        //}
                                        break;

                                    case "ProfileList": //NON-NLS
                                        try {
                                        String homeDir = value;
                                        String sid = artnode.getAttribute("sid"); //NON-NLS
                                        String username = artnode.getAttribute("username"); //NON-NLS
                                        BlackboardArtifact bbart = null;
                                        try {
                                            //check if any of the existing artifacts match this username
                                            ArrayList<BlackboardArtifact> existingArtifacts = currentCase.getSleuthkitCase().getBlackboardArtifacts(ARTIFACT_TYPE.TSK_OS_ACCOUNT);
                                            for (BlackboardArtifact artifact : existingArtifacts) {
                                                if (artifact.getDataSource().getId() == regFile.getDataSourceObjectId()) {
                                                    BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_USER_ID));
                                                    if (attribute != null && attribute.getValueString().equals(sid)) {
                                                        bbart = artifact;
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error getting existing os account artifact", ex);
                                        }
                                        if (bbart == null) {
                                            //create new artifact
                                            bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_OS_ACCOUNT);
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME,
                                                    parentModuleName, username));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_ID,
                                                    parentModuleName, sid));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                                                    parentModuleName, homeDir));
                                        } else {
                                            //add attributes to existing artifact
                                            BlackboardAttribute bbattr = bbart.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_USER_NAME));

                                            if (bbattr == null) {
                                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME,
                                                        parentModuleName, username));
                                            }
                                            bbattr = bbart.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH));
                                            if (bbattr == null) {
                                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                                                        parentModuleName, homeDir));
                                            }
                                        }
                                        bbart.addAttributes(bbattributes);
                                        newArtifacts.add(bbart);
                                    } catch (TskCoreException ex) {
                                        logger.log(Level.SEVERE, "Error adding account artifact to blackboard.", ex); //NON-NLS
                                    }
                                    break;

                                    case "NtuserNetwork": // NON-NLS
                                        try {
                                        String localPath = artnode.getAttribute("localPath"); //NON-NLS
                                        String remoteName = value;
                                        BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_REMOTE_DRIVE);
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LOCAL_PATH,
                                                parentModuleName, localPath));
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REMOTE_PATH,
                                                parentModuleName, remoteName));
                                        bbart.addAttributes(bbattributes);
                                        newArtifacts.add(bbart);
                                    } catch (TskCoreException ex) {
                                        logger.log(Level.SEVERE, "Error adding network artifact to blackboard.", ex); //NON-NLS
                                    }
                                    break;
                                    case "SSID": // NON-NLS
                                        String adapter = artnode.getAttribute("adapter"); //NON-NLS
                                        try {
                                            Long lastWriteTime = Long.parseLong(artnode.getAttribute("writeTime")); //NON-NLS
                                            lastWriteTime = Long.valueOf(lastWriteTime.toString());
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SSID, parentModuleName, value));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, parentModuleName, lastWriteTime));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID, parentModuleName, adapter));
                                            BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_WIFI_NETWORK);
                                            bbart.addAttributes(bbattributes);
                                            newArtifacts.add(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding SSID artifact to blackboard.", ex); //NON-NLS
                                        }
                                        break;
                                    case "shellfolders": // NON-NLS
                                        // The User Shell Folders subkey stores the paths to Windows Explorer folders for the current user of the computer
                                        // (https://technet.microsoft.com/en-us/library/Cc962613.aspx).
                                        // No useful information. Skip.
                                        break;

                                    default:
                                        logger.log(Level.SEVERE, "Unrecognized node name: {0}", dataType); //NON-NLS
                                        break;
                                }
                            }
                        }
                        break;
                }
            } // for
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, String.format("Error finding the registry file: %s", regFilePath), ex); //NON-NLS
        } catch (SAXException ex) {
            logger.log(Level.WARNING, String.format("Error parsing the registry XML: %s", regFilePath), ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Error building the document parser: %s", regFilePath), ex); //NON-NLS
        } catch (ParserConfigurationException ex) {
            logger.log(Level.WARNING, String.format("Error configuring the registry parser: %s", regFilePath), ex); //NON-NLS
        } finally {
            try {
                if (fstream != null) {
                    fstream.close();
                }
            } catch (IOException ex) {
            }

            postArtifacts(newArtifacts);
        }
        return false;
    }

    private boolean parseSystemPluginOutput(String regfilePath, AbstractFile regAbstractFile) {
        File regfile = new File(regfilePath);
        try (BufferedReader reader = new BufferedReader(new FileReader(regfile))) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();

                if (line.toLowerCase().matches("^bam v.*")) {
                    parseBamKey(regAbstractFile, reader, Bundle.Registry_System_Bam());
                } else if (line.toLowerCase().matches("^bthport v..*")) {
                    parseBlueToothDevices(regAbstractFile, reader);
                }
                line = reader.readLine();
            }
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, "Error finding the registry file.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error reading the system hive: {0}", ex); //NON-NLS
        }

        return false;

    }

    /**
     * Create recently used artifacts to parse the regripper plugin output, this
     * format is used in several diffent plugins
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parseBlueToothDevices(AbstractFile regFile, BufferedReader reader) throws FileNotFoundException, IOException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String line = reader.readLine();
        while ((line != null) && (!line.contains(SECTION_DIVIDER))) {
            line = reader.readLine();

            if (line != null) {
                line = line.trim();
            }

            if ((line != null) && (line.toLowerCase().contains("device unique id"))) {
                // Columns are seperated by colons :
                // Data : Values
                // Record is 4 lines in length (Device Unique Id, Name, Last Seen,  LastConnected
                while (line != null && !line.contains(SECTION_DIVIDER) && !line.isEmpty() && !line.toLowerCase().contains("radio support not found")) {
                    Collection<BlackboardAttribute> attributes = new ArrayList<>();
                    addBlueToothAttribute(line, attributes, TSK_DEVICE_ID);
                    line = reader.readLine();
                    // Name may not exist, check for it to make sure.
                    if ((line != null) && (line.toLowerCase().contains("name"))) {
                        addBlueToothAttribute(line, attributes, TSK_NAME);
                        line = reader.readLine();
                    }
                    addBlueToothAttribute(line, attributes, TSK_DATETIME);
                    line = reader.readLine();
                    addBlueToothAttribute(line, attributes, TSK_DATETIME_ACCESSED);
                    BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING, regFile, attributes);
                    if (bba != null) {
                        bbartifacts.add(bba);
                    }
                    // Read blank line between records then next read line is start of next block
                    reader.readLine();
                    line = reader.readLine();
                }

                if (line != null) {
                    line = line.trim();
                }
            }
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    private void addBlueToothAttribute(String line, Collection<BlackboardAttribute> attributes, ATTRIBUTE_TYPE attributeType) {
        if (line == null) {
            return;
        }

        String tokens[] = line.split(": ");
        if (tokens.length > 1 && !tokens[1].isEmpty()) {
            String tokenString = tokens[1];
            if (attributeType.getDisplayName().toLowerCase().contains("date")) {
                String dateString = tokenString.toLowerCase().replace(" z", "");
                // date format for plugin Tue Jun 23 10:27:54 2020 Z
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", US);
                Long dateLong = Long.valueOf(0);
                try {
                    Date newDate = dateFormat.parse(dateString);
                    dateLong = newDate.getTime() / 1000;
                } catch (ParseException ex) {
                    // catching error and displaying date that could not be parsed
                    // we set the timestamp to 0 and continue on processing
                    logger.log(Level.WARNING, String.format("Failed to parse date/time %s for Bluetooth Last Seen attribute.", dateString), ex); //NON-NLS
                }
                attributes.add(new BlackboardAttribute(attributeType, getName(), dateLong));
            } else {
                attributes.add(new BlackboardAttribute(attributeType, getName(), tokenString));
            }
        }
    }

    /**
     * Parse the output of the SAM regripper plugin to get additional Account
     * information
     *
     * @param regFilePath     the path to the registry file being parsed
     * @param regAbstractFile the file to associate newly created artifacts with
     *
     * @return true if successful, false if parsing failed at some point
     */
    private boolean parseSamPluginOutput(String regFilePath, AbstractFile regAbstractFile) {
        File regfile = new File(regFilePath);
        List<BlackboardArtifact> newArtifacts = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(regfile))) {
            // Read the file in and create a Document and elements
            String userInfoSection = "User Information";
            String previousLine = null;
            String line = bufferedReader.readLine();
            Set<Map<String, String>> userSet = new HashSet<>();
            Map<String, List<String>> groupMap = null;
            while (line != null) {
                if (line.contains(SECTION_DIVIDER) && previousLine != null && previousLine.contains(userInfoSection)) {
                    readUsers(bufferedReader, userSet);
                }

                if (line.contains(SECTION_DIVIDER) && previousLine != null && previousLine.contains("Group Membership Information")) {
                    groupMap = readGroups(bufferedReader);
                }

                previousLine = line;
                line = bufferedReader.readLine();
            }
            Map<String, Map<String, String>> userInfoMap = new HashMap<>();
            //load all the user info which was read into a map
            for (Map<String, String> userInfo : userSet) {
                userInfoMap.put(userInfo.get(SID_KEY), userInfo);
            }
            //get all existing OS account artifacts
            List<BlackboardArtifact> existingOsAccounts = tskCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_OS_ACCOUNT);
            for (BlackboardArtifact osAccount : existingOsAccounts) {
                //if the OS Account artifact was from the same data source check the user id
                if (osAccount.getDataSource().getId() == regAbstractFile.getDataSourceObjectId()) {
                    BlackboardAttribute existingUserId = osAccount.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_USER_ID));
                    if (existingUserId != null) {
                        String userID = existingUserId.getValueString().trim();
                        Map<String, String> userInfo = userInfoMap.remove(userID);
                        //if the existing user id matches a user id which we parsed information for check if that information exists and if it doesn't add it
                        if (userInfo != null) {
                            osAccount.addAttributes(getAttributesForAccount(userInfo, groupMap.get(userID), true, regAbstractFile));
                        }
                    }
                }
            }

            //add remaining userinfos as accounts;
            for (Map<String, String> userInfo : userInfoMap.values()) {
                BlackboardArtifact bbart = regAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_OS_ACCOUNT);
                bbart.addAttributes(getAttributesForAccount(userInfo, groupMap.get(userInfo.get(SID_KEY)), false, regAbstractFile));
                // index the artifact for keyword search
                newArtifacts.add(bbart);
            }
            // Get a mapping of user sids to user names and save globally so it can be used for other areas
            // of the registry, ie: BAM key
            try {
                userNameMap = makeUserNameMap(dataSource);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to create OS Account user name map", ex);
                // This is not the end of the world we will just continue without 
                // user names
                userNameMap = new HashMap<>();
            }
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, "Error finding the registry file.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error building the document parser: {0}", ex); //NON-NLS
        } catch (ParseException ex) {
            logger.log(Level.WARNING, "Error parsing the the date from the registry file", ex); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error updating TSK_OS_ACCOUNT artifacts to include newly parsed data.", ex); //NON-NLS
        } finally {
            postArtifacts(newArtifacts);
        }
        return false;
    }

    /**
     * Creates the attribute list for the given user information and group list.
     *
     * @param userInfo     Map of key\value pairs of user information
     * @param groupList    List of the groups that user belongs
     * @param existingUser
     *
     * @return List
     *
     * @throws ParseException
     */
    Collection<BlackboardAttribute> getAttributesForAccount(Map<String, String> userInfo, List<String> groupList, boolean existingUser, AbstractFile regAbstractFile) throws ParseException {
        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

        SimpleDateFormat regRipperTimeFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy 'Z'");
        regRipperTimeFormat.setTimeZone(getTimeZone("GMT"));

        if (!existingUser) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_ID,
                    getRAModuleName(), userInfo.get(SID_KEY)));

            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME,
                    this.moduleName, userInfo.get(USERNAME_KEY)));
        }

        String value = userInfo.get(ACCOUNT_CREATED_KEY);
        if (value != null && !value.isEmpty() && !value.equals(NEVER_DATE)) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                    getRAModuleName(), regRipperTimeFormat.parse(value).getTime() / MS_IN_SEC));
        }

        value = userInfo.get(LAST_LOGIN_KEY);
        if (value != null && !value.isEmpty() && !value.equals(NEVER_DATE)) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                    getRAModuleName(), regRipperTimeFormat.parse(value).getTime() / MS_IN_SEC));
        }

        value = userInfo.get(LOGIN_COUNT_KEY);
        if (value != null && !value.isEmpty()) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT,
                    getRAModuleName(), Integer.parseInt(value)));
        }

        value = userInfo.get(ACCOUNT_TYPE_KEY);
        if (value != null && !value.isEmpty()) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE,
                    getRAModuleName(), value));
        }

        value = userInfo.get(USER_COMMENT_KEY);
        if (value != null && !value.isEmpty()) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DESCRIPTION,
                    getRAModuleName(), value));
        }

        value = userInfo.get(NAME_KEY);
        if (value != null && !value.isEmpty()) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME,
                    getRAModuleName(), value));
        }

        value = userInfo.get(INTERNET_NAME_KEY);
        if (value != null && !value.isEmpty()) {
            try {
                // Create an account for this email, if it doesn't already exist.
                Case.getCurrentCaseThrows()
                        .getSleuthkitCase()
                        .getCommunicationsManager()
                        .createAccountFileInstance(Account.Type.EMAIL,
                                value, getRAModuleName(), regAbstractFile);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE,
                        String.format("Error adding email account with value "
                                + "%s, to the case database for file %s [objId=%d]",
                                value, regAbstractFile.getName(), regAbstractFile.getId()), ex);
            }

            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL,
                    getRAModuleName(), value));
        }

        value = userInfo.get(FULL_NAME_KEY);
        if (value != null && !value.isEmpty()) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DISPLAY_NAME,
                    getRAModuleName(), value));
        }

        value = userInfo.get(PWD_RESET_KEY);
        if (value != null && !value.isEmpty() && !value.equals(NEVER_DATE)) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_PASSWORD_RESET,
                    getRAModuleName(), regRipperTimeFormat.parse(value).getTime() / MS_IN_SEC));
        }

        value = userInfo.get(PASSWORD_HINT);
        if (value != null && !value.isEmpty()) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PASSWORD_HINT,
                    getRAModuleName(), value));
        }

        value = userInfo.get(PWD_FAILE_KEY);
        if (value != null && !value.isEmpty() && !value.equals(NEVER_DATE)) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_PASSWORD_FAIL,
                    getRAModuleName(), regRipperTimeFormat.parse(value).getTime() / MS_IN_SEC));
        }

        String settingString = "";
        for (String setting : PASSWORD_SETTINGS_FLAGS) {
            if (userInfo.containsKey(setting)) {
                settingString += setting + ", ";
            }
        }

        if (!settingString.isEmpty()) {
            settingString = settingString.substring(0, settingString.length() - 2);
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PASSWORD_SETTINGS,
                    getRAModuleName(), settingString));
        }

        settingString = "";
        for (String setting : ACCOUNT_SETTINGS_FLAGS) {
            if (userInfo.containsKey(setting)) {
                settingString += setting + ", ";
            }
        }

        if (!settingString.isEmpty()) {
            settingString = settingString.substring(0, settingString.length() - 2);
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ACCOUNT_SETTINGS,
                    getRAModuleName(), settingString));
        }

        settingString = "";
        for (String setting : ACCOUNT_TYPE_FLAGS) {
            if (userInfo.containsKey(setting)) {
                settingString += setting + ", ";
            }
        }

        if (!settingString.isEmpty()) {
            settingString = settingString.substring(0, settingString.length() - 2);
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_FLAG,
                    getRAModuleName(), settingString));
        }

        if (groupList != null && groupList.isEmpty()) {
            String groups = "";
            for (String group : groupList) {
                groups += group + ", ";
            }

            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GROUPS,
                    getRAModuleName(), groups.substring(0, groups.length() - 2)));
        }

        return bbattributes;
    }

    /**
     * Read the User Information section of the SAM regripper plugin's output
     * and collect user account information from the file.
     *
     * @param bufferedReader a buffered reader for the file which contains the
     *                       user information
     * @param users          the set to add UserInfo objects representing the
     *                       users found to
     *
     * @throws IOException
     */
    private void readUsers(BufferedReader bufferedReader, Set<Map<String, String>> users) throws IOException {
        String line = bufferedReader.readLine();
        //read until end of file or next section divider
        String userName = "";
        String user_rid = "";
        while (line != null && !line.contains(SECTION_DIVIDER)) {
            //when a user name field exists read the name and id number
            if (line.contains(USERNAME_KEY)) {
                String regx = USERNAME_KEY + "\\s*?:";
                String userNameAndIdString = line.replaceAll(regx, "");
                userName = userNameAndIdString.substring(0, userNameAndIdString.lastIndexOf('[')).trim();
                user_rid = userNameAndIdString.substring(userNameAndIdString.lastIndexOf('['), userNameAndIdString.lastIndexOf(']'));
            } else if (line.contains(SID_KEY) && !userName.isEmpty()) {
                Map.Entry<String, String> entry = getSAMKeyValue(line);

                HashMap<String, String> userInfo = new HashMap<>();
                userInfo.put(USERNAME_KEY, userName);
                userInfo.put(RID_KEY, user_rid);
                userInfo.put(entry.getKey(), entry.getValue());

                //continue reading this users information until end of file or a blank line between users
                line = bufferedReader.readLine();
                while (line != null && !line.isEmpty()) {
                    entry = getSAMKeyValue(line);
                    if (entry != null) {
                        userInfo.put(entry.getKey(), entry.getValue());
                    }
                    line = bufferedReader.readLine();
                }
                users.add(userInfo);

                userName = "";
            }
            line = bufferedReader.readLine();
        }
    }

    /**
     * Create recently used artifacts from NTUSER regripper files
     *
     * @param regFileName name of the regripper output file
     *
     * @param regFile     registry file the artifact is associated with
     *
     * @throws FileNotFound and IOException
     */
    private void createRecentlyUsedArtifacts(String regFileName, AbstractFile regFile) throws FileNotFoundException, IOException {
        File regfile = new File(regFileName);
        try (BufferedReader reader = new BufferedReader(new FileReader(regfile))) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();

                if (line.matches("^adoberdr v.*")) {
                    parseAdobeMRUList(regFile, reader, Bundle.Recently_Used_Artifacts_Adobe());
                } else if (line.matches("^mpmru v.*")) {
                    parseMediaPlayerMRUList(regFile, reader, Bundle.Recently_Used_Artifacts_Mediaplayer());
                } else if (line.matches("^trustrecords v.*")) {
                    parseOfficeTrustRecords(regFile, reader, Bundle.Recently_Used_Artifacts_Office_Trustrecords());
                } else if (line.matches("^ArcHistory:")) {
                    parse7ZipMRU(regFile, reader, Bundle.Recently_Used_Artifacts_ArcHistory());
                } else if (line.matches("^applets v.*")) {
                    parseGenericMRUList(regFile, reader, Bundle.Recently_Used_Artifacts_Applets());
                } else if (line.matches("^mmc v.*")) {
                    parseGenericMRUList(regFile, reader, Bundle.Recently_Used_Artifacts_Mmc());
                } else if (line.matches("^winrar v.*")) {
                    parseWinRARMRUList(regFile, reader, Bundle.Recently_Used_Artifacts_Winrar());
                } else if (line.matches("^officedocs2010 v.*")) {
                    parseOfficeDocs2010MRUList(regFile, reader, Bundle.Recently_Used_Artifacts_Officedocs());
                }
                line = reader.readLine();
            }
        }
    }

    /**
     * Create artifacts from BAM Regripper Plugin records
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parseBamKey(AbstractFile regFile, BufferedReader reader, String comment) throws FileNotFoundException, IOException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String line = reader.readLine();
        // Read thru first bam output to get to second bam output which is the same but delimited
        while (!line.contains(SECTION_DIVIDER)) {
            line = reader.readLine();
            line = line.trim();
        }
        line = reader.readLine();
        line = line.trim();
        while (!line.contains(SECTION_DIVIDER)) {
            // Split the line into it parts based on delimiter of "|"
            // 1570493613|BAM|||\Device\HarddiskVolume3\Program Files\TechSmith\Snagit 2018\Snagit32.exe (S-1-5-21-3042408413-2583535980-1301764466-1001)
            String tokens[] = line.split("\\|");
            Long progRunDateTime = Long.valueOf(tokens[0]);
            // Split on " (S-" as this signifies a User SID, if S- not used then may have issues becuase of (x86) in path is valid.
            // We can add the S- back to the string that we split on since S- is a valid beginning of a User SID
            String fileNameSid[] = tokens[4].split("\\s+\\(S-");
            String userSid = "S-" + fileNameSid[1].substring(0, fileNameSid[1].length() - 1);
            String userName = userNameMap.get(userSid);
            if (userName == null) {
                userName = userSid;
            }
            String fileName = fileNameSid[0];
            if (fileName.startsWith("\\Device\\HarddiskVolume")) {
                // Start at point past the 2nd slash
                int fileNameStart = fileName.indexOf('\\', 16);
                fileName = fileName.substring(fileNameStart, fileName.length());

            }
            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, getName(), fileName));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME, getName(), userName));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, getName(), progRunDateTime));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COMMENT, getName(), comment));
            BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_PROG_RUN, regFile, attributes);
            if (bba != null) {
                bbartifacts.add(bba);
                bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                if (bba != null) {
                    bbartifacts.add(bba);
                }
            }
            line = reader.readLine();
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Create recently used artifacts from adobemru Regripper Plugin records
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parseAdobeMRUList(AbstractFile regFile, BufferedReader reader, String comment) throws FileNotFoundException, IOException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String line = reader.readLine();
        SimpleDateFormat adobePluginDateFormat = new SimpleDateFormat("yyyyMMddHHmmssZ", US);
        Long adobeUsedTime = Long.valueOf(0);
        while (!line.contains(SECTION_DIVIDER)) {
            line = reader.readLine();
            line = line.trim();
            if (line.matches("^Key name,file name,sDate,uFileSize,uPageCount")) {
                line = reader.readLine();
                // Columns are
                // Key name, file name, sDate, uFileSize, uPageCount
                while (!line.contains(SECTION_DIVIDER)) {
                    // Split csv line, handles double quotes around individual file names
                    // since file names can contain commas
                    String tokens[] = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                    String fileName = tokens[1].substring(0, tokens[1].length() - 1);
                    fileName = fileName.replace("\"", "");
                    if (fileName.charAt(0) == '/') {
                        fileName = fileName.substring(1, fileName.length() - 1);
                        fileName = fileName.replaceFirst("/", ":/");
                    }
                    // Check to see if more then 2 tokens, Date may not be populated, will default to 0
                    if (tokens.length > 2) {
                        // Time in the format of 20200131104456-05'00'
                        try {
                            String fileUsedTime = tokens[2].replaceAll("'", "");
                            Date usedDate = adobePluginDateFormat.parse(fileUsedTime);
                            adobeUsedTime = usedDate.getTime() / 1000;
                        } catch (ParseException ex) {
                            // catching error and displaying date that could not be parsed
                            // we set the timestamp to 0 and continue on processing
                            logger.log(Level.WARNING, String.format("Failed to parse date/time %s for adobe file artifact.", tokens[2]), ex); //NON-NLS
                        }
                    }
                    Collection<BlackboardAttribute> attributes = new ArrayList<>();
                    attributes.add(new BlackboardAttribute(TSK_PATH, getName(), fileName));
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, getName(), adobeUsedTime));
                    attributes.add(new BlackboardAttribute(TSK_COMMENT, getName(), comment));
                    BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_RECENT_OBJECT, regFile, attributes);
                    if (bba != null) {
                        bbartifacts.add(bba);
                        fileName = fileName.replace("\0", "");
                        bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                        if (bba != null) {
                            bbartifacts.add(bba);
                        }
                    }
                    line = reader.readLine();
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Create recently used artifacts to parse the Media Player MRU regripper
     * (mpmru) records
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parseMediaPlayerMRUList(AbstractFile regFile, BufferedReader reader, String comment) throws FileNotFoundException, IOException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String line = reader.readLine();
        while (!line.contains(SECTION_DIVIDER)) {
            line = reader.readLine();
            line = line.trim();
            if (line.contains("LastWrite")) {
                line = reader.readLine();
                // Columns are
                // FileX -> <Media file>
                while (!line.contains(SECTION_DIVIDER) && !line.contains("RecentFileList has no values.")) {
                    // Split line on "> " which is the record delimiter between position and file
                    String tokens[] = line.split("> ");
                    String fileName = tokens[1];
                    Collection<BlackboardAttribute> attributes = new ArrayList<>();
                    attributes.add(new BlackboardAttribute(TSK_PATH, getName(), fileName));
                    attributes.add(new BlackboardAttribute(TSK_COMMENT, getName(), comment));
                    BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_RECENT_OBJECT, regFile, attributes);
                    if (bba != null) {
                        bbartifacts.add(bba);
                        bba = createAssociatedArtifact(fileName, bba);
                        if (bba != null) {
                            bbartifacts.add(bba);
                            bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                            if (bba != null) {
                                bbartifacts.add(bba);
                            }
                        }
                    }
                    line = reader.readLine();
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Create recently used artifacts to parse the regripper plugin output, this
     * format is used in several diffent plugins
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parseGenericMRUList(AbstractFile regFile, BufferedReader reader, String comment) throws FileNotFoundException, IOException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String line = reader.readLine();
        while (!line.contains(SECTION_DIVIDER)) {
            line = reader.readLine();
            line = line.trim();
            if (line.contains("LastWrite")) {
                line = reader.readLine();
                // Columns are
                // FileX -> <file>
                while (!line.contains(SECTION_DIVIDER) && !line.isEmpty() && !line.contains("Applets")
                        && !line.contains(("Recent File List"))) {
                    // Split line on "> " which is the record delimiter between position and file
                    String tokens[] = line.split("> ");
                    String fileName = tokens[1];
                    Collection<BlackboardAttribute> attributes = new ArrayList<>();
                    attributes.add(new BlackboardAttribute(TSK_PATH, getName(), fileName));
                    attributes.add(new BlackboardAttribute(TSK_COMMENT, getName(), comment));
                    BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_RECENT_OBJECT, regFile, attributes);
                    if (bba != null) {
                        bbartifacts.add(bba);
                        bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                        if (bba != null) {
                            bbartifacts.add(bba);
                        }
                    }
                    line = reader.readLine();
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Create recently used artifacts to parse the WinRAR Regripper plugin
     * output
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parseWinRARMRUList(AbstractFile regFile, BufferedReader reader, String comment) throws FileNotFoundException, IOException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String line = reader.readLine();
        while (!line.contains(SECTION_DIVIDER)) {
            line = reader.readLine();
            line = line.trim();
            if (line.contains("LastWrite")) {
                line = reader.readLine();
                // Columns are
                // FileX -> <Media file>
                if (!line.isEmpty()) {
                    while (!line.contains(SECTION_DIVIDER)) {
                        // Split line on "> " which is the record delimiter between position and file
                        String tokens[] = line.split("> ");
                        String fileName = tokens[1];
                        Collection<BlackboardAttribute> attributes = new ArrayList<>();
                        attributes.add(new BlackboardAttribute(TSK_PATH, getName(), fileName));
                        attributes.add(new BlackboardAttribute(TSK_COMMENT, getName(), comment));
                        BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_RECENT_OBJECT, regFile, attributes);
                        if (bba != null) {
                            bbartifacts.add(bba);
                            bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                            if (bba != null) {
                                bbartifacts.add(bba);
                            }
                        }
                        line = reader.readLine();
                    }
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Create recently used artifacts to parse the runmru ArcHistory (7Zip)
     * regripper plugin records
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parse7ZipMRU(AbstractFile regFile, BufferedReader reader, String comment) throws FileNotFoundException, IOException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String line = reader.readLine();
        line = line.trim();
        if (!line.contains("PathHistory:")) {
            while (!line.contains("PathHistory:") && !line.isEmpty()) {
                // Columns are
                // <fileName>
                String fileName = line;
                Collection<BlackboardAttribute> attributes = new ArrayList<>();
                attributes.add(new BlackboardAttribute(TSK_PATH, getName(), fileName));
                attributes.add(new BlackboardAttribute(TSK_COMMENT, getName(), comment));
                BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_RECENT_OBJECT, regFile, attributes);
                if (bba != null) {
                    bbartifacts.add(bba);
                    bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                    if (bba != null) {
                        bbartifacts.add(bba);
                    }
                }
                line = reader.readLine();
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Create recently used artifacts to parse the Office Documents 2010 records
     * Regripper Plugin output
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parseOfficeDocs2010MRUList(AbstractFile regFile, BufferedReader reader, String comment) throws FileNotFoundException, IOException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String line = reader.readLine();
        line = line.trim();
        // Reading to the SECTION DIVIDER to get next section of records to process.  Dates appear to have
        // multiple spaces in them that makes it harder to parse so next section will be easier to parse 
        while (!line.contains(SECTION_DIVIDER)) {
            line = reader.readLine();
        }
        line = reader.readLine();
        while (!line.contains(SECTION_DIVIDER)) {
            // record has the following format
            // 1294283922|REG|||OfficeDocs2010 - F:\Windows_time_Rules_xp.doc
            String tokens[] = line.split("\\|");
            Long docDate = Long.valueOf(tokens[0]);
            String fileNameTokens[] = tokens[4].split(" - ");
            String fileName = fileNameTokens[1];
            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            attributes.add(new BlackboardAttribute(TSK_PATH, getName(), fileName));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, getName(), docDate));
            attributes.add(new BlackboardAttribute(TSK_COMMENT, getName(), comment));
            BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_RECENT_OBJECT, regFile, attributes);
            if (bba != null) {
                bbartifacts.add(bba);
                bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                if (bba != null) {
                    bbartifacts.add(bba);
                }
            }
            line = reader.readLine();
            line = line.trim();
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Create recently used artifacts to parse the Office trust records
     * (trustrecords) Regipper plugin records
     *
     * @param regFile registry file the artifact is associated with
     *
     * @param reader  buffered reader to parse adobemru records
     *
     * @param comment string that will populate attribute TSK_COMMENT
     *
     * @throws FileNotFound and IOException
     */
    private void parseOfficeTrustRecords(AbstractFile regFile, BufferedReader reader, String comment) throws FileNotFoundException, IOException {
        String userProfile = regFile.getParentPath();
        userProfile = userProfile.substring(0, userProfile.length() - 1);
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
        SimpleDateFormat pluginDateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", US);
        Long usedTime = Long.valueOf(0);
        String line = reader.readLine();
        while (!line.contains(SECTION_DIVIDER)) {
            line = reader.readLine();
            line = line.trim();
            usedTime = Long.valueOf(0);
            if (!line.contains("**") && !line.contains("----------") && !line.contains("LastWrite")
                    && !line.contains(SECTION_DIVIDER) && !line.isEmpty() && !line.contains("TrustRecords")
                    && !line.contains("VBAWarnings =")) {
                // Columns are
                // Date : <File Name>/<Website>
                // Split line on " : " which is the record delimiter between position and file
                String fileName = null;
                String tokens[] = line.split(" : ");
                fileName = tokens[1];
                fileName = fileName.replace("%USERPROFILE%", userProfile);
                // Time in the format of Wed May 31 14:33:03 2017 Z 
                try {
                    String fileUsedTime = tokens[0].replaceAll(" Z", "");
                    Date usedDate = pluginDateFormat.parse(fileUsedTime);
                    usedTime = usedDate.getTime() / 1000;
                } catch (ParseException ex) {
                    // catching error and displaying date that could not be parsed
                    // we set the timestamp to 0 and continue on processing
                    logger.log(Level.WARNING, String.format("Failed to parse date/time %s for TrustRecords artifact.", tokens[0]), ex); //NON-NLS
                }
                Collection<BlackboardAttribute> attributes = new ArrayList<>();
                attributes.add(new BlackboardAttribute(TSK_PATH, getName(), fileName));
                attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, getName(), usedTime));
                attributes.add(new BlackboardAttribute(TSK_COMMENT, getName(), comment));
                BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_RECENT_OBJECT, regFile, attributes);
                if (bba != null) {
                    bbartifacts.add(bba);
                    bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                    if (bba != null) {
                        bbartifacts.add(bba);
                    }
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Create associated artifacts using file name and path and the artifact it
     * associates with
     *
     * @param filePathName file and path of object being associated with
     *
     * @param bba          blackboard artifact to associate with
     *
     * @returnv BlackboardArtifact or a null value
     */
    private BlackboardArtifact createAssociatedArtifact(String filePathName, BlackboardArtifact bba) {
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        String fileName = FilenameUtils.getName(filePathName);
        String filePath = FilenameUtils.getPath(filePathName);
        List<AbstractFile> sourceFiles;
        try {
            sourceFiles = fileManager.findFiles(dataSource, fileName, filePath); //NON-NLS
            if (!sourceFiles.isEmpty()) {
                for (AbstractFile sourceFile : sourceFiles) {
                    if (sourceFile.getParentPath().endsWith(filePath)) {
                        Collection<BlackboardAttribute> bbattributes2 = new ArrayList<>();
                        bbattributes2.addAll(Arrays.asList(
                                new BlackboardAttribute(TSK_ASSOCIATED_ARTIFACT, this.getName(),
                                        bba.getArtifactID())));

                        BlackboardArtifact associatedObjectBba = createArtifactWithAttributes(TSK_ASSOCIATED_OBJECT, sourceFile, bbattributes2);
                        if (associatedObjectBba != null) {
                            return associatedObjectBba;
                        }
                    }
                }
            }
        } catch (TskCoreException ex) {
            // only catching the error and displaying the message as the file may not exist on the 
            // system anymore
            logger.log(Level.WARNING, String.format("Error finding actual file %s. file may not exist", filePathName)); //NON-NLS
        }

        return null;
    }

    /**
     * Create a map of userids to usernames from the OS Accounts.
     *
     * @param dataSource
     *
     * @return A Map of userIDs and userNames
     *
     * @throws TskCoreException
     */
    private Map<String, String> makeUserNameMap(Content dataSource) throws TskCoreException {
        Map<String, String> userNameMap = new HashMap<>();

        List<BlackboardArtifact> accounts = blackboard.getArtifacts(TSK_OS_ACCOUNT.getTypeID(), dataSource.getId());

        for (BlackboardArtifact account : accounts) {
            BlackboardAttribute nameAttribute = getAttributeForArtifact(account, TSK_USER_NAME);
            BlackboardAttribute idAttribute = getAttributeForArtifact(account, TSK_USER_ID);

            String userName = nameAttribute != null ? nameAttribute.getDisplayString() : "";
            String userID = idAttribute != null ? idAttribute.getDisplayString() : "";

            if (!userID.isEmpty()) {
                userNameMap.put(userID, userName);
            }
        }

        return userNameMap;
    }

    /**
     * Gets the attribute for the given type from the given artifact.
     *
     * @param artifact BlackboardArtifact to get the attribute from
     * @param type     The BlackboardAttribute Type to get
     *
     * @return BlackboardAttribute for given artifact and type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute getAttributeForArtifact(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) throws TskCoreException {
        return artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(type.getTypeID())));
    }

    /**
     * Create the shellbag artifacts from the list of ShellBag objects.
     *
     * @param regFile   The data source file
     * @param shellbags List of shellbags from source file
     *
     * @throws TskCoreException
     */
    void createShellBagArtifacts(AbstractFile regFile, List<ShellBag> shellbags) throws TskCoreException {
        List<BlackboardArtifact> artifacts = new ArrayList<>();
        try {
            for (ShellBag bag : shellbags) {
                Collection<BlackboardAttribute> attributes = new ArrayList<>();
                BlackboardArtifact artifact = regFile.newArtifact(getShellBagArtifact().getTypeID());
                attributes.add(new BlackboardAttribute(TSK_PATH, getName(), bag.getResource()));
                attributes.add(new BlackboardAttribute(getKeyAttribute(), getName(), bag.getKey()));

                long time;
                time = bag.getLastWrite();
                if (time != 0) {
                    attributes.add(new BlackboardAttribute(getLastWriteAttribute(), getName(), time));
                }

                time = bag.getModified();
                if (time != 0) {
                    attributes.add(new BlackboardAttribute(TSK_DATETIME_MODIFIED, getName(), time));
                }

                time = bag.getCreated();
                if (time != 0) {
                    attributes.add(new BlackboardAttribute(TSK_DATETIME_CREATED, getName(), time));
                }

                time = bag.getAccessed();
                if (time != 0) {
                    attributes.add(new BlackboardAttribute(TSK_DATETIME_ACCESSED, getName(), time));
                }

                artifact.addAttributes(attributes);

                artifacts.add(artifact);
            }
        } finally {
            postArtifacts(artifacts);
        }
    }

    /**
     * Returns the custom Shellbag artifact type or creates it if it does not
     * currently exist.
     *
     * @return BlackboardArtifact.Type for shellbag artifacts
     *
     * @throws TskCoreException
     */
    private BlackboardArtifact.Type getShellBagArtifact() throws TskCoreException {
        if (shellBagArtifactType == null) {
            try {
                shellBagArtifactType = tskCase.getBlackboard().getOrAddArtifactType(SHELLBAG_ARTIFACT_NAME, Bundle.Shellbag_Artifact_Display_Name());
            } catch (BlackboardException ex) {
                throw new TskCoreException(String.format("Failed to get shell bag artifact type", SHELLBAG_ARTIFACT_NAME), ex);
            }  
        }

        return shellBagArtifactType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getLastWriteAttribute() throws TskCoreException {
        if (shellBagLastWriteAttributeType == null) {
            try {
                shellBagLastWriteAttributeType = tskCase.getBlackboard().getOrAddAttributeType(SHELLBAG_ATTRIBUTE_LAST_WRITE,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME,
                        Bundle.Shellbag_Last_Write_Attribute_Display_Name());
            } catch (BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", SHELLBAG_ATTRIBUTE_LAST_WRITE), ex);
            }
        }
        return shellBagLastWriteAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getKeyAttribute() throws TskCoreException {
        if (shellBagKeyAttributeType == null) {
            try {
                shellBagKeyAttributeType = tskCase.getBlackboard().getOrAddAttributeType(SHELLBAG_ATTRIBUTE_KEY,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                        Bundle.Shellbag_Key_Attribute_Display_Name());
            } catch (BlackboardException ex) {
                throw new TskCoreException(String.format("Failed to get key attribute %s", SHELLBAG_ATTRIBUTE_KEY), ex);
            }
        }
        return shellBagKeyAttributeType;
    }

    /**
     * Maps the user groups to the sid that are a part of them.
     *
     * @param bufferedReader
     *
     * @return A map if sid and the groups they map too
     *
     * @throws IOException
     */
    Map<String, List<String>> readGroups(BufferedReader bufferedReader) throws IOException {
        Map<String, List<String>> groupMap = new HashMap<>();

        String line = bufferedReader.readLine();

        int userCount = 0;
        String groupName = null;

        while (line != null && !line.contains(SECTION_DIVIDER)) {

            if (line.contains("Group Name")) {
                String value = line.replaceAll("Group Name\\s*?:", "").trim();
                groupName = (value.replaceAll("\\[\\d*?\\]", "")).trim();
                int startIndex = value.indexOf(" [") + 1; 
                int endIndex = value.indexOf(']');

                if (startIndex != -1 && endIndex != -1) {
                    String countStr = value.substring(startIndex + 1, endIndex);
                    userCount = Integer.parseInt(countStr);
                }
            } else if (line.matches("Users\\s*?:")) {
                for (int i = 0; i < userCount; i++) {
                    line = bufferedReader.readLine();
                    if (line != null) {
                        String sid = line.trim();
                        List<String> groupList = groupMap.get(sid);
                        if (groupList == null) {
                            groupList = new ArrayList<>();
                            groupMap.put(sid, groupList);
                        }
                        groupList.add(groupName);
                    }
                }
                groupName = null;
            }
            line = bufferedReader.readLine();
        }
        return groupMap;
    }

    /**
     * Gets the key value from user account strings of the format key:value or
     * --> value
     *
     * @param line String to parse
     *
     * @return key value pair
     */
    private Map.Entry<String, String> getSAMKeyValue(String line) {
        int index = line.indexOf(':');
        Map.Entry<String, String> returnValue = null;
        String key = null;
        String value = null;

        if (index != -1) {
            key = line.substring(0, index).trim();
            if (index + 1 < line.length()) {
                value = line.substring(index + 1).trim();
            } else {
                value = "";
            }

        } else if (line.contains("-->")) {
            key = line.replace("-->", "").trim();
            value = "true";
        }

        if (key != null) {
            returnValue = new AbstractMap.SimpleEntry<>(key, value);
        }

        return returnValue;
    }

    @Override
    public void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        this.context = context;

        progressBar.progress(Bundle.Progress_Message_Analyze_Registry());
        analyzeRegistryFiles();

    }

    /**
     * Private wrapper class for Registry output files
     */
    private class RegOutputFiles {

        public String autopsyPlugins = "";
        public String fullPlugins = "";
    }
}
