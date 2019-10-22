/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2019 Basis Technology Corp.
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
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;


abstract class Extract {

    protected Case currentCase;
    protected SleuthkitCase tskCase;
    protected Blackboard blackboard;
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private final ArrayList<String> errorMessages = new ArrayList<>();
    String moduleName = "";
    boolean dataFound = false;

    Extract() {        
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
     * Generic method for creating a blackboard artifact with attributes
     *
     * @param type         is a blackboard.artifact_type enum to determine which
     *                     type the artifact should be
     * @param content      is the Content object that needs to have the
     *                     artifact added for it
     * @param bbattributes is the collection of blackboard attributes that need
     *                     to be added to the artifact after the artifact has
     *                     been created
     * @return The newly-created artifact, or null on error
     */
    protected BlackboardArtifact createArtifactWithAttributes(BlackboardArtifact.ARTIFACT_TYPE type, Content content, Collection<BlackboardAttribute> bbattributes) {
        try {
            BlackboardArtifact bbart = content.newArtifact(type);
            bbart.addAttributes(bbattributes);
            return bbart;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error while trying to add an artifact", ex); //NON-NLS
        }
        return null;
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
            Long creationTime, String name, String value, String programName, String domain) {

        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                RecentActivityExtracterModuleFactory.getModuleName(),
                (url != null) ? url : "")); //NON-NLS

        if (creationTime != null) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
                    RecentActivityExtracterModuleFactory.getModuleName(), creationTime));
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
     * @return Newly created copy of the AbstractFile
     * @throws IOException 
     */
    protected File createTemporaryFile(IngestJobContext context, AbstractFile file) throws IOException{
        Path tempFilePath = Paths.get(RAImageIngestModule.getRATempPath(
                getCurrentCase(), getName()), file.getName() + file.getId() + file.getNameExtension());
        java.io.File tempFile = tempFilePath.toFile();
        
        try {
            ContentUtils.writeToFile(file, tempFile, context::dataSourceIngestIsCancelled);
        } catch (IOException ex) {
            throw new IOException("Error writingToFile: " + file, ex); //NON-NLS
        }
         
        return tempFile;
    }
}
