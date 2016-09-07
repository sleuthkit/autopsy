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
 * Analyzes database created by browser that stores GEO location info.
 */
class BrowserLocationAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(BrowserLocationAnalyzer.class.getName());
    private static Blackboard blackboard;

    public static void findGeoLocations(Content dataSource, FileManager fileManager,
            IngestJobContext context) {
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        try {
            List<AbstractFile> abstractFiles = fileManager.findFiles(dataSource, "CachedGeoposition%.db"); //NON-NLS

            for (AbstractFile abstractFile : abstractFiles) {
                try {
                    if (abstractFile.getSize() == 0) {
                        continue;
                    }
                    File jFile = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, jFile, context::dataSourceIngestIsCancelled);
                    findGeoLocationsInDB(jFile.toString(), abstractFile);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing Browser Location files", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding Browser Location files", e); //NON-NLS

        }
    }

    @NbBundle.Messages({"BrowserLocationAnalyzer.indexError.message=Failed to index GPS trackpoint artifact for keyword search."})
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
            logger.log(Level.SEVERE, "Error connecting to sql database", e); //NON-NLS
            return;
        }

        try {
            resultSet = statement.executeQuery(
                    "SELECT timestamp, latitude, longitude, accuracy FROM CachedPosition;"); //NON-NLS

            while (resultSet.next()) {
                Long timestamp = Long.valueOf(resultSet.getString("timestamp")) / 1000; //NON-NLS
                double latitude = Double.valueOf(resultSet.getString("latitude")); //NON-NLS
                double longitude = Double.valueOf(resultSet.getString("longitude")); //NON-NLS

                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, moduleName, latitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, moduleName, longitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, timestamp));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName,
                        NbBundle.getMessage(BrowserLocationAnalyzer.class,
                                "BrowserLocationAnalyzer.bbAttribute.browserLocationHistory")));
                //  bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),moduleName, accuracy)); 

                try {
                    // index the artifact for keyword search
                    blackboard.indexArtifact(bba);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactTypeName(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(
                            Bundle.BrowserLocationAnalyzer_indexError_message(), bba.getDisplayName());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error Putting artifacts to Blackboard", e); //NON-NLS
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
