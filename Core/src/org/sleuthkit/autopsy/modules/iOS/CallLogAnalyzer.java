/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.modules.iOS;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

class CallLogAnalyzer {

    private Connection connection = null;
    private ResultSet resultSet = null;
    private Statement statement = null;
    private String dbPath = "";
    private long fileId = 0;
    private java.io.File jFile = null;
    private String moduleName = iOSModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(CallLogAnalyzer.class.getName());
    private Blackboard blackboard;

    public void findCallLogs(IngestJobContext context) {
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        List<AbstractFile> absFiles;
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("name ='contacts2.db' OR name ='contacts.db'"); //NON-NLS //get exact file names
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile AF : absFiles) {
                try {
                    jFile = new java.io.File(Case.getCurrentCase().getTempDirectory(), AF.getName().replaceAll("[<>%|\"/:*\\\\]", ""));
                    ContentUtils.writeToFile(AF, jFile, context::dataSourceIngestIsCancelled);
                    dbPath = jFile.toString(); //path of file as string
                    fileId = AF.getId();
                    findCallLogsInDB(dbPath, fileId);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing Call logs", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding Call logs", e); //NON-NLS
        }
    }

    @Messages({"CallLogAnalyzer.indexError.message=Failed to index call log artifact for keyword search."})
    private void findCallLogsInDB(String DatabasePath, long fId) {
        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC"); //NON-NLS //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath); //NON-NLS
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Error opening database", e); //NON-NLS
        }

        Case currentCase = Case.getCurrentCase();
        SleuthkitCase skCase = currentCase.getSleuthkitCase();
        try {
            AbstractFile f = skCase.getAbstractFileById(fId);
            if (f == null) {
                logger.log(Level.SEVERE, "Error getting abstract file " + fId); //NON-NLS
                return;
            }

            try {
                resultSet = statement.executeQuery(
                        "SELECT number,date,duration,type, name FROM calls ORDER BY date DESC;"); //NON-NLS

                BlackboardArtifact bba;
                String name; // name of person dialed or called. null if unregistered
                String number; //string phone number
                String duration; //duration of call in seconds
                String date; // Unix time
                String type; // 1 incoming, 2 outgoing, 3 missed

                while (resultSet.next()) {
                    name = resultSet.getString("name"); //NON-NLS
                    number = resultSet.getString("number"); //NON-NLS
                    duration = resultSet.getString("duration"); //NON-NLS
                    date = resultSet.getString("date"); //NON-NLS
                    type = resultSet.getString("type"); //NON-NLS

                    bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG); //create a call log and then add attributes from result set.
                    if (type.equalsIgnoreCase("outgoing")) { //NON-NLS
                        bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, moduleName, number));
                    } else { /// Covers INCOMING and MISSED
                        bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, moduleName, number));
                    }
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START, moduleName, date)); // RC: Should be long!
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END, moduleName, duration + date)); // RC: Should be long!
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, type));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, moduleName, name));

                    try {
                        // index the artifact for keyword search
                        blackboard.indexArtifact(bba);
                    } catch (Blackboard.BlackboardException ex) {
                        logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                        MessageNotifyUtil.Notify.error(
                                Bundle.CallLogAnalyzer_indexError_message(), bba.getDisplayName());
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error parsing Call logs to the Blackboard", e); //NON-NLS
            } finally {
                try {
                    resultSet.close();
                    statement.close();
                    connection.close();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error closing the database", e); //NON-NLS
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Call logs to the Blackboard", e); //NON-NLS
        }

    }

}
