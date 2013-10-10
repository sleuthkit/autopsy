 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
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

/**
 * Firefox recent activity extraction
 */
public class Firefox extends Extract {

    private static final String ffquery = "SELECT moz_historyvisits.id,url,title,visit_count,(visit_date/1000000) as visit_date,from_visit,(SELECT url FROM moz_places WHERE id=moz_historyvisits.from_visit) as ref FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0";
    private static final String ffcookiequery = "SELECT name,value,host,expiry,(lastAccessed/1000000) as lastAccessed,(creationTime/1000000) as creationTime FROM moz_cookies";
    private static final String ff3cookiequery = "SELECT name,value,host,expiry,(lastAccessed/1000000) as lastAccessed FROM moz_cookies";
    private static final String ffbookmarkquery = "SELECT fk, moz_bookmarks.title, url FROM moz_bookmarks INNER JOIN moz_places ON moz_bookmarks.fk=moz_places.id";
    private static final String ffdownloadquery = "select target, source,(startTime/1000000) as startTime, maxBytes  from moz_downloads";
    public int FireFoxCount = 0;
    final public static String MODULE_VERSION = "1.0";
    private IngestServices services;

    //hide public constructor to prevent from instantiation by ingest module loader
    Firefox() {
        moduleName = "FireFox";
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        this.getHistory(dataSource, controller);
        this.getBookmark(dataSource, controller);
        this.getDownload(dataSource, controller);
        this.getCookie(dataSource, controller);
    }

    private void getHistory(Content dataSource, IngestDataSourceWorkerController controller) {
        //Make these seperate, this is for history

        //List<FsContent> FFSqlitedb = this.extractFiles(dataSource, "select * from tsk_files where name LIKE '%places.sqlite%' and name NOT LIKE '%journal%' and parent_path LIKE '%Firefox%'");
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> historyFiles = null;
        try {
            historyFiles = fileManager.findFiles(dataSource, "%places.sqlite%", "Firefox");
        } catch (TskCoreException ex) {
            String msg = "Error fetching internet history files for Firefox.";
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
        }

        int j = 0;
        for (AbstractFile historyFile : historyFiles) {
            String fileName = historyFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db";
            try {
                ContentUtils.writeToFile(historyFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox web history artifacts.{0}", ex);
                this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + fileName);
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, ffquery);
            logger.log(Level.INFO, moduleName + "- Now getting history from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", (Long.valueOf(result.get("visit_date").toString()))));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "RecentActivity", (Long.valueOf(result.get("visit_date").toString()))));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "RecentActivity", ((result.get("ref").toString() != null) ? result.get("ref").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", ((result.get("title").toString() != null) ? result.get("title").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "FireFox"));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""))));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, historyFile, bbattributes);

            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
    }

    private void getBookmark(Content dataSource, IngestDataSourceWorkerController controller) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> bookmarkFiles = null;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox");
        } catch (TskCoreException ex) {
            String msg = "Error fetching bookmark files for Firefox.";
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        int j = 0;
        for (AbstractFile bookmarkFile : bookmarkFiles) {
            String fileName = bookmarkFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db";
            try {
                ContentUtils.writeToFile(bookmarkFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox bookmark artifacts.{0}", ex);
                this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + fileName);
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, ffbookmarkquery);
            logger.log(Level.INFO, moduleName + "- Now getting bookmarks from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", ((result.get("title").toString() != null) ? result.get("title").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "FireFox"));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""))));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK, bookmarkFile, bbattributes);

            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
    }

    //COOKIES section
    // This gets the cookie info
    private void getCookie(Content dataSource, IngestDataSourceWorkerController controller) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles = null;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "cookies.sqlite", "Firefox");
        } catch (TskCoreException ex) {
            String msg = "Error fetching cookies files for Firefox.";
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        int j = 0;
        for (AbstractFile cookiesFile : cookiesFiles) {
            String fileName = cookiesFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db";
            try {
                ContentUtils.writeToFile(cookiesFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox cookie artifacts.{0}", ex);
                this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + fileName);
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            boolean checkColumn = Util.checkColumn("creationTime", "moz_cookies", temps);
            String query = null;
            if (checkColumn) {
                query = ffcookiequery;
            } else {
                query = ff3cookiequery;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, query);
            logger.log(Level.INFO, moduleName + "- Now getting cookies from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", ((result.get("host").toString() != null) ? result.get("host").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("host").toString() != null) ? EscapeUtil.decodeURL(result.get("host").toString()) : "")));
                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "Title", ((result.get("name").toString() != null) ? result.get("name").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Last Visited", (Long.valueOf(result.get("lastAccessed").toString()))));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", ((result.get("name").toString() != null) ? result.get("name").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", (Long.valueOf(result.get("lastAccessed").toString()))));
                if (checkColumn == true) {
                    //TODO Revisit usage of deprecated constructor as per TSK-583
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Created", (Long.valueOf(result.get("creationTime").toString()))));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", (Long.valueOf(result.get("creationTime").toString()))));
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "FireFox"));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", ((result.get("host").toString() != null) ? result.get("host").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", ((result.get("value").toString() != null) ? result.get("value").toString() : "")));
                String domain = Util.extractDomain(result.get("host").toString());
                domain = domain.replaceFirst("^\\.+(?!$)", "");
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);

            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
    }

    //Downloads section
    // This gets the downloads info
    private void getDownload(Content dataSource, IngestDataSourceWorkerController controller) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadsFiles = null;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "downloads.sqlite", "Firefox");
        } catch (TskCoreException ex) {
            String msg = "Error fetching 'downloads' files for Firefox.";
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        int j = 0;
        for (AbstractFile downloadsFile : downloadsFiles) {
            String fileName = downloadsFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db";
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox download artifacts.{0}", ex);
                this.addErrorMessage(this.getName() + ": Error while trying to analyze file:" + fileName);
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, ffdownloadquery);
            logger.log(Level.INFO, moduleName + "- Now getting downloads from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {
                try {
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                    String urldecodedtarget = URLDecoder.decode(result.get("source").toString().replaceAll("file:///", ""), "UTF-8");
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", ((result.get("source").toString() != null) ? result.get("source").toString() : "")));
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("source").toString() != null) ? EscapeUtil.decodeURL(result.get("source").toString()) : "")));
                    //TODO Revisit usage of deprecated constructor as per TSK-583
                    //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", (Long.valueOf(result.get("startTime").toString()))));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "RecentActivity", (Long.valueOf(result.get("startTime").toString()))));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(), "RecentActivity", Util.findID(dataSource, urldecodedtarget)));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), "RecentActivity", ((result.get("target").toString() != null) ? result.get("target").toString() : "")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "FireFox"));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", (Util.extractDomain((result.get("source").toString() != null) ? result.get("source").toString() : ""))));
                    this.addArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadsFile, bbattributes);
                } catch (UnsupportedEncodingException ex) {
                    logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex);
                    errors++;
                }
            }
            if (errors > 0) {
                this.addErrorMessage(this.getName() + ": Error parsing " + errors + " Firefox web history artifacts.");
            }
            j++;
            dbFile.delete();
            break;
        }
        
        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD));
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
    }

    @Override
    public void complete() {
        logger.info("Firefox Extract has completed.");
    }

    @Override
    public void stop() {
        logger.info("Attmped to stop Firefox extract, but operation is not supported; skipping...");
    }

    @Override
    public String getDescription() {
        return "Extracts activity from the Mozilla FireFox browser.";
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
