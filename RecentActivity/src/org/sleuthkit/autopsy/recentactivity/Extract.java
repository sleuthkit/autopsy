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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.SQLiteDBConnect;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.datamodel.*;

abstract class Extract {

    private static final Logger logger = Logger.getLogger(Extract.class.getName());

    protected Case currentCase;
    protected SleuthkitCase tskCase;
    protected Blackboard blackboard;
    protected FileManager fileManager;

    private final ArrayList<String> errorMessages = new ArrayList<>();
    boolean dataFound = false;

    /**
     * Returns the name of the inheriting class
     *
     * @return Gets the moduleName
     */
    abstract protected String getModuleName();

    @Messages({"Extract.indexError.message=Failed to index artifact for keyword search.",
        "Extract.noOpenCase.errMsg=No open case available."})
    final void init() throws IngestModuleException {
        try {
            currentCase = Case.getCurrentCaseThrows();
            tskCase = currentCase.getSleuthkitCase();
            blackboard = tskCase.getBlackboard();
            fileManager = currentCase.getServices().getFileManager();
        } catch (NoCurrentCaseException ex) {
            //TODO: fix this error message
            throw new IngestModuleException(Bundle.Extract_indexError_message(), ex);
        }
        configExtractor();
    }

    /**
     * Override to add any module-specific configuration
     *
     * @throws IngestModuleException
     */
    void configExtractor() throws IngestModuleException {
    }

    abstract void process(Content dataSource, IngestJobContext context);

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

//
//    /**
//     * Method to index a blackboard artifact for keyword search
//     *
//     * @param bbart Blackboard artifact to be indexed
//     */
//  
//    void postArtifacts(Collections<BlackboardArtifact> bbarts) throws Blackboard.BlackboardException {
//
//        // index the artifact for keyword search
//        blackboard.postArtifact(bbarts, getModuleName());
////        } catch (Blackboard.BlackboardException ex) {
////            logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bbart.getDisplayName(), ex); //NON-NLS
////            MessageNotifyUtil.Notify.error(Bundle.Extract_indexError_message(), bbart.getDisplayName());
////        } catch (NoCurrentCaseException ex) {
////            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
////            MessageNotifyUtil.Notify.error(Bundle.Extract_noOpenCase_errMsg(), bbart.getDisplayName());
////        }
//    }
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

        String connectionString = "jdbc:sqlite:" + path; //NON-NLS
        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", connectionString); //NON-NLS
                ResultSet temprs = tempdbconnect.executeQry(query);) {
            return this.resultSetToArrayList(temprs);
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error while trying to read into a sqlite db." + connectionString, ex); //NON-NLS
            errorMessages.add(NbBundle.getMessage(this.getClass(), "Extract.dbConn.errMsg.failedToQueryDb", getModuleName()));
            return Collections.<HashMap<String, Object>>emptyList();
        }
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

    public boolean foundData() {
        return dataFound;
    }
}
