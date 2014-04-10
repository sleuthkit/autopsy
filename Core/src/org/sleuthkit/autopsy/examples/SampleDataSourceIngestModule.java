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
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.TskData;

/**
 * Sample data source ingest module that doesn't do much. Demonstrates per
 * ingest job module settings, use of a subset of the available ingest services
 * and thread-safe sharing of per ingest job resources.
 * <p>
 * IMPORTANT TIP: This sample data source ingest module directly implements
 * DataSourceIngestModule, which extends IngestModule. A practical alternative,
 * recommended if you do not need to provide implementations of all of the
 * IngestModule methods, is to extend the abstract class IngestModuleAdapter to
 * get default "do nothing" implementations of the IngestModule methods.
 */
class SampleDataSourceIngestModule implements DataSourceIngestModule {

    private static final HashMap<Long, Integer> moduleReferenceCountsForIngestJobs = new HashMap<>();
    private static final HashMap<Long, Long> fileCountsForIngestJobs = new HashMap<>();
    private final boolean skipKnownFiles;
    private IngestJobContext context = null;

    SampleDataSourceIngestModule(SampleModuleIngestJobSettings settings) {
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

        // This method is thread-safe with per ingest job reference counting.
        initFileCount(context.getJobId());
    }

    /**
     * Processes a data source.
     *
     * @param dataSource The data source to process.
     * @param statusHelper A status helper to be used to report progress and
     * detect ingest job cancellation.
     * @return A result code indicating success or failure of the processing.
     */
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        // There are two tasks to do. Use the status helper to set the the 
        // progress bar to determinate and to set the remaining number of work 
        // units to be completed.
        statusHelper.switchToDeterminate(2);
        
        Case autopsyCase = Case.getCurrentCase();
        SleuthkitCase sleuthkitCase = autopsyCase.getSleuthkitCase();
        Services services = new Services(sleuthkitCase);
        FileManager fileManager = services.getFileManager();
        try {
            // Get count of files with .doc extension.
            long fileCount = 0;
            List<AbstractFile> docFiles = fileManager.findFiles(dataSource, "%.doc");
            for (AbstractFile docFile : docFiles) {
                if (!skipKnownFiles || docFile.getKnown() != TskData.FileKnown.KNOWN) {
                    ++fileCount;
                }                
            }
            
            statusHelper.progress(1);
            
            // Get files by creation time.
            long currentTime = System.currentTimeMillis() / 1000;
            long minTime = currentTime - (14 * 24 * 60 * 60); // Go back two weeks.
            List<FsContent> otherFiles = sleuthkitCase.findFilesWhere("crtime > " + minTime);
            for (FsContent otherFile : otherFiles) {
                if (!skipKnownFiles || otherFile.getKnown() != TskData.FileKnown.KNOWN) {
                    ++fileCount;
                }                
            }
            
            // This method is thread-safe and keeps per ingest job counters.
            addToFileCount(context.getJobId(), fileCount);
            
            statusHelper.progress(1);
            
        } catch (TskCoreException ex) {
            IngestServices ingestServices = IngestServices.getInstance();
            Logger logger = ingestServices.getLogger(SampleIngestModuleFactory.getModuleName());
            logger.log(Level.SEVERE, "File query failed", ex);
            return IngestModule.ProcessResult.ERROR;
        }

        return IngestModule.ProcessResult.OK;
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
        postFileCount(context.getJobId());
    }

    synchronized static void initFileCount(long ingestJobId) {
        Integer moduleReferenceCount;
        if (!moduleReferenceCountsForIngestJobs.containsKey(ingestJobId)) {
            moduleReferenceCount = 1;
            fileCountsForIngestJobs.put(ingestJobId, 0L);
        } else {
            moduleReferenceCount = moduleReferenceCountsForIngestJobs.get(ingestJobId);
            ++moduleReferenceCount;
        }
        moduleReferenceCountsForIngestJobs.put(ingestJobId, moduleReferenceCount);
    }

    synchronized static void addToFileCount(long ingestJobId, long countToAdd) {
        Long fileCount = fileCountsForIngestJobs.get(ingestJobId);
        fileCount += countToAdd;
        fileCountsForIngestJobs.put(ingestJobId, fileCount);
    }

    synchronized static void postFileCount(long ingestJobId) {
        Integer moduleReferenceCount = moduleReferenceCountsForIngestJobs.remove(ingestJobId);
        --moduleReferenceCount;
        if (moduleReferenceCount == 0) {
            Long filesCount = fileCountsForIngestJobs.remove(ingestJobId);
            String msgText = String.format("Found %d files", filesCount);
            IngestMessage message = IngestMessage.createMessage(
                    IngestMessage.MessageType.DATA,
                    SampleIngestModuleFactory.getModuleName(),
                    msgText);
            IngestServices.getInstance().postMessage(message);
        } else {
            moduleReferenceCountsForIngestJobs.put(ingestJobId, moduleReferenceCount);
        }
    }
}
