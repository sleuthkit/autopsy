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
 * Finds and parses the Google Maps database.
 */
class GoogleMapLocationAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(GoogleMapLocationAnalyzer.class.getName());
    private static Blackboard blackboard;

    public static void findGeoLocations(Content dataSource, FileManager fileManager,
            IngestJobContext context) {
        List<AbstractFile> absFiles;
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        try {
            absFiles = fileManager.findFiles(dataSource, "da_destination_history"); //NON-NLS
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile abstractFile : absFiles) {
                try {
                    File jFile = new java.io.File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, jFile, context::dataSourceIngestIsCancelled);
                    findGeoLocationsInDB(jFile.toString(), abstractFile);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing Google map locations", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding Google map locations", e); //NON-NLS
        }
    }

    @Messages({"GoogleMapLocationAnalyzer.indexError.message=Failed to index GPS route artifact for keyword search."})
    private static void findGeoLocationsInDB(String DatabasePath, AbstractFile f) {
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
                    "SELECT time,dest_lat,dest_lng,dest_title,dest_address,source_lat,source_lng FROM destination_history;"); //NON-NLS

            while (resultSet.next()) {
                Long time = Long.valueOf(resultSet.getString("time")) / 1000; //NON-NLS
                String dest_title = resultSet.getString("dest_title"); //NON-NLS
                String dest_address = resultSet.getString("dest_address"); //NON-NLS

                double dest_lat = convertGeo(resultSet.getString("dest_lat")); //NON-NLS
                double dest_lng = convertGeo(resultSet.getString("dest_lng")); //NON-NLS
                double source_lat = convertGeo(resultSet.getString("source_lat")); //NON-NLS
                double source_lng = convertGeo(resultSet.getString("source_lng")); //NON-NLS

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
                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE);
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, moduleName,
                        NbBundle.getMessage(GoogleMapLocationAnalyzer.class,
                                "GoogleMapLocationAnalyzer.bbAttribute.destination")));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, time));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END, moduleName, dest_lat));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END, moduleName, dest_lng));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START, moduleName, source_lat));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START, moduleName, source_lng));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, moduleName, dest_title));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION, moduleName, dest_address));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName,
                        NbBundle.getMessage(GoogleMapLocationAnalyzer.class,
                                "GoogleMapLocationAnalyzer.bbAttribute.googleMapsHistory")));

                try {
                    // index the artifact for keyword search
                    blackboard.indexArtifact(bba);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(
                            Bundle.GoogleMapLocationAnalyzer_indexError_message(), bba.getDisplayName());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Google map locations to the Blackboard", e); //NON-NLS
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
                statement.close();
                connection.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error closing the database", e); //NON-NLS
            }
        }
    }

    //add periods 6 decimal places before the end.
    private static double convertGeo(String s) {
        if (s.length() > 6) {
            return Double.valueOf(s.substring(0, s.length() - 6) + "." + s.substring(s.length() - 6, s.length()));
        } else {
            return Double.valueOf(s);
        }
    }
}
