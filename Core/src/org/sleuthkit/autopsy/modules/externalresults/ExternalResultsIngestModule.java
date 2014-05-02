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
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
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
    private static final String CFG_XML_FILENAME = "xml_filename";
    private static final String CFG_XML_FILENAME_DEFAULT = "results.xml";
    private static final String CFG_XML_FILEPATH = "xml_filepath";
    private static final String CFG_CMD_STR = "cmd_str";
    
    private long jobId;
    private String importPath;
    private String importFilename;
    private String cmdStr;
    String dataSourceLocalPath;
    Content dataSource;
    DataSourceIngestModuleProgress progressBar;

    /**
     * 
     * @param context
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException 
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        
        if (refCounter.incrementAndGet(jobId) == 1) {
            updateConfiguration();           

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
    }        

    private void updateConfiguration() {
        final String cfgModuleName = NbBundle.getMessage(ExternalResultsIngestModule.class, "ExternalResultsIngestModule.properties.text");
        importFilename = ModuleSettings.getConfigSetting(cfgModuleName, CFG_XML_FILENAME);
        if (importFilename == null) {
            importFilename = CFG_XML_FILENAME_DEFAULT;
            ModuleSettings.setConfigSetting(cfgModuleName, CFG_XML_FILENAME, importFilename);        
        }
        importPath = ModuleSettings.getConfigSetting(cfgModuleName, CFG_XML_FILEPATH);
        if (importPath == null) {
            // By default, we create the import path and provide it to the third party executable as an argument
            importPath = Case.getCurrentCase().getModulesOutputDirAbsPath() + File.separator + MODULE_DIR + jobId + File.separator + IMPORT_DIR;
            // Do not store the default import path to module settings, since it changes for each new case
        }
        cmdStr = ModuleSettings.getConfigSetting(cfgModuleName, CFG_CMD_STR);
        if (cmdStr == null) {
            ModuleSettings.setConfigSetting(cfgModuleName, CFG_CMD_STR, "");  //NON-NLS      
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
        this.dataSource = dataSource;
        
        try {
            dataSourceLocalPath = dataSource.getImage().getPaths()[0];
        } catch (TskCoreException ex) {
            String msgstr = NbBundle.getMessage(this.getClass(), "ExternalResultsIngestModule.process.exception.dataSourceLocalPath");
            logger.log(Level.SEVERE, msgstr);
            return ProcessResult.ERROR;
        }
        
        // Run
        if (refCounter.get(jobId) == 1) {
            try {
                runProgram(); 
            } catch(Exception ex) {
                String msgstr = NbBundle.getMessage(this.getClass(), "ExternalResultsIngestModule.process.exception.run") + ex.getLocalizedMessage();
                logger.log(Level.SEVERE, msgstr);
                return ProcessResult.ERROR;            
            }
            
            importResults();
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
    private void runProgram() throws IOException, InterruptedException {
        //run exe, passing the data source path and the import (results) path
        ExecUtil executor = new ExecUtil();
        executor.execute(cmdStr, importPath, dataSourceLocalPath);
        
        progressBar.progress(1);
    }
    
    /**
     * Interface with ExternalResultsUtility
     */
    private void importResults() {
        // execution is done, look for results to import
        String importFilePath = importPath + File.separator + importFilename;
        
        // First make a parser object to hand to ExternalResultsUtility
        ExternalResultsXML parser = new ExternalResultsXML(importFilePath);
        
        // This will tell the parser to do its thing and will then import that data
        ExternalResultsUtility.importResults(parser, dataSource);
        
        progressBar.progress(1);        
    }

}
