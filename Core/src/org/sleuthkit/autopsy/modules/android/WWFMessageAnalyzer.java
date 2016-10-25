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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Finds database for words with friends, parses it, and adds info to
 * blackboard.
 */
class WWFMessageAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(WWFMessageAnalyzer.class.getName());
    private static Blackboard blackboard;

    public static void findWWFMessages(Content dataSource, FileManager fileManager,
            IngestJobContext context) {
        List<AbstractFile> absFiles;
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        try {
            absFiles = fileManager.findFiles(dataSource, "WordsFramework"); //NON-NLS

            for (AbstractFile abstractFile : absFiles) {
                try {
                    File jFile = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, jFile, context::dataSourceIngestIsCancelled);

                    findWWFMessagesInDB(jFile.toString(), abstractFile);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing WWF messages", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding WWF messages", e); //NON-NLS
        }
    }

    @Messages({"WWFMessageAnalyzer.indexError.message=Failed to index WWF message artifact for keyword search."})
    private static void findWWFMessagesInDB(String DatabasePath, AbstractFile f) {
        Connection connection = null;
        ResultSet resultSet = null;
        Statement statement = null;

        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC"); //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath); //NON-NLS
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
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

                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE); //create a call log and then add attributes from result set.
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, created_at));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, moduleName, user_id));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MSG_ID, moduleName, game_id));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, moduleName, message));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, moduleName,
                        NbBundle.getMessage(WWFMessageAnalyzer.class,
                                "WWFMessageAnalyzer.bbAttribute.wordsWithFriendsMsg")));

                try {
                    // index the artifact for keyword search
                    blackboard.indexArtifact(bba);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(
                            Bundle.WWFMessageAnalyzer_indexError_message(), bba.getDisplayName());
                }
            }
        } catch (Exception e) {
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
}
