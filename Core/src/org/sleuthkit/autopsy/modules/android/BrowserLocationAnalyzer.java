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

class BrowserLocationAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(BrowserLocationAnalyzer.class.getName());

    public static void findGeoLocations() {
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            List<AbstractFile> abstractFiles = skCase.findAllFilesWhere("name LIKE 'CachedGeoposition%.db'"); //get exact file names

            for (AbstractFile abstractFile : abstractFiles) {
                try {
                    if (abstractFile.getSize() == 0) {
                        continue;
                    }
                    File jFile = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, jFile);
                    findGeoLocationsInDB(jFile.toString(), abstractFile);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing Browser Location files", e);
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding Browser Location files", e);

        }
    }

    private static void findGeoLocationsInDB(String DatabasePath, AbstractFile f) {
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
            logger.log(Level.SEVERE, "Error connecting to sql database", e);
            return;
        }

        try {
            resultSet = statement.executeQuery(
                    "Select timestamp, latitude, longitude, accuracy FROM CachedPosition;");

            while (resultSet.next()) {
                Long timestamp = Long.valueOf(resultSet.getString("timestamp")) / 1000;
                double latitude = Double.valueOf(resultSet.getString("latitude"));
                double longitude = Double.valueOf(resultSet.getString("longitude"));

                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), moduleName, latitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), moduleName, longitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, timestamp));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), moduleName, "Browser Location History"));
                //  bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),moduleName, accuracy)); 
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error Putting artifacts to Blackboard", e);
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
