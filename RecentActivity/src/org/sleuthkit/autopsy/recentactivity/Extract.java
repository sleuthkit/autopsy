 /*
 *
 * Autopsy Forensic Browser
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
import java.sql.Statement;
import java.util.*;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestModuleImage;
import org.sleuthkit.datamodel.*;

abstract public class Extract implements IngestModuleImage{

    protected Case currentCase = Case.getCurrentCase(); // get the most updated case
    protected SleuthkitCase tskCase = currentCase.getSleuthkitCase();
    public final Logger logger = Logger.getLogger(this.getClass().getName());
    protected ArrayList<String> errorMessages = null;
    protected String moduleName = "";
    
    List<String> getErrorMessages() {
        if(errorMessages == null) {
            errorMessages = new ArrayList<String>();
        }
        return errorMessages;
    }

    /**
     * Returns a List of FsContent objects from TSK based on sql query.
     *
     * @param  image is a Image object that denotes which image to get the files from
     * @param  query is a sql string query that is to be run
     * @return  FFSqlitedb is a List of FsContent objects
     */
    public List<FsContent> extractFiles(Image image, String query) {

        Collection<FileSystem> imageFS = tskCase.getFileSystems(image);
        List<String> fsIds = new LinkedList<String>();
        for (FileSystem img : imageFS) {
            Long tempID = img.getId();
            fsIds.add(tempID.toString());
        }

        String allFS = new String();
        for (int i = 0; i < fsIds.size(); i++) {
            if (i == 0) {
                allFS += " AND (0";
            }
            allFS += " OR fs_obj_id = '" + fsIds.get(i) + "'";
            if (i == fsIds.size() - 1) {
                allFS += ")";
            }
        }
        List<FsContent> FFSqlitedb = null;
        try {
            ResultSet rs = tskCase.runQuery(query + allFS);
            FFSqlitedb = tskCase.resultSetToFsContents(rs);
            Statement s = rs.getStatement();
            rs.close();
            if (s != null) {
                s.close();
            }
            rs.close();
            rs.getStatement().close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Error while trying to extract files for:" + this.getClass().getName(), ex);
            this.addErrorMessage(this.getName() + ": Error while trying to extract files to analyze.");
        }
        return FFSqlitedb;
    }

        /**
     *  Generic method for adding a blackboard artifact to the blackboard
     *
     * @param  type is a blackboard.artifact_type enum to determine which type the artifact should be
     * @param  content is the FsContent object that needs to have the artifact added for it
     * @param bbattributes is the collection of blackboard attributes that need to be added to the artifact after the artifact has been created
     */
    public void addArtifact(BlackboardArtifact.ARTIFACT_TYPE type, FsContent content, Collection<BlackboardAttribute> bbattributes) {

        try {
            BlackboardArtifact bbart = content.newArtifact(type);
            bbart.addAttributes(bbattributes);
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error while trying to add an artifact: " + ex);
            this.addErrorMessage(this.getName() + ": Error while trying to add artifact to case for file:" + content.getName());
        }
    }

        /**
     * Returns a List from a result set based on sql query.
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
            dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", connectionString);
            temprs = tempdbconnect.executeQry(query);
            list = this.resultSetToArrayList(temprs);
            tempdbconnect.closeConnection();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);
            return new ArrayList<HashMap<String,Object>>();
        }
        return list;
    }

        /**
     * Returns a List of FsContent objects from TSK based on sql query.
     *
     * @param  rs is the resultset that needs to be converted to an arraylist
     * @return  list returns the arraylist built from the converted resultset
     */
    public List<HashMap<String,Object>> resultSetToArrayList(ResultSet rs) throws SQLException {
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
     * Returns a List of string error messages from the inheriting class
     * @return  errorMessages returns all error messages logged
     */

    public ArrayList<String> getErrorMessage() {
        return errorMessages;
    }

        /**
     * Adds a string to the error message list
     *
     * @param  message is an error message represented as a string
     */
    public void addErrorMessage(String message) {
        errorMessages.add(message);
    }

        /**
     * Returns the name of the inheriting class
     * @return  Gets the moduleName set in the moduleName data member
     */
    public String getName() {
        return moduleName;
    }
}