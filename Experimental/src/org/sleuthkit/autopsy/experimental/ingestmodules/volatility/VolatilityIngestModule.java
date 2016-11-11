/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.ingestmodules.volatility;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.List;
import java.util.regex.Pattern;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.ProcTerminationCode;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;

/**
 * A data source ingest module that runs Volatility against hiberfil.sys files.
 */
@NbBundle.Messages({
    "unsupportedOS.message=Volatility module only supported for Windows platforms.",
    "missingExecutable.message=Unable to locate Volatility executable.",
    "cannotRunExecutable.message=Unable to execute Volatility.",
    "# {0} - output directory name", "cannotCreateOutputDir.message=Unable to create output directory: {0}."
})

final class VolatilityIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(VolatilityIngestModule.class.getName());
    private static final String EXECUTABLE_NAME = "volatility_2.4.win.standalone/volatility-2.4.standalone.exe"; // NON-NLS
    private static final String VERSION = "2.4";
    private static final String REPORT_NAME_BASE = VolatilityIngestModuleFactory.getModuleName() + " Output"; // NON-NLS
    private static final String TEMP_DIR_NAME = "temp"; // NON-NLS
    private IngestJobContext context;
    private File executableFile;
    private Path rootOutputDirPath;

    // List of known Volatility profiles
    private static final ArrayList<String> knownProfiles = new ArrayList<>(
            Arrays.asList("VistaSP0x64",
                    "VistaSP0x86",
                    "VistaSP1x64",
                    "VistaSP1x86",
                    "VistaSP2x64",
                    "VistaSP2x86",
                    "Win2003SP0x86",
                    "Win2003SP1x64",
                    "Win2003SP1x86",
                    "Win2003SP2x64",
                    "Win2003SP2x86",
                    "Win2008R2SP0x64",
                    "Win2008R2SP1x64",
                    "Win2008SP1x64",
                    "Win2008SP1x86",
                    "Win2008SP2x64",
                    "Win2008SP2x86",
                    "Win2012R2x64",
                    "Win2012x64",
                    "Win7SP0x64",
                    "Win7SP0x86",
                    "Win7SP1x64",
                    "Win7SP1x86",
                    "Win8SP0x64",
                    "Win8SP0x86",
                    "Win8SP1x64",
                    "Win8SP1x86",
                    "WinXPSP1x64",
                    "WinXPSP2x64",
                    "WinXPSP2x86",
                    "WinXPSP3x86"));

    // List of plugins to be run
    private static final VolatilityPlugin plugins[] = {
        // Image Identification plugins
        new VolatilityPlugin("imageinfo", ""),
        // Processes and DLLs
        new VolatilityPlugin("pslist", ""),
        new VolatilityPlugin("pstree", ""),
        new VolatilityPlugin("psscan", ""),
        new VolatilityPlugin("dlllist", ""),
        new VolatilityPlugin("handles", ""),
        new VolatilityPlugin("getsids", ""),
        new VolatilityPlugin("cmdscan", ""),
        new VolatilityPlugin("consoles", ""),
        new VolatilityPlugin("envars", ""),
        // Process memory
        new VolatilityPlugin("vadinfo", ""),
        new VolatilityPlugin("vadwalk", ""),
        new VolatilityPlugin("vadtree", ""),
        // Event logs
        new VolatilityPlugin("evtlogs", "-D evtlogs"),
        // Kernel memory and objects
        new VolatilityPlugin("modules", ""),
        new VolatilityPlugin("modscan", ""),
        new VolatilityPlugin("ssdt", ""),
        new VolatilityPlugin("driverscan", ""),
        new VolatilityPlugin("filescan", ""),
        new VolatilityPlugin("mutantscan", ""),
        new VolatilityPlugin("symlinkscan", ""),
        new VolatilityPlugin("thrdscan", ""),
        // Networking
        new VolatilityPlugin("connections", ""),
        new VolatilityPlugin("connscan", ""),
        new VolatilityPlugin("sockets", ""),
        new VolatilityPlugin("sockscan", ""),
        new VolatilityPlugin("netscan", ""),
        // Registry
        new VolatilityPlugin("hivescan", ""),
        new VolatilityPlugin("hivelist", ""),
        new VolatilityPlugin("printkey", ""),
        new VolatilityPlugin("userassist", ""),
        new VolatilityPlugin("shimcache", ""),
        new VolatilityPlugin("getservicesids", ""),
        // Crash dumps, hibernation and conversion
        new VolatilityPlugin("hibinfo", ""),
        // Miscellaneous
        new VolatilityPlugin("patcher", ""),};

    /**
     * Constructs a Volatility data source ingest module.
     *
     */
    VolatilityIngestModule() {
    }

    /**
     * @inheritDoc
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {

        this.context = context; // need to set context first, otherwise we might dereference a null pointer

        // Only process data sources that are disk images.
        if (!(context.getDataSource() instanceof Image)) {
            return;
        }

        this.executableFile = VolatilityIngestModule.locateExecutable();
        this.rootOutputDirPath = VolatilityIngestModule.createModuleOutputDirectoryForCase();
    }

    /**
     * @inheritDoc
     */
    @Override
    public IngestModule.ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        // Only process data sources that are disk images.
        if (!(dataSource instanceof Image)) {
            return IngestModule.ProcessResult.OK;
        }

        try {
            // Verify initialization succeeded. The ingest framework should 
            // have discarded this instance of the module if start up failed, 
            // but it does no harm to trust but verify.
            if ((null == context) || (null == this.executableFile) || (null == this.rootOutputDirPath)) {
                VolatilityIngestModule.logger.log(Level.SEVERE, "{0} ingest module called after failed start up", VolatilityIngestModuleFactory.getModuleName()); // NON-NLS
                return IngestModule.ProcessResult.ERROR;
            }

            Case autopsyCase = Case.getCurrentCase();
            SleuthkitCase sleuthkitCase = autopsyCase.getSleuthkitCase();
            Services services = new Services(sleuthkitCase);
            FileManager fileManager = services.getFileManager();

            // Find hiberfil.sys files on disk image
            List<AbstractFile> hiberfiles = fileManager.findFiles(dataSource, "hiberfil.sys"); // NON-NLS

            if (hiberfiles.isEmpty()) {
                IngestMessage message = IngestMessage.createMessage(
                        IngestMessage.MessageType.INFO,
                        VolatilityIngestModuleFactory.getModuleName(),
                        "No hiberfil.sys files found."); // NON-NLS

                IngestServices.getInstance().postMessage(message);
                return IngestModule.ProcessResult.OK;
            }

            // Make output subdirectories for the current time and image within
            // the module output directory for the current case.
            Image image = (Image) dataSource;
            DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss-SSSS");  // NON-NLS
            Date date = new Date();
            Path outputDirPath = Paths.get(this.rootOutputDirPath.toAbsolutePath().toString(), image.getName(), dateFormat.format(date));
            Files.createDirectories(outputDirPath);

            // A temp subdirectory is also created as a location for writing 
            // the hiberfil.sys file to disk.
            Path tempDirPath = Paths.get(outputDirPath.toString(), VolatilityIngestModule.TEMP_DIR_NAME);
            Files.createDirectory(tempDirPath);

            // Retrieve OS Info artifact(s) from database to construct Volatility profile
            ArrayList<BlackboardArtifact> osInfoArtifacts = sleuthkitCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO);

            // Limit artifacts to those associated with files in the current data source.
            Iterator<BlackboardArtifact> it = osInfoArtifacts.iterator();
            while (it.hasNext()) {
                if (!sleuthkitCase.isFileFromSource(dataSource, it.next().getObjectID())) {
                    it.remove();
                }
            }
            String volatilityProfile = "";

            if (osInfoArtifacts.isEmpty()) {
                VolatilityIngestModule.logger.log(Level.WARNING, "No Operating System Info artifacts found, some Volatility plugins may fail."); // NON-NLS                
            } else {
                volatilityProfile = VolatilityIngestModule.constructVolatilityProfile(osInfoArtifacts);
            }

            String profileArg = "";
            if (!volatilityProfile.isEmpty() && knownProfiles.contains(volatilityProfile)) {
                profileArg = "--profile=" + volatilityProfile; // NON-NLS
            }

            // Temporary path where hiberfil.sys is stored for processing
            Path hiberFilePath;
            // Where Volatility should store it's results
            Path volatilityOutputFilePath;
            Path volatilityErrorFilePath;

            // We report progress for each hiberfil we have to save multiplied by each plugin
            // we run against the hiberfil, plus 1 for the final report creation.
            int progressBarSize = (hiberfiles.size() * plugins.length) + 1;
            progressBar.switchToDeterminate(progressBarSize);
            int completeTasks = 0;
            for (AbstractFile file : hiberfiles) {
                // Save the hiberfil to disk
                long freeSpace = IngestServices.getInstance().getFreeDiskSpace();

                if (freeSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN && freeSpace <= file.getSize()) {
                    VolatilityIngestModule.logger.log(Level.SEVERE, "Not enough disk space to save {0}", file.getName()); // NON-NLS                    
                    return IngestModule.ProcessResult.ERROR;
                }

                hiberFilePath = Paths.get(tempDirPath.toString(), file.getName());
                progressBar.progress("Saving file: " + file.getName(), completeTasks);
                completeTasks++;
                ContentUtils.writeToFile(file, hiberFilePath.toFile(), context::dataSourceIngestIsCancelled);
                    
                // Because it can take a while to save the hiberfil.sys we
                // check to see if the task was cancelled while the file was being saved.
                if (this.context.dataSourceIngestIsCancelled()) {
                    FileUtil.deleteDir(outputDirPath.toFile());
                    return ProcessResult.OK;
                }

                for (VolatilityPlugin plugin : plugins) {
                    // The output file name will look something like hiberfil.sys_dlllist_41.txt
                    String outputFileName = file.getName() + "_" + plugin.getName() + "_" + file.getId() + ".txt"; // NON-NLS
                    volatilityOutputFilePath = Paths.get(outputDirPath.toString(), outputFileName);
                    String errorFileName = file.getName() + "_" + plugin.getName() + "_" + file.getId() + "_error.txt"; // NON-NLS
                    volatilityErrorFilePath = Paths.get(outputDirPath.toString(), errorFileName);

                    VolatilityIngestModule.logger.log(Level.INFO, "Volatility starting plugin: {0} ", plugin.getName()); // NON-NLS
                    progressBar.progress("Running plugin: " + plugin.getName()); // NON-NLS

                    DataSourceIngestModuleProcessTerminator terminator = new DataSourceIngestModuleProcessTerminator(this.context, true);
                    int exitValue = runVolatility(this.executableFile.toPath(), volatilityOutputFilePath, hiberFilePath, volatilityErrorFilePath, plugin, profileArg, terminator);

                    VolatilityIngestModule.logger.log(Level.INFO, "Volatility finished plugin: {0} ", plugin.getName()); // NON-NLS

                    // Check to see if the Volatility task has been cancelled before
                    // moving on to the next plugin.
                    if (this.context.dataSourceIngestIsCancelled()) {
                        // If the task is cancelled we delete all output.
                        FileUtil.deleteDir(outputDirPath.toFile());
                        return ProcessResult.OK;
                    } else if (terminator.getTerminationCode() == ProcTerminationCode.TIME_OUT) {
                        String msg = "Volatility module was terminated due to exceeding max allowable run time when scanning " + image.getName();
                        MessageNotifyUtil.Notify.error("Volatility Module Error", msg);
                        VolatilityIngestModule.logger.log(Level.SEVERE, msg); // NON-NLS
                        FileUtil.deleteDir(outputDirPath.toFile());
                        return IngestModule.ProcessResult.ERROR;
                    }

                    if (0 != exitValue) {
                        VolatilityIngestModule.logger.log(Level.WARNING, "Volatility returned error exit value = {0} when processing {1}", new Object[]{exitValue, file.getName()}); // NON-NLS
                    }

                    progressBar.progress(completeTasks); // Update progress bar as each plugin completes.
                    completeTasks++;
                }

                // Delete hiberfil
                Files.deleteIfExists(hiberFilePath);
            }

            // Delete temp folder
            FileUtil.deleteDir(tempDirPath.toFile());

            if (!VolatilityIngestModule.isDirectoryEmpty(outputDirPath)) {
                // Add the output directory to the case as an Autopsy report.            
                Case.getCurrentCase().addReport(outputDirPath.toAbsolutePath().toString(), VolatilityIngestModuleFactory.getModuleName(), VolatilityIngestModule.createReportName(image.getName()));
            }
            progressBar.progress(progressBarSize); // Report addition completed.

            return IngestModule.ProcessResult.OK;

        } catch (InterruptedException | IOException | SecurityException | UnsupportedOperationException | TskCoreException ex) {
            VolatilityIngestModule.logger.log(Level.SEVERE, "Error processing " + dataSource.getName() + " with " + VolatilityIngestModuleFactory.getModuleName(), ex); // NON-NLS
            return IngestModule.ProcessResult.ERROR;
        }
    }

    /**
     * Attempts to construct a profile string from information we have about the
     * operating system to pass to Volatility.
     *
     * @return A Volatility profile string.
     */
    static String constructVolatilityProfile(ArrayList<BlackboardArtifact> osInfoArtifacts) throws TskCoreException {
        String volatilityOSName = "";
        String architecture = "";

        for (BlackboardArtifact artifact : osInfoArtifacts) {
            List<BlackboardAttribute> attributes = artifact.getAttributes();

            for (BlackboardAttribute attribute : attributes) {
                if (attribute.getAttributeType().getTypeID() == ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()) {
                    if (!volatilityOSName.isEmpty()) {
                        VolatilityIngestModule.logger.log(Level.WARNING, "Volatility found multiple values for OS Name, using {0}.", volatilityOSName); // NON-NLS
                        continue;
                    }

                    volatilityOSName = VolatilityIngestModule.translateOSName(attribute.getValueString());
                } else if (attribute.getAttributeType().getTypeID() == ATTRIBUTE_TYPE.TSK_PROCESSOR_ARCHITECTURE.getTypeID()) {
                    if (!architecture.isEmpty()) {
                        VolatilityIngestModule.logger.log(Level.WARNING, "Volatility found multiple values for OS architecture, using {0}.", architecture); // NON-NLS
                        continue;
                    }

                    architecture = VolatilityIngestModule.translateArchitecture(attribute.getValueString());
                }
            }
        }

        return volatilityOSName + architecture;
    }

    /**
     * Translates the processor architecture to the Volatility format.
     *
     * @return Volatility compatible architecture.
     */
    static String translateArchitecture(String architecture) {
        if (architecture.contains("64")) {
            return "x64";
        } else if (architecture.contains("86")) {
            return "x86";
        } else {
            return "";
        }
    }

    /**
     * Translates the operating system name to the Volatility format.
     *
     * @return Volatility compatible OS name.
     */
    static String translateOSName(String osName) {
        String volatilityName = "";

        // First check the base operating system name.
        if (Pattern.matches("^.*[Vv][Ii][Ss][Tt][Aa].*$", osName)) { // NON-NLS
            volatilityName = "Vista"; // NON-NLS
        } else if (Pattern.matches("^.*2003.*$", osName)) { // NON-NLS
            volatilityName = "Win2003"; // NON-NLS
        } else if (Pattern.matches("^.*2008.*$", osName)) { // NON-NLS
            volatilityName = "Win2008"; // NON-NLS
        } else if (Pattern.matches("^.*2008 [Rr]2.*$", osName)) { // NON-NLS
            volatilityName = "Win2008R2"; // NON-NLS
        } else if (Pattern.matches("^.*2012.*$", osName)) { // NON-NLS
            volatilityName = "Win2012"; // NON-NLS
        } else if (Pattern.matches("^.*2012 [Rr]2.*$", osName)) { // NON-NLS
            volatilityName = "Win2012R2"; // NON-NLS
        } else if (Pattern.matches("^.*[Ww][Ii][Nn][Dd][Oo][Ww][Ss] 7.*$", osName)) { // NON-NLS
            volatilityName = "Win7"; // NON-NLS
        } else if (Pattern.matches("^.*[Ww][Ii][Nn][Dd][Oo][Ww][Ss] 8.*$", osName)) { // NON-NLS
            volatilityName = "Win8"; // NON-NLS
        } else if (Pattern.matches("^.*[Ww][Ii][Nn][Dd][Oo][Ww][Ss] [Xx][Pp].*$", osName)) { // NON-NLS
            volatilityName = "WinXP"; // NON-NLS
        }

        if (!volatilityName.isEmpty()) {
            // Next, check the service pack level
            if (osName.contains("Service Pack 3")) // NON-NLS
            {
                volatilityName += "SP3"; // NON-NLS
            } else if (osName.contains("Service Pack 2")) // NON-NLS
            {
                volatilityName += "SP2"; // NON-NLS
            } else {
                switch (volatilityName) {
                    case "Vista": // NON-NLS
                    case "Win2003": // NON-NLS
                    case "Win2008R2": // NON-NLS
                    case "Win7": // NON-NLS
                    case "Win8": // NON-NLS
                        volatilityName += "SP0"; // NON-NLS
                        break;
                    default:
                        volatilityName += "SP1"; // NON_NLS
                        break;
                }
            }
        }
        return volatilityName;
    }

    /**
     * Runs the Volatility executable.
     *
     * @param volatilityPath The path to the Bulk Extractor executable.
     * @param outputFilePath The path to the Volatility output file.
     * @param inputFilePath  The path to the input file.
     * @param terminator
     *
     * @return The exit value of the subprocess used to run Volatility.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private int runVolatility(Path volatilityPath, Path outputFilePath, Path inputFilePath, Path errorFilePath, VolatilityPlugin plugin, String profile, ExecUtil.ProcessTerminator terminator) throws SecurityException, IOException, InterruptedException {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(volatilityPath.toAbsolutePath().toString());
        commandLine.add(plugin.getName());
        commandLine.add(plugin.getArguments());
        commandLine.add(profile);
        commandLine.add("-f");
        commandLine.add(inputFilePath.toAbsolutePath().toString());

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(outputFilePath.toFile());
        processBuilder.redirectError(errorFilePath.toFile());

        return ExecUtil.execute(processBuilder, terminator);
    }

    /**
     * Locates the Volatility executable.
     *
     * @return The path of the executable.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    static File locateExecutable() throws IngestModule.IngestModuleException {
        // Must be running under a Windows operating system.
        if (!PlatformUtil.isWindowsOS()) {
            throw new IngestModule.IngestModuleException(Bundle.unsupportedOS_message());
        }

        File exeFile = InstalledFileLocator.getDefault().locate(VolatilityIngestModule.EXECUTABLE_NAME, VolatilityIngestModule.class.getPackage().getName(), false);

        if (null == exeFile) {
            throw new IngestModule.IngestModuleException(Bundle.missingExecutable_message());
        }
        if (!exeFile.canExecute()) {
            throw new IngestModule.IngestModuleException(Bundle.cannotRunExecutable_message());
        }

        return exeFile;
    }

    /**
     * Creates the output directory for this module for the current case, if it
     * does not already exist.
     *
     * @return The absolute path of the output directory.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    synchronized static Path createModuleOutputDirectoryForCase() throws IngestModule.IngestModuleException {
        Path path = Paths.get(Case.getCurrentCase().getModuleDirectory(), VolatilityIngestModuleFactory.getModuleName());
        try {
            Files.createDirectory(path);
        } catch (FileAlreadyExistsException ex) {
            // No worries.
        } catch (IOException | SecurityException | UnsupportedOperationException ex) {
            throw new IngestModule.IngestModuleException(Bundle.cannotCreateOutputDir_message(path.toString()), ex);
        }
        return path;
    }

    /**
     * Determines whether or not a directory is empty.
     *
     * @param directoryPath The path to the directory to inspect.
     *
     * @return True if the directory is empty, false otherwise.
     *
     * @throws IllegalArgumentException
     * @throws IOException
     */
    static boolean isDirectoryEmpty(final Path directoryPath) throws IllegalArgumentException, IOException {
        if (!Files.isDirectory(directoryPath)) {
            throw new IllegalArgumentException("The directoryPath argument must be a directory path"); // NON-NLS
        }
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directoryPath)) {
            return !dirStream.iterator().hasNext();
        }
    }

    /**
     * Creates a report name for the image that was processed.
     *
     * @param imageName The name of the image.
     *
     * @return The report name string.
     */
    static String createReportName(String imageName) {
        return imageName + " " + VolatilityIngestModule.REPORT_NAME_BASE;
    }
    
    /**
     * Get the current version of Volatility being used.
     * @return Version string
     */
    static String getVersion(){
        return VERSION;
    }

    private static final class VolatilityPlugin {

        private final String name;
        private final String arguments;

        VolatilityPlugin(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        String getName() {
            return name;
        }

        String getArguments() {
            return arguments;
        }
    }
}
