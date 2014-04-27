/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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


package org.sleuthkit.autopsy.modules.externalresults;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * 
 */
public class ExternalResultsIngestModule extends IngestModuleAdapter implements DataSourceIngestModule {
    private static final Logger logger = Logger.getLogger(ExternalResultsIngestModule.class.getName());
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final String MODULE_DIR = "ExternalResults";
    private static final String IMPORT_DIR = "import";
    private long jobId;
    private String importPath;
    private String cmdPath;
    private String cmdName;
    String dataSourcePath;
    private Process thirdPartyProc = null;
    DataSourceIngestModuleProgress progressBar;

    /**
     * 
     * @param context
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException 
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        refCounter.incrementAndGet(jobId);

        // By default, we create the import path and provide it to the third party executable as an argument
        importPath = Case.getCurrentCase().getModulesOutputDirAbsPath() + File.separator + MODULE_DIR + File.separator + IMPORT_DIR;
        
        // make sure module output directory and import path exist else create them
        File importPathDir = new File(importPath);
        if (!importPathDir.exists()) {
            if (!importPathDir.mkdirs()) {
                String message = NbBundle.getMessage(this.getClass(), "ExternalResultsIngestModule.startUp.exception.importdir");
                logger.log(Level.SEVERE, message);
                throw new IngestModuleException(message);
            }
        }
        
    }        
    
    /**
     * 
     * @param dataSource
     * @param statusHelper
     * @return 
     */
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        progressBar = statusHelper;
        progressBar.switchToDeterminate(2);
        
        try {
            dataSourcePath = dataSource.getUniquePath();
        } catch (TskCoreException ex) {
            String msgstr = NbBundle.getMessage(this.getClass(), "ExternalResultsIngestModule.process.exception.datasourcepath");
            logger.log(Level.SEVERE, msgstr);
            return ProcessResult.ERROR;
        }
        
        ///@todo get cmdName
        
        // Run
        if (refCounter.get(jobId) == 1) {
            try {
                runAndImportResults();
            } catch (Exception ex) {
                String msgstr = NbBundle.getMessage(this.getClass(), "ExternalResultsIngestModule.process.exception.run");
                logger.log(Level.SEVERE, msgstr);
                return ProcessResult.ERROR;                
            }
        }

        return ProcessResult.OK;
    }  

    /**
     * 
     * @param ingestJobCancelled 
     */
    @Override
    public void shutDown(boolean ingestJobCancelled) {
        String msgstr = NbBundle.getMessage(this.getClass(), "ExternalResultsIngestModule.process.shutdown.finished");
        IngestMessage message = IngestMessage.createMessage(
            IngestMessage.MessageType.DATA,
            ExternalResultsModuleFactory.getModuleName(),
            msgstr);        
        IngestServices.getInstance().postMessage(message);
    }    
    
    /**
     *  Launch the third-party process and import the results
     */
    private void runAndImportResults() throws IOException, InterruptedException {
        
        final String[] cmdArgs = { 
            cmdName,
            dataSourcePath,
            importPath };

        StringBuilder cmdSb = new StringBuilder();
        for (int i = 0; i < cmdArgs.length; ++i ) {
            cmdSb.append(cmdArgs[i]).append(" ");
        }      
        
        File workingDir = new File(cmdPath);
        //run exe, passing the data source path and the import (results) path
        logger.log(Level.INFO, "Starting external command using: " + cmdSb.toString()); //NON-NLS
        ExecUtil executor = new ExecUtil();
        executor.execute(cmdArgs[0], cmdArgs[1], cmdArgs[2]);
        logger.log(Level.INFO, "Finished running external command."); //NON-NLS
        
        progressBar.progress(1);

        // execution is done, look for results to import
        ExternalResultsXML parser = new ExternalResultsXML(importPath);
        ExternalResultsUtility.importResults(parser);
        progressBar.progress(1);
    }

}
