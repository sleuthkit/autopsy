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
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import org.openide.util.Exceptions;
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
            absFiles = skCase.findAllFilesWhere("name ='contacts2.db' OR name ='contacts.db'"); //NON-NLS //get exact file names
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile AF : absFiles) {
                try {
                    File jFile = new File(Case.getCurrentCase().getTempDirectory(), AF.getName());
                    ContentUtils.writeToFile(AF, jFile);
                    findContactsInDB(jFile.toString(), AF);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing Contacts", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding Contacts", e); //NON-NLS
        }
    }

    /**
     *
     * @param databasePath
     * @param fId Will create artifact from a database given by the path The
     * fileId will be the Abstract file associated with the artifacts
     */
    private static void findContactsInDB(String databasePath, AbstractFile f) {
        Connection connection = null;
        ResultSet resultSet = null;
        Statement statement = null;

        if (databasePath == null || databasePath.isEmpty()) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC"); //NON-NLS //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath); //NON-NLS
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Error opening database", e); //NON-NLS
            return;
        }

        try {
            // get display_name, mimetype(email or phone number) and data1 (phonenumber or email address depending on mimetype)
            //sorted by name, so phonenumber/email would be consecutive for a person if they exist.
            //safely building the query.
            //lists of tables and columns is created. These lists are referred while building a query.
            List<String> tableList = new ArrayList<>();
            List<String> columnList = new ArrayList<>();
            try {
                DatabaseMetaData metadata = connection.getMetaData();
                String tableName = "";
                ResultSet rs1 = metadata.getTables(null, null, "%", null);
                while (rs1.next()) {
                    tableName = rs1.getString("TABLE_NAME");
                    tableList.add(tableName);
                    //populate the list of columns
                    try {
                        ResultSet rs2 = metadata.getColumns(null, null, tableName, null);
                        while (rs2.next()) {
                            columnList.add(rs2.getString("COLUMN_NAME"));
                        }
                    } catch (SQLException ex) {
                        logger.log(Level.SEVERE, "Error getting metadata from the the Database");
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error getting metadata from the the Database.");
            }
            String query = "";
            if (columnList.contains("mimetype") && columnList.contains("data1") && tableList.contains("name_raw_contact") && 
                    columnList.contains("display_name") && tableList.contains("raw_contacts")) {
                query += "SELECT mimetype, data1, name_raw_contact.display_name AS display_name FROM raw_contacts ";
                if (tableList.contains("contacts") && tableList.contains("raw_contacts") && columnList.contains("contact_id") && columnList.contains("_id")) {
                query += "JOIN contacts ON (raw_contacts.contact_id=contacts._id) ";
                }
                if (tableList.contains("raw_contacts") && columnList.contains("name_raw_contact_id") && columnList.contains("_id")) {
                    query += "JOIN raw_contacts AS name_raw_contact ON(name_raw_contact_id=name_raw_contact._id) ";
                }
                if (tableList.contains("data") && columnList.contains("raw_contact_id") && columnList.contains("_id")) {
                    query += "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) ";
                }
                if (tableList.contains("mimetypes") && columnList.contains("mimetype_id") && columnList.contains("_id")) {
                    query += "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) ";

                }
                if (columnList.contains("mimetype") && columnList.contains("mimetype")) {
                    query += "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2' ";

                }
                if (tableList.contains("name_raw_contact") && columnList.contains("display_name"))
                    query += "ORDER BY name_raw_contact.display_name ASC;";
            }
            
            else {
                logger.log(Level.SEVERE, "The will not be executed. Contains non-existing column names/table names. ");
            }
            resultSet = statement.executeQuery(query);
            BlackboardArtifact bba;
            bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
            String name;
            String oldName = "";
            String mimetype; // either phone or email
            String data1; // the phone number or email
            while (resultSet.next()) {
                name = resultSet.getString("display_name"); //NON-NLS
                data1 = resultSet.getString("data1"); //NON-NLS
                mimetype = resultSet.getString("mimetype"); //NON-NLS
//                    System.out.println(resultSet.getString("data1") + resultSet.getString("mimetype") + resultSet.getString("display_name")); //Test code
                if (name.equals(oldName) == false) {
                    bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
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
            logger.log(Level.WARNING, "Unable to execute contacts SQL query against {0} : {1}", new Object[]{databasePath, e}); //NON-NLS
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
}
