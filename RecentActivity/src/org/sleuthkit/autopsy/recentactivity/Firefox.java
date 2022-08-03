/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.WebBrowserArtifactsHelper;

@Messages({
    "Progress_Message_Firefox_History=Firefox History",
    "Progress_Message_Firefox_Bookmarks=Firefox Bookmarks",
    "Progress_Message_Firefox_Cookies=Firefox Cookies",
    "Progress_Message_Firefox_Downloads=Firefox Downloads",
    "Progress_Message_Firefox_FormHistory=Firefox Form History",
    "Progress_Message_Firefox_AutoFill=Firefox Auto Fill"
})

/**
 * Firefox recent activity extraction
 */
class Firefox extends Extract {

    private static final Logger logger = Logger.getLogger(Firefox.class.getName());
    private static final String PLACE_URL_PREFIX = "place:";
    private static final String HISTORY_QUERY = "SELECT moz_historyvisits.id, url, title, visit_count,(visit_date/1000000) AS visit_date,from_visit,"
            + "(SELECT url FROM moz_historyvisits history, moz_places places where history.id = moz_historyvisits.from_visit and history.place_id = places.id ) as ref "
            + "FROM moz_places, moz_historyvisits "
            + "WHERE moz_places.id = moz_historyvisits.place_id "
            + "AND hidden = 0"; //NON-NLS
    private static final String COOKIE_QUERY = "SELECT name,value,host,expiry,(lastAccessed/1000000) AS lastAccessed,(creationTime/1000000) AS creationTime FROM moz_cookies"; //NON-NLS
    private static final String COOKIE_QUERY_V3 = "SELECT name,value,host,expiry,(lastAccessed/1000000) AS lastAccessed FROM moz_cookies"; //NON-NLS
    private static final String BOOKMARK_QUERY = "SELECT fk, moz_bookmarks.title, url, (moz_bookmarks.dateAdded/1000000) AS dateAdded FROM moz_bookmarks INNER JOIN moz_places ON moz_bookmarks.fk=moz_places.id"; //NON-NLS
    private static final String DOWNLOAD_QUERY = "SELECT target, source,(startTime/1000000) AS startTime, maxBytes FROM moz_downloads"; //NON-NLS
    private static final String DOWNLOAD_QUERY_V24 = "SELECT url, content AS target, (lastModified/1000000) AS lastModified "
            + " FROM moz_places, moz_annos, moz_anno_attributes "
            + " WHERE moz_places.id = moz_annos.place_id"
            + " AND moz_annos.anno_attribute_id = moz_anno_attributes.id"
            + " AND moz_anno_attributes.name='downloads/destinationFileURI'"; //NON-NLS
    private static final String FORMHISTORY_QUERY = "SELECT fieldname, value FROM moz_formhistory";
    private static final String FORMHISTORY_QUERY_V64 = "SELECT fieldname, value, timesUsed, firstUsed, lastUsed FROM moz_formhistory";
    private Content dataSource;
    private final IngestJobContext context;

    Firefox(IngestJobContext context) {
        super(NbBundle.getMessage(Firefox.class, "Firefox.moduleName"), context);
        this.context = context;
    }

    @Override
    public void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        dataFound = false;
        long ingestJobId = context.getJobId();

        progressBar.progress(Bundle.Progress_Message_Firefox_History());
        this.getHistory(context.getJobId());

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        progressBar.progress(Bundle.Progress_Message_Firefox_Bookmarks());
        this.getBookmark(ingestJobId);

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        progressBar.progress(Bundle.Progress_Message_Firefox_Downloads());
        this.getDownload(ingestJobId);

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        progressBar.progress(Bundle.Progress_Message_Firefox_Cookies());
        this.getCookie(ingestJobId);

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        progressBar.progress(Bundle.Progress_Message_Firefox_FormHistory());
        this.getFormsHistory(ingestJobId);

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        progressBar.progress(Bundle.Progress_Message_Firefox_AutoFill());
        this.getAutofillProfiles(ingestJobId);
    }

    /**
     * Get Firefox history.
     *
     * @param ingestJobId The ingest job id.
     */
    private void getHistory(long ingestJobId) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> historyFiles;
        try {
            historyFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errFetchingFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getDisplayName() + ": " + msg);
            return;
        }

        if (historyFiles.isEmpty()) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.noFilesFound");
            logger.log(Level.INFO, msg);
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        for (AbstractFile historyFile : historyFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            if (historyFile.getSize() == 0) {
                continue;
            }

            String fileName = historyFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox", ingestJobId) + File.separator + fileName + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(historyFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox web history artifacts file '%s' (id=%d).",
                        fileName, historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox web history artifacts file '%s' (id=%d).",
                        temps, fileName, historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.querySQLiteDb(temps, HISTORY_QUERY);
            logger.log(Level.INFO, "{0} - Now getting history from {1} with {2} artifacts identified.", new Object[]{getDisplayName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                String url = result.get("url").toString();
                String domain = extractDomain(url);
                try {
                    
                    Collection<BlackboardAttribute> bbattributes = createHistoryAttributes(
                            url, 
                            Long.valueOf(result.get("visit_date").toString()), 
                            result.get("ref").toString(), 
                            result.get("title").toString(), 
                            NbBundle.getMessage(this.getClass(), "Firefox.moduleName"), 
                            domain, 
                            null);
                
                    
                    bbartifacts.add(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_HISTORY, historyFile, bbattributes));
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to create TSK_WEB_HISTORY artifact for file %d", historyFile.getId()), ex);
                }
            }
            ++j;
            dbFile.delete();
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Queries for bookmark files and adds artifacts
     *
     * @param ingestJobId The ingest job id.
     */
    private void getBookmark(long ingestJobId) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> bookmarkFiles;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getBookmark.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getDisplayName() + ": " + msg);
            return;
        }

        if (bookmarkFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any firefox bookmark files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        for (AbstractFile bookmarkFile : bookmarkFiles) {
            if (bookmarkFile.getSize() == 0) {
                continue;
            }
            String fileName = bookmarkFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox", ingestJobId) + File.separator + fileName + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(bookmarkFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox bookmark artifacts file '%s' (id=%d).",
                        fileName, bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox bookmark artifacts file '%s' (id=%d).",
                        temps, fileName, bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getBookmark.errMsg.errAnalyzeFile",
                        this.getDisplayName(), fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.querySQLiteDb(temps, BOOKMARK_QUERY);
            logger.log(Level.INFO, "{0} - Now getting bookmarks from {1} with {2} artifacts identified.", new Object[]{getDisplayName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                if (context.dataSourceIngestIsCancelled()) {
                    break;
                }

                String url = result.get("url").toString();

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((url != null) ? url : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("title").toString() != null) ? result.get("title").toString() : ""))); //NON-NLS
                if (Long.valueOf(result.get("dateAdded").toString()) > 0) { //NON-NLS
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                            RecentActivityExtracterModuleFactory.getModuleName(),
                            (Long.valueOf(result.get("dateAdded").toString())))); //NON-NLS
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                String domain = extractDomain(url);
                if (domain != null && domain.isEmpty() == false) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                            RecentActivityExtracterModuleFactory.getModuleName(), domain)); //NON-NLS
                }

                try {
                    bbartifacts.add(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, bookmarkFile, bbattributes));
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to create TSK_WEB_BOOKMARK artifact for file %d", bookmarkFile.getId()), ex);
                }
            }
            ++j;
            dbFile.delete();
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Queries for cookies file and adds artifacts
     *
     * @param ingestJobId The ingest job id.
     */
    private void getCookie(long ingestJobId) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "cookies.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getCookie.errMsg.errFetchFile");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getDisplayName() + ": " + msg);
            return;
        }

        if (cookiesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Firefox cookie files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        for (AbstractFile cookiesFile : cookiesFiles) {
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            if (cookiesFile.getSize() == 0) {
                continue;
            }
            String fileName = cookiesFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox", ingestJobId) + File.separator + fileName + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(cookiesFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox cookie artifacts file '%s' (id=%d).",
                        fileName, cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox cookie artifacts file '%s' (id=%d).",
                        temps, fileName, cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getCookie.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            boolean checkColumn = Util.checkColumn("creationTime", "moz_cookies", temps); //NON-NLS
            String query;
            if (checkColumn) {
                query = COOKIE_QUERY;
            } else {
                query = COOKIE_QUERY_V3;
            }

            List<HashMap<String, Object>> tempList = this.querySQLiteDb(temps, query);
            logger.log(Level.INFO, "{0} - Now getting cookies from {1} with {2} artifacts identified.", new Object[]{getDisplayName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                if (context.dataSourceIngestIsCancelled()) {
                    break;
                }

                String host = result.get("host").toString();

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((host != null) ? host : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        (Long.valueOf(result.get("lastAccessed").toString())))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("name").toString() != null) ? result.get("name").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("value").toString() != null) ? result.get("value").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));

                if (checkColumn == true) {
                    String value = result.get("creationTime").toString();
                    if(value != null && !value.isEmpty()) {
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                                RecentActivityExtracterModuleFactory.getModuleName(),
                                (Long.valueOf(result.get("creationTime").toString())))); //NON-NLS
                    }
                }
                String domain = extractDomain(host);
                if (domain != null && domain.isEmpty() == false) {
                    domain = domain.replaceFirst("^\\.+(?!$)", "");
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                            RecentActivityExtracterModuleFactory.getModuleName(), domain));
                }

                try {
                    bbartifacts.add(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_COOKIE, cookiesFile, bbattributes));
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to create TSK_WEB_COOKIE artifact for file %d", cookiesFile.getId()), ex);
                }
            }
            ++j;
            dbFile.delete();
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Queries for downloads files and adds artifacts
     *
     * @param ingestJobId The ingest job id.
     */
    private void getDownload(long ingestJobId) {
        getDownloadPreVersion24(ingestJobId);
        getDownloadVersion24(ingestJobId);
    }

    /**
     * Finds downloads artifacts from Firefox data from versions before 24.0.
     *
     * Downloads were stored in a separate downloads database.
     *
     * @param ingestJobId The ingest job id.
     */
    private void getDownloadPreVersion24(long ingestJobId) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadsFiles;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "downloads.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getDisplayName() + ": " + msg);
            return;
        }

        if (downloadsFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any pre-version-24.0 Firefox download files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        for (AbstractFile downloadsFile : downloadsFiles) {
            if (downloadsFile.getSize() == 0) {
                continue;
            }
            String fileName = downloadsFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox", ingestJobId) + File.separator + fileName + j + ".db"; //NON-NLS
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox download artifacts file '%s' (id=%d).",
                        fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox download artifacts file '%s' (id=%d).",
                        temps, fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errAnalyzeFiles",
                        this.getDisplayName(), fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.querySQLiteDb(temps, DOWNLOAD_QUERY);
            logger.log(Level.INFO, "{0}- Now getting downloads from {1} with {2} artifacts identified.", new Object[]{getDisplayName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                if (context.dataSourceIngestIsCancelled()) {
                    break;
                }

                String source = result.get("source").toString();

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        source)); //NON-NLS
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("source").toString() != null) ? EscapeUtil.decodeURL(result.get("source").toString()) : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        (Long.valueOf(result.get("startTime").toString())))); //NON-NLS

                String target = result.get("target").toString(); //NON-NLS
                String downloadedFilePath = "";
                if (target != null) {
                    try {
                        downloadedFilePath = URLDecoder.decode(target.replaceAll("file:///", ""), "UTF-8"); //NON-NLS
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                                RecentActivityExtracterModuleFactory.getModuleName(),
                                downloadedFilePath));
                        long pathID = Util.findID(dataSource, downloadedFilePath);
                        if (pathID != -1) {
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID,
                                    RecentActivityExtracterModuleFactory.getModuleName(),
                                    pathID));
                        }
                    } catch (UnsupportedEncodingException ex) {
                        logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex); //NON-NLS
                        errors++;
                    }
                }

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                String domain = extractDomain(source);
                if (domain != null && domain.isEmpty() == false) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                            RecentActivityExtracterModuleFactory.getModuleName(),
                            domain)); //NON-NLS
                }
                try {
                    BlackboardArtifact webDownloadArtifact = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_DOWNLOAD, downloadsFile, bbattributes);
                    bbartifacts.add(webDownloadArtifact);

                    // find the downloaded file and create a TSK_ASSOCIATED_OBJECT for it, associating it with the TSK_WEB_DOWNLOAD artifact.
                    for (AbstractFile downloadedFile : currentCase.getSleuthkitCase().getFileManager().findFilesExactNameExactPath(dataSource,
                            FilenameUtils.getName(downloadedFilePath), FilenameUtils.getPath(downloadedFilePath))) {
                        bbartifacts.add(createAssociatedArtifact(downloadedFile, webDownloadArtifact));
                        break;
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error creating TSK_WEB_DOWNLOAD or TSK_ASSOCIATED_ARTIFACT artifact for file '%d'",
                            downloadsFile.getId()), ex); //NON-NLS
                }

            }
            if (errors > 0) {
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errParsingArtifacts",
                                this.getDisplayName(), errors));
            }
            j++;
            dbFile.delete();
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Gets download artifacts from Firefox data from version 24.
     *
     * Downloads are stored in the places database.
     *
     * @param ingestJobId The ingest job id.
     */
    private void getDownloadVersion24(long ingestJobId) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadsFiles;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getDisplayName() + ": " + msg);
            return;
        }

        if (downloadsFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any version-24.0 Firefox download files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        for (AbstractFile downloadsFile : downloadsFiles) {
            if (downloadsFile.getSize() == 0) {
                continue;
            }
            String fileName = downloadsFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox", ingestJobId) + File.separator + fileName + "-downloads" + j + ".db"; //NON-NLS
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox download artifacts file '%s' (id=%d).",
                        fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox download artifacts file '%s' (id=%d).",
                        temps, fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.querySQLiteDb(temps, DOWNLOAD_QUERY_V24);

            logger.log(Level.INFO, "{0} - Now getting downloads from {1} with {2} artifacts identified.", new Object[]{getDisplayName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                if (context.dataSourceIngestIsCancelled()) {
                    break;
                }

                String url = result.get("url").toString();

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        url)); //NON-NLS
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("source").toString() != null) ? EscapeUtil.decodeURL(result.get("source").toString()) : "")));
                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", (Long.valueOf(result.get("startTime").toString()))));

                String target = result.get("target").toString(); //NON-NLS
                String downloadedFilePath = "";
                if (target != null) {
                    try {
                        downloadedFilePath = URLDecoder.decode(target.replaceAll("file:///", ""), "UTF-8"); //NON-NLS
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                                RecentActivityExtracterModuleFactory.getModuleName(),
                                downloadedFilePath));
                        long pathID = Util.findID(dataSource, downloadedFilePath);
                        if (pathID != -1) {
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID,
                                    RecentActivityExtracterModuleFactory.getModuleName(),
                                    pathID));
                        }
                    } catch (UnsupportedEncodingException ex) {
                        logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex); //NON-NLS
                        errors++;
                    }
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        Long.valueOf(result.get("lastModified").toString()))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                String domain = extractDomain(url);
                if (domain != null && domain.isEmpty() == false) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                            RecentActivityExtracterModuleFactory.getModuleName(), domain)); //NON-NLS
                }
                try {
                    BlackboardArtifact webDownloadArtifact = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_DOWNLOAD, downloadsFile, bbattributes);
                    bbartifacts.add(webDownloadArtifact);

                    // find the downloaded file and create a TSK_ASSOCIATED_OBJECT for it, associating it with the TSK_WEB_DOWNLOAD artifact.
                    for (AbstractFile downloadedFile : currentCase.getSleuthkitCase().getFileManager().findFilesExactNameExactPath(dataSource,
                            FilenameUtils.getName(downloadedFilePath), FilenameUtils.getPath(downloadedFilePath))) {
                        bbartifacts.add(createAssociatedArtifact(downloadedFile, webDownloadArtifact));
                        break;
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error creating associated object artifact for file  '%s'",
                            downloadedFilePath), ex); //NON-NLS
                }
            }
            if (errors > 0) {
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errParsingArtifacts",
                        this.getDisplayName(), errors));
            }
            j++;
            dbFile.delete();
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Gets data from formshistory.sqlite database. Parses and creates
     * artifacts.
     *
     * @param ingestJobId The ingest job id.
     */
    private void getFormsHistory(long ingestJobId) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> formHistoryFiles;

        // Some fields are just noisy and can me excluded
        Set<String> excludedFieldNames = new HashSet<>(Arrays.asList(
                "it", // some kind of timestamp
                "ts" // some kind of timestamp
        ));

        try {
            formHistoryFiles = fileManager.findFiles(dataSource, "formhistory.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getFormsAutofill.errMsg.errFetchingFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getDisplayName() + ": " + msg);
            return;
        }

        if (formHistoryFiles.isEmpty()) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getFormsAutofill.errMsg.noFilesFound");
            logger.log(Level.INFO, msg);
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        for (AbstractFile formHistoryFile : formHistoryFiles) {
            if (formHistoryFile.getSize() == 0) {
                continue;
            }

            String fileName = formHistoryFile.getName();
            String tempFilePath = RAImageIngestModule.getRATempPath(currentCase, "firefox", ingestJobId) + File.separator + fileName + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(formHistoryFile, new File(tempFilePath), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox web history artifacts file '%s' (id=%d).",
                        fileName, formHistoryFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getFormsAutofill.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox web history artifacts file '%s' (id=%d).",
                        tempFilePath, fileName, formHistoryFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getFormsAutofill.errMsg.errAnalyzeFile", this.getDisplayName(),
                                fileName));
                continue;
            }
            File dbFile = new File(tempFilePath);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            // The table schema is a little different in newer version of Firefox
            boolean isFirefoxV64 = Util.checkColumn("timesUsed", "moz_formhistory", tempFilePath);
            String formHistoryQuery = (isFirefoxV64) ? FORMHISTORY_QUERY_V64 : FORMHISTORY_QUERY;

            List<HashMap<String, Object>> tempList = this.querySQLiteDb(tempFilePath, formHistoryQuery);
            logger.log(Level.INFO, "{0} - Now getting history from {1} with {2} artifacts identified.", new Object[]{getDisplayName(), tempFilePath, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                if (context.dataSourceIngestIsCancelled()) {
                    break;
                }

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

                String fieldName = ((result.get("fieldname").toString() != null) ? result.get("fieldname").toString() : "");
                // filter out unuseful values
                if (excludedFieldNames.contains(fieldName.toLowerCase())) {
                    continue;
                }

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        fieldName)); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("value").toString() != null) ? result.get("value").toString() : ""))); //NON-NLS

                // Newer versions of firefox have additional columns
                if (isFirefoxV64) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                            RecentActivityExtracterModuleFactory.getModuleName(),
                            (Long.valueOf(result.get("firstUsed").toString()) / 1000000))); //NON-NLS

                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                            RecentActivityExtracterModuleFactory.getModuleName(),
                            (Long.valueOf(result.get("lastUsed").toString()) / 1000000))); //NON-NLS

                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT,
                            RecentActivityExtracterModuleFactory.getModuleName(),
                            (Integer.valueOf(result.get("timesUsed").toString())))); //NON-NLS

                }
                try {
                    // Add artifact
                    bbartifacts.add(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_FORM_AUTOFILL, formHistoryFile, bbattributes));
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to create TSK_WEB_FORM_AUTOFILL artifact for file %d", formHistoryFile.getId()), ex);
                }
            }
            ++j;
            dbFile.delete();
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Gets data from autofill-profiles.json file. Parses file and makes
     * artifacts.
     *
     * @param ingestJobId The ingest job id.
     */
    private void getAutofillProfiles(long ingestJobId) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> autofillProfilesFiles;
        try {
            autofillProfilesFiles = fileManager.findFiles(dataSource, "autofill-profiles.json", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getAutofillProfiles.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getDisplayName() + ": " + msg);
            return;
        }

        if (autofillProfilesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Firefox Autofill Profiles files."); //NON-NLS
            return;
        }

        dataFound = true;
        int j = 0;
        while (j < autofillProfilesFiles.size()) {
            AbstractFile profileFile = autofillProfilesFiles.get(j++);
            if (profileFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "Firefox", ingestJobId) + File.separator + profileFile.getName() + j + ".json"; //NON-NLS
            try {
                ContentUtils.writeToFile(profileFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox Autofill profiles artifacts file '%s' (id=%d).",
                        profileFile.getName(), profileFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getAutofillProfiles.errMsg.errAnalyzingFile",
                        this.getDisplayName(), profileFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp file '%s' for Firefox Autofill profiles file '%s' (id=%d).",
                        temps, profileFile.getName(), profileFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getAutofillProfiles.errMsg.errAnalyzingFile",
                        this.getDisplayName(), profileFile.getName()));
                continue;
            }

            logger.log(Level.INFO, "{0}- Now getting Bookmarks from {1}", new Object[]{getDisplayName(), temps}); //NON-NLS
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            FileReader tempReader;
            try {
                tempReader = new FileReader(temps);
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, "Error while trying to read the Autofill profiles json file for Firefox.", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getAutofillProfiles.errMsg.errAnalyzeFile", this.getDisplayName(),
                                profileFile.getName()));
                continue;
            }

            JsonObject jsonRootObject;
            JsonArray jAddressesArray;

            try {
                jsonRootObject = JsonParser.parseReader(tempReader).getAsJsonObject();
                jAddressesArray = jsonRootObject.getAsJsonArray("addresses"); //NON-NLS
            } catch (JsonIOException | JsonSyntaxException | IllegalStateException ex) {
                logger.log(Level.WARNING, "Error parsing Json for Firefox Autofill profiles.", ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getAutofillProfiles.errMsg.errAnalyzingFile3",
                        this.getDisplayName(), profileFile.getName()));
                continue;
            }

            WebBrowserArtifactsHelper helper;
            try {
                // Helper to create web form address artifacts.
                helper = new WebBrowserArtifactsHelper(
                        Case.getCurrentCaseThrows().getSleuthkitCase(),
                        NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"),
                        profileFile,
                        ingestJobId
                );
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "No case open, bailing.", ex); //NON-NLS
                return;
            }

            for (JsonElement result : jAddressesArray) {
                JsonObject address = result.getAsJsonObject();
                if (address == null) {
                    continue;
                }

                JsonElement nameEl = address.get("name"); //NON-NLS
                String name = (nameEl != null) ? nameEl.getAsString() : "";

                JsonElement emailEl = address.get("email"); //NON-NLS
                String email = (emailEl != null) ? emailEl.getAsString() : "";

                JsonElement telEl = address.get("tel"); //NON-NLS
                String tel = (telEl != null) ? telEl.getAsString() : "";
                JsonElement telCountryCodeEl = address.get("tel-country-code"); //NON-NLS
                String telCountryCode = (telCountryCodeEl != null) ? telCountryCodeEl.getAsString() : "";
                JsonElement telNationalEl = address.get("tel-national"); //NON-NLS
                String telNational = (telNationalEl != null) ? telNationalEl.getAsString() : "";

                String phoneNumber = makeTelNumber(tel, telCountryCode, telNational);

                JsonElement createdEl = address.get("timeCreated"); //NON-NLS
                Long datetimeCreated = (createdEl != null) ? createdEl.getAsLong() / 1000 : Long.valueOf(0);
                JsonElement lastusedEl = address.get("timeLastUsed"); //NON-NLS
                Long datetimeLastUsed = (lastusedEl != null) ? lastusedEl.getAsLong() / 1000 : Long.valueOf(0);
                JsonElement timesUsedEl = address.get("timesUsed"); //NON-NLS
                Integer timesUsed = (timesUsedEl != null) ? timesUsedEl.getAsShort() : Integer.valueOf(0);

                JsonElement addressLine1El = address.get("address-line1"); //NON-NLS
                String addressLine1 = (addressLine1El != null) ? addressLine1El.getAsString() : "";
                JsonElement addressLine2El = address.get("address-line2"); //NON-NLS
                String addressLine2 = (addressLine2El != null) ? addressLine2El.getAsString() : "";
                JsonElement addressLine3El = address.get("address-line3"); //NON-NLS
                String addressLine3 = (addressLine3El != null) ? addressLine3El.getAsString() : "";

                JsonElement postalCodeEl = address.get("postal-code"); //NON-NLS
                String postalCode = (postalCodeEl != null) ? postalCodeEl.getAsString() : "";
                JsonElement countryEl = address.get("country"); //NON-NLS
                String country = (countryEl != null) ? countryEl.getAsString() : "";

                String mailingAddress = makeFullAddress(addressLine1, addressLine2, addressLine3, postalCode, country);

                try {
                    helper.addWebFormAddress(name, email, phoneNumber,
                            mailingAddress, datetimeCreated, datetimeLastUsed, timesUsed);
                } catch (TskCoreException | Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Firefox Autofill profile artifact{0}", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "Firefox.getAutofillProfiles.errMsg.errAnalyzingFile4",
                                    this.getDisplayName(), profileFile.getName()));
                }
            }
            dbFile.delete();
        }
    }

    /**
     * Extract the domain from the supplied URL. This method does additional
     * checks to detect invalid URLs.
     *
     * @param url The URL from which to extract the domain.
     *
     * @return The domain.
     */
    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        if (url.toLowerCase().startsWith(PLACE_URL_PREFIX)) {
            /*
             * Ignore URLs that begin with the matched text.
             */
            return null;
        }

        return NetworkUtils.extractDomain(url);
    }

    /**
     * Returns a phone number based on input number or components of phone
     * number.
     *
     * @param tel            full number, if available
     * @param telCountryCode country code
     * @param telNational    full national number
     *
     * @return phone number, or an empty string if no number can be deciphered
     *         from input
     */
    private String makeTelNumber(String tel, String telCountryCode, String telNational) {

        if (tel != null && !tel.isEmpty()) {
            return tel;
        }

        if ((telCountryCode != null && !telCountryCode.isEmpty())
                && (telNational != null && !telNational.isEmpty())) {
            return telCountryCode + telNational;
        }

        return "";
    }

    /**
     * Returns a full postal address from multiple address fields.
     *
     * @parm addressLine1
     * @parm addressLine2
     * @parm addressLine3
     * @parm postalCode
     * @parm country
     *
     * @return full address
     */
    private String makeFullAddress(String addressLine1, String addressLine2, String addressLine3, String postalCode, String country) {
        String fullAddress = "";
        fullAddress = appendAddressField(fullAddress, addressLine1);
        fullAddress = appendAddressField(fullAddress, addressLine2);
        fullAddress = appendAddressField(fullAddress, addressLine3);
        fullAddress = appendAddressField(fullAddress, postalCode);
        fullAddress = appendAddressField(fullAddress, country);

        return fullAddress;
    }

    /**
     * Appends the given address field to given address, if not empty. Adds
     * delimiter in between if needed.
     *
     * @param address
     * @param addressfield
     *
     * @return updated address
     */
    private String appendAddressField(String address, String addressfield) {

        String updatedAddress = address;
        if (addressfield != null && !addressfield.isEmpty()) {
            if (!updatedAddress.isEmpty()) {
                updatedAddress += ", ";
            }
            updatedAddress += addressfield;
        }

        return updatedAddress;
    }

}
