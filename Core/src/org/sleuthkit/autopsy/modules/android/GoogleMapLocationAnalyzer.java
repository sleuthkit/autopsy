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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;


class GoogleMapLocationAnalyzer {

    private Connection connection = null;
    private ResultSet resultSet = null;
    private Statement statement = null;
    private String dbPath = "";
    private long fileId = 0;
    private java.io.File jFile = null;
    private String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(GoogleMapLocationAnalyzer.class.getName());
    
    public void findGeoLocations() {
        List<AbstractFile> absFiles;
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("name ='da_destination_history'"); //get exact file name
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile AF : absFiles) {
                try {
                    jFile = new java.io.File(Case.getCurrentCase().getTempDirectory(), AF.getName());
                   ContentUtils.writeToFile(AF,jFile);
                    dbPath = jFile.toString(); //path of file as string
                    fileId = AF.getId();
                    findGeoLocationsInDB(dbPath, fileId);
                } catch (Exception e) {
                     logger.log(Level.SEVERE, "Error parsing Google map locations", e);
                }
            }
        } catch (TskCoreException e) {
             logger.log(Level.SEVERE, "Error finding Google map locations", e);
        }
    }

    private void findGeoLocationsInDB(String DatabasePath, long fId) {
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
                        "Select time,dest_lat,dest_lng,dest_title,dest_address,source_lat,source_lng FROM destination_history;");

                BlackboardArtifact bba;
                


                while (resultSet.next()) {
                    Long time = Long.valueOf(resultSet.getString("time")) / 1000;
                    String dest_lat = resultSet.getString("dest_lat");
                    String dest_lng = resultSet.getString("dest_lng");
                    String dest_title = resultSet.getString("dest_title");
                    String dest_address = resultSet.getString("dest_address");
                    String source_lat = resultSet.getString("source_lat");
                    String source_lng = resultSet.getString("source_lng");
                    
                    //add periods 6 decimal places before the end.
                    if(dest_lat.length()>6)
                        dest_lat =  dest_lat.substring(0, dest_lat.length()-6) + "." + dest_lat.substring(dest_lat.length()-6, dest_lat.length()) ;
                    if(dest_lng.length()>6)
                        dest_lng =  dest_lng.substring(0, dest_lng.length()-6) + "." + dest_lng.substring(dest_lng.length()-6, dest_lng.length())  ;
                    if(source_lat.length()>6)
                        source_lat = source_lat.substring(0, source_lat.length()-6) + "." + source_lat.substring(source_lat.length()-6, source_lat.length()) ;
                    if(source_lng.length()>6)
                        source_lng = source_lng.substring(0, source_lng.length()-6) + "." + source_lng.substring(source_lng.length()-6, source_lng.length()) ;
                    
//                    bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);//src
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY.getTypeID(), moduleName, "Source"));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), moduleName, source_lat));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), moduleName, source_lng));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, time));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID(), moduleName, "Google Maps History"));
//
//                    bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);//dest 
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY.getTypeID(), moduleName, "Destination"));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, time));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), moduleName, dest_lat));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), moduleName, dest_lng));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, dest_title));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID(), moduleName, dest_address));
//                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), moduleName, "Google Maps History"));
                 
                    bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE);
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY.getTypeID(), moduleName, "Destination"));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, time));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END.getTypeID(), moduleName, dest_lat));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END.getTypeID(), moduleName, dest_lng));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID(), moduleName, source_lat));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START.getTypeID(), moduleName, source_lng));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, dest_title));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID(), moduleName, dest_address));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), moduleName, "Google Maps History"));
                     
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error parsing Google map locations to the Blackboard", e);
            } finally {
                try {
                    resultSet.close();
                    statement.close();
                    connection.close();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error closing the database", e);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Google map locations to the Blackboard", e);
        }
    }
}
