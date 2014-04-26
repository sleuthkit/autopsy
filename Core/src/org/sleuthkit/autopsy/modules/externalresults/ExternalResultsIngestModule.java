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
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.Content;

/**
 *
 */
public class ExternalResultsIngestModule extends IngestModuleAdapter implements DataSourceIngestModule {
    private static final Logger logger = Logger.getLogger(ExternalResultsIngestModule.class.getName());
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private long jobId;
    private String reportPath;

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        
        ///@todo get reportPath config from user
        reportPath = Case.getCurrentCase().getModulesOutputDirAbsPath() + File.separator + "ExternalResults";

        
        if (refCounter.incrementAndGet(jobId) == 1) {
            runAndImportResults();
        }
    }        
    
    /**
     *  Launch the third-party process and import the results
     */
    private void runAndImportResults() {
        ///@todo run exe

        // execution is done, look for results to import
        ExternalResultsXML parser = new ExternalResultsXML(reportPath);
        ExternalResultsUtility.importResults(parser);
    }
    
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {

        //do nothing
        return ProcessResult.OK;
    }

}
