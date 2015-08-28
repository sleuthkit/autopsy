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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.MissingResourceException;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

public class GoogleMapLocationAnalyzer implements AndroidAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(GoogleMapLocationAnalyzer.class.getName());
    private static final String[] databaseNames = {"da_destination_history"};

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
            resultSet = statement.executeQuery(
                    "Select time,dest_lat,dest_lng,dest_title,dest_address,source_lat,source_lng FROM destination_history;"); //NON-NLS

            while (resultSet.next()) {
                Long time = Long.valueOf(resultSet.getString("time")) / 1000; //NON-NLS
                String dest_title = resultSet.getString("dest_title"); //NON-NLS
                String dest_address = resultSet.getString("dest_address"); //NON-NLS

                double dest_lat = convertGeo(resultSet.getString("dest_lat")); //NON-NLS
                double dest_lng = convertGeo(resultSet.getString("dest_lng")); //NON-NLS
                double source_lat = convertGeo(resultSet.getString("source_lat")); //NON-NLS
                double source_lng = convertGeo(resultSet.getString("source_lng")); //NON-NLS

                BlackboardArtifact bba = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE);
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY.getTypeID(), moduleName,
                        NbBundle.getMessage(org.sleuthkit.autopsy.modules.android.GoogleMapLocationAnalyzer.class,
                                "GoogleMapLocationAnalyzer.bbAttribute.destination")));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, time));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END.getTypeID(), moduleName, dest_lat));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END.getTypeID(), moduleName, dest_lng));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID(), moduleName, source_lat));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START.getTypeID(), moduleName, source_lng));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, dest_title));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID(), moduleName, dest_address));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), moduleName,
                        NbBundle.getMessage(org.sleuthkit.autopsy.modules.android.GoogleMapLocationAnalyzer.class,
                                "GoogleMapLocationAnalyzer.bbAttribute.googleMapsHistory")));

            }

        } catch (SQLException | NumberFormatException | TskCoreException | MissingResourceException e) {
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

    @Override
    public String[] getDatabaseNames() {
        return databaseNames;
    }

    //add periods 6 decimal places before the end.
    private static double convertGeo(String s) {
        if (s.length() > 6) {
            return Double.valueOf(s.substring(0, s.length() - 6) + "." + s.substring(s.length() - 6, s.length()));
        } else {
            return Double.valueOf(s);
        }
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
