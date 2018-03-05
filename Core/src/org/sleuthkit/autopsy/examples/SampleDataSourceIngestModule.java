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

import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
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
 * ingest job module settings, checking for job cancellation, updating the
 * DataSourceIngestModuleProgress object, and use of a subset of the available
 * ingest services.
 */
class SampleDataSourceIngestModule implements DataSourceIngestModule {

    private final boolean skipKnownFiles;
    private IngestJobContext context = null;

    SampleDataSourceIngestModule(SampleModuleIngestJobSettings settings) {
        this.skipKnownFiles = settings.skipKnownFiles();
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
    }

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        // There are two tasks to do.
        progressBar.switchToDeterminate(2);

        try {
            // Get count of files with .doc extension.
            FileManager fileManager = Case.getOpenCase().getServices().getFileManager();
            List<AbstractFile> docFiles = fileManager.findFiles(dataSource, "%.doc");

            long fileCount = 0;
            for (AbstractFile docFile : docFiles) {
                if (!skipKnownFiles || docFile.getKnown() != TskData.FileKnown.KNOWN) {
                    ++fileCount;
                }
            }
            progressBar.progress(1);

            // check if we were cancelled
            if (context.dataSourceIngestIsCancelled()) {
                return IngestModule.ProcessResult.OK;
            }

            // Get files by creation time.
            long currentTime = System.currentTimeMillis() / 1000;
            long minTime = currentTime - (14 * 24 * 60 * 60); // Go back two weeks.
            List<AbstractFile> otherFiles = fileManager.findFiles(dataSource, "crtime > " + minTime);
            for (AbstractFile otherFile : otherFiles) {
                if (!skipKnownFiles || otherFile.getKnown() != TskData.FileKnown.KNOWN) {
                    ++fileCount;
                }
            }
            progressBar.progress(1);

            if (context.dataSourceIngestIsCancelled()) {
                return IngestModule.ProcessResult.OK;
            }

            // Post a message to the ingest messages in box.
            String msgText = String.format("Found %d files", fileCount);
            IngestMessage message = IngestMessage.createMessage(
                    IngestMessage.MessageType.DATA,
                    SampleIngestModuleFactory.getModuleName(),
                    msgText);
            IngestServices.getInstance().postMessage(message);

            return IngestModule.ProcessResult.OK;

        } catch (TskCoreException | NoCurrentCaseException ex) {
            IngestServices ingestServices = IngestServices.getInstance();
            Logger logger = ingestServices.getLogger(SampleIngestModuleFactory.getModuleName());
            logger.log(Level.SEVERE, "File query failed", ex);
            return IngestModule.ProcessResult.ERROR;
        }
    }
}
