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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.codec.binary.Base64;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

 class TangoMessageAnalyzer {
    private Connection connection = null;
    private ResultSet resultSet = null;
    private Statement statement = null;
    private String dbPath = "";
    private long fileId = 0;
    private java.io.File jFile = null;
    private String moduleName= AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(TangoMessageAnalyzer.class.getName());
    
    public void findTangoMessages() {
        List<AbstractFile> absFiles;
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("name ='tc.db' "); //get exact file names
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile AF : absFiles) {
                try {
                    jFile = new java.io.File(Case.getCurrentCase().getTempDirectory(), AF.getName());
                    ContentUtils.writeToFile(AF,jFile);
                    dbPath = jFile.toString(); //path of file as string
                    fileId = AF.getId();
                    findTangoMessagesInDB(dbPath, fileId);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing Tango messages", e);
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding Tango messages", e);
        }
        
    }
    private void findTangoMessagesInDB(String DatabasePath, long fId) {
        if (DatabasePath == null || DatabasePath.isEmpty()) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC"); //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + DatabasePath);
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Error opening database", e);
        }

        Case currentCase = Case.getCurrentCase();
        SleuthkitCase skCase = currentCase.getSleuthkitCase();
        try {
            AbstractFile f = skCase.getAbstractFileById(fId);
            try {
                resultSet = statement.executeQuery(
                        "Select conv_id, create_time,direction,payload FROM messages ORDER BY create_time DESC;");

                BlackboardArtifact bba;
                String conv_id; // seems to wrap around the message found in payload after decoding from base-64
                String direction; // 1 incoming, 2 outgoing
                String payload; // seems to be a base64 message wrapped by the conv_id
              

                while (resultSet.next()) {
                    conv_id = resultSet.getString("conv_id");
                    Long create_time = Long.valueOf(resultSet.getString("create_time")) / 1000;
                    direction = resultSet.getString("direction");
                    payload = resultSet.getString("payload");

                    bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE); //create a call log and then add attributes from result set.
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, create_time));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID(), moduleName, direction));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(), moduleName, decodeMessage(conv_id,payload)));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE.getTypeID(), moduleName,"Tango Message" ));

                }

            } catch (Exception e) {
               logger.log(Level.SEVERE, "Error parsing Tango messages to the Blackboard", e);
            } finally {
                try {
                    resultSet.close();
                    statement.close();
                    connection.close();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error closing database", e);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Tango messages to the Blackboard", e);
        }
    }

   //take the message string which is wrapped by a certain string, and return the text enclosed.
   private String decodeMessage(String wrapper, String message)
   {
       String result= "";
       byte[] decoded = Base64.decodeBase64(message);
        try{
        String Z= new String (decoded,"UTF-8");
        result = Z.split(wrapper)[1];
        }catch(Exception e){
            logger.log(Level.SEVERE, "Error decoding a Tango message", e);
        }     
       return result;
   }
}
