/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import java.util.logging.Level;
import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Chrome recent activity extraction
 */
class Chrome extends Extract {

    private static final String HISTORY_QUERY = "SELECT urls.url, urls.title, urls.visit_count, urls.typed_count, " //NON-NLS
            + "last_visit_time, urls.hidden, visits.visit_time, (SELECT urls.url FROM urls WHERE urls.id=visits.url) AS from_visit, visits.transition FROM urls, visits WHERE urls.id = visits.url"; //NON-NLS
    private static final String COOKIE_QUERY = "SELECT name, value, host_key, expires_utc,last_access_utc, creation_utc FROM cookies"; //NON-NLS
    private static final String DOWNLOAD_QUERY = "SELECT full_path, url, start_time, received_bytes FROM downloads"; //NON-NLS
    private static final String DOWNLOAD_QUERY_V30 = "SELECT current_path AS full_path, url, start_time, received_bytes FROM downloads, downloads_url_chains WHERE downloads.id=downloads_url_chains.id"; //NON-NLS
    private static final String LOGIN_QUERY = "SELECT origin_url, username_value, signon_realm from logins"; //NON-NLS
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private Content dataSource;
    private IngestJobContext context;

    Chrome() {
        moduleName = NbBundle.getMessage(Chrome.class, "Chrome.moduleName");
    }

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        this.getHistory();
        this.getBookmark();
        this.getCookie();
        this.getLogin();
        this.getDownload();
    }

    /**
     * Query for history databases and add artifacts
     */
    private void getHistory() {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> historyFiles;
        try {
            historyFiles = fileManager.findFiles(dataSource, "History", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getHistory.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
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
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getHistory.errMsg.couldntFindAnyFiles");
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
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getHistory.errMsg.errAnalyzingFile",
                        this.getName(), historyFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome web history artifacts file '%s' (id=%d).",
                        temps, historyFile.getName(), historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getHistory.errMsg.errAnalyzingFile",
                        this.getName(), historyFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList;
            tempList = this.dbConnect(temps, HISTORY_QUERY);
            logger.log(Level.INFO, "{0}- Now getting history from {1} with {2}artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("url").toString() != null) ? result.get("url").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        (Long.valueOf(result.get("last_visit_time").toString()) / 1000000) - Long.valueOf("11644473600"))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("from_visit").toString() != null) ? result.get("from_visit").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("title").toString() != null) ? result.get("title").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : "")))); //NON-NLS

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, historyFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }
            dbFile.delete();
        }

        IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, bbartifacts));
    }

    /**
     * Search for bookmark files and make artifacts.
     */
    private void getBookmark() {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> bookmarkFiles;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "Bookmarks", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (bookmarkFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome bookmark files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;

        while (j < bookmarkFiles.size()) {
            AbstractFile bookmarkFile = bookmarkFiles.get(j++);
            if (bookmarkFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + bookmarkFile.getName() + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(bookmarkFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome bookmark artifacts file '%s' (id=%d).",
                        bookmarkFile.getName(), bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzingFile",
                        this.getName(), bookmarkFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome bookmark artifacts file '%s' (id=%d).",
                        temps, bookmarkFile.getName(), bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzingFile",
                        this.getName(), bookmarkFile.getName()));
                continue;
            }

            logger.log(Level.INFO, "{0}- Now getting Bookmarks from {1}", new Object[]{moduleName, temps}); //NON-NLS
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
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzeFile", this.getName(),
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
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzingFile3",
                        this.getName(), bookmarkFile.getName()));
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
                String domain = Util.extractDomain(url);
                try {
                    BlackboardArtifact bbart = bookmarkFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                    //TODO Revisit usage of deprecated constructor as per TSK-583
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                            NbBundle.getMessage(this.getClass(),
                                    "Chrome.parentModuleName"), url));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE,
                            NbBundle.getMessage(this.getClass(),
                                    "Chrome.parentModuleName"), name));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                            NbBundle.getMessage(this.getClass(),
                                    "Chrome.parentModuleName"), (date / 1000000) - Long.valueOf("11644473600")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                            NbBundle.getMessage(this.getClass(),
                                    "Chrome.parentModuleName"),
                            NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                            NbBundle.getMessage(this.getClass(),
                                    "Chrome.parentModuleName"), domain));
                    bbart.addAttributes(bbattributes);

                    // index the artifact for keyword search
                    this.indexArtifact(bbart);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Chrome bookmark artifact{0}", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzingFile4",
                                    this.getName(), bookmarkFile.getName()));
                }
            }
            dbFile.delete();
        }

        IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK, bbartifacts));
    }

    /**
     * Queries for cookie files and adds artifacts
     */
    private void getCookie() {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "Cookies", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getCookie.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (cookiesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome cookies files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        while (j < cookiesFiles.size()) {
            AbstractFile cookiesFile = cookiesFiles.get(j++);
            if (cookiesFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + cookiesFile.getName() + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(cookiesFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome cookie artifacts file '%s' (id=%d).",
                        cookiesFile.getName(), cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getCookie.errMsg.errAnalyzeFile",
                        this.getName(), cookiesFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome cookie artifacts file '%s' (id=%d).",
                        temps, cookiesFile.getName(), cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getCookie.errMsg.errAnalyzeFile",
                        this.getName(), cookiesFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, COOKIE_QUERY);
            logger.log(Level.INFO, "{0}- Now getting cookies from {1} with {2}artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("host_key").toString() != null) ? result.get("host_key").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        (Long.valueOf(result.get("last_access_utc").toString()) / 1000000) - Long.valueOf("11644473600"))); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("name").toString() != null) ? result.get("name").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("value").toString() != null) ? result.get("value").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                String domain = result.get("host_key").toString(); //NON-NLS
                domain = domain.replaceFirst("^\\.+(?!$)", "");
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), domain));

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }

            dbFile.delete();
        }

        IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE, bbartifacts));
    }

    /**
     * Queries for download files and adds artifacts
     */
    private void getDownload() {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadFiles;
        try {
            downloadFiles = fileManager.findFiles(dataSource, "History", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getDownload.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (downloadFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome download files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        while (j < downloadFiles.size()) {
            AbstractFile downloadFile = downloadFiles.get(j++);
            if (downloadFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + downloadFile.getName() + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(downloadFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome download artifacts file '%s' (id=%d).",
                        downloadFile.getName(), downloadFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getDownload.errMsg.errAnalyzeFiles1",
                        this.getName(), downloadFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome download artifacts file '%s' (id=%d).",
                        temps, downloadFile.getName(), downloadFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getDownload.errMsg.errAnalyzeFiles1",
                        this.getName(), downloadFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList;

            if (isChromePreVersion30(temps)) {
                tempList = this.dbConnect(temps, DOWNLOAD_QUERY);
            } else {
                tempList = this.dbConnect(temps, DOWNLOAD_QUERY_V30);
            }

            logger.log(Level.INFO, "{0}- Now getting downloads from {1} with {2}artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), (result.get("full_path").toString()))); //NON-NLS
                long pathID = Util.findID(dataSource, (result.get("full_path").toString())); //NON-NLS
                if (pathID != -1) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID,
                            NbBundle.getMessage(this.getClass(),
                                    "Chrome.parentModuleName"), pathID));
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("url").toString() != null) ? result.get("url").toString() : ""))); //NON-NLS
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                Long time = (Long.valueOf(result.get("start_time").toString()) / 1000000) - Long.valueOf("11644473600"); //NON-NLS

                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", time));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), time));
                String domain = Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), domain));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }

            dbFile.delete();
        }

        IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, bbartifacts));
    }

    /**
     * Queries for login files and adds artifacts
     */
    private void getLogin() {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> signonFiles;
        try {
            signonFiles = fileManager.findFiles(dataSource, "signons.sqlite", "Chrome"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (signonFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome signon files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        while (j < signonFiles.size()) {
            AbstractFile signonFile = signonFiles.get(j++);
            if (signonFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + signonFile.getName() + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(signonFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome login artifacts file '%s' (id=%d).",
                        signonFile.getName(), signonFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errAnalyzingFiles",
                        this.getName(), signonFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome login artifacts file '%s' (id=%d).",
                        temps, signonFile.getName(), signonFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errAnalyzingFiles",
                        this.getName(), signonFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, LOGIN_QUERY);
            logger.log(Level.INFO, "{0}- Now getting login information from {1} with {2}artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("origin_url").toString() != null) ? result.get("origin_url").toString() : ""))); //NON-NLS
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("origin_url").toString() != null) ? EscapeUtil.decodeURL(result.get("origin_url").toString()) : "")));
                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", ((Long.valueOf(result.get("last_visit_time").toString())) / 1000000)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        (Long.valueOf(result.get("last_visit_time").toString()) / 1000000) - Long.valueOf("11644473600"))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("from_visit").toString() != null) ? result.get("from_visit").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("title").toString() != null) ? result.get("title").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        (Util.extractDomain((result.get("origin_url").toString() != null) ? result.get("url").toString() : "")))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("username_value").toString() != null) ? result.get("username_value").toString().replaceAll("'", "''") : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        result.get("signon_realm").toString())); //NON-NLS

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, signonFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }

                // Don't add TSK_OS_ACCOUNT artifacts to the ModuleDataEvent
                Collection<BlackboardAttribute> osAcctAttributes = new ArrayList<>();
                osAcctAttributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME,
                        NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                        ((result.get("username_value").toString() != null) ? result.get("username_value").toString().replaceAll("'", "''") : ""))); //NON-NLS
                this.addArtifact(ARTIFACT_TYPE.TSK_OS_ACCOUNT, signonFile, osAcctAttributes);
            }

            dbFile.delete();
        }

        IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, bbartifacts));
    }

    private boolean isChromePreVersion30(String temps) {
        String query = "PRAGMA table_info(downloads)"; //NON-NLS
        List<HashMap<String, Object>> columns = this.dbConnect(temps, query);
        for (HashMap<String, Object> col : columns) {
            if (col.get("name").equals("url")) { //NON-NLS
                return true;
            }
        }

        return false;
    }
}
