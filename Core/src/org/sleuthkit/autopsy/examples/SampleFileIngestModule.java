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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 * Sample file ingest module that doesn't do much. Demonstrates per ingest job
 * module settings, use of a subset of the available ingest services and
 * thread-safe sharing of per ingest job data.
 */
class SampleFileIngestModule implements FileIngestModule {

    private int filesFound;
    private static final Logger logger = Logger.getLogger(NbBundle.getMessage(SampleFileIngestModuleFactory.class, "SampleFileIngestModuleFactory.moduleName"));;

    SampleFileIngestModule(SampleModuleIngestJobSettings settings) {
    }

    @Override
    /*
     Where any setup and configuration is done
     'context' is an instance of org.sleuthkit.autopsy.ingest.IngestJobContext.
     See: http://sleuthkit.org/autopsy/docs/api-docs/3.1/classorg_1_1sleuthkit_1_1autopsy_1_1ingest_1_1_ingest_job_context.html
     TODO: Add any setup code that you need here.
     */
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.filesFound = 0;
    }

    @Override
    /*
     Where the analysis is done.  Each file will be passed into here.
     The 'file' object being passed in is of type org.sleuthkit.datamodel.AbstractFile.
     See: http://www.sleuthkit.org/sleuthkit/docs/jni-docs/4.3/classorg_1_1sleuthkit_1_1datamodel_1_1_abstract_file.html
     TODO: Add your analysis code in here.
     */
    public IngestModule.ProcessResult process(AbstractFile file) {

        // Skip anything other than actual file system files.
        if ((file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                || (file.isFile() == false)) {
            return IngestModule.ProcessResult.OK;
        }
        /*
         This will work in 4.0.1 and beyond
         Use blackboard class to index blackboard artifacts for keyword search
         Blackboard blackboard = Case.getCurrentCase().getServices().getBlackboard()
         */
        //For an example, we will flag files with .txt in the name and make a blackboard artifact.
        if (file.getName().toLowerCase().endsWith(".txt")) {
            this.filesFound++;
            try {
                BlackboardArtifact art = file.newArtifact(ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                BlackboardAttribute attr = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME,
                        NbBundle.getMessage(SampleFileIngestModuleFactory.class, "SampleFileIngestModuleFactory.moduleName"), "Text Files");
                art.addAttribute(attr);
                /*
                 This will work in 4.0.1 and beyond
                 try {
                 //index the artifact for keyword search
                 blackboard.indexArtifact(art);
                 }
                 catch (Blackboard.BlackboardException ex) {
                 logger.log(Level.SEVERE, "Error indexing artifact " + art.getDisplayName());
                 }
                 */
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }

            //Fire an event to notify the UI and others that there is a new artifact
            IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(NbBundle.getMessage(SampleFileIngestModuleFactory.class, "SampleFileIngestModuleFactory.moduleName"),
                    ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT));
            /*
             For the example (this wouldn't be needed normally), we'll query the blackboard for data that was added
             by other modules. We then iterate over its attributes.  We'll just print them, but you would probably
             want to do something with them.
             */
            try {
                List<BlackboardArtifact> artifactList = file.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                for (BlackboardArtifact artifact : artifactList) {
                    List<BlackboardAttribute> attributeList = artifact.getAttributes();
                    for (BlackboardAttribute attribute : attributeList) {
                        logger.log(Level.INFO, attribute.toString());
                    }
                }
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }

        }
        //To further the example, this code will read the contents of the file
        //and count the number of bytes
        try {
            ReadContentInputStream inputStream = new ReadContentInputStream(file);
            byte buffer[] = new byte[1024];
            int totLen = 0;
            int len = inputStream.read(buffer);
            while (len != -1) {
                totLen += len;
                len = inputStream.read(buffer);
            }
            return IngestModule.ProcessResult.OK;

        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error processing file (id = " + file.getId() + ")", ex);
            return IngestModule.ProcessResult.ERROR;
        }
    }

    @Override
    public void shutDown() {
        IngestMessage message = IngestMessage.createMessage(
                IngestMessage.MessageType.DATA, NbBundle.getMessage(SampleFileIngestModuleFactory.class, "SampleFileIngestModuleFactory.moduleName"),
                this.filesFound + " files found");
        IngestServices.getInstance().postMessage(message);
    }
}
