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
package org.sleuthkit.autopsy.modules.iOS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
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

class ContactAnalyzer {

    private Connection connection = null;
    private ResultSet resultSet = null;
    private Statement statement = null;
    private String dbPath = "";
    private long fileId = 0;
    private java.io.File jFile = null;
    private String moduleName = iOSModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(ContactAnalyzer.class.getName());
    private Blackboard blackboard;

    public void findContacts(IngestJobContext context) {

        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        List<AbstractFile> absFiles;
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("LOWER(name) LIKE LOWER('%call_history%') "); //NON-NLS //get exact file names
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile AF : absFiles) {
                try {
                    jFile = new java.io.File(Case.getCurrentCase().getTempDirectory(), AF.getName().replaceAll("[<>%|\"/:*\\\\]", ""));
                    //jFile = new java.io.File(Case.getCurrentCase().getTempDirectory(), i+".txt");
                    ContentUtils.writeToFile(AF, jFile, context::dataSourceIngestIsCancelled);
                    //copyFileUsingStreams(AF,jFile);
                    //copyFileUsingStream(AF,jFile);
                    dbPath = jFile.toString(); //path of file as string
                    fileId = AF.getId();
                    //findContactsInDB(dbPath, fileId);
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
     * @param DatabasePath
     * @param fId          Will create artifact from a database given by the
     *                     path The fileId will be the Abstract file associated
     *                     with the artifacts
     */
    @Messages({"ContactAnalyzer.indexError.message=Failed to index contact artifact for keyword search."})
    private void findContactsInDB(String DatabasePath, long fId) {
        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC"); //NON-NLS //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath); //NON-NLS
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Error opening database", e); //NON-NLS
        }

        Case currentCase = Case.getCurrentCase();
        SleuthkitCase skCase = currentCase.getSleuthkitCase();
        try {
            AbstractFile f = skCase.getAbstractFileById(fId);
            if (f == null) {
                logger.log(Level.SEVERE, "Error getting abstract file " + fId); //NON-NLS
                return;
            }

            try {
                // get display_name, mimetype(email or phone number) and data1 (phonenumber or email address depending on mimetype)
                //sorted by name, so phonenumber/email would be consecutive for a person if they exist.
                resultSet = statement.executeQuery(
                        "SELECT mimetype,data1, name_raw_contact.display_name AS display_name \n" //NON-NLS
                        + "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) \n" //NON-NLS
                        + "JOIN raw_contacts AS name_raw_contact ON(name_raw_contact_id=name_raw_contact._id) " //NON-NLS
                        + "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) \n" //NON-NLS
                        + "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) \n" //NON-NLS
                        + "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2'\n" //NON-NLS
                        + "ORDER BY name_raw_contact.display_name ASC;"); //NON-NLS

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
                    if (name.equals(oldName) == false) {
                        bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
                        bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, moduleName, name));
                    }
                    if (mimetype.equals("vnd.android.cursor.item/phone_v2")) { //NON-NLS
                        bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, moduleName, data1));
                    } else {
                        bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL, moduleName, data1));
                    }
                    oldName = name;
                    
                    try {
                        // index the artifact for keyword search
                        blackboard.indexArtifact(bba);
                    } catch (Blackboard.BlackboardException ex) {
                        logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                        MessageNotifyUtil.Notify.error(
                                Bundle.ContactAnalyzer_indexError_message(), bba.getDisplayName());
                    }                    
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error parsing Contacts to Blackboard", e); //NON-NLS
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
            logger.log(Level.SEVERE, "Error parsing Contacts to Blackboard", e); //NON-NLS
        }

    }

    public static void copyFileUsingStream(AbstractFile file, File jFile) throws IOException {
        InputStream is = new ReadContentInputStream(file);
        OutputStream os = new FileOutputStream(jFile);
        byte[] buffer = new byte[8192];
        int length;
        try {
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
                System.out.println(length);
                os.flush();

            }

        } finally {
            is.close();
            os.close();
        }
    }

    public static void copyFileUsingStreams(AbstractFile file, File jFile) {
        InputStream istream;
        OutputStream ostream = null;
        int c;
        final int EOF = -1;
        istream = new ReadContentInputStream(file);
        try {
            ostream = new FileOutputStream(jFile);
            while ((c = istream.read()) != EOF) {
                ostream.write(c);
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage()); //NON-NLS
        } finally {
            try {
                istream.close();
                ostream.close();
            } catch (IOException e) {
                System.out.println("File did not close"); //NON-NLS
            }
        }
    }
}
