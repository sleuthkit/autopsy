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

import java.util.HashMap;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 * Sample file ingest module that doesn't do much. Demonstrates per ingest job
 * module settings, use of a subset of the available ingest services and
 * thread-safe sharing of per ingest job resources.
 * <p>
 * IMPORTANT TIP: This sample data source ingest module directly implements
 * FileIngestModule, which extends IngestModule. A practical alternative,
 * recommended if you do not need to provide implementations of all of the
 * IngestModule methods, is to extend the abstract class IngestModuleAdapter to
 * get default "do nothing" implementations of the IngestModule methods.
 */
class SampleFileIngestModule implements FileIngestModule {

    private static final HashMap<Long, Integer> moduleReferenceCountsForIngestJobs = new HashMap<>();
    private static final HashMap<Long, Long> artifactCountsForIngestJobs = new HashMap<>();
    private static long messageCount = 0;
    private static int attrId = -1;
    private final boolean skipKnownFiles;
    private IngestJobContext context = null;

    SampleFileIngestModule(SampleModuleIngestJobSettings settings) {
        this.skipKnownFiles = settings.skipKnownFiles();
    }

    /**
     * Invoked by Autopsy to allow an ingest module instance to set up any
     * internal data structures and acquire any private resources it will need
     * during an ingest job.
     * <p>
     * Autopsy will generally use several instances of an ingest module for each
     * ingest job it performs. Completing an ingest job entails processing a
     * single data source (e.g., a disk image) and all of the files from the
     * data source, including files extracted from archives and any unallocated
     * space (made to look like a series of files). The data source is passed
     * through one or more pipelines of data source ingest modules. The files
     * are passed through one or more pipelines of file ingest modules.
     * <p>
     * Autopsy may use multiple threads to complete an ingest job, but it is
     * guaranteed that there will be no more than one module instance per
     * thread. However, if the module instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release those resources
     * correctly. Also, more than one ingest job may be in progress at any given
     * time. This must also be taken into consideration when sharing resources
     * between module instances.
     * <p>
     * An ingest module that does not require initialization may extend the
     * abstract IngestModuleAdapter class to get a default "do nothing"
     * implementation of this method.
     *
     * @param context Provides data and services specific to the ingest job and
     * the ingest pipeline of which the module is a part.
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        synchronized (SampleFileIngestModule.class) {
            if (attrId == -1) {
                // For this sample, make a new attribute type to use to post 
                // results to the blackboard. There are many standard blackboard 
                // artifact and attribute types and you should use them instead
                // creating new ones to facilitate use of your results by other
                // modules.
                Case autopsyCase = Case.getCurrentCase();
                SleuthkitCase sleuthkitCase = autopsyCase.getSleuthkitCase();

                // See if the attribute type has already been defined.
                try {
                    attrId = sleuthkitCase.getAttrTypeID("ATTR_SAMPLE");
                } catch (TskCoreException e) {
                    // If not, create the the attribute type.
                    try {
                        attrId = sleuthkitCase.addAttrType("ATTR_SAMPLE", "Sample Attribute");
                    } catch (TskCoreException ex) {
                        IngestServices ingestServices = IngestServices.getInstance();
                        Logger logger = ingestServices.getLogger(SampleIngestModuleFactory.getModuleName());
                        logger.log(Level.SEVERE, "Failed to create blackboard attribute", ex);
                        attrId = -1;
                        throw new IngestModuleException(ex.getLocalizedMessage());
                    }
                }
            }
        }

        // This method is thread-safe with per ingest job reference counting.        
        initBlackboardPostCount(context.getJobId());
    }

    /**
     * Processes a file.
     *
     * @param file The file.
     * @return A result code indicating success or failure of the processing.
     */
    @Override
    public IngestModule.ProcessResult process(AbstractFile file) {
        if (attrId != -1) {
            return IngestModule.ProcessResult.ERROR;
        }

        // Skip anything other than actual file system files.
        if ((file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {
            return IngestModule.ProcessResult.OK;
        }

        // Skip NSRL / known files.
        if (skipKnownFiles && file.getKnown() == TskData.FileKnown.KNOWN) {
            return IngestModule.ProcessResult.OK;
        }

        // Do a nonsensical calculation of the number of 0x00 bytes
        // in the first 1024-bytes of the file.  This is for demo
        // purposes only.
        try {
            byte buffer[] = new byte[1024];
            int len = file.read(buffer, 0, 1024);
            int count = 0;
            for (int i = 0; i < len; i++) {
                if (buffer[i] == 0x00) {
                    count++;
                }
            }

            // Make an attribute using the ID for the attribute type that 
            // was previously created.
            BlackboardAttribute attr = new BlackboardAttribute(attrId, SampleIngestModuleFactory.getModuleName(), count);

            // Add the to the general info artifact for the file. In a
            // real module, you would likely have more complex data types 
            // and be making more specific artifacts.
            BlackboardArtifact art = file.getGenInfoArtifact();
            art.addAttribute(attr);

            // Thread-safe.
            addToBlackboardPostCount(context.getJobId(), 1L);

            // Fire an event to notify any listeners for blackboard postings.
            ModuleDataEvent event = new ModuleDataEvent(SampleIngestModuleFactory.getModuleName(), ARTIFACT_TYPE.TSK_GEN_INFO);
            IngestServices.getInstance().fireModuleDataEvent(event);

            return IngestModule.ProcessResult.OK;

        } catch (TskCoreException ex) {
            IngestServices ingestServices = IngestServices.getInstance();
            Logger logger = ingestServices.getLogger(SampleIngestModuleFactory.getModuleName());
            logger.log(Level.SEVERE, "Error processing file (id = " + file.getId() + ")", ex);
            return IngestModule.ProcessResult.ERROR;
        }
    }

    /**
     * Invoked by Autopsy when an ingest job is completed, before the ingest
     * module instance is discarded. The module should respond by doing things
     * like releasing private resources, submitting final results, and posting a
     * final ingest message.
     * <p>
     * Autopsy will generally use several instances of an ingest module for each
     * ingest job it performs. Completing an ingest job entails processing a
     * single data source (e.g., a disk image) and all of the files from the
     * data source, including files extracted from archives and any unallocated
     * space (made to look like a series of files). The data source is passed
     * through one or more pipelines of data source ingest modules. The files
     * are passed through one or more pipelines of file ingest modules.
     * <p>
     * Autopsy may use multiple threads to complete an ingest job, but it is
     * guaranteed that there will be no more than one module instance per
     * thread. However, if the module instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release those resources
     * correctly. Also, more than one ingest job may be in progress at any given
     * time. This must also be taken into consideration when sharing resources
     * between module instances.
     * <p>
     * An ingest module that does not require initialization may extend the
     * abstract IngestModuleAdapter class to get a default "do nothing"
     * implementation of this method.
     */
    @Override
    public void shutDown(boolean ingestJobCancelled) {
        // This method is thread-safe with per ingest job reference counting.
        reportBlackboardPostCount(context.getJobId());
    }

    synchronized static void initBlackboardPostCount(long ingestJobId) {
        Integer moduleReferenceCount;
        if (!moduleReferenceCountsForIngestJobs.containsKey(ingestJobId)) {
            moduleReferenceCount = 1;
            artifactCountsForIngestJobs.put(ingestJobId, 0L);
        } else {
            moduleReferenceCount = moduleReferenceCountsForIngestJobs.get(ingestJobId);
            ++moduleReferenceCount;
        }
        moduleReferenceCountsForIngestJobs.put(ingestJobId, moduleReferenceCount);
    }

    synchronized static void addToBlackboardPostCount(long ingestJobId, long countToAdd) {
        Long fileCount = artifactCountsForIngestJobs.get(ingestJobId);
        fileCount += countToAdd;
        artifactCountsForIngestJobs.put(ingestJobId, fileCount);
    }

    synchronized static void reportBlackboardPostCount(long ingestJobId) {
        Integer moduleReferenceCount = moduleReferenceCountsForIngestJobs.remove(ingestJobId);
        --moduleReferenceCount;
        if (moduleReferenceCount == 0) {
            Long filesCount = artifactCountsForIngestJobs.remove(ingestJobId);
            String msgText = String.format("Posted %d times to the blackboard", filesCount);
            IngestMessage message = IngestMessage.createMessage(
                    ++messageCount,
                    IngestMessage.MessageType.INFO,
                    SampleIngestModuleFactory.getModuleName(),
                    msgText);
            IngestServices.getInstance().postMessage(message);
        } else {
            moduleReferenceCountsForIngestJobs.put(ingestJobId, moduleReferenceCount);
        }
    }
}
