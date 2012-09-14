 /*
 *
 * Autopsy Forensic Browser
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

import java.io.File;
import java.io.*;
import java.sql.ResultSet;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestModuleImage;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.*;

/**
 * Extracting windows registry data using regripper
 */
public class ExtractRegistry extends Extract implements IngestModuleImage {
    
    public Logger logger = Logger.getLogger(this.getClass().getName());
    private String RR_PATH;
    boolean rrFound = false;
    private int sysid;
    private IngestServices services;

    ExtractRegistry() {
        final File rrRoot = InstalledFileLocator.getDefault().locate("rr", ExtractRegistry.class.getPackage().getName(), false);
        if (rrRoot == null) {
            logger.log(Level.WARNING, "RegRipper not found");
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

        RR_PATH = rrHome + File.separator + "rip.exe";
    }

    private void getregistryfiles(Image image, IngestImageWorkerController controller) {
        try {
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            Collection<FileSystem> imageFS = tempDb.getFileSystems(image);
            List<String> fsIds = new LinkedList<String>();
            for (FileSystem img : imageFS) {
                Long tempID = img.getId();
                fsIds.add(tempID.toString());
            }

            String allFS = new String();
            for (int i = 0; i < fsIds.size(); i++) {
                if (i == 0) {
                    allFS += " AND (0";
                }
                allFS += " OR fs_obj_id = '" + fsIds.get(i) + "'";
                if (i == fsIds.size() - 1) {
                    allFS += ")";
                }
            }
            List<FsContent> Regfiles = new ArrayList<FsContent>();
            try {
                ResultSet rs = tempDb.runQuery("select * from tsk_files where lower(name) = 'ntuser.dat' OR lower(parent_path) LIKE '%/system32/config%' and (name LIKE 'system' OR name LIKE 'software' OR name = 'SECURITY' OR name = 'SAM' OR name = 'default')" + allFS);
                Regfiles = tempDb.resultSetToFsContents(rs);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }

            int j = 0;

            while (j < Regfiles.size()) {
                boolean Success;
                Content orgFS = Regfiles.get(j);
                long orgId = orgFS.getId();
                String temps = currentCase.getTempDirectory() + "\\" + Regfiles.get(j).getName().toString();
                try {
                    ContentUtils.writeToFile(Regfiles.get(j), new File(currentCase.getTempDirectory() + "\\" + Regfiles.get(j).getName()));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                }
                File regFile = new File(temps);
                logger.log(Level.INFO, moduleName + "- Now getting registry information from " + temps);
                String txtPath = executeRegRip(temps, j);
                if (txtPath.length() > 0) {
                    Success = parseReg(txtPath, orgId);
                } else {
                    Success = false;
                }
                //At this point pasco2 proccessed the index files.
                //Now fetch the results, parse them and the delete the files.
                if (Success) {
                    //Delete dat file since it was succcessful
                    regFile.delete();
                }
                j++;

            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get Registry files", ex);
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


        } catch (Exception e) {

            logger.log(Level.WARNING, "ExtractRegistry::executeRegRip() -> ", e);
        }

        return txtPath;
    }

    private boolean parseReg(String regRecord, long orgId) {
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();

        try {
            File regfile = new File(regRecord);
            FileInputStream fstream = new FileInputStream(regfile);
            InputStreamReader fstreamReader = new InputStreamReader(fstream, "UTF-8");
            BufferedReader input = new BufferedReader(fstreamReader);
            //logger.log(Level.INFO, "using encoding " + fstreamReader.getEncoding());
            String regString = new Scanner(input).useDelimiter("\\Z").next();
            regfile.delete();
            String startdoc = "<?xml version=\"1.0\"?><document>";
            String result = regString.replaceAll("----------------------------------------", "");
            result = result.replaceAll("\\n", "");
            result = result.replaceAll("\\r", "");
            result = result.replaceAll("'", "&apos;");
            result = result.replaceAll("&", "&amp;");
            String enddoc = "</document>";
            String stringdoc = startdoc + result + enddoc;
            SAXBuilder sb = new SAXBuilder();
            Document document = sb.build(new StringReader(stringdoc));
            Element root = document.getRootElement();
            List<Element> types = root.getChildren();
            Iterator<Element> iterator = types.iterator();
            while (iterator.hasNext()) {
                String etime = "";
                String context = "";
                Element tempnode = iterator.next();
                // Element tempnode = types.get(i);
                context = tempnode.getName();
                Element timenode = tempnode.getChild("time");
                etime = timenode.getTextTrim();
                Long time = null;
                try {
                    Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(etime).getTime();
                    time = epochtime.longValue();
                    String Tempdate = time.toString();
                    time = Long.valueOf(Tempdate) / 1000;
                } catch (ParseException e) {
                    logger.log(Level.WARNING, "RegRipper::Conversion on DateTime -> ", e);
                }
                Element artroot = tempnode.getChild("artifacts");
                List<Element> artlist = artroot.getChildren();
                String winver = "";
                String installdate = "";
                if (artlist.isEmpty()) {
                } else {
                    Iterator<Element> aiterator = artlist.iterator();
                    while (aiterator.hasNext()) {
                        Element artnode = aiterator.next();
                        String name = artnode.getAttributeValue("name");
                        String value = artnode.getTextTrim();
                        Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();

                        if ("recentdocs".equals(context)) {
                            //               BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", context, time));
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", context, name));
                            //               bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", context, value));
                            //               bbart.addAttributes(bbattributes);
                        } else if ("usb".equals(context)) {

                            Long utime = null;
                            try {

                                utime = Long.parseLong(name);
                                String Tempdate = utime.toString();
                                utime = Long.valueOf(Tempdate);
                                utime = utime;
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "RegRipper::Conversion on DateTime -> ", e);
                            }

                            BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED);
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", context, utime));
                            String dev = artnode.getAttributeValue("dev");
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), "RecentActivity", context, dev));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID(), "RecentActivity", context, value));
                            bbart.addAttributes(bbattributes);
                        } else if ("uninstall".equals(context)) {
                            Long ftime = null;
                            try {
                                Long epochtime = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(name).getTime();
                                ftime = epochtime.longValue();
                                ftime = ftime / 1000;
                            } catch (ParseException e) {
                                logger.log(Level.WARNING, "RegRipper::Conversion on DateTime -> ", e);
                            }
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", context, time));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", context, value));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", context, ftime));
                            BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
                            bbart.addAttributes(bbattributes);
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
                                    logger.log(Level.WARNING, "RegRipper::Conversion on DateTime -> ", e);
                                }
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", context, winver));
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", context, installtime));
                                BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
                                bbart.addAttributes(bbattributes);
                            }
                        } else if ("office".equals(context)) {
                                                       
                            BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", context, time));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", context, name));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", context, value));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", context, artnode.getName()));
                            bbart.addAttributes(bbattributes);

                        } else {
//                            BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(sysid);
//                            bbart.addAttributes(bbattributes);
                        }
                    }
                }
            }
        } catch (Exception ex) {

            logger.log(Level.WARNING, "Error while trying to read into a registry file." + ex);
        }
        return true;
    }

    @Override
    public void process(Image image, IngestImageWorkerController controller) {
        this.getregistryfiles(image, controller);
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
    }

    @Override
    public void complete() {
        throw new UnsupportedOperationException("Not supported yet.");
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
