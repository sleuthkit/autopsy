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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

abstract public class Extract {

    protected Case currentCase = Case.getCurrentCase(); // get the most updated case
    protected SleuthkitCase tskCase = currentCase.getSleuthkitCase();
    public final Logger logger = Logger.getLogger(this.getClass().getName());
    protected ArrayList<String> errorMessages = null;
    protected String moduleName = "";

    public List<FsContent> extractFiles(List<String> image, String query) {

        String allFS = new String();
        for (int i = 0; i < image.size(); i++) {
            if (i == 0) {
                allFS += " AND (0";
            }
            allFS += " OR fs_obj_id = '" + image.get(i) + "'";
            if (i == image.size() - 1) {
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

    public void addArtifact(BlackboardArtifact.ARTIFACT_TYPE type, FsContent content, Collection<BlackboardAttribute> bbattributes) {

        try {
            BlackboardArtifact bbart = content.newArtifact(type);
            bbart.addAttributes(bbattributes);
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error while trying to add an artifact: " + ex);
            this.addErrorMessage(this.getName() + ": Error while trying to add artifact to case for file:" + content.getName());
        }
    }

    public List dbConnect(String path, String query) {
        ResultSet temprs = null;
        List list = null;
        String connectionString = "jdbc:sqlite:" + path;
        try {
            dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", connectionString);
            temprs = tempdbconnect.executeQry(query);
            list = this.resultSetToArrayList(temprs);
            tempdbconnect.closeConnection();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);
        }
        return list;
    }

    public List<HashMap> resultSetToArrayList(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List list = new ArrayList(50);
        while (rs.next()) {
            HashMap row = new HashMap(columns);
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

    public ArrayList<String> getErrorMessage() {
        return errorMessages;
    }

    public void addErrorMessage(String message) {
        errorMessages.add(message);
    }

    public String getName() {
        return moduleName;
    }
}