/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2025 Sleuth Kit Labs.
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Extract the thumbcache files to a temp directory so it can be
 * parsed and added as a derived file(s)
 */
final class ExtractThumbcache extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractThumbcache.class.getName());

    private static final String THUMBCACHE_TOOL_FOLDER = "thumbcache_parser"; //NON-NLS
    private static final String THUMBCACHE_TOOL_NAME_WINDOWS = "thumbcache_viewer_cmd.exe"; //NON-NLS
    private static final String THUMBCACHE_OUTPUT_FILE_NAME = "Output.txt"; //NON-NLS
    private static final String THUMBCACHE_ERROR_FILE_NAME = "Error.txt"; //NON-NLS
    
    private final IngestJobContext context;

    @Messages({"ExtractThumbcache_module_name=Thumbcache Analyzer"})     
    
    ExtractThumbcache(IngestJobContext context) {
        super(Bundle.ExtractThumbcache_module_name(), context);
        this.context = context;
    }

    @Messages({
        "Thumbcache_Files_Not_Found=Thumbcache files not found",
        "ExtractThumbcache_error_finding_program=Could not find thumbcache_viewer_cmd.exe program",
        "Thumbcache_process_error_executing_export_thumbcache_program=Error running thumbcache program"
    })

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        if (!PlatformUtil.isWindowsOS()) {
            logger.log(Level.WARNING,"Thumbcache only Supported on Windows Platform."); //NON-NLS
            return;  // No need to continue
        }
        
        String modOutPath = RAImageIngestModule.getRAOutputPath(Case.getCurrentCase(), "thumbcache", context.getJobId());
        File dir = new File(modOutPath);
        if (dir.exists() == false) {
            dir.mkdirs();
        }

        String tempDirPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "thumbcache", context.getJobId()); //NON-NLS
        List<AbstractFile> thumbcacheFiles = getThumbcacheFiles(dataSource, tempDirPath);
        if (thumbcacheFiles == null) {
            this.addErrorMessage(Bundle.Thumbcache_Files_Not_Found());
            logger.log(Level.WARNING, "Error finding thumbcache files"); //NON-NLS
            return; //If we cannot find the thumbcache files we cannot proceed
            
        }
        final String thumbcacheDumper = getPathForThumbcacheDumper();
        if (thumbcacheDumper == null) {
            this.addErrorMessage(Bundle.ExtractThumbcache_error_finding_program());
            logger.log(Level.WARNING, "Error finding thumbcache parsing program"); //NON-NLS
            return; //If we cannot find the thumbcache parser program we cannot proceed
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }
        String thumbcacheFileLocation = null;
        for (AbstractFile thumbcacheFile: thumbcacheFiles) {
            try {
                File thumbcacheFileName = new File(tempDirPath + File.separator + thumbcacheFile.getId() + "_" + thumbcacheFile.getName());
                if (thumbcacheFileName.exists()) {
                    String modOutFile = modOutPath + File.separator + thumbcacheFile.getId() + "_" +thumbcacheFile.getName();
                    thumbcacheFileLocation = tempDirPath + File.separator + thumbcacheFile.getId() + "_" + thumbcacheFile.getName();
                    
                    dir = new File(modOutFile);
                    if (dir.exists() == false) {
                        dir.mkdirs();
                    }

                    extractThumbcacheFiles(thumbcacheDumper, modOutFile, thumbcacheFileLocation);
                    addThumbcacheDerivedFiles(modOutFile, thumbcacheFile);
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error processing thumbcache file %s", thumbcacheFile.getId() + "_" + thumbcacheFile.getName()), ex); //NON-NLS=
            }
        }
    }

    /**
     * Extract the thumbcache file to the temp directory
     *
     * @param dataSource  datasource where software hive is
     * @param tempDirPath temp directory to write file to
     *      *
     * @return hive file location
     */
    List<AbstractFile> getThumbcacheFiles(Content dataSource, String tempDirPath) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> thumbcacheFiles;

        try {
            thumbcacheFiles = fileManager.findFiles(dataSource, "thumbcache_%.db", ""); //NON-NLS            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING,"Unable to find thumbcache files.", ex); //NON-NLS
            return null;  // No need to continue
        }
        
        if (thumbcacheFiles.isEmpty()) {
            return null;  // No thumbcache files found
        }
        
        for (AbstractFile thumbcacheFile : thumbcacheFiles) {
            String thumbcacheFileName = tempDirPath + File.separator + thumbcacheFile.getId() + "_" + thumbcacheFile.getName();

            try {
                ContentUtils.writeToFile(thumbcacheFile, new File(thumbcacheFileName));
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", thumbcacheFile.getName(), thumbcacheFile), ex); //NON-NLS
            }
        }

        return thumbcacheFiles;
    }

   /**
     * Run the thumbcache parser cmd program 
     *
     * @param thumbcacheExePath - Eecutable for the thumbcache program
     * @param thumbcacheFilePath - output directory for program 
     * @param tempOutPath - file to parse
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    void extractThumbcacheFiles(String thumbcacheExePath, String thumbcacheFilePath, String thumbcacheFile) throws IOException {
        final Path outputFilePath = Paths.get(thumbcacheFilePath, THUMBCACHE_OUTPUT_FILE_NAME);
        final Path errFilePath = Paths.get(thumbcacheFilePath, THUMBCACHE_ERROR_FILE_NAME);

        
        List<String> commandLine = new ArrayList<>();
        commandLine.add(thumbcacheExePath);
        commandLine.add("-O"); //NON-NLS
        commandLine.add(thumbcacheFilePath);
        commandLine.add(thumbcacheFile);


        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(outputFilePath.toFile());
        processBuilder.redirectError(errFilePath.toFile());

        ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context, true));
    }

    private String getPathForThumbcacheDumper() {
        Path path = Paths.get(THUMBCACHE_TOOL_FOLDER, THUMBCACHE_TOOL_NAME_WINDOWS);
        File thumbcacheToolFile = InstalledFileLocator.getDefault().locate(path.toString(),
                ExtractThumbcache.class.getPackage().getName(), false);
        if (thumbcacheToolFile != null) {
            return thumbcacheToolFile.getAbsolutePath();
        }

        return null;
    }

    private void addThumbcacheDerivedFiles(String outputFolder, AbstractFile thumbcacheFile) {
        Path outputFolderPath = Paths.get(outputFolder);
        List<File> files = (List<File>) FileUtils.listFiles(outputFolderPath.toFile(), null, true);
        for (File file : files) {
            if (context.dataSourceIngestIsCancelled()) {
                return;
            }
            
            Path candidate = file.toPath();
            
            if (candidate.getFileName().toString().equals(THUMBCACHE_ERROR_FILE_NAME) || candidate.getFileName().toString().equals(THUMBCACHE_OUTPUT_FILE_NAME)) {
                 continue;    
            }
            try {
                final Case currentCase = Case.getCurrentCaseThrows();
                final Path caseDirectory = Paths.get(currentCase.getCaseDirectory());
                final BasicFileAttributes attrs = Files.readAttributes(candidate, BasicFileAttributes.class);
                final Path localCasePath = caseDirectory.relativize(candidate);
            
                final DerivedFile tcacheFile = currentCase.getSleuthkitCase()
                         .addDerivedFile(candidate.getFileName().toString(),
                                localCasePath.toString(), attrs.size(), 0L,
                                attrs.creationTime().to(TimeUnit.SECONDS),
                                attrs.lastAccessTime().to(TimeUnit.SECONDS),
                                attrs.lastModifiedTime().to(TimeUnit.SECONDS),
                                attrs.isRegularFile(), thumbcacheFile, "",
                                "", "", "", TskData.EncodingType.NONE);

                context.addFilesToJob(Arrays.asList(tcacheFile));
                IngestServices.getInstance().fireModuleContentEvent(new ModuleContentEvent(tcacheFile));
            } catch (IOException ex) {
                logger.log(Level.WARNING, "I/O error encountered during thumbcache processing.", ex);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to add thumbcache as derived files.", ex);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "No open case!", ex);

            }
        }
    }    
}
