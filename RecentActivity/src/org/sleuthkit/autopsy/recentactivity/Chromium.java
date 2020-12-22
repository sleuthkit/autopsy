/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
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

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import java.util.logging.Level;
import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.blackboardutils.WebBrowserArtifactsHelper;

/**
 * Chromium recent activity extraction
 */
class Chromium extends Extract {

    private static final String HISTORY_QUERY = "SELECT urls.url, urls.title, urls.visit_count, urls.typed_count, " //NON-NLS
            + "last_visit_time, urls.hidden, visits.visit_time, (SELECT urls.url FROM urls WHERE urls.id=visits.url) AS from_visit, visits.transition FROM urls, visits WHERE urls.id = visits.url"; //NON-NLS
    private static final String COOKIE_QUERY = "SELECT name, value, host_key, expires_utc,last_access_utc, creation_utc FROM cookies"; //NON-NLS
    private static final String DOWNLOAD_QUERY = "SELECT full_path, url, start_time, received_bytes FROM downloads"; //NON-NLS
    private static final String DOWNLOAD_QUERY_V30 = "SELECT current_path AS full_path, url, start_time, received_bytes FROM downloads, downloads_url_chains WHERE downloads.id=downloads_url_chains.id"; //NON-NLS
    private static final String LOGIN_QUERY = "SELECT origin_url, username_value, date_created, signon_realm from logins"; //NON-NLS
    private static final String AUTOFILL_QUERY = "SELECT name, value, count, date_created "
            + " FROM autofill, autofill_dates "
            + " WHERE autofill.pair_id = autofill_dates.pair_id"; //NON-NLS
    private static final String AUTOFILL_QUERY_V8X = "SELECT name, value, count, date_created, date_last_used from autofill"; //NON-NLS
    private static final String WEBFORM_ADDRESS_QUERY = "SELECT first_name, middle_name, last_name, address_line_1, address_line_2, city, state, zipcode, country_code, number, email, date_modified "
            + " FROM autofill_profiles, autofill_profile_names, autofill_profile_emails, autofill_profile_phones"
            + " WHERE autofill_profiles.guid = autofill_profile_names.guid AND autofill_profiles.guid = autofill_profile_emails.guid AND autofill_profiles.guid = autofill_profile_phones.guid";

    private static final String WEBFORM_ADDRESS_QUERY_V8X = "SELECT first_name, middle_name, last_name, full_name, street_address, city, state, zipcode, country_code, number, email, date_modified, use_date, use_count"
            + " FROM autofill_profiles, autofill_profile_names, autofill_profile_emails, autofill_profile_phones"
            + " WHERE autofill_profiles.guid = autofill_profile_names.guid AND autofill_profiles.guid = autofill_profile_emails.guid AND autofill_profiles.guid = autofill_profile_phones.guid";
    private static final String HISTORY_FILE_NAME = "History";
    private static final String BOOKMARK_FILE_NAME = "Bookmarks";
    private static final String COOKIE_FILE_NAME = "Cookies";
    private static final String LOGIN_DATA_FILE_NAME = "Login Data";
    private static final String WEB_DATA_FILE_NAME = "Web Data";
    private static final String UC_BROWSER_NAME = "UC Browser";

    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private Content dataSource;
    private IngestJobContext context;

    private static final Map<String, String> BROWSERS_MAP = ImmutableMap.<String, String>builder()
            .put("Microsoft Edge", "Microsoft/Edge/User Data/Default")
            .put("Yandex", "YandexBrowser/User Data/Default")
            .put("Opera", "Opera Software/Opera Stable")
            .put("SalamWeb", "SalamWeb/User Data/Default")
            .put("UC Browser", "UCBrowser/User Data%/Default")
            .put("Brave", "BraveSoftware/Brave-Browser/User Data/Default")
            .put("Google Chrome", "Chrome/User Data/Default")
            .build();

    @Messages({"# {0} - browserName",
        "Progress_Message_Chrome_History=Chrome History Browser {0}",
        "# {0} - browserName",
        "Progress_Message_Chrome_Bookmarks=Chrome Bookmarks Browser {0}",
        "# {0} - browserName",
        "Progress_Message_Chrome_Cookies=Chrome Cookies Browser {0}",
        "# {0} - browserName",
        "Progress_Message_Chrome_Downloads=Chrome Downloads Browser {0}",
        "Progress_Message_Chrome_FormHistory=Chrome Form History",
        "# {0} - browserName",
        "Progress_Message_Chrome_AutoFill=Chrome Auto Fill Browser {0}",
        "# {0} - browserName",
        "Progress_Message_Chrome_Logins=Chrome Logins Browser {0}",
        "Progress_Message_Chrome_Cache=Chrome Cache",})

    Chromium() {
        moduleName = NbBundle.getMessage(Chromium.class, "Chrome.moduleName");
    }

    @Override
    public void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;

        for (Map.Entry<String, String> browser : BROWSERS_MAP.entrySet()) {
            String browserName = browser.getKey();
            String browserLocation = browser.getValue();
            progressBar.progress(NbBundle.getMessage(this.getClass(), "Progress_Message_Chrome_History", browserName));
            this.getHistory(browser.getKey(), browser.getValue());
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            progressBar.progress(NbBundle.getMessage(this.getClass(), "Progress_Message_Chrome_Bookmarks", browserName));
            this.getBookmark(browser.getKey(), browser.getValue());
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            progressBar.progress(NbBundle.getMessage(this.getClass(), "Progress_Message_Chrome_Cookies", browserName));
            this.getCookie(browser.getKey(), browser.getValue());
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            progressBar.progress(NbBundle.getMessage(this.getClass(), "Progress_Message_Chrome_Logins", browserName));
            this.getLogins(browser.getKey(), browser.getValue());
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            progressBar.progress(NbBundle.getMessage(this.getClass(), "Progress_Message_Chrome_AutoFill", browserName));
            this.getAutofill(browser.getKey(), browser.getValue());
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            progressBar.progress(NbBundle.getMessage(this.getClass(), "Progress_Message_Chrome_Downloads", browserName));
            this.getDownload(browser.getKey(), browser.getValue());
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }
        }

        progressBar.progress(Bundle.Progress_Message_Chrome_Cache());
        ChromeCacheExtractor chromeCacheExtractor = new ChromeCacheExtractor(dataSource, context, progressBar);
        chromeCacheExtractor.processCaches();

    }

    /**
     * Query for history databases and add artifacts
     */
    private void getHistory(String browser, String browserLocation) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> historyFiles;
        String historyFileName = HISTORY_FILE_NAME;
        if (browser.equals(UC_BROWSER_NAME)) {
            historyFileName = HISTORY_FILE_NAME + "%";
        }
        try {
            historyFiles = fileManager.findFiles(dataSource, historyFileName, browserLocation); //NON-NLS
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
        while (j < allocatedHistoryFiles.size()) {
            String temps = RAImageIngestModule.getRATempPath(currentCase, browser) + File.separator + allocatedHistoryFiles.get(j).getName() + j + ".db"; //NON-NLS
            final AbstractFile historyFile = allocatedHistoryFiles.get(j++);
            if ((historyFile.getSize() == 0) || (historyFile.getName().toLowerCase().contains("-slack"))
                    || (historyFile.getName().toLowerCase().contains("cache")) || (historyFile.getName().toLowerCase().contains("media"))
                    || (historyFile.getName().toLowerCase().contains("index"))) {
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
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("url").toString() != null) ? result.get("url").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        (Long.valueOf(result.get("last_visit_time").toString()) / 1000000) - Long.valueOf("11644473600"))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("from_visit").toString() != null) ? result.get("from_visit").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("title").toString() != null) ? result.get("title").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(), browser));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        (NetworkUtils.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : "")))); //NON-NLS

                BlackboardArtifact bbart = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_WEB_HISTORY, historyFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }
            dbFile.delete();
        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Search for bookmark files and make artifacts.
     */
    private void getBookmark(String browser, String browserLocation) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> bookmarkFiles;
        String bookmarkFileName = BOOKMARK_FILE_NAME;
        if (browser.equals(UC_BROWSER_NAME)) {
            bookmarkFileName = BOOKMARK_FILE_NAME + "%";
        }
        try {
            bookmarkFiles = fileManager.findFiles(dataSource, bookmarkFileName, browserLocation); //NON-NLS
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
            if ((bookmarkFile.getSize() == 0) || (bookmarkFile.getName().toLowerCase().contains("-slack"))
                    || (bookmarkFile.getName().toLowerCase().contains("extras")) || (bookmarkFile.getName().toLowerCase().contains("log"))
                    || (bookmarkFile.getName().toLowerCase().contains("backup")) || (bookmarkFile.getName().toLowerCase().contains("visualized"))
                    || (bookmarkFile.getName().toLowerCase().contains("bak")) || (bookmarkFile.getParentPath().toLowerCase().contains("backup"))) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, browser) + File.separator + bookmarkFile.getName() + j + ".db"; //NON-NLS
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
                logger.log(Level.WARNING, "Error while trying to read into the Bookmarks for Chrome.", ex); //NON-NLS
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
                String domain = NetworkUtils.extractDomain(url);
                try {
                    BlackboardArtifact bbart = bookmarkFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                    //TODO Revisit usage of deprecated constructor as per TSK-583
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                            RecentActivityExtracterModuleFactory.getModuleName(), url));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE,
                            RecentActivityExtracterModuleFactory.getModuleName(), name));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                            RecentActivityExtracterModuleFactory.getModuleName(), (date / 1000000) - Long.valueOf("11644473600")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                            RecentActivityExtracterModuleFactory.getModuleName(), browser));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                            RecentActivityExtracterModuleFactory.getModuleName(), domain));
                    bbart.addAttributes(bbattributes);

                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error while trying to insert Chrome bookmark artifact{0}", ex); //NON-NLS
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "Chrome.getBookmark.errMsg.errAnalyzingFile4",
                                    this.getName(), bookmarkFile.getName()));
                }
            }
            postArtifacts(bbartifacts);
            bbartifacts.clear();
            dbFile.delete();
        }
    }

    /**
     * Queries for cookie files and adds artifacts
     */
    private void getCookie(String browser, String browserLocation) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles;
        String cookieFileName = COOKIE_FILE_NAME;
        if (browser.equals(UC_BROWSER_NAME)) {
            // Wildcard on front and back of Cookies are there for Cookie files that start with something else
            // ie: UC browser has "Extension Cookies.9" as well as Cookies.9
            cookieFileName = "%" + COOKIE_FILE_NAME + "%";
        }
        try {
            cookiesFiles = fileManager.findFiles(dataSource, cookieFileName, browserLocation); //NON-NLS
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
            if ((cookiesFile.getSize() == 0) || (cookiesFile.getName().toLowerCase().contains("-slack"))) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, browser) + File.separator + cookiesFile.getName() + j + ".db"; //NON-NLS
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
            logger.log(Level.INFO, "{0}- Now getting cookies from {1} with {2} artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("host_key").toString() != null) ? result.get("host_key").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        (Long.valueOf(result.get("last_access_utc").toString()) / 1000000) - Long.valueOf("11644473600"))); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("name").toString() != null) ? result.get("name").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("value").toString() != null) ? result.get("value").toString() : ""))); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(), browser));
                String domain = result.get("host_key").toString(); //NON-NLS
                domain = domain.replaceFirst("^\\.+(?!$)", "");
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        RecentActivityExtracterModuleFactory.getModuleName(), domain));

                BlackboardArtifact bbart = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }

            dbFile.delete();
        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Queries for download files and adds artifacts
     */
    private void getDownload(String browser, String browserLocation) {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> downloadFiles;
        String historyFileName = HISTORY_FILE_NAME;
        if (browser.equals(UC_BROWSER_NAME)) {
            historyFileName = HISTORY_FILE_NAME + "%";
        }
        try {
            downloadFiles = fileManager.findFiles(dataSource, historyFileName, browserLocation); //NON-NLS
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
            if ((downloadFile.getSize() == 0) || (downloadFile.getName().toLowerCase().contains("-slack"))
                    || (downloadFile.getName().toLowerCase().contains("cache")) || (downloadFile.getName().toLowerCase().contains("index"))) {
                continue;
            }

            String temps = RAImageIngestModule.getRATempPath(currentCase, browser) + File.separator + downloadFile.getName() + j + ".db"; //NON-NLS
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

            logger.log(Level.INFO, "{0}- Now getting downloads from {1} with {2} artifacts identified.", new Object[]{moduleName, temps, tempList.size()}); //NON-NLS
            for (HashMap<String, Object> result : tempList) {
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                String fullPath = result.get("full_path").toString(); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                        RecentActivityExtracterModuleFactory.getModuleName(), fullPath));
                long pathID = Util.findID(dataSource, fullPath);
                if (pathID != -1) {
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID,
                            NbBundle.getMessage(this.getClass(),
                                    "Chrome.parentModuleName"), pathID));
                }
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("url").toString() != null) ? result.get("url").toString() : ""))); //NON-NLS
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "Recent Activity", ((result.get("url").toString() != null) ? EscapeUtil.decodeURL(result.get("url").toString()) : "")));
                Long time = (Long.valueOf(result.get("start_time").toString()) / 1000000) - Long.valueOf("11644473600"); //NON-NLS

                //TODO Revisit usage of deprecated constructor as per TSK-583
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "Recent Activity", "Last Visited", time));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        RecentActivityExtracterModuleFactory.getModuleName(), time));
                String domain = NetworkUtils.extractDomain((result.get("url").toString() != null) ? result.get("url").toString() : ""); //NON-NLS
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        RecentActivityExtracterModuleFactory.getModuleName(), domain));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(), browser));

                BlackboardArtifact webDownloadArtifact = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, downloadFile, bbattributes);
                if (webDownloadArtifact != null) {
                    bbartifacts.add(webDownloadArtifact);

                    // find the downloaded file and create a TSK_ASSOCIATED_OBJECT for it, associating it with the TSK_WEB_DOWNLOAD artifact.
                    try {
                        String normalizedFullPath = FilenameUtils.normalize(fullPath, true);
                        for (AbstractFile downloadedFile : fileManager.findFiles(dataSource, FilenameUtils.getName(normalizedFullPath), FilenameUtils.getPath(normalizedFullPath))) {
                            BlackboardArtifact associatedObjectArtifact = downloadedFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT);
                            associatedObjectArtifact.addAttribute(
                                    new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT,
                                            RecentActivityExtracterModuleFactory.getModuleName(), webDownloadArtifact.getArtifactID()));

                            bbartifacts.add(associatedObjectArtifact);
                            break;
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, String.format("Error creating associated object artifact for file  '%s'", fullPath), ex); //NON-NLS
                    }
                }
            }

            dbFile.delete();
        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Gets user logins from Login Data sqlite database
     */
    private void getLogins(String browser, String browserLocation) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> loginDataFiles;
        String loginDataFileName = LOGIN_DATA_FILE_NAME;
        if (browser.equals(UC_BROWSER_NAME)) {
            loginDataFileName = LOGIN_DATA_FILE_NAME + "%";
        }

        try {
            loginDataFiles = fileManager.findFiles(dataSource, loginDataFileName, browserLocation); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (loginDataFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome Login Data files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        while (j < loginDataFiles.size()) {
            AbstractFile loginDataFile = loginDataFiles.get(j++);
            if ((loginDataFile.getSize() == 0) || (loginDataFile.getName().toLowerCase().contains("-slack"))) {
                continue;
            }
            String temps = RAImageIngestModule.getRATempPath(currentCase, browser) + File.separator + loginDataFile.getName() + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(loginDataFile, new File(temps), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome login artifacts file '%s' (id=%d).",
                        loginDataFile.getName(), loginDataFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errAnalyzingFiles",
                        this.getName(), loginDataFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome login artifacts file '%s' (id=%d).",
                        temps, loginDataFile.getName(), loginDataFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errAnalyzingFiles",
                        this.getName(), loginDataFile.getName()));
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
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("origin_url").toString() != null) ? result.get("origin_url").toString() : ""))); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        (Long.valueOf(result.get("date_created").toString()) / 1000000) - Long.valueOf("11644473600"))); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        (NetworkUtils.extractDomain((result.get("origin_url").toString() != null) ? result.get("origin_url").toString() : "")))); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("username_value").toString() != null) ? result.get("username_value").toString().replaceAll("'", "''") : ""))); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REALM,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        ((result.get("signon_realm") != null && result.get("signon_realm").toString() != null) ? result.get("signon_realm").toString() : ""))); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        result.containsKey("signon_realm") ? NetworkUtils.extractDomain(result.get("signon_realm").toString()) : "")); //NON-NLS

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        RecentActivityExtracterModuleFactory.getModuleName(), browser));

                BlackboardArtifact bbart = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT, loginDataFile, bbattributes);
                if (bbart != null) {
                    bbartifacts.add(bbart);
                }
            }

            dbFile.delete();
        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Gets and parses Autofill data from 'Web Data' database, and creates
     * TSK_WEB_FORM_AUTOFILL, TSK_WEB_FORM_ADDRESS artifacts
     */
    private void getAutofill(String browser, String browserLocation) {

        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> webDataFiles;
        String webDataFileName = WEB_DATA_FILE_NAME;
        if (browser.equals(UC_BROWSER_NAME)) {
            webDataFileName = WEB_DATA_FILE_NAME + "%";
        }

        try {
            webDataFiles = fileManager.findFiles(dataSource, webDataFileName, browserLocation); //NON-NLS
        } catch (TskCoreException ex) {
            String msg = NbBundle.getMessage(this.getClass(), "Chrome.getAutofills.errMsg.errGettingFiles");
            logger.log(Level.SEVERE, msg, ex);
            this.addErrorMessage(this.getName() + ": " + msg);
            return;
        }

        if (webDataFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any Chrome Web Data files."); //NON-NLS
            return;
        }

        dataFound = true;
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        int j = 0;
        while (j < webDataFiles.size()) {
            AbstractFile webDataFile = webDataFiles.get(j++);
            if ((webDataFile.getSize() == 0) || (webDataFile.getName().toLowerCase().contains("-slack"))) {
                continue;
            }
            String tempFilePath = RAImageIngestModule.getRATempPath(currentCase, browser) + File.separator + webDataFile.getName() + j + ".db"; //NON-NLS
            try {
                ContentUtils.writeToFile(webDataFile, new File(tempFilePath), context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading Chrome Autofill artifacts file '%s' (id=%d).",
                        webDataFile.getName(), webDataFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getAutofill.errMsg.errAnalyzingFiles",
                        this.getName(), webDataFile.getName()));
                continue;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing temp sqlite db file '%s' for Chrome Web data file '%s' (id=%d).",
                        tempFilePath, webDataFile.getName(), webDataFile.getId()), ex); //NON-NLS
                this.addErrorMessage(NbBundle.getMessage(this.getClass(), "Chrome.getLogin.errMsg.errAnalyzingFiles",
                        this.getName(), webDataFile.getName()));
                continue;
            }
            File dbFile = new File(tempFilePath);
            if (context.dataSourceIngestIsCancelled()) {
                dbFile.delete();
                break;
            }

            // The DB schema is little different in schema version 8x vs older versions
            boolean isSchemaV8X = Util.checkColumn("date_created", "autofill", tempFilePath);

            // get form autofill artifacts
            bbartifacts.addAll(getFormAutofillArtifacts(webDataFile, tempFilePath, isSchemaV8X, browser));
            try {
                // get form address atifacts
                getFormAddressArtifacts(webDataFile, tempFilePath, isSchemaV8X);
            } catch (NoCurrentCaseException | TskCoreException | Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, String.format("Error adding artifacts to the case database "
                        + "for chrome file %s [objId=%d]", webDataFile.getName(), webDataFile.getId()), ex);
            }

            dbFile.delete();
        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    /**
     * Extracts and returns autofill artifacts from the given database file
     *
     * @param webDataFile - the database file in the data source
     * @param dbFilePath - path to a temporary file where the DB file is
     * extracted
     * @param isSchemaV8X - indicates of the DB schema version is 8X or greater
     *
     * @return collection of TSK_WEB_FORM_AUTOFILL artifacts
     */
    private Collection<BlackboardArtifact> getFormAutofillArtifacts(AbstractFile webDataFile, String dbFilePath, boolean isSchemaV8X, String browser) {

        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();

        // The DB Schema is little different in version 8x vs older versions
        String autoFillquery = (isSchemaV8X) ? AUTOFILL_QUERY_V8X
                : AUTOFILL_QUERY;

        List<HashMap<String, Object>> autofills = this.dbConnect(dbFilePath, autoFillquery);
        logger.log(Level.INFO, "{0}- Now getting Autofill information from {1} with {2}artifacts identified.", new Object[]{moduleName, dbFilePath, autofills.size()}); //NON-NLS
        for (HashMap<String, Object> result : autofills) {
            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

            // extract all common attributes
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME,
                    NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                    ((result.get("name").toString() != null) ? result.get("name").toString() : ""))); //NON-NLS

            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE,
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    ((result.get("value").toString() != null) ? result.get("value").toString() : ""))); //NON-NLS

            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT,
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    (Integer.valueOf(result.get("count").toString())))); //NON-NLS

            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    Long.valueOf(result.get("date_created").toString()))); //NON-NLS

            // get schema version specific attributes
            if (isSchemaV8X) {
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        Long.valueOf(result.get("date_last_used").toString()))); //NON-NLS
            }

            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME,
                    RecentActivityExtracterModuleFactory.getModuleName(), browser));

            // Add an artifact
            BlackboardArtifact bbart = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_WEB_FORM_AUTOFILL, webDataFile, bbattributes);
            if (bbart != null) {
                bbartifacts.add(bbart);
            }
        }

        // return all extracted artifacts
        return bbartifacts;
    }

    /**
     * Extracts and returns autofill form address artifacts from the given
     * database file
     *
     * @param webDataFile - the database file in the data source
     * @param dbFilePath - path to a temporary file where the DB file is
     * extracted
     * @param isSchemaV8X - indicates of the DB schema version is 8X or greater
     *
     * @return collection of TSK_WEB_FORM_ADDRESS artifacts
     */
    private void getFormAddressArtifacts(AbstractFile webDataFile, String dbFilePath, boolean isSchemaV8X) throws NoCurrentCaseException,
            TskCoreException, Blackboard.BlackboardException {

        String webformAddressQuery = (isSchemaV8X) ? WEBFORM_ADDRESS_QUERY_V8X
                : WEBFORM_ADDRESS_QUERY;

        // Helper to create web form address artifacts.
        WebBrowserArtifactsHelper helper = new WebBrowserArtifactsHelper(
                Case.getCurrentCaseThrows().getSleuthkitCase(),
                NbBundle.getMessage(this.getClass(), "Chrome.parentModuleName"),
                webDataFile
        );

        // Get Web form addresses
        List<HashMap<String, Object>> addresses = this.dbConnect(dbFilePath, webformAddressQuery);
        logger.log(Level.INFO, "{0}- Now getting Web form addresses from {1} with {2}artifacts identified.", new Object[]{moduleName, dbFilePath, addresses.size()}); //NON-NLS
        for (HashMap<String, Object> result : addresses) {

            // get name fields
            String first_name = result.get("first_name").toString() != null ? result.get("first_name").toString() : "";
            String middle_name = result.get("middle_name").toString() != null ? result.get("middle_name").toString() : "";
            String last_name = result.get("last_name").toString() != null ? result.get("last_name").toString() : "";

            // get email and phone
            String email_Addr = result.get("email").toString() != null ? result.get("email").toString() : "";
            String phone_number = result.get("number").toString() != null ? result.get("number").toString() : "";

            // Get the address fields
            String city = result.get("city").toString() != null ? result.get("city").toString() : "";
            String state = result.get("state").toString() != null ? result.get("state").toString() : "";
            String zipcode = result.get("zipcode").toString() != null ? result.get("zipcode").toString() : "";
            String country_code = result.get("country_code").toString() != null ? result.get("country_code").toString() : "";

            // schema version specific fields
            String full_name = "";
            String street_address = "";
            long date_modified = 0;
            int use_count = 0;
            long use_date = 0;

            if (isSchemaV8X) {
                full_name = result.get("full_name").toString() != null ? result.get("full_name").toString() : "";
                street_address = result.get("street_address").toString() != null ? result.get("street_address").toString() : "";
                date_modified = result.get("date_modified").toString() != null ? Long.valueOf(result.get("date_modified").toString()) : 0;
                use_count = result.get("use_count").toString() != null ? Integer.valueOf(result.get("use_count").toString()) : 0;
                use_date = result.get("use_date").toString() != null ? Long.valueOf(result.get("use_date").toString()) : 0;
            } else {
                String address_line_1 = result.get("address_line_1").toString() != null ? result.get("street_address").toString() : "";
                String address_line_2 = result.get("address_line_2").toString() != null ? result.get("address_line_2").toString() : "";
                street_address = String.join(" ", address_line_1, address_line_2);
            }

            // Create atrributes from extracted fields
            if (full_name == null || full_name.isEmpty()) {
                full_name = String.join(" ", first_name, middle_name, last_name);
            }

            String locationAddress = String.join(", ", street_address, city, state, zipcode, country_code);

            List<BlackboardAttribute> otherAttributes = new ArrayList<>();
            if (date_modified > 0) {
                otherAttributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED,
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        date_modified)); //NON-NLS
            }

            helper.addWebFormAddress(
                    full_name, email_Addr, phone_number,
                    locationAddress, 0, use_date,
                    use_count, otherAttributes);
        }
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
