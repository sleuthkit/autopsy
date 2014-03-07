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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
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
class Firefox extends Extract {

    private static final String historyQuery = "SELECT moz_historyvisits.id,url,title,visit_count,(visit_date/1000000) as visit_date,from_visit,(SELECT url FROM moz_places WHERE id=moz_historyvisits.from_visit) as ref FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0";
    private static final String cookieQuery = "SELECT name,value,host,expiry,(lastAccessed/1000000) as lastAccessed,(creationTime/1000000) as creationTime FROM moz_cookies";
    private static final String cookieQueryV3 = "SELECT name,value,host,expiry,(lastAccessed/1000000) as lastAccessed FROM moz_cookies";
    private static final String bookmarkQuery = "SELECT fk, moz_bookmarks.title, url, (moz_bookmarks.dateAdded/1000000) as dateAdded FROM moz_bookmarks INNER JOIN moz_places ON moz_bookmarks.fk=moz_places.id";
    private static final String downloadQuery = "SELECT target, source,(startTime/1000000) as startTime, maxBytes  FROM moz_downloads";
    private static final String downloadQueryVersion24 = "SELECT url, content as target, (lastModified/1000000) as lastModified FROM moz_places, moz_annos WHERE moz_places.id = moz_annos.place_id AND moz_annos.anno_attribute_id = 3";
    
    final private static String MODULE_VERSION = "1.0";
    private IngestServices services;

    //hide public constructor to prevent from instantiation by ingest module loader
    Firefox() {
        moduleName = NbBundle.getMessage(Firefox.class, "Firefox.moduleName");
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public void process(PipelineContext<IngestModuleDataSource> pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        dataFound = false;
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
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errFetchingFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }
        
        if (historyFiles.isEmpty()) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.noFilesFound");
            logger.log(Level.INFO, msg);
            return;
        }

        dataFound = true;
        
        int j = 0;
        for (AbstractFile historyFile : historyFiles) {
            if (historyFile.getSize() == 0) {
                continue;
            }
            
            String fileName = historyFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db";
            try {
                ContentUtils.writeToFile(historyFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox web history artifacts.{0}", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getName(),
                                            fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, historyQuery);
            logger.log(Level.INFO, moduleName + "- Now getting history from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         (Long.valueOf(result.get("visit_date").toString()))));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("ref").toString() != null) ? result.get("ref").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("title").toString() != null) ? result.get("title").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"), (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""))));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, historyFile, bbattributes);

            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
    }

    /**
     * Queries for bookmark files and adds artifacts
     * @param dataSource
     * @param controller 
     */
    private void getBookmark(Content dataSource, IngestDataSourceWorkerController controller) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> bookmarkFiles = null;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox");
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getBookmark.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (bookmarkFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any firefox bookmark files.");
            return;
        }
        
        dataFound = true;
        
        int j = 0;
        for (AbstractFile bookmarkFile : bookmarkFiles) {
            if (bookmarkFile.getSize() == 0) {
                continue;
            }
            String fileName = bookmarkFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db";
            try {
                ContentUtils.writeToFile(bookmarkFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox bookmark artifacts.{0}", ex);
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getBookmark.errMsg.errAnalyzeFile",
                                                         this.getName(), fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, bookmarkQuery);
            logger.log(Level.INFO, moduleName + "- Now getting bookmarks from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("title").toString() != null) ? result.get("title").toString() : "")));
                if (Long.valueOf(result.get("dateAdded").toString()) > 0) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
                                                             NbBundle.getMessage(this.getClass(),
                                                                                 "Firefox.parentModuleName.noSpace"),
                                                             (Long.valueOf(result.get("dateAdded").toString()))));
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""))));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK, bookmarkFile, bbattributes);

            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
    }

    /**
     * Queries for cookies file and adds artifacts
     * @param dataSource
     * @param controller 
     */
    private void getCookie(Content dataSource, IngestDataSourceWorkerController controller) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles = null;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "cookies.sqlite", "Firefox");
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getCookie.errMsg.errFetchFile");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (cookiesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Firefox cookie files.");
            return;
        }
        
        dataFound = true;
        int j = 0;
        for (AbstractFile cookiesFile : cookiesFiles) {
            if (cookiesFile.getSize() == 0) {
                continue;
            }
            String fileName = cookiesFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db";
            try {
                ContentUtils.writeToFile(cookiesFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox cookie artifacts.{0}", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getCookie.errMsg.errAnalyzeFile", this.getName(),
                                            fileName));
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
                query = cookieQuery;
            } else {
                query = cookieQueryV3;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, query);
            logger.log(Level.INFO, moduleName + "- Now getting cookies from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("host").toString() != null) ? result.get("host").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         (Long.valueOf(result.get("lastAccessed").toString()))));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("name").toString() != null) ? result.get("name").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("value").toString() != null) ? result.get("value").toString() : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                
                if (checkColumn == true) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
                                                             NbBundle.getMessage(this.getClass(),
                                                                                 "Firefox.parentModuleName.noSpace"),
                                                             (Long.valueOf(result.get("creationTime").toString()))));
                }
                String domain = Util.extractDomain(result.get("host").toString());
                domain = domain.replaceFirst("^\\.+(?!$)", "");
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"), domain));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);
            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
    }
    
    /**
     * Queries for downloads files and adds artifacts
     * @param dataSource
     * @param controller 
     */
    private void getDownload(Content dataSource, IngestDataSourceWorkerController controller) {
        getDownloadPreVersion24(dataSource, controller);
        getDownloadVersion24(dataSource, controller);
    }

    /**
     * Finds downloads artifacts from Firefox data from versions before 24.0.
     * 
     * Downloads were stored in a separate downloads database.
     * 
     * @param dataSource
     * @param controller 
     */
    private void getDownloadPreVersion24(Content dataSource, IngestDataSourceWorkerController controller) {
        
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadsFiles = null;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "downloads.sqlite", "Firefox");
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }
        
        if (downloadsFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any pre-version-24.0 Firefox download files.");
            return;
        }
        
        dataFound = true;
        int j = 0;
        for (AbstractFile downloadsFile : downloadsFiles) {
            if (downloadsFile.getSize() == 0) {
                continue;
            }
            String fileName = downloadsFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db";
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox download artifacts.{0}", ex);
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errAnalyzeFiles",
                                                         this.getName(), fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, downloadQuery);
            logger.log(Level.INFO, moduleName + "- Now getting downloads from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {
                
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("source").toString() != null) ? result.get("source").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("source").toString() != null) ? EscapeUtil.decodeURL(result.get("source").toString()) : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         (Long.valueOf(result.get("startTime").toString()))));
                
                String target = result.get("target").toString();
                
                if (target != null) {
                    try {
                        String decodedTarget = URLDecoder.decode(target.toString().replaceAll("file:///", ""), "UTF-8");
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(),
                                                                 NbBundle.getMessage(this.getClass(),
                                                                                     "Firefox.parentModuleName.noSpace"),
                                                                 decodedTarget));
                        long pathID = Util.findID(dataSource, decodedTarget);
                        if (pathID != -1) {
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(),
                                                                     NbBundle.getMessage(this.getClass(),
                                                                                         "Firefox.parentModuleName.noSpace"),
                                                                     pathID));
                        }
                    } catch (UnsupportedEncodingException ex) {
                        logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex);
                        errors++;
                    }
                }
                    
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         (Util.extractDomain((result.get("source").toString() != null) ? result.get("source").toString() : ""))));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadsFile, bbattributes);
                
            }
            if (errors > 0) {
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errParsingArtifacts",
                                            this.getName(), errors));
            }
            j++;
            dbFile.delete();
            break;
        }
        
        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD));
    }
    
    /**
     * Gets download artifacts from Firefox data from version 24.
     * 
     * Downloads are stored in the places database.
     * 
     * @param dataSource
     * @param controller 
     */
    private void getDownloadVersion24(Content dataSource, IngestDataSourceWorkerController controller) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadsFiles = null;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox");
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }
        
        if (downloadsFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any version-24.0 Firefox download files.");
            return;
        }
        
        dataFound = true;
        int j = 0;
        for (AbstractFile downloadsFile : downloadsFiles) {
            if (downloadsFile.getSize() == 0) {
                continue;
            }
            String fileName = downloadsFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + "-downloads" + j + ".db";
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps));
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing the sqlite db for firefox download artifacts.{0}", ex);
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errAnalyzeFile", this.getName(),
                                            fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (controller.isCancelled()) {
                dbFile.delete();
                break;
            }
            
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, downloadQueryVersion24);
            
            logger.log(Level.INFO, moduleName + "- Now getting downloads from " + temps + " with " + tempList.size() + "artifacts identified.");
            for (HashMap<String, Object> result : tempList) {
                
                Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         ((result.get("url").toString() != null) ? result.get("url").toString() : "")));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("source").toString() != null) ? EscapeUtil.decodeURL(result.get("source").toString()) : "")));
                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", (Long.valueOf(result.get("startTime").toString()))));
                
                String target = result.get("target").toString();
                if (target != null) {
                    try {
                        String decodedTarget = URLDecoder.decode(target.toString().replaceAll("file:///", ""), "UTF-8");
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(),
                                                                 NbBundle.getMessage(this.getClass(),
                                                                                     "Firefox.parentModuleName.noSpace"),
                                                                 decodedTarget));
                        long pathID = Util.findID(dataSource, decodedTarget);
                        if (pathID != -1) {
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(),
                                                                     NbBundle.getMessage(this.getClass(),
                                                                                         "Firefox.parentModuleName.noSpace"),
                                                                     pathID));
                        }
                    } catch (UnsupportedEncodingException ex) {
                        logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex);
                        errors++;
                    }
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         Long.valueOf(result.get("lastModified").toString())));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "Firefox.parentModuleName.noSpace"),
                                                         (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""))));
                this.addArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadsFile, bbattributes);
                
            }
            if (errors > 0) {
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errParsingArtifacts",
                                                         this.getName(), errors));
            }
            j++;
            dbFile.delete();
            break;
        }
        
        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD));
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
        return NbBundle.getMessage(this.getClass(), "Firefox.getDesc.text");
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
