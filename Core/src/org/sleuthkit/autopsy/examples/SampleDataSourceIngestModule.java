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
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.TskData;

/**
 * Sample data source ingest module that doesn't do much. Demonstrates per
 * ingest job module settings, use of a subset of the available ingest services
 * and thread-safe sharing of per ingest job data.
 */
class SampleDataSourceIngestModule extends IngestModuleAdapter implements DataSourceIngestModule {

    private static final HashMap<Long, Long> fileCountsForIngestJobs = new HashMap<>();
    private final boolean skipKnownFiles;
    private IngestJobContext context = null;

    SampleDataSourceIngestModule(SampleModuleIngestJobSettings settings) {
        this.skipKnownFiles = settings.skipKnownFiles();
    }
    
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        // This method is thread-safe with per ingest job reference counted
        // management of shared data.
        initFileCount(context.getJobId());
    }

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        // There are two tasks to do. Set the the progress bar to determinate 
        // and set the remaining number of work units to be completed to two.
        progressBar.switchToDeterminate(2);
        
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
            
            progressBar.progress(1);
            
            // Get files by creation time.
            long currentTime = System.currentTimeMillis() / 1000;
            long minTime = currentTime - (14 * 24 * 60 * 60); // Go back two weeks.
            List<FsContent> otherFiles = sleuthkitCase.findFilesWhere("crtime > " + minTime);
            for (FsContent otherFile : otherFiles) {
                if (!skipKnownFiles || otherFile.getKnown() != TskData.FileKnown.KNOWN) {
                    ++fileCount;
                }                
            }
            
            // This method is thread-safe with per ingest job reference counted
            // management of shared data.
            addToFileCount(context.getJobId(), fileCount);
            
            progressBar.progress(1);
            return IngestModule.ProcessResult.OK;         
            
        } catch (TskCoreException ex) {
            IngestServices ingestServices = IngestServices.getInstance();
            Logger logger = ingestServices.getLogger(SampleIngestModuleFactory.getModuleName());
            logger.log(Level.SEVERE, "File query failed", ex);
            return IngestModule.ProcessResult.ERROR;
        }
    }

    @Override
    public void shutDown(boolean ingestJobCancelled) {
        // This method is thread-safe with per ingest job reference counted
        // management of shared data.
        postFileCount(context.getJobId());
    }

    synchronized static void initFileCount(long ingestJobId) {
        Long refCount = IngestModuleAdapter.moduleRefCountIncrementAndGet(ingestJobId);
        if (refCount == 1) {
            fileCountsForIngestJobs.put(ingestJobId, 0L);
        }
    }

    synchronized static void addToFileCount(long ingestJobId, long countToAdd) {
        Long fileCount = fileCountsForIngestJobs.get(ingestJobId);
        fileCount += countToAdd;
        fileCountsForIngestJobs.put(ingestJobId, fileCount);
    }

    synchronized static void postFileCount(long ingestJobId) {
        Long refCount = IngestModuleAdapter.moduleRefCountDecrementAndGet(ingestJobId);
        if (refCount == 0) {
            Long filesCount = fileCountsForIngestJobs.remove(ingestJobId);
            String msgText = String.format("Found %d files", filesCount);
            IngestMessage message = IngestMessage.createMessage(
                    IngestMessage.MessageType.DATA,
                    SampleIngestModuleFactory.getModuleName(),
                    msgText);
            IngestServices.getInstance().postMessage(message);
        } 
    }
}
