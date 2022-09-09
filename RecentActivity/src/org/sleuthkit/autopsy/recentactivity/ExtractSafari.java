/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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

import com.dd.plist.NSArray;
import com.dd.plist.NSDate;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.recentactivity.BinaryCookieReader.Cookie;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.xml.sax.SAXException;

/**
 * Extract the bookmarks, cookies, downloads and history from Safari
 *
 */
final class ExtractSafari extends Extract {

    private final IngestServices services = IngestServices.getInstance();
    private final IngestJobContext context;

    // visit_time uses an epoch of Jan 1, 2001 thus the addition of 978307200
    private static final String HISTORY_QUERY = "SELECT url, title, visit_time + 978307200 as time FROM 'history_items' JOIN history_visits ON history_item = history_items.id;"; //NON-NLS

    private static final String HISTORY_FILE_NAME = "History.db"; //NON-NLS
    private static final String BOOKMARK_FILE_NAME = "Bookmarks.plist"; //NON-NLS
    private static final String DOWNLOAD_FILE_NAME = "Downloads.plist"; //NON-NLS
    private static final String COOKIE_FILE_NAME = "Cookies.binarycookies"; //NON-NLS
    private static final String COOKIE_FOLDER = "Cookies";
    private static final String SAFARI_FOLDER = "Safari";

    private static final String HEAD_URL = "url"; //NON-NLS
    private static final String HEAD_TITLE = "title"; //NON-NLS
    private static final String HEAD_TIME = "time"; //NON-NLS

    private static final String PLIST_KEY_CHILDREN = "Children"; //NON-NLS
    private static final String PLIST_KEY_URL = "URLString"; //NON-NLS
    private static final String PLIST_KEY_URI = "URIDictionary"; //NON-NLS
    private static final String PLIST_KEY_TITLE = "title"; //NON-NLS
    private static final String PLIST_KEY_DOWNLOAD_URL = "DownloadEntryURL"; //NON-NLS
    private static final String PLIST_KEY_DOWNLOAD_DATE = "DownloadEntryDateAddedKey"; //NON-NLS
    private static final String PLIST_KEY_DOWNLOAD_PATH = "DownloadEntryPath"; //NON-NLS
    private static final String PLIST_KEY_DOWNLOAD_HISTORY = "DownloadHistory"; //NON-NLS

    private static final Logger LOG = Logger.getLogger(ExtractSafari.class.getName());

    @Messages({
        "ExtractSafari_Module_Name=Safari Analyzer",
        "ExtractSafari_Error_Getting_History=An error occurred while processing Safari history files.",
        "ExtractSafari_Error_Parsing_Bookmark=An error occured while processing Safari Bookmark files",
        "ExtractSafari_Error_Parsing_Cookies=An error occured while processing Safari Cookies files",
        "Progress_Message_Safari_History=Safari History",
        "Progress_Message_Safari_Bookmarks=Safari Bookmarks",
        "Progress_Message_Safari_Cookies=Safari Cookies",
        "Progress_Message_Safari_Downloads=Safari Downloads",})

    ExtractSafari(IngestJobContext context) {
        super(Bundle.ExtractSafari_Module_Name(), context);
        this.context = context;
    }

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        setFoundData(false);

        progressBar.progress(Bundle.Progress_Message_Safari_Cookies());
        try {
            processHistoryDB(dataSource);

        } catch (IOException | TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractSafari_Error_Getting_History());
            LOG.log(Level.SEVERE, "Exception thrown while processing history file.", ex); //NON-NLS
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        progressBar.progress(Bundle.Progress_Message_Safari_Bookmarks());
        try {
            processBookmarkPList(dataSource);
        } catch (IOException | TskCoreException | SAXException | PropertyListFormatException | ParseException | ParserConfigurationException ex) {
            this.addErrorMessage(Bundle.ExtractSafari_Error_Parsing_Bookmark());
            LOG.log(Level.SEVERE, "Exception thrown while parsing Safari Bookmarks file.", ex); //NON-NLS
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        progressBar.progress(Bundle.Progress_Message_Safari_Downloads());
        try {
            processDownloadsPList(dataSource);
        } catch (IOException | TskCoreException | SAXException | PropertyListFormatException | ParseException | ParserConfigurationException ex) {
            this.addErrorMessage(Bundle.ExtractSafari_Error_Parsing_Bookmark());
            LOG.log(Level.SEVERE, "Exception thrown while parsing Safari Download.plist file.", ex); //NON-NLS
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        progressBar.progress(Bundle.Progress_Message_Safari_Cookies());
        try {
            processBinaryCookieFile(dataSource);
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractSafari_Error_Parsing_Cookies());
            LOG.log(Level.SEVERE, "Exception thrown while processing Safari cookies file.", ex); //NON-NLS
        }
    }

    /**
     * Finds the all of the history.db files in the case looping through them to
     * find all of the history artifacts.
     *
     * @throws TskCoreException
     * @throws IOException
     */
    private void processHistoryDB(Content dataSource) throws TskCoreException, IOException {
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        List<AbstractFile> historyFiles = fileManager.findFiles(dataSource, HISTORY_FILE_NAME, SAFARI_FOLDER);

        if (historyFiles == null || historyFiles.isEmpty()) {
            return;
        }

        setFoundData(true);

        for (AbstractFile historyFile : historyFiles) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            getHistory(historyFile);
        }
    }

    /**
     * Finds all Bookmark.plist files and looks for bookmark entries.
     *
     * @param dataSource
     *
     * throws TskCoreException
     *
     * @throws IOException
     * @throws SAXException
     * @throws PropertyListFormatException
     * @throws ParseException
     * @throws ParserConfigurationException
     */
    private void processBookmarkPList(Content dataSource) throws TskCoreException, IOException, SAXException, PropertyListFormatException, ParseException, ParserConfigurationException {
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        List<AbstractFile> files = fileManager.findFiles(dataSource, BOOKMARK_FILE_NAME, SAFARI_FOLDER);

        if (files == null || files.isEmpty()) {
            return;
        }

        setFoundData(true);

        for (AbstractFile file : files) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            getBookmarks(file);
        }
    }

    /**
     * Process the safari download.plist file.
     *
     * @param dataSource
     *
     * throws TskCoreException
     *
     * @throws IOException
     * @throws SAXException
     * @throws PropertyListFormatException
     * @throws ParseException
     * @throws ParserConfigurationException
     */
    private void processDownloadsPList(Content dataSource) throws TskCoreException, IOException, SAXException, PropertyListFormatException, ParseException, ParserConfigurationException {
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        List<AbstractFile> files = fileManager.findFiles(dataSource, DOWNLOAD_FILE_NAME, SAFARI_FOLDER);

        if (files == null || files.isEmpty()) {
            return;
        }

        setFoundData(true);

        for (AbstractFile file : files) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            getDownloads(dataSource, file);
        }
    }

    /**
     * Process the Safari Cookie file.
     *
     * @param dataSource
     *
     * throws TskCoreException
     *
     * @throws IOException
     */
    private void processBinaryCookieFile(Content dataSource) throws TskCoreException {
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        List<AbstractFile> files = fileManager.findFiles(dataSource, COOKIE_FILE_NAME, COOKIE_FOLDER);

        if (files == null || files.isEmpty()) {
            return;
        }

        setFoundData(true);

        for (AbstractFile file : files) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }
            try {
                getCookies(file);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, String.format("Failed to get cookies from file %s", Paths.get(file.getUniquePath(), file.getName()).toString()), ex);
            }
        }
    }

    /**
     * Creates a temporary copy of historyFile and creates a list of
     * BlackboardArtifacts for the history information in the file.
     *
     * @param historyFile AbstractFile version of the history file from the case
     *
     * @throws TskCoreException
     * @throws IOException
     */
    private void getHistory(AbstractFile historyFile) throws TskCoreException, IOException {
        if (historyFile.getSize() == 0) {
            return;
        }
        File tempHistoryFile = createTemporaryFile(historyFile);
        try {
            postArtifacts(getHistoryArtifacts(historyFile, tempHistoryFile.toPath()));
        } finally {
            tempHistoryFile.delete();
        }
    }

    /**
     * Creates a temporary bookmark file from the AbstractFile and creates
     * BlackboardArtifacts for the any bookmarks found.
     *
     * @param file AbstractFile from case
     *
     * @throws TskCoreException
     * @throws IOException
     * @throws SAXException
     * @throws PropertyListFormatException
     * @throws ParseException
     * @throws ParserConfigurationException
     */
    private void getBookmarks(AbstractFile file) throws TskCoreException, IOException, SAXException, PropertyListFormatException, ParseException, ParserConfigurationException {
        if (file.getSize() == 0) {
            return;
        }
        File tempFile = createTemporaryFile(file);
        try {
            postArtifacts(getBookmarkArtifacts(file, tempFile));
        } finally {
            tempFile.delete();
        }
    }

    /**
     * Creates a temporary downloads file from the AbstractFile and creates
     * BlackboardArtifacts for the any downloads found.
     *
     * @param file AbstractFile from case
     *
     * @throws TskCoreException
     * @throws IOException
     * @throws SAXException
     * @throws PropertyListFormatException
     * @throws ParseException
     * @throws ParserConfigurationException
     */
    private void getDownloads(Content dataSource, AbstractFile file) throws TskCoreException, IOException, SAXException, PropertyListFormatException, ParseException, ParserConfigurationException {
        if (file.getSize() == 0) {
            return;
        }
        File tempFile = createTemporaryFile(file);
        try {
            postArtifacts(getDownloadArtifacts(dataSource, file, tempFile));
        } finally {
            tempFile.delete();
        }
    }

    /**
     * Creates a temporary copy of the Cookie file and creates a list of cookie
     * BlackboardArtifacts.
     *
     * @param file Original Cookie file from the case
     *
     * @throws TskCoreException
     * @throws IOException
     */
    private void getCookies(AbstractFile file) throws TskCoreException, IOException {
        if (file.getSize() == 0) {
            return;
        }

        File tempFile = null;

        try {
            tempFile = createTemporaryFile(file);

            if (!context.dataSourceIngestIsCancelled()) {
                postArtifacts(getCookieArtifacts(file, tempFile));
            }

        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    /**
     * Queries the history db for the history information creating a list of
     * BlackBoardArtifact for each row returned from the db.
     *
     * @param origFile     AbstractFile of the history file from the case
     * @param tempFilePath Path to temporary copy of the history db
     *
     * @return Blackboard Artifacts for the history db or null if there are no
     *         history artifacts
     *
     * @throws TskCoreException
     */
    private Collection<BlackboardArtifact> getHistoryArtifacts(AbstractFile origFile, Path tempFilePath) throws TskCoreException {
        List<HashMap<String, Object>> historyList = this.querySQLiteDb(tempFilePath.toString(), HISTORY_QUERY);

        if (historyList == null || historyList.isEmpty()) {
            return null;
        }

        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        for (HashMap<String, Object> row : historyList) {
            if (context.dataSourceIngestIsCancelled()) {
                return bbartifacts;
            }

            String url = row.get(HEAD_URL).toString();
            String title = row.get(HEAD_TITLE).toString();
            Long time = (Double.valueOf(row.get(HEAD_TIME).toString())).longValue();

            bbartifacts.add(
                    createArtifactWithAttributes(
                            BlackboardArtifact.Type.TSK_WEB_HISTORY,
                            origFile,
                            createHistoryAttributes(url, time, null, title,
                                    this.getDisplayName(), NetworkUtils.extractDomain(url), null)));
        }

        return bbartifacts;
    }

    /**
     * Parses the temporary version of bookmarks.plist and creates
     *
     * @param origFile The origFile Bookmark.plist file from the case
     * @param tempFile The temporary local version of Bookmark.plist
     *
     * @return Collection of BlackboardArtifacts for the bookmarks in origFile
     *
     * @throws IOException
     * @throws PropertyListFormatException
     * @throws ParseException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws TskCoreException
     */
    private Collection<BlackboardArtifact> getBookmarkArtifacts(AbstractFile origFile, File tempFile) throws IOException, PropertyListFormatException, ParseException, ParserConfigurationException, SAXException, TskCoreException {
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();

        try {
            NSDictionary root = (NSDictionary) PropertyListParser.parse(tempFile);

            parseBookmarkDictionary(bbartifacts, origFile, root);
        } catch (PropertyListFormatException ex) {
            PropertyListFormatException plfe = new PropertyListFormatException(origFile.getName() + ": " + ex.getMessage());
            plfe.setStackTrace(ex.getStackTrace());
            throw plfe;
        } catch (ParseException ex) {
            ParseException pe = new ParseException(origFile.getName() + ": " + ex.getMessage(), ex.getErrorOffset());
            pe.setStackTrace(ex.getStackTrace());
            throw pe;
        } catch (ParserConfigurationException ex) {
            ParserConfigurationException pce = new ParserConfigurationException(origFile.getName() + ": " + ex.getMessage());
            pce.setStackTrace(ex.getStackTrace());
            throw pce;
        } catch (SAXException ex) {
            SAXException se = new SAXException(origFile.getName() + ": " + ex.getMessage());
            se.setStackTrace(ex.getStackTrace());
            throw se;
        }

        return bbartifacts;
    }

    /**
     * Finds the download entries in the tempFile and creates a list of
     * artifacts from them.
     *
     * @param origFile Download.plist file from case
     * @param tempFile Temporary copy of download.plist file
     *
     * @return Collection of BlackboardArtifacts for the downloads in origFile
     *
     * @throws IOException
     * @throws PropertyListFormatException
     * @throws ParseException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws TskCoreException
     */
    private Collection<BlackboardArtifact> getDownloadArtifacts(Content dataSource, AbstractFile origFile, File tempFile) throws IOException, PropertyListFormatException, ParseException, ParserConfigurationException, SAXException, TskCoreException {
        Collection<BlackboardArtifact> bbartifacts = null;

        try {
            while (true) {
                NSDictionary root = (NSDictionary) PropertyListParser.parse(tempFile);

                if (root == null) {
                    break;
                }

                NSArray nsArray = (NSArray) root.get(PLIST_KEY_DOWNLOAD_HISTORY);

                if (nsArray == null) {
                    break;
                }

                NSObject[] objectArray = nsArray.getArray();
                bbartifacts = new ArrayList<>();

                for (NSObject obj : objectArray) {
                    if (obj instanceof NSDictionary) {
                        bbartifacts.addAll(parseDownloadDictionary(dataSource, origFile, (NSDictionary) obj));
                    }
                }
                break;
            }

        } catch (PropertyListFormatException ex) {
            PropertyListFormatException plfe = new PropertyListFormatException(origFile.getName() + ": " + ex.getMessage());
            plfe.setStackTrace(ex.getStackTrace());
            throw plfe;
        } catch (ParseException ex) {
            ParseException pe = new ParseException(origFile.getName() + ": " + ex.getMessage(), ex.getErrorOffset());
            pe.setStackTrace(ex.getStackTrace());
            throw pe;
        } catch (ParserConfigurationException ex) {
            ParserConfigurationException pce = new ParserConfigurationException(origFile.getName() + ": " + ex.getMessage());
            pce.setStackTrace(ex.getStackTrace());
            throw pce;
        } catch (SAXException ex) {
            SAXException se = new SAXException(origFile.getName() + ": " + ex.getMessage());
            se.setStackTrace(ex.getStackTrace());
            throw se;
        }

        return bbartifacts;
    }

    /**
     * Finds the cookies in the tempFile creating a list of BlackboardArtifacts
     * each representing one cookie.
     *
     * @param origFile Original Cookies.binarycookie file from case
     * @param tempFile Temporary copy of the cookies file
     *
     * @return List of Blackboard Artifacts, one for each cookie
     *
     * @throws TskCoreException
     * @throws IOException
     */
    private Collection<BlackboardArtifact> getCookieArtifacts(AbstractFile origFile, File tempFile) throws TskCoreException, IOException {
        Collection<BlackboardArtifact> bbartifacts = null;
        BinaryCookieReader reader = BinaryCookieReader.initalizeReader(tempFile);

        if (reader != null) {
            bbartifacts = new ArrayList<>();

            Iterator<Cookie> iter = reader.iterator();
            while (iter.hasNext()) {
                if (context.dataSourceIngestIsCancelled()) {
                    return bbartifacts;
                }

                Cookie cookie = iter.next();

                bbartifacts.add(
                        createArtifactWithAttributes(
                                BlackboardArtifact.Type.TSK_WEB_COOKIE,
                                origFile,
                                createCookieAttributes(
                                        cookie.getURL(),
                                        cookie.getCreationDate(),
                                        null,
                                        cookie.getExpirationDate(),
                                        cookie.getName(), cookie.getValue(),
                                        this.getDisplayName(),
                                        NetworkUtils.extractDomain(cookie.getURL()))));
            }
        }

        return bbartifacts;
    }

    /**
     * Parses the plist object to find the bookmark child objects, then creates
     * an artifact with the bookmark information.
     *
     * @param bbartifacts BlackboardArtifact list to add new the artifacts to
     * @param origFile    The origFile Bookmark.plist file from the case
     * @param root        NSDictionary object to parse
     *
     * @throws TskCoreException
     */
    private void parseBookmarkDictionary(Collection<BlackboardArtifact> bbartifacts, AbstractFile origFile, NSDictionary root) throws TskCoreException {

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        if (root.containsKey(PLIST_KEY_CHILDREN)) {
            NSArray children = (NSArray) root.objectForKey(PLIST_KEY_CHILDREN);

            if (children != null) {
                for (NSObject obj : children.getArray()) {
                    parseBookmarkDictionary(bbartifacts, origFile, (NSDictionary) obj);
                }
            }
        } else if (root.containsKey(PLIST_KEY_URL)) {
            String url = null;
            String title = null;

            NSString nsstr = (NSString) root.objectForKey(PLIST_KEY_URL);
            if (nsstr != null) {
                url = nsstr.toString();
            }

            NSDictionary dic = (NSDictionary) root.get(PLIST_KEY_URI);

            nsstr = (NSString) root.objectForKey(PLIST_KEY_TITLE);

            if (nsstr != null) {
                title = ((NSString) dic.get(PLIST_KEY_TITLE)).toString();
            }

            if (url != null || title != null) {
                bbartifacts.add(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, origFile,
                        createBookmarkAttributes(url,
                                title,
                                null,
                                getDisplayName(),
                                NetworkUtils.extractDomain(url))));
            }
        }
    }

    /**
     * Parse the NSDictionary object that represents one download.
     *
     * @param origFile Download.plist file from the case
     * @param entry    One NSDictionary Object that represents one download
     *                 instance
     *
     * @return a Blackboard Artifact for the download.
     *
     * @throws TskCoreException
     */
    private Collection<BlackboardArtifact> parseDownloadDictionary(Content dataSource, AbstractFile origFile, NSDictionary entry) throws TskCoreException {
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        String url = null;
        String path = null;
        Long time = null;
        Long pathID = null;

        NSString nsstring = (NSString) entry.get(PLIST_KEY_DOWNLOAD_URL);
        if (nsstring != null) {
            url = nsstring.toString();
        }

        nsstring = (NSString) entry.get(PLIST_KEY_DOWNLOAD_PATH);
        if (nsstring != null) {
            path = nsstring.toString();
            pathID = Util.findID(dataSource, path);
        }

        NSDate date = (NSDate) entry.get(PLIST_KEY_DOWNLOAD_DATE);
        if (date != null) {
            time = date.getDate().getTime();
        }

        BlackboardArtifact webDownloadArtifact = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_DOWNLOAD, origFile, createDownloadAttributes(path, pathID, url, time, NetworkUtils.extractDomain(url), getDisplayName()));
        bbartifacts.add(webDownloadArtifact);

        // find the downloaded file and create a TSK_ASSOCIATED_OBJECT for it, associating it with the TSK_WEB_DOWNLOAD artifact.
        for (AbstractFile downloadedFile : currentCase.getSleuthkitCase().getFileManager().findFilesExactNameExactPath(dataSource,
                FilenameUtils.getName(path), FilenameUtils.getPath(path))) {
            bbartifacts.add(createAssociatedArtifact(downloadedFile, webDownloadArtifact));
            break;
        }

        return bbartifacts;
    }
}
