 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2018 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Firefox recent activity extraction
 */
class Firefox extends Extract {

    private static final Logger logger = Logger.getLogger(Firefox.class.getName());
    private static final String HISTORY_QUERY = "SELECT moz_historyvisits.id,url,title,visit_count,(visit_date/1000000) AS visit_date,from_visit,(SELECT url FROM moz_places WHERE id=moz_historyvisits.from_visit) as ref FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0"; //NON-NLS
    private static final String COOKIE_QUERY = "SELECT name,value,host,expiry,(lastAccessed/1000000) AS lastAccessed,(creationTime/1000000) AS creationTime FROM moz_cookies"; //NON-NLS
    private static final String COOKIE_QUERY_V3 = "SELECT name,value,host,expiry,(lastAccessed/1000000) AS lastAccessed FROM moz_cookies"; //NON-NLS
    private static final String BOOKMARK_QUERY = "SELECT fk, moz_bookmarks.title, url, (moz_bookmarks.dateAdded/1000000) AS dateAdded FROM moz_bookmarks INNER JOIN moz_places ON moz_bookmarks.fk=moz_places.id"; //NON-NLS
    private static final String DOWNLOAD_QUERY = "SELECT target, source,(startTime/1000000) AS startTime, maxBytes FROM moz_downloads"; //NON-NLS
    private static final String DOWNLOAD_QUERY_V24 = "SELECT url, content AS target, (lastModified/1000000) AS lastModified FROM moz_places, moz_annos WHERE moz_places.id = moz_annos.place_id AND moz_annos.anno_attribute_id = 3"; //NON-NLS
    private final IngestServices services = IngestServices.getInstance();
    private Content dataSource;
    private IngestJobContext context;

    Firefox() {
        moduleName = NbBundle.getMessage(Firefox.class, "Firefox.moduleName");
    }

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        this.getHistory();
        this.getBookmark();
        this.getDownload();
        this.getCookie();
    }

    private void getHistory() {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> historyFiles;
        try {
            historyFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
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
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        for (AbstractFile historyFile : historyFiles) {
            if (historyFile.getSize() == 0) {
                continue;
            }

            String fileName = historyFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(historyFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox web history artifacts file '%s' (id=%d).",
                        fileName, historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox web history artifacts file '%s' (id=%d).",
                        temps, fileName, historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getName(),
                                fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, HISTORY_QUERY);
            logger.log(Level.INFO, "{0} - Now getting history from {1} with {2} artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("url").toString() != null) ? result.get("url").toString() : ""))); //NON-NLS
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        (Long.valueOf(result.get("visit_date").toString())))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("ref").toString() != null) ? result.get("ref").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("title").toString() != null) ? result.get("title").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"), (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : "")))); //NON-NLS

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY, historyFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, bbartifacts));
    }

    /**
     * Queries for bookmark files and adds artifacts
     */
    private void getBookmark() {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> bookmarkFiles;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getBookmark.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
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
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(bookmarkFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox bookmark artifacts file '%s' (id=%d).",
                        fileName, bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox bookmark artifacts file '%s' (id=%d).",
                        temps, fileName, bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getBookmark.errMsg.errAnalyzeFile",
                        this.getName(), fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, BOOKMARK_QUERY);
            logger.log(Level.INFO, "{0} - Now getting bookmarks from {1} with {2} artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("url").toString() != null) ? result.get("url").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("title").toString() != null) ? result.get("title").toString() : ""))); //NON-NLS
                if (Long.valueOf(result.get("dateAdded").toString()) > 0) { //NON-NLS
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                            NbBundle.getMessage(this.getClass(),
                                    "Firefox.parentModuleName.noSpace"),
                            (Long.valueOf(result.get("dateAdded").toString())))); //NON-NLS
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : "")))); //NON-NLS

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK, bookmarkFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK, bbartifacts));
    }

    /**
     * Queries for cookies file and adds artifacts
     */
    private void getCookie() {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "cookies.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getCookie.errMsg.errFetchFile");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
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
            if (cookiesFile.getSize() == 0) {
                continue;
            }
            String fileName = cookiesFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(cookiesFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox cookie artifacts file '%s' (id=%d).",
                        fileName, cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox cookie artifacts file '%s' (id=%d).",
                        temps, fileName, cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getCookie.errMsg.errAnalyzeFile", this.getName(),
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

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, query);
            logger.log(Level.INFO, "{0} - Now getting cookies from {1} with {2} artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("host").toString() != null) ? result.get("host").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        (Long.valueOf(result.get("lastAccessed").toString())))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("name").toString() != null) ? result.get("name").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("value").toString() != null) ? result.get("value").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));

                if (checkColumn == true) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                            NbBundle.getMessage(this.getClass(),
                                    "Firefox.parentModuleName.noSpace"),
                            (Long.valueOf(result.get("creationTime").toString())))); //NON-NLS
                }
                String domain = Util.extractDomain(result.get("host").toString()); //NON-NLS
                domain = domain.replaceFirst("^\\.+(?!$)", "");
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"), domain));

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }
            ++j;
            dbFile.delete();
        }

        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE, bbartifacts));
    }

    /**
     * Queries for downloads files and adds artifacts
     */
    private void getDownload() {
        getDownloadPreVersion24();
        getDownloadVersion24();
    }

    /**
     * Finds downloads artifacts from Firefox data from versions before 24.0.
     *
     * Downloads were stored in a separate downloads database.
     */
    private void getDownloadPreVersion24() {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadsFiles;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "downloads.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
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
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + j + ".db"; //NON-NLS
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox download artifacts file '%s' (id=%d).",
                        fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox download artifacts file '%s' (id=%d).",
                        temps, fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errAnalyzeFiles",
                        this.getName(), fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, DOWNLOAD_QUERY);
            logger.log(Level.INFO, "{0}- Now getting downloads from {1} with {2} artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("source").toString() != null) ? result.get("source").toString() : ""))); //NON-NLS
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("source").toString() != null) ? EscapeUtil.decodeURL(result.get("source").toString()) : "")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        (Long.valueOf(result.get("startTime").toString())))); //NON-NLS

                String target = result.get("target").toString(); //NON-NLS

                if (target != null) {
                    try {
                        String decodedTarget = URLDecoder.decode(target.replaceAll("file:///", ""), "UTF-8"); //NON-NLS
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                                NbBundle.getMessage(this.getClass(),
                                        "Firefox.parentModuleName.noSpace"),
                                decodedTarget));
                        long pathID = Util.findID(dataSource, decodedTarget);
                        if (pathID != -1) {
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID,
                                    NbBundle.getMessage(this.getClass(),
                                            "Firefox.parentModuleName.noSpace"),
                                    pathID));
                        }
                    } catch (UnsupportedEncodingException ex) {
                        logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex); //NON-NLS
                        errors++;
                    }
                }

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        (Util.extractDomain((result.get("source").toString() != null) ? result.get("source").toString() : "")))); //NON-NLS

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadsFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
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
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, bbartifacts));
    }

    /**
     * Gets download artifacts from Firefox data from version 24.
     *
     * Downloads are stored in the places database.
     */
    private void getDownloadVersion24() {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadsFiles;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getName() + ": " + msg);
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
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + "-downloads" + j + ".db"; //NON-NLS
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox download artifacts file '%s' (id=%d).",
                        fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox download artifacts file '%s' (id=%d).",
                        temps, fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errAnalyzeFile", this.getName(),
                                fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, DOWNLOAD_QUERY_V24);

            logger.log(Level.INFO, "{0} - Now getting downloads from {1} with {2} artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {

                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        ((result.get("url").toString() != null) ? result.get("url").toString() : ""))); //NON-NLS
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", ((result.get("source").toString() != null) ? EscapeUtil.decodeURL(result.get("source").toString()) : "")));
                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", (Long.valueOf(result.get("startTime").toString()))));

                String target = result.get("target").toString(); //NON-NLS
                if (target != null) {
                    try {
                        String decodedTarget = URLDecoder.decode(target.replaceAll("file:///", ""), "UTF-8"); //NON-NLS
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                                NbBundle.getMessage(this.getClass(),
                                        "Firefox.parentModuleName.noSpace"),
                                decodedTarget));
                        long pathID = Util.findID(dataSource, decodedTarget);
                        if (pathID != -1) {
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID,
                                    NbBundle.getMessage(this.getClass(),
                                            "Firefox.parentModuleName.noSpace"),
                                    pathID));
                        }
                    } catch (UnsupportedEncodingException ex) {
                        logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex); //NON-NLS
                        errors++;
                    }
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        Long.valueOf(result.get("lastModified").toString()))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        NbBundle.getMessage(this.getClass(), "Firefox.moduleName")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        NbBundle.getMessage(this.getClass(),
                                "Firefox.parentModuleName.noSpace"),
                        (Util.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : "")))); //NON-NLS

                BlackboardArtifact bbart = this.addArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadsFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
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
                NbBundle.getMessage(this.getClass(), "Firefox.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, bbartifacts));
    }
}
