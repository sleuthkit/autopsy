/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extract the bookmarks, cookies, downloads and history from Microsoft Edge
 */
final class ExtractEdge extends Extract {

    private static final Logger LOG = Logger.getLogger(ExtractEdge.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private final Path moduleTempResultPath;
    private Content dataSource;
    private IngestJobContext context;
    private HashMap<String, ArrayList<String>> containersTable;

    private static final String EDGE = "Edge"; //NON-NLS

    private static final String EDGE_KEYWORD_VISIT = "Visited:"; //NON-NLS
    private static final String IGNORE_COMMA_IN_QUOTES_REGEX = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"; //NON-NLS

    private static final String EDGE_TABLE_TYPE_DOWNLOAD = "iedownload"; //NON-NLS
    private static final String EDGE_TABLE_TYPE_HISTORY = "History"; //NON-NLS
    private static final String EDGE_TABLE_TYPE_COOKIE = "cookie"; //NON-NLS

    private static final String EDGE_HEAD_URL = "url"; //NON-NLS
    private static final String EDGE_HEAD_ACCESSTIME = "accessedtime"; //NON-NLS
    private static final String EDGE_HEAD_NAME = "name"; //NON-NLS
    private static final String EDGE_HEAD_CONTAINER_ID = "containerid"; //NON-NLS
    private static final String EDGE_HEAD_RESPONSEHEAD = "responseheaders"; //NON-NLS
    private static final String EDGE_HEAD_TITLE = "title"; //NON-NLS
    private static final String EDGE_HEAD_RDOMAIN = "rdomain"; //NON-NLS
    private static final String EDGE_HEAD_VALUE = "value"; //NON-NLS
    private static final String EDGE_HEAD_LASTMOD = "lastmodified"; //NON-NLS

    private static final String EDGE_WEBCACHE_PREFIX = "WebCacheV01"; //NON-NLS
    private static final String EDGE_CONTAINER_FILE_PREFIX = "Container_"; //NON-NLS
    private static final String EDGE_CONTAINER_FILE_EXT = ".csv"; //NON-NLS
    private static final String EDGE_WEBCACHE_EXT = ".dat"; //NON-NLS

    private static final String ESE_TOOL_NAME = "ESEDatabaseView.exe"; //NON-NLS
    private static final String EDGE_WEBCACHE_NAME = "WebCacheV01.dat"; //NON-NLS
    private static final String EDGE_SPARTAN_NAME = "Spartan.edb"; //NON-NLS
    private static final String EDGE_CONTAINTERS_FILE_NAME = "Containers.csv"; //NON-NLS
    private static final String EDGE_FAVORITE_FILE_NAME = "Favorites.csv"; //NON-NLS
    private static final String EDGE_OUTPUT_FILE_NAME = "Output.txt"; //NON-NLS
    private static final String EDGE_ERROR_FILE_NAME = "File.txt"; //NON-NLS
    private static final String EDGE_WEBCACHE_FOLDER_NAME = "WebCache"; //NON-NLS
    private static final String EDGE_SPARTAN_FOLDER_NAME = "MicrosoftEdge"; //NON-NLS

    private static final String ESE_TOOL_FOLDER = "ESEDatabaseView"; //NON-NLS
    private static final String EDGE_RESULT_FOLDER_NAME = "results"; //NON-NLS

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a"); //NON-NLS

    @Messages({
        "ExtractEdge_process_errMsg_unableFindESEViewer=Unable to find ESEDatabaseViewer",
        "ExtractEdge_process_errMsg_errGettingWebCacheFiles=Error trying to retrieving Edge WebCacheV01 file",
        "ExtractEdge_process_errMsg_webcacheFail=Failure processing Microsoft Edge WebCacheV01.dat file",
        "ExtractEdge_process_errMsg_spartanFail=Failure processing Microsoft Edge spartan.edb file",
        "ExtractEdge_Module_Name=Microsoft Edge",
        "ExtractEdge_getHistory_containerFileNotFound=Error while trying to analyze Edge history",
        "Progress_Message_Edge_History=Microsoft Edge History",
        "Progress_Message_Edge_Bookmarks=Microsoft Edge Bookmarks",
        "Progress_Message_Edge_Cookies=Microsoft Edge Cookies",
    })

    /**
    * Extract the bookmarks, cookies, downloads and history from Microsoft Edge
    */
    ExtractEdge() throws NoCurrentCaseException {
        moduleTempResultPath = Paths.get(RAImageIngestModule.getRATempPath(Case.getCurrentCaseThrows(), EDGE), EDGE_RESULT_FOLDER_NAME);
    }

    @Override
    protected String getName() {
        return Bundle.ExtractEdge_Module_Name();
    }

    @Override
    void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        this.context = context;
        this.setFoundData(false);

        List<AbstractFile> webCacheFiles = null;
        List<AbstractFile> spartanFiles = null;

        try {
            webCacheFiles = fetchWebCacheDBFiles();
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractEdge_process_errMsg_errGettingWebCacheFiles());
            LOG.log(Level.SEVERE, "Error fetching 'WebCacheV01.dat' files for Microsoft Edge", ex); //NON-NLS
        }

        try {
            spartanFiles = fetchSpartanDBFiles(); // For later use with bookmarks
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractEdge_process_errMsg_spartanFail());
            LOG.log(Level.SEVERE, "Error fetching 'spartan.edb' files for Microsoft Edge", ex); //NON-NLS
        }

        // No edge files found 
        if (webCacheFiles == null && spartanFiles == null) {
            return;
        }

        this.setFoundData(true);

        if (!PlatformUtil.isWindowsOS()) {
            LOG.log(Level.WARNING, "Microsoft Edge files found, unable to parse on Non-Windows system"); //NON-NLS
            return;
        }

        final String esedumper = getPathForESEDumper();
        if (esedumper == null) {
            this.addErrorMessage(Bundle.ExtractEdge_process_errMsg_unableFindESEViewer());
            LOG.log(Level.SEVERE, "Error finding ESEDatabaseViewer program"); //NON-NLS
            return; //If we cannot find the ESEDatabaseView we cannot proceed
        }

        try {
            this.processWebCacheDbFile(esedumper, webCacheFiles, progressBar);
        } catch (IOException | TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractEdge_process_errMsg_webcacheFail());
            LOG.log(Level.SEVERE, "Error returned from processWebCacheDbFile", ex); // NON-NLS
        }

        progressBar.progress(Bundle.Progress_Message_Edge_Bookmarks());
        try {
            this.processSpartanDbFile(esedumper, spartanFiles);
        } catch (IOException | TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractEdge_process_errMsg_spartanFail());
            LOG.log(Level.SEVERE, "Error returned from processSpartanDbFile", ex); // NON-NLS
        }
    }

    /**
     * Process WebCacheV01.dat ese database file creating artifacts for cookies,
     * and history contained within.
     *
     * @param eseDumperPath Path to ESEDatabaseView.exe
     * @param webCacheFiles List of case WebCacheV01.dat files
     * @throws IOException
     * @throws TskCoreException
     */
    void processWebCacheDbFile(String eseDumperPath, List<AbstractFile> webCacheFiles, DataSourceIngestModuleProgress progressBar) throws IOException, TskCoreException {

        for (AbstractFile webCacheFile : webCacheFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }
            
            clearContainerTable();

            //Run the dumper 
            String tempWebCacheFileName = EDGE_WEBCACHE_PREFIX
                    + Integer.toString((int) webCacheFile.getId()) + EDGE_WEBCACHE_EXT; //NON-NLS
            File tempWebCacheFile = new File(RAImageIngestModule.getRATempPath(currentCase, EDGE), tempWebCacheFileName);

            try {
                ContentUtils.writeToFile(webCacheFile, tempWebCacheFile,
                        context::dataSourceIngestIsCancelled);
            } catch (IOException ex) {
                throw new IOException("Error writingToFile: " + webCacheFile, ex); //NON-NLS
            }

            File resultsDir = new File(moduleTempResultPath.toAbsolutePath() + Integer.toString((int) webCacheFile.getId()));
            resultsDir.mkdirs();
            try {
                executeDumper(eseDumperPath, tempWebCacheFile.getAbsolutePath(),
                        resultsDir.getAbsolutePath());

                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }
                
                progressBar.progress(Bundle.Progress_Message_Edge_History());
                
                this.getHistory(webCacheFile, resultsDir);

                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                progressBar.progress(Bundle.Progress_Message_Edge_Cookies());
                
                this.getCookies(webCacheFile, resultsDir);

            } finally {
                tempWebCacheFile.delete();
                FileUtil.deleteFileDir(resultsDir);
            }
        }
    }

    /**
     * Process spartan.edb ese database file creating artifacts for the bookmarks
     * contained within.
     *
     * @param eseDumperPath Path to ESEDatabaseViewer
     * @param spartanFiles List of the case spartan.edb files
     * @throws IOException
     * @throws TskCoreException
     */
    void processSpartanDbFile(String eseDumperPath, List<AbstractFile> spartanFiles) throws IOException, TskCoreException {

        for (AbstractFile spartanFile : spartanFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            //Run the dumper 
            String tempSpartanFileName = EDGE_WEBCACHE_PREFIX
                    + Integer.toString((int) spartanFile.getId()) + EDGE_WEBCACHE_EXT; 
            File tempSpartanFile = new File(RAImageIngestModule.getRATempPath(currentCase, EDGE), tempSpartanFileName);

            try {
                ContentUtils.writeToFile(spartanFile, tempSpartanFile,
                        context::dataSourceIngestIsCancelled);
            } catch (IOException ex) {
                throw new IOException("Error writingToFile: " + spartanFile, ex); //NON-NLS
            }

            File resultsDir = new File(moduleTempResultPath.toAbsolutePath() + Integer.toString((int) spartanFile.getId()));
            resultsDir.mkdirs();
            try {
                executeDumper(eseDumperPath, tempSpartanFile.getAbsolutePath(),
                        resultsDir.getAbsolutePath());

                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                this.getBookmarks(spartanFile, resultsDir);

            } finally {
                tempSpartanFile.delete();
                FileUtil.deleteFileDir(resultsDir);
            }
        }
    }

    /**
     * getHistory searches the files with "container" in the file name for lines
     * with the text "Visited" in them. Note that not all of the container
     * files, if fact most of them do not, have the browser history in them.
     * @param origFile Original case file
     * @param resultDir Output directory of ESEDatabaseViewer
     * @throws TskCoreException
     * @throws FileNotFoundException 
     */
    private void getHistory(AbstractFile origFile, File resultDir) throws TskCoreException, FileNotFoundException {
        ArrayList<File> historyFiles = getHistoryFiles(resultDir);

        if (historyFiles == null) {
            return;
        }

        for (File file : historyFiles) {
            Scanner fileScanner;
            try {
                fileScanner = new Scanner(new FileInputStream(file.toString()));
            } catch (FileNotFoundException ex) {
                LOG.log(Level.WARNING, "Unable to find the ESEDatabaseView file at " + file.getPath(), ex); //NON-NLS
                continue; // If we couldn't open this file, continue to the next file
            }

            Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();

            try {
                List<String> headers = null;
                while (fileScanner.hasNext()) {
                    String line = fileScanner.nextLine();
                    if (headers == null) {
                        headers = Arrays.asList(line.toLowerCase().split(","));
                        continue;
                    }

                    if (line.contains(EDGE_KEYWORD_VISIT)) {
                        BlackboardArtifact ba = getHistoryArtifact(origFile, headers, line);
                        if (ba != null) {
                            bbartifacts.add(ba);
                            this.indexArtifact(ba);
                        }
                    }
                }
            } finally {
                fileScanner.close();
            }

            if (!bbartifacts.isEmpty()) {
                services.fireModuleDataEvent(new ModuleDataEvent(
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, bbartifacts));
            }
        }
    }

    /**
     * Search for bookmark files and make artifacts.
     *
     * @param origFile Original case file
     * @param resultDir Output directory of ESEDatabaseViewer
     * @throws TskCoreException
     * @throws FileNotFoundException
     */
    private void getBookmarks(AbstractFile origFile, File resultDir) throws TskCoreException {
        Scanner fileScanner;
        File favoriteFile = new File(resultDir, EDGE_FAVORITE_FILE_NAME);

        try {
            fileScanner = new Scanner(new FileInputStream(favoriteFile));
        } catch (FileNotFoundException ex) {
            // This is a non-fatal error, if the favorites file is not found
            // there might have not been any favorites\bookmarks
            return;
        }

        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();

        try {
            List<String> headers = null;
            while (fileScanner.hasNext()) {
                String line = fileScanner.nextLine();
                if (headers == null) {
                    headers = Arrays.asList(line.toLowerCase().split(","));
                    continue;
                }

                BlackboardArtifact ba = getBookmarkArtifact(origFile, headers, line);
                if (ba != null) {
                    bbartifacts.add(ba);
                    this.indexArtifact(ba);
                }
            }
        } finally {
            fileScanner.close();
        }

        if (!bbartifacts.isEmpty()) {
            services.fireModuleDataEvent(new ModuleDataEvent(
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, bbartifacts));
        }
    }

    /**
     * Queries for cookie files and adds artifacts.
     *
     * @param origFile Original case file
     * @param resultDir Output directory of ESEDatabaseViewer
     * @throws TskCoreException
     */
    private void getCookies(AbstractFile origFile, File resultDir) throws TskCoreException {
        File containerFiles[] = resultDir.listFiles((dir, name) -> name.toLowerCase().contains(EDGE_TABLE_TYPE_COOKIE));

        if (containerFiles == null) {
            return;
        }

        for (File file : containerFiles) {
            Scanner fileScanner;
            try {
                fileScanner = new Scanner(new FileInputStream(file.toString()));
            } catch (FileNotFoundException ex) {
                LOG.log(Level.WARNING, "Unable to find the ESEDatabaseView file at " + file.getPath(), ex); //NON-NLS
                continue; // If we couldn't open this file, continue to the next file
            }

            Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();

            try {
                List<String> headers = null;
                while (fileScanner.hasNext()) {
                    String line = fileScanner.nextLine();
                    if (headers == null) {
                        headers = Arrays.asList(line.toLowerCase().split(","));
                        continue;
                    }

                    BlackboardArtifact ba = getCookieArtifact(origFile, headers, line);
                    if (ba != null) {
                        bbartifacts.add(ba);
                        this.indexArtifact(ba);
                    }
                }
            } finally {
                fileScanner.close();
            }

            if (!bbartifacts.isEmpty()) {
                services.fireModuleDataEvent(new ModuleDataEvent(
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, bbartifacts));
            }
        }
    }

    /**
     * Queries for download files and adds artifacts.
     * 
     * Leaving for future use.
     * 
     * @param origFile Original case file
     * @param resultDir Output directory of ESEDatabaseViewer
     * @throws TskCoreException
     * @throws FileNotFoundException 
     */
    private void getDownloads(AbstractFile origFile, File resultDir) throws TskCoreException, FileNotFoundException {
        ArrayList<File> downloadFiles = getDownloadFiles(resultDir);

        if (downloadFiles == null) {
            return;
        }

        for (File file : downloadFiles) {
            Scanner fileScanner;
            try {
                fileScanner = new Scanner(new FileInputStream(file.toString()));
            } catch (FileNotFoundException ex) {
                LOG.log(Level.WARNING, "Unable to find the ESEDatabaseView file at " + file.getPath(), ex); //NON-NLS
                continue; // If we couldn't open this file, continue to the next file
            }
            Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();

            try {
                List<String> headers = null;
                while (fileScanner.hasNext()) {
                    String line = fileScanner.nextLine();
                    if (headers == null) {
                        headers = Arrays.asList(line.toLowerCase().split(","));
                        continue;
                    }

                    if (line.contains(EDGE_TABLE_TYPE_DOWNLOAD)) {

                        BlackboardArtifact ba = getDownloadArtifact(origFile, headers, line);
                        if (ba != null) {
                            bbartifacts.add(ba);
                            this.indexArtifact(ba);
                        }
                    }
                }
            } finally {
                fileScanner.close();
            }

            if (!bbartifacts.isEmpty()) {
                services.fireModuleDataEvent(new ModuleDataEvent(
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, bbartifacts));
            }
        }
    }

    /**
     * Find the location of ESEDatabaseViewer.exe
     *
     * @return Absolute path to ESEDatabaseViewer.exe or null if the file is not found
     */
    private String getPathForESEDumper() {
        Path path = Paths.get(ESE_TOOL_FOLDER, ESE_TOOL_NAME);
        File eseToolFile = InstalledFileLocator.getDefault().locate(path.toString(),
                ExtractEdge.class.getPackage().getName(), false);
        if (eseToolFile != null) {
            return eseToolFile.getAbsolutePath();
        }

        return null;
    }

    /**
     * Finds all of the WebCacheV01.dat files in the case
     *
     * @return A list of WebCacheV01.dat files, possibly empty if none are found
     * @throws TskCoreException
     */
    private List<AbstractFile> fetchWebCacheDBFiles() throws TskCoreException {
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager
                = currentCase.getServices().getFileManager();
        return fileManager.findFiles(dataSource, EDGE_WEBCACHE_NAME, EDGE_WEBCACHE_FOLDER_NAME);
    }

    /**
     * Finds all of the spartan.edb files in the case
     *
     * @return A list of spartan files, possibly empty if none are found
     * @throws TskCoreException
     */
    private List<AbstractFile> fetchSpartanDBFiles() throws TskCoreException {
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager
                = currentCase.getServices().getFileManager();
        return fileManager.findFiles(dataSource, EDGE_SPARTAN_NAME, EDGE_SPARTAN_FOLDER_NAME);
    }

    /**
     * Executes the ESEViewDumper on the given inputFile.
     *
     * Each table in the ese database will be dumped as a comma separated file
     * named <tableName>.csv
     *
     * @param dumperPath Path to ESEDatabaseView.exe
     * @param inputFilePath Path to ese database file to be dumped
     * @param outputDir Output directory for dumper
     * @throws IOException
     */
    private void executeDumper(String dumperPath, String inputFilePath,
            String outputDir) throws IOException {

        final Path outputFilePath = Paths.get(outputDir, EDGE_OUTPUT_FILE_NAME);
        final Path errFilePath = Paths.get(outputDir, EDGE_ERROR_FILE_NAME);
        LOG.log(Level.INFO, "Writing ESEDatabaseViewer results to: {0}", outputDir); //NON-NLS   

        List<String> commandLine = new ArrayList<>();
        commandLine.add(dumperPath);
        commandLine.add("/table");  //NON-NLS
        commandLine.add(inputFilePath);
        commandLine.add("*");  //NON-NLS
        commandLine.add("/scomma");  //NON-NLS
        commandLine.add(outputDir + "\\" + "*.csv");  //NON-NLS

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(outputFilePath.toFile());
        processBuilder.redirectError(errFilePath.toFile());

        ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context));
    }

    /**
     * Create a BlackboardArtifact for the given row from the Edge history
     * table.
     *
     * @param origFile Original case file
     * @param headers List of table headers
     * @param line CSV string representing a row of history table 
     * @return BlackboardArtifact representing one history table entry
     * @throws TskCoreException
     */
    private BlackboardArtifact getHistoryArtifact(AbstractFile origFile, List<String> headers, String line) throws TskCoreException {
        String[] rowSplit = line.split(",");

        int index = headers.indexOf(EDGE_HEAD_URL);
        String urlUserStr = rowSplit[index];

        String[] str = urlUserStr.split("@");
        String user = (str[0].replace(EDGE_KEYWORD_VISIT, "")).trim();
        String url = str[1];

        index = headers.indexOf(EDGE_HEAD_ACCESSTIME);
        String accessTime = rowSplit[index].trim();
        Long ftime = null;
        try {
            Long epochtime = DATE_FORMATTER.parse(accessTime).getTime();
            ftime = epochtime / 1000;
        } catch (ParseException ex) {
            LOG.log(Level.WARNING, "The Accessed Time format in history file seems invalid " + accessTime, ex); //NON-NLS
        }

        BlackboardArtifact bbart = origFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY);

        bbart.addAttributes(createHistoryAttribute(url, ftime,
                null, null,
                this.getName(),
                NetworkUtils.extractDomain(url), user));

        return bbart;
    }

    /**
     * Create a BlackboardArtifact for the given row from the Edge cookie table.
     *
     * @param origFile Original case file
     * @param headers List of table headers
     * @param line CSV string representing a row of cookie table 
     * @return BlackboardArtifact representing one cookie table entry
     * @throws TskCoreException
     */
    private BlackboardArtifact getCookieArtifact(AbstractFile origFile, List<String> headers, String line) throws TskCoreException {
        String[] lineSplit = line.split(","); // NON-NLS

        String accessTime = lineSplit[headers.indexOf(EDGE_HEAD_LASTMOD)].trim();
        Long ftime = null;
        try {
            Long epochtime = DATE_FORMATTER.parse(accessTime).getTime();
            ftime = epochtime / 1000;
        } catch (ParseException ex) {
            LOG.log(Level.WARNING, "The Accessed Time format in history file seems invalid " + accessTime, ex); //NON-NLS
        }

        String domain = lineSplit[headers.indexOf(EDGE_HEAD_RDOMAIN)].trim();
        String name = hexToChar(lineSplit[headers.indexOf(EDGE_HEAD_NAME)].trim());
        String value = hexToChar(lineSplit[headers.indexOf(EDGE_HEAD_VALUE)].trim());
        String url = flipDomain(domain);

        BlackboardArtifact bbart = origFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE);
        bbart.addAttributes(createCookieAttributes(url, ftime, name, value, this.getName(), NetworkUtils.extractDomain(url)));
        return bbart;
    }

    /**
     * Create a BlackboardArtifact for the given row from the Edge cookie table.
     *
     * This function is on hold for the moment. All of the information need
     * seems to be in decodedheader, but its not currently obvious how to pull
     * it apart.
     *
     * @param origFile Original case file
     * @param headers List of table headers
     * @param line CSV string representing a row of download table 
     * @return BlackboardArtifact representing one download table entry
     * @throws TskCoreException
     */
    private BlackboardArtifact getDownloadArtifact(AbstractFile origFile, List<String> headers, String line) throws TskCoreException {
        BlackboardArtifact bbart = null;
        
        String[] lineSplit = line.split(","); // NON-NLS
        String rheader = lineSplit[headers.indexOf(EDGE_HEAD_RESPONSEHEAD)];
        
        return bbart;
    }

    /**
     * Parse the comma separated row of information from the "Favorites" table
     * of the spartan database.
     *
     * Note: The "Favorites" table does not have a "Creation Time"
     *
     * @param origFile File the table came from ie spartan.edb
     * @param headers List of table column headers
     * @param line The line or row of the table to parse
     * @return BlackboardArtifact representation of the passed in line\table row or null if no Bookmark is found
     * @throws TskCoreException
     */
    private BlackboardArtifact getBookmarkArtifact(AbstractFile origFile, List<String> headers, String line) throws TskCoreException {
        // split on all commas as long as they are not inbetween quotes
        String[] lineSplit = line.split(IGNORE_COMMA_IN_QUOTES_REGEX, -1);

        String url = lineSplit[headers.indexOf(EDGE_HEAD_URL)];
        String title = lineSplit[headers.indexOf(EDGE_HEAD_TITLE)].replace("\"", ""); // NON-NLS

        if (url.isEmpty()) {
            return null;
        }

        BlackboardArtifact bbart = origFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
        bbart.addAttributes(createBookmarkAttributes(url, title, null,
                this.getName(), NetworkUtils.extractDomain(url)));
        return bbart;
    }

    /**
     * Converts a space separated string of hex values to ascii characters.
     *
     * @param hexString
     * @return "decoded" string or null if a non-hex value was found
     */
    private String hexToChar(String hexString) {
        String[] hexValues = hexString.split(" "); // NON-NLS
        StringBuilder output = new StringBuilder();

        for (String str : hexValues) {
            try {
                int value = Integer.parseInt(str, 16);
                if (value > 31) { // Ignore non-print characters
                    output.append((char) value);
                }
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        return output.toString();
    }

    /**
     * The RDomain in the WebCacheV01.data cookies tables are backwards, this
     * function corrects them.
     *
     * Values in the RDomain appear as either com.microsoft.www or com.microsoft
     * but for some reason there could also be "junk". the length checks are
     * there to weed out the "junk".
     *
     * @param domain
     * @return Correct domain string
     */
    private String flipDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return null;
        }

        String[] tokens = domain.split("\\."); // NON-NLS

        if (tokens.length < 2 || tokens.length > 3) {
            return domain; // don't know what to do, just send it back as is
        }

        StringBuilder buf = new StringBuilder();
        if (tokens.length > 2) {
            buf.append(tokens[2]);
            buf.append(".");
        }
        buf.append(tokens[1]);
        buf.append(".");
        buf.append(tokens[0]);

        return buf.toString();
    }

    /**
     * Returns a list the container files that have download information in
     * them.
     *
     * @param resultDir Path to ESEDatabaseViewer output
     * @return List of download table files
     */
    private ArrayList<File> getDownloadFiles(File resultDir) throws FileNotFoundException {
        return getContainerFiles(resultDir, EDGE_TABLE_TYPE_DOWNLOAD);
    }

    /**
     * Returns a list the container files that have history information in them.
     *
     * @param resultDir Path to ESEDatabaseViewer output
     * @return List of history table files
     * @throws FileNotFoundException
     */
    private ArrayList<File> getHistoryFiles(File resultDir) throws FileNotFoundException {
        return getContainerFiles(resultDir, EDGE_TABLE_TYPE_HISTORY);
    }

    /**
     * Returns a list of the containers files that are of the given type string
     *
     * @param resultDir Path to ESEDatabaseViewer output
     * @param type Type of table files 
     * @return List of table files returns null if no files of that type are found
     * @throws FileNotFoundException
     */
    private ArrayList<File> getContainerFiles(File resultDir, String type) throws FileNotFoundException {
        HashMap<String, ArrayList<String>> idTable = getContainerIDTable(resultDir);

        ArrayList<String> idList = idTable.get(type);
        if (idList == null) {
            return null;
        }

        ArrayList<File> fileList = new ArrayList<>();
        for (String str : idList) {
            String fileName = EDGE_CONTAINER_FILE_PREFIX + str + EDGE_CONTAINER_FILE_EXT;
            fileList.add(new File(resultDir, fileName));
        }

        return fileList;
    }

    /**
     * Opens and reads the Containers table to create a table of information
     * about which of the Continer_xx files contain which type of information.
     *
     * Each row of the "Containers" table describes one of the Container_xx
     * files.
     *
     * @param resultDir Path to ESEDatabaseViewer output
     * @return Hashmap with Key representing the table type, the value is a list of table ids for that type
     */
    private HashMap<String, ArrayList<String>> getContainerIDTable(File resultDir) throws FileNotFoundException {

        if (containersTable == null) {
            File containerFile = new File(resultDir, EDGE_CONTAINTERS_FILE_NAME);

            try (Scanner fileScanner = new Scanner(new FileInputStream(containerFile))) {
                List<String> headers = null;
                containersTable = new HashMap<>();
                int nameIdx = 0;
                int idIdx = 0;
                while (fileScanner.hasNext()) {
                    String line = fileScanner.nextLine();
                    if (headers == null) {
                        headers = Arrays.asList(line.toLowerCase().split(","));
                        nameIdx = headers.indexOf(EDGE_HEAD_NAME);
                        idIdx = headers.indexOf(EDGE_HEAD_CONTAINER_ID);
                    } else {
                        String[] row = line.split(","); // NON-NLS
                        String name = row[nameIdx];
                        String id = row[idIdx];

                        ArrayList<String> idList = containersTable.get(name);
                        if (idList == null) {
                            idList = new ArrayList<>();
                            containersTable.put(name, idList);
                        }

                        idList.add(id);
                    }
                }
            }
        }

        return containersTable;
    }
    
    /**
     * Clears the containerTable
     */
    private void clearContainerTable(){
        containersTable = null;
    }
}
