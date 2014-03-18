/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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

package org.sleuthkit.autopsy.fileextmismatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskException;

/**
 * Flags mismatched filename extensions based on file signature.
 */
public class FileExtMismatchIngestModule extends IngestModuleAdapter implements FileIngestModule {
    private static final Logger logger = Logger.getLogger(FileExtMismatchIngestModule.class.getName());   
    private static long processTime = 0;
    private static int messageId = 0;
    private static long numFiles = 0;
    private boolean skipKnown = false;
    private boolean skipNoExt = true;
    private boolean skipTextPlain = false;       
    private IngestServices services;
    private HashMap<String, String[]> SigTypeToExtMap = new HashMap<>();
    
    FileExtMismatchIngestModule() {
    }
            
    @Override
    public void startUp(org.sleuthkit.autopsy.ingest.IngestJobContext context) throws Exception {
        super.startUp(context);
        services = IngestServices.getDefault();           
        FileExtMismatchXML xmlLoader = FileExtMismatchXML.getDefault();
        SigTypeToExtMap = xmlLoader.load();
    }
    
    @Override
    public ResultCode process(AbstractFile abstractFile) {
        // skip non-files
        if ((abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) ||
            (abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {            
            return ResultCode.OK;
        }
        
        // deleted files often have content that was not theirs and therefor causes mismatch
        if ((abstractFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC)) ||
                (abstractFile.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC))) {
            return ResultCode.OK;
        }

        if (skipKnown && (abstractFile.getKnown() == FileKnown.KNOWN)) {
            return ResultCode.OK;
        }
        
        try 
        {
            long startTime = System.currentTimeMillis();
           
            boolean mismatchDetected = compareSigTypeToExt(abstractFile);
            
            processTime += (System.currentTimeMillis() - startTime);
            numFiles++;
                        
            if (mismatchDetected) {
                // add artifact               
                BlackboardArtifact bart = abstractFile.newArtifact(ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED);

                services.fireModuleDataEvent(new ModuleDataEvent(FileExtMismatchDetectorModuleFactory.getModuleName(), ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED, Collections.singletonList(bart)));                
            }
            return ResultCode.OK;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error matching file signature", ex);
            return ResultCode.ERROR;
        }
    }
    
    /**
     * Compare file type for file and extension.
     * @param abstractFile
     * @return false if the two match.  True if there is a mismatch.
     */
    private boolean compareSigTypeToExt(AbstractFile abstractFile) {
        try {
            String currActualExt = abstractFile.getNameExtension();

            // If we are skipping names with no extension
            if (skipNoExt && currActualExt.isEmpty()) {
                return false;
            }

            // find file_sig value.
            // check the blackboard for a file type attribute
            ArrayList<BlackboardAttribute> attributes = abstractFile.getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) {
                String currActualSigType = attribute.getValueString();
                if (skipTextPlain) {
                    if (!currActualExt.isEmpty() && currActualSigType.equals("text/plain")) {
                        return false;
                    }
                }

                //get known allowed values from the map for this type
                String[] allowedExtArray = SigTypeToExtMap.get(currActualSigType);
                if (allowedExtArray != null) {
                    List<String> allowedExtList = Arrays.asList(allowedExtArray);

                    // see if the filename ext is in the allowed list
                    if (allowedExtList != null) {
                        for (String e : allowedExtList) {
                            if (e.equals(currActualExt)) {
                                return false;
                            }
                        }
                        return true; //potential mismatch
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error while getting file signature from blackboard.", ex);
        }

        return false;
    }
    
    @Override
    public void shutDown(boolean ingestJobCancelled) {
        StringBuilder detailsSb = new StringBuilder();
        //details
        detailsSb.append("<table border='0' cellpadding='4' width='280'>");

        detailsSb.append("<tr><td>" + FileExtMismatchDetectorModuleFactory.getModuleName() + "</td></tr>");

        detailsSb.append("<tr><td>Total Processing Time</td><td>").append(processTime).append("</td></tr>\n");
        detailsSb.append("<tr><td>Total Files Processed</td><td>").append(numFiles).append("</td></tr>\n");
        detailsSb.append("</table>");

        services.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, FileExtMismatchDetectorModuleFactory.getModuleName(), "File Extension Mismatch Results", detailsSb.toString()));
    }

    // RJCTODO: Ingest setting
    public void setSkipKnown(boolean flag) {
        skipKnown = flag;
    }

    // RJCTODO: Ingest setting
    public void setSkipNoExt(boolean flag) {
        skipNoExt = flag;
    }        
    
    // RJCTODO: Ingest setting
    public void setSkipTextPlain(boolean flag) {
        skipTextPlain = flag;
    }
}

