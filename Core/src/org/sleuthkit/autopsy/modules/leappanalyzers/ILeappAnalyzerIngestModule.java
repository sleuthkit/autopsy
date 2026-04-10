/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import java.util.Arrays;
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
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Data source ingest module that runs iLeapp against logical iOS files.
 */
public class ILeappAnalyzerIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(ILeappAnalyzerIngestModule.class.getName());
    private static final String MODULE_NAME = ILeappAnalyzerModuleFactory.getModuleName();

    private static final String ILEAPP = "iLeapp"; //NON-NLS
    private static final String ILEAPP_FS = "fs_"; //NON-NLS
    private static final String ILEAPP_EXECUTABLE = "ileapp.exe";//NON-NLS
    private static final String ILEAPP_PATHS_FILE = "iLeapp_paths.txt"; //NON-NLS

    private static final String XMLFILE = "ileapp-artifact-attribute-reference.xml"; //NON-NLS

    // iOS-specific files used to detect whether the data source is from an iOS device.
    // Entries cover both /private/var/mobile/ (canonical) and /var/mobile/ (symlinked path
    // that some acquisition tools use). Finding any one file is sufficient.
    private static final List<String[]> IOS_INDICATOR_FILES = Arrays.asList(
            new String[]{"sms.db", "/private/var/mobile/Library/SMS/"},                                //NON-NLS
            new String[]{"sms.db", "/var/mobile/Library/SMS/"},                                        //NON-NLS
            new String[]{"AddressBook.sqlitedb", "/private/var/mobile/Library/AddressBook/"},          //NON-NLS
            new String[]{"AddressBook.sqlitedb", "/var/mobile/Library/AddressBook/"},                  //NON-NLS
            new String[]{"com.apple.mobilephone.plist", "/private/var/mobile/Library/Preferences/"},   //NON-NLS
            new String[]{"com.apple.mobilephone.plist", "/var/mobile/Library/Preferences/"});          //NON-NLS

    private File iLeappExecutable;

    private IngestJobContext context;

    private LeappFileProcessor iLeappFileProcessor;

    ILeappAnalyzerIngestModule() {
        // This constructor is intentionally empty. Nothing special is needed here.     
    }

    @NbBundle.Messages({
        "ILeappAnalyzerIngestModule.executable.not.found=iLeapp Executable Not Found.",
        "ILeappAnalyzerIngestModule.requires.windows=iLeapp module requires windows.",
        "ILeappAnalyzerIngestModule.error.ileapp.file.processor.init=Failure to initialize ILeappProcessFile"})
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        if (false == PlatformUtil.is64BitOS()) {
            throw new IngestModuleException(NbBundle.getMessage(this.getClass(), "IleappAnalyzerIngestModule.not.64.bit.os"));
        }

        if (false == PlatformUtil.isWindowsOS()) {
            throw new IngestModuleException(Bundle.ILeappAnalyzerIngestModule_requires_windows());
        }

        try {
            iLeappFileProcessor = new LeappFileProcessor(XMLFILE, ILeappAnalyzerModuleFactory.getModuleName(), ILEAPP, context);
        } catch (IOException | IngestModuleException | NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.ILeappAnalyzerIngestModule_error_ileapp_file_processor_init(), ex);
        }

        try {
            iLeappExecutable = locateExecutable(ILEAPP_EXECUTABLE);
        } catch (FileNotFoundException exception) {
            logger.log(Level.WARNING, "iLeapp executable not found.", exception); //NON-NLS
            throw new IngestModuleException(Bundle.ILeappAnalyzerIngestModule_executable_not_found(), exception);
        }

    }

    @NbBundle.Messages({
        "ILeappAnalyzerIngestModule.error.running.iLeapp=Error running iLeapp, see log file.",
        "ILeappAnalyzerIngestModule.error.creating.output.dir=Error creating iLeapp module output directory.",
        "ILeappAnalyzerIngestModule.running.iLeapp=Running iLeapp",
        "ILeappAnalyzerIngestModule_processing_iLeapp_results=Processing iLeapp results",
        "ILeappAnalyzerIngestModule.has.run=iLeapp",
        "ILeappAnalyzerIngestModule.iLeapp.cancelled=iLeapp run was canceled",
        "ILeappAnalyzerIngestModule.completed=iLeapp Processing Completed",
        "ILeappAnalyzerIngestModule.report.name=iLeapp Html Report",
        "ILeappAnalyzerIngestModule.notIOS.skipped=iLeapp skipped: data source does not appear to be an iOS device."})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {

        if (!isIOSDataSource(dataSource)) {
            logger.log(Level.INFO, "iLeapp: data source does not appear to be iOS, skipping."); //NON-NLS
            IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                    MODULE_NAME, Bundle.ILeappAnalyzerIngestModule_notIOS_skipped());
            IngestServices.getInstance().postMessage(message);
            return ProcessResult.OK;
        }

        statusHelper.switchToIndeterminate();
        statusHelper.progress(Bundle.ILeappAnalyzerIngestModule_running_iLeapp());

        Case currentCase = Case.getCurrentCase();
        Path tempOutputPath = Paths.get(currentCase.getTempDirectory(), ILEAPP, ILEAPP_FS + dataSource.getId());
        try {
            Files.createDirectories(tempOutputPath);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error creating iLeapp output directory %s", tempOutputPath.toString()), ex);
            writeErrorMsgToIngestInbox();
            return ProcessResult.ERROR;
        }

        List<String> iLeappPathsToProcess;
        ProcessBuilder iLeappCommand = buildiLeappListCommand(tempOutputPath);
        try {
            int result = ExecUtil.execute(iLeappCommand, new DataSourceIngestModuleProcessTerminator(context, true));
            if (result != 0) {
                logger.log(Level.SEVERE, String.format("Error when trying to execute iLeapp program getting file paths to search for result is %d", result));
                writeErrorMsgToIngestInbox();
                return ProcessResult.ERROR;
            }
            iLeappPathsToProcess = loadIleappPathFile(tempOutputPath);
            if (iLeappPathsToProcess.isEmpty()) {
                logger.log(Level.SEVERE, String.format("Error getting file paths to search, list is empty"));
                writeErrorMsgToIngestInbox();
                return ProcessResult.ERROR;
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error when trying to execute iLeapp program getting file paths to search"), ex);
            writeErrorMsgToIngestInbox();
            return ProcessResult.ERROR;
        }

        if ((context.getDataSource() instanceof LocalFilesDataSource)) {
            /*
             * The data source may be local files from an iOS file system, or it
             * may be a tarred/ZIP of an iOS file system. If it is the latter,
             * extract the files we need to process.
             */
            List<AbstractFile> iLeappFilesToProcess = LeappFileProcessor.findLeappFilesToProcess(dataSource);
            if (!iLeappFilesToProcess.isEmpty()) {
                statusHelper.switchToDeterminate(iLeappFilesToProcess.size());
                Integer filesProcessedCount = 0;
                for (AbstractFile iLeappFile : iLeappFilesToProcess) {
                    processILeappFile(dataSource, currentCase, statusHelper, filesProcessedCount, iLeappFile);
                    filesProcessedCount++;
                }
            }
        }

        statusHelper.switchToIndeterminate();
        statusHelper.progress(Bundle.ILeappAnalyzerIngestModule_processing_iLeapp_results());
        extractFilesFromDataSource(dataSource, iLeappPathsToProcess, tempOutputPath);
        processILeappFs(dataSource, currentCase, statusHelper, tempOutputPath.toString());

        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                Bundle.ILeappAnalyzerIngestModule_has_run(),
                Bundle.ILeappAnalyzerIngestModule_completed());
        IngestServices.getInstance().postMessage(message);
        return ProcessResult.OK;
    }

    /**
     * Process each tar/zip file that is found in a logical image that contains
     * xLeapp data
     *
     * @param dataSource          Datasource where the file has been found
     * @param currentCase         current case
     * @param statusHelper        Progress bar for messages to show user
     * @param filesProcessedCount count that is incremented for progress bar
     * @param iLeappFile          abstract file that will be processed
     */
    private void processILeappFile(Content dataSource, Case currentCase, DataSourceIngestModuleProgress statusHelper, int filesProcessedCount,
            AbstractFile iLeappFile) {
        statusHelper.progress(NbBundle.getMessage(this.getClass(), "ILeappAnalyzerIngestModule.processing.file", iLeappFile.getName()), filesProcessedCount);

        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss z", Locale.US).format(System.currentTimeMillis());//NON-NLS
        Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), ILEAPP, currentTime);
        try {
            Files.createDirectories(moduleOutputPath);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error creating iLeapp output directory %s", moduleOutputPath.toString()), ex);
            return;
        }

        ProcessBuilder iLeappCommand = buildiLeappCommand(moduleOutputPath, iLeappFile.getLocalAbsPath(), iLeappFile.getNameExtension());
        try {
            int result = ExecUtil.execute(iLeappCommand, new DataSourceIngestModuleProcessTerminator(context, true));
            if (result != 0) {
                logger.log(Level.WARNING, String.format("Error when trying to execute iLeapp program getting file paths to search for result is %d", result));
                return;
            }

            addILeappReportToReports(moduleOutputPath, currentCase);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error when trying to execute iLeapp program against file %s", iLeappFile.getLocalAbsPath()), ex);
            return;
        }

        if (context.dataSourceIngestIsCancelled()) {
            logger.log(Level.INFO, "ILeapp Analyser ingest module run was canceled"); //NON-NLS
            return;
        }

        iLeappFileProcessor.processFiles(dataSource, moduleOutputPath, iLeappFile, statusHelper);
    }

    /**
     * Process extracted files from a disk image using xLeapp
     *
     * @param dataSource         Datasource where the file has been found
     * @param currentCase        current case
     * @param statusHelper       Progress bar for messages to show user
     * @param directoryToProcess
     */
    private void processILeappFs(Content dataSource, Case currentCase, DataSourceIngestModuleProgress statusHelper, String directoryToProcess) {
        statusHelper.progress(NbBundle.getMessage(this.getClass(), "ILeappAnalyzerIngestModule.processing.filesystem"));
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss z", Locale.US).format(System.currentTimeMillis());//NON-NLS
        Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), ILEAPP, currentTime);
        try {
            Files.createDirectories(moduleOutputPath);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error creating iLeapp output directory %s", moduleOutputPath.toString()), ex);
            return;
        }

        ProcessBuilder iLeappCommand = buildiLeappCommand(moduleOutputPath, directoryToProcess, "fs");
        try {
            int result = ExecUtil.execute(iLeappCommand, new DataSourceIngestModuleProcessTerminator(context, true));
            if (result != 0) {
                logger.log(Level.WARNING, String.format("Error when trying to execute iLeapp program getting file paths to search for result is %d", result));
                return;
            }

            addILeappReportToReports(moduleOutputPath, currentCase);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error when trying to execute iLeapp program against file system"), ex);
            return;
        }

        if (context.dataSourceIngestIsCancelled()) {
            logger.log(Level.INFO, "ILeapp Analyser ingest module run was canceled"); //NON-NLS
            return;
        }

        iLeappFileProcessor.processFileSystem(dataSource, moduleOutputPath, statusHelper);
    }

    /**
     * Build the command to run xLeapp
     *
     * @param moduleOutputPath     output path for xLeapp
     * @param sourceFilePath       path where the xLeapp file is
     * @param iLeappFileSystemType type of file to process tar/zip/fs
     *
     * @return process to run
     */
    private ProcessBuilder buildiLeappCommand(Path moduleOutputPath, String sourceFilePath, String iLeappFileSystemType) {

        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                iLeappExecutable.getAbsolutePath(), //NON-NLS
                "-t", iLeappFileSystemType, //NON-NLS
                "-i", sourceFilePath, //NON-NLS
                "-o", moduleOutputPath.toString()
        );
        processBuilder.directory(moduleOutputPath.toFile());
        processBuilder.redirectError(moduleOutputPath.resolve("iLeapp_err.txt").toFile());  //NON-NLS
        processBuilder.redirectOutput(moduleOutputPath.resolve("iLeapp_out.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    /**
     * Command to run xLeapp using the path option
     *
     * @param moduleOutputPath path where the file paths output will reside
     *
     * @return process to run
     */
    private ProcessBuilder buildiLeappListCommand(Path moduleOutputPath) {

        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                iLeappExecutable.getAbsolutePath(), //NON-NLS
                "-p"
        );
        // leapp process also outputs a file to the working directory in addition to stdout.
        processBuilder.directory(moduleOutputPath.toFile());
        processBuilder.redirectError(moduleOutputPath.resolve("iLeapp_paths_error.txt").toFile());  //NON-NLS
        processBuilder.redirectOutput(moduleOutputPath.resolve("iLeapp_paths.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    static private ProcessBuilder buildProcessWithRunAsInvoker(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force iLeapp to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        return processBuilder;
    }

    private static File locateExecutable(String executableName) throws FileNotFoundException {
        String executableToFindName = Paths.get(ILEAPP, executableName).toString();

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, ILeappAnalyzerIngestModule.class.getPackage().getName(), false);
        if (null == exeFile || exeFile.canExecute() == false) {
            throw new FileNotFoundException(executableName + " executable not found.");
        }
        return exeFile;
    }

    /**
     * Find the index.html file in the iLeapp output directory so it can be
     * added to reports
     */
    private void addILeappReportToReports(Path iLeappOutputDir, Case currentCase) {
        List<String> allIndexFiles;

        try (Stream<Path> walk = Files.walk(iLeappOutputDir)) {

            allIndexFiles = walk.map(x -> x.toString())
                    .filter(f -> f.toLowerCase().endsWith("index.html")).collect(Collectors.toList());

            if (!allIndexFiles.isEmpty()) {
                // Check for existance of directory that holds report data if does not exist then report contains no data
                String filePath = FilenameUtils.getFullPathNoEndSeparator(allIndexFiles.get(0));
                File dataFilesDir = new File(Paths.get(filePath, "_TSV Exports").toString());
                if (dataFilesDir.exists()) {
                    currentCase.addReport(allIndexFiles.get(0), MODULE_NAME, Bundle.ILeappAnalyzerIngestModule_report_name());
                }
            }

        } catch (IOException | UncheckedIOException | TskCoreException ex) {
            // catch the error and continue on as report is not added
            logger.log(Level.WARNING, String.format("Error finding index file in path %s", iLeappOutputDir.toString()), ex);
        }

    }

    /*
     * Reads the iLeapp paths file to get the paths that we want to extract
     *
     * @param moduleOutputPath path where the file paths output will reside
     */
    private List<String> loadIleappPathFile(Path moduleOutputPath) throws FileNotFoundException, IOException {
        List<String> iLeappPathsToProcess = new ArrayList<>();

        Path filePath = Paths.get(moduleOutputPath.toString(), ILEAPP_PATHS_FILE);

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toString()))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.contains("path list generation") || line.length() < 2) {
                    line = reader.readLine();
                    continue;
                }
                iLeappPathsToProcess.add(line.trim());
                line = reader.readLine();
            }
        }

        return iLeappPathsToProcess;
    }

    /**
     * Extract files from a disk image to process with xLeapp
     *
     * @param dataSource           Datasource of the image
     * @param iLeappPathsToProcess List of paths to extract content from
     * @param moduleOutputPath     path to write content to
     */
    private void extractFilesFromDataSource(Content dataSource, List<String> iLeappPathsToProcess, Path moduleOutputPath) {
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        for (String fullFilePath : iLeappPathsToProcess) {

            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, "ILeapp Analyser ingest module run was canceled"); //NON-NLS
                break;
            }

            String ffp = fullFilePath.replaceAll("\\*", "%");
            ffp = FilenameUtils.normalize(ffp, true);
            String fileName = FilenameUtils.getName(ffp);
            String filePath = FilenameUtils.getPath(ffp);

            List<AbstractFile> iLeappFiles;
            try {
                if (filePath.isEmpty()) {
                    iLeappFiles = fileManager.findFiles(dataSource, fileName); //NON-NLS                
                } else {
                    iLeappFiles = fileManager.findFiles(dataSource, fileName, filePath); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "No files found to process"); //NON-NLS
                return;
            }

            for (AbstractFile iLeappFile : iLeappFiles) {
                Path parentPath = Paths.get(moduleOutputPath.toString(), iLeappFile.getParentPath());
                File fileParentPath = new File(parentPath.toString());

                extractFileToOutput(dataSource, iLeappFile, fileParentPath, parentPath);
            }
        }
    }

    /**
     * Create path and file from datasource in temp
     *
     * @param dataSource     datasource of the image
     * @param iLeappFile     abstract file to write out
     * @param fileParentPath parent file path
     * @param parentPath     parent file
     */
    private void extractFileToOutput(Content dataSource, AbstractFile iLeappFile, File fileParentPath, Path parentPath) {
        if (fileParentPath.exists()) {
            if (!iLeappFile.isDir()) {
                writeiLeappFile(dataSource, iLeappFile, fileParentPath.toString());
            } else {
                try {
                    Files.createDirectories(Paths.get(parentPath.toString(), iLeappFile.getName()));
                } catch (IOException ex) {
                    logger.log(Level.INFO, String.format("Error creating iLeapp output directory %s", parentPath.toString()), ex);
                }
            }
        } else {
            try {
                Files.createDirectories(parentPath);
            } catch (IOException ex) {
                logger.log(Level.INFO, String.format("Error creating iLeapp output directory %s", parentPath.toString()), ex);
            }
            if (!iLeappFile.isDir()) {
                writeiLeappFile(dataSource, iLeappFile, fileParentPath.toString());
            } else {
                try {
                    Files.createDirectories(Paths.get(parentPath.toString(), iLeappFile.getName()));
                } catch (IOException ex) {
                    logger.log(Level.INFO, String.format("Error creating iLeapp output directory %s", parentPath.toString()), ex);
                }
            }
        }
    }

    /**
     * Write out file to output
     *
     * @param dataSource datasource of disk image
     * @param iLeappFile acstract file to write out
     * @param parentPath path to write file to
     */
    private void writeiLeappFile(Content dataSource, AbstractFile iLeappFile, String parentPath) {
        String fileName = iLeappFile.getName().replace(":", "-");
        if (!fileName.matches(".") && !fileName.matches("..") && !fileName.toLowerCase().endsWith("-slack")) {
            Path filePath = Paths.get(parentPath, fileName);
            File localFile = new File(filePath.toString());
            try {
                ContentUtils.writeToFile(iLeappFile, localFile, context::dataSourceIngestIsCancelled);
            } catch (ReadContentInputStream.ReadContentInputStreamException ex) {
                logger.log(Level.WARNING, String.format("Error reading file '%s' (id=%d).",
                        iLeappFile.getName(), iLeappFile.getId()), ex); //NON-NLS
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Error writing file local file '%s' (id=%d).",
                        filePath.toString(), iLeappFile.getId()), ex); //NON-NLS
            }
        }
    }

    /**
     * Determines whether the data source appears to be from an iOS device.
     * For disk images, rules out clearly non-iOS filesystem types first (fast,
     * no I/O): Ext2/3/4, YAFFS2 (Android), and NTFS (Windows) are
     * iOS-negative. HFS and APFS are ambiguous (also used by macOS) and fall
     * through to a file-based check. FAT and unknown types also fall through.
     *
     * @param dataSource the data source to evaluate
     *
     * @return true if the data source appears to be iOS
     */
    private boolean isIOSDataSource(Content dataSource) {
        if (dataSource instanceof Image) {
            try {
                boolean hasDefinitelyNonIOSFs = false;
                for (FileSystem fs : ((Image) dataSource).getFileSystems()) {
                    switch (fs.getFsType()) {
                        case TSK_FS_TYPE_EXT2:
                        case TSK_FS_TYPE_EXT3:
                        case TSK_FS_TYPE_EXT4:
                        case TSK_FS_TYPE_EXT_DETECT:
                        case TSK_FS_TYPE_YAFFS2:
                        case TSK_FS_TYPE_YAFFS2_DETECT:
                        case TSK_FS_TYPE_NTFS:
                        case TSK_FS_TYPE_NTFS_DETECT:
                            hasDefinitelyNonIOSFs = true;
                            break;
                        default:
                            break;
                    }
                }
                if (hasDefinitelyNonIOSFs) {
                    return false;
                }
                // HFS, APFS, FAT, or unknown: fall through to file check
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error checking filesystem types for iLeapp iOS detection", ex); //NON-NLS
            }
        }
        return hasIOSIndicatorFiles(dataSource);
    }

    /**
     * Searches for a small set of files that are present on virtually all iOS
     * devices under /private/var/mobile/ or /var/mobile/ (symlinked path used
     * by some acquisition tools). Returns true as soon as any one is found.
     *
     * @param dataSource the data source to search
     *
     * @return true if at least one iOS indicator file is found
     */
    private boolean hasIOSIndicatorFiles(Content dataSource) {
        FileManager fileManager = getCurrentCase().getServices().getFileManager();
        for (String[] fileInfo : IOS_INDICATOR_FILES) {
            try {
                if (!fileManager.findFiles(dataSource, fileInfo[0], fileInfo[1]).isEmpty()) {
                    return true;
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Error searching for iOS indicator file '%s'", fileInfo[0]), ex); //NON-NLS
                return true; // Fail open: an inconclusive search is not a negative result.
            }
        }
        return false;
    }

    /**
     * Writes a generic error message to the ingest inbox, directing the user to
     * consult the application log fpor more details.
     */
    private void writeErrorMsgToIngestInbox() {
        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.ERROR,
                MODULE_NAME,
                Bundle.ILeappAnalyzerIngestModule_error_running_iLeapp());
        IngestServices.getInstance().postMessage(message);
    }

}
