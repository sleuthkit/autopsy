/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.leappanalyzers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.getCurrentCase;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Data source ingest module that runs aLeapp against logical iOS files.
 */
public class ALeappAnalyzerIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(ALeappAnalyzerIngestModule.class.getName());
    private static final String MODULE_NAME = ALeappAnalyzerModuleFactory.getModuleName();

    private static final String ALEAPP = "aLeapp"; //NON-NLS
    private static final String ALEAPP_FS = "fs_"; //NON-NLS
    private static final String ALEAPP_EXECUTABLE = "aleapp.exe";//NON-NLS
    private static final String ALEAPP_PATHS_FILE = "aLeapp_paths.txt"; //NON-NLS
    
    private static final String XMLFILE = "aleap-artifact-attribute-reference.xml"; //NON-NLS


    private File aLeappExecutable;

    private IngestJobContext context;

    private LeappFileProcessor aLeappFileProcessor;

    ALeappAnalyzerIngestModule() {
        // This constructor is intentionally empty. Nothing special is needed here.     
    }

    @NbBundle.Messages({
        "ALeappAnalyzerIngestModule.executable.not.found=aLeapp Executable Not Found.",
        "ALeappAnalyzerIngestModule.requires.windows=aLeapp module requires windows.",
        "ALeappAnalyzerIngestModule.error.ileapp.file.processor.init=Failure to initialize aLeappProcessFile"})
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        if (false == PlatformUtil.isWindowsOS()) {
            throw new IngestModuleException(Bundle.ALeappAnalyzerIngestModule_requires_windows());
        }

        try {
            aLeappFileProcessor = new LeappFileProcessor(XMLFILE);
        } catch (IOException | IngestModuleException | NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.ALeappAnalyzerIngestModule_error_ileapp_file_processor_init(), ex);
        }

        try {
            aLeappExecutable = locateExecutable(ALEAPP_EXECUTABLE);
        } catch (FileNotFoundException exception) {
            logger.log(Level.WARNING, "aLeapp executable not found.", exception); //NON-NLS
            throw new IngestModuleException(Bundle.ALeappAnalyzerIngestModule_executable_not_found(), exception);
        }

    }

    @NbBundle.Messages({
        "ALeappAnalyzerIngestModule.error.running.aLeapp=Error running aLeapp, see log file.",
        "ALeappAnalyzerIngestModule.error.creating.output.dir=Error creating aLeapp module output directory.",
        "ALeappAnalyzerIngestModule.starting.aLeapp=Starting aLeapp",
        "ALeappAnalyzerIngestModule.running.aLeapp=Running aLeapp",
        "ALeappAnalyzerIngestModule.has.run=aLeapp",
        "ALeappAnalyzerIngestModule.aLeapp.cancelled=aLeapp run was canceled",
        "ALeappAnalyzerIngestModule.completed=aLeapp Processing Completed",
        "ALeappAnalyzerIngestModule.report.name=aLeapp Html Report"})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {

        Case currentCase = Case.getCurrentCase();
        Path tempOutputPath = Paths.get(currentCase.getTempDirectory(), ALEAPP, ALEAPP_FS + dataSource.getId());
        try {
            Files.createDirectories(tempOutputPath);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error creating aLeapp output directory %s", tempOutputPath.toString()), ex);
            return ProcessResult.ERROR;
        }

        List<String> aLeappPathsToProcess = new ArrayList<>();
        ProcessBuilder aLeappCommand = buildaLeappListCommand(tempOutputPath);
        try {
            int result = ExecUtil.execute(aLeappCommand, new DataSourceIngestModuleProcessTerminator(context, true));
            if (result != 0) {
                logger.log(Level.SEVERE, String.format("Error when trying to execute aLeapp program getting file paths to search for result is %d", result));
                return ProcessResult.ERROR;
            }
            aLeappPathsToProcess = loadIleappPathFile(tempOutputPath);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error when trying to execute aLeapp program getting file paths to search"), ex);
            return ProcessResult.ERROR;
        }

        statusHelper.progress(Bundle.ALeappAnalyzerIngestModule_starting_aLeapp(), 0);

        List<AbstractFile> aLeappFilesToProcess = new ArrayList<>();

        if (!(context.getDataSource() instanceof LocalFilesDataSource)) {
            extractFilesFromImage(dataSource, aLeappPathsToProcess, tempOutputPath);
            statusHelper.switchToDeterminate(aLeappFilesToProcess.size());
            processALeappFs(dataSource, currentCase, statusHelper, tempOutputPath.toString());
        } else {
            aLeappFilesToProcess = findaLeappFilesToProcess(dataSource);
            statusHelper.switchToDeterminate(aLeappFilesToProcess.size());

            Integer filesProcessedCount = 0;
            for (AbstractFile aLeappFile : aLeappFilesToProcess) {
                processALeappFile(dataSource, currentCase, statusHelper, filesProcessedCount, aLeappFile);
                filesProcessedCount++;
            }
            // Process the logical image as a fs in aLeapp to make sure this is not a logical fs that was added
            extractFilesFromImage(dataSource, aLeappPathsToProcess, tempOutputPath);
            processALeappFs(dataSource, currentCase, statusHelper, tempOutputPath.toString());
        }

        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                Bundle.ALeappAnalyzerIngestModule_has_run(),
                Bundle.ALeappAnalyzerIngestModule_completed());
        IngestServices.getInstance().postMessage(message);
        return ProcessResult.OK;
    }

    /**
     * Process a file from a logical image using the aLeapp program
     * @param dataSource datasource to process
     * @param currentCase current case that is being worked on
     * @param statusHelper show progress and update what is being processed
     * @param filesProcessedCount number of files that have been processed
     * @param aLeappFile the abstract file to process
     */
    private void processALeappFile(Content dataSource, Case currentCase, DataSourceIngestModuleProgress statusHelper, int filesProcessedCount,
            AbstractFile aLeappFile) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss z", Locale.US).format(System.currentTimeMillis());//NON-NLS
        Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), ALEAPP, currentTime);
        try {
            Files.createDirectories(moduleOutputPath);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error creating aLeapp output directory %s", moduleOutputPath.toString()), ex);
            return;
        }

        statusHelper.progress(NbBundle.getMessage(this.getClass(), "ALeappAnalyzerIngestModule.processing.file", aLeappFile.getName()), filesProcessedCount);
        ProcessBuilder aLeappCommand = buildaLeappCommand(moduleOutputPath, aLeappFile.getLocalAbsPath(), aLeappFile.getNameExtension());
        try {
            int result = ExecUtil.execute(aLeappCommand, new DataSourceIngestModuleProcessTerminator(context, true));
            if (result != 0) {
                logger.log(Level.WARNING, String.format("Error when trying to execute aLeapp program getting file paths to search for result is %d", result));
                return;
            }

            addILeappReportToReports(moduleOutputPath, currentCase);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error when trying to execute aLeapp program against file %s", aLeappFile.getLocalAbsPath()), ex);
            return;
        }

        if (context.dataSourceIngestIsCancelled()) {
            logger.log(Level.INFO, "ILeapp Analyser ingest module run was canceled"); //NON-NLS
            return;
        }

        ProcessResult fileProcessorResult = aLeappFileProcessor.processFiles(dataSource, moduleOutputPath, aLeappFile);

        if (fileProcessorResult == ProcessResult.ERROR) {
            return;
        }
    }

    /**
     * Process a image/directory using the aLeapp program
     * @param dataSource datasource to process
     * @param currentCase current case being procesed
     * @param statusHelper show progress and update what is being processed
     * @param directoryToProcess directory to run aLeapp against
     */
    private void processALeappFs(Content dataSource, Case currentCase, DataSourceIngestModuleProgress statusHelper, String directoryToProcess) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss z", Locale.US).format(System.currentTimeMillis());//NON-NLS
        Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), ALEAPP, currentTime);
        try {
            Files.createDirectories(moduleOutputPath);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error creating aLeapp output directory %s", moduleOutputPath.toString()), ex);
            return;
        }

        statusHelper.progress(NbBundle.getMessage(this.getClass(), "ALeappAnalyzerIngestModule.processing.filesystem"));
        ProcessBuilder aLeappCommand = buildaLeappCommand(moduleOutputPath, directoryToProcess, "fs");
        try {
            int result = ExecUtil.execute(aLeappCommand, new DataSourceIngestModuleProcessTerminator(context, true));
            if (result != 0) {
                logger.log(Level.WARNING, String.format("Error when trying to execute aLeapp program getting file paths to search for result is %d", result));
                return;
            }

            addILeappReportToReports(moduleOutputPath, currentCase);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error when trying to execute aLeapp program against file system"), ex);
            return;
        }

        if (context.dataSourceIngestIsCancelled()) {
            logger.log(Level.INFO, "ILeapp Analyser ingest module run was canceled"); //NON-NLS
            return;
        }

        ProcessResult fileProcessorResult = aLeappFileProcessor.processFileSystem(dataSource, moduleOutputPath);

        if (fileProcessorResult == ProcessResult.ERROR) {
            return;
        }

    }

    /**
     * Find the files that will be processed by the aLeapp program
     *
     * @param dataSource
     *
     * @return List of abstract files to process.
     */
    private List<AbstractFile> findaLeappFilesToProcess(Content dataSource) {

        List<AbstractFile> aLeappFiles = new ArrayList<>();

        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        // findFiles use the SQL wildcard % in the file name
        try {
            aLeappFiles = fileManager.findFiles(dataSource, "%", "/"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "No files found to process"); //NON-NLS
            return aLeappFiles;
        }

        List<AbstractFile> aLeappFilesToProcess = new ArrayList<>();
        for (AbstractFile aLeappFile : aLeappFiles) {
            if (((aLeappFile.getLocalAbsPath() != null)
                    && (!aLeappFile.getNameExtension().isEmpty() && (!aLeappFile.isVirtual())))
                    && ((aLeappFile.getName().toLowerCase().contains(".zip") || (aLeappFile.getName().toLowerCase().contains(".tar")))
                    || aLeappFile.getName().toLowerCase().contains(".tgz"))) {
                aLeappFilesToProcess.add(aLeappFile);

            }
        }

        return aLeappFilesToProcess;
    }

    /**
     * Build the aLeapp command to run
     * 
     * @param moduleOutputPath output path for the aLeapp program.
     * @param sourceFilePath where the source files to process reside.
     * @param aLeappFileSystemType the filesystem type to process
     * 
     * @return the command to execute
     */
    private ProcessBuilder buildaLeappCommand(Path moduleOutputPath, String sourceFilePath, String aLeappFileSystemType) {

        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + aLeappExecutable + "\"", //NON-NLS
                "-t", aLeappFileSystemType, //NON-NLS
                "-i", sourceFilePath, //NON-NLS
                "-o", moduleOutputPath.toString()
        );
        processBuilder.redirectError(moduleOutputPath.resolve("aLeapp_err.txt").toFile());  //NON-NLS
        processBuilder.redirectOutput(moduleOutputPath.resolve("aLeapp_out.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    private ProcessBuilder buildaLeappListCommand(Path moduleOutputPath) {

        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + aLeappExecutable + "\"", //NON-NLS
                "-p"
        );
        processBuilder.redirectError(moduleOutputPath.resolve("aLeapp_paths_error.txt").toFile());  //NON-NLS
        processBuilder.redirectOutput(moduleOutputPath.resolve("aLeapp_paths.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    static private ProcessBuilder buildProcessWithRunAsInvoker(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force aLeapp to run with
         * the same permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        return processBuilder;
    }

    private static File locateExecutable(String executableName) throws FileNotFoundException {
        String executableToFindName = Paths.get(ALEAPP, executableName).toString();

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, ALeappAnalyzerIngestModule.class.getPackage().getName(), false);
        if (null == exeFile || exeFile.canExecute() == false) {
            throw new FileNotFoundException(executableName + " executable not found.");
        }
        return exeFile;
    }

    /**
     * Find the index.html file in the aLeapp output directory so it can be
     * added to reports
     */
    private void addILeappReportToReports(Path aLeappOutputDir, Case currentCase) {
        List<String> allIndexFiles = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(aLeappOutputDir)) { 

            allIndexFiles = walk.map(x -> x.toString())
                    .filter(f -> f.toLowerCase().endsWith("index.html")).collect(Collectors.toList());

            if (!allIndexFiles.isEmpty()) {
                // Check for existance of directory that holds report data if does not exist then report contains no data
                String filePath = FilenameUtils.getFullPathNoEndSeparator(allIndexFiles.get(0));
                File dataFilesDir = new File(Paths.get(filePath, "_TSV Exports").toString());
                if (dataFilesDir.exists()) {
                    currentCase.addReport(allIndexFiles.get(0), MODULE_NAME, Bundle.ALeappAnalyzerIngestModule_report_name());
                }
            }

        } catch (IOException | UncheckedIOException | TskCoreException ex) {
            // catch the error and continue on as report is not added
            logger.log(Level.WARNING, String.format("Error finding index file in path %s", aLeappOutputDir.toString()), ex);
        }

    }

    /*
     * Reads the aLeapp paths file to get the paths that we want to extract
     *
     */
    private List<String> loadIleappPathFile(Path moduleOutputPath) throws FileNotFoundException, IOException {
        List<String> aLeappPathsToProcess = new ArrayList<>();

        Path filePath = Paths.get(moduleOutputPath.toString(), ALEAPP_PATHS_FILE);

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toString()))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.contains("path list generation") || line.length() < 2) {
                    line = reader.readLine();
                    continue;
                }
                aLeappPathsToProcess.add(line.trim());
                line = reader.readLine();
            }
        }

        return aLeappPathsToProcess;
    }

    private void extractFilesFromImage(Content dataSource, List<String> aLeappPathsToProcess, Path moduleOutputPath) {
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        for (String fullFilePath : aLeappPathsToProcess) {

            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, "aLeapp Analyser ingest module run was canceled"); //NON-NLS
                break;
            }

            String ffp = fullFilePath.replaceAll("\\*", "%");
            ffp = FilenameUtils.normalize(ffp, true);
            String fileName = FilenameUtils.getName(ffp);
            String filePath = FilenameUtils.getPath(ffp);

            List<AbstractFile> aLeappFiles = new ArrayList<>();
            try {
                if (filePath.isEmpty()) {
                    aLeappFiles = fileManager.findFiles(dataSource, fileName); //NON-NLS                
                } else {
                    aLeappFiles = fileManager.findFiles(dataSource, fileName, filePath); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "No files found to process"); //NON-NLS
                return;
            }

            for (AbstractFile aLeappFile : aLeappFiles) {
                Path parentPath = Paths.get(moduleOutputPath.toString(), aLeappFile.getParentPath());
                File fileParentPath = new File(parentPath.toString());

                extractFileToOutput(dataSource, aLeappFile, fileParentPath, parentPath);
            }
        }
    }

    private void extractFileToOutput(Content dataSource, AbstractFile aLeappFile, File fileParentPath, Path parentPath) {
        if (fileParentPath.exists()) {
                    if (!aLeappFile.isDir()) {
                        writeaLeappFile(dataSource, aLeappFile, fileParentPath.toString());
                    } else {
                        try {
                            Files.createDirectories(Paths.get(parentPath.toString(), aLeappFile.getName()));
                        } catch (IOException ex) {
                            logger.log(Level.INFO, String.format("Error creating aLeapp output directory %s", parentPath.toString()), ex);
                        }
                    }
                } else {
                    try {
                        Files.createDirectories(parentPath);
                    } catch (IOException ex) {
                        logger.log(Level.INFO, String.format("Error creating aLeapp output directory %s", parentPath.toString()), ex);
                    }
                    if (!aLeappFile.isDir()) {
                        writeaLeappFile(dataSource, aLeappFile, fileParentPath.toString());
                    } else {
                        try {
                            Files.createDirectories(Paths.get(parentPath.toString(), aLeappFile.getName()));
                        } catch (IOException ex) {
                            logger.log(Level.INFO, String.format("Error creating aLeapp output directory %s", parentPath.toString()), ex);
                        }
                    }
                }
    }
    
    private void writeaLeappFile(Content dataSource, AbstractFile aLeappFile, String parentPath) {
        String fileName = aLeappFile.getName().replace(":", "-");
        if (!fileName.matches(".") && !fileName.matches("..") && !fileName.toLowerCase().endsWith("-slack")) {
            Path filePath = Paths.get(parentPath, fileName);
            File localFile = new File(filePath.toString());
            try {
                ContentUtils.writeToFile(aLeappFile, localFile, context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStream.ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading file '%s' (id=%d).",
                        aLeappFile.getName(), aLeappFile.getId()), ex); //NON-NLS
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Error writing file local file '%s' (id=%d).",
                        filePath.toString(), aLeappFile.getId()), ex); //NON-NLS
            }
        }
    }
}
