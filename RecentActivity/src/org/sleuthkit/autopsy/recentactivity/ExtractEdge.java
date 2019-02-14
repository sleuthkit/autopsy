/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

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
    
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");

    ExtractEdge() throws NoCurrentCaseException{
        moduleName = NbBundle.getMessage(Chrome.class, "ExtractEdge.moduleName");
        moduleTempResultsDir = RAImageIngestModule.getRATempPath(Case.getCurrentCaseThrows(), EDGE) + File.separator + "results";
    }
    
    @Override
    void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        
        this.processWebCache();
        
         // Bookmarks come from spartan.edb different file
        this.getBookmark(); // Not implemented yet
    }
    
    void processWebCache(){
        Path path = Paths.get("ESEDatabaseView", ESE_TOOL_NAME);
        ESE_TOOL_FILE =  InstalledFileLocator.getDefault().locate(path.toString(), ExtractEdge.class.getPackage().getName(), false); //NON-NLS
        if (ESE_TOOL_FILE == null) {
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "ExtractEdge.process.errMsg.unableFindESEViewer", this.getName()));
            logger.log(Level.SEVERE, "Error finding ESEDatabaseViewer program "); //NON-NLS
        }
        
         final String esedumper = ESE_TOOL_FILE.getAbsolutePath();

        // get WebCacheV01.dat files
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> webCachFiles;
        try {
            webCachFiles = fileManager.findFiles(dataSource, EDGE_WEBCACHE_NAME); //NON-NLS
        } catch (TskCoreException ex) {
            this.addErrorMessage(NbBundle.getMessage(this.getClass(), "ExtractEdge.process.errMsg.errGettingWebCacheFiles",
                    this.getName()));
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history."); //NON-NLS
            return;
        }

        if (webCachFiles.isEmpty()) {
            String msg = NbBundle.getMessage(this.getClass(), "ExtractEdge.process.errMsg.noWebCachFiles");
            logger.log(Level.INFO, msg);
            return;
        }

        dataFound = true;   
        
        if(!PlatformUtil.isWindowsOS()){
            logger.log(Level.WARNING, "Edge data found, unable to parse on non-windows system."); //NON-NLS
            return;
        }
        
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
                        NbBundle.getMessage(this.getClass(), "ExtractEdge.process.errMsg.errWriteFile", this.getName(),
                                datFile.getAbsolutePath()));
                continue;
            }
            
            File resultsDir = new File(moduleTempResultsDir + Integer.toString((int) indexFile.getId()));
            resultsDir.mkdirs();
            executeDumper(esedumper, datFile.getAbsolutePath(), "webcache", resultsDir.getAbsolutePath());
             
            this.getHistory(indexFile, resultsDir); // Not implemented yet
            this.getCookie(); // Not implemented yet
            this.getDownload(); // Not implemented yet
            
             datFile.delete();
             resultsDir.delete();
         }   
    }
    
   
    @Messages({
        "ExtractEdge_getHistory_containerFileNotFound=Error while trying to analyze Edge history"
    })
    private void getHistory(AbstractFile origFile, File resultDir) {
        File containerFiles[] = resultDir.listFiles((dir, name) -> name.toLowerCase().contains("container"));
        
        if(containerFiles == null){
            this.addErrorMessage(Bundle.ExtractEdge_getHistory_containerFileNotFound());
            return;
        }
        
        // The assumption is that the history is in one or more of the container files.
        // search through all of them looking for a lines with the text "Visited:"
        for(File file: containerFiles){
            Scanner fileScanner;
            try {
                fileScanner = new Scanner(new FileInputStream(file.toString()));
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "Unable to find the ESEDatabaseView file at " + file.getPath(), ex); //NON-NLS
                continue; // Should we keep going or bail on the whole process?
            }
            
            Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
            
            try{
                List<String> headers = null;
                while (fileScanner.hasNext()) {
                    String line = fileScanner.nextLine();
                    if(headers == null){ // The header should be the first line
                        headers = Arrays.asList(line.toLowerCase().split(","));
                        continue;
                    }

                    if(line.contains("Visited")){
                        BlackboardArtifact b = parseHistoryLine(origFile, headers, line);
                        if(b != null){
                            bbartifacts.add(b);
                            this.indexArtifact(b);
                        }
                    }else{
                        // I am making the assumption that if the line doesn't have
                        // "Visited" in it that its probably not the file we are looking for
                        // therefore we should move on to the next file.
                        break;
                    }
                }
            }
            finally{
                fileScanner.close();
            }
            
            if(!bbartifacts.isEmpty()){
                services.fireModuleDataEvent(new ModuleDataEvent(
                    RecentActivityExtracterModuleFactory.getModuleName(),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, bbartifacts));
            }
        }
      
    }
    
    @Messages({
        "ExtractEdge_programName=Microsoft Edge"
    })
    private BlackboardArtifact parseHistoryLine(AbstractFile origFile, List<String> headers, String line){
        BlackboardArtifact bbart = null;
        String[] rowSplit = line.split(",");
        
        int index = headers.indexOf("url");
        String urlUserStr = rowSplit[index];
        
        String[] str = urlUserStr.split("@");
        String user = str[0].replace("Visited: ", "");
        String url = str[1];
        
        index = headers.indexOf("accessedtime");
        String accessTime = rowSplit[index].trim();
        Long ftime = null;
        try{
            Long epochtime = dateFormatter.parse(accessTime).getTime();
            ftime = epochtime / 1000;
        }catch(ParseException ex){
            logger.log(Level.WARNING, "The Accessed Time format in history file seems invalid " + accessTime, ex);
        }
        
        try{
            bbart = origFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY);
            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                RecentActivityExtracterModuleFactory.getModuleName(), url));
            
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED,
                RecentActivityExtracterModuleFactory.getModuleName(), ftime));
       
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER,
                RecentActivityExtracterModuleFactory.getModuleName(), ""));
            
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE,
                RecentActivityExtracterModuleFactory.getModuleName(), ""));
            
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
                RecentActivityExtracterModuleFactory.getModuleName(), Bundle.ExtractEdge_programName()));
            
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN,
                RecentActivityExtracterModuleFactory.getModuleName(), (NetworkUtils.extractDomain(url)))); //NON-NLS
            
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME,
                RecentActivityExtracterModuleFactory.getModuleName(), user));
           
            bbart.addAttributes(bbattributes);
             
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error writing Microsoft Edge web history artifact to the blackboard.", ex); //NON-NLS
        }
        
        return bbart;
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
    
    private boolean executeDumper(String dumperPath, String inputFilePath, String inputFilePrefix, String outputDir){
        final String outputFileFullPath = outputDir + File.separator + inputFilePrefix + ".txt";
        final String errFileFullPath = outputDir + File.separator + inputFilePrefix + ".err"; //NON-NLS
        logger.log(Level.INFO, "Writing ESEDatabaseViewer results to: {0}", outputDir); //NON-NLS   
        
        List<String> commandLine = new ArrayList<>();
        commandLine.add(dumperPath);
        commandLine.add("/table");
        commandLine.add(inputFilePath);
         commandLine.add("*");
        commandLine.add("/scomma");
        commandLine.add(outputDir + "\\" + inputFilePrefix + "_*.csv");
         
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(new File(outputFileFullPath));
        processBuilder.redirectError(new File(errFileFullPath));

        try{
            ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context));
        }catch(IOException ex){
            logger.log(Level.SEVERE, "Unable to execute ESEDatabaseView to process Edge file." , ex); //NON-NLS
            return false;
        }
        
        return true;
    }
}
