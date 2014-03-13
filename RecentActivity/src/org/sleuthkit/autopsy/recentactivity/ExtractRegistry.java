 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2013 Basis Technology Corp.
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
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.recentactivity.ExtractUSB.USBInfo;
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
    private ExecUtil execRR;

    //hide public constructor to prevent from instantiation by ingest module loader
    ExtractRegistry() {
        final File rrRoot = InstalledFileLocator.getDefault().locate("rr", ExtractRegistry.class.getPackage().getName(), false);
        if (rrRoot == null) {
            logger.log(Level.SEVERE, "RegRipper not found");
            rrFound = false;
            return;
        } else {
            rrFound = true;
        }
        
        final String rrHome = rrRoot.getAbsolutePath();
        logger.log(Level.INFO, "RegRipper home: " + rrHome);

        if (PlatformUtil.isWindowsOS()) {
            RR_PATH = rrHome + File.separator + "rip.exe";
        } else {
            RR_PATH = "perl " + rrHome + File.separator + "rip.pl";
        }
        
        final File rrFullRoot = InstalledFileLocator.getDefault().locate("rr-full", ExtractRegistry.class.getPackage().getName(), false);
        if (rrFullRoot == null) {
            logger.log(Level.SEVERE, "RegRipper Full not found");
            rrFullFound = false;
        } else {
            rrFullFound = true;
        }
        
        final String rrFullHome = rrFullRoot.getAbsolutePath();
        logger.log(Level.INFO, "RegRipper Full home: " + rrFullHome);

        if (PlatformUtil.isWindowsOS()) {
            RR_FULL_PATH = rrFullHome + File.separator + "rip.exe";
        } else {
            RR_FULL_PATH = "perl " + rrFullHome + File.separator + "rip.pl";
        }
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    
    /**
     * Search for the registry hives on the system.
     * @param dataSource Data source to search for hives in.
     * @return List of registry hives 
     */
    private List<AbstractFile> findRegistryFiles(Content dataSource) {
        List<AbstractFile> allRegistryFiles = new ArrayList<>();
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        
        // find the user-specific ntuser-dat files
        try {
            allRegistryFiles.addAll(fileManager.findFiles(dataSource, "ntuser.dat"));
        } 
        catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'ntuser.dat' file.");
        }

        // find the system hives'
        String[] regFileNames = new String[] {"system", "software", "security", "sam"};
        for (String regFileName : regFileNames) {
            try {
                allRegistryFiles.addAll(fileManager.findFiles(dataSource, regFileName, "/system32/config"));
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
     * 
     * @param dataSource
     * @param controller 
     */
    private void analyzeRegistryFiles(Content dataSource, IngestDataSourceWorkerController controller) {
        List<AbstractFile> allRegistryFiles = findRegistryFiles(dataSource);
        
        // open the log file
        FileWriter logFile = null;
        try {
            logFile = new FileWriter(RAImageIngestModule.getRAOutputPath(currentCase, "reg") + File.separator + "regripper-info.txt");
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        ExtractUSB extrctr = new ExtractUSB();
        
        int j = 0;
        for (AbstractFile regFile : allRegistryFiles) {
            String regFileName = regFile.getName();
            String regFileNameLocal = RAImageIngestModule.getRATempPath(currentCase, "reg") + File.separator + regFileName;
            String outputPathBase = RAImageIngestModule.getRAOutputPath(currentCase, "reg") + File.separator + regFileName + "-regripper-" + Integer.toString(j++);
            File regFileNameLocalFile = new File(regFileNameLocal);
            try {
                ContentUtils.writeToFile(regFile, regFileNameLocalFile);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the temp registry file. {0}", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.analyzeRegFiles.errMsg.errWritingTemp",
                                            this.getName(), regFileName));
                continue;
            }
            
            if (controller.isCancelled()) {
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
            
            logger.log(Level.INFO, moduleName + "- Now getting registry information from " + regFileNameLocal);
            RegOutputFiles regOutputFiles = executeRegRip(regFileNameLocal, outputPathBase);
            
            if (controller.isCancelled()) {
                break;
            }
            
            // parse the autopsy-specific output
            if (regOutputFiles.autopsyPlugins.isEmpty() == false) {
                if (parseAutopsyPluginOutput(regOutputFiles.autopsyPlugins, regFile.getId(), extrctr) == false) {
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
                            logger.log(Level.WARNING, "Failed to close reader.", ex);
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
        
        if (regFilePath.toLowerCase().contains("system")) {
            autopsyType = "autopsysystem";
            fullType = "system";
        } 
        else if (regFilePath.toLowerCase().contains("software")) {
            autopsyType = "autopsysoftware";
            fullType = "software";
        } 
        else if (regFilePath.toLowerCase().contains("ntuser")) {
            autopsyType = "autopsyntuser";
            fullType = "ntuser";
        }  
        else if (regFilePath.toLowerCase().contains("sam")) {
            fullType = "sam";
        } 
        else if (regFilePath.toLowerCase().contains("security")) {
            fullType = "security";
        } 
        else {
            return regOutputFiles;
        }
        
        // run the autopsy-specific set of modules
        if (!autopsyType.isEmpty() && rrFound) {
            // TODO - add error messages
            Writer writer = null;
            try {
                regOutputFiles.autopsyPlugins = outFilePathBase + "-autopsy.txt";
                logger.log(Level.INFO, "Writing RegRipper results to: " + regOutputFiles.autopsyPlugins);
                writer = new FileWriter(regOutputFiles.autopsyPlugins);
                execRR = new ExecUtil();
                execRR.execute(writer, RR_PATH,
                        "-r", regFilePath, "-f", autopsyType);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to RegRipper and process parse some registry files.", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile",
                                            this.getName()));
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "RegRipper has been interrupted, failed to parse registry.", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile2",
                                            this.getName()));
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, "Error closing output writer after running RegRipper", ex);
                    }
                }
            }
        }
        
        // run the full set of rr modules
        if (!fullType.isEmpty() && rrFullFound) {
            Writer writer = null;
            try {
                regOutputFiles.fullPlugins = outFilePathBase + "-full.txt";
                logger.log(Level.INFO, "Writing Full RegRipper results to: " + regOutputFiles.fullPlugins);
                writer = new FileWriter(regOutputFiles.fullPlugins);
                execRR = new ExecUtil();
                execRR.execute(writer, RR_FULL_PATH,
                        "-r", regFilePath, "-f", fullType);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to run full RegRipper and process parse some registry files.", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile3",
                                            this.getName()));
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "RegRipper full has been interrupted, failed to parse registry.", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractRegistry.execRegRip.errMsg.failedAnalyzeRegFile4",
                                            this.getName()));
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, "Error closing output writer after running RegRipper full", ex);
                    }
                }
            }
        }
        
        return regOutputFiles;
    }
    
    // @@@ VERIFY that we are doing the right thing when we parse multiple NTUSER.DAT
    private boolean parseAutopsyPluginOutput(String regRecord, long orgId, ExtractUSB extrctr) {
        FileInputStream fstream = null;
        try {
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
     
            // Read the file in and create a Document and elements
            File regfile = new File(regRecord);
            fstream = new FileInputStream(regfile);
            
            String regString = new Scanner(fstream, "UTF-8").useDelimiter("\\Z").next();
            String startdoc = "<?xml version=\"1.0\"?><document>";
            String result = regString.replaceAll("----------------------------------------", "");
            result = result.replaceAll("\\n", "");
            result = result.replaceAll("\\r", "");
            result = result.replaceAll("'", "&apos;");
            result = result.replaceAll("&", "&amp;");
            String enddoc = "</document>";
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

                NodeList timenodes = tempnode.getElementsByTagName("mtime");
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
                        logger.log(Level.WARNING, "Failed to parse epoch time when parsing the registry.");
                    }
                }

                NodeList artroots = tempnode.getElementsByTagName("artifacts");
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

                        if ("recentdocs".equals(dataType)) {
                            //               BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", dataType, mtime));
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", dataType, mtimeItem));
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", dataType, value));
                            //               bbart.addAttributes(bbattributes);
                            // @@@ BC: Why are we ignoring this...
                        } 
                        else if ("usb".equals(dataType)) {
                            try {      
                                Long usbMtime = Long.parseLong(artnode.getAttribute("mtime"));
                                usbMtime = Long.valueOf(usbMtime.toString());

                                BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED);
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), usbMtime));
                                String dev = artnode.getAttribute("dev");       
                                String model = dev; 
                                if (dev.toLowerCase().contains("vid")) {
                                    USBInfo info = extrctr.get(dev);
                                    if(info.getVendor()!=null)
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID(),
                                                                                 NbBundle.getMessage(this.getClass(),
                                                                                                     "ExtractRegistry.parentModuleName.noSpace"), info.getVendor()));
                                    if(info.getProduct() != null)
                                        model = info.getProduct();
                                }
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), model));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID(),
                                                                         NbBundle.getMessage(this.getClass(),
                                                                                             "ExtractRegistry.parentModuleName.noSpace"), value));
                                bbart.addAttributes(bbattributes);
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error adding device attached artifact to blackboard.");
                            }
                        } 
                        else if ("uninstall".equals(dataType)) {
                            Long itemMtime = null;
                            try {
                                Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(artnode.getAttribute("mtime")).getTime();
                                itemMtime = epochtime.longValue();
                                itemMtime = itemMtime / 1000;
                            } catch (ParseException e) {
                                logger.log(Level.WARNING, "Failed to parse epoch time for installed program artifact.");
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
                                logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard.");
                            }
                        } 
                        else if ("WinVersion".equals(dataType)) {
                            String name = artnode.getAttribute("name");

                            if (name.contains("ProductName")) {
                                winver = value;
                            }
                            if (name.contains("CSDVersion")) {
                                winver = winver + " " + value;
                            }
                            if (name.contains("InstallDate")) {
                                Long installtime = null;
                                try {
                                    Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(value).getTime();
                                    installtime = epochtime.longValue();
                                    String Tempdate = installtime.toString();
                                    installtime = Long.valueOf(Tempdate) / 1000;
                                } catch (ParseException e) {
                                    logger.log(Level.SEVERE, "RegRipper::Conversion on DateTime -> ", e);
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
                                    logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard.");
                                }
                            }
                        } 
                        else if ("office".equals(dataType)) {
                            String name = artnode.getAttribute("name");
                            
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
                                logger.log(Level.SEVERE, "Error adding recent object artifact to blackboard.");
                            }
                        }
                    }
                }
            }
            return true;
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Error finding the registry file.");
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, "Error parsing the registry XML: {0}", ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error building the document parser: {0}", ex);
        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, "Error configuring the registry parser: {0}", ex);
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
    public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        analyzeRegistryFiles(dataSource, controller);
    }

    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
    }

    @Override
    public void complete() {
    }

    @Override
    public void stop() {
        if (execRR != null) {
            execRR.stop();
            execRR = null;
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "ExtractRegistry.getName");
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "ExtractRegistry.getDesc");
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
