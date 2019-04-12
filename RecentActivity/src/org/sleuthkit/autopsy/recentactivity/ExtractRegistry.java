/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2019 Basis Technology Corp.
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

import java.io.*;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.recentactivity.UsbDeviceIdMapper.USBInfo;
import org.sleuthkit.datamodel.*;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import java.nio.file.Path;
import static java.util.TimeZone.getTimeZone;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;

/**
 * Extract windows registry data using regripper. Runs two versions of
 * regripper. One is the generally available set of plug-ins and the second is a
 * set that were customized for Autopsy to produce a more structured output of
 * XML so that we can parse and turn into blackboard artifacts.
 */
@NbBundle.Messages({
    "RegRipperNotFound=Autopsy RegRipper executable not found.",
    "RegRipperFullNotFound=Full version RegRipper executable not found.",
    "Progress_Message_Analyze_Registry=Analyzing Registry Files"
})
class ExtractRegistry extends Extract {

    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private String RR_PATH;
    private String RR_FULL_PATH;
    private Path rrHome;  // Path to the Autopsy version of RegRipper
    private Path rrFullHome; // Path to the full version of RegRipper
    private Content dataSource;
    private IngestJobContext context;
    final private static UsbDeviceIdMapper USB_MAPPER = new UsbDeviceIdMapper();
    final private static String RIP_EXE = "rip.exe";
    final private static String RIP_PL = "rip.pl";
    final private static int MS_IN_SEC = 1000;
    final private static String NEVER_DATE = "Never";
    final private static String SECTION_DIVIDER = "-------------------------";
    private final List<String> rrCmd = new ArrayList<>();
    private final List<String> rrFullCmd = new ArrayList<>();

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
        RR_PATH = rrHome.resolve(executableToRun).toString();
        rrFullHome = rrFullRoot.toPath();
        RR_FULL_PATH = rrFullHome.resolve(executableToRun).toString();

        if (!(new File(RR_PATH).exists())) {
            throw new IngestModuleException(Bundle.RegRipperNotFound());
        }
        if (!(new File(RR_FULL_PATH).exists())) {
            throw new IngestModuleException(Bundle.RegRipperFullNotFound());
        }
        if (PlatformUtil.isWindowsOS()) {
            rrCmd.add(RR_PATH);
            rrFullCmd.add(RR_FULL_PATH);
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
            rrCmd.add(RR_PATH);
            rrFullCmd.add(perl);
            rrFullCmd.add(RR_FULL_PATH);
        }
    }

    /**
     * Search for the registry hives on the system.
     */
    private List<AbstractFile> findRegistryFiles() {
        List<AbstractFile> allRegistryFiles = new ArrayList<>();
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();

        // find the user-specific ntuser-dat files
        try {
            allRegistryFiles.addAll(fileManager.findFiles(dataSource, "ntuser.dat")); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'ntuser.dat' file."); //NON-NLS
        }

        // find the system hives'
        String[] regFileNames = new String[]{"system", "software", "security", "sam"}; //NON-NLS
        for (String regFileName : regFileNames) {
            try {
                allRegistryFiles.addAll(fileManager.findFiles(dataSource, regFileName, "/system32/config")); //NON-NLS
            } catch (TskCoreException ex) {
                String msg = NbBundle.getMessage(this.getClass(),
                        "ExtractRegistry.findRegFiles.errMsg.errReadingFile", regFileName);
                logger.log(Level.WARNING, msg);
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
            if (regOutputFiles.autopsyPlugins.isEmpty() == false) {
                if (parseAutopsyPluginOutput(regOutputFiles.autopsyPlugins, regFile) == false) {
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.failedParsingResults",
                                    this.getName(), regFileName));
                }
            }

            // create a report for the full output
            if (!regOutputFiles.fullPlugins.isEmpty()) {
                //parse the full regripper output from SAM hive files
                if (regFileNameLocal.toLowerCase().contains("sam")) {
                    if (parseSamPluginOutput(regOutputFiles.fullPlugins, regFile) == false) {
                        this.addErrorMessage(
                                NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.failedParsingResults",
                                        this.getName(), regFileName));
                    }
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

    private class RegOutputFiles {

        public String autopsyPlugins = "";
        public String fullPlugins = "";
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
        }
        return regOutputFiles;
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
            ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to run RegRipper", ex); //NON-NLS
            this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile", this.getName()));
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
            // Add all "usb" dataType nodes to collection of BlackboardArtifacts 
            // that we will submit in a ModuleDataEvent for additional processing.
            Collection<BlackboardArtifact> usbBBartifacts = new ArrayList<>();
            // Add all "ssid" dataType nodes to collection of BlackboardArtifacts 
            // that we will submit in a ModuleDataEvent for additional processing.
            Collection<BlackboardArtifact> wifiBBartifacts = new ArrayList<>();
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
                    try {
                        Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(etime).getTime();
                        mtime = epochtime;
                        String Tempdate = mtime.toString();
                        mtime = Long.valueOf(Tempdate) / MS_IN_SEC;
                    } catch (ParseException ex) {
                        logger.log(Level.WARNING, "Failed to parse epoch time when parsing the registry."); //NON-NLS
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
                String winver = "";

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

                                String value = artnode.getTextContent().trim();
                                String name = artnode.getAttribute("name"); //NON-NLS
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
                                        try {
                                            Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(value).getTime();
                                            installtime = epochtime;
                                            String Tempdate = installtime.toString();
                                            installtime = Long.valueOf(Tempdate) / MS_IN_SEC;
                                        } catch (ParseException e) {
                                            logger.log(Level.SEVERE, "RegRipper::Conversion on DateTime -> ", e); //NON-NLS
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

                                // index the artifact for keyword search
                                this.indexArtifact(bbart);
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
                        String procId = "";
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
                                        procId = value;
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

                                // index the artifact for keyword search
                                this.indexArtifact(bbart);
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

                                // index the artifact for keyword search
                                this.indexArtifact(bbart);
                            } else {
                                results.get(0).addAttributes(bbattributes);
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error adding os info artifact to blackboard."); //NON-NLS
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

                                            // index the artifact for keyword search
                                            this.indexArtifact(bbart);
                                            // add to collection for ModuleDataEvent
                                            usbBBartifacts.add(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding device attached artifact to blackboard."); //NON-NLS
                                        }
                                        break;
                                    case "uninstall": //NON-NLS
                                        Long itemMtime = null;
                                        try {
                                            Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(artnode.getAttribute("mtime")).getTime(); //NON-NLS
                                            itemMtime = epochtime;
                                            itemMtime = itemMtime / MS_IN_SEC;
                                        } catch (ParseException e) {
                                            logger.log(Level.WARNING, "Failed to parse epoch time for installed program artifact."); //NON-NLS
                                        }

                                        try {
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, parentModuleName, value));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, parentModuleName, itemMtime));
                                            BlackboardArtifact bbart = regFile.newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
                                            bbart.addAttributes(bbattributes);

                                            // index the artifact for keyword search
                                            this.indexArtifact(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard."); //NON-NLS
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

                                            // index the artifact for keyword search
                                            this.indexArtifact(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding recent object artifact to blackboard."); //NON-NLS
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
                                                logger.log(Level.WARNING, "Error getting existing os account artifact", ex);
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
                                            // index the artifact for keyword search
                                            this.indexArtifact(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding account artifact to blackboard."); //NON-NLS
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
                                            // index the artifact for keyword search
                                            this.indexArtifact(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding network artifact to blackboard."); //NON-NLS
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
                                            // index the artifact for keyword search
                                            this.indexArtifact(bbart);
                                            wifiBBartifacts.add(bbart);
                                        } catch (TskCoreException ex) {
                                            logger.log(Level.SEVERE, "Error adding SSID artifact to blackboard."); //NON-NLS
                                        }
                                        break;
                                    case "shellfolders": // NON-NLS
                                        // The User Shell Folders subkey stores the paths to Windows Explorer folders for the current user of the computer
                                        // (https://technet.microsoft.com/en-us/library/Cc962613.aspx).
                                        // No useful information. Skip.
                                        break;

                                    default:
                                        logger.log(Level.WARNING, "Unrecognized node name: {0}", dataType); //NON-NLS
                                        break;
                                }
                            }
                        }
                        break;
                }
            } // for
            if (!usbBBartifacts.isEmpty()) {
                IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(moduleName, BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED, usbBBartifacts));
            }
            if (!wifiBBartifacts.isEmpty()) {
                IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(moduleName, BlackboardArtifact.ARTIFACT_TYPE.TSK_WIFI_NETWORK, wifiBBartifacts));
            }
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Error finding the registry file.", ex); //NON-NLS
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, "Error parsing the registry XML.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error building the document parser.", ex); //NON-NLS
        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, "Error configuring the registry parser.", ex); //NON-NLS
        } finally {
            try {
                if (fstream != null) {
                    fstream.close();
                }
            } catch (IOException ex) {
            }
        }
        return false;
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
        String parentModuleName = RecentActivityExtracterModuleFactory.getModuleName();
        SimpleDateFormat regRipperTimeFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy 'Z'");
        regRipperTimeFormat.setTimeZone(getTimeZone("GMT"));
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(regfile))) {
            // Read the file in and create a Document and elements
            String userInfoSection = "User Information";
            String previousLine = null;
            String line = bufferedReader.readLine();
            Set<UserInfo> userSet = new HashSet<>();
            while (line != null) {
                if (line.contains(SECTION_DIVIDER) && previousLine != null) {
                    if (previousLine.contains(userInfoSection)) {
                        readUsers(bufferedReader, userSet);
                    } 
                }
                previousLine = line;
                line = bufferedReader.readLine();
            }
            Map<String, UserInfo> userInfoMap = new HashMap<>();
            //load all the user info which was read into a map
            for (UserInfo userInfo : userSet) {
                userInfoMap.put(userInfo.getUserSid(), userInfo);
            }
            //get all existing OS account artifacts
            List<BlackboardArtifact> existingOsAccounts = tskCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_OS_ACCOUNT);
            for (BlackboardArtifact osAccount : existingOsAccounts) {
                //if the OS Account artifact was from the same data source check the user id
                if (osAccount.getDataSource().getId() == regAbstractFile.getDataSourceObjectId()) {
                    BlackboardAttribute existingUserId = osAccount.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_USER_ID));
                    if (existingUserId != null) {
                        UserInfo userInfo = userInfoMap.remove(existingUserId.getValueString().trim());
                        //if the existing user id matches a user id which we parsed information for check if that information exists and if it doesn't add it
                        if (userInfo != null) {
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                            if (userInfo.getAccountCreatedDate() != null && !userInfo.getAccountCreatedDate().equals(NEVER_DATE)) {
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                                        parentModuleName, regRipperTimeFormat.parse(userInfo.getAccountCreatedDate()).getTime() / MS_IN_SEC));
                            }
                            if (userInfo.getLastLoginDate() != null && !userInfo.getLastLoginDate().equals(NEVER_DATE)) {
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                                        parentModuleName, regRipperTimeFormat.parse(userInfo.getLastLoginDate()).getTime() / MS_IN_SEC));
                            }
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT,
                                    parentModuleName, userInfo.getLoginCount()));
                            osAccount.addAttributes(bbattributes);
                        }
                    }
                }
            }
            //add remaining userinfos as accounts;
            for (String userId : userInfoMap.keySet()) {
                UserInfo userInfo = userInfoMap.get(userId);
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                BlackboardArtifact bbart = regAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_OS_ACCOUNT);
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME,
                        parentModuleName, userInfo.getUserName()));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_ID,
                        parentModuleName, userId));
                if (userInfo.getAccountCreatedDate() != null && !userInfo.getAccountCreatedDate().equals(NEVER_DATE)) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                            parentModuleName, regRipperTimeFormat.parse(userInfo.getAccountCreatedDate()).getTime() / MS_IN_SEC));
                }
                if (userInfo.getLastLoginDate() != null && !userInfo.getLastLoginDate().equals(NEVER_DATE)) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                            parentModuleName, regRipperTimeFormat.parse(userInfo.getLastLoginDate()).getTime() / MS_IN_SEC));
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT,
                        parentModuleName, userInfo.getLoginCount()));
                bbart.addAttributes(bbattributes);
                // index the artifact for keyword search
                this.indexArtifact(bbart);
            }
            //store set of attributes to make artifact for later in collection of artifact like objects
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Error finding the registry file.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error building the document parser: {0}", ex); //NON-NLS
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, "Error parsing the the date from the registry file", ex); //NON-NLS
        } catch (TskCoreException ex) {
             logger.log(Level.SEVERE, "Error updating TSK_OS_ACCOUNT artifacts to include newly parsed data.", ex); //NON-NLS
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
    private void readUsers(BufferedReader bufferedReader, Set<UserInfo> users) throws IOException {
        String userNameLabel = "Username        :";
        String sidLabel = "SID             :";
        String accountCreatedLabel = "Account Created :";
        String loginCountLabel = "Login Count     :";
        String lastLoginLabel = "Last Login Date :";
        String line = bufferedReader.readLine();
        //read until end of file or next section divider
        String userName = "";
        while (line != null && !line.contains(SECTION_DIVIDER)) {
            //when a user name field exists read the name and id number
            if (line.contains(userNameLabel)) {
                String userNameAndIdString = line.replace(userNameLabel, "");
                userName = userNameAndIdString.substring(0, userNameAndIdString.lastIndexOf('[')).trim();
            }
            else if (line.contains(sidLabel) && !userName.isEmpty()){
                String sid = line.replace(sidLabel, "").trim();
                UserInfo userInfo = new UserInfo(userName, sid);
                //continue reading this users information until end of file or a blank line between users
                line = bufferedReader.readLine();
                while (line != null && !line.isEmpty()) {
                    if (line.contains(accountCreatedLabel)) {
                        userInfo.setAccountCreatedDate(line.replace(accountCreatedLabel, "").trim());
                    } else if (line.contains(loginCountLabel)) {
                        userInfo.setLoginCount(Integer.parseInt(line.replace(loginCountLabel, "").trim()));
                    } else if (line.contains(lastLoginLabel)) {
                        userInfo.setLastLoginDate(line.replace(lastLoginLabel, "").trim());
                    }
                    line = bufferedReader.readLine();
                }
                users.add(userInfo);
                userName = "";
            }
            line = bufferedReader.readLine();
        }
    }

    @Override
    public void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        this.context = context;
        
        progressBar.progress(Bundle.Progress_Message_Analyze_Registry());
        analyzeRegistryFiles();

    }

    /**
     * Class for organizing information associated with a TSK_OS_ACCOUNT before
     * the artifact is created.
     */
    private class UserInfo {

        private final String userName;
        private final String userSid;
        private String lastLoginDate;
        private String accountCreatedDate;
        private int loginCount = 0;

        /**
         * Create a UserInfo object
         *
         * @param name         - the os user account name
         * @param userSidString - the SID for the user account
         */
        private UserInfo(String name, String userSidString) {
            userName = name;
            userSid = userSidString;
        }

        /**
         * Get the user name.
         *
         * @return the userName
         */
        String getUserName() {
            return userName;
        }

        /**
         * Get the user SID.
         *
         * @return the user SID
         */
        String getUserSid() {
            return userSid;
        }

        /**
         * Get the last login date for the user
         *
         * @return the lastLoginDate
         */
        String getLastLoginDate() {
            return lastLoginDate;
        }

        /**
         * Set the last login date for the users
         *
         * @param lastLoginDate the lastLoginDate to set
         */
        void setLastLoginDate(String lastLoginDate) {
            this.lastLoginDate = lastLoginDate;
        }

        /**
         * Get the account creation date.
         *
         * @return the accountCreatedDate
         */
        String getAccountCreatedDate() {
            return accountCreatedDate;
        }

        /**
         * Set the account creation date.
         *
         * @param accountCreatedDate the accountCreatedDate to set
         */
        void setAccountCreatedDate(String accountCreatedDate) {
            this.accountCreatedDate = accountCreatedDate;
        }

        /**
         * Get the number of times the user logged in.
         *
         * @return the loginCount
         */
        int getLoginCount() {
            return loginCount;
        }

        /**
         * Set the number of times the user logged in.
         *
         * @param loginCount the loginCount to set
         */
        void setLoginCount(int loginCount) {
            this.loginCount = loginCount;
        }

    }
}
