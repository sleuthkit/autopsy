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
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
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
 * Parses cache files that Android maintains for Wifi and cell towers. Adds GPS
 * points to blackboard.
 */
class CacheLocationAnalyzer {

    private static final String moduleName = AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(CacheLocationAnalyzer.class.getName());
    private static Blackboard blackboard;

    /**
     * cache.cell stores mobile tower GPS locations and cache.wifi stores GPS
     * and MAC info from Wifi points.
     */
    public static void findGeoLocations(Content dataSource, FileManager fileManager,
            IngestJobContext context) {
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        try {
            List<AbstractFile> abstractFiles = fileManager.findFiles(dataSource, "cache.cell"); //NON-NLS
            abstractFiles.addAll(fileManager.findFiles(dataSource, "cache.wifi")); //NON-NLS

            for (AbstractFile abstractFile : abstractFiles) {
                try {
                    if (abstractFile.getSize() == 0) {
                        continue;
                    }
                    File jFile = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, jFile, context::dataSourceIngestIsCancelled);

                    findGeoLocationsInFile(jFile, abstractFile);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error parsing cached Location files", e); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding cached Location files", e); //NON-NLS
        }
    }

    @Messages({"CacheLocationAnalyzer.indexError.message=Failed to index GPS trackpoint artifact for keyword search."})
    private static void findGeoLocationsInFile(File file, AbstractFile f) {
        byte[] bytes; // will temporarily hold bytes to be converted into the correct data types

        try {
            InputStream inputStream = new FileInputStream(file);

            bytes = new byte[2]; // version
            inputStream.read(bytes);

            bytes = new byte[2];
            inputStream.read(bytes); //number of location entries

            int iterations = new BigInteger(bytes).intValue();

            for (int i = 0; i < iterations; i++) { //loop through every entry
                bytes = new byte[2];
                inputStream.read(bytes);

                bytes = new byte[1];
                inputStream.read(bytes);
                while (new BigInteger(bytes).intValue() != 0) { //pass through non important values until the start of accuracy(around 7-10 bytes)
                    if (0 > inputStream.read(bytes)) {
                        break; /// we've passed the end of the file, so stop
                    }
                }
                bytes = new byte[3];
                inputStream.read(bytes);
                if (new BigInteger(bytes).intValue() <= 0) {//This refers to a location that could not be calculated.
                    bytes = new byte[28]; //read rest of the row's bytes
                    inputStream.read(bytes);
                    continue;
                }
                String accuracy = "" + new BigInteger(bytes).intValue();

                bytes = new byte[4];
                inputStream.read(bytes);
                String confidence = "" + new BigInteger(bytes).intValue();

                bytes = new byte[8];
                inputStream.read(bytes);
                double latitude = toDouble(bytes);

                bytes = new byte[8];
                inputStream.read(bytes);
                double longitude = toDouble(bytes);

                bytes = new byte[8];
                inputStream.read(bytes);
                Long timestamp = new BigInteger(bytes).longValue() / 1000;

                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, moduleName, latitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, moduleName, longitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, timestamp));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName,
                        NbBundle.getMessage(CacheLocationAnalyzer.class,
                                "CacheLocationAnalyzer.bbAttribute.fileLocationHistory",
                                file.getName())));

                //Not storing these for now.
                //    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),moduleName, accuracy));       
                //    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(),moduleName, confidence));
                try {
                    // index the artifact for keyword search
                    blackboard.indexArtifact(bba);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(
                            Bundle.CacheLocationAnalyzer_indexError_message(), bba.getDisplayName());
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Cached GPS locations to Blackboard", e); //NON-NLS
        }
    }

    private static double toDouble(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getDouble();
    }
}
