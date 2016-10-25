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
package org.sleuthkit.autopsy.modules.android;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Locates a variety of different call log databases, parses them, and populates
 * the blackboard.
 */
class CallLogAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(CallLogAnalyzer.class.getName());
    private static Blackboard blackboard;

    /**
     * the names of tables that potentially hold call logs in the dbs
     */
    private static final Iterable<String> tableNames = Arrays.asList("calls", "logs"); //NON-NLS

    public static void findCallLogs(Content dataSource, FileManager fileManager,
            IngestJobContext context) {
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        try {
            List<AbstractFile> absFiles = fileManager.findFiles(dataSource, "logs.db"); //NON-NLS
            absFiles.addAll(fileManager.findFiles(dataSource, "contacts.db")); //NON-NLS
            absFiles.addAll(fileManager.findFiles(dataSource, "contacts2.db")); //NON-NLS
            for (AbstractFile abstractFile : absFiles) {
                try {
                    File file = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, file, context::dataSourceIngestIsCancelled);
                    findCallLogsInDB(file.toString(), abstractFile);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error writing temporary call log db to disk", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding call logs", e); //NON-NLS
        }
    }

    @Messages({"CallLogAnalyzer.indexError.message=Failed to index call log artifact for keyword search."})
    private static void findCallLogsInDB(String DatabasePath, AbstractFile f) {

        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath); //NON-NLS
                Statement statement = connection.createStatement();) {

            for (String tableName : tableNames) {
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT number,date,duration,type, name FROM " + tableName + " ORDER BY date DESC;");) { //NON-NLS
                    logger.log(Level.INFO, "Reading call log from table {0} in db {1}", new Object[]{tableName, DatabasePath}); //NON-NLS
                    while (resultSet.next()) {
                        Long date = resultSet.getLong("date") / 1000;
                        final CallDirection direction = CallDirection.fromType(resultSet.getInt("type")); //NON-NLS
                        String directionString = direction != null ? direction.getDisplayName() : "";
                        final String number = resultSet.getString("number"); //NON-NLS
                        final long duration = resultSet.getLong("duration"); //NON-NLS  //duration of call is in seconds
                        final String name = resultSet.getString("name"); //NON-NLS  // name of person dialed or called. null if unregistered

                        try {
                            BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG); //create a call log and then add attributes from result set.
                            if (direction == CallDirection.OUTGOING) {
                                bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, moduleName, number));
                            } else { /// Covers INCOMING and MISSED
                                bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, moduleName, number));
                            }
                            bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_START, moduleName, date));
                            bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_END, moduleName, duration + date));
                            bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, directionString));
                            bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, name));

                            try {
                                // index the artifact for keyword search
                                blackboard.indexArtifact(bba);
                            } catch (Blackboard.BlackboardException ex) {
                                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                                MessageNotifyUtil.Notify.error(
                                        Bundle.CallLogAnalyzer_indexError_message(), bba.getDisplayName());
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error posting call log record to the Blackboard", ex); //NON-NLS
                        }
                    }
                } catch (SQLException e) {
                    logger.log(Level.WARNING, String.format("Could not read table %s in db %s", tableName, DatabasePath), e); //NON-NLS
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not parse call log; error connecting to db " + DatabasePath, e); //NON-NLS
        }
    }

    private static enum CallDirection {

        INCOMING(1, "Incoming"), OUTGOING(2, "Outgoing"), MISSED(3, "Missed"); //NON-NLS

        private final int type;

        private final String displayName;

        public String getDisplayName() {
            return displayName;
        }

        private CallDirection(int type, String displayName) {
            this.type = type;
            this.displayName = displayName;
        }

        static CallDirection fromType(int t) {
            switch (t) {
                case 1:
                    return INCOMING;
                case 2:
                    return OUTGOING;
                case 3:
                    return MISSED;
                default:
                    return null;
            }
        }
    }
}
