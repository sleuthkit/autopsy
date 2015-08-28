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

/**
 * Analyzes database created by browser that stores GEO location info.
 */
public class BrowserLocationAnalyzer implements AndroidAnalyzer {

    private static final String moduleName = org.sleuthkit.autopsy.modules.android.AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(org.sleuthkit.autopsy.modules.android.BrowserLocationAnalyzer.class.getName());
    private static final String[] dataBaseNames = {"CachedGeoposition%.db"};

    @Override
    public void findInDB(Connection connection, AbstractFile abstractFile) {
        ResultSet resultSet = null;
        Statement statement = null;
        try {
            statement = connection.createStatement();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error connecting to sql database", e); //NON-NLS
            return;
        }
        try {
            resultSet = statement.executeQuery(
                    "Select timestamp, latitude, longitude, accuracy FROM CachedPosition;"); //NON-NLS

            while (resultSet.next()) {
                Long timestamp = Long.valueOf(resultSet.getString("timestamp")) / 1000; //NON-NLS
                double latitude = Double.valueOf(resultSet.getString("latitude")); //NON-NLS
                double longitude = Double.valueOf(resultSet.getString("longitude")); //NON-NLS

                BlackboardArtifact bba = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), moduleName, latitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), moduleName, longitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), moduleName, timestamp));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), moduleName,
                        NbBundle.getMessage(org.sleuthkit.autopsy.modules.android.BrowserLocationAnalyzer.class,
                                "BrowserLocationAnalyzer.bbAttribute.browserLocationHistory")));
                //  bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),moduleName, accuracy)); 
            }
        } catch (SQLException | NumberFormatException | TskCoreException | MissingResourceException e) {
            logger.log(Level.SEVERE, "Error Putting artifacts to Blackboard", e); //NON-NLS
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
                statement.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error closing database", e); //NON-NLS
            }
        }
    }

    @Override
    public String[] getDatabaseNames() {
        return dataBaseNames;
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
