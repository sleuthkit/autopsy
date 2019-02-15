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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extract the bookmarks, cookies, downloads and history from the Microsoft Edge
 * files
 *
 * @author kelly
 */
final class ExtractEdge extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractIE.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private final String moduleTempResultsDir;
    private Content dataSource;
    private IngestJobContext context;

    private static final String ESE_TOOL_NAME = "ESEDatabaseView.exe";
    private static final String EDGE_WEBCACHE_NAME = "WebCacheV01.dat";
    private static final String EDGE_WEBCACHE_PREFIX = "WebCacheV01";
    private static final String EDGE = "Edge";
    private static final String ESE_TOOL_FOLDER = "ESEDatabaseView";
    private static final String EDGE_SPARTAN_NAME = "Spartan.edb";

    ExtractEdge() throws NoCurrentCaseException {
        moduleTempResultsDir = RAImageIngestModule.getRATempPath(Case.getCurrentCaseThrows(), EDGE)
                + File.separator + "results"; //NON-NLS
    }

    @Messages({
        "ExtractEdge_Module_Name=Microsoft Edge"
    })
    @Override
    protected String getName() {
        return Bundle.ExtractEdge_Module_Name();
    }

    @Messages({
        "ExtractEdge_process_errMsg_unableFindESEViewer=Unable to find ESEDatabaseViewer",
        "ExtractEdge_process_errMsg_errGettingWebCacheFiles=Error trying to retrieving Edge WebCacheV01 file",
        "ExtractEdge_process_errMsg_webcacheFail=Failure processing Microsoft Edge WebCache file"
    })
    @Override
    void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;

        List<AbstractFile> webCacheFiles;
        List<AbstractFile> spartanFiles;
        try {
            webCacheFiles = fetchWebCacheFiles();
            spartanFiles = fetchSpartanFiles(); // For later use with bookmarks
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractEdge_process_errMsg_errGettingWebCacheFiles());
            logger.log(Level.WARNING, "Error fetching 'WebCacheV01.dat' files for Microsoft Edge", ex); //NON-NLS
            return;
        }

        // No edge files found 
        if (webCacheFiles == null && spartanFiles == null) {
            return;
        }

        dataFound = true;

        if (!PlatformUtil.isWindowsOS()) {
            logger.log(Level.INFO, "Microsoft Edge files found, unable to parse on Non-Windows system"); //NON-NLS
            return;
        }

        final String esedumper = getPathForESEDumper();
        if (esedumper == null) {
            this.addErrorMessage(Bundle.ExtractEdge_process_errMsg_unableFindESEViewer());
            logger.log(Level.SEVERE, "Error finding ESEDatabaseViewer program"); //NON-NLS
            return; //If we cannot find the ESEDatabaseView we cannot proceed
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        try {
            this.processWebCache(esedumper, webCacheFiles);
        } catch (IOException ex) {
            this.addErrorMessage(Bundle.ExtractEdge_process_errMsg_webcacheFail());
            logger.log(Level.SEVERE, "Error returned from processWebCach", ex); // NON-NLS
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        // Bookmarks come from spartan.edb different file
        this.getBookmark(); // Not implemented yet
    }

    void processWebCache(String eseDumperPath, List<AbstractFile> webCachFiles) throws IOException {

        for (AbstractFile webCacheFile : webCachFiles) {

            //Run the dumper 
            String tempWebCacheFileName = EDGE_WEBCACHE_PREFIX
                    + Integer.toString((int) webCacheFile.getId()) + ".dat"; //NON-NLS
            File tempWebCacheFile = new File(RAImageIngestModule.getRATempPath(currentCase, EDGE)
                    + File.separator + tempWebCacheFileName);

            try {
                ContentUtils.writeToFile(webCacheFile, tempWebCacheFile,
                        context::dataSourceIngestIsCancelled);
            } catch (IOException ex) {
                throw new IOException("Error writingToFile: " + webCacheFile, ex); //NON-NLS
            }

            File resultsDir = new File(moduleTempResultsDir + Integer.toString((int) webCacheFile.getId()));
            resultsDir.mkdirs();
            try {
                executeDumper(eseDumperPath, tempWebCacheFile.getAbsolutePath(),
                        EDGE_WEBCACHE_PREFIX, resultsDir.getAbsolutePath());

                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                this.getHistory(); // Not implemented yet

                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                this.getCookie(); // Not implemented yet

                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                this.getDownload(); // Not implemented yet
            } finally {
                tempWebCacheFile.delete();
                resultsDir.delete();
            }
        }
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

    private String getPathForESEDumper() {
        Path path = Paths.get(ESE_TOOL_FOLDER, ESE_TOOL_NAME);
        File eseToolFile = InstalledFileLocator.getDefault().locate(path.toString(),
                ExtractEdge.class.getPackage().getName(), false);
        if (eseToolFile != null) {
            return eseToolFile.getAbsolutePath();
        }

        return null;
    }

    private List<AbstractFile> fetchWebCacheFiles() throws TskCoreException {
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager
                = currentCase.getServices().getFileManager();
        return fileManager.findFiles(dataSource, EDGE_WEBCACHE_NAME);
    }

    private List<AbstractFile> fetchSpartanFiles() throws TskCoreException {
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager
                = currentCase.getServices().getFileManager();
        return fileManager.findFiles(dataSource, EDGE_SPARTAN_NAME);
    }

    private void executeDumper(String dumperPath, String inputFilePath,
            String inputFilePrefix, String outputDir) throws IOException {
        final String outputFileFullPath = outputDir + File.separator + inputFilePrefix + ".txt"; //NON-NLS
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

        ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context));
    }
}
