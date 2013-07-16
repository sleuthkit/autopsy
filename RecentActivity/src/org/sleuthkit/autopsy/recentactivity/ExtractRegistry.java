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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
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
 * Extracting windows registry data using regripper
 */
public class ExtractRegistry extends Extract {

    public Logger logger = Logger.getLogger(this.getClass().getName());
    private String RR_PATH;
    private String RR_FULL_PATH;
    boolean rrFound = false;
    boolean rrFullFound = false;
    private int sysid;
    private IngestServices services;
    final public static String MODULE_VERSION = "1.0";
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
     * Identifies registry files in the database by name, runs regripper on them, and parses the output.
     * 
     * @param dataSource
     * @param controller 
     */
    private void getRegistryFiles(Content dataSource, IngestDataSourceWorkerController controller) {
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> allRegistryFiles = new ArrayList<>();
        try {
            allRegistryFiles.addAll(fileManager.findFiles(dataSource, "ntuser.dat"));
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'ntuser.dat' file.");
        }

        // try to find each of the listed registry files whose parent directory
        // is like '/system32/config'
        String[] regFileNames = new String[] {"system", "software", "security", "sam", "default"};
        for (String regFileName : regFileNames) {
            try {
                allRegistryFiles.addAll(fileManager.findFiles(dataSource, regFileName, "/system32/config"));
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error fetching registry file: " + regFileName);
            }
        }
        ExtractUSB extrctr = new ExtractUSB();
        FileWriter logFile = null;
        try {
            logFile = new FileWriter(RAImageIngestModule.getRAOutputPath(currentCase, "reg") + File.separator + "regripper-info.txt");
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
            logFile = null;
        }
        
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
                continue;
            }
           
            try {
                if (logFile != null) {
                    logFile.write(Integer.toString(j-1) + "\t" + regFile.getUniquePath() + "\n");
                }
            } catch (TskCoreException ex) {
                java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
            }
            catch (IOException ex) {
                java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            logger.log(Level.INFO, moduleName + "- Now getting registry information from " + regFileNameLocal);
            RegOutputFiles regOutputFiles = executeRegRip(regFileNameLocal, outputPathBase);
            if (parseReg(regOutputFiles.autopsyPlugins, regFile.getId(), extrctr) == false) {
                continue;
            }

            try {
                BlackboardArtifact art = regFile.newArtifact(ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID());
                BlackboardAttribute att = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "RegRipper");
                art.addAttribute(att);
            
                FileReader fread = new FileReader(regOutputFiles.fullPlugins);
                BufferedReader input = new BufferedReader(fread);
                
                StringBuilder sb = new StringBuilder();
                while (true) {
                    
                    try {
                        String s = input.readLine();
                        if (s == null) {
                            break;
                        }
                        sb.append(s).append("\n");
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
                        break;
                    }
                }
                
                att = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(), "RecentActivity", sb.toString());
                art.addAttribute(att);
            } catch (FileNotFoundException ex) {
                java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
            } catch (TskCoreException ex) {
                java.util.logging.Logger.getLogger(ExtractRegistry.class.getName()).log(Level.SEVERE, null, ex);
            } 
    
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

    // TODO: Hardcoded command args/path needs to be removed. Maybe set some constants and set env variables for classpath
    // I'm not happy with this code. Can't stand making a system call, is not an acceptable solution but is a hack for now.
    /**
     * Execute regripper on the given registry.
     * @param regFilePath Path to local copy of registry
     * @param outFilePathBase  Path to location to save output file to.  Base name that will be extended on
     */
    private RegOutputFiles executeRegRip(String regFilePath, String outFilePathBase) {
        Writer writer = null;
     
        String type = "";
        String fullType = "";
        RegOutputFiles regOutputFiles = new RegOutputFiles();

        if (regFilePath.toLowerCase().contains("system")) {
            type = "autopsysystem";
            fullType = "system";
        } else if (regFilePath.toLowerCase().contains("software")) {
            type = "autopsysoftware";
            fullType = "software";
        } else if (regFilePath.toLowerCase().contains("ntuser")) {
            type = "autopsy";
            fullType = "ntuser";
        } else if (regFilePath.toLowerCase().contains("default")) {
            //type = "1default";
        } else if (regFilePath.toLowerCase().contains("sam")) {
            fullType = "sam";
        } else if (regFilePath.toLowerCase().contains("security")) {
            fullType = "security";
        } else {
            // @@@ Seems like we should error out or something...
            type = "1default";
        }

        if ((type.equals("") == false) && (rrFound)) {
            try {
                regOutputFiles.autopsyPlugins = outFilePathBase + "-autopsy.txt";
                logger.log(Level.INFO, "Writing RegRipper results to: " + regOutputFiles.autopsyPlugins);
                writer = new FileWriter(regOutputFiles.autopsyPlugins);
                execRR = new ExecUtil();
                execRR.execute(writer, RR_PATH,
                        "-r", regFilePath, "-f", type);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to RegRipper and process parse some registry files.", ex);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "RegRipper has been interrupted, failed to parse registry.", ex);
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
        else {
            logger.log(Level.INFO, "Not running Autopsy-only modules on hive");
        }
        
        if ((fullType.equals("") == false) && (rrFullFound)) {
            try {
                regOutputFiles.fullPlugins = outFilePathBase + "-full.txt";
                logger.log(Level.INFO, "Writing Full RegRipper results to: " + regOutputFiles.fullPlugins);
                writer = new FileWriter(regOutputFiles.fullPlugins);
                execRR = new ExecUtil();
                execRR.execute(writer, RR_FULL_PATH,
                        "-r", regFilePath, "-f", fullType);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to run full RegRipper and process parse some registry files.", ex);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "RegRipper full has been interrupted, failed to parse registry.", ex);
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
        else {
            logger.log(Level.INFO, "Not running original RR modules on hive");
        }
        return regOutputFiles;
    }
    
    // @@@ VERIFY that we are doing the right thing when we parse multiple NTUSER.DAT

    private boolean parseReg(String regRecord, long orgId, ExtractUSB extrctr) {
        FileInputStream fstream = null;
        try {
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
     
            // Read the file in and create a Document and elements
            File regfile = new File(regRecord);
            fstream = new FileInputStream(regfile);
            //InputStreamReader fstreamReader = new InputStreamReader(fstream, "UTF-8");
            //BufferedReader input = new BufferedReader(fstreamReader);
            //logger.log(Level.INFO, "using encoding " + fstreamReader.getEncoding());
            String regString = new Scanner(fstream, "UTF-8").useDelimiter("\\Z").next();
            //regfile.delete();
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
                String context = tempnode.getNodeName();

                NodeList timenodes = tempnode.getElementsByTagName("time");
                Long time = null;
                if (timenodes.getLength() > 0) {
                    Element timenode = (Element) timenodes.item(0);
                    String etime = timenode.getTextContent();
                    try {
                        Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(etime).getTime();
                        time = epochtime.longValue();
                        String Tempdate = time.toString();
                        time = Long.valueOf(Tempdate) / 1000;
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
                String installdate = "";
                for (int j = 0; j < myartlist.getLength(); j++) {
                    Node artchild = myartlist.item(j);
                    // If it has attributes, then it is an Element (based off API)
                    if (artchild.hasAttributes()) {
                        Element artnode = (Element) artchild;
                        String name = artnode.getAttribute("name");
                        String value = artnode.getTextContent().trim();
                        Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();

                        if ("recentdocs".equals(context)) {
                            //               BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", context, time));
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", context, name));
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", context, value));
                            //               bbart.addAttributes(bbattributes);
                        } else if ("usb".equals(context)) {
                            try {
                                Long utime = null;
                                utime = Long.parseLong(name);
                                String Tempdate = utime.toString();
                                utime = Long.valueOf(Tempdate);

                                BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED);
                                //TODO Revisit usage of deprecated constructor as per TSK-583
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", context, utime));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", utime));
                                String dev = artnode.getAttribute("dev");
                                //TODO Revisit usage of deprecated constructor as per TSK-583
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), "RecentActivity", context, dev));
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID(), "RecentActivity", context, value));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), "RecentActivity", dev));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID(), "RecentActivity", value));
                                if (dev.toLowerCase().contains("vid")) {
                                    USBInfo info = extrctr.get(dev);
                                    if(info.getVendor()!=null)
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID(), "RecentActivity", info.getVendor()));
                                    if(info.getProduct() != null)
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), "RecentActivity", info.getProduct()));
                                }
                                bbart.addAttributes(bbattributes);
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error adding device attached artifact to blackboard.");
                            }
                        } else if ("uninstall".equals(context)) {
                            Long ftime = null;
                            try {
                                Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(name).getTime();
                                ftime = epochtime.longValue();
                                ftime = ftime / 1000;
                            } catch (ParseException e) {
                                logger.log(Level.WARNING, "Failed to parse epoch time for installed program artifact.");
                            }

                            //TODO Revisit usage of deprecated constructor as per TSK-583
                            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", context, time));
                            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", context, value));
                            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", context, ftime));

                            try {
                                if (time != null) {
                                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "RecentActivity", time));
                                }
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", value));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", ftime));
                                BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
                                bbart.addAttributes(bbattributes);
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard.");
                            }
                        } else if ("WinVersion".equals(context)) {

                            if (name.contains("ProductName")) {
                                winver = value;
                            }
                            if (name.contains("CSDVersion")) {
                                winver = winver + " " + value;
                            }
                            if (name.contains("InstallDate")) {
                                installdate = value;
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
                                    //TODO Revisit usage of deprecated constructor as per TSK-583
                                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", context, winver));
                                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", context, installtime));
                                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", winver));
                                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", installtime));
                                    BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
                                    bbart.addAttributes(bbattributes);
                                } catch (TskCoreException ex) {
                                    logger.log(Level.SEVERE, "Error adding installed program artifact to blackboard.");
                                }
                            }
                        } else if ("office".equals(context)) {
                            try {
                                BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                                //TODO Revisit usage of deprecated constructor as per TSK-583
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", context, time));
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", context, name));
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", context, value));
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", context, artnode.getName()));
                                if (time != null) {
                                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "RecentActivity", time));
                                }
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", name));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", value));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", artnode.getNodeName()));
                                bbart.addAttributes(bbattributes);
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error adding recent object artifact to blackboard.");
                            }

                        } else {
                            //BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(sysid);
                            //bbart.addAttributes(bbattributes);
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
        this.getRegistryFiles(dataSource, controller);
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
    }

    @Override
    public void complete() {
        logger.info("Registry Extract has completed.");
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
        return "Registry";
    }

    @Override
    public String getDescription() {
        return "Extracts activity from the Windows registry utilizing RegRipper.";
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
