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
import org.apache.commons.codec.binary.Base64;
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
 * Locates database for the Tango app and adds info to blackboard.
 */
class TangoMessageAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(TangoMessageAnalyzer.class.getName());
    private static Blackboard blackboard;

    public static void findTangoMessages(Content dataSource, FileManager fileManager,
            IngestJobContext context) {
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        List<AbstractFile> absFiles;
        try {
            absFiles = fileManager.findFiles(dataSource, "tc.db"); //NON-NLS
            for (AbstractFile abstractFile : absFiles) {
                try {
                    File jFile = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, jFile, context::dataSourceIngestIsCancelled);
                    findTangoMessagesInDB(jFile.toString(), abstractFile);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing Tango messages", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding Tango messages", e); //NON-NLS
        }
    }

    @Messages({"TangoMessageAnalyzer.indexError.message=Failed to index Tango message artifact for keyword search."})
    private static void findTangoMessagesInDB(String DatabasePath, AbstractFile f) {
        Connection connection = null;
        ResultSet resultSet = null;
        Statement statement = null;

        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC"); //NON-NLS //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath); //NON-NLS
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Error opening database", e); //NON-NLS
            return;
        }

        try {
            resultSet = statement.executeQuery(
                    "SELECT conv_id, create_time,direction,payload FROM messages ORDER BY create_time DESC;"); //NON-NLS

            String conv_id; // seems to wrap around the message found in payload after decoding from base-64
            String direction; // 1 incoming, 2 outgoing
            String payload; // seems to be a base64 message wrapped by the conv_id

            while (resultSet.next()) {
                conv_id = resultSet.getString("conv_id"); //NON-NLS
                Long create_time = Long.valueOf(resultSet.getString("create_time")) / 1000; //NON-NLS
                if (resultSet.getString("direction").equals("1")) { //NON-NLS
                    direction = "Incoming"; //NON-NLS
                } else {
                    direction = "Outgoing"; //NON-NLS
                }
                payload = resultSet.getString("payload"); //NON-NLS

                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE); //create a call log and then add attributes from result set.
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, create_time));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, direction));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, moduleName, decodeMessage(conv_id, payload)));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, moduleName,
                        NbBundle.getMessage(TangoMessageAnalyzer.class,
                                "TangoMessageAnalyzer.bbAttribute.tangoMessage")));

                try {
                    // index the artifact for keyword search
                    blackboard.indexArtifact(bba);
                } catch (Blackboard.BlackboardException ex) {
                    MessageNotifyUtil.Notify.error(
                            Bundle.TangoMessageAnalyzer_indexError_message(), bba.getDisplayName());
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Tango messages to the Blackboard", e); //NON-NLS
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

    //take the message string which is wrapped by a certain string, and return the text enclosed.
    private static String decodeMessage(String wrapper, String message) {
        String result = "";
        byte[] decoded = Base64.decodeBase64(message);
        try {
            String Z = new String(decoded, "UTF-8");
            result = Z.split(wrapper)[1];
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error decoding a Tango message", e); //NON-NLS
        }
        return result;
    }
}
