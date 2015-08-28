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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Locates a variety of different call log databases, parses them, and populates
 * the blackboard.
 */
public class CallLogAnalyzer implements AndroidAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(CacheLocationAnalyzer.class.getName());
    private static final String[] databaseNames = {"logs.db", "contacts.db", "contacts2.db"};
    /**
     * the names of tables that potentially hold call logs in the dbs
     */
    private static final Iterable<String> tableNames = Arrays.asList("calls", "logs"); //NON-NLS

    @Override
    public void findInDB(Connection connection, AbstractFile abstractFile) {

        try {
            Statement statement = connection.createStatement();
            for (String tableName : tableNames) {
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT number,date,duration,type, name FROM " + tableName + " ORDER BY date DESC;");) { //NON-NLS
                    logger.log(Level.INFO, "Reading call log from table {0}", new Object[]{tableName}); //NON-NLS
                    while (resultSet.next()) {
                        Long date = resultSet.getLong("date") / 1000;
                        final CallDirection direction = CallDirection.fromType(resultSet.getInt("type")); //NON-NLS
                        String directionString = direction != null ? direction.getDisplayName() : "";
                        final String number = resultSet.getString("number"); //NON-NLS
                        final long duration = resultSet.getLong("duration"); //NON-NLS  //duration of call is in seconds
                        final String name = resultSet.getString("name"); //NON-NLS  // name of person dialed or called. null if unregistered

                        try {
                            BlackboardArtifact bba = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG); //create a call log and then add attributes from result set.
                            if (direction == CallLogAnalyzer.CallDirection.OUTGOING) {
                                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO.getTypeID(), moduleName, number));
                            } else { /// Covers INCOMING and MISSED
                                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM.getTypeID(), moduleName, number));
                            }
                            bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID(), moduleName, date));
                            bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID(), moduleName, duration + date));
                            bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID(), moduleName, directionString));
                            bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, name));
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error posting call log record to the Blackboard", ex); //NON-NLS
                        }
                    }
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Could not read table {0}", new Object[]{tableName}); //NON-NLS
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Could not parse call log; error connecting to db", ex); //NON-NLS
        }
    }

    @Override
    public String[] getDatabaseNames() {
        return databaseNames;
    }

    @Override
    public boolean parsesDB() {
        return true;
    }

    @Override
    public void findInFile(File file, AbstractFile abstractFile) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
