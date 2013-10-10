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

//IO imports
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

//Util Imports
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.Collection;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TSK Imports
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.JLNK;
import org.sleuthkit.autopsy.coreutils.JLnkParser;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.*;

public class ExtractIE extends Extract {
    private static final Logger logger = Logger.getLogger(ExtractIE.class.getName());
    private IngestServices services;
    
    //paths set in init()
    private String moduleTempResultsDir;
    private String PASCO_LIB_PATH;
    private String JAVA_PATH;
    
    // List of Pasco result files for this data source
    private List<String> pascoResults;
    boolean pascoFound = false;
    final public static String MODULE_VERSION = "1.0";
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    private  ExecUtil execPasco;

    //hide public constructor to prevent from instantiation by ingest module loader
    ExtractIE() {
        moduleName = "Internet Explorer";
        moduleTempResultsDir = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "IE") + File.separator + "results";
        JAVA_PATH = PlatformUtil.getJavaPath();
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }


    @Override
    public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
            this.extractAndRunPasco(dataSource, controller);
            this.getBookmark(dataSource, controller);
            this.getCookie(dataSource, controller);
            this.getRecentDocuments(dataSource, controller);
            this.getHistory(pascoResults);
    }

    //Favorites section
    // This gets the favorite info
    private void getBookmark(Content dataSource, IngestDataSourceWorkerController controller) {       
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> favoritesFiles = null;
        try {
            favoritesFiles = fileManager.findFiles(dataSource, "%.url", "Favorites");
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history.", ex);
            this.addErrorMessage(this.getName() + ": Error getting Internet Explorer Bookmarks.");
            return;
        }

        for (AbstractFile favoritesFile : favoritesFiles) {
            if (controller.isCancelled()) {
                break;
            }
            Content fav = favoritesFile;
            byte[] t = new byte[(int) fav.getSize()];
            try {
                final int bytesRead = fav.read(t, 0, fav.getSize());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error reading bytes of Internet Explorer favorite.", ex);
                this.addErrorMessage(this.getName() + ": Error reading Internet Explorer Bookmark file " + favoritesFile.getName());
                continue;
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
            String name = favoritesFile.getName();
            Long datetime = favoritesFile.getCrtime();
            String Tempdate = datetime.toString();
            datetime = Long.valueOf(Tempdate);
            String domain = Util.extractDomain(url);

            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
            //TODO revisit usage of deprecated constructor as per TSK-583
            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", datetime));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "RecentActivity", datetime));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", url));
            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", EscapeUtil.decodeURL(url)));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", name));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
            this.addArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK, favoritesFile, bbattributes);

            services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
        }
    }

    //Cookies section
    // This gets the cookies info
    private void getCookie(Content dataSource, IngestDataSourceWorkerController controller) {
        
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles = null;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "%.txt", "Cookies");
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history.");
            this.addErrorMessage(this.getName() + ": " + "Error getting Internet Explorer cookie files.");
            return;
        }

        for (AbstractFile cookiesFile : cookiesFiles) {
            if (controller.isCancelled()) {
                break;
            }
            Content fav = cookiesFile;
            byte[] t = new byte[(int) fav.getSize()];
            try {
                final int bytesRead = fav.read(t, 0, fav.getSize());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error reading bytes of Internet Explorer cookie.", ex);
                this.addErrorMessage(this.getName() + ": Error reading Internet Explorer cookie " + cookiesFile.getName());
                continue;
            }
            String cookieString = new String(t);
            String[] values = cookieString.split("\n");
            String url = values.length > 2 ? values[2] : "";
            String value = values.length > 1 ? values[1] : "";
            String name = values.length > 0 ? values[0] : "";
            Long datetime = cookiesFile.getCrtime();
            String tempDate = datetime.toString();
            datetime = Long.valueOf(tempDate);
            String domain = Util.extractDomain(url);

            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", url));
            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", EscapeUtil.decodeURL(url)));
            //TODO Revisit usage of deprecated Constructor as of TSK-583
            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Last Visited", datetime));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", datetime));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", value));
            //TODO Revisit usage of deprecated Constructor as of TSK-583
            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "Title", (name != null) ? name : ""));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", (name != null) ? name : ""));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
            this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);
        }

        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
    }

    //Recent Documents section
    // This gets the recent object info
    private void getRecentDocuments(Content dataSource, IngestDataSourceWorkerController controller) {
        
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> recentFiles = null;
        try {
            recentFiles = fileManager.findFiles(dataSource, "%.lnk", "Recent");
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history.");
            this.addErrorMessage(this.getName() + ": Error getting Recent Files.");
            return;
        }

        for (AbstractFile recentFile : recentFiles) {
            if (controller.isCancelled()) {
                break;
            }
            Content fav = recentFile;
            if (fav.getSize() == 0) {
                continue;
            }
            JLNK lnk = null;
            JLnkParser lnkParser = new JLnkParser(new ReadContentInputStream(fav), (int) fav.getSize());
            try {
                lnk = lnkParser.parse();
            } catch (Exception e) {
                //TODO should throw a specific checked exception
                logger.log(Level.SEVERE, "Error lnk parsing the file to get recent files" + recentFile);
                this.addErrorMessage(this.getName() + ": Error parsing Recent File " + recentFile.getName());
                continue;
            }
            String path = lnk.getBestPath();
            Long datetime = recentFile.getCrtime();

            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), "RecentActivity", path));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", Util.getFileName(path)));
            long id = Util.findID(dataSource, path);
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(), "RecentActivity", id));
            //TODO Revisit usage of deprecated constructor as per TSK-583
            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Date Created", datetime));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", datetime));
            this.addArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT, recentFile, bbattributes);
        }

        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT));
    }


    private void extractAndRunPasco(Content dataSource, IngestDataSourceWorkerController controller) {
        pascoResults = new ArrayList<String>();

        logger.log(Level.INFO, "Pasco results path: " + moduleTempResultsDir);

        final File pascoRoot = InstalledFileLocator.getDefault().locate("pasco2", ExtractIE.class.getPackage().getName(), false);
        if (pascoRoot == null) {
            logger.log(Level.SEVERE, "Pasco2 not found");
            pascoFound = false;
            return;
        } else {
            pascoFound = true;
        }

        final String pascoHome = pascoRoot.getAbsolutePath();
        logger.log(Level.INFO, "Pasco2 home: " + pascoHome);

        PASCO_LIB_PATH = pascoHome + File.separator + "pasco2.jar" + File.pathSeparator
                + pascoHome + File.separator + "*";

        File resultsDir = new File(moduleTempResultsDir);
        resultsDir.mkdirs();
     
 
        // get index.dat files
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> indexFiles = null;
        try {
            indexFiles = fileManager.findFiles(dataSource, "index.dat");
        } catch (TskCoreException ex) {
            this.addErrorMessage(this.getName() + ": Error getting Internet Explorer history files");
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history.");
            return;
        }

        String temps;
        String indexFileName;
        for (AbstractFile indexFile : indexFiles) {
            // Since each result represent an index.dat file,
            // just create these files with the following notation:
            // index<Number>.dat (i.e. index0.dat, index1.dat,..., indexN.dat)
            // Write each index.dat file to a temp directory.
            //BlackboardArtifact bbart = fsc.newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
            indexFileName = "index" + Integer.toString((int) indexFile.getId()) + ".dat";
            //indexFileName = "index" + Long.toString(bbart.getArtifactID()) + ".dat";
            temps = RAImageIngestModule.getRATempPath(currentCase, "IE") + File.separator + indexFileName;
            File datFile = new File(temps);
            if (controller.isCancelled()) {
                datFile.delete();
                break;
            }
            try {
                ContentUtils.writeToFile(indexFile, datFile);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error while trying to write index.dat file " + datFile.getAbsolutePath(), e);
                this.addErrorMessage(this.getName() + ": Error while trying to write file:" + datFile.getAbsolutePath());
                continue;
            }

            String filename = "pasco2Result." + indexFile.getId() + ".txt";
            boolean bPascProcSuccess = executePasco(temps, filename);
            pascoResults.add(filename);

            //At this point pasco2 proccessed the index files.
            //Now fetch the results, parse them and the delete the files.
            if (bPascProcSuccess) {

                //Delete index<n>.dat file since it was succcessfully by Pasco
                datFile.delete();
            }
        }
    }

    //Simple wrapper to JavaSystemCaller.Exec() to execute pasco2 jar
    // TODO: Hardcoded command args/path needs to be removed. Maybe set some constants and set env variables for classpath
    // I'm not happy with this code. Can't stand making a system call, is not an acceptable solution but is a hack for now.
    private boolean executePasco(String indexFilePath, String filename) {
        if (pascoFound == false) {
            return false;
        }
        boolean success = true;

        Writer writer = null;
        try {
            final String pascoOutFile = moduleTempResultsDir + File.separator + filename;
            logger.log(Level.INFO, "Writing pasco results to: " + pascoOutFile);
            writer = new FileWriter(pascoOutFile);
            execPasco = new ExecUtil();
            execPasco.execute(writer, JAVA_PATH, 
                    "-cp", PASCO_LIB_PATH, 
                    "isi.pasco2.Main", "-T", "history", indexFilePath );
            // @@@ Investigate use of history versus cache as type.
        } catch (IOException ex) {
            success = false;
            logger.log(Level.SEVERE, "Unable to execute Pasco to process Internet Explorer web history.", ex);
        } catch (InterruptedException ex) {
            success = false;
            logger.log(Level.SEVERE, "Pasco has been interrupted, failed to extract some web history from Internet Explorer.", ex);
        }
        finally {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error closing writer stream after for Pasco result", ex);
                }
            }
        }

        return success;
    }

    private void getHistory(List<String> filenames) {
        // Make sure pasco and the results exist
        File rFile = new File(moduleTempResultsDir);
        if (pascoFound == false || ! rFile.exists()) {
            return;
        }

        //Give me a list of pasco results in that directory
        File[] pascoFiles = rFile.listFiles();
        for (File file : pascoFiles) {
            String fileName = file.getName();
            if (!filenames.contains(fileName)) {
                logger.log(Level.INFO, "Found a temp Pasco result file not in the list: {0}", fileName);
                continue;
            }

            // Make sure the file the is not empty or the Scanner will
            // throw a "No Line found" Exception
            if (file.length() == 0) {
                continue;
            }

            long artObjId = Long.parseLong(fileName.substring(fileName.indexOf(".") + 1, fileName.lastIndexOf(".")));
            Scanner fileScanner;
            try {
                fileScanner = new Scanner(new FileInputStream(file.toString()));
            } catch (FileNotFoundException ex) {
                this.addErrorMessage(this.getName() + ": Error parsing IE history entry " + file.getName());
                logger.log(Level.WARNING, "Unable to find the Pasco file at " + file.getPath(), ex);
                continue;
            }
            
            while (fileScanner.hasNext()) {
                String line = fileScanner.nextLine();
                if (!line.startsWith("URL")) {  
                    continue;
                }

                String[] lineBuff = line.split("\\t");

                if (lineBuff.length < 4) {
                    logger.log(Level.INFO, "Found unrecognized IE history format.");
                    continue;
                }

                String ddtime = lineBuff[2];
                String actime = lineBuff[3];
                Long ftime = (long) 0;
                String user = "";
                String realurl = "";
                String domain = "";

                /* We've seen two types of lines: 
                 * URL  http://XYZ.com ....
                 * URL  Visited: Joe@http://XYZ.com ....
                 */
                if (lineBuff[1].contains("@")) {
                    String url[] = lineBuff[1].split("@", 2);
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
                } else {
                    user = "";
                    realurl = lineBuff[1].trim();
                }

                domain = Util.extractDomain(realurl);

                if (!ddtime.isEmpty()) {
                    ddtime = ddtime.replace("T", " ");
                    ddtime = ddtime.substring(ddtime.length() - 5);
                }

                if (!actime.isEmpty()) {
                    try {
                        Long epochtime = dateFormatter.parse(actime).getTime();
                        ftime = epochtime.longValue();
                        ftime = ftime / 1000;
                    } catch (ParseException e) {
                        this.addErrorMessage(this.getName() + ": Error parsing Internet Explorer History entry.");
                        logger.log(Level.SEVERE, "Error parsing Pasco results.", e);
                    }
                }

                try {
                    BlackboardArtifact bbart = tskCase.getContentById(artObjId).newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", realurl));
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", EscapeUtil.decodeURL(realurl)));

                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "RecentActivity", ftime));

                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "RecentActivity", ""));

                    //   bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", ddtime));

                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME.getTypeID(), "RecentActivity", user));
                    bbart.addAttributes(bbattributes);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error writing Internet Explorer web history artifact to the blackboard.", ex);
                }                                    
            }
            fileScanner.close();
        }
        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
    }

    @Override
    public void complete() {
        // Delete all the results when complete
        /*for (String file : pascoResults) {
            String filePath = moduleTempResultsDir + File.separator + file;
            try {
                File f = new File(filePath);
                if (f.exists() && f.canWrite()) {
                    f.delete();
                } else {
                    logger.log(Level.WARNING, "Unable to delete file " + filePath);
                }
            } catch (SecurityException ex) {
                logger.log(Level.WARNING, "Incorrect permission to delete file " + filePath, ex);
            }
        }
        */
        pascoResults.clear();
        logger.info("Internet Explorer extract has completed.");
    }

    @Override
    public void stop() {
        if (execPasco != null) {
            execPasco.stop();
            execPasco = null;
        }
        
        //call regular cleanup from complete() method
        complete();
    }

    @Override
    public String getDescription() {
        return "Extracts activity from Internet Explorer browser, as well as recent documents in windows.";
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}