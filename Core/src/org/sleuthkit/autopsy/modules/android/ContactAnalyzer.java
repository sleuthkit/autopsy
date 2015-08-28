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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Locates a variety of different contacts databases, parses them, and populates
 * the blackboard.
 */
public class ContactAnalyzer implements AndroidAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(ContactAnalyzer.class.getName());
    private static final String[] databaseNames = {"contacts.db", "contacts2.db"};

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
            // get display_name, mimetype(email or phone number) and data1 (phonenumber or email address depending on mimetype)
            //sorted by name, so phonenumber/email would be consecutive for a person if they exist.
            // check if contacts.name_raw_contact_id exists. Modify the query accordingly.
            Boolean column_found = false;
            DatabaseMetaData metadata = connection.getMetaData();
            ResultSet columnListResultSet = metadata.getColumns(null, null, "contacts", null); //NON-NLS
            while (columnListResultSet.next()) {
                if (columnListResultSet.getString("COLUMN_NAME").equals("name_raw_contact_id")) { //NON-NLS
                    column_found = true;
                    break;
                }
            }
            if (column_found) {
                resultSet = statement.executeQuery(
                        "SELECT mimetype,data1, name_raw_contact.display_name AS display_name \n" //NON-NLS
                        + "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) \n" //NON-NLS
                        + "JOIN raw_contacts AS name_raw_contact ON(name_raw_contact_id=name_raw_contact._id) " //NON-NLS
                        + "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) \n" //NON-NLS
                        + "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) \n" //NON-NLS
                        + "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2'\n" //NON-NLS
                        + "ORDER BY name_raw_contact.display_name ASC;"); //NON-NLS
            } else {
                resultSet = statement.executeQuery(
                        "SELECT mimetype,data1, raw_contacts.display_name AS display_name \n" //NON-NLS
                        + "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) \n" //NON-NLS
                        + "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) \n" //NON-NLS
                        + "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) \n" //NON-NLS
                        + "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2'\n" //NON-NLS
                        + "ORDER BY raw_contacts.display_name ASC;"); //NON-NLS
            }

            BlackboardArtifact bba;
            bba = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
            String name;
            String oldName = "";
            String mimetype; // either phone or email
            String data1; // the phone number or email
            while (resultSet.next()) {
                name = resultSet.getString("display_name"); //NON-NLS
                data1 = resultSet.getString("data1"); //NON-NLS
                mimetype = resultSet.getString("mimetype"); //NON-NLS
                if (name.equals(oldName) == false) {
                    bba = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, name));
                }
                if (mimetype.equals("vnd.android.cursor.item/phone_v2")) { //NON-NLS
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID(), moduleName, data1));
                } else {
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL.getTypeID(), moduleName, data1));
                }
                oldName = name;
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Unable to execute contacts SQL query: {1}", new Object[]{e}); //NON-NLS
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error posting to blackboard", e); //NON-NLS
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
