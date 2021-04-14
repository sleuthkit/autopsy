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
import java.util.Optional;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.SQLiteDBConnect;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;


abstract class Extract {

    protected Case currentCase;
    protected SleuthkitCase tskCase;
    protected Blackboard blackboard;
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private final ArrayList<String> errorMessages = new ArrayList<>();
    private String moduleName = "";
    boolean dataFound = false;
    private RAOsAccountCache osAccountCache = null;

    Extract() {
        this("");
    }
    
    Extract(String moduleName) {
        this.moduleName = moduleName;
    }

    final void init() throws IngestModuleException {
        try {
            currentCase = Case.getCurrentCaseThrows();
            tskCase = currentCase.getSleuthkitCase();
            blackboard = tskCase.getBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.Extract_indexError_message(), ex);
        }
        configExtractor();
    }
    
    /**
     * Override to add any module-specific configuration
     * 
     * @throws IngestModuleException 
     */
    void configExtractor() throws IngestModuleException  {        
    }

    /**
     * Extractor process method intended to mirror the Ingest process method.
     * 
     *  Subclasses should overload just the abstract version of the method.
     * 
     * @param dataSource The data source object to ingest.
     * @param context   The the context for the current job.
     * @param progressBar A handle to the progressBar for the module to update with status.
     * @param osAccountCache The OsAccountCache.
     */
    void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar, RAOsAccountCache osAccountCache) {
        this.osAccountCache = osAccountCache;
        process(dataSource, context, progressBar);
    }
    
    abstract void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar);

    void complete() {
    }

    /**
     * Returns a List of string error messages from the inheriting class
     *
     * @return errorMessages returns all error messages logged
     */
    List<String> getErrorMessages() {
        return errorMessages;
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
     * @param file       The file the artifact originated from.
     * @param attributes A list of the attributes to associate with the
     *                   artifact.
     *
     * @return The newly created artifact.
     */
    BlackboardArtifact createArtifactWithAttributes(BlackboardArtifact.ARTIFACT_TYPE type, Content content, Collection<BlackboardAttribute> attributes) throws TskCoreException {
       return createArtifactWithAttributes(new BlackboardArtifact.Type(type), content, attributes);
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
        Optional<OsAccount> optional = getOsAccount(content);
        if (optional.isPresent() && type.getCategory() == BlackboardArtifact.Category.DATA_ARTIFACT) {
            return content.newDataArtifact(type, attributes, optional.get());
        } else {
            BlackboardArtifact bbart = content.newArtifact(type.getTypeID());
            bbart.addAttributes(attributes);
            return bbart;
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
        return createArtifactWithAttributes(TSK_ASSOCIATED_OBJECT, content, Collections.singletonList(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT,
                RecentActivityExtracterModuleFactory.getModuleName(), artifact.getArtifactID())));
    }
    
    /**
     * Method to post a blackboard artifact to the blackboard.
     *
     * @param bbart Blackboard artifact to be indexed. Nothing will occure if a null object is passed in.
     */
    @Messages({"Extract.indexError.message=Failed to index artifact for keyword search.",
               "Extract.noOpenCase.errMsg=No open case available."})
    void postArtifact(BlackboardArtifact bbart) {
        if(bbart == null) {
            return;
        }
        
        try {
            // index the artifact for keyword search
            blackboard.postArtifact(bbart, getName());
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bbart.getDisplayName(), ex); //NON-NLS
        }
    }
    
    /**
     * Method to post a list of BlackboardArtifacts to the blackboard.
     * 
     * @param artifacts A list of artifacts.  IF list is empty or null, the function will return.
     */
    void postArtifacts(Collection<BlackboardArtifact> artifacts) {
        if(artifacts == null || artifacts.isEmpty()) {
            return;
        }
        
        try{
            blackboard.postArtifacts(artifacts, getName());
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Unable to post blackboard artifacts", ex); //NON-NLS
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
        }
        finally {
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
     * Returns the name of the inheriting class
     *
     * @return Gets the moduleName set in the moduleName data member
     */
    protected String getName() {
        return moduleName;
    }
    
    protected String getRAModuleName() {
        return RecentActivityExtracterModuleFactory.getModuleName();
    }

    /**
     * Returns the state of foundData
     * @return 
     */
    public boolean foundData() {
        return dataFound;
    }
    
    /**
     * Sets the value of foundData
     * @param foundData 
     */
    protected void setFoundData(boolean foundData){
        dataFound = foundData;
    }
    
    /**
     * Returns the current case instance
     * @return Current case instance
     */
    protected Case getCurrentCase(){
        return this.currentCase;
    }
    
    /**
     * Creates a list of attributes for a history artifact.
     *
     * @param url 
     * @param accessTime Time url was accessed
     * @param referrer referred url
     * @param title title of the page
     * @param programName module name
     * @param domain domain of the url
     * @param user user that accessed url
     * @return List of BlackboardAttributes for giving attributes
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
     * @param url cookie url
     * @param creationTime cookie creation time 
     * @param name cookie name
     * @param value cookie value
     * @param programName Name of the module creating the attribute
     * @param domain Domain of the URL
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
        
        if(endTime != null && endTime != 0) {
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
     * @param url Bookmark url
     * @param title Title of the bookmarked page
     * @param creationTime Date & time at which the bookmark was created
     * @param programName Name of the module creating the attribute
     * @param domain The domain of the bookmark's url
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
     * @param url URL of the downloaded file
     * @param accessTime Time the download occurred
     * @param domain Domain of the URL
     * @param programName Name of the module creating the attribute
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
     * Create temporary file for the given AbstractFile.  The new file will be 
     * created in the temp directory for the module with a unique file name.
     * 
     * @param context
     * @param file
     * @param IngestJobId The ingest job id.
     * @return Newly created copy of the AbstractFile
     * @throws IOException 
     */
    protected File createTemporaryFile(IngestJobContext context, AbstractFile file, long ingestJobId) throws IOException{
        Path tempFilePath = Paths.get(RAImageIngestModule.getRATempPath(
                getCurrentCase(), getName(), ingestJobId), file.getName() + file.getId() + file.getNameExtension());
        java.io.File tempFile = tempFilePath.toFile();
        
        try {
            ContentUtils.writeToFile(file, tempFile, context::dataSourceIngestIsCancelled);
        } catch (IOException ex) {
            throw new IOException("Error writingToFile: " + file, ex); //NON-NLS
        }
         
        return tempFile;
    }
    
    /**
     * Return the appropriate OsAccount for the given file.
     * 
     * @param file
     * 
     * @return An Optional OsACcount object.
     * 
     * @throws TskCoreException 
     */
    Optional<OsAccount> getOsAccount(Content content) throws TskCoreException {
        if(content instanceof AbstractFile) {
            if(osAccountCache == null) {
                Optional<Long> accountId = ((AbstractFile)content).getOsAccountObjectId();
                if(accountId.isPresent()) {
                    return Optional.ofNullable(tskCase.getOsAccountManager().getOsAccountByObjectId(accountId.get()));
                }
                return Optional.empty();
            } 

            return osAccountCache.getOsAccount(((AbstractFile)content));
        }
        return Optional.empty();
    }
}
