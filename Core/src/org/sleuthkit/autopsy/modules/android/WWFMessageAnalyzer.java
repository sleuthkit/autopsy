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
import java.util.MissingResourceException;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author sidhesh
 */
public class WWFMessageAnalyzer implements AndroidAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(GoogleMapLocationAnalyzer.class.getName());
    private static final String[] databaseNames = {"WordsFramework"};

    @Override
    public void findInDB(Connection connection, AbstractFile abstractFile) {
        ResultSet resultSet = null;
        Statement statement = null;

        try {
            statement = connection.createStatement();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error opening database", e); //NON-NLS
            return;
        }

        try {
            resultSet = statement.executeQuery(
                    "SELECT message,strftime('%s' ,created_at) as datetime,user_id,game_id FROM chat_messages ORDER BY game_id DESC, created_at DESC;"); //NON-NLS

            String message; // WWF Message
            String user_id; // the ID of the user who sent the message.
            String game_id; // ID of the game which the the message was sent.

            while (resultSet.next()) {
                message = resultSet.getString("message"); //NON-NLS
                Long created_at = resultSet.getLong("datetime"); //NON-NLS
                user_id = resultSet.getString("user_id"); //NON-NLS
                game_id = resultSet.getString("game_id"); //NON-NLS

                BlackboardArtifact bba = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE); //create a call log and then add attributes from result set.
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, created_at));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, user_id));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MSG_ID.getTypeID(), moduleName, game_id));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(), moduleName, message));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE.getTypeID(), moduleName,
                        NbBundle.getMessage(org.sleuthkit.autopsy.modules.android.WWFMessageAnalyzer.class,
                                "WWFMessageAnalyzer.bbAttribute.wordsWithFriendsMsg")));
            }
        } catch (SQLException | TskCoreException | MissingResourceException e) {
            logger.log(Level.SEVERE, "Error parsing WWF messages to the Blackboard", e); //NON-NLS
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
                statement.close();
                connection.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error closing database", e); //NON-NLS
            }
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

}
