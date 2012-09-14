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

//IO imports
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

// SQL imports
import java.sql.ResultSet;

//Util Imports
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TSK Imports
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.DecodeUtil;
import org.sleuthkit.autopsy.coreutils.JLNK;
import org.sleuthkit.autopsy.coreutils.JLnkParser;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleImage;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.*;

public class ExtractIE extends Extract implements IngestModuleImage {

    private static final Logger logger = Logger.getLogger(ExtractIE.class.getName());
  
    private IngestServices services;
    
    private String indexDatQueryStr = "select * from tsk_files where name LIKE '%index.dat%'";
    private String favoriteQuery = "select * from `tsk_files` where parent_path LIKE '%/Favorites%' and name LIKE '%.url'";
    private String cookiesQuery = "select * from `tsk_files` where parent_path LIKE '%/Cookies%' and name LIKE '%.txt'";
    private String recentQuery = "select * from `tsk_files` where parent_path LIKE '%/Recent%' and name LIKE '%.lnk'";
    //sleauthkit db handle
    SleuthkitCase tempDb;
    //paths set in init()
    private String PASCO_RESULTS_PATH;
    private String PASCO_LIB_PATH;
    private String JAVA_PATH;
    //Results List to be referenced/used outside the class
    public ArrayList<HashMap<String, Object>> PASCO_RESULTS_LIST = new ArrayList<HashMap<String, Object>>();
    //Look Up Table  that holds Pasco2 results
    private HashMap<String, Object> PASCO_RESULTS_LUT;
    private KeyValue IE_PASCO_LUT = new KeyValue(BrowserType.IE.name(), BrowserType.IE.getType());
    public LinkedHashMap<String, Object> IE_OBJ;
    boolean pascoFound = false;

    public ExtractIE() {
        moduleName = "Internet Explorer";
    }

    @Override
    public void process(Image image, IngestImageWorkerController controller) {
        this.getHistory(image, controller);
        this.getBookmark(image, controller);
        this.getCookie(image, controller);
        this.getRecentDocuments(image, controller);
        this.parsePascoResults();
    }

    //Favorites section
    // This gets the favorite info
    private void getBookmark(Image image, IngestImageWorkerController controller) {

        List<FsContent> FavoriteList = this.extractFiles(image, favoriteQuery);

        for (FsContent Favorite : FavoriteList) {
            if (controller.isCancelled()) {
                break;
            }
            Content fav = Favorite;
            byte[] t = new byte[(int) fav.getSize()];
            try {
                final int bytesRead = fav.read(t, 0, fav.getSize());
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to retrieve content from the TSK .", ex);
            }
            String bookmarkString = new String(t);
            String re1 = ".*?";	// Non-greedy match on filler
            String re2 = "((?:http|https)(?::\\/{2}[\\w]+)(?:[\\/|\\.]?)(?:[^\\s\"]*))";	// HTTP URL 1
            String url = "";
            Pattern p = Pattern.compile(re1 + re2, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(bookmarkString);
            if (m.find()) {
                url = m.group(1);
            }
            String name = Favorite.getName();
            Long datetime = Favorite.getCrtime();
            String Tempdate = datetime.toString();
            datetime = Long.valueOf(Tempdate);
            String domain = Util.extractDomain(url);
            try {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", datetime));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", url));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", DecodeUtil.decodeURL(url)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", name));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK, Favorite, bbattributes);
                
                services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + Favorite.getName());
            }

        }
    }

    //Cookies section
    // This gets the cookies info
    private void getCookie(Image image, IngestImageWorkerController controller) {

        List<FsContent> CookiesList = this.extractFiles(image, cookiesQuery);

        for (FsContent Cookie : CookiesList) {
            if (controller.isCancelled()) {
                break;
            }
            Content fav = Cookie;
            byte[] t = new byte[(int) fav.getSize()];
            try {
                final int bytesRead = fav.read(t, 0, fav.getSize());
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to retrieve content from the TSK .", ex);
            }
            String cookieString = new String(t);
            String[] values = cookieString.split("\n");
            String url = values.length > 2 ? values[2] : "";
            String value = values.length > 1 ? values[1] : "";
            String name = values.length > 0 ? values[0] : "";
            Long datetime = Cookie.getCrtime();
            String Tempdate = datetime.toString();
            datetime = Long.valueOf(Tempdate);
            String domain = Util.extractDomain(url);
            
            try {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", url));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", DecodeUtil.decodeURL(url)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Last Visited", datetime));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", value));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "Title", (name != null) ? name : ""));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, Cookie, bbattributes);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + Cookie.getName());
            }

        }
        
        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
    }

    //Recent Documents section
    // This gets the recent object info
    private void getRecentDocuments(Image image, IngestImageWorkerController controller) {

        List<FsContent> RecentList = this.extractFiles(image, recentQuery);

        for (FsContent Recent : RecentList) {
            if (controller.isCancelled()) {
                break;
            }
            Content fav = Recent;
            JLNK lnk = new JLnkParser(new ReadContentInputStream(fav), (int) fav.getSize()).parse();
            String path = lnk.getBestPath();
            Long datetime = Recent.getCrtime();
            try {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), "RecentActivity", path));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", Util.getFileName(path)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(), "RecentActivity", Util.findID(path)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Date Created", datetime));
                this.addArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT, Recent, bbattributes);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + Recent.getName());
            }

        }
        
        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT));

    }

    //@Override
    public KeyValue getRecentActivity() {
        return IE_PASCO_LUT;
    }

    private void getHistory(Image image, IngestImageWorkerController controller) {
        final Case currentCase = Case.getCurrentCase();
        final String caseDir = Case.getCurrentCase().getCaseDirectory();
        PASCO_RESULTS_PATH = Case.getCurrentCase().getTempDirectory() + File.separator + "results";
        JAVA_PATH = PlatformUtil.getJavaPath();

        logger.log(Level.INFO, "Pasco results path: " + PASCO_RESULTS_PATH);

        final File pascoRoot = InstalledFileLocator.getDefault().locate("pasco2", ExtractIE.class.getPackage().getName(), false);
        if (pascoRoot == null) {
            logger.log(Level.WARNING, "Pasco2 not found");
            pascoFound = false;
            return;
        } else {
            pascoFound = true;
        }

        final String pascoHome = pascoRoot.getAbsolutePath();
        logger.log(Level.INFO, "Pasco2 home: " + pascoHome);

        PASCO_LIB_PATH = pascoHome + File.separator + "pasco2.jar" + File.pathSeparator
                + pascoHome + File.separator + "*";

        try {
            File resultsDir = new File(PASCO_RESULTS_PATH);
            resultsDir.mkdirs();

            Collection<FsContent> FsContentCollection = null;
            tempDb = currentCase.getSleuthkitCase();
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
            try {
                ResultSet rs = tempDb.runQuery(indexDatQueryStr + allFS);
                FsContentCollection = tempDb.resultSetToFsContents(rs);
                rs.close();
                rs.getStatement().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }
            String temps;
            String indexFileName;

            for (FsContent fsc : FsContentCollection) {
                // Since each result represent an index.dat file,
                // just create these files with the following notation:
                // index<Number>.dat (i.e. index0.dat, index1.dat,..., indexN.dat)
                // Write each index.dat file to a temp directory.
                //BlackboardArtifact bbart = fsc.newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                indexFileName = "index" + Integer.toString((int) fsc.getId()) + ".dat";
                //indexFileName = "index" + Long.toString(bbart.getArtifactID()) + ".dat";
                temps = currentCase.getTempDirectory() + File.separator + indexFileName;
                File datFile = new File(temps);
                if (controller.isCancelled()) {
                    datFile.delete();
                    break;
                }
                try {
                    ContentUtils.writeToFile(fsc, datFile);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error while trying to write index.dat file " + datFile.getAbsolutePath(), e);
                }

                boolean bPascProcSuccess = executePasco(temps, (int) fsc.getId());

                //At this point pasco2 proccessed the index files.
                //Now fetch the results, parse them and the delete the files.
                if (bPascProcSuccess) {

                    //Delete index<n>.dat file since it was succcessfully by Pasco
                    datFile.delete();
                }
            }
        } catch (Exception ioex) {
            logger.log(Level.WARNING, "Error while trying to write index.dat files.", ioex);
        }

        //bookmarks

        //cookies
    }

    //Simple wrapper to JavaSystemCaller.Exec() to execute pasco2 jar
    // TODO: Hardcoded command args/path needs to be removed. Maybe set some constants and set env variables for classpath
    // I'm not happy with this code. Can't stand making a system call, is not an acceptable solution but is a hack for now.
    private boolean executePasco(String indexFilePath, int fileIndex) {
        if (pascoFound == false) {
            return false;
        }
        boolean success = true;

        try {
            StringBuilder command = new StringBuilder();

            command.append(" -cp");
            command.append(" \"").append(PASCO_LIB_PATH).append("\"");
            command.append(" isi.pasco2.Main");
            command.append(" -T history");
            command.append(" \"").append(indexFilePath).append("\"");
            command.append(" > \"").append(PASCO_RESULTS_PATH).append("\\pasco2Result.").append(Integer.toString(fileIndex)).append(".txt\"");
            // command.add(" > " + "\"" + PASCO_RESULTS_PATH + File.separator + Long.toString(bbId) + "\"");
            String cmd = command.toString();
            JavaSystemCaller.Exec.execute("\"\"" + JAVA_PATH + "\" " + cmd + "\"");

        } catch (Exception e) {
            success = false;
            logger.log(Level.WARNING, "ExtractIE::executePasco() -> ", e);
        }

        return success;
    }

    private void parsePascoResults() {
        if (pascoFound == false) {
            return;
        }
        // First thing we want to do is check to make sure the results directory
        // is not empty.
        File rFile = new File(PASCO_RESULTS_PATH);


        //Let's make sure our list and lut are empty.
        //PASCO_RESULTS_LIST.clear();

        if (rFile.exists()) {
            //Give me a list of pasco results in that directory
            File[] pascoFiles = rFile.listFiles();

            if (pascoFiles.length > 0) {
                try {
                    for (File file : pascoFiles) {
                        String fileName = file.getName();
                        long artObjId = Long.parseLong(fileName.substring(fileName.indexOf(".") + 1, fileName.lastIndexOf(".")));
                        //bbartname = bbartname.substring(0, 4);

                        // Make sure the file the is not empty or the Scanner will
                        // throw a "No Line found" Exception
                        if (file != null && file.length() > 0) {
                            Scanner fileScanner = new Scanner(new FileInputStream(file.toString()));
                            //Skip the first three lines
                            fileScanner.nextLine();
                            fileScanner.nextLine();
                            fileScanner.nextLine();
                            //  long inIndexId = 0;

                            while (fileScanner.hasNext()) {
                                //long bbartId = Long.parseLong(bbartname + inIndexId++);

                                String line = fileScanner.nextLine();

                                //Need to change this pattern a bit because there might
                                //be instances were "V" might not apply.
                                String pattern = "(?)URL(\\s)(V|\\:)";
                                Pattern p = Pattern.compile(pattern);
                                Matcher m = p.matcher(line);
                                if (m.find()) {
                                    try {
                                        String[] lineBuff = line.split("\\t");
                                        PASCO_RESULTS_LUT = new HashMap<String, Object>();
                                        String url[] = lineBuff[1].split("@", 2);
                                        String ddtime = lineBuff[2];
                                        String actime = lineBuff[3];
                                        Long ftime = (long) 0;
                                        String user = "";
                                        String realurl = "";
                                        String domain = "";
                                        if (url.length > 1) {
                                            user = url[0];
                                            user = user.replace("Visited:", "");
                                            user = user.replace(":Host:", "");
                                            user = user.replaceAll("(:)(.*?)(:)", "");
                                            user = user.trim();
                                            realurl = url[1];
                                            realurl = realurl.replace("Visited:", "");
                                            realurl = realurl.replaceAll(":(.*?):", "");
                                            realurl = realurl.replace(":Host:", "");
                                            realurl = realurl.trim();
                                            domain = Util.extractDomain(realurl);
                                        }
                                        if (!ddtime.isEmpty()) {
                                            ddtime = ddtime.replace("T", " ");
                                            ddtime = ddtime.substring(ddtime.length() - 5);
                                        }
                                        if (!actime.isEmpty()) {
                                            try {
                                                Long epochtime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(actime).getTime();
                                                ftime = epochtime.longValue();
                                                ftime = ftime / 1000;
                                            } catch (ParseException e) {
                                                logger.log(Level.WARNING, "ExtractIE::parsePascosResults() -> ", e);
                                            }
                                        }

                                        // TODO: Need to fix this so we have the right obj_id
                                        try {
                                            BlackboardArtifact bbart = tempDb.getContentById(artObjId).newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", realurl));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", DecodeUtil.decodeURL(realurl)));

                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", ftime));

                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "RecentActivity", ""));

                                            //   bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", ddtime));

                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
                                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USERNAME.getTypeID(), "RecentActivity", user));
                                            bbart.addAttributes(bbattributes);
                                        } catch (Exception ex) {
                                            logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                                            this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + tempDb.getAbstractFileById(artObjId).getName());
                                        }

                                        //KeyValueThing
                                        //This will be redundant in terms IE.name() because of
                                        //the way they implemented KeyValueThing
                                        IE_OBJ = new LinkedHashMap<String, Object>();
                                        IE_OBJ.put(BrowserType.IE.name(), PASCO_RESULTS_LUT);
                                        IE_PASCO_LUT.addMap(IE_OBJ);

                                        PASCO_RESULTS_LIST.add(PASCO_RESULTS_LUT);
                                    } catch (Exception ex) {
                                        logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                                    }
                                }

                            }
                        }
                        //TODO: Fix Delete issue
                        boolean bDelete = file.delete();
                    }
                } catch (IOException ioex) {
                    logger.log(Level.WARNING, "ExtractIE::parsePascosResults() -> ", ioex);
                }

            }
        }

        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
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
        if(JavaSystemCaller.Exec.getProcess() != null)
        {
            JavaSystemCaller.Exec.stop();
        }
    }

    @Override
    public String getDescription() {
        return "Extracts activity from Internet Explorer browser, as well as recent documents in windows.";
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
