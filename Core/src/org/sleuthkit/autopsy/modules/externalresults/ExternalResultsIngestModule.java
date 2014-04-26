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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.examples.SampleIngestModuleFactory;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.Content;

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
    DataSourceIngestModuleProgress progressBar;

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        
        // By default, we create the import path and provide it to the third party executable as an argument
        importPath = Case.getCurrentCase().getModulesOutputDirAbsPath() + File.separator + MODULE_DIR + File.separator + IMPORT_DIR;

        refCounter.incrementAndGet(jobId);
    }        
    
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        progressBar = statusHelper;
        progressBar.switchToDeterminate(2);
        
        if (refCounter.get(jobId) == 1) {
            runAndImportResults();
        }

        return ProcessResult.OK;
    }    
    
    @Override
    public void shutDown(boolean ingestJobCancelled) {
        IngestMessage message = IngestMessage.createMessage(
            IngestMessage.MessageType.DATA,
            ExternalResultsModuleFactory.getModuleName(),
            "Finished.");        
        IngestServices.getInstance().postMessage(message);
    }    
    
    /**
     *  Launch the third-party process and import the results
     */
    private void runAndImportResults() {
        ///@todo run exe, passing the data source path and the import (results) path
        progressBar.progress(1);

        // execution is done, look for results to import
        ExternalResultsXML parser = new ExternalResultsXML(importPath);
        ExternalResultsUtility.importResults(parser);
        progressBar.progress(1);
    }

}
