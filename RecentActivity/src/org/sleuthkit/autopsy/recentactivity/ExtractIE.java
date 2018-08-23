/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_ACCOUNT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extracts activity from Internet Explorer browser, as well as recent documents
 * in windows.
 */
class ExtractIE extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractIE.class.getName());
    private static final String PARENT_MODULE_NAME
            = NbBundle.getMessage(ExtractIE.class, "ExtractIE.parentModuleName.noSpace");
    private static final String PASCO_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private final String moduleTempResultsDir;
    private final String JAVA_PATH = PlatformUtil.getJavaPath();

    private Content dataSource;
    private IngestJobContext context;

    ExtractIE() throws NoCurrentCaseException {
        moduleTempResultsDir = RAImageIngestModule.getRATempPath(Case.getCurrentCaseThrows(), "IE") + File.separator + "results"; //NON-NLS
    }

    @Override
    protected String getModuleName() {
        return NbBundle.getMessage(ExtractIE.class, "ExtractIE.moduleName.text");
    }

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        this.getBookmark();
        this.getCookie();
        this.getHistory();
    }

    /**
     * Finds the files storing bookmarks and creates artifacts
     */
    private void getBookmark() {
        List<AbstractFile> favoritesFiles;
        try {
            favoritesFiles = fileManager.findFiles(dataSource, "%.url", "Favorites"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'url' files for Internet Explorer bookmarks.", ex); //NON-NLS
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractIE.getBookmark.errMsg.errGettingBookmarks",
                            this.getModuleName()));
            return;
        }

        if (favoritesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any IE bookmark files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        for (AbstractFile fav : favoritesFiles) {
            if (fav.getSize() == 0) {
                continue;
            }

            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                    new BlackboardAttribute(
                            TSK_URL, PARENT_MODULE_NAME, getURLFromIEBookmarkFile(fav)),
                    new BlackboardAttribute(
                            TSK_TITLE, PARENT_MODULE_NAME, fav.getName()),
                    new BlackboardAttribute(
                            TSK_DATETIME_CREATED, PARENT_MODULE_NAME, fav.getCrtime()),
                    new BlackboardAttribute(
                            TSK_PROG_NAME, PARENT_MODULE_NAME,
                            NbBundle.getMessage(this.getClass(), "ExtractIE.moduleName.text")),
                    new BlackboardAttribute(
                            TSK_DOMAIN, PARENT_MODULE_NAME, Util.extractDomain(getURLFromIEBookmarkFile(fav))));
            try {
                BlackboardArtifact bbart = fav.newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                bbart.addAttributes(bbattributes);
                bbartifacts.add(bbart);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error while trying to create Internet Explorer  bookmark artifact.", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(Chrome.class, "ExtractIE.getBookmark.errMsg.errGettingBookmarks", //NON-NLS
                                this.getModuleName(), fav.getName()));
            }
        }
        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Internet Explorer bookmark artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    private String getURLFromIEBookmarkFile(AbstractFile fav) {
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ReadContentInputStream(fav)));) {
            while (null != (line = reader.readLine())) {
                // The actual shortcut line we are interested in is of the
                // form URL=http://path/to/website
                if (line.startsWith("URL")) { //NON-NLS
                    return StringUtils.substringAfter(line, "="); //NON-NLS
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to read from content: " + fav.getName(), ex); //NON-NLS
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractIE.getURLFromIEBmkFile.errMsg", this.getModuleName(),
                            fav.getName()));
        } catch (IndexOutOfBoundsException ex) {
            logger.log(Level.WARNING, "Failed while getting URL of IE bookmark. Unexpected format of the bookmark file: " + fav.getName(), ex); //NON-NLS
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractIE.getURLFromIEBmkFile.errMsg2", this.getModuleName(),
                            fav.getName()));
        }

        return "";
    }

    /**
     * Finds files that store cookies and adds artifacts for them.
     */
    private void getCookie() {
        List<AbstractFile> cookiesFiles;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "%.txt", "Cookies"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting cookie files for IE"); //NON-NLS
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractIE.getCookie.errMsg.errGettingFile", this.getModuleName()));
            return;
        }

        if (cookiesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any IE cookies files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        for (AbstractFile cookiesFile : cookiesFiles) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }
            if (cookiesFile.getSize() == 0) {
                continue;
            }

            byte[] cookiesBuffer = new byte[(int) cookiesFile.getSize()];
            try {
                cookiesFile.read(cookiesBuffer, 0, cookiesFile.getSize());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error reading bytes of Internet Explorer cookie.", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractIE.getCookie.errMsg.errReadingIECookie",
                                this.getModuleName(), cookiesFile.getName()));
                continue;
            }

            String[] values = new String(cookiesBuffer).split("\n");
            String URL = values.length > 2 ? values[2] : "";

            Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                    new BlackboardAttribute(
                            TSK_DATETIME, PARENT_MODULE_NAME,
                            cookiesFile.getCrtime()),
                    new BlackboardAttribute(
                            TSK_NAME, PARENT_MODULE_NAME,
                            values.length > 0 ? values[0] : ""),
                    new BlackboardAttribute(
                            TSK_VALUE, PARENT_MODULE_NAME,
                            values.length > 1 ? values[1] : ""),
                    new BlackboardAttribute(
                            TSK_URL, PARENT_MODULE_NAME,
                            URL),
                    new BlackboardAttribute(
                            TSK_PROG_NAME, PARENT_MODULE_NAME,
                            getModuleName()),
                    new BlackboardAttribute(
                            TSK_DOMAIN, PARENT_MODULE_NAME,
                            Util.extractDomain(URL)));
            try {
                BlackboardArtifact bbart = cookiesFile.newArtifact(TSK_WEB_COOKIE);
                bbart.addAttributes(bbattributes);
                bbartifacts.add(bbart);
            } catch (TskCoreException ex) {
                this.addErrorMessage(
                        NbBundle.getMessage(Chrome.class, "ExtractIE.getCookie.errMsg.errReadingIECookie", //NON-NLS
                                this.getModuleName(), cookiesFile.getName()));

            }
        }
        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Internet Explorer cookie artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Locates index.dat files, runs Pasco on them, and creates artifacts.
     */
    private void getHistory() {
        logger.log(Level.INFO, "Pasco results path: {0}", moduleTempResultsDir); //NON-NLS

        //JIRA-2385: Why are we getting the pasco library path for datasource we process?
        final File pascoRoot = InstalledFileLocator.getDefault().locate("pasco2", ExtractIE.class.getPackage().getName(), false); //NON-NLS
        if (pascoRoot == null) {
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractIE.getHistory.errMsg.unableToGetHist", this.getModuleName()));
            logger.log(Level.SEVERE, "Error finding pasco program "); //NON-NLS
            return;
        }

        final String pascoHome = pascoRoot.getAbsolutePath();
        logger.log(Level.INFO, "Pasco2 home: {0}", pascoHome); //NON-NLS

        String pascoLibPath = pascoHome + File.separator + "pasco2.jar" + File.pathSeparator //NON-NLS
                              + pascoHome + File.separator + "*";

        File resultsDir = new File(moduleTempResultsDir);
        resultsDir.mkdirs();

        // get index.dat files
        List<AbstractFile> indexFiles;
        try {
            indexFiles = fileManager.findFiles(dataSource, "index.dat"); //NON-NLS
        } catch (TskCoreException ex) {
            this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractIE.getHistory.errMsg.errGettingHistFiles",
                    this.getModuleName()));
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history."); //NON-NLS
            return;
        }

        if (indexFiles.isEmpty()) {
            String msg = NbBundle.getMessage(this.getClass(), "ExtractIE.getHistory.errMsg.noHistFiles");
            logger.log(Level.INFO, msg);
            return;
        }

        dataFound = true;
        boolean foundHistory = false;
        Collection<BlackboardArtifact> historyArtifacts = new ArrayList<>();
        Collection<BlackboardArtifact> accountArtifacts = new ArrayList<>();

        for (AbstractFile indexFile : indexFiles) {
            /*
             * Since each result represent an index.dat file, just create these
             * files with the following notation: index<Number>.dat (i.e.
             * index0.dat, index1.dat,..., indexN.dat), where <Number> is the
             * obj_id of the file. Write each index.dat file to a temp
             * directory. *
             */
            String indexFileName = "index" + indexFile.getId() + ".dat"; //NON-NLS
            String temps = RAImageIngestModule.getRATempPath(currentCase, "IE") + File.separator + indexFileName; //NON-NLS
            File datFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }
            try {
                ContentUtils.writeToFile(indexFile, datFile, context::dataSourceIngestIsCancelled);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error while trying to write index.dat file " + datFile.getAbsolutePath(), e); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractIE.getHistory.errMsg.errWriteFile", this.getModuleName(),
                                datFile.getAbsolutePath()));
                continue;
            }

            String filename = "pasco2Result." + indexFile.getId() + ".txt"; //NON-NLS
            boolean bPascProcSuccess = executePasco(pascoLibPath, temps, filename);
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            //At this point pasco2 proccessed the index file.
            //Now fetch the results, parse them and the delete the file.
            if (bPascProcSuccess) {
                // Don't add TSK_OS_ACCOUNT artifacts to the ModuleDataEvent
                HashMultimap<ARTIFACT_TYPE, BlackboardArtifact> artifacts = parsePascoOutput(indexFile, filename);
                historyArtifacts.addAll(artifacts.get(TSK_WEB_HISTORY));
                accountArtifacts.addAll(artifacts.get(TSK_OS_ACCOUNT));
                foundHistory = true;

                //Delete index<n>.dat file since it was succcessfully parsed by Pasco
                datFile.delete();
            } else {
                logger.log(Level.WARNING, "pasco execution failed on: {0}", filename); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractIE.getHistory.errMsg.errProcHist", this.getModuleName()));
            }
        }

        if (foundHistory) {
            try {
                blackboard.postArtifacts(historyArtifacts, PARENT_MODULE_NAME);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Error while trying to post Internet Explorer history artifact.", ex); //NON-NLS
                this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
            }
            try {
                blackboard.postArtifacts(accountArtifacts, PARENT_MODULE_NAME);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Error while trying to post Internet Explorer os account artifact.", ex); //NON-NLS
                this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
            }
        }
    }

    /**
     * Execute pasco on a single file that has been saved to disk.
     *
     * @param indexFilePath  Path to local index.dat file to analyze
     * @param outputFileName Name of file to save output to
     *
     * @return Success. False if there is an error executing pasco.
     */
    private boolean executePasco(String pascoLibraryPath, String indexFilePath, String outputFileName) {
        boolean success = true;
        try {
            final String outputFileFullPath = moduleTempResultsDir + File.separator + outputFileName;
            final String errFileFullPath = moduleTempResultsDir + File.separator + outputFileName + ".err"; //NON-NLS
            logger.log(Level.INFO, "Writing pasco results to: {0}", outputFileFullPath); //NON-NLS   
            List<String> commandLine = Arrays.asList(
                    JAVA_PATH,
                    "-cp",//NON-NLS
                    pascoLibraryPath,
                    "isi.pasco2.Main", //NON-NLS
                    "-T", //NON-NLS
                    "history", //NON-NLS
                    indexFilePath);
            ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
            processBuilder.redirectOutput(new File(outputFileFullPath));
            processBuilder.redirectError(new File(errFileFullPath));
            /*
             * NOTE on Pasco return codes: There is no documentation for Pasco.
             * Looking at the Pasco source code I see that when something goes
             * wrong Pasco returns a negative number as a return code. However,
             * we should still attempt to parse the Pasco output even if that
             * happens. I have seen many situations where Pasco output file
             * contains a lot of useful data and only the last entry is
             * corrupted.
             */
            ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context));
            // @@@ Investigate use of history versus cache as type.
        } catch (IOException ex) {
            success = false;
            logger.log(Level.SEVERE, "Unable to execute Pasco to process Internet Explorer web history.", ex); //NON-NLS
        }
        return success;
    }

    /**
     * parse Pasco output and create artifacts
     *
     * @param origFile            Original index.dat file that was analyzed to
     *                            get this output
     * @param pascoOutputFileName name of pasco output file
     *
     * @return A collection of created artifacts
     */
    private HashMultimap<BlackboardArtifact.ARTIFACT_TYPE, BlackboardArtifact> parsePascoOutput(AbstractFile origFile, String pascoOutputFileName) {
        HashMultimap<BlackboardArtifact.ARTIFACT_TYPE, BlackboardArtifact> bbartifacts = HashMultimap.create();

        String fnAbs = moduleTempResultsDir + File.separator + pascoOutputFileName;

        File file = new File(fnAbs);
        if (file.exists() == false) {
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractIE.parsePascoOutput.errMsg.notFound", this.getModuleName(),
                            file.getName()));
            logger.log(Level.WARNING, "Pasco Output not found: {0}", file.getPath()); //NON-NLS
            return bbartifacts;
        }

        // Make sure the file the is not empty or the Scanner will
        // throw a "No Line found" Exception
        if (file.length() == 0) {
            return bbartifacts;
        }
        try (Scanner fileScanner = new Scanner(new FileInputStream(file.toString()));) {

            // Keep a list of reported user accounts to avoid repeats.
            // Initialize it with the empty string to represent an unknown user.
            Set<String> reportedUserAccounts = Sets.newHashSet("");
            while (fileScanner.hasNext()) {
                String line = fileScanner.nextLine();
                if (!line.startsWith("URL")) {   //NON-NLS
                    continue;
                }

                String[] lineBuff = line.split("\\t"); //NON-NLS

                if (lineBuff.length < 4) {
                    logger.log(Level.INFO, "Found unrecognized IE history format."); //NON-NLS
                    continue;
                }

                String actime = lineBuff[3];
                Long ftime = (long) 0;
                String user;
                String realurl;
                String domain;

                /*
                 * We've seen two types of lines: URL http://XYZ.com .... URL
                 * Visited: Joe@http://XYZ.com ....
                 */
                if (lineBuff[1].contains("@")) {
                    String url[] = lineBuff[1].split("@", 2);
                    user = url[0];
                    user = user.replace("Visited:", ""); //NON-NLS
                    user = user.replace(":Host:", ""); //NON-NLS
                    user = user.replaceAll(":(.*?):", "");
                    user = user.trim();
                    realurl = url[1];
                    realurl = realurl.replace("Visited:", ""); //NON-NLS
                    realurl = realurl.replaceAll(":(.*?):", "");
                    realurl = realurl.replace(":Host:", ""); //NON-NLS
                    realurl = realurl.trim();
                } else {
                    user = "";
                    realurl = lineBuff[1].trim();
                }

                domain = Util.extractDomain(realurl);

                if (!actime.isEmpty()) {
                    try {
                        Long epochtime = new SimpleDateFormat(PASCO_DATE_FORMAT).parse(actime).getTime();
                        ftime = epochtime / 1000;
                    } catch (ParseException e) {
                        this.addErrorMessage(
                                NbBundle.getMessage(this.getClass(), "ExtractIE.parsePascoOutput.errMsg.errParsingEntry",
                                        this.getModuleName()));
                        logger.log(Level.WARNING, String.format("Error parsing Pasco results, may have partial processing of corrupt file (id=%d)", origFile.getId()), e); //NON-NLS
                    }
                }

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                realurl),
                        new BlackboardAttribute(
                                TSK_DATETIME_ACCESSED, PARENT_MODULE_NAME,
                                ftime),
                        //JIRA-2386: why are we adding an attribute that is always blank?
                        new BlackboardAttribute(
                                TSK_REFERRER, PARENT_MODULE_NAME,
                                ""),
                        // @@@ NOte that other browser modules are adding TITLE in here for the title
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName()),
                        new BlackboardAttribute(
                                TSK_DOMAIN, PARENT_MODULE_NAME,
                                domain),
                        new BlackboardAttribute(
                                TSK_USER_NAME, PARENT_MODULE_NAME,
                                user));
                try {
                    BlackboardArtifact bbart = origFile.newArtifact(TSK_WEB_HISTORY);
                    bbart.addAttributes(bbattributes);
                    bbartifacts.put(TSK_WEB_HISTORY, bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to create Internet Explorer history artifact.", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(Chrome.class, "ExtractIE.getHistory.errMsg.errProcHist", //NON-NLS
                                    origFile.getName()));
                }

                if (reportedUserAccounts.contains(user) == false) {
                    try {
                        BlackboardArtifact osAttr = origFile.newArtifact(TSK_OS_ACCOUNT);
                        osAttr.addAttribute(new BlackboardAttribute(TSK_USER_NAME, PARENT_MODULE_NAME, user));
                        bbartifacts.put(TSK_OS_ACCOUNT, osAttr);
                        reportedUserAccounts.add(user);
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "Error while trying to create Internet Explorer  os account artifact.", ex); //NON-NLS
                        this.addErrorMessage(
                                NbBundle.getMessage(Chrome.class, "ExtractIE.getHistory.errMsg.errProcHist", //NON-NLS
                                        origFile.getName()));
                    }
                }
            }
            return bbartifacts;
        } catch (FileNotFoundException ex) {
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractIE.parsePascoOutput.errMsg.errParsing", this.getModuleName(),
                            file.getName()));
            logger.log(Level.WARNING, "Unable to find the Pasco file at " + file.getPath(), ex); //NON-NLS
            return bbartifacts;
        }
    }
}
