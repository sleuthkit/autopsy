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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

class TextMessageAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(TextMessageAnalyzer.class.getName());

    public static void findTexts() {
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            List<AbstractFile> absFiles = skCase.findAllFilesWhere("name ='mmssms.db'"); //NON-NLS //get exact file name

            for (AbstractFile abstractFile : absFiles) {
                try {
                    File jFile = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, jFile);
                    findTextsInDB(jFile.toString(), abstractFile);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing text messages", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding text messages", e); //NON-NLS
        }
    }

    private static void findTextsInDB(String DatabasePath, AbstractFile f) {
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
                    "Select address,date,read,type,subject,body FROM sms;"); //NON-NLS

            String address; // may be phone number, or other addresses

            String direction; // message received in inbox = 1, message sent = 2
            String subject;//message subject
            Integer read; // may be unread = 0, read = 1
            String body; //message body
            while (resultSet.next()) {
                address = resultSet.getString("address"); //NON-NLS
                Long date = Long.valueOf(resultSet.getString("date")) / 1000; //NON-NLS
                
                read = resultSet.getInt("read"); //NON-NLS
                subject = resultSet.getString("subject"); //NON-NLS
                body = resultSet.getString("body"); //NON-NLS

                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE); //create Message artifact and then add attributes from result set.
                if (resultSet.getString("type").equals("1")) { //NON-NLS
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID(), moduleName,
                                                             NbBundle.getMessage(TextMessageAnalyzer.class,
                                                                                 "TextMessageAnalyzer.bbAttribute.incoming")));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM.getTypeID(), moduleName, address));
                } else {
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID(), moduleName,
                                                             NbBundle.getMessage(TextMessageAnalyzer.class,
                                                                                 "TextMessageAnalyzer.bbAttribute.outgoing")));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO.getTypeID(), moduleName, address));
                }
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, date));
                
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS.getTypeID(), moduleName, read));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID(), moduleName, subject));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(), moduleName, body));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE.getTypeID(), moduleName,
                                                         NbBundle.getMessage(TextMessageAnalyzer.class,
                                                                             "TextMessageAnalyzer.bbAttribute.smsMessage")));
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing text messages to Blackboard", e); //NON-NLS
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
