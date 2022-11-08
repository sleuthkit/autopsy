/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
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
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;
import static java.util.Locale.US;
import java.util.Optional;
import static java.util.TimeZone.getTimeZone;
import java.util.stream.Collectors;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.recentactivity.ShellBagParser.ShellBag;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_HOME_DIR;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.HostManager;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccount.OsAccountAttribute;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.OsAccountManager;
import org.sleuthkit.datamodel.OsAccountManager.NotUserSIDException;
import org.sleuthkit.datamodel.OsAccountManager.OsAccountUpdateResult;
import org.sleuthkit.datamodel.OsAccountRealm;
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
    "Sam_Security_Question_1_Attribute_Display_Name=Security Question 1",
    "Sam_Security_Answer_1_Attribute_Display_Name=Security Answer 1",
    "Sam_Security_Question_2_Attribute_Display_Name=Security Question 2",
    "Sam_Security_Answer_2_Attribute_Display_Name=Security Answer 2",
    "Sam_Security_Question_3_Attribute_Display_Name=Security Question 3",
    "Sam_Security_Answer_3_Attribute_Display_Name=Security Answer 3",
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
    private static final String SECURITY_QUESTION_1 = "Question 1";
    private static final String SECURITY_ANSWER_1 = "Answer 1";
    private static final String SECURITY_QUESTION_2 = "Question 2";
    private static final String SECURITY_ANSWER_2 = "Answer 2";
    private static final String SECURITY_QUESTION_3 = "Question 3";
    private static final String SECURITY_ANSWER_3 = "Answer 3";
    
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
    private final IngestJobContext context;
    private Map<String, String> userNameMap;
    private final List<String> samDomainIDsList = new ArrayList<>();

    private String compName = "";
    private String domainName = "";

    private static final String SHELLBAG_ARTIFACT_NAME = "RA_SHELL_BAG"; //NON-NLS
    private static final String SHELLBAG_ATTRIBUTE_LAST_WRITE = "RA_SHELL_BAG_LAST_WRITE"; //NON-NLS
    private static final String SHELLBAG_ATTRIBUTE_KEY = "RA_SHELL_BAG_KEY"; //NON-NLS
    private static final String SAM_SECURITY_QUESTION_1 = "RA_SAM_QUESTION_1"; //NON-NLS;
    private static final String SAM_SECURITY_ANSWER_1 = "RA_SAM_ANSWER_1"; //NON-NLS;
    private static final String SAM_SECURITY_QUESTION_2 = "RA_SAM_QUESTION_2"; //NON-NLS;
    private static final String SAM_SECURITY_ANSWER_2 = "RA_SAM_ANSWER_2"; //NON-NLS;
    private static final String SAM_SECURITY_QUESTION_3 = "RA_SAM_QUESTION_3"; //NON-NLS;
    private static final String SAM_SECURITY_ANSWER_3 = "RA_SAM_ANSWER_3"; //NON-NLS;
    

    private static final SimpleDateFormat REG_RIPPER_TIME_FORMAT = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy 'Z'", US);

    private BlackboardArtifact.Type shellBagArtifactType = null;
    private BlackboardAttribute.Type shellBagKeyAttributeType = null;
    private BlackboardAttribute.Type shellBagLastWriteAttributeType = null;
    
    private OSInfo osInfo = new OSInfo();

    static {
        REG_RIPPER_TIME_FORMAT.setTimeZone(getTimeZone("GMT"));
    }

    ExtractRegistry(IngestJobContext context) throws IngestModuleException {
        super(NbBundle.getMessage(ExtractRegistry.class, "ExtractRegistry.moduleName.text"), context);
        this.context = context;

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
            this.addErrorMessage(this.getDisplayName() + ": " + msg);
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
                this.addErrorMessage(this.getDisplayName() + ": " + msg);
            }
        }
        return allRegistryFiles;
    }

    /**
     * Identifies registry files in the database by mtimeItem, runs regripper on
     * them, and parses the output.
     *
     * @param ingestJobId The ingest job id.
     */
    private void analyzeRegistryFiles(long ingestJobId) {
        List<AbstractFile> allRegistryFiles = findRegistryFiles();

        // open the log file
        FileWriter logFile = null;
        try {
            logFile = new FileWriter(RAImageIngestModule.getRAOutputPath(currentCase, "reg", ingestJobId) + File.separator + "regripper-info.txt"); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        for (AbstractFile regFile : allRegistryFiles) {
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            String regFileName = regFile.getName();
            long regFileId = regFile.getId();
            String regFileNameLocal = RAImageIngestModule.getRATempPath(currentCase, "reg", ingestJobId) + File.separator + regFileName;
            String outputPathBase = RAImageIngestModule.getRAOutputPath(currentCase, "reg", ingestJobId) + File.separator + regFileName + "-regripper-" + Long.toString(regFileId); //NON-NLS
            File regFileNameLocalFile = new File(regFileNameLocal);
            try {
                ContentUtils.writeToFile(regFile, regFileNameLocalFile, context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading registry file '%s' (id=%d).",
                        regFile.getName(), regFileId), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.errMsg.errWritingTemp",
                                this.getDisplayName(), regFileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp registry file '%s' for registry file '%s' (id=%d).",
                        regFileNameLocal, regFile.getName(), regFileId), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.errMsg.errWritingTemp",
                                this.getDisplayName(), regFileName));
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

            logger.log(Level.INFO, "{0}- Now getting registry information from {1}", new Object[]{getDisplayName(), regFileNameLocal}); //NON-NLS
            RegOutputFiles regOutputFiles = ripRegistryFile(regFileNameLocal, outputPathBase);
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            // parse the autopsy-specific output
            if (regOutputFiles.autopsyPlugins.isEmpty() == false && parseAutopsyPluginOutput(regOutputFiles.autopsyPlugins, regFile) == false) {
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.failedParsingResults",
                                this.getDisplayName(), regFileName));
            }

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            // create a report for the full output
            if (!regOutputFiles.fullPlugins.isEmpty()) {
                //parse the full regripper output from SAM hive files
                if (regFileNameLocal.toLowerCase().contains("sam") && parseSamPluginOutput(regOutputFiles.fullPlugins, regFile, ingestJobId) == false) {
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.failedParsingResults",
                                    this.getDisplayName(), regFileName));
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
                                    this.getDisplayName(), regFileName));
                }

                if (context.dataSourceIngestIsCancelled()) {
                    return;
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
        
        // RA can be run on non-window images. We are going to assume that
        // the data source was from windows if there was registry files. 
        // Therefore we will only create the OSInfo object if there are 
        // registry files.
        if(allRegistryFiles.size() > 0) {
            osInfo.createOSInfo();
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
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getDisplayName(), regFilePath));
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
            this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getDisplayName(), hiveFilePath));
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
        String parentModuleName = RecentActivityExtracterModuleFactory.getModuleName();
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
                    String etime = timenode.getTextContent().trim();
                    //sometimes etime will be an empty string and therefore can not be parsed into a date
                    if (etime != null && !etime.isEmpty()) {
                        try {
                            mtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", US).parse(etime).getTime();
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
                                                installtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyyZ", US).parse(value + "+0000").getTime();
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
                        
                        osInfo.setOsName(version);
                        osInfo.setInstalltime(installtime);
                        osInfo.setSystemRoot(systemRoot);
                        osInfo.setProductId(productId);
                        osInfo.setRegOwner(regOwner);
                        osInfo.setRegOrg(regOrg);
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
                        
                        osInfo.setOsName(os);
                        osInfo.setProcessorArchitecture(procArch);
                        osInfo.setTempDir(tempDir);
                        break;
                    case "CompName": // NON-NLS
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
                                    domainName = value;
                                }
                            }
                        }
                        
                        osInfo.setCompName(compName);
                        osInfo.setDomain(domainName);
                        
                        for (Map.Entry<String, String> userMap : getUserNameMap().entrySet()) {
                            String sid = "";
                            try {
                                sid = userMap.getKey();
                                String userName = userMap.getValue();
                                // Accounts in the SAM are all local accounts
                                createOrUpdateOsAccount(regFile, sid, userName, null, null, OsAccountRealm.RealmScope.LOCAL);
                            } catch (TskCoreException | TskDataException | NotUserSIDException ex) {
                                logger.log(Level.WARNING, String.format("Failed to update Domain for existing OsAccount: %s, sid: %s", regFile.getId(), sid), ex);
                            }
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
                                            Long usbMtime = Long.valueOf("0");
                                            if (!artnode.getAttribute("mtime").isEmpty()) {
                                                usbMtime = Long.parseLong(artnode.getAttribute("mtime")); //NON-NLS
                                            } 
                                            usbMtime = Long.valueOf(usbMtime.toString());
                                            if (usbMtime > 0) {
                                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, parentModuleName, usbMtime));
                                            }
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
                                            newArtifacts.add(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_DEVICE_ATTACHED, regFile, bbattributes));
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, String.format("Error adding device_attached artifact to blackboard for file %d.", regFile.getId()), ex); //NON-NLS
                                        }
                                        break;
                                    case "uninstall": //NON-NLS
                                        Long itemMtime = null;
                                        try {
                                            String mTimeAttr = artnode.getAttribute("mtime");
                                            if (mTimeAttr != null && !mTimeAttr.isEmpty()) {
                                                itemMtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", US).parse(mTimeAttr).getTime(); //NON-NLS
                                                itemMtime /= MS_IN_SEC;
                                            }
                                        } catch (ParseException ex) {
                                            logger.log(Level.SEVERE, "Failed to parse epoch time for installed program artifact.", ex); //NON-NLS
                                        }

                                        try {
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, parentModuleName, value));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, parentModuleName, itemMtime));
                                            BlackboardArtifact bbart = regFile.newDataArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_INSTALLED_PROG), bbattributes);
                                            newArtifacts.add(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard.", ex); //NON-NLS
                                        }
                                        break;
                                    case "office": //NON-NLS
                                        String officeName = artnode.getAttribute("name"); //NON-NLS

                                        try {
                                            // @@@ BC: Consider removing this after some more testing. It looks like an Mtime associated with the root key and not the individual item
                                            if (mtime != null) {
                                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, parentModuleName, mtime));
                                            }
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, parentModuleName, officeName));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE, parentModuleName, value));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, parentModuleName, artnode.getNodeName()));
                                            BlackboardArtifact bbart = regFile.newDataArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_RECENT_OBJECT), bbattributes);

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
                                        String homeDir = value;
                                        String sid = artnode.getAttribute("sid"); //NON-NLS
                                        String username = artnode.getAttribute("username"); //NON-NLS
                                        String domName = domainName;

                                        // accounts in profileList can be either domain or local
                                        // Assume domain unless the SID was seen before in the SAM (which is only local). 
                                        OsAccountRealm.RealmScope scope = OsAccountRealm.RealmScope.DOMAIN;
                                        if (isDomainIdInSAMList(sid)) {
                                            domName = null;
                                            scope = OsAccountRealm.RealmScope.LOCAL;
                                        }

                                        try {
                                            createOrUpdateOsAccount(regFile, sid, username, homeDir, domName, scope);
                                        } catch (TskCoreException | TskDataException | NotUserSIDException ex) {
                                            logger.log(Level.SEVERE, String.format("Failed to create OsAccount for file: %s, sid: %s", regFile.getId(), sid), ex);
                                        }
                                        break;

                                    case "NtuserNetwork": // NON-NLS
                                        try {
                                        String localPath = artnode.getAttribute("localPath"); //NON-NLS
                                        String remoteName = value;

                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LOCAL_PATH,
                                                parentModuleName, localPath));
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REMOTE_PATH,
                                                parentModuleName, remoteName));
                                        BlackboardArtifact bbart = regFile.newDataArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_REMOTE_DRIVE), bbattributes);
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
                                            BlackboardArtifact bbart = regFile.newDataArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WIFI_NETWORK), bbattributes);
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

            if (!context.dataSourceIngestIsCancelled()) {
                postArtifacts(newArtifacts);
            }
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

                    try {
                        bbartifacts.add(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_BLUETOOTH_PAIRING, regFile, attributes));
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to create bluetooth_pairing artifact for file %d", regFile.getId()), ex);
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

        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
                attributes.add(new BlackboardAttribute(attributeType, getDisplayName(), dateLong));
            } else {
                attributes.add(new BlackboardAttribute(attributeType, getDisplayName(), tokenString));
            }
        }
    }

    /**
     * Parse the output of the SAM regripper plugin to get additional Account
     * information
     *
     * @param regFilePath     the path to the registry file being parsed
     * @param regAbstractFile the file to associate newly created artifacts with
     * @param ingestJobId     The ingest job id.
     *
     * @return true if successful, false if parsing failed at some point
     */
    private boolean parseSamPluginOutput(String regFilePath, AbstractFile regAbstractFile, long ingestJobId) {

        File regfile = new File(regFilePath);
        List<BlackboardArtifact> newArtifacts = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(regfile), StandardCharsets.UTF_8))) {
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
                String sid = userInfo.get(SID_KEY);
                userInfoMap.put(sid, userInfo);
                addSIDToSAMList(sid);
            }

            // New OsAccount Code 
            OsAccountManager accountMgr = tskCase.getOsAccountManager();
            HostManager hostMrg = tskCase.getHostManager();
            Host host = hostMrg.getHostByDataSource((DataSource) dataSource);

            List<OsAccount> existingAccounts = accountMgr.getOsAccounts(host);
            for (OsAccount osAccount : existingAccounts) {
                Optional<String> optional = osAccount.getAddr();
                if (!optional.isPresent()) {
                    continue;
                }

                String sid = optional.get();
                Map<String, String> userInfo = userInfoMap.remove(sid);
                if (userInfo != null) {
                    addAccountInstance(accountMgr, osAccount, (DataSource) dataSource);
                    updateOsAccount(osAccount, userInfo, groupMap.get(sid), regAbstractFile, ingestJobId);
                }
            }

            //add remaining userinfos as accounts;
            for (Map<String, String> userInfo : userInfoMap.values()) {
                OsAccount osAccount = accountMgr.newWindowsOsAccount(userInfo.get(SID_KEY), null, null, host, OsAccountRealm.RealmScope.LOCAL);
                accountMgr.newOsAccountInstance(osAccount, (DataSource) dataSource, OsAccountInstance.OsAccountInstanceType.LAUNCHED);
                updateOsAccount(osAccount, userInfo, groupMap.get(userInfo.get(SID_KEY)), regAbstractFile, ingestJobId);
            }
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, "Error finding the registry file.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error building the document parser: {0}", ex); //NON-NLS
        } catch (TskDataException | TskCoreException ex) {
            logger.log(Level.WARNING, "Error updating TSK_OS_ACCOUNT artifacts to include newly parsed data.", ex); //NON-NLS
        } catch (OsAccountManager.NotUserSIDException ex) {
            logger.log(Level.WARNING, "Error creating OS Account, input SID is not a user SID.", ex); //NON-NLS
        } finally {
            if (!context.dataSourceIngestIsCancelled()) {
                postArtifacts(newArtifacts);
            }
        }
        return false;
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
            String userName = getUserNameMap().get(userSid);
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
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, getDisplayName(), fileName));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME, getDisplayName(), userName));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, getDisplayName(), progRunDateTime));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COMMENT, getDisplayName(), comment));

            try {
                BlackboardArtifact bba = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_PROG_RUN, regFile, attributes);
                bbartifacts.add(bba);
                bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                if (bba != null) {
                    bbartifacts.add(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to create TSK_PROG_RUN artifact for file %d", regFile.getId()), ex);
            }
            line = reader.readLine();
        }
        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
                    attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), fileName));
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, getDisplayName(), adobeUsedTime));
                    attributes.add(new BlackboardAttribute(TSK_COMMENT, getDisplayName(), comment));
                    try {
                        BlackboardArtifact bba = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_RECENT_OBJECT, regFile, attributes);
                        if (bba != null) {
                            bbartifacts.add(bba);
                            fileName = fileName.replace("\0", "");
                            bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                            if (bba != null) {
                                bbartifacts.add(bba);
                            }
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to create TSK_RECENT_OBJECT artifact for file %d", regFile.getId()), ex);
                    }
                    line = reader.readLine();
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
                    attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), fileName));
                    attributes.add(new BlackboardAttribute(TSK_COMMENT, getDisplayName(), comment));
                    try {
                        BlackboardArtifact bba = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_RECENT_OBJECT, regFile, attributes);
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
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to create TSK_RECENT_OBJECT artifact for file %d", regFile.getId()), ex);
                    }
                    line = reader.readLine();
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
                    if (tokens.length > 1) {
                        String fileName = tokens[1];
                        Collection<BlackboardAttribute> attributes = new ArrayList<>();
                        attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), fileName));
                        attributes.add(new BlackboardAttribute(TSK_COMMENT, getDisplayName(), comment));
                        try {
                            BlackboardArtifact bba = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_RECENT_OBJECT, regFile, attributes);
                            if (bba != null) {
                                bbartifacts.add(bba);
                                bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                                if (bba != null) {
                                    bbartifacts.add(bba);
                                }
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, String.format("Failed to create TSK_RECENT_OBJECT artifact for file %d", regFile.getId()), ex);
                        }
                    }
                    line = reader.readLine();
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
                        attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), fileName));
                        attributes.add(new BlackboardAttribute(TSK_COMMENT, getDisplayName(), comment));
                        try {
                            BlackboardArtifact bba = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_RECENT_OBJECT, regFile, attributes);
                            bbartifacts.add(bba);
                            bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                            if (bba != null) {
                                bbartifacts.add(bba);
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, String.format("Failed to create TSK_RECENT_OBJECT artifact for file %d", regFile.getId()), ex);
                        }
                        line = reader.readLine();
                    }
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
                attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), fileName));
                attributes.add(new BlackboardAttribute(TSK_COMMENT, getDisplayName(), comment));
                try {
                    BlackboardArtifact bba = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_RECENT_OBJECT, regFile, attributes);
                    bbartifacts.add(bba);
                    bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                    if (bba != null) {
                        bbartifacts.add(bba);
                    }

                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to create TSK_RECENT_OBJECT artifact for file %d", regFile.getId()), ex);
                }
                line = reader.readLine();
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
            attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), fileName));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, getDisplayName(), docDate));
            attributes.add(new BlackboardAttribute(TSK_COMMENT, getDisplayName(), comment));
            try {
                BlackboardArtifact bba = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_RECENT_OBJECT, regFile, attributes);
                bbartifacts.add(bba);
                bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                if (bba != null) {
                    bbartifacts.add(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to create TSK_RECENT_OBJECT artifact for file %d", regFile.getId()), ex);
            }
            line = reader.readLine();
            line = line.trim();
        }
        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
                attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), fileName));
                attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, getDisplayName(), usedTime));
                attributes.add(new BlackboardAttribute(TSK_COMMENT, getDisplayName(), comment));
                try {
                    BlackboardArtifact bba = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_RECENT_OBJECT, regFile, attributes);
                    bbartifacts.add(bba);
                    bba = createAssociatedArtifact(FilenameUtils.normalize(fileName, true), bba);
                    if (bba != null) {
                        bbartifacts.add(bba);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to create TSK_RECENT_OBJECT artifact for file %d", regFile.getId()), ex);
                }
                line = line.trim();
            }
        }
        if (!bbartifacts.isEmpty() && !context.dataSourceIngestIsCancelled()) {
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
        String fileName = FilenameUtils.getName(filePathName);
        String filePath = FilenameUtils.getPath(filePathName);
        List<AbstractFile> sourceFiles;
        try {
            sourceFiles = currentCase.getSleuthkitCase().getFileManager().findFilesExactNameExactPath(dataSource, fileName, filePath);
            if (!sourceFiles.isEmpty()) {
                return createAssociatedArtifact(sourceFiles.get(0), bba);
            }
        } catch (TskCoreException ex) {
            // only catching the error and displaying the message as the file may not exist on the 
            // system anymore
            logger.log(Level.WARNING, String.format("Error finding actual file %s. file may not exist", filePathName)); //NON-NLS
        }

        return null;
    }

    /**
     * Create a map of userids to usernames for all OS Accounts associated with
     * the current host in OsAccountManager.
     *
     * @param dataSource
     *
     * @return A Map of userIDs and userNames
     *
     * @throws TskCoreException
     */
    private Map<String, String> makeUserNameMap(Content dataSource) throws TskCoreException {
        Map<String, String> map = new HashMap<>();

        for (OsAccount account : tskCase.getOsAccountManager().getOsAccounts(((DataSource) dataSource).getHost())) {
            Optional<String> userName = account.getLoginName();
            String address = account.getAddr().orElse("");
            if (!address.isEmpty()) {
                map.put(address, userName.isPresent() ? userName.get() : "");
            }
        }

        return map;
    }

    /**
     * Strip the machine sid off of the osAccountSID. The returned string will
     * include everything in the osAccountSID up to the last -.
     *
     * There must be at least three dashes in the SID for it to be useful. The
     * sid is of a format S-R-X-Y1 where Y1 is the domain identifier which may
     * contain multiple dashes. Everything after the final dash is the relative
     * identifier. For example S-1-5-21-1004336348-1177238915-682003330-512
     *
     * In this example the domain identifier is
     * 21-1004336348-1177238915-682003330 The relative identifier is 512.
     *
     * In other words everything between the third and last dash is the domain
     * identifier.
     *
     * @param osAccountSID The SID of the os account.
     *
     * @return The Machine SID
     */
    private String stripRelativeIdentifierFromSID(String osAccountSID) {
        if (osAccountSID.split("-").length > 4) {
            int index = osAccountSID.lastIndexOf('-');
            return index > 1 ? osAccountSID.substring(0, index) : "";
        }
        return "";
    }

    private final List<String> machineSIDs = new ArrayList<>();

    /**
     * Returns a mapping of user sids to user names.
     *
     * @return SID to username map. Will be empty if none where found.
     */
    private Map<String, String> getUserNameMap() {
        if (userNameMap == null) {
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
                attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), bag.getResource()));
                attributes.add(new BlackboardAttribute(getKeyAttribute(), getDisplayName(), bag.getKey()));

                long time;
                time = bag.getLastWrite();
                if (time != 0) {
                    attributes.add(new BlackboardAttribute(getLastWriteAttribute(), getDisplayName(), time));
                }

                time = bag.getModified();
                if (time != 0) {
                    attributes.add(new BlackboardAttribute(TSK_DATETIME_MODIFIED, getDisplayName(), time));
                }

                time = bag.getCreated();
                if (time != 0) {
                    attributes.add(new BlackboardAttribute(TSK_DATETIME_CREATED, getDisplayName(), time));
                }

                time = bag.getAccessed();
                if (time != 0) {
                    attributes.add(new BlackboardAttribute(TSK_DATETIME_ACCESSED, getDisplayName(), time));
                }

                BlackboardArtifact artifact = createArtifactWithAttributes(getShellBagArtifact(), regFile, attributes);
                artifacts.add(artifact);
            }
        } finally {
            if (!context.dataSourceIngestIsCancelled()) {
                postArtifacts(artifacts);
            }
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
    public void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;

        progressBar.progress(Bundle.Progress_Message_Analyze_Registry());
        analyzeRegistryFiles(context.getJobId());
    }

    /**
     * Private wrapper class for Registry output files
     */
    private class RegOutputFiles {

        public String autopsyPlugins = "";
        public String fullPlugins = "";
    }

    /**
     * Updates an existing or creates a new OsAccount with the given attributes.
     *
     * @param file     Registry file
     * @param sid      Account sid
     * @param userName Login name
     * @param homeDir  Account home Directory
     *
     * @throws TskCoreException
     * @throws TskDataException
     * @throws OsAccountManager.NotUserSIDException
     */
    private void createOrUpdateOsAccount(AbstractFile file, String sid, String userName, String homeDir, String domainName, OsAccountRealm.RealmScope realmScope) throws TskCoreException, TskDataException, NotUserSIDException {
        OsAccountManager accountMgr = tskCase.getOsAccountManager();
        HostManager hostMrg = tskCase.getHostManager();
        Host host = hostMrg.getHostByDataSource((DataSource) dataSource);

        Optional<OsAccount> optional = accountMgr.getWindowsOsAccount(sid, null, null, host);
        OsAccount osAccount;
        if (!optional.isPresent()) {
            osAccount = accountMgr.newWindowsOsAccount(sid, userName != null && userName.isEmpty() ? null : userName, domainName, host, realmScope);
            accountMgr.newOsAccountInstance(osAccount, (DataSource) dataSource, OsAccountInstance.OsAccountInstanceType.LAUNCHED);
        } else {
            osAccount = optional.get();
            addAccountInstance(accountMgr, osAccount, (DataSource) dataSource);
            if (userName != null && !userName.isEmpty()) {
                OsAccountUpdateResult updateResult = accountMgr.updateCoreWindowsOsAccountAttributes(osAccount, null, userName, (domainName == null || domainName.isEmpty()) ? null : domainName, host);
                osAccount = updateResult.getUpdatedAccount().orElse(osAccount);
            }
        }

        if (homeDir != null && !homeDir.isEmpty()) {
            List<OsAccountAttribute> attributes = new ArrayList<>();
            String dir = homeDir.replaceFirst("^(%\\w*%)", "");
            dir = dir.replace("\\", "/");
            attributes.add(createOsAccountAttribute(TSK_HOME_DIR, dir, osAccount, host, file));
            accountMgr.addExtendedOsAccountAttributes(osAccount, attributes);
        }

    }

    /**
     * Create an account for the found email address.
     *
     * @param regFile      File the account was found in
     * @param emailAddress The emailAddress
     * @param ingestJobId  The ingest job id.
     */
    private void addEmailAccount(AbstractFile regFile, String emailAddress, long ingestJobId) {
        try {
            currentCase.getSleuthkitCase()
                    .getCommunicationsManager()
                    .createAccountFileInstance(Account.Type.EMAIL,
                            emailAddress, getRAModuleName(), regFile,
                            Collections.emptyList(),
                            ingestJobId);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE,
                    String.format("Error adding email account with value "
                            + "%s, to the case database for file %s [objId=%d]",
                            emailAddress, regFile.getName(), regFile.getId()), ex);
        }
    }

    /**
     * Parse the data time string found in the SAM file.
     *
     * @param value Date time string in the REG_RIPPER_TIME_FORMAT
     *
     * @return Java epoch time in seconds or null if the value could not be
     *         parsed.
     */
    private Long parseRegRipTime(String value) {
        try {
            return REG_RIPPER_TIME_FORMAT.parse(value).getTime() / MS_IN_SEC;
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, String.format("Failed to parse reg rip time: %s", value));
        }
        return null;
    }

    /**
     * Parse the data from the userInfo map created by parsing the SAM file.
     *
     * @param osAccount Account to update.
     * @param userInfo  userInfo map from SAM file parsing.
     * @param groupList Group list from the SAM file parsing.
     * @param regFile   Source file.
     * @param ingestJobId
     *
     * @throws TskDataException
     * @throws TskCoreException
     */
    private void updateOsAccount(OsAccount osAccount, Map<String, String> userInfo, List<String> groupList, AbstractFile regFile, long ingestJobId) throws TskDataException, TskCoreException, NotUserSIDException {
        Host host = ((DataSource) dataSource).getHost();
        SimpleDateFormat regRipperTimeFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy 'Z'", US);
        regRipperTimeFormat.setTimeZone(getTimeZone("GMT"));

        List<OsAccountAttribute> attributes = new ArrayList<>();

        Long creationTime = null;

        String value = userInfo.get(ACCOUNT_CREATED_KEY);
        if (value != null && !value.isEmpty() && !value.equals(NEVER_DATE)) {
            creationTime = parseRegRipTime(value);
        }

        value = userInfo.get(LAST_LOGIN_KEY);
        if (value != null && !value.isEmpty() && !value.equals(NEVER_DATE)) {
            Long time = parseRegRipTime(value);
            if (time != null) {
                attributes.add(createOsAccountAttribute(TSK_DATETIME_ACCESSED,
                        parseRegRipTime(value),
                        osAccount, host, regFile));
            }
        }

        String loginName = null;
        value = userInfo.get(USERNAME_KEY);
        if (value != null && !value.isEmpty()) {
            loginName = value;
        }

        value = userInfo.get(LOGIN_COUNT_KEY);
        if (value != null && !value.isEmpty()) {
            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_COUNT,
                    Integer.parseInt(value),
                    osAccount, host, regFile));
        }

        // From regripper the possible values for this key are
        // "Default Admin User", "Custom Limited Acct"
        // and "Default Guest Acct"
        value = userInfo.get(ACCOUNT_TYPE_KEY);
        if (value != null && !value.isEmpty() && value.toLowerCase().contains("admin")) {
            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_IS_ADMIN,
                    1, osAccount, host, regFile));
        }

        value = userInfo.get(USER_COMMENT_KEY);
        if (value != null && !value.isEmpty()) {
            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_DESCRIPTION,
                    value, osAccount, host, regFile));
        }

        value = userInfo.get(INTERNET_NAME_KEY);
        if (value != null && !value.isEmpty()) {
            addEmailAccount(regFile, value, ingestJobId);

            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_EMAIL,
                    value, osAccount, host, regFile));
        }

        // FULL_NAME_KEY and NAME_KEY appear to be the same value.
        String fullName = null;
        value = userInfo.get(FULL_NAME_KEY);
        if (value != null && !value.isEmpty()) {
            fullName = value;
        } else {
            value = userInfo.get(NAME_KEY);
            if (value != null && !value.isEmpty()) {
                fullName = value;
            }
        }

        value = userInfo.get(PWD_RESET_KEY);
        if (value != null && !value.isEmpty() && !value.equals(NEVER_DATE)) {
            Long time = parseRegRipTime(value);
            if (time != null) {
                attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_PASSWORD_RESET,
                        time, osAccount, host, regFile));
            }
        }
        
        value = userInfo.get(SECURITY_QUESTION_1);
        if (value != null && !value.isEmpty()) {
            BlackboardAttribute.Type securityQuestionAttributeType = null;
            try {
                    securityQuestionAttributeType = tskCase.getBlackboard().getOrAddAttributeType(SAM_SECURITY_QUESTION_1,
                            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                            Bundle.Sam_Security_Question_1_Attribute_Display_Name());
            } catch (BlackboardException ex) {
                    throw new TskCoreException(String.format("Failed to get key attribute %s", SAM_SECURITY_QUESTION_1), ex);
            }
            attributes.add(createOsAccountAttribute(securityQuestionAttributeType, value, osAccount, host, regFile));
        }
        
        value = userInfo.get(SECURITY_ANSWER_1);
        if (value != null && !value.isEmpty()) {
            BlackboardAttribute.Type securityAnswerAttributeType = null;
            try {
                    securityAnswerAttributeType = tskCase.getBlackboard().getOrAddAttributeType(SAM_SECURITY_ANSWER_1,
                            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                            Bundle.Sam_Security_Answer_1_Attribute_Display_Name());
            } catch (BlackboardException ex) {
                    throw new TskCoreException(String.format("Failed to get key attribute %s", SAM_SECURITY_ANSWER_1), ex);
            }
            attributes.add(createOsAccountAttribute(securityAnswerAttributeType, value, osAccount, host, regFile));
        }
        
        value = userInfo.get(SECURITY_QUESTION_2);
        if (value != null && !value.isEmpty()) {
            BlackboardAttribute.Type securityQuestionAttributeType = null;
            try {
                    securityQuestionAttributeType = tskCase.getBlackboard().getOrAddAttributeType(SAM_SECURITY_QUESTION_2,
                            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                            Bundle.Sam_Security_Question_2_Attribute_Display_Name());
            } catch (BlackboardException ex) {
                    throw new TskCoreException(String.format("Failed to get key attribute %s", SAM_SECURITY_QUESTION_2), ex);
            }
            attributes.add(createOsAccountAttribute(securityQuestionAttributeType, value, osAccount, host, regFile));
        }
        
        value = userInfo.get(SECURITY_ANSWER_2);
        if (value != null && !value.isEmpty()) {
            BlackboardAttribute.Type securityAnswerAttributeType = null;
            try {
                    securityAnswerAttributeType = tskCase.getBlackboard().getOrAddAttributeType(SAM_SECURITY_ANSWER_2,
                            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                            Bundle.Sam_Security_Answer_2_Attribute_Display_Name());
            } catch (BlackboardException ex) {
                    throw new TskCoreException(String.format("Failed to get key attribute %s", SAM_SECURITY_ANSWER_2), ex);
            }
            attributes.add(createOsAccountAttribute(securityAnswerAttributeType, value, osAccount, host, regFile));
        }
        
        value = userInfo.get(SECURITY_QUESTION_3);
        if (value != null && !value.isEmpty()) {
            BlackboardAttribute.Type securityQuestionAttributeType = null;
            try {
                    securityQuestionAttributeType = tskCase.getBlackboard().getOrAddAttributeType(SAM_SECURITY_QUESTION_3,
                            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                            Bundle.Sam_Security_Question_2_Attribute_Display_Name());
            } catch (BlackboardException ex) {
                    throw new TskCoreException(String.format("Failed to get key attribute %s", SAM_SECURITY_QUESTION_3), ex);
            }
            attributes.add(createOsAccountAttribute(securityQuestionAttributeType, value, osAccount, host, regFile));
        }
        
        value = userInfo.get(SECURITY_ANSWER_3);
        if (value != null && !value.isEmpty()) {
            BlackboardAttribute.Type securityAnswerAttributeType = null;
            try {
                    securityAnswerAttributeType = tskCase.getBlackboard().getOrAddAttributeType(SAM_SECURITY_ANSWER_3,
                            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                            Bundle.Sam_Security_Answer_3_Attribute_Display_Name());
            } catch (BlackboardException ex) {
                    throw new TskCoreException(String.format("Failed to get key attribute %s", SAM_SECURITY_ANSWER_3), ex);
            }
            attributes.add(createOsAccountAttribute(securityAnswerAttributeType, value, osAccount, host, regFile));
        }
        
        value = userInfo.get(PASSWORD_HINT);
        if (value != null && !value.isEmpty()) {
            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_PASSWORD_HINT,
                    value, osAccount, host, regFile));
        }

        value = userInfo.get(PWD_FAILE_KEY);
        if (value != null && !value.isEmpty() && !value.equals(NEVER_DATE)) {
            Long time = parseRegRipTime(value);
            if (time != null) {
                attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_PASSWORD_FAIL,
                        time, osAccount, host, regFile));
            }
        }

        String settingString = getSettingsFromMap(PASSWORD_SETTINGS_FLAGS, userInfo);
        if (!settingString.isEmpty()) {
            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_PASSWORD_SETTINGS,
                    settingString, osAccount, host, regFile));
        }

        settingString = getSettingsFromMap(ACCOUNT_SETTINGS_FLAGS, userInfo);
        if (!settingString.isEmpty()) {
            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_ACCOUNT_SETTINGS,
                    settingString, osAccount, host, regFile));
        }

        settingString = getSettingsFromMap(ACCOUNT_TYPE_FLAGS, userInfo);
        if (!settingString.isEmpty()) {
            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_FLAG,
                    settingString, osAccount, host, regFile));
        }

        if (groupList != null && groupList.isEmpty()) {
            String groups = groupList.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));

            attributes.add(createOsAccountAttribute(ATTRIBUTE_TYPE.TSK_GROUPS,
                    groups, osAccount, host, regFile));
        }

        // add the attributes to account.
        OsAccountManager accountMgr = tskCase.getOsAccountManager();
        accountMgr.addExtendedOsAccountAttributes(osAccount, attributes);

        // update the loginname
        accountMgr.updateCoreWindowsOsAccountAttributes(osAccount, null, loginName, null, host);

        // update other standard attributes  -  fullname, creationdate
        accountMgr.updateStandardOsAccountAttributes(osAccount, fullName, null, null, creationTime);

    }

    /**
     * Create comma separated list from the set values for the given keys.
     *
     * @param keys List of map keys.
     * @param map  Data map.
     *
     * @return Comma separated String of values.
     */
    private String getSettingsFromMap(String[] keys, Map<String, String> map) {
        List<String> settingsList = new ArrayList<>();
        for (String setting : keys) {
            if (map.containsKey(setting)) {
                settingsList.add(setting);
            }
        }

        if (!settingsList.isEmpty()) {
            return settingsList.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }

        return "";
    }

    /**
     * Helper for constructing a new OsAccountAttribute
     *
     * @param type      Attribute type
     * @param value     The value to store
     * @param osAccount The OsAccount this attribute belongs to
     * @param host      The Host related to the OsAccount
     * @param file      The source where the attribute was found.
     *
     * @return Newly created OsACcountAttribute
     */
    private OsAccountAttribute createOsAccountAttribute(BlackboardAttribute.Type type, String value, OsAccount osAccount, Host host, AbstractFile file) {
        return osAccount.new OsAccountAttribute(type, value, osAccount, host, file);
    }

    /**
     * Helper for constructing a new OsAccountAttribute
     *
     * @param type      Attribute type
     * @param value     The value to store
     * @param osAccount The OsAccount this attribute belongs to
     * @param host      The Host related to the OsAccount
     * @param file      The source where the attribute was found.
     *
     * @return Newly created OsACcountAttribute
     */
    private OsAccountAttribute createOsAccountAttribute(BlackboardAttribute.ATTRIBUTE_TYPE type, String value, OsAccount osAccount, Host host, AbstractFile file) {
        return osAccount.new OsAccountAttribute(new BlackboardAttribute.Type(type), value, osAccount, host, file);
    }

    /**
     * Helper for constructing a new OsAccountAttribute
     *
     * @param type      Attribute type
     * @param value     The value to store
     * @param osAccount The OsAccount this attribute belongs to
     * @param host      The Host related to the OsAccount
     * @param file      The source where the attribute was found.
     *
     * @return Newly created OsACcountAttribute
     */
    private OsAccountAttribute createOsAccountAttribute(BlackboardAttribute.ATTRIBUTE_TYPE type, Long value, OsAccount osAccount, Host host, AbstractFile file) {
        return osAccount.new OsAccountAttribute(new BlackboardAttribute.Type(type), value, osAccount, host, file);
    }

    /**
     * Helper for constructing a new OsAccountAttribute
     *
     * @param type      Attribute type
     * @param value     The value to store
     * @param osAccount The OsAccount this attribute belongs to
     * @param host      The Host related to the OsAccount
     * @param file      The source where the attribute was found.
     *
     * @return Newly created OsACcountAttribute
     */
    private OsAccountAttribute createOsAccountAttribute(BlackboardAttribute.ATTRIBUTE_TYPE type, Integer value, OsAccount osAccount, Host host, AbstractFile file) {
        return osAccount.new OsAccountAttribute(new BlackboardAttribute.Type(type), value, osAccount, host, file);
    }

    /**
     * Adds an account instance for the given data source if one does not
     * already exist.
     *
     * @param accountMgr
     * @param osAccount
     * @param dataSource
     *
     * @throws TskCoreException
     */
    private void addAccountInstance(OsAccountManager accountMgr, OsAccount osAccount, DataSource dataSource) throws TskCoreException {
        accountMgr.newOsAccountInstance(osAccount, dataSource, OsAccountInstance.OsAccountInstanceType.LAUNCHED);
    }

    /**
     * Add the domainId of the given account sid to the sam domain id list.
     *
     * @param sid OS account sid
     */
    private void addSIDToSAMList(String sid) {
        String relativeID = stripRelativeIdentifierFromSID(sid);
        if (!relativeID.isEmpty() && !samDomainIDsList.contains(relativeID)) {
            samDomainIDsList.add(relativeID);
        }
    }

    /**
     * Returns true if the domain id of the os account sid is in the list of
     * domain ids seen when parsing the sam file.
     *
     * @param osAccountSID
     *
     * @return If the domainID is in the same file list.
     */
    private boolean isDomainIdInSAMList(String osAccountSID) {
        String relativeID = stripRelativeIdentifierFromSID(osAccountSID);
        return samDomainIDsList.contains(relativeID);
    }
    
    // Structure to keep the OSInfo meta data so that only one instance
    // of TSK_OS_INFO is created per RA run.
    private class OSInfo {
        private String compName = null;
        private String progName = "Windows";
        private String processorArchitecture = null;
        private String tempDir = null;
        private String domain = null;
        private Long installtime = null;
        private String systemRoot = null;
        private String productId = null;
        private String regOwner = null;
        private String regOrg = null;
        
        private OSInfo() {}
        
        void createOSInfo() {
            try{
                String parentModuleName = RecentActivityExtracterModuleFactory.getModuleName();
                ArrayList<BlackboardArtifact> results = tskCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_OS_INFO, context.getDataSource().getId());
                
                if (results.isEmpty()) {
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                    if (compName != null && !compName.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, parentModuleName, compName));
                    }
                    if (domain != null && !domain.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, parentModuleName, domain));
                    }
                    if (progName != null && !progName.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, parentModuleName, progName));
                    }
                    if (processorArchitecture != null && !processorArchitecture.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROCESSOR_ARCHITECTURE, parentModuleName, processorArchitecture));
                    }
                    if (tempDir != null && !tempDir.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEMP_DIR, parentModuleName, tempDir));
                    }
                    if (installtime != null) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, parentModuleName, installtime));
                    }
                    if (systemRoot != null && !systemRoot.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH, parentModuleName, systemRoot));
                    }
                    if (productId != null && !productId.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PRODUCT_ID, parentModuleName, productId));
                    }
                    if (regOwner != null && !regOwner.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_OWNER, parentModuleName, regOwner));
                    }
                    if (regOrg != null && !regOrg.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ORGANIZATION, parentModuleName, regOrg));
                    }
                    
                    postArtifact(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_OS_INFO, context.getDataSource(), bbattributes));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to create default OS_INFO artifact", ex); //NON-NLS
            }
        }

        void setCompName(String compName) {
            if(this.compName == null || this.compName.isEmpty()) {
                this.compName = compName;
            }
        }

        void setOsName(String progName) {
            if(progName != null && !progName.isEmpty()) {
                this.progName = progName;
            }
        }

        void setProcessorArchitecture(String processorArchitecture) {
            if(this.processorArchitecture == null || this.processorArchitecture.isEmpty()) {
                this.processorArchitecture = processorArchitecture;
            }
        }

        void setTempDir(String tempDir) {
            if(this.tempDir == null || this.tempDir.isEmpty()) {
                this.tempDir = tempDir;
            }
        }

        void setDomain(String domain) {
            if(this.domain == null || this.domain.isEmpty()) {
                this.domain = domain;
            }
        }

        void setInstalltime(Long installtime) {
            if(this.domain == null) {
                this.installtime = installtime;
            }
        }

        void setSystemRoot(String systemRoot) {
            if(this.systemRoot == null || this.systemRoot.isEmpty()) {
                this.systemRoot = systemRoot;
            }
        }

        void setProductId(String productId) {
            if(this.productId == null || this.productId.isEmpty()) {
                this.productId = productId;
            }
        }

        void setRegOwner(String regOwner) {
            if(this.regOwner == null || this.regOwner.isEmpty()) {
                this.regOwner = regOwner;
            }
        }

        void setRegOrg(String regOrg) {
            if(this.regOrg == null || this.regOrg.isEmpty()) {
                this.regOrg = regOrg;
            }
        } 
    }

}
