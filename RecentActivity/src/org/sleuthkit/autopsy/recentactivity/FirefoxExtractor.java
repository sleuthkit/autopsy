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

import static com.google.common.collect.Lists.newArrayList;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Firefox recent activity extraction
 */
final class FirefoxExtractor extends Extract {

    private static final Logger logger = Logger.getLogger(FirefoxExtractor.class.getName());
    private static final String PARENT_MODULE_NAME
            = NbBundle.getMessage(FirefoxExtractor.class, "Firefox.parentModuleName.noSpace");

    private static final String PLACE_URL_PREFIX = "place:";
    private static final String HISTORY_QUERY = "SELECT moz_historyvisits.id,url,title,visit_count,(visit_date/1000000) AS visit_date,from_visit,(SELECT url FROM moz_places WHERE id=moz_historyvisits.from_visit) as ref FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0"; //NON-NLS
    private static final String COOKIE_QUERY = "SELECT name,value,host,expiry,(lastAccessed/1000000) AS lastAccessed,(creationTime/1000000) AS creationTime FROM moz_cookies"; //NON-NLS
    private static final String COOKIE_QUERY_V3 = "SELECT name,value,host,expiry,(lastAccessed/1000000) AS lastAccessed FROM moz_cookies"; //NON-NLS
    private static final String BOOKMARK_QUERY = "SELECT fk, moz_bookmarks.title, url, (moz_bookmarks.dateAdded/1000000) AS dateAdded FROM moz_bookmarks INNER JOIN moz_places ON moz_bookmarks.fk=moz_places.id"; //NON-NLS
    private static final String DOWNLOAD_QUERY = "SELECT target, source,(startTime/1000000) AS startTime, maxBytes FROM moz_downloads"; //NON-NLS
    private static final String DOWNLOAD_QUERY_V24 = "SELECT url, content AS target, (lastModified/1000000) AS lastModified FROM moz_places, moz_annos WHERE moz_places.id = moz_annos.place_id AND moz_annos.anno_attribute_id = 3"; //NON-NLS

    private Content dataSource;
    private IngestJobContext context;

    @Override
    protected String getModuleName() {
        return NbBundle.getMessage(FirefoxExtractor.class, "Firefox.moduleName");
    }

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        this.getHistory();
        this.getBookmark();
        getDownloadPreVersion24();
        getDownloadVersion24();
        this.getCookie();
    }

    private void getHistory() {
        List<AbstractFile> historyFiles;
        try {
            historyFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errFetchingFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (historyFiles.isEmpty()) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.noFilesFound");
            logger.log(Level.INFO, msg);
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        for (AbstractFile historyFile : historyFiles) {
            if (historyFile.getSize() == 0) {
                continue;
            }

            String fileName = historyFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + index + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(historyFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox web history artifacts file '%s' (id=%d).",
                        fileName, historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getModuleName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox web history artifacts file '%s' (id=%d).",
                        temps, fileName, historyFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getModuleName(),
                                fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, HISTORY_QUERY);
            logger.log(Level.INFO, "{0} - Now getting history from {1} with {2} artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) { 
                String url = Objects.toString(result.get("url"), "");
                Collection<BlackboardAttribute> bbattributes = newArrayList(
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                url),
                        new BlackboardAttribute(
                                TSK_DATETIME_ACCESSED, PARENT_MODULE_NAME,
                                Long.valueOf(result.get("visit_date").toString())), //NON-NLS
                        new BlackboardAttribute(
                                TSK_REFERRER, PARENT_MODULE_NAME,
                                Objects.toString(result.get("ref"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_TITLE, PARENT_MODULE_NAME,
                                Objects.toString(result.get("title"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName())); 

                if (isIgnoredUrl(url) == false) {
                    bbattributes.add(new BlackboardAttribute(
                            TSK_DOMAIN, PARENT_MODULE_NAME,
                            NetworkUtils.extractDomain(url)));
                }
                try {
                    BlackboardArtifact bbart = historyFile.newArtifact(TSK_WEB_HISTORY);
                    bbart.addAttributes(bbattributes);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to create Firefox history artifact.", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(Chrome.class, "Firefox.getHistory.errMsg.errAnalyzeFile=", //NON-NLS
                                    this.getModuleName(), historyFile.getName()));
                }
            }
            index++;
            dbFile.delete();
        }
        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Firefox history artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Queries for bookmark files and adds artifacts
     */
    private void getBookmark() {
        List<AbstractFile> bookmarkFiles;
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getBookmark.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (bookmarkFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any firefox bookmark files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        for (AbstractFile bookmarkFile : bookmarkFiles) {
            if (bookmarkFile.getSize() == 0) {
                continue;
            }
            String fileName = bookmarkFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + index + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(bookmarkFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox bookmark artifacts file '%s' (id=%d).",
                        fileName, bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getModuleName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox bookmark artifacts file '%s' (id=%d).",
                        temps, fileName, bookmarkFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getBookmark.errMsg.errAnalyzeFile",
                        this.getModuleName(), fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            List<HashMap<String, Object>> tempList = this.dbConnect(temps, BOOKMARK_QUERY);
            logger.log(Level.INFO, "{0} - Now getting bookmarks from {1} with {2} artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) { 
                String url = Objects.toString(result.get("url"), "");

                Collection<BlackboardAttribute> bbattributes = newArrayList(
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                url),
                        new BlackboardAttribute(
                                TSK_TITLE, PARENT_MODULE_NAME,
                                Objects.toString(result.get("title"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName())); //NON-NLS

                if (isIgnoredUrl(url) == false) {
                    bbattributes.add(new BlackboardAttribute(
                            TSK_DOMAIN, PARENT_MODULE_NAME,
                            NetworkUtils.extractDomain(url))); 
                }
                Long createdTime = Long.valueOf(result.get("dateAdded").toString());
                if (createdTime > 0) { //NON-NLS
                    bbattributes.add(new BlackboardAttribute(
                            TSK_DATETIME_CREATED, PARENT_MODULE_NAME,
                            createdTime)); //NON-NLS
                }
                try {
                    BlackboardArtifact bbart = bookmarkFile.newArtifact(TSK_WEB_BOOKMARK);
                    bbart.addAttributes(bbattributes);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to create Firefox bookmark artifact.", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(Chrome.class, "Firefox.getBookmark.errMsg.errAnalyzeFile=", //NON-NLS
                                    this.getModuleName(), bookmarkFile.getName()));
                }
            }
            index++;
            dbFile.delete();
        }
        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Firefox bookmark artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Queries for cookies file and adds artifacts
     */
    private void getCookie() {
        List<AbstractFile> cookiesFiles;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "cookies.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getCookie.errMsg.errFetchFile");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (cookiesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Firefox cookie files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        for (AbstractFile cookiesFile : cookiesFiles) {
            if (cookiesFile.getSize() == 0) {
                continue;
            }
            String fileName = cookiesFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + index + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(cookiesFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox cookie artifacts file '%s' (id=%d).",
                        fileName, cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getModuleName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox cookie artifacts file '%s' (id=%d).",
                        temps, fileName, cookiesFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getCookie.errMsg.errAnalyzeFile", this.getModuleName(),
                                fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }
            boolean checkColumn = Util.checkColumn("creationTime", "moz_cookies", temps); //NON-NLS
            String query = checkColumn ? COOKIE_QUERY : COOKIE_QUERY_V3;

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, query);
            logger.log(Level.INFO, "{0} - Now getting cookies from {1} with {2} artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) { 
                String host = Objects.toString(result.get("host"), "");

                Collection<BlackboardAttribute> bbattributes = newArrayList(
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                host),
                        new BlackboardAttribute(
                                TSK_DATETIME, PARENT_MODULE_NAME,
                                Long.valueOf(result.get("lastAccessed").toString())),
                        new BlackboardAttribute(
                                TSK_NAME, PARENT_MODULE_NAME,
                                Objects.toString(result.get("name"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_VALUE, PARENT_MODULE_NAME,
                                Objects.toString(result.get("value"), "")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName()));

                if (isIgnoredUrl(host) == false) {
                    bbattributes.add(new BlackboardAttribute(
                            TSK_DOMAIN, PARENT_MODULE_NAME,
                            NetworkUtils.extractDomain(host.replaceFirst("^\\.+(?!$)", ""))));//NON-NLS 
                }
                if (checkColumn) {
                    bbattributes.add(new BlackboardAttribute(
                            TSK_DATETIME_CREATED, PARENT_MODULE_NAME,
                            (Long.valueOf(result.get("creationTime").toString())))); //NON-NLS
                }
                try {
                    BlackboardArtifact bbart = cookiesFile.newArtifact(TSK_WEB_COOKIE);
                    bbart.addAttributes(bbattributes);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to create Firefox cookie artifact.", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(Chrome.class, "Firefox.getCookie.errMsg.errAnalyzeFile=", //NON-NLS
                                    this.getModuleName(), cookiesFile.getName()));
                }
            }
            ++index;
            dbFile.delete();
        }

        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Firefox cookie artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Finds downloads artifacts from Firefox data from versions before 24.0.
     *
     * Downloads were stored in a separate downloads database.
     */
    private void getDownloadPreVersion24() {

        List<AbstractFile> downloadsFiles;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "downloads.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (downloadsFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any pre-version-24.0 Firefox download files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        for (AbstractFile downloadsFile : downloadsFiles) {
            if (downloadsFile.getSize() == 0) {
                continue;
            }
            String fileName = downloadsFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + index + ".db"; //NON-NLS
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox download artifacts file '%s' (id=%d).",
                        fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getModuleName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox download artifacts file '%s' (id=%d).",
                        temps, fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errAnalyzeFiles",
                        this.getModuleName(), fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, DOWNLOAD_QUERY);
            logger.log(Level.INFO, "{0}- Now getting downloads from {1} with {2} artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                String sourceURL = Objects.toString(result.get("source"), "");//NON-NLS
                Collection<BlackboardAttribute> bbattributes = newArrayList(
                        new BlackboardAttribute(TSK_URL, PARENT_MODULE_NAME,
                                sourceURL),
                        new BlackboardAttribute(TSK_DATETIME_ACCESSED, PARENT_MODULE_NAME,
                                Long.valueOf(result.get("startTime").toString())), //NON-NLS
                        new BlackboardAttribute(TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName()));

                if (isIgnoredUrl(sourceURL) == false) {
                    bbattributes.add(new BlackboardAttribute(TSK_DOMAIN, PARENT_MODULE_NAME,
                            NetworkUtils.extractDomain(sourceURL)));
                }
                String target = result.get("target").toString(); //NON-NLS

                try {
                    String decodedTarget = URLDecoder.decode(target.replaceAll("file:///", ""), "UTF-8"); //NON-NLS
                    bbattributes.add(new BlackboardAttribute(
                            TSK_PATH, PARENT_MODULE_NAME,
                            decodedTarget));
                    long pathID = Util.findID(dataSource, decodedTarget);
                    if (pathID != -1) {
                        bbattributes.add(new BlackboardAttribute(
                                TSK_PATH_ID, PARENT_MODULE_NAME,
                                pathID));
                    }
                } catch (UnsupportedEncodingException ex) {
                    logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex); //NON-NLS
                    errors++;
                }
                try { 
                    BlackboardArtifact bbart = downloadsFile.newArtifact(TSK_WEB_DOWNLOAD);
                    bbart.addAttributes(bbattributes); 
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to create Firefox download artifact.", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(Chrome.class, "Firefox.getDlPre24.errMsg.errAnalyzeFiles", //NON-NLS
                                    this.getModuleName(), downloadsFile.getName()));
                }
            }
            if (errors > 0) {
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getDlPre24.errMsg.errParsingArtifacts",
                                this.getModuleName(), errors));
            }
            index++;
            dbFile.delete();
        }

        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Firefox download artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
     * Gets download artifacts from Firefox data from version 24.
     *
     * Downloads are stored in the places database.
     */
    private void getDownloadVersion24() {
        List<AbstractFile> downloadsFiles;
        try {
            downloadsFiles = fileManager.findFiles(dataSource, "places.sqlite", "Firefox"); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errFetchFiles");
            logger.log(Level.WARNING, msg);
            this.addErrorMessage(this.getModuleName() + ": " + msg);
            return;
        }

        if (downloadsFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any version-24.0 Firefox download files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int index = 0;
        for (AbstractFile downloadsFile : downloadsFiles) {
            if (downloadsFile.getSize() == 0) {
                continue;
            }
            String fileName = downloadsFile.getName();
            String temps = RAImageIngestModule.getRATempPath(currentCase, "firefox") + File.separator + fileName + "-downloads" + index + ".db"; //NON-NLS
            int errors = 0;
            try {
                ContentUtils.writeToFile(downloadsFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Firefox download artifacts file '%s' (id=%d).",
                        fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getHistory.errMsg.errAnalyzeFile", this.getModuleName(),
                                fileName));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Firefox download artifacts file '%s' (id=%d).",
                        temps, fileName, downloadsFile.getId()), ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errAnalyzeFile", this.getModuleName(),
                                fileName));
                continue;
            }
            File dbFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            List<HashMap<String, Object>> tempList = this.dbConnect(temps, DOWNLOAD_QUERY_V24);

            logger.log(Level.INFO, "{0} - Now getting downloads from {1} with {2} artifacts identified.", new Object[]{getModuleName(), temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                String url = result.get("url").toString(); //NON-NLS

                Collection<BlackboardAttribute> bbattributes = newArrayList(
                        new BlackboardAttribute(
                                TSK_URL, PARENT_MODULE_NAME,
                                url),
                        new BlackboardAttribute(
                                TSK_DATETIME_ACCESSED, PARENT_MODULE_NAME,
                                Long.valueOf(result.get("lastModified").toString())), //NON-NLS
                        new BlackboardAttribute(
                                TSK_PROG_NAME, PARENT_MODULE_NAME,
                                getModuleName()));

                if (isIgnoredUrl(url) == false) {
                    bbattributes.add(new BlackboardAttribute(TSK_DOMAIN, PARENT_MODULE_NAME,
                            NetworkUtils.extractDomain(url)));
                }
                String target = result.get("target").toString(); //NON-NLS

                try {
                    String decodedTarget = URLDecoder.decode(target.replaceAll("file:///", ""), "UTF-8"); //NON-NLS
                    bbattributes.add(new BlackboardAttribute(
                            TSK_PATH, PARENT_MODULE_NAME,
                            decodedTarget));
                    long pathID = Util.findID(dataSource, decodedTarget);
                    if (pathID != -1) {
                        bbattributes.add(new BlackboardAttribute(
                                TSK_PATH_ID, PARENT_MODULE_NAME,
                                pathID));
                    } 
                } catch (UnsupportedEncodingException ex) {
                    logger.log(Level.SEVERE, "Error decoding Firefox download URL in " + temps, ex); //NON-NLS
                    errors++; 
                }

                try {
                    BlackboardArtifact bbart = downloadsFile.newArtifact(TSK_WEB_DOWNLOAD);
                    bbart.addAttributes(bbattributes);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to create Firefox download artifact.", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(Chrome.class, "Firefox.getDlV24.errMsg.errAnalyzeFile", //NON-NLS
                                    this.getModuleName(), downloadsFile.getName()));
                }
            }
            if (errors > 0) {
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Firefox.getDlV24.errMsg.errParsingArtifacts",
                        this.getModuleName(), errors));
            }
            index++;
            dbFile.delete();

        }

        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post Firefox download artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

    /**
 
     * Determine if the URL should be ignored.
     *
     * @param url The URL to test.
     *s
     * @return True if the URL should be ignored; otherwise false.
     */
    private boolean isIgnoredUrl(String url) {
        /*
         * Ignore blank URLS and URLs that begin with the matched text.
         */
        return StringUtils.isBlank(url)
               || url.toLowerCase().startsWith(PLACE_URL_PREFIX); 
    }
}
