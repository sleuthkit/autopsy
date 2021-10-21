/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.SQLiteDBConnect;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

abstract class Extract {

    protected final Case currentCase;
    protected final SleuthkitCase tskCase;
    private static final Logger logger = Logger.getLogger(Extract.class.getName());
    private final ArrayList<String> errorMessages = new ArrayList<>();
    private final String displayName;
    private final IngestJobContext context;
    protected boolean dataFound = false;

    /**
     * Constructs the super class part of an extractor used by the Recent
     * Activity ingest module.
     *
     * @param displayName The display name of the extractor.
     * @param context     The ingest job context.
     */
    Extract(String displayName, IngestJobContext context) {
        this.displayName = displayName;
        this.context = context;
        currentCase = Case.getCurrentCase();
        tskCase = currentCase.getSleuthkitCase();
    }

    /**
     * Configures this extractor. Called by the by the Recent Activity ingest
     * module in its startUp() method.
     *
     * @throws IngestModuleException The exception is thrown if there is an
     *                               error configuring the extractor.
     */
    void configExtractor() throws IngestModuleException {
        logger.info(String.format("%s configured for Recent Activity ingest module", displayName)); //NON-NLS        
    }

    /**
     * Analyzes the given data source. Called by the by the Recent Activity
     * ingest module in its startUp() method.
     *
     * @param dataSource  The data source to be analyzed.
     * @param progressBar A progress object that can be used to report analysis
     *                    progress.
     */
    abstract void process(Content dataSource, DataSourceIngestModuleProgress progressBar);

    /**
     * Cleans up this extractor. Called by the Recent Activity ingest module in
     * its shutDown() method.
     */
    void cleanUp() {
        logger.info(String.format("%s processing completed for Recent Activity ingest module", displayName)); //NON-NLS        
    }

    /**
     * Returns a List of string error messages from the inheriting class
     *
     * @return errorMessages returns all error messages logged
     */
    List<String> getErrorMessages() {
        return Collections.unmodifiableList(errorMessages);
    }

    /**
     * Adds a string to the error message list
     *
     * @param message is an error message represented as a string
     */
    protected void addErrorMessage(String message) {
        errorMessages.add(message);
    }

    /**
     * Generic method for creating artifacts.
     *
     * @param type       The type of artifact.
     * @param content    The file the artifact originated from.
     * @param attributes A list of the attributes to associate with the
     *                   artifact.
     *
     * @return The newly created artifact.
     *
     * @throws TskCoreException
     */
    BlackboardArtifact createArtifactWithAttributes(BlackboardArtifact.Type type, Content content, Collection<BlackboardAttribute> attributes) throws TskCoreException {
        if (type.getCategory() == BlackboardArtifact.Category.DATA_ARTIFACT) {
                return content.newDataArtifact(type, attributes);            
        } else if (type.getCategory() == BlackboardArtifact.Category.ANALYSIS_RESULT) {
                return content.newAnalysisResult(type, Score.SCORE_UNKNOWN, null, null, null, attributes).getAnalysisResult();            
        } else {
                throw new TskCoreException("Unknown category type: " + type.getCategory().getDisplayName());            
        }
    }

    /**
     * Returns and associated artifact for the given artifact.
     *
     * @param content  The content to create the artifact from.
     * @param artifact The artifact to associate the new artifact with.
     *
     * @return The newly created artifact.
     *
     * @throws TskCoreException
     */
    BlackboardArtifact createAssociatedArtifact(Content content, BlackboardArtifact artifact) throws TskCoreException {
        BlackboardAttribute attribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, getRAModuleName(), artifact.getArtifactID());
        return createArtifactWithAttributes(BlackboardArtifact.Type.TSK_ASSOCIATED_OBJECT, content, Collections.singletonList(attribute));
    }

    /**
     * Posts an artifact to the blackboard.
     *
     * @param artifact The artifact.
     */
    void postArtifact(BlackboardArtifact artifact) {
        if (artifact != null && !context.dataArtifactIngestIsCancelled()) {
            postArtifacts(Collections.singleton(artifact));
        }
    }

    /**
     * Posts a collection of artifacts to the blackboard.
     *
     * @param artifacts The artifacts.
     */
    void postArtifacts(Collection<BlackboardArtifact> artifacts) {
        if (artifacts != null && !artifacts.isEmpty() && !context.dataArtifactIngestIsCancelled()) {
            try {
                tskCase.getBlackboard().postArtifacts(artifacts, RecentActivityExtracterModuleFactory.getModuleName(), context.getJobId());
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Failed to post artifacts", ex); //NON-NLS
            }
        }
    }

    /**
     * Returns a List from a result set based on sql query. This is used to
     * query sqlite databases storing user recent activity data, such as in
     * firefox sqlite db
     *
     * @param path  is the string path to the sqlite db file
     * @param query is a sql string query that is to be run
     *
     * @return list is the ArrayList that contains the resultset information in
     *         it that the query obtained
     */
    protected List<HashMap<String, Object>> dbConnect(String path, String query) {
        ResultSet temprs;
        List<HashMap<String, Object>> list;
        String connectionString = "jdbc:sqlite:" + path; //NON-NLS
        SQLiteDBConnect tempdbconnect = null;
        try {
            tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", connectionString); //NON-NLS
            temprs = tempdbconnect.executeQry(query);
            list = this.resultSetToArrayList(temprs);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex); //NON-NLS
            return Collections.<HashMap<String, Object>>emptyList();
        } finally {
            if (tempdbconnect != null) {
                tempdbconnect.closeConnection();
            }
        }
        return list;
    }

    /**
     * Returns a List of AbstractFile objects from TSK based on sql query.
     *
     * @param rs is the resultset that needs to be converted to an arraylist
     *
     * @return list returns the arraylist built from the converted resultset
     */
    private List<HashMap<String, Object>> resultSetToArrayList(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<HashMap<String, Object>> list = new ArrayList<>(50);
        while (rs.next()) {
            HashMap<String, Object> row = new HashMap<>(columns);
            for (int i = 1; i <= columns; ++i) {
                if (rs.getObject(i) == null) {
                    row.put(md.getColumnName(i), "");
                } else {
                    row.put(md.getColumnName(i), rs.getObject(i));
                }
            }
            list.add(row);
        }

        return list;
    }

    /**
     * Gets the display name of this extractor.
     *
     * @return The display name.
     */
    protected String getDisplayName() {
        return displayName;
    }

    protected String getRAModuleName() {
        return RecentActivityExtracterModuleFactory.getModuleName();
    }

    /**
     * Returns the state of foundData
     *
     * @return
     */
    public boolean foundData() {
        return dataFound;
    }

    /**
     * Sets the value of foundData
     *
     * @param foundData
     */
    protected void setFoundData(boolean foundData) {
        dataFound = foundData;
    }

    /**
     * Returns the current case instance
     *
     * @return Current case instance
     */
    protected Case getCurrentCase() {
        return this.currentCase;
    }

    /**
     * Creates a list of attributes for a history artifact.
     *
     * @param url
     * @param accessTime  Time url was accessed
     * @param referrer    referred url
     * @param title       title of the page
     * @param programName module name
     * @param domain      domain of the url
     * @param user        user that accessed url
     *
     * @return List of BlackboardAttributes for giving attributes
     *
     * @throws TskCoreException
     */
    protected Collection<BlackboardAttribute> createHistoryAttribute(String url, Long accessTime,
            String referrer, String title, String programName, String domain, String user) throws TskCoreException {

        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (url != null) ? url : "")); //NON-NLS

        if (accessTime != null) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                    RecentActivityExtracterModuleFactory.getModuleName(), accessTime));
        }

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (referrer != null) ? referrer : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (title != null) ? title : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (programName != null) ? programName : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (domain != null) ? domain : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (user != null) ? user : "")); //NON-NLS

        return bbattributes;
    }

    /**
     * Creates a list of attributes for a cookie.
     *
     * @param url          cookie url
     * @param creationTime cookie creation time
     * @param name         cookie name
     * @param value        cookie value
     * @param programName  Name of the module creating the attribute
     * @param domain       Domain of the URL
     *
     * @return List of BlackboarAttributes for the passed in attributes
     */
    protected Collection<BlackboardAttribute> createCookieAttributes(String url,
            Long creationTime, Long accessTime, Long endTime, String name, String value, String programName, String domain) {

        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (url != null) ? url : "")); //NON-NLS

        if (creationTime != null && creationTime != 0) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                    RecentActivityExtracterModuleFactory.getModuleName(), creationTime));
        }

        if (accessTime != null && accessTime != 0) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                    RecentActivityExtracterModuleFactory.getModuleName(), accessTime));
        }

        if (endTime != null && endTime != 0) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END,
                    RecentActivityExtracterModuleFactory.getModuleName(), endTime));
        }

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (name != null) ? name : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (value != null) ? value : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (programName != null) ? programName : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (domain != null) ? domain : "")); //NON-NLS

        return bbattributes;
    }

    /**
     * Creates a list of bookmark attributes from the passed in parameters.
     *
     * @param url          Bookmark url
     * @param title        Title of the bookmarked page
     * @param creationTime Date & time at which the bookmark was created
     * @param programName  Name of the module creating the attribute
     * @param domain       The domain of the bookmark's url
     *
     * @return A collection of bookmark attributes
     */
    protected Collection<BlackboardAttribute> createBookmarkAttributes(String url, String title, Long creationTime, String programName, String domain) {
        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (url != null) ? url : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (title != null) ? title : "")); //NON-NLS

        if (creationTime != null) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                    RecentActivityExtracterModuleFactory.getModuleName(), creationTime));
        }

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (programName != null) ? programName : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (domain != null) ? domain : "")); //NON-NLS

        return bbattributes;
    }

    /**
     * Creates a list of the attributes of a downloaded file
     *
     * @param path
     * @param url         URL of the downloaded file
     * @param accessTime  Time the download occurred
     * @param domain      Domain of the URL
     * @param programName Name of the module creating the attribute
     *
     * @return A collection of attributes of a downloaded file
     */
    protected Collection<BlackboardAttribute> createDownloadAttributes(String path, Long pathID, String url, Long accessTime, String domain, String programName) {
        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (path != null) ? path : "")); //NON-NLS

        if (pathID != null && pathID != -1) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID,
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    pathID));
        }

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (url != null) ? url : "")); //NON-NLS

        if (accessTime != null) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                    RecentActivityExtracterModuleFactory.getModuleName(), accessTime));
        }

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (domain != null) ? domain : "")); //NON-NLS

        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (programName != null) ? programName : "")); //NON-NLS

        return bbattributes;
    }

    /**
     * Creates a list of the attributes for source of a downloaded file
     *
     * @param url source URL of the downloaded file
     *
     * @return A collection of attributes for source of a downloaded file
     */
    protected Collection<BlackboardAttribute> createDownloadSourceAttributes(String url) {
        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (url != null) ? url : "")); //NON-NLS
        return bbattributes;
    }

    /**
     * Writes a file to disk in this extractor's dedicated temp directory within
     * the Recent Activity ingest modules temp directory. The object ID of the
     * file is appended to the file name for uniqueness.
     *
     * @param file The file.
     *
     * @return A File object that represents the file on disk.
     *
     * @throws IOException Exception thrown if there is a problem writing the
     *                     file to disk.
     */
    protected File createTemporaryFile(AbstractFile file) throws IOException {
        Path tempFilePath = Paths.get(RAImageIngestModule.getRATempPath(getCurrentCase(), getDisplayName(), context.getJobId()), file.getName() + file.getId() + file.getNameExtension());
        java.io.File tempFile = tempFilePath.toFile();
        ContentUtils.writeToFile(file, tempFile, context::dataSourceIngestIsCancelled);
        return tempFile;
    }

}
