/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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

package org.sleuthkit.autopsy.filetypeid;

import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskException;

/**
 * Detects the type of a file based on signature (magic) values.
 * Posts results to the blackboard.
 */
 public class FileTypeIdIngestModule extends org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile {
    private static FileTypeIdIngestModule defaultInstance = null;
    public final static String MODULE_NAME = NbBundle.getMessage(FileTypeIdIngestModule.class,
                                                                 "FileTypeIdIngestModule.moduleName.text");
    public final static String MODULE_DESCRIPTION = NbBundle.getMessage(FileTypeIdIngestModule.class,
                                                                        "FileTypeIdIngestModule.moduleDesc.text");
    public final static String MODULE_VERSION = Version.getVersion();    
    private static final Logger logger = Logger.getLogger(FileTypeIdIngestModule.class.getName());
    private static long matchTime = 0;
    private static int messageId = 0;
    private static long numFiles = 0;
    private static boolean skipKnown = true;
    private static long MIN_FILE_SIZE = 512;
    
    private FileTypeIdSimpleConfigPanel simpleConfigPanel;
    private IngestServices services;
    
    // The detector. Swap out with a different implementation of FileTypeDetectionInterface as needed.
    // If desired in the future to be more knowledgable about weird files or rare formats, we could 
    // actually have a list of detectors which are called in order until a match is found.
    private FileTypeDetectionInterface detector = new TikaFileTypeDetector(); 
    //private FileTypeDetectionInterface detector = new JMimeMagicFileTypeDetector();
    //private FileTypeDetectionInterface detector = new MimeUtilFileTypeDetector(); 
    
    // Private to ensure Singleton status
    private FileTypeIdIngestModule() {
    }
    
    // File-level ingest modules are currently singleton -- this is required
    public static synchronized FileTypeIdIngestModule getDefault() {
        //defaultInstance is a private static class variable
        if (defaultInstance == null) {
            defaultInstance = new FileTypeIdIngestModule();
        }
        return defaultInstance;
    }

    
    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
        services = IngestServices.getDefault();
    }
    
    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile> pipelineContext, AbstractFile abstractFile) {
        // skip non-files
        if ((abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) ||
            (abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {         
            
            return ProcessResult.OK;
        }
       
        if (skipKnown && (abstractFile.getKnown() == FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }
        
        if (abstractFile.getSize() < MIN_FILE_SIZE) {
             return ProcessResult.OK;        
        }        
        
        try 
        {
            long startTime = System.currentTimeMillis();
            FileTypeDetectionInterface.FileIdInfo fileId = detector.attemptMatch(abstractFile);
            matchTime += (System.currentTimeMillis() - startTime);
            numFiles++;
            
            if (!fileId.type.isEmpty()) {
                // add artifact
                BlackboardArtifact bart = abstractFile.getGenInfoArtifact();
                BlackboardAttribute batt = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID(), MODULE_NAME, fileId.type);
                bart.addAttribute(batt);

                // we don't fire the event because we just updated TSK_GEN_INFO, which isn't displayed in the tree and is vague.
            }
            return ProcessResult.OK;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error matching file signature", ex);
            return ProcessResult.ERROR;
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Error matching file signature", e);
            return ProcessResult.ERROR;
        }
    }
    

    @Override
    public void complete() {
        StringBuilder detailsSb = new StringBuilder();
        //details
        detailsSb.append("<table border='0' cellpadding='4' width='280'>");

        detailsSb.append("<tr><td>"+MODULE_DESCRIPTION+"</td></tr>");

        detailsSb.append("<tr><td>")
                 .append(NbBundle.getMessage(this.getClass(), "FileTypeIdIngestModule.complete.totalProcTime"))
                 .append("</td><td>").append(matchTime).append("</td></tr>\n");
        detailsSb.append("<tr><td>")
                 .append(NbBundle.getMessage(this.getClass(), "FileTypeIdIngestModule.complete.totalFiles"))
                 .append("</td><td>").append(numFiles).append("</td></tr>\n");
        detailsSb.append("</table>");

        services.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this,
                                                         NbBundle.getMessage(this.getClass(),
                                                                             "FileTypeIdIngestModule.complete.srvMsg.text"),
                                                         detailsSb.toString()));
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
    public javax.swing.JPanel getSimpleConfiguration(String context) {
        if (simpleConfigPanel == null) {
           simpleConfigPanel = new FileTypeIdSimpleConfigPanel();  
        }
        
        return simpleConfigPanel;
    }    
    
    @Override
    public boolean hasBackgroundJobsRunning() {
        // we're single threaded...
        return false;
    }
    
    public static void setSkipKnown(boolean flag) {
        skipKnown = flag;
    }
    
    /**
     * Validate if a given mime type is in the detector's registry.
     * @param mimeType Full string of mime type, e.g. "text/html"
     * @return true if detectable
     */
    public static boolean isMimeTypeDetectable(String mimeType) {
        FileTypeDetectionInterface detector = new TikaFileTypeDetector();         
        return detector.isMimeTypeDetectable(mimeType);
    }    
    
}