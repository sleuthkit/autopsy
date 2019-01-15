/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2019 Basis Technology Corp.
 *
 * Copyright 2012 42six Solutions.
 *
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

import com.google.common.collect.Lists;
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
import java.util.*;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_ACCOUNT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL_DECODED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Chrome recent activity extraction
 */
final class Chrome extends Extract {

    private static final Logger logger = Logger.getLogger(Chrome.class.getName());
    private static final String PARENT_MODULE_NAME = NbBundle.getMessage(Chrome.class, "Chrome.parentModuleName");
    private static final String HISTORY_QUERY = "SELECT urls.url, urls.title, urls.visit_count, urls.typed_count, " //NON-NLS
                                                + "last_visit_time, urls.hidden, visits.visit_time, (SELECT urls.url FROM urls WHERE urls.id=visits.url) AS from_visit, visits.transition FROM urls, visits WHERE urls.id = visits.url"; //NON-NLS
    private static final String COOKIE_QUERY = "SELECT name, value, host_key, expires_utc,last_access_utc, creation_utc FROM cookies"; //NON-NLS
    private static final String DOWNLOAD_QUERY = "SELECT full_path, url, start_time, received_bytes FROM downloads"; //NON-NLS
    private static final String DOWNLOAD_QUERY_V30 = "SELECT current_path AS full_path, url, start_time, received_bytes FROM downloads, downloads_url_chains WHERE downloads.id=downloads_url_chains.id"; //NON-NLS
    private static final String LOGIN_QUERY = "SELECT origin_url, username_value, signon_realm from logins"; //NON-NLS

    private static final long SECONDS_SINCE_JAN_1_1601 = 11_644_473_600L;

    private Content dataSource;
    private IngestJobContext context;

    @Override
    protected String getModuleName() {
        return NbBundle.getMessage(Chrome.class, "Chrome.moduleName");
    }

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        this.getHistory();
        this.getBookmark();
        this.getCookie();
        this.getDownload();
    }

    /**
     * Query for history databases and add artifacts
     */
    @NbBundle.Messages({"# {0} - Extractor / program name",
        "Extractor.errPostingArtifacts={0}:Error while trying to post artifacts."})
    private void getHistory() {

        List<AbstractFile> historyFiles;
        try {
            historyFiles = fileManager.findFiles(dataSource, "History", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(Chrome.class, "Chrome.getHistory.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        // get only the allocated ones, for now
        List<AbstractFile> allocatedHistoryFiles = new ArrayList<>();
        for (AbstractFile historyFile : historyFiles) {
            if (historyFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)) {
                allocatedHistoryFiles.add(historyFile);
            }
        }

        // log a message if we don't have any allocated history files
        if (allocatedHistoryFiles.isEmpty()) {
            String msg = NbBundle.getMessage(Chrome.class, "Chrome.getHistory.errMsg.couldntFindAnyFiles");
            logger.log(Level.INFO, msg);
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        while (j < historyFiles.size()) {
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + historyFiles.get(j).getName() + j + ".db"; //NON-NLS
            final AbstractFile historyFile = historyFiles.get(j++);
            if (historyFile.getSize() == 0) {
                continue;
            }
            try {
                ContentUtils.writeToFile(historyFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome web history artifacts file '%s' (id=%d).",
                        historyFile.getName(), historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getHistory.errMsg.errAnalyzingFile",
                        this.getModuleName(), historyFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome web history artifacts file '%s' (id=%d).",
                        temps, historyFile.getName(), historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getHistory.errMsg.errAnalyzingFile",
                        this.getModuleName(), historyFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList;
            tempList = this.dbConnect(temps, HISTORY_QUERY);
            logger.log(Level.INFO, "{0}- Now getting history from {1} with {2}artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                Objects.toString(result.get("url"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DATETIME_ACCESSED, PARENT_MODULE_NAME,
                                (Long.valueOf(result.get("last_visit_time").toString()) / 1000000) - SECONDS_SINCE_JAN_1_1601),
                        new BlackboardAttribute(
                                TSK_REFERRER, PARENT_MODULE_NAME,
                                Objects.toString(result.get("from_visit"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_TITLE, PARENT_MODULE_NAME,
                                Objects.toString(result.get("title"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName()),
                        new BlackboardAttribute(
                                TSK_DOMAIN, PARENT_MODULE_NAME,
                                NetworkUtils.extractDomain(Objects.toString(result.get("url"), "")))); //NON-NLS
                try {
                    BlackboardArtifact bbart = historyFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                    bbart.addAttributes(bbattributes);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to create Chrome history artifact.", ex); //NON-NLS
                    this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getHistory.errMsg.errAnalyzingFile",
                            this.getModuleName(), historyFile.getName()));
                }
            }
            dbFile.delete();
        }
        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Chrome history artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Search for bookmark files and make artifacts.
     */
    private void getBookmark() {
        List<AbstractFile> bookmarkFiles;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "Bookmarks", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(Chrome.class, "Chrome.getBookmark.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (bookmarkFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome bookmark files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        while (index < bookmarkFiles.size()) {
            AbstractFile bookmarkFile = bookmarkFiles.get(index++);
            if (bookmarkFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + bookmarkFile.getName() + index + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(bookmarkFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome bookmark artifacts file '%s' (id=%d).",
                        bookmarkFile.getName(), bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getBookmark.errMsg.errAnalyzingFile",
                        this.getModuleName(), bookmarkFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome bookmark artifacts file '%s' (id=%d).",
                        temps, bookmarkFile.getName(), bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getBookmark.errMsg.errAnalyzingFile",
                        this.getModuleName(), bookmarkFile.getName()));
                continue;
            }

            logger.log(Level.INFO, "{0}- Now getting Bookmarks from {1}", new Object[]{getModuleName(), temps}); //NON-NLS
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            FileReader tempReader;
            try {
                tempReader = new FileReader(temps);
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, "Error while trying to read into the Bookmarks for Chrome.", ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getBookmark.errMsg.errAnalyzeFile", this.getModuleName(),
                        bookmarkFile.getName()));
                continue;
            }

            final JsonParser parser = new JsonParser();
            JsonElement jsonElement;
            JsonObject jElement, jRoot, jBookmark;
            JsonArray jBookmarkArray;

            try {
                jsonElement = parser.parse(tempReader);
                jElement = jsonElement.getAsJsonObject();
                jRoot = jElement.get("roots").getAsJsonObject(); //NON-NLS
                jBookmark = jRoot.get("bookmark_bar").getAsJsonObject(); //NON-NLS
                jBookmarkArray = jBookmark.getAsJsonArray("children"); //NON-NLS
            } catch (JsonIOException | JsonSyntaxException | IllegalStateException ex) {
                logger.log(Level.WARNING, "Error parsing Json from Chrome Bookmark.", ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getBookmark.errMsg.errAnalyzingFile3",
                        this.getModuleName(), bookmarkFile.getName()));
                continue;
            }

            for (JsonElement result : jBookmarkArray) {
                JsonObject address = result.getAsJsonObject();
                if (address == null) {
                    continue;
                }
                JsonElement urlEl = address.get("url"); //NON-NLS
                String url;
                if (urlEl != null) {
                    url = urlEl.getAsString();
                } else {
                    url = "";
                }
                String name;
                JsonElement nameEl = address.get("name"); //NON-NLS
                if (nameEl != null) {
                    name = nameEl.getAsString();
                } else {
                    name = "";
                }
                Long date;
                JsonElement dateEl = address.get("date_added"); //NON-NLS
                if (dateEl != null) {
                    date = dateEl.getAsLong();
                } else {
                    date = Long.valueOf(0);
                }
                String domain = NetworkUtils.extractDomain(url);
                try {
                    Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                            new BlackboardAttribute(
                                    TSK_URL, PARENT_MODULE_NAME,
                                    url),
                            new BlackboardAttribute(
                                    TSK_TITLE, PARENT_MODULE_NAME,
                                    name),
                            new BlackboardAttribute(
                                    TSK_DATETIME_CREATED, PARENT_MODULE_NAME,
                                    (date / 1_000_000) - SECONDS_SINCE_JAN_1_1601),
                            new BlackboardAttribute(
                                    TSK_PROG_NAME, PARENT_MODULE_NAME,
                                    getModuleName()),
                            new BlackboardAttribute(
                                    TSK_DOMAIN, PARENT_MODULE_NAME,
                                    domain));
                    BlackboardArtifact bbart = bookmarkFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                    bbart.addAttributes(bbattributes);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Chrome bookmark artifact.", ex); //NON-NLS
                    this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getBookmark.errMsg.errAnalyzingFile4",
                            this.getModuleName(), bookmarkFile.getName()));
                }
            }
            dbFile.delete();
        }
        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Chrome bookmark artifact{0}", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Queries for cookie files and adds artifacts
     */
    private void getCookie() {
        List<AbstractFile> cookiesFiles;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "Cookies", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(Chrome.class, "Chrome.getCookie.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (cookiesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome cookies files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        while (index < cookiesFiles.size()) {
            AbstractFile cookiesFile = cookiesFiles.get(index++);
            if (cookiesFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + cookiesFile.getName() + index + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(cookiesFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome cookie artifacts file '%s' (id=%d).",
                        cookiesFile.getName(), cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getCookie.errMsg.errAnalyzeFile",
                        this.getModuleName(), cookiesFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome cookie artifacts file '%s' (id=%d).",
                        temps, cookiesFile.getName(), cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getCookie.errMsg.errAnalyzeFile",
                        this.getModuleName(), cookiesFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, COOKIE_QUERY);
            logger.log(Level.INFO, "{0}- Now getting cookies from {1} with {2}artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                String domain = result.get("host_key").toString(); //NON-NLS
                domain = domain.replaceFirst("^\\.+(?!$)", "");
                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                TSK_DOMAIN, PARENT_MODULE_NAME,
                                domain),
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                Objects.toString(result.get("host_key"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DATETIME, PARENT_MODULE_NAME,
                                (Long.valueOf(result.get("last_access_utc").toString()) / 1000000) - SECONDS_SINCE_JAN_1_1601), //NON-NLS
                        new BlackboardAttribute(
                                TSK_NAME, PARENT_MODULE_NAME,
                                Objects.toString(result.get("name"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_VALUE, PARENT_MODULE_NAME,
                                Objects.toString(result.get("value"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName()));
                try {
                    BlackboardArtifact bbart = cookiesFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE);
                    bbart.addAttributes(bbattributes);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Chrome cookie artifact.", ex); //NON-NLS
                    this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getCookie.errMsg.errAnalyzingFile",
                            this.getModuleName(), cookiesFile.getName()));
                }
            }

            dbFile.delete();
        }
        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Chrome cookie artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Queries for download files and adds artifacts
     */
    private void getDownload() {
        List<AbstractFile> downloadFiles;
        try {
            downloadFiles = fileManager.findFiles(dataSource, "History", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(Chrome.class, "Chrome.getDownload.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (downloadFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome download files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        while (index < downloadFiles.size()) {
            AbstractFile downloadFile = downloadFiles.get(index++);
            if (downloadFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + downloadFile.getName() + index + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(downloadFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome download artifacts file '%s' (id=%d).",
                        downloadFile.getName(), downloadFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getDownload.errMsg.errAnalyzeFiles1",
                        this.getModuleName(), downloadFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome download artifacts file '%s' (id=%d).",
                        temps, downloadFile.getName(), downloadFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getDownload.errMsg.errAnalyzeFiles1",
                        this.getModuleName(), downloadFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps,
                    (isChromePreVersion30(temps)) ? DOWNLOAD_QUERY : DOWNLOAD_QUERY_V30);

            logger.log(Level.INFO, "{0}- Now getting downloads from {1} with {2}artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = Lists.newArrayList(
                        new BlackboardAttribute(
                                TSK_PATH, PARENT_MODULE_NAME,
                                Objects.toString(result.get("full_path"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                Objects.toString(result.get("url"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DATETIME_ACCESSED, PARENT_MODULE_NAME,
                                (Long.valueOf(result.get("start_time").toString()) / 1000000) - SECONDS_SINCE_JAN_1_1601), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DOMAIN, PARENT_MODULE_NAME,
                                NetworkUtils.extractDomain(Objects.toString(result.get("url"), ""))), //NON-NLS
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName())
                );

                long pathID = Util.findID(dataSource, (result.get("full_path").toString())); //NON-NLS
                if (pathID != -1) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID, PARENT_MODULE_NAME, pathID));
                }
                try {
                    BlackboardArtifact bbart = downloadFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD);
                    bbart.addAttributes(bbattributes);

                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Chrome download artifact.", ex); //NON-NLS
                    this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getDownload.errMsg.errAnalyzeFiles1",
                            this.getModuleName(), downloadFile.getName()));
                }
            }

            dbFile.delete();
        }
        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Chrome download artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Queries for login files and adds artifacts
     */
    private void getLogin() {
        List<AbstractFile> signonFiles;
        try {
            signonFiles = fileManager.findFiles(dataSource, "signons.sqlite", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(Chrome.class, "Chrome.getLogin.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (signonFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome signon files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        while (index < signonFiles.size()) {
            AbstractFile signonFile = signonFiles.get(index++);
            if (signonFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + signonFile.getName() + index + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(signonFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome login artifacts file '%s' (id=%d).",
                        signonFile.getName(), signonFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getLogin.errMsg.errAnalyzingFiles",
                        this.getModuleName(), signonFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome login artifacts file '%s' (id=%d).",
                        temps, signonFile.getName(), signonFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getLogin.errMsg.errAnalyzingFiles",
                        this.getModuleName(), signonFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, LOGIN_QUERY);
            logger.log(Level.INFO, "{0}- Now getting login information from {1} with {2}artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                Objects.toString(result.get("origin_url"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DATETIME_ACCESSED, PARENT_MODULE_NAME,
                                (Long.valueOf(result.get("last_visit_time").toString()) / 1000000) - SECONDS_SINCE_JAN_1_1601), //NON-NLS
                        new BlackboardAttribute(
                                TSK_REFERRER, PARENT_MODULE_NAME,
                                Objects.toString(result.get("from_visit"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_NAME, PARENT_MODULE_NAME,
                                Objects.toString(result.get("title").toString(), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName()),
                        new BlackboardAttribute(
                                TSK_URL_DECODED, PARENT_MODULE_NAME,
                                NetworkUtils.extractDomain(Objects.toString(result.get("origin_url"), ""))), //NON-NLS
                        new BlackboardAttribute(
                                TSK_USER_NAME, PARENT_MODULE_NAME,
                                Objects.toString(result.get("username_value"), "").replaceAll("'", "''")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DOMAIN, PARENT_MODULE_NAME,
                                Objects.toString(result.get("signon_realm"), ""))); //NON-NLS

                try {
                    BlackboardArtifact bbart = signonFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                    bbart.addAttributes(bbattributes);

                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Chrome login artifact.", ex); //NON-NLS
                    this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getLogin.errMsg.errAnalyzingFiles",
                            this.getModuleName(), signonFile.getName()));
                }

                Set<BlackboardAttribute> osAccountAttriubtes = Collections.singleton(
                        new BlackboardAttribute(
                                TSK_USER_NAME, PARENT_MODULE_NAME,
                                Objects.toString(result.get("username_value"), "").replaceAll("'", "''")));//NON-NLS
                try {
                    BlackboardArtifact osAccountArtifact = signonFile.newArtifact(TSK_OS_ACCOUNT);
                    osAccountArtifact.addAttributes(osAccountAttriubtes);
                    bbartifacts.add(osAccountArtifact);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Chrome os account artifact.", ex); //NON-NLS
                    this.addErrorMessage(NbBundle.getMessage(Chrome.class, "Chrome.getLogin.errMsg.errAnalyzingFiles",
                            this.getModuleName(), signonFile.getName()));
                }
            }

            dbFile.delete();
        }

        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Chrome login artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    private boolean isChromePreVersion30(String temps) {
        String query = "PRAGMA table_info(downloads)"; //NON-NLS
        List<HashMap<String, Object>> columns = this.dbConnect(temps, query);
        return columns.stream()
                .anyMatch(col -> "url".equals(col.get("name")));
    }
}
