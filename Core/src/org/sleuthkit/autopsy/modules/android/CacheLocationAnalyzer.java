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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

class CacheLocationAnalyzer {

    private String filePath = "";
    private long fileId = 0;
    private java.io.File jFile = null;
    private String moduleName= AndroidModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(CacheLocationAnalyzer.class.getName());
    public void findGeoLocations() {
        List<AbstractFile> absFiles;
        try {
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            absFiles = skCase.findAllFilesWhere("name ='cache.cell'OR name='cache.wifi'"); //get exact file names
            if (absFiles.isEmpty()) {
                return;
            }
            for (AbstractFile AF : absFiles) {
                try {
                    if (AF.getSize() ==0) continue;
                    jFile = new java.io.File(Case.getCurrentCase().getTempDirectory(), AF.getName());
                    ContentUtils.writeToFile(AF,jFile);
                    filePath = jFile.toString(); //path of file as string
                    fileId = AF.getId();
                    findGeoLocationsInFile(filePath, fileId);
                } catch (Exception e) {
                   logger.log(Level.SEVERE, "Error parsing cached Location files", e);
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error finding cached Location files", e);
        }
    }

    private void findGeoLocationsInFile(String filePath, long fId) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        String fileName=filePath.contains("cell")? "cache.cell":"cache.wifi";
        File file = new File(filePath); //cache.cell or cache.wifi
        byte[] bytes; // will temporarily hold bytes to be converted into the correct data types
        Case currentCase = Case.getCurrentCase();
        SleuthkitCase skCase = currentCase.getSleuthkitCase();
        try {
            InputStream inputStream = new FileInputStream(file);
            AbstractFile f = skCase.getAbstractFileById(fId);
            BlackboardArtifact bba;
            
            String latitude; 
            String longitude; 
            String confidence;
            String accuracy; //measure of how accurate the gps location is.

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
                while (new BigInteger(bytes).intValue() != 0) //pass through non important values until the start of accuracy(around 7-10 bytes)
                {
                    inputStream.read(bytes);
                }
                bytes = new byte[3];
                inputStream.read(bytes);
                if (new BigInteger(bytes).intValue()<=0){//This refers to a location that could not be calculated.
                    bytes = new byte[28]; //read rest of the row's bytes
                    inputStream.read(bytes);
                    continue;
                } 
                accuracy=""+new BigInteger(bytes).intValue(); 
                
                bytes = new byte[4];
                inputStream.read(bytes);
                confidence=""+new BigInteger(bytes).intValue();
                
                bytes = new byte[8];
                inputStream.read(bytes);
                latitude=""+toDouble(bytes);
                
                bytes = new byte[8];
                inputStream.read(bytes);
                longitude= ""+toDouble(bytes);
                
                bytes = new byte[8];
                inputStream.read(bytes);
                Long timestamp = new BigInteger(bytes).longValue();
                
                bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(),moduleName,latitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(),moduleName, longitude));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),moduleName, timestamp));
                bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),moduleName, fileName+" Location History"));
                
             //Not storing these for now.
            //    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),moduleName, accuracy));       
            //    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(),moduleName, confidence));
            }
                
        }catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Cached GPS locations to Blackboard", e);
        }
    }

    public static double toDouble(byte[] bytes) {
    return ByteBuffer.wrap(bytes).getDouble();
    }   
}
