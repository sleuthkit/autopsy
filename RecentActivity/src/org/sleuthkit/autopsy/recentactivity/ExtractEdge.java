/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 *
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author kelly
 */
public class ExtractEdge extends Extract{
    
    private static final Logger logger = Logger.getLogger(ExtractIE.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private final String moduleTempResultsDir;
    private Content dataSource;
    private IngestJobContext context;
    
    private static String ESE_TOOL_NAME = "ESEDatabaseView.exe";
    private static File ESE_TOOL_FILE;
    private static String EDGE_WEBCACHE_NAME = "WebCacheV01.dat";
    private static String EDGE = "Edge";

    ExtractEdge() throws NoCurrentCaseException{
        moduleName = NbBundle.getMessage(Chrome.class, "ExtractEdge.moduleName");
        moduleTempResultsDir = RAImageIngestModule.getRATempPath(Case.getCurrentCaseThrows(), EDGE) + File.separator + "results"; 
    }
    
    @Override
    void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        
        Path path = Paths.get("ESEDatabaseView", ESE_TOOL_NAME);
        ESE_TOOL_FILE =  InstalledFileLocator.getDefault().locate(path.toString(), ExtractEdge.class.getPackage().getName(), false); //NON-NLS
        if (ESE_TOOL_FILE == null) {
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractEdge.errMsg.unableFindESEViewer", this.getName()));
            logger.log(Level.SEVERE, "Error finding ESEDatabaseViewer program "); //NON-NLS
        }

        // get WebCacheV01.dat files
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> webCachFiles;
        try {
            webCachFiles = fileManager.findFiles(dataSource, EDGE_WEBCACHE_NAME); //NON-NLS
        } catch (TskCoreException ex) {
            this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractIE.getHistory.errMsg.errGettingHistFiles",
                    this.getName()));
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history."); //NON-NLS
            return;
        }

        if (webCachFiles.isEmpty()) {
            String msg = NbBundle.getMessage(this.getClass(), "ExtractIE.getHistory.errMsg.noHistFiles");
            logger.log(Level.INFO, msg);
            return;
        }

        dataFound = true;
        
        File resultsDir = new File(moduleTempResultsDir);
        resultsDir.mkdirs();
        
        String temps;
        String indexFileName;
        for(AbstractFile indexFile : webCachFiles) {
        
            //Run the dumper 
            indexFileName = "WebCacheV01" + Integer.toString((int) indexFile.getId()) + ".dat";
            temps = RAImageIngestModule.getRATempPath(currentCase, EDGE) + File.separator + indexFileName; //NON-NLS
            File datFile = new File(temps);
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }
            try {
                ContentUtils.writeToFile(indexFile, datFile, context::dataSourceIngestIsCancelled);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error while trying to write index.dat file " + datFile.getAbsolutePath(), e); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "ExtractIE.getHistory.errMsg.errWriteFile", this.getName(),
                                datFile.getAbsolutePath()));
                continue;
            }
            
            executeDumper(path.toAbsolutePath().toString(), datFile.getAbsolutePath(), moduleTempResultsDir);
             
            this.getHistory();
            this.getCookie(); // Not implemented yet
            this.getDownload(); // Not implemented yet
            
            // cleam up the temp files
         }
         
         // Bookmarks come from a different file
         this.getBookmark();
    }
    
    private boolean executeDumper(String dumperPath, String inputFile, String outputDir){
        final String outputFileFullPath = moduleTempResultsDir + File.separator + inputFile;
        final String errFileFullPath = moduleTempResultsDir + File.separator + inputFile + ".err"; //NON-NLS
        logger.log(Level.INFO, "Writing ESEDatabaseViewer results to: {0}", outputFileFullPath); //NON-NLS   
        
        List<String> commandLine = new ArrayList<>();
        commandLine.add(dumperPath);
        commandLine.add("/table");
        commandLine.add(inputFile);
        commandLine.add("/scomma");
        commandLine.add(outputDir + "\\webcache_*.csv");
         
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(new File(outputFileFullPath));
        processBuilder.redirectError(new File(errFileFullPath));
        
        String str = processBuilder.command().toString();
        
        int result;
        try{
            result = ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context));
        }catch(IOException ex){
            
        }
        
        return false;
    }
    
    private void createWebCachTempFiles(){
        
    }
    
    private void cleanupWebCacheTempFiles(){
        
    }
    
    private boolean executeESEDumper(String eseToolFilePath, String eseFilPath, String outputFileName){
        final String outputFileFullPath = moduleTempResultsDir + File.separator + outputFileName;
        final String errFileFullPath = moduleTempResultsDir + File.separator + outputFileName + ".err"; 
        
        logger.log(Level.INFO, "Writing ESEDatabaseViewer results to: {0}", outputFileFullPath); //NON-NLS   
        List<String> commandLine = new ArrayList<>();
       
        
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(new File(outputFileFullPath));
        processBuilder.redirectError(new File(errFileFullPath));
        
        return false;
    }
    
    /**
     * Query for history databases and add artifacts
     */
    private void getHistory() {
        
    }
    
    /**
     * Search for bookmark files and make artifacts.
     */
    private void getBookmark() {
        
    }
    
    /**
     * Queries for cookie files and adds artifacts
     */
    private void getCookie() {
        
    }
    
    /**
     * Queries for download files and adds artifacts
     */
    private void getDownload() {
        
    }
}
