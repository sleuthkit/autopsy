 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2014 Basis Technology Corp.
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

/**
 * Extract windows registry data using regripper.
 * Runs two versions of regripper. One is the generally available set of plug-ins 
 * and the second is a set that were customized for Autopsy to produce a more structured
 * output of XML so that we can parse and turn into blackboard artifacts. 
 */
class ExtractRegistry extends Extract {

    private Logger logger = Logger.getLogger(this.getClass().getName());
    private String RR_PATH;
    private String RR_FULL_PATH;
    private boolean rrFound = false;    // true if we found the Autopsy-specific version of regripper
    private boolean rrFullFound = false; // true if we found the full version of regripper
    final private static String MODULE_VERSION = "1.0";
    
    private Content dataSource;
    private IngestJobContext context;

    //hide public constructor to prevent from instantiation by ingest module loader
    ExtractRegistry() {
        moduleName = NbBundle.getMessage(ExtractIE.class, "ExtractRegistry.moduleName.text");
        final File rrRoot = InstalledFileLocator.getDefault().locate("rr", ExtractRegistry.class.getPackage().getName(), false); //NON-NLS
        if (rrRoot == null) {
            logger.log(Level.SEVERE, "RegRipper not found"); //NON-NLS
            rrFound = false;
            return;
        } else {
            rrFound = true;
        }
        
        final String rrHome = rrRoot.getAbsolutePath();
        logger.log(Level.INFO, "RegRipper home: {0}", rrHome); //NON-NLS

        if (PlatformUtil.isWindowsOS()) {
            RR_PATH = rrHome + File.separator + "rip.exe"; //NON-NLS
        } else {
            RR_PATH = "perl " + rrHome + File.separator + "rip.pl"; //NON-NLS
        }
        
        final File rrFullRoot = InstalledFileLocator.getDefault().locate("rr-full", ExtractRegistry.class.getPackage().getName(), false); //NON-NLS
        if (rrFullRoot == null) {
            logger.log(Level.SEVERE, "RegRipper Full not found"); //NON-NLS
            rrFullFound = false;
        } else {
            rrFullFound = true;
        }
        
        final String rrFullHome = rrFullRoot.getAbsolutePath();
        logger.log(Level.INFO, "RegRipper Full home: {0}", rrFullHome); //NON-NLS

        if (PlatformUtil.isWindowsOS()) {
            RR_FULL_PATH = rrFullHome + File.separator + "rip.exe"; //NON-NLS
        } else {
            RR_FULL_PATH = "perl " + rrFullHome + File.separator + "rip.pl"; //NON-NLS
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
        } 
        catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'ntuser.dat' file."); //NON-NLS
        }

        // find the system hives'
        String[] regFileNames = new String[] {"system", "software", "security", "sam"}; //NON-NLS
        for (String regFileName : regFileNames) {
            try {
                allRegistryFiles.addAll(fileManager.findFiles(dataSource, regFileName, "/system32/config")); //NON-NLS
            } 
            catch (TskCoreException ex) {
                String msg = NbBundle.getMessage(this.getClass(),
                                                 "ExtractRegistry.findRegFiles.errMsg.errReadingFile", regFileName);
                logger.log(Level.WARNING, msg);
                this.addErrorMessage(this.getName() + ": " + msg);
            }
        }
        return allRegistryFiles;
    }
    
    /**
     * Identifies registry files in the database by mtimeItem, runs regripper on them, and parses the output.
     */
    private void analyzeRegistryFiles() {        
        List<AbstractFile> allRegistryFiles = findRegistryFiles();
        
        // open the log file
        FileWriter logFile = null;
        try {
            logFile = new FileWriter(RAImageIngestModule.getRAOutputPath(currentCase, "reg") + File.separator + "regripper-info.txt"); //NON-NLS
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        UsbDeviceIdMapper usbMapper = new UsbDeviceIdMapper();
        
        int j = 0;
        for (AbstractFile regFile : allRegistryFiles) {
            String regFileName = regFile.getName();
            String regFileNameLocal = RAImageIngestModule.getRATempPath(currentCase, "reg") + File.separator + regFileName;
            String outputPathBase = RAImageIngestModule.getRAOutputPath(currentCase, "reg") + File.separator + regFileName + "-regripper-" + Integer.toString(j++); //NON-NLS
            File regFileNameLocalFile = new File(regFileNameLocal);
            try {
                ContentUtils.writeToFile(regFile, regFileNameLocalFile);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the temp registry file. {0}", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.errMsg.errWritingTemp",
                                            this.getName(), regFileName));
                continue;
            }
            
            if (context.isJobCancelled()) {
                break;
            }
           
            try {
                if (logFile != null) {
                    logFile.write(Integer.toString(j-1) + "\t" + regFile.getUniquePath() + "\n");
                }
            } 
            catch (TskCoreException | IOException ex) {
                java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            logger.log(Level.INFO, moduleName + "- Now getting registry information from " + regFileNameLocal); //NON-NLS
            RegOutputFiles regOutputFiles = executeRegRip(regFileNameLocal, outputPathBase);
            
            if (context.isJobCancelled()) {
                break;
            }
            
            // parse the autopsy-specific output
            if (regOutputFiles.autopsyPlugins.isEmpty() == false) {
                if (parseAutopsyPluginOutput(regOutputFiles.autopsyPlugins, regFile.getId(), usbMapper) == false) {
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.failedParsingResults",
                                                this.getName(), regFileName));
                }
            }

            // create a RAW_TOOL artifact for the full output
            if (regOutputFiles.fullPlugins.isEmpty() == false) {
                try {
                    BlackboardArtifact art = regFile.newArtifact(ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID());
                    BlackboardAttribute att = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                                      NbBundle.getMessage(this.getClass(),
                                                                                          "ExtractRegistry.parentModuleName.noSpace"),
                                                                      NbBundle.getMessage(this.getClass(),
                                                                                          "ExtractRegistry.programName"));
                    art.addAttribute(att);

                    FileReader fread = new FileReader(regOutputFiles.fullPlugins);
                    BufferedReader input = new BufferedReader(fread);

                    StringBuilder sb = new StringBuilder();
                    try {
                        while (true) {
                            String s = input.readLine();
                            if (s == null) {
                                break;
                            }
                            sb.append(s).append("\n");
                        }
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        try {
                            input.close();
                        } catch (IOException ex) {
                            logger.log(Level.WARNING, "Failed to close reader.", ex); //NON-NLS
                        }
                    }
                    att = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(),
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "ExtractRegistry.parentModuleName.noSpace"), sb.toString());
                    art.addAttribute(att);
                } catch (FileNotFoundException ex) {
                    this.addErrorMessage(NbBundle.getMessage(this.getClass(),
                                                             "ExtractRegistry.analyzeRegFiles.errMsg.errReadingRegFile",
                                                             this.getName(), regOutputFiles.fullPlugins));
                    java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
                } catch (TskCoreException ex) {
                    // TODO - add error message here?
                    java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
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
            java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private class RegOutputFiles {
        public String autopsyPlugins = "";
        public String fullPlugins = "";
    }

    /**
     * Execute regripper on the given registry.
     * @param regFilePath Path to local copy of registry
     * @param outFilePathBase  Path to location to save output file to.  Base mtimeItem that will be extended on
     */
    private RegOutputFiles executeRegRip(String regFilePath, String outFilePathBase) {
        String autopsyType = "";    // Type argument for rr for autopsy-specific modules
        String fullType = "";   // Type argument for rr for full set of modules

        RegOutputFiles regOutputFiles = new RegOutputFiles();
        
        if (regFilePath.toLowerCase().contains("system")) { //NON-NLS
            autopsyType = "autopsysystem"; //NON-NLS
            fullType = "system"; //NON-NLS
        } 
        else if (regFilePath.toLowerCase().contains("software")) { //NON-NLS
            autopsyType = "autopsysoftware"; //NON-NLS
            fullType = "software"; //NON-NLS
        } 
        else if (regFilePath.toLowerCase().contains("ntuser")) { //NON-NLS
            autopsyType = "autopsyntuser"; //NON-NLS
            fullType = "ntuser"; //NON-NLS
        }  
        else if (regFilePath.toLowerCase().contains("sam")) { //NON-NLS
            fullType = "sam"; //NON-NLS
        } 
        else if (regFilePath.toLowerCase().contains("security")) { //NON-NLS
            fullType = "security"; //NON-NLS
        } 
        else {
            return regOutputFiles;
        }
        
        // run the autopsy-specific set of modules
        if (!autopsyType.isEmpty() && rrFound) {
            // TODO - add error messages
            Writer writer = null;
            ExecUtil execRR = null;
            try {
                regOutputFiles.autopsyPlugins = outFilePathBase + "-autopsy.txt"; //NON-NLS
                logger.log(Level.INFO, "Writing RegRipper results to: " + regOutputFiles.autopsyPlugins); //NON-NLS
                writer = new FileWriter(regOutputFiles.autopsyPlugins);
                execRR = new ExecUtil();
                execRR.execute(writer, RR_PATH,
                        "-r", regFilePath, "-f", autopsyType); //NON-NLS
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to RegRipper and process parse some registry files.", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile",
                                            this.getName()));
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "RegRipper has been interrupted, failed to parse registry.", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile2",
                                            this.getName()));
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, "Error closing output writer after running RegRipper", ex); //NON-NLS
                    }
                }
                if (execRR != null) {
                    execRR.stop();
                }
            }
        }
        
        // run the full set of rr modules
        if (!fullType.isEmpty() && rrFullFound) {
            Writer writer = null;
            ExecUtil execRR = null;
            try {
                regOutputFiles.fullPlugins = outFilePathBase + "-full.txt"; //NON-NLS
                logger.log(Level.INFO, "Writing Full RegRipper results to: " + regOutputFiles.fullPlugins); //NON-NLS
                writer = new FileWriter(regOutputFiles.fullPlugins);
                execRR = new ExecUtil();
                execRR.execute(writer, RR_FULL_PATH,
                        "-r", regFilePath, "-f", fullType); //NON-NLS
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to run full RegRipper and process parse some registry files.", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile3",
                                            this.getName()));
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "RegRipper full has been interrupted, failed to parse registry.", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile4",
                                            this.getName()));
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, "Error closing output writer after running RegRipper full", ex); //NON-NLS
                    }
                }
                if (execRR != null) {
                    execRR.stop();
                }
            }
        }
        
        return regOutputFiles;
    }
    
    // @@@ VERIFY that we are doing the right thing when we parse multiple NTUSER.DAT
    private boolean parseAutopsyPluginOutput(String regRecord, long orgId, UsbDeviceIdMapper extrctr) {
        FileInputStream fstream = null;
        try {
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
     
            // Read the file in and create a Document and elements
            File regfile = new File(regRecord);
            fstream = new FileInputStream(regfile);
            
            String regString = new Scanner(fstream, "UTF-8").useDelimiter("\\Z").next(); //NON-NLS
            String startdoc = "<?xml version=\"1.0\"?><document>"; //NON-NLS
            String result = regString.replaceAll("----------------------------------------", "");
            result = result.replaceAll("\\n", ""); //NON-NLS
            result = result.replaceAll("\\r", ""); //NON-NLS
            result = result.replaceAll("'", "&apos;"); //NON-NLS
            result = result.replaceAll("&", "&amp;"); //NON-NLS
            String enddoc = "</document>"; //NON-NLS
            String stringdoc = startdoc + result + enddoc;
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(stringdoc)));
            
            // cycle through the elements in the doc
            Element oroot = doc.getDocumentElement();
            NodeList children = oroot.getChildNodes();
            int len = children.getLength();
            for (int i = 0; i < len; i++) {
                Element tempnode = (Element) children.item(i);
                
                String dataType = tempnode.getNodeName();

                NodeList timenodes = tempnode.getElementsByTagName("mtime"); //NON-NLS
                Long mtime = null;
                if (timenodes.getLength() > 0) {
                    Element timenode = (Element) timenodes.item(0);
                    String etime = timenode.getTextContent();
                    try {
                        Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(etime).getTime();
                        mtime = epochtime.longValue();
                        String Tempdate = mtime.toString();
                        mtime = Long.valueOf(Tempdate) / 1000;
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
                String winver = "";
                for (int j = 0; j < myartlist.getLength(); j++) {
                    Node artchild = myartlist.item(j);
                    // If it has attributes, then it is an Element (based off API)
                    if (artchild.hasAttributes()) {
                        Element artnode = (Element) artchild;
                        
                        String value = artnode.getTextContent().trim();
                        Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();

                        if ("recentdocs".equals(dataType)) { //NON-NLS
                            //               BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", dataType, mtime));
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", dataType, mtimeItem));
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", dataType, value));
                            //               bbart.addAttributes(bbattributes);
                            // @@@ BC: Why are we ignoring this...
                        } 
                        else if ("usb".equals(dataType)) { //NON-NLS
                            try {      
                                Long usbMtime = Long.parseLong(artnode.getAttribute("mtime")); //NON-NLS
                                usbMtime = Long.valueOf(usbMtime.toString());

                                BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED);
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), usbMtime));
                                String dev = artnode.getAttribute("dev"); //NON-NLS
                                String make = "";
                                String model = dev; 
                                if (dev.toLowerCase().contains("vid")) { //NON-NLS
                                    USBInfo info = extrctr.parseAndLookup(dev);
                                    if (info.getVendor() != null) {
                                        make = info.getVendor();
                                    }
                                    if (info.getProduct() != null) {
                                        model = info.getProduct();
                                    }
                                }
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID(),
                                        NbBundle.getMessage(this.getClass(),
                                                "ExtractRegistry.parentModuleName.noSpace"), make));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(),
                                        NbBundle.getMessage(this.getClass(),
                                                "ExtractRegistry.parentModuleName.noSpace"), model));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID(),
                                        NbBundle.getMessage(this.getClass(),
                                                "ExtractRegistry.parentModuleName.noSpace"), value));
                                bbart.addAttributes(bbattributes);
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error adding device attached artifact to blackboard."); //NON-NLS
                            }
                        } 
                        else if ("uninstall".equals(dataType)) { //NON-NLS
                            Long itemMtime = null;
                            try {
                                Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(artnode.getAttribute("mtime")).getTime(); //NON-NLS
                                itemMtime = epochtime.longValue();
                                itemMtime = itemMtime / 1000;
                            } catch (ParseException e) {
                                logger.log(Level.WARNING, "Failed to parse epoch time for installed program artifact."); //NON-NLS
                            }

                            try {
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), value));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), itemMtime));
                                BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
                                bbart.addAttributes(bbattributes);
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard."); //NON-NLS
                            }
                        } 
                        else if ("WinVersion".equals(dataType)) { //NON-NLS
                            String name = artnode.getAttribute("name"); //NON-NLS

                            if (name.contains("ProductName")) { //NON-NLS
                                winver = value;
                            }
                            if (name.contains("CSDVersion")) { //NON-NLS
                                winver = winver + " " + value;
                            }
                            if (name.contains("InstallDate")) { //NON-NLS
                                Long installtime = null;
                                try {
                                    Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(value).getTime();
                                    installtime = epochtime.longValue();
                                    String Tempdate = installtime.toString();
                                    installtime = Long.valueOf(Tempdate) / 1000;
                                } catch (ParseException e) {
                                    logger.log(Level.SEVERE, "RegRipper::Conversion on DateTime -> ", e); //NON-NLS
                                }
                                try {
                                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                                             NbBundle.getMessage(this.getClass(),
                                                                                                 "ExtractRegistry.parentModuleName.noSpace"), winver));
                                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                                                                             NbBundle.getMessage(this.getClass(),
                                                                                                 "ExtractRegistry.parentModuleName.noSpace"), installtime));
                                    BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
                                    bbart.addAttributes(bbattributes);
                                } catch (TskCoreException ex) {
                                    logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard."); //NON-NLS
                                }
                            }
                        } 
                        else if ("office".equals(dataType)) { //NON-NLS
                            String name = artnode.getAttribute("name"); //NON-NLS
                            
                            try {
                                BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                                // @@@ BC: Consider removing this after some more testing. It looks like an Mtime associated with the root key and not the individual item
                                if (mtime != null) {
                                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(),
                                                                             NbBundle.getMessage(this.getClass(),
                                                                                                 "ExtractRegistry.parentModuleName.noSpace"), mtime));
                                }
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), name));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), value));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), artnode.getNodeName()));
                                bbart.addAttributes(bbattributes);
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error adding recent object artifact to blackboard."); //NON-NLS
                            }
                        }
                    }
                }
            }
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Error finding the registry file."); //NON-NLS
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, "Error parsing the registry XML: {0}", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error building the document parser: {0}", ex); //NON-NLS
        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, "Error configuring the registry parser: {0}", ex); //NON-NLS
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

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        analyzeRegistryFiles();
    }

}
