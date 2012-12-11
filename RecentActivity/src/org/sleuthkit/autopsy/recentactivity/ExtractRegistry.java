 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestModuleImage;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.*;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.FileSystem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Extracting windows registry data using regripper
 */
public class ExtractRegistry extends Extract implements IngestModuleImage {

    public Logger logger = Logger.getLogger(this.getClass().getName());
    private String RR_PATH;
    boolean rrFound = false;
    private int sysid;
    private IngestServices services;
    final public static String MODULE_VERSION = "1.0";
    private String args;

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
//        try {
//            Case currentCase = Case.getCurrentCase(); // get the most updated case
//            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
//            ResultSet artset = tempDb.runQuery("SELECT * from blackboard_artifact_types WHERE type_name = 'TSK_SYS_INFO'");
//
//            while (artset.next()) {
//                sysid = artset.getInt("artifact_type_id");
//            }
//        } catch (Exception e) {
//        }
        final String rrHome = rrRoot.getAbsolutePath();
        logger.log(Level.INFO, "RegRipper home: " + rrHome);

        if (PlatformUtil.isWindowsOS()) {
            RR_PATH = rrHome + File.separator + "rip.exe";
        }
        else {
            RR_PATH = "perl " + rrHome + File.separator + "rip.pl";
        }
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public String getArguments() {
        return args;
    }

    @Override
    public void setArguments(String args) {
        this.args = args;
    }
    
    private void getRegistryFiles(Image image, IngestImageWorkerController controller) {
        
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<FsContent> allRegistryFiles = new ArrayList<FsContent>();
        try {
            allRegistryFiles.addAll(fileManager.findFiles(image, "ntuser.dat"));
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'ntuser.dat' file.");
        }
        
        // try to find each of the listed registry files whose parent directory
        // is like '%/system32/config%'
        String[] regFileNames = new String[] {"system", "software", "security", "sam", "default"};
        for (String regFileName : regFileNames) {
            try {
                allRegistryFiles.addAll(fileManager.findFiles(image, regFileName, "%/system32/config%"));
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error fetching registry file: " + regFileName);
            }
        }
        
        int j = 0;
        for (FsContent regFile : allRegistryFiles) {
            String regFileName = regFile.getName();
            String temps = currentCase.getTempDirectory() + "\\" + regFileName;
            try {
                ContentUtils.writeToFile(regFile, new File(currentCase.getTempDirectory() + "\\" + regFileName));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the temp registry file. {0}", ex);
            }
            File aRegFile = new File(temps);
            logger.log(Level.INFO, moduleName + "- Now getting registry information from " + temps);
            String txtPath = executeRegRip(temps, j++);
            if (txtPath.length() > 0) {
                if (parseReg(txtPath, regFile.getId()) == false) {
                    continue;
                }
            }
            
            //At this point pasco2 proccessed the index files.
            //Now fetch the results, parse them and the delete the files.
            aRegFile.delete();
        }
    }

    // TODO: Hardcoded command args/path needs to be removed. Maybe set some constants and set env variables for classpath
    // I'm not happy with this code. Can't stand making a system call, is not an acceptable solution but is a hack for now.
    private String executeRegRip(String regFilePath, int fileIndex) {
        String txtPath = regFilePath + Integer.toString(fileIndex) + ".txt";
        String type = "";

        try {
            if (regFilePath.toLowerCase().contains("system")) {
                type = "autopsysystem";
            }
            if (regFilePath.toLowerCase().contains("software")) {
                type = "autopsysoftware";
            }
            if (regFilePath.toLowerCase().contains("ntuser")) {
                type = "autopsy";
            }
            if (regFilePath.toLowerCase().contains("default")) {
                type = "1default";
            }
            if (regFilePath.toLowerCase().contains("sam")) {
                type = "1sam";
            }
            if (regFilePath.toLowerCase().contains("security")) {
                type = "1security";
            }
            String command = "\"" + RR_PATH + "\" -r \"" + regFilePath + "\" -f " + type + " > \"" + txtPath + "\" 2> NUL";
            JavaSystemCaller.Exec.execute("\"" + command + "\"");

        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to RegRipper and process parse some registry files.", ex);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "RegRipper has been interrupted, failed to parse registry.", ex);
        }
        
        return txtPath;
    }

    private boolean parseReg(String regRecord, long orgId) {
        FileInputStream fstream = null;
        try {
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            File regfile = new File(regRecord);
            fstream = new FileInputStream(regfile);
            //InputStreamReader fstreamReader = new InputStreamReader(fstream, "UTF-8");
            //BufferedReader input = new BufferedReader(fstreamReader);
            //logger.log(Level.INFO, "using encoding " + fstreamReader.getEncoding());
            String regString = new Scanner(fstream, "UTF-8").useDelimiter("\\Z").next();
            regfile.delete();
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
            Element oroot = doc.getDocumentElement();
            NodeList children = oroot.getChildNodes();
            int len = children.getLength();
            for(int i=0; i<len; i++) {
                Element tempnode = (Element) children.item(i);
                String context = tempnode.getNodeName();

                NodeList timenodes = tempnode.getElementsByTagName("time");
                Long time = null;
                if(timenodes.getLength() > 0) {
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
                if(artroots.getLength() == 0) {
                    // If there isn't an artifact node, skip this entry
                    continue;
                }
                Element artroot = (Element) artroots.item(0);
                NodeList myartlist = artroot.getChildNodes();
                String winver = "";
                String installdate = "";
                for(int j=0; j<myartlist.getLength(); j++) {
                    Node artchild = myartlist.item(j);
                    // If it has attributes, then it is an Element (based off API)
                    if(artchild.hasAttributes()) {
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
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity",  utime));
                                String dev = artnode.getAttribute("dev");
                                //TODO Revisit usage of deprecated constructor as per TSK-583
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), "RecentActivity", context, dev));
                                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID(), "RecentActivity", context, value));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), "RecentActivity", dev));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID(), "RecentActivity", value));
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
                                if(time != null) {
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
                                } catch(TskCoreException ex) {
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
                                 if(time != null) {
                                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "RecentActivity", time));
                                 }
                                 bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", name));
                                 bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", value));
                                 bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", artnode.getNodeName()));
                                bbart.addAttributes(bbattributes);
                            } catch(TskCoreException ex) {
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
    public void process(Image image, IngestImageWorkerController controller) {
        this.getRegistryFiles(image, controller);
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
        if (JavaSystemCaller.Exec.getProcess() != null) {
            JavaSystemCaller.Exec.stop();
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
    public ModuleType getType() {
        return ModuleType.Image;
    }

    @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }

    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return null;
    }

    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        return null;
    }

    @Override
    public void saveAdvancedConfiguration() {
    }

    @Override
    public void saveSimpleConfiguration() {
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
