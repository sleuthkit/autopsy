/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Look for text messages and allow resulting blackboard artifacts to be
 * generated.
 */
class TextMessageAnalyzer {

    private Connection connection = null;
    private ResultSet resultSet = null;
    private Statement statement = null;
    private String dbPath = "";
    private long fileId = 0;
    private java.io.File jFile = null;
    List<AbstractFile> absFiles;
    private final String moduleName = iOSModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(TextMessageAnalyzer.class.getName());
    private Blackboard blackboard;

    /**
     * Find text messages given an ingest job context and index the results.
     *
     * @param context The ingest job context.
     */
    void findTexts(IngestJobContext context) {
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }
        blackboard = openCase.getServices().getBlackboard();
        try {
            SleuthkitCase skCase = openCase.getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("name ='mmssms.db'"); //NON-NLS //get exact file name
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile file : absFiles) {
                try {
                    jFile = new java.io.File(Case.getOpenCase().getTempDirectory(), file.getName().replaceAll("[<>%|\"/:*\\\\]", ""));
                    dbPath = jFile.toString(); //path of file as string
                    fileId = file.getId();
                    ContentUtils.writeToFile(file, jFile, context::dataSourceIngestIsCancelled);
                    findTextsInDB(dbPath, fileId);
                } catch (ReadContentInputStream.ReadContentInputStreamException ex) {
                    logger.log(Level.WARNING, String.format("Error reading content from file '%s' (id=%d).", file.getName(), fileId), ex); //NON-NLS
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, String.format("Error writing content from file '%s' (id=%d) to '%s'.", file.getName(), fileId, dbPath), ex); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding text messages", e); //NON-NLS
        }
    }

    /**
     * Create blackboard artifacts and index results for text messages found in
     * the database.
     *
     * @param DatabasePath The path to the database.
     * @param fileId       The ID of the file associated with artifacts.
     */
    @Messages({"TextMessageAnalyzer.indexError.message=Failed to index text message artifact for keyword search."})
    private void findTextsInDB(String DatabasePath, long fileId) {
        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        Case currentCase;
        try {
            currentCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC"); //NON-NLS //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath); //NON-NLS
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Error opening database", e); //NON-NLS
        }

        SleuthkitCase skCase = currentCase.getSleuthkitCase();
        try {
            AbstractFile file = skCase.getAbstractFileById(fileId);
            if (file == null) {
                logger.log(Level.SEVERE, "Error getting abstract file {0}", fileId); //NON-NLS
                return;
            }

            try {
                resultSet = statement.executeQuery(
                        "SELECT address,date,type,subject,body FROM sms;"); //NON-NLS

                BlackboardArtifact bba;
                String address; // may be phone number, or other addresses
                String date;//unix time
                String type; // message received in inbox = 1, message sent = 2
                String subject;//message subject
                String body; //message body
                while (resultSet.next()) {
                    address = resultSet.getString("address"); //NON-NLS
                    date = resultSet.getString("date"); //NON-NLS
                    type = resultSet.getString("type"); //NON-NLS
                    subject = resultSet.getString("subject"); //NON-NLS
                    body = resultSet.getString("body"); //NON-NLS

                    bba = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE); //create Message artifact and then add attributes from result set.
                    Collection<BlackboardAttribute> attributes = new ArrayList<>();
                    // @@@ NEed to put into more specific TO or FROM
                    if (type.equals("1")) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, NbBundle.getMessage(this.getClass(), "TextMessageAnalyzer.bbAttribute.incoming")));
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, moduleName, address));
                    } else {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, NbBundle.getMessage(this.getClass(), "TextMessageAnalyzer.bbAttribute.outgoing")));
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, moduleName, address));
                    }
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, date));
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, type));
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT, moduleName, subject));
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, moduleName, body));
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, moduleName, NbBundle.getMessage(this.getClass(), "TextMessageAnalyzer.bbAttribute.smsMessage")));

                    bba.addAttributes(attributes);
                    try {
                        // index the artifact for keyword search
                        blackboard.indexArtifact(bba);
                    } catch (Blackboard.BlackboardException ex) {
                        logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                        MessageNotifyUtil.Notify.error(
                                Bundle.TextMessageAnalyzer_indexError_message(), bba.getDisplayName());
                    }
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error parsing text messages to Blackboard", e); //NON-NLS
            } finally {
                try {
                    resultSet.close();
                    statement.close();
                    connection.close();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error closing database", e); //NON-NLS
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing text messages to Blackboard", e); //NON-NLS
        }

    }

}
