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
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

class CallLogAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();

    private static final Logger logger = Logger.getLogger(CallLogAnalyzer.class.getName());

    /** the where clause(without 'where' of sql select statement to choose call
     * log dbs, update the list of file names to include more files */
    private static final String fileNameQuery = Stream.of("'logs.db'", "'contacts2.db'", "'contacts.db'")
            .collect(Collectors.joining(" OR name = ", "name = ", ""));

    /** the names of tables that potentially hold call logs in the dbs */
    private static final Iterable<String> tableNames = Arrays.asList("calls", "logs");

    public static void findCallLogs() {
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();

            for (AbstractFile abstractFile : skCase.findAllFilesWhere(fileNameQuery)) {
                try {
                    File file = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, file);
                    findCallLogsInDB(file.toString(), abstractFile);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error writing temporary call log db to disk", e);
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding call logs", e);
        }
    }

    private static void findCallLogsInDB(String DatabasePath, AbstractFile f) {

        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath);
             Statement statement = connection.createStatement();) {

            for (String tableName : tableNames) {
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT number,date,duration,type, name FROM " + tableName + " ORDER BY date DESC;");) {
                    logger.log(Level.INFO, "Reading call log from table {0} in db {1}", new Object[]{tableName, DatabasePath});
                    while (resultSet.next()) {
                        Long date = resultSet.getLong("date") / 1000;
                        final CallDirection direction = CallDirection.fromType(resultSet.getInt("type"));
                        String directionString = direction != null ? direction.getDisplayName() : "";
                        final String number = resultSet.getString("number");
                        final long duration = resultSet.getLong("duration");//duration of call is in seconds
                        final String name = resultSet.getString("name");// name of person dialed or called. null if unregistered

                        try {
                            BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG); //create a call log and then add attributes from result set.
                            if(direction == CallDirection.OUTGOING) {
                                bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO.getTypeID(), moduleName, number));
                            }
                            else { /// Covers INCOMING and MISSED
                                bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM.getTypeID(), moduleName, number));
                            }
                            bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID(), moduleName, date));
                            bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID(), moduleName, duration + date));
                            bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID(), moduleName, directionString));
                            bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, name));
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error posting call log record to the Blackboard", ex);
                        }
                    }
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Could not read table {0} in db {1}", new Object[]{tableName, DatabasePath});
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not parse call log; error connecting to db " + DatabasePath, e);
        }
    }

    private static enum CallDirection {

        INCOMING(1, "Incoming"), OUTGOING(2, "Outgoing"), MISSED(3, "Missed");

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
