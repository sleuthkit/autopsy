/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2013 Basis Technology Corp.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskException;

/**
 *
 */
public class FileExtMismatchIngestModule extends org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile {
    private static FileExtMismatchIngestModule defaultInstance = null;
    private static final Logger logger = Logger.getLogger(FileExtMismatchIngestModule.class.getName());   
    public static final String MODULE_NAME = "File Extension Mismatch Detection";
    public static final String MODULE_DESCRIPTION = "Flags mismatched filename extensions based on file signature.";
    public static final String MODULE_VERSION = Version.getVersion();    
    private static final String ART_NAME = "TSK_MISMATCH";
    private static final String ATTR_NAME = "TSK_FILE_TYPE_EXT_WRONG";
    private static final byte[] ATTR_VALUE_WRONG = {1};

    private static long processTime = 0;
    private static int messageId = 0;
    private static long numFiles = 0;
    private static boolean skipKnown = false;
    
    private int attrId = -1;
    private FileExtMismatchSimpleConfigPanel simpleConfigPanel;
    private FileExtMismatchConfigPanel advancedConfigPanel;
    private IngestServices services;
    private HashMap<String, String[]> SigTypeToExtMap = new HashMap<>();
    
    // Private to ensure Singleton status
    private FileExtMismatchIngestModule() {

    }
    
    // File-level ingest modules are currently singleton -- this is required
    public static synchronized FileExtMismatchIngestModule getDefault() {
        //defaultInstance is a private static class variable
        if (defaultInstance == null) {
            defaultInstance = new FileExtMismatchIngestModule();
        }
        return defaultInstance;
    }

    
    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
        
        // Add a new artifact and attribute type
        
        SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();        
        
        // see if the type already exists in the blackboard.
        try {
            attrId = sleuthkitCase.getAttrTypeID(ATTR_NAME);
        } catch (TskCoreException ex) {
            // create it if not
            try {
                attrId = sleuthkitCase.addAttrType(ATTR_NAME, "Flag for detected mismatch between filename extension and file signature.");
            } catch (TskCoreException ex1) {
                logger.log(Level.SEVERE, "Error adding artifact and attribute types: " + ex1.getLocalizedMessage());
                attrId = -1;
            }
        }        

        // Load mapping
        FileExtMismatchXML xmlLoader = FileExtMismatchXML.getDefault();
        SigTypeToExtMap = xmlLoader.load();

    }
    
    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile> pipelineContext, AbstractFile abstractFile) {
        // skip non-files
        if ((abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) ||
            (abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {         
            
            return ProcessResult.OK;
        }
       
        if (skipKnown && ((abstractFile.getKnown() == FileKnown.KNOWN) || (abstractFile.getKnown() == FileKnown.BAD))) {
            return ProcessResult.OK;
        }
        
        try 
        {
            long startTime = System.currentTimeMillis();
           
            boolean flag = compareSigTypeToExt(abstractFile);
            
            processTime += (System.currentTimeMillis() - startTime);
            numFiles++;
                        
            if (flag) {
                // add artifact               
                BlackboardArtifact bart = abstractFile.newArtifact(ARTIFACT_TYPE.TSK_MISMATCH_DETECTED);
                BlackboardAttribute batt = new BlackboardAttribute(attrId, MODULE_NAME, "", ATTR_VALUE_WRONG);
                bart.addAttribute(batt);

                services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_MISMATCH_DETECTED, Collections.singletonList(bart)));                
            }
            return ProcessResult.OK;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error matching file signature", ex);
            return ProcessResult.ERROR;
        }
    }
    
    private boolean compareSigTypeToExt(AbstractFile abstractFile) {
        try {
            String extStr = "";
            int i = abstractFile.getName().lastIndexOf(".");
            if ((i > -1) && ((i + 1) < abstractFile.getName().length())) {
                extStr = abstractFile.getName().substring(i + 1).toLowerCase();
            }
            
            // find file_sig value.
            // getArtifacts by type doesn't seem to work, so get all artifacts
            ArrayList<BlackboardArtifact> artList = abstractFile.getAllArtifacts();

            for (BlackboardArtifact art : artList) {
                List<BlackboardAttribute> atrList = art.getAttributes();
                for (BlackboardAttribute att : atrList) {
                    if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID()) {                        

                        //get known allowed values from the map for this type
                        String[] slist = SigTypeToExtMap.get(att.getValueString());
                        if (slist != null) {
                            List<String> allowedExtList = Arrays.asList(slist);

                            // see if the filename ext is in the allowed list
                            if (allowedExtList != null) {
                                for (String e : allowedExtList) {
                                    if (e.equals(extStr)) {
                                        return false;
                                    }
                                }
                                return true; //potential mismatch
                            }
                        }
                    }
                }                
            }
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
        }
        return false;
    }
    
    @Override
    public void complete() {
        StringBuilder detailsSb = new StringBuilder();
        //details
        detailsSb.append("<table border='0' cellpadding='4' width='280'>");

        detailsSb.append("<tr><td>"+MODULE_DESCRIPTION+"</td></tr>");

        detailsSb.append("<tr><td>Total Processing Time</td><td>").append(processTime).append("</td></tr>\n");
        detailsSb.append("<tr><td>Total Files Processed</td><td>").append(numFiles).append("</td></tr>\n");
        detailsSb.append("</table>");

        services.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "File Extension Mismatch Results", detailsSb.toString()));
    }

    @Override
    public void stop() {
        //do nothing
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public boolean hasSimpleConfiguration() {
        return true;
    }
    
    @Override
    public boolean hasAdvancedConfiguration() {
        return true;
    }
    
    @Override
    public javax.swing.JPanel getSimpleConfiguration(String context) {
        if (simpleConfigPanel == null) {
           simpleConfigPanel = new FileExtMismatchSimpleConfigPanel();  
        }
        
        return simpleConfigPanel;
    }    
    
    @Override
    public javax.swing.JPanel getAdvancedConfiguration(String context) {
        getPanel().load();
        return getPanel();
    }

    private FileExtMismatchConfigPanel getPanel() {
        if (advancedConfigPanel == null) {
            advancedConfigPanel = new FileExtMismatchConfigPanel();
        }
        return advancedConfigPanel;
    }

    @Override
    public void saveAdvancedConfiguration() {
        getPanel().store();
    }    
    
    @Override
    public boolean hasBackgroundJobsRunning() {
        // we're single threaded...
        return false;
    }
    
    public static void setSkipKnown(boolean flag) {
        skipKnown = flag;
    }
    
    public static String getAttributeName() {
        return ATTR_NAME;
    }
    
}

