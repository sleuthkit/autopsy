 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2013 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Chrome recent activity extraction
 */
class Chrome extends Extract {

    private static final String historyQuery = "SELECT urls.url, urls.title, urls.visit_count, urls.typed_count, "
            + "last_visit_time, urls.hidden, visits.visit_time, (SELECT urls.url FROM urls WHERE urls.id=visits.url) as from_visit, visits.transition FROM urls, visits WHERE urls.id = visits.url";
    private static final String cookieQuery = "select name, value, host_key, expires_utc,last_access_utc, creation_utc from cookies";
    private static final String bookmarkQuery = "SELECT starred.title, urls.url, starred.date_added, starred.date_modified, urls.typed_count,urls._last_visit_time FROM starred INNER JOIN urls ON urls.id = starred.url_id";
    private static final String downloadQuery = "select full_path, url, start_time, received_bytes from downloads";
    private static final String downloadQueryVersion30 = "SELECT current_path as full_path, url, start_time, received_bytes FROM downloads, downloads_url_chains WHERE downloads.id=downloads_url_chains.id";
    private static final String loginQuery = "select origin_url, username_value, signon_realm from logins";
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private int ChromeCount = 0;
    final private static String MODULE_VERSION = "1.0";
    private IngestServices services;

    //hide public constructor to prevent from instantiation by ingest module loader
    Chrome() {
        moduleName = NbBundle.getMessage(Chrome.class, "Chrome.moduleName");
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }


    @Override
    public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        dataFound = false;
        this.getHistory(dataSource, controller);
        this.getBookmark(dataSource, controller);
        this.getCookie(dataSource, controller);
        this.getLogin(dataSource, controller);
        this.getDownload(dataSource, controller);
    }

    /**
     * Query for history databases and add artifacts
     * @param dataSource
     * @param controller 
     */
    private void getHistory(Content dataSource, IngestDataSourceWorkerController controller) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> historyFiles = null;
        try {
            historyFiles = fileManager.findFiles(dataSource, "History", "Chrome");
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
        int j = 0;
        while (j < historyFiles.size()) {
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + historyFiles.get(j).getName().toString() + j + ".db";
            final AbstractFile historyFile = historyFiles.get(j++);
            if (historyFile.getSize() == 0) {
                continue;
            }
            try {
                ContentUtils.writeToFile(historyFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome web history artifacts.{0}", ex);
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getHistory.errMsg.errAnalyzingFile",
                                                         this.getName(), historyFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = null;
            tempList = this.dbConnect(temps, historyQuery);
            logger.log(Level.INFO, moduleName + "- Now getting history from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((Long.valueOf(result.get("last_visit_time").toString())) / 10000000)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("from_visit").toString() != null) ? result.get("from_visit").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("title").toString() != null) ? result.get("title").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""))));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, historyFile, bbattributes);

            }
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
    }

    /**
     * Search for bookmark files and make artifacts.
     * @param dataSource
     * @param controller 
     */
    private void getBookmark(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> bookmarkFiles = null;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "Bookmarks", "Chrome");
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (bookmarkFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome bookmark files.");
            return;
        }
        
        dataFound = true;
        int j = 0;
            
        while (j < bookmarkFiles.size()) {
            AbstractFile bookmarkFile =  bookmarkFiles.get(j++);
            if (bookmarkFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + bookmarkFile.getName().toString() + j + ".db";
            try {
                ContentUtils.writeToFile(bookmarkFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome bookmark artifacts.{0}", ex);
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzingFile",
                                                         this.getName(), bookmarkFile.getName()));
                continue;
            }
            
            logger.log(Level.INFO, moduleName + "- Now getting Bookmarks from " + temps);
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            
            FileReader tempReader;
            try {
                 tempReader = new FileReader(temps);
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, "Error while trying to read into the Bookmarks for Chrome.", ex);
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
                jRoot = jElement.get("roots").getAsJsonObject();
                jBookmark = jRoot.get("bookmark_bar").getAsJsonObject();
                jBookmarkArray = jBookmark.getAsJsonArray("children");
            } catch (JsonIOException | JsonSyntaxException | IllegalStateException ex) {
                logger.log(Level.WARNING, "Error parsing Json from Chrome Bookmark.", ex);
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzingFile3",
                                                         this.getName(), bookmarkFile.getName()));
                continue;
            }
            
            for (JsonElement result : jBookmarkArray) {
                JsonObject address = result.getAsJsonObject();
                if (address == null) {
                    continue;
                }
                JsonElement urlEl = address.get("url");
                String url = null;
                if (urlEl != null) {
                    url = urlEl.getAsString();
                }
                else {
                    url = "";
                }
                String name = null;
                JsonElement nameEl = address.get("name");
                if (nameEl != null) {
                    name = nameEl.getAsString();
                }
                else {
                    name = "";
                }
                Long date = null;
                JsonElement dateEl = address.get("date_added");
                if (dateEl != null) {
                    date = dateEl.getAsLong();
                }
                else {
                    date = Long.valueOf(0);
                }
                String domain = Util.extractDomain(url);
                try {
                    BlackboardArtifact bbart = bookmarkFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                    //TODO Revisit usage of deprecated constructor as per TSK-583
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", (date / 10000000)));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                             NbBundle.getMessage(this.getClass(),
                                                                                 "Chrome.parentModuleName"), url));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE.getTypeID(),
                                                             NbBundle.getMessage(this.getClass(),
                                                                                 "Chrome.parentModuleName"), name));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
                                                             NbBundle.getMessage(this.getClass(),
                                                                                 "Chrome.parentModuleName"), (date / 10000000)));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                             NbBundle.getMessage(this.getClass(),
                                                                                 "Chrome.parentModuleName"),
                                                             NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                             NbBundle.getMessage(this.getClass(),
                                                                                 "Chrome.parentModuleName"), domain));
                    bbart.addAttributes(bbattributes);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Chrome bookmark artifact{0}", ex);
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzingFile4",
                                                this.getName(), bookmarkFile.getName()));
                }
            }
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
    }

    /**
     * Queries for cookie files and adds artifacts
     * @param dataSource
     * @param controller 
     */
    private void getCookie(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles = null;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "Cookies", "Chrome");
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getCookie.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (cookiesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome cookies files.");
            return;
        }
        
        dataFound = true;
        int j = 0;
        while (j < cookiesFiles.size()) {
            AbstractFile cookiesFile = cookiesFiles.get(j++);
            if (cookiesFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + cookiesFile.getName().toString() + j + ".db";
            try {
                ContentUtils.writeToFile(cookiesFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome cookie artifacts.{0}", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Chrome.getCookie.errMsg.errAnalyzeFile", this.getName(),
                                            cookiesFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, cookieQuery);
            logger.log(Level.INFO, moduleName + "- Now getting cookies from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("host_key").toString() != null) ? result.get("host_key").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((Long.valueOf(result.get("last_access_utc").toString())) / 10000000)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("name").toString() != null) ? result.get("name").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("value").toString() != null) ? result.get("value").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                String domain = result.get("host_key").toString();
                domain = domain.replaceFirst("^\\.+(?!$)", "");
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), domain));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);
            }

            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
    }

    /**
     * Queries for download files and adds artifacts
     * @param dataSource
     * @param controller 
     */
    private void getDownload(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadFiles = null;
        try {
            downloadFiles = fileManager.findFiles(dataSource, "History", "Chrome");
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getDownload.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (downloadFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome download files.");
            return;
        }
        
        dataFound = true;
        int j = 0;
        while (j < downloadFiles.size()) {
            AbstractFile downloadFile = downloadFiles.get(j++);
            if (downloadFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + downloadFile.getName().toString() + j + ".db";
            try {
                ContentUtils.writeToFile(downloadFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome download artifacts.{0}", ex);
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getDownload.errMsg.errAnalyzeFiles1",
                                                         this.getName(), downloadFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = null;
            
            if (isChromePreVersion30(temps)) {
                tempList = this.dbConnect(temps, downloadQuery);
            } else {
                tempList = this.dbConnect(temps, downloadQueryVersion30);
            }
            
            logger.log(Level.INFO, moduleName + "- Now getting downloads from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), (result.get("full_path").toString())));
                long pathID = Util.findID(dataSource, (result.get("full_path").toString()));
                if (pathID != -1) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(),
                                                             NbBundle.getMessage(this.getClass(),
                                                                                 "Chrome.parentModuleName"), pathID));
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                Long time = (Long.valueOf(result.get("start_time").toString()));
                String Tempdate = time.toString();
                time = Long.valueOf(Tempdate) / 10000000;
                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", time));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), time));
                String domain = Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : "");
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), domain));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadFile, bbattributes);
            }

            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD));
    }

    /**
     * Queries for login files and adds artifacts
     * @param dataSource
     * @param controller 
     */
    private void getLogin(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> signonFiles = null;
        try {
            signonFiles = fileManager.findFiles(dataSource, "signons.sqlite", "Chrome");
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (signonFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome signon files.");
            return;
        }
        
        dataFound = true;
        int j = 0;
        while (j < signonFiles.size()) {
            AbstractFile signonFile = signonFiles.get(j++);
            if (signonFile.getSize() == 0) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + signonFile.getName().toString() + j + ".db";
            try {
                ContentUtils.writeToFile(signonFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome login artifacts.{0}", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errAnalyzingFiles", this.getName(),
                                            signonFile.getName()));
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, loginQuery);
            logger.log(Level.INFO, moduleName + "- Now getting login information from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("origin_url").toString() != null) ? result.get("origin_url").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("origin_url").toString() != null) ? EscapeUtil.decodeURL(result.get("origin_url").toString()) : "")));
                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", ((Long.valueOf(result.get("last_visit_time").toString())) / 1000000)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((Long.valueOf(result.get("last_visit_time").toString())) / 1000000)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("from_visit").toString() != null) ? result.get("from_visit").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("title").toString() != null) ? result.get("title").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         (Util.extractDomain((result.get("origin_url").toString() != null) ? result.get("url").toString() : ""))));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         ((result.get("username_value").toString() != null) ? result.get("username_value").toString().replaceAll("'", "''") : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                                                         result.get("signon_realm").toString()));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, signonFile, bbattributes);
            }

            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
    }

    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
        services = IngestServices.getDefault();
    }

    @Override
    public void complete() {
    }

    @Override
    public void stop() {
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "Chrome.getDesc.text");
    }


    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }

    private boolean isChromePreVersion30(String temps) {
        String query = "PRAGMA table_info(downloads)";
        List<HashMap<String, Object>> columns = this.dbConnect(temps, query);
        for (HashMap<String, Object> col : columns) {
            if (col.get("name").equals("url")) {
                return true;
            }
        }
        
        return false;
    }
}
