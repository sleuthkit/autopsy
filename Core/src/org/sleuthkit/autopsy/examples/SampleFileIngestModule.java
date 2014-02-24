/*
* Sample module in the public domain.  Feel free to use this as a template
* for your modules.
* 
*  Contact: Brian Carrier [carrier <at> sleuthkit [dot] org]
*
*  This is free and unencumbered software released into the public domain.
*  
*  Anyone is free to copy, modify, publish, use, compile, sell, or
*  distribute this software, either in source code form or as a compiled
*  binary, for any purpose, commercial or non-commercial, and by any
*  means.
*  
*  In jurisdictions that recognize copyright laws, the author or authors
*  of this software dedicate any and all copyright interest in the
*  software to the public domain. We make this dedication for the benefit
*  of the public at large and to the detriment of our heirs and
*  successors. We intend this dedication to be an overt act of
*  relinquishment in perpetuity of all present and future rights to this
*  software under copyright law.
*  
*  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
*  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
*  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
*  IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
*  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
*  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
*  OTHER DEALINGS IN THE SOFTWARE. 
*/

package org.sleuthkit.autopsy.examples;

import org.apache.log4j.Logger;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 * This is a sample and simple module.  It is a file-level ingest module, meaning
 * that it will get called on each file in the disk image / logical file set.
 * It does a stupid calculation of the number of null bytes in the beginning of the
 * file in order to show the basic flow. 
 * 
 * Autopsy has been hard coded to ignore this module based on the it's package name.
 * IngestModuleLoader will not load things from the org.sleuthkit.autopsy.examples package.
 * Either change the package or the loading code to make this module actually run. 
 */
 class SampleFileIngestModule extends org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile {
    private int attrId = -1;
    private static SampleFileIngestModule defaultInstance = null;
    
    // Private to ensure Singleton status
    private SampleFileIngestModule() {
    }
    
    // File-level ingest modules are currently singleton -- this is required
    public static synchronized SampleFileIngestModule getDefault() {
        //defaultInstance is a private static class variable
        if (defaultInstance == null) {
            defaultInstance = new SampleFileIngestModule();
        }
        return defaultInstance;
    }

    
    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
        /* For this demo, we are going to make a private attribute to post our
         * results to the blackbaord with. There are many standard blackboard artifact
         * and attribute types and you should first consider using one of those before
         * making private ones because other modules won't know about provate ones.
         * Because our demo has results that have no real value, we do not have an 
         * official attribute for them. 
         */
        Case case1 = Case.getCurrentCase();
        SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();
        
        // see if the type already exists in the blackboard.
        try {
            attrId = sleuthkitCase.getAttrTypeID("ATTR_SAMPLE");
        } catch (TskCoreException ex) {
            // create it if not
            try {
                attrId = sleuthkitCase.addAttrType("ATTR_SAMPLE", "Sample Attribute");
            } catch (TskCoreException ex1) {
                Logger log = Logger.getLogger(SampleFileIngestModule.class);
                log.fatal("Error adding attribute type: " + ex1.getLocalizedMessage());
                attrId = -1;
            }
        }
    }
    
    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile> pipelineContext, AbstractFile abstractFile) {
        // skip non-files
        if ((abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) ||
            (abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {
            return ProcessResult.OK;
        }
        
        // skip NSRL / known files
        if (abstractFile.getKnown() == TskData.FileKnown.KNOWN) {
            return ProcessResult.OK;
        }
    
        
        /* Do a non-sensical calculation of the number of 0x00 bytes
         * in the first 1024-bytes of the file.  This is for demo
         * purposes only.
         */
        try {
            byte buffer[] = new byte[1024];
            int len = abstractFile.read(buffer, 0, 1024);
            int count = 0;
            for (int i = 0; i < len; i++) {
                if (buffer[i] == 0x00) {
                    count++;
                }
            }
            
            if (attrId != -1) {
                // Make an attribute using the ID for the private type that we previously created.
                BlackboardAttribute attr = new BlackboardAttribute(attrId, getName(), count);

                /* add it to the general info artifact.  In real modules, you would likely have
                 * more complex data types and be making more specific artifacts.
                 */
                BlackboardArtifact art = abstractFile.getGenInfoArtifact();
                art.addAttribute(attr);
            }
                
            return ProcessResult.OK;
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            return ProcessResult.ERROR;
        }
    }
    

    @Override
    public void complete() {
        
    }

    @Override
    public void stop() {
        
    }

    @Override
    public String getVersion() {
        return "1.0";
    }
    
    @Override
    public String getName() {
        return "SampleFileIngestModule";
    }

    @Override
    public String getDescription() {
        return "Doesn't do much";
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        // we're single threaded...
        return false;
    }
}
