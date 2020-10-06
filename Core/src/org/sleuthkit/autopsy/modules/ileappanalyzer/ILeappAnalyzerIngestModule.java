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
package org.sleuthkit.autopsy.modules.ileappanalyzer;

import java.io.File;
import java.io.FileNotFoundException;
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
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.getCurrentCase;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
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
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Data source ingest module that runs iLeapp against logical iOS files.
 */
public class ILeappAnalyzerIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(ILeappAnalyzerIngestModule.class.getName());
    private static final String MODULE_NAME = ILeappAnalyzerModuleFactory.getModuleName();

    private static final String ILEAPP = "iLeapp"; //NON-NLS
    private static final String ILEAPP_EXECUTABLE = "ileapp.exe";//NON-NLS

    private File iLeappExecutable;

    private IngestJobContext context;

    private ILeappFileProcessor iLeappFileProcessor;

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

        if (false == PlatformUtil.isWindowsOS()) {
            throw new IngestModuleException(Bundle.ILeappAnalyzerIngestModule_requires_windows());
        }

        try {
            iLeappFileProcessor = new ILeappFileProcessor();
        } catch (IOException | IngestModuleException ex) {
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
        "ILeappAnalyzerIngestModule.starting.iLeapp=Starting iLeapp",
        "ILeappAnalyzerIngestModule.running.iLeapp=Running iLeapp",
        "ILeappAnalyzerIngestModule.has.run=iLeapp",
        "ILeappAnalyzerIngestModule.iLeapp.cancelled=iLeapp run was canceled",
        "ILeappAnalyzerIngestModule.completed=iLeapp Processing Completed",
        "ILeappAnalyzerIngestModule.report.name=iLeapp Html Report"})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {

        if (!(context.getDataSource() instanceof LocalFilesDataSource)) {
            return ProcessResult.OK;
        }

        statusHelper.progress(Bundle.ILeappAnalyzerIngestModule_starting_iLeapp(), 0);

        List<AbstractFile> iLeappFilesToProcess = findiLeappFilesToProcess(dataSource);

        statusHelper.switchToDeterminate(iLeappFilesToProcess.size());

        Integer filesProcessedCount = 0;

        Case currentCase = Case.getCurrentCase();
        for (AbstractFile iLeappFile : iLeappFilesToProcess) {

            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss z", Locale.US).format(System.currentTimeMillis());//NON-NLS
            Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), ILEAPP, currentTime);
            try {
                Files.createDirectories(moduleOutputPath);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error creating iLeapp output directory %s", moduleOutputPath.toString()), ex);
                return ProcessResult.ERROR;
            }

            statusHelper.progress(NbBundle.getMessage(this.getClass(), "ILeappAnalyzerIngestModule.processing.file", iLeappFile.getName()), filesProcessedCount);
            ProcessBuilder iLeappCommand = buildiLeappCommand(moduleOutputPath, iLeappFile.getLocalAbsPath(), iLeappFile.getNameExtension());
            try {
                int result = ExecUtil.execute(iLeappCommand, new DataSourceIngestModuleProcessTerminator(context, true));
                if (result != 0) {
                    // ignore if there is an error and continue to try and process the next file if there is one
                    continue;
                }

                addILeappReportToReports(moduleOutputPath, currentCase);

            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error when trying to execute iLeapp program against file %s", iLeappFile.getLocalAbsPath()), ex);
                return ProcessResult.ERROR;
            }

            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, "ILeapp Analyser ingest module run was canceled"); //NON-NLS
                return ProcessResult.OK;
            }

            ProcessResult fileProcessorResult = iLeappFileProcessor.processFiles(dataSource, moduleOutputPath, iLeappFile);

            if (fileProcessorResult == ProcessResult.ERROR) {
                return ProcessResult.ERROR;
            }

            filesProcessedCount++;
        }

        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                Bundle.ILeappAnalyzerIngestModule_has_run(),
                Bundle.ILeappAnalyzerIngestModule_completed());
        IngestServices.getInstance().postMessage(message);
        return ProcessResult.OK;
    }

    /**
     * Find the files that will be processed by the iLeapp program
     *
     * @param dataSource
     *
     * @return List of abstract files to process.
     */
    private List<AbstractFile> findiLeappFilesToProcess(Content dataSource) {

        List<AbstractFile> iLeappFiles = new ArrayList<>();

        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        // findFiles use the SQL wildcard % in the file name
        try {
            iLeappFiles = fileManager.findFiles(dataSource, "%", "/"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "No files found to process"); //NON-NLS
            return iLeappFiles;
        }

        List<AbstractFile> iLeappFilesToProcess = new ArrayList<>();
        for (AbstractFile iLeappFile : iLeappFiles) {
            if (((iLeappFile.getLocalAbsPath() != null)
                    && (!iLeappFile.getNameExtension().isEmpty() && (!iLeappFile.isVirtual()))) 
                && ((iLeappFile.getName().toLowerCase().contains(".zip") || (iLeappFile.getName().toLowerCase().contains(".tar")))
                        || iLeappFile.getName().toLowerCase().contains(".tgz"))) {
                    iLeappFilesToProcess.add(iLeappFile);
                
            }
        }

        return iLeappFilesToProcess;
    }

    private ProcessBuilder buildiLeappCommand(Path moduleOutputPath, String sourceFilePath, String iLeappFileSystemType) {

        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + iLeappExecutable + "\"", //NON-NLS
                "-t", iLeappFileSystemType, //NON-NLS
                "-i", sourceFilePath, //NON-NLS
                "-o", moduleOutputPath.toString()
        );
        processBuilder.redirectError(moduleOutputPath.resolve("iLeapp_err.txt").toFile());  //NON-NLS
        processBuilder.redirectOutput(moduleOutputPath.resolve("iLeapp_out.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    static private ProcessBuilder buildProcessWithRunAsInvoker(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force log2timeline/psort to run with
         * the same permissions Autopsy uses.
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
        List<String> allIndexFiles = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(iLeappOutputDir)) {

            allIndexFiles = walk.map(x -> x.toString())
                    .filter(f -> f.toLowerCase().endsWith("index.html")).collect(Collectors.toList());

            if (!allIndexFiles.isEmpty()) {
                currentCase.addReport(allIndexFiles.get(0), MODULE_NAME, Bundle.ILeappAnalyzerIngestModule_report_name());
            }

        } catch (IOException | UncheckedIOException | TskCoreException ex) {
            // catch the error and continue on as report is not added
            logger.log(Level.WARNING, String.format("Error finding index file in path %s", iLeappOutputDir.toString()), ex);
        }

    }

}
