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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
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
public class Chrome extends Extract {

    private static final String chquery = "SELECT urls.url, urls.title, urls.visit_count, urls.typed_count, "
            + "last_visit_time, urls.hidden, visits.visit_time, (SELECT urls.url FROM urls WHERE urls.id=visits.url) as from_visit, visits.transition FROM urls, visits WHERE urls.id = visits.url";
    private static final String chcookiequery = "select name, value, host_key, expires_utc,last_access_utc, creation_utc from cookies";
    private static final String chbookmarkquery = "SELECT starred.title, urls.url, starred.date_added, starred.date_modified, urls.typed_count,urls._last_visit_time FROM starred INNER JOIN urls ON urls.id = starred.url_id";
    private static final String chdownloadquery = "select full_path, url, start_time, received_bytes from downloads";
    private static final String chloginquery = "select origin_url, username_value, signon_realm from logins";
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    public int ChromeCount = 0;
    final public static String MODULE_VERSION = "1.0";
    private IngestServices services;

    //hide public constructor to prevent from instantiation by ingest module loader
    Chrome() {
        moduleName = "Chrome";
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }


    @Override
    public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        this.getHistory(dataSource, controller);
        this.getBookmark(dataSource, controller);
        this.getCookie(dataSource, controller);
        this.getLogin(dataSource, controller);
        this.getDownload(dataSource, controller);
    }

    private void getHistory(Content dataSource, IngestDataSourceWorkerController controller) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> historyFiles = null;
        try {
            historyFiles = fileManager.findFiles(dataSource, "History", "Chrome");
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error when trying to get Chrome history files.", ex);
        }
        
        // get only the allocated ones, for now
        List<AbstractFile> allocatedHistoryFiles = new ArrayList<>();
        for (AbstractFile historyFile : historyFiles) {
            if (historyFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)) {
                allocatedHistoryFiles.add(historyFile);
            }
        }
        
        // log a message if we don't have any allocated history files
        if (allocatedHistoryFiles.size() == 0) {
            logger.log(Level.INFO, "Could not find any allocated Chrome history files.");
            return;
        }

        int j = 0;
        while (j < historyFiles.size()) {
            String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + historyFiles.get(j).getName().toString() + j + ".db";
            int errors = 0;
            final AbstractFile historyFile = historyFiles.get(j++);
            if (historyFile.getSize() == 0) {
                continue;
            }
            try {
                ContentUtils.writeToFile(historyFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome web history artifacts.{0}", ex);
                this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + historyFile.getName());
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = null;
            tempList = this.dbConnect(temps, chquery);
            logger.log(Level.INFO, moduleName + "- Now getting history from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "Recent Activity", ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                //TODO Revisit usage of deprecated constructor per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", ((Long.valueOf(result.get("last_visit_time").toString())) / 10000000)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "Recent Activity", ((Long.valueOf(result.get("last_visit_time").toString())) / 10000000)));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "Recent Activity", ((result.get("from_visit").toString() != null) ? result.get("from_visit").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "Recent Activity", ((result.get("title").toString() != null) ? result.get("title").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "Recent Activity", "Chrome"));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "Recent Activity", (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""))));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, historyFile, bbattributes);

            }
            if (errors > 0) {
                this.addErrorMessage(this.getName() + ": Error parsing " + errors + " Chrome web history artifacts.");
            }

            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
    }

    private void getBookmark(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> bookmarkFiles = null;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "Bookmarks", "Chrome");
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error when trying to get Chrome history files.", ex);
        }

        int j = 0;
        if (bookmarkFiles != null && !bookmarkFiles.isEmpty()) {
            while (j < bookmarkFiles.size()) {
                AbstractFile bookmarkFile =  bookmarkFiles.get(j++);
                String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + bookmarkFile.getName().toString() + j + ".db";
                int errors = 0;
                try {
                    ContentUtils.writeToFile(bookmarkFile, new File(temps));
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome bookmark artifacts.{0}", ex);
                    this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + bookmarkFile.getName());
                    continue;
                }
                logger.log(Level.INFO, moduleName + "- Now getting Bookmarks from " + temps);
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                try {
                   final JsonParser parser = new JsonParser();
                    JsonElement jsonElement = parser.parse(new FileReader(temps));
                    JsonObject jElement = jsonElement.getAsJsonObject();
                    JsonObject jRoot = jElement.get("roots").getAsJsonObject();
                    JsonObject jBookmark = jRoot.get("bookmark_bar").getAsJsonObject();
                    JsonArray jBookmarkArray = jBookmark.getAsJsonArray("children");
                    for (JsonElement result : jBookmarkArray) {
                        try {
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
                            BlackboardArtifact bbart = bookmarkFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            //TODO Revisit usage of deprecated constructor as per TSK-583
                            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", (date / 10000000)));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "Recent Activity", (date / 10000000)));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "Recent Activity", url));
                            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", EscapeUtil.decodeURL(url)));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "Recent Activity", name));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "Recent Activity", "Chrome"));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "Recent Activity", domain));
                            bbart.addAttributes(bbattributes);
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error while trying to insert Chrom bookmark artifact{0}", ex);
                            errors++;
                        }
                    }
                    if (errors > 0) {
                        this.addErrorMessage(this.getName() + ": Error parsing " + errors + " Chrome bookmark artifacts.");
                    }
                } catch (FileNotFoundException ex) {
                    logger.log(Level.SEVERE, "Error while trying to read into the Bookmarks for Chrome." + ex);
                }
             
                dbFile.delete();
            }

            services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
        }
    }

    //COOKIES section
    // This gets the cookie info
    private void getCookie(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles = null;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "Cookies", "Chrome");
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error when trying to get Chrome history files.", ex);
        }

        int j = 0;
        if (cookiesFiles != null && !cookiesFiles.isEmpty()) {
            while (j < cookiesFiles.size()) {
                AbstractFile cookiesFile = cookiesFiles.get(j++);
                String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + cookiesFile.getName().toString() + j + ".db";
                int errors = 0;
                try {
                    ContentUtils.writeToFile(cookiesFile, new File(temps));
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome cookie artifacts.{0}", ex);
                    this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + cookiesFile.getName());
                    continue;
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }

                List<HashMap<String, Object>> tempList = this.dbConnect(temps, chcookiequery);
                logger.log(Level.INFO, moduleName + "- Now getting cookies from " + temps + " with " + tempList.size() + "artifacts identified.");
                for (HashMap<String, Object> result : tempList) {
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                    //TODO Revisit usage of deprecated constructor as per TSK-583
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "Recent Activity", "Title", ((result.get("name").toString() != null) ? result.get("name").toString() : "")));
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "Recent Activity", "Last Visited", ((Long.valueOf(result.get("last_access_utc").toString())) / 10000000)));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "Recent Activity", ((result.get("name").toString() != null) ? result.get("name").toString() : "")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "Recent Activity", ((Long.valueOf(result.get("last_access_utc").toString())) / 10000000)));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "Recent Activity", ((result.get("value").toString() != null) ? result.get("value").toString() : "")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "Recent Activity", "Chrome"));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "Recent Activity", ((result.get("host_key").toString() != null) ? result.get("host_key").toString() : "")));
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("host_key").toString() != null) ? EscapeUtil.decodeURL(result.get("host_key").toString()) : "")));
                    String domain = result.get("host_key").toString();
                    domain = domain.replaceFirst("^\\.+(?!$)", "");
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "Recent Activity", domain));
                    this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);

                }
                if (errors > 0) {
                    this.addErrorMessage(this.getName() + ": Error parsing " + errors + " Chrome cookie artifacts.");
                }
        
                dbFile.delete();
            }

            services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
        }
    }

    //Downloads section
    // This gets the downloads info
    private void getDownload(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> historyFiles = null;
        try {
            historyFiles = fileManager.findFiles(dataSource, "History", "Chrome");
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error when trying to get Chrome history files.", ex);
        }

        int j = 0;
        if (historyFiles != null && !historyFiles.isEmpty()) {
            while (j < historyFiles.size()) {
                AbstractFile historyFile = historyFiles.get(j++);
                if (historyFile.getSize() == 0) {
                    continue;
                }
                String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + historyFile.getName().toString() + j + ".db";
                int errors = 0;
                try {
                    ContentUtils.writeToFile(historyFile, new File(temps));
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome download artifacts.{0}", ex);
                    this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + historyFile.getName());
                    continue;
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }

                List<HashMap<String, Object>> tempList = this.dbConnect(temps, chdownloadquery);
                logger.log(Level.INFO, moduleName + "- Now getting downloads from " + temps + " with " + tempList.size() + "artifacts identified.");
                for (HashMap<String, Object> result : tempList) {
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), "Recent Activity", (result.get("full_path").toString())));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(), "Recent Activity", Util.findID(dataSource, (result.get("full_path").toString()))));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "Recent Activity", ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                    Long time = (Long.valueOf(result.get("start_time").toString()));
                    String Tempdate = time.toString();
                    time = Long.valueOf(Tempdate) / 10000000;
                    //TODO Revisit usage of deprecated constructor as per TSK-583
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", time));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "Recent Activity", time));
                    String domain = Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : "");
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "Recent Activity", domain));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "Recent Activity", "Chrome"));
                    this.addArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, historyFile, bbattributes);

                }
                if (errors > 0) {
                    this.addErrorMessage(this.getName() + ": Error parsing " + errors + " Chrome download artifacts.");
                }
       
                dbFile.delete();
            }

            services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD));
        }
    }

    //Login/Password section
    // This gets the user info
    private void getLogin(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> signonFiles = null;
        try {
            signonFiles = fileManager.findFiles(dataSource, "signons.sqlite", "Chrome");
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error when trying to get Chrome history files.", ex);
        }

        int j = 0;
        if (signonFiles != null && !signonFiles.isEmpty()) {
            while (j < signonFiles.size()) {
                AbstractFile signonFile = signonFiles.get(j++);
                String temps = RAImageIngestModule.getRATempPath(currentCase, "chrome") + File.separator + signonFile.getName().toString() + j + ".db";
                int errors = 0;
                try {
                    ContentUtils.writeToFile(signonFile, new File(temps));
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error writing temp sqlite db for Chrome login artifacts.{0}", ex);
                    this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + signonFile.getName());
                    continue;
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                List<HashMap<String, Object>> tempList = this.dbConnect(temps, chloginquery);
                logger.log(Level.INFO, moduleName + "- Now getting login information from " + temps + " with " + tempList.size() + "artifacts identified.");
                for (HashMap<String, Object> result : tempList) {
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "Recent Activity", ((result.get("origin_url").toString() != null) ? result.get("origin_url").toString() : "")));
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("origin_url").toString() != null) ? EscapeUtil.decodeURL(result.get("origin_url").toString()) : "")));
                    //TODO Revisit usage of deprecated constructor as per TSK-583
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", ((Long.valueOf(result.get("last_visit_time").toString())) / 1000000)));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "Recent Activity", ((Long.valueOf(result.get("last_visit_time").toString())) / 1000000)));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "Recent Activity", ((result.get("from_visit").toString() != null) ? result.get("from_visit").toString() : "")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "Recent Activity", ((result.get("title").toString() != null) ? result.get("title").toString() : "")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "Recent Activity", "Chrome"));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "Recent Activity", (Util.extractDomain((result.get("origin_url").toString() != null) ? result.get("url").toString() : ""))));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME.getTypeID(), "Recent Activity", ((result.get("username_value").toString() != null) ? result.get("username_value").toString().replaceAll("'", "''") : "")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "Recent Activity", result.get("signon_realm").toString()));
                    this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, signonFile, bbattributes);

                }
                if (errors > 0) {
                    this.addErrorMessage(this.getName() + ": Error parsing " + errors + " Chrome login artifacts.");
                }
             
                dbFile.delete();
            }

            services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
        }
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
    }

    @Override
    public void complete() {
        logger.info("Chrome Extract has completed");
    }

    @Override
    public void stop() {
        logger.info("Attempted to stop chrome extract, but operation is not supported; skipping...");
    }

    @Override
    public String getDescription() {
        return "Extracts activity from the Google Chrome browser.";
    }


    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
