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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

class ContactAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(ContactAnalyzer.class.getName());

    public static void findContacts() {

        List<AbstractFile> absFiles;
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("name ='contacts2.db' OR name ='contacts.db'"); //get exact file names
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile AF : absFiles) {
                try {
                    File jFile = new File(Case.getCurrentCase().getTempDirectory(), AF.getName());
                    ContentUtils.writeToFile(AF, jFile);
                    findContactsInDB(jFile.toString(), AF);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing Contacts", e);
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding Contacts", e);
        }
    }

    /**
     *
     * @param DatabasePath
     * @param fId Will create artifact from a database given by the path The
     * fileId will be the Abstract file associated with the artifacts
     */
    private static void findContactsInDB(String DatabasePath, AbstractFile f) {
        Connection connection = null;
        ResultSet resultSet = null;
        Statement statement = null;

        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC"); //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath);
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Error opening database", e);
            return;
        }

        try {
            // get display_name, mimetype(email or phone number) and data1 (phonenumber or email address depending on mimetype)
            //sorted by name, so phonenumber/email would be consecutive for a person if they exist.
            resultSet = statement.executeQuery(
                    "SELECT mimetype,data1, name_raw_contact.display_name AS display_name \n"
                    + "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) \n"
                    + "JOIN raw_contacts AS name_raw_contact ON(name_raw_contact_id=name_raw_contact._id) "
                    + "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) \n"
                    + "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) \n"
                    + "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2'\n"
                    + "ORDER BY name_raw_contact.display_name ASC;");

            BlackboardArtifact bba;
            bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
            String name;
            String oldName = "";
            String mimetype; // either phone or email
            String data1; // the phone number or email
            while (resultSet.next()) {
                name = resultSet.getString("display_name");
                data1 = resultSet.getString("data1");
                mimetype = resultSet.getString("mimetype");
//                    System.out.println(resultSet.getString("data1") + resultSet.getString("mimetype") + resultSet.getString("display_name")); //Test code
                if (name.equals(oldName) == false) {
                    bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, name));
                }
                if (mimetype.equals("vnd.android.cursor.item/phone_v2")) {
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID(), moduleName, data1));
                } else {
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL.getTypeID(), moduleName, data1));
                }
                oldName = name;
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Contacts to Blackboard", e);
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
                statement.close();
                connection.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error closing database", e);
            }
        }

    }

}
