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
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
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
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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
    private Logger logger;

    SampleDataSourceIngestModule(SampleModuleIngestJobSettings settings) {
        this.skipKnownFiles = settings.skipKnownFiles();
    }

    @Override
    /*
     Where any setup and configuration is done
     'context' is an instance of org.sleuthkit.autopsy.ingest.IngestJobContext.
     See: http://sleuthkit.org/autopsy/docs/api-docs/3.1/classorg_1_1sleuthkit_1_1autopsy_1_1ingest_1_1_ingest_job_context.html
     TODO: Add any setup code that you need here.
     */
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        this.logger = Logger.getLogger(NbBundle.getMessage(SampleDataSourceIngestModuleFactory.class, "SampleDataSourceIngestModuleFactory.moduleName"));
    }

    @Override
    /*
     Where the analysis is done.
     The 'dataSource' object being passed in is of type org.sleuthkit.datamodel.Content.
     See: http://www.sleuthkit.org/sleuthkit/docs/jni-docs/4.3/interfaceorg_1_1sleuthkit_1_1datamodel_1_1_content.html
     'progressBar' is of type org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress
     See: http://sleuthkit.org/autopsy/docs/api-docs/3.1/classorg_1_1sleuthkit_1_1autopsy_1_1ingest_1_1_data_source_ingest_module_progress.html
     TODO: Add your analysis code in here.
     */
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        progressBar.switchToIndeterminate();
        /*
         This will work in 4.0.1 and beyond
         Use blackboard class to index blackboard artifacts for keyword search
         Blackboard blackboard = Case.getCurrentCase().getServices().getBlackboard()
        
         For our example, we will use FileManager to get all
         files with the word "test"
         in the name and then count and read them
         FileManager API: http://sleuthkit.org/autopsy/docs/api-docs/3.1/classorg_1_1sleuthkit_1_1autopsy_1_1casemodule_1_1services_1_1_file_manager.html
         */

        try {
            // Get count of files with .doc extension.
            FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
            List<AbstractFile> docFiles = fileManager.findFiles(dataSource, "%test%");

            int numFiles = docFiles.size();
            logger.log(Level.INFO, "found " + numFiles + " files");
            progressBar.switchToDeterminate(numFiles);
            progressBar.progress(1);
            int fileCount = 0;
            for (AbstractFile file : docFiles) {
                // check if we were cancelled
                if (context.dataSourceIngestIsCancelled()) {
                    return IngestModule.ProcessResult.OK;
                }
                logger.log(Level.INFO, "Processing file: " + file.getName());
                ++fileCount;
                /*
                 Make an artifact on the blackboard.  TSK_INTERESTING_FILE_HIT is a generic type of
                 artfiact.  Refer to the developer docs for other examples.
                 */
                try {
                    BlackboardArtifact art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                    BlackboardAttribute attr = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME,
                            NbBundle.getMessage(SampleDataSourceIngestModuleFactory.class, "SampleDataSourceIngestModuleFactory.moduleName"), "Test File");
                    art.addAttribute(attr);
                    /*
                     This will work in 4.0.1 and beyond
                     try {
                     //index the artifact for keyword search
                     blackboard.indexArtifact(art)
                     }
                     catch (Blackboard.BlackboardException ex) {
                     self.log(Level.SEVERE, "Error indexing artifact " + art.getDisplayName())
                     }
//                     */
                } catch (TskCoreException ex) {
                    Exceptions.printStackTrace(ex);
                }
                try {
                    byte buffer[] = new byte[1024];
                    int len = file.read(buffer, 0, 1024);
                    int count = 0;
                    while (len != -1) {
                        count += len;
                        len = file.read(buffer, count, 1024);
                    }

                } catch (TskCoreException ex) {
                    IngestServices ingestServices = IngestServices.getInstance();
                    Logger logger = ingestServices.getLogger(SampleDataSourceIngestModuleFactory.getModuleName());
                    logger.log(Level.SEVERE, "Error processing file (id = " + file.getId() + ")", ex);
                    return IngestModule.ProcessResult.ERROR;
                }
                progressBar.progress(fileCount);
            }

            IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                    "Sample Data Source Ingest Module", "Found" + fileCount + " files");
            IngestServices.getInstance().postMessage(message);
            return IngestModule.ProcessResult.OK;

        } catch (TskCoreException ex) {
            IngestServices ingestServices = IngestServices.getInstance();
            Logger logger = ingestServices.getLogger(SampleDataSourceIngestModuleFactory.getModuleName());
            logger.log(Level.SEVERE, "File query failed", ex);
            return IngestModule.ProcessResult.ERROR;
        }
    }
}
