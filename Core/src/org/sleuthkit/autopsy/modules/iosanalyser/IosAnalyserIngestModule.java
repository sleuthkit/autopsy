/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.iosanalyser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;
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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Data source ingest module that runs Plaso against the image.
 */
public class IosAnalyserIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(IosAnalyserIngestModule.class.getName());
    private static final String MODULE_NAME = IosAnalyserModuleFactory.getModuleName();

    private static final String ILEAPP = "iLeapp"; //NON-NLS
    private static final String ILEAPP_EXECUTABLE = "ileapp.exe";//NON-NLS

    private File iLeappExecutable;

    private IngestJobContext context;
    private Case currentCase;
    private FileManager fileManager;

    private Image image;
    private AbstractFile previousFile = null; // cache used when looking up files in Autopsy DB

    IosAnalyserIngestModule() {
        
    }

    @NbBundle.Messages({
        "IosAnalyserIngestModule.executable.not.found=iLeapp Executable Not Found.",
        "IosAnalyserIngestModule.requires.windows=iLeapp module requires windows."})
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        if (false == PlatformUtil.isWindowsOS()) {
            throw new IngestModuleException(Bundle.IosAnalyserIngestModule_requires_windows());
        }

        try {
            iLeappExecutable = locateExecutable(ILEAPP_EXECUTABLE);
        } catch (FileNotFoundException exception) {
            logger.log(Level.WARNING, "iLeapp executable not found.", exception); //NON-NLS
            throw new IngestModuleException(Bundle.IosAnalyserIngestModule_executable_not_found(), exception);
        }

    }

    @NbBundle.Messages({
        "IosAnalyserIngestModule.error.running.iLeapp=Error running iLeapp, see log file.",
        "IosAnalyserIngestModule.error.creating.output.dir=Error creating iLeapp module output directory.",
        "IosAnalyserIngestModule.starting.iLeapp=Starting iLeapp",
        "IosAnalyserIngestModule.running.iLeapp=Running iLeapp",
        "IosAnalyserIngestModule.has.run=iLeapp",
        "IosAnalyserIngestModule.iLeapp.cancelled=iLeapp run was canceled",
        "IosAnalyserIngestModule.completed=iLeapp Processing Completed"})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {

        currentCase = Case.getCurrentCase();
        fileManager = currentCase.getServices().getFileManager();

        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss z", Locale.US).format(System.currentTimeMillis());//NON-NLS
        Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), ILEAPP, currentTime);
        try {
            Files.createDirectories(moduleOutputPath);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating iLeapp module output directory.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }

        List<AbstractFile> iLeappFilesToProcess = findiLeappFilesToProcess(dataSource);
        
        if (!iLeappFilesToProcess.isEmpty()) {
            // Run iLeapp
            for (AbstractFile iLeappFile: iLeappFilesToProcess) {
                logger.log(Level.INFO, "Starting iLeapp Run.");//NON-NLS
                statusHelper.progress(Bundle.IosAnalyserIngestModule_starting_iLeapp(), 0);
                ProcessBuilder iLeappCommand = buildiLeappCommand(moduleOutputPath, iLeappFile.getLocalAbsPath(), iLeappFile.getNameExtension());
                try {
                    int result = ExecUtil.execute(iLeappCommand, new DataSourceIngestModuleProcessTerminator(context));
                    if (result != 0) {
                        logger.log(Level.SEVERE, String.format("Error running iLeapp, error code returned %d", result)); //NON-NLS
                        return ProcessResult.ERROR;
                    } 
                } catch (IOException ex) {
                     logger.log(Level.SEVERE, String.format("Error when trying to execute iLeapp program against file %s", iLeappFile.getLocalAbsPath()), ex);
                }

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Log2timeline run was canceled"); //NON-NLS
                    return ProcessResult.OK;
                }
            }
        
//        if (Files.notExists(moduleOutputPath.resolve(PLASO))) {
//                logger.log(Level.WARNING, "Error running log2timeline: there was no storage file."); //NON-NLS
//                return ProcessResult.ERROR;
//        }

        // parse the output and make artifacts
//        createPlasoArtifacts(plasoFile.toString(), statusHelper);

        }
        
        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                Bundle.IosAnalyserIngestModule_has_run(),
                Bundle.IosAnalyserIngestModule_completed());
        IngestServices.getInstance().postMessage(message);
        return ProcessResult.OK;
    }

    private List<AbstractFile> findiLeappFilesToProcess(Content dataSource) {
        
        List<AbstractFile> iLeappFiles = new ArrayList<>();
        
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        // findFiles use the SQL wildcard # in the file name
        try {
            iLeappFiles = fileManager.findFiles(dataSource, "%", "/"); //NON-NLS
        } catch (TskCoreException ex) {
           logger.log(Level.WARNING, "No files found to process");; //NON-NLS
           return iLeappFiles;
        }
        
        List<AbstractFile> iLeappFilesToProcess = new ArrayList<>();
        for (AbstractFile iLeappFile: iLeappFiles) {
            if ((iLeappFile.getName().toLowerCase().contains(".zip") || (iLeappFile.getName().toLowerCase().contains(".tar")) 
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

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, IosAnalyserIngestModule.class.getPackage().getName(), false);
        if (null == exeFile || exeFile.canExecute() == false) {
            throw new FileNotFoundException(executableName + " executable not found.");
        }
        return exeFile;
    }


}
