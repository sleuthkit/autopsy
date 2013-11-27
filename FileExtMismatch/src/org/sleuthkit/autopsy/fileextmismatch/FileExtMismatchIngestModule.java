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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
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
    public final static String MODULE_NAME = "File Extension Mismatch Detection";
    public final static String MODULE_DESCRIPTION = "Flags mismatched filename extensions based on file signature.";
    public final static String MODULE_VERSION = Version.getVersion();    
    private static final String ATTR_NAME = "TSK_FILE_TYPE_EXT_WRONG";
    private static final byte[] ATTR_VALUE_WRONG = {1};
    private static final Logger logger = Logger.getLogger(FileExtMismatchIngestModule.class.getName());
    private static long processTime = 0;
    private static int messageId = 0;
    private static long numFiles = 0;
    private static boolean skipKnown = false;
    
    private int attrId = -1;    
    //private FileTypeIdSimpleConfigPanel simpleConfigPanel;
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
        
        // Add a new attribute type
        
        SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();        
        
        // see if the type already exists in the blackboard.
        try {
            attrId = sleuthkitCase.getAttrTypeID(ATTR_NAME);
        } catch (TskCoreException ex) {
            // create it if not
            try {
                attrId = sleuthkitCase.addAttrType(ATTR_NAME, "Flag for detected mismatch between filename extension and file signature.");
            } catch (TskCoreException ex1) {
                logger.log(Level.SEVERE, "Error adding attribute type: " + ex1.getLocalizedMessage());
                attrId = -1;
            }
        }        
        
        // Set up default mapping (eventually this will be loaded from a config file)
        String[] exts = {"doc", "docx", "dot", "dotx", "xls", "xlsx", "ppt", "pot", "pptx", "potx"};
        SigTypeToExtMap.put("application/x-msoffice", exts);
        String[] exts2 = {"jpg","jpeg"};
        SigTypeToExtMap.put("image/jpeg", exts2);
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
                BlackboardArtifact bart = abstractFile.newArtifact(ARTIFACT_TYPE.TSK_GEN_INFO);
                BlackboardAttribute batt = new BlackboardAttribute(attrId, MODULE_NAME, "", ATTR_VALUE_WRONG);
                bart.addAttribute(batt);

                services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_GEN_INFO, Collections.singletonList(bart)));
            }
            return ProcessResult.OK;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error matching file signature", ex);
            return ProcessResult.ERROR;
        }
    }
    
    private boolean compareSigTypeToExt(AbstractFile abstractFile) {
        try {
            int i = abstractFile.getName().lastIndexOf(".");
            if ((i > -1) && ((i + 1) < abstractFile.getName().length())) {
                String extStr = abstractFile.getName().substring(i + 1);
            
                // find file_sig value.
                ArrayList<BlackboardArtifact> artList = abstractFile.getArtifacts(ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID());
                for (BlackboardArtifact art : artList) {
                    List<BlackboardAttribute> atrList = art.getAttributes();
                    for (BlackboardAttribute att : atrList) {
                        if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID()) {                        
                                
                            //get known allowed values from the map for this type
                            List<String> allowedExtList = Arrays.asList(SigTypeToExtMap.get(att.getValueString()));
                            
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
        return false;
    }
    
//    @Override
//    public javax.swing.JPanel getSimpleConfiguration(String context) {
//        if (simpleConfigPanel == null) {
//           simpleConfigPanel = new FileTypeIdSimpleConfigPanel();  
//        }
//        
//        return simpleConfigPanel;
//    }    
    
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

