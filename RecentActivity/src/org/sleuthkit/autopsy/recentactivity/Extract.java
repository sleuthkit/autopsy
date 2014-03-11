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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.datamodel.*;

abstract class Extract extends IngestModuleDataSource{

    protected Case currentCase = Case.getCurrentCase(); // get the most updated case
    protected SleuthkitCase tskCase = currentCase.getSleuthkitCase();
    public final Logger logger = Logger.getLogger(this.getClass().getName());
    private final ArrayList<String> errorMessages = new ArrayList<>();
    String moduleName = "";
    boolean dataFound = false;
    
    //hide public constructor to prevent from instantiation by ingest module loader
    Extract() {
        dataFound = false;
    }
    
    /**
     * Returns a List of string error messages from the inheriting class
     * @return  errorMessages returns all error messages logged
     */
    List<String> getErrorMessages() {
        return errorMessages;
    }

    /**
     * Adds a string to the error message list
     *
     * @param  message is an error message represented as a string
     */
    protected void addErrorMessage(String message) {
        errorMessages.add(message);
    }
    

    /**
     * Generic method for adding a blackboard artifact to the blackboard
     *
     * @param type is a blackboard.artifact_type enum to determine which type
     * the artifact should be
     * @param content is the AbstractFile object that needs to have the artifact
     * added for it
     * @param bbattributes is the collection of blackboard attributes that need
     * to be added to the artifact after the artifact has been created
     */
    public void addArtifact(BlackboardArtifact.ARTIFACT_TYPE type, AbstractFile content, Collection<BlackboardAttribute> bbattributes) {

        try {
            BlackboardArtifact bbart = content.newArtifact(type);
            bbart.addAttributes(bbattributes);
        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Error while trying to add an artifact: " + ex);
        }
    }

     /**
     * Returns a List from a result set based on sql query.
     * This is used to query sqlite databases storing user recent activity data, such as in firefox sqlite db
     *
     * @param  path is the string path to the sqlite db file
     * @param  query is a sql string query that is to be run
     * @return  list is the ArrayList that contains the resultset information in it that the query obtained
     */
    public List<HashMap<String,Object>> dbConnect(String path, String query) {
        ResultSet temprs = null;
        List<HashMap<String,Object>> list = new ArrayList<HashMap<String,Object>>();
        String connectionString = "jdbc:sqlite:" + path;
        try {
            SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", connectionString);
            temprs = tempdbconnect.executeQry(query);
            list = this.resultSetToArrayList(temprs);
            tempdbconnect.closeConnection();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error while trying to read into a sqlite db." + connectionString, ex);
            errorMessages.add(NbBundle.getMessage(this.getClass(), "Extract.dbConn.errMsg.failedToQueryDb", getName()));
            return Collections.<HashMap<String,Object>>emptyList();
        }
        return list;
    }

        /**
     * Returns a List of AbstractFile objects from TSK based on sql query.
     *
     * @param  rs is the resultset that needs to be converted to an arraylist
     * @return  list returns the arraylist built from the converted resultset
     */
    private List<HashMap<String,Object>> resultSetToArrayList(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<HashMap<String,Object>> list = new ArrayList<HashMap<String,Object>>(50);
        while (rs.next()) {
            HashMap<String,Object> row = new HashMap<String,Object>(columns);
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
     * @return  Gets the moduleName set in the moduleName data member
     */
    public String getName() {
        return moduleName;
    }
    
    public boolean foundData() {
        return dataFound;
    }
}